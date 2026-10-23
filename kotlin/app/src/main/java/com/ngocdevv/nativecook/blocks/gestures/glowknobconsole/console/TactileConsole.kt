// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import androidx.compose.foundation.Canvas
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SharedValue
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.enums.NumericTextAlignment
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.enums.TransitionDirection
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.records.TransitionShape
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.views.NumericText
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.PanGesture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.Pressability
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.gestureDetector
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.pressable
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Haptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val TOP = Specimen.top
private const val HEIGHT = Specimen.height

/** Native text inside the artwork uses the platform system face (`sans-serif`). */
private val systemFace = FontFamily.SansSerif

/** Tiny string table: English unless the device language is Vietnamese. */
class Strings(val vi: Boolean) {
    fun t(en: String, vi: String) = if (this.vi) vi else en
}

/** The specimen drawing (DeviceArt) scaled into its box. */
@Composable
fun ControlDrawing(control: TactileControl, scale: Float, fx: Effects, tilt: Shared<Tilt>, modifier: Modifier = Modifier) {
    Perf.recomposed("ControlDrawing")
    val painter = remember { DeviceArtPainter() }
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxSize().clipToBounds().clearAndSetSemantics {}) {
        Perf.section("DeviceArt:draw") { drawArt(control, tilt, painter, scale, density, fx) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArt(control: TactileControl, tilt: Shared<Tilt>, painter: DeviceArtPainter, scale: Float, density: Float, fx: Effects) {
    run {
        val state = ArtState.of(control, tilt.get())
        val s = scale * density
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.save()
            c.scale(s, s)
            c.translate(0f, TOP.toFloat())
            painter.draw(c, state, fx, baked = false, pxPerUnit = s)
            c.restore()
        }
    }
}

/** glyphs.tsx: reference icon silhouettes for the two in-artwork mode selectors. */
@Composable
fun ReferenceGlyph(mode: Int, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 26f
        scale(k, k, pivot = Offset.Zero) {
            if (mode == 0) {
                drawCircle(tint, radius = 6f, center = Offset(13f, 13f), style = Stroke(width = 1.5f))
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(
                        tint,
                        Offset((13 + sin(a) * 10).toFloat(), (13 + cos(a) * 10).toFloat()),
                        Offset((13 + sin(a) * 12).toFloat(), (13 + cos(a) * 12).toFloat()),
                        strokeWidth = 1.6f,
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                val path = Path().apply {
                    moveTo(3f, 9f); lineTo(9f, 9f); lineTo(17f, 3f); lineTo(17f, 23f); lineTo(9f, 17f); lineTo(3f, 17f); close()
                    moveTo(22f, 10f); lineTo(22f, 16f)
                }
                drawPath(path, tint, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private data class Shown(val value: Int, val roll: Boolean, val quick: Boolean)

/**
 * readout.tsx: the percentage reads as one color at a time: light glyphs on
 * the empty well, dark glyphs once the light covers nearly all of them. The
 * whole number swaps (140ms crossfade, with hysteresis), so a light edge never
 * splits a digit into two colors.
 */
@Composable
fun Readout(control: TactileControl, scale: Float, highContrast: Boolean) {
    Perf.recomposed("Readout")
    // NumericText animates natively per glyph; it only needs the integer, so the
    // frame loop hands over a value only when it changes. Discrete jumps (tap,
    // step) roll at 240ms; under a dragging finger each step is a short, low
    // slide with no scale or blur so the digits stay legible. A channel swap
    // does not roll: it happens unseen under the fade.
    var shown by remember { mutableStateOf(Shown(52, roll = false, quick = false)) }
    DisposableEffect(control) {
        val r = AnimationEngine.reaction({ jsRound(control.readout()).toInt() }) { next, previous ->
            if (next == previous) return@reaction
            val quick = control.numberQuick()
            shown = Shown(next, rollsOnStep(previous, next, quick, control.numberRolls()), quick)
        }
        onDispose { AnimationEngine.removeReaction(r) }
    }
    val left = (G.Readout.x * scale).dp
    val width = (G.Readout.width * scale).dp
    val line = (G.Readout.line * scale).dp
    @Composable
    fun number(tint: Color) {
        NumericText(
            text = "${shown.value}%",
            modifier = Modifier.size(width, line),
            style = TextStyle(
                fontFamily = systemFace,
                fontWeight = if (highContrast) FontWeight.W600 else FontWeight.W500,
                fontSize = (G.Readout.size * scale).sp,
                fontFeatureSettings = "tnum",
            ),
            color = tint,
            alignment = NumericTextAlignment.End,
            direction = TransitionDirection.Automatic,
            blurEnabled = !shown.quick,
            shape = TransitionShape(
                maxBlurRadius = 1.5f,
                enterScale = if (shown.quick) 1f else 0.7f,
                travelRatio = if (shown.quick) 0.28f else 0.4f,
            ),
            animationsEnabled = shown.roll && !control.reduced,
            duration = if (shown.quick) 0.11f else 0.24f,
            bounce = if (shown.quick) 0f else 0.12f,
        )
    }
    Box(
        Modifier
            .offset(left, ((TOP + G.Readout.y) * scale).dp)
            .size(width, line)
            .graphicsLayer { alpha = control.numberAlpha().toFloat() },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1 - control.numberInk()).toFloat() }) {
            number(if (highContrast) Color.White else Color(Tactile.text))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = control.numberInk().toFloat() }) {
            number(Color(Tactile.ink))
        }
    }
}

/**
 * tactile-console.tsx: layout, native text overlays (title, readout,
 * FINE ×¼), hit areas, accessibility.
 */
@Composable
fun TactileConsole(
    control: TactileControl,
    fx: Effects,
    tilt: Shared<Tilt>,
    highContrast: Boolean,
    haptics: Haptics,
    strings: Strings,
    /** Hold-to-lift gesture on the bare housing (behind the controls). */
    pickUp: PanGesture?,
    /** 1 while the lifted 3D object stands in for this drawing. */
    standIn: SharedValue?,
    modifier: Modifier = Modifier,
) {
    Perf.recomposed("TactileConsole")
    val inactive = Color(if (highContrast) Tactile.inactiveHigh else Tactile.inactive)
    val fontScale = LocalDensity.current.fontScale
    val largeText = fontScale >= 1.35f
    val title = if (control.mode == 0) strings.t("Brightness", "Độ sáng") else strings.t("Volume", "Âm lượng")
    val now = control.snapshot[control.mode]

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value
        val scale = widthDp / G.width.toFloat()
        SideEffect { control.width.set(G.Track.width * widthDp / G.width) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (largeText) {
                val label = TextStyle(
                    fontFamily = Fonts.manropeSemiBold,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(Tactile.pageText),
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(title, style = label)
                    BasicText("$now%", style = label)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((HEIGHT * scale).dp)
                    .graphicsLayer { alpha = (if (standIn != null) 1 - standIn.get() else 1.0).toFloat() },
            ) {
                if (pickUp != null) {
                    Box(
                        Modifier
                            .offset(y = (TOP * scale).dp)
                            .fillMaxWidth()
                            .height((G.height * scale).dp)
                            .clearAndSetSemantics {}
                            .gestureDetector(pickUp),
                    )
                }
                ControlDrawing(control, scale, fx, tilt)
                if (!largeText) {
                    Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                        // One title whose glyphs swap natively, in the direction of
                        // the selector that was pressed (Volume sits below Brightness).
                        // Its left-to-right glyph stagger ends inside the 380ms light
                        // handoff.
                        Box(
                            Modifier
                                .offset((118 * scale).dp, ((TOP + 36) * scale).dp)
                                .size((360 * scale).dp, (40 * scale).dp)
                                .graphicsLayer { alpha = control.labelAlpha().toFloat() },
                        ) {
                            NumericText(
                                text = if (control.mode == 0) "Brightness" else "Volume",
                                modifier = Modifier.size((360 * scale).dp, (40 * scale).dp),
                                style = TextStyle(
                                    fontFamily = systemFace,
                                    fontWeight = FontWeight.W400,
                                    fontSize = (31 * scale).sp,
                                ),
                                color = Color(Tactile.text),
                                alignment = NumericTextAlignment.Start,
                                direction = if (control.mode == 1) TransitionDirection.Up else TransitionDirection.Down,
                                shape = TransitionShape(maxBlurRadius = 3f),
                                animationsEnabled = !control.reduced,
                                duration = 0.3f,
                                bounce = 0.18f,
                            )
                        }
                        Readout(control, scale, highContrast)
                        if (fx.fineScrub) {
                            BasicText(
                                "FINE ×¼",
                                modifier = Modifier
                                    .offset((300 * scale).dp, ((TOP + 44) * scale).dp)
                                    .graphicsLayer { alpha = control.fineShown.get().toFloat() },
                                style = TextStyle(
                                    fontFamily = systemFace,
                                    fontWeight = FontWeight.W400,
                                    fontSize = (20 * scale).sp,
                                    color = Color(Tactile.inactive),
                                ),
                            )
                        }
                    }
                }
                val adjustable: Modifier.(String) -> Modifier = { label ->
                    semantics {
                        contentDescription = label
                        stateDescription = "$now%"
                        progressBarRangeInfo = ProgressBarRangeInfo(now.toFloat(), 0f..100f, 0)
                        if (!control.enabled) disabled()
                        setProgress { target ->
                            control.adjust(if (target > now) 5 else -5)
                            true
                        }
                    }
                }
                val trackHeight = max(48f, 104 * scale)
                Box(
                    Modifier
                        .offset(
                            (G.Track.x * scale).dp,
                            ((TOP + G.Track.y + 52) * scale - trackHeight / 2).toFloat().dp,
                        )
                        .size((G.Track.width * scale).dp, trackHeight.dp)
                        .adjustable(
                            "$title, " + strings.t(
                                "Drag to adjust, or use increment and decrement.",
                                "Kéo để điều chỉnh, hoặc dùng thao tác tăng và giảm.",
                            ),
                        )
                        .gestureDetector(control.trackPan, control.trackTap),
                )
                Box(
                    Modifier
                        .offset(((G.Dial.x - 100) * scale).dp, ((TOP + G.Dial.y - 100) * scale).dp)
                        .size((200 * scale).dp)
                        .adjustable(
                            strings.t("$title dial", "Núm ${title.lowercase()}") + ", " +
                                strings.t("Drag left or right to adjust.", "Kéo sang trái hoặc phải để điều chỉnh."),
                        )
                        .gestureDetector(control.dialPan),
                )
                // The in-artwork selectors are the only mode controls on this page,
                // so they stay in the accessibility tree.
                for (mode in 0..1) ModeAction(control, mode, scale, inactive, haptics, strings)
            }
        }
    }
}

@Composable
private fun ModeAction(control: TactileControl, mode: Int, scale: Float, inactive: Color, haptics: Haptics, strings: Strings) {
    val (cx, cy, r) = if (mode == 0) Triple(G.Brightness.x, G.Brightness.y, G.Brightness.radius)
    else Triple(G.Volume.x, G.Volume.y, G.Volume.radius)
    val size = ((if (mode == 0) 50 else 46) * scale).dp
    val slop = 8f
    val press = if (mode == 0) control.brightPress else control.volumePress
    val pressability = remember(control, mode) {
        Pressability(
            onPressIn = { control.pressSelector(mode, true) },
            onPressOut = { control.pressSelector(mode, false) },
            onPress = {
                haptics.selection()
                control.select(mode)
            },
        )
    }
    pressability.enabled = control.enabled
    pressability.hitSlop = slop
    val label = if (mode == 0) strings.t("Brightness", "Độ sáng") else strings.t("Volume", "Âm lượng")
    Box(
        Modifier
            .offset(((cx - r) * scale - slop).toFloat().dp, ((TOP + cy - r) * scale - slop).toFloat().dp)
            .size(((r * 2) * scale + 2 * slop).toFloat().dp)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = control.mode == mode
                if (!control.enabled) disabled()
                onClick { haptics.selection(); control.select(mode); true }
            }
            .pressable(pressability),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { val k = (1 - press.get() * 0.08).toFloat(); scaleX = k; scaleY = k }) {
            ReferenceGlyph(mode, size, inactive)
            ReferenceGlyph(
                mode, size, Color(Tactile.text),
                Modifier.graphicsLayer {
                    // The lit glyph crossfades with the light's color handoff.
                    alpha = (if (mode == 0) 1 - control.selectorMix() else control.selectorMix()).toFloat()
                },
            )
        }
    }
}

/** Manrope (res/font/glowknobconsole_manrope_*.ttf, OFL) for the page text around the console. */
object Fonts {
    val manropeRegular = FontFamily(androidx.compose.ui.text.font.Font(com.ngocdevv.nativecook.R.font.glowknobconsole_manrope_regular, FontWeight.W400))
    val manropeSemiBold = FontFamily(androidx.compose.ui.text.font.Font(com.ngocdevv.nativecook.R.font.glowknobconsole_manrope_semibold, FontWeight.W600))
}
