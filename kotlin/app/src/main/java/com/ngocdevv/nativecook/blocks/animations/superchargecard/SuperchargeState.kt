package com.ngocdevv.nativecook.blocks.animations.superchargecard

internal class SuperchargeState(val initialSeed: Long = 0x51cafe) {
    enum class Phase {
        Rest,
        Charging,
        Draining,
        Primed,
        Strike,
        Charged,
        Discharging,
    }

    var phase = Phase.Rest
    var clock = 0.0
    var phaseTime = 0.0
    var accumulator = 0.0
    var ticks = 0L
    var seed = initialSeed
    var events = 0
    var silent = false
    var down = false
    var real = false
    var automated = false
    var script = 0.0
    var scriptPaused = false
    var resumeAt = 0.0
    var downAt = 0.0
    var downX = 0.0
    var downY = 0.0
    var travel = 0.0
    var energy = 0.0
    var glow = 0.0
    var glyph = M.ember.base
    var glyphScale = 1.0
    var ring = 0.0
    var ringLit = 0.0
    var dim = 1.0
    var flash = 0.0
    var tree = 0.0
    var treeGlow = 0.0
    var pulse = -1.0
    var shock = 0.0
    var meter = 0.0
    var charged = 0.0
    var caption = 0
    var card = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
    var sink = 0.0
    var shaft = doubleArrayOf(240.0, 0.0)
    val touch = doubleArrayOf(201.0, 392.0, 0.0)
    var sinkV = 0.0
    var scale = 1.0
    var scaleV = 0.0
    var drop = 0.0
    var dropV = 0.0
    var rotation = 0.0
    var rotationV = 0.0
    var chargeTime = 0.0
    var tickTime = 0.0
    var drainFrom = 1.0
    var glowFrom = 1.0
    var treeFrom = .3
    var ringFrom = 1.0
    var typeFrom = 1.0
    var geometry: SuperchargeLightning.Geometry? = null
    var lightning = SuperchargeLightning.Frame()
    var filament = SuperchargeLightning.Frame()
    var filamentAt = -1.0
    var filamentX = 0.0
    var filamentY = 0.0
    var filamentEnergy = 0.0
    var arc = SuperchargeLightning.Frame()
    var arcAt = 0.0
    var arcUntil = 0.0
    var coronaLines = mutableListOf<Double>()
    var coronaAt = -1.0
    var coronaUntil = 0.0
    var shakeAt = -1.0
    var shakeX = 0.0
    var shakeY = 0.0
    var shakeR = 0.0
    var particles = mutableListOf<Double>()
    var sparks = mutableListOf<Double>()
}
