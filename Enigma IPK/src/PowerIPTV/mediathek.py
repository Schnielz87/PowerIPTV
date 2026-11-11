# -*- coding: utf-8 -*-
"""
Mediatheken von ARD, ZDF, Arte & Co. (wie in der App) – ueber die freie MediathekView-Datenbank
(mediathekviewweb.de), unabhaengig vom IPTV-Zugang. Erst den Sender waehlen (oder suchen), dann die
Sendungen (neueste zuerst); rechts stehen Thema, Sendezeit, Laenge und Beschreibung.
Python 2 und 3 (OpenPLi 7/8/9, OpenATV …).
"""
import json
import re
import time

from Components.ActionMap import ActionMap
from Screens.ChoiceBox import ChoiceBox
from Screens.VirtualKeyBoard import VirtualKeyBoard

from . import store
from .api import UA, Request, urlopen
from .common import PList, run_async
from .compat import native

API = "https://mediathekviewweb.de/api/query"
PAGE = 60
CHANNELS = [("Alle Sender", ""), ("ARD", "ARD"), ("ZDF", "ZDF"), ("Arte", "ARTE.DE"), ("3sat", "3Sat"), ("ZDFneo", "ZDFneo"),
            ("ZDFinfo", "ZDFinfo"), ("Phoenix", "PHOENIX"), ("KiKA", "KiKA"), ("ZDFtivi", "ZDF-tivi"), ("BR", "BR"), ("WDR", "WDR"),
            ("NDR", "NDR"), ("SWR", "SWR"), ("MDR", "MDR"), ("HR", "HR"), ("RBB", "RBB"), ("SR", "SR"),
            ("Radio Bremen", "Radio Bremen TV"), ("ORF", "ORF"), ("SRF", "SRF"), ("DW", "DW")]
SPECIAL = re.compile(u"Audiodeskription|Gebärdensprache|Hörfassung|\\(mit Untertitel\\)|klare Sprache", re.I | re.U)
WD = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]
SEARCH, MORE = "__search__", "__more__"


