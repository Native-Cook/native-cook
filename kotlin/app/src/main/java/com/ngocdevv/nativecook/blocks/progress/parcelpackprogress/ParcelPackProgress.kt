package com.ngocdevv.nativecook.blocks.progress.parcelpackprogress

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The film's period (s), as in parcelMotion.ts. */
const val LOOP = ParcelMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val ROW_H = 56f
private const val TOP = 22f
private const val HEAD_H = 34f
private const val SUB_H = 22f
private const val CARD_GAP = 16f
private const val BOTTOM = 16f
private const val PANEL_H = TOP + HEAD_H + SUB_H + CARD_GAP + ROW_H * ParcelMotion.STEP_N + BOTTOM
private const val MARK = 28f
private const val ROW_PAD = 14f
private const val LINK_GAP = 4f
private const val LINK_H = ROW_H - MARK - LINK_GAP * 2
private const val PILL_INSET = 5f
private const val GHOST_X = 0.64f
private const val GHOST_Y = 0.62f
private const val DOT = 46f

private fun rgb(hex: Int, a: Float = 1f): Color = Color(hex or (0xFF shl 24)).copy(alpha = a)

private fun type(size: Int, line: Int, color: Color, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, tabular: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * An order-tracking screen told as a 10-second film: a real 3D carton on a studio floor acts out the four steps of
 * the list below it. The flaps fall shut, tape runs over the seam, a label drops on, and the carton slides off as a
 * new open one slides in.
 *
 * Ported from the React Native block (react-native-filament): the same parcel.glb and light in Filament, with every
 * hinge driven from the loop's time. Tap a step to run the film to it; drag sideways on the carton to look round it.
 * 2 s after the last touch the film carries on. Reduced motion: a still at 5.75 s; taps set the step at once.
 */
@Composable
fun ParcelPackProgress(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { ParcelPackProgressController(autoPlay, reduced) }
    val view = LocalView.current

    // One clock for the Compose layers: Choreographer's frame time, read only in draw and layer lambdas.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(controller) {
        var ticks = controller.ticks
        var thuds = controller.thuds
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                if (controller.thuds != thuds) {
                    thuds = controller.thuds
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                if (controller.ticks != ticks) {
                    ticks = controller.ticks
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
    val film: () -> Double = remember(controller) {
        {
            controller.frame(frameNanos / 1e6)
            controller.t
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(ParcelMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(200f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            ParcelPackProgressStage(controller, Modifier.fillMaxSize())
            val ghost = remember { DoubleArray(3) }
            val dotShadow = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Parcel. Drag sideways to look round the parcel" }
                    .pointerInput(controller, w) {
                        val d = this.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dx = 0f
                            var dy = 0f
                            var active = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.positionChange()
                                dx += delta.x
                                dy += delta.y
                                if (!active) {
                                    if (abs(dy) / d > 28) break
                                    if (abs(dx) / d >= 8) {
                                        active = true
                                        controller.dragBegan(w.toDouble())
                                    }
                                }
                                if (active) {
                                    controller.dragMoved((dx / d).toDouble(), w.toDouble())
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (active) controller.dragEnded((tracker.calculateVelocity().x / d).toDouble(), w.toDouble())
                                    break
                                }
                            }
                            if (active) controller.dragFinalized()
                        }
                    },
            ) {
                val tt = film()
                val d = density.density
                // the stage fades in over the backdrop once the model is there
                val veil = 1f - controller.shown.toFloat()
                if (veil > 0f) drawRect(rgb(ParcelMotion.BACKDROP), alpha = veil)
                // the backdrop's colour over the top of the floor, so the status bar and the shop's name sit on one tone
                val shadeH = (top + 72) * d
                val b = rgb(ParcelMotion.BACKDROP)
                drawRect(
                    Brush.verticalGradient(
                        0f to b,
                        0.55f to b.copy(alpha = 0.6f),
                        1f to b.copy(alpha = 0f),
                        startY = 0f,
                        endY = shadeH,
                    ),
                    size = Size(size.width, shadeH),
                )
                // the film's finger
                ParcelMotion.ghostAt(tt, ghost)
                val a = (ghost[0] * 0.9).toFloat() * if (controller.filming) 1f else 0f
                if (a > 0.002f) {
                    val x = w * GHOST_X + (-ParcelMotion.ORBIT_FILM / ParcelMotion.ORBIT_SPAN * w * ghost[2]).toFloat()
                    val c = Offset(x * d, stageH * GHOST_Y * d)
                    val r = DOT / 2 * (1 - 0.16f * ghost[1].toFloat()) * d
                    drawIntoCanvas {
                        if (dotShadow.maskFilter == null) dotShadow.maskFilter = BlurMaskFilter(13f * d, BlurMaskFilter.Blur.NORMAL)
                        dotShadow.color = android.graphics.Color.argb((0.28f * 0.42f * a * 255).roundToInt(), 0x3B, 0x2F, 0x1E)
                        it.nativeCanvas.drawCircle(c.x, c.y + 3 * d, r, dotShadow)
                    }
                    drawCircle(Color.White, radius = r, center = c, alpha = 0.42f * a)
                    drawCircle(Color.White, radius = r - 0.75f * d, center = c, alpha = 0.85f * a, style = Stroke(1.5f * d))
                }
            }
        }

        Row(
            modifier = Modifier.offset(y = (top + 10).dp).fillMaxWidth().padding(horizontal = GUTTER.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(ParcelMotion.SHOP, style = type(17, 22, rgb(ParcelMotion.INK), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
            Text(ParcelMotion.ORDER, style = type(15, 20, rgb(ParcelMotion.INK, 0.62f), tabular = true), modifier = Modifier.alignByBaseline())
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color(0x3B, 0x2F, 0x1E).copy(alpha = 0.16f), offsetY = (-8).dp, blur = 48.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(ParcelMotion.PANEL))
                // the sheet takes its touches: the stage runs a little way under its corners
                .pointerInput(Unit) {}
                .drawWithContent {
                    drawContent()
                    val r = PANEL_R.dp.toPx()
                    clipRect(bottom = r) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.95f),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(size.width - 1f, r * 3),
                            cornerRadius = CornerRadius(r - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            ParcelPanel(width = min(w, 560f) - GUTTER * 2, film = film, onStep = controller::jumpTo)
        }
    }
}

@Composable
private fun ParcelPanel(width: Float, film: () -> Double, onStep: (Int) -> Unit) {
    val density = LocalDensity.current
    Column(Modifier.width(width.dp).padding(top = TOP.dp)) {
        Box(Modifier.fillMaxWidth().height(HEAD_H.dp)) {
            ParcelMotion.HEADLINES.forEachIndexed { j, line ->
                Text(
                    text = line,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = type(26, HEAD_H.toInt(), rgb(ParcelMotion.INK), FontWeight.Bold, tracking = -0.4f),
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        val a = ParcelMotion.headlineAlpha(j, film()).toFloat()
                        alpha = a
                        translationY = (1 - a) * 6 * this.density
                    },
                )
            }
        }
        Text(
            ParcelMotion.ARRIVES,
            style = type(15, SUB_H.toInt(), rgb(ParcelMotion.MUTE), tabular = true),
            modifier = Modifier.height(SUB_H.dp),
        )
        Box(
            Modifier
                .padding(top = CARD_GAP.dp)
                .fillMaxWidth()
                .height((ROW_H * ParcelMotion.STEP_N).dp)
                .cssBoxShadow(Color(0x3A, 0x2E, 0x1E).copy(alpha = 0.07f), offsetY = 3.dp, blur = 20.dp, radius = 20.dp)
                .background(rgb(ParcelMotion.CARD), RoundedCornerShape(20.dp))
                .border(with(density) { 1f.toDp() }, rgb(ParcelMotion.INK, 0.08f), RoundedCornerShape(20.dp)),
        ) {
            // the highlight behind the row in progress
            Box(
                Modifier
                    .offset(x = PILL_INSET.dp, y = PILL_INSET.dp)
                    .width((width - PILL_INSET * 2).dp)
                    .height((ROW_H - PILL_INSET * 2).dp)
                    .graphicsLayer {
                        val tt = film()
                        alpha = ParcelMotion.clamp01(ParcelMotion.activeAlpha(tt)).toFloat()
                        translationY = ParcelMotion.activeRow(tt).toFloat() * ROW_H * this.density
                    }
                    .background(rgb(ParcelMotion.GREEN_SOFT), RoundedCornerShape(15.dp)),
            )
            for (i in 0 until ParcelMotion.STEP_N - 1) StepLink(i, film)
            Column {
                for (i in 0 until ParcelMotion.STEP_N) StepRow(i, film, onStep)
            }
        }
    }
}

/** The line from step `index` to the next: it fills from the top as the step is done. */
@Composable
private fun StepLink(index: Int, film: () -> Double) {
    Box(
        Modifier
            .offset(x = (ROW_PAD + MARK / 2 - 1).dp, y = (index * ROW_H + ROW_H / 2 + MARK / 2 + LINK_GAP).dp)
            .size(2.dp, LINK_H.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(rgb(ParcelMotion.TRACK)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleY = ParcelMotion.doneAt(index, film()).toFloat()
                }
                .background(rgb(ParcelMotion.GREEN)),
        )
    }
}

/** One step: a ring while it waits, a ring with a beating dot while in progress, a check when done. */
@Composable
private fun StepRow(index: Int, film: () -> Double, onStep: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Show the parcel at this step",
                role = Role.Button,
            ) { onStep(index) }
            .padding(horizontal = ROW_PAD.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(MARK.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().border(2.dp, rgb(ParcelMotion.TRACK), CircleShape))
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val tt = film()
                        val near = ParcelMotion.clamp01(1 - abs(ParcelMotion.activeRow(tt) - index))
                        alpha = (near * ParcelMotion.clamp01(ParcelMotion.activeAlpha(tt)) * (1 - ParcelMotion.doneAt(index, tt))).toFloat()
                    }
                    .border(2.dp, rgb(ParcelMotion.GREEN), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer {
                            val s = 0.72f + 0.28f * ParcelMotion.pulseAt(film()).toFloat()
                            scaleX = s
                            scaleY = s
                        }
                        .background(rgb(ParcelMotion.GREEN), CircleShape),
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val tt = film()
                        alpha = ParcelMotion.doneAt(index, tt).toFloat()
                        val s = ParcelMotion.checkScale(index, tt).toFloat()
                        scaleX = s
                        scaleY = s
                    },
            ) {
                drawCircle(rgb(ParcelMotion.GREEN))
                val u = size.width / MARK
                val tick = Path().apply {
                    moveTo(9.2f * u, 14.4f * u)
                    lineTo(12.6f * u, 17.8f * u)
                    lineTo(19f * u, 10.6f * u)
                }
                drawPath(tick, Color.White, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(
            ParcelMotion.STEP_TITLES[index],
            style = type(17, 22, rgb(ParcelMotion.INK), FontWeight.SemiBold),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
                .graphicsLayer {
                    val tt = film()
                    val near = ParcelMotion.clamp01(1 - abs(ParcelMotion.activeRow(tt) - index)) * ParcelMotion.clamp01(ParcelMotion.activeAlpha(tt))
                    alpha = (0.46 + 0.54 * max(ParcelMotion.doneAt(index, tt), near)).toFloat()
                },
        )
        Text(
            ParcelMotion.STEP_TIMES[index],
            style = type(15, 20, rgb(ParcelMotion.MUTE), tabular = true),
            modifier = Modifier.graphicsLayer { alpha = ParcelMotion.doneAt(index, film()).toFloat() },
        )
    }
}
