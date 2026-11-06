#!/usr/bin/env python3
"""Preview how every in-app Chinese literal renders after conversion."""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_chinese_map import CHAR_MAP, PHRASES  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java"


def convert(text: str) -> str:
    for traditional, simplified in PHRASES:
        text = text.replace(traditional, simplified)
    return "".join(CHAR_MAP.get(ch, ch) for ch in text)


def main() -> int:
    rows: list[str] = []
    for path in sorted(MAIN.rglob("*.kt")):
        if "ChineseTextConverter" in path.name:
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r'"([^"\\\n]*(?:\\.[^"\\\n]*)*)"', text):
            literal = match.group(1)
            if not re.search(r"[\u4e00-\u9fff]", literal):
                continue
            converted = convert(literal)
            if converted != literal:
                rows.append(f"{path.name}\n  - {literal}\n  + {converted}")
    out = ROOT / "scripts/_preview.txt"
    out.write_text("\n".join(rows), encoding="utf-8")
    print(f"{len(rows)} literals change; see {out.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
