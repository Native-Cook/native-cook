// Morph Object Onboarding — drawing, every frame, in the 402 × 874 reference space (scaled to the
// screen's width, standing on its bottom edge).

import { BlendMode, BlurStyle, FilterMode, MipmapMode, type SkCanvas, type SkFont, type SkImage, type SkPath, type SkPoint, type SkTypeface, Skia, TileMode } from '@shopify/react-native-skia';

import { LOOP, mix, smooth } from './moo-motion';
import { C, COPY, MORPH, PAGES, REF_W, SIZE, UI } from './moo-theme';

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

export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.36, l.font, paint);
}

export type Faces = { regular: SkTypeface; medium: SkTypeface; bold: SkTypeface };

export type Res = {
  titles: Label[][];
  subs: Label[][];
  next: Label;
  start: Label;
  skip: Label;
  atlas: SkImage; // 33 frames of the morph, small
  keys: SkImage[]; // the three resting objects, sharp
  star: SkPath;
};

const STAR = 'M0 -10 C1.1 -3.4 3.4 -1.1 10 0 C3.4 1.1 1.1 3.4 0 10 C-1.1 3.4 -3.4 1.1 -10 0 C-3.4 -1.1 -1.1 -3.4 0 -10 Z';

export function makeRes(f: Faces, atlas: SkImage, keys: SkImage[]): Res {
  return {
    titles: PAGES.map((p) => p.title.map((s) => makeLabel(f.bold, s, SIZE.title))),
    subs: PAGES.map((p) => p.sub.map((s) => makeLabel(f.regular, s, SIZE.sub))),
    next: makeLabel(f.medium, COPY.next, SIZE.button),
    start: makeLabel(f.medium, COPY.start, SIZE.button),
    skip: makeLabel(f.regular, COPY.skip, SIZE.skip),
    atlas,
    keys,
    star: Skia.Path.MakeFromSVGString(STAR)!,
  };
}

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = {
  amb: number;
  p: number; // page position, may run past 0 … 2 under the finger (rubber band)
  arrive: number; // seconds since arriving on a page (−1 none): sparkle burst, card bump
  hop: number; // seconds since a tap on the object (−1 none)
  press: number;
};

const TAU = Math.PI * 2;

function hex(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}

function star(c: SkCanvas, r: Res, x: number, y: number, half: number, rot: number, color: Float32Array) {
  'worklet';
  if (half < 0.3) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(half / 10, half / 10);
  c.drawPath(r.star, p);
  c.restore();
}

