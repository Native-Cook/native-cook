package com.ngocdevv.nativecook.blocks.pickers.combinationsafedial

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The film's period (s), as in safeMotion.ts. */
const val LOOP = SafeMotion.LOOP

private const val GUTTER = 20f
private const val PANEL_R = 28f
private const val TOP = 16f
private const val HEAD_H = 32f
private const val GAP = 12f
private const val SLOT_H = 64f
private const val ROW_H = 38f
private const val BOTTOM = 14f
private const val PANEL_H = TOP + HEAD_H + GAP + SLOT_H + GAP + ROW_H * 3 + BOTTOM

/** The film's finger circles the dial at this radius (world metres) from this starting angle (deg clockwise). */
private const val GHOST_RADIUS = 0.066
private const val GHOST_START = 58.0
private const val DOT = 44f
private const val PAN_SLOP = 2f

private val DIAL_WORLD_Y = (SafeMotion.DOOR_HINGE[1] + SafeMotion.DIAL_PIVOT[1]).toDouble()
private val HAIR_COLOR = Color(29, 33, 30).copy(alpha = 0.09f)

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

private fun twoDigits(v: Int): String = if (v < 10) "0$v" else "$v"

private fun slotText(i: Int, count: Int, entry: Int, dial: Double): String = when {
    i < count -> twoDigits(entry)
    i == count -> twoDigits(SafeMotion.dialNumber(dial))
    else -> "--"
}

/**
 * Unlocking a password vault as a 10-second film: a real 3D wall safe (bottle-green housing, round brushed-steel door,
 * numbered dial, T handle, eight bolts) on a studio sweep above the vault's sheet. The dial turns to three numbers; on
 * the third the handle swings, the bolts draw in and the door opens towards the camera with warm light in the gap.
 *
 * Ported from the React Native block (react-native-filament): the same safe.glb and light in Filament, every moving
 * node posed from the loop's values (no imported animation). Drag round the dial anywhere on the stage to turn it: on
 * release its speed carries it on and a spring lands it on a number, which is entered; the right code opens the safe,
 * a wrong one shakes it. The Lock button is Material 3. 2 s after the last touch the door shuts, the dial goes home and
 * 1.25 s later the film restarts. Reduced motion: a still of the open safe with the code entered.
 */
