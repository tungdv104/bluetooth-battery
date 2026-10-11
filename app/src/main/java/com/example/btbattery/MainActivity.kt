
package com.example.btbattery

import android.Manifest
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private lateinit var list: LinearLayout
    private lateinit var statusView: TextView

    private val connected = linkedMapOf<String, BluetoothDevice>()
    private val levels = mutableMapOf<String, Int>()
    private var receiverRegistered = false

    private val batteryAction =
        "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = getDevice(intent) ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    connected[device.address] = device
                    refreshUI()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connected.remove(device.address)
                    refreshUI()
                }

                batteryAction -> {
                    val level = intent.getIntExtra(
                        "android.bluetooth.device.extra.BATTERY_LEVEL", -1
                    )
                    if (level in 0..100) {
                        levels[device.address] = level
                        refreshUI()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 10, 12)
        window.navigationBarColor = Color.rgb(8, 10, 12)

        buildUI()
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerBluetoothReceiver()
        if (hasBluetoothPermission()) {
            startMonitorService()
            refreshConnectedDevices()
            refreshUI()
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(btReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            startMonitorService()
            refreshConnectedDevices()
            refreshUI()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(batteryAction)
        }

        try {
            ContextCompat.registerReceiver(
                this, btReceiver, filter, ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun startMonitorService() {
        if (!hasBluetoothPermission()) return
        try {
            val intent = Intent(this, PopupService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun refreshConnectedDevices() {
        val bt = adapter ?: return
        if (!hasBluetoothPermission() || !bt.isEnabled) return

        requestProfile(BluetoothProfile.A2DP)
        requestProfile(BluetoothProfile.HEADSET)

        try {
            for (device in bt.bondedDevices) {
                readBattery(device)?.let { levels[device.address] = it }
            }
        } catch (_: Exception) {
        }
    }

    private fun requestProfile(profile: Int) {
        val bt = adapter ?: return
        try {
            bt.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profileType: Int,
                        proxy: BluetoothProfile
                    ) {
                        try {
                            for (device in proxy.connectedDevices) {
                                connected[device.address] = device
                                readBattery(device)?.let {
                                    levels[device.address] = it
                                }
                            }
                            runOnUiThread { refreshUI() }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                bt.closeProfileProxy(profileType, proxy)
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

    private fun buildUI() {
        val green = Color.rgb(91, 230, 145)
        val secondary = Color.rgb(155, 165, 175)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 10, 12))
            isFillViewport = true
        }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(30))
        }
        scroll.addView(list)
        setContentView(scroll)

        list.addView(TextView(this).apply {
            text = "BT BATTERY"
            textSize = 12f
            letterSpacing = 0.2f
            setTextColor(green)
            setTypeface(null, Typeface.BOLD)
        })

        list.addView(TextView(this).apply {
            text = "Pin thiết bị"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })

        list.addView(TextView(this).apply {
            text = "Theo dõi kết nối và pin Bluetooth"
            textSize = 14f
            setTextColor(secondary)
        })

        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(25, 29, 32), 14)
        }
        list.addView(statusView, params(top = 22, bottom = 12))

        list.addView(makeButton("Cấp quyền thông báo") {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        })

        list.addView(makeButton("Làm mới thiết bị") {
            refreshConnectedDevices()
            refreshUI()
        }, params(top = 10, bottom = 20))

        refreshUI()
    }

    private fun refreshUI() {
        if (!::list.isInitialized) return

        runOnUiThread {
            while (list.childCount > 6) {
                list.removeViewAt(6)
            }

            statusView.text = when {
                adapter == null -> "Điện thoại không hỗ trợ Bluetooth."
                !hasBluetoothPermission() -> "Hãy cấp quyền Bluetooth."
                adapter?.isEnabled != true -> "Bluetooth đang tắt."
                connected.isNotEmpty() ->
                    "Đang kết nối ${connected.size} thiết bị."
                else -> "Chưa phát hiện tai nghe đang kết nối."
            }

            list.addView(TextView(this).apply {
                text = "THIẾT BỊ ĐÃ GHÉP ĐÔI"
                textSize = 12f
                letterSpacing = 0.08f
                setTextColor(Color.rgb(155, 165, 175))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(12))
            })

            if (adapter == null || !hasBluetoothPermission()) {
                addMessage("Không thể đọc danh sách thiết bị. Kiểm tra quyền Bluetooth.")
                return@runOnUiThread
            }

            val devices = try {
                (adapter!!.bondedDevices + connected.values)
                    .associateBy { it.address }
                    .values.toList()
            } catch (_: Exception) {
                connected.values.toList()
            }

            if (devices.isEmpty()) {
                addMessage("Chưa có thiết bị ghép đôi. Hãy kết nối tai nghe rồi nhấn Làm mới.")
                return@runOnUiThread
            }

            devices.forEach { device ->
                val level = levels[device.address] ?: readBattery(device)
                val isConnected = connected.containsKey(device.address)

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    background = rounded(Color.rgb(22, 26, 29), 18)
                }

                card.addView(TextView(this).apply {
                    text = safeName(device)
                    textSize = 17f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                })

                card.addView(TextView(this).apply {
                    text = if (isConnected) "● Đã kết nối" else "○ Đã ghép đôi"
                    textSize = 13f
                    setTextColor(
                        if (isConnected) Color.rgb(91, 230, 145)
                        else Color.rgb(155, 165, 175)
                    )
                    setPadding(0, dp(6), 0, dp(8))
                })

                card.addView(TextView(this).apply {
                    text = level?.let { "Pin Bluetooth: $it%" }
                        ?: "Android chưa cung cấp dữ liệu pin."
                    textSize = 14f
                    setTextColor(Color.rgb(210, 215, 220))
                })

                list.addView(card, params(bottom = 12))
            }
        }
    }

    private fun safeName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Thiết bị Bluetooth"
        } catch (_: Exception) {
            "Thiết bị Bluetooth"
        }
    }

    private fun addMessage(message: String) {
        list.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(210, 215, 220))
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = rounded(Color.rgb(22, 26, 29), 16)
        })
    }

    private fun makeButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.rgb(8, 10, 12))
            background = rounded(Color.rgb(91, 230, 145), 14)
            setOnClickListener { action() }
            layoutParams = params()
        }

    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (hasBluetoothPermission()) {
                startMonitorService()
                refreshConnectedDevices()
                refreshUI()
            } else {
                Toast.makeText(
                    this, "Cần cấp quyền Bluetooth để hoạt động.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (requestCode == 101 || requestCode == 100) {
            refreshUI()
        }
    }
}
