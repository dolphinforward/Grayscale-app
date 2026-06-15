package com.dolphinforward.grayscale

import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * Reads and writes the system "color correction" (display daltonizer) secure
 * settings, which is how Android renders the whole screen in grayscale.
 *
 * Grayscale ON  == daltonizer == MODE_MONOCHROMACY (0) AND enabled == 1
 * Grayscale OFF == enabled == 0
 *
 * Every method here assumes the app already holds WRITE_SECURE_SETTINGS (see
 * [PermissionManager]). Writes throw [SecurityException] if it does not.
 */
object GrayscaleController {

    // Secure setting keys. These are hidden constants on Settings.Secure, so we
    // inline the documented string values.
    private const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val KEY_MODE = "accessibility_display_daltonizer"

    /** Daltonizer value for monochromacy / grayscale. */
    private const val MODE_MONOCHROMACY = 0

    val enabledUri: Uri = Settings.Secure.getUriFor(KEY_ENABLED)
    val modeUri: Uri = Settings.Secure.getUriFor(KEY_MODE)

    /** True if the screen is currently rendered in grayscale. */
    fun isGrayscaleOn(context: Context): Boolean {
        val cr = context.contentResolver
        val enabled = Settings.Secure.getInt(cr, KEY_ENABLED, 0) == 1
        val mode = Settings.Secure.getInt(cr, KEY_MODE, -1)
        return enabled && mode == MODE_MONOCHROMACY
    }

    /** Turn the whole screen grayscale. Requires WRITE_SECURE_SETTINGS. */
    fun enableGrayscale(context: Context): Boolean {
        val cr = context.contentResolver
        return try {
            Settings.Secure.putInt(cr, KEY_MODE, MODE_MONOCHROMACY)
            Settings.Secure.putInt(cr, KEY_ENABLED, 1)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Restore normal (color) rendering. Requires WRITE_SECURE_SETTINGS. */
    fun disableGrayscale(context: Context): Boolean {
        val cr = context.contentResolver
        return try {
            Settings.Secure.putInt(cr, KEY_ENABLED, 0)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
