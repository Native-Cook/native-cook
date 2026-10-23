package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun GiftCardPickerSelectionFlight(
    state: GiftCardPickerMotionState,
    width: Float,
    sheetTop: Float,
    sheetHeight: Float,
    reduceMotion: Boolean,
) {
    val raw = state.commit.value
    if (raw <= 0f) return
    val duration = if (reduceMotion) 420f else GiftCardPickerMotionSpec.CommitMs.toFloat()
    val elapsed = raw * duration
    val lift = if (reduceMotion) 0f else giftCardPickerEaseOut(elapsed / 240f)
    val flip = if (reduceMotion) 0f else giftCardPickerClamp01((elapsed - 240f) / 560f)
    val certificateMix = if (reduceMotion) giftCardPickerClamp01(elapsed / 200f) else if (flip >= 0.5f) 1f else 0f
    val flight = giftCardPickerClamp01((elapsed - 1_500f) / GiftCardPickerMotionSpec.CommitFlightMs)
    val landing = giftCardPickerClamp01((elapsed - 2_120f) / GiftCardPickerMotionSpec.CommitLandingMs)
    val startY = sheetTop + (GiftCardPickerLayout.Stock.CardTop - GiftCardPickerLayout.Sheet.Top) * (sheetHeight / 812f)
    val targetY = sheetTop - GiftCardPickerLayout.Sheet.Top + GiftCardPickerLayout.Portfolio.GiftTop +
        (GiftCardPickerLayout.Portfolio.GiftHeight - GiftCardPickerLayout.Stock.CardHeight * 0.3f) / 2f
    val easedFlight = giftCardPickerEaseOut(flight)
    val y = if (reduceMotion) startY else startY - 10f * lift + (targetY - (startY - 10f)) * flight * flight
    val x = (width - GiftCardPickerLayout.Stock.CardWidth) / 2f + if (reduceMotion) 0f else sin(flight * Math.PI.toFloat()) * 34f
    val flightScale = 1.05f + (0.3f - 1.05f) * easedFlight
    val scale = if (reduceMotion) 1f else flightScale * (1f - 0.42f * landing)
    val frontRotation = if (reduceMotion) 0f else flip * 180f
    val certificateRotation = if (reduceMotion) 0f else flip * 180f + 180f
    val stock = GiftCardPickerStocks[state.commitIndex.coerceIn(0, GiftCardPickerStocks.lastIndex)]

    Box(
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .size(GiftCardPickerLayout.Stock.CardWidth.dp, GiftCardPickerLayout.Stock.CardHeight.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = if (reduceMotion) 0f else -10f * flight
                alpha = 1f - landing
                shadowElevation = 18.dp.toPx() * (1f - landing)
                shape = RoundedCornerShape(GiftCardPickerLayout.Stock.CardRadius.dp)
                clip = false
            }
            .clearAndSetSemantics {
                contentDescription = "${stock.name} share certificate moving to portfolio"
            },
    ) {
        GiftCardPickerFlightFront(stock, frontRotation, 1f - certificateMix)
        GiftCardPickerFlightCertificate(stock, certificateRotation, certificateMix)
    }
}

