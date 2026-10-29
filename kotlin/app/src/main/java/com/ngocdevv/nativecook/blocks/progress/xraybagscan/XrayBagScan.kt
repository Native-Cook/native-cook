package com.ngocdevv.nativecook.blocks.progress.xraybagscan

import android.graphics.Canvas as NativeCanvas
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun hex(c: Int, a: Float = 1f) = Color(0xFF000000.toInt() or c).copy(alpha = a)

/**
 * The bag-drop step of an invented airline (Halcyon Air). A lilac carry-on rides the belt through a
 * scanner gantry; the gantry's window is a mask that shows the bag as its x-ray (cyan on navy,
 * contents visible) while the rest of the bag stays a photo. Light curtains glow on the window's
 * edges, a sweep passes while the bag dwells, detection boxes pop on the liquids, headphones and
 * laptop, and the card steps Ready to scan → Scanning… → Checking contents → Bag cleared. The bag
 * leaves on the right while the next one arrives at the rest spot, so the loop (LOOP = 9 s) is seamless.
 *
 * Drag the bag along the belt to scrub the scan; release and the belt carries on. "Scan again"
 * restarts the pass. Reduced motion: the cleared state, still.
 */
@Composable
fun XrayBagScan(autoPlay: Boolean = false) {
    val m = XrayBagScanMotion
    val art = XrayBagScanArt
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
    BlockSystemBars(darkIcons = true)

    val images = remember { art.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { XrayBagScanTouch() }
    val awake = remember { mutableStateOf(false) }
    val scrubbing = remember { mutableStateOf(false) }
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
            if (!autoPlay && !touch.touching && clock.doubleValue > touch.restsAfter) {
                awake.value = false
                break
            }
        }
    }
    // Reading `scrubbing` keeps the touch-driven frames redrawing even while the clock alone would not change them.
    val loopT: () -> Double = { if (reduced) 5.6 else touch.loopTime(clock.doubleValue, autoPlay) }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(hex(XrayBagScanArt.FLOOR))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val sy = top - 59
        val cardBottom = min(818 + sy, height - max(bottom, 12.0))
        val cardTop = cardBottom - 210
        val u: (Double) -> Dp = { (it * k).dp }
        val ts: (Double) -> TextUnit = { with(density) { (it * k).dp.toSp() } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A lilac suitcase passing through the bag scanner" },
        ) {
            val c = clock.doubleValue
            scrubbing.value
            val t = loopT()
            val d = if (reduced) m.LEG1 else touch.belt(c, autoPlay)
            val lean = if (reduced) 0.0 else touch.lean(c, t)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(size.width / 393f, size.width / 393f)
            drawScene(canvas, (size.width / k).toDouble(), height, sy, t, d, lean, touch.touching, images)
            canvas.restore()
        }

        // The scene answers the finger: drag the bag along the belt.
        Box(
            Modifier
                .offset(0.dp, u(252 + sy))
                .size(u(393.0), u(360.0))
                .clearAndSetSemantics {}
                .pointerInput(reduced, k) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (reduced) return@awaitEachGesture
                        val scale = size.width / 393f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var began = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = (change.position.x - down.position.x) / scale
                            if (!began && abs(dx) > 4) {
                                touch.begin(clock.doubleValue, autoPlay)
                                scrubbing.value = true
                                awake.value = true
                                began = true
                            }
                            if (began) {
                                val vx = tracker.calculateVelocity().x / scale
                                if (touch.move(dx.toDouble(), vx.toDouble())) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                clock.doubleValue += 1e-6
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        if (began) {
                            touch.end(clock.doubleValue)
                            scrubbing.value = false
                        }
                    }
                },
        )

        Header(top, u, ts, k)
        Card(cardTop, u, ts, k, loopT, clock, reduced) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!reduced) {
                touch.restart(clock.doubleValue)
                awake.value = true
                clock.doubleValue += 1e-6
            }
        }
    }
}

