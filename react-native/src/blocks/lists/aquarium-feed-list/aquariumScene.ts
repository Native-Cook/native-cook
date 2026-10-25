/*
 Aquarium Feed List — the loop and the fish, as pure functions of time.

 Six fish cruise on closed Lissajous paths whose frequencies are whole multiples of the loop, so
 every frame at t = LOOP equals the frame at 0. Two values stir them:
 - `stir` (0–1): how hard the water is being churned by scrolling. Fish scatter outward and away
   from the scroll direction, tails beat wider, bubbles stream up.
 - `feed` (0–1): a card was tapped; fish leave their paths and circle it.
 Under autoPlay both come from the script; under touch from the scroll's speed and the last tap.
*/
import { Skia, type SkPath } from '@shopify/react-native-skia';

export const LOOP = 9;
/** Card rows in the list: a 104 pt card and a 12 pt gap. */
export const CARD_H = 104;
export const CARD_GAP = 12;
export const ROW = CARD_H + CARD_GAP;
/** The scripted scroll: down to SCROLL_TO over 1.4–4.4 s, back to 0 over 6.6–8.2 s. */
export const SCROLL_TO = 420;
/** The card the loop taps at 5.8 s. */
export const TAP_CARD = 4;
export const TAP_AT = 5.8;

export type FishSpec = {
  /** Body length and depth (pt). */
  len: number;
  dep: number;
  colors: [string, string, string];
  fin: string;
  /** White bands (as fractions of the body from tail to nose), drawn over the body. */
  bands: number[];
  /** Path centre and reach as fractions of the swimming area; whole-number frequencies. */
  cx: number;
  cy: number;
  ax: number;
  ay: number;
  kx: number;
  ky: number;
  phase: number;
  /** Tail beats per loop (whole). */
  beats: number;
  /** Depth: far fish are smaller and hazier. */
  near: number;
};

export const FISH: FishSpec[] = [
  { len: 75, dep: 44, colors: ['#E39A1F', '#F6C33F', '#FCE59A'], fin: '#F0B02E', bands: [], cx: 0.5, cy: 0.3, ax: 0.36, ay: 0.12, kx: 1, ky: 2, phase: 0.2, beats: 24, near: 1 },
  { len: 49, dep: 22, colors: ['#1B4FB0', '#2F86E4', '#9ED4FF'], fin: '#3C93EA', bands: [], cx: 0.42, cy: 0.62, ax: 0.32, ay: 0.16, kx: 1, ky: 3, phase: 2.1, beats: 33, near: 0.8 },
  { len: 58, dep: 29, colors: ['#D24A15', '#FF7A33', '#FFC08F'], fin: '#F26B26', bands: [0.32, 0.66], cx: 0.6, cy: 0.48, ax: 0.3, ay: 0.14, kx: -1, ky: 2, phase: 4.0, beats: 30, near: 0.92 },
  { len: 70, dep: 25, colors: ['#5A36B8', '#9A63E6', '#E6C6FF'], fin: '#B07CF0', bands: [], cx: 0.46, cy: 0.82, ax: 0.38, ay: 0.08, kx: 1, ky: 2, phase: 5.2, beats: 27, near: 0.95 },
  { len: 38, dep: 16, colors: ['#5E9A2E', '#9CCF55', '#E3F5B4'], fin: '#8DC04A', bands: [], cx: 0.3, cy: 0.18, ax: 0.22, ay: 0.08, kx: -2, ky: 3, phase: 1.1, beats: 36, near: 0.66 },
  { len: 44, dep: 16, colors: ['#6F8897', '#B9CCD7', '#F1F7FA'], fin: '#A6BCC9', bands: [], cx: 0.68, cy: 0.72, ax: 0.26, ay: 0.12, kx: 2, ky: -3, phase: 3.3, beats: 36, near: 0.72 },
];

