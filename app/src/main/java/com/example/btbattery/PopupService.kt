package com.example.btbattery

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class PopupService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var popupView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Inflate giao diện pop-up
        popupView = LayoutInflater.from(this).inflate(R.layout.popup_battery_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Hiển thị pop-up lên màn hình
        windowManager.addView(popupView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::popupView.isInitialized) {
            windowManager.removeView(popupView)
        }
    }
}
