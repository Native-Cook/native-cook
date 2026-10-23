package com.ngocdevv.nativecook.blocks.buttons.ordertruckbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay

private const val WIDTH = 212f
private const val HEIGHT = 56f
private const val AUTO_PAUSE_MS = 1300L

// The truck, seen from above and drawn with its cargo box's rear-left corner at the origin.
private const val BOX_W = 52f
private const val BOX_H = 37f
private const val CAB_X = BOX_W + 1f
private const val CAB_W = 21f
private const val TRUCK_W = CAB_X + CAB_W
private const val DOOR = BOX_H / 2
private const val DOOR_SWING = 115f
private const val PARCEL = 19f
private const val DASH = 7f
private const val DASH_GAP = 5.5f

// How fast the road runs under the truck, in dp per second.
private const val ROAD_SPEED = 48f

// Where the truck's rear sits along the pill.
private const val X_OFF = WIDTH + 8f
private const val X_REST = (WIDTH - TRUCK_W) / 2
private const val X_AHEAD = 120f
private const val X_BACK = 15f
private const val PARCEL_OFF = -PARCEL - 3f
private const val PARCEL_REST = 16f

// The whole story is a function of one clock (seconds since the tap).
private val T_LABEL_OUT = 0f..0.25f
private val T_ENTER = 0.15f..1.15f
private val T_PARCEL_IN = 0.2f..0.55f
private val T_DOORS_OPEN = 0.45f..0.9f
private val T_LOAD = 1.4f..2.0f
private val T_DOORS_CLOSE = 2.0f..2.75f
private val T_AHEAD = 3.2f..3.9f
private val T_LIGHTS = 3.2f..3.5f
private val T_ROAD_IN = 3.3f..3.7f
private val T_DRIFT = 4.2f..6.1f
private val T_EXIT = 6.1f..6.7f
private val T_ROAD_OUT = 6.45f..6.85f
private val T_DONE_IN = 7.0f..7.3f
private val T_CHECK = 7.35f..7.65f
private val T_DONE_OUT = 9.0f..9.25f
private val T_LABEL_IN = 9.25f..9.6f
private const val TOTAL = 9.6f

private val PillColor = Color(0xFF1C212B)
private val CabBlue = Color(0xFF2B5DF5)
private val RoofBlue = Color(0xFF7096F8)
private val Lamp = Color(0xFFF2D24E)
private val CheckGreen = Color(0xFF2FCF8E)

private val LabelStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)

private fun seg(t: Float, range: ClosedFloatingPointRange<Float>) =
    ((t - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

private fun easeOut(u: Float) = 1f - (1f - u) * (1f - u) * (1f - u)
private fun easeIn(u: Float) = u * u * u
private fun easeInOut(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f

// Sets off gently, then spends most of the trip slowing down.
private fun easeDrift(u: Float): Float {
    val s = u * u * (3f - 2f * u)
    return 1f - (1f - s) * (1f - s)
}

private fun truckX(t: Float) = X_OFF +
    (X_REST - X_OFF) * easeOut(seg(t, T_ENTER)) +
    (X_AHEAD - X_REST) * easeInOut(seg(t, T_AHEAD)) +
    (X_BACK - X_AHEAD) * easeDrift(seg(t, T_DRIFT)) +
    (X_OFF - X_BACK) * easeIn(seg(t, T_EXIT))

/** Paths that never change, built once. All in the truck's own coordinates (see [BOX_W]). */
private class TruckPaths {
    private val top = -BOX_H / 2
    private val bottom = BOX_H / 2

    val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, WIDTH, HEIGHT, CornerRadius(HEIGHT / 2))) }

    // -1 is the top light, +1 the bottom one: the same beam mirrored about the truck's axis.
    val beams = listOf(-1f, 1f).map { side ->
        val edge = side * bottom
        Path().apply {
            moveTo(TRUCK_W, edge - side * 5f)
            lineTo(TRUCK_W + 41f, edge + side * 9f)
            lineTo(TRUCK_W + 41f, edge - side * 15f)
            lineTo(TRUCK_W, edge - side * 9f)
            close()
        }
    }
    val shell = Path().apply {
        moveTo(CAB_X, top + 0.5f)
        lineTo(CAB_X + 11f, top + 0.5f)
        quadraticTo(TRUCK_W, top + 0.5f, TRUCK_W, top + 11f)
        lineTo(TRUCK_W, bottom - 11f)
        quadraticTo(TRUCK_W, bottom - 0.5f, CAB_X + 11f, bottom - 0.5f)
        lineTo(CAB_X, bottom - 0.5f)
        close()
    }
    val windscreen = Path().apply {
        moveTo(CAB_X + 6.5f, top + 3.5f)
        lineTo(CAB_X + 10.5f, top + 3.5f)
        quadraticTo(TRUCK_W - 3.5f, top + 4f, TRUCK_W - 3.5f, top + 12f)
        lineTo(TRUCK_W - 3.5f, bottom - 12f)
        quadraticTo(TRUCK_W - 3.5f, bottom - 4f, CAB_X + 10.5f, bottom - 3.5f)
        lineTo(CAB_X + 6.5f, bottom - 3.5f)
        close()
    }
    val road = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH_GAP))
}

