package com.onetaskman.student.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onetaskman.student.R

object NotificationHelper {
    private const val CHANNEL_ID = "onetaskman_device"
    private const val CHANNEL_NAME = "OneTaskMan Device"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun showTaskComplete(ctx: Context, taskName: String, skipped: Boolean) {
        ensureChannel(ctx)

        val text = if (skipped) {
            "Completed (with skipped steps)"
        } else {
            "Completed successfully"
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // change if you have a better icon
            .setContentTitle("✅ Task completed")
            .setContentText("$taskName — $text")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted.")
            return
        }
        NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
}