@Composable
private fun GiftCardPickerFlightFront(stock: GiftCardPickerStockData, rotation: Float, faceAlpha: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
                alpha = faceAlpha
                shape = RoundedCornerShape(GiftCardPickerLayout.Stock.CardRadius.dp)
                clip = true
            }
            .background(Color(0xFF101018)),
    ) {
        Image(painterResource(stock.card), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (GiftCardPickerLayout.Stock.LogoY - GiftCardPickerLayout.Stock.Logo / 2f).dp)
                .size(GiftCardPickerLayout.Stock.Logo.dp)
                .background(stock.markBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GiftCardPickerBrandMark(stock, GiftCardPickerLayout.Stock.Logo, Modifier.fillMaxSize())
        }
        Text(
            stock.name,
            Modifier.offset(y = (GiftCardPickerLayout.Stock.NameY - 17f).dp).fillMaxWidth(),
            color = Color.White,
            fontSize = GiftCardPickerLayout.Stock.NameSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "${if (stock.change > 0) "↑" else "↓"} ${abs(stock.change)}% past year",
            Modifier.offset(y = (GiftCardPickerLayout.Stock.PercentY - 10f).dp).fillMaxWidth(),
            color = if (stock.change > 0) GiftCardPickerTheme.Up else GiftCardPickerTheme.Down,
            fontSize = GiftCardPickerLayout.Stock.PercentSize.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GiftCardPickerFlightCertificate(stock: GiftCardPickerStockData, rotation: Float, faceAlpha: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
                alpha = faceAlpha
                shape = RoundedCornerShape(GiftCardPickerLayout.Stock.CardRadius.dp)
                clip = true
            }
            .background(Color(0xFFF7F1E6))
            .border(2.dp, stock.markBackground.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SHARE CERTIFICATE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        Box(
            Modifier.padding(top = 12.dp).size(58.dp).background(stock.markBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GiftCardPickerBrandMark(stock, 58f, Modifier.fillMaxSize())
        }
        Text(stock.name, Modifier.padding(top = 12.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("USD 3 · ONE GIFT SHARE", Modifier.padding(top = 10.dp), fontSize = 10.sp, letterSpacing = 0.8.sp)
        Text("NO. 0003–2026", Modifier.padding(top = 8.dp), fontSize = 9.sp)
    }
}

@Composable
internal fun GiftCardPickerDiscoverSheet(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    sx: Float,
    ky: Float,
    reduceMotion: Boolean,
    view: View,
    enabled: Boolean,
) {
    Box(Modifier.fillMaxSize().background(GiftCardPickerTheme.SheetLight)) {
        GiftCardPickerOrb(state, width, height, sx, ky, reduceMotion)

        Text(
            text = "Terms and Conditions",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (height - GiftCardPickerLayout.Discover.TermsBottom - 10f).dp)
                .graphicsLayer {
                    alpha = 1f - (state.stage.value / 0.1f).coerceIn(0f, 1f)
                },
            style = TextStyle(
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = GiftCardPickerTheme.Terms,
                textDecoration = TextDecoration.Underline,
            ),
        )

        Text(
            text = "Discovering your gift share value...",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (height - GiftCardPickerLayout.Discover.CaptionBottom - 11f).dp)
                .graphicsLayer {
                    val stage = state.stage.value
                    val incoming = (stage / 0.1f).coerceIn(0f, 1f)
                    val outgoing = 1f - ((stage - 1f) / 0.05f).coerceIn(0f, 1f)
                    alpha = minOf(incoming, outgoing)
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, color = GiftCardPickerTheme.Caption),
        )

        GiftCardPickerDiscoverButton(
            state = state,
            width = width,
            height = height,
            view = view,
            enabled = enabled,
        )
    }
}

@Composable
private fun GiftCardPickerDiscoverButton(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    view: View,
    enabled: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        state.setPress(state.pressLight, pressed, SystemClock.uptimeMillis())
        state.wakeFrames()
    }

    Box(
        modifier = Modifier
            .offset(
                x = GiftCardPickerLayout.Button.Side.dp,
                y = (height - GiftCardPickerLayout.Button.Bottom - GiftCardPickerLayout.Button.Height).dp,
            )
            .width((width - GiftCardPickerLayout.Button.Side * 2f).dp)
            .height(GiftCardPickerLayout.Button.Height.dp)
            .graphicsLayer {
                val stage = state.stage.value
                val first = 1f - (stage / 0.1f).coerceIn(0f, 1f)
                val second = ((stage - 1f) / 0.05f).coerceIn(0f, 1f)
                alpha = maxOf(first, second)
                val scale = 1f - (1f - GiftCardPickerMotionSpec.PressScale) * state.pressLight.value
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(GiftCardPickerLayout.Button.Radius.dp))
            .background(GiftCardPickerTheme.Orange)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                val stage = state.stage.value
                val now = SystemClock.uptimeMillis()
                when {
                    stage < 0.5f -> {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        state.discover(now)
                        state.wakeFrames()
                    }
                    stage > 1.9f -> {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        state.choose(now)
                        state.wakeFrames()
                    }
                }
            }
            .semantics { contentDescription = "Discover amount, then choose a gift stock" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Discover amount",
            modifier = Modifier.graphicsLayer {
                alpha = 1f - (state.stage.value / 0.1f).coerceIn(0f, 1f)
            },
            style = TextStyle(fontSize = GiftCardPickerLayout.Button.Font.sp, fontWeight = FontWeight.Medium, color = Color.White),
        )
        Text(
            text = "Choose a gift stock",
            modifier = Modifier.graphicsLayer {
                alpha = ((state.stage.value - 1f) / 0.05f).coerceIn(0f, 1f)
            },
            style = TextStyle(fontSize = GiftCardPickerLayout.Button.Font.sp, fontWeight = FontWeight.Medium, color = Color.White),
        )
    }
}

