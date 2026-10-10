package com.example.btbattery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val deviceList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        listView = ListView(this)
        setContentView(listView)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = adapter

        checkPermissionsAndLoadDevices()
    }

    private fun checkPermissionsAndLoadDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        loadConnectedDevices()
    }

    private fun loadConnectedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Hãy bật Bluetooth trước khi dùng", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Lấy danh sách các thiết bị đã ghép nối/kết nối
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
            deviceList.clear()
            
            pairedDevices?.forEach { device ->
                val name = device.name ?: "Unknown"
                val address = device.address
                // Lưu ý: % pin chi tiết lấy trực tiếp qua phản hồi hệ thống audio profile (A2DP/HFP)
                deviceList.add("Tên: $name\nMac: $address")
            }
            
            if (deviceList.isEmpty()) {
                deviceList.add("Không tìm thấy thiết bị Bluetooth nào đã ghép nối.")
            }
            
            adapter.notifyDataSetChanged()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Thiếu quyền kết nối Bluetooth!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadConnectedDevices()
        } else {
            Toast.makeText(this, "Cần cấp quyền Bluetooth để quét thiết bị", Toast.LENGTH_SHORT).show()
        }
    }
}
