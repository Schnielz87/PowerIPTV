# -*- coding: utf-8 -*-
"""Zoom-Effekt: das kleine Live-Bild der Vorschau zieht beim Oeffnen sichtbar zum Vollbild auf
(und beim Verlassen des Players wieder zurueck ins kleine Fenster).

Enigma2-Receiver stellen Position/Groesse des Videobilds ueber /proc/stb/vmpeg/0/dst_* ein
(Koordinaten im 720x576-Raster, hexadezimal). Klappt das auf einem Geraet nicht, wird einfach
ohne Animation umgeschaltet.
"""
import os

from Screens.Screen import Screen

from .common import scale, make_timer

PROC = "/proc/stb/vmpeg/0/"
PIG = (680, 112, 560, 315)          # Vorschau-Fenster in HD-Koordinaten (wie im Skin)
FULL = (0, 0, 720, 576)
STEPS = 14                          # 14 Schritte x 25 ms ~ 0,35 s
STEP_MS = 25


def pig_rect():
    x, y, w, h = PIG
    return (x * 720 // 1280, y * 576 // 720, w * 720 // 1280, h * 576 // 720)


def set_video(rect):
    """Videobild auf (x, y, w, h) im 720x576-Raster setzen. False, wenn das Geraet das nicht kann."""
    try:
        for name, value in zip(("dst_left", "dst_top", "dst_width", "dst_height"), rect):
            with open(PROC + name, "w") as f:
                f.write("%08x" % max(0, int(value)))
        if os.path.exists(PROC + "dst_apply"):
            with open(PROC + "dst_apply", "w") as f:
                f.write("1")
        return True
    except Exception:
        return False


def supported():
    return os.path.exists(PROC + "dst_width")


def _ease(t):
    return 1 - (1 - t) ** 3  # schnell los, weich ankommen


class ZoomScreen(Screen):
    """Durchsichtiger Zwischen-Bildschirm: Zoom klein -> gross, dann Player; danach gross -> klein."""

    def __init__(self, session, open_player):
        self.skin = scale('<screen name="PortivaZoom" position="0,0" size="1280,720" flags="wfNoBorder" backgroundColor="#ff000000" />')
        Screen.__init__(self, session)
        self.open_player = open_player
        self.step = 0
        self.src = self.dst = None
        self.after = None
        self.result = ()
        self.timer = make_timer(self.tick)
        self.onFirstExecBegin.append(self.grow)
        self.onClose.append(self.timer.stop)

    def animate(self, src, dst, after):
        self.src, self.dst, self.after, self.step = src, dst, after, 0
        if not set_video(src):
            self.after = None
            after()  # Geraet kann es nicht -> ohne Animation
            return
        self.timer.start(STEP_MS, False)

    def tick(self):
        if self.after is None:
            return
        self.step += 1
        t = _ease(min(1.0, self.step / float(STEPS)))
        set_video([a + (b - a) * t for a, b in zip(self.src, self.dst)])
        if self.step >= STEPS:
            self.timer.stop()
            after, self.after = self.after, None
            after()

    def grow(self):
        self.animate(pig_rect(), FULL, lambda: self.open_player(self.back))

    def back(self, *result):
        self.result = result
        self.animate(FULL, pig_rect(), self.done)

    def done(self):
        self.close(*self.result)
