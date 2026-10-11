
package com.example.btbattery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED) {
                return
            }

            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            val level = intent.getIntExtra(
                BluetoothDevice.EXTRA_BATTERY_LEVEL, -1
            )

            if (device != null && level in 0..100) {
                val name = try {
                    device.name ?: "Thiết bị Bluetooth"
                } catch (_: SecurityException) {
                    "Thiết bị Bluetooth"
                }

                batteryLevels[device.address] = level
                renderDevices()

                val serviceIntent = Intent(
                    this@MainActivity, PopupService::class.java
                ).apply {
                    putExtra("device_name", name)
                    putExtra("battery_level", level)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Không thể khởi chạy dịch vụ pin: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 12)
        window.navigationBarColor = Color.rgb(8, 10, 12)

        buildInterface()
        updatePermissionStatus()
        checkBluetoothPermission()
    }

    override fun onResume() {
        super.onResume()

        if (!receiverRegistered) {
            val filter = IntentFilter(
                BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED
            )
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        updatePermissionStatus()
        if (hasBluetoothPermission()) renderDevices()
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun buildInterface() {
        val background = Color.rgb(8, 10, 12)
        val green = Color.rgb(91, 230, 145)
        val secondary = Color.rgb(155, 165, 175)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(background)
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
            text = "Theo dõi pin Bluetooth của thiết bị đã ghép đôi"
            textSize = 14f
            setTextColor(secondary)
        })

        permissionStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(25, 29, 32), 14)
        }
        val statusParams = LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            topMargin = dp(22)
            bottomMargin = dp(14)
        }
        container.addView(permissionStatus, statusParams)

        container.addView(makeButton(
            "Cấp quyền thông báo", green
        ) {
            requestNotificationPermission()
        })

        container.addView(makeButton(
            "Làm mới thiết bị", Color.rgb(35, 43, 40)
        ) {
            checkBluetoothPermission()
        }.apply {
            setTextColor(green)
        }, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(10)
        })

        container.addView(TextView(this).apply {
            text = "THIẾT BỊ ĐÃ GHÉP ĐÔI"
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

        // Giữ lại phần đầu giao diện và xóa các thẻ thiết bị cũ.
        while (container.childCount > 5) {
            container.removeViewAt(5)
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            addMessageCard("Thiết bị này không hỗ trợ Bluetooth.")
            return
        }

        if (!hasBluetoothPermission()) {
            addMessageCard("Cần cấp quyền Bluetooth để xem thiết bị.")
            return
        }

        if (!adapter.isEnabled) {
            addMessageCard("Bluetooth đang tắt. Hãy bật Bluetooth rồi làm mới.")
            return
        }

        val devices = try {
            adapter.bondedDevices.toList()
        } catch (_: SecurityException) {
            addMessageCard("Ứng dụng chưa được cấp quyền Bluetooth.")
            return
        }

        if (devices.isEmpty()) {
            addMessageCard("Chưa có thiết bị ghép đôi với điện thoại.")
            return
        }

        devices.forEach { device ->
            val name = try {
                device.name ?: "Thiết bị Bluetooth"
            } catch (_: SecurityException) {
                "Thiết bị Bluetooth"
            }

            val level = batteryLevels[device.address]
            addDeviceCard(name, device.address, level)
        }
    }

    private fun addDeviceCard(
        name: String,
        address: String,
        level: Int?
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
            text = "Đã ghép đôi"
            textSize = 12f
            setTextColor(Color.rgb(155, 165, 175))
            setPadding(0, dp(4), 0, 0)
        })

        header.addView(titleColumn, LinearLayout.LayoutParams(
            0, -2, 1f
        ))

        header.addView(TextView(this).apply {
            text = level?.let { "$it%" } ?: "—"
            textSize = 24f
            setTextColor(Color.rgb(91, 230, 145))
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(header)

        val progress = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = level ?: 0
            progressTintList = android.content.res.ColorStateList.valueOf(
                Color.rgb(91, 230, 145)
            )
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(52, 59, 62)
                )
        }

        card.addView(progress, LinearLayout.LayoutParams(
            -1, dp(6)
        ).apply {
            topMargin = dp(16)
            bottomMargin = dp(12)
        })

        card.addView(TextView(this).apply {
            text = if (level != null) {
                "Mức pin nhận được từ Android"
            } else {
                "Chưa nhận được dữ liệu pin từ Android"
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

        container.addView(card, LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            bottomMargin = dp(12)
        })
    }

    private fun addMessageCard(message: String) {
        val view = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(210, 215, 220))
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = rounded(Color.rgb(22, 26, 29), 16)
        }

        container.addView(view, LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            bottomMargin = dp(12)
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
        layoutParams = LinearLayout.LayoutParams(
            -1, dp(50)
        )
    }

    private fun checkBluetoothPermission() {
        if (!hasBluetoothPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                101
            )
        } else {
            renderDevices()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                102
            )
        } else {
            val intent = Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
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
                "Thông báo đang tắt. Nhấn nút bên dưới để cấp quyền."
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
            requestCode, permissions, grantResults
        )

        if (requestCode == 101 || requestCode == 102) {
            updatePermissionStatus()

            if (requestCode == 101 &&
                grantResults.firstOrNull() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                renderDevices()
            }

            if (grantResults.firstOrNull() !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    if (requestCode == 101)
                        "Cần quyền Bluetooth để đọc danh sách thiết bị."
                    else
                        "Thông báo chưa được cấp quyền.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
