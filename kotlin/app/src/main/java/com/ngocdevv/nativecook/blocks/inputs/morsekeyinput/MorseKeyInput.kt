package com.ngocdevv.nativecook.blocks.inputs.morsekeyinput

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

/*
 Morse Key Input — "Dot Dash", a puzzle app where the answer is keyed on a brass telegraph key.
 A port of MorseKeyInput.tsx (the reference) and the SwiftUI block; the numbers are the same.

 Look: a green leather desk, a telegram card with the clue typed on it and two answer boxes, a
 brass register (with a paper reel turning behind a window) that pays out a strip of tape, and a
 straight key on a walnut base: a brass lever on a trunnion, a black bakelite knob, a spring, a
 silver contact, binding posts wired to the register.

 Press the key: the lever pivots 3.3° about the trunnion onto the contact (a spring, ω 60 rad/s,
 ζ 0.5, stopped by the contact with a little bounce), the contact sparks blue-white and hums while
 closed, the register's pen drops onto the tape, and the tape — running at 34 dp/s while you key
 — prints a dot or a dash (longer than 0.2 s). After a 0.55 s pause the letter is decoded and rolls
 up into the next answer box; two letters show "Correct · +40" (or "Not quite"). 2 s after the
 last touch the printed tape tears off and falls, the answer rolls out and a fresh leader feeds
 out (0.9 s); then the loop (MorseKeyInputMotion.kt, LOOP = 9 s, "HI" in real timing) starts
 again. Reduced motion: no loop; the key still works, without sparks.
*/

private val INK = Color(0xFFEFE6D2)
private val MUTE = Color(239 / 255f, 230 / 255f, 210 / 255f, 0.64f)
private val FAINT = Color(239 / 255f, 230 / 255f, 210 / 255f, 0.44f)
private val BRASS = Color(0xFFD8B670)

private fun nowSeconds() = System.nanoTime() / 1e9

private class MorseArtSet(val scene: MorseScene, val layers: MorseLayers, val painter: MorsePainter)

private class MorseController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var lastTouch = 0.0
    var lastFrame = -1.0
    var leader = 0.0
    var sim = MorseSim(0.0)
    private var poseAt = Double.NaN
    private var cached: MorsePose? = null

    fun pose(at: Double = now): MorsePose {
        val c = cached
        if (c != null && at == poseAt) return c
        val p = when {
            mode != Mode.Auto -> MorseMotion.simPose(sim, at, leader)
            !auto || loopStart < 0 -> MorseMotion.loopPose(0.0, leader)
            else -> MorseMotion.loopPose((((at - loopStart) % MorseMotion.LOOP) + MorseMotion.LOOP) % MorseMotion.LOOP, leader)
        }
        // No sparks under reduced motion.
        if (reduced) p.sparkAge = 99.0
        cached = p
        poseAt = at
        return p
    }

    /** One frame; returns "letter", "done" or null for the haptic of a decoded letter. */
    fun frame(at: Double): String? {
        if (loopStart < 0) loopStart = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        var feel: String? = null
        if (mode != Mode.Auto) {
            val s = sim
            if (s.step(at, dt)) feel = if (s.decoded.size == 2 && s.correct) "done" else "letter"
            if (mode == Mode.Touch && !s.held && s.buffer.isEmpty() && at - lastTouch > MorseMotion.IDLE_BEFORE_BACK) {
                mode = Mode.Back
                s.torn = s.strip()
                s.backAt = at
            } else if (mode == Mode.Back && at >= s.backAt + MorseMotion.BACK_FOR) {
                sim = MorseSim(leader)
                mode = Mode.Auto
                loopStart = at
            }
        }
        cached = null
        now = at
        return feel
    }

    /** Hands whatever the loop shows to the simulation, so a finger never makes anything jump. */
    fun takeOver(at: Double) {
        if (mode == Mode.Touch) return
        val p = pose()
        val s = MorseSim(leader)
        if (mode == Mode.Auto && p.fall == 0.0) {
            s.base = p.tape.len - p.tape.run
            s.run = p.tape.run
            s.marks = p.tape.marks.copyOf(maxOf(64, p.tape.count))
            s.markCount = p.tape.count
            s.depth = p.depth
            for (i in 0 until 2) {
                val sl = p.slots[i]
                if (sl.ch.isNotEmpty() && sl.into > 0.5 && sl.out < 0.5) {
                    s.decoded.add(sl.ch)
                    s.slotAt.add(at - 1)
                }
            }
            // A letter the loop was in the middle of keying is finished as dots.
            s.buffer = p.buffer
            s.upAt = at
            if (s.decoded.size == 2) {
                s.chipAt = at - 1
                s.correct = true
            }
        } else {
            s.base = p.tape.len
        }
        sim = s
        mode = Mode.Touch
        cached = null
    }
}

