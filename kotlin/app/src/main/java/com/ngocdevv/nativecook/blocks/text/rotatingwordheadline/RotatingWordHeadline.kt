package com.ngocdevv.nativecook.blocks.text.rotatingwordheadline

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MODE_AUTO = 0
private const val MODE_TOUCH = 1
private const val MODE_RETURN = 2

/** A drag of this many font sizes moves one word. */
private const val DRAG_PER_WORD = 1.15f

/** React Native's withSpring({ damping: 20, stiffness: 190, mass: 1 }), integrated by hand on the frame clock. */
private const val SPRING_DAMPING = 20f
private const val SPRING_STIFFNESS = 190f
private const val SPRING_MASS = 1f

private fun View.lightTap() = performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

/** System sans at weight 800 (React Native fontWeight '800'); bold below API 28. */
internal fun heavyTypeface(): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD

/**
 * Measured once per font size, like the React Native onLayout pass: every letter is its own text there, so a word is
 * as wide as the sum of its letters' advances plus the −3 % letter spacing after each of them.
 */
private class Fit(
    val fs: Float, // dp
    val letterX: List<FloatArray>, // px, per word
    val letterW: List<FloatArray>, // px, per word
    val widths: FloatArray, // px, per word
    val slotW: Float, // px, widest word
)

private fun measureFit(words: List<String>, tail: String, fontSize: Float, maxWidth: Float, density: Float, paint: Paint): Fit {
    var fs = fontSize
    var shrinks = 0
    while (true) {
        paint.textSize = fs * density
        paint.letterSpacing = 0f
        val ls = -0.03f * fs * density
        val xs = ArrayList<FloatArray>()
        val ws = ArrayList<FloatArray>()
        val widths = FloatArray(words.size)
        words.forEachIndexed { k, word ->
            val x = FloatArray(word.length)
            val w = FloatArray(word.length)
            var acc = 0f
            for (i in word.indices) {
                x[i] = acc
                w[i] = paint.measureText(word, i, i + 1) + ls
                acc += w[i]
            }
            xs.add(x)
            ws.add(w)
            widths[k] = acc
        }
        paint.letterSpacing = -0.03f
        val tailW = paint.measureText(tail)
        paint.letterSpacing = 0f
        val widest = widths.max()
        val line = (widest + fs * 0.27f * density + tailW) / density
        if (line > maxWidth + 0.5f && shrinks < 2) {
            // Shrink the font so the longest line fits; everything is measured again at the new size.
            fs = floor(fs * maxWidth / line)
            shrinks += 1
            continue
        }
        return Fit(fs, xs, ws, widths, widest)
    }
}

/** Everything the frame loop, the gesture and the draw share; only pos, clock and mode are snapshot state. */
private class Ctl(pos: MutableFloatState) {
    var posState = pos
    val clock = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(MODE_AUTO)
    var now = 0f
    var lastTouch = 0f
    var dragging = false
    var dragFrom = 0f
    var returnFrom = 0f
    var returnTo = 0f
    var returnAt = 0f
    var springOn = false
    var springTarget = 0f
    var springV = 0f
    var lastRound = Int.MIN_VALUE
    var auto = true
    var reduced = false

    var pos: Float
        get() = posState.floatValue
        set(v) {
            posState.floatValue = v
        }

    fun takeOver() {
        springOn = false
        lastTouch = now
        mode.intValue = MODE_TOUCH
    }

    fun springTo(target: Float, velocity: Float) {
        if (reduced) {
            springOn = false
            pos = target
            return
        }
        springTarget = target
        springV = velocity
        springOn = true
    }

    fun step(dt: Float) {
        if (!springOn) return
        var x = pos
        var v = springV
        val n = max(1, (dt / 0.002f).roundToInt())
        val h = dt / n
        repeat(n) {
            val a = (-SPRING_STIFFNESS * (x - springTarget) - SPRING_DAMPING * v) / SPRING_MASS
            v += a * h
            x += v * h
        }
        if (abs(x - springTarget) < 0.0005f && abs(v) < 0.005f) {
            x = springTarget
            v = 0f
            springOn = false
        }
        springV = v
        pos = x
    }
}

