"""يرسم أيقونة التطبيق (هلال وقبة) بكل الأحجام. شغّله: python3 tools/make_icon.py"""
from PIL import Image, ImageDraw
import os

GREEN = (30, 91, 63, 255)
GOLD = (201, 162, 39, 255)
S = 1024

img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([0, 0, S - 1, S - 1], radius=230, fill=GREEN)
# القبة
d.pieslice([292, 400, 732, 840], 180, 360, fill=GOLD)
d.rectangle([292, 618, 732, 800], fill=GOLD)
# الباب
d.pieslice([452, 650, 572, 770], 180, 360, fill=GREEN)
d.rectangle([452, 710, 572, 800], fill=GREEN)
# الهلال
d.ellipse([432, 170, 592, 330], fill=GOLD)
d.ellipse([472, 150, 622, 300], fill=GREEN)
# القاعدة
d.rectangle([232, 800, 792, 840], fill=GOLD)

sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
base = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
for name, px in sizes.items():
    out = os.path.join(base, "mipmap-" + name)
    os.makedirs(out, exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
