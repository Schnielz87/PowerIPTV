# -*- coding: utf-8 -*-
"""
Synchronisieren (Favoriten, eigene Listen, Weiterschauen) mit anderen Portiva-Geraeten im Heimnetz, auf denen
derselbe Zugang eingerichtet ist. Gleiches Format wie Android/Windows (SyncCore.kt), iOS (Sync.swift) und
Samsung-TV/Fire TV (sync.js). Jede Aenderung hat einen Zeitstempel (ms); pro Eintrag gewinnt die neuere.
Geloeschtes bleibt 60 Tage als "del" erhalten, damit es nicht zurueckkommt. Python 2 und 3.
"""
import hashlib
import json
import re
import threading
import time

from . import store
from .compat import native

DEFAULT_LIST = "default"
TOMBSTONE_MS = 60 * 86400000
_lock = threading.Lock()


def now_ms():
    return int(time.time() * 1000)


def _norm(u):
    u = (u or "").strip().lower()
    for p in ("http://", "https://"):
        if u.startswith(p):
            u = u[len(p):]
    return u.rstrip("/")


def can_sync(p):
    return bool(p) and bool((p.get("username") or "").strip() or (p.get("m3u") or "").strip())


def _host(u):
    h = _norm(u)
    for sep in ("/", "?", "#"):
        h = h.split(sep, 1)[0]
    for port in (":80", ":443"):
        if h.endswith(port):
            h = h[:-len(port)]
    return h


def _query(u):
    try:
        from urllib.parse import unquote_plus
    except ImportError:
        from urllib import unquote_plus
    q = (u or "").split("?", 1)[1] if "?" in (u or "") else ""
    q = q.split("#", 1)[0]
    out = {}
    for kv in q.split("&"):
        k, _, v = kv.partition("=")
        if k:
            out[k.lower()] = native(unquote_plus(v))
    return out


def account_key(p):
    """Gleicher Zugang = gleiche Kennung, egal ob als Xtream-Login oder M3U-Link (mit username/password) eingetragen."""
    username = (p.get("username") or "").strip()
    m3u = p.get("m3u") or ""
    q = _query(m3u)
    user = username or (q.get("username") or "").strip()
    if user:
        pw = ((p.get("password") or "") if username else (q.get("password") or "")).strip()
        server = p.get("server") if (username and (p.get("server") or "").strip()) else m3u
        raw = "portiva-sync-v2|%s|%s|%s" % (_host(server), user, pw)
    else:
        raw = "portiva-sync-v2|m3u|%s" % _norm(m3u)
    if not isinstance(raw, bytes):
        raw = raw.encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


# ---------- Zusammenfuehren ----------
def _newer(a, a_del, b, b_del):
    return a > b or (a == b and not a_del and b_del)


def _merge_entries(a, b):
    order, out = [], {}
    for e in a + b:
        cur = out.get(e["k"])
        if cur is None:
            out[e["k"]] = e
            order.append(e["k"])
        elif _newer(e.get("ts", 0), e.get("del"), cur.get("ts", 0), cur.get("del")):
            n = dict(e)
            n["item"] = e.get("item") or cur.get("item")
            out[e["k"]] = n
        else:
            n = dict(cur)
            n["item"] = cur.get("item") or e.get("item")
            out[e["k"]] = n
    return [out[k] for k in order]


