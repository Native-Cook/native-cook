/**
 * The machine's materials, written as SkSL runtime effects because gradients cannot do them: polished chrome and
 * brushed steel that reflect one studio (a softbox above and to the left, an overhead scrim, a dim card on the
 * right, a dark floor), black powder coat, lacquered walnut with its grain, glazed porcelain, the shot and its
 * crema, the streams, steam and the gauge's glass. Every part is shaded from the same light and the same studio;
 * the lever's shaders receive them turned into the lever's own frame (`uRot`), so its highlights move with the angle.
 *
 * One camera for everything: a front elevation with the eye a little above the cup's rim (`GEO.tilt`). Normals are
 * turned into its frame with `view()`, so tops catch the scrim and circles show as thin ellipses.
 *
 * Geometry and colours come from theme.ts and are baked into the source once; only the angle, the pressure, the
 * coffee and the light are uniforms, so a frame costs a few uniform writes.
 */
import { Skia, type SkRuntimeEffect } from '@shopify/react-native-skia';

import { LAMP_AT } from './lever-look';
import { GEO, LIGHT, MATERIAL } from './theme';

/** A float literal for SkSL. */
const f = (n: number) => (Number.isInteger(n) ? `${n}.0` : `${n}`);
const v3 = (c: readonly number[]) => `float3(${f(c[0])}, ${f(c[1])}, ${f(c[2])})`;

const SE = GEO.tilt;
const CE = Math.sqrt(1 - SE * SE);
const KEY_LEN = Math.hypot(LIGHT.key[0], LIGHT.key[1] - 140, LIGHT.key[2]); // to the middle of the machine
const LV = GEO.lever;
const GR = GEO.group;
const PF = GEO.pf;
const CUP = GEO.cup;
const B = GEO.body;
const TR = GEO.tray;
const GA = GEO.gauge;
const TI = GEO.timer;
const LAMP = LAMP_AT;
const NK = GEO.neck;
const PF_TILT = (PF.tilt * Math.PI) / 180;
// The stop pin, 6 units inside the ticks, where the needle at rest touches it (its half width there plus the pin's radius).
const PIN_A = ((90 + (360 - GA.sweep) / 2 + (GA.pin / GA.max) * GA.sweep) * Math.PI) / 180 - (0.37 + 0.85) / (GA.ticks - 6);

const COMMON = `
const float3 V = float3(0.0, 0.0, 1.0);
const float SE = ${f(SE)};
const float CE = ${f(CE)};
float hash12(float2 p) {
  float3 p3 = fract(float3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(hash12(i), hash12(i + float2(1.0, 0.0)), u.x), mix(hash12(i + float2(0.0, 1.0)), hash12(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + float2(17.1, 9.2); a *= 0.5; }
  return s;
}
float2 rot(float2 v, float2 cs) { return float2(v.x * cs.x - v.y * cs.y, v.x * cs.y + v.y * cs.x); }
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float sqr(float x) { return x * x; }
float3 srgb(float3 c) { return pow(max(c, 0.0), float3(0.4545)); }
float cover(float d, float aa) { return clamp(0.5 - d / aa, 0.0, 1.0); }
half4 over(half4 acc, float3 c, float a) { return half4(half3(c * a), half(a)) + acc * half(1.0 - a); }
float segDist(float2 p, float2 a, float2 b) {
  float2 ab = b - a;
  float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
  return length(p - a - ab * t);
}
// A normal in the world (y down, z toward the viewer, level) seen by the camera, which looks a little down.
float3 view(float3 n) { return float3(n.x, n.y * CE - n.z * SE, n.z * CE - n.y * SE); }
`;

/** The key light and the studio every material reflects. */
const LIGHTING = `
const float3 KEY = ${v3(LIGHT.key)};
// Inverse square from the softbox, 1 at the middle of the machine: the side of the machine nearer the light is a little brighter.
float falloff(float3 P) { float d = length(KEY - P) / ${f(KEY_LEN)}; return 1.0 / (d * d); }
// The studio: a dark room, an overhead scrim, a warm dark floor, a large softbox above left in front, a dim card
// on the right. \`rough\` widens and dims what a surface reflects of it.
float3 env(float3 R, float rough) {
  float soft = 0.05 + rough * 0.6;
  float gain = 1.0 / (1.0 + rough * 2.2);
  float3 c = float3(0.014, 0.013, 0.012);
  c += float3(0.3, 0.29, 0.28) * smoothstep(0.3 - soft, 0.9, -R.y) * gain;
  c = mix(c, ${v3(MATERIAL.floor)} * 1.6, smoothstep(-0.05, 0.35, R.y));
  float z = max(R.z, 0.002);
  float ax = atan(R.x, z);
  float ay = atan(R.y, z);
  float front = smoothstep(-0.1, 0.15, R.z);
  float box = smoothstep(0.34 + soft, 0.34 - soft * 0.6, abs(ax + 0.6)) * smoothstep(0.5 + soft, 0.5 - soft * 0.6, abs(ay + 0.62));
  c += float3(1.0, 0.97, 0.93) * 2.7 * box * gain * front;
  float card = smoothstep(0.1 + soft, 0.04, abs(ax - 1.0)) * smoothstep(0.85 + soft, 0.6, abs(ay + 0.1));
  c += float3(0.85, 0.88, 0.92) * 0.3 * card * gain * front;
  return c;
}
// Metal is only what it reflects, tinted.
float3 metal(float3 P, float3 N, float3 tint, float rough) {
  return tint * env(reflect(-V, N), rough) * mix(1.0, falloff(P), 0.5);
}
// Anything else: a diffuse body under the key light and the room, and a clear coat that reflects the studio.
float3 dielectric(float3 P, float3 N, float3 alb, float rough, float coat) {
  float3 L = normalize(KEY - P);
  float nl = dot(N, L);
  float lit = mix(max(nl, 0.0), max((nl + 0.3) / 1.3, 0.0), 0.4);
  float sky = clamp(0.5 - 0.5 * N.y, 0.0, 1.0);
  float F = 0.04 + 0.96 * pow(1.0 - clamp(N.z, 0.0, 1.0), 5.0);
  return alb * (${f(LIGHT.diffuse)} * lit * falloff(P) + ${f(LIGHT.ambient)} + 0.1 * sky) + env(reflect(-V, N), rough) * F * coat;
}
// Lacquered walnut: grain along the axis (\`s\` along it, \`arc\` round it), waved, with open pores.
float3 walnut(float3 P, float3 N, float s, float arc) {
  float w = fbm(float2(s * 0.035, arc * 0.18));
  float lines = 0.5 + 0.5 * sin(arc * 1.9 + w * 9.0 + s * 0.02);
  float late = smoothstep(0.55, 0.95, lines) * 0.75 + 0.25 * smoothstep(0.62, 0.8, vnoise(float2(s * 0.6, arc * 2.2)));
  float3 alb = mix(${v3(MATERIAL.walnut)}, ${v3(MATERIAL.walnutDark)}, late) * (0.9 + 0.2 * fbm(float2(s * 0.12, arc * 0.5)));
  return dielectric(P, N, alb, 0.14, 0.9);
}
`;

/** A vertical turned part seen from above: its outline, and whether a point is on its top face. */
const TURNED = `
float sdTurned(float2 p, float r, float yt, float yb) {
  float q = clamp(p.x / r, -1.0, 1.0);
  float e = r * SE * sqrt(1.0 - q * q);
  return max(abs(p.x) - r, max((yt - e) - p.y, p.y - (yb + e)));
}
// Chrome or steel on a vertical cylinder: the top face, a rounded edge, the side.
float3 turned(float2 p, float r, float yt, float rough, float3 tint, float dome) {
  float q = clamp(p.x / r, -0.999, 0.999);
  float s = sqrt(1.0 - q * q);
  float edge = yt + r * SE * s;
  float k = smoothstep(-1.6, 1.4, p.y - edge);
  float zt = clamp((p.y - yt) / (r * SE), -1.0, 1.0);
  float3 Ntop = normalize(float3(q * dome, -1.0, zt * dome));
  float3 N = normalize(mix(Ntop, float3(q, 0.0, s), k));
  return metal(float3(p, s * r), view(N), tint, rough);
}
`;

const CHROME = v3(MATERIAL.chrome);
const STEEL = v3(MATERIAL.steel);

