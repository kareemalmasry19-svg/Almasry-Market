"""The analysis team: three specialist agents run in parallel, then a chief strategist
merges their notes into one Arabic report.

LLM provider, in order:
  1. Anthropic Claude, if ANTHROPIC_API_KEY is set
  2. GitHub Models (free, uses the workflow's GITHUB_TOKEN with `models: read`)
Falls back to a rule-based report when neither works."""
from __future__ import annotations

import json
import os
import re
from concurrent.futures import ThreadPoolExecutor

import requests

ANTHROPIC_MODEL = os.environ.get("ANTHROPIC_MODEL") or "claude-sonnet-5-5"
GITHUB_MODEL = os.environ.get("GITHUB_MODEL") or "openai/gpt-4.1"
GITHUB_ENDPOINT = "https://models.github.ai/inference/chat/completions"


def provider() -> str | None:
    if os.environ.get("ANTHROPIC_API_KEY"):
        return "anthropic"
    if os.environ.get("GITHUB_TOKEN"):
        return "github"
    return None

COMMON = (
    "أنت عضو في فريق تحليل أسواق لتطبيق عربي اسمه «المصري ماركت». "
    "اعتمد فقط على البيانات والأخبار المرفقة، ولا تخترع أرقاماً أو أحداثاً غير موجودة فيها. "
    "اكتب بالعربية المبسطة الواضحة. هذا تحليل للمساعدة في الفهم وليس توصية استثمارية ملزمة."
)

TECHNICAL = COMMON + (
    " دورك: المحلل الفني. حلل الاتجاه والزخم ومستويات الدعم والمقاومة لمؤشر EGX30 والأسهم المصرية "
    "وأزواج الفوركس والذهب والنفط، باستخدام RSI وMACD والمتوسطات المرفقة. "
    "اذكر أقوى 3 فرص صعود وأوضح 3 إشارات ضعف، مع السبب الفني لكل واحدة. لا تتجاوز 250 كلمة."
)

NEWS = COMMON + (
    " دورك: محلل الأخبار والمشاعر. اقرأ العناوين المرفقة وحدد المزاج العام للسوق المصري والعالمي، "
    "وأهم 5 أخبار مؤثرة وأي أصول تتأثر بكل خبر (أسهم مصرية، الجنيه، الدولار، الذهب). "
    "اذكر أي أحداث قادمة مذكورة صراحة في الأخبار (اجتماعات بنوك مركزية، بيانات تضخم...). لا تتجاوز 250 كلمة."
)

MACRO = COMMON + (
    " دورك: محلل الاقتصاد الكلي والعملات. حلل سعر الجنيه مقابل الدولار واليورو والريال، "
    "ومؤشر الدولار والذهب والنفط، والأسواق العالمية، واشرح كيف تنعكس على البورصة المصرية "
    "(مثلاً الشركات المصدّرة مقابل المستوردة، البنوك، العقارات). لا تتجاوز 250 كلمة."
)

CHIEF = COMMON + (
    " دورك: كبير الاستراتيجيين ومدير المخاطر. استلمت ملاحظات المحلل الفني ومحلل الأخبار ومحلل الاقتصاد الكلي. "
    "وازن بينها، وإذا تعارضت اذكر ذلك، وقيّم المخاطر. أخرج JSON فقط بدون أي نص قبله أو بعده، بالشكل التالي:\n"
    '{"headline": "عنوان قصير لليوم",'
    ' "summary": "ملخص من 3 إلى 5 جمل",'
    ' "egx": {"outlook": "إيجابي|سلبي|محايد", "text": "جملتان أو ثلاث"},'
    ' "egp": {"outlook": "إيجابي|سلبي|محايد", "text": "..."},'
    ' "forex": {"outlook": "إيجابي|سلبي|محايد", "text": "..."},'
    ' "gold": {"outlook": "إيجابي|سلبي|محايد", "text": "..."},'
    ' "watchlist": [{"symbol": "COMI", "market": "egx|fx|commodity", "view": "صعود|هبوط|مراقبة", "reason": "..."}],'
    ' "risks": ["..."],'
    ' "events": ["..."]}\n'
    "قائمة المراقبة 6 عناصر كحد أقصى ورموزها من البيانات المرفقة فقط. "
    "نظرة الجنيه «إيجابي» تعني قوة الجنيه، ونظرة الفوركس تخص الدولار الأمريكي."
)


