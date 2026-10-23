package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** Visual state passed to a preset icon renderer. */
@Immutable
public data class DialPresetIconState(
    val selected: Boolean,
    val adjusted: Boolean,
    val value: Double,
)

/** A preset (adjustment tool) rendered by [DialSlider]. */
@Immutable
public class DialPreset(
    /** Stable, non-empty identifier used as the key in the values map. */
    public val id: String,
    /** TalkBack label. */
    public val label: String,
    /** Renderer for the selected and adjusted visual states. */
    public val icon: @Composable (state: DialPresetIconState) -> Unit,
    public val minValue: Double? = null,
    public val maxValue: Double? = null,
    public val initialValue: Double? = null,
    /** Accessibility increment/decrement amount (default: 1). */
    public val step: Double? = null,
    public val formatValue: ((value: Double) -> String)? = null,
    public val disabled: Boolean = false,
) {
    public fun copy(
        id: String = this.id,
        label: String = this.label,
        icon: @Composable (state: DialPresetIconState) -> Unit = this.icon,
        minValue: Double? = this.minValue,
        maxValue: Double? = this.maxValue,
        initialValue: Double? = this.initialValue,
        step: Double? = this.step,
        formatValue: ((value: Double) -> String)? = this.formatValue,
        disabled: Boolean = this.disabled,
    ): DialPreset = DialPreset(
        id = id,
        label = label,
        icon = icon,
        minValue = minValue,
        maxValue = maxValue,
        initialValue = initialValue,
        step = step,
        formatValue = formatValue,
        disabled = disabled,
    )

    override fun toString(): String =
        "DialPreset(id=$id, label=$label, minValue=$minValue, maxValue=$maxValue, " +
            "initialValue=$initialValue, step=$step, disabled=$disabled)"
}

/** Immutable map of preset id to its current value. */
public typealias DialSliderValues = Map<String, Double>
