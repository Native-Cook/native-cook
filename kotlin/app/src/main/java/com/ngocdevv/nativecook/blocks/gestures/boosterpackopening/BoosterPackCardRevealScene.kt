package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Light lives in card UV space; its placement is driven by the card normal.
// A stationary finger holds the foil. Only the measured reveal sweep uses time.
private const val CARD_MATERIAL =
    """
uniform shader artwork;
uniform float2 size;
uniform float2 tilt;
uniform float holo;
uniform float shine;
uniform float sweep;
uniform float flash;

half4 main(float2 p) {
  half4 base = artwork.eval(p);
  float2 uv = p / size + 0.5;
  float slope = uv.x * 0.72 + uv.y * 0.58;
  float phase = tilt.x * 0.82 + tilt.y * 0.6;
  float3 rainbow = 0.5 + 0.5 * cos(6.28318 *
    (slope * 1.65 - phase + float3(0.0, 0.33, 0.67)));
  float band = pow(max(0.0, 1.0 - abs(slope - 0.63 - phase * 0.8) * 2.6), 2.0);
  float border = 1.0 - smoothstep(0.012, 0.04,
    min(min(uv.x, 1.0-uv.x), min(uv.y, 1.0-uv.y)));
  float movement = min(1.0, abs(tilt.x) * 1.7 + abs(tilt.y));
  float foil = holo * (0.045 + movement * 0.26 + border * 0.18) * (0.28 + band * 0.72);
  float grain = fract(sin(dot(floor(uv * size * 0.8), float2(12.9898, 78.233))) * 43758.5453);
  foil *= 0.88 + grain * 0.24;
  float3 color = mix(base.rgb, min(float3(1.0), base.rgb * 0.85 + rainbow * 0.72), foil);
  float glint = pow(max(0.0, 1.0 - abs(slope - (sweep * 1.9 - 0.25)) * 5.0), 3.0);
  float reflection = pow(max(0.0, 1.0 - abs(slope - 0.25 - phase) * 4.0), 6.0);
  float white = shine * glint * flash * 0.8 + reflection * movement * (0.055 + holo * 0.16);
  color = 1.0 - (1.0 - color) * (1.0 - white);
  color *= 1.0 - min(0.1, abs(tilt.x) * 0.065);
  return half4(color, base.a);
}
"""

private val AURA_COLORS =
    intArrayOf(0xFFF8AD72.toInt(), 0xFFE055AB.toInt(), 0xFFCF4BE3.toInt(), 0xFF777AF1.toInt(), 0xFF67D8E8.toInt())
private val AURA_GLOW = intArrayOf(0x8CFFFBE9.toInt(), 0x00FFF8EF)
private val RARE_MARK_COLORS = intArrayOf(0xFFFFF9C8.toInt(), 0xFFFFDF61.toInt(), 0xFFC18C24.toInt())
private val PLAIN_MARK_COLORS = intArrayOf(0xFFFAFCFF.toInt(), 0xFFDDE9EE.toInt(), 0xFF8DA1B3.toInt())
private val SETTLED = rareCardPose(2600.0, true)

/** A closed mark around a centre: four points for a plain card, five with inner notches for a rare one. */
private fun markPath(x: Double, y: Double, size: Double, fourPoint: Boolean = false): Path {
    val builder = PathBuilder()
    val points = if (fourPoint) 4 else 10
    for (i in 0 until points) {
        val angle = -PI / 2 + i * PI * 2 / points
        val radius = if (fourPoint) size * (if (i % 2 == 1) 0.65 else 1.0) else size * (if (i % 2 == 1) 0.46 else 1.0)
        val px = x + cos(angle) * radius
        val py = y + sin(angle) * radius
        if (i == 0) builder.moveTo(px, py) else builder.lineTo(px, py)
    }
    return builder.close().build()
}

