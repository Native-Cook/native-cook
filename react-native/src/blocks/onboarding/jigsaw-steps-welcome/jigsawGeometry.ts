import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkImage, type SkPath } from '@shopify/react-native-skia';

/*
 Jigsaw Steps Welcome — layout, the twelve piece outlines, where each piece lies when scattered,
 and the static art (paper background, the board's recessed tray) baked once.

 The board is 4 × 3 cells over a 4:3 photo. Every inner edge carries one classic knob whose side
 is chosen by a seeded hash; the knob is four cubic Béziers over the edge (neck at 39 – 61 % of
 the edge, a round head 27 % of the edge tall). Paths are built in screen space at the piece's
 home, so drawing a piece elsewhere is only a transform.
 */

export const COLS = 4;
export const ROWS = 3;
export const COUNT = COLS * ROWS;

/** Which pieces each onboarding step snaps in: corners, then the outer middles, then the middle row. */
export const STEPS = [
  [0, 3, 8, 11],
  [1, 2, 9, 10],
  [4, 5, 6, 7],
];

export type JigsawLayout = {
  W: number;
  H: number;
  board: { x: number; y: number; w: number; h: number };
  cell: { w: number; h: number };
  /** Home centres of the cells, and the scattered rest pose of each piece: x, y, rotation (rad). */
  home: number[][];
  scatter: number[][];
  textY: number;
  ctaY: number;
};

export const TOP_BAR = 44;
export const TEXT_H = 104;
export const CTA_H = 52;

function hash(n: number) {
  const v = Math.sin(n * 12.9898 + 4.1414) * 43758.5453;
  return v - Math.floor(v);
}

export function layoutJigsaw(W: number, H: number, top: number, bottom: number): JigsawLayout {
  const bx = 20;
  const bw = W - 40;
  const bh = bw * 0.75;
  const by = top + 8 + TOP_BAR + 12;
  const ctaY = H - bottom - 16 - CTA_H;
  const textY = ctaY - 20 - TEXT_H;
  const cw = bw / COLS;
  const ch = bh / ROWS;
  const home: number[][] = [];
  for (let r = 0; r < ROWS; r += 1) for (let c = 0; c < COLS; c += 1) home.push([bx + (c + 0.5) * cw, by + (r + 0.5) * ch]);
  // The pile: a loose 4 × 3 grid under the board, shuffled so no piece waits under its own column.
  const zx0 = bx + cw * 0.42;
  const zx1 = bx + bw - cw * 0.42;
  const zy0 = by + bh + ch * 0.18;
  const zy1 = Math.max(zy0 + ch * 0.6, textY - ch * 0.42);
  const spot = [7, 2, 10, 0, 5, 11, 3, 8, 1, 9, 4, 6];
  const scatter = home.map((_, i) => {
    const s = spot[i];
    const gc = s % 4;
    const gr = Math.floor(s / 4);
    const x = zx0 + ((zx1 - zx0) * gc) / 3 + (hash(i + 1) - 0.5) * 18;
    const y = zy0 + ((zy1 - zy0) * gr) / 2 + (hash(i + 21) - 0.5) * 14;
    const rot = (hash(i + 41) - 0.5) * 0.9;
    return [x, y, rot];
  });
  return { W, H, board: { x: bx, y: by, w: bw, h: bh }, cell: { w: cw, h: ch }, home, scatter, textY, ctaY };
}

// The knob along a unit edge: x along it, y outwards. Four cubics after the start point.
const KNOB = [
  [0.3, 0.0, 0.42, -0.04, 0.39, 0.08],
  [0.36, 0.2, 0.41, 0.27, 0.5, 0.27],
  [0.59, 0.27, 0.64, 0.2, 0.61, 0.08],
  [0.58, -0.04, 0.7, 0.0, 1.0, 0.0],
];

type Builder = ReturnType<typeof Skia.PathBuilder.Make>;

/**
 * One edge from a to b; sign +1 knob out of the piece, -1 into it, 0 straight. `back` is true for
 * the bottom and left edges, which neighbours walk the other way: the knob's sideways shift flips
 * so both pieces share exactly the same curve.
 */
