package com.telecom.widget.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import com.telecom.widget.MainActivity
import com.telecom.widget.R
import com.telecom.widget.network.SavedAccount

object TelecomLiveNotificationHelper {

    const val CHANNEL_ID = "telecom_live_balance_channel"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Telecom Balance",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Real-time telecom balance chip and notification for status bar and lockscreen"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun updateNotification(context: Context, account: SavedAccount) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(context)

        val notifId = account.id.hashCode()

        if (!account.liveNotificationEnabled || account.cachedData == null) {
            manager.cancel(notifId)
            return
        }

        val data = account.cachedData
        val phone = formatNationalPhone(account.phone.ifEmpty { data.phoneNumber.ifEmpty { account.email } })
        val callsFormatted = formatCompactCalls(data.callsRemaining)
        val globe = "\uD83C\uDF10"
        val phoneIcon = "\uD83D\uDCDE"

        val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
        val callsDisplay = if (data.callsPercent != null) "$callsFormatted (${data.callsPercent.toInt()}%)" else callsFormatted

        // Put remaining data first so Samsung One UI status pill/chip displays GB immediately
        val title = "$internetDisplay  •  ${account.operator}"
        val bodyText = "$globe $internetDisplay   •   $phoneIcon $callsDisplay"
        val subTextInfo = phone

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshIntent = Intent(context, NotificationRefreshReceiver::class.java)
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progress = parseInternetProgress(data.internetRemaining)

        // =========================================================================
        // ANDROID 16 / ONE UI 8.5 NATIVE PROGRESS-CENTRIC LIVE UPDATE NOTIFICATION
        // =========================================================================
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val nativeBuilder = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_telecom)
                    .setContentTitle(title)
                    .setContentText(bodyText)
                    .setContentIntent(contentPendingIntent)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setSubText(subTextInfo)

                // Request Live Status Bar Promotion (One UI Now Bar / Dynamic Pill)
                nativeBuilder.extras.putBoolean("android.requestPromotedOngoing", true)
                try {
                    val method = nativeBuilder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    method.invoke(nativeBuilder, true)
                } catch (ignored: Exception) {}

                // Build Android 16 ProgressStyle showing remaining data
                val progressStyle = Notification.ProgressStyle()
                progressStyle.setStyledByProgress(true)
                progressStyle.setProgress(progress)
                progressStyle.setProgressSegments(listOf(
                    Notification.ProgressStyle.Segment(progress).setColor(Color.parseColor("#0381FE")),
                    Notification.ProgressStyle.Segment((100 - progress).coerceAtLeast(1)).setColor(Color.parseColor("#2E7D32"))
                ))

                nativeBuilder.setStyle(progressStyle)

                val refreshAction = Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_refresh),
                    "Refresh",
                    refreshPendingIntent
                ).build()
                nativeBuilder.addAction(refreshAction)

                manager.notify(notifId, nativeBuilder.build())
                return
            } catch (ignored: Throwable) {}
        }

        // =========================================================================
        // STANDARD NOTIFICATION FALLBACK
        // =========================================================================
        val bigText = buildString {
            appendLine("$globe Internet: ${data.internetRemaining}")
            append("$phoneIcon Calls: $callsFormatted")
            if (!data.structuredDetails.isNullOrEmpty()) {
                appendLine()
                data.structuredDetails.take(4).forEach {
                    appendLine("• ${it.label}: ${it.value}")
                }
            }
        }

        val compatBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_telecom)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(100, progress, false)
            .setSubText(subTextInfo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent)

        manager.notify(notifId, compatBuilder.build())
    }

    fun cancelNotification(context: Context, accountId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(accountId.hashCode())
    }

    fun updateAll(context: Context, accounts: List<SavedAccount>) {
        accounts.forEach { acc ->
            updateNotification(context, acc)
        }
    }

    private fun parseInternetProgress(internetRemaining: String): Int {
        val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(Go|Mo|MB|GB)", RegexOption.IGNORE_CASE).find(internetRemaining)
        return if (m != null) {
            val (numRaw, unit) = m.destructured
            val num = numRaw.replace(',', '.')
            val v = num.toFloatOrNull() ?: 15f
            if (unit.uppercase().startsWith("G")) ((v / 30f) * 100).toInt().coerceIn(5, 100)
            else ((v / 1024f) * 100).toInt().coerceIn(5, 100)
        } else 50
    }

    private fun formatCompactCalls(calls: String): String {
        val res = calls.replace(Regex("\\s*\\d+\\s*(?:s|sec|secondes?)\\b", RegexOption.IGNORE_CASE), "").trim()
        val normalized = if (res.count { it == ':' } == 2) res.substringBeforeLast(':') else res

        val m = Regex("0*(\\d+)\\s*[hH]\\s*0*(\\d+)\\s*(?:min|m)?", RegexOption.IGNORE_CASE).find(normalized)
        if (m != null) {
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val mins = m.groupValues[2].toIntOrNull() ?: 0
            return when {
                h == 0 && mins == 0 -> "0m"
                h == 0 -> "${mins}m"
                mins == 0 -> "${h}h"
                else -> "${h}h ${mins}m"
            }
        }
        return normalized.ifEmpty { calls }
    }

    private fun formatNationalPhone(phone: String): String {
        val clean = phone.filter { it.isDigit() }
        return if (clean.startsWith("212") && clean.length >= 12) {
            "0" + clean.substring(3)
        } else if (clean.startsWith("0")) {
            clean
        } else {
            phone
        }
    }
}
