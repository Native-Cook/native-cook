package com.ngocdevv.nativecook.blocks.inputs.popupburnsearch

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot

/*
 Popup Burn Search — SupperSeek 99, an invented retro "ask the kitchen" search on a deep-blue desktop
 (a port of PopupBurnSearch.swift).

 Look: a black slanted search bar with a cream search button, a red comic starburst behind it, white answer
 pop-ups with lilac title bars, a dock with the app's name and a clock. All drawn in code.
 Hero: the answer pop-ups pile up, then burn away bottom first: a jagged burn front climbs each window, the
 paper is gone below it, a char rim and scorch sit above it and the rest darkens, a four-layer comic flame
 crown rides the front and boils at 12 fps, embers and ash rise, and a warm glow lights the desktop.
 Supporting: the query types with a caret, the starburst springs in, the cursor glides in and clicks with a
 squish, windows scale in with an overshoot and a settling jitter, the status line counts the answers down.

 autoPlay: LOOP = 9 s (PbsModel); rest on the empty bar → type → burst → click → six windows → burn →
 select-all and clear → rest. Every frame is a function of loop time; frame 9 s equals frame 0.
 Touch: tap the search button to spawn the windows, tap a window to burn it, long-press to burn them all.
 A touch takes over where the loop is; 2 s after the last touch the rest burns, the query clears, the loop restarts.
 Reduced motion: no loop; the query, burst and six windows stand still; taps fade windows out and back in.
*/

private fun pbsColor(hex: Int, a: Double = 1.0) = Color(
    red = ((hex shr 16) and 0xFF) / 255f,
    green = ((hex shr 8) and 0xFF) / 255f,
    blue = (hex and 0xFF) / 255f,
    alpha = a.coerceIn(0.0, 1.0).toFloat(),
)

private fun Double.f() = toFloat()

/** Text measured once, in dp (density 1). */
private class PbsTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, mono: Boolean = false, track: Float = 0f) = m.measure(
        s,
        TextStyle(
            fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp, color = Color.White,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
        density = unit,
    )

    val app = t("SupperSeek 99", 15f, FontWeight.Bold)
    val version = t("v1.0", 11f, FontWeight.Bold, mono = true)
    val title = t("Ask the kitchen.", 34f, FontWeight.ExtraBold)
    val query = (0..PbsModel.QUERY.length).map { t(PbsModel.QUERY.take(it), 11f, FontWeight.ExtraBold, track = 0.3f) }
    val statuses = PbsModel.STATUSES.map { t(it, 10f, FontWeight.Bold, mono = true) }
    val answers = PbsModel.ANSWERS.map { lines -> lines.map { t(it, 26f, FontWeight.Normal) } }
    val dockName = t("SUPPERSEEK", 11f, FontWeight.Bold, mono = true)
    val clock = t("7:42 PM", 11f, FontWeight.Bold, mono = true)
}

private fun TextLayoutResult.w() = size.width.toFloat()
private fun TextLayoutResult.h() = size.height.toFloat()

private class PbsHolder {
    var frame = PbsFrame()
}

@Composable
fun PopupBurnSearch(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val safeTop = (WindowInsets.safeDrawing.getTop(screen) / screen.density).toDouble()
    val safeBottom = (WindowInsets.safeDrawing.getBottom(screen) / screen.density).toDouble()
    BlockSystemBars(darkIcons = false)
    val driver = remember(auto, reduced) { PbsDriver() }
    val holder = remember { PbsHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { PbsTexts(measurer) }
    val painter = remember { PbsPainter() }

    BoxWithConstraints(modifier.fillMaxSize().background(pbsColor(0x12306A)).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            while (true) {
                val idle = withFrameNanos { n ->
                    val now = n / 1e9
                    holder.frame = driver.frame(now, auto, reduced)
                    tick.intValue += 1
                    !auto && !driver.busy(now)
                }
                if (idle) break
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun feel() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "SupperSeek 99. What should I cook tonight? Six answers." }
                .pointerInput(driver, w, h, auto) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        driver.takeOver(now(), auto)
                        wake.intValue += 1
                        var last = down.position
                        var moved = false
                        val released = withTimeoutOrNull(450L) {
                            var up = false
                            while (!up) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id }
                                if (c == null) {
                                    up = true
                                } else {
                                    last = c.position
                                    if ((last - down.position).getDistance() / d > 10) moved = true
                                    if (!c.pressed) up = true
                                }
                            }
                            true
                        }
                        if (released == null) {
                            if (!moved) {
                                val t = now()
                                driver.takeOver(t, auto)
                                if (driver.burnAll(t)) feel()
                            }
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                            }
                            driver.takeOver(now(), auto)
                        } else {
                            val t = now()
                            driver.takeOver(t, auto)
                            val dx = ((last.x - down.position.x) / d).toDouble()
                            val dy = ((last.y - down.position.y) / d).toDouble()
                            if (!moved && hypot(dx, dy) < 10) {
                                val x = (last.x / d).toDouble()
                                val y = (last.y / d).toDouble()
                                if (PbsModel.buttonHit(x, y, w, h)) {
                                    if (driver.search(t)) feel()
                                } else if (driver.burn(x, y, t, w, h)) {
                                    feel()
                                }
                            }
                        }
                        wake.intValue += 1
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                with(painter) { draw(f, w, h, safeTop, safeBottom, texts) }
            }
        }
    }
}

