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

    private val CHANNEL_ID = "BtBatteryChannel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Tạo thông báo hiển thị lên thanh trạng thái (Notification bar)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pin Tai Nghe Bluetooth")
            .setContentText("L: 80% | Case: 91% | R: 100%")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true) // Giữ thông báo không bị vuốt xóa mất
            .build()

        // Đưa service chạy ở chế độ Foreground để ghim thông báo lên thanh trạng thái
        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Battery Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
