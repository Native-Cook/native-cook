// Text Effect Keys: the scene, drawn into one Skia picture per frame on the UI thread. The parts that never move
// (page, nav, panel, key shadows and skirts, the composer's outline) are recorded once.

import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  type SkCanvas,
  Skia,
  type SkPaint,
  type SkPicture,
  StrokeCap,
  StrokeJoin,
  TileMode,
  vec,
} from '@shopify/react-native-skia';

import { effectPose, explodeAlpha, FIELD_BASE, type Frame, mix, seg } from './tek-motion';
import type { Res, Run } from './tek-text';
import { C, EF, G } from './tek-theme';

const hex = (h: string) => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
const INK = hex(C.ink);
const ON_LIME = hex(C.onLime);
const LIME = hex(C.lime);
const MUTE = hex(C.mute);
const RECEIVED = hex(C.received);

const rgba = (c: number[], a: number) => {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
};
const mixRgb = (a: number[], b: number[], k: number) => {
  'worklet';
  return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
};
const fill = (c: number[], a = 1) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(rgba(c, a));
  return p;
};

/** Cap height of a Geist run at its size. */
const cap = (run: Run) => {
  'worklet';
  return run.font.getSize() * 0.71;
};

const drawRun = (canvas: SkCanvas, run: Run, x: number, baseline: number, paint: SkPaint) => {
  'worklet';
  const pos = [];
  for (let i = 0; i < run.ids.length; i++) pos.push({ x: run.xs[i], y: 0 });
  canvas.drawGlyphs(run.ids, pos, x, baseline, run.font, paint);
};

/** A message bubble: radius 16, one squared corner (5). */
const bubble = (x: number, y: number, w: number, h: number, left: boolean, tail = 5) => {
  'worklet';
  const r = Math.min(16, h / 2, w / 2);
  const t = Math.min(tail, r);
  return {
    rect: Skia.XYWHRect(x, y, w, h),
    topLeft: vec(r, r),
    topRight: vec(r, r),
    bottomRight: left ? vec(r, r) : vec(t, t),
    bottomLeft: left ? vec(t, t) : vec(r, r),
  };
};

// ---------------------------------------------------------------------------------------------------------------
// static parts (recorded on the JS thread once the fonts are in)

export type Statics = { back: SkPicture; panel: SkPicture; composer: SkPicture };

