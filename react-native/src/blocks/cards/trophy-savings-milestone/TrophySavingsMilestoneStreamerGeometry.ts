import { clamp, smooth } from './TrophySavingsMilestoneMotion';

export type StreamerPoint = { x: number; y: number };
export type StreamerStation = { center: StreamerPoint; tangent: StreamerPoint; width: number; facing: number; widthAxis?: StreamerPoint; depth?: number; axisDepth?: number; joinBlend?: number };
export type StreamerSpan = { a: StreamerStation; b: StreamerStation; foreground: boolean; depth?: number; surface?: 'edge' };
export type StreamerFrame = {
  headDistance: number; tailDistance: number; bodyLength: number; tipLength: number; coiledLength: number;
  coilScale: number; coilJoint: StreamerPoint; coilWidth: number; coilOpacity: number; meshOpacity: number; spans: StreamerSpan[]; edgeSpans: StreamerSpan[];
  roll: { center: StreamerPoint; tangent: StreamerPoint; width: number; opacity: number; foreground: boolean };
};
type Curve = { a: StreamerPoint; b: StreamerPoint; c: StreamerPoint; d: StreamerPoint };
type GuidePoint = { distance: number; center: StreamerPoint; tangent: StreamerPoint; facing: number; foreground: boolean };
type Guide = { points: GuidePoint[]; seam: number; length: number; feedLength: number; boundaries: number[] };

export const STREAMER_MATERIAL_LENGTH = 250;
export const STREAMER_TIP_RADIUS = 9;
export const STREAMER_FACE_WIDTH = 34;
export const STREAMER_FEED_JOINT = { x: 35.24, y: 154.95 };

function add(a: StreamerPoint, b: StreamerPoint): StreamerPoint { 'worklet'; return { x: a.x + b.x, y: a.y + b.y }; }
function sub(a: StreamerPoint, b: StreamerPoint): StreamerPoint { 'worklet'; return { x: a.x - b.x, y: a.y - b.y }; }
function mul(a: StreamerPoint, b: number): StreamerPoint { 'worklet'; return { x: a.x * b, y: a.y * b }; }
function unit(a: StreamerPoint): StreamerPoint { 'worklet'; return mul(a, 1 / Math.max(0.000001, Math.hypot(a.x, a.y))); }
function normal(a: StreamerPoint): StreamerPoint { 'worklet'; return { x: -a.y, y: a.x }; }

function cubic(curve: Curve, t: number): StreamerPoint {
  'worklet';
  const u = 1 - t;
  return add(add(mul(curve.a, u * u * u), mul(curve.b, 3 * u * u * t)), add(mul(curve.c, 3 * u * t * t), mul(curve.d, t * t * t)));
}
function cubicTangent(curve: Curve, t: number): StreamerPoint {
  'worklet';
  const u = 1 - t;
  return unit(add(add(mul(sub(curve.b, curve.a), 3 * u * u), mul(sub(curve.c, curve.b), 6 * u * t)), mul(sub(curve.d, curve.c), 3 * t * t)));
}
function facing(curve: number, t: number, release = 0) {
  'worklet';
  if (curve === 1) return Math.cos(Math.PI * smooth((t - 0.25) / 0.75));
  if (curve === 2) return -1;
  if (curve === 3) return -Math.cos(Math.PI * 0.5 * smooth((t - 0.65) / 0.35));
  if (curve === 4) return Math.sin(Math.PI * 0.5 * smooth(t / 0.28)) * (1 - 2 * smooth(release / 0.38) * Math.sin(Math.PI * t) ** 2);
  if (curve === 5 || curve === 6 || curve === 7) return 1;
  if (curve >= 8) return -1;
  return 1;
}

const CURVES: Curve[] = [
  { a: STREAMER_FEED_JOINT, b: { x: 30, y: 135 }, c: { x: 56, y: 94 }, d: { x: 88, y: 115 } },
  { a: { x: 88, y: 115 }, b: { x: 118, y: 144 }, c: { x: 156, y: 144 }, d: { x: 185, y: 133 } },
  { a: { x: 185, y: 133 }, b: { x: 226, y: 111 }, c: { x: 268, y: 132 }, d: { x: 275, y: 160 } },
  { a: { x: 275, y: 160 }, b: { x: 252, y: 139 }, c: { x: 122, y: 155 }, d: { x: 128, y: 169 } },
  { a: { x: 128, y: 169 }, b: { x: 137, y: 212 }, c: { x: 177, y: 223 }, d: { x: 212, y: 207 } },
  { a: { x: 212, y: 207 }, b: { x: 249, y: 194 }, c: { x: 280, y: 183 }, d: { x: 300, y: 188 } },
];

function layer(curve: number, t: number, release: number) {
  'worklet';
  if (curve === 4) return release < 0.19;
  if (curve === 5) return true;
  return curve === 6 || (curve === 7 && t < 0.5);
}

