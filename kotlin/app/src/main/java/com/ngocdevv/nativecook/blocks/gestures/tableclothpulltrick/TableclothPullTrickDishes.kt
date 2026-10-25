package com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.clamp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/*
 The place settings for six, drawn every frame in table-plane coordinates (each through the plane's
 affine matrix at its spot, so a round plate is a true perspective ellipse) or, for upright things
 (glasses, candles), in screen space from their projected base. Every item carries its own jiggle.

 What never changes in an item's own frame (the plate, the napkin, the fork and knife, the soft
 shadows) is baked once into sprites and drawn through the plane matrix, so a frame blurs nothing:
 a shadow that softens as its dish hops is the same sprite, grown and faded.
 */

/** kind: 0 plate, 1 glass, 2 fork, 3 knife, 4 candle. */
private class ClothItem(val kind: Int, val x: Double, val y: Double, val id: Int)

private val SETTINGS = arrayOf(
    doubleArrayOf(-110.0, 250.0),
    doubleArrayOf(0.0, 250.0),
    doubleArrayOf(110.0, 250.0),
    doubleArrayOf(-106.0, 82.0),
    doubleArrayOf(0.0, 82.0),
    doubleArrayOf(106.0, 82.0),
)

private val ITEMS: List<ClothItem> = buildList {
    SETTINGS.forEachIndexed { i, s ->
        add(ClothItem(0, s[0], s[1], i))
        add(ClothItem(1, s[0] + 34, s[1] + 36, 10 + i))
        add(ClothItem(2, s[0] - 44, s[1], 20 + i))
        add(ClothItem(3, s[0] + 44, s[1] - 4, 30 + i))
    }
    add(ClothItem(4, -55.0, 168.0, 40))
    add(ClothItem(4, 55.0, 162.0, 41))
}.sortedByDescending { it.y } // Farthest first (a stable sort, like the original's).

private fun c(vararg v: Pair<Int, Float>) = clothColors(*v)
private fun f(vararg v: Float) = v

/** A baked image of something in its own (plane) frame, and the rect of that frame it covers. */
internal class ClothSprite(val bitmap: Bitmap, val rect: RectF)

private fun sprite(x0: Float, y0: Float, x1: Float, y1: Float, scale: Float, draw: (Canvas, ClothKit) -> Unit): ClothSprite {
    val k = ClothKit()
    val bmp = clothBake(x1 - x0, y1 - y0, scale) { cv ->
        cv.translate(-x0, -y0)
        draw(cv, k)
    }
    return ClothSprite(bmp, RectF(x0, y0, x1, y1))
}

