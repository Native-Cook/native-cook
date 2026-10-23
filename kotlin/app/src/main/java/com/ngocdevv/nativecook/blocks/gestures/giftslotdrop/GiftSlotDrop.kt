package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.min

const val LOOP: Long = Tokens.LOOP_MS

@Composable
fun GiftSlotDrop(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onShop: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val assets = rememberGiftSlotDropAssets()
    val renderer = remember(assets) { GiftSlotDropRenderer(assets) }
    val simulation = remember {
        GiftSlotDropSimulation { event ->
            val feedback = when (event) {
                GiftSlotDropHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK
                GiftSlotDropHaptic.Armed -> HapticFeedbackConstants.CONTEXT_CLICK
                GiftSlotDropHaptic.Insert -> HapticFeedbackConstants.LONG_PRESS
                GiftSlotDropHaptic.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
                GiftSlotDropHaptic.Light -> HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(feedback)
        }
    }
    var drawTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(GiftSlotDropPhase.Choosing) }
    var selected by remember { mutableIntStateOf(Tokens.START_INDEX) }
    var armed by remember { mutableStateOf(false) }
    var detailMask by remember { mutableIntStateOf(0) }

    LaunchedEffect(autoPlay) {
        simulation.start(autoPlay)
    }

    LaunchedEffect(simulation, autoPlay, reducedMotion) {
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val rawSeconds = (now - previous).coerceAtLeast(0L) / 1_000_000_000f
            previous = now
            simulation.step(min(Tokens.MAX_FRAME_SECONDS, rawSeconds), autoPlay, reducedMotion)
            if (phase != simulation.phase) {
                phase = simulation.phase
            }
            if (selected != simulation.selected) {
                selected = simulation.selected
            }
            if (armed != simulation.armed) {
                armed = simulation.armed
            }
            detailMask = offerDetailMask(simulation, reducedMotion)
            drawTick += 1L
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(hex(Tokens.COLORS.stageEdge)),
    ) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        simulation.width = widthDp
        val gift = Tokens.GIFTS[selected]
        val choosing = phase == GiftSlotDropPhase.Choosing ||
            phase == GiftSlotDropPhase.Pulling ||
            phase == GiftSlotDropPhase.Swiping
        val activating = phase == GiftSlotDropPhase.Inserting || phase == GiftSlotDropPhase.Activating
        val offer = phase == GiftSlotDropPhase.Offer || phase == GiftSlotDropPhase.Closing

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    if (choosing) {
                        role = Role.Button
                        contentDescription = "${gift.name}, ${gift.headline} ${gift.line} ${gift.cap}. Gift ${selected + 1} of ${Tokens.GIFTS.size}"
                        customActions = listOf(
                            CustomAccessibilityAction("Next gift") {
                                simulation.takeOver()
                                simulation.stepGift(1)
                                simulation.letGo()
                                true
                            },
                            CustomAccessibilityAction("Previous gift") {
                                simulation.takeOver()
                                simulation.stepGift(-1)
                                simulation.letGo()
                                true
                            },
                            CustomAccessibilityAction("Activate gift") {
                                simulation.takeOver()
                                simulation.activate()
                                simulation.letGo()
                                true
                            },
                        )
                    }
                }
                .pointerInput(simulation, density.density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        simulation.takeOver()
                        simulation.down(
                            down.position.x / density.density,
                            down.position.y / density.density,
                            false,
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity()
                                simulation.up(
                                    velocity.x / density.density,
                                    velocity.y / density.density,
                                )
                                simulation.letGo()
                                break
                            }
                            simulation.move(
                                change.position.x / density.density,
                                change.position.y / density.density,
                            )
                            change.consume()
                        }
                    }
                },
        ) {
            drawTick
            val save = drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.scale(density.density, density.density)
            renderer.draw(
                drawContext.canvas.nativeCanvas,
                simulation,
                widthDp,
                heightDp,
                reducedMotion,
            )
            drawContext.canvas.nativeCanvas.restoreToCount(save)
        }

        GiftSlotDropChoosingOverlay(
            choosing = choosing,
            activating = activating,
            offer = offer,
            armed = armed,
            assets = assets,
        )
        if (offer) {
            GiftSlotDropOfferOverlay(
                width = widthDp,
                gift = gift,
                detailMask = detailMask,
                onClose = {
                    simulation.takeOver()
                    simulation.close()
                    simulation.letGo()
                },
                onShop = {
                    if (onShop != null) {
                        onShop(gift.id)
                    } else {
                        simulation.takeOver()
                        simulation.close()
                        simulation.letGo()
                    }
                },
            )
        }
    }
}

