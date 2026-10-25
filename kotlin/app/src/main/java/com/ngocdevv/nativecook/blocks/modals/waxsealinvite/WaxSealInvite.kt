package com.ngocdevv.nativecook.blocks.modals.waxsealinvite

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Wax Seal Invite — an invitation that arrives as a modal over the inbox: a cream envelope closed
 with an oxblood wax seal stamped "L&T". Pull the seal down (or hold it) and it cracks into seven
 pieces that fall away; the flap swings open about its top edge on its red liner, and the card
 rises out of the envelope, comes forward and settles with "Attending" / "Can't make it". The
 loop (WaxSealInviteMotion.kt, LOOP = 10 s) shows the same and closes it all again in reverse.

 Touch: drag the seal down; it follows on a rubber band (at most 30 dp) while hairline cracks
 start; past 60 dp of pull, or after a 0.7 s hold, it breaks and the envelope opens (3.6 s).
 Releasing earlier springs it back and heals the cracks. The close button re-seals it (2.4 s).
 2 s after the last touch an open invitation closes itself in 0.9 s and the loop restarts.
 Reduced motion: the invitation shown open and still; the buttons still answer.
 */

// The user's timeline runs the loop's own script: the break starts it at BREAK_AT (cracks a
// third grown, as the pull left them), it opens until OPEN_AT and closes from CLOSE_AT to LOOP.
private const val BREAK_AT = 2.48f
private const val OPEN_AT = 6.0f
private const val CLOSE_AT = 7.6f
// The seal breaks past BREAK_PULL dp of pull or after BREAK_HOLD s held.
private const val BREAK_PULL = 60f
private const val BREAK_HOLD = 0.7f
private const val IDLE = 2f
private const val RESET = 0.9f

internal val WaxRoom = Color(0xFFF2EDE6)
private val Cream = Color(0xFFFBF3EA)
private val CHIPS = listOf("Sat 14 June", "Lake Orta", "RSVP by 1 May")
private val MAIL = listOf(
    Triple("Studio Morrow", "Your proofs are ready to review", "9:41"),
    Triple("Ada Brennan", "Train times for Saturday", "Yesterday"),
    Triple("Lena & Theo", "Something special in the post", "Yesterday"),
    Triple("Harbor Books", "Your order has shipped", "Tue"),
    Triple("Nico Alvarez", "Dinner on Friday?", "Mon"),
    Triple("Pine & Plaster", "Quote for the hallway", "Sun"),
)

// Seconds from a fixed origin, so a float keeps millisecond precision however long the device has run.
private val WaxOrigin = System.nanoTime()

private fun nowSeconds() = (System.nanoTime() - WaxOrigin) / 1e9f

private class WaxEngine(val reduced: Boolean) {
    var clock = 0f
    var user = false
    var lastTouch = 0f
    var resetting = false
    var pressing = false
    var pressStart = 0f
    var openedAt = -1f
    // The finger's pose: whole (pressed, pulled, hairline cracks) or broken (a time on the script).
    var broken = false
    val scriptT = WaxTrack(0f)
    val press = WaxTrack(0f)
    val pull = WaxTrack(0f)
    var crack = 0f
    val crackTrack = WaxTrack(0f)
    var running = false
    /** Events for the frame loop to pass on: haptics and the open state. */
    var pendingBreak = false

    private val pose = WaxPose()
    private var poseAt = Float.NaN
    private var poseClock = Float.NaN

    fun busy(now: Float) = !reduced && (pressing || user || !scriptT.done(now))

    private fun crackNow(now: Float) = if (pressing) crack else crackTrack.value(now)

    fun pose(now: Float): WaxPose {
        if (now == poseAt && clock == poseClock) return pose
        poseAt = now
        poseClock = clock
        when {
            reduced -> pose.setOpen()
            !user -> pose.setLoop(clock)
            broken -> pose.setLoop(scriptT.value(now))
            else -> {
                pose.setRest()
                pose.press = press.value(now)
                pose.pull = pull.value(now)
                pose.crack = crackNow(now)
            }
        }
        return pose
    }

    /** Runs the script from where it is to `to`, at real speed or squeezed into `seconds`. */
    private fun play(now: Float, to: Float, seconds: Float? = null) {
        val from = scriptT.value(now)
        if (seconds != null) scriptT.timing(now, to, seconds, WaxEaseInOutCurve) else scriptT.timing(now, to, max(0f, to - from), WaxLinear)
    }

