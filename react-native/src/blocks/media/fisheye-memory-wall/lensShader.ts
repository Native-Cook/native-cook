import { Skia } from '@shopify/react-native-skia';

/*
 The fisheye lens over the repeating wall (a port of FisheyeMemoryWall.metal). Every screen point is
 traced back to the wall (wall = pan + (p − c) · f(d), d = the point's radius in the lens ellipse) and
 the atlas of one wall period is sampled with repeat tiling. Image children arrive in the surface's
 colour space, so only the computed colours (near-black, white, grey) are added on top; they read the
 same in sRGB and Display P3.

 lens: centre x, y and radii; view: wall point under the centre (x, y), bulge k, atlas texels per
 screen pixel; wall: period w, h, one screen pixel in points, atlas pixels per point; focus: the
 focused tile's rect in the period; ring: its corner radius and the ring's opacity.
*/
const LENS_SKSL = `
uniform shader atlas;
uniform float4 lens;
uniform float4 view;
uniform float4 wall;
uniform float4 focus;
uniform float2 ring;
uniform float4 background;

float lensF(float d, float k) {
  float d2 = d * d;
  return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 / 3.0 + 2.284 * d2 * d2 * d2 / 7.0);
}

float lensRadial(float d, float k) {
  float d2 = d * d;
  return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 + 2.284 * d2 * d2 * d2);
}

half3 tap(float2 p) {
  float2 q = (p - lens.xy) / lens.zw;
  float d = min(length(q), 1.0);
  float2 w = view.xy + (p - lens.xy) * lensF(d, view.z);
  w = w - floor(w / wall.xy) * wall.xy;
  return atlas.eval(w * wall.w).rgb;
}

half4 main(float2 position) {
  float2 q = (position - lens.xy) / lens.zw;
  float d = length(q);
  float minify = lensRadial(min(d, 1.0), view.z) * view.w;
  half3 color;
  if (minify > 1.4) {
    float o = wall.z * 0.25;
    color = (tap(position + float2(-o, -o)) + tap(position + float2(o, -o))
           + tap(position + float2(-o, o)) + tap(position + float2(o, o))) * 0.25;
  } else {
    color = tap(position);
  }
  if (ring.y > 0.001) {
    float dc = min(d, 1.0);
    float2 w = view.xy + (position - lens.xy) * lensF(dc, view.z);
    w = w - floor(w / wall.xy) * wall.xy;
    float2 halfSize = focus.zw * 0.5;
    float2 qb = abs(w - (focus.xy + halfSize)) - halfSize + ring.x;
    float sd = length(max(qb, 0.0)) + min(max(qb.x, qb.y), 0.0) - ring.x;
    float px = wall.z * lensRadial(dc, view.z);
    float line = 1.0 - smoothstep(0.9, 0.9 + px, abs(sd - 2.2));
    float glow = exp(-max(sd - 2.2, 0.0) * 0.35) * step(0.0, sd) * 0.18;
    color = mix(color, half3(1.0), half(ring.y * min(1.0, line * 0.92 + glow)));
  }
  float vignette = 1.0 - 0.82 * smoothstep(0.7, 1.0, d);
  color *= half(vignette);
  float2 sheen = (position - float2(lens.x - 0.36 * lens.z, lens.y - 0.42 * lens.w)) / (0.9 * lens.z);
  color += half3(0.07 * exp(-dot(sheen, sheen) * 2.2) * (1.0 - smoothstep(0.8, 1.0, d)));
  float outside = smoothstep(0.985, 1.02, d);
  color = mix(color, half3(background.rgb), half(outside));
  return half4(color, 1.0);
}
`;

export const LENS_EFFECT = Skia.RuntimeEffect.Make(LENS_SKSL);
