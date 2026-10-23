import { Skia, type SkPath } from '@shopify/react-native-skia';

/**
 * Rock geometry. Every rock is drawn in its own "rock units": a box of about 200 × 200 centred on
 * (0, 0), so the faces painted on them can share one set of coordinates whatever the rock's size.
 */

/** A small seeded random generator, so every rock keeps its shape from run to run. */
export function seeded(seed: number) {
  let t = seed >>> 0;
  return () => {
    t = (t + 0x6d2b79f5) >>> 0;
    let r = Math.imul(t ^ (t >>> 15), 1 | t);
    r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
}

// Samples around the outline, and how far the outline leans to a faceted polygon (1) rather than
// a round blob (0): river stones are mostly round with a few flat, chipped faces.
const SAMPLES = 120;
const FACETED = 0.55;
// Corners of the underlying polygon.
const CORNERS_MIN = 7;
const CORNERS_SPREAD = 4;
// Low harmonics that make the blob lumpy, and a fine ripple that roughens the edge.
const HARMONICS = [2, 3, 4, 5];
const LUMP = 0.05;
const RIPPLE = 0.006;
// Rocks sit on a flatter base: below this height (rock units) the outline is pressed up by half.
const BASE_AT = 66;

/**
 * A pebble outline: a faceted polygon blended with a lumpy blob and smoothed, so it reads as a
 * stone with a few flat faces rather than a circle. `aspect` squashes it vertically.
 */
export function makeRockPath(seed: number, aspect: number): SkPath {
  const rand = seeded(seed);
  const corners = CORNERS_MIN + Math.floor(rand() * CORNERS_SPREAD);
  const points: { x: number; y: number }[] = [];
  for (let k = 0; k < corners; k += 1) {
    const angle = ((k + (rand() - 0.5) * 0.5) / corners) * Math.PI * 2;
    const radius = 0.86 + rand() * 0.16;
    points.push({ x: Math.cos(angle) * radius, y: Math.sin(angle) * radius });
  }
  const angles = points.map((p) => {
    const a = Math.atan2(p.y, p.x);
    return a < 0 ? a + Math.PI * 2 : a;
  });
  const order = angles.map((a, i) => ({ a, p: points[i] })).sort((l, r) => l.a - r.a);
  const lumps = HARMONICS.map((n) => ({ n, amp: (rand() * LUMP) / Math.sqrt(n * 0.5), phase: rand() * Math.PI * 2 }));
  const ripplePhase = rand() * Math.PI * 2;

  const radii: number[] = [];
  for (let j = 0; j < SAMPLES; j += 1) {
    const theta = (j / SAMPLES) * Math.PI * 2;
    // The polygon edge this ray crosses: r·d = P1 + u·(P2 − P1)  ⇒  r = (P1 × E) / (d × E).
    let k = order.findIndex((o) => o.a > theta) - 1;
    if (k < 0) k = order.length - 1;
    const p1 = order[k].p;
    const p2 = order[(k + 1) % order.length].p;
    const ex = p2.x - p1.x;
    const ey = p2.y - p1.y;
    const dx = Math.cos(theta);
    const dy = Math.sin(theta);
    const denominator = dx * ey - dy * ex;
    const line = Math.abs(denominator) < 1e-6 ? 1 : (p1.x * ey - p1.y * ex) / denominator;
    let round = 0.95;
    for (const l of lumps) round += l.amp * Math.sin(l.n * theta + l.phase);
    round += RIPPLE * Math.sin(23 * theta + ripplePhase);
    radii.push(FACETED * Math.min(1.1, Math.max(0.7, line)) + (1 - FACETED) * round);
  }
  // A short running average rounds the polygon's corners off, like water would.
  const smooth = radii.map((_, j) => {
    let sum = 0;
    for (let o = -3; o <= 3; o += 1) sum += radii[(j + o + SAMPLES) % SAMPLES];
    return sum / 7;
  });

  const path = Skia.PathBuilder.Make();
  smooth.forEach((r, j) => {
    const theta = (j / SAMPLES) * Math.PI * 2;
    const x = Math.cos(theta) * r * 100;
    let y = Math.sin(theta) * r * 100 * aspect;
    const base = BASE_AT * aspect;
    if (y > base) y = base + (y - base) * 0.5;
    if (j === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  });
  path.close();
  return path.detach();
}

/** Tiny dark and light flecks scattered over the stone, as two paths. */
export function makeSpeckles(seed: number) {
  const rand = seeded(seed * 7 + 3);
  const dark = Skia.PathBuilder.Make();
  const light = Skia.PathBuilder.Make();
  for (let k = 0; k < 70; k += 1) {
    const angle = rand() * Math.PI * 2;
    const distance = Math.sqrt(rand()) * 94;
    const radius = 0.5 + rand() * rand() * 2.2;
    (k % 3 === 0 ? light : dark).addCircle(Math.cos(angle) * distance, Math.sin(angle) * distance, radius);
  }
  return { dark: dark.detach(), light: light.detach() };
}
