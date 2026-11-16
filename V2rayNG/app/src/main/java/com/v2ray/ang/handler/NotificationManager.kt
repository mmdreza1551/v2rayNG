package com.v2ray.ang.handler

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.home.toPersianDigits
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val QUERY_INTERVAL_MS = 1000L

    private var lastQueryTime = 0L
    private var serviceStartTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: android.app.NotificationManager? = null

    fun startSpeedNotification() {
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                updateSpeedNotificationOnce()
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    fun showConnectingNotification(serverName: String?) {
        val service = getService() ?: return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel() else ""
        val title = serverName?.takeIf { it.isNotBlank() } ?: service.getString(R.string.app_name)

        val publicNotif = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_shield_plain)
            .setColor(0xFFD8B45A.toInt())
            .setContentTitle(title)
            .setContentText("در حال اتصال…")
            .build()

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_shield_plain)
            .setColor(0xFFD8B45A.toInt())
            .setContentTitle(title)
            .setContentText("در حال اتصال…")
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setPublicVersion(publicNotif)
            .addAction(
                R.drawable.ic_notif_close,
                "لغو",
                stopV2RayPendingIntent
            )

        val largeIconBitmap = BitmapFactory.decodeResource(service.resources, R.mipmap.ic_launcher)
        if (largeIconBitmap != null) {
            mBuilder?.setLargeIcon(largeIconBitmap)
        }

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        serviceStartTime = System.currentTimeMillis()
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_RESTART)
        }
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel() else ""
        val title = currentConfig?.remarks?.takeIf { it.isNotBlank() } ?: service.getString(R.string.app_name)

        val publicNotif = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_shield_check)
            .setColor(0xFFD8B45A.toInt())
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText("متصل است")
            .build()

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_shield_check)
            .setColor(0xFFD8B45A.toInt())
            .setContentTitle(title)
            .setContentText("۰۰:۰۰:۰۰ • ↓ ۰ KB/s ↑ ۰ KB/s")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setPublicVersion(publicNotif)
            .addAction(
                R.drawable.ic_notif_power,
                "قطع اتصال",
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_notif_refresh,
                "اتصال مجدد",
                restartV2RayPendingIntent
            )

        val largeIconBitmap = BitmapFactory.decodeResource(service.resources, R.mipmap.ic_launcher)
        if (largeIconBitmap != null) {
            mBuilder?.setLargeIcon(largeIconBitmap)
        }

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    fun stopSpeedNotification() {
        speedNotificationJob?.cancel()
        speedNotificationJob = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        val notificationManager =
            service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val localizedName = "وضعیت اتصال"
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel == null) {
            val channel = android.app.NotificationChannel(
                channelId,
                localizedName,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت اتصال فعال MR VPN"
                lightColor = Color.DKGRAY
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)
        } else if (existingChannel.name.toString() != localizedName) {
            existingChannel.name = localizedName
            existingChannel.description = "نمایش وضعیت اتصال فعال MR VPN"
            notificationManager.createNotificationChannel(existingChannel)
        }
        return channelId
    }

    private fun updateSpeedNotificationOnce() {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime).coerceAtLeast(1L)
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            if (stat.tag != AppConfig.TAG_BLOCKED && stat.tag != AppConfig.TAG_DIRECT) {
                when (stat.direction) {
                    AppConfig.UPLINK -> proxyUplink += stat.value
                    AppConfig.DOWNLINK -> proxyDownlink += stat.value
                }
            }
        }

        val elapsedSec = if (serviceStartTime > 0L) {
            ((queryTime - serviceStartTime) / 1000L).coerceAtLeast(0L)
        } else 0L

        val h = elapsedSec / 3600L
        val m = (elapsedSec % 3600L) / 60L
        val s = elapsedSec % 60L
        val timeStr = toPersianDigits(String.format(Locale.US, "%02d:%02d:%02d", h, m, s))

        val upRate = proxyUplink / sinceLastQueryInSeconds
        val downRate = proxyDownlink / sinceLastQueryInSeconds

        fun formatRate(bytesPerSec: Double): String {
            return if (bytesPerSec >= 1_000_000.0) {
                val mb = bytesPerSec / 1_000_000.0
                "${toPersianDigits(String.format(Locale.US, "%.1f", mb))} MB/s"
            } else {
                val kb = (bytesPerSec / 1_000.0).roundToLong().coerceAtLeast(0L)
                "${toPersianDigits(kb.toString())} KB/s"
            }
        }

        val contentText = "$timeStr • ↓ ${formatRate(downRate)} ↑ ${formatRate(upRate)}"

        if (mBuilder != null) {
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
        lastQueryTime = queryTime
    }

    private fun getNotificationManager(): android.app.NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        }
        return mNotificationManager
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
