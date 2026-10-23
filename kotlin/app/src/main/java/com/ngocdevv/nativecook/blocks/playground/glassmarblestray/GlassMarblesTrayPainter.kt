package com.ngocdevv.nativecook.blocks.playground.glassmarblestray

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val MARBLES_INK = 0xFFF4E9DC.toInt()
internal const val MARBLES_MUTED = 0xFFA8927E.toInt()
internal val MarblesTable = intArrayOf(0xFF3B2C22.toInt(), 0xFF1B130E.toInt(), 0xFF0D0907.toInt())

private fun argb(c: IntArray, a: Double) = argb(c[0], c[1], c[2], a)

private fun argb(r: Int, g: Int, b: Int, a: Double): Int {
    val alpha = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
    return (alpha shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}

private fun mix(a: IntArray, b: IntArray, t: Double) = intArrayOf(
    (a[0] + (b[0] - a[0]) * t).roundToInt(),
    (a[1] + (b[1] - a[1]) * t).roundToInt(),
    (a[2] + (b[2] - a[2]) * t).roundToInt(),
)

private val WHITE = intArrayOf(255, 255, 255)

/** The radial gradients of one marble, built once in unit space and placed by a local matrix. */
private class MarbleShaders(set: Marble) {
    val body = RadialGradient(
        0.18f, 0.22f, 1.08f,
        intArrayOf(argb(mix(set.glass, WHITE, 0.4), 0.5), argb(set.glass, 0.62), argb(mix(set.glass, intArrayOf(20, 20, 30), 0.55), 0.92)),
        floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
    )
    val haze = RadialGradient(-0.3f, -0.35f, 1.1f, argb(WHITE, 0.22), argb(set.glass, 0.06), Shader.TileMode.CLAMP)
    val glow = RadialGradient(0.38f, 0.44f, 0.7f, argb(mix(set.glass, WHITE, 0.6), 0.6), argb(set.glass, 0.0), Shader.TileMode.CLAMP)
    val fresnel = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(argb(set.glass, 0.0), argb(mix(set.glass, intArrayOf(10, 10, 20), 0.6), 0.55)),
        floatArrayOf(0.72f, 1f), Shader.TileMode.CLAMP,
    )
    // The caustic in the shadow, at full strength: its fade goes in the paint's alpha.
    val caustic = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(argb(mix(set.glass, WHITE, 0.5), 0.8), argb(set.glass, 0.35), argb(set.glass, 0.0)),
        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
    )
    val vane = argb(set.vane, 1.0) and 0xFFFFFF
    val vane2 = argb(set.vane2, 1.0) and 0xFFFFFF
    val vaneEdge = argb(mix(set.vane, WHITE, 0.5), 0.5)
    val vane2Edge = argb(mix(set.vane2, WHITE, 0.5), 0.5)
    val rim = argb(mix(set.glass, intArrayOf(0, 0, 0), 0.6), 0.45)
    val shadowTint = argb(mix(intArrayOf(46, 22, 6), set.glass, 0.35), 1.0) and 0xFFFFFF
}

/**
 * Draws the table, the tray and its marbles in dp, as the React Native picture does: the table, the
 * tray's shadow and sides on screen; then, under the floor's homography, the baked tray top and the
 * marbles' shadows; then the marbles, their glints and the tilt readout. Paints, paths and the
 * marbles' gradients are kept between frames.
 */
internal class GlassMarblesTrayPainter {
    private val shaders = MARBLES.map { MarbleShaders(it) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val blurred = HashMap<Int, Paint>()
    private val flashLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val flashDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
    }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARBLES_INK
        textSize = 22f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARBLES_MUTED
        textSize = 10.5f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val path = Path()
    private val circle = Path()
    private val local = Matrix()
    private val floor = Matrix()
    private val floorValues = FloatArray(9)
    private val light = DoubleArray(3)
    private val rect = RectF()
    private val order = IntArray(MARBLES.size)
    private val px = DoubleArray(4)
    private val py = DoubleArray(4)
    private var tableFor = -1.0
    private var table: Shader? = null

