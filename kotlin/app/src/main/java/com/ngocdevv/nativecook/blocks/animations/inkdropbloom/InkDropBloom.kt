package com.ngocdevv.nativecook.blocks.animations.inkdropbloom

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Ink Drop Bloom — "Studio Tint", a palette generator whose loader is a glass of water.

 One full-screen AGSL shader (InkDropBloomShader.kt; a gradient stand-in below Android 13) draws
 the room, the glass, the ink, the press and the falling drop. Drops of indigo, orchid and lagoon
 fall in one by one and bloom into plumes that sink, break into smaller rings and mix; the palette
 card fills each swatch as its colour blooms; a mesh press pushes the ink down and the water clears.

 autoPlay: LOOP = 9 s (InkDropBloomMotion.kt). Touch: tap in the water to drop ink there (indigo,
 orchid, lagoon in turn; light haptic); drag to stir. 2 s after the last touch the ink fades out
 (0.9 s) and the loop restarts from t = 0. Reduced motion: the full palette, still, no loop.

 Per frame: the simulation steps once and bumps `tick`, which is read only inside draw and
 graphicsLayer lambdas, so nothing recomposes while it plays.
*/

internal val InkBackground = Color(0xFFE6DED2)
private const val PHASE_LINE = 22f
private val InkText = Color(0xFF2A2330)
private val Muted = Color(0xFF776C61)
private val Rust = Color(0xFF8A5A3C)
private const val REGEN_PATH = "M17.6 8.2 A7 7 0 1 0 18 13.2 M17.8 3.6 V8.4 H13"

/** Whether the frame loop is running, so a touch knows to start it again. */
private class InkLoop {
    var running = false
}

/**
 * A palette generator whose loader is a tall glass of water: drops of ink bloom into plumes, the
 * swatches fill, and a press clears the water.
 *
 * @param autoPlay plays the 9 s drop-bloom-filter loop until touched.
 */
@Composable
fun InkDropBloom(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(INK_DROP_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val sim = remember(auto) { InkSim() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val loop = remember { InkLoop() }
    val tint = remember { FloatArray(3) }

    BoxWithConstraints(modifier.fillMaxSize().background(InkBackground).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val geo = remember(w, h, safeTop, safeBottom) { makeInkGeo(w, h, safeTop, safeBottom) }

        LaunchedEffect(sim, geo, auto, reduced, wake.intValue) {
            sim.step(0f, geo, auto, reduced)
            tick.intValue += 1
            if (reduced) return@LaunchedEffect
            loop.running = true
            try {
                var last = -1L
                while (true) {
                    val idle = withFrameNanos { now ->
                        val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                        last = now
                        sim.step(dt, geo, auto, false)
                        tick.intValue += 1
                        sim.idle(auto) && !sim.touching
                    }
                    if (idle) break
                }
            } finally {
                loop.running = false
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A glass of water. Tap the water to drop ink, drag to stir it." }
                .pointerInput(sim, geo, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val x0 = down.position.x / d
                        val y0 = down.position.y / d
                        sim.touchDown(x0, y0)
                        if (!loop.running) wake.intValue += 1
                        var x = x0
                        var y = y0
                        var lifted = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                x = c.position.x / d
                                y = c.position.y / d
                                if (!c.pressed) {
                                    lifted = true
                                    break
                                }
                                if (c.positionChanged()) sim.stir(x, y)
                            }
                            // A short touch that barely moved is a tap: ink goes in where it landed.
                            if (lifted && hypot(x - x0, y - y0) < 10f && inkInWater(geo, x, y)) {
                                sim.drop(x, y)
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } finally {
                            sim.touchUp()
                        }
                    }
                },
        ) {
            tick.intValue
            val d = density
            val f = sim.frame
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    inkTint(f, tint)
                    shader.setFloatUniform("size", max(1f, geo.w), max(1f, geo.h))
                    shader.setFloatUniform("glass", geo.cx, geo.top, geo.bottom, geo.hwTop)
                    shader.setFloatUniform("water", geo.surface, geo.floor, geo.hwBot, geo.wall)
                    for (i in 0 until 4) {
                        shader.setFloatUniform("d$i", f.drops[i * 4], f.drops[i * 4 + 1], f.drops[i * 4 + 2], f.drops[i * 4 + 3])
                        val c = InkColors[f.inks[i]]
                        shader.setFloatUniform("c$i", c[0], c[1], c[2])
                    }
                    shader.setFloatUniform("falling", f.falling[0], f.falling[1], f.falling[2], f.falling[3])
                    val fc = InkColors[f.fallInk]
                    shader.setFloatUniform("fallCol", fc[0], fc[1], fc[2])
                    shader.setFloatUniform("press", f.press[0], f.press[1], f.press[2], f.press[3])
                    shader.setFloatUniform("stir", f.stir[0], f.stir[1], f.stir[2], f.stir[3])
                    shader.setFloatUniform("flow", sim.flow)
                    shader.setFloatUniform("tint", tint[0], tint[1], tint[2])
                    drawRect(brush, Offset.Zero, Size(geo.w, geo.h))
                } else {
                    drawInkFallback(geo, f)
                }
            }
        }

        InkHeader(safeTop, sim, tick)
        InkPalette(geo, sim, tick)
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = geo.buttonTop.dp)
                .fillMaxWidth()
                .height(INK_BUTTON_H.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InkSaveButton(sim, tick)
            InkRegenerateButton()
        }
    }
}

