package com.ngocdevv.nativecook.blocks.media.tilediscradio

import android.app.Activity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Tile Disc Radio — the now-playing screen of an invented radio app ("parquet.fm")
 (a port of TileDiscRadio.swift).

 Look: the page is the station's colour. A square cover of rounded "petal" tiles fills the top, and on
 it a big disc made of the same kind of tiles in another palette, separated by thin coloured lines, with
 the station's words set over it like a poster. Under it the station, the show, a tuner dial and the
 transport. Everything is drawn in code.

 Hero: changing station turns the disc a quarter turn (a 4° lean back, then a spring with a little
 overshoot) while each row of tiles runs like a conveyor, carrying the old station's tiles out and the
 new station's in, rows alternating direction with a 60 ms stagger and the gap lines widening as they
 slide. The background tiles recolour in a diagonal wave, the page colour follows, the words re-set
 one by one and the tuner needle scans to the new frequency.

 autoPlay: LOOP = 9 s. Rest on Lumen Sounds, "next" at 1.5 s, 4.3 s and 7.1 s (three stations), so
 the loop ends where it began; the disc has turned three quarter turns, and since every station's
 layout is drawn turned with it, the last frame is the first.
 Touch: swipe sideways on the cover to turn the disc with the finger: past 22° (or thrown) it changes
 station that way, else it springs back. Tap ⏮ / ⏭ to change, the middle button to pause. 2 s after
 the last touch it goes back to the first station and the loop restarts.
 Reduced motion: no turn and no conveyor; a change crossfades in 0.3 s, and the loop is off.
*/

private const val INK = 0xFFF8F2L
private const val COVER_INK = 0x160D22L
private const val LIVE_RED = 0xFF3B4AL

private fun hex(v: Long, a: Float = 1f) = Color(
    red = ((v shr 16) and 0xFF) / 255f,
    green = ((v shr 8) and 0xFF) / 255f,
    blue = (v and 0xFF) / 255f,
    alpha = a,
)

/** Two colours mixed in sRGB. */
private fun mix(a: Long, b: Long, t: Double, alpha: Float = 1f): Color {
    fun ch(v: Long, s: Int) = ((v shr s) and 0xFF) / 255.0
    return Color(
        red = TdrModel.lerp(ch(a, 16), ch(b, 16), t).toFloat(),
        green = TdrModel.lerp(ch(a, 8), ch(b, 8), t).toFloat(),
        blue = TdrModel.lerp(ch(a, 0), ch(b, 0), t).toFloat(),
        alpha = alpha,
    )
}

private fun Double.f() = toFloat()

internal class TdrGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx = w / 2
    val coverX = 16.0
    val coverY = top + 52
    val side = w - 32
    val bgCell = (side - 14) / 4
    val discY = coverY + side / 2
    val radius = side * 0.45
    val cell = radius * 2 / 3.4
    val y0 = coverY + side
    val transportY = h - max(bottom, 16.0) - 62
    val tunerY = (y0 + 97 + transportY) / 2 + 4

    /** 1 disc, 2 previous, 3 next, 4 play, 0 nothing. */
    fun target(x: Double, y: Double): Int {
        if (y >= coverY && y <= coverY + side) return 1
        if (abs(y - transportY) <= 40) {
            if (abs(x - cx) <= 40) return 4
            if (abs(x - (cx - 108)) <= 34) return 2
            if (abs(x - (cx + 108)) <= 34) return 3
        }
        return 0
    }
}

/** Text measured once per string, style and weight (at 1 px per dp; drawn inside the density scale). */
internal class TdrTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private val cache = HashMap<String, TextLayoutResult>()

    fun get(s: String, size: Float, weight: FontWeight, track: Float = 0f): TextLayoutResult {
        val key = "$s|$size|${weight.weight}|$track"
        return cache.getOrPut(key) {
            m.measure(
                s,
                TextStyle(color = Color.White, fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
                density = unit,
            )
        }
    }
}

private class TdrHolder {
    var frame = TdrFrame()
    var running = false
}

