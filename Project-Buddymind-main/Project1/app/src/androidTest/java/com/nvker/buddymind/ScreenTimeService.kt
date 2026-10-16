package com.nvker.buddymind

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.*

class ScreenTimeService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Buddymind Monitoring")
            .setContentText("We are keeping an eye on your screen time.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)

        // Start a loop to check usage every minute
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkUsage()
            }
        }, 0, 60000) // 60000ms = 1 minute

        return START_STICKY
    }

    private fun checkUsage() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // Check last 24 hours

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        // Logic to calculate specific app time will go here
        println("Checking screen time stats...")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel("CHANNEL_ID", "Monitoring", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}