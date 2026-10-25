package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.chipAt
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.cookieAt
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/*
 Fortune Cookie Crack — an order-delivered modal with a fortune cookie on a porcelain plate. A port
 of the React Native block (FortuneCookieCrack.tsx) and its SwiftUI twin; the numbers are the same.

 Hold to squeeze the cookie (or tap) and it shakes, cracks along a jagged line and snaps into two
 halves that swing apart, spilling crumbs; a rolled slip rises out of the gap, drops into place and
 unrolls to show the fortune, and the lucky numbers pop in. Then everything folds back.

 autoPlay timeline (LOOP = 9 s, FortuneCookieCrackMotion.kt): rest 0 – 1.4 (bob and tilt), squeeze
 1.4 – 2.0 (squash, shake), crack 2.0 – 2.45, snap 2.5 – 3.2 (halves swing 19.5° with an overshoot,
 26 crumbs), slip emerges 3.15, drops 3.45 – 3.85, unrolls 3.8 – 4.4, numbers 4.05 – 4.7, hold to
 6.4, close 6.4 – 8.4, rest again.

 Drawing: the backdrop (screen, lanterns, modal paper with grain, plate) is one bitmap baked off the
 main thread; the cookie's whole and broken pieces are bitmaps of the RN shader's maths computed on
 the CPU. Per frame one Canvas draws two shadow bitmaps, the crumbs as one path, two or three cookie
 bitmaps with the trimmed crack, and the slip. The clock is the only state and is read only in draw
 and graphicsLayer lambdas.

 Touch: press and hold plays the squeeze and it cracks at 0.6 s (light tick); let go earlier and it
 relaxes in 280 ms; a tap under 0.22 s cracks it at once; while open, a tap or Close plays the close.
 2 s after the last touch an open cookie closes itself and the loop resumes. Reduced motion: the loop
 does not play; touch still works.
 */

private val Lacquer = Color(0xFFA8322A)
private val Ink = Color(0xFF3B1F17)
private val Mute = Color(59 / 255f, 31 / 255f, 23 / 255f, 0.6f)
private val Lucky = intArrayOf(7, 18, 23, 31, 42, 56)
private const val Tabular = "tnum"

private class FortuneBaked(val art: FortuneArt, val backdrop: Bitmap, val scene: FortuneScene)

