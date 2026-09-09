package com.gorisse.thomas.quota

import android.app.Application
import com.gorisse.thomas.quota.data.StatusRepository
import com.gorisse.thomas.quota.widget.RefreshWorker

class QuotaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warms the cache-backed snapshot so the first frame is not empty.
        val repository = StatusRepository.get(this)
        if (repository.isPaired || repository.isDemo) {
            RefreshWorker.schedule(this)
        }
    }
}
