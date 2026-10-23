package com.ngocdevv.nativecook.blocks.charts.spiketostairchart

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Static blur masks are rasterized once; only trims, transforms and colour change per frame. */
internal class ChartRenderer(context: Context) {
    private val assets = context.assets
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val regular =
        Typeface.createFromAsset(context.assets, "spiketostairchart/ArchivoExpanded-Regular.ttf")
    private val black =
        Typeface.createFromAsset(context.assets, "spiketostairchart/ArchivoExpanded-Black.ttf")

    private fun bitmap(name: String): Bitmap =
        assets.open("spiketostairchart/$name").use { BitmapFactory.decodeStream(it) }

    private val shards = bitmap("shards.png")
    private val glints = bitmap("floor-glints.png")
    private val streaks =
        bitmap("floor-streaks.png").let { original ->
            // Skia multiply at opacity .62 is dst * (.38 + .62 * texture).
            // PorterDuff MULTIPLY is modulation, so keep this texture opaque on every API.
            val result =
                Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val texturePaint =
                Paint().apply {
                    colorFilter =
                        ColorMatrixColorFilter(
                            floatArrayOf(
                                .62f,
                                0f,
                                0f,
                                0f,
                                255f * .38f,
                                0f,
                                .62f,
                                0f,
                                0f,
                                255f * .38f,
                                0f,
                                0f,
                                .62f,
                                0f,
                                255f * .38f,
                                0f,
                                0f,
                                0f,
                                0f,
                                255f,
                            )
                        )
                }
            Canvas(result).drawBitmap(original, 0f, 0f, texturePaint)
            original.recycle()
            result
        }
    private val streakShader =
        BitmapShader(streaks, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(.6f, .6f)
                    postTranslate(24f, 522f)
                }
            )
        }
    private val floorFade =
        LinearGradient(
            0f,
            522f,
            0f,
            770f,
            intArrayOf(0, 0x38000000, 0xAD000000.toInt(), Color.BLACK),
            floatArrayOf(0f, 90f / 248, 180f / 248, 1f),
            Shader.TileMode.CLAMP,
        )
    private val paths = ChartGeometry.curves.map { path(it.points) }
    private val measures = paths.map { PathMeasure(it, false) }
    private val pieces = ChartGeometry.pieces.map { path(it.points) }
    private val trim = Path()
    private val liveBloom = if (Build.VERSION.SDK_INT >= 31) ChartLiveBloom() else null
    private val axes =
        Path().apply {
            moveTo(64f, 236f)
            lineTo(64f, 510f)
            lineTo(338f, 510f)
        }
    private val glyphs =
        listOf(
            Path().apply {
                moveTo(-12f, 7f)
                lineTo(-6f, -7f)
                lineTo(0f, 3f)
                lineTo(5f, -2f)
                lineTo(12f, 7f)
            },
            Path().apply {
                moveTo(-12f, 7f)
                lineTo(-6f, 7f)
                lineTo(-6f, 3f)
                lineTo(0f, 3f)
                lineTo(0f, -1f)
                lineTo(6f, -1f)
                lineTo(6f, -5f)
                lineTo(9f, -5f)
                lineTo(12f, -7f)
            },
        )
    private val grid =
        Path().apply {
            for (i in 1..13) {
                val n = i * 274f / 14
                moveTo(64 + n, 236f)
                lineTo(64 + n, 510f)
                moveTo(64f, 236 + n)
                lineTo(338f, 236 + n)
            }
        }
    private val dots =
        Path().apply {
            for (i in 0..89) addCircle(
                (64 + (.13 + .1 * (i % 9)) * 274).toFloat(),
                (510 - (.04 + .1 * (i / 9)) * 274).toFloat(),
                .9f,
                Path.Direction.CW,
            )
        }
    private val arrow =
        Path().apply {
            moveTo(0f, 0f)
            lineTo(-9f, -4f)
            lineTo(-7f, 0f)
            lineTo(-9f, 4f)
            close()
        }
    private val hotAxis =
        Path().apply {
            moveTo(64f, 510f)
            lineTo(326.218f, 510f)
        }
    private val hotMeasure = PathMeasure(hotAxis, false)
    private val areaPaths =
        (0..1).map { d ->
            val limit = if (d == 1) .968 else .957
            val last = atU(d, limit)
            path(ChartGeometry.curves[d].points.filter { it.u <= limit } + last).apply {
                lineTo(last.x.toFloat(), 510f)
                lineTo(64f, 510f)
                close()
            }
        }
    private val glow = paths.map { tubeGlow(it, false) }
    private val mirrorGlow = paths.map { tubeGlow(it, true) }
    private val pieceGlow = pieces.map { tubeGlow(it, false) }
    private val pieceMirrorGlow = pieces.map { tubeGlow(it, true) }
    private val contact = paths.map { mask(it, 5f, 4f) }
    private val sparkGlow =
        mask(Path().apply { addCircle(0f, 0f, 2.25f, Path.Direction.CW) }, 0f, 2f)
    private val axisGlow = mask(axes, 4f, 4f)
    private val axisMirrorGlow = mask(axes, 3f, 3f)
    private val heatGlow = tubeGlow(hotAxis, false)
    private val heatMirrorGlow = tubeGlow(hotAxis, true)
    private val glyphGlow = glyphs.map { mask(it, 1.75f, 2.5f) }
    private val labels = listOf(labelPath("MOTIVATION"), labelPath("DISCIPLINE"))
    private val labelWide = labels.map { mask(it, 0f, 14f) }
    private val labelTight = labels.map { mask(it, 0f, 6f) }
    private val thumbPath =
        Path().apply { addRoundRect(0f, 0f, 62f, 36f, 18f, 18f, Path.Direction.CW) }
    private val thumbGlow = mask(thumbPath, 0f, 8f)
    private val curtain = (0..1).map { d -> curtainMask(d, false) }
    private val curtainMirror = (0..1).map { d -> curtainMask(d, true) }
    private var tint = Color.WHITE
    private var core = Color.WHITE
    private var tintFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)

    private data class Sprite(val bitmap: Bitmap, val bounds: RectF)

    private fun path(points: List<ChartSample>) =
        Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat())
                else lineTo(p.x.toFloat(), p.y.toFloat())
            }
        }

    private fun fresh(color: Int = Color.WHITE, alpha: Double = 1.0): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = color
        paint.alpha = (Color.alpha(color) * clamp(alpha)).roundToInt()
        return paint
    }

    private fun stroke(p: Path, c: Canvas, color: Int, width: Double, alpha: Double = 1.0) {
        fresh(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(p, paint)
    }

    private fun raster(bounds: RectF, draw: (Canvas) -> Unit): Sprite {
        val b =
            Bitmap.createBitmap(
                ceil(bounds.width() * 2).toInt().coerceAtLeast(1),
                ceil(bounds.height() * 2).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val c = Canvas(b)
        c.scale(2f, 2f)
        c.translate(-bounds.left, -bounds.top)
        draw(c)
        return Sprite(b, bounds)
    }

    private fun mask(path: Path, width: Float, sigma: Float): Sprite {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        bounds.inset(-(sigma * 3 + width + 2), -(sigma * 3 + width + 2))
        return raster(bounds) { c ->
            fresh()
            if (width > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = width
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            paint.maskFilter =
                BlurMaskFilter(
                    ((sigma - .5f) / .57735f).coerceAtLeast(.01f),
                    BlurMaskFilter.Blur.NORMAL,
                )
            c.drawPath(path, paint)
        }
    }

    private fun tubeGlow(path: Path, mirror: Boolean): Sprite {
        val wide = mask(path, if (mirror) 14f else 10f, if (mirror) 16f else 12f)
        val tight = mask(path, if (mirror) 6f else 4.5f, if (mirror) 6f else 2.5f)
        val result =
            raster(wide.bounds) { c ->
                sprite(c, wide, Color.WHITE, if (mirror) .4 else .42)
                sprite(c, tight, Color.WHITE, if (mirror) .7 else .9)
            }
        wide.bitmap.recycle()
        tight.bitmap.recycle()
        return result
    }

    private fun sprite(c: Canvas, sprite: Sprite, color: Int, alpha: Double = 1.0) {
        if (alpha <= 0) return
        fresh(Color.WHITE, min(1.0, alpha))
        paint.colorFilter =
            if (alpha > 1) {
                ColorMatrixColorFilter(
                    floatArrayOf(
                        Color.red(color) / 255f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        Color.green(color) / 255f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        Color.blue(color) / 255f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        (alpha * Color.alpha(color) / 255).toFloat(),
                        0f,
                    )
                )
            } else if (color == tint) tintFilter
            else PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(sprite.bitmap, null, sprite.bounds, paint)
    }

    private fun curtainMask(d: Int, mirror: Boolean): Sprite =
        raster(RectF(64f, 236f, 338f, 510f)) { c ->
            c.clipPath(areaPaths[d])
            val vertical =
                LinearGradient(
                    0f,
                    236f,
                    0f,
                    510f,
                    intArrayOf(0x4DFFFFFF, 0x26FFFFFF),
                    null,
                    Shader.TileMode.CLAMP,
                )
            val horizontal =
                if (mirror && d == 1)
                    LinearGradient(
                        64f,
                        0f,
                        338f,
                        0f,
                        intArrayOf(0x73FFFFFF, Color.WHITE, 0, 0),
                        floatArrayOf(0f, (274 * .968f - 10) / 274, .968f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                else
                    LinearGradient(
                        64f,
                        0f,
                        338f,
                        0f,
                        0x73FFFFFF,
                        Color.WHITE,
                        Shader.TileMode.CLAMP,
                    )
            fresh()
            paint.shader = ComposeShader(vertical, horizontal, PorterDuff.Mode.DST_IN)
            c.drawRect(64f, 236f, 338f, 510f, paint)
        }

    private fun labelPath(word: String): Path {
        fresh()
        paint.typeface = regular
        paint.textSize = 15f
        paint.isSubpixelText = true
        val advances = FloatArray(word.length)
        paint.getTextWidths(word, advances)
        var x = 201 - (advances.sum() + 2.6f * (word.length - 1)) / 2
        val result = Path()
        for (i in word.indices) {
            val glyph = Path()
            paint.getTextPath(word, i, i + 1, x, 204f, glyph)
            result.addPath(glyph)
            x += advances[i] + 2.6f
        }
        return result
    }

    private fun radial(
        c: Canvas,
        x: Double,
        y: Double,
        radius: Double,
        colors: IntArray,
        stops: FloatArray? = null,
        alpha: Double = 1.0,
    ) {
        if (alpha <= 0 || radius <= 0) return
        fresh(Color.WHITE, alpha)
        paint.shader =
            RadialGradient(
                x.toFloat(),
                y.toFloat(),
                radius.toFloat(),
                colors,
                stops,
                Shader.TileMode.CLAMP,
            )
        c.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
    }

    private fun gradient(
        c: Canvas,
        rect: RectF,
        colors: IntArray,
        alpha: Double = 1.0,
        endY: Float = rect.bottom,
    ) {
        fresh(Color.WHITE, alpha)
        paint.shader = LinearGradient(0f, rect.top, 0f, endY, colors, null, Shader.TileMode.CLAMP)
        c.drawRect(rect, paint)
    }

    private fun color(r: Double, g: Double, b: Double, a: Double = 1.0) =
        Color.argb(
            (clamp(a) * 255).roundToInt(),
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255),
        )

    private fun alpha(color: Int, amount: Double) =
        Color.argb(
            (clamp(amount) * 255).roundToInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

    private inline fun saved(c: Canvas, block: () -> Unit) {
        val save = c.save()
        block()
        c.restoreToCount(save)
    }

    private fun segment(measure: PathMeasure, start: Double, end: Double): Path {
        trim.rewind()
        measure.getSegment(
            (clamp(start) * measure.length).toFloat(),
            (clamp(end) * measure.length).toFloat(),
            trim,
            true,
        )
        return trim
    }

    fun draw(c: Canvas, width: Float, height: Float, motion: ChartMotion) {
        c.drawColor(Color.BLACK)
        val f = motion.state()
        tint = color(f.r, f.g, f.b)
        core = color(f.coreR, f.coreG, f.coreB)
        tintFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        val k = min(width / 402, height / 874)
        saved(c) {
            c.translate((width - 402 * k) / 2, (height - 874 * k) / 2)
            c.scale(k, k)
            val fade = motion.presented * motion.sceneOpacity
            val layer =
                if (fade < .999)
                    c.saveLayerAlpha(-80f, -100f, 490f, 940f, (clamp(fade) * 255).roundToInt())
                else c.save()
            saved(c) {
                c.translate((-8 * f.jolt).toFloat(), (6 * f.jolt + f.dip).toFloat())
                c.rotate(-f.jolt.toFloat(), 201f, 400f)
                c.scale((1 + .025 * f.jolt).toFloat(), (1 + .025 * f.jolt).toFloat(), 201f, 400f)
                radial(c, 201.0, 250.0, 260.0, intArrayOf(0x12FFFFFF, 0))
                radial(c, 201.0, 400.0, 230.0, intArrayOf(alpha(tint, .1 + .06 * f.axisLight), 0))
                saved(c) {
                    c.translate(201f, 522f)
                    c.scale(1f, 26f / 190)
                    radial(c, 0.0, 0.0, 190.0, intArrayOf(alpha(tint, .16), 0), alpha = f.pool)
                }
                saved(c) {
                    c.clipRect(24f, 522f, 378f, 780f)
                    saved(c) {
                        c.translate(0f, 1044f)
                        c.scale(1f, -1f)
                        scene(c, f, motion, true)
                        marker(c, f, motion, true)
                    }
                    fresh()
                    paint.shader = floorFade
                    c.drawRect(16f, 522f, 386f, 788f, paint)
                    fresh()
                    paint.shader = streakShader
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    c.drawRect(24f, 522f, 378f, 780f, paint)
                    fresh(Color.WHITE, .6)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    paint.colorFilter =
                        PorterDuffColorFilter(
                            color(178.5 + f.r * .3, 178.5 + f.g * .3, 178.5 + f.b * .3),
                            PorterDuff.Mode.MULTIPLY,
                        )
                    c.drawBitmap(glints, null, RectF(40f, 520f, 362f, 681f), paint)
                }
                for (i in intArrayOf(0, 2, 4, 5)) shard(c, f, i, motion.reduced)
                scene(c, f, motion, false)
                if (f.packet > 0)
                    stroke(
                        segment(measures[1], f.packetStart, f.packetEnd),
                        c,
                        Color.WHITE,
                        2.0,
                        f.packet * .6,
                    )
                for (i in intArrayOf(1, 3)) shard(c, f, i, motion.reduced)
                label(c, f, motion.intro, 0)
                label(c, f, motion.intro, 1)
                marker(c, f, motion, false)
            }
            switch(c, motion.thumbPosition(f), motion.pressed, motion.pressAmount)
            c.restoreToCount(layer)
        }
    }

    private fun scene(c: Canvas, f: ChartFilm, motion: ChartMotion, mirror: Boolean) {
        saved(c) {
            c.clipRect(
                64f,
                236f,
                max(64.0, min(f.hx, 64 + if (f.d == 1) 274 * .968 else 274.0)).toFloat(),
                510f,
            )
            sprite(
                c,
                if (mirror) curtainMirror[f.d] else curtain[f.d],
                tint,
                clamp(f.area) * (if (mirror) .9 else 1.0) * max(1.0, f.area),
            )
            if (!mirror)
                saved(c) {
                    c.clipPath(areaPaths[f.d])
                    if (f.d == 1) stroke(grid, c, tint, .75, .11 * clamp(f.area))
                    else c.drawPath(dots, fresh(tint, .11 * clamp(f.area)))
                }
        }
        val axisY = clamp(motion.intro * .9 / .4)
        val axisX = clamp((motion.intro * .9 - .35) / .55)
        if (!mirror) {
            stroke(grid, c, 0x12FFFFFF, .75, f.grid)
            saved(c) {
                c.clipRect(64f, 236f, (64 + 274 * smooth(motion.intro)).toFloat(), 510f)
                c.drawPath(dots, fresh(0x47FFFFFF, (1 - f.grid) * smooth(motion.intro)))
            }
            for (i in 0..3) {
                val u = ChartGeometry.risers[i]
                val p = atU(1, u)
                if (f.d == 1 && f.hx >= p.x) {
                    val opacity =
                        .22 +
                            .5 *
                                max(
                                    columnFlash(f.l, i),
                                    pulse(motion.elapsed - motion.columnTimes[i], .12, .4),
                                )
                    gradient(
                        c,
                        RectF(p.x.toFloat(), p.y.toFloat(), p.x.toFloat() + .8f, 510f),
                        intArrayOf(tint, alpha(tint, 0.0)),
                        opacity,
                    )
                }
            }
        }
        sprite(
            c,
            if (mirror) axisMirrorGlow else axisGlow,
            Color.WHITE,
            (if (mirror) .24 else .35) * axisX,
        )
        if (!mirror) {
            fresh(0xEBFFFFFF.toInt())
            paint.strokeWidth = 1.5f
            c.drawLine(64f, 236f, 64f, (236 + 274 * axisY).toFloat(), paint)
            c.drawLine(64f, 510f, (64 + 274 * axisX).toFloat(), 510f, paint)
        }
        val lineAlpha = if (f.l >= 7.05 && f.l < 8.07) 1 - smooth((f.l - 7.05) / .04) else 1.0
        if (lineAlpha > 0 && f.end > 0) {
            val partial = segment(measures[f.d], 0.0, f.end)
            if (!mirror)
                saved(c) {
                    c.clipRect(-80f, -120f, (f.hx + 1).toFloat(), 580f)
                    c.translate(0f, 3f)
                    sprite(c, contact[f.d], Color.BLACK, .5 * lineAlpha * f.glow)
                }
            if (
                f.end < .9999 &&
                    Build.VERSION.SDK_INT >= 31 &&
                    c.isHardwareAccelerated &&
                    liveBloom != null
            ) {
                liveBloom.draw(c, partial, tint, lineAlpha * f.glow, mirror)
            } else {
                saved(c) {
                    // API 26–30 keeps the cached tube material and the same native motion.
                    if (f.end < .9999) c.clipRect(-80f, -120f, (f.hx + 1).toFloat(), 580f)
                    sprite(c, if (mirror) mirrorGlow[f.d] else glow[f.d], tint, lineAlpha * f.glow)
                }
            }
            if (!mirror) stroke(partial, c, core, 2.0, lineAlpha)
        }
        if (f.broken > 0)
            for (i in 0..5) {
                val p = piecePose(f.l, i)
                val piece = ChartGeometry.pieces[i]
                saved(c) {
                    c.translate(p.x.toFloat(), p.y.toFloat())
                    c.rotate(Math.toDegrees(p.angle).toFloat())
                    c.scale(1f, p.sy.toFloat())
                    c.translate(-piece.cx.toFloat(), -piece.cy.toFloat())
                    sprite(
                        c,
                        if (mirror) pieceMirrorGlow[i] else pieceGlow[i],
                        0xFFFF2D3D.toInt(),
                        f.broken * p.alpha,
                    )
                    if (!mirror)
                        stroke(pieces[i], c, 0xFFFFA0A8.toInt(), 2 / sqrt(p.sy), f.broken * p.alpha)
                }
            }
        for (i in 0..5) {
            val t = f.l - (7.05 + (5 - i) * .045)
            val a = pulse(t, .006, .084) * (if (mirror) .5 else 1.0)
            if (a > 0) {
                val p = atArc(0, ChartGeometry.pieces[i].start)
                radial(
                    c,
                    p.x,
                    p.y,
                    2 + 7 * clamp(t / .09),
                    intArrayOf(Color.WHITE, 0xFFFFD7AC.toInt(), 0x00FF2D3D),
                    floatArrayOf(0f, .2f, 1f),
                    a,
                )
            }
        }
        if (f.heat > 0 && f.heatEnd > f.heatStart) {
            saved(c) {
                c.clipRect(
                    (64 + 262.218 * f.heatStart).toFloat() - 1,
                    470f,
                    (64 + 262.218 * f.heatEnd).toFloat() + 1,
                    550f,
                )
                sprite(
                    c,
                    if (mirror) heatMirrorGlow else heatGlow,
                    0xFFFF2D3D.toInt(),
                    f.heat * (if (mirror) .7 else 1.0),
                )
            }
            if (!mirror) {
                val p = segment(hotMeasure, f.heatStart, f.heatEnd)
                fresh(Color.WHITE, f.heat)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 2f
                paint.shader =
                    LinearGradient(
                        max(64.0, f.hx - 80).toFloat(),
                        510f,
                        max(65.0, f.hx).toFloat(),
                        510f,
                        0xFFFF8793.toInt(),
                        0xFFFFF4E9.toInt(),
                        Shader.TileMode.CLAMP,
                    )
                c.drawPath(p, paint)
            }
        }
        for (i in 0..29) {
            val (x, y) = sparkPoint(f.l, i)
            if (x > 0) {
                saved(c) {
                    c.translate(x.toFloat(), y.toFloat())
                    sprite(c, sparkGlow, 0xFFFF2D3D.toInt(), if (mirror) .4 else .75)
                }
                if (!mirror) c.drawCircle(x.toFloat(), y.toFloat(), .8f, fresh(0xFFFFF1D9.toInt()))
            }
        }
        if (f.gatherFlash > 0) {
            val a = f.gatherFlash * (if (mirror) .5 else 1.0)
            radial(
                c,
                64.0,
                510.0,
                10 + 34 * f.gatherTime,
                intArrayOf(Color.WHITE, 0xFFFFBCAD.toInt(), 0x00FF2D3D),
                floatArrayOf(0f, .18f, 1f),
                a * .9,
            )
            fresh(0xFFFF9A91.toInt(), a * .7)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            c.drawCircle(64f, 510f, (6 + 24 * f.gatherTime).toFloat(), paint)
        }
        saved(c) {
            c.translate(f.hx.toFloat(), f.hy.toFloat())
            c.rotate(Math.toDegrees(f.angle).toFloat())
            val a = f.arrow * (if (mirror) .35 else 1.0)
            radial(
                c,
                0.0,
                0.0,
                14 + 8 * f.comet - 5 * f.gather,
                intArrayOf(alpha(core, .6 + .3 * f.comet), alpha(tint, .5), alpha(tint, 0.0)),
                floatArrayOf(0f, .22f, 1f),
                a,
            )
            if (!mirror) c.drawPath(arrow, fresh(core, a))
            c.drawCircle(0f, 0f, (3.5 + 3.5 * f.gather).toFloat(), fresh(Color.WHITE, a * f.comet))
        }
    }

    private fun shard(c: Canvas, f: ChartFilm, i: Int, still: Boolean) {
        val pose = shardPose(f.l, i, still, f.life)
        if (pose.alpha <= 0) return
        val size = ChartGeometry.shards[i][2].toFloat()
        saved(c) {
            c.translate(pose.x.toFloat(), pose.y.toFloat())
            c.rotate(Math.toDegrees(pose.angle).toFloat())
            fresh(Color.WHITE, pose.alpha)
            paint.colorFilter =
                PorterDuffColorFilter(
                    color(255 * .85 + f.r * .15, 255 * .85 + f.g * .15, 255 * .85 + f.b * .15),
                    PorterDuff.Mode.MULTIPLY,
                )
            val tile = shards.width / 6
            c.drawBitmap(
                shards,
                Rect(tile * i, 0, tile * (i + 1), shards.height),
                RectF(-size / 2, -size / 2, size / 2, size / 2),
                paint,
            )
            if (i < 2) {
                c.translate(-size / 2, -size / 2)
                c.scale(size / 256, size / 256)
                fresh(tint, .25 * pose.alpha)
                paint.strokeWidth = 2f
                val points =
                    if (i == 0) floatArrayOf(43f, 53f, 175f, 33f, 175f, 33f, 219f, 177f)
                    else floatArrayOf(62f, 31f, 211f, 87f, 211f, 87f, 162f, 220f)
                c.drawLines(points, paint)
            }
        }
    }

    private fun label(c: Canvas, f: ChartFilm, intro: Double, d: Int) {
        if (d == 1 && f.label <= 0) return
        saved(c) {
            c.translate(0f, (4 * if (d == 1) 1 - f.label else f.label).toFloat())
            val layer = c.saveLayer(20f, 140f, 385f, 245f, null)
            sprite(c, labelWide[d], 0xFFF4F6F5.toInt(), .25)
            sprite(c, labelTight[d], 0xFFF4F6F5.toInt(), .8)
            c.drawPath(labels[d], fresh(0xFFF4F6F5.toInt()))
            val reveal = if (intro < 1) smooth(intro) else f.label
            val left = (40 + 322 * reveal).toFloat()
            fresh()
            paint.shader =
                LinearGradient(
                    left,
                    0f,
                    left + 24,
                    0f,
                    if (d == 1 || intro < 1) Color.WHITE else 0,
                    if (d == 1 || intro < 1) 0 else Color.WHITE,
                    Shader.TileMode.CLAMP,
                )
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            c.drawRect(20f, 140f, 385f, 245f, paint)
            c.restoreToCount(layer)
            if (d == 1 && f.glitch > 0)
                for (i in 0..1) saved(c) {
                    c.clipRect(60f, if (i == 0) 187f else 195f, 350f, if (i == 0) 195f else 204f)
                    c.translate(if (i == 0) 4f else -4f, 0f)
                    c.drawPath(labels[d], fresh(Color.WHITE, f.glitch * .35))
                }
        }
    }

    private fun marker(c: Canvas, f: ChartFilm, motion: ChartMotion, mirror: Boolean) {
        val demo = motion.mode == ChartMode.Loop && !motion.paused
        val a = (if (demo) f.markerAlpha else motion.scrubAlpha) * (if (mirror) .35 else 1.0)
        if (a <= 0) return
        val u = clamp(if (demo) f.markerU else motion.scrubU, 0.0, if (f.d == 1) .968 else .957)
        val p = atU(f.d, u)
        saved(c) {
            c.translate(p.x.toFloat(), p.y.toFloat())
            val s = (.9 + .1 * (if (demo) f.markerAlpha else motion.scrubAlpha)).toFloat()
            c.scale(s, s)
            gradient(
                c,
                RectF(-.5f, 0f, .5f, max(0.0, 510 - p.y).toFloat()),
                intArrayOf(alpha(tint, .5), alpha(tint, .15)),
                a,
                274f,
            )
            radial(c, 0.0, 0.0, 15.0, intArrayOf(alpha(tint, .5), alpha(tint, .15)), alpha = a * .5)
            c.drawCircle(0f, 0f, 3.5f, fresh(Color.WHITE, a))
            if (!mirror) {
                c.translate((clamp(p.x, 92.0, 310.0) - p.x - 36).toFloat(), -42f)
                c.drawRoundRect(0f, 0f, 72f, 28f, 14f, 14f, fresh(0xE00C0E0D.toInt(), a))
                fresh(tint, a * .55)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                c.drawRoundRect(0f, 0f, 72f, 28f, 14f, 14f, paint)
                fresh(0x99FFFFFF.toInt(), a)
                paint.typeface = regular
                paint.textSize = 9f
                c.drawText("D${(30*u/(if(f.d == 1) 1.0 else .957)).roundToInt()}", 10f, 18f, paint)
                fresh(Color.WHITE, a)
                paint.typeface = black
                paint.textSize = 13f
                val value = (clamp(p.v) * 100).roundToInt().toString()
                c.drawText(value, 62 - paint.measureText(value), 19f, paint)
            }
        }
    }

    private fun switch(c: Canvas, thumb: Double, pressed: Int, pressAmount: Double) {
        c.drawRoundRect(133f, 770f, 269f, 814f, 22f, 22f, fresh(0x0DFFFFFF))
        fresh()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.shader =
            LinearGradient(0f, 770f, 0f, 814f, 0x38FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(133f, 770f, 269f, 814f, 22f, 22f, paint)
        val p = clamp(thumb)
        val selectedColor = color(255 - 215 * p, 45 + 179 * p, 61 + 61 * p)
        saved(c) {
            c.translate((169 + 64 * thumb).toFloat(), 792f)
            val scale =
                (if (pressed == thumb.roundToInt()) 1 - .03 * pressAmount else 1.0).toFloat()
            c.scale(scale, scale)
            c.translate(-31f, -18f)
            sprite(c, thumbGlow, selectedColor, .25)
            c.drawPath(thumbPath, fresh(0x17FFFFFF))
            stroke(thumbPath, c, selectedColor, 1.0, .55)
        }
        for (d in 0..1) saved(c) {
            c.translate(if (d == 1) 233f else 169f, 792f)
            val scale = (if (pressed == d) 1 - .03 * pressAmount else 1.0).toFloat()
            c.scale(scale, scale)
            val active = if (d == 1) p else 1 - p
            val press = if (pressed == d) pressAmount * .2 else 0.0
            val col =
                if (d == 1)
                    color(
                        255 - 215 * active * (1 - press),
                        255 - 31 * active * (1 - press),
                        255 - 133 * active * (1 - press),
                        .38 + .62 * active + press,
                    )
                else
                    color(
                        255.0,
                        255 - 210 * active * (1 - press),
                        255 - 194 * active * (1 - press),
                        .38 + .62 * active + press,
                    )
            sprite(c, glyphGlow[d], col, .6 * active)
            stroke(glyphs[d], c, col, 1.75)
        }
    }
}

/** Blur the trimmed path itself, preserving the round bloom ahead of a moving tip. */
@android.annotation.TargetApi(31)
private class ChartLiveBloom {
    private val nodes =
        Array(4) { index ->
            val mirror = index >= 2
            val wide = index % 2 == 0
            val sigma = if (mirror) (if (wide) 16f else 6f) else (if (wide) 12f else 2.5f)
            RenderNode("SpikeToStair tube $index").apply {
                setPosition(0, 0, 1000, 1600)
                setClipToBounds(false)
                setRenderEffect(
                    RenderEffect.createBlurEffect(sigma * 2, sigma * 2, Shader.TileMode.DECAL)
                )
            }
        }
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    fun draw(canvas: Canvas, path: Path, color: Int, opacity: Double, mirror: Boolean) {
        for (pass in 0..1) {
            val wide = pass == 0
            val node = nodes[pass + if (mirror) 2 else 0]
            val recording = node.beginRecording()
            recording.scale(2f, 2f)
            recording.translate(50f, 120f)
            paint.color = color
            val alpha = if (mirror) (if (wide) .4 else .7) else (if (wide) .42 else .9)
            paint.alpha = (255 * clamp(alpha * opacity)).roundToInt()
            paint.strokeWidth = if (mirror) (if (wide) 14f else 6f) else (if (wide) 10f else 4.5f)
            recording.drawPath(path, paint)
            node.endRecording()
            val save = canvas.save()
            canvas.translate(-50f, -120f)
            canvas.scale(.5f, .5f)
            canvas.drawRenderNode(node)
            canvas.restoreToCount(save)
        }
    }
}