@Composable
private fun GiftSlotDropChoosingOverlay(
    choosing: Boolean,
    activating: Boolean,
    offer: Boolean,
    armed: Boolean,
    assets: GiftSlotDropAssets,
) {
    val topAlpha by animateFloatAsState(if (offer) 0f else 1f, tween(220), label = "gift top")
    val chooseAlpha by animateFloatAsState(if (choosing && !armed) 1f else 0f, tween(220), label = "gift choose")
    val armedAlpha by animateFloatAsState(if (choosing && armed) 1f else 0f, tween(220), label = "gift armed")
    val activeAlpha by animateFloatAsState(
        if (activating) 1f else 0f,
        tween(220, delayMillis = 200),
        label = "gift activating",
    )
    val hintAlpha by animateFloatAsState(if (choosing && !armed) 1f else 0f, tween(220), label = "gift hint")
    Box(modifier = Modifier.fillMaxSize()) {
        GiftSlotDropHeader(
            top = Tokens.LAYOUT.headerTop,
            left = Tokens.LAYOUT.headerLeft,
            right = Tokens.LAYOUT.headerRight,
            label = "T&Cs",
            alpha = topAlpha,
        )
        Image(
            bitmap = assets.giftBox.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    x = Tokens.LAYOUT.giftDx.dp,
                    y = (Tokens.LAYOUT.giftY - Tokens.LAYOUT.giftSize / 2f).dp,
                )
                .size(Tokens.LAYOUT.giftSize.dp)
                .graphicsLayer { alpha = topAlpha },
        )
        GiftSlotDropTitle(
            text = "Choose your\nwelcome gift",
            colour = hex(Tokens.COLORS.ink),
            alpha = chooseAlpha,
            top = Tokens.LAYOUT.titleY - Tokens.LAYOUT.titleLine,
        )
        GiftSlotDropTitle(
            text = "Release to\nactivate offer!",
            colour = hex(Tokens.COLORS.yellow),
            alpha = armedAlpha,
            top = Tokens.LAYOUT.titleY - Tokens.LAYOUT.titleLine,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (Tokens.LAYOUT.cardY - 70f).dp)
                .graphicsLayer { alpha = activeAlpha },
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                bitmap = assets.giftBox.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "Activating the\noffer for you…",
                modifier = Modifier.offset(y = 44.dp),
                color = hex(Tokens.COLORS.yellow),
                fontSize = Tokens.LAYOUT.titleSize.sp,
                lineHeight = Tokens.LAYOUT.titleLine.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "Drag down to activate offer",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (Tokens.LAYOUT.hintY - 9f).dp)
                .graphicsLayer { alpha = hintAlpha },
            color = hex(Tokens.COLORS.hint),
            fontSize = Tokens.TYPE.hint.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun GiftSlotDropOfferOverlay(
    width: Float,
    gift: Tokens.Gift,
    detailMask: Int,
    onClose: () -> Unit,
    onShop: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        GiftSlotDropHeader(
            top = Tokens.LAYOUT.offerHeaderTop,
            left = Tokens.LAYOUT.headerLeft,
            right = Tokens.LAYOUT.headerRight,
            label = "Help",
            alpha = detailAlpha(detailMask, 0),
            onLeading = onClose,
            close = true,
        )
        val detailOne = detailAlpha(detailMask, 1)
        val detailTwo = detailAlpha(detailMask, 2)
        Text(
            text = "How to avail cashback?",
            modifier = Modifier
                .offset(x = Tokens.SPACE.gutter.dp, y = Tokens.LAYOUT.howTop.dp)
                .graphicsLayer {
                    alpha = detailOne
                    translationY = (1f - detailOne) * Tokens.LAYOUT.detailRise
                },
            color = hex(Tokens.COLORS.yellow),
            fontSize = Tokens.TYPE.howTitle.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
        )
        Row(
            modifier = Modifier
                .offset(x = Tokens.SPACE.gutter.dp, y = Tokens.LAYOUT.stepTop.dp)
                .graphicsLayer {
                    alpha = detailTwo
                    translationY = (1f - detailTwo) * Tokens.LAYOUT.detailRise
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "1",
                modifier = Modifier.width(34.dp),
                color = hex(Tokens.COLORS.ink),
                fontSize = Tokens.TYPE.stepNumber.sp,
                fontWeight = FontWeight.ExtraLight,
            )
            Text(
                text = gift.use,
                modifier = Modifier
                    .padding(start = Tokens.SPACE.md.dp)
                    .width(250.dp),
                color = hex(Tokens.COLORS.ink),
                fontSize = Tokens.TYPE.stepText.sp,
                lineHeight = Tokens.TYPE.stepLine.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
            )
        }
        val buttonAlpha = detailAlpha(detailMask, 3)
        GiftSlotDropPressable(
            modifier = Modifier
                .offset(
                    x = ((width - Tokens.LAYOUT.buttonW) / 2f).dp,
                    y = Tokens.LAYOUT.buttonTop.dp,
                )
                .width(Tokens.LAYOUT.buttonW.dp)
                .height(Tokens.LAYOUT.buttonH.dp)
                .graphicsLayer {
                    alpha = buttonAlpha
                    translationY = (1f - buttonAlpha) * Tokens.LAYOUT.detailRise
                },
            label = "Order from ${gift.name}",
            onClick = onShop,
        )
    }
}

