# -*- coding: utf-8 -*-
"""Bildschirme des Plugins: Start, Kategorien, Sender/Filme/Serien, Staffeln, Zugaenge, Einstellungen."""
import re
import time

from enigma import getDesktop, eTimer
from twisted.internet import threads
from Screens.Screen import Screen
from Screens.MessageBox import MessageBox
from Screens.ChoiceBox import ChoiceBox
from Screens.VirtualKeyBoard import VirtualKeyBoard
from Components.ActionMap import ActionMap
from Components.MenuList import MenuList
from Components.Label import Label
from Components.ConfigList import ConfigListScreen
from Components.config import ConfigText, ConfigPassword, ConfigSelection, getConfigListEntry
from Tools.Directories import resolveFilename, SCOPE_PLUGINS

from . import store
from .api import make_source, normalize_server
from .player import PortivaPlayer

PLUGIN_DIR = resolveFilename(SCOPE_PLUGINS, "Extensions/PowerIPTV/")
VERSION = "1.1.0"
try:
    with open(PLUGIN_DIR + "version.txt") as _f:
        VERSION = _f.read().strip()
except Exception:
    pass

# ---------- Skin (HD-Masse, bei Full-HD automatisch x1,5) ----------
FHD = getDesktop(0).size().width() >= 1920


def scale(xml):
    if not FHD:
        return xml

    def num(m):
        return str(int(round(int(m.group(0)) * 1.5)))

    def attr(m):
        return m.group(1) + re.sub(r"\d+", num, m.group(2)) + m.group(3)
    return re.sub(r'((?:position|size|itemHeight|font)=")([^"]*)(")', attr, xml)


COLORS = 'backgroundColor="#000b1020" foregroundColor="#00ffffff"'
LIST = 'backgroundColor="#00111827" foregroundColor="#00ffffff" backgroundColorSelected="#001e5bd8" foregroundColorSelected="#00ffffff" scrollbarMode="showOnDemand"'


def skin(name, title_text=True, info=True, config=False):
    lst = ('<widget name="config" position="40,110" size="1200,470" itemHeight="44" font="Regular;26" %s />' % LIST) if config else \
          ('<widget name="list" position="40,110" size="%s,470" itemHeight="44" font="Regular;26" %s />' % ("600" if info else "1200", LIST))
    xml = """
<screen name="%(name)s" position="0,0" size="1280,720" flags="wfNoBorder" %(colors)s>
  <eLabel position="0,0" size="1280,720" backgroundColor="#000b1020" zPosition="-1" />
  <ePixmap pixmap="%(logo)s" position="40,24" size="64,64" alphatest="blend" />
  <widget name="title" position="120,26" size="900,40" font="Regular;32" %(colors)s transparent="1" />
  <widget name="sub" position="120,66" size="1100,30" font="Regular;20" backgroundColor="#000b1020" foregroundColor="#009fb1c9" transparent="1" />
  %(list)s
  %(info)s
  <widget name="status" position="40,590" size="1200,30" font="Regular;22" backgroundColor="#000b1020" foregroundColor="#005ec4f2" transparent="1" />
  <eLabel position="40,640" size="10,30" backgroundColor="#00e11d2e" />
  <widget name="key_red" position="58,638" size="270,34" font="Regular;22" %(colors)s transparent="1" />
  <eLabel position="340,640" size="10,30" backgroundColor="#0014a37f" />
  <widget name="key_green" position="358,638" size="270,34" font="Regular;22" %(colors)s transparent="1" />
  <eLabel position="640,640" size="10,30" backgroundColor="#00ffcc00" />
  <widget name="key_yellow" position="658,638" size="270,34" font="Regular;22" %(colors)s transparent="1" />
  <eLabel position="940,640" size="10,30" backgroundColor="#001e5bd8" />
  <widget name="key_blue" position="958,638" size="290,34" font="Regular;22" %(colors)s transparent="1" />
</screen>""" % {
        "name": name, "colors": COLORS, "logo": PLUGIN_DIR + ("logo_fhd.png" if FHD else "logo.png"), "list": lst,
        "info": ('<widget name="info" position="670,110" size="570,470" font="Regular;24" backgroundColor="#00111827" foregroundColor="#00ffffff" />' if info else ""),
    }
    return scale(xml)


