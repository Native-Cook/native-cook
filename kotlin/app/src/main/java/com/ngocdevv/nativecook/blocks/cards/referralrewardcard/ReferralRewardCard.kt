package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size

/** Self-contained card. [reduceTransparency] is a live host override, independent of motion. */
@Composable
fun ReferralRewardCard(
    width: Dp,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    val context = LocalContext.current
    val displayDensity = LocalDensity.current
    val density = displayDensity.density
    val reduced = referralReducedMotion()
    val cardWidth = width.value
    val state = rememberReferralState(cardWidth, autoPlay, reduced)
    val glass = remember { ReferralGlass() }
    val rail = remember(context, density) { ReferralRailPainter(context, density) }
    val points =
        remember(density, displayDensity.fontScale) {
            ReferralPoints(density, displayDensity.fontScale)
        }
    val tiers = remember(density, displayDensity.fontScale) { ReferralTiers() }
    val flight = remember(density, displayDensity.fontScale) { ReferralFlight(density) }
    val effects = rememberReferralLabelBlur(density)
    var height by remember { mutableFloatStateOf(0f) }
    var railTop by remember { mutableFloatStateOf(0f) }
    var markX by remember { mutableFloatStateOf(0f) }
    var markY by remember { mutableFloatStateOf(0f) }
    var cardPosition by remember { mutableStateOf(Offset.Zero) }
    val first = stringResource(R.string.referralrewardcard_refer)
    val second = stringResource(R.string.referralrewardcard_reset)
    val labelStyle =
        referralText(16, 22, FontWeight.Medium)
            .copy(letterSpacing = (-0.2).sp, textAlign = TextAlign.Center)
    val buttonSize = referralButtonSize(cardWidth, density, first, second, labelStyle)
    val buttonWidth = buttonSize.width
    val buttonHeight = buttonSize.height
    LaunchedEffect(cardWidth, height, railTop, buttonWidth, buttonHeight) {
        state.railTop = railTop.toDouble()
        if (height > 0 && railTop > 0) {
            state.footerPath =
                ReferralPath(
                    cardWidth / 2.0,
                    (height - Size.cardPad - buttonHeight / 2).toDouble(),
                    cardWidth / 2.0,
                    (railTop + Size.cy).toDouble(),
                )
        }
    }
    Box(
        modifier
            .width(width)
            .onSizeChanged { height = it.height / density }
            .onGloballyPositioned {
                cardPosition = it.localToRoot(Offset.Zero)
            }.pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) state.touch()
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                glass.draw(
                    canvas,
                    state,
                    cardWidth,
                    height,
                    buttonWidth,
                    buttonHeight,
                    density,
                    reduceTransparency,
                )
                canvas.restore()
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = Size.cardPad.dp)) {
            ReferralHeader(
                state,
                cardWidth,
                density,
                displayDensity.fontScale,
                tiers,
                cardPosition,
            ) { x, y ->
                markX = x
                markY = y
            }
            Spacer(Modifier.height(20.dp))
            ReferralCardRail(state, rail, points, cardWidth, density, effects, cardPosition) {
                railTop = it
            }
            ReferralButton(
                state,
                buttonWidth,
                buttonHeight,
                density,
                effects,
                first,
                second,
                labelStyle,
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                flight.draw(
                    canvas,
                    state,
                    cardWidth,
                    height,
                    markX + Size.tierDiameter / 2 + Size.tierStep * state.rewardIndex,
                    markY + Size.tierDiameter / 2,
                )
                canvas.restore()
            }
        }
    }
}
