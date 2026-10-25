package com.ngocdevv.nativecook.blocks.animations.soapbubblepop

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Soap Bubble Pop — "Bubble Bath", the reward screen of a kids' word-learning app.

 A pastel tiled bathroom wall, three soap bubbles hovering with swirling thin-film colour, and a
 coral bubble wand holding a film of soap above the "Up next" card: one full-screen AGSL shader
 (SoapBubblePopShader.kt; a gradient stand-in below Android 13). Tap a bubble and it pops: the film
 tears open from the finger, droplets fly off and fall, a "+10" star floats up and the score rolls.

 autoPlay: LOOP = 8 s (SoapBubblePopMotion.kt). Touch interrupts it; popped bubbles are blown again
 from the wand one at a time; 2 s after the last touch the bubbles glide home (0.9 s) and the loop
 restarts from t = 0. Reduced motion: the bubbles rest, still; nothing plays or pops.

 Per frame: the simulation steps once and bumps `tick`, read only in draw and graphicsLayer lambdas.
*/

internal val BubbleBackground = Color(0xFF86A9DC)
private val Navy = Color(0xFF1B2F55)
private val Muted = Color(0xFF3E5276)
private val Coral = Color(0xFFF0644A)
private const val DIGIT_H = 22f
private val TENS = listOf("", "1", "2", "3", "4", "5", "6", "7", "8", "9")
private const val STAR_PATH = "M12 2.6 L14.8 8.6 L21.3 9.3 L16.4 13.7 L17.8 20.2 L12 16.9 L6.2 20.2 L7.6 13.7 L2.7 9.3 L9.2 8.6 Z"
private const val CLOSE_PATH = "M3.5 3.5 L12.5 12.5 M12.5 3.5 L3.5 12.5"
private const val SHELL_PATH = "M14 4 C7 4 3.5 10 4 16 L14 24 L24 16 C24.5 10 21 4 14 4 Z"
private const val SHELL_RIBS = "M14 6 V22 M9 7.5 L11.5 21 M19 7.5 L16.5 21 M5.5 12 L10 20 M22.5 12 L18 20"

/** Whether the frame loop is running, so a touch knows to start it again. */
private class BubbleLoop {
    var running = false
}

/**
 * The reward screen of a kids' word-learning app: soap bubbles over a tiled wall that pop under a
 * finger for stars.
 *
 * @param autoPlay plays the 8 s hover-pop-blow loop until touched.
 */
