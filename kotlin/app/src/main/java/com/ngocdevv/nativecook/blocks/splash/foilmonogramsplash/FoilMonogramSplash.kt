package com.ngocdevv.nativecook.blocks.splash.foilmonogramsplash

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val Cream = Color(0xFFF4EDE3)
private val Ink = Color(0xFF1F1812)
private val Muted = Color(0xFF8A7A68)
private val CardTone = Color(0xFFE7DCCD)
private val Bone = Color(0xFFFBF6EE)
private val SplashColor = Color(0xFF130D0A)

private fun photo(id: String, w: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&q=80"
private val Hero = photo("photo-1567042661848-7161ce446f85", 1000)
private class ShelfItem(val name: String, val price: String, val url: String)
private val Shelf = listOf(
    ShelfItem("Seeded rye", "$8", photo("photo-1509440159596-0249088772ff", 560)),
    ShelfItem("Twin baguettes", "$6", photo("photo-1622808516114-02a5749cd965", 560)),
)

/**
 * The script's clock, stepped once a frame: a tap on the splash speeds it up, a finger on the
 * splash holds it, and without autoPlay it rests on the app.
 */
private class FoilClock {
    var t = 0f
    var speed = 1f
    var pause = 0f
    var looping = false
    var down = false
    var lightAt = 0.5f
    var elapsed = 0f
    // The finger's hold on the light, eased in over 0.3 s and out over 0.7 s.
    private var followFrom = 0f
    private var followTo = 0f
    private var followStart = 0f
    private var followTime = 0f

    fun advance(dt: Float) {
        elapsed += dt
        if (dt <= 0f || down) return
        if (pause > 0f) {
            pause = max(0f, pause - dt)
            return
        }
        val before = t
        // Without autoPlay the app stays once it is open; a replay jumps to the close.
        if (!looping && before >= APP_REST && before < CLOSE_AT) return
        var next = before + dt * speed
        if (before < HANDOFF_AT && next >= HANDOFF_AT) speed = 1f
        if (!looping && before < APP_REST && next > APP_REST) next = APP_REST
        if (next >= LOOP) next -= LOOP
        t = if (next.isFinite()) next else 0f
    }

    fun follow(): Float {
        if (followTime <= 0f) return followTo
        val u = ((elapsed - followStart) / followTime).coerceIn(0f, 1f)
        val eased = if (followTo > followFrom) FastOutSlowIn.transform(u) else InOut.transform(u)
        return followFrom + (followTo - followFrom) * eased
    }

    private fun followTo(target: Float, time: Float) {
        followFrom = follow()
        followTo = target
        followStart = elapsed
        followTime = time
    }

    fun shineX(centre: Float): Float {
        val f = follow()
        val at = sweep(t) * (1f - f) + lightAt.coerceIn(-0.25f, 1.25f) * f
        return centre + (at * 2f - 1f) * SWEEP_REACH
    }

    fun tap(reduced: Boolean): Boolean {
        if (t >= HANDOFF_AT) return false
        if (reduced) t = HANDOFF_AT else speed = SKIP_SPEED
        pause = 0f
        return true
    }

    fun dragStart(): Boolean {
        if (t >= HANDOFF_AT) return false
        down = true
        followTo(1f, 0.3f)
        return true
    }

    fun dragEnd() {
        if (!down) return
        down = false
        followTo(0f, 0.7f)
        pause = if (looping) RESUME_AFTER else 0f
    }

    fun replay() {
        t = CLOSE_AT
        speed = 1f
        pause = 0f
    }

    /** A touch on the open app keeps it open a little longer under autoPlay. */
    fun holdApp() {
        if (looping && t >= ARCH_AT + ARCH_TIME * 0.7f && t < CLOSE_AT) pause = max(pause, 3f)
    }

    private companion object {
        val FastOutSlowIn = CubicBezierEasing(0f, 0f, 0.58f, 1f)
        val InOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
    }
}

/**
 * A splash for an invented bakery, Loaven. On espresso, a knot of two gold-foil loops draws on
 * stroke by stroke, woven over and under; a light sweeps across the foil while the wordmark rises
 * through its baseline letter by letter. Then the letters drop away, the knot flies up into the
 * app's header and an arched doorway opens from the middle onto the app.
 */
@Composable
fun FoilMonogramSplash(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onReserve: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val clock = remember { FoilClock() }
    val art = remember { FoilArt() }
    // The script's time, read only where it is drawn, so a frame redraws without recomposing.
    val time = remember { mutableFloatStateOf(0f) }
    // The finger's light, so the canvas redraws while it moves even when the script is held.
    val light = remember { mutableFloatStateOf(0f) }
    val open by remember { derivedStateOf { time.floatValue >= ARCH_AT + ARCH_TIME * 0.7f && time.floatValue < CLOSE_AT } }
    val reserve by rememberUpdatedState(onReserve)

    LaunchedEffect(clock, auto) {
        clock.looping = auto
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min(0.05f, (now - last) / 1e9f)
                last = now
                clock.advance(dt)
                time.floatValue = clock.t
                light.floatValue = clock.lightAt + clock.follow() * 100f
            }
        }
    }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(SplashColor)
            .pointerInput(clock, reduced) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val startedAt = down.uptimeMillis
                    var dragging = false
                    var lastX = start.x
                    var liftedAt = -1L
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            liftedAt = change.uptimeMillis
                            break
                        }
                        lastX = change.position.x
                        if (!dragging && hypot(change.position.x - start.x, change.position.y - start.y) > 6.dp.toPx()) {
                            dragging = clock.dragStart()
                            if (!dragging) break
                        }
                        if (dragging) {
                            clock.lightAt = 0.5f + (lastX / density.density - size.width / density.density / 2f) / (2f * SWEEP_REACH)
                        }
                    }
                    if (dragging) {
                        clock.dragEnd()
                    } else if (liftedAt >= 0 && liftedAt - startedAt <= 400) {
                        // A tap: on the splash it plays the rest faster; on the app it keeps it open.
                        if (clock.tap(reduced)) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        } else {
                            clock.holdApp()
                        }
                    }
                }
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val layout = remember(w, h, safeTop) { FoilLayout(w, h, safeTop) }

        FoilApp(
            time = time,
            reduced = reduced,
            layout = layout,
            safeBottom = safeBottom,
            onReplay = {
                clock.replay()
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onReserve = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                reserve?.invoke()
            },
        )
        // While the splash is up, the app underneath takes no touches.
        if (!open) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } })
        }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            light.floatValue
            drawIntoCanvas { art.draw(it.nativeCanvas, layout, density.density, t, reduced, clock.shineX(layout.cx)) }
        }

        BasicText(
            "HOLLIS STREET BAKEHOUSE",
            style = TextStyle(
                color = Color(216, 183, 126).copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (layout.baseline + 22f).dp)
                .graphicsLayer {
                    val t = time.floatValue
                    val enter = tagEnter(t)
                    alpha = enter * (1f - step(t, HANDOFF_AT, 0.25f))
                    translationY = if (reduced) 0f else (1f - enter) * 6f * density.density
                },
        )
    }
}

