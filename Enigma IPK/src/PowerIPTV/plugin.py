# -*- coding: utf-8 -*-
# Portiva – PowerIPTV fuer Enigma2 (OpenATV, OpenPLi, VTi, ...): Live TV, Filme & Serien ueber Xtream Codes oder M3U.
from Plugins.Plugin import PluginDescriptor


def main(session, **kwargs):
    try:
        from .compat import patch_gui
        patch_gui()  # Python 2: Texte als UTF-8 an die Anzeige (sonst gruener Bildschirm)
    except Exception:
        pass
    try:
        from . import link
        link.start(session)  # falls Enigma2 seit der Installation nicht neu gestartet wurde
    except Exception:
        pass
    from .ui import PortivaHome
    session.open(PortivaHome)


def sessionstart(reason, **kwargs):
    """Beim Start von Enigma2: Portiva Link bereitstellen (Zugang/Wiedergabe von Handy, Tablet, PC empfangen)."""
    if reason == 0 and "session" in kwargs:
        try:
            from .compat import patch_gui
            patch_gui()
        except Exception:
            pass
        try:
            from . import link
            link.start(kwargs["session"])
        except Exception:
            pass


def mainmenu(menuid, **kwargs):
    """Eigener Eintrag direkt im Hauptmenue (wie BlueBoxx), zusaetzlich zu Menue > Plugins."""
    if menuid == "mainmenu":
        return [("PowerIPTV", main, "poweriptv", 3)]
    return []


def Plugins(**kwargs):
    return [
        PluginDescriptor(name="PowerIPTV", description="Portiva – Live TV, Filme & Serien (Xtream / M3U)",
                         where=PluginDescriptor.WHERE_PLUGINMENU, icon="plugin.png", fnc=main),
        PluginDescriptor(name="PowerIPTV", description="Portiva – Live TV, Filme & Serien",
                         where=PluginDescriptor.WHERE_EXTENSIONSMENU, fnc=main),
        PluginDescriptor(name="PowerIPTV", description="Portiva – Live TV, Filme & Serien",
                         where=PluginDescriptor.WHERE_MENU, fnc=mainmenu),
        PluginDescriptor(where=PluginDescriptor.WHERE_SESSIONSTART, fnc=sessionstart),
    ]