@Composable
private fun InkHeader(safeTop: Float, sim: InkSim, tick: MutableIntState) {
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp, top = (safeTop + 12f).dp)
            .fillMaxWidth()
            .height(INK_HEADER_H.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                "STUDIO TINT",
                style = TextStyle(color = Rust, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp),
            )
            Box(
                Modifier
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(rgba(138, 90, 60, 0.1f))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "Batch 0427",
                    style = TextStyle(color = Rust, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
        }
        BasicText(
            "Generating your palette",
            modifier = Modifier.padding(top = 10.dp),
            style = TextStyle(color = InkText, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 30.sp, stepSize = 0.5.sp),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(PHASE_LINE.dp)
                .clipToBounds(),
        ) {
            InkPhases.forEachIndexed { i, label ->
                BasicText(
                    label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PHASE_LINE.dp)
                        .graphicsLayer {
                            tick.intValue
                            val d = i - sim.frame.label
                            alpha = max(0f, 1f - abs(d) * 1.4f)
                            translationY = d * PHASE_LINE.dp.toPx()
                        },
                    style = TextStyle(color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InkPalette(geo: InkGeo, sim: InkSim, tick: MutableIntState) {
    val shape = RoundedCornerShape(24.dp)
    val hairline = (1f / LocalDensity.current.density).dp
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = geo.paletteTop.dp)
            .fillMaxWidth()
            .height(INK_PALETTE_H.dp)
            // The card is not water: touches on it do not reach the glass, as in React Native.
            .pointerInput(Unit) {}
            .cssBoxShadow(rgba(70, 52, 36, 0.14f), 1.dp, 3.dp, 24.dp)
            .cssBoxShadow(rgba(70, 52, 36, 0.12f), 18.dp, 36.dp, 24.dp)
            .clip(shape)
            .background(rgba(252, 249, 244, 0.86f))
            .inkInsetTop(rgba(255, 255, 255, 0.9f), 24.dp)
            .border(hairline, rgba(120, 100, 80, 0.18f), shape)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Palette", style = TextStyle(color = InkText, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(rgba(42, 35, 48, 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            tick.intValue
                            scaleX = max(0.001f, sim.frame.bar)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF4A3F86)),
                )
            }
            BasicText(
                "5 colours",
                style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            InkSwatches.forEachIndexed { i, s -> InkSwatchView(i, s, sim, tick) }
        }
    }
}

