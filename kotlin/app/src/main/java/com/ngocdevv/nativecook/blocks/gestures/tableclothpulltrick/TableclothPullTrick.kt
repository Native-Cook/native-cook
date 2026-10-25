package com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.CLOTH_LEN
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.OUT
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.THROW_BACK
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.YANK
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.clamp
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.easeInOut
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.easeOut
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.seg
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Tablecloth Pull Trick — a dinner-party planner ("Supper Club — Table for six") whose hero is the
 set table seen from above the front: a checked linen cloth (a textured mesh) under six place
 settings, wine glasses and two candles. Grab the hem and yank: fast enough (> 1300 pt/s at the
 release) and the cloth whips off in 0.55 s, a wave running through it toward you, ghosts and speed
 streaks behind it, while each dish hops as the cloth leaves from under it and jiggles; too slow
 and the dishes slide along with it (up to 40 pt) and the glasses lean. A clean pull rolls the
 counter up and shows a badge. The cloth is then thrown back over the table and billows down.

 One Canvas: a baked room and walnut table, the cloth mesh (baked linen bitmap + a colour-only
 shading mesh), candlelight pooled on it, the dishes back to front (baked sprites), the counter
 and the badge. The frame loop only moves numbers; only the Canvas reads them.

 Touch takes over at once. 2 s after the last touch the cloth is thrown back on (2.4 s, as in the
 loop) or, if it is still on the table, eases back (1 s), the dishes go home, and the loop
 restarts. Reduced motion: no loop; a pull jumps to its result.
 */

private const val IDLE = 2.0
private const val RESET = 1.0
private const val FAST = 1300.0
private const val MAX_SLIDE = 40.0

internal val ClothBackground = Color(0xFF1E1513)

private class ClothArt(val room: Bitmap, val linen: BitmapShader, val sprites: ClothSprites)

/** The finger's state and the drawing's pose. mode: 0 loop / rest, 1 a finger, 2 the cloth going back on. */
private class ClothState {
    var mode = 0
    var autoStart = -1.0
    var now = -1.0
    var lastTouch = -1.0
    var pulling = false
    var grabD = 0.0
    var grabbed = false
    // The finger's pull, its velocity, the dishes' drift, glass lean spring, events.
    var d = 0.0
    var v = 0.0
    var slide = 0.0
    var tip = 0.0
    var tipV = 0.0
    var wave = 0.0
    var fling = 0.0
    var whipAt = -1.0
    var whipFrom = 0.0
    var jig = 0.0
    var count = 3.0
    var shown = 3.0
    var cleanAt = -1.0
    // The way back.
    var backAt = 0.0
    var backD = 0.0
    var backSlide = 0.0
    var backCount = 3.0
    var backThrown = false
    val pose = ClothPose()
    val frame = mutableIntStateOf(0)

    fun resetUser() {
        d = 0.0; v = 0.0; slide = 0.0; tip = 0.0; tipV = 0.0; wave = 0.0; fling = 0.0
        whipAt = -1.0; whipFrom = 0.0; jig = 0.0; count = 3.0; shown = 3.0; cleanAt = -1.0
    }
}

/**
 * A dinner table set for six on a linen cloth: grab the hem and yank it fast to pull the cloth out
 * from under the dishes.
 *
 * @param autoPlay yanks the cloth (a clean pull), then throws it back on, on a 9 s loop.
 */
