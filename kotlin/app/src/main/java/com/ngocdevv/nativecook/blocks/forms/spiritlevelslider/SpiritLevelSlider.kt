package com.ngocdevv.nativecook.blocks.forms.spiritlevelslider

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.spiritlevelslider.SpiritMotion.MAX_ANGLE
import com.ngocdevv.nativecook.blocks.forms.spiritlevelslider.SpiritMotion.MIN_ANGLE
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 Spirit Level Slider — step 3 of a roof-estimate form ("Level — How steep is your roof?"). The
 slider is a yellow aluminium spirit level: drag either end up or down to tilt it about its centre
 and the bubble slides along the curved lime vial to the higher end, lagging on a spring, stretched
 by its speed and squashed against the vial's ends. The pitch card above follows: the degrees and
 the rise per 12 roll like an odometer, the pitch word rolls in its pill, and the house's roof
 tilts to the same pitch. Presets under the level spring it to 4/12, 6/12, 9/12 or 12/12.

 One Canvas: a baked background (paper, card, house walls), the roof and readouts drawn per frame,
 and the level as a baked bitmap under its rotation with the bubble and glass on top. The frame
 loop only moves numbers; only the Canvas reads them.

 Touch: the level follows the finger (rubber-band past −6° and 48°); on release it springs back
 inside the range (ω 12, ζ 0.8). A selection tick each whole step of rise, a light tap when the
 bubble returns between the centre marks. 2 s after the last touch the level eases back to 0°
 (0.9 s) and the loop restarts. Reduced motion: no loop; the bubble sits where the tilt puts it.
 */

private const val IDLE = 2.0
private const val RETURN = 0.9
private const val LEVEL_OMEGA = 12.0
private const val LEVEL_ZETA = 0.8
private val PRESETS = intArrayOf(4, 6, 9, 12)

internal val SpiritBackground = Color(0xFFECE4D5)
private val Ink = Color(0xFF2A241C)

private class SpiritImages(val background: Bitmap, val level: Bitmap, val shadow: Bitmap)

/** mode: 0 loop / rest, 1 a finger (or a preset) is in charge, 2 easing back to rest. */
private class SpiritState {
    var mode = 0
    var autoStart = -1.0
    var now = -1.0
    var lastTouch = -1.0
    var dragging = false
    /** [angle, angular velocity] of the level. */
    val level = doubleArrayOf(0.0, 0.0)
    var goal = 0.0
    var grabAngle = 0.0
    var grabX = 0f
    var grabbed = false
    val bubble = doubleArrayOf(0.0, 0.0)
    var returnAngle = 0.0
    var returnAt = 0.0
    var hapticKey = -1
    val frame = mutableIntStateOf(0)
}

/**
 * A roof-pitch form step whose slider is a spirit level: drag an end to tilt it, or pick a preset.
 *
 * @param autoPlay tilts the level to 28°, past 40° and down to 18°, then back to level, on an 8 s loop.
 */
