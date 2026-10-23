package com.ngocdevv.nativecook.blocks.playground.fractaltreebloom

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Atlas cells (64 dp each): two blossoms, a leaf, a petal and a snowflake.
private const val CELL = 64f
private const val BLOSSOM_A = 0
private const val BLOSSOM_B = 1
private const val LEAF_CELL = 2
private const val PETAL_CELL = 3
private const val SNOW_CELL = 4

// Colours by season (spring, summer, autumn, winter): sky top and bottom, far hills, ground, bark.
private val SkyTop = arrayOf(doubleArrayOf(0.96, 0.84, 0.82), doubleArrayOf(0.72, 0.86, 0.9), doubleArrayOf(0.93, 0.66, 0.47), doubleArrayOf(0.47, 0.55, 0.66))
private val SkyLow = arrayOf(doubleArrayOf(0.99, 0.94, 0.89), doubleArrayOf(0.97, 0.95, 0.85), doubleArrayOf(0.99, 0.88, 0.74), doubleArrayOf(0.8, 0.85, 0.9))
private val Hills = arrayOf(doubleArrayOf(0.82, 0.86, 0.74), doubleArrayOf(0.6, 0.74, 0.6), doubleArrayOf(0.82, 0.6, 0.42), doubleArrayOf(0.78, 0.82, 0.88))
private val GroundColors = arrayOf(doubleArrayOf(0.56, 0.7, 0.45), doubleArrayOf(0.38, 0.56, 0.32), doubleArrayOf(0.62, 0.45, 0.27), doubleArrayOf(0.93, 0.95, 0.97))
private val Bark = arrayOf(doubleArrayOf(0.3, 0.22, 0.2), doubleArrayOf(0.27, 0.21, 0.17), doubleArrayOf(0.28, 0.19, 0.15), doubleArrayOf(0.22, 0.2, 0.21))
// Leaves: summer greens, then autumn gold, orange and crimson.
private val GreenA = doubleArrayOf(0.36, 0.6, 0.3)
private val GreenB = doubleArrayOf(0.2, 0.46, 0.27)
private val Gold = doubleArrayOf(0.93, 0.68, 0.18)
private val Orange = doubleArrayOf(0.89, 0.42, 0.13)
private val Crimson = doubleArrayOf(0.72, 0.16, 0.14)

/** Linear blend of two colour triples into [out]. */
private fun mix3(a: DoubleArray, b: DoubleArray, t: Double, out: DoubleArray) {
    val r = a[0] + (b[0] - a[0]) * t
    val g = a[1] + (b[1] - a[1]) * t
    val bl = a[2] + (b[2] - a[2]) * t
    out[0] = r
    out[1] = g
    out[2] = bl
}

/** A colour from a four-season table at season s (0…4, wrapping), eased between seasons. */
private fun seasonal(table: Array<DoubleArray>, s: Double, out: DoubleArray) {
    val w = s - 4 * floor(s / 4)
    val i = floor(w).toInt() % 4
    val f = w - floor(w)
    val e = f * f * (3 - 2 * f)
    mix3(table[i], table[(i + 1) % 4], e, out)
}

private fun smooth(a: Double, b: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)
}

/** RN's `css`: each channel rounded to a byte. */
private fun argb(c: DoubleArray, a: Double = 1.0) =
    Color.argb((a.coerceIn(0.0, 1.0) * 255).roundToInt(), (c[0] * 255).roundToInt(), (c[1] * 255).roundToInt(), (c[2] * 255).roundToInt())

/** The autumn colour of a site or a falling leaf. */
private fun autumn(hue: Double, out: DoubleArray) {
    if (hue < 0.34) {
        mix3(Gold, Orange, hue / 0.34 * 0.5, out)
    } else if (hue < 0.7) {
        mix3(Orange, Gold, (hue - 0.34) * 0.6, out)
    } else {
        mix3(Crimson, Orange, (hue - 0.7) * 1.2, out)
    }
}

