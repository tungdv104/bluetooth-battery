package com.example.btbattery

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView

class PopupService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var popupView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Inflate giao diện pop-up từ file XML
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

        // --- CẬP NHẬT DỮ LIỆU PIN (Ví dụ mẫu: L = 80%, Case = 91%, R = 100%) ---
        updateBatteryUI(leftBattery = 80, caseBattery = 91, rightBattery = 100)

        // Hiển thị pop-up lên màn hình
        windowManager.addView(popupView, params)
    }

    private fun updateBatteryUI(leftBattery: Int, caseBattery: Int, rightBattery: Int) {
        // Ánh xạ View cho bên trái (L)
        val progressLeft = popupView.findViewById<ProgressBar>(R.id.progressLeft)
        val txtLeft = popupView.findViewById<TextView>(R.id.txtLeftBattery)
        progressLeft?.progress = leftBattery
        txtLeft?.text = "L: $leftBattery%"

        // Ánh xạ View cho hộp sạc (Case)
        val progressCase = popupView.findViewById<ProgressBar>(R.id.progressCase)
        val txtCase = popupView.findViewById<TextView>(R.id.txtCaseBattery)
        progressCase?.progress = caseBattery
        txtCase?.text = "Case: $caseBattery%"

        // Ánh xạ View cho bên phải (R)
        val progressRight = popupView.findViewById<ProgressBar>(R.id.progressRight)
        val txtRight = popupView.findViewById<TextView>(R.id.txtRightBattery)
        progressRight?.progress = rightBattery
        txtRight?.text = "R: $rightBattery%"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::popupView.isInitialized) {
            windowManager.removeView(popupView)
        }
    }
}