def run_async(screen, fn, ok, err=None):
    """Netzwerk im Hintergrund; Ergebnis nur verarbeiten, wenn der Bildschirm noch offen ist."""
    def done(result):
        if not getattr(screen, "p_closed", False):
            ok(result)

    def fail(f):
        if not getattr(screen, "p_closed", False):
            msg = f.getErrorMessage() if hasattr(f, "getErrorMessage") else str(f)
            (err or (lambda m: screen["status"].setText("Fehler: " + m)))(msg)
    threads.deferToThread(fn).addCallbacks(done, fail)


def make_timer(fn):
    t = eTimer()
    try:
        t.callback.append(fn)
    except AttributeError:
        t.p_conn = t.timeout.connect(fn)
    return t


class Base(Screen):
    def __init__(self, session, name, title, info=True, config=False):
        self.skin = skin(name, info=info, config=config)
        Screen.__init__(self, session)
        self.p_closed = False
        self.onClose.append(self._p_closed)
        self["title"] = Label(title)
        self["sub"] = Label("")
        self["status"] = Label("")
        if info:
            self["info"] = Label("")
        for k in ("key_red", "key_green", "key_yellow", "key_blue"):
            self[k] = Label("")
        self.setTitle(title)

    def _p_closed(self):
        self.p_closed = True


def fmt_time(ts):
    return time.strftime("%H:%M", time.localtime(ts))


