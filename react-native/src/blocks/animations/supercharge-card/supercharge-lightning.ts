// Seeded, held electrical channels: uneven long bends with fine displacement and dying forks.
import { BOLT, BOLT_TOP, GLASS, RING } from './supercharge-layout';
import { M } from './supercharge-constants';
import type { LightningFrame } from './supercharge-types';
export type Point = { x: number; y: number };
type Random = { seed: number };
export type BoltGeometry = { main: number[]; branch: number[]; twig: number[]; ground: number[]; groundBranch: number[]; groundTwig: number[]; groundX: number; meanX: number };
export function random(r: Random) {
  'worklet';
  r.seed = (Math.imul(r.seed, 1664525) + 1013904223) >>> 0;
  return r.seed / 4294967296;
}
export function emptyLightning(): LightningFrame {
  'worklet';
  return { main: [], branch: [], twig: [], level: 0 };
}
// Displace across a fixed forward axis. Progress can never reverse, even at a sharp kink.
export function channel(from: Point, to: Point, seed: number, levels = 6, roughness = 0.11): Point[] {
  'worklet';
  const rng = { seed }, dx = to.x - from.x, dy = to.y - from.y;
  const span = Math.max(1, Math.hypot(dx, dy));
  const vertical = Math.abs(dy) > 1;
  const detour = (random(rng) * 2 - 1) * span * 0.16;
  const bend = 0.3 + random(rng) * 0.4;
  let samples = [{ t: 0, offset: 0 }, { t: bend, offset: detour }, { t: 1, offset: 0 }];
  let amplitude = span * roughness;
  for (let level = 0; level < levels; level++) {
    const next = [samples[0]];
    for (let i = 1; i < samples.length; i++) {
      const a = samples[i - 1], b = samples[i], fraction = 0.35 + random(rng) * 0.3;
      next.push({ t: a.t + (b.t - a.t) * fraction,
        offset: a.offset + (b.offset - a.offset) * fraction + (random(rng) * 2 - 1) * amplitude }, b);
    }
    samples = next; amplitude *= 0.55;
  }
  const kinkCount = 2 + Math.floor(random(rng) * 3), kinks: number[] = [];
  for (let i = 0; i < kinkCount; i++) {
    const at = (i + 0.3 + random(rng) * 0.4) / kinkCount;
    const run = 0.025 + random(rng) * 0.025;
    const height = Math.tan((25 + random(rng) * 30) * Math.PI / 180) * span * run * (random(rng) < 0.5 ? -1 : 1);
    kinks.push(at, run, height);
  }
  const result: Point[] = [];
  for (let i = 0; i < samples.length; i++) {
    const sample = samples[i]; let offset = sample.offset;
    for (let j = 0; j < kinks.length; j += 3) offset += Math.max(0, 1 - Math.abs(sample.t - kinks[j]) / kinks[j + 1]) * kinks[j + 2];
    result.push(vertical ? { x: from.x + dx * sample.t + offset, y: from.y + dy * sample.t }
      : { x: from.x + dx * sample.t, y: from.y + dy * sample.t + offset });
  }
  return result;
}
// Geometry stores [x0,y0,x1,y1,arrival0,arrival1] so a leader clips branches by travelled distance.
function append(target: number[], points: Point[], start: number, span: number) {
  'worklet';
  let total = 0;
  for (let i = 1; i < points.length; i++) total += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y);
  let walked = 0;
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i];
    const length = Math.hypot(b.x - a.x, b.y - a.y);
    target.push(a.x, a.y, b.x, b.y, start + span * walked / Math.max(1, total), start + span * (walked + length) / Math.max(1, total));
    walked += length;
  }
}
function xAt(points: Point[], y: number) {
  'worklet';
  for (let i = 1; i < points.length; i++) {
    if (points[i].y >= y) {
      const a = points[i - 1], b = points[i];
      return a.x + (b.x - a.x) * Math.max(0, (y - a.y) / Math.max(0.001, b.y - a.y));
    }
  }
  return points[points.length - 1].x;
}
// Include every main-channel bend in the fork's y partitions before enforcing separation.
// Both paths are piecewise linear, so this also prevents crossings between their vertices.
function separateFork(fork: Point[], trunk: Point[], side: number): Point[] {
  'worklet';
  const result = [fork[0]], root = fork[0];
  for (let i = 1; i < fork.length; i++) {
    const a = fork[i - 1], b = fork[i];
    for (let j = 0; j < trunk.length; j++) {
      const y = trunk[j].y;
      if (y <= a.y || y >= b.y) continue;
      const x = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y);
      const edge = trunk[j].x + side * (y - root.y) * 0.22;
      result.push({ x: side > 0 ? Math.max(x, edge) : Math.min(x, edge), y });
    }
    const edge = xAt(trunk, b.y) + side * (b.y - root.y) * 0.22;
    result.push({ x: side > 0 ? Math.max(b.x, edge) : Math.min(b.x, edge), y: b.y });
  }
  return result;
}
function forks(points: Point[], count: number, rng: Random, branch: number[], twig: number[]) {
  'worklet';
  const top = points[0].y, height = points[points.length - 1].y - top;
  let side = random(rng) < 0.5 ? -1 : 1;
  for (let i = 0; i < count; i++) {
    // Stratification leaves at least 28 pt between sky roots (including their jitter).
    const y = top + 8 + i * (height - 46) / Math.max(1, count - 1) + random(rng) * 5;
    const origin = { x: xAt(points, y), y };
    const u = (y - top) / height;
    if (random(rng) < 0.85) side *= -1;
    const local = Math.atan2(xAt(points, y + 10) - xAt(points, y - 10), 20);
    const angle = (22 + random(rng) * 20) * Math.PI / 180;
    // Clamp the heading outward; the channel envelope below guarantees no crossing.
    const heading = side * Math.max(0.32, Math.min(1.3, side * local + angle));
    const length = 60 + (1 - u) * (70 + random(rng) * 60);
    const end = { x: origin.x + Math.sin(heading) * length, y: origin.y + Math.cos(heading) * length };
    const fork = separateFork(channel(origin, end, rng.seed, 4, 0.11), points, side);
    const tapered: number[] = [];
    append(tapered, fork, u, (1 - u) * 0.85);
    for (let j = 0; j < tapered.length; j += 6) {
      const target = (tapered[j + 4] - u) / Math.max(0.001, (1 - u) * 0.85) < 0.45 ? branch : twig;
      for (let k = 0; k < 6; k++) target.push(tapered[j + k]);
    }
    const subCount = Math.floor(random(rng) * 3);
    for (let j = 0; j < subCount; j++) {
      const at = fork[Math.floor(fork.length * (0.3 + random(rng) * 0.35))];
      const subLength = 25 + random(rng) * 35;
      const sub = separateFork(channel(at, { x: at.x + side * subLength * 0.78, y: at.y + subLength * 0.63 }, rng.seed, 3, 0.08), points, side);
      append(twig, sub, u + (1 - u) * 0.3, (1 - u) * 0.6);
    }
  }
}
export function strikeGeometry(seed: number): BoltGeometry {
  'worklet';
  const rng = { seed };
  const start = { x: 90 + random(rng) * 222, y: -30 };
  const points = channel(start, BOLT_TOP, rng.seed, 6);
  const groundX = 150 + random(rng) * 100;
  const result: BoltGeometry = { main: [], branch: [], twig: [], ground: [], groundBranch: [], groundTwig: [], groundX, meanX: 0 };
  append(result.main, points, 0, 1);
  for (let i = 0; i < points.length; i++) result.meanX += points[i].x / points.length;
  forks(points, 5 + Math.floor(random(rng) * 3), rng, result.branch, result.twig);
  const ground = channel({ x: groundX, y: 628 }, { x: groundX + (random(rng) - 0.5) * 150, y: 900 }, rng.seed, 6);
  append(result.ground, ground, 0, 1);
  forks(ground, 2 + Math.floor(random(rng) * 2), rng, result.groundBranch, result.groundTwig);
  return result;
}
function cut(lines: number[], progress: number, shimmer: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < lines.length; i += 6) {
    if (lines[i + 4] >= progress) continue;
    const t = Math.min(1, (progress - lines[i + 4]) / Math.max(0.00001, lines[i + 5] - lines[i + 4]));
    const jitter = i === 0 ? 0 : shimmer * Math.sin(i * 7.13);
    // Identical endpoint perturbations keep adjacent segments joined during a re-strike.
    const endJitter = i + 6 === lines.length ? 0 : shimmer * Math.sin((i + 6) * 7.13);
    out.push(lines[i] + jitter, lines[i + 1], lines[i] + (lines[i + 2] - lines[i]) * t + endJitter, lines[i + 1] + (lines[i + 3] - lines[i + 1]) * t);
  }
  return out;
}
export function strikeFrame(g: BoltGeometry, age: number, reduced: boolean): LightningFrame {
  'worklet';
  if (reduced) return { main: cut(g.main, 1.01, 0), ground: [], branch: [], twig: [], level: age >= M.reduced.channel ? 0 : Math.max(0, Math.sin(Math.PI * Math.max(0, age) / M.reduced.channel)) * M.reduced.channelLevel };
  if (age >= M.strike.channelEnd) return emptyLightning();
  const leader = age < M.strike.leader;
  const front = leader ? Math.min(1, (Math.floor(age / M.strike.leaderJump) + 1) / M.strike.leaderSteps) : 1.01;
  let pulse = 0;
  while (pulse < M.strike.pulseEnds.length && age >= M.strike.pulseEnds[pulse]) pulse++;
  const tail = M.strike.pulseEnds[M.strike.pulseEnds.length - 1];
  const level = leader ? M.strike.leaderLevel : M.strike.pulseLevels[pulse] * (pulse === M.strike.pulseEnds.length ? 1 - (age - tail) / (M.strike.channelEnd - tail) : 1);
  const shimmer = leader || age < M.strike.pulseEnds[1] ? 0 : Math.floor(age / M.strike.shimmerInterval) % M.strike.shimmerVariants * M.strike.shimmerPoints;
  const branch = age < M.strike.branchEnd ? cut(g.branch, front, 0) : [];
  const twig = age < M.strike.branchEnd ? cut(g.twig, front, 0) : [];
  if (!leader && age < M.strike.branchEnd) {
    branch.push(...cut(g.groundBranch, 1.01, 0)); twig.push(...cut(g.groundTwig, 1.01, 0));
  }
  return { main: cut(g.main, front, shimmer), branch, twig,
    ground: leader ? [] : cut(g.ground, 1.01, shimmer), level,
    firstPulse: !leader && age < M.strike.pulseEnds[0],
    forkLevel: leader ? M.strike.leaderLevel : M.strike.branchLevel * Math.min(1, Math.max(0, (M.strike.branchEnd - age) / 0.03)) };
}
function segments(points: Point[], target: number[]) {
  'worklet';
  for (let i = 1; i < points.length; i++) target.push(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y);
}
// Split at exactly 35% of travelled length, including the segment that crosses the boundary.
function thread(points: Point[], result: LightningFrame, strong: boolean) {
  'worklet';
  let total = 0;
  for (let i = 1; i < points.length; i++) total += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y);
  const root = strong ? total * 0.35 : 0;
  let walked = 0;
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1], b = points[i], length = Math.hypot(b.x - a.x, b.y - a.y);
    if (walked < root && walked + length > root) {
      const t = (root - walked) / length, x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
      result.branch.push(a.x, a.y, x, y); result.twig.push(x, y, b.x, b.y);
    } else {
      const target = walked < root ? result.branch : result.twig;
      target.push(a.x, a.y, b.x, b.y);
    }
    walked += length;
  }
}
export function filaments(x: number, y: number, energy: number, seed: number, charged = false): LightningFrame {
  'worklet';
  const rng = { seed };
  const result = emptyLightning();
  result.level = charged ? 1 : M.filament.base + M.filament.gain * energy;
  const candidates: Point[] = [];
  for (let i = 0; i < BOLT.length; i++) {
    const a = BOLT[i], b = BOLT[(i + 1) % BOLT.length];
    candidates.push({ x: a[0], y: a[1] }, { x: (a[0] + b[0]) / 2, y: (a[1] + b[1]) / 2 });
  }
  const count = charged || energy > M.filament.thirdAt ? 3 : 2;
  for (let n = 0; n < count; n++) {
    let nearest = 0, distance = Infinity;
    for (let i = 0; i < candidates.length; i++) {
      const d = Math.hypot(candidates[i].x - x, candidates[i].y - y);
      if (d >= 26 && d < distance) { distance = d; nearest = i; }
    }
    const from = candidates[nearest];
    candidates.splice(nearest, 1);
    random(rng);
    const points = channel(from, { x, y }, rng.seed, 4, 0.2);
    thread(points, result, n === 0);
    const forks = Math.floor(random(rng) * 3);
    for (let j = 0; j < forks; j++) {
      const at = points[Math.floor((0.2 + random(rng) * 0.5) * (points.length - 1))];
      const side = random(rng) < 0.5 ? -1 : 1;
      segments(channel(at, { x: at.x + side * (12 + random(rng) * 20), y: at.y - 8 - random(rng) * 18 }, rng.seed, 3, 0.2), result.twig);
    }
  }
  return result;
}
export function corona(seed: number, count: number): number[] {
  'worklet';
  const rng = { seed }, result: number[] = [];
  const tips = [0, 2, 3, 5];
  for (let i = 0; i < count; i++) {
    const p = BOLT[tips[Math.floor(random(rng) * tips.length)]];
    const theta = Math.atan2(p[1] - 312, p[0] - 214) + (random(rng) - 0.5) * 1.2;
    const length = 16 + random(rng) * 24;
    segments(channel({ x: p[0], y: p[1] }, { x: p[0] + Math.cos(theta) * length, y: p[1] + Math.sin(theta) * length }, rng.seed, 3, 0.22), result);
  }
  return result;
}

