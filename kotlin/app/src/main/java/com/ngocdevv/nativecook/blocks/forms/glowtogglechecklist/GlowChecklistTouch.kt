package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

/** Control -2 is empty, -1 is the cap; dx and velocity are in logical screen points. */
internal data class GlowChecklistTouch(
    val control: Int,
    val down: Boolean,
    val dx: Double = 0.0,
    val velocity: Double = 0.0,
    val k: Double = 1.0,
    val cancel: Boolean = false,
    val activate: Boolean = false,
)
