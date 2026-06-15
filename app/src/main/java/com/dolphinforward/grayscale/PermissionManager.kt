package com.dolphinforward.grayscale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Single source of truth for whether the app can write the grayscale secure
 * settings, and for obtaining that capability through any of the three
 * supported elevation paths.
 *
 * All paths converge on the same end state: the app holding
 * WRITE_SECURE_SETTINGS. Once [hasWriteSecureSettings] is true, the rest of the
 * app talks to [GrayscaleController] directly with no further IPC.
 */
object PermissionManager {

    /** True once WRITE_SECURE_SETTINGS has been granted to this app. */
    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether Shizuku is running and has granted us its API permission. */
    fun shizukuReady(): Boolean = ShizukuGranter.hasPermission()

    /** Whether the Shizuku service is running (permission maybe not yet granted). */
    fun shizukuRunning(): Boolean = ShizukuGranter.isAvailable()

    /** Whether a root shell is available. */
    fun rootAvailable(): Boolean = RootGranter.isAvailable()

    /**
     * Try to grant WRITE_SECURE_SETTINGS using whatever elevation is available,
     * preferring Shizuku, then root. Returns true if the permission is held
     * afterwards. The ADB path is manual and handled by the user.
     */
    fun tryGrant(context: Context): Boolean {
        if (hasWriteSecureSettings(context)) return true

        val pkg = context.packageName
        if (ShizukuGranter.hasPermission() && ShizukuGranter.grantWriteSecureSettings(pkg)) {
            if (hasWriteSecureSettings(context)) return true
        }
        if (RootGranter.isAvailable() && RootGranter.grantWriteSecureSettings(pkg)) {
            if (hasWriteSecureSettings(context)) return true
        }
        return hasWriteSecureSettings(context)
    }

    /** Exact ADB command the user can run to grant the permission manually. */
    fun adbCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
}
