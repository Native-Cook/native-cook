package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val INK = Color(VisorBootMotion.INK)
private val MUTE = Color(VisorBootMotion.MUTE)
private val AMBER = Color(VisorBootMotion.AMBER)
private val BG_EDGE = Color(VisorBootMotion.BG_EDGE)

private const val GUTTER = 24f
private const val ROW_H = 56f
private const val DOT = 6f
private const val DOT_STRETCH = 18f
private const val DOT_GAP = 8f

private class VisorBootCopy(val label: String, val title: String, val body: String)

private val COPY = listOf(
    VisorBootCopy("", "Meet Halo", "Your visor shows what matters, right where you look. Setup takes about a minute."),
    VisorBootCopy("Step 1 of 3", "See more at a glance", "Directions, distance and light appear on the visor only when you look up."),
    VisorBootCopy("Step 2 of 3", "Stay aware", "Sensors on each side notice what’s behind you and give one gentle pulse."),
    VisorBootCopy("Step 3 of 3", "Hands-free", "Ask Halo anything. Answers appear on the visor, not out loud."),
    VisorBootCopy("", "You’re all set", "Next, a quick fit check. It takes about 40 seconds."),
)

/**
 * First-run onboarding for an AR headset app ("Halo"), told as an 18-second product film around a real 3D helmet on
 * a graphite plinth (ported from the React Native block; the Swift port is the reference video). Each of the three
 * steps swings the camera to a new angle and boots a module on the helmet itself: a perspective-correct HUD on the
 * visor, a radar sweeping round the ear sensor, a voice waveform at the mouthpiece. Then Get Started grows out of
 * the pager's pill while the helmet lifts under a light sweep.
 *
 * One clock: the Choreographer frame time, read by the Filament stage's frame callback and by the draw lambdas and
 * graphics layers below (never by composition), through VisorBootController.state(now).
 *
 * Touch: drag the helmet to orbit (velocity decay on yaw, rubber-banded pitch, a light haptic every 45 deg); swipe
 * the copy to page; the arrow, dots and Skip page too. Any touch blends the film into the pages in 0.45 s; 2 s after
 * the last touch it eases back to the poster in 1.4 s and the loop restarts. Reduced motion: the poster page, no
 * loop; pages change with a 0.22 s ease.
 */
@Composable
fun VisorBootOnboarding(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { VisorBootController(autoPlay, reduced) }
    val context = LocalContext.current
    val view = LocalView.current
    val hud = remember { VisorBootHudPainter(context) }
    val backdrop = remember { VisorBootBackdropPainter() }
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    fun now() = frameNanos / 1e9

    BlockSystemBars(darkIcons = false)

    // the one clock, and the haptics that follow it (a page landing under a finger, a 45 deg detent of the orbit)
    LaunchedEffect(controller) {
        var lastPage = -1
        var lastDetent = Int.MIN_VALUE
        while (true) {
            withFrameNanos { n ->
                frameNanos = n
                val t = n / 1e9
                val s = controller.state(t)
                val pg = if (controller.isManual) s.page.roundToInt() else -1
                if (lastPage >= 0 && pg >= 0 && pg != lastPage) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastPage = pg
                val detent = floor(s.yaw / VisorBootController.DETENT_DEG).toInt()
                if (lastDetent != Int.MIN_VALUE && detent != lastDetent && controller.isOrbiting(t)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                lastDetent = detent
            }
        }
    }

    val density = LocalDensity.current
    val insetTop = WindowInsets.statusBars.getTop(density) / density.density
    val insetBottom = WindowInsets.navigationBars.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(BG_EDGE)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val compact = h < 760
        val copyHeight = if (compact) 168f else 206f
        val rowBottom = insetBottom + 20f
        val copyTop = h - (rowBottom + ROW_H + 22f + copyHeight)
        val px = density.density

        // the studio, then the helmet, then the modules on it
        Canvas(Modifier.fillMaxSize()) {
            // until the helmet is on screen there is no plinth to cast the floor shadow
            val floor = visorBootFloor(controller.state(now()), w.toDouble(), h.toDouble(), controller.ready)
            backdrop.draw(this, floor, px)
        }
        VisorBootStage3D(controller, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val s = controller.state(now())
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.scale(px, px)
            hud.draw(nc, w.toDouble(), h.toDouble(), s, copyTop.toDouble())
            nc.restore()
        }

        // drag on the helmet orbits the camera about it
        Box(
            Modifier
                .fillMaxWidth()
                .height(copyTop.coerceAtLeast(0f).dp)
                .pointerInput(controller) {
                    val tracker = VelocityTracker()
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            dx = 0f; dy = 0f
                            controller.orbitBegan(System.nanoTime() / 1e9)
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            controller.orbitEnded(System.nanoTime() / 1e9, v.x / px.toDouble(), v.y / px.toDouble())
                        },
                        onDragCancel = { controller.orbitEnded(System.nanoTime() / 1e9, 0.0, 0.0) },
                    ) { change, drag ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dx += drag.x
                        dy += drag.y
                        controller.orbitChanged(System.nanoTime() / 1e9, dx / px.toDouble(), dy / px.toDouble())
                    }
                },
        )
        // swipe sideways on the copy to page
        Box(
            Modifier
                .fillMaxWidth()
                .height((h - copyTop).coerceAtLeast(0f).dp)
                .offset(y = copyTop.dp)
                .pointerInput(controller) {
                    val tracker = VelocityTracker()
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            dx = 0f
                            controller.pagerBegan(System.nanoTime() / 1e9)
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            controller.pagerEnded(System.nanoTime() / 1e9, dx / px.toDouble(), v.x / px.toDouble(), w.toDouble())
                        },
                        onDragCancel = { controller.pagerEnded(System.nanoTime() / 1e9, dx / px.toDouble(), 0.0, w.toDouble()) },
                    ) { change, drag ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dx += drag
                        controller.pagerChanged(System.nanoTime() / 1e9, dx / px.toDouble(), w.toDouble())
                    }
                },
        )

        VisorBootOverlay(
            controller = controller,
            now = ::now,
            width = w,
            height = h,
            top = insetTop,
            bottom = rowBottom,
            compact = compact,
            copyHeight = copyHeight,
            reduced = reduced,
            haptic = { confirm ->
                view.performHapticFeedback(if (confirm) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
            },
        )
    }
}

