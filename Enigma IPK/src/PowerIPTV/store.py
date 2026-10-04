# -*- coding: utf-8 -*-
"""Gespeicherte Daten: Zugaenge, Favoriten, Weiterschauen, Einstellungen (JSON in /etc/enigma2/PowerIPTV)."""
import json
import os
import time

DIR = "/etc/enigma2/PowerIPTV"


def _path(name):
    return os.path.join(DIR, name + ".json")


def load(name, default):
    try:
        with open(_path(name)) as f:
            return json.load(f)
    except Exception:
        return default


def save(name, data):
    try:
        if not os.path.isdir(DIR):
            os.makedirs(DIR)
        tmp = _path(name) + ".tmp"
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.rename(tmp, _path(name))
        os.chmod(_path(name), 0o600)  # enthaelt Zugangsdaten
    except Exception:
        pass


# ---------- Zugaenge ----------
def profiles():
    return load("profiles", {"list": [], "active": None})


def active_profile():
    d = profiles()
    for p in d["list"]:
        if p.get("id") == d.get("active"):
            return p
    return d["list"][0] if d["list"] else None


def save_profile(p):
    d = profiles()
    if not p.get("id"):
        p["id"] = str(int(time.time() * 1000))
    d["list"] = [x for x in d["list"] if x.get("id") != p["id"]] + [p]
    d["active"] = p["id"]
    save("profiles", d)
    return p


def activate(pid):
    d = profiles()
    d["active"] = pid
    save("profiles", d)


def delete_profile(pid):
    d = profiles()
    d["list"] = [x for x in d["list"] if x.get("id") != pid]
    if d.get("active") == pid:
        d["active"] = d["list"][0]["id"] if d["list"] else None
    save("profiles", d)


# ---------- Favoriten (je Zugang) ----------
def _fav_key(item):
    return "%s:%s" % (item.get("kind"), item.get("id"))


def favorites(pid):
    return load("favorites", {}).get(pid, [])


def is_favorite(pid, item):
    k = _fav_key(item)
    return any(_fav_key(x) == k for x in favorites(pid))


def toggle_favorite(pid, item):
    all_ = load("favorites", {})
    lst = all_.get(pid, [])
    k = _fav_key(item)
    if any(_fav_key(x) == k for x in lst):
        lst = [x for x in lst if _fav_key(x) != k]
        added = False
    else:
        lst.append(slim(item))
        added = True
    all_[pid] = lst
    save("favorites", all_)
    return added


ITEM_KEYS = ("kind", "id", "name", "logo", "ext", "number", "url", "group", "_kind", "archive", "series_id", "series_name")


def slim(item):
    return dict((k, item.get(k)) for k in ITEM_KEYS if item.get(k) is not None)


# ---------- Weiterschauen (Sekunden je Stream-Adresse, mit Titel fuer die Liste "Weiterschauen") ----------
def resume_get(url):
    v = load("resume", {}).get(url, 0)
    return v.get("pos", 0) if isinstance(v, dict) else v


def resume_set(url, seconds, total, item=None, pid=None):
    d = load("resume", {})
    if seconds < 30 or (total and seconds > total - 60):
        d.pop(url, None)  # kaum angefangen oder fertig gesehen
    else:
        d[url] = {"pos": int(seconds), "total": int(total or 0), "item": slim(item or {}), "pid": pid, "t": int(time.time())}
    if len(d) > 300:
        for k in sorted(d.keys(), key=lambda k: (d[k].get("t", 0) if isinstance(d[k], dict) else 0))[:len(d) - 300]:
            d.pop(k, None)
    save("resume", d)


def continue_watching(pid):
    """Angefangene Filme/Folgen dieses Zugangs, zuletzt gesehene zuerst."""
    d = load("resume", {})
    out = [v for v in d.values() if isinstance(v, dict) and v.get("pid") == pid and v.get("item")]
    out.sort(key=lambda v: -v.get("t", 0))
    return out


# ---------- Verlauf (zuletzt gesehen, je Zugang) ----------
def history(pid):
    return load("history", {}).get(pid, [])


def add_history(pid, item):
    if not pid:
        return
    all_ = load("history", {})
    lst = [x for x in all_.get(pid, []) if _fav_key(x) != _fav_key(item)]
    lst.insert(0, slim(item))
    all_[pid] = lst[:60]
    save("history", all_)


# ---------- Einstellungen ----------
DEFAULTS = {"player": "auto", "live_format": "ts", "pin": "", "auto_adult": True, "locked": [],
            "auto_update": True, "device_name": "Enigma2-Receiver", "sort": "DEFAULT", "language": ""}


# ---------- Kindersicherung ----------
def is_locked(pid, kind, cat):
    """Kategorie gesperrt (von Hand oder automatisch als Erwachseneninhalt)?"""
    from .rules import is_adult
    s = settings()
    if not s.get("pin"):
        return False
    key = "%s|%s|%s" % (pid, kind, cat.get("id"))
    return key in s.get("locked", []) or (s.get("auto_adult") and is_adult(cat.get("name")))


def toggle_lock(pid, kind, cat):
    s = settings()
    key = "%s|%s|%s" % (pid, kind, cat.get("id"))
    locked = s.get("locked", [])
    if key in locked:
        locked.remove(key)
        res = False
    else:
        locked.append(key)
        res = True
    s["locked"] = locked
    save_settings(s)
    return res


def settings():
    s = dict(DEFAULTS)
    s.update(load("settings", {}))
    return s


def save_settings(s):
    save("settings", s)