    private fun finishClose() {
        broken = false
        scriptT.set(0f)
        press.set(0f)
        pull.set(0f)
        crack = 0f
        crackTrack.set(0f)
        if (resetting) {
            resetting = false
            clock = 0f
            user = false
        }
    }

    /** Takes over from the loop where it is; a moving envelope finishes the move it was making. */
    fun touched(now: Float) {
        lastTouch = now
        if (resetting) return
        if (user) return
        user = true
        val t = clock
        if (t < 2.4f) {
            val p = WaxPose().apply { setLoop(t) }
            broken = false
            press.set(p.press)
            pull.set(p.pull)
            crack = p.crack
            crackTrack.set(p.crack)
            press.timing(now, 0f, 0.25f, WaxEaseQuad)
            pull.spring(now, 0f, 0.45f, 0.6f)
            crackTrack.timing(now, 0f, 0.3f, WaxEaseQuad)
            return
        }
        broken = true
        scriptT.set(t)
        if (t < OPEN_AT) play(now, OPEN_AT) else if (t >= CLOSE_AT) play(now, LOOP)
    }

    private fun breakSeal(now: Float) {
        pressing = false
        broken = true
        scriptT.set(BREAK_AT)
        play(now, OPEN_AT)
        pendingBreak = true
    }

    fun closeEnvelope(now: Float) {
        touched(now)
        if (!broken || scriptT.value(now) < OPEN_AT - 0.01f) return
        if (scriptT.value(now) < CLOSE_AT) scriptT.set(CLOSE_AT)
        play(now, LOOP)
    }

    fun step(now: Float, dt: Float, auto: Boolean) {
        // A close that reached the end of the script hands the screen back.
        if (broken && scriptT.target >= LOOP && scriptT.done(now)) finishClose()
        if (!user) {
            if (auto) clock = (clock + dt) % LOOP
            return
        }
        if (reduced) return
        if (pressing && !broken) {
            val held = now - pressStart
            crack = max(crack, min(0.3f, held / BREAK_HOLD * 0.3f))
            if (held >= BREAK_HOLD) breakSeal(now)
            return
        }
        val t = scriptT.value(now)
        val isOpen = broken && t > OPEN_AT - 0.001f && t < CLOSE_AT
        if (!isOpen) openedAt = -1f else if (openedAt < 0f) openedAt = now
        if (resetting || now - max(lastTouch, openedAt) < IDLE) return
        if (!broken) {
            user = false
            clock = 0f
        } else if (isOpen) {
            // An open invitation closes itself, squeezed into RESET s.
            resetting = true
            scriptT.set(CLOSE_AT)
            play(now, LOOP, RESET)
        }
    }

    fun sealDown(now: Float) {
        touched(now)
        if (broken) return
        pressing = true
        pressStart = now
        crack = crackTrack.value(now)
        press.timing(now, 1f, 0.12f, WaxEaseQuad)
    }

    fun sealMove(now: Float, dy: Float) {
        if (broken || !pressing) return
        lastTouch = now
        val raw = max(0f, dy)
        pull.set(waxRubber(raw, WAX_PULL_LIMIT))
        crack = max(crack, min(0.3f, raw / BREAK_PULL * 0.3f))
        if (raw > BREAK_PULL) breakSeal(now)
    }

    fun sealUp(now: Float) {
        lastTouch = now
        if (!pressing || broken) return
        pressing = false
        press.timing(now, 0f, 0.2f, WaxEaseQuad)
        pull.spring(now, 0f, 0.45f, 0.55f)
        crackTrack.set(crack)
        crackTrack.timing(now, 0f, 0.35f, WaxEaseQuad)
    }
}

private class WaxArt(val env: WaxEnvelopeArt, val card: Bitmap?, val cardShadowed: Bitmap?, val mask: Bitmap?)

/**
 * An invitation modal over the inbox: an envelope closed with a wax seal. Pull the seal down (or
 * hold it) to crack it into pieces; the flap opens and the card rises out with RSVP buttons.
 *
 * @param autoPlay breaks the seal, opens the envelope, shows the card and closes it again, on a loop of [LOOP] s.
 */
@Composable
fun WaxSealInvite(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(density) / density.density
    val bottom = insets.getBottom(density) / density.density
    BoxWithConstraints(modifier.fillMaxSize().background(WaxRoom)) {
        val layout = remember(maxWidth, maxHeight, top, bottom) { waxLayout(maxWidth.value, maxHeight.value, top, bottom) }
        // The app under the modal: the inbox, blurred (Android 12+) and dimmed.
        WaxInbox(top, Modifier.blur(9.dp))
        Box(Modifier.fillMaxSize().background(Color(30, 16, 12, 158)))
        WaxScene(layout, autoPlay && !reduced, reduced)
    }
}

