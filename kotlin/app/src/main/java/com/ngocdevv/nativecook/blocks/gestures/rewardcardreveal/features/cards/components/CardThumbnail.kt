package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.model.GalleryItem
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CARD_HEIGHT_RATIO
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.fixedSp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Spacing

@Composable
fun CardThumbnail(
    item: GalleryItem,
    width: Dp,
    onOpenCard: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is GalleryItem.Locked -> {
            Box(
                modifier = modifier
                    .width(width)
                    .padding(bottom = Spacing.lg)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${item.lockCount} locked cards"
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                CollectionCardBack(
                    width = width,
                    markerCount = item.lockCount,
                    markerVariant = MarkerVariant.Lock,
                )
            }
        }

        is GalleryItem.Owned -> {
            val height = width * CARD_HEIGHT_RATIO
            val borderRadius = (10f * (width.value / 218f)).dp
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()

            Column(
                modifier = modifier
                    .width(width)
                    .padding(bottom = Spacing.lg)
                    .graphicsLayer { alpha = if (pressed) 0.72f else 1f }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { onOpenCard(item.cardId) },
                    )
                    .semantics {
                        contentDescription =
                            "${item.name}, ${item.count} thẻ trong bộ sưu tập. Mở màn hình chi tiết và hiệu ứng holographic"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width, height)
                        .clip(RoundedCornerShape(borderRadius)),
                ) {
                    Image(
                        painter = painterResource(item.image),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.035f
                                scaleY = 1.035f
                            },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(cssColor("rgba(255, 255, 255, 0.86)"), CircleShape)
                        .border(1.dp, cssColor("rgba(255, 255, 255, 0.96)"), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = item.count.toString(),
                        style = TextStyle(
                            color = cssColor("#8a94a1"),
                            fontFamily = FontFamily.Serif,
                            fontSize = 10f.fixedSp(),
                            lineHeight = 12f.fixedSp(),
                            fontFeatureSettings = "tnum",
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}
