package com.gorisse.thomas.quota.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Redraws every placed widget. Safe to call from anywhere; it never throws at the caller. */
object QuotaWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { QuotaWidget().updateAll(appContext) }
        }
    }
}
