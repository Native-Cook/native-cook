package com.ngocdevv.nativecook.blocks.splash.papercutdioramasplash

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
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
import kotlin.math.max
import kotlin.math.min

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Paper Cut Diorama Splash — the launch screen of a storytelling app ("Fable"). A port of
 PaperCutDioramaSplash.tsx (the reference) and PaperCutDioramaSplash.swift; the numbers are the
 same. Six layers of cut paper (dusk sky with a paper moon, far hills, pines, a meadow with a
 river, a grass edge, and a cream front frame with an arched window) form a diorama lit from
 behind the logo and wordmark cut out of the frame; the scene sways in parallax, the camera flies
 through the logo's little door into the home screen, and the home screen recedes while the
 diorama rises back from below as one piece. Every layer is baked once into a bitmap
 (PaperCutDioramaSplashArt.kt); per frame only their transforms change. Timeline in
 PaperCutDioramaSplashMotion.kt (LOOP = 9 s).

 Touch: drag anywhere to tilt the diorama: each layer slides by its depth (the front frame and
 the sky in opposite directions, ±18 dp at full tilt, rubber-banded) and springs back level on
 release (0.6 s, damping ratio 0.55, carrying the finger's speed). A touch during any other phase
 brings the scene to the lit diorama first (0.7 s, ease-in-out: from home it flies back out
 through the door, during the rebuild it finishes rising). 2 s after the last touch the tilt
 levels (0.4 s) and the loop restarts from its rest pose.
 Reduced motion: the lit diorama, still; tilting still works.

 Per frame: seven bitmaps under their own transforms, the lamp's gradient circle, and the home
 screen's layer alpha and scale.
 */

private const val HOLD_AFTER_TOUCH = 2f
private const val TILT = 18f
private val PaperInk = Color(0xFF2A2230)
private val PaperMute = Color(0xFF2A2230).copy(alpha = 0.58f)
private val PaperRust = Color(0xFFB0663E)

private class PaperCutController(val auto: Boolean, val reduced: Boolean, val riseDist: Float) {
    var frame by mutableFloatStateOf(0f)
    var wake by mutableIntStateOf(0)

    var clock = 0f
    var now = 0f
    /** 0 = autoPlay, 1 = the finger's, 2 = levelling back to the loop. */
    var mode = 0
    var touching = false
    var lastTouch = 0f
    val sceneT = PaperCutAnim(0f)
    val tiltX = PaperCutAnim(0f)
    val tiltY = PaperCutAnim(0f)
    val pose = PaperCutPose()

    init { compute() }

    fun needsFrames() = auto || mode != 0 || sceneT.busy || tiltX.busy || tiltY.busy

    private fun compute() {
        PaperCutMotion.pose(if (mode == 0) (if (reduced) PaperCutMotion.REST_SCENE else clock) else sceneT.value, riseDist, pose)
    }

    fun tick(dt: Float) {
        now += dt
        if (mode == 0) {
            if (auto) clock = (clock + dt) % PaperCutMotion.LOOP
        } else if (mode == 1 && !touching && now - lastTouch > HOLD_AFTER_TOUCH) {
            // The scene is already at rest; level the tilt and hand back to the loop at t = 0.
            mode = 2
            tiltY.tween(0f, 0.4f)
            tiltX.tween(0f, 0.4f) { clock = 0f; mode = 0 }
        }
        sceneT.step(dt); tiltX.step(dt); tiltY.step(dt)
        compute()
        frame = now
    }

    fun begin() {
        touching = true
        lastTouch = now
        if (mode != 1) {
            val from = if (mode == 0) clock else sceneT.value
            // Play the scene to the lit diorama: back out through the door from home, or on through
            // the rebuild, whichever way the rest pose is nearer in the story.
            sceneT.set(from)
            mode = 1
            sceneT.tween(if (from >= PaperCutMotion.REBUILD_AT) PaperCutMotion.LOOP else PaperCutMotion.REST_SCENE, 0.7f, PaperCutMotion.bezier(0.65f, 0f, 0.35f, 1f))
            tiltX.cancel(); tiltY.cancel()
        }
    }

    fun move(dx: Float, dy: Float) {
        lastTouch = now
        tiltX.set(PaperCutMotion.rubber(dx / 130f))
        tiltY.set(PaperCutMotion.rubber(dy / 130f))
    }

    fun end(vx: Float, vy: Float) {
        touching = false
        lastTouch = now
        tiltX.spring(0f, 0.6f, 0.55f, vx / 130f)
        tiltY.spring(0f, 0.6f, 0.55f, vy / 130f)
    }
}

private class PaperCutPainter(val L: PaperCutLayout, val art: PaperCutArt) {
    val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val screen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    val lamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(paperCutHex(0xFFF1D2), paperCutHex(0xFFC274), paperCutHex(0xF08A3E), paperCutArgb(232, 120, 60, 0f)),
            floatArrayOf(0f, 0.22f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    /** The door: every layer scales about it. */
    val ex = L.ex + L.ew / 2f
    val ey = L.ey + L.eh * 0.62f
    /** The lamp sits between the door and the wordmark, and lights only the frame below the window. */
    val gy = (ey + L.wordY) / 2f
    val dst = RectF()

    private fun transform(n: android.graphics.Canvas, c: PaperCutController, i: Int) {
        val l = c.pose.layers
        val k = (PaperCutMotion.DEPTH[i] - 0.66f) * 2.2f
        n.translate(ex + l[i * 4 + 1] + c.tiltX.value * TILT * k, ey + l[i * 4] + l[i * 4 + 2] + c.tiltY.value * TILT * 0.5f * k)
        n.scale(l[i * 4 + 3], l[i * 4 + 3])
        n.translate(-ex, -ey)
    }

    private fun layer(n: android.graphics.Canvas, a: PaperCutLayerArt, paint: Paint) {
        dst.set(a.x, a.y, a.x + a.w, a.y + a.h)
        n.drawBitmap(a.bitmap, null, dst, paint)
    }

    fun draw(n: android.graphics.Canvas, c: PaperCutController) {
        dst.set(0f, 0f, L.W, L.H)
        n.drawBitmap(art.backing, null, dst, bmp)
        val p = c.pose
        if (p.visible < 0.5f) return
        val last = PaperCutMotion.LAYERS - 1
        for (i in 0 until last) {
            n.save(); transform(n, c, i); layer(n, art.layers[i], bmp); n.restore()
        }
        val light = min(1f, p.light)
        // The lamp behind the front frame: seen only through its cut-outs.
        if (light > 0.002f) {
            n.save()
            transform(n, c, last)
            n.clipRect(-40f, L.winBottom + 2f, L.W + 40f, L.winBottom + 2f + L.H)
            val r = 130f + 60f * min(1f, max(0f, p.light - 1f))
            lamp.alpha = (light * 255f).toInt()
            n.translate(ex, gy)
            n.scale(r, r)
            n.drawCircle(0f, 0f, 1f, lamp)
            n.restore()
        }
        n.save(); transform(n, c, last); layer(n, art.layers[last], bmp); n.restore()
        if (light > 0.002f) {
            screen.alpha = (light * 255f).toInt()
            n.save(); transform(n, c, last); layer(n, art.bloom, screen); n.restore()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperCutDioramaSplash(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFEFE6D6)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = max(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value,
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().value,
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val W = maxWidth.value
        val H = maxHeight.value
        val L = remember(W, H, top) { PaperCutLayout(W, H, top) }
        val art by produceState<PaperCutArt?>(null, L) {
            delay(SETTLE_MS)
            value = withContext(Dispatchers.Default) { paperCutBake(L, min(2f, density)) }
        }
        val c = remember(auto, reduced, H) { PaperCutController(auto, reduced, H + 60f) }
        val ready = art != null

        LaunchedEffect(c, ready, c.wake) {
            if (!ready) return@LaunchedEffect
            var last = -1L
            do {
                withFrameNanos { n ->
                    val dt = if (last < 0) 0f else min(0.05f, (n - last) / 1e9f)
                    last = n
                    c.tick(dt)
                }
            } while (c.needsFrames())
        }

        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Fable. A paper-cut diorama of a river valley at dusk. Drag to tilt it." }
                .pointerInput(c) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        c.begin()
                        c.wake++
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        val start = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            if (!ch.pressed) break
                            ch.consume()
                            c.move((ch.position.x - start.x) / density, (ch.position.y - start.y) / density)
                        }
                        val vel = vt.calculateVelocity()
                        c.end(vel.x / density, vel.y / density)
                    }
                },
        ) {
            val a = art
            if (a != null) {
                val painter = remember(a) { PaperCutPainter(L, a) }
                Canvas(Modifier.fillMaxSize()) {
                    c.frame
                    drawIntoCanvas { cv ->
                        val n = cv.nativeCanvas
                        n.save()
                        n.scale(density, density)
                        painter.draw(n, c)
                        n.restore()
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        c.frame
                        alpha = c.pose.home
                        scaleX = c.pose.homeScale
                        scaleY = c.pose.homeScale
                    },
            ) {
                PaperCutHome(top, bottom)
            }
        }
    }
}

private class PaperCutShelf(val title: String, val meta: String, val color: Color)

private val paperCutShelf = listOf(
    PaperCutShelf("The Moth Who Kept Time", "8 min · Chapter 2 of 5", Color(0xFF5B4F86)),
    PaperCutShelf("Seven Paper Boats", "10 min · New tonight", Color(0xFF3F6470)),
    PaperCutShelf("Grandpa's Kite", "6 min · Finished twice", Color(0xFFF0AE88)),
    PaperCutShelf("Under the Willow Bridge", "14 min · Saved for Sunday", Color(0xFF7E9C68)),
)

private val paperCutTabs = listOf(
    "Tonight" to "M12 3a7 7 0 1 0 7.5 9.5A6 6 0 0 1 12 3z",
    "Library" to "M4 4h4v16H4zM10 4h4v16h-4zM16.5 4.6l3.6-.9 3.2 15.5-3.6.9z",
    "Profile" to "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 8a7 7 0 0 1 14 0z",
)

private val paperCutHills = listOf(
    "M0 150 C60 120 110 140 170 128 C230 116 290 136 345 120 L345 220 L0 220 Z" to Color(0xFF7F74A8).copy(alpha = 0.9f),
    "M0 175 C70 160 120 178 190 166 C250 156 300 172 345 160 L345 220 L0 220 Z" to Color(0xFF34545F),
    "M0 200 C80 188 150 204 220 194 C280 186 320 198 345 192 L345 220 L0 220 Z" to Color(0xFF2F5137),
)

@Composable
private fun PaperCutHome(top: Float, bottom: Float) {
    Box(Modifier.fillMaxSize().background(Color(0xFFFBF2E2))) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (top + 16f).dp)) {
            BasicText("FABLE", style = TextStyle(color = PaperRust, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("Good evening, Mira", Modifier.padding(top = 4.dp), style = TextStyle(color = PaperInk, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Tonight's story is ready · 12 min", Modifier.padding(top = 2.dp), style = TextStyle(color = PaperMute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            PaperCutCard()
            BasicText("Your shelf", Modifier.padding(top = 28.dp, bottom = 8.dp), style = TextStyle(color = PaperInk, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            for (s in paperCutShelf) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(s.color)
                            .drawBehind { drawRect(Color.White.copy(alpha = 0.25f), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
                    )
                    Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(s.title, style = TextStyle(color = PaperInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        BasicText(s.meta, style = TextStyle(color = PaperMute, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1)
                    }
                }
            }
        }
        // Tab bar.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFFF6EBD9))
                .drawBehind { drawRect(PaperInk.copy(alpha = 0.14f), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
                .padding(top = 10.dp, bottom = (bottom + 6f).dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            paperCutTabs.forEachIndexed { k, (label, d) ->
                val tint = if (k == 0) PaperRust else PaperInk.copy(alpha = 0.45f)
                val path = remember(d) { PathParser().parsePathString(d).toPath() }
                Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Canvas(Modifier.size(22.dp)) {
                        val s = size.width / 24f
                        scale(s, s, pivot = Offset.Zero) { drawPath(path, tint) }
                    }
                    BasicText(label, style = TextStyle(color = if (k == 0) PaperRust else PaperInk.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun PaperCutCard() {
    val hills = remember { paperCutHills.map { (d, col) -> PathParser().parsePathString(d).toPath() to col } }
    Box(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .height(220.dp)
            .cssBoxShadow(Color(40 / 255f, 30 / 255f, 50 / 255f, 0.18f), 2.dp, 4.dp, 24.dp)
            .cssBoxShadow(Color(40 / 255f, 30 / 255f, 50 / 255f, 0.18f), 16.dp, 32.dp, 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(0f to Color(0xFF26315A), 0.55f to Color(0xFF7A6496), 1f to Color(0xFFF0AE88))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The paper hills (a 345 × 220 drawing stretched to the card).
            for ((p, col) in hills) {
                scale(size.width / 345f, size.height / 220f, pivot = Offset.Zero) { drawPath(p, col) }
            }
            // The moon and its glow (box-shadow 0 0 18 rgba(255,230,190,0.7)).
            val r = 17.dp.toPx()
            val center = Offset(size.width - 56.dp.toPx() - r, 40.dp.toPx() + r)
            drawCircle(Brush.radialGradient(0f to Color(1f, 230 / 255f, 190 / 255f, 0.7f), 1f to Color(1f, 230 / 255f, 190 / 255f, 0f), center = center, radius = r + 18.dp.toPx()), r + 18.dp.toPx(), center)
            drawCircle(Color(0xFFFCE9C6), r, center)
        }
        Column(Modifier.padding(start = 20.dp, top = 22.dp, end = 120.dp)) {
            BasicText("The Lantern Fox", style = TextStyle(color = Color(0xFFFFF6E8), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            BasicText("Chapter 3 · read by Juno Vale", Modifier.padding(top = 4.dp), style = TextStyle(color = Color(0xFFFFF6E8).copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp)
                .height(36.dp)
                .background(Color(0xFFFBF2E2), CircleShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Continue", style = TextStyle(color = PaperInk, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}
