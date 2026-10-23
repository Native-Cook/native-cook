package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max

/** Physical-pixel rectangles shared by the foreground buttons and the lens texture. */
internal data class GlassDiscOnboardingButtonRects(val light: RectF, val dark: RectF)

internal fun glassDiscOnboardingButtonRects(width: Float, height: Float, density: Float): GlassDiscOnboardingButtonRects {
    val layout = GlassDiscOnboardingTheme.LAYOUT
    val gutter = layout.gutter * density
    val darkHeight = layout.buttonHeightDark * density
    val lightHeight = layout.buttonHeight * density
    val darkTop = height - layout.buttonsBottom * density - darkHeight
    val lightTop = darkTop - layout.buttonGap * density - lightHeight
    return GlassDiscOnboardingButtonRects(
        light = RectF(gutter, lightTop, width - gutter, lightTop + lightHeight),
        dark = RectF(gutter, darkTop, width - gutter, darkTop + darkHeight),
    )
}

/** Handles the local material response; the CTAs themselves deliberately do not navigate. */
internal class GlassDiscOnboardingCtaState {
    var lensX: Float = 0f
    private var startValue: Float = 0f
    private var targetValue: Float = 0f
    private var transitionAt: Long = 0L
    private var transitionDuration: Int = 1

    fun down(x: Float, now: Long) {
        lensX = x
        transition(start = amount(now), target = 1f, at = now, duration = GlassDiscOnboardingMotion.UPGRADE.pressIn)
    }

    fun up(now: Long) {
        transition(start = amount(now), target = 0f, at = now, duration = GlassDiscOnboardingMotion.UPGRADE.pressOut)
    }

    fun amount(now: Long): Float {
        val progress = ((now - transitionAt).toFloat() / transitionDuration).coerceIn(0f, 1f)
        return startValue + (targetValue - startValue) * cubicEaseOut(progress)
    }

    private fun transition(start: Float, target: Float, at: Long, duration: Int) {
        startValue = start
        targetValue = target
        transitionAt = at
        transitionDuration = max(1, duration)
    }
}

internal fun drawGlassDiscOnboardingCtas(canvas: Canvas, buttons: GlassDiscOnboardingButtonRects, density: Float, alpha: Float, states: List<GlassDiscOnboardingCtaState>, now: Long, offsetY: Float) {
    if (alpha <= 0.005f) return
    canvas.save()
    canvas.translate(0f, offsetY)
    val light = states[0]
    val dark = states[1]
    drawGlassDiscOnboardingCta(canvas, buttons.light, false, GlassDiscOnboardingTheme.COPY.email, density, alpha, light.amount(now), light.lensX)
    drawGlassDiscOnboardingCta(canvas, buttons.dark, true, GlassDiscOnboardingTheme.COPY.phone, density, alpha, dark.amount(now), dark.lensX)
    canvas.restore()
}

