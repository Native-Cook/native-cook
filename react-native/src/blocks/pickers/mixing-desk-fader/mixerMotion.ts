// One loop time drives the film: the faders, knobs and mutes are a pure function of t, and the meters are periodic
// signals of t, so t = LOOP gives the same frame as t = 0.
//
//  0.00 – 0.95  rest: the Intro preset (music up), meters moving
//  0.95 – 1.95  a finger rides Music down to 0.40
//  2.35 – 3.25  a finger brings Host up to 0 dB, where it settles exactly
//  3.90         SFX is muted: its button sinks, its light and its meter go out (0.16 s)
//  5.00         the Interview preset is tapped: all four faders and knobs glide there (1.1 s each, 70 ms apart); SFX stays muted
//  7.40         Intro is tapped: everything glides back, SFX comes on again
//  8.71 – 10.0  rest; last frame = first frame. The camera leans in a little (0.6 – 2.4) and back (8.0 – 9.8).
//
// A pose is 12 numbers: faders 0..3 (0..1 of their travel), knobs 4..7 (0..1 of their sweep), mutes 8..11 (0 live, 1 muted).

import {
  AZ_LEAN,
  AZ_REST,
  CAP_Y,
  DECK_ORIGIN,
  DECK_SLOPE,
  DIST,
  EL_LEAN,
  EL_REST,
  FADER_BASE,
  FRAME_H,
  FRAME_W,
  KNOB_SPAN_DEG,
  KNOB_ZERO_DEG,
  LEDS,
  MUTE_POS,
  TARGET,
  TRAVEL,
  ZOOM_LEAN,
} from "./mixerScene";

export const LOOP = 10;
export const POSE = 12;

/** Intro, Interview, Outro: faders, knobs, mutes. */
export const PRESETS: readonly (readonly number[])[] = [
  [0.62, 0.62, 0.82, 0.5, 0.55, 0.5, 0.45, 0.4, 0, 0, 0, 0],
  [0.75, 0.72, 0.24, 0.36, 0.62, 0.6, 0.35, 0.42, 0, 0, 0, 1],
  [0.55, 0.55, 0.88, 0.6, 0.5, 0.5, 0.6, 0.5, 0, 0, 0, 0],
];
/** The film's rides: [channel, to, start, end]. */
export const RIDES = [
  [2, 0.4, 0.95, 1.95],
  [0, 0.75, 2.35, 3.25],
] as const;
/** The film's mute: channel and time. */
export const MUTE_TAP = [3, 3.9] as const;
/** The film's preset recalls: [preset, time]. */
export const RECALLS = [
  [1, 5.0],
  [0, 7.4],
] as const;
/** A recall: each fader glides this long (s), this much after the one on its left. A mute moves in MUTE_MOVE. */
export const GLIDE = 1.1;
export const STAGGER = 0.07;
export const MUTE_MOVE = 0.16;
/** The camera's lean. */
export const LEAN = [0.6, 2.4, 8.0, 9.8] as const;

/** The fader law: travel (0..1) to dB, piecewise linear; 0 dB sits at SNAP_V. */
export const V_TAB = [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1] as const;
export const DB_TAB = [-60, -40, -30, -20, -10, -5, 0, 5, 10] as const;
export const SNAP_V = 0.75;
export const SNAP_ZONE = 0.03;
/** How far a fader can be pulled past an end (of its travel) before it stops. */
export const OVERPULL = 0.035;

/** The meters: a channel's signal offset (dB), and the dB the meter's bottom and top stand for. */
export const SIGNAL_DB = [0, -2, -6, -4] as const;
export const METER_FLOOR = -42;
export const METER_SPAN = 48;
/** SFX hits (s) and how fast they decay (s). */
export const HITS = [1.4, 4.6, 6.9, 8.3] as const;
export const HIT_DECAY = 0.35;

// ---------------------------------------------------------------- helpers (declared before their callers)

export function clamp01(x: number): number {
  "worklet";
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}

export function smooth(x: number): number {
  "worklet";
  return x * x * (3 - 2 * x);
}

