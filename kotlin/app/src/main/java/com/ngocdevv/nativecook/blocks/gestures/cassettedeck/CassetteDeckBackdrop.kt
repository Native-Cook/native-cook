package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Material
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The static and light-only layers under the cassettes: the lift above the cassette, the speaker grille, the LED,
 * the key glyphs and the small music pill (RN `Backdrop`, `glyphPaths`, `pillPaths`). Dp units.
 */
internal class CassetteDeckBackdrop(private val geometry: CassetteDeckGeometry, density: Float) {
    private val s = geometry.s
    private val oy = geometry.oy
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    // The room lift. The gradient is rebuilt only when the tint changes (it moves while the cassettes slide).
    private val liftCentreX = ((CassetteDeckTheme.cassette.x + CassetteDeckTheme.cassette.w / 2.0) * s).toFloat()
    private val liftCentreY = (oy + 205.0 * s).toFloat()
    private val liftRadius = (150.0 * s).toFloat()
    private var liftColor = 0
    private var liftShader: Shader? = null

    private val grille = buildGrille()
    private val glyphs = List(CassetteDeckSim.KEYS) { buildGlyph(it) }

    private val led = CassetteDeckTheme.led
    private val ledX = led.cx * s
    private val ledY = oy + led.cy * s

    // RN CassetteDeck.tsx:265-273. Spills and glows are blurred once and only their opacity animates.
    private val keys = CassetteDeckTheme.keys
    private val redSpill = CassetteDeckGlow.circle(
        cx = (keys.x0 + keys.w / 2.0) * s,
        cy = oy + (keys.y + keys.h * 0.62) * s,
        radius = CassetteDeckTheme.Space.xxl * s,
        sigma = 22.0 * s,
        color = CassetteDeckTheme.redSpill,
        density = density,
    )
    private val ledSpill = CassetteDeckGlow.circle(ledX, ledY, 22.0 * s, 16.0 * s, CassetteDeckTheme.ledSpill, density)
    private val ledGlow = CassetteDeckGlow.circle(ledX, ledY, led.r * 3.0 * s, 3.0 * s, CassetteDeckTheme.ledGlow, density)
    private val glyphGlows = glyphs.map { CassetteDeckGlow.path(it, 2.5 * s, CassetteDeckTheme.glyphGlow, density) }

    private val pillBody = Path()
    private val pillNote = Path()
    private var pillShader: Shader? = null

    init {
        buildPill()
    }

    fun draw(canvas: Canvas, sim: CassetteDeckSim) {
        drawLift(canvas, sim)
        fill.shader = null
        fill.color = CassetteDeckTheme.grille
        canvas.drawPath(grille, fill)

        // The LED flickers with a seeded level while the tape plays (two incommensurate sines, deterministic per loop).
        val flicker = 0.5 + 0.25 * sin(sim.clock * 23.1) + 0.25 * sin(sim.clock * 37.7 + 1.3)
        val ledLevel = sim.led * (1.0 - (if (sim.reduced) 0.0 else Material.ledFlicker * flicker * min(1.0, abs(sim.v))))
        val spill = (if (sim.reduced) 0.6 else 1.0) * Material.keyGlow * min(1.0, sim.lit[0])
        redSpill.draw(canvas, spill)
        ledSpill.draw(canvas, ledLevel)
        ledGlow.draw(canvas, ledLevel)
        fill.color = cassetteDeckLerpColor(ledLevel, CassetteDeckTheme.ledOff, CassetteDeckTheme.ledOn)
        canvas.drawCircle(ledX.toFloat(), ledY.toFloat(), (led.r * s).toFloat(), fill)

        for (i in glyphs.indices) {
            val lit = sim.lit[i]
            glyphGlows[i].draw(canvas, lit)
            fill.color = cassetteDeckLerpColor(lit, CassetteDeckTheme.glyphOff, CassetteDeckTheme.glyphOn)
            canvas.drawPath(glyphs[i], fill)
        }

        fill.shader = pillShader
        canvas.drawPath(pillBody, fill)
        fill.shader = null
        fill.color = CassetteDeckTheme.pillInk
        canvas.drawPath(pillNote, fill)
    }

    // The lift above the cassette takes the hue of the cassettes in view, weighted by how close each is to the deck.
    private fun drawLift(canvas: Canvas, sim: CassetteDeckSim) {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var a = 0.0
        var weights = 0.0
        for (j in 0 until CassetteDeckSim.SLOTS) {
            val weight = max(0.0, 1.0 - abs(CassetteDeckSim.slotX(j, sim.p)))
            val tint = CassetteDeckTheme.tints[j]
            r += tint[0] * weight
            g += tint[1] * weight
            b += tint[2] * weight
            a += tint[3] * weight
            weights += weight
        }
        val k = if (weights > 0.0) 1.0 / weights else 0.0
        // The source writes the alpha with toFixed(3).
        val alpha = cassetteDeckRound(a * k * 1000.0) / 1000.0
        val color = cassetteDeckArgb(cassetteDeckRound(r * k).toInt(), cassetteDeckRound(g * k).toInt(), cassetteDeckRound(b * k).toInt(), alpha)
        if (color != liftColor || liftShader == null) {
            liftColor = color
            liftShader = RadialGradient(
                liftCentreX, liftCentreY, liftRadius,
                intArrayOf(color, CassetteDeckTheme.liftFar), null, Shader.TileMode.CLAMP,
            )
        }
        fill.shader = liftShader
        canvas.drawRect(0f, 0f, geometry.width.toFloat(), geometry.height.toFloat(), fill)
    }

