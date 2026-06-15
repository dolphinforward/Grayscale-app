package com.dolphinforward.grayscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by AlarmManager [GrayscaleService.delayMinutes] after the user turns
 * grayscale off. Re-enables grayscale if the feature is still on, and makes
 * sure the monitoring service is running.
 */
class ReapplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        if (!prefs.enabled) return

        if (PermissionManager.hasWriteSecureSettings(context)) {
            GrayscaleController.enableGrayscale(context)
        }
        // Ensure the observer service is alive to catch the next toggle-off.
        GrayscaleService.start(context)
    }
}