def merge(a, b):
    cutoff = now_ms() - TOMBSTONE_MS
    order, lists = [], {}
    for l in (a.get("lists") or []) + (b.get("lists") or []):
        cur = lists.get(l["id"])
        if cur is None:
            lists[l["id"]] = l
            order.append(l["id"])
        else:
            head = dict(l if _newer(l.get("ts", 0), l.get("del"), cur.get("ts", 0), cur.get("del")) else cur)
            head["items"] = _merge_entries(cur.get("items") or [], l.get("items") or [])
            lists[l["id"]] = head
    rorder, resume = [], {}
    for r in (a.get("resume") or []) + (b.get("resume") or []):
        cur = resume.get(r["k"])
        if cur is None:
            resume[r["k"]] = r
            rorder.append(r["k"])
        else:
            win, other = (r, cur) if r.get("ts", 0) > cur.get("ts", 0) else (cur, r)
            n = dict(win)
            n["item"] = win.get("item") or other.get("item")
            n["ep"] = win.get("ep") or other.get("ep")
            resume[r["k"]] = n

    def old(x):
        return x.get("del") and 0 < x.get("ts", 0) < cutoff
    out = []
    for lid in order:
        l = lists[lid]
        if old(l):
            continue
        l = dict(l)
        l["items"] = [e for e in l.get("items") or [] if not old(e)]
        out.append(l)
    return {"v": 1, "lists": out, "resume": [resume[k] for k in rorder]}


def changes(before, after):
    def present(d):
        return set("%s|%s" % (l["id"], e["k"]) for l in d["lists"] if not l.get("del") for e in l.get("items") or [] if not e.get("del"))
    b, a = present(before), present(after)
    res = len([r for r in after["resume"] if not any(x["k"] == r["k"] and x.get("ts", 0) >= r.get("ts", 0) for x in before["resume"])])
    return len(a - b) + res, len(b - a)


# ---------- Zeitstempel der lokalen Aenderungen (je Zugang; negativ = geloescht) ----------
def meta(pid):
    return store.load("syncmeta", {}).get(pid, {})


def _set_meta(pid, values):
    all_ = store.load("syncmeta", {})
    m = all_.get(pid, {})
    m.update(values)
    cutoff = now_ms() - TOMBSTONE_MS
    for k in list(m.keys()):
        if m[k] < 0 and -m[k] < cutoff:
            del m[k]
    all_[pid] = m
    store.save("syncmeta", all_)


# ---------- Umrechnung Receiver-Eintrag <-> Abgleich-Format ----------
KINDS = {"live": "LIVE", "movie": "MOVIE", "series": "SERIES"}


def item_key(i):
    return "%s:%s" % (KINDS.get(i.get("kind"), "MOVIE"), i.get("id"))


def to_sync(i):
    o = {"id": str(i.get("id")), "name": i.get("name") or "", "type": KINDS.get(i.get("kind"), "MOVIE")}
    for k in ("logo", "ext", "url"):
        if i.get(k):
            o[k] = i.get(k)
    if i.get("cat") is not None:
        o["cat"] = str(i.get("cat"))
    elif i.get("group"):
        o["cat"] = i.get("group")
    return o


def from_sync(s):
    o = {"kind": (s.get("type") or "MOVIE").lower(), "id": s.get("id"), "name": s.get("name") or ""}
    for src, dst in (("logo", "logo"), ("ext", "ext"), ("cat", "cat"), ("url", "url")):
        if s.get(src):
            o[dst] = s.get(src)
    return o


def _label(ep):
    if ep.get("season") or ep.get("num"):
        return ("S%02d E%02d  %s" % (ep.get("season") or 0, ep.get("num") or 0, ep.get("title") or "")).strip()
    return ep.get("title") or ""


