package com.ngocdevv.nativecook.blocks.modals.firstlootreward

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class FirstLootRewardRays {
    val shader=RuntimeShader("""
uniform float cx; uniform float cy; uniform float turn; uniform float alpha;
uniform float tx; uniform float ty;
uniform float waveR; uniform float waveA;
half4 main(float2 xy) {
  float2 d = xy - float2(cx - tx * 10.0, cy - ty * 10.0);
  float r = length(d);
  float a = atan(d.y, d.x) / 6.2831853 + 0.5;
  float f = fract(a * 14.0 + turn * 2.0);
  float edge = 0.6 / max(r * 6.2831853 / 14.0, 1.0);
  float lit = smoothstep(0.42 - edge, 0.42 + edge, f) * (1.0 - smoothstep(0.98 - edge, 0.98 + edge, f));
  float fall = (1.0 - smoothstep(150.0, 470.0, r)) * smoothstep(40.0, 120.0, r);
  // the burst's shockwave lights the wedges as it passes
  float wave = exp(-pow((r - waveR) / 46.0, 2.0)) * waveA;
  float k = lit * (fall + wave * 1.6) * alpha;
  float3 page = float3(0.035, 0.035, 0.035);
  float3 ray = float3(0.06, 0.048, 0.082);
  float3 col = mix(page, ray, k);
  return half4(half3(col), 1.0);
}""")
    fun update(x: Float,y: Float,alpha: Float,tx: Float,ty: Float): RuntimeShader {
        shader.setFloatUniform("cx",x); shader.setFloatUniform("cy",y)
        shader.setFloatUniform("turn",0f); shader.setFloatUniform("alpha",alpha)
        shader.setFloatUniform("tx",tx); shader.setFloatUniform("ty",ty)
        shader.setFloatUniform("waveR",0f); shader.setFloatUniform("waveA",0f)
        return shader
    }
}