function makeGuide(curves: Curve[], release = 0): Guide {
  const points: GuidePoint[] = [{ distance: 0, center: curves[0].a, tangent: cubicTangent(curves[0], 0), facing: 1, foreground: false }];
  let distance = 0;
  let prior = curves[0].a;
  let seam = 0;
  let feedLength = 0;
  const boundaries: number[] = [];
  for (let curve = 0; curve < curves.length; curve += 1) {
    for (let index = 1; index <= 64; index += 1) {
      const t = index / 64;
      const center = cubic(curves[curve], t);
      distance += Math.hypot(center.x - prior.x, center.y - prior.y);
      points.push({ distance, center, tangent: cubicTangent(curves[curve], t), facing: facing(curve, t, release),
        foreground: curves.length === 6 ? curve >= 4 : layer(curve, t, release) });
      prior = center;
      if (curve === 7 && index === 32) boundaries.push(distance);
    }
    if (curve === 3) { seam = distance; boundaries.push(distance); }
    if (curve === 5) { feedLength = distance; boundaries.push(distance); }
  }
  return { points, seam, length: distance, feedLength, boundaries };
}

function lerp(a: number, b: number, q: number) { 'worklet'; return a + (b - a) * q; }
function pointLerp(a: StreamerPoint, b: StreamerPoint, q: number): StreamerPoint { 'worklet'; return { x: lerp(a.x, b.x, q), y: lerp(a.y, b.y, q) }; }

function returnCurves(release: number): Curve[] {
  const lift = smooth(release / 0.38);
  const curves = CURVES.map(curve => ({ ...curve }));
  curves[3] = { ...curves[3], c: pointLerp(curves[3].c, { x: 116, y: 138 }, lift), d: pointLerp(curves[3].d, { x: 138, y: 145 }, lift) };
  curves[4] = { a: curves[3].d, b: pointLerp(curves[4].b, { x: 160, y: 152 }, lift),
    c: pointLerp(curves[4].c, { x: 230, y: 151 }, lift), d: pointLerp(curves[4].d, { x: 250, y: 172 }, lift) };
  curves[5] = { a: curves[4].d, b: pointLerp(curves[5].b, { x: 267, y: 190 }, lift),
    c: pointLerp(curves[5].c, { x: 281, y: 198 }, lift), d: curves[5].d };
  const outgoing = sub(curves[5].d, curves[5].c);
  return [...curves,
    { a: { x: 300, y: 188 }, b: add(curves[5].d, outgoing), c: { x: 337, y: 174 }, d: { x: 340, y: 194 } },
    { a: { x: 340, y: 194 }, b: { x: 346, y: 234 }, c: { x: 305, y: 202 }, d: { x: 275, y: 142 } },
    { a: { x: 275, y: 142 }, b: { x: 252, y: 119 }, c: { x: 205, y: 116 }, d: { x: 178, y: 128 } },
    { a: { x: 178, y: 128 }, b: { x: 130, y: 151 }, c: { x: 105, y: 132 }, d: { x: 70, y: 115 } },
    { a: { x: 70, y: 115 }, b: { x: 42, y: 100 }, c: { x: 30, y: 135 }, d: STREAMER_FEED_JOINT },
  ];
}

// Cache a bounded family of arc-length guides once. Frame work interpolates corresponding stations;
// it never rebuilds/allocates complete sampled curves or adds a second paper strand.
const GUIDE = makeGuide(CURVES);
const RETURN_GUIDES = Array.from({ length: 17 }, (_, index) => makeGuide(returnCurves(index / 16), index / 16));
export const STREAMER_GUIDE_LENGTH = GUIDE.length;
export const STREAMER_SEAM_DISTANCE = GUIDE.seam;

type GuidePair = { a: Guide; b: Guide; mix: number };
function guidePair(release: number): GuidePair {
  'worklet';
  if (release < 0) return { a: GUIDE, b: GUIDE, mix: 0 };
  const q = clamp(release) * 16;
  const low = Math.min(15, Math.floor(q));
  return { a: RETURN_GUIDES[low], b: RETURN_GUIDES[low + 1], mix: q - low };
}
function sample(distance: number, release = -1): GuidePoint {
  'worklet';
  const { a: ga, b: gb, mix } = guidePair(release);
  const d = clamp(distance, 0, lerp(ga.length, gb.length, mix));
  let low = 0;
  let high = ga.points.length - 1;
  while (low + 1 < high) {
    const mid = Math.floor((low + high) / 2);
    if (lerp(ga.points[mid].distance, gb.points[mid].distance, mix) <= d) low = mid;
    else high = mid;
  }
  const a0 = ga.points[low]; const a1 = gb.points[low];
  const b0 = ga.points[high]; const b1 = gb.points[high];
  const q = (d - lerp(a0.distance, a1.distance, mix)) / Math.max(0.000001, lerp(b0.distance, b1.distance, mix) - lerp(a0.distance, a1.distance, mix));
  const foreground = release < 0 ? d >= GUIDE.seam : layer(Math.floor((low + 0.001) / 64), ((low + q) % 64) / 64, release);
  return { distance: d, center: pointLerp(pointLerp(a0.center, a1.center, mix), pointLerp(b0.center, b1.center, mix), q),
    tangent: unit(pointLerp(pointLerp(a0.tangent, a1.tangent, mix), pointLerp(b0.tangent, b1.tangent, mix), q)),
    facing: lerp(lerp(a0.facing, a1.facing, mix), lerp(b0.facing, b1.facing, mix), q), foreground };
}

