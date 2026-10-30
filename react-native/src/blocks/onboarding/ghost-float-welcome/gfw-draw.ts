// Ghost Float Welcome — drawing, every frame, in the 402 × 874 reference space (the screen is
// scaled to fit its width and centred vertically on taller screens).

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FillType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  PathOp,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkMaskFilter,
  type SkPath,
  type SkPoint,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { bodyPose, type Frame, LOOP, mix, starPose } from './gfw-motion';
import { BODIES, C, COPY, GLINTS, REF_W, SIZE, STARS, TOKEN, UI } from './gfw-theme';

// ------------------------------------------------------------------ labels

export type Label = { font: SkFont; glyphs: number[]; pos: SkPoint[]; width: number; size: number };

export function makeLabel(face: SkTypeface, str: string, size: number): Label {
  const font = Skia.Font(face, size);
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i];
  }
  return { font, glyphs: ids, pos, width: x, size };
}

/** Draws `l` with the middle of its cap height on `cy`; `ax` 0 left, 0.5 centre, 1 right. */
export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.36, l.font, paint);
}

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };

export type Res = {
  title: Label;
  sub1: Label;
  sub2: Label;
  agree: Label;
  terms: Label;
  create: Label;
  have: Label;
  help: Label;
  dino: SkImage;
  penguin: SkImage;
  glyphs: { lumen: SkPath; ember: SkPath; fern: SkPath; halo: SkPath; fernRib: SkPath };
  star: SkPath; // concave four-point star, half-size 10
  glint: SkPath;
  check: SkPath;
  blurGlow: SkMaskFilter;
  blurRing: SkMaskFilter;
};

const STAR = 'M0 -10 C1.2 -3.2 3.2 -1.2 10 0 C3.2 1.2 1.2 3.2 0 10 C-1.2 3.2 -3.2 1.2 -10 0 C-3.2 -1.2 -1.2 -3.2 0 -10 Z';
const GLINT = 'M0 -10 Q1.6 -1.6 10 0 Q1.6 1.6 0 10 Q-1.6 1.6 -10 0 Q-1.6 -1.6 0 -10 Z';
const CHECK = 'M-5.2 0.2 L-1.6 3.8 L5.4 -3.6';

export function makeRes(f: Faces, im: { dino: SkImage; penguin: SkImage }): Res {
  // the four invented tokens' marks, drawn in a 24-unit box round the origin
  const moonA = Skia.Path.Make();
  moonA.addCircle(0, 0, 8.6);
  const moonB = Skia.Path.Make();
  moonB.addCircle(4.6, -3.4, 7.4);
  const lumen = Skia.Path.MakeFromOp(moonA, moonB, PathOp.Difference) ?? moonA;
  const ember = Skia.Path.MakeFromSVGString(
    'M0.5 -11 C5.5 -5.5 9 -1.5 8.2 3.8 C7.5 8.6 4 11 0 11 C-4.2 11 -7.8 8.4 -8 4 C-8.2 0.4 -5.8 -2 -4.6 -5 C-3.4 -2.6 -1.8 -1.2 -0.8 -1.4 C-1.2 -4.6 -1.4 -7.8 0.5 -11 Z M0.2 3.4 C-1.6 5.4 -2.6 6.6 -2.4 8 C-2.2 9.2 -1 9.8 0.2 9.8 C1.6 9.8 2.8 9 2.8 7.6 C2.8 6 1.6 4.8 0.2 3.4 Z',
  )!;
  ember.setFillType(FillType.EvenOdd); // the inner flame is a hole
  const fern = Skia.Path.MakeFromSVGString('M-8.5 8.5 C-9.6 -2.8 -1.6 -9.4 9 -9 C9.6 1.6 2.6 9.6 -8.5 8.5 Z')!;
  const fernRib = Skia.Path.MakeFromSVGString('M-8.5 8.5 L3.6 -3.6 M-2.6 2.6 L-2.4 -3.2 M0.8 -0.8 L5.2 -0.6')!;
  const halo = Skia.Path.Make();
  halo.addCircle(-3.6, 0, 6);
  halo.addCircle(3.6, 0, 6);
  return {
    title: makeLabel(f.bold, COPY.title, SIZE.title),
    sub1: makeLabel(f.regular, COPY.sub1, SIZE.sub),
    sub2: makeLabel(f.regular, COPY.sub2, SIZE.sub),
    agree: makeLabel(f.regular, COPY.agree, SIZE.agree),
    terms: makeLabel(f.semi, COPY.terms, SIZE.agree),
    create: makeLabel(f.bold, COPY.create, SIZE.button),
    have: makeLabel(f.semi, COPY.have, SIZE.have),
    help: makeLabel(f.semi, '?', 12.5),
    dino: im.dino,
    penguin: im.penguin,
    glyphs: { lumen, ember, fern, halo, fernRib },
    star: Skia.Path.MakeFromSVGString(STAR)!,
    glint: Skia.Path.MakeFromSVGString(GLINT)!,
    check: Skia.Path.MakeFromSVGString(CHECK)!,
    blurGlow: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true),
    blurRing: Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true),
  };
}

