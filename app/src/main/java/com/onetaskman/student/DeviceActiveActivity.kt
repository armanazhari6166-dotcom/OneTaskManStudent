package com.onetaskman.student

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.onetaskman.student.ble.StudentBleManager
import com.onetaskman.student.utils.FirebaseHelper
import org.json.JSONObject

class DeviceActiveActivity : AppCompatActivity() {

    private lateinit var bleManager: StudentBleManager
    private val db = FirebaseHelper.db
    private lateinit var studentUid: String
    private lateinit var studentName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_active)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 99)
            }
        }

        studentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        studentName = prefs.getString("studentName", "") ?: ""

        initBleManager()
    }

    private fun initBleManager() {
        // Request BLUETOOTH_CONNECT on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
                return
            }
        }

        db.child("Users").child("Students").child(studentUid)
            .child("enrolledClasses").get()
            .addOnSuccessListener { snap ->
                Log.d("BLE", "enrolledClasses raw: ${snap.value}")

                // enrolledClasses is a JSON array → read values, not keys
                val classes = snap.children
                    .mapNotNull { it.getValue(String::class.java) }
                    .toList()

                Log.d("BLE", "Classes parsed: $classes")

                if (classes.isEmpty()) {
                    Log.e("BLE", "No enrolled classes — tasks won't load")
                    return@addOnSuccessListener
                }

                bleManager = StudentBleManager(
                    context = this,
                    db = Firebase.database.reference,
                    studentUid = studentUid,
                    studentName = studentName,
                    enrolledClasses = classes
                )

                val address = intent.getStringExtra("bleDeviceAddress")
                    ?: run { Log.e("BLE", "No BLE address in intent"); return@addOnSuccessListener }

                // Use BluetoothManager (non-deprecated) to get adapter
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device: BluetoothDevice? = bluetoothManager.adapter?.getRemoteDevice(address)

                if (device == null) {
                    Log.e("BLE", "Could not get BluetoothDevice for address $address")
                    return@addOnSuccessListener
                }

                bleManager.connectToDevice(device)
            }
            .addOnFailureListener { e ->
                Log.e("BLE", "Firebase fetch failed: ${e.message}")
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initBleManager()  // retry after permission granted
        } else {
            Log.e("BLE", "Bluetooth permission denied")
        }
    }
}