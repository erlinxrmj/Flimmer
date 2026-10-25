"""Erzeugt App-Icon und TV-Banner für Flimmer.

Aufruf: python3 art/make_art.py <pfad-zu-inter-bold>
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
BG = (15, 15, 15, 255)
AMBER = (255, 176, 46)
CORAL = (255, 72, 66)


def gradient_mark(size):
    """Das Flimmer-Zeichen: drei gestaffelte, abgerundete Balken, die einen Pfeil bilden."""
    scale = 4
    s = size * scale
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    h = s * 0.2
    gap = s * 0.1
    top = (s - (3 * h + 2 * gap)) / 2
    widths = [0.62, 0.92, 0.62]
    offsets = [0.04, 0.04, 0.04]
    for i, w in enumerate(widths):
        y = top + i * (h + gap)
        x0 = s * offsets[i]
        x1 = x0 + s * w
        d.rounded_rectangle([x0, y, x1, y + h], radius=h / 2, fill=255)
    grad = Image.new("RGBA", (s, s))
    gd = ImageDraw.Draw(grad)
    for x in range(s):
        t = x / (s - 1)
        c = tuple(int(AMBER[k] + (CORAL[k] - AMBER[k]) * t) for k in range(3)) + (255,)
        gd.line([(x, 0), (x, s)], fill=c)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def icon(path, size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(bg).rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.22, fill=255)
    bg = bg.resize((size, size), Image.LANCZOS)
    img.paste(Image.new("RGBA", (size, size), BG), (0, 0), bg)
    mark = gradient_mark(int(size * 0.62))
    off = (size - mark.width) // 2
    img.alpha_composite(mark, (off, off))
    img.save(path)


def banner(path, font_path):
    w, h = 320, 180
    img = Image.new("RGBA", (w, h), BG)
    mark = gradient_mark(64)
    font = ImageFont.truetype(font_path, 44)
    text = "Flimmer"
    tw = ImageDraw.Draw(img).textlength(text, font=font)
    total = 64 + 12 + tw
    x = int((w - total) / 2)
    img.alpha_composite(mark, (x, (h - 64) // 2))
    ImageDraw.Draw(img).text((x + 76, h / 2), text, font=font, fill=(255, 255, 255), anchor="lm")
    img.save(path)


if __name__ == "__main__":
    font = sys.argv[1]
    os.makedirs(os.path.join(ROOT, "mipmap-xhdpi"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "drawable-xhdpi"), exist_ok=True)
    icon(os.path.join(ROOT, "mipmap-xhdpi", "ic_launcher.png"), 96)
    banner(os.path.join(ROOT, "drawable-xhdpi", "banner.png"), font)
    print("Bilder erstellt")
