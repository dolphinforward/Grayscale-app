package com.dolphinforward.grayscale

import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Uses Shizuku (https://shizuku.rikka.app) to grant WRITE_SECURE_SETTINGS to
 * this app. Shizuku runs a process with shell (adb) identity, which is allowed
 * to grant "development" permissions such as WRITE_SECURE_SETTINGS - exactly
 * what `adb shell pm grant` does, just brokered through Shizuku's binder.
 *
 * The framework's IPackageManager is hidden from the compile SDK, so we reach
 * `grantRuntimePermission` via reflection on the live framework classes.
 */
object ShizukuGranter {

    const val REQUEST_CODE = 4242

    /** True when the Shizuku service is running and reachable. */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** True when this app already holds the Shizuku API permission. */
    fun hasPermission(): Boolean = try {
        isAvailable() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Prompt the user to grant this app access to the Shizuku API. */
    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            // Shizuku not ready; caller handles via status refresh.
        }
    }

    /**
     * Grant WRITE_SECURE_SETTINGS to [packageName] using Shizuku's shell
     * identity. Returns true on success.
     *
     * Primary path: run `pm grant ...` through Shizuku's shell. This is exactly
     * what the ADB instructions do and is version-agnostic. Fallback: call
     * `grantRuntimePermission` on the package-manager binder via reflection (in
     * case `newProcess` is unavailable on a given Shizuku build).
     */
    fun grantWriteSecureSettings(packageName: String): Boolean {
        if (!hasPermission()) return false
        if (grantViaShell(packageName)) return true
        return grantViaBinder(packageName)
    }

    private fun grantViaShell(packageName: String): Boolean {
        return try {
            // Shizuku.newProcess is hidden API; reach it reflectively.
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val cmd = arrayOf(
                "pm", "grant", packageName,
                "android.permission.WRITE_SECURE_SETTINGS"
            )
            val process = newProcess.invoke(null, cmd, null, null) as Process
            process.waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }

    private fun grantViaBinder(packageName: String): Boolean {
        return try {
            val pmBinder: IBinder = SystemServiceHelper.getSystemService("package")
            val wrapped = ShizukuBinderWrapper(pmBinder)

            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val pm = asInterface.invoke(null, wrapped)

            val iPmClass = Class.forName("android.content.pm.IPackageManager")
            val grant = iPmClass.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            // userId 0 == the primary user.
            grant.invoke(pm, packageName, "android.permission.WRITE_SECURE_SETTINGS", 0)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