/**
 * The now-playing screen of a radio app whose tiled disc cover turns, slides its tiles and recolours
 * when the station changes.
 *
 * @param autoPlay plays the 9 s station-change loop until touched.
 */
@Composable
fun TileDiscRadio(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto, reduced) { TdrDriver() }
    val holder = remember { TdrHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { TdrTexts(measurer) }

    BoxWithConstraints(modifier.fillMaxSize().background(hex(TdrModel.STATIONS[0].frame)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            TdrGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            holder.frame = driver.frame(System.nanoTime() / 1e9, auto, reduced)
            tick.intValue += 1
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        holder.frame = driver.frame(now, auto, reduced)
                        tick.intValue += 1
                        !auto && driver.settled(now)
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        fun tap(button: Int) {
            if (driver.tap(button, now(), auto, reduced)) haptic()
            if (!holder.running) wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Now playing. Swipe the cover or use the buttons to change station."
                    customActions = listOf(
                        CustomAccessibilityAction("Next station") { tap(1); true },
                        CustomAccessibilityAction("Previous station") { tap(-1); true },
                        CustomAccessibilityAction("Play or pause") { tap(0); true },
                    )
                }
                .pointerInput(driver, g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        var target = g.target((down.position.x / d).toDouble(), (down.position.y / d).toDouble())
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (target == 1) {
                            if (reduced || !driver.beginDrag(now(), auto)) target = 0
                            if (!holder.running) wake.intValue += 1
                        }
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            if (target == 1 && driver.drag(((c.position.x - down.position.x) / d).toDouble(), g.radius, now())) haptic()
                            if (!c.pressed) break
                            c.consume()
                        }
                        val moved = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d)
                        when (target) {
                            1 -> {
                                if (driver.endDrag((tracker.calculateVelocity().x / d).toDouble(), now())) haptic()
                                if (!holder.running) wake.intValue += 1
                            }
                            2 -> if (moved < 12) tap(-1)
                            3 -> if (moved < 12) tap(1)
                            4 -> if (moved < 12) tap(0)
                        }
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                tdrDraw(g, f, texts)
            }
        }
    }
}

// Drawing (in dp).

private val blurPaint = android.graphics.Paint()

private fun DrawScope.tdrDraw(g: TdrGeo, f: TdrFrame, tx: TdrTexts) {
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    val page = if (f.reduced) TdrModel.fade(f.ct) else TdrModel.pageMix(f.ct)
    drawRect(mix(a.frame, b.frame, page), Offset.Zero, Size(g.w.f(), g.h.f()))
    header(g, f, tx)
    background(g, f)
    disc(g, f)
    // The disc's edge catches a little light at the top.
    drawCircle(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.12f)),
            startY = (g.discY - g.radius).f(),
            endY = (g.discY + g.radius).f(),
        ),
        (g.radius - 0.5).f(),
        Offset(g.cx.f(), g.discY.f()),
        style = Stroke(1f),
    )
    words(g, f, tx)
    titles(g, f, tx)
    tuner(g, f, tx)
    transport(g, f, page)
}

/** Draws a line of text vertically centred on y: starting at x (anchor −1) or centred on it (0). */
private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, color: Color, anchor: Int = -1) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    val left = if (anchor < 0) x.f() else x.f() - w / 2
    drawText(l, color = color, topLeft = Offset(left, y.f() - h / 2))
}

private fun DrawScope.header(g: TdrGeo, f: TdrFrame, tx: TdrTexts) {
    val y = g.top + 24
    text(tx.get("parquet.fm", 20f, FontWeight.ExtraBold, -0.4f), 20.0, y, hex(INK))
    val w = 84.0
    val x = g.w - 20 - w
    drawRoundRect(
        hex(INK, 0.35f), Offset(x.f() + 0.75f, (y - 14).f() + 0.75f), Size(w.f() - 1.5f, 26.5f),
        CornerRadius(13.25f), style = Stroke(1.5f),
    )
    // The dot breathes once a second (nine times per loop).
    val pulse = if (f.playing) 0.55 + 0.45 * cos(PI * f.clock).pow(2) else 1.0
    if (f.playing) {
        drawCircle(hex(LIVE_RED, (0.25 * (1 - pulse)).f()), 8f, Offset((x + 14).f(), y.f()))
        drawCircle(hex(LIVE_RED, pulse.f()), 4f, Offset((x + 14).f(), y.f()))
    } else {
        drawCircle(hex(INK, 0.45f), 4f, Offset((x + 14).f(), y.f()))
    }
    text(tx.get(if (f.playing) "LIVE" else "PAUSED", 12f, FontWeight.Bold, 1.2f), x + 26, y, hex(INK))
}

