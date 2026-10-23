package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The accepted SkSL foil translated to AGSL. Android already colour-manages RuntimeShader output,
 * so this version deliberately omits the React Native sRGB-to-Display-P3 compensation matrix.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class EventPassShareFoilShader private constructor() {
    private val shader = RuntimeShader(Source)
    private val foil = FloatArray(4)

    val effect = RenderEffect.createRuntimeShaderEffect(shader, "poster").asComposeRenderEffect()

    fun update(frame: EventPassFrame, pixelScale: Float, posterSize: Float) {
        val turn = if (frame.quietCrossfade) 0.0 else frame.flipAngle
        eventPassShareFoilUniform(
            tiltX = frame.tiltX,
            tiltY = frame.tiltY,
            flipAngle = turn,
            scale = pixelScale.toDouble(),
            output = foil,
        )
        val size = posterSize * pixelScale
        shader.setFloatUniform("res", size, size)
        shader.setFloatUniform("foil", foil[0], foil[1], foil[2], foil[3])
    }

    companion object {
        fun create(): EventPassShareFoilShader = EventPassShareFoilShader()

        private const val Source = """
uniform shader poster;
uniform float2 res;
uniform float4 foil;

const float TAU = 6.2831853;
const float DEG = 0.01745329;
const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  float a = hs(i);
  float b = hs(i + float2(1.0, 0.0));
  float c = hs(i + float2(0.0, 1.0));
  float d = hs(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float3 thinfilm(float opd) {
  return 0.5 + 0.5 * cos(TAU * opd / float3(650.0, 532.0, 450.0));
}

float greenink(float2 q) {
  float4 t = poster.eval(q);
  float3 c = t.a > 0.004 ? t.rgb / t.a : float3(1.0);
  float lu = dot(c, LUMA);
  return smoothstep(0.035, 0.09, c.g - 0.5 * (c.r + c.b)) * (1.0 - smoothstep(0.42, 0.64, lu)) * t.a;
}

float4 over(float4 dst, float3 rgb, float a) {
  return float4(rgb * a + dst.rgb * (1.0 - a), a + dst.a * (1.0 - a));
}

half4 main(float2 p) {
  float amt = foil.z;
  if (amt <= 0.002) return half4(0.0);
  float4 tex = poster.eval(p);
  if (tex.a < 0.01) return half4(0.0);
  float3 c = tex.rgb / tex.a;
  float lu = dot(c, LUMA);
  float sc = foil.w;

  float ink = greenink(p);
  float gold = smoothstep(0.15, 0.26, c.r - c.b) * smoothstep(0.32, 0.48, lu) * (1.0 - smoothstep(0.86, 0.95, lu));
  float paper = smoothstep(0.80, 0.93, lu);

  float2 o = (p - 0.5 * res) / sc;
  float tx = foil.x * DEG;
  float ty = foil.y * DEG;
  float3 nrm = normalize(float3(sin(ty), -sin(tx), 1.0));
  float3 eye = normalize(float3(-o, 650.0));
  float3 lgt = normalize(float3(-0.30, -0.70, 1.0));
  float3 hv = normalize(lgt + eye);
  float2 slope = nrm.xy - hv.xy;

  float flow = 0.85 * vn(o * 0.016 + float2(3.1, 7.7)) + 0.30 * vn(o * 0.055 + float2(11.0, 2.0));
  float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
  float3 film = thinfilm(400.0 + 470.0 * phase);

  float k = dot(o / 125.0, float2(0.97, 0.24)) - (sin(ty) * 0.97 - sin(tx) * 0.24) * 5.2 + 0.15;
  float sheen = exp(-k * k / 0.10);
  float glint = exp(-k * k / 0.010);
  float env = 0.5 + 0.5 * sin(dot(o, float2(0.020, 0.008)) * 6.0 + dot(slope, float2(1.0, 0.6)) * 16.0 + flow * 2.0);
  float refl = 0.16 + 0.34 * env * env + 0.85 * sheen + 0.8 * glint;

  float foilmask = max(ink, gold);
  float2 gp = o / 0.7;
  float2 id = floor(gp);
  float2 f = fract(gp) - 0.5;
  float r1 = hs(id);
  float r2 = hs(id + 17.31);
  float r3 = hs(id + 41.73);
  float2 groove = float2(cos(r1 * TAU), sin(r1 * TAU));
  float tw = pow(max(0.0, cos(dot(slope, groove) * 70.0 + r2 * TAU)), 24.0);
  float sparkle = tw * step(mix(0.72, 0.18, foilmask), r3) * exp(-dot(f, f) * 8.0) * (0.6 + 1.0 * sheen);
  float3 sparkcol = mix(float3(1.0), thinfilm(420.0 + 600.0 * fract(r2 + phase * 0.35)), 0.55);

  float4 acc = float4(0.0);
  acc = over(acc, mix(float3(1.0), film, 0.30), paper * amt * (0.01 + 0.06 * sheen));

  float3 deep = float3(0.06, 0.25, 0.19) * (0.75 + 0.55 * refl);
  float3 holo = mix(float3(0.82, 0.85, 0.84), pow(film, float3(0.8)), 0.80) * (0.42 + 0.78 * refl);
  float lit = clamp((0.40 + 0.50 * env + 0.70 * sheen) * amt, 0.0, 0.95);
  float3 inkcol = deep * (1.0 - lit) + holo * lit + float3(glint * 0.40);
  acc = over(acc, inkcol, ink * amt * 0.96);

  float3 goldcol = float3(0.84, 0.64, 0.33) * (0.50 + 0.70 * refl) + film * 0.10 * sheen + float3(1.0, 0.94, 0.78) * glint * 0.45;
  acc = over(acc, goldcol, gold * amt * 0.92);

  float2 ld = normalize(float2(-0.35, -0.80)) * 0.55 * sc;
  float edge = greenink(p + ld) - greenink(p - ld);
  float emb = smoothstep(0.0, 0.35, amt);
  acc = over(acc, float3(1.0), max(-edge, 0.0) * 0.55 * emb * ink);
  acc = over(acc, float3(0.02, 0.07, 0.05), max(edge, 0.0) * 0.32 * emb * (1.0 - ink));

  float grainmask = 0.12 * paper * sheen + 0.20 * (1.0 - paper) * (1.0 - foilmask) + 1.2 * foilmask;
  acc = over(acc, sparkcol, clamp(sparkle * amt * grainmask, 0.0, 1.0));

  float3 rgb = acc.a > 0.0001 ? acc.rgb / acc.a : float3(0.0);
  float3 outc = clamp(rgb, 0.0, 1.0);
  return half4(outc * acc.a * tex.a, acc.a * tex.a);
}
"""
    }
}

@Composable
internal fun rememberEventPassShareFoilShader(): EventPassShareFoilShader? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { EventPassShareFoilShader.create() }.getOrNull()
    } else {
        null
    }
}

/** Pure React Native `foilUniform` port. The caller may reuse `output` to avoid frame garbage. */
internal fun eventPassShareFoilUniform(
    tiltX: Double,
    tiltY: Double,
    flipAngle: Double,
    scale: Double,
    output: FloatArray = FloatArray(4),
): FloatArray {
    require(output.size >= 4)
    val turn = flipAngle.coerceIn(0.0, 90.0)
    val shadeY = tiltY + turn * 0.2
    val tilt = min(1.0, hypot(tiltX / 6.0, tiltY / 7.5))
    val lit = min(1.0, tilt + sin(turn * PI / 180.0) * 0.8)
    val amount = lit * lit * (3.0 - 2.0 * lit)
    output[0] = tiltX.toFloat()
    output[1] = shadeY.toFloat()
    output[2] = amount.toFloat()
    output[3] = scale.toFloat()
    return output
}
