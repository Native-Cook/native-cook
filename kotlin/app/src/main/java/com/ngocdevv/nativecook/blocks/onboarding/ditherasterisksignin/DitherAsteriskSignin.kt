package com.ngocdevv.nativecook.blocks.onboarding.ditherasterisksignin

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.round

/*
 Dither Asterisk Sign In — the sign-in screen of "Footnote", an invented reading app (a port of
 DitherAsteriskSignin.swift, the reference).

 A black stage, a big white asterisk built from a 31 × 31 grid of square cells, the heavy wordmark
 "FOOTNOTE", "Read between the lines", three dark pill buttons (passkey, email, phone, with glyphs
 drawn in code) and "or browse without an account".

 Hero: the mark is sliced. Vertical slices of the grid slide up and down by whole cells, their
 cells thin into a glyph dither (■ 1 0 +) and leave glyph streaks where they were, row bands shear
 sideways at the peak, then every slice springs home and the asterisk is whole again. The mark is
 one Path per frame (DitherAsteriskSigninMotion.markPath).

 autoPlay: LOOP = 7 s (DitherAsteriskSigninMotion.kt). Touch: drag across the mark to slice the
 columns under the finger (offset and dither follow the finger's movement and speed); release and they
 spring back. Light haptics on the first slice of a drag and when the mark is whole again. 2 s
 after the release, once whole, the loop restarts from t = 0. Reduced motion: the whole mark,
 still; no loop, no touch.

 Per frame the simulation runs once and bumps `tick`, read only inside the Canvas draw lambda, so
 nothing recomposes while it plays.
*/

private val Bg = Color(0xFF060606)
private val Ink = Color(0xFFF4F4F2)
private val TaglineColor = Color(0xFFD6D6D6)
private val Muted = Color(0xFF7C7C80)
private val Pill = Color(0xFF1C1C1E)

private class DitherAsteriskSigninGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    private val m = DitherAsteriskSigninMotion
    val span = m.N * m.PITCH - 1
    val markLeft = round((w - span) / 2 * 3) / 3
    val markTop = round(top + 64)
    val markCY = markTop + span / 2
    val wordTop = markTop + span + 34
    val tagTop = wordTop + 82
    val footerCY = h - bottom - 24
    fun buttonTop(i: Int): Double = footerCY - 32 - 54 - (2 - i) * 66
    val touchTop = maxOf(top, markTop - 40)
    val touchBottom = markTop + span + 34
}

private class DitherAsteriskSigninHolder {
    var frame = DitherAsteriskSigninFrame()
    val path = Path()
    val scratch = IntArray(DitherAsteriskSigninMotion.ROWS * DitherAsteriskSigninMotion.N)
    var running = false
}

/**
 * A sign-in screen whose pixel asterisk gets sliced into a glyph dither and springs back whole.
 *
 * @param autoPlay plays the 7 s slice-and-reassemble loop until touched.
 */
