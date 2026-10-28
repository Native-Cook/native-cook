package com.ngocdevv.nativecook.blocks.lists.newspaperpilereader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Newspaper Pile Reader — the morning digest of an invented news app, as a pile of folded papers
 (a port of NewspaperPileReader.swift).

 Look: a warm white page, a date pill, and six invented papers (mastheads, headlines, columns, photos,
 grain, fold and a curled corner, all drawn in code once into bitmaps) piled down the screen, each a
 little rotated and offset, each lower one in front. Bottom: a dark tab pill and a pile / reader toggle.

 Hero (object, "lift"): a paper is picked out of the pile. It dips, lifts (its shadow grows), straightens
 and grows to reading size in the middle of the screen while the others are tossed out to the edges with
 a stagger; the paper's headline and summary fade in under it. Putting it back reverses it: the paper
 settles into its slot and the others slide in over it, re-forming the pile.

 autoPlay: LOOP = 9 s. Rest; a riffle wave runs down the pile (1.0 s); rest; the second paper is picked
 (2.6 s), read (3.5–6.0 s), put back (6.0–7.0 s); rest to 9 s. Every value is a pure function of the
 loop time and both ends of the loop are the pile at rest.
 Touch: tap a paper to open it; in the reader swipe sideways to page, swipe down or tap to put it back;
 drag the pile sideways to riffle it. 2 s after the last touch it goes back to the pile and the loop restarts.
 Reduced motion: no riffle and no flight; opening and closing crossfade; the loop is off.
*/

private const val BG = 0xF6F5F1L
private const val INK = 0x1C1B19L
private const val GREY = 0x9C9A92L

private fun hex(v: Long, a: Double = 1.0) = Color(
    red = ((v shr 16) and 0xFF) / 255f,
    green = ((v shr 8) and 0xFF) / 255f,
    blue = (v and 0xFF) / 255f,
    alpha = min(max(a, 0.0), 1.0).toFloat(),
)

/** Two colours mixed in sRGB. */
private fun mix(a: Long, b: Long, t: Double): Color {
    fun ch(v: Long, s: Int) = ((v shr s) and 0xFF) / 255.0
    return Color(
        red = NprModel.lerp(ch(a, 16), ch(b, 16), t).toFloat(),
        green = NprModel.lerp(ch(a, 8), ch(b, 8), t).toFloat(),
        blue = NprModel.lerp(ch(a, 0), ch(b, 0), t).toFloat(),
    )
}

private fun Double.f() = toFloat()

/** Text measured once per string and style (at 1 px per dp; drawn inside the density scale). */
private class NprTexts(private val m: TextMeasurer, private val serif: FontFamily) {
    private val unit = Density(1f, 1f)
    private val cache = HashMap<String, TextLayoutResult>()

    fun ui(s: String, size: Float, weight: FontWeight, track: Float = 0f): TextLayoutResult =
        cache.getOrPut("u|$s|$size|${weight.weight}|$track") {
            m.measure(
                s,
                TextStyle(color = Color.White, fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
                density = unit,
            )
        }

    fun title(s: String, width: Int): TextLayoutResult = cache.getOrPut("t|$s|$width") {
        m.measure(
            s,
            TextStyle(color = Color.White, fontSize = 26.sp, fontFamily = serif),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = width),
            density = unit,
        )
    }

    fun summary(s: String, width: Int): TextLayoutResult = cache.getOrPut("s|$s|$width") {
        m.measure(
            s,
            TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 21.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = width),
            density = unit,
        )
    }
}

private class NprHolder {
    var frame = NprFrame()
    var running = false
}

/**
 * The morning digest of a news app as a pile of folded papers: tap one to lift it out and read it.
 *
 * @param autoPlay plays the 9 s pick-and-put-back loop until touched.
 */
