// Ink Wash Page Turn — the bleed: one AGSL shader over the three baked pages (API 33+), ported line
// for line from iwp-shader.ts, and a drawn approximation for API 26–32.
//
// The front is a distance field in dp:
//   f(p) = |p − origin| + (blot − ½)·150 + (fibre − ½)·26 + (fine − ½)·5
// and s = radius − f is how far behind the front a point is. Every term is scaled by `ends`, 0 at
// progress 0 and 1, so a finished turn is exactly the new page and the loop swaps pages without a mark.
package com.ngocdevv.nativecook.blocks.navigation.inkwashpageturn

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private const val IWP_AGSL = """
uniform shader page0;
uniform shader page1;
uniform shader page2;
uniform float2 res;       // dp
uniform float2 origin;    // where the ink starts, dp
uniform float3 turn;      // page shown, page arriving, progress 0…1
uniform float reach;      // the farthest corner from the origin, dp
layout(color) uniform half4 inkTone;
layout(color) uniform half4 sheenTone;

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}

// 3 octaves, each turned 37° and doubled
float fbm3(float2 p) {
  float sum = 0.5 * vnoise(p);
  p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
  sum += 0.25 * vnoise(p);
  p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
  sum += 0.125 * vnoise(p);
  return sum / 0.875;
}

float frontAt(float2 p) {
  float2 q = p / 90.0;
  float2 w = float2(fbm3(q + float2(3.1, 0.0)), fbm3(q + float2(7.7, 1.3)));
  float blot = fbm3(q + 1.6 * w);
  float2 a = float2(dot(p, float2(0.966, 0.259)), dot(p, float2(-0.259, 0.966)));
  float2 b = float2(dot(p, float2(0.940, -0.342)), dot(p, float2(0.342, 0.940)));
  float fibre = 0.6 * vnoise(a * float2(0.012, 0.17)) + 0.4 * vnoise(b * float2(0.016, 0.21));
  float fine = vnoise(p * 0.9);
  return length(p - origin) + (blot - 0.5) * 150.0 + (fibre - 0.5) * 26.0 + (fine - 0.5) * 5.0;
}

half4 pageAt(float i, float2 p) {
  float2 at = clamp(p, float2(0.5), res - float2(0.5));
  if (i < 0.5) { return page0.eval(at); }
  if (i < 1.5) { return page1.eval(at); }
  return page2.eval(at);
}

half4 main(float2 p) {
  float3 inkColor = float3(inkTone.rgb);
  float3 sheenColor = float3(sheenTone.rgb);
  float progress = turn.z;
  float ends = smoothstep(0.0, 0.03, progress) * (1.0 - smoothstep(0.97, 1.0, progress));
  float radius = mix(-100.0, reach + 130.0, progress);
  float s = radius - frontAt(p);
  float behind = step(0.0, s);

  // wet paper swells: what is printed on it wobbles near the front
  float nearFront = smoothstep(-27.0, 0.0, s) * (1.0 - smoothstep(0.0, 60.0, s)) * ends;
  float2 wob = (float2(vnoise(p * 0.11 + float2(5.0, 0.0)), vnoise(p * 0.11 + float2(0.0, 9.0))) - 0.5) * 9.0 * nearFront;
  float3 oldPage = float3(pageAt(turn.x, p + wob).rgb);
  float3 nextPage = float3(pageAt(turn.y, p + wob * 0.6).rgb);
  float shown = smoothstep(0.0, 22.0, s);
  float3 col = mix(oldPage, nextPage, shown);

  // the paper's tooth, and the wet paper behind the front
  float tooth = 0.5 * vnoise(p * 1.7) + 0.5 * vnoise(float2(p.x * 0.09, p.y * 1.3));
  float wet = behind * exp(-max(s, 0.0) / 120.0) * ends;
  col *= 1.0 - 0.07 * wet * (0.8 + 0.4 * tooth);

  // the ink: a dark lip, pooled pigment behind it, a soak ahead of it
  float lip = exp(-(s * s) / 20.25);
  float pool = behind * exp(-max(s, 0.0) / 36.0) * (0.8 + 0.4 * tooth);
  float soak = (1.0 - behind) * exp(min(s, 0.0) / 9.0);
  float density = (0.8 * lip + 0.8 * pool + 0.5 * soak) * ends;
  col = mix(col, inkColor, 1.0 - exp(-density * 2.2));

  // the wet sheen just behind the lip
  float band = (s - 7.0) / 4.0;
  col += sheenColor * 0.1 * exp(-band * band) * (0.4 + 0.6 * vnoise(p * 0.25)) * ends;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/** what draws a turn between two pages; coordinates in dp, the canvas already scaled to them */
internal interface IwpBleed {
    fun draw(canvas: Canvas, width: Float, height: Float, originX: Float, originY: Float, reach: Float, from: Int, to: Int, progress: Float)
}

@RequiresApi(33)
internal class IwpRuntimeBleed(pages: List<Bitmap>, scale: Float) : IwpBleed {
    private val shader = RuntimeShader(IWP_AGSL)
    private val paint = Paint()

    init {
        val toDp = Matrix().apply { setScale(1f / scale, 1f / scale) }
        pages.forEachIndexed { i, page ->
            val image = BitmapShader(page, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            image.filterMode = BitmapShader.FILTER_MODE_LINEAR
            image.setLocalMatrix(toDp)
            shader.setInputShader("page$i", image)
        }
        shader.setColorUniform("inkTone", Color.valueOf(0.086f, 0.094f, 0.157f))
        shader.setColorUniform("sheenTone", Color.valueOf(0.92f, 0.96f, 1f))
        paint.shader = shader
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, originX: Float, originY: Float, reach: Float, from: Int, to: Int, progress: Float) {
        shader.setFloatUniform("res", width, height)
        shader.setFloatUniform("origin", originX, originY)
        shader.setFloatUniform("turn", from.toFloat(), to.toFloat(), progress)
        shader.setFloatUniform("reach", reach)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/**
 * API 26–32: no runtime shaders. The same noisy front as a clipped path: the new page inside it, the
 * old one outside, and three strokes of the ink colour for the lip and the pooled ink.
 */
internal class IwpDrawnBleed(private val pages: List<Bitmap>) : IwpBleed {
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val front = Path()
    private val rect = RectF()

    private fun hash(x: Double, y: Double): Double {
        val v = sin(x * 127.1 + y * 311.7) * 43758.5453
        return v - floor(v)
    }

    private fun noise(x: Double, y: Double): Double {
        val ix = floor(x)
        val iy = floor(y)
        var u = x - ix
        var v = y - iy
        u = u * u * (3.0 - 2.0 * u)
        v = v * v * (3.0 - 2.0 * v)
        val a = hash(ix, iy) + (hash(ix + 1.0, iy) - hash(ix, iy)) * u
        val b = hash(ix, iy + 1.0) + (hash(ix + 1.0, iy + 1.0) - hash(ix, iy + 1.0)) * u
        return a + (b - a) * v
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, originX: Float, originY: Float, reach: Float, from: Int, to: Int, progress: Float) {
        rect.set(0f, 0f, width, height)
        canvas.drawBitmap(pages[from], null, rect, image)
        val radius = -100.0 + (reach + 230.0) * progress
        front.rewind()
        var any = false
        for (i in 0 until FRONT_POINTS) {
            val a = 2.0 * PI * i / FRONT_POINTS
            val c = cos(a)
            val s = sin(a)
            val blot = (noise(c * 2.4 + 3.1, s * 2.4 + 1.3) - 0.5) * 150.0
            val fibre = (noise(c * 9.0 + 7.7, s * 9.0) - 0.5) * 26.0
            val r = max(0.0, radius - blot - fibre)
            if (r > 0.0) any = true
            val x = originX + (c * r).toFloat()
            val y = originY + (s * r).toFloat()
            if (i == 0) front.moveTo(x, y) else front.lineTo(x, y)
        }
        front.close()
        if (!any) return
        val saved = canvas.save()
        canvas.clipPath(front)
        canvas.drawBitmap(pages[to], null, rect, image)
        canvas.restoreToCount(saved)
        val p = progress.toDouble()
        val ends = (smooth(seg(p, 0.0, 0.03)) * (1.0 - smooth(seg(p, 0.97, 1.0)))).toFloat()
        for (k in STROKES.indices) {
            ink.strokeWidth = STROKES[k]
            ink.color = Color.argb((STROKE_ALPHA[k] * ends * 255f).toInt(), 22, 24, 40)
            canvas.drawPath(front, ink)
        }
    }

    private companion object {
        const val FRONT_POINTS = 144
        val STROKES = floatArrayOf(26f, 11f, 3.5f)
        val STROKE_ALPHA = floatArrayOf(0.12f, 0.24f, 0.72f)
    }
}
