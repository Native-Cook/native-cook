package com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** Blur the recorded backdrop in the hinged flap's local plane. API 26–30 use its tint. */
@RequiresApi(31)
internal class GlassPlaylistFolderBackdrop {
    private val scene = RenderNode("GlassPlaylistFolder scene")
    private val blur = RenderNode("GlassPlaylistFolder frost").apply {
        setPosition(0, 0, 400, 400)
        setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP))
    }
    private val clip = Path().apply { addRoundRect(RectF(-134f,-38f,134f,102f),22f,22f,Path.Direction.CW) }
    fun begin(width: Int, height: Int): Canvas {
        scene.setPosition(0, 0, width, height)
        return scene.beginRecording(width, height)
    }
    fun finish(destination: Canvas, flap: GlassPlaylistFolderMatrix) {
        scene.endRecording()
        destination.drawRenderNode(scene)
        val inverse = Matrix()
        (flap * GlassPlaylistFolderMatrix.translate(-200.0,-200.0)).native().invert(inverse)
        val local = blur.beginRecording(400,400)
        local.concat(inverse);local.drawRenderNode(scene)
        blur.endRecording()
        destination.save();destination.concat(flap.native());destination.clipPath(clip)
        destination.translate(-200f,-200f);destination.drawRenderNode(blur);destination.restore()
    }
}