@Composable
fun SpiritLevelSlider(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { SpiritState() }
    val table = remember { SpiritMotion.buildBubbleTable() }
    val kit = remember { SpiritKit() }
    val fonts = remember { SpiritFonts() }
    var wake by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize().background(SpiritBackground)) {
        val geo = remember(maxWidth, maxHeight, safeTop, safeBottom) { SpiritGeo(maxWidth.value, maxHeight.value, safeTop, safeBottom) }
        var images by remember { mutableStateOf<SpiritImages?>(null) }
        val scale = min(screen.density, 2f)

        LaunchedEffect(geo, scale) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            images = withContext(Dispatchers.Default) {
                val k = SpiritKit()
                val ft = SpiritFonts()
                val bg = spiritBake(geo.w, geo.h, scale) { spiritDrawBackground(it, k, geo, ft) }
                k.begin()
                val level = spiritBake(geo.boxW, geo.boxH, scale) { spiritDrawLevelBody(it, k, geo, ft) }
                k.begin()
                val shadow = spiritBake(geo.len + SPIRIT_SHADOW_PAD * 2, geo.thick + SPIRIT_SHADOW_PAD * 2, scale) { spiritDrawShadow(it, k, geo) }
                SpiritImages(bg, level, shadow)
            }
        }

        val ready = images != null
        LaunchedEffect(geo, auto, reduced, ready, wake) {
            if (!ready) return@LaunchedEffect
            state.now = -1.0
            while (true) {
                val keepGoing = withFrameNanos { nanos -> spiritStep(state, nanos / 1e9, auto, reduced, table, view) }
                state.frame.intValue += 1
                if (!keepGoing) break
            }
        }

        val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val bgRect = remember(geo) { RectF(0f, 0f, geo.w, geo.h) }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Roof pitch. Drag the level's end up or down to tilt it." }
                .pointerInput(geo) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val y0 = down.position.y / d
                        if (y0 < geo.card.y + geo.card.h || y0 > geo.chipsTop - 4) return@awaitEachGesture
                        val s = state
                        s.mode = 1
                        s.autoStart = -1.0
                        s.dragging = true
                        s.lastTouch = s.now
                        // The lever arm is where the finger landed; the level keeps its angle under the finger.
                        s.grabAngle = s.level[0]
                        s.grabX = down.position.x / d
                        s.grabbed = true
                        s.level[1] = 0.0
                        wake++
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            s.lastTouch = s.now
                            val side = if (s.grabX >= geo.cx) 1 else -1
                            val arm = max(abs(s.grabX - geo.cx), 60f)
                            val ty = (change.position.y - down.position.y) / d
                            val turned = atan2((-ty * side).toDouble(), arm.toDouble()) * 180 / PI
                            val a = SpiritMotion.rubber(s.grabAngle + turned)
                            val prev = s.level[0]
                            s.level[0] = a
                            s.level[1] = (a - prev) * 60
                            change.consume()
                        }
                        s.dragging = false
                        s.lastTouch = s.now
                        s.goal = SpiritMotion.clamp(s.level[0], MIN_ANGLE + 6, MAX_ANGLE)
                        s.grabbed = false
                    }
                },
        ) {
            state.frame.intValue
            val img = images ?: return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(density, density)
                kit.begin()
                nc.drawBitmap(img.background, null, bgRect, bitmapPaint)
                val angle = state.level[0]
                spiritDrawRoof(nc, kit, geo, angle)
                spiritDrawReadout(nc, kit, geo, fonts, angle)
                spiritDrawLevel(nc, kit, geo, img.level, img.shadow, bitmapPaint, angle, state.bubble[0], state.bubble[1])
                nc.restore()
            }
        }

        if (ready) {
            SpiritHeader(Modifier.padding(start = 24.dp, end = 24.dp, top = (safeTop + 4).dp))
            Row(
                Modifier.offset(y = geo.chipsTop.dp).padding(horizontal = 16.dp).fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (r in PRESETS) {
                    SpiritChip("$r/12", "$r in 12 pitch") {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val s = state
                        s.mode = 1
                        s.autoStart = -1.0
                        s.lastTouch = s.now
                        s.goal = atan(r / 12.0) * 180 / PI
                        wake++
                    }
                }
            }
            SpiritCta(Modifier.offset(y = geo.ctaTop.dp).padding(horizontal = 16.dp))
        }
    }
}