/** The group head on its neck, the portafilter and its handle. Static; drawn bottom part first. */
const GROUP = `${COMMON}${LIGHTING}${TURNED}
uniform float uAA;
const float2 PFD = float2(${f(Math.cos(PF_TILT))}, ${f(Math.sin(PF_TILT))});
float pfRadius(float s) {
  float u = clamp((s - ${f(PF.wood.from)}) / ${f(PF.wood.to - PF.wood.from)}, 0.0, 1.0);
  float r = mix(${f(PF.wood.r0)}, ${f(PF.wood.r1)}, smoothstep(0.0, 0.7, u));
  return r * sqrt(max(1.0 - sqr(max(u - 0.86, 0.0) / 0.14), 0.0));
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  // The boiler's feed: a chrome pipe out of the body's side, in its shadow where it leaves it, with a hex union nut.
  // A horizontal chrome part only sees the overhead scrim and the floor: a crisp highlight high up, a dark core,
  // a faint warm bounce along its underside.
  float dn = max(max(p.x - ${f(NK.to)}, ${f(NK.from)} - p.x), abs(p.y - ${f(NK.y)}) - ${f(NK.r)});
  if (dn < uAA) {
    float t = clamp((p.y - ${f(NK.y)}) / ${f(NK.r)}, -1.0, 1.0);
    float3 c = float3(0.035) + float3(0.95, 0.94, 0.92) * exp(-sqr((t + 0.52) / 0.16)) + float3(0.16) * smoothstep(0.3, -0.9, t)
      + float3(0.11, 0.1, 0.09) * exp(-sqr((t - 0.78) / 0.14));
    c *= 1.0 - 0.6 * exp(-max(p.x - ${f(NK.from)}, 0.0) / 1.6);
    acc = over(acc, srgb(c * ${CHROME}), cover(dn, uAA));
  }
  float2 nq = p - float2(${f((NK.nut.from + NK.nut.to) / 2)}, ${f(NK.y)});
  float dNut = sdBox(nq, float2(${f((NK.nut.to - NK.nut.from) / 2)}, ${f(NK.nut.r)}), 0.6);
  if (dNut < uAA) {
    // Seen across its flats: a top face toward the scrim, a front face, a bottom face; bright and dark ridges
    // between them; chamfered ends.
    float t = nq.y / ${f(NK.nut.r)};
    float3 c = t < -0.5 ? mix(float3(0.5), float3(0.3), (t + 1.0) / 0.5) : (t > 0.5 ? float3(0.075, 0.07, 0.065) : mix(float3(0.14), float3(0.07), (t + 0.5)));
    c += float3(0.7) * exp(-abs(t + 0.5) * ${f(NK.nut.r)} / 0.22);
    c *= 1.0 - 0.6 * exp(-abs(t - 0.5) * ${f(NK.nut.r)} / 0.22);
    c += float3(0.12, 0.11, 0.1) * exp(-(1.0 - t) * ${f(NK.nut.r)} / 0.4); // the floor's bounce on its lower edge
    float end = smoothstep(${f((NK.nut.to - NK.nut.from) / 2 - 1.2)}, ${f((NK.nut.to - NK.nut.from) / 2)}, abs(nq.x));
    c = mix(c, c * (nq.x < 0.0 ? 1.6 : 0.45), end);
    acc = over(acc, srgb(c * ${CHROME}), cover(dNut, uAA));
  }
  // The portafilter's handle: a chrome arm into a turned walnut grip, dipping toward its end.
  float2 h = p - float2(${f(PF.arm.from)}, ${f(PF.arm.y)});
  float hs = dot(h, PFD);
  float ht = dot(h, float2(-PFD.y, PFD.x));
  float sAbs = hs + ${f(PF.arm.from)};
  float dArm = max(abs(ht) - ${f(PF.arm.half)}, max(-hs, sAbs - ${f(PF.arm.to)}));
  if (dArm < uAA) {
    float t = clamp(ht / ${f(PF.arm.half)}, -0.99, 0.99);
    float3 n = float3(-PFD.y * t, PFD.x * t, sqrt(1.0 - t * t));
    float3 c = metal(float3(p, 6.0), view(n), ${CHROME}, 0.1);
    acc = over(acc, srgb(c), cover(dArm, uAA));
  }
  float rw = pfRadius(sAbs);
  float dWood = max(abs(ht) - rw, max(${f(PF.wood.from)} - sAbs, sAbs - ${f(PF.wood.to)}));
  if (dWood < uAA && sAbs > ${f(PF.wood.from - 1)}) {
    float t = clamp(ht / max(rw, 0.5), -0.99, 0.99);
    float slope = (pfRadius(sAbs + 0.5) - pfRadius(sAbs - 0.5));
    float3 nl = normalize(float3(-slope, t, sqrt(1.0 - t * t)));
    float3 n = float3(nl.x * PFD.x - nl.y * PFD.y, nl.x * PFD.y + nl.y * PFD.x, nl.z);
    float3 c = walnut(float3(p, 8.0), view(n), sAbs, asin(t) * rw);
    c *= 1.0 - 0.45 * exp(-max(sAbs - ${f(PF.wood.from)}, 0.0) / 1.6); // tucked into the ferrule
    acc = over(acc, srgb(c), cover(dWood, uAA));
  }
  // Two spouts, a little apart, under the basket, flaring a little to their open ends.
  for (int i = 0; i < 2; i++) {
    float side = float(i) * 2.0 - 1.0;
    float2 a = float2(side * 2.5, ${f(PF.stem.bottom - 3)});
    float2 b = float2(side * ${f(PF.spout.x)}, ${f(PF.spout.tip)});
    float2 ax = normalize(b - a);
    float along = clamp(dot(p - a, ax) / length(b - a), 0.0, 1.0);
    float sr = mix(${f(PF.spout.r * 0.8)}, ${f(PF.spout.r)}, along * along);
    float ds = segDist(p, a, b) - sr;
    if (ds < uAA) {
      float t = clamp(dot(p - a, float2(-ax.y, ax.x)) / sr, -0.99, 0.99);
      float3 n = float3(-ax.y * t, ax.x * t, sqrt(1.0 - t * t));
      float3 c = metal(float3(p, 4.0), view(n), ${CHROME}, 0.08);
      // The open end: a lit lip round a dark bore, seen almost edge on.
      float2 e = p - b;
      float2 eq = float2(dot(e, float2(-ax.y, ax.x)) / ${f(PF.spout.bore)}, dot(e, ax) / 0.9);
      float bore = 1.0 - smoothstep(0.8, 1.05, length(eq));
      c = mix(c, float3(0.03, 0.022, 0.018), bore);
      c += float3(0.5) * exp(-abs(length(eq) - 1.25) / 0.18) * smoothstep(-2.0, 0.0, dot(e, ax)) * 0.6;
      acc = over(acc, srgb(c), cover(ds, uAA));
    }
  }
  float dSt = sdTurned(p, ${f(PF.stem.r)}, ${f(PF.stem.top)}, ${f(PF.stem.bottom)});
  if (dSt < uAA) acc = over(acc, srgb(turned(p, ${f(PF.stem.r)}, ${f(PF.stem.top)}, 0.1, ${CHROME}, 0.0)), cover(dSt, uAA));
  // The basket: a short cylinder closing in a shallow dome.
  const float BR = ${f(PF.bowl.r)};
  const float DOME = ${f(PF.bowl.bottom)} - 9.0;
  float dDome = (length(float2(p.x / BR, (p.y - DOME) / 9.0)) - 1.0) * 9.0;
  dDome = max(dDome, DOME - p.y);
  if (dDome < uAA) {
    float2 q = float2(p.x / BR, (p.y - DOME) / 9.0);
    float z = sqrt(max(1.0 - dot(q, q), 0.0));
    float3 n = normalize(float3(q.x / BR, q.y / 9.0, z / BR));
    acc = over(acc, srgb(metal(float3(p, z * BR), view(n), ${CHROME}, 0.1)), cover(dDome, uAA));
  }
  float dB = sdTurned(p, BR, ${f(PF.bowl.top)}, DOME);
  if (dB < uAA) acc = over(acc, srgb(turned(p, BR, ${f(PF.bowl.top)}, 0.1, ${CHROME}, 0.0)), cover(dB, uAA));
  // The bayonet ring, the cylinder with its turned groove, the cap.
  float dF = sdTurned(p, ${f(GR.flange.r)}, ${f(GR.flange.top)}, ${f(GR.flange.bottom)});
  if (dF < uAA) {
    float3 c = turned(p, ${f(GR.flange.r)}, ${f(GR.flange.top)}, 0.16, ${CHROME}, 0.0);
    // The basket's shadow line under it.
    float q = clamp(p.x / ${f(GR.flange.r)}, -1.0, 1.0);
    c *= 1.0 - 0.5 * smoothstep(-2.5, 0.0, p.y - (${f(GR.flange.bottom)} + ${f(GR.flange.r * SE)} * sqrt(1.0 - q * q)));
    acc = over(acc, srgb(c), cover(dF, uAA));
  }
  float dG = sdTurned(p, ${f(GR.body.r)}, ${f(GR.body.top)}, ${f(GR.body.bottom)});
  if (dG < uAA) {
    float3 c = turned(p, ${f(GR.body.r)}, ${f(GR.body.top)}, 0.06, ${CHROME}, 0.0);
    float q = clamp(p.x / ${f(GR.body.r)}, -1.0, 1.0);
    float gy = ${f(GR.groove)} + ${f(GR.body.r * SE)} * sqrt(1.0 - q * q);
    c *= 1.0 - 0.75 * exp(-abs(p.y - gy) / 0.45);
    c += float3(0.35) * exp(-abs(p.y - gy - 1.3) / 0.4) * smoothstep(0.2, -0.6, q) * 0.6;
    // Occlusion where the cylinder stands in the bayonet ring.
    float fy = ${f(GR.flange.top)} + ${f(GR.flange.r * SE)} * sqrt(max(1.0 - sqr(p.x / ${f(GR.flange.r)}), 0.0));
    c *= 1.0 - 0.45 * smoothstep(-5.0, 0.0, p.y - fy);
    acc = over(acc, srgb(c), cover(dG, uAA));
  }
  float dC = sdTurned(p, ${f(GR.cap.r)}, ${f(GR.cap.top)}, ${f(GR.cap.bottom)});
  if (dC < uAA) acc = over(acc, srgb(turned(p, ${f(GR.cap.r)}, ${f(GR.cap.top)}, 0.08, ${CHROME}, 0.55)), cover(dC, uAA));
  // The bracket the lever turns on: a brushed plate standing on the cap.
  float dY = sdBox(p - float2(0.0, ${f((GEO.yoke.top + GEO.yoke.bottom) / 2)}), float2(${f(GEO.yoke.half)}, ${f((GEO.yoke.bottom - GEO.yoke.top) / 2)}), ${f(GEO.yoke.half - 0.5)});
  if (dY < uAA) {
    float u = clamp((dY + 1.6) / 1.6, 0.0, 1.0);
    float e = 0.35;
    float2 g = normalize(float2(
      sdBox(p + float2(e, 0.0) - float2(0.0, ${f((GEO.yoke.top + GEO.yoke.bottom) / 2)}), float2(${f(GEO.yoke.half)}, ${f((GEO.yoke.bottom - GEO.yoke.top) / 2)}), ${f(GEO.yoke.half - 0.5)}) - dY,
      sdBox(p + float2(0.0, e) - float2(0.0, ${f((GEO.yoke.top + GEO.yoke.bottom) / 2)}), float2(${f(GEO.yoke.half)}, ${f((GEO.yoke.bottom - GEO.yoke.top) / 2)}), ${f(GEO.yoke.half - 0.5)}) - dY) + 0.0001);
    float3 n = normalize(float3(g * u * 0.9, 1.0 - u * 0.6));
    float3 c = metal(float3(p, 4.0), view(n), ${STEEL}, 0.32);
    c *= 1.0 - 0.5 * smoothstep(-3.0, 0.0, p.y - ${f(GEO.yoke.bottom - 3)});
    acc = over(acc, srgb(c), cover(dY, uAA));
  }
  return acc;
}
`;

