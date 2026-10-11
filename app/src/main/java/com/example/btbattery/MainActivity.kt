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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var deviceContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var totalText: TextView

    private val connectedDevices =
        linkedMapOf<String, BluetoothDevice>()

    private val batteryLevels = mutableMapOf<String, Int>()
    private val peatsBatteryStates =
        mutableMapOf<String, PeatsBatteryState>()
    private val peatsReadInProgress = mutableSetOf<String>()
    private val peatsLastReadAt = mutableMapOf<String, Long>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private var refreshing = false

    private val bg = Color.rgb(8, 12, 24)
    private val card = Color.rgb(28, 38, 58)
    private val tile = Color.rgb(37, 52, 75)
    private val accent = Color.rgb(113, 218, 255)
    private val green = Color.rgb(111, 240, 177)
    private val muted = Color.rgb(164, 180, 201)

    companion object {
        private const val REQUEST_BT = 501
        private const val REQUEST_NOTIFICATIONS = 502

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

                    if (device != null) {
                        connectedDevices[device.address] = device
                        readPeatsBatteryIfSupported(device)
                        sendNotification(
                            device,
                            getBatteryLevel(device)
                        )
                    }

                    refreshConnectedDevices()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getDeviceFromIntent(intent)

                    if (device != null) {
                        connectedDevices.remove(device.address)
                        batteryLevels.remove(device.address)
                        peatsBatteryStates.remove(device.address)
                        peatsLastReadAt.remove(device.address)
                    }

                    refreshUI()
                }

                ACTION_BATTERY_CHANGED -> {
                    val device = getDeviceFromIntent(intent)

                    if (device != null) {
                        connectedDevices[device.address] = device

                        val level = intent.getIntExtra(
                            EXTRA_BATTERY_LEVEL,
                            -1
                        )

                        if (level in 0..100) {
                            batteryLevels[device.address] = level
                        }

                        sendNotification(
                            device,
                            batteryLevels[device.address] ?: -1
                        )
                    }

                    refreshUI()
                }

                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
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

        window.statusBarColor = bg
        window.navigationBarColor = bg

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

    // --------------------------------------------------
    // GIAO DIỆN
    // --------------------------------------------------

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(8, 13, 29),
                    Color.rgb(15, 24, 43),
                    Color.rgb(20, 24, 43)
                )
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(20))
        }

        header.addView(
            text("BT BATTERY", 28f, Color.WHITE, true)
        )

        header.addView(
            text(
                "SMART BATTERY MONITOR",
                10f,
                accent,
                true
            ).apply {
                letterSpacing = 0.16f
                setPadding(0, dp(5), 0, 0)
            }
        )

        content.addView(header)

        // Trạng thái ứng dụng, không hiển thị PIN TỔNG.
        val statusCard = glassCard(22).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
        }

        statusText = text(
            "Đang kiểm tra Bluetooth...",
            15f,
            green,
            true
        )

        totalText = text(
            "Đang chờ dữ liệu pin",
            12f,
            muted
        ).apply {
            setPadding(0, dp(7), 0, 0)
        }

        statusCard.addView(statusText)
        statusCard.addView(totalText)

        content.addView(
            statusCard,
            matchWrap().apply {
                bottomMargin = dp(18)
            }
        )

        // Các nút thao tác nhanh.
        val actionCard = glassCard(22).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }

        actionCard.addView(
            text("THAO TÁC NHANH", 11f, muted, true).apply {
                letterSpacing = 0.1f
                setPadding(dp(3), dp(2), 0, dp(8))
            }
        )

        actionCard.addView(
            actionButton("Cấp quyền Bluetooth") {
                requestBluetoothPermissions()
            }
        )

        actionCard.addView(
            actionButton("Cấp quyền thông báo") {
                requestNotificationPermission()
            }
        )

        actionCard.addView(
            actionButton("Cài đặt thông báo và popup") {
                openNotificationSettings()
            }
        )

        actionCard.addView(
            actionButton("Làm mới thiết bị và pin") {
                refreshConnectedDevices(forceRead = true)
            }
        )

        content.addView(
            actionCard,
            matchWrap().apply {
                bottomMargin = dp(22)
            }
        )

        content.addView(
            text(
                "THIẾT BỊ CỦA BẠN",
                11f,
                muted,
                true
            ).apply {
                letterSpacing = 0.12f
                setPadding(dp(3), 0, 0, dp(12))
            }
        )

        deviceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(deviceContainer)

        content.addView(
            text(
                "Pin trái, phải và hộp sạc được đọc trực tiếp từ giao thức tai nghe nếu thiết bị hỗ trợ.",
                10f,
                muted
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(18), dp(8), dp(10))
            }
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
    }

    private fun actionButton(
        label: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setOnClickListener { onClick() }
        }
    }

    // --------------------------------------------------
    // QUYỀN ỨNG DỤNG
    // --------------------------------------------------

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    needed.toTypedArray(),
                    REQUEST_BT
                )
                return
            }
        }

        refreshConnectedDevices(forceRead = true)
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
                REQUEST_NOTIFICATIONS
            )
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        packageName
                    )
                }
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("BTBattery", "Không mở được cài đặt thông báo", e)
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

        when (requestCode) {
            REQUEST_BT -> {
                if (
                    grantResults.isNotEmpty() &&
                    grantResults.all {
                        it == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    statusText.text = "Đã cấp quyền Bluetooth"
                    refreshConnectedDevices(forceRead = true)
                } else {
                    statusText.text = "Cần cấp quyền Bluetooth"
                }
            }

            REQUEST_NOTIFICATIONS -> {
                val granted =
                    Build.VERSION.SDK_INT < 33 ||
                    (
                        grantResults.isNotEmpty() &&
                        grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
                    )

                statusText.text = if (granted) {
                    "Đã cấp quyền thông báo"
                } else {
                    "Chưa được cấp quyền thông báo"
                }

                if (granted) {
                    refreshConnectedDevices()
                } else {
                    openNotificationSettings()
                }
            }
        }
    }

    // --------------------------------------------------
    // BLUETOOTH RECEIVER
    // --------------------------------------------------

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
        } catch (e: Exception) {
            Log.e("BTBattery", "Không đăng ký được Bluetooth receiver", e)
        }
    }

    // --------------------------------------------------
    // ĐỌC PIN SOUNDPEATS
    // --------------------------------------------------

    @Suppress("MissingPermission")
    private fun readPeatsBatteryIfSupported(
        device: BluetoothDevice,
        force: Boolean = false
    ) {
        val name = getDeviceName(device).lowercase()

        val likelySoundPeats =
            name.contains("soundpeats") ||
            name.contains("pearlclip") ||
            name.contains("pearl clip") ||
            name.contains("pearclip")

        if (!likelySoundPeats) return

        val address = device.address
        val now = System.currentTimeMillis()

        if (address in peatsReadInProgress) return

        if (
            !force &&
            now - (peatsLastReadAt[address] ?: 0L) < 15_000L
        ) {
            return
        }

        peatsReadInProgress.add(address)
        peatsLastReadAt[address] = now

        PeatsBatteryReader(device).read { state, error ->
            runOnUiThread {
                peatsReadInProgress.remove(address)

                if (state != null && state.hasAnyValue) {
                    peatsBatteryStates[address] = state

                    Log.i(
                        "PeatsBatteryReader",
                        "Battery result for $name: " +
                            "L=${state.left}, " +
                            "R=${state.right}, " +
                            "case=${state.case}"
                    )

                    // Vẽ lại các ô pin và cập nhật popup bằng dữ liệu mới.
                    refreshUI()
                } else {
                    Log.w(
                        "PeatsBatteryReader",
                        "Could not read $name: ${error ?: "unknown error"}"
                    )

                    if (::totalText.isInitialized) {
                        totalText.text =
                            "Chưa đọc được pin riêng; thử làm mới lại"
                    }
                }
            }
        }
    }

    // --------------------------------------------------
    // TÌM THIẾT BỊ ĐANG KẾT NỐI
    // --------------------------------------------------

    @Suppress("MissingPermission")
    private fun refreshConnectedDevices(
        forceRead: Boolean = false
    ) {
        if (refreshing) return

        val adapter = bluetoothAdapter ?: run {
            if (::statusText.isInitialized) {
                statusText.text = "Không hỗ trợ Bluetooth"
            }
            return
        }

        try {
            if (!adapter.isEnabled) {
                connectedDevices.clear()
                refreshUI()
                return
            }

            refreshing = true

            adapter.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {

                    override fun onServiceConnected(
                        profile: Int,
                        proxy: BluetoothProfile
                    ) {
                        try {
                            for (device in proxy.connectedDevices) {
                                connectedDevices[device.address] = device

                                readPeatsBatteryIfSupported(
                                    device,
                                    forceRead
                                )

                                val level = getBatteryLevel(device)

                                if (level in 0..100) {
                                    batteryLevels[device.address] = level
                                }

                                sendNotification(device, level)
                            }

                            refreshUI()
                        } catch (e: Exception) {
                            Log.e(
                                "BTBattery",
                                "Lỗi khi làm mới thiết bị",
                                e
                            )
                        } finally {
                            refreshing = false

                            try {
                                adapter.closeProfileProxy(profile, proxy)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        refreshing = false
                    }
                },
                BluetoothProfile.A2DP
            )
        } catch (e: Exception) {
            refreshing = false

            if (::statusText.isInitialized) {
                statusText.text = "Không đọc được trạng thái Bluetooth"
            }

            Log.e("BTBattery", "Lỗi quét Bluetooth", e)
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun getBatteryLevel(device: BluetoothDevice): Int {
        try {
            val method =
                device.javaClass.getMethod("getBatteryLevel")

            val level = method.invoke(device) as? Int ?: -1

            if (level in 0..100) {
                batteryLevels[device.address] = level
                return level
            }
        } catch (_: Exception) {
            // Một số ROM không cung cấp API pin Bluetooth này.
        }

        return batteryLevels[device.address] ?: -1
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

    // --------------------------------------------------
    // CẬP NHẬT POPUP THÔNG BÁO
    // --------------------------------------------------

    private fun sendNotification(
        device: BluetoothDevice,
        level: Int
    ) {
        val peats = peatsBatteryStates[device.address]

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
                level.takeIf { it in 0..100 } ?: -1
            )

            putExtra(
                PopupService.EXTRA_LEFT_BATTERY,
                peats?.left?.takeIf { it in 0..100 } ?: -1
            )

            putExtra(
                PopupService.EXTRA_RIGHT_BATTERY,
                peats?.right?.takeIf { it in 0..100 } ?: -1
            )

            putExtra(
                PopupService.EXTRA_CASE_BATTERY,
                peats?.case?.takeIf { it in 0..100 } ?: -1
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(
                "BTBattery",
                "Không cập nhật được popup",
                e
            )
        }
    }

    // --------------------------------------------------
    // VẼ GIAO DIỆN PIN
    // --------------------------------------------------

    private fun refreshUI() {
        if (!::deviceContainer.isInitialized) return

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

            val empty = glassCard(22).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(32), dp(20), dp(32))
            }

            empty.addView(
                text("◉", 32f, accent, true).apply {
                    gravity = Gravity.CENTER
                }
            )

            empty.addView(
                text(
                    "Chưa có thiết bị kết nối",
                    15f,
                    Color.WHITE,
                    true
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(5))
                }
            )

            empty.addView(
                text(
                    "Bật Bluetooth và kết nối tai nghe.",
                    12f,
                    muted
                ).apply {
                    gravity = Gravity.CENTER
                }
            )

            deviceContainer.addView(empty)
            return
        }

        statusText.text = "${devices.size} thiết bị Bluetooth"

        for (device in devices) {
            val level = batteryLevels[device.address]
                ?: getBatteryLevel(device).takeIf { it in 0..100 }

            if (level != null && level in 0..100) {
                batteryLevels[device.address] = level
            }

            val item = glassCard(24).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(17), dp(15), dp(15))
            }

            item.addView(
                text(
                    getDeviceName(device),
                    18f,
                    Color.WHITE,
                    true
                )
            )

            item.addView(
                text(
                    "●  ĐÃ KẾT NỐI",
                    10f,
                    green,
                    true
                ).apply {
                    setPadding(0, dp(7), 0, dp(17))
                    letterSpacing = 0.07f
                }
            )

            val peats = peatsBatteryStates[device.address]

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            row.addView(
                batteryTile("TRÁI", "L", peats?.left),
                LinearLayout.LayoutParams(0, dp(115), 1f).apply {
                    marginEnd = dp(4)
                }
            )

            row.addView(
                batteryTile("HỘP SẠC", "C", peats?.case),
                LinearLayout.LayoutParams(0, dp(115), 1f).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                }
            )

            row.addView(
                batteryTile("PHẢI", "R", peats?.right),
                LinearLayout.LayoutParams(0, dp(115), 1f).apply {
                    marginStart = dp(4)
                }
            )

            item.addView(row)

            item.addView(
                text(
                    device.address,
                    10f,
                    muted
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, 0)
                }
            )

            deviceContainer.addView(
                item,
                matchWrap().apply {
                    bottomMargin = dp(14)
                }
            )

            // Luôn chuyển trạng thái pin riêng hiện có sang popup.
            sendNotification(device, level ?: -1)
        }

        totalText.text = if (peatsBatteryStates.isNotEmpty()) {
            "Đã nhận dữ liệu pin tai nghe"
        } else {
            "Đang chờ dữ liệu pin riêng từ tai nghe"
        }
    }

    private fun batteryTile(
        label: String,
        symbol: String,
        value: Int?
    ): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(8), dp(3), dp(8))
            background = rounded(tile, 17)
        }

        layout.addView(
            text(symbol, 23f, accent, true).apply {
                gravity = Gravity.CENTER
            }
        )

        layout.addView(
            text(label, 9f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(5))
            }
        )

        layout.addView(
            text(
                value?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—",
                17f,
                Color.WHITE,
                true
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        return layout
    }

    // --------------------------------------------------
    // TIỆN ÍCH GIAO DIỆN
    // --------------------------------------------------

    private fun glassCard(radius: Int): LinearLayout {
        return LinearLayout(this).apply {
            background = rounded(card, radius)
            elevation = dp(2).toFloat()
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(
                dp(1),
                Color.argb(45, 185, 220, 255)
            )
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun matchWrap() =
        LinearLayout.LayoutParams(-1, -2)

    @Suppress("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            device.name?.takeIf { it.isNotBlank() }
                ?: "Tai nghe Bluetooth"
        } catch (_: SecurityException) {
            "Tai nghe Bluetooth"
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
