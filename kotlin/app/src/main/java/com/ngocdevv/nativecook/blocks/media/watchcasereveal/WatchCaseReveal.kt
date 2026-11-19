package com.ngocdevv.nativecook.blocks.media.watchcasereveal

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Watch Case Reveal: a walnut watch case on a paper sweep opens on its hinge, the pillow rises and tips the watch to
// the lens, the watch turns to catch the light while the camera pushes in to the dial and the dial picker tries every
// dial, then everything closes again. A port of WatchCaseReveal.tsx: the 3D stage is Filament + gltfio with the block's
// own watch.glb and studio IBL (WatchCaseRevealStage.kt), the studio light, the case's soft floor shadow and the
// crystal's glint are Compose canvases, and the controls are the React Native block's Android segments and button.
// One controller (WcrController) is read by the stage and the layers for the same vsync, so they never disagree.

private val INK = Color(WcrScene.INK)
private val MUTE = Color(WcrScene.MUTE)
private val BG_TOP = Color(WcrScene.BG_TOP)
private val BG_BOTTOM = Color(WcrScene.BG_BOTTOM)

/** The product panel's height (title, specs, picker, button) and the header row's. */
private const val PANEL_H = 236.0
private const val HEADER_H = 44.0
/** The stage window fades into the paper over its last FADE dp (and a short TOP_FADE at the top). */
private const val FADE = 48.0
private const val TOP_FADE = 16.0
private const val GUTTER = 20.0
private const val RIM = 28
private const val DIAL_R = 0.0162

private fun paperAt(y: Double, h: Double, a: Float): Color {
    val k = (y / h).coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = BG_TOP.red + (BG_BOTTOM.red - BG_TOP.red) * k,
        green = BG_TOP.green + (BG_BOTTOM.green - BG_TOP.green) * k,
        blue = BG_TOP.blue + (BG_BOTTOM.blue - BG_TOP.blue) * k,
        alpha = a,
    )
}

