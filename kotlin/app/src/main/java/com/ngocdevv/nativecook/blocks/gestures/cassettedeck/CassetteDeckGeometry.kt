package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import kotlin.math.max

/**
 * Where everything sits for one window size, in dp ("points" of the source): the source's 402 x 874 pt
 * screen scaled by width / 402 and centred vertically when the window is taller (RN `CassetteDeck` :48-50).
 */
internal class CassetteDeckGeometry(val width: Double, val height: Double) {
    val s: Double = width / CassetteDeckTheme.SCREEN_W
    val oy: Double = max(0.0, (height - CassetteDeckTheme.SCREEN_H * s) / 2.0)

    /** Left of the first key's cap. */
    fun keyLeft(index: Int): Double = (CassetteDeckTheme.keys.x0 + index * CassetteDeckTheme.keys.pitch) * s

    val keyTop: Double get() = oy + CassetteDeckTheme.keys.y * s
    val keyWidth: Double get() = CassetteDeckTheme.keys.w * s
    val keyHeight: Double get() = CassetteDeckTheme.keys.h * s

    // The cassette's touch area spans the screen: 20 pt above and below the cassette (RN :196).
    val swipeTop: Double get() = oy + (CassetteDeckTheme.cassette.y - 20.0) * s
    val swipeHeight: Double get() = (CassetteDeckTheme.cassette.h + 40.0) * s

    /** Centre of a reel in the touch area's own coordinates (RN :140-143). */
    fun reelX(reel: Int): Double = (CassetteDeckTheme.cassette.x + CassetteDeckTheme.reels[reel].cx) * s
    fun reelY(reel: Int): Double = (20.0 + CassetteDeckTheme.reels[reel].cy) * s
}
