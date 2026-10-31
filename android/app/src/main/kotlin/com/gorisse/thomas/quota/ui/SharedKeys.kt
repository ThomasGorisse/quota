package com.gorisse.thomas.quota.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

/** Keys pairing a provider card's pieces with its detail for the shared transition. */
internal object SharedKeys {
    fun container(provider: String) = "provider-container-$provider"
    fun ring(provider: String) = "provider-ring-$provider"
    fun title(provider: String) = "provider-title-$provider"
    fun subtitle(provider: String) = "provider-subtitle-$provider"
    fun heroLabel(provider: String) = "provider-hero-label-$provider"

    /** Detail → list fades, the direction predictive back scrubs: progress maps straight to opacity. */
    val BackFade = tween<Float>(300, easing = LinearEasing)

    /** Both sides scale to the container's width from its top, so their headers line up. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ContainerResize = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)

    /** The plan line changes text size between card and detail: scale one into the other in place. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val TextResize = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillHeight, Alignment.CenterStart)
}
