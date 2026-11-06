@file:OptIn(ExperimentalMaterial3Api::class)

package com.almasry.market

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val title: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("التقرير", Icons.Filled.Insights),
    Tab("البورصة", Icons.AutoMirrored.Filled.ShowChart),
    Tab("العملات", Icons.Filled.CurrencyExchange),
    Tab("الأخبار", Icons.Filled.Newspaper),
)

@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Instrument?>(null) }
    BackHandler(enabled = detail != null) { detail = null }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text("المصري ماركت", color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                val m = state.market
                                if (m != null) {
                                    Text("آخر تحديث ${ago(m.updated)}", color = Muted, fontSize = 12.sp)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = vm::refresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = Gold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                    )
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Gold)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = InkRaised) {
                    Tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.title) },
                            label = { Text(t.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = Gold,
                                indicatorColor = Gold,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                val m = state.market
                when {
                    m == null && state.error != null -> Message(state.error!!)
                    m == null -> Message("جاري تحميل بيانات السوق…")
                    else -> when (tab) {
                        0 -> ReportTab(m, state.error) { detail = it }
                        1 -> EgxTab(m) { detail = it }
                        2 -> FxTab(m) { detail = it }
                        else -> NewsTab(m)
                    }
                }
            }
        }

        AnimatedVisibility(visible = detail != null) {
            detail?.let { DetailScreen(it) { detail = null } }
        }
    }
}

@Composable
private fun Message(text: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item { Text(text, color = Muted, modifier = Modifier.fillMaxWidth()) }
    }
}