@Composable
fun NewspaperPileReader(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto, reduced) { NprDriver() }
    val holder = remember { NprHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val fonts = remember(context) { NprFonts(context.assets) }
    val serif = remember(context) {
        FontFamily(Font("newspaperpilereader/newspaperpilereader_DMSerifDisplay-Regular.ttf", context.assets))
    }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer, serif) { NprTexts(measurer, serif) }
    val density = screen.density
    val images by produceState<NprImages?>(null, fonts, density) {
        value = withContext(Dispatchers.Default) { NprArt.make(fonts, density) }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(hex(BG)).clipToBounds()) {
        val g = remember(maxWidth, maxHeight, safeTop, safeBottom) {
            NprGeo(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeTop.toDouble(), safeBottom.toDouble())
        }

        // The loop clock starts once the papers are drawn, so the first visible frame is the rest state.
        val ready = images != null
        LaunchedEffect(driver, g, auto, reduced, wake.intValue, ready) {
            if (!ready) return@LaunchedEffect
            holder.frame = driver.frame(System.nanoTime() / 1e9, auto, reduced, g)
            tick.intValue += 1
            holder.running = true
            try {
                while (true) {
                    val idle = withFrameNanos { n ->
                        val now = n / 1e9
                        holder.frame = driver.frame(now, auto, reduced, g)
                        tick.intValue += 1
                        !auto && !driver.live(now)
                    }
                    if (idle) break
                }
            } finally {
                holder.running = false
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        fun wakeUp() {
            if (!holder.running) wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Today's papers. Tap a paper to read it, swipe it down to put it back."
                    customActions = listOf(
                        CustomAccessibilityAction("Open the next paper") {
                            driver.accessibilityToggle(now(), auto, g)
                            wakeUp()
                            true
                        },
                    )
                }
                .pointerInput(driver, g, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val sx = (down.position.x / d).toDouble()
                        val sy = (down.position.y / d).toDouble()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (driver.change(sx, sy, 0.0, 0.0, 0.0, 0.0, now(), auto, reduced, g)) haptic()
                        wakeUp()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            if (!c.pressed) break
                            val v = tracker.calculateVelocity()
                            val dx = ((c.position.x - down.position.x) / d).toDouble()
                            val dy = ((c.position.y - down.position.y) / d).toDouble()
                            if (driver.change(sx, sy, dx, dy, (v.x / d).toDouble(), (v.y / d).toDouble(), now(), auto, reduced, g)) haptic()
                            c.consume()
                        }
                        val v = tracker.calculateVelocity()
                        val dx = ((last.x - down.position.x) / d).toDouble()
                        val dy = ((last.y - down.position.y) / d).toDouble()
                        if (driver.end(
                                (last.x / d).toDouble(), (last.y / d).toDouble(), dx, dy,
                                (v.x / d).toDouble(), (v.y / d).toDouble(), now(), auto, g,
                            )
                        ) haptic()
                        wakeUp()
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            val img = images
            scale(density, density, pivot = Offset.Zero) {
                header(g, texts)
                if (img != null) papers(f, img)
                chrome(g, f, img)
                caption(g, f, texts)
            }
        }
    }
}

// Drawing (in dp).

private val bitmapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

/** Draws a bitmap in the rect with an opacity (the paint's alpha is set on every call). */
private fun DrawScope.bitmap(b: Bitmap, x: Double, y: Double, w: Double, h: Double, alpha: Double) {
    if (alpha <= 0.001) return
    bitmapPaint.alpha = (min(alpha, 1.0) * 255 + 0.5).toInt()
    drawIntoCanvas {
        it.nativeCanvas.drawBitmap(b, null, RectF(x.f(), y.f(), (x + w).f(), (y + h).f()), bitmapPaint)
    }
}

/** Draws a line of text vertically centred on y: starting at x (anchor −1) or centred on it (0). */
private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, color: Color, anchor: Int = -1) {
    val w = l.size.width.toFloat()
    val h = l.size.height.toFloat()
    val left = if (anchor < 0) x.f() else x.f() - w / 2
    drawText(l, color = color, topLeft = Offset(left, y.f() - h / 2))
}

