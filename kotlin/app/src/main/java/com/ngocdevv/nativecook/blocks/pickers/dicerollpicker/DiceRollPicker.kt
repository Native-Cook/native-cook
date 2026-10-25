package com.ngocdevv.nativecook.blocks.pickers.dicerollpicker

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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.EDGE
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.HALF
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.THROW_END
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TX0
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TX1
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TY0
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TY1
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.clamp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/*
 Dice Roll Picker — a board-game night screen ("Game Table — Who goes first?"). Two ivory dice,
 drawn in real 3D (projected rounded cubes, lit faces, concave pips, shadows that grow and soften
 with height) sit on green felt in a walnut tray. Flick on the table to throw: the dice tumble,
 hop, bounce off the far rim and settle on their faces; their sum picks a player, whose avatar pops
 and wears a "Starts" tag while the line rolls to "11 · Priya starts". Push a die with a finger to
 roll it over by hand; let go and it settles flat on the nearest face.

 One Canvas: a baked room and tray, then per frame the shadows, the dice (far one first), the
 avatars and the result line. 3D math and the closed-form throw: DiceRollPickerMath.kt; timeline:
 DiceRollPickerMotion.kt. Touch throws use the same closed-form throw with seeded faces.

 2 s after the last touch (once the dice are still) they slide home to 3 and 4 (1.6 s) and the
 loop restarts. Reduced motion: no loop; a throw places the dice at once and the result fades in.
 */

private const val IDLE = 2.0
private const val SLIDE = 1.6
/** Release speed (pt/s) above which a drag on the felt throws the dice. */
private const val FLICK = 650.0

internal val DiceBackground = Color(0xFF1A1512)

/** A die's motion: 0 still at `pose`, 1 a throw, 2 a slide from (m0, p0) to (m1, p1). */
private class DieState(
    val kind: Int,
    val t0: Double,
    val dur: Double,
    val pose: DicePose,
    val th: DiceThrow? = null,
    val m0: DoubleArray = pose.m,
    val m1: DoubleArray = pose.m,
    val p0x: Double = pose.x,
    val p0y: Double = pose.y,
    val p1x: Double = pose.x,
    val p1y: Double = pose.y,
)

private fun still(pose: DicePose) = DieState(0, 0.0, 0.0, pose)

private class DiceState {
    /** mode: 0 loop / rest, 1 a finger is in charge, 2 sliding home. */
    var mode = 0
    var autoStart = -1.0
    var now = -1.0
    var lastTouch = -1.0
    var dragging = -1
    var dragPrevX = 0.0
    var dragPrevY = 0.0
    val dice = arrayOf(still(DiceMotion.restPose(0)), still(DiceMotion.restPose(1)))
    /** The result line's roll, the chosen avatar's pop, the chosen player, the sum, settled since. */
    var roll = 0.0
    var pop = 0.0
    var chosen = 3
    var sum = 7
    var settledAt = -1.0
    var returnAt = -1.0
    var landed = false
    // What the Canvas draws.
    var a: DicePose = DiceMotion.restPose(0)
    var b: DicePose = DiceMotion.restPose(1)
    var fRoll = 0f
    var fPop = 0f
    var fChosen = 3
    var fSum = 7
    var label = ""
    var labelKey = -1
    val frame = mutableIntStateOf(0)
}

/** Where a die is now, and whether its motion is over. */
private fun evalDie(st: DieState, now: Double, done: BooleanArray, i: Int): DicePose {
    if (st.kind == 1 && st.th != null) {
        val s = now - st.t0
        done[i] = s >= THROW_END
        return DiceMath.throwPose(st.th, clamp(s, 0.0, THROW_END))
    }
    if (st.kind == 2) {
        val u = (now - st.t0) / st.dur
        done[i] = u >= 1
        return DiceMath.slidePose(st.m0, st.m1, st.p0x, st.p0y, st.p1x, st.p1y, u)
    }
    done[i] = true
    return st.pose
}

