package com.gorisse.thomas.quota.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.*
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.ui.theme.Dimens
import java.time.Instant

/**
 * Every limit of one provider, reached by growing its card. The hero keeps its 112 dp ring and
 * reads ring → window label → reset, top to bottom; the other windows stay text rows, never
 * extra rings, with the same wording as the card. No history plot on the phone.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ProviderDetail(
    name: String, provider: Provider, stale: Boolean, now: Instant,
    mode: DisplayMode, onBack: () -> Unit,
    sharedScope: SharedTransitionScope, visibilityScope: AnimatedVisibilityScope,
) = with(sharedScope) {
    val windows = provider.glanceWindows()
    val hero = windows.firstOrNull()
    // The container keeps the card's corners while it travels, then squares off on the screen.
    // The clip reads the corner when it draws, so the animation never recomposes this screen.
    val corner = visibilityScope.transition.animateDp(label = "detail-corner") {
        if (it == EnterExitState.Visible) 0.dp else 24.dp
    }
    val clip = remember(corner) { OverlayClip(AnimatedCornerShape(corner)) }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .sharedBounds(rememberSharedContentState(SharedKeys.container(name)), visibilityScope,
                exit = fadeOut(SharedKeys.BackFade),
                resizeMode = SharedKeys.ContainerResize,
                clipInOverlayDuringTransition = clip),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = DetailBarHeight, bottom = 24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.sharedElement(rememberSharedContentState(SharedKeys.title(name)), visibilityScope)) {
                        ProviderMark(ProviderId.of(name.lowercase()), stale = stale)
                        // Same style as the card: the title travels as one element, it does not resize.
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                    val subtitle = providerSubtitle(name, provider.plan)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.sharedBounds(rememberSharedContentState(SharedKeys.subtitle(name)),
                            visibilityScope, resizeMode = SharedKeys.TextResize))
                    Spacer(Modifier.height(16.dp))
                    if (hero != null) {
                        RingGauge(hero, stale, diameter = 112.dp, resetsIn = hero.resetsIn(now),
                            showDetails = false, mode = mode,
                            modifier = Modifier.sharedElement(rememberSharedContentState(SharedKeys.ring(name)), visibilityScope))
                        Text(hero.glanceLabel(), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.sharedElement(rememberSharedContentState(SharedKeys.heroLabel(name)), visibilityScope))
                        Text(formatResetsIn(hero.resetsIn(now)), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SeverityPill(if (stale) Severity.STALE else hero.severity)
                    } else {
                        NoData(provider, textAlign = TextAlign.Center)
                    }
                }
                if (windows.size > 1) {
                    Text("Other limits", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 32.dp, bottom = 8.dp))
                    Column(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.CardRadius))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)),
                    ) {
                        windows.drop(1).forEachIndexed { index, window ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${window.glanceLabel()} ${window.shown(mode)}% ${if (mode.isUsed) "used" else "left"}",
                                    style = MaterialTheme.typography.labelLarge)
                                Text(formatResetsIn(window.resetsIn(now)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            DetailBar(scrolled = scrolled, onBack = onBack)
        }
    }
}

private val DetailBarHeight = 64.dp

/**
 * Pinned back button. Transparent at rest; once the content scrolls, a veil of the surface fades
 * in behind it so text never runs under the status bar, like the home bar.
 */
@Composable
private fun DetailBar(scrolled: Boolean, onBack: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val veil by animateFloatAsState(if (scrolled) 1f else 0f, tween(200), label = "detail-veil")
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(barVeil(surface, veil, solidUntil = 1f - BarFade.toPx() / size.height)) }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = BarFade),
    ) {
        Row(Modifier.fillMaxWidth().height(DetailBarHeight).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to all providers")
            }
        }
    }
}

/** Rounded corners whose radius is read at draw time, from a state the transition animates. */
private class AnimatedCornerShape(private val radius: State<Dp>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        RoundedCornerShape(radius.value).createOutline(size, layoutDirection, density)
}
