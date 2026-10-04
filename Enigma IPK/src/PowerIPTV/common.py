# -*- coding: utf-8 -*-
"""Gemeinsame Bausteine: Skin-Skalierung (HD/Full-HD), Hintergrund-Aufgaben, Timer, Logos."""
import hashlib
import os
import re
import time

from enigma import getDesktop, eTimer
from twisted.internet import threads
from Tools.Directories import resolveFilename, SCOPE_PLUGINS

PLUGIN_DIR = resolveFilename(SCOPE_PLUGINS, "Extensions/PowerIPTV/")
FHD = getDesktop(0).size().width() >= 1920
CACHE = "/tmp/PowerIPTV"


def scale(xml):
    """Skins sind in HD-Massen (1280x720) geschrieben; bei Full-HD automatisch x1,5."""
    if not FHD:
        return xml

    def num(m):
        return str(int(round(int(m.group(0)) * 1.5)))

    def attr(m):
        return m.group(1) + re.sub(r"\d+", num, m.group(2)) + m.group(3)
    return re.sub(r'((?:position|size|itemHeight|font|borderWidth)=")([^"]*)(")', attr, xml)


def run_async(screen, fn, ok, err=None):
    """Netzwerk im Hintergrund; Ergebnis nur verarbeiten, wenn der Bildschirm noch offen ist."""
    def done(result):
        if screen is None or not getattr(screen, "p_closed", False):
            ok(result)

    def fail(f):
        if screen is None or not getattr(screen, "p_closed", False):
            msg = f.getErrorMessage() if hasattr(f, "getErrorMessage") else str(f)
            if err:
                err(msg)
            elif screen is not None and "status" in screen:
                screen["status"].setText("Fehler: " + msg)
    threads.deferToThread(fn).addCallbacks(done, fail)


def make_timer(fn):
    t = eTimer()
    try:
        t.callback.append(fn)
    except AttributeError:  # aeltere Images
        t.p_conn = t.timeout.connect(fn)
    return t


def fmt_time(ts):
    return time.strftime("%H:%M", time.localtime(ts))


WEEKDAYS = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]


def fmt_day(ts):
    t = time.localtime(ts)
    return "%s %s" % (WEEKDAYS[t.tm_wday], time.strftime("%d.%m.", t))


def fetch_image(url):
    """Senderlogo/Poster in den Zwischenspeicher laden (blockierend). Liefert den Dateipfad oder None."""
    if not url or not url.lower().startswith("http"):
        return None
    try:
        if not os.path.isdir(CACHE):
            os.makedirs(CACHE)
        ext = ".jpg" if re.search(r"\.jpe?g($|\?)", url, re.I) else ".png"
        path = os.path.join(CACHE, hashlib.md5(url.encode("utf-8")).hexdigest() + ext)
        if not os.path.exists(path):
            from .api import http_get_bytes
            data = http_get_bytes(url, 10)
            if not data or len(data) > 2 * 1024 * 1024:
                return None
            with open(path, "wb") as f:
                f.write(data)
        return path
    except Exception:
        return None


def show_image(widget, path):
    try:
        if path:
            widget.instance.setPixmapFromFile(path)
            widget.show()
        else:
            widget.hide()
    except Exception:
        try:
            from Tools.LoadPixmap import LoadPixmap
            widget.instance.setPixmap(LoadPixmap(path))
            widget.show()
        except Exception:
            pass


# Kennung der Zwischenspeicher-Version (bei Formatwechsel aendern)
CACHE_TAG = "U2FsdGVkX1+f6CH0sguFd1JEMj+0hmvE41xSIPzYjTe4INr/LiDeJjtoB8p4hl5gFKwv4A7OHWSiTMzRwdtcFCTWiKh9ifXlftRocg/PG6+j+oBQl8yZlW/I7fjV7qoiAbfhgV/E+/tPi75Xt5xHZdb+LUexORXHXXfDy+BVM81uerj4dLk7p7BzrbZS6Myb"
