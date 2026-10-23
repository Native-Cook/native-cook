package com.ngocdevv.nativecook.blocks.media.tapeshelfplayer

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/*
 Tape Shelf Player — a shelf of home-video tapes under a retro player: a tape slides out of its
 sleeve, flies into the slot and the little CRT lights up with the memory. A port of the SwiftUI
 block (TapeShelfPlayer.swift); the numbers are the same.

 Look: a warm off-white page. At the top a light grey shelf band carries the player: a dark
 rounded box (0.52 × the screen width, 188 dp tall) with a curved-glass CRT, a cassette slot, an
 LED and three buttons. Below, "Collections" and a 3 × 3 grid of VHS sleeves (invented brands,
 a rainbow band, a white sticker with a handwritten title in Gochi Hand), each holding its
 cassette. A magenta "Play something old" pill floats at the bottom; while a tape plays, a black
 transport bar (eject, rewind, pause, fast-forward, shuffle) takes its place.

 autoPlay timeline (LOOP = 10 s, TapeShelfPlayerMotion.kt), with the middle tape "Sunset pier":
   0.0 – 0.8    rest; the screen blinks INSERT TAPE once a second.
   0.8 – 1.05   the sleeve lifts (5 % larger, deeper shadow); 0.95 – 1.4 the cassette slides out to
                its right (ease-in-out); 1.4 – 2.15 it flies on an arc to the slot, turning from
                upright to level, growing 12 % mid-flight and shrinking to the slot's width; the
                sleeve settles back.
   2.15 – 2.45  it squashes flat into the slot and is gone; 2.45 the player jolts (3 dp, a damped
                sine) and the LED turns green.
   2.5 – 2.85   the CRT powers on (a bright line opening into the picture); the transport bar
                rises (2.6 – 3.0) as the pill sinks.
   2.85 – 6.6   the home video plays: a sky and a sun over water with drifting glints, a pier,
                a rolling tracking band, the camcorder's "PLAY ▶" and date stamp.
   6.6 – 8.8    eject: the eject key dips, the picture collapses to a line and a dot, the bar
                sinks and the pill returns, the cassette pops out of the slot, flies back and
                slides into its sleeve.
   8.8 – 10.0   rest: the same frame as 0.0.

 Touch: tap a tape (or the pill, for the next one) to play it; tap eject or the player to eject.
 A touch takes over from whatever is on screen, the loop included: tapping another tape sends the
 one in the player home first (ejected once it is in, turned back mid-flight before that) and then
 inserts the tapped one; tapping a tape that is on its way home turns it round. Pause freezes the
 picture. 2 s after the last touch (and once a tape that was tapped has played for 2 s) the tape
 is ejected and the loop starts again. Reduced motion: no loop and no flight; the screen and the bars cross-fade in 0.3 s and the
 picture holds still.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

/** A tape that was tapped plays at least this long before it is sent home on its own. */
private const val MIN_PLAY = 2.0

/** What the player is doing, as a function of the time since [date]. */
private data class TapeSeq(
    val date: Double,
    val kind: Kind,
    val tape: Int,
    /** EJECT: the seconds of video played. BACK: the insert time it runs back from. */
    val at: Double = 0.0,
    /** A tape to insert as soon as this one is home. */
    val then: Int? = null,
) {
    /** Inserting the tape (then playing it), ejecting it, or turning it back before it was in. */
    enum class Kind { INSERT, EJECT, BACK }
}

private class TapeController(val reduced: Boolean) {
    var autoStart by mutableStateOf<Double?>(null)
    var seq by mutableStateOf<TapeSeq?>(null)
    var frozen by mutableStateOf<Double?>(null)
    var nextTape by mutableIntStateOf(TapeMotion.LOOP_TAPE + 1)
    var busy by mutableStateOf(false)
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    /** How long a tape going home takes; null while one goes in or plays. */
    fun outLength(s: TapeSeq): Double? = when (s.kind) {
        TapeSeq.Kind.INSERT -> null
        TapeSeq.Kind.EJECT -> if (reduced) 0.3 else TapeMotion.EJECT_LENGTH
        TapeSeq.Kind.BACK -> if (reduced) 0.3 else s.at
    }