@Composable
fun CombinationSafeDial(
    modifier: Modifier = Modifier,
    code: List<Int> = SafeMotion.DEMO_CODE.toList(),
    autoPlay: Boolean = true,
    onUnlock: () -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced, code) { CombinationSafeDialController(autoPlay, reduced, code.toIntArray()) }
    val view = LocalView.current
    val unlocked by rememberUpdatedState(onUnlock)

    // One clock for the Compose layers, read only in draw and layer lambdas. The sheet's few discrete states (the three
    // slots' digits, whether Lock shows) change only as numbers pass, and are read inside the sheet only.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val slots = remember(controller) { List(3) { i -> mutableStateOf(slotText(i, controller.count, controller.entries[i], controller.dialNow)) } }
    val lockable = remember(controller) { mutableStateOf(controller.canLock) }
    LaunchedEffect(controller) {
        var ticks = controller.ticks
        var rights = controller.rights
        var wrongs = controller.wrongs
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                for (i in 0 until 3) {
                    val s = slotText(i, controller.count, controller.entries[i], controller.dialNow)
                    if (slots[i].value != s) slots[i].value = s
                }
                if (lockable.value != controller.canLock) lockable.value = controller.canLock
                if (controller.ticks != ticks) {
                    ticks = controller.ticks
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                if (controller.rights != rights) {
                    rights = controller.rights
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                    unlocked()
                }
                if (controller.wrongs != wrongs) {
                    wrongs = controller.wrongs
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
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

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(SafeMotion.BACKDROP))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value
        val stageH = max(240f, h - PANEL_H - bottom + PANEL_R)

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            CombinationSafeDialStage(controller, Modifier.fillMaxSize())
            val g = remember { DoubleArray(2) }
            val p0 = remember { DoubleArray(2) }
            val p1 = remember { DoubleArray(2) }
            val p2 = remember { DoubleArray(2) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Safe dial. Drag round the dial to turn it" }
                    .pointerInput(controller, w, stageH) {
                        val d = this.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var active = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val x = (change.position.x / d).toDouble()
                                val y = (change.position.y / d).toDouble()
                                if (!active && change.pressed) {
                                    val moved = change.position - down.position
                                    if (hypot(moved.x, moved.y) / d >= PAN_SLOP) {
                                        active = true
                                        controller.panStart(x, y, w.toDouble(), stageH.toDouble())
                                    }
                                }
                                if (active) {
                                    if (change.positionChange() != Offset.Zero) controller.panMove(x, y)
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (active) {
                                        val v = tracker.calculateVelocity()
                                        controller.panEnd(x, y, (v.x / d).toDouble(), (v.y / d).toDouble())
                                    }
                                    break
                                }
                            }
                            if (active) controller.panFinalize()
                        }
                    },
            ) {
                val tt = film()
                val px = density.density
                val veil = 1f - controller.shown.toFloat()
                if (veil > 0f) drawRect(rgb(SafeMotion.BACKDROP), alpha = veil)
                drawGapLight(controller.openNow, w.toDouble(), stageH.toDouble(), px, p0, p1, p2)
                // the film's finger, circling the dial with it
                if (controller.filming) {
                    SafeMotion.dialGhost(tt, g)
                    val a = (g[0] * 0.9).toFloat()
                    if (a > 0.002f) {
                        SafeMotion.project(0.0, DIAL_WORLD_Y, SafeMotion.DIAL_FACE_Z, 0.0, w.toDouble(), stageH.toDouble(), p0)
                        SafeMotion.project(GHOST_RADIUS, DIAL_WORLD_Y, SafeMotion.DIAL_FACE_Z, 0.0, w.toDouble(), stageH.toDouble(), p1)
                        val r = abs(p1[0] - p0[0])
                        val th = (GHOST_START - controller.dialNow * SafeMotion.DEG_PER_NUMBER) * PI / 180
                        drawGhost((p0[0] + r * sin(th)).toFloat() * px, (p0[1] - r * cos(th)).toFloat() * px, a, g[1].toFloat())
                    }
                }
                // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
                val shadeH = (top + 64) * px
                val b = rgb(SafeMotion.BACKDROP)
                drawRect(
                    Brush.verticalGradient(0f to b, 0.55f to b.copy(alpha = 0.6f), 1f to b.copy(alpha = 0f), startY = 0f, endY = shadeH),
                    size = Size(size.width, shadeH),
                )
            }
        }

        Text(
            SafeMotion.APP,
            style = type(17, 22, rgb(SafeMotion.INK), FontWeight.SemiBold),
            modifier = Modifier.offset(x = GUTTER.dp, y = (top + 10).dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((PANEL_H + bottom).dp)
                .cssBoxShadow(Color(38, 32, 20).copy(alpha = 0.2f), offsetY = (-8).dp, blur = 24.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(SafeMotion.PANEL))
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
            SafePanel(min(w, 560f) - GUTTER * 2, controller, slots, lockable, film) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                controller.relock()
            }
        }
    }
}