/** An app element that rises into place as the doorway opens. */
private fun Modifier.rise(time: androidx.compose.runtime.FloatState, k: Int, reduced: Boolean) = graphicsLayer {
    val p = appIn(time.floatValue, k)
    alpha = p
    translationY = if (reduced) 0f else (1f - p) * APP_RISE * density
}

/** The app behind the splash: today's bake at a bakery, with a header the knot lands in. */
@Composable
private fun FoilApp(
    time: androidx.compose.runtime.FloatState,
    reduced: Boolean,
    layout: FoilLayout,
    safeBottom: Float,
    onReplay: () -> Unit,
    onReserve: () -> Unit,
) {
    val markWidth = KNOT_WIDTH * HEADER_MARK / KNOT_HEIGHT
    val heroHeight = min(310f, max(210f, layout.height * 0.34f))
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Cream)) {
        // The header: the knot lands at its left; the name and the bag rise in beside it.
        Box(
            Modifier
                .offset(x = (HEADER_LEFT + markWidth + 10f).dp, y = (layout.top + 10f).dp)
                .height(HEADER_MARK.dp)
                .rise(time, 1, reduced),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                "LOAVEN",
                style = TextStyle(color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 4.5.sp),
            )
        }
        Box(
            Modifier
                .offset(x = (HEADER_LEFT - 4f).dp, y = (layout.top + 10f).dp)
                .size((markWidth + 8f).dp, HEADER_MARK.dp)
                .semantics { contentDescription = "Replay the splash" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onReplay),
        )
        Box(
            Modifier
                .offset(x = (layout.width - 20f - 40f).dp, y = (layout.top + 8f).dp)
                .size(40.dp)
                .rise(time, 1, reduced)
                .border(0.5.dp, Ink.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = "Bag", tint = Ink, modifier = Modifier.size(20.dp))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 4.dp)
                    .defaultMinSize(16.dp, 16.dp)
                    .background(Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("2", style = TextStyle(color = Cream, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = (layout.top + 70f).dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.rise(time, 2, reduced), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    "TUESDAY MORNING",
                    style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
                )
                BasicText(
                    "The first loaves\nare out.",
                    style = TextStyle(
                        color = Ink,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 37.sp,
                        letterSpacing = (-0.4).sp,
                    ),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight.dp)
                    .rise(time, 3, reduced)
                    .clip(RoundedCornerShape(26.dp))
                    .background(CardTone),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(Hero).crossfade(250).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color(14, 9, 6, 0),
                                1f to Color(14, 9, 6).copy(alpha = 0.78f),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(
                            "Country sourdough",
                            style = TextStyle(color = Bone, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
                        )
                        BasicText(
                            "Out of the oven at 6:40 · 14 left",
                            style = TextStyle(color = Bone.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    Box(
                        Modifier
                            .height(32.dp)
                            .background(Bone, RoundedCornerShape(16.dp))
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("$9", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp).rise(time, 4, reduced),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(
                    "From the shelf",
                    style = TextStyle(color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                )
                Spacer(Modifier.weight(1f))
                BasicText("See all", style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }

            Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shelf.forEachIndexed { i, item ->
                    Column(Modifier.weight(1f).rise(time, 5 + i, reduced), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(item.url).crossfade(250).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(CardTone),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicText(
                                item.name,
                                style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(item.price, style = TextStyle(color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // The dark pill at the bottom, dipping to 0.97 under the finger.
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.97f else 1f,
            tween(if (pressed) 110 else 160, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
            label = "press",
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 12f).dp)
                .fillMaxWidth()
                .height(56.dp)
                .rise(time, 7, reduced)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(28.dp))
                .background(Ink)
                .clickable(interactionSource = press, indication = null, onClick = onReserve),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Reserve for pickup", style = TextStyle(color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp))
        }
    }
}
