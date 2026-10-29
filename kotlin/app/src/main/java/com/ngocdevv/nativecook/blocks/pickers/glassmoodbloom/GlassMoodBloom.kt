package com.ngocdevv.nativecook.blocks.pickers.glassmoodbloom

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import kotlin.math.sin

/*
 Glass Mood Bloom — the mood check-in of Petalog, an invented journal app (a port of GlassMoodBloom.swift).

 Look: a soft colour wash, a two-line title, a layered glass flower in the middle, the mood word, a capsule slider
 from "Very unpleasant" to "Very pleasant" and a Next button. Everything is drawn in code.
 Hero: the flower is six stacked glass plates, each a closed shape whose radius is a function of angle. Petal count,
 valley depth, petal profile, the twist of the outer plate, the rotation and the hue are continuous functions of the
 mood value, so the morph is a real shape change: a calm blue circle (Neutral) becomes a green pentagon, a yellow
 star and an orange five-lobed flower, or a blue scalloped octagon and a violet eight-pointed star. Inner plates
 lead and outer plates follow 45 ms each, so a change blooms out of the core. Each plate has a radial fill, folded
 facets, an inner glow, a back edge for its thickness and a bright rim; a white core glows in the middle.
 Supporting: the background, the word (which slides when it changes), the slider and the Next button follow the hue.

 autoPlay: LOOP = 10 s; rest at Neutral → Slightly Pleasant → Pleasant → Very Pleasant (hold) → Neutral →
 Slightly Unpleasant → Unpleasant → Very Unpleasant (hold) → Neutral (rest). Frame 10 s equals frame 0.
 Touch: drag the slider, or drag across the flower (160 dp = half the range). Release springs to the nearest mood.
 A light haptic when the word changes. 2 s after the last touch the flower eases back to Neutral and the loop restarts.
 Reduced motion: no loop, no breathing, no lag; Neutral rests and dragging sets the shape directly.
*/

private fun Double.f() = toFloat()

private fun GmbRgb.c(a: Double = 1.0) = Color(r.f(), g.f(), b.f(), a.coerceIn(0.0, 1.0).f())

private const val GMB_TITLE_1 = "Choose how you’re"
private const val GMB_TITLE_2 = "feeling right now"

/** Text measured once, in dp (density 1). */
private class GmbTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private fun t(s: String, size: Float, weight: FontWeight, track: Float = 0f) = m.measure(
        s,
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp, color = Color.White),
        density = unit,
    )

    val back = t("Back", 17f, FontWeight.Normal)
    val app = t("Petalog", 17f, FontWeight.SemiBold)
    val cancel = t("Cancel", 17f, FontWeight.Normal)
    val title1 = t(GMB_TITLE_1, 26f, FontWeight.SemiBold)
    val title2 = t(GMB_TITLE_2, 26f, FontWeight.SemiBold)
    val moods = GmbModel.MOODS.map { t(it.name, 27f, FontWeight.Bold) }
    val left = t("VERY UNPLEASANT", 11f, FontWeight.SemiBold, 0.5f)
    val right = t("VERY PLEASANT", 11f, FontWeight.SemiBold, 0.5f)
    val next = t("Next", 17f, FontWeight.SemiBold)
}

private fun TextLayoutResult.w() = size.width.toFloat()
private fun TextLayoutResult.h() = size.height.toFloat()

private class GmbLayout(val w: Double, val h: Double, top: Double, bottom: Double) {
    val navY = top + 29
    val titleY = top + 129
    val nextY = h - bottom - 32
    val sliderY = nextY - 101
    val captionY = sliderY + 35
    val labelY = sliderY - 71
    val cx = w / 2
    val cy: Double
    val radius: Double
    val trackX0 = 32.0
    val trackX1 = w - 32
    val knobX0 = trackX0 + 19
    val knobX1 = trackX1 - 19

    init {
        val titleBottom = top + 163
        val labelTop = labelY - 18
        cy = (titleBottom + labelTop) / 2
        radius = maxOf(60.0, minOf(148.0, (labelTop - titleBottom) / 2 - 40, (w - 48) / 2))
    }

    fun knobX(v: Double) = knobX0 + (knobX1 - knobX0) * ((v.coerceIn(-1.0, 1.0) + 1) / 2)
    fun valueAt(x: Double) = (x - knobX0) / maxOf(knobX1 - knobX0, 1.0) * 2 - 1
}

private class GmbHolder {
    var frame: GmbFrame? = null
    var held = false
    var nextDown = false
}