export const makeStatics = (R: Res, navY: number): Statics => {
  const rec = Skia.PictureRecorder();
  // page + nav row
  let c = rec.beginRecording(Skia.XYWHRect(0, 0, 402, 3000));
  c.drawRect(Skia.XYWHRect(0, 0, 402, 3000), fill(hex(C.page)));
  const stroke = (col: number[], w: number) => {
    const p = fill(col);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(w);
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
    return p;
  };
  c.save();
  c.translate(G.receivedX - 8, navY + 4);
  c.drawPath(R.icons.chevron, stroke(INK, 2));
  c.restore();
  c.save();
  c.translate(201 - 16, navY);
  c.scale(32 / 24, 32 / 24);
  c.drawCircle(12, 12, 12, fill(hex(C.avatar)));
  const hair = fill([1, 1, 1], 0.06);
  hair.setStyle(PaintStyle.Stroke);
  hair.setStrokeWidth(0.5);
  c.drawCircle(12, 12, 11.75, hair);
  c.drawPath(R.icons.mono, fill(INK));
  c.restore();
  drawRun(c, R.name, 201 - R.name.w / 2, navY + 38 + cap(R.name) / 2 + 2, fill(INK));
  const back = rec.finishRecordingAsPicture();

  // composer: the plus disc and the field's outline
  c = rec.beginRecording(Skia.XYWHRect(0, 400, 402, 200));
  const pl = G.plus;
  c.drawCircle(pl.x + pl.d / 2, pl.y + pl.d / 2, pl.d / 2, fill(hex(C.disc)));
  c.save();
  c.translate(pl.x + pl.d / 2 - 12, pl.y + pl.d / 2 - 12);
  c.drawPath(R.icons.plus, stroke(hex(C.glyph), 1.5));
  c.restore();
  const fd = G.field;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(fd.x, fd.y, fd.w, fd.h), fd.r, fd.r), fill(hex(C.page)));
  const fs = fill(hex(C.fieldStroke));
  fs.setStyle(PaintStyle.Stroke);
  fs.setStrokeWidth(1);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(fd.x + 0.5, fd.y + 0.5, fd.w - 1, fd.h - 1), fd.r, fd.r), fs);
  const composer = rec.finishRecordingAsPicture();

  // panel: fill to the bottom edge, its top hairline, the format row, the keys' shadows and skirts
  c = rec.beginRecording(Skia.XYWHRect(0, G.panelY - 10, 402, 3000));
  c.drawRect(Skia.XYWHRect(0, G.panelY, 402, 3000), fill(hex(C.panel)));
  c.drawRect(Skia.XYWHRect(0, G.panelY, 402, 1), fill([1, 1, 1], 0.06));
  for (let k = 0; k < 4; k++) {
    const run = R.fmt[k];
    const x = G.fmtX[k] - run.w / 2;
    const base = G.fmtY + cap(run) / 2;
    drawRun(c, run, x, base, fill(INK));
    if (k === 2) c.drawRect(Skia.XYWHRect(x, base + 2.2, run.w, 1.3), fill(INK));
    if (k === 3) c.drawRect(Skia.XYWHRect(x - 1, base - 4.6, run.w + 2, 1.3), fill(INK));
  }
  for (let k = 0; k < 8; k++) {
    const x = G.keyX[k % 2];
    const y = G.keyY[Math.floor(k / 2)];
    const rr = (dy: number) => Skia.RRectXY(Skia.XYWHRect(x, y + dy, G.keyW, G.keyH), G.keyR, G.keyR);
    const amb = fill([0, 0, 0], 0.1);
    amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    c.drawRRect(rr(4), amb);
    const con = fill([0, 0, 0], 0.22);
    con.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1, true));
    c.drawRRect(rr(1), con);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + G.skirt, G.keyW, G.keyH - G.skirt), G.keyR, G.keyR), fill(hex(C.skirt)));
  }
  const panel = rec.finishRecordingAsPicture();
  return { back, panel, composer };
};

// ---------------------------------------------------------------------------------------------------------------
// the moving parts

/** Reduced motion: each key shows one still pose of its effect. [phase, amplitude] */
const STILL = [
  [0.5, 0.4],
  [0.57, 0.57],
  [0, 0],
  [0, 0],
  [0.215, 1],
  [0.27, 1],
  [0, 0],
  [0, 0],
];

const KEY_TOP = hex(C.keyTop);
const KEY_BOTTOM = hex(C.keyBottom);
const FACE_H = G.keyH - G.skirt;

/** A key's label performing its own effect (and a kick when a letter lands on it), centred at (cx, cy). */
const drawLabel = (canvas: SkCanvas, f: Frame, run: Run, k: number, cx: number, cy: number) => {
  'worklet';
  const n = run.ids.length;
  const u = run.font.getSize() / 15;
  const ch = cap(run);
  const base = cy + ch / 2;
  const x0 = cx - run.w / 2;
  const colour = mixRgb(INK, LIME, f.active[k]);
  const kick = f.kick[k];
  const still = f.still === 1;
  const p = still ? STILL[k][0] : f.keyP;
  // the chosen key holds its label still (lime) while the word performs its effect
  const amp = still ? STILL[k][1] : f.amp * (1 - f.active[k]);
  for (let i = 0; i < n; i++) {
    const a = effectPose(k, p, i, n, u, false);
    let dx = a.dx * amp;
    let dy = a.dy * amp;
    let rot = a.rot * amp;
    let sc = a.sc * amp;
    let w = a.w * amp;
    let blur = still ? 0 : a.blur * amp;
    let alpha = k === EF.explode && !still ? mix(1, explodeAlpha(p), 1 - f.active[k]) : 1;
    if (!still && kick >= 0 && kick < 1) {
      const b = effectPose(k, 0.05 + 0.9 * kick, i, n, u, false);
      dx += b.dx;
      dy += b.dy;
      rot += b.rot;
      sc += b.sc;
      w += b.w;
      blur = Math.max(blur, b.blur);
      if (k === EF.explode) alpha = Math.min(alpha, explodeAlpha(0.05 + 0.9 * kick));
    }
    if (alpha < 0.01) continue;
    // a scale grows the word from its centre
    const gx = x0 + run.xs[i] + run.ws[i] / 2;
    const px = cx + (gx - cx) * (1 + sc) + dx;
    const py = base - ch / 2 + dy;
    const paint = fill(colour, alpha);
    if (blur > 0.3) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    canvas.save();
    canvas.translate(px, py);
    canvas.rotate(rot, 0, 0);
    canvas.scale(1 + sc, 1 + sc);
    canvas.drawGlyphs([run.ids[i]], [{ x: -run.ws[i] / 2, y: ch / 2 }], 0, 0, run.font, paint);
    if (w > 0.05) {
      paint.setStyle(PaintStyle.Stroke);
      paint.setStrokeJoin(StrokeJoin.Round);
      paint.setStrokeWidth(w / (1 + sc));
      canvas.drawGlyphs([run.ids[i]], [{ x: -run.ws[i] / 2, y: ch / 2 }], 0, 0, run.font, paint);
    }
    canvas.restore();
  }
};

