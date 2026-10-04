# -*- coding: utf-8 -*-
"""Filme/Folgen auf die Festplatte bzw. den USB-Stick des Receivers laden (erscheinen dann in den Aufnahmen)."""
import os
import re
import threading

_active = {}


def target_dir():
    for d in ("/media/hdd/movie", "/media/usb/movie", "/media/hdd", "/media/usb", "/media/mmc/movie", "/media/mmc"):
        if os.path.isdir(d) and os.access(d, os.W_OK):
            return d
    return None


def _notify(text):
    try:
        from Tools.Notifications import AddPopup
        AddPopup(text, 1, 8, "PowerIPTVDownload")
    except Exception:
        pass


def start(name, url, ext):
    """Startet den Download im Hintergrund. Liefert eine Statusmeldung."""
    folder = target_dir()
    if not folder:
        return "Kein Speicher gefunden (Festplatte oder USB-Stick unter /media/hdd bzw. /media/usb)"
    if url in _active:
        return "„%s“ wird bereits heruntergeladen" % name
    safe = re.sub(r'[\\/:*?"<>|]+', " ", name).strip()[:120] or "PowerIPTV"
    path = os.path.join(folder, "%s.%s" % (safe, ext or "mp4"))

    def run():
        tmp = path + ".part"
        try:
            try:
                from urllib.request import urlopen, Request
            except ImportError:
                from urllib2 import urlopen, Request
            resp = urlopen(Request(url, headers={"User-Agent": "PowerIPTV-Enigma2"}), timeout=60)
            with open(tmp, "wb") as f:
                while True:
                    chunk = resp.read(256 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
                    _active[url] = os.path.getsize(tmp)
            os.rename(tmp, path)
            _notify("PowerIPTV: „%s“ heruntergeladen – zu finden unter Aufnahmen" % name)
        except Exception as e:
            try:
                os.remove(tmp)
            except Exception:
                pass
            _notify("PowerIPTV: Download von „%s“ fehlgeschlagen (%s)" % (name, e))
        finally:
            _active.pop(url, None)
    _active[url] = 0
    t = threading.Thread(target=run)
    t.daemon = True
    t.start()
    return "Download gestartet: „%s“ → %s (du wirst benachrichtigt, wenn er fertig ist)" % (name, folder)