@Composable
fun TableclothPullTrick(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { ClothState() }
    val kit = remember { ClothKit() }
    val fonts = remember { ClothFonts() }
    val scratch = remember { ClothScratch() }
    val mesh = remember { ClothMesh() }
    var wake by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ClothBackground)
            .semantics { contentDescription = "A table set for six. Grab the tablecloth's hem and yank it fast to pull it out from under the dishes." },
    ) {
        val geo = remember(maxWidth, maxHeight, safeTop, safeBottom) { ClothGeo(maxWidth.value, maxHeight.value, safeTop, safeBottom) }
        var art by remember { mutableStateOf<ClothArt?>(null) }
        val density = screen.density

        LaunchedEffect(geo, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) {
                val scale = min(density, 2f)
                val room = clothBake(geo.w, geo.h, scale) { clothDrawRoom(it, ClothKit(), geo) }
                val linen = clothBake(CLOTH_W.toFloat(), CLOTH_LEN.toFloat(), TEX_SCALE) { clothDrawLinen(it, ClothKit()) }
                ClothArt(room, clothLinenShader(linen), ClothSprites(clothSpriteScale(density)))
            }
        }

        val ready = art != null
        LaunchedEffect(geo, auto, reduced, ready, wake) {
            if (!ready) return@LaunchedEffect
            state.now = -1.0
            while (true) {
                val keepGoing = withFrameNanos { nanos -> clothStep(state, nanos / 1e9, auto) }
                state.frame.intValue += 1
                if (!keepGoing) break
            }
        }

        val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val spritePaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val roomRect = remember(geo) { RectF(0f, 0f, geo.w, geo.h) }
        val ghost = remember { ClothPose() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geo, reduced) {
                    val front = DoubleArray(3)
                    geo.project(0.0, 0.0, 0.0, front)
                    val frontY = front[1]
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val y0 = down.position.y / d
                        if (y0 < frontY - 50 || y0 > geo.panelTop) return@awaitEachGesture
                        val s = state
                        if (s.mode != 1) {
                            // Take over from the loop (or the reset) where it is.
                            val p = s.pose
                            s.resetUser()
                            s.d = if (p.d >= OUT - 1) 0.0 else p.d
                            s.slide = p.slide
                            s.count = p.count.roundToInt().toDouble()
                            s.shown = p.count
                        }
                        s.mode = 1
                        s.autoStart = -1.0
                        s.lastTouch = s.now
                        wake++
                        if (s.whipAt >= 0 || s.d >= OUT - 1) return@awaitEachGesture
                        s.pulling = true
                        s.grabD = s.d
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var vy = 0.0
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                vy = (tracker.calculateVelocity().y / d).toDouble()
                                if (!change.pressed) break
                                s.lastTouch = s.now
                                val raw = s.grabD + (change.position.y - down.position.y) / d * 1.15
                                val nd = if (raw < 0) raw / (1 - raw / 30) else min(raw, CLOTH_LEN + 40)
                                val delta = nd - s.d
                                val fast = vy > FAST
                                if (delta > 0) {
                                    // Friction drags the dishes along with a slow pull; a fast one leaves them behind.
                                    s.slide = min(MAX_SLIDE, s.slide + delta * (if (fast) 0.02 else 0.85))
                                    s.tipV += delta * (if (fast) 3 else 8)
                                }
                                s.v = vy * 1.15
                                s.wave = clamp(abs(vy) / 120, 0.0, 12.0)
                                s.fling = clamp(vy / 3000, 0.0, 0.6)
                                s.d = nd
                                change.consume()
                            }
                        } finally {
                            s.pulling = false
                            s.lastTouch = s.now
                            val t1 = s.now
                            if (vy > FAST && s.d > 16) {
                                val clean = s.slide < 12
                                s.whipAt = if (reduced) t1 - YANK - 2 else t1
                                s.whipFrom = s.d
                                s.jig = if (clean) 1.0 else 1.5
                                if (clean) {
                                    s.count += 1
                                    s.cleanAt = t1 + 0.25
                                }
                                if (clean) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            } else {
                                s.v = min(s.v, 0.0)
                                s.tipV -= 60
                            }
                        }
                    }
                },
        ) {
            state.frame.intValue
            val a = art ?: return@Canvas
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val p = state.pose
                nc.save()
                nc.scale(density, density)
                kit.begin()
                nc.drawBitmap(a.room, null, roomRect, bitmapPaint)
                // Motion blur on the yank: two fading ghosts a few hundredths of a second behind.
                if (p.speed > 300) {
                    ghost.copyFrom(p)
                    clothDraw(nc, kit, scratch, geo, mesh, a.linen, ghost, p.d - p.speed * 0.06, 0.16f)
                    clothDraw(nc, kit, scratch, geo, mesh, a.linen, ghost, p.d - p.speed * 0.03, 0.3f)
                }
                clothDraw(nc, kit, scratch, geo, mesh, a.linen, p, p.d)
                clothDrawStreaks(nc, kit, scratch, geo, p)
                clothDrawCandlePools(nc, kit, scratch, geo, p.time)
                clothDrawDishes(nc, kit, scratch, geo, a.sprites, spritePaint, p)
                // Thrown back on, the cloth is above the dishes: drawn over them, fading under as it lands.
                if (p.lift > 8) {
                    val k = clamp((p.lift - 8) / 52, 0.0, 1.0)
                    clothDraw(nc, kit, scratch, geo, mesh, a.linen, p, p.d, (k * k * (3 - 2 * k)).toFloat())
                }
                clothDrawHud(nc, kit, scratch, geo, fonts, safeTop, p)
                nc.restore()
            }
        }

        if (ready) {
            Column(
                Modifier.padding(start = 24.dp, end = 120.dp, top = (safeTop + 16).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText("SUPPER CLUB", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Color(0x80F4E7D4)))
                BasicText("Table for six", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Color(0xFFFBF0DF)))
                BasicText("Sat 19 Oct · 7:30 pm · 14 Hollis Lane", style = TextStyle(fontSize = 14.sp, color = Color(0x99F4E7D4)))
            }
            ClothPanel(Modifier.offset(y = geo.panelTop.dp), safeBottom)
        }
    }
}

