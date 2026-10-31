package com.gorisse.thomas.quota.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.*
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.ui.theme.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Height of the pinned bar, below the status bar. The list scrolls underneath it. */
private val BarHeight = 64.dp

/**
 * Home and provider detail share one screen: a card grows into its detail (container
 * transform) and predictive back scrubs that same transition, so the gesture previews exactly
 * where it lands. Only the arc animates its value; this motion is navigation, never data.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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

    val providers = snapshot?.orderedProviders().orEmpty()
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val navigation = remember { SeekableTransitionState(openName) }
    val transition = rememberTransition(navigation, label = "provider")
    val scope = rememberCoroutineScope()
    LaunchedEffect(openName) { navigation.animateTo(openName) }
    // A provider can leave the payload while its detail is open; go back to the list then.
    LaunchedEffect(openName, providers) {
        if (openName != null && providers.none { it.first == openName }) openName = null
    }
    PredictiveBackHandler(enabled = openName != null) { progress ->
        try {
            progress.collect { navigation.seekTo(it.progress, targetState = null) }
            // Closing disables this handler, which cancels this block: the catch below finishes the animation.
            openName = null
            navigation.animateTo(null)
        } catch (e: CancellationException) {
            // A cancelled gesture leaves openName set and settles back on the detail; a committed one settles on the list.
            scope.launch { navigation.animateTo(openName) }
        }
    }

    SharedTransitionLayout(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        transition.AnimatedContent(
            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
        ) { name ->
            val entry = name?.let { n -> providers.firstOrNull { it.first == n } }
            if (entry == null) {
                Overview(
                    snapshot = snapshot, nowMillis = nowMillis, refreshing = refreshing, error = error,
                    stale = stale, now = now, demo = demo, mode = mode, onModeChange = onModeChange,
                    onRefresh = onRefresh, onUnpair = { confirmUnpair = true },
                    onOpen = { openName = it },
                    sharedScope = this@SharedTransitionLayout, visibilityScope = this,
                )
            } else {
                val (detailName, provider) = entry
                ProviderDetail(
                    name = detailName, provider = provider,
                    stale = stale || provider.isStaleAt(nowMillis), now = now, mode = mode,
                    onBack = { openName = null },
                    sharedScope = this@SharedTransitionLayout, visibilityScope = this,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun Overview(
    snapshot: Snapshot?, nowMillis: Long, refreshing: Boolean, error: String?,
    stale: Boolean, now: Instant, demo: Boolean,
    mode: DisplayMode, onModeChange: (DisplayMode) -> Unit,
    onRefresh: () -> Unit, onUnpair: () -> Unit, onOpen: (String) -> Unit,
    sharedScope: SharedTransitionScope, visibilityScope: AnimatedVisibilityScope,
) {
    // Edge to edge: the list runs under the status and navigation bars, padded by their insets.
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val top = safe.calculateTopPadding() + BarHeight
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val pullState = rememberPullToRefreshState()

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = onRefresh, state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState, isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                )
            },
        ) {
            when {
                snapshot == null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(
                        start = safe.calculateStartPadding(direction) + 20.dp, top = top,
                        end = safe.calculateEndPadding(direction) + 20.dp,
                    )),
                ) {
                    // Nothing to switch between until there is a payload, and the write would fail.
                    Header("Waiting for first update", demo, mode, onModeChange, showToggle = false)
                    if (error != null) ErrorState(error, onRefresh, onUnpair)
                    else Box(Modifier.fillMaxWidth().padding(vertical = 160.dp), contentAlignment = Alignment.Center) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(32.dp))
                        else Text("Pull down to load your limits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = safe.calculateStartPadding(direction) + 20.dp,
                        top = top,
                        end = safe.calculateEndPadding(direction) + 20.dp,
                        bottom = safe.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Header(
                            "Updated " + formatAge(snapshot.ageSeconds(nowMillis)),
                            demo, mode = mode, onModeChange = onModeChange,
                        )
                    }
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
                        ProviderCard(name, provider, stale || provider.isStaleAt(nowMillis), now, mode,
                            onOpen = { onOpen(name) }, sharedScope = sharedScope, visibilityScope = visibilityScope)
                    }
                    item {
                        Text("The most-used limit leads each card. Tap one for every limit.",
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TopBar(
            demo = demo, scrolled = scrolled, refreshing = refreshing,
            onRefresh = onRefresh, onUnpair = onUnpair,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Pinned, transparent at rest. Once the list scrolls under it, a gradient from the background
 * to transparent fades in behind it — no flat fill, no elevation, the cards dissolve upward.
 */
@Composable
private fun TopBar(
    demo: Boolean, scrolled: Boolean, refreshing: Boolean,
    onRefresh: () -> Unit, onUnpair: () -> Unit, modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val scrim by animateFloatAsState(if (scrolled) 1f else 0f, tween(200), label = "bar-scrim")
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.verticalGradient(
                    0f to background.copy(alpha = scrim),
                    .7f to background.copy(alpha = scrim * .9f),
                    1f to background.copy(alpha = 0f),
                ))
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(BarHeight).padding(start = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    }
}

@Composable
private fun Header(
    ageLabel: String, demo: Boolean,
    mode: DisplayMode, onModeChange: (DisplayMode) -> Unit, showToggle: Boolean = true,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your capacity", style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
internal fun SeverityPill(severity: Severity) {
    val ink = severity.textColor()
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(severity.color.copy(alpha = .12f))
            .padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        Text(severity.glanceLabel(), style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ProviderCard(
    name: String, provider: Provider, stale: Boolean, now: Instant,
    mode: DisplayMode, onOpen: () -> Unit,
    sharedScope: SharedTransitionScope, visibilityScope: AnimatedVisibilityScope,
) = with(sharedScope) {
    val windows = provider.glanceWindows()
    val hero = windows.firstOrNull()
    Card(onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { onClick(label = "Show every $name limit", action = null) }
            .sharedBounds(rememberSharedContentState(SharedKeys.container(name)), visibilityScope,
                resizeMode = SharedKeys.ContainerResize,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(24.dp)))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (hero != null) {
                    RingGauge(hero, stale, diameter = 112.dp, resetsIn = hero.resetsIn(now),
                        showDetails = false, mode = mode,
                        modifier = Modifier.sharedElement(rememberSharedContentState(SharedKeys.ring(name)), visibilityScope))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.sharedElement(rememberSharedContentState(SharedKeys.title(name)), visibilityScope)) {
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
                        NoData(provider)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoData(provider: Provider) {
    Text("No data", style = MaterialTheme.typography.titleMedium)
    Text(if (provider.extra.containsKey("error")) "Couldn’t read this provider. Check its connection on your Mac." else "Waiting for a usage reading from your Mac.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
