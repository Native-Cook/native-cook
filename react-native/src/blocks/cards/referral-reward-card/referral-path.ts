import { referralSize as S } from "./referral-palette";

export type Point = { x: number; y: number };
type Sample = Point & { length: number };

// Speeds in pt/ms: the ring catches the launch, accelerates it, then lets it go.
export const COMET_SPEED = { launch: 1.3, catch: 0.45, sling: 1.1 };
// Three degrees per chord keeps the 42 pt ring smooth.
const SEGMENTS = 120;

/** Built once per layout on JS; the worklet only searches this length table. */
export function buildReferralPath(origin: Point, centre: Point) {
  const samples: Sample[] = [{ ...origin, length: 0 }];
  const add = (point: Point) => {
    const last = samples[samples.length - 1];
    samples.push({ ...point, length: last.length + Math.hypot(point.x - last.x, point.y - last.y) });
  };
  const curve = (from: Point, control: Point, to: Point) => {
    for (let i = 1; i <= SEGMENTS; i++) {
      const u = i / SEGMENTS;
      const m = 1 - u;
      add({ x: m * m * from.x + 2 * m * u * control.x + u * u * to.x,
        y: m * m * from.y + 2 * m * u * control.y + u * u * to.y });
    }
  };
  const join = { x: centre.x - S.ring, y: centre.y };
  curve(origin, { x: join.x, y: join.y + Math.max((origin.y - centre.y) * 0.5, S.ring * 1.2) }, join);
  const launchLength = samples[samples.length - 1].length;
  for (let i = 1; i <= SEGMENTS; i++) {
    const angle = Math.PI + (i / SEGMENTS) * Math.PI * 2;
    add(i === SEGMENTS ? join : { x: centre.x + S.ring * Math.cos(angle), y: centre.y + S.ring * Math.sin(angle) });
  }
  const orbitEnd = samples[samples.length - 1].length;
  curve(join, { x: join.x, y: centre.y - 63 }, { x: centre.x, y: centre.y - S.pointsRise + 6 });
  const length = samples[samples.length - 1].length;
  // Constant acceleration: distance is duration times the mean speed.
  const launch = 2 * launchLength / (COMET_SPEED.launch + COMET_SPEED.catch);
  const orbit = 2 * (orbitEnd - launchLength) / (COMET_SPEED.catch + COMET_SPEED.sling);
  const sling = 2 * (length - orbitEnd) / COMET_SPEED.sling;
  return { samples, launchLength, orbitEnd, length, launch, orbit, sling, total: launch + orbit + sling };
}

export type ReferralPath = ReturnType<typeof buildReferralPath>;

export function distanceAt(path: ReferralPath, time: number) {
  "worklet";
  const t = Math.min(Math.max(time, 0), path.total);
  if (t < path.launch) {
    return COMET_SPEED.launch * t + (COMET_SPEED.catch - COMET_SPEED.launch) * t * t / (2 * path.launch);
  }
  if (t < path.launch + path.orbit) {
    const u = t - path.launch;
    return path.launchLength + COMET_SPEED.catch * u + (COMET_SPEED.sling - COMET_SPEED.catch) * u * u / (2 * path.orbit);
  }
  const u = t - path.launch - path.orbit;
  return path.orbitEnd + COMET_SPEED.sling * u - COMET_SPEED.sling * u * u / (2 * path.sling);
}

/** `u` is elapsed time, so the ribbon's earlier samples share the head's motion. */
export function referralAt(path: ReferralPath, u: number) {
  "worklet";
  const distance = distanceAt(path, u * path.total);
  const points = path.samples;
  let lo = 0;
  let hi = points.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (points[mid].length < distance) lo = mid;
    else hi = mid;
  }
  const a = points[lo];
  const b = points[hi];
  const mix = (distance - a.length) / (b.length - a.length);
  return { x: a.x + (b.x - a.x) * mix, y: a.y + (b.y - a.y) * mix };
}
