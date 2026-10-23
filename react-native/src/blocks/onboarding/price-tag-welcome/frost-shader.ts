/**
 * Price Tag Welcome — the frost. Two SkSL runtime effects, compiled once:
 *
 * STICKER_SKSL  drawn over a product sticker (child shader `img` = the same render the screen shows).
 *               Ice crystals grow cell by cell from the tag outwards; under them the product is
 *               desaturated and cooled, needles and cell borders texture the rime, a few facets
 *               twinkle, the newest cells flash as the front passes, and a faint rime halo forms on
 *               the page around the sticker. Thawing melts the farthest cells first, with a wet sheen,
 *               while drops run down the product.
 * TAG_SKSL      the same ice over a price tag (a pill), milky enough that the price ghosts through.
 *
 * Every pixel is a pure function of the uniforms (seeded hashes, no state), so autoPlay frames repeat.
 * Lengths are in points; `k` = screen width / 402 scales every feature size.
 * iOS treats a runtime effect's output as Display P3, so the colour constants are converted from
 * sRGB here, once, in JS (the child image arrives already in the working space).
 */

import { Skia } from '@shopify/react-native-skia';

import { frost } from './theme';

/** sRGB (0–255) → the same colour's Display P3 coordinates, as an SkSL float3 literal. */
function p3(c: readonly number[]) {
  const lin = c.map((v) => {
    const x = v / 255;
    return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
  });
  const m = [
    [0.8225, 0.1774, 0.0],
    [0.0332, 0.9669, 0.0],
    [0.0171, 0.0724, 0.9108],
  ];
  const out = m.map((r) => {
    const l = Math.max(0, r[0] * lin[0] + r[1] * lin[1] + r[2] * lin[2]);
    return l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
  });
  return `float3(${out.map((v) => v.toFixed(4)).join(', ')})`;
}

// Shared helpers. Identifiers stay clear of WGSL builtin names (no `distance`, `select`, …).
const COMMON = `
uniform float2 org;
uniform float grow;
uniform float span;
uniform float melt;
uniform float clock;
uniform float k;
uniform float fade;
uniform float seed;

const float3 ICE_HI = ${p3(frost.iceHi)};
const float3 ICE_MID = ${p3(frost.iceMid)};
const float3 ICE_DEEP = ${p3(frost.iceDeep)};
const float3 COOL_TINT = ${p3(frost.coolTint)};

float h1(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7)) + seed) * 43758.5453); }
float2 h2(float2 q) {
  return fract(sin(float2(dot(q, float2(127.1, 311.7)), dot(q, float2(269.5, 183.3))) + seed) * 43758.5453);
}
float sat1(float v) { return clamp(v, 0.0, 1.0); }

// Value noise, two octaves (rime halo).
float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = h1(i);
  float b = h1(i + float2(1.0, 0.0));
  float c = h1(i + float2(0.0, 1.0));
  float d = h1(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// One ice cell: amount frozen (0…1), border closeness, needles, sparkle, fresh flash, wet sheen.
// Returns float4(ice, border, sparkle, fresh) and writes needles / wet through the out params.
float4 iceCell(float2 p, float cs, out float needles, out float wet) {
  float2 g = p / cs;
  float2 ip = floor(g);
  float2 fp = fract(g);
  float d1 = 8.0;
  float d2 = 8.0;
  float2 id = float2(0.0);
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float2 o = float2(float(i), float(j));
      float2 r = o + h2(ip + o) * 0.85 + 0.075 - fp;
      float dd = dot(r, r);
      if (dd < d1) { d2 = d1; d1 = dd; id = ip + o; }
      else if (dd < d2) { d2 = dd; }
    }
  }
  float edge = sqrt(d2) - sqrt(d1);
  float2 centre = (id + h2(id) * 0.85 + 0.075) * cs;
  float jit = (h1(id + 3.7) - 0.5) * 10.0 * k;
  float reach = length(centre - org) + jit;

  // Growth: a whole cell crystallises as the front passes it (a 2.5 pt ramp ≈ 20 ms).
  float ice = sat1((grow - reach) / (2.5 * k));
  float fresh = ice * exp(-max(grow - reach, 0.0) / (9.0 * k));
  // Thaw: a soft, ragged waterline recedes from the outside in (per pixel, not per cell, so it
  // reads as liquid); the tag goes last.
  float wl = length(p - org) + (vnoise(p / (6.0 * k)) - 0.5) * 18.0 * k;
  float order = 0.04 + 0.70 * (1.0 - sat1(wl / span));
  float gone = smoothstep(order, order + 0.10, melt);
  wet = gone * (1.0 - gone) * 4.0 * ice;
  ice *= 1.0 - gone;

  // Needles: fine parallel striations in two directions 60° apart, oriented per cell.
  float a = h1(id + 7.3) * 6.2832;
  float2 lp = (p - centre) / k;
  float n1 = abs(sin(dot(lp, float2(cos(a), sin(a))) * 1.9));
  float n2 = abs(sin(dot(lp, float2(cos(a + 1.047), sin(a + 1.047))) * 1.3));
  needles = smoothstep(0.9, 1.0, 1.0 - n1) + 0.55 * smoothstep(0.93, 1.0, 1.0 - n2);

  float border = 1.0 - smoothstep(0.0, 0.07, edge);

  // A quarter of the cells carry a facet that twinkles: a small four-point star.
  float hs = h1(id + 1.3);
  float tw = pow(0.5 + 0.5 * sin(clock * (2.2 + 2.6 * hs) + hs * 41.0), 18.0);
  float2 sp = (p - centre) / k;
  float star = exp(-dot(sp, sp) / 0.7) + 0.55 * exp(-abs(sp.x) / 0.32 - abs(sp.y) / 3.2) + 0.55 * exp(-abs(sp.y) / 0.32 - abs(sp.x) / 3.2);
  float sparkle = hs > 0.74 ? star * tw : 0.0;
  return float4(ice, border, sparkle, fresh);
}
`;