/**
 * The reusable piece: "<lead> / <word> <tail>" where the word rolls letter by letter on vertical wheels, its width
 * morphs so the tail slides along by transform, and a coloured underline stretches to match.
 *
 * Nothing recomposes per frame: the frame loop writes [progress] (and the clock), which only the draw lambda reads.
 */
@Composable
fun RotatingWordHeadline(
    words: List<String>,
    accents: List<Color>,
    lead: String,
    tail: String,
    fontSize: Dp,
    maxWidth: Dp,
    ink: Color,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    reducedMotion: Boolean = false,
    progress: MutableFloatState? = null,
) {
    val density = LocalDensity.current
    val d = density.density
    val view = LocalView.current
    val ownPos = remember { mutableFloatStateOf(0f) }
    val posState = progress ?: ownPos
    val ctl = remember { Ctl(posState) }
    val auto = autoPlay && !reducedMotion
    SideEffect {
        ctl.posState = posState
        ctl.auto = auto
        ctl.reduced = reducedMotion
    }

    val typeface = remember { heavyTypeface() }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            // Glyphs are drawn one by one: no ligatures, or "fi" would give all its width to the f.
            fontFeatureSettings = "liga 0, clig 0"
        }
    }
    val fit = remember(words, tail, fontSize, maxWidth, d) { measureFit(words, tail, fontSize.value, maxWidth.value, d, Paint(paint)) }
    val fs = fit.fs
    val slotH = fs * 1.26f
    val accentArgb = remember(accents) { accents.map { it.toArgb() } }
    val inkArgb = ink.toArgb()
    val lineH = fs * 1.2f

    LaunchedEffect(ctl) {
        var last = -1L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                last = nanos
                ctl.now += dt
                when (ctl.mode.intValue) {
                    MODE_AUTO -> if (ctl.auto) {
                        val t = (ctl.clock.floatValue + dt) % LOOP
                        ctl.clock.floatValue = t
                        ctl.pos = posAt(t)
                    }
                    MODE_TOUCH -> {
                        ctl.step(dt)
                        // Without autoplay the word stays where the finger left it.
                        if (ctl.auto && !ctl.dragging && ctl.now - ctl.lastTouch > IDLE) {
                            ctl.springOn = false
                            val p = ctl.pos
                            ctl.returnFrom = p
                            ctl.returnTo = Math.round(p / WORDS).toFloat() * WORDS
                            ctl.returnAt = ctl.now
                            ctl.mode.intValue = MODE_RETURN
                        }
                    }
                    else -> {
                        val u = min(1f, (ctl.now - ctl.returnAt) / RETURN_LEN)
                        ctl.pos = mix(ctl.returnFrom, ctl.returnTo, easeInOutCubic(u))
                        if (u >= 1f) {
                            // Back on the first word: the loop restarts from t = 0.
                            ctl.pos = 0f
                            ctl.clock.floatValue = 0f
                            ctl.mode.intValue = MODE_AUTO
                        }
                    }
                }
                // A light tick each time a new word settles under the finger.
                if (ctl.mode.intValue == MODE_TOUCH) {
                    val r = Math.round(ctl.pos)
                    if (ctl.lastRound != Int.MIN_VALUE && r != ctl.lastRound) view.lightTap()
                    ctl.lastRound = r
                } else {
                    ctl.lastRound = Int.MIN_VALUE
                }
            }
        }
    }

    val matrix = remember { Matrix() }
    val src = remember { FloatArray(8) }
    val dst = remember { FloatArray(8) }
    val fm = remember(fit) { Paint(paint).apply { textSize = fs * d }.fontMetrics }
    val linePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val ghostClip = remember { android.graphics.Path() }
    val ghostFill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val ghostStroke = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val ghostShadow = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // shadowRadius 10 → sigma 5 (the house cssBoxShadow reading of an iOS shadow).
                maskFilter = android.graphics.BlurMaskFilter(sigmaToRadius(5f * d), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    // One label for the whole line, following the word that shows (the nearest whole position, so it changes once
    // per word, not per frame); the lead's Text and the drawn letters are not read on their own.
    val current by remember(words.size) { derivedStateOf { wrapIndex(Math.round(ctl.pos), words.size) } }
    Column(
        modifier.clearAndSetSemantics {
            heading()
            contentDescription = "$lead ${words[current]} $tail"
        },
    ) {
        val fsSp = with(density) { fs.dp.toSp() }
        Text(
            text = lead,
            color = ink,
            style = TextStyle(
                fontSize = fsSp,
                lineHeight = with(density) { (fs * 1.12f).dp.toSp() },
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.03f).em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height((slotH + 14f).dp)
                .pointerInput(fit, reducedMotion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var failed = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(c.uptimeMillis, c.position)
                            val dx = (c.position.x - down.position.x) / d
                            val dy = (c.position.y - down.position.y) / d
                            if (!c.pressed) {
                                if (dragging) {
                                    ctl.dragging = false
                                    ctl.lastTouch = ctl.now
                                    val vy = tracker.calculateVelocity().y / d
                                    val v = -vy / (fs * DRAG_PER_WORD)
                                    val target = Math.round(ctl.pos + v * 0.12f).toFloat()
                                    ctl.springTo(target, v)
                                } else if (!failed && c.uptimeMillis - down.uptimeMillis <= 400) {
                                    ctl.takeOver()
                                    ctl.springTo(Math.round(ctl.pos).toFloat() + 1f, 0f)
                                }
                                break
                            }
                            if (!dragging && !failed) {
                                if (abs(dx) > 24f) {
                                    failed = true
                                } else if (abs(dy) > 8f) {
                                    dragging = true
                                    ctl.takeOver()
                                    ctl.dragging = true
                                    ctl.dragFrom = ctl.pos
                                }
                            }
                            if (dragging) {
                                c.consume()
                                ctl.lastTouch = ctl.now
                                ctl.pos = ctl.dragFrom - dy / (fs * DRAG_PER_WORD)
                            }
                        }
                        if (ctl.dragging) {
                            ctl.dragging = false
                            ctl.lastTouch = ctl.now
                        }
                    }
                }
                .graphicsLayer()
                .drawBehind {
                    val p = ctl.pos
                    val a = wrapIndex(floor(p).toInt(), WORDS)
                    val b = wrapIndex(floor(p).toInt() + 1, WORDS)
                    val slotWpx = fit.slotW
                    val slotHpx = slotH * d
                    val lhPx = lineH * d
                    val reduced = ctl.reduced
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        // ---- the slot: four words stacked, each letter on its own wheel, clipped to the widest word ----
                        nc.save()
                        nc.clipRect(0f, 0f, slotWpx, slotHpx)
                        paint.textSize = fs * d
                        paint.letterSpacing = 0f
                        paint.color = inkArgb
                        val top = fs * 0.03f * d
                        val baseline = top + lhPx / 2f - (fm.ascent + fm.descent) / 2f
                        val persp = fs * 9f * d
                        for (k in words.indices) {
                            val word = words[k]
                            val xs = fit.letterX[k]
                            val ws = fit.letterW[k]
                            for (i in word.indices) {
                                val w = letterWheel(p, k, i)
                                val alpha = 1f - easeInOutCubic(min(1f, abs(w) * 1.15f))
                                if (alpha <= 0.002f) continue
                                val left = xs[i]
                                val lw = ws[i]
                                val cx = left + lw / 2f
                                val cy = top + lhPx / 2f
                                val ty = if (reduced) 0f else w * fs * ROLL_RISE * d
                                val turn = if (reduced) 0f else -w * ROLL_DEG
                                nc.save()
                                if (ty != 0f || turn != 0f) {
                                    // React Native: [perspective, translateY, rotateX] about the letter's centre.
                                    val th = Math.toRadians(turn.toDouble())
                                    val c = cos(th).toFloat()
                                    val s = sin(th).toFloat()
                                    val hw = lw / 2f
                                    val hh = lhPx / 2f
                                    var j = 0
                                    for (cy0 in floatArrayOf(-hh, hh)) {
                                        for (cx0 in if (cy0 < 0) floatArrayOf(-hw, hw) else floatArrayOf(hw, -hw)) {
                                            src[j] = cx + cx0
                                            src[j + 1] = cy + cy0
                                            val sc = persp / (persp - cy0 * s)
                                            dst[j] = cx + cx0 * sc
                                            dst[j + 1] = cy + (cy0 * c + ty) * sc
                                            j += 2
                                        }
                                    }
                                    matrix.setPolyToPoly(src, 0, dst, 0, 4)
                                    nc.concat(matrix)
                                }
                                paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                                nc.drawText(word, i, i + 1, left, baseline, paint)
                                nc.restore()
                            }
                        }
                        paint.alpha = 255
                        nc.restore()

                        // ---- underline: as wide as the widest word, scaled to the live width, colour between accents ----
                        val uLine = morphAmount(p, LINE_LAG)
                        val wLine = mix(fit.widths[a], fit.widths[b], uLine)
                        val full = max(1f, slotWpx)
                        val lh = max(5f, fs * 0.13f) * d
                        val lTop = slotHpx + 2f * d
                        linePaint.color = lerpRgb(accentArgb[a], accentArgb[b], uLine)
                        nc.save()
                        nc.translate(-(full - wLine) / 2f, 0f)
                        nc.scale(wLine / full, 1f, full / 2f, lTop + lh / 2f)
                        val r = min(fs * d, lh / 2f)
                        nc.drawRoundRect(0f, lTop, full, lTop + lh, r, r, linePaint)
                        nc.restore()

                        // ---- tail: follows the slot's live width by translation only ----
                        val wSlot = mix(fit.widths[a], fit.widths[b], morphAmount(p, WIDTH_LAG))
                        val tTop = (slotHpx - lhPx) / 2f
                        paint.letterSpacing = -0.03f
                        nc.drawText(tail, wSlot + fs * 0.27f * d, tTop + lhPx / 2f - (fm.ascent + fm.descent) / 2f, paint)
                        paint.letterSpacing = 0f

                        // ---- ghost finger (autoplay only) ----
                        if (ctl.mode.intValue == MODE_AUTO && ctl.auto) {
                            val t = ctl.clock.floatValue
                            val go = ghostOpacity(t) * 0.9f
                            if (go > 0.002f) {
                                val gs = ghostScale(t)
                                val ga = wrapIndex(Math.round(p), WORDS)
                                val gx = fit.widths[ga] * 0.5f
                                val gy = slotHpx / 2f
                                val rad = 22f * d
                                nc.save()
                                nc.scale(gs, gs, gx, gy)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    // Outside the dot only, as an iOS shadow under a translucent view reads.
                                    nc.save()
                                    ghostClip.reset()
                                    ghostClip.addCircle(gx, gy, rad, android.graphics.Path.Direction.CW)
                                    nc.clipOutPath(ghostClip)
                                    ghostShadow.color = android.graphics.Color.argb((0.18f * go * 255f).roundToInt(), 0x2a, 0x24, 0x1c)
                                    nc.drawCircle(gx, gy + 4f * d, rad, ghostShadow)
                                    nc.restore()
                                }
                                ghostFill.color = android.graphics.Color.argb((0.55f * go * 255f).roundToInt(), 255, 255, 255)
                                nc.drawCircle(gx, gy, rad, ghostFill)
                                ghostStroke.strokeWidth = 1f * d
                                ghostStroke.color = android.graphics.Color.argb((0.18f * go * 255f).roundToInt(), 40, 36, 30)
                                nc.drawCircle(gx, gy, rad - 0.5f * d, ghostStroke)
                                nc.restore()
                            }
                        }
                    }
                },
        )
    }
}

/** React Native's interpolateColor: straight RGB(A) blend in sRGB. */
internal fun lerpRgb(a: Int, b: Int, u: Float): Int {
    fun ch(x: Int, y: Int) = (x + (y - x) * u).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(
        ch(a ushr 24, b ushr 24),
        ch((a shr 16) and 0xff, (b shr 16) and 0xff),
        ch((a shr 8) and 0xff, (b shr 8) and 0xff),
        ch(a and 0xff, b and 0xff),
    )
}