function station(distance: number, opening: number, headDistance: number, release = -1): StreamerStation {
  'worklet';
  const point = sample(distance, release);
  const projected = 0.8 + (STREAMER_FACE_WIDTH - 0.8) * Math.abs(point.facing) * opening;
  const baseWidth = 1.3 + (projected - 1.3) * smooth(distance / 22);
  return { center: point.center, tangent: point.tangent, width: baseWidth, widthAxis: normal(point.tangent),
    facing: point.facing, depth: 0, axisDepth: 0, joinBlend: smooth((distance - headDistance + 22) / 22) * smooth((headDistance - 45) / 65) };
}

export function streamerFrame(progress: number, pose = progress): StreamerFrame {
  'worklet';
  const release = pose >= 1 ? clamp(pose - 1) : -1;
  const pair = guidePair(release);
  const feedLength = lerp(pair.a.feedLength, pair.b.feedLength, pair.mix);
  const totalLength = lerp(pair.a.length, pair.b.length, pair.mix);
  const rightTurnEnd = release < 0 ? feedLength : lerp(pair.a.points[7 * 64].distance, pair.b.points[7 * 64].distance, pair.mix);
  const gather = release < 0 ? 0 : smooth((release - 0.78) / 0.22);
  const headDistance = release < 0 ? clamp(progress) * GUIDE.length
    : feedLength + (rightTurnEnd - feedLength) * smooth(release / 0.5) + (totalLength - rightTurnEnd) * gather;
  const tipLength = 36 * smooth((headDistance - 45) / 65) * (1 - gather);
  const bodyLength = release < 0 ? Math.min(headDistance, STREAMER_MATERIAL_LENGTH - tipLength) : 214 * (1 - gather);
  const tailDistance = Math.max(0, headDistance - bodyLength);
  const coiledLength = Math.max(0, STREAMER_MATERIAL_LENGTH - bodyLength - tipLength);
  const fullRadius = Math.sqrt(1.6 * 1.6 + 0.45 * STREAMER_MATERIAL_LENGTH / Math.PI);
  const radius = Math.sqrt(1.6 * 1.6 + 0.45 * coiledLength / Math.PI);
  const spans: StreamerSpan[] = [];
  const edgeSpans: StreamerSpan[] = [];
  const opening = smooth((headDistance - 24) / 50) * (1 - 0.96 * gather);
  if (bodyLength > 0.000001) {
    const distances = [tailDistance];
    const steps = Math.max(1, Math.ceil(bodyLength / 6.5));
    for (let index = 1; index < steps; index += 1) distances.push(tailDistance + bodyLength * index / steps);
    for (let index = 0; index < pair.a.boundaries.length; index += 1) {
      const boundary = lerp(pair.a.boundaries[index], pair.b.boundaries[index], pair.mix);
      if (boundary > tailDistance && boundary < headDistance) distances.push(boundary);
    }
    distances.push(headDistance);
    distances.sort((a, b) => a - b);
    for (let index = 1; index < distances.length; index += 1) {
      const a = distances[index - 1];
      const b = distances[index];
      if (b - a > 0.000001) spans.push({ a: station(a, opening, headDistance, release), b: station(b, opening, headDistance, release), foreground: sample((a + b) * 0.5, release).foreground });
    }
  }
  return { headDistance, tailDistance, bodyLength, tipLength, coiledLength, coilScale: radius / fullRadius, coilJoint: release < 0 ? STREAMER_FEED_JOINT : sample(headDistance, release).center,
    coilWidth: 1.3 + 0.8 * Math.sin(Math.PI * (1 - coiledLength / STREAMER_MATERIAL_LENGTH)), coilOpacity: 0.75 * (release < 0 ? smooth(coiledLength / 10) : smooth((gather - 0.72) / 0.28)), meshOpacity: smooth(headDistance / 8), spans, edgeSpans,
    roll: { center: sample(headDistance, release).center, tangent: sample(headDistance, release).tangent,
      width: station(headDistance, opening, headDistance, release).width, opacity: smooth(tipLength / 36) * (1 - smooth((gather - 0.58) / 0.24)), foreground: sample(headDistance, release).foreground } };
}

export function streamerEdge(point: StreamerStation, side: number): StreamerPoint {
  'worklet';
  return add(point.center, mul(point.widthAxis ?? normal(point.tangent), point.width * 0.5 * side));
}
