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

    var registeredTags: Set<String>
        get() = prefs.getStringSet("registered_tags", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("registered_tags", value).apply()

    var blockedApps: Set<String>
        get() = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("blocked_apps", value).apply()

    fun isAppBlocked(packageName: String): Boolean = blockedApps.contains(packageName)
    
    fun addTag(tagId: String) {
        val tags = registeredTags.toMutableSet()
        tags.add(tagId)
        registeredTags = tags
    }

    fun removeTag(tagId: String) {
        val tags = registeredTags.toMutableSet()
        tags.remove(tagId)
        registeredTags = tags
    }
}
