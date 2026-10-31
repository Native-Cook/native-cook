package com.ngocdevv.nativecook.blocks.cards.parcellockerpickup

import android.content.Context
import android.graphics.*
import kotlin.math.*

/**
 * Layout in dp; wallScale maps the 1206 × 2622 source artwork into the scene's safe-area layout.
 */
internal data class ParcelLockerPickupGeometry(
    val width: Double,
    val height: Double,
    val top: Double,
    val bottom: Double,
) {
    val wallScale
        get() = width / 1206

    val wallOriginY
        get() = top + 118 - 329 * wallScale

    val doorX
        get() = 435 * wallScale

    val doorY
        get() = wallOriginY + 1057 * wallScale

    val doorWidth
        get() = 336 * wallScale

    val doorHeight
        get() = 335 * wallScale

    val doorCenterX
        get() = doorX + doorWidth / 2

    val doorCenterY
        get() = doorY + doorHeight / 2

    val sheetBottom
        get() = height - bottom - 14

    val parcelWidth
        get() = doorWidth * 0.8

    val parcelHeight
        get() = parcelWidth * 523 / 640

    val parcelCenterY
        get() = doorY + doorHeight - parcelHeight / 2 - doorHeight * 0.07

    val thumbnailCenterY
        get() = sheetBottom - 179

    /**
     * Projects a door point: angle in degrees; hinge distance, y and thickness in dp; focal length
     * 1000 dp.
     */
    fun project(
        angleDegrees: Double,
        hingeDistance: Double,
        verticalPosition: Double,
        thickness: Double = 0.0,
    ): Pair<Double, Double> {
        val angleRadians = angleDegrees * PI / 180
        val x = doorX + hingeDistance * cos(angleRadians) + thickness * sin(angleRadians)
        val depth = hingeDistance * sin(angleRadians) - thickness * cos(angleRadians)
        val perspectiveScale = 1000 / (1000 - depth)
        return Pair(
            doorX + (x - doorX) * perspectiveScale,
            doorCenterY + (verticalPosition - doorCenterY) * perspectiveScale,
        )
    }
}

/** Decoded images, typefaces, paths and frosted backdrop are retained outside the frame loop. */
internal class ParcelLockerPickupArt(private val context: Context) {
    private fun bitmap(name: String) =
        context.assets.open("parcellockerpickup/parcellockerpickup_$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private val wall = bitmap("wall.jpg")
    private val compartment = bitmap("compartment.webp")
    private val parcel = bitmap("parcel.webp")
    private val fonts =
        listOf("Archivo-Medium", "Archivo-SemiBold", "Archivo-ExtraBold", "JetBrainsMono-Medium")
            .associateWith {
                Typeface.createFromAsset(
                    context.assets,
                    "parcellockerpickup/parcellockerpickup_$it.ttf",
                )
            }

    private fun fontHeight(face: String, size: Float): Double {
        val fontPaint =
            Paint().apply {
                typeface = fonts[face]
                textSize = size
            }
        return ceil((fontPaint.fontMetrics.descent - fontPaint.fontMetrics.ascent).toDouble())
    }

    val pickupButtonY = 165 + fontHeight("JetBrainsMono-Medium", 10.5f)
    val receiptButtonY = 145 + max(28.0, fontHeight("Archivo-ExtraBold", 26f))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val textWidths = mutableMapOf<String, FloatArray>()
    private val path = Path()
    private val matrix = Matrix()
    private val sourceCorners = FloatArray(8)
    private val projectedCorners = FloatArray(8)
    private val frost = makeBlur()
    private lateinit var canvas: Canvas

    private fun color(hex: Int, alpha: Double = 1.0) =
        ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or hex

    private fun rect(x: Double, y: Double, width: Double, height: Double) =
        RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

    private fun reset(hex: Int = 0xffffff, alpha: Double = 1.0) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = color(hex, alpha)
    }

