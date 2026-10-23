/**
 * The bubble cloud as worklets. Bubbles leave the bead small, grow as they climb, and are pulled by springs to
 * the homes the source's bubbles settle in (`CLOUD`), a funnel that widens to the top of the screen; they push
 * each other apart only when they overlap more than the source's do, and move out of a finger's way. On a rewind they drift up and off the top. A fixed 1/120 s step keeps the motion
 * the same at any frame rate, and nothing is random at run time, so every loop and every port is identical.
 */
import { BURST, STICKERS, UPGRADE } from './motion';
import { CLOUD, CLOUD_FRAME } from './theme';

export const HIDDEN = 0;
export const OUT = 1;
export const LEAVING = 2;

export type Stickers = {
  phase: number;
  x: number[]; y: number[]; vx: number[]; vy: number[];
  hx: number[]; hy: number[]; r: number[]; scale: number[]; cell: number[]; order: number[];
  bx: number; by: number;
  fx: number; fy: number; fvx: number; fvy: number; finger: boolean;
  clock: number; remainder: number;
};

export function initialStickers(width: number, height: number): Stickers {
  'worklet';
  const n = CLOUD.length;
  const s: Stickers = {
    phase: HIDDEN, x: [], y: [], vx: [], vy: [], hx: [], hy: [], r: [], scale: [], cell: [], order: [],
    bx: 0, by: 0, fx: 0, fy: 0, fvx: 0, fvy: 0, finger: false, clock: 0, remainder: 0,
  };
  for (let i = 0; i < n; i++) {
    s.x.push(0); s.y.push(0); s.vx.push(0); s.vy.push(0); s.hx.push(0); s.hy.push(0);
    const [x, y, r, cell] = CLOUD[i];
    s.hx[i] = (x / CLOUD_FRAME.width) * width;
    s.hy[i] = (y / CLOUD_FRAME.height) * height;
    s.r.push(r); s.scale.push(0); s.cell.push(cell); s.order.push(0);
  }
  // Launch order: the lowest homes leave first, so the funnel fills from the bead upwards and outwards.
  for (let i = 0; i < n; i++) {
    let rank = 0;
    for (let j = 0; j < n; j++) if (s.hy[j] > s.hy[i] || (s.hy[j] === s.hy[i] && j < i)) rank += 1;
    s.order[i] = rank;
  }
  return s;
}

export function launch(s: Stickers, bx: number, by: number, reduced: boolean): void {
  'worklet';
  s.phase = OUT;
  // Every burst starts the float from the same phase, so repeated loops are identical.
  s.clock = 0;
  s.remainder = 0;
  s.bx = bx;
  s.by = by;
  for (let i = 0; i < s.r.length; i++) {
    s.x[i] = reduced ? s.hx[i] : bx;
    s.y[i] = reduced ? s.hy[i] : by;
    s.vx[i] = 0;
    s.vy[i] = 0;
    s.scale[i] = 0;
  }
}

export function leave(s: Stickers): void {
  'worklet';
  if (s.phase !== OUT) return;
  s.phase = LEAVING;
  s.clock = 0;
  s.finger = false;
}

export function hide(s: Stickers): void {
  'worklet';
  s.phase = HIDDEN;
  for (let i = 0; i < s.r.length; i++) s.scale[i] = 0;
}

