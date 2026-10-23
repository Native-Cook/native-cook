package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Sixteen steps per dp cover the lens's maximum seven-dp horizontal blur.
private const val RAIL_BLUR_STEPS = 112

/** Blur layers sit outside the canvas's blend layers; the clip is applied after the filter. */
@Composable
internal fun ReferralRail(
    state: ReferralState,
    painter: ReferralRailPainter,
    width: Float,
    density: Float,
) {
    val effects =
        remember(density) {
            if (Build.VERSION.SDK_INT >= 31) {
                Array(RAIL_BLUR_STEPS + 1) { i ->
                    if (i == 0) {
                        null
                    } else {
                        android.graphics.RenderEffect
                            .createBlurEffect(
                                i / 16f * density,
                                0f,
                                Shader.TileMode.DECAL,
                            ).asComposeRenderEffect()
                    }
                }
            } else {
                null
            }
        }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            painter.background(canvas, state, width)
            canvas.restore()
        }
    }
    Box(
        Modifier.fillMaxSize().drawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            painter.clipRow(canvas, state, width)
            canvas.scale(1 / density, 1 / density)
            drawContent()
            canvas.restore()
        },
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect =
                    effects?.get((min(state.rush * 0.43, 5.0) * 16).roundToInt().coerceIn(0, RAIL_BLUR_STEPS))
            },
        ) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.outside(canvas, state, width)
                canvas.restore()
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale =
                    ((1 + 0.05 * state.pop.value) * (0.9 + 0.1 * state.railArrival)).toFloat()
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, Size.cy.toFloat() / Size.railHeight)
                alpha = max(state.railArrival, if (state.warm > 0) 0.012 else 0.0).toFloat()
            }.drawWithContent {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.clipLens(canvas, state, width)
                canvas.scale(1 / density, 1 / density)
                drawContent()
                canvas.restore()
            },
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect =
                    effects?.get((min(state.rush * 0.6, 7.0) * 16).roundToInt().coerceIn(0, RAIL_BLUR_STEPS))
            },
        ) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                painter.lens(canvas, state, width)
                canvas.restore()
            }
        }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            painter.foreground(canvas, state, width)
            canvas.restore()
        }
    }
}