    // Blurred shapes that do not change from frame to frame, drawn as alpha sprites tinted by the
    // paint: the same pixels as a live mask-filter blur, without rasterising the blur every frame.
    private val sprites = HashMap<Long, Bitmap>()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** An oval (or rounded rect with [corner] > 0) of w × h dp blurred by [sigma] dp, at [res] px per dp. */
    private fun sprite(w: Double, h: Double, sigma: Double, corner: Double, res: Float): Bitmap {
        val key = ((w * 8).roundToInt().toLong() shl 40) or ((h * 8).roundToInt().toLong() shl 24) or
            ((sigma * 16).roundToInt().toLong() shl 12) or (corner * 4).roundToInt().toLong()
        return sprites.getOrPut(key) {
            val pad = sigma * 3 + 1
            val bw = ceil((w + pad * 2) * res).toInt()
            val bh = ceil((h + pad * 2) * res).toInt()
            val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val c = Canvas(bitmap)
            c.scale(bw / (w + pad * 2).toFloat(), bh / (h + pad * 2).toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                if (sigma > 0) maskFilter = BlurMaskFilter(sigmaToRadius(sigma.toFloat()), BlurMaskFilter.Blur.NORMAL)
            }
            val l = pad.toFloat()
            val t = pad.toFloat()
            if (corner > 0) {
                c.drawRoundRect(l, t, l + w.toFloat(), t + h.toFloat(), corner.toFloat(), corner.toFloat(), paint)
            } else {
                c.drawOval(l, t, l + w.toFloat(), t + h.toFloat(), paint)
            }
            bitmap
        }
    }

    /** Draws a [sprite] centred on (cx, cy) with colour [color]. */
    private fun drawSprite(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, w: Double, h: Double, sigma: Double, color: Int) {
        val pad = sigma * 3 + 1
        val hw = (w / 2 + pad).toFloat()
        val hh = (h / 2 + pad).toFloat()
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        tint.color = color
        canvas.drawBitmap(bitmap, null, rect, tint)
    }

