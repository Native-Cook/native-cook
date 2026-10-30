// Raffle Drum Spin — the chrome drum as one triangle mesh (built once, on the JS thread).
//
// Vertex: position (3), normal (3), material (1), spins (1) = 8 floats. Materials: 0 polished
// chrome (cage, hubs, axle, crank), 1 brushed chrome (stand), 2 dark gunmetal (base), 3 black
// rubber (crank handle). `spins` = 1 for what turns with the cage about the x axis.

type V3 = [number, number, number];

const add = (a: V3, b: V3): V3 => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a: V3, b: V3): V3 => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const scale = (a: V3, s: number): V3 => [a[0] * s, a[1] * s, a[2] * s];
const cross = (a: V3, b: V3): V3 => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a: V3): V3 => {
  const l = Math.hypot(a[0], a[1], a[2]) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
};

class Mesh {
  data: number[] = [];
  push(p: V3, n: V3, material: number, spins: number) {
    this.data.push(p[0], p[1], p[2], n[0], n[1], n[2], material, spins);
  }
  tri(a: [V3, V3], b: [V3, V3], c: [V3, V3], m: number, s: number) {
    this.push(a[0], a[1], m, s);
    this.push(b[0], b[1], m, s);
    this.push(c[0], c[1], m, s);
  }
  /** A tube along `path` (closed or open), elliptical section rw × rh, `sides` around. */
  tube(path: V3[], rw: number, rh: number, closed: boolean, material: number, spins: number, sides = 10, up: V3 = [0, 0, 1]) {
    const rings: [V3, V3][][] = [];
    const count = path.length;
    for (let i = 0; i < count; i++) {
      const prev = path[closed ? (i - 1 + count) % count : Math.max(0, i - 1)];
      const next = path[closed ? (i + 1) % count : Math.min(count - 1, i + 1)];
      const tangent = norm(sub(next, prev));
      let side = cross(tangent, up);
      if (Math.hypot(...side) < 1e-4) side = cross(tangent, [0, 1, 0]);
      side = norm(side);
      const lift = norm(cross(side, tangent));
      const ring: [V3, V3][] = [];
      for (let j = 0; j < sides; j++) {
        const a = (j / sides) * Math.PI * 2;
        const c = Math.cos(a);
        const s = Math.sin(a);
        const offset = add(scale(side, c * rw), scale(lift, s * rh));
        const normal = norm(add(scale(side, c / rw), scale(lift, s / rh)));
        ring.push([add(path[i], offset), normal]);
      }
      rings.push(ring);
    }
    const segs = closed ? count : count - 1;
    for (let i = 0; i < segs; i++) {
      const r0 = rings[i];
      const r1 = rings[(i + 1) % count];
      for (let j = 0; j < sides; j++) {
        const j1 = (j + 1) % sides;
        this.tri(r0[j], r1[j], r1[j1], material, spins);
        this.tri(r0[j], r1[j1], r0[j1], material, spins);
      }
    }
    if (!closed) {
      // End caps.
      for (const [i, dir] of [[0, -1], [count - 1, 1]] as const) {
        const centre = path[i];
        const t = norm(sub(path[Math.min(count - 1, i + 1)], path[Math.max(0, i - 1)]));
        const n = scale(t, dir);
        for (let j = 0; j < sides; j++) this.tri([centre, n], [rings[i][j][0], n], [rings[i][(j + 1) % sides][0], n], material, spins);
      }
    }
  }
  /** An axis-aligned box with bevelled (averaged) normals at its edges. */
  box(min: V3, max: V3, material: number, spins: number) {
    const [x0, y0, z0] = min;
    const [x1, y1, z1] = max;
    const faces: [V3, V3, V3, V3, V3][] = [
      [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1], [0, 1, 0]],
      [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0], [0, -1, 0]],
      [[x0, y0, z1], [x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [0, 0, 1]],
      [[x1, y0, z0], [x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [0, 0, -1]],
      [[x1, y0, z1], [x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [1, 0, 0]],
      [[x0, y0, z0], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [-1, 0, 0]],
    ];
    for (const [a, b, c, d, n] of faces) {
      this.tri([a, n], [b, n], [c, n], material, spins);
      this.tri([a, n], [c, n], [d, n], material, spins);
    }
  }
}

const bezier = (p0: V3, p1: V3, p2: V3, p3: V3, steps: number): V3[] => {
  const out: V3[] = [];
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const u = 1 - t;
    out.push(add(add(scale(p0, u * u * u), scale(p1, 3 * u * u * t)), add(scale(p2, 3 * u * t * t), scale(p3, t * t * t))));
  }
  return out;
};