@Composable
fun DitherAsteriskSignin(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
    val sim = remember(auto) { DitherAsteriskSigninSim() }
    val holder = remember { DitherAsteriskSigninHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize().background(Bg).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            DitherAsteriskSigninGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(sim, auto, reduced, wake.intValue) {
            sim.onFeel = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            fun publish(f: DitherAsteriskSigninFrame) {
                holder.frame = f
                tick.intValue += 1
            }
            if (reduced) {
                publish(DitherAsteriskSigninMotion.autoFrame(0.0))
                return@LaunchedEffect
            }
            publish(sim.frame(System.nanoTime() / 1e9, auto))
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { now ->
                        publish(sim.frame(now / 1e9, auto))
                        !auto && !sim.live
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val f = holder.frame
            val w = g.w.toFloat()
            val cy = g.markCY.toFloat()
            scale(density, density, pivot = Offset.Zero) {
                drawRect(
                    Brush.radialGradient(listOf(Color(0xFF151515), Bg), center = Offset(w / 2, cy), radius = 300f),
                    Offset.Zero, Size(w, g.h.toFloat()),
                )
                if (f.glow > 0.001) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = f.glow.toFloat()), Color.White.copy(alpha = 0f)),
                            center = Offset(w / 2, cy), radius = 190f,
                        ),
                        radius = 190f, center = Offset(w / 2, cy),
                    )
                }
                DitherAsteriskSigninMotion.markPath(f, g.markLeft, g.markTop, holder.path, holder.scratch)
                // Streaks never reach into the wordmark, even under a hard drag.
                clipRect(0f, 0f, w, (g.wordTop + 4).toFloat()) {
                    drawPath(holder.path, Ink)
                }
            }
        }

        BasicText(
            "FOOTNOTE",
            modifier = Modifier
                .offset(x = 12.dp, y = g.wordTop.dp)
                .width((g.w - 24).dp)
                .height(78.dp),
            style = TextStyle(color = Ink, fontSize = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 49.sp, maxFontSize = 70.sp, stepSize = 0.5.sp),
        )
        BasicText(
            "Read between the lines",
            modifier = Modifier
                .offset(x = 16.dp, y = g.tagTop.dp)
                .width((g.w - 32).dp)
                .height(30.dp),
            style = TextStyle(color = TaglineColor, fontSize = 25.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 17.sp, maxFontSize = 25.sp, stepSize = 0.5.sp),
        )

        SignInPill("Continue with passkey", 0, g.buttonTop(0), g)
        SignInPill("Continue with email", 1, g.buttonTop(1), g)
        SignInPill("Continue with phone", 2, g.buttonTop(2), g)

        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Muted)) { append("or  ") }
                withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.SemiBold)) { append("browse without an account") }
            },
            modifier = Modifier
                .offset(y = (g.footerCY - 10).dp)
                .fillMaxWidth()
                .height(20.dp),
            style = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
            maxLines = 1,
        )

        // The mark's touch area.
        Box(
            Modifier
                .offset(y = g.touchTop.dp)
                .fillMaxWidth()
                .height((g.touchBottom - g.touchTop).dp)
                .semantics { contentDescription = "Footnote logo, an asterisk made of pixels. Drag across it to slice it." }
                .pointerInput(sim, g, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val d = density.toDouble()
                        fun feed(px: Float, py: Float) {
                            val xd = px / d
                            sim.touch(xd, py / d, (xd - g.markLeft - DitherAsteriskSigninMotion.CELL / 2) / DitherAsteriskSigninMotion.PITCH, System.nanoTime() / 1e9)
                        }
                        feed(first.position.x, first.position.y)
                        if (!holder.running) wake.intValue += 1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == first.id } ?: break
                                if (!c.pressed) break
                                feed(c.position.x, c.position.y)
                                c.consume()
                            }
                        } finally {
                            sim.release(System.nanoTime() / 1e9)
                        }
                    }
                },
        )
    }
}

@Composable
private fun SignInPill(label: String, icon: Int, y: Double, g: DitherAsteriskSigninGeo) {
    val shape = RoundedCornerShape(27.dp)
    Box(
        Modifier
            .offset(x = 16.dp, y = y.dp)
            .width((g.w - 32).dp)
            .height(54.dp)
            .background(Pill, shape)
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.White.copy(alpha = 0f)), shape)
            .semantics(mergeDescendants = true) { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(20.dp),
        ) {
            scale(density, density, pivot = Offset.Zero) {
                val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val p = Path()
                when (icon) {
                    0 -> {
                        drawCircle(Ink, 4f, Offset(6f, 10f), style = stroke)
                        p.moveTo(10f, 10f); p.lineTo(18.5f, 10f)
                        p.moveTo(15f, 10f); p.lineTo(15f, 13f)
                        p.moveTo(18f, 10f); p.lineTo(18f, 13.5f)
                    }
                    1 -> {
                        drawRoundRect(Ink, Offset(1.5f, 4f), Size(17f, 12f), CornerRadius(2f), style = stroke)
                        p.moveTo(2.5f, 5.5f); p.lineTo(10f, 11f); p.lineTo(17.5f, 5.5f)
                    }
                    else -> {
                        drawRoundRect(Ink, Offset(5f, 1.5f), Size(10f, 17f), CornerRadius(2.5f), style = stroke)
                        p.moveTo(8.5f, 15.2f); p.lineTo(11.5f, 15.2f)
                    }
                }
                drawPath(p, Ink, style = stroke)
            }
        }
    }
}