export const ENTRIES: { title: string; detail: string; when: string; tag: string; ios: string; android: string; tint: string }[] = [
  { title: 'Fed', detail: '2 cubes mysis, 1 pinch of pellets', when: '08:12', tag: 'Feed', ios: 'fish.fill', android: 'set_meal', tint: '#F6C33F' },
  { title: 'Water test', detail: 'pH 8.2 · dKH 8.4 · NO₃ 5 ppm', when: '07:40', tag: 'Test', ios: 'testtube.2', android: 'science', tint: '#7FD6E8' },
  { title: 'Frag added', detail: 'Green star polyp, left rock', when: 'Yesterday', tag: 'Coral', ios: 'leaf.fill', android: 'eco', tint: '#9CCF55' },
  { title: 'Water change', detail: '15 % · 33 L fresh saltwater', when: 'Yesterday', tag: 'Care', ios: 'drop.fill', android: 'water_drop', tint: '#5FB2F0' },
  { title: 'Glass scraped', detail: 'Front pane, 4 minutes', when: 'Tue', tag: 'Care', ios: 'paintbrush.pointed.fill', android: 'cleaning_services', tint: '#C9A7F5' },
  { title: 'Light schedule', detail: 'Sunrise 09:00 · 70 % blue', when: 'Tue', tag: 'Lights', ios: 'sun.horizon.fill', android: 'wb_twilight', tint: '#FFB36B' },
  { title: 'Fed', detail: 'Nori clip for the tang', when: 'Mon', tag: 'Feed', ios: 'fish.fill', android: 'set_meal', tint: '#F6C33F' },
  { title: 'Temperature dip', detail: '25.1 °C overnight, heater checked', when: 'Mon', tag: 'Alert', ios: 'thermometer.medium', android: 'thermostat', tint: '#FF8A73' },
  { title: 'Skimmer cleaned', detail: 'Collection cup, two days early', when: 'Sun', tag: 'Care', ios: 'wind', android: 'air', tint: '#9FD0DA' },
  { title: 'Snail count', detail: '11 nerites, all accounted for', when: 'Sat', tag: 'Census', ios: 'checklist', android: 'checklist', tint: '#E8D29A' },
  { title: 'Top-off', detail: '1.2 L RO water', when: 'Sat', tag: 'Care', ios: 'drop.fill', android: 'water_drop', tint: '#5FB2F0' },
  { title: 'New arrival', detail: 'Lavender wrasse, quarantine 3 weeks', when: 'Fri', tag: 'Fish', ios: 'fish.fill', android: 'set_meal', tint: '#B07CF0' },
];

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function easeInOutCubic(v: number) {
  'worklet';
  const x = clamp01(v);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(v: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(v), 3);
}

/** The list's scroll offset in the loop. */
export function autoScroll(t: number) {
  'worklet';
  return SCROLL_TO * (easeInOutCubic((t - 1.4) / 3.0) - easeInOutCubic((t - 6.6) / 1.6));
}

/** The loop's stir: a long churn for the first scroll, a shorter one for the way back. Direction +1 = content moving up. */
export function autoStir(t: number) {
  'worklet';
  const a = easeOutCubic((t - 1.5) / 0.7) * (1 - easeInOutCubic((t - 4.0) / 1.5));
  const b = 0.75 * easeOutCubic((t - 6.7) / 0.6) * (1 - easeInOutCubic((t - 7.8) / 0.9));
  return { stir: Math.max(a, b), dir: a >= b ? 1 : -1 };
}

/** The loop's feeding: fish gather round the tapped card from 5.8 s and leave as the list scrolls back. */
export function autoFeed(t: number) {
  'worklet';
  return easeInOutCubic((t - TAP_AT) / 0.8) * (1 - easeInOutCubic((t - 6.7) / 0.9));
}

export type Area = { x: number; y: number; w: number; h: number };

/**
 Fish i's centre at fish-time `tau`, stirred by `stir` in direction `dir`, drawn to (tx, ty) by
 `feed`. Writes [x, y] into out.
*/
export function fishAt(i: number, tau: number, area: Area, stir: number, dir: number, feed: number, tx: number, ty: number, out: number[]) {
  'worklet';
  const f = FISH[i];
  const w = (2 * Math.PI * tau) / LOOP;
  let x = area.x + area.w * (f.cx + f.ax * Math.sin(f.kx * w + f.phase));
  let y = area.y + area.h * (f.cy + f.ay * Math.sin(f.ky * w + f.phase * 1.7));
  // Scatter: away from the middle of the tank and against the scroll.
  const mx = x - (area.x + area.w / 2);
  const my = y - (area.y + area.h / 2);
  const d = Math.sqrt(mx * mx + my * my) + 30;
  x += stir * (mx / d) * 70;
  y += stir * ((my / d) * 50 - dir * 60);
  // Feeding: each fish takes its own place on a slow ring round the card (two laps a loop).
  const ang = f.phase * 2.3 + 2 * w * (i % 2 === 0 ? 1 : -1);
  const gx = tx + Math.cos(ang) * (110 + 18 * (i % 3));
  const gy = ty + Math.sin(ang) * (46 + 8 * (i % 2));
  out[0] = x + (gx - x) * feed;
  out[1] = y + (gy - y) * feed;
}