const drawKeys = (canvas: SkCanvas, f: Frame, R: Res) => {
  'worklet';
  // the lime under-glow of a pressed key, under every face, so it spills onto the neighbours' sides
  for (let k = 0; k < 8; k++) {
    const d = f.depth[k];
    if (d < 0.01) continue;
    const x = G.keyX[k % 2];
    const y = G.keyY[Math.floor(k / 2)] + 4 * d;
    const g = fill(LIME, 0.6 * Math.min(d, 1));
    g.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 2, y - 1, G.keyW + 4, FACE_H + 6), G.keyR + 2, G.keyR + 2), g);
  }
  for (let k = 0; k < 8; k++) {
    const d = Math.min(f.depth[k], 1.2);
    const x = G.keyX[k % 2];
    const y = G.keyY[Math.floor(k / 2)] + 4 * d;
    const rect = Skia.XYWHRect(x, y, G.keyW, FACE_H);
    const rr = Skia.RRectXY(rect, G.keyR, G.keyR);
    // face: lit from the top; pressed, its top light dims
    const top = mixRgb(KEY_TOP, KEY_BOTTOM, 0.45 * d);
    const face = Skia.Paint();
    face.setAntiAlias(true);
    face.setShader(Skia.Shader.MakeLinearGradient(vec(0, y), vec(0, y + FACE_H), [rgba(top, 1), rgba(KEY_BOTTOM, 1)], null, TileMode.Clamp));
    canvas.drawRRect(rr, face);
    canvas.save();
    canvas.clipRRect(rr, ClipOp.Intersect, true);
    // top highlight 1.5 pt and a 1 pt shade along the bottom
    const hi = Skia.Paint();
    hi.setAntiAlias(true);
    hi.setStyle(PaintStyle.Stroke);
    hi.setStrokeWidth(3);
    hi.setShader(Skia.Shader.MakeLinearGradient(vec(0, y), vec(0, y + 5), [rgba([1, 1, 1], 0.14 - 0.1 * Math.min(d, 1)), rgba([1, 1, 1], 0)], null, TileMode.Clamp));
    canvas.drawRRect(rr, hi);
    const lo = Skia.Paint();
    lo.setAntiAlias(true);
    lo.setStyle(PaintStyle.Stroke);
    lo.setStrokeWidth(2);
    lo.setShader(Skia.Shader.MakeLinearGradient(vec(0, y + FACE_H - 4), vec(0, y + FACE_H), [rgba([0, 0, 0], 0), rgba([0, 0, 0], 0.35)], null, TileMode.Clamp));
    canvas.drawRRect(rr, lo);
    canvas.restore();
    // the label is not clipped to the face: Explode's letters leave the key
    drawLabel(canvas, f, R.labels[k], k, x + G.keyW / 2, y + FACE_H / 2);
  }
};

