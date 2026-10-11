
package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_battery_channel"
        private const val NOTIFICATION_ID = 2401

        private const val ACTION_BATTERY =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

        private const val EXTRA_BATTERY =
            "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    private var receiverRegistered = false
    private var currentDevice: BluetoothDevice? = null
    private var currentName = "Đang chờ tai nghe Bluetooth"
    private var currentBattery = -1
    private var connectionStatus = "Đang chờ kết nối"

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = getDevice(intent) ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    currentDevice = device
                    currentName = deviceName(device)
                    currentBattery = readBattery(device) ?: -1
                    connectionStatus = "Đã kết nối"
                    showNotification()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (currentDevice?.address == device.address) {
                        currentName = deviceName(device)
                        currentBattery = -1
                        connectionStatus = "Đã ngắt kết nối"
                        showNotification()
                        currentDevice = null
                    }
                }

                ACTION_BATTERY -> {
                    val level = intent.getIntExtra(EXTRA_BATTERY, -1)
                    if (level in 0..100) {
                        if (currentDevice?.address == device.address ||
                            currentDevice == null
                        ) {
                            currentDevice = device
                            currentName = deviceName(device)
                            currentBattery = level
                            connectionStatus = "Đã kết nối"
                            showNotification()
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getDevice(intent: Intent): BluetoothDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // Đưa service lên foreground ngay khi khởi động.
        try {
            startForegroundCompat(buildNotification())
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Khi app mở lại, tìm thiết bị đang kết nối sẵn.
        queryConnectedProfiles()
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY)
        }

        try {
            ContextCompat.registerReceiver(
                this,
                bluetoothReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun queryConnectedProfiles() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!hasBluetoothPermission()) return

        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profile ->
            try {
                adapter.getProfileProxy(
                    this,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(
                            profileType: Int,
                            proxy: BluetoothProfile
                        ) {
                            try {
                                val devices = proxy.connectedDevices
                                if (devices.isNotEmpty()) {
                                    val device = devices.first()
                                    currentDevice = device
                                    currentName = deviceName(device)
                                    currentBattery = readBattery(device) ?: -1
                                    connectionStatus = "Đã kết nối"
                                    showNotification()
                                }
                            } catch (_: Exception) {
                            } finally {
                                try {
                                    adapter.closeProfileProxy(profileType, proxy)
                                } catch (_: Exception) {
                                }
                            }
                        }

                        override fun onServiceDisconnected(profileType: Int) = Unit
                    },
                    profile
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun readBattery(device: BluetoothDevice): Int? {
        if (!hasBluetoothPermission()) return null
        return try {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Thiết bị Bluetooth"
        } catch (_: Exception) {
            "Thiết bị Bluetooth"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pin Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Trạng thái kết nối và pin tai nghe"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2402,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hasBattery = currentBattery in 0..100
        val title = if (hasBattery) {
            "$currentName · $currentBattery%"
        } else {
            currentName
        }

        val detail = if (connectionStatus == "Đã kết nối") {
            if (hasBattery) {
                "Đã kết nối · Pin Bluetooth $currentBattery%"
            } else {
                "Đã kết nối · Android chưa cung cấp dữ liệu pin"
            }
        } else {
            connectionStatus
        }

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(detail)
            )
            .setContentIntent(pendingIntent)
            .setColor(Color.rgb(91, 230, 145))
            .setPriority(
                androidx.core.app.NotificationCompat.PRIORITY_LOW
            )
            .setCategory(
                androidx.core.app.NotificationCompat.CATEGORY_STATUS
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
