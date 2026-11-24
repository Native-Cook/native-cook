// Louver Theme Switch — the turn, one Skia runtime effect over the two snapshots.
//
// The screen is cut into LOUVER.slats horizontal slats. Slat i turns by θ around its long axis, at
// y0 = (i + ½)·h. A point s along the slat sits at (X, y0 + s·cos θ, s·sin θ), seen by a camera D away
// from the screen's centre (cx, cy). Inverting that projection for a pixel (px, py):
//   s = D (py − y0) / (D cos θ + (py − cy) sin θ)      k = D / (D − s sin θ)      X = cx + (px − cx) / k
// The face towards the camera shows the light page at (X, y0 + s), or the dark page at (X, y0 − s),
// so at θ = π the dark page reads the right way up. A slat can overhang its band in perspective, so
// each pixel asks its own slat and both neighbours and paints them far to near, over a dark cavity.
// At rest the effect returns the page itself, so swapping it for the live view shows no change.
//
// Colour: the pages arrive through image shaders, already in the canvas's colour space; the one colour
// written as numbers (the cavity) is converted from sRGB to Display P3 on iOS in JS (AGENTS, Mistakes).

import { Platform } from 'react-native';

import { LOUVER, REST } from './lts-motion';

const f = (n: number) => n.toFixed(4);

// Identifiers avoid WGSL builtin and reserved names (Skia may compile this to WGSL).
export const LTS_SKSL = `
uniform shader lightPage;
uniform shader darkPage;
uniform float2 res;       // pt
uniform float2 turn;      // amount 0…1, direction ±1
uniform float px;         // device pixels per pt
uniform float3 cavityTone;

const float PI_ = 3.14159265;

float easeBack(float u) {
  float c = ${f(LOUVER.back)};
  float v = 2.0 * u;
  if (u < 0.5) { return 0.5 * v * v * ((c + 1.0) * v - c); }
  float w = v - 2.0;
  return 0.5 * (w * w * ((c + 1.0) * w + c) + 2.0);
}

float angleOf(float k) {
  float p = turn.y > 0.0 ? turn.x : 1.0 - turn.x;
  float e = easeBack(clamp(p * ${f(1 + LOUVER.spread)} - ${f(LOUVER.spread)} * k, 0.0, 1.0));
  return PI_ * (turn.y > 0.0 ? e : 1.0 - e);
}

// slat i at pixel p: colour with coverage in alpha, and how near the camera it is there
float4 slatAt(float i, float2 p, out float depth) {
  depth = -1.0e6;
  float count = ${f(LOUVER.slats)};
  if (i < 0.0 || i > count - 1.0) { return float4(0.0); }
  float h = res.y / count;
  float y0 = (i + 0.5) * h;
  float th = angleOf(i / (count - 1.0));
  float sn = sin(th);
  float cs = cos(th);
  float cx = res.x * 0.5;
  float cy = res.y * 0.5;
  float cam = res.y * ${f(LOUVER.camera)};
  float denom = cam * cs + (p.y - cy) * sn;
  if (abs(denom) < 0.001) { return float4(0.0); }
  float s = cam * (p.y - y0) / denom;
  float reach = h * 0.5 + ${f(LOUVER.overlap)};
  float slope = abs(cam * (cam * cs + (y0 - cy) * sn) / (denom * denom));
  float cover = clamp((reach - abs(s)) / max(slope, 0.001) * px + 0.5, 0.0, 1.0);
  if (cover <= 0.0) { return float4(0.0); }
  float k = cam / (cam - s * sn);
  float wx = cx + (p.x - cx) / k;
  cover *= clamp(min(wx, res.x - wx) * px + 0.5, 0.0, 1.0);
  if (cover <= 0.0) { return float4(0.0); }
  depth = s * sn;

  float3 normal = float3(0.0, -sn, cs);
  float3 toEye = float3(cx - wx, cy - (y0 + s * cs), cam - s * sn);
  bool faceUp = dot(normal, toEye) > 0.0;
  if (!faceUp) { normal = -normal; }
  float2 uv = clamp(float2(wx, faceUp ? y0 + s : y0 - s), float2(0.5), res - float2(0.5));
  float3 col = faceUp ? lightPage.eval(uv).rgb : darkPage.eval(uv).rgb;

  float3 sun = normalize(float3(0.0, ${f(LOUVER.lightY)}, ${f(LOUVER.lightZ)}));
  float lit = max(dot(normal, sun), 0.0) / sun.z;
  float turned = sn * sn;
  col *= min(${f(LOUVER.ambient)} + ${f(1 - LOUVER.ambient)} * lit, 1.0);
  float3 mid = normalize(sun + float3(0.0, 0.0, 1.0));
  col += pow(max(dot(normal, mid), 0.0), ${f(LOUVER.glossPower)}) * ${f(LOUVER.gloss)} * turned;
  float rim = reach - abs(s);
  col += smoothstep(${f(LOUVER.edgeWidth)}, 0.0, rim) * ${f(LOUVER.edge)} * abs(sn);
  col *= 1.0 - ${f(LOUVER.contact)} * exp(-max(rim, 0.0) / ${f(LOUVER.contactFold)}) * clamp(abs(sn) * 1.4, 0.0, 1.0);
  return float4(clamp(col, 0.0, 1.0), cover);
}

half4 main(float2 p) {
  if (turn.x <= ${f(REST)}) { return lightPage.eval(p); }
  if (turn.x >= ${f(1 - REST)}) { return darkPage.eval(p); }
  float band = floor(p.y / (res.y / ${f(LOUVER.slats)}));
  float da; float db; float dc;
  float4 a = slatAt(band - 1.0, p, da);
  float4 b = slatAt(band, p, db);
  float4 c = slatAt(band + 1.0, p, dc);
  // far to near
  if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
  if (db > dc) { float4 q = b; b = c; c = q; float r = db; db = dc; dc = r; }
  if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
  float shade = 0.72 + 0.28 * abs(p.y / res.y - 0.5) * 2.0;
  float3 col = cavityTone * (1.2 - 0.4 * shade);
  col = mix(col, a.rgb, a.a);
  col = mix(col, b.rgb, b.a);
  col = mix(col, c.rgb, c.a);
  return half4(half3(col), 1.0);
}
`;

/** an sRGB colour (0…1 per channel) as the numbers a runtime effect must write for it: Display P3 on iOS, unchanged elsewhere */
export function tone(r: number, g: number, b: number): [number, number, number] {
  if (Platform.OS !== 'ios') return [r, g, b];
  const dec = (v: number) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
  const enc = (v: number) => (v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
  const lr = dec(r);
  const lg = dec(g);
  const lb = dec(b);
  return [enc(0.8225 * lr + 0.1774 * lg), enc(0.0332 * lr + 0.9669 * lg), enc(0.0171 * lr + 0.0724 * lg + 0.9108 * lb)];
}

/** what shows between the slats, a blue-black */
export const CAVITY_TONE = tone(0.055, 0.059, 0.082);