/** The body: powder coat, its top seen from above, the gauge's bezel and face, the timer's window, the lamp. */
const BODY = `${COMMON}${LIGHTING}
uniform float uAA;
uniform float uZone;
uniform float uReady;
const float2 BC = float2(${f((B.left + B.right) / 2)}, ${f((B.top + B.bottom) / 2)});
const float2 BH = float2(${f((B.right - B.left) / 2)}, ${f((B.bottom - B.top) / 2)});
const float2 GC = float2(${f(GA.x)}, ${f(GA.y)});
const float2 TC = float2(${f(TI.x)}, ${f(TI.y)});
const float2 TH = float2(${f(TI.width / 2)}, ${f(TI.height / 2)});
const float2 LAMP = float2(${f(LAMP.x)}, ${f(LAMP.y)});
const float PI = 3.14159265;
const float START = ${f(((90 + (360 - GA.sweep) / 2) * Math.PI) / 180)};
const float SWEEP = ${f((GA.sweep * Math.PI) / 180)};
const float2 PIN = GC + float2(${f(Math.cos(PIN_A))}, ${f(Math.sin(PIN_A))}) * ${f(GA.ticks - 6)};
float sdSide(float2 p) { return sdBox(p - BC, BH, ${f(B.radius)}); }
float sdWin(float2 p) { return sdBox(p - TC, TH, ${f(TI.radius)}); }
float2 grad(float2 p) {
  float e = 0.35;
  return normalize(float2(sdSide(p + float2(e, 0.0)) - sdSide(p - float2(e, 0.0)), sdSide(p + float2(0.0, e)) - sdSide(p - float2(0.0, e))) + 0.00001);
}
float2 gradWin(float2 p) {
  float e = 0.35;
  return normalize(float2(sdWin(p + float2(e, 0.0)) - sdWin(p - float2(e, 0.0)), sdWin(p + float2(0.0, e)) - sdWin(p - float2(0.0, e))) + 0.00001);
}
float3 coat(float3 P, float3 n, float grain) {
  return dielectric(P, view(n), ${v3(MATERIAL.enamel)} * (0.92 + 0.16 * grain), 0.55, 0.55);
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  // Feet on the floor.
  for (int i = 0; i < 2; i++) {
    float fx = i == 0 ? ${f(GEO.feet.at[0])} : ${f(GEO.feet.at[1])};
    float d = sdBox(p - float2(fx, ${f(GEO.feet.y + GEO.feet.height / 2)}), float2(${f(GEO.feet.half)}, ${f(GEO.feet.height / 2)}), 1.5);
    if (d < uAA) acc = over(acc, srgb(${v3(MATERIAL.rubber)} * (1.0 + 2.0 * smoothstep(${f(GEO.feet.y + 3)}, ${f(GEO.feet.y)}, p.y))), cover(d, uAA));
  }
  float grain = vnoise(p * 1.9) * 0.6 + vnoise(p * 0.45) * 0.4;
  float d = sdSide(p);
  if (d >= uAA) return acc;
  float3 P = float3(p, 40.0);
  float2 g = grad(p);
  float u = clamp((d + ${f(B.chamfer)}) / ${f(B.chamfer)}, 0.0, 1.0);
  // Its rounded edges turn toward their side; along the top edge, toward the top.
  float3 n = normalize(float3(g * u * 0.95, sqrt(max(1.0 - u * u * 0.9, 0.05))));
  float3 c = coat(P, n, grain);
  // A brushed steel top, the cup warmer, with a dark seam where it meets the powder coat.
  float lid = ${f(B.top + 10)};
  if (p.y < lid + 0.6) {
    float3 nl = normalize(float3(g * u * 0.95, sqrt(max(1.0 - u * u * 0.9, 0.05))) + float3(0.0, -0.35 * smoothstep(lid - 3.0, lid, p.y), 0.0));
    float3 steel = metal(P, view(nl), ${STEEL}, 0.36) * (0.86 + 0.28 * vnoise(float2(p.x * 0.05, p.y * 3.0)));
    c = mix(c, steel, smoothstep(lid + 0.5, lid - 0.5, p.y));
  }
  c *= 1.0 - 0.7 * exp(-abs(p.y - lid - 0.8) / 0.5);
  // The gauge: a thin polished bezel, its shadow on the face, the face with its scale and the brew zone.
  float2 gq = p - GC;
  float gr = length(gq);
  float2 radial = gq / max(gr, 0.001);
  // A tight seat where the bezel is set into the panel.
  c *= 1.0 - 0.6 * exp(-max(gr - ${f(GA.bezel)}, 0.0) / 0.9) * step(${f(GA.bezel - 0.5)}, gr);
  if (gr < ${f(GA.bezel)} + uAA) {
    float3 cg;
    if (gr > ${f(GA.dial)}) {
      // A half-round ring, polished but not mirror: a narrow highlight where it faces the softbox.
      float t = clamp((gr - ${f((GA.bezel + GA.dial) / 2)}) / ${f((GA.bezel - GA.dial) / 2)}, -1.0, 1.0);
      float3 nb = normalize(float3(radial * t * 0.9, sqrt(max(1.0 - 0.81 * t * t, 0.0))));
      cg = metal(float3(p, 42.0), view(nb), ${CHROME} * 0.82, 0.14);
      cg *= 1.0 - 0.6 * exp(-(gr - ${f(GA.dial)}) / 0.35); // the step down to the face
    } else {
      float a = mod(atan(gq.y, gq.x) - START + 4.0 * PI, 2.0 * PI);
      float bar = a / SWEEP * ${f(GA.max)};
      float onScale = step(bar, ${f(GA.max + 0.02)});
      float arc = SWEEP / ${f(GA.max)} * gr; // pt per bar at this radius
      float hb = abs(fract(bar * 2.0 + 0.5) - 0.5) / 2.0 * arc;
      float whole = abs(fract(bar + 0.5) - 0.5) * arc;
      float third = abs(fract(bar / 3.0 + 0.5) - 0.5) * 3.0 * arc;
      float rim = 1.0 - smoothstep(${f(GA.ticks)} - uAA, ${f(GA.ticks)} + uAA, gr);
      float minor = (1.0 - smoothstep(0.2, 0.2 + uAA, hb)) * smoothstep(${f(GA.ticks - 1.8)} - uAA, ${f(GA.ticks - 1.8)} + uAA, gr);
      float major = (1.0 - smoothstep(0.3, 0.3 + uAA, whole)) * smoothstep(${f(GA.ticks - 2.6)} - uAA, ${f(GA.ticks - 2.6)} + uAA, gr);
      float big = (1.0 - smoothstep(0.45, 0.45 + uAA, third)) * smoothstep(${f(GA.ticks - 3.6)} - uAA, ${f(GA.ticks - 3.6)} + uAA, gr);
      float ink = max(max(minor, major), big) * onScale * rim;
      float3 face = ${v3(MATERIAL.dial)} * (0.9 + 0.25 * smoothstep(${f(GA.dial)}, 0.0, length(gq + float2(10.0, 12.0))));
      face = dielectric(float3(p, 41.0), view(float3(0.0, 0.0, 1.0)), face, 0.6, 0.15);
      cg = mix(face, float3(0.66, 0.64, 0.6), ink);
      // The brew zone, 8 to 10 bar, on its own track outside the ticks: dim, and lit at 9 bar.
      float za = clamp(bar, 8.0, 10.0);
      float2 zp = GC + float2(cos(START + za / ${f(GA.max)} * SWEEP), sin(START + za / ${f(GA.max)} * SWEEP)) * ${f(GA.zone)};
      float dz = length(p - zp) - 0.75;
      float zone = 1.0 - smoothstep(-uAA, uAA, dz);
      cg = mix(cg, ${v3(MATERIAL.zone)} * (0.22 + 1.1 * uZone), zone);
      cg += ${v3(MATERIAL.zone)} * uZone * exp(-max(dz, 0.0) / 0.9) * 0.18 * (1.0 - zone);
      // The needle's stop pin, just below 0.
      float dPin = length(p - PIN) - 0.85;
      float3 pin = metal(float3(p, 41.5), view(normalize(float3((p - PIN) / 0.85 * 0.8, 1.0))), ${CHROME}, 0.1);
      cg *= 1.0 - 0.55 * exp(-max(length(p - PIN - float2(0.45, 0.65)) - 0.85, 0.0) / 0.5);
      cg = mix(cg, pin, cover(dPin, uAA));
      // The bezel's shadow: the face is lit where the light clears its rim.
      float sh = smoothstep(-1.0, 1.8, length(gq - float2(1.2, 1.8)) - ${f(GA.dial)});
      cg *= 1.0 - 0.7 * sh;
    }
    c = mix(c, cg, cover(gr - ${f(GA.bezel)}, uAA));
  }
  // The shot timer's window: a chamfer into the panel, two drums behind it.
  float dw = sdWin(p);
  if (dw < ${f(B.chamfer)} + 0.5) {
    float2 gw = gradWin(p);
    if (dw > 0.0) {
      float k = 1.0 - smoothstep(${f(B.chamfer - 0.5)}, ${f(B.chamfer + 0.5)}, dw);
      float3 nw = normalize(float3(-gw * 0.5, 0.86));
      c = mix(c, coat(P, nw, grain) + float3(0.04) * smoothstep(0.0, -0.8, gw.y), k);
    } else {
      float2 w = p - TC;
      float t = clamp(w.y / (TH.y * 1.3), -0.99, 0.99);
      float3 nd = float3(0.0, t, sqrt(1.0 - t * t));
      float3 cd = dielectric(float3(p, 30.0), view(nd), float3(0.03, 0.029, 0.028), 0.4, 0.3);
      cd *= 1.0 - 0.85 * (1.0 - smoothstep(${f(TI.gap / 2)}, ${f(TI.gap / 2 + 0.8)}, abs(w.x)));
      cd *= smoothstep(0.0, 5.0, -dw) * 0.55 + 0.45;
      c = mix(c, cd, cover(dw, uAA));
    }
  }
  // The pilot lamp: a small amber glass in a dark seat, dark until the shot is ready; its light falls on the panel.
  float dl = length(p - LAMP);
  float lampGlass = cover(dl - ${f(LAMP.r)}, uAA);
  c *= 1.0 - 0.6 * exp(-max(dl - ${f(LAMP.r)}, 0.0) / 0.5) * (1.0 - lampGlass);
  float3 lamp = float3(0.03, 0.02, 0.015) + ${v3(MATERIAL.warm)} * (0.06 + 1.6 * uReady) * exp(-dl * dl / ${f(LAMP.r * LAMP.r * 0.4)});
  lamp += float3(0.6) * exp(-length(p - LAMP + float2(0.9, 1.0)) / 0.4) * 0.45;
  c = mix(c, lamp, lampGlass);
  c += ${v3(MATERIAL.warm)} * uReady * (exp(-max(dl - ${f(LAMP.r)}, 0.0) / 3.0) * 0.22 + exp(-max(dl - ${f(LAMP.r)}, 0.0) / 20.0) * 0.06) * (1.0 - lampGlass);
  acc = over(acc, srgb(c), cover(d, uAA));
  return acc;
}
`;