# ---------- Wiedergabe ----------
def play(session, source, items, index):
    item = items[index]
    if item.get("kind") == "series":
        session.open(SeasonScreen, source, item)
        return
    live = item.get("kind") == "live"
    if live:
        session.open(PortivaPlayer, source, items, index, True)
        return
    url = source.stream_url(item, store.settings().get("live_format", "ts"))
    pos = store.resume_get(url)
    if pos <= 0:
        session.open(PortivaPlayer, source, items, index, False)
        return

    def answer(choice):
        if choice is None:
            return
        session.open(PortivaPlayer, source, items, index, False, choice[1])
    session.openWithCallback(answer, ChoiceBox, title=item.get("name", ""),
                             list=[("Weiterschauen ab %d:%02d" % (pos // 60, pos % 60), pos), ("Von vorne", 0)])


# ---------- Start ----------
class PortivaMain(Base):
    def __init__(self, session):
        Base.__init__(self, session, "PortivaMain", "Portiva – PowerIPTV")
        self.source = None
        self["list"] = MenuList([])
        self["key_red"].setText("Zugänge")
        self["key_green"].setText("Einstellungen")
        self["key_blue"].setText("Suche")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.open_entry, "cancel": self.close,
            "red": lambda: self.session.openWithCallback(self.refresh, ProfilesScreen),
            "green": lambda: self.session.open(SettingsScreen),
            "blue": self.search,
        }, -1)
        self.onFirstExecBegin.append(self.refresh)

    def refresh(self, *args):
        p = store.active_profile()
        if not p:
            self["sub"].setText("Noch kein Zugang eingerichtet")
            self.session.openWithCallback(self.refresh_after_edit, ProfileEdit, None)
            return
        self.source = make_source(p)
        self["title"].setText("Portiva – PowerIPTV  ·  " + p.get("name", ""))
        self["list"].setList([("Live TV", "live"), ("Filme", "movie"), ("Serien", "series"), ("Favoriten", "fav"),
                              ("Suche", "search"), ("Zugänge (Benutzer wechseln)", "profiles"), ("Einstellungen", "settings")])
        self["info"].setText("Live TV, Filme und Serien deines IPTV-Zugangs.\n\nOK = öffnen\nINFO im Player = Jetzt/Danach\n"
                             "Hoch/Runter im Live TV = Sender wechseln\n\nVersion " + VERSION)
        self["sub"].setText("Verbinde …")
        run_async(self, self.source.account_text, lambda t: self["sub"].setText(t or ""), lambda m: self["sub"].setText("Verbindung fehlgeschlagen: " + m))

    def refresh_after_edit(self, *args):
        if store.active_profile():
            self.refresh()
        else:
            self.close()

    def open_entry(self):
        cur = self["list"].getCurrent()
        if not cur or not self.source:
            return
        key = cur[1]
        if key in ("live", "movie", "series"):
            self.session.open(CategoryScreen, self.source, key)
        elif key == "fav":
            p = store.active_profile()
            favs = store.favorites(p["id"]) if p else []
            if not favs:
                self["status"].setText("Noch keine Favoriten – in einer Liste mit der gelben Taste hinzufügen")
                return
            self.session.open(ItemScreen, self.source, None, None, "Favoriten", favs)
        elif key == "search":
            self.search()
        elif key == "profiles":
            self.session.openWithCallback(self.refresh, ProfilesScreen)
        elif key == "settings":
            self.session.open(SettingsScreen)

    def search(self):
        if not self.source:
            return

        def done(text):
            if not text:
                return
            q = text.lower()
            self["status"].setText("Suche „%s“ …" % text)

            def work():
                out = []
                for kind in ("live", "movie", "series"):
                    try:
                        out += [i for i in self.source.items(kind, None) if q in (i.get("name") or "").lower()]
                    except Exception:
                        pass
                return out[:500]

            def show(res):
                self["status"].setText("")
                if not res:
                    self["status"].setText("Nichts gefunden für „%s“" % text)
                    return
                self.session.open(ItemScreen, self.source, None, None, "Suche: " + text, res)
            run_async(self, work, show)
        self.session.openWithCallback(done, VirtualKeyBoard, title="Suche (Sender, Filme, Serien)", text="")


# ---------- Kategorien ----------
class CategoryScreen(Base):
    TITLES = {"live": "Live TV", "movie": "Filme", "series": "Serien"}

    def __init__(self, session, source, kind):
        Base.__init__(self, session, "PortivaCategories", self.TITLES[kind], info=False)
        self.source, self.kind = source, kind
        self["list"] = MenuList([])
        if kind == "live":
            self["key_green"].setText("Als Bouquet")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.open_cat, "cancel": self.close, "green": self.bouquet,
        }, -1)
        self["status"].setText("Lade Kategorien …")
        run_async(self, lambda: source.categories(kind), self.loaded)

    def loaded(self, cats):
        self["status"].setText("%d Kategorien" % len(cats))
        self["list"].setList([("Alle", None)] + [(c["name"], c["id"]) for c in cats])

    def open_cat(self):
        cur = self["list"].getCurrent()
        if cur:
            self.session.open(ItemScreen, self.source, self.kind, cur[1], cur[0])

    def bouquet(self):
        """Live-Kategorie als Bouquet in die normale Senderliste von Enigma2 uebernehmen."""
        cur = self["list"].getCurrent()
        if self.kind != "live" or not cur:
            return
        from .bouquet import export_bouquet
        self["status"].setText("Lege Bouquet „%s“ an …" % cur[0])

        def work():
            items = self.source.items("live", cur[1])
            return export_bouquet(cur[0], items, self.source)
        run_async(self, work, lambda n: self["status"].setText("✓ Bouquet „PowerIPTV – %s“ mit %d Sendern angelegt" % (cur[0], n)))