@Composable
fun FortuneCookieCrack(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val looping = autoPlay && !reduced
    val model = remember(looping) {
        FortuneCookieCrackModel(looping) { kind ->
            view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    var awake by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1C0E0B))) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val l = remember(maxWidth, maxHeight, top, bottom) { FortuneLayout(maxWidth.value, maxHeight.value, top, bottom) }

        // Static art, baked once off the main thread; the loop starts at t = 0 on the first frame that has it.
        // The cookie art does not depend on the layout, so it starts at once; the backdrop follows the
        // insets, which animate while the demo hides the status bar, so it waits for them to settle
        // (cancellable) instead of restarting a bake on every inset frame. Baked at 2 px per dp at most.
        val scale = min(2f, density)
        val art by produceState<FortuneArt?>(null, scale) {
            value = withContext(Dispatchers.Default) { FortuneArt(scale, coroutineContext[Job]) }
        }
        var baked by remember { mutableStateOf<FortuneBaked?>(null) }
        LaunchedEffect(l, art) {
            val a = art ?: return@LaunchedEffect
            delay(120)
            baked = withContext(Dispatchers.Default) { FortuneBaked(a, a.backdrop(l), FortuneScene(a, l)) }
        }
        val ready = baked != null

        LaunchedEffect(model, ready, looping || awake) {
            if (!ready || !(looping || awake)) return@LaunchedEffect
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.0 else min(max((now - last) / 1e9, 0.0), 1.0 / 30)
                    last = now
                    model.step(dt)
                }
                // Outside autoPlay the frames stop again once the cookie is back at rest.
                if (!looping && model.resting) {
                    awake = false
                    break
                }
            }
        }

        val b = baked
        if (b != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { it.nativeCanvas.drawBitmap(b.backdrop, null, android.graphics.RectF(0f, 0f, size.width, size.height), null) }
            }
            Canvas(Modifier.fillMaxSize()) {
                val pose = cookieAt(model.clock)
                drawIntoCanvas {
                    val c = it.nativeCanvas
                    c.save()
                    c.scale(density, density)
                    b.scene.draw(c, pose)
                    c.restore()
                }
            }
        }

        // The cookie takes touches anywhere around it (the buttons lie above this layer).
        val currentLooping by rememberUpdatedState(looping)
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Button
                    contentDescription = "Fortune cookie. Tap or press and hold to crack it open."
                }
                .pointerInput(model, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (model.down(down.position.x / density, down.position.y / density, l) && !currentLooping) awake = true
                        try {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.none { it.pressed }) break
                            }
                            model.up()
                        } catch (c: CancellationException) {
                            model.cancel()
                            throw c
                        }
                    }
                },
        )

        // The dimmed app bar behind the modal.
        Row(
            Modifier.offset(x = 24.dp, y = (top + 14).dp).width((l.w - 48).dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("Golden Wok", style = TextStyle(color = Color(1f, 236 / 255f, 214 / 255f, 0.55f), fontSize = 17.sp, fontWeight = FontWeight.Bold))
            BasicText(
                "Order #GW-3318",
                style = TextStyle(color = Color(1f, 236 / 255f, 214 / 255f, 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = Tabular),
            )
        }

        // The modal's head: badge, title and the sub line that rolls when the cookie opens.
        Column(
            Modifier.offset(x = 40.dp, y = (l.mTop + 26).dp).width((l.w - 80).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .height(24.dp)
                    .background(Color(62 / 255f, 122 / 255f, 78 / 255f, 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(14.dp).background(Color(0xFF3E7A4E), CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(8.dp)) {
                        val u = size.width / 8
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(1.2f * u, 4.2f * u)
                            lineTo(3.2f * u, 6f * u)
                            lineTo(6.8f * u, 2f * u)
                        }
                        drawPath(p, Color(0xFFF2FBF3), style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                BasicText(
                    "ORDER DELIVERED · 7:42 PM",
                    style = TextStyle(color = Color(0xFF356A43), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontFeatureSettings = Tabular),
                )
            }
            BasicText(
                "Your fortune is ready",
                style = TextStyle(color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, textAlign = TextAlign.Center),
            )
            Box(Modifier.fillMaxWidth().height(20.dp).clipToBounds()) {
                val sub = TextStyle(color = Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 20.sp)
                BasicText(
                    "Tap or squeeze the cookie to crack it",
                    Modifier.fillMaxWidth().graphicsLayer {
                        val s = cookieAt(model.clock).stage.toFloat()
                        alpha = 1 - s
                        translationY = -s * 18 * density
                    },
                    style = sub, maxLines = 1,
                )
                BasicText(
                    "Baked into every Golden Wok order",
                    Modifier.fillMaxWidth().graphicsLayer {
                        val s = cookieAt(model.clock).stage.toFloat()
                        alpha = s
                        translationY = (1 - s) * 18 * density
                    },
                    style = sub, maxLines = 1,
                )
            }
        }

        // Lucky numbers under the slip, popping in one after another.
        Column(
            Modifier.offset(x = 40.dp, y = (l.slipY + FortuneLayout.SLIP_H / 2 + 20).dp).width((l.w - 80).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("LUCKY NUMBERS", style = TextStyle(color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lucky.forEachIndexed { i, n -> FortuneChip(n) { chipAt(model.clock, i) } }
            }
        }

        // Save and Close.
        Row(
            Modifier.offset(x = 36.dp, y = (l.h - bottom - 40 - 52).dp).width((l.w - 72).dp).height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FortuneButton("Save", primary = false) {}
            FortuneButton("Close", primary = true) {
                if (model.close() && !looping) awake = true
            }
        }
    }
}

/** One lucky number: a red ball that pops in after the slip opens. */
@Composable
private fun FortuneChip(n: Int, value: () -> Double) {
    Box(
        Modifier
            .graphicsLayer {
                val v = value().toFloat()
                alpha = min(1f, max(0f, v * 1.4f))
                translationY = (1 - v) * 10 * density
                scaleX = 0.9f + 0.1f * v
                scaleY = scaleX
            }
            .size(34.dp)
            .cssBoxShadow(Color(80 / 255f, 20 / 255f, 10 / 255f, 0.18f), 6.dp, 12.dp, 17.dp)
            .cssBoxShadow(Color(80 / 255f, 20 / 255f, 10 / 255f, 0.35f), 1.dp, 2.dp, 17.dp)
            .background(Lacquer, CircleShape)
            .drawBehind {
                // The inset highlight on the top edge.
                val w = 1.dp.toPx()
                drawCircle(
                    Brush.verticalGradient(0f to Color(1f, 220 / 255f, 200 / 255f, 0.45f), 0.5f to Color.Transparent),
                    radius = size.minDimension / 2 - w / 2,
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(w),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("$n", style = TextStyle(color = Color(0xFFFFF1E2), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular))
    }
}

@Composable
private fun RowScope.FortuneButton(label: String, primary: Boolean, action: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.8f, stiffness = 440f), label = "press")
    val currentAction by rememberUpdatedState(action)
    val shape = RoundedCornerShape(26.dp)
    val base = Modifier
        .weight(1f)
        .height(52.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .semantics {
            role = Role.Button
            contentDescription = label
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
        }
    val styled = if (primary) {
        base
            .cssBoxShadow(Color(120 / 255f, 30 / 255f, 20 / 255f, 0.25f), 10.dp, 22.dp, 26.dp)
            .cssBoxShadow(Color(80 / 255f, 20 / 255f, 10 / 255f, 0.4f), 1.dp, 2.dp, 26.dp)
            .background(Lacquer, shape)
    } else {
        base
            .background(Color(59 / 255f, 31 / 255f, 23 / 255f, 0.07f), shape)
            .border(1.dp, Color(59 / 255f, 31 / 255f, 23 / 255f, 0.14f), shape)
    }
    Box(styled, contentAlignment = Alignment.Center) {
        BasicText(label, style = TextStyle(color = if (primary) Color(0xFFFFF4E6) else Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}
