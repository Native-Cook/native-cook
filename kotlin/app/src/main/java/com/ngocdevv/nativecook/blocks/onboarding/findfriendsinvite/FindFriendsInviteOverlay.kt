package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Box
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Colors
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteTheme.Radius
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Everything that leaves the layout, drawn after the rest in dp units: the folded paper and its flight,
 * the liquid contacts (AGSL metaball field on API 33+, a union of circles below it) and the faces
 * riding in the blob and the droplets. Paints, the gradient and the shader are made once.
 */
internal class FindFriendsInviteOverlay {
    private val fold = FindFriendsInviteFold
    private val goo = FindFriendsInviteGoo
    private val colors = goo.colors(listOf(0xF298B9, 0xADEEA9, 0xCCC0FB, 0xD5D5D8))
    private val matrix = Matrix()
    private val values = FloatArray(9)
    private val path = Path()
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gooPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradientWidth = -1f
    private var gradient: Shader? = null
    private var runtime: Any? = null
    private val runtimeReady = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun gradientFor(width: Float): Shader {
        if (gradient == null || gradientWidth != width) {
            val stops = Colors.inviteStops
            gradient = LinearGradient(
                0f, 0f, width, 0f,
                IntArray(stops.size) { stops[it].second.toArgb() },
                FloatArray(stops.size) { stops[it].first },
                Shader.TileMode.CLAMP,
            )
            gradientWidth = width
        }
        return gradient!!
    }

    fun draw(
        canvas: Canvas,
        clocks: M.Clocks,
        geometry: FindFriendsInviteGeometry,
        assets: FindFriendsInviteAssets,
    ) {
        if (clocks.reduced) return
        val inv = if (clocks.warm) M.Warm.inv else clocks.invite
        val scan = if (clocks.warm) M.Warm.scan else clocks.scan
        val paperGeo = paperGeometry(geometry)
        val frame = if (fold.paperOn(inv)) fold.frame(inv, paperGeo, Radius.invite.toDouble()) else null
        val contacts = if (FindFriendsInviteGoo.gooOn(scan)) goo.frame(scan, gooGeometry(geometry)) else null
        if (frame == null && contacts == null) return

        val save = canvas.save()
        canvas.scale(geometry.density, geometry.density)
        if (clocks.warm) canvas.saveLayerAlpha(null, 1)
        if (frame != null) drawPaper(canvas, frame, paperGeo)
        if (contacts != null) {
            drawLiquid(canvas, contacts, geometry)
            drawFaces(canvas, contacts, geometry, assets)
        }
        canvas.restoreToCount(save)
    }

    private fun paperGeometry(g: FindFriendsInviteGeometry): FindFriendsInviteFold.Geo {
        val start = M.Flight.points[0]
        val w = g.inviteWidth.toDouble()
        val h = Box.invite.height.toDouble()
        return FindFriendsInviteFold.Geo(
            w = w, h = h, left = g.inviteLeft.toDouble(), top = g.inviteTop.toDouble(),
            camX = M.Flight.cameraAtX + g.dx, camY = M.Flight.cameraAtY + g.dy,
            offX = g.inviteLeft + 3 * w / 4 - start[0], offY = g.inviteTop + h / 2 - start[1],
        )
    }

    private fun gooGeometry(g: FindFriendsInviteGeometry): FindFriendsInviteGoo.Geo {
        val rest = Box.avatars.map {
            FindFriendsInviteGoo.Ball((it.centerX + g.dx).toDouble(), (it.centerY + g.dy).toDouble(), it.diameter / 2.0)
        }
        var ax = 0.0
        var ay = 0.0
        var aw = 0.0
        for (b in rest) {
            ax += b.x * b.r * b.r
            ay += b.y * b.r * b.r
            aw += b.r * b.r
        }
        return FindFriendsInviteGoo.Geo(rest, ax / aw, ay / aw, g.widthDp.toDouble())
    }

    /** A Skia blur sigma as a BlurMaskFilter radius. */
    private fun blurRadius(sigma: Double): Float = max(0.5, (sigma - 0.5) / 0.57735).toFloat()

    // MARK: paper

