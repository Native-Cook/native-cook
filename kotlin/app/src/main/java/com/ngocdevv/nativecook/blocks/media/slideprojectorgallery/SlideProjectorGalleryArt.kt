package com.ngocdevv.nativecook.blocks.media.slideprojectorgallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/*
 Slide Projector Gallery — layout, the slides, and the art drawn once off the main thread (a port
 of projectorArt.ts): the atlas of slides at three focus levels (blurred here with three box
 passes per axis, the usual stand-in for a Gaussian), and the projector (a walnut-and-cream body,
 the lens barrel, the round carousel tray's walls) as a bitmap with transparency over the wall.
 The tray's slide mounts turn, so they are rebuilt as two paths per frame (`trayMounts`). Below
 API 33 the wall itself is also baked here on the CPU with the shader's own noise.
 */

internal class SlideProjectorSlide(val title: String, val meta: String)

internal val slideProjectorSlides = listOf(
    SlideProjectorSlide("Aunt Ivy at Crescent Point", "July 1987 · Kodachrome-style transparency"),
    SlideProjectorSlide("Under the striped umbrellas", "Pine Cove · August 1987"),
    SlideProjectorSlide("Lake day, watermelon for lunch", "Lake Wren · July 1987"),
    SlideProjectorSlide("The lifeguard's chair", "Long Strand · August 1987"),
    SlideProjectorSlide("Dad's blue coupe", "Driveway, Elm Street · June 1987"),
)

private val slideProjectorPhotos = listOf("seaside", "umbrellas", "picnic", "lifeguard", "chrome")

internal const val SLIDE_TILE_W = 600
internal const val SLIDE_TILE_H = 400
internal const val SLIDE_HEADER_H = 92f
internal const val SLIDE_KEY_TOP = 1.045f
internal const val SLIDE_KEY_BOTTOM = 0.955f

internal class SlideProjectorLayout(val W: Float, val H: Float, top: Float, bottom: Float) {
    val projCx = W / 2f
    val projTop = top + 12f + SLIDE_HEADER_H
    val projW = W - 40f
    val projH = projW / 1.5f
    val captionY = projTop + projH + 16f
    val trayCx = W / 2f
    val trayRx = min(W * 0.42f, 170f)
    val trayRy = trayRx * 0.34f
    val trayCy = H - bottom - trayRy - 52f
    val traySide = 18f
    val bodyTop = trayCy - trayRy - 40f
    val lensX = W / 2f
    val lensY = bodyTop - 20f
}

internal fun slideArgb(r: Int, g: Int, b: Int, a: Float = 1f) =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

internal fun slideHex(hex: Long) = (0xFF000000 or hex).toInt()

internal class SlideProjectorArt(val atlas: Bitmap, val body: Bitmap, val wall: Bitmap?)

/** The slides at three focus levels; independent of the layout, so baked once. */
internal fun slideProjectorAtlas(context: Context, job: Job? = null): Bitmap {
    val photos = slideProjectorPhotos.map { n ->
        context.assets.open("slideprojectorgallery/slideprojectorgallery_$n.jpg").use { s ->
            BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })!!
        }
    }
    val atlas = bakeAtlas(photos, job)
    photos.forEach { it.recycle() }
    return atlas
}

/** The projector (and, below API 33, the wall): keyed on the layout, so baked once the insets settle. */
internal fun slideProjectorBody(L: SlideProjectorLayout, scale: Float, needWall: Boolean, job: Job? = null): Pair<Bitmap, Bitmap?> {
    val body = bakeProjector(L, scale)
    job?.ensureActive()
    return body to (if (needWall) bakeWall(L, job) else null)
}

/** Three box blurs in each direction, ≈ a Gaussian of σ; edges clamp. */
private fun boxBlur(px: IntArray, w: Int, h: Int, sigma: Float) {
    val width = sqrt(4f * sigma * sigma + 1f).roundToInt() or 1
    val r = width / 2
    val tmp = IntArray(px.size)
    repeat(3) {
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }
}

