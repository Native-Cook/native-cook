import type { SkPathBuilder } from "@shopify/react-native-skia";

// A comet: one tapered stroke of light along a path. It is a ribbon through
// samples of the path, each a little earlier than the one before, so its tail
// is short while it moves slowly and long when it is fast, and it stays one
// unbroken shape however far it travels in a frame. Its clock runs past 1 by
// the tail's length, so the tail is drawn all the way into the target.

export const TRAIL = { samples: 40, gap: 0.005, size: 3.2 };
export const TRAIL_END = 1 + TRAIL.samples * TRAIL.gap;

/**
 * Half the ribbon's width at sample `k`, which is at `u` of the path, when the
 * head is at `t`.
 */
function widthAt(k: number, u: number, t: number, initialRadius: number) {
  "worklet";
  // The tail is only as long as the comet has had time to draw, and it thins
  // to nothing over that length: it ends in a point where it set off, not in
  // a cut.
  const reach = Math.min(TRAIL.samples, Math.max(t / TRAIL.gap, 1));
  const taper = Math.pow(Math.max(1 - k / reach, 0), 1.3);
  // The head grows to its size, or shrinks to it from the dot it was.
  const born = Math.min(t / 0.06, 1);
  // Each part is taken in as it reaches the target.
  const taken = u > 0.9 ? (1 - u) / 0.1 : 1;
  return (initialRadius * (1 - born) + TRAIL.size * born) * taper * taken;
}

/**
 * Adds the comet at clock `t` to `p`. `at(u)` is its position at time `u`, 0 to 1;
 * `ox`, `oy` are added to its points. `initialRadius` inherits a waiting dot.
 */
export function comet(
  p: SkPathBuilder,
  t: number,
  at: (u: number) => { x: number; y: number },
  ox: number,
  oy: number,
  initialRadius = 0,
) {
  "worklet";
  const xs: number[] = [];
  const ys: number[] = [];
  const rs: number[] = [];
  for (let k = 0; k <= TRAIL.samples; k++) {
    const u = t - k * TRAIL.gap;
    if (u < 0 || u >= 1) continue;
    const q = at(u);
    xs.push(ox + q.x);
    ys.push(oy + q.y);
    rs.push(widthAt(k, u, t, initialRadius));
  }
  const n = xs.length;
  if (n < 2) {
    // The button's dot is already a head on the very first launch frame.
    if (n === 1 && initialRadius > 0) p.addCircle(xs[0], ys[0], rs[0]);
    return;
  }

  // The two edges: each sample pushed out along the normal of the path there.
  const nx: number[] = [];
  const ny: number[] = [];
  for (let i = 0; i < n; i++) {
    const a = Math.max(i - 1, 0);
    const b = Math.min(i + 1, n - 1);
    const dx = xs[a] - xs[b];
    const dy = ys[a] - ys[b];
    const len = Math.hypot(dx, dy) || 1;
    nx.push(-dy / len);
    ny.push(dx / len);
  }
  p.moveTo(xs[0] + nx[0] * rs[0], ys[0] + ny[0] * rs[0]);
  for (let i = 1; i < n; i++) p.lineTo(xs[i] + nx[i] * rs[i], ys[i] + ny[i] * rs[i]);
  for (let i = n - 1; i >= 0; i--) p.lineTo(xs[i] - nx[i] * rs[i], ys[i] - ny[i] * rs[i]);
  // A round head: from the far edge, round the front, back to the start.
  const fx = ny[0];
  const fy = -nx[0];
  for (let j = 1; j < 6; j++) {
    const phi = (j * Math.PI) / 6;
    const c = Math.cos(phi);
    const s = Math.sin(phi);
    p.lineTo(xs[0] + rs[0] * (-nx[0] * c + fx * s), ys[0] + rs[0] * (-ny[0] * c + fy * s));
  }
  p.close();
}
