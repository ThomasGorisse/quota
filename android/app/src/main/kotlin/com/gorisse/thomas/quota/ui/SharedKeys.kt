package com.gorisse.thomas.quota.ui

/** Keys pairing a provider card's pieces with its detail for the shared transition. */
internal object SharedKeys {
    fun container(provider: String) = "provider-container-$provider"
    fun ring(provider: String) = "provider-ring-$provider"
    fun title(provider: String) = "provider-title-$provider"
}
