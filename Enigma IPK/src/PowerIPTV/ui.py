# -*- coding: utf-8 -*-
"""
Bildschirme des Plugins (fuer die Fernbedienung am Fernseher gebaut):
Startseite mit Kacheln, Kategorien (Sprache, Favoriten, Zuletzt gesehen, Kindersicherung), Listen
(Jetzt/Danach, Film-Infos, Sortieren, Filtern, Favoriten, Download), Staffeln, Weiterschauen,
Zugaenge (QR anzeigen, vom Handy empfangen), Einstellungen, Update.
"""
import os
import re
import time

from enigma import ePoint, eSize
from Screens.Screen import Screen
from Screens.MessageBox import MessageBox
from Screens.ChoiceBox import ChoiceBox
from Screens.VirtualKeyBoard import VirtualKeyBoard
from Components.ActionMap import ActionMap
from Components.MenuList import MenuList
from Components.Label import Label
from Components.Pixmap import Pixmap
from Components.ProgressBar import ProgressBar
from Components.ConfigList import ConfigListScreen
from Components.config import ConfigText, ConfigPassword, ConfigSelection, ConfigYesNo, getConfigListEntry

from . import store, rules
from .api import make_source, normalize_server
from .common import PLUGIN_DIR, FHD, CACHE, scale, run_async, make_timer, fmt_time, px, PList, fetch_image, show_image, stream_switch, is_stream
from .player import PortivaPlayer

VERSION = "1.1.0"
try:
    with open(PLUGIN_DIR + "version.txt") as _f:
        VERSION = _f.read().strip()
except Exception:
    pass

FAV, RECENT, ALL = "__fav__", "__recent__", "__all__"
TITLES = {"live": "Live TV", "movie": "Filme", "series": "Serien", "episode": "Folgen"}

# ---------- Skin (HD-Masse, bei Full-HD automatisch x1,5; Grafiken aus tools/make_skin.py) ----------
BG = "#00070b16"
CLEAR = "#ff000000"  # durchsichtig (Live-Vorschau scheint durch)
GREY = "#009fb1c9"
CYAN = "#005ec4f2"
TXT = 'foregroundColor="#00ffffff" backgroundColor="#00101b30" transparent="1"'
LIST = ('foregroundColor="#00ffffff" foregroundColorSelected="#00ffffff" backgroundColor="#00101b30" '
        'transparent="1" scrollbarMode="showOnDemand"')
KEYS = "".join("""
  <ePixmap pixmap="%(dot)s" position="%(x)d,667" size="16,16" alphatest="blend" />
  <widget name="key_%(k)s" position="%(tx)d,660" size="270,30" font="Regular;20" %(t)s />""" % {
    "dot": px("key_%s.png" % k), "x": x, "tx": x + 24, "k": k, "t": TXT}
    for k, x in (("red", 40), ("green", 340), ("yellow", 640), ("blue", 940)))


def head(bg="bg_list.png"):
    return """
  <ePixmap pixmap="%(bg)s" position="0,0" size="1280,720" zPosition="-10" alphatest="blend" />
  <ePixmap pixmap="%(logo)s" position="40,24" size="64,64" alphatest="blend" />
  <widget name="title" position="120,20" size="900,44" font="Regular;32" %(t)s />
  <widget name="sub" position="120,64" size="900,28" font="Regular;19" foregroundColor="%(grey)s" backgroundColor="#00101b30" transparent="1" />
  <widget source="global.CurrentTime" render="Label" position="1040,20" size="200,44" font="Regular;32" halign="right" %(t)s>
    <convert type="ClockToText">Default</convert>
  </widget>
  <widget source="global.CurrentTime" render="Label" position="1000,64" size="240,28" font="Regular;19" halign="right" foregroundColor="%(grey)s" backgroundColor="#00101b30" transparent="1">
    <convert type="ClockToText">Format:%%a, %%d.%%m.%%Y</convert>
  </widget>""" % {"bg": px(bg), "t": TXT, "grey": GREY, "logo": PLUGIN_DIR + ("logo_fhd.png" if FHD else "logo.png")}


HEAD = head("bg_full.png")
PIG = '<widget source="session.VideoPicture" render="Pig" position="680,112" size="560,315" zPosition="1" backgroundColor="%s" />' % CLEAR


def skin(name, info=True, config=False, layout=None):
    """layout: None/"list" = Liste + Infospalte, "full" = Liste ueber die ganze Breite,
    "live" = Liste + Live-Vorschau + Programm, "media" = Liste + Cover + Infos,
    "catlive" = Kategorien + laufendes Bild, "catart" = Kategorien + Bild."""
    layout = layout or ("full" if (config or not info) else "list")
    bg, screen_bg = {"full": "bg_full.png", "live": "bg_live.png", "catlive": "bg_live.png"}.get(layout, "bg_list.png"), BG
    if layout in ("live", "catlive"):
        screen_bg = CLEAR
    if config:
        body = ('<widget name="config" position="52,124" size="1176,460" itemHeight="46" font="Regular;24" selectionPixmap="%s" %s />'
                % (px("sel_full.png"), LIST))
    elif layout == "full":
        body = ('<widget name="list" position="52,124" size="1176,460" itemHeight="46" font="Regular;24" selectionPixmap="%s" %s />'
                % (px("sel_full.png"), LIST))
    else:
        body = ('<widget name="list" position="52,124" size="596,460" itemHeight="46" font="Regular;24" selectionPixmap="%s" %s />'
                % (px("sel_list.png"), LIST))
    if layout == "list":
        body += '\n  <widget name="info" position="700,132" size="520,460" font="Regular;21" %s />' % TXT
    elif layout == "media":
        body += ('\n  <widget name="cover" position="700,132" size="180,270" alphatest="blend" scale="1" zPosition="2" />'
                 '\n  <widget name="info" position="896,132" size="330,464" font="Regular;19" %s />' % TXT)
    elif layout == "live":
        body += ("\n  " + PIG +
                 '\n  <widget name="pv_hint" position="680,250" size="560,40" font="Regular;22" halign="center" foregroundColor="%(grey)s" backgroundColor="#00000000" transparent="1" zPosition="2" />'
                 '\n  <widget name="pv_name" position="696,450" size="420,32" font="Regular;24" %(t)s />'
                 '\n  <widget name="pv_live" position="1120,454" size="104,26" font="Regular;17" halign="right" foregroundColor="%(cyan)s" backgroundColor="#00101b30" transparent="1" />'
                 '\n  <widget name="pv_now" position="696,484" size="528,28" font="Regular;19" foregroundColor="%(cyan)s" backgroundColor="#00101b30" transparent="1" />'
                 '\n  <widget name="pv_progress" position="696,516" size="528,6" pixmap="%(prog)s" backgroundColor="#002a3348" borderWidth="0" />'
                 '\n  <widget name="pv_next" position="696,530" size="528,26" font="Regular;18" foregroundColor="#00b0b8c8" backgroundColor="#00101b30" transparent="1" />'
                 '\n  <widget name="pv_desc" position="696,558" size="528,48" font="Regular;16" foregroundColor="%(grey)s" backgroundColor="#00101b30" transparent="1" />'
                 % {"t": TXT, "grey": GREY, "cyan": CYAN, "prog": px("progress_small.png")})
    elif layout == "catlive":
        body += ("\n  " + PIG +
                 '\n  <widget name="info" position="696,452" size="528,150" font="Regular;19" %s />' % TXT)
    elif layout == "catart":
        body += ('\n  <widget name="art" position="770,136" size="380,250" alphatest="blend" zPosition="2" />'
                 '\n  <widget name="info" position="700,410" size="520,190" font="Regular;19" %s />' % TXT)
    return scale("""<screen name="%s" position="0,0" size="1280,720" flags="wfNoBorder" backgroundColor="%s">%s
  %s
  <widget name="status" position="52,622" size="1180,28" font="Regular;19" foregroundColor="%s" backgroundColor="#00101b30" transparent="1" />%s
</screen>""" % (name, screen_bg, head(bg), body, CYAN, KEYS))


