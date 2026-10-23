/**
 * The living heart, its halo and the ECG trace, as one Skia RuntimeEffect (SkSL).
 *
 * Shape: the pin's heart outline, measured at 3× and fitted as a polar cosine series around
 * HEART.centre (θ from straight up, clockwise): r(θ) = Σ c_k cos kθ, max error 3.4 pt, rms 0.5 pt.
 * The signed distance is approximated by (ρ − r) / √(1 + (r′/r)²) (≈ 1 pt off near the edge).
 *
 * Rest look (all fitted on the pin, sRGB 0–255, d = inside distance in pt, v = (y − 172) / 232,
 * u = (x − 201.2) / 122): colour = a + b·e^(−d/3) + c·e^(−d/3)·v + e·e^(−d/15) + f·e^(−d/15)·v
 * + g·v + h·u². The pink halo is the heart's red at a low alpha: a plateau to 40 pt and a ring
 * peaking 37 pt out, gone by 53 pt. At rest the shader reproduces the pin; motion only adds.
 *
 * Skia treats RuntimeEffect output as Display P3 on iOS: colours are converted from sRGB at the
 * end (toP3). Output is premultiplied so the page colour shows through.
 */

export const HEART = {
  centre: [201.1667, 290] as const,
  /** Polar cosine coefficients c_0…c_13 in ref pt. */
  radius: [114.202, 15.914, -2.614, -14.183, -4.316, -4.079, -0.688, -1.807, -0.379, -1.124, -0.233, -0.716, -0.107, -0.477],
  /** Canvas covers ref y 120 (under the metric strip) to 500 (behind the headline). */
  canvasTop: 120,
  canvasBottom: 500,
  /** The halo follows a rounder heart: the first four harmonics only (no notch). */
  haloHarmonics: 4,
};

/** Colour fit per channel: [a, b, c, e, f, g, h] (see header). */
export const BODY_FIT = {
  r: [254.3, -16.0, -8.8, 2.4, 2.0, -0.8, -0.6],
  g: [124.9, -22.2, 43.3, -37.6, -53.6, -27.7, -71.4],
  b: [145.8, -31.5, 30.5, -24.5, -32.9, -26.7, -53.9],
};

/** ECG waveform (ref pt up, against u = x offset / sweep width): gaussians [amp, centre, sigma]. */
export const ECG = {
  baseline: 362,
  sweep: 402,
  waves: [
    [3.5, -0.2, 0.025], // P
    [-3, -0.028, 0.009], // Q
    [26, 0, 0.0085], // R (tip lands 4 pt under "BPM")
    [-8, 0.026, 0.01], // S
    [6, 0.24, 0.04], // T
  ],
};

const f = (n: number) => (Number.isInteger(n) ? `${n}.0` : `${n}`);

function radiusCode(): string {
  const c = HEART.radius;
  const lines = [`float r = ${f(c[0])}; dr = 0.0; float ck = cs; float sk = sn; float t;`];
  for (let k = 1; k < c.length; k += 1) {
    if (k > 1) lines.push('t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;');
    lines.push(`r += ${f(c[k])} * ck; dr -= ${f(k * c[k])} * sk;`);
  }
  return lines.join('\n  ');
}

function haloRadiusCode(): string {
  const c = HEART.radius;
  const lines = [`float r = ${f(c[0])}; float ck = cs; float sk = sn; float t;`];
  for (let k = 1; k < HEART.haloHarmonics; k += 1) {
    if (k > 1) lines.push('t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;');
    lines.push(`r += ${f(c[k])} * ck;`);
  }
  return lines.join('\n  ');
}

function fitCode(ch: number[]): string {
  return `${f(ch[0])} + ${f(ch[1])} * e1 + ${f(ch[2])} * e1 * v + ${f(ch[3])} * e2 + ${f(ch[4])} * e2 * v + ${f(ch[5])} * v + ${f(ch[6])} * u * u`;
}

function ecgCode(): string {
  return ECG.waves
    .map(([a, c, s]) => `${f(a)} * exp(-((u - ${f(c)}) * (u - ${f(c)})) / ${f(2 * s * s)})`)
    .join(' + ');
}

