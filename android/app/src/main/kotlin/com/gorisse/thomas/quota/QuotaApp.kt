package com.gorisse.thomas.quota

import android.app.Application
import com.gorisse.thomas.quota.data.StatusRepository
import com.gorisse.thomas.quota.widget.RefreshWorker
import com.gorisse.thomas.quota.widget.ThemeChangeReceiver

class QuotaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A dark/light flip repaints the widget card instantly but leaves whatever the
        // launcher already holds; ask Glance for a fresh render.
        ThemeChangeReceiver.register(this)
        // Warms the cache-backed snapshot so the first frame is not empty.
        val repository = StatusRepository.get(this)
        if (repository.isPaired || repository.isDemo) {
            RefreshWorker.schedule(this)
        }
    }
}
