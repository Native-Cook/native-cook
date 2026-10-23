package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Canvas
import android.os.Build

internal class PitStopReviewSmokePainter {
    private val api33 = if (Build.VERSION.SDK_INT >= 33) PitStopReviewApi33SmokePainter() else null

    fun draw(canvas: Canvas, puffs: PitStopReviewMotion.PackedPuffs, unit: Float): Boolean {
        if (Build.VERSION.SDK_INT < 33 || api33 == null) return false
        canvas.save()
        canvas.scale(unit, unit)
        api33.draw(canvas, puffs)
        canvas.restore()
        return true
    }
}