function edge(b: Builder, ax: number, ay: number, bx: number, by: number, sign: number, jitter: number, back: boolean) {
  if (sign === 0) {
    b.lineTo(bx, by);
    return;
  }
  const dx = bx - ax;
  const dy = by - ay;
  // Outward normal for a clockwise outline (screen y down).
  const nx = dy;
  const ny = -dx;
  const shift = (jitter - 0.5) * 0.08 * (back ? -1 : 1);
  const P = (u: number, v: number) => [ax + dx * (u + (u > 0.05 && u < 0.95 ? shift : 0)) + nx * v * sign, ay + dy * (u + (u > 0.05 && u < 0.95 ? shift : 0)) + ny * v * sign];
  for (const k of KNOB) {
    const [x1, y1] = P(k[0], k[1]);
    const [x2, y2] = P(k[2], k[3]);
    const [x3, y3] = P(k[4], k[5]);
    b.cubicTo(x1, y1, x2, y2, x3, y3);
  }
}

/** The twelve outlines at their homes. */
export function piecePaths(L: JigsawLayout): SkPath[] {
  const { board: B, cell } = L;
  // h[r][c]: edge under cell (r, c); +1 knob goes down. v[r][c]: edge right of (r, c); +1 knob goes right.
  const hE: number[][] = [];
  const vE: number[][] = [];
  for (let r = 0; r < ROWS; r += 1) {
    hE.push([]);
    vE.push([]);
    for (let c = 0; c < COLS; c += 1) {
      hE[r].push(hash(r * 7 + c * 3 + 1) > 0.5 ? 1 : -1);
      vE[r].push(hash(r * 5 + c * 11 + 2) > 0.5 ? 1 : -1);
    }
  }
  const paths: SkPath[] = [];
  for (let r = 0; r < ROWS; r += 1) {
    for (let c = 0; c < COLS; c += 1) {
      const x0 = B.x + c * cell.w;
      const y0 = B.y + r * cell.h;
      const x1 = x0 + cell.w;
      const y1 = y0 + cell.h;
      const top = r === 0 ? 0 : -hE[r - 1][c];
      const right = c === COLS - 1 ? 0 : vE[r][c];
      const bottom = r === ROWS - 1 ? 0 : hE[r][c];
      const left = c === 0 ? 0 : -vE[r][c - 1];
      const b = Skia.PathBuilder.Make().moveTo(x0, y0);
      edge(b, x0, y0, x1, y0, top, hash(r * 13 + c), false);
      edge(b, x1, y0, x1, y1, right, hash(r * 17 + c + 5), false);
      edge(b, x1, y1, x0, y1, bottom, hash(r * 13 + c + 13), true);
      edge(b, x0, y1, x0, y0, left, hash(r * 17 + c + 4), true);
      b.close();
      paths.push(b.build());
    }
  }
  return paths;
}

/** Cream paper, grain and the board's recessed tray with the faint cut lines printed in it. */
export function bakePaper(L: JigsawLayout, outlines: SkPath[], scale: number): SkImage | null {
  const s = Skia.Surface.Make(Math.ceil(L.W * scale), Math.ceil(L.H * scale));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  const { W, H, board: B } = L;
  const col = (x: string) => Skia.Color(x);
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: W * 0.4, y: H }, [col('#F7F1E7'), col('#F1E8DA'), col('#E9DDCB')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), bg);
  const warm = Skia.Paint();
  warm.setShader(Skia.Shader.MakeRadialGradient({ x: W * 0.15, y: H * 0.05 }, W * 1.1, [col('rgba(255,214,170,0.35)'), col('rgba(255,214,170,0)')], [0, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), warm);
  const rr = Skia.RRectXY(Skia.XYWHRect(B.x - 6, B.y - 6, B.w + 12, B.h + 12), 16, 16);
  const tray = Skia.Paint();
  tray.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: B.y }, { x: 0, y: B.y + B.h }, [col('#E2D5C1'), col('#DCCDB6'), col('#D6C6AD')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(rr, tray);
  // Recess: a dark inner edge at the top, a light lip at the bottom.
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  const inner = Skia.Paint();
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(10);
  inner.setColor(col('rgba(90,64,40,0.22)'));
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(B.x - 6, B.y - 9, B.w + 12, B.h + 12), 16, 16), inner);
  const cut = Skia.Paint();
  cut.setStyle(PaintStyle.Stroke);
  cut.setStrokeWidth(0.8);
  cut.setColor(col('rgba(110,82,56,0.24)'));
  for (const p of outlines) c.drawPath(p, cut);
  c.restore();
  const lip = Skia.Paint();
  lip.setStyle(PaintStyle.Stroke);
  lip.setStrokeWidth(1);
  lip.setColor(col('rgba(255,252,246,0.9)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(B.x - 6.5, B.y - 5.5, B.w + 13, B.h + 12.5), 16.5, 16.5), lip);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 9, 0, 0));
  grain.setAlphaf(0.07);
  grain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, W, H), grain);
  s.flush();
  return s.makeImageSnapshot();
}
