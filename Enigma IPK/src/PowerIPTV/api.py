# -*- coding: utf-8 -*-
"""
Zugaenge wie in den anderen Portiva-Varianten: Xtream Codes (player_api.php) und M3U-Link.
Laeuft im Hintergrund-Thread (siehe ui.run_async), nie im Bedien-Thread von Enigma2.
"""
import json
import re
import time
import base64

from .rules import year_of

try:  # Python 3 (OpenATV 7, OpenPLi 9 ...)
    from urllib.request import urlopen, Request
    from urllib.parse import urlencode, quote
except ImportError:  # Python 2 (aeltere Images)
    from urllib2 import urlopen, Request
    from urllib import urlencode, quote

UA = "PowerIPTV-Enigma2"


def http_get(url, timeout=30):
    req = Request(url, headers={"User-Agent": UA})
    resp = urlopen(req, timeout=timeout)
    data = resp.read()
    try:
        return data.decode("utf-8")
    except Exception:
        return data.decode("latin-1")


def http_get_bytes(url, timeout=15):
    return urlopen(Request(url, headers={"User-Agent": UA}), timeout=timeout).read()


def normalize_server(s):
    s = (s or "").strip().rstrip("/")
    if s and not re.match(r"^https?://", s, re.I):
        s = "http://" + s
    return s


def _b64(s):
    if not s:
        return ""
    try:
        return base64.b64decode(s).decode("utf-8", "ignore")
    except Exception:
        return s


def _num(v, default=0):
    try:
        return int(float(v))
    except Exception:
        return default