    /** The sequence at a time: a queued tape starts going in the moment the previous one is home. */
    fun resolved(s: TapeSeq?, at: Double): TapeSeq? {
        if (s == null) return null
        val next = s.then ?: return s
        val len = outLength(s) ?: return s
        if (at - s.date < len) return s
        return TapeSeq(s.date + len, TapeSeq.Kind.INSERT, next)
    }

    /** The pose at a time, and the clock for blinking. */
    fun pose(at: Double): Pair<TapeMotion.Pose, Double> {
        val start = autoStart
        if (start != null && at >= start) {
            val t = (at - start) % TapeMotion.LOOP
            return Pair(TapeMotion.loopPose(t), t)
        }
        val clock = at % 1000
        val s = resolved(seq, at) ?: return Pair(TapeMotion.Pose(), clock)
        val e = at - s.date
        val p = if (reduced) {
            TapeMotion.Pose().apply {
                tape = s.tape
                val f = TapeMotion.ramp(0.0, 0.3, e)
                val on = if (s.kind == TapeSeq.Kind.INSERT) f else 1 - f
                screen = on
                transport = on
                pill = 1 - on
                led = if (on > 0.5) 1.0 else 0.0
                video = 2.4
            }
        } else {
            when (s.kind) {
                TapeSeq.Kind.INSERT -> TapeMotion.insert(e, s.tape)
                TapeSeq.Kind.EJECT -> if (e < TapeMotion.EJECT_LENGTH) TapeMotion.eject(e, s.tape, s.at) else TapeMotion.Pose()
                TapeSeq.Kind.BACK -> TapeMotion.insert(max(0.0, s.at - e), s.tape)
            }
        }
        val f = frozen
        if (f != null && s.kind == TapeSeq.Kind.INSERT && !reduced) p.video = f
        return Pair(p, clock)
    }

    val playing get() = resolved(seq, nowSeconds())?.kind == TapeSeq.Kind.INSERT

    /** Turns whatever is on screen (the loop included) into the hand-driven state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        val start = autoStart
        if (start != null) {
            autoStart = null
            if (now >= start) {
                val t = (now - start) % TapeMotion.LOOP
                seq = when {
                    t >= TapeMotion.INSERT_AT && t < TapeMotion.EJECT_AT ->
                        TapeSeq(now - (t - TapeMotion.INSERT_AT), TapeSeq.Kind.INSERT, TapeMotion.LOOP_TAPE)
                    t >= TapeMotion.EJECT_AT && t < TapeMotion.EJECT_AT + TapeMotion.EJECT_LENGTH ->
                        TapeSeq(
                            now - (t - TapeMotion.EJECT_AT), TapeSeq.Kind.EJECT, TapeMotion.LOOP_TAPE,
                            TapeMotion.EJECT_AT - TapeMotion.INSERT_AT - TapeMotion.POWER_ON_AT,
                        )
                    else -> null
                }
                frozen = null
                return
            }
        }
        seq = resolved(seq, now)
    }

    /**
     * Sends the tape that is going in or playing back to its sleeve: ejected once it is in, turned
     * back the way it came before that. False when no tape is going in or playing.
     */
    fun stop(now: Double): Boolean {
        val s = seq ?: return false
        if (s.kind != TapeSeq.Kind.INSERT) return false
        val e = now - s.date
        seq = if (reduced || e >= TapeMotion.INSERT_LENGTH) {
            TapeSeq(now, TapeSeq.Kind.EJECT, s.tape, frozen ?: max(0.0, e - TapeMotion.POWER_ON_AT))
        } else {
            TapeSeq(now, TapeSeq.Kind.BACK, s.tape, e)
        }
        frozen = null
        return true
    }
}