export function easeInOut(x: number): number {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

// ---------------------------------------------------------------- the film

/** The film's pose at t into out (12 numbers). */
export function filmPose(t: number, out: number[]): void {
  "worklet";
  const start = PRESETS[0];
  for (let i = 0; i < POSE; i++) out[i] = start[i];
  for (let r = 0; r < RIDES.length; r++) {
    const c = RIDES[r][0];
    out[c] = lerp(out[c], RIDES[r][1], easeInOut(seg(t, RIDES[r][2], RIDES[r][3])));
  }
  const mc = MUTE_TAP[0];
  out[8 + mc] = lerp(out[8 + mc], 1, smooth(seg(t, MUTE_TAP[1], MUTE_TAP[1] + MUTE_MOVE)));
  for (let r = 0; r < RECALLS.length; r++) {
    const p = PRESETS[RECALLS[r][0]];
    const at = RECALLS[r][1];
    for (let c = 0; c < 4; c++) {
      const k = easeInOut(seg(t, at + c * STAGGER, at + c * STAGGER + GLIDE));
      out[c] = lerp(out[c], p[c], k);
      out[4 + c] = lerp(out[4 + c], p[4 + c], k);
      out[8 + c] = lerp(out[8 + c], p[8 + c], smooth(seg(t, at + 0.1, at + 0.1 + MUTE_MOVE)));
    }
  }
}

/** The preset the film's segmented control shows. */
export function filmPreset(t: number): number {
  "worklet";
  let p = 0;
  for (let r = 0; r < RECALLS.length; r++) {
    if (t >= RECALLS[r][1]) p = RECALLS[r][0];
  }
  return p;
}

/** The film's finger on the desk: out = [opacity, pressed, channel, kind (0 fader, 1 mute)]. */
export function deskGhost(t: number, out: number[]): void {
  "worklet";
  out[0] = 0;
  out[1] = 0;
  out[2] = 0;
  out[3] = 0;
  for (let r = 0; r < RIDES.length; r++) {
    const a = RIDES[r][2];
    const b = RIDES[r][3];
    if (t < a - 0.35 || t > b + 0.35) continue;
    out[0] = seg(t, a - 0.32, a - 0.08) * (1 - seg(t, b + 0.1, b + 0.32));
    out[1] = seg(t, a - 0.08, a) * (1 - seg(t, b, b + 0.12));
    out[2] = RIDES[r][0];
    return;
  }
  const at = MUTE_TAP[1];
  if (t > at - 0.35 && t < at + 0.35) {
    out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32));
    out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18));
    out[2] = MUTE_TAP[0];
    out[3] = 1;
  }
}

/** The film's finger on the presets: out = [opacity, pressed, preset]. */
export function presetGhost(t: number, out: number[]): void {
  "worklet";
  let best = 0;
  let dist = 1e9;
  for (let r = 0; r < RECALLS.length; r++) {
    const d = Math.abs(t - RECALLS[r][1]);
    if (d < dist) {
      dist = d;
      best = r;
    }
  }
  const at = RECALLS[best][1];
  out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32));
  out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18));
  out[2] = RECALLS[best][0];
}

// ---------------------------------------------------------------- sound

/** A fader's travel to dB. */
export function faderDb(v: number): number {
  "worklet";
  if (v <= 0) return DB_TAB[0];
  for (let i = 0; i < V_TAB.length - 1; i++) {
    if (v <= V_TAB[i + 1]) return lerp(DB_TAB[i], DB_TAB[i + 1], (v - V_TAB[i]) / (V_TAB[i + 1] - V_TAB[i]));
  }
  return DB_TAB[DB_TAB.length - 1];
}

/** What the panel says for a channel. */
export function dbLabel(v: number, muted: number): string {
  "worklet";
  if (muted > 0.5) return "Muted";
  if (v < 0.01) return "−∞ dB";
  const db = faderDb(v);
  const r = Math.round(db * 10) / 10;
  if (r === 0) return "0.0 dB";
  return `${r > 0 ? "+" : "−"}${Math.abs(r).toFixed(1)} dB`;
}

/** Channel c's signal (0..1) at signal time s: two people taking turns, music on the beat, sound effects as hits. */
export function signalAt(c: number, s: number): number {
  "worklet";
  const w = (2 * Math.PI) / LOOP;
  if (c <= 1) {
    const turn = 0.5 + 0.5 * Math.sin(w * 2 * s);
    const mine = c === 0 ? turn : 1 - turn;
    const syll = 0.5 + 0.3 * Math.sin(w * (37 + 6 * c) * s + c * 1.7) + 0.2 * Math.sin(w * (61 + 4 * c) * s + c * 0.6);
    return (0.25 + 0.75 * mine) * syll;
  }
  if (c === 2) {
    const beat = 0.5 + 0.5 * Math.cos(w * 20 * s);
    return 0.55 + 0.33 * beat * beat * beat * beat + 0.1 * Math.sin(w * 7 * s);
  }
  let e = 0.08;
  for (let i = 0; i < HITS.length; i++) {
    const d = (s - HITS[i] + LOOP) % LOOP;
    e = Math.max(e, Math.exp(-d / HIT_DECAY));
  }
  return e;
}

/** Channel c's meter (0..1 of its LEDs) for its fader, knob and mute. */
export function meterAt(c: number, s: number, fader: number, knob: number, mute: number): number {
  "worklet";
  const db = -40 + 40 * signalAt(c, s) + SIGNAL_DB[c] + faderDb(fader) + (knob - 0.5) * 12;
  return clamp01((db - METER_FLOOR) / METER_SPAN) * (1 - mute);
}

