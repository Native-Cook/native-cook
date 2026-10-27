package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * `Cover`: Small Rituals is typographic, set in code; A Brief Tour of Almost Everything is an illustrated
 * plate with its title set in code. [large] is the 190 x 268 cover of the detail screen, otherwise the
 * 74 x 105 one of a library row. The type on a cover never follows the system font scale.
 */
@Composable
internal fun FolioCover(
    book: FolioBookId,
    large: Boolean,
    assets: FolioFeatureLibraryAssets,
    modifier: Modifier = Modifier,
) {
    val width = if (large) 190f else 74f
    val height = if (large) 268f else 105f
    val radius = if (large) 8f else 4f
    var frame = modifier.size(width.dp, height.dp)
    if (large) frame = frame.folioShadow(FolioShadowCoverDetail) { radius }
    frame = frame.clip(RoundedCornerShape(radius.dp))
    if (book == FolioBookId.Noticing) {
        FolioTourCover(large, assets, frame)
    } else {
        FolioRitualsCover(large, frame)
    }
}

@Composable
private fun FolioTourCover(large: Boolean, assets: FolioFeatureLibraryAssets, modifier: Modifier) {
    Box(modifier) {
        Image(
            bitmap = assets.everything,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolioText(
                text = "THEO MARSH",
                size = if (large) 13f else 6f,
                color = Color(0xFFF2C94C),
                weight = FontWeight.ExtraBold,
                letterSpacing = 0.6f,
                scaling = false,
            )
            FolioText(
                text = "A BRIEF TOUR OF\nALMOST\nEVERYTHING",
                size = if (large) 22f else 8.5f,
                lineHeight = if (large) 24f else 9.5f,
                color = Color(0xFFF5E9C8),
                weight = FontWeight.Black,
                align = TextAlign.Center,
                family = FolioSerif,
                modifier = Modifier.padding(top = 2.dp),
                scaling = false,
            )
        }
    }
}

@Composable
private fun FolioRitualsCover(large: Boolean, modifier: Modifier) {
    Box(modifier.background(Color(0xFF161616)).padding(3.dp)) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF4EEDF))) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FolioText(
                    text = "Small Steps,\nLasting Change",
                    size = if (large) 11f else 5f,
                    lineHeight = if (large) 14f else 6.5f,
                    color = Color(0xFF2A2A2A),
                    weight = FontWeight.Bold,
                    align = TextAlign.Center,
                    scaling = false,
                )
                FolioText(
                    text = "Small\nRituals",
                    size = if (large) 44f else 17f,
                    lineHeight = if (large) 44f else 17f,
                    color = Color(0xFFD18A55),
                    family = FolioSerif,
                    align = TextAlign.Center,
                    letterSpacing = -0.5f,
                    scaling = false,
                )
                FolioText(
                    text = "A gentle way to build good days",
                    size = if (large) 7f else 3.5f,
                    color = Color(0xFF3A3A3A),
                    align = TextAlign.Center,
                    scaling = false,
                )
                FolioText(
                    text = "Mira Wells",
                    size = if (large) 15f else 7f,
                    color = FolioInkBar,
                    weight = FontWeight.Bold,
                    letterSpacing = 0.3f,
                    scaling = false,
                )
            }
            if (large) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF7D27A))
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3B53F)),
                    contentAlignment = Alignment.Center,
                ) {
                    FolioText(
                        text = "NEW\nSAMPLE\nREAD",
                        size = 6.5f,
                        lineHeight = 7.5f,
                        color = Color(0xFF3A2A10),
                        weight = FontWeight.ExtraBold,
                        align = TextAlign.Center,
                        scaling = false,
                    )
                }
            }
        }
    }
}
