package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The loading screen runs on `progress * 2.75` seconds, as the autoplay opening does (2.75 s). */
private const val LOADER_SECONDS = 2.75f

/**
 * `OpeningCollection`: a light screen with a glass lens. A neutral empty lens turns green at 1.4 s, then the
 * book resolves out of it (1.4667 to 2 s) and the title appears. Under Reduce Motion it shows the finished
 * state. The back button cancels and returns to Explore.
 */
@Composable
internal fun FolioLoader(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val fontScale = LocalDensity.current.fontScale
    val feature = FolioFeatures[model.feature]
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFE9E9E9)))),
    ) {
        FolioPressable(
            onClick = { model.closeLibrary() },
            scale = 1f,
            label = "Cancel opening and return to Explore",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = (metrics.top + 8f).dp)
                .widthIn(min = 48.dp)
                .heightIn(min = 48.dp),
        ) {
            Box(Modifier.align(Alignment.Center)) { FolioIconView(FolioIcon.ChevronBack, 21f, Color(0xFF363C34)) }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = (if (fontScale > 1.4f) metrics.top + 64f else metrics.height * 0.36f).dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(148.dp)) {
                Image(
                    bitmap = assets.lensNeutral,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.65f * shellOpacity(model, clock.frame.doubleValue) },
                    contentScale = ContentScale.Fit,
                )
                Image(
                    bitmap = assets.lensGreen,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = greenOpacity(model, clock.frame.doubleValue) },
                    contentScale = ContentScale.Fit,
                )
                Image(
                    bitmap = assets.book,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = bookAlpha(model, clock.frame.doubleValue) },
                    contentScale = ContentScale.Fit,
                )
            }
            FolioText(
                text = feature.name,
                size = 19f,
                color = FolioInk,
                weight = FontWeight.SemiBold,
                letterSpacing = -0.3f,
                align = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .graphicsLayer { alpha = if (seconds(model, clock.frame.doubleValue) >= 1.4f || model.reduced) 1f else 0f },
            )
            FolioText(
                text = "Loading…",
                size = 13f,
                color = FolioMuted,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun seconds(model: FolioFeatureLibraryModel, now: Double): Float = model.loaderProgress(now) * LOADER_SECONDS

/** `bookAlpha`: the book resolves between 1.4667 s and 2 s. */
private fun bookAlpha(model: FolioFeatureLibraryModel, now: Double): Float {
    if (model.reduced) return 1f
    return folioInterpolate(
        seconds(model, now),
        floatArrayOf(1.4667f, 1.5f, 1.6f, 1.75f, 2f),
        floatArrayOf(0f, 0.1f, 0.45f, 0.85f, 1f),
    )
}

/** The neutral lens is shown until 1.4 s. */
private fun shellOpacity(model: FolioFeatureLibraryModel, now: Double): Float =
    if (model.reduced || seconds(model, now) >= 1.4f) 0f else 1f

/** The green lens takes over at 1.4 s and gives way to the book. */
private fun greenOpacity(model: FolioFeatureLibraryModel, now: Double): Float =
    if (model.reduced || seconds(model, now) < 1.4f) 0f else 1f - bookAlpha(model, now)