/** The drip tray: a brushed steel box; its grate's top, thin bars over dark slots, seen a little from above. */
const TRAY = `${COMMON}${LIGHTING}
uniform float uAA;
uniform float uReady;
const float L = ${f(TR.left)};
const float R = ${f(TR.right)};
const float TOP = ${f(TR.top)};
const float FACE = ${f(TR.face)};
const float BOT = ${f(TR.bottom)};
const float BR = ${f(B.right)}; // the body's side: the tray runs in behind it
half4 main(float2 p) {
  half4 acc = half4(0.0);
  float dfoot = sdBox(p - float2(${f(GEO.feet.at[2])}, ${f(GEO.feet.y + GEO.feet.height / 2)}), float2(${f(GEO.feet.half)}, ${f(GEO.feet.height / 2)}), 1.5);
  if (dfoot < uAA) acc = over(acc, srgb(${v3(MATERIAL.rubber)} * (1.0 + 2.0 * smoothstep(${f(GEO.feet.y + 3)}, ${f(GEO.feet.y)}, p.y))), cover(dfoot, uAA));
  float cx = (L + R) * 0.5;
  float hx = (R - L) * 0.5;
  float brush = 0.9 + 0.2 * (vnoise(float2(p.x * 0.03, p.y * 2.6)) * 0.7 + vnoise(float2(p.x * 0.11, p.y * 7.0)) * 0.3);
  // The grate: a frame round thin bars running front to back, the slots between them dark.
  float dTop = sdBox(p - float2(cx, (TOP + FACE) * 0.5), float2(hx, (FACE - TOP) * 0.5), 1.0);
  if (dTop < uAA) {
    float v = (p.y - TOP) / (FACE - TOP); // 0 at the back, 1 at the front
    float w = ${f(TR.bar / 2)};
    float u = abs(fract((p.x - L - ${f(TR.pitch / 2)}) / ${f(TR.pitch)}) - 0.5) * ${f(TR.pitch)};
    float isBar = 1.0 - smoothstep(w - uAA * 0.5, w + uAA * 0.5, u);
    float inX = smoothstep(BR + 4.0 - uAA, BR + 4.0 + uAA, p.x) * (1.0 - smoothstep(R - 4.0 - uAA, R - 4.0 + uAA, p.x));
    float inY = smoothstep(1.4 - uAA, 1.4 + uAA, p.y - TOP) * (1.0 - smoothstep(FACE - 2.2 - uAA, FACE - 2.2 + uAA, p.y));
    float slot = (1.0 - isBar) * inX * inY;
    // Tops face up; each bar's rounded edges turn away from it, so its middle is lit and its sides fall off.
    float e = clamp(u / w, 0.0, 1.0);
    float3 nb = normalize(float3(sign(fract((p.x - L - ${f(TR.pitch / 2)}) / ${f(TR.pitch)}) - 0.5) * e * e * 0.9, -1.0, 0.25));
    float3 top = metal(float3(p, 0.0), view(normalize(mix(float3(0.0, -1.0, 0.25), nb, inX * inY))), ${STEEL}, 0.22) * brush;
    top *= mix(0.62, 1.0, v); // the back of the grate is further from the light
    // The frame's front edge rolls over toward the viewer: a lit line.
    float lip = smoothstep(FACE - 2.2, FACE - 0.4, p.y);
    float3 nlip = normalize(float3(0.0, -1.0 + lip * 0.9, 0.3 + lip));
    top = mix(top, metal(float3(p, 20.0), view(nlip), ${STEEL}, 0.16), lip);
    // Slots: dark, a little lighter low down where the tray's pan shows.
    float3 gap = float3(0.009, 0.008, 0.007) * (0.6 + 1.6 * v);
    float3 c = mix(top, gap, slot);
    // A finished shot's lamp warms the grate a little, nearest the body.
    c += ${v3(MATERIAL.warm)} * uReady * 0.02 * exp(-(p.x - BR) / 40.0) * (1.0 - slot);
    c *= 1.0 - 0.6 * exp(-max(p.x - BR, 0.0) / 2.5); // running into the body
    acc = over(acc, srgb(c), cover(dTop, uAA));
  }
  // The front: brushed along its length, a chamfer lit along the top, a darker one along the foot, rounded ends.
  float dF = sdBox(p - float2(cx, (FACE + BOT) * 0.5), float2(hx, (BOT - FACE) * 0.5), ${f(TR.radius)});
  if (dF < uAA) {
    float ct = 1.0 - smoothstep(0.4, 2.0, p.y - FACE);
    float cb = smoothstep(BOT - 1.6, BOT, p.y);
    float ce = smoothstep(${f(TR.radius)}, 0.0, R - p.x); // the right end rounds away; the left is against the body
    float3 n = normalize(float3(ce * 0.8, -1.4 * ct + 0.8 * cb - 0.04, 1.0));
    // Brushing smears the studio a little across the grain: average over normals tipped along it.
    float3 c = float3(0.0);
    for (int k = 0; k < 3; k++) c += metal(float3(p, 30.0), view(normalize(n + float3((float(k) - 1.0) * 0.06, 0.0, 0.0))), ${STEEL} * 0.55, 0.2);
    c *= brush / 3.0;
    // The softbox's sheen falls off along the face, away from the light (the camera is not at infinity).
    c *= mix(1.0, 0.42, smoothstep(BR, R + 20.0, p.x) * (1.0 - ct));
    c *= 0.78 + 0.22 * (1.0 - smoothstep(FACE, BOT, p.y));
    c *= 1.0 - 0.6 * exp(-max(p.x - BR, 0.0) / 2.5);
    acc = over(acc, srgb(c), cover(dF, uAA));
  }
  // A hairline seam between the grate and the front.
  float seam = exp(-abs(p.y - FACE) / 0.25) * step(L, p.x) * step(p.x, R);
  acc = half4(acc.rgb * half(1.0 - 0.6 * seam), acc.a);
  return acc;
}
`;

