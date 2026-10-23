import { PathOp, Skia, type SkPath } from '@shopify/react-native-skia';

/**
 * Everything drawn on the ticket that does not move: its outline with the tear-off perforation,
 * the barcode on the stub, the sunflower mark, and the holographic foil shader.
 */

/** The ticket's outline: a rounded card with a notch at each end of the perforation and a row of
 * small punched holes between them, cut out so the background shows through. */
export function makeTicketShape(width: number, height: number, perfY: number, radius: number): SkPath {
  const card = Skia.PathBuilder.Make()
    .addRRect({ rect: { x: 0, y: 0, width, height }, rx: radius, ry: radius })
    .build();
  const cuts = Skia.PathBuilder.Make();
  const NOTCH = 13;
  cuts.addCircle(0, perfY, NOTCH);
  cuts.addCircle(width, perfY, NOTCH);
  const HOLE = 2.3;
  const STEP = 10;
  const from = NOTCH + 12;
  const count = Math.floor((width - from * 2) / STEP);
  const start = (width - count * STEP) / 2;
  for (let k = 0; k <= count; k += 1) cuts.addCircle(start + k * STEP, perfY, HOLE);
  return Skia.Path.MakeFromOp(card, cuts.build(), PathOp.Difference) ?? card;
}

/** A barcode of bars of 1–3 units, fixed by its seed so every ticket prints the same one. */
export function makeBarcode(width: number, height: number, seed: number): SkPath {
  let t = seed;
  const rand = () => {
    t = (t * 9301 + 49297) % 233280;
    return t / 233280;
  };
  const path = Skia.PathBuilder.Make();
  let x = 0;
  while (x < width) {
    const bar = 1 + Math.floor(rand() * 3);
    const gap = 1 + Math.floor(rand() * 2.4);
    if (x + bar > width) break;
    path.addRect({ x, y: 0, width: bar, height });
    x += bar + gap;
  }
  return path.build();
}

/** A sunflower mark: petals around a seed head, centred on (0, 0) with radius `r`. */
export function makeSunflower(r: number) {
  const PETALS = 14;
  const petals = Skia.PathBuilder.Make();
  const inner = r * 0.36;
  const half = r * 0.17;
  for (let k = 0; k < PETALS; k += 1) {
    const a = (k / PETALS) * Math.PI * 2;
    const c = Math.cos(a);
    const s = Math.sin(a);
    // A point (u along the petal, v across it) turned onto the petal's angle.
    const at = (u: number, v: number): [number, number] => [u * c - v * s, u * s + v * c];
    const [x0, y0] = at(inner, 0);
    const [x1, y1] = at(inner + (r - inner) * 0.35, half);
    const [x2, y2] = at(r - (r - inner) * 0.1, half * 0.55);
    const [x3, y3] = at(r, 0);
    const [x4, y4] = at(r - (r - inner) * 0.1, -half * 0.55);
    const [x5, y5] = at(inner + (r - inner) * 0.35, -half);
    petals.moveTo(x0, y0).cubicTo(x1, y1, x2, y2, x3, y3).cubicTo(x4, y4, x5, y5, x0, y0).close();
  }
  const head = Skia.PathBuilder.Make().addCircle(0, 0, r * 0.4).build();
  const seeds = Skia.PathBuilder.Make();
  // Seeds on a golden-angle spiral, the way a real seed head packs them.
  for (let k = 0; k < 22; k += 1) {
    const a = k * 2.39996;
    const d = Math.sqrt(k / 22) * r * 0.32;
    seeds.addCircle(Math.cos(a) * d, Math.sin(a) * d, r * 0.035);
  }
  return { petals: petals.build(), head, seeds: seeds.build() };
}

/**
 * Holographic scratch foil. Brushed silver with a diamond guilloché pressed into it, rainbow
 * bands that slide with `tilt` and `time`, a soft specular sweep and a few twinkling sparkles.
 * `dissolve` (0…1) eats the foil away along a noise field with a hot golden edge.
 * `time` is a phase in radians and only enters through sin(time · n), so it loops.
 */
export const FOIL_SHADER = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float time;
uniform float2 tilt;
uniform float dissolve;

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

half4 main(float2 xy) {
  float2 uv = xy / size;
  float n = noise(xy * 0.022) * 0.65 + noise(xy * 0.07) * 0.35;
  // Brushed metal: fine streaks running across the foil.
  float brushed = noise(float2(xy.x * 0.015, xy.y * 0.9)) * 0.6 + noise(float2(xy.x * 0.03, xy.y * 1.7)) * 0.4;

  // A fine diamond guilloché, pressed in so its lines catch a little light.
  float2 g = float2(xy.x + xy.y, xy.x - xy.y) / 13.0;
  float2 d = abs(fract(g) - 0.5);
  float line = 1.0 - smoothstep(0.0, 0.06, min(d.x, d.y));

  float band = uv.x * 0.7 + uv.y * 0.5 + n * 0.35 + tilt.x * 0.35 + tilt.y * 0.25 + sin(time) * 0.1;
  float3 rainbow = 0.72 + 0.28 * cos(6.28318 * (band + float3(0.0, 0.33, 0.67)));
  float3 silver = float3(0.76, 0.77, 0.80) + (brushed - 0.5) * 0.14 + (n - 0.5) * 0.08;
  float3 col = silver * mix(float3(1.0), rainbow, 0.45);
  col += line * 0.045;

  // A soft band of light that drifts with the time and follows the tilt.
  float lightAt = 0.55 + 0.5 * sin(time) + tilt.x * 0.4;
  float s = uv.x * 0.8 + uv.y * 0.45 - lightAt;
  col += exp(-s * s * 22.0) * float3(0.26, 0.26, 0.28);
  // Darker towards the edges, like foil that is not quite flat.
  col *= 1.0 - 0.12 * length(uv - 0.5);

  // Sparkles: a few cells hold a tiny four-point star that twinkles.
  float cellSize = 11.0;
  float2 cell = floor(xy / cellSize);
  float h = hash(cell);
  float2 at = (cell + 0.25 + 0.5 * float2(hash(cell + 7.1), hash(cell + 3.7))) * cellSize;
  float2 q = abs(xy - at);
  float star = max(0.0, 1.0 - length(q) / 1.8) + max(0.0, 1.0 - q.x / 0.7) * max(0.0, 1.0 - q.y / 4.5) + max(0.0, 1.0 - q.y / 0.7) * max(0.0, 1.0 - q.x / 4.5);
  float twinkle = step(0.965, h) * pow(0.5 + 0.5 * sin(time * 3.0 + h * 60.0), 4.0);
  col += min(star, 1.2) * twinkle * 0.9;

  float dn = noise(xy * 0.05) * 0.7 + noise(xy * 0.16) * 0.3;
  float edgeAt = dissolve * 1.3 - 0.15;
  float a = smoothstep(edgeAt - 0.02, edgeAt + 0.02, dn);
  float glow = (1.0 - smoothstep(0.0, 0.07, abs(dn - edgeAt))) * step(0.001, dissolve) * (1.0 - dissolve * 0.3);
  col = mix(col, float3(1.0, 0.9, 0.58), glow * 0.85);
  a = max(a, glow);
  return half4(half3(col * a), half(a));
}
`)!;
