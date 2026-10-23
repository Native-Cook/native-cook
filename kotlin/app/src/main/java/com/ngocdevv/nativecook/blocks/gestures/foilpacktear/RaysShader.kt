package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val RAYS_AGSL = """
uniform float uHeat;
uniform float uClock;
uniform float uAge;
half4 main(float2 p) {
  if (uHeat < 0.001) { return half4(0.0); }
  float2 q = p - float2(201.0, 364.0);
  float radius = length(q);
  float theta = atan(q.y, q.x);
  float phase = uClock * 6.28318530718 / 13.0;
  float rayA = pow(max(0.0, sin(theta * 13.0 + phase)), 8.0);
  float rayB = pow(max(0.0, sin(theta * 7.0 - phase)), 14.0);
  float paneFade = (1.0 - smoothstep(132.0, 151.0, abs(q.x))) * (1.0 - smoothstep(222.0, 244.0, abs(q.y)));
  float pulse = uAge < 0.0 ? 0.0 : (1.0 - smoothstep(0.05, 0.60, uAge)) * smoothstep(0.0, 0.05, uAge);
  float settling = uAge < 0.0 ? 0.0 : 1.0 - smoothstep(0.15, 1.4, uAge);
  float rays = (rayA + rayB * 0.32) * (0.15 + settling * 0.20) * paneFade * smoothstep(20.0, 120.0, radius);
  float halo = exp(-dot(q / float2(134.0, 183.0), q / float2(134.0, 183.0)) * 0.8) * paneFade * 0.13;
  float flash = exp(-radius * radius / 24000.0) * (1.0 - smoothstep(165.0, 220.0, radius)) * pulse * 1.35;
  float alpha = (rays + halo + flash) * uHeat;
  return half4(half3((float3(1.0, 0.79, 0.43) * (rays + halo) + float3(1.0, 0.95, 0.77) * flash) * uHeat), half(alpha));
}
"""
