package com.ngocdevv.nativecook.blocks.cards.glassdomeshowroom

import android.app.Activity
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * The line-up of Nuvra, an invented electric car maker. Each car stands under a tall glass bell
 * jar; the jars ride a carousel with the featured car in the middle and its neighbours peeking in
 * at the edges. The glass reads as glass: a bright rim, vertical specular streaks that slide
 * across it as the jar moves, a lighter refracted copy of the wash inside, the far and near rims
 * of its base around the car, a contact shadow and a mirror image on the glossy floor. Each car
 * brings its own full-screen colour wash, the giant MORE stack rolls up one row per car, and the
 * carousel smears with its speed (LOOP = 10 s).
 *
 * Swipe to change car (it carries the finger's speed and snaps to a jar); tap a side jar to bring
 * it to the middle; tap the heart to like the car. 2 s after the last touch the carousel springs
 * back to the first car and the loop restarts. Reduced motion: the first car, still. The motion
 * smear needs API 31 (RenderEffect); below it the jars slide unblurred.
 */
@Composable
fun GlassDomeShowroom(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsOff(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val m = GlassDomeShowroomMotion
    val art = GlassDomeShowroomArt
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { GlassDomeShowroomTouch() }
    val awake = remember { mutableStateOf(false) }
    /** Bumped by a heart tap so a still screen (reduced motion) redraws. */
    val likeTick = remember { mutableIntStateOf(0) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                clock.doubleValue = base + (now - startNanos) / 1e9
            }
            if (!autoPlay && !touch.touching && touch.release != null && clock.doubleValue > touch.restsAfter) {
                awake.value = false
                break
            }
        }
    }
    val playing = autoPlay && !reduced
    val phiAt: (Double) -> Double = { c -> if (reduced) 0.0 else touch.phi(c, playing) }
    /** Carousel speed in cars per second (wrap-aware, so the loop's seam is not a jump). */
    val speedAt: (Double) -> Double = { c ->
        when {
            reduced -> 0.0
            touch.touching -> touch.dragVelocity
            else -> m.wrap(phiAt(c) - phiAt(c - 1.0 / 240)) * 240
        }
    }

    val images = remember { art.loadImages(context) }
    val painter = remember { GlassDomeShowroomPainter(images) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(art.argb(art.cars[0].mid)))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val safeTop = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val safeBottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val floorY = m.floorY(height, safeBottom)
        val u: (Double) -> Dp = { (it * k).dp }
        val ts: (Double) -> TextUnit = { with(density) { (it * k).dp.toSp() } }

        // Wash and floor spotlight.
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / 393f
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(s, s)
            painter.drawBackground(canvas, 393f, size.height / s, art.wash(phiAt(clock.doubleValue)), floorY)
            canvas.restore()
        }

        // The stacked headline, rolling one row per car with a vertical smear.
        Canvas(
            Modifier
                .offset(0.dp, u(safeTop))
                .size(u(393.0), u(236.0))
                .graphicsLayer {
                    val r = min(9.0, 0.03 * abs(speedAt(clock.doubleValue - 0.06)) * m.HEADLINE_PITCH) * k * density.density * 0.85
                    renderEffect = if (r > 0.35) BlurEffect(0.01f, r.toFloat(), TileMode.Decal) else null
                }
                .semantics { contentDescription = "More, more, more"; heading() },
        ) {
            val s = size.width / 393f
            val canvas = drawContext.canvas.nativeCanvas
            val p = phiAt(clock.doubleValue - 0.06)
            canvas.save()
            canvas.scale(s, s)
            painter.drawHeadline(canvas, (p - floor(p)) * m.HEADLINE_PITCH)
            canvas.restore()
        }

        Column(
            Modifier.offset(0.dp, u(safeTop + 238)).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val style = TextStyle(color = Color.White, fontSize = ts(18.0), lineHeight = ts(22.0), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("Nuvra Electric", style = style, maxLines = 1, softWrap = false)
            Text("more miles for your money", style = style, maxLines = 1, softWrap = false)
        }

        // The jars, smeared horizontally with the carousel's speed.
        val selected by remember { derivedStateOf { m.index(phiAt(clock.doubleValue)) } }
        val selectedLiked by remember { derivedStateOf { likeTick.intValue; touch.liked(selected, clock.doubleValue, playing) } }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = min(18.0, 0.018 * abs(speedAt(clock.doubleValue)) * m.SPACING) * k * density.density * 0.85
                    renderEffect = if (r > 0.35) BlurEffect(r.toFloat(), 0.01f, TileMode.Decal) else null
                }
                .clearAndSetSemantics {
                    val car = art.cars[selected]
                    contentDescription = "The new ${car.model} ${car.trim}${if (selectedLiked) ", liked" else ""}. Swipe left or right to see another car"
                }
                .pointerInput(reduced, k, floorY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        touch.begin(clock.doubleValue, playing)
                        if (!reduced) awake.value = true
                        var last = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            if (!reduced) {
                                val d = change.position - down.position
                                if (touch.move(d.x / scale.toDouble(), d.y / scale.toDouble(), clock.doubleValue)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        val vx = if (reduced) 0f else tracker.calculateVelocity().x / scale
                        val feedback = touch.end(clock.doubleValue, vx.toDouble(), last.x / scale.toDouble(), last.y / scale.toDouble(), floorY)
                        likeTick.intValue += 1
                        when (feedback) {
                            GlassDomeShowroomTouch.Feedback.None -> {}
                            GlassDomeShowroomTouch.Feedback.Like -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            GlassDomeShowroomTouch.Feedback.Unlike, GlassDomeShowroomTouch.Feedback.Snap ->
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                },
        ) {
            likeTick.intValue
            val s = size.width / 393f
            val c = clock.doubleValue
            val p = phiAt(c)
            val wash = art.wash(p)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(s, s)
            for (jar in m.jars(p)) {
                val age = if (reduced) 10.0 else touch.popAge(jar.index, c, playing)
                painter.drawJar(canvas, jar, floorY, wash, touch.liked(jar.index, c, playing), age)
            }
            canvas.restore()
        }

        // Range and price of the car in the middle, cross-fading between cars.
        Text(
            art.cars[selected].info,
            color = Color.White.copy(alpha = 0.78f), fontSize = ts(14.0), fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            modifier = Modifier
                .offset(0.dp, u(floorY + 56 - 9))
                .fillMaxWidth()
                .graphicsLayer {
                    val p = phiAt(clock.doubleValue)
                    alpha = (1 - 2 * abs(p - m.round(p))).toFloat()
                },
        )

        Box(
            Modifier
                .offset(u(m.CENTRE_X - 110), u(floorY + 76))
                .size(u(220.0), u(52.0))
                .shadow(u(10.0), RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "Book a test drive",
                style = TextStyle(fontSize = ts(17.0), fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
                color = { Color(art.argb(art.wash(phiAt(clock.doubleValue))[2])) },
            )
        }
    }
}

private fun animationsOff(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
