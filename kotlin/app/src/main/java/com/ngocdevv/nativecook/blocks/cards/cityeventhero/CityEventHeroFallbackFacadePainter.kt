package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** CPU LED cells on a cylindrical Canvas mesh for API 26–32; no runtime shader required. */
internal class CityEventHeroFallbackFacadePainter(private val assets: CityEventHeroAssets) {
    private val columns = CityEventHeroLed.cols.toInt()
    private val rows = CityEventHeroLed.rows.toInt()
    private val pixels = IntArray(columns * rows)
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    private val uniforms = FloatArray(16)
    private val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val atlas = " 0123456789AEHJNORSTUY"
    private var previousShow = Float.NaN

    init {
        for (y in 0..rows) {
            val row = rows - y.toFloat()
            val referenceY = CityEventHeroTower.baseY - row * CityEventHeroLed.row
            for (x in 0..columns) {
                val side = sin((x.toFloat() / columns - .5f) * PI).toFloat()
                val sag = CityEventHeroLed.sag * (1f - side * side)
                val photoY = (referenceY - CityEventHeroLed.eye * sag) / (1f - sag)
                val halfWidth = CityEventHeroTowerHalfWidth(photoY)
                val at = (y * (columns + 1) + x) * 2
                vertices[at] = CityEventHeroLed.mid + halfWidth * side
                vertices[at + 1] = photoY
            }
        }
    }

    fun draw(canvas: Canvas, frame: CityEventHeroFrame, opacity: Float) {
        CityEventHeroLedAt(frame.show, uniforms)
        val on = uniforms[0] * min(1f, frame.night / CityEventHeroNight.fireFade) * opacity
        if (on <= .001f) return
        if (previousShow != frame.show) {
            for (y in 0 until rows) {
                val row = rows - 1 - y
                for (column in 0 until columns) {
                    pixels[y * columns + column] = cell(column, row)
                }
            }
            bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
            previousShow = frame.show
        }
        paint.alpha = (on * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, null, 0, paint)
    }

    private fun cell(column: Int, row: Int): Int {
        if (row > uniforms[1]) return 0
        val scene = CityEventHeroTheme.scene
        val band = (column + .5f) / columns
        val wave = .5f + .5f * sin(6.2832f * (row - uniforms[10]) / CityEventHeroLed.fieldWave + 2.2f * band)
        val field = scene.ledField * (.8f + .4f * hash(column.toFloat(), row.toFloat()))
        var red = mix(scene.fieldA[0], scene.fieldB[0], wave) * field
        var green = mix(scene.fieldA[1], scene.fieldB[1], wave) * field
        var blue = mix(scene.fieldA[2], scene.fieldB[2], wave) * field
        val front = (uniforms[1] - row) / 6f
        val scan = if (uniforms[1] <= 9999f) exp(-front * front) else 0f
        red += scene.ledScan[0] * scan
        green += scene.ledScan[1] * scan
        blue += scene.ledScan[2] * scan
        val page = uniforms[2].toInt()
        if (page >= 0) {
            val text = if (page < 3) CityEventHeroLed.words[page] else "${uniforms[5].toInt()}JAN"
            val height = text.length * CityEventHeroLed.slot - 5f + if (page == 3) CityEventHeroLed.dateGap else 0f
            var fromTop = height - 1f - (row - uniforms[3])
            if (page == 3 && fromTop >= 2f * CityEventHeroLed.slot) fromTop -= CityEventHeroLed.dateGap
            val letter = floor(fromTop / CityEventHeroLed.slot).toInt()
            val inner = fromTop - letter * CityEventHeroLed.slot
            val glyphColumn = floor((column - CityEventHeroLed.colsFrom) / CityEventHeroLed.glyphCols).toInt()
            if (fromTop >= 0f && letter in text.indices && inner < 35f && glyphColumn in 0..4) {
                val glyph = max(0, atlas.indexOf(text[letter]))
                val bit = assets.glyphs.getPixel(glyph * 5 + glyphColumn, (inner / 5f).toInt()) and 0xFF
                val age = (uniforms[4] - letter) * CityEventHeroLed.stagger
                if (bit > 127 && age >= 0f) {
                    val settle = smooth(0f, CityEventHeroLed.letterFlash, age)
                    val light = 1f + .8f * exp(-age / .08f)
                    red = mix(scene.ledHot[0], scene.ledText[0], settle) * light
                    green = mix(scene.ledHot[1], scene.ledText[1], settle) * light
                    blue = mix(scene.ledHot[2], scene.ledText[2], settle) * light
                }
            }
        }
        if (uniforms[8] > .5f && uniforms[7] > .3f) {
            val y = CityEventHeroTower.baseY - (row + .5f) * CityEventHeroLed.row
            val width = CityEventHeroTowerHalfWidth(y)
            val x = (band - .5f) * PI.toFloat() * width
            val dx = x / uniforms[7]
            val dy = (y - uniforms[6]) / uniforms[7]
            val radius = sqrt(dx * dx + dy * dy)
            if (radius < 1f) {
                val pixel = assets.viewer.getPixel(
                    ((dx * .5f + .5f) * assets.viewer.width).toInt().coerceIn(0, assets.viewer.width - 1),
                    ((dy * .5f + .5f) * assets.viewer.height).toInt().coerceIn(0, assets.viewer.height - 1),
                )
                val ring = smooth(.8f, .88f, radius)
                red = mix(((pixel shr 16) and 255) / 255f, scene.ledRing[0], ring) * 1.2f
                green = mix(((pixel shr 8) and 255) / 255f, scene.ledRing[1], ring) * 1.2f
                blue = mix((pixel and 255) / 255f, scene.ledRing[2], ring) * 1.2f
            }
        }
        val keep = if (hash(column * 1.7f, row.toFloat()) * .999f >= uniforms[12]) 1f else 0f
        val slab = if (row % 2 == 0) 1f else .65f
        val gain = slab * CityEventHeroTheme.scene.ledGain + .3f
        val flash = uniforms[9] * .6f * slab
        fun byte(value: Float, hot: Float): Int = ((value * keep * gain + hot * flash) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(red, scene.ledHot[0]) shl 16) or (byte(green, scene.ledHot[1]) shl 8) or byte(blue, scene.ledHot[2])
    }

    private fun hash(x: Float, y: Float): Float {
        val value = sin(x * 12.9898f + y * 78.233f) * 43758.5453f
        return value - floor(value)
    }

    private fun mix(a: Float, b: Float, p: Float): Float = a + (b - a) * p

    private fun smooth(a: Float, b: Float, value: Float): Float {
        val p = ((value - a) / (b - a)).coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}
