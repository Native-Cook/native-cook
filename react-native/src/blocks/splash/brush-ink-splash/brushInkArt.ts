import { Skia, type SkImage, type SkPoint } from '@shopify/react-native-skia';

import { enso, plan, type Plan, type Stroke, wordmark } from './brushInkMotion';
import { PAPER_SKSL } from './brushInkShader';

/*
 Layout and static art. The painted strokes are fixed geometry, built once: one triangle mesh for
 all of them, its texture coordinates in stroke space (see brushInkShader.ts). Per frame only the
 shader's uniforms change. The finger's stroke uses the same mesh builder in a worklet, rebuilt
 when the finger moves.
 */

/** How far past the stroke's nominal half-width the mesh reaches (room for the bleed). */
export const MARGIN = 1.7;

export type Layout = {
  width: number;
  height: number;
  enso: { x: number; y: number; r: number };
  word: { top: number; k: number };
  taglineTop: number;
  footerTop: number;
};

export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const r = Math.min(width * 0.34, 140, (height - top - bottom) * 0.18);
  const y = top + (height - top - bottom) * 0.38;
  const k = Math.min(1.3, width / 300);
  const wordTop = y + r + 44;
  return {
    width,
    height,
    enso: { x: width / 2, y, r },
    word: { top: wordTop, k },
    taglineTop: wordTop + 60 * k + 26,
    footerTop: height - Math.max(bottom, 16) - 34,
  };
}

export type Mesh = { vertices: SkPoint[]; textures: SkPoint[]; indices: number[] };

/** Adds one stroke to a mesh: two vertices per centreline point, two triangles per step. */
export function addStroke(mesh: Mesh, s: Stroke, index: number, start: number, margin: number) {
  'worklet';
  const n = s.xs.length;
  if (n < 2) return;
  const base = mesh.vertices.length;
  // The centreline runs on past both ends by a half-width, so the shader can round the caps.
  const sx = s.xs[0] - (s.xs[1] - s.xs[0]) / (Math.hypot(s.xs[1] - s.xs[0], s.ys[1] - s.ys[0]) || 1) * s.hw[0] * 1.2;
  const sy = s.ys[0] - (s.ys[1] - s.ys[0]) / (Math.hypot(s.xs[1] - s.xs[0], s.ys[1] - s.ys[0]) || 1) * s.hw[0] * 1.2;
  const ex = s.xs[n - 1] + (s.xs[n - 1] - s.xs[n - 2]) / (Math.hypot(s.xs[n - 1] - s.xs[n - 2], s.ys[n - 1] - s.ys[n - 2]) || 1) * s.hw[n - 1] * 1.2;
  const ey = s.ys[n - 1] + (s.ys[n - 1] - s.ys[n - 2]) / (Math.hypot(s.xs[n - 1] - s.xs[n - 2], s.ys[n - 1] - s.ys[n - 2]) || 1) * s.hw[n - 1] * 1.2;
  const xs = [sx, ...s.xs, ex];
  const ys = [sy, ...s.ys, ey];
  const hw = [s.hw[0], ...s.hw, s.hw[n - 1]];
  let along = start - s.hw[0] * 1.2;
  for (let i = 0; i < xs.length; i += 1) {
    if (i > 0) along += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
    const a = Math.max(0, i - 1);
    const b = Math.min(xs.length - 1, i + 1);
    let tx = xs[b] - xs[a];
    let ty = ys[b] - ys[a];
    const tl = Math.hypot(tx, ty) || 1;
    tx /= tl;
    ty /= tl;
    const off = hw[i] * margin;
    mesh.vertices.push({ x: xs[i] - ty * off, y: ys[i] + tx * off });
    mesh.vertices.push({ x: xs[i] + ty * off, y: ys[i] - tx * off });
    mesh.textures.push({ x: along, y: -margin + 4 * index });
    mesh.textures.push({ x: along, y: margin + 4 * index });
    if (i > 0) {
      const p = base + (i - 1) * 2;
      mesh.indices.push(p, p + 1, p + 2, p + 1, p + 3, p + 2);
    }
  }
}

/** The splash's strokes (ensō first, then the wordmark), their plan, and one mesh for all. */
export function buildSplash(l: Layout): { plan: Plan; mesh: Mesh; ensoEnd: { x: number; y: number; angle: number }; ensoStart: { x: number; y: number } } {
  const strokes: Stroke[] = [enso(l.enso.x, l.enso.y, l.enso.r), ...wordmark(l.width / 2, l.word.top, l.word.k)];
  const p = plan(strokes);
  const mesh: Mesh = { vertices: [], textures: [], indices: [] };
  strokes.forEach((s, i) => addStroke(mesh, s, i, p.starts[i], i === 0 ? MARGIN : 1.6));
  const e = strokes[0];
  const n = e.xs.length;
  return {
    plan: p,
    mesh,
    ensoStart: { x: e.xs[0], y: e.ys[0] },
    ensoEnd: { x: e.xs[n - 1], y: e.ys[n - 1], angle: Math.atan2(e.ys[n - 1] - e.ys[n - 4], e.xs[n - 1] - e.xs[n - 4]) },
  };
}

const paperEffect = Skia.RuntimeEffect.Make(PAPER_SKSL);

/** The rice paper, baked into one image at `scale` pixels per point. */
export function bakePaper(l: Layout, scale: number): SkImage | null {
  if (!paperEffect) return null;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(l.width * scale), Math.ceil(l.height * scale));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  const paint = Skia.Paint();
  paint.setShader(paperEffect.makeShader([l.width, l.height]));
  canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), paint);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}
