package com.gorisse.thomas.quota.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Redraws every placed widget when the system configuration changes — in practice, the
 * dark/light flip.
 *
 * `ACTION_CONFIGURATION_CHANGED` is not deliverable to a manifest-declared receiver, so
 * this one is registered from [com.gorisse.thomas.quota.QuotaApp] and lives as long as the
 * process. The ring bitmaps no longer bake any theme-dependent pixel (see [RingBitmap]),
 * so this is the belt to that fix's braces: it also refreshes the font scale, the locale
 * and anything else Glance measured at render time.
 */
class ThemeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_CONFIGURATION_CHANGED) return
        QuotaWidgetUpdater.updateAll(context)
    }

    companion object {
        fun register(context: Context) {
            context.registerReceiver(
                ThemeChangeReceiver(),
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            )
        }
    }
}
