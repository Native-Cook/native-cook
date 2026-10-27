// Isometric key geometry, drawn the way the reference illustration is shaded:
// flat faces, a hairline ink outline, a lit (left-facing) wall and an ink-black
// (right-facing) wall. Everything is precomputed once; only the body wall's top
// edge moves with the cap, so the press worklet only offsets numbers.

export type Point = [number, number];
type Sample = { p: Point; n: Point };

/** Key plate units: the face is a rounded square centred on (108, 65). */
export const CENTER: Point = [108, 65];
export const FACE_SIDE = 114;
export const FACE_RADIUS = 31;
/** Wall height of the key body, in plate units (scaled with the key). */
export const DEPTH = 14;
/** Raised symbols stand this far above the cap. */
export const GLYPH_RISE = 6.5;

/** Local plane → screen: the same projection the symbols always used. */
export function iso([x, y]: Point): Point {
  return [CENTER[0] + 0.94 * x - 0.94 * y, CENTER[1] + 0.53 * x + 0.53 * y];
}

function roundedRect(w: number, h: number, r: number, turn = 0, at: Point = [0, 0]): Sample[] {
  const out: Sample[] = [];
  const corners: [number, number, number][] = [
    [w / 2 - r, -h / 2 + r, -90],
    [w / 2 - r, h / 2 - r, 0],
    [-w / 2 + r, h / 2 - r, 90],
    [-w / 2 + r, -h / 2 + r, 180],
  ];
  const c = Math.cos(turn), s = Math.sin(turn);
  for (const [cx, cy, start] of corners) {
    for (let i = 0; i <= 12; i++) {
      const a = (start + i * 7.5) * Math.PI / 180;
      const nx = Math.cos(a), ny = Math.sin(a);
      const x = cx + r * nx, y = cy + r * ny;
      out.push({ p: [at[0] + x * c - y * s, at[1] + x * s + y * c], n: [nx * c - ny * s, nx * s + ny * c] });
    }
  }
  return out;
}

// A wall is visible when its outward normal points down the screen. It is lit
// when it faces left (the key light sits upper-left), ink when it faces right.
function label({ n: [nx, ny] }: Sample) {
  if (nx + ny <= 1e-6) return 0;
  return ny >= nx ? 1 : 2;
}

/** Contiguous visible runs of one label, in boundary order, endpoints shared. */
function runs(shape: Sample[], kind: 1 | 2): Point[][] {
  const labels = shape.map(label);
  const start = labels.findIndex((value, i) => value !== kind && labels[(i + 1) % labels.length] === kind);
  if (start < 0) return labels[0] === kind ? [shape.map(s => s.p)] : [];
  const result: Point[][] = [];
  let current: Point[] | null = null;
  for (let k = 1; k <= shape.length; k++) {
    const i = (start + k) % shape.length;
    if (labels[i] === kind) { current ??= [shape[(i - 1 + shape.length) % shape.length].p]; current.push(shape[i].p); }
    else if (current) { current.push(shape[i].p); result.push(current); current = null; }
  }
  if (current) result.push(current);
  return result;
}

const fmt = (v: number) => Math.round(v * 100) / 100;
function polyline(points: Point[], dy = 0, dx = 0) {
  return points.map(([x, y], i) => `${i ? 'L' : 'M'}${fmt(x + dx)} ${fmt(y + dy)}`).join('');
}
export function closed(points: Point[], dy = 0, dx = 0) { return `${polyline(points, dy, dx)}Z`; }
/** A wall between the same projected chain at two heights. */
export function wall(chain: Point[], top: number, bottom: number) {
  return `${polyline(chain, top)}${[...chain].reverse().map(([x, y]) => `L${fmt(x)} ${fmt(y + bottom)}`).join('')}Z`;
}

const face = roundedRect(FACE_SIDE, FACE_SIDE, FACE_RADIUS);
export const FACE_PATH = closed(face.map(s => iso(s.p)));
export const RING_PATH = closed(roundedRect(FACE_SIDE + 9, FACE_SIDE + 9, FACE_RADIUS + 4.5).map(s => iso(s.p)), DEPTH + 1.2);
/** Projected wall chains of the body: [lit, ink]. Flat [x, y, x, y…] for worklets. */
export const BODY_LIT = runs(face, 1)[0].map(iso);
export const BODY_INK = runs(face, 2)[0].map(iso);
export const BODY_LIT_FLAT = BODY_LIT.flat();
export const BODY_INK_FLAT = BODY_INK.flat();
/** The silhouette's lower edge at ground level, for the outline stroke. */
export const BODY_FOOT = closed([...BODY_INK, ...BODY_LIT.slice(1)], DEPTH).replace(/Z$/, '');
/** Front corner of the body, where the ink wall (right) meets the lit wall (left). */
export const FRONT = BODY_LIT[0];

/** The highlight that catches the two upper rims of the face. */
export const FACE_GLINT = (() => {
  const inner = roundedRect(FACE_SIDE - 9, FACE_SIDE - 9, FACE_RADIUS - 4.5);
  // Normals facing up the screen are the upper rims; walk them as one cyclic run.
  const up = inner.map(s => s.n[0] + s.n[1] < -0.35);
  const first = up.findIndex((value, i) => value && !up[(i - 1 + up.length) % up.length]);
  const top: Point[] = [];
  for (let k = 0; k < inner.length && up[(first + k) % inner.length]; k++) top.push(iso(inner[(first + k) % inner.length].p));
  return polyline(top);
})();

export type GlyphPart = { top: string; lit: string[]; ink: string[] };
export type Operation = '+' | '−' | '×' | '÷' | '=';

function part(shape: Sample[]): GlyphPart {
  const projected = (points: Point[]) => points.map(iso);
  return {
    top: closed(shape.map(s => iso(s.p)), -GLYPH_RISE),
    lit: runs(shape, 1).map(run => wall(projected(run), -GLYPH_RISE, 0)),
    ink: runs(shape, 2).map(run => wall(projected(run), -GLYPH_RISE, 0)),
  };
}

const UPRIGHT = -Math.PI / 4;
const BAR_LONG = 74, BAR = 21;
export const GLYPHS: Record<Operation, GlyphPart[]> = {
  '+': [roundedRect(BAR_LONG, BAR, BAR / 2, UPRIGHT), roundedRect(BAR, BAR_LONG, BAR / 2, UPRIGHT)].map(part),
  '×': [roundedRect(BAR_LONG, BAR, BAR / 2), roundedRect(BAR, BAR_LONG, BAR / 2)].map(part),
  '−': [roundedRect(BAR_LONG, BAR, BAR / 2)].map(part),
  '=': [roundedRect(BAR_LONG, BAR, BAR / 2, 0, [0, -15]), roundedRect(BAR_LONG, BAR, BAR / 2, 0, [0, 15])].map(part),
  '÷': [
    roundedRect(BAR_LONG, BAR - 2, (BAR - 2) / 2, UPRIGHT),
    roundedRect(20, 20, 10, 0, [Math.cos(-3 * Math.PI / 4) * 31, Math.sin(-3 * Math.PI / 4) * 31]),
    roundedRect(20, 20, 10, 0, [Math.cos(Math.PI / 4) * 31, Math.sin(Math.PI / 4) * 31]),
  ].map(part),
};
