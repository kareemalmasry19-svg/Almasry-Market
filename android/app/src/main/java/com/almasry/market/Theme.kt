package com.almasry.market

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFF3D98B)
val GoldDeep = Color(0xFF9C7A1F)
val Ink = Color(0xFF0A0A0B)
val InkRaised = Color(0xFF151417)
val InkHigh = Color(0xFF1E1C21)
val Cream = Color(0xFFF5F1E6)
val Muted = Color(0xFFB9B2A0)
val Up = Color(0xFF3DDC84)
val Down = Color(0xFFFF5A5F)
val Flat = Color(0xFFB9B2A0)

private val Colors = darkColorScheme(
    primary = Gold, onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2F0B), onPrimaryContainer = GoldLight,
    secondary = GoldLight, onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2E2611), onSecondaryContainer = GoldLight,
    background = Ink, onBackground = Cream,
    surface = Ink, onSurface = Cream,
    surfaceVariant = InkHigh, onSurfaceVariant = Muted,
    surfaceContainer = InkRaised, surfaceContainerHigh = InkHigh,
    outline = GoldDeep,
)

@Composable
fun MarketTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)

val Cairo: ZoneId = ZoneId.of("Africa/Cairo")

/** Keeps numbers and signs left-to-right inside Arabic text. */
fun ltr(s: String) = "⁦$s⁩"

fun price(v: Double): String = ltr(
    when {
        abs(v) >= 1000 -> String.format(Locale.US, "%,.2f", v)
        abs(v) >= 10 -> String.format(Locale.US, "%.2f", v)
        else -> String.format(Locale.US, "%.4f", v)
    }
)

fun pct(v: Double): String = ltr(String.format(Locale.US, "%+.2f%%", v))

fun changeColor(v: Double): Color = when {
    v > 0.005 -> Up
    v < -0.005 -> Down
    else -> Flat
}

fun outlookColor(o: String): Color = when {
    o.contains("إيجاب") || o.contains("صعود") -> Up
    o.contains("سلب") || o.contains("هبوط") -> Down
    else -> Gold
}

/** "منذ 12 دقيقة" style age from an ISO timestamp. */
fun ago(iso: String): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
    val m = Duration.between(t, OffsetDateTime.now()).toMinutes()
    return when {
        m < 1 -> "الآن"
        m < 60 -> "منذ ${ltr(m.toString())} دقيقة"
        m < 60 * 24 -> "منذ ${ltr((m / 60).toString())} ساعة"
        else -> "منذ ${ltr((m / 1440).toString())} يوم"
    }
}

fun cairoTime(iso: String): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
    val z = t.atZoneSameInstant(Cairo)
    return ltr(String.format(Locale.US, "%02d:%02d", z.hour, z.minute))
}

/** EGX: Sunday–Thursday, 10:00–14:30 Cairo time (public holidays not included). */
fun egxOpen(now: ZonedDateTime = ZonedDateTime.now(Cairo)): Boolean {
    val day = now.dayOfWeek.value // Mon=1 … Sun=7
    if (day == 5 || day == 6) return false
    val mins = now.hour * 60 + now.minute
    return mins in 600 until 870
}

/** Forex trades from Sunday 22:00 to Friday 22:00 UTC. Returns the active sessions. */
fun forexSessions(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))): List<String> {
    val day = now.dayOfWeek.value
    val h = now.hour
    val closed = day == 6 || (day == 7 && h < 22) || (day == 5 && h >= 22)
    if (closed) return emptyList()
    val out = mutableListOf<String>()
    if (h >= 21 || h < 6) out += "سيدني"
    if (h in 0 until 9) out += "طوكيو"
    if (h in 7 until 16) out += "لندن"
    if (h in 12 until 21) out += "نيويورك"
    return out
}
