package com.onetaskman.student.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.onetaskman.student.DeviceActiveActivity
import com.onetaskman.student.R
import com.onetaskman.student.databinding.ActivityBleScanBinding
import com.onetaskman.student.databinding.ItemBleDeviceBinding

class BleScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBleScanBinding
    private lateinit var deviceAdapter: DeviceListAdapter
    private val REQUEST_BLE = 1001
    private var bleScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private val foundDevices = mutableListOf<BluetoothDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null && !foundDevices.any { it.address == device.address }) {
                foundDevices.add(device)
                runOnUiThread { deviceAdapter.addDevice(device) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceAdapter = DeviceListAdapter { device ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return@DeviceListAdapter
            }
            bleScanner?.stopScan(scanCallback)
            val intent = Intent(this, DeviceActiveActivity::class.java)
            intent.putExtra("bleDeviceAddress", device.address)
            startActivity(intent)
            finish()
        }
        binding.rvBleDevices.adapter = deviceAdapter
        binding.rvBleDevices.layoutManager = LinearLayoutManager(this)

        requestBlePermissions()
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLE
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_BLE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grants: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grants)
        if (requestCode == REQUEST_BLE && grants.all { it == PackageManager.PERMISSION_GRANTED }) {
            startBleScan()
        } else {
            showAlert("Bluetooth permissions are required to use the One Focus Device.")
        }
    }

    private fun startBleScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            showAlert("Please enable Bluetooth."); return
        }

        bleScanner = adapter.bluetoothLeScanner
        foundDevices.clear()
        deviceAdapter.clearDevices()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bleScanner?.startScan(scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return@postDelayed
            }
            bleScanner?.stopScan(scanCallback)
            if (foundDevices.isEmpty()) {
                showToast("No devices found. Try again.")
            }
        }, 10000)
    }

    private fun showAlert(msg: String) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

class DeviceListAdapter(private val onDeviceClick: (BluetoothDevice) -> Unit) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()

    fun addDevice(device: BluetoothDevice) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            notifyDataSetChanged()
        }
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBleDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(private val binding: ItemBleDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BluetoothDevice) {
            if (ActivityCompat.checkSelfPermission(binding.root.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            binding.tvDeviceName.text = device.name ?: "Unknown Device"
            binding.tvDeviceAddress.text = device.address
        }
    }
}