private fun haptic(view: View, kind: Int) {
    val c = when (kind) {
        0 -> HapticFeedbackConstants.VIRTUAL_KEY // light impact
        1 -> HapticFeedbackConstants.CLOCK_TICK // soft impact
        2 -> HapticFeedbackConstants.CLOCK_TICK // selection
        else -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(c)
}

@Composable
fun WatchCaseReveal(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
    val controller = remember(autoPlay, reduced) { WcrController(autoPlay, reduced) }
    /** The vsync time (ns) of the frame on screen: the one clock every layer reads in its draw lambda. */
    var frameNs by remember { mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(controller) {
        var lastOpen = Double.NaN
        var lastDetent = Int.MIN_VALUE
        var lastReserved = false
        while (true) {
            val ns = withFrameNanos { it }
            frameNs = ns
            val p = controller.frame(ns / 1e9)
            if (!reduced) {
                // the clasp, the lid at rest, the pillow at the top, the lid landing shut; a detent every 12 deg while
                // a finger turns the watch; the film's press of the reserve button
                val o = p.open
                if (!lastOpen.isNaN()) {
                    if (lastOpen < 0.015 && o >= 0.015) haptic(view, 0)
                    if (lastOpen < 0.97 && o >= 0.97) haptic(view, 1)
                    if (lastOpen < 1.95 && o >= 1.95) haptic(view, 0)
                    if (lastOpen > 0.02 && o <= 0.02) haptic(view, 1)
                }
                lastOpen = o
                val detent = floor(p.yaw / WcrController.DETENT).toInt()
                if (lastDetent != Int.MIN_VALUE && detent != lastDetent && controller.isTurning) haptic(view, 2)
                lastDetent = detent
                if (p.filmPlaying && p.reserved && !lastReserved) haptic(view, 3)
                lastReserved = p.reserved
            }
        }
    }

    val pickerDial by remember { derivedStateOf { controller.frame(frameNs / 1e9).pickerDial } }
    val reserved by remember { derivedStateOf { controller.frame(frameNs / 1e9).reserved } }

    val localDensity = LocalDensity.current
    val insetTop = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }.value.toDouble()
    val insetBottom = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }.value.toDouble()

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG_TOP, BG_BOTTOM)))) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val panelTop = height - (insetBottom + 12 + PANEL_H)
        val stageTop = insetTop + HEADER_H
        val stageH = max(120.0, panelTop - 8 - stageTop)
        val stageBottom = stageTop + stageH
        fun d(v: Double): Dp = v.dp

        // the stage window: light, the 3D case, the glint, and the edges fading into the paper
        Box(Modifier.offset(y = d(stageTop)).width(maxWidth).height(d(stageH)).clipToBounds()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width.toDouble()
                val h = size.height.toDouble()
                val p = controller.frame(frameNs / 1e9)
                val c = Offset((w * 0.4).toFloat(), (h * 0.26).toFloat())
                val r = (w * 0.9).toFloat()
                drawCircle(
                    Brush.radialGradient(listOf(Color(1f, 253 / 255f, 248 / 255f, 0.8f), Color(1f, 253 / 255f, 248 / 255f, 0f)), c, r),
                    r, c,
                )
                val cam = DoubleArray(6)
                WcrMotion.cameraAt(p.open, p.macro, p.t, cam)
                val b = DoubleArray(9)
                WcrMotion.cameraBasis(cam, w, b)
                val o = DoubleArray(3)
                if (WcrMotion.project(cam, b, w, h, 0.0, 0.0, 0.0, o)) {
                    val s = WcrScene.CASE_W * b[8] / o[2]
                    translate(o[0].toFloat(), (o[1] + s * 0.05).toFloat()) {
                        scale((s * 1.05).toFloat(), (s * 0.36).toFloat(), Offset.Zero) {
                            val shade = Color(52 / 255f, 40 / 255f, 30 / 255f)
                            drawCircle(
                                Brush.radialGradient(
                                    0f to shade.copy(alpha = 0.28f), 0.5f to shade.copy(alpha = 0.1f), 1f to shade.copy(alpha = 0f),
                                    center = Offset.Zero, radius = 1f,
                                ),
                                1f, Offset.Zero,
                            )
                        }
                    }
                }
            }
            WatchStage3D(controller, onReady = {}, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) { drawGlint(controller.frame(frameNs / 1e9)) }
            Box(
                Modifier.fillMaxWidth().height(d(TOP_FADE)).background(
                    Brush.verticalGradient(listOf(paperAt(stageTop, height, 1f), paperAt(stageTop + TOP_FADE, height, 0f))),
                ),
            )
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(d(FADE)).background(
                    Brush.verticalGradient(listOf(paperAt(stageBottom - FADE, height, 0f), paperAt(stageBottom, height, 1f))),
                ),
            )
        }

        // the gesture area over the stage: drag up to open, down to close, sideways to turn; tap to open
        Box(
            Modifier
                .offset(y = d(stageTop))
                .width(maxWidth)
                .height(d(stageH))
                .semantics { contentDescription = "Watch case. Drag up to open the case, sideways to turn the watch" }
                .pointerInput(controller) {
                    val slop = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var panning = false
                        val t0 = down.uptimeMillis
                        var upTime = t0
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            val delta = ch.position - down.position
                            if (!panning && delta.getDistance() > slop) {
                                panning = true
                                controller.dragBegan(System.nanoTime() / 1e9)
                            }
                            if (panning) {
                                controller.dragChanged(System.nanoTime() / 1e9, (delta.x / density).toDouble(), (delta.y / density).toDouble())
                                ch.consume()
                            }
                            if (!ch.pressed) {
                                upTime = ch.uptimeMillis
                                break
                            }
                        }
                        val now = System.nanoTime() / 1e9
                        if (panning) {
                            val v = tracker.calculateVelocity()
                            controller.dragEnded(now, (v.x / density).toDouble(), (v.y / density).toDouble())
                        } else if (upTime - t0 <= 300) {
                            controller.tap(now)
                        }
                    }
                },
        )

        // the house name
        Row(
            Modifier.offset(x = d(GUTTER), y = d(insetTop + 8)).width(d(width - 2 * GUTTER)),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                WcrScene.BRAND,
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = INK, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            )
            Text("New arrival", modifier = Modifier.alignByBaseline(), style = TextStyle(color = MUTE, fontSize = 13.sp))
        }

        // the product panel hangs from the bottom edge
        val inner = min(width, 560.0) - GUTTER * 2
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = d(GUTTER), bottom = d(insetBottom + 12))
                .width(d(inner)),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    WcrScene.MODEL,
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1,
                    style = TextStyle(color = INK, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                )
                Text(
                    WcrScene.PRICE,
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1,
                    style = TextStyle(color = INK, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
            Text(WcrScene.LINE, modifier = Modifier.padding(top = 2.dp), style = TextStyle(color = MUTE, fontSize = 15.sp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp)) {
                for (i in WcrScene.SPECS.indices) {
                    Spec(i, { controller.frame(frameNs / 1e9).open }, Modifier.weight(1f))
                }
            }
            DialSegments(pickerDial) { i ->
                haptic(view, 2)
                controller.pickDial(i, System.nanoTime() / 1e9)
            }
            Box(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(INK, RoundedCornerShape(26.dp))
                    .semantics { role = Role.Button }
                    .clickable {
                        haptic(view, 3)
                        controller.reserve(System.nanoTime() / 1e9)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (reserved) "Reserved" else "Reserve in a boutique",
                    style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** One spec: its number counts up as the pillow rises (only the number's text recomposes, and only when it changes). */
@Composable
private fun Spec(i: Int, open: () -> Double, modifier: Modifier) {
    val s = WcrScene.SPECS[i]
    val k by remember { derivedStateOf { WcrMotion.clamp01((WcrMotion.specsOf(open()) - i * 0.08) / 0.84) } }
    val value by remember { derivedStateOf { (s.value * k).roundToInt() } }
    Column(modifier.graphicsLayer { alpha = (0.35 + 0.65 * WcrMotion.clamp01((WcrMotion.specsOf(open()) - i * 0.08) / 0.84)).toFloat() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$value",
                modifier = Modifier.alignByBaseline().width((s.value.toString().length * 17 + 6).dp),
                maxLines = 1,
                style = TextStyle(color = INK, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
            Text(
                s.unit,
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
        Text(s.label, modifier = Modifier.padding(top = 1.dp), maxLines = 1, style = TextStyle(color = MUTE, fontSize = 13.sp))
    }
}

/** The React Native block's Android dial picker: three segments in a soft grey track, the chosen one white. */
@Composable
private fun DialSegments(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(118, 118, 128, (0.14f * 255).roundToInt()), RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        for (i in WcrScene.DIALS.indices) {
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                            .background(Color.White, RoundedCornerShape(7.dp))
                        else Modifier,
                    )
                    .semantics { this.selected = on; role = Role.Button }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    WcrScene.DIALS[i].name,
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                )
            }
        }
    }
}

/**
 * The sapphire crystal catching the softbox (WatchLight.tsx's WatchGlint): a soft bright band, clipped to the dial's
 * projected outline, slides across the glass as the watch turns.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlint(p: WcrPose) {
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val o = p.open
    val lift = WcrMotion.liftOf(o)
    val k = WcrMotion.clamp01((lift - 0.2) / 0.8)
    if (k <= 0.001) return
    val cam = DoubleArray(6)
    WcrMotion.cameraAt(o, p.macro, p.t, cam)
    val b = DoubleArray(9)
    WcrMotion.cameraBasis(cam, w, b)
    val tilt = WcrScene.TILT * WcrMotion.tipOf(o)
    val yaw = p.yaw
    val y = WcrScene.CUSHION_Y + WcrScene.LIFT * lift
    val ct = cos(tilt)
    val st = sin(tilt)
    val cy = cos(yaw)
    val sy = sin(yaw)
    val pt = DoubleArray(3)
    val path = Path()
    var minX = 1e9
    var maxX = -1e9
    var minY = 1e9
    var maxY = -1e9
    for (i in 0 until RIM) {
        val a = 2 * PI * i / RIM
        val qx = WcrScene.DIAL_C[0] + cos(a) * DIAL_R
        val qy = WcrScene.DIAL_C[1]
        val qz = WcrScene.DIAL_C[2] + sin(a) * DIAL_R
        // cushion space to world: tipped by tilt about x, turned by yaw about y, lifted
        val y1 = qy * ct - qz * st
        val z1 = qy * st + qz * ct
        val wx = qx * cy + z1 * sy
        val wz = -qx * sy + z1 * cy
        if (!WcrMotion.project(cam, b, w, h, wx, y1 + y, wz, pt)) continue
        if (i == 0) path.moveTo(pt[0].toFloat(), pt[1].toFloat()) else path.lineTo(pt[0].toFloat(), pt[1].toFloat())
        minX = min(minX, pt[0]); maxX = max(maxX, pt[0]); minY = min(minY, pt[1]); maxY = max(maxY, pt[1])
    }
    path.close()
    // the band's centre runs across the dial with the turn (and a little with the tip)
    val s = WcrMotion.clamp01(0.5 + sin(yaw * 2.4) * 0.75 + (WcrMotion.tipOf(o) - 1) * 0.3)
    val cx = minX + (maxX - minX) * s
    val cyy = minY + (maxY - minY) * (1 - s * 0.6)
    val r = (maxX - minX) * 0.35
    val brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0f), 0.38f to Color.White.copy(alpha = 0.18f), 0.5f to Color.White.copy(alpha = 0.55f),
        0.62f to Color.White.copy(alpha = 0.18f), 1f to Color.White.copy(alpha = 0f),
        start = Offset((cx - r).toFloat(), (cyy - r * 1.4).toFloat()),
        end = Offset((cx + r).toFloat(), (cyy + r * 1.4).toFloat()),
    )
    clipPath(path) {
        drawPath(path, brush, alpha = (0.5 * k).toFloat(), blendMode = BlendMode.Plus)
    }
}
