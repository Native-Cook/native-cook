package com.ngocdevv.nativecook.blocks.cards.typeddestinationcard

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param photo Address of a portrait photo, about 3 : 4.
 * @param tint Flat colour shown until the photo arrives.
 */
data class DestinationCity(val name: String, val photo: String, val tint: Color)

private const val WIDTH = 320f
private const val HEIGHT = 418f

private const val PHOTO_WIDTH = 240f
private const val PHOTO_HEIGHT = 324f
private const val PHOTO_RADIUS = 14f
private const val PHOTO_LEFT = (WIDTH - PHOTO_WIDTH) / 2f

// The pill is wider than the photo and crosses its middle.
private const val PILL_WIDTH = 300f
private const val PILL_HEIGHT = 76f
private const val PILL_LEFT = (WIDTH - PILL_WIDTH) / 2f
private const val PILL_TOP = (PHOTO_HEIGHT - PILL_HEIGHT) / 2f
private const val PILL_SHADOW_OPACITY = 0.14f
private const val PILL_SHADOW_SIGMA = 18f
private const val PILL_SHADOW_Y = 10f

// The pill's canvas overflows it by this much on every side to fit its shadow.
private val PILL_BLEED = 64.dp
private const val PIN_WIDTH = 20f
private const val PIN_HEIGHT = 27f
private const val PIN_HOLE = 3.7f
private const val PIN_LEFT = 29f
private const val TEXT_LEFT = 68f
private const val CARET_WIDTH = 2f
private const val CARET_HEIGHT = 33f
private const val CARET_GAP = 2f

private const val BUTTON_TOP = 364f
private const val BUTTON_HEIGHT = 54f
private const val BUTTON_GAP = 9f
private const val TARGET_RING = 5.5f
private const val TARGET_DOT = 2.2f
private const val TARGET_STROKE = 1.8f

// Seconds before each key lands, repeated along the name: an uneven but fixed rhythm.
private val KEY_DELAYS = floatArrayOf(0.12f, 0.085f, 0.14f, 0.095f, 0.07f, 0.16f, 0.09f, 0.11f)
private const val DELETE_STEP = 0.045f

// The beat between the last deleted letter and the first typed one.
private const val TYPE_PAUSE = 0.2f

// Half a blink of the caret: this long shown, this long hidden.
private const val CARET_BLINK = 0.53f

// When a name is complete the next photo fades in while it settles from a slight zoom.
private const val FADE_TIME = 0.5f
private const val ZOOM_TIME = 0.7f
private const val ZOOM = 0.1f

// The pin's hop: a spring at rest that is kicked upwards, in dp per second.
private const val PIN_MASS = 1f
private const val PIN_STIFFNESS = 221f
private const val PIN_DAMPING = 10f
private const val PIN_KICK = 170f

// autoPlay: the first edit starts after FIRST_AT, then one city every CITY_TIME, so the three
// default cities come round every LOOP seconds.
private const val FIRST_AT = 0.8f
private const val LOOP = 7.8f
private const val CITY_TIME = LOOP / 3f

// After a tap the loop waits this long once the name is typed.
private const val USER_HOLD = 2f

