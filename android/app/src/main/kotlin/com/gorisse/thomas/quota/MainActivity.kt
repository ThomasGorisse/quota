package com.gorisse.thomas.quota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
                val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    // Material fade through: pairing and home are siblings, not a stack.
                    AnimatedContent(
                        targetState = paired,
                        transitionSpec = {
                            (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = .92f)) togetherWith
                                fadeOut(tween(90))
                        },
                        label = "paired",
                    ) { isPaired ->
                        if (isPaired) {
                            // Edge to edge: home pads its own content with the window insets.
                            HomeScreen(
                                snapshot = snapshot,
                                demo = viewModel.isDemo,
                                nowMillis = now,
                                refreshing = refreshing,
                                error = error,
                                onRefresh = viewModel::refresh,
                                onUnpair = viewModel::unpair,
                                mode = displayMode,
                                onModeChange = viewModel::setDisplayMode,
                            )
                        } else {
                            PairingScreen(
                                pairingError = error,
                                onPaired = viewModel::pair,
                                onDemoData = viewModel::enableDemoData,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.systemBars)
                                    .consumeWindowInsets(WindowInsets.systemBars),
                            )
                        }
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