# ---------- Sender / Filme / Serien ----------
class ItemScreen(Base):
    def __init__(self, session, source, kind, cat_id, title, items=None):
        Base.__init__(self, session, "PortivaItems", title)
        self.source, self.kind, self.cat_id = source, kind, cat_id
        self.all = []
        self.shown = []
        self.filter = ""
        self.pid = (store.active_profile() or {}).get("id")
        self["list"] = MenuList([])
        self["list"].onSelectionChanged.append(self.selection_changed)
        self["key_yellow"].setText("Favorit")
        self["key_blue"].setText("Filtern")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.ok, "cancel": self.close, "yellow": self.fav, "blue": self.ask_filter,
        }, -1)
        self.info_timer = make_timer(self.load_info)
        self.info_cache = {}
        if items is not None:
            self.loaded(items)
        else:
            self["status"].setText("Lade …")
            run_async(self, lambda: source.items(kind, cat_id), self.loaded)

    def label(self, i):
        star = "★ " if self.pid and store.is_favorite(self.pid, i) else ""
        num = ("%s  " % i["number"]) if i.get("kind") == "live" and i.get("number") else ""
        tag = {"movie": "", "series": "  [Serie]", "episode": ""}.get(i.get("kind"), "")
        return star + num + (i.get("name") or "") + (tag if self.kind is None else "")

    def loaded(self, items):
        self.all = items
        self.apply()

    def apply(self):
        q = self.filter.lower()
        self.shown = [i for i in self.all if q in (i.get("name") or "").lower()] if q else list(self.all)
        self["list"].setList([(self.label(i), n) for n, i in enumerate(self.shown)])
        self["status"].setText("%d Einträge%s" % (len(self.shown), ("  ·  Filter: " + self.filter) if self.filter else ""))
        self.selection_changed()

    def current(self):
        cur = self["list"].getCurrent()
        return (cur[1], self.shown[cur[1]]) if cur and cur[1] < len(self.shown) else (None, None)

    def selection_changed(self):
        n, item = self.current()
        if item is None:
            self["info"].setText("")
            return
        self["info"].setText(item.get("name", ""))
        self.info_timer.start(500, True)  # erst nach kurzer Pause nachladen (schnelles Blaettern)

    def load_info(self):
        n, item = self.current()
        if item is None:
            return
        key = "%s:%s" % (item.get("kind"), item.get("id"))
        if key in self.info_cache:
            self["info"].setText(self.info_cache[key])
            return
        kind = item.get("kind")
        if kind == "live":
            def work():
                epg = self.source.short_epg(item)
                now = time.time()
                cur = [e for e in epg if e["start"] <= now < e["end"]]
                nxt = [e for e in epg if e["start"] >= (cur[0]["end"] if cur else now)]
                lines = [item.get("name", ""), ""]
                if cur:
                    lines += ["Jetzt: %s – %s" % (fmt_time(cur[0]["start"]), fmt_time(cur[0]["end"])), cur[0]["title"], (cur[0].get("desc") or "")[:350], ""]
                else:
                    lines += ["Jetzt: Kein Programm gefunden", ""]
                if nxt:
                    lines += ["Danach: %s  %s" % (fmt_time(nxt[0]["start"]), nxt[0]["title"])]
                return "\n".join(lines)
        elif kind == "movie":
            def work():
                i = self.source.vod_info(item)
                parts = [item.get("name", "")]
                meta = " · ".join([str(x) for x in (i.get("year"), i.get("genre"), i.get("duration"), ("★ %s" % i["rating"]) if i.get("rating") else None) if x])
                if meta:
                    parts += ["", meta]
                if i.get("plot"):
                    parts += ["", i["plot"][:700]]
                if i.get("cast"):
                    parts += ["", "Mit: " + i["cast"][:200]]
                return "\n".join(parts)
        else:
            text = "\n".join([x for x in (item.get("name", ""), "", (item.get("plot") or "")[:700]) if x is not None])
            self.info_cache[key] = text
            self["info"].setText(text)
            return

        def show(text):
            self.info_cache[key] = text
            if self.current()[1] is item:
                self["info"].setText(text)
        run_async(self, work, show, lambda m: None)

    def ok(self):
        n, item = self.current()
        if item is not None:
            play(self.session, self.source, self.shown, n)

    def fav(self):
        n, item = self.current()
        if item is None or not self.pid:
            return
        added = store.toggle_favorite(self.pid, item)
        self["status"].setText(("★ „%s“ zu Favoriten hinzugefügt" if added else "„%s“ aus Favoriten entfernt") % item.get("name", ""))
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


