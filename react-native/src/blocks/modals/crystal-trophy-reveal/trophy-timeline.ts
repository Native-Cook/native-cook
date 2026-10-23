// Deterministic UI-thread theatre: one simulation, shared by fingers, accessibility and the demo.
import { TIMING, type TrophyFrame } from './trophy-contract';

export const TAU = Math.PI * 2;
export const EVENT = { soft: 1, medium: 2, light: 4, success: 8, selection: 16, closed: 32, shared: 64 } as const;
export type Phase = 'closed' | 'revealing' | 'open' | 'closing';
export type TrophyState = {
  phase: Phase; clock: number; phaseTime: number; demoTime: number; paused: boolean; recovering: boolean;
  reduced: boolean; auto: boolean; started: boolean; realDown: boolean; lastInput: number;
  open: number; hem: number; hemRaw: number; hemV: number; shiver: number; seam: number;
  lamp: number; beam: number; under: number; rise: number; yaw: number; yawV: number; restTarget: number; etch: number;
  touch: number; touchFrom: number; touchX: number; touchY: number; flash: number; flashAge: number;
  down: boolean; downAt: number; downX: number; downY: number; dragBase: number; maxDrag: number; crossed: boolean;
  spin: boolean; spinTarget: number; spinStart: number; caught: boolean; frontReported: boolean; releaseAge: number; releaseYaw: number;
  startOpen: number; startRise: number; startLamp: number; startYaw: number; startUi: number;
  titleTime: number; ui: number; veil: number; hint: number;
  cameraScale: number; cameraY: number; cameraV: number; seated: boolean; startCamera: number;
  shareX: number; shareY: number; closeX: number; closeY: number;
  shareDown: boolean; closeDown: boolean; sharePressAge: number; closePressAge: number; sharePress: number; sharePressV: number; closePress: number; closePressV: number;
  shareAge: number; copied: number; check: number; sheen: number; events: number;
};

