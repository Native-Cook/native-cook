package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Copy
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Type
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The copy at the bottom left: APPEARANCE, Dark rolling into Light like a split-flap, and the hint. The text
 * recolours when the light's front passes it (or cross-fades under Reduce Motion). Drawn from the frame's
 * values in the draw phase, so it never composes again. Port of PullCordTheme.tsx's text styles.
 */
internal class PullCordThemeText(
    measurer: TextMeasurer,
    private val g: PullCordThemeGeometry,
    private val density: Float,
    bottomInset: Double,
) {
    private val eyebrow = measurer.measure(
        text = Copy.eyebrow,
        style = TextStyle(
            fontSize = Type.eyebrowSize.sp,
            letterSpacing = Type.eyebrowTracking.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        maxLines = 1,
        softWrap = false,
    )
    private val titleStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = Type.titleSize.sp,
        lineHeight = Type.titleLine.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    private val darkTitle = measurer.measure(Copy.dark, titleStyle, maxLines = 1, softWrap = false)
    private val lightTitle = measurer.measure(Copy.light, titleStyle, maxLines = 1, softWrap = false)
    private val hint = measurer.measure(
        text = Copy.hint,
        style = TextStyle(
            fontSize = Type.hintSize.sp,
            lineHeight = Type.hintLine.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        maxLines = 1,
        softWrap = false,
    )

    // The copy block sits Type.bottom above the bottom inset: hint, gap, title box, gap, eyebrow, from the bottom up.
    private val bottomEdge = g.h - bottomInset - Type.bottom
    private val hintTop = bottomEdge - Type.hintLine
    private val titleTop = hintTop - Type.gap - Type.titleLine
    private val eyebrowTop = titleTop - Type.gap - eyebrow.size.height / density

    /** The eyebrow's anchor for the recolour wave, as the React Native block computes it. */
    private val textTop = g.h - bottomInset - Type.bottom - Type.hintLine - Type.gap - Type.titleLine - Type.gap -
        Type.eyebrowSize

    /** 0 while the line is still in the dark room, 1 once the light's front has passed it. */
    private fun mixAt(y: Double, f: PullCordThemeFrame): Double {
        if (f.reduced) return f.fade
        val d = hypot(Type.margin + 40 - f.cx, y - f.cy)
        return min(1.0, max(0.0, (f.radius - d + PullCordThemeMotion.Reveal.textBand / 2) / PullCordThemeMotion.Reveal.textBand))
    }

    fun draw(scope: DrawScope, f: PullCordThemeFrame) {
        with(scope) {
            val left = (Type.margin * density).toFloat()
            val mixEyebrow = mixAt(textTop, f)
            val mixTitle = mixAt(textTop + 30, f)
            val mixHint = mixAt(textTop + 80, f)

            drawText(
                eyebrow,
                color = mix(PullCordThemeTheme.DARK.mute, PullCordThemeTheme.LIGHT.mute, mixEyebrow),
                topLeft = Offset(left, (eyebrowTop * density).toFloat()),
            )

            // Dark rolls into Light as the light's edge reaches the label; the box clips both.
            val boxTop = (titleTop * density).toFloat()
            val boxBottom = ((titleTop + Type.titleLine) * density).toFloat()
            val roll = if (f.reduced) 0.0 else Type.titleLine
            clipRect(left = 0f, top = boxTop, right = size.width, bottom = boxBottom) {
                drawTitle(darkTitle, PullCordThemeTheme.DARK.ink, 1 - mixTitle, -roll * mixTitle, left, boxTop)
                drawTitle(lightTitle, PullCordThemeTheme.LIGHT.ink, mixTitle, roll * (1 - mixTitle), left, boxTop)
            }

            drawText(
                hint,
                color = mix(PullCordThemeTheme.DARK.mute, PullCordThemeTheme.LIGHT.mute, mixHint),
                topLeft = Offset(left, (hintTop * density).toFloat()),
            )
        }
    }

    private fun DrawScope.drawTitle(
        layout: TextLayoutResult,
        ink: Color,
        opacity: Double,
        offsetDp: Double,
        left: Float,
        boxTop: Float,
    ) {
        if (opacity <= 0.0) return
        drawText(
            layout,
            color = ink.copy(alpha = ink.alpha * opacity.toFloat().coerceIn(0f, 1f)),
            topLeft = Offset(left, boxTop + (offsetDp * density).toFloat()),
        )
    }

    /** Reanimated's interpolateColor: each channel, alpha included, mixed in sRGB. */
    private fun mix(from: Color, to: Color, t: Double): Color {
        val k = t.toFloat().coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * k,
            green = from.green + (to.green - from.green) * k,
            blue = from.blue + (to.blue - from.blue) * k,
            alpha = from.alpha + (to.alpha - from.alpha) * k,
        )
    }
}
