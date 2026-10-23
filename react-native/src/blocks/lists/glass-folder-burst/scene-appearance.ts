import { FOLDER } from './burst-contract';
import type { BurstLayout } from './burst-layout';
import type { SceneState } from './burst-timeline';
import { clamp01, smooth01 } from './row-pose';
/** Position followed by velocity for each named spring. */
export const SPRING = { lean: 0, disc: 2, recoil: 4, kick: 6, folderPress: 8, rowPress: 10, rise: 12 } as const;
/** Cubic Bezier timing with x inversion; shared by app and offline proofs. */
export function bezier(t: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  const x = clamp01(t);
  let lo = 0, hi = 1, u = x;
  for (let i = 0; i < 14; i++) {
    const q = 1 - u;
    const at = 3 * q * q * u * x1 + 3 * q * u * u * x2 + u * u * u;
    if (at < x)
      lo = u;
    else
      hi = u;
    u = (lo + hi) / 2;
  }
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}
// Defined before stepScene: a worklet captures the worklets it calls when it is created.
export function sceneFolder(s: SceneState, layout: BurstLayout) {
  'worklet';
  const t = s.idleTime;
  return { cx: layout.rest.cx, cy: layout.rest.cy + (layout.docked.cy - layout.rest.cy) * s.dock + s.springs[SPRING.kick]
      + (s.reduced ? 0 : 2 * Math.sin(t * Math.PI * 2 / 5) * (1 - s.dock) * s.idleGain),
    scale: (1.5 - .5 * s.dock),
    open: s.springs[SPRING.lean], press: s.springs[SPRING.folderPress], squash: s.springs[SPRING.recoil],
    flow: !s.expanded && !s.home && !(s.sim.completed && s.sim.target === 0) ? Math.max(.5, s.sim.flow) : s.sim.flow };
}
export function sceneOrb(s: SceneState, layout: BurstLayout) {
  'worklet';
  const f = sceneFolder(s, layout), p = clamp01(s.springs[SPRING.disc]);
  const rise = s.springs[SPRING.rise];
  const storedY = f.cy + (FOLDER.orbStoredY - rise) * f.scale;
  // Reach the left rail early, then approach the dock vertically below the text.
  const rail = smooth01(p / 0.65);
  const bow = p < 0.65 ? Math.sin(Math.PI * p / 0.65) : 0;
  return { x: f.cx + (layout.header.disc.cx - f.cx) * rail - 36 * bow,
    y: storedY + (layout.header.disc.cy - storedY) * p - 60 * Math.sin(Math.PI * p),
    r: 24 * f.scale + (20 - 24 * f.scale) * p,
    glow: s.reduced ? .55 : p > 0 && p < .98 ? 1 : p >= .98 ? .7 : .56 + .06 * Math.sin(s.idleTime * Math.PI * 2 / 5) * s.idleGain,
    // The mark shows as soon as the disc starts to clear the lip, and is gone again once it is stored.
    mark: Math.max(smooth01((p - 0.05) / 0.30), smooth01(rise / 16)),
    beat: s.reduced ? 0 : s.selected >= 0 ? 1 : .3 };
}
