"""Entry point: collect data, (re)build the report, write data/market.json for the app.

Env:
  OUT                 output path (default ../data/market.json)
  ANTHROPIC_API_KEY   enables the AI agents team
  FORCE_REPORT=1      rebuild the report now
  REPORT_HOURS_UTC    hours when the report is rebuilt (default 7,11,15,19 = 9/13/17/21 Cairo)
"""
from __future__ import annotations

import datetime as dt
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import fetch  # noqa: E402
import universe as u  # noqa: E402
from agents import build_report, provider  # noqa: E402

OUT = Path(os.environ.get("OUT", Path(__file__).parent.parent / "data" / "market.json"))


def collect() -> dict:
    indices = fetch.group(u.EGX_INDICES)
    if not indices:
        fb = fetch.egx30_fallback()
        indices = [fb] if fb else []
    egp = fetch.group(u.EGP_PAIRS)
    commodities = fetch.group(u.COMMODITIES)
    gold = next((r for r in commodities if r["symbol"] == "XAU/USD"), None)
    usd = next((r for r in egp if r["symbol"] == "USD/EGP"), None)
    return {
        "egx": {"indices": indices, "stocks": fetch.group(u.EGX_STOCKS)},
        "fx": {
            "egp": egp,
            "majors": fetch.group(u.FX_MAJORS),
            "commodities": commodities,
            "gold_egp": fetch.local_gold(gold, usd),
        },
        "global": fetch.group(u.GLOBAL_INDICES),
        "news": {k: fetch.news(v) for k, v in u.NEWS_FEEDS.items()},
    }


def main() -> None:
    now = dt.datetime.now(dt.timezone.utc)
    previous = {}
    if OUT.exists():
        try:
            previous = json.loads(OUT.read_text(encoding="utf-8"))
        except Exception:
            previous = {}

    market = collect()
    total = sum(len(market["egx"][k]) for k in market["egx"]) + sum(
        len(v) for k, v in market["fx"].items() if k != "gold_egp")
    if total == 0 and previous:
        print("[error] no market data at all; keeping previous file")
        sys.exit(1)

    hours = {int(h) for h in os.environ.get("REPORT_HOURS_UTC", "7,11,15,19").split(",") if h.strip()}
    old_report = previous.get("report")
    want_ai = provider() is not None

    def age_hours(r: dict) -> float:
        try:
            return (now - dt.datetime.fromisoformat(r["created"])).total_seconds() / 3600
        except Exception:
            return 99.0

    rebuild = (
        os.environ.get("FORCE_REPORT") == "1"
        or not old_report
        or now.hour in hours
        # AI failed last time: retry, but at most every 2 hours to respect free-tier limits
        or (want_ai and old_report.get("generated_by") != "ai" and age_hours(old_report) >= 2)
    )
    if rebuild:
        report = build_report(market)
        report["created"] = now.isoformat(timespec="seconds")
    else:
        report = old_report
        print("[info] keeping previous report")

    market["report"] = report
    market["updated"] = now.isoformat(timespec="seconds")
    market["disclaimer"] = "تحليل للمساعدة في الفهم وليس نصيحة استثمارية. البيانات من مصادر عامة وقد تتأخر."
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(market, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"[done] wrote {OUT} ({OUT.stat().st_size // 1024} KB), report={report.get('generated_by')}")


if __name__ == "__main__":
    main()
