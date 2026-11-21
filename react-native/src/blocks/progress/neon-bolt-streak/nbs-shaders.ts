// Neon Bolt Streak: SkSL. Light is added in linear sRGB and tone-mapped (x / (1 + 0.6 x)) so overlapping blooms never
// clip to flat white; `outLin` encodes and re-maps to Display P3 on iOS, where a runtime effect's output is read as
// P3. The baked atlases are data, not colour: `data3` undoes the sRGB → P3 conversion Skia applies to a child image
// there, so R (distance), G (arc length) and B (bevel) come back as stored.

import { Skia } from '@shopify/react-native-skia';

import { C } from './nbs-theme';

const linHex = (h: string) => {
  const f = (i: number) => {
    const c = parseInt(h.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return `float3(${f(1).toFixed(5)}, ${f(3).toFixed(5)}, ${f(5).toFixed(5)})`;
};

/**
 * The day tube's centre line as two chains from the electrodes to the top, where the two heads meet (traced from
 * TUBE_D, fillets as two chords; scene points about the bolt's centre). The bloom is lit from the distance to the part
 * of these chains the discharge has reached: exact everywhere, so it has no seams where the atlas ends or where its
 * outside arc-length field jumps across a medial axis.
 */
const CHAIN_R = [[-5.89, 28.0], [-4.5, 27.67], [-3.34, 26.83], [17.14, 4.96], [17.91, 2.01], [15.86, -0.25], [7.36, -3.22], [6.29, -4.11], [6.06, -5.49], [9.61, -23.96], [7.79, -27.64], [7.77, -27.64]];
const CHAIN_L = [[-9.23, 26.18], [-9.7, 24.83], [-9.63, 23.41], [-5.54, 7.02], [-5.75, 5.43], [-6.98, 4.4], [-15.82, 1.43], [-17.9, -0.8], [-17.17, -3.75], [3.75, -26.88], [7.77, -27.64]];
const chainSksl = (pts: number[][]) => {
  const f = (v: number) => v.toFixed(3);
  let total = 0;
  for (let i = 0; i + 1 < pts.length; i++) total += Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]);
  let c = 0;
  let body = `  Lf = f * ${f(total)};\n`;
  for (let i = 0; i + 1 < pts.length; i++) {
    const [ax, ay] = pts[i];
    const [bx, by] = pts[i + 1];
    const l = Math.max(Math.hypot(bx - ax, by - ay), 1e-3);
    body += `  r = min(r, segD(q, float2(${f(ax)}, ${f(ay)}), float2(${f(bx)}, ${f(by)}), (Lf - ${f(c)}) / ${f(l)}));\n`;
    c += l;
  }
  return body;
};
/** The point the discharge has reached on one chain (its head), as unrolled SkSL. */
const headSksl = (pts: number[][], name: string) => {
  const f = (v: number) => v.toFixed(3);
  let total = 0;
  for (let i = 0; i + 1 < pts.length; i++) total += Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]);
  let c = 0;
  let body = `float2 ${name}(float f) {\n  float Lf = clamp(f, 0.0, 1.0) * ${f(total)};\n`;
  for (let i = 0; i + 1 < pts.length; i++) {
    const [ax, ay] = pts[i];
    const [bx, by] = pts[i + 1];
    const l = Math.max(Math.hypot(bx - ax, by - ay), 1e-3);
    body += `  if (Lf <= ${f(c + l)}) return mix(float2(${f(ax)}, ${f(ay)}), float2(${f(bx)}, ${f(by)}), (Lf - ${f(c)}) / ${f(l)});\n`;
    c += l;
  }
  const [lx, ly] = pts[pts.length - 1];
  return `${body}  return float2(${f(lx)}, ${f(ly)});\n}\n`;
};
const LIT_DIST = `
${headSksl(CHAIN_R, 'headR')}${headSksl(CHAIN_L, 'headL')}
float segD(float2 p, float2 a, float2 b, float tMax) {
  if (tMax <= 0.0) return 1e4;
  float2 ab = b - a;
  float h = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, min(tMax, 1.0));
  return length(p - a - ab * h);
}
float litDist(float2 q, float f) {
  float r = 1e4;
  float Lf = 0.0;
${chainSksl(CHAIN_R)}${chainSksl(CHAIN_L)}  return r;
}
`;