/** Every sprite of the table, baked once off the main thread. */
internal class ClothSprites(scale: Float) {
    /** Plate shadows: the soft ambient one (r 35, blur 5) and the tight contact one (r 34.5, blur 1.5). */
    val plateShadowSoft = sprite(-55f, -55f, 55f, 55f, scale) { cv, k -> cv.drawCircle(0f, 0f, 35f, k.fill(0x2a1508, 1f, 5f)) }
    val plateShadowTight = sprite(-42f, -42f, 42f, 42f, scale) { cv, k -> cv.drawCircle(0f, 0f, 34.5f, k.fill(0x2a1508, 1f, 1.5f)) }
    val plate = sprite(-36f, -36f, 36f, 36f, scale) { cv, k ->
        cv.drawCircle(
            0f, 0f, 34f,
            k.radial(0f, 0f, 34f, c(0xf6f2ea to 1f, 0xfbf9f3 to 1f, 0xe2dacb to 1f, 0xfaf7f0 to 1f, 0xd8cfbf to 1f), f(0f, 0.6f, 0.66f, 0.82f, 1f)),
        )
        cv.drawCircle(0f, 0f, 30.5f, k.stroke(0xc9a45a, 0.9f, 0.9f))
        cv.save()
        cv.clipRect(-36f, -36f, 36f, 36f)
        cv.drawCircle(-10f, 12f, 9f, k.fill(0xffffff, 0.55f, 5f))
        cv.restore()
    }
    /** The folded napkin with its own small shadow, centred on the plate. */
    val napkin = sprite(-22f, -18f, 22f, 16f, scale) { cv, k ->
        cv.drawRoundRect(-14f, -10.5f, 16f, 7.5f, 2.5f, 2.5f, k.fill(0x5a2a12, 0.25f, 1.5f))
        cv.drawRoundRect(-15f, -9f, 15f, 9f, 2.5f, 2.5f, k.linear(0f, -9f, 0f, 9f, c(0xd27a52 to 1f, 0xc0603a to 1f, 0x9e4a2a to 1f), f(0f, 0.5f, 1f)))
        cv.drawLine(-15f, 1f, 15f, 1f, k.stroke(0x8a3a1e, 0.8f, 0.5f))
        cv.drawLine(-14f, -7.5f, 14f, -7.5f, k.stroke(0xf0a882, 0.7f, 0.6f))
    }
    val fork = sprite(-5f, -26f, 5f, 21f, scale) { cv, k -> cutlery(cv, k, false, false) }
    val forkShadow = sprite(-10f, -31f, 10f, 26f, scale) { cv, k -> cutlery(cv, k, false, true) }
    val knife = sprite(-5f, -26f, 5f, 28f, scale) { cv, k -> cutlery(cv, k, true, false) }
    val knifeShadow = sprite(-10f, -31f, 10f, 33f, scale) { cv, k -> cutlery(cv, k, true, true) }
    /** Glass shadow (oval 24 × 30, blur 6) and candle shadow (oval 32 × 40, blur 8). */
    val glassShadow = sprite(-32f, -36f, 32f, 34f, scale) { cv, k -> cv.drawOval(-12f, -16f, 12f, 14f, k.fill(0x2a1508, 1f, 6f)) }
    val candleShadow = sprite(-42f, -48f, 42f, 44f, scale) { cv, k -> cv.drawOval(-16f, -22f, 16f, 18f, k.fill(0x2a1508, 1f, 8f)) }
}

private fun cutlery(cv: Canvas, k: ClothKit, knife: Boolean, shadow: Boolean) {
    val p = if (shadow) k.fill(0x1a0c04, 1f, 1.5f) else k.linear(-3f, 0f, 3f, 0f, c(0x8d939b to 1f, 0xf2f4f6 to 1f, 0xa9afb7 to 1f), f(0f, 0.45f, 1f))
    // One path for the whole piece, so the shadow's blur does not double where parts overlap.
    val path = android.graphics.Path()
    path.addRoundRect(-1.9f, -24f, 1.9f, 2f, 1.9f, 1.9f, android.graphics.Path.Direction.CW)
    if (knife) {
        val blade = android.graphics.Path()
        blade.moveTo(-1.6f, 2f)
        blade.lineTo(1.9f, 2f)
        blade.lineTo(1.9f, 24f)
        blade.quadTo(0f, 27f, -1.6f, 22f)
        blade.close()
        path.op(blade, android.graphics.Path.Op.UNION)
    } else {
        val head = android.graphics.Path()
        head.addRoundRect(-3.6f, 2f, 3.6f, 8f, 2f, 2f, android.graphics.Path.Direction.CW)
        for (t in 0 until 4) head.addRect(-3.6f + t * 2.2f, 7f, -3.6f + t * 2.2f + 1.2f, 19f, android.graphics.Path.Direction.CW)
        path.op(head, android.graphics.Path.Op.UNION)
    }
    cv.drawPath(path, p)
}

/** Draws a sprite through the current matrix, scaled about its frame's origin, at an alpha. */
private fun drawSprite(canvas: Canvas, sp: ClothSprite, paint: Paint, alpha: Float, grow: Float = 1f, dx: Float = 0f, dy: Float = 0f) {
    if (alpha <= 0.003f) return
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    val r = sp.rect
    canvas.save()
    canvas.translate(dx, dy)
    if (grow != 1f) canvas.scale(grow, grow)
    canvas.drawBitmap(sp.bitmap, null, r, paint)
    canvas.restore()
}

