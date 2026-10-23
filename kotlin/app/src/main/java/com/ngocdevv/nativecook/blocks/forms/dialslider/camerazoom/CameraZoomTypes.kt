package com.ngocdevv.nativecook.blocks.forms.dialslider.camerazoom

import androidx.compose.runtime.Immutable

/** Minimal contract shared by anything that carries a zoom factor. */
public interface CameraZoomStopValue {
    public val value: Double

    /** Returns a copy with the zoom factor replaced (used when snapping to a step). */
    public fun withValue(value: Double): CameraZoomStopValue
}

/** An optical or quick-zoom stop shown in the compact control and on the wheel. */
@Immutable
public data class CameraZoomStop(
    /** Positive zoom factor; normalized to the nearest configured step. */
    override val value: Double,
    /** Circular wheel label. Defaults to the numeric zoom factor without `x`. */
    val label: String? = null,
    /** Compact inactive label. Defaults to `label`, or camera-style numeric text. */
    val compactLabel: String? = null,
    /** Optional equivalent focal length, for example `13MM` or `26MM`. */
    val focalLength: String? = null,
) : CameraZoomStopValue {
    override fun withValue(value: Double): CameraZoomStop = copy(value = value)
}
