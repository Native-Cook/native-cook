@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Canvas

/** Three passes preserve glass → surface → native copy → airborne ordering. */
internal class EmberRenderer(density: Float) {
    private val glass = EmberGlass(density)
    private val surface = EmberSurface()
    private val bar = EmberBar(density)
    private val heart = EmberHeart()
    private val effects = EmberEffects()

    fun glass(canvas: Canvas, motion: EmberMotion) {
        glass.draw(canvas, motion)
    }

    fun surface(canvas: Canvas, motion: EmberMotion) {
        surface.draw(canvas)
        bar.draw(canvas, motion)
        heart.draw(canvas, motion)
    }

    fun effects(canvas: Canvas, motion: EmberMotion) {
        effects.draw(canvas, motion)
    }
}
