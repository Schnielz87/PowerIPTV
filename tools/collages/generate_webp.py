"""Wandelt die gerenderten PNGs in WebP um und legt sie in den App-Ressourcen ab."""
from PIL import Image, ImageFilter
import os

here = os.path.dirname(os.path.abspath(__file__))
target = os.path.join(here, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
os.makedirs(target, exist_ok=True)
for src, dst in [("live", "live_tv_collage"), ("movies", "movies_collage"), ("series", "series_collage")]:
    img = Image.open(os.path.join(here, f"out_{src}.png")).convert("RGB")
    img = img.filter(ImageFilter.GaussianBlur(0.6))  # minimal weich, Icon/Text bleiben im Fokus
    path = os.path.join(target, f"{dst}.webp")
    img.save(path, "WEBP", quality=80, method=6)
    print(path, os.path.getsize(path) // 1024, "KB")
