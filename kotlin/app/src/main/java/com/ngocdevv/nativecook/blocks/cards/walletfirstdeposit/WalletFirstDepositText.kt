package com.ngocdevv.nativecook.blocks.cards.walletfirstdeposit

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.pow

private data class DigitSlot(
    val fixed: Char? = null,
    val from: Int = 0,
    val to: Int = 0,
    val cycles: Int = 0,
    val enter: Boolean = false,
    val grey: Boolean = false,
)

private fun typeface(weight: Int): Typeface = when {
    weight >= 600 -> Typeface.create("sans-serif", Typeface.BOLD)
    weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.create("sans-serif", Typeface.NORMAL)
}

private fun Color.argb(): Int = android.graphics.Color.argb(
    (alpha * 255f).roundToInt().coerceIn(0, 255),
    (red * 255f).roundToInt().coerceIn(0, 255),
    (green * 255f).roundToInt().coerceIn(0, 255),
    (blue * 255f).roundToInt().coerceIn(0, 255),
)

private fun makePaint(geo: WalletGeo, size: Float, color: Color, weight: Int, tracking: Float = 0f, alpha: Float = 1f): NativePaint =
    NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)).argb()
        textSize = size * geo.fontScale * geo.density
        typeface = typeface(weight)
        // Android Paint's value is em units; the reference tracking is in logical points.
        letterSpacing = if (textSize > 0f) tracking * geo.fontScale * geo.density / textSize else 0f
        isSubpixelText = true
    }

private fun DrawScope.label(geo: WalletGeo, value: String, x: Float, baseline: Float, size: Float, color: Color = Ink, weight: Int = 400, tracking: Float = 0f, alpha: Float = 1f) {
    if (alpha <= 0.001f) return
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(value, geo.u * x, geo.y(baseline), makePaint(geo, size, color, weight, tracking, alpha)) }
}

private fun DrawScope.centerLabel(geo: WalletGeo, value: String, centerX: Float, baseline: Float, size: Float, color: Color = Ink, weight: Int = 400, tracking: Float = 0f, alpha: Float = 1f) {
    if (alpha <= 0.001f) return
    drawIntoCanvas { canvas ->
        val paint = makePaint(geo, size, color, weight, tracking = tracking, alpha = alpha)
        paint.textAlign = NativePaint.Align.CENTER
        canvas.nativeCanvas.drawText(value, geo.u * centerX, geo.y(baseline), paint)
    }
}

private fun DrawScope.rightLabel(geo: WalletGeo, value: String, right: Float, baseline: Float, size: Float, color: Color = Ink, weight: Int = 500, alpha: Float = 1f) {
    if (alpha <= 0.001f) return
    drawIntoCanvas { canvas ->
        val paint = makePaint(geo, size, color, weight, alpha = alpha)
        paint.textAlign = NativePaint.Align.RIGHT
        canvas.nativeCanvas.drawText(value, geo.u * right, geo.y(baseline), paint)
    }
}

private fun easeOutBack(value: Float): Float {
    val x = clamp01(value)
    val c1 = 1.2f
    return 1f + (c1 + 1f) * (x - 1f).pow(3f) + c1 * (x - 1f).pow(2f)
}

/** Draws the same right-to-left staggered rolling slots used by the reference odometer. */
private fun DrawScope.odometer(geo: WalletGeo, slots: List<DigitSlot>, progress: Float, span: Float, left: Float, baseline: Float, size: Float, color: Color, greyColor: Color, tracking: Float) {
    val n = slots.size
    val duration = 1f - ((n - 1) * STAGGER_DIGITS) / span
    var cursor = geo.u * left
    drawIntoCanvas { canvas ->
        slots.forEachIndexed { index, slot ->
            val q = clamp01((progress - ((n - 1 - index) * STAGGER_DIGITS) / span) / duration)
            val presence = if (slot.enter) easeOutBack(q) else 1f
            val shownAlpha = if (slot.enter) clamp01(q * 1.6f) else 1f
            val paint = makePaint(geo, size, if (slot.grey) greyColor else color, 500, tracking, shownAlpha)
            if (slot.fixed != null) {
                val glyph = slot.fixed.toString()
                canvas.nativeCanvas.drawText(glyph, cursor, geo.y(baseline), paint)
                cursor += paint.measureText(glyph) * presence
            } else {
                val amount = slot.to - slot.from + 10 * slot.cycles
                val value = slot.from + amount * easeOut(q)
                val modulo = ((value % 10f) + 10f) % 10f
                val currentDigit = floor(modulo).toInt().coerceIn(0, 9)
                val fraction = modulo - floor(modulo)
                val currentWidth = paint.measureText(currentDigit.toString())
                val nextWidth = paint.measureText(((currentDigit + 1) % 10).toString())
                val glyphWidth = currentWidth * (1f - fraction) + nextWidth * fraction
                val baselinePx = geo.y(baseline)
                val lineHeight = kotlin.math.ceil(paint.textSize * 1.2f)
                val windowTop = baselinePx - ASCENT * paint.textSize
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipRect(cursor, windowTop, cursor + paint.textSize, windowTop + lineHeight)
                canvas.nativeCanvas.drawText(currentDigit.toString(), cursor, baselinePx - fraction * lineHeight, paint)
                canvas.nativeCanvas.drawText(((currentDigit + 1) % 10).toString(), cursor, baselinePx + (1f - fraction) * lineHeight, paint)
                canvas.nativeCanvas.restore()
                cursor += glyphWidth * presence
            }
        }
    }
}

