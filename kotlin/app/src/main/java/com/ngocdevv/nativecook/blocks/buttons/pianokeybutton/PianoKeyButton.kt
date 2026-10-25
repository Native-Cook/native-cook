package com.ngocdevv.nativecook.blocks.buttons.pianokeybutton

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

/*
 Piano Key Button — "Keys", a music-lessons app whose three calls to action are real piano keys.
 A port of PianoKeyButton.tsx (the reference) and PianoKeyButton.swift; the numbers are the same.

 Look: ebony lacquer, a lesson card with a small stave (C, E, G and the C + G chord), a fallboard
 rail with a gold pinstripe, a dark recess where the hammers and their brass strings sit, a
 burgundy felt strip, then three big ivory keys (lengthwise grain, side shading, a front lip) with
 two black keys between them. Each key is a CTA: "Warm up", "Lesson 14", "Free play".

 Press: the key pivots down about its far edge (it foreshortens 5 dp, narrows 3 dp a side, its
 lip shrinks, it darkens toward the front and its neighbours shade its sides), the felt's shadow
 tightens, the hammer behind flicks up 14 dp and falls back, its three strings brighten while they
 ring, three rings of the key's colour spread from it (1.3 s, 0.12 s apart), the label rolls up
 into the note name (C / E / G), the note on the stave lights and the streak chip pulses. Two
 fingers on C and G play the chord, which completes the exercise: the streak rolls 12 → 13 days.

 Motion: the key is a spring (ω 40 rad/s, ζ 0.62); autoPlay is the analytic response of that
 spring to the script (PianoKeyButtonMotion.kt), so every frame is a function of the loop time.
 One frame clock drives everything and is read only in draw and graphicsLayer lambdas. A touch
 takes over at once; 2 s after the last finger lifts the screen eases back to rest (0.6 s) and the
 loop starts again from t = 0. Reduced motion: no loop, no rings or hammer flicks; keys still press.
*/

private val INK = Color(0xFFF3E7D6)
private val MUTE = Color(243 / 255f, 231 / 255f, 214 / 255f, 0.62f)
private val FAINT = Color(243 / 255f, 231 / 255f, 214 / 255f, 0.42f)
private val AMBER = Color(0xFFF2B35B)
private const val DIGIT_H = 22f

private fun nowSeconds() = System.nanoTime() / 1e9

/** The frame clock and a one-frame cache of the pose. */
private class PianoKeyClock {
    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    private var poseAt = Double.NaN
    private var cached: PianoKeyButtonMotion.Pose? = null

    fun pose(engine: PianoKeyEngine, auto: Boolean, reduced: Boolean): PianoKeyButtonMotion.Pose {
        val t = now
        val c = cached
        if (c != null && t == poseAt) return c
        val p = engine.pose(t, auto)
        // No ripples or hammer flicks: the key still travels and its label still turns.
        if (reduced) for (k in p.keys) k.downAge = 99.0
        cached = p
        poseAt = t
        return p
    }
}

private class PianoKeyArtSet(val scene: PianoKeyScene, val layers: PianoKeyLayers, val painter: PianoKeyPainter)