class XtreamSource(object):
    kinds = ("live", "movie", "series")

    def __init__(self, profile):
        self.p = profile
        self.base = normalize_server(profile.get("server"))
        self.cache = {}
        self.timezone = None

    def api(self, action=None, timeout=40, **params):
        q = {"username": self.p.get("username", ""), "password": self.p.get("password", "")}
        if action:
            q["action"] = action
        q.update(params)
        text = http_get(self.base + "/player_api.php?" + urlencode(q), timeout)
        if not text.strip():
            return None
        return json.loads(text)

    def authenticate(self):
        r = self.api()
        info = (r or {}).get("user_info") if isinstance(r, dict) else None
        if not info:
            raise Exception("Ungültige Antwort vom Server")
        if str(info.get("auth")) != "1":
            raise Exception("Benutzername oder Passwort falsch")
        self.timezone = ((r or {}).get("server_info") or {}).get("timezone") or self.timezone
        return info

    def categories(self, kind):
        key = "cat_" + kind
        if key not in self.cache:
            action = {"live": "get_live_categories", "movie": "get_vod_categories", "series": "get_series_categories"}[kind]
            data = self.api(action) or []
            self.cache[key] = [{"id": str(c.get("category_id")), "name": c.get("category_name") or "Unbenannt"} for c in data if c.get("category_id") is not None]
        return self.cache[key]

    def items(self, kind, cat_id=None):
        key = "items_%s_%s" % (kind, cat_id)
        if key in self.cache:
            return self.cache[key]
        action = {"live": "get_live_streams", "movie": "get_vod_streams", "series": "get_series"}[kind]
        params = {"category_id": cat_id} if cat_id else {}
        data = self.api(action, timeout=90, **params) or []
        out = []
        for o in data:
            name = o.get("name") or ""
            try:
                rating = float(o.get("rating_5based") or 0) * 2 or float(o.get("rating") or 0)
            except Exception:
                rating = 0
            if kind == "series":
                sid = o.get("series_id")
                out.append({"kind": kind, "id": str(sid), "name": name, "logo": o.get("cover"), "cat": str(o.get("category_id")),
                            "plot": o.get("plot"), "year": year_of(o.get("releaseDate") or o.get("year"), name),
                            "rating_f": rating, "added": _num(o.get("last_modified"))})
            else:
                sid = o.get("stream_id")
                out.append({"kind": kind, "id": str(sid), "name": name, "logo": o.get("stream_icon"), "cat": str(o.get("category_id")),
                            "number": o.get("num"), "ext": o.get("container_extension") or "mp4",
                            "archive": _num(o.get("tv_archive_duration")) or 1 if str(o.get("tv_archive")) == "1" else 0,
                            "year": year_of(o.get("year"), name) if kind == "movie" else 0,
                            "rating_f": rating, "added": _num(o.get("added"))})
        self.cache[key] = out
        return out

    def stream_url(self, item, live_ext="ts"):
        u, p = quote(self.p.get("username", "")), quote(self.p.get("password", ""))
        if item["kind"] == "live":
            return "%s/live/%s/%s/%s.%s" % (self.base, u, p, item["id"], live_ext)
        if item["kind"] == "episode":
            return "%s/series/%s/%s/%s.%s" % (self.base, u, p, item["id"], item.get("ext") or "mp4")
        return "%s/movie/%s/%s/%s.%s" % (self.base, u, p, item["id"], item.get("ext") or "mp4")

    def short_epg(self, item):
        try:
            r = self.api("get_short_epg", timeout=15, stream_id=item["id"], limit="4") or {}
        except Exception:
            return []
        out = []
        for o in r.get("epg_listings") or []:
            start, end = _epg_times(o)
            if end > start:
                out.append({"start": start, "end": end, "title": _b64(o.get("title")), "desc": _b64(o.get("description"))})
        now = time.time()
        if not any(e["end"] > now for e in out):
            # Manche Anbieter liefern die Kurz-EPG leer -> Jetzt/Weiter aus dem vollen Programm nehmen
            out = [e for e in self.full_epg(item) if e["end"] > now][:4]
        return sorted(out, key=lambda x: x["start"])

    def full_epg(self, item):
        """Programm mehrerer Tage (mit Archiv-Kennzeichen fuer Catch-up)."""
        try:
            r = self.api("get_simple_data_table", timeout=30, stream_id=item["id"]) or {}
        except Exception:
            return []
        out = []
        for o in r.get("epg_listings") or []:
            start, end = _epg_times(o)
            if end > start:
                out.append({"start": start, "end": end, "title": _b64(o.get("title")), "desc": _b64(o.get("description")),
                            "archive": str(o.get("has_archive")) == "1"})
        return sorted(out, key=lambda x: x["start"])

    def catchup_url(self, item, start, end):
        """Vergangene Sendung aus dem Archiv des Anbieters (Xtream timeshift)."""
        days = item.get("archive") or 0
        now = time.time()
        if not days or start > now or start < now - days * 86400:
            return None
        minutes = max(1, int(round((end - start) / 60.0)))
        u, p = quote(self.p.get("username", "")), quote(self.p.get("password", ""))
        return "%s/timeshift/%s/%s/%d/%s/%s.ts" % (self.base, u, p, minutes, self._fmt_zone(start), item["id"])

    def _fmt_zone(self, ts):
        if self.timezone:
            try:
                from zoneinfo import ZoneInfo
                import datetime
                return datetime.datetime.fromtimestamp(ts, ZoneInfo(self.timezone)).strftime("%Y-%m-%d:%H-%M")
            except Exception:
                pass
        return time.strftime("%Y-%m-%d:%H-%M", time.localtime(ts))

    def vod_info(self, item):
        try:
            r = self.api("get_vod_info", timeout=20, vod_id=item["id"]) or {}
        except Exception:
            return {}
        info = r.get("info") or {}
        return {"plot": info.get("plot") or info.get("description"), "genre": info.get("genre"), "cast": info.get("cast"),
                "duration": info.get("duration"), "rating": info.get("rating"), "year": info.get("releasedate"),
                "cover": info.get("movie_image") or info.get("cover_big")}

    def series_info(self, item):
        r = self.api("get_series_info", timeout=40, series_id=item["id"]) or {}
        seasons = {}
        eps = r.get("episodes") or {}
        if isinstance(eps, dict):
            for season, lst in eps.items():
                for e in lst or []:
                    n = _num(e.get("season"), _num(season))
                    seasons.setdefault(n, []).append({
                        "kind": "episode", "id": str(e.get("id")), "ext": e.get("container_extension") or "mp4",
                        "name": "S%02d E%02d  %s" % (n, _num(e.get("episode_num")), e.get("title") or ""),
                        "episode_num": _num(e.get("episode_num")), "plot": (e.get("info") or {}).get("plot") if isinstance(e.get("info"), dict) else None,
                    })
        for n in seasons:
            seasons[n].sort(key=lambda x: x["episode_num"])
        info = r.get("info") or {}
        return {"plot": info.get("plot"), "seasons": seasons}

    def account_text(self):
        try:
            info = self.authenticate()
        except Exception:
            return ""
        parts = []
        exp = _num(info.get("exp_date"))
        if exp:
            import time
            parts.append("Gültig bis " + time.strftime("%d.%m.%Y", time.localtime(exp)))
        if info.get("max_connections"):
            parts.append("%s Verbindung(en)" % info.get("max_connections"))
        return " · ".join(parts)


