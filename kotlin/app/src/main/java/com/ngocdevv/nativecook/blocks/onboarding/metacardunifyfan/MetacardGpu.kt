package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

import android.graphics.*
import kotlin.math.*

@android.annotation.TargetApi(33)
internal class MetacardGpu(faces: Array<Bitmap>, shadow: Bitmap, chip: Bitmap) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val page = RuntimeShader(MetacardShaders.PAGE)
    private val fan =
        Array(9) {
            RuntimeShader(MetacardShaders.FAN).apply {
                setInputShader("face", bitmapShader(faces[it % 8]))
            }
        }
    private val shadows =
        Array(9) {
            RuntimeShader(MetacardShaders.FAN_SHADOW).apply {
                setInputShader("sprite", bitmapShader(shadow))
            }
        }
    private val dark = RuntimeShader(MetacardShaders.DARKNESS)
    private val bloom = RuntimeShader(MetacardShaders.BLOOM)
    private val hero =
        RuntimeShader(MetacardShaders.HERO).apply { setInputShader("chip", bitmapShader(chip)) }
    private val heroShadow = RuntimeShader(MetacardShaders.HERO_SHADOW)
    private val inner = RuntimeShader(MetacardShaders.INNER_LIGHT)
    private val halo = RuntimeShader(MetacardShaders.HALO)
    private val bounce = RuntimeShader(MetacardShaders.BOUNCE)
    private val uniformNames = HashMap<RuntimeShader, Set<String>>()

    private fun bitmapShader(bitmap: Bitmap) =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }

    private fun uniforms(shader: RuntimeShader, values: Map<String, FloatArray>, source: String) {
        val names =
            uniformNames.getOrPut(shader) {
                Regex("uniform\\s+\\w+\\s+(\\w+)").findAll(source).map { it.groupValues[1] }.toSet()
            }
        for ((name, value) in values) if (name in names) shader.setFloatUniform(name, value)
    }

    private fun rect(
        canvas: Canvas,
        shader: RuntimeShader,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        paint.shader = shader
        canvas.drawRect(left, top, right, bottom, paint)
    }

    // Page grain is sampled in physical pixels; object materials use design coordinates.
    fun drawPage(
        canvas: Canvas,
        m: MetacardMotion,
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        scale: Float,
    ) {
        val mix =
            if (m.reduced) m.u else MetacardTimeline.clamp(MetacardTimeline.liftState(m.u * 1900))
        page.setFloatUniform("pageMix", mix.toFloat())
        page.setFloatUniform("frame", x, y, scale)
        rect(canvas, page, 0f, 0f, width, height)
    }

    fun draw(canvas: Canvas, m: MetacardMotion) {
        val u = m.u
        val reduced = m.reduced
        val e = MetacardTimeline.effectsState(u, m.forward, m.clock, reduced)
        uniforms(
            bounce,
            MetacardTimeline.bounceUniforms(u, m.spin, m.anchorSpin, reduced),
            MetacardShaders.BOUNCE,
        )
        paint.blendMode = BlendMode.PLUS
        rect(canvas, bounce, -160f, 0f, 562f, 874f)
        paint.blendMode = BlendMode.SRC_OVER
        val h =
            MetacardTimeline.heroUniforms(
                u,
                m.spin,
                m.clock,
                m.forward,
                m.objectPress,
                m.dragYaw,
                m.dragPitch,
                reduced,
            )
        uniforms(
            bloom,
            mapOf(
                "amount" to MetacardTimeline.values(e.getValue("bloom")),
                "reach" to MetacardTimeline.values(e.getValue("bloomReach")),
                "centre" to h.getValue("centre"),
                "halfSize" to h.getValue("halfSize"),
                "corner" to h.getValue("corner"),
                "angle" to MetacardTimeline.values(MetacardTimeline.radians(m.spin)),
                "colors" to MetacardTimeline.colors,
            ),
            MetacardShaders.BLOOM,
        )
        if (e.getValue("bloom") > 0) rect(canvas, bloom, -160f, 80f, 562f, 874f)
        val layer =
            canvas.saveLayerAlpha(
                -160f,
                0f,
                562f,
                874f,
                if (reduced) ((1 - u) * 255).toInt() else 255,
            )
        for (index in 0..8) {
            val f =
                MetacardTimeline.fanUniforms(
                    index % 8,
                    m.spin,
                    if (reduced) 0.0 else u,
                    m.fanPress,
                    index == 8,
                    m.anchorSpin,
                )
            if (f.getValue("opacity")[0] <= 0 || index == 8 && f.getValue("handover")[0] == 0f)
                continue
            uniforms(shadows[index], f, MetacardShaders.FAN_SHADOW)
            uniforms(fan[index], f, MetacardShaders.FAN)
            leaf(canvas, shadows[index], f, true)
            leaf(canvas, fan[index], f, false)
        }
        dark.setFloatUniform("amount", if (reduced) 1f else e.getValue("darkness").toFloat())
        paint.blendMode = BlendMode.SRC_ATOP
        rect(canvas, dark, 0f, 200f, 402f, 660f)
        paint.blendMode = BlendMode.SRC_OVER
        if (reduced)
            drawHero(
                canvas,
                MetacardTimeline.heroUniforms(0.0, m.spin, 0.0, 0.0, m.objectPress, reduced = true),
            )
        canvas.restoreToCount(layer)
        val heroLayer =
            if (reduced) canvas.saveLayerAlpha(-160f, 0f, 562f, 874f, (u * 255).toInt())
            else canvas.save()
        drawHero(
            canvas,
            if (reduced)
                MetacardTimeline.heroUniforms(1.0, m.spin, 0.0, 0.0, m.objectPress, reduced = true)
            else h,
        )
        canvas.restoreToCount(heroLayer)
        halo.setFloatUniform("halo", e.getValue("halo").toFloat())
        if (e.getValue("halo") > 0) rect(canvas, halo, -50f, 160f, 452f, 680f)
        paint.shader = null
    }

    private fun leaf(
        canvas: Canvas,
        shader: RuntimeShader,
        f: Map<String, FloatArray>,
        shadow: Boolean,
    ) {
        val angle = f.getValue("angle")[0]
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        val hw = if (shadow) (176f * 144 / 128) / 2 else 72.5f
        val hh = if (shadow) (129f * 91 / 81) / 2 else 46f
        val scale = f.getValue("scale")[0]
        val rx = (c * hw + s * hh) * scale
        val ry = (s * hw + c * hh) * scale
        val center = f.getValue("centre")
        val x = center[0] + if (shadow) -1.8f else 0f
        val y = center[1] + if (shadow) 3.56f else 0f
        rect(canvas, shader, x - rx, y - ry, x + rx, y + ry)
    }

    private fun drawHero(canvas: Canvas, h: Map<String, FloatArray>) {
        uniforms(heroShadow, h, MetacardShaders.HERO_SHADOW)
        uniforms(inner, h, MetacardShaders.INNER_LIGHT)
        uniforms(hero, h, MetacardShaders.HERO)
        val c = h.getValue("centre")
        val size = h.getValue("halfSize")
        val radius = max(96f, hypot(size[0], size[1]) * 1.16f) + 64
        rect(canvas, heroShadow, c[0] - radius, c[1] - radius, c[0] + radius, c[1] + radius)
        if (h.getValue("inner")[0] > 0)
            rect(canvas, inner, c[0] - radius, c[1] - radius, c[0] + radius, c[1] + radius)
        val morph = h.getValue("morph")[0]
        val octagon = h.getValue("octagon")[0]
        val x = octagon + (size[0] - octagon) * morph
        val y = octagon + (size[1] - octagon) * morph
        val r = h.getValue("rot")
        val thickness = h.getValue("thickness")[0]
        val z = abs(r[2]) * x + abs(r[5]) * y + thickness
        val projection = 555 / (555 - z)
        val rx = (abs(r[0]) * x + abs(r[3]) * y + thickness) * projection + 2
        val ry = (abs(r[1]) * x + abs(r[4]) * y + thickness) * projection + 2
        rect(canvas, hero, c[0] - rx, c[1] - ry, c[0] + rx, c[1] + ry)
    }
}
