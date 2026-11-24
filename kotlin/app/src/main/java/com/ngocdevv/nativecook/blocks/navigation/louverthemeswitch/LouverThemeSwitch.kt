// Louver Theme Switch — a theme change where the whole screen turns like a louver.
//
// The reusable piece. Give it `screen`, a composable that draws your screen for a theme; it keeps both
// themes composed as real views, one over the other, snapshots each once (and again when `contentKey`
// changes) through a GraphicsLayer, and during a change one AGSL shader (LtsShader.kt) turns fourteen
// slats in perspective, each with the light screen on its front and the dark one on its back. At rest
// the overlay draws nothing and the live screen takes touches.
//
// autoPlay: LOOP = 8 s. A ghost finger taps Dark, the slats turn top to bottom, the dark screen holds,
// the finger taps Light and they turn back; frame 8 s equals frame 0. The control, or a vertical drag
// that scrubs the turn and commits or springs back on release, takes over at once; IDLE seconds after
// the last touch the screen returns to light and the loop restarts at t = 0. Reduced motion: the theme
// changes at once, no turn and no loop.
//
// Thread model: one frame callback writes `amount` and the ghost; only draw and graphicsLayer lambdas
// read them. Composition changes only when the turn crosses its middle, under the overlay, to swap
// which live screen is on top.
package com.ngocdevv.nativecook.blocks.navigation.louverthemeswitch

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class LouverTheme { Light, Dark }

/** what a screen gets: its theme, a way to ask for a theme, and a modifier for the control */
class LouverScreenScope internal constructor(
    /** the theme of this copy of the screen */
    val theme: LouverTheme,
    /** ask for a theme; the louver turns to it */
    val select: (LouverTheme) -> Unit,
    /** put this on the theme control, so the autoplay's finger knows where to tap (the light screen's is measured) */
    val controlModifier: Modifier,
)

private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2
private const val RUN_NONE = 0
private const val RUN_LINEAR = 1
private const val RUN_SPRING = 2
private const val GHOST = 44f
/** let controls lay out and finish animating before a snapshot, ms */
private const val SETTLE_MS = 380L
/** the drag's thresholds (React Native Gesture Handler's activeOffsetY and failOffsetX), dp */
private const val ACTIVE_Y = 12f
private const val FAIL_X = 18f

/** the clock, the script and the finger; everything on the main thread */
private class LtsEngine {
    /** bumped when something the overlay or the ghost shows has changed */
    var tick by mutableIntStateOf(0)
        private set
    /** which live screen is on top: 0 light, 1 dark */
    var side by mutableIntStateOf(0)
        private set

    var auto = false
    var canTurn = true
    var ready = false
    var height = 1f
    /** which side composition has put on top */
    var committed = 0
    var onSide: (Int) -> Unit = {}
    var onHaptic: () -> Unit = {}
    /** the control in the compose root's coordinates, dp: x, y, width, height */
    val control = FloatArray(4)
    /** where this block sits in the compose root, dp */
    var originX = 0f
    var originY = 0f

    private var clock = 0.0
    private var mode = MODE_AUTO
    private var autoStart = 0.0
    var amount = 0.0
        private set
    var dir = 1.0
        private set
    private var asked = 0
    /** a theme asked for: 0, 1, or −1 for nothing */
    private var request = -1
    private var down = false
    private var grabFrom = 0.0
    private var lastTouch = 0.0
    private var running = RUN_NONE
    private var runAt = 0.0
    private var runFrom = 0.0
    private var runTo = 0.0
    private var runSpeed = 0.0
    private var runFor = 0.0
    var ghostA = 0.0
        private set
    var ghostP = 0.0
        private set
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    private val shown = FloatArray(6) { Float.NaN }

    fun select(theme: LouverTheme) {
        val to = if (theme == LouverTheme.Dark) 1 else 0
        if (canTurn) {
            request = to
        } else {
            // no turn: the theme changes at once
            mode = MODE_TOUCH
            running = RUN_NONE
            amount = to.toDouble()
        }
    }

    /** the drag became a drag */
    fun dragStart() {
        val from = amount
        mode = MODE_TOUCH
        running = RUN_NONE
        down = true
        grabFrom = from
        lastTouch = clock
        ghostA = 0.0
        if (from <= REST || from >= 1 - REST) dir = if (from < 0.5) 1.0 else -1.0
    }

    /** translationY in dp from where the finger landed */
    fun dragUpdate(translationY: Float) {
        lastTouch = clock
        amount = clamp01(grabFrom + dir * abs(translationY) / (SCRUB * height))
    }

    fun dragEnd(translationY: Float, velocityY: Float) {
        val away = if (translationY >= 0f) velocityY else -velocityY
        val speed = dir * away / (SCRUB * height)
        val at = amount
        runFrom = at
        runSpeed = max(-5.0, min(5.0, speed))
        runTo = if (at + speed * PROJECT > 0.5) 1.0 else 0.0
        runAt = clock
        running = RUN_SPRING
    }