/** LED i of a meter, 0..1 (the top one partly lit as the level crosses it). */
export function ledAt(level: number, i: number): number {
  "worklet";
  return clamp01(level * LEDS - i);
}

/** A knob's turn (rad, about the deck's up axis) for its value. */
export function knobRad(k: number): number {
  "worklet";
  return ((KNOB_ZERO_DEG - k * KNOB_SPAN_DEG) * Math.PI) / 180;
}

// ---------------------------------------------------------------- a finger on a fader

/** The 0 dB detent: inside SNAP_ZONE the cap is drawn towards SNAP_V. */
export function magnet(v: number): number {
  "worklet";
  const d = v - SNAP_V;
  const m = Math.abs(d);
  if (m >= SNAP_ZONE) return v;
  return SNAP_V + d * (m / SNAP_ZONE) * (m / SNAP_ZONE);
}

/** A dragged fader's value: rubber-banded past either end, then the detent. */
export function faderFromDrag(raw: number): number {
  "worklet";
  if (raw > 1) {
    const o = raw - 1;
    return 1 + (o * OVERPULL) / (o + OVERPULL);
  }
  if (raw < 0) {
    const o = -raw;
    return -(o * OVERPULL) / (o + OVERPULL);
  }
  return magnet(raw);
}

// ---------------------------------------------------------------- camera and projection

/** How far the camera leans in, 0..1. */
export function leanAt(t: number): number {
  "worklet";
  return smooth(seg(t, LEAN[0], LEAN[1])) * (1 - smooth(seg(t, LEAN[2], LEAN[3])));
}

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera's eye for a lean k: out = [x, y, z]. It looks at TARGET. */
export function cameraAt(k: number, out: number[]): void {
  "worklet";
  const az = (lerp(AZ_REST, AZ_LEAN, k) * Math.PI) / 180;
  const el = (lerp(EL_REST, EL_LEAN, k) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM_LEAN * k);
  out[0] = TARGET[0] + d * Math.cos(el) * Math.sin(az);
  out[1] = TARGET[1] + d * Math.sin(el);
  out[2] = TARGET[2] + d * Math.cos(el) * Math.cos(az);
}

/** A point in the deck's frame to world space: out = [x, y, z]. */
export function deckToWorld(x: number, y: number, z: number, out: number[]): void {
  "worklet";
  const c = Math.cos(DECK_SLOPE);
  const s = Math.sin(DECK_SLOPE);
  out[0] = DECK_ORIGIN[0] + x;
  out[1] = DECK_ORIGIN[1] + y * c - z * s;
  out[2] = DECK_ORIGIN[2] + y * s + z * c;
}

/** A world point to stage pixels for a lean k: out = [x, y]. */
export function project(x: number, y: number, z: number, k: number, width: number, height: number, out: number[]): void {
  "worklet";
  const eye = [0, 0, 0];
  cameraAt(k, eye);
  let fx = TARGET[0] - eye[0];
  let fy = TARGET[1] - eye[1];
  let fz = TARGET[2] - eye[2];
  const fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
  fx /= fl;
  fy /= fl;
  fz /= fl;
  let rx = -fz;
  let rz = fx;
  const rl = Math.sqrt(rx * rx + rz * rz);
  rx /= rl;
  rz /= rl;
  const ux = -rz * fy;
  const uy = rz * fx - rx * fz;
  const uz = rx * fy;
  const px = x - eye[0];
  const py = y - eye[1];
  const pz = z - eye[2];
  const depth = px * fx + py * fy + pz * fz;
  const aspect = width / height;
  const tanY = tanHalfFovY(aspect);
  const ndcX = (px * rx + pz * rz) / (depth * tanY * aspect);
  const ndcY = (px * ux + py * uy + pz * uz) / (depth * tanY);
  out[0] = (ndcX * 0.5 + 0.5) * width;
  out[1] = (0.5 - ndcY * 0.5) * height;
}

/** Fader c's grip on screen at travel v: out = [x, y]. */
export function capOnScreen(c: number, v: number, k: number, width: number, height: number, out: number[]): void {
  "worklet";
  const b = FADER_BASE[c];
  const w = [0, 0, 0];
  deckToWorld(b[0], CAP_Y, b[2] - v * TRAVEL, w);
  project(w[0], w[1], w[2], k, width, height, out);
}

/** Mute button c on screen: out = [x, y]. */
export function muteOnScreen(c: number, k: number, width: number, height: number, out: number[]): void {
  "worklet";
  const m = MUTE_POS[c];
  const w = [0, 0, 0];
  deckToWorld(m[0], 0.005, m[2], w);
  project(w[0], w[1], w[2], k, width, height, out);
}
