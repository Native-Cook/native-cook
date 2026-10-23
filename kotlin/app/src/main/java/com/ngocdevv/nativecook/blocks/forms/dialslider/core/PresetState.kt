package com.ngocdevv.nativecook.blocks.forms.dialslider.core

import kotlin.math.max
import kotlin.math.min

/** Minimal contract for preset identity used by the state helpers. */
public interface PresetIdentity {
    public val id: String
}

/** Preset identity plus the disabled flag used when choosing a fallback selection. */
public interface SelectablePreset : PresetIdentity {
    public val disabled: Boolean
}

/**
 * A value callback may cross the gesture→state boundary after the user has
 * selected another preset. Only callbacks tagged with the currently active
 * revision may mutate the preset value map.
 */
public fun isCurrentPresetRevision(callbackRevision: Int, currentRevision: Int): Boolean =
    callbackRevision == currentRevision

public data class PresetValueUpdateInput(
    val callbackRevision: Int,
    val currentRevision: Int,
    val presetId: String,
    val nextValue: Double,
    val values: Map<String, Double>,
)

/** Returns null for stale callbacks without mutating the input map. */
public fun updatePresetValueForRevision(input: PresetValueUpdateInput): Map<String, Double>? {
    if (!isCurrentPresetRevision(input.callbackRevision, input.currentRevision)) {
        return null
    }
    if (input.values[input.presetId] == input.nextValue) return input.values
    return input.values + (input.presetId to input.nextValue)
}

public data class PresetValueChange(
    val presetId: String,
    val value: Double,
    val values: Map<String, Double>,
    val changed: Boolean,
)

/** Builds the exact payload emitted by onValueChange and onValuesChange. */
public fun createPresetValueChange(input: PresetValueUpdateInput): PresetValueChange? {
    val nextValues = updatePresetValueForRevision(input) ?: return null
    return PresetValueChange(
        presetId = input.presetId,
        value = input.nextValue,
        values = nextValues,
        changed = nextValues !== input.values,
    )
}

/** Keeps the first occurrence so keys and value maps remain deterministic. */
public fun <T> dedupePresetsById(presets: List<T>, id: (T) -> String): List<T> {
    val seen = HashSet<String>()
    return presets.filter { preset ->
        val presetId = id(preset)
        if (presetId.isBlank()) return@filter false
        seen.add(presetId)
    }
}

/** Keeps the first occurrence so keys and value maps remain deterministic. */
public fun <T : PresetIdentity> dedupePresetsById(presets: List<T>): List<T> =
    dedupePresetsById(presets) { it.id }

/** Chooses a usable preset deterministically for invalid or disabled IDs. */
public fun <T> resolveSelectedPresetId(
    presets: List<T>,
    preferredId: String?,
    id: (T) -> String,
    disabled: (T) -> Boolean,
): String =
    presets.firstOrNull { id(it) == preferredId && !disabled(it) }?.let(id)
        ?: presets.firstOrNull { !disabled(it) }?.let(id)
        ?: presets.firstOrNull()?.let(id)
        ?: ""

/** Chooses a usable preset deterministically for invalid or disabled IDs. */
public fun <T : SelectablePreset> resolveSelectedPresetId(presets: List<T>, preferredId: String?): String =
    resolveSelectedPresetId(presets, preferredId, { it.id }, { it.disabled })

public data class PresetValueConfig(
    val id: String,
    val min: Double,
    val max: Double,
    val initialValue: Double,
)

public data class ReconciledPresetValues(
    val values: Map<String, Double>,
    val changed: Boolean,
)

public fun reconcilePresetValues(
    presets: List<PresetValueConfig>,
    currentValues: Map<String, Double>,
): ReconciledPresetValues {
    val values = LinkedHashMap<String, Double>()
    for (preset in presets) {
        val current = currentValues[preset.id] ?: preset.initialValue
        values[preset.id] = min(preset.max, max(preset.min, current))
    }

    val changed = currentValues.size != values.size ||
        values.any { (key, value) -> currentValues[key] != value }
    return ReconciledPresetValues(values = values, changed = changed)
}
