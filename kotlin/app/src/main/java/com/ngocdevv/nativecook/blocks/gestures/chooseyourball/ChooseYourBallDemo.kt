package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

private const val WORDMARK_FRACTION = 0.2f
private const val WORDMARK_MAX = 82f
private const val COMPACT_HEIGHT = 740f

/**
 * Choose Your Ball: the 26-ball belt, one ball at a time on a Filament stage. Swipe sideways (or
 * tap the strip) to change ball; drag up or down to look over and under it.
 *
 * The block takes the whole screen, draws under the system bars and keeps its own content clear
 * of them. Beyond its own folder it needs Filament (filament-android, gltfio-android and
 * filament-utils-android) and the files in `assets/chooseyourball`.
 */
@Composable
fun ChooseYourBallDemo() {
    val systemDensity = LocalDensity.current
    // The font scale is applied by hand, capped, to the text that follows it in the reference;
    // inside the block one sp is one dp.
    val typeScale = min(systemDensity.fontScale, MAX_TYPE_SCALE)
    val reducedMotion = rememberReducedMotion()
    val carousel = rememberBallCarouselState(initialBall = BallId.DUSK)
    val tilt = rememberBallTiltState()

    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, fontScale = 1f)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(STAGE_INK)) {
            val width = maxWidth.value
            val height = maxHeight.value
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val compact = height < COMPACT_HEIGHT

            val ball = carousel.previewBall
            val (hero, tail) = remember(ball) { wordmark(ball.label) }
            val facts = remember(ball) { ballFacts(ball) }
            val grounds = remember(carousel) { carousel.order.map { groundFromBall(it.fx.aura) } }
            val offset = remember(carousel) { { carousel.offset } }
            val tiltValue = remember(tilt) { { tilt.tilt } }

            val stageSize = pickerSourceBoundarySize(width, compact)
            val stageCentreY = height * PICKER_BALL_CENTER_FRACTION
            val wordSize = min(WORDMARK_MAX, width * WORDMARK_FRACTION) * (if (compact) 0.88f else 1f)

            BallBackdrop(colors = grounds, offset = offset, modifier = Modifier.fillMaxSize())

            Box(Modifier.fillMaxSize().ballGestures(carousel, tilt, tiltEnabled = !reducedMotion)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = max(insets.calculateTopPadding().value, 12f).dp + 4.dp)
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(
                        "YOUR BELT",
                        Modifier.alignByBaseline(),
                        style = chromeText(RITUAL_GOLD, 9f, FontWeight.Black, 1.9f, typeScale),
                    )
                    BasicText(
                        "${(carousel.preview + 1).toString().padStart(2, '0')} / ${carousel.total}",
                        Modifier.alignByBaseline(),
                        style = chromeText(Color.White.copy(alpha = 0.45f), 10f, FontWeight.ExtraBold, 1.2f, typeScale)
                            .copy(fontFeatureSettings = "tnum"),
                    )
                }

                // Behind the ball: the name is part of the same picture as the object.
                BallWordmark(
                    hero = hero,
                    tail = tail,
                    size = wordSize.dp,
                    width = width.dp,
                    typeScale = typeScale,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.offset(y = (stageCentreY - stageSize * 0.42f - wordSize * 1.1f).dp),
                )

                // The stage is a little wider than a phone's screen; the ball itself is not.
                BallStage3D(
                    offset = offset,
                    tilt = tiltValue,
                    reducedMotion = reducedMotion,
                    variant = ball,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (stageCentreY - stageSize / 2f).dp)
                        .requiredSize(stageSize.dp),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = max(insets.calculateBottomPadding().value, 14f).dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    ) {
                        for (fact in facts) Chip(fact, typeScale)
                    }
                    ActionButton(
                        label = "EQUIP ${ball.label.uppercase()}",
                        typeScale = typeScale,
                        onPress = {},
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp),
                    )
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(42.dp)) {
                        BallStrip(order = carousel.order, offset = offset, onPick = carousel::selectSlot)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, typeScale: Float) {
    val hairline = with(LocalDensity.current) { 1f.toDp() }
    BasicText(
        text,
        Modifier
            .background(Color.White.copy(alpha = 0.09f), CircleShape)
            .border(hairline, Color.White.copy(alpha = 0.16f), CircleShape)
            // The hairline is part of the chip's size, outside its 11 / 5 padding.
            .padding(horizontal = 11.dp + hairline, vertical = 5.dp + hairline),
        style = chromeText(Color.White.copy(alpha = 0.9f), 9f, FontWeight.ExtraBold, 0.9f, typeScale),
    )
}

/** Small capitals of the chrome. `size` and `tracking` are the reference's points. */
private fun chromeText(color: Color, size: Float, weight: FontWeight, tracking: Float, typeScale: Float) = TextStyle(
    color = color,
    fontSize = (size * typeScale).sp,
    fontWeight = weight,
    letterSpacing = (tracking * typeScale).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
