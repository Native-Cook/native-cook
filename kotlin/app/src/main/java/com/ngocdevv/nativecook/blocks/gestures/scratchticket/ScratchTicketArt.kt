package com.ngocdevv.nativecook.blocks.gestures.scratchticket

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Everything drawn on the ticket that does not move: its outline with the tear-off perforation,
 * the barcode on the stub, the sunflower mark, a grain tile and the holographic foil shader.
 */
internal object ScratchArt {
    /**
     * The ticket's outline: a rounded card with a notch at each end of the perforation and a row
     * of small punched holes between them, cut out so the background shows through.
     */
    fun ticketShape(width: Float, height: Float, perfY: Float, radius: Float): Path {
        val card = Path().apply { addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW) }
        val cuts = Path()
        val notch = 13f
        cuts.addCircle(0f, perfY, notch, Path.Direction.CW)
        cuts.addCircle(width, perfY, notch, Path.Direction.CW)
        val hole = 2.3f
        val step = 10f
        val from = notch + 12f
        val count = floor((width - from * 2) / step).toInt()
        val start = (width - count * step) / 2f
        for (k in 0..count) cuts.addCircle(start + k * step, perfY, hole, Path.Direction.CW)
        card.op(cuts, Path.Op.DIFFERENCE)
        return card
    }

    /** A barcode of bars of 1–3 units, fixed by its seed so every ticket prints the same one. */
    fun barcode(width: Float, height: Float, seed: Int): Path {
        var t = seed
        fun rand(): Float {
            t = (t * 9301 + 49297) % 233280
            return t / 233280f
        }
        val path = Path()
        var x = 0f
        while (x < width) {
            val bar = 1f + floor(rand() * 3f)
            val gap = 1f + floor(rand() * 2.4f)
            if (x + bar > width) break
            path.addRect(x, 0f, x + bar, height, Path.Direction.CW)
            x += bar + gap
        }
        return path
    }

    class Sunflower(val petals: Path, val head: Path, val seeds: Path)

    /** A sunflower mark: petals around a seed head, centred on (0, 0) with radius [r]. */
    fun sunflower(r: Float): Sunflower {
        val petals = Path()
        val inner = r * 0.36f
        val half = r * 0.17f
        for (k in 0 until 14) {
            val a = k / 14f * TAU
            val c = cos(a)
            val s = sin(a)
            // A point (u along the petal, v across it) turned onto the petal's angle.
            fun x(u: Float, v: Float) = u * c - v * s
            fun y(u: Float, v: Float) = u * s + v * c
            val u1 = inner + (r - inner) * 0.35f
            val u2 = r - (r - inner) * 0.1f
            petals.moveTo(x(inner, 0f), y(inner, 0f))
            petals.cubicTo(x(u1, half), y(u1, half), x(u2, half * 0.55f), y(u2, half * 0.55f), x(r, 0f), y(r, 0f))
            petals.cubicTo(x(u2, -half * 0.55f), y(u2, -half * 0.55f), x(u1, -half), y(u1, -half), x(inner, 0f), y(inner, 0f))
            petals.close()
        }
        val head = Path().apply { addCircle(0f, 0f, r * 0.4f, Path.Direction.CW) }
        val seeds = Path()
        // Seeds on a golden-angle spiral, the way a real seed head packs them.
        for (k in 0 until 22) {
            val a = k * 2.39996f
            val d = sqrt(k / 22f) * r * 0.32f
            seeds.addCircle(cos(a) * d, sin(a) * d, r * 0.035f, Path.Direction.CW)
        }
        return Sunflower(petals, head, seeds)
    }

    /**
     * A tile of grain: grey `base + amount · n` with n random per pixel, repeated over a surface
     * (one tile pixel per [pixel] dp) for the paper and the baize.
     */
    fun grainTile(side: Int, base: Float, amount: Float, seed: Int): Bitmap {
        val random = Random(seed)
        val pixels = IntArray(side * side) {
            val v = ((base + amount * (random.nextFloat() * 0.5f + random.nextFloat() * 0.5f)) * 255f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    /**
     * Holographic scratch foil (AGSL, Android 13+). Brushed silver with a diamond guilloché pressed
     * into it, rainbow bands that slide with `tilt` and `time`, a soft specular sweep and a few
     * twinkling sparkles. `dissolve` (0…1) eats the foil away along a noise field with a hot golden
     * edge. `time` is a phase in radians and only enters through sin(time · n), so it loops.
     */
    const val FOIL = """
uniform float2 size;
uniform float time;
uniform float2 tilt;
uniform float dissolve;

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

half4 main(float2 xy) {
  float2 uv = xy / size;
  float n = noise(xy * 0.022) * 0.65 + noise(xy * 0.07) * 0.35;
  float brushed = noise(float2(xy.x * 0.015, xy.y * 0.9)) * 0.6 + noise(float2(xy.x * 0.03, xy.y * 1.7)) * 0.4;

  float2 g = float2(xy.x + xy.y, xy.x - xy.y) / 13.0;
  float2 d = abs(fract(g) - 0.5);
  float line = 1.0 - smoothstep(0.0, 0.06, min(d.x, d.y));

  float band = uv.x * 0.7 + uv.y * 0.5 + n * 0.35 + tilt.x * 0.35 + tilt.y * 0.25 + sin(time) * 0.1;
  float3 rainbow = 0.72 + 0.28 * cos(6.28318 * (band + float3(0.0, 0.33, 0.67)));
  float3 silver = float3(0.76, 0.77, 0.80) + (brushed - 0.5) * 0.14 + (n - 0.5) * 0.08;
  float3 col = silver * mix(float3(1.0), rainbow, 0.45);
  col += line * 0.045;

  float lightAt = 0.55 + 0.5 * sin(time) + tilt.x * 0.4;
  float s = uv.x * 0.8 + uv.y * 0.45 - lightAt;
  col += exp(-s * s * 22.0) * float3(0.26, 0.26, 0.28);
  col *= 1.0 - 0.12 * length(uv - 0.5);

  float cellSize = 11.0;
  float2 cell = floor(xy / cellSize);
  float h = hash(cell);
  float2 at = (cell + 0.25 + 0.5 * float2(hash(cell + 7.1), hash(cell + 3.7))) * cellSize;
  float2 q = abs(xy - at);
  float star = max(0.0, 1.0 - length(q) / 1.8) + max(0.0, 1.0 - q.x / 0.7) * max(0.0, 1.0 - q.y / 4.5) + max(0.0, 1.0 - q.y / 0.7) * max(0.0, 1.0 - q.x / 4.5);
  float twinkle = step(0.965, h) * pow(0.5 + 0.5 * sin(time * 3.0 + h * 60.0), 4.0);
  col += min(star, 1.2) * twinkle * 0.9;

  float dn = noise(xy * 0.05) * 0.7 + noise(xy * 0.16) * 0.3;
  float edgeAt = dissolve * 1.3 - 0.15;
  float a = smoothstep(edgeAt - 0.02, edgeAt + 0.02, dn);
  float glow = (1.0 - smoothstep(0.0, 0.07, abs(dn - edgeAt))) * step(0.001, dissolve) * (1.0 - dissolve * 0.3);
  col = mix(col, float3(1.0, 0.9, 0.58), glow * 0.85);
  a = max(a, glow);
  return half4(half3(col * a), half(a));
}
"""
}
