package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import kotlin.math.*

internal data class TearDemo(
    var time: Double = 0.0,
    var paused: Boolean = false,
    var realDown: Boolean = false,
    var quiet: Double = 0.0,
    var segment: Double = 0.0,
    var startFront: Double = 0.0,
    var startX: Double = 0.0,
    var startY: Double = 0.0,
    var grabOffset: Double = 0.0,
    var lastX: Double = 0.0,
    var lastY: Double = 0.0,
    var remainder: Double = 0.0,
)
