package com.ngocdevv.nativecook.blocks.modals.paperlanternconfirm

import android.graphics.RuntimeShader
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Paper Lantern Confirm — "Lantern Night", a wishes app's confirm sheet. A folded paper sky lantern
 stands in the sheet with the wish written on it. Confirm and the wick lights, the paper glows from
 inside, the lantern fills, lifts off out of the sheet and rises into the night among the other
 lanterns, smaller with distance; a "Wish sent" toast drops in, and a new lantern unfolds.

 Drawing: two Canvases. Behind the sheet, the sky (PAPER_LANTERN_SKY_AGSL: gradient, stars, moon,
 hills and the far lanterns). Above the sheet, a canvas with two lanterns (PAPER_LANTERN_BAG_AGSL in
 a rect under a transform, one shader instance each, the wish drawn as measured text): the one in
 the sheet and the one in flight. Below Android 13, PaperLanternConfirmFallback.kt draws both.
 Everything moving reads one scene object written by the frame loop, in draw and graphicsLayer
 lambdas only.

 Touch: hold the lantern to light it (the wick catches in 0.5 s, then it fills in about a second);
 drag it up while you hold it; let go of a full lantern above its seat, or flick it up, and it flies
 off into the loop's rise. A lantern let go before it is full sinks back and folds again; 2 s later
 it eases back to rest (0.9 s) and the loop restarts. Tap "Light and send" to play the send.
 Reduced motion: no loop; the lantern stands lit and full in the sheet.

 LOOP = 9 s:
   0.0 – 1.2  rest: the lantern stands folded in the sheet with the wish written on it.
   1.1 – 1.3  "Light and send" is pressed; its label rolls to "Lighting the wick…".
   1.2 – 2.0  the wick lights: a spark, a dip, then full; the paper glows from inside.
   2.0 – 3.0  it fills: the folds smooth out, with a 3 % swell and settle.
   3.0 – 6.4  it lifts off and rises into the sky, shrinking to a third, swaying 14 dp.
   5.6 – 6.8  the "Wish sent" toast drops in and leaves.
   6.4 – 8.6  it drifts on and fades among the far lanterns.
   6.8 – 8.2  a new lantern unfolds in the sheet; 8.2 the label rolls back.
   8.2 – 9.0  rest: the frame at 9 s is the frame at 0 s.
*/

private const val LOOP = 9f
/** How folded a lantern stands at rest (1 flat … 0 full). */
private const val REST_FOLD = 0.72f
/** The sheet's height above the bottom inset. */
private const val SHEET_H = 446f
/** The lantern's seat: its centre below the sheet's top, and its scale there. */
private const val SEAT_Y = 190f
private const val SEAT_SCALE = 0.8f
private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2f
private const val RETURN = 0.9f
private const val LIFT_OFF = 3.0f
private val PIf = PI.toFloat()

private val Paper = Color(0xFFF5EDDF)
private val Ink = Color(0xFF2E2119)
private val InkSoft = Color(0x992E2119)
private val Amber = Color(0xFFE9822F)
private val Labels = listOf("Light and send", "Lighting the wick…", "Wish sent")

internal class PaperLanternLayout(val sheetTop: Float, val seatX: Float, val seatY: Float, val skyY: Float, val width: Float)

private fun lanternLayout(width: Float, height: Float, insetBottom: Float): PaperLanternLayout {
    val sheetTop = height - insetBottom - SHEET_H
    return PaperLanternLayout(sheetTop, width / 2f, sheetTop + SEAT_Y, max(110f, sheetTop * 0.42f), width)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}
private fun easeOut(x: Float): Float {
    val c = clamp01(x)
    return 1f - (1f - c).pow(3)
}

/** One lantern on screen: centre, scale (x, y), opacity, fold, glow. */
internal class PaperLanternLamp {
    var x = 0f
    var y = 0f
    var sx = SEAT_SCALE
    var sy = SEAT_SCALE
    var o = 0f
    var fold = REST_FOLD
    var glow = 0f

