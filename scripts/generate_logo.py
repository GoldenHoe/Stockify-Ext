"""Rebuild the Simplified-Chinese wordmark so it matches each existing logo variant.

The Traditional assets are the source of truth for canvas size and ink placement;
the Simplified glyphs are rendered with a heavy serif that matches their weight.
Run this after editing the Traditional artwork.
"""
from __future__ import annotations

import pathlib

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
FONT = "C:/Windows/Fonts/NotoSerifSC-VF.ttf"
TEXT = "韭菜记账本"


def render_like(original: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    alpha = original.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise SystemExit("original wordmark has no ink")

    font = ImageFont.truetype(FONT, 400)
    try:
        font.set_variation_by_axes([900])
    except Exception:
        pass
    scratch = Image.new("L", (400 * len(TEXT) + 200, 700), 0)
    ImageDraw.Draw(scratch).text((50, 100), TEXT, font=font, fill=255)
    ink = scratch.getbbox()
    glyphs = scratch.crop(ink)

    target_width = bbox[2] - bbox[0]
    target_height = bbox[3] - bbox[1]
    glyphs = glyphs.resize((target_width, target_height), Image.LANCZOS)

    layer = Image.new("RGBA", glyphs.size, (*color, 0))
    layer.putalpha(glyphs)

    canvas = Image.new("RGBA", original.size, (0, 0, 0, 0))
    canvas.paste(layer, (bbox[0], bbox[1]), layer)
    return canvas


def main() -> int:
    pairs = [
        ("drawable-zh-rTW/stockify.png", "drawable/stockify.png", (0, 0, 0)),
        ("drawable-zh-rTW-night/stockify.png", "drawable-night/stockify.png", (255, 255, 255)),
    ]
    for traditional, simplified, color in pairs:
        source = RES / traditional
        if not source.exists():
            print("missing Traditional source:", source)
            return 1
        original = Image.open(source).convert("RGBA")
        result = render_like(original, color)
        result.save(RES / simplified, optimize=True)
        print(f"{simplified}: {result.size} from {original.size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