def _compact(rows: list[dict]) -> list[dict]:
    out = []
    for r in rows:
        ta = r.get("ta", {})
        out.append({
            "s": r["symbol"], "p": r["price"], "d%": r["change_pct"], "w%": r["week_pct"], "m%": r["month_pct"],
            "rsi": ta.get("rsi"), "sma20": ta.get("sma20"), "sma50": ta.get("sma50"),
            "macd>sig": None if ta.get("macd") is None else ta["macd"] > ta["macd_signal"],
            "sup": ta.get("support"), "res": ta.get("resistance"), "score": ta.get("score"),
        })
    return out


def _ask(client, system: str, content: str, max_tokens: int = 1200) -> str:
    if client == "github":
        r = requests.post(
            GITHUB_ENDPOINT,
            headers={
                "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
                "Accept": "application/vnd.github+json",
                "Content-Type": "application/json",
            },
            json={
                "model": GITHUB_MODEL, "max_tokens": max_tokens, "temperature": 0.3,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": content}],
            },
            timeout=120,
        )
        try:
            data = r.json()
        except ValueError:
            data = None
        if r.status_code >= 400 or not data:
            raise RuntimeError(
                f"GitHub Models {r.status_code} [{r.headers.get('content-type')}] {r.url}: {r.text[:300]!r}")
        return data["choices"][0]["message"]["content"].strip()
    msg = client.messages.create(
        model=ANTHROPIC_MODEL, max_tokens=max_tokens, system=system,
        messages=[{"role": "user", "content": content}],
    )
    return "".join(b.text for b in msg.content if getattr(b, "type", "") == "text").strip()


def _parse_json(text: str) -> dict:
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        raise ValueError("no JSON in chief output")
    return json.loads(m.group(0))


def ai_report(market: dict) -> dict:
    if provider() == "anthropic":
        import anthropic
        client = anthropic.Anthropic()
        model = ANTHROPIC_MODEL
    else:
        client = "github"
        model = GITHUB_MODEL
    prices = {
        "egx_indices": _compact(market["egx"]["indices"]),
        "egx_stocks": _compact(market["egx"]["stocks"]),
        "egp": _compact(market["fx"]["egp"]),
        "fx_majors": _compact(market["fx"]["majors"]),
        "commodities": _compact(market["fx"]["commodities"]),
        "global_indices": _compact(market["global"]),
        "gold_egp_per_gram": market["fx"].get("gold_egp", []),
    }
    headlines = {
        k: [n["title"] for n in v[:20]] for k, v in market["news"].items()
    }
    prices_txt = json.dumps(prices, ensure_ascii=False, separators=(",", ":"))
    symbols = [r["s"] for v in prices.values() if isinstance(v, list) for r in v if isinstance(r, dict) and "s" in r]
    news_txt = json.dumps(headlines, ensure_ascii=False)

    with ThreadPoolExecutor(3) as pool:
        tech = pool.submit(_ask, client, TECHNICAL, "البيانات:\n" + prices_txt)
        news = pool.submit(_ask, client, NEWS, "العناوين:\n" + news_txt)
        macro = pool.submit(_ask, client, MACRO, "البيانات:\n" + prices_txt + "\n\nالعناوين:\n" + news_txt)
        notes = {"technical": tech.result(), "news": news.result(), "macro": macro.result()}

    chief_in = (
        f"ملاحظات المحلل الفني:\n{notes['technical']}\n\n"
        f"ملاحظات محلل الأخبار:\n{notes['news']}\n\n"
        f"ملاحظات محلل الاقتصاد الكلي:\n{notes['macro']}\n\n"
        f"الرموز المتاحة لقائمة المراقبة: {', '.join(symbols)}"
    )
    report = _parse_json(_ask(client, CHIEF, chief_in, max_tokens=2000))
    report["agents"] = notes
    report["generated_by"] = "ai"
    report["model"] = model
    return report