export const HEART_SKSL = `
uniform float scale;    // canvas pt per ref pt
uniform float4 deform;  // body, lobe, squash, lift
uniform float4 shine;   // gloss, light, push, flash
uniform float2 taus;    // seconds since the last two lubs (ripples)
uniform float3 trace;   // phi, traceStart, traceEnd

const float2 C = float2(${f(HEART.centre[0])}, ${f(HEART.centre[1])});
const float TOP = ${f(HEART.canvasTop)};
const float3 RED = float3(0.957, 0.18, 0.349);

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float radiusAt(float cs, float sn, out float dr) {
  ${radiusCode()}
  return r;
}

// Approximate signed distance to the heart outline (ref pt, negative inside), q from the centre.
float shapeDist(float2 q) {
  float rho = length(q) + 0.0001;
  float dr;
  float r = radiusAt(-q.y / rho, q.x / rho, dr);
  return (rho - r) / sqrt(1.0 + (dr * dr) / (r * r));
}

// Inverse of the beat's deformation: screen offset from the centre -> undeformed offset.
float2 unwarp(float2 q) {
  float w = smoothstep(20.0, -90.0, q.y);  // upper lobes
  float sx = (1.0 + deform.x) * (1.0 + deform.z) + deform.y * w;
  float sy = (1.0 + deform.x) * (1.0 - 0.8 * deform.z) + 0.4 * deform.y * w;
  return float2(q.x / sx, q.y / sy);
}

float haloRadius(float cs, float sn) {
  ${haloRadiusCode()}
  return r;
}

// Distance to the rounder halo heart (radial, ref pt).
float haloDist(float2 q) {
  float2 q0 = unwarp(q);
  float rho = length(q0) + 0.0001;
  return (rho - haloRadius(-q0.y / rho, q0.x / rho)) * (1.0 + deform.x);
}

float distAt(float2 q) {
  return shapeDist(unwarp(q)) * (1.0 + deform.x);
}

// Soft ellipse at c with radii r, rotated by a (rad). edge >= 0: crisp rim at that inner share;
// edge < 0: gaussian falloff.
float blob(float2 q, float2 c, float2 r, float a, float edge) {
  float2 d = q - c;
  float cs = cos(a); float sn = sin(a);
  d = float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y) / r;
  float e = dot(d, d);
  return edge >= 0.0 ? smoothstep(1.0, edge, e) : exp(-2.0 * e);
}

float ecg(float u) {
  return ${ecgCode()};
}

float ripple(float d, float tau) {
  if (tau < 0.0 || tau > 0.75) return 0.0;
  float o = 4.0 + 150.0 * tau;
  float w = 4.0 + 12.0 * tau;
  float k = 1.0 - tau / 0.75;
  return 0.11 * k * k * smoothstep(0.0, 0.03, tau) * exp(-((d - o) * (d - o)) / (w * w));
}

half4 main(float2 pos) {
  float2 p = pos / scale + float2(0.0, TOP);
  float2 q = p - C - float2(0.0, deform.w);
  float sd = distAt(q);

  // Halo: the pin's pink ring, pushed out by the beat, plus the pressure ripples.
  float d = haloDist(q) - shine.z;
  float vy = clamp((p.y - 172.0) / 232.0, 0.0, 1.0);
  float halo = (7.0 * (1.0 - smoothstep(36.0, 56.0, d)) + 7.0 * exp(-((d - 36.0) * (d - 36.0)) / 110.0)) / 193.0;
  halo *= (1.12 - 0.25 * vy) * smoothstep(-1.0, 3.0, sd);
  float rip = (ripple(sd, taus.x) + ripple(sd, taus.y)) * smoothstep(120.0, 160.0, p.y) * smoothstep(500.0, 450.0, p.y);
  float aHalo = clamp(halo + rip * smoothstep(-1.0, 3.0, sd), 0.0, 1.0);
  float4 o = float4(toP3(RED) * aHalo, aHalo);

  // Body: the pin's matte gel shading, carried by the deformation.
  float bodyA = 1.0 - smoothstep(-2.0, 2.0, sd);
  if (bodyA > 0.0) {
    float2 q0 = unwarp(q);
    float din = max(-sd / (1.0 + deform.x), 0.0);
    float v = (q0.y + C.y - 172.0) / 232.0;
    float u = q0.x / 122.0;
    float e1 = exp(-din / 3.0);
    float e2 = exp(-din / 15.0);
    float3 col = float3(${fitCode(BODY_FIT.r)}, ${fitCode(BODY_FIT.g)}, ${fitCode(BODY_FIT.b)}) / 255.0;

    // Wet gloss: painted highlights on the lobes, carried by the deformation (q0) and slid by
    // the beat's light shift; a rim sheen on the upper-left edge. Zero at rest (the pin is matte).
    float gloss = shine.x;
    if (gloss > 0.002) {
      float2 slide = shine.y * float2(40.0, 22.0);
      float spec = 0.85 * blob(q0, float2(-62.0, -66.0) + slide, float2(24.0, 10.5), -0.7, 0.3)
                 + 0.45 * blob(q0, float2(58.0, -72.0) + 0.5 * slide, float2(9.0, 4.5), 0.6, 0.3)
                 + 0.14 * blob(q0, float2(-30.0, -40.0) + slide, float2(78.0, 56.0), -0.5, -1.0);
      float rim = exp(-din / 3.5) * clamp(dot(normalize(q0 + float2(0.0, 0.0001)), float2(-0.55, -0.83)), 0.0, 1.0) * 0.32;
      col = mix(col, float3(0.86, 0.06, 0.24), 0.10 * gloss);   // the muscle flushes deeper
      col += (spec + rim) * gloss;
    }
    // R-peak bloom: the heart lights from within for ~200 ms (the reading's flash).
    float bloom = shine.w * 0.2 * exp(-dot(q0 - float2(0.0, -6.0), q0 - float2(0.0, -6.0)) / 7200.0);
    col += bloom * float3(1.0, 0.82, 0.86);
    float3 pc = toP3(col);
    o = float4(pc * bodyA, bodyA) + o * (1.0 - bodyA);
  }

  // ECG trace: a phosphor line swept left to right once per beat, R-peak at the heart's centre.
  float phi = trace.x;
  if (p.y > ${f(ECG.baseline - 40)} && p.y < ${f(ECG.baseline + 22)} && phi > trace.y) {
    float W = ${f(ECG.sweep)};
    float penU = fract(phi + 0.5) - 0.5;
    float u = (p.x - C.x) / W;
    float age = fract(penU - u);
    float drawnAt = phi - age;
    float fadeOut = 1.0 - smoothstep(trace.z, trace.z + ${f(0.6)}, phi);
    float vis = step(trace.y, drawnAt) * step(drawnAt, trace.z) * smoothstep(${f(0.97)}, ${f(0.9)}, age);
    float alpha = vis * exp(-${f(1.25)} * age) * fadeOut;
    float h = 0.0015;
    float yT = ${f(ECG.baseline)} - ecg(u);
    float slope = (ecg(u - h) - ecg(u + h)) / (2.0 * h * W);
    float dist = abs(p.y - yT) / sqrt(1.0 + slope * slope);
    float core = 1.0 - smoothstep(0.95, 1.8, dist);
    float glow = exp(-(dist * dist) / 26.0) * 0.4;
    float penOn = step(trace.y, phi) * step(phi, trace.z) * fadeOut;
    float2 head = float2(C.x + penU * W, ${f(ECG.baseline)} - ecg(penU));
    float hd = dot(p - head, p - head);
    float pen = penOn * (exp(-hd / 7.0) + 0.45 * exp(-hd / 70.0));
    float a = clamp(max(core, glow) * alpha + pen, 0.0, 1.0);
    if (a > 0.001) {
      float3 tc = mix(RED * 0.92, float3(1.0), bodyA);
      o = float4(toP3(tc) * a, a) + o * (1.0 - a);
    }
  }
  return half4(o);
}
`;