/**
 * "Complete Order" pill: on tap a parcel is loaded into a truck that drives off along a dashed
 * road, then the pill confirms with "Order Placed ✓" and returns to rest.
 *
 * @param onClick Called when the order is placed (on tap, before the truck sets off).
 * @param autoPlay Place an order by itself every few seconds. Tapping still works while idle.
 */
@Composable
fun OrderTruckButton(
    label: String = "Complete Order",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    doneLabel: String = "Order Placed",
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // Seconds since the tap. It ends where it began (idle label showing), so the rewind is invisible.
    val clock = remember { Animatable(0f) }

    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        busy = true
        clock.snapTo(0f)
        if (reducedMotion) {
            // Reduced motion skips the delivery: the label simply cross-fades to the confirmation and back.
            val skipFrom = T_DONE_IN.start - 0.1f
            clock.animateTo(T_LABEL_OUT.endInclusive, tween((T_LABEL_OUT.endInclusive * 1000).toInt(), easing = LinearEasing))
            clock.snapTo(skipFrom)
            clock.animateTo(TOTAL, tween(((TOTAL - skipFrom) * 1000).toInt(), easing = LinearEasing))
        } else {
            clock.animateTo(TOTAL, tween((TOTAL * 1000).toInt(), easing = LinearEasing))
        }
        clock.snapTo(0f)
        busy = false
    }

    LaunchedEffect(autoPlay, reducedMotion, busy, pressed) {
        if (!autoPlay || reducedMotion || busy || pressed) return@LaunchedEffect
        delay(AUTO_PAUSE_MS)
        busy = true
        run++
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paths = remember { TruckPaths() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                if (!busy) {
                    onClick()
                    busy = true
                    run++
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Draw in dp, exactly like the React Native and SwiftUI versions.
            scale(density, density, pivot = Offset.Zero) { drawScene(paths, clock.value, still = reducedMotion) }
        }
        BasicText(
            text = label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - seg(clock.value, T_LABEL_OUT) + seg(clock.value, T_LABEL_IN)
            },
        )
        Row(
            modifier = Modifier.graphicsLayer { alpha = seg(clock.value, T_DONE_IN) - seg(clock.value, T_DONE_OUT) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = doneLabel, style = LabelStyle, maxLines = 1)
            Canvas(Modifier.size(16.dp)) {
                scale(density, density, pivot = Offset.Zero) { drawCheck(easeOut(seg(clock.value, T_CHECK))) }
            }
        }
    }
}

