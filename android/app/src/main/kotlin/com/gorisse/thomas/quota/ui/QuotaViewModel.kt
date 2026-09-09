package com.gorisse.thomas.quota.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.data.Pairing
import com.gorisse.thomas.quota.data.Snapshot
import com.gorisse.thomas.quota.data.StatusRepository
import com.gorisse.thomas.quota.data.UnauthorizedException
import com.gorisse.thomas.quota.widget.QuotaWidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class QuotaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatusRepository.get(application)

    val snapshot: StateFlow<Snapshot?> = repository.snapshot

    /** Left or Used — the same value the widget reads out of the cache. */
    val displayMode: StateFlow<DisplayMode> = repository.displayMode

    private val _paired = MutableStateFlow(repository.isPaired || repository.isDemo)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()
    val isDemo: Boolean get() = repository.isDemo

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Ticks so "updated 42 s ago" and every "resets in …" keep counting down on screen. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        if (_paired.value) refresh()
    }

    fun pair(pairing: Pairing) {
        repository.pairingStore.demoData = false
        repository.pairingStore.write(pairing)
        _error.value = null
        _paired.value = true
        refresh()
    }

    fun enableDemoData() {
        repository.pairingStore.demoData = true
        _error.value = null
        _paired.value = true
        refresh()
    }

    fun unpair() {
        repository.clearPairing()
        _paired.value = false
        _error.value = null
        QuotaWidgetUpdater.updateAll(getApplication())
    }

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch {
            if (repository.setDisplayMode(mode)) {
                _error.value = null
            } else {
                _error.value = "Couldn\u2019t save that on your other screens."
            }
            QuotaWidgetUpdater.updateAll(getApplication())
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val result = repository.refresh()
            result
                .onSuccess {
                    _error.value = null
                    QuotaWidgetUpdater.updateAll(getApplication())
                }
                .onFailure { throwable ->
                    if (throwable is UnauthorizedException) {
                        unpair()
                        _error.value = "This pairing link has expired or is no longer valid. Use the latest link from your Mac."
                    } else {
                        _error.value = when (throwable) {
                            is IOException -> "Check your connection and make sure the collector on your Mac is running. Then try again."
                            else -> "Your usage couldn’t be loaded. Try again in a moment."
                        }
                    }
                }
            _refreshing.value = false
        }
    }
}
