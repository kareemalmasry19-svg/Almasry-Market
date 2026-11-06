"""Technical indicators computed from a list of daily closes (oldest first)."""
from __future__ import annotations

import math


def _ema(values: list[float], period: int) -> list[float]:
    k = 2 / (period + 1)
    out, prev = [], None
    for v in values:
        prev = v if prev is None else v * k + prev * (1 - k)
        out.append(prev)
    return out


def sma(values: list[float], period: int) -> float | None:
    if len(values) < period:
        return None
    return sum(values[-period:]) / period


def rsi(values: list[float], period: int = 14) -> float | None:
    if len(values) <= period:
        return None
    gains, losses = 0.0, 0.0
    for i in range(1, period + 1):
        d = values[i] - values[i - 1]
        gains += max(d, 0)
        losses += max(-d, 0)
    avg_g, avg_l = gains / period, losses / period
    for i in range(period + 1, len(values)):
        d = values[i] - values[i - 1]
        avg_g = (avg_g * (period - 1) + max(d, 0)) / period
        avg_l = (avg_l * (period - 1) + max(-d, 0)) / period
    if avg_l == 0:
        return 100.0
    return 100 - 100 / (1 + avg_g / avg_l)


def macd(values: list[float]) -> tuple[float, float] | None:
    if len(values) < 35:
        return None
    line = [a - b for a, b in zip(_ema(values, 12), _ema(values, 26))]
    signal = _ema(line, 9)
    return line[-1], signal[-1]


def volatility(values: list[float], period: int = 20) -> float | None:
    """Annualised volatility (%) of daily returns."""
    if len(values) < period + 1:
        return None
    rets = [math.log(values[i] / values[i - 1]) for i in range(len(values) - period, len(values))
            if values[i - 1] > 0 and values[i] > 0]
    if len(rets) < 2:
        return None
    m = sum(rets) / len(rets)
    var = sum((r - m) ** 2 for r in rets) / (len(rets) - 1)
    return math.sqrt(var) * math.sqrt(252) * 100


def analyse(closes: list[float]) -> dict:
    """Indicator snapshot plus a simple rule-based score from -100 (bearish) to +100 (bullish)."""
    last = closes[-1]
    s20, s50 = sma(closes, 20), sma(closes, 50)
    r = rsi(closes)
    m = macd(closes)
    vol = volatility(closes)
    hi = max(closes[-20:]) if len(closes) >= 20 else None
    lo = min(closes[-20:]) if len(closes) >= 20 else None

    score, reasons = 0, []
    if s20 is not None:
        if last > s20:
            score += 20; reasons.append("السعر فوق متوسط 20 يوم")
        else:
            score -= 20; reasons.append("السعر تحت متوسط 20 يوم")
    if s20 is not None and s50 is not None:
        if s20 > s50:
            score += 20; reasons.append("متوسط 20 فوق متوسط 50 (اتجاه صاعد)")
        else:
            score -= 20; reasons.append("متوسط 20 تحت متوسط 50 (اتجاه هابط)")
    if m is not None:
        if m[0] > m[1]:
            score += 20; reasons.append("MACD إيجابي")
        else:
            score -= 20; reasons.append("MACD سلبي")
    if r is not None:
        if r >= 70:
            score -= 25; reasons.append(f"RSI {r:.0f}: تشبع شرائي")
        elif r <= 30:
            score += 25; reasons.append(f"RSI {r:.0f}: تشبع بيعي")
        elif r > 55:
            score += 10
        elif r < 45:
            score -= 10
    if len(closes) >= 6 and closes[-6] > 0:
        mom = (last / closes[-6] - 1) * 100
        score += max(-15, min(15, mom * 3))

    score = int(max(-100, min(100, score)))
    if score >= 40:
        signal = "صعود قوي"
    elif score >= 15:
        signal = "صعود"
    elif score > -15:
        signal = "محايد"
    elif score > -40:
        signal = "هبوط"
    else:
        signal = "هبوط قوي"

    def rnd(x, n=4):
        return None if x is None else round(x, n)

    return {
        "rsi": rnd(r, 1),
        "sma20": rnd(s20),
        "sma50": rnd(s50),
        "macd": rnd(m[0], 5) if m else None,
        "macd_signal": rnd(m[1], 5) if m else None,
        "volatility": rnd(vol, 1),
        "support": rnd(lo),
        "resistance": rnd(hi),
        "score": score,
        "signal": signal,
        "reasons": reasons,
    }
