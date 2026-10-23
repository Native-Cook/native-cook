package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val MOUTH_AGSL = """
uniform float uLeak;
uniform float uMouth;
uniform float uEdgeY;
half4 main(float2 p) {
  float across = clamp(p.x / 216.0, 0.0, 1.0);
  float bowl = sqrt(max(0.0, sin(across * 3.14159265)));
  float lit = (0.30 + 0.65 * bowl) * (0.65 + 0.35 * uMouth);
  float streak = 0.5 + 0.5 * sin(p.x * 0.14 + (p.y - uEdgeY) * 0.9);
  float3 silver = float3(0.26, 0.34, 0.33) * lit + float3(0.10, 0.12, 0.11) * streak * bowl;
  silver += float3(0.08, 0.06, 0.035) * uLeak * bowl;
  return half4(half3(silver), 1.0);
}
"""
