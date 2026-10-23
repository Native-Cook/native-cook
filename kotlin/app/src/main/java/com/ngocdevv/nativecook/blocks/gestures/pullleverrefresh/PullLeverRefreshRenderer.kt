package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object PullLeverRefreshRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun renderStaticBackground(
        widthDp: Float,
        heightDp: Float,
        density: Float,
        layout: PullLeverRefreshMachineLayout,
    ): Bitmap {
        val width = max((widthDp * density).toInt(), 1)
        val height = max((heightDp * density).toInt(), 1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            drawStudio(canvas, widthDp, heightDp)
            withMachine(canvas, layout) {
                drawBody(this)
                drawGaugeFace(this)
                drawGroup(this)
                drawPortafilter(this)
                drawTray(this)
                drawTimerCase(this)
            }
        }
    }

    fun drawDynamic(canvas: Canvas, layout: PullLeverRefreshMachineLayout, state: PullLeverRefreshState) {
        withMachine(canvas, layout) {
            drawPour(this, state.brew)
            drawVentPuff(this, state.ventPuff)
            drawCup(this, state.brew, state.cupOffset, state.cupAlpha, state.clock, state.puff)
            drawNeedle(this, state.bar)
            drawTimer(this, state.brew, state.stage)
            drawLever(this, state.angle)
        }
    }

    private fun drawStudio(canvas: Canvas, width: Float, height: Float) {
        reset(fill)
        if (Build.VERSION.SDK_INT >= 33) {
            val shader = RuntimeShader(STUDIO_SHADER)
            shader.setFloatUniform("resolution", width, height)
            fill.shader = shader
        } else {
            fill.shader = RadialGradient(
                width * 0.48f,
                height * 0.42f,
                max(width, height) * 0.7f,
                intArrayOf(Color.rgb(35, 31, 27), Color.rgb(11, 10, 9)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, height, fill)
        reset(fill)
        for (i in 0 until 360) {
            val x = ((i * 73) % 997) / 997f * width
            val y = ((i * 193) % 991) / 991f * height
            fill.color = if (i % 3 == 0) 0x09FFFFFF else 0x0B000000
            canvas.drawCircle(x, y, 0.45f + (i % 4) * 0.12f, fill)
        }
        fill.shader = RadialGradient(
            width * 0.55f,
            height * 0.86f,
            width * 0.52f,
            intArrayOf(0x2D5D4637, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(RectF(width * 0.05f, height * 0.77f, width * 0.97f, height * 1.03f), fill)
    }

    private fun drawBody(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        reset(fill)
        fill.shader = verticalGradient(m.BodyTop, m.BodyBottom, 0xFF3A3936.toInt(), 0xFF11100F.toInt())
        canvas.drawRoundRect(RectF(m.BodyLeft, m.BodyTop, m.BodyRight, m.BodyBottom), m.BodyRadius, m.BodyRadius, fill)
        fill.shader = LinearGradient(m.BodyLeft, 0f, m.BodyRight, 0f, intArrayOf(0xFF121110.toInt(), 0xFF47443F.toInt(), 0xFF171614.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(m.BodyLeft + 5f, m.BodyTop + 5f, m.BodyRight - 5f, m.BodyBottom - 5f), 10f, 10f, fill)
        reset(fill)
        fill.color = 0x46110D09
        canvas.drawRoundRect(RectF(m.BodyRight - 4f, m.BodyTop + 14f, m.BodyRight + 5f, m.BodyBottom - 10f), 3f, 3f, fill)
        fill.color = 0xFF080706.toInt()
        listOf(-140f, -70f, 88f).forEach { x -> canvas.drawRoundRect(RectF(x - 9f, 307f, x + 9f, 316f), 3f, 3f, fill) }
        fill.shader = RadialGradient(-25f, m.Floor, 128f, intArrayOf(0x503B2A20, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(-165f, 299f, 148f, 330f), fill)
    }

    private fun drawGaugeFace(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        val x = m.GaugeX
        val y = m.GaugeY
        reset(fill)
        fill.shader = RadialGradient(x - 8f, y - 11f, m.GaugeBezel * 1.4f, intArrayOf(0xFFFFFFFF.toInt(), 0xFF625E58.toInt(), 0xFF171513.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, m.GaugeBezel, fill)
        fill.color = 0xFFDED8CF.toInt()
        fill.shader = null
        canvas.drawCircle(x, y, m.GaugeDial, fill)
        fill.shader = RadialGradient(x - 8f, y - 12f, 45f, intArrayOf(0x55FFFFFF, 0x11000000), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, m.GaugeDial, fill)
        reset(stroke)
        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0..m.GaugeTicks) {
            val angle = Math.toRadians((135f + i / m.GaugeTicks.toFloat() * m.GaugeSweep).toDouble())
            val major = i % 5 == 0
            val r0 = if (major) 25.5f else 28f
            stroke.color = if (i >= 20) 0xFF9A5C42.toInt() else 0xFF615B54.toInt()
            stroke.strokeWidth = if (major) 1.25f else .65f
            canvas.drawLine(x + cos(angle).toFloat() * r0, y + sin(angle).toFloat() * r0, x + cos(angle).toFloat() * 31f, y + sin(angle).toFloat() * 31f, stroke)
        }
        reset(text)
        text.color = PullLeverRefreshTokens.Color.DialMute
        text.textSize = 5.8f
        text.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        listOf(0, 3, 6, 9, 12).forEach { value ->
            val a = Math.toRadians(pullLeverRefreshGaugeAngle(value.toFloat()).toDouble())
            canvas.drawText(value.toString(), x + cos(a).toFloat() * 19.6f, y + sin(a).toFloat() * 19.6f + 2f, text)
        }
        text.textSize = 5.2f
        text.letterSpacing = .08f
        canvas.drawText("BAR", x, y + 14f, text)
    }

    private fun drawGroup(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        reset(fill)
        fill.shader = metalGradient(-m.GroupRadius, m.GroupRadius)
        canvas.drawRoundRect(RectF(-m.GroupCapRadius, m.GroupCapTop, m.GroupCapRadius, m.GroupCapBottom), 5f, 5f, fill)
        fill.shader = if (Build.VERSION.SDK_INT >= 33) materialShader(-m.GroupRadius, m.GroupRadius, 0.78f) else metalGradient(-m.GroupRadius, m.GroupRadius)
        canvas.drawRoundRect(RectF(-m.GroupRadius, m.GroupTop, m.GroupRadius, m.GroupBottom), 12f, 12f, fill)
        reset(stroke)
        stroke.color = 0x7099948D
        stroke.strokeWidth = 1f
        canvas.drawLine(-27f, m.GroupGroove, 27f, m.GroupGroove, stroke)
        canvas.drawLine(-26f, m.GroupGroove + 3f, 26f, m.GroupGroove + 3f, stroke)
        reset(fill)
        fill.shader = metalGradient(-m.FlangeRadius, m.FlangeRadius)
        canvas.drawRoundRect(RectF(-m.FlangeRadius, m.FlangeTop, m.FlangeRadius, m.FlangeBottom), 6f, 6f, fill)
        fill.color = 0xFF171514.toInt()
        fill.shader = null
        canvas.drawOval(RectF(-29f, 119f, 29f, 131f), fill)
        fill.shader = metalGradient(m.NeckFrom, m.NeckTo)
        canvas.drawRoundRect(RectF(m.NeckFrom, m.NeckY - m.NeckRadius, m.NeckTo, m.NeckY + m.NeckRadius), m.NeckRadius, m.NeckRadius, fill)
        fill.shader = metalGradient(m.NutFrom, m.NutTo)
        canvas.drawRoundRect(RectF(m.NutFrom, m.NeckY - m.NutRadius, m.NutTo, m.NeckY + m.NutRadius), 2f, 2f, fill)
    }

    private fun drawPortafilter(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        reset(fill)
        fill.shader = metalGradient(-m.BowlRadius, m.BowlRadius)
        canvas.drawRoundRect(RectF(-m.BowlRadius, m.BowlTop, m.BowlRadius, m.BowlBottom), 8f, 8f, fill)
        canvas.drawRoundRect(RectF(-m.StemRadius, m.StemTop, m.StemRadius, m.StemBottom), 3f, 3f, fill)
        canvas.save()
        canvas.rotate(m.PortafilterTilt, 26f, m.PortafilterY)
        fill.shader = metalGradient(m.PortafilterFrom, m.PortafilterTo)
        canvas.drawRoundRect(RectF(m.PortafilterFrom, m.PortafilterY - m.PortafilterHalf, m.PortafilterTo + 4f, m.PortafilterY + m.PortafilterHalf), m.PortafilterHalf, m.PortafilterHalf, fill)
        fill.shader = LinearGradient(m.WoodFrom, 0f, m.WoodTo, 0f, intArrayOf(0xFF4C2415.toInt(), 0xFFB16B3A.toInt(), 0xFF35170E.toInt()), null, Shader.TileMode.CLAMP)
        val wood = Path().apply {
            moveTo(m.WoodFrom, m.PortafilterY - m.WoodR0)
            lineTo(m.WoodTo - 5f, m.PortafilterY - m.WoodR1)
            quadTo(m.WoodTo + 4f, m.PortafilterY, m.WoodTo - 5f, m.PortafilterY + m.WoodR1)
            lineTo(m.WoodFrom, m.PortafilterY + m.WoodR0)
            close()
        }
        canvas.drawPath(wood, fill)
        canvas.restore()
        reset(stroke)
        stroke.strokeWidth = m.SpoutRadius * 2f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.shader = metalGradient(-16f, 16f)
        listOf(-m.SpoutX, m.SpoutX).forEach { x ->
            val path = Path().apply {
                moveTo(x, m.StemBottom)
                cubicTo(x, 158f, x + if (x < 0) -m.SpoutSpread else m.SpoutSpread, 161f, x + if (x < 0) -m.SpoutSpread else m.SpoutSpread, m.SpoutTip)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawTray(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        reset(fill)
        fill.shader = verticalGradient(m.TrayTop, m.TrayBottom, 0xFFBDB7AE.toInt(), 0xFF3A3733.toInt())
        canvas.drawRoundRect(RectF(m.TrayLeft, m.TrayTop, m.TrayRight, m.TrayBottom), m.TrayRadius, m.TrayRadius, fill)
        fill.color = 0xFF1B1917.toInt()
        fill.shader = null
        canvas.drawRect(m.TrayLeft + 4f, m.TrayTop + 4f, m.TrayRight - 4f, m.TrayFace, fill)
        reset(stroke)
        stroke.color = 0xFF706B64.toInt()
        stroke.strokeWidth = m.TrayBar
        var x = m.TrayLeft + 7f
        while (x < m.TrayRight - 5f) {
            canvas.drawLine(x, m.TrayTop + 5f, x, m.TrayFace - 1f, stroke)
            x += m.TrayPitch
        }
    }

    private fun drawTimerCase(canvas: Canvas) {
        val m = PullLeverRefreshTokens.Machine
        reset(fill)
        fill.color = 0xFF080706.toInt()
        canvas.drawRoundRect(RectF(m.TimerX - m.TimerWidth / 2f, m.TimerY - m.TimerHeight / 2f, m.TimerX + m.TimerWidth / 2f, m.TimerY + m.TimerHeight / 2f), m.TimerRadius, m.TimerRadius, fill)
        fill.shader = verticalGradient(m.TimerY - 16f, m.TimerY + 16f, 0xFF282522.toInt(), 0xFF0B0A09.toInt())
        canvas.drawRoundRect(RectF(m.TimerX - 25f, m.TimerY - 16f, m.TimerX + 25f, m.TimerY + 16f), 6f, 6f, fill)
    }

    private fun drawNeedle(canvas: Canvas, bar: Float) {
        val m = PullLeverRefreshTokens.Machine
        val angle = Math.toRadians(pullLeverRefreshGaugeAngle(bar).toDouble())
        reset(stroke)
        stroke.color = 0xFF7D3327.toInt()
        stroke.strokeWidth = 1.5f
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(m.GaugeX - cos(angle).toFloat() * 4f, m.GaugeY - sin(angle).toFloat() * 4f, m.GaugeX + cos(angle).toFloat() * 25f, m.GaugeY + sin(angle).toFloat() * 25f, stroke)
        reset(fill)
        fill.color = 0xFF2A2521.toInt()
        canvas.drawCircle(m.GaugeX, m.GaugeY, 3.2f, fill)
        fill.color = 0xFFC2BAB0.toInt()
        canvas.drawCircle(m.GaugeX - .6f, m.GaugeY - .8f, 1.2f, fill)
    }

    private fun drawTimer(canvas: Canvas, brew: Float, stage: PullLeverRefreshStage) {
        val m = PullLeverRefreshTokens.Machine
        val seconds = when (stage) {
            PullLeverRefreshStage.BREWING -> brew * PullLeverRefreshTokens.Brew.Seconds
            PullLeverRefreshStage.DONE -> PullLeverRefreshTokens.Brew.Seconds
            else -> 0f
        }
        reset(fill)
        fill.color = if (stage == PullLeverRefreshStage.BREWING) 0xFFD48B55.toInt() else 0xFF4F443B.toInt()
        canvas.drawCircle(m.TimerX - 20f, m.TimerY - 11f, m.TimerLamp, fill)
        reset(text)
        text.color = PullLeverRefreshTokens.Color.Digit
        text.textSize = 22f
        text.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        text.textAlign = Paint.Align.CENTER
        val shown = min(seconds.toInt(), 99).toString().padStart(2, '0')
        canvas.save()
        canvas.clipRect(m.TimerX - 23f, m.TimerY - 11f, m.TimerX + 23f, m.TimerY + 13f)
        canvas.drawText(shown, m.TimerX, m.TimerY + 9f, text)
        canvas.restore()
    }

    private fun drawPour(canvas: Canvas, brew: Float) {
        val b = PullLeverRefreshTokens.Brew
        if (brew <= b.Drip || brew >= b.Dry) return
        val strength = when {
            brew < b.Flowing -> (brew - b.Drip) / (b.Flowing - b.Drip)
            brew < b.Thinning -> 1f
            else -> 1f - (brew - b.Thinning) / (b.Dry - b.Thinning)
        }.coerceIn(0f, 1f)
        reset(stroke)
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = 1.5f + strength * 1.8f
        stroke.shader = verticalGradient(166f, 234f, 0xFF3E170B.toInt(), 0xFFC27137.toInt())
        val wobble = sin(brew * PI.toFloat() * 18f) * .8f
        listOf(-12f, 12f).forEach { x ->
            val path = Path().apply {
                moveTo(x, 165f)
                cubicTo(x + wobble, 187f, x - wobble, 207f, x * .55f, 233f)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawVentPuff(canvas: Canvas, puff: Float) {
        if (puff <= 0f) return
        reset(stroke)
        stroke.color = 0xA8D8D0C6.toInt()
        stroke.strokeWidth = 1.1f
        stroke.strokeCap = Paint.Cap.ROUND
        repeat(3) { index ->
            val phase = (1f - puff) * 16f + index * 3f
            val x = -51f - index * 2.5f
            val path = Path().apply {
                moveTo(x, 51f - phase)
                cubicTo(x - 5f, 45f - phase, x + 4f, 39f - phase, x - 1f, 33f - phase)
            }
            stroke.alpha = (puff * (110 - index * 22)).toInt().coerceIn(0, 255)
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawCup(canvas: Canvas, brew: Float, offset: Float, alpha: Float, clock: Float, puff: Float) {
        if (alpha <= 0f) return
        val m = PullLeverRefreshTokens.Machine
        canvas.save()
        canvas.translate(offset, 0f)
        val cup = Path().apply {
            moveTo(-m.CupRadius, m.CupRim)
            cubicTo(-m.CupBelly, 250f, -m.CupBottom, 274f, -m.CupFoot, m.CupBase)
            lineTo(m.CupFoot, m.CupBase)
            cubicTo(m.CupBottom, 274f, m.CupBelly, 250f, m.CupRadius, m.CupRim)
            close()
        }
        reset(fill)
        fill.alpha = (alpha * 255).toInt()
        fill.shader = if (Build.VERSION.SDK_INT >= 33) materialShader(-m.CupRadius, m.CupRadius, .94f) else metalGradient(-m.CupRadius, m.CupRadius)
        canvas.drawPath(cup, fill)
        reset(stroke)
        stroke.alpha = (alpha * 255).toInt()
        stroke.strokeWidth = m.CupWall
        stroke.color = 0xFFD9D3CB.toInt()
        canvas.drawOval(RectF(-m.CupRadius, m.CupRim - 4f, m.CupRadius, m.CupRim + 5f), stroke)
        stroke.strokeWidth = 4f
        canvas.drawOval(RectF(m.CupEarX - m.CupEarRx, m.CupEarY - m.CupEarRy, m.CupEarX + m.CupEarRx, m.CupEarY + m.CupEarRy), stroke)
        if (brew > 0.06f) {
            reset(fill)
            fill.alpha = (alpha * 255).toInt()
            fill.shader = RadialGradient(-6f, 229f, 39f, intArrayOf(0xFFD59B58.toInt(), 0xFF3A160B.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawOval(RectF(-33f, 227f, 33f, 235f), fill)
        }
        if (brew > .25f || puff > 0f) {
            reset(stroke)
            stroke.color = 0x88D9D1C7.toInt()
            stroke.strokeWidth = 1.2f
            for (i in 0..2) {
                val phase = (clock * 18f + i * 8f) % 18f
                val x = -11f + i * 11f + sin(clock * 1.7f + i) * 2f
                val path = Path().apply {
                    moveTo(x, 226f - phase)
                    cubicTo(x - 5f, 219f - phase, x + 5f, 213f - phase, x, 207f - phase)
                }
                stroke.alpha = ((1f - phase / 18f) * alpha * 100).toInt()
                canvas.drawPath(path, stroke)
            }
        }
        canvas.restore()
    }

    private fun drawLever(canvas: Canvas, angle: Float) {
        val l = PullLeverRefreshTokens.Lever
        canvas.save()
        canvas.rotate(angle)
        reset(fill)
        fill.shader = metalGradient(0f, l.FerruleTo)
        canvas.drawRoundRect(RectF(0f, -l.Half0, l.FerruleFrom, l.Half0), l.Fillet, l.Fillet, fill)
        canvas.drawRoundRect(RectF(l.FerruleFrom, -l.FerruleHalf, l.FerruleTo + 3f, l.FerruleHalf), 3f, 3f, fill)
        fill.shader = LinearGradient(l.HandleFrom, 0f, l.HandleTo, 0f, intArrayOf(0xFF32150D.toInt(), 0xFF9F5C31.toInt(), 0xFF4C2414.toInt()), null, Shader.TileMode.CLAMP)
        val handle = Path().apply {
            moveTo(l.HandleFrom, -l.HandleR0)
            cubicTo(119f, -9f, 144f, -l.HandleR1, l.HandleTo, -l.HandleR1 * .45f)
            quadTo(l.HandleTo + 5f, 0f, l.HandleTo, l.HandleR1 * .45f)
            cubicTo(144f, l.HandleR1, 119f, 9f, l.HandleFrom, l.HandleR0)
            close()
        }
        canvas.drawPath(handle, fill)
        reset(stroke)
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = .8f
        canvas.drawLine(107f, -4.5f, 153f, -6.4f, stroke)
        canvas.restore()
        reset(fill)
        fill.shader = RadialGradient(-4f, -6f, l.Boss * 1.5f, intArrayOf(0xFFFFFFFF.toInt(), 0xFF69645D.toInt(), 0xFF171513.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, l.Boss, fill)
        fill.color = 0xFF292623.toInt()
        fill.shader = null
        canvas.drawCircle(0f, 0f, l.Hub, fill)
        fill.shader = RadialGradient(-2f, -3f, l.Hub, intArrayOf(0xFFBFB8AE.toInt(), 0xFF383430.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 5.5f, fill)
    }

    private inline fun withMachine(canvas: Canvas, layout: PullLeverRefreshMachineLayout, block: Canvas.() -> Unit) {
        canvas.save()
        canvas.translate(layout.pivotX, layout.pivotY)
        canvas.scale(layout.scale, layout.scale)
        canvas.block()
        canvas.restore()
    }

    private fun metalGradient(left: Float, right: Float): Shader = LinearGradient(
        left,
        0f,
        right,
        0f,
        intArrayOf(0xFF282622.toInt(), 0xFFF0EAE1.toInt(), 0xFF716D66.toInt(), 0xFFE4DDD3.toInt(), 0xFF24211E.toInt()),
        floatArrayOf(0f, .18f, .48f, .72f, 1f),
        Shader.TileMode.CLAMP,
    )

    private fun verticalGradient(top: Float, bottom: Float, start: Int, end: Int): Shader =
        LinearGradient(0f, top, 0f, bottom, start, end, Shader.TileMode.CLAMP)

    private fun materialShader(left: Float, right: Float, polish: Float): Shader {
        if (Build.VERSION.SDK_INT < 33) return metalGradient(left, right)
        return RuntimeShader(MATERIAL_SHADER).apply {
            setFloatUniform("left", left)
            setFloatUniform("right", right)
            setFloatUniform("polish", polish)
        }
    }

    private fun reset(paint: Paint) {
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.shader = null
        paint.color = Color.WHITE
    }

    private const val STUDIO_SHADER = """
        uniform float2 resolution;
        half4 main(float2 p) {
            float2 uv = p / resolution;
            float2 d = uv - float2(0.48, 0.42);
            float vignette = smoothstep(0.78, 0.08, length(d * float2(0.85, 1.0)));
            float glow = exp(-14.0 * dot(d, d));
            float grain = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
            float3 c = float3(0.043, 0.039, 0.035) + vignette * float3(0.035, 0.028, 0.022) + glow * float3(0.018, 0.014, 0.010);
            c += grain * 0.018;
            return half4(c, 1.0);
        }
    """

    private const val MATERIAL_SHADER = """
        uniform float left;
        uniform float right;
        uniform float polish;
        half4 main(float2 p) {
            float u = clamp((p.x - left) / max(right - left, 0.001), 0.0, 1.0);
            float key = exp(-pow((u - 0.24) * 7.0, 2.0));
            float rim = exp(-pow((u - 0.74) * 11.0, 2.0));
            float base = 0.13 + 0.25 * smoothstep(0.0, 0.55, u) - 0.18 * smoothstep(0.62, 1.0, u);
            float v = base + key * 0.68 * polish + rim * 0.34 * polish;
            return half4(half3(v, v * 0.98, v * 0.94), 1.0);
        }
    """
}