def search(query, channel, offset=0):
    """Sendungen suchen (blockierend, im Hintergrund aufrufen). query/channel leer = alle/neueste."""
    queries = []
    if channel:
        queries.append({"fields": ["channel"], "query": channel})
    if query:
        queries.append({"fields": ["title", "topic"], "query": query})
    body = json.dumps({"queries": queries, "sortBy": "timestamp", "sortOrder": "desc", "future": False,
                       "offset": offset, "size": PAGE, "duration_min": 180})
    req = Request(API, data=body.encode("utf-8"), headers={"User-Agent": UA, "Content-Type": "application/json"})
    data = json.loads(urlopen(req, timeout=20).read().decode("utf-8"))
    out, seen = [], set()
    for o in ((data or {}).get("result") or {}).get("results") or []:
        url = o.get("url_video") or ""
        title = o.get("title") or u""
        if not url.startswith("http") or SPECIAL.search(title):
            continue
        key = u"%s|%s|%d" % (o.get("channel"), title, int(o.get("duration") or 0) // 60)
        if key in seen:
            continue
        seen.add(key)
        hd = o.get("url_video_hd") or ""
        out.append({"id": o.get("id") or url, "channel": o.get("channel") or u"", "topic": o.get("topic") or u"", "title": title,
                    "desc": (o.get("description") or u"").strip(), "ts": int(o.get("timestamp") or 0),
                    "dur": int(o.get("duration") or 0), "url": hd if hd.startswith("http") else url})
    return native(out)


def when(ts):
    if not ts:
        return ""
    t = time.localtime(ts)
    return "%s %02d.%02d. %02d:%02d" % (WD[t.tm_wday], t.tm_mday, t.tm_mon, t.tm_hour, t.tm_min)


def _base():
    from .ui import Base
    return Base


class MediathekChannels(_base()):
    """Sender waehlen (oder suchen)."""

    def __init__(self, session):
        _base().__init__(self, session, "PortivaMediathek", "Mediathek – ARD, ZDF, Arte & Co.", layout="list")
        self["list"] = PList([("Suchen …", SEARCH)] + [(label, q) for label, q in CHANNELS])
        self["sub"].setText("Kostenlose Sendungen der öffentlich-rechtlichen Sender – unabhängig von deinem IPTV-Zugang")
        self["info"].setText("Sender wählen und OK drücken: die neuesten Sendungen erscheinen.\n\n"
                             "„Suchen …“ (oder BLAU): Sendung oder Thema eingeben, z.B. Tatort, Tagesschau, Terra X.\n\n"
                             "Die Videos kommen direkt aus den Sender-Mediatheken (meist in HD).")
        self["key_blue"].setText("Suchen")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.ok, "cancel": self.close, "blue": self.ask_search,
        }, -1)

    def ok(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        if cur[1] == SEARCH:
            self.ask_search()
        else:
            self.session.open(MediathekList, cur[0], cur[1], "")

    def ask_search(self):
        cur = self["list"].getCurrent()
        channel = cur[1] if cur and cur[1] != SEARCH else ""
        label = cur[0] if channel else "Alle Sender"
        self.session.openWithCallback(lambda text: self.searched(text, label, channel), VirtualKeyBoard,
                                      title="Mediathek durchsuchen (%s)" % label, text="")

    def searched(self, text, label, channel):
        if text and text.strip():
            self.session.open(MediathekList, label, channel, text.strip())


class MediathekList(_base()):
    """Sendungen eines Senders bzw. Suchergebnis; rechts die Infos zur gewaehlten Sendung."""

    def __init__(self, session, label, channel, query):
        title = ("Mediathek · %s" % label) + ((" · „%s“" % query) if query else "")  # alles native str (Python 2!)
        _base().__init__(self, session, "PortivaMediathekList", title, layout="list")
        self.channel, self.query, self.items = channel, query, []
        self["list"] = PList([])
        self["list"].onSelectionChanged.append(self.selection_changed)
        self["key_blue"].setText("Suchen")
        self["actions"] = ActionMap(["OkCancelActions", "ColorActions"], {
            "ok": self.ok, "cancel": self.close, "blue": self.ask_search,
        }, -1)
        self["status"].setText("Lade …")
        run_async(self, self.fetch_first, self.loaded, self.failed)

    def fetch_first(self):
        return search(self.query, self.channel, 0)

    def failed(self, msg):
        self["status"].setText("Mediathek nicht erreichbar: %s" % msg)

    def loaded(self, items, more_from=None):
        if more_from is None:
            self.items = items
        else:
            known = set(i["id"] for i in self.items)
            self.items = self.items + [i for i in items if i["id"] not in known]
        rows = [("%s   %s · %s" % (when(i["ts"]), i["channel"], i["title"]), n) for n, i in enumerate(self.items)]
        has_more = len(items) >= PAGE // 2 if more_from is None else bool(items)
        if has_more:
            rows.append(("►  Weitere Sendungen laden", MORE))
        self["list"].setList(rows)
        if more_from:
            self["list"].moveToIndex(min(more_from, len(rows) - 1))
        self["status"].setText(("%d Sendungen" % len(self.items)) if self.items else
                               ("Keine Treffer für „%s“" % self.query if self.query else "Keine Sendungen gefunden"))
        self.selection_changed()

    def selection_changed(self):
        cur = self["list"].getCurrent()
        if not cur or cur[1] == MORE:
            self["info"].setText("OK: weitere Sendungen laden" if cur else "")
            return
        i = self.items[cur[1]]
        meta = " · ".join([x for x in (i["channel"], when(i["ts"]), ("%d Min." % ((i["dur"] + 59) // 60)) if i["dur"] else "") if x])
        desc = i["desc"]
        if len(desc) > 420:
            desc = desc[:420].rsplit(" ", 1)[0] + " …"
        self["info"].setText("%s\n\n%s\n%s\n\n%s" % (i["title"], i["topic"], meta, desc))

    def ok(self):
        cur = self["list"].getCurrent()
        if not cur:
            return
        if cur[1] == MORE:
            start = len(self.items)
            self["status"].setText("Lade weitere Sendungen …")
            run_async(self, lambda: search(self.query, self.channel, start), lambda r: self.loaded(r, start), self.failed)
            return
        self.play(self.items[cur[1]])

    def play(self, i):
        from .player import PortivaPlayer, DirectSource
        name = i["title"] if (not i["topic"] or i["topic"].lower() in i["title"].lower()) else "%s: %s" % (i["topic"], i["title"])
        item = {"kind": "movie", "id": i["url"], "name": name, "url": i["url"]}
        pos = store.resume_get(i["url"])
        if pos <= 0:
            self.session.open(PortivaPlayer, DirectSource(), [item], 0, False)
            return

        def answer(choice):
            if choice is not None:
                self.session.open(PortivaPlayer, DirectSource(), [item], 0, False, choice[1])
        self.session.openWithCallback(answer, ChoiceBox, title=name,
                                      list=[("Weiterschauen ab %d:%02d" % (pos // 60, pos % 60), pos), ("Von vorne", 0)])

    def ask_search(self):
        self.session.openWithCallback(self.searched, VirtualKeyBoard, title="Mediathek durchsuchen", text=self.query or "")

    def searched(self, text):
        if text and text.strip():
            self.session.open(MediathekList, "Suche", self.channel, text.strip())
