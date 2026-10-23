package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The opaque browse bar cross-fades with Done while the first row comes forward. */
@Composable
internal fun DragMultiSelectNavBar(
    state: DragMultiSelectState,
    topInset: Dp,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneInteraction = remember { MutableInteractionSource() }
    val donePressed by doneInteraction.collectIsPressedAsState()
    val barHeight = topInset + DragMultiSelectLayout.navTop.dp + DragMultiSelectLayout.nav.dp +
        DragMultiSelectTokens.View.Nav.barExtraHeight.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - state.navFade.value }
                .background(DragMultiSelectColors.background)
                .then(if (state.isSelecting) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topInset + DragMultiSelectLayout.navTop.dp)
                    .fillMaxWidth()
                    .height(DragMultiSelectLayout.nav.dp)
                    .padding(
                        horizontal = (
                            DragMultiSelectLayout.gutter -
                                DragMultiSelectTokens.View.Nav.horizontalInsetAdjustment
                            ).dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(DragMultiSelectTokens.View.Nav.sideWidth.dp)
                        .height(DragMultiSelectTokens.View.Nav.sideHeight.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Back"
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Canvas(Modifier.size(DragMultiSelectTokens.View.Nav.backIcon.dp)) {
                        dragMultiSelectIcon(
                            DragMultiSelectIcon.Back,
                            DragMultiSelectColors.nav,
                            DragMultiSelectTokens.View.iconStroke,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                width = DragMultiSelectTokens.View.Nav.trayWidth.dp,
                                height = DragMultiSelectTokens.View.Nav.trayHeight.dp,
                            )
                            .background(
                                DragMultiSelectColors.tray,
                                RoundedCornerShape(DragMultiSelectTokens.View.Nav.trayRadius.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = DragMultiSelectTokens.View.Nav.trayLidTop.dp)
                                .size(
                                    width = DragMultiSelectTokens.View.Nav.trayLidWidth.dp,
                                    height = DragMultiSelectTokens.View.Nav.trayLidHeight.dp,
                                )
                                .background(
                                    DragMultiSelectColors.trayLine,
                                    RoundedCornerShape(DragMultiSelectTokens.View.Nav.trayLidRadius.dp),
                                ),
                        )
                    }
                    BasicText(
                        text = "Someday",
                        modifier = Modifier.padding(
                            start = DragMultiSelectTokens.View.Nav.trayTitleGap.dp,
                            end = DragMultiSelectTokens.View.Nav.titleGap.dp,
                        ),
                        style = TextStyle(
                            color = DragMultiSelectColors.ink,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = DragMultiSelectType.nav.fontSize.sp,
                            lineHeight = DragMultiSelectType.nav.lineHeight.sp,
                            fontWeight = FontWeight(DragMultiSelectType.nav.fontWeight),
                        ),
                    )
                    Canvas(Modifier.size(DragMultiSelectTokens.View.Nav.titleChevron.dp)) {
                        dragMultiSelectIcon(
                            DragMultiSelectIcon.ChevronDown,
                            DragMultiSelectColors.nav,
                            DragMultiSelectTokens.View.iconStroke,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(DragMultiSelectTokens.View.Nav.sideWidth.dp)
                        .height(DragMultiSelectTokens.View.Nav.sideHeight.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "More"
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(DragMultiSelectLayout.more.dp)
                            .background(Color.Transparent, CircleShape)
                            .then(
                                Modifier.graphicsLayer {
                                    shape = CircleShape
                                    clip = false
                                },
                            ),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(
                                color = DragMultiSelectColors.nav,
                                radius = size.minDimension / 2f -
                                    (DragMultiSelectTokens.View.Nav.moreStroke / 2f).dp.toPx(),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    DragMultiSelectTokens.View.Nav.moreStroke.dp.toPx(),
                                ),
                            )
                            val inset = (
                                DragMultiSelectLayout.more -
                                    DragMultiSelectTokens.View.Nav.moreChevron
                                ) / 2f
                            inset(inset.dp.toPx()) {
                                dragMultiSelectIcon(
                                    DragMultiSelectIcon.ChevronDown,
                                    DragMultiSelectColors.nav,
                                    DragMultiSelectTokens.View.iconStroke,
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = topInset + DragMultiSelectLayout.navTop.dp +
                        (DragMultiSelectLayout.nav - DragMultiSelectLayout.done.height).dp / 2,
                    end = (
                        DragMultiSelectLayout.gutter -
                            DragMultiSelectTokens.View.Nav.doneEndAdjustment
                        ).dp,
                )
                .size(width = DragMultiSelectTokens.Layout.Done.width.dp, height = DragMultiSelectTokens.Layout.Done.height.dp)
                .graphicsLayer {
                    alpha = state.navFade.value * if (donePressed) {
                        DragMultiSelectTokens.View.Nav.pressedAlpha
                    } else {
                        1f
                    }
                }
                .background(DragMultiSelectColors.blue, RoundedCornerShape(DragMultiSelectTokens.Layout.Done.radius.dp))
                .then(if (!state.isSelecting) Modifier.clearAndSetSemantics {} else Modifier)
                .clickable(
                    interactionSource = doneInteraction,
                    indication = null,
                    enabled = state.isSelecting,
                    onClick = onDone,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "Done selecting"
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Done",
                style = TextStyle(
                    color = DragMultiSelectColors.white,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = DragMultiSelectType.done.fontSize.sp,
                    lineHeight = DragMultiSelectType.done.lineHeight.sp,
                    fontWeight = FontWeight(DragMultiSelectType.done.fontWeight),
                ),
            )
        }
    }
}
