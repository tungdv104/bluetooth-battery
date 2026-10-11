
package com.example.btbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class PopupService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_battery_channel"
        private const val NOTIFICATION_ID = 2401
        private const val POPUP_DURATION = 4500L

        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_BATTERY_LEVEL = "battery_level"
        const val EXTRA_LEFT_BATTERY = "left_battery"
        const val EXTRA_RIGHT_BATTERY = "right_battery"
        const val EXTRA_CASE_BATTERY = "case_battery"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var deviceName = "SoundPEATS PearlClip Pro"
    private var batteryLevel = -1
    private var leftBattery = -1
    private var rightBattery = -1
    private var caseBattery = -1

    private var receiverRegistered = false
    private var popupView: View? = null

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private val hidePopupRunnable = Runnable {
        removeAirPodsPopup()
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) {
                return
            }

            val device = getDeviceFromIntent(intent) ?: return
            val name = getDeviceName(device)

            if (!isSoundPeats(name)) return

            deviceName = name
            leftBattery = -1
            rightBattery = -1
            caseBattery = -1

            // Hiện popup ngay khi phát hiện kết nối.
            showAirPodsPopup()

            // Đọc pin thật bằng reader hiện có của dự án.
            try {
                PeatsBatteryReader(device).read { state, error ->
                    mainHandler.post {
                        if (state != null && state.hasAnyValue) {
                            leftBattery = state.left
                            rightBattery = state.right
                            caseBattery = state.case

                            updateNotification()
                            showAirPodsPopup()
                        } else {
                            android.util.Log.w(
                                "PopupService",
                                "Không đọc được pin sau khi kết nối: $error"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "PopupService",
                    "Lỗi đọc pin tai nghe",
                    e
                )
            }
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
        if (intent != null) {
            deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: deviceName

            batteryLevel = intent.getIntExtra(
                EXTRA_BATTERY_LEVEL, -1
            ).takeIf { it in 0..100 } ?: -1

            leftBattery = intent.getIntExtra(
                EXTRA_LEFT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1

            rightBattery = intent.getIntExtra(
                EXTRA_RIGHT_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1

            caseBattery = intent.getIntExtra(
                EXTRA_CASE_BATTERY, -1
            ).takeIf { it in 0..100 } ?: -1
        }

        try {
            val notification = buildNotification()

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
                "Không thể chạy dịch vụ nền",
                e
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(
            BluetoothDevice.ACTION_ACL_CONNECTED
        )

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
            android.util.Log.e(
                "PopupService",
                "Không đăng ký được sự kiện Bluetooth",
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

        val summary =
            "L ${batteryText(leftBattery)} · " +
            "Hộp ${batteryText(caseBattery)} · " +
            "R ${batteryText(rightBattery)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_sys_data_bluetooth
            )
            .setContentTitle(deviceName)
            .setContentText(summary)
            .setCustomContentView(content)
            .setCustomBigContentView(content)
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

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không cập nhật được thông báo pin",
                e
            )
        }
    }

    // --------------------------------------------------
    // POPUP KÍNH MỜ KIỂU AIRPODS
    // --------------------------------------------------

    private fun showAirPodsPopup() {
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.i(
                "PopupService",
                "Chưa được cấp quyền hiển thị popup nổi"
            )
            return
        }

        removeAirPodsPopup()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(14), dp(17), dp(15))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(25).toFloat()
                setColor(Color.argb(238, 23, 31, 48))
                setStroke(
                    dp(1),
                    Color.argb(110, 180, 218, 255)
                )
            }
            elevation = dp(12).toFloat()
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        nameColumn.addView(popupText(
            deviceName,
            14f,
            Color.WHITE,
            true
        ))

        nameColumn.addView(popupText(
            "Đã kết nối",
            11f,
            Color.rgb(111, 240, 177),
            false
        ).apply {
            setPadding(0, dp(3), 0, 0)
        })

        heading.addView(
            nameColumn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        heading.addView(popupText(
            "BT",
            12f,
            Color.rgb(113, 218, 255),
            true
        ))

        root.addView(heading)

        val divider = View(this).apply {
            setBackgroundColor(Color.argb(45, 220, 235, 255))
        }

        root.addView(
            divider,
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(12)
                bottomMargin = dp(10)
            }
        )

        val batteryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        batteryRow.addView(
            popupBatteryTile("TRÁI", leftBattery),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        batteryRow.addView(
            popupBatteryTile("HỘP SẠC", caseBattery),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        batteryRow.addView(
            popupBatteryTile("PHẢI", rightBattery),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(batteryRow)

        root.setOnClickListener {
            removeAirPodsPopup()
            val open = Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(open)
        }

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            minOf(dp(340), resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(64)
        }

        try {
            windowManager.addView(root, params)
            popupView = root

            mainHandler.removeCallbacks(hidePopupRunnable)
            mainHandler.postDelayed(
                hidePopupRunnable,
                POPUP_DURATION
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "PopupService",
                "Không thể hiển thị popup nổi",
                e
            )
        }
    }

    private fun popupBatteryTile(
        label: String,
        value: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(3))

            addView(popupText(
                label,
                9f,
                Color.rgb(164, 180, 201),
                true
            ))

            addView(popupText(
                batteryText(value),
                19f,
                Color.WHITE,
                true
            ).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun popupText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    private fun removeAirPodsPopup() {
        mainHandler.removeCallbacks(hidePopupRunnable)

        val current = popupView ?: return
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
        }
        popupView = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun isSoundPeats(name: String): Boolean {
        val value = name.lowercase()
        return value.contains("soundpeats") ||
            value.contains("pearlclip") ||
            value.contains("pearl clip") ||
            value.contains("pearclip")
    }

    @Suppress("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            device.name?.takeIf { it.isNotBlank() }
                ?: "Tai nghe Bluetooth"
        } catch (_: SecurityException) {
            "Tai nghe Bluetooth"
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

    override fun onDestroy() {
        mainHandler.removeCallbacks(hidePopupRunnable)
        removeAirPodsPopup()

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
