package com.gorisse.thomas.quota.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.*
import com.gorisse.thomas.quota.ui.theme.*
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: Snapshot?, nowMillis: Long, refreshing: Boolean, error: String?,
    onRefresh: () -> Unit, onUnpair: () -> Unit, modifier: Modifier = Modifier,
    demo: Boolean = false,
) {
    val stale = snapshot?.isStale(nowMillis) ?: true
    val now = Instant.ofEpochMilli(nowMillis)
    var confirmUnpair by remember { mutableStateOf(false) }
    if (confirmUnpair) {
        AlertDialog(onDismissRequest = { confirmUnpair = false },
            title = { Text(if (demo) "Leave demo?" else "Disconnect this phone?") },
            text = { Text(if (demo) "Return to pairing to connect your own usage." else "You can reconnect with your pairing link. Your widgets will return to the pairing state.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; onUnpair() }) { Text(if (demo) "Leave demo" else "Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } })
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(snapshot?.severity(nowMillis) ?: Severity.STALE,
            snapshot?.let { "Updated " + formatAge(it.ageSeconds(nowMillis)) } ?: "Waiting for first update",
            demo, onUnpair = { confirmUnpair = true })
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            when {
                snapshot == null && error != null -> ErrorState(error, onRefresh, onUnpair)
                snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(32.dp))
                    else Text("Pull down to load your limits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (error != null) item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Text("Couldn't refresh. Showing your last update.", Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    items(snapshot.orderedProviders(), key = { it.first }) { (name, provider) ->
                        ProviderCard(name, provider, snapshot, stale, now)
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Every number is what’s left.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Pull to refresh", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(severity: Severity, ageLabel: String, demo: Boolean, onUnpair: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 0.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuotaWordmark(Modifier.weight(1f))
            if (demo) Text("Demo", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            SeverityPill(severity)
            IconButton(onClick = onUnpair) {
                Icon(Icons.Filled.LinkOff, if (demo) "Leave demo" else "Disconnect this phone",
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(ageLabel, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier)
    }
}

@Composable
private fun SeverityPill(severity: Severity) {
    val ink = severity.textColor()
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(severity.color.copy(alpha = .12f))
            .padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        Text(when (severity) {
            Severity.OK -> "All good"
            Severity.WARN -> "Watch usage"
            Severity.CRITICAL -> "Running low"
            Severity.STALE -> "Out of date"
        }, style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ProviderCard(name: String, provider: Provider, snapshot: Snapshot, stale: Boolean, now: Instant) {
    Card(shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderMark(ProviderId.of(name.lowercase()))
                Text(name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
                provider.plan?.let { plan ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Text(plan.replaceFirstChar(Char::uppercase), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val primary = provider.windows.filter { it.id != "reserve" }
            primary.chunked(2).forEach { windows ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val diameter = ((maxWidth - 24.dp) / 2).coerceAtMost(120.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        windows.forEach { window ->
                            RingGauge(window, stale, Modifier.weight(1f), diameter, window.resetsIn(now))
                        }
                    }
                }
            }
            provider.windows.filter { it.id == "reserve" }.forEach { window ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(window.label, style = MaterialTheme.typography.titleSmall)
                        Text(formatResetsIn(window.resetsIn(now)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RingGauge(window, stale, diameter = 72.dp, resetsIn = window.resetsIn(now), showDetails = false)
                }
            }
            ProviderHistory(name.lowercase(), snapshot.status.history, stale, now)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onPairAgain: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your limits are out of reach", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("Try again") }
                TextButton(onClick = onPairAgain) { Text("Use another pairing link") }
            }
        }
    }
}
