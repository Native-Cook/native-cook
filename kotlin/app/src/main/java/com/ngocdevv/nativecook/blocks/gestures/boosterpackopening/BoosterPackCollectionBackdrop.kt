package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BACKDROP_BACKGROUND = 0xFFE5E9EF.toInt()
private const val BACKDROP_PATTERN = 0xFFB9C3CF.toInt()

private const val ART_WIDTH = 218.0
private const val ART_HEIGHT = ART_WIDTH * (88.0 / 63.0)
private const val ROTATION_DURATION = 38_000.0
private const val ENTRY_SCALE = 1.5
private const val RESTING_SCALE = 1.0
private const val BACKDROP_BLUR = 16.0

/** The blurred pattern is kept at half a pixel per dp: it holds no detail finer than its blur. */
private const val PATTERN_RESOLUTION = 0.5

// Large abstract silhouettes, as outlines of straight segments on a 218-wide sheet.
private val PATTERN_PATHS =
    arrayOf(
        "M27.1 4.9 L6.2 4.9 L6.9 0 L0 0 L0 8.4 L1.4 5.6 L4.9 6.3 L4.9 43.2 L30.5 13.9 Z",
        "M5.6 66.2 L34 44.6 L56.9 36.2 L88.9 18.8 L102.8 22.3 L152.7 23.7 L164.5 22.3 L174.3 16 L175 22.3 L184 30 L212.4 41.1 L212.4 7 L206.9 4.2 L55.5 4.9 L52.1 12.5 L11.1 54.4 Z",
        "M108.3 34.1 L91.6 36.2 L85.4 42.5 L50.7 52.3 L34 62.7 L20.1 77.3 L11.1 98.3 L11.1 108.7 L19.4 120.5 L19.4 124.7 L4.9 142.8 L4.9 170 L9 185.4 L4.9 186.7 L4.9 212.5 L18.1 216.7 L18.1 209 L12.5 195.8 L16 195.1 L35.4 212.5 L55.5 208.3 L54.2 227.9 L61.1 239 L67.3 241.8 L90.9 239 L97.2 246 L115.2 247.4 L145.1 234.1 L170.1 218.1 L188.8 200 L197.9 186.7 L195.8 174.9 L202.7 157.5 L203.4 140.8 L198.6 127.5 L190.2 118.5 L177.7 117.8 L164.5 105.2 L152 100.3 L136.1 101 L134 103.8 L136.1 111.5 L155.5 115.7 L160.4 127.5 L179.1 142.8 L182.6 151.2 L181.2 163.8 L165.9 171.4 L153.4 188.8 L150.7 188.8 L148.6 183.3 L143.7 181.2 L128.4 198.6 L118 203.5 L102.8 204.9 L89.6 202.1 L86.8 205.6 L82.6 204.9 L77.8 198.6 L56.2 147.7 L51.4 141.5 L43 139.4 L46.5 110.8 L59 83.6 L54.8 72.5 L78.5 60.6 L115.2 54.4 L120.1 51.6 L121.5 46 L145.1 48.8 L164.5 55.7 L169.4 54.4 L163.8 46 L152.7 40.4 Z",
        "M181.9 65.5 L177 71.1 L177 81.5 L192.3 100.3 L212.4 114.3 L212.4 70.4 L193.7 63.4 Z",
        "M154.1 80.8 L126.4 72.5 L115.2 73.2 L107.6 77.3 L90.3 77.3 L81.2 81.5 L69.4 94.8 L61.8 109.4 L62.5 117.1 L68 118.5 L76.4 115 L81.9 110.1 L86.8 97.6 L97.2 96.9 L128.4 81.5 L143 81.5 L153.4 86.4 Z",
        "M213.1 201.4 L211.8 199.3 L206.9 208.3 L187.5 225.1 L179.1 237.6 L164.5 241.8 L152 249.5 L134 271.8 L93 272.5 L86.8 264.8 L58.3 248.8 L35.4 239.7 L11.1 235.5 L4.2 244.6 L4.2 291.3 L6.2 297.5 L21.5 295.4 L31.2 298.2 L70.1 298.2 L84.7 294.1 L115.9 294.8 L152.7 282.2 L175.6 269 L202 244.6 L207.6 236.2 L212.4 219.5 Z",
        "M212.4 288.5 L182.6 289.9 L168.7 297.5 L206.2 298.2 L212.4 296.1 Z",
    )

private const val PARTICLE_COUNT = 26
private val PARTICLE_SIZES = doubleArrayOf(3.0, 4.0, 6.0, 3.0, 8.0, 5.0, 11.0, 4.0, 7.0, 3.0, 5.0, 9.0)
private const val TWO_PI = PI * 2

private val PARTICLE_OPACITY_STOPS = doubleArrayOf(0.0, 0.12, 0.58, 1.0)

private fun fraction(index: Int, multiplier: Int, offset: Int): Double = ((index * multiplier + offset) % 997) / 997.0

