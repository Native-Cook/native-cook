/** Analytical port of HeatShapePickerModel.swift. Times are seconds. */
export const LOOP = 8;
export type Pose = { p: number; c: number; k: number; phi: number; h: number };
export type Layer = { pose: Pose; opacity: number };
export type Frame = {
  pose: Pose; layers: Layer[]; time: number; morph: number; glow: number; ring: number;
  selected: number; labels: number[]; labelY: number[]; chips: number[];
};
export type Transition = { source: Frame; target: number; at: number; settling?: boolean };
export type Spring = { from: number; target: number; velocity: number; at: number; response: number; damping: number };
export type Layout = { width: number; height: number; sx: number; sy: number; shift: number; cx: number; cy: number };

export const NAMES = ['Boost', 'Warm', 'Eco', 'Breeze'];
export const CAPTIONS = ['Full power for ten minutes', 'Steady 22° through the evening', 'Holds 18° and saves energy', 'Fan only, no heat'];
export const MODES: Pose[] = [
  { p: 0.62, c: 0, k: 1.16, phi: 0, h: 0.50 },
  { p: 2, c: 0, k: 0.96, phi: Math.PI / 4, h: 1.25 },
  { p: 4.6, c: 0, k: 0.92, phi: Math.PI / 2, h: 2 },
  { p: 2.6, c: 0.26, k: 1.04, phi: 3 * Math.PI / 4, h: 2.75 },
  { p: 0.62, c: 0, k: 1.16, phi: Math.PI, h: 3.50 },
];
export function clamp(x: number) { 'worklet'; return Math.min(1, Math.max(0, x)); }
export function rounded(x: number) { 'worklet'; return x >= 0 ? Math.floor(x + 0.5) : -Math.floor(-x + 0.5); }
export function mix(a: number, b: number, m: number) { 'worklet'; return a + (b - a) * m; }
export function seg(t: number, a: number, b: number) { 'worklet'; return clamp((t - a) / (b - a)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function ease(x: number) { 'worklet'; const u = clamp(x); return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(2 - 2 * u, 3) / 2; }
export function out(x: number) { 'worklet'; return 1 - Math.pow(1 - clamp(x), 3); }
export function back(x: number) { 'worklet'; const u = clamp(x) - 1; return 1 + 2.2 * u * u * u + 1.2 * u * u; }
export function spring(age: number, response: number, damping: number) {
  'worklet';
  if (age <= 0) return 0;
  const w = 2 * Math.PI / response, wd = w * Math.sqrt(1 - damping * damping);
  const v = 1 - Math.exp(-damping * w * age) * (Math.cos(wd * age) + damping * w / wd * Math.sin(wd * age));
  return mix(v, 1, smooth((age - 1) / 0.2));
}
export function interpolate(a: Pose, b: Pose, m: number): Pose {
  'worklet';
  return { p: Math.exp(mix(Math.log(a.p), Math.log(b.p), m)), c: mix(a.c, b.c, m),
    k: mix(a.k, b.k, m), phi: mix(a.phi, b.phi, m), h: mix(a.h, b.h, m) };
}

/** The only outline function. Null time disables wobble for glyphs and reduced motion. */
export function radius(theta: number, p: number, c: number, k: number, phi: number, t: number | null) {
  'worklet';
  const a = theta - phi;
  const ac = Math.sqrt(Math.pow(Math.cos(a), 2) + 0.004);
  const as = Math.sqrt(Math.pow(Math.sin(a), 2) + 0.004);
  const rp = Math.pow(Math.pow(ac, p) + Math.pow(as, p), -1 / p);
  const lobe = 1 - c * Math.pow(1 - Math.abs(Math.sin(2 * a)), 2.2);
  const tau = 2 * Math.PI * (t ?? 0) / LOOP;
  const wob = t === null ? 1 : 1 + 0.018 * Math.sin(3 * theta + 2 * tau) + 0.012 * Math.sin(5 * theta - 3 * tau + 1.7);
  return 110 * k * rp * lobe * wob;
}

const RAMP = [
  [0, 0x2B2BE8], [0.12, 0x14051F], [0.25, 0xE3210B], [0.38, 0xFF7A1A],
  [0.50, 0xFFD75A], [0.62, 0xFFFFFF], [0.78, 0xC9C4FF], [0.90, 0x6A63FF], [1, 0x2B2BE8],
];
export function ramp(value: number, alpha = 1) {
  'worklet';
  const x = value - Math.floor(value);
  let j = 7;
  for (let i = 0; i < 8; i++) { if (x < RAMP[i + 1][0]) { j = i; break; } }
  const [a, ca] = RAMP[j], [b, cb] = RAMP[j + 1], m = (x - a) / (b - a);
  return Float32Array.of(mix((ca >> 16) & 255, (cb >> 16) & 255, m) / 255,
    mix((ca >> 8) & 255, (cb >> 8) & 255, m) / 255, mix(ca & 255, cb & 255, m) / 255, alpha);
}
export function rampString(value: number) {
  const c = ramp(value);
  return `rgb(${c[0] * 255}, ${c[1] * 255}, ${c[2] * 255})`;
}
export function coalesced(layers: Layer[]) {
  'worklet';
  const result: Layer[] = [];
  for (const layer of layers) {
    if (layer.opacity <= 0.001) continue;
    const p = layer.pose;
    const i = result.findIndex((item) => {
      const q = item.pose, dh = p.h - q.h;
      return Math.abs(p.p - q.p) + Math.abs(p.c - q.c) + Math.abs(p.k - q.k) < 0.00001
        && Math.abs(Math.sin(2 * (p.phi - q.phi))) < 0.00001 && Math.abs(dh - rounded(dh)) < 0.00001;
    });
    if (i >= 0) result[i].opacity += layer.opacity;
    else result.push({ ...layer });
  }
  return result;
}
export function makeFrame(): Frame {
  'worklet';
  const pose = { ...MODES[0] };
  return { pose, layers: [{ pose, opacity: 1 }], time: 0, morph: 0, glow: 1, ring: 0, selected: 0,
    labels: [1, 0, 0, 0], labelY: [0, 0, 0, 0], chips: [1, 0, 0, 0] };
}
export function automatic(time: number, reduced: boolean): Frame {
  'worklet';
  const t = Math.max(0, time) % LOOP;
  const i = Math.floor(t / 2), next = (i + 1) % 4, s = t - i * 2;
  const u = seg(s, 1.1, 2), m = ease(u);
  const f = makeFrame(); f.time = t; f.selected = s < 1.55 ? i : next;
  f.ring = mix(i, next, reduced ? seg(s, 1.25, 1.75) : back(seg(s, 1.25, 1.75)));
  f.chips = [0, 0, 0, 0];
  const chip = seg(s, 1.45, 1.65); f.chips[i] = 1 - chip; f.chips[next] = chip;
  f.labels = [0, 0, 0, 0]; f.labelY = [0, 0, 0, 0];
  if (reduced) {
    const fade = smooth(seg(s, 1.55, 1.85));
    f.pose = { ...MODES[f.selected] };
    f.layers = [{ pose: MODES[i], opacity: 1 - fade }, { pose: MODES[next], opacity: fade }];
    f.labels[i] = 1 - fade; f.labels[next] = fade;
    return f;
  }
  f.pose = interpolate(MODES[i], MODES[i + 1], m);
  if (s < 1.1) {
    f.pose.k *= 1 + 0.022 * Math.sin(2 * Math.PI * s / 1.1);
    f.pose.h += 0.045 * Math.sin(2 * Math.PI * s / 1.1);
  }
  else {
    const scale = u < 0.28 ? mix(1, 0.90, smooth(u / 0.28))
      : u < 0.72 ? mix(0.90, 1.07, smooth((u - 0.28) / 0.44))
      : mix(1.07, 1, smooth((u - 0.72) / 0.28));
    f.pose.k *= scale;
  }
  f.layers = [{ pose: f.pose, opacity: 1 }]; f.morph = m;
  f.glow = s < 1.1 ? 1 + 0.08 * Math.sin(Math.PI * s) : 1 + 0.5 * Math.sin(Math.PI * m);
  const old = Math.pow(seg(s, 1.35, 1.55), 3), incoming = out(seg(s, 1.55, 1.85));
  f.labels[i] = 1 - old; f.labelY[i] = -10 * old;
  f.labels[next] = incoming; f.labelY[next] = 10 * (1 - incoming);
  return f;
}
export function transitionFrame(transition: Transition, now: number, reduced: boolean): Frame {
  'worklet';
  const { source, target, at, settling = false } = transition;
  const age = Math.max(0, now - at), fade = smooth(age / 0.3);
  const f: Frame = { ...source, labels: source.labels.slice(), labelY: source.labelY.slice(), chips: source.chips.slice() };
  const end = { ...MODES[target] };
  if (settling) {
    end.h += rounded(source.pose.h - end.h);
    end.phi += rounded((source.pose.phi - end.phi) / (Math.PI / 2)) * Math.PI / 2;
  } else {
    while (end.h < source.pose.h) end.h += 1;
    while (end.phi < source.pose.phi) end.phi += Math.PI;
  }
  const m = reduced ? fade : spring(age, 0.55, 0.68);
  f.selected = target; f.pose = interpolate(source.pose, end, m);
  f.time = mix(source.time, target * 2, smooth(age / 1.2));
  f.morph = reduced ? 0 : settling ? mix(source.morph, 0, fade) : clamp(m);
  f.glow = mix(source.glow, 1, fade) + (reduced || settling ? 0 : 0.5 * Math.sin(Math.PI * f.morph));
  if (reduced) {
    f.pose = end;
    f.layers = source.layers.map((l) => ({ pose: l.pose, opacity: l.opacity * (1 - fade) }));
    f.layers.push({ pose: end, opacity: fade });
    f.layers = coalesced(f.layers);
  } else {
    if (!settling) f.pose.k *= 1 + 0.8 * Math.max(0, m - 1);
    // One idle heat cycle ends at the two-second autoplay resume boundary.
    const heatTime = Math.max(0, age - 0.9);
    f.pose.h += 0.045 * Math.sin(2 * Math.PI * heatTime / 1.1);
    f.layers = [{ pose: f.pose, opacity: 1 }];
  }
  const ring = reduced ? fade : spring(age, 0.4, 0.72);
  f.ring = mix(source.ring, target, ring);
  for (let i = 0; i < 4; i++) {
    const to = i === target ? 1 : 0;
    f.chips[i] = mix(source.chips[i], to, clamp(age / 0.2));
    f.labels[i] = mix(source.labels[i], to, fade);
    f.labelY[i] = reduced ? 0 : i === target
      ? mix(source.labels[i] > 0 ? source.labelY[i] : 10, 0, out(age / 0.3))
      : source.labelY[i] - 10 * fade;
  }
  return f;
}
export function makeSpring(): Spring {
  'worklet';
  return { from: 0, target: 0, velocity: 0, at: 0, response: 0.5, damping: 0.5 };
}
export function springState(s: Spring, now: number): [number, number] {
  'worklet';
  const age = Math.max(0, now - s.at);
  if (age >= 1.4) return [s.target, 0];
  const w = 2 * Math.PI / s.response, a = s.damping * w, b = w * Math.sqrt(1 - s.damping * s.damping);
  const d = s.from - s.target, k = (s.velocity + a * d) / b, e = Math.exp(-a * age);
  const tail = 1 - smooth((age - 1.2) / 0.2);
  return [s.target + e * (d * Math.cos(b * age) + k * Math.sin(b * age)) * tail,
    e * ((k * b - a * d) * Math.cos(b * age) + (-d * b - a * k) * Math.sin(b * age)) * tail];
}
export function retarget(s: Spring, value: number, now: number, releasing = false): Spring {
  'worklet';
  const [from, velocity] = springState(s, now);
  return { from, velocity, target: value, at: now, response: releasing ? 0.5 : 0.35, damping: releasing ? 0.5 : 0.68 };
}
export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const sx = width / 393, sy = height / 852, shift = Math.max(0, top - 59) - Math.max(0, bottom - 34);
  return { width, height, sx, sy, shift, cx: width / 2, cy: 372 * sy + shift };
}