/** Draws one frame of the tree, reusing its meshes, the sprite sheet and its paints. */
internal class TreeDrawing(private val density: Float) {
    private val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private var atlas: Bitmap? = null
    private var atlasShader: BitmapShader? = null
    private val plain = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter((10 - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL) }
    private val seedShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter((2.5f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL) }
    private val path = Path()
    private val oval = RectF()
    private val c1 = DoubleArray(3)
    private val c2 = DoubleArray(3)
    private val tint = DoubleArray(3)
    private val autumnTint = DoubleArray(3)

    // The branch mesh: four corners per branch, two triangles each, coloured per side.
    private var branchVerts = FloatArray(0)
    private var branchColors = IntArray(0)
    private var branchIndices = ShortArray(0)
    // The sprites: four corners each, textured from the sheet and tinted.
    private val maxSprites = 900 * 2 + MAX_PARTICLES
    private val spriteVerts = FloatArray(maxSprites * 8)
    private val spriteTexs = FloatArray(maxSprites * 8)
    private val spriteColors = IntArray(maxSprites * 4)
    private val spriteIndices = ShortArray(maxSprites * 6).also {
        for (k in 0 until maxSprites) {
            val v = k * 4
            val n = k * 6
            it[n] = v.toShort()
            it[n + 1] = (v + 1).toShort()
            it[n + 2] = (v + 2).toShort()
            it[n + 3] = (v + 1).toShort()
            it[n + 4] = (v + 3).toShort()
            it[n + 5] = (v + 2).toShort()
        }
    }
    private var sprites = 0
    // Below Android 10 the sprites are drawn one by one, tinted by a cached filter.
    private val filters = HashMap<Int, ColorFilter>()
    private val spriteMatrix = Matrix()
    private val src = android.graphics.Rect()
    private val dst = RectF()

    /** The sprite sheet, drawn once at the screen's density (and again if it was ever dropped). */
    private fun sheet(): Bitmap {
        atlas?.let { if (!it.isRecycled) return it }
        val bitmap = Bitmap.createBitmap((CELL * 5 * density).roundToInt(), (CELL * density).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        drawSheet(canvas)
        atlas = bitmap
        atlasShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            // Texture coordinates are given in dp.
            setLocalMatrix(Matrix().apply { setScale(1 / density, 1 / density) })
        }
        return bitmap
    }

