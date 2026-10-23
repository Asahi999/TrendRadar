package com.wechatbroadcast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object WeChatStatus {
    const val PACKAGE_NAME = "com.tencent.mm"

    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BroadcastAccessibilityService::class.java)
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return setting.split(':').any { raw ->
            ComponentName.unflattenFromString(raw) == expected
        }
    }

    fun serviceConnected(): Boolean = BroadcastAccessibilityService.isConnected()

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
