# -*- coding: utf-8 -*-
"""
Player: der Film-Player von Enigma2 (MoviePlayer) mit Portiva-Extras:
Live TV mit Hoch/Runter bzw. CH+/CH- umschalten, Weiterschauen bei Filmen/Folgen,
naechste Folge automatisch, INFO = Jetzt/Danach.
"""
import os
import time

from enigma import eServiceReference, eTimer
from Screens.InfoBar import MoviePlayer
from Screens.MessageBox import MessageBox
from Components.ActionMap import ActionMap

from . import store


def service_type():
    """GStreamer (4097) oder – falls ServiceApp installiert – exteplayer3 (5002, spielt mehr Formate)."""
    s = store.settings().get("player", "auto")
    if s != "auto":
        try:
            return int(s)
        except Exception:
            return 4097
    if os.path.isdir("/usr/lib/enigma2/python/Plugins/SystemPlugins/ServiceApp"):
        return 5002
    return 4097


def make_ref(url, name):
    ref = eServiceReference(service_type(), 0, url)
    ref.setName(name)
    return ref


class PortivaPlayer(MoviePlayer):
    def __init__(self, session, source, playlist, index, live, start_at=0):
        self.p_source = source
        self.p_list = playlist
        self.p_index = index
        self.p_live = live
        self.p_url = self._url(playlist[index])
        self.p_start_at = start_at
        MoviePlayer.__init__(self, session, make_ref(self.p_url, playlist[index].get("name", "")))
        self.skinName = ["PortivaPlayer", "MoviePlayer"]
        actions = {"showEventInfo": self.p_info, "info": self.p_info}
        if live and len(playlist) > 1:
            # Sender wechseln: Hoch/Runter und CH+/CH- (je nach Image heissen die Aktionen etwas anders)
            actions.update({
                "up": lambda: self.p_zap(-1), "down": lambda: self.p_zap(1),
                "zapUp": lambda: self.p_zap(-1), "zapDown": lambda: self.p_zap(1),
                "switchChannelUp": lambda: self.p_zap(-1), "switchChannelDown": lambda: self.p_zap(1),
                "nextBouquet": lambda: self.p_zap(-1), "prevBouquet": lambda: self.p_zap(1),
            })
        self["portivaActions"] = ActionMap(
            ["InfobarEPGActions", "EPGSelectActions", "DirectionActions", "InfobarChannelSelection", "ChannelSelectBaseActions"],
            actions, -2)
        self.p_seek_timer = eTimer()
        try:
            self.p_seek_timer.callback.append(self.p_apply_resume)
        except AttributeError:  # aeltere Images
            self.p_seek_timer_conn = self.p_seek_timer.timeout.connect(self.p_apply_resume)
        if start_at > 0 and not live:
            self.p_seek_timer.start(2500, True)

    def _url(self, item):
        return self.p_source.stream_url(item, store.settings().get("live_format", "ts"))

    # ---------- Weiterschauen ----------
    def p_position(self):
        """(Position, Laenge) in Sekunden."""
        try:
            seek = self.session.nav.getCurrentService().seek()
            pos = seek.getPlayPosition()
            length = seek.getLength()
            return (pos[1] // 90000 if not pos[0] else 0), (length[1] // 90000 if not length[0] else 0)
        except Exception:
            return 0, 0

    def p_apply_resume(self):
        try:
            self.session.nav.getCurrentService().seek().seekTo(int(self.p_start_at) * 90000)
        except Exception:
            pass

    def p_save(self, ended=False):
        if self.p_live:
            return
        pos, total = self.p_position()
        store.resume_set(self.p_url, total if ended else pos, total)

    # ---------- Bedienung ----------
    def p_zap(self, delta):
        self.p_index = (self.p_index + delta) % len(self.p_list)
        item = self.p_list[self.p_index]
        self.p_url = self._url(item)
        self.session.nav.playService(make_ref(self.p_url, item.get("name", "")))
        try:
            self.doShow()
        except Exception:
            pass

    def p_info(self):
        item = self.p_list[self.p_index]
        if not self.p_live:
            text = item.get("name", "")
            pos, total = self.p_position()
            if total:
                text += "\n\n%d:%02d / %d:%02d" % (pos // 60, pos % 60, total // 60, total % 60)
            self.session.open(MessageBox, text, MessageBox.TYPE_INFO, timeout=6)
            return
        epg = []
        try:
            epg = self.p_source.short_epg(item)
        except Exception:
            pass
        now = time.time()
        cur = [e for e in epg if e["start"] <= now < e["end"]]
        nxt = [e for e in epg if e["start"] >= (cur[0]["end"] if cur else now)]
        fmt = lambda e: "%s – %s  %s" % (time.strftime("%H:%M", time.localtime(e["start"])), time.strftime("%H:%M", time.localtime(e["end"])), e["title"])
        lines = [item.get("name", ""), ""]
        lines.append("Jetzt: " + (fmt(cur[0]) if cur else "Kein Programm gefunden"))
        if cur and cur[0].get("desc"):
            lines.append(cur[0]["desc"][:300])
        lines.append("Weiter: " + (fmt(nxt[0]) if nxt else "Kein Programm gefunden"))
        self.session.open(MessageBox, "\n".join(lines), MessageBox.TYPE_INFO, timeout=10)

    # ---------- Beenden ----------
    def leavePlayer(self):
        self.p_save()
        self.is_closing = True
        self.close()

    def leavePlayerOnExit(self):
        self.leavePlayer()

    def doEofInternal(self, playing):
        if not playing or self.p_live:
            return
        self.p_save(ended=True)
        # Serien: naechste Folge automatisch
        if self.p_list[self.p_index].get("kind") == "episode" and self.p_index + 1 < len(self.p_list):
            self.p_index += 1
            item = self.p_list[self.p_index]
            self.p_url = self._url(item)
            self.session.nav.playService(make_ref(self.p_url, item.get("name", "")))
            return
        self.is_closing = True
        self.close()

    def showMovies(self):
        pass