    /** A fill paint blurred by [sigma] dp, cached by sigma in quarter steps. */
    private fun soft(sigma: Double): Paint {
        val key = (sigma * 4).roundToInt()
        return blurred.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Hardware canvases only honour mask filters from API 28.
                if (key > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    maskFilter = BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL)
                }
            }
        }
    }

    fun draw(canvas: Canvas, e: GlassMarblesTrayEngine, top: Bitmap?, insetTop: Double, density: Float) {
        val width = e.width
        val height = e.height
        // The table under the tray: warm dark wood lit from above.
        val tableKey = width * 7 + height
        if (tableFor != tableKey) {
            tableFor = tableKey
            table = RadialGradient(
                (width * 0.42).toFloat(), (e.cy - 40).toFloat(), (max(width, height) * 0.75).toFloat(),
                MarblesTable, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        shaded.shader = table
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaded)
        shaded.shader = null
        val w = e.world ?: return
        if (!e.updatePose()) return
        val pose = e.pose
        val halfW = e.trayW / 2
        val halfH = e.trayH / 2

        // The tray's shadow on the table, pushed away from the light and spread by the tilt: a
        // blurred sprite of the tray's outline laid on the floor plane. React Native's blur ignores
        // the canvas matrix, density included, so its sigma is in pixels.
        val tiltSize = hypot(w.tiltX, w.tiltY)
        pose.floorMatrix(floorValues)
        floor.setValues(floorValues)
        // Quantised to quarter-dp steps of sigma so the sprites are reused.
        val traySigma = ((16 + tiltSize * 40) / density * 4).roundToInt() / 4.0
        val shadowSprite = sprite(e.trayW, e.trayH, traySigma, OUTER_CORNER, 1f)
        canvas.save()
        canvas.concat(floor)
        drawSprite(canvas, shadowSprite, 10f, 22f, e.trayW, e.trayH, traySigma, argb(4, 2, 1, 0.6))
        canvas.restore()

        drawSides(canvas, pose, halfW, halfH)

        // The floor plane: the baked top and the marbles' shadows, under the homography.
        canvas.save()
        canvas.concat(floor)
        if (top != null && !top.isRecycled) {
            rect.set(-halfW.toFloat(), -halfH.toFloat(), halfW.toFloat(), halfH.toFloat())
            canvas.drawBitmap(top, null, rect, image)
        }
        pose.trayLight(light)
        for (i in 0 until w.n) drawShadow(canvas, w, i, shaders[i])
        canvas.restore()

        // Marbles, lowest first so a hopping one passes over the rest.
        var count = 0
        for (i in 0 until w.n) {
            var at = count
            while (at > 0 && w.z[order[at - 1]] > w.z[i]) {
                order[at] = order[at - 1]
                at -= 1
            }
            order[at] = i
            count += 1
        }
        for (k in 0 until count) {
            val i = order[k]
            pose.project(w.x[i], w.y[i], w.r[i] + w.z[i])
            drawMarble(canvas, pose.px, pose.py, w.r[i] * pose.ps, MARBLES[i], shaders[i], w.rot, i * 9)
        }
        for (f in 0 until FLASHES) {
            if (w.flashAge[f] >= FLASH_LIFE) continue
            pose.project(w.flashX[f], w.flashY[f], 12.0)
            drawFlash(canvas, pose.px, pose.py, w.flashAge[f] / FLASH_LIFE, w.flashPower[f])
        }

        // The tilt readout, top right.
        val degrees = (tiltSize * 180) / PI
        val value = String.format(java.util.Locale.US, "%.1f°", degrees)
        val right = (width - 24).toFloat()
        val readTop = (insetTop + 12).toFloat()
        canvas.drawText(value, right - valueText.measureText(value), readTop + 38, valueText)
        canvas.drawText("TILT", right - smallText.measureText("TILT"), readTop + 11, smallText)
    }

    /** The tray's sides: the faces that turn towards the eye as it tilts, shaded by the light. */
    private fun drawSides(canvas: Canvas, p: Pose, halfW: Double, halfH: Double) {
        val r = OUTER_CORNER
        val n = 28
        fun rimX(i: Int): Double {
            val c = i / 7
            val k = i % 7
            val cx = if (c == 0 || c == 1) halfW - r else -halfW + r
            return cx + cos(-PI / 2 + c * PI / 2 + (PI / 2) * (k / 6.0)) * r
        }
        fun rimY(i: Int): Double {
            val c = i / 7
            val k = i % 7
            val cy = if (c == 1 || c == 2) halfH - r else -halfH + r
            return cy + sin(-PI / 2 + c * PI / 2 + (PI / 2) * (k / 6.0)) * r
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = rimX(i)
            val ay = rimY(i)
            val bx = rimX(j)
            val by = rimY(j)
            // The face's outward normal, turned with the tray (a direction: turn it about the origin).
            var l = hypot(by - ay, bx - ax)
            if (l == 0.0) l = 1.0
            p.turn((by - ay) / l, -(bx - ax) / l, 0.0)
            val nx = p.px
            val ny = p.py
            val nz = p.pz
            // Seen when it faces the eye, which sits FOCAL dp above the tray's centre.
            p.turn((ax + bx) / 2, (ay + by) / 2, -TRAY_DEPTH / 2)
            if (nx * -p.px + ny * -p.py + nz * (FOCAL - p.pz) <= 0) continue
            path.rewind()
            p.project(ax, ay, 0.0)
            path.moveTo(p.px.toFloat(), p.py.toFloat())
            p.project(bx, by, 0.0)
            path.lineTo(p.px.toFloat(), p.py.toFloat())
            p.project(bx, by, -TRAY_DEPTH)
            path.lineTo(p.px.toFloat(), p.py.toFloat())
            p.project(ax, ay, -TRAY_DEPTH)
            path.lineTo(p.px.toFloat(), p.py.toFloat())
            path.close()
            val lit = max(0.0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])
            val shade = 0.4 + 0.6 * lit
            fill.shader = null
            fill.color = argb((96 * shade + 18).roundToInt(), (56 * shade + 10).roundToInt(), (30 * shade + 6).roundToInt(), 1.0)
            canvas.drawPath(path, fill)
        }
    }

    /** A marble's shadow and the coloured light its glass focuses into it, on the floor. */
    private fun drawShadow(canvas: Canvas, w: World, i: Int, s: MarbleShaders) {
        val r = w.r[i]
        val lift = r + w.z[i]
        val ox = (-light[0] / light[2]) * lift
        val oy = (-light[1] / light[2]) * lift
        val x = w.x[i]
        val y = w.y[i]
        val fade = 1 / (1 + w.z[i] * 0.02)
        // Contact shade right under it.
        val grounded = w.z[i] <= 0
        if (grounded) {
            drawSprite(canvas, sprite(r * 1.24, r * 1.24, 2.0, 0.0, 3f), (x + ox * 0.25).toFloat(), (y + oy * 0.25).toFloat(), r * 1.24, r * 1.24, 2.0, argb(40, 18, 4, 0.4 * fade))
        } else {
            val contact = soft(2 + w.z[i] * 0.1)
            contact.color = argb(40, 18, 4, 0.4 * fade)
            canvas.drawCircle((x + ox * 0.25).toFloat(), (y + oy * 0.25).toFloat(), (r * 0.62).toFloat(), contact)
        }
        // The cast shadow, tinted by the glass.
        val castColor = s.shadowTint or ((0.58 * fade * 255).roundToInt().coerceIn(0, 255) shl 24)
        canvas.save()
        canvas.translate((x + ox * 0.9).toFloat(), (y + oy * 0.9).toFloat())
        canvas.rotate((atan2(oy, ox) * 180 / PI).toFloat())
        if (grounded) {
            drawSprite(canvas, sprite(r * 2.5, r * 1.9, 2.5, 0.0, 3f), 0f, 0f, r * 2.5, r * 1.9, 2.5, castColor)
        } else {
            val cast = soft(2.5 + w.z[i] * 0.08)
            cast.color = castColor
            rect.set((-r * 1.25).toFloat(), (-r * 0.95).toFloat(), (r * 1.25).toFloat(), (r * 0.95).toFloat())
            canvas.drawOval(rect, cast)
        }
        canvas.restore()
        // The caustic: the glass gathers light into a bright, coloured spot inside its shadow.
        val cx = (x + ox * 1.05).toFloat()
        val cy = (y + oy * 1.05).toFloat()
        val cr = (r * 0.62).toFloat()
        local.setScale(cr, cr)
        local.postTranslate(cx, cy)
        s.caustic.setLocalMatrix(local)
        add.shader = s.caustic
        add.alpha = (fade * 255).roundToInt().coerceIn(1, 255)
        canvas.drawCircle(cx, cy, cr, add)
        add.shader = null
        add.alpha = 255
    }

    /** Draws [shader] (unit space) over the disc of the marble at (cx, cy) radius rs, with [paint]. */
    private fun disc(canvas: Canvas, shader: Shader, paint: Paint, cx: Float, cy: Float, rs: Float) {
        local.setScale(rs, rs)
        local.postTranslate(cx, cy)
        shader.setLocalMatrix(local)
        paint.shader = shader
        canvas.drawCircle(cx, cy, rs, paint)
        paint.shader = null
    }

    /** One marble on screen: the glass, the swirl that rolls with it, the inner glow and the shine. */
    private fun drawMarble(canvas: Canvas, cxd: Double, cyd: Double, rsd: Double, set: Marble, s: MarbleShaders, rot: DoubleArray, o: Int) {
        val cx = cxd.toFloat()
        val cy = cyd.toFloat()
        val rs = rsd.toFloat()
        // The glass body: clear in the middle, the tint thick at its edges.
        disc(canvas, s.body, shaded, cx, cy, rs)

        canvas.save()
        circle.rewind()
        circle.addCircle(cx, cy, rs, Path.Direction.CW)
        canvas.clipPath(circle)
        // The swirl: leaf-shaped vanes through the centre, each in a plane through the marble's own
        // axis, turned by its orientation and seen straight on. Far halves sit behind the glass.
        for (k in 0 until set.vanes) {
            val psi = (PI * k) / set.vanes
            val ex = cos(psi) * 0.74
            val ey = sin(psi) * 0.74
            var depth = 0.0
            for (t in 0 until 4) {
                // Two tips on the equator and two on the axis, slightly twisted.
                val lx: Double
                val ly: Double
                val lz: Double
                when (t) {
                    0 -> { lx = ex; ly = ey; lz = 0.0 }
                    1 -> { lx = -sin(psi) * 0.16; ly = cos(psi) * 0.16; lz = 0.72 }
                    2 -> { lx = -ex; ly = -ey; lz = 0.0 }
                    else -> { lx = sin(psi) * 0.16; ly = -cos(psi) * 0.16; lz = -0.72 }
                }
                px[t] = cxd + (rot[o] * lx + rot[o + 1] * ly + rot[o + 2] * lz) * rsd
                py[t] = cyd + (rot[o + 3] * lx + rot[o + 4] * ly + rot[o + 5] * lz) * rsd
                depth += rot[o + 6] * lx + rot[o + 7] * ly + rot[o + 8] * lz
            }
            path.rewind()
            path.moveTo(px[0].toFloat(), py[0].toFloat())
            for (t in 1..4) {
                val a = t - 1
                val b = t % 4
                // Bulge each edge outward from the centre, so the vane is a leaf, not a diamond.
                val mx = (px[a] + px[b]) / 2
                val my = (py[a] + py[b]) / 2
                path.quadTo((cxd + (mx - cxd) * 1.45).toFloat(), (cyd + (my - cyd) * 1.45).toFloat(), px[b].toFloat(), py[b].toFloat())
            }
            path.close()
            val even = k % 2 == 0
            val vane = soft(0.7)
            vane.color = (if (even) s.vane else s.vane2) or (((0.7 + 0.1 * max(-1.0, min(1.0, depth))) * 255).roundToInt() shl 24)
            canvas.drawPath(path, vane)
            stroke.strokeWidth = 0.6f
            stroke.color = if (even) s.vaneEdge else s.vane2Edge
            canvas.drawPath(path, stroke)
        }
        // The front of the glass lies over the swirl: a faint milky haze towards the light.
        disc(canvas, s.haze, shaded, cx, cy, rs)
        // Light through the glass gathers on the far side, low right.
        disc(canvas, s.glow, screen, cx, cy, rs)
        // Thick glass at the rim: a darker ring.
        disc(canvas, s.fresnel, shaded, cx, cy, rs)
        canvas.restore()

        // The shine: a sharp highlight towards the light and a soft window reflection around it.
        shaded.shader = RadialGradient(cx - rs * 0.36f, cy - rs * 0.4f, rs * 0.55f, argb(WHITE, 0.5), argb(WHITE, 0.0), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx - rs * 0.36f, cy - rs * 0.4f, rs * 0.55f, shaded)
        shaded.shader = null
        // The highlights are sprites at the marble's own size, scaled by the perspective.
        val k = rsd / set.r
        canvas.save()
        canvas.translate(cx - rs * 0.4f, cy - rs * 0.44f)
        canvas.rotate(-38f)
        canvas.scale(k.toFloat(), k.toFloat())
        drawSprite(canvas, sprite(set.r * 0.4, set.r * 0.22, 0.6, 0.0, 4f), 0f, 0f, set.r * 0.4, set.r * 0.22, 0.6, argb(WHITE, 0.95))
        canvas.restore()
        canvas.save()
        canvas.translate(cx + rs * 0.46f, cy + rs * 0.5f)
        canvas.scale(k.toFloat(), k.toFloat())
        drawSprite(canvas, sprite(set.r * 0.18, set.r * 0.18, 1.0, 0.0, 4f), 0f, 0f, set.r * 0.18, set.r * 0.18, 1.0, argb(WHITE, 0.35))
        canvas.restore()
        stroke.strokeWidth = 0.8f
        stroke.color = s.rim
        canvas.drawCircle(cx, cy, rs - 0.4f, stroke)
    }

    /** A glint where two marbles clicked: a tiny four-pointed star that flares and fades. */
    private fun drawFlash(canvas: Canvas, xd: Double, yd: Double, life: Double, power: Double) {
        val k = 1 - life
        val size = (3 + 9 * power * sin(PI * min(1.0, life * 1.6 + 0.2))).toFloat()
        val x = xd.toFloat()
        val y = yd.toFloat()
        flashLine.color = argb(255, 246, 225, 0.9 * k * power)
        canvas.drawLine(x - size, y, x + size, y, flashLine)
        canvas.drawLine(x, y - size, x, y + size, flashLine)
        flashDot.color = argb(255, 240, 210, 0.7 * k * power)
        canvas.drawCircle(x, y, (2.5 + 2 * power).toFloat(), flashDot)
    }
}
