package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

internal data class GreatWaveMorningFrame(
    val amb: Double,
    val scroll: Double,
    val foam: Double = -1.0,
    val foamPow: Double = 1.0,
    val done: List<Double> = List(5) { 0.0 },
    val tab: Double = 0.0,
    val pressRow: Int = -1,
    val pressK: Double = 0.0,
    val tiltX: Double = 0.0,
    val tiltY: Double = 0.0,
)