export function clamp(value: number, low = 0, high = 1): number {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

/** Bisection gives the specified CSS cubic curve without capturing an easing closure. */
export function bezier(x: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  const p = clamp(x);
  if (p === 0 || p === 1) return p;
  let lo = 0; let hi = 1; let t = p;
  for (let i = 0; i < 14; i++) {
    t = (lo + hi) * 0.5;
    const u = 1 - t;
    const bx = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t;
    if (bx < p) lo = t; else hi = t;
  }
  return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
}
export function enter(x: number): number { 'worklet'; return bezier(x, 0.23, 1, 0.32, 1); }
export function travel(x: number): number { 'worklet'; return bezier(x, 0.77, 0, 0.175, 1); }

/** Exact damped spring step; stable under 120 Hz, 60 Hz and clamped dropped frames. */
export function spring(x: number, v: number, target: number, stiffness: number, damping: number, dt: number) {
  'worklet';
  const a = damping * 0.5;
  const w = Math.sqrt(Math.max(0.0001, stiffness - a * a));
  const decay = Math.exp(-a * dt);
  const c = Math.cos(w * dt); const sn = Math.sin(w * dt);
  const y = x - target; const b = (v + a * y) / w;
  return { x: target + decay * (y * c + b * sn), v: decay * (v * c - (a * b + w * y) * sn) };
}
export function pulse(age: number, tail = 0.52): number {
  'worklet';
  if (age < 0 || age > 0.06 + tail) return 0;
  return age < 0.06 ? enter(age / 0.06) : 1 - enter((age - 0.06) / tail);
}
export function initialState(auto = false, reduced = false): TrophyState {
  'worklet';
  const shown = auto;
  return {
    phase: shown ? 'open' : 'closed', clock: 0, phaseTime: 0, demoTime: 0, paused: false, recovering: false,
    reduced, auto, started: shown, realDown: false, lastInput: 0,
    open: shown ? 1 : 0, hem: shown ? 1 : 0, hemRaw: shown ? 1 : 0, hemV: 0, shiver: 0, seam: shown ? 0 : 0.37,
    lamp: shown ? 1 : 0, beam: shown ? 1 : 0, under: 0, rise: shown ? 1 : 0, yaw: 0, yawV: 0, restTarget: 0, etch: shown ? 1 : 0,
    touch: 0, touchFrom: 0, touchX: 0, touchY: 1.8, flash: 0, flashAge: 100,
    down: false, downAt: 0, downX: 0, downY: 0, dragBase: 0, maxDrag: 0, crossed: false,
    spin: false, spinTarget: 0, spinStart: 0, caught: false, frontReported: false, releaseAge: 100, releaseYaw: 0,
    startOpen: 0, startRise: 0, startLamp: 0, startYaw: 0, startUi: 0,
    titleTime: shown ? 10 : -1, ui: shown ? 1 : 0, veil: 0, hint: 0,
    cameraScale: 1, cameraY: 0, cameraV: 0, seated: false, startCamera: 1,
    shareX: 0.5, shareY: 0.5, closeX: 0.5, closeY: 0.5,
    shareDown: false, closeDown: false, sharePressAge: 100, closePressAge: 100, sharePress: 0, sharePressV: 0, closePress: 0, closePressV: 0,
    shareAge: 100, copied: 0, check: 0, sheen: -1, events: 0,
  };
}
export function interrupt(s: TrophyState, down: boolean): void {
  'worklet';
  s.paused = s.auto; s.recovering = false; s.realDown = down; s.lastInput = s.clock;
  // A real touch also releases the invisible demo finger and its button.
  if (down) { s.touchFrom = s.touch; s.releaseAge = 0; s.down = false; s.shareDown = false; s.closeDown = false; s.spin = false; }
}
export function reveal(s: TrophyState): void {
  'worklet';
  if (s.phase !== 'closed') return;
  s.phase = 'revealing'; s.phaseTime = 0; s.startOpen = s.open; s.started = true;
  s.seated = false; s.cameraY = 0; s.cameraV = 0;
  s.titleTime = -1; s.ui = 0; s.hint = 0; s.events |= EVENT.soft;
}
export function close(s: TrophyState): void {
  'worklet';
  if (s.phase !== 'open' && s.phase !== 'revealing') return;
  s.startCamera = s.cameraScale;
  s.phase = 'closing'; s.phaseTime = 0; s.startOpen = s.open; s.startRise = s.rise;
  s.startLamp = s.lamp; s.startYaw = Math.atan2(Math.sin(s.yaw), Math.cos(s.yaw)); s.startUi = s.ui;
  s.down = false; s.spin = false; s.yawV = 0; s.shareDown = false;
}
export function touchDown(s: TrophyState, x: number, y: number): void {
  'worklet';
  if (s.phase !== 'open' && s.phase !== 'closed') return;
  s.touchFrom = s.touch; s.down = true; s.downAt = s.clock; s.downX = x; s.downY = y;
  s.dragBase = s.yaw; s.restTarget = Math.round(s.yaw / TAU) * TAU;
  s.maxDrag = 0; s.crossed = Math.abs(s.yaw - s.restTarget) > 0.52; s.spin = false; s.yawV = 0;
  s.touchX = clamp(x, -0.86, 0.86); s.touchY = clamp(y, 0, 2.9);
}
/** dx/dy are canvas points; x/y are trophy-space coordinates. */
export function dragTo(s: TrophyState, dx: number, dy: number, x: number, y: number, width: number): void {
  'worklet';
  if (!s.down) return;
  s.maxDrag = Math.max(s.maxDrag, Math.sqrt(dx * dx + dy * dy));
  s.touchX = clamp(x, -0.86, 0.86); s.touchY = clamp(y, 0, 2.9);
  if (s.phase === 'closed') {
    if (s.reduced) return;
    const pull = Math.abs(dx) / (width * 0.5);
    s.open = pull < 0.22 ? pull : Math.min(0.32, 0.22 + (pull - 0.22) / (1 + (pull - 0.22) * 10));
  } else if (s.phase === 'open') {
    const base = s.dragBase - s.restTarget;
    const raw = base + dx * 0.0062;
    const extra = Math.max(0, Math.abs(raw) - 0.52);
    const baseExtra = Math.max(0, Math.abs(base) - 0.52);
    const baseBand = Math.sign(base) * (Math.min(Math.abs(base), 0.52) + baseExtra / (1 + baseExtra * 3.2));
    const band = Math.sign(raw) * (Math.min(Math.abs(raw), 0.52) + extra / (1 + extra * 3.2));
    // Subtract the starting resistance so grabbing a spinning back face never snaps it to the front.
    s.yaw = s.dragBase + band - baseBand;
    if (extra > 0 && !s.crossed) { s.crossed = true; s.events |= EVENT.selection; }
  }
}
export function tapAt(s: TrophyState, x: number, y: number): void {
  'worklet';
  if (s.phase !== 'open') return;
  s.touchX = clamp(x, -0.86, 0.86); s.touchY = clamp(y, 0, 2.9); s.flashAge = 0;
  s.yawV += s.reduced ? 0 : (x < 0 ? -0.6 : 0.6); s.events |= EVENT.light;
}
export function release(s: TrophyState, velocity: number, cancelled = false): void {
  'worklet';
  if (!s.down) return;
  s.touchFrom = s.touch; s.down = false; s.lastInput = s.clock; s.releaseAge = 0; s.releaseYaw = s.yaw;
  if (s.phase === 'closed') {
    if (!cancelled && (s.maxDrag < 8 || s.open > 0.12 || Math.abs(velocity) > 600)) reveal(s);
    return;
  }
  if (s.phase !== 'open') return;
  if (!cancelled && s.clock - s.downAt <= 0.25 && s.maxDrag < 8) {
    tapAt(s, s.touchX, s.touchY); return;
  }
  s.yawV = cancelled ? 0 : velocity * 0.0062;
  if (!s.reduced && !cancelled && Math.abs(velocity) > 900) {
    const sign = Math.sign(velocity);
    s.spin = true; s.caught = false; s.frontReported = false; s.spinStart = s.yaw;
    s.spinTarget = (Math.round(s.yaw / TAU) + sign) * TAU;
    // Enough energy for one complete revolution, even for a just-over-threshold flick.
    s.yawV = sign * Math.max(10.8, Math.min(16, Math.abs(s.yawV)));
  }
}
export function pressShare(s: TrophyState, down: boolean, activate = false, x = 0.5, y = 0.5): void {
  'worklet';
  if (down && !s.shareDown) s.sharePressAge = 0;
  if (down) { s.shareX = clamp(x); s.shareY = clamp(y); }
  s.shareDown = down && s.phase === 'open';
  if (activate && s.phase === 'open') { s.shareAge = 0; s.events |= EVENT.light | EVENT.shared; }
}
export function pressClose(s: TrophyState, down: boolean, activate = false, x = 0.5, y = 0.5): void {
  'worklet';
  if (down && !s.closeDown) s.closePressAge = 0;
  if (down) { s.closeX = clamp(x); s.closeY = clamp(y); }
  s.closeDown = down;
  if (activate) close(s);
}
export function uiProgress(s: TrophyState, start: number, duration = 0.32): number {
  'worklet';
  return s.reduced ? s.ui : enter((s.titleTime - start) / duration) * s.ui;
}
export function letterProgress(s: TrophyState, index: number): number {
  'worklet';
  return s.reduced ? s.ui : enter((s.titleTime - TIMING.title.start - index * TIMING.title.stagger) / TIMING.title.letter) * s.ui;
}

/** Inverse of the stage Group: translate after scaling about the crystal centre. */
export function stagePoint(s: TrophyState, x: number, y: number, frame: TrophyFrame) {
  'worklet';
  const centerY = frame.originY - 1.45 * frame.unit;
  return { x: frame.originX + (x - frame.originX) / s.cameraScale,
    y: centerY + (y - centerY - s.cameraY) / s.cameraScale };
}

/** Mutates a private snapshot; the hook publishes it atomically, then writes StageValues once per frame. */
export function stepSimulation(s: TrophyState, dt: number): void {
  'worklet';
  const previous = s.phaseTime;
  const previousRise = s.rise;
  s.clock += dt; s.phaseTime += dt; s.flashAge += dt; s.releaseAge += dt; s.shareAge += dt;
  const t = s.phaseTime;
  s.flash = s.reduced ? (s.flashAge < 0.3 ? Math.sin(Math.PI * s.flashAge / 0.3) * 0.35 : 0) : pulse(s.flashAge);
  s.touch = s.down && s.phase === 'open'
    ? s.touchFrom + (1 - s.touchFrom) * enter((s.clock - s.downAt) / 0.12)
    : s.touchFrom * (1 - enter(s.releaseAge / 0.22));
  s.sharePressAge += dt; s.closePressAge += dt;
  const sp = spring(s.sharePress, s.sharePressV, s.shareDown ? 1 : 0, 500, 32, dt);
  s.sharePress = s.shareDown ? enter(s.sharePressAge / 0.12) : sp.x; s.sharePressV = s.shareDown ? 0 : sp.v;
  const cp = spring(s.closePress, s.closePressV, s.closeDown ? 1 : 0, 500, 32, dt);
  s.closePress = s.closeDown ? enter(s.closePressAge / 0.12) : cp.x; s.closePressV = s.closeDown ? 0 : cp.v;
  s.copied = enter(s.shareAge / 0.22) * (1 - enter((s.shareAge - 1.43) / 0.22));
  s.check = clamp(s.shareAge / 0.22) * s.copied;
  s.sheen = s.shareAge < 0.65 ? s.shareAge / 0.65 : -1;
  if (s.shareAge > 2) { s.copied = 0; s.check = 0; }

  if (s.reduced && (s.phase === 'revealing' || s.phase === 'closing')) {
    const opening = s.phase === 'revealing';
    s.veil = t < 0.18 ? enter(t / 0.18) : 1 - enter((t - 0.18) / 0.32);
    const shown = t < 0.18 ? !opening : opening;
    s.open = shown ? 1 : 0; s.hem = s.open; s.hemRaw = s.open; s.rise = s.open; s.etch = s.open;
    s.lamp = s.open; s.beam = s.open; s.under = 0; s.shiver = 0;
    s.ui = opening ? enter((t - 0.30) / 0.32) : 1 - enter(t / 0.18);
    s.titleTime = opening ? 10 : -1; s.yaw = 0;
    if (t >= 0.62) {
      s.phase = opening ? 'open' : 'closed'; s.phaseTime = 0; s.veil = 0;
      if (!opening) s.events |= EVENT.soft | EVENT.closed;
    }
  } else if (s.phase === 'revealing') {
    s.open = s.startOpen + (1 - s.startOpen) * travel((t - 0.3) / 0.7);
    s.shiver = clamp(t / 0.32); s.seam = (0.37 + 0.63 * enter(t / 0.32)) * (1 - clamp((t - 0.32) / 0.68));
    const l = t - 0.86;
    s.lamp = l < 0 ? 0 : l < 0.06 ? 0.55 * l / 0.06 : l < 0.15 ? 0.55 - 0.43 * (l - 0.06) / 0.09 : 0.12 + 0.88 * enter((l - 0.15) / 0.24);
    s.beam = travel((t - 0.92) / 0.66);
    s.under = enter((t - 1.44) / 0.2) * (1 - enter((t - 2) / 0.6));
    const rising = spring(0, 0, 1, 110, 11.5, Math.max(0, t - 1.56)).x;
    s.rise = rising <= 1 ? rising : 1 + 0.065 * Math.tanh((rising - 1) / 0.065);
    if (previous < 1.56 && t >= 1.56) s.events |= EVENT.medium;
    if (previous < 2.3 && t >= 2.3) { s.flashAge = 0; s.touchX = 0.05; s.touchY = 2.85; s.events |= EVENT.light; }
    s.lamp += 0.2 * s.flash;
    s.etch = bezier((t - 2.36) / 1.15, 0.4, 0, 0.2, 1);
    if (previous < 3.51 && t >= 3.51) { s.flashAge = 0; s.touchX = 0.02; s.touchY = 2.0; s.events |= EVENT.success; }
    s.titleTime = t; s.ui = 1;
    if (t >= 4) { s.phase = 'open'; s.phaseTime = 0; s.rise = 1; s.hem = 1; s.hemRaw = 1; s.hemV = 0; s.shiver = 0; }
  } else if (s.phase === 'closing') {
    s.ui = s.startUi * (1 - enter(t / 0.2));
    s.lamp = s.startLamp * (1 - enter(t / 0.32));
    s.rise = s.startRise * (1 - travel((t - 0.08) / 0.48));
    if (s.rise < 0.001) s.etch = 0;
    s.yaw = s.startYaw * (1 - enter(t / 0.3));
    s.open = s.startOpen * (1 - travel((t - 0.42) / 0.62));
    s.under = 0; s.flash = 0; s.seam = 0.37 * enter((t - 1.02) / 0.3);
    if (previous < 1.04 && t >= 1.04) s.events |= EVENT.soft;
    if (t >= 1.32) { s.phase = 'closed'; s.phaseTime = 0; s.events |= EVENT.closed; s.titleTime = -1; }
  } else if (s.phase === 'closed') {
    if (!s.down) { const rail = spring(s.open, s.yawV, 0, 180, 22, dt); s.open = rail.x; s.yawV = rail.v; }
    const seamClock = s.clock % 3.2;
    const demoSwell = s.demoTime >= 2.1 && s.demoTime < 2.8 ? Math.sin(Math.PI * (s.demoTime - 2.1) / 0.7) : 0;
    s.seam = 0.37 + (s.reduced ? 0 : s.auto && !s.paused ? 0.45 * demoSwell : 0.09 * Math.sin(TAU * seamClock / 3.2)) + Math.max(0, s.open);
    s.hint = s.auto ? 0 : enter((t - 1.4) / 0.4) * (s.reduced ? 1 : 0.8 + 0.2 * Math.sin(TAU * ((s.auto && !s.paused ? s.demoTime : s.clock) % 3.2) / 3.2));
    if (!s.started && t >= 0.5 && !s.down) reveal(s);
  } else {
    s.titleTime = Math.min(10, s.titleTime + dt); s.ui = 1; s.lamp = 1 + 0.22 * s.flash; s.shiver = 0;
    if (!s.down) {
      if (s.reduced) { s.yaw = s.releaseYaw * (1 - enter(s.releaseAge / 0.24)); s.yawV = 0; }
      else if (s.spin && !s.caught) {
        const old = s.yaw;
        s.yaw += s.yawV * (1 - Math.exp(-1.45 * dt)) / 1.45;
        s.yawV *= Math.exp(-1.45 * dt);
        if (Math.abs(s.yaw - s.spinStart) >= Math.PI || Math.abs(s.yawV) < 2) s.caught = true;
        if ((old - s.spinTarget) * (s.yaw - s.spinTarget) <= 0) s.caught = true;
      } else {
        const idleAge = Math.max(0, s.clock - s.lastInput - 1.2);
        const target = s.spin ? s.spinTarget : s.restTarget + (s.auto ? 0 : 0.07 * Math.sin(TAU * (s.clock % 7) / 7) * enter(idleAge / 0.8));
        const turn = spring(s.yaw, s.yawV, target, 120, 14, dt);
        if (s.spin && !s.frontReported && (s.yaw - target) * (turn.x - target) <= 0) {
          s.events |= EVENT.medium; s.frontReported = true;
        }
        s.yaw = turn.x; s.yawV = turn.v;
        // Renormalise only at rest; crossing the front is marked once by caught -> spin false below.
        if (s.spin && Math.abs(s.yaw - target) < 0.002 && Math.abs(s.yawV) < 0.02) {
          s.yaw = 0; s.yawV = 0; s.restTarget = 0; s.spin = false; s.caught = false;
        } else if (!s.spin && s.restTarget !== 0 && Math.abs(s.yaw - s.restTarget) < 0.002 && Math.abs(s.yawV) < 0.02) {
          s.yaw -= s.restTarget; s.restTarget = 0;
        } else if (!s.spin && s.auto && Math.abs(s.yaw) < 0.0001 && Math.abs(s.yawV) < 0.001) { s.yaw = 0; s.yawV = 0; }
      }
    }
  }
  if (s.reduced) { s.cameraScale = 1; s.cameraY = 0; s.cameraV = 0; }
  else {
    const push = s.phase === 'revealing'
      ? (t < TIMING.curtain.start ? enter(t / TIMING.curtain.start)
        : 1 - enter((t - TIMING.curtain.start) / (TIMING.settled - TIMING.curtain.start))) * 0.07
      : s.phase === 'closing' ? (s.startCamera - 1) * (1 - enter(t / 0.32)) : 0;
    s.cameraScale = (1 + push) * (1 + 0.012 * s.flash);
    if (s.phase === 'revealing' && !s.seated && previousRise < 1 && s.rise >= 1) {
      s.seated = true; s.cameraY = 2; s.cameraV = 0;
    } else {
      const seat = spring(s.cameraY, s.cameraV, 0, 420, 26, dt);
      s.cameraY = seat.x; s.cameraV = seat.v;
      if (Math.abs(s.cameraY) < 0.0001 && Math.abs(s.cameraV) < 0.001) { s.cameraY = 0; s.cameraV = 0; }
    }
  }
  if (!s.reduced) {
    const hem = spring(s.hemRaw, s.hemV, s.open, 70, 9, dt);
    s.hemRaw = hem.x; s.hemV = hem.v;
    // Keep the prescribed spring's energy, softly limiting only the visible cloth overshoot.
    s.hem = hem.x < 0 ? 0.06 * Math.tanh(hem.x / 0.06)
      : hem.x > 1 ? 1 + 0.06 * Math.tanh((hem.x - 1) / 0.06) : hem.x;
  }
}
