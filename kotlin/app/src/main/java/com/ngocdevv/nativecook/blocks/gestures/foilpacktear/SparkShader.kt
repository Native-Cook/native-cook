package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val SPARK_AGSL = """
uniform shader uSpark;
uniform float uSize;
half4 main(float2 p) {
  float2 uv = p * (96.0 / max(uSize, 0.1)) + 48.0;
  half4 texel = uSpark.eval(uv);
  float glow = pow(max(float(texel.a), 0.0), 0.60);
  float support = 1.0 - smoothstep(42.0, 48.0, max(abs(uv.x - 48.0), abs(uv.y - 48.0)));
  float alpha = glow * support;
  return half4(half3(float3(1.0, 0.93, 0.76) * alpha), half(alpha));
}
"""
