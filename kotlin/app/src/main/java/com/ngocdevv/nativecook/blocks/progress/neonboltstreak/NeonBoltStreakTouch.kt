package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

internal data class NeonBoltStreakTouch(
    var started: Double,
    var day: Int,
    val countGesture: Boolean,
    val startX: Float,
    val startY: Float,
    var charge: Float = 0f,
    var cancelled: Boolean = false,
    var mode: Mode = Mode.Pressing,
    var lastDay: Int = day,
    var beganCharging: Boolean = false,
    val flickers: MutableList<Pair<Int, Double>> = mutableListOf(),
) {
    enum class Mode { Pressing, Igniting, Flickering, Celebrating }
}
