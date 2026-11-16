package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

/**
 * Film grain over the wallpaper, one speck per device pixel, +-3.5 % of white or black (RN `BeamShapeTorch.tsx`). The wallpaper is
 * a smooth generated image with soft folds: the grain breaks its banding and the JPEG's weave and gives the surface a material,
 * photographic grain. `uPx` is device pixels per dp.
 */
internal const val BeamShapeTorchGrainSource = """
uniform float uPx;
float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
half4 main(float2 xy) {
  float g = hash(floor(xy * uPx)) - 0.5;
  float a = abs(g) * 0.07;
  float lum = g > 0.0 ? 1.0 : 0.0;
  return half4(half3(lum * a), half(a));
}
"""
