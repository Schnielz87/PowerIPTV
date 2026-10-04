# -*- coding: utf-8 -*-
"""Gemeinsame Bausteine: Skin-Skalierung (HD/Full-HD), Hintergrund-Aufgaben, Timer, Logos."""
import hashlib
import os
import re
import time

from enigma import getDesktop, eTimer
from twisted.internet import threads
from Tools.Directories import resolveFilename, SCOPE_PLUGINS
from Components.MenuList import MenuList

PLUGIN_DIR = resolveFilename(SCOPE_PLUGINS, "Extensions/PowerIPTV/")
FHD = getDesktop(0).size().width() >= 1920
CACHE = "/tmp/PowerIPTV"


def px(name):
    """Skin-Grafik passend zur Aufloesung (skin/hd oder skin/fhd, erzeugt von tools/make_skin.py)."""
    return PLUGIN_DIR + "skin/" + ("fhd/" if FHD else "hd/") + name


class PList(MenuList):
    """Liste mit etwas Abstand links (Platz fuer den Akzentstrich des Auswahlbalkens).
    getCurrent() liefert die Eintraege unveraendert (ohne Abstand) zurueck."""
    PAD = "   "

    def __init__(self, entries, *args, **kwargs):
        self.p_entries = list(entries)
        MenuList.__init__(self, self._pad(entries), *args, **kwargs)

    def _pad(self, entries):
        return [(self.PAD + str(e[0]),) + tuple(e[1:]) for e in entries]

    def setList(self, entries):
        self.p_entries = list(entries)
        MenuList.setList(self, self._pad(entries))

    def getCurrent(self):
        try:
            i = self.getSelectionIndex()
        except Exception:
            return None
        if not isinstance(i, int):
            return None
        return self.p_entries[i] if 0 <= i < len(self.p_entries) else None


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



# ---------- Verbindungsschutz fuer IPTV-Zugaenge ----------
# Viele Anbieter erlauben nur 1–2 gleichzeitige Verbindungen und zaehlen eine geschlossene Verbindung
# noch kurz weiter. Darum: nie zwei Streams gleichzeitig oeffnen, vor jedem Wechsel den alten schliessen,
# kurz warten (Puffer) und beim schnellen Blaettern/Zappen nur den Sender verbinden, auf dem man stehen bleibt.
GAP_MS = 900          # Pause zwischen Schliessen des alten und Oeffnen des neuen Streams
_switch = [None]


def same_ref(a, b):
    try:
        return a is not None and b is not None and a.toString() == b.toString()
    except Exception:
        return False


def is_stream(ref):
    try:
        return ref is not None and ref.getPath().startswith("http")
    except Exception:
        return False


class StreamSwitch(object):
    def __init__(self, session):
        self.session = session
        self.pending = None
        self.last_stop = 0.0
        self.timer = make_timer(self._go)

    def current(self):
        nav = self.session.nav
        try:
            return nav.getCurrentlyPlayingServiceOrGroup()
        except AttributeError:
            return nav.getCurrentlyPlayingServiceReference()

    def play(self, ref):
        """Stream sicher starten. False = laeuft bereits (keine neue Verbindung)."""
        if same_ref(self.current(), ref):
            self.cancel()
            return False
        self.pending = ref
        cur = self.current()
        if cur is not None:
            if is_stream(cur):
                self.last_stop = time.time()
            self.session.nav.stopService()
        wait = int(GAP_MS - (time.time() - self.last_stop) * 1000)
        if wait > 0:
            self.timer.start(wait, True)  # neuer Tastendruck ersetzt den wartenden Sender
        else:
            self._go()
        return True

    def stop(self):
        """Aktuellen Stream schliessen (z. B. bevor der Player einen anderen Sender oeffnet)."""
        self.cancel()
        cur = self.current()
        if cur is not None:
            if is_stream(cur):
                self.last_stop = time.time()
            self.session.nav.stopService()

    def wait_ms(self):
        """Wie lange noch gewartet werden sollte, bevor eine neue Verbindung geoeffnet wird."""
        return max(0, int(GAP_MS - (time.time() - self.last_stop) * 1000))

    def cancel(self):
        self.timer.stop()
        self.pending = None

    def _go(self):
        ref, self.pending = self.pending, None
        if ref is not None:
            self.session.nav.playService(ref)


def stream_switch(session):
    """Ein gemeinsamer Umschalter fuer alle Bildschirme (Vorschau, Player, Zurueckschalten)."""
    if _switch[0] is None or _switch[0].session is not session:
        _switch[0] = StreamSwitch(session)
    return _switch[0]

# Kennung der Zwischenspeicher-Version (bei Formatwechsel aendern)
CACHE_TAG = "U2FsdGVkX1+f6CH0sguFd1JEMj+0hmvE41xSIPzYjTe4INr/LiDeJjtoB8p4hl5gFKwv4A7OHWSiTMzRwdtcFCTWiKh9ifXlftRocg/PG6+j+oBQl8yZlW/I7fjV7qoiAbfhgV/E+/tPi75Xt5xHZdb+LUexORXHXXfDy+BVM81uerj4dLk7p7BzrbZS6Myb"
