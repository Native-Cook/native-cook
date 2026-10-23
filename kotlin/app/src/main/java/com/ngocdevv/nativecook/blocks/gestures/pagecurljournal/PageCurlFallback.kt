package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * API 26 to 32, where AGSL is not available: the same fold and the same pages, drawn with canvas clips.
 * The sheet is a flat fold (no rolled cylinder): the front page up to the fold, the printed-through back
 * mirrored over it, the page beneath with a soft cast shadow past the fold. The roll's shading, the cut
 * edge and the paper grain are the shader's alone, and so is the foil.
 */
internal class PageCurlFallback(
    private val pages: List<Bitmap>,
    private val density: Float,
    private val widthDp: Float,
    private val heightDp: Float,
) {
    private val toPoints = Matrix().apply { setScale(1f / density, 1f / density) }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val foldShadow = Paint()
    private val reach = hypot(widthDp, heightDp) * 2f

    fun drawCurl(canvas: Canvas, u: CurlUniforms) {
        canvas.save()
        canvas.scale(density, density)
        val front = pages[u.frontIdx.toInt()]
        val under = pages[u.underIdx.toInt()]

        // Reduce Motion: the front page cross-fades into the next.
        if (u.fade >= 0f) {
            canvas.drawBitmap(front, toPoints, paint)
            paint.alpha = (u.fade * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(under, toPoints, paint)
            paint.alpha = 255
            canvas.restore()
            return
        }

        val vx = u.grabX - u.tipX
        val vy = u.grabY - u.tipY
        val distance = hypot(vx, vy)
        if (distance < 0.25f) {
            canvas.drawBitmap(front, toPoints, paint)
            canvas.restore()
            return
        }
        val dx = vx / distance
        val dy = vy / distance
        val radius = max(u.rmin, u.r0)
        // The fold line passes through the axis point, perpendicular to the pull.
        val axisX = u.grabX - (distance + PI.toFloat() * radius) * 0.5f * dx
        val axisY = u.grabY - (distance + PI.toFloat() * radius) * 0.5f * dy
        val degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

        // The page beneath, with the sheet's cast shadow just past the fold.
        if (u.clearUnder < 0.5f) {
            canvas.drawBitmap(under, toPoints, paint)
        }
        canvas.save()
        canvas.translate(axisX, axisY)
        canvas.rotate(degrees)
        val shadowReach = u.shW * 6f
        foldShadow.shader = LinearGradient(
            radius, 0f, radius + shadowReach, 0f,
            intArrayOf(shadowColor(u.shA), shadowColor(u.shA * 0.25f), 0),
            floatArrayOf(0f, 0.3f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(radius, -reach, radius + shadowReach, reach, foldShadow)
        canvas.restore()

        // The flat part of the sheet: the front page on the unfolded side of the fold.
        canvas.save()
        halfPlane(canvas, axisX, axisY, degrees)
        canvas.drawBitmap(front, toPoints, paint)
        canvas.restore()

        // The back of the sheet lying over it: the front page mirrored across the fold, paper white with the print
        // showing through.
        canvas.save()
        halfPlane(canvas, axisX, axisY, degrees)
        canvas.translate(axisX, axisY)
        canvas.rotate(degrees)
        canvas.scale(-1f, 1f)
        // In the shader q = p + (PI * R - 2 * x) * d, so the mirrored paper starts one half-circumference
        // beyond the fold axis in the sheet's source coordinates.
        canvas.translate(-PI.toFloat() * radius, 0f)
        canvas.rotate(-degrees)
        canvas.translate(-axisX, -axisY)
        canvas.clipRect(0f, 0f, widthDp, heightDp)
        val showThrough = u.bg * u.ba / 3f
        val paper = u.bg * (1f - u.ba)
        val greenTint = 0.972f
        val blueTint = 0.918f
        val matrix = ColorMatrix(
            floatArrayOf(
                showThrough, showThrough, showThrough, 0f, 255f * paper,
                showThrough * greenTint, showThrough * greenTint, showThrough * greenTint, 0f, 255f * paper * greenTint,
                showThrough * blueTint, showThrough * blueTint, showThrough * blueTint, 0f, 255f * paper * blueTint,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(front, toPoints, paint)
        paint.colorFilter = null
        canvas.restore()

        // The fold's dark line.
        canvas.save()
        canvas.translate(axisX, axisY)
        canvas.rotate(degrees)
        foldShadow.shader = LinearGradient(
            -radius * 2f, 0f, radius * 2f, 0f,
            intArrayOf(0, shadowColor(0.18f), 0),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(-radius * 2f, -reach, radius * 2f, reach, foldShadow)
        canvas.restore()
        canvas.restore()
    }

    /** Clips to the side of the fold line that holds the flat sheet (the axis-relative x of 0 or less). */
    private fun halfPlane(canvas: Canvas, axisX: Float, axisY: Float, degrees: Float) {
        canvas.translate(axisX, axisY)
        canvas.rotate(degrees)
        canvas.clipRect(-reach, -reach, 0f, reach)
        // Back to the page's own space: a clip stays where it was set.
        canvas.rotate(-degrees)
        canvas.translate(-axisX, -axisY)
    }

    private fun shadowColor(alpha: Float): Int = ((alpha * 255f).toInt().coerceIn(0, 255)) shl 24
}