/** Where a dish sits for a pose: [x, y, spin, lean, hop, shadow x, shadow y]. */
private fun placed(it: ClothItem, p: ClothPose, j: DoubleArray, out: DoubleArray) {
    ClothMotion.jiggle(p, it.id, it.y, j)
    val weight = if (it.kind == 4) 0.4 else 1.0
    val y0 = it.y - p.slide * (0.8 + 0.2 * ((it.id * 7) % 5) / 4)
    out[0] = it.x + j[0] * weight
    out[1] = y0 + j[1] * weight
    out[2] = j[2] * weight
    out[3] = j[3] * weight + if (it.kind == 1) p.tip else 0.0
    out[4] = j[4] * weight
    out[5] = it.x + j[0] * weight * 0.4
    out[6] = y0 + j[1] * weight * 0.4
}

private fun drawPlate(canvas: Canvas, sc: ClothScratch, g: ClothGeo, sp: ClothSprites, bp: Paint, v: DoubleArray) {
    val hop = v[4]
    val lift = clamp(hop / 9, 0.0, 1.0).toFloat()
    canvas.save()
    g.planeMatrix(v[5], v[6], 0.0, sc.matrix, sc)
    canvas.concat(sc.matrix)
    drawSprite(canvas, sp.plateShadowSoft, bp, 0.3f - 0.1f * lift, (35 + 2 * lift) / 35f, 3f, -5f)
    drawSprite(canvas, sp.plateShadowTight, bp, 0.3f * (1 - lift), 1f, 1f, -1.5f)
    canvas.restore()
    canvas.save()
    g.planeMatrix(v[0], v[1], 1.6 + hop, sc.matrix, sc)
    canvas.concat(sc.matrix)
    drawSprite(canvas, sp.plate, bp, 1f)
    // A folded napkin on the plate: its turn shows the plate's spin.
    canvas.rotate((v[2] * 180 / PI + 18).toFloat())
    drawSprite(canvas, sp.napkin, bp, 1f)
    canvas.restore()
}

private fun drawCutlery(canvas: Canvas, sc: ClothScratch, g: ClothGeo, sp: ClothSprites, bp: Paint, v: DoubleArray, knife: Boolean) {
    val spin = (v[2] * 1.6 * 180 / PI).toFloat()
    canvas.save()
    g.planeMatrix(v[5] + 1.5, v[6] - 2.5, 0.0, sc.matrix, sc)
    canvas.concat(sc.matrix)
    canvas.rotate(spin)
    drawSprite(canvas, if (knife) sp.knifeShadow else sp.forkShadow, bp, 0.35f)
    canvas.restore()
    canvas.save()
    g.planeMatrix(v[0], v[1], 0.8 + v[4], sc.matrix, sc)
    canvas.concat(sc.matrix)
    canvas.rotate(spin)
    drawSprite(canvas, if (knife) sp.knife else sp.fork, bp, 1f)
    canvas.restore()
}

private val GLASS_SIDE = c(0xffffff to 0.35f, 0xd9e4e8 to 0.18f, 0xffffff to 0.4f)
private val GLASS_STEM = c(0xffffff to 0.75f, 0xbfd0d6 to 0.35f)
private val WINE = c(0x7a1424 to 0.92f, 0x4a0812 to 0.95f)
private val STOPS_MID = f(0f, 0.5f, 1f)

