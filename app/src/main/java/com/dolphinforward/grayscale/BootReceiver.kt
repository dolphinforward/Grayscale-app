package com.dolphinforward.grayscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the monitoring service after a reboot if the feature is enabled,
 * so grayscale enforcement survives restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        if (Prefs(context).enabled) {
            GrayscaleService.start(context)
        }
    }
}