/** A swatch: a dashed outline that fills from the bottom like a little vial of its colour. */
@Composable
private fun InkSwatchView(index: Int, s: InkSwatch, sim: InkSim, tick: MutableIntState) {
    val vial = RoundedCornerShape(14.dp)
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(vial)
                .background(rgba(42, 35, 48, 0.03f))
                .drawWithContent {
                    drawContent()
                    // The dashed outline sits over the fill, as a layer border does on iOS.
                    val sw = 1.5.dp.toPx()
                    drawRoundRect(
                        rgba(42, 35, 48, 0.22f),
                        topLeft = Offset(sw / 2f, sw / 2f),
                        size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(14.dp.toPx() - sw / 2f),
                        style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * sw, 3 * sw))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(52.dp)
                    .graphicsLayer {
                        tick.intValue
                        translationY = (1f - sim.frame.swatches[index]) * 52.dp.toPx()
                    }
                    .background(s.color),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .graphicsLayer { alpha = 0.55f }
                        .background(s.deep),
                )
                Box(
                    Modifier
                        .padding(start = 6.dp, top = 6.dp)
                        .size(14.dp, 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(rgba(255, 255, 255, 0.35f)),
                )
            }
        }
        Column(
            Modifier
                .padding(top = 6.dp)
                .graphicsLayer {
                    tick.intValue
                    alpha = 0.35f + 0.65f * min(1f, sim.frame.swatches[index] * 1.5f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(s.name, style = TextStyle(color = InkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            BasicText(
                s.hex,
                modifier = Modifier.padding(top = 1.dp),
                style = TextStyle(color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RowScope.InkSaveButton(sim: InkSim, tick: MutableIntState) {
    val shape = RoundedCornerShape((INK_BUTTON_H / 2f).dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                tick.intValue
                alpha = 0.5f + 0.5f * sim.frame.bar
            }
            .cssBoxShadow(rgba(30, 20, 40, 0.3f), 1.dp, 2.dp, (INK_BUTTON_H / 2f).dp)
            .cssBoxShadow(rgba(30, 20, 40, 0.22f), 10.dp, 24.dp, (INK_BUTTON_H / 2f).dp)
            .clip(shape)
            .background(Color(0xFF2B2433))
            .inkInsetTop(rgba(255, 255, 255, 0.14f), (INK_BUTTON_H / 2f).dp)
            .clickable(interactionSource = null, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Save palette", style = TextStyle(color = Color(0xFFF6F1EA), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun InkRegenerateButton() {
    val shape = RoundedCornerShape((INK_BUTTON_H / 2f).dp)
    val hairline = (1f / LocalDensity.current.density).dp
    Box(
        Modifier
            .size(INK_BUTTON_H.dp)
            .cssBoxShadow(rgba(70, 52, 36, 0.14f), 1.dp, 2.dp, (INK_BUTTON_H / 2f).dp)
            .cssBoxShadow(rgba(70, 52, 36, 0.1f), 8.dp, 18.dp, (INK_BUTTON_H / 2f).dp)
            .clip(shape)
            .background(rgba(252, 249, 244, 0.9f))
            .border(hairline, rgba(120, 100, 80, 0.22f), shape)
            .clickable(interactionSource = null, indication = null) {}
            .semantics { contentDescription = "Regenerate" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .drawWithCache {
                    val path = PathParser().parsePathString(REGEN_PATH).toPath()
                    val stroke = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind {
                        scale(density, density, pivot = Offset.Zero) { drawPath(path, InkText, style = stroke) }
                    }
                },
        )
    }
}

/** CSS `inset 0 1px 0 color`: a one-dp crescent of light along the top inside edge. */
private fun Modifier.inkInsetTop(color: Color, radius: Dp) = drawWithCache {
    val r = radius.toPx()
    val dy = 1.dp.toPx()
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(r))) }
    onDrawBehind {
        clipPath(outer) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
    }
}
