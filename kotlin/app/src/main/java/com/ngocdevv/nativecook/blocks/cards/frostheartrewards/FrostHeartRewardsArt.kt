package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.Scene
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.clamp
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.ease
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.inOutCubic
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.mix
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.out
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.seg
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsMotion.smooth
import com.ngocdevv.nativecook.blocks.cards.frostheartrewards.FrostHeartRewardsTheme as T
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the block draws, in design points, layered as FhrStage + FhrAtelierStage + the GPU overlay. */
internal class FrostHeartRewardsArt(context: Context) {
    val type = FrostHeartRewardsType(context)
    private val page = FrostHeartRewardsPage(type)
    private val gem = FrostHeartRewardsGem(context)
    private val assets = context.assets
    private fun image(name: String): Bitmap = assets.open("frostheartrewards/frostheartrewards_$name.png").use {
        requireNotNull(BitmapFactory.decodeStream(it)) { "Frost Heart asset $name" }
    }
    private val bear = image("fhr-bear")
    private val hug = image("fhr-bear-hug")
    private val heart = image("fhr-heart")
    private val paw = image("fhr-paw")
    private val frontPaw = image("fhr-front-paw")
    private val runtime: FrostHeartRewardsRuntime? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { FrostHeartRewardsRuntime(gem, bear, hug, heart) }.getOrNull()
    } else null
    val traced get() = runtime != null

    @Volatile private var rays: Bitmap? = null
    @Volatile private var pageBitmap: Bitmap? = null
    private var pageScale = 0.0

    /** The CPU rasterized rays and the static page; call off the main thread. */
    fun prepare(scale: Double) {
        if (rays == null) {
            val r = FrostHeartRewardsPage.rays(RAYS_SCALE)
            rays = r
        }
        if (pageScale != scale || pageBitmap == null) {
            pageBitmap = page.bake(scale)
            pageScale = scale
        }
    }
    val prepared get() = rays != null && pageBitmap != null

    // Paints and shapes.
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val rect = RectF()
    private val camera = Camera()
    private val matrix = Matrix()
    private val path = Path()
    private val wink = PathParser.createPathFromPathData("M193 183 Q200 189 207 182")
    private val badge = PathParser.createPathFromPathData(T.BADGE)
    private val badgeBaseline = PathParser.createPathFromPathData(T.BADGE_BASELINE)
    private val badgeHighlight = PathParser.createPathFromPathData(T.BADGE_HIGHLIGHT)
    private val pawClip = Path().apply { addCircle(286.5f, 208.5f, 21.5f, Path.Direction.CW) }
    private val filters = HashMap<FloatArray, ColorMatrixColorFilter>()
    private val levels = HashMap<FloatArray, FloatArray>()
    private val pearl = RadialGradient(0f, 0f, 1f, IntArray(6) { k -> argb(exp(-(k / 5.0).pow(2) * 4.8), 1.0, 0.91, 0.95) },
        FloatArray(6) { it / 5f }, Shader.TileMode.CLAMP)
    private val soft = RadialGradient(0f, 0f, 1f, IntArray(6) { k -> argb(exp(-(k / 5.0).pow(2) * 3.2), 1.0, 1.0, 1.0) },
        FloatArray(6) { it / 5f }, Shader.TileMode.CLAMP)
    private val lumiPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        typeface = type.face(T.BOLD); textSize = 47f
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        typeface = type.face(T.SEMIBOLD); textSize = 17.7f
    }
    private val uniforms = FrostHeartRewardsRuntime.Uniforms()

    private fun filter(m: FloatArray) = filters.getOrPut(m) { ColorMatrixColorFilter(T.android(m)) }
    private fun level(m: FloatArray) = levels.getOrPut(m) { T.compose(T.AMETHYST, m) }
    private fun a255(a: Double) = (255 * a.coerceIn(0.0, 1.0)).roundToInt()

    fun draw(c: Canvas, width: Float, offsetY: Float, d: FrostHeartRewardsDriver, reduced: Boolean) {
        val s = d.scene
        val scale = width / 402f
        rays?.let { r -> runtime?.setPage(r, RAYS_SCALE) }
        c.drawColor(T.BACKGROUND)
        c.save()
        c.translate(0f, offsetY)
        c.scale(scale, scale)
        rays?.let { rect.set(0f, 0f, 402f, 540f); c.drawBitmap(it, null, rect, bitmapPaint) }
        val keep = 1 - s.reset
        val tiltX = d.tiltX
        val tiltY = d.tiltY
        val hx = tiltX * keep
        val hy = tiltY * keep
        val open = clamp(s.display) * keep
        val hue = s.hue * keep
        val jelly = if (reduced) 0.0 else d.jelly * keep
        c.save()
        c.translate(hx.toFloat(), hy.toFloat())
        heroBack(c, s, open, hue, jelly, keep, reduced)
        c.restore()
        crystal(c, s, d, scale, keep, tiltX, tiltY, reduced)
        c.save()
        c.translate(hx.toFloat(), hy.toFloat())
        heroFront(c, s, open, hue, keep, reduced)
        c.restore()
        sprite(c, frontPaw, T.FRONT_PAW, T.PAW_MATRIX, hue, reduced)
        slab(c, s, keep)
        val light = s.spotlight * keep
        if (light > 0) spotlight(c, light)
        tag(c, s, keep, light, hue, reduced)
        pageBitmap?.let { rect.set(0f, 0f, 402f, 874f); c.drawBitmap(it, null, rect, bitmapPaint) }
        // The counters: the live scene and, during the return, the resting one fading in over it.
        c.save()
        c.clipRect(0f, 380f, 402f, 568f)
        val ages = DoubleArray(7) { d.chipAge(it, s, true) }
        page.drawState(c, s, ages, reduced, 1 - s.reset)
        if (s.reset > 0) {
            val rest = FrostHeartRewardsMotion.sceneAt(0.0)
            page.drawState(c, rest, DoubleArray(7) { d.chipAge(it, rest, false) }, reduced, s.reset)
        }
        c.restore()
        c.restore()
    }

    // ----- Sprites: colour matrices composed, never nested; masks applied inside one layer. -----

    private fun spriteBounds(sp: T.Sprite): RectF {
        val a = sp.rotation * PI / 180
        val cs = abs(cos(a)); val sn = abs(sin(a))
        val w = sp.w * cs + sp.h * sn + 24
        val h = sp.w * sn + sp.h * cs + 24
        return RectF((sp.x + sp.w / 2 - w / 2).toFloat(), (sp.y + sp.h / 2 - h / 2).toFloat(), (sp.x + sp.w / 2 + w / 2).toFloat(), (sp.y + sp.h / 2 + h / 2).toFloat())
    }
    private fun image(c: Canvas, bitmap: Bitmap, sp: T.Sprite, m: FloatArray, alpha: Double = 1.0) {
        bitmapPaint.colorFilter = filter(m)
        bitmapPaint.alpha = a255(alpha)
        c.save()
        c.rotate(sp.rotation.toFloat(), (sp.x + sp.w / 2).toFloat(), (sp.y + sp.h / 2).toFloat())
        rect.set(sp.x.toFloat(), sp.y.toFloat(), (sp.x + sp.w).toFloat(), (sp.y + sp.h).toFloat())
        c.drawBitmap(bitmap, null, rect, bitmapPaint)
        c.restore()
        bitmapPaint.colorFilter = null
        bitmapPaint.alpha = 255
    }

    /** The level colour arrives as a soft front growing out of the gem (WAVE_MASK_SKSL). */
    private fun sprite(c: Canvas, bitmap: Bitmap, sp: T.Sprite, m: FloatArray, hue: Double, reduced: Boolean) {
        val lvl = level(m)
        when {
            reduced -> { image(c, bitmap, sp, m); if (hue > 0) image(c, bitmap, sp, lvl, hue) }
            hue >= 1 -> image(c, bitmap, sp, lvl)
            hue <= 0 -> image(c, bitmap, sp, m)
            else -> {
                image(c, bitmap, sp, m)
                val radius = mix(-24.0, 230.0, hue)
                val outer = radius + 18
                if (outer <= 0) return
                val b = spriteBounds(sp)
                c.saveLayer(b, null)
                image(c, bitmap, sp, lvl)
                val inner = max(0.0, radius - 18)
                val stops = FloatArray(6) { k -> (mix(inner, outer, k / 5.0) / outer).toFloat() }
                val colors = IntArray(6) { k -> argb(1 - smooth(radius - 18, radius + 18, mix(inner, outer, k / 5.0)), 1.0, 1.0, 1.0) }
                maskPaint.shader = RadialGradient(175f, 278f, outer.toFloat(), colors, stops, Shader.TileMode.CLAMP)
                c.drawRect(b, maskPaint)
                maskPaint.shader = null
                c.restore()
            }
        }
    }

    /** SPRITE_MASK_SKSL: a world-space floor below which the sprite fades out. */
    private fun floor(c: Canvas, b: RectF, floorY: Double, fade: Double) {
        val colors = IntArray(6) { k -> argb(1 - smooth(0.0, 1.0, k / 5.0), 1.0, 1.0, 1.0) }
        maskPaint.shader = LinearGradient(0f, floorY.toFloat(), 0f, (floorY + fade).toFloat(), colors, FloatArray(6) { it / 5f }, Shader.TileMode.CLAMP)
        c.drawRect(b, maskPaint)
        maskPaint.shader = null
    }

    private fun heroBack(c: Canvas, s: Scene, open: Double, hue: Double, jelly: Double, keep: Double, reduced: Boolean) {
        if (open > 0) ellipse(c, 177.0, 282.0, 119.0 + 22, 89.0 + 22, argb(0.15 * open, 235 / 255.0, 201 / 255.0, 221 / 255.0), 0.69)
        c.save()
        c.scale((1 - 0.03 * jelly).toFloat(), (1 + 0.015 * jelly).toFloat(), 175f, 245f)
        val b = spriteBounds(T.BEAR)
        if (open < 1) {
            c.saveLayerAlpha(b, a255(1 - open))
            sprite(c, bear, T.BEAR, T.BEAR_MATRIX, hue, reduced)
            floor(c, b, 292.0, 40.0)
            c.restore()
        }
        if (open > 0) {
            c.saveLayerAlpha(b, a255(open))
            sprite(c, hug, T.BEAR, T.BEAR_MATRIX, hue, reduced)
            floor(c, b, 310.0, 40.0)
            c.restore()
        }
        val winkAlpha = abs(jelly) * keep
        if (winkAlpha > 0.01) {
            line.shader = null; line.color = 0xFF6E3B73.toInt(); line.alpha = a255(winkAlpha); line.strokeWidth = 2.6f
            c.drawPath(wink, line)
            line.alpha = 255
        }
        c.restore()
        // The original frost raster is the heart's skin until the last facet has cleared.
        val frostHalo = 1 - ease(seg(s.t, 3.8, 3.9))
        val heartAlpha = max(frostHalo, s.reset)
        if (heartAlpha > 0) {
            val hb = RectF(35f, 160f, 330f, 405f)
            c.saveLayerAlpha(hb, a255(heartAlpha))
            image(c, heart, T.HEART, T.IDENTITY)
            floor(c, hb, 306.0, 62.0)
            c.restore()
        }
        val progress = clamp((s.balance - 1650) / 75)
        val progressAlpha = 0.2 * progress * sin(min(1.0, progress * 3) * PI / 2) * (1 - s.cut) * keep
        if (progressAlpha > 0.001) {
            line.shader = null; line.color = 0xFFE3E1F7.toInt(); line.alpha = a255(progressAlpha); line.strokeWidth = 0.75f
            path.reset(); path.addArc(RectF(49f, 175f, 301f, 381f), -90f, (360 * progress).toFloat())
            c.drawPath(path, line)
            line.alpha = 255
        }
    }

    private fun heroFront(c: Canvas, s: Scene, open: Double, hue: Double, keep: Double, reduced: Boolean) {
        if (open < 1) {
            c.save()
            c.rotate((open * 0.13 * 180 / PI).toFloat(), 257f, 217f)
            c.clipPath(pawClip)
            val b = spriteBounds(T.RAISED_PAW)
            c.saveLayerAlpha(b, a255(1 - open))
            sprite(c, paw, T.RAISED_PAW, T.IDENTITY, hue, reduced)
            c.restore()
            c.restore()
        }
        val mist = 0.62 * (1 - s.cut * keep)
        if (mist > 0.001) ellipse(c, 182.0, 348.5, 95.0 + 16, 31.5 + 16, argb(mist, 0xB8 / 255.0, 0xBE / 255.0, 0xD4 / 255.0), 0.6)
        val gem = s.gem * keep
        if (gem > 0 && !reduced) runtime?.drawCaustic(c, s.t, gem, open, s.polish * keep)
    }

    /** A soft oval: a radial gradient stretched to the ellipse, standing in for a blurred Oval. */
    private fun ellipse(c: Canvas, cx: Double, cy: Double, rx: Double, ry: Double, color: Int, core: Double) {
        val a = (color ushr 24) and 0xff
        fill.shader = RadialGradient(0f, 0f, 1f, intArrayOf(color, color and 0xffffff or ((a * 0.85).roundToInt() shl 24), color and 0xffffff), floatArrayOf(0f, core.toFloat(), 1f), Shader.TileMode.CLAMP)
        fill.color = 0xFFFFFFFF.toInt()
        c.save()
        c.translate(cx.toFloat(), cy.toFloat())
        c.scale(rx.toFloat(), ry.toFloat())
        c.drawCircle(0f, 0f, 1f, fill)
        c.restore()
        fill.shader = null
    }

    // ----- The crystal: AGSL ray trace (API 33+) or drawn facets, then pearls, caustic, rays, flares. -----

    private fun crystal(c: Canvas, s: Scene, d: FrostHeartRewardsDriver, scale: Float, keep: Double, tiltX: Double, tiltY: Double, reduced: Boolean) {
        val pitch = tiltY / 8 * 0.25
        val yaw = clamp(d.ring + tiltX / 8 * 0.18, -0.61, 0.61)
        val pose = FrostHeartRewardsGem.Pose(pitch, yaw)
        val visible = keep > 0 && (s.t >= 2.1 || s.breath > 0.0001 || (reduced && s.gem > 0))
        if (visible) {
            val rt = runtime
            if (rt != null) {
                uniforms.apply {
                    t = s.t; gem = s.gem; display = s.display; spotlight = s.spotlight
                    reset = s.reset; polish = s.polish; hue = s.hue; this.reduced = reduced
                    this.pitch = pitch; this.yaw = yaw; this.tiltX = tiltX; this.tiltY = tiltY
                    polishX = s.polishX; breath = s.breath; breathX = s.breathX; breathY = s.breathY
                    core = s.core
                    completion = if (reduced) 0.0 else (1 - (1 - seg(s.t, 3.8, 3.92)).pow(3)) * (1 - seg(s.t, 3.92, 4.22)).pow(3) * 0.30
                }
                rt.draw(c, scale, uniforms)
            } else facets(c, s, pose, keep, tiltX, tiltY, reduced)
        }
        if (reduced) return
        pearls(c, s, d, keep)
        val gemLight = s.gem * keep
        if (gemLight <= 0) return
        causticSpots(c, pose, gemLight * s.spotlight)
        coreRays(c, s, keep)
        flares(c, s, pose, keep, tiltX, tiltY)
    }

    /** Below API 33: the Blender facets drawn back to front, each clearing when the laser reaches it. */
    private fun facets(c: Canvas, s: Scene, pose: FrostHeartRewardsGem.Pose, keep: Double, tiltX: Double, tiltY: Double, reduced: Boolean) {
        val assembled = FrostHeartRewardsMotion.assembled(s.t, s.display)
        val key = FrostHeartRewardsV3(-0.18, 0.78, 0.60).normalized()
        val rim = FrostHeartRewardsV3(-0.62, -0.55, 0.56).normalized()
        class Drawn(val points: FloatArray, val depth: Double, val light: Double, val arrival: Double)
        val faces = ArrayList<Drawn>(96)
        for (k in 0 until 24) {
            val g = k / 2
            val sector = gem.sectors[k]
            val spread = FrostHeartRewardsMotion.spread(s.t, s.display, g)
            val spin = if (assembled) 0.0 else pose.spin(g, spread)
            val pivot = gem.pivots[g]
            val off = if (assembled) FrostHeartRewardsV3.ZERO else pose.offset(pivot, spread)
            for (f in sector.faces) {
                val nw = pose.worldNormal(f.normal, spin)
                if (nw.z <= 0.02) continue
                val pts = FloatArray(f.indices.size * 2)
                var depth = 0.0
                f.indices.forEachIndexed { i, idx ->
                    val w = pose.worldPoint(sector.vertices[idx], pivot, spin, off)
                    pts[i * 2] = (175 + w.x * 105 + tiltX * keep).toFloat()
                    pts[i * 2 + 1] = (278 - w.y * 105 + tiltY * keep).toFloat()
                    depth += w.z
                }
                val lit = 0.25 + 0.55 * max(0.0, nw dot key).pow(3) + 0.35 * max(0.0, nw dot rim).pow(6) + 0.2 * nw.z
                faces.add(Drawn(pts, depth / f.indices.size, lit, f.arrival))
            }
        }
        faces.sortBy { it.depth }
        val tint = s.hue * keep
        for (f in faces) {
            val age = seg(s.t, f.arrival, f.arrival + 0.18)
            val cleared = if (s.t >= 3.8 || (reduced && s.gem > 0)) 1.0 else 1 - (1 - age).pow(3)
            val mark = smooth(2.1, 2.4, s.t) * (1 - cleared)
            val laserDistance = (s.t - f.arrival) / 0.032
            val laser = if (s.t >= 2.4) exp(-laserDistance * laserDistance) else 0.0
            path.reset()
            path.moveTo(f.points[0], f.points[1])
            for (i in 1 until f.points.size / 2) path.lineTo(f.points[i * 2], f.points[i * 2 + 1])
            path.close()
            if (cleared > 0) {
                val l = f.light.coerceIn(0.0, 1.2)
                val r = mix(0.36 + 0.6 * l, 0.40 + 0.5 * l, tint)
                val g = mix(0.34 + 0.6 * l, 0.22 + 0.5 * l, tint)
                val b = mix(0.44 + 0.56 * l, 0.62 + 0.4 * l, tint)
                fill.shader = null
                fill.color = argb(0.9 * cleared * keep, r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0))
                c.drawPath(path, fill)
            }
            val edge = (0.18 * cleared + 0.35 * mark + 0.8 * laser) * keep
            if (edge > 0.01) {
                line.shader = null; line.strokeWidth = 0.6f
                line.color = argb(edge.coerceIn(0.0, 1.0), 0.94, 0.95, 1.0)
                c.drawPath(path, line)
            }
        }
    }

    /** Claim pearls rise from a chip to the heart along a quadratic arc, with an eight-step tail. */
    private fun pearls(c: Canvas, s: Scene, d: FrostHeartRewardsDriver, keep: Double) {
        fill.shader = pearl
        fill.color = 0xFFFFFFFF.toInt()
        for (i in 0 until 5) {
            val age = d.pearlAge(i, s)
            if (age <= 0 || age >= 0.65) continue
            val u = inOutCubic(seg(age, 0.0, 0.65))
            val sx = 147.5 + i * 54; val sy = 511.0
            val cx = sx - 25; val cy = 350.0
            for (tail in 0 until 8) {
                val q = max(0.0, u - tail * 0.006)
                val x = (1 - q) * (1 - q) * sx + 2 * (1 - q) * q * cx + q * q * 175
                val y = (1 - q) * (1 - q) * sy + 2 * (1 - q) * q * cy + q * q * 278
                val extent = (3.8 - tail * 0.35) * 1.6
                val alpha = (1 - tail / 8.0).pow(1.6) * smooth(0.0, 0.04, age) * (1 - smooth(0.60, 0.65, age)) * keep
                fill.alpha = a255(alpha)
                c.save(); c.translate(x.toFloat(), y.toFloat()); c.scale(extent.toFloat(), extent.toFloat())
                c.drawCircle(0f, 0f, 1f, fill)
                c.restore()
            }
        }
        fill.alpha = 255
        fill.shader = null
    }

    /** Twelve dispersed focus spots on the table under the gem, red and blue split per crown facet. */
    private fun causticSpots(c: Canvas, pose: FrostHeartRewardsGem.Pose, alpha: Double) {
        if (alpha <= 0.001) return
        val ray = FrostHeartRewardsV3(0.12, -0.8, -0.6).normalized()
        for (j in 0 until 12) {
            val n = pose.forward(gem.sectors[j * 2].star).normalized()
            val red = ray.refract(n, 1 / 2.405) ?: continue
            val blue = ray.refract(n, 1 / 2.446) ?: red
            val x = 184 + red.x * 70; val y = 353 + red.z * 17
            val k = 0.05 * alpha
            glow(c, x, y, 22.0, 4.6, 0.86 * k, 0.6 * k, 0.45 * k)
            glow(c, x + (blue.x - red.x) * 130, y, 22.0, 4.6, 0.45 * k, 0.6 * k, 1.0 * k)
        }
    }

    /** A soft additive light: the shared white falloff tinted (and dimmed) by a lighting filter. */
    private fun glow(c: Canvas, x: Double, y: Double, sx: Double, sy: Double, r: Double, g: Double, b: Double) {
        add.shader = soft
        add.color = 0xFFFFFFFF.toInt()
        add.colorFilter = LightingColorFilter(argb(1.0, r, g, b) and 0xFFFFFF, 0)
        c.save(); c.translate(x.toFloat(), y.toFloat()); c.scale(sx.toFloat(), sy.toFloat())
        c.drawCircle(0f, 0f, 1f, add)
        c.restore()
        add.colorFilter = null
        add.shader = null
    }

    /** During the cut, light escapes through each finished cut: a short streak out of its portal. */
    private fun coreRays(c: Canvas, s: Scene, keep: Double) {
        val window = smooth(2.4, 2.5, s.t) * (1 - smooth(3.8, 3.94, s.t))
        if (window <= 0 || s.core <= 0) return
        val cx = 185.0; val cy = 289.0
        for (k in 0 until 12) {
            val (px, py) = gem.portals[k]
            val gate = out(seg(s.t, 2.48 + k * 0.105, 2.60 + k * 0.105))
            val strength = gate * s.core * window * 0.29 * keep * 2.2
            if (strength <= 0.002 || py > 360) continue
            val len = hypot(px - cx, py - cy)
            val dx = (px - cx) / len; val dy = (py - cy) / len
            val ox = cx + (px - cx) * 0.88; val oy = cy + (py - cy) * 0.88
            val x0 = ox - dx * 4; val y0 = oy - dy * 4
            val x1 = ox + dx * 27; val y1 = oy + dy * 27
            val a = strength.coerceIn(0.0, 1.0)
            line.strokeWidth = 1.1f
            line.color = 0xFFFFFFFF.toInt()
            line.shader = LinearGradient(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(),
                intArrayOf(0, argb(a, 1.0, 0.88, 0.93), argb(a * 0.37, 1.0, 0.88, 0.93), argb(a * 0.2, 1.0, 0.88, 0.93), 0),
                floatArrayOf(0f, 5f / 31, 16f / 31, 23f / 31, 1f), Shader.TileMode.CLAMP)
            line.xfermode = add.xfermode
            c.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), line)
            line.xfermode = null
            line.shader = null
        }
    }

    private val anchors = listOf(FrostHeartRewardsV3(0.60, 0.54, -0.12), FrostHeartRewardsV3(-0.58, 0.52, -0.12), FrostHeartRewardsV3(0.88, 0.12, -0.10))

    /** Three cross-shaped glints: the hold's crown pins, and slower twinkles once the gem is whole. */
    private fun flares(c: Canvas, s: Scene, pose: FrostHeartRewardsGem.Pose, keep: Double, tiltX: Double, tiltY: Double) {
        val hold = smooth(7.4, 7.65, s.t) * (1 - smooth(8.65, 8.9, s.t))
        val quiet = mix(0.22, 1.0, smooth(6.4, 6.65, s.t))
        val open = clamp(s.display)
        for (k in 0 until 3) {
            val age = s.t - 7.4
            val z: Double
            val anchor: FrostHeartRewardsV3
            if (hold > 0) {
                anchor = anchors[k] + FrostHeartRewardsV3(0.045 * sin(age * 2.2 + k * 1.7), 0.025 * sin(age * 1.9 + k * 2.0), 0.0)
                z = (0.55 + 0.40 * sin(age * 2.4 + k * 1.2).pow(2)) * hold
            } else {
                anchor = anchors[k]
                val tw = 0.5 + 0.5 * sin(s.t * 2.3 + k * 2.1)
                z = 0.85 * tw * tw * smooth(3.9, 4.3, s.t) * (1 - open)
            }
            val gate = z * s.gem * keep * quiet
            if (gate <= 0.01) continue
            val w = pose.forward(anchor)
            val x = 175 + w.x * 105 + tiltX * keep
            val y = 278 - w.y * 105 + tiltY * keep
            if (occluded(x, y, open)) continue
            flare(c, x, y, gate.coerceIn(0.0, 1.0))
        }
    }
    private fun occluded(x: Double, y: Double, open: Double): Boolean {
        val dx = x - 187.5; val dy = y - 291.0
        val cr = cos(-0.36); val sr = sin(-0.36)
        val vx = abs(dx * cr + dy * sr) - 33.5; val vy = abs(-dx * sr + dy * cr) - 3.0
        val sdf = hypot(max(vx, 0.0), max(vy, 0.0)) + min(max(vx, vy), 0.0) - 38
        return sdf < 2 || (open < 0.5 && hypot(x - 286.5, y - 208.5) < 23)
    }
    private fun flare(c: Canvas, x: Double, y: Double, a: Double) {
        glow(c, x, y, 9.0, 9.0, 0.5 * a, 0.5 * a, 0.55 * a)
        glow(c, x, y, 13.0, 0.7, a, a, a)
        glow(c, x, y, 0.7, 13.0, a, a, a)
    }

    // ----- The plaque, the jeweller's lamp and the level tag. -----

    private fun slab(c: Canvas, s: Scene, keep: Double) {
        val b = T.SLAB
        val cx = b.cx.toFloat(); val cy = b.cy.toFloat()
        c.save()
        c.rotate((T.SLAB_ROTATION * 180 / PI).toFloat(), cx, cy)
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = LinearGradient(cx, b.y.toFloat(), cx, (b.y + b.h + 6).toFloat(), intArrayOf(0xFFF9B9ED.toInt(), 0xFFDE2DC7.toInt(), 0xFF76196F.toInt()), null, Shader.TileMode.CLAMP)
        rect.set(b.x.toFloat(), (b.y + 5).toFloat(), (b.x + b.w).toFloat(), (b.y + b.h + 5).toFloat())
        c.drawRoundRect(rect, 39f, 39f, fill)
        // SLAB_SKSL's own fill: a vertical magenta ramp, then rim, shine, sweep and core added over it.
        val stops = FloatArray(5) { it / 4f }
        fill.shader = LinearGradient(cx, b.y.toFloat(), cx, (b.y + b.h).toFloat(), IntArray(5) { k ->
            val v = (k / 4.0).pow(0.9)
            argb(1.0, mix(1.0, 0.68, v), mix(0.085, 0.025, v), mix(0.84, 0.62, v))
        }, stops, Shader.TileMode.CLAMP)
        rect.set(b.x.toFloat(), b.y.toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat())
        c.drawRoundRect(rect, 38f, 38f, fill)
        fill.shader = null
        path.reset(); path.addRoundRect(rect, 38f, 38f, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        // Bright rim, strongest along the top edge.
        line.strokeWidth = 2.2f
        line.color = 0xFFFFFFFF.toInt()
        line.shader = LinearGradient(cx, b.y.toFloat(), cx, (b.y + b.h).toFloat(),
            intArrayOf(argb(0.6, 0.94, 0.78, 0.91), argb(0.45, 0.94, 0.78, 0.91), argb(0.1, 0.94, 0.78, 0.91)), floatArrayOf(0f, 0.3f, 0.62f), Shader.TileMode.CLAMP)
        rect.inset(1.7f, 1.7f)
        line.xfermode = add.xfermode
        c.drawRoundRect(rect, 36.3f, 36.3f, line)
        line.xfermode = null
        line.shader = null
        rect.inset(-1.7f, -1.7f)
        // A slanted shine across the upper third.
        add.shader = LinearGradient(0f, 248f, 0f, 276f, intArrayOf(0, argb(1.0, 0.13, 0.075, 0.115), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        add.color = 0xFFFFFFFF.toInt()
        c.save(); c.skew(0f, -0.12f); c.translate(0f, cx * 0.12f); c.drawRect(b.x.toFloat(), 240f, (b.x + b.w).toFloat(), 285f, add); c.restore()
        val sweep = s.spotlight * keep * 0.32 + s.polish * keep * 0.44
        if (sweep > 0.002) {
            val sx = (b.cx + sin(s.t * 0.6283185) * 68).toFloat()
            add.shader = LinearGradient(sx - 26, 0f, sx + 26, 0f, intArrayOf(0, argb(1.0, 0.21 * sweep, 0.15 * sweep, 0.19 * sweep), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(rect, add)
        }
        add.shader = null
        if (s.core > 0) glow(c, 187.5, 291.0, 78.0, 43.0, 0.15 * s.core * keep, 0.11 * s.core * keep, 0.13 * s.core * keep)
        c.restore()
        // "lumi", pressed into the plaque.
        val width = lumiPaint.measureText("lumi")
        val tx = (b.cx - width / 2).toFloat(); val ty = (b.y + b.h * 0.70).toFloat()
        lumiPaint.shader = null
        lumiPaint.color = 0xCC6F256D.toInt(); c.drawText("lumi", tx + 0.8f, ty + 2f, lumiPaint)
        lumiPaint.color = 0xFFB77AAD.toInt(); c.drawText("lumi", tx, ty + 0.8f, lumiPaint)
        lumiPaint.color = 0xFFFFFFFF.toInt()
        lumiPaint.shader = LinearGradient(cx, ty - 36, cx, ty + 1, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDF3FF.toInt(), 0xFFD6BDD8.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawText("lumi", tx - 0.4f, ty - 0.5f, lumiPaint)
        lumiPaint.shader = null
        c.restore()
    }

    /** SPOTLIGHT_SKSL: a 25 % edge vignette and a warm cone from above, inside the hero's band. */
    private fun spotlight(c: Canvas, light: Double) {
        val band = RectF(0f, 96f, 402f, 486f)
        c.saveLayer(band, null)
        val r = 245f * 1.08f
        val vignette = IntArray(6) { k -> argb(smooth(0.28, 1.08, 1.08 * k / 5.0) * 0.25 * light, 0.0, 0.0, 0.0) }
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = RadialGradient(0f, 0f, r, vignette, FloatArray(6) { it / 5f }, Shader.TileMode.CLAMP)
        c.save(); c.translate(175f, 270f); c.scale(205f / 245f, 1f); c.drawRect(-600f, -600f, 600f, 600f, fill); c.restore()
        val warm = intArrayOf(0, argb(0.11 * light * 0.5, 1.0, 0.955, 0.865), argb(0.11 * light * 0.5, 1.0, 0.955, 0.865), 0)
        val pos = floatArrayOf(0f, (142f - 78f) / (442f - 78f), (300f - 78f) / (442f - 78f), 1f)
        fill.shader = LinearGradient(0f, 78f, 0f, 442f, warm, pos, Shader.TileMode.CLAMP)
        for (k in listOf(0.52, 1.0)) {
            path.reset()
            val top = (28 + 2 * 0.47) * k; val bottom = (28 + 366 * 0.47) * k
            path.moveTo((174 - top).toFloat(), 78f); path.lineTo((174 + top).toFloat(), 78f)
            path.lineTo((174 + bottom).toFloat(), 442f); path.lineTo((174 - bottom).toFloat(), 442f); path.close()
            c.drawPath(path, fill)
        }
        fill.shader = null
        val edges = intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF)
        maskPaint.shader = LinearGradient(0f, 96f, 0f, 486f, edges, floatArrayOf(0f, 34f / 390, 299f / 390, 1f), Shader.TileMode.CLAMP)
        c.drawRect(band, maskPaint)
        maskPaint.shader = null
        c.restore()
    }

    /** TAG_SKSL as a gradient: purple satin turning to brushed platinum under the lamp, flipped on X. */
    private fun tag(c: Canvas, s: Scene, keep: Double, light: Double, hue: Double, reduced: Boolean) {
        val tagBlend = if (s.flip >= 0.5) 1.0 else 0.0
        val first = if (reduced) 1 - hue else (1 - tagBlend) * keep + s.reset
        val next = if (reduced) hue else tagBlend * keep
        val platinum = next
        val angle = s.flip * 180
        val folded = if (angle > 90) angle - 180 else angle
        val cx = (T.TAG_VIEW.cx).toFloat(); val cy = (T.TAG_VIEW.cy).toFloat()
        c.save()
        if (!reduced && folded != 0.0) {
            camera.save()
            camera.setLocation(0f, 0f, -700f / 72f)
            camera.rotateX((-folded * (1 - s.reset)).toFloat())
            camera.getMatrix(matrix)
            camera.restore()
            matrix.preTranslate(-cx, -cy)
            matrix.postTranslate(cx, cy)
            c.concat(matrix)
        }
        val n = 13
        val colors = IntArray(n) { k ->
            val v = k / (n - 1.0)
            val pr = if (v < 0.5) mix(0.761, 0.631, v / 0.5) else mix(0.631, 0.690, (v - 0.5) * 2)
            val pg = if (v < 0.5) mix(0.408, 0.286, v / 0.5) else mix(0.286, 0.294, (v - 0.5) * 2)
            val pb = if (v < 0.5) mix(0.953, 0.890, v / 0.5) else mix(0.890, 0.894, (v - 0.5) * 2)
            val band = 0.5 + 0.5 * cos(v * 12 - 0.7)
            val edge = abs(2 * v - 1).pow(6)
            val mr = mix(mix(0.25, 0.65, band), mix(0.77, 0.99, band) + 0.045 * edge, platinum)
            val mg = mix(mix(0.31, 0.72, band), mix(0.80, 0.98, band) + 0.022 * edge, platinum)
            val mb = mix(mix(0.41, 0.81, band), mix(0.84, 0.95, band) - 0.018 * edge, platinum)
            argb(1.0, mix(pr, mr, light).coerceIn(0.0, 1.0), mix(pg, mg, light).coerceIn(0.0, 1.0), mix(pb, mb, light).coerceIn(0.0, 1.0))
        }
        line.color = 0xFFFFFFFF.toInt()
        line.strokeWidth = 29f
        line.shader = LinearGradient(295f, 144f, 362f, 230f, colors, null, Shader.TileMode.CLAMP)
        c.drawPath(badge, line)
        // One key light sweeps the metal as it turns.
        val sweep = light * 0.22 * (0.35 + s.polish * keep * 0.5)
        if (sweep > 0.002) {
            val sx = mix(282.0, 371.0, clamp((s.t - 6.65) / 0.6)).toFloat()
            line.shader = LinearGradient(sx - 16, 0f, sx + 16, 0f, intArrayOf(0, argb(1.0, sweep, sweep * 1.09, sweep * 1.23), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            line.xfermode = add.xfermode
            c.drawPath(badge, line)
            line.xfermode = null
        }
        line.shader = null
        fun label(text: String, p: Path, color: Int, alpha: Double) {
            if (alpha <= 0.001) return
            levelPaint.color = color
            levelPaint.alpha = a255(alpha)
            c.drawTextOnPath(text, p, 0f, 0f, levelPaint)
        }
        label("Level 3", badgeBaseline, 0xFFFFFFFF.toInt(), first * (1 - light))
        label("Level 4", badgeBaseline, 0xFFFFFFFF.toInt(), next * (1 - light))
        label("Level 3", badgeHighlight, 0xFFB9C8DE.toInt(), first * light)
        label("Level 4", badgeHighlight, 0xFFFFFFFF.toInt(), next * light)
        label("Level 3", badgeBaseline, 0xFF263143.toInt(), first * light)
        label("Level 4", badgeBaseline, 0xFF53535B.toInt(), next * light)
        c.restore()
    }

    companion object {
        const val RAYS_SCALE = 1.5
        fun argb(a: Double, r: Double, g: Double, b: Double): Int =
            ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or ((r.coerceIn(0.0, 1.0) * 255).roundToInt() shl 16) or
                ((g.coerceIn(0.0, 1.0) * 255).roundToInt() shl 8) or (b.coerceIn(0.0, 1.0) * 255).roundToInt()
    }
}
