package com.dolphinforward.grayscale

import java.io.DataOutputStream

/**
 * Grants WRITE_SECURE_SETTINGS via root (`su`), if the device is rooted.
 * This mirrors what the ADB and Shizuku paths do, using a root shell instead.
 */
object RootGranter {

    /** Best-effort check for an available `su` binary that grants root. */
    fun isAvailable(): Boolean = runAsRoot("id") { line ->
        line.contains("uid=0")
    }

    /** Run `pm grant` as root to grant the permission to [packageName]. */
    fun grantWriteSecureSettings(packageName: String): Boolean {
        val cmd = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        return runAsRoot(cmd) { true }
    }

    /**
     * Execute [command] in a root shell. Returns true if the process exited 0
     * and [onOutput] (called with the trimmed stdout) returns true.
     */
    private fun runAsRoot(command: String, onOutput: (String) -> Boolean): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            exit == 0 && onOutput(output)
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
}