function drawObject(c: SkCanvas, r: Res, live: Live) {
  'worklet';
  const p = Math.max(0, Math.min(2, live.p));
  const over = live.p - p; // rubber band past the ends
  const b = MORPH.box;
  const bob = 4 * Math.sin(TAU * 2 * (live.amb / LOOP));
  const tilt = 2 * Math.sin(TAU * (live.amb / LOOP) + 0.6);
  // a tap: a hop with a squash on landing and a little twist
  let hop = 0;
  let squash = 0;
  let twist = 0;
  if (live.hop >= 0) {
    const q = live.hop / 0.5;
    if (q < 1) {
      hop = 16 * Math.sin(Math.PI * q);
      twist = 10 * Math.sin(TAU * q);
    } else squash = 0.08 * Math.exp(-(live.hop - 0.5) / 0.14) * Math.cos(TAU * (live.hop - 0.5) / 0.22);
  }
  // arriving on a page: a soft settle
  if (live.arrive >= 0) squash += 0.05 * Math.exp(-live.arrive / 0.18) * Math.cos(TAU * live.arrive / 0.3);
  const cx = b.x + b.w / 2;
  const cy = b.y + b.h / 2;

  // its shadow on the dark floor, smaller as it rises
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0, 0, 0, 0.35 * (1 - hop / 30)));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  const sw = 150 * (1 - hop / 60) * (1 - bob / 80);
  c.drawOval(Skia.XYWHRect(cx - sw / 2, b.y + b.h * 0.86, sw, 16), sh);

  c.save();
  c.translate(cx - over * 26, cy + bob - hop);
  c.rotate(tilt + twist + over * -14, 0, 0);
  c.scale(1 + squash + Math.abs(over) * 0.06, 1 - squash - Math.abs(over) * 0.04);
  c.translate(-b.w / 2, -b.h / 2);
  const dst = Skia.XYWHRect(0, 0, b.w, b.h);
  const fr = p * MORPH.perPage;
  const f0 = Math.floor(fr);
  const f1 = Math.min(MORPH.frames - 1, f0 + 1);
  const k = fr - f0;
  const ip = Skia.Paint();
  ip.setAntiAlias(true);
  // near a page the sharp render, in between the small morph frames cross-faded
  const near = Math.round(p);
  const sharp = 1 - smooth(Math.abs(p - near) / 0.07);
  const src = (i: number) => {
    'worklet';
    const col = i % MORPH.cols;
    const row = Math.floor(i / MORPH.cols);
    return Skia.XYWHRect(col * MORPH.fw, row * MORPH.fh, MORPH.fw, MORPH.fh);
  };
  if (sharp < 0.999) {
    ip.setAlphaf(1);
    c.drawImageRectOptions(r.atlas, src(f0), dst, FilterMode.Linear, MipmapMode.None, ip);
    if (k > 0.002) {
      ip.setAlphaf(k);
      c.drawImageRectOptions(r.atlas, src(f1), dst, FilterMode.Linear, MipmapMode.None, ip);
    }
  }
  if (sharp > 0.001) {
    const key = r.keys[near];
    ip.setAlphaf(sharp);
    c.drawImageRectOptions(key, Skia.XYWHRect(0, 0, key.width(), key.height()), dst, FilterMode.Linear, MipmapMode.Linear, ip);
  }
  c.restore();
  // mid-morph the object gives off light: a soft flare and motes that swirl round it, so the
  // in-between frames read as one transformation rather than two objects swapping
  const mid = Math.sin(Math.PI * (p - Math.floor(p)));
  if (mid > 0.02) {
    const fl = Skia.Paint();
    fl.setBlendMode(BlendMode.Plus);
    fl.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy + bob - hop }, 120, [Float32Array.of(1, 1, 1, 0.22 * mid), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp));
    c.drawCircle(cx, cy + bob - hop, 120, fl);
    const mp = Skia.Paint();
    mp.setAntiAlias(true);
    mp.setBlendMode(BlendMode.Plus);
    for (let k2 = 0; k2 < 14; k2++) {
      const ang = TAU * (k2 / 14) + p * 3.2 + k2 * 0.4;
      const rad = 70 + 30 * Math.sin(k2 * 1.7) + 26 * mid;
      const x = cx + Math.cos(ang) * rad;
      const y = cy + bob - hop + Math.sin(ang) * rad * 0.62;
      mp.setColor(Float32Array.of(1, 0.98, 0.9, 0.75 * mid * (0.5 + 0.5 * Math.sin(k2 * 2.3))));
      c.drawCircle(x, y, 1.4 + 1.2 * ((k2 * 0.37) % 1), mp);
    }
  }
}

function drawGlow(c: SkCanvas, live: Live) {
  'worklet';
  const p = Math.max(0, Math.min(2, live.p));
  const i = Math.min(1, Math.floor(p));
  const k = p - i;
  const a = hex(PAGES[i].glow);
  const b = hex(PAGES[i + 1].glow);
  const breathe = 0.16 + 0.04 * Math.sin(TAU * 2 * (live.amb / LOOP));
  const col = Float32Array.of(mix(a[0], b[0], k), mix(a[1], b[1], k), mix(a[2], b[2], k), breathe);
  const g = Skia.Paint();
  const bx = MORPH.box;
  const cx = bx.x + bx.w / 2;
  const cy = bx.y + bx.h / 2;
  g.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, 190, [col, Float32Array.of(col[0], col[1], col[2], 0)], null, TileMode.Clamp));
  c.drawCircle(cx, cy, 190, g);
}

function drawTop(c: SkCanvas, r: Res, live: Live) {
  'worklet';
  // the dots: the current one is a white pill; as the page changes one shrinks while the next grows
  const d = UI.dots;
  const p = Math.max(0, Math.min(2, live.p));
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  let x = d.x;
  for (let i = 0; i < 3; i++) {
    const wgt = smooth(1 - Math.min(1, Math.abs(p - i)));
    const w = mix(d.dot, d.pill, wgt);
    const h = mix(d.dot, d.h, wgt);
    const base = hex(C.dot);
    dot.setColor(Float32Array.of(mix(base[0], 1, wgt), mix(base[1], 1, wgt), mix(base[2], 1, wgt), 1));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, d.y - h / 2, w, h), h / 2, h / 2), dot);
    x += w + d.gap;
  }
  // Skip fades away on the last page
  const sk = Skia.Paint();
  sk.setAntiAlias(true);
  sk.setColor(hex(C.skip, 1 - smooth((p - 1.4) / 0.5)));
  drawLabel(c, r.skip, UI.skip.x, UI.skip.y, 1, sk);
}