@Composable
internal fun GiftCardPickerStockSheet(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    ky: Float,
    view: View,
    reduceMotion: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val stockLayout = GiftCardPickerLayout.Stock
    val cardLeft = (width - stockLayout.CardWidth) / 2f
    val cardTop = (stockLayout.CardTop - GiftCardPickerLayout.Sheet.Top) * ky
    val commitOpacity = 1f - (state.commit.value * if (reduceMotion) 4f else 15.875f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(GiftCardPickerTheme.SkeletonBg)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = commitOpacity }) {
            GiftCardPickerSkeleton(state, width, height, ky, cardLeft, cardTop)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.reveal.value.coerceIn(0f, 1f) * commitOpacity },
        ) {
            GiftCardPickerStocks.forEachIndexed { index, stock ->
                GiftCardPickerBackdrop(index, state)
            }
            GiftCardPickerDust(width, height)

            Text(
                text = "Choose your\n$GiftCardPickerAmount gift stock",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = ((stockLayout.TitleY[0] - GiftCardPickerLayout.Sheet.Top) * ky - 17f).dp)
                    .fillMaxWidth()
                    .semantics { heading() },
                style = TextStyle(
                    fontSize = stockLayout.TitleSize.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (cardTop - 20f).dp)
                    .height((stockLayout.CardHeight + 40f).dp)
                    .graphicsLayer { alpha = state.trackFade.value }
                    .pointerInput(state, enabled, width) {
                        if (!enabled) return@pointerInput
                        val density = this.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val tracker = VelocityTracker()
                            var active = false
                            var verticalFail = false
                            var change = down
                            while (change.pressed) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!active && !verticalFail) {
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (abs(dx) > 10f * density && abs(dy) <= 14f * density) {
                                        active = true
                                        state.beginDrag(SystemClock.uptimeMillis())
                                    } else if (abs(dy) > 14f * density && abs(dx) <= 10f * density) {
                                        verticalFail = true
                                    }
                                }
                                if (active) {
                                    val translation = (change.position.x - down.position.x) / density
                                    val ticked = state.drag(translation, stockLayout.Spacing, SystemClock.uptimeMillis())
                                    if (ticked) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    change.consume()
                                }
                            }
                            if (active) {
                                val velocity = tracker.calculateVelocity().x / density
                                val ticked = state.endDrag(velocity, stockLayout.Spacing, SystemClock.uptimeMillis())
                                if (ticked) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                state.wakeFrames()
                            }
                        }
                    },
            ) {
                GiftCardPickerStocks.forEachIndexed { index, stock ->
                    GiftCardPickerStockCard(
                        stock = stock,
                        index = index,
                        left = cardLeft,
                        state = state,
                        enabled = enabled,
                        onClick = {
                            val ticked = state.goTo(index, 0f, SystemClock.uptimeMillis(), allowHaptic = !state.playing)
                            if (ticked) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            state.wakeFrames()
                        },
                    )
                }
            }

            GiftCardPickerStockDots(state, width, ky)
            GiftCardPickerStockDescriptions(state, width, height)
            GiftCardPickerChooseButton(state, width, height, enabled, onChoose)
        }
    }
}