    // RN :218-232: a hexagonal grid of dots clipped to a disc.
    private fun buildGrille(): Path {
        val path = Path()
        val g = CassetteDeckTheme.grilleDots
        val rows = ceil(g.radius / (g.pitch * 0.866)).toInt()
        for (row in -rows..rows) {
            val y = row * g.pitch * 0.866
            val shift = (abs(row) % 2) * g.pitch * 0.5
            var x = -g.radius - shift
            while (x <= g.radius + g.pitch) {
                val px = x + shift
                if (px * px + y * y <= g.radius * g.radius) {
                    path.addCircle(((g.cx + px) * s).toFloat(), (oy + (g.cy + y) * s).toFloat(), (g.dot * s).toFloat(), Path.Direction.CW)
                }
                x += g.pitch
            }
        }
        return path
    }

    // RN :316-336: play, pause, next, rewind and fast-forward glyphs above the keys.
    private fun buildGlyph(index: Int): Path {
        val path = Path()
        val cx = (CassetteDeckTheme.glyphs.x0 + index * CassetteDeckTheme.keys.pitch) * s
        val cy = oy + CassetteDeckTheme.glyphs.y * s
        fun polygon(vararg points: Pair<Double, Double>) {
            path.moveTo((cx + points[0].first * s).toFloat(), (cy + points[0].second * s).toFloat())
            for (k in 1 until points.size) path.lineTo((cx + points[k].first * s).toFloat(), (cy + points[k].second * s).toFloat())
            path.close()
        }
        when (index) {
            0 -> polygon(-3.0 to -4.0, 4.0 to 0.0, -3.0 to 4.0)
            1 -> {
                polygon(-3.5 to -4.0, -1.2 to -4.0, -1.2 to 4.0, -3.5 to 4.0)
                polygon(1.2 to -4.0, 3.5 to -4.0, 3.5 to 4.0, 1.2 to 4.0)
            }
            2 -> {
                polygon(-4.0 to -4.0, 2.0 to 0.0, -4.0 to 4.0)
                polygon(2.4 to -4.0, 4.2 to -4.0, 4.2 to 4.0, 2.4 to 4.0)
            }
            3 -> {
                polygon(0.0 to -3.6, -5.0 to 0.0, 0.0 to 3.6)
                polygon(5.0 to -3.6, 0.0 to 0.0, 5.0 to 3.6)
            }
            else -> {
                polygon(0.0 to -3.6, 5.0 to 0.0, 0.0 to 3.6)
                polygon(-5.0 to -3.6, 0.0 to 0.0, -5.0 to 3.6)
            }
        }
        return path
    }

    // RN :298-314: the small music button at the bottom left, a light grey pill with a note.
    private fun buildPill() {
        val pill = CassetteDeckTheme.pill
        val x = pill.x * s
        val y = oy + pill.y * s
        val radius = (pill.h / 2.0 * s).toFloat()
        pillBody.addRoundRect(RectF(x.toFloat(), y.toFloat(), (x + pill.w * s).toFloat(), (y + pill.h * s).toFloat()), radius, radius, Path.Direction.CW)
        val cx = x + pill.w / 2.0 * s
        val cy = y + pill.h / 2.0 * s
        pillNote.addOval(RectF((cx - 3.2 * s).toFloat(), (cy + 1.2 * s).toFloat(), (cx - 3.2 * s + 3.4 * s).toFloat(), (cy + 1.2 * s + 2.6 * s).toFloat()), Path.Direction.CW)
        pillNote.addRect(RectF((cx - 0.2 * s).toFloat(), (cy - 5.0 * s).toFloat(), (cx - 0.2 * s + 0.9 * s).toFloat(), (cy - 5.0 * s + 7.6 * s).toFloat()), Path.Direction.CW)
        pillNote.moveTo((cx + 0.7 * s).toFloat(), (cy - 5.0 * s).toFloat())
        pillNote.quadTo((cx + 2.6 * s).toFloat(), (cy - 3.2 * s).toFloat(), (cx + 3.0 * s).toFloat(), (cy - 1.4 * s).toFloat())
        pillNote.lineTo((cx + 0.7 * s).toFloat(), (cy - 2.6 * s).toFloat())
        pillNote.close()
        pillShader = LinearGradient(
            0f, y.toFloat(), 0f, (y + pill.h * s).toFloat(),
            CassetteDeckTheme.pillTop, CassetteDeckTheme.pillBottom, Shader.TileMode.CLAMP,
        )
    }
}