/**
 * Two dice on a felt tray pick who goes first: flick to throw them, push a die to roll it by hand,
 * or press "Roll the dice".
 *
 * @param autoPlay picks the dice up, throws them (5 and 6, Priya starts) and slides them home, on an 8 s loop.
 */
@Composable
fun DiceRollPicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { DiceState() }
    val kit = remember { DiceKit() }
    val fonts = remember { DiceFonts() }
    val scratch = remember { DiceScratch() }
    var wake by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(DiceBackground)
            .semantics { contentDescription = "Two dice on a felt table. Flick to throw them, or push a die to turn it by hand." },
    ) {
        val geo = remember(maxWidth, maxHeight, safeTop, safeBottom) { DiceGeo(maxWidth.value, maxHeight.value, safeTop, safeBottom) }
        var room by remember { mutableStateOf<Bitmap?>(null) }
        val scale = min(screen.density, 2f)

        LaunchedEffect(geo, scale) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            room = withContext(Dispatchers.Default) { diceBake(geo.w, geo.h, scale) { diceDrawRoom(it, DiceKit(), geo) } }
        }

        val ready = room != null
        LaunchedEffect(geo, auto, reduced, ready, wake) {
            if (!ready) return@LaunchedEffect
            state.now = -1.0
            while (true) {
                val keepGoing = withFrameNanos { nanos -> diceStep(state, nanos / 1e9, auto, reduced, view) }
                state.frame.intValue += 1
                if (!keepGoing) break
            }
        }

        val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val roomRect = remember(geo) { RectF(0f, 0f, geo.w, geo.h) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geo, reduced) {
                    val limX0 = TX0 + HALF + 2
                    val limX1 = TX1 - HALF - 2
                    val limY0 = TY0 + HALF + 2
                    val limY1 = TY1 - HALF - 2
                    val gcx = geo.cx.toDouble()
                    val gcy = geo.cy.toDouble()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val ex = down.position.x / d
                        val ey = down.position.y / d
                        if (ey > geo.resultBase - 30) return@awaitEachGesture
                        val s = state
                        diceTakeOver(s)
                        wake++
                        // A die under the finger? (projected centre, generous radius)
                        val poses = arrayOf(s.a, s.b)
                        var hit = -1
                        var best = EDGE * 0.85
                        val out = DoubleArray(3)
                        for (i in 0 until 2) {
                            val p = poses[i]
                            DiceMath.project(gcx, gcy, p.x, p.y, p.hop + DiceMath.support(p.m), out)
                            val dd = hypot(ex - out[0], ey - out[1])
                            if (dd < best) {
                                best = dd
                                hit = i
                            }
                        }
                        if (hit >= 0 && poses[hit].hop < 4) {
                            s.dragging = hit
                            s.dice[hit] = still(poses[hit])
                            val w = DiceMath.unproject(gcx, gcy, ex.toDouble(), ey.toDouble(), HALF)
                            s.dragPrevX = w[0]
                            s.dragPrevY = w[1]
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            s.lastTouch = s.now
                            val i = s.dragging
                            if (i < 0) continue
                            val w = DiceMath.unproject(gcx, gcy, (change.position.x / d).toDouble(), (change.position.y / d).toDouble(), HALF)
                            val p = s.dice[i].pose
                            val x = clamp(p.x + (w[0] - s.dragPrevX), limX0, limX1)
                            val y = clamp(p.y + (w[1] - s.dragPrevY), limY0, limY1)
                            val dx = x - p.x
                            val dy = y - p.y
                            val dist = hypot(dx, dy)
                            var m = p.m
                            // Pushed along the felt it tips over: a quarter turn per edge length, about z × direction.
                            if (dist > 0.01) m = DiceMath.mul(DiceMath.axisAngle(-dy / dist, dx / dist, 0.0, dist / EDGE * (PI / 2)), m)
                            s.dice[i] = still(DicePose(m, x, y, 0.0))
                            s.dragPrevX = w[0]
                            s.dragPrevY = w[1]
                        }
                        s.lastTouch = s.now
                        val i = s.dragging
                        if (i >= 0) {
                            s.dragging = -1
                            val p = s.dice[i].pose
                            val flat = DiceMath.snapFlat(p.m)
                            s.dice[i] = if (reduced) still(DicePose(flat, p.x, p.y, 0.0)) else DieState(2, s.now, 0.28, p, null, p.m, flat)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } else {
                            val v = tracker.calculateVelocity()
                            val vx = v.x / d
                            val vy = v.y / d
                            if (hypot(vx, vy) > FLICK) diceThrowAll(s, clamp(vx / 2500.0, -0.6, 0.6), reduced)
                        }
                    }
                },
        ) {
            state.frame.intValue
            val img = room ?: return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val s = state
                nc.save()
                nc.scale(density, density)
                kit.begin()
                nc.drawBitmap(img, null, roomRect, bitmapPaint)
                diceDrawShadow(nc, kit, scratch, geo, s.a)
                diceDrawShadow(nc, kit, scratch, geo, s.b)
                // The die farther away (larger y) first.
                if (s.a.y > s.b.y) {
                    diceDrawDie(nc, kit, scratch, geo, s.a)
                    diceDrawDie(nc, kit, scratch, geo, s.b)
                } else {
                    diceDrawDie(nc, kit, scratch, geo, s.b)
                    diceDrawDie(nc, kit, scratch, geo, s.a)
                }
                diceDrawAvatars(nc, kit, geo, fonts, s.fPop, s.fChosen)
                val key = s.fSum * 8 + s.fChosen
                if (key != s.labelKey) {
                    s.labelKey = key
                    s.label = "${s.fSum} · ${DicePlayers[s.fChosen].name} starts"
                }
                diceDrawResult(nc, kit, geo, fonts, s.label, s.fRoll)
                nc.restore()
            }
        }

        if (ready) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("GAME TABLE · THURSDAY", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Color(0x80F3EADB)))
                BasicText("Who goes first?", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Color(0xFFF7EEDF)))
                BasicText("Harbor Lights · round 2 of 3", style = TextStyle(fontSize = 15.sp, color = Color(0x99F3EADB)))
            }
            DiceCta(Modifier.offset(y = geo.ctaTop.dp).padding(horizontal = 16.dp)) {
                diceThrowAll(state, 0.0, reduced)
                wake++
            }
        }
    }
}

