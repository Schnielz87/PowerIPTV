# -*- coding: utf-8 -*-
# Portiva – PowerIPTV fuer Enigma2 (OpenATV, OpenPLi, VTi, ...): Live TV, Filme & Serien ueber Xtream Codes oder M3U.
from Plugins.Plugin import PluginDescriptor


def main(session, **kwargs):
    from .ui import PortivaMain
    session.open(PortivaMain)


def Plugins(**kwargs):
    return [
        PluginDescriptor(name="PowerIPTV", description="Portiva – Live TV, Filme & Serien (Xtream / M3U)",
                         where=PluginDescriptor.WHERE_PLUGINMENU, icon="plugin.png", fnc=main),
        PluginDescriptor(name="PowerIPTV", description="Portiva – Live TV, Filme & Serien",
                         where=PluginDescriptor.WHERE_EXTENSIONSMENU, fnc=main),
    ]
