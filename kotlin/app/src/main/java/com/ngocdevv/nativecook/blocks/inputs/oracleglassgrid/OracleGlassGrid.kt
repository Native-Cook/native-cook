package com.ngocdevv.nativecook.blocks.inputs.oracleglassgrid

import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Oracle Glass Grid — the "ask the cards" screen of Sibyl, an invented oracle app (a port of OracleGlassGrid.swift).

 Look: a live lava gradient (purple, maroon, deep green, teal; an AGSL shader on API 33+, blurred blobs below)
 behind a 3 × 3 grid of frosted glass cards, each with a thin glowing line icon drawn in code that shimmers,
 under "What answers do you seek?" and above an "ASK FOR ME" pill.
 Hero: the chosen card turns solid white while the other eight gather into a stack behind it; the stack rides
 to the centre and the others vanish; the white card flips about Y until it is edge-on, a glowing line that
 shrinks into a caret, and the caret opens sideways into the question field "Tap to type". The return plays
 it backwards and the grid re-deals from the stack with a small overshoot.

 autoPlay: LOOP = 10 s (OggModel.frame); picks Career, types "Should I take the leap?", folds back.
 Touch: tap any card to open it into the field; tap the back arrow or swipe down to fold it back. A touch
 during the story takes it over where it is. 2 s after the last touch an open field folds back and, at rest,
 the loop starts again.
 Reduced motion: no loop, lava and icons still; a tap crossfades between the grid and the field.
*/

private fun Double.f() = toFloat()

private fun oggHex(hex: Int, a: Double = 1.0) = Color(
    red = ((hex shr 16) and 0xFF) / 255f,
    green = ((hex shr 8) and 0xFF) / 255f,
    blue = (hex and 0xFF) / 255f,
    alpha = a.coerceIn(0.0, 1.0).f(),
)

private fun oggMix(a: Int, b: Int, t: Double): Int {
    fun ch(v: Int, s: Int) = ((v shr s) and 0xFF).toDouble()
    fun m(s: Int) = (Math.round(ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt() and 0xFF) shl s
    return m(16) or m(8) or m(0)
}

private const val LAVA_SKSL = """
uniform float2 size;
uniform float phase;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float sm(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
float blob(float2 p, float bx, float by, float r, float ry, float k, float ph, float s, float a) {
    float2 c = float2(bx + r * cos(k * a + ph), by + r * ry * sin(k * a + ph));
    float2 d = p - c;
    return exp(-dot(d, d) / (s * s));
}

half4 main(float2 position) {
    float a = phase;
    float2 u = (position - 0.5 * size) / size.y * 2.0;
    float2 p = u + 0.10 * float2(sin(u.y * 3.1 + a + 0.4), cos(u.x * 2.7 - a + 1.1));
    float f = blob(p, -0.30, -0.62, 0.22, 0.8, 1.0, 0.0, 0.30, a)
            + blob(p, 0.28, -0.10, 0.20, 0.8, -1.0, 1.7, 0.34, a)
            + blob(p, -0.20, 0.42, 0.24, 0.8, 1.0, 3.1, 0.32, a)
            + blob(p, 0.30, 0.78, 0.20, 0.8, 2.0, 4.4, 0.26, a)
            + blob(p, 0.05, 0.05, 0.30, 0.8, -1.0, 5.5, 0.22, a);
    float g = blob(p, -0.55, -0.95, 0.18, 1.0, 1.0, 2.2, 0.36, a)
            + blob(p, 0.62, 0.35, 0.22, 1.0, -1.0, 0.6, 0.32, a)
            + blob(p, -0.58, 0.95, 0.15, 1.0, 1.0, 4.0, 0.30, a);
    float3 purple = float3(0.643, 0.400, 0.769);
    float3 plum = float3(0.550, 0.340, 0.660);
    float3 maroon = float3(0.430, 0.230, 0.270);
    float3 green = float3(0.114, 0.227, 0.169);
    float3 teal = float3(0.270, 0.640, 0.620);
    float3 col = mix(purple, plum, sm(0.05, 0.45, f));
    col = mix(col, maroon, sm(0.22, 0.70, f));
    col = mix(col, green, sm(0.45, 1.15, f));
    col = mix(col, teal, sm(0.15, 0.85, g) * (1.0 - sm(0.25, 0.75, f)) * 0.9);
    float2 v = (position / size - 0.5) * float2(1.1, 0.9);
    col *= 1.0 - 0.35 * dot(v, v);
    col += (hash(floor(position)) - 0.5) * 0.03;
    return half4(half3(col), 1.0);
}
"""

/** Text measured once, in dp (density 1). */
private class OggTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, italic: Boolean = false, track: Float = 0f) = m.measure(
        s,
        TextStyle(
            fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp, color = Color.White,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        ),
        density = unit,
    )

    val labels = OggModel.LABELS.map { lines -> lines.map { t(it, 12f, FontWeight.SemiBold, italic = true) } }
    val titleGrid = t(OggModel.TITLE_GRID, 20f, FontWeight.SemiBold)
    val titleField = t(OggModel.TITLE_FIELD, 20f, FontWeight.SemiBold)
    val placeholder = t(OggModel.PLACEHOLDER, 17f, FontWeight.SemiBold)
    val ask = t(OggModel.ASK, 12f, FontWeight.Bold, track = 1.4f)
    val typed = (1..OggModel.SAMPLE.length).map { t(OggModel.SAMPLE.substring(0, it), 17f, FontWeight.SemiBold) }
}