private fun drawScene(
    c: NativeCanvas, width: Double, height: Double, sy: Double, t: Double, d: Double, lean: Double,
    scrubbing: Boolean, images: XrayBagScanImages,
) {
    val m = XrayBagScanMotion
    val art = XrayBagScanArt
    art.drawBackground(c, width, height, sy)
    val window = art.windowRect(sy)
    val bagXs = doubleArrayOf(-1.0, 0.0, 1.0).map { m.REST_X + d - m.SPACING * it }
        .filter { abs(it - 196.5) < 196.5 + art.imageW / 2 + 12 }

    c.save()
    c.clipOutRect(window)
    art.drawBelt(c, width, d, sy)
    for (x in bagXs) art.drawBagShadow(c, x, sy)
    for (x in bagXs) art.drawBag(c, images.photo, x, lean, sy)
    c.restore()

    c.save()
    c.clipRect(window)
    art.drawWindowBack(c, sy)
    art.drawBeltXray(c, d, sy)
    for (x in bagXs) {
        if (x + art.imageW / 2 <= m.WIN_L - 8 || x - art.imageW / 2 >= m.WIN_R + 8) continue
        art.drawBag(c, images.glow, x, lean, sy, alpha = 0.35, additive = true)
        art.drawBag(c, images.xray, x, lean, sy)
    }
    val bagX = m.REST_X + d
    art.drawBoxes(c, bagX, lean, sy, m.boxes(t, bagX, scrubbing, art.itemOffsets), m.cleared(t))
    art.drawWindowFront(c, sy, m.sweepY(t), if (scrubbing) 0.0 else m.sweepAlpha(t))
    c.restore()

    val left = bagXs.maxOfOrNull { m.curtain(m.WIN_L, it) } ?: 0.35
    val right = bagXs.maxOfOrNull { m.curtain(m.WIN_R, it) } ?: 0.35
    art.drawGantry(c, sy, m.beacon(t), m.scanning(t), m.cleared(t), t)
    art.drawCurtains(c, sy, left, right)
}

