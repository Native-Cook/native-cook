// Seconds-based choreography. Every helper precedes its callers; no nested worklet callbacks.
import type { SheetLayout } from './sheet-theme';

export const SHEET_PERIOD = 16.4;
export const STEP_DURATION = 1.15;
export const CLOSE_DURATION = 0.46;
export type SheetFrame = {
  position: number; open: number; contents: number; stage: number; drag: number; edge: number;
  labelOpen: number; labelClosed: number; labelClosedY: number; labelOpenY: number; buttonPress: number; puckPress: number; puck: number;
  running: number; bounce: number; lean: number; press: number;
  markerX: number; markerY: number; markerOpacity: number; markerPress: number; markerKind: number;
};
export function clamp(n: number, lo = 0, hi = 1) { 'worklet'; return Math.max(lo, Math.min(hi, n)); }
export function mix(a: number, b: number, t: number) { 'worklet'; return a + (b - a) * t; }
export function progress(t: number, start: number, duration: number) { 'worklet'; return clamp((t - start) / duration); }
export function bezier(p: number, x1 = 0.23, y1 = 1, x2 = 0.32, y2 = 1) {
  'worklet';
  if (p <= 0) return 0;
  if (p >= 1) return 1;
  let lo = 0; let hi = 1; let u = p;
  for (let i = 0; i < 20; i++) {
    const v = 1 - u;
    const x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
    if (x < p) lo = u; else hi = u;
    u = (lo + hi) / 2;
  }
  const v = 1 - u;
  return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
}
export function spring(t: number, from: number, to: number, stiffness = 320, damping = 22, velocity = 0) {
  'worklet';
  if (t <= 0) return from;
  if (t >= 1) return to;
  const d = damping / 2; const w = Math.sqrt(stiffness - d * d); const x = from - to;
  return to + Math.exp(-d * t) * (x * Math.cos(w * t) + (velocity + d * x) / w * Math.sin(w * t));
}
export function bounceAt(t: number, amplitude = 1) {
  // Starts at zero (a kick of velocity, not of position): the arrival frame and the first bounce frame are the same picture.
  'worklet'; return t < 0 || t >= 0.9 ? 0 : amplitude * 1.6 * Math.exp(-6 * t) * Math.sin(2 * Math.PI * 1.9 * t);
}
export function stepEase(p: number) { 'worklet'; return bezier(p, 0.45, 0, 0.4, 1); }
export function sheetEase(p: number) { 'worklet'; return bezier(p, 0.32, 0.72, 0, 1); }
export function labelIndex(f: SheetFrame) {
  'worklet'; return f.labelOpen * (1 + clamp((f.position - 2.35) / 0.3));
}
export function sheetHeight(position: number, short: number, tall: number) {
  'worklet';
  const p = clamp(position, 0, 3); const step = Math.floor(p); const fraction = p - step;
  const a = step % 2; const b = (step + 1) % 2;
  return mix(short, tall, mix(a, b, fraction));
}
/** Disjoint phases: the outgoing label is fully gone before the incoming label starts. */
export function labelRoll(p: number) {
  'worklet';
  const out = bezier(progress(p, 0, 0.45)); const incoming = bezier(progress(p, 0.45, 0.55));
  return { outOpacity: 1 - out, inOpacity: incoming, outY: -12 * out, inY: 12 * (1 - incoming) };
}
export function rollSheetLabels(f: SheetFrame, from: SheetFrame, p: number, opening: boolean) {
  'worklet';
  const roll = labelRoll(p);
  if (opening) {
    f.labelClosed = from.labelClosed * roll.outOpacity;
    f.labelOpen = mix(from.labelOpen, 1, roll.inOpacity);
    f.labelClosedY = mix(from.labelClosedY, -12, 1 - roll.outOpacity);
    f.labelOpenY = mix(from.labelOpen === 0 ? 12 : from.labelOpenY, 0, roll.inOpacity);
  } else {
    f.labelOpen = from.labelOpen * roll.outOpacity;
    f.labelClosed = mix(from.labelClosed, 1, roll.inOpacity);
    f.labelOpenY = mix(from.labelOpenY, -12, 1 - roll.outOpacity);
    f.labelClosedY = mix(from.labelClosed === 0 ? 12 : from.labelClosedY, 0, roll.inOpacity);
  }
}
export function buttonLabelState(f: SheetFrame, index: number) {
  'worklet';
  if (index === 0) return { opacity: f.labelClosed, y: f.labelClosedY };
  const roll = labelRoll(clamp((f.position - 2.35) / 0.3));
  return { opacity: f.labelOpen * (index === 1 ? roll.outOpacity : roll.inOpacity),
    y: f.labelOpenY + (index === 1 ? roll.outY : roll.inY) };
}
/** Only fade at the far end of travel; finger drag never changes this envelope. */
export function sheetContentFade(open: number) {
  'worklet';
  const p = progress(open, 0, 0.25);
  return p * p * (3 - 2 * p);
}
/** Follow the sheet 80 ms late, settling with its 560 ms opening. */
export function openingStage(elapsed: number) { 'worklet'; return sheetEase(progress(elapsed, 0.08, 0.48)); }
export function sheetTravel(f: SheetFrame, layout: SheetLayout, reduced: boolean) {
  'worklet';
  return reduced ? 0 : (1 - f.open) * sheetHeight(f.position, layout.sheetShort, layout.sheetTall) + f.drag;
}
/** Match the initial finger speed, then use the authored release bezier; warp is monotone. */
export function releaseEase(p: number, distance: number, velocity: number, duration: number) {
  'worklet';
  const slope = Math.abs(distance) < 0.0001 ? 0 : clamp(velocity * duration / distance, 0, 6);
  const a = slope * 0.23;
  return bezier(a * clamp(p) + (1 - a) * clamp(p) * clamp(p));
}
export function sheetRest(): SheetFrame {
  'worklet';
  return { position: 0, open: 1, contents: 1, stage: 1, drag: 0, edge: 0, labelOpen: 1, labelClosed: 0, labelClosedY: -12, labelOpenY: 0,
    buttonPress: 0, puckPress: 0, puck: 0, running: 1, bounce: 0, lean: 0, press: 0,
    markerX: 201, markerY: 804, markerOpacity: 0, markerPress: 0, markerKind: 0 };
}
function marker(f: SheetFrame, t: number, down: number, up: number, kind: number, x: number, y: number) {
  'worklet';
  if (t < down - 0.12 || t >= up + 0.2) return;
  f.markerOpacity = Math.min(progress(t, down - 0.12, 0.12), 1 - progress(t, up, 0.2));
  f.markerPress = t < down ? 0 : t < up ? 1 : 1 - progress(t, up, 0.12);
  f.markerKind = kind; f.markerX = x; f.markerY = y;
}
function buttonBeat(t: number, down: number, up: number) {
  'worklet';
  if (t < down || t >= up + 1) return 0;
  return t < up ? bezier(progress(t, down, 0.12)) : spring(t - up, 1, 0);
}
/** Exactly periodic, including invisible marker coordinates and all residual channels. */
export function sheetLoop(seconds: number, reduced = false): SheetFrame {
  'worklet';
  const t = Math.round(((seconds % SHEET_PERIOD + SHEET_PERIOD) % SHEET_PERIOD) * 1e9) / 1e9 % SHEET_PERIOD;
  const f = sheetRest();
  const stepDuration = reduced ? 0.45 : STEP_DURATION;
  // Each step is left while its own loop rests or holds its hero pose (the stage's loops last 2.0, 2.3, 2.4 and 2.0 s).
  if (t >= 2.05) f.position = stepEase(progress(t, 2.05, stepDuration));
  // The scripted finger scrubs half-way, backs up a little (the morph is held and reversed under it),
  // then flicks on and lets go at speed; the release is the same velocity hand-over a real finger gets.
  if (t >= 6.2 && t < 6.8) {
    const p = progress(t, 6.2, 0.6); const e = p * p * (3 - 2 * p);
    f.position = 1 + 0.5 * e;
    f.lean = reduced ? 0 : -0.6 * Math.sin(Math.PI * p);
  } else if (t >= 6.8 && t < 7.1) {
    const p = progress(t, 6.8, 0.3); const e = p * p * (3 - 2 * p);
    f.position = 1.5 - 0.14 * e;
    f.lean = reduced ? 0 : 0.3 * Math.sin(Math.PI * p);
  } else if (t >= 7.1 && t < 7.4) {
    const p = progress(t, 7.1, 0.3);
    f.position = 1.36 + 0.36 * p * p;
    f.lean = reduced ? 0 : -0.8 * p;
  } else if (t >= 7.4) {
    f.position = reduced ? mix(1.72, 2, bezier(progress(t, 7.4, 0.28)))
      : mix(1.72, 2, releaseEase(progress(t, 7.4, 0.28), 0.28, 2.4, 0.28));
    f.lean = reduced || t >= 8.1 ? 0 : -0.8 * Math.exp(-10 * (t - 7.4)) * (1 - progress(t, 7.8, 0.3));
  }
  const fingerTravel = t < 6.2 ? 0 : t < 7.4 ? 280 * (f.position - 1) : 280 * 0.72;
  if (t >= 11.12) f.position = 2 + stepEase(progress(t, 11.12, stepDuration));
  if (!reduced) {
    f.bounce = bounceAt(t - (2.05 + stepDuration)) + bounceAt(t - 4.35, 0.6)
      + bounceAt(t - 7.68, 0.7) + bounceAt(t - (11.12 + stepDuration));
  }
  if (t >= 4.2 && t < 4.35) f.press = bezier(progress(t, 4.2, 0.11));
  else if (t >= 4.35 && t < 5.35) f.press = clamp(spring(t - 4.35, 1, 0));
  f.buttonPress = buttonBeat(t, 1.93, 2.05) + buttonBeat(t, 11, 11.12)
    + buttonBeat(t, 14.33, 14.45) + buttonBeat(t, 15.15, 15.27);
  if (t >= 14.45 && t < 15.27) {
    f.open = 1 - (reduced ? progress(t, 14.45, 0.2) : sheetEase(progress(t, 14.45, CLOSE_DURATION)));
    f.contents = reduced ? f.open : sheetContentFade(f.open);
    f.stage = reduced ? f.open : 1;
    rollSheetLabels(f, sheetRest(), progress(t, 14.45, 0.26), false);
    f.running = 0;
    if (t >= (reduced ? 14.65 : 14.91)) { f.position = 0; f.stage = 0; }
  } else if (t >= 15.27) {
    f.position = 0;
    f.open = reduced ? progress(t, 15.27, 0.2) : sheetEase(progress(t, 15.27, 0.56));
    f.contents = reduced ? f.open : sheetContentFade(f.open);
    f.stage = reduced ? f.open : openingStage(t - 15.27);
    const closed = { ...sheetRest(), labelOpen: 0, labelClosed: 1, labelClosedY: 0, labelOpenY: -12 };
    rollSheetLabels(f, closed, progress(t, 15.27, 0.26), true); f.running = 0;
  }
  marker(f, t, 1.93, 2.05, 0, 201, 804);
  marker(f, t, 4.2, 4.35, 1, 201, 612);
  marker(f, t, 6.15, 7.4, 2, 316 - fingerTravel, 716);
  marker(f, t, 11, 11.12, 0, 201, 804);
  marker(f, t, 14.33, 14.45, 0, 201, 804);
  marker(f, t, 15.15, 15.27, 0, 201, 804);
  return f;
}