    /** any touch ended (a drag or not) */
    fun finalize() {
        if (down && running == RUN_NONE && amount > REST && amount < 1 - REST) {
            // cancelled mid-drag: go back to where it began
            runFrom = amount
            runSpeed = 0.0
            runTo = if (grabFrom > 0.5) 1.0 else 0.0
            runAt = clock
            running = RUN_SPRING
        }
        down = false
        lastTouch = clock
    }

    fun step(dt: Float) {
        clock += dt
        val now = clock

        val wanted = request
        if (wanted >= 0) {
            request = -1
            mode = MODE_TOUCH
            lastTouch = now
            ghostA = 0.0
            val from = amount
            if (from != wanted.toDouble()) {
                if (from <= REST || from >= 1 - REST) dir = if (wanted > from) 1.0 else -1.0
                runFrom = from
                runTo = wanted.toDouble()
                runFor = TAP_TURN * abs(wanted - from)
                runAt = now
                running = RUN_LINEAR
            }
        }

        if (mode == MODE_AUTO) {
            if (!ready || !auto) {
                autoStart = now
            } else {
                val t = (now - autoStart) % LOOP
                amount = filmAmount(t)
                dir = filmDir(t)
                val g = ghostAt(t)
                ghostA = g.alpha
                ghostP = g.press
                ghostX = control[0] - originX + control[2] * (if (g.segment == 1) 0.75f else 0.25f)
                ghostY = control[1] - originY + control[3] / 2f
            }
        } else if (running != RUN_NONE) {
            val tau = now - runAt
            val to = runTo
            val x: Double
            val done: Boolean
            if (running == RUN_LINEAR) {
                x = runFrom + (to - runFrom) * seg(tau, 0.0, max(runFor, 0.001))
                done = tau >= runFor
            } else {
                x = clamp01(settleAt(runFrom, runSpeed, to, tau))
                done = abs(x - to) < 0.002 && tau > 0.05
            }
            amount = if (done) to else x
            if (done) {
                running = RUN_NONE
                if (mode == MODE_RETURN) {
                    autoStart = now
                    mode = MODE_AUTO
                }
            }
        } else if (mode == MODE_TOUCH && auto && !down && now - lastTouch >= IDLE) {
            if (amount <= REST) {
                autoStart = now
                mode = MODE_AUTO
            } else {
                dir = -1.0
                runFrom = amount
                runTo = 0.0
                runFor = TAP_TURN * amount
                runAt = now
                running = RUN_LINEAR
                mode = MODE_RETURN
            }
        }

        val nextSide = if (amount > 0.5) 1 else 0
        if (nextSide != asked) {
            asked = nextSide
            side = nextSide
            onSide(nextSide)
            if (mode != MODE_AUTO) onHaptic()
        }
        publish()
    }

    private fun publish() {
        val a = amount.toFloat()
        if (a != shown[0] || dir.toFloat() != shown[1] || ghostA.toFloat() != shown[2] || ghostP.toFloat() != shown[3] ||
            ghostX != shown[4] || ghostY != shown[5]
        ) {
            shown[0] = a
            shown[1] = dir.toFloat()
            shown[2] = ghostA.toFloat()
            shown[3] = ghostP.toFloat()
            shown[4] = ghostX
            shown[5] = ghostY
            tick += 1
        }
    }

    /** the overlay shows while turning, and until composition has put the matching screen on top */
    val coverShown: Boolean
        get() {
            val a = amount
            val atRest = a <= REST || a >= 1 - REST
            return !(atRest && committed == (if (a > 0.5) 1 else 0))
        }
}

/** a snapshot as a software bitmap a BitmapShader can sample on any API level */
private fun software(bitmap: Bitmap): Bitmap =
    if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

