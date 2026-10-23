// The full-screen AGSL pass: fixed dawn, moving pane, and the source's shared HERO lighting.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(33)
internal class HorizonScene(private val geometry: HorizonGeometry) {
    private val shader = RuntimeShader(horizonSceneAgsl)
    private val uniforms = HorizonShaderUniforms()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@HorizonScene.shader }
    init {
        val g = geometry
        uniforms.set(shader, "rest", g.cx, g.cy, g.cardWidth / 2, 120f)
        uniforms.set(shader, "viewport", g.width, g.height)
        uniforms.set(shader, "well", 0f, 67f, g.panelWidth / 2, 29f)
        uniforms.set(shader, "bead", g.cardWidth / 2 - 26, -94f, 16f)
        uniforms.set(shader, "planet", g.width / 2, g.apex + 560)
        uniforms.set(shader, "reach", g.reach)
        uniforms.set(shader, "scale", g.density)
        uniforms.set(shader, "opaque", 0f)
    }
    fun draw(canvas: Canvas, state: HorizonState) {
        val g = geometry
        val c = if (state.reduced) 0f else state.crest.value
        val inhale = HorizonMath.part(c, 0f, .12f)
        val flareAt = HorizonMath.part(c, .12f, .3f)
        val bell = if (flareAt > 0 && flareAt < 1) sin(PI.toFloat() * flareAt) else 0f
        val sweepAt = HorizonMath.part(c, .25f, .7f)
        val settled = state.done.value.coerceIn(0f, 1f) * if (state.reduced) 1f else HorizonMath.part(c, .7f, 1f)
        val diamondRise = HorizonMath.smooth(HorizonMath.part(flareAt, 0f, .5f))
        val dawnBreak = if (state.reduced) state.done.value.coerceIn(0f, 1f)
            else if ((c > 0 && c < 1) || state.done.value > 0)
                diamondRise * (1 + .5f * (1 - HorizonMath.part(c, .21f, 1f))) else 0f
        uniforms.set(shader, "slab", g.cx + state.shiftX.value, g.cy + state.shiftY.value - state.lift * 46, g.cardWidth / 2, 120f)
        uniforms.set(shader, "slabScale", state.scale)
        uniforms.set(shader, "progress", max(state.progress.coerceIn(0f, 1f), settled))
        uniforms.set(shader, "power", max(state.power.value, 0f) * (1 - .1f * inhale * (1 - flareAt)))
        uniforms.set(shader, "pulse", state.pulse.coerceIn(0f, 1f))
        uniforms.set(shader, "flare", bell * bell)
        uniforms.set(shader, "streak", 1 - (1 - flareAt).pow(3))
        uniforms.set(shader, "settled", settled)
        uniforms.set(shader, "dawnBreak", dawnBreak)
        uniforms.set(shader, "sweep", HorizonMath.smooth(sweepAt))
        uniforms.set(shader, "sweepStrength", HorizonMath.part(sweepAt, 0f, .12f) * (1 - HorizonMath.part(sweepAt, .84f, 1f)))
        uniforms.set(shader, "closePress", state.closePress.value)
        uniforms.set(shader, "shimmer", if (state.reduced) 0f else (state.now % 9000 / 9000).toFloat())
        canvas.drawRect(0f, 0f, g.width, g.height, paint)
    }
}
