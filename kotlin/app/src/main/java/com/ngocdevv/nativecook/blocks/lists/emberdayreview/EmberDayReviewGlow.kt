package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val GLOW_SIZE = 460f

/**
 * The peach glow behind the hero (`styles.glow` with the `ember-glow` radial gradient): a 460 dp disc whose centre
 * sits 131 dp (100 dp when compact) below the title row. It grows from 60% as the hero opens, slides away with the page
 * and fades while the Review page is down (`glowStyle`).
 */
@Composable
internal fun EmberDayReviewGlow(controller: EmberDayReviewController, metrics: EmberDayReviewMetrics) {
    val brush = remember {
        Brush.radialGradient(
            0f to EmberDayReviewPalette.GlowCore.copy(alpha = 0.95f),
            0.55f to EmberDayReviewPalette.GlowMid.copy(alpha = 0.7f),
            1f to EmberDayReviewPalette.GlowEdge.copy(alpha = 0f),
            center = Offset(GLOW_SIZE / 2f, GLOW_SIZE / 2f),
            radius = GLOW_SIZE / 2f,
        )
    }
    val reduced = metrics.reduced
    val left = metrics.width.value / 2f - GLOW_SIZE / 2f
    val top = metrics.insetTop.value + 52f + (if (metrics.compact) 100f else 131f) - GLOW_SIZE / 2f
    val widthPx = with(LocalDensity.current) { metrics.width.toPx() }
    Canvas(
        Modifier
            .offset(left.dp, top.dp)
            .requiredSize(GLOW_SIZE.dp)
            .graphicsLayer {
                val t = controller.time.floatValue
                val page = controller.pageProgress()
                val review = controller.reviewProgress()
                val timeOpacity = if (reduced) 1f else emberGlowOpacity(t)
                alpha = (timeOpacity * (1f - page) * (1f - review)).coerceIn(0f, 1f)
                translationX = if (reduced) 0f else -page * widthPx
                val grow = if (reduced) 1f else emberGlowScale(t)
                scaleX = grow
                scaleY = grow
            },
    ) {
        scale(density, density, pivot = Offset.Zero) {
            drawCircle(brush, radius = GLOW_SIZE / 2f, center = Offset(GLOW_SIZE / 2f, GLOW_SIZE / 2f))
        }
    }
}
