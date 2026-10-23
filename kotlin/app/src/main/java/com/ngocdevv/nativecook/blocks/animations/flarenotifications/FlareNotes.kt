package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.FrameCallback
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.OutQuad
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.ReduceMotion
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.SharedValue
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.SpringConfig
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.bezier
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.withDelay
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.withSequence
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.withSpring
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.withTiming
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.FlareMotion as M
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform.Haptics
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Flare — three notification pills, each with a small fire at its cap.
// Port of flare-notes.tsx. After a reference clip of
// brand notifications whose trailing ends burn in the brand's colors. Heat
// here means "new": a notification arrives and ignites, tap to read it and the
// fire cools to an ember, tap again to relight, hold to stoke it.
//
// Every shared value runs on the engine (the anim package), which keeps
// Reanimated's semantics: withTiming/withSpring/withDelay/withSequence, frame
// callbacks and reactions in one frame loop, Reduce Motion as
// ReduceMotion.System. Values are snapshot state read in draw / graphicsLayer
// lambdas, so per-frame motion never recomposes.

private val EASE_OUT = bezier(0.23f, 1f, 0.32f, 1f)
private val EASE_IN_OUT = bezier(0.77f, 0f, 0.175f, 1f)

// When the last pill has finished arriving.
private val ARRIVAL = (FLARES.size - 1) * M.STAGGER + M.ENTER

// expo-haptics' default (useMotionPrefs in the RN app returns haptics: true).
private const val HAPTICS = true

private class Geo(val width: Float, val x: Float, val top: Float, val stack: Float)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun FlareNotes() {
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val haptics = remember { Haptics(context) }
    val blobs = remember { FlareBlobs.load(context) }
    var box by remember { mutableStateOf<IntSize?>(null) }
    var round by remember { mutableIntStateOf(0) }
    // Bumped by "Deliver again"; each pill replays itself in place (a reaction
    // on the engine), never recreated.
    val cycle = remember { SharedValue(0.0) }
    // The first frame of three shader layers can hold the render thread for a
    // long time; an arrival started on mount would play out unseen and the
    // pills would pop in whole. So the arrival waits until the frame loop has
    // run a few smooth frames in a row.
    var live by remember { mutableStateOf(false) }

    val geo = box?.let { b ->
        val w = b.width / density
        val h = b.height / density
        val width = min(w - 48f, (M.HEIGHT * M.ASPECT).roundToInt().toFloat())
        val stack = FLARES.size * M.HEIGHT + (FLARES.size - 1) * M.GAP
        val top = (h * 0.36f - stack / 2).roundToInt().toFloat()
        Geo(width, (w - width) / 2, top, stack)
    }

    // Count from the composition that shows the pills, and stop once live.
    DisposableEffect(geo != null, live) {
        if (geo == null || live) return@DisposableEffect onDispose {}
        var smooth = 0
        var last = Double.NaN
        val watch = FrameCallback { ts ->
            val dt = if (last.isNaN()) 100.0 else ts - last
            last = ts
            smooth = if (dt < 34) smooth + 1 else 0
            if (smooth == 6) live = true
        }
        AnimationEngine.addFrameCallback(watch)
        onDispose { AnimationEngine.removeFrameCallback(watch) }
    }

    val replay = {
        cycle.set(cycle.get() + 1)
        // Only the read state lives in composition; it can catch up a frame late.
        round += 1
        if (HAPTICS) haptics.impact(Haptics.Impact.Light)
    }

