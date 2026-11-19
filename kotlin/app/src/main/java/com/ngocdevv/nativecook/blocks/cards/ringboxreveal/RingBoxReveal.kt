package com.ngocdevv.nativecook.blocks.cards.ringboxreveal

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The film's period (s), as in ringMotion.ts. */
const val LOOP = RingMotion.LOOP

private const val PANEL_H = 262f
private const val HEADER_H = 42f
private const val FADE = 40f
private const val TOP_FADE = 14f
private const val GUTTER = 20f

private fun rgb(hex: Int, a: Float = 1f): Color = Color(hex or (0xFF shl 24)).copy(alpha = a)

/** The paper's colour at screen y (the screen gradient runs BG_TOP to BG_BOTTOM), with an alpha. */
private fun paperAt(y: Float, h: Float, a: Float): Color {
    val k = (y / h).coerceIn(0f, 1f)
    fun ch(s: Int): Float {
        val t = (RingMotion.BG_TOP shr s) and 255
        val b = (RingMotion.BG_BOTTOM shr s) and 255
        return (t + (b - t) * k).roundToInt() / 255f
    }
    return Color(ch(16), ch(8), ch(0), a)
}

/**
 * A jeweller's proposal planner told as an 11.5-second product tour around a real 3D ring box: the navy velvet box
 * on a soft paper floor with a gentle reflection; the lid snaps open, the solitaire throws glints and fire, the camera
 * comes round to the ring and goes in to the stone while every option is tried in the pickers and the slider, then
 * "Book a private viewing".
 *
 * Ported from the React Native block (react-native-filament + Skia): the same ring.glb and studio light in Filament
 * (RingBoxRevealScene), the same film (RingBoxRevealMotion), the Skia layers on a Canvas (RingBoxRevealLight).
 * Touch: tap the shut box to snap it open, tap the open box to go in to the stone and back; drag the lid up or down;
 * drag sideways once open to turn the box. 2 s after the last touch the camera pulls back, the lid closes and the
 * film restarts from the buyer's choices. Reduced motion: open on the ring shot, no film.
 */
