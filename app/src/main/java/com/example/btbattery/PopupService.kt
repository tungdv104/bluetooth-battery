package com.example.btbattery

import android.graphics.Color
import android.util.TypedValue
import android.content.res.Configuration
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class PopupService : Service() {
@@ -26,21 +26,7 @@ class PopupService : Service() {
const val EXTRA_RIGHT_BATTERY = "right_battery"
const val EXTRA_CASE_BATTERY = "case_battery"
}
    private fun resolveThemeColor(
    attr: Int,
    fallback: Int
    ): Int {
    val value = TypedValue()
    return if (
        theme.resolveAttribute(attr, value, true) &&
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..
            TypedValue.TYPE_LAST_COLOR_INT
    ) {
        value.data
    } else {
        fallback
    }
    }

private var deviceName = "SoundPEATS PearlClip Pro"
private var batteryLevel = -1
private var leftBattery = -1
@@ -58,41 +44,38 @@ class PopupService : Service() {
startId: Int
): Int {
if (intent != null) {
            deviceName = intent.getStringExtra(
                EXTRA_DEVICE_NAME
            ) ?: deviceName
            deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: deviceName

            batteryLevel = intent.getIntExtra(
                EXTRA_BATTERY_LEVEL, -1
            ).takeIf { it in 0..100 } ?: -1
            intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                .takeIf { it in 0..100 }
                ?.let { batteryLevel = it }

            leftBattery = intent.getIntExtra(
                EXTRA_LEFT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1
            intent.getIntExtra(EXTRA_LEFT_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let { leftBattery = it }

            rightBattery = intent.getIntExtra(
                EXTRA_RIGHT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1
            intent.getIntExtra(EXTRA_RIGHT_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let { rightBattery = it }

            caseBattery = intent.getIntExtra(
                EXTRA_CASE_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1
            intent.getIntExtra(EXTRA_CASE_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let { caseBattery = it }
}

val notification = buildNotification()

try {
            if (Build.VERSION.SDK_INT >= 29) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                startForeground(NOTIFICATION_ID, notification)
}
} catch (e: Exception) {
android.util.Log.e(
@@ -108,30 +91,39 @@ class PopupService : Service() {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pin tai nghe",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hiển thị pin tai trái, tai phải và hộp sạc"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
}
    }

    private fun batteryText(value: Int): String {
        return if (value in 0..100) "$value%" else "—"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
}

    private fun batteryText(value: Int): String =
        if (value in 0..100) "$value%" else "—"

private fun buildNotification(): Notification {
        val nightMode =
            (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val primaryTextColor =
            if (nightMode) Color.WHITE else Color.BLACK

        val secondaryTextColor =
            if (nightMode) Color.LTGRAY else Color.DKGRAY

val openIntent = Intent(
this,
MainActivity::class.java
@@ -148,96 +140,99 @@ class PopupService : Service() {
PendingIntent.FLAG_IMMUTABLE
)

       
val nightMode =
    (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

val primaryTextColor = if (nightMode) {
    Color.WHITE
} else {
    Color.BLACK
}
        // Popup thu gọn: hiển thị cả 3 mức pin trên cùng một hàng.
        val compactContent = RemoteViews(
            packageName,
            R.layout.notification_battery_compact
        ).apply {
            setTextViewText(
                R.id.notification_left,
                "L  ${batteryText(leftBattery)}"
            )
            setTextViewText(
                R.id.notification_case,
                "Hộp  ${batteryText(caseBattery)}"
            )
            setTextViewText(
                R.id.notification_right,
                "R  ${batteryText(rightBattery)}"
            )

val secondaryTextColor = if (nightMode) {
    Color.LTGRAY
} else {
    Color.DKGRAY
}
            setTextColor(R.id.notification_left, primaryTextColor)
            setTextColor(R.id.notification_case, primaryTextColor)
            setTextColor(R.id.notification_right, primaryTextColor)
        }

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

    // Chữ chính: trắng ở theme tối, đen ở theme sáng.
    setTextColor(
        R.id.notification_device_name,
        primaryTextColor
    )
    setTextColor(
        R.id.notification_left,
        primaryTextColor
    )
    setTextColor(
        R.id.notification_case,
        primaryTextColor
    )
    setTextColor(
        R.id.notification_right,
        primaryTextColor
    )
}
        // Popup mở rộng: giữ bố cục 3 ô, không cần nền riêng từng ô.
        val expandedContent = RemoteViews(
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

            setTextColor(
                R.id.notification_device_name,
                primaryTextColor
            )
            setTextColor(R.id.notification_left, primaryTextColor)
            setTextColor(R.id.notification_case, primaryTextColor)
            setTextColor(R.id.notification_right, primaryTextColor)
            setTextColor(R.id.notification_total, secondaryTextColor)

            val hasSeparateBattery =
                leftBattery in 0..100 ||
                rightBattery in 0..100 ||
                caseBattery in 0..100

            setTextViewText(
                R.id.notification_total,
                if (hasSeparateBattery) {
                    "Đã cập nhật pin tai nghe"
                } else if (batteryLevel in 0..100) {
                    "Pin Bluetooth: ${batteryText(batteryLevel)}"
                } else {
                    "Đang chờ dữ liệu pin"
                }
            )
        }

val summary = if (
leftBattery in 0..100 ||
rightBattery in 0..100 ||
caseBattery in 0..100
) {
            "L ${batteryText(leftBattery)}  ·  " +
                "Case ${batteryText(caseBattery)}  ·  " +
            "L ${batteryText(leftBattery)} · " +
                "Hộp ${batteryText(caseBattery)} · " +
"R ${batteryText(rightBattery)}"
} else if (batteryLevel in 0..100) {
            "Đang chờ pin riêng từng bên"
            "Pin Bluetooth: ${batteryText(batteryLevel)}"
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
.setContentTitle(deviceName)
.setContentText(summary)
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setStyle(
                NotificationCompat.DecoratedCustomViewStyle()
            )
            .setCustomContentView(compactContent)
            .setCustomBigContentView(expandedContent)
            .setCustomHeadsUpContentView(compactContent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
.setContentIntent(pendingIntent)
.setPriority(NotificationCompat.PRIORITY_LOW)
.setCategory(NotificationCompat.CATEGORY_STATUS)