/** A rounded slab: corner radius `rc` in plan, edges rounded by `b`, from y0 to y1. */
function slab(m: Mesh, hw: number, hd: number, y0: number, y1: number, rc: number, b: number, top: number, side: number) {
  // The outline in plan, anticlockwise seen from above, with its outward normals.
  const outline: { p: [number, number]; n: [number, number] }[] = [];
  const corners: [number, number, number][] = [[hw - rc, hd - rc, 0], [-(hw - rc), hd - rc, Math.PI / 2], [-(hw - rc), -(hd - rc), Math.PI], [hw - rc, -(hd - rc), (3 * Math.PI) / 2]];
  for (const [cx, cz, a0] of corners) {
    for (let i = 0; i <= 8; i++) {
      const a = a0 + (i / 8) * (Math.PI / 2);
      outline.push({ p: [cx + Math.cos(a) * rc, cz + Math.sin(a) * rc], n: [Math.cos(a), Math.sin(a)] });
    }
  }
  // The profile: inset, height and normal (radial, up) from the top face round to the bottom.
  const prof: { o: number; y: number; nr: number; ny: number; mat: number }[] = [];
  for (let i = 0; i <= 5; i++) {
    const f = (Math.PI / 2) * (1 - i / 5);
    prof.push({ o: -b + b * Math.cos(f), y: y1 - b + b * Math.sin(f), nr: Math.cos(f), ny: Math.sin(f), mat: i < 3 ? top : side });
  }
  for (let i = 0; i <= 5; i++) {
    const f = -(Math.PI / 2) * (i / 5);
    prof.push({ o: -b + b * Math.cos(f), y: y0 + b + b * Math.sin(f), nr: Math.cos(f), ny: Math.sin(f), mat: side });
  }
  const at = (k: number, j: number): [V3, V3] => {
    const e = outline[k % outline.length];
    const q = prof[j];
    return [[e.p[0] + e.n[0] * q.o, q.y, e.p[1] + e.n[1] * q.o], norm([e.n[0] * q.nr, q.ny, e.n[1] * q.nr])];
  };
  for (let k = 0; k < outline.length; k++) {
    for (let j = 0; j < prof.length - 1; j++) {
      const mat = prof[j].mat;
      m.tri(at(k, j), at(k + 1, j), at(k + 1, j + 1), mat, 0);
      m.tri(at(k, j), at(k + 1, j + 1), at(k, j + 1), mat, 0);
    }
  }
  // Top and bottom faces as fans.
  for (const [j, ny, mat] of [[0, 1, top], [prof.length - 1, -1, side]] as const) {
    const c: [V3, V3] = [[0, prof[j].y, 0], [0, ny, 0]];
    for (let k = 0; k < outline.length; k++) {
      const a = at(k, j);
      const d = at(k + 1, j);
      m.tri(c, [a[0], [0, ny, 0]], [d[0], [0, ny, 0]], mat, 0);
    }
  }
}

/** A turned part along +x (a lathe): radius `rad(u)` for u 0 … 1 from x0 to x1, at (y, z). */
function lathe(m: Mesh, x0: number, x1: number, y: number, z: number, rad: (u: number) => number, material: number, spins: number, steps = 24, sides = 18) {
  const ring = (i: number) => {
    const u = i / steps;
    const r = rad(u);
    const dr = (rad(Math.min(1, u + 0.01)) - rad(Math.max(0, u - 0.01))) / (0.02 * (x1 - x0));
    const out: [V3, V3][] = [];
    for (let k = 0; k <= sides; k++) {
      const a = (k / sides) * Math.PI * 2;
      out.push([[x0 + (x1 - x0) * u, y + Math.cos(a) * r, z + Math.sin(a) * r], norm([-dr, Math.cos(a), Math.sin(a)])]);
    }
    return out;
  };
  let prev = ring(0);
  for (let i = 1; i <= steps; i++) {
    const next = ring(i);
    for (let k = 0; k < sides; k++) {
      m.tri(prev[k], next[k], next[k + 1], material, spins);
      m.tri(prev[k], next[k + 1], prev[k + 1], material, spins);
    }
    prev = next;
  }
}