// ------------------------------------------------------------------ the scene

export type Geo = { w: number; h: number; s: number; dy: number };

/** Touch and ambient extras on top of the timeline. */
export type Live = {
  amb: number;
  ox: number[]; // spring offsets per live body (7 floating things, then 6 stars)
  oy: number[];
  or: number[];
  vx?: number[]; // spring velocities, for the speed streak of a thrown thing
  vy?: number[];
  lookX: number;
  lookY: number;
  puff: number; // the ghost's boo from a tap
  ring: number;
  wide: number;
  roll: number;
  hop: number;
  agree: number; // −1: the loop decides
  btnPress: number;
  no: number; // the button's "not yet" shake
  noP: number;
  tiltX: number;
  tiltY: number;
};

const TAU = Math.PI * 2;

function rgb(hex: string) {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
const K = {
  btnOff: rgb(C.btnOff),
  btnOn: rgb(C.btnOn),
  inkOff: rgb(C.btnOffInk),
  inkOn: rgb(C.btnOnInk),
  lavender: rgb(C.lavender),
  ring: rgb(C.ring),
};
function colMix(a: number[], b: number[], p: number, alpha = 1) {
  'worklet';
  return Float32Array.of(a[0] + (b[0] - a[0]) * p, a[1] + (b[1] - a[1]) * p, a[2] + (b[2] - a[2]) * p, alpha);
}
function hex(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}

function drawStars(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(C.star));
  for (let i = 0; i < STARS.length; i++) {
    const s = STARS[i];
    const pose = starPose(i, f.t, live.amb);
    const j = BODIES.length + i;
    c.save();
    c.translate(pose.x + live.ox[j] - live.tiltX * 10 * s.depth, pose.y + live.oy[j] - live.tiltY * 8 * s.depth);
    c.rotate(pose.rot + live.or[j], 0, 0);
    c.scale(s.s / 10, s.s / 10);
    c.drawPath(r.star, p);
    c.restore();
  }
}

function drawGlint(c: SkCanvas, r: Res, x: number, y: number, half: number, a: number, rot: number) {
  'worklet';
  if (a < 0.02) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(1, 1, 1, a));
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(half / 10, half / 10);
  c.drawPath(r.glint, p);
  c.restore();
}

function drawToken(c: SkCanvas, r: Res, i: number, x: number, y: number, rot: number) {
  'worklet';
  const b = BODIES[i];
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.save();
  c.translate(x, y);
  if (b.kind === 'dino' || b.kind === 'penguin') {
    const img = b.kind === 'dino' ? r.dino : r.penguin;
    const clip = Skia.Path.Make();
    clip.addCircle(0, 0, b.r);
    c.save();
    c.clipPath(clip, ClipOp.Intersect, true);
    c.rotate(rot * 0.5, 0, 0);
    c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-b.r * 1.04, -b.r * 1.04, b.r * 2.08, b.r * 2.08), FilterMode.Linear, MipmapMode.Linear, null);
    c.restore();
    c.restore();
    return;
  }
  const tk = TOKEN[b.kind as 'lumen' | 'ember' | 'fern' | 'halo'];
  p.setColor(hex(tk.face));
  c.drawCircle(0, 0, b.r, p);
  // a soft light from the top left, so the coins have a little body
  const hl = Skia.Paint();
  hl.setAntiAlias(true);
  hl.setShader(
    Skia.Shader.MakeRadialGradient({ x: -b.r * 0.4, y: -b.r * 0.5 }, b.r * 1.3, [Float32Array.of(1, 1, 1, 0.16), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp),
  );
  c.drawCircle(0, 0, b.r, hl);
  c.rotate(rot, 0, 0);
  const k = (b.r / 24) * 1.55;
  c.scale(k, k);
  const g = Skia.Paint();
  g.setAntiAlias(true);
  g.setColor(hex(tk.glyph));
  if (b.kind === 'lumen') c.drawPath(r.glyphs.lumen, g);
  else if (b.kind === 'ember') c.drawPath(r.glyphs.ember, g);
  else if (b.kind === 'fern') {
    c.drawPath(r.glyphs.fern, g);
    const rib = Skia.Paint();
    rib.setAntiAlias(true);
    rib.setStyle(PaintStyle.Stroke);
    rib.setStrokeWidth(1.3);
    rib.setStrokeCap(StrokeCap.Round);
    rib.setColor(hex(tk.face));
    c.drawPath(r.glyphs.fernRib, rib);
  } else {
    g.setStyle(PaintStyle.Stroke);
    g.setStrokeWidth(2.4);
    c.drawPath(r.glyphs.halo, g);
  }
  c.restore();
}