/** Steps the cloud. Returns true once a rewind has carried every bubble off the top. */
export function stepStickers(s: Stickers, ms: number, height: number, reduced: boolean): boolean {
  'worklet';
  if (s.phase === HIDDEN) return false;
  s.remainder += Math.min(ms, STICKERS.maxFrameMs);
  const dt = STICKERS.step;
  const dms = dt * 1000;
  const n = s.r.length;
  while (s.remainder >= dms - 1e-6) {
    s.remainder -= dms;
    s.clock += dms;
    if (s.phase === LEAVING) {
      let left = 0;
      for (let i = 0; i < n; i++) {
        if (s.scale[i] === 0) continue;
        if (reduced) {
          s.scale[i] = Math.max(0, s.scale[i] - dms / 200);
          if (s.scale[i] > 0) left += 1;
          continue;
        }
        // Top bubbles go first, the funnel empties upwards.
        const start = (n - 1 - s.order[i]) * STICKERS.exitStaggerMs;
        if (s.clock > start) {
          const goal = s.hy[i] - height * STICKERS.exitLift;
          s.vy[i] += (STICKERS.exitStiffness * (goal - s.y[i]) - STICKERS.exitDamping * s.vy[i]) * dt;
          s.y[i] += s.vy[i] * dt;
        }
        if (s.y[i] + s.r[i] * s.scale[i] > -2) left += 1;
      }
      if (left === 0) {
        hide(s);
        return true;
      }
      continue;
    }
    // The finger's speed fades once it stops.
    s.fvx *= 0.9;
    s.fvy *= 0.9;
    for (let i = 0; i < n; i++) {
      if (reduced) {
        s.scale[i] = Math.min(1, s.scale[i] + dms / 200);
        continue;
      }
      const start = BURST.delayMs + s.order[i] * BURST.staggerMs;
      if (s.clock < start) continue;
      if (s.scale[i] === 0) {
        // Leave the bead upwards, fanned towards the bubble's home.
        const dx = s.hx[i] - s.bx;
        const dy = s.hy[i] - s.by;
        const d = Math.max(1, Math.hypot(dx, dy));
        s.vx[i] = (dx / d) * BURST.launchSpeed;
        s.vy[i] = (dy / d) * BURST.launchSpeed;
        s.scale[i] = BURST.startScale;
      }
      const travelled = Math.hypot(s.x[i] - s.bx, s.y[i] - s.by);
      const aged = (s.clock - start) / BURST.growMs;
      s.scale[i] = Math.max(s.scale[i], Math.min(1, BURST.startScale + Math.max(travelled / BURST.growOver, aged)));
      const float = Math.sin((s.clock / STICKERS.floatPeriodMs) * Math.PI * 2 + i * 1.7) * STICKERS.float;
      s.vx[i] += (STICKERS.stiffness * (s.hx[i] - s.x[i]) - STICKERS.damping * s.vx[i]) * dt;
      s.vy[i] += (STICKERS.stiffness * (s.hy[i] + float - s.y[i]) - STICKERS.damping * s.vy[i]) * dt;
      if (s.finger) {
        const dx = s.x[i] - s.fx;
        const dy = s.y[i] - s.fy;
        const d = Math.max(0.001, Math.hypot(dx, dy));
        const reach = STICKERS.push + s.r[i];
        if (d < reach) {
          const shove = (reach - d) * 0.35;
          s.x[i] += (dx / d) * shove;
          s.y[i] += (dy / d) * shove;
          s.vx[i] += ((dx / d) * shove) / dt * 0.2;
          s.vy[i] += ((dy / d) * shove) / dt * 0.2;
          // A swipe carries the bubbles it touches along with it.
          const near = 1 - d / reach;
          s.vx[i] += s.fvx * UPGRADE.fingerCarry * near * dt * 8;
          s.vy[i] += s.fvy * UPGRADE.fingerCarry * near * dt * 8;
        }
      }
      s.x[i] += s.vx[i] * dt;
      s.y[i] += s.vy[i] * dt;
    }
    if (reduced) continue;
    for (let i = 0; i < n; i++) {
      if (s.scale[i] === 0) continue;
      for (let j = i + 1; j < n; j++) {
        if (s.scale[j] === 0) continue;
        const dx = s.x[j] - s.x[i];
        const dy = s.y[j] - s.y[i];
        const d = Math.max(0.001, Math.hypot(dx, dy));
        const min = (s.r[i] * s.scale[i] + s.r[j] * s.scale[j]) * STICKERS.overlap;
        if (d >= min) continue;
        const push = ((min - d) / d) * STICKERS.separation * 0.5;
        s.x[i] -= dx * push; s.y[i] -= dy * push;
        s.x[j] += dx * push; s.y[j] += dy * push;
      }
    }
  }
  return false;
}
