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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_battery_channel"
        private const val NOTIFICATION_ID = 2401

        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
    }

    private var deviceName = "Tai nghe Bluetooth"
    private var batteryLevel = -1

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
            deviceName = intent
                .getStringExtra(EXTRA_DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: deviceName

            val incomingLevel = intent.getIntExtra(
                EXTRA_BATTERY_LEVEL, -1
            )

            // Không ghi đè dữ liệu hợp lệ bằng -1.
            if (incomingLevel in 0..100) {
                batteryLevel = incomingLevel
            }
        }

        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(
            NotificationManager::class.java
        )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pin Bluetooth",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Theo dõi pin tai nghe Bluetooth"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
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

        val hasBattery = batteryLevel in 0..100
        val batteryText = if (hasBattery) {
            "$batteryLevel%"
        } else {
            "Chưa có dữ liệu"
        }

        val content = RemoteViews(
            packageName,
            R.layout.notification_battery
        ).apply {
            setTextViewText(
                R.id.notification_device_name,
                deviceName
            )

            // Android chỉ cung cấp một mức pin tổng ở đây.
            // Không giả lập pin riêng từng bên hoặc hộp sạc.
            setTextViewText(R.id.notification_left, "—")
            setTextViewText(R.id.notification_case, "—")
            setTextViewText(R.id.notification_right, "—")

            setTextViewText(
                R.id.notification_total,
                if (hasBattery) {
                    "PIN BLUETOOTH  ·  $batteryText"
                } else {
                    "Đang chờ dữ liệu pin từ tai nghe"
                }
            )
        }

        val summary = if (hasBattery) {
            "Pin Bluetooth: $batteryText"
        } else {
            "Đang theo dõi kết nối"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
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