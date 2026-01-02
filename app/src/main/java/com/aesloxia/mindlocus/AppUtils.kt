package com.aesloxia.mindlocus

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false
)

object AppUtils {
    suspend fun getLockableApps(context: Context, blockedApps: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        
        // 1. Get packages with launcher activities
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherPackages = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()

        // 2. Get Keyboards
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val keyboards = imm.enabledInputMethodList.map { it.packageName }.toSet()

        // 3. Get all installed apps
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        allApps.filter { app ->
            val isNotMe = app.packageName != context.packageName
            val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isLauncherApp = launcherPackages.contains(app.packageName)
            val isKeyboard = keyboards.contains(app.packageName)

            isNotMe && (isUserApp || isUpdatedSystemApp || isLauncherApp || isKeyboard)
        }.map { appInfo ->
            AppInfo(
                name = pm.getApplicationLabel(appInfo).toString(),
                packageName = appInfo.packageName,
                icon = pm.getApplicationIcon(appInfo),
                isSelected = blockedApps.contains(appInfo.packageName)
            )
        }.sortedBy { it.name }
    }
}