private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val n = if (horizontal) w else h
    val div = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val k = i.coerceIn(0, n - 1)
            return if (horizontal) src[line * w + k] else src[k * w + line]
        }
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) { val c = at(i); sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255 }
        for (i in 0 until n) {
            val o = if (horizontal) line * w + i else i * w + line
            dst[o] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val a = at(i + r + 1)
            val b = at(i - r)
            sr += ((a shr 16) and 255) - ((b shr 16) and 255)
            sg += ((a shr 8) and 255) - ((b shr 8) and 255)
            sb += (a and 255) - (b and 255)
        }
    }
}

/**
 * Five slides × (sharp, soft, softer) in a 3 × 5 grid of TILE_W × TILE_H tiles. The blurred levels
 * are blurred at half (σ 4) and quarter (σ 12) size and scaled back up: the same look for a
 * fraction of the work, which kept the screen blank for seconds on an emulator.
 */
private fun bakeAtlas(photos: List<Bitmap>, job: Job?): Bitmap {
    val tw = SLIDE_TILE_W
    val th = SLIDE_TILE_H
    val atlas = Bitmap.createBitmap(tw * 3, th * photos.size, Bitmap.Config.ARGB_8888)
    val c = Canvas(atlas)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val dst = RectF()
    photos.forEachIndexed { row, img ->
        job?.ensureActive()
        dst.set(0f, (row * th).toFloat(), tw.toFloat(), ((row + 1) * th).toFloat())
        c.drawBitmap(img, null, dst, paint)
        for ((col, sigma, down) in listOf(Triple(1, 4f, 2), Triple(2, 12f, 4))) {
            val sw = tw / down
            val sh = th / down
            val small = Bitmap.createScaledBitmap(img, sw, sh, true)
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            boxBlur(px, sw, sh, sigma / down)
            small.setPixels(px, 0, sw, 0, 0, sw, sh)
            dst.set((col * tw).toFloat(), (row * th).toFloat(), ((col + 1) * tw).toFloat(), ((row + 1) * th).toFloat())
            c.drawBitmap(small, null, dst, paint)
            small.recycle()
        }
    }
    return atlas
}

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, p: Paint) = c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)