/** The ghost: a dome with a three-scallop hem that ripples; it squashes, stretches, rolls and looks. */
function drawGhost(c: SkCanvas, r: Res, f: Frame, live: Live, x: number, y: number, rot: number) {
  'worklet';
  const ph = (TAU * 4 * live.amb) / LOOP; // the hem ripples four times a loop
  const puff = f.puff + live.puff;
  const inhale = f.inhale;
  const sx = 1 + 0.07 * inhale - 0.1 * puff;
  const sy = 1 - 0.1 * inhale + 0.16 * puff;
  const roll = f.roll + live.roll;
  const hop = f.hop + live.hop;
  // a breath of light round it
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Float32Array.of(0.85, 0.82, 1, 0.025 + 0.02 * Math.sin(TAU * 2 * live.amb / LOOP) + 0.12 * Math.max(0, puff)));
  glow.setMaskFilter(r.blurGlow);
  c.drawCircle(x, y - hop, 44, glow);

  c.save();
  c.translate(x, y - hop + 30);
  c.scale(sx, sy);
  c.translate(0, -30);
  c.rotate(rot - 8 + roll, 0, 0);
  const w0 = 3.2 * Math.sin(ph);
  const w1 = 3.2 * Math.sin(ph + 2.1);
  const w2 = 3.2 * Math.sin(ph + 4.2);
  const g = Skia.Path.Make();
  g.moveTo(-38, 14);
  g.cubicTo(-40, -14, -20, -34, 2, -34);
  g.cubicTo(26, -34, 40, -14, 38, 10);
  g.lineTo(38, 20);
  g.quadTo(28, 36 + w0, 14, 24);
  g.quadTo(1, 36 + w1, -12, 23);
  g.quadTo(-28, 33 + w2, -38, 14);
  g.close();
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setColor(hex(C.ghost));
  c.drawPath(g, body);
  // eyes: ovals that follow the look; wide on the boo; closed on a blink; ^ ^ when happy
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(C.eye));
  const lx = live.lookX * 3.2;
  const ly = live.lookY * 2.6;
  const wide = Math.max(f.wide, live.wide);
  const happy = f.happy;
  for (let e = 0; e < 2; e++) {
    const ex = (e === 0 ? 7 : 20) + lx;
    const ey = -9 + ly;
    if (happy > 0.5) {
      const arc = Skia.Path.Make();
      arc.moveTo(ex - 4.6, ey + 2);
      arc.quadTo(ex, ey - 5.5, ex + 4.6, ey + 2);
      const st = Skia.Paint();
      st.setAntiAlias(true);
      st.setStyle(PaintStyle.Stroke);
      st.setStrokeWidth(3);
      st.setStrokeCap(StrokeCap.Round);
      st.setColor(hex(C.eye, (happy - 0.5) * 2));
      c.drawPath(arc, st);
      continue;
    }
    const k = 1 + 0.32 * wide;
    const open = Math.max(0.08, 1 - f.blink) * (1 - 0.35 * inhale) * (1 - happy * 2 > 0 ? 1 - happy * 2 : 0.05);
    c.drawOval(Skia.XYWHRect(ex - 4.3 * k, ey - 6.3 * k * open, 8.6 * k, 12.6 * k * open), ink);
  }
  // boo: a small round mouth opens with the puff
  const mouth = Math.max(wide, Math.max(0, puff)) * (1 - happy);
  if (mouth > 0.05) {
    c.drawOval(Skia.XYWHRect(13.5 + lx - 3.4 * mouth, 4 + ly - 4 * mouth, 6.8 * mouth, 8 * mouth), ink);
  }
  c.restore();
}

