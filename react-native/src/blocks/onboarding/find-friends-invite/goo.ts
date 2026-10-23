/**
 * The contacts: the four avatars melt into one liquid blob, contact droplets are pulled in from
 * the screen edges, and the blob splits back into the four avatars.
 *
 * Each droplet carries a contact's face (Overlay clips a head into it), fading as it merges.
 *
 * The blob is a metaball field drawn by one SkSL RuntimeEffect: each ball adds r²/d², and the
 * surface is the iso-line f = 1. `goo` blends the field from the union of the circles (max, at 0:
 * exactly the resting avatars, the later one on top where two overlap) to the sum (at 1: bridges
 * grow between nearby balls and droplets neck into the blob). The edge is antialiased with the
 * field's gradient, and a little dome lighting (`gloss`) makes it read as liquid.
 *
 * Skia treats RuntimeEffect output as Display P3 on iOS: colours are converted from sRGB at the end.
 * Identifiers avoid WGSL builtin and reserved names.
 */
import { clamp01, ease, EASE_IN_OUT, EASE_OUT, GOO, kick, seg, springStep } from './motion';

const BALLS = 9;

const ballDecls = Array.from({ length: BALLS }, (_, i) => `uniform float4 b${i};\nuniform float3 k${i};`).join('\n');
const ballCalls = Array.from({ length: BALLS }, (_, i) => `  ball(p, b${i}, k${i}, fs, gs, fu, gu, cw, ws, ctop, cmax);`).join('\n');

export const GOO_SKSL = `
${ballDecls}
uniform float goo;
uniform float gloss;

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

void ball(float2 p, float4 b, float3 k, inout float fs, inout float2 gs, inout float fu, inout float2 gu,
          inout float3 cw, inout float ws, inout float3 ctop, inout float3 cmax) {
  if (b.z <= 0.0) { return; }
  float2 dv = p - b.xy;
  float d2 = max(dot(dv, dv), 0.0001);
  float q = b.z * b.z / d2;
  float2 gq = -2.0 * q * dv / d2;
  fs += q;
  gs += gq;
  if (q > fu) { fu = q; gu = gq; cmax = k; }
  if (q >= 1.0) { ctop = k; }
  float e = d2 + 30.0;
  float w = b.z / (e * e * e);
  cw += k * w;
  ws += w;
}

half4 main(float2 p) {
  float fs = 0.0;
  float fu = 0.0;
  float2 gs = float2(0.0);
  float2 gu = float2(0.0);
  float3 cw = float3(0.0);
  float ws = 0.0;
  float3 ctop = float3(-1.0);
  float3 cmax = float3(0.0);
${ballCalls}
  float f = mix(fu, fs, goo);
  float2 gr = mix(gu, gs, goo);
  float gl = max(length(gr), 0.0001);
  float sd = (f - 1.0) / gl;
  float a = clamp(sd * 3.0 + 0.5, 0.0, 1.0);
  if (a <= 0.0) { return half4(0.0); }
  float3 base = ctop.r >= 0.0 ? ctop : cmax;
  float3 mixc = ws > 0.0 ? cw / ws : base;
  float3 col = mix(base, mixc, smoothstep(0.0, 0.5, goo));
  float dep = max(clamp(sd / 16.0, 0.0, 1.0), clamp((f - 1.0) / 1.5, 0.0, 1.0));
  float2 outw = -gr / gl;
  float tilt = pow(1.0 - dep, 1.6) * 1.4;
  float3 n = normalize(float3(outw * tilt, 1.0));
  float3 lv = normalize(float3(-0.45, -0.65, 0.75));
  float dif = dot(n, lv) - lv.z;
  float3 hv = normalize(lv + float3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, hv), 0.0), 60.0);
  float3 lit = col * (1.0 + 0.35 * dif) + float3(spec * 0.55);
  col = mix(col, lit, gloss);
  float3 outc = toP3(clamp(col, 0.0, 1.0));
  return half4(outc * a, a);
}
`;

