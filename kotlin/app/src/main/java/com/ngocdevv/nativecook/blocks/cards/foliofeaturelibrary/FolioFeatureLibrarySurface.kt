package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * `appSurface`: the library, the loading screen and the detail screen on one pale surface that opens from the
 * card that was tapped. It grows from that card's frame to the whole screen (`appStyle`) while its content
 * fades in (`appContentStyle`), and shrinks back to the same frame on close.
 */
@Composable
internal fun FolioAppSurface(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val interactive = model.screen != FolioScreen.Explore && !model.topicsOpen && model.notice == null
    FolioHitGate(interactive = interactive, hidden = !interactive) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = model.appProgress.value(clock.frame.doubleValue)
                    val frame = model.origin
                    if (model.reduced) {
                        alpha = p
                    } else {
                        alpha = folioInterpolate(p, floatArrayOf(0f, 0.025f, 1f), floatArrayOf(0f, 1f, 1f))
                        translationX = (frame.x + frame.width / 2f - metrics.width / 2f) * (1f - p) * unit
                        translationY = (frame.y + frame.height / 2f - metrics.height / 2f) * (1f - p) * unit
                        scaleX = frame.width / metrics.width + (1f - frame.width / metrics.width) * p
                        scaleY = frame.height / metrics.height + (1f - frame.height / metrics.height) * p
                    }
                    clip = true
                }
                .background(FolioSurfaceBg),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = model.appProgress.value(clock.frame.doubleValue)
                        alpha = if (model.reduced) 1f else folioInterpolate(p, floatArrayOf(0.35f, 0.75f), floatArrayOf(0f, 1f))
                    },
            ) {
                FolioLibrary(model, assets, host)
                if (model.screen == FolioScreen.Loading) FolioLoader(model, assets)
                FolioDetail(model, assets, host)
            }
        }
    }
}

/**
 * The card that opens: a copy of the tapped feature card drawn over the surface at the frame it was measured
 * at. As the surface grows it drifts to the middle of the screen, scales up by 35% and fades out by 60% of
 * the way (`cardBridgeStyle`). It takes no touches.
 */
@Composable
internal fun FolioCardBridge(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val frame = model.origin
    val feature = FolioFeatures[model.feature]
    Box(
        Modifier
            .offset { IntOffset((frame.x * unit).roundToInt(), (frame.y * unit).roundToInt()) }
            .size(frame.width.dp, frame.height.dp)
            .graphicsLayer {
                val p = model.appProgress.value(clock.frame.doubleValue)
                val now = model.origin
                alpha = if (model.reduced) {
                    0f
                } else {
                    folioInterpolate(
                        p,
                        floatArrayOf(0f, 0.025f, 0.18f, 0.6f, 1f),
                        floatArrayOf(0f, 1f, 1f, 0f, 0f),
                    )
                }
                translationX = (metrics.width / 2f - now.x - now.width / 2f) * p * unit
                translationY = (metrics.height / 2f - now.y - now.height / 2f) * p * unit
                scaleX = 1f + p * 0.35f
                scaleY = 1f + p * 0.35f
            }
            .folioShadow(FolioCardShadow) { 28f }
            .clip(RoundedCornerShape(28.dp))
            .background(FolioWhite),
    ) {
        FolioFeatureCardContent(feature, assets)
    }
}