@Composable
fun GlassMoodBloom(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val safeTop = (WindowInsets.safeDrawing.getTop(screen) / screen.density).toDouble()
    val safeBottom = (WindowInsets.safeDrawing.getBottom(screen) / screen.density).toDouble()
    BlockSystemBars(darkIcons = true)
    val driver = remember(auto, reduced) { GmbDriver().also { it.auto = auto; it.reduced = reduced } }
    val holder = remember { GmbHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { GmbTexts(measurer) }
    val painter = remember { GmbPainter() }

    BoxWithConstraints(modifier.fillMaxSize().background(GmbModel.MOODS[3].bgTop.c()).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val lay = remember(w, h, safeTop, safeBottom) { GmbLayout(w, h, safeTop, safeBottom) }

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            while (true) {
                val idle = withFrameNanos { n ->
                    val now = n / 1e9
                    holder.frame = GmbFrame.make(driver, now, reduced)
                    tick.intValue += 1
                    !auto && !driver.busy(now)
                }
                if (idle) break
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun feel() {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Choose how you're feeling right now. Mood slider." }
                .pointerInput(driver, lay) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val px = (down.position.x / d).toDouble()
                        val py = (down.position.y / d).toDouble()
                        val slider = abs(py - lay.sliderY) < 30 && px > lay.trackX0 - 12 && px < lay.trackX1 + 12
                        val flower = !slider && hypot(px - lay.cx, py - lay.cy) < lay.radius * 1.15
                        val next = !slider && !flower && abs(py - lay.nextY) < 30 && px > lay.trackX0 && px < lay.trackX1
                        if (!slider && !flower) {
                            if (next) {
                                holder.nextDown = true
                                wake.intValue += 1
                            }
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                            }
                            if (next) {
                                holder.nextDown = false
                                feel()
                                wake.intValue += 1
                            }
                            return@awaitEachGesture
                        }
                        val v0 = driver.touchDown(now())
                        var bucket = GmbModel.bucket(v0)
                        holder.held = true
                        wake.intValue += 1
                        fun move(x: Double, dx: Double) {
                            val v = (if (slider) lay.valueAt(x) else v0 + dx / 160).coerceIn(-1.0, 1.0)
                            driver.touchMove(now(), v)
                            val b = GmbModel.bucket(v)
                            if (b != bucket) {
                                bucket = b
                                feel()
                            }
                        }
                        if (slider) move(px, 0.0)
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            val x = (c.position.x / d).toDouble()
                            move(x, x - px)
                            c.consume()
                            if (!c.pressed) break
                        }
                        driver.touchUp(now())
                        holder.held = false
                        wake.intValue += 1
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame ?: return@Canvas
            scale(density, density, pivot = Offset.Zero) {
                with(painter) { draw(f, lay, texts, holder.held, holder.nextDown) }
            }
        }
    }
}