/** One card of the stack, with the uniforms of its own copy of the foil material. */
private class RevealCard(
    val cardIndex: Int,
    val image: Bitmap,
    cardRect: Rect,
) {
    val artwork = imageShader(image, cardRect)
    val shader: RuntimeShader? = runtimeShader("card", CARD_MATERIAL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setInputShader("artwork", artwork)
            shader.setFloatUniform("size", cardRect.width.toFloat(), cardRect.height.toFloat())
            shader.setFloatUniform("holo", CARD_HOLO[cardIndex].toFloat())
            shader.setFloatUniform("shine", if (isRareCard(cardIndex)) 1f else 0.7f)
            paint.shader = shader
        } else {
            paint.shader = artwork
        }
    }
}

private class Sparkle(
    id: Int,
    width: Double,
    height: Double,
) {
    val phase = id * 2.1
    val x = width * (0.06 + ((id * 37 + 17) % 89) / 100.0)
    val y = height * (0.17 + ((id * 29 + 3) % 63) / 100.0)
    val size = width * (if (id % 3 == 0) 0.015 else 0.007)
    val path = markPath(x, y, size, true)
}

/**
 * The stack of five cards drawn over the collection backdrop: each card a perspective-projected
 * rounded rectangle with its foil material, the coloured room and sparkles of a rare arrival, and
 * the marks under the top card. Below Android 13 a card is its artwork without the foil.
 */
