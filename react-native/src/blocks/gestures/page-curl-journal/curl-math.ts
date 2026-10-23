// Pure worklets for the page turn: where the grabbed point may go, and how far the sheet has turned.

// The sheet is bound at the spine (x = 0). A spine point must stay on the unfolded side of the fold, i.e. no
// farther from the tip than from the grab point; otherwise the page would tear. Project the tip into both discs.
export function clampTip(gx: number, gy: number, tx: number, ty: number, height: number): [number, number] {
  'worklet';
  let x = tx;
  let y = ty;
  for (let pass = 0; pass < 3; pass++) {
    for (let k = 0; k < 2; k++) {
      const sy = k === 0 ? 0 : height;
      const r = Math.hypot(gx, gy - sy);
      const d = Math.hypot(x, y - sy);
      if (d > r && d > 0) {
        x = (x * r) / d;
        y = sy + ((y - sy) * r) / d;
      }
    }
  }
  return [x, y];
}

// 0 = flat, 1 = turned all the way over the spine.
export function turnProgress(gx: number, gy: number, tx: number, ty: number) {
  'worklet';
  return Math.min(1, Math.hypot(gx - tx, gy - ty) / Math.max(1, 2 * gx));
}

// The roll is small while the sheet first lifts and while it lands flat: a sheet bends before it folds.
export function rollRamp(d: number, full: number, rampIn: number, rampOut: number) {
  'worklet';
  return Math.max(0, Math.min(1, d / rampIn, (full - d) / rampOut));
}

// The scripted finger: a quadratic path, eased so a flick is still accelerating when it lifts.
export function strokePoint(
  from: [number, number], via: [number, number], to: [number, number], u: number, flick: boolean,
): [number, number] {
  'worklet';
  const e = flick ? u * u : u * u * (3 - 2 * u);
  const a = (1 - e) * (1 - e);
  const b = 2 * e * (1 - e);
  const c = e * e;
  return [a * from[0] + b * via[0] + c * to[0], a * from[1] + b * via[1] + c * to[1]];
}