private class GmbPainter {
    private val m = GmbModel
    private val paths = Array(6) { Path() }
    private val corePath = Path()
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    private fun build(path: Path, cx: Double, cy: Double, radius: Double, look: GmbLook, rot: Double, samples: Int = 240) {
        path.reset()
        for (s in 0 until samples) {
            val th = s.toDouble() / samples * 2 * PI
            val r = radius * m.radius(th - rot, look)
            val x = (cx + r * cos(th)).f()
            val y = (cy + r * sin(th)).f()
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    fun DrawScope.draw(f: GmbFrame, lay: GmbLayout, tx: GmbTexts, held: Boolean, nextDown: Boolean) {
        val look = m.look(f.v)
        val center = Offset(lay.cx.f(), lay.cy.f())
        val r = lay.radius

        // Background wash and the halo behind the flower
        drawRect(
            Brush.verticalGradient(
                0f to look.bgTop.c(), 0.5f to look.bgTop.mix(look.bgBottom, 0.55).c(), 1f to look.bgBottom.c(),
                startY = 0f, endY = lay.h.f(),
            ),
            size = Size(lay.w.f(), lay.h.f()),
        )
        val halo = (r * 1.7).f()
        drawCircle(
            Brush.radialGradient(
                0f to GmbRgb.WHITE.c(0.55), 0.5f to GmbRgb.WHITE.c(0.18), 1f to GmbRgb.WHITE.c(0.0),
                center = center, radius = halo,
            ),
            radius = halo, center = center,
        )

        flower(f, center, r)
        slider(f, look, lay, held)
        button(look, lay, nextDown)
        texts(f, look, lay, tx, nextDown)
    }

    private fun DrawScope.flower(f: GmbFrame, center: Offset, rr: Double) {
        val core = m.look(f.v)
        val glow = (rr * 1.25).f()
        drawCircle(
            Brush.radialGradient(
                0f to core.deep.c(0.30), 0.45f to core.deep.c(0.16), 1f to core.deep.c(0.0),
                center = center, radius = glow,
            ),
            radius = glow, center = center,
        )
        val cx = center.x.toDouble()
        val cy = center.y.toDouble()
        for (i in 0 until 6) {
            val v = f.layerV[i]
            val look = m.look(v)
            val k = i / 5.0
            val side = if (i % 2 == 0) 1.0 else -1.0
            val wob = 1.6 * side * m.wobble(f.loopT, i) * f.breath
            val rotDeg = 24 * v - (if (i == 0) look.twist else 0.0) + wob
            val rot = -PI / 2 + rotDeg * PI / 180
            val radius = rr * look.size * m.LAYER_SCALE[i] * (1 + 0.012 * m.breathe(f.loopT, i) * f.breath + f.swell[i])
            val path = paths[i]
            build(path, cx, cy, radius, look, rot)
            val col = m.layerColor(look, k)
            val a = m.LAYER_ALPHA[i]

            // Back edge: the plate's thickness, a thin darker outline just below and right
            translate(1.2f, 2.0f) { drawPath(path, look.deep.c(0.38), style = Stroke(1f)) }

            // Glass fill
            drawPath(
                path,
                Brush.radialGradient(
                    listOf(col.mix(GmbRgb.WHITE, 0.35).c(a), col.c(minOf(1.0, a * 1.15))),
                    center = center, radius = radius.f(),
                ),
            )

            clipPath(path) {
                // Folded facets: each petal lit on one side of its ridge, shaded on the other
                val strength = (1 - m.valley(look)) * (0.3 + 0.7 * k)
                if (strength > 0.01) {
                    val n = look.n.toInt()
                    val colors = IntArray(n * 3)
                    val stops = FloatArray(n * 3)
                    val light = GmbRgb.WHITE.c(0.42 * strength).toArgb()
                    val dark = look.deep.c(0.30 * strength).toArgb()
                    val clear = GmbRgb.WHITE.c(0.0).toArgb()
                    for (j in 0 until n) {
                        val s = j.toDouble() / n
                        val w = 1.0 / n
                        colors[j * 3] = dark; stops[j * 3] = (s + 0.0005).f()
                        colors[j * 3 + 1] = clear; stops[j * 3 + 1] = (s + w * 0.5).f()
                        colors[j * 3 + 2] = light; stops[j * 3 + 2] = (s + w - 0.0005).f()
                    }
                    val deg = (rot * 180 / PI).f()
                    val brush = object : ShaderBrush() {
                        override fun createShader(size: Size): Shader =
                            android.graphics.SweepGradient(center.x, center.y, colors, stops).apply {
                                setLocalMatrix(android.graphics.Matrix().apply { setRotate(deg, center.x, center.y) })
                            }
                    }
                    drawPath(path, brush)
                }
                // Inner glow along the rim
                drawPath(path, look.deep.c(0.14 + 0.08 * k), style = Stroke(12f))
                drawPath(path, look.deep.c(0.10), style = Stroke(5f))
            }

            // Bright rim, lit from the top left
            val b = path.getBounds()
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(GmbRgb.WHITE.c(0.95), GmbRgb.WHITE.c(0.55), GmbRgb.WHITE.c(0.30)),
                    start = Offset(b.left, b.top), end = Offset(b.right, b.bottom),
                ),
                style = Stroke(1.3f),
            )
        }

        // Glowing white core in the same shape
        val cg = 26f
        drawCircle(
            Brush.radialGradient(
                listOf(GmbRgb.WHITE.c(0.85), GmbRgb.WHITE.c(0.25), GmbRgb.WHITE.c(0.0)),
                center = center, radius = cg,
            ),
            radius = cg, center = center,
        )
        build(corePath, cx, cy, 6.5, core, -PI / 2 + 24 * f.v * PI / 180, 80)
        drawPath(corePath, Color.White)
    }

    private fun DrawScope.shadowed(color: Color, radius: Float, dy: Float, draw: (android.graphics.Canvas, android.graphics.Paint) -> Unit) {
        drawContext.canvas.nativeCanvas.let { c ->
            val p = shadowPaint
            p.color = color.copy(alpha = 1f).toArgb()
            p.setShadowLayer(radius, 0f, dy, color.toArgb())
            draw(c, p)
        }
    }

