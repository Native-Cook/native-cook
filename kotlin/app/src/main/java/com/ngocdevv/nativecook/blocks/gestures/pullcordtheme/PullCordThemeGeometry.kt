package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Cord
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.ReplicaArt

/** Where everything sits on a screen of [w] x [h] dp; [s] is the scale from the 402 dp stage. */
internal class PullCordThemeGeometry(val w: Double, val h: Double) {
    val s = w / PullCordThemeTheme.STAGE_WIDTH

    /** The bulb sits exactly where the accepted replica put it (the source framed by ref.py fit). */
    private val k = PullCordThemeTheme.FIT_K * s
    val socketTop = ReplicaArt.socketTopSrc * k
    val pivotX = ReplicaArt.centreSrc * k + PullCordThemeTheme.FIT_CROP_X * s
    val pivotY = socketTop - ReplicaArt.wire * s
    val anchorX = w * Cord.xRatio
    val anchorY = Cord.top * s
    val restY = h * Cord.restYRatio
    val length = restY - anchorY
    val seg = length / (Cord.points - 1)

    /** Pivot to the glass centre. */
    val glassArm = socketTop - pivotY + ReplicaArt.glassCenterSrc * PullCordThemeTheme.FIT_K * s
}
