#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
constexpr sampler arrowRankCardsSampler(coord::normalized,address::clamp_to_edge,filter::linear);
[[ stitchable ]] half4 arrowRankCardsPrisms(float2 xy, float4 q0, float4 q1, float q2, float axis, float reverse, float twitch, float flatMix, texture2d<half> oldFace, texture2d<half> newFace) {
  float q[9]={q0.x,q0.y,q0.z,q0.w,q1.x,q1.y,q1.z,q1.w,q2};
  if (flatMix >= 0.0) return mix(oldFace.sample(arrowRankCardsSampler,xy / float2(375,374)), newFace.sample(arrowRankCardsSampler,xy / float2(375,374)), flatMix);
  float span = axis > 0.5 ? 374.0 : 375.0;
  float across = axis > 0.5 ? xy.y : xy.x;
  float pitch = span / 9.0;
  int physical = int(clamp(floor(across / pitch), 0.0, 8.0));
  int slot = reverse > 0.5 ? 8 - physical : physical;
  float turn = 0.0;
  for (int i = 0; i < 9; i++) { if (i == slot) turn = q[i]; }
  if (turn <= 0.0 && abs(twitch) < 0.001) return oldFace.sample(arrowRankCardsSampler,xy / float2(375,374));
  if (turn == 1.0 && abs(twitch) < 0.001) return newFace.sample(arrowRankCardsSampler,xy / float2(375,374));
  // p > 1 is the pop's overshoot: the arriving face swings a few degrees past square and back.
  float p = clamp(turn + twitch * sin(float(slot) * 1.7 + 0.5) / 120.0, 0.0, 1.2);
  float local = across - float(physical) * pitch;
  if (reverse > 0.5) local = pitch - local;
  // Project the two adjacent faces of a triangular prism. Both textures compress towards the rotating seam.
  float oldWidth = max(0.0, cos(p * 2.0943951));
  float newWidth = max(0.0, cos(abs(1.0 - p) * 2.0943951));
  float seam = pitch * newWidth / max(oldWidth + newWidth, 0.001);
  bool arriving = local < seam;
  float source = arriving ? local * pitch / max(seam, 0.001) : (local - seam) * pitch / max(pitch - seam, 0.001);
  if (reverse > 0.5) source = pitch - source;
  float mapped = float(physical) * pitch + source;
  float2 uv = axis > 0.5 ? float2(xy.x, mapped) : float2(mapped, xy.y);
  half4 col = arriving ? newFace.sample(arrowRankCardsSampler,uv / float2(375,374)) : oldFace.sample(arrowRankCardsSampler,uv / float2(375,374));
  float shade = arriving ? 0.70 + 0.30 * (1.0 - abs(1.0 - p)) : 1.0 - 0.30 * p;
  col.rgb *= shade;
  float edge = min(local, pitch - local);
  float shadow = (1.0 - smoothstep(0.0, 2.0, edge)) * 0.25;
  col.rgb *= 1.0 - shadow;
  float highlight = (1.0 - smoothstep(0.0, 1.5, abs(local - seam))) * 0.3;
  if (arriving) col.rgb += (col.a - col.rgb) * highlight;
  return col;
}
