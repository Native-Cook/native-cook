package com.ngocdevv.nativecook.blocks.buttons.rockerlightswitch

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The film's period (s), as in switchMotion.ts. */
const val LOOP = SwitchMotion.LOOP

private const val GUTTER = 20f

/** The panel's height above the bottom safe area, and its top corners (it overlaps the stage by as much). */
private const val PANEL_H = 252f
private const val PANEL_R = 28f

/** The hit area: the plate's outline on screen, grown by this much. */
private const val HIT_PAD = 18f
private const val DOT = 46f

/** The halo reaches this many globe radii. */
private const val HALO = 4.2

private fun rgb(hex: Int, a: Float = 1f): Color = Color(hex or (0xFF shl 24)).copy(alpha = a)

/** React Native's type: a line box of `line`, the glyphs centred in it, no font padding. */
private fun type(size: Int, line: Int, color: Color, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, tabular: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * A smart-home room screen told as an 8-second film of a wall at dusk with a real 3D rocker switch and a brass
 * sconce. A finger presses the top half of the rocker: it follows the press, tips past the middle and snaps home on a
 * spring with a click, and 120 ms later the filament warms up: the globe glows, the wall takes the warm pool of light
 * and the plate throws its shadow. The camera eases a few degrees round the wall while the slider dims the light and
 * brings it back, then the bottom half is pressed and the room goes back to dusk.
 *
 * Ported from the React Native block (react-native-filament + Skia): the same switch.glb and dusk light in Filament
 * (RockerLightSwitchScene), the same film (RockerLightSwitchMotion), the Skia veil and halo on a Canvas, a Material 3
 * slider for the brightness.
 * Touch: press either half of the rocker, or drag it up and down to feel the tipping point; move the slider for the
 * brightness. 2 s after the last touch the light goes off and the film restarts. Reduced motion: the light on, no
 * film; presses still switch, with a short fade.
 */
@Composable
fun RockerLightSwitch(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val controller = remember(autoPlay, reduced) { RockerLightSwitchController(autoPlay, reduced) }
    val view = LocalView.current

    // One clock for the Compose layers: Choreographer's frame time, read only in draw lambdas.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    var lit by remember(controller) { mutableStateOf(controller.lit) }
    var sliderValue by remember(controller) { mutableFloatStateOf(1f) }
    LaunchedEffect(controller) {
        var clicks = controller.clicks
        while (true) {
            withFrameNanos { nanos ->
                controller.frame(nanos / 1e6)
                frameNanos = nanos
                if (controller.clicks != clicks) {
                    clicks = controller.clicks
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                lit = controller.lit
                // the film moves the slider in steps of 2 %; a finger's own drag is left alone
                if (!controller.sliding) sliderValue = (controller.brightNow * 50).roundToInt() * 2 / 100f
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(rgb(SwitchMotion.DUSK))) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val top = with(density) { insets.getTop(this).toDp().value }
        val bottom = with(density) { insets.getBottom(this).toDp().value }
        val w = maxWidth.value
        val h = maxHeight.value

        // the 3D stage is the window above the sheet (it runs a little way under the sheet's round corners)
        val panelH = PANEL_H + bottom
        val stageH = max(160f, h - panelH + PANEL_R)

        // the plate on screen with the camera at rest: the touch target
        val plate = remember(w, stageH) {
            val c = DoubleArray(6)
            SwitchMotion.cameraAt(0.0, 0.0, c)
            val o = DoubleArray(3)
            val p = SwitchMotion.ROCKER_PIVOT
            SwitchMotion.project(c, w.toDouble(), stageH.toDouble(), p[0], p[1], SwitchMotion.ROCKER_FACE_Z + p[2], o)
            floatArrayOf(o[0].toFloat(), o[1].toFloat(), (SwitchMotion.PLATE_HALF_W * 2 * o[2]).toFloat(), (SwitchMotion.PLATE_HALF_H * 2 * o[2]).toFloat())
        }
        // the dusk falls away from the lamp: clear round the globe (at rest), deepest in the far corner
        val lamp = remember(w, stageH) {
            val c = DoubleArray(6)
            SwitchMotion.cameraAt(0.0, 0.0, c)
            val o = DoubleArray(3)
            val g = SwitchMotion.GLOBE
            SwitchMotion.project(c, w.toDouble(), stageH.toDouble(), g[0], g[1], g[2], o)
            Offset(o[0].toFloat(), o[1].toFloat())
        }

        Box(Modifier.width(w.dp).height(stageH.dp).clipToBounds()) {
            RockerLightSwitchStage(controller, Modifier.fillMaxSize())

            // Over the 3D view: the dusk (a blue veil with darker corners that lifts as the light comes on), the
            // globe's halo in the room's air and the film's finger, pinned with the stage's own camera maths.
            val cam = remember { DoubleArray(6) }
            val pt = remember { DoubleArray(3) }
            val dotShadow = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
            Canvas(Modifier.fillMaxSize()) {
                controller.frame(frameNanos / 1e6)
                val d = density.density
                val k = controller.light.toFloat()
                val dusk = 1 - 0.92f * k
                val navy = Color(8, 14, 34)
                drawRect(rgb(SwitchMotion.DUSK), alpha = 0.3f * dusk)
                val far = hypot(max(lamp.x, w - lamp.x), max(lamp.y, stageH - lamp.y)) * d
                drawRect(
                    Brush.radialGradient(
                        0.08f to navy.copy(alpha = 0f),
                        0.4f to navy.copy(alpha = 0.16f),
                        0.75f to navy.copy(alpha = 0.4f),
                        1f to navy.copy(alpha = 0.6f),
                        center = lamp * d,
                        radius = far,
                    ),
                    alpha = dusk,
                )
                SwitchMotion.cameraAt(controller.orbitNow, controller.t, cam)
                if (k > 0.002f) {
                    val g = SwitchMotion.GLOBE
                    SwitchMotion.project(cam, w.toDouble(), stageH.toDouble(), g[0], g[1], g[2], pt)
                    val c = Offset(pt[0].toFloat() * d, pt[1].toFloat() * d)
                    val r = (SwitchMotion.GLOBE_R * pt[2] * HALO).toFloat() * d
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(255, 214, 150, 128),
                            0.3f to Color(255, 196, 120, 51),
                            0.62f to Color(255, 188, 110, 15),
                            1f to Color(255, 188, 110, 0),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                        alpha = 0.55f * k,
                    )
                }
                // a little shade under the status bar and the app's name, so both read on the lit wall
                val shade = Color(12, 14, 22)
                val shadeH = (top + 64) * d
                drawRect(
                    Brush.verticalGradient(
                        0f to shade.copy(alpha = 0.42f),
                        0.6f to shade.copy(alpha = 0.14f),
                        1f to shade.copy(alpha = 0f),
                        startY = 0f,
                        endY = shadeH,
                    ),
                    size = Size(size.width, shadeH),
                )
                // the film's finger: a soft dot on the half being pressed
                val ghost = controller.ghost
                val a = (ghost[0] * 0.9).toFloat()
                if (a > 0.002f) {
                    val p = SwitchMotion.ROCKER_PIVOT
                    SwitchMotion.project(
                        cam, w.toDouble(), stageH.toDouble(),
                        p[0], p[1] + ghost[1] * SwitchMotion.ROCKER_HALF_H * 0.55, p[2] + SwitchMotion.ROCKER_FACE_Z, pt,
                    )
                    val c = Offset(pt[0].toFloat() * d, pt[1].toFloat() * d)
                    val r = DOT / 2 * (1 - 0.16f * ghost[2].toFloat()) * d
                    drawIntoCanvas {
                        // its shadow: 8 pt of blur, 3 pt down, through the see-through dot
                        if (dotShadow.maskFilter == null) dotShadow.maskFilter = BlurMaskFilter(13f * d, BlurMaskFilter.Blur.NORMAL)
                        dotShadow.color = android.graphics.Color.argb((0.25f * 0.4f * a * 255).roundToInt(), 0, 0, 0)
                        it.nativeCanvas.drawCircle(c.x, c.y + 3 * d, r, dotShadow)
                    }
                    drawCircle(Color.White, radius = r, center = c, alpha = 0.34f * a)
                    drawCircle(Color.White, radius = r - 0.75f * d, center = c, alpha = 0.7f * a, style = Stroke(1.5f * d))
                }
            }
        }

        // The app's name.
        Row(
            modifier = Modifier.offset(y = (top + 10).dp).fillMaxWidth().padding(horizontal = GUTTER.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(SwitchMotion.APP, style = type(17, 22, Color(255, 252, 246).copy(alpha = 0.96f), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
            Text("Evening", style = type(15, 20, Color(255, 252, 246).copy(alpha = 0.72f)), modifier = Modifier.alignByBaseline())
        }

        // The rocker: press a half, or drag it.
        Box(
            modifier = Modifier
                .offset(x = (plate[0] - plate[2] / 2 - HIT_PAD).dp, y = (plate[1] - plate[3] / 2 - HIT_PAD).dp)
                .size((plate[2] + HIT_PAD * 2).dp, (plate[3] + HIT_PAD * 2).dp)
                .semantics { contentDescription = "Light switch. Press the top half to turn the light on, the bottom half to turn it off, or drag to tip it" }
                .pointerInput(controller, plate[3]) {
                    val d = this.density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        controller.pressBegan(top = down.position.y / d < plate[3] / 2 + HIT_PAD)
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dy += change.positionChange().y
                            controller.pressMoved((dy / d).toDouble())
                            change.consume()
                            if (!change.pressed) {
                                controller.pressEnded((tracker.calculateVelocity().y / d).toDouble())
                                break
                            }
                        }
                        controller.pressFinalized()
                    }
                },
        )

        // The room's sheet: its name, how many lights are on, and the sconce's row with the brightness slider.
        val inner = min(w, 560f) - GUTTER * 2
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(panelH.dp)
                // the sheet's long soft shadow up the wall
                .cssBoxShadow(Color(0x0B, 0x0D, 0x14).copy(alpha = 0.28f), offsetY = (-8).dp, blur = 48.dp, radius = PANEL_R.dp)
                .clip(RoundedCornerShape(topStart = PANEL_R.dp, topEnd = PANEL_R.dp))
                .background(rgb(SwitchMotion.PANEL))
                .drawWithContent {
                    drawContent()
                    // the lit top edge: a hairline round the top corners
                    val r = PANEL_R.dp.toPx()
                    clipRect(bottom = r) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(size.width - 1f, r * 3),
                            cornerRadius = CornerRadius(r - 0.5f),
                            style = Stroke(1f),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.width(inner.dp).padding(top = 22.dp)) {
                val count = SwitchMotion.OTHER_LIGHTS_ON + if (lit) 1 else 0
                Text(SwitchMotion.ROOM, style = type(28, 34, rgb(SwitchMotion.INK), FontWeight.Bold, tracking = -0.4f))
                Text("$count lights on", style = type(15, 20, rgb(SwitchMotion.MUTE), tabular = true), modifier = Modifier.padding(top = 2.dp))
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        // the card's tight contact shadow
                        .cssBoxShadow(Color(0x3A, 0x2E, 0x1E).copy(alpha = 0.08f), offsetY = 3.dp, blur = 20.dp, radius = 20.dp)
                        .background(rgb(SwitchMotion.CARD), RoundedCornerShape(20.dp))
                        .border(with(density) { 1f.toDp() }, Color(29, 27, 24).copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(SwitchMotion.LIGHT_NAME, style = type(17, 22, rgb(SwitchMotion.INK), FontWeight.SemiBold), modifier = Modifier.alignByBaseline())
                        Text(
                            if (lit) "On" else "Off",
                            style = type(15, 20, if (lit) Color(0xB3, 0x6F, 0x0E) else rgb(SwitchMotion.MUTE), FontWeight.Medium),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Row(Modifier.padding(top = 12.dp, bottom = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Brightness", style = type(15, 20, rgb(SwitchMotion.MUTE)), modifier = Modifier.alignByBaseline())
                        Text(
                            "${(sliderValue * 100).roundToInt()}%",
                            style = type(15, 20, rgb(SwitchMotion.INK), FontWeight.Medium, tabular = true),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    // The brightness slider: the system's. The film moves it by state; a finger uses it the native way.
                    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { v ->
                                controller.setSliding(true)
                                sliderValue = v
                                controller.setBrightness(v.toDouble())
                            },
                            onValueChangeFinished = { controller.setSliding(false) },
                            valueRange = SwitchMotion.BRIGHT_MIN.toFloat()..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = rgb(SwitchMotion.AMBER),
                                activeTrackColor = rgb(SwitchMotion.AMBER),
                                inactiveTrackColor = Color(120, 116, 108).copy(alpha = 0.22f),
                            ),
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).semantics { contentDescription = "Brightness" },
                        )
                    }
                }
            }
        }
    }
}