@Composable
fun RingBoxReveal(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { RingBoxRevealController(autoPlay, reduced) }
    val light = remember { RingBoxRevealLight() }
    val view = LocalView.current

    // One clock for the Compose layers: Choreographer's frame time, read only in draw lambdas.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    var pickerMetal by remember { mutableIntStateOf(0) }
    var pickerStone by remember { mutableIntStateOf(0) }
    var sliderCarat by remember { mutableDoubleStateOf(RingMotion.CARAT_MODEL) }
    var booked by remember { mutableStateOf(false) }
    val uiFrame = remember { RingFrame() }
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { nanos ->
                frameNanos = nanos
                controller.frame(nanos / 1e9, uiFrame)
                pickerMetal = uiFrame.pickerMetal
                pickerStone = uiFrame.pickerStone
                sliderCarat = uiFrame.sliderCarat
                booked = uiFrame.booked
            }
        }
    }

    // Haptics: the snap (half way up), the lid landing shut, the film's press of the booking button.
    val haptics = remember(controller) {
        object {
            var lastOpen = Double.NaN
            var lastBooked = false
            fun onFrame(f: RingFrame) {
                if (controller.reduced) return
                val o = f.pose.open
                if (!lastOpen.isNaN()) {
                    if (lastOpen < 0.5 && o >= 0.5) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (lastOpen > 0.01 && o <= 0.01) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                lastOpen = o
                if (f.filmPlaying && f.booked && !lastBooked) view.performHapticFeedback(confirmHaptic())
                lastBooked = f.booked
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(rgb(RingMotion.BG_TOP), rgb(RingMotion.BG_BOTTOM)))),
    ) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val panelTop = h - (bottom + 12 + PANEL_H)
        val stageTop = top + HEADER_H
        val stageH = max(120f, panelTop - 8 - stageTop)
        val paperTop = -stageTop

        Box(
            modifier = Modifier
                .offset(y = stageTop.dp)
                .width(w.dp)
                .height(stageH.dp)
                .clipToBounds(),
        ) {
            val drawFrame = remember { RingFrame() }
            Canvas(Modifier.fillMaxSize()) {
                controller.frame(frameNanos / 1e9, drawFrame)
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(density.density, density.density)
                    light.backdrop(nc, w, stageH, drawFrame.pose, paperTop, h, controller.stageReady)
                    nc.restore()
                }
            }
            RingBoxRevealStage(controller, onFrame = haptics::onFrame, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                controller.frame(frameNanos / 1e9, drawFrame)
                if (controller.stageReady) drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(density.density, density.density)
                    light.floorFade(nc, w, stageH, drawFrame.pose, paperTop, h)
                    light.sparkle(nc, w, stageH, drawFrame.pose)
                    nc.restore()
                }
            }
            // the window's edges fade into the paper, so a close shot never runs behind the type
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TOP_FADE.dp)
                    .background(Brush.verticalGradient(listOf(paperAt(stageTop, h, 1f), paperAt(stageTop + TOP_FADE, h, 0f)))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(FADE.dp)
                    .background(
                        Brush.verticalGradient(listOf(paperAt(stageTop + stageH - FADE, h, 0f), paperAt(stageTop + stageH, h, 1f))),
                    ),
            )
        }

        // The touch area over the stage: a tap, or a drag on the lid / sideways.
        Box(
            modifier = Modifier
                .offset(y = stageTop.dp)
                .width(w.dp)
                .height(stageH.dp)
                .semantics { contentDescription = "Ring box" }
                .pointerInput(controller) {
                    val slop = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = down.uptimeMillis
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var total = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += change.positionChange()
                            val now = System.nanoTime() / 1e9
                            if (!dragging && total.getDistance() > slop) {
                                dragging = true
                                controller.dragBegan(now)
                            }
                            if (dragging) {
                                change.consume()
                                controller.dragChanged(now, (total.x / density.density).toDouble(), (total.y / density.density).toDouble())
                            }
                            if (!change.pressed) {
                                if (dragging) {
                                    val v = tracker.calculateVelocity()
                                    controller.dragEnded(now, (v.x / density.density).toDouble(), (v.y / density.density).toDouble())
                                } else if (change.uptimeMillis - downTime <= 300) {
                                    controller.tap(now)
                                }
                                break
                            }
                        }
                    }
                },
        )

        // The atelier's name.
        Row(
            modifier = Modifier
                .offset(y = (top + 8).dp)
                .fillMaxWidth()
                .padding(horizontal = GUTTER.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                RingMotion.ATELIER,
                color = rgb(RingMotion.INK),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Text("Proposal planner", color = rgb(RingMotion.MUTE), fontSize = 13.sp, modifier = Modifier.alignByBaseline())
        }

        // The ring's name, price and line; the options and the booking button.
        val inner = min(w, 560f) - GUTTER * 2
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = GUTTER.dp, bottom = (bottom + 12).dp)
                .width(inner.dp),
        ) {
            val select: () -> Unit = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    RingMotion.RING_NAME,
                    color = rgb(RingMotion.INK),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Text(
                    RingMotion.priceOf(pickerMetal, pickerStone, sliderCarat),
                    color = rgb(RingMotion.INK),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                "${"%.2f".format(java.util.Locale.US, sliderCarat)} ct ${RingMotion.STONES[pickerStone].name.lowercase()} in " +
                    RingMotion.METALS[pickerMetal].name.lowercase(),
                color = rgb(RingMotion.MUTE),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionRow("Metal") {
                    Segments(RingMotion.METALS.map { it.short }, pickerMetal) { i ->
                        if (i != pickerMetal) {
                            select()
                            controller.pickMetal(i, System.nanoTime() / 1e9)
                        }
                    }
                }
                OptionRow("Stone") {
                    Segments(RingMotion.STONES.map { it.name }, pickerStone) { i ->
                        if (i != pickerStone) {
                            select()
                            controller.pickStone(i, System.nanoTime() / 1e9)
                        }
                    }
                }
                OptionRow("Carat") {
                    val current by rememberUpdatedState(sliderCarat)
                    CaratSlider(sliderCarat, Modifier.weight(1f)) { v ->
                        val r = (v * 4).roundToInt() / 4.0
                        if (r != current) {
                            select()
                            controller.pickCarat(r, System.nanoTime() / 1e9)
                        }
                    }
                    Text(
                        "${"%.2f".format(java.util.Locale.US, sliderCarat)} ct",
                        color = rgb(RingMotion.INK),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.width(64.dp),
                    )
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BookButton(booked) {
                        view.performHapticFeedback(confirmHaptic())
                        controller.book(System.nanoTime() / 1e9)
                    }
                }
            }
        }
    }
}

