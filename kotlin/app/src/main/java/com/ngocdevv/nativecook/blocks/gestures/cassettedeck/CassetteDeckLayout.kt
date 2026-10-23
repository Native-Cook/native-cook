package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import kotlin.math.max
import kotlin.math.min

/** Positions the 402 x 778 reference stage inside the safe drawing area. */
internal class CassetteDeckLayout(
    val width: Double,
    val height: Double,
    topInset: Double,
    bottomInset: Double,
) {
    private val room = height - topInset - max(bottomInset, 16.0)
    val scale = min(width / CassetteDeckTheme.STAGE_WIDTH, room / CassetteDeckTheme.STAGE_HEIGHT)
    val originX = (width - CassetteDeckTheme.STAGE_WIDTH * scale) / 2.0
    val originY = topInset + (room - CassetteDeckTheme.STAGE_HEIGHT * scale) / 2.0

    fun stageX(screenX: Double): Double = (screenX - originX) / scale

    fun stageY(screenY: Double): Double = (screenY - originY) / scale
}