@Composable
fun MorseKeyInput(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { MorseController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF111D1A)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        var art by remember { mutableStateOf<MorseArtSet?>(null) }

        // Static art is baked off the main thread; the loop starts on the first frame that has it.
        LaunchedEffect(w, h, top, bottom, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake does not restart on every inset frame.
            delay(120)
            val made = withContext(Dispatchers.Default) {
                val fonts = MorseFonts()
                val scene = MorseScene(w, h, top, bottom)
                MorseArtSet(scene, MorseKeyInputArt.bakeLayers(scene, fonts, min(2f, density)), MorsePainter(fonts))
            }
            c.leader = made.scene.leader
            if (c.mode == MorseController.Mode.Auto) c.sim = MorseSim(c.leader)
            art = made
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                var feel: String? = null
                withFrameNanos { n -> feel = c.frame(n / 1e9) }
                when (feel) {
                    "done" -> view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                    "letter" -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                // With nothing playing and no finger, the frame stays as it is.
                if (!auto && c.mode == MorseController.Mode.Auto) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Telegraph key. Tap for a dot, hold for a dash; pause to finish a letter."
                }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Anywhere below the tape is the key.
                        if (down.position.y / density < a.scene.tapeY + a.scene.tapeH) return@awaitEachGesture
                        down.consume()
                        val at = nowSeconds()
                        c.takeOver(at)
                        c.sim.keyDown(at)
                        c.lastTouch = at
                        c.awake = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                            }
                        } finally {
                            val up = nowSeconds()
                            c.sim.keyUp(up)
                            c.lastTouch = up
                        }
                    }
                },
        ) {
            val a = current ?: return@Canvas
            val pose = c.pose()
            scale(density, density, Offset.Zero) {
                a.painter.frame(drawContext.canvas.nativeCanvas, a.scene, a.layers, pose)
            }
        }

        Column(Modifier.offset(x = 20.dp, y = (top + 12).dp).width((w - 40).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("DOT DASH", style = TextStyle(color = BRASS, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Send your answer", style = TextStyle(color = INK, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
            BasicText("Puzzle 27 · The telegraph room", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        val s = current?.scene
        if (s != null) {
            /** "Clear tape": tear it off now. */
            fun clear() {
                val at = nowSeconds()
                c.takeOver(at)
                val sim = c.sim
                sim.held = false
                sim.buffer = ""
                sim.torn = sim.strip()
                sim.backAt = at
                c.mode = MorseController.Mode.Back
                c.awake = true
            }

            /** "Send": finish the letter in progress at once. */
            fun send() {
                val at = nowSeconds()
                c.takeOver(at)
                c.sim.upAt = at - MorseMotion.LETTER_GAP - 0.01
                c.lastTouch = at
                c.awake = true
            }

            val areaTop = s.baseTop + 76
            Column(
                Modifier
                    .offset(x = 20.dp, y = areaTop.dp)
                    .width((w - 40).dp)
                    .height((h - areaTop - bottom - 8).coerceAtLeast(0f).dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    MorseLegend("Tap") { Box(Modifier.size(7.dp).background(INK, RoundedCornerShape(3.5.dp))) }
                    MorseLegend("Hold") { Box(Modifier.size(18.dp, 6.dp).background(INK, RoundedCornerShape(3.dp))) }
                    MorseLegend("Pause for the next letter") {
                        Canvas(Modifier.padding(top = 2.dp).size(18.dp, 6.dp)) {
                            val sw = 1.5f * density
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(sw / 2, 0f)
                                lineTo(sw / 2, size.height - sw / 2)
                                lineTo(size.width - sw / 2, size.height - sw / 2)
                                lineTo(size.width - sw / 2, 0f)
                            }
                            drawPath(p, INK, style = Stroke(sw))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MorseButton("Clear tape", primary = false, ::clear)
                    MorseButton("Send letter", primary = true, ::send)
                }
            }
        }
    }
}

@Composable
private fun MorseLegend(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        mark()
        BasicText(label, style = TextStyle(color = FAINT, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun RowScope.MorseButton(label: String, primary: Boolean, action: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    val shape = RoundedCornerShape(26.dp)
    var m = Modifier
        .weight(1f)
        .height(52.dp)
        .graphicsLayer {
            val sc = if (pressed) 0.97f else 1f
            scaleX = sc
            scaleY = sc
        }
    m = if (primary) {
        m.cssBoxShadow(Color(216 / 255f, 182 / 255f, 112 / 255f, 0.18f), 10.dp, 24.dp, 26.dp)
            .cssBoxShadow(Color(0f, 0f, 0f, 0.45f), 1.dp, 2.dp, 26.dp)
            .background(BRASS, shape)
            .drawBehind {
                // Inset top highlight.
                drawRoundRect(
                    Color(255 / 255f, 246 / 255f, 220 / 255f, 0.6f),
                    topLeft = Offset(size.height / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width - size.height, 1 * density),
                    cornerRadius = CornerRadius(0.5f * density),
                )
            }
            .border(0.5.dp, Color(0xFFE9CC8E), shape)
    } else {
        m.background(Color(239 / 255f, 230 / 255f, 210 / 255f, 0.07f), shape)
            .border(0.5.dp, Color(239 / 255f, 230 / 255f, 210 / 255f, 0.18f), shape)
    }
    Box(
        m
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { currentAction(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { currentAction() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (primary) Color(0xFF2A2112) else INK,
                fontSize = 16.sp,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
    }
}