@Composable
private fun GiftCardPickerSkeleton(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    ky: Float,
    cardLeft: Float,
    cardTop: Float,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - state.reveal.value.coerceIn(0f, 1f) },
    ) {
        GiftCardPickerLayout.Skeleton.Title.forEach { (y, w) ->
            Box(
                Modifier
                    .offset(x = ((width - w) / 2f).dp, y = ((y - GiftCardPickerLayout.Sheet.Top) * ky).dp)
                    .size(w.dp, GiftCardPickerLayout.Skeleton.TitleHeight.dp)
                    .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(7.dp)),
            )
        }
        Box(
            Modifier
                .offset(x = cardLeft.dp, y = cardTop.dp)
                .size(GiftCardPickerLayout.Stock.CardWidth.dp, GiftCardPickerLayout.Stock.CardHeight.dp)
                .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(GiftCardPickerLayout.Stock.CardRadius.dp)),
        )
        Box(
            Modifier
                .offset(x = cardLeft.dp, y = cardTop.dp)
                .size(GiftCardPickerLayout.Stock.CardWidth.dp, GiftCardPickerLayout.Stock.CardHeight.dp)
                .graphicsLayer {
                    translationX = GiftCardPickerLayout.Stock.Spacing * density
                    scaleX = GiftCardPickerLayout.Stock.SideScale
                    scaleY = GiftCardPickerLayout.Stock.SideScale
                }
                .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(GiftCardPickerLayout.Stock.CardRadius.dp))
                .drawWithContent {
                    val radians = GiftCardPickerLayout.Stock.SideSkew * (Math.PI.toFloat() / 180f)
                    val matrix = Matrix().apply {
                        setSkew(kotlin.math.tan(radians), 0f, size.width / 2f, size.height / 2f)
                    }
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    canvas.concat(matrix)
                    drawContent()
                    canvas.restoreToCount(save)
                },
        )
        Box(
            Modifier
                .offset(
                    x = ((width - GiftCardPickerLayout.Skeleton.DotsWidth) / 2f).dp,
                    y = ((GiftCardPickerLayout.Skeleton.DotsY - GiftCardPickerLayout.Sheet.Top) * ky).dp,
                )
                .size(GiftCardPickerLayout.Skeleton.DotsWidth.dp, GiftCardPickerLayout.Skeleton.DotsHeight.dp)
                .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(4.dp)),
        )
        GiftCardPickerLayout.Skeleton.Description.forEach { (screenY, w) ->
            val fromBottom = GiftCardPickerLayout.ScreenHeight - screenY
            val top = height - fromBottom
            Box(
                Modifier
                    .offset(x = ((width - w) / 2f).dp, y = top.dp)
                    .size(w.dp, GiftCardPickerLayout.Skeleton.DescriptionHeight.dp)
                    .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(3.dp)),
            )
        }
        Box(
            Modifier
                .offset(x = GiftCardPickerLayout.Button.Side.dp, y = (height - GiftCardPickerLayout.Button.Bottom - GiftCardPickerLayout.Button.Height).dp)
                .width((width - GiftCardPickerLayout.Button.Side * 2f).dp)
                .height(GiftCardPickerLayout.Button.Height.dp)
                .background(GiftCardPickerTheme.Skeleton, RoundedCornerShape(GiftCardPickerLayout.Button.Radius.dp)),
        )
    }
}

private data class GiftCardPickerDustPoint(
    val x: Float,
    val y: Float,
    val size: Float,
    val opacity: Float,
)

