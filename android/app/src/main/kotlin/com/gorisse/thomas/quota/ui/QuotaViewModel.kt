package com.gorisse.thomas.quota.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _paired = MutableStateFlow(repository.isPaired || repository.isDemo)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

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
        _paired.value = true
        refresh()
    }

    fun enableDemoData() {
        repository.pairingStore.demoData = true
        _paired.value = true
        refresh()
    }

    fun unpair() {
        repository.clearPairing()
        _paired.value = false
        _error.value = null
        QuotaWidgetUpdater.updateAll(getApplication())
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
                        _error.value = throwable.message
                    } else {
                        _error.value = when (throwable) {
                            is IOException -> throwable.message ?: "Could not reach the server"
                            else -> throwable.message ?: "Something went wrong"
                        }
                    }
                }
            _refreshing.value = false
        }
    }
}
