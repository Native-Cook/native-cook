package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components.AwakeningCard
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.components.CollectionCardBackdrop
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.data.getCardById
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme.CollectionCardBackdropColors
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.fixedSp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Colors
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Radii
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.Spacing
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme.TypeScale
import kotlin.math.min

private val SHEET_RADIUS = 32.dp
private val PRESS_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val PRESS_DURATION = 110

@Composable
private fun BackButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) cssColor("rgba(103, 113, 124, 0.24)") else cssColor("rgba(255, 255, 255, 0.62)"),
        animationSpec = tween(PRESS_DURATION, easing = PRESS_EASING),
        label = "back-bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(PRESS_DURATION, easing = PRESS_EASING),
        label = "back-scale",
    )
    val iconTint = cssColor("#667080")

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(Radii.pill))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Quay lại bộ sưu tập. Lá bài ổn định lại trước khi trở về danh sách"
                if (!enabled) disabled()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(background, CircleShape)
            .border(1.dp, cssColor("rgba(105, 115, 132, 0.14)"), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // SF Symbol `chevron.left` / Material `chevron_left`, 14 dp.
        Canvas(modifier = Modifier.size(14.dp)) {
            val stroke = 2f * density
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.64f, size.height * 0.12f)
                lineTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.64f, size.height * 0.88f)
            }
            drawPath(
                path = path,
                color = iconTint,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
fun CardDetailScreen(cardId: String?, onBack: () -> Unit) {
    val card = getCardById(cardId)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    var isDismissing by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)

    if (card == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.background)
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                text = "Không tìm thấy thẻ",
                style = TextStyle(
                    color = Colors.text,
                    fontSize = TypeScale.title.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            BasicText(
                text = "Trở về bộ sưu tập",
                style = TextStyle(color = Colors.accent, fontSize = 14.sp),
                modifier = Modifier
                    .padding(top = Spacing.md)
                    .clickable(role = Role.Button) { currentOnBack() },
            )
        }
        return
    }

    // Keep consuming system back while the card settles, including repeated presses.
    BackHandler {
        if (!isDismissing) isDismissing = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKDROP_COLORS[0]),
    ) {
        val width = maxWidth
        val height = maxHeight
        val cardWidth = min((width - Spacing.xxl * 2).value, 316f).dp
        val sheetTop = if (topInset + Spacing.xs > Spacing.xl) topInset + Spacing.xs else Spacing.xl
        val sheetHeight = if ((height - sheetTop).value > 1f) height - sheetTop else 1.dp

        SheetBackdrop()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sheetTop)
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS))
                .background(CollectionCardBackdropColors.background),
        ) {
            CollectionCardBackdrop(height = sheetHeight, width = width)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.sm)
                    .size(34.dp, 5.dp)
                    .background(cssColor("rgba(103, 113, 124, 0.62)"), RoundedCornerShape(Radii.pill)),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = sheetHeight)
                    .padding(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = Spacing.md,
                        bottom = bottomInset + Spacing.xxl,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(enabled = !isDismissing, onClick = { isDismissing = true })
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AwakeningCard(
                        width = cardWidth,
                        exiting = isDismissing,
                        onExitReady = { currentOnBack() },
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 54.dp)
                        .size(30.dp)
                        .shadow(6.dp, CircleShape, ambientColor = cssColor("rgba(91, 103, 119, 0.14)"),
                            spotColor = cssColor("rgba(91, 103, 119, 0.14)"))
                        .background(cssColor("rgba(255, 255, 255, 0.84)"), CircleShape)
                        .border(1.dp, cssColor("rgba(255, 255, 255, 0.94)"), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "1",
                        style = TextStyle(
                            color = cssColor("#84909d"),
                            fontFamily = FontFamily.Serif,
                            fontSize = TypeScale.label.fixedSp(),
                        ),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 50.dp)
                        .widthIn(max = 430.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    BasicText(
                        text = "The Awakening",
                        style = TextStyle(
                            color = cssColor("#18212b"),
                            fontFamily = FontFamily.Serif,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-1.2f).sp,
                            lineHeight = 36.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    BasicText(
                        text = "Congratulations on signing up to mymind.\nWelcome to your beautiful private garden.",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = cssColor("#68717d"),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CardDetailScreenPreview() {
    CardDetailScreen(cardId = "verdant-sproutling", onBack = {})
}