/** Hands control to the finger: freezes whatever plays into still dice. */
private fun diceTakeOver(s: DiceState) {
    if (s.mode != 1) {
        s.dice[0] = still(s.a)
        s.dice[1] = still(s.b)
    }
    s.mode = 1
    s.autoStart = -1.0
    s.lastTouch = s.now
}

private fun diceThrowAll(s: DiceState, bias: Double, reduced: Boolean) {
    diceTakeOver(s)
    val t1 = s.now
    val done = BooleanArray(2)
    for (i in 0 until 2) {
        val cur = evalDie(s.dice[i], t1, done, i)
        // A throw's outcome from the moment it was thrown (seeded, so it needs no random source).
        val seed = floor(t1 * 997) + i * 131
        val face = 1 + floor(DiceMath.hash(seed) * 6).toInt()
        val yaw = (DiceMath.hash(seed + 1) - 0.5) * 1.4
        val fx = (if (i == 0) -40.0 else 42.0) + bias * 30 + (DiceMath.hash(seed + 2) - 0.5) * 24
        val fy = 18 + DiceMath.hash(seed + 3) * 40
        val end = DiceMath.faceUp(face, yaw)
        if (reduced) {
            s.dice[i] = still(DicePose(end, fx, fy, 0.0))
            continue
        }
        val th = DiceMath.makeThrow(cur.m, end, cur.x, cur.y, fx, fy, cur.hop, 2 + floor(DiceMath.hash(seed + 4) * 2).toInt(), DiceMotion.RIM_Y)
        s.dice[i] = DieState(1, t1 + i * 0.05, THROW_END, cur, th, cur.m, end, cur.x, cur.y, fx, fy)
    }
}