// ---------- shared pieces ----------

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = InkRaised,
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.border(1.dp, GoldDeep.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(14.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Text(text, color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun InstrumentRow(i: Instrument, onClick: () -> Unit) {
    Card(Modifier.padding(vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.name, color = Cream, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ltr(i.symbol), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(i.ta.signal, color = outlookColor(i.ta.signal), fontSize = 12.sp)
                }
            }
            Sparkline(i.history.takeLast(30), Modifier.width(64.dp).height(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                Text(price(i.price), color = Cream, fontWeight = FontWeight.Bold)
                Text(pct(i.changePct), color = changeColor(i.changePct), fontSize = 13.sp)
            }
        }
    }
}

private fun LazyListScope.instruments(title: String, list: List<Instrument>, sub: String? = null, onOpen: (Instrument) -> Unit) {
    if (list.isEmpty()) return
    item { SectionTitle(title, sub) }
    items(list, key = { title + it.symbol }) { InstrumentRow(it) { onOpen(it) } }
}

// ---------- Report ----------

@Composable
private fun ReportTab(m: Market, error: String?, onOpen: (Instrument) -> Unit) {
    val r = m.report
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (error != null) item { Text("تعذر التحديث: $error — بيعرض آخر نسخة محفوظة", color = Down, fontSize = 12.sp) }
        if (r == null) {
            item { Message("لسه مفيش تقرير.") }
            return@LazyColumn
        }
        item {
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.generatedBy == "ai") {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تحليل فريق الذكاء الاصطناعي", color = Gold, fontSize = 12.sp)
                        } else {
                            Text("تحليل فني تلقائي", color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(ago(r.created), color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(r.headline, color = Cream, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(r.summary, color = Cream.copy(alpha = 0.9f), lineHeight = 22.sp)
                }
            }
        }
        item {
            SectionTitle("النظرة العامة")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlookCard("البورصة المصرية", r.egx, Modifier.weight(1f))
                    OutlookCard("الجنيه المصري", r.egp, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlookCard("الدولار والفوركس", r.forex, Modifier.weight(1f))
                    OutlookCard("الذهب", r.gold, Modifier.weight(1f))
                }
            }
        }
        if (r.watchlist.isNotEmpty()) {
            item { SectionTitle("قائمة المراقبة") }
            items(r.watchlist, key = { "w" + it.symbol + it.view }) { w ->
                val inst = m.all().firstOrNull { it.symbol.equals(w.symbol, ignoreCase = true) }
                Card(Modifier.padding(vertical = 4.dp), onClick = if (inst != null) ({ onOpen(inst) }) else null) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ltr(w.symbol), color = Cream, fontWeight = FontWeight.Bold)
                            if (inst != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(inst.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                            }
                            Spacer(Modifier.weight(1f))
                            Pill(w.view, outlookColor(w.view))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(w.reason, color = Cream.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
        if (r.risks.isNotEmpty()) {
            item { SectionTitle("المخاطر") }
            items(r.risks) { Bullet(it, Down) }
        }
        if (r.events.isNotEmpty()) {
            item { SectionTitle("أحداث قادمة") }
            items(r.events) { Bullet(it, Gold) }
        }
        if (r.agents.isNotEmpty()) {
            item { SectionTitle("ملاحظات فريق التحليل") }
            val names = mapOf("technical" to "المحلل الفني", "news" to "محلل الأخبار", "macro" to "محلل الاقتصاد والعملات")
            items(r.agents.entries.toList(), key = { "a" + it.key }) { (k, v) -> AgentNote(names[k] ?: k, v) }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text(m.disclaimer, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OutlookCard(title: String, o: Outlook?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Card(modifier, onClick = { open = !open }) {
        Column {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            val label = o?.outlook ?: "—"
            Text(label, color = outlookColor(label), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (o != null && o.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    o.text, color = Cream.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp,
                    maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String, color: Color) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Cream.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun AgentNote(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.padding(vertical = 4.dp), onClick = { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Cream, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Gold)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text(text, color = Cream.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

// ---------- EGX ----------

@Composable
private fun EgxTab(m: Market, onOpen: (Instrument) -> Unit) {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val stocks = when (sort) {
        0 -> m.egxStocks.sortedByDescending { it.changePct }
        1 -> m.egxStocks.sortedBy { it.changePct }
        else -> m.egxStocks.sortedByDescending { it.ta.score }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            val open = egxOpen()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (open) "السوق مفتوح" else "السوق مغلق", if (open) Up else Muted)
                Spacer(Modifier.width(8.dp))
                Text("الأحد–الخميس ${ltr("10:00–14:30")}", color = Muted, fontSize = 12.sp)
            }
        }
        items(m.egxIndices, key = { "i" + it.symbol }) { IndexCard(it) { onOpen(it) } }
        if (m.egxStocks.isNotEmpty()) {
            item {
                SectionTitle("الأسهم")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("الأكثر ارتفاعاً", "الأكثر انخفاضاً", "أقوى إشارة").forEachIndexed { i, t ->
                        FilterChip(
                            selected = sort == i, onClick = { sort = i }, label = { Text(t, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold, selectedLabelColor = Color.Black, labelColor = Muted,
                            ),
                        )
                    }
                }
            }
            items(stocks, key = { "s" + it.symbol }) { InstrumentRow(it) { onOpen(it) } }
        }
    }
}

@Composable
private fun IndexCard(i: Instrument, onClick: () -> Unit) {
    Card(Modifier.padding(top = 12.dp), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.name, color = Muted, modifier = Modifier.weight(1f))
                Pill(i.ta.signal, outlookColor(i.ta.signal))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price(i.price), color = Cream, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Text(pct(i.changePct), color = changeColor(i.changePct), fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            PriceChart(i.history, Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp))
            Row(Modifier.padding(top = 6.dp)) {
                Text("أسبوع ${pct(i.weekPct)}", color = changeColor(i.weekPct), fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
                Text("شهر ${pct(i.monthPct)}", color = changeColor(i.monthPct), fontSize = 12.sp)
            }
        }
    }
}

// ---------- FX ----------

@Composable
private fun FxTab(m: Market, onOpen: (Instrument) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            val s = forexSessions()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (s.isEmpty()) "الفوركس مغلق" else "الفوركس مفتوح", if (s.isEmpty()) Muted else Up)
                if (s.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("الجلسات: ${s.joinToString("، ")}", color = Muted, fontSize = 12.sp)
                }
            }
        }
        instruments("الجنيه المصري", m.egp, onOpen = onOpen)
        if (m.goldEgp.isNotEmpty()) {
            item {
                SectionTitle("الذهب في مصر", "سعر نظري للجرام بالجنيه من سعر الأوقية والدولار، من غير مصنعية")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.goldEgp.forEach { g ->
                        Card(Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text("عيار ${ltr(g.karat.toString())}", color = Muted, fontSize = 12.sp)
                                Text(price(g.priceEgp).replace(".00", ""), color = GoldLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        instruments("العملات الرئيسية", m.majors, onOpen = onOpen)
        instruments("السلع", m.commodities, onOpen = onOpen)
        instruments("الأسواق العالمية", m.global, onOpen = onOpen)
    }
}

// ---------- News ----------

@Composable
private fun NewsTab(m: Market) {
    var which by rememberSaveable { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    val list = if (which == 0) m.newsEgypt else m.newsGlobal
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مصر", "العالم").forEachIndexed { i, t ->
                    FilterChip(
                        selected = which == i, onClick = { which = i }, label = { Text(t) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold, selectedLabelColor = Color.Black, labelColor = Muted,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (list.isEmpty()) item { Text("مفيش أخبار حالياً.", color = Muted) }
        items(list, key = { "n" + which + it.link + it.title }) { n ->
            Card(Modifier.padding(vertical = 4.dp), onClick = { if (n.link.isNotBlank()) runCatching { uri.openUri(n.link) } }) {
                Column {
                    Text(n.title, color = Cream, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(n.source, color = Gold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(ago(n.published), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------- Detail ----------

@Composable
private fun DetailScreen(i: Instrument, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Gold)
                        }
                    },
                    title = {
                        Column {
                            Text(i.name, color = Cream, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ltr(i.symbol), color = Muted, fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                )
            },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(price(i.price), color = Cream, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(pct(i.changePct), color = changeColor(i.changePct), fontSize = 18.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text("آخر سعر بتاريخ ${ltr(i.date)}", color = Muted, fontSize = 12.sp)
                    PriceChart(
                        i.history, Modifier.fillMaxWidth().height(220.dp).padding(vertical = 12.dp),
                        support = i.ta.support, resistance = i.ta.resistance,
                    )
                    Text("الخط الأخضر المتقطع: الدعم — الأحمر: المقاومة (آخر 20 يوم)", color = Muted, fontSize = 11.sp)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("يوم", pct(i.changePct), changeColor(i.changePct), Modifier.weight(1f))
                        Stat("أسبوع", pct(i.weekPct), changeColor(i.weekPct), Modifier.weight(1f))
                        Stat("شهر", pct(i.monthPct), changeColor(i.monthPct), Modifier.weight(1f))
                    }
                }
                item {
                    SectionTitle("التحليل الفني")
                    Card {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الإشارة", color = Muted, modifier = Modifier.weight(1f))
                                Pill(i.ta.signal, outlookColor(i.ta.signal))
                            }
                            Spacer(Modifier.height(8.dp))
                            ScoreBar(i.ta.score)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    val t = i.ta
                    val rows = listOf(
                        "RSI (14)" to t.rsi?.let { ltr(String.format(java.util.Locale.US, "%.1f", it)) },
                        "متوسط 20 يوم" to t.sma20?.let(::price),
                        "متوسط 50 يوم" to t.sma50?.let(::price),
                        "MACD" to t.macd?.let { m -> ltr(String.format(java.util.Locale.US, "%.4f", m)) },
                        "إشارة MACD" to t.macdSignal?.let { m -> ltr(String.format(java.util.Locale.US, "%.4f", m)) },
                        "التذبذب السنوي" to t.volatility?.let { v -> ltr(String.format(java.util.Locale.US, "%.1f%%", v)) },
                        "الدعم" to t.support?.let(::price),
                        "المقاومة" to t.resistance?.let(::price),
                    ).filter { it.second != null }
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { (k, v) ->
                                Row {
                                    Text(k, color = Muted, modifier = Modifier.weight(1f))
                                    Text(v!!, color = Cream, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                if (i.ta.reasons.isNotEmpty()) {
                    item { SectionTitle("ليه الإشارة دي؟") }
                    items(i.ta.reasons) { Bullet(it, Gold) }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("مؤشرات فنية محسوبة تلقائياً وليست نصيحة استثمارية.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

/** -100 … +100 as a bar: bearish on the left (red), bullish on the right (green). */
@Composable
private fun ScoreBar(score: Int) {
    val frac = ((score + 100) / 200f).coerceIn(0f, 1f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val barH = 8.dp.toPx()
                val top = (size.height - barH) / 2
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Down, Gold, Up)),
                    topLeft = Offset(0f, top), size = Size(size.width, barH),
                    cornerRadius = CornerRadius(barH / 2, barH / 2),
                )
                drawCircle(Color.White, radius = size.height / 2, center = Offset(frac * size.width, size.height / 2))
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text("هبوط", color = Down, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("القوة: ${ltr(score.toString())}", color = Cream, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("صعود", color = Up, fontSize = 11.sp)
            }
        }
    }
}
