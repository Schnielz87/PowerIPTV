#!/usr/bin/env python3
"""Erzeugt die Skin-Grafiken des Enigma2-Plugins (HD 1280x720 und Full-HD 1920x1080).

Aufruf:  python3 tools/make_skin.py   ->  src/PowerIPTV/skin/hd/*.png und skin/fhd/*.png
Alle Masse in HD-Koordinaten; Full-HD = x1,5. Gezeichnet wird 4-fach vergroessert und dann
verkleinert (weiche Kanten). Braucht nur Pillow.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "PowerIPTV")
SS = 4  # Supersampling

CYAN = (94, 196, 242)
BLUE = (30, 91, 216)
BG_TOP = (7, 11, 22)
BG_BOTTOM = (11, 20, 40)
PANEL = (16, 27, 48)
WHITE = (255, 255, 255)

# Layout (HD) – muss zu den Skins in ui.py / player.py passen
LIST_PANEL = (40, 112, 620, 500)        # x, y, w, h  (linke Liste)
INFO_PANEL = (680, 112, 560, 500)       # rechte Spalte
FULL_PANEL = (40, 112, 1200, 500)       # Liste ueber die ganze Breite
PIG = (680, 112, 560, 315)              # Live-Vorschau 16:9
PIG_INFO = (680, 439, 560, 173)         # Programm unter der Vorschau
LIST_ITEM = (596, 46)                   # Auswahlbalken linke Liste
FULL_ITEM = (1176, 46)
PANEL_ITEM = (488, 42)                  # Senderliste im Player
BIG = (380, 250)
SMALL = (222, 104)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def canvas(w, h, f, color=(0, 0, 0, 0)):
    return Image.new("RGBA", (int(w * f * SS), int(h * f * SS)), color)


def finish(img, w, h, f):
    return img.resize((int(round(w * f)), int(round(h * f))), Image.LANCZOS)


def S(v, f):
    return int(round(v * f * SS))


def background(f, panels=(), hole=None, frame=None):
    w, h = 1280, 720
    img = canvas(w, h, f)
    W, H = img.size
    grad = Image.new("RGBA", (1, H))
    for y in range(H):
        grad.putpixel((0, y), lerp(BG_TOP, BG_BOTTOM, y / (H - 1)) + (255,))
    img.paste(grad.resize((W, H)))
    # weiches Leuchten oben links (cyan) und unten rechts (blau)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([S(-260, f), S(-300, f), S(560, f), S(320, f)], fill=CYAN + (34,))
    g.ellipse([S(820, f), S(420, f), S(1600, f), S(1000, f)], fill=BLUE + (40,))
    glow = glow.filter(ImageFilter.GaussianBlur(S(120, f)))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    # Kopfzeile: Linie mit Verlauf
    line = Image.new("RGBA", (W, S(2, f)), (0, 0, 0, 0))
    for x in range(W):
        t = x / W
        a = int(200 * max(0.0, 1 - abs(t - 0.25) / 0.75))
        for yy in range(line.size[1]):
            line.putpixel((x, yy), CYAN + (a,))
    img.alpha_composite(line, (0, S(100, f)))
    # Panels (Glas-Optik)
    for (x, y, pw, ph) in panels:
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([S(x, f), S(y + 6, f), S(x + pw, f), S(y + ph + 6, f)], S(16, f), fill=(0, 0, 0, 110))
        img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(S(10, f))))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([S(x, f), S(y, f), S(x + pw, f), S(y + ph, f)], S(16, f), fill=PANEL + (235,),
                            outline=WHITE + (22,), width=S(1, f))
        # feiner Lichtrand oben
        d.line([S(x + 18, f), S(y + 1, f), S(x + pw - 18, f), S(y + 1, f)], fill=WHITE + (40,), width=S(1, f))
    # Rahmen + Loch fuer die Live-Vorschau
    if frame:
        x, y, pw, ph = frame
        gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(gl).rounded_rectangle([S(x - 3, f), S(y - 3, f), S(x + pw + 3, f), S(y + ph + 3, f)], S(6, f),
                                             outline=CYAN + (150,), width=S(3, f))
        img = Image.alpha_composite(img, gl.filter(ImageFilter.GaussianBlur(S(6, f))))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([S(x - 2, f), S(y - 2, f), S(x + pw + 2, f), S(y + ph + 2, f)], S(4, f),
                            outline=CYAN + (230,), width=S(2, f))
    if hole:
        x, y, pw, ph = hole
        mask = Image.new("L", img.size, 255)
        ImageDraw.Draw(mask).rectangle([S(x, f), S(y, f), S(x + pw, f) - 1, S(y + ph, f) - 1], fill=0)
        img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", img.size, 0), mask))
    return finish(img, w, h, f)


def selection(w, h, f, radius=10):
    img = canvas(w, h, f)
    W, H = img.size
    grad = Image.new("RGBA", (W, 1))
    for x in range(W):
        t = x / (W - 1)
        c = lerp(BLUE, (36, 150, 225), t)
        a = int(255 * (1 - 0.35 * t))
        grad.putpixel((x, 0), c + (a,))
    grad = grad.resize((W, H))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], S(radius, f), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], S(radius, f), outline=CYAN + (200,), width=S(1, f))
    d.rounded_rectangle([S(6, f), S(10, f), S(10, f), H - S(10, f)], S(2, f), fill=WHITE + (235,))  # Akzentstrich links
    return finish(img, w, h, f)


def focus_frame(w, h, f, pad=8, radius=18):
    tw, th = w + 2 * pad, h + 2 * pad
    img = canvas(tw, th, f)
    gl = canvas(tw, th, f)
    ImageDraw.Draw(gl).rounded_rectangle([S(3, f), S(3, f), S(tw - 3, f), S(th - 3, f)], S(radius, f),
                                         outline=CYAN + (220,), width=S(5, f))
    img = Image.alpha_composite(img, gl.filter(ImageFilter.GaussianBlur(S(4, f))))
    ImageDraw.Draw(img).rounded_rectangle([S(pad - 3, f), S(pad - 3, f), S(tw - pad + 3, f), S(th - pad + 3, f)],
                                          S(radius - 4, f), outline=WHITE + (255,), width=S(3, f))
    return finish(img, tw, th, f)


def rounded_photo(path, w, h, f, radius=16):
    src = Image.open(path).convert("RGBA").resize((int(w * f * SS), int(h * f * SS)), Image.LANCZOS)
    W, H = src.size
    # unten dunkler Verlauf fuer die Schrift
    shade = Image.new("RGBA", (1, H))
    for y in range(H):
        t = max(0.0, (y / H - 0.45) / 0.55)
        shade.putpixel((0, y), (4, 8, 18, int(225 * t ** 1.3)))
    src = Image.alpha_composite(src, shade.resize((W, H)))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], S(radius, f), fill=255)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    ImageDraw.Draw(out).rounded_rectangle([0, 0, W - 1, H - 1], S(radius, f), outline=WHITE + (40,), width=S(1, f))
    return finish(out, w, h, f)


# ---------- Symbole fuer die kleinen Kacheln (Linien-Icons) ----------
def icon(name, d, cx, cy, r, f, col):
    lw = S(3.2, f)

    def P(x, y):
        return (S(cx + x * r, f), S(cy + y * r, f))

    def box(x0, y0, x1, y1):
        return [P(x0, y0), P(x1, y1)]
    if name == "continue":
        d.ellipse(box(-1, -1, 1, 1), outline=col, width=lw)
        d.polygon([P(-0.32, -0.5), P(-0.32, 0.5), P(0.52, 0)], fill=col)
    elif name == "search":
        d.ellipse(box(-0.95, -0.95, 0.45, 0.45), outline=col, width=lw)
        d.line([P(0.35, 0.35), P(0.95, 0.95)], fill=col, width=S(4.5, f))
    elif name == "guide":
        d.rounded_rectangle(box(-1, -0.8, 1, 0.9), S(4, f), outline=col, width=lw)
        d.line([P(-1, -0.35), P(1, -0.35)], fill=col, width=lw)
        for x in (-0.45, 0.15, 0.7):
            for y in (0.05, 0.5):
                d.rectangle(box(x - 0.12, y - 0.1, x + 0.12, y + 0.1), fill=col)
        d.line([P(-0.5, -1.05), P(-0.5, -0.6)], fill=col, width=lw)
        d.line([P(0.5, -1.05), P(0.5, -0.6)], fill=col, width=lw)
    elif name == "fav":
        pts = []
        for i in range(200):
            t = 2 * math.pi * i / 200
            x = 16 * math.sin(t) ** 3
            y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
            pts.append(P(x / 17.0, y / 17.0 + 0.05))
        d.polygon(pts, fill=col)
    elif name == "recent":
        d.ellipse(box(-1, -1, 1, 1), outline=col, width=lw)
        d.line([P(0, 0), P(0, -0.6)], fill=col, width=lw)
        d.line([P(0, 0), P(0.45, 0.25)], fill=col, width=lw)
    elif name == "profiles":
        d.ellipse(box(-0.95, -0.85, -0.15, -0.05), outline=col, width=lw)
        d.chord(box(-1.3, 0.1, 0.2, 1.5), 180, 360, outline=col, width=lw)
        d.ellipse(box(0.2, -0.65, 0.85, 0.0), outline=col, width=lw)
        d.arc(box(-0.05, 0.25, 1.15, 1.35), 200, 360, fill=col, width=lw)
    elif name == "receive":
        d.rounded_rectangle(box(-0.55, -1, 0.55, 1), S(5, f), outline=col, width=lw)
        d.line([P(-0.15, 0.78), P(0.15, 0.78)], fill=col, width=lw)
        d.line([P(0, -0.55), P(0, 0.3)], fill=col, width=lw)
        d.polygon([P(-0.3, 0.05), P(0.3, 0.05), P(0, 0.45)], fill=col)
    elif name == "settings":
        for i in range(8):
            a = i * math.pi / 4
            d.line([P(math.cos(a) * 0.55, math.sin(a) * 0.55), P(math.cos(a) * 1.0, math.sin(a) * 1.0)], fill=col, width=S(6, f))
        d.ellipse(box(-0.72, -0.72, 0.72, 0.72), outline=col, width=lw)
        d.ellipse(box(-0.28, -0.28, 0.28, 0.28), outline=col, width=lw)
    elif name == "update":
        d.line([P(0, -1), P(0, 0.35)], fill=col, width=lw)
        d.polygon([P(-0.42, 0.0), P(0.42, 0.0), P(0, 0.5)], fill=col)
        d.line([P(-1, 0.45), P(-1, 0.95), P(1, 0.95), P(1, 0.45)], fill=col, width=lw, joint="curve")
    elif name == "reload":
        d.arc(box(-0.95, -0.95, 0.95, 0.95), 200, 340, fill=col, width=lw)
        d.arc(box(-0.95, -0.95, 0.95, 0.95), 20, 160, fill=col, width=lw)
        d.polygon([P(0.95, -0.55), P(0.95, 0.05), P(0.42, -0.2)], fill=col)
        d.polygon([P(-0.95, 0.55), P(-0.95, -0.05), P(-0.42, 0.2)], fill=col)


def small_tile(name, f):
    w, h = SMALL
    img = canvas(w, h, f)
    d = ImageDraw.Draw(img)
    W, H = img.size
    grad = Image.new("RGBA", (1, H))
    for y in range(H):
        grad.putpixel((0, y), lerp((22, 36, 64), (13, 22, 40), y / (H - 1)) + (245,))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], S(14, f), fill=255)
    img.paste(grad.resize((W, H)), (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], S(14, f), outline=WHITE + (26,), width=S(1, f))
    d.line([S(16, f), S(1, f), S(w - 16, f), S(1, f)], fill=WHITE + (45,), width=S(1, f))
    icon(name, d, w / 2.0, 34, 17, f, CYAN + (255,))
    return finish(img, w, h, f)


def dot(color, f, size=16):
    img = canvas(size, size, f)
    d = ImageDraw.Draw(img)
    d.ellipse([S(1, f), S(1, f), S(size - 1, f), S(size - 1, f)], fill=color + (255,))
    d.ellipse([S(4, f), S(3, f), S(size - 7, f), S(size - 8, f)], fill=WHITE + (70,))
    return finish(img, size, size, f)


def osd_bottom(f):
    w, h = 1280, 240
    img = canvas(w, h, f)
    W, H = img.size
    grad = Image.new("RGBA", (1, H))
    for y in range(H):
        t = y / (H - 1)
        grad.putpixel((0, y), (4, 8, 18, int(235 * min(1.0, (t / 0.45)) ** 1.5)))
    img.paste(grad.resize((W, H)))
    d = ImageDraw.Draw(img)
    line = Image.new("RGBA", (W, S(2, f)), (0, 0, 0, 0))
    for x in range(W):
        a = int(170 * max(0.0, 1 - abs(x / W - 0.3) / 0.7))
        for yy in range(line.size[1]):
            line.putpixel((x, yy), CYAN + (a,))
    img.alpha_composite(line, (0, S(66, f)))
    # Logo-Feld
    d.rounded_rectangle([S(40, f), S(84, f), S(170, f), S(170, f)], S(12, f), fill=(255, 255, 255, 18), outline=WHITE + (40,), width=S(1, f))
    return finish(img, w, h, f)


def progress(w, h, f):
    img = canvas(w, h, f)
    W, H = img.size
    grad = Image.new("RGBA", (W, 1))
    for x in range(W):
        grad.putpixel((x, 0), lerp(BLUE, CYAN, x / (W - 1)) + (255,))
    img.paste(grad.resize((W, H)))
    return finish(img, w, h, f)


def side_panel(f):
    w, h = 520, 720
    img = canvas(w, h, f)
    W, H = img.size
    grad = Image.new("RGBA", (W, 1))
    for x in range(W):
        t = x / (W - 1)
        grad.putpixel((x, 0), lerp((10, 16, 30), (8, 13, 26), t) + (int(240 * min(1.0, t / 0.06)),))
    img.paste(grad.resize((W, H)))
    d = ImageDraw.Draw(img)
    d.line([S(24, f), S(70, f), S(496, f), S(70, f)], fill=CYAN + (150,), width=S(2, f))
    return finish(img, w, h, f)


def main():
    for tag, f in (("hd", 1.0), ("fhd", 1.5)):
        out = os.path.join(SRC, "skin", tag)
        if not os.path.isdir(out):
            os.makedirs(out)

        def save(img, name):
            img.save(os.path.join(out, name), optimize=True)
        save(background(f), "bg.png")
        save(background(f, panels=[LIST_PANEL, INFO_PANEL]), "bg_list.png")
        save(background(f, panels=[FULL_PANEL]), "bg_full.png")
        save(background(f, panels=[LIST_PANEL, PIG_INFO], hole=PIG, frame=PIG), "bg_live.png")
        save(selection(LIST_ITEM[0], LIST_ITEM[1], f), "sel_list.png")
        save(selection(FULL_ITEM[0], FULL_ITEM[1], f), "sel_full.png")
        save(selection(PANEL_ITEM[0], PANEL_ITEM[1], f), "sel_panel.png")
        save(focus_frame(BIG[0], BIG[1], f), "focus_big.png")
        save(focus_frame(SMALL[0], SMALL[1], f, pad=8, radius=16), "focus_small.png")
        for name, src in (("live", "live_tv.jpg"), ("movie", "movies.jpg"), ("series", "series.jpg")):
            save(rounded_photo(os.path.join(SRC, src), BIG[0], BIG[1], f), "big_%s.png" % name)
        for name in ("continue", "search", "guide", "fav", "recent", "profiles", "receive", "settings", "update", "reload"):
            save(small_tile(name, f), "tile_%s.png" % name)
        for name, col in (("red", (225, 29, 46)), ("green", (20, 163, 127)), ("yellow", (255, 204, 0)), ("blue", (30, 91, 216))):
            save(dot(col, f), "key_%s.png" % name)
        save(osd_bottom(f), "osd.png")
        save(progress(1050, 6, f), "progress.png")
        save(progress(528, 6, f), "progress_small.png")
        save(side_panel(f), "panel.png")
    print("ok")


if __name__ == "__main__":
    main()