private fun DrawScope.header(g: NprGeo, tx: NprTexts) {
    drawRoundRect(hex(0xECEAE4), Offset(16f, (g.top + 8).f()), Size((g.w - 32).f(), 44f), CornerRadius(22f))
    text(tx.ui("Tuesday, June 4", 22f, FontWeight.Normal, -0.3f), 34.0, g.top + 30, hex(INK))
    val x = g.w - 42 - 9
    val y = g.top + 8 + 22 - 8.5
    val ic = hex(0x6E6C66)
    drawRoundRect(ic, Offset((x + 0.75).f(), (y + 2.75).f()), Size(16.5f, 13.5f), CornerRadius(2.25f), style = Stroke(1.5f))
    drawRect(ic, Offset(x.f(), (y + 6).f()), Size(18f, 1.5f))
    for (k in doubleArrayOf(5.0, 13.0)) {
        drawRoundRect(ic, Offset((x + k - 0.75).f(), y.f()), Size(1.5f, 4.5f), CornerRadius(0.75f))
    }
    text(tx.ui("Last update · Today 7:30 AM · 6 papers", 12f, FontWeight.Normal), g.cx, g.top + 72, hex(GREY), 0)
}

private fun DrawScope.papers(f: NprFrame, img: NprImages) {
    for (i in f.sheets.indices) sheet(f.sheets[i], img.papers[i], img, f.papersAlpha)
    if (f.raised >= 0 && f.raisedAlpha > 0.001) {
        sheet(f.sheets[f.raised], img.papers[f.raised], img, f.raisedAlpha * f.papersAlpha)
    }
}

private fun DrawScope.sheet(s: NprSheet, paper: Bitmap, img: NprImages, alpha: Double) {
    if (alpha <= 0.001) return
    val lift = min(max(s.lift, 0.0), 1.2)
    val w = NprModel.PAPER_W
    val h = NprModel.PAPER_H
    // Ambient shadow: grows, drops and darkens a little as the paper lifts.
    val ap = NprArt.AMBIENT_PAD
    val ak = s.scale * NprModel.lerp(1.0, 1.06, lift)
    withTransform({
        translate(s.x.f(), (s.y + NprModel.lerp(8.0, 26.0, lift)).f())
        rotate(s.rot.f(), Offset.Zero)
        scale(ak.f(), ak.f(), Offset.Zero)
    }) {
        bitmap(img.ambient, -w / 2 - ap, -h / 2 - ap, w + 2 * ap, h + 2 * ap, alpha * NprModel.lerp(0.12, 0.22, lift))
    }
    // Contact shadow: tight, fades as the paper leaves the pile.
    val cp = NprArt.CONTACT_PAD
    withTransform({
        translate(s.x.f(), (s.y + 1.5).f())
        rotate(s.rot.f(), Offset.Zero)
        scale(s.scale.f(), s.scale.f(), Offset.Zero)
    }) {
        bitmap(img.contact, -w / 2 - cp, -h / 2 - cp, w + 2 * cp, h + 2 * cp, alpha * NprModel.lerp(0.22, 0.06, min(lift, 1.0)))
    }
    // The paper.
    val pad = NprArt.PAD
    withTransform({
        translate(s.x.f(), s.y.f())
        rotate(s.rot.f(), Offset.Zero)
        scale(s.scale.f(), s.scale.f(), Offset.Zero)
    }) {
        bitmap(paper, -w / 2 - pad, -h / 2 - pad, w + 2 * pad, h + 2 * pad, alpha)
    }
}