    private fun box(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        hex: Int,
        alpha: Double = 1.0,
        radius: Double = 0.0,
        stroke: Boolean = false,
    ) {
        reset(hex, alpha)
        if (stroke) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
        }
        canvas.drawRoundRect(rect(x, y, width, height), radius.toFloat(), radius.toFloat(), paint)
    }

    private fun image(bitmap: Bitmap, x: Double, y: Double, width: Double, height: Double) {
        reset()
        canvas.drawBitmap(bitmap, null, rect(x, y, width, height), paint)
    }

    private fun gradient(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        colors: IntArray,
        startX: Double = x,
        startY: Double = y,
        endX: Double = x,
        endY: Double = y + height,
        alpha: Double = 1.0,
        additive: Boolean = false,
        positions: FloatArray? = null,
    ) {
        reset(0xffffff, alpha)
        paint.shader =
            LinearGradient(
                startX.toFloat(),
                startY.toFloat(),
                endX.toFloat(),
                endY.toFloat(),
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
        if (additive) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        canvas.drawRect(rect(x, y, width, height), paint)
    }

    /**
     * Draws bundled glyphs with explicit tracking. Font size, line height and positions are dp.
     * Cached glyph widths avoid measuring every frame; the returned height positions the following
     * row.
     */
    private fun text(
        text: String,
        x: Double,
        y: Double,
        size: Double,
        face: String = "Archivo-Medium",
        hex: Int = 0xf4efe8,
        tracking: Double = 0.0,
        height: Double? = null,
        center: Boolean = false,
        right: Boolean = false,
    ): Double {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.isSubpixelText = true
        textPaint.typeface = fonts[face]
        textPaint.textSize = size.toFloat()
        textPaint.color = color(hex)
        val metrics = textPaint.fontMetrics
        val lineHeight = height ?: ceil((metrics.descent - metrics.ascent).toDouble())
        val baseline = y + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
        val widths =
            textWidths.getOrPut("$face/$size/$text") {
                FloatArray(text.length).also { textPaint.getTextWidths(text, it) }
            }
        val width = widths.sum() + tracking * (text.length - 1)
        var glyphX = if (center) x - width / 2 else if (right) x - width else x
        if (tracking == 0.0) canvas.drawText(text, glyphX.toFloat(), baseline.toFloat(), textPaint)
        else
            for (i in text.indices) {
                canvas.drawText(text, i, i + 1, glyphX.toFloat(), baseline.toFloat(), textPaint)
                glyphX += widths[i] + tracking
            }
        return lineHeight
    }

    /**
     * Downsamples and blurs the static wall once for the frosted sheet, before any frame is drawn.
     */
    private fun makeBlur(): Bitmap {
        val small = Bitmap.createScaledBitmap(wall, 134, 291, true)
        val width = small.width
        val height = small.height
        val pixels = IntArray(width * height)
        val scratchPixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        // Three separable box passes approximate the same sigma=18 dp on a 402 dp wall.
        repeat(3) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (i in -6..6) {
                        val pixel = pixels[y * width + (x + i).coerceIn(0, width - 1)]
                        red += pixel shr 16 and 255
                        green += pixel shr 8 and 255
                        blue += pixel and 255
                    }
                    scratchPixels[y * width + x] = Color.rgb(red / 13, green / 13, blue / 13)
                }
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (i in -6..6) {
                        val pixel = scratchPixels[(y + i).coerceIn(0, height - 1) * width + x]
                        red += pixel shr 16 and 255
                        green += pixel shr 8 and 255
                        blue += pixel and 255
                    }
                    pixels[y * width + x] = Color.rgb(red / 13, green / 13, blue / 13)
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Draws in reference order: wall/interior, light/shadows, projected door/edge, LED, sheet,
     * parcel, text. Geometry is dp, time is seconds (for the LED pulse) and the pose contains
     * normalized progress.
     */
    fun draw(
        targetCanvas: Canvas,
        geometry: ParcelLockerPickupGeometry,
        pose: ParcelLockerPickupMotion.Pose,
        time: Double,
    ) {
        canvas = targetCanvas
        val motion = ParcelLockerPickupMotion
        val angleRadians = pose.angle * PI / 180
        box(0.0, 0.0, geometry.width, geometry.height, 0x141312)
        image(wall, 0.0, geometry.wallOriginY, geometry.width, 2622 * geometry.wallScale)
        canvas.save()
        canvas.clipRect(
            rect(geometry.doorX, geometry.doorY, geometry.doorWidth, geometry.doorHeight)
        )
        image(compartment, geometry.doorX, geometry.doorY, geometry.doorWidth, geometry.doorHeight)
        val darkness = 1 - 0.82 * pose.light
        box(
            geometry.doorX,
            geometry.doorY,
            geometry.doorWidth,
            geometry.doorHeight,
            0x05060a,
            darkness,
        )
        if (pose.parcel < 0.02) {
            reset(0, 0.5 * (1 - motion.smooth(0.0, 0.25, pose.parcel)))
            paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(
                rect(
                    geometry.doorCenterX - geometry.parcelWidth * 0.46,
                    geometry.parcelCenterY + geometry.parcelHeight * 0.36,
                    geometry.parcelWidth * 0.92,
                    geometry.parcelHeight * 0.2,
                ),
                (geometry.parcelHeight * 0.1).toFloat(),
                (geometry.parcelHeight * 0.1).toFloat(),
                paint,
            )
            image(
                parcel,
                geometry.doorCenterX - geometry.parcelWidth / 2,
                geometry.parcelCenterY - geometry.parcelHeight / 2,
                geometry.parcelWidth,
                geometry.parcelHeight,
            )
            box(
                geometry.doorCenterX - geometry.parcelWidth / 2,
                geometry.parcelCenterY - geometry.parcelHeight / 2,
                geometry.parcelWidth,
                geometry.parcelHeight,
                0x05060a,
                darkness,
            )
        }
        gradient(
            geometry.doorX,
            geometry.doorY,
            geometry.doorWidth,
            geometry.doorHeight * 0.35,
            intArrayOf(color(0xaac8ff, 0.22), color(0xaac8ff, 0.0)),
            alpha = pose.light,
            additive = true,
        )
        canvas.restore()
        reset(0xb9cdff, 0.55 * pose.light * min(1.0, sin(angleRadians) * 1.4) * 0.5)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        paint.maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            rect(
                geometry.doorX - 26,
                geometry.doorY - 18,
                geometry.doorWidth + 52,
                geometry.doorHeight + 52,
            ),
            20f,
            20f,
            paint,
        )
        reset(0, 0.55)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(
            rect(geometry.doorX, geometry.doorY, geometry.doorWidth, geometry.doorHeight),
            paint,
        )
        gradient(
            geometry.doorX - geometry.doorWidth * 0.75,
            geometry.doorY - 4,
            geometry.doorWidth * 0.75,
            geometry.doorHeight + 8,
            intArrayOf(color(0, 0.85), 0),
            startX = geometry.doorX,
            startY = 0.0,
            endX = geometry.doorX - geometry.doorWidth * 0.75,
            endY = 0.0,
            alpha = max(0.0, sin(angleRadians)) * 0.45,
        )
        val projectedPoints =
            arrayOf(
                geometry.project(pose.angle, 0.0, geometry.doorY),
                geometry.project(pose.angle, geometry.doorWidth, geometry.doorY),
                geometry.project(
                    pose.angle,
                    geometry.doorWidth,
                    geometry.doorY + geometry.doorHeight,
                ),
                geometry.project(pose.angle, 0.0, geometry.doorY + geometry.doorHeight),
            )
        val sourcePoints =
            arrayOf(
                Pair(geometry.doorX, geometry.doorY),
                Pair(geometry.doorX + geometry.doorWidth, geometry.doorY),
                Pair(geometry.doorX + geometry.doorWidth, geometry.doorY + geometry.doorHeight),
                Pair(geometry.doorX, geometry.doorY + geometry.doorHeight),
            )
        repeat(4) { i ->
            sourceCorners[i * 2] = sourcePoints[i].first.toFloat()
            sourceCorners[i * 2 + 1] = sourcePoints[i].second.toFloat()
            projectedCorners[i * 2] = projectedPoints[i].first.toFloat()
            projectedCorners[i * 2 + 1] = projectedPoints[i].second.toFloat()
        }
        matrix.reset()
        matrix.setPolyToPoly(sourceCorners, 0, projectedCorners, 0, 4)
        canvas.save()
        canvas.concat(matrix)
        canvas.clipRect(
            rect(geometry.doorX, geometry.doorY, geometry.doorWidth, geometry.doorHeight)
        )
        if (angleRadians <= PI / 2) {
            image(wall, 0.0, geometry.wallOriginY, geometry.width, 2622 * geometry.wallScale)
            box(
                geometry.doorX,
                geometry.doorY,
                geometry.doorWidth,
                geometry.doorHeight,
                0,
                min(0.4, 0.3 * sin(angleRadians)),
            )
            gradient(
                geometry.doorX,
                geometry.doorY,
                geometry.doorWidth,
                geometry.doorHeight,
                intArrayOf(color(0xffaa5a, 0.55), color(0xffaa5a, 0.05)),
                endX = geometry.doorX + geometry.doorWidth,
                alpha = max(0.0, sin(angleRadians)) * 0.45,
                additive = true,
            )
        } else {
            gradient(
                geometry.doorX,
                geometry.doorY,
                geometry.doorWidth,
                geometry.doorHeight,
                intArrayOf(color(0x3a3e45), color(0x24272c)),
                endX = geometry.doorX + geometry.doorWidth,
            )
            box(
                geometry.doorX + geometry.doorWidth * 0.08,
                geometry.doorY + geometry.doorHeight * 0.36,
                geometry.doorWidth * 0.16,
                geometry.doorHeight * 0.28,
                0x15171a,
            )
            reset(0xb9cdff, 0.16 * pose.light)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            canvas.drawRect(
                rect(geometry.doorX, geometry.doorY, geometry.doorWidth, geometry.doorHeight),
                paint,
            )
        }
        canvas.restore()
        val edge =
            arrayOf(
                geometry.project(pose.angle, geometry.doorWidth, geometry.doorY),
                geometry.project(pose.angle, geometry.doorWidth, geometry.doorY, 5.0),
                geometry.project(
                    pose.angle,
                    geometry.doorWidth,
                    geometry.doorY + geometry.doorHeight,
                    5.0,
                ),
                geometry.project(
                    pose.angle,
                    geometry.doorWidth,
                    geometry.doorY + geometry.doorHeight,
                ),
            )
        path.reset()
        edge.forEachIndexed { i, point ->
            if (i == 0) path.moveTo(point.first.toFloat(), point.second.toFloat())
            else path.lineTo(point.first.toFloat(), point.second.toFloat())
        }
        path.close()
        reset(0xffffff, min(1.0, sin(angleRadians) * 3))
        paint.shader =
            LinearGradient(
                0f,
                geometry.doorY.toFloat(),
                0f,
                (geometry.doorY + geometry.doorHeight).toFloat(),
                color(0xa9aeb6),
                color(0x5e636b),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        val led =
            geometry.project(
                pose.angle,
                306 * geometry.wallScale,
                geometry.doorY + 35 * geometry.wallScale,
            )
        val alpha =
            if (pose.led == 2) 0.0
            else if (pose.led == 1) 1.0 else 0.25 + 0.75 * sin(PI * (time % 2) / 2).pow(2)
        val hex = if (pose.led == 1) 0x5be39a else 0xffb25e
        reset(hex, alpha * 0.8)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(led.first.toFloat(), led.second.toFloat(), 7f, paint)
        reset(hex, alpha)
        canvas.drawCircle(led.first.toFloat(), led.second.toFloat(), 2.4f, paint)
        gradient(
            0.0,
            0.0,
            geometry.width,
            geometry.top + 190,
            intArrayOf(color(0x0e0d0c, 0.98), color(0x0e0d0c, 0.82), color(0x0e0d0c, 0.0)),
            positions = floatArrayOf(0f, 0.55f, 1f),
        )
        val sheetHeight = 248 + 52 * pose.sheet
        val sheetY = geometry.sheetBottom - sheetHeight
        path.reset()
        path.addRoundRect(
            rect(16.0, sheetY, geometry.width - 32, sheetHeight),
            28f,
            28f,
            Path.Direction.CW,
        )
        canvas.save()
        canvas.clipPath(path)
        image(frost, 0.0, geometry.wallOriginY, geometry.width, 2622 * geometry.wallScale)
        box(16.0, sheetY, geometry.width - 32, sheetHeight, 0x161413, 0.62)
        canvas.restore()
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.shader =
            LinearGradient(
                0f,
                (geometry.sheetBottom - 300).toFloat(),
                0f,
                geometry.sheetBottom.toFloat(),
                color(0xffffff, 0.22),
                color(0xffffff, 0.05),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        if (pose.parcel >= 0.02) {
            val lift = motion.smooth(0.0, 0.35, pose.parcel)
            val fly = motion.smooth(0.3, 1.0, pose.parcel)
            val liftedY = geometry.parcelCenterY - 10 * lift
            val liftedScale = 1 + 0.32 * lift
            val controlX = (geometry.doorCenterX + 70) / 2 - 40
            val controlY = min(liftedY, geometry.thumbnailCenterY) - 30
            val x =
                (1 - fly).pow(2) * geometry.doorCenterX +
                    2 * (1 - fly) * fly * controlX +
                    fly * fly * 70
            val y =
                (1 - fly).pow(2) * liftedY +
                    2 * (1 - fly) * fly * controlY +
                    fly * fly * geometry.thumbnailCenterY
            val parcelScale = liftedScale + (64 * 0.92 / geometry.parcelWidth - liftedScale) * fly
            canvas.saveLayerAlpha(null, ((1 - pose.fold) * 255).roundToInt())
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate((-0.12 * sin(PI * fly) * 180 / PI).toFloat())
            canvas.scale(parcelScale.toFloat(), parcelScale.toFloat())
            image(
                parcel,
                -geometry.parcelWidth / 2,
                -geometry.parcelHeight / 2,
                geometry.parcelWidth,
                geometry.parcelHeight,
            )
            canvas.restore()
        }
        header(geometry, pose)
        content(geometry, pose, sheetY, sheetHeight)
    }

    /** Crossfades pickup instructions into the collection confirmation as the sheet grows. */
    private fun header(geometry: ParcelLockerPickupGeometry, pose: ParcelLockerPickupMotion.Pose) {
        val motion = ParcelLockerPickupMotion
        val eyebrowHeight =
            text(
                "NORTHWIND LOCKERS · LOBBY",
                24.0,
                geometry.top + 14,
                11.0,
                "JetBrainsMono-Medium",
                0xa39a90,
                1.6,
            )
        val y = geometry.top + 14 + eyebrowHeight + 10
        canvas.saveLayerAlpha(null, ((1 - motion.smooth(0.3, 0.7, pose.sheet)) * 255).roundToInt())
        text("Locker 14B", 24.0, y, 36.0, "Archivo-ExtraBold", tracking = -0.7, height = 40.0)
        text(
            "Middle column, third row · blinking amber",
            24.0,
            y + 44,
            15.0,
            hex = 0xa39a90,
            height = 20.0,
        )
        canvas.restore()
        canvas.saveLayerAlpha(null, (motion.smooth(0.5, 0.9, pose.sheet) * 255).roundToInt())
        text(
            "Enjoy your parcel",
            24.0,
            y,
            36.0,
            "Archivo-ExtraBold",
            tracking = -0.7,
            height = 40.0,
        )
        text(
            "Locker 14B is closed and free again",
            24.0,
            y + 44,
            15.0,
            hex = 0xa39a90,
            height = 20.0,
        )
        canvas.restore()
    }

    /**
     * Clips both sheet faces together; local text and button positions are dp from the sheet top.
     */
    private fun content(
        geometry: ParcelLockerPickupGeometry,
        pose: ParcelLockerPickupMotion.Pose,
        sheetY: Double,
        sheetHeight: Double,
    ) {
        val motion = ParcelLockerPickupMotion
        val pickupFade = motion.smooth(0.0, 0.45, pose.sheet)
        val receiptFade = motion.smooth(0.45, 1.0, pose.sheet)
        val x = 38.0
        val width = geometry.width - 76
        canvas.save()
        canvas.clipRect(rect(16.0, sheetY, geometry.width - 32, sheetHeight))
        canvas.saveLayerAlpha(null, ((1 - pickupFade) * 255).roundToInt())
        canvas.translate(0f, (sheetY - 12 * pickupFade).toFloat())
        val brandHeight =
            text("NORTHWIND PARCELS", x, 20.0, 10.5, "JetBrainsMono-Medium", 0xa39a90, 1.4)
        text(
            "MEDIUM · 2.4 KG",
            geometry.width - 38,
            20.0,
            10.5,
            "JetBrainsMono-Medium",
            0xa39a90,
            1.4,
            right = true,
        )
        val titleY = 20 + brandHeight + 12
        val subtitleY = titleY + 30
        val codeY = subtitleY + 35
        val buttonY = codeY + 68
        text("Your parcel is ready", x, titleY, 22.0, "Archivo-SemiBold", height = 28.0)
        text(
            "Order 48-1173 · picked up by 9 Nov",
            x,
            subtitleY,
            14.0,
            hex = 0xa39a90,
            height = 19.0,
        )
        listOf("4", "8", "1", "7").forEachIndexed { i, digit ->
            val digitLight = (pose.digits - i).coerceIn(0.0, 1.0)
            val digitX = x + i * 64
            val digitY = codeY - 8 * digitLight * (1 - digitLight)
            box(digitX, digitY, 54.0, 50.0, 0xffb25e, 0.06 + 0.16 * digitLight, 14.0)
            box(
                digitX + 0.5,
                digitY + 0.5,
                53.0,
                49.0,
                0xffb25e,
                0.18 + 0.62 * digitLight,
                13.5,
                true,
            )
            text(
                digit,
                digitX + 27,
                digitY,
                24.0,
                "JetBrainsMono-Medium",
                height = 50.0,
                center = true,
            )
        }
        canvas.save()
        val scale = (1 - 0.035 * pose.press).toFloat()
        canvas.scale(scale, scale, (geometry.width / 2).toFloat(), (codeY + 85).toFloat())
        box(x, buttonY, width, 52.0, 0xffb25e, radius = 26.0)
        text(
            "Open locker",
            geometry.width / 2,
            buttonY,
            17.0,
            "Archivo-SemiBold",
            0x1b1308,
            height = 52.0,
            center = true,
        )
        canvas.restore()
        canvas.restore()
        canvas.saveLayerAlpha(null, (receiptFade * 255).roundToInt())
        canvas.translate(0f, (sheetY + 14 * (1 - receiptFade)).toFloat())
        val pickedHeight =
            text("Picked up", x + 38, 22.0, 26.0, "Archivo-ExtraBold", tracking = -0.4)
        val receiptRowHeight = max(28.0, pickedHeight)
        val checkProgress = motion.smooth(0.55, 1.0, pose.sheet)
        canvas.saveLayerAlpha(null, (checkProgress * 255).roundToInt())
        canvas.translate((x + 14).toFloat(), (22 + receiptRowHeight / 2).toFloat())
        val checkScale = (0.6 + 0.4 * checkProgress).toFloat()
        canvas.scale(checkScale, checkScale)
        box(-14.0, -14.0, 28.0, 28.0, 0x5be39a, radius = 14.0)
        path.reset()
        path.moveTo(-4.5f, 0.4f)
        path.lineTo(-1.4f, 3.3f)
        path.lineTo(4.6f, -3.1f)
        reset(0x0e1a13)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        canvas.restore()
        val subY = 22 + receiptRowHeight + 2
        val itemY = subY + 37
        text("6:42 PM · Locker 14B closed", x, subY, 14.0, hex = 0xa39a90, height = 19.0)
        box(x, itemY, 64.0, 64.0, 0xffffff, 0.06, 16.0)
        box(x + 0.5, itemY + 0.5, 63.0, 63.0, 0xffffff, 0.1, 15.5, true)
        text("Order 48-1173", x + 78, itemY + 12, 17.0, "Archivo-SemiBold")
        text("1 parcel · medium · 2.4 kg", x + 78, itemY + 36, 13.0, hex = 0xa39a90)
        val doneY = itemY + 84
        box(x, doneY, width, 50.0, 0xffffff, 0.1, 25.0)
        box(x + 0.5, doneY + 0.5, width - 1, 49.0, 0xffffff, 0.16, 24.5, true)
        text(
            "Done",
            geometry.width / 2,
            doneY,
            16.0,
            "Archivo-SemiBold",
            height = 50.0,
            center = true,
        )
        canvas.restore()
        canvas.restore()
    }
}