@Composable
fun LouverThemeSwitch(
    modifier: Modifier = Modifier,
    /** change this when what the screens show changes, to take new snapshots */
    contentKey: Any? = null,
    autoPlay: Boolean = true,
    onThemeChange: ((LouverTheme) -> Unit)? = null,
    /** your screen; it is composed twice, once per theme */
    screen: @Composable (LouverScreenScope) -> Unit,
) {
    val reduced = rememberLiveReducedMotion()
    val canTurn = !reduced
    val view = LocalView.current
    val engine = remember { LtsEngine() }
    val side = engine.side
    val themeChange by rememberUpdatedState(onThemeChange)
    BlockSystemBars(darkIcons = side == 0)

    BoxWithConstraints(modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0E0F15))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val density = LocalDensity.current.density
        val lightLayer = rememberGraphicsLayer()
        val darkLayer = rememberGraphicsLayer()
        var turn by remember { mutableStateOf<LtsTurn?>(null) }

        SideEffect {
            engine.auto = autoPlay && canTurn
            engine.canTurn = canTurn
            engine.height = height
            engine.committed = side
            engine.onSide = { s -> themeChange?.invoke(if (s == 1) LouverTheme.Dark else LouverTheme.Light) }
            engine.onHaptic = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        }
        LaunchedEffect(engine) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
                    last = now
                    engine.step(dt)
                }
            }
        }
        // snapshot both screens once they have laid out, and again when their content changes
        LaunchedEffect(canTurn, contentKey, width, height) {
            if (!canTurn) return@LaunchedEffect
            delay(SETTLE_MS)
            val light = lightLayer.toImageBitmap().asAndroidBitmap()
            val dark = darkLayer.toImageBitmap().asAndroidBitmap()
            val made = withContext(Dispatchers.Default) {
                val l = software(light)
                val d = software(dark)
                val runtime = if (Build.VERSION.SDK_INT >= 33) runCatching { LtsRuntimeTurn(l, d, density) }
                    .onFailure { Log.w("LouverThemeSwitch", "AGSL turn unavailable, drawing the fallback", it) }
                    .getOrNull() else null
                runtime ?: LtsDrawnTurn(l, d, density)
            }
            turn = made
            engine.ready = true
        }

        val select = remember(engine) { { theme: LouverTheme -> engine.select(theme) } }
        val measureControl = remember(engine, density) {
            Modifier.onGloballyPositioned { coords ->
                val box = coords.boundsInRoot()
                engine.control[0] = box.left / density
                engine.control[1] = box.top / density
                engine.control[2] = box.width / density
                engine.control[3] = box.height / density
            }
        }
        val lightScope = remember(select, measureControl) { LouverScreenScope(LouverTheme.Light, select, measureControl) }
        val darkScope = remember(select) { LouverScreenScope(LouverTheme.Dark, select, Modifier) }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val at = it.positionInRoot()
                    engine.originX = at.x / density
                    engine.originY = at.y / density
                }
                .pointerInput(engine, canTurn) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        val start = first.position
                        var active = false
                        var failed = !canTurn
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                // one finger only: a second one ends the drag
                                if (event.changes.count { it.pressed } > 1 && active) break
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dx = (change.position.x - start.x) / density
                                val dy = (change.position.y - start.y) / density
                                if (!change.pressed) {
                                    if (active) {
                                        change.consume()
                                        engine.dragEnd(dy, tracker.calculateVelocity().y / density)
                                    }
                                    break
                                }
                                if (failed) continue
                                if (!active) {
                                    if (abs(dx) > FAIL_X) {
                                        failed = true
                                        continue
                                    }
                                    if (abs(dy) > ACTIVE_Y) {
                                        active = true
                                        engine.dragStart()
                                    }
                                }
                                if (active) {
                                    change.consume()
                                    engine.dragUpdate(dy)
                                }
                            }
                        } finally {
                            engine.finalize()
                        }
                    }
                },
        ) {
            // both screens stay live and opaque; the one in front is chosen under the overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (side == 1) 2f else 1f)
                    .drawWithContent {
                        darkLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(darkLayer)
                    }
                    .then(if (side == 1) Modifier.touchSink() else Modifier),
            ) {
                screen(darkScope)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (side == 0) 2f else 1f)
                    .drawWithContent {
                        lightLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(lightLayer)
                    }
                    .then(if (side == 0) Modifier.touchSink() else Modifier),
            ) {
                screen(lightScope)
            }
            Canvas(Modifier.fillMaxSize().zIndex(3f)) {
                engine.tick
                val current = turn ?: return@Canvas
                if (!engine.coverShown) return@Canvas
                drawIntoCanvas { c ->
                    val canvas = c.nativeCanvas
                    val saved = canvas.save()
                    canvas.scale(size.width / width, size.height / height)
                    current.draw(canvas, width, height, engine.amount.toFloat(), engine.dir.toFloat())
                    canvas.restoreToCount(saved)
                }
            }
            GhostFinger(
                Modifier
                    .zIndex(4f)
                    .graphicsLayer {
                        engine.tick
                        translationX = (engine.ghostX - GHOST / 2f) * density
                        translationY = (engine.ghostY - GHOST / 2f) * density
                        val s = 1f - 0.16f * engine.ghostP.toFloat()
                        scaleX = s
                        scaleY = s
                        alpha = engine.ghostA.toFloat().coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** the screen on top takes every touch that reaches it, so none falls through to the one under it */
private fun Modifier.touchSink(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** reads on both themes: a pale core inside a dark ring */
@Composable
private fun GhostFinger(modifier: Modifier) {
    Box(
        modifier
            .size(GHOST.dp)
            .cssBoxShadow(rgba(20, 18, 16, 0.22f), offsetY = 2.dp, blur = 12.dp, radius = (GHOST / 2f).dp)
            .background(rgba(120, 118, 112, 0.2f), CircleShape)
            .border(1.5.dp, rgba(120, 118, 112, 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).background(rgba(120, 118, 112, 0.55f), CircleShape))
    }
}