const drawThread = (canvas: SkCanvas, f: Frame, R: Res) => {
  'worklet';
  const r = f.rise * G.rise;
  const ink = fill(INK);
  const mute = fill(MUTE);
  drawRun(canvas, R.stamp, 201 - R.stamp.w / 2, G.stampY - r + cap(R.stamp) / 2, mute);
  const recv = fill(RECEIVED);
  for (let j = 0; j < 2; j++) {
    const run = R.received[j];
    const y = G.received[j].y - r + f.dips[j];
    canvas.drawRRect(bubble(G.receivedX, y, run.w + 24, G.bubbleH, true), recv);
    drawRun(canvas, run, G.receivedX + 12, y + G.bubbleH / 2 + cap(run) / 2, ink);
  }
  const old = R.old;
  const ow = old.w + 2 * G.bubblePad;
  const oy = G.oldY - r + f.dips[2];
  canvas.drawRRect(bubble(G.bubbleRight - ow, oy, ow, G.bubbleH, false), fill(LIME));
  drawRun(canvas, old, G.bubbleRight - ow + G.bubblePad, oy + G.bubbleH / 2 + cap(old) / 2, fill(ON_LIME));
  drawRun(canvas, R.seen, G.bubbleRight - R.seen.w, G.seenY + cap(R.seen) / 2, mute);

  // the new bubble grows out of the send disc
  if (f.body > 0.002) {
    const k = f.body;
    const geo = R.geo;
    const sd = G.send;
    const cx = mix(sd.x + sd.d / 2, geo.bubbleL + geo.bubbleW / 2, k);
    const cy = mix(sd.y + sd.d / 2, G.oldY + G.bubbleH / 2, k);
    const w = mix(sd.d, geo.bubbleW, k) * (1 + f.squash) * (1 + f.blip);
    const h = mix(sd.d, G.bubbleH, k) * (1 - f.squash) * (1 + f.blip);
    const shape = bubble(cx - w / 2, cy - h / 2, w, h, false, mix(sd.d / 2, 5, seg(k, 0, 1)));
    // emptied, it is a glass shell: a faint lime tint inside a lit lime rim
    const full = seg(f.shell, 0.4, 1);
    canvas.drawRRect(shape, fill(LIME, mix(0.1, 1, full)));
    if (full < 0.99) {
      const rim = fill(LIME, 0.9 * (1 - full));
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(1.5);
      canvas.drawRRect(bubble(cx - w / 2 + 0.75, cy - h / 2 + 0.75, w - 1.5, h - 1.5, false), rim);
    }
  }
};

const drawComposer = (canvas: SkCanvas, f: Frame, R: Res, P: Statics) => {
  'worklet';
  canvas.drawPicture(P.composer);
  const run = R.heroR;
  // selection box behind the word, revealed by a mask from the left
  if (f.select > 0.01) {
    const x = G.fieldTextX - 2;
    const w = run.w + 4;
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(x, 0, w * f.select, 2000), ClipOp.Intersect, true);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, FIELD_BASE - 13.5, w, 17.5), 3, 3), fill(LIME, 0.22));
    canvas.restore();
  }
  if (f.placeholder > 0.01) {
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(G.fieldTextX - 2, 0, (R.placeholder.w + 4) * f.placeholder, 2000), ClipOp.Intersect, true);
    drawRun(canvas, R.placeholder, G.fieldTextX, FIELD_BASE, fill(MUTE));
    canvas.restore();
  }
  // send disc
  const sd = G.send;
  const cx = sd.x + sd.d / 2;
  const cy = sd.y + sd.d / 2;
  canvas.save();
  canvas.translate(cx, cy);
  canvas.scale(f.sendS, f.sendS);
  canvas.drawCircle(0, 0, sd.d / 2, fill(LIME));
  const ar = fill(ON_LIME);
  ar.setStyle(PaintStyle.Stroke);
  ar.setStrokeWidth(2);
  ar.setStrokeCap(StrokeCap.Round);
  ar.setStrokeJoin(StrokeJoin.Round);
  canvas.translate(-12, -12 + f.arrowY);
  canvas.scale(0.82, 0.82);
  canvas.translate(2.6, 2.4);
  canvas.drawPath(R.icons.arrow, ar);
  canvas.restore();
};