    fun copyFrom(a: PaperLanternLamp) {
        x = a.x; y = a.y; sx = a.sx; sy = a.sy; o = a.o; fold = a.fold; glow = a.glow
    }

    fun blend(a: PaperLanternLamp, b: PaperLanternLamp, e: Float) {
        x = a.x + (b.x - a.x) * e
        y = a.y + (b.y - a.y) * e
        sx = a.sx + (b.sx - a.sx) * e
        sy = a.sy + (b.sy - a.sy) * e
        o = a.o + (b.o - a.o) * e
        fold = a.fold + (b.fold - a.fold) * e
        glow = a.glow + (b.glow - a.glow) * e
    }
}

private class LanternScene {
    val seat = PaperLanternLamp()
    val fly = PaperLanternLamp()
    var press = 0f
    /** 0 Light and send, 1 Lighting, 2 Wish sent, 3 Light and send again. */
    var label = 0
    var toast = 0f
}

/** The wick catching: a spark, a dip, then full. */
private fun ignite(t: Float): Float = when {
    t < 1.2f -> 0f
    t < 1.35f -> 0.5f * easeOut(seg(t, 1.2f, 1.35f))
    t < 1.45f -> 0.5f - 0.2f * sin(PIf * seg(t, 1.35f, 1.45f) * 0.5f)
    else -> 0.3f + 0.7f * easeOut(seg(t, 1.45f, 2.0f))
}

private fun loopScene(t: Float, l: PaperLanternLayout, s: LanternScene) {
    val seat = s.seat
    val fly = s.fly
    seat.x = l.seatX
    seat.y = l.seatY
    seat.sx = SEAT_SCALE
    seat.sy = SEAT_SCALE
    seat.fold = REST_FOLD
    seat.glow = 0f
    if (t < 3.0f) {
        val fill = seg(t, 2.0f, 3.0f)
        val swell = 1f + 0.03f * sin(PIf * fill)
        seat.o = 1f
        seat.glow = ignite(t)
        seat.fold = REST_FOLD * (1f - easeOut(fill))
        seat.sx = SEAT_SCALE * swell
        seat.sy = SEAT_SCALE * swell
    } else if (t < 6.8f) {
        seat.o = 0f
    } else {
        // A new lantern unfolds from flat.
        val u = seg(t, 6.8f, 8.0f)
        val grow = if (u < 0.75f) 0.15f + 0.89f * easeOut(u / 0.75f) else 1.04f - 0.04f * easeOut((u - 0.75f) / 0.25f)
        seat.o = easeOut(seg(t, 6.8f, 7.1f))
        seat.fold = REST_FOLD + (1f - REST_FOLD) * (1f - easeOut(seg(t, 6.9f, 8.2f)))
        seat.glow = 0f
        seat.sy = SEAT_SCALE * grow
    }
    fly.glow = 1f
    fly.fold = 0f
    if (t < 3.0f || t >= 8.6f) {
        fly.o = 0f
        fly.x = l.seatX
        fly.y = l.seatY
        fly.sx = SEAT_SCALE
        fly.sy = SEAT_SCALE
    } else {
        val dip = 3f * sin(PIf * seg(t, 3.0f, 3.15f))
        val e = easeInOut(seg(t, 3.0f, 6.4f))
        val drift = seg(t, 6.4f, 8.6f)
        fly.y = l.seatY + (l.skyY - l.seatY) * e + dip - 40f * sin(drift * PIf * 0.5f)
        fly.x = l.seatX + 14f * sin(PIf * e * 1.5f) - 10f * drift
        val k = SEAT_SCALE * (1f - 0.66f * easeOut(seg(t, 3.0f, 6.6f))) * (1f - 0.35f * drift)
        fly.sx = k
        fly.sy = k
        fly.o = 1f - easeInOut(seg(t, 7.6f, 8.6f))
    }
    s.press = sin(PIf * seg(t, 1.1f, 1.3f))
    s.label = if (t < 1.25f) 0 else if (t < 5.6f) 1 else if (t < 8.2f) 2 else 3
    s.toast = easeOut(seg(t, 5.6f, 5.95f)) * (1f - easeInOut(seg(t, 6.5f, 6.8f)))
}

