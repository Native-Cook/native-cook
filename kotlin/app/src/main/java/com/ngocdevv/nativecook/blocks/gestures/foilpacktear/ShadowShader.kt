package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val SHADOW_AGSL = """
uniform float2 uExtent;
uniform float uStrength;
half4 main(float2 p) {
  float2 q = abs(p) - uExtent + 9.0;
  float signedD = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 9.0;
  float reach = max(signedD, 0.0);
  float alpha = exp(-reach * reach / 190.0) * (1.0 - smoothstep(23.0, 29.0, reach)) * uStrength;
  return half4(0.0, 0.0, 0.0, half(alpha));
}
"""
