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
        lst.append(dict((key, item.get(key)) for key in ("kind", "id", "name", "logo", "ext", "number", "url", "group", "_kind")))
        added = True
    all_[pid] = lst
    save("favorites", all_)
    return added


# ---------- Weiterschauen (Sekunden je Stream-Adresse) ----------
def resume_get(url):
    return load("resume", {}).get(url, 0)


def resume_set(url, seconds, total):
    d = load("resume", {})
    if seconds < 30 or (total and seconds > total - 60):
        d.pop(url, None)  # kaum angefangen oder fertig gesehen
    else:
        d[url] = int(seconds)
    if len(d) > 300:
        for k in list(d.keys())[:len(d) - 300]:
            d.pop(k, None)
    save("resume", d)


# ---------- Einstellungen ----------
DEFAULTS = {"player": "auto", "live_format": "ts"}


def settings():
    s = dict(DEFAULTS)
    s.update(load("settings", {}))
    return s


def save_settings(s):
    save("settings", s)
