
package com.example.btbattery

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var deviceContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var totalText: TextView

    private val batteryLevels = mutableMapOf<String, Int>()
    private val connectedDevices =
        linkedMapOf<String, BluetoothDevice>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private var permissionRequestCode = 501

    private val green = Color.rgb(91, 230, 145)
    private val bg = Color.rgb(10, 13, 15)
    private val cardColor = Color.rgb(27, 33, 36)
    private val secondary = Color.rgb(164, 176, 179)

    companion object {
        private const val ACTION_BATTERY_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

        private const val EXTRA_BATTERY_LEVEL =
            "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = getDeviceFromIntent(intent)
                        ?: return

                    connectedDevices[device.address] = device

                    val level = batteryLevels[device.address]
                        ?: getBatteryLevel(device)

                    if (level in 0..100) {
                        batteryLevels[device.address] = level
                    }

                    updateNotification(
                        device,
                        level.takeIf { it in 0..100 } ?: -1
                    )

                    refreshUI()
                    refreshConnectedDevices()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getDeviceFromIntent(intent)
                        ?: return

                    connectedDevices.remove(device.address)
                    refreshUI()
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshConnectedDevices()
                }

                ACTION_BATTERY_CHANGED -> {
                    val device = getDeviceFromIntent(intent)
                        ?: return

                    val level = intent.getIntExtra(
                        EXTRA_BATTERY_LEVEL,
                        -1
                    )

                    connectedDevices[device.address] = device

                    if (level in 0..100) {
                        batteryLevels[device.address] = level
                    }

                    updateNotification(
                        device,
                        level.takeIf { it in 0..100 }
                            ?: batteryLevels[device.address]
                            ?: -1
                    )

                    refreshUI()
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshConnectedDevices()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

        bluetoothAdapter = manager.adapter

        buildUI()
        requestBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerBluetoothReceiver()
        refreshConnectedDevices()
    }

    override fun onPause() {
        super.onPause()

        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
        }

        header.addView(TextView(this).apply {
            text = "BT BATTERY"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
        })

        header.addView(TextView(this).apply {
            text = "THE SMART BATTERY MONITOR"
            textSize = 10f
            setTextColor(green)
            letterSpacing = 0.12f
            setPadding(0, dp(5), 0, 0)
        })

        root.addView(header)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(cardColor, 18)
        }

        statusText = TextView(this).apply {
            text = "Đang kiểm tra Bluetooth..."
            textSize = 14f
            setTextColor(green)
            setTypeface(null, Typeface.BOLD)
        }

        totalText = TextView(this).apply {
            text = "Đang chờ dữ liệu pin"
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(6), 0, 0)
        }

        statusCard.addView(statusText)
        statusCard.addView(totalText)

        root.addView(
            statusCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(18)
            }
        )

        root.addView(TextView(this).apply {
            text = "THIẾT BỊ CỦA BẠN"
            textSize = 11f
            setTextColor(secondary)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dp(3), 0, 0, dp(12))
        })

        deviceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                deviceContainer,
                ViewGroup.LayoutParams(-1, -2)
            )
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        root.addView(TextView(this).apply {
            text = "Pin riêng L / Case / R phụ thuộc dữ liệu tai nghe."
            textSize = 10f
            setTextColor(Color.rgb(115, 126, 131))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })

        setContentView(root)
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
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
        } catch (_: Exception) {
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    permissionRequestCode
                )
                return
            }
        }

        refreshConnectedDevices()
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

        if (requestCode == permissionRequestCode) {
            if (
                grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {
                refreshConnectedDevices()
            } else {
                statusText.text =
                    "Cần quyền Bluetooth để tìm thiết bị"
            }
        }
    }

    private fun refreshConnectedDevices() {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            statusText.text =
                "Thiết bị không hỗ trợ Bluetooth"
            refreshUI()
            return
        }

        try {
            if (!adapter.isEnabled) {
                connectedDevices.clear()
                statusText.text = "Bluetooth đang tắt"
                refreshUI()
                return
            }

            for (device in adapter.bondedDevices ?: emptySet()) {
                val level = batteryLevels[device.address]
                    ?: getBatteryLevel(device)

                if (level in 0..100) {
                    batteryLevels[device.address] = level
                }
            }

            adapter.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profile: Int,
                        proxy: BluetoothProfile
                    ) {
                        try {
                            for (device in proxy.connectedDevices) {
                                connectedDevices[device.address] =
                                    device

                                val level =
                                    batteryLevels[device.address]
                                        ?: getBatteryLevel(device)

                                if (level in 0..100) {
                                    batteryLevels[device.address] =
                                        level
                                }

                                updateNotification(
                                    device,
                                    level.takeIf {
                                        it in 0..100
                                    } ?: -1
                                )
                            }

                            refreshUI()
                        } catch (_: SecurityException) {
                        } catch (_: Exception) {
                        } finally {
                            try {
                                adapter.closeProfileProxy(
                                    profile,
                                    proxy
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onServiceDisconnected(
                        profile: Int
                    ) {
                    }
                },
                BluetoothProfile.A2DP
            )

            statusText.text = "Đang theo dõi Bluetooth"
            refreshUI()
        } catch (_: SecurityException) {
            statusText.text =
                "Chưa được cấp quyền Bluetooth"
        } catch (_: Exception) {
            statusText.text =
                "Không đọc được trạng thái Bluetooth"
            refreshUI()
        }
    }

    private fun getBatteryLevel(
        device: BluetoothDevice
    ): Int {
        return try {
            val method = device.javaClass.getMethod(
                "getBatteryLevel"
            )
            val level = method.invoke(device) as? Int ?: -1
            level.takeIf { it in 0..100 } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun getDeviceFromIntent(
        intent: Intent
    ): BluetoothDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateNotification(
        device: BluetoothDevice,
        level: Int
    ) {
        try {
            val serviceIntent = Intent(
                this,
                PopupService::class.java
            ).apply {
                putExtra(
                    PopupService.EXTRA_DEVICE_NAME,
                    getDeviceName(device)
                )
                putExtra(
                    PopupService.EXTRA_BATTERY_LEVEL,
                    level
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun refreshUI() {
        deviceContainer.removeAllViews()

        val devices = connectedDevices.values.toList()

        if (devices.isEmpty()) {
            statusText.text =
                if (bluetoothAdapter?.isEnabled == true) {
                    "Chưa phát hiện tai nghe đang kết nối"
                } else {
                    "Bluetooth đang tắt"
                }

            totalText.text = "Kết nối tai nghe để xem pin"

            deviceContainer.addView(TextView(this).apply {
                text = "Chưa có thiết bị kết nối.\n\nBật Bluetooth và kết nối tai nghe."
                textSize = 14f
                setTextColor(secondary)
                gravity = Gravity.CENTER
                setPadding(
                    dp(20), dp(42), dp(20), dp(42)
                )
                background = rounded(cardColor, 18)
            })

            return
        }

        statusText.text = "${devices.size} thiết bị Bluetooth"

        var knownLevels = 0

        for (device in devices) {
            val level = batteryLevels[device.address]
                ?: getBatteryLevel(device).takeIf {
                    it in 0..100
                }

            if (level != null && level in 0..100) {
                batteryLevels[device.address] = level
                knownLevels++
            }

            addDeviceCard(
                name = getDeviceName(device),
                address = device.address,
                level = level,
                connected = true
            )

            // Luôn yêu cầu hiển thị thông báo cho thiết bị kết nối.
            // -1 nghĩa là chưa có mức pin hợp lệ.
            updateNotification(device, level ?: -1)
        }

        totalText.text = if (knownLevels > 0) {
            "Đã đọc được pin tổng của $knownLevels thiết bị"
        } else {
            "Đang chờ dữ liệu pin từ tai nghe"
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
            setPadding(dp(15), dp(17), dp(15), dp(15))
            background = rounded(cardColor, 22)
        }

        card.addView(TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
        })

        card.addView(TextView(this).apply {
            text = if (connected) {
                "● ĐÃ KẾT NỐI"
            } else {
                "○ ĐÃ NGẮT KẾT NỐI"
            }
            textSize = 10f
            setTextColor(if (connected) green else secondary)
            letterSpacing = 0.06f
            setPadding(0, dp(6), 0, dp(17))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        row.addView(
            makeBatteryTile("TRÁI", "L", null),
            LinearLayout.LayoutParams(0, dp(124), 1f).apply {
                marginEnd = dp(5)
            }
        )

        row.addView(
            makeBatteryTile("CASE", "▱", null),
            LinearLayout.LayoutParams(0, dp(124), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            }
        )

        row.addView(
            makeBatteryTile("PHẢI", "R", null),
            LinearLayout.LayoutParams(0, dp(124), 1f).apply {
                marginStart = dp(5)
            }
        )

        card.addView(row)

        card.addView(TextView(this).apply {
            text = if (level != null && level in 0..100) {
                "Pin Bluetooth tổng  ·  $level%"
            } else {
                "Pin Bluetooth tổng  ·  Chưa có dữ liệu"
            }
            textSize = 13f
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(0, dp(17), 0, dp(10))
        })

        card.addView(TextView(this).apply {
            text = address
            textSize = 10f
            setTextColor(Color.rgb(115, 126, 131))
            gravity = Gravity.CENTER
        })

        deviceContainer.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(13)
            }
        )
    }

    private fun makeBatteryTile(
        label: String,
        symbol: String,
        battery: Int?
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(9), dp(3), dp(9))
            background = rounded(Color.rgb(35, 43, 44), 16)

            addView(TextView(this@MainActivity).apply {
                text = symbol
                textSize = 23f
                setTextColor(green)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                setTextColor(secondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(5))
            })

            addView(TextView(this@MainActivity).apply {
                text = battery?.let { "$it%" } ?: "—"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
        }
    }

    private fun getDeviceName(
        device: BluetoothDevice
    ): String {
        return try {
            device.name?.takeIf { it.isNotBlank() }
                ?: "Tai nghe Bluetooth"
        } catch (_: SecurityException) {
            "Tai nghe Bluetooth"
        }
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(
                dp(1),
                Color.argb(45, 255, 255, 255)
            )
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