/** One frame of the clock, the touch state and the pose. Returns whether to keep running. */
private fun clothStep(s: ClothState, t1: Double, auto: Boolean): Boolean {
    val first = s.now < 0
    val dt = if (first) 0.0 else min(t1 - s.now, 0.05)
    s.now = t1
    if (s.lastTouch < 0) s.lastTouch = t1
    if (first && auto && s.mode == 0) s.autoStart = t1
    val loopT = ((t1 % LOOP) + LOOP) % LOOP
    val p = s.pose
    if (s.mode == 0 && s.autoStart >= 0) {
        ClothMotion.autoPose((t1 - s.autoStart) % LOOP, p)
        return true
    }
    if (s.mode == 0) {
        // At rest without the loop: one still frame.
        p.rest(0.0, 3.0)
        return false
    }
    if (s.mode == 2) {
        // Back to rest: thrown back on if the cloth is gone, else eased back; the dishes go home.
        val r = t1 - s.backAt
        p.rest(loopT, 3.0)
        val done: Boolean
        if (s.backThrown) {
            ClothMotion.throwBack(r, p)
            done = r >= THROW_BACK
        } else {
            p.d = s.backD * (1 - easeOut(seg(r, 0.0, RESET)))
            done = r >= RESET
        }
        val e = easeInOut(seg(r, 0.2, if (s.backThrown) 2.0 else RESET))
        p.slide = s.backSlide * (1 - e)
        p.count = s.backCount + (3 - s.backCount) * e
        if (done) {
            s.resetUser()
            s.mode = 0
            s.autoStart = t1
        }
        return true
    }
    var speed = 0.0
    var yankAge = -1.0
    if (s.whipAt >= 0) {
        // The yank, as in the loop: u^1.6 over 0.55 s, a wave and a whip down the drape.
        val age = t1 - s.whipAt
        s.d = ClothMotion.yankD(s.whipFrom, age)
        speed = ClothMotion.yankSpeed(s.whipFrom, age)
        s.fling = 0.85 * sin(PI * clamp((age - 0.1) / 0.75, 0.0, 1.0))
        s.wave = 26 * sin(PI * clamp((age - 0.03) / 0.67, 0.0, 1.0))
        yankAge = age
        if (age >= YANK + 1.7) s.whipAt = -1.0
    } else if (!s.pulling && s.d < OUT - 1 && dt > 0) {
        // Let go slowly: the cloth springs back under the dishes (ω 9, ζ 0.75).
        val steps = ceil(dt * 240).toInt()
        val h = dt / steps
        repeat(steps) {
            val acc = -81 * s.d - 13.5 * s.v
            s.v += acc * h
            s.d += s.v * h
        }
        s.wave *= exp(-dt * 4)
        s.fling *= exp(-dt * 6)
    }
    // Glass lean: a spring back to upright (ω 14, ζ 0.18 — glasses wobble).
    if (dt > 0) {
        val steps = ceil(dt * 240).toInt()
        val h = dt / steps
        repeat(steps) {
            val acc = -196 * s.tip - 5 * s.tipV
            s.tipV += acc * h
            s.tip += s.tipV * h
        }
    }
    s.shown += (s.count - s.shown) * (1 - exp(-dt * 9))
    val badge = if (s.cleanAt >= 0) easeOut(seg(t1, s.cleanAt, s.cleanAt + 0.3)) * (1 - seg(t1, s.cleanAt + 2.2, s.cleanAt + 2.6)) else 0.0
    p.rest(loopT, s.shown)
    p.d = s.d
    p.speed = speed
    p.wave = s.wave
    p.fling = s.fling
    p.slide = s.slide
    p.yankAge = yankAge
    p.yankFrom = s.whipFrom
    p.jig = s.jig
    p.tip = s.tip
    p.badge = badge
    val idle = !s.pulling && s.whipAt < 0 && t1 - s.lastTouch > IDLE
    if (auto && idle) {
        s.mode = 2
        s.backAt = t1
        s.backD = s.d
        s.backSlide = s.slide
        s.backCount = s.shown
        s.backThrown = s.d >= OUT - 1
        return true
    }
    if (auto) return true
    // Without the loop the frames stop once nothing moves any more.
    val still = !s.pulling && s.whipAt < 0 && (s.d >= OUT - 1 || (abs(s.d) < 0.01 && abs(s.v) < 0.01)) &&
        abs(s.tip) < 0.005 && abs(s.tipV) < 0.01 && abs(s.shown - s.count) < 1e-3 && s.wave < 0.01 && s.fling < 0.001 &&
        (s.cleanAt < 0 || t1 > s.cleanAt + 2.6)
    return !still
}

