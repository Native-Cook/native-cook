package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingTheme as Theme
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.round

/** The page changes' one pass (API 33+): the UI atlas and the glyph field as children, RN `uniforms` per frame. */
@RequiresApi(33)
internal class SolidMorphOnboardingShaderPass(atlas: Bitmap, glyphs: Bitmap, private val l: Theme.Layout, private val density: Float) {
    private val shader = RuntimeShader(SolidMorphOnboardingSource)
    private val paint = Paint()
    private val ink = Theme.rgba(Theme.ink)
    private val paper = Theme.rgba(Theme.paper)
    private val clay = Theme.rgba(Theme.clay)
    private val clayShade = Theme.rgba(Theme.clayShade)
    private val glaze = Theme.rgba(Theme.glaze)

    init {
        // the atlas holds the pages stacked at `density` px per dp: its pixels map to dp by 1 / density
        val a = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        a.filterMode = BitmapShader.FILTER_MODE_LINEAR
        val m = Matrix()
        val k = (l.w / atlas.width).toFloat()
        m.setScale(k, k)
        a.setLocalMatrix(m)
        shader.setInputShader("pages", a)
        val g = BitmapShader(glyphs, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        g.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("glyphs", g)
    }

    fun draw(c: Canvas, e: SolidMorphOnboardingEngine, pages: List<SolidMorphOnboardingPage>, spin: Double, conserve: Boolean, secondaryHidden: Boolean) {
        val count = pages.size
        val st = M.pageMix(e.p.value, count)
        val a = pages[st.from].solid
        val b = pages[st.to].solid
        val t = e.clock
        val reduced = e.reduced
        val pv = e.p.value
        val edge = M.clamp((pv - M.clamp(pv, 0.0, (count - 1).toDouble())) * 4, -1.0, 1.0)
        val turn = (st.from + M.ease(st.t)) * spin + (edge * M.END_SPIN) / 360
        val precess = if (reduced || !e.hover) 0.0 else M.hoverAt(t)
        val playing = e.playing
        fun base(s: SolidMorphOnboardingSolid) = when (s.base) { "match" -> 2f; "disc" -> 1f; else -> 0f }
        shader.setFloatUniform("size", l.w.toFloat(), l.h.toFloat())
        shader.setFloatUniform("centre", l.heroX.toFloat(), l.heroY.toFloat())
        shader.setFloatUniform("radius", l.heroRadius.toFloat())
        shader.setFloatUniform("progress", pv.toFloat())
        shader.setFloatUniform("count", count.toFloat())
        shader.setFloatUniform("sides", a.sides.toFloat(), b.sides.toFloat())
        shader.setFloatUniform("rounds", a.roundness.toFloat(), b.roundness.toFloat())
        shader.setFloatUniform("splits", if (a.split) 1f else 0f, if (b.split) 1f else 0f)
        shader.setFloatUniform("bases", base(a), base(b))
        shader.setFloatUniform("themes", if (pages[st.from].dark) 0f else 1f, if (pages[st.to].dark) 0f else 1f)
        shader.setFloatUniform("spin", (if (reduced) (st.from + round(st.t)) * spin else turn).toFloat())
        shader.setFloatUniform("speed", (if (reduced) 0.0 else M.spinRate(pv, e.rate.value, spin)).toFloat())
        shader.setFloatUniform("hover", precess.toFloat())
        shader.setFloatUniform("bob", (if (reduced) 0.0 else (if (playing) M.bobAt(t) else 0.0) + e.lift.value).toFloat())
        shader.setFloatUniform("baseTilt", (if (reduced) 0.0 else (if (playing) M.baseAt(t) else 0.0) + e.base.value).toFloat())
        shader.setFloatUniform("press", (if (reduced) 0.0 else e.cushion.value).toFloat())
        if (reduced) {
            shader.setFloatUniform("lean", 0f, 0f)
        } else {
            shader.setFloatUniform("lean", (((e.tiltX.value + precess * 4 + edge * M.END_TILT) * PI) / 180).toFloat(), ((e.tiltY.value * PI) / 180).toFloat())
        }
        shader.setFloatUniform("ink", ink)
        shader.setFloatUniform("paper", paper)
        shader.setFloatUniform("clay", clay)
        shader.setFloatUniform("clayShade", clayShade)
        shader.setFloatUniform("glaze", glaze)
        shader.setFloatUniform("render", if (conserve) 1f else 0f, 0f, 0f, (1.0 / max(1.0, l.heroRadius * density)).toFloat())
        shader.setFloatUniform("footerStack", if (l.largeText) 1f else 0f)
        shader.setFloatUniform("buttonPress", e.primaryPress.value.toFloat(), (if (secondaryHidden) -1.0 else e.secondaryPress.value).toFloat())
        shader.setFloatUniform("controls", l.progressY.toFloat(), l.footerY.toFloat(), l.buttonHeight.toFloat(), l.s.toFloat())
        shader.setFloatUniform("copyBand", (l.chipY - 12 * l.s).toFloat(), (l.bodyY + l.bodyHeight + 8 * l.s).toFloat())
        shader.setFloatUniform("arrival", (if (playing) M.arrivalAt(t) else 0.0).toFloat())
        shader.setFloatUniform("reduced", if (reduced) 1f else 0f)
        shader.setFloatUniform("reaction", e.contactPulse.value.toFloat(), (if (playing) M.basePulseAt(t) else e.basePulse.value).toFloat())
        paint.shader = shader
        c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), paint)
    }
}