const COMMON = `
uniform float uP3;
uniform float uData;
uniform float uAA;
const float3 PAGE = ${linHex(C.page)};
const float3 PANEL = ${linHex(C.panel)};
const float3 BORDER = ${linHex(C.border)};
const float3 C_CORE = ${linHex(C.core)};
const float3 C_TUBE = ${linHex(C.tube)};
const float3 C_BLOOM = ${linHex(C.bloom)};
const float3 C_AMBER = ${linHex(C.amber)};
const float3 ZINC_SHADE = ${linHex(C.zincShade)};
const float3 ZINC_BASE = ${linHex(C.zincBase)};
const float3 ZINC_EDGE = ${linHex(C.zincEdge)};
const float3 HERO_TOP = ${linHex(C.heroTop)};
const float3 HERO_MID = ${linHex(C.heroMid)};
const float3 HOT = float3(1.6, 1.5, 1.2);

float lin1(float c) { c = clamp(c, 0.0, 1.0); return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc1(float c) { c = clamp(c, 0.0, 1.0); return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
float3 lin3(float3 c) { return float3(lin1(c.r), lin1(c.g), lin1(c.b)); }
float3 enc3(float3 c) { return float3(enc1(c.r), enc1(c.g), enc1(c.b)); }
half4 outLin(float3 l) {
  if (uP3 > 0.5) l = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half4(half3(enc3(l)), 1.0);
}
float3 data3(half4 c) {
  float3 v = float3(c.rgb);
  if (uData > 0.5) {
    float3 l = lin3(v);
    l = float3(1.2249 * l.r - 0.2247 * l.g, -0.0420 * l.r + 1.0419 * l.g, -0.0197 * l.r - 0.0786 * l.g + 1.0979 * l.b);
    v = enc3(l);
  }
  return v;
}
// tone map that leaves the page colour where it is
float3 toneMap(float3 x) { return x / (1.0 + 0.6 * x) * (1.0 + 0.6 * PAGE); }
// alpha blend the way a designer's "white 7 %" means it: in encoded sRGB
float3 over(float3 base, float3 top, float a) { return lin3(mix(enc3(base), enc3(top), clamp(a, 0.0, 1.0))); }
float sdRound(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
`;

/**
 * The week panel and its seven tubes, in scene points (the rect is the panel plus 48 pt). Day bolts read the 128 × 192
 * day atlas: R signed distance to the tube's centre line (± 24 pt), G arc length from the electrodes, B the zinc bevel.
 */
