/*
 Paper Plane Steps — the folds, worked out once per sheet size.

 The sheet (0..SW, 0..SH) is folded four times, each a flat fold along a line:
   1. the top-left corner down to the centre line (line from (SW/2, 0) to (0, SW/2));
   2. the top-right corner, mirrored;
   3. the new left edge onto the centre line (line from the nose at 22.5° to the vertical);
   4. the same on the right.
 For each fold every face of the paper so far is clipped by the line (Sutherland–Hodgman, carrying
 its texture point along): the part on the fold's far side stays, the part on the near side is the
 flap that turns over. After the fold the flap is reflected across the line, shows its other side,
 and lands on top of everything with its layers in reverse order (a stack turned over).
 The result is a dart: a long nose and a short tail, the top view of a plane.

 A Face is flat arrays so it crosses to the UI thread as plain data.
*/

export type Face = { pts: number[]; uv: number[]; back: number; layer: number };
export type Fold = { ax: number; ay: number; bx: number; by: number; statics: Face[]; movers: Face[] };
export type Plan = { sw: number; sh: number; folds: Fold[]; final: Face[]; maxLayer: number };

function sideOf(ax: number, ay: number, bx: number, by: number, x: number, y: number) {
  return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
}

/** Keeps the part of a face where sign · side ≥ 0, or null when nothing (or a sliver) is left. */
function clip(f: Face, ax: number, ay: number, bx: number, by: number, sign: number): Face | null {
  const pts: number[] = [];
  const uv: number[] = [];
  const n = f.pts.length / 2;
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    const px = f.pts[i * 2];
    const py = f.pts[i * 2 + 1];
    const qx = f.pts[j * 2];
    const qy = f.pts[j * 2 + 1];
    const sp = sign * sideOf(ax, ay, bx, by, px, py);
    const sq = sign * sideOf(ax, ay, bx, by, qx, qy);
    if (sp >= 0) {
      pts.push(px, py);
      uv.push(f.uv[i * 2], f.uv[i * 2 + 1]);
    }
    if (sp >= 0 !== sq >= 0) {
      const t = sp / (sp - sq);
      pts.push(px + (qx - px) * t, py + (qy - py) * t);
      uv.push(f.uv[i * 2] + (f.uv[j * 2] - f.uv[i * 2]) * t, f.uv[i * 2 + 1] + (f.uv[j * 2 + 1] - f.uv[i * 2 + 1]) * t);
    }
  }
  if (pts.length < 6) return null;
  // Drop slivers thinner than half a point.
  let area = 0;
  const m = pts.length / 2;
  for (let i = 0; i < m; i += 1) {
    const j = (i + 1) % m;
    area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1];
  }
  if (Math.abs(area) < 1) return null;
  return { pts, uv, back: f.back, layer: f.layer };
}

function reflect(x: number, y: number, ax: number, ay: number, bx: number, by: number): [number, number] {
  const dx = bx - ax;
  const dy = by - ay;
  const l = Math.hypot(dx, dy);
  const ux = dx / l;
  const uy = dy / l;
  const d = (x - ax) * ux + (y - ay) * uy;
  const fx = ax + ux * d;
  const fy = ay + uy * d;
  return [2 * fx - x, 2 * fy - y];
}

export function makePlan(sw: number, sh: number): Plan {
  const t = Math.tan((22.5 * Math.PI) / 180);
  const lines: [number, number, number, number, number, number][] = [
    // ax, ay, bx, by, and a point on the flap's side
    [sw / 2, 0, 0, sw / 2, 1, 1],
    [sw / 2, 0, sw, sw / 2, sw - 1, 1],
    [sw / 2, 0, 0, sw / 2 / t, 2, sw * 0.6],
    [sw / 2, 0, sw, sw / 2 / t, sw - 2, sw * 0.6],
  ];
  let faces: Face[] = [{ pts: [0, 0, sw, 0, sw, sh, 0, sh], uv: [0, 0, sw, 0, sw, sh, 0, sh], back: 0, layer: 0 }];
  const folds: Fold[] = [];
  for (const [ax, ay, bx, by, rx, ry] of lines) {
    const flap = Math.sign(sideOf(ax, ay, bx, by, rx, ry));
    const statics: Face[] = [];
    const movers: Face[] = [];
    for (const f of faces) {
      const s = clip(f, ax, ay, bx, by, -flap);
      const m = clip(f, ax, ay, bx, by, flap);
      if (s) statics.push(s);
      if (m) movers.push(m);
    }
    folds.push({ ax, ay, bx, by, statics, movers });
    let top = 0;
    for (const f of statics) top = Math.max(top, f.layer);
    let moverTop = 0;
    for (const f of movers) moverTop = Math.max(moverTop, f.layer);
    const moved = movers.map((f) => {
      const pts: number[] = [];
      for (let i = 0; i < f.pts.length; i += 2) pts.push(...reflect(f.pts[i], f.pts[i + 1], ax, ay, bx, by));
      return { pts, uv: f.uv.slice(), back: 1 - f.back, layer: top + 1 + (moverTop - f.layer) };
    });
    faces = statics.concat(moved).sort((a, b) => a.layer - b.layer);
  }
  let maxLayer = 0;
  for (const f of faces) maxLayer = Math.max(maxLayer, f.layer);
  for (const f of folds) {
    f.statics.sort((a, b) => a.layer - b.layer);
    f.movers.sort((a, b) => a.layer - b.layer);
  }
  return { sw, sh, folds, final: faces, maxLayer };
}
