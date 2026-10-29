package com.ngocdevv.nativecook.blocks.cards.glassdomeshowroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import kotlin.math.floor
import kotlin.math.sin

/** One car of the invented Nuvra line-up, with the colour wash it brings to the screen. */
internal class GlassDomeShowroomCar(
    val model: String,
    val trim: String,
    val info: String,
    val file: String,
    /** Wash: top, middle (55 % down) and bottom of the screen. */
    val top: Int,
    val mid: Int,
    val bottom: Int,
)

internal object GlassDomeShowroomArt {
    val cars = listOf(
        GlassDomeShowroomCar("Kite 3", "Spark", "268 mi range · from \$219/mo", "cobalt", 0x5A93DA, 0x2B5FAA, 0x0D2246),
        GlassDomeShowroomCar("Torva", "Line", "301 mi range · from \$259/mo", "red", 0xCF4A57, 0x9C1D2C, 0x36060D),
        GlassDomeShowroomCar("Orla 4", "Pro", "322 mi range · from \$289/mo", "aqua", 0x3DB8CF, 0x14879E, 0x063A47),
        GlassDomeShowroomCar("Nimbus", "Lux Pro", "347 mi range · from \$339/mo", "pearl", 0xA8B3BE, 0x75818E, 0x252C34),
        GlassDomeShowroomCar("Zest", "Sport", "244 mi range · from \$199/mo", "lime", 0xADBD3A, 0x78881D, 0x262E08),
    )

    const val HEART_RED = 0xFF3B4E
    const val HEART_PINK = 0xFF8A9A

    /** The car artwork, decoded once. */
    fun loadImages(context: Context): List<Bitmap?> = cars.map { car ->
        runCatching {
            context.assets.open("glassdomeshowroom/glassdomeshowroom_car_${car.file}.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })?.also { it.prepareToDraw() }
            }
        }.getOrNull()
    }

    /** ARGB from a 0xRRGGBB colour and an alpha in 0…1. */
    fun argb(hex: Int, alpha: Double = 1.0): Int {
        val a = (alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        return (a shl 24) or (hex and 0xFFFFFF)
    }

    fun mix(a: Int, b: Int, f: Double): Int {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f + 0.5).toInt().coerceIn(0, 255)
        return (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    /** The wash for carousel position φ (top, middle, bottom as 0xRRGGBB): the two nearest cars mixed. */
    fun wash(phi: Double): IntArray {
        val n = cars.size
        val base = floor(phi)
        val f = phi - base
        val i = Math.floorMod(base.toInt(), n)
        val a = cars[i]
        val b = cars[(i + 1) % n]
        return intArrayOf(mix(a.top, b.top, f), mix(a.mid, b.mid, f), mix(a.bottom, b.bottom, f))
    }

    /** A heart [size] pt wide centred on the origin (the 24-pt "favourite" glyph, redrawn). */
    fun heart(size: Float, out: Path = Path()): Path {
        val s = size / 20f
        fun x(v: Float) = (v - 12f) * s
        fun y(v: Float) = (v - 12.18f) * s
        out.reset()
        out.moveTo(x(12f), y(21.35f))
        out.lineTo(x(10.55f), y(20.03f))
        out.cubicTo(x(5.4f), y(15.36f), x(2f), y(12.28f), x(2f), y(8.5f))
        out.cubicTo(x(2f), y(5.42f), x(4.42f), y(3f), x(7.5f), y(3f))
        out.cubicTo(x(9.24f), y(3f), x(10.91f), y(3.81f), x(12f), y(5.09f))
        out.cubicTo(x(13.09f), y(3.81f), x(14.76f), y(3f), x(16.5f), y(3f))
        out.cubicTo(x(19.58f), y(3f), x(22f), y(5.42f), x(22f), y(8.5f))
        out.cubicTo(x(22f), y(12.28f), x(18.6f), y(15.36f), x(13.45f), y(20.04f))
        out.close()
        return out
    }

    fun hash(i: Double): Double {
        val v = sin(i * 12.9898) * 43758.5453
        return v - floor(v)
    }
}
