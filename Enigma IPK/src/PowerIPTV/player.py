# -*- coding: utf-8 -*-
"""
Player auf Basis des Film-Players von Enigma2 (MoviePlayer: Spulen, Tonspur, Untertitel, Seitenverhaeltnis
wie gewohnt) mit der Portiva-Oberflaeche:
  Live TV: Infoleiste unten (Logo, Jetzt mit Fortschritt, Weiter), Hoch/Runter bzw. CH+/- = Sender,
           OK = Senderliste rechts (Gruen = EPG aktualisieren), INFO/EPG = Programm mit Catch-up,
           Rot = letzter Sender, Blau = an anderes Portiva-Geraet senden.
  Filme/Folgen: Titel + Fortschritt, Weiterschauen, naechste Folge automatisch, Blau = senden.
"""
import os
import time

from enigma import eServiceReference
from Screens.InfoBar import MoviePlayer
from Screens.Screen import Screen
from Screens.MessageBox import MessageBox
from Screens.ChoiceBox import ChoiceBox
from Components.ActionMap import ActionMap
from Components.Label import Label
from Components.Pixmap import Pixmap
from Components.MenuList import MenuList
from Components.ProgressBar import ProgressBar

from . import store
from .common import scale, run_async, make_timer, fmt_time, fmt_day, fetch_image, show_image, px, PList, stream_switch


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


class DirectSource(object):
    """Fuer Inhalte mit fertiger Adresse (von anderem Geraet gesendet, Catch-up)."""

    def stream_url(self, item, live_ext="ts"):
        return item["url"]

    def short_epg(self, item):
        return []

    def full_epg(self, item):
        return []

    def catchup_url(self, item, start, end):
        return None


def _player_skin():
    return """
<screen name="PortivaPlayer" position="0,0" size="1280,720" flags="wfNoBorder" backgroundColor="#ff000000">
  <ePixmap pixmap="%(osd)s" position="0,480" size="1280,240" alphatest="blend" zPosition="-1" />
  <widget name="p_logo" position="48,572" size="114,70" alphatest="blend" scale="1" zPosition="2" />
  <widget name="p_title" position="190,556" size="880,40" font="Regular;30" foregroundColor="#00ffffff" backgroundColor="#00000000" transparent="1" />
  <widget source="global.CurrentTime" render="Label" position="1090,556" size="150,40" font="Regular;30" halign="right" foregroundColor="#00ffffff" backgroundColor="#00000000" transparent="1">
    <convert type="ClockToText">Default</convert>
  </widget>
  <widget name="p_now" position="190,598" size="1050,32" font="Regular;24" foregroundColor="#005ec4f2" backgroundColor="#00000000" transparent="1" />
  <widget name="p_progress" position="190,636" size="1050,6" pixmap="%(prog)s" borderWidth="0" backgroundColor="#002a3348" />
  <widget name="p_next" position="190,650" size="1050,28" font="Regular;20" foregroundColor="#00b0b8c8" backgroundColor="#00000000" transparent="1" />
  <widget name="p_hint" position="40,690" size="1200,24" font="Regular;17" foregroundColor="#009fb1c9" backgroundColor="#00000000" transparent="1" />
</screen>""" % {"osd": px("osd.png"), "prog": px("progress.png")}