private fun Color.mix(other: Color, amount: Float) = Color(
    red + (other.red - red) * amount,
    green + (other.green - green) * amount,
    blue + (other.blue - blue) * amount,
    alpha + (other.alpha - alpha) * amount,
)

internal fun DrawScope.drawWalletText(frame: WalletFrame, geo: WalletGeo, bannerPress: Float = 0f) {
    label(geo, "Total balance", 45f, 474f, 13.3f, weight = 500, tracking = -0.2f)
    odometer(
        geo,
        listOf(
            DigitSlot(fixed = '$'), DigitSlot(from = 0, to = 6, enter = true), DigitSlot(fixed = ',', enter = true),
            DigitSlot(from = 0, to = 7, enter = true), DigitSlot(from = 0, to = 9, enter = true), DigitSlot(from = 0, to = 0, cycles = 1),
            DigitSlot(fixed = '.', grey = true), DigitSlot(from = 0, to = 2, cycles = 1, grey = true), DigitSlot(from = 0, to = 2, cycles = 1, grey = true),
        ), frame.balance, 1f, 50f, 635f, 49f, Ink, Decimals, -3.4f,
    )

    // Banner copy fades between the original explanation and the verified line.
    val bannerAlpha = 1f - frame.banner
    val bannerOffset = -30f * frame.banner
    val oldAlpha = bannerAlpha * (1f - frame.copy)
    val bannerScale = (1f - 0.03f * frame.banner) * (1f - 0.03f * bannerPress)
    withTransform({
        translate(0f, geo.x(bannerOffset))
        scale(bannerScale, bannerScale, Offset(geo.x(Banner.x + Banner.w / 2f), geo.y(Banner.y + Banner.h / 2f)))
    }) {
        label(geo, "You’ll be able to set up your debit card once", 57f, 842f, 12.7f, weight = 500, alpha = oldAlpha)
        label(geo, "your verification is completed.", 56f, 898f, 12.7f, weight = 500, alpha = oldAlpha)
        label(geo, "This usually", 609f, 898f, 12.7f, Secondary, alpha = oldAlpha)
        label(geo, "takes a few hours. Please wait…", 55f, 953f, 12.7f, Secondary, alpha = oldAlpha)
        label(geo, "Verified. Your card is ready.", 57f, 842f + 12f * (1f - frame.copy), 12.7f, weight = 500, alpha = bannerAlpha * frame.copy)
    }

    // Pill labels. The arrows share the same native path as the painted pill backgrounds.
    val receiveAlpha = minOf(1f, frame.pill0 * 2f)
    if (frame.pill0 > 0.001f) {
        val receiveCenter = Offset(geo.x(46.3f + 330.9f / 2f), geo.y(722.4f + 127.2f / 2f))
        withTransform({ scale(0.9f + 0.1f * frame.pill0, 0.9f + 0.1f * frame.pill0, receiveCenter) }) {
            drawWalletPillArrow(Offset(geo.x(125.5f), geo.y(786.5f)), geo.x(24f), downLeft = true, Color.White.copy(alpha = receiveAlpha), geo.x(6.2f))
            label(geo, "Receive", 172f, 800f, 13.3f, Color.White, 500, alpha = receiveAlpha)
        }
    }
    val sendAlpha = minOf(1f, frame.pill1 * 2f)
    if (frame.pill1 > 0.001f) {
        val sendCenter = Offset(geo.x(394.2f + 330.5f / 2f), geo.y(722.4f + 127.2f / 2f))
        withTransform({ scale(0.9f + 0.1f * frame.pill1, 0.9f + 0.1f * frame.pill1, sendCenter) }) {
            drawWalletPillArrow(Offset(geo.x(500.5f), geo.y(786.5f)), geo.x(24f), downLeft = false, Color.White.copy(alpha = sendAlpha), geo.x(6.2f))
            label(geo, "Send", 546f, 800f, 13.3f, Color.White, 500, alpha = sendAlpha)
        }
    }

    // Transaction heading and rows keep their own 50 ms stagger.
    label(geo, "Transactions", 45f, 1002f + 48f * (1f - frame.txHead), 13.3f, weight = 500, tracking = -0.2f, alpha = frame.txHead)
    label(geo, "See all", 1015f, 1000f + 48f * (1f - frame.txHead), 13.3f, PillText, weight = 500, alpha = frame.txHead)
    label(geo, "Hearth Coffee", 237f, 1150f + 48f * (1f - frame.txRow0), 16.4f, weight = 500, alpha = frame.txRow0)
    label(geo, "Purchase. Yesterday 8:10 am", 239f, 1207f + 48f * (1f - frame.txRow0), 13.3f, Secondary, tracking = -0.35f, alpha = frame.txRow0)
    rightLabel(geo, "$4.85", 1132f, 1180f + 48f * (1f - frame.txRow0), 17.4f, alpha = frame.txRow0)
    label(geo, "Dieter E.", 239f, 1369f + 48f * (1f - frame.txRow1), 16.4f, weight = 500, alpha = frame.txRow1)
    label(geo, "Received. 12 Nov 9:20 am", 239f, 1427f + 48f * (1f - frame.txRow1), 13.3f, Secondary, tracking = -0.35f, alpha = frame.txRow1)
    rightLabel(geo, "+ $109.25", 1132f, 1400f + 48f * (1f - frame.txRow1), 17.4f, Ink.mix(Green, frame.green), alpha = frame.txRow1)

    // Recent payees title moves to the funded slot while the old action copy leaves.
    val recentX = interpolate(51f, 46f, frame.reflow)
    val recentY = interpolate(1173f, 1615f, frame.reflow)
    label(geo, "Recent payees", recentX, recentY, 13.3f, weight = 500, tracking = -0.2f)
    val actionAlpha = 1f - frame.actionsOut
    label(geo, "Invite friends", 78f, 1519f - 10f * (1f - actionAlpha), 13.3f, weight = 500, tracking = -0.2f, alpha = actionAlpha)
    label(geo, "Share the app", 78f, 1573f - 10f * (1f - actionAlpha), 13.3f, Secondary, alpha = actionAlpha)
    label(geo, "Send money", 467f, 1519f - 10f * (1f - actionAlpha), 13.3f, weight = 500, tracking = -0.2f, alpha = actionAlpha)
    label(geo, "Start transfer", 467f, 1573f - 10f * (1f - actionAlpha), 13.3f, Secondary, alpha = actionAlpha)
    label(geo, "Receive funds", 855f, 1519f - 10f * (1f - actionAlpha), 13.3f, weight = 500, tracking = -0.2f, alpha = actionAlpha)
    label(geo, "Share details", 855f, 1573f - 10f * (1f - actionAlpha), 13.3f, Secondary, alpha = actionAlpha)
    val namesAlpha = frame.names
    centerLabel(geo, "Konstantin", 22.25f + 285.3f / 2f, 1901f, 13.3f, weight = 500, tracking = -0.2f, alpha = namesAlpha)
    centerLabel(geo, "Vasya", 22.25f + 291.3f + 285.3f / 2f, 1901f, 13.3f, weight = 500, tracking = -0.2f, alpha = namesAlpha)
    centerLabel(geo, "Aleksa", 22.25f + 2f * 291.3f + 285.3f / 2f, 1901f, 13.3f, weight = 500, tracking = -0.2f, alpha = namesAlpha)
    centerLabel(geo, "Joshua", 22.25f + 3f * 291.3f + 285.3f / 2f, 1901f, 13.3f, weight = 500, tracking = -0.2f, alpha = namesAlpha)
    label(geo, "See all", 1014f, 1612f, 13.3f, PillText, weight = 500, alpha = namesAlpha)

    // Analytics text follows the chart card and then glides within it as the plot grows.
    val chartTop = interpolate(1823.2f, 2147.5f, frame.reflow2)
    label(geo, "Analytics", 45f, interpolate(1773f, 2097f, frame.reflow2), 13.3f, weight = 500, tracking = -0.2f)
    label(geo, "More", 1038f, interpolate(1771f, 2096f, frame.reflow2), 13.3f, PillText, weight = 500)
    val spentY = chartTop + interpolate(88.8f, 87.5f, frame.plot)
    val valueY = chartTop + interpolate(164.8f, 156.5f, frame.plot)
    val labelY = chartTop + interpolate(256.8f, 157.5f, frame.plot)
    label(geo, "Spent this week", interpolate(98f, 91f, frame.plot), spentY, 13.8f, Secondary)
    odometer(
        geo,
        listOf(DigitSlot(fixed = '$'), DigitSlot(from = 0, to = 9, enter = true), DigitSlot(from = 0, to = 8, enter = true), DigitSlot(from = 0, to = 3)),
        frame.spentValue, 0.8f, interpolate(74f, 68f, frame.plot), valueY, 19.4f, Ink, Decimals, -1.2f,
    )
    rightLabel(geo, "$312", 1123f, labelY - 16f * frame.label, 13.1f, Label, alpha = 1f - frame.label)
    rightLabel(geo, "$1,92k", 1128f, labelY + 16f * (1f - frame.label), 10.5f, Label, alpha = frame.label)
    val axisY = chartTop + interpolate(519.8f, 470.5f, frame.plot)
    listOf("1", "2", "3", "4", "5", "6", "7").forEachIndexed { index, value -> centerLabel(geo, value, 81f + index * 173f, axisY, 11.8f, Axis) }
}
