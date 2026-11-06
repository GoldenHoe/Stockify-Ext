#!/usr/bin/env python3
"""Regenerate app/src/main/assets/cn_stocks.json from Eastmoney's A-share list.

The asset stores names in Traditional Chinese (the app keeps Traditional as the
canonical storage form and converts to Simplified at render time), so names are
mapped back with the same table the Kotlin converter uses.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.parse
import urllib.request
from typing import Iterable

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_chinese_map import CHAR_MAP, PHRASES  # noqa: E402

BASE_URL = "https://push2.eastmoney.com/api/qt/clist/get"
# 滬 A / 科創 / 深 A / 創業板 / 北交所
MARKET_FILTER = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048"
PAGE_SIZE = 100
DEFAULT_OUTPUT = pathlib.Path("app/src/main/assets/cn_stocks.json")

EXCHANGE_LABELS = {"1": "滬市", "0": "深市"}

# Simplified -> Traditional, longest phrase first, then single characters.
_REVERSE_PHRASES = sorted(PHRASES, key=lambda pair: len(pair[1]), reverse=True)
_REVERSE_CHARS = {}
for _traditional, _simplified in CHAR_MAP.items():
    _REVERSE_CHARS.setdefault(_simplified, _traditional)
for _keep in "台里后面只干":
    _REVERSE_CHARS.pop(_keep, None)


def to_traditional(text: str) -> str:
    for simplified, traditional in _REVERSE_PHRASES:
        text = text.replace(simplified, traditional)
    return "".join(_REVERSE_CHARS.get(char, char) for char in text)


def exchange_for(code: str, market: str) -> str:
    if code.startswith(("92", "4", "8")):
        return "北交所"
    return EXCHANGE_LABELS.get(market, "滬市" if code.startswith("6") else "深市")


def fetch_page(page: int) -> dict:
    query = urllib.parse.urlencode(
        {
            "pn": page,
            "pz": PAGE_SIZE,
            "po": 1,
            "np": 1,
            "fltt": 2,
            "invt": 2,
            "fid": "f12",
            "fs": MARKET_FILTER,
            "fields": "f12,f13,f14",
        }
    )
    request = urllib.request.Request(f"{BASE_URL}?{query}", headers={"User-Agent": "Stockify"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def iter_rows() -> Iterable[dict]:
    page = 1
    total = None
    while True:
        payload = fetch_page(page)
        data = payload.get("data") or {}
        rows = data.get("diff") or []
        if not rows:
            return
        total = total or data.get("total") or 0
        for row in rows:
            yield row
        if total and page * PAGE_SIZE >= total:
            return
        page += 1


def build_entries() -> list[dict]:
    seen: set[str] = set()
    entries: list[dict] = []
    for row in iter_rows():
        code = str(row.get("f12") or "").strip()
        name = str(row.get("f14") or "").strip()
        if not code or not name or code in seen:
            continue
        seen.add(code)
        entries.append(
            {
                "name": to_traditional(name),
                "code": code,
                "market": exchange_for(code, str(row.get("f13") or "")),
                "industry": "",
                "stockType": "股票",
            }
        )
    entries.sort(key=lambda item: item["code"])
    return entries


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    entries = build_entries()
    if not entries:
        print("Eastmoney returned no rows; keeping the existing asset", file=sys.stderr)
        return 1

    args.output.write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(entries)} A-share rows to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