function hex(c: string): number[] {
  const v = parseInt(c.slice(1), 16);
  return [((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255];
}

export type Ball = { x: number; y: number; r: number };

export type GooGeo = {
  /** The four avatars at rest (screen pt). */
  rest: Ball[];
  /** Area-weighted centre of the cluster. */
  cx: number;
  cy: number;
  width: number;
};

export type GooFrame = {
  on: boolean;
  uniforms: Record<string, number | number[]>;
  /** Per avatar face: translation, scale, opacity. */
  faces: { tx: number; ty: number; s: number; o: number }[];
  /** Per droplet: centre, scale of its face (radius ÷ the droplet's own) and the face's opacity. */
  drops: { x: number; y: number; s: number; o: number }[];
};

/** Colours as sRGB floats: the four avatars, then the droplets. */
export function gooColors(avatarColors: string[]): number[][] {
  const out: number[][] = [];
  for (let i = 0; i < avatarColors.length; i++) out.push(hex(avatarColors[i]));
  for (let i = 0; i < GOO.drops.length; i++) out.push(hex(GOO.drops[i].color));
  return out;
}

/** Whether the React Native avatars are shown at `sigma` seconds after the press. */
export function avatarsShown(sigma: number) {
  'worklet';
  return sigma < GOO.rnOff || sigma >= GOO.rnOn;
}

/** Is the Skia blob drawn at σ? Outside, the overlay pins its goo clock to 0 (no redraws at rest). */
export function gooOn(sigma: number) {
  'worklet';
  return sigma >= GOO.skiaOn && sigma < GOO.skiaOff;
}

/** The blob at `sigma` seconds after the press of "check contacts". */
export function gooFrame(sigma: number, g: GooGeo, colors: number[][]): GooFrame {
  'worklet';
  const uniforms: Record<string, number | number[]> = { goo: 0, gloss: 0 };
  const faces: { tx: number; ty: number; s: number; o: number }[] = [];
  const on = gooOn(sigma);
  for (let i = 0; i < BALLS; i++) {
    uniforms[`b${i}`] = [0, 0, 0, 0];
    uniforms[`k${i}`] = colors[i];
  }
  const drops: { x: number; y: number; s: number; o: number }[] = [];
  for (let i = 0; i < 4; i++) faces.push({ tx: 0, ty: 0, s: 1, o: 1 });
  for (let j = 0; j < GOO.drops.length; j++) drops.push({ x: 0, y: 0, s: 1, o: 0 });
  if (!on) return { on, uniforms, faces, drops };

  const goo =
    ease(EASE_IN_OUT, seg(sigma, GOO.gooIn[0], GOO.gooIn[1])) * (1 - ease(EASE_OUT, seg(sigma, GOO.gooOut[0], GOO.gooOut[1])));
  uniforms.goo = goo;
  uniforms.gloss = goo;

  // Pull toward the centre (soft spring), then split back out (underdamped, through the rest places).
  const pl = GOO.pull;
  const sp = GOO.split;
  const u = pl.amount * springStep(sigma - pl.start, pl.omega, pl.zeta) * (1 - springStep(sigma - sp.start, sp.omega, sp.zeta));

  // Swell from each absorbed droplet.
  let swell = 0;
  const ak = GOO.absorbKick;
  for (let j = 0; j < GOO.drops.length; j++) {
    const at = GOO.drops[j].start + GOO.dropDur * 0.85;
    swell += ak.amp * kick(sigma - at, ak.period, ak.decay, ak.fadeFrom, ak.end);
  }
  const wb = GOO.wobble;
  const env = seg(sigma, wb.on[0], wb.on[1]) * (1 - seg(sigma, wb.off[0], wb.off[1]));

  for (let i = 0; i < 4; i++) {
    const b = g.rest[i];
    const ph = i * 1.7;
    const ox = wb.amp * env * Math.sin(2 * Math.PI * wb.freq * sigma + ph);
    const oy = wb.amp * env * Math.cos(2 * Math.PI * wb.freq * 1.3 * sigma + ph);
    const x = b.x + u * (g.cx - b.x) + ox;
    const y = b.y + u * (g.cy - b.y) + oy;
    const r = b.r * (1 - pl.shrink * clamp01(u)) * (1 + swell);
    uniforms[`b${i}`] = [x, y, r, 0];

    // Faces sink, then come back up on a spring once the blob has split.
    const rk = GOO.rank[i];
    const sk = GOO.sink;
    const fi = GOO.faceIn;
    const back = fi.start + rk * fi.stagger;
    let o = 1;
    let s = 1;
    if (sigma < back) {
      const e = ease(EASE_OUT, seg(sigma, sk.start + rk * sk.stagger, sk.start + rk * sk.stagger + sk.dur));
      o = 1 - e;
      s = 1 - (1 - sk.scale) * e;
    } else {
      o = ease(EASE_OUT, seg(sigma, back, back + fi.dur));
      s = fi.from + (1 - fi.from) * springStep(sigma - back, fi.omega, fi.zeta);
    }
    faces[i] = { tx: x - b.x, ty: y - b.y, s, o };
  }

  // Contact droplets from the screen edges.
  for (let j = 0; j < GOO.drops.length; j++) {
    const dr = GOO.drops[j];
    const q = seg(sigma, dr.start, dr.start + GOO.dropDur);
    if (q <= 0 || q >= 1) continue;
    const e = ease(EASE_IN_OUT, q);
    const sx = dr.side < 0 ? -GOO.dropOffscreen - dr.r : g.width + GOO.dropOffscreen + dr.r;
    const sy = g.cy + dr.dy;
    const tx = g.cx;
    const ty = g.cy + dr.dy * 0.15;
    const bow = (dr.dy >= 0 ? 1 : -1) * GOO.dropArc * Math.sin(Math.PI * e);
    const x = sx + (tx - sx) * e;
    const y = sy + (ty - sy) * e + bow;
    const r = dr.r * (1 - seg(e, GOO.dropAbsorb, 1));
    uniforms[`b${4 + j}`] = [x, y, r, 0];
    drops[j] = { x, y, s: r / dr.r, o: 1 - ease(EASE_OUT, seg(e, GOO.dropFaces.fade[0], GOO.dropFaces.fade[1])) };
  }
  return { on, uniforms, faces, drops };
}