@Composable
private fun Header(top: Double, u: (Double) -> Dp, ts: (Double) -> TextUnit, k: Float) {
    Row(
        Modifier
            .offset(u(20.0), u(top + 7))
            .height(u(30.0))
            .background(hex(0xFFF1DE, 0.8f), RoundedCornerShape(50))
            .padding(horizontal = u(12.0))
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Modifier.size(u(16.0), u(14.0)), k) { XrayBagScanArt.drawBird(it) }
        Spacer(Modifier.width(u(7.0)))
        Text("Halcyon Air", color = hex(XrayBagScanArt.INK), fontSize = ts(14.0), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
    Box(Modifier.offset(0.dp, u(top + 7)).width(u(373.0)), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier.height(u(30.0)).background(hex(XrayBagScanArt.INK, 0.08f), RoundedCornerShape(50)).padding(horizontal = u(12.0)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Step 2 of 3", color = hex(XrayBagScanArt.BROWN), fontSize = ts(13.0), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
    Text(
        "Bag check", color = hex(XrayBagScanArt.INK), fontSize = ts(34.0), fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
        modifier = Modifier.offset(u(20.0), u(top + 68 - 22)).semantics { heading() },
    )
    Text(
        "HA 418 · Lisbon → Oslo · Gate E7", color = hex(XrayBagScanArt.BROWN), fontSize = ts(15.0), fontWeight = FontWeight.Medium,
        maxLines = 1, softWrap = false, modifier = Modifier.offset(u(20.0), u(top + 102 - 10)),
    )
}

@Composable
private fun Card(
    top: Double,
    u: (Double) -> Dp,
    ts: (Double) -> TextUnit,
    k: Float,
    loopT: () -> Double,
    clock: androidx.compose.runtime.MutableDoubleState,
    reduced: Boolean,
    onAgain: () -> Unit,
) {
    val m = XrayBagScanMotion
    Box(
        Modifier
            .offset(u(16.0), u(top))
            .size(u(361.0), u(210.0))
            .shadow(u(12.0), RoundedCornerShape(u(28.0)), ambientColor = hex(0x2B1A0E, 0.5f), spotColor = hex(0x2B1A0E, 0.35f))
            .background(hex(XrayBagScanArt.CARD), RoundedCornerShape(u(28.0))),
    )
    Canvas(Modifier.offset(u(37.0), u(top + 21)).size(u(46.0)).clearAndSetSemantics {}) {
        clock.doubleValue
        val t = loopT()
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / 46f, size.width / 46f)
        XrayBagScanArt.drawStatusIcon(canvas, m.stage(t), m.spin(t), m.checkScale(t), m.checkStroke(t))
        canvas.restore()
    }

    // Status line: two texts cross-fade; only their index changes in composition, the fade in the layer.
    val base by remember { derivedStateOf { clock.doubleValue; m.stageIndex(m.stage(loopT())) } }
    val shown by remember { derivedStateOf { clock.doubleValue; val s = m.mod(m.stage(loopT()), 4.0); m.statuses[(m.stageIndex(s) + if (s - floor(s) < 0.5) 0 else 1) % 4] } }
    Box(
        Modifier.offset(u(94.0), u(top + 35 - 14)).size(u(250.0), u(28.0)).semantics { contentDescription = shown },
        contentAlignment = Alignment.CenterStart,
    ) {
        for (o in 0..1) {
            Text(
                m.statuses[(base + o) % 4], color = hex(XrayBagScanArt.INK), fontSize = ts(20.0), fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
                modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                    clock.doubleValue
                    val s = m.mod(m.stage(loopT()), 4.0)
                    val f = (s - floor(s)).toFloat()
                    alpha = if (o == 0) 1 - f else f
                    translationY = (if (o == 0) -8 * f else 8 * (1 - f)) * k * density
                },
            )
        }
    }
    Text(
        "Carry-on · 7.4 kg · Tag 418 207", color = hex(XrayBagScanArt.MUTED), fontSize = ts(14.0), maxLines = 1, softWrap = false,
        modifier = Modifier.offset(u(94.0), u(top + 58 - 9)),
    )

    val percent by remember { derivedStateOf { clock.doubleValue; (m.fill(loopT()) * 100).roundToInt() } }
    Canvas(
        Modifier.offset(u(36.0), u(top + 90)).size(u(321.0), u(8.0)).semantics { contentDescription = "Progress, $percent percent" },
    ) {
        clock.doubleValue
        val t = loopT()
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / 321f, size.width / 321f)
        XrayBagScanArt.drawBar(canvas, 321.0, m.fill(t), m.cleared(t))
        canvas.restore()
    }
    val labels = listOf("Scan", "Contents", "Cleared")
    for (i in 0 until 3) {
        val step by remember { derivedStateOf { clock.doubleValue; (m.clamp((m.fill(loopT()) - (i + 1) / 3.0 + 0.04) * 25) * 20).roundToInt() } }
        Box(Modifier.offset(u(36.0 + i * 109), u(top + 114 - 9)).width(u(107.0)).clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
            Text(
                labels[i], color = Color(0xFF000000.toInt() or XrayBagScanArt.mix(0xB8A08A, if (i == 2) XrayBagScanArt.GREEN else XrayBagScanArt.INK, step / 20.0)),
                fontSize = ts(13.0), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            )
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .offset(u(36.0), u(top + 142))
            .size(u(321.0), u(48.0))
            .graphicsLayer { val s = if (pressed) 0.97f else 1f; scaleX = s; scaleY = s }
            .background(hex(XrayBagScanArt.INK), RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onAgain)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Scan again" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Modifier.size(u(18.0)), k) { XrayBagScanArt.drawAgainGlyph(it, 0xFFFFF3E3.toInt()) }
        Spacer(Modifier.width(u(8.0)))
        Text("Scan again", color = hex(0xFFF3E3), fontSize = ts(16.0), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentWidth())
    }
}

/** A small icon drawn in units on a native canvas. */
@Composable
private fun Icon(modifier: Modifier, k: Float, draw: (NativeCanvas) -> Unit) {
    Canvas(modifier) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(k * density, k * density)
        draw(canvas)
        canvas.restore()
    }
}
