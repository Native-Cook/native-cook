package com.ngocdevv.nativecook.blocks.modals.smokesignaltoast

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Smoke Signal Toast — "Campfire", an outdoor group's chat, where a notification arrives as smoke.

 A night sky with stars over a pine treeline, a campfire at the bottom, the group's last messages
 under the header. The fire puffs a cloud that rises and condenses into the message "Dinner's
 ready at the lake" from Kai, which holds readable, then a gust tears it away to the right. One
 full-screen AGSL shader (SmokeSignalToastShader.kt; a drawn stand-in below Android 13) with the
 message baked once, off the main thread, as two masks (SmokeSignalToastArt.kt).

 autoPlay: LOOP = 9 s (SmokeSignalToastMotion.kt). Touch: tap the message to open it (a card with
 quick replies; the smoke drifts up out of its way); swipe it to blow it away in the swipe's
 direction (past a third of the way, or a fling, it goes; otherwise it gathers back). 2 s after
 the last touch the smoke clears (0.9 s) and the loop restarts. Reduced motion: the message shown
 still and readable, no loop.

 Per frame: the simulation steps once and bumps `tick`, read only in the draw lambda.
*/

internal val SmokeBackground = Color(0xFF0B0E1A)
private val Ink = Color(0xFFEDE7DF)
private val Soft = rgba(237, 231, 223, 0.6f)
private val Ember = Color(0xFFF08A3C)
private val InkOnEmber = Color(0xFF2A1608)
private const val BACK_PATH = "M9.5 2.5 L2.5 10 L9.5 17.5"
private const val SEND_PATH = "M9 14.5 V3.8 M4.2 8.4 L9 3.6 L13.8 8.4"

private class SmokeLine(val who: String, val text: String, val time: String, val mine: Boolean, val tint: Color)

private val Thread = listOf(
    SmokeLine("Mira", "Tents are up by the pines", "7:58 PM", false, Color(0xFF7FA58A)),
    SmokeLine("You", "Bringing the kettle and cocoa", "8:05 PM", true, Ember),
    SmokeLine("Theo", "Who has the lantern?", "8:21 PM", false, Color(0xFF8C9AC8)),
)

/** Whether the frame loop is running, so a touch knows to start it again. */
private class SmokeLoop {
    var running = false
}

/** The masks as the shader reads them: bitmap pixels mapped onto the screen in dp. */
private fun smokeMaskShader(bitmap: Bitmap, bandTop: Float): BitmapShader =
    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(
            Matrix().apply {
                setScale(1f / SMOKE_ART_SCALE, 1f / SMOKE_ART_SCALE)
                postTranslate(0f, bandTop)
            },
        )
    }

/**
 * An outdoor group's chat where a notification arrives as smoke from the campfire, condensing into
 * the message and blown away by the wind.
 *
 * @param autoPlay plays the 9 s puff-form-disperse loop until touched.
 */
