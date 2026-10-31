package com.gorisse.thomas.quota.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

/** Keys pairing a provider card's pieces with its detail for the shared transition. */
internal object SharedKeys {
    fun container(provider: String) = "provider-container-$provider"
    fun ring(provider: String) = "provider-ring-$provider"
    fun title(provider: String) = "provider-title-$provider"

    /** Both sides scale to the container's width from its top, so their headers line up. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ContainerResize = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
}