@Composable
fun SoapBubblePop(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(SOAP_BUBBLE_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val sim = remember(auto) { BubbleSim() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val loop = remember { BubbleLoop() }

    BoxWithConstraints(modifier.fillMaxSize().background(BubbleBackground).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { makeBubbleGeo(w, h, safeTop, safeBottom) }

        LaunchedEffect(sim, geo, auto, reduced, wake.intValue) {
            sim.step(0f, geo, auto, reduced)
            tick.intValue += 1
            if (reduced) return@LaunchedEffect
            loop.running = true
            try {
                var last = -1L
                while (true) {
                    val idle = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                        last = now
                        sim.step(dt, geo, auto, false)
                        tick.intValue += 1
                        sim.idle(auto)
                    }
                    if (idle) break
                }
            } finally {
                loop.running = false
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Soap bubbles. Tap a bubble to pop it." }
                .pointerInput(sim, geo, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        sim.touch()
                        if (!loop.running) wake.intValue += 1
                        // A tap is a touch lifted within 400 ms without sliding away.
                        val up = withTimeoutOrNull(400) { waitForUpOrCancellation() }
                        if (up != null) {
                            val d = density
                            if (sim.tap(up.position.x / d, up.position.y / d) >= 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }
                },
        ) {
            tick.intValue
            val d = density
            val f = sim.frame
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    val p = f.poses
                    shader.setFloatUniform("size", max(1f, geo.w), max(1f, geo.h))
                    for (i in 0 until 3) {
                        shader.setFloatUniform("b$i", p[i][0], p[i][1], p[i][2], p[i][3])
                        shader.setFloatUniform("k$i", p[i][4], p[i][5], p[i][6], p[i][7])
                    }
                    shader.setFloatUniform("wand", geo.wx, geo.wy, geo.wr, f.wandFilm)
                    shader.setFloatUniform("tap", f.tap[0], f.tap[1], f.tap[2], 0f)
                    shader.setFloatUniform("film", f.film - floor(f.film))
                    drawRect(brush, Offset.Zero, Size(geo.w, geo.h))
                } else {
                    drawBubbleFallback(geo, f)
                }
            }
        }

        for (i in 0 until 3) BubbleFloatingStar(i, sim, tick)
        BubbleHeader(safeTop, sim, tick)
        BubbleCard(geo)
    }
}

@Composable
private fun BubbleHeader(safeTop: Float, sim: BubbleSim, tick: MutableIntState) {
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = (safeTop + 8f).dp)
            .fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .cssBoxShadow(rgba(27, 47, 85, 0.18f), 1.dp, 2.dp, 20.dp)
                    .clip(CircleShape)
                    .background(rgba(255, 255, 255, 0.55f))
                    .bubbleInsetTop(rgba(255, 255, 255, 0.8f), 20.dp)
                    .clickable(interactionSource = null, indication = null) {}
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) {
                BubblePathIcon(CLOSE_PATH, 16f, Navy, 2f)
            }
            BasicText(
                "Lesson 4 of 12",
                style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
            Row(
                Modifier
                    .height(40.dp)
                    .cssBoxShadow(rgba(27, 47, 85, 0.18f), 1.dp, 2.dp, 20.dp)
                    .cssBoxShadow(rgba(27, 47, 85, 0.12f), 8.dp, 18.dp, 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(rgba(255, 255, 255, 0.72f))
                    .bubbleInsetTop(rgba(255, 255, 255, 0.9f), 20.dp)
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = "Round stars" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BubbleStarIcon(18f)
                Row(Modifier.width(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    // The tens of the round score: a column of digits that rolls and rests on a whole digit.
                    Box(
                        Modifier
                            .height(DIGIT_H.dp)
                            .clipToBounds(),
                    ) {
                        Column(
                            Modifier
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .graphicsLayer {
                                    tick.intValue
                                    translationY = -sim.frame.score * DIGIT_H.dp.toPx()
                                },
                        ) {
                            TENS.forEach { BubbleDigit(it) }
                        }
                    }
                    BubbleDigit("0")
                }
            }
        }
        BasicText(
            "Lesson 4 done!",
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(color = Navy, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            maxLines = 1,
        )
        BasicText(
            "Ocean Words · pop the bubbles for stars",
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun BubbleDigit(text: String) {
    Box(Modifier.height(DIGIT_H.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = TextStyle(
                color = Navy,
                fontSize = 18.sp,
                lineHeight = DIGIT_H.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

/** "+10" with a star, floating up from where a bubble popped. */
@Composable
private fun BubbleFloatingStar(index: Int, sim: BubbleSim, tick: MutableIntState) {
    val screen = LocalDensity.current
    val shadow = remember(screen) {
        Shadow(rgba(27, 47, 85, 0.45f), Offset(0f, with(screen) { 1.5.dp.toPx() }), with(screen) { 3.dp.toPx() })
    }
    Row(
        Modifier
            .size(88.dp, 40.dp)
            .graphicsLayer {
                tick.intValue
                val f = sim.frame
                val age = f.stars[index]
                if (age < 0) {
                    alpha = 0f
                    translationX = -200.dp.toPx()
                    translationY = -200.dp.toPx()
                    scaleX = 0.9f
                    scaleY = 0.9f
                } else {
                    val u = min(1f, age / BUBBLE_STAR_FOR)
                    val rise = 1f - (1f - u).pow(3)
                    val pop = min(1f, age / 0.22f)
                    // A playful overshoot on the way in: 0.9 → 1.06 → 1.
                    val s = if (age < 0.22f) 0.9f + 0.16f * sin(pop * PI.toFloat() * 0.5f) else 1.06f - 0.06f * min(1f, (age - 0.22f) / 0.2f)
                    alpha = min(1f, age / 0.12f) * (1f - max(0f, (age - 0.7f) / 0.4f))
                    translationX = (f.starX[index] - 44f).dp.toPx()
                    translationY = (f.starY[index] - 20f - 76f * rise).dp.toPx()
                    scaleX = s
                    scaleY = s
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        BubbleStarIcon(26f)
        BasicText(
            "+10",
            style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, shadow = shadow),
        )
    }
}

@Composable
private fun BubbleCard(geo: BubbleGeo) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = geo.cardTop.dp)
            .fillMaxWidth()
            .height(BUBBLE_CARD_H.dp)
            // Touches on the card do not reach the bubbles, as in React Native.
            .pointerInput(Unit) {}
            .cssBoxShadow(rgba(27, 30, 70, 0.18f), 2.dp, 4.dp, 28.dp)
            .cssBoxShadow(rgba(27, 30, 70, 0.2f), 22.dp, 44.dp, 28.dp)
            .clip(shape)
            .background(Color(0xFFFBF8FF))
            .bubbleInsetTop(Color.White, 28.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFEDE5))
                    .drawWithCache {
                        val shell = PathParser().parsePathString(SHELL_PATH).toPath()
                        val ribs = PathParser().parsePathString(SHELL_RIBS).toPath()
                        val outline = Stroke(1.6f, join = StrokeJoin.Round)
                        val rib = Stroke(1.2f, cap = StrokeCap.Round)
                        onDrawBehind {
                            val o = (size.width / density - 28f) / 2f
                            scale(density, density, pivot = Offset.Zero) {
                                translate(o, o) {
                                    drawPath(shell, Color(0xFFFFD9C9))
                                    drawPath(shell, Coral, style = outline)
                                    drawPath(ribs, Coral, style = rib)
                                }
                            }
                        }
                    },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    "UP NEXT · 6 MIN",
                    style = TextStyle(color = Color(0xFF8A7FA6), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                )
                BasicText(
                    "Lesson 5 · Shells & Shores",
                    style = TextStyle(color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .cssBoxShadow(rgba(160, 50, 30, 0.35f), 1.dp, 2.dp, 24.dp)
                .cssBoxShadow(rgba(240, 100, 74, 0.35f), 8.dp, 18.dp, 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Coral)
                .bubbleInsetTop(rgba(255, 255, 255, 0.3f), 24.dp)
                .clickable(interactionSource = null, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Continue", style = TextStyle(color = Color(0xFFFFF8F4), fontSize = 17.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** The gold star of the score pill and the floating "+10", in a 24-unit view box. */
@Composable
private fun BubbleStarIcon(sizeDp: Float) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .drawWithCache {
                val path = PathParser().parsePathString(STAR_PATH).toPath()
                // SVG's default gradient spans the path's bounding box, top to bottom.
                val fill = Brush.verticalGradient(
                    0f to Color(0xFFFFE28A),
                    0.55f to Color(0xFFFFC23D),
                    1f to Color(0xFFF29A1F),
                    startY = 2.6f,
                    endY = 20.2f,
                )
                val stroke = Stroke(1f, join = StrokeJoin.Round)
                onDrawBehind {
                    val k = size.width / 24f
                    scale(k, k, pivot = Offset.Zero) {
                        drawPath(path, fill)
                        drawPath(path, Color(0xFFD9821A), style = stroke)
                        drawCircle(rgba(255, 255, 255, 0.7f), radius = 1.4f, center = Offset(9.6f, 9.8f))
                    }
                }
            },
    )
}

@Composable
private fun BubblePathIcon(path: String, box: Float, color: Color, width: Float) {
    Box(
        Modifier
            .size(box.dp)
            .drawWithCache {
                val p = PathParser().parsePathString(path).toPath()
                val stroke = Stroke(width, cap = StrokeCap.Round)
                onDrawBehind { scale(density, density, pivot = Offset.Zero) { drawPath(p, color, style = stroke) } }
            },
    )
}

/** CSS `inset 0 1px 0 color`: a one-dp crescent of light along the top inside edge. */
private fun Modifier.bubbleInsetTop(color: Color, radius: Dp) = drawWithCache {
    val r = radius.toPx()
    val dy = 1.dp.toPx()
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(r))) }
    onDrawBehind {
        clipPath(outer) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
    }
}
