import { Skia, type SkPath } from '@shopify/react-native-skia';

import { BOULE_CY, BOWL_RX, BOWL_RY, LOOP, RIM_Y, hash } from './breadMotion';

/** A closed path through the dough's points, smoothed with quadratic segments through midpoints. */
export function doughPath(p: number[]): SkPath {
  'worklet';
  const n = p.length / 2;
  const b = Skia.PathBuilder.Make();
  const mx = (i: number) => (p[2 * (i % n)] + p[2 * ((i + 1) % n)]) / 2;
  const my = (i: number) => (p[2 * (i % n) + 1] + p[2 * ((i + 1) % n) + 1]) / 2;
  b.moveTo(mx(0), my(0));
  for (let i = 1; i <= n; i += 1) b.quadTo(p[2 * (i % n)], p[2 * (i % n) + 1], mx(i), my(i));
  b.close();
  return b.build();
}

/** The bowl's rim seen from slightly above: a flat ellipse. */
export const RIM_RY = 15;

export function bowlBack(): SkPath {
  return Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-BOWL_RX, RIM_Y - RIM_RY, BOWL_RX * 2, RIM_RY * 2), 180, 180).build();
}

/** The glass in front of the dough: the bowl's body from the near half of the rim down to the foot. */
export function bowlFront(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-BOWL_RX, RIM_Y);
  b.cubicTo(-BOWL_RX, RIM_Y + BOWL_RY * 0.75, -BOWL_RX * 0.55, RIM_Y + BOWL_RY, 0, RIM_Y + BOWL_RY);
  b.cubicTo(BOWL_RX * 0.55, RIM_Y + BOWL_RY, BOWL_RX, RIM_Y + BOWL_RY * 0.75, BOWL_RX, RIM_Y);
  b.addArc(Skia.XYWHRect(-BOWL_RX, RIM_Y - RIM_RY, BOWL_RX * 2, RIM_RY * 2), 0, 180);
  return b.build();
}

export function rimFront(): SkPath {
  return Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-BOWL_RX, RIM_Y - RIM_RY, BOWL_RX * 2, RIM_RY * 2), 0, 180).build();
}

/** Two long reflections down the glass, curved with the wall. */
export function glassStreaks(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-BOWL_RX + 14, RIM_Y + 18);
  b.cubicTo(-BOWL_RX + 12, RIM_Y + 70, -BOWL_RX + 34, RIM_Y + 112, -BOWL_RX * 0.42, RIM_Y + BOWL_RY - 14);
  b.moveTo(BOWL_RX - 22, RIM_Y + 24);
  b.cubicTo(BOWL_RX - 22, RIM_Y + 52, BOWL_RX - 30, RIM_Y + 74, BOWL_RX - 44, RIM_Y + 96);
  return b.build();
}

/** The glass foot ring. */
export function bowlFoot(): SkPath {
  return Skia.PathBuilder.Make().addOval(Skia.XYWHRect(-48, RIM_Y + BOWL_RY - 9, 96, 16)).build();
}

/** Seeded speckles (flour on raw dough, blisters on the crust) over the dough's area. */
export function speckles(seed: number, count: number, rMin: number, rMax: number): SkPath {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < count; i += 1) {
    const x = (hash(seed + i * 1.37) - 0.5) * 240;
    const y = -80 + hash(seed + i * 2.71) * (BOULE_CY + 10);
    b.addCircle(x, y, rMin + (rMax - rMin) * hash(seed + i * 4.13));
  }
  return b.build();
}

/**
 * The score across the boule's top as it blooms: a lens between two curves that opens by `open`
 * (0…1). `top` is the boule's top y.
 */
export function scorePath(top: number, open: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const y0 = top + 38;
  const w = 1 + 13 * open;
  b.moveTo(-62, y0 + 18);
  b.cubicTo(-30, y0 - 6 - w * 0.6, 30, y0 - 14 - w * 0.6, 64, y0 - 2);
  b.cubicTo(30, y0 - 14 + w, -30, y0 - 6 + w, -62, y0 + 18);
  b.close();
  return b.build();
}

/** The ear: the score's lifted upper lip, a lit edge. */
export function earPath(top: number, open: number): SkPath {
  'worklet';
  const y0 = top + 38;
  const w = 1 + 13 * open;
  return Skia.PathBuilder.Make()
    .moveTo(-60, y0 + 17)
    .cubicTo(-30, y0 - 6 - w * 0.6, 30, y0 - 14 - w * 0.6, 62, y0 - 2)
    .build();
}

/** Three steam wisps rising from the crust; waves move with whole multiples of the loop. */
export function steamPath(t: number, top: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  for (let w = 0; w < 3; w += 1) {
    const x0 = (w - 1) * 44 + 6;
    const ph = (2 * Math.PI * (3 + w) * t) / LOOP + w * 1.7;
    for (let j = 0; j <= 12; j += 1) {
      const y = top - 6 - j * 11;
      const x = x0 + (4 + j * 0.9) * Math.sin(j * 0.55 - ph) + j * (w - 1) * 1.2;
      if (j === 0) b.moveTo(x, y);
      else b.lineTo(x, y);
    }
  }
  return b.build();
}

/** A check mark for the ready badge, centred on (0, 0). */
export const CHECK = Skia.PathBuilder.Make().moveTo(-6.5, 0.5).lineTo(-2, 5).lineTo(7, -5).build();

/** Grain for the backdrop: a soft-light hash. */
export const GRAIN_SKSL = `
uniform float amount;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float n = h(floor(p * 1.4)) * 0.6 + h(floor(p * 0.4) + 19.0) * 0.4;
  return half4(half3(n), 1.0) * half(amount);
}
`;

/** A linen cloth on the counter under the bowl, hanging over the edge with a soft wavy hem. */
export function clothPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const top = BOWL_RY - 12;
  b.moveTo(-158, top);
  b.lineTo(158, top);
  b.cubicTo(166, top + 20, 176, top + 40, 182, top + 64);
  const hem = top + 64;
  for (let k = 0; k < 6; k += 1) {
    const x0 = 182 - k * 60.7;
    b.quadTo(x0 - 30, hem + (k % 2 === 0 ? 7 : -3), x0 - 60.7, hem);
  }
  b.cubicTo(-176, top + 40, -166, top + 20, -158, top);
  b.close();
  return b.build();
}

/** Soft folds running down the cloth's hanging part. */
export function clothFolds(): SkPath {
  const b = Skia.PathBuilder.Make();
  for (const x of [-120, -52, 30, 104]) {
    b.moveTo(x, BOWL_RY + 14);
    b.quadTo(x + 6, BOWL_RY + 34, x + 2, BOWL_RY + 56);
  }
  return b.build();
}
