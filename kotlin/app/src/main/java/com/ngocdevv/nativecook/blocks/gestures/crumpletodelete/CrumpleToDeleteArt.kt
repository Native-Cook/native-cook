/** Bundled note artwork, mesh rasterization, and native Canvas composition. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import android.content.Context
import android.graphics.*
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.GX
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.GY
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.Pose
import kotlin.math.*

internal data class CrumpleToDeleteLayout(
    val w: Double,
    val h: Double,
    val top: Double,
    val bottom: Double,
) {
    val nw = min(w - 56, 352.0)
    val nh = floor(nw * 1.2 + 0.5)
    val hx = w / 2
    val hy = top + 128 + nh / 2
    val bw = 156.0
    val bh = 597 * bw / 600
    val bx = w / 2 - bw / 2
    val by = h - bottom - 20 - bh
    val bcx = bx + 315.4 * bw / 600
    val bcy = by + 157.7 * bw / 600
    val brx = 231.6 * bw / 600
    val bry = 136 * bw / 600
    val geo = CrumpleToDeleteMotion.Geo(hx, hy, bcx, bcy - 6)
}

/** Static note textures and bundled artwork; only 208 mesh vertices change each frame. */
internal class CrumpleToDeleteArt(private val context: Context) {
    private fun image(name: String) =
        context.assets.open("crumpletodelete/crumpletodelete_$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private fun font(name: String) =
        Typeface.createFromAsset(context.assets, "crumpletodelete/crumpletodelete_$name.ttf")

    private val desk = image("desk.jpg")
    private val paper = image("paper.webp")
    private val bin = image("bin.webp")
    private val hand = font("GochiHand-Regular")
    private val bold = font("Archivo-ExtraBold")
    private val medium = font("Archivo-Medium")
    private val semi = font("Archivo-SemiBold")
    private val mono = font("JetBrainsMono-Medium")
    private val drawingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var noteWidth = 0.0
    private lateinit var noteA: Bitmap
    private lateinit var noteB: Bitmap
    private lateinit var shader: BitmapShader
    private val positions = FloatArray(GX * GY * 2)
    private val textures = FloatArray(GX * GY * 2)
    private val colors = IntArray(GX * GY)
    private val indices =
        buildList<Short> {
                for (j in 0 until GY - 1) for (i in 0 until GX - 1) {
                    val a = j * GX + i
                    val tri =
                        if ((i + j) % 2 == 0)
                            intArrayOf(a, a + 1, a + GX, a + 1, a + GX + 1, a + GX)
                        else intArrayOf(a, a + 1, a + GX + 1, a, a + GX + 1, a + GX)
                    tri.forEach { add(it.toShort()) }
                }
            }
            .toShortArray()
    private val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val shadeLayerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = multiply }
    private val shadowCache = mutableMapOf<Int, Bitmap>()

    private fun rect(x: Double, y: Double, w: Double, h: Double) =
        RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    private fun paint(color: Int, alpha: Double = 1.0): Paint {
        drawingPaint.reset()
        drawingPaint.isAntiAlias = true
        drawingPaint.isFilterBitmap = true
        drawingPaint.color = color
        drawingPaint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        return drawingPaint
    }

    private fun text(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        size: Double,
        face: Typeface,
        color: Int,
        tracking: Double = 0.0,
    ) {
        val tp = paint(color)
        tp.typeface = face
        tp.textSize = size.toFloat()
        if (tracking == 0.0) canvas.drawText(text, x.toFloat(), y.toFloat(), tp)
        else {
            var xx = x
            val widths = FloatArray(text.length)
            tp.getTextWidths(text, widths)
            text.forEachIndexed { i, ch ->
                canvas.drawText(ch.toString(), xx.toFloat(), y.toFloat(), tp)
                xx += widths[i] + tracking
            }
        }
    }

