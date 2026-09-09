package com.gorisse.thomas.quota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.Provider
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Snapshot
import com.gorisse.thomas.quota.ui.theme.Dimens
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.accent
import com.gorisse.thomas.quota.ui.theme.color
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: Snapshot?,
    nowMillis: Long,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stale = snapshot?.isStale(nowMillis) ?: true
    val now = Instant.ofEpochMilli(nowMillis)

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(
            severity = snapshot?.severity(nowMillis) ?: Severity.STALE,
            ageLabel = snapshot?.let { "updated " + formatAge(it.ageSeconds(nowMillis)) }
                ?: "never updated",
            onUnpair = onUnpair,
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                snapshot == null && error != null ->
                    ErrorState(message = error, onRetry = onRefresh)

                snapshot == null ->
                    EmptyState(refreshing = refreshing)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.Grid * 2,
                        end = Dimens.Grid * 2,
                        bottom = Dimens.Grid * 4,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Grid * 2),
                ) {
                    if (error != null) {
                        item { InlineError(error) }
                    }
                    items(snapshot.orderedProviders(), key = { it.first }) { (name, provider) ->
                        ProviderCard(name = name, provider = provider, stale = stale, now = now)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(severity: Severity, ageLabel: String, onUnpair: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Grid * 2, vertical = Dimens.Grid * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeverityPill(severity)
        Text(
            text = ageLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.Grid * 1.5f).weight(1f),
        )
        IconButton(onClick = onUnpair) {
            Icon(
                imageVector = Icons.Filled.LinkOff,
                contentDescription = "Unpair this device",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeverityPill(severity: Severity) {
    val label = when (severity) {
        Severity.OK -> "OK"
        Severity.WARN -> "Warn"
        Severity.CRITICAL -> "Critical"
        Severity.STALE -> "Stale"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.ChipRadius))
            .background(severity.color.copy(alpha = QuotaColors.TrackAlpha))
            .padding(horizontal = Dimens.Grid * 1.5f, vertical = Dimens.Grid * 0.75f),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.Grid)
                .clip(CircleShape)
                .background(severity.color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = severity.color,
            modifier = Modifier.padding(start = Dimens.Grid * 0.75f),
        )
    }
}

@Composable
private fun ProviderCard(name: String, provider: Provider, stale: Boolean, now: Instant) {
    val accent = ProviderId.of(name.lowercase())?.accent ?: MaterialTheme.colorScheme.onSurface
    Card(
        shape = RoundedCornerShape(Dimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Grid * 2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Dimens.Grid * 1.25f)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Dimens.Grid),
                )
                provider.plan?.let { plan ->
                    Text(
                        text = plan.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier
                            .padding(start = Dimens.Grid)
                            .clip(RoundedCornerShape(Dimens.ChipRadius))
                            .background(accent.copy(alpha = QuotaColors.TrackAlpha))
                            .padding(horizontal = Dimens.Grid, vertical = Dimens.Grid * 0.25f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Grid * 2),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                // Three rings have to fit side by side on a 6 inch phone, so the diameter
                // shrinks with the count rather than letting the row scroll.
                val diameter = if (provider.windows.size >= 3) 96.dp else 112.dp
                provider.windows.forEach { window ->
                    RingGauge(
                        window = window,
                        stale = stale,
                        diameter = diameter,
                        resetsIn = window.resetsIn(now),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = QuotaColors.Critical,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ChipRadius))
            .background(QuotaColors.Critical.copy(alpha = QuotaColors.TrackAlpha))
            .padding(Dimens.Grid * 1.5f),
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.Grid * 4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Could not load your limits",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.Grid),
        )
        androidx.compose.material3.Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = Dimens.Grid * 2),
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState(refreshing: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (refreshing) "Loading…" else "Pull down to load your limits",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