private fun drawGlassDiscOnboardingCta(canvas: Canvas, rect: RectF, dark: Boolean, label: String, density: Float, alpha: Float, amount: Float, lensX: Float) {
    val centerX = rect.centerX()
    val centerY = rect.centerY()
    val scale = 1f - (1f - GlassDiscOnboardingMotion.UPGRADE.pressScale) * amount
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.save()
    canvas.scale(scale, scale, centerX, centerY)
    if (!dark) {
        val shadowAlpha = (GlassDiscOnboardingTokens.VIEW.buttonShadowOpacity * 255f).toInt() shl 24
        val shadowColor = (GlassDiscOnboardingTheme.COLOR.buttonLightShadow.toArgb() and 0x00FFFFFF) or shadowAlpha
        paint.setShadowLayer(
            GlassDiscOnboardingTokens.VIEW.buttonShadowRadius * density,
            0f,
            GlassDiscOnboardingTokens.VIEW.buttonShadowOffsetY * density,
            shadowColor,
        )
    }
    paint.color = (if (dark) GlassDiscOnboardingTheme.COLOR.buttonDark else GlassDiscOnboardingTheme.COLOR.buttonLight).toArgb()
    paint.alpha = (alpha * 255).toInt()
    canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
    paint.clearShadowLayer()
    if (!dark) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = GlassDiscOnboardingTheme.COLOR.buttonLightEdge.toArgb()
        paint.alpha = (alpha * .10f * 255).toInt()
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        paint.style = Paint.Style.FILL
        paint.color = GlassDiscOnboardingTheme.COLOR.buttonDarkInk.toArgb()
        paint.alpha = (alpha * GlassDiscOnboardingTokens.VIEW.buttonTopRimAlpha * 255f).toInt()
        canvas.drawRoundRect(
            RectF(
                rect.left + GlassDiscOnboardingTheme.SPACE.xl * density,
                rect.top,
                rect.right - GlassDiscOnboardingTheme.SPACE.xl * density,
                rect.top + GlassDiscOnboardingTokens.VIEW.buttonTopRimHairlines,
            ),
            1f,
            1f,
            paint,
        )
    }
    if (amount > 0.005f) {
        val x = if (lensX == 0f) centerX else lensX
        val lensHalfWidth = GlassDiscOnboardingTokens.VIEW.buttonLensWidth * density / 2f
        val lensAlpha = if (dark) {
            GlassDiscOnboardingTokens.VIEW.buttonLensDarkAlpha
        } else {
            GlassDiscOnboardingTokens.VIEW.buttonLensLightAlpha
        }
        val lensCenterColor = ((lensAlpha * 255f).toInt() shl 24) or 0x00FFFFFF
        paint.shader = LinearGradient(
            x - lensHalfWidth,
            rect.centerY(),
            x + lensHalfWidth,
            rect.centerY(),
            intArrayOf(0x00FFFFFF, lensCenterColor, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = (alpha * amount * 255).toInt()
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        paint.shader = null
    }
    paint.color = (if (dark) GlassDiscOnboardingTheme.COLOR.buttonDarkInk else GlassDiscOnboardingTheme.COLOR.buttonLightInk).toArgb()
    paint.alpha = (alpha * 255).toInt()
    paint.textSize = GlassDiscOnboardingTheme.TYPE.button.fontSize * density
    paint.letterSpacing = GlassDiscOnboardingTheme.TYPE.button.letterSpacing / GlassDiscOnboardingTheme.TYPE.button.fontSize
    val buttonWeight = if (dark) {
        GlassDiscOnboardingTokens.VIEW.buttonDarkWeight
    } else {
        GlassDiscOnboardingTokens.VIEW.buttonLightWeight
    }
    paint.typeface = weightedTypeface(buttonWeight)
    canvas.drawText(label, centerX - paint.measureText(label) / 2f, centerY - (paint.ascent() + paint.descent()) / 2f, paint)
    drawGlassDiscOnboardingCtaIcon(canvas, rect, dark, paint, density)
    canvas.restore()
}

private fun drawGlassDiscOnboardingCtaIcon(canvas: Canvas, rect: RectF, dark: Boolean, paint: Paint, density: Float) {
    val left = rect.left + GlassDiscOnboardingTheme.LAYOUT.buttonIconInset * density
    val size = GlassDiscOnboardingTheme.LAYOUT.buttonIcon * density
    paint.style = Paint.Style.FILL
    if (dark) {
        val path = Path().apply {
            moveTo(left + size * .30f, rect.centerY() - size * .32f)
            cubicTo(left + size * .05f, rect.centerY() - size * .06f, left + size * .15f, rect.centerY() + size * .25f, left + size * .39f, rect.centerY() + size * .34f)
            lineTo(left + size * .52f, rect.centerY() + size * .15f)
            lineTo(left + size * .43f, rect.centerY() + size * .06f)
            lineTo(left + size * .59f, rect.centerY() - size * .1f)
            lineTo(left + size * .69f, rect.centerY())
            lineTo(left + size * .88f, rect.centerY() - size * .14f)
            cubicTo(left + size * .96f, rect.centerY() + size * .1f, left + size * .62f, rect.centerY() + size * .55f, left + size * .36f, rect.centerY() + size * .45f)
        }
        canvas.drawPath(path, paint)
    } else {
        val box = RectF(left, rect.centerY() - size * .34f, left + size, rect.centerY() + size * .34f)
        canvas.drawRoundRect(box, 2f * density, 2f * density, paint)
        paint.color = (if (dark) GlassDiscOnboardingTheme.COLOR.buttonDark else GlassDiscOnboardingTheme.COLOR.buttonLight).toArgb()
        val flap = Path().apply {
            moveTo(box.left + 1f * density, box.top + 1f * density)
            lineTo(box.centerX(), box.centerY() + 1f * density)
            lineTo(box.right - density, box.top + density)
            lineTo(box.right - density, box.top + 3f * density)
            lineTo(box.centerX(), box.centerY() + 4f * density)
            lineTo(box.left + density, box.top + 3f * density)
            close()
        }
        canvas.drawPath(flap, paint)
    }
    paint.style = Paint.Style.FILL
}

private fun cubicEaseOut(progress: Float): Float {
    var u = progress
    repeat(5) {
        val x = cubic(u, .23f, .32f) - progress
        val dx = cubicDerivative(u, .23f, .32f)
        if (dx > .001f) u = (u - x / dx).coerceIn(0f, 1f)
    }
    return cubic(u, 1f, 1f)
}

private fun weightedTypeface(weight: Int): Typeface {
    val base = Typeface.create("sans-serif", Typeface.NORMAL)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return Typeface.create(base, weight, false)
    }
    val family = if (weight > GlassDiscOnboardingTokens.VIEW.buttonLightWeight) {
        "sans-serif-medium"
    } else {
        "sans-serif"
    }
    return Typeface.create(family, Typeface.NORMAL)
}

private fun cubic(t: Float, p1: Float, p2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * p1 + 3f * inverse * t * t * p2 + t * t * t
}

private fun cubicDerivative(t: Float, p1: Float, p2: Float): Float =
    3f * (1f - t) * (1f - t) * p1 + 6f * (1f - t) * t * (p2 - p1) + 3f * t * t * (1f - p2)
