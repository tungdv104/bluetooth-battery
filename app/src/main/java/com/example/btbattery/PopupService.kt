
package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "BtBatteryChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Phải gọi startForeground sớm khi service được khởi chạy.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                "BT Battery",
                "Đang chờ dữ liệu pin Bluetooth"
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val deviceName = intent?.getStringExtra("device_name")
        val batteryLevel = intent?.getIntExtra("battery_level", -1) ?: -1

        if (!deviceName.isNullOrBlank() && batteryLevel in 0..100) {
            val notification = buildNotification(
                deviceName,
                "Mức pin Bluetooth: $batteryLevel%"
            )

            val manager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            manager.notify(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String,
        message: String
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Battery",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông tin pin Bluetooth"
            }

            val manager =
                getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
