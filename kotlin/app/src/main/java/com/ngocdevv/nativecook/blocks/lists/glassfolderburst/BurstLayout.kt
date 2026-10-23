package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

internal class BurstLayout(val width: Double, val height: Double, insetTop: Double, insetBottom: Double) {
    data class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    val headerTop = max(insetTop, 20.0) + 12.0
    val listTop = headerTop + 56.0
    val bottom = height - max(insetBottom, 16.0) - 24.0
    val dockTop = bottom - 104.0
    val n = floor((dockTop - 32.0 - listTop + 8.0) / 60.0).toInt().coerceIn(5, 9)
    val restY = round(height * 0.5)
    val dockY = bottom - 52.0
    val slots = List(n) { Box(24.0, listTop + 60.0 * it, width - 48.0, 52.0) }
}
