package com.almasry.market

import org.json.JSONArray
import org.json.JSONObject

data class Ta(
    val rsi: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val macd: Double?,
    val macdSignal: Double?,
    val volatility: Double?,
    val support: Double?,
    val resistance: Double?,
    val score: Int,
    val signal: String,
    val reasons: List<String>,
)

data class Instrument(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePct: Double,
    val weekPct: Double,
    val monthPct: Double,
    val date: String,
    val history: List<Double>,
    val ta: Ta,
)

data class Outlook(val outlook: String, val text: String)
data class Watch(val symbol: String, val market: String, val view: String, val reason: String)
data class GoldGram(val karat: Int, val priceEgp: Double)
data class News(val title: String, val source: String, val link: String, val published: String)

data class Report(
    val headline: String,
    val summary: String,
    val egx: Outlook?,
    val egp: Outlook?,
    val forex: Outlook?,
    val gold: Outlook?,
    val watchlist: List<Watch>,
    val risks: List<String>,
    val events: List<String>,
    val agents: Map<String, String>,
    val generatedBy: String,
    val model: String,
    val created: String,
)

data class Market(
    val updated: String,
    val egxIndices: List<Instrument>,
    val egxStocks: List<Instrument>,
    val egp: List<Instrument>,
    val majors: List<Instrument>,
    val commodities: List<Instrument>,
    val goldEgp: List<GoldGram>,
    val global: List<Instrument>,
    val newsEgypt: List<News>,
    val newsGlobal: List<News>,
    val report: Report?,
    val disclaimer: String,
) {
    fun all(): List<Instrument> = egxIndices + egxStocks + egp + majors + commodities + global
}

private fun JSONObject.d(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.s(key: String): String = if (isNull(key)) "" else optString(key, "")

private inline fun <T> JSONArray?.map(f: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        runCatching { f(o) }.getOrNull()?.let { out.add(it) }
    }
    return out
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it, "").takeIf(String::isNotBlank) }
}

private fun parseTa(o: JSONObject?): Ta {
    val t = o ?: JSONObject()
    return Ta(
        rsi = t.d("rsi"), sma20 = t.d("sma20"), sma50 = t.d("sma50"),
        macd = t.d("macd"), macdSignal = t.d("macd_signal"), volatility = t.d("volatility"),
        support = t.d("support"), resistance = t.d("resistance"),
        score = t.optInt("score", 0), signal = t.s("signal").ifBlank { "محايد" },
        reasons = t.optJSONArray("reasons").strings(),
    )
}

private fun parseInstrument(o: JSONObject): Instrument {
    val h = o.optJSONArray("history")
    val history = if (h == null) emptyList() else (0 until h.length()).map { h.optDouble(it) }.filter { !it.isNaN() }
    return Instrument(
        symbol = o.s("symbol"), name = o.s("name"),
        price = o.d("price") ?: 0.0, change = o.d("change") ?: 0.0,
        changePct = o.d("change_pct") ?: 0.0, weekPct = o.d("week_pct") ?: 0.0,
        monthPct = o.d("month_pct") ?: 0.0, date = o.s("date"),
        history = history, ta = parseTa(o.optJSONObject("ta")),
    )
}

private fun parseOutlook(o: JSONObject?): Outlook? =
    o?.let { Outlook(it.s("outlook").ifBlank { "محايد" }, it.s("text")) }

private fun parseNews(a: JSONArray?) = a.map { News(it.s("title"), it.s("source"), it.s("link"), it.s("published")) }

private fun parseReport(o: JSONObject?): Report? {
    if (o == null) return null
    val agents = mutableMapOf<String, String>()
    o.optJSONObject("agents")?.let { a -> a.keys().forEach { k -> agents[k] = a.optString(k, "") } }
    return Report(
        headline = o.s("headline"), summary = o.s("summary"),
        egx = parseOutlook(o.optJSONObject("egx")), egp = parseOutlook(o.optJSONObject("egp")),
        forex = parseOutlook(o.optJSONObject("forex")), gold = parseOutlook(o.optJSONObject("gold")),
        watchlist = o.optJSONArray("watchlist").map { Watch(it.s("symbol"), it.s("market"), it.s("view"), it.s("reason")) },
        risks = o.optJSONArray("risks").strings(),
        events = o.optJSONArray("events").strings(),
        agents = agents,
        generatedBy = o.s("generated_by"), model = o.s("model"), created = o.s("created"),
    )
}

fun parseMarket(text: String): Market {
    val o = JSONObject(text)
    val egx = o.optJSONObject("egx") ?: JSONObject()
    val fx = o.optJSONObject("fx") ?: JSONObject()
    val news = o.optJSONObject("news") ?: JSONObject()
    return Market(
        updated = o.s("updated"),
        egxIndices = egx.optJSONArray("indices").map(::parseInstrument),
        egxStocks = egx.optJSONArray("stocks").map(::parseInstrument),
        egp = fx.optJSONArray("egp").map(::parseInstrument),
        majors = fx.optJSONArray("majors").map(::parseInstrument),
        commodities = fx.optJSONArray("commodities").map(::parseInstrument),
        goldEgp = fx.optJSONArray("gold_egp").map { GoldGram(it.optInt("karat"), it.d("price_egp") ?: 0.0) },
        global = o.optJSONArray("global").map(::parseInstrument),
        newsEgypt = parseNews(news.optJSONArray("egypt")),
        newsGlobal = parseNews(news.optJSONArray("global")),
        report = parseReport(o.optJSONObject("report")),
        disclaimer = o.s("disclaimer"),
    )
}