export const MERIDIANS = 6;

export function buildDrum() {
  const m = new Mesh();
  // The cage, as in the reference: six wire meridians round an axis leaning 23° to the left (they
  // cross high on the left and low on the right), and the level hoop the axle runs through.
  const tilt = 0.4;
  const ct = Math.cos(tilt);
  const st = Math.sin(tilt);
  for (let k = 0; k < MERIDIANS; k++) {
    const a = (k / MERIDIANS) * Math.PI + 0.26;
    const path: V3[] = [];
    for (let i = 0; i < 120; i++) {
      const u = (i / 120) * Math.PI * 2;
      const x = Math.sin(u) * Math.cos(a);
      const y = Math.cos(u);
      const z = Math.sin(u) * Math.sin(a);
      path.push([(ct * x - st * y) * 0.99, (st * x + ct * y) * 0.99, z * 0.99]);
    }
    m.tube(path, 0.022, 0.022, true, 0, 1, 8, [0, 0, 1]);
  }
  const hoop: V3[] = [];
  for (let i = 0; i < 120; i++) {
    const u = (i / 120) * Math.PI * 2;
    hoop.push([Math.cos(u) * 0.995, 0, Math.sin(u) * 0.995]);
  }
  m.tube(hoop, 0.022, 0.022, true, 0, 1, 8, [0, 1, 0]);
  // Axle, hubs and crank (they turn with the cage).
  // The axle shows only outside the cage, as stubs through the stand.
  for (const [x0, x1] of [[-1.28, -0.96], [0.96, 1.34]]) m.tube([[x0, 0, 0], [x1, 0, 0]], 0.028, 0.028, false, 0, 1, 12, [0, 1, 0]);
  for (const s of [-1, 1]) m.tube([[s * 0.97, 0, 0], [s * 1.13, 0, 0]], 0.07, 0.07, false, 0, 1, 16, [0, 1, 0]);
  m.tube([[1.32, 0.02, 0], [1.32, -0.2, 0]], 0.03, 0.022, false, 0, 1, 10, [1, 0, 0]);
  // The handle: a chrome ferrule, then a turned lacquer grip that swells in the middle and closes
  // in a rounded end, so it reads as something made to be held.
  lathe(m, 1.29, 1.37, -0.2, 0, (u) => 0.05 + 0.012 * Math.sin(Math.PI * u), 0, 1, 6, 16);
  lathe(m, 1.37, 1.78, -0.2, 0, (u) => {
    const end = u > 0.86 ? Math.sqrt(Math.max(0, 1 - Math.pow((u - 0.86) / 0.14, 2))) : 1;
    return (0.046 + 0.02 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.08)), 0.8)) * end;
  }, 3, 1, 28, 18);
  // The stand: two flat polished bars bowing out from the hubs and in to the base's sides.
  for (const s of [-1, 1]) {
    m.tube([[s * 1.14, 0, 0], [s * 1.2, 0, 0]], 0.075, 0.09, false, 1, 0, 12, [0, 1, 0]);
    m.box([s > 0 ? 1.11 : -1.23, -0.15, -0.08], [s > 0 ? 1.23 : -1.11, 0.11, 0.08], 1, 0);
    const bar = bezier([s * 1.2, -0.1, 0], [s * 1.44, -0.42, 0], [s * 1.44, -1.06, 0], [s * 1.02, -1.32, 0], 30);
    m.tube(bar, 0.045, 0.085, false, 1, 0, 12, [0, 0, 1]);
  }
  slab(m, 1.08, 0.56, -1.44, -1.3, 0.18, 0.035, 2, 1);
  return new Float32Array(m.data);
}
