package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** API 26–32 fallback: evaluate the same shader equations into display-density bitmaps. */
internal class IslandPrintCameraFallback(private val plate: Bitmap) {
    private class Surface {
        var bitmap: Bitmap? = null
        var pixels = IntArray(0)
        var key = ""
    }
    private val surfaces = Array(5) { Surface() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photo = IntArray(plate.width * plate.height).also { plate.getPixels(it, 0, plate.width, 0, 0, plate.width, plate.height) }

    private fun smooth(a: Double, b: Double, v: Double): Double {
        val k = ((v - a) / (b - a)).coerceIn(0.0, 1.0)
        return k * k * (3 - 2 * k)
    }
    private fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    private fun sd(x: Double, y: Double, bx: Double, by: Double, r: Double): Double {
        val qx = abs(x) - bx + r
        val qy = abs(y) - by + r
        return hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r
    }
    private fun pack(r: Double, g: Double, b: Double, a: Double): Int {
        if (a <= 0) return 0
        return Color.argb((a.coerceIn(0.0, 1.0) * 255).roundToInt(),
            ((r / a).coerceIn(0.0, 1.0) * 255).roundToInt(),
            ((g / a).coerceIn(0.0, 1.0) * 255).roundToInt(),
            ((b / a).coerceIn(0.0, 1.0) * 255).roundToInt())
    }
    private inline fun raster(canvas: Canvas, id: Int, bounds: RectF, scale: Float, key: String, sample: (Double, Double) -> Int) {
        val surface = surfaces[id]
        val w = max(1, ceil(bounds.width() * scale).toInt())
        val h = max(1, ceil(bounds.height() * scale).toInt())
        if (surface.bitmap?.width != w || surface.bitmap?.height != h) {
            surface.bitmap?.recycle()
            surface.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            surface.pixels = IntArray(w * h)
            surface.key = ""
        }
        if (surface.key != key) {
            for (y in 0 until h) for (x in 0 until w) {
                surface.pixels[y * w + x] = sample(bounds.left + (x + .5) * bounds.width() / w, bounds.top + (y + .5) * bounds.height() / h)
            }
            surface.bitmap?.setPixels(surface.pixels, 0, w, 0, 0, w, h)
            surface.key = key
        }
        surface.bitmap?.let { canvas.drawBitmap(it, null, bounds, paint) }
    }

    fun body(canvas: Canvas, body: RectF, radius: Float, f: IslandPrintCameraFrame, scale: Float) {
        val bx = body.width() / 2.0
        val by = body.height() / 2.0 + f.slotDip / 2
        val cx = body.left + bx
        val cy = body.top + body.height() / 2.0 + f.slotDip / 2
        val r = min(radius.toDouble(), min(bx, body.height() / 2.0))
        raster(canvas, 0, RectF(0f, 0f, 402f, 430f), scale, "$body/$radius/${f.slotDip}/${f.bloom}") { x, y ->
            val d = sd(x - cx, y - cy, bx, by, r)
            val ds = sd(x - cx, y - cy - 6, bx, by, r)
            val sh = .30 * (1 - smooth(-4.0, 14.0, ds))
            var cr = .478 * sh; var cg = .059 * sh; var cb = .047 * sh; var ca = sh
            val band = 1 - smooth(0.0, 3.2, d)
            val ny = ((y - cy) / max(by, 1.0)).coerceIn(-1.0, 1.0)
            val k = smooth(-.5, .5, ny)
            val la = band * mix(.6, .65, k)
            cr = mix(.957, .725, k) * la + cr * (1 - la)
            cg = mix(.329, .133, k) * la + cg * (1 - la)
            cb = mix(.290, .118, k) * la + cb * (1 - la)
            ca = la + ca * (1 - la)
            val bl = if (d >= 0) f.bloom * exp(-max(d, 0.0) / 40) else 0.0
            cr = bl + cr * (1 - bl); cg = bl + cg * (1 - bl); cb = bl + cb * (1 - bl); ca = bl + ca * (1 - bl)
            val inside = 1 - smooth(-.6, .6, d)
            pack(.039 * inside + cr * (1 - inside), .024 * inside + cg * (1 - inside),
                .027 * inside + cb * (1 - inside), inside + ca * (1 - inside))
        }
    }

    fun dome(canvas: Canvas, i: Int, f: IslandPrintCameraFrame, flashOn: Boolean, scale: Float) {
        val cx = doubleArrayOf(73.0, 201.0, 329.0)[i]
        val r = doubleArrayOf(24.0, 41.0, 25.0)[i]
        val rise = max(f.rise[i], 0.0)
        val depth = if (i == 1) f.depth else 0.0
        val ring = i == 2
        val halo = if (i == 0) f.ledHalo else 0.0
        val led = when {
            f.led == 2 -> doubleArrayOf(224.0/255, 48.0/255, 30.0/255)
            f.led == 1 -> doubleArrayOf(166.0/255, 209.0/255, 31.0/255)
            flashOn -> doubleArrayOf(245.0/255, 166.0/255, 35.0/255)
            else -> doubleArrayOf(47.0/255, 56.0/255, 21.0/255)
        }
        val shell = doubleArrayOf(232.0/255, 40.0/255, 34.0/255)
        val dome = if (i == 0) led else if (i == 1) doubleArrayOf(245.0/255, 118.0/255, 2.0/255) else shell
        val reach = if (i == 1) 62f else 44f
        val bounds = RectF(cx.toFloat() - reach, 362f - reach, cx.toFloat() + reach, 362f - reach + if (i == 1) 130f else 92f)
        val height = (if (ring) .10 else .22) * 2 * r * rise * (1 - .8 * depth)
        val ly = -.82 / hypot(.82, .57); val lz = .57 / hypot(.82, .57)
        val hvn = hypot(ly, lz + 1); val hy = ly / hvn; val hz = (lz + 1) / hvn
        raster(canvas, i + 1, bounds, scale, "$rise/$depth/${f.flood[i]}/${f.dim}/$halo/${f.led}/$flashOn") { x, y ->
            val qx = x - cx; val qy = y - 362
            val qr = hypot(qx, qy); val rr = qr / r
            val dpt = hypot(qx, qy - 2 * rise) - r
            val sa = rise * (1 - .6 * depth) * (.30 * (1 - smooth(-1.0, 3.0, dpt)) + .18 * (1 - smooth(-2.0, 14.0, dpt - 4)))
            var cr = .478 * sa; var cg = .059 * sa; var cb = .047 * sa; var ca = sa
            if (halo > 0 && qr >= r) {
                val h = halo * (rise * 4).coerceIn(0.0, 1.0) * exp(-max(qr - r, 0.0) / 16)
                cr = led[0] * h + cr * (1 - h); cg = led[1] * h + cg * (1 - h); cb = led[2] * h + cb * (1 - h); ca = h + ca * (1 - h)
            }
            if (rr < 1) {
                val s = sqrt(max(1 - rr * rr, 1e-4))
                val nx = qx / (r * r * s) * height
                val ny = qy / (r * r * s) * height
                val norm = sqrt(nx * nx + ny * ny + 1)
                val diff = ((ny * ly + lz) / norm).coerceIn(0.0, 1.0)
                val flood = if (ring) 0.0 else 1 - smooth(f.flood[i] * 1.25 - .25, f.flood[i] * 1.25, (qy + r) / (2 * r))
                val spec = max((ny * hy + hz) / norm, 0.0).pow(22) * .32 * rise.coerceIn(0.0, 1.0) * (1 - .3 * depth)
                val ao = (1 - .4 * smooth(.75, 1.0, rr) * rise.coerceIn(0.0, 1.0)) * f.dim
                var red = ((mix(shell[0], dome[0], flood) * (.62 + .48 * diff) + spec) * ao).coerceIn(0.0, 1.0)
                var green = ((mix(shell[1], dome[1], flood) * (.62 + .48 * diff) + spec) * ao).coerceIn(0.0, 1.0)
                var blue = ((mix(shell[2], dome[2], flood) * (.62 + .48 * diff) + spec) * ao).coerceIn(0.0, 1.0)
                if (ring) {
                    val k = (1 - smooth(.55, 1.05, abs(qr - (r - .9)))) * (.35 + rise).coerceIn(0.0, 1.0)
                    red = mix(red, .722, k); green = mix(green, .122, k); blue = mix(blue, .110, k)
                }
                val a = (1 - smooth(.985, 1.0, rr)) * (rise * 6).coerceIn(0.0, 1.0)
                cr = red * a + cr * (1 - a); cg = green * a + cg * (1 - a); cb = blue * a + cb * (1 - a); ca = a + ca * (1 - a)
            }
            pack(cr, cg, cb, ca)
        }
    }

    private fun hash(x: Double, y: Double): Double {
        val v = sin(x * 127.1 + y * 311.7) * 43758.5453
        return v - floor(v)
    }
    private fun noise(x: Double, y: Double): Double {
        val ix = floor(x); val iy = floor(y)
        val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        return mix(mix(hash(ix, iy), hash(ix + 1, iy), ux), mix(hash(ix, iy + 1), hash(ix + 1, iy + 1), ux), uy)
    }
    fun photo(canvas: Canvas, d: Double, scale: Float) {
        raster(canvas, 4, RectF(13f, 22f, 193f, 202f), scale, d.toString()) { x, y ->
            val sx = ((x - 13) / 180 * plate.width - .5).coerceIn(0.0, plate.width - 1.0)
            val sy = ((y - 22) / 180 * plate.height - .5).coerceIn(0.0, plate.height - 1.0)
            val ix = floor(sx).toInt(); val iy = floor(sy).toInt()
            val c00 = photo[iy * plate.width + ix]
            val c10 = photo[iy * plate.width + min(ix + 1, plate.width - 1)]
            val c01 = photo[min(iy + 1, plate.height - 1) * plate.width + ix]
            val c11 = photo[min(iy + 1, plate.height - 1) * plate.width + min(ix + 1, plate.width - 1)]
            fun channel(shift: Int) = mix(mix(((c00 ushr shift) and 255).toDouble(), ((c10 ushr shift) and 255).toDouble(), sx - ix),
                mix(((c01 ushr shift) and 255).toDouble(), ((c11 ushr shift) and 255).toDouble(), sx - ix), sy - iy) / 255
            val cr = channel(16); val cg = channel(8); val cb = channel(0)
            val n = (noise(x / 180 * 4, y / 180 * 4) * .65 + noise(x / 180 * 9, y / 180 * 9) * .35 - .5) * .16
            val dd = d + n
            val con = mix(.7, 1.0, d.coerceIn(0.0, 1.0)); val sat = mix(.5, 1.0, d.coerceIn(0.0, 1.0))
            val luminance = cr * .299 + cg * .587 + cb * .114
            fun developed(c: Double) = (mix(luminance, c, sat) - .5) * con + .5
            val ghostR=mix(.80,.20,1-luminance); val ghostG=mix(.87,.33,1-luminance); val ghostB=mix(.89,.39,1-luminance)
            val reveal=smooth(0.0,.45,dd)
            pack(mix(mix(.878,ghostR,reveal),developed(cr),smooth(.55,1.0,dd)),mix(mix(.878,ghostG,reveal),developed(cg),smooth(.35,.80,dd)),mix(mix(.886,ghostB,reveal),developed(cb),smooth(.45,.90,dd)),1.0)
        }
    }
}