private class PbsPainter {
    private val m = PbsModel
    private val layerPaint = Paint()
    private val dotPath = Path()
    private var dotSize = Size.Zero

    private fun poly(pts: List<PbsPoint>, dx: Double = 0.0, dy: Double = 0.0): Path {
        val p = Path()
        pts.forEachIndexed { i, q ->
            if (i == 0) p.moveTo((q.x + dx).f(), (q.y + dy).f()) else p.lineTo((q.x + dx).f(), (q.y + dy).f())
        }
        p.close()
        return p
    }

    private fun line(pts: List<PbsPoint>, dy: Double = 0.0): Path {
        val p = Path()
        pts.forEachIndexed { i, q ->
            if (i == 0) p.moveTo(q.x.f(), (q.y + dy).f()) else p.lineTo(q.x.f(), (q.y + dy).f())
        }
        return p
    }

    private fun DrawScope.textLeading(t: TextLayoutResult, x: Double, cy: Double, color: Color) {
        drawText(t, color = color, topLeft = Offset(x.f(), (cy - t.h() / 2).f()))
    }

    private fun DrawScope.textTrailing(t: TextLayoutResult, x: Double, cy: Double, color: Color) {
        drawText(t, color = color, topLeft = Offset((x - t.w()).f(), (cy - t.h() / 2).f()))
    }

    fun DrawScope.draw(f: PbsFrame, w: Double, h: Double, top: Double, bottom: Double, tx: PbsTexts) {
        background(w, h)
        dots(w, h)
        glows(f, w, h)
        header(w, top, tx)
        dock(w, h, bottom, tx)
        searchBar(f, w, h, tx)
        for (i in 0 until 6) window(i, f, w, h, tx)
        if (!f.reduced) for (i in 0 until 6) flames(i, f, w, h)
        cursor(f, w, h)
    }

    private fun DrawScope.background(w: Double, h: Double) {
        val size = Size(w.f(), h.f())
        drawRect(
            Brush.linearGradient(
                0f to pbsColor(0x0B1C3A), 0.55f to pbsColor(0x1C4890), 1f to pbsColor(0x3F86E2),
                start = Offset.Zero, end = Offset(w.f(), h.f()),
            ),
            size = size,
        )
        drawRect(
            Brush.radialGradient(
                listOf(pbsColor(0x6FB6FF, 0.35), pbsColor(0x6FB6FF, 0.0)),
                center = Offset((w * 0.95).f(), (h * 0.3).f()), radius = (w * 0.8).f(),
            ),
            size = size,
        )
    }

    /** A faint desktop dot grid, 24 dp apart (built once per size). */
    private fun DrawScope.dots(w: Double, h: Double) {
        val s = Size(w.f(), h.f())
        if (s != dotSize) {
            dotSize = s
            dotPath.reset()
            var y = 12.0
            while (y < h) {
                var x = 12.0
                while (x < w) {
                    dotPath.addRect(Rect((x - 0.75).f(), (y - 0.75).f(), (x + 0.75).f(), (y + 0.75).f()))
                    x += 24
                }
                y += 24
            }
        }
        drawPath(dotPath, pbsColor(0xFFFFFF, 0.07))
    }