/** The next label index at or after `from` whose label (index mod 3) is `state`: labels roll forward. */
private fun nextLabel(from: Float, state: Int): Float {
    val base = floor(from + 1e-3f).toInt()
    for (i in 0 until 3) if (((base + i) % 3 + 3) % 3 == state) return (base + i).toFloat()
    return base.toFloat()
}

/** Past `limit`, a pull gives less and less. */
private fun rubber(x: Float, limit: Float): Float {
    if (x <= limit) return x
    val over = x - limit
    return limit + over / (1f + over / 60f)
}

private class LanternState {
    var layout = PaperLanternLayout(0f, 0f, 0f, 110f, 0f)
    var mode = MODE_AUTO
    var clock = 0f
    var now = 0f
    /** The sky and the flames: in the loop alone it equals the clock. */
    var skyClock = 0f
    var lastTouch = 0f
    var returnAt = 0f
    val scene = LanternScene()
    val from = PaperLanternLamp()
    val blendOut = PaperLanternLamp()
    var labelPos = 0f
    // The lantern under a finger.
    var hGlow = 0f
    var hFold = REST_FOLD
    var hDy = 0f
    var hVy = 0f
    var hTarget = 0f
    var hHolding = false
    // After a launch the flyer starts where the finger let go.
    var launchDy = 0f
    var launchAt = -10f
    var posed = false
    val frame = mutableIntStateOf(0)
    val awake = mutableStateOf(true)
}

/**
 * "Lantern Night", a confirm sheet whose paper sky lantern lights, fills, lifts out of the sheet
 * and rises into a night of other lanterns, drawn by two AGSL shaders. Hold the lantern to light
 * it and drag it up to let it fly, or tap "Light and send".
 *
 * @param autoPlay plays the 9 s light-fill-rise-toast-new-lantern loop.
 */
