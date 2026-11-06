package com.almasry.market

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads market.json from the data branch and keeps the last good copy for offline use. */
class Repo(context: Context) {
    private val cache = File(context.filesDir, "market.json")

    suspend fun cached(): Market? = withContext(Dispatchers.IO) {
        runCatching { if (cache.exists()) parseMarket(cache.readText()) else null }.getOrNull()
    }

    suspend fun fetch(): Market = withContext(Dispatchers.IO) {
        val url = URL(BuildConfig.DATA_URL + "?t=" + System.currentTimeMillis() / 60_000)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code == 404) error("لسه مفيش بيانات — شغّل Market agents على GitHub")
            if (code !in 200..299) error("خطأ من السيرفر ($code)")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val market = parseMarket(text)
            cache.writeText(text)
            market
        } finally {
            conn.disconnect()
        }
    }
}
