# -*- coding: utf-8 -*-
"""Live-Kategorie als Bouquet in die normale Enigma2-Senderliste uebernehmen (umschalten mit der Fernbedienung wie gewohnt)."""
import os
import re

try:
    from urllib.parse import quote
except ImportError:
    from urllib import quote

from . import store
from .player import service_type

E2 = "/etc/enigma2/"


def export_bouquet(name, items, source):
    safe = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") or "live"
    fname = "userbouquet.poweriptv_%s.tv" % safe
    stype = service_type()
    live_ext = store.settings().get("live_format", "ts")
    lines = ["#NAME PowerIPTV - %s" % name]
    for it in items:
        url = source.stream_url(it, live_ext)
        title = (it.get("name") or "").replace(":", " ")
        lines.append("#SERVICE %d:0:1:0:0:0:0:0:0:0:%s:%s" % (stype, quote(url, safe=""), title))
        lines.append("#DESCRIPTION %s" % title)
    with open(E2 + fname, "w") as f:
        f.write("\n".join(lines) + "\n")
    ref = '#SERVICE 1:7:1:0:0:0:0:0:0:0:FROM BOUQUET "%s" ORDER BY bouquet' % fname
    index = E2 + "bouquets.tv"
    content = open(index).read() if os.path.exists(index) else "#NAME User - Bouquets (TV)\n"
    if fname not in content:
        with open(index, "w") as f:
            f.write(content.rstrip("\n") + "\n" + ref + "\n")
    try:
        from enigma import eDVBDB
        eDVBDB.getInstance().reloadBouquets()
    except Exception:
        pass
    return len(items)