private fun TextLayoutResult.w() = (getLineRight(0) - getLineLeft(0)).toDouble()

private class OggHolder {
    var frame = OggFrame()
}

@Composable
fun OracleGlassGrid(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val safeTop = (WindowInsets.safeDrawing.getTop(screen) / screen.density).toDouble()
    val safeBottom = (WindowInsets.safeDrawing.getBottom(screen) / screen.density).toDouble()
    BlockSystemBars(darkIcons = false)
    val driver = remember(auto, reduced) { OggDriver() }
    val holder = remember { OggHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { OggTexts(measurer) }
    val painter = remember { OggPainter() }

    BoxWithConstraints(modifier.fillMaxSize().background(oggHex(0x1D3A2B)).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val grid = OggModel.gridCenter(w, h, safeTop, safeBottom)
        val field = OggModel.fieldCenter(w, h)

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
        fun cardAt(x: Double, y: Double): Int? {
            for (i in 0 until 9) {
                val sx = OggModel.slotX(i, grid[0])
                val sy = OggModel.slotY(i, grid[1])
                if (abs(x - sx) <= OggModel.CARD_W / 2 + OggModel.GAP / 2 && abs(y - sy) <= OggModel.CARD_H / 2 + OggModel.GAP / 2) return i
            }
            return null
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Sibyl. What answers do you seek? Nine topic cards." }
                .pointerInput(driver, w, h, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            if (!c.pressed) break
                        }
                        val dx = ((last.x - down.position.x) / d).toDouble()
                        val dy = ((last.y - down.position.y) / d).toDouble()
                        val x = (last.x / d).toDouble()
                        val y = (last.y / d).toDouble()
                        val moved = hypot(dx, dy)
                        val swipeDown = dy > 60 && abs(dy) > abs(dx)
                        val backHit = x < 72 && abs(y - (safeTop + 24)) < 30
                        val t = now()
                        if (reduced) {
                            if (driver.reducedOpen) {
                                if (swipeDown || (moved < 10 && backHit)) {
                                    driver.setReduced(false, null, t)
                                    feel()
                                }
                            } else if (moved < 10) {
                                val i = cardAt(x, y)
                                if (i != null) {
                                    driver.setReduced(true, i, t)
                                    feel()
                                }
                            }
                        } else if (driver.isRest(t, auto)) {
                            val i = if (moved < 10) cardAt(x, y) else null
                            if (i != null) {
                                driver.pick(i, t)
                                feel()
                            } else {
                                driver.takeOver(t, auto)
                            }
                        } else {
                            driver.takeOver(t, auto)
                            if (swipeDown || (moved < 10 && backHit)) {
                                if (driver.close(t)) feel()
                            }
                        }
                        wake.intValue += 1
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            painter.lava(this, size.width, size.height, density, f.clock, w, h)
            scale(density, density, pivot = Offset.Zero) {
                with(painter) {
                    draw(f, w, h, safeTop, safeBottom, grid, field, texts)
                }
            }
        }
    }
}

private class OggPainter {
    private val m = OggModel
    private val lavaShader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(LAVA_SKSL) else null
    private val shaderPaint = android.graphics.Paint()
    private val blobPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()
    private val icons: List<List<Path>> = m.ICONS.map { list -> list.map { PathParser().parsePathString(it.d).toPath() } }

    /** The lava field, in pixels. Below API 33: purple with soft green and teal blobs. */
    fun lava(scope: DrawScope, pw: Float, ph: Float, density: Float, clock: Double, w: Double, h: Double) {
        val phase = 2 * PI * clock / m.LOOP
        scope.drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            val sh = lavaShader
            if (sh != null && Build.VERSION.SDK_INT >= 33) {
                sh.setFloatUniform("size", pw, ph)
                sh.setFloatUniform("phase", phase.f())
                shaderPaint.shader = sh
                c.drawRect(0f, 0f, pw, ph, shaderPaint)
            } else {
                c.drawColor(oggHex(0xA466C4).toArgb())
                val unit = ph / 2.0
                fun blob(bx: Double, by: Double, r: Double, ry: Double, k: Double, p: Double, s: Double, color: Int) {
                    val cx = pw / 2 + (bx + r * cos(k * phase + p)) * unit
                    val cy = ph / 2 + (by + r * ry * sin(k * phase + p)) * unit
                    val rad = (s * 1.7 * unit).f()
                    blobPaint.color = android.graphics.Color.BLACK
                    blobPaint.shader = RadialGradient(
                        cx.f(), cy.f(), rad,
                        intArrayOf(color or (0xFF shl 24), color or (0xD0 shl 24), color or (0x60 shl 24), color),
                        floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(cx.f(), cy.f(), rad, blobPaint)
                }
                blob(-0.55, -0.95, 0.18, 1.0, 1.0, 2.2, 0.36, 0x45A39E)
                blob(0.62, 0.35, 0.22, 1.0, -1.0, 0.6, 0.32, 0x45A39E)
                blob(-0.58, 0.95, 0.15, 1.0, 1.0, 4.0, 0.30, 0x45A39E)
                blob(-0.30, -0.62, 0.22, 0.8, 1.0, 0.0, 0.30, 0x1D3A2B)
                blob(0.28, -0.10, 0.20, 0.8, -1.0, 1.7, 0.34, 0x1D3A2B)
                blob(-0.20, 0.42, 0.24, 0.8, 1.0, 3.1, 0.32, 0x1D3A2B)
                blob(0.30, 0.78, 0.20, 0.8, 2.0, 4.4, 0.26, 0x1D3A2B)
                blob(0.05, 0.05, 0.30, 0.8, -1.0, 5.5, 0.22, 0x1D3A2B)
                blobPaint.shader = null
            }
        }
    }

    private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double) = Path().apply {
        addRoundRect(RoundRect(x.f(), y.f(), (x + w).f(), (y + h).f(), CornerRadius(r.f())))
    }

    private fun oval(cx: Double, cy: Double, r: Double) = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect((cx - r).f(), (cy - r).f(), (cx + r).f(), (cy + r).f()))
    }

    /** A soft (blurred) fill or stroke. Android's blur radius is 1.5 × SwiftUI's. */
    private fun DrawScope.blurred(path: Path, color: Color, radius: Double, strokeWidth: Double = 0.0) {
        blurPaint.reset()
        blurPaint.isAntiAlias = true
        blurPaint.color = color.toArgb()
        if (strokeWidth > 0) {
            blurPaint.style = android.graphics.Paint.Style.STROKE
            blurPaint.strokeWidth = strokeWidth.f()
            blurPaint.strokeCap = android.graphics.Paint.Cap.ROUND
            blurPaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
        drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), blurPaint) }
    }

    /** Draws a measured text centred on x, its cap middle on y. */
    private fun DrawScope.centered(l: TextLayoutResult, x: Double, y: Double, capHalf: Double, color: Color, alpha: Double = 1.0) {
        drawText(
            l, color = color, alpha = alpha.coerceIn(0.0, 1.0).f(),
            topLeft = Offset((x - l.w() / 2 - l.getLineLeft(0)).f(), (y + capHalf - l.firstBaseline).f()),
        )
    }

    fun DrawScope.draw(f: OggFrame, w: Double, h: Double, top: Double, bottom: Double, grid: DoubleArray, field: DoubleArray, tx: OggTexts) {
        val c = f.chosen
        val chosenX = m.slotX(c, grid[0])
        val chosenY = m.slotY(c, grid[1])
        val groupX = m.lerp(chosenX, field[0], f.center)
        val groupY = m.lerp(chosenY, field[1], f.center)
        val groupScale = m.lerp(1.0, m.CENTER_SCALE, f.center)
        val chosenScale = groupScale * (1 - 0.05 * f.press)

        // Halo behind the chosen card.
        if (f.halo > 0.01) {
            val r = m.CARD_H * chosenScale * 1.05
            drawCircle(
                Brush.radialGradient(
                    listOf(oggHex(m.WHITE, 0.42 * f.halo), oggHex(m.WHITE, 0.0)),
                    center = Offset(groupX.f(), groupY.f()), radius = r.f(),
                ),
                radius = r.f(), center = Offset(groupX.f(), groupY.f()),
            )
        }

        // Cards: farthest first, the chosen one on top.
        val order = (0 until 9).sortedBy { if (it == c) 99 else -f.ranks[it] }
        for (i in order) {
            if (i == c) {
                if (f.flip >= 0.999) continue
                card(i, groupX, groupY, chosenScale, 90 * f.flip, f.cardAlpha, f.pick, 1.0, f.flip, null, f, tx)
            } else {
                val k = min(f.ranks[i], 3).toDouble()
                val g = f.gather[i]
                val spread = groupScale * (1 - f.vanish)
                val tx0 = groupX + 9 * k * spread
                val ty0 = groupY - 7 * k * spread
                val sx = m.slotX(i, grid[0])
                val sy = m.slotY(i, grid[1])
                val s = m.lerp(1.0, groupScale * (1 - 0.03 * k), g) * (1 - 0.08 * f.vanish)
                val sheen = sheenOffset(f.sheen, sx, sy, grid)
                card(
                    i, m.lerp(sx, tx0, g), m.lerp(sy, ty0, g), s, 0.0, (1 - f.vanish) * f.cardAlpha, 0.0,
                    1 - 0.5 * g.coerceIn(0.0, 1.0), 0.0, sheen, f, tx,
                )
            }
        }

        drawField(f, field[0], field[1], w, tx)
        drawChrome(f, w, h, top, bottom, tx)
    }

    private fun sheenOffset(s: Double, sx: Double, sy: Double, grid: DoubleArray): Double? {
        if (s <= 0 || s >= 1) return null
        val span = 820.0
        val band = -span / 2 + span * s
        val local = (sx - grid[0]) + 0.5 * (sy - grid[1])
        return band - local
    }

    private fun DrawScope.card(
        i: Int, cx: Double, cy: Double, scale: Double, degrees: Double, alpha: Double, white: Double,
        iconDim: Double, shade: Double, sheen: Double?, f: OggFrame, tx: OggTexts,
    ) {
        if (alpha <= 0.001) return
        val cw = m.CARD_W
        val ch = m.CARD_H
        drawIntoCanvas { canvas ->
            val n = canvas.nativeCanvas
            n.save()
            n.translate(cx.f(), cy.f())
            if (degrees > 0.01) {
                camera.save()
                camera.setLocation(0f, 0f, -(m.CAMERA / 72).f())
                camera.rotateY(degrees.f())
                camera.getMatrix(matrix)
                camera.restore()
                n.concat(matrix)
            }
            n.scale(scale.f(), scale.f())
            n.translate((-cw / 2).f(), (-ch / 2).f())
            if (alpha < 0.999) n.saveLayerAlpha(-20f, -20f, (cw + 20).f(), (ch + 20).f(), (alpha * 255).toInt())
        }
        val shape = roundRect(0.0, 0.0, cw, ch, m.CARD_R)
        val glass = 1 - white
        drawPath(shape, oggHex(m.WHITE, 0.10 * glass))
        drawPath(
            shape,
            Brush.verticalGradient(listOf(oggHex(m.WHITE, 0.16 * glass), oggHex(m.WHITE, 0.0)), startY = 0f, endY = (ch * 0.45).f()),
        )
        if (white > 0.001) drawPath(shape, oggHex(m.CARD_WHITE, white))
        if (sheen != null) {
            val dx = 1 / 1.25
            val dy = 0.5 / 1.25
            val a = Offset((cw / 2 + dx * (sheen - 70)).f(), (ch / 2 + dy * (sheen - 70)).f())
            val b = Offset((cw / 2 + dx * (sheen + 70)).f(), (ch / 2 + dy * (sheen + 70)).f())
            drawPath(
                shape,
                Brush.linearGradient(
                    0f to oggHex(m.WHITE, 0.0), 0.5f to oggHex(m.WHITE, 0.16 * glass), 1f to oggHex(m.WHITE, 0.0),
                    start = a, end = b,
                ),
            )
        }
        drawPath(roundRect(0.6, 0.6, cw - 1.2, ch - 1.2, m.CARD_R - 0.6), oggHex(m.WHITE, 0.80 + 0.2 * white), style = Stroke(1.2f))
        if (glass > 0.001) {
            drawPath(roundRect(2.0, 2.0, cw - 4, ch - 4, m.CARD_R - 2), oggHex(m.WHITE, 0.18 * glass), style = Stroke(0.6f))
        }

        // Icon.
        val ink = oggMix(m.WHITE, m.INK, white)
        val marks = m.ICONS[i]
        val paths = icons[i]
        drawIntoCanvas { canvas ->
            val n = canvas.nativeCanvas
            n.save()
            n.translate((cw / 2).f(), (ch / 2 - m.ICON_LIFT).f())
            n.scale(m.ICON_SCALE.f(), m.ICON_SCALE.f())
            n.translate(-28f, -28f)
        }
        if (glass > 0.001) {
            for (j in marks.indices) {
                val mk = marks[j]
                if (!mk.glow) continue
                val a = m.markAlpha(mk, f.clock) * iconDim * glass * 0.9
                if (mk.fill) blurred(paths[j], oggHex(m.WHITE, a), 3.2)
                blurred(paths[j], oggHex(m.WHITE, a), 3.2, strokeWidth = 2.4)
            }
        }
        val stroke = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (j in marks.indices) {
            val mk = marks[j]
            val a = (if (mk.k > 0) m.markAlpha(mk, f.clock) * glass + mk.dim * white else mk.dim) * iconDim
            if (mk.fill) drawPath(paths[j], oggHex(ink, a)) else drawPath(paths[j], oggHex(ink, a), style = stroke)
        }
        drawIntoCanvas { it.nativeCanvas.restore() }

        // Label.
        val labelLines = tx.labels[i]
        val labelAlpha = if (iconDim > 0.99) 1.0 else 0.6 + 0.4 * iconDim
        for (j in labelLines.indices) {
            val l = labelLines[j]
            val baseline = ch - 13 - 13.5 * (labelLines.size - 1 - j)
            drawText(
                l, color = oggHex(ink), alpha = labelAlpha.f(),
                topLeft = Offset(((cw - l.w()) / 2 - l.getLineLeft(0)).f(), (baseline - l.firstBaseline).f()),
            )
        }

        // Turning away: the face darkens and a specular band slides across it.
        if (shade > 0.001) {
            drawPath(shape, oggHex(0x000000, 0.25 * shade))
            val x = cw * (1.2 - 1.6 * shade)
            drawPath(
                shape,
                Brush.horizontalGradient(
                    0f to oggHex(m.WHITE, 0.0), 0.5f to oggHex(m.WHITE, 0.45 * sin(PI * min(shade * 1.2, 1.0))), 1f to oggHex(m.WHITE, 0.0),
                    startX = (x - 30).f(), endX = (x + 30).f(),
                ),
            )
        }

        drawIntoCanvas { canvas ->
            val n = canvas.nativeCanvas
            if (alpha < 0.999) n.restore()
            n.restore()
        }
    }

    private fun DrawScope.drawField(f: OggFrame, px: Double, py: Double, w: Double, tx: OggTexts) {
        if (!(f.flip > 0.5 || f.reducedField) || f.fieldAlpha <= 0.001) return
        drawIntoCanvas { it.nativeCanvas.saveLayerAlpha(0f, 0f, w.f() * 2, (py * 4).f(), (f.fieldAlpha * 255).toInt()) }
        val o = f.open
        val fieldW = w - 2 * m.FIELD_MARGIN
        if (o > 0.001) {
            val fw = m.lerp(m.CARET_W, fieldW, o)
            val fh = m.lerp(m.CARET_H, m.FIELD_H, m.easeOut((o * 2).coerceIn(0.0, 1.0)))
            val rx = px - fw / 2
            val ry = py - fh / 2
            val shape = roundRect(rx, ry, fw, fh, fh / 2)
            val a = m.seg(o, 0.0, 0.12)
            drawPath(shape, oggHex(m.WHITE, 0.07 * a))
            blurred(shape, oggHex(m.WHITE, 0.45 * a), 6.0, strokeWidth = 2.5)
            drawPath(shape, oggHex(m.WHITE, 0.92 * a), style = Stroke(1.4f))
            val iw = fw - 7
            val ih = fh - 7
            if (iw > 8) {
                drawPath(roundRect(rx + 3.5, ry + 3.5, iw, ih, ih / 2), oggHex(m.WHITE, 0.30 * m.seg(o, 0.3, 0.8)), style = Stroke(0.8f))
            }
            if (f.rings > 0.001) {
                val r = 4.5 * m.lerp(0.4, 1.0, f.rings)
                for (x in listOf(rx, rx + fw)) {
                    val ring = oval(x, py, r)
                    drawPath(ring, oggHex(0x6E5A78, 0.9 * f.rings))
                    drawPath(ring, oggHex(m.WHITE, 0.95 * f.rings), style = Stroke(1.2f))
                }
            }
        }

        // Text, placeholder and the caret.
        var caretX: Double
        if (f.typed > 0) {
            val l = tx.typed[min(f.typed, tx.typed.size) - 1]
            if (f.textAlpha > 0.001) centered(l, px, py, 6.0, oggHex(m.WHITE), f.textAlpha)
            caretX = m.lerp(px, px + l.w() / 2 + 3, f.textAlpha)
        } else {
            val l = tx.placeholder
            if (f.placeholder > 0.001) centered(l, px, py, 6.0, oggHex(m.WHITE, 0.62), f.placeholder)
            caretX = m.lerp(px, px - l.w() / 2 - 6, f.toPlaceholder)
        }

        val lineA = f.lineAlpha * f.caret
        if (lineA > 0.001) {
            val lh = m.lerp(m.CARD_H * m.CENTER_SCALE, m.CARET_H, f.line)
            val bloom = lineA * (1 - m.seg(o, 0.0, 0.6))
            if (bloom > 0.001) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(oggHex(m.WHITE, 0.30 * bloom), oggHex(m.WHITE, 0.0)),
                        center = Offset(caretX.f(), py.f()), radius = 34f,
                    ),
                    radius = 34f, center = Offset(caretX.f(), py.f()),
                )
            }
            blurred(
                roundRect(caretX - m.CARET_W / 2 - 1.5, py - lh / 2, m.CARET_W + 3, lh, 2.0),
                oggHex(m.WHITE, (0.95 - 0.45 * m.seg(o, 0.5, 1.0)) * lineA), 5.0,
            )
            drawPath(roundRect(caretX - m.CARET_W / 2, py - lh / 2, m.CARET_W, lh, m.CARET_W / 2), oggHex(m.WHITE, lineA))
        }
        drawIntoCanvas { it.nativeCanvas.restore() }
    }

    private fun DrawScope.drawChrome(f: OggFrame, w: Double, h: Double, top: Double, bottom: Double, tx: OggTexts) {
        val y = top + 24
        val white = oggHex(m.WHITE, 0.95)
        // Sibyl's mark: a ring with a four-point star and an orbiting dot.
        val mark = oval(w / 2, y, 15.0)
        blurred(mark, oggHex(m.WHITE, 0.55), 4.0, strokeWidth = 2.0)
        drawPath(mark, white, style = Stroke(1.4f))
        val cx = w / 2
        drawPath(
            Path().apply {
                moveTo(cx.f(), (y - 8).f())
                lineTo((cx + 1.9).f(), (y - 1.9).f())
                lineTo((cx + 8).f(), y.f())
                lineTo((cx + 1.9).f(), (y + 1.9).f())
                lineTo(cx.f(), (y + 8).f())
                lineTo((cx - 1.9).f(), (y + 1.9).f())
                lineTo((cx - 8).f(), y.f())
                lineTo((cx - 1.9).f(), (y - 1.9).f())
                close()
            },
            white,
        )
        drawCircle(white, 1.6f, Offset((cx + 8.5).f(), (y - 8.5).f()))

        // Info and More.
        for ((i, x) in listOf(w - 66, w - 34).withIndex()) {
            drawPath(oval(x, y, 12.0), oggHex(m.WHITE, 0.9), style = Stroke(1.3f))
            if (i == 0) {
                drawCircle(white, 1.1f, Offset(x.f(), (y - 5).f()))
                drawPath(roundRect(x - 0.8, y - 2.4, 1.6, 8.0, 0.8), white)
            } else {
                for (dx in listOf(-5.0, 0.0, 5.0)) drawCircle(white, 1.2f, Offset((x + dx).f(), y.f()))
            }
        }

        // Back arrow (field only).
        if (f.titleField > 0.001) {
            val a = Path().apply {
                moveTo(41f, y.f()); lineTo(23f, y.f())
                moveTo(30f, (y - 7).f()); lineTo(23f, y.f()); lineTo(30f, (y + 7).f())
            }
            drawPath(a, oggHex(m.WHITE, 0.95 * f.titleField), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Titles.
        val ty = top + 86
        if (f.titleGrid > 0.001) centered(tx.titleGrid, w / 2, ty + f.titleGridY, 7.0, oggHex(m.WHITE, 0.96), f.titleGrid)
        if (f.titleField > 0.001) centered(tx.titleField, w / 2, ty + f.titleFieldY, 7.0, oggHex(m.WHITE, 0.96), f.titleField)

        // ASK FOR ME.
        val askA = f.askAlpha.coerceIn(0.0, 1.0)
        if (askA > 0.001) {
            val ay = h - bottom - 44
            val shape = roundRect(w / 2 - m.ASK_W / 2, ay - m.ASK_H / 2, m.ASK_W, m.ASK_H, m.ASK_H / 2)
            drawPath(shape, oggHex(m.WHITE, 0.15 * askA))
            drawPath(shape, oggHex(m.WHITE, 0.20 * askA), style = Stroke(1f))
            centered(tx.ask, w / 2 + 0.7, ay, 4.3, oggHex(m.WHITE, 0.95), askA)
            val nudge = 1.6 * sin(2 * PI * 5 * f.clock / m.LOOP)
            for (s in listOf(-1.0, 1.0)) {
                val chx = w / 2 + s * (82 - nudge)
                val ch = Path().apply {
                    moveTo((chx + s * 2).f(), (ay - 4).f())
                    lineTo((chx - s * 2).f(), ay.f())
                    lineTo((chx + s * 2).f(), (ay + 4).f())
                }
                drawPath(ch, oggHex(m.WHITE, 0.8 * askA), style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(oggHex(m.WHITE, 0.8 * askA), 1.6f, Offset((w / 2 + s * 64).f(), ay.f()))
            }
        }
    }
}
