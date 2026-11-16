package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.ceil

/**
 * A Gaussian blur of what `content` draws (RN's `Blur` layer and `BackdropFilter`), through a render node with a blur render effect
 * (API 31+). The node is recorded at device resolution and drawn back at the same place, so the blur's sigma is in device pixels
 * (sigma dp x density). Edges clamp: callers record a rectangle wider than what they show.
 */
@RequiresApi(31)
internal class PrintLineScrubBlur {
    private val node = RenderNode("PrintLineScrubBlur")
    private var sigma = -1f

    /** Draws the blurred `content` (called with a dp canvas in the same coordinates as `c`) at the dp rect. Returns false if the canvas cannot draw render nodes. */
    fun draw(c: Canvas, density: Float, left: Float, top: Float, right: Float, bottom: Float, sigmaDp: Float, content: (Canvas) -> Unit): Boolean {
        if (!c.isHardwareAccelerated) return false
        val w = ceil((right - left) * density).toInt().coerceAtLeast(1)
        val h = ceil((bottom - top) * density).toInt().coerceAtLeast(1)
        node.setPosition(0, 0, w, h)
        val rc = node.beginRecording(w, h)
        try {
            rc.scale(density, density)
            rc.translate(-left, -top)
            content(rc)
        } finally {
            node.endRecording()
        }
        val sp = (sigmaDp * density).coerceAtLeast(0.1f)
        if (sp != sigma) {
            sigma = sp
            node.setRenderEffect(RenderEffect.createBlurEffect(sp, sp, Shader.TileMode.CLAMP))
        }
        c.save()
        c.translate(left, top)
        c.scale(1f / density, 1f / density)
        c.drawRenderNode(node)
        c.restore()
        return true
    }
}
