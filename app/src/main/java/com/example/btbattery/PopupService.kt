
package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
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
        const val EXTRA_LEFT_BATTERY = "left_battery"
        const val EXTRA_RIGHT_BATTERY = "right_battery"
        const val EXTRA_CASE_BATTERY = "case_battery"
    }

    private var deviceName = "SoundPEATS PearlClip Pro"
    private var batteryLevel = -1
    private var leftBattery = -1
    private var rightBattery = -1
    private var caseBattery = -1

    private var isDeviceConnected = false
    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val action = intent.action ?: return

            if (
                action != BluetoothDevice.ACTION_ACL_CONNECTED &&
                action != BluetoothDevice.ACTION_ACL_DISCONNECTED
            ) {
                return
            }

            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

            val remoteName = try {
                device?.name.orEmpty()
            } catch (e: SecurityException) {
                ""
            }

            // Chỉ xử lý tai nghe mục tiêu, tránh nhầm với thiết bị Bluetooth khác.
            val targetName = deviceName.lowercase()
            val name = remoteName.lowercase()

            val isTargetDevice =
                name.contains("soundpeats") ||
                name.contains("pearlclip") ||
                (
                    name.isNotBlank() &&
                    targetName.contains(name)
                ) ||
                (
                    name.isNotBlank() &&
                    name.contains(targetName)
                )

            if (!isTargetDevice) return

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    isDeviceConnected = true
                    refreshNotification()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    isDeviceConnected = false
                    refreshNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    bluetoothReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(bluetoothReceiver, filter)
            }

            receiverRegistered = true
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không đăng ký được Bluetooth receiver",
                e
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent != null) {
            intent.getStringExtra(EXTRA_DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.let { deviceName = it }

            var receivedBattery = false

            intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                .takeIf { it in 0..100 }
                ?.let {
                    batteryLevel = it
                    receivedBattery = true
                }

            intent.getIntExtra(EXTRA_LEFT_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let {
                    leftBattery = it
                    receivedBattery = true
                }

            intent.getIntExtra(EXTRA_RIGHT_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let {
                    rightBattery = it
                    receivedBattery = true
                }

            intent.getIntExtra(EXTRA_CASE_BATTERY, -1)
                .takeIf { it in 0..100 }
                ?.let {
                    caseBattery = it
                    receivedBattery = true
                }

            if (receivedBattery) {
                isDeviceConnected = true
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
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không thể hiển thị thông báo",
                e
            )
            stopSelf()
            return START_NOT_STICKY
        }

        updateWidget()

        return START_STICKY
    }

    private fun refreshNotification() {
        try {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            manager.notify(
                NOTIFICATION_ID,
                buildNotification()
            )

            updateWidget()
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không cập nhật được trạng thái kết nối",
                e
            )
        }
    }

    private fun updateWidget() {
        try {
            BatteryWidgetProvider.persistAndUpdate(
                context = this,
                deviceName = deviceName,
                left = leftBattery,
                right = rightBattery,
                caseBattery = caseBattery,
                total = batteryLevel,
                connected = isDeviceConnected
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không cập nhật được widget",
                e
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

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

        val compactContent = RemoteViews(
            packageName,
            R.layout.notification_battery_compact
        ).apply {
            if (isDeviceConnected) {
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
            } else {
                setTextViewText(
                    R.id.notification_left,
                    "Đã ngắt kết nối"
                )
                setTextViewText(
                    R.id.notification_case,
                    "L ${batteryText(leftBattery)}"
                )
                setTextViewText(
                    R.id.notification_right,
                    "R ${batteryText(rightBattery)}"
                )
            }

            setTextColor(R.id.notification_left, primaryTextColor)
            setTextColor(R.id.notification_case, primaryTextColor)
            setTextColor(R.id.notification_right, primaryTextColor)
        }

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
            setTextColor(
                R.id.notification_total,
                secondaryTextColor
            )

            val hasSeparateBattery =
                leftBattery in 0..100 ||
                rightBattery in 0..100 ||
                caseBattery in 0..100

            val status = when {
                !isDeviceConnected -> "Đã ngắt kết nối"
                hasSeparateBattery -> "Đã cập nhật pin tai nghe"
                batteryLevel in 0..100 ->
                    "Pin Bluetooth: ${batteryText(batteryLevel)}"
                else -> "Đang chờ dữ liệu pin"
            }

            setTextViewText(
                R.id.notification_total,
                status
            )
        }

        val summary = when {
            !isDeviceConnected -> "Đã ngắt kết nối Bluetooth"
            leftBattery in 0..100 ||
                rightBattery in 0..100 ||
                caseBattery in 0..100 -> {
                "L ${batteryText(leftBattery)} · " +
                    "Hộp ${batteryText(caseBattery)} · " +
                    "R ${batteryText(rightBattery)}"
            }
            batteryLevel in 0..100 ->
                "Pin Bluetooth: ${batteryText(batteryLevel)}"
            else -> "Đang chờ dữ liệu pin"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_sys_data_bluetooth
            )
            .setContentTitle(
                if (isDeviceConnected) {
                    deviceName
                } else {
                    "$deviceName · Đã ngắt kết nối"
                }
            )
            .setContentText(summary)
            .setCustomContentView(compactContent)
            .setCustomBigContentView(expandedContent)
            .setCustomHeadsUpContentView(compactContent)
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

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                android.util.Log.w(
                    "PopupService",
                    "Bluetooth receiver đã được gỡ",
                    e
                )
            }
            receiverRegistered = false
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