    private fun DrawScope.glows(f: PbsFrame, w: Double, h: Double) {
        if (f.reduced) return
        for (i in 0 until 6) {
            val a = f.burnAge[i]
            if (a < 0 || a >= m.BURN_LENGTH) continue
            val r = m.windowRect(i, w, h)
            val g = m.flameGrow(a)
            val cy = m.burnFront(a, r).coerceIn(r.minY, r.maxY)
            val c = Offset(r.midX.f(), cy.f())
            drawCircle(
                Brush.radialGradient(listOf(pbsColor(0xFF7A1A, 0.28 * g), pbsColor(0xFF7A1A, 0.0)), center = c, radius = 150f),
                radius = 150f, center = c,
            )
        }
    }

    private val pixelIcon = listOf(".###...", "#...#..", "#...#..", "#...#..", ".###...", "....##.", ".....##")

    private fun DrawScope.header(w: Double, top: Double, tx: PbsTexts) {
        val y = top + 22
        pixelIcon.forEachIndexed { row, s ->
            s.forEachIndexed { col, ch ->
                if (ch == '#') {
                    drawRect(pbsColor(0xFFE03D), Offset((20 + col * 2).toFloat(), (y - 7 + row * 2).f()), Size(2f, 2f))
                }
            }
        }
        textLeading(tx.app, 42.0, y, Color.White)
        textTrailing(tx.version, w - 20, y, pbsColor(0xFFFFFF, 0.55))
    }

    private fun DrawScope.dock(w: Double, h: Double, bottom: Double, tx: PbsTexts) {
        val x = 16.0
        val y = h - bottom - 46
        val rw = w - 32
        val mid = y + 18
        drawRoundRect(pbsColor(0x0A1838, 0.6), Offset(x.f(), y.f()), Size(rw.f(), 36f), CornerRadius(4f, 4f))
        drawRect(pbsColor(0xFFFFFF, 0.12), Offset((x + 4).f(), y.f()), Size((rw - 8).f(), 1f))
        val bx = x + 12
        drawRect(pbsColor(0xFFFFFF, 0.8), Offset(bx.f(), (mid - 5).f()), Size(10f, 10f), style = Stroke(1.5f))
        drawRect(pbsColor(0xFFE03D), Offset((bx + 3).f(), (mid - 2).f()), Size(4f, 4f))
        textLeading(tx.dockName, x + 30, mid, pbsColor(0xFFFFFF, 0.8))
        textTrailing(tx.clock, x + rw - 12, mid, pbsColor(0xFFFFFF, 0.8))
    }

    private fun DrawScope.searchBar(f: PbsFrame, w: Double, h: Double, tx: PbsTexts) {
        val cx = w / 2
        val y = m.barY(h)
        val bx = m.buttonX(w)

        textLeading(tx.title, cx - 164, y - 72, Color.White)

        if (f.burstScale > 0.001) {
            translate((bx + 4).f(), (y + 2).f()) {
                rotate(f.burstAngle.f(), pivot = Offset.Zero) {
                    scale(f.burstScale.f(), f.burstScale.f(), pivot = Offset.Zero) {
                        drawPath(poly(m.burstPoints(f.burstQ)), pbsColor(0xEF3B55))
                    }
                }
            }
        }

        val body = poly(
            listOf(
                PbsPoint(cx - 166, y - 16), PbsPoint(cx + 104, y - 16),
                PbsPoint(cx + 112, y + 16), PbsPoint(cx - 158, y + 16),
            ),
        )
        drawPath(body, pbsColor(0x08080C))

        val x0 = cx - 148
        val q = tx.query[f.typed]
        val tw = if (f.typed == 0) 0.0 else q.w().toDouble()
        if (f.selected && tw > 0) {
            drawRect(pbsColor(0xFBF0D2), Offset((x0 - 3).f(), (y - 9).f()), Size((tw + 6).f(), 18f))
        }
        if (f.typed > 0) textLeading(q, x0, y, if (f.selected) pbsColor(0x0B1A3A) else Color.White)
        if (f.caret) drawRect(Color.White, Offset((x0 + tw + 3).f(), (y - 6.5).f()), Size(2f, 13f))

        val by = y + 2 * f.press
        val button = poly(
            listOf(
                PbsPoint(bx - 37, by - 24), PbsPoint(bx + 25, by - 24),
                PbsPoint(bx + 37, by + 24), PbsPoint(bx - 25, by + 24),
            ),
        )
        drawPath(button, pbsColor(0xFBF0D2))
        if (f.press > 0) drawPath(button, pbsColor(0x000000, 0.12 * f.press))
        drawPath(button, pbsColor(0x08080C), style = Stroke(1f))
        drawCircle(pbsColor(0x121212), 6.5f, Offset((bx - 2).f(), (by - 2).f()), style = Stroke(2.2f))
        drawLine(
            pbsColor(0x121212), Offset((bx + 2.8).f(), (by + 2.8).f()), Offset((bx + 7).f(), (by + 7).f()),
            strokeWidth = 2.6f, cap = StrokeCap.Round,
        )

        textLeading(tx.statuses[f.status], cx - 160, y + 40, pbsColor(0xFFFFFF, 0.72))
    }

