package com.almasry.market

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("market", Context.MODE_PRIVATE)
    fun seenReport(c: Context): String = p(c).getString("seen_report", "") ?: ""
    fun setSeenReport(c: Context, v: String) = p(c).edit().putString("seen_report", v).apply()
}

/** Checks hourly for a new report and posts a notification with its headline. */
class ReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val market = runCatching { Repo(applicationContext).fetch() }.getOrElse { return Result.retry() }
        val report = market.report ?: return Result.success()
        if (report.created.isBlank() || report.created == Prefs.seenReport(applicationContext)) return Result.success()
        Prefs.setSeenReport(applicationContext, report.created)
        notify(report)
        return Result.success()
    }

    private fun notify(report: Report) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(report.headline.ifBlank { "تقرير السوق الجديد" })
            .setContentText(report.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(report.summary))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, n) }
    }

    companion object {
        const val CHANNEL = "reports"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "تقارير السوق", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("report-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
