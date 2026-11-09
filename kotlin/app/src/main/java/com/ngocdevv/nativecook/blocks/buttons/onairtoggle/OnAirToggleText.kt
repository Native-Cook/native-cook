package com.ngocdevv.nativecook.blocks.buttons.onairtoggle

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.roundToInt

/** Cached layouts reproduce the RN overlay without laying out text on every frame. */
internal class OnAirToggleText(
    private val pixelsPerDp: Float,
    private val fontScale: Float,
    private val width: Float,
    private val height: Float,
    private val safeTop: Float,
    private val safeBottom: Float,
) {
    private class Label(
        val layout: StaticLayout,
        val opacity: Float,
        val leading: Float,
        val height: Float,
        val naturalWidth: Float,
    )

    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun face(weight: Int): Typeface = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(sans, weight, false)
    } else {
        Typeface.create(
            when (weight) {
                800 -> "sans-serif-black"
                700 -> "sans-serif"
                else -> "sans-serif-medium"
            },
            if (weight == 700) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    private fun label(
        value: String,
        size: Float,
        weight: Int = 400,
        tracking: Float = 0f,
        opacity: Float = 1f,
        lineHeight: Float? = null,
    ): Label {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (weight == 400) sans else face(weight)
            // Avoid TextPaint.density (defaults to 1): layouts are px, scene coordinates are dp.
            textSize = size * pixelsPerDp * fontScale
            letterSpacing = tracking / size
        }
        val leading = lineHeight?.let {
            it * pixelsPerDp * fontScale - (paint.fontMetrics.descent - paint.fontMetrics.ascent)
        } ?: 0f
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, ((width - 48) * pixelsPerDp).roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(leading, 1f)
            .build()
        return Label(
            layout, opacity, leading / pixelsPerDp / 2,
            lineHeight?.let { it * fontScale * layout.lineCount } ?: (layout.height / pixelsPerDp),
            (0 until layout.lineCount).maxOf { layout.getLineWidth(it) } / pixelsPerDp,
        )
    }

    private val brand = label("Murmur", 20f, 800, tracking = -0.4f)
    private val meta = label("Studio A · 48 kHz", 13f, 600, opacity = 0.6f)
    private val overline = label("EPISODE 48 · TUESDAY", 12f, 700, tracking = 2f, opacity = 0.5f)
    private val title = label("The Quiet Hours", 34f, 700, tracking = -0.8f, lineHeight = 40f)
    private val sub = label("Ada Kole with two guests from the night shift", 15f, opacity = 0.62f, lineHeight = 20f)
    private val off = label("Off air", 17f, 600, tracking = 0.2f)
    private val on = label("On air", 17f, 600, tracking = 0.2f)
    private val mic = label("Mic 1 · Ribbon", 16f, 700)
    private val input = label("Input −12 dB", 14f, opacity = 0.62f)
    private val guest = label("Guest line", 16f, 700)
    private val waiting = label("2 waiting", 14f, opacity = 0.62f)
    private val foot = label("Tap the switch or slide the lamp to go live.", 13f, opacity = 0.5f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowExtent = 24f
    // Rasterize once on software Canvas: hardware Canvas ignores shape BlurMaskFilter.
    private val dotGlow = Bitmap.createBitmap(
        (glowExtent * 2 * pixelsPerDp).roundToInt().coerceAtLeast(1),
        (glowExtent * 2 * pixelsPerDp).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NativeColor.rgb(255, 75, 58)
            maskFilter = BlurMaskFilter((6f * pixelsPerDp - 0.5f).coerceAtLeast(0.01f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        NativeCanvas(bitmap).drawCircle(glowExtent * pixelsPerDp, glowExtent * pixelsPerDp, 4f * pixelsPerDp, blur)
    }

    private fun color(a: Int, b: Int, u: Double, alpha: Double = 1.0): Int {
        fun channel(shift: Int) = (((a shr shift) and 255) +
            (((b shr shift) and 255) - ((a shr shift) and 255)) * u).roundToInt().coerceIn(0, 255)
        return NativeColor.argb((255 * alpha).roundToInt().coerceIn(0, 255), channel(16), channel(8), channel(0))
    }

    private fun draw(c: NativeCanvas, label: Label, x: Float, y: Float, ink: Int, alpha: Double = 1.0) {
        if (alpha <= 0) return
        label.layout.paint.color = ink
        label.layout.paint.alpha = (255 * label.opacity * alpha).roundToInt().coerceIn(0, 255)
        c.save()
        c.translate(x, y + label.leading)
        c.scale(1 / pixelsPerDp, 1 / pixelsPerDp)
        label.layout.draw(c)
        c.restore()
    }

    fun draw(c: NativeCanvas, f: OnAirToggleFrame) {
        val room = f.room.coerceIn(0.0, 1.0)
        val glow = f.glow.coerceIn(0.0, 1.0)
        val ink = color(0x1d1a1a, 0xf3eeea, onAirToggleInkMix(room))
        val headY = safeTop + 8
        draw(c, brand, 24f, headY + (32 - brand.height) / 2, ink)
        draw(c, meta, width - 24 - meta.naturalWidth, headY + (32 - meta.height) / 2, ink)
        val overlineY = headY + 32 + 22
        draw(c, overline, 24f, overlineY, ink)
        val titleY = overlineY + overline.height + 6
        draw(c, title, 24f, titleY, ink)
        draw(c, sub, 24f, titleY + title.height + 4, ink)

        val statusY = height * 0.48f + 74 + 18 + 30
        val dotX = (width - (8 + 8 + off.naturalWidth)) / 2 + 4
        val dotY = statusY + 12
        if (glow > 0.001) {
            paint.shader = null
            paint.color = NativeColor.WHITE
            paint.alpha = (255 * 0.9 * glow).roundToInt()
            c.save()
            c.translate(dotX - glowExtent, dotY - glowExtent)
            c.scale(1 / pixelsPerDp, 1 / pixelsPerDp)
            c.drawBitmap(dotGlow, 0f, 0f, paint)
            c.restore()
        }
        paint.color = color(0xb5aea8, 0xff4b3a, glow)
        paint.alpha = 255
        c.drawCircle(dotX, dotY, 4f, paint)
        val stateX = dotX + 4 + 8
        draw(c, off, stateX, statusY + (24 - off.height) / 2, ink, ((0.5 - room) * 3).coerceIn(0.0, 1.0))
        draw(c, on, stateX, statusY + (24 - off.height) / 2, ink, ((room - 0.5) * 3).coerceIn(0.0, 1.0))

        val footY = height - safeBottom - 14 - foot.height
        val cardBottom = footY - 12
        val border = 1 / pixelsPerDp
        val cardTop = cardBottom - (32 + mic.height + 3 + input.height + 2 * border)
        paint.style = Paint.Style.FILL
        paint.color = NativeColor.WHITE
        paint.alpha = (255 * (0.55 - 0.49 * room)).roundToInt()
        c.drawRoundRect(20f, cardTop, width - 20, cardBottom, 22f, 22f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = border
        paint.color = if (room > 0.5) color(0xffffff, 0xffffff, 0.0, 0.1 * room)
            else color(0x1d1a1a, 0x1d1a1a, 0.0, 0.08 * (1 - room))
        c.drawRoundRect(20 + border / 2, cardTop + border / 2, width - 20 - border / 2,
            cardBottom - border / 2, 22 - border / 2, 22 - border / 2, paint)
        paint.style = Paint.Style.FILL
        val cardY = cardTop + 16 + border
        draw(c, mic, 38 + border, cardY, ink)
        draw(c, input, 38 + border, cardY + mic.height + 3, ink)
        draw(c, guest, width - 38 - border - guest.naturalWidth, cardY, ink)
        draw(c, waiting, width - 38 - border - waiting.naturalWidth, cardY + guest.height + 3, ink)
        draw(c, foot, (width - foot.naturalWidth) / 2, footY, ink)
    }
}