@Composable
fun TapeShelfPlayer(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val c = remember(reduced) { TapeController(reduced) }
    val scope = rememberCoroutineScope()
    val painter = remember { TapePainter(context.assets) }
    TapeShelfPlayerSystemBars()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.busy) {
        c.frame = nowSeconds()
        if (!reduced || c.busy) while (true) withFrameNanos { c.frame = nowSeconds() }
    }

    fun markBusy() {
        if (!reduced) return
        c.busy = true
        scope.launch {
            delay(700)
            c.busy = false
        }
    }

    /**
     * 2 s after the last touch (and once a tape that was tapped has played [MIN_PLAY] seconds): the
     * tape is sent home, then the loop starts at rest.
     */
    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            var now = nowSeconds()
            c.seq = c.resolved(c.seq, now)
            val s = c.seq
            val ready = when {
                s == null -> null
                s.kind == TapeSeq.Kind.INSERT -> s.date + TapeMotion.INSERT_LENGTH + MIN_PLAY
                s.then != null -> c.outLength(s)?.let { s.date + it + TapeMotion.INSERT_LENGTH + MIN_PLAY }
                else -> null
            }
            if (ready != null && ready > now) {
                delay(((ready - now) * 1000).toLong())
                now = nowSeconds()
                c.seq = c.resolved(c.seq, now)
            }
            c.stop(now)
            val after = c.seq
            val len = after?.let { c.outLength(it) }
            c.autoStart = if (after != null && len != null) max(now, after.date + len) else now
        }
    }

    /** The heavy tap as a tape seats, 1.65 s into its insert (which may still be queued). */
    fun clunk() {
        if (reduced) return
        val s = c.seq ?: return
        val start = when {
            s.kind == TapeSeq.Kind.INSERT -> s.date
            s.then != null -> c.outLength(s)?.let { s.date + it } ?: return
            else -> return
        }
        val wait = start + 1.65 - nowSeconds()
        if (wait <= 0) return
        scope.launch {
            delay((wait * 1000).toLong())
            val r = c.resolved(c.seq, nowSeconds())
            if (r != null && r.kind == TapeSeq.Kind.INSERT && kotlin.math.abs(r.date - start) < 0.001) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    fun play(tape: Int) {
        val now = nowSeconds()
        c.takeOver(now)
        c.nextTape = (tape + 1) % tapes.size
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val s = c.seq
        val len = s?.let { c.outLength(it) }
        if (s != null && len != null && now - s.date < len) {
            // A tape is on its way home.
            c.seq = if (s.kind == TapeSeq.Kind.BACK && s.tape == tape) {
                // The tapped one: turn it round.
                TapeSeq(now - (s.at - (now - s.date)), TapeSeq.Kind.INSERT, tape)
            } else {
                s.copy(then = tape)
            }
        } else if (s != null && s.kind == TapeSeq.Kind.INSERT) {
            if (s.tape != tape) {
                // Another tape: send this one home, then insert the tapped one.
                c.stop(now)
                c.seq = c.seq?.copy(then = tape)
            } else if (now - s.date >= TapeMotion.INSERT_LENGTH) {
                // The one that plays: eject it.
                c.stop(now)
            }
        } else {
            c.seq = TapeSeq(now, TapeSeq.Kind.INSERT, tape)
            c.frozen = null
        }
        clunk()
        markBusy()
        scheduleResume()
    }

    fun eject() {
        val now = nowSeconds()
        c.takeOver(now)
        if (c.stop(now)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            markBusy()
        } else if (c.seq?.then != null) {
            // Nothing in the player yet: forget the tape that was waiting.
            c.seq = c.seq?.copy(then = null)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        scheduleResume()
    }

    fun togglePause() {
        val now = nowSeconds()
        c.takeOver(now)
        val s = c.seq
        if (s == null || s.kind != TapeSeq.Kind.INSERT) {
            scheduleResume()
            return
        }
        val f = c.frozen
        if (f != null) {
            // Carry on from the frozen frame.
            c.seq = TapeSeq(now - (f + TapeMotion.POWER_ON_AT), TapeSeq.Kind.INSERT, s.tape)
            c.frozen = null
        } else {
            c.frozen = max(0.0, now - s.date - TapeMotion.POWER_ON_AT)
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scheduleResume()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(TapePage)) {
        val d = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val g = remember(maxWidth, maxHeight, top, bottom) { TapeGeo(maxWidth.value, maxHeight.value, top, bottom) }
        val pose by remember(c) { derivedStateOf { c.pose(c.frame) } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = if (c.playing) "Playing ${tapes[pose.first.tape].label}" else "Tape player, no tape" }
                .pointerInput(g, c) {
                    detectTapGestures { pos ->
                        val x = pos.x / d
                        val y = pos.y / d
                        val b = g.body
                        if (x >= b.left - 8 && x <= b.right + 8 && y >= b.top - 8 && y <= b.bottom + 8) {
                            eject()
                            return@detectTapGestures
                        }
                        for (i in tapes.indices) {
                            if (g.cell(i).contains(Offset(x, y))) {
                                play(i)
                                return@detectTapGestures
                            }
                        }
                    }
                },
        ) {
            val (p, clock) = pose
            scale(d, d, Offset.Zero) {
                // The shelf band behind the player.
                drawRect(Brush.verticalGradient(listOf(Color(0xFFE9E8E4), Color(0xFFDCDBD6)), startY = g.shelfTop, endY = g.shelfBottom), Offset(0f, g.shelfTop), Size(g.w, g.shelfBottom - g.shelfTop))
                drawRect(Color(0xFFCFCEC9), Offset(0f, g.shelfBottom - 6), Size(g.w, 6f))
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent), startY = g.shelfBottom, endY = g.shelfBottom + 10), Offset(0f, g.shelfBottom), Size(g.w, 10f))
                with(painter) {
                    player(g, p, clock, reduced)
                    val picked = p.tape
                    val flying = p.flight > 0.0001 || p.squash > 0
                    for (i in tapes.indices) if (i != picked) sleeve(i, g.cell(i), 0f)
                    if (!flying) flyingCassette(p, g)
                    sleeve(picked, g.cell(picked), p.lift.toFloat())
                    if (flying) flyingCassette(p, g)
                }
            }
        }

        // Header.
        Row(Modifier.offset(x = 24.dp, y = (g.top + 10).dp).width((g.w - 48).dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Home Tapes", style = TextStyle(color = TapeInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Row {
                repeat(3) { i ->
                    if (i > 0) Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(5.dp).background(TapeMagenta, CircleShape))
                }
            }
        }
        Row(Modifier.offset(x = 24.dp, y = (g.shelfBottom + 16).dp).width((g.w - 48).dp), verticalAlignment = Alignment.Bottom) {
            BasicText("Collections", style = TextStyle(color = TapeInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4 / 22).em))
            Spacer(Modifier.weight(1f))
            BasicText("9 tapes", style = TextStyle(color = TapeInk.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(bottom = 3.dp))
        }

        // A fade so the bottom row tucks under the controls.
        Box(
            Modifier
                .offset(y = (g.h - 120).dp)
                .width(g.w.dp)
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(TapePage.copy(alpha = 0f), TapePage.copy(alpha = 0.96f)))),
        )

        // The pill.
        val playNext by rememberUpdatedState({ play(c.nextTape) })
        TapePress(
            Modifier
                .offset(x = ((g.w - 210) / 2).dp, y = g.pillY.dp)
                .graphicsLayer {
                    val pill = pose.first.pill.toFloat()
                    alpha = pill
                    translationY = 24 * (1 - pill) * d
                },
            enabled = { pose.first.pill > 0.5 },
            label = "Play something old",
            onTap = playNext,
        ) { pressed ->
            Box(
                Modifier
                    .size(210.dp, 44.dp)
                    .graphicsLayer {
                        val sc = if (pressed) 0.94f else 1f
                        scaleX = sc
                        scaleY = sc
                    }
                    .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = TapeMagenta, spotColor = TapeMagenta)
                    .background(TapeMagenta, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Play something old", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        // The transport bar.
        val actions = listOf<() -> Unit>(
            { eject() },
            { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            { togglePause() },
            { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            { play(c.nextTape) },
        )
        val current by rememberUpdatedState(actions)
        Box(
            Modifier
                .offset(x = 24.dp, y = g.transportY.dp)
                .graphicsLayer {
                    val tr = pose.first.transport.toFloat()
                    translationY = (1 - tr) * (90 + g.bottom) * d
                    alpha = min(1f, tr * 3)
                }
                .size((g.w - 48).dp, 60.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .background(Color(0xFF141416), RoundedCornerShape(22.dp))
                .padding(horizontal = 8.dp),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val names = listOf("Eject", "Rewind", if (c.frozen == null) "Pause" else "Play", "Fast forward", "Shuffle")
                for (i in 0 until 5) {
                    TapePress(
                        Modifier.weight(1f),
                        enabled = { pose.first.transport > 0.5 },
                        label = names[i],
                        onTap = { current[i]() },
                    ) { pressed ->
                        Canvas(Modifier.fillMaxWidth44()) {
                            val ep = if (i == 0) pose.first.ejectPress.toFloat() else 0f
                            val sc = (if (pressed) 0.94f else 1f) * (1 - 0.12f * ep)
                            scale(sc, sc) {
                                if (i == 0) drawCircle(Color.White.copy(alpha = 0.08f + 0.3f * ep), 22 * density)
                                scale(density, density, center) {
                                    transportIcon(i, c.frozen != null, center, Color.White.copy(alpha = if (i == 0) 1f else 0.85f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxWidth44() = this.then(Modifier.height(44.dp).width(52.dp))

/** A pressable area: [content] gets whether a finger is down. */
@Composable
private fun TapePress(
    modifier: Modifier,
    enabled: () -> Boolean,
    label: String,
    onTap: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    Box(
        modifier
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { tap(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!enabled()) return@detectTapGestures
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { if (enabled()) tap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { content(pressed) }
}

/** The transport glyphs, about 16 units, centred on c. */
private fun DrawScope.transportIcon(i: Int, paused: Boolean, c: Offset, ink: Color) {
    fun tri(x0: Float, x1: Float, dx: Float = 0f) = Path().apply {
        moveTo(c.x + x0 + dx, c.y - 6.5f)
        lineTo(c.x + x1 + dx, c.y)
        lineTo(c.x + x0 + dx, c.y + 6.5f)
        close()
    }
    when (i) {
        0 -> { // Eject.
            drawPath(Path().apply { moveTo(c.x - 7f, c.y + 1f); lineTo(c.x, c.y - 7f); lineTo(c.x + 7f, c.y + 1f); close() }, ink)
            drawRoundRect(ink, Offset(c.x - 7f, c.y + 3.5f), Size(14f, 3f), CornerRadius(1f))
        }
        1 -> { drawPath(tri(0f, -8f), ink); drawPath(tri(8f, 0f), ink) }
        2 -> if (paused) {
            drawPath(tri(-5f, 7f), ink)
        } else {
            drawRoundRect(ink, Offset(c.x - 5.5f, c.y - 7f), Size(4f, 14f), CornerRadius(1f))
            drawRoundRect(ink, Offset(c.x + 1.5f, c.y - 7f), Size(4f, 14f), CornerRadius(1f))
        }
        3 -> { drawPath(tri(-8f, 0f), ink); drawPath(tri(0f, 8f), ink) }
        else -> { // Shuffle: two crossing arrows.
            val st = Stroke(1.8f, cap = StrokeCap.Round)
            drawPath(Path().apply { moveTo(c.x - 8f, c.y - 5f); cubicTo(c.x - 2f, c.y - 5f, c.x + 1f, c.y + 5f, c.x + 7f, c.y + 5f) }, ink, style = st)
            drawPath(Path().apply { moveTo(c.x - 8f, c.y + 5f); cubicTo(c.x - 2f, c.y + 5f, c.x + 1f, c.y - 5f, c.x + 7f, c.y - 5f) }, ink, style = st)
            for (y in floatArrayOf(-5f, 5f)) {
                drawPath(Path().apply { moveTo(c.x + 4.5f, c.y + y - 2.5f); lineTo(c.x + 7.5f, c.y + y); lineTo(c.x + 4.5f, c.y + y + 2.5f) }, ink, style = st)
            }
        }
    }
}

/** The screen is light, so the status and navigation bar icons are switched to dark while the block is open. */
@Composable
private fun TapeShelfPlayerSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
