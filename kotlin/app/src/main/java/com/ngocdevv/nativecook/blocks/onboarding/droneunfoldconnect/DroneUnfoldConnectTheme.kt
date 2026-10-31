package com.ngocdevv.nativecook.blocks.onboarding.droneunfoldconnect

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.roundToInt

internal object DroneUnfoldConnectTheme {
    val page = Color.rgb(13, 16, 20)
    val ink = Color.rgb(241, 243, 245)
    val mute = Color.rgb(138, 144, 153)
    val card = Color.rgb(23, 27, 33)
    val line = Color.rgb(38, 44, 52)
    val accent = Color.rgb(233, 255, 112)
    val accentInk = Color.rgb(26, 31, 0)
    fun light(v: Float): Int {
        val f = v.coerceIn(0f, 1f)
        fun channel(a: Float, b: Float) = ((a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * f).pow(1 / 2.2f)).roundToInt()
        return Color.rgb(channel(255f, 61f), channel(91f, 220f), channel(79f, 132f))
    }
}

internal class DroneUnfoldConnectArt(assets: AssetManager) {
    private val regular = Typeface.createFromAsset(assets, "droneunfoldconnect/droneunfoldconnect_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "droneunfoldconnect/droneunfoldconnect_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(assets, "droneunfoldconnect/droneunfoldconnect_Inter-SemiBold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val c = DroneUnfoldConnectTheme
    private fun fill(color: Int, alpha: Float = 1f) {
        paint.shader = null; paint.color = color; paint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        paint.style = Paint.Style.FILL
    }
    private fun pill(cv: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Float = 1f, stroke: Float = 0f) {
        fill(color, alpha)
        if (stroke > 0) { paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke }
        rect.set(x, y, x + w, y + h)
        cv.drawRoundRect(rect, h / 2, h / 2, paint)
    }
    private fun text(cv: Canvas, value: String, x: Float, cy: Float, size: Float, color: Int, bold: Boolean = false, alpha: Float = 1f) {
        fill(color, alpha); paint.typeface = if (bold) semibold else medium
        paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        cv.drawText(value, x, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }
    fun ui(cv: Canvas, w: Float, top: Float, cardTop: Float, bottomTop: Float, s: DroneUnfoldConnectState) {
        val f = s.frame
        // Header follows the reference's space-between layout (including the 36 pt Land slot).
        fill(c.ink); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        val cy = top + 30
        cv.drawPath(Path().apply { moveTo(30f, cy - 8); lineTo(22f, cy); lineTo(30f, cy + 8) }, paint)
        text(cv, "Add drone", (w - 24) / 2, cy, 17f, c.ink, true)
        if (s.stage.intValue == 2) text(cv, "Land", w - 38, cy, 16f, c.accent, true)
        text(cv, "Skyline Mini", w / 2, cardTop + 16, 26f, c.ink, true)
        val status = s.status()
        fill(c.mute); paint.typeface = medium; paint.textSize = 15f
        val textWidth = paint.measureText(status)
        val rowX = (w - textWidth - 16) / 2
        fill(c.light(f.light)); cv.drawCircle(rowX + 4, cardTop + 47, 4f, paint)
        text(cv, status, rowX + 16 + textWidth / 2, cardTop + 47, 15f, c.mute)
        val connectAlpha = (1 - f.connected) * (1 - 0.55f * (f.unfold * 3).coerceAtMost(1f))
        cv.save(); cv.scale(1 - 0.04f * f.pressConnect, 1 - 0.04f * f.pressConnect, w / 2, bottomTop + 60)
        pill(cv, 20f, bottomTop + 32, w - 40, 56f, c.accent, connectAlpha)
        text(cv, "Connect", w / 2, bottomTop + 60, 17f, c.accentInk, true, connectAlpha)
        cv.restore()
        cv.save(); val padScale = 0.9f + 0.1f * f.connected
        cv.scale(padScale, padScale, w / 2, bottomTop + 60)
        pill(cv, w / 2 - 60, bottomTop, 120f, 120f, c.card, f.connected)
        pill(cv, w / 2 - 59.5f, bottomTop + 0.5f, 119f, 119f, c.line, f.connected, 1f)
        val kx = w / 2 + f.stickX * 44 - 26
        val ky = bottomTop + 60 + f.stickY * 44 - 26
        pill(cv, kx, ky, 52f, 52f, Color.rgb(44, 51, 61), f.connected)
        pill(cv, kx + 0.5f, ky + 0.5f, 51f, 51f, Color.rgb(58, 67, 79), f.connected, 1f)
        cv.restore()
        val fx = w / 2 + if (f.fingerAt == 1) f.stickX * 44 else 0f
        val fy = bottomTop + 60 + if (f.fingerAt == 1) f.stickY * 44 else 0f
        val radius = 22 * (1 - 0.12f * f.pressConnect)
        pill(cv, fx - radius, fy - radius, radius * 2, radius * 2, Color.WHITE, 0.35f * 0.85f * f.finger)
        pill(cv, fx - radius, fy - radius, radius * 2, radius * 2, Color.WHITE, 0.8f * 0.85f * f.finger, 1.5f)
    }
    /** Flat, same-palette fallback for Android 8–12 or an unavailable GPU shader. */
    fun fallback(cv: Canvas, w: Float, floor: Float, ppu: Float, s: DroneUnfoldConnectState) {
        val f = s.frame
        val x = w / 2 + f.drift * ppu
        val y = floor - (f.lift + 0.2f) * ppu
        fill(Color.BLACK, 0.25f / (1 + f.lift)); rect.set(x - ppu * 0.6f, floor - 10, x + ppu * 0.6f, floor + 10); cv.drawOval(rect, paint)
        cv.save(); cv.rotate(-f.roll * 35, x, y)
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            val folded = if (sz > 0) 0f else Math.PI.toFloat()
            val open = if (sz > 0) Math.PI.toFloat() * 0.25f else Math.PI.toFloat() * 0.75f
            val a = folded + (open - folded) * f.unfold
            val hx = x + sx * 0.32f * ppu; val hy = y + sz * 0.42f * ppu * 0.38f
            val tx = hx + sin(a) * sx * 0.78f * ppu; val ty = hy + cos(a) * 0.78f * ppu * 0.38f
            fill(Color.rgb(51, 54, 59)); paint.strokeWidth = 0.13f * ppu; paint.strokeCap = Paint.Cap.ROUND; cv.drawLine(hx, hy, tx, ty, paint)
            fill(Color.rgb(140, 145, 153)); cv.drawCircle(tx, ty, ppu * 0.1f, paint)
            fill(Color.rgb(200, 210, 230), 1 - f.spin * 0.8f)
            rect.set(tx - ppu * 0.3f, ty - ppu * 0.06f, tx + ppu * 0.3f, ty + ppu * 0.06f); cv.drawOval(rect, paint)
        }
        pill(cv, x - ppu * 0.3f, y - ppu * 0.4f, ppu * 0.6f, ppu * 0.8f, Color.rgb(41, 43, 48))
        pill(cv, x - ppu * 0.25f, y - ppu * 0.4f, ppu * 0.5f, ppu * 0.65f, Color.rgb(209, 214, 222))
        fill(Color.rgb(13, 20, 36)); cv.drawCircle(x, y + ppu * 0.4f, ppu * 0.08f, paint)
        fill(c.light(f.light)); cv.drawCircle(x, y - ppu * 0.42f, ppu * 0.035f, paint)
        cv.restore()
    }
}