    private fun shape(canvas: Canvas, i: Int, frame: FindFriendsInviteFold.Frame, geo: FindFriendsInviteFold.Geo, darts: Array<DoubleArray>, paint: Paint) {
        val w = geo.w.toFloat()
        val h = geo.h.toFloat()
        val r = minOf(frame.radius.toFloat(), h / 2f)
        when (i) {
            0 -> {
                if (frame.flat) canvas.clipRect(0f, 0f, w, h) else canvas.clipRect(0f, 0f, w / 2f, h)
                rect.set(0f, 0f, w, h)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            1 -> {
                canvas.clipRect(w / 2f, 0f, w, h)
                rect.set(0f, 0f, w, h)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            else -> {
                val p = darts[i - 2]
                path.reset()
                path.moveTo(p[0].toFloat(), p[1].toFloat())
                path.lineTo(p[2].toFloat(), p[3].toFloat())
                path.lineTo(p[4].toFloat(), p[5].toFloat())
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun setMatrix(m: DoubleArray) {
        for (k in 0..8) values[k] = m[k].toFloat()
        matrix.setValues(values)
    }

    private fun drawPaper(canvas: Canvas, frame: FindFriendsInviteFold.Frame, geo: FindFriendsInviteFold.Geo) {
        val darts = fold.dartOutlines(geo.w, geo.h)

        // Shadows on the card: one blurred layer.
        if (frame.any) {
            val layer = canvas.saveLayerAlpha(null, (frame.shadowOpacity * 255).roundToInt())
            shadowPaint.maskFilter = BlurMaskFilter(blurRadius(frame.shadowBlur), BlurMaskFilter.Blur.NORMAL)
            for (i in fold.drawOrder) {
                if (frame.vis[i] <= 0.0) continue
                val s = canvas.save()
                setMatrix(frame.shadows[i])
                canvas.concat(matrix)
                shadowPaint.alpha = (frame.vis[i] * 255).roundToInt()
                shape(canvas, i, frame, geo, darts, shadowPaint)
                canvas.restoreToCount(s)
            }
            canvas.restoreToCount(layer)
        }

        // Contrail: six strips of the ribbon, each a little more opaque toward the dart.
        if (frame.trailOpacity > 0.0 && frame.trail.size >= 8) {
            val layer = canvas.saveLayerAlpha(null, (frame.trailOpacity * 255).roundToInt())
            trailPaint.maskFilter = BlurMaskFilter(blurRadius(M.Contrail.blur), BlurMaskFilter.Blur.NORMAL)
            val count = M.Contrail.samples
            val segments = M.Contrail.segments
            for (j in 0 until segments) {
                val a = (j * (count - 1)) / segments
                val b = ((j + 1) * (count - 1)) / segments
                path.reset()
                path.moveTo(frame.trail[4 * a].toFloat(), frame.trail[4 * a + 1].toFloat())
                for (k in a + 1..b) path.lineTo(frame.trail[4 * k].toFloat(), frame.trail[4 * k + 1].toFloat())
                for (k in b downTo a) path.lineTo(frame.trail[4 * k + 2].toFloat(), frame.trail[4 * k + 3].toFloat())
                path.close()
                trailPaint.color = Colors.planeInk.toArgb()
                trailPaint.alpha = (M.Contrail.opacityHead * (j + 1) / segments * 255).roundToInt()
                canvas.drawPath(path, trailPaint)
            }
            canvas.restoreToCount(layer)
        }

        // The paper: the button's own gradient printed on every face, lit from the upper left.
        for (i in fold.drawOrder) {
            val vis = frame.vis[i]
            if (vis <= 0.0) continue
            val s = canvas.save()
            if (vis < 0.999) canvas.saveLayerAlpha(null, (vis * 255).roundToInt())
            setMatrix(frame.mats[i])
            canvas.concat(matrix)
            fill.shader = gradientFor(geo.w.toFloat())
            fill.alpha = 255
            shape(canvas, i, frame, geo, darts, fill)
            overlayPaint.shader = null
            if (frame.dark[i] > 0.0) {
                overlayPaint.color = android.graphics.Color.BLACK
                overlayPaint.alpha = (frame.dark[i] * 255).roundToInt()
                shape(canvas, i, frame, geo, darts, overlayPaint)
            }
            if (frame.bright[i] > 0.0) {
                overlayPaint.color = android.graphics.Color.WHITE
                overlayPaint.alpha = (frame.bright[i] * 255).roundToInt()
                shape(canvas, i, frame, geo, darts, overlayPaint)
            }
            canvas.restoreToCount(s)
        }
        fill.shader = null
    }

    // MARK: liquid contacts

    private fun drawLiquid(canvas: Canvas, frame: FindFriendsInviteGoo.Frame, g: FindFriendsInviteGeometry) {
        var top = Double.POSITIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (b in gooGeometry(g).rest) {
            top = minOf(top, b.y - b.r)
            bottom = maxOf(bottom, b.y + b.r)
        }
        val bandY = (top - M.Goo.bandPad).toFloat()
        val bandH = (bottom - top + 2 * M.Goo.bandPad).toFloat()
        if (runtimeReady) {
            val shader = runtimeShader()
            for (i in 0 until FindFriendsInviteGoo.BALLS) {
                shader.setFloatUniform("b$i", frame.balls[i * 3], frame.balls[i * 3 + 1], frame.balls[i * 3 + 2], 0f)
            }
            shader.setFloatUniform("goo", frame.goo.toFloat())
            shader.setFloatUniform("gloss", frame.gloss.toFloat())
            gooPaint.shader = shader
            canvas.drawRect(0f, bandY, g.widthDp, bandY + bandH, gooPaint)
            gooPaint.shader = null
        } else {
            // No AGSL: the circles as they are (no bridges between nearby balls).
            for (i in 0 until FindFriendsInviteGoo.BALLS) {
                val r = frame.balls[i * 3 + 2]
                if (r <= 0f) continue
                val c = colors[i]
                gooPaint.color = android.graphics.Color.rgb((c[0] * 255).roundToInt(), (c[1] * 255).roundToInt(), (c[2] * 255).roundToInt())
                canvas.drawCircle(frame.balls[i * 3], frame.balls[i * 3 + 1], r, gooPaint)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun runtimeShader(): RuntimeShader {
        (runtime as? RuntimeShader)?.let { return it }
        val shader = RuntimeShader(FindFriendsInviteGoo.AGSL)
        for (i in 0 until FindFriendsInviteGoo.BALLS) {
            shader.setFloatUniform("k$i", colors[i][0], colors[i][1], colors[i][2])
        }
        runtime = shader
        return shader
    }

    private fun drawFaces(canvas: Canvas, frame: FindFriendsInviteGoo.Frame, g: FindFriendsInviteGeometry, assets: FindFriendsInviteAssets) {
        for ((j, d) in frame.drops.withIndex()) {
            if (!d.live) continue
            val key = M.Goo.dropHeads[j]
            val avatar = Box.avatars.first { it.source == key }
            val r = M.Goo.drops[j].r.toFloat()
            val s = canvas.save()
            canvas.translate(d.x.toFloat(), d.y.toFloat())
            canvas.scale(d.s.toFloat(), d.s.toFloat())
            path.reset()
            path.addCircle(0f, 0f, r, Path.Direction.CW)
            canvas.clipPath(path)
            if (M.Goo.dropMirror[j]) canvas.scale(-1f, 1f)
            val img = 2f * r * avatar.head
            bitmapPaint.alpha = (d.o * 255).roundToInt()
            drawHead(canvas, assets.head(key).asAndroidBitmap(), -img / 2f, -img / 2f + avatar.dy * 2f * r, img)
            canvas.restoreToCount(s)
        }
        for ((i, f) in frame.faces.withIndex()) {
            val a = Box.avatars[i]
            val r = a.diameter / 2f
            val s = canvas.save()
            canvas.translate(a.centerX + g.dx + f.tx.toFloat(), a.centerY + g.dy + f.ty.toFloat())
            canvas.scale(f.s.toFloat(), f.s.toFloat())
            path.reset()
            path.addCircle(0f, 0f, r, Path.Direction.CW)
            canvas.clipPath(path)
            val img = a.diameter * a.head
            bitmapPaint.alpha = (f.o * 255).roundToInt()
            drawHead(canvas, assets.head(a.source).asAndroidBitmap(), -img / 2f, -img / 2f + a.dy * a.diameter, img)
            canvas.restoreToCount(s)
        }
    }

    private fun drawHead(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, size: Float) {
        rect.set(left, top, left + size, top + size)
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
    }
}
