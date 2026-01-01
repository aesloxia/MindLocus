package com.aesloxia.mindlocus

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationBlockerService : NotificationListenerService() {

    private val prefs by lazy { PreferenceManager(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (prefs.isBlocking) {
            val packageName = sbn?.packageName
            if (packageName != null && prefs.isAppBlocked(packageName)) {
                // Cancel the notification from the blocked app
                cancelNotification(sbn.key)
            }
        }
    }
}