class Base(Screen):
    def __init__(self, session, name, title, info=True, config=False, layout=None):
        self.skin = skin(name, info=info, config=config, layout=layout)
        Screen.__init__(self, session)
        self.p_closed = False
        self.onClose.append(self._p_closed)
        self["title"] = Label(title)
        self["sub"] = Label("")
        self["status"] = Label("")
        layout = layout or ("full" if (config or not info) else "list")
        if layout in ("list", "media", "catlive", "catart"):
            self["info"] = Label("")
        if layout == "media":
            self["cover"] = Pixmap()
        if layout == "catart":
            self["art"] = Pixmap()
        if layout == "live":
            for k in ("pv_hint", "pv_name", "pv_live", "pv_now", "pv_next", "pv_desc"):
                self[k] = Label("")
            self["pv_progress"] = ProgressBar()
        for k in ("key_red", "key_green", "key_yellow", "key_blue"):
            self[k] = Label("")
        self.setTitle(title)

    def _p_closed(self):
        self.p_closed = True


def pid():
    return (store.active_profile() or {}).get("id")


def ask_pin(session, callback, title="PIN der Kindersicherung"):
    """PIN abfragen (falls eine gesetzt ist). callback(True/False)."""
    pin = store.settings().get("pin")
    if not pin:
        callback(True)
        return
    from Screens.InputBox import InputBox
    from Components.Input import Input

    def done(text):
        if text is None:
            callback(False)
        elif text == pin:
            callback(True)
        else:
            session.open(MessageBox, "Falsche PIN", MessageBox.TYPE_ERROR, timeout=4)
            callback(False)
    session.openWithCallback(done, InputBox, title=title, text="", type=Input.PIN)


