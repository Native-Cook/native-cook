package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val LIP_AGSL = """
uniform shader uMaps;
uniform float4 uColour;
half4 main(float2 p) {
  if (p.x < 0.0 || p.x > 216.0) { return half4(0.0); }
  float2 uv = clamp(p * 3.0, float2(0.6), float2(647.4, 1127.4));
  float coverage = uMaps.eval(float2(uv.x + 1944.0, uv.y)).r;
  float alpha = coverage * uColour.a;
  return half4(half3(uColour.rgb * alpha), half(alpha));
}
"""