# ---------- Serien: Staffeln ----------
class SeasonScreen(Base):
    def __init__(self, session, source, series):
        Base.__init__(self, session, "PortivaSeasons", series.get("name", "Serie"))
        self.source, self.series = source, series
        self.seasons = {}
        self["list"] = MenuList([])
        self["actions"] = ActionMap(["OkCancelActions"], {"ok": self.ok, "cancel": self.close}, -1)
        self["status"].setText("Lade Staffeln …")
        run_async(self, lambda: source.series_info(series), self.loaded)

    def loaded(self, info):
        self.seasons = info.get("seasons") or {}
        self["info"].setText((info.get("plot") or series_plot(self.series))[:900])
        keys = sorted(self.seasons.keys())
        self["list"].setList([("Staffel %d   (%d Folgen)" % (k, len(self.seasons[k])), k) for k in keys])
        self["status"].setText("%d Staffeln" % len(keys) if keys else "Keine Folgen gefunden")

    def ok(self):
        cur = self["list"].getCurrent()
        if cur:
            self.session.open(ItemScreen, self.source, "episode", None, "%s – Staffel %d" % (self.series.get("name", ""), cur[1]), self.seasons[cur[1]])


def series_plot(s):
    return s.get("plot") or ""


# ---------- Zugaenge ----------
class ProfilesScreen(Base):
    def __init__(self, session):
        Base.__init__(self, session, "PortivaProfiles", "Zugänge", info=False)
        self["list"] = MenuList([])
        self["key_red"].setText("Löschen")
        self["key_green"].setText("Neu")
        self["key_yellow"].setText("Bearbeiten")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.activate, "cancel": self.close, "red": self.delete, "green": lambda: self.edit(None), "yellow": self.edit_current,
        }, -1)
        self.onShown.append(self.reload)

    def reload(self):
        d = store.profiles()
        self["list"].setList([(("✓ " if p.get("id") == d.get("active") else "   ") + p.get("name", "Zugang") +
                               ("   (M3U)" if p.get("type") == "m3u" else "   (Xtream · %s)" % p.get("username", "")), p["id"]) for p in d["list"]])
        self["status"].setText("OK = diesen Zugang benutzen")

    def activate(self):
        cur = self["list"].getCurrent()
        if cur:
            store.activate(cur[1])
            self.close(True)

    def edit(self, profile):
        self.session.openWithCallback(lambda *a: self.reload(), ProfileEdit, profile)

    def edit_current(self):
        cur = self["list"].getCurrent()
        if cur:
            self.edit(next((p for p in store.profiles()["list"] if p.get("id") == cur[1]), None))

    def delete(self):
        cur = self["list"].getCurrent()
        if not cur:
            return

        def yes(ok):
            if ok:
                store.delete_profile(cur[1])
                self.reload()
        self.session.openWithCallback(yes, MessageBox, "Zugang „%s“ löschen?" % cur[0].strip("✓ "), MessageBox.TYPE_YESNO)


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
        self["status"].setText("Hinweis: OK oder Gelb öffnet die Tastatur")

    def build(self, *args):
        lst = [getConfigListEntry("Art des Zugangs", self.c_type), getConfigListEntry("Name (beliebig)", self.c_name)]
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
        self.c_player = ConfigSelection([("auto", "Automatisch (exteplayer3 wenn vorhanden)"), ("4097", "GStreamer (4097)"),
                                         ("5001", "GStreamer über ServiceApp (5001)"), ("5002", "exteplayer3 über ServiceApp (5002)")], default=s["player"])
        self.c_live = ConfigSelection([("ts", "MPEG-TS (Standard)"), ("m3u8", "HLS (m3u8)")], default=s["live_format"])
        ConfigListScreen.__init__(self, [getConfigListEntry("Player", self.c_player), getConfigListEntry("Live-TV-Format", self.c_live)], session=session)
        self["key_red"].setText("Abbrechen")
        self["key_green"].setText("Speichern")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.save, "cancel": self.close, "red": self.close, "green": self.save,
        }, -2)
        self["status"].setText("Ruckelt oder fehlt Bild/Ton: anderen Player bzw. HLS probieren")

    def save(self):
        store.save_settings({"player": self.c_player.value, "live_format": self.c_live.value})
        self.close()
