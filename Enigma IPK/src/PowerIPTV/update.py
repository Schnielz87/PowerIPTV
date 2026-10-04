# -*- coding: utf-8 -*-
"""Update ueber GitHub (wie Android/Windows): neue Version finden, herunterladen und direkt per opkg installieren."""
import json
import os
import time

from Screens.MessageBox import MessageBox
from Components.ActionMap import ActionMap
from Components.MenuList import MenuList

from . import store
from .common import run_async, PLUGIN_DIR

REPO = "Schnielz87/PowerIPTV"
ASSET = "PowerIPTV-Enigma2.ipk"
IPK = "/tmp/PowerIPTV-Enigma2.ipk"


def current_version():
    try:
        with open(PLUGIN_DIR + "version.txt") as f:
            return f.read().strip()
    except Exception:
        return "0"


def _parts(v):
    out = []
    for x in str(v or "").lstrip("vV").split("."):
        try:
            out.append(int(x))
        except Exception:
            out.append(0)
    return out


def newer(a, b):
    return _parts(a) > _parts(b)


def check():
    """Blockierend. Liefert {version, url, notes} oder None (aktuell)."""
    from .api import http_get
    r = json.loads(http_get("https://api.github.com/repos/%s/releases/latest" % REPO, 20))
    tag = r.get("tag_name") or ""
    asset = next((a for a in r.get("assets") or [] if a.get("name") == ASSET), None)
    s = store.settings()
    s["update_checked"] = int(time.time())
    info = {"version": tag.lstrip("v"), "url": asset.get("browser_download_url"), "notes": r.get("body") or ""} if asset and newer(tag, current_version()) else None
    s["update_info"] = info
    store.save_settings(s)
    return info


def check_cached():
    """Hoechstens alle 24 Stunden wirklich nachfragen."""
    s = store.settings()
    if time.time() - s.get("update_checked", 0) < 24 * 3600:
        info = s.get("update_info")
        return info if info and newer(info.get("version"), current_version()) else None
    return check()


def download(url):
    from .api import http_get_bytes
    data = http_get_bytes(url, 120)
    with open(IPK, "wb") as f:
        f.write(data)
    return IPK


class UpdateScreen(__import__("Screens.Screen", fromlist=["Screen"]).Screen):
    def __init__(self, session):
        from .ui import skin
        from Components.Label import Label
        self.skin = skin("PortivaUpdate", info=True)
        super(UpdateScreen, self).__init__(session)
        self.p_closed = False
        self.onClose.append(self._closed)
        self.info = None
        self["title"] = Label("Update")
        self["sub"] = Label("Installiert: Version %s" % current_version())
        self["status"] = Label("Prüfe GitHub …")
        self["info"] = Label("")
        self["key_red"] = Label("")
        self["key_green"] = Label("")
        self["key_yellow"] = Label("Erneut prüfen")
        self["key_blue"] = Label("")
        self["list"] = MenuList([])
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.install, "green": self.install, "yellow": self.recheck, "cancel": self.close,
        }, -1)
        self.onLayoutFinish.append(self.recheck)

    def _closed(self):
        self.p_closed = True

    def recheck(self):
        self["status"].setText("Prüfe GitHub …")
        run_async(self, check, self.result, lambda m: self["status"].setText("Prüfung fehlgeschlagen: " + m))

    def result(self, info):
        self.info = info
        if not info:
            self["status"].setText("Du hast die neueste Version.")
            self["key_green"].setText("")
            self["list"].setList([("Version %s ist aktuell" % current_version(), None)])
            self["info"].setText("")
            return
        self["status"].setText("Neue Version %s verfügbar – GRÜN oder OK installiert sie direkt." % info["version"])
        self["key_green"].setText("Jetzt installieren")
        self["list"].setList([("Version %s installieren" % info["version"], "go")])
        self["info"].setText(info.get("notes", "").replace("*", "").replace("`", "").replace("#", "")[:1500])

    def install(self):
        if not self.info:
            return
        self["status"].setText("Lade Version %s herunter …" % self.info["version"])
        run_async(self, lambda: download(self.info["url"]), self.downloaded, lambda m: self["status"].setText("Download fehlgeschlagen: " + m))

    def downloaded(self, path):
        from Screens.Console import Console
        self.session.openWithCallback(self.installed, Console, title="PowerIPTV wird aktualisiert",
                                      cmdlist=["opkg install --force-reinstall --force-downgrade %s" % path])

    def installed(self, *args):
        def restart(yes):
            if yes:
                from Screens.Standby import TryQuitMainloop
                self.session.open(TryQuitMainloop, 3)
        self.session.openWithCallback(restart, MessageBox, "Update installiert. Jetzt die Benutzeroberfläche (GUI) neu starten?", MessageBox.TYPE_YESNO)
