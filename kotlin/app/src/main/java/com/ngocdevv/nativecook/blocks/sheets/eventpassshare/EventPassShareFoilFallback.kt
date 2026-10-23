package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas

/** Static ink-only spectral title used when API 33 AGSL is unavailable. */
@Composable
internal fun EventPassShareFoilFallback(
    runtime: EventPassShareRuntime,
    pixelScale: Float,
    modifier: Modifier = Modifier,
) {
    val foil = remember { FloatArray(4) }
    val titlePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val titlePath = remember { Path() }
    val token = EventPassShareTheme.foilFallback
    val titleGradient = remember(pixelScale) {
        LinearGradient(
            token.angle.x1 * 251f * pixelScale,
            token.angle.y1 * 251f * pixelScale,
            token.angle.x2 * 251f * pixelScale,
            token.angle.y2 * 251f * pixelScale,
            token.stops,
            null,
            Shader.TileMode.CLAMP,
        )
    }

    Canvas(modifier) {
        runtime.drawRevision.intValue
        val frame = runtime.frame.get()
        val turn = if (frame.quietCrossfade) 0.0 else frame.flipAngle
        eventPassShareFoilUniform(
            tiltX = frame.tiltX,
            tiltY = frame.tiltY,
            flipAngle = turn,
            scale = pixelScale.toDouble(),
            output = foil,
        )
        val amount = foil[2]
        if (amount <= 0.002f) return@Canvas

        titlePaint.reset()
        titlePaint.isAntiAlias = true
        titlePaint.style = Paint.Style.FILL
        titlePaint.typeface = Typeface.DEFAULT
        titlePaint.textSize = 22f * pixelScale
        titlePaint.color = 0xFFFFFFFF.toInt()
        titlePaint.alpha = 255
        titlePaint.shader = titleGradient
        titlePaint.alpha = (amount * token.opacity * 255f).toInt().coerceIn(0, 255)

        titlePath.reset()
        addTrackedTextPath(
            paint = titlePaint,
            text = "PAWS, PEOPLE &",
            x = 38f * pixelScale,
            baseline = 70f * pixelScale,
            tracking = -0.7f * pixelScale,
            output = titlePath,
        )
        addTrackedTextPath(
            paint = titlePaint,
            text = "PURPOSE",
            x = 38f * pixelScale,
            baseline = 91f * pixelScale,
            tracking = -0.7f * pixelScale,
            output = titlePath,
        )
        drawContext.canvas.nativeCanvas.drawPath(titlePath, titlePaint)
    }
}

private fun addTrackedTextPath(
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    tracking: Float,
    output: Path,
) {
    var cursor = x
    text.indices.forEach { index ->
        paint.getTextPath(text, index, index + 1, cursor, baseline, output)
        cursor += paint.measureText(text, index, index + 1) + tracking
    }
}
