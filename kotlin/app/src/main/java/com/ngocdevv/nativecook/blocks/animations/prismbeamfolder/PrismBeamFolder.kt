package com.ngocdevv.nativecook.blocks.animations.prismbeamfolder

import android.app.Activity
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.animations.prismbeamfolder.PrismBeamFolderArt.drawBack
import com.ngocdevv.nativecook.blocks.animations.prismbeamfolder.PrismBeamFolderArt.drawFront
import com.ngocdevv.nativecook.blocks.animations.prismbeamfolder.PrismBeamFolderArt.drawItem
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/*
 Prism Beam Folder — "Larder", a grocery list app saving the picks in your cart to a collection.

 A near-black stage and a light-blue folder named "Weekend Snacks". Press it and a prism beam
 shoots up out of it and opens into a cone of soft pink, red, amber, cream, blue and violet bands
 (AGSL, PrismBeamFolderBeam.kt; gradient bands below Android 13); the picks — peaches, cherries,
 aubergines, chilies drawn in code (PrismBeamFolderArt.kt) — float down through the light into the
 folder, which bobs as each one lands while its badge ticks up. Then the beam narrows to a line
 and drops back into the folder.

 autoPlay: LOOP = 8 s (PrismBeamFolderMotion.kt). Touch: hold the folder to open the beam and pour
 (one pick every 0.26 s while held); release and the beam closes after the last landing. Light
 haptics when the beam opens and on each landing. 2 s after the release, once the folder is back
 at rest, the loop restarts from t = 0. Reduced motion: a still of the beam fully open, no touch.

 Per frame the simulation runs once and bumps `tick`, read only inside draw and graphicsLayer
 lambdas, so nothing recomposes while it plays (the count line recomposes when the count changes).
*/

private val Background = pbfHex(0x08070C)
private val TextMain = pbfHex(0xF3F0F8)
private val TextMuted = pbfHex(0x8D88A0)

private class PrismBeamFolderHolder {
    var frame = PrismBeamFolderFrame()
    var running = false
}

/**
 * A save-to-collection screen: hold the folder and a prism beam opens out of it, carrying the
 * picks down into the folder.
 *
 * @param autoPlay plays the 8 s press-pour-close loop until touched.
 */
