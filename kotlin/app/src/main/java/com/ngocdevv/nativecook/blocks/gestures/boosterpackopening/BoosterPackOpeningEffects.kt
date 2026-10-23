package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val GLINT_COLORS =
    intArrayOf(
        0xFF36E4FF.toInt(),
        0xFFFF62E6.toInt(),
        0xFFFFD84A.toInt(),
        0xFF5CF0FF.toInt(),
        0xFFFF7BEA.toInt(),
        0xFFFFE66B.toInt(),
    )

// Rays are evaluated around the rising card. Their rotation follows the shared
// opening clock, so a paused session also pauses the light.
private const val CARD_RISE_LIGHT =
    """
uniform float2 center;
uniform float radius;
uniform float phase;
uniform float strength;

half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d) / radius;
  float angle = atan(d.y, d.x);
  float broad = pow(0.5 + 0.5 * sin(angle * 7.0 + phase), 3.0);
  float fine = pow(0.5 + 0.5 * sin(angle * 13.0 - phase * 1.7 + 1.9), 4.0);
  float falloff = pow(max(0.0, 1.0 - dist), 1.35);
  float core = pow(max(0.0, 1.0 - dist * 1.15), 2.2);
  float light = clamp(strength * (core * 0.95 + (broad * 0.62 + fine * 0.38) * falloff * 1.05), 0.0, 1.0);
  return half4(float3(1.0, 0.98, 0.88) * light, light);
}
"""

private fun addSpark(path: Path, spark: TearSpark, width: Double) {
    val speed = hypot(spark.vx, spark.vy)
    if (speed < 0.001) return
    val fade = 1 - spark.age / spark.life
    val dx = spark.vx / speed
    val dy = spark.vy / speed
    val length = spark.size * (1.6 + speed / (width * 0.22)) * fade
    val half = spark.size * 0.5 * fade + 0.25
    path.moveTo((spark.x + dx * length).toFloat(), (spark.y + dy * length).toFloat())
    path.lineTo((spark.x - dy * half).toFloat(), (spark.y + dx * half).toFloat())
    path.lineTo((spark.x - dx * length * 0.55).toFloat(), (spark.y - dy * length * 0.55).toFloat())
    path.lineTo((spark.x + dy * half).toFloat(), (spark.y - dx * half).toFloat())
    path.close()
}

/** Sparks thrown from the tear frontier in proportion to newly torn material. */
internal class TearSparks(
    private val width: Double,
) {
    private val path = Path()
    private var sparks: List<TearSpark> = emptyList()
    private var emitterX = 0.0
    private var emitterY = 0.0
    private var tracking = false
    private var budget = 0.0
    private var seed = 0
    private val spacing = width * 0.017

    val isEmpty: Boolean get() = sparks.isEmpty()

    fun clear() {
        sparks = emptyList()
        tracking = false
        budget = 0.0
        path.rewind()
    }

    /** One frame: [head] is the tear's live endpoint while a finger is tearing, or null. */
    fun step(elapsedMs: Double, head: Point?, direction: Int) {
        val seconds = min(elapsedMs, 50.0) / 1000
        val next = ArrayList<TearSpark>(sparks.size + 6)
        for (spark in sparks) {
            val stepped = stepTearSpark(spark, seconds, width)
            if (stepped != null) next.add(stepped)
        }

        if (head != null) {
            if (tracking) {
                // Emission follows distance. Holding the finger still emits nothing.
                budget = min(budget + hypot(head.x - emitterX, head.y - emitterY) / spacing, 6.0)
                val count = floor(budget).toInt()
                for (index in 0 until count) {
                    seed += 1
                    if (next.size >= MAX_TEAR_SPARKS) continue
                    val along = (index + 1).toDouble() / count
                    next.add(
                        spawnTearSpark(
                            Point(emitterX + (head.x - emitterX) * along, emitterY + (head.y - emitterY) * along),
                            seed,
                            direction,
                            width,
                        ),
                    )
                }
                budget -= count
            }
            emitterX = head.x
            emitterY = head.y
            tracking = true
        } else {
            tracking = false
            budget = 0.0
        }
        sparks = next

        path.rewind()
        for (spark in next) addSpark(path, spark, width)
    }

    fun draw(canvas: Canvas, brush: Brush) {
        if (sparks.isEmpty()) return
        canvas.drawPath(path, brush.fill(0xFFF45CFF.toInt(), blur = width * 0.012, blend = BLEND_SCREEN))
        canvas.drawPath(path, brush.fill(0xFFFF9CF7.toInt(), blur = width * 0.004, blend = BLEND_SCREEN))
        canvas.drawPath(path, brush.fill(0xFFFFF6FF.toInt(), blend = BLEND_SCREEN))
    }
}