class M3uSource(object):
    """M3U-Link: Gruppen = Kategorien; Filme/Serien anhand der Adresse (/movie/, /series/) erkannt."""

    def __init__(self, profile):
        self.p = profile
        self.entries = None

    def _load(self):
        if self.entries is not None:
            return
        text = http_get(self.p.get("m3u", ""), 120)
        entries, info = [], None
        for line in text.splitlines():
            line = line.strip()
            if line.startswith("#EXTINF"):
                name = line.split(",", 1)[1].strip() if "," in line else ""
                g = re.search(r'group-title="([^"]*)"', line)
                logo = re.search(r'tvg-logo="([^"]*)"', line)
                info = {"name": name, "group": g.group(1) if g else "Ohne Gruppe", "logo": logo.group(1) if logo else None}
            elif line and not line.startswith("#") and info is not None:
                low = line.lower()
                kind = "movie" if "/movie/" in low else "series" if "/series/" in low else "live"
                info.update({"url": line, "kind": "episode" if kind == "series" else kind, "id": line})
                info["_kind"] = kind
                entries.append(info)
                info = None
        self.entries = entries

    def authenticate(self):
        self._load()
        if not self.entries:
            raise Exception("Die M3U-Liste ist leer oder nicht erreichbar")
        return {}

    def categories(self, kind):
        self._load()
        seen, out = set(), []
        for e in self.entries:
            if e["_kind"] == kind and e["group"] not in seen:
                seen.add(e["group"])
                out.append({"id": e["group"], "name": e["group"]})
        return out

    def items(self, kind, cat_id=None):
        self._load()
        return [e for e in self.entries if e["_kind"] == kind and (cat_id is None or e["group"] == cat_id)]

    def stream_url(self, item, live_ext="ts"):
        return item["url"]

    def short_epg(self, item):
        return []

    def full_epg(self, item):
        return []

    def catchup_url(self, item, start, end):
        return None

    def vod_info(self, item):
        return {}

    def series_info(self, item):
        return {"plot": None, "seasons": {1: [item]}}

    def account_text(self):
        return "M3U-Link"


def _epg_times(o):
    """Start/Ende einer Sendung: Unix-Zeit, sonst Text "JJJJ-MM-TT HH:MM:SS" (UTC, wie bei Android)."""
    def one(ts_key, text_key):
        v = _num(o.get(ts_key))
        if v:
            return v
        try:
            import calendar
            return int(calendar.timegm(time.strptime(str(o.get(text_key) or "")[:19], "%Y-%m-%d %H:%M:%S")))
        except Exception:
            return 0
    return one("start_timestamp", "start"), one("stop_timestamp", "end")


def make_source(profile):
    return M3uSource(profile) if profile.get("type") == "m3u" else XtreamSource(profile)