    val seen = stringResource(R.string.flarenotifications_seen)
    val action = stringResource(R.string.flarenotifications_pill_action)
    Box(Modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (geo != null) {
            Column(
                Modifier.offset(x = geo.x.dp, y = geo.top.dp).width(geo.width.dp),
                verticalArrangement = Arrangement.spacedBy(M.GAP.dp),
            ) {
                FLARES.forEachIndexed { i, f ->
                    key(f.id) {
                        FlarePill(
                            flare = f,
                            blobs = blobs,
                            round = round,
                            cycle = cycle,
                            live = live,
                            index = i,
                            width = geo.width,
                            line = stringResource(f.line),
                            seen = seen,
                            action = action,
                            reduced = reduced,
                            haptics = haptics,
                        )
                    }
                }
            }
            // The controls wait for the last note to land.
            if (live) Controls(top = geo.top + geo.stack + 34, reduced = reduced, onReplay = replay)
        }
    }
}

@Composable
private fun Controls(top: Float, reduced: Boolean, onReplay: () -> Unit) {
    // Reanimated FadeIn.delay(arrival + 120).duration(320).easing(EASE_OUT);
    // no entering animation under Reduce Motion.
    val opacity = remember { SharedValue(if (reduced) 1.0 else 0.0) }
    LaunchedEffect(Unit) {
        if (!reduced) opacity.set(withDelay(ARRIVAL + 120, withTiming(1.0, 320.0, EASE_OUT)))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = top.dp)
            .graphicsLayer { alpha = opacity.get().toFloat() },
    ) {
        Hint(stringResource(R.string.flarenotifications_hint), FlareTone.pageHint)
        Box(Modifier.fillMaxWidth().padding(top = 34.dp), contentAlignment = Alignment.TopCenter) {
            ReplayButton(stringResource(R.string.flarenotifications_deliver_again), onReplay)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FlarePill(
    flare: Flare,
    blobs: FlareBlobs,
    round: Int,
    cycle: SharedValue,
    // False until the stack's shader layers have drawn; the arrival waits for it.
    live: Boolean,
    index: Int,
    width: Float,
    line: String,
    seen: String,
    action: String,
    reduced: Boolean,
    haptics: Haptics,
) {
    val density = LocalDensity.current.density
    // The round the note was read in; a new delivery leaves it unread.
    var readIn by remember { mutableIntStateOf(-1) }
    val read = readIn == round
    // The flame stays faint left of the copy's right edge. Both status lines
    // are measured, so reading a note never moves that edge.
    var copyW by remember { mutableFloatStateOf(0f) }
    var seenW by remember { mutableFloatStateOf(0f) }
    val guard = if (copyW > 0) M.INSET + max(copyW, seenW) + 8 else width * 0.45f
    val crackle = remember { HapticRhythm(haptics) }

    val body = remember(flare) { floatArrayOf(flare.body.red, flare.body.green, flare.body.blue) }

    val enter = remember { SharedValue(0.0) }
    val reach = remember { SharedValue(if (reduced) 1.0 else 0.0) }
    val heat = remember { SharedValue(if (reduced) 1.0 else 0.0) }
    val cool = remember { SharedValue(0.0) }
    // 0 shows the note's line, 1 shows "Seen just now".
    val seenT = remember { SharedValue(0.0) }
    val press = remember { SharedValue(0.0) }
    val stoke = remember { SharedValue(0.0) }
    // 1 once the finger has stayed down past the hold threshold.
    val held = remember { SharedValue(0.0) }
    // Integrated flame time in seconds, so speeding the fire up never jumps the
    // pattern. All three start together, as in the reference.
    val time = remember { SharedValue(0.0) }

    // The arrival: the note fades in from above while its fire catches, licks
    // out past its rest length and settles, the core flashing white. On a
    // replay the old note first fades out over `out` ms and is reset unseen.
    val arrive: (Double) -> Unit = { out ->
        val delay = index * M.STAGGER
        fun reset() = withDelay(out, withTiming(0.0, 0.0))
        enter.set(
            withSequence(
                withTiming(0.0, out, EASE_OUT),
                withDelay(delay, withTiming(1.0, if (reduced) 200.0 else M.ENTER, EASE_OUT)),
            ),
        )
        cool.set(reset())
        seenT.set(reset())
        if (!reduced) {
            reach.set(
                withSequence(
                    reset(),
                    withDelay(delay + M.IGNITE_DELAY, withSpring(1.0, SpringConfig(duration = 900.0, dampingRatio = 0.55))),
                ),
            )
            heat.set(
                withSequence(
                    reset(),
                    withDelay(
                        delay + M.IGNITE_DELAY,
                        withSequence(
                            withTiming(1.7, M.FLASH, EASE_OUT),
                            withTiming(1.0, M.SETTLE, EASE_OUT),
                        ),
                    ),
                ),
            )
        }
    }
    val currentArrive by rememberUpdatedState(arrive)
    val currentReduced by rememberUpdatedState(reduced)

    DisposableEffect(live) {
        if (live) currentArrive(0.0)
        onDispose { crackle.stop() }
    }

    DisposableEffect(Unit) {
        val reaction = AnimationEngine.reaction({ cycle.get() }) { now, before ->
            if (before != null && now != before) currentArrive(if (currentReduced) 120.0 else M.LEAVE)
        }
        onDispose { AnimationEngine.removeReaction(reaction) }
    }

    // useFrameCallback(..., !reduced): flame time, faster when stoked or
    // pressed, slower once read.
    DisposableEffect(reduced) {
        if (reduced) return@DisposableEffect onDispose {}
        var last = Double.NaN
        val tick = FrameCallback { ts ->
            val since = if (last.isNaN()) 16.0 else ts - last
            last = ts
            val dt = min(since, 48.0) / 1000
            val speed = 1 + stoke.get() * 1.1 + press.get() * 0.25 - cool.get() * 0.55
            time.set(time.get() + dt * speed)
        }
        AnimationEngine.addFrameCallback(tick)
        onDispose { AnimationEngine.removeFrameCallback(tick) }
    }

    val toggle = {
        val next = !read
        readIn = if (next) round else -1
        seenT.set(withTiming(if (next) 1.0 else 0.0, if (reduced) 120.0 else M.SWAP, EASE_OUT))
        cool.set(
            withTiming(
                if (next) 1.0 else 0.0,
                if (reduced) 200.0 else if (next) M.COOL else M.RELIGHT,
                EASE_IN_OUT,
            ),
        )
        if (!next && !reduced) {
            heat.set(withSequence(withTiming(1.7, 140.0, EASE_OUT), withTiming(1.0, 600.0, EASE_OUT)))
        }
        if (HAPTICS) {
            if (next) haptics.selection() else haptics.impact(Haptics.Impact.Light)
        }
    }
    val currentToggle by rememberUpdatedState(toggle)

    // RNGH Pan().minDistance(0): onBegin on touch-down, onFinalize on release
    // or cancel. Tap vs hold is decided here: a release before the hold
    // threshold, close to where it started, is a tap.
    val onBegin = {
        press.set(withTiming(1.0, 120.0, EASE_OUT))
        held.set(0.0)
        // The hold timer must run its full 260 ms even with Reduce Motion on:
        // ReduceMotion.System skips the delay and jumps to the end, which made
        // every press a hold and no tap ever read a note.
        held.set(withDelay(M.HOLD_AFTER, withTiming(1.0, 1.0), ReduceMotion.Never))
        if (!reduced) stoke.set(withDelay(M.HOLD_AFTER, withTiming(1.0, M.STOKE, OutQuad)))
        // The crackle waits for the press to become a hold.
        if (HAPTICS && !reduced) crackle.play(first = 70.0, stretch = 0.97, jitter = 60.0, total = M.STOKE, delay = M.HOLD_AFTER)
    }
    val onFinalize = { tx: Float, ty: Float ->
        val tap = held.get() < 1 && hypot(tx, ty) < 24
        held.set(0.0)
        press.set(withSpring(0.0, SpringConfig(duration = 300.0, dampingRatio = 0.8)))
        stoke.set(withSpring(0.0, SpringConfig(duration = 600.0, dampingRatio = 0.7)))
        crackle.stop()
        if (tap) currentToggle()
    }
    val currentBegin by rememberUpdatedState(onBegin)
    val currentFinalize by rememberUpdatedState(onFinalize)

    val shader = remember { RuntimeShader(FLARE_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val frame = remember(flare) { BlobFrame(blobs, blobs.keysFor(flare.id)) }
    val shadowPaint = remember(flare, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = flare.shadow.toArgb()
            // Skia's BlurMask blur is a sigma (10 dp on the RN canvas);
            // Android's BlurMaskFilter takes a radius, converted back to a
            // sigma as 0.57735 * radius + 0.5.
            val sigma = M.SHADOW_BLUR * density
            maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    val label = "${flare.title.replace(Regex("[.*]"), "")}, ${if (read) seen else line}"

    Box(
        Modifier
            .size(width.dp, M.HEIGHT.dp)
            .graphicsLayer {
                // RN views draw children with alpha, without an offscreen
                // layer, so the shadow outside the pill is never clipped.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val e = enter.get().toFloat()
                alpha = e
                translationY = (if (reduced) 0f else (1 - e) * -10f) * density
                val s = (if (reduced) 1f else 0.97f + 0.03f * e) - press.get().toFloat() * 0.03f
                scaleX = s
                scaleY = s
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentBegin()
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) last = change.position
                        if (event.changes.none { it.pressed }) break
                    }
                    currentFinalize((last.x - down.position.x) / density, (last.y - down.position.y) / density)
                }
            }
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                selected = read
                // TalkBack reads this as "Double-tap to mark read or unread",
                // the RN accessibilityHint.
                onClick(label = action) {
                    currentToggle()
                    true
                }
            }
            .drawBehind {
                val px = density
                val h = M.HEIGHT * px
                val r = h / 2
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRoundRect(
                        M.SHADOW_INSET * px,
                        M.SHADOW_DROP * px,
                        size.width - M.SHADOW_INSET * px,
                        M.SHADOW_DROP * px + h,
                        r,
                        r,
                        shadowPaint,
                    )
                }
                val c = cool.get()
                val k = stoke.get()
                val p = press.get()
                frame.update(time.get())
                shader.setFloatUniform("density", px)
                shader.setFloatUniform("size", width, M.HEIGHT)
                // 0 at ignition, 1 at rest, longer when stoked.
                shader.setFloatUniform("stretch", (reach.get() * (1 + 0.55 * k + 0.08 * p)).toFloat())
                shader.setFloatUniform("heat", (heat.get() * (1 + 0.35 * k + 0.15 * p)).toFloat())
                shader.setFloatUniform("cool", (c * (1 - k * 0.5)).toFloat())
                shader.setFloatUniform("ember", M.EMBER)
                shader.setFloatUniform("guard", guard)
                shader.setFloatUniform("body", body[0], body[1], body[2])
                shader.setFloatUniform("geo", frame.geo)
                shader.setFloatUniform("col", frame.col)
                shader.setFloatUniform("rot", frame.rot)
                drawRoundRect(brush, cornerRadius = CornerRadius(r, r))
            },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = M.INSET.dp)
                .onSizeChanged { copyW = it.width / density },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PillTitle(flare.title, flare.ink)
            Box {
                PillLine(
                    line.uppercase(),
                    flare.sub,
                    Modifier.graphicsLayer {
                        val s = seenT.get().toFloat()
                        alpha = 1 - s
                        translationY = (if (reduced) 0f else s * -4f) * density
                    },
                )
                PillLine(
                    seen.uppercase(),
                    flare.sub,
                    Modifier
                        .onSizeChanged { seenW = it.width / density }
                        .graphicsLayer {
                            val s = seenT.get().toFloat()
                            alpha = s
                            translationY = (if (reduced) 0f else (1 - s) * 4f) * density
                        },
                )
            }
        }
    }
}
