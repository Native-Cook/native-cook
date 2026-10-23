package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components.CardThumbnail
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.data.galleryItems
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CARD_HEIGHT_RATIO
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CollectionGalleryColors
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.fixedSp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Radii
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Spacing
import kotlin.math.min

private val COLLECTION_BUTTON_HEIGHT = 42.dp
private val MOBILE_COLUMN_GAP = 10.dp
private val SHEET_RADIUS = 32.dp
val BACKDROP_COLORS = listOf("#d69aa8", "#e4b7bd", "#d8a6ae").map(::cssColor)
private val FOOTER_FADE_STOPS = arrayOf(
    0f to cssColor("rgba(233, 237, 243, 0)"),
    0.46f to cssColor("rgba(233, 237, 243, 0.72)"),
    1f to cssColor("rgba(233, 237, 243, 0.97)"),
)

/** Pink diagonal backdrop shared by both screens (`start (0,0) → end (1, 0.8)`). */
@Composable
fun SheetBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = BACKDROP_COLORS,
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset(widthPx, heightPx * 0.8f),
                    ),
                ),
        )
    }
}

@Composable
fun CardGalleryScreen(onOpenCard: (cardId: String) -> Unit) {
    val galleryLayer = rememberGraphicsLayer()
    var footerTopPx by remember { mutableFloatStateOf(0f) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKDROP_COLORS[0]),
    ) {
        val width = maxWidth
        val horizontalPadding = Spacing.lg
        val columnGap = if (width >= 720.dp) Spacing.lg else MOBILE_COLUMN_GAP
        val cardWidth = min(((width - horizontalPadding * 2 - columnGap) / 2).value, 218f).dp
        val cardHeight = cardWidth * CARD_HEIGHT_RATIO
        val gridWidth = cardWidth * 2 + columnGap
        val sheetTop = if (topInset + Spacing.xs > Spacing.xl) topInset + Spacing.xs else Spacing.xl

        SheetBackdrop()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sheetTop)
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS))
                .background(CollectionGalleryColors.background),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .drawWithContent {
                        galleryLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(galleryLayer)
                    },
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = Spacing.huge,
                    bottom = COLLECTION_BUTTON_HEIGHT + Spacing.huge,
                ),
                horizontalArrangement = Arrangement.spacedBy(columnGap, Alignment.CenterHorizontally),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = "Achievements and\nrecord of a growing mind.",
                            style = TextStyle(
                                color = CollectionGalleryColors.text,
                                fontFamily = FontFamily.Serif,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = (-1.1f).sp,
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.widthIn(max = 450.dp).fillMaxWidth(),
                        )
                    }
                }

                items(galleryItems, key = { it.id }) { item ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        CardThumbnail(item = item, width = cardWidth, onOpenCard = onOpenCard)
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight * 0.58f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Row(
                            modifier = Modifier
                                .width(gridWidth)
                                .clipToBounds()
                                .graphicsLayer { alpha = 0.45f },
                            horizontalArrangement = Arrangement.spacedBy(columnGap),
                        ) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .size(cardWidth, cardHeight)
                                        .background(
                                            cssColor("rgba(255, 255, 255, 0.56)"),
                                            RoundedCornerShape((10f * (cardWidth.value / 218f)).dp),
                                        )
                                        .border(
                                            1.dp,
                                            cssColor("rgba(255, 255, 255, 0.84)"),
                                            RoundedCornerShape((10f * (cardWidth.value / 218f)).dp),
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.sm)
                    .size(34.dp, 5.dp)
                    .background(cssColor("rgba(103, 113, 124, 0.58)"), RoundedCornerShape(Radii.pill)),
            )

            // Blur the same recorded list drawing under the footer, preserving its scroll position.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { footerTopPx = it.positionInParent().y },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = Spacing.sm)
                        .clipToBounds()
                        .blur(5.dp)
                        .graphicsLayer { alpha = 0.72f },
                ) {
                    translate(top = -footerTopPx - Spacing.sm.toPx()) {
                        drawLayer(galleryLayer)
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(colorStops = FOOTER_FADE_STOPS)),
                )
                Box(
                    modifier = Modifier.padding(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = Spacing.huge,
                        bottom = if (bottomInset > Spacing.lg) bottomInset else Spacing.lg,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(min((gridWidth * 0.63f).value, 284f).dp)
                            .height(COLLECTION_BUTTON_HEIGHT)
                            .background(cssColor("rgba(238, 242, 247, 0.7)"), RoundedCornerShape(Radii.pill))
                            .border(1.dp, cssColor("rgba(69, 79, 92, 0.44)"), RoundedCornerShape(Radii.pill)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = "My Collection",
                            style = TextStyle(
                                color = CollectionGalleryColors.text,
                                fontSize = 15f.fixedSp(),
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CardGalleryScreenPreview() {
    CardGalleryScreen(onOpenCard = {})
}
