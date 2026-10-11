package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_battery_channel"
        private const val NOTIFICATION_ID = 2401

        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
        const val EXTRA_LEFT_BATTERY = "left_battery"
        const val EXTRA_RIGHT_BATTERY = "right_battery"
        const val EXTRA_CASE_BATTERY = "case_battery"
    }

    private var deviceName = "SoundPEATS PearlClip Pro"
    private var batteryLevel = -1
    private var leftBattery = -1
    private var rightBattery = -1
    private var caseBattery = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent != null) {
            deviceName = intent.getStringExtra(
                EXTRA_DEVICE_NAME
            ) ?: deviceName

            batteryLevel = intent.getIntExtra(
                EXTRA_BATTERY_LEVEL, -1
            ).takeIf { it in 0..100 } ?: -1

            leftBattery = intent.getIntExtra(
                EXTRA_LEFT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1

            rightBattery = intent.getIntExtra(
                EXTRA_RIGHT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1

            caseBattery = intent.getIntExtra(
                EXTRA_CASE_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1
        }

        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không thể hiển thị thông báo",
                e
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pin tai nghe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị pin trái, phải và hộp sạc"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun batteryText(value: Int): String {
        return if (value in 0..100) "$value%" else "—"
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2402,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val content = android.widget.RemoteViews(
            packageName,
            R.layout.notification_battery
        ).apply {
            setTextViewText(
                R.id.notification_device_name,
                deviceName
            )

            setTextViewText(
                R.id.notification_left,
                batteryText(leftBattery)
            )

            setTextViewText(
                R.id.notification_case,
                batteryText(caseBattery)
            )

            setTextViewText(
                R.id.notification_right,
                batteryText(rightBattery)
            )
        }

        val summary = if (
            leftBattery in 0..100 ||
            rightBattery in 0..100 ||
            caseBattery in 0..100
        ) {
            "L ${batteryText(leftBattery)}  ·  " +
                "Case ${batteryText(caseBattery)}  ·  " +
                "R ${batteryText(rightBattery)}"
        } else if (batteryLevel in 0..100) {
            "Đang chờ pin riêng từng bên"
        } else {
            "Đang theo dõi kết nối Bluetooth"
        }

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.stat_sys_data_bluetooth
            )
            .setContentTitle(deviceName)
            .setContentText(summary)
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setStyle(
                NotificationCompat.DecoratedCustomViewStyle()
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