private fun addGlint(path: Path, x: Double, y: Double, size: Double, rotation: Double) {
    for (index in 0 until 8) {
        val angle = rotation + index * PI / 4
        val radius = if (index % 2 == 0) size else size * 0.26
        val px = (x + cos(angle) * radius).toFloat()
        val py = (y + sin(angle) * radius).toFloat()
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
}

private fun addEllipse(builder: PathBuilder, cx: Double, cy: Double, rx: Double, ry: Double) {
    val arc = 0.55228475
    builder.moveTo(cx - rx, cy)
    builder.cubicTo(cx - rx, cy - ry * arc, cx - rx * arc, cy - ry, cx, cy - ry)
    builder.cubicTo(cx + rx * arc, cy - ry, cx + rx, cy - ry * arc, cx + rx, cy)
    builder.cubicTo(cx + rx, cy + ry * arc, cx + rx * arc, cy + ry, cx, cy + ry)
    builder.cubicTo(cx - rx * arc, cy + ry, cx - rx, cy + ry * arc, cx - rx, cy)
    builder.close()
}

/** Expanding ring and glints that carry the single-frame flash into the opening. */
internal class CutBurst(
    private val pack: Rect,
    private val cutY: Double,
    private val width: Double,
    private val height: Double,
) {
    private val glints = Path()
    private val glintGradient = linearGradient(pack.x, 0.0, pack.x + pack.width, 0.0, GLINT_COLORS, null)

    fun draw(canvas: Canvas, brush: Brush, ms: Double) {
        if (ms <= 0) return
        if (ms < 480) {
            val wave = shockwaveAt(ms, width, height)
            val ring = PathBuilder()
            addEllipse(ring, width * 0.5, cutY, wave.radiusX, wave.radiusY)
            canvas.drawPath(
                ring.build(),
                brush.stroke(wave.strokeWidth, 0xFFB4FFFA.toInt(), wave.opacity, blur = width * 0.014, blend = BLEND_SCREEN),
            )
        }
        if (ms >= BURST_DURATION_MS) return
        glints.rewind()
        for (index in 0 until BURST_GLINT_COUNT) {
            val glint = burstGlintAt(index, ms, pack, cutY, width, height)
            if (glint != null && glint.size > 0.2) addGlint(glints, glint.x, glint.y, glint.size, glint.rotation)
        }
        // Saturated halos keep the glints readable over the pale background.
        canvas.drawPath(glints, brush.fill(blur = width * 0.012, shader = glintGradient))
        canvas.drawPath(glints, brush.fill(blur = width * 0.004, shader = glintGradient))
        canvas.drawPath(glints, brush.fill(0xFFFFFFFF.toInt(), 0.92))
    }
}

/**
 * Warm rays behind the card as it leaves the pack. Below Android 13 the rays are left out and
 * the light is its soft core alone.
 */
internal class CardRiseLight(
    private val width: Double,
    private val height: Double,
    private val cardHeight: Double,
) {
    private val shader = runtimeShader("card rise light", CARD_RISE_LIGHT)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.xfermode = BLEND_PLUS
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setFloatUniform("radius", (width * 1.02).toFloat())
            shader.setFloatUniform("strength", 1f)
        }
    }

    fun draw(canvas: Canvas, time: Double, cardTop: Double) {
        val opacity = cardRiseLightAt(time)
        // An invisible full-screen shader still costs fill; draw nothing while dark.
        if (opacity <= 0.001) return
        val centerY = cardTop + cardHeight * 0.46
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setFloatUniform("center", (width * 0.5).toFloat(), centerY.toFloat())
            shader.setFloatUniform("phase", (time * 0.0021).toFloat())
            paint.shader = shader
        } else {
            paint.shader =
                radialGradient(
                    width * 0.5, centerY, width * 1.02 / 1.15,
                    intArrayOf(rgba(255, 250, 224, 0.95), rgba(255, 250, 224, 0.2), rgba(255, 250, 224, 0.0)),
                    floatArrayOf(0f, 0.5f, 1f),
                )
        }
        paint.alpha = alpha255(opacity)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
