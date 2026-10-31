package com.ngocdevv.nativecook.blocks.forms.fuzzymoodmonster

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds; the four breaths, shake and two hops return to the initial Meh pose. */
const val LOOP = 8.0
private const val BALL = 290f
private const val TRACK = 300f
private const val THUMB = 34f
private const val HOLD = 2.0
private const val RETURN = 0.8
private val Ink = Color(0xFF2B1B17)

/*
 * Faithful port of the React Native reference. One mood value crossfades the three bundled
 * furballs and drives the background, word, button and thumb.
 * 0–0.8: Meh; 0.8–2: Meh → Grumpy; 2–3: 7 dp shake at 6 Hz;
 * 3–4.6: Grumpy → Sunny; 4.7–5.2 and 5.3–5.8: 26 dp hops with 0.2 s landings;
 * 6–7.2: Sunny → Meh; 7.2–8: rest. Transitions use ease-in-out cubic.
 * Drag: hold the selected mood for 2 s after release, ease to Meh over 0.8 s, then restart.
 * Reduced motion freezes Meh and disables the gesture.
 */
private data class Mood(val word: String, val note: String, val top: Int, val bottom: Int, val deep: Int)
private val Moods = listOf(
    Mood("Grumpy", "Everything is a little loud today.", 0xFFE3DC, 0xF4A493, 0xB7311C),
    Mood("Meh", "Not bad, not great. Just here.", 0xFFF2DC, 0xFFC57E, 0xB76A00),
    Mood("Sunny", "Light, easy and ready to go.", 0xEDF8DD, 0xAEDD84, 0x2F7F1A),
)

private fun segment(t: Double, start: Double, end: Double) = ((t - start) / (end - start)).coerceIn(0.0, 1.0)
private fun cubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2

/** Round each sRGB channel just as the reference's mixColor does. */
private fun mixColor(a: Int, b: Int, u: Float): Color {
    fun channel(shift: Int) = (((a shr shift) and 255) * (1 - u) + ((b shr shift) and 255) * u).roundToInt()
    return Color(channel(16), channel(8), channel(0))
}

private fun moodColor(mood: Float, value: (Mood) -> Int): Color {
    val m = mood.coerceIn(0f, 2f)
    return if (m <= 1) mixColor(value(Moods[0]), value(Moods[1]), m)
    else mixColor(value(Moods[1]), value(Moods[2]), m - 1)
}

private data class MonsterFrame(
    val mood: Float = 1f, val breath: Float = 0f, val shake: Float = 0f,
    val hop: Float = 0f, val squash: Float = 0f, val finger: Float = 0f,
)

private fun stateAt(t: Double): MonsterFrame {
    val mood = when {
        t < 2.0 -> 1 - cubic(segment(t, 0.8, 2.0))
        t < 3.0 -> 0.0
        t < 4.6 -> 2 * cubic(segment(t, 3.0, 4.6))
        t < 6.0 -> 2.0
        else -> 2 - cubic(segment(t, 6.0, 7.2))
    }
    var hop = 0.0
    var squash = 0.0
    for (k in 0..1) {
        val u = segment(t, 4.7 + 0.6 * k, 5.2 + 0.6 * k)
        if (u > 0 && u < 1) hop = sin(PI * u) * 26
        val land = segment(t, 5.2 + 0.6 * k, 5.4 + 0.6 * k)
        if (land > 0 && land < 1) squash = sin(PI * land)
    }
    val finger = segment(t, 0.6, 0.8) * (1 - segment(t, 2.0, 2.2)) +
        segment(t, 2.8, 3.0) * (1 - segment(t, 4.6, 4.8)) +
        segment(t, 5.8, 6.0) * (1 - segment(t, 7.2, 7.4))
    return MonsterFrame(
        mood = mood.toFloat(),
        shake = (sin(PI * segment(t, 2.0, 3.0)) * 7 * sin(t * PI * 12)).toFloat(),
        hop = hop.toFloat(), squash = squash.toFloat(), finger = finger.toFloat(),
    )
}

