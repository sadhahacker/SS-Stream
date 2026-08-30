package com.streamcore

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast

object StreamSettingsManager {
    private const val PREFS_NAME = "streamcore_provider_prefs"
    private const val KEY_TOP_SOURCE = "key_top_priority_source"
    private const val KEY_DISABLED_SOURCES = "key_disabled_sources"

    private var sharedPrefs: SharedPreferences? = null

    /**
     * Initializes the settings manager with the plugin context.
     */
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

    /**
     * Returns the name of the user's preferred top-priority source.
     * Defaults to "VidCore".
     */
    fun getTopPrioritySource(context: Context? = null): String {
        return getPrefs(context)?.getString(KEY_TOP_SOURCE, "VidCore") ?: "VidCore"
    }

    /**
     * Sets the user's preferred top-priority source.
     */
    fun setTopPrioritySource(sourceName: String, context: Context? = null) {
        getPrefs(context)?.edit()?.putString(KEY_TOP_SOURCE, sourceName)?.apply()
    }

    /**
     * Returns the set of disabled source names.
     */
    fun getDisabledSources(context: Context? = null): Set<String> {
        return getPrefs(context)?.getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()
    }

    /**
     * Toggles a source between enabled and disabled.
     */
    fun toggleSource(sourceName: String, isEnabled: Boolean, context: Context? = null) {
        val prefs = getPrefs(context) ?: return
        val currentDisabled = prefs.getStringSet(KEY_DISABLED_SOURCES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isEnabled) {
            currentDisabled.remove(sourceName)
        } else {
            currentDisabled.add(sourceName)
        }
        prefs.edit().putStringSet(KEY_DISABLED_SOURCES, currentDisabled).apply()
    }

    /**
     * Returns all registered sources sorted by runtime priority:
     * 1. The user's chosen top-priority source gets priority 10000.
     * 2. Other sources retain their base priority.
     * 3. Disabled sources are filtered out.
     */
    fun getRuntimeSortedSources(context: Context? = null): List<StreamingSource> {
        val topSource = getTopPrioritySource(context)
        val disabled = getDisabledSources(context)

        return REGISTERED_SOURCES
            .filter { !disabled.contains(it.name) && it.enabled }
            .sortedByDescending { source ->
                if (source.name.equals(topSource, ignoreCase = true)) {
                    10000 // Bump chosen source to the absolute top
                } else {
                    source.priority
                }
            }
    }

    /**
     * Opens the in-app Settings dialog when the user taps Settings
     * in Cloudstream (Settings > Extensions > StreamCore > Settings).
     */
    fun showSettingsDialog(context: Context) {
        init(context)

        val sources = REGISTERED_SOURCES.map { it.name }.toTypedArray()
        val currentTop = getTopPrioritySource(context)
        var selectedIndex = sources.indexOf(currentTop).takeIf { it >= 0 } ?: 0

        val builder = AlertDialog.Builder(context)
        builder.setTitle("StreamCore - Select Primary Source")

        builder.setSingleChoiceItems(sources, selectedIndex) { _, which ->
            selectedIndex = which
        }

        builder.setPositiveButton("Set as Top Priority") { dialog, _ ->
            val chosenSource = sources[selectedIndex]
            setTopPrioritySource(chosenSource, context)
            Toast.makeText(
                context,
                "Priority updated: $chosenSource is now primary (#1)",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setNeutralButton("Configure Servers") { dialog, _ ->
            dialog.dismiss()
            showToggleServersDialog(context)
        }

        builder.create().show()
    }

    /**
     * Secondary dialog allowing users to toggle individual servers ON or OFF.
     */
    private fun showToggleServersDialog(context: Context) {
        val sources = REGISTERED_SOURCES.map { it.name }.toTypedArray()
        val disabled = getDisabledSources(context)
        val checkedItems = BooleanArray(sources.size) { i ->
            !disabled.contains(sources[i])
        }

        AlertDialog.Builder(context)
            .setTitle("Enable / Disable Streaming Servers")
            .setMultiChoiceItems(sources, checkedItems) { _, which, isChecked ->
                val sourceName = sources[which]
                toggleSource(sourceName, isChecked, context)
            }
            .setPositiveButton("Done") { dialog, _ ->
                Toast.makeText(context, "Server configuration saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
