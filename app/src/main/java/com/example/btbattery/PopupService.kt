
package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_battery_channel"
        private const val NOTIFICATION_ID = 2401

        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
    }

    private lateinit var notificationManager: NotificationManager

    private var deviceName = "Thiết bị Bluetooth"
    private var batteryLevel = -1

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // Nhận dữ liệu do MainActivity gửi sang.
        intent?.let {
            deviceName = it.getStringExtra(EXTRA_DEVICE_NAME)
                ?: deviceName

            val level = it.getIntExtra(
                EXTRA_BATTERY_LEVEL,
                -1
            )

            if (level in 0..100) {
                batteryLevel = level
            }
        }

        val notification = buildNotification()

        // Phải đưa service lên foreground ngay khi khởi chạy
        // bằng startForegroundService().
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
            // Tránh làm ứng dụng bị crash nếu hệ thống từ chối
            // khởi chạy foreground service.
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pin Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Theo dõi pin thiết bị Bluetooth"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2402,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val hasBattery = batteryLevel in 0..100

        val title = if (hasBattery) {
            "$deviceName · $batteryLevel%"
        } else {
            "$deviceName · Chưa có dữ liệu pin"
        }

        val detail = if (hasBattery) {
            "Mức pin Bluetooth hiện tại: $batteryLevel%"
        } else {
            "Đang chờ dữ liệu pin từ thiết bị Bluetooth."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detail)
            )
            .setContentIntent(pendingIntent)
            .setColor(Color.rgb(91, 230, 145))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(
                100,
                if (hasBattery) batteryLevel else 0,
                !hasBattery
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