/** The porcelain cup, its shadow on the grate, and the shot in it. Drawn in the cup's own place (it slides). */
const CUP_SRC = `${COMMON}${LIGHTING}
uniform float uAA;
uniform float uFill;
uniform float uSurface;
uniform float uCrema;
uniform float uFlow;
uniform float uT;
uniform float uReady;
uniform float uMotion;
const float RIM = ${f(CUP.rim)};
const float BASE = ${f(CUP.base)};
const float BOWL = ${f(CUP.base - 4)};
const float WALL = ${f(CUP.wall)};
const float3 GLAZE = ${v3(MATERIAL.porcelain)};
const float2 LAMP = float2(${f(LAMP.x)}, ${f(LAMP.y)});
float cupR(float y) {
  // One smooth profile: upright under the rim, rounding in to the foot.
  float h = clamp((y - RIM) / (BOWL - RIM), 0.0, 1.0);
  return ${f(CUP.bottom)} + ${f(CUP.r - CUP.bottom)} * sqrt(max(1.0 - pow(h, 3.2), 0.0));
}
float3 glaze(float3 P, float3 n) {
  float3 c = dielectric(P, view(n), GLAZE, 0.09, 1.0);
  // The pilot lamp's warm light on the side facing it.
  c += ${v3(MATERIAL.warm)} * uReady * 0.05 * max(-n.x, 0.0) * exp(-length(P.xy - LAMP) / 120.0);
  return c;
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  // Its shadow on the grate: a dark contact ring under the foot and a soft fall to the right, away from the light;
  // only on the grate's top (the tray's front is in front of it).
  float2 sq = (p - float2(1.5, BASE + 0.4)) / float2(${f(CUP.foot + 2.5)}, ${f((CUP.foot + 2.5) * SE + 0.8)});
  float contact = 1.0 - smoothstep(0.55, 1.15, length(sq));
  float2 cq = (p - float2(16.0, BASE + 0.6)) / float2(36.0, ${f(Math.max((TR.face - TR.top) / 2 - 0.5, 2))});
  float fall = 1.0 - smoothstep(0.15, 1.0, length(cq));
  float onGrate = smoothstep(${f(TR.top)}, ${f(TR.top + 1)}, p.y) * (1.0 - smoothstep(${f(TR.face - 1)}, ${f(TR.face)}, p.y));
  acc = half4(0.0, 0.0, 0.0, half((1.0 - (1.0 - contact * 0.92) * (1.0 - fall * 0.5)) * onGrate));
  // The ear, a loop of porcelain on the right, behind the bowl where they join.
  float2 eq = (p - float2(${f(CUP.ear.x)}, ${f(CUP.ear.y)})) / float2(${f(CUP.ear.rx)}, ${f(CUP.ear.ry)});
  float er = length(eq);
  float ring = abs(er - ${f((1 + CUP.ear.hole) / 2)}) - ${f((1 - CUP.ear.hole) / 2)};
  if (ring * ${f(CUP.ear.ry)} < uAA && p.x > 30.0) {
    float t = clamp((er - ${f((1 + CUP.ear.hole) / 2)}) / ${f((1 - CUP.ear.hole) / 2)}, -0.99, 0.99);
    float2 rd = eq / max(er, 0.001);
    float3 n = normalize(float3(rd * t, sqrt(1.0 - t * t)));
    float3 c = glaze(float3(p, 4.0), n);
    acc = over(acc, srgb(c), cover(ring * ${f(CUP.ear.ry)}, uAA));
  }
  // The foot ring.
  float qf = clamp(p.x / ${f(CUP.foot)}, -1.0, 1.0);
  float ef = ${f(CUP.foot * SE)} * sqrt(1.0 - qf * qf);
  float dFoot = max(abs(p.x) - ${f(CUP.foot)}, max((BOWL - ef) - p.y, p.y - (BASE + ef)));
  if (dFoot < uAA) {
    // In the bowl's shadow, lighter toward its lower edge where it catches the grate's light.
    float3 c = glaze(float3(p, 10.0), float3(qf, 0.0, sqrt(1.0 - qf * qf))) * mix(0.14, 0.24, smoothstep(BOWL, BASE + ef, p.y));
    c *= 1.0 - 0.6 * exp(-max(p.y - (BOWL + ${f(CUP.bottom * SE)} * sqrt(max(1.0 - sqr(p.x / ${f(CUP.bottom)}), 0.0))), 0.0) / 0.5); // the crease under the bowl
    acc = over(acc, srgb(c), cover(dFoot, uAA));
  }
  // The mouth: the rim's ellipse, the lip, and inside it the back wall and the shot.
  float2 mq = float2(p.x / ${f(CUP.r)}, (p.y - RIM) / ${f(CUP.r * SE)});
  float dMouth = (length(mq) - 1.0) * ${f(CUP.r * SE)};
  if (dMouth < uAA) {
    float3 c;
    float ri = ${f(CUP.r - CUP.wall)};
    float2 iq = float2(p.x / ri, (p.y - RIM) / (ri * SE));
    float din = (length(iq) - 1.0) * ri * SE;
    // Inside: the back wall above the shot, in the rim's shadow on the left.
    float y1 = p.y + SE * sqrt(max(ri * ri - p.x * p.x, 0.0));
    float lit = smoothstep(-0.55, 0.35, p.x / ri - 0.03 * (y1 - RIM));
    float3 nb = normalize(float3(-p.x / ri, 0.0, sqrt(max(1.0 - sqr(p.x / ri), 0.0))));
    c = dielectric(float3(p, -10.0), view(nb), GLAZE, 0.3, 0.3) * mix(0.16, 0.72, lit) * (1.0 - 0.012 * (y1 - RIM));
    // The shot: its surface is a disc at uSurface, seen through the mouth wherever the disc is.
    float rs = cupR(uSurface) - WALL;
    float2 sq2 = float2(p.x / rs, (p.y - uSurface) / (rs * SE));
    float sr = length(sq2);
    float ds = (sr - 1.0) * rs * SE;
    if (ds < uAA && uFill > 0.001) {
      // Crema: hazelnut, lighter where it is freshest, with the darker flecks of a good shot and a dark ring
      // where it meets the porcelain. Ripples run out from where the streams fall in.
      float2 w = sq2 * 2.6;
      float warp = fbm(w * 1.3 + float2(uT * 0.04, 0.0) * uMotion);
      float tig = fbm(w * float2(2.6, 1.6) + warp * 1.8 + float2(0.0, uT * 0.12) * uMotion);
      float tiger = smoothstep(0.44, 0.66, tig);
      float3 crema = mix(${v3(MATERIAL.cremaLight)}, ${v3(MATERIAL.crema)}, smoothstep(0.1, 0.95, sr));
      crema = mix(crema, ${v3(MATERIAL.tiger)}, tiger * 0.45);
          crema = mix(crema, ${v3(MATERIAL.cremaLight)} * 1.15, smoothstep(0.6, 0.85, fbm(w * 4.0 + 3.0)) * 0.35);
      crema = mix(crema, ${v3(MATERIAL.tiger)} * 0.6, smoothstep(0.8, 1.0, sr));
      float3 col = mix(${v3(MATERIAL.coffee)}, crema, uCrema);
      float ripple = 0.0;
      for (int k = 0; k < 2; k++) {
        float side = float(k) * 2.0 - 1.0;
        float dd = length(sq2 - float2(side * ${f(PF.spout.x)} / rs, 0.0));
        ripple += sin(dd * 34.0 - uT * 16.0) * exp(-dd * 5.0) * uFlow * uMotion;
        col = mix(col, ${v3(MATERIAL.cremaLight)} * 1.1, exp(-dd * dd / 0.012) * uFlow * 0.7);
      }
      float3 ns = normalize(float3(ripple * 0.12, -1.0, ripple * 0.12));
      float lit2 = smoothstep(-0.9, -0.1, sq2.x + 0.35 * sq2.y);
      float3 cs = dielectric(float3(p, 0.0), view(ns), col, 0.18, 0.7) * mix(0.4, 1.05, lit2);
          // The softbox, a soft window in the wet surface, back and to the left.
          cs += float3(1.0, 0.92, 0.82) * exp(-dot((sq2 - float2(-0.38, -0.5)) * float2(1.6, 3.2), (sq2 - float2(-0.38, -0.5)) * float2(1.6, 3.2))) * 0.12 * uCrema;
      c = mix(c, cs, cover(ds, uAA));
    }
    // The lip: a rounded ring of glaze, brightest where it faces the scrim.
    float t = clamp((length(mq) - ${f((CUP.r - CUP.wall / 2) / CUP.r)}) / ${f(CUP.wall / 2 / CUP.r)}, -1.0, 1.0);
    float2 rd = normalize(mq + 0.0001);
    float3 nl = normalize(float3(rd.x * t, -sqrt(1.0 - t * t) * 0.9, rd.y * t));
    c = mix(c, glaze(float3(p, 30.0), nl), 1.0 - cover(din, uAA));
    acc = over(acc, srgb(c), cover(dMouth, uAA));
  }
  // The bowl's front, over the mouth: find the level whose front edge passes through this point.
  float y0 = p.y - SE * ${f(CUP.r)};
  for (int i = 0; i < 6; i++) {
    float r = cupR(y0);
    y0 = p.y - SE * sqrt(max(r * r - p.x * p.x, 0.0));
  }
  float r0 = cupR(y0);
  float lipY = RIM + ${f(CUP.r * SE)} * sqrt(max(1.0 - sqr(p.x / ${f(CUP.r)}), 0.0));
  float dWall = max(max(abs(p.x) - r0, lipY - p.y), y0 - BOWL);
  if (dWall < uAA) {
    float z = sqrt(max(r0 * r0 - p.x * p.x, 0.0));
    float dr = cupR(y0 + 0.5) - cupR(y0 - 0.5);
    float3 n = normalize(float3(p.x / r0, -dr, z / r0));
    float3 c = glaze(float3(p.x, y0, z), n);
    // Occlusion where the bowl turns under toward the foot.
    c *= 1.0 - 0.45 * smoothstep(BOWL - 12.0, BOWL, y0);
    acc = over(acc, srgb(c), cover(dWall, uAA));
  }
  return acc;
}
`;

