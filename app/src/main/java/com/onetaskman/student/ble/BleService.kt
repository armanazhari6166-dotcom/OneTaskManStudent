package com.onetaskman.student.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onetaskman.student.utils.FirebaseHelper

class BleService : Service() {
    private lateinit var bleManager: StudentBleManager
    private val binder = LocalBinder()

    fun getBleManager(): StudentBleManager = bleManager

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        val studentUid = prefs.getString("studentUid", "") ?: ""
        val studentName = prefs.getString("studentName", "") ?: ""
        val enrolledClasses = prefs.getStringSet("enrolledClasses", emptySet())?.toList() ?: emptyList()

        bleManager = StudentBleManager(
            context = this,
            db = FirebaseHelper.db,
            studentUid = studentUid,
            studentName = studentName,
            enrolledClasses = enrolledClasses
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "ble_channel"
        val channel = NotificationChannel(channelId, "BLE Session", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("One Focus Device")
            .setContentText("Focus session active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }
}