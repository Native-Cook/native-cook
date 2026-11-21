package com.ngocdevv.nativecook.blocks.gestures.trailparallaxtracker
internal object TrailParallaxTrackerShaders {
 const val LAYER="""

uniform shader imgA;
uniform shader imgB;
uniform float uSplit;
uniform float uOffA;
uniform float uOffB;
uniform float uTop;
uniform float uScA;
uniform float uScB;
uniform float uNA;
uniform float uNB;
uniform float3 uSrcA[6];
uniform float3 uDstA[6];
uniform float3 uSrcB[6];
uniform float3 uDstB[6];

half4 trailSample(float2 xy) {
  bool left = xy.x < uSplit;
  float sc = left ? uScA : uScB;
  // above the art's top edge the first row is repeated: skies stay empty, cropped trunks and the grove's wash run on up
  float2 p = float2((xy.x + (left ? uOffA : uOffB)) * sc, max((xy.y - uTop) * sc, 0.5));
  half4 c = left ? imgA.eval(p) : imgB.eval(p);
  if (c.a < 0.003) return half4(0.0);
  float3 u = float3(c.rgb) / float(c.a);
  float n = left ? uNA : uNB;
  float best = 1e9;
  float3 delta = float3(0.0);
  for (int i = 0; i < 6; i++) {
    if (float(i) < n) {
      float3 s = left ? uSrcA[i] : uSrcB[i];
      float3 e = u - s;
      float dd = dot(e, e);
      if (dd < best) {
        best = dd;
        delta = (left ? uDstA[i] : uDstB[i]) - s;
      }
    }
  }
  float3 o = clamp(u + delta, 0.0, 1.0);
  return half4(half3(o) * c.a, c.a);
}

uniform float uBlur;
half4 main(float2 xy) {
 if (uBlur < 0.1) return trailSample(xy);
 return trailSample(xy)*0.4026 + (trailSample(xy+float2(uBlur,0))+trailSample(xy-float2(uBlur,0)))*0.2442
  +(trailSample(xy+float2(2*uBlur,0))+trailSample(xy-float2(2*uBlur,0)))*0.0545;
}

"""
 const val GRAIN="""

uniform float uScale;
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash2(floor(xy * uScale)) * 2.0 - 1.0;
  float a = 0.03 * abs(g);
  return g > 0.0 ? half4(a, a, a, a) : half4(0.0, 0.0, 0.0, a);
}

"""
}
