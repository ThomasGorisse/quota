package com.gorisse.thomas.quota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gorisse.thomas.quota.ui.HomeScreen
import com.gorisse.thomas.quota.ui.PairingScreen
import com.gorisse.thomas.quota.ui.QuotaViewModel
import com.gorisse.thomas.quota.ui.theme.QuotaTheme
import com.gorisse.thomas.quota.widget.RefreshWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            QuotaTheme {
                val viewModel: QuotaViewModel = viewModel()
                val paired by viewModel.paired.collectAsStateWithLifecycle()
                val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
                val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
                val error by viewModel.error.collectAsStateWithLifecycle()
                val now by viewModel.now.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    val insets = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .consumeWindowInsets(WindowInsets.systemBars)
                    if (paired) {
                        HomeScreen(
                            snapshot = snapshot,
                            demo = viewModel.isDemo,
                            nowMillis = now,
                            refreshing = refreshing,
                            error = error,
                            onRefresh = viewModel::refresh,
                            onUnpair = viewModel::unpair,
                            modifier = insets,
                        )
                    } else {
                        PairingScreen(
                            pairingError = error,
                            onPaired = viewModel::pair,
                            onDemoData = viewModel::enableDemoData,
                            modifier = insets,
                        )
                    }
                }
            }
        }
    }

    /** Every app open is a refresh: the widget and the screen share the same cache. */
    override fun onStart() {
        super.onStart()
        RefreshWorker.refreshNow(this)
    }
}