// Independent glass/ring arcs stay legible even when the finger is close to the lamp.
export function rimArcs(seed: number, energy: number, count: number): LightningFrame {
  'worklet';
  const rng = { seed }, result = emptyLightning(), candidates: Point[] = [];
  result.level = 0.6 + energy * 0.4;
  for (let i = 0; i < BOLT.length; i++) {
    const a = BOLT[i], b = BOLT[(i + 1) % BOLT.length];
    if (i === 0 || i === 2 || i === 3 || i === 5) candidates.push({ x: a[0], y: a[1] });
    candidates.push({ x: (a[0] + b[0]) / 2, y: (a[1] + b[1]) / 2 });
  }
  for (let n = 0; n < Math.min(3, count); n++) {
    let from = candidates[0];
    let to: Point = { x: GLASS.x, y: 320 };
    for (let attempt = 0; attempt < 24; attempt++) {
      from = candidates[Math.floor(random(rng) * candidates.length)];
      const target = random(rng);
      if (energy >= 0.7 && target < 0.58) {
        const theta = -Math.PI + random(rng) * Math.PI;
        to = { x: RING.x + Math.cos(theta) * RING.radius, y: RING.y + Math.sin(theta) * RING.radius };
      } else if (target < 0.72) to = { x: GLASS.x + random(rng) * GLASS.width, y: GLASS.y };
      else to = { x: random(rng) < 0.5 ? GLASS.x : GLASS.x + GLASS.width, y: GLASS.y + 12 + random(rng) * 142 };
      const length = Math.hypot(to.x - from.x, to.y - from.y);
      if (length >= 20 && length <= 140) break;
    }
    const points = channel(from, to, rng.seed, 4, 0.11);
    thread(points, result, n === 0);
    const count = 1 + Math.floor(random(rng) * 3);
    for (let i = 0; i < count; i++) {
      const at = points[Math.floor((0.2 + random(rng) * 0.5) * (points.length - 1))];
      const angle = random(rng) * Math.PI * 2, length = 10 + random(rng) * 20;
      segments(channel(at, { x: at.x + Math.cos(angle) * length, y: at.y + Math.sin(angle) * length }, rng.seed, 3, 0.1), result.twig);
    }
  }
  return result;
}
