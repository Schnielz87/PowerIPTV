"""Erzeugt icon/portiva.ico (Windows-App-Symbol) aus dem Android-Logo (app_logo.xml).
Aufruf: pip install cairosvg pillow && python Windows/icon/make_icon.py"""
import os, io, xml.etree.ElementTree as ET
import cairosvg
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main", "resources", "app_logo.xml")
A = "{http://schemas.android.com/apk/res/android}"
gid = [0]

def color(c):
    c = c.lstrip("#")
    if len(c) == 8: return "#" + c[2:], int(c[:2], 16) / 255
    return "#" + c[-6:], 1.0

def grad(el, defs):
    g = el.find("gradient")
    gid[0] += 1; name = f"g{gid[0]}"
    stops = "".join(
        f'<stop offset="{i.get(A+"offset")}" stop-color="{color(i.get(A+"color"))[0]}" stop-opacity="{color(i.get(A+"color"))[1]:.3f}"/>'
        for i in g.findall("item"))
    defs.append(f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" x1="{g.get(A+"startX")}" y1="{g.get(A+"startY")}" x2="{g.get(A+"endX")}" y2="{g.get(A+"endY")}">{stops}</linearGradient>')
    return f"url(#{name})"

def conv(el, defs):
    out = []
    clip = None
    for ch in el:
        tag = ch.tag
        if tag == "clip-path":
            gid[0] += 1; clip = f"c{gid[0]}"
            defs.append(f'<clipPath id="{clip}"><path d="{ch.get(A+"pathData")}"/></clipPath>')
            out.append(f'<g clip-path="url(#{clip})">'); continue
        if tag == "path":
            attrs = [f'd="{ch.get(A+"pathData")}"']
            fill = ch.get(A + "fillColor")
            ga = [a for a in ch if a.tag.endswith("attr")]
            if ga and ga[0].get("name") == "android:fillColor": attrs.append(f'fill="{grad(ga[0], defs)}"')
            elif fill:
                c, o = color(fill); attrs.append(f'fill="{c}" fill-opacity="{o:.3f}"')
            else: attrs.append('fill="none"')
            if ch.get(A + "fillType") == "evenOdd": attrs.append('fill-rule="evenodd"')
            if ch.get(A + "strokeColor"):
                c, o = color(ch.get(A + "strokeColor"))
                attrs += [f'stroke="{c}"', f'stroke-opacity="{o:.3f}"', f'stroke-width="{ch.get(A+"strokeWidth", "1")}"']
                if ch.get(A + "strokeLineCap"): attrs.append(f'stroke-linecap="{ch.get(A+"strokeLineCap")}"')
            out.append("<path " + " ".join(attrs) + "/>"); continue
        if tag == "group":
            tx = float(ch.get(A + "translateX", 0)); ty = float(ch.get(A + "translateY", 0))
            px = float(ch.get(A + "pivotX", 0)); py = float(ch.get(A + "pivotY", 0))
            sx = float(ch.get(A + "scaleX", 1)); sy = float(ch.get(A + "scaleY", 1))
            r = float(ch.get(A + "rotation", 0))
            t = f"translate({tx+px} {ty+py}) rotate({r}) scale({sx} {sy}) translate({-px} {-py})"
            out.append(f'<g transform="{t}">' + conv(ch, defs) + "</g>")
    if clip: out.append("</g>")
    return "".join(out)

root = ET.parse(SRC).getroot()
defs = []
body = conv(root, defs)
w, h = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}"><defs>{"".join(defs)}</defs>{body}</svg>'
open(os.path.join(HERE, "portiva.svg"), "w").write(svg)
png = cairosvg.svg2png(bytestring=svg.encode(), output_width=256, output_height=256)
img = Image.open(io.BytesIO(png)).convert("RGBA")
img.save(os.path.join(HERE, "portiva.png"))
img.save(os.path.join(HERE, "portiva.ico"), sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
print("portiva.ico erzeugt")
