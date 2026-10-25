/*
 Slide Projector Gallery — the living-room wall, the projected slide and the projector's beam,
 in one full-screen pass (screen coordinates, pt).

 Wall: warm dark plaster (two octaves of value noise for mottling, a fine grain) with a faint
 wallpaper stripe, darker towards the corners.

 Slide: a keystoned trapezoid (the projector sits low, so the top edge is wider). Each pixel is
 mapped back into the slide (u across the row's own width, v down), then sampled from an atlas of
 the five slides at three focus levels (sharp, σ 4 px, σ 12 px on 600 × 400 tiles), mixed by
 `focus` (0 sharp … 1 soft). The light is warm (tungsten), brighter in the middle (hot spot) and
 darker at the corners (vignette), multiplied by the wall's own texture so it reads as light on
 plaster, with a little spill around the frame. `drop` slides the picture down in the gate (pt)
 while the next slide falls in; `bright` is the lamp through the gate (0 while the slide changes).

 Beam: the cone from the lens to the slide's outer corners, seen from behind the projector: dense
 near the lens, thinning towards the wall, with soft edges and faint shafts.

 Uniforms: size; proj (centre x, top, width, height); key (top width ×, bottom width ×); lens
 (x, y); slide (atlas row, bright, drop pt, focus); look (flicker, image scale, p3, unused);
 tile (atlas tile w, h in px).
 Skia treats RuntimeEffect output as Display P3 on iOS: constant colours go through toP3; the
 atlas samples arrive in the output space already.
 */
export const PROJECTOR_SKSL = `
uniform shader atlas;
uniform float2 size;
uniform float4 proj;
uniform float2 key;
uniform float2 lens;
uniform float4 slide;
uniform float4 look;
uniform float2 tile;

float3 toP3(float3 c) {
  if (look.z < 0.5) return c;
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}

float3 tileAt(float2 uv, float col) {
  float2 m = 1.5 / tile;
  uv = clamp(uv, m, 1.0 - m);
  return atlas.eval((float2(col, slide.x) + uv) * tile).rgb;
}

half4 main(float2 p) {
  // Wall.
  float mott = vnoise(p * 0.012) * 0.6 + vnoise(p * 0.045) * 0.4;
  float grain = hash(floor(p * 1.5)) - 0.5;
  float stripe = 0.5 + 0.5 * sin(p.x * 0.33);
  float tex = 0.86 + 0.14 * mott + 0.035 * grain;
  float3 wall = toP3(float3(0.165, 0.128, 0.108)) * tex * (0.97 + 0.03 * stripe);
  float2 vc = (p - float2(size.x * 0.5, size.y * 0.38)) / size;
  wall *= 1.0 - 0.9 * dot(vc, vc);

  float3 lamp = toP3(float3(1.0, 0.9, 0.74));
  float lampOn = slide.y * look.x;

  // The keystoned slide.
  float v = (p.y - proj.y) / proj.w;
  float hw = proj.z * 0.5 * mix(key.x, key.y, clamp(v, 0.0, 1.0));
  float u = (p.x - proj.x) / (2.0 * hw) + 0.5;
  float soft = 0.8 + slide.w * 5.0;
  float ex = min(u, 1.0 - u) * 2.0 * hw;
  float ey = min(v, 1.0 - v) * proj.w;
  float inside = smoothstep(-soft, soft, min(ex, ey));
  float3 col = wall;
  if (inside > 0.0) {
    float2 c = float2(u, v) - 0.5;
    float2 uv = c / look.y + 0.5;
    uv.y -= slide.z / proj.w;
    float3 img;
    float f = clamp(slide.w, 0.0, 1.0);
    if (f < 0.5) img = mix(tileAt(uv, 0.0), tileAt(uv, 1.0), f * 2.0);
    else img = mix(tileAt(uv, 1.0), tileAt(uv, 2.0), f * 2.0 - 1.0);
    // Above the dropping slide the gate is empty: open light.
    float gate = step(uv.y, 0.0);
    img = mix(img, float3(0.92), gate);
    float vig = 1.0 - 0.42 * dot(c * float2(1.6, 1.9), c * float2(1.6, 1.9));
    float hot = 1.0 + 0.1 * exp(-dot(c, c) * 9.0);
    float3 light = img * lamp * vig * hot * lampOn;
    col = wall + light * (0.8 + 0.2 * tex) * inside;
  }
  // Light spilling around the frame.
  float2 q = float2(u - 0.5, v - 0.5);
  float d = length(max(abs(q) * float2(2.0 * hw, proj.w) - float2(hw, proj.w * 0.5), 0.0));
  col += lamp * exp(-d / 26.0) * 0.07 * lampOn * (1.0 - inside);

  // The beam, seen from behind the projector: from the lens to the slide's lower corners (the
  // picture covers the rest of the cone).
  float bottom = proj.y + proj.w;
  float span = lens.y - bottom;
  float s = clamp((lens.y - p.y) / span, 0.0, 1.0);
  if (p.y < lens.y && p.y > bottom - proj.w * 0.5) {
    float bw = mix(11.0, proj.z * 0.5 * key.y, s);
    if (p.y < bottom) bw = proj.z * 0.5 * mix(key.x, key.y, clamp((p.y - proj.y) / proj.w, 0.0, 1.0));
    float dx = abs(p.x - lens.x) / bw;
    float edge = 1.0 - smoothstep(0.72, 1.0, dx);
    float shafts = 0.72 + 0.28 * vnoise(float2((p.x - lens.x) / max(bw, 1.0) * 9.0, s * 1.5));
    float dens = 0.2 * pow(1.0 - s, 1.5) + 0.03;
    // Over the picture the haze thins out quickly.
    dens *= 1.0 - 0.8 * smoothstep(0.0, proj.w * 0.5, bottom - p.y);
    dens *= p.y < bottom ? 0.35 : 1.0;
    col += lamp * dens * edge * shafts * lampOn;
  }
  // The lens itself glows.
  float2 dl = p - lens;
  col += lamp * exp(-dot(dl, dl) / 260.0) * 0.55 * lampOn;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
`;