// The tick is two straight strokes, drawn up to `progress` of their combined length.
private fun DrawScope.drawCheck(progress: Float) {
    if (progress <= 0f) return
    val a = Offset(2.5f, 8.5f)
    val b = Offset(6.2f, 12f)
    val c = Offset(13.5f, 4.2f)
    val first = hypot(b.x - a.x, b.y - a.y)
    val second = hypot(c.x - b.x, c.y - b.y)
    val drawn = progress * (first + second)
    val path = Path().apply {
        moveTo(a.x, a.y)
        val u = min(1f, drawn / first)
        lineTo(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
        if (drawn > first) {
            val v = (drawn - first) / second
            lineTo(b.x + (c.x - b.x) * v, b.y + (c.y - b.y) * v)
        }
    }
    drawPath(path, CheckGreen, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawScene(paths: TruckPaths, t: Float, still: Boolean) {
    drawRoundRect(PillColor, Offset.Zero, Size(WIDTH, HEIGHT), CornerRadius(HEIGHT / 2))
    if (still || t <= 0f) return

    val cy = HEIGHT / 2
    clipPath(paths.pill) {
        // Road: dashes running right to left under the truck.
        val roadAlpha = seg(t, T_ROAD_IN) - seg(t, T_ROAD_OUT)
        if (roadAlpha > 0f) {
            val period = DASH + DASH_GAP
            val shift = (max(0f, t - T_ROAD_IN.start) * ROAD_SPEED) % period
            drawLine(
                color = rgba(255, 255, 255, 0.92f),
                start = Offset(-shift, cy),
                end = Offset(WIDTH + period, cy),
                strokeWidth = 1.4f,
                pathEffect = paths.road,
                alpha = roadAlpha,
            )
        }

        // Once the parcel is under the cargo box it stays hidden, or the truck would drive off and leave it behind.
        if (t > T_PARCEL_IN.start && t < T_LOAD.endInclusive) {
            val x = PARCEL_OFF +
                (PARCEL_REST - PARCEL_OFF) * easeOut(seg(t, T_PARCEL_IN)) +
                (X_REST + 8f - PARCEL_REST) * easeIn(seg(t, T_LOAD))
            val y = cy - PARCEL / 2
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFEDD09A), Color(0xFFDCB777)), startY = y, endY = y + PARCEL),
                topLeft = Offset(x, y),
                size = Size(PARCEL, PARCEL),
                cornerRadius = CornerRadius(2.5f),
            )
            drawLine(Color(0xFFC9A05C), Offset(x, cy), Offset(x + PARCEL, cy), strokeWidth = 1.2f)
        }

        translate(truckX(t), cy) { drawTruck(paths, t) }
    }
}

private fun DrawScope.drawTruck(paths: TruckPaths, t: Float) {
    val top = -BOX_H / 2
    val bottom = BOX_H / 2

    // Headlight beams, behind the cab.
    val lights = seg(t, T_LIGHTS)
    if (lights > 0f) {
        val beam = Brush.horizontalGradient(
            listOf(rgba(244, 214, 84, 0.8f), rgba(214, 200, 130, 0.16f)),
            startX = TRUCK_W,
            endX = TRUCK_W + 41f,
        )
        paths.beams.forEach { drawPath(it, beam, alpha = lights) }
    }

    // Cab: blue shell, lighter roof slab, dark windscreen with two reflections.
    drawPath(paths.shell, CabBlue)
    drawPath(paths.windscreen, PillColor)
    val glint = rgba(255, 255, 255, 0.2f)
    drawLine(glint, Offset(CAB_X + 8f, -3f), Offset(CAB_X + 15.5f, -9f), strokeWidth = 1.3f)
    drawLine(glint, Offset(CAB_X + 8f, 1.5f), Offset(CAB_X + 15.5f, -4.5f), strokeWidth = 1.3f)
    drawRoundRect(RoofBlue, Offset(CAB_X, top + 0.5f), Size(6.5f, BOX_H - 1f), CornerRadius(1.2f))
    drawRoundRect(Lamp, Offset(TRUCK_W - 1.2f, top + 3f), Size(2.6f, 7f), CornerRadius(0.8f))
    drawRoundRect(Lamp, Offset(TRUCK_W - 1.2f, bottom - 10f), Size(2.6f, 7f), CornerRadius(0.8f))

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFF7F9FD), Color(0xFFCDD6EA)), startY = top, endY = bottom),
        topLeft = Offset(0f, top),
        size = Size(BOX_W, BOX_H),
        cornerRadius = CornerRadius(2.5f),
    )

    // Rear doors, hinged on the box's back corners. Shut, they are simply its rear edge.
    val swing = DOOR_SWING * (easeOut(seg(t, T_DOORS_OPEN)) - easeInOut(seg(t, T_DOORS_CLOSE)))
    val doorAlpha = min(1f, Math.toRadians(swing.toDouble()).toFloat() * 12f)
    if (doorAlpha > 0f) {
        for (side in listOf(-1f, 1f)) {
            val hinge = Offset(0f, side * bottom)
            rotate(-side * swing, pivot = hinge) {
                drawLine(
                    color = Color.White,
                    start = hinge,
                    end = Offset(0f, hinge.y - side * DOOR),
                    strokeWidth = 1.3f,
                    cap = StrokeCap.Round,
                    alpha = doorAlpha,
                )
            }
        }
    }
}
