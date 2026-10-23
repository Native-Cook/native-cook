package com.ngocdevv.nativecook.blocks.animations.superchargecard

import android.content.Context
import android.graphics.*
import android.os.Build
import java.io.File
import kotlin.math.*

internal class SuperchargeRenderer(context: Context) {
    private val paper = decode(SuperchargePaperData.bytes())
    private val tree = decode(SuperchargeTreeData.bytes())
    private val bold = font(context, "bold", SuperchargeBoldData.bytes())
    private val regular = font(context, "regular", SuperchargeRegularData.bytes())
    private val mono = font(context, "mono", SuperchargeMonoData.bytes())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isLinearText = true }
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu(paper, tree) else null
    private val electricity = SuperchargeElectricityRenderer()
    private var captionFrom = 0
    private var captionTo = 0
    private var captionAt = 0.0
    private var previousClock = -1.0
    private val dotAt = DoubleArray(12) { -1.0 }
    private val fallback = if (gpu == null) SuperchargeFallback(paper) else null

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        s: SuperchargeState,
        reduced: Boolean,
    ) {
        val w = width / density
        val h = height / density
        val k = min(1f, min(w / 402f, h / 874f))
        val x = (w - 402 * k) / 2
        val y = (h - 874 * k) / 2
        canvas.save()
        canvas.scale(density, density)
        if (gpu != null) gpu.draw(canvas, w, h, x, y, k, s, reduced)
        else {
            canvas.drawColor(Color.rgb(5, 5, 6))
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(k, k)
            fallback?.draw(canvas, s)
            canvas.restore()
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(k, k)
        canvas.save()
        cardTransform(canvas, s)
        electricity.draw(canvas, s)
        print(canvas, s)
        canvas.restore()
        caption(canvas, s, reduced)
        canvas.restore()
        if (gpu != null) gpu.flash(canvas, w, h, x, y, k, s)
        canvas.restore()
        previousClock = s.clock
    }

    private fun cardTransform(canvas: Canvas, s: SuperchargeState) {
        canvas.translate((201 + s.card[0]).toFloat(), (458 + s.card[1]).toFloat())
        canvas.rotate((s.card[2] * 180 / PI).toFloat())
        canvas.scale(s.card[3].toFloat(), s.card[3].toFloat())
        canvas.translate(-201f, -458f)
    }

    private fun run(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        font: Typeface,
        size: Float,
        tracking: Float = 0f,
        color: Int = Color.rgb(23, 24, 26),
        alpha: Double = 1.0,
    ): Float {
        text.typeface = font
        text.textSize = size
        text.color = color
        text.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).roundToInt()
        var at = x
        value.forEach { ch ->
            val letter = ch.toString()
            canvas.drawText(letter, at, y, text)
            at += text.measureText(letter) + tracking
        }
        return at - x - if (value.isNotEmpty()) tracking else 0f
    }

    private fun print(canvas: Canvas, s: SuperchargeState) {
        val resting = max(0.0, 1 - 2 * s.charged)
        val incoming = max(0.0, 2 * s.charged - 1)
        val restRise = (-4 * s.charged).toFloat()
        val chargedRise = (4 * (1 - s.charged)).toFloat()
        text.typeface = mono
        text.textSize = 10f
        val advance = text.measureText("0") + 1.2f
        run(canvas, "CHG ", 85f, 492f, mono, 10f, 1.2f, alpha = .56)
        val percent = (s.meter * 100).roundToInt().toString().padStart(3, '0') + "%"
        run(
            canvas,
            percent,
            85 + 4 * advance,
            492 + restRise,
            mono,
            10f,
            1.2f,
            alpha = .56 * resting,
        )
        run(
            canvas,
            "MAX",
            85 + 4 * advance,
            492 + chargedRise,
            mono,
            10f,
            1.2f,
            alpha = .56 * incoming,
        )
        for (i in 0 until 12) {
            val on = s.meter + M.epsilon >= (i + 1) / 12.0
            if (!on || s.clock < previousClock) dotAt[i] = -1.0
            if (on && dotAt[i] < 0) dotAt[i] = s.clock
            paint.color = Color.argb(66, 23, 24, 26)
            canvas.drawCircle(150 + i * 6f, 488.5f, 1f, paint)
            if (on) {
                val pop = ((s.clock - dotAt[i]) / M.print.dotPop).coerceIn(0.0, 1.0)
                paint.color = Color.rgb(199, 154, 0)
                canvas.drawCircle(150 + i * 6f, 488.5f, (.6 + .4 * pop).toFloat(), paint)
            }
        }
        val titleWidth = run(canvas, "Supercharge", 85f, 544f, bold, 28f, -.6f)
        val c = s.charged
        val spring =
            if (c >= 1) 0.0 else exp(-M.print.stampDamping * c) * cos(M.print.stampFrequency * c)
        canvas.save()
        canvas.translate(85 + titleWidth - .6f, (544 - M.print.stampDrop * spring).toFloat())
        canvas.scale(
            (1 + M.print.stampScale * spring).toFloat(),
            (1 + M.print.stampScale * spring).toFloat(),
        )
        run(canvas, "d", 0f, 0f, bold, 28f, alpha = c)
        canvas.restore()
        val bodyColor = Color.argb(143, 23, 24, 26)
        run(
            canvas,
            "Press and hold the card",
            85f,
            566 + restRise,
            regular,
            13f,
            color = bodyColor,
            alpha = resting,
        )
        run(
            canvas,
            "to build up a charge",
            85f,
            584 + restRise,
            regular,
            13f,
            color = bodyColor,
            alpha = resting,
        )
        run(
            canvas,
            "Full power, held in glass.",
            85f,
            566 + chargedRise,
            regular,
            13f,
            color = bodyColor,
            alpha = incoming,
        )
        run(
            canvas,
            "Tap the card to let it go.",
            85f,
            584 + chargedRise,
            regular,
            13f,
            color = bodyColor,
            alpha = incoming,
        )
    }

    private fun caption(canvas: Canvas, s: SuperchargeState, reduced: Boolean) {
        if (s.clock < previousClock) {
            captionFrom = 0
            captionTo = 0
            captionAt = 0.0
        }
        if (s.caption != captionTo) {
            captionFrom = captionTo
            captionTo = s.caption
            captionAt = s.clock
        }
        val elapsed = s.clock - captionAt
        listOf("HOLD TO CHARGE", "RELEASE", "TAP TO DRAIN").forEachIndexed { index, value ->
            val visibility =
                when {
                    captionFrom == captionTo || elapsed < 0 -> if (index == captionTo) 1.0 else 0.0
                    captionTo == -1 ->
                        if (index == captionFrom) max(0.0, 1 - elapsed / M.print.captionOut)
                        else 0.0
                    captionFrom == -1 ->
                        if (index == captionTo) min(1.0, elapsed / M.print.captionIn) else 0.0
                    index == captionFrom ->
                        max(0.0, 1 - 2 * min(1.0, elapsed / M.print.captionDuration))
                    index == captionTo ->
                        max(0.0, 2 * min(1.0, elapsed / M.print.captionDuration) - 1)
                    else -> 0.0
                }
            text.typeface = mono
            text.textSize = 11f
            val width =
                value.sumOf { text.measureText(it.toString()).toDouble() }.toFloat() +
                    (value.length - 1) * 2.2f
            val alpha =
                visibility *
                    (if (index == 1)
                        if (reduced) 1.0 else .85 + .15 * sin(s.clock * 2 * PI * M.print.releaseHz)
                    else .46)
            run(
                canvas,
                value,
                201 - width / 2,
                (712 + (1 - visibility) * M.print.captionRise).toFloat(),
                mono,
                11f,
                2.2f,
                if (index == 1) Color.rgb(255, 242, 122) else Color.WHITE,
                alpha,
            )
        }
    }

    private fun decode(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inScaled = false
                inPremultiplied = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun font(context: Context, name: String, bytes: ByteArray): Typeface {
        val file = File(context.cacheDir, "supercharge-$name.ttf")
        if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        return Typeface.createFromFile(file)
    }

    @android.annotation.TargetApi(33)
    private class Gpu(paper: Bitmap, tree: Bitmap) {
        private val scene = RuntimeShader(SuperchargeShader.scene)
        private val flash = RuntimeShader(SuperchargeShader.flash)
        private val paint = Paint()
        private val flashPaint = Paint().apply { blendMode = BlendMode.PLUS }

        init {
            // Input buffers skip color management and premultiplication: these are data, not art.
            scene.setInputBuffer(
                "paperStrip",
                BitmapShader(paper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                },
            )
            scene.setInputBuffer(
                "treeStrip",
                BitmapShader(tree, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                },
            )
            paint.shader = scene
            flashPaint.shader = flash
        }

        fun draw(
            canvas: Canvas,
            w: Float,
            h: Float,
            x: Float,
            y: Float,
            k: Float,
            s: SuperchargeState,
            reduced: Boolean,
        ) {
            scene.setFloatUniform("resolution", w, h)
            scene.setFloatUniform("frame", x, y, k)
            scene.setFloatUniform("uClock", if (reduced) 0f else s.clock.toFloat())
            scene.setFloatUniform("uCard", s.card.map { it.toFloat() }.toFloatArray())
            scene.setFloatUniform("uSink", s.sink.toFloat())
            scene.setFloatUniform("uEnergy", s.energy.toFloat())
            scene.setFloatUniform("uGlow", s.glow.toFloat())
            scene.setFloatUniform("uGlyph", s.glyph.toFloat())
            scene.setFloatUniform("uGlyphScale", s.glyphScale.toFloat())
            scene.setFloatUniform("uRing", s.ring.toFloat())
            scene.setFloatUniform("uRingLit", s.ringLit.toFloat())
            scene.setFloatUniform("uDim", s.dim.toFloat())
            scene.setFloatUniform("uTree", s.tree.toFloat())
            scene.setFloatUniform("uTreeGlow", s.treeGlow.toFloat())
            scene.setFloatUniform("uPulse", s.pulse.toFloat())
            scene.setFloatUniform("uShock", s.shock.toFloat())
            scene.setFloatUniform("uShaft", s.shaft[0].toFloat(), s.shaft[1].toFloat())
            scene.setFloatUniform(
                "uTouch",
                s.touch[0].toFloat(),
                s.touch[1].toFloat(),
                s.touch[2].toFloat(),
            )
            scene.setFloatUniform("uShake", hypot(s.card[0], s.card[1]).toFloat())
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        fun flash(
            canvas: Canvas,
            w: Float,
            h: Float,
            x: Float,
            y: Float,
            k: Float,
            s: SuperchargeState,
        ) {
            if (s.flash < .001) return
            val dx = (214 - 201) * s.card[3]
            val dy = (312 + s.sink - 458) * s.card[3]
            val co = cos(s.card[2])
            val si = sin(s.card[2])
            flash.setFloatUniform(
                "centre",
                x + (201 + s.card[0] + co * dx - si * dy).toFloat() * k,
                y + (458 + s.card[1] + si * dx + co * dy).toFloat() * k,
            )
            flash.setFloatUniform("scale", k)
            flash.setFloatUniform("flash", s.flash.toFloat())
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }
    }
}
