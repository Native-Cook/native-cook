package com.ngocdevv.nativecook.blocks.splash.cheesedripburger

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlin.math.hypot
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion

private const val WORD = "MELTY"

/**
 * The launch screen of an invented burger kitchen (Copper Bun): a deep red poster with a giant
 * condensed "MELTY" above and below a double cheeseburger. The cheese is drawn in code as molten
 * goo: a drape along each slice, drips that stretch under gravity, neck down and let go of a drop
 * that falls into a puddle at the base, glossy with a warm gradient, a specular streak, a darker
 * rim and a soft shadow on the patty. The giant word stacks up behind once, steam rises.
 *
 * Loop (LOOP = 8 s): rest → the melt builds (1.6–4.3 s), two drops fall (4.15, 4.6 s) → the drips
 * set back to rest (5–7 s). Hold to melt more (drops keep falling), release and it slowly sets; tap
 * to drop the burger in again. Reduced motion: the rest poster, still.
 */
@Composable
fun CheeseDripBurger(autoPlay: Boolean = false) {
    val m = CheeseDripBurgerMotion
    val art = CheeseDripBurgerArt
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberLiveReducedMotion()
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = false)

    val res = remember { art.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { CheeseDripBurgerTouch() }
    val awake = remember { mutableStateOf(false) }
    val pendingHaptics = remember { ArrayList<Double>() }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val c = base + (now - startNanos) / 1e9
                clock.doubleValue = c
                if (touch.update(c)) pendingHaptics.add(c + m.PINCH_LEAD)
                if (pendingHaptics.removeAll { it <= c }) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (!autoPlay && !touch.touching && clock.doubleValue > touch.restsAfter) {
                awake.value = false
                break
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF000000.toInt() or art.RED))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val layout = remember(height, top, bottom) { CheeseDripBurgerArt.Layout(393.0, height, top, bottom) }

        val dropIn = {
            if (!reduced) {
                touch.dropIn(clock.doubleValue)
                awake.value = true
                pendingHaptics.add(clock.doubleValue + m.FALL_TIME)
                clock.doubleValue += 1e-6
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Copper Bun. MELTY burger. Double Melt, 2 patties, 2 slices, 8 dollars 90. " +
                        "A double cheeseburger with molten cheese dripping down every layer. Hold to melt more, tap to drop the burger in again."
                    role = Role.Button
                    onClick { dropIn(); true }
                }
                .pointerInput(reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (reduced) return@awaitEachGesture
                        touch.begin(clock.doubleValue, autoPlay)
                        awake.value = true
                        clock.doubleValue += 1e-6
                        var moved = 0f
                        var lastUptime = down.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastUptime = change.uptimeMillis
                            moved = maxOf(moved, hypot(change.position.x - down.position.x, change.position.y - down.position.y) / (size.width / 393f))
                        } while (event.changes.any { it.pressed })
                        touch.end(clock.doubleValue)
                        if (lastUptime - down.uptimeMillis < 250 && moved < 12) dropIn()
                    }
                },
        ) {
            val c = clock.doubleValue
            val t: Double? = if (reduced) 0.0 else touch.loopTime(c, autoPlay)
            val ambient = if (reduced) 0.0 else m.mod(c, m.LOOP)
            val melts = if (reduced) m.layers.map { DoubleArray(it.drips.size) } else touch.melts(c, t)
            val events = if (reduced) emptyList() else touch.activeEvents(c, t)
            val tau = if (reduced) -1.0 else c - touch.dropInClock
            val lit = DoubleArray(m.ROWS) { if (reduced) 0.0 else touch.rowLit(c, t, it) }
            val slide = DoubleArray(m.ROWS) { if (t != null) m.rowSlide(t, it) else 0.0 }
            val frame = m.frame(melts, events, ambient, m.jiggle(tau))
            val drop = m.dropIn(tau)

            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            canvas.save()
            canvas.scale(scale, scale)
            art.drawBackground(canvas, layout, res)
            art.drawRows(canvas, layout, lit, slide, WORD, res)
            art.drawFloorShadow(canvas, layout, -drop[0])

            canvas.save()
            canvas.translate(196.5f, (layout.baseY + drop[0]).toFloat())
            canvas.scale(drop[1].toFloat(), drop[2].toFloat())
            canvas.translate(-196.5f, -layout.baseY.toFloat())
            canvas.translate(layout.imageX.toFloat(), layout.imageY.toFloat())
            canvas.scale(art.burgerScale.toFloat(), art.burgerScale.toFloat())
            art.drawBurgerImage(canvas, res)
            art.drawCheese(canvas, frame)
            art.drawSteam(canvas, ambient, 1.0)
            canvas.restore()

            // Copy.
            val brandW = art.wordWidth("COPPER BUN", 15.0, res.anton, 2.4)
            art.drawMark(canvas, (196.5 - (30 + brandW) / 2).toFloat(), (layout.brandY - 7.5).toFloat(), art.argb(art.SAND))
            art.drawWord(canvas, "COPPER BUN", 196.5 + 15, layout.brandY, 15.0, res.anton, art.argb(art.SAND), 2.4)
            art.drawWord(canvas, WORD, 196.5, layout.topWordY, art.WORD_SIZE, res.anton, art.argb(art.CREAM))
            art.drawWord(canvas, WORD, 196.5, layout.bottomWordY, art.WORD_SIZE, res.anton, art.argb(art.CREAM))
            art.drawWord(canvas, "BURGER", 196.5 + 4.5, layout.labelY, 22.0, res.anton, art.argb(art.SAND), 9.0)
            art.drawLine(canvas, "Double Melt  ·  2 patties, 2 slices  ·  $8.90", 196.5, layout.productY, 14.0, res.semibold, art.argb(art.SAND, 0.85))
            canvas.restore()
        }
    }
}
