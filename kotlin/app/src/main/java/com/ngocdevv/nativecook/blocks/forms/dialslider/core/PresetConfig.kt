package com.ngocdevv.nativecook.blocks.forms.dialslider.core

import com.ngocdevv.nativecook.blocks.forms.dialslider.dial.DialConfig
import com.ngocdevv.nativecook.blocks.forms.dialslider.dial.DialPreset

public fun getPresetRange(preset: DialPreset): DialRange =
    normalizeDialRange(
        preset.minValue ?: DialConfig.MIN_VALUE,
        preset.maxValue ?: DialConfig.MAX_VALUE,
        DialConfig.MIN_VALUE,
        DialConfig.MAX_VALUE,
    )

public fun getPresetInitialValue(preset: DialPreset): Double {
    val (min, max) = getPresetRange(preset)
    val initial = preset.initialValue
    val requestedInitial = if (initial != null && initial.isFinite()) initial else 0.0
    return clampDialValue(requestedInitial, min, max)
}

public fun createInitialPresetValues(presets: List<DialPreset>): Map<String, Double> =
    presets.associate { preset -> preset.id to getPresetInitialValue(preset) }

/**
 * Creates a complete, clamped map from a partial controlled/default map.
 * Missing and non-finite entries fall back to each preset's initial value.
 */
public fun resolvePresetValues(
    presets: List<DialPreset>,
    requestedValues: Map<String, Double>? = null,
): Map<String, Double> =
    presets.associate { preset ->
        val (min, max) = getPresetRange(preset)
        val requested = requestedValues?.get(preset.id)
        val value = if (requested != null && requested.isFinite()) {
            clampDialValue(requested, min, max)
        } else {
            getPresetInitialValue(preset)
        }
        preset.id to value
    }
