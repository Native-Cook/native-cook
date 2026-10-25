package com.ngocdevv.nativecook.blocks.media.slideprojectorgallery

/*
 Slide Projector Gallery — the living-room wall, the projected slide and the projector's beam in
 one full-screen pass (screen coordinates, dp). A port of projectorShader.ts with the same maths;
 the Display P3 conversion of the iOS version is gone (look.z is unused here).

 Wall: warm dark plaster (two octaves of value noise, a fine grain, a faint wallpaper stripe),
 darker towards the corners. Slide: a keystoned trapezoid; each pixel is mapped back into the
 slide and sampled from an atlas of the five slides at three focus levels (sharp, σ 4 px, σ 12 px
 on 600 × 400 tiles), mixed by `focus`, lit warm with a hot spot and a vignette, multiplied by the
 wall's texture, with a little spill around the frame. `drop` slides the picture down in the gate;
 `bright` is the lamp through the gate. Beam: the cone from the lens to the slide's lower corners,
 dense near the lens, with soft edges and faint shafts; the lens glows.

 Uniforms: size; proj (centre x, top, width, height); key (top width ×, bottom width ×); lens;
 slide (atlas row, bright, drop dp, focus); look (flicker, image scale, unused, unused); tile.
 */
internal const val SLIDE_PROJECTOR_AGSL = """
uniform shader atlas;
uniform float2 size;
uniform float4 proj;
uniform float2 key;
uniform float2 lens;
uniform float4 slide;
uniform float4 look;
uniform float2 tile;

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
  float mott = vnoise(p * 0.012) * 0.6 + vnoise(p * 0.045) * 0.4;
  float grain = hash(floor(p * 1.5)) - 0.5;
  float stripe = 0.5 + 0.5 * sin(p.x * 0.33);
  float tex = 0.86 + 0.14 * mott + 0.035 * grain;
  float3 wall = float3(0.165, 0.128, 0.108) * tex * (0.97 + 0.03 * stripe);
  float2 vc = (p - float2(size.x * 0.5, size.y * 0.38)) / size;
  wall *= 1.0 - 0.9 * dot(vc, vc);

  float3 lamp = float3(1.0, 0.9, 0.74);
  float lampOn = slide.y * look.x;

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
    float gate = step(uv.y, 0.0);
    img = mix(img, float3(0.92), gate);
    float vig = 1.0 - 0.42 * dot(c * float2(1.6, 1.9), c * float2(1.6, 1.9));
    float hot = 1.0 + 0.1 * exp(-dot(c, c) * 9.0);
    float3 light = img * lamp * vig * hot * lampOn;
    col = wall + light * (0.8 + 0.2 * tex) * inside;
  }
  float2 q = float2(u - 0.5, v - 0.5);
  float d = length(max(abs(q) * float2(2.0 * hw, proj.w) - float2(hw, proj.w * 0.5), 0.0));
  col += lamp * exp(-d / 26.0) * 0.07 * lampOn * (1.0 - inside);

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
    dens *= 1.0 - 0.8 * smoothstep(0.0, proj.w * 0.5, bottom - p.y);
    dens *= p.y < bottom ? 0.35 : 1.0;
    col += lamp * dens * edge * shafts * lampOn;
  }
  float2 dl = p - lens;
  col += lamp * exp(-dot(dl, dl) / 260.0) * 0.55 * lampOn;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