function drawSparkles(c: SkCanvas, r: Res, live: Live) {
  'worklet';
  const p = Math.max(0, Math.min(2, live.p));
  // the pin's pink sparkle belongs to the first page
  const w0 = 1 - smooth(p / 0.45);
  const tw = 0.5 + 0.5 * Math.sin(TAU * 3 * (live.amb / LOOP));
  star(c, r, UI.sparkle.x, UI.sparkle.y, UI.sparkle.s * (0.82 + 0.18 * tw) * w0, 8 * Math.sin(TAU * live.amb / LOOP), hex(C.sparkle, w0));
  // arriving on a page: small sparkles burst out of the object
  if (live.arrive >= 0 && live.arrive < 0.75) {
    const q = live.arrive / 0.75;
    const b = MORPH.box;
    const cx = b.x + b.w / 2;
    const cy = b.y + b.h / 2;
    const page = Math.round(p);
    const col = hex(PAGES[page].glow);
    for (let i = 0; i < 7; i++) {
      const a = (TAU * i) / 7 + page;
      const dist = (95 + 25 * ((i * 37) % 7) / 7) * (1 - Math.pow(1 - q, 3));
      const life = Math.sin(Math.PI * q);
      star(c, r, cx + Math.cos(a) * dist, cy + Math.sin(a) * dist * 0.85, (4 + (i % 3) * 2) * life, 90 * q, Float32Array.of(mix(col[0], 1, 0.5), mix(col[1], 1, 0.5), mix(col[2], 1, 0.5), life));
    }
  }
}

function drawCard(c: SkCanvas, r: Res, live: Live, g: Geo) {
  'worklet';
  const p = Math.max(0, Math.min(2, live.p));
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setColor(hex(C.card));
  const bump = live.arrive >= 0 ? 0.008 * Math.exp(-live.arrive / 0.2) * Math.cos(TAU * live.arrive / 0.35) : 0;
  c.save();
  c.translate(REF_W / 2, UI.card.y);
  c.scale(1 + bump, 1 + bump);
  c.translate(-REF_W / 2, -UI.card.y);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, UI.card.y, REF_W, 874 - UI.card.y + 60 / g.s), UI.card.r, UI.card.r), card);
  c.restore();
  // each page's words slide in from where its page lies and come into focus
  for (let i = 0; i < 3; i++) {
    const d = i - p;
    const a = 1 - Math.min(1, Math.abs(d) * 1.7);
    if (a <= 0.003) continue;
    const blur = Math.abs(d) * 7;
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(hex(C.ink, a));
    if (blur > 0.05) tp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setColor(hex(C.sub, a));
    if (blur > 0.05) sp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    for (let l = 0; l < 2; l++) {
      // the second line and the subtitle trail the first a little (a stagger tied to the drag)
      drawLabel(c, r.titles[i][l], REF_W / 2 + d * (70 + l * 14), UI.title[l], 0.5, tp);
      drawLabel(c, r.subs[i][l], REF_W / 2 + d * (96 + l * 10), UI.sub[l], 0.5, sp);
    }
  }
  // the button: "Continue Now" turns into "Let's start" on the last page
  const b = UI.button;
  const bs = 1 - 0.04 * live.press;
  c.save();
  c.translate(b.x + b.w / 2, b.y + b.h / 2);
  c.scale(bs, bs);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(hex(C.button));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-b.w / 2, -b.h / 2, b.w, b.h), b.h / 2, b.h / 2), bp);
  const k = smooth((p - 1.35) / 0.55);
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  if (k < 0.999) {
    lp.setColor(hex(C.buttonInk, 1 - k));
    drawLabel(c, r.next, 0, -8 * k, 0.5, lp);
  }
  if (k > 0.001) {
    lp.setColor(hex(C.buttonInk, k));
    drawLabel(c, r.start, 0, 8 * (1 - k), 0.5, lp);
  }
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, live: Live) {
  'worklet';
  c.drawColor(hex(C.bg));
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  drawGlow(c, live);
  drawSparkles(c, r, live);
  drawObject(c, r, live);
  drawTop(c, r, live);
  drawCard(c, r, live, g);
  c.restore();
}