private val tilePath = Path()

/** Adds a cell's tiles to a path: rounded rects with a big radius on the masked corners. */
private fun addCell(path: Path, code: Int, x: Double, y: Double, size: Double, inset: Double, small: Double) {
    for (t in TdrModel.tiles(code)) {
        val l = x + t.x * size + inset
        val tp = y + t.y * size + inset
        val w = t.w * size - 2 * inset
        val h = t.h * size - 2 * inset
        val big = max(0.0, min(w, h) / 2)
        val sm = min(small, big)
        fun r(bit: Int) = CornerRadius((if (t.mask and bit != 0) big else sm).f())
        path.addRoundRect(
            RoundRect(
                l.f(), tp.f(), (l + w).f(), (tp + h).f(),
                topLeftCornerRadius = r(1), topRightCornerRadius = r(2),
                bottomRightCornerRadius = r(4), bottomLeftCornerRadius = r(8),
            ),
        )
    }
}

private fun DrawScope.background(g: TdrGeo, f: TdrFrame) {
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    for (j in 0 until 4) {
        for (i in 0 until 4) {
            val t = if (f.reduced) TdrModel.fade(f.ct) else TdrModel.bgMix(f.ct, i, j, f.ch.dir)
            tilePath.reset()
            addCell(tilePath, TdrModel.BG_LAYOUT[j * 4 + i], g.coverX + 7 + i * g.bgCell, g.coverY + 7 + j * g.bgCell, g.bgCell, 1.5, 6.0)
            drawPath(tilePath, mix(a.bgTile, b.bgTile, t))
        }
    }
}

/** A soft (blurred) circle, offset by dy. Android's blur radius is 1.5 × SwiftUI's. */
private fun DrawScope.blurredCircle(cx: Double, cy: Double, r: Double, color: Color, radius: Double, dy: Double) {
    blurPaint.reset()
    blurPaint.isAntiAlias = true
    blurPaint.color = color.toArgb()
    blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { it.nativeCanvas.drawCircle(cx.f(), (cy + dy).f(), r.f(), blurPaint) }
}

private val oldTiles = Path()
private val newTiles = Path()
private val discClip = Path()

private fun DrawScope.disc(g: TdrGeo, f: TdrFrame) {
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    // Two shadows: a soft ambient one and a tight contact one.
    blurredCircle(g.cx, g.discY, g.radius, Color.Black.copy(alpha = 0.22f), 18.0, 10.0)
    blurredCircle(g.cx, g.discY, g.radius, Color.Black.copy(alpha = 0.25f), 3.0, 2.0)

    discClip.reset()
    discClip.addOval(androidx.compose.ui.geometry.Rect(Offset(g.cx.f(), g.discY.f()), g.radius.f()))
    val gap = if (f.reduced) TdrModel.fade(f.ct) else TdrModel.gapMix(f.ct)
    val cell = g.cell
    clipPath(discClip) {
        drawPath(discClip, mix(a.line, b.line, gap))
        translate(g.cx.f(), g.discY.f()) {
            rotate(f.angle.f(), pivot = Offset.Zero) {
                oldTiles.reset()
                newTiles.reset()
                if (f.reduced) {
                    for (r in 0 until 4) for (c in 0 until 4) {
                        addCell(oldTiles, TdrModel.localCode(f.ch.from, f.ch.kFrom, c, r), (c - 2) * cell, (r - 2) * cell, cell, 1.5, 5.0)
                        addCell(newTiles, TdrModel.localCode(f.ch.to, f.ch.kTo, c, r), (c - 2) * cell, (r - 2) * cell, cell, 1.5, 5.0)
                    }
                    drawPath(oldTiles, hex(a.tile))
                    drawPath(newTiles, hex(b.tile), alpha = TdrModel.fade(f.ct).f())
                } else {
                    for (r in 0 until 4) {
                        val p = if (f.ch.dir == 0) 1.0 else TdrModel.rowP(f.ct, r)
                        val sgn = (if (r % 2 == 0) 1.0 else -1.0) * (if (f.ch.dir == 0) 1 else f.ch.dir)
                        val inset = 1.5 + 2.5 * sin(PI * p)
                        val y = (r - 2) * cell
                        for (c in 0 until 4) {
                            val x = (c - 2) * cell
                            if (p < 1) {
                                addCell(oldTiles, TdrModel.localCode(f.ch.from, f.ch.kFrom, c, r), x + sgn * 4 * cell * p, y, cell, inset, 5.0)
                            }
                            if (p > 0) {
                                addCell(newTiles, TdrModel.localCode(f.ch.to, f.ch.kTo, c, r), x - sgn * 4 * cell * (1 - p), y, cell, inset, 5.0)
                            }
                        }
                    }
                    drawPath(oldTiles, hex(a.tile))
                    drawPath(newTiles, hex(b.tile))
                }
            }
        }
    }
}