@Composable
fun PianoKeyButton(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val engine = remember { PianoKeyEngine() }
    val clock = remember { PianoKeyClock() }

    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(Color(0xFF15100D)) }.clipToBounds()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        val scene = remember(w, h, top, bottom) { PianoKeyScene(w, h, top, bottom) }
        var art by remember { mutableStateOf<PianoKeyArtSet?>(null) }

        // Static art is baked off the main thread; the loop starts on the first frame that has it.
        LaunchedEffect(scene, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake never restarts, and piles up, on every inset frame.
            delay(120)
            val made = withContext(Dispatchers.Default) {
                val fonts = PianoKeyFonts()
                val grains = PianoKeyGrains()
                val layers = PianoKeyButtonArt.bakeLayers(scene, fonts, grains, min(2f, density))
                PianoKeyArtSet(scene, layers, PianoKeyPainter(fonts, grains))
            }
            art = made
        }

        val ready = art != null
        LaunchedEffect(ready, clock.awake, auto) {
            if (!ready || !clock.awake) return@LaunchedEffect
            while (true) {
                withFrameNanos { n ->
                    val t = n / 1e9
                    if (engine.loopStart == null) engine.loopStart = t
                    engine.step(t)
                    clock.now = t
                }
                // With nothing playing and no finger, the frame stays as it is.
                if (!auto && engine.mode == PianoKeyEngine.Mode.Auto) {
                    clock.awake = false
                    break
                }
            }
        }

        val owner = remember { HashMap<Long, Int>() }
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Three piano keys: Warm up, Lesson 14 and Free play. Press C and G together to finish the exercise."
                }
                .pointerInput(scene, auto) {
                    awaitEachGesture {
                        try {
                            do {
                                val event = awaitPointerEvent()
                                for (ch in event.changes) {
                                    if (ch.changedToDown()) {
                                        val i = scene.keyAt(ch.position.x / density, ch.position.y / density)
                                        if (i < 0) continue
                                        ch.consume()
                                        owner[ch.id.value] = i
                                        val chord = engine.press(i, nowSeconds(), auto)
                                        clock.awake = true
                                        view.performHapticFeedback(
                                            if (chord) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                                            } else {
                                                HapticFeedbackConstants.KEYBOARD_TAP
                                            },
                                        )
                                    } else if (ch.changedToUp()) {
                                        val i = owner.remove(ch.id.value) ?: continue
                                        ch.consume()
                                        engine.release(i, nowSeconds())
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            // A cancelled gesture lets go of every key it held.
                            for (i in owner.values) engine.release(i, nowSeconds())
                            owner.clear()
                        }
                    }
                },
        ) {
            val a = art ?: return@Canvas
            val pose = clock.pose(engine, auto, reduced)
            scale(density, density, Offset.Zero) {
                a.painter.frame(drawContext.canvas.nativeCanvas, a.scene, a.layers, pose)
            }
        }

        if (ready) {
            // Header: kicker, title, subtitle and the streak chip.
            Row(
                Modifier.offset(x = 20.dp, y = (top + 12).dp).width((w - 36).dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("KEYS", style = TextStyle(color = AMBER, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
                    BasicText("Daily practice", style = TextStyle(color = INK, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
                    BasicText("Wednesday · 18 of 20 min", style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                }
                PianoKeyChip(Modifier.padding(top = 20.dp)) { clock.pose(engine, auto, reduced) }
            }

            // The lesson card's text, left of the stave.
            Column(
                Modifier.offset(x = (scene.cardX + 20).dp, y = (scene.cardY + 20).dp).width((scene.staffL - scene.cardX - 32).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText("TODAY’S EXERCISE", style = TextStyle(color = AMBER, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
                BasicText("C, E, G, then the chord", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 2)
                BasicText("Lesson 14 · 72 BPM", style = TextStyle(color = FAINT, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
            }
            BasicText(
                "Tap a key · Hold C and G together for the chord",
                Modifier.offset(x = 24.dp, y = (scene.keyBottom + 28).dp).width((w - 48).dp),
                style = TextStyle(color = FAINT, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}

private val flamePath = PathParser().parsePathString(
    "M6.2 0.6C6.8 3 9.2 4.4 10.4 6.8C11.9 9.9 10.1 14.2 6 14.4C2.2 14.6 0.2 11.4 1.1 8.4C1.6 6.8 2.8 5.9 3.2 4.4C4 5.3 4.2 6.4 4.1 7.4C5.6 6 6.6 3.4 6.2 0.6Z",
).toPath()

private val flameBrush = Brush.verticalGradient(0f to Color(0xFFFFD58A), 0.55f to Color(0xFFF2A24B), 1f to Color(0xFFD9682F), startY = 0f, endY = 15f)

/** The streak chip: a flame, a rolling units digit and "days"; it swells and glows with a pulse. */
@Composable
private fun PianoKeyChip(modifier: Modifier, pose: () -> PianoKeyButtonMotion.Pose) {
    val paints = remember {
        object {
            val far = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(77, 0, 0, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(10f), BlurMaskFilter.Blur.NORMAL)
            }
            val near = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(115, 0, 0, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(1f), BlurMaskFilter.Blur.NORMAL)
            }
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(140, 242, 179, 91)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(9f), BlurMaskFilter.Blur.NORMAL)
            }
        }
    }
    Row(
        modifier
            .graphicsLayer {
                val s = 1 + 0.08f * pose().pulse.toFloat()
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val d = density
                val r = size.height / 2
                val c = drawContext.canvas.nativeCanvas
                val pulse = PianoKeyButtonMotion.clamp01(pose().pulse).toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    c.drawRoundRect(0f, 8 * d, size.width, size.height + 8 * d, r, r, paints.far)
                    c.drawRoundRect(0f, 1 * d, size.width, size.height + 1 * d, r, r, paints.near)
                }
                drawRoundRect(Color(0xFF2A1F18), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(255 / 255f, 236 / 255f, 210 / 255f, 0.12f), Color.Transparent), endY = size.height / 2),
                    topLeft = Offset(0.5f * d, 0.5f * d),
                    size = androidx.compose.ui.geometry.Size(size.width - d, size.height - d),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r - 0.5f * d),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(d),
                )
                if (pulse > 0.001f) {
                    paints.glow.alpha = (140 * pulse).toInt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) c.drawRoundRect(0f, 0f, size.width, size.height, r, r, paints.glow)
                    drawRoundRect(Color(242 / 255f, 179 / 255f, 91 / 255f, 0.16f * pulse), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                }
                drawRoundRect(
                    Color(255 / 255f, 214 / 255f, 160 / 255f, 0.22f),
                    topLeft = Offset(0.25f * d, 0.25f * d),
                    size = androidx.compose.ui.geometry.Size(size.width - 0.5f * d, size.height - 0.5f * d),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(0.5f * d),
                )
            }
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(12.dp, 15.dp)) {
            scale(density, density, Offset.Zero) { drawPath(flamePath, flameBrush) }
        }
        val digit = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
        Row(Modifier.height(DIGIT_H.dp)) {
            Box(Modifier.height(DIGIT_H.dp), contentAlignment = Alignment.Center) { BasicText("1", style = digit) }
            Box(Modifier.height(DIGIT_H.dp).clipToBounds()) {
                Column(
                    Modifier
                        .wrapUnbounded()
                        .graphicsLayer { translationY = -((pose().streak - 10) * DIGIT_H).toFloat() * density },
                ) {
                    for (d in 0..9) Box(Modifier.height(DIGIT_H.dp), contentAlignment = Alignment.Center) { BasicText("$d", style = digit) }
                }
            }
        }
        BasicText("days", style = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Lets the digit column keep its full height inside the 22 dp window that clips it. */
private fun Modifier.wrapUnbounded() = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        layout(p.width, min(p.height, constraints.maxHeight)) { p.place(0, 0) }
    },
)