export const STICKER_SKSL =
  COMMON +
  `
uniform shader img;
uniform float2 box; // sticker render: origin (canvas pt) is (M, M), size box
uniform float margin;

half4 main(float2 p) {
  if (fade <= 0.0) return half4(0.0);
  float4 src = img.eval(p);
  float needles = 0.0;
  float wet = 0.0;
  float4 c = iceCell(p, ${frost.cell.toFixed(2)} * k, needles, wet);
  float ice = c.x;
  float4 outc = float4(0.0);

  if (src.a > 0.003) {
    float3 rgb = src.rgb / src.a;
    float lum = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    float3 cool = lum * COOL_TINT + float3(0.02, 0.05, 0.09);
    float3 base = mix(rgb, cool, 0.86);
    float veil = 0.40 + 0.20 * needles;
    float3 col = mix(base, ICE_HI, veil);
    col = mix(col, ICE_DEEP, 0.10 * c.y);
    col = mix(col, ICE_MID, 0.10 * (1.0 - c.y) * needles);
    col = mix(col, float3(1.0), 0.65 * c.w);
    col += float3(1.0) * c.z * 0.9;
    float a = src.a * sat1(ice + c.z * ice);
    // Wet sheen while a cell melts: the product shows through a film of water.
    float sheen = wet * (0.35 + 0.65 * smoothstep(0.2, 1.0, needles));
    float wa = src.a * wet * 0.45;
    float3 wcol = mix(rgb, ICE_MID, 0.35) + 0.25 * sheen;
    outc = float4(col * a, a);
    outc = outc + float4(wcol * wa, wa) * (1.0 - outc.a);

    // Drops run down the product while it thaws.
    float lift = 0.0;
    float3 dcol = float3(0.0);
    for (int i = 0; i < 6; i++) {
      float fi = float(i);
      float t0 = 0.08 + 0.17 * h1(float2(fi, 9.1));
      float life = melt - t0;
      if (life > 0.0 && life < 0.75) {
        float dx = margin + (0.2 + 0.6 * h1(float2(fi, 5.3))) * box.x;
        float y0 = margin + (0.18 + 0.32 * h1(float2(fi, 2.9))) * box.y;
        float yy = y0 + pow(life / 0.75, 1.7) * box.y * 0.62;
        float rd = (2.2 + 1.2 * h1(float2(fi, 4.4))) * k;
        float vis = smoothstep(0.0, 0.08, life) * (1.0 - smoothstep(0.55, 0.75, life));
        float2 q = (p - float2(dx, yy)) / rd;
        float body = 1.0 - smoothstep(0.85, 1.0, length(q * float2(1.0, 0.88)));
        float trail = (1.0 - smoothstep(0.18, 0.32, abs(p.x - dx) / rd / 2.0)) * step(y0, p.y) * step(p.y, yy) * smoothstep(y0, yy, p.y) * 0.5;
        float rim = body * smoothstep(0.35, 1.0, length(q));
        float spec = exp(-dot(q - float2(-0.32, -0.38), q - float2(-0.32, -0.38)) / 0.05);
        float m = max(body, trail) * vis * step(0.5, src.a);
        lift = max(lift, m * (0.30 + 0.45 * rim));
        dcol += float3(1.0) * spec * vis * body;
      }
    }
    outc = outc + float4(ICE_DEEP * lift, lift) * (1.0 - outc.a);
    outc.rgb += dcol * 0.95;
    outc.a = max(outc.a, sat1(length(dcol)));
  }

  // Rime halo: condensation on the page around a frozen sticker.
  float halo = 0.0;
  float rr = 9.0 * k;
  for (int i = 0; i < 8; i++) {
    float an = float(i) * 0.7854;
    halo += img.eval(p + float2(cos(an), sin(an)) * rr).a;
    halo += img.eval(p + float2(cos(an + 0.39), sin(an + 0.39)) * rr * 2.0).a * 0.6;
  }
  halo = sat1(halo / 9.0) * (1.0 - src.a);
  float coverage = sat1(grow / span) * (1.0 - smoothstep(0.0, 0.7, melt));
  float grain = vnoise(p / (5.0 * k)) * 0.6 + vnoise(p / (2.2 * k)) * 0.4;
  float ha = halo * coverage * smoothstep(0.25, 0.85, grain) * ${frost.halo.toFixed(2)};
  outc = outc + float4(ICE_HI * ha, ha) * (1.0 - outc.a);
  return half4(outc * fade);
}
`;