/**
 * Two streams of coffee from the spouts into the cup. First a bead swells at each spout and lets go as a round drop
 * that stretches as it falls; then a column runs down from the spout and joins the drops into a stream, with a
 * meniscus where it leaves the spout and thinning as it falls; tiger-striped, then blond, then thin, then dry.
 */
const STREAMS = `${COMMON}
uniform float uAA;
uniform float uFlow;
uniform float uJoin;
uniform float uT;
uniform float uSurface;
uniform float uBlond;
uniform float uMotion;
const float TIP = ${f(PF.spout.tip)};
const float RIM = ${f(CUP.rim)};
const float DRIP = 0.13; // s between drops
const float G = 1700.0; // units/s², a time-lapse's gravity
float3 coffee(float2 p, float xc, float w, float side) {
  // Tiger tail: darker and golden bands running down the stream; lighter as the shot blonds.
  float band = vnoise(float2(side * 7.0 + (p.x - xc) * 0.4, (p.y - uT * 90.0 * uMotion) * 0.07));
  float3 col = mix(float3(0.035, 0.011, 0.004), float3(0.3, 0.11, 0.03), smoothstep(0.35, 0.85, band));
  col = mix(col, float3(0.52, 0.27, 0.1), uBlond * 0.7);
  float t = clamp((p.x - xc) / max(w, 0.3), -1.0, 1.0);
  col *= 0.55 + 0.45 * (1.0 - t * t);
  return col + float3(0.95, 0.86, 0.74) * exp(-sqr((t + 0.4) / 0.24)) * 0.5;
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  if (uFlow <= 0.001) return acc;
  float join = mix(1.0, uJoin, uMotion);
  float steady = smoothstep(0.5, 0.9, uFlow) * join;
  for (int i = 0; i < 2; i++) {
    float side = float(i) * 2.0 - 1.0;
    float x0 = side * ${f(PF.spout.x)};
    float fall = max(p.y - TIP, 0.0);
    float xc = x0 + side * 0.7 * (1.0 - exp(-fall / 5.0)) + sin(p.y * 0.21 - uT * 19.0 + side * 1.7) * 0.05 * uMotion * steady;
    // The cup's front lip hides the rest; once the shot is high enough the stream meets its surface.
    float end = min(RIM + ${f(CUP.r * SE)} * sqrt(max(1.0 - sqr(xc / ${f(CUP.r)}), 0.0)) - 1.0, uSurface);
    float hide = 1.0 - smoothstep(end - 0.5, end + 0.5, p.y);
    // The column: from the spout down to its front, which falls like the drops do; round at the front.
    float w = mix(0.8, 1.45, uFlow) / sqrt(1.0 + fall / 22.0) + 0.75 * exp(-fall / 1.8);
    float front = TIP + join * (end - TIP + 6.0);
    float dCol = abs(p.x - xc) - w;
    if (p.y > front - w) dCol = length(float2(p.x - xc, p.y - (front - w))) - w;
    dCol = join > 0.001 ? max(dCol, TIP - 0.5 - p.y) : 1e4;
    // The drops, only while the column has not reached them: a bead at the spout, then up to three in the air.
    float dDrop = 1e4;
    if (join < 1.0) {
      float ph = fract(uT / DRIP + side * 0.37);
      float rb = mix(0.6, 1.6, ph) * (1.0 - join);
      dDrop = length((p - float2(x0, TIP + rb * 0.75)) / float2(1.0, 1.15)) - rb;
      for (int k = 0; k < 3; k++) {
        float age = (ph + float(k)) * DRIP;
        float y = TIP + 1.2 + 0.5 * G * age * age;
        float stretch = min(G * age * 0.004, 4.5);
        float r = 1.5 / sqrt(1.0 + stretch * 0.2);
        float xd = x0 + side * 0.7 * (1.0 - exp(-(y - TIP) / 5.0));
        // A teardrop: full at its leading end, thinning into the tail it stretches behind it.
        float dd = segDist(p, float2(xd, y - stretch), float2(xd, y)) - r * (0.55 + 0.45 * smoothstep(y - stretch, y, p.y));
        if (y - r > end) dd = 1e4;
        dDrop = min(dDrop, dd);
      }
    }
    float d = min(dCol, dDrop);
    float a = cover(d, uAA) * hide * step(TIP - 1.0, p.y);
    if (a > 0.0) acc = over(acc, srgb(coffee(p, xc, max(w, 1.0), side)), a);
  }
  return acc;
}
`;