@Composable
fun SmokeSignalToast(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(SMOKE_SIGNAL_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { SmokeFallback() }
    val sim = remember(auto) { SmokeSim() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val loop = remember { SmokeLoop() }
    var open by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().background(SmokeBackground).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { makeSmokeGeo(w, h, safeTop, safeBottom) }
        var art by remember { mutableStateOf<SmokeArt?>(null) }
        LaunchedEffect(geo.w, geo.toastY) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) { bakeSmokeMessage(geo.w, geo.toastY) }
        }
        val ready = art
        // The shader samples the masks; until they are baked it reads an empty pixel.
        remember(shader, ready) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                if (ready != null) {
                    shader.setInputShader("mask", smokeMaskShader(ready.sharp, ready.bandTop))
                    shader.setInputShader("haze", smokeMaskShader(ready.soft, ready.bandTop))
                } else {
                    val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    shader.setInputShader("mask", smokeMaskShader(empty, 0f))
                    shader.setInputShader("haze", smokeMaskShader(empty, 0f))
                }
            }
            ready
        }

        // The loop starts at t = 0 on the first frame that has the baked message.
        LaunchedEffect(sim, geo, auto, reduced, ready != null, wake.intValue) {
            var blown: Boolean? = null
            fun after() {
                tick.intValue += 1
                // The card follows the simulation (the loop's return closes it).
                if (sim.open != open) open = sim.open
                // A light tick when a swipe has blown the message away.
                val now = sim.mode == SMOKE_MODE_LIVE && !sim.open && sim.frame.wind[2] > 0.9f
                if (now && blown == false) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                blown = now
            }
            sim.step(0f, geo, auto, reduced)
            after()
            if (ready == null || reduced) return@LaunchedEffect
            loop.running = true
            try {
                var last = -1L
                while (true) {
                    val idle = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                        last = now
                        sim.step(dt, geo, auto, false)
                        after()
                        sim.idle(auto)
                    }
                    if (idle) break
                }
            } finally {
                loop.running = false
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val d = density
            val f = sim.frame
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", max(1f, geo.w), max(1f, geo.h))
                    shader.setFloatUniform("fire", geo.fireX, geo.fireY, f.flare, geo.fireS)
                    shader.setFloatUniform("puff", f.puff)
                    shader.setFloatUniform("text", f.text)
                    shader.setFloatUniform("wind", f.wind)
                    shader.setFloatUniform("phase", f.phase - floor(f.phase))
                    shader.setFloatUniform("treeY", geo.treeY)
                    drawRect(brush, Offset.Zero, Size(geo.w, geo.h))
                } else {
                    with(fallback) { draw(geo, f, ready) }
                }
            }
        }

        SmokeHeader(safeTop)
        SmokeThread(safeTop, w)

        if (ready != null && !reduced) {
            Box(
                Modifier
                    .offset(ready.boxX.dp, ready.boxY.dp)
                    .size(ready.boxW.dp, ready.boxH.dp)
                    .semantics { contentDescription = "Kai: $SMOKE_MESSAGE. Tap to open, swipe to dismiss." }
                    .pointerInput(sim) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = density
                            sim.touch()
                            if (!loop.running) wake.intValue += 1
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dragging = false
                            var lifted = false
                            var upAt = down.uptimeMillis
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(c.uptimeMillis, c.position)
                                    if (!c.pressed) {
                                        lifted = true
                                        upAt = c.uptimeMillis
                                        break
                                    }
                                    val dx = (c.position.x - down.position.x) / d
                                    val dy = (c.position.y - down.position.y) / d
                                    // A swipe starts after 8 dp; until then it may still be a tap.
                                    if (!dragging && hypot(dx, dy) >= 8f) dragging = true
                                    if (dragging) {
                                        c.consume()
                                        if (!sim.open && sim.frame.text[3] >= 0.3f) sim.drag(dx, dy)
                                    }
                                }
                                if (dragging) {
                                    val v = tracker.calculateVelocity()
                                    sim.dragEnd(v.x / d, v.y / d)
                                } else if (lifted && upAt - down.uptimeMillis <= 350 && sim.readable() && !sim.open) {
                                    sim.openCard(true)
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } finally {
                                if (sim.dragging) sim.dragEnd(0f, 0f)
                            }
                        }
                    },
            )
        }

        SmokeCard(geo, open) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            open = false
            sim.openCard(false)
        }
        SmokeInput(geo)
    }
}