/** Warm light from inside the safe, standing in the gap between the door's free edge and the bore (dp maths, px out). */
private fun DrawScope.drawGapLight(open: Double, width: Double, height: Double, px: Float, edge: DoubleArray, bore: DoubleArray, top: DoubleArray) {
    val alpha = (SafeMotion.seg(open, 0.05, 0.42) * 0.82).toFloat()
    if (alpha <= 0.002f) return
    val hinge = SafeMotion.DOOR_HINGE
    val a = open * SafeMotion.DOOR_DEG * PI / 180
    val reach = SafeMotion.DOOR_CENTRE[0] + SafeMotion.DOOR_RADIUS
    SafeMotion.project(hinge[0] + reach * cos(a), hinge[1].toDouble(), hinge[2] + reach * sin(a), open, width, height, edge)
    SafeMotion.project(SafeMotion.BORE_RADIUS, hinge[1].toDouble(), SafeMotion.FRONT_Z, open, width, height, bore)
    SafeMotion.project(SafeMotion.BORE_RADIUS, hinge[1] + SafeMotion.BORE_RADIUS, SafeMotion.FRONT_Z, open, width, height, top)
    val span = abs(bore[1] - top[1]) * 2.5
    val gw = max(40.0, abs(edge[0] - bore[0]) * 2.1)
    val cx = ((edge[0] + bore[0]) / 2).toFloat() * px
    val cy = bore[1].toFloat() * px
    val glow = rgb(SafeMotion.GLOW)
    // an ellipse gw x span with its own elliptical radial gradient: a unit disc of radius 100, scaled
    withTransform({
        translate(cx, cy)
        scale((gw * px / 200).toFloat(), (span * px / 200).toFloat(), pivot = Offset.Zero)
    }) {
        drawCircle(
            Brush.radialGradient(
                0f to glow.copy(alpha = 0.85f),
                0.38f to glow.copy(alpha = 0.34f),
                0.72f to glow.copy(alpha = 0.08f),
                1f to glow.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 100f,
            ),
            radius = 100f,
            center = Offset.Zero,
            alpha = alpha,
        )
    }
}

/** The film's finger: a 44 pt disc of pale cream with a rim, pressed a little smaller. */
private fun DrawScope.drawGhost(x: Float, y: Float, opacity: Float, pressed: Float) {
    val d = density
    val c = Offset(x, y)
    val k = 1 - 0.16f * pressed
    val r = DOT / 2 * k * d
    val cream = Color(250, 246, 236)
    drawCircle(cream, radius = r, center = c, alpha = 0.3f * opacity)
    drawCircle(cream, radius = r - 0.75f * k * d, center = c, alpha = 0.75f * opacity, style = Stroke(1.5f * k * d))
}

@Composable
private fun SafePanel(
    inner: Float,
    controller: CombinationSafeDialController,
    slots: List<MutableState<String>>,
    lockable: MutableState<Boolean>,
    film: () -> Double,
    onLock: () -> Unit,
) {
    val hair = with(LocalDensity.current) { 1f.toDp() }
    Column(Modifier.width(inner.dp).padding(top = TOP.dp)) {
        Row(
            Modifier.fillMaxWidth().height(HEAD_H.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(SafeMotion.VAULT, style = type(20, 26, rgb(SafeMotion.INK), FontWeight.Bold, tracking = -0.3f))
            if (lockable.value) {
                LockButton(onLock)
            } else {
                SafeStatus(controller, film)
            }
        }
        Row(Modifier.padding(top = GAP.dp).fillMaxWidth().height(SLOT_H.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 3) Slot(i, slots[i], controller, film, hair, Modifier.weight(1f))
        }
        Column(
            Modifier
                .padding(top = GAP.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(rgb(SafeMotion.CARD))
                .border(hair, HAIR_COLOR, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
        ) {
            for (i in SafeMotion.ROW_LABELS.indices) {
                VaultRow(SafeMotion.ROW_LABELS[i], SafeMotion.ROW_VALUES[i], i == SafeMotion.ROW_LABELS.lastIndex, controller, film, hair)
            }
        }
    }
}

/** "Locked", "Unlocked" and "Wrong code" in one place, crossfading with the door and the wrong code's flash. */
@Composable
private fun SafeStatus(controller: CombinationSafeDialController, film: () -> Double) {
    Box(Modifier.size(120.dp, 22.dp), contentAlignment = Alignment.TopEnd) {
        val base = type(15, 22, rgb(SafeMotion.MUTE), FontWeight.Medium)
        Text(
            "Locked",
            style = base,
            modifier = Modifier.graphicsLayer {
                film()
                alpha = ((1 - SafeMotion.seg(controller.openNow, 0.1, 0.5)) * (1 - controller.wrongNow)).toFloat().coerceIn(0f, 1f)
            },
        )
        Text(
            "Unlocked",
            style = base.copy(color = rgb(SafeMotion.GREEN), fontWeight = FontWeight.SemiBold),
            modifier = Modifier.graphicsLayer {
                film()
                alpha = SafeMotion.seg(controller.openNow, 0.3, 0.8).toFloat()
            },
        )
        Text(
            "Wrong code",
            style = base.copy(color = rgb(SafeMotion.RED), fontWeight = FontWeight.SemiBold),
            modifier = Modifier.graphicsLayer {
                film()
                alpha = controller.wrongNow.toFloat().coerceIn(0f, 1f)
            },
        )
    }
}

/** Locks the vault again: a Material 3 filled button in the vault's green (the system's button on iOS). */
@Composable
private fun LockButton(onLock: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onLock,
            modifier = Modifier.height(32.dp).semantics { contentDescription = "Lock the vault" },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = rgb(SafeMotion.GREEN), contentColor = rgb(SafeMotion.PANEL)),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Lock", style = type(15, 20, rgb(SafeMotion.PANEL), FontWeight.SemiBold))
        }
    }
}

