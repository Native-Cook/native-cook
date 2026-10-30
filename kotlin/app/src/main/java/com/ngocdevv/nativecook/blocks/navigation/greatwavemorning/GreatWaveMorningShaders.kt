package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

internal object GreatWaveMorningShaders {
    // RuntimeShader coordinates are physical pixels; normalize to RN's logical screen points.
    val PAPER  =  """
    uniform float scale;
    float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
    half4 main(float2 xy) {
    float2 p = xy / scale;
    float grain = h(floor(p * 0.75));
    float fiber = h(floor(p * float2(0.08, 1.4)));
    return half4(0, 0, 0, (grain * 0.55 + fiber * 0.45) * 0.07);
    }
    """.trimIndent()
}