class PortivaPlayer(MoviePlayer):
    def __init__(self, session, source, playlist, index, live, start_at=0, keep_stream=False):
        self.p_source = source
        self.p_list = playlist
        self.p_index = index
        self.p_last = None
        self.p_live = live
        self.p_url = self._url(playlist[index])
        self.p_start_at = start_at
        self.p_keep = keep_stream and live  # aus der Live-Vorschau gestartet: Sender beim Verlassen weiterlaufen lassen
        self.p_epg = []
        self.p_epg_cache = {}
        self.p_pid = (store.active_profile() or {}).get("id")
        MoviePlayer.__init__(self, session, make_ref(self.p_url, playlist[index].get("name", "")))
        self.skin = scale(_player_skin())
        self.skinName = ["PortivaPlayer"]
        self.p_closed = False
        self.onClose.append(self._p_closed)
        self["p_logo"] = Pixmap()
        self["p_title"] = Label("")
        self["p_now"] = Label("")
        self["p_next"] = Label("")
        self["p_hint"] = Label("")
        self["p_progress"] = ProgressBar()
        actions = {"showEventInfo": self.p_programme, "info": self.p_programme, "showEPGBar": self.p_programme,
                   "showEventInfoPlugin": self.p_programme, "blue": self.p_send, "yellow": self.p_fav}
        if live:
            actions.update({
                "ok": self.p_channels, "red": self.p_zap_back,
                "up": lambda: self.p_zap(-1), "down": lambda: self.p_zap(1),
                "zapUp": lambda: self.p_zap(-1), "zapDown": lambda: self.p_zap(1),
                "switchChannelUp": lambda: self.p_zap(-1), "switchChannelDown": lambda: self.p_zap(1),
                "nextBouquet": lambda: self.p_zap(-1), "prevBouquet": lambda: self.p_zap(1),
                "pageUp": lambda: self.p_zap(-1), "pageDown": lambda: self.p_zap(1),
            })
        self["portivaActions"] = ActionMap(
            ["OkCancelActions", "ColorActions", "InfobarEPGActions", "EPGSelectActions", "DirectionActions",
             "InfobarChannelSelection", "ChannelSelectBaseActions"], actions, -2)
        self.p_timer = make_timer(self.p_tick)
        self.p_seek_timer = make_timer(self.p_apply_resume)
        self.onLayoutFinish.append(self.p_layout)

    def _p_closed(self):
        self.p_closed = True
        self.p_timer.stop()

    def _url(self, item):
        if item.get("url"):
            return item["url"]
        return self.p_source.stream_url(item, store.settings().get("live_format", "ts"))

    def p_item(self):
        return self.p_list[self.p_index]

    # ---------- Anzeige ----------
    def p_layout(self):
        self.p_timer.start(1000)
        if self.p_start_at > 0 and not self.p_live:
            self.p_seek_timer.start(2500, True)
        self.p_refresh_info()

    def p_refresh_info(self):
        item = self.p_item()
        num = ("%s  " % item["number"]) if self.p_live and item.get("number") else ""
        self["p_title"].setText(num + (item.get("name") or ""))
        if self.p_live:
            self["p_hint"].setText("▲▼ Sender   OK Senderliste   INFO Programm/Catch-up   ROT letzter Sender   BLAU an Gerät senden")
            self["p_now"].setText("Jetzt: …")
            self["p_next"].setText("")
            self.p_load_epg()
        else:
            self["p_hint"].setText("◄► Spulen   AUDIO Tonspur   TEXT Untertitel   BLAU an Gerät senden   EXIT zurück")
            self["p_next"].setText("")
        logo = item.get("logo")
        self["p_logo"].hide()
        run_async(self, lambda: fetch_image(logo), lambda path: show_image(self["p_logo"], path), lambda m: None)

    def p_load_epg(self, force=False):
        item = self.p_item()
        key = item.get("id")
        if not force and key in self.p_epg_cache and time.time() - self.p_epg_cache[key][0] < 600:
            self.p_epg = self.p_epg_cache[key][1]
            self.p_show_epg()
            return

        def done(epg):
            self.p_epg_cache[key] = (time.time(), epg)
            if self.p_item().get("id") == key:
                self.p_epg = epg
                self.p_show_epg()
        run_async(self, lambda: self.p_source.short_epg(item), done, lambda m: None)

    def p_show_epg(self):
        now = time.time()
        cur = [e for e in self.p_epg if e["start"] <= now < e["end"]]
        nxt = [e for e in self.p_epg if e["start"] >= (cur[0]["end"] if cur else now)]
        line = lambda e: "%s – %s  %s" % (fmt_time(e["start"]), fmt_time(e["end"]), e["title"])
        self["p_now"].setText("Jetzt: " + (line(cur[0]) if cur else "Kein Programm gefunden"))
        self["p_next"].setText("Weiter: " + (line(nxt[0]) if nxt else "Kein Programm gefunden"))
        self.p_cur = cur[0] if cur else None

    def p_tick(self):
        if self.p_live:
            cur = getattr(self, "p_cur", None)
            if cur:
                now = time.time()
                if now >= cur["end"]:
                    self.p_load_epg(force=True)
                else:
                    self["p_progress"].setValue(int(100 * (now - cur["start"]) / max(1, cur["end"] - cur["start"])))
            else:
                self["p_progress"].setValue(0)
            return
        pos, total = self.p_position()
        if total:
            self["p_progress"].setValue(int(100 * pos / total))
            self["p_now"].setText("%s  /  %s   (noch %s)" % (self._hms(pos), self._hms(total), self._hms(total - pos)))

    @staticmethod
    def _hms(s):
        s = int(max(0, s))
        return "%d:%02d:%02d" % (s // 3600, (s // 60) % 60, s % 60) if s >= 3600 else "%d:%02d" % (s // 60, s % 60)

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
        item = self.p_item()
        if self.p_live or item.get("kind") == "catchup":
            return
        pos, total = self.p_position()
        store.resume_set(self.p_url, total if ended else pos, total, item, self.p_pid)

    # ---------- Live TV ----------
    def p_play_index(self, index):
        if index == self.p_index:
            return
        self.p_last = self.p_index
        self.p_index = index % len(self.p_list)
        item = self.p_item()
        self.p_url = self._url(item)
        # Zapp-Puffer: alten Stream schliessen, kurz warten; beim schnellen Zappen wird nur der letzte Sender verbunden
        stream_switch(self.session).play(make_ref(self.p_url, item.get("name", "")))
        store.add_history(self.p_pid, item)
        self.p_refresh_info()
        try:
            self.doShow()  # beim Umschalten immer die Sender-Infos zeigen
        except Exception:
            pass

    def p_zap(self, delta):
        if len(self.p_list) > 1:
            self.p_play_index(self.p_index + delta)

    def p_zap_back(self):
        if self.p_last is not None:
            self.p_play_index(self.p_last)

    def p_channels(self):
        def chosen(index):
            if index is not None:
                self.p_play_index(index)
        self.session.openWithCallback(chosen, ChannelPanel, self.p_source, self.p_list, self.p_index)

    def p_programme(self):
        item = self.p_item()
        if not self.p_live:
            text = item.get("name", "")
            pos, total = self.p_position()
            if total:
                text += "\n\n%s / %s" % (self._hms(pos), self._hms(total))
            self.session.open(MessageBox, text, MessageBox.TYPE_INFO, timeout=6)
            return

        def chosen(res):
            if res:  # Catch-up einer vergangenen Sendung
                self.session.open(PortivaPlayer, DirectSource(), [res], 0, False)
        self.session.openWithCallback(chosen, ProgrammeScreen, self.p_source, item)

    def p_fav(self):
        item = self.p_item()
        if self.p_pid and item.get("kind") in ("live", "movie"):
            added = store.toggle_favorite(self.p_pid, item)
            self.session.open(MessageBox, ("♥ „%s“ zu Favoriten hinzugefügt" if added else "„%s“ aus Favoriten entfernt") % item.get("name", ""),
                              MessageBox.TYPE_INFO, timeout=3)

    # ---------- Portiva Link: an anderes Geraet senden ----------
    def p_send(self):
        from . import link
        self["p_hint"].setText("Suche Portiva-Geräte im Heimnetz …")
        try:
            self.doShow()
        except Exception:
            pass

        def found(devs):
            self.p_refresh_hint()
            if not devs:
                self.session.open(MessageBox, "Kein Portiva-Gerät gefunden.\nPortiva muss auf dem anderen Gerät geöffnet sein (gleiches Heimnetz).",
                                  MessageBox.TYPE_INFO, timeout=8)
                return

            def chosen(c):
                if not c:
                    return
                item = self.p_item()
                pos, total = self.p_position()

                def sent(err):
                    if err:
                        self.session.open(MessageBox, err, MessageBox.TYPE_ERROR, timeout=6)
                    else:
                        self.p_save()
                        self.is_closing = True
                        self.close()
                run_async(self, lambda: link.send_play(c[1], item.get("name", ""), self.p_url, self.p_live, 0 if self.p_live else pos, total, item.get("logo")), sent)
            self.session.openWithCallback(chosen, ChoiceBox, title="An Gerät senden – hier weiterschauen auf …",
                                          list=[("%s  (%s)" % (d.get("name"), d.get("platform")), d) for d in devs])
        run_async(self, link.discover, found, lambda m: self.p_refresh_hint())

    def p_refresh_hint(self):
        self.p_refresh_info() if not self.p_live else self["p_hint"].setText(
            "▲▼ Sender   OK Senderliste   INFO Programm/Catch-up   ROT letzter Sender   BLAU an Gerät senden")

    # ---------- Beenden ----------
    def leavePlayer(self):
        self.p_save()
        self.is_closing = True
        if self.p_keep:
            # zurueck in die Senderliste: aktuellen Sender in der Vorschau weiterlaufen lassen (keine neue Verbindung)
            try:
                self.lastservice = self.session.nav.getCurrentlyPlayingServiceOrGroup()
            except AttributeError:
                self.lastservice = self.session.nav.getCurrentlyPlayingServiceReference()
            self.close(self.p_index)
            return
        self.close()

    def leavePlayerOnExit(self):
        self.leavePlayer()

    def doEofInternal(self, playing):
        if not playing or self.p_live:
            return
        self.p_save(ended=True)
        # Serien: naechste Folge automatisch
        if self.p_item().get("kind") == "episode" and self.p_index + 1 < len(self.p_list):
            self.p_index += 1
            item = self.p_item()
            self.p_url = self._url(item)
            stream_switch(self.session).play(make_ref(self.p_url, item.get("name", "")))
            self.p_refresh_info()
            return
        self.is_closing = True
        self.close()

    def showMovies(self):
        pass


# ---------- Senderliste rechts im Bild ----------
def _panel_skin():
    return """
<screen name="PortivaChannelPanel" position="760,0" size="520,720" flags="wfNoBorder" backgroundColor="#ff000000">
  <ePixmap pixmap="%(panel)s" position="0,0" size="520,720" alphatest="blend" zPosition="-1" />
  <widget name="title" position="24,22" size="300,40" font="Regular;30" foregroundColor="#00ffffff" backgroundColor="#00080d1a" transparent="1" />
  <ePixmap pixmap="%(dot)s" position="330,34" size="16,16" alphatest="blend" />
  <widget name="key_green" position="354,26" size="160,32" font="Regular;19" foregroundColor="#00ffffff" backgroundColor="#00080d1a" transparent="1" />
  <widget name="epg" position="24,84" size="472,100" font="Regular;20" foregroundColor="#00c8d2e0" backgroundColor="#00080d1a" transparent="1" />
  <widget name="list" position="16,192" size="488,504" itemHeight="42" font="Regular;23" selectionPixmap="%(sel)s" foregroundColor="#00ffffff" foregroundColorSelected="#00ffffff" backgroundColor="#00080d1a" transparent="1" scrollbarMode="showOnDemand" />
</screen>""" % {"panel": px("panel.png"), "dot": px("key_green.png"), "sel": px("sel_panel.png")}


class ChannelPanel(Screen):
    def __init__(self, session, source, items, current):
        self.skin = scale(_panel_skin())
        Screen.__init__(self, session)
        self.source, self.items = source, items
        self.p_closed = False
        self.onClose.append(self._closed)
        self.cache = {}
        self["title"] = Label("Senderliste")
        self["key_green"] = Label("EPG aktualisieren")
        self["epg"] = Label("")
        self["list"] = PList([(("%s  " % i["number"] if i.get("number") else "") + (i.get("name") or ""), n) for n, i in enumerate(items)])
        self["list"].onSelectionChanged.append(self.changed)
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.ok, "cancel": lambda: self.close(None), "green": self.refresh,
        }, -1)
        self.timer = make_timer(self.load)
        self.onLayoutFinish.append(lambda: (self["list"].moveToIndex(current), self.changed()))

    def _closed(self):
        self.p_closed = True

    def changed(self):
        self["epg"].setText("")
        self.timer.start(400, True)

    def refresh(self):
        self.cache = {}
        self["epg"].setText("EPG wird aktualisiert …")
        self.load()

    def load(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        item = self.items[cur[1]]
        key = item.get("id")
        if key in self.cache:
            self["epg"].setText(self.cache[key])
            return

        def work():
            epg = self.source.short_epg(item)
            now = time.time()
            c = [e for e in epg if e["start"] <= now < e["end"]]
            n = [e for e in epg if e["start"] >= (c[0]["end"] if c else now)]
            return "\n".join(["Jetzt: " + ("%s  %s" % (fmt_time(c[0]["start"]), c[0]["title"]) if c else "Kein Programm gefunden"),
                              "Danach: " + ("%s  %s" % (fmt_time(n[0]["start"]), n[0]["title"]) if n else "–")])

        def done(text):
            self.cache[key] = text
            cur2 = self["list"].getCurrent()
            if cur2 and self.items[cur2[1]].get("id") == key:
                self["epg"].setText(text)
        run_async(self, work, done, lambda m: None)

    def ok(self):
        cur = self["list"].getCurrent()
        self.close(cur[1] if cur else None)


# ---------- Programm eines Senders mit Catch-up ----------
class ProgrammeScreen(Screen):
    def __init__(self, session, source, item):
        from .ui import skin
        self.skin = skin("PortivaProgramme")
        Screen.__init__(self, session)
        self.source, self.item = source, item
        self.p_closed = False
        self.onClose.append(self._closed)
        self.entries = []
        self["title"] = Label("Programm: " + (item.get("name") or ""))
        self["sub"] = Label("► = als Catch-up abspielbar" if item.get("archive") else "Dieser Sender hat kein Archiv (Catch-up)")
        self["status"] = Label("Lade Programm …")
        self["info"] = Label("")
        self["key_red"] = Label("")
        self["key_green"] = Label("EPG aktualisieren")
        self["key_yellow"] = Label("")
        self["key_blue"] = Label("")
        self["list"] = PList([])
        self["list"].onSelectionChanged.append(self.changed)
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.ok, "cancel": lambda: self.close(None), "green": self.load,
        }, -1)
        self.onLayoutFinish.append(self.load)

    def _closed(self):
        self.p_closed = True

    def load(self):
        self["status"].setText("Lade Programm …")
        run_async(self, lambda: self.source.full_epg(self.item) or self.source.short_epg(self.item), self.loaded)

    def loaded(self, epg):
        now = time.time()
        self.entries = [e for e in epg if e["end"] > now - (self.item.get("archive") or 0) * 86400]
        rows, sel = [], 0
        for n, e in enumerate(self.entries):
            live = e["start"] <= now < e["end"]
            can = (e["end"] <= now or live) and self.source.catchup_url(self.item, e["start"], e["end"]) is not None
            if live:
                sel = n
            rows.append(("%s %s  %s%s%s" % (fmt_day(e["start"]), fmt_time(e["start"]), e["title"], "   ● LIVE" if live else "",
                                           "   ►" if can and not live else ""), n))
        self["list"].setList(rows)
        self["list"].moveToIndex(sel)
        self["status"].setText("%d Sendungen" % len(rows) if rows else "Für diesen Sender liefert der Anbieter kein Programm")
        self.changed()

    def changed(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        e = self.entries[cur[1]]
        self["info"].setText("%s, %s – %s\n\n%s\n\n%s" % (fmt_day(e["start"]), fmt_time(e["start"]), fmt_time(e["end"]), e["title"], (e.get("desc") or "")[:700]))

    def ok(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        e = self.entries[cur[1]]
        now = time.time()
        if e["start"] > now:
            self["status"].setText("Diese Sendung läuft noch nicht")
            return
        url = self.source.catchup_url(self.item, e["start"], e["end"])
        if not url:
            if e["end"] > now:
                self.close(None)  # laufende Sendung ohne Archiv: einfach weiter live schauen
            else:
                self["status"].setText("Diese Sendung ist nicht im Archiv des Anbieters")
            return
        # Catch-up (vergangene Sendung bzw. laufende Sendung von Anfang an)
        self.close({"kind": "catchup", "id": url, "url": url, "name": "%s: %s" % (self.item.get("name", ""), e["title"]), "logo": self.item.get("logo")})
