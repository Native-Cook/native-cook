package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * `NewsPage`: one page of the carousel, [width] dp wide, with its illustration and its copy. The page
 * slides by its distance from the carousel's position; its copy hands over faster than the art
 * (it is gone at a distance of 0.6 pages) and slides a fifth less than a page, so two headlines
 * never meet mid-swipe. Under Reduce Motion the pages stay in place and only the nearest one shows.
 * Every number is read in a layer block, so a frame moves the layers and recomposes nothing.
 */
@Composable
internal fun SpectrumNewsCarouselPage(
    index: Int,
    width: Float,
    drive: SpectrumNewsCarouselDrive,
    boardScale: Float,
    photo: ImageBitmap,
) {
    val text = SpectrumNewsCarouselPages[index]
    Column(
        Modifier
            .width(width.dp)
            .graphicsLayer {
                val distance = index - drive.position
                if (drive.reduced) {
                    alpha = if (SpectrumNewsCarouselMotion.jsRound(drive.position).toInt() == index) 1f else 0f
                    translationX = 0f
                } else {
                    val intro = if (index == 0) drive.welcomeIntro() else 1.0
                    alpha = (SpectrumNewsCarouselMotion.clamp(1.3 - abs(distance) * 0.3) * intro).toFloat()
                    translationX = (distance * width * this.density).toFloat()
                }
            }
            .padding(horizontal = 24.dp)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpectrumNewsCarouselIllustration(index, drive, boardScale, photo)
        // `copy`: 35 dp under the art, 10 on the welcome page.
        Box(
            Modifier
                .padding(top = if (index == 0) 10.dp else 35.dp)
                .graphicsLayer {
                    if (drive.reduced) {
                        alpha = 1f
                        translationX = 0f
                    } else {
                        alpha = SpectrumNewsCarouselMotion.clamp(1.0 - abs(index - drive.position) * 1.7).toFloat()
                        translationX = ((index - drive.position) * -width * 0.18 * this.density).toFloat()
                    }
                },
        ) {
            if (index == 0) {
                SpectrumNewsCarouselBodyText(
                    text = text.body,
                    size = 18f,
                    lineHeight = 23f,
                    color = Color(0xFF7A7A7A),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpectrumNewsCarouselBodyText(
                        text = text.title,
                        size = 16f,
                        lineHeight = 21f,
                        color = Color(0xFF111111),
                        weight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    SpectrumNewsCarouselBodyText(
                        text = text.body,
                        size = 15f,
                        lineHeight = 19.5f,
                        color = Color(0xFF6B6B6B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