def _outlook(score: float, invert: bool = False) -> str:
    if invert:
        score = -score
    return "إيجابي" if score >= 15 else "سلبي" if score <= -15 else "محايد"


def rules_report(market: dict) -> dict:
    """Plain technical summary used when AI is unavailable."""
    def find(rows, code):
        return next((r for r in rows if r["symbol"] == code), None)

    egx30 = find(market["egx"]["indices"], "EGX30")
    usd = find(market["fx"]["egp"], "USD/EGP")
    dxy = find(market["fx"]["majors"], "DXY")
    gold = find(market["fx"]["commodities"], "XAU/USD")

    def line(r, label):
        if not r:
            return {"outlook": "محايد", "text": f"لا تتوفر بيانات {label} حالياً."}
        ta = r["ta"]
        return {
            "outlook": _outlook(ta["score"]),
            "text": f"{label} عند {r['price']:,.{2 if r['price'] >= 10 else 4}f} ({r['change_pct']:+.2f}% اليوم، {r['week_pct']:+.2f}% أسبوعياً). "
                    f"الإشارة الفنية: {ta['signal']}. " + "، ".join(ta["reasons"][:3]),
        }

    egp = line(usd, "الدولار مقابل الجنيه")
    if usd:  # a rising USD/EGP means a weaker pound
        egp["outlook"] = _outlook(usd["ta"]["score"], invert=True)

    stocks = sorted(market["egx"]["stocks"], key=lambda r: r["ta"]["score"], reverse=True)
    fx = sorted(market["fx"]["majors"], key=lambda r: abs(r["ta"]["score"]), reverse=True)
    watch = [
        {"symbol": r["symbol"], "market": "egx", "view": "صعود", "reason": r["ta"]["signal"] + " — " + "، ".join(r["ta"]["reasons"][:2])}
        for r in stocks[:3] if r["ta"]["score"] > 0
    ] + [
        {"symbol": r["symbol"], "market": "egx", "view": "هبوط", "reason": r["ta"]["signal"] + " — " + "، ".join(r["ta"]["reasons"][:2])}
        for r in stocks[-2:] if r["ta"]["score"] < 0
    ] + [
        {"symbol": r["symbol"], "market": "fx", "view": "صعود" if r["ta"]["score"] > 0 else "هبوط", "reason": r["ta"]["signal"]}
        for r in fx[:1]
    ]

    headline = "ملخص فني تلقائي"
    if egx30:
        headline = f"EGX30 {egx30['change_pct']:+.2f}% — الإشارة {egx30['ta']['signal']}"
    return {
        "headline": headline,
        "summary": "تقرير مبني على المؤشرات الفنية فقط (RSI وMACD والمتوسطات). "
                   "فريق الـ AI لم يكن متاحاً في هذا التحديث، وسيحاول مرة أخرى في الموعد القادم.",
        "egx": line(egx30, "مؤشر EGX30"),
        "egp": egp,
        "forex": line(dxy, "مؤشر الدولار"),
        "gold": line(gold, "الذهب"),
        "watchlist": watch[:6],
        "risks": ["التحليل الفني وحده لا يأخذ الأخبار والقرارات الاقتصادية في الاعتبار."],
        "events": [],
        "generated_by": "rules",
    }


def build_report(market: dict) -> dict:
    if provider():
        try:
            return ai_report(market)
        except Exception as e:
            print(f"[warn] AI report failed, using rules: {e}")
            r = rules_report(market)
            r["ai_error"] = str(e)[:400]
            return r
    return rules_report(market)
