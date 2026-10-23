import { FACE_BITMAPS, FLIP_PLANS, LEGENDS, flipDotDelay } from './keycap-glyphs';
import { KEYCAP_LAYOUT } from './keycap-layout';
import type { LegendFlip, MoodMix } from './keycap-sim';
import { FAN_ORDER, MOOD_RGB, dotPitch, type KeycapLayout } from './keycap-theme';

/** Artistic controls, in linear light unless named otherwise. Geometry comes only from Run A. */
export const LOOK = {
  white: { albedo: 0.82, cobaltU: 0.9, sceneU: 1, waveU: 1.6, lip: 0.01, bloom: KEYCAP_LAYOUT.gains.preview.white.bloom, led: 0.05 },
  glass: { whiteU: 0.25, waveU: 1.4, inner: KEYCAP_LAYOUT.gains.preview.glass.l, lip: 0.08, bloom: 0.04, led: 0.45 },
  mood: { rim: 1, chargeRim: 1, inner: 0.45, chargeInner: 0.12, lip: 0.14, chargeLip: 0.3, burstLip: 0.25, bloom: 0.09 },
  mini: { inner: 0.45, rim: 0.34, bloom: 0.025, selectedBloom: 0.065, glowRadius: 42, pitch: 2 },
  material: { gloss: 0.9, wellDark: 0.0015, wellLED: 0.075, occlusion: 0.42, occlusionWidth: 3, softGradient: 8, wellFeather: 14 },
  flood: { edgeWidth: 6, edgeBrightness: 0.5 },
  page: { radius: 300, centreAlpha: 0.16, middleAlpha: 0.05, middleStop: 0.45, exposure: 0.12, blackTop: 110, topFeather: 18 },
  ring: { radius: 520, width: 60, alpha: 0.22, decay: 1.5 },
  legend: { debossX: -0.5, debossY: 0.67, debossAlpha: 0.32, shadowX: 1.5, shadowY: 2.5, shadowBlur: 2, shadowAlpha: 0.45,
    backlightGain: 1.15, backlightAlpha: 0.6, backlightBlur: 3, minHeight: 0.12, flipMs: 110, fadeMs: 140,
    ink: [0.0033465, 0.0033465, 0.004777], shadow: '#06103A' },
  cobalt: [0.0109601, 0.0251869, 1],
  revealMs: 240,
} as const;
export type SceneValues = { travel: number[]; rim: number[]; dim: number; mood: MoodMix; scene: number[];
  charge: number; burst: number; ring: number; fan: number[]; miniTravel: number[]; selected: number };