/** The projector and the tray's static parts, transparent elsewhere. */
private fun bakeProjector(L: SlideProjectorLayout, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(L.W * scale).toInt(), ceil(L.H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val W = L.W
    val H = L.H
    val bodyTop = L.bodyTop
    val cx = L.trayCx
    val cy = L.trayCy
    val rx = L.trayRx
    val ry = L.trayRy

    // Body: two shadows on the wall side, then a walnut shell with a cream top deck.
    c.drawRoundRect(10f, bodyTop - 10f, W - 10f, H + 30f, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(0, 0, 0, 0.55f); maskFilter = blur(22f) })
    c.drawRoundRect(14f, bodyTop, W - 14f, H + 30f, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, bodyTop, 0f, H, intArrayOf(slideHex(0x5A3E2C), slideHex(0x3E2A1E), slideHex(0x2A1C14)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    })
    c.drawRoundRect(22f, bodyTop + 6f, W - 22f, cy + ry + 30f, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, bodyTop + 6f, 0f, cy + ry + 30f, intArrayOf(slideHex(0xD9CBB2), slideHex(0xC4B394), slideHex(0xA8977A)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    })
    c.drawRoundRect(22.5f, bodyTop + 6.5f, W - 22.5f, cy + ry + 29.5f, 23.5f, 23.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = slideArgb(255, 250, 236, 0.55f) })
    // Vent slots either side of the tray.
    val vent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(60, 44, 30, 0.55f) }
    for (i in 0 until 6) {
        val y = bodyTop + 22f + i * 7f
        c.drawRoundRect(34f, y, 60f, y + 2.6f, 1.3f, 1.3f, vent)
        c.drawRoundRect(W - 60f, y, W - 34f, y + 2.6f, 1.3f, 1.3f, vent)
    }
    // A chrome trim along the shell.
    c.drawRect(18f, cy + ry + 36f, W - 18f, cy + ry + 38f, Paint().apply {
        shader = LinearGradient(14f, 0f, W - 14f, 0f, intArrayOf(slideHex(0x8F8A84), slideHex(0xF2EEE8), slideHex(0x8F8A84)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })

    // Lens barrel: a short black cylinder with a chrome ring, pointing at the wall.
    val lx = L.lensX
    val ly = L.lensY
    val barrel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(lx - 24f, 0f, lx + 24f, 0f, intArrayOf(slideHex(0x1A1716), slideHex(0x4A4542), slideHex(0x121010)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(lx - 24f, ly, lx + 24f, bodyTop + 12f, barrel)
    oval(c, lx, bodyTop + 12f, 24f, 8f, barrel)
    oval(c, lx, ly, 26f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(lx - 26f, 0f, lx + 26f, 0f, intArrayOf(slideHex(0x7F7A75), slideHex(0xF4F0EA), slideHex(0x6E6964)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    })
    oval(c, lx, ly, 20f, 6.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(lx - 5f, ly - 2f, 22f, intArrayOf(slideHex(0x6E7C86), slideHex(0x1D2328), slideHex(0x0B0D0F)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })

    // Tray: drop shadow, the outer wall (a cylinder band), the top rim and the dark slot well.
    oval(c, cx, cy + L.traySide + 6f, rx + 4f, ry + 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideArgb(30, 20, 10, 0.45f); maskFilter = blur(10f) })
    val band = Path().apply {
        moveTo(cx - rx, cy)
        lineTo(cx - rx, cy + L.traySide)
        arcTo(RectF(cx - rx, cy + L.traySide - ry, cx + rx, cy + L.traySide + ry), 180f, -180f, false)
        lineTo(cx + rx, cy)
        close()
    }
    c.drawPath(band, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(cx - rx, 0f, cx + rx, 0f, intArrayOf(slideHex(0x1E1B1A), slideHex(0x4B4644), slideHex(0x2A2625), slideHex(0x141211)), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    })
    oval(c, cx, cy, rx, ry, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, cy - ry, 0f, cy + ry, intArrayOf(slideHex(0x3A3532), slideHex(0x2A2624), slideHex(0x3C3734)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    oval(c, cx, cy, rx * 0.92f, ry * 0.92f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slideHex(0x141110) })
    oval(c, cx, cy, rx - 0.6f, ry - 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = slideArgb(255, 240, 220, 0.25f) })

    // Grain on the body.
    val tile = noiseTile(5)
    c.save()
    c.clipPath(Path().apply { addRoundRect(14f, bodyTop, W - 14f, H + 30f, 30f, 30f, Path.Direction.CW) })
    c.drawRect(0f, bodyTop, W, H, Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        alpha = (0.06f * 255).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    })
    c.restore()
    return bmp
}

private fun noiseTile(seed: Int): Bitmap {
    val n = 160
    val rnd = Random(seed)
    val px = IntArray(n * n) {
        val v = (rnd.nextFloat() * 255f).toInt()
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

private fun fract(x: Float) = x - floor(x)
private fun hash(x: Float, y: Float) = fract(sin(x * 127.1f + y * 311.7f) * 43758.5453f)
private fun vnoise(x: Float, y: Float): Float {
    val ix = floor(x); val iy = floor(y)
    var fx = x - ix; var fy = y - iy
    fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy)
    val a = hash(ix, iy); val b = hash(ix + 1f, iy); val c = hash(ix, iy + 1f); val d = hash(ix + 1f, iy + 1f)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy
}

/**
 * Below API 33: the shader's wall on the CPU, at 1.5 px per dp (the grain's own cell size). The
 * mottling is low-frequency, so it is computed on a coarse grid (every 8 px) and interpolated.
 */
private fun bakeWall(L: SlideProjectorLayout, job: Job?): Bitmap {
    val k = 1.5f
    val w = ceil(L.W * k).toInt()
    val h = ceil(L.H * k).toInt()
    val step = 8
    val gw = w / step + 2
    val gh = h / step + 2
    val coarse = FloatArray(gw * gh)
    for (gj in 0 until gh) for (gi in 0 until gw) {
        val x = gi * step / k
        val y = gj * step / k
        coarse[gj * gw + gi] = vnoise(x * 0.012f, y * 0.012f) * 0.6f + vnoise(x * 0.045f, y * 0.045f) * 0.4f
    }
    val px = IntArray(w * h)
    for (j in 0 until h) {
        job?.ensureActive()
        val y = (j + 0.5f) / k
        val gy = (j + 0.5f) / step
        val j0 = min(gh - 2, gy.toInt())
        val fy = gy - j0
        for (i in 0 until w) {
            val x = (i + 0.5f) / k
            val gx = (i + 0.5f) / step
            val i0 = min(gw - 2, gx.toInt())
            val fx = gx - i0
            val a = coarse[j0 * gw + i0]
            val b = coarse[j0 * gw + i0 + 1]
            val cc = coarse[(j0 + 1) * gw + i0]
            val d = coarse[(j0 + 1) * gw + i0 + 1]
            val mott = (a + (b - a) * fx) + ((cc + (d - cc) * fx) - (a + (b - a) * fx)) * fy
            val grain = hash(floor(x * 1.5f), floor(y * 1.5f)) - 0.5f
            val stripe = 0.5f + 0.5f * sin(x * 0.33f)
            val tex = 0.86f + 0.14f * mott + 0.035f * grain
            val vx = (x - L.W * 0.5f) / L.W
            val vy = (y - L.H * 0.38f) / L.H
            val m = tex * (0.97f + 0.03f * stripe) * (1f - 0.9f * (vx * vx + vy * vy))
            val r = (0.165f * m * 255f).roundToInt().coerceIn(0, 255)
            val g = (0.128f * m * 255f).roundToInt().coerceIn(0, 255)
            val bl = (0.108f * m * 255f).roundToInt().coerceIn(0, 255)
            px[j * w + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/**
 * The cardboard slide mounts standing in the tray at `pos` (slots turned), into two paths: the far
 * half (in shade) and the near half (lit). The slot at the gate (far side, 12 o'clock) is empty.
 */
internal fun slideProjectorMounts(L: SlideProjectorLayout, pos: Float, slots: Int, far: Path, near: Path) {
    far.rewind()
    near.rewind()
    val step = (2 * PI / slots).toFloat()
    for (k in 0 until slots) {
        val a = -PI.toFloat() / 2f + (k - pos) * step
        val d = abs(atan2(sin(a + PI.toFloat() / 2f), cos(a + PI.toFloat() / 2f)))
        if (d < step * 0.5f) continue
        val ca = cos(a)
        val sa = sin(a)
        val x0 = L.trayCx + ca * L.trayRx * 0.5f
        val y0 = L.trayCy + sa * L.trayRy * 0.5f
        val x1 = L.trayCx + ca * L.trayRx * 0.88f
        val y1 = L.trayCy + sa * L.trayRy * 0.88f
        val up = 3f + 4f * (sa + 1f) * 0.5f
        val nx = -sa * L.trayRy
        val ny = ca * L.trayRx
        val nl = max(1e-3f, hypot(nx, ny))
        val tx = nx / nl * 0.9f
        val ty = ny / nl * 0.9f
        val b = if (sa > 0f) near else far
        b.moveTo(x0 - tx, y0 - ty - up)
        b.lineTo(x1 - tx, y1 - ty - up)
        b.lineTo(x1 + tx, y1 + ty - up)
        b.lineTo(x1 + tx, y1 + ty)
        b.lineTo(x0 + tx, y0 + ty)
        b.close()
    }
}
