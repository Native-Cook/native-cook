package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The notice (`Modal` with `animationType="fade"`): a card over a 30% black backdrop with the feature's art,
 * a title, the message and a Got it button. The card is as tall as its text, at most the screen minus the
 * top inset and 48 dp.
 */
@Composable
internal fun FolioNotice(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val open = model.notice != null
    val scroll = rememberScrollState()
    FolioHitGate(interactive = open, hidden = !open) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = model.noticeFade.value(clock.frame.doubleValue) }
                .background(Color(0x4D000000))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (metrics.height - metrics.top - 48f).dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(FolioWhite)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FolioProgressiveBlurViewport(
                    enabled = open,
                    overflowing = { scroll.maxValue > 4f * unit },
                    backgroundColor = FolioWhite,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            bitmap = assets.art(FolioFeatures[model.feature].art),
                            contentDescription = null,
                            modifier = Modifier.size(116.dp),
                            contentScale = ContentScale.Fit,
                        )
                        FolioText(
                            text = "Made for a little curiosity.",
                            size = 20f,
                            color = FolioInk,
                            weight = FontWeight.SemiBold,
                            align = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        FolioParagraph(model.lastNotice, Modifier.fillMaxWidth())
                    }
                }
                FolioButton(
                    label = "Got it",
                    onClick = { model.notice = null },
                    reduced = model.reduced,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