@Composable
fun PaperLanternConfirm(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val st = remember { LanternState() }
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(screen) / screen.density
    val insetBottom = insets.getBottom(screen) / screen.density
    val shaders = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { Triple(RuntimeShader(PAPER_LANTERN_SKY_AGSL), RuntimeShader(PAPER_LANTERN_BAG_AGSL), RuntimeShader(PAPER_LANTERN_BAG_AGSL)) }.getOrNull()
        } else {
            null
        }
    }
    val brushes = remember(shaders) { shaders?.let { Triple(ShaderBrush(it.first), ShaderBrush(it.second), ShaderBrush(it.third)) } }
    val measurer = rememberTextMeasurer()
    val wish = remember(measurer, screen) {
        val big = TextStyle(color = Color(0xFF5B3B28), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp)
        val small = TextStyle(color = Color(0xFF7A5640), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 13.sp)
        listOf(measurer.measure("Safe travels", big), measurer.measure("home, Amara", big), measurer.measure("love, June", small))
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0A0D22))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = lanternLayout(width, height, insetBottom)
        st.layout = l
        if (!st.posed || reduced) {
            loopScene(if (reduced) 2.99f else 0f, l, st.scene)
            st.posed = true
        }

        LaunchedEffect(st, auto, reduced, st.awake.value) {
            if (reduced || !st.awake.value) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    lanternFrame(st, dt, auto)
                    st.frame.intValue += 1
                    !auto && st.mode == MODE_AUTO && st.clock == 0f && st.labelPos == nextLabel(st.labelPos, 0)
                }
                if (stop) {
                    st.awake.value = false
                    break
                }
            }
        }

        // The sky behind the sheet.
        Canvas(Modifier.fillMaxSize()) {
            st.frame.intValue
            val d = this.density
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaders != null && brushes != null) {
                    val sky = shaders.first
                    sky.setFloatUniform("size", width, height)
                    sky.setFloatUniform("horizon", l.sheetTop + 26f)
                    sky.setFloatUniform("phase", st.skyClock / LOOP)
                    drawRect(brushes.first, Offset.Zero, Size(width, height))
                } else {
                    paperLanternSkyFallback(width, height, l.sheetTop + 26f, st.skyClock / LOOP)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (insetTop + 18f).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("LANTERN NIGHT", style = TextStyle(color = Color(0xFFF7B266), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp))
            BasicText("Riverside · Friday 8:40 pm", style = TextStyle(color = Color(0xCCFAECDC), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }

        // The sheet.
        val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(
            Modifier
                .offset(y = l.sheetTop.dp)
                .fillMaxWidth()
                .heightIn(min = (height - l.sheetTop).dp)
                .cssBoxShadow(Color(0x73050514), (-20).dp, 50.dp, 32.dp)
                .drawBehind {
                    // A thin light edge just above the sheet's top.
                    drawRoundRect(Color(0x99FFFFFF), Offset(0f, -1.dp.toPx()), Size(size.width, 64.dp.toPx()), CornerRadius(32.dp.toPx()))
                }
                .clip(sheetShape)
                .background(Paper)
                .padding(start = 20.dp, end = 20.dp, bottom = insetBottom.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x2E2E2119)))
            BasicText(
                "Send your wish?",
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                style = TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, textAlign = TextAlign.Center),
            )
            BasicText(
                "It will rise with 1,283 other lanterns tonight",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = TextStyle(color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(206.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                LanternChip(Icons.Filled.Person, "From June")
                LanternChip(Icons.Filled.Favorite, "$5 · River Fund")
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        st.frame.intValue
                        val k = 1f - 0.03f * st.scene.press
                        scaleX = k
                        scaleY = k
                    }
                    .cssBoxShadow(Color(0x4D5A280A), 1.dp, 1.dp, 26.dp)
                    .cssBoxShadow(Color(0x59E9822F), 10.dp, 22.dp, 26.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(0f to Color(0xFFF4A24A), 0.5f to Color(0xFFE9822F), 1f to Color(0xFFC9561F)))
                    .drawBehind {
                        drawLine(Color(0x99FFE6BE), Offset(26.dp.toPx(), 1.dp.toPx()), Offset(size.width - 26.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Light and send"
                    }
                    .pointerInput(st, reduced) {
                        if (reduced) return@pointerInput
                        detectTapGestures(onTap = {
                            if (lanternSend(st)) {
                                st.awake.value = true
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                LanternRoller(st)
            }
            BasicText(
                "Edit wish",
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
        }

        // The lantern's hold area.
        Box(
            Modifier
                .offset(x = (l.seatX - 80f).dp, y = (l.seatY - 110f).dp)
                .size(160.dp, 190.dp)
                .semantics { contentDescription = "Paper lantern. Hold to light it, then drag it up to let it fly." }
                .pointerInput(st, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val took = lanternTakeOver(st)
                        if (took) {
                            st.hHolding = true
                            st.lastTouch = st.now
                            st.awake.value = true
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        var ended = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                ended = true
                                break
                            }
                            if (st.mode == MODE_USER) {
                                val ty = (change.position.y - down.position.y) / d
                                // Up is free (a full lantern pulls a little), down is rubber-banded.
                                st.hTarget = if (ty > 0f) rubber(ty, 0f) * 0.5f else -rubber(-ty, 140f) * (if (st.hFold < 0.1f) 1f else 0.6f)
                                st.lastTouch = st.now
                                change.consume()
                            }
                        }
                        if (st.mode == MODE_USER) {
                            val vy = tracker.calculateVelocity().y / d
                            if (ended && st.hFold < 0.12f && st.hGlow > 0.9f && (st.hDy < -30f || vy < -400f)) {
                                // Away it goes: the loop's rise takes over from where the finger let go.
                                st.launchDy = st.hDy
                                st.launchAt = st.now
                                st.clock = LIFT_OFF
                                st.mode = MODE_AUTO
                                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                            } else {
                                st.hTarget = 0f
                                st.hHolding = false
                                st.lastTouch = st.now
                            }
                        }
                    }
                },
        )

        // The two lanterns, over the sheet.
        Canvas(Modifier.fillMaxSize()) {
            st.frame.intValue
            val s = st.scene
            val flicker = st.skyClock / LOOP
            val seatInk = 0.82f * (1f - max(0f, s.seat.fold - REST_FOLD) / (1f - REST_FOLD))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shaders != null && brushes != null) {
                drawLantern(s.seat, shaders.second, brushes.second, flicker, 3f, wish, seatInk)
                drawLantern(s.fly, shaders.third, brushes.third, flicker, 7f, wish, 0.82f)
            } else {
                drawLantern(s.seat, null, null, flicker, 3f, wish, seatInk)
                drawLantern(s.fly, null, null, flicker, 7f, wish, 0.82f)
            }
        }

        // The "Wish sent" toast.
        Box(Modifier.fillMaxWidth().padding(top = (insetTop + 10f).dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .graphicsLayer {
                        st.frame.intValue
                        val k = st.scene.toast
                        alpha = k
                        translationY = -70f * (1f - k) * density
                    }
                    .height(52.dp)
                    .cssBoxShadow(Color(0x4D000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x66000000), 12.dp, 30.dp, 26.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF020182C))
                    .border(1.dp, Color(0x4DF7B266), RoundedCornerShape(26.dp))
                    .padding(start = 8.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF7B266)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2A1708), modifier = Modifier.size(16.dp))
                }
                Column {
                    BasicText("Wish sent", style = TextStyle(color = Color(0xFFFAEEDC), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    BasicText("Lantern 1,284 is in the sky", style = TextStyle(color = Color(0xA8FAEEDC), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

/** One lantern: the bag shader in a 180 × 240 dp rect round its centre, the wish over it. */
private fun DrawScope.drawLantern(
    p: PaperLanternLamp,
    shader: RuntimeShader?,
    brush: ShaderBrush?,
    flicker: Float,
    seed: Float,
    wish: List<TextLayoutResult>,
    ink: Float,
) {
    if (p.o <= 0.001f) return
    val d = density
    withTransform({
        translate(p.x * d, p.y * d)
        scale(p.sx * d, p.sy * d, pivot = Offset.Zero)
    }) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
            shader.setFloatUniform("fold", p.fold)
            shader.setFloatUniform("glow", p.glow)
            shader.setFloatUniform("flicker", flicker)
            shader.setFloatUniform("seed", seed)
            drawRect(brush, Offset(-90f, -130f), Size(180f, 240f), alpha = clamp01(p.o))
        } else {
            paperLanternBagFallback(p.fold, p.glow, flicker, clamp01(p.o))
        }
        // The wish, written on the paper: baselines at −22, +2 and +28 dp.
        val a = clamp01(p.o * ink)
        if (a > 0.001f) {
            scale(1f / d, 1f / d, pivot = Offset.Zero) {
                val baselines = floatArrayOf(-22f, 2f, 28f)
                for (i in 0 until 3) {
                    val t = wish[i]
                    drawText(t, topLeft = Offset(-t.size.width / 2f, baselines[i] * d - t.firstBaseline), alpha = a)
                }
            }
        }
    }
}

/** The button's label in a clipped box: the old one rolls up and out, the new one in from below. */
@Composable
private fun LanternRoller(st: LanternState) {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(24.dp).clipToBounds()) {
        for (i in -1..3) {
            BasicText(
                Labels[(i + 3) % 3],
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .graphicsLayer {
                        st.frame.intValue
                        val dd = i - st.labelPos
                        alpha = max(0f, 1f - abs(dd) * 1.2f)
                        translationY = dd * 24.dp.toPx()
                    },
                style = TextStyle(
                    color = Color(0xFF2A1708),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun LanternChip(icon: ImageVector, label: String) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x1AE9822F))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
        BasicText(label, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/** One frame: the loop, the lantern under a hand, or the ease back to rest. */
private fun lanternFrame(st: LanternState, dt: Float, auto: Boolean) {
    val l = st.layout
    val at = st.now + dt
    st.now = at
    st.skyClock = (st.skyClock + dt) % LOOP
    val s = st.scene
    when (st.mode) {
        MODE_AUTO -> {
            val running = auto || st.clock > 0f
            val next = st.clock + dt
            // Without autoPlay a send plays once and stops back at rest.
            st.clock = if (!running) 0f else if (!auto && next >= LOOP) 0f else next % LOOP
            loopScene(st.clock, l, s)
            s.fly.y += st.launchDy * exp(-(at - st.launchAt) * 4f)
        }
        MODE_USER -> {
            loopScene(0f, l, s)
            if (st.hHolding) {
                st.hGlow = min(1f, st.hGlow + dt / 0.5f)
                if (st.hGlow > 0.6f) st.hFold = max(0f, st.hFold - dt / 1.0f)
            } else {
                st.hGlow = max(0f, st.hGlow - dt / 1.5f)
                st.hFold = min(REST_FOLD, st.hFold + dt / 2.5f)
            }
            // The lantern follows the finger on a spring (response ~0.35 s, a little bounce).
            val k = 170f
            val c = 2f * 0.7f * sqrt(k)
            st.hVy += (k * (st.hTarget - st.hDy) - c * st.hVy) * dt
            st.hDy += st.hVy * dt
            s.seat.glow = st.hGlow
            s.seat.fold = st.hFold
            s.seat.y = l.seatY + st.hDy
            s.seat.o = 1f
            if (!st.hHolding && at - st.lastTouch > IDLE) {
                st.from.copyFrom(s.seat)
                st.returnAt = at
                st.mode = MODE_RETURN
            }
        }
        else -> {
            val e = easeInOut((at - st.returnAt) / RETURN)
            loopScene(0f, l, s)
            st.blendOut.blend(st.from, s.seat, e)
            s.seat.copyFrom(st.blendOut)
            if (e >= 1f) {
                st.mode = MODE_AUTO
                st.clock = 0f
            }
        }
    }
    // The button's label rolls forward to the scene's label (index mod 3).
    var pos = st.labelPos
    var target = nextLabel(pos, s.label % 3)
    if (pos >= 2f && target >= 3f) {
        pos -= 3f
        target -= 3f
    }
    pos += (target - pos) * min(1f, dt * 9f)
    if (abs(target - pos) < 0.002f) pos = target
    st.labelPos = pos
}

/** Only a lantern standing in the sheet can be held. */
private fun lanternTakeOver(st: LanternState): Boolean {
    if (st.mode == MODE_USER) return true
    val s = st.scene
    if (s.seat.o < 0.95f || s.fly.o > 0.02f) return false
    st.hGlow = s.seat.glow
    st.hFold = s.seat.fold
    st.hDy = s.seat.y - st.layout.seatY
    st.hVy = 0f
    st.hTarget = 0f
    st.hHolding = true
    st.mode = MODE_USER
    return true
}

/** "Light and send" from rest plays the send. */
private fun lanternSend(st: LanternState): Boolean {
    val t = st.clock
    val atRest = (st.mode == MODE_AUTO && (t < 1.1f || t >= 8.2f)) || (st.mode == MODE_USER && st.hGlow < 0.05f)
    if (!atRest) return false
    st.launchDy = 0f
    st.launchAt = -10f
    st.hHolding = false
    st.clock = 1.1f
    st.mode = MODE_AUTO
    return true
}