def export(pid):
    m = meta(pid)
    local = [{"id": DEFAULT_LIST, "name": "Favoriten", "items": store.favorites(pid)}] + store.lists(pid)
    lists = []
    for l in local:
        pre = "I|%s|" % l["id"]
        keys = set(item_key(i) for i in l.get("items") or [])
        items = [{"k": item_key(i), "ts": abs(m.get(pre + item_key(i), 0)), "del": False, "item": to_sync(i)} for i in l.get("items") or []]
        items += [{"k": k[len(pre):], "ts": -v, "del": True} for k, v in m.items() if v < 0 and k.startswith(pre) and k[len(pre):] not in keys]
        lists.append({"id": l["id"], "name": l.get("name") or "", "ts": abs(m.get("L|" + l["id"], 0)), "del": False, "items": items})
    ids = set(l["id"] for l in local)
    lists += [{"id": k[2:], "name": "", "ts": -v, "del": True, "items": []} for k, v in m.items() if v < 0 and k.startswith("L|") and k[2:] not in ids]

    resume, seen = [], set()
    for e in store.continue_watching(pid):  # neueste zuerst
        it = e.get("item") or {}
        ts = int(e.get("t", 0)) * 1000
        r = {"pos": int(e.get("pos", 0)) * 1000, "dur": int(e.get("total", 0)) * 1000, "ts": ts, "done": False}
        if it.get("kind") == "episode" and it.get("series_id"):
            r["k"] = "SERIES:%s" % it["series_id"]
            r["item"] = {"id": str(it["series_id"]), "name": it.get("series_name") or "", "type": "SERIES"}
            if it.get("logo"):
                r["item"]["logo"] = it["logo"]
            if it.get("cat") is not None:
                r["item"]["cat"] = str(it["cat"])
            m2 = re.match(r"^S(\d+)\s*E(\d+)\s*(.*)$", it.get("name") or "")
            r["ep"] = {"id": str(it.get("id")), "season": int(m2.group(1)) if m2 else 0, "num": int(m2.group(2)) if m2 else 0,
                       "title": m2.group(3) if m2 else (it.get("name") or ""), "ext": it.get("ext")}
        elif it.get("kind") == "movie":
            r["k"] = item_key(it)
            r["item"] = to_sync(it)
        else:
            continue
        if r["k"] in seen:
            continue
        seen.add(r["k"])
        resume.append(r)
    return {"v": 1, "lists": lists, "resume": resume}