    private fun DrawScope.window(i: Int, f: PbsFrame, w: Double, h: Double, tx: PbsTexts) {
        val s = f.spawnAge[i]
        val a = f.burnAge[i]
        if (s < 0 || a >= m.BURN_LENGTH) return
        val r = m.windowRect(i, w, h)
        var alpha: Double
        var scale = 1.0
        var ox = 0.0
        var oy = 0.0
        if (f.reduced) {
            alpha = m.seg(s, 0.0, 0.25) * (if (a >= 0) 1 - m.seg(a, 0.0, 0.3) else 1.0)
        } else {
            alpha = m.seg(s, 0.0, 0.06)
            scale = 0.55 + 0.45 * m.easeOutBack(m.seg(s, 0.0, 0.26))
            val settle = 1 - m.easeOutCubic(m.seg(s, 0.0, 0.3))
            ox = (m.hash(i * 5.1 + 1) - 0.5) * 20 * settle
            oy = (m.hash(i * 5.1 + 2) - 0.5) * 16 * settle
        }
        if (alpha <= 0.001) return
        val burning = !f.reduced && a >= 0
        val edge = if (burning) m.edgePoints(i, a, r) else emptyList()

        drawIntoCanvas { c ->
            layerPaint.alpha = alpha.f()
            c.saveLayer(Rect((r.minX - 40).f(), (r.minY - 40).f(), (r.maxX + 40).f(), (r.maxY + 40).f()), layerPaint)
        }
        translate((r.midX + ox).f(), (r.midY + oy).f()) {
            scale(scale.f(), scale.f(), pivot = Offset.Zero) {
                translate((-r.midX).f(), (-r.midY).f()) {
                    if (burning) {
                        val clip = ArrayList<PbsPoint>()
                        clip.add(PbsPoint(r.minX - 2, r.minY - 2))
                        clip.add(PbsPoint(r.maxX + 14, r.minY - 2))
                        clip.addAll(edge.reversed())
                        clipPath(poly(clip)) { paper(i, r, a, edge, burning, tx) }
                    } else {
                        paper(i, r, a, edge, burning, tx)
                    }
                }
            }
        }
        drawIntoCanvas { it.restore() }

        if (burning) {
            val inside = edge.filter { it.x >= r.minX - 1 && it.x <= r.maxX + 1 && it.y > r.minY - 1 }
            if (inside.size > 1) {
                drawPath(line(inside, 1.0), pbsColor(0xFF8C1A), style = Stroke(2.5f, join = StrokeJoin.Round))
            }
        }
    }

