"""Crop and upscale a region of a screenshot for close inspection."""
from __future__ import annotations

import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
source, target, left, top, right, bottom = (
    sys.argv[1],
    sys.argv[2],
    *(int(value) for value in sys.argv[3:7]),
)
image = Image.open(ROOT / source)
crop = image.crop((left, top, right, bottom))
crop = crop.resize((crop.width * 4, crop.height * 4), Image.LANCZOS)
crop.save(ROOT / target)
print("saved", target, crop.size)