# ---------- Wiedergabe ----------
def play(session, source, items, index):
    item = items[index]
    if item.get("kind") == "series":
        store.add_history(pid(), item)
        session.open(SeasonScreen, source, item)
        return
    live = item.get("kind") == "live"
    if item.get("kind") == "episode" and item.get("series_id"):
        store.add_history(pid(), {"kind": "series", "id": item["series_id"], "name": item.get("series_name", ""), "logo": item.get("logo"), "cat": item.get("cat")})
    else:
        store.add_history(pid(), item)
    if live:
        session.open(PortivaPlayer, source, items, index, True)
        return
    url = item.get("url") or source.stream_url(item, store.settings().get("live_format", "ts"))
    pos = store.resume_get(url)
    if pos <= 0:
        session.open(PortivaPlayer, source, items, index, False)
        return

    def answer(choice):
        if choice is not None:
            session.open(PortivaPlayer, source, items, index, False, choice[1])
    session.openWithCallback(answer, ChoiceBox, title=item.get("name", ""),
                             list=[("Weiterschauen ab %d:%02d" % (pos // 60, pos % 60), pos), ("Von vorne", 0)])


# ---------- Startseite ----------
TILE_BIG = [("LIVE TV", "Sender & Programm", "live", "live_tv.jpg"), ("FILME", "Filme & Neuheiten", "movie", "movies.jpg"),
            ("SERIEN", "Serien & Staffeln", "series", "series.jpg")]
TILE_SMALL = [("Weiterschauen", "continue"), ("Suche", "search"), ("TV-Guide", "guide"), ("Favoriten", "fav"), ("Zuletzt gesehen", "recent"),
              ("Benutzer wechseln", "profiles"), ("Vom Handy empfangen", "receive"), ("Einstellungen", "settings"), ("Update", "update"),
              ("Playlist aktualisieren", "reload")]


def _home_skin():
    parts = []
    for i, t in enumerate(TILE_BIG):
        x = 40 + i * 410
        parts.append('<ePixmap pixmap="%s" position="%d,112" size="380,250" alphatest="blend" zPosition="1" />' % (px("big_%s.png" % t[2]), x))
        parts.append('<widget name="bigt%d" position="%d,282" size="360,44" font="Regular;34" halign="center" foregroundColor="#00ffffff" '
                     'backgroundColor="#00000000" transparent="1" zPosition="3" />' % (i, x + 10))
        parts.append('<widget name="bigs%d" position="%d,324" size="360,28" font="Regular;19" halign="center" foregroundColor="#00d0d8e8" '
                     'backgroundColor="#00000000" transparent="1" zPosition="3" />' % (i, x + 10))
    for i, t in enumerate(TILE_SMALL):
        row, col = divmod(i, 5)
        x, y = 40 + col * 240, 384 + row * 118
        parts.append('<ePixmap pixmap="%s" position="%d,%d" size="222,104" alphatest="blend" zPosition="1" />' % (px("tile_%s.png" % t[1]), x, y))
        parts.append('<widget name="small%d" position="%d,%d" size="206,36" font="Regular;19" halign="center" valign="center" '
                     'foregroundColor="#00ffffff" backgroundColor="#00101b30" transparent="1" zPosition="2" />' % (i, x + 8, y + 58))
    return scale("""<screen name="PortivaHome" position="0,0" size="1280,720" flags="wfNoBorder" backgroundColor="%s">%s
  <widget name="focus_big" position="32,104" size="396,266" pixmap="%s" alphatest="blend" zPosition="5" />
  <widget name="focus_small" position="32,376" size="238,120" pixmap="%s" alphatest="blend" zPosition="5" />
  %s
  <widget name="status" position="40,622" size="1200,28" font="Regular;19" foregroundColor="%s" backgroundColor="#00101b30" transparent="1" />
  <widget name="hint" position="40,662" size="1200,28" font="Regular;18" foregroundColor="%s" backgroundColor="#00101b30" transparent="1" />
</screen>""" % (BG, head("bg.png"), px("focus_big.png"), px("focus_small.png"), "\n  ".join(parts), CYAN, GREY))


class PortivaHome(Screen):
    def __init__(self, session):
        self.skin = _home_skin()
        Screen.__init__(self, session)
        self.p_closed = False
        self.onClose.append(self._p_closed)
        self.source = None
        self.pos = 0  # 0..2 grosse Kacheln, 3.. kleine Kacheln
        self["title"] = Label("PowerIPTV  by Portiva©")
        self["sub"] = Label("")
        self["status"] = Label("")
        self["hint"] = Label("OK öffnen   ·   ROT Benutzer   ·   GRÜN Einstellungen   ·   BLAU Suche   ·   Version " + VERSION)
        self["focus_big"] = Pixmap()
        self["focus_small"] = Pixmap()
        for i, t in enumerate(TILE_BIG):
            self["bigt%d" % i] = Label(t[0])
            self["bigs%d" % i] = Label(t[1])
        for i, t in enumerate(TILE_SMALL):
            self["small%d" % i] = Label(t[0])
        self["actions"] = ActionMap(["OkCancelActions", "DirectionActions", "ColorActions"], {
            "ok": self.open_tile, "cancel": self.close,
            "left": lambda: self.move(0, -1), "right": lambda: self.move(0, 1),
            "up": lambda: self.move(-1, 0), "down": lambda: self.move(1, 0),
            "red": lambda: self.open_key("profiles"), "green": lambda: self.open_key("settings"), "blue": lambda: self.open_key("search"),
        }, -1)
        self.onLayoutFinish.append(self.layout)

    def _p_closed(self):
        self.p_closed = True

    def layout(self):
        self.show_focus()
        # Erst laden, wenn die Startseite wirklich angezeigt wird: Enigma2 erlaubt das Oeffnen weiterer
        # Fenster (z. B. "Zugang hinzufuegen" beim ersten Start) nicht waehrend des Aufbaus -> sonst Absturz.
        self.p_start_timer = make_timer(self.refresh)
        self.p_start_timer.start(50, True)
        if store.settings().get("auto_update", True):
            from . import update
            run_async(self, update.check_cached, self.update_found, lambda m: None)

    def update_found(self, info):
        if info:
            self["small8"].setText("Update %s!" % info["version"])
            self["status"].setText("Neue Version %s verfügbar – Kachel „Update“" % info["version"])

    def refresh(self, *args):
        p = store.active_profile()
        if not p:
            self["sub"].setText("Noch kein Zugang eingerichtet")
            self.session.openWithCallback(self.after_first, AddChooser)
            return
        self.source = make_source(p)
        self["title"].setText("PowerIPTV  by Portiva©  ·  " + p.get("name", ""))
        self["sub"].setText("Verbinde …")
        run_async(self, self.source.account_text, lambda t: self["sub"].setText(t or ""), lambda m: self["sub"].setText("Verbindung fehlgeschlagen: " + m))
        src, profile_id = self.source, pid()

        def adult_scan():
            # Erwachsenen-Kategorien kennen -> solche Titel nie in "Zuletzt gesehen" (auch alte Eintraege entfernen)
            for kind in ("live", "movie", "series"):
                try:
                    store.register_cats(kind, src.categories(kind))
                except Exception:
                    pass
            store.purge_history(profile_id)
        run_async(self, adult_scan, lambda r: None, lambda m: None)

    def after_first(self, *args):
        if store.active_profile():
            self.refresh()
        else:
            self.close()

    # Fokus: Zeile 0 = grosse Kacheln (3), Zeile 1/2 = kleine Kacheln (je 5)
    @staticmethod
    def rect(pos):
        if pos < 3:
            return 40 + pos * 410, 112, 380, 250
        row, col = divmod(pos - 3, 5)
        return 40 + col * 240, 384 + row * 118, 222, 104

    def show_focus(self):
        """Leuchtender Rahmen um die gewaehlte Kachel (eigene Grafik fuer grosse und kleine Kacheln)."""
        x, y, w, h = self.rect(self.pos)
        f = 1.5 if FHD else 1
        big = self.pos < 3
        on, off = ("focus_big", "focus_small") if big else ("focus_small", "focus_big")
        self[off].hide()
        pad = 8
        self[on].instance.move(ePoint(int((x - pad) * f), int((y - pad) * f)))
        self[on].show()

    def move(self, drow, dcol):
        if self.pos < 3:
            row, col = 0, self.pos
        else:
            row, col = 1 + (self.pos - 3) // 5, (self.pos - 3) % 5
        if dcol:
            col = max(0, min((3 if row == 0 else 5) - 1, col + dcol))
        if drow:
            nrow = max(0, min(2, row + drow))
            if nrow != row:
                x, y, w, h = self.rect(self.pos)
                cx = x + w / 2.0
                n = 3 if nrow == 0 else 5
                col = min(range(n), key=lambda c: abs(self.rect(c if nrow == 0 else 3 + (nrow - 1) * 5 + c)[0] +
                                                      self.rect(c if nrow == 0 else 3 + (nrow - 1) * 5 + c)[2] / 2.0 - cx))
                row = nrow
        self.pos = col if row == 0 else 3 + (row - 1) * 5 + col
        self.show_focus()

    def open_tile(self):
        self.open_key(TILE_BIG[self.pos][2] if self.pos < 3 else TILE_SMALL[self.pos - 3][1])

    def open_key(self, key):
        if key not in ("profiles", "receive", "settings", "update") and not self.source:
            return
        if key in ("live", "movie", "series"):
            self.session.open(CategoryScreen, self.source, key)
        elif key == "guide":
            self.session.open(CategoryScreen, self.source, "live", True)
        elif key == "fav":
            favs = store.favorites(pid())
            if not favs:
                self["status"].setText("Noch keine Favoriten – in einer Liste mit GELB hinzufügen")
                return
            self.session.open(ItemScreen, self.source, None, None, "Favoriten", favs)
        elif key == "recent":
            hist = store.history(pid())
            if not hist:
                self["status"].setText("Noch nichts angesehen")
                return
            self.session.open(ItemScreen, self.source, None, None, "Zuletzt gesehen", hist)
        elif key == "continue":
            self.session.open(ContinueScreen, self.source)
        elif key == "search":
            self.search()
        elif key == "profiles":
            self.session.openWithCallback(self.refresh, ProfilesScreen)
        elif key == "receive":
            self.session.openWithCallback(lambda *a: self.refresh(), ReceiveScreen)
        elif key == "settings":
            self.session.open(SettingsScreen)
        elif key == "update":
            from .update import UpdateScreen
            self.session.open(UpdateScreen)
        elif key == "reload":
            self.source = make_source(store.active_profile())
            self["status"].setText("Playlist wird neu geladen …")
            run_async(self, lambda: self.source.categories("live"), lambda c: self["status"].setText("Playlist aktualisiert"))

    def search(self):
        def done(text):
            if not text:
                return
            self["status"].setText("Suche „%s“ …" % text)

            def work():
                out = []
                for kind in ("live", "movie", "series"):
                    try:
                        out += [i for i in self.source.items(kind, None) if rules.matches(i.get("name"), text)]
                    except Exception:
                        pass
                return out[:600]

            def show(res):
                self["status"].setText("" if res else "Nichts gefunden für „%s“" % text)
                if res:
                    self.session.open(ItemScreen, self.source, None, None, "Suche: " + text, res)
            run_async(self, work, show)
        self.session.openWithCallback(done, VirtualKeyBoard, title="Suche (Sender, Filme, Serien)", text="")


# ---------- Kategorien ----------
class CategoryScreen(Base):
    def __init__(self, session, source, kind, guide=False):
        Base.__init__(self, session, "PortivaCategories" + ("Live" if kind == "live" else "Media"), ("TV-Guide" if guide else TITLES[kind]),
                      layout="catlive" if kind == "live" else "catart")
        self.source, self.kind, self.guide = source, kind, guide
        self.cats = []
        self.langs = []
        self.lang = store.settings().get("language", "")
        self["list"] = PList([])
        if kind != "live":
            self.onLayoutFinish.append(lambda: self["art"].instance.setPixmapFromFile(px("big_%s.png" % kind)))
        self["key_red"].setText("Sperren")
        if kind == "live":
            self["key_green"].setText("Als Bouquet")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.open_cat, "cancel": self.close, "green": self.bouquet, "yellow": self.next_lang, "red": self.toggle_lock,
        }, -1)
        if guide:
            self["sub"].setText("Kategorie wählen – in der Senderliste zeigt INFO das Programm mit Catch-up")
        self["status"].setText("Lade Kategorien …")
        run_async(self, lambda: source.categories(kind), self.loaded)

    def loaded(self, cats):
        self.cats = cats
        store.register_cats(self.kind, cats)
        self.langs = rules.detect_languages(cats)
        if self.lang and self.lang not in self.langs:
            self.lang = ""
        self.build()

    def build(self):
        p = pid()
        shown = [c for c in self.cats if not self.lang or rules.category_language(c["name"]) in (self.lang, None)]
        rows = [("♥  Favoriten", FAV), ("Zuletzt gesehen", RECENT), ("Alle Sender" if self.kind == "live" else "Alle", ALL)]
        for c in shown:
            name = rules.strip_language(c["name"]) if self.lang else c["name"]
            rows.append((name + ("   (gesperrt)" if store.is_locked(p, self.kind, c) else ""), c["id"]))
        self["list"].setList(rows)
        self["status"].setText("%d Kategorien%s" % (len(shown), ("  ·  Sprache: " + self.lang) if self.lang else ""))
        tips = ["%d Kategorien" % len(shown), ""]
        if self.langs:
            tips.append("GELB  Sprache wählen (%s)" % (self.lang or "alle"))
        tips.append("ROT  Kategorie mit PIN sperren")
        if self.kind == "live":
            tips.append("GRÜN  Kategorie als Bouquet übernehmen")
        self["info"].setText("\n".join(tips))
        self["key_yellow"].setText(("Sprache: " + (self.lang or "Alle")) if self.langs else "")

    def next_lang(self):
        if not self.langs:
            return
        opts = [""] + self.langs
        self.lang = opts[(opts.index(self.lang) + 1) % len(opts)] if self.lang in opts else ""
        s = store.settings()
        s["language"] = self.lang
        store.save_settings(s)
        self.build()

    def cat_of(self, cid):
        return next((c for c in self.cats if c["id"] == cid), None)

    def open_cat(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        name, cid = cur
        p = pid()
        if cid == FAV:
            items = [i for i in store.favorites(p) if i.get("kind") == self.kind]
            self.session.open(ItemScreen, self.source, self.kind, None, TITLES[self.kind] + " – Favoriten", items)
            return
        if cid == RECENT:
            items = [i for i in store.history(p) if i.get("kind") == self.kind]
            self.session.open(ItemScreen, self.source, self.kind, None, TITLES[self.kind] + " – Zuletzt gesehen", items)
            return
        cat = self.cat_of(cid)

        def go(ok):
            if ok:
                self.session.open(ItemScreen, self.source, self.kind, None if cid == ALL else cid, name.replace("   (gesperrt)", ""))
        if cat and store.is_locked(p, self.kind, cat):
            ask_pin(self.session, go)
        else:
            go(True)

    def toggle_lock(self):
        cur = self["list"].getCurrent()
        cat = self.cat_of(cur[1]) if cur else None
        if not cat:
            return
        if not store.settings().get("pin"):
            self["status"].setText("Zuerst in den Einstellungen eine PIN für die Kindersicherung festlegen")
            return

        def go(ok):
            if ok:
                locked = store.toggle_lock(pid(), self.kind, cat)
                self["status"].setText(("„%s“ ist jetzt gesperrt" if locked else "„%s“ ist wieder frei") % cat["name"])
                idx = self["list"].getSelectionIndex()
                self.build()
                self["list"].moveToIndex(idx)
        ask_pin(self.session, go)

    def bouquet(self):
        """Live-Kategorie als Bouquet in die normale Senderliste von Enigma2 uebernehmen."""
        cur = self["list"].getCurrent()
        if self.kind != "live" or not cur or cur[1] in (FAV, RECENT):
            return
        from .bouquet import export_bouquet
        name, cid = cur[0].replace("   (gesperrt)", ""), cur[1]
        self["status"].setText("Lege Bouquet „%s“ an …" % name)

        def work():
            return export_bouquet(name, self.source.items("live", None if cid == ALL else cid), self.source)
        run_async(self, work, lambda n: self["status"].setText("Bouquet „PowerIPTV - %s“ mit %d Sendern angelegt" % (name, n)))


# ---------- Sender / Filme / Serien ----------
class ItemScreen(Base):
    def __init__(self, session, source, kind, cat_id, title, items=None):
        self.p_live = kind == "live"
        Base.__init__(self, session, "PortivaItemsLive" if self.p_live else "PortivaItemsMedia", title,
                      layout="live" if self.p_live else "media")
        self.source, self.kind, self.cat_id = source, kind, cat_id
        self.all = []
        self.shown = []
        self.filter = ""
        self.sort = store.settings().get("sort", "DEFAULT")
        self.pid = pid()
        self["list"] = PList([])
        self["list"].onSelectionChanged.append(self.selection_changed)
        self["key_red"].setText("Sortieren")
        self["key_green"].setText("Mehr (MENU)")
        self["key_yellow"].setText("Favorit")
        self["key_blue"].setText("Filtern")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions", "MenuActions", "InfobarEPGActions", "EPGSelectActions"], {
            "ok": self.ok, "cancel": self.close, "yellow": self.fav, "blue": self.ask_filter, "red": self.ask_sort,
            "green": self.menu, "menu": self.menu, "showEventInfo": self.programme, "info": self.programme,
        }, -1)
        self.info_timer = make_timer(self.load_info)
        self.info_cache = {}
        # Live-Vorschau: markierten Sender nach kurzem Verweilen klein rechts oben abspielen
        self.pv_timer = make_timer(self.preview)
        self.pv_on = self.p_live and store.settings().get("preview", True)
        self.pv_ref = None       # was gerade in der Vorschau laeuft
        self.pv_prev = None      # was vorher lief (wird beim Schliessen wiederhergestellt)
        self.pv_started = False
        self.pv_last = 0.0       # Zeitpunkt der letzten Vorschau-Verbindung (Puffer gegen zu schnelles Umschalten)
        self.open_timer = make_timer(self.open_pending)
        self.open_item = None
        if self.p_live:
            self["pv_hint"].setText("Live-Vorschau …" if self.pv_on else "")
            self["pv_progress"].hide()
            if self.pv_on:
                run_async(self, source.connections, self.check_connections, lambda m: None)
        self.onClose.append(self.p_cleanup)
        self.onExecBegin.append(self.p_resume)
        if items is not None:
            self.loaded(items)
        else:
            self["status"].setText("Lade …")
            run_async(self, lambda: source.items(kind, cat_id), self.loaded)

    def label(self, i):
        fav = "♥ " if self.pid and store.is_favorite(self.pid, i) else ""
        num = ("%s  " % i["number"]) if i.get("kind") == "live" and i.get("number") else ""
        tag = {"live": "  [Sender]", "movie": "  [Film]", "series": "  [Serie]"}.get(i.get("kind"), "") if self.kind is None else ""
        arch = "  (Archiv)" if i.get("kind") == "live" and i.get("archive") else ""
        return fav + num + (i.get("name") or "") + tag + arch

    def loaded(self, items):
        # Kindersicherung: Erwachseneninhalte in Listen ausblenden, solange gesperrt
        s = store.settings()
        if s.get("pin") and s.get("auto_adult"):
            items = [i for i in items if not rules.is_adult(i.get("name"))]
        self.all = items
        self.apply()

    def apply(self):
        lst = [i for i in self.all if rules.matches(i.get("name"), self.filter)] if self.filter else list(self.all)
        self.shown = rules.apply_sort(lst, self.sort) if self.kind != "episode" else lst
        self["list"].setList([(self.label(i), n) for n, i in enumerate(self.shown)])
        sort_name = dict(rules.SORTS).get(self.sort, "")
        self["status"].setText("%d Einträge%s%s" % (len(self.shown), ("  ·  Filter: " + self.filter) if self.filter else "",
                                                     ("  ·  " + sort_name) if self.sort != "DEFAULT" and self.kind != "episode" else ""))
        self.selection_changed()

    def current(self):
        cur = self["list"].getCurrent()
        return (cur[1], self.shown[cur[1]]) if cur and cur[1] < len(self.shown) else (None, None)

    def selection_changed(self):
        if self.p_closed:
            return
        n, item = self.current()
        if self.p_live:
            self["pv_name"].setText(item.get("name", "") if item else "")
            self["pv_now"].setText("")
            self["pv_next"].setText("")
            self["pv_desc"].setText("")
            self["pv_progress"].hide()
            if self.pv_on and item is not None:
                self.pv_timer.start(1500, True)  # erst verbinden, wenn man kurz auf dem Sender stehen bleibt
        elif item is None:
            self["info"].setText("")
        else:
            self["info"].setText(item.get("name", ""))
            self["cover"].hide()
        if item is not None:
            self.info_timer.start(500, True)  # erst nach kurzer Pause nachladen (schnelles Blaettern)

    # ---------- Live-Vorschau (mit Verbindungsschutz) ----------
    def check_connections(self, res):
        """Vorschau pausieren, wenn der Zugang keine freie Verbindung mehr hat (z. B. anderes Geraet schaut)."""
        active, maximum = res
        if not maximum or self.p_closed:
            return
        own = 1 if is_stream(stream_switch(self.session).current()) else 0
        if active - own >= maximum:
            self.pv_on = False
            self.pv_timer.stop()
            self["pv_hint"].setText("Vorschau pausiert – alle %d Verbindung(en) belegt" % maximum)

    def url_of(self, item):
        return item.get("url") or self.source.stream_url(item, store.settings().get("live_format", "ts"))

    def preview(self):
        n, item = self.current()
        if self.p_closed or not self.pv_on or item is None or item.get("kind") != "live":
            return
        # Puffer: hoechstens alle 3 Sekunden eine neue Verbindung, auch beim schnellen Blaettern
        wait = 3.0 - (time.time() - self.pv_last)
        if wait > 0:
            self.pv_timer.start(int(wait * 1000) + 50, True)
            return
        from .player import make_ref
        url = self.url_of(item)
        if url == self.pv_ref:
            return
        sw = stream_switch(self.session)
        if not self.pv_started:
            self.pv_prev = sw.current()
            self.pv_started = True
        self.pv_ref = url
        self.pv_last = time.time()
        self["pv_hint"].setText("")
        self["pv_live"].setText("● LIVE")
        sw.play(make_ref(url, item.get("name", "")))  # schliesst den alten Stream und wartet kurz

    def p_resume(self):
        """Zurueck aus dem Player: Vorschau wieder aufnehmen (der Player hat den Stream evtl. beendet)."""
        if self.pv_on and self.pv_started and not self.p_closed:
            self.pv_ref = None
            self.pv_timer.start(1500, True)

    def p_cleanup(self):
        """Beim Schliessen: Timer stoppen und das vorherige Programm wieder einschalten."""
        self.info_timer.stop()
        self.pv_timer.stop()
        self.open_timer.stop()
        if self.pv_started:
            try:
                sw = stream_switch(self.session)
                if self.pv_prev is not None:
                    sw.play(self.pv_prev)
                else:
                    sw.stop()
            except Exception:
                pass

    def load_info(self):
        n, item = self.current()
        if item is None or self.p_closed:
            return
        key = "%s:%s" % (item.get("kind"), item.get("id"))
        if key in self.info_cache:
            self.show_info(item, self.info_cache[key])
            return
        kind = item.get("kind")
        if kind == "live":
            def work():
                epg = self.source.short_epg(item)
                now = time.time()
                cur = [e for e in epg if e["start"] <= now < e["end"]]
                nxt = [e for e in epg if e["start"] >= (cur[0]["end"] if cur else now)]
                return {"cur": cur[0] if cur else None, "next": nxt[0] if nxt else None, "logo": fetch_image(item.get("logo"))}
        elif kind == "movie":
            def work():
                i = self.source.vod_info(item)
                parts = [item.get("name", "")]
                meta = " · ".join([str(x) for x in (i.get("year"), i.get("genre"), i.get("duration"),
                                                    ("Bewertung %s" % i["rating"]) if i.get("rating") else None) if x])
                if meta:
                    parts += ["", meta]
                if i.get("plot"):
                    parts += ["", i["plot"][:600]]
                if i.get("cast"):
                    parts += ["", "Mit: " + i["cast"][:160]]
                pos = store.resume_get(item.get("url") or self.source.stream_url(item))
                if pos > 0:
                    parts += ["", "Weiterschauen ab %d:%02d" % (pos // 60, pos % 60)]
                return {"text": "\n".join(parts), "logo": fetch_image(i.get("cover") or item.get("logo"))}
        else:
            def work():
                text = "\n".join([x for x in (item.get("name", ""), "", (item.get("plot") or "")[:600]) if x is not None])
                return {"text": text, "logo": fetch_image(item.get("logo"))}

        def show(data):
            self.info_cache[key] = data
            if not self.p_closed and self.current()[1] is item:
                self.show_info(item, data)
        run_async(self, work, show, lambda m: None)

    def show_info(self, item, data):
        if self.p_live:
            cur, nxt = data.get("cur"), data.get("next")
            if cur:
                now = time.time()
                self["pv_now"].setText("%s – %s   %s" % (fmt_time(cur["start"]), fmt_time(cur["end"]), cur["title"]))
                self["pv_progress"].setValue(int(100 * (now - cur["start"]) / max(1, cur["end"] - cur["start"])))
                self["pv_progress"].show()
                self["pv_desc"].setText((cur.get("desc") or "")[:160])
            else:
                self["pv_now"].setText("Kein Programm gefunden")
                self["pv_progress"].hide()
                self["pv_desc"].setText("INFO = Programm" + (" mit Catch-up" if item.get("archive") else ""))
            self["pv_next"].setText(("Danach: %s   %s" % (fmt_time(nxt["start"]), nxt["title"])) if nxt else "")
            return
        if item.get("kind") == "live":  # Sender in gemischten Listen (Favoriten, Zuletzt, Suche)
            cur, nxt = data.get("cur"), data.get("next")
            lines = [item.get("name", ""), ""]
            if cur:
                lines += ["Jetzt: %s – %s" % (fmt_time(cur["start"]), fmt_time(cur["end"])), cur["title"], ""]
            if nxt:
                lines += ["Danach: %s  %s" % (fmt_time(nxt["start"]), nxt["title"])]
            text = "\n".join(lines)
        else:
            text = data.get("text", "")
        self["info"].setText(text)
        show_image(self["cover"], data.get("logo"))

    def ok(self):
        n, item = self.current()
        if item is None:
            return
        self.pv_timer.stop()
        if self.pv_started and item.get("kind") == "live" and self.url_of(item) == self.pv_ref:
            play(self.session, self.source, self.shown, n)  # laeuft schon in der Vorschau -> keine neue Verbindung
            return
        if self.pv_started:
            # anderer Sender/Film: Vorschau-Stream erst schliessen und kurz warten, dann oeffnen
            sw = stream_switch(self.session)
            sw.stop()
            self.pv_ref = None
            self.open_item = n
            self.open_timer.start(max(100, sw.wait_ms()), True)
            return
        play(self.session, self.source, self.shown, n)

    def open_pending(self):
        n, self.open_item = self.open_item, None
        if n is not None and not self.p_closed and n < len(self.shown):
            play(self.session, self.source, self.shown, n)

    def programme(self):
        n, item = self.current()
        if item is None or item.get("kind") != "live":
            return
        from .player import ProgrammeScreen, DirectSource

        def chosen(res):
            if res:
                self.session.open(PortivaPlayer, DirectSource(), [res], 0, False)
        self.session.openWithCallback(chosen, ProgrammeScreen, self.source, item)

    def fav(self):
        n, item = self.current()
        if item is None or not self.pid:
            return
        added = store.toggle_favorite(self.pid, item)
        self["status"].setText(("♥ „%s“ zu Favoriten hinzugefügt" if added else "„%s“ aus Favoriten entfernt") % item.get("name", ""))
        idx = self["list"].getSelectionIndex()
        self["list"].setList([(self.label(i), k) for k, i in enumerate(self.shown)])
        self["list"].moveToIndex(idx)

    def ask_filter(self):
        def done(text):
            if text is None:
                return
            self.filter = text.strip()
            self.apply()
        self.session.openWithCallback(done, VirtualKeyBoard, title="In dieser Liste filtern", text=self.filter)

    def ask_sort(self):
        def done(c):
            if c:
                self.sort = c[1]
                s = store.settings()
                s["sort"] = self.sort
                store.save_settings(s)
                self.apply()
        self.session.openWithCallback(done, ChoiceBox, title="Sortieren", list=[(("► " if k == self.sort else "") + l, k) for k, l in rules.SORTS])

    def menu(self):
        n, item = self.current()
        if item is None:
            return
        opts = [("Abspielen", "play"), ("Favorit an/aus", "fav")]
        if item.get("kind") in ("movie", "episode"):
            opts.append(("Herunterladen (auf Festplatte/USB des Receivers)", "download"))
        if item.get("kind") == "live":
            opts.append(("Programm / Catch-up (INFO)", "programme"))
        opts += [("Sortieren", "sort"), ("Filtern", "filter")]

        def done(c):
            if not c:
                return
            k = c[1]
            if k == "play":
                self.ok()
            elif k == "fav":
                self.fav()
            elif k == "programme":
                self.programme()
            elif k == "sort":
                self.ask_sort()
            elif k == "filter":
                self.ask_filter()
            elif k == "download":
                from . import download
                url = item.get("url") or self.source.stream_url(item)
                self["status"].setText(download.start(item.get("name", "Film"), url, item.get("ext") or "mp4"))
        self.session.openWithCallback(done, ChoiceBox, title=item.get("name", ""), list=opts)


# ---------- Weiterschauen ----------
class ContinueScreen(ItemScreen):
    def __init__(self, session, source):
        items = []
        for e in store.continue_watching(pid()):
            it = dict(e["item"])
            it["_resume"] = e
            items.append(it)
        ItemScreen.__init__(self, session, source, None, None, "Weiterschauen", items)
        if not items:
            self["status"].setText("Noch nichts angefangen – Filme und Folgen merken sich automatisch die Stelle")

    def label(self, i):
        e = i.get("_resume") or {}
        pct = int(100 * e.get("pos", 0) / e["total"]) if e.get("total") else 0
        return "%s   (%d%% gesehen)" % (i.get("name") or "", pct)


# ---------- Serien: Staffeln ----------
class SeasonScreen(Base):
    def __init__(self, session, source, series):
        Base.__init__(self, session, "PortivaSeasons", series.get("name", "Serie"))
        self.source, self.series = source, series
        self.seasons = {}
        self["list"] = PList([])
        self["actions"] = ActionMap(["OkCancelActions"], {"ok": self.ok, "cancel": self.close}, -1)
        self["status"].setText("Lade Staffeln …")
        run_async(self, lambda: source.series_info(series), self.loaded)

    def loaded(self, info):
        self.seasons = info.get("seasons") or {}
        for lst in self.seasons.values():
            for e in lst:
                e["series_id"] = self.series.get("id")
                e["series_name"] = self.series.get("name")
                e["cat"] = self.series.get("cat")
                e.setdefault("logo", self.series.get("logo"))
        self["info"].setText((info.get("plot") or self.series.get("plot") or "")[:900])
        keys = sorted(self.seasons.keys())
        self["list"].setList([("Staffel %d   (%d Folgen)" % (k, len(self.seasons[k])), k) for k in keys])
        self["status"].setText(("%d Staffeln" % len(keys)) if keys else "Keine Folgen gefunden")

    def ok(self):
        cur = self["list"].getCurrent()
        if cur:
            self.session.open(ItemScreen, self.source, "episode", None, "%s – Staffel %d" % (self.series.get("name", ""), cur[1]), self.seasons[cur[1]])


# ---------- Zugaenge ----------
class AddChooser(Base):
    """Neuer Zugang: per QR vom Handy oder manuell."""

    def __init__(self, session):
        Base.__init__(self, session, "PortivaAdd", "Zugang hinzufügen", info=True)
        self["list"] = PList([("Vom Handy empfangen (QR-Code scannen)", "receive"), ("Manuell eingeben (Xtream Codes / M3U)", "manual")])
        self["info"].setText("Am schnellsten: Auf dem Handy in Portiva unter „Benutzer wechseln“ beim Zugang auf das QR-Symbol tippen → "
                             "„An TV-Stick / Fernseher senden“ und den Code scannen, der hier gleich erscheint.\n\n"
                             "Handy und Receiver müssen im selben Heimnetz sein.")
        self["actions"] = ActionMap(["OkCancelActions"], {"ok": self.ok, "cancel": self.close}, -1)

    def ok(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        if cur[1] == "receive":
            self.session.openWithCallback(self.done, ReceiveScreen)
        else:
            self.session.openWithCallback(self.done, ProfileEdit, None)

    def done(self, *args):
        if store.active_profile():
            self.close(True)


class ProfilesScreen(Base):
    def __init__(self, session):
        Base.__init__(self, session, "PortivaProfiles", "Benutzer wechseln", info=True)
        self["list"] = PList([])
        self["key_red"].setText("Löschen")
        self["key_green"].setText("Neu")
        self["key_yellow"].setText("Bearbeiten")
        self["key_blue"].setText("QR anzeigen")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.activate, "cancel": self.close, "red": self.delete, "green": self.add, "yellow": self.edit_current, "blue": self.qr,
        }, -1)
        self["info"].setText("OK = diesen Zugang benutzen\n\nGRÜN = neuer Zugang (manuell oder vom Handy)\n\n"
                             "BLAU = Zugang als QR-Code für Handy/Tablet anzeigen (nur dir selbst zeigen – enthält die Zugangsdaten)")
        self.onShown.append(self.reload)

    def reload(self):
        d = store.profiles()
        self["list"].setList([(("► " if p.get("id") == d.get("active") else "    ") + p.get("name", "Zugang") +
                               ("   (M3U)" if p.get("type") == "m3u" else "   (Xtream · %s)" % p.get("username", "")), p["id"]) for p in d["list"]])

    def current_profile(self):
        cur = self["list"].getCurrent()
        return next((p for p in store.profiles()["list"] if cur and p.get("id") == cur[1]), None)

    def activate(self):
        p = self.current_profile()
        if p:
            store.activate(p["id"])
            self.close(True)

    def add(self):
        self.session.openWithCallback(lambda *a: self.reload(), AddChooser)

    def edit_current(self):
        p = self.current_profile()
        if p:
            self.session.openWithCallback(lambda *a: self.reload(), ProfileEdit, p)

    def qr(self):
        p = self.current_profile()
        if p:
            from . import link
            self.session.open(QrScreen, "Zugang „%s“" % p.get("name", ""), link.account_qr(p),
                              "Auf dem Handy/Tablet in Portiva:\nBenutzer wechseln → Neuer Zugang → „QR-Code scannen“.\n\n"
                              "Nur dir selbst zeigen – der Code enthält die Zugangsdaten.")

    def delete(self):
        p = self.current_profile()
        if not p:
            return

        def yes(ok):
            if ok:
                store.delete_profile(p["id"])
                self.reload()
        self.session.openWithCallback(yes, MessageBox, "Zugang „%s“ löschen?" % p.get("name", ""), MessageBox.TYPE_YESNO)


QR_SKIN = """<screen name="PortivaQr" position="0,0" size="1280,720" flags="wfNoBorder" backgroundColor="%s">%s
  <eLabel position="40,110" size="380,380" backgroundColor="#00ffffff" />
  <widget name="qr" position="50,120" size="360,360" alphatest="off" zPosition="1" />
  <widget name="info" position="460,110" size="780,380" font="Regular;26" foregroundColor="#00ffffff" backgroundColor="%s" transparent="1" />
  <widget name="status" position="40,520" size="1200,80" font="Regular;24" foregroundColor="#005ec4f2" backgroundColor="%s" transparent="1" />
  <widget name="hint" position="40,640" size="1200,30" font="Regular;22" foregroundColor="#009fb1c9" backgroundColor="%s" transparent="1" />
</screen>"""


class QrScreen(Screen):
    def __init__(self, session, title, text, info):
        self.skin = scale(QR_SKIN % (BG, HEAD, BG, BG, BG))
        Screen.__init__(self, session)
        self.text = text
        self["title"] = Label(title)
        self["sub"] = Label("")
        self["info"] = Label(info)
        self["status"] = Label("")
        self["hint"] = Label("EXIT = schließen")
        self["qr"] = Pixmap()
        self["actions"] = ActionMap(["OkCancelActions"], {"ok": self.close, "cancel": self.close}, -1)
        self.onLayoutFinish.append(self.draw)

    def draw(self):
        try:
            from .qrimage import write_qr_png
            if not os.path.isdir(CACHE):
                os.makedirs(CACHE)
            path = os.path.join(CACHE, "qr_%d.png" % int(time.time() * 1000))
            write_qr_png(self.text, path, 540 if FHD else 360)
            self["qr"].instance.setPixmapFromFile(path)
        except Exception as e:
            self["status"].setText("QR-Code kann auf diesem Receiver nicht angezeigt werden (%s)" % e)


class ReceiveScreen(QrScreen):
    """Dieser Receiver zeigt einen Empfangs-Code; das Handy scannt ihn und schickt den Zugang."""

    def __init__(self, session):
        from . import link
        self.code = link.new_code()
        QrScreen.__init__(self, session, "Zugang vom Handy empfangen", link.pair_qr(self.code),
                          "Auf dem Handy in Portiva:\n1. Benutzer wechseln\n2. beim gewünschten Zugang auf das QR-Symbol tippen\n"
                          "3. „An TV-Stick / Fernseher senden“\n4. diesen Code scannen\n\nHandy und Receiver im selben Heimnetz.")
        self["status"].setText("Warte auf Zugang …   Code %s   ·   %s" % (self.code, link.local_ip() or "kein Netzwerk"))
        link.set_pairing(self.code, self.received)
        self.onClose.append(lambda: link.set_pairing(None, None))
        if not link.port():
            self["status"].setText("Empfang nicht möglich: Port %d ist belegt" % link.LINK_PORT)

    def received(self, profile):
        self.session.open(MessageBox, "Zugang „%s“ übernommen" % profile.get("name", ""), MessageBox.TYPE_INFO, timeout=5)
        self.close(True)


class ProfileEdit(ConfigListScreen, Base):
    def __init__(self, session, profile):
        Base.__init__(self, session, "PortivaProfileEdit", "Zugang bearbeiten" if profile else "Zugang hinzufügen", info=False, config=True)
        p = profile or {}
        self.profile = profile
        self.c_type = ConfigSelection([("xtream", "Xtream Codes"), ("m3u", "M3U-Link")], default=p.get("type", "xtream"))
        self.c_name = ConfigText(default=p.get("name", ""), fixed_size=False)
        self.c_server = ConfigText(default=p.get("server", "http://"), fixed_size=False)
        self.c_user = ConfigText(default=p.get("username", ""), fixed_size=False)
        self.c_pass = ConfigPassword(default=p.get("password", ""), fixed_size=False)
        self.c_m3u = ConfigText(default=p.get("m3u", "http://"), fixed_size=False)
        ConfigListScreen.__init__(self, [], session=session)
        self["key_red"].setText("Abbrechen")
        self["key_green"].setText("Prüfen & speichern")
        self["key_yellow"].setText("Tastatur")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.keyboard, "cancel": self.close, "red": self.close, "green": self.save, "yellow": self.keyboard,
        }, -2)
        self.c_type.addNotifier(self.build, initial_call=False)
        self.onClose.append(lambda: self.c_type.removeNotifier(self.build))
        self.build()
        self["status"].setText("OK oder GELB öffnet die Tastatur · GRÜN prüft die Verbindung und speichert")

    def build(self, *args):
        lst = [getConfigListEntry("Art des Zugangs", self.c_type), getConfigListEntry("Name (beliebig, z.B. Wohnzimmer)", self.c_name)]
        if self.c_type.value == "xtream":
            lst += [getConfigListEntry("Server-Adresse (z.B. http://anbieter.tv:8080)", self.c_server),
                    getConfigListEntry("Benutzername", self.c_user), getConfigListEntry("Passwort", self.c_pass)]
        else:
            lst += [getConfigListEntry("M3U-Link", self.c_m3u)]
        self["config"].list = lst
        self["config"].l.setList(lst)

    def keyboard(self):
        cur = self["config"].getCurrent()
        if not cur or not isinstance(cur[1], ConfigText):
            return
        el = cur[1]

        def done(text):
            if text is not None:
                el.value = text
                self["config"].invalidateCurrent()
        self.session.openWithCallback(done, VirtualKeyBoard, title=cur[0], text=el.value)

    def save(self):
        p = dict(self.profile or {})
        p["type"] = self.c_type.value
        if p["type"] == "xtream":
            p.update({"server": normalize_server(self.c_server.value), "username": self.c_user.value.strip(), "password": self.c_pass.value})
            if not p["username"] or not p["password"] or p["server"] in ("", "http://"):
                self["status"].setText("Bitte Server, Benutzername und Passwort eingeben")
                return
        else:
            p["m3u"] = self.c_m3u.value.strip()
            if not p["m3u"].lower().startswith("http"):
                self["status"].setText("Bitte einen gültigen M3U-Link eingeben")
                return
        p["name"] = self.c_name.value.strip() or (re.sub(r"^https?://", "", p.get("server", "")).split(":")[0] if p["type"] == "xtream" else "M3U-Playlist")
        self["status"].setText("Prüfe Verbindung …")
        run_async(self, lambda: make_source(p).authenticate(), lambda r: self.saved(p),
                  lambda m: self["status"].setText("Verbindung fehlgeschlagen: " + m))

    def saved(self, p):
        store.save_profile(p)
        self.close(True)


