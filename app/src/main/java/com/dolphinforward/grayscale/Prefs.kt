package com.dolphinforward.grayscale

import android.content.Context

/**
 * Thin wrapper over SharedPreferences for the two pieces of state the app
 * persists: whether the auto-grayscale feature is enabled ("master switch")
 * and how many minutes to wait before re-enabling grayscale after the user
 * turns it off.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("auto_grayscale", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Delay before grayscale is re-applied, in minutes. Clamped to a sane range. */
    var delayMinutes: Int
        get() = sp.getInt(KEY_DELAY_MIN, DEFAULT_DELAY_MIN)
        set(value) = sp.edit()
            .putInt(KEY_DELAY_MIN, value.coerceIn(MIN_DELAY_MIN, MAX_DELAY_MIN))
            .apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DELAY_MIN = "delay_minutes"

        const val DEFAULT_DELAY_MIN = 5
        const val MIN_DELAY_MIN = 1
        const val MAX_DELAY_MIN = 240
    }
}