@Composable
private fun SmokeAvatar(letter: String, color: Color, sizeDp: Float, ring: Color) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(letter, style = TextStyle(color = Color(0xFFFFF6EC), fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SmokeHeader(safeTop: Float) {
    Row(
        Modifier
            .padding(start = 12.dp, end = 20.dp, top = (safeTop + 4f).dp)
            .fillMaxWidth()
            .height(SMOKE_HEADER_H.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp, 44.dp)
                .clickable(interactionSource = null, indication = null) {}
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            SmokeStrokeIcon(BACK_PATH, 12f, 20f, Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            val colors = listOf(Color(0xFFC9733A), Color(0xFF5F8A6C), Color(0xFF6573A6))
            listOf("K", "M", "T").forEachIndexed { i, l -> SmokeAvatar(l, colors[i], 32f, Color(0xFF0E1220)) }
        }
        Column(Modifier.weight(1f)) {
            BasicText("Lakeside Crew", style = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            BasicText(
                "6 around the fire · Pine Hollow",
                modifier = Modifier.padding(top = 1.dp),
                style = TextStyle(color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SmokeThread(safeTop: Float, width: Float) {
    val hairline = (1f / LocalDensity.current.density).dp
    val maxBubble = ((width - 32f) * 0.76f).dp
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = (safeTop + SMOKE_HEADER_H + 16f).dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            "TONIGHT",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 2.dp),
            style = TextStyle(color = rgba(237, 231, 223, 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
        )
        Thread.forEach { m ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp, if (m.mine) Alignment.End else Alignment.Start),
            ) {
                val time = @Composable {
                    BasicText(
                        m.time,
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = TextStyle(color = rgba(237, 231, 223, 0.38f), fontSize = 11.sp, fontFeatureSettings = "tnum"),
                    )
                }
                if (m.mine) time()
                val shape = if (m.mine) {
                    RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                } else {
                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
                }
                Column(
                    Modifier
                        .widthIn(max = maxBubble)
                        .then(if (m.mine) Modifier.cssBoxShadow(rgba(240, 138, 60, 0.25f), 4.dp, 14.dp, 18.dp) else Modifier)
                        .clip(shape)
                        .background(if (m.mine) rgba(240, 138, 60, 0.88f) else rgba(237, 231, 223, 0.09f))
                        .then(if (m.mine) Modifier else Modifier.border(hairline, rgba(237, 231, 223, 0.14f), shape))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    if (!m.mine) {
                        BasicText(
                            m.who,
                            modifier = Modifier.padding(bottom = 2.dp),
                            style = TextStyle(color = m.tint, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    BasicText(
                        m.text,
                        style = TextStyle(
                            color = if (m.mine) InkOnEmber else Ink,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = if (m.mine) FontWeight.Medium else FontWeight.Normal,
                        ),
                    )
                }
                if (!m.mine) time()
            }
        }
    }
}

@Composable
private fun SmokeCard(geo: SmokeGeo, open: Boolean, onClose: () -> Unit) {
    val hairline = (1f / LocalDensity.current.density).dp
    val shown by animateFloatAsState(
        if (open) 1f else 0f,
        tween(if (open) 320 else 220, easing = EaseOutStrong),
        label = "card",
    )
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = (geo.toastY - 92f).dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 16.dp.toPx()
                val s = 0.96f + 0.04f * shown
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(rgba(0, 0, 0, 0.4f), 2.dp, 4.dp, 26.dp)
            .cssBoxShadow(rgba(0, 0, 0, 0.5f), 24.dp, 48.dp, 26.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(rgba(30, 26, 30, 0.94f))
            .smokeInsetTop(rgba(255, 255, 255, 0.08f), 26.dp)
            .border(hairline, rgba(237, 231, 223, 0.18f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmokeAvatar("K", Color(0xFFC9733A), 36f, rgba(255, 255, 255, 0.15f))
            Column(Modifier.weight(1f)) {
                BasicText("Kai", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                BasicText(
                    "8:42 PM · Lakeside Crew",
                    style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                )
            }
        }
        BasicText(
            "$SMOKE_MESSAGE. Grilled corn and trout, bring your mugs.",
            style = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmokeReply("On my way", main = true, enabled = open, onClose = onClose)
            SmokeReply("Save me a plate", main = false, enabled = open, onClose = onClose)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmokeReply(label: String, main: Boolean, enabled: Boolean, onClose: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (main) Ember else rgba(237, 231, 223, 0.1f))
            .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (main) InkOnEmber else Ink,
                fontSize = 15.sp,
                fontWeight = if (main) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun SmokeInput(geo: SmokeGeo) {
    val hairline = (1f / LocalDensity.current.density).dp
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = geo.inputTop.dp)
            .fillMaxWidth()
            .height(SMOKE_INPUT_H.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(rgba(20, 16, 18, 0.72f))
                .border(hairline, rgba(237, 231, 223, 0.16f), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText("Message Lakeside Crew", style = TextStyle(color = rgba(237, 231, 223, 0.45f), fontSize = 16.sp), maxLines = 1)
        }
        Box(
            Modifier
                .size(48.dp)
                .cssBoxShadow(rgba(0, 0, 0, 0.4f), 1.dp, 2.dp, 24.dp)
                .cssBoxShadow(rgba(240, 138, 60, 0.35f), 6.dp, 16.dp, 24.dp)
                .clip(CircleShape)
                .background(Ember)
                .smokeInsetTop(rgba(255, 255, 255, 0.3f), 24.dp)
                .clickable(interactionSource = null, indication = null) {}
                .semantics { contentDescription = "Send" },
            contentAlignment = Alignment.Center,
        ) {
            SmokeStrokeIcon(SEND_PATH, 18f, 18f, InkOnEmber)
        }
    }
}

@Composable
private fun SmokeStrokeIcon(path: String, w: Float, h: Float, color: Color) {
    Box(
        Modifier
            .size(w.dp, h.dp)
            .drawWithCache {
                val p = PathParser().parsePathString(path).toPath()
                val stroke = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind { scale(density, density, pivot = Offset.Zero) { drawPath(p, color, style = stroke) } }
            },
    )
}

/** CSS `inset 0 1px 0 color`: a one-dp crescent of light along the top inside edge. */
private fun Modifier.smokeInsetTop(color: Color, radius: Dp) = drawWithCache {
    val r = radius.toPx()
    val dy = 1.dp.toPx()
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(r))) }
    onDrawBehind {
        clipPath(outer) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
    }
}
