import { AlphaType, ColorType, Skia, type SkImage } from '@shopify/react-native-skia';

import { canonicalTime, clamp, columnDelay, hash, rotationMatrix, smooth, type Pose } from './motion';

type Point = { x: number; y: number };
type Nearest = { distance: number; point: Point; tangent: Point; along: number };
export type Resources = { atlas: SkImage; grain: SkImage; shadow: SkImage; arcs: number[]; completion: number[]; delays: number[] };
function cubic(a: Point, b: Point, c: Point, d: Point, t: number): Point {
  const s = 1 - t; return { x: s * s * s * a.x + 3 * s * s * t * b.x + 3 * s * t * t * c.x + t * t * t * d.x, y: s * s * s * a.y + 3 * s * s * t * b.y + 3 * s * t * t * c.y + t * t * t * d.y };
}
function transformed(p: Point, f: number) { const spread = .08 + .92 * smooth(f), top = clamp((6.3 - p.y) / 5.8); return { x: 3.1 + (p.x - 3.1) * spread, y: p.y - 1.8 * (1 - f) * top }; }
function frontCurve(f: number) {
  const segments = [ [[.8, 6.3], [1.8, 5.7], [1.2, 3.8], [1.2, 2]], [[1.2, 2], [1.65, .2], [4.35, .2], [4.8, 2]], [[4.8, 2], [4.8, 3.8], [4.4, 5.7], [5.4, 6.3]], [[5.4, 6.3], [6.1, 7.1], [6.9, 7], [6.9, 6]] ];
  return segments.flatMap(points => Array.from({ length: 13 }, (_, i) => { const [a, b, c, d] = points.map(([x, y]) => ({ x, y })); return transformed(cubic(a, b, c, d, i / 12), f); }));
}
function backCurve(f: number) { return Array.from({ length: 33 }, (_, i) => { const t = i / 32, s = 1 - t; return transformed({ x: s * s * -.7 + 2 * s * t * 3.1 + t * t * 6.9, y: s * s * 3.7 + 2 * s * t * 8 + t * t * 3.7 }, f); }); }
function nearest(p: Point, path: Point[]): Nearest {
  let best = Infinity, result = { x: 0, y: 0 }, tangent = { x: 0, y: 1 }, along = 0;
  for (let i = 1; i < path.length; i++) { const a = path[i - 1], b = path[i], dx = b.x - a.x, dy = b.y - a.y, t = clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / Math.max(.000001, dx * dx + dy * dy)), q = { x: a.x + dx * t, y: a.y + dy * t }, distance = Math.hypot(p.x - q.x, p.y - q.y); if (distance < best) { best = distance; result = q; const length = Math.max(.000001, Math.hypot(dx, dy)); tangent = { x: dx / length, y: dy / length }; along = i - 1 + t; } }
  return { distance: best, point: result, tangent, along };
}
function byte(v: number) { return Math.round(clamp(v) * 255); }
function raster(bytes: Uint8Array, width: number, height: number): SkImage {
  // MakeImage has no color-space tag: the material child preserves exact data bytes.
  const data = Skia.Data.fromBytes(bytes), image = Skia.Image.MakeImage({ width, height, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul }, data, width * 4); data.dispose();
  if (!image) throw new Error('Stitch Weave Preview: atlas creation failed.'); return image;
}
export function makeResources(): Resources {
  const width = 512, height = 256, pixels = new Uint8Array(width * height * 4);
  for (let i = 0; i < pixels.length; i += 4) { pixels[i + 1] = 128; pixels[i + 2] = 128; pixels[i + 3] = 255; }
  for (let frame = 0; frame < 16; frame++) {
    const f = frame / 15, front = frontCurve(f), back = backCurve(f);
    for (let y = 0; y < 40; y++) for (let x = 0; x < 32; x++) {
      const p = { x: (x + .5) / 3 - 2.2, y: (y + .5) / 3 - 2.4 }, a = nearest(p, front), b = nearest(p, back), radius = .98, side = (p.x - a.point.x) * -a.tangent.y + (p.y - a.point.y) * a.tangent.x;
      const fibers = .5 + .5 * Math.cos(side * 14.3 + .25 * Math.sin(a.along * .9)), fringe = .10 * clamp((radius + .38 - a.distance) / .38) * (.45 + .55 * hash(x * 37 + y * 103)), core = clamp((radius + .17 - a.distance) / .34), aa = core + fringe * (1 - core), ba = clamp((radius + .19 - b.distance) / .38) * .96, coverage = (aa + ba * (1 - aa)) * smooth(f * 6), near = aa > .02 ? a : b;
      const nx = clamp((p.x - near.point.x) / radius, -1, 1), ny = clamp((p.y - near.point.y) / radius, -1, 1), index = ((Math.floor(frame / 8) * 40 + y) * width + (frame % 8) * 32 + x) * 4;
      pixels[index] = byte(coverage); pixels[index + 1] = byte(nx * .5 + .5); pixels[index + 2] = byte(ny * .5 + .5);
      const fi = index + 256 * 4; pixels[fi] = byte(fibers); pixels[fi + 1] = byte(fringe); pixels[fi + 2] = byte(.5 + .5 * Math.sin(side * 20 + a.along * 1.2)); pixels[fi + 3] = 255;
    }
  }
  const arcs = Array<number>(65 * 129).fill(0);
  for (let ai = 0; ai < 65; ai++) {
    const a = ai / 64 * 17, samples = Array<number>(129).fill(0), integrand = (u: number) => { const dz = 2 * Math.PI * a * Math.cos(2 * Math.PI * u + .6); return Math.sqrt(322.4 * 322.4 - dz * dz); };
    for (let col = 1; col < 129; col++) { const l = (col - 1) / 128, r = col / 128, mid = (l + r) / 2; samples[col] = samples[col - 1] + (r - l) / 6 * (integrand(l) + 4 * integrand(mid) + integrand(r)); }
    const center = samples[128] / 2;
    for (let col = 0; col < 129; col++) { const x = samples[col] - center, baseline = (col / 128 - .5) * 322.4, normalized = clamp((x - baseline) / 16 + .5), packed = Math.round(normalized * 65535), index = ((128 + ai) * width + col) * 4; pixels[index] = packed >> 8; pixels[index + 1] = packed & 255; pixels[index + 2] = 0; pixels[index + 3] = 255; arcs[ai * 129 + col] = baseline + (packed / 65535 - .5) * 16; }
  }
  for (let row = 0; row < 48; row++) for (let col = 0; col < 52; col++) {
    const point = { x: (col + .5) * 6.2, y: (47.5 - row) * 6.8 }, segment = (u: number, v: number, x: number, y: number) => nearest(point, [{ x: u * 322.4, y: v * 326.4 }, { x: x * 322.4, y: y * 326.4 }]).distance;
    let sprig = segment(.5, .28, .5, .73), ripple = Infinity;
    for (let i = 0; i < 3; i++) { let v = .40 + i * .12; sprig = Math.min(sprig, segment(.5, v, .33, v - .085), segment(.5, v, .67, v - .085)); v = .30 + i * .16; ripple = Math.min(ripple, segment(.24, v, .50, v + .1), segment(.50, v + .1, .76, v)); }
    const index = ((208 + row) * width + col) * 4; pixels[index] = byte(1 - smooth((sprig - 6.8) / .9)); pixels[index + 1] = byte(1 - smooth((ripple - 6.8) / .9)); pixels[index + 2] = 0; pixels[index + 3] = 255;
  }
  // Static completion/delay values live in unused atlas row200: 24-bit data, no dynamic uniform indexing.
  const pack24 = (x: number, value: number) => { const packed = Math.round(clamp(value) * 16777215), i = (200 * width + x) * 4; pixels[i] = packed >>> 16; pixels[i + 1] = (packed >>> 8) & 255; pixels[i + 2] = packed & 255; pixels[i + 3] = 255; };
  for (let row = 0; row < 48; row++) pack24(row, canonicalTime(clamp((row + 1 - 3) / 45)) / 1.9);
  for (let col = 0; col < 52; col++) pack24(64 + col, (columnDelay(col) + .004) / .0155);
  const noise = new Uint8Array(128 * 128 * 4).fill(255); for (let i = 0; i < 128 * 128; i++) { const n = byte(hash(i + 321)); noise[i * 4] = n; noise[i * 4 + 1] = n; noise[i * 4 + 2] = n; }
  const shade = new Uint8Array(256 * 96 * 4); for (let y = 0; y < 96; y++) for (let x = 0; x < 256; x++) { const dx = (x - 127.5) / 73, dy = (y - 47.5) / 18; shade[(y * 256 + x) * 4 + 3] = byte(Math.exp(-.5 * (dx * dx + dy * dy))); }
  return { atlas: raster(pixels, width, height), grain: raster(noise, 128, 128), shadow: raster(shade, 256, 96), arcs, completion: Array.from({ length: 48 }, (_, i) => canonicalTime(clamp((i + 1 - 3) / 45))), delays: Array.from({ length: 52 }, (_, i) => columnDelay(i)) };
}
export function arc(arcs: number[], u: number, amplitude: number) {
  'worklet'; const x = clamp(u) * 128, a = clamp(amplitude / 17) * 64, xi = Math.min(127, Math.floor(x)), ai = Math.min(63, Math.floor(a)), xf = x - xi, af = a - ai;
  const first = arcs[ai * 129 + xi] * (1 - xf) + arcs[ai * 129 + xi + 1] * xf, next = arcs[(ai + 1) * 129 + xi] * (1 - xf) + arcs[(ai + 1) * 129 + xi + 1] * xf; return first * (1 - af) + next * af;
}
export function project(arcs: number[], u: number, v: number, p: Pose): Point {
  'worklet'; const amplitude = (8 + 9 * p.p) * Math.sin(Math.PI * v), x = arc(arcs, u, Math.max(0, amplitude)), y = 245.6 + 326.4 * v - 420 + p.hem, lead = 1 - (3 + 45 * p.p) / 48, weight = smooth(1 - Math.max(0, (v - lead) * 326.4) / 256), z = amplitude * Math.sin(2 * Math.PI * u + .6) + p.lift * weight, a = rotationMatrix(p.rotation), b = rotationMatrix(p.lagRotation), lag = clamp(1 - v);
  const row = (i: number) => { 'worklet'; const n = i * 3; return (a[n] + (b[n] - a[n]) * lag) * x + (a[n + 1] + (b[n + 1] - a[n + 1]) * lag) * y + (a[n + 2] + (b[n + 2] - a[n + 2]) * lag) * z; }, perspective = 950 / (950 - row(2)); return { x: 196.5 + row(0) * perspective, y: 420 + row(1) * perspective };
}
