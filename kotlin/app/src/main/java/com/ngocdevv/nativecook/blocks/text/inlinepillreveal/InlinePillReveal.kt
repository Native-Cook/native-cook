package com.ngocdevv.nativecook.blocks.text.inlinepillreveal

import android.graphics.BlurMaskFilter
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.text.inlinepillreveal.IprArt.scene
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Inline Pill Reveal — the welcome screen of an invented slow-travel stays brand ("slowfield")
 (a port of InlinePillReveal.swift).

 Look: a flat terracotta screen with "Slow mornings," in Outfit, the brand mark and Skip at the
 top, page dots at the bottom. Hero: a caret blinks between the two words, then a pill opens in
 that gap and pushes the words apart by its real width until they slide off both edges; the pill
 drifts to the centre and grows into a full-bleed window (capsule corners → 0). Inside it is a
 sunrise over layered hills drawn in code (InlinePillRevealArt.kt): as the pill grows the hills
 rise into frame and the sun climbs over the ridge. "made yours" rises over the scene, then a
 Find your stay button; the page dots move on. It holds, then plays back to the text line.

 autoPlay: LOOP = 8 s (IprModel.timeline); frame 8 s equals frame 0.
 Touch: drag sideways on the line (or anywhere once open) to scrub the reveal; release snaps open
 or closed with the finger's speed; tap toggles. 2 s after the last touch it eases closed and the
 loop starts again.
 Reduced motion: no loop; the revealed screen shows still, a tap crossfades to the text line.
*/

private fun Double.f() = toFloat()

/** Text measured once, in dp (density 1). */
private class IprTexts(m: TextMeasurer, regular: FontFamily, medium: FontFamily) {
    private val unit = Density(1f, 1f)
    private fun t(m: TextMeasurer, s: String, family: FontFamily, size: Float, track: Float, color: Color) = m.measure(
        s,
        TextStyle(fontFamily = family, fontSize = size.sp, letterSpacing = track.sp, color = color),
        density = unit,
    )

    private val cream = iprHex(IprModel.CREAM)
    val left = t(m, IprModel.LINE1[0], regular, IprModel.FONT_SIZE, IprModel.TRACKING, cream)
    val right = t(m, IprModel.LINE1[1], regular, IprModel.FONT_SIZE, IprModel.TRACKING, cream)
    val line1 = t(m, IprModel.LINE1.joinToString(" "), regular, IprModel.FONT_SIZE, IprModel.TRACKING, cream)
    val words2 = IprModel.LINE2.map { t(m, it, regular, IprModel.FONT_SIZE, IprModel.TRACKING, cream) }
    val line2 = t(m, IprModel.LINE2.joinToString(" "), regular, IprModel.FONT_SIZE, IprModel.TRACKING, cream)
    val brand = t(m, IprModel.BRAND, medium, 18f, 0.2f, cream)
    val skip = t(m, IprModel.SKIP, regular, 15f, 0f, iprHex(IprModel.CREAM, 0.8))
    val cta = t(m, IprModel.CTA, medium, 17f, 0f, iprHex(IprModel.INK))
}

/** Width of a one-line layout, unrounded. */
private fun TextLayoutResult.w() = (getLineRight(0) - getLineLeft(0)).toDouble()

private class IprHolder {
    var frame = IprFrame()
    var shown = 1.0
}

