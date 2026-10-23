import { ClipOp, PaintStyle, Skia, type SkCanvas, type SkPaint, type SkPath, TileMode } from '@shopify/react-native-skia';

import { CAMERAS, type Camera, HEIGHTS, ROLLS, SCENES, type V3, add, cross, dot, look, mul, normalize, smoothstep, sub } from './camera-model';

/*
 Drawing for Camera Carousel Fan (a port of CameraCarouselFanRender in CameraCarouselFan.swift):
 the camera's faces are rotated, projected in perspective (focal 760 pt), shaded (Lambert + Blinn)
 and painted back to front together with the eight photos on their ring. Every function runs on
 the UI thread.
 */

export type Shown = { p: number; clock: number; idle: number; photoP: number[] };

type M3 = number[];

export function m3x(a: number): M3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [1, 0, 0, 0, c, -s, 0, s, c];
}
export function m3y(a: number): M3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [c, 0, s, 0, 1, 0, -s, 0, c];
}
function m3z(a: number): M3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [c, -s, 0, s, c, 0, 0, 0, 1];
}
export function m3mul(a: M3, b: M3): M3 {
  'worklet';
  const o = [0, 0, 0, 0, 0, 0, 0, 0, 0];
  for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
  }
  return o;
}
function apply(m: M3, v: V3): V3 {
  'worklet';
  return [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
}

const FOCAL = 760;
const LIGHT = normalize([-0.5, 0.75, 0.6]);
const HALFWAY = normalize(add(LIGHT, [0, 0, 1]));

function color(c: V3, alpha = 1) {
  'worklet';
  const f = (x: number) => Math.min(1, Math.max(0, x));
  return Float32Array.of(f(c[0]), f(c[1]), f(c[2]), f(alpha));
}

function shade(c: V3, gloss: number, n: V3): V3 {
  'worklet';
  const lambert = Math.max(0, dot(n, LIGHT));
  const spec = Math.pow(Math.max(0, dot(n, HALFWAY)), 28) * gloss * 0.85;
  const rim = Math.pow(Math.max(0, 1 - Math.abs(n[2])), 4) * 0.08;
  const k = 0.26 + 0.84 * lambert;
  return [c[0] * k + spec + rim, c[1] * k + spec + rim, c[2] * k + spec + rim];
}

function project(q: V3, cx: number, cy: number) {
  'worklet';
  const k = FOCAL / (FOCAL - q[2]);
  return { x: cx + q[0] * k, y: cy - q[1] * k };
}

const GLASS: { pos: number[]; colors: V3[] }[] = [
  { pos: [0, 0.35, 0.75, 1], colors: [[0.99, 0.99, 0.98], [0.9, 0.88, 0.85], [0.6, 0.58, 0.57], [0.28, 0.27, 0.28]] },
  { pos: [0, 0.18, 0.6, 1], colors: [[0.6, 0.66, 0.82], [0.22, 0.26, 0.38], [0.05, 0.06, 0.1], [0.02, 0.02, 0.03]] },
  { pos: [0, 0.2, 0.65, 1], colors: [[0.58, 0.88, 0.84], [0.12, 0.36, 0.37], [0.03, 0.08, 0.09], [0.01, 0.02, 0.02]] },
];

type Drawn = { z: number; path: SkPath; paint: SkPaint; stroke: boolean; rim: number; decals: { path: SkPath; paint: SkPaint }[] };

/** A camera's visible faces, projected, with their depth. */
export function cameraFaces(cam: Camera, m: M3, scale: number, bob: number, cx: number, cy: number, alpha: number): Drawn[] {
  'worklet';
  const faces = cam.faces;
  const out: Drawn[] = [];
  const slot: number[] = new Array(faces.length).fill(-1);
  const view = (p: V3): V3 => {
    const q = apply(m, mul(p, scale));
    return [q[0], q[1] + bob, q[2]];
  };
  for (let i = 0; i < faces.length; i++) {
    const f = faces[i];
    const nv = apply(m, f.normal);
    const cv = view(f.center);
    if (dot(nv, sub([0, 0, FOCAL], cv)) <= 0) continue;
    if (f.parent >= 0 && slot[f.parent] < 0) continue;
    const path = Skia.Path.Make();
    for (let j = 0; j < f.pts.length; j++) {
      const o = project(view(f.pts[j]), cx, cy);
      if (j === 0) path.moveTo(o.x, o.y);
      else path.lineTo(o.x, o.y);
    }
    path.close();
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    if (f.glass >= 0) {
      const u = cross(f.axis, f.normal);
      const focus = project(view(add(f.center, mul(sub(f.axis, u), 0.32 * f.half))), cx, cy);
      const k = FOCAL / (FOCAL - cv[2]);
      const g = GLASS[f.glass];
      paint.setShader(
        Skia.Shader.MakeRadialGradient(focus, f.half * 1.45 * scale * k, g.colors.map((c) => color(c, alpha)), g.pos, TileMode.Clamp),
      );
    } else if (f.n0 && f.n1) {
      const a = project(view(mul(add(f.pts[0], f.pts[3]), 0.5)), cx, cy);
      const b = project(view(mul(add(f.pts[1], f.pts[2]), 0.5)), cx, cy);
      const c0 = color(shade(f.color, f.gloss, apply(m, f.n0)), alpha);
      const c1 = color(shade(f.color, f.gloss, apply(m, f.n1)), alpha);
      if (Math.hypot(b.x - a.x, b.y - a.y) < 0.5) paint.setColor(c0);
      else paint.setShader(Skia.Shader.MakeLinearGradient(a, b, [c0, c1], null, TileMode.Clamp));
    } else {
      const base = shade(f.color, f.gloss, nv);
      const top = project(view(add(f.center, mul(f.axis, f.half))), cx, cy);
      const bottom = project(view(sub(f.center, mul(f.axis, f.half))), cx, cy);
      paint.setShader(
        Skia.Shader.MakeLinearGradient(
          top,
          bottom,
          [color(add(mul(base, 1.12), [0.02, 0.02, 0.02]), alpha), color(mul(base, 0.86), alpha)],
          null,
          TileMode.Clamp,
        ),
      );
    }
    if (f.parent >= 0) {
      out[slot[f.parent]].decals.push({ path, paint });
      slot[i] = slot[f.parent];
    } else {
      slot[i] = out.length;
      const rim = f.glass < 0 && !f.n0 ? 0.16 * Math.max(0, dot(nv, LIGHT)) : 0;
      out.push({ z: cv[2] + f.bias * scale, path, paint, stroke: f.glass < 0, rim, decals: [] });
    }
  }
  return out;
}

export function drawFace(canvas: SkCanvas, d: Drawn) {
  'worklet';
  canvas.drawPath(d.path, d.paint);
  if (d.stroke) {
    const s = d.paint.copy();
    s.setStyle(PaintStyle.Stroke);
    s.setStrokeWidth(0.7);
    canvas.drawPath(d.path, s);
  }
  if (d.rim > 0.01) {
    const r = Skia.Paint();
    r.setAntiAlias(true);
    r.setStyle(PaintStyle.Stroke);
    r.setStrokeWidth(1);
    r.setColor(Float32Array.of(1, 1, 1, d.rim));
    canvas.drawPath(d.path, r);
  }
  for (const dc of d.decals) canvas.drawPath(dc.path, dc.paint);
}

/** One instant photo in a 64 × 78 frame: a 56 × 56 picture and a written caption. */
function drawPhoto(canvas: SkCanvas, scene: number, lk: number, front: boolean, fade: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Float32Array.of(0.96, 0.95, 0.92, fade));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, 64, 78), 2, 2), paint);
  if (!front) {
    paint.setColor(Float32Array.of(0, 0, 0, 0.08 * fade));
    canvas.drawRect(Skia.XYWHRect(8, 30, 48, 1.5), paint);
    return;
  }
  const c = (v: V3) => color(look(v, lk), fade);
  const px = (x: number) => 4 + x * 56;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(4, 4, 56, 56), ClipOp.Intersect, true);
  for (const op of SCENES[scene]) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    if (op.t === 'rect') {
      p.setColor(c(op.c));
      canvas.drawRect(Skia.XYWHRect(px(op.x), px(op.y), op.w * 56, op.h * 56), p);
    } else if (op.t === 'grad') {
      p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: px(op.y) }, { x: 0, y: px(op.y + op.h) }, [c(op.c), c(op.c2)], null, TileMode.Clamp));
      canvas.drawRect(Skia.XYWHRect(px(op.x), px(op.y), op.w * 56, op.h * 56), p);
    } else if (op.t === 'circle') {
      p.setColor(c(op.c));
      canvas.drawCircle(px(op.x), px(op.y), op.r * 56, p);
    } else {
      p.setColor(c(op.c));
      const path = Skia.Path.Make();
      for (let j = 0; j < op.pts.length; j += 2) {
        if (j === 0) path.moveTo(px(op.pts[j]), px(op.pts[j + 1]));
        else path.lineTo(px(op.pts[j]), px(op.pts[j + 1]));
      }
      path.close();
      canvas.drawPath(path, p);
    }
  }
  // A soft sheen across the print.
  const sheen = Skia.Paint();
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: px(0), y: px(0) },
      { x: px(0.6), y: px(0.6) },
      [Float32Array.of(1, 1, 1, 0.16 * fade), Float32Array.of(1, 1, 1, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(4, 4, 56, 56), sheen);
  canvas.restore();
  // The caption, as if written in pen.
  paint.setColor(Float32Array.of(0, 0, 0, 0.28 * fade));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(7, 66, 26, 2.2), 1.1, 1.1), paint);
  paint.setColor(Float32Array.of(0, 0, 0, 0.16 * fade));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(7, 71, 14, 2), 1, 1), paint);
  paint.setColor(Float32Array.of(0, 0, 0, 0.18 * fade));
  canvas.drawCircle(54.5, 68.5, 2.5, paint);
}

