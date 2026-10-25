package com.ngocdevv.nativecook.blocks.media.darkroomtraydevelop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Darkroom Tray Develop — "Safelight", a film app's print step under a red safelight. A blank
 fibre print lies in the developer; rocking the tray sends a wave across it and the image comes
 up, shadows first, unevenly where the liquid front reaches it last. Then the stop bath, a FIXED
 stamp, the room light flickers on to show the print in white light, and back to red: the print
 slides out and a fresh sheet slides in.

 Drawing: one Canvas with one AGSL shader (DarkroomTrayDevelopShader.kt) that takes the photo as a
 child shader (one bundled 560 × 700 JPEG, decoded once off the main thread) and draws bench, tray,
 liquid, tongs and the two sheets; below Android 13, DarkroomTrayDevelopFallback.kt. Uniforms come
 from one frame object written by the frame loop; the header, timer, stamp and stage pills read it
 in graphicsLayer, draw and text-colour lambdas, so nothing recomposes per frame.

 Touch: drag sideways to rock the tray: the tilt follows the finger (rubber-banded past a full tip),
 springs back on release, and the faster it rocks the faster the print develops. A fully developed
 print hands over to the loop's stop-fix-lights sequence; otherwise, 2 s after the finger lifts,
 the print slides out and a fresh sheet comes in (the loop's last phase). Reduced motion: no loop
 and no wave; the finished print in white light, and dragging does nothing.

 LOOP = 9 s:
   0.0 – 1.2  rest: a fresh sheet with a faint latent image, the liquid swaying gently.
   1.2 – 5.0  rocking (period 1.25 s), the image comes up shadows first; the timer runs 0:00 → 1:30.
   5.0 – 5.8  stop bath: the liquid turns faintly amber and clears into the fixer.
   5.3        the FIXED stamp lands (scale 1.3 → 1 with a small overshoot).
   5.6 – 6.55 the room light flickers on (white), holds, and goes back to red.
   6.6 – 8.1  the print slides out to the right, a fresh sheet slides in from the left.
   8.1 – 9.0  rest: the frame at 9 s is the frame at 0 s.
*/

private const val LOOP = 9f
private const val DEV_SECONDS = 90f
private const val ROCK_PERIOD = 1.25f
/** The faint latent image a sheet shows within 0.6 s of landing in the developer. */
private const val LATENT = 0.14f
/** Whole number of wave crests a loop, so the wave phase repeats. */
private const val WAVES = 7f
private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val IDLE = 2f
/** Where the loop picks up after a touch: the stop bath for a finished print, the exchange otherwise. */
private const val RESUME_FIXED = 5.0f
private const val RESUME_EXCHANGE = 6.6f
private const val PHOTO_W = 560f
private const val PHOTO_H = 700f
private val PIf = PI.toFloat()

private val InkRed = Color(0xFFF2B9AD)
private val InkWhite = Color(0xFFF3F0EA)
private val SoftRed = Color(0x9EF2B9AD)
private val SoftWhite = Color(0xA8F3F0EA)
private const val DIGIT_H = 40

private class DarkroomStage(val name: String, val time: String)
private val Stages = listOf(DarkroomStage("Develop", "1:30"), DarkroomStage("Stop", "0:30"), DarkroomStage("Fix", "2:00"))

/** The tray and the sheet in dp: tray = (x, y, w, h), sheet = (cx, cy, w, h). */
internal class DarkroomLayout(val tray: FloatArray, val sheet: FloatArray)

private fun darkroomLayout(width: Float, height: Float, insetTop: Float, insetBottom: Float): DarkroomLayout {
    val top = insetTop + 118f
    val bottom = height - insetBottom - 16f - 128f - 18f
    val tw = width - 32f
    val th = max(260f, bottom - top)
    val sw = min(tw - 76f, (th - 90f) * 0.8f)
    return DarkroomLayout(floatArrayOf(16f, top, tw, th), floatArrayOf(width / 2f, top + th / 2f, sw, sw * 1.25f))
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

/** One frame of the scene: what the shader, the timer, the stamp and the pills show. */
internal class DarkroomFrame {
    var dev = LATENT
    /** The fresh sheet's development (it starts as soon as it lands). */
    var devB = 0f
    var slideA = 0f
    var slideB = -1000f
    var rock = 0f
    var wave = 0f
    var wphase = 0f
    var light = 0f
    var stopBath = 0f
    /** The stamp's landing (0 … 1) and its exit (0 … 1). */
    var stamp = 0f
    var stampOut = 0f
    var secs = 0f
    /** 0 Develop, 1 Stop, 2 Fix (back to Develop for the fresh sheet). */
    var stage = 0
}

private fun rockEnvelope(t: Float) = easeInOut(seg(t, 1.2f, 1.6f)) * (1f - easeInOut(seg(t, 4.6f, 5.0f)))

/** The loop at time t, for a tray `width` dp wide; `leave` is the development of the print that leaves. */
private fun loopFrame(t: Float, width: Float, leave: Float, f: DarkroomFrame) {
    val env = rockEnvelope(t)
    f.rock = 0.75f * env * sin(2f * PIf * (t - 1.2f) / ROCK_PERIOD) + 0.06f * sin(2f * PIf * 2f * t / LOOP)
    f.wphase = 2f * PIf * WAVES * t / LOOP
    val stir = sin(PIf * seg(t, 6.6f, 8.3f))
    f.wave = 0.12f + 0.68f * env + 0.35f * stir
    val d = seg(t, 0.6f, 4.9f)
    f.dev = if (t < 6.4f) LATENT + (1f - LATENT) * d.pow(1.25f) else leave
    f.devB = LATENT * easeOut(seg(t, 7.1f, 7.9f))
    f.secs = DEV_SECONDS * seg(t, 1.2f, 5.0f) * (1f - easeInOut(seg(t, 7.0f, 7.8f)))
    f.stopBath = easeOut(seg(t, 5.0f, 5.25f)) * (1f - easeInOut(seg(t, 5.45f, 5.8f)))
    f.stamp = seg(t, 5.3f, 5.55f)
    f.stampOut = easeInOut(seg(t, 6.4f, 6.65f))
    // The room light: a fluorescent flicker on, then off in 0.15 s.
    f.light = when {
        t >= 5.6f && t < 5.64f -> 0.75f
        t >= 5.64f && t < 5.67f -> 0.2f
        t >= 5.67f && t < 6.4f -> easeOut(seg(t, 5.67f, 5.75f))
        t >= 6.4f -> 1f - easeOut(seg(t, 6.4f, 6.55f))
        else -> 0f
    }
    val out = easeInOut(seg(t, 6.6f, 7.7f))
    val inn = easeOut(seg(t, 6.9f, 8.1f))
    f.slideA = out * width * 1.05f
    f.slideB = if (t < 6.6f) -width * 1.05f else -(1f - inn) * width * 1.05f
    f.stage = if (t < 5.0f) 0 else if (t < 5.45f) 1 else if (t < 6.6f) 2 else 0
}

/** The stamp's scale while it lands: 1.3 down to 0.94 (ease-out), back up to 1. */
private fun stampScale(s: Float) = if (s < 0.7f) 1.3f - 0.36f * easeOut(s / 0.7f) else 0.94f + 0.06f * easeOut((s - 0.7f) / 0.3f)

/** A finger's push past a full tip gives less and less (rubber band). */
private fun rubber(x: Float): Float {
    val a = abs(x)
    if (a <= 1f) return x
    val over = a - 1f
    return sign(x) * (1f + over / (1f + over * 2.2f))
}

private class DarkroomState {
    var layout = DarkroomLayout(FloatArray(4), FloatArray(4))
    var mode = MODE_AUTO
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var holding = false
    val f = DarkroomFrame()
    val g = DarkroomFrame()
    var leave = 1f
    var target = 0f
    var rock = 0f
    var rockV = 0f
    var dev = 0f
    var wave = 0f
    var wphase = 0f
    /** What the loop adds back after a touch, fading out: rock, wave, secs, and when. */
    var offRock = 0f
    var offWave = 0f
    var offSecs = 0f
    var offAt = -10f
    var stagePos = 0f
    var posed = false
    val frame = mutableIntStateOf(0)
    val awake = mutableStateOf(true)
}

/** The timer's shown seconds: whole seconds, each clicking in over 0.3 s. */
private fun shownSecs(f: DarkroomFrame): Float {
    val s = max(0f, f.secs)
    val whole = floor(s)
    return whole + easeOut((s - whole) / 0.3f)
}

/**
 * "Safelight", a print developing in a darkroom tray under a red safelight, drawn by one AGSL
 * shader over a bundled photo. Rocking sends a wave across the print and the image comes up
 * shadows first; then the stop bath, a FIXED stamp, the room light, and the next sheet.
 *
 * @param autoPlay plays the 9 s rock-develop-fix-lights-next-sheet loop.
 */
@Composable
fun DarkroomTrayDevelop(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val state = remember { DarkroomState() }
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(screen) / screen.density
    val insetBottom = insets.getBottom(screen) / screen.density

    // The photo, decoded once off the main thread; a pale stand-in until it arrives, so the tray and
    // a blank sheet show from the first frame.
    val photo by produceState<Bitmap?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("darkroomtraydevelop/darkroomtraydevelop_print.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    val stand = remember { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(236, 234, 228)) } }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(DARKROOM_TRAY_DEVELOP_AGSL) }.getOrNull() else null
    }
    val source = photo ?: stand
    val brush = remember(shader, source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            shader.setInputShader("photo", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            ShaderBrush(shader)
        } else {
            null
        }
    }
    val image = remember(photo) { photo?.asImageBitmap() }
    val imgW = source.width.toFloat()
    val imgH = source.height.toFloat()

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0E0606))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = darkroomLayout(width, height, insetTop, insetBottom)
        state.layout = l
        if (!state.posed || reduced) {
            darkroomFrame(state, 0f, auto, reduced) {}
            state.posed = true
        }

        LaunchedEffect(state, auto, reduced, state.awake.value) {
            if (reduced || !state.awake.value) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    darkroomFrame(state, dt, auto, false) {
                        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    state.frame.intValue += 1
                    !auto && state.mode == MODE_AUTO && state.now - state.offAt > 2f && abs(state.stagePos - state.f.stage) < 0.002f
                }
                if (stop) {
                    state.awake.value = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A developing tray with a print. Drag sideways to rock the tray and develop the print." }
                .pointerInput(state, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        state.holding = true
                        state.lastTouch = state.now
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = (change.position.x - down.position.x) / d
                            val dy = (change.position.y - down.position.y) / d
                            if (!started && hypot(dx, dy) >= 4f) {
                                started = true
                                darkroomTakeOver(state)
                                state.awake.value = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            if (started && state.mode == MODE_USER) {
                                state.target = rubber(dx / 110f)
                                state.lastTouch = state.now
                                change.consume()
                            }
                        }
                        state.holding = false
                        state.target = 0f
                        state.lastTouch = state.now
                    }
                },
        ) {
            state.frame.intValue
            val f = state.f
            val dd = this.density
            scale(dd, dd, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("imgSize", imgW, imgH)
                    shader.setFloatUniform("size", width, height)
                    shader.setFloatUniform("tray", l.tray[0], l.tray[1], l.tray[2], l.tray[3])
                    shader.setFloatUniform("sheet", l.sheet[0], l.sheet[1], l.sheet[2], l.sheet[3])
                    shader.setFloatUniform("devA", f.dev)
                    shader.setFloatUniform("devB", f.devB)
                    shader.setFloatUniform("slide", f.slideA, f.slideB)
                    shader.setFloatUniform("rock", f.rock, f.wave)
                    shader.setFloatUniform("wphase", f.wphase)
                    shader.setFloatUniform("light", f.light)
                    shader.setFloatUniform("stopBath", f.stopBath)
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    darkroomFallback(width, height, l, f, image)
                }
            }
        }

        val ink = ColorProducer { state.frame.intValue; lerp(InkRed, InkWhite, state.f.light) }
        val soft = ColorProducer { state.frame.intValue; lerp(SoftRed, SoftWhite, state.f.light) }

        // Header: the print's name on the left, the development timer on the right.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (insetTop + 18f).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("SAFELIGHT", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp), color = ink)
                BasicText(
                    "Print 3 of 12",
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
                    color = ink,
                    maxLines = 1,
                )
                BasicText("Fibre paper · 8 × 10 in · 20 °C", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(top = 2.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("DEV", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp), color = soft)
                Row(Modifier.height(DIGIT_H.dp), verticalAlignment = Alignment.CenterVertically) {
                    DarkroomWheel(state, 0, ink)
                    BasicText(
                        ":",
                        modifier = Modifier.padding(horizontal = 1.dp),
                        style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, lineHeight = DIGIT_H.sp, lineHeightStyle = CenteredLine),
                        color = ink,
                    )
                    DarkroomWheel(state, 1, ink)
                    DarkroomWheel(state, 2, ink)
                }
            }
        }

        // The FIXED stamp on the print's corner.
        val sheetLeft = l.sheet[0] - l.sheet[2] / 2f
        val sheetTop = l.sheet[1] - l.sheet[3] / 2f
        Box(
            Modifier
                .offset(x = (sheetLeft + 14f).dp, y = (sheetTop + 22f).dp)
                .graphicsLayer {
                    state.frame.intValue
                    val f = state.f
                    alpha = if (f.stamp > 0f) min(1f, f.stamp * 3f) * (1f - f.stampOut) else 0f
                    translationY = -8f * f.stampOut * screen.density
                    rotationZ = -9f
                    val k = stampScale(f.stamp)
                    scaleX = k
                    scaleY = k
                }
                .border(2.dp, Color(0xD9FAECDE), RoundedCornerShape(10.dp))
                .padding(3.dp),
        ) {
            Column(
                Modifier
                    .border(1.dp, Color(0xB3FAECDE), RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText("FIXED", style = TextStyle(color = Color(0xEBFAECDE), fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp))
                BasicText("24 OCT · 2:00", style = TextStyle(color = Color(0xCCFAECDE), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, fontFeatureSettings = "tnum"))
            }
        }

        // The stages panel.
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = (insetBottom + 16f).dp), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x73000000), 18.dp, 40.dp, 26.dp)
                    .clip(shape)
                    .background(Color(0xE0180E0E))
                    .drawBehind {
                        drawLine(Color(0x0FFFE6DC), Offset(26.dp.toPx(), 1.dp.toPx()), Offset(size.width - 26.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                    }
                    .border(1.dp, Color(0x12FFDCD2), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stages.forEachIndexed { i, s -> DarkroomPill(state, i, s, Modifier.weight(1f)) }
                }
                BasicText(
                    "Drag sideways to rock the tray",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    color = soft,
                )
            }
        }
    }
}

