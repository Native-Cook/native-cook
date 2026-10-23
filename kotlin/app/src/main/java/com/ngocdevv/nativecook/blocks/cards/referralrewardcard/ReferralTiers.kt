package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.max
import kotlin.math.min

// The reward discs overlap like links in a chain. Filling one must leave glass visible
// between it and its neighbor, which is why the separating cut-out uses DST_OUT.
internal class ReferralTiers {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val clear =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    private val icons = Array(4) { PathParser.createPathFromPathData(ReferralIcons.paths[it]) }

    fun draw(
        canvas: Canvas,
        state: ReferralState,
    ) {
        val layer = canvas.saveLayer(-3f, -3f, 129f, 33f, null)
        for (i in 2 downTo 0) {
            val fill = max(state.level.value - i, 0.0)
            val disc = min(fill * 1.4, 1.0).toFloat()
            val scale = if (fill <= 0) 1f else (0.84 + 0.16 * min(fill, 1.2)).toFloat()
            val x = Size.tierDiameter / 2 + Size.tierStep.toFloat() * i
            canvas.save()
            canvas.scale(scale, scale, x, (Size.tierDiameter / 2f))
            clear.alpha = (255 * disc).toInt()
            canvas.drawCircle(x, (Size.tierDiameter / 2f), 16.5f, clear)
            paint.color = Tone.ink
            paint.alpha = (255 * disc).toInt()
            canvas.drawCircle(x, (Size.tierDiameter / 2f), (Size.tierDiameter / 2f), paint)
            line.color = Tone.ink
            line.alpha = (255 * Tone.ringTrackAlpha * (1 - min(fill * 2, 1.0))).toInt()
            line.strokeWidth = 1.5f
            canvas.drawCircle(x, (Size.tierDiameter / 2f), 14.25f, line)
            canvas.translate(x - 8.4f, 15 - 8.4f - (if (i == 1) 0f else 1f))
            canvas.scale(0.7f, 0.7f)
            line.color = if (fill > 0.5) Tone.night else Tone.sub
            line.strokeWidth = 1.5f / 0.7f
            canvas.drawPath(icons[i], line)
            canvas.restore()
        }
        canvas.save()
        val scale = (1 + 0.12 * state.bump.value).toFloat()
        canvas.scale(scale, scale, 111f, (Size.tierDiameter / 2f))
        paint.color = Tone.ink
        paint.alpha = 255
        canvas.drawCircle(111f, (Size.tierDiameter / 2f), (Size.tierDiameter / 2f), paint)
        canvas.translate(111 - 15.6f, 15 - 15.6f)
        canvas.scale(1.3f, 1.3f)
        paint.color = Tone.night
        canvas.drawPath(icons[3], paint)
        canvas.restore()
        canvas.restoreToCount(layer)
        clear.alpha = 255
    }
}
