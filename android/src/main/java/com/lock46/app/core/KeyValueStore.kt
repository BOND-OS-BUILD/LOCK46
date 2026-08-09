package com.lock46.app.core

/**
 * Minimal persistence abstraction.
 *
 * Everything in [com.lock46.app.core] is written against this interface rather than
 * against SharedPreferences directly, so the duty / whitelist / PIN logic can be
 * exercised by plain JVM unit tests with no Android framework and no Robolectric.
 */
interface KeyValueStore {
    fun getLong(key: String, default: Long): Long
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String): String?
    fun getStringSet(key: String): Set<String>

    fun putLong(key: String, value: Long)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String?)
    fun putStringSet(key: String, value: Set<String>)

    fun remove(key: String)
}

/** In-memory [KeyValueStore]; used by unit tests and as a safe fallback. */
class InMemoryKeyValueStore(
    initial: Map<String, Any> = emptyMap()
) : KeyValueStore {

    private val map: MutableMap<String, Any> = LinkedHashMap(initial)

    /** Snapshot of the backing map — lets tests simulate process death / reboot. */
    fun snapshot(): Map<String, Any> = LinkedHashMap(map)

    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default
    override fun getString(key: String): String? = map[key] as? String

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> = (map[key] as? Set<String>) ?: emptySet()

    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun putStringSet(key: String, value: Set<String>) { map[key] = LinkedHashSet(value) }

    override fun remove(key: String) { map.remove(key) }
}
