package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Absolute placement in the flat app's coordinates: `position: 'absolute', left, top`. */
internal fun Modifier.at(left: Float, top: Float): Modifier = offset(left.dp, top.dp)

/** Runs [block] in dp units, the way the reference lays everything out. */
internal inline fun DrawScope.inDp(block: DrawScope.() -> Unit) {
    scale(density, density, pivot = Offset.Zero) { block() }
}

/** interpolateColor(t, [0, 1], [from, to]): straight RGB, as Reanimated does by default. */
internal fun rgbLerp(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)

private val TiltSwipeFontFamily = FontFamily(
    Font(R.font.tiltswipeactions_montserrat_medium, FontWeight.Medium),
    Font(R.font.tiltswipeactions_montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.tiltswipeactions_montserrat_bold, FontWeight.Bold),
)

/** `<Text style={TYPE.x}>`: the reference's line box, centred, without the font's own padding. */
@Composable
internal fun TiltLabel(
    text: String,
    style: TiltText,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: Float = style.fontSize,
    lineHeight: Float = style.lineHeight,
    weight: Int = style.weight,
    maxLines: Int = Int.MAX_VALUE,
    tabular: Boolean = false,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            fontWeight = FontWeight(weight),
            fontFamily = TiltSwipeFontFamily,
            letterSpacing = style.letterSpacing.sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
            textAlign = textAlign,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * The soft shadow of a view (shadowColor, shadowOpacity, shadowRadius, shadowOffset): a blurred
 * copy of the view's shape. Real blur from API 28; below that a few stacked rectangles stand in.
 * Drawn in dp units (inside [inDp] or a [com.ngocdevv.nativecook.shared.surface]) with the
 * opacity already multiplied by the view's own alpha, as CALayer does.
 */
internal class TiltShadow(color: Color, shadowRadius: Float) {
    private val sigma = shadowRadius / 2f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = 1f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val bounds = RectF()

    fun draw(scope: DrawScope, x: Float, y: Float, w: Float, h: Float, r: Float, opacity: Float) {
        if (opacity <= 0f) return
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                paint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
                native.drawRoundRect(x, y, x + w, y + h, r, r, paint)
            } else {
                val steps = 6
                paint.alpha = (opacity * 255f * 1.6f / steps).roundToInt().coerceIn(0, 255)
                for (i in 0 until steps) {
                    val grow = sigma * (1.5f - 2f * i / (steps - 1))
                    bounds.set(x - grow, y - grow, x + w + grow, y + h + grow)
                    if (bounds.width() <= 0f || bounds.height() <= 0f) continue
                    val radius = max(0f, r + grow)
                    native.drawRoundRect(bounds, radius, radius, paint)
                }
            }
        }
    }
}