/** The fog over the pile's foot, the page dashes and the bottom bar. */
private fun DrawScope.chrome(g: NprGeo, f: NprFrame, img: NprImages?) {
    val fogY = g.h - 210
    drawRect(
        Brush.verticalGradient(
            0f to hex(BG, 0.0), (90f / 210f) to hex(BG, 0.92), 1f to hex(BG, 1.0),
            startY = fogY.f(), endY = g.h.f(),
        ),
        Offset(0f, fogY.f()), Size(g.w.f(), 210f),
    )

    // Page dashes.
    if (f.rows[3] > 0.001) {
        val n = NprModel.PAPERS.size
        val total = (n - 1) * 20.0 + 28
        var x = g.cx - total / 2
        for (i in 0 until n) {
            val on = max(0.0, 1 - abs(i - f.page))
            val wd = 14 + 14 * on
            drawRoundRect(
                mix(0xCFCCC4, INK, on), Offset(x.f(), (g.dashY + f.rise[3] - 1.5).f()), Size(wd.f(), 3f),
                CornerRadius(1.5f), alpha = f.rows[3].f(),
            )
            x += wd + 6
        }
    }

    // Tab pill.
    val y = g.barY
    drawRoundRect(hex(0x161513), Offset(20f, (y - 28).f()), Size(150f, 56f), CornerRadius(28f))
    val on = hex(0xF4F3EE)
    val off = hex(0x8A8780)
    drawRoundRect(on, Offset(49f, (y - 7).f()), Size(18f, 13f), CornerRadius(2.5f), style = Stroke(1.6f))
    drawLine(on, Offset(52f, (y - 10.5).f()), Offset(64f, (y - 10.5).f()), 1.6f)
    drawCircle(on, 1.5f, Offset(58f, (y + 13.5).f()))
    val mark = Path().apply {
        moveTo(89f, (y - 9).f())
        lineTo(101f, (y - 9).f())
        lineTo(101f, (y + 9).f())
        lineTo(95f, (y + 4).f())
        lineTo(89f, (y + 9).f())
        close()
    }
    drawPath(mark, off, style = Stroke(1.6f, join = StrokeJoin.Round))
    for ((k, kx) in listOf(-4.0 to -3.0, 4.0 to 3.0)) {
        drawLine(off, Offset(124f, (y + k).f()), Offset(140f, (y + k).f()), 1.6f)
        drawCircle(off, 2.5f, Offset((132 + kx * 1.5).f(), (y + k).f()), style = Stroke(1.6f))
    }

    // Mode toggle: reader (three dots on a line) | pile (three lines).
    val mx = g.w - 20 - 108
    if (img != null) {
        val tp = NprArt.TOGGLE_PAD
        bitmap(img.toggleShadow, mx - tp, y - 28 + 6 - tp, 108 + 2 * tp, 56 + 2 * tp, 0.10)
    }
    drawRoundRect(Color.White, Offset(mx.f(), (y - 28).f()), Size(108f, 56f), CornerRadius(28f))
    val hx = NprModel.lerp(mx + 78, mx + 30, f.toggle)
    drawCircle(hex(0xECEAE4), 22f, Offset(hx.f(), y.f()))
    val readerCol = mix(0x8A8780, INK, f.toggle)
    val pileCol = mix(INK, 0x8A8780, f.toggle)
    drawLine(readerCol, Offset((mx + 20).f(), y.f()), Offset((mx + 40).f(), y.f()), 1.2f)
    for (k in doubleArrayOf(-8.0, 0.0, 8.0)) drawCircle(readerCol, 3f, Offset((mx + 30 + k).f(), y.f()))
    for (k in doubleArrayOf(-6.0, 0.0, 6.0)) {
        drawRoundRect(pileCol, Offset((mx + 69).f(), (y + k - 1).f()), Size(18f, 2f), CornerRadius(1f))
    }
}