private fun confirmHaptic(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

@Composable
private fun OptionRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = rgb(RingMotion.MUTE), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(56.dp))
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segments(items: List<String>, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x24767680))
            .padding(2.dp),
    ) {
        items.forEachIndexed { i, name ->
            val on = value == i
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(14.dp)).background(Color.White, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    color = rgb(RingMotion.INK),
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The capsule button: a calendar, or a check once the viewing is requested. */
@Composable
private fun BookButton(booked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(rgb(RingMotion.NAVY))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(17.dp)) {
            val s = size.minDimension
            val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
            if (booked) {
                drawLine(Color.White, Offset(s * 0.12f, s * 0.52f), Offset(s * 0.4f, s * 0.8f), s * 0.11f, StrokeCap.Round)
                drawLine(Color.White, Offset(s * 0.4f, s * 0.8f), Offset(s * 0.9f, s * 0.2f), s * 0.11f, StrokeCap.Round)
            } else {
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(s * 0.06f, s * 0.14f),
                    size = androidx.compose.ui.geometry.Size(s * 0.88f, s * 0.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.16f),
                    style = stroke,
                )
                drawLine(Color.White, Offset(s * 0.06f, s * 0.36f), Offset(s * 0.94f, s * 0.36f), s * 0.09f)
                for (k in 0..2) for (r in 0..1) {
                    drawCircle(Color.White, s * 0.05f, Offset(s * (0.3f + 0.2f * k), s * (0.56f + 0.2f * r)))
                }
            }
        }
        Text(
            if (booked) "Viewing requested" else "Book a private viewing",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The carat slider: a thin navy track and a white capsule thumb, stepping by 0.25 ct. */
@Composable
private fun CaratSlider(value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    val onChangeNow by rememberUpdatedState(onChange)
    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .semantics { contentDescription = "Carat" }
            .pointerInput(Unit) {
                val thumbW = 38.dp.toPx()
                fun valueAt(x: Float): Double {
                    val k = ((x - thumbW / 2) / (size.width - thumbW)).coerceIn(0f, 1f)
                    return RingMotion.CARAT_MIN + (RingMotion.CARAT_MAX - RingMotion.CARAT_MIN) * k
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChangeNow(valueAt(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        onChangeNow(valueAt(change.position.x))
                    }
                }
            },
    ) {
        val k = ((value - RingMotion.CARAT_MIN) / (RingMotion.CARAT_MAX - RingMotion.CARAT_MIN)).toFloat().coerceIn(0f, 1f)
        val thumbW = 38.dp
        val travel = maxWidth - thumbW
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x29787880)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .width(travel * k + thumbW / 2 - 4.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(rgb(RingMotion.NAVY)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = travel * k)
                .width(thumbW)
                .height(24.dp)
                .shadow(3.dp, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp)),
        )
    }
}