@Composable
fun InlinePillReveal(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    BlockSystemBars(darkIcons = false)
    val driver = remember(auto, reduced) { IprDriver() }
    val holder = remember { IprHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer, context) {
        IprTexts(
            measurer,
            FontFamily(Font(IprModel.REGULAR_ASSET, context.assets)),
            FontFamily(Font(IprModel.MEDIUM_ASSET, context.assets)),
        )
    }
    val grain = remember { ShaderBrush(ImageShader(IprArt.grain, TileMode.Repeated, TileMode.Repeated)) }

    BoxWithConstraints(modifier.fillMaxSize().background(iprHex(IprModel.PAGE)).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            while (true) {
                val idle = withFrameNanos { n ->
                    val now = n / 1e9
                    holder.frame = driver.frame(now, auto, reduced)
                    holder.shown = if (reduced) driver.shownAmount(now) else 1.0
                    tick.intValue += 1
                    !auto && !driver.busy(now, reduced)
                }
                if (idle) break
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun kick() {
            wake.intValue += 1
        }
        fun feel() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Slow mornings, made yours. Find your stay."
                    onClick("Open or close the morning view") {
                        val t = now()
                        if (reduced) {
                            driver.toggleReduced(t)
                        } else {
                            driver.begin(t, auto)
                            driver.end(0.0, 0.0, true, w, t)
                        }
                        kick()
                        true
                    }
                }
                .pointerInput(driver, w, h, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val grabbed = !reduced && driver.accepts((down.position.y / d).toDouble(), h / 2, now(), auto)
                        if (grabbed) driver.begin(now(), auto)
                        kick()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            val dx = ((c.position.x - down.position.x) / d).toDouble()
                            if (grabbed && abs(dx) > 6) driver.drag(dx, w, now())
                            if (!c.pressed) break
                            if (grabbed) c.consume()
                        }
                        val moved = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d)
                        val t = now()
                        if (reduced) {
                            if (moved < 10) {
                                driver.toggleReduced(t)
                                feel()
                            }
                        } else if (grabbed) {
                            val dx = ((last.x - down.position.x) / d).toDouble()
                            val vx = (tracker.calculateVelocity().x / d).toDouble()
                            if (driver.end(dx, vx, moved < 8, w, t)) feel()
                        }
                        kick()
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                drawRect(iprHex(IprModel.PAGE), Offset.Zero, Size(w.f(), h.f()))
                if (reduced) {
                    layer(1 - holder.shown, w, h) { iprDraw(w, h, safeTop.toDouble(), safeBottom.toDouble(), IprModel.reveal(0.0, 0.0), texts) }
                    layer(holder.shown, w, h) { iprDraw(w, h, safeTop.toDouble(), safeBottom.toDouble(), f, texts) }
                } else {
                    iprDraw(w, h, safeTop.toDouble(), safeBottom.toDouble(), f, texts)
                }
            }
            drawRect(grain, alpha = 0.045f)
        }
    }
}

// Drawing (in dp).

private val blurPaint = android.graphics.Paint()
private val layerPaint = Paint()

private inline fun DrawScope.layer(alpha: Double, w: Double, h: Double, block: DrawScope.() -> Unit) {
    if (alpha <= 0.001) return
    layerPaint.alpha = alpha.f().coerceIn(0f, 1f)
    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w.f(), h.f()), layerPaint) }
    block()
    drawIntoCanvas { it.restore() }
}

/** A soft (blurred) fill or stroke of a path. Android's blur radius is 1.5 × SwiftUI's. */
private fun DrawScope.blurred(path: Path, color: Color, radius: Double, dy: Double = 0.0, strokeWidth: Double = 0.0) {
    blurPaint.reset()
    blurPaint.isAntiAlias = true
    blurPaint.color = color.toArgb()
    if (strokeWidth > 0) {
        blurPaint.style = android.graphics.Paint.Style.STROKE
        blurPaint.strokeWidth = strokeWidth.f()
    }
    blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas {
        val c = it.nativeCanvas
        c.save()
        c.translate(0f, dy.f())
        c.drawPath(path.asAndroidPath(), blurPaint)
        c.restore()
    }
}

private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double) = Path().apply {
    addRoundRect(RoundRect(Rect(x.f(), y.f(), (x + w).f(), (y + h).f()), CornerRadius(r.f())))
}

/** Draws a measured text with its baseline at y, starting at x. */
private fun DrawScope.put(l: TextLayoutResult, x: Double, baseline: Double, alpha: Double = 1.0, shadow: Shadow? = null) {
    drawText(l, topLeft = Offset((x - l.getLineLeft(0)).f(), (baseline - l.firstBaseline).f()), alpha = alpha.f(), shadow = shadow)
}