private fun DrawScope.caption(g: NprGeo, f: NprFrame, tx: NprTexts) {
    if (f.rows[0] <= 0.001 && f.rows[1] <= 0.001 && f.rows[2] <= 0.001) return
    val p = NprModel.PAPERS[f.caption]
    val w = (g.w - 48).toInt()
    val x = 24f
    var y = (g.captionTop - 8).f()
    val kicker = tx.ui(p.kicker, 11f, FontWeight.SemiBold, 1.1f)
    drawText(kicker, color = hex(0x8F8C84), topLeft = Offset(x, y + f.rise[0].f()), alpha = f.rows[0].f())
    y += kicker.size.height + 8
    val title = tx.title(p.title, w)
    drawText(title, color = hex(INK), topLeft = Offset(x, y + f.rise[1].f()), alpha = f.rows[1].f())
    y += title.size.height + 10
    val summary = tx.summary(p.summary, w)
    drawText(summary, color = hex(0x6B6960), topLeft = Offset(x, y + f.rise[2].f()), alpha = f.rows[2].f())
}

/** Holds the touch state and turns it, or the loop, into an [NprState] for each frame. */
internal class NprDriver {
    private val m = NprModel
    private var autoStart: Double? = null
    private var manual = false
    private var s = NprState()
    private var lastTouch = -1e9
    private var mode = 0 // 0 undecided, 1 riffle, 2 page, 3 down, 4 ignored
    private var touching = false
    private var pageBase = 0.0
    private var lastFrame = NprFrame()

    private fun isOpen() = s.openAt != null && s.closeAt == null

    /** True while something still moves after a touch (keeps the clock running without autoPlay). */
    fun live(now: Double): Boolean {
        if (mode != 0 || touching) return true
        if (now - lastTouch < NprModel.IDLE_BEFORE_BACK + 1.5) return true
        val b = s.closeAt
        if (b != null && now - b < 1.2) return true
        return false
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean, geo: NprGeo): NprFrame {
        s.reduced = reduced
        if (auto && manual && mode == 0 && !touching && now - lastTouch > NprModel.IDLE_BEFORE_BACK) {
            if (isOpen()) {
                close(now)
            } else if (settled(now)) {
                manual = false
                autoStart = now
            }
        }
        val f = if (auto && !manual) {
            val start = autoStart ?: now.also { autoStart = it }
            val tau = (now - start) % NprModel.LOOP
            val a = m.autoState()
            a.reduced = reduced
            m.frame(a, tau, geo)
        } else {
            m.frame(s, now, geo)
        }
        lastFrame = f
        return f
    }

    private fun settled(now: Double): Boolean {
        val b = s.closeAt
        if (b != null && now - b < 1.0) return false
        if (now - s.riffleAt < 0.7 || now - s.pageAt < 0.6 || now - s.dragAt < 0.5) return false
        val a = s.autoRiffle
        if (a != null && now - a < 1.0) return false
        return true
    }

    /** Takes over from the loop where it is, so nothing jumps. */
    private fun capture(now: Double, auto: Boolean) {
        val start = autoStart
        if (!auto || manual || start == null) {
            manual = true
            return
        }
        manual = true
        val tau = (now - start) % NprModel.LOOP
        val shift = now - tau
        val a = NprState()
        a.reduced = s.reduced
        a.autoRiffle = NprModel.RIFFLE_AT + shift
        if (tau >= NprModel.OPEN_AT) a.openAt = NprModel.OPEN_AT + shift
        if (tau >= NprModel.CLOSE_AT) a.closeAt = NprModel.CLOSE_AT + shift
        s = a
    }

    private fun open(i: Int, now: Double) {
        val keep = s
        s = NprState().apply {
            reduced = keep.reduced
            riffleFromDx = keep.riffleFromDx
            riffleFromRot = keep.riffleFromRot
            riffleFromLift = keep.riffleFromLift
            riffleAt = keep.riffleAt
            autoRiffle = keep.autoRiffle
            openAt = now
            sel = i
            closeSel = i
            pageFrom = i.toDouble()
            pageTo = i.toDouble()
        }
    }

