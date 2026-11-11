/** Timing and touch contract ported from GlassVoiceCapsule.swift. All times are seconds. */
export const LOOP = 9;

export type Frame = {
  time: number; amount: number; scale: number; content: number; droplet: number;
  level: number; seconds: number; digitAge: number; timerScale: number;
  floatY: number; angle: number; micPulse: number; micRed: number;
  listening: number; saved: number; sheen: number; recording: boolean; digitRise: boolean;
};
export type Interaction = { source: Frame; at: number; recording: boolean; recordAt: number; stoppedAt: number | null };
export type Drag = { x: number; y: number; targetX: number; targetY: number; vx: number; vy: number; at: number; releasing: boolean };
export type Motion = { epoch: number; manual: Interaction | null; drag: Drag; touching: boolean; startX: number; startY: number; idleAt: number; resetAt: number };

export function clamp(x: number) { 'worklet'; return Math.min(1, Math.max(0, x)); }
export function out(x: number) { 'worklet'; return 1 - Math.pow(1 - clamp(x), 3); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function spring(age: number, response: number, damping: number, duration: number) {
  'worklet';
  if (age <= 0) return 0;
  if (age >= duration) return 1;
  const w = 2 * Math.PI / response, wd = w * Math.sqrt(1 - damping * damping);
  const value = 1 - Math.exp(-damping * w * age) * (Math.cos(wd * age) + damping * w / wd * Math.sin(wd * age));
  return value + (1 - value) * smooth((age - duration + 0.16) / 0.16);
}
export function voice(seconds: number) {
  'worklet';
  return clamp(0.46 + 0.30 * Math.sin(2 * Math.PI * 1.5 * seconds) + 0.24 * Math.sin(2 * Math.PI * 3.5 * seconds + 1.1));
}
function filteredVoice(seconds: number) {
  'worklet';
  const dt = 0.54 / 16;
  let sum = 0, weight = 0;
  for (let i = 0; i < 16; i++) {
    const age = (i + 0.5) * dt, w = Math.exp(-age / 0.09);
    sum += voice(Math.max(0, seconds - age)) * w; weight += w;
  }
  return sum / weight;
}
export function emptyFrame(): Frame {
  'worklet';
  return { time: 0, amount: 0, scale: 1, content: 0, droplet: 0.2, level: 0.18, seconds: 0,
    digitAge: 1, timerScale: 0.86, floatY: 0, angle: 0, micPulse: 1, micRed: 0,
    listening: 0, saved: 0, sheen: -1, recording: false, digitRise: true };
}
export function autoFrame(time: number): Frame {
  'worklet';
  const t = time % LOOP, f = emptyFrame(); f.time = t;
  const start = spring(t - 0.92, 0.55, 0.62, 0.98), stop = spring(t - 6.82, 0.55, 0.62, 0.98);
  const raw = start * (1 - stop);
  f.amount = raw < 0 ? raw / (1 + Math.abs(raw) * 14) : raw;
  const r = clamp(f.amount);
  f.scale = t < 0.92 ? 1 - 0.07 * out((t - 0.8) / 0.12) : 0.93 + 0.07 * start;
  f.content = out((t - 1.15) / 0.45) * (1 - Math.pow(clamp((t - 6.82) / 0.38), 3));
  f.timerScale = (0.86 + 0.14 * out((t - 1.15) / 0.45)) * (1 - 0.7 * out((t - 6.82) / 0.38));
  f.droplet = (0.2 + 0.8 * spring(t - 1.2, 0.45, 0.58, 0.8)) * (1 - 0.12 * out((t - 6.7) / 0.12)) * (1 - 0.7 * out((t - 6.82) / 0.38));
  f.seconds = Math.min(5, Math.max(0, t - 1.7)); f.digitAge = Math.max(0, t - 1.7 - Math.floor(f.seconds));
  if (t >= 1.7 && t < 6.82) f.level = r * voice(f.seconds);
  else if (t >= 6.82) { const peak = voice(5); f.level = peak + (0.18 - peak) * out((t - 6.82) / 0.78); }
  f.micRed = out((t - 0.92) / 0.3) * (1 - out((t - 6.82) / 0.58));
  f.recording = t >= 0.92 && t < 6.82;
  f.listening = out((t - 0.92) / 0.25) * (1 - out((t - 6.82) / 0.25));
  f.saved = out((t - 6.82) / 0.25) * (1 - out((t - 8.02) / 0.25));
  if (t >= 1.7 && t < 6.7) {
    const phase = 2 * Math.PI * (t - 1.7) / 5;
    const envelope = smooth((t - 1.7) / 0.25) * (1 - smooth((t - 6.45) / 0.25));
    f.floatY = -6 * Math.sin(2 * phase) * r * envelope;
    f.angle = 1.6 * Math.sin(phase + 0.6) * r * envelope;
    f.micPulse = 1 + 0.08 * Math.pow(Math.sin(Math.PI * (t - 1.7)), 2) * envelope;
  }
  if (t > 7.9 && t < 8.7) f.sheen = smooth((t - 7.9) / 0.8);
  return f;
}
export function interactionFrame(s: Interaction, now: number, reduced: boolean): Frame {
  'worklet';
  const age = Math.max(0, now - s.at), source = s.source;
  const p = reduced ? smooth(age / 0.3) : spring(age, 0.55, 0.62, 0.98);
  const q = out(age / 0.38), target = s.recording ? 1 : 0, f = { ...source };
  const raw = source.amount + (target - source.amount) * p;
  const lower = Math.min(0, source.amount), over = raw - lower;
  f.amount = over < 0 ? lower + over / (1 + Math.abs(over) * 14) : raw;
  const r = clamp(f.amount);
  f.time = source.time + age; f.scale = source.scale + (1 - source.scale) * p;
  f.content = source.content + (target - source.content) * q;
  f.timerScale = source.timerScale + ((s.recording ? 1 : 0.3) - source.timerScale) * q;
  const d = reduced ? q : spring(age, 0.45, 0.58, 0.8);
  f.droplet = source.droplet + ((s.recording ? 1 : 0.3) - source.droplet) * d;
  f.seconds = Math.max(0, (s.stoppedAt ?? now) - s.recordAt);
  f.digitAge = Math.floor(f.seconds) === Math.floor(source.seconds) ? source.digitAge + age : Math.max(0, now - s.recordAt - Math.floor(f.seconds));
  const level = s.recording ? (reduced ? 0.55 : filteredVoice(f.seconds)) : 0.18;
  f.level = source.level + (level - source.level) * smooth(age / (reduced ? 0.4 : 0.09));
  f.micRed = source.micRed + (target - source.micRed) * out(age / 0.3);
  f.listening = source.listening + (target - source.listening) * out(age / 0.25);
  const saved = s.stoppedAt === null ? 0 : 1 - out((now - s.stoppedAt - 1.2) / 0.25);
  f.saved = source.saved + (saved - source.saved) * out(age / 0.25);
  const phase = 2 * Math.PI * f.seconds / 5;
  const floatY = s.recording && !reduced ? -6 * Math.sin(2 * phase) * r : 0;
  const angle = s.recording && !reduced ? 1.6 * Math.sin(phase + 0.6) * r : 0;
  f.floatY = source.floatY + (floatY - source.floatY) * smooth(age / 0.3);
  f.angle = source.angle + (angle - source.angle) * smooth(age / 0.3);
  f.micPulse = reduced ? 1 : 1 + 0.08 * Math.pow(Math.sin(Math.PI * f.seconds), 2) * r;
  f.recording = s.recording; f.sheen = -1; f.digitRise = !reduced;
  return f;
}
export function initialMotion(now = 0): Motion {
  'worklet';
  return { epoch: now, manual: null, drag: { x: 0, y: 0, targetX: 0, targetY: 0, vx: 0, vy: 0, at: 0, releasing: false }, touching: false, startX: 0, startY: 0, idleAt: -1, resetAt: -1 };
}
export function frameAt(s: Motion, now: number, playing: boolean, reduced: boolean): Frame {
  'worklet';
  return s.manual ? interactionFrame(s.manual, now, reduced) : autoFrame(playing ? Math.max(0, now - s.epoch) : 0);
}
export function dragState(s: Drag, now: number) {
  'worklet';
  const t = Math.max(0, now - s.at);
  if (t >= 1.2) return { x: s.targetX, y: s.targetY, vx: 0, vy: 0 };
  const w = 2 * Math.PI / (s.releasing ? 0.5 : 0.38), z = s.releasing ? 0.55 : 0.6;
  const a = z * w, b = w * Math.sqrt(1 - z * z), e = Math.exp(-a * t);
  const dx = s.x - s.targetX, dy = s.y - s.targetY, kx = (s.vx + a * dx) / b, ky = (s.vy + a * dy) / b;
  return { x: s.targetX + e * (dx * Math.cos(b * t) + kx * Math.sin(b * t)),
    y: s.targetY + e * (dy * Math.cos(b * t) + ky * Math.sin(b * t)),
    vx: e * ((kx * b - a * dx) * Math.cos(b * t) + (-dx * b - a * kx) * Math.sin(b * t)),
    vy: e * ((ky * b - a * dy) * Math.cos(b * t) + (-dy * b - a * ky) * Math.sin(b * t)) };
}
export function retarget(s: Drag, now: number, x: number, y: number, releasing: boolean): Drag {
  'worklet';
  return { ...dragState(s, now), targetX: x, targetY: y, at: now, releasing };
}
export function takeOver(s: Motion, now: number, playing: boolean, reduced: boolean): Motion {
  'worklet';
  if (s.manual) return { ...s, idleAt: -1, resetAt: -1 };
  const f = frameAt(s, now, playing, reduced);
  return { ...s, manual: { source: f, at: now, recording: f.recording, recordAt: now - f.seconds, stoppedAt: f.saved > 0 ? now : null }, idleAt: -1, resetAt: -1 };
}
export function toggle(s: Motion, now: number, playing: boolean, reduced: boolean): Motion {
  'worklet';
  const next = takeOver(s, now, playing, reduced), source = frameAt(next, now, playing, reduced);
  const recording = !next.manual!.recording;
  return { ...next, manual: { source, at: now, recording, recordAt: recording ? now : next.manual!.recordAt, stoppedAt: recording ? null : now }, idleAt: now + 2 };
}
export function advance(s: Motion, now: number, reduced: boolean): Motion {
  'worklet';
  if (s.touching || !s.manual) return s;
  if (s.resetAt >= 0 && now >= s.resetAt) return { ...initialMotion(s.resetAt), drag: s.drag };
  if (s.idleAt >= 0 && now >= s.idleAt) {
    const f = interactionFrame(s.manual, s.idleAt, reduced);
    return { ...s, manual: { source: f, at: s.idleAt, recording: false, recordAt: s.manual.recordAt,
      stoppedAt: s.manual.stoppedAt ?? (f.recording ? s.idleAt : null) }, resetAt: s.idleAt + 1.45, idleAt: -1 };
  }
  return s;
}
export function geometry(f: Frame, drag: Drag, now: number, w: number, h: number, reduced: boolean) {
  'worklet';
  const offset = reduced ? { x: 0, y: 0 } : dragState(drag, now);
  return { width: 84 + 238 * f.amount, height: 84 + 12 * f.amount + 5 * f.level * clamp(f.amount),
    cx: w / 2 + offset.x, cy: h * 488 / 852 + f.floatY + offset.y,
    angle: reduced ? 0 : f.angle + Math.min(8, Math.max(-8, offset.x / 14)) };
}