    private fun makeNote(
        layout: CrumpleToDeleteLayout,
        title: String,
        lines: List<String>,
    ): Bitmap {
        val out =
            Bitmap.createBitmap(
                (layout.nw * 2).roundToInt(),
                (layout.nh * 2).roundToInt(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(out)
        canvas.scale(2f, 2f)
        // RN fit=cover crops the paper to the note'pose 1:1.2 aspect ratio.
        cover(canvas, paper, 0.0, 0.0, layout.nw, layout.nh)
        val line0 = 176 * layout.nw / 880
        val gap = 51.2 * layout.nw / 880
        text(canvas, title, layout.nw * 0.2, line0 - 6, 30.0, hand, 0xff2a2621.toInt())
        lines.forEachIndexed { i, line ->
            text(
                canvas,
                line,
                layout.nw * 0.2,
                line0 + gap * (i + 1) - 5,
                20.0,
                hand,
                if (i == 1) 0xffb7472e.toInt() else 0xff2a2621.toInt(),
            )
        }
        return out
    }

    private fun prepare(layout: CrumpleToDeleteLayout) {
        if (layout.nw == noteWidth) return
        if (::noteA.isInitialized) {
            noteA.recycle()
            noteB.recycle()
        }
        noteWidth = layout.nw
        noteA =
            makeNote(
                layout,
                "Saturday market",
                listOf(
                    "sourdough + 2 lemons",
                    "call Mai about the van!",
                    "return the library books",
                    "fix the bike light",
                    "flowers for Ana",
                ),
            )
        noteB =
            makeNote(
                layout,
                "Ideas for the trip",
                listOf("night train to the coast", "pack the film camera", "ask about the cabin"),
            )
        shader = BitmapShader(noteA, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        for (j in 0 until GY) for (i in 0 until GX) {
            val id = j * GX + i
            textures[id * 2] = (i.toDouble() / (GX - 1) * layout.nw * 2).toFloat()
            textures[id * 2 + 1] = (j.toDouble() / (GY - 1) * layout.nh * 2).toFloat()
        }
        shadowCache.values.forEach { it.recycle() }
        shadowCache.clear()
    }

    private fun cover(canvas: Canvas, b: Bitmap, x: Double, y: Double, w: Double, h: Double) {
        val scale = max(w / b.width, h / b.height)
        val sw = w / scale
        val sh = h / scale
        val src =
            Rect(
                ((b.width - sw) / 2).roundToInt(),
                ((b.height - sh) / 2).roundToInt(),
                ((b.width + sw) / 2).roundToInt(),
                ((b.height + sh) / 2).roundToInt(),
            )
        canvas.drawBitmap(b, src, rect(x, y, w, h), paint(Color.WHITE))
    }

    /** Software-rasterized blur masks work on the hardware Canvas on API 26 too. */
    private fun shadow(
        canvas: Canvas,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        r: Double,
        blur: Double,
        alpha: Double,
        key: Int,
    ) {
        if (alpha <= 0) return
        val pad = ceil(blur * 3).toInt()
        val image =
            shadowCache.getOrPut(key) {
                Bitmap.createBitmap(
                        ceil(w + 2 * pad).toInt().coerceAtLeast(1),
                        ceil(h + 2 * pad).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    .also {
                        val pp =
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xff3c2814.toInt()
                                maskFilter =
                                    BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL)
                            }
                        Canvas(it)
                            .drawRoundRect(
                                rect(pad.toDouble(), pad.toDouble(), w, h),
                                r.toFloat(),
                                r.toFloat(),
                                pp,
                            )
                    }
            }
        canvas.drawBitmap(
            image,
            null,
            rect(x - pad, y - pad, w + 2 * pad, h + 2 * pad),
            paint(Color.WHITE, alpha),
        )
    }

    private fun mesh(canvas: Canvas) {
        val texturePaint = paint(Color.WHITE)
        texturePaint.shader = shader
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            positions.size,
            positions,
            0,
            textures,
            0,
            null,
            0,
            indices,
            0,
            indices.size,
            texturePaint,
        )
        // Resolve overlapping facets with SrcOver first, then multiply the final shade once.
        // Multiplying each triangle directly into the paper would darken folded overlaps
        // repeatedly.
        val layer = canvas.saveLayer(null, shadeLayerPaint)
        val shadePaint = paint(Color.WHITE)
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            positions.size,
            positions,
            0,
            null,
            0,
            colors,
            0,
            indices,
            0,
            indices.size,
            shadePaint,
        )
        canvas.restoreToCount(layer)
    }