private class MonsterClock {
    // Only draw/layer lambdas read this snapshot; no whole-screen frame recomposition.
    var frame by mutableStateOf(MonsterFrame())
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var userMood = 1f
    var lastStep = 1

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val t = (elapsed - origin) % LOOP
        val breath = (0.025 * sin(t / LOOP * PI * 8)).toFloat()
        if (touching || manual) {
            var m = userMood
            if (!touching) {
                val since = elapsed - released
                val k = cubic(((since - HOLD) / RETURN).coerceIn(0.0, 1.0)).toFloat()
                m += (1 - m) * k
                if (since > HOLD + RETURN) {
                    manual = false
                    origin = elapsed
                }
            }
            frame = MonsterFrame(
                mood = m, breath = breath,
                shake = ((1 - m / 0.5f).coerceIn(0f, 1f) * 6 * sin(elapsed * PI * 12)).toFloat(),
                hop = (((m - 1.6f) / 0.4f).coerceIn(0f, 1f) * abs(sin(elapsed * PI * 1.6)) * 18).toFloat(),
            )
        } else {
            frame = (if (autoPlay) stateAt(t) else MonsterFrame()).copy(breath = breath)
        }
    }

    fun follow(x: Float, trackLeft: Float): Boolean {
        userMood = ((x - trackLeft - THUMB / 2) / (TRACK - THUMB) * 2).coerceIn(0f, 2f)
        val step = userMood.roundToInt()
        return (step != lastStep).also { lastStep = step }
    }
}

