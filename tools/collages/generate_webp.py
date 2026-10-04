"""Wandelt die gerenderten PNGs in WebP um und legt sie in ALLEN Varianten ab (Android, Windows, Samsung/Fire TV Vega)."""
from PIL import Image, ImageFilter
import os

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..", "..")
targets = [
    os.path.join(root, "Android", "app", "src", "main", "res", "drawable-nodpi"),
    os.path.join(root, "Windows", "src", "main", "resources"),
    os.path.join(root, "Tizen Samsung", "assets"),  # auch Oberflaeche der Fire-TV-Vega-App
]
for src, dst in [("live", "live_tv_collage"), ("movies", "movies_collage"), ("series", "series_collage")]:
    img = Image.open(os.path.join(here, f"out_{src}.png")).convert("RGB")
    img = img.filter(ImageFilter.GaussianBlur(0.6))  # minimal weich, Icon/Text bleiben im Fokus
    for target in targets:
        os.makedirs(target, exist_ok=True)
        path = os.path.join(target, f"{dst}.webp")
        img.save(path, "WEBP", quality=80, method=6)
        print(os.path.relpath(path, root), os.path.getsize(path) // 1024, "KB")