/** The whole picture. */
export function drawScene(canvas: SkCanvas, w: number, h: number, s: Shown, reduce: boolean) {
  'worklet';
  const scale = w / 393;
  const cx = w / 2;
  const cy = h * 0.35;
  const i = Math.min(4, Math.max(0, Math.round(s.p)));
  const frac = s.p - Math.round(s.p);
  const tau = 2 * Math.PI;

  // Spotlight in the accent of the cameras either side of p.
  const lo = Math.min(4, Math.max(0, Math.floor(s.p)));
  const hi = Math.min(4, lo + 1);
  const mixT = Math.min(1, Math.max(0, s.p - lo));
  const accent = add(mul(CAMERAS[lo].accent, 1 - mixT), mul(CAMERAS[hi].accent, mixT));
  const bg = Skia.Paint();
  bg.setColor(Float32Array.of(0.03, 0.03, 0.035, 1));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  const spot = Skia.Paint();
  spot.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy - 10 * scale },
      270 * scale,
      [color(accent, 0.26), color(accent, 0.08), color(accent, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), spot);
  // A faint floor under the camera.
  canvas.save();
  canvas.translate(cx, cy + 118 * scale);
  canvas.scale(1, 0.16);
  const floor = Skia.Paint();
  floor.setShader(
    Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, 150 * scale, [Float32Array.of(1, 1, 1, 0.1), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp),
  );
  canvas.drawCircle(0, 0, 150 * scale, floor);
  canvas.restore();

  // The camera.
  const phi = reduce ? 0 : frac * Math.PI;
  const idleYaw = ((s.idle * 7 * Math.PI) / 180) * Math.sin((tau * s.clock) / 4);
  const bob = s.idle * 4 * Math.sin((tau * s.clock) / 4 + 1) * scale;
  const m = m3mul(m3mul(m3mul(m3y(idleYaw - 0.18), m3x(0.21)), m3x(phi)), m3y(0.35 * phi));
  const alpha = reduce ? 1 - 2 * Math.abs(frac) : 1;
  const faces = cameraFaces(CAMERAS[i], m, scale * 1.06, bob, cx, cy, alpha);

  // The photos on their ring.
  const ring = 172 * scale;
  const tilt = m3x(0.26);
  const sway = reduce ? 0 : -s.p * 0.75 * Math.PI + s.idle * 0.1 * Math.sin((tau * s.clock) / 8);
  const placed: { c: V3; ex: V3; ey: V3; look: number; dim: number; fade: number }[] = [];
  for (let k = 0; k < 8; k++) {
    const a = sway + (k * tau) / 8;
    const pk = s.photoP[k];
    const kr = Math.round(pk);
    const flip = reduce ? 0 : (pk - kr) * Math.PI;
    const photoBob = s.idle * 3 * Math.sin((tau * s.clock) / 4 + k) * scale;
    const c = add(apply(tilt, [ring * Math.sin(a), HEIGHTS[k] * scale, ring * Math.cos(a)]), [0, photoBob + bob * 0.5, 0]);
    const rot = m3mul(m3mul(tilt, m3y(0.55 * Math.sin(a) + flip)), m3z((ROLLS[k] * Math.PI) / 180));
    const lk = CAMERAS[Math.min(4, Math.max(0, kr))].look;
    const dim = 0.42 + 0.58 * smoothstep(-ring, ring * 0.7, c[2]);
    placed.push({ c, ex: apply(rot, [1, 0, 0]), ey: apply(rot, [0, 1, 0]), look: lk, dim, fade: reduce ? 1 - 2 * Math.abs(pk - kr) : 1 });
  }

  // Back to front: faces by their index (>= 0), photos as -1 - k.
  const order: number[][] = [];
  for (let j = 0; j < faces.length; j++) order.push([faces[j].z, j]);
  for (let k = 0; k < placed.length; k++) order.push([placed[k].c[2], -1 - k]);
  order.sort((x, y) => x[0] - y[0]);
  const pw = 64 * scale;
  const ph = 78 * scale;
  for (const [, id] of order) {
    if (id >= 0) {
      drawFace(canvas, faces[id]);
      continue;
    }
    const k = -1 - id;
    const pl = placed[k];
    const tl = project(add(sub(pl.c, mul(pl.ex, pw / 2)), mul(pl.ey, ph / 2)), cx, cy);
    const tr = project(add(add(pl.c, mul(pl.ex, pw / 2)), mul(pl.ey, ph / 2)), cx, cy);
    const bl = project(sub(sub(pl.c, mul(pl.ex, pw / 2)), mul(pl.ey, ph / 2)), cx, cy);
    const a = (tr.x - tl.x) / 64;
    const b = (tr.y - tl.y) / 64;
    const c = (bl.x - tl.x) / 78;
    const d = (bl.y - tl.y) / 78;
    canvas.save();
    canvas.concat(Skia.Matrix([a, c, tl.x, b, d, tl.y, 0, 0, 1]));
    drawPhoto(canvas, k, pl.look, a * d - b * c > 0, pl.fade);
    const shadeP = Skia.Paint();
    shadeP.setColor(Float32Array.of(0, 0, 0, (1 - pl.dim) * pl.fade));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, 64, 78), 2, 2), shadeP);
    canvas.restore();
  }
}