private val GiftCardPickerDustPoints = List(46) { index ->
    val a = sin(index * 12.9898) * 43758.5453
    val b = sin(index * 78.233) * 12345.6789
    val fx = a - floor(a)
    val fy = b - floor(b)
    GiftCardPickerDustPoint(
        x = fx.toFloat(),
        y = 0.18f + fy.toFloat() * 0.66f,
        size = 1f + ((index * 7) % 3) * 0.5f,
        opacity = 0.25f + ((index * 13) % 5) * 0.1f,
    )
}

@Composable
private fun GiftCardPickerDust(width: Float, height: Float) {
    GiftCardPickerDustPoints.forEach { dust ->
        Box(
            Modifier
                .offset(x = (dust.x * width).dp, y = (dust.y * height).dp)
                .size(dust.size.dp)
                .background(Color.White.copy(alpha = dust.opacity), CircleShape),
        )
    }
}

@Composable
private fun GiftCardPickerStockCard(
    stock: GiftCardPickerStockData,
    index: Int,
    left: Float,
    state: GiftCardPickerMotionState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val layout = GiftCardPickerLayout.Stock
    val layerOrder by remember(state, index) {
        derivedStateOf {
            (10f - abs(index - state.scroll.value) * 2f).roundToInt().toFloat()
        }
    }
    Box(
        modifier = Modifier
            .offset(x = left.dp, y = 20.dp)
            .size(layout.CardWidth.dp, layout.CardHeight.dp)
            .graphicsLayer {
                val difference = index - state.scroll.value
                val side = difference.coerceIn(-1f, 1f)
                val absolute = abs(side)
                alpha = if (abs(difference) > 2.2f) 0f else 1f
                translationX = difference * layout.Spacing * density
                scaleX = 1f - (1f - layout.SideScale) * absolute
                scaleY = 1f - (1f - layout.SideScale) * absolute
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
            .drawWithContent {
                val difference = index - state.scroll.value
                val side = difference.coerceIn(-1f, 1f)
                val radians = side * layout.SideSkew * (Math.PI.toFloat() / 180f)
                val matrix = Matrix().apply {
                    setSkew(kotlin.math.tan(radians), 0f, size.width / 2f, size.height / 2f)
                }
                val canvas = drawContext.canvas.nativeCanvas
                val save = canvas.save()
                canvas.concat(matrix)
                drawContent()
                canvas.restoreToCount(save)
            }
            .clip(RoundedCornerShape(layout.CardRadius.dp))
            .background(Color(0xFF101018))
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${stock.name}, ${if (stock.change > 0) "up" else "down"} ${abs(stock.change)}% past year"
            }
            // The rounded order changes only at card-crossing thresholds, not on every frame.
            .zIndex(layerOrder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = state.reveal.value
                    alpha = when {
                        k <= 1f -> 0.4f + 0.35f * k
                        else -> 0.75f + 0.25f * (k - 1f)
                    }
                },
        ) {
            Image(
                painter = painterResource(stock.card),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        GiftCardPickerBrandMark(
            stock = stock,
            size = layout.Logo,
            modifier = Modifier
                .offset(x = ((layout.CardWidth - layout.Logo) / 2f).dp, y = (layout.LogoY - layout.Logo / 2f).dp)
                .size(layout.Logo.dp)
                .graphicsLayer {
                    val amount = (state.reveal.value - 1f).coerceIn(0f, 1f)
                    alpha = amount
                    val scale = 0.9f + 0.1f * amount
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(stock.markBackground),
        )
        Text(
            text = stock.name,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (layout.NameY - 17f).dp)
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            style = TextStyle(fontSize = layout.NameSize.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Color.White, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        val positive = stock.change > 0
        Text(
            text = "${if (positive) "↑" else "↓"} ${abs(stock.change)}% past year",
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (layout.PercentY - 10f).dp).fillMaxWidth(),
            style = TextStyle(fontSize = layout.PercentSize.sp, lineHeight = 20.sp, color = if (positive) GiftCardPickerTheme.Up else GiftCardPickerTheme.Down, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(layout.CardRadius.dp))
                .background(Color.Transparent)
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        // Compose defines a zero-width stroke as one physical-pixel hairline.
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.CardRadius.dp.toPx()),
                    )
                },
        )
    }
}