private class Particle(
    index: Int,
) {
    val size = PARTICLE_SIZES[index % PARTICLE_SIZES.size]
    val drift = (if (index % 2 == 0) -1 else 1) * (4 + fraction(index, 179, 41) * 10)
    val duration = 11_500 + fraction(index, 263, 73) * 6_000
    val left = 0.03 + fraction(index, 619, 17) * 0.94
    val maxOpacity = min(0.54, 0.22 + size * 0.022 + fraction(index, 131, 59) * 0.08)
    val waveOffset = fraction(index, 449, 97) * TWO_PI
    var progress = fraction(index, 397, 29)
    val opacity = doubleArrayOf(0.0, maxOpacity, maxOpacity * 0.68, 0.0)
    val reducedOpacity = doubleArrayOf(0.0, maxOpacity * 0.34, maxOpacity * 0.24, 0.0)
}

/** Adds the outlines above to one path, scaled from the sheet to the scene and moved to its place. */
private fun patternPath(scaleX: Double, scaleY: Double, offsetX: Double, offsetY: Double): Path {
    val path = Path()
    for (outline in PATTERN_PATHS) {
        val tokens = outline.split(' ')
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token == "Z") {
                path.close()
                index += 1
                continue
            }
            val x = (token.substring(1).toDouble() * scaleX + offsetX).toFloat()
            val y = (tokens[index + 1].toDouble() * scaleY + offsetY).toFloat()
            if (token[0] == 'M') path.moveTo(x, y) else path.lineTo(x, y)
            index += 2
        }
    }
    return path
}

/**
 * The pale room the cards are shown in: a large blurred pattern that turns slowly and settles from
 * a zoom as it appears, with soft motes rising through it. The source blurs the pattern on a layer
 * every frame; here the blurred pattern is drawn once into a bitmap, which is then only turned and
 * scaled.
 */
internal class CollectionCardBackdrop(
    private val width: Double,
    private val height: Double,
) {
    private val particles = List(PARTICLE_COUNT) { Particle(it) }
    private var rotation = 0.0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layerPaint = Paint()
    private val destination = RectF()
    private val pattern: Bitmap
    private val artSize = max(width * 1.55, height * 0.82)
    private val margin = BACKDROP_BLUR * 3

    init {
        val scaleX = artSize / ART_WIDTH
        val scaleY = artSize / ART_HEIGHT
        val side = max(ceil((artSize + margin * 2) * PATTERN_RESOLUTION).toInt(), 1)
        pattern = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pattern)
        val matrix = Matrix()
        matrix.setScale(PATTERN_RESOLUTION.toFloat(), PATTERN_RESOLUTION.toFloat())
        canvas.setMatrix(matrix)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = BACKDROP_PATTERN
        fill.maskFilter = BlurCache().sigma(BACKDROP_BLUR)
        canvas.drawPath(patternPath(scaleX, scaleY, margin, margin), fill)
        val left = width / 2 - artSize / 2 - margin
        val top = height / 2 - artSize / 2 - margin
        val extent = side / PATTERN_RESOLUTION
        destination.set(left.toFloat(), top.toFloat(), (left + extent).toFloat(), (top + extent).toFloat())
    }

    /** Turns the pattern and lifts the motes while the room is visible and motion is allowed. */
    fun step(elapsedMs: Double, motionActive: Boolean) {
        if (!motionActive) return
        rotation = (rotation - 360 * elapsedMs / ROTATION_DURATION) % 360
        for (particle in particles) particle.progress += elapsedMs / particle.duration
    }

    fun draw(
        canvas: Canvas,
        brush: Brush,
        opacityProgress: Double,
        zoomProgress: Double,
        reducedMotion: Boolean,
    ) {
        val alpha = alpha255(opacityProgress)
        if (alpha == 0) return
        val layered = alpha < 255
        if (layered) {
            layerPaint.alpha = alpha
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
        }
        canvas.drawColor(BACKDROP_BACKGROUND)

        val scale =
            if (reducedMotion) RESTING_SCALE else ENTRY_SCALE + (RESTING_SCALE - ENTRY_SCALE) * clamp(zoomProgress, 0.0, 1.0)
        canvas.save()
        canvas.rotate(rotation.toFloat(), (width / 2).toFloat(), (height / 2).toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat(), (width / 2).toFloat(), (height / 2).toFloat())
        canvas.drawBitmap(pattern, null, destination, paint)
        canvas.restore()

        for (particle in particles) {
            val cycle = particle.progress % 1
            val translateY = height + particle.size + cycle * (-particle.size - (height + particle.size))
            val opacity =
                interpolateKeyframes(
                    cycle,
                    PARTICLE_OPACITY_STOPS,
                    if (reducedMotion) particle.reducedOpacity else particle.opacity,
                )
            if (opacity <= 0.002) continue
            val translateX = if (reducedMotion) 0.0 else sin(cycle * TWO_PI + particle.waveOffset) * particle.drift
            val x = (particle.left * width + translateX).toFloat()
            val y = (translateY + particle.size / 2).toFloat()
            val radius = (particle.size / 2).toFloat()
            // The mote's glow: a shadow with no offset and a blur of at least 4.
            canvas.drawCircle(
                x, y, radius,
                brush.fill(rgba(255, 255, 255, 0.58), opacity, blur = max(4.0, particle.size) / 2),
            )
            canvas.drawCircle(x, y, radius, brush.fill(rgba(255, 255, 255, 0.96), opacity))
        }
        if (layered) canvas.restore()
    }
}
