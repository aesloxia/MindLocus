package com.aesloxia.mindlocus

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MindLocus", Context.MODE_PRIVATE)

    var isFirstRun: Boolean
        get() = prefs.getBoolean("is_first_run", true)
        set(value) = prefs.edit().putBoolean("is_first_run", value).apply()

    var isBlocking: Boolean
        get() = prefs.getBoolean("is_blocking", false)
        set(value) = prefs.edit().putBoolean("is_blocking", value).apply()

    var registeredTag: String?
        get() = prefs.getString("registered_tag", null)
        set(value) = prefs.edit().putString("registered_tag", value).apply()

    var blockedApps: Set<String>
        get() = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("blocked_apps", value).apply()

    fun isAppBlocked(packageName: String): Boolean = blockedApps.contains(packageName)
}