function drawRing(c: SkCanvas, r: Res, x: number, y: number, p: number) {
  'worklet';
  if (p < 0) return;
  const q = 1 - Math.pow(1 - p, 3);
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(mix(14, 1.5, q));
  st.setColor(Float32Array.of(0.86, 0.82, 1, 0.3 * (1 - p)));
  st.setMaskFilter(r.blurRing);
  c.drawCircle(x, y, mix(30, 270, q), st);
}

function drawControls(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const white = Skia.Paint();
  white.setAntiAlias(true);
  white.setColor(Float32Array.of(1, 1, 1, 1));
  drawLabel(c, r.title, REF_W / 2, UI.titleY, 0.5, white);
  const sub = Skia.Paint();
  sub.setAntiAlias(true);
  sub.setColor(hex(C.sub));
  drawLabel(c, r.sub1, REF_W / 2, UI.sub1Y, 0.5, sub);
  drawLabel(c, r.sub2, REF_W / 2, UI.sub2Y, 0.5, sub);

  // page dots: the first is a lavender pill
  const d = UI.dots;
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  const total = d.pillW + (d.n - 1) * d.gap;
  let x = REF_W / 2 - total / 2;
  dot.setColor(hex(C.pill));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, UI.dotsY - d.h / 2, d.pillW, d.h), d.h / 2, d.h / 2), dot);
  dot.setColor(hex(C.dot));
  x += d.pillW + d.gap - d.h / 2;
  for (let i = 1; i < d.n; i++) {
    c.drawCircle(x, UI.dotsY, d.h / 2, dot);
    x += d.gap;
  }

  // the agreement: the loop's tick, unless a finger has taken over
  const agree = live.agree >= 0 ? live.agree : f.tick;
  const ck = UI.check;
  const press = f.press;
  c.save();
  c.translate(ck.x, ck.y);
  c.scale(1 - 0.12 * press, 1 - 0.12 * press);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1.7);
  ring.setColor(colMix(K.ring, K.lavender, agree));
  c.drawCircle(0, 0, ck.r - 0.85, ring);
  if (agree > 0.001) {
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setColor(hex(C.lavender, Math.min(1, agree * 1.4)));
    c.drawCircle(0, 0, (ck.r - 0.2) * (0.6 + 0.4 * Math.min(1, agree * 1.3)), fill);
    const tick = r.check.copy();
    tick.trim(0, Math.min(1, Math.max(0, (agree - 0.25) / 0.75)), false);
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setStyle(PaintStyle.Stroke);
    tp.setStrokeWidth(2.4);
    tp.setStrokeCap(StrokeCap.Round);
    tp.setStrokeJoin(StrokeJoin.Round);
    tp.setColor(hex(C.bg));
    c.drawPath(tick, tp);
  }
  c.restore();
  // "not yet": the checkbox calls for attention with a lavender ripple
  if (live.noP >= 0) {
    const rp = Skia.Paint();
    rp.setAntiAlias(true);
    rp.setStyle(PaintStyle.Stroke);
    rp.setStrokeWidth(2);
    rp.setColor(hex(C.lavender, 0.7 * (1 - live.noP)));
    c.drawCircle(ck.x, ck.y, ck.r + 10 * live.noP, rp);
  }
  const grey = Skia.Paint();
  grey.setAntiAlias(true);
  grey.setColor(hex(C.grey));
  drawLabel(c, r.agree, UI.agreeX, ck.y, 0, grey);
  const lav = Skia.Paint();
  lav.setAntiAlias(true);
  lav.setColor(hex(C.lavender));
  drawLabel(c, r.terms, UI.agreeX + r.agree.width, ck.y, 0, lav);

  // the button: wakes from muted to lavender once agreed
  const btnOn = live.agree >= 0 ? live.agree : f.button;
  const b = UI.button;
  const bs = 1 - 0.04 * live.btnPress;
  c.save();
  c.translate(b.x + b.w / 2 + 7 * live.no, b.y + b.h / 2);
  c.scale(bs, bs);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(colMix(K.btnOff, K.btnOn, btnOn));
  const rr = Skia.RRectXY(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), b.h / 2, b.h / 2);
  c.drawRRect(rr, bp);
  if (f.sheen >= 0 && live.agree < 0) {
    const sx = mix(-b.w / 2 - 60, b.w / 2 + 60, f.sheen);
    const sh = Skia.Paint();
    sh.setBlendMode(BlendMode.Plus);
    sh.setShader(
      Skia.Shader.MakeLinearGradient({ x: sx - 50, y: 0 }, { x: sx + 50, y: 10 }, [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.35), Float32Array.of(1, 1, 1, 0)], [0, 0.5, 1], TileMode.Clamp),
    );
    c.save();
    c.clipRRect(rr, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), sh);
    c.restore();
  }
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  lp.setColor(colMix(K.inkOff, K.inkOn, btnOn));
  drawLabel(c, r.create, 0, 0, 0.5, lp);
  c.restore();

  const have = Skia.Paint();
  have.setAntiAlias(true);
  have.setColor(hex(C.have));
  drawLabel(c, r.have, REF_W / 2, UI.haveY, 0.5, have);

  // help
  const hp = Skia.Paint();
  hp.setAntiAlias(true);
  hp.setStyle(PaintStyle.Stroke);
  hp.setStrokeWidth(1.5);
  hp.setColor(hex(C.have));
  c.drawCircle(UI.help.x, UI.help.y, UI.help.r, hp);
  const hq = Skia.Paint();
  hq.setAntiAlias(true);
  hq.setColor(hex(C.have));
  drawLabel(c, r.help, UI.help.x, UI.help.y, 0.5, hq);
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  c.drawColor(hex(C.bg));
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  drawStars(c, r, f, live);
  // the ring of the boo, behind the floating things
  const g0 = bodyPose(0, f.t, live.amb);
  const gx = g0.x + live.ox[0] - live.tiltX * 12;
  const gy = g0.y + live.oy[0] - live.tiltY * 9;
  drawRing(c, r, gx, gy, f.ring);
  drawRing(c, r, gx, gy, live.ring);
  for (let i = 1; i < BODIES.length; i++) {
    const b = BODIES[i];
    const p = bodyPose(i, f.t, live.amb);
    const x = p.x + live.ox[i] - live.tiltX * 12 * b.depth;
    const y = p.y + live.oy[i] - live.tiltY * 9 * b.depth;
    // thrown fast, a thing leaves a short streak of soft light behind it
    const vx = live.vx ? live.vx[i] : 0;
    const vy = live.vy ? live.vy[i] : 0;
    const sp = Math.hypot(vx, vy);
    if (sp > 260) {
      const k = Math.min(1, (sp - 260) / 900);
      const sk = Skia.Paint();
      sk.setAntiAlias(true);
      sk.setStyle(PaintStyle.Stroke);
      sk.setStrokeCap(StrokeCap.Round);
      sk.setStrokeWidth(b.r * 1.1);
      sk.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x: x - vx * 0.07, y: y - vy * 0.07 }, [Float32Array.of(0.85, 0.82, 1, 0.22 * k), Float32Array.of(0.85, 0.82, 1, 0)], null, TileMode.Clamp));
      c.drawLine(x, y, x - vx * 0.07, y - vy * 0.07, sk);
    }
    drawToken(c, r, i, x, y, p.rot + live.or[i]);
  }
  // the white glints riding on the tokens, twinkling
  for (let k = 0; k < GLINTS.length; k++) {
    const gl = GLINTS[k];
    const b = BODIES[gl.body];
    const p = bodyPose(gl.body, f.t, live.amb);
    const tw = 0.5 + 0.5 * Math.sin(TAU * ((2 + (k % 2)) * live.amb / LOOP + k * 0.27));
    drawGlint(
      c,
      r,
      p.x + live.ox[gl.body] - live.tiltX * 12 * b.depth + gl.dx,
      p.y + live.oy[gl.body] - live.tiltY * 9 * b.depth + gl.dy,
      gl.s * (0.7 + 0.3 * tw),
      0.75 + 0.25 * tw,
      12 * Math.sin(TAU * (live.amb / LOOP + k * 0.2)),
    );
  }
  drawGhost(c, r, f, live, gx, gy, g0.rot + live.or[0]);
  drawControls(c, r, f, live);
  c.restore();
}
