package com.ngocdevv.nativecook.blocks.media.viewmasterreel

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Viewmaster Reel — a travel-memories screen ("Reels — Iceland, 2025"). A port of
 ViewmasterReel.tsx (the reference) and ViewmasterReel.swift; the numbers are the same. A red
 stereo viewer's window shows the current slide; under it the cardboard reel with its seven film
 chips, and the viewer's lever at its right. Pull the lever down or turn the reel with a finger:
 the reel turns one slot with a mechanical clunk, and the window blinks (its shutter closes
 between slots) to the next slide. Timeline in ViewmasterReelMotion.kt (LOOP = 9 s).

 Touch: a finger on the reel turns it about its centre (the angle under the finger); on release
 it springs to the nearest slot the flick points at (0.5 s, damping ratio 0.55, carrying the
 angular velocity). The lever follows a downward drag (rubber-banded past its stop); past 82 % it
 advances the reel one slot (spring 0.42 s, damping 0.5) and springs back on release (damping
 0.45). A tick per slot, a light tap per lever pull. 2 s after the last touch the reel eases back
 to slide 1 (0.9 s) and the loop restarts. Reduced motion: slide 1, still; the reel and lever
 still answer the finger without the wobble.

 Per frame: bitmaps only (room, one photo, the lens overlay, the turning reel, the gate overlay,
 the turning lever) plus two shutter rects and the pivot cap; the captions move in layers.
 */

private val ViewmasterBg = Color(0xFF121C23)
private val ViewmasterInk = Color(0xFFEEF2F3)
private val ViewmasterMute = Color(0xFFEEF2F3).copy(alpha = 0.56f)
private val ViewmasterAccent = Color(0xFFE0574A)
private const val HOLD_AFTER_TOUCH = 2f
private val STEP_RAD = (ViewmasterReelMotion.STEP_DEG * PI / 180.0).toFloat()

private class ViewmasterController(val L: ViewmasterReelLayout, val auto: Boolean, val reduced: Boolean) {
    var frame by mutableFloatStateOf(0f)
    var wake by mutableIntStateOf(0)

    var clock = 0f
    var now = 0f
    var mode = 0
    var touching = false
    var lastTouch = 0f
    val manPos = ViewmasterReelAnim(0f, 0.002f)
    val manLever = ViewmasterReelAnim(0f)
    /** 0 none, 1 the reel, 2 the lever. */
    var grab = 0
    var lastAng = 0f
    var leverStart = 0f
    var fired = false
    private var prevSlot: Int? = null
    private val pose = FloatArray(3)

    /** [pos, lever, shake, wobble time] */
    val v = FloatArray(4)

    init { compute() }

    val idx get() = ViewmasterReelMotion.wrapInt(round(v[0]).toInt(), ViewmasterReelMotion.SLOTS)
    val shut get() = ViewmasterReelMotion.shutterFor(v[0])
    val leverDeg get() = VIEWMASTER_LEVER_REST + (VIEWMASTER_LEVER_PULLED - VIEWMASTER_LEVER_REST) * v[1]

    fun needsFrames() = auto || mode != 0 || manPos.busy || manLever.busy

    private fun compute() {
        if (mode == 0) {
            ViewmasterReelMotion.pose(clock, pose)
            v[0] = pose[0]; v[1] = pose[1]
            v[2] = if (reduced) 0f else pose[2]
            v[3] = if (reduced) 0f else clock
        } else {
            v[0] = manPos.value; v[1] = manLever.value; v[2] = 0f
            v[3] = if (reduced) 0f else now % ViewmasterReelMotion.LOOP
        }
    }

    fun tick(dt: Float, view: View) {
        now += dt
        if (mode == 0) {
            if (auto) clock = (clock + dt) % ViewmasterReelMotion.LOOP
        } else if (mode == 1 && !touching && now - lastTouch > HOLD_AFTER_TOUCH) {
            mode = 2
            val ease = ViewmasterReelMotion.bezier(0.65f, 0f, 0.35f, 1f)
            manLever.tween(0f, 0.3f)
            val slots = ViewmasterReelMotion.SLOTS.toFloat()
            manPos.tween(round(manPos.value / slots) * slots, 0.9f, ease) { clock = 0f; mode = 0 }
        }
        manPos.step(dt); manLever.step(dt)
        compute()
        // A tick per slot that passes the gate under the finger.
        val cur = if (mode == 1) round(v[0]).toInt() else null
        val prev = prevSlot
        if (cur != null && prev != null && cur != prev) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        prevSlot = cur
        frame = now
    }