@Composable
fun FuzzyMoodMonster(
    modifier: Modifier = Modifier,
    brand: String = "tuft",
    date: String = "Thu, 31 Oct",
    question: String = "How’s your vibe\ntoday?",
    action: String = "Log my vibe",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember { MonsterClock() }
    val art = remember(context) {
        listOf("grumpy", "meh", "sunny").map { mood ->
            context.assets.open("fuzzymoodmonster/fuzzymoodmonster_monster-$mood.png").use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }
    }
    val family = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "fuzzymoodmonster/fuzzymoodmonster_BricolageHeadline.ttf",
        )))
    }
    val headline = remember(family) {
        TextStyle(
            fontFamily = family,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    val system = remember {
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    BlockSystemBars(darkIcons = true)
    LaunchedEffect(clock, lifecycle, reduced, autoPlay) {
        if (reduced) {
            clock.frame = MonsterFrame()
            clock.touching = false
            clock.manual = false
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1_000_000_000.0
                previous = now
                clock.advance(dt, autoPlay)
            }
        }
    }

    val safe = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val trackLeft = (width - TRACK) / 2
        val ballTop = height * 0.45f - BALL / 2
        val wordTop = ballTop + BALL + 6
        val top = safe.calculateTopPadding().value
        val bottom = safe.calculateBottomPadding().value
        val glowPath = remember(width, height, density) {
            val d = density.density
            Path().apply {
                addRoundRect(RoundRect(
                    left = 0f, top = height * 0.35f * d, right = width * d, bottom = height * d,
                    topLeftCornerRadius = CornerRadius(200 * d),
                    topRightCornerRadius = CornerRadius(200 * d),
                ))
            }
        }
        Box(Modifier.fillMaxSize().drawBehind {
            val mood = clock.frame.mood
            drawRect(moodColor(mood) { it.top })
            val lower = moodColor(mood) { it.bottom }
            // Transparent→20% white inside the reference's 55% opacity group.
            val lowerWhite = Color(
                red = lower.red * 0.8f + 0.2f, green = lower.green * 0.8f + 0.2f,
                blue = lower.blue * 0.8f + 0.2f, alpha = 0.55f,
            )
            clipPath(glowPath) {
                drawRect(Brush.verticalGradient(
                    listOf(lower.copy(alpha = 0.55f), lowerWhite),
                    startY = size.height * 0.35f, endY = size.height,
                ))
            }
            drawRect(Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
                startY = 0f, endY = size.height * 0.5f,
            ))
        }.pointerInput(reduced, trackLeft) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                clock.touching = true
                clock.manual = true
                fun follow(x: Float) {
                    if (clock.follow(x / density.density, trackLeft)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                follow(down.position.x)
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) follow(change.position.x)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                } finally {
                    clock.touching = false
                    clock.released = clock.elapsed
                }
            }
        })

        Row(
            Modifier.offset(24.dp, (top + 8).dp).size((width - 48).dp, 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(brand, style = headline.copy(fontSize = 26.sp, letterSpacing = (-0.5).sp, color = Ink))
            BasicText(date, style = system.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.55f)))
        }
        BasicText(
            question, modifier = Modifier.offset(24.dp, (top + 64).dp).size((width - 48).dp, 84.dp),
            style = headline.copy(fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1).sp, color = Ink, textAlign = TextAlign.Center),
        )
        Box(Modifier.offset(((width - 180) / 2).dp, (ballTop + BALL - 24).dp)
            .size(180.dp, 18.dp).graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val b = clock.frame
                scaleX = 1 - b.hop / 80 + 0.1f * b.squash
                alpha = 0.1f - b.hop / 600
            }.cssBoxShadow(Ink.copy(alpha = 0.6f), 0.dp, 20.dp, 90.dp)
            .background(Ink, RoundedCornerShape(90.dp)))
        Box(Modifier.offset(((width - BALL) / 2).dp, ballTop.dp).size(BALL.dp)
            .semantics { contentDescription = "Mood furball" }) {
            art.forEachIndexed { k, bitmap ->
                Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val b = clock.frame
                        alpha = (1 - abs(b.mood - k)).coerceAtLeast(0f)
                        translationX = b.shake * density.density
                        translationY = (-b.hop + 0.05f * BALL * b.squash) * density.density
                        scaleX = 1 + b.breath + 0.08f * b.squash
                        scaleY = 1 - b.breath - 0.1f * b.squash
                    })
            }
        }
        Moods.forEachIndexed { k, mood ->
            Column(
                Modifier.offset(24.dp, wordTop.dp).size((width - 48).dp, 90.dp).graphicsLayer {
                    val m = clock.frame.mood
                    alpha = (1 - abs(m - k) * 1.6f).coerceAtLeast(0f)
                    translationY = (m - k) * -18 * density.density
                }, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(mood.word, style = headline.copy(
                    fontSize = 46.sp, lineHeight = 54.sp, letterSpacing = (-1.2).sp,
                    color = Color(0xFF000000.toInt() or mood.deep),
                ))
                BasicText(mood.note, Modifier.padding(top = 2.dp), style = system.copy(
                    fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Ink.copy(alpha = 0.6f),
                ))
            }
        }
        Box(Modifier.offset(trackLeft.dp, (wordTop + 104).dp).size(TRACK.dp, THUMB.dp)) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(14.dp).background(
                Brush.horizontalGradient(listOf(Color(0xFFE8472E), Color(0xFFFFA329), Color(0xFF7CC63A))),
                RoundedCornerShape(7.dp),
            ))
            Box(Modifier.size(THUMB.dp).graphicsLayer {
                translationX = clock.frame.mood.coerceIn(0f, 2f) / 2 * (TRACK - THUMB) * density.density
            }) {
                Box(Modifier.size(THUMB.dp).graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    val f = clock.frame.finger
                    alpha = f
                    scaleX = 0.8f + 0.2f * f
                    scaleY = scaleX
                }.drawBehind {
                    drawCircle(Ink.copy(alpha = 0.12f), radius = 26.dp.toPx())
                    drawCircle(Ink.copy(alpha = 0.35f), radius = 25.25.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                })
                Box(Modifier.size(THUMB.dp).cssBoxShadow(Ink.copy(alpha = 0.25f), 4.dp, 16.dp, 17.dp)
                    .background(Color.White, CircleShape).border(3.dp, Ink.copy(alpha = 0.08f), CircleShape))
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = (bottom + 14).dp)
                .fillMaxWidth().height(58.dp).semantics { role = Role.Button }
                .drawBehind { drawRoundRect(moodColor(clock.frame.mood) { it.deep }, cornerRadius = CornerRadius(29.dp.toPx())) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = headline.copy(fontSize = 19.sp, color = Color.White))
        }
    }
}