/** One number of the combination: the live dial while it is the next one, its entry once in, "--" after. */
@Composable
private fun Slot(index: Int, text: MutableState<String>, controller: CombinationSafeDialController, film: () -> Double, hair: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val fill = Color(31, 66, 56).copy(alpha = 0.07f)
    val bad = Color(179, 58, 43).copy(alpha = 0.14f)
    val brass = rgb(SafeMotion.BRASS)
    Box(
        modifier
            .height(SLOT_H.dp)
            .clip(shape)
            .background(rgb(SafeMotion.CARD))
            .border(hair, HAIR_COLOR, shape)
            .drawBehind {
                film()
                val count = controller.count
                if (index < count) drawRect(fill)
                val wrong = controller.wrongNow.toFloat().coerceIn(0f, 1f)
                if (wrong > 0f) drawRect(bad, alpha = wrong)
                if (count == index) {
                    val sw = 2.dp.toPx()
                    val r = 16.dp.toPx()
                    drawRoundRect(brass, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), cornerRadius = CornerRadius(r - sw / 2), style = Stroke(sw))
                }
            }
            .semantics { contentDescription = "Number ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.value,
            style = type(32, 38, rgb(SafeMotion.INK), FontWeight.Bold, tabular = true).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VaultRow(label: String, value: String, last: Boolean, controller: CombinationSafeDialController, film: () -> Double, hair: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height((ROW_H - 0.34f).dp)
            .drawBehind {
                if (!last) drawRect(HAIR_COLOR, topLeft = Offset(0f, size.height - hair.toPx()), size = Size(size.width, hair.toPx()))
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type(15, 20, rgb(SafeMotion.INK)))
        Box(Modifier.size(64.dp, 20.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                "••••",
                style = type(15, 20, rgb(SafeMotion.MUTE), tracking = 1f),
                modifier = Modifier.graphicsLayer {
                    film()
                    alpha = (1 - SafeMotion.seg(controller.openNow, 0.1, 0.55)).toFloat()
                },
            )
            Text(
                value,
                style = type(15, 20, rgb(SafeMotion.INK), FontWeight.SemiBold, tabular = true),
                modifier = Modifier.graphicsLayer {
                    film()
                    alpha = SafeMotion.seg(controller.openNow, 0.35, 0.9).toFloat()
                },
            )
        }
    }
}
