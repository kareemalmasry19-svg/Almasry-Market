"""Market data and news collection. Every source is optional: a failure is logged and skipped."""
from __future__ import annotations

import datetime as dt
import html
import re
import urllib.parse

import feedparser
import yfinance as yf

from indicators import analyse

HISTORY_POINTS = 60  # closes sent to the app for the chart


def _closes(symbol: str) -> tuple[list[float], list[str]]:
    hist = yf.Ticker(symbol).history(period="9mo", interval="1d", auto_adjust=False)
    if hist is None or hist.empty:
        return [], []
    hist = hist.dropna(subset=["Close"])
    closes = [float(c) for c in hist["Close"].tolist()]
    dates = [d.strftime("%Y-%m-%d") for d in hist.index]
    return closes, dates


def instrument(symbol: str, code: str, name: str) -> dict | None:
    try:
        closes, dates = _closes(symbol)
    except Exception as e:  # network, delisted, rate limit…
        print(f"[skip] {symbol}: {e}")
        return None
    if len(closes) < 2:
        print(f"[skip] {symbol}: no data")
        return None
    last, prev = closes[-1], closes[-2]
    week = closes[-6] if len(closes) >= 6 else closes[0]
    month = closes[-22] if len(closes) >= 22 else closes[0]
    return {
        "symbol": code,
        "yahoo": symbol,
        "name": name,
        "price": round(last, 4),
        "change": round(last - prev, 4),
        "change_pct": round((last / prev - 1) * 100, 2) if prev else 0.0,
        "week_pct": round((last / week - 1) * 100, 2) if week else 0.0,
        "month_pct": round((last / month - 1) * 100, 2) if month else 0.0,
        "date": dates[-1],
        "history": [round(c, 4) for c in closes[-HISTORY_POINTS:]],
        "ta": analyse(closes),
    }


def group(items: list[tuple[str, str, str]]) -> list[dict]:
    out = []
    for sym, code, name in items:
        row = instrument(sym, code, name)
        if row:
            out.append(row)
            print(f"[ok] {code} {row['price']} ({row['change_pct']:+.2f}%)")
    return out


def egx30_fallback() -> dict | None:
    """Uses the community `egx` package if Yahoo has no EGX30 data."""
    try:
        import egx  # type: ignore
        df = egx.get_index_data("EGX30", period=270)
        col = next(c for c in df.columns if "close" in c.lower() or "value" in c.lower())
        closes = [float(x) for x in df[col].dropna().tolist()]
        if len(closes) < 2:
            return None
        last, prev = closes[-1], closes[-2]
        return {
            "symbol": "EGX30", "yahoo": "egx", "name": "مؤشر EGX30",
            "price": round(last, 2), "change": round(last - prev, 2),
            "change_pct": round((last / prev - 1) * 100, 2),
            "week_pct": round((last / closes[max(0, len(closes) - 6)] - 1) * 100, 2),
            "month_pct": round((last / closes[max(0, len(closes) - 22)] - 1) * 100, 2),
            "date": dt.date.today().isoformat(),
            "history": [round(c, 2) for c in closes[-HISTORY_POINTS:]],
            "ta": analyse(closes),
        }
    except Exception as e:
        print(f"[skip] egx fallback: {e}")
        return None


def local_gold(gold_usd: dict | None, usd_egp: dict | None) -> list[dict]:
    """Theoretical gold price per gram in EGP (no workmanship or dealer margin)."""
    if not gold_usd or not usd_egp:
        return []
    per_gram_24 = gold_usd["price"] * usd_egp["price"] / 31.1035
    return [
        {"karat": k, "price_egp": round(per_gram_24 * k / 24, 0)} for k in (24, 21, 18)
    ]


_TAG = re.compile(r"<[^>]+>")


def _clean(text: str) -> str:
    return html.unescape(_TAG.sub("", text or "")).strip()


def news(queries: list[tuple[str, str, str]], limit: int = 25) -> list[dict]:
    seen, items = set(), []
    for q, lang, country in queries:
        url = (
            "https://news.google.com/rss/search?q="
            + urllib.parse.quote(f"{q} when:2d")
            + f"&hl={lang}&gl={country}&ceid={country}:{lang}"
        )
        try:
            feed = feedparser.parse(url)
        except Exception as e:
            print(f"[skip] news {q}: {e}")
            continue
        for e in feed.entries[:15]:
            title = _clean(e.get("title", ""))
            source = _clean(e.get("source", {}).get("title", "")) if e.get("source") else ""
            if source and title.endswith(" - " + source):
                title = title[: -len(source) - 3]
            key = title[:60]
            if not title or key in seen:
                continue
            seen.add(key)
            published = ""
            if e.get("published_parsed"):
                published = dt.datetime(*e.published_parsed[:6], tzinfo=dt.timezone.utc).isoformat()
            items.append({"title": title, "source": source, "link": e.get("link", ""), "published": published})
    items.sort(key=lambda x: x["published"], reverse=True)
    print(f"[ok] news: {len(items)} items")
    return items[:limit]