@Composable
private fun GiftCardPickerStockDots(
    state: GiftCardPickerMotionState,
    width: Float,
    ky: Float,
) {
    val dot = GiftCardPickerLayout.Stock.Dot
    val gap = GiftCardPickerLayout.Stock.DotGap - dot
    val rowWidth = dot * GiftCardPickerStocks.size + gap * (GiftCardPickerStocks.size - 1)
    Row(
        modifier = Modifier
            .offset(
                x = ((width - rowWidth) / 2f).dp,
                y = ((GiftCardPickerLayout.Stock.DotsY - GiftCardPickerLayout.Sheet.Top) * ky - dot / 2f).dp,
            )
            .semantics { invisibleToUser() },
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        GiftCardPickerStocks.indices.forEach { index ->
            Box(
                Modifier
                    .size(dot.dp)
                    .graphicsLayer { alpha = if (state.activeIndex == index) 1f else 0.38f }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun GiftCardPickerStockDescriptions(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
) {
    val layout = GiftCardPickerLayout.Stock
    val top = height - layout.DescriptionBottom - layout.DescriptionLine / 2f
    GiftCardPickerStocks.forEachIndexed { index, stock ->
        Column(
            modifier = Modifier
                .offset(x = 24.dp, y = top.dp)
                .width((width - 48f).dp)
                .graphicsLayer { alpha = state.descriptions[index].value }
                .semantics {
                    if (state.activeIndex != index) invisibleToUser()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stock.lines[0],
                style = TextStyle(fontSize = layout.DescriptionSize.sp, lineHeight = layout.DescriptionLine.sp, color = Color.White.copy(alpha = 0.86f)),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        append(stock.lines[1])
                        stock.tail.forEachIndexed { index, letter ->
                            pushStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f - index * 0.3f)))
                            append(letter)
                            pop()
                        }
                    },
                    style = TextStyle(fontSize = layout.DescriptionSize.sp, lineHeight = layout.DescriptionLine.sp, color = Color.White.copy(alpha = 0.86f)),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = "more",
                    modifier = Modifier.padding(start = 6.dp),
                    style = TextStyle(fontSize = layout.DescriptionSize.sp, lineHeight = layout.DescriptionLine.sp, color = Color.White, textDecoration = TextDecoration.Underline),
                )
            }
        }
    }
}

@Composable
private fun GiftCardPickerChooseButton(
    state: GiftCardPickerMotionState,
    width: Float,
    height: Float,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        state.setPress(state.pressDark, pressed, SystemClock.uptimeMillis())
        state.wakeFrames()
    }
    Box(
        modifier = Modifier
            .offset(
                x = GiftCardPickerLayout.Button.Side.dp,
                y = (height - GiftCardPickerLayout.Button.Bottom - GiftCardPickerLayout.Button.Height).dp,
            )
            .width((width - GiftCardPickerLayout.Button.Side * 2f).dp)
            .height(GiftCardPickerLayout.Button.Height.dp)
            .graphicsLayer {
                val scale = 1f - (1f - GiftCardPickerMotionSpec.PressScale) * state.pressDark.value
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(GiftCardPickerLayout.Button.Radius.dp))
            .background(GiftCardPickerTheme.Choose)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onChoose)
            .semantics { contentDescription = "Choose this stock" },
        contentAlignment = Alignment.Center,
    ) {
        GiftCardPickerStocks.forEachIndexed { index, stock ->
            Text(
                text = "Choose ${stock.name}",
                modifier = Modifier.graphicsLayer { alpha = if (state.activeIndex == index) 1f else 0f },
                style = TextStyle(fontSize = GiftCardPickerLayout.Button.Font.sp, color = GiftCardPickerTheme.ChooseInk),
            )
        }
    }
}
