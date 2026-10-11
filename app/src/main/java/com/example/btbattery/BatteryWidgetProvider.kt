package com.example.btbattery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS = "battery_widget_prefs"

        private const val KEY_NAME = "device_name"
        private const val KEY_LEFT = "left_battery"
        private const val KEY_RIGHT = "right_battery"
        private const val KEY_CASE = "case_battery"
        private const val KEY_TOTAL = "battery_level"
        private const val KEY_CONNECTED = "device_connected"

        /**
         * Lưu trạng thái pin và kết nối, sau đó cập nhật widget.
         * Được gọi từ PopupService.
         */
        fun persistAndUpdate(
            context: Context,
            deviceName: String,
            left: Int,
            right: Int,
            caseBattery: Int,
            total: Int,
            connected: Boolean = true
        ) {
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).edit()
                .putString(KEY_NAME, deviceName)
                .putInt(KEY_LEFT, left)
                .putInt(KEY_RIGHT, right)
                .putInt(KEY_CASE, caseBattery)
                .putInt(KEY_TOTAL, total)
                .putBoolean(KEY_CONNECTED, connected)
                .apply()

            updateWidgets(context)
        }

        /**
         * Cập nhật tất cả widget đang được đặt trên màn hình.
         */
        private fun updateWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(
                context,
                BatteryWidgetProvider::class.java
            )
            val ids = manager.getAppWidgetIds(component)

            if (ids.isNotEmpty()) {
                BatteryWidgetProvider().onUpdate(
                    context,
                    manager,
                    ids
                )
            }
        }

        private fun batteryText(value: Int): String {
            return if (value in 0..100) "$value%" else "—"
        }

        private fun safeProgress(value: Int): Int {
            return if (value in 0..100) value else 0
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val name = prefs.getString(
            KEY_NAME,
            "SoundPEATS PearlClip Pro"
        ) ?: "SoundPEATS PearlClip Pro"

        val left = prefs.getInt(KEY_LEFT, -1)
        val right = prefs.getInt(KEY_RIGHT, -1)
        val caseBattery = prefs.getInt(KEY_CASE, -1)
        val total = prefs.getInt(KEY_TOTAL, -1)
        val connected = prefs.getBoolean(KEY_CONNECTED, false)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_battery
            )

            views.setTextViewText(
                R.id.widget_device_name,
                name
            )

            views.setTextViewText(
                R.id.widget_left_value,
                batteryText(left)
            )

            views.setTextViewText(
                R.id.widget_right_value,
                batteryText(right)
            )

            views.setTextViewText(
                R.id.widget_case_value,
                batteryText(caseBattery)
            )

            views.setProgressBar(
                R.id.widget_left_progress,
                100,
                safeProgress(left),
                false
            )

            views.setProgressBar(
                R.id.widget_right_progress,
                100,
                safeProgress(right),
                false
            )

            views.setProgressBar(
                R.id.widget_case_progress,
                100,
                safeProgress(caseBattery),
                false
            )

            val hasSeparateBattery =
                left in 0..100 ||
                right in 0..100 ||
                caseBattery in 0..100

            val status = when {
                !connected -> "Đã ngắt kết nối"
                hasSeparateBattery -> "Đang kết nối · Pin tai nghe"
                total in 0..100 -> "Đang kết nối · Pin Bluetooth: $total%"
                else -> "Đang kết nối · Đang chờ dữ liệu pin"
            }

            views.setTextViewText(
                R.id.widget_status,
                status
            )

            val openIntent = Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(
                R.id.widget_root,
                pendingIntent
            )

            appWidgetManager.updateAppWidget(
                appWidgetId,
                views
            )
        }
    }
}