# ---------- Einstellungen ----------
class SettingsScreen(ConfigListScreen, Base):
    def __init__(self, session):
        Base.__init__(self, session, "PortivaSettings", "Einstellungen", info=False, config=True)
        s = store.settings()
        self.old_pin = s.get("pin", "")
        self.c_player = ConfigSelection([("auto", "Automatisch (exteplayer3 wenn vorhanden)"), ("4097", "GStreamer (4097)"),
                                         ("5001", "GStreamer über ServiceApp (5001)"), ("5002", "exteplayer3 über ServiceApp (5002)")], default=s["player"])
        self.c_live = ConfigSelection([("ts", "MPEG-TS (Standard)"), ("m3u8", "HLS (m3u8)")], default=s["live_format"])
        self.c_name = ConfigText(default=s.get("device_name") or "Enigma2-Receiver", fixed_size=False)
        self.c_pin = ConfigText(default=self.old_pin, fixed_size=False)
        self.c_adult = ConfigYesNo(default=bool(s.get("auto_adult", True)))
        self.c_update = ConfigYesNo(default=bool(s.get("auto_update", True)))
        self.c_preview = ConfigYesNo(default=bool(s.get("preview", True)))
        ConfigListScreen.__init__(self, [
            getConfigListEntry("Player", self.c_player),
            getConfigListEntry("Live-TV-Format", self.c_live),
            getConfigListEntry("Live-Vorschau in der Senderliste", self.c_preview),
            getConfigListEntry("Name dieses Receivers (für „An Gerät senden“)", self.c_name),
            getConfigListEntry("PIN der Kindersicherung (leer = aus)", self.c_pin),
            getConfigListEntry("Erwachseneninhalte automatisch sperren", self.c_adult),
            getConfigListEntry("Automatisch nach Updates suchen", self.c_update),
        ], session=session)
        self["key_red"].setText("Abbrechen")
        self["key_green"].setText("Speichern")
        self["key_yellow"].setText("Tastatur")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.keyboard, "cancel": self.close, "red": self.close, "green": self.save, "yellow": self.keyboard,
        }, -2)
        self["status"].setText("Ruckelt es oder fehlt Bild/Ton: anderen Player bzw. HLS probieren")

    def keyboard(self):
        cur = self["config"].getCurrent()
        if not cur or not isinstance(cur[1], ConfigText):
            return
        el = cur[1]

        def done(text):
            if text is not None:
                el.value = text
                self["config"].invalidateCurrent()
        self.session.openWithCallback(done, VirtualKeyBoard, title=cur[0], text=el.value)

    def save(self):
        new_pin = re.sub(r"\D", "", self.c_pin.value or "")[:8]

        def go(ok):
            if not ok:
                return
            s = store.settings()
            s.update({"player": self.c_player.value, "live_format": self.c_live.value, "device_name": self.c_name.value.strip() or "Enigma2-Receiver",
                      "pin": new_pin, "auto_adult": bool(self.c_adult.value), "auto_update": bool(self.c_update.value),
                      "preview": bool(self.c_preview.value)})
            store.save_settings(s)
            self.close()
        # PIN aendern/entfernen nur mit der alten PIN
        if self.old_pin and new_pin != self.old_pin:
            ask_pin(self.session, go, "Bisherige PIN eingeben")
        else:
            go(True)
