package com.nvker.buddymind

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ScreenTimeService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // 1. Persistent notification required for Foreground Services on Android 14+
        val notification = NotificationCompat.Builder(this, "MONITOR_CHANNEL")
            .setContentTitle("Buddymind is Active")
            .setContentText("Monitoring your wellness in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        // 2. Heartbeat: Check usage every 60 seconds
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkUsageAndAlert()
            }
        }, 0, 60000)

        return START_STICKY
    }

    private fun checkUsageAndAlert() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val db = FirebaseFirestore.getInstance()

        // Get the limit set by user (default to 1 min for testing)
        val sharedPrefs = getSharedPreferences("BuddymindPrefs", Context.MODE_PRIVATE)
        val userLimit = sharedPrefs.getInt("time_limit", 1)

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        // Query usage stats for today
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        if (stats != null) {
            // Find the app with the most foreground time
            val topApp = stats.filter { it.totalTimeInForeground > 0 }
                .maxByOrNull { it.totalTimeInForeground }

            topApp?.let { app ->
                val minutes = app.totalTimeInForeground / 1000 / 60
                Log.d("Buddymind", "Checking ${app.packageName}: $minutes mins (Limit: $userLimit)")

                // Trigger if limit is hit and it's not our own app
                if (minutes >= userLimit && !app.packageName.contains("nvker.buddymind")) {

                    // 3. TRY TO FETCH FROM FIREBASE
                    db.collection("community_riddles")
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                val randomDoc = documents.documents.random()
                                val riddle = randomDoc.getString("text") ?: "Stay mindful!"
                                sendChallengeNotification(app.packageName, riddle)
                            } else {
                                // FALLBACK 1: If database is empty, show local riddle
                                sendChallengeNotification(app.packageName, "Riddle: What has a face but no eyes? (A Clock)")
                            }
                        }
                        .addOnFailureListener { e ->
                            // FALLBACK 2: If Firebase connection fails (DEVELOPER_ERROR)
                            Log.e("Buddymind", "Firebase Error: ${e.message}")
                            sendChallengeNotification(app.packageName, "Limit Hit! Riddle: What has legs but doesn't walk? (A Table)")
                        }
                }
            }
        }
    }

    private fun sendChallengeNotification(packageName: String, riddle: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val appName = packageName.substringAfterLast('.')

        // Re-create high-importance channel to ensure the notification pops up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MONITOR_CHANNEL", "Screen Monitor", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val alert = NotificationCompat.Builder(this, "MONITOR_CHANNEL")
            .setContentTitle("Mindfulness Break: $appName")
            .setContentText(riddle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("You've used $appName too long!\n\nSolve this: $riddle"))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Makes it pop up on screen
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        // Use ID 2 so it doesn't replace the main monitoring notification (ID 1)
        notificationManager.notify(2, alert)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MONITOR_CHANNEL",
                "Screen Time Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}