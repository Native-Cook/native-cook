package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.cos
import kotlin.math.sin

/** Live medal material. API 33+ samples the original six-tile data strips in AGSL. */
internal class DayOneMedalShader(
    fronts: List<Bitmap>,
    back: Bitmap,
) {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu(fronts, back) else null

    fun draw(
        canvas: Canvas,
        centreX: Float,
        centreY: Float,
        half: Float,
        yaw: Float,
        pitch: Float,
        roll: Float,
        lightX: Float,
        lightY: Float,
        heat: Float,
        charge: Float,
        stamp: Float,
        morph: Float,
        engrave: Float,
        sweep: Float,
        from: Int,
        to: Int,
        alpha: Float,
    ): Boolean {
        val renderer = gpu ?: return false
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)
        renderer.draw(
            canvas = canvas,
            centreX = centreX,
            centreY = centreY,
            half = half,
            rotation = arrayOf(
                floatArrayOf(cr * cy - sr * sp * sy, sr * cy + cr * sp * sy, -cp * sy),
                floatArrayOf(-sr * cp, cr * cp, sp),
                floatArrayOf(cr * sy + sr * sp * cy, sr * sy - cr * sp * cy, cp * cy),
            ),
            lightX = lightX,
            lightY = lightY,
            heat = heat,
            charge = charge,
            stamp = stamp,
            morph = morph,
            engrave = engrave,
            sweep = sweep,
            from = from,
            to = to,
            alpha = alpha,
        )
        return true
    }

    @TargetApi(33)
    private class Gpu(fronts: List<Bitmap>, back: Bitmap) {
        private val shader = RuntimeShader(Source)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            blendMode = BlendMode.SRC_OVER
        }
        private val frontShaders = fronts.map(::bitmapShader)
        private val backShader = bitmapShader(back)
        private val metals = arrayOf(
            floatArrayOf(0.86f, 0.37f, 0.085f),
            floatArrayOf(0.95f, 0.93f, 0.88f),
            floatArrayOf(0.92f, 0.72f, 0.34f),
        )

        init {
            shader.setInputShader("uBack", backShader)
            paint.shader = shader
        }

        fun draw(
            canvas: Canvas,
            centreX: Float,
            centreY: Float,
            half: Float,
            rotation: Array<FloatArray>,
            lightX: Float,
            lightY: Float,
            heat: Float,
            charge: Float,
            stamp: Float,
            morph: Float,
            engrave: Float,
            sweep: Float,
            from: Int,
            to: Int,
            alpha: Float,
        ) {
            val old = from.coerceIn(0, 2)
            val next = to.coerceIn(0, 2)
            shader.setInputShader("uFront", frontShaders[old])
            shader.setInputShader("uFrontNext", frontShaders[next])
            shader.setFloatUniform("uCentre", centreX, centreY)
            shader.setFloatUniform("uHalf", half)
            shader.setFloatUniform("uTile", 512f, 956f)
            shader.setFloatUniform("uLight", lightX, lightY)
            shader.setFloatUniform("uHeat", heat)
            shader.setFloatUniform("uCharge", charge)
            shader.setFloatUniform("uStamp", stamp)
            shader.setFloatUniform("uMorph", morph)
            shader.setFloatUniform("uEngrave", engrave)
            shader.setFloatUniform("uSweep", sweep)
            shader.setFloatUniform("uR0", rotation[0])
            shader.setFloatUniform("uR1", rotation[1])
            shader.setFloatUniform("uR2", rotation[2])
            shader.setFloatUniform("uMetal", metals[old])
            shader.setFloatUniform("uMetalNext", metals[next])
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(centreX - half * 1.9f, centreY - half * 1.9f,
                centreX + half * 1.9f, centreY + half * 1.9f, paint)
        }

        companion object {
            private fun bitmapShader(bitmap: Bitmap): BitmapShader {
                return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                    it.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                }
            }
        }
    }

    private companion object {
        // The 44-step slab march and material equations follow the source 3-D renderer.
        private const val Source = """
            uniform shader uFront;
            uniform shader uFrontNext;
            uniform shader uBack;
            uniform float2 uCentre;
            uniform float uHalf;
            uniform float2 uTile;
            uniform float2 uLight;
            uniform float uHeat;
            uniform float uCharge;
            uniform float uStamp;
            uniform float uMorph;
            uniform float3 uMetal;
            uniform float3 uMetalNext;
            uniform float uEngrave;
            uniform float uSweep;
            uniform float3 uR0;
            uniform float3 uR1;
            uniform float3 uR2;

            const float A = 0.5352;
            const float H = 0.045;
            const float BEVEL = 0.016;
            const float CAM = 6.9;
            const float RELIEF = 0.04;

            float squared(float value) { return value * value; }

            float stadium(float2 p) {
              float2 q = float2(p.x, max(abs(p.y) - (1.0 - A), 0.0));
              return length(q) - A;
            }

            float3 toObject(float3 p) { return float3(dot(uR0, p), dot(uR1, p), dot(uR2, p)); }
            float3 toWorld(float3 p) { return uR0 * p.x + uR1 * p.y + uR2 * p.z; }

            float2 faceUV(float3 p) {
              float2 uv = float2(p.x / (2.0 * A) + 0.5, 0.5 - p.y * 0.5);
              if (p.z < 0.0) { uv.x = 1.0 - uv.x; }
              return clamp(uv * uTile, float2(1.0), uTile - float2(1.0));
            }

            float frontTap(float2 uv, float tile) {
              if (tile > 4.5) { uv = floor(uv) + 0.5; }
              float2 at = float2(uv.x + tile * uTile.x, uv.y);
              if (uMorph < 0.5) { return uFront.eval(at).a; }
              return uFrontNext.eval(at).a;
            }

            float tap(float2 uv, float tile, bool front) {
              if (front) { return frontTap(uv, tile); }
              if (tile > 4.5) { uv = floor(uv) + 0.5; }
              return uBack.eval(float2(uv.x + tile * uTile.x, uv.y)).a;
            }

            float2 fieldUV(float2 uv, bool front) {
              return front
                ? float2(180.0 + mod(uv.x, 150.0), 750.0 + mod(uv.y, 65.0))
                : float2(90.0 + mod(uv.x, 80.0), 600.0 + mod(uv.y, 220.0));
            }

            float existence(float order, bool front) {
              if (order < 0.001) { return 1.0; }
              if (front) {
                float formed = 1.0 - pow(1.0 - clamp(uStamp, 0.0, 1.0), 3.0);
                if (uMorph > 0.0 && uMorph < 1.0) {
                  float level = uMorph < 0.5 ? 1.08 * (1.0 - uMorph / 0.46)
                    : 1.08 * (uMorph - 0.54) / 0.46;
                  formed *= smoothstep(order - 0.04, order + 0.04, level);
                }
                return formed;
              }
              if (uEngrave >= 1.0) { return 1.0; }
              if (uEngrave <= 0.0) { return 0.0; }
              return smoothstep(order - 0.02, order, uEngrave);
            }

            float heatGrain(float2 uv) {
              return sin(uv.x * 0.061 + sin(uv.y * 0.047) * 2.0) * 0.024
                + sin(uv.x * 0.19 - uv.y * 0.081) * 0.012;
            }

            float chargeAt(float order, float grit) {
              if (order < 0.001 || uCharge <= 0.0) { return 0.0; }
              float uneven = (grit - 0.56) * 0.65;
              return smoothstep(-0.10, 0.10, uCharge - order + uneven);
            }

            float slab(float3 p, float lift) {
              float2 w = float2(stadium(p.xy) + BEVEL, abs(p.z) - H - lift + BEVEL);
              return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - BEVEL;
            }

            float restMap(float3 p) {
              float2 uv = faceUV(p);
              float b = p.z < 0.0 ? tap(uv, 4.0, false) : tap(uv, 4.0, true);
              float inside = smoothstep(-0.012, -0.05, stadium(p.xy));
              return slab(p, (b - 0.25) * RELIEF * inside);
            }

            float map(float3 p) {
              float2 uv = faceUV(p);
              bool front = p.z >= 0.0;
              float b = tap(uv, 4.0, front);
              if ((front && (uStamp < 1.0 || (uMorph > 0.0 && uMorph < 1.0))) ||
                  (!front && uEngrave < 1.0)) {
                float made = existence(tap(uv, 5.0, front), front);
                if (made < 1.0) { b = mix(tap(fieldUV(uv, front), 4.0, front), b, made); }
              }
              float inside = smoothstep(-0.012, -0.05, stadium(p.xy));
              return slab(p, (b - 0.25) * RELIEF * inside);
            }

            float3 slabNormal(float3 p) {
              const float e = 0.002;
              return normalize(float3(1.0,-1.0,-1.0) * slab(p + float3(1.0,-1.0,-1.0) * e, 0.0)
                + float3(-1.0,-1.0,1.0) * slab(p + float3(-1.0,-1.0,1.0) * e, 0.0)
                + float3(-1.0,1.0,-1.0) * slab(p + float3(-1.0,1.0,-1.0) * e, 0.0)
                + float3(1.0,1.0,1.0) * slab(p + float3(1.0,1.0,1.0) * e, 0.0));
            }

            float lamp(float3 d, float3 towards, float size) {
              return exp((dot(d, towards) - 1.0) / (size * size * 0.5));
            }

            float3 room(float3 d, float rough, float brushed) {
              float fill = lamp(d, normalize(float3(0.06, 0.20, 0.98)), mix(0.48, 0.98, rough));
              float key = lamp(d, normalize(float3(-0.52 + uLight.x * 0.28, 0.60 + uLight.y * 0.2, 0.61)), mix(0.16, 0.80, rough));
              float3 low = normalize(float3(-0.10 + uLight.x * 0.22, -0.17 + uLight.y * 0.2, 0.98));
              float2 off = d.xy - low.xy;
              float band = exp(-off.x * off.x / 0.20 - off.y * off.y / 0.0042) * step(0.0, d.z);
              float flash = mix(lamp(d, low, mix(0.085, 0.42, rough)), band, brushed);
              float rim = lamp(d, normalize(float3(0.86 + uLight.x * 1.8, 0.12, -0.42)), mix(0.14, 0.50, rough));
              float stripWidth = 0.10 + rough * 0.45;
              float strip = exp(-squared((d.y - 0.80) / stripWidth)) * smoothstep(-0.25, 0.45, d.z);
              return float3(1.0, 0.975, 0.94) * fill * 0.62 / (0.6 + rough * 1.6)
                + float3(1.0, 0.965, 0.91) * key * 2.3 / (0.6 + rough * 1.6)
                + float3(1.0, 0.88, 0.66) * flash * mix(1.7 / (0.5 + rough * 2.4), 2.6, brushed)
                + float3(1.0, 0.98, 0.95) * strip * 0.22 / (0.7 + rough * 1.4)
                + float3(0.86, 0.91, 1.0) * rim * 0.30 / (0.7 + rough * 1.2)
                + float3(0.018);
            }

            half4 main(float2 xy) {
              float2 q = (xy - uCentre) / uHalf;
              if (length(q) > 1.9) { return half4(0.0); }
              float2 projected = float2(q.x, -q.y);
              float2 spine = uR1.xy * (1.0 - A);
              float along = clamp(dot(projected, spine) / max(dot(spine, spine), 0.00001), -1.0, 1.0);
              float2 radial = projected - spine * along;
              float shortLength = length(uR2.xy);
              float2 shortAxis = shortLength > 0.00001 ? uR2.xy / shortLength : float2(1.0, 0.0);
              float narrow = A * abs(uR2.z) + H * shortLength;
              float2 axes = float2(dot(radial, shortAxis), dot(radial, float2(-shortAxis.y, shortAxis.x)));
              float radialLength = length(axes);
              float radius = radialLength / max(length(axes / float2(max(narrow, H), A)), 0.00001);
              float away = radialLength < 0.00001 ? -narrow : radialLength - radius;
              float warm = max(uHeat, uCharge * 0.22);
              float halo = exp(-max(away, 0.0) * mix(12.0, 6.0, warm))
                * (1.0 - smoothstep(1.4, 1.9, length(q))) * max(uR2.z, 0.0) * (0.07 + warm);
              float3 haloColour = mix(float3(1.0, 0.50, 0.12), float3(1.0, 0.80, 0.52), warm) * halo;
              if (length(q) > 1.18) { return half4(half3(haloColour), half(min(halo * 0.22, 1.0))); }

              float3 eye = float3(0.0, 0.0, CAM);
              float3 ray = normalize(float3(q.x, -q.y, 0.0) - eye);
              float3 ro = toObject(eye);
              float3 rd = toObject(ray);
              float t = CAM - 1.12;
              float nearest = 1.0;
              bool hit = false;
              float3 p = ro;
              for (int i = 0; i < 44; i++) {
                p = ro + rd * t;
                float d = restMap(p);
                nearest = min(nearest, d);
                if (d < 0.0007) { hit = true; break; }
                t += d * 0.86;
                if (t > CAM + 1.12) { break; }
              }
              float edge = hit ? 1.0 : 1.0 - smoothstep(0.0, 0.009, nearest);
              if (edge <= 0.0) { return half4(half3(haloColour), half(min(halo * 0.22, 1.0))); }
              bool front = p.z >= 0.0;
              bool changing = front ? (uStamp < 1.0 || (uMorph > 0.0 && uMorph < 1.0)) : uEngrave < 1.0;
              if (hit && changing) {
                for (int k = 0; k < 10; k++) {
                  float correction = map(p);
                  if (abs(correction) < 0.00015) { break; }
                  t += correction * 0.86;
                  p = ro + rd * t;
                }
              }

              float3 normal = slabNormal(p);
              float2 uv = faceUV(p);
              float facing = smoothstep(0.55, 0.92, abs(normal.z))
                * smoothstep(-0.006, -0.035, stadium(p.xy));
              float2 tilt = float2(tap(uv, 0.0, front), tap(uv, 1.0, front)) * 2.0 - 1.0;
              float3 detail = normalize(float3(tilt, sqrt(max(1.0 - dot(tilt, tilt), 0.0))));
              float tint = tap(uv, 2.0, front);
              float grit = tap(uv, 3.0, front);
              float order = tap(uv, 5.0, front);
              float made = existence(order, front);
              if (made < 1.0) {
                float2 clean = fieldUV(uv, front);
                float2 grainTilt = float2(tap(clean, 0.0, front), tap(clean, 1.0, front)) * 2.0 - 1.0;
                detail = normalize(mix(normalize(float3(grainTilt, sqrt(max(1.0 - dot(grainTilt, grainTilt), 0.0)))), detail, made));
                grit = mix(tap(clean, 3.0, front), grit, made);
                tint *= made;
              }
              if (!front) { detail = float3(-detail.x, detail.y, -detail.z); }
              detail.xy *= front ? mix(0.5, 1.0, tint) : 1.0;
              normal = normalize(mix(normal, normalize(detail), facing));

              float3 iron = float3(0.024, 0.024, 0.027);
              float3 gold = uMorph < 0.5 ? uMetal : uMetalNext;
              float3 satin = float3(0.062, 0.062, 0.066);
              float3 f0 = front ? mix(iron, gold, tint * facing) : mix(iron, satin + float3(0.24) * tint, facing);
              float rough = mix(0.46, grit, facing);
              float3 worldNormal = toWorld(normal);
              float nv = clamp(dot(worldNormal, -ray), 0.0, 1.0);
              float3 fresnel = f0 + (float3(1.0) - f0) * pow(1.0 - nv, 5.0) * (1.0 - rough * 0.7);
              float3 colour = fresnel * room(reflect(ray, worldNormal), rough, front ? tint * facing : 0.0);
              colour *= mix(0.52, 1.0, smoothstep(-1.05, 0.55, toWorld(p).y));
              colour += f0 * 0.11;

              float sweepBand = exp(-squared((p.x + p.y * 0.32 - uSweep) / 0.085));
              colour += facing * sweepBand * float3(1.0, 0.96, 0.88) * (0.22 + 1.5 * (front ? tint : 0.25));
              if (front && uHeat > 0.001) {
                float3 ember = mix(float3(1.0, 0.26, 0.03), float3(1.0, 0.88, 0.66), uHeat * uHeat);
                colour += tint * uHeat * ember * (0.7 + 6.0 * uHeat);
              }
              if (front && uCharge > 0.0) {
                float climbed = chargeAt(order, 0.56 - heatGrain(uv));
                float behind = uCharge - order - heatGrain(uv);
                float crest = exp(-squared(behind / 0.14));
                float3 hot = mix(float3(0.55, 0.012, 0.001), float3(1.0, 0.87, 0.65), crest);
                colour += tint * climbed * hot * (0.65 + crest * 1.7);
              }
              if (!front && uEngrave > 0.0 && uEngrave < 1.0 && order > 0.001) {
                float age = uEngrave - order;
                colour += (0.18 + tint * 0.82) * exp(-squared(age / 0.012)) * float3(5.0, 2.4, 0.65);
              }
              float molten = sin(3.141593 * uMorph) * uStamp;
              if (front && molten > 0.0) {
                float bath = exp(-squared(squared(p.x / 0.28)) - squared(squared((p.y + 0.04) / 0.62)));
                colour += molten * bath * float3(0.48, 0.07, 0.007) + tint * molten * float3(3.8, 2.0, 0.65);
              }
              colour = colour / (1.0 + colour * 0.22);
              colour = pow(max(colour, float3(0.0)), float3(0.4545));
              return half4(half3(colour * edge + haloColour * (1.0 - edge)), half(edge));
            }
        """
    }
}
