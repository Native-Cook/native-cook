package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform

/** Star sizes of the three hero stars (`Star size=` in React Native): small, main, right. */
private const val SMALL_STAR = 46f
private const val MAIN_STAR = 138f
private const val RIGHT_STAR = 80f

/**
 * The hero: an orange ring halo and three four-point stars in a box that opens from the top
 * (`styles.hero` + `Star`), laid out in a [full]-high slot and clipped to the opening height `full * heroProgress`.
 * The small star sits at 23% / 25% of the opening box, the right one at 17% from the right / 47%, the main one in its
 * middle, 3 dp up; the halo is centred. All of it is drawn in dp units from loop time, with no recomposition.
 */
@Composable
internal fun EmberDayReviewHero(
    controller: EmberDayReviewController,
    metrics: EmberDayReviewMetrics,
    star: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val haloBrush = remember {
        val ring = EmberDayReviewPalette.Halo
        Brush.radialGradient(
            0.5f to ring.copy(alpha = 0f),
            0.73f to ring.copy(alpha = 0.5f),
            1f to ring.copy(alpha = 0f),
            center = Offset.Zero,
            radius = 150f,
        )
    }
    val reduced = metrics.reduced
    Canvas(modifier.fillMaxWidth().height(metrics.heroHeight)) {
        val t = controller.time.floatValue
        val heroDp = metrics.heroHeight.value * (if (reduced) 1f else emberHeroProgress(t))
        if (heroDp <= 0f) {
            return@Canvas
        }
        val widthDp = size.width / density
        clipRect(left = 0f, top = 0f, right = size.width, bottom = heroDp * density) {
            scale(density, density, pivot = Offset.Zero) {
                val haloOpacity = if (reduced) 0f else emberHaloOpacity(t)
                if (haloOpacity > 0f) {
                    val haloScale = emberHaloScale(t)
                    withTransform({
                        translate(widthDp / 2f, heroDp / 2f)
                        scale(haloScale, haloScale, Offset.Zero)
                    }) {
                        drawCircle(haloBrush, radius = 150f, center = Offset.Zero, alpha = haloOpacity)
                    }
                }
                if (star != null) {
                    drawStar(star, widthDp * 0.23f, heroDp * 0.25f, SMALL_STAR, emberStarPose(t, 0, reduced))
                    drawStar(star, (widthDp - MAIN_STAR) / 2f, (heroDp - MAIN_STAR) / 2f - 3f, MAIN_STAR, emberStarPose(t, 1, reduced))
                    drawStar(star, widthDp - widthDp * 0.17f - RIGHT_STAR, heroDp * 0.47f, RIGHT_STAR, emberStarPose(t, 2, reduced))
                }
            }
        }
    }
}

/**
 * One `Star`: a [size] square at ([x], [y]) holding the artwork stretched to 129% x 106% of it
 * (left -14.5%, top -3%). The pose is applied as React Native orders it: rotateZ, then scaleX, then scale, then
 * translateY, all about the square's centre.
 */
private fun DrawScope.drawStar(image: ImageBitmap, x: Float, y: Float, size: Float, pose: EmberDayReviewStarPose) {
    if (pose.opacity <= 0f) {
        return
    }
    val half = size / 2f
    val center = Offset(half, half)
    withTransform({
        translate(x, y)
        rotate(pose.rollDegrees, center)
        scale(pose.scaleX, 1f, center)
        scale(pose.emerge, pose.emerge, center)
        translate(0f, pose.riseDp)
        translate(-size * 0.145f, -size * 0.03f)
        scale(size * 1.29f / image.width, size * 1.06f / image.height, Offset.Zero)
    }) {
        drawImage(image, Offset.Zero, alpha = pose.opacity)
    }
}