private val SampleCities = listOf(
    DestinationCity(
        name = "Paris",
        photo = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=480&h=648&q=80",
        tint = Color(0xFF8C818D),
    ),
    DestinationCity(
        name = "Tokyo",
        photo = "https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?auto=format&fit=crop&w=480&h=648&q=80",
        tint = Color(0xFF4A848E),
    ),
    DestinationCity(
        name = "New York",
        photo = "https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?auto=format&fit=crop&w=480&h=648&q=80",
        tint = Color(0xFF6E6469),
    ),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val TypedStyle = TextStyle(
    fontSize = 27.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = (-0.4).sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ButtonStyle = TextStyle(
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.2).sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

// A map pin, 20 × 27, with its tip at the bottom.
private val Pin = Path().apply {
    moveTo(10f, 0f)
    cubicTo(4.48f, 0f, 0f, 4.48f, 0f, 10f)
    cubicTo(0f, 17.5f, 10f, 27f, 10f, 27f)
    cubicTo(10f, 27f, 20f, 17.5f, 20f, 10f)
    cubicTo(20f, 4.48f, 15.52f, 0f, 10f, 0f)
    close()
}

// The text in the pill is kept as one number: the city times this, plus the letters shown.
private const val CODE = 1000

/** The text in the pill [elapsed] seconds into an edit: which city's name, and how many of its letters. */
private fun typedText(elapsed: Float, fromCity: Int, fromLength: Int, to: Int, toLength: Int): Int {
    val deleteTime = fromLength * DELETE_STEP
    if (elapsed < deleteTime) return fromCity * CODE + fromLength - 1 - (elapsed / DELETE_STEP).toInt()
    val typing = elapsed - deleteTime - TYPE_PAUSE
    var count = 0
    var at = 0f
    while (count < toLength) {
        at += KEY_DELAYS[count % KEY_DELAYS.size]
        if (at > typing) break
        count += 1
    }
    return to * CODE + count
}

/** Seconds an edit takes: deleting what was there, a pause, and one key after another. */
private fun completeTime(fromLength: Int, toLength: Int): Float {
    var time = fromLength * DELETE_STEP + TYPE_PAUSE
    for (key in 0 until toLength) time += KEY_DELAYS[key % KEY_DELAYS.size]
    return time
}

private fun smooth(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

private fun easeOut(t: Float): Float {
    val p = 1f - t.coerceIn(0f, 1f)
    return 1f - p * p * p
}

/** Height of the pin [since] seconds after a name is completed: a spring kicked upwards. */
private fun pinHop(since: Float): Float {
    if (since <= 0f) return 0f
    val decay = PIN_DAMPING / (2f * PIN_MASS)
    val omega = sqrt(PIN_STIFFNESS / PIN_MASS - decay * decay)
    return -PIN_KICK / omega * exp(-decay * since) * sin(omega * since)
}

/** What is being typed: the text left from [fromCity] is deleted, then [to] is typed. */
private class TypingSim(private val lengths: List<Int>) {
    /** When the loop moves on to the next city. */
    var nextAt = FIRST_AT
    var startedAt = 0f
    var fromCity = 0
    var fromLength = 0
    val to = mutableIntStateOf(0)

    /** The photo under the one fading in. */
    val base = mutableIntStateOf(0)

    /** The text in the pill, as [typedText] packs it. */
    val shown = mutableIntStateOf(0)

    // Written last in a frame: reading it is what redraws the card.
    val time = mutableFloatStateOf(0f)

    init {
        rest()
    }

    private fun toLength() = lengths.getOrElse(to.intValue) { 0 }

    /** Seconds since the name was completed; negative while it is still being edited. */
    fun since() = time.floatValue - startedAt - completeTime(fromLength, toLength())

    /** Shows the first city fully typed, with the caret at the start of a blink. */
    fun rest() {
        fromCity = 0
        fromLength = 0
        to.intValue = 0
        base.intValue = 0
        startedAt = time.floatValue - completeTime(0, toLength()) - CARET_BLINK * 8f
        nextAt = time.floatValue + FIRST_AT
        shown.intValue = toLength()
    }

    /** Starts deleting whatever is shown at [now] and typing the next city. */
    fun begin(now: Float, reduced: Boolean) {
        if (lengths.size < 2) return
        val text = typedText(now - startedAt, fromCity, fromLength, to.intValue, toLength())
        if (now - startedAt >= completeTime(fromLength, toLength())) base.intValue = to.intValue
        fromCity = text / CODE
        fromLength = text % CODE
        to.intValue = (to.intValue + 1) % lengths.size
        // Under reduced motion the name is swapped at once and only the photo fades.
        startedAt = if (reduced) now - completeTime(fromLength, toLength()) else now
    }

    /** A tap on the pill: on to the next city now; the loop waits before it carries on. */
    fun skip(reduced: Boolean) {
        begin(time.floatValue, reduced)
        nextAt = time.floatValue + completeTime(fromLength, toLength()) + USER_HOLD
    }

    fun advance(dt: Float, looping: Boolean, reduced: Boolean) {
        val now = time.floatValue + dt
        if (looping && now >= nextAt) {
            begin(nextAt, reduced)
            nextAt += CITY_TIME
        }
        shown.intValue = typedText(now - startedAt, fromCity, fromLength, to.intValue, toLength())
        time.floatValue = now
    }
}

/**
 * A destination picker for an onboarding screen: a portrait photo crossed by a white search pill
 * in which a city is typed and deleted letter by letter. When a name is complete the photo
 * cross-fades to that city with a slight zoom and the map pin hops. A black pill button for the
 * current location sits underneath. Tapping the search pill moves on to the next city.
 *
 * @param cities The cities, typed one after another; the first is shown at rest.
 * @param autoPlay Types its way through the cities by itself, one every 2.6 s.
 * @param onSelect Called with the index of the city a tap on the pill moves to.
 */
@Composable
fun TypedDestinationCard(
    cities: List<DestinationCity> = SampleCities,
    autoPlay: Boolean = false,
    buttonLabel: String = "Current location",
    pillColor: Color = Color.White,
    inkColor: Color = Color(0xFF141414),
    buttonColor: Color = Color(0xFF171717),
    buttonTextColor: Color = Color.White,
    onSelect: ((Int) -> Unit)? = null,
    onCurrentLocation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val sim = remember(cities) { TypingSim(cities.map { it.name.length }) }
    val pillShadow = remember { SoftRoundRect(rgba(0, 0, 0, PILL_SHADOW_OPACITY), sigma = PILL_SHADOW_SIGMA) }

    LaunchedEffect(sim, auto) { sim.rest() }

    LaunchedEffect(sim, reducedMotion) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            sim.advance(dt, looping, reducedMotion)
        }
    }

    val shown = sim.shown.intValue
    val text = cities.getOrNull(shown / CODE)?.name?.take(shown % CODE) ?: ""

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        Box(
            Modifier
                .offset(PHOTO_LEFT.dp, 0.dp)
                .size(PHOTO_WIDTH.dp, PHOTO_HEIGHT.dp)
                .clip(RoundedCornerShape(PHOTO_RADIUS.dp))
                .clearAndSetSemantics {},
        ) {
            cities.forEachIndexed { index, city ->
                val incoming = index == sim.to.intValue
                val under = index == sim.base.intValue
                // One city's photo: it fades in over the one before when its name is complete.
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(city.photo).crossfade(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (incoming) 2f else if (under) 1f else 0f)
                        .graphicsLayer {
                            val since = sim.since()
                            alpha = if (incoming) smooth(since / FADE_TIME) else if (under) 1f else 0f
                            val zoom = if (incoming && !reducedMotion) 1f + ZOOM * (1f - easeOut(since / ZOOM_TIME)) else 1f
                            scaleX = zoom
                            scaleY = zoom
                        }
                        .background(city.tint),
                )
            }
        }

        PressablePill(
            pressedScale = 0.98f,
            label = cities.getOrNull(shown / CODE)?.name ?: "",
            onClick = {
                if (cities.size > 1) {
                    sim.skip(reducedMotion)
                    onSelect?.invoke(sim.to.intValue)
                }
            },
            modifier = Modifier.offset(PILL_LEFT.dp, PILL_TOP.dp).size(PILL_WIDTH.dp, PILL_HEIGHT.dp),
        ) {
            Box(Modifier.matchParentSize().bleed(PILL_BLEED)) {
                Canvas(Modifier.fillMaxSize()) {
                    surface(PILL_BLEED) { x, y, w, h ->
                        drawIntoCanvas { pillShadow.draw(it.nativeCanvas, x, y + PILL_SHADOW_Y, w, h, h / 2f) }
                    }
                }
            }
            Box(Modifier.matchParentSize().background(pillColor, CircleShape))
            Canvas(
                Modifier
                    .offset(PIN_LEFT.dp, ((PILL_HEIGHT - PIN_HEIGHT) / 2f).dp)
                    .size(PIN_WIDTH.dp, PIN_HEIGHT.dp)
                    .graphicsLayer { translationY = (if (reducedMotion) 0f else pinHop(sim.since())).dp.toPx() },
            ) {
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(Pin, inkColor)
                    drawCircle(pillColor, PIN_HOLE, Offset(PIN_WIDTH / 2f, PIN_WIDTH / 2f))
                }
            }
            Row(
                Modifier.offset(TEXT_LEFT.dp, 0.dp).size((PILL_WIDTH - TEXT_LEFT).dp, PILL_HEIGHT.dp),
                horizontalArrangement = Arrangement.spacedBy(CARET_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(text, style = TypedStyle.copy(color = inkColor), maxLines = 1)
                Box(
                    Modifier
                        .size(CARET_WIDTH.dp, CARET_HEIGHT.dp)
                        .graphicsLayer {
                            val since = sim.since()
                            alpha = if (reducedMotion || since < 0f || since % (CARET_BLINK * 2f) < CARET_BLINK) 1f else 0f
                        }
                        .background(inkColor, CircleShape),
                )
            }
        }

        PressablePill(
            pressedScale = 0.97f,
            label = buttonLabel,
            onClick = { onCurrentLocation?.invoke() },
            modifier = Modifier.offset(0.dp, BUTTON_TOP.dp).size(WIDTH.dp, BUTTON_HEIGHT.dp),
        ) {
            Row(
                Modifier.matchParentSize().background(buttonColor, CircleShape),
                horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A "locate me" target in a 20 × 20 box: a ring, a dot and four ticks.
                Canvas(Modifier.size(20.dp)) {
                    scale(density, density, pivot = Offset.Zero) {
                        val stroke = Stroke(TARGET_STROKE, cap = StrokeCap.Round)
                        drawCircle(buttonTextColor, TARGET_RING, Offset(10f, 10f), style = stroke)
                        drawCircle(buttonTextColor, TARGET_DOT, Offset(10f, 10f))
                        drawLine(buttonTextColor, Offset(10f, 1.2f), Offset(10f, 4.5f), TARGET_STROKE, StrokeCap.Round)
                        drawLine(buttonTextColor, Offset(10f, 15.5f), Offset(10f, 18.8f), TARGET_STROKE, StrokeCap.Round)
                        drawLine(buttonTextColor, Offset(1.2f, 10f), Offset(4.5f, 10f), TARGET_STROKE, StrokeCap.Round)
                        drawLine(buttonTextColor, Offset(15.5f, 10f), Offset(18.8f, 10f), TARGET_STROKE, StrokeCap.Round)
                    }
                }
                BasicText(buttonLabel, style = ButtonStyle.copy(color = buttonTextColor), maxLines = 1)
            }
        }
    }
}

/** A tappable pill: it dips while pressed, with a light haptic. */
@Composable
private fun PressablePill(
    pressedScale: Float,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        content = content,
    )
}