export const TAG_SKSL =
  COMMON +
  `
uniform float2 size; // the pill, from (0, 0)

half4 main(float2 p) {
  if (fade <= 0.0) return half4(0.0);
  float r = size.y * 0.5;
  float2 q = abs(p - size * 0.5) - (size * 0.5 - r);
  float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
  float inside = 1.0 - smoothstep(-0.6, 0.6, sd);
  if (inside <= 0.0) return half4(0.0);
  float needles = 0.0;
  float wet = 0.0;
  float4 c = iceCell(p, ${frost.tagCell.toFixed(2)} * k, needles, wet);
  float3 col = mix(ICE_HI, ICE_MID, 0.30 + 0.25 * needles);
  col = mix(col, ICE_DEEP, 0.07 * c.y);
  // Thicker ice towards the rim of the pill.
  float rimIce = 1.0 - smoothstep(-2.4 * k, -0.2 * k, sd);
  col = mix(col, ICE_MID, 0.35 * (1.0 - rimIce));
  col = mix(col, float3(1.0), 0.65 * c.w);
  col += float3(1.0) * c.z * 0.9;
  float a = inside * sat1(c.x * ${frost.tagAlpha.toFixed(2)} + c.z * c.x);
  float wa = inside * wet * 0.35;
  float4 outc = float4(col * a, a);
  outc = outc + float4(mix(ICE_HI, float3(1.0), 0.5) * wa, wa) * (1.0 - outc.a);
  return half4(outc * fade);
}
`;

function compile(src: string, name: string) {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`price-tag-welcome: ${name} shader failed to compile`);
  return e;
}

export const stickerEffect = compile(STICKER_SKSL, 'sticker frost');
export const tagEffect = compile(TAG_SKSL, 'tag frost');