private fun drawGlass(canvas: Canvas, k: ClothKit, sc: ClothScratch, g: ClothGeo, sp: ClothSprites, bp: Paint, x: Double, y: Double, lean: Float, hop: Double, sx: Double, sy: Double) {
    g.project(x, y, hop, sc.p1)
    val bx = sc.p1[0].toFloat()
    val by = sc.p1[1].toFloat()
    val s = sc.p1[2].toFloat()
    val cc = 0.72f * s
    // Shadow on the cloth (it stays down when the glass hops), then the glass leaning about its foot.
    canvas.save()
    g.planeMatrix(sx + 4, sy - 10, 0.0, sc.matrix, sc)
    canvas.concat(sc.matrix)
    // The original widens the blur as the glass hops; the baked shadow grows a little and fades instead.
    drawSprite(canvas, sp.glassShadow, bp, 0.22f / (1 + hop.toFloat() * 0.04f), 1 + hop.toFloat() * 0.025f)
    canvas.restore()
    canvas.save()
    canvas.translate(bx, by)
    canvas.rotate(lean)
    val ry = 0.72f
    canvas.drawOval(-11 * s, -11 * s * ry, 11 * s, 11 * s * ry, k.linear(-11 * s, 0f, 11 * s, 0f, GLASS_SIDE, STOPS_MID))
    canvas.drawOval(-11 * s, -11 * s * ry, 11 * s, 11 * s * ry, k.stroke(0xffffff, 0.8f, 0.5f))
    canvas.drawRect(-1.1f * s, -26 * cc, 1.1f * s, 0f, k.linear(-1.1f * s, 0f, 1.1f * s, 0f, GLASS_STEM, null))
    val top = 66 * cc
    val bot = 26 * cc
    val r = 15 * s
    val bowl = sc.path
    bowl.reset()
    bowl.moveTo(-r, -top)
    bowl.cubicTo(-r * 1.05f, -top + 22 * cc, -r * 0.55f, -bot, 0f, -bot)
    bowl.cubicTo(r * 0.55f, -bot, r * 1.05f, -top + 22 * cc, r, -top)
    bowl.close()
    // Wine: clipped to the bowl, its surface kept level against the lean.
    canvas.save()
    canvas.clipPath(bowl)
    canvas.save()
    canvas.translate(0f, -44 * cc)
    canvas.rotate(-lean * 0.85f)
    canvas.drawRect(-40 * s, 0f, 40 * s, 40 * s, k.linear(0f, 0f, 0f, 20 * cc, WINE, null))
    canvas.drawOval(-11 * s, -3.5f * s * ry, 11 * s, 3.5f * s * ry, k.fill(0x9c2436, 0.9f))
    canvas.restore()
    canvas.restore()
    canvas.drawPath(bowl, k.fill(0xe8f2f4, 0.14f))
    canvas.drawPath(bowl, k.stroke(0xffffff, 0.9f, 0.55f))
    canvas.drawOval(-r, -top - r * ry * 0.5f, r, -top + r * ry * 0.5f, k.stroke(0xffffff, 1f, 0.75f))
    canvas.drawLine(-r * 0.62f, -top + 6 * cc, -r * 0.45f, -bot - 8 * cc, k.stroke(0xffffff, 1.6f, 0.6f))
    canvas.restore()
}

private val BRASS = c(0x7a5420 to 1f, 0xf0cf86 to 1f, 0xa77a34 to 1f, 0x5e3e14 to 1f)
private val BRASS_STOPS = f(0f, 0.35f, 0.7f, 1f)
private val WAX = c(0xe9dcc2 to 1f, 0xfff6e2 to 1f, 0xd2c19f to 1f)
private val WAX_STOPS = f(0f, 0.4f, 1f)
private val FLAME = c(0xff8a2a to 1f, 0xffd36a to 1f, 0xfff6d8 to 1f)
private val GLOW = c(0xffb35a to 0.32f, 0xff8a3a to 0.1f, 0xff8a3a to 0f)
private val POOL = c(0xffb35a to 0.2f, 0xffb35a to 0.06f, 0xffb35a to 0f)
private val GLOW_STOPS = f(0f, 0.4f, 1f)
private val POOL_STOPS = f(0f, 0.45f, 1f)

private fun additive(p: Paint): Paint {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.blendMode = BlendMode.PLUS else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    return p
}