/** One frame of the clock, the dice and the result. Returns whether to keep running. */
private fun diceStep(s: DiceState, t1: Double, auto: Boolean, reduced: Boolean, view: View): Boolean {
    val first = s.now < 0
    val dt = if (first) 0.0 else min(t1 - s.now, 0.05)
    s.now = t1
    if (s.lastTouch < 0) s.lastTouch = t1
    if (first && auto && s.mode == 0) s.autoStart = t1
    if (s.mode == 0 && s.autoStart >= 0) {
        val t = (t1 - s.autoStart) % LOOP
        s.a = DiceMotion.autoPose(0, t)
        s.b = DiceMotion.autoPose(1, t)
        s.fRoll = DiceMotion.autoRoll(t).toFloat()
        s.fPop = DiceMotion.autoPop(t).toFloat()
        s.fChosen = 5
        s.fSum = DiceMotion.AUTO_SUM
        return true
    }
    val done = BooleanArray(2)
    val a = evalDie(s.dice[0], t1, done, 0)
    val b = evalDie(s.dice[1], t1, done, 1)
    val settled = s.mode != 2 && s.dragging < 0 && done[0] && done[1]
    if (settled) {
        if (s.settledAt < 0) {
            s.settledAt = t1
            s.sum = DiceMath.topFace(a.m) + DiceMath.topFace(b.m)
            s.chosen = dicePlayerFor(s.sum)
        }
        s.pop = if (reduced) 1.0 else DiceMotion.springIn(t1 - s.settledAt)
    } else {
        s.settledAt = -1.0
        s.pop *= exp(-dt * 8)
    }
    s.roll += ((if (settled) 1.0 else 0.0) - s.roll) * (1 - exp(-dt * 10))
    if (s.mode == 1 && auto && settled && t1 - s.lastTouch > IDLE) {
        // Slide home from wherever the dice lie.
        s.mode = 2
        s.returnAt = t1
        val cur = arrayOf(a, b)
        for (i in 0 until 2) {
            val rest = DiceMotion.restPose(i)
            s.dice[i] = DieState(2, t1, SLIDE, rest, null, cur[i].m, rest.m, cur[i].x, cur[i].y, rest.x, rest.y)
        }
    } else if (s.mode == 2 && t1 > s.returnAt + SLIDE + 0.2) {
        s.mode = 0
        s.dice[0] = still(DiceMotion.restPose(0))
        s.dice[1] = still(DiceMotion.restPose(1))
        s.autoStart = t1
    }
    s.a = a
    s.b = b
    s.fRoll = s.roll.toFloat()
    s.fPop = s.pop.toFloat()
    s.fChosen = s.chosen
    s.fSum = s.sum
    // A light tap as the result lands (finger throws only).
    val landed = s.mode == 1 && s.roll > 0.5
    if (landed && !s.landed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    s.landed = landed
    if (auto) return true
    // Without the loop the frames stop once everything has come to rest.
    return !(settled && abs(1 - s.roll) < 1e-3 && t1 - s.settledAt > 1.5)
}

@Composable
private fun DiceCta(modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ink = Color(0xFF3A2610)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val k = if (pressed) 0.97f else 1f
                scaleX = k
                scaleY = k
            }
            .cssBoxShadow(Color(0xFFF2C46B).copy(alpha = 0.22f), 10.dp, 28.dp, 28.dp)
            .cssBoxShadow(Color.Black.copy(alpha = 0.35f), 1.dp, 2.dp, 28.dp)
            .background(Color(0xFFF2C46B), RoundedCornerShape(28.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(20.dp).border(1.5.dp, ink, RoundedCornerShape(5.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val u = density
                for (p in floatArrayOf(5.5f, 10f, 14.5f)) drawCircle(ink, 1.5f * u, Offset(p * u, p * u))
            }
        }
        Spacer(Modifier.width(10.dp))
        BasicText("Roll the dice", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink))
    }
}