private val PanelInk = Color(0xFFFBF0DF)
private val Guests = listOf("A" to Color(0xFFC0603A), "B" to Color(0xFF4F7A3A), "C" to Color(0xFF7A5BA6), "D" to Color(0xFFB8873A), "E" to Color(0xFF3F7F8C), "F" to Color(0xFFA8475E))

/** The sheet under the table: who is coming, and the button. */
@Composable
private fun ClothPanel(modifier: Modifier, safeBottom: Float) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val panel = Color(0xFF211714)
    Column(
        modifier
            .fillMaxSize()
            .cssBoxShadow(Color.Black.copy(alpha = 0.45f), (-16).dp, 32.dp, 28.dp)
            .background(panel, shape)
            .border(Dp.Hairline, Color(0x14FFECD2), shape)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = maxOf(safeBottom, 12f).dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width((32 + 24 * (Guests.size - 1)).dp).height(32.dp)) {
                Guests.forEachIndexed { i, (letter, color) ->
                    Box(
                        Modifier
                            .offset(x = (24 * i).dp)
                            .size(32.dp)
                            .background(color, CircleShape)
                            .border(2.dp, panel, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(letter, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFF4E4)))
                    }
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("6 of 6 coming", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PanelInk))
                BasicText(
                    "Yank the hem — fast — before dessert",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 13.sp, color = Color(0x8CF4E7D4)),
                )
            }
        }
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    val k = if (pressed) 0.97f else 1f
                    scaleX = k
                    scaleY = k
                }
                .cssBoxShadow(Color(0xFFE9B872).copy(alpha = 0.18f), 8.dp, 22.dp, 28.dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.3f), 1.dp, 2.dp, 28.dp)
                .background(Color(0xFFE9B872), RoundedCornerShape(28.dp))
                .clickable(interactionSource = source, indication = null, role = Role.Button) {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Send the menu", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2412)))
        }
    }
}