private fun drawCandle(canvas: Canvas, k: ClothKit, sc: ClothScratch, g: ClothGeo, sp: ClothSprites, bp: Paint, x: Double, y: Double, lean: Float, time: Double, id: Int) {
    g.project(x, y, 0.0, sc.p1)
    val bx = sc.p1[0].toFloat()
    val by = sc.p1[1].toFloat()
    val s = sc.p1[2].toFloat()
    val cc = 0.72f * s
    canvas.save()
    g.planeMatrix(x + 6, y - 14, 0.0, sc.matrix, sc)
    canvas.concat(sc.matrix)
    drawSprite(canvas, sp.candleShadow, bp, 0.28f)
    canvas.restore()
    canvas.save()
    canvas.translate(bx, by)
    canvas.rotate(lean * 0.5f)
    val brass = k.linear(-14 * s, 0f, 14 * s, 0f, BRASS, BRASS_STOPS)
    canvas.drawOval(-14 * s, -14 * s * 0.36f, 14 * s, -14 * s * 0.36f + 28 * s * 0.72f, brass)
    canvas.drawRect(-3 * s, -30 * cc, 3 * s, 0f, brass)
    canvas.drawOval(-8 * s, -32 * cc - 4 * s, 8 * s, -32 * cc + 4 * s, brass)
    canvas.drawRect(-5 * s, -96 * cc, 5 * s, -32 * cc, k.linear(-5 * s, 0f, 5 * s, 0f, WAX, WAX_STOPS))
    canvas.drawOval(-5 * s, -96 * cc - 2 * s, 5 * s, -96 * cc + 2 * s, k.fill(0xfff1d2))
    // Flame: a teardrop that breathes and leans with whole-loop sines.
    val tau = 2 * PI
    val flick = (1 + 0.08 * sin(tau * 27 * time / LOOP + id) + 0.05 * sin(tau * 41 * time / LOOP + 1 + id)).toFloat()
    val sway = (1.2 * sin(tau * 7 * time / LOOP + id * 2)).toFloat()
    val fy = -96 * cc - 3 * s
    val fh = 15 * s * flick
    val flame = sc.path
    flame.reset()
    flame.moveTo(0f, fy)
    flame.cubicTo(-4.5f * s, fy - fh * 0.3f, -1.5f * s + sway, fy - fh * 0.8f, sway, fy - fh)
    flame.cubicTo(1.5f * s + sway, fy - fh * 0.8f, 4.5f * s, fy - fh * 0.3f, 0f, fy)
    flame.close()
    canvas.drawPath(flame, k.linear(0f, fy, 0f, fy - fh, FLAME, GLOW_STOPS))
    canvas.drawOval(-1.3f * s, fy - 4 * s, 1.3f * s, fy, k.fill(0x3a6ad8, 0.5f, 1f))
    val gr = 70 * s * flick
    canvas.drawCircle(0f, fy - fh * 0.5f, gr, additive(k.radial(0f, fy - fh * 0.5f, gr, GLOW, GLOW_STOPS)))
    canvas.restore()
}

/** Candlelight pooled on the cloth, under the dishes. */
internal fun clothDrawCandlePools(canvas: Canvas, k: ClothKit, sc: ClothScratch, g: ClothGeo, time: Double) {
    val tau = 2 * PI
    for (i in 0 until 2) {
        val flick = (1 + 0.06 * sin(tau * 27 * time / LOOP + 40 + i)).toFloat()
        canvas.save()
        g.planeMatrix(if (i == 0) -55.0 else 55.0, if (i == 0) 168.0 else 162.0, 0.0, sc.matrix, sc)
        canvas.concat(sc.matrix)
        canvas.drawCircle(0f, 0f, 150 * flick, additive(k.radial(0f, 0f, 150 * flick, POOL, POOL_STOPS)))
        canvas.restore()
    }
}

private val jig = DoubleArray(5)
private val place = DoubleArray(7)

/** Every dish, back to front. Main thread only (shares its scratch). */
internal fun clothDrawDishes(canvas: Canvas, k: ClothKit, sc: ClothScratch, g: ClothGeo, sp: ClothSprites, bp: Paint, p: ClothPose) {
    for (it in ITEMS) {
        placed(it, p, jig, place)
        when (it.kind) {
            0 -> drawPlate(canvas, sc, g, sp, bp, place)
            1 -> drawGlass(canvas, k, sc, g, sp, bp, place[0], place[1], clamp(place[3], -24.0, 24.0).toFloat(), place[4], place[5], place[6])
            2, 3 -> drawCutlery(canvas, sc, g, sp, bp, place, it.kind == 3)
            else -> drawCandle(canvas, k, sc, g, sp, bp, place[0], place[1], place[3].toFloat(), p.time, it.id)
        }
    }
}

/** Pixels per plane unit for the sprites: the view's zoom on the screen's density, with a margin. */
internal fun clothSpriteScale(density: Float) = ceil(max(1f, density) * 1.6f)
