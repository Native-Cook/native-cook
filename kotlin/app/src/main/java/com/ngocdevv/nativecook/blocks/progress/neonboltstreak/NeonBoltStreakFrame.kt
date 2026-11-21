package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

internal data class NeonBoltStreakFrame(
    val time: Double = 0.0,
    val camera: Float = 0f,
    val lit: FloatArray = FloatArray(7),
    val fill: FloatArray = FloatArray(7),
    val head: FloatArray = FloatArray(7),
    val count: Float = 3f,
    val flood: Float = 0f,
    val flash: Float = 0f,
    val ghost: Float = .07f,
    val border: Float = 0f,
    val borderLit: Boolean = false,
    val strikeDay: Int = -1,
    val strikeDrop: Float = 0f,
    val charge: Float = 0f,
    val jolt: Float = 0f,
)
