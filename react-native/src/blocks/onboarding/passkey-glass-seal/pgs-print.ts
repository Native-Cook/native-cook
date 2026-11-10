// Passkey Glass Seal — the fingerprint's ridges and the key they unwind into, as one thread of points.
//
// The ridges are eleven nested loops open at the bottom, as in the reference's print, broken here and
// there like a real print. The key is the outline of a key (its bow's ring, inside and out, the shaft
// and its teeth). Both are walked as one thread and sampled at the same N fractions of their length,
// so point i sits on the print at A and on the key at B, and the print unwinds into the key in order.
// Coordinates in points, y down, from the print's centre; the print is 120 pt tall.

export const N = 6144;
export const RIDGES = 8;

type Pt = [number, number];

function rand(n: number) {
  const s = Math.sin(n * 91.3458) * 47453.5453;
  return s - Math.floor(s);
}

/** the print's ridges as polylines, inner first */
export function ridgeLines(): { pts: Pt[]; k: number }[] {
  const out: { pts: Pt[]; k: number }[] = [];
  for (let k = 0; k < RIDGES; k++) {
    const a = 5.5 + 6.9 * k;
    const b = 7 + 7.5 * k;
    const cy = -2;
    // every loop runs down to about the same line, as in the reference; the outermost stop short
    const legL = 54 - cy - (k > 5 ? (k - 5) * 14 : 0) - 3 * Math.sin(k * 1.7);
    const pts: Pt[] = [];
    // the left leg, bottom to top, leaning out as it goes down
    const steps = 24;
    for (let i = 0; i <= steps; i++) {
      const y = cy + legL * (1 - i / steps);
      const d = y - cy;
      pts.push([-(a + 0.05 * d - 0.0016 * d * d) + 0.0012 * d * d, y]);
    }
    // over the top
    for (let i = 1; i < 60; i++) {
      const th = Math.PI * (1 - i / 60);
      pts.push([a * Math.cos(th) * (1 + 0.04 * Math.sin(th * 2)), cy - b * Math.sin(th)]);
    }
    // the right leg, down; shorter, as in the reference
    const legR = legL * (0.82 + 0.12 * rand(k + 3));
    for (let i = 0; i <= steps; i++) {
      const y = cy + legR * (i / steps);
      const d = y - cy;
      pts.push([a + 0.05 * d - 0.0016 * d * d + 0.0012 * d * d, y]);
    }
    // break it once or twice
    const cuts = k < 2 ? 0 : k < 5 ? 1 : 2;
    let pieces: Pt[][] = [pts];
    for (let c = 0; c < cuts; c++) {
      const longest = pieces.reduce((m, p) => (p.length > m.length ? p : m), pieces[0]);
      const at = Math.floor(longest.length * (0.25 + 0.5 * rand(k * 7 + c)));
      const gap = 3;
      const i = pieces.indexOf(longest);
      pieces = [...pieces.slice(0, i), longest.slice(0, at), longest.slice(at + gap), ...pieces.slice(i + 1)];
    }
    for (const p of pieces) if (p.length > 2) out.push({ pts: p, k });
  }
  // the core: one short stroke in the innermost loop
  out.unshift({ pts: Array.from({ length: 16 }, (_, i) => [0, -2 + i * 1.6] as Pt), k: 0 });
  return out;
}

/** the key's outline as polylines: the bow's ring out and in, the shaft over the top, back under the teeth */
function keyLines(): Pt[][] {
  const bow: Pt = [-50, 0];
  const ring = (r: number): Pt[] => Array.from({ length: 81 }, (_, i) => {
    const t = (i / 80) * Math.PI * 2;
    return [bow[0] + r * Math.cos(t), bow[1] + r * Math.sin(t)];
  });
  const top: Pt[] = [];
  for (let x = -26; x <= 66; x += 2) top.push([x, -6.5]);
  const under: Pt[] = [];
  const teeth: [number, number][] = [
    [66, 6.5],
    [62, 6.5],
    [62, 16],
    [54, 16],
    [54, 6.5],
    [48, 6.5],
    [48, 13],
    [40, 13],
    [40, 6.5],
    [34, 6.5],
    [34, 18],
    [27, 18],
    [27, 6.5],
    [-26, 6.5],
  ];
  for (let i = 0; i < teeth.length - 1; i++) {
    const [x0, y0] = teeth[i];
    const [x1, y1] = teeth[i + 1];
    const n = Math.max(2, Math.round(Math.hypot(x1 - x0, y1 - y0) / 2));
    for (let j = 0; j < n; j++) under.push([x0 + ((x1 - x0) * j) / n, y0 + ((y1 - y0) * j) / n]);
  }
  const tip: Pt[] = Array.from({ length: 8 }, (_, i) => [66, -6.5 + (13 * i) / 7] as Pt);
  return [ring(24), ring(11), top, tip, under];
}

function lengths(lines: Pt[][]) {
  const segs: { a: Pt; b: Pt; len: number; line: number }[] = [];
  lines.forEach((pts, li) => {
    for (let i = 0; i < pts.length - 1; i++) segs.push({ a: pts[i], b: pts[i + 1], len: Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]), line: li });
  });
  return { segs, total: segs.reduce((s, g) => s + g.len, 0) };
}

/** N points along a thread of polylines at even arc length: x, y and the polyline each falls on */
function sample(lines: Pt[][]) {
  const { segs, total } = lengths(lines);
  const out = new Float32Array(N * 3);
  let si = 0;
  let acc = 0;
  for (let i = 0; i < N; i++) {
    const s = ((i + 0.5) / N) * total;
    while (si < segs.length - 1 && acc + segs[si].len < s) {
      acc += segs[si].len;
      si++;
    }
    const g = segs[si];
    const f = g.len > 0 ? (s - acc) / g.len : 0;
    out[i * 3] = g.a[0] + (g.b[0] - g.a[0]) * f;
    out[i * 3 + 1] = g.a[1] + (g.b[1] - g.a[1]) * f;
    out[i * 3 + 2] = g.line;
  }
  return out;
}

/** the points for the GPU: per point (ax, ay, bx, by) and (ridge 0…1, distance from the core 0…1, seed, -) */
export function makePoints() {
  const ridges = ridgeLines();
  const a = sample(ridges.map((r) => r.pts));
  const b = sample(keyLines());
  const out = new Float32Array(N * 8);
  for (let i = 0; i < N; i++) {
    const ax = a[i * 3];
    const ay = a[i * 3 + 1];
    out.set([ax, ay, b[i * 3], b[i * 3 + 1], ridges[a[i * 3 + 2]].k / (RIDGES - 1), Math.min(1, Math.hypot(ax, ay + 2) / 62), rand(i + 11), 0], i * 8);
  }
  return out;
}
