"""Sends each new report to WhatsApp through the free CallMeBot API.

Env (GitHub secrets, never committed — the repo is public):
  WHATSAPP_PHONE    number with country code, e.g. 2010xxxxxxxx (no + or leading 00)
  CALLMEBOT_APIKEY  key CallMeBot sends after you message the bot
"""
from __future__ import annotations

import os

import requests

API = "https://api.callmebot.com/whatsapp.php"
APK = "https://github.com/{repo}/releases/download/latest/AlmasryMarket.apk"
MAX_CHARS = 1500

ICON = {"إيجابي": "🟢", "سلبي": "🔴", "محايد": "🟡", "صعود": "🟢", "هبوط": "🔴", "مراقبة": "🟡"}


def _phone(raw: str) -> str:
    p = "".join(ch for ch in raw if ch.isdigit())
    if p.startswith("00"):
        p = p[2:]
    if p.startswith("0"):  # local Egyptian format 01xxxxxxxxx
        p = "20" + p[1:]
    return "+" + p


def _find(rows: list[dict], code: str) -> dict | None:
    return next((r for r in rows if r["symbol"] == code), None)


def format_message(market: dict) -> str:
    r = market["report"]
    lines = [f"📊 *المصري ماركت*", f"*{r.get('headline', '')}*", "", r.get("summary", ""), ""]

    labels = [("egx", "البورصة"), ("egp", "الجنيه"), ("forex", "الدولار"), ("gold", "الذهب")]
    lines.append(" | ".join(
        f"{ICON.get(r[k]['outlook'], '🟡')} {name}: {r[k]['outlook']}" for k, name in labels if isinstance(r.get(k), dict)
    ))

    prices = []
    egx30 = _find(market["egx"]["indices"], "EGX30")
    usd = _find(market["fx"]["egp"], "USD/EGP")
    gold = _find(market["fx"]["commodities"], "XAU/USD")
    if egx30:
        prices.append(f"EGX30 {egx30['price']:,.0f} ({egx30['change_pct']:+.2f}%)")
    if usd:
        prices.append(f"الدولار {usd['price']:.2f} ج ({usd['change_pct']:+.2f}%)")
    g21 = next((g for g in market["fx"].get("gold_egp", []) if g["karat"] == 21), None)
    if g21:
        prices.append(f"عيار 21 ≈ {g21['price_egp']:,.0f} ج")
    if gold:
        prices.append(f"الأوقية ${gold['price']:,.0f}")
    if prices:
        lines += ["", "💹 " + " • ".join(prices)]

    if r.get("watchlist"):
        lines += ["", "*قائمة المراقبة:*"]
        lines += [f"{ICON.get(w['view'], '🟡')} {w['symbol']}: {w['view']}" for w in r["watchlist"][:6]]

    if r.get("risks"):
        lines += ["", "⚠️ " + r["risks"][0]]

    repo = os.environ.get("GITHUB_REPOSITORY")
    tail = ["", "التفاصيل في التطبيق." + (f" تحميل: {APK.format(repo=repo)}" if repo else ""), "_ليست نصيحة استثمارية._"]
    text = "\n".join(lines)
    budget = MAX_CHARS - len("\n".join(tail))
    if len(text) > budget:
        text = text[: budget - 1] + "…"
    return text + "\n" + "\n".join(tail)


def send(market: dict) -> None:
    phone, key = os.environ.get("WHATSAPP_PHONE"), os.environ.get("CALLMEBOT_APIKEY")
    if not phone or not key:
        print("[info] WhatsApp not configured (WHATSAPP_PHONE / CALLMEBOT_APIKEY)")
        return
    try:
        resp = requests.get(API, params={"phone": _phone(phone), "text": format_message(market), "apikey": key}, timeout=60)
        ok = resp.status_code == 200 and "error" not in resp.text.lower()
        print(f"[{'ok' if ok else 'warn'}] WhatsApp {resp.status_code}: {resp.text[:200]}")
    except Exception as e:
        print(f"[warn] WhatsApp failed: {e}")
