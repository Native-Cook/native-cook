package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import android.graphics.*

/** Designed still illustrations for API 26–32, where RuntimeShader is unavailable. */
internal object IconMorphFallback {
    fun draw(canvas: Canvas, x: Double, y: Double, unit: Double, step: Int, alpha: Double) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.saveLayerAlpha(null, (alpha * 255).toInt().coerceIn(0, 255))
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.scale((unit / 84).toFloat(), (unit / 84).toFloat())
        fun fill(top: Int, bottom: Int) {
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(-65f, -70f, 65f, 70f, top, bottom, Shader.TileMode.CLAMP)
        }
        fun mark(value: String, size: Float, x: Float, y: Float, color: Int) {
            p.shader = null
            p.color = color
            p.textSize = size
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            canvas.drawText(value, x, y, p)
        }
        fun currency(x: Float, y: Float, scale: Float, tint: Int) {
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, -scale)
            canvas.skew(0.20f, 0f)
            p.shader = null
            p.color = tint
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.126f
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            val mark = Path().apply {
                moveTo(-0.24f, -0.28f)
                lineTo(-0.24f, 0.28f)
                lineTo(0.24f, -0.28f)
                lineTo(0.24f, 0.28f)
                moveTo(-0.40f, 0f)
                lineTo(0.40f, 0f)
            }
            canvas.drawPath(mark, p)
            p.style = Paint.Style.FILL
            canvas.restore()
        }
        when (step) {
            0 -> {
                fill(0xff999caa.toInt(), 0xff626672.toInt())
                val ring = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRoundRect(-43f, -71f, 43f, 71f, 36f, 36f, Path.Direction.CW)
                    addRoundRect(-11f, -43f, 11f, 43f, 11f, 11f, Path.Direction.CW)
                }
                canvas.drawPath(ring, p)
                fill(0xffffeb45.toInt(), 0xffdfa013.toInt())
                canvas.drawOval(-47f, -94f, 45f, -4f, p)
                canvas.drawCircle(-83f, 45f, 20f, p)
                canvas.drawCircle(89f, 55f, 20f, p)
                currency(0f, -49f, 60f, 0xffed7410.toInt())
            }
            1 -> {
                fill(0xff3e3342.toInt(), 0xff13141e.toInt())
                canvas.drawCircle(0f, 0f, 74f, p)
                fill(0xffff7e81.toInt(), 0xffc00035.toInt())
                canvas.drawRoundRect(-30f, -42f, 30f, 38f, 28f, 28f, p)
                fill(0xff343440.toInt(), 0xff161822.toInt())
                canvas.drawOval(-40f, 26f, 40f, 45f, p)
                fill(0xff94b8ff.toInt(), 0xff4268f6.toInt())
                val shield = Path().apply {
                    moveTo(30f, -65f)
                    lineTo(54f, -71f)
                    lineTo(79f, -64f)
                    lineTo(77f, -40f)
                    lineTo(54f, -21f)
                    lineTo(31f, -40f)
                    close()
                }
                canvas.drawPath(shield, p)
                mark("✓", 38f, 54f, -35f, Color.WHITE)
            }
            2 -> {
                fill(0xff6dc9ff.toInt(), 0xff0871e1.toInt())
                canvas.drawRoundRect(-76f, -48f, 76f, 49f, 11f, 11f, p)
                fill(0xfff2f6ff.toInt(), 0xffbbcce5.toInt())
                canvas.drawRoundRect(-61f, -69f, 61f, 14f, 10f, 10f, p)
                currency(0f, -44f, 49f, 0xff181d29.toInt())
                fill(0xff3bb9ff.toInt(), 0xff066bdf.toInt())
                val fold = Path().apply { moveTo(-76f, -43f)
                    lineTo(0f, 7f)
                    lineTo(76f, -43f)
                    lineTo(76f, 49f)
                    lineTo(-76f, 49f)
                    close() }
                canvas.drawPath(fold, p)
                fill(0xffffea4a.toInt(), 0xffffa21c.toInt())
                canvas.drawCircle(88f, -62f, 22f, p)
                canvas.drawCircle(-82f, 42f, 27f, p)
                mark("♥", 23f, 88f, -54f, 0xffe4313b.toInt())
                mark("⌣", 35f, -82f, 47f, 0xff88322a.toInt())
            }
            else -> {
                canvas.rotate(-6f)
                fill(0xffd59b23.toInt(), 0xffbd7713.toInt())
                canvas.drawRoundRect(-79f, -38f, 79f, 51f, 15f, 15f, p)
                fill(0xffffffff.toInt(), 0xffbbc6d7.toInt())
                canvas.drawRoundRect(-77f, -42f, 77f, 44f, 14f, 14f, p)
                fill(0xffffeb40.toInt(), 0xffe6c900.toInt())
                canvas.drawRoundRect(-79f, -52f, 79f, 34f, 14f, 14f, p)
                p.shader = null
                p.color = Color.WHITE
                canvas.drawRoundRect(-58f, -30f, -19f, -26f, 2f, 2f, p)
                canvas.drawRoundRect(-58f, -17f, -8f, -13f, 2f, 2f, p)
                fill(0xff78ffa6.toInt(), 0xff08b757.toInt())
                canvas.drawCircle(68f, -52f, 19f, p)
                mark("✓", 30f, 68f, -42f, Color.WHITE)
            }
        }
        canvas.restore()
    }
}