/** The word's eight glyphs, wherever they are: field, bubble, the air over the thread, the keys, the stage. */
const drawHero = (canvas: SkCanvas, f: Frame, R: Res, conversationScale: number) => {
  'worklet';
  const geo = R.geo;
  for (let i = 0; i < f.hero.length; i++) {
    const h = f.hero[i];
    const run = h.font ? R.heroM : R.heroR;
    const id = run.ids[i];
    const w = run.ws[i];
    const hg = geo.h[i];
    const colour = mixRgb(mixRgb(INK, ON_LIME, h.home), LIME, h.lime);
    const speed = Math.sqrt(h.vx * h.vx + h.vy * h.vy);
    // one-axis Gaussian along the velocity (σ = speed × 8 ms, cap 14 pt), only on the big letters
    const sig = Math.min(speed * 0.008, 14) * seg(h.s, 1.5, 3) * h.mb + h.blur;
    const cx = h.x;
    const y = h.y - (hg * h.s) / 2;
    const cy = y < G.field.y ? G.field.y + (y - G.field.y) * conversationScale : y;
    const glyphScale = mix(conversationScale, 1, seg(y, G.field.y - 30, G.field.y));
    const paint = fill(colour);
    const positionGlyph = () => {
      canvas.translate(cx, cy);
      canvas.scale(1, glyphScale);
    };
    const glyph = () => {
      canvas.drawGlyphs([id], [{ x: -w / 2, y: hg / 2 }], 0, 0, run.font, paint);
      if (h.w > 0.05) {
        const sp = fill(colour);
        sp.setStyle(PaintStyle.Stroke);
        sp.setStrokeJoin(StrokeJoin.Round);
        sp.setStrokeWidth(h.w / h.s);
        canvas.drawGlyphs([id], [{ x: -w / 2, y: hg / 2 }], 0, 0, run.font, sp);
      }
    };
    if (sig > 0.6) {
      const ang = (Math.atan2(h.vy, h.vx) * 180) / Math.PI;
      // the tail: the letter smeared along its path, trailing behind it
      canvas.save();
      positionGlyph();
      canvas.rotate(ang, 0, 0);
      const lp = Skia.Paint();
      lp.setAlphaf(0.7);
      lp.setImageFilter(Skia.ImageFilter.MakeBlur(sig, 0.01, TileMode.Decal, null));
      canvas.saveLayer(lp);
      canvas.translate(-sig * 1.1, 0);
      canvas.rotate(h.rot - ang, 0, 0);
      canvas.scale(h.s, h.s);
      glyph();
      canvas.restore();
      canvas.restore();
      // the letter itself, softened along the same axis
      const core = sig * 0.22;
      canvas.save();
      positionGlyph();
      canvas.rotate(ang, 0, 0);
      if (core > 0.4) {
        const cp = Skia.Paint();
        cp.setImageFilter(Skia.ImageFilter.MakeBlur(core, 0.01, TileMode.Decal, null));
        canvas.saveLayer(cp);
      }
      canvas.rotate(h.rot - ang, 0, 0);
      canvas.scale(h.s, h.s);
      glyph();
      if (core > 0.4) canvas.restore();
      canvas.restore();
      continue;
    }
    canvas.save();
    positionGlyph();
    canvas.rotate(h.rot, 0, 0);
    canvas.scale(h.s, h.s);
    glyph();
    canvas.restore();
  }
};

/** The whole screen for one frame; `oy` shifts the bottom-anchored parts on screens taller or shorter than 874 pt. */
export const drawScene = (canvas: SkCanvas, f: Frame, R: Res, P: Statics, S: number, oy: number, conversationScale: number) => {
  'worklet';
  canvas.save();
  canvas.scale(S, S);
  canvas.drawPicture(P.back);
  canvas.translate(0, oy);
  drawComposer(canvas, f, R, P);
  canvas.save();
  canvas.translate(0, G.field.y * (1 - conversationScale));
  canvas.scale(1, conversationScale);
  drawThread(canvas, f, R);
  canvas.restore();
  canvas.drawPicture(P.panel);
  drawKeys(canvas, f, R);
  drawHero(canvas, f, R, conversationScale);
  canvas.restore();
};