@Composable
private fun GiftSlotDropHeader(
    top: Float,
    left: Float,
    right: Float,
    label: String,
    alpha: Float,
    onLeading: (() -> Unit)? = null,
    close: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = left.dp, end = right.dp)
            .offset(y = top.dp)
            .height(Tokens.LAYOUT.headerH.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GiftSlotDropHeaderIcon(close = close, onClick = onLeading)
        Box(modifier = Modifier.weight(1f))
        Text(
            text = label,
            color = hex(Tokens.COLORS.ink),
            fontSize = Tokens.TYPE.header.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun GiftSlotDropHeaderIcon(close: Boolean, onClick: (() -> Unit)?) {
    val clickable = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Canvas(
        modifier = Modifier
            .size(44.dp)
            .offset(x = (-Tokens.LAYOUT.headerHitSlop).dp)
            .then(clickable),
    ) {
        val path = androidx.compose.ui.graphics.Path()
        if (close) {
            path.moveTo(14f, 14f)
            path.lineTo(30f, 30f)
            path.moveTo(30f, 14f)
            path.lineTo(14f, 30f)
        } else {
            path.moveTo(25f, 14f)
            path.lineTo(17f, 22f)
            path.lineTo(25f, 30f)
        }
        drawPath(
            path = path,
            color = hex(Tokens.COLORS.ink),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun GiftSlotDropTitle(text: String, colour: Color, alpha: Float, top: Float) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = top.dp)
            .graphicsLayer { this.alpha = alpha },
        color = colour,
        fontSize = Tokens.LAYOUT.titleSize.sp,
        lineHeight = Tokens.LAYOUT.titleLine.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GiftSlotDropPressable(modifier: Modifier, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Tokens.buttonScale else 1f,
        animationSpec = tween(if (pressed) Tokens.buttonInMs.toInt() else Tokens.buttonOutMs.toInt()),
        label = "gift button press",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(Tokens.LAYOUT.buttonH.dp / 2))
            .background(hex(Tokens.COLORS.button))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = hex(Tokens.COLORS.buttonInk),
            fontSize = Tokens.TYPE.button.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun rememberGiftSlotDropAssets(): GiftSlotDropAssets {
    val cards = mapOf(
        "white" to ImageBitmap.imageResource(R.drawable.giftslotdrop_card_white).asAndroidBitmap(),
        "parcels" to ImageBitmap.imageResource(R.drawable.giftslotdrop_card_parcels).asAndroidBitmap(),
        "burger" to ImageBitmap.imageResource(R.drawable.giftslotdrop_card_burger).asAndroidBitmap(),
        "red" to ImageBitmap.imageResource(R.drawable.giftslotdrop_card_red).asAndroidBitmap(),
    )
    val marks = mapOf(
        "parcels" to ImageBitmap.imageResource(R.drawable.giftslotdrop_mark_arrow).asAndroidBitmap(),
        "burger" to ImageBitmap.imageResource(R.drawable.giftslotdrop_mark_bun).asAndroidBitmap(),
        "red" to ImageBitmap.imageResource(R.drawable.giftslotdrop_mark_swirl).asAndroidBitmap(),
    )
    val offer = ImageBitmap.imageResource(R.drawable.giftslotdrop_offer_bg).asAndroidBitmap()
    val giftBox = ImageBitmap.imageResource(R.drawable.giftslotdrop_gift_box).asAndroidBitmap()
    return remember(cards, marks, offer, giftBox) {
        GiftSlotDropAssets(
            cards = cards,
            marks = marks,
            offer = offer,
            giftBox = giftBox,
        )
    }
}

private fun offerDetailMask(simulation: GiftSlotDropSimulation, reducedMotion: Boolean): Int {
    if (simulation.phase != GiftSlotDropPhase.Offer && simulation.phase != GiftSlotDropPhase.Closing) {
        return 0
    }
    val milestones = giftSlotDropMilestones(reducedMotion)
    val feedDuration = if (reducedMotion) 0f else {
        Tokens.printFeeds * (Tokens.printFeedMs + Tokens.printFeedGap).toFloat()
    }
    val base = milestones.printStart + feedDuration + Tokens.printDetailsDelay
    var mask = 0
    repeat(4) { index ->
        if (simulation.elapsedSinceRelease() >= base + index * Tokens.printStagger) {
            mask = mask or (1 shl index)
        }
    }
    return mask
}

@Composable
private fun detailAlpha(mask: Int, index: Int): Float {
    val visible = mask and (1 shl index) != 0
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320),
        label = "gift detail $index",
    )
    return alpha
}

private fun hex(value: String): Color {
    return Color(android.graphics.Color.parseColor(value))
}