private fun DrawScope.iprDraw(w: Double, h: Double, top: Double, bottom: Double, f: IprFrame, tx: IprTexts) {
    val m = IprModel
    val cy = h / 2
    val baseline = cy + m.BASELINE_DROP
    val wl = tx.left.w()
    val wr = tx.right.w()
    val whole = tx.line1.w()
    val space = max(6.0, whole - wl - wr)
    val startX = (w - whole) / 2
    val gapX = startX + wl + space / 2

    // The pill: inline (width from the gap, drifting to the centre), then full bleed.
    val o = f.open
    val inlineW = m.lerp(0.0, w - 2 * m.PILL_MARGIN, o)
    val inlineX = m.lerp(gapX, w / 2, o)
    val inlineH = m.lerp(m.CARET_H, m.PILL_H, m.easeOut(m.seg(o, 0.0, 0.2)))
    val full = f.full
    val rx = m.lerp(inlineX - inlineW / 2, 0.0, full)
    val ry = m.lerp(cy - inlineH / 2, 0.0, full)
    val rw = m.lerp(inlineW, w, full)
    val rh = m.lerp(inlineH, h, full)

    // Words ride the pill's edges.
    val restHalf = space / 2 + m.CARET_PART * f.part
    val half = m.lerp(restHalf, m.WORD_GAP, m.easeOut(m.seg(o, 0.0, 0.15)))
    val leftEnd = (if (o > 0) rx else gapX) - half
    val rightStart = (if (o > 0) rx + rw else gapX) + half

    if (o > 0) {
        val dw = max(rw, m.CARET_W)
        val dx = rx + rw / 2 - dw / 2
        val radius = min(dw, rh) / 2 * (1 - full)
        val shape = roundRect(dx, ry, dw, rh, radius)
        val lift = 1 - full
        if (lift > 0.01) {
            blurred(shape, iprHex(0x3A1A10, 0.20 * lift), 11.0, 12.0)
            blurred(shape, iprHex(0x3A1A10, 0.18 * lift), 1.5, 2.0)
        }
        clipPath(shape) {
            scene(w, h, cy, o, full, f.t)
            if (lift > 0.01) blurred(shape, iprHex(0x2A1208, 0.22 * lift), 4.0, strokeWidth = 6.0)
        }
        if (lift > 0.01) {
            val rim = roundRect(dx + 0.5, ry + 0.5, dw - 1, rh - 1, max(0.0, radius - 0.5))
            drawPath(
                rim,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = (0.24 * lift).f()), Color.White.copy(alpha = 0f)),
                    startY = ry.f(), endY = (ry + 20).f(),
                ),
                style = Stroke(1f),
            )
        }
    }

    put(tx.left, leftEnd - wl, baseline)
    put(tx.right, rightStart, baseline)

    if (f.caret > 0.001) {
        drawPath(
            roundRect(gapX - m.CARET_W / 2, cy - m.CARET_H / 2, m.CARET_W, m.CARET_H, m.CARET_W / 2),
            iprHex(m.CREAM, f.caret),
        )
    }

    // Second line over the scene.
    val made = tx.words2[0].w()
    val yours = tx.words2[1].w()
    val l2 = tx.line2.w()
    val l2Start = (w - l2) / 2
    val l2Space = max(6.0, l2 - made - yours)
    val xs = doubleArrayOf(l2Start, l2Start + made + l2Space)
    for (i in 0 until 2) {
        val a = f.wordIn[i] * (1 - f.wordOut[i])
        if (a < 0.001) continue
        val dy = 18 * (1 - f.wordIn[i]) + 10 * f.wordOut[i]
        put(tx.words2[i], xs[i], baseline + dy, a, Shadow(iprHex(0x2A1410, 0.55), Offset(0f, 2f), 15f))
    }

    chrome(w, h, top, bottom, f, tx)
}

/** Brand mark, Skip, page dots and the call to action. */
private fun DrawScope.chrome(w: Double, h: Double, top: Double, bottom: Double, f: IprFrame, tx: IprTexts) {
    val cream = iprHex(IprModel.CREAM)
    val markY = top + 24
    drawArc(cream, 180f, 180f, true, Offset(25f, (markY + 3 - 7).f()), Size(14f, 14f))
    drawPath(roundRect(22.0, markY + 5, 20.0, 1.8, 0.9), cream)
    put(tx.brand, 48.0, markY + 6)
    put(tx.skip, w - 24 - tx.skip.w(), markY + 5)

    // Page dots.
    val dotY = h - bottom - 26
    val act = doubleArrayOf(1 - f.dots, f.dots, 0.0)
    val widths = DoubleArray(3) { 6 + 14 * act[it] }
    var x = (w - (widths.sum() + 16)) / 2
    for (i in 0 until 3) {
        drawPath(roundRect(x, dotY - 3, widths[i], 6.0, 3.0), iprHex(IprModel.CREAM, 0.45 + 0.55 * act[i]))
        x += widths[i] + 8
    }

    // Call to action.
    val a = f.ctaIn * (1 - f.ctaOut)
    if (a > 0.001) {
        val dy = 16 * (1 - f.ctaIn) + 10 * f.ctaOut
        val bx = 24.0
        val by = h - bottom - 84 - 27 + dy
        val bw = w - 48
        val shape = roundRect(bx, by, bw, 54.0, 27.0)
        layer(a, w, h) {
            blurred(shape, iprHex(0x140805, 0.25), 12.0, 6.0)
            drawPath(shape, cream)
            val lw = tx.cta.w()
            val gx = bx + bw / 2 - (lw + 10 + 14) / 2
            val my = by + 27
            put(tx.cta, gx, my + 6)
            val ax = gx + lw + 10
            val arrow = Path().apply {
                moveTo(ax.f(), my.f())
                lineTo((ax + 14).f(), my.f())
                moveTo((ax + 9).f(), (my - 5).f())
                lineTo((ax + 14).f(), my.f())
                lineTo((ax + 9).f(), (my + 5).f())
            }
            drawPath(arrow, iprHex(IprModel.INK), style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