/**
 A fish's body in its own frame (nose toward +x, centred), its tail and fins swinging with `phase`;
 `amp` is the swing as a share of the body's depth.
*/
export function fishBody(f: FishSpec, phase: number, amp: number): SkPath {
  'worklet';
  const len = f.len;
  const h = f.dep;
  const n = 14;
  const b = Skia.PathBuilder.Make();
  const top: number[] = [];
  const bot: number[] = [];
  const xs: number[] = [];
  for (let k = 0; k <= n; k++) {
    const u = k / n;
    const x = -len / 2 + u * len;
    const bend = amp * h * Math.sin(phase - u * 4.5) * (1 - u) * (1 - u);
    const w = u < 0.6 ? (h / 2) * (0.16 + 0.84 * Math.sin((Math.PI / 2) * (u / 0.6))) : (h / 2) * Math.sqrt(Math.max(0, 1 - ((u - 0.6) / 0.4) * ((u - 0.6) / 0.4)));
    xs.push(x);
    top.push(bend - w);
    bot.push(bend + w);
  }
  b.moveTo(xs[0], top[0]);
  for (let k = 1; k <= n; k++) b.lineTo(xs[k], top[k]);
  for (let k = n; k >= 0; k--) b.lineTo(xs[k], bot[k]);
  b.close();
  // Tail: a forked fan that trails the body's swing.
  const tx = xs[0];
  const ty = (top[0] + bot[0]) / 2;
  const swing = amp * h * 1.1 * Math.sin(phase + 0.9);
  b.moveTo(tx + 2, ty - h * 0.08);
  b.quadTo(tx - len * 0.18, ty + swing * 0.6 - h * 0.2, tx - len * 0.3, ty + swing - h * 0.42);
  b.quadTo(tx - len * 0.2, ty + swing * 0.85, tx - len * 0.3, ty + swing + h * 0.42);
  b.quadTo(tx - len * 0.18, ty + swing * 0.6 + h * 0.2, tx + 2, ty + h * 0.08);
  b.close();
  // Dorsal fin: a soft sail over the back.
  const a = 5;
  const c = 10;
  b.moveTo(xs[a], top[a] + 1);
  b.quadTo(xs[a + 1], top[a + 1] - h * 0.42, xs[c], top[c] + 1);
  b.close();
  return b.build();
}

/** The white bands of a banded fish, following the same swing. */
export function fishBands(f: FishSpec, phase: number, amp: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const len = f.len;
  const h = f.dep;
  for (let j = 0; j < f.bands.length; j++) {
    const u0 = f.bands[j];
    const pts: number[] = [];
    for (let k = 0; k <= 3; k++) {
      const u = u0 + (k / 3) * 0.09;
      const bend = amp * h * Math.sin(phase - u * 4.5) * (1 - u) * (1 - u);
      const w = u < 0.6 ? (h / 2) * (0.16 + 0.84 * Math.sin((Math.PI / 2) * (u / 0.6))) : (h / 2) * Math.sqrt(Math.max(0, 1 - ((u - 0.6) / 0.4) * ((u - 0.6) / 0.4)));
      pts.push(-len / 2 + u * len, bend - w * 0.98, bend + w * 0.98);
    }
    b.moveTo(pts[0], pts[1]);
    for (let k = 1; k <= 3; k++) b.lineTo(pts[k * 3], pts[k * 3 + 1]);
    for (let k = 3; k >= 0; k--) b.lineTo(pts[k * 3], pts[k * 3 + 2]);
    b.close();
  }
  return b.build();
}

/** Seeded 0–1. */
export function hash(n: number) {
  'worklet';
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

/** Bubbles: rings and their glints, a steady thin stream from the airstone plus many more while stirred. */
export function bubblePaths(t: number, stir: number, W: number, top: number, bottom: number) {
  'worklet';
  const rings = Skia.PathBuilder.Make();
  const glints = Skia.PathBuilder.Make();
  const n = 54;
  const span = bottom - top;
  for (let j = 0; j < n; j++) {
    // Periods 2.25, 3 or 4.5 s: whole divisions of the loop.
    const period = j % 3 === 0 ? 2.25 : j % 3 === 1 ? 3 : 4.5;
    const life = (t / period + hash(j * 1.7)) % 1;
    const air = j < 10;
    const on = air ? 0.85 : clamp01(stir * 1.6 - hash(j * 3.1) * 0.6);
    if (on <= 0.02) continue;
    const r = (air ? 2 + hash(j) * 2.2 : 2 + hash(j * 5.3) * 4) * on * Math.min(1, life * 6, (1 - life) * 8);
    if (r < 0.4) continue;
    const x0 = air ? W - 34 + hash(j * 2.9) * 10 : 24 + hash(j * 4.4) * (W - 48);
    const x = x0 + Math.sin(2 * Math.PI * (life * 3 + hash(j * 6.1))) * (3 + r);
    const y = bottom - life * span * (air ? 1 : 0.85);
    rings.addCircle(x, y, r);
    glints.addCircle(x - r * 0.35, y - r * 0.35, r * 0.28);
  }
  return { rings: rings.build(), glints: glints.build() };
}
