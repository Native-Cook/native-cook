import type { BurstLayout, FolderPlacement } from './burst-layout';
import { rowCenterX, rowCenterY, smooth01 } from './row-pose';
/** Seconds throughout. Rows are a flat array, 12 numbers per row. */
export const STATE = {
  stride: 12, p: 0, v: 1, m: 2, trigger: 3, active: 4,
  landed: 5, ejected: 6, fade: 7, modeFrom: 8, modeAt: 9, reduced: 10, flash: 11,
} as const;
export const BURST_EVENT = { eject: 1, land: 2, gulp: 4, allOut: 8, allIn: 16 } as const;
export const PHYSICS = {
  step: 1 / 240, maxDt: 1 / 20, openStagger: 0.045, closeStagger: 0.050,
  reducedStagger: 0.030, impulse: 0.8, stiffness: 38, damping: 10,
  suction: 6.3, epsilon: 0.14, drag: 1.2, modeSeconds: 0.180,
  fadeSeconds: 0.200, landingSeconds: 0.140,
  farRate: 0.70, nearRate: 0.98, distanceSlots: 8, flowRate: 18, bloomRate: 12,
} as const;
export type BurstState = {
  rows: number[];
  n: number;
  time: number;
  accumulator: number;
  target: 0 | 1;
  reduced: boolean;
  completed: boolean;
  flow: number;
  bloomX: number;
  bloomY: number;
  bloomStrength: number;
};
export function createBurstState(n: number, reduced = false, initiallyOpen = false): BurstState {
  'worklet';
  const rows: number[] = [];
  for (let i = 0; i < n; i++) {
    rows.push(initiallyOpen ? 1 : 0, 0, 0, 0, 0, initiallyOpen ? 1 : 0, initiallyOpen ? 1 : 0, initiallyOpen ? 1 : 0, 0, 0, reduced ? 1 : 0, 0);
  }
  return { rows, n, time: 0, accumulator: 0, target: initiallyOpen ? 1 : 0,
    reduced, completed: true, flow: 0, bloomX: 0, bloomY: 0, bloomStrength: 0 };
}
/** Call on the UI thread, using this simulation's seconds clock, not wall-clock milliseconds.
* Re-commanding the same target is a no-op. No p/v/m changes here: interruption is continuous.
*/
export function commandBurst(s: BurstState, open: boolean, now = s.time + s.accumulator): void {
  'worklet';
  const target = open ? 1 : 0;
  if (s.target === target)
    return;
  s.target = target;
  s.completed = false;
  const rows = s.rows.slice();
  for (let i = 0; i < s.n; i++) {
    const o = i * STATE.stride;
    rows[o + STATE.trigger] = now + (s.reduced ? PHYSICS.reducedStagger : open ? PHYSICS.openStagger : PHYSICS.closeStagger) * (open ? i : s.n - 1 - i);
    rows[o + STATE.active] = 0;
    rows[o + STATE.landed] = rows[o] >= 1 ? 1 : 0;
    rows[o + STATE.ejected] = rows[o] >= 0.06 ? 1 : 0;
  }
  s.rows = rows;
}
/** Mutates the simulation; publishes one fresh rows array per call. Returns edge-triggered bits.
* Caller: stepBurst(sim, dtSeconds, layout, placement); rows.set(sim.rows).
* All substeps, smoothing, triggers and fades use the same 240 Hz clock.
*/
export function stepBurst(s: BurstState, dt: number, layout: BurstLayout, folder: FolderPlacement = layout.docked, publish = true): number {
  'worklet';
  s.accumulator += Math.max(0, Math.min(PHYSICS.maxDt, dt));
  const rows = publish ? s.rows.slice() : s.rows;
  let events = 0;
  const h = PHYSICS.step;
  while (s.accumulator + 1e-10 >= h) {
    s.accumulator = Math.max(0, s.accumulator - h);
    s.time += h;
    let near = 0, flying = 0, weight = 0, sumX = 0, sumY = 0;
    let allOut = true, allIn = true;
    for (let i = 0; i < s.n; i++) {
      const o = i * STATE.stride;
      let p = rows[o], v = rows[o + STATE.v];
      // Interpolate duration, not frequency, so shorter trips still land top to bottom.
      const flightRate = 1 / (1 / PHYSICS.farRate + (1 / PHYSICS.nearRate - 1 / PHYSICS.farRate) * i / PHYSICS.distanceSlots);
      rows[o + STATE.flash] = Math.max(0, rows[o + STATE.flash] - h / PHYSICS.landingSeconds);
      if (!s.completed && s.time + 1e-10 >= rows[o + STATE.trigger]) {
        if (rows[o + STATE.active] === 0) {
          rows[o + STATE.active] = 1;
          rows[o + STATE.modeFrom] = rows[o + STATE.m];
          rows[o + STATE.modeAt] = s.time;
          // Only a stored strip receives an impulse. Reversing an airborne strip preserves momentum.
          if (!s.reduced && s.target === 1 && p <= 0)
            v = Math.max(v, PHYSICS.impulse * flightRate);
        }
        const blend = smooth01((s.time - rows[o + STATE.modeAt]) / PHYSICS.modeSeconds);
        rows[o + STATE.m] = rows[o + STATE.modeFrom] + (1 - s.target - rows[o + STATE.modeFrom]) * blend;
        if (s.reduced) {
          p = s.target;
          v = 0;
          const fade = rows[o + STATE.fade];
          rows[o + STATE.fade] = s.target === 1 ? Math.min(1, fade + h / PHYSICS.fadeSeconds) : Math.max(0, fade - h / PHYSICS.fadeSeconds);
          if (s.target === 1 && rows[o + STATE.landed] === 0) {
            events |= BURST_EVENT.land;
            rows[o + STATE.landed] = 1;
          }
          if (s.target === 0 && rows[o] > 0)
            events |= BURST_EVENT.gulp;
        }
        else if (s.target === 1) {
          v += (PHYSICS.stiffness * flightRate * flightRate * (1 - p) - PHYSICS.damping * flightRate * v) * h;
          p += v * h;
          // A reversal can retain inward velocity at the mouth: absorb at the physical boundary.
          if (p < 0) {
            p = 0;
            v = PHYSICS.impulse;
          }
          if (rows[o + STATE.ejected] === 0 && p >= 0.06) {
            events |= BURST_EVENT.eject;
            rows[o + STATE.ejected] = 1;
          }
          if (rows[o + STATE.landed] === 0 && p >= 1) {
            events |= BURST_EVENT.land;
            rows[o + STATE.landed] = 1;
            rows[o + STATE.flash] = 1;
          }
          if (Math.abs(p - 1) < 0.003 && Math.abs(v) < 0.02) {
            p = 1;
            v = 0;
          }
        }
        else if (p > 0 || v > 0) {
          v += (-PHYSICS.suction / (Math.max(0, p) + PHYSICS.epsilon) - PHYSICS.drag * v) * h;
          p += v * h;
          if (p <= 0) {
            p = 0;
            v = 0;
            events |= BURST_EVENT.gulp;
          }
        }
      }
      rows[o] = p;
      rows[o + STATE.v] = v;
      if (Math.abs(p - 1) >= 0.003 || Math.abs(v) >= 0.02 || (s.reduced && rows[o + STATE.fade] < 1))
        allOut = false;
      if (p !== 0 || v !== 0 || (s.reduced && rows[o + STATE.fade] > 0))
        allIn = false;
      if (!s.reduced && p > 0 && p < 0.22 && Math.abs(v) > 0.02)
        near++;
      if (!s.reduced && p > 0.015 && (Math.abs(p - 1) > 0.003 || Math.abs(v) > 0.02)) {
        flying++;
        const brightness = 0.12 + Math.sin(Math.PI * Math.min(1, p)) * 0.88;
        weight += brightness;
        sumX += rowCenterX(p, rows[o + STATE.m], i, layout, folder.cx, folder.scale) * brightness;
        sumY += rowCenterY(p, rows[o + STATE.m], i, layout, folder.cy, folder.scale) * brightness;
      }
    }
    s.flow += (Math.min(1, near / 3) - s.flow) * (1 - Math.exp(-PHYSICS.flowRate * h));
    s.bloomStrength += (flying / s.n - s.bloomStrength) * (1 - Math.exp(-PHYSICS.bloomRate * h));
    if (weight > 0) {
      s.bloomX = sumX / weight;
      s.bloomY = sumY / weight;
    }
    if (!s.completed && ((s.target === 1 && allOut) || (s.target === 0 && allIn))) {
      events |= s.target === 1 ? BURST_EVENT.allOut : BURST_EVENT.allIn;
      s.completed = true;
    }
  }
  s.rows = rows;
  return events;
}