/** One frame of the clock, the touch modes, the level spring and the bubble. Returns whether to keep running. */
private fun spiritStep(s: SpiritState, t1: Double, auto: Boolean, reduced: Boolean, table: DoubleArray, view: View): Boolean {
    val first = s.now < 0
    val dt = if (first) 0.0 else min(t1 - s.now, 0.05)
    s.now = t1
    if (s.lastTouch < 0) s.lastTouch = t1
    if (first && auto && s.mode == 0) s.autoStart = t1
    if (s.mode == 0 && s.autoStart >= 0) {
        val t = (t1 - s.autoStart) % LOOP
        val a = SpiritMotion.angleAt(t)
        s.level[0] = a
        s.level[1] = (SpiritMotion.angleAt(t + 1.0 / 120) - a) * 120
        SpiritMotion.sampleBubble(table, t, s.bubble)
    } else {
        var a = s.level[0]
        var v = s.level[1]
        if (s.mode == 2) {
            a = SpiritMotion.mix(s.returnAngle, 0.0, SpiritMotion.easeInOut(SpiritMotion.seg(t1, s.returnAt, s.returnAt + RETURN)))
            v = 0.0
            if (t1 > s.returnAt + RETURN + 0.6) {
                s.mode = 0
                s.autoStart = t1
            }
        } else if (!s.dragging && dt > 0) {
            // Spring toward the goal (a preset, or back inside the range after a rubber-band).
            val steps = ceil(dt * 240).toInt()
            val h = dt / steps
            repeat(steps) {
                val acc = -LEVEL_OMEGA * LEVEL_OMEGA * (a - s.goal) - 2 * LEVEL_ZETA * LEVEL_OMEGA * v
                v += acc * h
                a += v * h
            }
            if (s.mode == 1 && auto && t1 - s.lastTouch > IDLE) {
                s.mode = 2
                s.returnAngle = a
                s.returnAt = t1
            }
        }
        s.level[0] = a
        s.level[1] = v
        val goal = SpiritMotion.bubbleGoal(a)
        if (reduced) {
            s.bubble[0] = goal
            s.bubble[1] = 0.0
        } else {
            SpiritMotion.stepBubble(s.bubble, goal, dt)
        }
    }
    // Haptics only for a finger: a tick per whole step of rise, a tap back between the centre marks.
    val key = if (s.mode == 1) SpiritMotion.jsRound(SpiritMotion.riseOf(s.level[0])) * 2 + (if (abs(s.bubble[0]) < 0.06) 1 else 0) else -1
    val prev = s.hapticKey
    if (prev >= 0 && key >= 0 && key != prev) {
        if (key shr 1 != prev shr 1) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        else if (key and 1 == 1) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    s.hapticKey = key
    if (auto) return true
    val settled = !s.dragging && abs(s.level[1]) < 1e-3 && abs(s.level[0] - s.goal) < 1e-3 &&
        abs(s.bubble[1]) < 1e-3 && abs(s.bubble[0] - SpiritMotion.bubbleGoal(s.level[0])) < 1e-4
    return !settled
}

private val Paper = Color(0xE6FFFDF8)
private val ShadowInk = Color(0xFF5A4A30)

@Composable
private fun SpiritHeader(modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .cssBoxShadow(ShadowInk.copy(alpha = 0.10f), 6.dp, 16.dp, 20.dp)
                    .cssBoxShadow(ShadowInk.copy(alpha = 0.18f), 1.dp, 2.dp, 20.dp)
                    .background(Paper, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Back") {}
                    .semantics { contentDescription = "Back" },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val u = density
                    val cx = 22f * u
                    val cy = 20f * u
                    val r = 6.4f * u
                    drawLine(Ink, Offset(cx, cy - r), Offset(cx - r, cy), strokeWidth = 2f * u, cap = StrokeCap.Square)
                    drawLine(Ink, Offset(cx - r, cy), Offset(cx, cy + r), strokeWidth = 2f * u, cap = StrokeCap.Square)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("Step 3 of 5", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.55f)))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Ink.copy(alpha = 0.1f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(Color(0xFFE8862A), RoundedCornerShape(2.dp)))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BasicText("ROOF ESTIMATE", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Ink.copy(alpha = 0.5f)))
        BasicText(
            "How steep is your roof?",
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Ink),
        )
        BasicText(
            "Tilt the level to match its pitch.",
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(fontSize = 15.sp, color = Ink.copy(alpha = 0.62f)),
        )
    }
}

@Composable
private fun RowScope.SpiritChip(label: String, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                val k = if (pressed) 0.97f else 1f
                scaleX = k
                scaleY = k
            }
            .background(Color(0xD9FFFDF8), shape)
            .border(Dp.Hairline, Ink.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun SpiritCta(modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val k = if (pressed) 0.97f else 1f
                scaleX = k
                scaleY = k
            }
            .cssBoxShadow(Ink.copy(alpha = 0.22f), 10.dp, 24.dp, 28.dp)
            .cssBoxShadow(Ink.copy(alpha = 0.3f), 1.dp, 2.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {}
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText("Continue", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF8F2E6)))
        BasicText("Next: roof area", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0x8CF8F2E6)))
    }
}