/** The poster's words: station, ×, call sign, frequency, volume (text, x and y as fractions of the cover). */
private fun posterWords(s: TdrStation): List<Triple<String, Double, Double>> {
    val parts = s.name.uppercase(Locale.US).split(" ")
    return listOf(
        Triple(parts[0], 0.05, 0.355),
        Triple(if (parts.size > 1) parts[1] else "", 0.05, 0.395),
        Triple("×", 0.29, 0.355),
        Triple(s.call, 0.52, 0.355),
        Triple(String.format(Locale.US, "%.1f FM", s.freq), 0.755, 0.355),
        Triple(s.vol, 0.80, 0.935),
    )
}

private fun DrawScope.words(g: TdrGeo, f: TdrFrame, tx: TdrTexts) {
    val size = max(10.0, g.side * 0.034).f()
    fun put(s: TdrStation, alpha: (Int) -> Double, rise: (Int) -> Double) {
        posterWords(s).forEachIndexed { n, w ->
            val al = alpha(n)
            if (al <= 0.001 || w.first.isEmpty()) return@forEachIndexed
            text(
                tx.get(w.first, size, FontWeight.Medium, 0.2f),
                g.coverX + w.second * g.side, g.coverY + w.third * g.side + rise(n), hex(COVER_INK, al.f()),
            )
        }
    }
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    if (f.ch.from == f.ch.to) {
        put(b, { 1.0 }, { 0.0 })
        return
    }
    if (f.reduced) {
        val t = TdrModel.fade(f.ct)
        put(a, { 1 - t }, { 0.0 })
        put(b, { t }, { 0.0 })
        return
    }
    val out = TdrModel.wordsOut(f.ct)
    put(a, { out }, { 0.0 })
    put(b, { n -> TdrModel.wordIn(f.ct, n) }, { n -> 6 * (1 - TdrModel.wordIn(f.ct, n)) })
}

private fun DrawScope.titles(g: TdrGeo, f: TdrFrame, tx: TdrTexts) {
    fun block(s: TdrStation, dx: Double, alpha: Double) {
        if (alpha <= 0.001) return
        val al = alpha.f()
        val over = "NOW ON AIR · ${s.call} ${String.format(Locale.US, "%.1f", s.freq)} FM"
        text(tx.get(over, 12f, FontWeight.SemiBold, 1.4f), 20 + dx, g.y0 + 34, hex(INK, 0.72f * al))
        text(tx.get(s.name, 32f, FontWeight.Bold, -0.6f), 20 + dx, g.y0 + 66, hex(INK, al))
        text(tx.get("${s.show} · ${s.listening} listening", 15f, FontWeight.Normal), 20 + dx, g.y0 + 97, hex(INK, 0.72f * al))
    }
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    if (f.ch.from == f.ch.to) {
        block(b, 0.0, 1.0)
        return
    }
    val dir = f.ch.dir.toDouble()
    if (f.reduced) {
        val t = TdrModel.fade(f.ct)
        block(a, 0.0, 1 - t)
        block(b, 0.0, t)
        return
    }
    val o = TdrModel.titleOut(f.ct)
    block(a, -20 * dir * o, 1 - o)
    val i = TdrModel.titleIn(f.ct)
    block(b, 24 * dir * (1 - i), i)
}

