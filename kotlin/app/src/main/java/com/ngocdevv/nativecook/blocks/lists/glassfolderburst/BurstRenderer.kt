package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import android.annotation.TargetApi
import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Records the backdrop once, then refracts that same live scene through the front outline. */
internal class BurstRenderer(context: Context, private val layout: BurstLayout, private val density: Float) {
    private val rows = BurstRowPainter(context, layout)
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu(layout, density, rows) else null
    private val fallback = if (gpu == null) BurstStillRenderer(layout, rows) else null

    fun draw(canvas: Canvas, scene: BurstScene) {
        canvas.drawColor(Color.BLACK)
        if (scene.fade < .999) canvas.saveLayerAlpha(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), (scene.fade * 255).toInt())
        if (gpu != null && canvas.isHardwareAccelerated) gpu.draw(canvas, scene)
        else {
            canvas.save()
            canvas.scale(density, density)
            fallback?.draw(canvas, scene)
            canvas.restore()
        }
        if (scene.fade < .999) canvas.restore()
    }
    fun close() {
        gpu?.close()
        rows.close()
    }

    @TargetApi(33)
    private class Gpu(private val layout: BurstLayout, private val density: Float, private val rows: BurstRowPainter) {
        private val background = RuntimeShader(GlassFolderBurstShaders.background)
        private val back = RuntimeShader(GlassFolderBurstShaders.back)
        private val orb = RuntimeShader(GlassFolderBurstShaders.orb)
        private val glass = RuntimeShader(GlassFolderBurstShaders.glass)
        private val sceneNode = RenderNode("GlassFolderBurst backdrop")
        private val glassNode = RenderNode("GlassFolderBurst refracted front")
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val physicalPath = Path()
        private val pixelMatrix = Matrix().apply { setScale(density, density) }
        private val width = ceil(layout.width * density).toInt()
        private val height = ceil(layout.height * density).toInt()

        init {
            sceneNode.setPosition(0, 0, width, height)
            glassNode.setPosition(0, 0, width, height)
        }
        private fun uniform(shader: RuntimeShader, name: String, vararg values: Double) {
            shader.setFloatUniform(name, values.map { it.toFloat() }.toFloatArray())
        }
        fun draw(canvas: Canvas, s: BurstScene) {
            val f = BurstPose.folder(s, layout)
            val o = BurstPose.orb(s, layout)
            val clock = if (s.reduced) 0.0 else s.idleTime
            uniform(background, "size", layout.width, layout.height)
            uniform(background, "center", f.x, f.y)
            uniform(background, "scale", f.sy)
            uniform(background, "flow", f.flow)
            uniform(background, "time", clock)
            uniform(background, "bloomCenter", if (s.sim.bloomX == 0.0) layout.width / 2 else s.sim.bloomX,
                if (s.sim.bloomY == 0.0) layout.dockY - 180 else s.sim.bloomY)
            uniform(background, "bloom", max(s.sim.bloomStrength, if (s.expanded) .12 else 0.0))
            uniform(background, "caustic", o.x.coerceIn(f.x - 50 * f.sx, f.x + 50 * f.sx), BurstGeometry.storedLight(f, o))
            uniform(back, "center", f.x, f.y)
            uniform(back, "stretch", f.sx, f.sy)
            uniform(back, "opened", f.open)
            uniform(back, "flow", f.flow)
            uniform(orb, "center", o.x, o.y)
            uniform(orb, "radius", o.r)
            uniform(orb, "glow", o.glow)
            uniform(orb, "beat", o.beat)
            uniform(orb, "mark", o.mark)
            uniform(orb, "time", clock)
            val recorded = sceneNode.beginRecording(width, height)
            recorded.scale(density, density)
            paint.shader = background
            recorded.drawRect(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), paint)
            paint.shader = back
            val half = 66 + max(0.0, f.open) * 6
            recorded.drawRect((f.x - half * f.sx).toFloat(), (f.y - 52 * f.sy).toFloat(),
                (f.x + half * f.sx).toFloat(), (f.y + 52 * f.sy).toFloat(), paint)
            paint.shader = orb
            recorded.drawRect((o.x - o.r * 2.2).toFloat(), (o.y - o.r * 2.2).toFloat(),
                (o.x + o.r * 2.2).toFloat(), (o.y + o.r * 2.2).toFloat(), paint)
            rows.rows(recorded, s, f)
            sceneNode.endRecording()

            uniform(glass, "center", f.x, f.y)
            uniform(glass, "stretch", f.sx, f.sy)
            uniform(glass, "opened", f.open)
            uniform(glass, "pressed", f.press)
            uniform(glass, "flow", f.flow)
            uniform(glass, "time", clock)
            uniform(glass, "pd", density.toDouble())
            uniform(glass, "orb", o.x, o.y, o.r, o.glow)
            uniform(glass, "orbBeat", o.beat)
            uniform(glass, "orbMark", o.mark)
            glassNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(glass, "image"))
            val refracted = glassNode.beginRecording(width, height)
            refracted.drawRenderNode(sceneNode)
            glassNode.endRecording()
            canvas.drawRenderNode(sceneNode)
            BurstGeometry.front(f, path)
            path.transform(pixelMatrix, physicalPath)
            canvas.save()
            canvas.clipPath(physicalPath)
            canvas.drawRenderNode(glassNode)
            canvas.restore()
            canvas.save()
            canvas.scale(density, density)
            rows.header(canvas, s)
            canvas.restore()
        }
        fun close() {
            sceneNode.discardDisplayList()
            glassNode.discardDisplayList()
        }
    }
}