/** Steam over the cup once the shot is in it, and the vent of steam each stroke lets out of the group. */
const STEAM = `${COMMON}
uniform float uT;
uniform float uAmt;
uniform float uPuff;
uniform float uPuffAge;
uniform float uWarm;
const float RIM = ${f(CUP.rim)};
half4 main(float2 p) {
  float a = 0.0;
  if (uAmt > 0.001) {
    float h = RIM - 2.0 - p.y;
    if (h > 0.0) {
      float xc = sin(h * 0.045 - uT * 0.05) * h * 0.12 + (fbm(float2(h * 0.02 - uT * 0.02, 3.0)) - 0.5) * 18.0;
      float w = 9.0 + h * 0.28;
      float col = exp(-sqr((p.x - xc) / w));
      float n = fbm(float2(p.x * 0.05, (p.y + uT) * 0.035)) * fbm(float2(p.x * 0.11 + 5.0, (p.y + uT * 1.3) * 0.07));
      a = col * smoothstep(0.08, 0.32, n) * smoothstep(0.0, 14.0, h) * exp(-h / 55.0) * 0.32 * uAmt;
    }
  }
  if (uPuff > 0.001) {
    float2 c = float2(${f(GR.body.r + 1)} + uPuffAge * 10.0, ${f(GR.body.top + 6)} - uPuffAge * 30.0);
    float r = 3.0 + uPuffAge * 16.0;
    float n = fbm((p - c) * 0.18 + float2(uPuffAge * 2.0, 0.0));
    a += exp(-dot(p - c, p - c) / (r * r)) * smoothstep(0.2, 0.6, n) * uPuff * uPuff * 0.3;
  }
  float3 col = mix(float3(0.92, 0.9, 0.87), float3(1.0, 0.86, 0.72), uWarm * 0.6) * min(a, 1.0);
  return half4(half3(col), half(min(a, 1.0)));
}
`;

/** The lever: its brushed arm round the pivot, a polished ferrule, the walnut handle, the hub. Lever frame. */
const LEVER = `${COMMON}${LIGHTING}
uniform float2 uRot;
uniform float uAA;
const float3 ALU = ${STEEL};
// A vector in the lever's frame, in the world's.
float3 world(float3 n) { return float3(rot(n.xy, float2(uRot.x, -uRot.y)), n.z); }
float sdArm(float2 p) {
  float boss = length(p) - ${f(LV.boss)};
  float x = clamp(p.x, 0.0, ${f(LV.ferrule.from + 2)});
  float hw = mix(${f(LV.half0)}, ${f(LV.half1)}, x / ${f(LV.ferrule.from)});
  float arm = max(abs(p.y) - hw, max(-p.x, p.x - ${f(LV.ferrule.from + 2)}));
  return smin(boss, arm, ${f(LV.fillet)});
}
float handleR(float s) {
  float u = clamp((s - ${f(LV.handle.from)}) / ${f(LV.handle.to - LV.handle.from)}, 0.0, 1.0);
  if (u <= ${f(LV.handle.swell)}) return ${f(LV.handle.r0)} + ${f(LV.handle.r1 - LV.handle.r0)} * sin(1.5707963 * u / ${f(LV.handle.swell)});
  float v = (u - ${f(LV.handle.swell)}) / ${f(1 - LV.handle.swell)};
  return ${f(LV.handle.r1)} * sqrt(max(1.0 - pow(v, 2.4), 0.0));
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  // The arm: brushed along its length, its edges rounded over.
  float dA = sdArm(p);
  if (dA < uAA) {
    float e = 0.35;
    float2 g = normalize(float2(sdArm(p + float2(e, 0.0)) - sdArm(p - float2(e, 0.0)), sdArm(p + float2(0.0, e)) - sdArm(p - float2(0.0, e))) + 0.00001);
    float u = clamp((dA + ${f(LV.bevel)}) / ${f(LV.bevel)}, 0.0, 1.0);
    float tilt = min(u * u * 0.95 + 0.1 * smoothstep(-7.0, 0.0, dA), 0.97);
    float3 n = float3(g * tilt, sqrt(1.0 - tilt * tilt));
    float streak = vnoise(float2(p.x * 0.05, p.y * 3.3)) * 0.6 + vnoise(float2(p.x * 0.012, p.y * 12.0)) * 0.4;
    // Brushing smears the reflection across the grooves: average the studio over normals tipped across them.
    float3 c = float3(0.0);
    for (int k = 0; k < 3; k++) {
      float o = (float(k) - 1.0) * 0.22 * (1.0 - u);
      c += metal(float3(p, 8.0), view(world(normalize(n + float3(0.0, o, 0.0)))), ALU, mix(0.32, 0.06, u));
    }
    c *= (0.88 + 0.24 * streak) / 3.0;
    // The hub's shadow on the boss, and occlusion where the arm meets the ferrule.
    float2 ho = rot(float2(3.0, 4.2), uRot);
    c *= 1.0 - 0.5 * (1.0 - smoothstep(-1.0, 2.5, length(p - ho) - ${f(LV.hub)}));
    c *= 1.0 - 0.45 * exp(-max(${f(LV.ferrule.from)} - p.x, 0.0) / 1.8) * step(p.x, ${f(LV.ferrule.from + 1)});
    acc = over(acc, srgb(c), cover(dA, uAA));
  }
  // The ferrule: a polished collar.
  float dF = sdBox(p - float2(${f((LV.ferrule.from + LV.ferrule.to) / 2)}, 0.0), float2(${f((LV.ferrule.to - LV.ferrule.from) / 2)}, ${f(LV.ferrule.half)}), 1.6);
  if (dF < uAA) {
    float t = clamp(p.y / ${f(LV.ferrule.half)}, -0.99, 0.99);
    float3 c = metal(float3(p, 10.0), view(world(float3(0.0, t, sqrt(1.0 - t * t)))), ${CHROME}, 0.07);
    float ex = abs(p.x - ${f((LV.ferrule.from + LV.ferrule.to) / 2)}) - ${f((LV.ferrule.to - LV.ferrule.from) / 2 - 1.2)};
    c *= 1.0 - 0.35 * smoothstep(0.0, 1.2, ex);
    acc = over(acc, srgb(c), cover(dF, uAA));
  }
  // The handle: turned walnut, lacquered.
  float rh = handleR(p.x);
  float dH = max(abs(p.y) - rh, max(${f(LV.handle.from)} - p.x, p.x - ${f(LV.handle.to)}));
  if (dH < uAA) {
    float t = clamp(p.y / max(rh, 0.4), -0.99, 0.99);
    float slope = handleR(p.x + 0.5) - handleR(p.x - 0.5);
    float3 n = normalize(float3(-slope * sqrt(1.0 - t * t), t, sqrt(1.0 - t * t)));
    float3 c = walnut(float3(p, 12.0), view(world(n)), p.x, asin(t) * rh);
    c *= 1.0 - 0.5 * exp(-max(p.x - ${f(LV.handle.from)}, 0.0) / 1.4);
    acc = over(acc, srgb(c), cover(dH, uAA));
  }
  // The hub: a polished dome on the pivot pin.
  float r = length(p);
  float dHub = r - ${f(LV.hub)};
  if (dHub < uAA) {
    float2 q = p / ${f(LV.hub)};
    float rr = min(dot(q, q), 1.0);
    float edge = smoothstep(0.7, 0.98, sqrt(rr));
    float3 n = normalize(float3(q * (0.55 + edge * 1.2), 1.0));
    float3 c = metal(float3(p, 14.0), view(world(n)), ${CHROME}, 0.05);
    c *= 1.0 - 0.6 * (1.0 - smoothstep(1.0, 1.8, r));
    acc = over(acc, srgb(c), cover(dHub, uAA));
  }
  return acc;
}
`;

/** The lever's soft shadow on the sweep behind it, offset along the light. Lever frame. */
const LEVER_SHADOW = `${COMMON}
uniform float2 uRot;
float sdLever(float2 p) {
  float arm = segDist(p, float2(0.0, 0.0), float2(${f(LV.ferrule.to)}, 0.0)) - 5.5;
  float handle = segDist(p, float2(${f(LV.handle.from + 6)}, 0.0), float2(${f(LV.handle.to - 6)}, 0.0)) - 8.5;
  return min(min(arm, handle), length(p) - ${f(LV.boss)});
}
half4 main(float2 p) {
  float2 off = rot(float2(${f(LIGHT.shadow[0])}, ${f(LIGHT.shadow[1])}), uRot);
  float d = sdLever(p - off);
  float a = (1.0 - smoothstep(-6.0, 14.0, d)) * 0.3;
  return half4(0.0, 0.0, 0.0, half(a));
}
`;