private val CenteredLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** A wheel of digits for the minutes (0), the tens of seconds (1) or the seconds (2). */
@Composable
private fun DarkroomWheel(state: DarkroomState, which: Int, ink: ColorProducer) {
    val count = if (which == 1) 6 else 10
    Box(Modifier.size(21.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    state.frame.intValue
                    val s = shownSecs(state.f)
                    val pos = when (which) {
                        2 -> s % 10f
                        1 -> floor((s % 60f) / 10f) + max(0f, (s % 10f) - 9f)
                        else -> floor(s / 60f) + max(0f, (s % 60f) - 59f)
                    }
                    translationY = -(pos % count) * DIGIT_H.dp.toPx()
                },
        ) {
            for (i in 0..count) {
                Box(Modifier.size(21.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        "${i % count}",
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        style = TextStyle(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Light,
                            fontFeatureSettings = "tnum",
                            textAlign = TextAlign.Center,
                            lineHeight = DIGIT_H.sp,
                            lineHeightStyle = CenteredLine,
                        ),
                        color = ink,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** One stage of the process; the current one lit and a little larger. */
@Composable
private fun DarkroomPill(state: DarkroomState, i: Int, stage: DarkroomStage, modifier: Modifier) {
    val on = { max(0f, 1f - abs(state.stagePos - i)) }
    val text = ColorProducer {
        state.frame.intValue
        val c = lerp(InkRed, InkWhite, state.f.light)
        c.copy(alpha = 0.55f + 0.45f * on())
    }
    Column(
        modifier
            .height(58.dp)
            .graphicsLayer {
                state.frame.intValue
                val k = 1f + 0.03f * on()
                scaleX = k
                scaleY = k
            }
            .drawBehind {
                state.frame.intValue
                val light = state.f.light
                val base = lerp(Color(0x14F27864), Color(0x14F3F0EA), light)
                val lit = lerp(Color(0x4DF27864), Color(0x38F3F0EA), light)
                drawRoundRect(lerp(base, lit, on()), cornerRadius = CornerRadius(18.dp.toPx()))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        BasicText(stage.name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = text, maxLines = 1)
        BasicText(stage.time, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), color = text, maxLines = 1)
    }
}

/** One frame: the loop, or the tray rocked by hand. */
private inline fun darkroomFrame(s: DarkroomState, dt: Float, auto: Boolean, reduced: Boolean, finished: () -> Unit) {
    val tw = s.layout.tray[2]
    val at = s.now + dt
    s.now = at
    val f = s.f
    if (reduced) {
        loopFrame(6.0f, tw, 1f, f)
        f.wave = 0f
        f.rock = 0f
    } else if (s.mode == MODE_AUTO) {
        val before = s.clock
        val t = if (auto) (before + dt) % LOOP else 0f
        if (t < before) s.leave = 1f
        s.clock = t
        loopFrame(t, tw, s.leave, f)
        val fade = exp(-(at - s.offAt) * 5f)
        f.rock += s.offRock * fade
        f.wave += s.offWave * fade
        f.secs += s.offSecs * exp(-(at - s.offAt) * 3f)
    } else {
        // Rocking by hand: a spring toward the finger's tilt, the wave from the speed.
        val k = 90f
        val c = 2f * 0.62f * sqrt(k)
        var v = s.rockV + (k * (s.target - s.rock) - c * s.rockV) * dt
        val r = s.rock + v * dt
        if (abs(v) < 1e-4f) v = 0f
        s.rockV = v
        s.rock = r
        val speed = min(1f, abs(v) / 3f)
        s.wave += (min(1f, 0.12f + speed) - s.wave) * min(1f, dt * 4f)
        s.wphase = (s.wphase + dt * (2f * PIf * WAVES / LOOP) * (1f + speed * 1.5f)) % (PIf * 200f)
        s.dev = min(1f, s.dev + dt * (0.012f + 0.42f * speed))
        loopFrame(1.2f, tw, 1f, f)
        f.rock = r
        f.wave = s.wave
        f.wphase = s.wphase
        f.dev = s.dev
        f.secs = s.dev * DEV_SECONDS
        val done = s.dev >= 1f
        if (done || (!s.holding && at - s.lastTouch > IDLE)) {
            // Hand back to the loop where it fits, carrying the tilt, the wave and the timer.
            val t0 = if (done) RESUME_FIXED else RESUME_EXCHANGE
            val g = s.g
            loopFrame(t0, tw, s.dev, g)
            s.offRock = r - g.rock
            s.offWave = s.wave - g.wave
            s.offSecs = f.secs - g.secs
            s.offAt = at
            s.leave = s.dev
            s.clock = t0
            s.mode = MODE_AUTO
            if (done) finished()
        }
    }
    s.stagePos = if (reduced || dt == 0f) f.stage.toFloat() else s.stagePos + (f.stage - s.stagePos) * min(1f, dt * 10f)
}

/** The finger takes the tray from the loop. During the exchange the fresh sheet takes the touch. */
private fun darkroomTakeOver(s: DarkroomState) {
    if (s.mode != MODE_AUTO) return
    val f = s.f
    s.rock = f.rock
    s.rockV = 0f
    s.wave = f.wave
    s.wphase = f.wphase
    s.dev = if (s.clock >= RESUME_EXCHANGE) max(LATENT, f.devB) else min(0.99f, f.dev)
    s.mode = MODE_USER
}