private fun DrawScope.tuner(g: TdrGeo, f: TdrFrame, tx: TdrTexts) {
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    val t = if (f.reduced) TdrModel.fade(f.ct) else TdrModel.freqMix(f.ct)
    val freq = TdrModel.lerp(a.freq, b.freq, t)
    val y = g.tunerY
    val half = g.w / 2 - 20
    val perMHz = 40.0
    val lo = floor((freq - half / perMHz) * 5).toInt() - 1
    val hi = ceil((freq + half / perMHz) * 5).toInt() + 1
    for (n in lo..hi) {
        val mhz = n / 5.0
        if (mhz < 87.5 || mhz > 108.0) continue
        val dx = (mhz - freq) * perMHz
        if (abs(dx) > half) continue
        val fade = 1 - (abs(dx) / half).pow(3)
        val major = n % 5 == 0
        val h = if (major) 16.0 else 8.0
        drawRect(
            hex(INK, ((if (major) 0.8 else 0.4) * fade).f()),
            Offset((g.cx + dx - 0.75).f(), (y + 8 - h).f()), Size(1.5f, h.f()),
        )
        if (major && (n / 5) % 2 == 0) {
            text(tx.get("${n / 5}", 11f, FontWeight.SemiBold), g.cx + dx, y + 24, hex(INK, (0.6 * fade).f()), anchor = 0)
        }
    }
    // The needle in the disc's tile colour.
    val needle = mix(a.tile, b.tile, t)
    drawRoundRect(needle, Offset((g.cx - 1).f(), (y - 20).f()), Size(2f, 32f), CornerRadius(1f))
    drawCircle(needle, 4f, Offset(g.cx.f(), (y - 20).f()))
}

private val skipPath = Path()

/** ⏭ (side +1) or ⏮ (side −1), centred on 0. */
private fun skip(side: Double): Path {
    skipPath.reset()
    for (k in 0..1) {
        val x0 = (-13 + k * 12) * side
        skipPath.moveTo(x0.f(), -8f)
        skipPath.lineTo((x0 + 12 * side).f(), 0f)
        skipPath.lineTo(x0.f(), 8f)
        skipPath.close()
    }
    val bx = if (side > 0) 11f else -13f
    skipPath.addRect(androidx.compose.ui.geometry.Rect(bx, -8f, bx + 2.5f, 8f))
    return skipPath
}

private fun DrawScope.transport(g: TdrGeo, f: TdrFrame, page: Double) {
    val a = TdrModel.STATIONS[f.ch.from]
    val b = TdrModel.STATIONS[f.ch.to]
    val y = g.transportY
    drawCircle(hex(INK), 36f, Offset(g.cx.f(), y.f()))
    val glyph = mix(a.frame, b.frame, page)
    if (f.playing) {
        drawRoundRect(glyph, Offset((g.cx - 9).f(), (y - 12).f()), Size(6f, 24f), CornerRadius(2f))
        drawRoundRect(glyph, Offset((g.cx + 3).f(), (y - 12).f()), Size(6f, 24f), CornerRadius(2f))
    } else {
        val p = Path()
        p.moveTo((g.cx - 8).f(), (y - 13).f())
        p.lineTo((g.cx + 13).f(), y.f())
        p.lineTo((g.cx - 8).f(), (y + 13).f())
        p.close()
        drawPath(p, glyph)
    }
    for (side in intArrayOf(-1, 1)) {
        val pressed = if (f.reduced || f.ch.press != side) 1.0 else TdrModel.press(f.ct)
        translate((g.cx + side * 108).f(), y.f()) {
            scale(pressed.f(), pressed.f(), pivot = Offset.Zero) {
                drawPath(skip(side.toDouble()), hex(INK))
            }
        }
    }
}
