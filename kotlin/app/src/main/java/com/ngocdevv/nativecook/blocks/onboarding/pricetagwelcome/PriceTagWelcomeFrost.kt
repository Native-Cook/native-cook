package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.max

internal class PriceTagWelcomeFrostRenderer(
    assets: PriceTagWelcomeAssets,
) {
    private val modern = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PriceTagWelcomeModernFrost(assets)
    } else {
        null
    }
    private val fallback = PriceTagWelcomeFallbackFrost(assets)

    fun drawSticker(
        canvas: Canvas,
        index: Int,
        look: PriceTagWelcomeMotion.FrostLook,
        clock: Double,
        originX: Float,
        originY: Float,
        span: Float,
        featureScale: Float,
        boxWidth: Float,
        boxHeight: Float,
        margin: Float,
    ) {
        if (!look.on || look.fade <= 0.0) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && modern != null) {
            modern.drawSticker(
                canvas,
                index,
                look,
                clock,
                originX,
                originY,
                span,
                featureScale,
                boxWidth,
                boxHeight,
                margin,
            )
        } else {
            fallback.drawSticker(
                canvas,
                index,
                look,
                originX,
                originY,
                span,
                boxWidth,
                boxHeight,
                margin,
            )
        }
    }

    fun drawTag(
        canvas: Canvas,
        index: Int,
        look: PriceTagWelcomeMotion.FrostLook,
        clock: Double,
        span: Float,
        featureScale: Float,
        width: Float,
        height: Float,
    ) {
        if (!look.on || look.fade <= 0.0) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && modern != null) {
            modern.drawTag(canvas, index, look, clock, span, featureScale, width, height)
        } else {
            fallback.drawTag(canvas, look, span, width, height)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PriceTagWelcomeModernFrost(
    assets: PriceTagWelcomeAssets,
) {
    private val art = PriceTagWelcomeTheme.items.map { item ->
        requireNotNull(assets.art[item.key]).asAndroidBitmap()
    }
    private val stickerShaders = Array(PriceTagWelcomeMotion.N) {
        RuntimeShader(priceTagWelcomeStickerSource())
    }
    private val tagShaders = Array(PriceTagWelcomeMotion.N) {
        RuntimeShader(priceTagWelcomeTagSource())
    }
    private val imageShaders = art.map { bitmap ->
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val source = RectF()
    private val destination = RectF()

    init {
        for (index in stickerShaders.indices) {
            stickerShaders[index].setInputShader("img", imageShaders[index])
        }
    }

    fun drawSticker(
        canvas: Canvas,
        index: Int,
        look: PriceTagWelcomeMotion.FrostLook,
        clock: Double,
        originX: Float,
        originY: Float,
        span: Float,
        featureScale: Float,
        boxWidth: Float,
        boxHeight: Float,
        margin: Float,
    ) {
        val bitmap = art[index]
        source.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        destination.set(margin, margin, margin + boxWidth, margin + boxHeight)
        matrix.setRectToRect(source, destination, Matrix.ScaleToFit.FILL)
        imageShaders[index].setLocalMatrix(matrix)

        val shader = stickerShaders[index]
        shader.setFloatUniform("org", originX, originY)
        shader.setFloatUniform("grow", (look.grow * span).toFloat())
        shader.setFloatUniform("span", span)
        shader.setFloatUniform("melt", look.melt.toFloat())
        shader.setFloatUniform("clock", clock.toFloat())
        shader.setFloatUniform("k", featureScale)
        shader.setFloatUniform("fade", look.fade.toFloat())
        shader.setFloatUniform("seed", (1.7 + 3.1 * index).toFloat())
        shader.setFloatUniform("box", boxWidth, boxHeight)
        shader.setFloatUniform("margin", margin)
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, boxWidth + 2f * margin, boxHeight + 2f * margin, paint)
        paint.shader = null
    }

    fun drawTag(
        canvas: Canvas,
        index: Int,
        look: PriceTagWelcomeMotion.FrostLook,
        clock: Double,
        span: Float,
        featureScale: Float,
        width: Float,
        height: Float,
    ) {
        val shader = tagShaders[index]
        shader.setFloatUniform("org", width / 2f, height / 2f)
        shader.setFloatUniform("grow", (look.grow * span).toFloat())
        shader.setFloatUniform("span", span)
        shader.setFloatUniform("melt", look.melt.toFloat())
        shader.setFloatUniform("clock", clock.toFloat())
        shader.setFloatUniform("k", featureScale)
        shader.setFloatUniform("fade", look.fade.toFloat())
        shader.setFloatUniform("seed", (5.3 + 2.3 * index).toFloat())
        shader.setFloatUniform("size", width, height)
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }
}

private class PriceTagWelcomeFallbackFrost(
    assets: PriceTagWelcomeAssets,
) {
    private val art = PriceTagWelcomeTheme.items.map { item ->
        requireNotNull(assets.art[item.key]).asAndroidBitmap()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val clipPath = Path()

    fun drawSticker(
        canvas: Canvas,
        index: Int,
        look: PriceTagWelcomeMotion.FrostLook,
        originX: Float,
        originY: Float,
        span: Float,
        boxWidth: Float,
        boxHeight: Float,
        margin: Float,
    ) {
        val remaining = (1.0 - PriceTagWelcomeMotion.smoothstep(look.melt)).toFloat()
        val radius = max(0f, (look.grow * span).toFloat() * remaining)
        if (radius <= 0f) {
            return
        }
        val checkpoint = canvas.save()
        clipPath.reset()
        clipPath.addCircle(originX, originY, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        destination.set(margin, margin, margin + boxWidth, margin + boxHeight)
        paint.alpha = (255.0 * look.fade).toInt().coerceIn(0, 255)
        paint.colorFilter = PorterDuffColorFilter(
            Color.rgb(186, 214, 238),
            PorterDuff.Mode.SRC_ATOP,
        )
        canvas.drawBitmap(art[index], null, destination, paint)
        paint.colorFilter = null
        canvas.restoreToCount(checkpoint)
    }

    fun drawTag(
        canvas: Canvas,
        look: PriceTagWelcomeMotion.FrostLook,
        span: Float,
        width: Float,
        height: Float,
    ) {
        val remaining = (1.0 - PriceTagWelcomeMotion.smoothstep(look.melt)).toFloat()
        val radius = max(0f, (look.grow * span).toFloat() * remaining)
        if (radius <= 0f) {
            return
        }
        val checkpoint = canvas.save()
        clipPath.reset()
        clipPath.addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        paint.color = Color.rgb(186, 214, 238)
        paint.alpha = (
            255.0 *
                PriceTagWelcomeTheme.Frost.tagAlpha *
                look.fade
            ).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(0f, 0f, width, height, height / 2f, height / 2f, paint)
        canvas.restoreToCount(checkpoint)
    }
}

private fun priceTagWelcomeStickerSource(): String {
    return (priceTagWelcomeFrostCommon() + """
        uniform shader img;
        uniform float2 box;
        uniform float margin;

        float4 sampleImg(float2 p) {
          float2 lo = float2(margin);
          float2 hi = lo + box;
          if (p.x < lo.x || p.y < lo.y || p.x > hi.x || p.y > hi.y) return float4(0.0);
          return img.eval(p);
        }

        half4 main(float2 p) {
          if (fade <= 0.0) return half4(0.0);
          float4 src = sampleImg(p);
          float needles = 0.0;
          float wet = 0.0;
          float4 c = iceCell(p, __CELL__ * k, needles, wet);
          float ice = c.x;
          float4 outc = float4(0.0);

          if (src.a > 0.003) {
            float3 rgb = src.rgb / src.a;
            float lum = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            float3 cool = lum * COOL_TINT + float3(0.02, 0.05, 0.09);
            float3 base = mix(rgb, cool, 0.86);
            float veil = 0.40 + 0.20 * needles;
            float3 col = mix(base, ICE_HI, veil);
            col = mix(col, ICE_DEEP, 0.10 * c.y);
            col = mix(col, ICE_MID, 0.10 * (1.0 - c.y) * needles);
            col = mix(col, float3(1.0), 0.65 * c.w);
            col += float3(1.0) * c.z * 0.9;
            float alpha = src.a * sat1(ice + c.z * ice);
            float sheen = wet * (0.35 + 0.65 * smoothstep(0.2, 1.0, needles));
            float wetAlpha = src.a * wet * 0.45;
            float3 wetColor = mix(rgb, ICE_MID, 0.35) + 0.25 * sheen;
            outc = float4(col * alpha, alpha);
            outc = outc + float4(wetColor * wetAlpha, wetAlpha) * (1.0 - outc.a);

            float lift = 0.0;
            float3 dropColor = float3(0.0);
            for (int i = 0; i < 6; i++) {
              float fi = float(i);
              float start = 0.08 + 0.17 * h1(float2(fi, 9.1));
              float life = melt - start;
              if (life > 0.0 && life < 0.75) {
                float dropX = margin + (0.2 + 0.6 * h1(float2(fi, 5.3))) * box.x;
                float startY = margin + (0.18 + 0.32 * h1(float2(fi, 2.9))) * box.y;
                float dropY = startY + pow(life / 0.75, 1.7) * box.y * 0.62;
                float radius = (2.2 + 1.2 * h1(float2(fi, 4.4))) * k;
                float visibility = smoothstep(0.0, 0.08, life) *
                  (1.0 - smoothstep(0.55, 0.75, life));
                float2 q = (p - float2(dropX, dropY)) / radius;
                float body = 1.0 - smoothstep(0.85, 1.0, length(q * float2(1.0, 0.88)));
                float trail = (1.0 - smoothstep(0.18, 0.32, abs(p.x - dropX) / radius / 2.0)) *
                  step(startY, p.y) * step(p.y, dropY) * smoothstep(startY, dropY, p.y) * 0.5;
                float rim = body * smoothstep(0.35, 1.0, length(q));
                float specular = exp(-dot(q - float2(-0.32, -0.38), q - float2(-0.32, -0.38)) / 0.05);
                float mask = max(body, trail) * visibility * step(0.5, src.a);
                lift = max(lift, mask * (0.30 + 0.45 * rim));
                dropColor += float3(1.0) * specular * visibility * body;
              }
            }
            outc = outc + float4(ICE_DEEP * lift, lift) * (1.0 - outc.a);
            outc.rgb += dropColor * 0.95;
            outc.a = max(outc.a, sat1(length(dropColor)));
          }

          float halo = 0.0;
          float radius = 9.0 * k;
          for (int i = 0; i < 8; i++) {
            float angle = float(i) * 0.7854;
            halo += sampleImg(p + float2(cos(angle), sin(angle)) * radius).a;
            halo += sampleImg(p + float2(cos(angle + 0.39), sin(angle + 0.39)) * radius * 2.0).a * 0.6;
          }
          halo = sat1(halo / 9.0) * (1.0 - src.a);
          float coverage = sat1(grow / span) * (1.0 - smoothstep(0.0, 0.7, melt));
          float grain = vnoise(p / (5.0 * k)) * 0.6 + vnoise(p / (2.2 * k)) * 0.4;
          float haloAlpha = halo * coverage * smoothstep(0.25, 0.85, grain) * __HALO__;
          outc = outc + float4(ICE_HI * haloAlpha, haloAlpha) * (1.0 - outc.a);
          return half4(outc * fade);
        }
    """.trimIndent())
        .replace("__CELL__", PriceTagWelcomeTheme.Frost.cell.toString())
        .replace("__HALO__", PriceTagWelcomeTheme.Frost.halo.toString())
}

private fun priceTagWelcomeTagSource(): String {
    return (priceTagWelcomeFrostCommon() + """
        uniform float2 size;

        half4 main(float2 p) {
          if (fade <= 0.0) return half4(0.0);
          float radius = size.y * 0.5;
          float2 q = abs(p - size * 0.5) - (size * 0.5 - radius);
          float signedDistance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
          float inside = 1.0 - smoothstep(-0.6, 0.6, signedDistance);
          if (inside <= 0.0) return half4(0.0);
          float needles = 0.0;
          float wet = 0.0;
          float4 c = iceCell(p, __TAG_CELL__ * k, needles, wet);
          float3 col = mix(ICE_HI, ICE_MID, 0.30 + 0.25 * needles);
          col = mix(col, ICE_DEEP, 0.07 * c.y);
          float rimIce = 1.0 - smoothstep(-2.4 * k, -0.2 * k, signedDistance);
          col = mix(col, ICE_MID, 0.35 * (1.0 - rimIce));
          col = mix(col, float3(1.0), 0.65 * c.w);
          col += float3(1.0) * c.z * 0.9;
          float alpha = inside * sat1(c.x * __TAG_ALPHA__ + c.z * c.x);
          float wetAlpha = inside * wet * 0.35;
          float4 outc = float4(col * alpha, alpha);
          outc = outc + float4(mix(ICE_HI, float3(1.0), 0.5) * wetAlpha, wetAlpha) *
            (1.0 - outc.a);
          return half4(outc * fade);
        }
    """.trimIndent())
        .replace("__TAG_CELL__", PriceTagWelcomeTheme.Frost.tagCell.toString())
        .replace("__TAG_ALPHA__", PriceTagWelcomeTheme.Frost.tagAlpha.toString())
}

private fun priceTagWelcomeFrostCommon(): String {
    return """
        uniform float2 org;
        uniform float grow;
        uniform float span;
        uniform float melt;
        uniform float clock;
        uniform float k;
        uniform float fade;
        uniform float seed;

        const float3 ICE_HI = __ICE_HI__;
        const float3 ICE_MID = __ICE_MID__;
        const float3 ICE_DEEP = __ICE_DEEP__;
        const float3 COOL_TINT = __COOL_TINT__;

        float h1(float2 q) {
          return fract(sin(dot(q, float2(127.1, 311.7)) + seed) * 43758.5453);
        }

        float2 h2(float2 q) {
          return fract(sin(float2(
            dot(q, float2(127.1, 311.7)),
            dot(q, float2(269.5, 183.3))
          ) + seed) * 43758.5453);
        }

        float sat1(float value) {
          return clamp(value, 0.0, 1.0);
        }

        float vnoise(float2 q) {
          float2 cell = floor(q);
          float2 fraction = fract(q);
          float2 blend = fraction * fraction * (3.0 - 2.0 * fraction);
          float a = h1(cell);
          float b = h1(cell + float2(1.0, 0.0));
          float c = h1(cell + float2(0.0, 1.0));
          float d = h1(cell + float2(1.0, 1.0));
          return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
        }

        float4 iceCell(float2 p, float cellSize, out float needles, out float wet) {
          float2 grid = p / cellSize;
          float2 gridCell = floor(grid);
          float2 fraction = fract(grid);
          float nearest = 8.0;
          float second = 8.0;
          float2 identity = float2(0.0);
          for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
              float2 offset = float2(float(column), float(row));
              float2 point = offset + h2(gridCell + offset) * 0.85 + 0.075 - fraction;
              float squared = dot(point, point);
              if (squared < nearest) {
                second = nearest;
                nearest = squared;
                identity = gridCell + offset;
              } else if (squared < second) {
                second = squared;
              }
            }
          }
          float edge = sqrt(second) - sqrt(nearest);
          float2 centre = (identity + h2(identity) * 0.85 + 0.075) * cellSize;
          float jitter = (h1(identity + 3.7) - 0.5) * 10.0 * k;
          float reach = length(centre - org) + jitter;
          float ice = sat1((grow - reach) / (2.5 * k));
          float fresh = ice * exp(-max(grow - reach, 0.0) / (9.0 * k));
          float waterline = length(p - org) + (vnoise(p / (6.0 * k)) - 0.5) * 18.0 * k;
          float order = 0.04 + 0.70 * (1.0 - sat1(waterline / span));
          float gone = smoothstep(order, order + 0.10, melt);
          wet = gone * (1.0 - gone) * 4.0 * ice;
          ice *= 1.0 - gone;

          float angle = h1(identity + 7.3) * 6.2832;
          float2 local = (p - centre) / k;
          float firstNeedle = abs(sin(dot(local, float2(cos(angle), sin(angle))) * 1.9));
          float secondNeedle = abs(sin(
            dot(local, float2(cos(angle + 1.047), sin(angle + 1.047))) * 1.3
          ));
          needles = smoothstep(0.9, 1.0, 1.0 - firstNeedle) +
            0.55 * smoothstep(0.93, 1.0, 1.0 - secondNeedle);

          float border = 1.0 - smoothstep(0.0, 0.07, edge);
          float sparkleHash = h1(identity + 1.3);
          float twinkle = pow(
            0.5 + 0.5 * sin(clock * (2.2 + 2.6 * sparkleHash) + sparkleHash * 41.0),
            18.0
          );
          float2 sparklePoint = (p - centre) / k;
          float star = exp(-dot(sparklePoint, sparklePoint) / 0.7) +
            0.55 * exp(-abs(sparklePoint.x) / 0.32 - abs(sparklePoint.y) / 3.2) +
            0.55 * exp(-abs(sparklePoint.y) / 0.32 - abs(sparklePoint.x) / 3.2);
          float sparkle = sparkleHash > 0.74 ? star * twinkle : 0.0;
          return float4(ice, border, sparkle, fresh);
        }
    """.trimIndent()
        .replace("__ICE_HI__", priceTagWelcomeAglColor(PriceTagWelcomeTheme.Frost.iceHi))
        .replace("__ICE_MID__", priceTagWelcomeAglColor(PriceTagWelcomeTheme.Frost.iceMid))
        .replace("__ICE_DEEP__", priceTagWelcomeAglColor(PriceTagWelcomeTheme.Frost.iceDeep))
        .replace("__COOL_TINT__", priceTagWelcomeAglColor(PriceTagWelcomeTheme.Frost.coolTint))
}

private fun priceTagWelcomeAglColor(color: Triple<Double, Double, Double>): String {
    return "float3(" +
        color.first / 255.0 +
        ", " +
        color.second / 255.0 +
        ", " +
        color.third / 255.0 +
        ")"
}