internal class CardRevealScene(
    private val state: CardReveal,
    private val width: Double,
    private val height: Double,
    images: List<Bitmap>,
    private val back: Bitmap,
) {
    private val cardWidth = width * ReferenceLayout.Card.WIDTH
    private val cardHeight = height * ReferenceLayout.Card.HEIGHT
    private val radius = cardWidth * 0.025
    private val cardRect = Rect(-cardWidth / 2, -cardHeight / 2, cardWidth, cardHeight)
    private val cards = List(CARD_COUNT) { RevealCard(it, images[it], cardRect) }
    private val sparkles = List(18) { Sparkle(it, width, height) }
    private val auraGradient = linearGradient(0.0, 0.0, width, height, AURA_COLORS, null)
    private val auraGlow = radialGradient(width * 0.5, height * 0.46, height * 0.58, AURA_GLOW, null)
    private val matrix = Matrix()
    private val bounds = RectF()
    private val clip = Path()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val markSize = width * 0.025
    private val markY = height * (1 + ReferenceLayout.Card.HEIGHT) / 2 + width * 0.065
    private val plainMarks = List(3) { markPath(markX(it), markY, markSize * 0.78, true) }
    private val rareMarks = List(3) { markPath(markX(it), markY, markSize) }

    init {
        clip.addRoundRect(
            (-cardWidth / 2).toFloat(),
            (-cardHeight / 2).toFloat(),
            (cardWidth / 2).toFloat(),
            (cardHeight / 2).toFloat(),
            radius.toFloat(),
            radius.toFloat(),
            Path.Direction.CW,
        )
    }

    private fun markX(slot: Int) = width * (1 - ReferenceLayout.Card.WIDTH) / 2 + markSize + slot * markSize * 2.5

    private fun roundRect(canvas: Canvas, inset: Double, offsetY: Double, paint: Paint) {
        bounds.set(
            (-cardWidth / 2 + inset).toFloat(),
            (-cardHeight / 2 + inset + offsetY).toFloat(),
            (cardWidth / 2 - inset).toFloat(),
            (cardHeight / 2 - inset + offsetY).toFloat(),
        )
        canvas.drawRoundRect(bounds, radius.toFloat(), radius.toFloat(), paint)
    }

    /** [top] is where the card that rose out of the pack sits; the stack is drawn at the same place. */
    fun draw(canvas: Canvas, brush: Brush, top: Double, opacity: Double, reducedMotion: Boolean) {
        if (opacity <= 0) return
        val index = state.index
        val revealMs = state.revealMs.value
        val exit = state.exit.value

        val aura = if (isRareCard(index)) rareCardPose(revealMs, reducedMotion).light * (1 - exit) else 0.0
        if (aura > 0.001) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), brush.fill(opacity = opacity * aura, shader = auraGradient))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), brush.fill(opacity = opacity * aura, shader = auraGlow))
        }

        if (!reducedMotion && isRareCard(index)) {
            val light = rareCardPose(revealMs).light
            for (sparkle in sparkles) {
                val sparkleOpacity = opacity * light * max(0.0, sin(revealMs / 170 + sparkle.phase)).pow(4) * 0.9
                if (sparkleOpacity <= 0.002) continue
                canvas.drawCircle(
                    sparkle.x.toFloat(),
                    sparkle.y.toFloat(),
                    (sparkle.size * 1.4).toFloat(),
                    brush.fill(0xFFFFF4BB.toInt(), sparkleOpacity * 0.55, blur = sparkle.size * 1.5),
                )
                canvas.drawPath(sparkle.path, brush.fill(0xFFFFFEF1.toInt(), sparkleOpacity))
            }
        }

        for (cardIndex in CARD_COUNT - 1 downTo 0) drawCard(canvas, brush, cards[cardIndex], top, opacity, reducedMotion)

        val marks = if (isRareCard(index)) rareMarks else plainMarks
        val colors = if (isRareCard(index)) RARE_MARK_COLORS else PLAIN_MARK_COLORS
        for (slot in 0 until 3) {
            if (slot >= CARD_MARKS[index]) continue
            // The first card's reveal clock starts when the stack becomes interactive.
            if (index == 0 && !state.ready) continue
            val delay = if (isRareCard(index)) 2100.0 else 120.0
            val markOpacity =
                opacity * state.opacity * (1 - exit) * clamp((revealMs - delay - slot * 90) / 180, 0.0, 1.0)
            if (markOpacity <= 0.002) continue
            val x = markX(slot)
            canvas.drawPath(
                marks[slot],
                brush.stroke(markSize * 0.34, 0xFF263442.toInt(), markOpacity, join = Paint.Join.ROUND),
            )
            canvas.drawPath(
                marks[slot],
                brush.fill(
                    opacity = markOpacity,
                    shader = linearGradient(x, markY - markSize, x, markY + markSize, colors, null),
                ),
            )
        }
    }

    /** Draws a card's two faces once, so their programs exist before the stack appears. */
    fun prewarm(canvas: Canvas, brush: Brush) {
        val card = cards[CARD_COUNT - 1]
        canvas.save()
        canvas.translate((width / 2).toFloat(), (height / 2).toFloat())
        roundRect(canvas, -0.6, 0.0, brush.fill(0xFF43516D.toInt(), blur = width * 0.012))
        card.paint.alpha = 255
        canvas.clipPath(clip)
        bounds.set(cardRect.x.toFloat(), cardRect.y.toFloat(), (cardWidth / 2).toFloat(), (cardHeight / 2).toFloat())
        canvas.drawRect(bounds, card.paint)
        imagePaint.alpha = 255
        canvas.drawBitmap(back, null, bounds, imagePaint)
        canvas.restore()
    }

    private fun drawCard(
        canvas: Canvas,
        brush: Brush,
        card: RevealCard,
        top: Double,
        sceneOpacity: Double,
        reducedMotion: Boolean,
    ) {
        val cardIndex = card.cardIndex
        val index = state.index
        val relative = cardIndex - index
        if (relative < 0 || relative >= CARD_COUNT) return
        val progress = state.exit.value
        val revealMs = state.revealMs.value
        val leaving = relative == 0 && state.exiting
        val rareCard = isRareCard(cardIndex)
        val rare = if (relative == 0 && rareCard && !leaving) rareCardPose(revealMs, reducedMotion) else SETTLED
        val arrival = if (relative == 0 && !leaving && !rareCard) commonCardArrival(revealMs, reducedMotion).pop else 0.0
        val tiltMix = if (relative > 0) 1 - progress else 1.0
        val yaw = state.yaw.value * tiltMix
        val pitch = state.pitch.value * tiltMix
        val depth = max(0, relative) * cardWidth * CARD_STACK_DEPTH
        val pose =
            CardPose(
                x =
                    width / 2 - sin(yaw) * depth +
                        (if (leaving && !reducedMotion) state.exitDirection * width * 1.22 * progress else 0.0),
                y =
                    top + cardHeight / 2 + sin(pitch) * depth -
                        (if (leaving && !reducedMotion) sin(progress * PI) * cardWidth * 0.025 else 0.0),
                yaw =
                    if (reducedMotion) {
                        0.0
                    } else {
                        yaw + rare.yaw + (if (leaving) state.exitDirection * 0.22 * progress else 0.0)
                    },
                pitch = if (reducedMotion) 0.0 else pitch + rare.pitch,
                roll = if (reducedMotion) 0.0 else rare.roll,
                scale = rare.scale * (1 + arrival * 0.032) * (1 - max(0, relative) * 0.003 * (1 - progress)),
            )

        var opacity =
            if (relative == 0) {
                if (reducedMotion) 1 - progress else 1.0
            } else {
                1 - (if (isRareCard(index)) rareCardPose(revealMs, reducedMotion).light else 0.0)
            }
        opacity *= sceneOpacity
        if (opacity <= 0.002) return
        val facing = cos(pose.yaw)

        if (facing >= 0.002) {
            // The reference shows clean paper edges on unrevealed layers. Bring in the
            // next face as soon as the outgoing card starts uncovering it.
            val artworkVisible = relative == 0 || (relative == 1 && state.exiting)
            canvas.save()
            matrix.setValues(cardProjection(pose, cardWidth))
            canvas.concat(matrix)
            bounds.set(
                (-cardWidth / 2 - 0.7).toFloat(),
                (-cardHeight / 2 + 1).toFloat(),
                (cardWidth / 2 + 0.7).toFloat(),
                (cardHeight / 2 + 2.4).toFloat(),
            )
            canvas.drawRoundRect(
                bounds, radius.toFloat(), radius.toFloat(),
                brush.fill(0xFF43516D.toInt(), opacity * 0.26, blur = width * 0.012),
            )
            roundRect(canvas, -0.6, 0.0, brush.fill(0xFFF6F7FC.toInt(), opacity))
            if (artworkVisible) {
                if (card.shader != null && SHADERS_AVAILABLE) {
                    // Rare cards keep their measured sweep; plain cards get a softer arrival glint.
                    val sweep: Double
                    val flash: Double
                    if (relative != 0) {
                        sweep = SETTLED.sweep
                        flash = SETTLED.flash
                    } else if (rareCard) {
                        val light = rareCardPose(revealMs, reducedMotion)
                        sweep = light.sweep
                        flash = light.flash
                    } else {
                        val light = commonCardArrival(revealMs, reducedMotion)
                        sweep = light.sweep
                        flash = light.flash
                    }
                    card.shader.setFloatUniform("tilt", sin(pose.yaw).toFloat(), pose.pitch.toFloat())
                    card.shader.setFloatUniform("sweep", sweep.toFloat())
                    card.shader.setFloatUniform("flash", flash.toFloat())
                }
                card.paint.alpha = alpha255(opacity)
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawRect(
                    (-cardWidth / 2).toFloat(),
                    (-cardHeight / 2).toFloat(),
                    (cardWidth / 2).toFloat(),
                    (cardHeight / 2).toFloat(),
                    card.paint,
                )
                canvas.restore()
            }
            val borderGlow =
                if (cardIndex == index && rareCard) rareCardPose(revealMs, reducedMotion).flash * 0.82 else 0.0
            if (borderGlow > 0.002) {
                roundRect(canvas, 0.0, 0.0, brush.stroke(2.2, 0xFFF6FEFF.toInt(), opacity * borderGlow, blur = 3.0))
            }
            canvas.restore()
        } else if (facing < -0.002) {
            canvas.save()
            matrix.setValues(cardProjection(pose, cardWidth, true))
            canvas.concat(matrix)
            roundRect(canvas, -0.9, 0.0, brush.fill(0xFFE1E4EE.toInt(), opacity))
            canvas.save()
            canvas.clipPath(clip)
            bounds.set(
                (-cardWidth / 2).toFloat(),
                (-cardHeight / 2).toFloat(),
                (cardWidth / 2).toFloat(),
                (cardHeight / 2).toFloat(),
            )
            imagePaint.alpha = alpha255(opacity)
            canvas.drawBitmap(back, null, bounds, imagePaint)
            canvas.restore()
            canvas.restore()
        }
    }
}