    private fun DrawScope.slider(f: GmbFrame, look: GmbLook, lay: GmbLayout, held: Boolean) {
        val tl = Offset(lay.trackX0.f(), (lay.sliderY - 19).f())
        val ts = Size((lay.trackX1 - lay.trackX0).f(), 38f)
        val cr = CornerRadius(19f)
        drawRoundRect(GmbRgb.WHITE.c(0.34), tl, ts, cr)
        drawRoundRect(look.deep.c(0.10), tl, ts, cr)
        drawRoundRect(GmbRgb.WHITE.c(0.55), tl, ts, cr, style = Stroke(0.6f))
        val kx = lay.knobX(f.v).f()
        val kr = if (held) 18.4f else 17f
        val ky = lay.sliderY.f()
        shadowed(look.label.c(0.18), 6f, 3f) { c, p ->
            p.color = android.graphics.Color.WHITE
            c.drawCircle(kx, ky, kr, p)
        }
        drawCircle(Color.White, kr, Offset(kx, ky))
    }

    private fun DrawScope.button(look: GmbLook, lay: GmbLayout, nextDown: Boolean) {
        val s = if (nextDown) 0.97f else 1f
        val bw = (lay.trackX1 - lay.trackX0).f() * s
        val bh = 52f * s
        val left = lay.cx.f() - bw / 2
        val top = lay.nextY.f() - bh / 2
        shadowed(look.button.c(0.28), 10f, 6f) { c, p ->
            p.color = look.button.c().toArgb()
            c.drawRoundRect(left, top, left + bw, top + bh, bh / 2, bh / 2, p)
        }
        drawRoundRect(look.button.c(), Offset(left, top), Size(bw, bh), CornerRadius(bh / 2))
        drawRoundRect(
            Brush.verticalGradient(listOf(GmbRgb.WHITE.c(0.16), GmbRgb.WHITE.c(0.0)), startY = top, endY = top + bh / 2),
            Offset(left, top), Size(bw, bh), CornerRadius(bh / 2),
        )
    }

    private fun DrawScope.centered(t: TextLayoutResult, cx: Float, cy: Float, color: Color, alpha: Float = 1f) {
        drawText(t, color = color, topLeft = Offset(cx - t.w() / 2, cy - t.h() / 2), alpha = alpha)
    }

    private fun DrawScope.texts(f: GmbFrame, look: GmbLook, lay: GmbLayout, tx: GmbTexts, nextDown: Boolean) {
        val nav = GmbRgb.hex(0x2A3038).mix(look.label, 0.35)
        val navY = lay.navY.f()
        val chevron = Path().apply {
            moveTo(20.5f, navY - 8.5f)
            lineTo(13f, navY)
            lineTo(20.5f, navY + 8.5f)
        }
        drawPath(chevron, nav.c(0.78), style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawText(tx.back, color = nav.c(0.78), topLeft = Offset(25f, navY - tx.back.h() / 2))
        centered(tx.app, lay.cx.f(), navY, nav.c())
        drawText(tx.cancel, color = nav.c(0.78), topLeft = Offset(lay.w.f() - 16f - tx.cancel.w(), navY - tx.cancel.h() / 2))

        val title = GmbRgb.hex(0x23272E).mix(look.label, 0.25).c()
        centered(tx.title1, lay.cx.f(), lay.titleY.f() - 16.5f, title)
        centered(tx.title2, lay.cx.f(), lay.titleY.f() + 16.5f, title)

        if (f.prevAlpha > 0.001) {
            centered(tx.moods[f.labelPrev], lay.cx.f(), (lay.labelY + f.prevDy).f(), look.label.c(), f.prevAlpha.f())
        }
        centered(tx.moods[f.label], lay.cx.f(), (lay.labelY + f.nowDy).f(), look.label.c(), f.nowAlpha.f())

        val cap = GmbRgb.hex(0x3E4650).mix(look.label, 0.3).c(0.72)
        drawText(tx.left, color = cap, topLeft = Offset(36f, lay.captionY.f() - tx.left.h() / 2))
        drawText(tx.right, color = cap, topLeft = Offset(lay.w.f() - 36f - tx.right.w(), lay.captionY.f() - tx.right.h() / 2))

        scale(if (nextDown) 0.97f else 1f, pivot = Offset(lay.cx.f(), lay.nextY.f())) {
            centered(tx.next, lay.cx.f(), lay.nextY.f(), Color.White)
        }
    }
}
