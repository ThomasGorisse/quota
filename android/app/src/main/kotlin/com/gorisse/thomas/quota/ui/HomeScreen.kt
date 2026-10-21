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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.*
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.ui.theme.*
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: Snapshot?, nowMillis: Long, refreshing: Boolean, error: String?,
    onRefresh: () -> Unit, onUnpair: () -> Unit, modifier: Modifier = Modifier,
    demo: Boolean = false,
    mode: DisplayMode = DisplayMode.REMAINING,
    onModeChange: (DisplayMode) -> Unit = {},
) {
    val stale = snapshot?.isStale(nowMillis) ?: true
    val now = Instant.ofEpochMilli(nowMillis)
    var confirmUnpair by remember { mutableStateOf(false) }
    if (confirmUnpair) {
        AlertDialog(onDismissRequest = { confirmUnpair = false },
            title = { Text(if (demo) "Leave sample data?" else "Disconnect this phone?") },
            text = { Text(if (demo) "The sample numbers go away and you return to pairing, where you can connect your own usage." else "You can reconnect with your pairing link. Your widgets will return to the pairing state.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; onUnpair() }) { Text(if (demo) "Leave sample data" else "Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } })
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(
            snapshot?.let { "Updated " + formatAge(it.ageSeconds(nowMillis)) } ?: "Waiting for first update",
            demo, onUnpair = { confirmUnpair = true }, mode = mode, onModeChange = onModeChange,
            // Nothing to switch between until there is a payload, and the write would fail.
            showToggle = snapshot != null, onRefresh = onRefresh, refreshing = refreshing)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            when {
                snapshot == null && error != null -> ErrorState(error, onRefresh, onUnpair)
                snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(32.dp))
                    else Text("Pull down to load your limits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (error != null) item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Text("Couldn't refresh. Showing your last update.", Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    if (snapshot.orderedProviders().isEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Connected. Waiting for usage.", style = MaterialTheme.typography.titleLarge)
                                Text("Open Claude, Codex or Gemini on your Mac and keep the Quota collector running. Your first reading will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = onRefresh) { Text("Check for an update") }
                            }
                        }
                    }
                    items(snapshot.orderedProviders(), key = { it.first }) { (name, provider) ->
                        ProviderCard(name, provider, stale || provider.isStaleAt(nowMillis), now, mode)
                    }
                    item {
                        Text("The most-used limit leads each card. Pull down to refresh.",
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    ageLabel: String, demo: Boolean, onUnpair: () -> Unit,
    mode: DisplayMode, onModeChange: (DisplayMode) -> Unit, showToggle: Boolean,
    onRefresh: () -> Unit, refreshing: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuotaWordmark(Modifier.weight(1f))
            if (demo) SampleDataChip(Modifier.padding(end = 8.dp))
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Filled.Refresh, "Refresh usage", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUnpair) {
                Icon(Icons.Filled.LinkOff, if (demo) "Leave sample data" else "Disconnect this phone",
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Your capacity", style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (demo) "A preview of your day" else ageLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showToggle) DisplayToggle(mode, onModeChange)
        }
    }
}

/** The web dashboard's Left / Used control, in Compose: two segments in one rounded pill. */
@Composable
private fun DisplayToggle(mode: DisplayMode, onModeChange: (DisplayMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
            .padding(2.dp)
            .semantics { contentDescription = "Show percentages as" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(DisplayMode.REMAINING to "Left", DisplayMode.USED to "Used").forEach { (value, label) ->
            val selected = value == mode
            Surface(
                onClick = { onModeChange(value) },
                selected = selected,
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            ) {
                Text(
                    label,
                    Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Persistent, never dismissable: while the app runs on [DemoData] every screen has to say so,
 * or a screenshot of it reads as someone's real usage. Same 12 dp chip radius as the severity
 * pill next to it, but outlined rather than tinted — severity owns the colour here.
 */
@Composable
private fun SampleDataChip(modifier: Modifier = Modifier) {
    Text(
        text = "Sample data",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.ChipRadius))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .10f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = "Showing sample data, not your own usage" },
    )
}

@Composable
private fun SeverityPill(severity: Severity) {
    val ink = severity.textColor()
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(severity.color.copy(alpha = .12f))
            .padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        Text(severity.glanceLabel(), style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ProviderCard(
    name: String, provider: Provider, stale: Boolean, now: Instant,
    mode: DisplayMode,
) {
    val windows = provider.glanceWindows()
    val hero = windows.firstOrNull()
    var expanded by remember(name) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (hero != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RingGauge(hero, stale, diameter = 112.dp, resetsIn = hero.resetsIn(now),
                            showDetails = false, mode = mode)
                        if (windows.size > 1) {
                            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (expanded) "Hide details" else "Details")
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderMark(ProviderId.of(name.lowercase()), stale = stale)
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                    val subtitle = providerSubtitle(name, provider.plan)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hero != null) {
                        Text(hero.glanceLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(formatResetsIn(hero.resetsIn(now)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SeverityPill(if (stale) Severity.STALE else hero.severity)
                        windows.drop(1).forEach { window ->
                            Text("${window.glanceLabel()} ${window.shown(mode)}% ${if (mode.isUsed) "used" else "left"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("No data", style = MaterialTheme.typography.titleMedium)
                        Text(if (provider.extra.containsKey("error")) "Couldn’t read this provider. Check its connection on your Mac." else "Waiting for a usage reading from your Mac.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded && windows.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                windows.drop(1).forEach { window ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${window.glanceLabel()} ${window.shown(mode)}% ${if (mode.isUsed) "used" else "left"}",
                            style = MaterialTheme.typography.labelLarge)
                        Text(formatResetsIn(window.resetsIn(now)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
