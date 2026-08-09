package com.lock46.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the set of packages the user explicitly approved for use during duty. */
class WhitelistRepository(private val store: KeyValueStore) {

    companion object {
        private const val KEY_PACKAGES = "whitelist.packages"
    }

    private val _packages = MutableStateFlow(store.getStringSet(KEY_PACKAGES))
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    fun get(): Set<String> = _packages.value

    fun save(packages: Set<String>) {
        val cleaned = packages.filter { it.isNotBlank() }.toSet()
        store.putStringSet(KEY_PACKAGES, cleaned)
        _packages.value = cleaned
    }

    fun contains(packageName: String): Boolean = packageName in _packages.value

    /** Reloads from the store — used after a process restart. */
    fun reload() {
        _packages.value = store.getStringSet(KEY_PACKAGES)
    }
}