export function clamp01(x: number) { 'worklet'; return Math.max(0, Math.min(1, x)); }
export function srgb(x: number) { 'worklet'; return x <= 0.0031308 ? x * 12.92 : 1.055 * Math.pow(Math.max(0, x), 1 / 2.4) - 0.055; }
export function mixedMood(mood: MoodMix) {
  'worklet';
  const a = MOOD_RGB[mood.from]; const b = MOOD_RGB[mood.to]; const p = mood.p;
  return [a[0] + (b[0] - a[0]) * p, a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p];
}
export function miniGeometry(layout: KeycapLayout, index: number, out: number, reduced: boolean) {
  'worklet';
  const cx = layout.width / 2; const cy = layout.keys[3].y + layout.K / 2;
  const amount = reduced ? 1 : out;
  return { x: cx + (layout.minis[index].x - cx) * amount, y: cy + (layout.minis[index].y - cy) * amount,
    scale: layout.u * (reduced ? 1 : 0.6 + 0.4 * clamp01(out)), opacity: reduced ? clamp01(out) : clamp01(out / 0.4) };
}
export function faceRows(mood: number) {
  'worklet';
  const result: number[] = [];
  for (let y = 0; y < 10; y++) {
    let bits = 0;
    for (let x = 0; x < 12; x++) if (FACE_BITMAPS[mood][y][x] === 'X') bits += 2 ** x;
    result.push(bits);
  }
  return result;
}
export function capUniforms(layout: KeycapLayout, key: number, s: SceneValues, mini: number, reduced: boolean, ratio: number) {
  'worklet';
  const small = mini >= 0; const hero = key === 3;
  const sizeRatio = small ? KEYCAP_LAYOUT.miniFootprint / KEYCAP_LAYOUT.footprint : 1;
  const g = small ? miniGeometry(layout, mini, s.fan[mini], reduced) : { x: layout.width / 2, y: layout.keys[key].y + layout.K / 2, scale: layout.u, opacity: 1 };
  const m: MoodMix = small ? { from: FAN_ORDER[mini], to: FAN_ORDER[mini], p: 1, ox: 0, oy: 0, radial: 0 } : s.mood;
  const from = hero ? MOOD_RGB[m.from] : [LOOK.white.albedo, LOOK.white.albedo, LOOK.white.albedo];
  const to = hero ? MOOD_RGB[m.to] : from;
  const upper: number[] = []; const lower: number[] = [];
  for (let c = 0; c < 3; c++) {
    upper.push(key === 0 ? LOOK.cobalt[c] * LOOK.white.cobaltU + s.scene[c] * s.rim[0] * LOOK.white.waveU
      : key === 1 ? LOOK.glass.whiteU + s.scene[c] * s.rim[1] * LOOK.glass.waveU
        : key === 2 ? s.scene[c] * (LOOK.white.sceneU + s.rim[2] * LOOK.white.waveU) : 0);
    lower.push(key === 1 ? LOOK.cobalt[c] * LOOK.glass.inner + s.scene[c] * s.rim[1] * LOOK.glass.waveU : 0);
  }
  const charge = small ? 0 : s.charge; const burst = small ? 1 : s.burst;
  const pitch = Math.round(LOOK.mini.pitch * g.scale * ratio) / ratio / g.scale;
  return {
    centre: [g.x, g.y], scale: g.scale, tileSize: small ? KEYCAP_LAYOUT.miniTile : KEYCAP_LAYOUT.tile,
    pxPerPt: KEYCAP_LAYOUT.pxPerPt, travel: small ? s.miniTravel[mini] : s.travel[key],
    press: [KEYCAP_LAYOUT.press.x * sizeRatio, KEYCAP_LAYOUT.press.y * sizeRatio],
    albedoFrom: from, albedoTo: to, progress: hero ? m.p : 1,
    origin: [m.ox / layout.u, m.oy / layout.u], radial: hero && !small ? m.radial : 0,
    upper, lower, rimGain: hero ? small ? LOOK.mini.rim : LOOK.mood.rim + charge * LOOK.mood.chargeRim : 0,
    innerGain: hero ? small ? LOOK.mini.inner : LOOK.mood.inner * burst - LOOK.mood.chargeInner * charge : 0,
    led: hero ? mixedMood(m) : key === 1 ? LOOK.cobalt.slice() : [LOOK.white.led, LOOK.white.led, LOOK.white.led],
    opacity: g.opacity, dim: hero ? 1 : s.dim, gloss: LOOK.material.gloss,
    well: [LOOK.material.wellDark, LOOK.material.wellLED, LOOK.material.occlusion, LOOK.material.occlusionWidth * sizeRatio],
    softGradient: LOOK.material.softGradient, flood: [LOOK.flood.edgeWidth, LOOK.flood.edgeBrightness],
    mini: small ? 1 : 0, face: [KEYCAP_LAYOUT.face.cx * sizeRatio, KEYCAP_LAYOUT.face.cy * sizeRatio, pitch],
    faceRows: faceRows(m.to), ink: LOOK.legend.ink.slice(),
    deboss: [LOOK.legend.debossX * sizeRatio, LOOK.legend.debossY * sizeRatio, LOOK.legend.debossAlpha],
  };
}
export function sceneUniforms(layout: KeycapLayout, s: SceneValues, reduced: boolean) {
  'worklet';
  const centres: number[][] = []; const colors: number[][] = []; const gains: number[][] = []; const minis: number[][] = [];
  const mood = mixedMood(s.mood);
  for (let i = 0; i < 4; i++) {
    centres.push([layout.width / 2, layout.keys[i].y + layout.K / 2]);
    const color: number[] = [];
    for (let c = 0; c < 3; c++) color.push(i === 3 ? mood[c] : i === 1 ? LOOK.cobalt[c] * LOOK.glass.led : LOOK.white.led + s.scene[c] * s.rim[i]);
    colors.push(color);
    gains.push(i === 3 ? [LOOK.mood.lip + LOOK.mood.chargeLip * s.charge + LOOK.mood.burstLip * (s.burst - 1), LOOK.mood.bloom * s.burst]
      : i === 1 ? [LOOK.glass.lip, LOOK.glass.bloom] : [LOOK.white.lip, LOOK.white.bloom]);
  }
  for (let i = 0; i < 6; i++) {
    const g = miniGeometry(layout, i, s.fan[i], reduced);
    minis.push([g.x, g.y, g.opacity, g.scale]);
  }
  return { u: layout.u, centres, colors, gains, minis, scene: s.scene, burst: s.burst, ring: reduced ? 0 : s.ring,
    miniColors: [MOOD_RGB[5], MOOD_RGB[2], MOOD_RGB[0], MOOD_RGB[3], MOOD_RGB[4], MOOD_RGB[1]],
    wellFeather: LOOK.material.wellFeather, pageExposure: LOOK.page.exposure, selected: s.selected, fanOrder: FAN_ORDER, wellTile: KEYCAP_LAYOUT.wellTile, pxPerPt: KEYCAP_LAYOUT.pxPerPt,
    page: [LOOK.page.radius, LOOK.page.centreAlpha, LOOK.page.middleAlpha, LOOK.page.middleStop],
    top: [LOOK.page.blackTop * layout.u, LOOK.page.topFeather * layout.u],
    ringLook: [LOOK.ring.radius, LOOK.ring.width, LOOK.ring.alpha, LOOK.ring.decay],
    miniGlow: [LOOK.mini.glowRadius, LOOK.mini.bloom, LOOK.mini.selectedBloom] };
}
/** Rects share snapped edges, never inset their width, and are united in a single path. */
export function staticDots(key: number, choice: number, u: number, ratio: number) {
  'worklet';
  const dots = LEGENDS[key][choice]; const pitch = dotPitch(key, u, ratio); const rects: number[][] = [];
  for (let i = 0; i < dots.length; i += 2) rects.push([Math.round((dots[i] - 0.5) * pitch * ratio) / ratio,
    Math.round((dots[i + 1] - 0.5) * pitch * ratio) / ratio, pitch, pitch]);
  return rects;
}
export function legendRects(key: number, legend: LegendFlip, u: number, ratio: number, ox: number, oy: number) {
  'worklet';
  if (legend.t < 0 || legend.mode === 2) return staticDots(key, legend.to, u, ratio);
  const pitch = dotPitch(key, u, ratio); const plan = FLIP_PLANS[key][legend.from][legend.to]; const rects: number[][] = [];
  for (let side = 0; side < 3; side++) {
    const dots = plan[side];
    for (let i = 0; i < dots.length; i += 3) {
      const delay = flipDotDelay(key, dots[i], dots[i + 1], pitch, ox - KEYCAP_LAYOUT.face.cx * u, oy - KEYCAP_LAYOUT.face.cy * u);
      const t = clamp01((legend.t - delay) / LOOK.legend.flipMs);
      if ((side === 1 && t >= 0.5) || (side === 2 && t < 0.5)) continue;
      const h = side === 0 ? 1 : Math.abs(Math.cos(Math.PI * t));
      if (h < LOOK.legend.minHeight) continue;
      const x = Math.round((dots[i] - 0.5) * pitch * ratio) / ratio;
      const y = Math.round((dots[i + 1] - 0.5) * pitch * ratio) / ratio;
      rects.push([x, y + pitch * (1 - h) / 2, pitch, pitch * h]);
    }
  }
  return rects;
}
export function legendTransform(layout: KeycapLayout, key: number, travel: number, ratio: number) {
  'worklet';
  return [{ translateX: Math.round((layout.width / 2 + KEYCAP_LAYOUT.face.cx * layout.u) * ratio) / ratio + KEYCAP_LAYOUT.press.x * layout.u * travel },
    { translateY: Math.round((layout.keys[key].y + layout.K / 2 + KEYCAP_LAYOUT.face.cy * layout.u) * ratio) / ratio + KEYCAP_LAYOUT.press.y * layout.u * travel }];
}
export function legendColor(key: number, rim: number, scene: number[]) {
  'worklet';
  const amount = clamp01(rim); const color: number[] = [];
  for (let c = 0; c < 3; c++) color.push(srgb(clamp01((key === 1 ? 1 : LOOK.legend.ink[c]) * (1 - amount) + scene[c] * LOOK.legend.backlightGain * amount)));
  return color;
}
