package com.aesloxia.mindlocus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object ManufacturerUtils {

    fun getBackgroundSettingsIntent(context: Context): Intent {
        val brand = Build.MANUFACTURER.lowercase()
        val intent = Intent()

        when {
            brand.contains("xiaomi") -> {
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
            brand.contains("oppo") || brand.contains("realme") -> {
                intent.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            }
            brand.contains("vivo") -> {
                intent.component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            }
            brand.contains("huawei") -> {
                intent.component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            }
            brand.contains("samsung") -> {
                // Samsung doesn't have a direct "autostart" but uses Battery Optimization
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            else -> {
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.parse("package:${context.packageName}")
            }
        }
        return intent
    }

    fun getManufacturerLabel(): String {
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> "MIUI Settings"
            "samsung" -> "Device Care"
            "oppo", "realme" -> "Startup Manager"
            "huawei" -> "App Launch"
            else -> "App Settings"
        }
    }
}