@Composable
fun PrismBeamFolder(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    // A shader the driver cannot compile throws: fall back to the drawn bands instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(PRISM_BEAM_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val sim = remember(auto) { PrismBeamFolderSim() }
    val holder = remember { PrismBeamFolderHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val count = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            PrismBeamFolderGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(sim, g, auto, reduced, wake.intValue) {
            sim.onFeel = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            fun publish(f: PrismBeamFolderFrame) {
                holder.frame = f
                if (count.intValue != f.count) count.intValue = f.count
                tick.intValue += 1
            }
            if (reduced) {
                publish(PrismBeamFolderSession.script(PrismBeamFolderMotion.REDUCED_AT, g))
                return@LaunchedEffect
            }
            publish(sim.frame(System.nanoTime() / 1e9, g, auto))
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { now ->
                        publish(sim.frame(now / 1e9, g, auto))
                        !auto && !sim.inSession && !sim.touching
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Weekend Snacks folder. Hold it to pour your picks in." }
                .pointerInput(sim, g, reduced, auto) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        if (!g.onFolder(down.position.x / d.toDouble(), down.position.y / d.toDouble())) return@awaitEachGesture
                        sim.press(System.nanoTime() / 1e9, g, auto)
                        if (!holder.running) wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                            }
                        } finally {
                            sim.release(System.nanoTime() / 1e9)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                drawStage(g, f, shader, brush, measurer)
            }
        }

        PrismBeamFolderHeader(safeTop, holder, tick)
        PrismBeamFolderLabels(g, holder, tick, count)
        BasicText(
            "Hold the folder to pour",
            modifier = Modifier
                .padding(top = (g.h - g.bottom - 39).dp)
                .fillMaxWidth()
                .height(18.dp),
            style = TextStyle(color = pbfHex(0x5F5A72), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

private fun DrawScope.drawStage(
    g: PrismBeamFolderGeo,
    f: PrismBeamFolderFrame,
    shader: RuntimeShader?,
    brush: ShaderBrush?,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val w = g.w.toFloat()
    val h = g.h.toFloat()
    val cx = g.cx.toFloat()
    val cy = g.folderCY.toFloat()
    drawRect(
        Brush.radialGradient(listOf(pbfHex(0x14101C), Background), center = Offset(cx, cy), radius = h * 0.75f),
        Offset.Zero, Size(w, h),
    )

    // Floor glow under the folder.
    val glow = f.glow.toFloat()
    if (glow > 0.001f) {
        withTransform({ scale(1f, 0.3f, pivot = Offset(cx, cy + 44f)) }) {
            drawCircle(
                Brush.radialGradient(
                    0f to pbfHex(0xFF5FD2, 0.32f * glow), 0.5f to pbfHex(0xB65CFF, 0.14f * glow), 1f to pbfHex(0xB65CFF, 0f),
                    center = Offset(cx, cy + 44f), radius = 190f,
                ),
                radius = 190f, center = Offset(cx, cy + 44f),
            )
        }
    }

    // The beam.
    if (f.beamAlpha > 0.001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
            shader.setFloatUniform("beam", cx, g.originY.toFloat(), g.length.toFloat(), f.tip.toFloat())
            shader.setFloatUniform("shape", f.hb.toFloat(), f.ht.toFloat(), f.beamAlpha.toFloat(), prismBeamPhase(f))
            shader.setFloatUniform("fade", (g.top + 60).toFloat(), (g.top + 260).toFloat(), 0f, 0f)
            drawRect(brush, Offset.Zero, Size(w, h))
        } else {
            drawBeamFallback(g, f)
        }
    }

    // Contact shadow (a soft radial stand-in for a 6 pt blur).
    val sx = f.folderSX.toFloat()
    withTransform({ scale(1f, 12f / 40f, pivot = Offset(cx, cy + 44f)) }) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.6f), 0.55f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent,
                center = Offset(cx, cy + 44f), radius = 67f * sx,
            ),
            radius = 67f * sx, center = Offset(cx, cy + 44f),
        )
    }

    // The folder scales from the middle of its bottom edge; items sit between its back and front.
    val sy = f.folderSY.toFloat()
    withTransform({
        translate(cx, cy + 42f)
        scale(sx, sy, pivot = Offset.Zero)
        translate(0f, -42f)
    }) {
        drawBack()
        if (f.beamAlpha > 0.001) {
            val k = (f.beamAlpha * (0.4 + 0.6 * f.glow)).toFloat()
            drawCircle(
                Brush.radialGradient(
                    0f to pbfHex(0xFFF3E6, 0.55f * k), 0.45f to pbfHex(0xFF5FD2, 0.22f * k), 1f to pbfHex(0xFF5FD2, 0f),
                    center = Offset(0f, -34f), radius = 70f,
                ),
                radius = 70f, center = Offset(0f, -34f), blendMode = BlendMode.Plus,
            )
        }
    }
    for (it in f.items) {
        val s = (it.scale * it.size / 48).toFloat()
        withTransform({
            translate(it.x.toFloat(), it.y.toFloat())
            rotate(it.angle.toFloat(), pivot = Offset.Zero)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawItem(it.kind, it.alpha.toFloat())
        }
    }
    withTransform({
        translate(cx, cy + 42f)
        scale(sx, sy, pivot = Offset.Zero)
        translate(0f, -42f)
    }) {
        drawFront(f.open.toFloat(), (f.glow * f.beamAlpha).toFloat())
    }

    // Press ripple.
    if (f.rippleA > 0.001) {
        val a = f.rippleA.toFloat()
        drawCircle(Color.White.copy(alpha = a * 0.45f), 22f, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = a), f.rippleR.toFloat(), Offset(cx, cy), style = Stroke(2f))
    }

    // Badge.
    if (f.badge > 0.001) {
        val b = f.badge.toFloat()
        translate(cx + 46f, cy - 38f) {
            scale(b, b, pivot = Offset.Zero) {
                drawCircle(Background, 14.5f, Offset.Zero)
                drawCircle(Brush.verticalGradient(listOf(pbfHex(0xFF4D9A), pbfHex(0xE8317F)), -13f, 13f), 13f, Offset.Zero)
                val layout = measurer.measure(
                    "${f.count}",
                    TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    density = androidx.compose.ui.unit.Density(1f, 1f),
                )
                drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
            }
        }
    }
}

@Composable
private fun PrismBeamFolderHeader(safeTop: Float, holder: PrismBeamFolderHolder, tick: MutableIntState) {
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp, top = (safeTop + 18f).dp)
            .fillMaxWidth()
            .graphicsLayer {
                tick.intValue
                alpha = holder.frame.header.toFloat()
            },
    ) {
        BasicText(
            "LARDER · COLLECTIONS",
            modifier = Modifier.height(14.dp),
            style = TextStyle(color = pbfHex(0x6F6A82), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            maxLines = 1,
        )
        BasicText(
            "Save your picks",
            modifier = Modifier.padding(top = 6.dp).height(36.dp),
            style = TextStyle(color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 30.sp, stepSize = 0.5.sp),
        )
        BasicText(
            "Hold a folder to pour them in.",
            modifier = Modifier.padding(top = 4.dp).height(20.dp),
            style = TextStyle(color = TextMuted, fontSize = 15.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PrismBeamFolderLabels(g: PrismBeamFolderGeo, holder: PrismBeamFolderHolder, tick: MutableIntState, count: MutableIntState) {
    Column(
        Modifier
            .padding(top = (g.folderCY + 42 + 16).dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "Weekend Snacks",
            modifier = Modifier.height(22.dp),
            style = TextStyle(color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(18.dp)) {
            val line = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
            BasicText(
                "9 picks ready",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        tick.intValue
                        alpha = 1f - holder.frame.saved.toFloat()
                    },
                style = line.copy(color = TextMuted),
                maxLines = 1,
            )
            BasicText(
                "+${count.intValue} saved",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        tick.intValue
                        alpha = holder.frame.saved.toFloat()
                    },
                style = line.copy(color = pbfHex(0xFF6FB5)),
                maxLines = 1,
            )
        }
    }
}
