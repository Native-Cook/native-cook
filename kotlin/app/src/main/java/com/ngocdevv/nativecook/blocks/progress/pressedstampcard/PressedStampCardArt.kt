package com.ngocdevv.nativecook.blocks.progress.pressedstampcard

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class PressedStampCardArt(context: Context) {
    private val assets = context.assets
    private fun bitmap(name: String) = assets.open("pressedstampcard/pressedstampcard_$name.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private fun font(name: String) = Typeface.createFromAsset(assets, "pressedstampcard/pressedstampcard_$name.ttf")
    private val regular = font("PSCInter_Regular")
    private val inter = font("PSCInter_SemiBold")
    private val serif = font("PSCSerif_SemiBold")
    private val ringFont = font("PSCSerif_Bold")
    private val paper = bitmap("ps_paper")
    private val leather = bitmap("ps_leather")
    private val sourceArt = bitmap("ps_seal_art")
    private val dieBase = bitmap("ps_die_base")
    private val dieKnob = bitmap("ps_die_knob")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val inkAtlas = makeInkAtlas()
    private val material = if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(PressedStampCardShaders.RELIEF).apply {
        setFloatUniform("uP3", 0f)
        setInputShader("uArt", imageShader(inkAtlas, 56f, Shader.TileMode.CLAMP))
        setInputShader("uGrain", imageShader(leather, 128f, Shader.TileMode.REPEAT))
    } }.getOrNull() else null
    private val paperShader = if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(PressedStampCardShaders.PAPER).apply {
        setFloatUniform("uP3", 0f); setInputShader("uPaper", imageShader(paper, 256f, Shader.TileMode.REPEAT))
    } }.getOrNull() else null
    private val iconPaths = mapOf(
        "cup" to "M4 4.5Q4 3 5.5 3h13Q20 3 20 4.5T18.5 6h-13Q4 6 4 4.5ZM5.5 7l2 14h9l2-14M6.5 12h11l-.6 4H7.1",
        "home" to "M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9",
        "card" to "M3 6h18v14H3V6ZM3 10h18M6 15h4",
        "gift" to "M3 9h18v5H3V9ZM5 14v7h14v-7M12 9v12M12 9C4 9 5 2 8 3c2 0 4 6 4 6ZM12 9s2-6 4-6c3-1 4 6-4 6Z",
        "person" to "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2",
        "qr" to "M3 3h6v6H3V3ZM15 3h6v6h-6V3ZM3 15h6v6H3v-6ZM15 15h2v2h-2v-2ZM20 14v3M14 20h3v-2M20 20h1v1",
    ).mapValues { PathParser.createPathFromPathData(it.value) }

    fun draw(canvas: Canvas, height: Float, tabsY: Float, f: PressedStampCardFrame, magnet: Int, pull: Double) {
        fill.color = Color.WHITE; fill.alpha = 255; fill.shader = paperShader
        if (paperShader != null) canvas.drawRect(0f, 0f, 402f, height, fill) else {
            fill.color = 0xFFF4EDE0.toInt(); canvas.drawRect(0f, 0f, 402f, height, fill)
        }
        fill.shader = null
        canvas.save(); canvas.scale(f.push.toFloat(), f.push.toFloat(), 311f, 326f)
        label(canvas, "Your card", 20f, 96f, 33f, INK, serif)
        drawCard(canvas, f, magnet, pull)
        drawButton(canvas, f)
        drawOrders(canvas, f)
        drawDie(canvas, f)
        canvas.restore()
        drawTabs(canvas, tabsY)
    }
    private fun drawCard(canvas: Canvas, f: PressedStampCardFrame, magnet: Int, pull: Double) {
        canvas.save(); canvas.translate(20f, 118f + f.dip.toFloat() + pull.toFloat() * .2f)
        val camera = Camera(); val matrix = Matrix()
        camera.save(); camera.rotateX((f.tilt + f.flip).toFloat()); camera.getMatrix(matrix); camera.restore()
        matrix.preTranslate(-181f, -172f); matrix.postTranslate(181f, 172f); canvas.concat(matrix)
        fill.shader = null; fill.color = 0x2E5A4326; fill.maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(RectF(0f, 18f, 362f, 362f), 24f, 24f, fill); fill.maskFilter = null
        fill.color = 0x4D31200C; fill.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(RectF(0f, 2f, 362f, 346f), 24f, 24f, fill); fill.maskFilter = null
        val shader = material
        if (shader != null && Build.VERSION.SDK_INT >= 33) {
            shader.setFloatUniform("uLight", f.light.toFloat()); shader.setFloatUniform("uElevation", 40f)
            shader.setFloatUniform("uDent", f.dent.toFloat()); shader.setFloatUniform("uSweep", f.sweep.toFloat())
            shader.setFloatUniform("uPhase", f.phase.toFloat()); shader.setFloatUniform("uNext", if (f.count < 8) f.count.toFloat() else -1f)
            shader.setFloatUniform("uFlipFlash", f.flipFlash.toFloat()); shader.setFloatUniform("uGold", f.gold.toFloat()); shader.setFloatUniform("uMagnet", magnet.toFloat())
            shader.setFloatUniform("uRipple", f.ripple.map { it.toFloat() }.toFloatArray())
            shader.setFloatUniform("uSeals", f.seals.flatMap { listOf(it.x.toFloat(), it.y.toFloat(), it.radius.toFloat(), it.alpha.toFloat()) }.toFloatArray())
            shader.setFloatUniform("uDetail", f.seals.flatMap { listOf(it.rotation.toFloat(), it.gold.toFloat(), it.flash.toFloat(), it.lift.toFloat()) }.toFloatArray())
            fill.color = Color.WHITE; fill.shader = shader; canvas.drawRect(0f, 0f, 362f, 344f, fill); fill.shader = null
        } else drawFallbackCard(canvas, f)
        label(canvas, "STAMP CARD", 20f, 36f, 13f, 0xFFBFA783.toInt(), inter, tracking = .14f)
        canvas.save(); canvas.clipRect(264f, 12f, 348f, 52f)
        val countColor = if (f.gold > .4) 0xFFE8C272.toInt() else 0xFFC79A5B.toInt()
        if (f.oldCount != f.count && f.roll < 1) label(canvas, "${f.oldCount}/8", 342f, (42 - 40 * f.roll).toFloat(), 29f, countColor, serif, Paint.Align.RIGHT, alpha = 1 - f.roll)
        label(canvas, "${f.count}/8", 342f, (42 + 40 * (1 - f.roll)).toFloat(), 29f, countColor, serif, Paint.Align.RIGHT, alpha = f.roll)
        canvas.restore()
        for (i in 0..7) if (f.seals[i].alpha < .1) label(canvas, "%02d".format(i + 1), PressedStampCardMotion.wellX(i).toFloat(), PressedStampCardMotion.wellY(i).toFloat() + 6, 16f, 0xFF8A6A40.toInt(), serif, Paint.Align.CENTER)
        val sentence = if (f.count == 8) "Your free coffee is ready" else "${8 - f.count} more ${if (f.count == 7) "stamp" else "stamps"} until your free coffee"
        label(canvas, sentence, 20f, 304f, 15f, 0xFFCDBB9E.toInt(), regular)
        canvas.restore()
    }
    private fun drawFallbackCard(canvas: Canvas, f: PressedStampCardFrame) {
        fill.shader = LinearGradient(0f, 0f, 362f, 344f, intArrayOf(0xFF3A2610.toInt(), 0xFF31200C.toInt(), 0xFF1F1508.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, 362f, 344f), 24f, 24f, fill); fill.shader = null
        for (i in 0..7) {
            val x = PressedStampCardMotion.wellX(i).toFloat(); val y = PressedStampCardMotion.wellY(i).toFloat()
            fill.color = 0xFF150D04.toInt(); canvas.drawCircle(x, y, 36f, fill)
            stroke.color = 0xFF7A5730.toInt(); stroke.strokeWidth = 1f; canvas.drawCircle(x, y, 35.3f, stroke)
            val seal = f.seals[i]; if (seal.alpha <= 0) continue
            canvas.save(); canvas.translate(seal.x.toFloat(), seal.y.toFloat()); canvas.rotate(seal.rotation.toFloat())
            fill.color = if (seal.gold > .5) 0xFFE8C272.toInt() else 0xFFEFE6D2.toInt(); canvas.drawCircle(0f, 0f, 34f, fill)
            fill.color = Color.WHITE; canvas.drawBitmap(inkAtlas, null, RectF(-34f, -34f, 34f, 34f), fill); canvas.restore()
        }
    }
    private fun drawButton(canvas: Canvas, f: PressedStampCardFrame) {
        fill.color = 0xFF2A1B0C.toInt()
        if (f.buttonGold > 0) fill.shader = LinearGradient(20f, 478f, 382f, 534f, intArrayOf(0xFF6B4A14.toInt(), 0xFFD9A441.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(20f, 478f, 382f, 534f), 18f, 18f, fill); fill.shader = null
        stroke.color = 0xFF614A2D.toInt(); stroke.strokeWidth = 1f; canvas.drawRoundRect(RectF(20f, 478f, 382f, 534f), 18f, 18f, stroke)
        canvas.save(); canvas.clipRect(40f, 494f, 362f, 518f)
        if (f.buttonRoll < 1) {
            icon(canvas, "qr", 92f, (495 - 24 * f.buttonRoll).toFloat(), 21f, 0xFFF8F1E4.toInt())
            label(canvas, "Scan at the counter", 221f, (512 - 24 * f.buttonRoll).toFloat(), 16f, 0xFFF8F1E4.toInt(), inter, Paint.Align.CENTER, alpha = 1 - f.buttonRoll)
        }
        if (f.buttonRoll > 0) label(canvas, "Redeem free coffee", 201f, (512 + 24 * (1 - f.buttonRoll)).toFloat(), 16f, INK, inter, Paint.Align.CENTER, alpha = f.buttonRoll)
        canvas.restore()
        if (f.redeemRing > 0) { stroke.color = Color.WHITE; stroke.alpha = (102 * (1 - f.redeemRing)).roundToInt(); stroke.strokeWidth = 2f; canvas.drawCircle(201f, 506f, (15 * (1 + f.redeemRing * .6)).toFloat(), stroke); stroke.alpha = 255 }
    }
    private fun drawOrders(canvas: Canvas, f: PressedStampCardFrame) {
        label(canvas, "Recent orders", 20f, 581f, 26f, INK, serif)
        label(canvas, "View all", 382f, 579f, 14f, 0xFF91622B.toInt(), inter, Paint.Align.RIGHT)
        canvas.save(); canvas.clipRect(20f, 610f, 382f, 793f)
        for (j in 1..8) {
            val y = (610 + (f.orders - j) * 76).toFloat()
            if (y < 540 || y > 793) continue
            fill.color = 0xFFFBF6EA.toInt(); canvas.drawRoundRect(RectF(20f, y, 382f, y + 68), 20f, 20f, fill)
            stroke.color = 0xFFEADFC8.toInt(); stroke.strokeWidth = 1f; canvas.drawRoundRect(RectF(20f, y, 382f, y + 68), 20f, 20f, stroke)
            fill.color = 0xFFEDE2C9.toInt(); canvas.drawCircle(54f, y + 33, 22f, fill); icon(canvas, "cup", 43f, y + 22, 22f, 0xFF8A6A3A.toInt())
            val names = arrayOf("Doppio", "Oat latte", "Espresso", "Latte macchiato", "Americano", "Flat white", "Cortado", "Cappuccino")
            val times = arrayOf("4 days ago", "3 days ago", "2 days ago", "Yesterday · 08:40", "Today · 09:41", "Just now", "Just now", "Just now")
            val prices = arrayOf("€2.80", "€4.20", "€2.60", "€3.80", "€3.00", "€3.90", "€3.40", "€3.60")
            label(canvas, names[j - 1], 90f, y + 28, 16f, INK, inter)
            label(canvas, times[j - 1], 90f, y + 52, 13f, 0xFF817361.toInt(), regular)
            label(canvas, prices[j - 1], 366f, y + 28, 16f, INK, inter, Paint.Align.RIGHT)
            val active = j == f.target + 1 && f.event >= 0
            if (!active || f.event < .25) label(canvas, "+1 stamp", 366f, y + 52, 13f, 0xFF91622B.toInt(), inter, Paint.Align.RIGHT)
        }
        canvas.restore()
    }
    private fun drawDie(canvas: Canvas, f: PressedStampCardFrame) {
        val die = f.fly; if (die.alpha <= 0) return
        val x = die.x.toFloat() + 20; val y = die.y.toFloat() + 118 + f.dip.toFloat(); val r = die.radius.toFloat()
        canvas.save(); canvas.translate(x, y); canvas.rotate(die.rotation.toFloat()); canvas.scale(r / 55, r / 55)
        fill.color = 0x660C0703; fill.maskFilter = BlurMaskFilter((3 + 27 * die.lift).toFloat(), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle((18 * die.lift).toFloat(), (2 + 24 * die.lift).toFloat(), 55f, fill); fill.maskFilter = null
        fill.color = Color.WHITE; canvas.drawBitmap(dieBase, null, RectF(-55f, -55f, 55f, 55f), fill)
        if (f.dieGlow > 0) { stroke.color = 0xFFFF9A3C.toInt(); stroke.alpha = (255 * (f.dieGlow / .7).coerceIn(0.0, 1.0)).toInt(); stroke.strokeWidth = 2f; canvas.drawCircle(0f, 0f, 54f, stroke); stroke.alpha = 255 }
        val off = (f.dieTilt / 8 * 9).toFloat(); canvas.drawBitmap(dieKnob, null, RectF(-31.9f + off, -31.9f - off, 31.9f + off, 31.9f - off), fill)
        canvas.restore()
        if (f.target >= 0 && f.event >= .25 && f.event < if (f.target == 7) 1.14 else .96) {
            val p = PressedStampCardMotion.out(PressedStampCardMotion.segment(f.event, .25, .37))
            val tx = PressedStampCardMotion.mix(340.0, x + off.toDouble(), p).toFloat()
            val ty = PressedStampCardMotion.mix(654.4 + (f.orders - f.target - 1) * 76, y - off.toDouble(), p).toFloat() - 6 * sin(PI * PressedStampCardMotion.segment(f.event, .25, .37)).toFloat()
            label(canvas, "+1 stamp", tx, ty, (13 * (1 - .2 * p)).toFloat(), 0xFFFFF1DC.toInt(), inter, Paint.Align.CENTER)
        }
    }
    private fun drawTabs(canvas: Canvas, y: Float) {
        fill.color = 0xFFFBF6EA.toInt(); canvas.drawRoundRect(RectF(16f, y, 386f, y + 64), 32f, 32f, fill)
        stroke.color = 0xFFEADFC8.toInt(); stroke.strokeWidth = 1f; canvas.drawRoundRect(RectF(16f, y, 386f, y + 64), 32f, 32f, stroke)
        val names = arrayOf("Home", "Card", "Rewards", "Account"); val icons = arrayOf("home", "card", "gift", "person")
        for (i in 0..3) {
            val x = 24f + 88.5f * i
            if (i == 1) { fill.color = 0xFFEADFC8.toInt(); canvas.drawRoundRect(RectF(x, y + 8, x + 88.5f, y + 56), 24f, 24f, fill) }
            icon(canvas, icons[i], x + 33.75f, y + 12, 21f, INK)
            label(canvas, names[i], x + 44.25f, y + 48, 13f, INK, inter, Paint.Align.CENTER)
        }
    }
    private fun icon(canvas: Canvas, name: String, x: Float, y: Float, size: Float, color: Int) {
        val path = iconPaths[name] ?: return
        canvas.save(); canvas.translate(x, y); canvas.scale(size / 24, size / 24)
        stroke.color = color; stroke.strokeWidth = 2f; canvas.drawPath(path, stroke); canvas.restore()
    }
    private fun label(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT, tracking: Float = 0f, alpha: Double = 1.0) {
        text.typeface = face; text.textSize = size; text.color = color; text.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt(); text.textAlign = align; text.letterSpacing = tracking
        canvas.drawText(value, x, y, text); text.alpha = 255; text.letterSpacing = 0f
    }
    private fun imageShader(bitmap: Bitmap, size: Float, mode: Shader.TileMode): BitmapShader {
        return BitmapShader(bitmap, mode, mode).apply { setLocalMatrix(Matrix().apply { setScale(size / bitmap.width, size / bitmap.height) }) }
    }
    private fun makeInkAtlas(): Bitmap {
        val bitmap = Bitmap.createBitmap(336, 336, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); canvas.scale(6f, 6f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E1F0E.toInt(); typeface = ringFont; textSize = 5.6f; isSubpixelText = true }
        canvas.drawBitmap(sourceArt, null, RectF(0f, 0f, 56f, 56f), Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        fun arc(value: String, upper: Boolean) {
            val radius = if (upper) 20.8 else 24.4; val widths = value.map { paint.measureText(it.toString()).toDouble() }
            var pen = -(widths.sum() + .25 * (value.length - 1)) / 2
            value.forEachIndexed { i, glyph ->
                val a = (pen + widths[i] / 2) / radius; val theta = if (upper) -PI / 2 + a else PI / 2 - a
                canvas.save(); canvas.translate((28 + cos(theta) * radius).toFloat(), (28 + sin(theta) * radius).toFloat())
                canvas.rotate(Math.toDegrees(if (upper) theta + PI / 2 else theta - PI / 2).toFloat())
                canvas.drawText(glyph.toString(), (-widths[i] / 2).toFloat(), 0f, paint); canvas.restore(); pen += widths[i] + .25
            }
        }
        arc("FRESHLY ROASTED", true); arc("COFFEE CLUB", false)
        return bitmap
    }
    companion object { private const val INK = 0xFF2A1B0C.toInt() }
}