    fun begin(x: Float, y: Float): Boolean {
        val a = Math.toRadians(leverDeg.toDouble()).toFloat()
        val kx = L.px + cos(a) * L.len
        val ky = L.py + sin(a) * L.len
        val onLever = hypot(x - kx, y - ky) < 48f
        val onDisc = hypot(x - L.cx, y - L.cy) < L.r + 12f
        if (!onLever && !onDisc) { grab = 0; return false }
        touching = true
        lastTouch = now
        manPos.cancel(); manLever.cancel()
        if (mode != 1) {
            manPos.set(v[0]); manLever.set(v[1]); mode = 1
        }
        if (onLever) {
            grab = 2; leverStart = manLever.value; fired = false
        } else {
            grab = 1; lastAng = atan2(y - L.cy, x - L.cx)
        }
        return true
    }

    fun move(x: Float, y: Float, dx: Float, dy: Float, view: View) {
        if (grab == 0) return
        lastTouch = now
        if (grab == 1) {
            val ang = atan2(y - L.cy, x - L.cx)
            var d = ang - lastAng
            if (d > PI) d -= (2 * PI).toFloat()
            if (d < -PI) d += (2 * PI).toFloat()
            lastAng = ang
            manPos.set(manPos.value - d / STEP_RAD)
        } else {
            val lv = ViewmasterReelMotion.rubber(leverStart + dy / 72f - dx / 220f, 0f, 1f, 0.22f)
            manLever.set(lv)
            if (lv > 0.82f && !fired) {
                fired = true
                manPos.spring(round(manPos.value) + 1f, 0.42f, 0.5f)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else if (lv < 0.3f) {
                fired = false
            }
        }
    }

    fun end(x: Float, y: Float, vx: Float, vy: Float) {
        val g = grab
        if (g == 0) return
        grab = 0
        touching = false
        lastTouch = now
        if (g == 1) {
            val rx = x - L.cx
            val ry = y - L.cy
            val omega = (rx * vy - ry * vx) / max(400f, rx * rx + ry * ry)
            val vel = -omega / STEP_RAD
            manPos.spring(round(manPos.value + vel * 0.12f), 0.5f, 0.55f, vel)
        } else {
            manLever.spring(0f, 0.38f, 0.45f)
        }
    }
}

private class ViewmasterPainter(val L: ViewmasterReelLayout, val art: ViewmasterReelArt) {
    val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val lid = Paint().apply { color = viewmasterHex(0x0B0909) }
    val black = Paint().apply { color = viewmasterHex(0x0A0A0B) }
    val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = viewmasterArgb(255, 236, 200, 0.22f)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(L.px - 3f, L.py - 4f, 13f, intArrayOf(viewmasterHex(0xFBF7F2), viewmasterHex(0xA8A29C), viewmasterHex(0x57524D)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    val winClip = Path().apply { addRoundRect(L.win, 18f, 18f, Path.Direction.CW) }
    val full = RectF(0f, 0f, L.W, L.H)
    val src = Rect()
    val dst = RectF()

    fun draw(n: android.graphics.Canvas, c: ViewmasterController) {
        val v = c.v
        val Wn = L.win
        n.drawBitmap(art.room, null, full, bmp)

        // The viewer's window.
        n.save()
        n.translate(0f, v[2] * 0.5f)
        n.save()
        n.clipPath(winClip)
        n.drawRect(Wn, black)
        val w = (2f * PI.toFloat() * v[3]) / ViewmasterReelMotion.LOOP
        n.save()
        n.translate(Wn.centerX(), Wn.centerY())
        n.translate(3.5f * sin(2f * w), 2.2f * sin(3f * w + 0.7f) + v[2])
        val s = 1.06f + 0.012f * sin(w)
        n.scale(s, s)
        n.translate(-Wn.centerX(), -Wn.centerY())
        val sl = viewmasterSlides[c.idx]
        val photo = art.photos[sl.photo]
        val cr = sl.crop
        val sx = Wn.width() / cr[2]
        val sy = Wn.height() / cr[3]
        dst.set(Wn.left - cr[0] * sx, Wn.top - cr[1] * sy, Wn.left - cr[0] * sx + photo.width * sx, Wn.top - cr[1] * sy + photo.height * sy)
        n.drawBitmap(photo, null, dst, bmp)
        n.restore()
        val shut = c.shut
        val hh = (Wn.height() / 2f + 2f) * shut
        if (hh > 0.01f) {
            n.drawRect(Wn.left, Wn.top - 2f, Wn.right, Wn.top - 2f + hh, lid)
            n.drawRect(Wn.left, Wn.bottom + 2f - hh, Wn.right, Wn.bottom + 2f, lid)
        }
        n.drawBitmap(art.lens, null, Wn, bmp)
        n.restore()
        n.restore()

        // The reel, turned; then the light and the gate that do not turn.
        n.save()
        n.rotate(-v[0] * ViewmasterReelMotion.STEP_DEG, L.cx, L.cy)
        dst.set(L.cx - L.r, L.cy - L.r, L.cx + L.r, L.cy + L.r)
        n.drawBitmap(art.reel, null, dst, bmp)
        n.restore()
        n.drawBitmap(art.over, null, full, bmp)
        val chipW = L.r * 0.3f
        val chipH = chipW * 0.75f
        val gateY = L.cy - L.r * 0.56f
        n.drawRect(L.cx - chipW / 2f, gateY - chipH / 2f, L.cx + chipW / 2f, gateY + chipH / 2f, screen)

        // The lever and its chrome pivot cap.
        n.save()
        n.rotate(c.leverDeg, L.px, L.py)
        val pad = VIEWMASTER_LEVER_PAD
        dst.set(L.px - pad, L.py - pad, L.px + L.len + pad, L.py + pad)
        n.drawBitmap(art.lever, null, dst, bmp)
        n.restore()
        n.drawCircle(L.px, L.py, 11f, cap)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewmasterReel(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced

    BoxWithConstraints(modifier.fillMaxSize().background(ViewmasterBg).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = max(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value,
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().value,
        )
        val W = maxWidth.value
        val H = maxHeight.value
        val L = remember(W, H, top) { ViewmasterReelLayout(W, H, top) }
        val art by produceState<ViewmasterReelArt?>(null, L) {
            delay(SETTLE_MS)
            value = withContext(Dispatchers.Default) { viewmasterBake(context, L, min(3f, density)) }
        }
        val c = remember(L, auto, reduced) { ViewmasterController(L, auto, reduced) }
        val ready = art != null

        LaunchedEffect(c, ready, c.wake) {
            if (!ready) return@LaunchedEffect
            var last = -1L
            do {
                withFrameNanos { n ->
                    val dt = if (last < 0) 0f else min(0.05f, (n - last) / 1e9f)
                    last = n
                    c.tick(dt, view)
                }
            } while (c.needsFrames())
        }

        val a = art
        if (a != null) {
            val painter = remember(a) { ViewmasterPainter(L, a) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "A stereo viewer reel of Iceland. Turn the reel or pull the lever down for the next view." }
                    .pointerInput(c) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!c.begin(down.position.x / density, down.position.y / density)) return@awaitEachGesture
                            c.wake++
                            val vt = VelocityTracker()
                            vt.addPosition(down.uptimeMillis, down.position)
                            val start = down.position
                            var last = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                vt.addPosition(ch.uptimeMillis, ch.position)
                                last = ch.position
                                if (!ch.pressed) break
                                ch.consume()
                                c.move(ch.position.x / density, ch.position.y / density, (ch.position.x - start.x) / density, (ch.position.y - start.y) / density, view)
                            }
                            val vel = vt.calculateVelocity()
                            c.end(last.x / density, last.y / density, vel.x / density, vel.y / density)
                        }
                    },
            ) {
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

        Column(Modifier.offset(x = 24.dp, y = (top + 12f).dp).width((W - 48f).dp)) {
            BasicText("REELS", style = TextStyle(color = ViewmasterAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("Iceland, 2025", Modifier.padding(top = 4.dp), style = TextStyle(color = ViewmasterInk, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("7 views · Ring Road, June · 1,332 km", Modifier.padding(top = 2.dp), style = TextStyle(color = ViewmasterMute, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1)
        }

        // Captions: one per slide (and slide 1 again), rolling up out of a clipped box.
        Box(Modifier.offset(x = 24.dp, y = L.captionY.dp).width((W - 120f).dp).height(VIEWMASTER_CAPTION_H.dp).clipToBounds()) {
            val captions = viewmasterSlides + viewmasterSlides[0]
            captions.forEachIndexed { k, s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(VIEWMASTER_CAPTION_H.dp)
                        .graphicsLayer {
                            c.frame
                            val d = k - ViewmasterReelMotion.roll(c.v[0])
                            alpha = max(0f, 1f - abs(d) * 1.8f)
                            translationY = d * VIEWMASTER_CAPTION_H * density
                        },
                    verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(
                        "VIEW ${((k % ViewmasterReelMotion.SLOTS) + 1).toString().padStart(2, '0')} / 07",
                        style = TextStyle(color = ViewmasterAccent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, fontFeatureSettings = "tnum"),
                    )
                    BasicText(s.title, style = TextStyle(color = ViewmasterInk, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(s.place, style = TextStyle(color = ViewmasterMute, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
