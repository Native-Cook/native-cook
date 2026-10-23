package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

/** All continuous values are doubles, matching the source's deterministic numerical state. */
internal class GlowChecklistState(val autoPlay: Boolean, val still: Boolean) {
    class Knob {
        var on = false
        var lead = 0.0
        var trail = 0.0
        var leadV = 0.0
        var trailV = 0.0
        var hold = 0.0
        var linearFrom = 0.0
        var linearAge = 0.12
    }

    class Events {
        var changed = false
        var cap = false
        var onMask = 0
        var offMask = 0
        var success = false
        var reset = false
        var paused = false
        var resumed = false
    }

    val knobs = Array(4) { Knob() }
    var events = Events()
    var clock = 0.0
    var present = 0.0
    val letters = DoubleArray(7)
    var open = 0.0
    var openTarget = 0.0
    var openFrom = 0.0
    var openAge = 1.0
    var press = 0.0
    var sum = 0.0
    var fill = 0.0
    var fillV = 0.0
    var hue = 0.0
    var hueV = 0.0
    var tip = 0.0
    var tipV = 0.0
    var surge = 0.0
    var surgeAge = -1.0
    var flash = 0.0
    var glare = -0.3
    var glareAge = -1.0
    var glareDelay = -1.0
    var surgeDelay = -1.0
    var introAge = -1.0
    var outroAge = -1.0
    var opacity = if (still) 0.0 else 1.0
    var fadeAge = -1.0
    var fadeFrom = 0.0
    var fadeKind = 0
    var fadeTarget = 0.0
    var started = false
    var loopTime = 9.25
    var loopTick = 2220
    var paused = false
    var idle = 0.0
    var remainder = 0.0
    var finger = -2
    var fingerReal = false
    var realDown = false
    var start = 0.0
    var moved = 0.0
    var drag = false
    var lastDx = 0.0
    var fingerVelocity = 0.0
}