    private fun DrawScope.paper(i: Int, r: PbsRect, a: Double, edge: List<PbsPoint>, burning: Boolean, tx: PbsTexts) {
        drawRect(pbsColor(0x071226, 0.4), Offset((r.minX + 5).f(), (r.minY + 5).f()), Size(r.w.f(), r.h.f()))
        drawRect(pbsColor(0xFBFBFE), Offset(r.minX.f(), r.minY.f()), Size(r.w.f(), r.h.f()))
        drawRect(pbsColor(0x6C63E6), Offset(r.minX.f(), r.minY.f()), Size(r.w.f(), m.TITLE_H.f()))
        drawRect(pbsColor(0x9A94FF), Offset(r.minX.f(), r.minY.f()), Size(r.w.f(), 1f))
        titleButtons(r)
        tx.answers[i].forEachIndexed { k, t ->
            textLeading(t, r.minX + 16, r.minY + m.TITLE_H + 30 + k * 30, pbsColor(0x111118))
        }
        drawRect(
            pbsColor(0x1B1B3A, 0.4), Offset((r.minX + 0.5).f(), (r.minY + 0.5).f()), Size((r.w - 1).f(), (r.h - 1).f()),
            style = Stroke(1f),
        )
        if (burning) {
            val u = m.easeInOutSine(m.seg(a, 0.15, 1.6))
            val front = m.burnFront(a, r)
            val reach = 26 + 30 * u
            drawRect(
                Brush.verticalGradient(
                    listOf(pbsColor(0x6B3A16, 0.0), pbsColor(0x4A2410, 0.9)),
                    startY = (front - reach).f(), endY = front.f(),
                ),
                Offset(r.minX.f(), (front - reach).f()), Size(r.w.f(), (reach + 20).f()),
            )
            val char = 0.85 * m.seg(a, 0.7, 1.5)
            if (char > 0) drawRect(pbsColor(0x120A06, char), Offset(r.minX.f(), r.minY.f()), Size(r.w.f(), r.h.f()))
            drawPath(line(edge), pbsColor(0x1A0D06), style = Stroke(14f, join = StrokeJoin.Miter))
        }
    }

    private fun DrawScope.titleButtons(r: PbsRect) {
        val ink = pbsColor(0x1B1B3A)
        for (k in 0 until 3) {
            val x = r.maxX - 4 - 10 - (2 - k) * 12
            val y = r.minY + 4
            drawRect(Color.White, Offset(x.f(), y.f()), Size(10f, 10f))
            drawRect(ink, Offset((x + 0.5).f(), (y + 0.5).f()), Size(9f, 9f), style = Stroke(1f))
            when (k) {
                0 -> drawRect(ink, Offset((x + 2.5).f(), (y + 6.5).f()), Size(5f, 1.2f))
                1 -> drawRect(ink, Offset((x + 2.5).f(), (y + 2.5).f()), Size(5f, 5f), style = Stroke(1f))
                else -> {
                    drawLine(ink, Offset((x + 2.5).f(), (y + 2.5).f()), Offset((x + 7.5).f(), (y + 7.5).f()), 1.2f)
                    drawLine(ink, Offset((x + 7.5).f(), (y + 2.5).f()), Offset((x + 2.5).f(), (y + 7.5).f()), 1.2f)
                }
            }
        }
    }

    private val flameColors = intArrayOf(0xA80F28, 0xE8203A, 0xFF8C1A, 0xFFE03D)

    private fun DrawScope.flames(i: Int, f: PbsFrame, w: Double, h: Double) {
        val a = f.burnAge[i]
        if (f.spawnAge[i] < 0 || a < 0 || a >= m.BURN_LENGTH) return
        val r = m.windowRect(i, w, h)
        if (m.flameGrow(a) > 0.01) {
            for (l in 0 until 4) {
                val off = if (l == 0) 3.0 else 0.0
                drawPath(poly(m.flameContour(i, l, a, r), off, off), pbsColor(flameColors[l]))
            }
        }
        for (p in m.embers(i, a, r)) {
            translate(p.x.f(), p.y.f()) {
                rotate(Math.toDegrees(p.angle).f(), pivot = Offset.Zero) {
                    drawRect(
                        pbsColor(p.color, p.alpha), Offset((-p.size / 2).f(), (-p.size / 2).f()),
                        Size(p.size.f(), p.size.f()),
                    )
                }
            }
        }
    }

    private val arrow = listOf(
        PbsPoint(0.0, 0.0), PbsPoint(0.0, 17.0), PbsPoint(4.2, 13.2), PbsPoint(7.0, 19.6),
        PbsPoint(9.8, 18.4), PbsPoint(7.1, 12.2), PbsPoint(12.4, 12.2),
    )
    private val arrowPath = poly(arrow)

    private fun DrawScope.cursor(f: PbsFrame, w: Double, h: Double) {
        if (f.cursorAlpha <= 0.001) return
        val s = (1.4 * f.cursorSquish).f()
        translate((m.buttonX(w) + f.cursorX).f(), (m.barY(h) + f.cursorY).f()) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(arrowPath, Color.White, alpha = f.cursorAlpha.f())
                drawPath(
                    arrowPath, pbsColor(0x08080C), alpha = f.cursorAlpha.f(),
                    style = Stroke(1.6f / 1.4f, join = StrokeJoin.Round),
                )
            }
        }
    }
}