const WEEK = `${COMMON}
uniform shader uDay;
uniform shader uZinc;
uniform float uLit[7];
uniform float uFill[7];
uniform float uBurn[7];
uniform float uHead[7];
uniform float uAfter[7];
uniform float uPop[7];
uniform float uDy[7];
uniform float uRimL[7];
uniform float uRimR[7];
uniform float uBlur[7];
uniform float uBorder;
uniform float uBorderLit;
uniform float uBorderAfter;
uniform float uTime;
uniform float uLife;
uniform float uOff;
uniform float3 uTouch;

const float CELL = 52.857143;
const float LOOP = 9.5;
const float CHAIN = 74.3;

// near + far bloom of a tube at distance d (pt): kept local so seven of them never wash the panel flat
float bloomAt(float d) { float ad = abs(d); return 0.45 / (1.0 + (d / 6.0) * (d / 6.0)) * exp(-ad / 40.0) + 0.09 * exp(-ad / 26.0); }

${LIT_DIST}
float3 dayAt(float2 q) { return data3(uDay.eval((q + float2(48.0, 72.0)) * 1.3333333)); }

// arc length along the panel's rounded rect, clockwise from the start of the top edge
float perim(float2 q, float a, float b, float r) {
  float2 c = clamp(q, float2(-a, -b), float2(a, b));
  float2 v = q - c;
  if (v.x == 0.0 && v.y == 0.0) {
    float dt = q.y + b; float db = b - q.y; float dl = q.x + a; float dr = a - q.x;
    float m = min(min(dt, db), min(dl, dr));
    if (m == dt) v = float2(0.0, -1.0); else if (m == dr) v = float2(1.0, 0.0); else if (m == db) v = float2(0.0, 1.0); else v = float2(-1.0, 0.0);
  }
  float Lt = 2.0 * a; float Lr = 2.0 * b; float arc = 1.5707963 * r;
  if (v.x == 0.0 && v.y < 0.0) return c.x + a;
  if (v.x > 0.0 && v.y < 0.0) return Lt + r * atan(v.x, -v.y);
  if (v.x > 0.0 && v.y == 0.0) return Lt + arc + c.y + b;
  if (v.x > 0.0 && v.y > 0.0) return Lt + arc + Lr + r * atan(v.y, v.x);
  if (v.x == 0.0 && v.y > 0.0) return Lt + Lr + 2.0 * arc + a - c.x;
  if (v.x < 0.0 && v.y > 0.0) return 2.0 * Lt + Lr + 2.0 * arc + r * atan(-v.x, v.y);
  if (v.x < 0.0 && v.y == 0.0) return 2.0 * Lt + Lr + 3.0 * arc + b - c.y;
  return 2.0 * Lt + 2.0 * Lr + 3.0 * arc + r * atan(-v.y, -v.x);
}

// the point at arc length s (pt) along that rounded rect, the inverse of perim
float2 perimPt(float s, float a, float b, float r) {
  float Lt = 2.0 * a; float Lr = 2.0 * b; float arc = 1.5707963 * r;
  if (s < Lt) return float2(-a + s, -b - r);
  s -= Lt;
  if (s < arc) { float t = s / r; return float2(a + r * sin(t), -b - r * cos(t)); }
  s -= arc;
  if (s < Lr) return float2(a + r, -b + s);
  s -= Lr;
  if (s < arc) { float t = s / r; return float2(a + r * cos(t), b + r * sin(t)); }
  s -= arc;
  if (s < Lt) return float2(a - s, b + r);
  s -= Lt;
  if (s < arc) { float t = s / r; return float2(-a - r * sin(t), b + r * cos(t)); }
  s -= arc;
  if (s < Lr) return float2(-a - r, b - s);
  s -= Lr;
  float t = min(s / r, 1.5707963);
  return float2(-a - r * cos(t), -b - r * sin(t));
}

half4 main(float2 p) {
  float2 pc = p - float2(201.0, 644.0);
  float rr = sdRound(pc, float2(185.0, 84.0), 20.0);
  float inside = 1.0 - smoothstep(-uAA, uAA, rr);
  float3 col = mix(PAGE, PANEL, inside);
  // glossy backplate: a faint lift toward the top edge, as from the room's light
  col *= 1.0 + inside * 0.16 * (1.0 - smoothstep(-84.0, 30.0, pc.y));

  float3 light = float3(0.0);
  float3 refl = float3(0.0);
  float3 deadCol = float3(0.0);
  float deadA = 0.0;
  float strikeHaze = 0.0;
  float spill = 0.0;
  float shadow = 0.0;
  float breath = 1.0 + uLife * 0.02 * sin(6.2831853 * 2.0 * uTime / LOOP);

  for (int k = 0; k < 7; k++) {
    float cx = 16.0 + CELL * (float(k) + 0.5);
    float pop = 1.0 + uPop[k];
    float2 q = (p - float2(cx, 629.0 + uDy[k])) / pop;
    float fill = uFill[k];
    float burn = uBurn[k];
    float lit = uLit[k] * breath;
    float far = length(max(abs(q) - float2(18.0, 28.0), 0.0)) * pop;
    float lightOn = lit * smoothstep(0.0, 0.3, fill);
    spill += 0.9 * lightOn / (1.0 + (far / 30.0) * (far / 30.0));
    // the plasma-globe finger: the glow gathers under a touch on a lit tube, the rest of it dims
    float tr = length(p - uTouch.xy);
    float near0 = 1.0 - smoothstep(42.0, 72.0, length(uTouch.xy - float2(cx, 629.0)));
    float touchG = 1.0 + uTouch.z * near0 * (-0.2 + 0.6 * exp(-(tr / 30.0) * (tr / 30.0)));
    if (lit > 0.001 && fill > 0.0 && far < 110.0) {
      float dl = litDist(q, min(fill, 1.0)) * pop;
      float falloff = (1.0 - smoothstep(80.0, 110.0, far)) * lit * touchG;
      light += bloomAt(dl) * falloff * mix(C_BLOOM, C_AMBER, uAfter[k]);
      // A broader halo follows the active discharge. Keep the brightest contribution so overlapping strikes
      // don't wash out the backplate, and leave the narrow tube profile in bloomAt().
      if (uHead[k] > 0.001) {
        float haze = 0.065 * smoothstep(4.0, 9.0, dl) * exp(-dl / 42.0);
        strikeHaze = max(strikeHaze, haze * falloff * uHead[k] * (1.0 - uAfter[k]));
      }
    }
    if (abs(q.x) > 52.0 || abs(q.y) > 76.0) continue;

    float3 a = dayAt(q);
    float d = (a.r - 0.5) * 48.0;
    if (a.r > 0.985) d = max(d, far / pop);
    d *= pop;
    float G = a.g;
    float B = a.b;
    // G is stored in 8 bits: where the burn front crosses the slow interior field the levels terrace, so average a
    // small cross of taps there (only near the front of a tube that is burning)
    float front = max(fill, burn);
    if (front > 0.0 && front < 1.0 && abs(G - front) < 0.08 && d < 3.0) {
      G = 0.2 * (G + dayAt(q + float2(0.9, 0.0)).g + dayAt(q - float2(0.9, 0.0)).g + dayAt(q + float2(0.0, 0.9)).g + dayAt(q - float2(0.0, 0.9)).g);
    }
    float ad = abs(d);
    float wid = 1.0 + 0.6 * uBlur[k];
    float after = uAfter[k];

    // ---- light: core, tube, near and far bloom, gated by the discharge front ----
    if (lit > 0.001 && fill > 0.0) {
      float gHard = 1.0 - smoothstep(fill - 0.012, fill, G);
      float sh = 1.0 + uLife * 0.06 * sin(6.2831853 * (G * 1.5 - uTime * 8.0 / LOOP));
      float gain = lit * sh * touchG;
      float3 cTube = mix(C_TUBE, C_AMBER, after);
      float3 cCore = mix(C_CORE, C_AMBER, after);
      float core = (1.0 - smoothstep(0.3 * wid, 1.1 * wid, ad)) * (1.0 - 0.85 * after);
      float dt = d / (2.0 * wid);
      float tube = exp(-dt * dt) / wid;
      float glass = 0.06 * (1.0 - smoothstep(-2.6, -1.6, d));
      float2 sil = q + float2(20.0, 30.0);
      float cut = smoothstep(1.75, 2.25, length(sil - float2(12.19, 57.554)));
      light += (cut * gHard * (core * cCore + (tube + glass) * cTube)) * gain;
      // white-hot comet heads, 6 pt long, on both sides of the outline
      float behind = max(fill - G, 0.0) * CHAIN;
      float dh = d / 2.4;
      // white-hot on the tube right behind each head (the atlas is exact this close), a radial spark glow round the
      // two head points themselves
      float comet = uHead[k] * exp(-behind / 4.0) * step(G, fill) * cut * max(lit, 0.6) * (1.0 - smoothstep(3.0, 5.0, ad));
      float hd2 = min(length(q - headR(fill)), length(q - headL(fill))) * pop;
      light += comet * exp(-dh * dh) * HOT * 2.2 + uHead[k] * max(lit, 0.6) * 0.8 / (1.0 + (hd2 / 4.5) * (hd2 / 4.5)) * (1.0 - smoothstep(24.0, 44.0, hd2)) * float3(1.0, 0.85, 0.45);
      // the glossy panel's reflection: same outline, offset down-right, blurred
      float3 r = dayAt(q - float2(uOff, uOff) / pop);
      float rd = (r.r - 0.5) * 48.0 * pop;
      float rg = 1.0 - smoothstep(fill - 0.04, fill, r.g);
      float rdd = rd / 5.0;
      refl += 0.30 * exp(-rdd * rdd) * rg * gain * cTube;
    }

    // ---- dead metal: bevelled zinc, consumed by the burn, with a 2 pt white edge right behind the front ----
    float silA = 1.0 - smoothstep(2.0 - uAA, 2.0 + uAA, d);
    if (silA > 0.0) {
      float unburnt = burn <= 0.0 ? 1.0 : smoothstep(burn - 0.004, burn + 0.01, G);
      float da = silA * unburnt;
      if (da > 0.0) {
        float3 zc = B < 0.5 ? mix(ZINC_SHADE, ZINC_BASE, B * 2.0) : mix(ZINC_BASE, ZINC_EDGE, (B - 0.5) * 2.0);
        float zg = float(uZinc.eval((q + float2(20.0, 30.0)) * 3.1).r) - 0.5;
        zc *= 1.0 + 0.2 * zg;
        // spill: a yellow rim on the side that faces a lit neighbour
        if (d > 0.5) {
          float dx = (dayAt(q + float2(0.75, 0.0)).r - a.r);
          float dyv = (dayAt(q + float2(0.0, 0.75)).r - a.r);
          float2 n = normalize(float2(dx, dyv) + float2(1e-6, 0.0));
          float band = smoothstep(0.5, 1.1, d) * (1.0 - smoothstep(1.7, 2.2, d));
          float rim = band * (uRimL[k] * max(-n.x, 0.0) + uRimR[k] * max(n.x, 0.0));
          zc += C_TUBE * rim * 0.35 * 1.6;
        }
        // the gas glowing on the metal near the electrodes during failed strikes
        deadCol += zc * da;
        deadA += da;
      }
      if (uHead[k] > 0.0 && burn > 0.0) {
        float behindB = (burn - G) * CHAIN;
        float edge = (1.0 - smoothstep(0.0, 2.0, behindB)) * step(0.0, behindB) * silA * uHead[k];
        light += edge * HOT * 0.75;
      }
    }
    // contact shadow of the metal, 1 pt down-right
    if (burn < 1.0) {
      float3 s = dayAt(q - float2(1.0, 1.0) / pop);
      float sd = (s.r - 0.5) * 48.0 * pop;
      float sUn = burn <= 0.0 ? 1.0 : smoothstep(burn - 0.01, burn + 0.01, s.g);
      shadow = max(shadow, 0.3 * (1.0 - smoothstep(1.2, 4.2, sd)) * sUn);
    }
  }

  // ---- the border: a 3 pt tube whose current runs clockwise from the top-left, 40 pt white head ----
  float db = abs(rr + 0.5);
  float tint = 0.25 * min(spill, 1.0);
  float bline = 1.0 - smoothstep(0.5 - uAA, 0.5 + uAA, db);
  float topLight = 1.0 + 0.35 * (1.0 - smoothstep(-84.0, 0.0, pc.y));
  col = mix(col, mix(BORDER * topLight, C_BLOOM * 0.35, tint), bline);
  if (uBorderLit > 0.0 || uBorder > 0.0) {
    float L = 4.0 * 165.0 + 4.0 * 64.0 + 6.2831853 * 20.0;
    float s = perim(pc, 165.0, 64.0, 20.0) / L;
    float on = 1.0 - smoothstep(uBorder - 0.002, uBorder, s);
    float3 bTube = mix(C_TUBE, C_AMBER, uBorderAfter);
    float bcore = (1.0 - smoothstep(0.25, 0.9, db)) * (1.0 - 0.85 * uBorderAfter);
    float bt = db / 1.5;
    float btube = exp(-bt * bt);
    // the bloom comes from the lit part of the tube only: its distance, not the nearest point's state, so no seams
    float dl = db;
    if (uBorder < 1.0 && s > uBorder) dl = min(length(pc - perimPt(0.0, 165.0, 64.0, 20.0)), length(pc - perimPt(uBorder * L, 165.0, 64.0, 20.0)));
    float bglow = bloomAt(dl);
    float bsh = 1.0 + uLife * 0.05 * sin(6.2831853 * (s * 3.0 - uTime * 8.0 / LOOP));
    light += uBorderLit * bsh * (on * (bcore * C_CORE + btube * bTube) + bglow * mix(C_BLOOM, C_AMBER, uBorderAfter));
    if (uBorder > 0.0 && uBorder < 1.0) {
      float behind = (uBorder - s) * L;
      float hd = exp(-max(behind, 0.0) / 13.0) * step(0.0, behind);
      float hh = db / 1.8;
      float dh = length(pc - perimPt(uBorder * L, 165.0, 64.0, 20.0));
      light += hd * exp(-hh * hh) * HOT * 1.4 + 1.3 * bloomAt(dh) * C_BLOOM;
    }
  }

  // fade the light out before the rect's edge, so the page beyond matches
  float edgeD = min(min(p.x + 32.0, 434.0 - p.x), min(p.y - 512.0, 776.0 - p.y));
  light = (light + strikeHaze * C_BLOOM) * smoothstep(0.0, 30.0, edgeD);

  col = col * (1.0 + inside * spill * float3(1.0, 0.96, 0.78)) + refl * inside;
  col *= 1.0 - shadow * inside;
  col = col * (1.0 - min(deadA, 1.0)) + deadCol;
  return outLin(toneMap(col + light));
}
`;