def apply(profile, merged, before):
    pid = profile["id"]
    stamps, local = {}, []
    for l in merged["lists"]:
        stamps["L|" + l["id"]] = -l.get("ts", 0) if l.get("del") else l.get("ts", 0)
        for e in l.get("items") or []:
            stamps["I|%s|%s" % (l["id"], e["k"])] = -e.get("ts", 0) if e.get("del") else e.get("ts", 0)
        if l.get("del") and l["id"] != DEFAULT_LIST:
            continue
        live = [(n, e) for n, e in enumerate(l.get("items") or []) if not e.get("del") and e.get("item")]
        live.sort(key=lambda x: (-x[1].get("ts", 0), x[0]))
        local.append({"id": l["id"], "name": l.get("name") or ("Favoriten" if l["id"] == DEFAULT_LIST else "Liste"),
                      "items": [store.slim(from_sync(e["item"])) for _, e in live]})
    stamps = dict((k, v) for k, v in stamps.items() if v)
    fav = store.load("favorites", {})
    fav[pid] = next((l["items"] for l in local if l["id"] == DEFAULT_LIST), [])
    store.save("favorites", fav)
    lst = store.load("lists", {})
    lst[pid] = [l for l in local if l["id"] != DEFAULT_LIST]
    store.save("lists", lst)
    _set_meta(pid, stamps)

    local_ts = dict((r["k"], r.get("ts", 0)) for r in before["resume"])
    incoming = [r for r in merged["resume"] if r.get("item") and r.get("ts", 0) > local_ts.get(r["k"], 0)]
    incoming.sort(key=lambda r: r.get("ts", 0))  # aelteste zuerst -> neueste steht danach vorne im Verlauf
    if not incoming:
        return
    from .api import make_source
    src = make_source(profile)
    d = store.load("resume", {})
    for r in incoming:
        it = from_sync(r["item"])
        ep = r.get("ep")
        if it["kind"] == "series":
            if not ep or profile.get("type") == "m3u":
                continue
            play = {"kind": "episode", "id": ep["id"], "name": _label(ep), "ext": ep.get("ext") or "mp4", "series_id": it["id"],
                    "series_name": it.get("name"), "logo": it.get("logo"), "cat": it.get("cat")}
        elif it["kind"] == "movie":
            play = it
        else:
            continue
        try:
            url = src.stream_url(play)
        except Exception:
            continue
        pos, total = int(r.get("pos", 0)) // 1000, int(r.get("dur", 0)) // 1000
        if r.get("done") or pos < 30:
            d.pop(url, None)
        else:
            d[url] = {"pos": pos, "total": total, "item": store.slim(play), "pid": pid, "t": int(r.get("ts", 0)) // 1000}
        if it["kind"] == "series":
            store.add_history(pid, {"kind": "series", "id": it["id"], "name": it.get("name", ""), "logo": it.get("logo"), "cat": it.get("cat")})
        else:
            store.add_history(pid, play)
    store.save("resume", d)


# ---------- Server (/portiva/sync) ----------
def handle(body):
    """Liefert die Antwort (dict). Benutzer nicht vorhanden -> unknown=True."""
    name = store.settings().get("device_name") or "Enigma2-Receiver"
    if not isinstance(body, dict) or not re.match(r"^[0-9a-f]{64}$", str(body.get("account") or "")) or not isinstance(body.get("data"), dict):
        return {"ok": False, "error": "Ungueltige Anfrage"}
    p = next((x for x in store.profiles()["list"] if can_sync(x) and account_key(x) == body["account"]), None)
    if not p:
        return {"ok": False, "name": name, "unknown": True, "error": "Benutzer auf „%s“ nicht eingerichtet" % name}
    with _lock:
        before = export(p["id"])
        merged = merge(before, body["data"])
        apply(p, merged, before)
    return {"ok": True, "name": name, "data": merged}


# ---------- Client ----------
def _post(dev, body):
    from .link import LINK_PORT
    try:
        from urllib.request import urlopen, Request
        from urllib.error import HTTPError
    except ImportError:
        from urllib2 import urlopen, Request, HTTPError
    data = json.dumps(body).encode("utf-8")
    req = Request("http://%s:%d/portiva/sync" % (dev["host"], int(dev.get("port") or LINK_PORT)), data=data,
                  headers={"Content-Type": "application/json; charset=utf-8"})
    try:
        r = urlopen(req, timeout=20)
        return r.getcode(), native(json.loads(r.read().decode("utf-8")))
    except HTTPError as e:
        return e.code, None
    except Exception:
        return 0, None


def sync_all(profile):
    """Mit allen Geraeten im Heimnetz abgleichen (blockierend, im Hintergrund aufrufen). Liefert die Meldung."""
    from .link import discover
    if not can_sync(profile):
        return "Für diesen Zugang ist kein Abgleich möglich."
    devices = discover()
    if not devices:
        return "Kein anderes Gerät mit Portiva gefunden.\nIm gleichen Heimnetz? Portiva dort geöffnet?"
    account = account_key(profile)
    me = store.settings().get("device_name") or "Enigma2-Receiver"
    done, failed, added, removed = [], [], 0, 0
    for d in devices:
        dname = d.get("name") or d.get("host")
        with _lock:
            before = export(profile["id"])
            code, reply = _post(d, {"account": account, "data": before, "from": me})
            ok = code == 200 and isinstance(reply, dict) and reply.get("ok") and isinstance(reply.get("data"), dict)
            if ok:
                merged = merge(before, reply["data"])
                apply(profile, merged, before)
                a, b = changes(before, merged)
                added += a
                removed += b
        if ok:
            done.append(dname)
        elif isinstance(reply, dict) and reply.get("unknown"):
            continue
        elif code == 404:
            failed.append("Portiva auf „%s“ ist zu alt – bitte dort aktualisieren" % dname)
        elif code == 0:
            failed.append("„%s“ nicht erreichbar" % dname)
        else:
            failed.append((reply or {}).get("error") or "Fehler %d" % code)
    if done:
        msg = "Synchronisiert mit %s.\n" % ", ".join(done)
        msg += ("%d neu oder aktualisiert, %d entfernt." % (added, removed)) if (added or removed) else "Alles war schon aktuell."
    else:
        msg = "Kein Gerät mit diesem Benutzer gefunden.\nAuf dem anderen Gerät muss derselbe Zugang eingerichtet und Portiva geöffnet sein."
    if failed:
        msg += "\n\nNicht geklappt: " + "; ".join(failed)
    return native(msg)
