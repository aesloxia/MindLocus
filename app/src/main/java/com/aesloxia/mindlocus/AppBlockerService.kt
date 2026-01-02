package com.aesloxia.mindlocus

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { PreferenceManager(this) }
    private var lastBlockTime = 0L
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (prefs.isBlocking) checkTopApp()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_BLOCKING" -> {
                prefs.isBlocking = true
                startForeground(1, createNotification())
            }
            "STOP_BLOCKING" -> {
                prefs.isBlocking = false
                // Keep service running in foreground but update notification text
                startForeground(1, createNotification())
                
                // Alternatively, if you want the notification to disappear entirely when inactive:
                // stopForeground(STOP_FOREGROUND_REMOVE)
                // stopSelf()
            }
        }
        
        if (intent == null && !prefs.isBlocking) {
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun checkTopApp() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 2000, time)
        
        if (stats.isNullOrEmpty()) return

        val topApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        
        if (topApp != null && topApp != packageName && prefs.isAppBlocked(topApp)) {
            if (time - lastBlockTime > 1500) {
                lastBlockTime = time
                val intent = Intent(this, BlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("BlockerChannel", "MindLocus Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val title = if (prefs.isBlocking) "Lock Mode ACTIVE" else "MindLocus Ready"
        val text = if (prefs.isBlocking) "Your focus is currently protected." else "Scan tag to start focus session."
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "BlockerChannel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