    private fun close(now: Double) {
        val p = m.page(s, now)
        s.closeDrag = m.dragOffset(s, now)
        s.dragLive = null
        s.pageLive = null
        s.pageFrom = p
        s.pageTo = p
        s.closeSel = min(max(m.roundHalfAway(p).toInt(), 0), NprModel.PAPERS.size - 1)
        s.closeAt = now
    }

    /** Returns true when a haptic should play. */
    fun change(
        startX: Double, startY: Double, dx: Double, dy: Double, vx: Double, vy: Double,
        now: Double, auto: Boolean, reduced: Boolean, geo: NprGeo,
    ): Boolean {
        if (!touching) {
            touching = true
            capture(now, auto)
        }
        lastTouch = now
        if (mode == 0) {
            if (hypot(dx, dy) <= 8) return false
            mode = if (isOpen()) {
                if (abs(dx) > abs(dy)) {
                    pageBase = m.page(s, now)
                    2
                } else if (dy > 0) 3 else 4
            } else if (s.closeAt == null || settled(now)) {
                if (abs(dx) > abs(dy) && !reduced) {
                    s.riffleOn = true
                    s.riffleY = startY
                    1
                } else 4
            } else 4
        }
        when (mode) {
            1 -> {
                s.riffleDx = dx
                s.riffleVx = vx
            }
            2 -> {
                val n = (NprModel.PAPERS.size - 1).toDouble()
                var p = pageBase - dx / NprModel.PAGE_STRIDE
                if (p < 0) p = -(-p) / (1 + (-p) * 2)
                if (p > n) p = n + (p - n) / (1 + (p - n) * 2)
                s.pageLive = p
            }
            3 -> s.dragLive = if (dy > 0) dy / (1 + dy / 400) else 0.0
        }
        return false
    }

    fun end(
        x: Double, y: Double, dx: Double, dy: Double, vx: Double, vy: Double,
        now: Double, auto: Boolean, geo: NprGeo,
    ): Boolean {
        lastTouch = now
        if (!touching) capture(now, auto)
        val mode0 = mode
        mode = 0
        touching = false
        val moved = hypot(dx, dy)
        when (mode0) {
            1 -> {
                for (i in 0 until NprModel.PAPERS.size) {
                    val r = m.fingerRiffle(s, i, geo)
                    s.riffleFromDx[i] = r[0]
                    s.riffleFromRot[i] = r[1]
                    s.riffleFromLift[i] = r[2]
                }
                s.riffleOn = false
                s.riffleAt = now
                return false
            }
            2 -> {
                val p = s.pageLive ?: m.page(s, now)
                val from = m.roundHalfAway(pageBase).toInt()
                var target = m.roundHalfAway(p - vx / NprModel.PAGE_STRIDE * 0.25)
                target = min(max(target, (from - 1).toDouble()), (from + 1).toDouble())
                target = min(max(target, 0.0), (NprModel.PAPERS.size - 1).toDouble())
                s.pageLive = null
                s.pageFrom = p
                s.pageTo = target
                s.pageAt = now
                return target.toInt() != from
            }
            3 -> {
                val d = s.dragLive ?: 0.0
                if (d > 90 || vy > 700) {
                    close(now)
                    return true
                }
                s.dragLive = null
                s.dragFrom = d
                s.dragAt = now
                return false
            }
            4 -> return false
            else -> {
                if (moved >= 10) return false
                if (isOpen()) {
                    close(now)
                    return true
                }
                val b = s.closeAt
                if (b != null && now - b < 0.9) return false
                val i = NprModel.hit(lastFrame, x, y)
                if (i >= 0) {
                    open(i, now)
                    return true
                }
                return false
            }
        }
    }

    fun accessibilityToggle(now: Double, auto: Boolean, geo: NprGeo) {
        capture(now, auto)
        lastTouch = now
        if (isOpen()) close(now) else open(1, now)
    }
}
