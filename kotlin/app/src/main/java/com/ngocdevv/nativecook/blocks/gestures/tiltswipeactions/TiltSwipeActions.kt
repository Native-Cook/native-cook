package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The flat app drawn through one affine matrix, as TiltSwipeActions.tsx does with `matrix`:
 * screen = M * flat + t. Compose layers have no skew, so M is split (SVD) into a rotation, a
 * scale and a rotation, which two nested layers apply; touches still map back through them.
 */
private object TiltPlane {
    private val a = TiltView.scale * TiltView.u[0]
    private val b = TiltView.scale * TiltView.u[1]
    private val c = TiltView.scale * TiltView.v[0]
    private val d = TiltView.scale * TiltView.v[1]
    val translateX = TiltView.scale * (TiltView.p0[0] - TiltView.cropX - FLAT_HEIGHT * TiltView.v[0])
    val translateY = TiltView.scale * (TiltView.p0[1] - FLAT_HEIGHT * TiltView.v[1])

    // M = [[a, c], [b, d]] = Rot(outer) * diag(scaleX, scaleY) * Rot(inner)
    val scaleX: Float
    val scaleY: Float
    val outerDegrees: Float
    val innerDegrees: Float

    init {
        val e = (a + d) / 2f
        val f = (a - d) / 2f
        val g = (b + c) / 2f
        val h = (b - c) / 2f
        val q = hypot(e, h)
        val r = hypot(f, g)
        scaleX = q + r
        scaleY = q - r
        val a1 = atan2(g, f)
        val a2 = atan2(h, e)
        innerDegrees = Math.toDegrees(((a2 - a1) / 2f).toDouble()).toFloat()
        outerDegrees = Math.toDegrees(((a2 + a1) / 2f).toDouble()).toFloat()
    }
}

/**
 * "Latest spending": swipe a row left to flag or delete it; it leans with your finger.
 * With [autoPlay] a scripted finger shows the effect and a real touch takes over.
 */
@Composable
fun TiltSwipeActions(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val screen = remember(autoPlay, reduceMotion, view) {
        TiltScreen(autoPlay, reduceMotion, scope) { kind -> performHaptic(view, kind) }
    }
    val demo = remember(screen) { TiltDemoScript(screen, autoPlay).also { screen.demo = it } }
    DisposableEffect(screen) { onDispose { screen.dispose() } }

    // One frame loop: the script, then every running animation.
    LaunchedEffect(screen, demo) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) {
                    0.016f
                } else {
                    ((now - previous) / 1_000_000_000f).coerceAtMost(MAX_FRAME_DELTA_SECONDS)
                }
                previous = now
                demo.tick(dt)
                screen.clock.tick(dt)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TiltColor.gradient[0])
            .background(Brush.linearGradient(TiltColor.desk))
            // Any real touch stops the autoplay (onTouchStart / onTouchEnd of the root view).
            .pointerInput(demo) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            if (change.changedToDownIgnoreConsumed()) {
                                demo.touchStart()
                            } else if (change.changedToUpIgnoreConsumed()) {
                                demo.touchEnd()
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = TiltPlane.scaleX
                    scaleY = TiltPlane.scaleY
                    rotationZ = TiltPlane.outerDegrees
                    translationX = TiltPlane.translateX * density
                    translationY = TiltPlane.translateY * density
                }
                .size(PLANE_W.dp, PLANE_H.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    rotationZ = TiltPlane.innerDegrees
                    clip = true
                    shape = RoundedCornerShape(TiltView.screenRadius.dp)
                }
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            TiltColor.gradientStops[0] to TiltColor.gradient[0],
                            TiltColor.gradientStops[1] to TiltColor.gradient[1],
                            TiltColor.gradientStops[2] to TiltColor.gradient[2],
                            TiltColor.gradientStops[3] to TiltColor.gradient[3],
                            TiltColor.gradientStops[4] to TiltColor.gradient[4],
                            TiltColor.gradientStops[5] to TiltColor.gradient[5],
                            TiltColor.gradientStops[6] to TiltColor.gradient[6],
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                },
        ) {
            PlaneContent(screen, demo, reduceMotion)
        }
    }
}

/** Everything inside the plane, in the flat app's coordinates. */
@Composable
private fun PlaneContent(screen: TiltScreen, demo: TiltDemoScript, reduceMotion: Boolean) {
    val spendingShown = screen.tab == 2
    // Overview (the cards) and Spending (the list): the source cross-fades and slides the content.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - screen.tabShown.value
                translationY = (screen.tabShown.value * -TiltTiming.tabSlide).dp.toPx()
            },
    ) { CardsView(screen) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = screen.tabShown.value * (
                    1f - TiltDetail.menuFade * screen.menuProgress.value.coerceAtMost(1f)
                    )
                translationY = (
                    (1f - screen.tabShown.value) * TiltTiming.tabSlide -
                        TiltDetail.menuLift * screen.menuProgress.value
                    ).dp.toPx()
            },
    ) {
        BalanceCard(screen)
        SectionTitle()
        Column(
            Modifier
                .at(0f, TiltFlat.rowsTop)
                .width(FLAT_WIDTH.dp)
                .height((TiltFlat.Panel.top - 8f - TiltFlat.rowsTop).dp)
                .clipToBounds(),
        ) {
            for (row in screen.rows) {
                key(row.item.key) {
                    TiltSwipeRow(
                        screen = screen,
                        row = row,
                        flagged = row.item.key in screen.flagged,
                        interactive = spendingShown,
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }
    }
    UndoPill(screen, reduceMotion)
    TabPanel(screen, reduceMotion)
    if (screen.autoPlay) Ghost(demo)
}

@Composable
private fun SectionTitle() {
    TiltLabel(
        text = "Latest spending",
        style = TiltType.section,
        color = TiltColor.title,
        modifier = Modifier
            .at(TiltFlat.Section.left, TiltFlat.Section.centreY - TiltType.section.lineHeight / 2f)
            .semantics { heading() },
    )
}

/** The demo's finger. */
@Composable
private fun Ghost(demo: TiltDemoScript) {
    Box(
        modifier = Modifier
            .size(TiltLayout.finger.dp)
            .graphicsLayer {
                alpha = demo.ghostO.value
                translationX = (demo.ghostX.value - TiltLayout.finger / 2f).dp.toPx()
                translationY = (demo.ghostY.value - TiltLayout.finger / 2f).dp.toPx()
                val scale = 1f - TiltDetail.fingerPressScale * demo.ghostPress.value
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawCircle(TiltColor.finger)
                val hairline = 1.dp.toPx()
                drawCircle(
                    TiltColor.fingerEdge,
                    radius = size.minDimension / 2f - hairline / 2f,
                    style = Stroke(hairline),
                )
            },
    )
}

/** swipe-haptics.ts: light, discrete moments only. */
private fun performHaptic(view: View, kind: SwipeHaptic) {
    val constant = when (kind) {
        SwipeHaptic.Open -> HapticFeedbackConstants.CLOCK_TICK
        SwipeHaptic.Arm -> HapticFeedbackConstants.CONTEXT_CLICK
        SwipeHaptic.Disarm -> HapticFeedbackConstants.CLOCK_TICK
        SwipeHaptic.Commit -> HapticFeedbackConstants.LONG_PRESS
        SwipeHaptic.Undo ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        SwipeHaptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(constant)
}