    private fun drawSheet(canvas: Canvas) {
        val c = CELL / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Blossoms: five rounded petals around a deep centre with stamens.
        fun blossom(cell: Int, outer: Int, inner: Int, heart: Int) {
            val ox = cell * CELL + c
            paint.color = Color.BLACK
            // Centred on the blossom: in each petal's own frame (moved out 13 and turned) that is (0, 13).
            paint.shader = RadialGradient(0f, 13f, 30f, intArrayOf(inner, outer), floatArrayOf(0.15f, 1f), Shader.TileMode.CLAMP)
            for (k in 0 until 5) {
                val a = k / 5.0 * PI * 2 - PI / 2
                val px = ox + cos(a).toFloat() * 13
                val py = c + sin(a).toFloat() * 13
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate((a * 180 / PI).toFloat() + 90)
                canvas.drawOval(-10f, -14f, 10f, 13f, paint)
                canvas.restore()
            }
            paint.shader = null
            paint.color = heart
            canvas.drawCircle(ox, c, 5.5f, paint)
            paint.color = 0xFFF6D36B.toInt()
            for (k in 0 until 7) {
                val a = k / 7.0 * PI * 2
                canvas.drawCircle(ox + cos(a).toFloat() * 7.5f, c + sin(a).toFloat() * 7.5f, 1.6f, paint)
            }
        }
        blossom(BLOSSOM_A, 0xFFFBE6EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFC2486C.toInt())
        blossom(BLOSSOM_B, 0xFFF3A9C0.toInt(), 0xFFFCD9E4.toInt(), 0xFF9C2F55.toInt())
        // The leaf: an almond with a midrib, in greys so it can be tinted.
        run {
            val ox = LEAF_CELL * CELL + c
            val leaf = Path()
            leaf.moveTo(ox, c - 28)
            leaf.cubicTo(ox + 17, c - 14, ox + 15, c + 14, ox, c + 28)
            leaf.cubicTo(ox - 15, c + 14, ox - 17, c - 14, ox, c - 28)
            leaf.close()
            paint.color = Color.BLACK
            paint.shader = LinearGradient(ox - 16, c, ox + 16, c, 0xFFFFFFFF.toInt(), 0xFFB8B8B8.toInt(), Shader.TileMode.CLAMP)
            canvas.drawPath(leaf, paint)
            paint.shader = null
            paint.color = Color.argb((0.55f * 255).roundToInt(), 80, 80, 80)
            paint.strokeWidth = 1.6f
            canvas.drawLine(ox, c - 24, ox, c + 27, paint)
        }
        // A single petal, white, to be tinted.
        run {
            val ox = PETAL_CELL * CELL + c
            paint.color = Color.BLACK
            paint.shader = RadialGradient(ox, c + 8, 22f, 0xFFFFFFFF.toInt(), 0xFFE9E9E9.toInt(), Shader.TileMode.CLAMP)
            canvas.drawOval(ox - 13, c - 18, ox + 13, c + 18, paint)
        }
        // A snowflake: a soft round flake.
        run {
            val ox = SNOW_CELL * CELL + c
            paint.color = Color.BLACK
            paint.shader = RadialGradient(ox, c, 20f, intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(ox, c, 20f, paint)
            paint.shader = null
        }
    }

    fun draw(canvas: Canvas, engine: TreeEngine, width: Float, height: Float) {
        // A gradient with a radius of 0 throws: nothing to draw in an empty box anyway.
        if (width <= 0f || height <= 0f) return
        val sea = engine.season
        drawBackdrop(canvas, engine, sea, width, height)
        drawBranches(canvas, engine, sea)
        sprites = 0
        drawFoliage(engine, sea)
        drawParticles(engine)
        flushSprites(canvas)
        drawSeed(canvas, engine)
    }

    /** Sky, sun, far hills, the mound and the tree's shade on it. */
    private fun drawBackdrop(canvas: Canvas, engine: TreeEngine, sea: Double, width: Float, height: Float) {
        val ground = engine.ground
        seasonal(SkyTop, sea, c1)
        seasonal(SkyLow, sea, c2)
        plain.color = Color.BLACK
        plain.shader = LinearGradient(0f, 0f, 0f, ground.base.toFloat(), argb(c1), argb(c2), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, plain)
        // A low, soft sun behind the crown.
        plain.shader = RadialGradient(width * 0.72f, height * 0.3f, width * 0.55f, argb(c2, 0.85), argb(c2, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, plain)
        plain.shader = null
        // Two far hills, paler with distance.
        seasonal(Hills, sea, c1)
        for (layer in 0 until 2) {
            val top = ground.base - (if (layer == 0) 120 else 70)
            path.rewind()
            path.moveTo(0f, height)
            for (k in 0..24) {
                val x = k / 24.0 * width
                val y = top - (if (layer == 0) 38 else 26) * sin(k / 24.0 * PI * (if (layer == 0) 1.3 else 2.1) + (if (layer == 0) 0.6 else 2.4))
                path.lineTo(x.toFloat(), y.toFloat())
            }
            path.lineTo(width, height)
            path.close()
            plain.color = argb(c1, if (layer == 0) 0.42 else 0.7)
            canvas.drawPath(path, plain)
        }
        // The mound, with a lit crest.
        seasonal(GroundColors, sea, c1)
        path.rewind()
        path.moveTo(0f, height)
        for (k in 0..40) {
            val x = k / 40.0 * width
            path.lineTo(x.toFloat(), ground.y(x).toFloat())
        }
        path.lineTo(width, height)
        path.close()
        c2[0] = c1[0] * 0.72
        c2[1] = c1[1] * 0.72
        c2[2] = c1[2] * 0.74
        plain.color = Color.BLACK
        plain.shader = LinearGradient(0f, (ground.base - ground.bump).toFloat(), 0f, height, argb(c1), argb(c2), Shader.TileMode.CLAMP)
        canvas.drawPath(path, plain)
        plain.shader = null
        // The crown's shade on the ground, as wide as the crown and as dark as its foliage.
        val g = min(1.0, engine.growth / (MAX_DEPTH + 1))
        if (g > 0.05) {
            shade.color = Color.argb((0.22 * g * 255).roundToInt(), 30, 24, 20)
            val r = (width * 0.36 * g).toFloat()
            oval.set(engine.rootX.toFloat() - r + 14, engine.rootY.toFloat() - 9, engine.rootX.toFloat() + r + 14, engine.rootY.toFloat() + 13)
            canvas.drawOval(oval, shade)
        }
    }

    /** The branches: one mesh of tapered quads, lit on one side, with snow along their tops in winter. */
    private fun drawBranches(canvas: Canvas, engine: TreeEngine, sea: Double) {
        val tree = engine.tree ?: return
        if (engine.growth <= 0.001) return
        if (branchVerts.size != tree.count * 8) {
            branchVerts = FloatArray(tree.count * 8)
            branchColors = IntArray(tree.count * 4)
            branchIndices = ShortArray(tree.count * 6)
            for (i in 0 until tree.count) {
                val v = i * 4
                val n = i * 6
                branchIndices[n] = v.toShort()
                branchIndices[n + 1] = (v + 1).toShort()
                branchIndices[n + 2] = (v + 2).toShort()
                branchIndices[n + 3] = (v + 1).toShort()
                branchIndices[n + 4] = (v + 3).toShort()
                branchIndices[n + 5] = (v + 2).toShort()
            }
        }
        seasonal(Bark, sea, c1)
        val w = sea - 4 * floor(sea / 4)
        val snow = smooth(2.75, 3.2, w) * (1 - smooth(3.6, 4.0, w))
        val pos = branchVerts
        val cols = branchColors
        for (i in 0 until tree.count) {
            val v = i * 4
            val bx = tree.bx[i]
            val by = tree.by[i]
            var dx = tree.tx[i] - bx
            var dy = tree.ty[i] - by
            val len = sqrt(dx * dx + dy * dy)
            if (len < 0.2) {
                for (k in 0 until 4) {
                    pos[(v + k) * 2] = bx.toFloat()
                    pos[(v + k) * 2 + 1] = by.toFloat()
                }
                continue
            }
            dx /= len
            dy /= len
            // Normal to the left of the branch.
            val nx = dy
            val ny = -dx
            val h0 = tree.w0[i] / 2
            val h1 = tree.w1[i] / 2
            // Reach back into the parent a little so joints close up.
            val back = if (tree.depth[i] == 0) 0.0 else h0 * 0.9
            pos[v * 2] = (bx - dx * back + nx * h0).toFloat()
            pos[v * 2 + 1] = (by - dy * back + ny * h0).toFloat()
            pos[v * 2 + 2] = (bx - dx * back - nx * h0).toFloat()
            pos[v * 2 + 3] = (by - dy * back - ny * h0).toFloat()
            pos[v * 2 + 4] = (tree.tx[i] + nx * h1).toFloat()
            pos[v * 2 + 5] = (tree.ty[i] + ny * h1).toFloat()
            pos[v * 2 + 6] = (tree.tx[i] - nx * h1).toFloat()
            pos[v * 2 + 7] = (tree.ty[i] - ny * h1).toFloat()
            // Light from the upper left: the side facing it is lighter. Snow lies on the side facing up.
            for (side in 0 until 2) {
                val sx = if (side == 0) nx else -nx
                val sy = if (side == 0) ny else -ny
                val lit = 0.78 + 0.42 * max(0.0, -0.62 * sx - 0.78 * sy)
                val up = max(0.0, -sy) * snow * (if (tree.depth[i] < MAX_DEPTH) 1.0 else 0.5)
                val col = Color.rgb(
                    (min(1.0, c1[0] * lit * (1 - up) + 0.97 * up) * 255).roundToInt(),
                    (min(1.0, c1[1] * lit * (1 - up) + 0.98 * up) * 255).roundToInt(),
                    (min(1.0, c1[2] * lit * (1 - up) + 1 * up) * 255).roundToInt(),
                )
                cols[v + side] = col
                cols[v + side + 2] = col
            }
        }
        if (modern) {
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES, tree.count * 8, pos, 0, null, 0, cols, 0,
                branchIndices, 0, tree.count * 6, meshPaint,
            )
        } else {
            // Below Android 10 vertex meshes are not drawn by the hardware renderer: a quad and a
            // gradient across it per branch.
            for (i in 0 until tree.count) {
                val v = i * 4
                path.rewind()
                path.moveTo(pos[v * 2], pos[v * 2 + 1])
                path.lineTo(pos[v * 2 + 4], pos[v * 2 + 5])
                path.lineTo(pos[v * 2 + 6], pos[v * 2 + 7])
                path.lineTo(pos[v * 2 + 2], pos[v * 2 + 3])
                path.close()
                plain.color = Color.BLACK
                plain.shader = LinearGradient(
                    (pos[v * 2] + pos[v * 2 + 4]) / 2, (pos[v * 2 + 1] + pos[v * 2 + 5]) / 2,
                    (pos[v * 2 + 2] + pos[v * 2 + 6]) / 2, (pos[v * 2 + 3] + pos[v * 2 + 7]) / 2,
                    cols[v], cols[v + 1], Shader.TileMode.CLAMP,
                )
                canvas.drawPath(path, plain)
            }
            plain.shader = null
        }
    }

    /** Queues a sprite from cell `cell` with the RN atlas's RSXform (scos, ssin, tx, ty) and tint. */
    private fun sprite(cell: Int, cs: Double, sn: Double, tx: Double, ty: Double, color: Int) {
        if (sprites >= maxSprites) return
        val k = sprites
        val v = k * 8
        val c = cs.toFloat()
        val s = sn.toFloat()
        val x = tx.toFloat()
        val y = ty.toFloat()
        // Corners (0,0), (64,0), (0,64), (64,64) of the cell.
        spriteVerts[v] = x
        spriteVerts[v + 1] = y
        spriteVerts[v + 2] = x + c * CELL
        spriteVerts[v + 3] = y + s * CELL
        spriteVerts[v + 4] = x - s * CELL
        spriteVerts[v + 5] = y + c * CELL
        spriteVerts[v + 6] = x + c * CELL - s * CELL
        spriteVerts[v + 7] = y + s * CELL + c * CELL
        val u0 = cell * CELL
        spriteTexs[v] = u0
        spriteTexs[v + 1] = 0f
        spriteTexs[v + 2] = u0 + CELL
        spriteTexs[v + 3] = 0f
        spriteTexs[v + 4] = u0
        spriteTexs[v + 5] = CELL
        spriteTexs[v + 6] = u0 + CELL
        spriteTexs[v + 7] = CELL
        val n = k * 4
        spriteColors[n] = color
        spriteColors[n + 1] = color
        spriteColors[n + 2] = color
        spriteColors[n + 3] = color
        sprites += 1
    }

    private fun tintColor(r: Double, g: Double, b: Double, a: Double) = Color.argb(
        (a.coerceIn(0.0, 1.0) * 255).roundToInt(),
        (r.coerceIn(0.0, 1.0) * 255).roundToInt(),
        (g.coerceIn(0.0, 1.0) * 255).roundToInt(),
        (b.coerceIn(0.0, 1.0) * 255).roundToInt(),
    )

    /** Draws every queued sprite: one textured, tinted mesh (the RN atlas), or one by one below Android 10. */
    private fun flushSprites(canvas: Canvas) {
        if (sprites == 0) return
        val bitmap = sheet()
        if (modern) {
            spritePaint.shader = atlasShader
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES, sprites * 8, spriteVerts, 0, spriteTexs, 0, spriteColors, 0,
                spriteIndices, 0, sprites * 6, spritePaint,
            )
            spritePaint.shader = null
            return
        }
        for (k in 0 until sprites) {
            val v = k * 8
            val color = spriteColors[k * 4]
            // Quantised tints share a filter: multiply by the tint, then the alpha.
            val key = color and 0xF8F8F8
            val filter = filters.getOrPut(key) { PorterDuffColorFilter(key or 0xFF000000.toInt(), PorterDuff.Mode.MULTIPLY) }
            spritePaint.colorFilter = filter
            spritePaint.alpha = Color.alpha(color)
            val c = (spriteVerts[v + 2] - spriteVerts[v]) / CELL
            val s = (spriteVerts[v + 3] - spriteVerts[v + 1]) / CELL
            spriteMatrix.setValues(floatArrayOf(c, -s, spriteVerts[v], s, c, spriteVerts[v + 1], 0f, 0f, 1f))
            val u0 = (spriteTexs[v] * density).roundToInt()
            src.set(u0, 0, u0 + (CELL * density).roundToInt(), (CELL * density).roundToInt())
            dst.set(0f, 0f, CELL, CELL)
            canvas.save()
            canvas.concat(spriteMatrix)
            canvas.drawBitmap(bitmap, src, dst, spritePaint)
            canvas.restore()
        }
        spritePaint.colorFilter = null
        spritePaint.alpha = 255
    }

