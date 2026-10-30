package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeMotion.Frame
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.C
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.Gauge
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.UI

/** The reference uses only Canvas primitives; there is no shader or API-33 dependency. */
internal class IsometricScoreGaugeArt(private val context: Context) {
    private val icons = context.assets.open("isometricscoregauge/isometricscoregauge_icons.webp").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private fun face(name: String) = Typeface.createFromAsset(context.assets, "isometricscoregauge/isometricscoregauge_$name.ttf")
    private val serif = face("DMSerifDisplay-Regular")
    private val display = face("ArchivoBlack-Regular")
    private val medium = face("Inter-Medium")
    private val semi = face("Inter-SemiBold")
    private val digitWidth = paint(0xffffff).apply { typeface = display; textSize = Gauge.numberSize }.measureText("8")
    // Android hardware Canvas ignores mask filters. Draw the small blurred masks in software,
    // then upload them as bitmaps, retaining the reference's Gaussian sigma in reference points.
    private val gaugeGlow = Bitmap.createBitmap(330, 330, Bitmap.Config.ARGB_8888)
    private val gaugeGlowCanvas = Canvas(gaugeGlow)
    private val cardGlows = intArrayOf(C.up, C.down).associateWith { tint ->
        Bitmap.createBitmap(231, 279, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.translate(115.5f, 139.5f)
            canvas.drawRoundRect(cardRect, 9f, 9f, paint(tint).apply { maskFilter = blur(14f) })
        }
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun color(rgb: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or rgb
    private fun paint(rgb: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(rgb, alpha) }
    private fun blur(sigma: Float) = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    private val cardRect get() = RectF(-UI.cardW / 2, -UI.cardH / 2, UI.cardW / 2, UI.cardH / 2)
    private fun centered(canvas: Canvas, text: String, x: Float, cy: Float, size: Float, font: Typeface, brush: Paint, tracking: Float = 0f) {
        brush.typeface = font
        brush.textSize = size
        if (tracking == 0f) { canvas.drawText(text, x - brush.measureText(text) / 2, cy + size * 0.36f, brush); return }
        val widths = FloatArray(text.length)
        brush.getTextWidths(text, widths)
        val total = widths.sum() + max(0, text.length - 1) * tracking * size
        var left = x - total / 2
        for (index in text.indices) {
            canvas.drawText(text, index, index + 1, left, cy + size * 0.36f, brush)
            left += widths[index] + tracking * size
        }
    }
    fun drawScene(canvas: Canvas, width: Float, height: Float, frame: Frame, ambient: Double, overlay: Frame? = null, overlayAlpha: Float = 0f) {
        canvas.drawColor(color(C.bg))
        canvas.save()
        val scale = width / IsometricScoreGaugeTheme.REF_W
        canvas.translate(0f, (height - IsometricScoreGaugeTheme.REF_H * scale) / 2)
        canvas.scale(scale, scale)
        drawReferenceScene(canvas, frame, ambient)
        if (overlay != null && overlayAlpha > 0.001f) {
            val layer = canvas.saveLayerAlpha(null, (overlayAlpha * 255).roundToInt())
            canvas.drawColor(color(C.bg))
            drawReferenceScene(canvas, overlay, 0.0)
            canvas.restoreToCount(layer)
        }
        canvas.restore()
    }
    private fun drawReferenceScene(canvas: Canvas, frame: Frame, ambient: Double) {
        val arrow = paint(C.ink).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        canvas.drawLine(UI.backX - 14, UI.backY, UI.backX + 14, UI.backY, arrow)
        canvas.drawLine(UI.backX - 14, UI.backY, UI.backX - 7, UI.backY - 7, arrow)
        canvas.drawLine(UI.backX - 14, UI.backY, UI.backX - 7, UI.backY + 7, arrow)
        val logo = paint(C.ink).apply { shader = LinearGradient(140f, 0f, 262f, 0f, intArrayOf(color(0xF2F2F2), color(0xB8B8BE)), null, Shader.TileMode.CLAMP) }
        centered(canvas, IsometricScoreGaugeTheme.LOGO, UI.logoX, UI.logoY, UI.logoSize, serif, logo)
        drawGauge(canvas, frame, ambient)
        centered(canvas, IsometricScoreGaugeTheme.CAPTION, 201f, UI.captionY, 9.6f, semi, paint(C.caption), 0.12f)
        drawCards(canvas, frame, ambient)
    }
    private fun drawGauge(canvas: Canvas, frame: Frame, ambient: Double) {
        val disc = paint(C.disc).apply {
            shader = RadialGradient(Gauge.cx, Gauge.cy - 10, Gauge.radius + 30,
                intArrayOf(color(0x1A1A1E), color(C.disc), color(C.bg, 0.0)), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(Gauge.cx, Gauge.cy - 10, Gauge.radius + 30, disc)
        val bounds = RectF(Gauge.cx - Gauge.radius, Gauge.cy - Gauge.radius, Gauge.cx + Gauge.radius, Gauge.cy + Gauge.radius)
        val track = paint(C.track).apply { style = Paint.Style.STROKE; strokeWidth = Gauge.width; strokeCap = Paint.Cap.ROUND }
        canvas.drawArc(bounds, Gauge.start, Gauge.sweep, false, track)
        val end = Gauge.start + ((frame.score.coerceIn(Gauge.low, Gauge.high) - Gauge.low) / (Gauge.high - Gauge.low) * Gauge.sweep).toFloat()
        val stops = intArrayOf(0xE8344B, 0xF2552F, 0xF59A2A, 0xE9D34A, 0x7FD957, 0x2FCB72, 0x2FCB72).map { color(it) }.toIntArray()
        val positions = floatArrayOf(0f, 0.12f, 0.3f, 0.48f, 0.66f, 0.78f, 1f).map { (12 + it * Gauge.sweep) / 360 }.toFloatArray()
        val sweep = SweepGradient(Gauge.cx, Gauge.cy, stops, positions).apply {
            setLocalMatrix(Matrix().apply { setRotate(Gauge.start - 12, Gauge.cx, Gauge.cy) })
        }
        val arc = paint(0xffffff).apply { style = Paint.Style.STROKE; strokeWidth = Gauge.width; strokeCap = Paint.Cap.ROUND; shader = sweep }
        gaugeGlow.eraseColor(Color.TRANSPARENT)
        gaugeGlowCanvas.save()
        gaugeGlowCanvas.translate(165f - Gauge.cx, 165f - Gauge.cy)
        val glow = Paint(arc).apply { strokeWidth = Gauge.width * 3.4f; alpha = ((0.35 + 0.08 * sin(2 * PI * 2 * ambient / IsometricScoreGaugeMotion.LOOP)) * 255).roundToInt(); maskFilter = blur(9f) }
        gaugeGlowCanvas.drawArc(bounds, Gauge.start, end - Gauge.start, false, glow)
        gaugeGlowCanvas.restore()
        imagePaint.alpha = 255
        canvas.drawBitmap(gaugeGlow, Gauge.cx - 165f, Gauge.cy - 165f, imagePaint)
        canvas.drawArc(bounds, Gauge.start, end - Gauge.start, false, arc)
        val radians = end * PI / 180
        canvas.drawCircle(Gauge.cx + (cos(radians) * Gauge.radius).toFloat(), Gauge.cy + (sin(radians) * Gauge.radius).toFloat(), Gauge.width * 0.42f, paint(0xffffff, 0.85))
        centered(canvas, "300", Gauge.labelXs[0], Gauge.labelY, 11f, medium, paint(C.grey))
        centered(canvas, "900", Gauge.labelXs[1], Gauge.labelY, 11f, medium, paint(C.grey))
        drawNumber(canvas, frame, ambient)
    }
    private fun drawNumber(canvas: Canvas, frame: Frame, ambient: Double) {
        val value = frame.score
        val dw = digitWidth * 0.98f
        val x0 = Gauge.cx - dw * 3 / 2
        val capH = Gauge.numberSize * 0.72f
        val delta = frame.score - frame.base
        val tint = if (delta >= 0) C.up else C.down
        val amount = min(1.0, abs(delta) / 20)
        fun tinted(base: Double, shift: Int) = (IsometricScoreGaugeMotion.mix(base, ((tint shr shift) and 255) / 255.0, 0.35 * amount) * 255).roundToInt()
        val middle = Color.rgb(tinted(0.62, 16), tinted(0.62, 8), tinted(0.66, 0))
        for (digitIndex in 0..2) {
            val place = 10.0.pow(2 - digitIndex)
            val whole = value / place
            val fraction = if (digitIndex == 2) whole - floor(whole) else max(0.0, ((value % place) / place - 0.9) * 10)
            val digit = floor(whole).toInt() % 10
            val x = x0 + digitIndex * dw
            canvas.save()
            canvas.clipRect(x - 4, Gauge.numberY - capH / 2 - 6, x + dw + 4, Gauge.numberY + capH / 2 + 6)
            for (next in 0..1) {
                val text = (if (next == 0) digit else (digit + 1) % 10).toString()
                val y = Gauge.numberY + capH / 2 - fraction.toFloat() * (capH + 14) + next * (capH + 14)
                val under = paint(0x2A2A30).apply { typeface = display; textSize = Gauge.numberSize }
                canvas.drawText(text, x + 1.5f, y + 2.5f, under)
                val chrome = paint(0xffffff).apply {
                    typeface = display; textSize = Gauge.numberSize
                    shader = LinearGradient(0f, y - capH, 0f, y,
                        intArrayOf(color(0xffffff), middle, color(0xE6E6EA), color(0x8C8C93)), floatArrayOf(0f, 0.45f, 0.62f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawText(text, x, y, chrome)
                val q = (ambient % (IsometricScoreGaugeMotion.LOOP / 2)) / (IsometricScoreGaugeMotion.LOOP / 2) * 1.6 - 0.3
                val gx = (Gauge.cx - dw * 2 + q * dw * 4).toFloat()
                if (q > -0.25 && q < 1.25) {
                    chrome.shader = LinearGradient(gx - 26, y - capH, gx + 26, y,
                        intArrayOf(color(0xffffff, 0.0), color(0xffffff, 0.85), color(0xffffff, 0.0)),
                        floatArrayOf(0.3f, 0.5f, 0.7f), Shader.TileMode.CLAMP)
                    canvas.drawText(text, x, y, chrome)
                }
            }
            canvas.restore()
        }
    }
    private fun drawCards(canvas: Canvas, frame: Frame, ambient: Double) {
        for (index in IsometricScoreGaugeTheme.columns.indices) {
            val pose = IsometricScoreGaugeMotion.columnPose(index, frame.scroll)
            if (pose.x < -90 || pose.x > IsometricScoreGaugeTheme.REF_W + 90) continue
            for (row in 0..1) {
                val scenario = IsometricScoreGaugeTheme.columns[index][row]
                val lift = if (index * 2 + row == frame.sel) frame.selW else 0.0
                canvas.save()
                canvas.translate(pose.x.toFloat(), UI.rows[row] + UI.cardH / 2 - (6 * lift).toFloat())
                // Exact homography of RN perspective(520) × rotateY(angle), not an affine squash.
                val angle = pose.ang * PI / 180
                canvas.concat(Matrix().apply { setValues(floatArrayOf(cos(angle).toFloat(), 0f, 0f, 0f, 1f, 0f, (sin(angle) / 520).toFloat(), 0f, 1f)) })
                val scale = (pose.k * (1 + 0.04 * lift)).toFloat()
                canvas.scale(scale, scale)
                val tint = if (scenario.delta >= 0) C.up else C.down
                if (lift > 0.01) {
                    imagePaint.alpha = (0.45 * lift * 255).roundToInt()
                    canvas.drawBitmap(requireNotNull(cardGlows[tint]), -115.5f, -139.5f, imagePaint)
                }
                canvas.drawRoundRect(cardRect, 9f, 9f, paint(C.card))
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(cardRect, 9f, 9f, Path.Direction.CW) })
                val bottom = when (scenario.icon) { 4 -> 0x24493A; 5 -> 0x4A3426; else -> C.glow }
                val glow = paint(C.glow).apply { shader = LinearGradient(0f, UI.cardH * 0.05f, 0f, UI.cardH / 2, intArrayOf(color(C.glow, 0.0), color(bottom, 0.85)), null, Shader.TileMode.CLAMP) }
                canvas.drawRect(cardRect, glow)
                val tile = icons.width / 3
                val source = Rect(scenario.icon % 3 * tile, scenario.icon / 3 * tile, (scenario.icon % 3 + 1) * tile, (scenario.icon / 3 + 1) * tile)
                val side = UI.cardW * 0.82f
                val left = -side / 2 + 8
                val top = UI.cardH / 2 - side + 12 - (4 * lift + 5 * lift * (0.5 + 0.5 * sin(2 * PI * 4 * ambient / IsometricScoreGaugeMotion.LOOP))).toFloat()
                imagePaint.alpha = 255
                canvas.drawBitmap(icons, source, RectF(left, top, left + side, top + side), imagePaint)
                canvas.restore()
                val edge = if (lift > 0.01) paint(tint, 0.4 + 0.5 * lift) else paint(C.cardEdge)
                edge.style = Paint.Style.STROKE; edge.strokeWidth = 1f
                canvas.drawRoundRect(cardRect, 9f, 9f, edge)
                val text = paint(C.cardText, 1 - 0.35 * IsometricScoreGaugeMotion.smooth(abs(pose.ang) / 62))
                scenario.lines.forEachIndexed { line, copy -> centered(canvas, copy, 0f, -UI.cardH / 2 + 34 + line * 22, 15.5f, serif, text) }
                canvas.restore()
            }
        }
    }
}