    /** Draws a complete frame, rebuilding the paper vertices from the supplied live pose. */
    fun draw(
        canvas: Canvas,
        layout: CrumpleToDeleteLayout,
        pose: Pose,
        density: Float,
        fontScale: Float,
        pressed: Boolean,
    ) {
        prepare(layout)
        canvas.save()
        canvas.scale(density, density)
        cover(canvas, desk, 0.0, 0.0, layout.w, layout.h)
        canvas.drawRect(rect(0.0, 0.0, layout.w, layout.h), paint(0xff281e14.toInt(), 0.06))
        canvas.save()
        canvas.translate(layout.hx.toFloat(), (layout.hy + 6).toFloat())
        canvas.rotate((0.035 * 180 / PI).toFloat())
        shadow(
            canvas,
            -layout.nw / 2 + 4,
            -layout.nh / 2 + 8,
            layout.nw - 8,
            layout.nh,
            6.0,
            10.0,
            0.28,
            -1,
        )
        canvas.drawBitmap(
            noteB,
            null,
            rect(-layout.nw / 2, -layout.nh / 2, layout.nw, layout.nh),
            paint(Color.WHITE),
        )
        canvas.restore()
        CrumpleToDeleteMotion.crumple(
            pose.c.coerceIn(0.0, 1.0),
            pose.spin,
            pose.sc * (1 - 0.25 * pose.sink) * (1 + 0.025 * pose.lift),
            pose.x,
            pose.y + 26 * pose.sink,
            layout.nw,
            layout.nh,
            positions,
            colors,
        )
        canvas.save()
        canvas.rotate(
            (pose.wobble * 0.06 * 180 / PI).toFloat(),
            layout.bcx.toFloat(),
            (layout.by + layout.bh).toFloat(),
        )
        // Bin shadow is an oval, rasterized once to retain the soft desk contact.
        val ovalShadow =
            shadowCache.getOrPut(-2) {
                Bitmap.createBitmap(188, 74, Bitmap.Config.ARGB_8888).also {
                    val pp =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xff322314.toInt()
                            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                        }
                    Canvas(it).drawOval(RectF(24f, 24f, 164f, 50f), pp)
                }
            }
        canvas.drawBitmap(
            ovalShadow,
            (layout.bx + 8 - 24).toFloat(),
            (layout.by + layout.bh - 18 - 24).toFloat(),
            paint(Color.WHITE, 0.35),
        )
        canvas.drawBitmap(
            bin,
            null,
            rect(layout.bx, layout.by, layout.bw, layout.bh),
            paint(Color.WHITE),
        )
        if (pose.sink > 0.02) {
            canvas.save()
            val clip =
                Path().apply {
                    addOval(
                        rect(
                            layout.bcx - layout.brx * 0.94,
                            layout.bcy + 2 - layout.bry * 0.9,
                            layout.brx * 1.88,
                            layout.bry * 1.8,
                        ),
                        Path.Direction.CW,
                    )
                }
            canvas.clipPath(clip)
            mesh(canvas)
            canvas.drawRect(
                rect(layout.bx, layout.by, layout.bw, layout.bh),
                paint(0xff0a0a0a.toInt(), 0.25),
            )
            canvas.restore()
        }
        canvas.restore()
        val cc = pose.c.coerceIn(0.0, 1.0)
        val e = cc * cc * (3 - 2 * cc)
        val sw = (layout.nw + (88 - layout.nw) * e) * pose.sc
        val sh = (layout.nh + (70 - layout.nh) * e) * pose.sc
        val sy = pose.y + 10 + pose.lift * 10 + (1 - e) * 4
        val a =
            (0.32 - 0.12 * pose.lift) *
                (1 - pose.sink) *
                if (pose.toast > 0.01 && pose.sink > 0.5) 0 else 1
        // Quantize only the cached blur artwork, never the moving paper or its trajectory.
        val key = (cc * 24).roundToInt() * 5 + (pose.lift * 4).roundToInt()
        shadow(
            canvas,
            pose.x - sw / 2 + 4,
            sy - sh / 2,
            sw,
            sh,
            6 + 40 * e,
            10 + 12 * pose.lift,
            a,
            key,
        )
        if (pose.sink <= 0.02) mesh(canvas)
        val smallScale = min(fontScale.toDouble(), 1.3)
        val titleScale = min(fontScale.toDouble(), 1.2)
        // Baselines follow RN text'pose line boxes and bundled font metrics.
        val eyebrowSize = 11 * smallScale
        val titleSize = 36 * titleScale
        val subSize = 15 * smallScale
        text(
            canvas,
            "NOTES · TODAY",
            24.0,
            layout.top + 12 + eyebrowSize,
            eyebrowSize,
            mono,
            0xff6f665c.toInt(),
            1.6,
        )
        val eyebrowHeight = 14 * smallScale
        val titleTop = layout.top + 12 + eyebrowHeight + 8
        text(canvas, "Notes", 24.0, titleTop + titleSize, titleSize, bold, 0xff2c2722.toInt(), -0.7)
        text(
            canvas,
            "Pull a note into the bin to delete it",
            24.0,
            titleTop + 40 * titleScale + 2 + subSize,
            subSize,
            medium,
            0xff6f665c.toInt(),
        )
        if (pose.toast > 0.001) {
            canvas.saveLayerAlpha(null, (pose.toast * 255).roundToInt())
            val ty = layout.top + 96 + 16 * (1 - pose.toast)
            canvas.drawRoundRect(
                rect(20.0, ty, layout.w - 40, 52.0),
                26f,
                26f,
                paint(0xff221e1a.toInt(), 0.92),
            )
            val pp = paint(Color.WHITE)
            pp.typeface = semi
            pp.textSize = (15 * fontScale).toFloat()
            val uw = pp.measureText("Undo").toDouble() + 32
            val ux = layout.w - 28 - uw
            canvas.save()
            if (pressed) canvas.scale(0.96f, 0.96f, (ux + uw / 2).toFloat(), (ty + 26).toFloat())
            canvas.drawRoundRect(
                rect(ux, ty + 8, uw, 36.0),
                18f,
                18f,
                paint(Color.WHITE, if (pressed) 0.22 else 0.14),
            )
            text(
                canvas,
                "Undo",
                ux + 16,
                ty + 26 + 5 * fontScale,
                15.0 * fontScale,
                semi,
                0xffffd7a3.toInt(),
            )
            canvas.restore()
            text(
                canvas,
                "Note deleted",
                40.0,
                ty + 26 + 5 * fontScale,
                15.0 * fontScale,
                semi,
                0xfff6efe6.toInt(),
            )
            canvas.restore()
        }
        canvas.restore()
    }

    fun undoWidth(fontScale: Float): Double {
        drawingPaint.typeface = semi
        drawingPaint.textSize = 15 * fontScale
        return drawingPaint.measureText("Undo") + 32.0
    }

    fun close() {
        desk.recycle()
        paper.recycle()
        bin.recycle()
        if (::noteA.isInitialized) {
            noteA.recycle()
            noteB.recycle()
        }
        shadowCache.values.forEach { it.recycle() }
        shadowCache.clear()
    }
}