    /** Blossoms and leaves at their sites, each reading the season a little late. */
    private fun drawFoliage(engine: TreeEngine, sea: Double) {
        val tree = engine.tree ?: return
        if (engine.growth < MAX_DEPTH - 1) return
        val bl = engine.bloom
        // Drawing back into the seed, the foliage goes first.
        val keep = min(1.0, (engine.growth - (MAX_DEPTH - 1)) / 1.5)
        for (i in 0 until tree.sites) {
            val b = tree.siteBranch[i]
            if (tree.shown[b] < 0.6) continue
            val lag = tree.siteLag[i]
            val local = localSeason(sea, lag)
            val x = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i]
            val y = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i]
            val sway = tree.abs[b]
            // Blossoms open after the tree is grown, each a little after the last, and give way to leaves.
            val open = treeEaseOutBack((bl - lag * 1.6) / 0.45) * keep
            val blossom = open * (1 - smooth(0.45, 0.95, local) + smooth(3.4, 3.95, local))
            if (blossom > 0.02) {
                val k = tree.siteSize[i] * 0.33 * blossom
                val a = tree.siteSpin[i] + sway * 0.5
                val cs = cos(a) * k
                val sn = sin(a) * k
                sprite(
                    if (tree.siteKind[i] == 0) BLOSSOM_A else BLOSSOM_B, cs, sn, x - (cs * 32 - sn * 32), y - (sn * 32 + cs * 32),
                    tintColor(1.0, 1.0, 1.0, min(1.0, blossom * 1.5)),
                )
            }
            // Leaves: in with summer, coloured in autumn, gone at their moment.
            val fall = tree.siteFall[i]
            val wither = if (engine.reduced || !tree.leafFalls(i)) 0.22 else 0.03
            val on = smooth(0.4, 0.95, local) * (1 - smooth(fall - wither, fall, local)) * open
            if (on > 0.02 && local < 3.5) {
                val hue = tree.siteHue[i]
                val green = if (hue < 0.5) 0.0 else 1.0
                mix3(GreenA, GreenB, green * 0.8 + hue * 0.2, tint)
                autumn(hue, autumnTint)
                mix3(tint, autumnTint, smooth(1.4, 1.95, local), tint)
                val k = tree.siteSize[i] * 0.28 * on
                val side = if (tree.siteSide[i] < 0) -1.0 else 1.0
                val a = sway + side * 0.95 + tree.siteSpin[i] * 0.15
                val cs = cos(a) * k
                val sn = sin(a) * k
                // The leaf hangs from its stalk end (the sprite's bottom centre) at the site.
                sprite(LEAF_CELL, cs, sn, x - (cs * 32 - sn * 60), y - (sn * 32 + cs * 60), tintColor(tint[0], tint[1], tint[2], min(1.0, on * 1.4)))
            }
        }
    }

    /** Falling petals, leaves and snow. */
    private fun drawParticles(engine: TreeEngine) {
        val ps = engine.particles
        if (ps.count == 0) return
        // Drawing back into the seed, whatever is still falling fades away with the tree.
        val away = if (engine.growing < 0) min(1.0, engine.growth / (MAX_DEPTH + 1)) else 1.0
        // Each kind belongs to its season: petals fade out with the summer, leaves by spring, snow by spring.
        val sea = engine.season - 4 * floor(engine.season / 4)
        val petals = 1 - smooth(1.5, 1.95, sea)
        val leaves = smooth(1.2, 1.6, sea) * (1 - smooth(3.5, 3.95, sea))
        val flakes = smooth(2.6, 2.9, sea) * (1 - smooth(3.7, 3.98, sea))
        for (i in 0 until ps.count) {
            val kind = ps.kind[i]
            val restFade = away * (if (kind == SNOW) flakes else if (kind == LEAF) leaves else petals) *
                (if (ps.rest[i] < 0) 1.0 else max(0.0, 1 - ps.rest[i] / (if (kind == SNOW) 1.2 else 2.0)))
            val k = ps.size[i] * (if (kind == SNOW) 0.17 else if (kind == LEAF) 0.26 else 0.22)
            // A tumbling flake turns in depth too: its width breathes with the turn.
            val tumble = if (kind == SNOW) 1.0 else 0.55 + 0.45 * abs(cos(ps.rot[i] * 1.7))
            val a = ps.rot[i]
            val cs = cos(a) * k
            val sn = sin(a) * k
            val tx = ps.x[i] - (cs * tumble * 32 - sn * 32)
            val ty = ps.y[i] - (sn * tumble * 32 + cs * 32)
            when (kind) {
                SNOW -> sprite(SNOW_CELL, cs * tumble, sn * tumble, tx, ty, tintColor(1.0, 1.0, 1.0, restFade))
                LEAF -> {
                    autumn(ps.hue[i], autumnTint)
                    sprite(LEAF_CELL, cs * tumble, sn * tumble, tx, ty, tintColor(autumnTint[0], autumnTint[1], autumnTint[2], restFade))
                }
                else -> sprite(PETAL_CELL, cs * tumble, sn * tumble, tx, ty, tintColor(0.98, 0.8 + 0.12 * ps.hue[i], 0.86, restFade))
            }
        }
    }

    /** The seed on the mound, until the sprout has taken over. */
    private fun drawSeed(canvas: Canvas, engine: TreeEngine) {
        val g = engine.growth
        if (g > 0.6) return
        val alpha = 1 - g / 0.6
        canvas.save()
        canvas.translate(engine.rootX.toFloat(), engine.rootY.toFloat() - 5)
        canvas.rotate(-18f)
        seedShadow.color = Color.argb((0.3 * alpha * 255).roundToInt(), 40, 28, 20)
        canvas.drawOval(-9f, 1f, 11f, 9f, seedShadow)
        plain.color = Color.BLACK
        plain.shader = RadialGradient(
            -3f, -3f, 12f,
            intArrayOf(0xFFB7835A.toInt(), 0xFF6B4329.toInt(), 0xFF3D2414.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        plain.alpha = (alpha * 255).roundToInt()
        canvas.drawOval(-10f, -6f, 10f, 6f, plain)
        plain.shader = null
        plain.color = Color.argb((0.55 * alpha * 255).roundToInt(), 255, 240, 220)
        canvas.drawOval(-6f, -4.5f, 1f, -1.5f, plain)
        canvas.restore()
    }
}