/**
 * The hero bolt (256 × 384 atlas: R distance to its edge, B a soft 11 pt pillow bevel) and the four ghost bolts, which
 * reuse it at 0.62 ×. The hero's gradient level is the streak: its transparent end rises or falls with the count.
 */
const HERO = `${COMMON}
uniform shader uHero;
uniform float uLevel;
uniform float uBloom;
uniform float uFlash;
uniform float uGhost[4];
uniform float uGhostTint;

float3 heroAt(float2 q) { return data3(uHero.eval((q + float2(45.0, 64.0)) * 0.9142857)); }

half4 main(float2 p) {
  float3 col = PAGE;
  for (int g = 0; g < 4; g++) {
    float cx = g < 2 ? 24.0 + 68.0 * float(g) : 310.0 + 68.0 * float(g - 2);
    float2 q = (p - float2(cx, 296.0)) / 0.62 + float2(95.0, 146.0);
    if (q.x < -40.0 || q.x > 230.0 || q.y < -60.0 || q.y > 352.0) continue;
    float3 h = heroAt(q);
    float d = (h.r - 0.5) * 48.0 * 0.62;
    float a = (1.0 - smoothstep(-uAA, uAA, d)) * uGhost[g];
    float bev = h.b - 0.5;
    float3 gc = mix(float3(1.0), C_TUBE, uGhostTint);
    col = over(col, gc, a * (1.0 + 1.6 * bev));
  }

  float2 q = p - float2(106.0, 150.0);
  float3 h = heroAt(q);
  float d = (h.r - 0.5) * 48.0;
  if (h.r > 0.985) d = max(d, length(max(abs(q - float2(95.0, 146.0)) - float2(95.0, 146.0), 0.0)));
  float inA = 1.0 - smoothstep(-uAA, uAA, d);
  float v = q.y / 292.0;
  float e = mix(0.42, 1.85, uLevel);
  float u = v / e;
  float3 fillC = mix(HERO_TOP, HERO_MID, smoothstep(0.0, 0.55, u));
  float fa = 1.0 - smoothstep(0.55, 1.0, u);
  float bv = h.b - 0.5;
  fillC *= (1.0 + 1.1 * bv) * (1.0 + 0.7 * uFlash);
  fillC += float3(1.0, 0.95, 0.7) * max(bv, 0.0) * 0.35;
  col = over(col, fillC, inA * fa);
  // the drained part is still glass: a fine rim lit from the top-left
  float rd = (d + 0.7) / 0.7;
  float rim = exp(-rd * rd) * (0.06 + 0.5 * max(bv, 0.0)) * (1.0 - 0.8 * fa);
  col = over(col, float3(1.0), rim);
  // its light on the page
  // near glow from the edge (ends before the atlas saturates) and a soft halo round the bolt, both seamless
  float he = length((q - float2(95.0, 150.0)) / float2(125.0, 175.0));
  float halo = exp(-max(he - 0.5, 0.0) * 2.5) * (1.0 - smoothstep(0.85, 1.2, he));
  float nearG = exp(-max(d, 0.0) / 9.0) * (1.0 - smoothstep(12.0, 22.0, d));
  float glow = (uBloom + 0.04 * uLevel + 0.25 * uFlash) * (0.75 * nearG + 0.4 * halo) * (1.0 - inA) * (1.0 - smoothstep(0.5, 1.1, u));
  col += glow * C_BLOOM;
  return outLin(col);
}
`;

/** Screen-space finish: vignette to #1F2023 at the corners and a 3 % grain. */
const FINISH = `
uniform shader uGrain;
uniform float2 uSize;
half4 main(float2 p) {
  float2 n = (p - uSize * 0.5) / (uSize * 0.5);
  float v = 0.19 * smoothstep(0.6, 1.5, length(n * float2(1.0, 0.8)));
  half4 col = half4(0.0, 0.0, 0.0, v);
  float g = (float(uGrain.eval(p).r) - 0.5) * 0.06;
  float ga = abs(g);
  half3 gc = g > 0.0 ? half3(1.0) : half3(0.0);
  return half4(gc * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
}
`;

const make = (src: string, name: string) => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`Neon Bolt Streak: the ${name} shader failed to compile`);
  return e;
};

export const WEEK_FX = make(WEEK, 'week');
export const HERO_FX = make(HERO, 'hero');
export const FINISH_FX = make(FINISH, 'finish');
