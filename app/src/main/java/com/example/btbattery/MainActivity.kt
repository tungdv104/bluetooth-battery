
package com.example.btbattery

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private lateinit var container: LinearLayout
    private lateinit var permissionStatus: TextView

    private val batteryLevels = mutableMapOf<String, Int>()
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()

    private var receiverRegistered = false

    private val batteryAction =
        "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    private val batteryExtra =
        "android.bluetooth.device.extra.BATTERY_LEVEL"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return

            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(
                    BluetoothDevice.EXTRA_DEVICE
                )
            } ?: return

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    connectedDevices[device.address] = device
                    sendDeviceNotification(
                        device,
                        "Đã kết nối",
                        batteryLevels[device.address] ?: -1
                    )
                    renderDevices()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    renderDevices()
                }

                batteryAction -> {
                    val level = intent.getIntExtra(batteryExtra, -1)
                    if (level !in 0..100) return

                    batteryLevels[device.address] = level

                    val status = if (
                        connectedDevices.containsKey(device.address)
                    ) "Đã kết nối" else "Bluetooth"

                    sendDeviceNotification(device, status, level)
                    renderDevices()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 10, 12)
        window.navigationBarColor = Color.rgb(8, 10, 12)

        buildInterface()
        checkBluetoothPermission()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(batteryAction)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }

            try {
                ContextCompat.registerReceiver(
                    this,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            } catch (_: Exception) {
            }
        }

        updatePermissionStatus()

        if (hasBluetoothPermission()) {
            refreshBatteryData()
            renderDevices()
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun sendDeviceNotification(
        device: BluetoothDevice,
        status: String,
        level: Int
    ) {
        val serviceIntent = Intent(
            this,
            PopupService::class.java
        ).apply {
            putExtra("device_name", deviceName(device))
            putExtra("battery_level", level)
            putExtra("connection_status", status)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Thiết bị Bluetooth"
        } catch (_: SecurityException) {
            "Thiết bị Bluetooth"
        }
    }

    private fun readSystemBatteryLevel(
        device: BluetoothDevice
    ): Int? {
        if (!hasBluetoothPermission()) return null

        return try {
            val method = BluetoothDevice::class.java.getMethod(
                "getBatteryLevel"
            )
            val value = method.invoke(device) as? Int
            value?.takeIf { it in 0..100 }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun refreshBatteryData() {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermission() || !adapter.isEnabled) return

        try {
            for (device in adapter.bondedDevices) {
                readSystemBatteryLevel(device)?.let {
                    batteryLevels[device.address] = it
                }
            }
        } catch (_: Exception) {
        }

        requestProfile(BluetoothProfile.A2DP)
        requestProfile(BluetoothProfile.HEADSET)
    }

    private fun requestProfile(profile: Int) {
        val adapter = bluetoothAdapter ?: return

        try {
            adapter.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profileType: Int,
                        proxy: BluetoothProfile
                    ) {
                        try {
                            for (device in proxy.connectedDevices) {
                                connectedDevices[device.address] = device

                                val level =
                                    batteryLevels[device.address]
                                        ?: readSystemBatteryLevel(device)

                                if (level != null) {
                                    batteryLevels[device.address] = level
                                }

                                sendDeviceNotification(
                                    device,
                                    "Đã kết nối",
                                    level ?: -1
                                )
                            }

                            runOnUiThread {
                                renderDevices()
                            }
                        } catch (_: SecurityException) {
                        } finally {
                            try {
                                adapter.closeProfileProxy(profileType, proxy)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onServiceDisconnected(profileType: Int) {
                    }
                },
                profile
            )
        } catch (_: Exception) {
        }
    }

    private fun buildInterface() {
        val green = Color.rgb(91, 230, 145)
        val secondary = Color.rgb(155, 165, 175)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 10, 12))
            isFillViewport = true
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(30))
        }

        scroll.addView(container)
        setContentView(scroll)

        container.addView(TextView(this).apply {
            text = "BT BATTERY"
            textSize = 12f
            letterSpacing = 0.2f
            setTextColor(green)
            setTypeface(null, Typeface.BOLD)
        })

        container.addView(TextView(this).apply {
            text = "Pin thiết bị"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })

        container.addView(TextView(this).apply {
            text = "Theo dõi pin Bluetooth của thiết bị đã kết nối"
            textSize = 14f
            setTextColor(secondary)
        })

        permissionStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(25, 29, 32), 14)
        }

        container.addView(
            permissionStatus,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(22)
                bottomMargin = dp(14)
            }
        )

        container.addView(makeButton("Cấp quyền thông báo", green) {
            requestNotificationPermission()
        })

        container.addView(
            makeButton("Làm mới thiết bị", Color.rgb(35, 43, 40)) {
                refreshBatteryData()
                renderDevices()
            }.apply {
                setTextColor(green)
            },
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(10)
            }
        )

        container.addView(TextView(this).apply {
            text = "THIẾT BỊ BLUETOOTH"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(secondary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(28), 0, dp(12))
        })

        renderDevices()
    }

    private fun renderDevices() {
        if (!::container.isInitialized) return

        while (container.childCount > 7) {
            container.removeViewAt(7)
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            addMessageCard("Điện thoại không hỗ trợ Bluetooth.")
            return
        }

        if (!hasBluetoothPermission()) {
            addMessageCard("Cần cấp quyền Bluetooth.")
            return
        }

        if (!adapter.isEnabled) {
            addMessageCard("Bluetooth đang tắt.")
            return
        }

        val devices = try {
            adapter.bondedDevices.toList()
        } catch (_: Exception) {
            emptyList()
        }

        if (devices.isEmpty()) {
            addMessageCard("Chưa có thiết bị ghép đôi.")
            return
        }

        devices.forEach { device ->
            val level = batteryLevels[device.address]
                ?: readSystemBatteryLevel(device)

            if (level != null) {
                batteryLevels[device.address] = level
            }

            val connected = connectedDevices.containsKey(device.address)

            addDeviceCard(
                deviceName(device),
                device.address,
                level,
                connected
            )
        }
    }

    private fun addDeviceCard(
        name: String,
        address: String,
        level: Int?,
        connected: Boolean
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(22, 26, 29), 18)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleColumn.addView(TextView(this).apply {
            text = name
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })

        titleColumn.addView(TextView(this).apply {
            text = if (connected) "Đã kết nối" else "Đã ghép đôi"
            textSize = 12f
            setTextColor(
                if (connected) Color.rgb(91, 230, 145)
                else Color.rgb(155, 165, 175)
            )
            setPadding(0, dp(4), 0, 0)
        })

        header.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(TextView(this).apply {
            text = level?.let { "$it%" } ?: "—"
            textSize = 24f
            setTextColor(Color.rgb(91, 230, 145))
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(header)

        val progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            this.progress = level ?: 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(91, 230, 145)
                )
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(52, 59, 62)
                )
        }

        card.addView(
            progress,
            LinearLayout.LayoutParams(-1, dp(6)).apply {
                topMargin = dp(16)
                bottomMargin = dp(12)
            }
        )

        card.addView(TextView(this).apply {
            text = if (level != null) {
                "Mức pin Bluetooth: $level%"
            } else {
                "Android chưa cung cấp mức pin cho thiết bị này."
            }
            textSize = 12f
            setTextColor(Color.rgb(155, 165, 175))
        })

        card.addView(TextView(this).apply {
            text = "MAC: $address"
            textSize = 11f
            setTextColor(Color.rgb(115, 125, 135))
            setPadding(0, dp(8), 0, 0)
        })

        container.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun addMessageCard(message: String) {
        container.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(210, 215, 220))
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = rounded(Color.rgb(22, 26, 29), 16)
        })
    }

    private fun makeButton(
        label: String,
        color: Int,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.rgb(8, 10, 12))
        background = rounded(color, 14)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50))
    }

    private fun checkBluetoothPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasBluetoothPermission()
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                101
            )
        } else {
            refreshBatteryData()
            renderDevices()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                102
            )
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun updatePermissionStatus() {
        if (!::permissionStatus.isInitialized) return

        val notificationsEnabled =
            if (Build.VERSION.SDK_INT >= 24) {
                getSystemService(NotificationManager::class.java)
                    .areNotificationsEnabled()
            } else true

        permissionStatus.text = when {
            !hasBluetoothPermission() ->
                "Bluetooth: cần cấp quyền truy cập thiết bị."
            !notificationsEnabled ->
                "Thông báo đang tắt. Hãy cấp quyền thông báo."
            else ->
                "Quyền Bluetooth và thông báo đã sẵn sàng."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        updatePermissionStatus()

        if (
            requestCode == 101 &&
            grantResults.firstOrNull() ==
            PackageManager.PERMISSION_GRANTED
        ) {
            refreshBatteryData()
            renderDevices()
        } else if (
            (requestCode == 101 || requestCode == 102) &&
            grantResults.firstOrNull() !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Chưa cấp quyền cần thiết.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