@Composable
private fun WaxInbox(top: Float, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = (top + 12f).dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(
            "Inbox",
            Modifier.padding(bottom = 8.dp),
            style = TextStyle(color = Color(0xFF1D1714), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold),
        )
        for ((from, subject, whenText) in MAIL) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(Color(0xFFCDB9A6), CircleShape))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(from, style = TextStyle(color = Color(0xFF1D1714), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        BasicText(whenText, style = TextStyle(color = Color(0xFF8A7D72), fontSize = 13.sp))
                    }
                    BasicText(subject, Modifier.padding(top = 2.dp), style = TextStyle(color = Color(0xFF5D524A), fontSize = 14.sp))
                }
            }
        }
    }
}

@Composable
private fun WaxScene(layout: WaxLayout, auto: Boolean, reduced: Boolean) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val scale = min(d, 3f)
    val r = layout.sealR
    val engine = remember(reduced) { WaxEngine(reduced) }
    val tick = remember { mutableFloatStateOf(nowSeconds()) }
    val wake = remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(reduced) }
    var answer by remember { mutableStateOf<String?>(null) }
    val art by produceState<WaxArt?>(null, layout, scale) {
        value = withContext(Dispatchers.Default) {
            WaxArt(
                waxBakeEnvelope(layout, min(scale, 2f)),
                waxBakeCard(layout.cardW, layout.cardH, scale, shadow = false),
                waxBakeCard(layout.cardW, layout.cardH, scale, shadow = true),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) waxBuildSealMask(r, scale) else null,
            )
        }
    }
    val wax = remember(art) {
        val mask = art?.mask
        if (mask != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(WAX_AGSL).apply {
                    setInputShader("mask", BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    setFloatUniform("origin", 0f, 0f)
                    setFloatUniform("scale", scale)
                    setFloatUniform("R", r)
                    setFloatUniform("pad", WAX_SEAL_PAD)
                }
            }.getOrNull()
        } else {
            null
        }
    }
    val g = remember(layout, wax) { WaxGeometry(layout, wax) }

    LaunchedEffect(engine, auto, wake.intValue) {
        engine.running = true
        var last = -1L
        try {
            while (true) {
                val keepGoing = withFrameNanos { nanos ->
                    val now = (nanos - WaxOrigin) / 1e9f
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    engine.step(now, dt, auto)
                    if (engine.pendingBreak) {
                        engine.pendingBreak = false
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                    val p = engine.pose(now)
                    val isOpen = p.card > 1.9f && p.buttons > 0.5f
                    if (isOpen != open) {
                        open = isOpen
                        if (!isOpen) answer = null
                        if (isOpen && engine.user && !reduced) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    tick.floatValue = now
                    auto || engine.busy(now)
                }
                if (!keepGoing) break
            }
        } finally {
            engine.running = false
        }
    }

    fun wakeUp() {
        tick.floatValue = nowSeconds()
        if (!engine.running) wake.intValue += 1
    }

    // On short screens the card rises past the header: the header steps aside while it does.
    val headerFades = layout.clearTop < layout.headerTop + 86f

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 72.dp)
                .offset { IntOffset(0, (layout.headerTop * d).roundToInt()) }
                .graphicsLayer {
                    if (headerFades) {
                        val c = engine.pose(tick.floatValue).card
                        alpha = 1f - 0.9f * waxClamp01((c - 0.4f) / 0.4f) * waxClamp01((1.9f - c) / 0.4f)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("YOU'RE INVITED", style = TextStyle(color = Color(0xFFE9B9A4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("A letter from Lena & Theo", style = TextStyle(color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp))
            BasicText("Delivered yesterday by courier", style = TextStyle(color = Cream.copy(alpha = 0.62f), fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }

        Canvas(Modifier.fillMaxSize()) {
            val p = engine.pose(tick.floatValue)
            scale(d, d, pivot = Offset.Zero) {
                art?.let { waxDrawScene(layout, it, g, p) }
            }
        }

        // The seal's hit area: pull down or hold.
        Box(
            Modifier
                .offset { IntOffset(((layout.sealX - r - 22f) * d).roundToInt(), ((layout.sealY - r - 22f) * d).roundToInt()) }
                .size((r * 2f + 44f).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Wax seal. Pull down or hold to open the invitation."
                }
                .then(
                    if (open || reduced) {
                        Modifier
                    } else {
                        Modifier.pointerInput(engine) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                engine.sealDown(nowSeconds())
                                wakeUp()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    engine.sealMove(nowSeconds(), (change.position.y - down.position.y) / d)
                                }
                                engine.sealUp(nowSeconds())
                                wakeUp()
                            }
                        }
                    },
                ),
        )

        // The card's RSVP buttons (they follow the card).
        Box(
            Modifier
                .offset { IntOffset((layout.cardX * d).roundToInt(), 0) }
                .size(layout.cardW.dp, layout.cardH.dp)
                .graphicsLayer {
                    val p = engine.pose(tick.floatValue)
                    val (cardTop, s) = waxCardPlace(layout, p.card)
                    alpha = p.buttons.coerceIn(0f, 1f)
                    translationY = cardTop * d
                    scaleX = s
                    scaleY = s
                },
        ) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tap: (String) -> Unit = { a ->
                    engine.touched(nowSeconds())
                    answer = a
                    val kind = if (a == "yes" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
                    view.performHapticFeedback(kind)
                    wakeUp()
                }
                WaxPill(open, answer == "no", true, if (answer == "yes") "See you there" else "Attending") { tap("yes") }
                WaxPill(open, answer == "yes", false, if (answer == "no") "Sent your regrets" else "Can't make it") { tap("no") }
            }
        }

        // The close button.
        val closeSource = remember { MutableInteractionSource() }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp)
                .offset { IntOffset(0, ((layout.headerTop - 4f) * d).roundToInt()) }
                .size(36.dp)
                .graphicsLayer { alpha = engine.pose(tick.floatValue).buttons.coerceIn(0f, 1f) }
                .background(Color(255, 240, 225, 36), CircleShape)
                .border((1f / d).dp, Color(255, 240, 225, 77), CircleShape)
                .semantics { contentDescription = "Close and seal again" }
                .then(
                    if (open && !reduced) {
                        Modifier.clickable(interactionSource = closeSource, indication = null, role = Role.Button) {
                            answer = null
                            engine.closeEnvelope(nowSeconds())
                            wakeUp()
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                scale(d, d, pivot = Offset.Zero) {
                    val c = Color(0xFFF3E6D4)
                    drawLine(c, Offset(2f, 2f), Offset(12f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(c, Offset(12f, 2f), Offset(2f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, (layout.chipsTop * d).roundToInt()) },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            for (c in CHIPS) {
                Box(
                    Modifier
                        .height(30.dp)
                        .background(Color(255, 240, 225, 26), RoundedCornerShape(15.dp))
                        .border((1f / d).dp, Color(255, 240, 225, 71), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(c, style = TextStyle(color = Cream.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                }
            }
        }

        BasicText(
            "Pull the seal down to open",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, (layout.hintTop * d).roundToInt()) }
                .graphicsLayer {
                    val p = engine.pose(tick.floatValue)
                    alpha = max(0f, 1f - p.crack * 3f - p.card).coerceIn(0f, 1f)
                },
            style = TextStyle(color = Cream.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun RowScope.WaxPill(enabled: Boolean, faded: Boolean, yes: Boolean, label: String, onTap: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .graphicsLayer {
                val s = if (pressed) 0.97f else 1f
                scaleX = s
                scaleY = s
                alpha = if (faded) 0.45f else 1f
            }
            .then(
                if (yes) {
                    Modifier
                        .cssBoxShadow(Color(60, 10, 10, 89), 1.dp, 2.dp, 20.dp)
                        .cssBoxShadow(Color(60, 10, 10, 46), 6.dp, 14.dp, 20.dp)
                        .background(Color(0xFF7C1A20), shape)
                } else {
                    Modifier.background(Color(255, 250, 240, 153), shape).border(1.dp, Color(0xFFB48F58), shape)
                },
            )
            .then(if (enabled) Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onTap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = if (yes) {
                TextStyle(color = Color(0xFFFBF3E6), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                TextStyle(color = Color(0xFF4A2A22), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            },
        )
    }
}

/** The card's top (dp) and scale for a card value: inside, rising clear, then forward over the envelope. */
private fun waxCardPlace(l: WaxLayout, c: Float): Pair<Float, Float> {
    val top = if (c <= 1f) l.insideTop + (l.clearTop - l.insideTop) * c else l.clearTop + (l.finalTop - l.clearTop) * (c - 1f)
    return top to (if (c <= 1f) 1f else 1f + 0.1f * (c - 1f))
}

/** The fixed geometry and the reused paints of the scene. Coordinates are dp. */
private class WaxGeometry(l: WaxLayout, val wax: Any?) {
    val r = l.sealR
    val side = (r + WAX_SEAL_PAD) * 2f
    val cracks = waxMakeCracks(r)
    val wedges = Array(WAX_CRACKS) { waxWedgePath(cracks, it, r) }
    val mids = FloatArray(WAX_CRACKS) { waxPieceAngle(cracks, it) }
    val flap = waxFlapPath(l.env.w, l.flap, l.env.x, l.env.y)
    val crackLines = Path()
    val motion = FloatArray(5)
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val waxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { if (wax != null) shader = wax as Shader }
    val flapShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF462810.toInt()
        maskFilter = BlurMaskFilter(waxSigma(3f), BlurMaskFilter.Blur.NORMAL)
    }
    val sealShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3C0A0A.toInt()
        maskFilter = BlurMaskFilter(waxSigma(4f), BlurMaskFilter.Blur.NORMAL)
    }
    val crackDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF2A0507.toInt()
    }
    val crackLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFD4646A.toInt()
    }
    val hinge = Matrix()
    val src = FloatArray(8)
    val dst = FloatArray(8)
    val rect = RectF()
}

private fun alpha255(a: Float) = (a * 255f).roundToInt().coerceIn(0, 255)

/**
 * Skia's [{ perspective: 900 }, { rotateX: a }] about the envelope's top edge, as the homography
 * of the flap's rectangle: a point y below the hinge goes to y cos a, raised y sin a toward the
 * eye, and is projected from 900 dp away. False when the flap is edge-on.
 */
private fun waxHingeMatrix(g: WaxGeometry, l: WaxLayout, a: Float): Boolean {
    val cx = l.width / 2f
    val hy = l.env.y
    val x0 = l.env.x
    val x1 = l.env.x + l.env.w
    val y1 = l.flap + 4f
    if (kotlin.math.abs(cos(a)) < 0.01f) return false
    fun put(i: Int, x: Float, y: Float) {
        g.src[i * 2] = x
        g.src[i * 2 + 1] = hy + y
        val z = y * sin(a)
        val s = 900f / (900f - z)
        g.dst[i * 2] = cx + (x - cx) * s
        g.dst[i * 2 + 1] = hy + y * cos(a) * s
    }
    put(0, x0, 0f)
    put(1, x1, 0f)
    put(2, x1, y1)
    put(3, x0, y1)
    return g.hinge.setPolyToPoly(g.src, 0, g.dst, 0, 4)
}

private fun DrawScope.waxDrawScene(l: WaxLayout, art: WaxArt, g: WaxGeometry, p: WaxPose) {
    val env = l.env
    drawIntoCanvas { canvas ->
        val n = canvas.nativeCanvas
        val e = art.env
        fun image(b: Bitmap?, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
            if (b == null) return
            g.rect.set(x, y, x + w, y + h)
            g.bitmapPaint.alpha = alpha255(alpha)
            n.drawBitmap(b, null, g.rect, g.bitmapPaint)
        }
        // The envelope's two shadows and its back with the liner.
        val bb = WAX_BACK_BLEED
        image(e.back, env.x - bb, env.y - bb, env.w + bb * 2f, env.h + bb * 2f)

        // The flap's lined inside, once it is past upright.
        if (p.flap > WAX_PI / 2f && waxHingeMatrix(g, l, p.flap)) {
            n.save()
            n.concat(g.hinge)
            image(e.flapIn, env.x, env.y, env.w, l.flap + 4f)
            g.fill.color = 0xFF1A0606.toInt()
            g.fill.alpha = alpha255((WAX_PI - p.flap) / (WAX_PI / 2f) * 0.5f)
            n.drawPath(g.flap, g.fill)
            n.restore()
        }

        // The card behind the envelope's front while it goes in or out (clipped to above its mouth).
        if (p.card > 0.001f && p.card <= 1f) {
            n.save()
            n.clipRect(0f, 0f, l.width, env.y + env.h * 0.5f)
            waxDrawCard(n, l, art.card, p.card, g)
            n.restore()
        }

        image(e.front, env.x, env.y, env.w, env.h)

        // The flap's shadow on the front, then its outside while it is less than upright.
        val fs = 0.22f * max(0f, cos(p.flap))
        if (fs > 0.002f) {
            n.save()
            n.translate(0f, 3f)
            g.flapShadow.alpha = alpha255(fs)
            n.drawPath(g.flap, g.flapShadow)
            n.restore()
        }
        if (p.flap <= WAX_PI / 2f && waxHingeMatrix(g, l, p.flap)) {
            n.save()
            n.concat(g.hinge)
            image(e.flapOut, env.x, env.y, env.w, l.flap + 4f)
            val shade = p.flap / (WAX_PI / 2f) * 0.45f
            if (shade > 0.002f) {
                g.fill.color = 0xFF2A1A0C.toInt()
                g.fill.alpha = alpha255(shade)
                n.drawPath(g.flap, g.fill)
            }
            n.restore()
        }

        // The seal, in its own coordinates (centre at 0, 0).
        n.save()
        n.translate(l.sealX, l.sealY + p.pull)
        val ss = 1f - 0.05f * p.press
        n.scale(ss, ss)
        val shadowA = 0.4f * max(0f, 1f - p.fall * 5f)
        if (shadowA > 0.002f) {
            g.sealShadow.alpha = alpha255(shadowA)
            n.drawCircle(1f, 3.5f, g.r * 0.98f, g.sealShadow)
        }
        val h = g.side / 2f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && g.wax != null) {
            (g.wax as RuntimeShader).setFloatUniform("gleam", p.gleam)
        }
        if (!p.broken) {
            waxDrawWax(n, g, h)
        } else {
            for (i in 0 until WAX_CRACKS) {
                waxPieceMotion(i, g.mids[i], p.fall, g.r, g.motion)
                // Off screen once fallen far enough: skip it.
                if (g.motion[1] > l.height) continue
                n.save()
                n.translate(g.motion[3] + g.motion[0], g.motion[4] + g.motion[1])
                n.rotate(Math.toDegrees(g.motion[2].toDouble()).toFloat())
                n.translate(-g.motion[3], -g.motion[4])
                n.clipPath(g.wedges[i])
                waxDrawWax(n, g, h)
                n.restore()
            }
        }
        val crackA = max(0f, 1f - p.fall * 6f)
        if (crackA > 0.002f && p.crack > 0.001f) {
            waxCrackPath(g.cracks, p.crack, g.crackLines)
            g.crackDark.alpha = alpha255(crackA)
            n.drawPath(g.crackLines, g.crackDark)
            n.save()
            n.translate(0.6f, 0.8f)
            g.crackLight.alpha = alpha255(crackA)
            n.drawPath(g.crackLines, g.crackLight)
            n.restore()
        }
        n.restore()

        // The card in front, with its shadows, once it has come out.
        if (p.card > 1f) waxDrawCard(n, l, art.cardShadowed, p.card, g)
    }
}

/** The wax over the seal's square: the shader, or below Android 13 a few gradients approximating it. */
private fun waxDrawWax(n: android.graphics.Canvas, g: WaxGeometry, h: Float) {
    if (g.wax != null) {
        n.drawRect(-h, -h, h, h, g.waxPaint)
        return
    }
    val r = g.r
    g.fill.alpha = 255
    g.fill.shader = android.graphics.RadialGradient(
        -r * 0.3f, -r * 0.4f, r * 1.3f,
        intArrayOf(0xFFB0303A.toInt(), 0xFF8F1A21.toInt(), 0xFF5E0B12.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    n.drawCircle(0f, 0f, r, g.fill)
    g.fill.shader = android.graphics.RadialGradient(
        0f, 0f, r * 0.68f,
        intArrayOf(0xFF6E1018.toInt(), 0xFF7E141C.toInt(), 0x00000000), floatArrayOf(0f, 0.9f, 1f), Shader.TileMode.CLAMP,
    )
    n.drawCircle(0f, 0f, r * 0.68f, g.fill)
    g.fill.shader = null
}

/** The card at its place for `c`, scaled about its centre. */
private fun waxDrawCard(n: android.graphics.Canvas, l: WaxLayout, bmp: Bitmap?, c: Float, g: WaxGeometry) {
    if (bmp == null) return
    val (top, s) = waxCardPlace(l, c)
    n.save()
    n.translate(l.cardX + l.cardW / 2f, top + l.cardH / 2f)
    n.scale(s, s)
    n.translate(-l.cardW / 2f, -l.cardH / 2f)
    val b = WAX_CARD_BLEED
    g.rect.set(-b, -b, l.cardW + b, l.cardH + b)
    g.bitmapPaint.alpha = 255
    n.drawBitmap(bmp, null, g.rect, g.bitmapPaint)
    n.restore()
}
