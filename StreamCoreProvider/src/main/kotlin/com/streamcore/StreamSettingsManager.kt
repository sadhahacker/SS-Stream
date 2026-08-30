package com.streamcore

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast

/**
 * Persists and applies the user's in-app source preferences
 * (Settings > Extensions > StreamCore > gear icon).
 */
object StreamSettingsManager {
    private const val PREFS_NAME = "streamcore_provider_prefs"
    private const val KEY_TOP_SOURCE = "key_top_priority_source"
    private const val KEY_DISABLED_SOURCES = "key_disabled_sources"
    private const val DEFAULT_TOP_SOURCE = "VidCore"

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(context: Context? = null): SharedPreferences? {
        if (sharedPrefs == null && context != null) {
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return sharedPrefs
    }

    fun getTopPrioritySource(context: Context? = null): String {
        return getPrefs(context)?.getString(KEY_TOP_SOURCE, DEFAULT_TOP_SOURCE) ?: DEFAULT_TOP_SOURCE
    }

    fun setTopPrioritySource(sourceName: String, context: Context? = null) {
        getPrefs(context)?.edit()?.putString(KEY_TOP_SOURCE, sourceName)?.apply()
    }

    fun getDisabledSources(context: Context? = null): Set<String> {
        return getPrefs(context)?.getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()
    }

    fun toggleSource(sourceName: String, isEnabled: Boolean, context: Context? = null) {
        val prefs = getPrefs(context) ?: return
        val currentDisabled = prefs.getStringSet(KEY_DISABLED_SOURCES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isEnabled) currentDisabled.remove(sourceName) else currentDisabled.add(sourceName)
        prefs.edit().putStringSet(KEY_DISABLED_SOURCES, currentDisabled).apply()
    }

    /**
     * Returns all registered sources sorted by runtime priority:
     * 1. The user's chosen top-priority source is bumped above everything else.
     * 2. Other sources retain their base priority.
     * 3. Disabled sources are filtered out entirely.
     */
    fun getRuntimeSortedSources(context: Context? = null): List<StreamingSource> {
        val topSource = getTopPrioritySource(context)
        val disabled = getDisabledSources(context)

        return REGISTERED_SOURCES
            .filter { it.enabled && !disabled.contains(it.name) }
            .sortedByDescending { source ->
                if (source.name.equals(topSource, ignoreCase = true)) Int.MAX_VALUE else source.priority
            }
    }

    fun showSettingsDialog(context: Context) {
        init(context)

        val sources = REGISTERED_SOURCES.map { it.name }.toTypedArray()
        val currentTop = getTopPrioritySource(context)
        var selectedIndex = sources.indexOf(currentTop).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(context)
            .setTitle("StreamCore - Select Primary Source")
            .setSingleChoiceItems(sources, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton("Set as Top Priority") { dialog, _ ->
                val chosenSource = sources[selectedIndex]
                setTopPrioritySource(chosenSource, context)
                Toast.makeText(context, "Priority updated: $chosenSource is now primary", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNeutralButton("Configure Servers") { dialog, _ ->
                dialog.dismiss()
                showToggleServersDialog(context)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun showToggleServersDialog(context: Context) {
        val sources = REGISTERED_SOURCES.map { it.name }.toTypedArray()
        val disabled = getDisabledSources(context)
        val checkedItems = BooleanArray(sources.size) { i -> !disabled.contains(sources[i]) }

        AlertDialog.Builder(context)
            .setTitle("Enable / Disable Streaming Servers")
            .setMultiChoiceItems(sources, checkedItems) { _, which, isChecked ->
                toggleSource(sources[which], isChecked, context)
            }
            .setPositiveButton("Done") { dialog, _ ->
                Toast.makeText(context, "Server configuration saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