/** Header, copy, pager and the Get Started button that grows out of the pager's pill (VisorOverlay.tsx). */
@Composable
private fun VisorBootOverlay(
    controller: VisorBootController,
    now: () -> Double,
    width: Float,
    height: Float,
    top: Float,
    bottom: Float,
    compact: Boolean,
    copyHeight: Float,
    reduced: Boolean,
    haptic: (Boolean) -> Unit,
) {
    val rowW = min(width, 520f) - GUTTER * 2
    val rowY = height - (bottom + 20f) - ROW_H
    val copyY = height - (bottom + 20f + ROW_H + 22f) - copyHeight
    val scope = rememberCoroutineScope()
    val nextPress = remember { Animatable(1f) }
    val startPress = remember { Animatable(1f) }
    fun nowFresh() = System.nanoTime() / 1e9

    fun dotK(i: Int, s: VisorBootState) =
        VisorBootMotion.blend(VisorBootMotion.filmDot(i, s.t), VisorBootMotion.pageDot(i, s.page), s.m)
    fun ctaK(s: VisorBootState) = VisorBootMotion.blend(VisorBootMotion.filmCta(s.t), VisorBootMotion.pageCta(s.page), s.m)
    fun dotLeft(i: Int, s: VisorBootState): Float {
        var x = 0f
        for (j in 0 until i) x += DOT + DOT_STRETCH * dotK(j, s).toFloat() + DOT_GAP
        return x
    }

    Box(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier
                .offset(x = GUTTER.dp, y = (top + 10f).dp)
                .size((width - GUTTER * 2).dp, 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Halo", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            BasicText(
                "Skip",
                style = TextStyle(color = MUTE, fontSize = 17.sp),
                modifier = Modifier.pointerInput(controller) {
                    detectTapGestures {
                        haptic(false)
                        controller.skip(nowFresh())
                    }
                },
            )
        }

        // copy: five blocks, bottom-aligned in one box, cross-fading
        Box(
            Modifier
                .offset(x = GUTTER.dp, y = copyY.dp)
                .size(rowW.dp, copyHeight.dp),
        ) {
            COPY.forEachIndexed { i, c ->
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer {
                            val s = controller.state(now())
                            val o = VisorBootMotion.blend(
                                VisorBootMotion.filmCopyOpacity(i, s.t), VisorBootMotion.pageCopyOpacity(i, s.page), s.m,
                            )
                            val dy = VisorBootMotion.blend(VisorBootMotion.filmCopyDy(i, s.t), VisorBootMotion.pageCopyDy(i, s.page), s.m)
                            alpha = o.toFloat()
                            translationY = dy.toFloat() * density
                        },
                ) {
                    if (c.label.isNotEmpty()) {
                        BasicText(
                            c.label,
                            style = TextStyle(color = AMBER, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    val size = if (compact) 30 else 34
                    BasicText(
                        c.title,
                        style = TextStyle(
                            color = INK, fontSize = size.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4 / size).em, lineHeight = (size * 1.18).roundToInt().sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                    )
                    BasicText(
                        c.body,
                        style = TextStyle(
                            color = MUTE, fontSize = if (compact) 15.sp else 17.sp, lineHeight = if (compact) 20.sp else 23.sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                        modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp).widthIn(max = 340.dp),
                    )
                }
            }
        }

        // pager row: the dots, the arrow and Get Started, drawn from the clock
        Box(
            Modifier
                .offset(x = GUTTER.dp, y = rowY.dp)
                .size(rowW.dp, ROW_H.dp)
                .pointerInput(controller) {
                    detectTapGestures(
                        onPress = { pos ->
                            val s = controller.state(nowFresh())
                            val g = ctaK(s)
                            val x = pos.x / density
                            val target = when {
                                g > 0.5 -> startPress to 0.97f
                                x > rowW - ROW_H -> nextPress to 0.94f
                                else -> null
                            }
                            if (target != null && !reduced) scope.launch { target.first.animateTo(target.second, spring(0.7f, 440f)) }
                            tryAwaitRelease()
                            if (target != null) scope.launch { target.first.animateTo(1f, spring(0.7f, 440f)) }
                        },
                        onTap = { pos ->
                            val t = nowFresh()
                            val s = controller.state(t)
                            val x = pos.x / density
                            when {
                                ctaK(s) > 0.5 -> {
                                    haptic(true)
                                    controller.start(t)
                                }
                                x > rowW - ROW_H -> {
                                    haptic(false)
                                    controller.next(t)
                                }
                                else -> for (i in 0 until 4) {
                                    val l = dotLeft(i, s)
                                    val r = l + DOT + DOT_STRETCH * dotK(i, s).toFloat()
                                    if (x >= l - 4 && x <= r + 4) {
                                        haptic(false)
                                        controller.goPage(i, t)
                                        break
                                    }
                                }
                            }
                        },
                    )
                },
        ) {
            // the arrow button
            Canvas(
                Modifier
                    .offset(x = (rowW - ROW_H).dp)
                    .size(ROW_H.dp)
                    .graphicsLayer {
                        val k = VisorBootMotion.clamp01(ctaK(controller.state(now())) * 2.5).toFloat()
                        alpha = 1 - k
                        val sc = (1 - 0.1f * k) * nextPress.value
                        scaleX = sc
                        scaleY = sc
                    },
            ) {
                val d = density
                drawCircle(Color(0x47EEF0F2), radius = size.minDimension / 2 - d / 3f, style = Stroke(2f / 3f * d))
                drawArrow(this, Offset(size.width / 2 - 9 * d, size.height / 2 - 9 * d), 18f * d, INK, 1.6f * d)
            }
            // dots and the button capsule
            Canvas(Modifier.fillMaxSize()) {
                val d = density
                val s = controller.state(now())
                val g = ctaK(s)
                val fade = 1 - VisorBootMotion.clamp01(g * 3)
                var x = 0f
                for (i in 0 until 4) {
                    val k = dotK(i, s).toFloat()
                    val dw = DOT + DOT_STRETCH * k
                    drawRoundRect(
                        INK.copy(alpha = ((0.28f + 0.72f * k) * fade.toFloat()).coerceIn(0f, 1f)),
                        topLeft = Offset(x * d, (ROW_H - DOT) / 2 * d),
                        size = Size(dw * d, DOT * d),
                        cornerRadius = CornerRadius(DOT / 2 * d),
                    )
                    x += dw + DOT_GAP
                }
                if (g > 0.002) {
                    val mm = VisorBootMotion.clamp01(s.m)
                    val anchor = VisorBootMotion.filmCtaAnchor(s.t) * (1 - mm) + 3 * mm
                    val left0 = dotLeft(anchor.roundToInt(), s).toDouble()
                    val bh = VisorBootMotion.lerp(DOT.toDouble(), ROW_H.toDouble(), g).toFloat()
                    val bw = VisorBootMotion.lerp((DOT + DOT_STRETCH).toDouble(), rowW.toDouble(), g).toFloat()
                    val bx = VisorBootMotion.lerp(left0, 0.0, min(1.0, g)).toFloat()
                    val sc = startPress.value
                    scale(sc, sc, Offset(size.width / 2, size.height / 2)) {
                        drawRoundRect(
                            INK,
                            topLeft = Offset(bx * d, (ROW_H - bh) / 2 * d),
                            size = Size(bw * d, bh * d),
                            cornerRadius = CornerRadius(bh / 2 * d),
                        )
                    }
                }
            }
            // Get Started's label
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val g = ctaK(controller.state(now()))
                        val k = VisorBootMotion.seg(g, 0.6, 1.0).toFloat()
                        alpha = k
                        translationY = (1 - k) * 6 * density
                        scaleX = startPress.value
                        scaleY = startPress.value
                    },
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Get Started", style = TextStyle(color = BG_EDGE, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.size(10.dp))
                Canvas(Modifier.size(16.dp)) {
                    drawArrow(this, Offset.Zero, size.width, BG_EDGE, 1.8f * density * 16f / 18f)
                }
            }
        }
    }
}

/** React Native's arrow: M2 9h13M10 3.5 15.5 9 10 14.5 in an 18 x 18 box. */
private fun drawArrow(scope: DrawScope, origin: Offset, box: Float, color: Color, stroke: Float) {
    val k = box / 18f
    val p = Path().apply {
        moveTo(origin.x + 2 * k, origin.y + 9 * k)
        lineTo(origin.x + 15 * k, origin.y + 9 * k)
        moveTo(origin.x + 10 * k, origin.y + 3.5f * k)
        lineTo(origin.x + 15.5f * k, origin.y + 9 * k)
        lineTo(origin.x + 10 * k, origin.y + 14.5f * k)
    }
    scope.drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