/** The machine's shadows: on the sweep behind it, offset along the light, and on the floor under it. */
const CAST = `${COMMON}
half4 main(float2 p) {
  float2 o = float2(${f(LIGHT.shadow[0])}, ${f(LIGHT.shadow[1])});
  float2 q = p - o;
  float body = sdBox(q - float2(${f((B.left + B.right) / 2)}, ${f((B.top + B.bottom) / 2)}), float2(${f((B.right - B.left) / 2)}, ${f((B.bottom - B.top) / 2)}), ${f(B.radius)});
  float group = sdBox(q - float2(0.0, 82.0), float2(34.0, 70.0), 14.0);
  float tray = sdBox(q - float2(${f((TR.left + TR.right) / 2)}, ${f((TR.top + TR.bottom) / 2)}), float2(${f((TR.right - TR.left) / 2)}, ${f((TR.bottom - TR.top) / 2)}), 6.0);
  float cup = sdBox(q - float2(0.0, ${f((CUP.rim + CUP.base) / 2)}), float2(36.0, ${f((CUP.base - CUP.rim) / 2)}), 18.0);
  float d = min(min(body, group), min(tray, cup));
  float wall = (1.0 - smoothstep(-8.0, 26.0, d)) * 0.42;
  // On the floor: a tight contact line under the machine and a soft pool reaching to the right.
  float fy = p.y - ${f(GEO.floor)};
  float contact = exp(-max(fy, 0.0) / 2.5) * smoothstep(-3.0, 0.0, fy) * smoothstep(${f(B.left - 6)}, ${f(B.left + 8)}, p.x) * (1.0 - smoothstep(${f(TR.right - 4)}, ${f(TR.right + 10)}, p.x));
  float2 pq = (p - float2(${f(TR.right - 10)}, ${f(GEO.floor + 2)})) / float2(80.0, 10.0);
  float pool = (1.0 - smoothstep(0.0, 1.0, length(pq))) * step(0.0, fy + 2.0);
  float a = 1.0 - (1.0 - wall * smoothstep(3.0, -3.0, fy)) * (1.0 - contact * 0.8) * (1.0 - pool * 0.35);
  return half4(0.0, 0.0, 0.0, half(a));
}
`;

/** The studio: a warm near-black sweep, lighter behind the machine, a vignette and a fine grain. Screen space. */
const PANEL = `${COMMON}
uniform float2 uSize;
uniform float2 uPivot;
uniform float uK;
uniform float uPR;
half4 main(float2 p) {
  float v = clamp(p.y / uSize.y, 0.0, 1.0);
  float3 c = mix(${v3(MATERIAL.panelHigh)} * 0.55, ${v3(MATERIAL.panelLow)}, smoothstep(0.0, 1.0, v));
  float2 lp = (p - uPivot - float2(-40.0, 90.0) * uK) / (float2(300.0, 330.0) * uK);
  c += ${v3(MATERIAL.panelHigh)} * exp(-dot(lp, lp));
  // The floor of the sweep, lit near the machine's feet.
  float2 fp = (p - uPivot - float2(-30.0, ${f(GEO.floor + 10)}) * uK) / (float2(280.0, 70.0) * uK);
  c += ${v3(MATERIAL.floor)} * 1.2 * exp(-dot(fp, fp));
  float2 e = (p / uSize - 0.5) * float2(1.1, 1.0);
  c *= 1.0 - 0.55 * smoothstep(0.32, 0.8, length(e));
  c += (hash12(floor(p * uPR)) - 0.5) * ${f(LIGHT.grain)};
  return half4(half3(srgb(c)), 1.0);
}
`;

/** Over the gauge's numerals: the needle and its shadow, the hub, and the domed glass. Gauge-centred. */
const GAUGE_GLASS = `${COMMON}${LIGHTING}
uniform float uAA;
uniform float uNeedle;
const float RD = ${f(GA.dial)};
const float TIP = ${f(GA.ticks - 0.8)};
// A slim pointer reaching into the ticks, a short tail behind the hub ending in a round counterweight.
float sdNeedle(float2 p) {
  float2 d = float2(cos(uNeedle), sin(uNeedle));
  float s = dot(p, d);
  float t = dot(p, float2(-d.y, d.x));
  float hw = mix(0.95, 0.24, clamp(s / TIP, 0.0, 1.0));
  float blade = max(abs(t) - hw, max(-6.0 - s, s - TIP));
  return min(blade, length(float2(s + 5.6, t)) - 1.55);
}
half4 main(float2 p) {
  half4 acc = half4(0.0);
  float r = length(p);
  if (r > RD + uAA) return acc;
  // The needle's shadow on the face, a little below and to the right.
  float sh = 1.0 - smoothstep(-0.5, 1.4, sdNeedle(p - float2(0.8, 1.2)));
  acc = half4(0.0, 0.0, 0.0, half(sh * 0.5));
  float dn = sdNeedle(p);
  if (dn < uAA) {
    float2 d = float2(cos(uNeedle), sin(uNeedle));
    float t = clamp(dot(p, float2(-d.y, d.x)) / 1.0, -0.95, 0.95);
    float3 c = dielectric(float3(p, 2.0), float3(-d.y * t * 0.6, d.x * t * 0.6, 0.8), ${v3(MATERIAL.needle)}, 0.2, 0.6);
    acc = over(acc, srgb(c), cover(dn, uAA));
  }
  // The hub: a polished cap with a dark pin in its middle.
  float dc = r - 2.8;
  if (dc < 2.5) acc = over(acc, float3(0.0), (1.0 - smoothstep(-0.4, 1.2, length(p - float2(0.5, 0.7)) - 2.8)) * 0.45 * step(uAA, dc));
  if (dc < uAA) {
    float2 q = p / 2.8;
    float3 n = normalize(float3(q * 0.9, 1.0));
    float3 c = metal(float3(p, 4.0), view(n), ${CHROME} * 0.9, 0.1);
    c = mix(c, float3(0.02), 1.0 - smoothstep(0.75 - uAA, 0.75 + uAA, r));
    acc = over(acc, srgb(c), cover(dc, uAA));
  }
  // The glass: a faint reflection of the softbox high on the left, a fresnel rim, one small glint.
  float2 n2 = p / RD;
  float rr = dot(n2, n2);
  float nz = sqrt(max(1.0 - rr * 0.35, 0.0));
  float3 N = normalize(float3(n2 * 0.6, nz));
  float3 R = reflect(-V, view(N));
  float3 refl = env(R, 0.35) * (0.04 + 0.96 * pow(1.0 - view(N).z, 5.0));
  float2 wq = rot(n2 - float2(-0.34, -0.5), float2(0.8, 0.6)) * float2(1.0, 3.2);
  float band = (1.0 - smoothstep(0.18, 0.4, length(wq))) * smoothstep(0.0, 0.6, n2.y + 0.95);
  float3 glass = refl * 0.06 + float3(1.0) * band * 0.03;
  glass += float3(1.0) * exp(-length(n2 - float2(-0.5, -0.56)) / 0.018) * 0.25;
  // Light on glass only adds: premultiplied colour over the same alpha.
  return half4(acc.rgb + half3(srgb(glass) * cover(r - RD, uAA)), acc.a);
}
`;

/** Over the timer's drums and digits: the drums' curve, the shadow of the window's lip, and the glass. */
const TIMER_GLASS = `${COMMON}
uniform float2 uSize;
uniform float uR;
half4 main(float2 p) {
  float d = sdBox(p - uSize * 0.5, uSize * 0.5, uR);
  float inside = clamp(0.5 - d, 0.0, 1.0);
  float2 n = p / uSize;
  float curve = pow(abs(n.y - 0.5) * 2.0, 2.4) * 0.82;
  float lip = (1.0 - smoothstep(0.0, 6.0, p.y)) * 0.55 + (1.0 - smoothstep(0.0, 4.0, p.x)) * 0.35;
  float dark = 1.0 - (1.0 - curve) * (1.0 - lip);
  float band = smoothstep(0.0, 0.35, 0.62 - (n.x * 0.55 + n.y)) * smoothstep(0.62, 0.3, 0.62 - (n.x * 0.55 + n.y) + 0.25);
  float edge = exp(-abs(d + 1.2) / 0.5) * (1.0 - smoothstep(0.2, 0.75, n.y));
  float3 light = float3(1.0) * (band * 0.07 + edge * 0.28);
  return half4(half3(light * inside), half(dark * inside));
}
`;

function make(name: string, source: string): SkRuntimeEffect {
  const effect = Skia.RuntimeEffect.Make(source);
  if (!effect) throw new Error(`pull-lever-refresh: the ${name} shader did not compile`);
  return effect;
}

export const SHADERS = {
  panel: make('panel', PANEL),
  cast: make('cast', CAST),
  body: make('body', BODY),
  tray: make('tray', TRAY),
  group: make('group', GROUP),
  cup: make('cup', CUP_SRC),
  streams: make('streams', STREAMS),
  steam: make('steam', STEAM),
  lever: make('lever', LEVER),
  leverShadow: make('leverShadow', LEVER_SHADOW),
  gaugeGlass: make('gaugeGlass', GAUGE_GLASS),
  timerGlass: make('timerGlass', TIMER_GLASS),
};


