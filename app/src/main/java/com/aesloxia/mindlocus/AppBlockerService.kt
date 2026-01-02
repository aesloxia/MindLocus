package com.aesloxia.mindlocus

import android.app.*
import android.app.usage.UsageEvents
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
            handler.postDelayed(this, 300)
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
                startForeground(1, createNotification())
            }
        }
        
        if (intent == null && prefs.isBlocking) {
            startForeground(1, createNotification())
        } else if (intent == null && !prefs.isBlocking) {
            stopSelf()
        }
        
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // This is triggered when the app is swiped away from recent apps.
        // We schedule an immediate restart to bypass the system's 10-15s delay.
        if (prefs.isBlocking) {
            val restartServiceIntent = Intent(applicationContext, this.javaClass).also {
                it.setPackage(packageName)
            }
            val restartServicePendingIntent = PendingIntent.getService(
                this, 1, restartServiceIntent, 
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 500, // Restart in 0.5 seconds
                restartServicePendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun checkTopApp() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        val events = usm.queryEvents(time - 5000, time)
        val event = UsageEvents.Event()
        var foregroundApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.packageName
            }
        }

        if (foregroundApp == null) {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 5000, time)
            foregroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }
        
        if (foregroundApp != null && foregroundApp != packageName && prefs.isAppBlocked(foregroundApp)) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBlockTime > 1000) {
                lastBlockTime = currentTime
                
                try {
                    val intent = Intent(this, BlockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                                 Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                                 Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Handled via MIUI Background Pop-up permission
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("BlockerChannel", "MindLocus Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val title = if (prefs.isBlocking) "Lock Mode ACTIVE" else "MindLocus Standby"
        val text = if (prefs.isBlocking) "Digital space is protected." else "Ready to start focus session."
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "BlockerChannel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
