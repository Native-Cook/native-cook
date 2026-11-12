// Glass Pay Morph — drawing one frame: the shader pass (page, lights, glass), then the card's printed
// face through the same projective matrix as the glass, the pay pulse, the captions and the top bar.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  type SkCanvas,
  type SkFont,
  type SkPaint,
  type SkPath,
  type SkRuntimeEffect,
  type SkTypeface,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
} from '@shopify/react-native-skia';

import { cardInverse, cardMatrix, clamp01, easeInOutCubic, easeOutCubic, type Scene, seg, T, TRAIL } from './gpm-motion';
import { C, CARD, CHROME, COPY, LENS, NUMBER_FX, REF_W } from './gpm-theme';

export type Geo = { w: number; h: number; s: number; dy: number; top: number; px: number };

type Line = { ids: number[]; pos: { x: number; y: number }[]; width: number };

export type Res = {
  effect: SkRuntimeEffect;
  number: SkFont;
  numberGroups: Line[];
  numberX: number[]; // each group's left edge, card pt
  name: SkFont;
  nameLine: Line;
  cap1: SkFont;
  cap2: SkFont;
  capLines: Line[]; // ready 1, ready 2, paid 1, paid 2
  brand: SkFont;
  brandLine: Line;
  small: SkFont;
  last4Line: Line;
  chipOutline: SkPath;
  chipLines: number[][]; // polylines, card-local pt
  paint: SkPaint;
  stroke: SkPaint;
};

function line(font: SkFont, str: string, tracking = 0): Line {
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  const pos: { x: number; y: number }[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + (i < ids.length - 1 ? tracking : 0);
  }
  return { ids, pos, width: x };
}

/** The chip's inner contact lines, traced from the pin (chip-local 0..1, then card pt). */
const CHIP_LINES_N: number[][] = [
  [0.05, 0.24, 0.4, 0.24, 0.4, 1.0],
  [0.05, 0.47, 0.22, 0.47],
  [0.05, 0.75, 0.4, 0.75],
  [0.61, 0.0, 0.61, 0.58, 0.63, 0.65, 0.68, 0.69, 0.75, 0.7, 1.0, 0.7],
  [0.61, 0.24, 1.0, 0.24],
  [0.61, 0.47, 1.0, 0.47],
];

export function makeRes(
  effect: SkRuntimeEffect,
  f: { light: SkTypeface; regular: SkTypeface; medium: SkTypeface; semi: SkTypeface },
): Res {
  const number = Skia.Font(f.light, CARD.number.size);
  const name = Skia.Font(f.regular, CARD.name.size);
  const cap1 = Skia.Font(f.medium, CHROME.cap1.size);
  const cap2 = Skia.Font(f.regular, CHROME.cap2.size);
  const brand = Skia.Font(f.semi, CHROME.brandSize);
  const small = Skia.Font(f.regular, CHROME.last4Size);
  const ch = CARD.chip;
  const chipOutline = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(ch.x, ch.y, ch.s, ch.s), ch.r, ch.r)).build();
  const chipLines = CHIP_LINES_N.map((pl) => pl.map((v, i) => (i % 2 === 0 ? ch.x + v * ch.s : ch.y + v * ch.s)));
  const numberGroups = COPY.number.map((g) => line(number, g));
  const numberX: number[] = [];
  let nx = CARD.number.x;
  for (const g of numberGroups) {
    numberX.push(nx);
    nx += g.width + CARD.number.gap;
  }
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setStrokeJoin(StrokeJoin.Round);
  return {
    effect,
    number,
    numberGroups,
    numberX,
    name,
    nameLine: line(name, COPY.name, CARD.name.tracking),
    cap1,
    cap2,
    capLines: [line(cap1, COPY.ready[0]), line(cap2, COPY.ready[1]), line(cap1, COPY.paid[0]), line(cap2, COPY.paid[1])],
    brand,
    brandLine: line(brand, COPY.brand, -0.2),
    small,
    last4Line: line(small, COPY.last4, 0.6),
    chipOutline,
    chipLines,
    paint,
    stroke,
  };
}

function glyphs(c: SkCanvas, l: Line, x: number, y: number, font: SkFont, p: SkPaint) {
  'worklet';
  c.drawGlyphs(l.ids, l.pos, x, y, font, p);
}

/** A polyline drawn on from its start to `trim` of its length. */
function trimmed(pts: number[], trim: number): SkPath {
  'worklet';
  const path = Skia.PathBuilder.Make();
  let total = 0;
  for (let i = 2; i < pts.length; i += 2) total += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
  let left = total * trim;
  path.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length && left > 0; i += 2) {
    const sl = Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
    const k = Math.min(1, left / sl);
    path.lineTo(pts[i - 2] + (pts[i] - pts[i - 2]) * k, pts[i - 1] + (pts[i + 1] - pts[i - 1]) * k);
    left -= sl;
  }
  return path.build();
}

function smooth01(x: number) {
  'worklet';
  const k = x < 0 ? 0 : x > 1 ? 1 : x;
  return k * k * (3 - 2 * k);
}

/** The lens's place on the glass, in rest-card pt (the dots of the number run into it). */
function lensOnCard(s: Scene) {
  'worklet';
  const m = cardInverse(s);
  const x = LENS.x;
  const y = LENS.y;
  const z = m[6] * x + m[7] * y + m[8];
  const lx = (m[0] * x + m[1] * y + m[2]) / z + s.w / 2;
  const ly = (m[3] * x + m[4] * y + m[5]) / z + s.h / 2;
  return [(lx * CARD.w) / s.w, (ly * CARD.h) / s.h];
}

/**
 * The card number. Collapse: each group condenses (squeezes and blurs) into a dot, 25 ms apart, the
 * last four holding a beat, and the dots run into the lens. Expand: the digits roll up into their
 * slots, 18 ms apart, as the printed face comes round.
 */
function drawNumber(c: SkCanvas, r: Res, s: Scene, p: SkPaint) {
  'worklet';
  const nb = CARD.number;
  const t = s.t;
  const N = NUMBER_FX;
  const condensing = t >= N.condense0 && t < T.expand0;
  const rolling = t >= N.roll0 && t < T.expand1;
  const lens = condensing ? lensOnCard(s) : [0, 0];
  for (let i = 0; i < r.numberGroups.length; i++) {
    const l = r.numberGroups[i];
    const gx = r.numberX[i];
    const cx = gx + l.width / 2;
    const cy = nb.base - nb.size * 0.36;
    if (condensing) {
      const d0 = N.condense0 + i * N.stagger + (i === 3 ? N.lastHold : 0);
      const k = easeInOutCubic(seg(t, d0, d0 + N.condense));
      const run = easeInOutCubic(seg(t, d0 + N.condense * 0.75, d0 + N.condense * 0.75 + N.run));
      if (k < 1) {
        c.save();
        c.translate(cx, cy);
        c.scale(1 - 0.92 * k, 1 - 0.55 * k);
        c.translate(-cx, -cy);
        p.setAlphaf(0.92 * s.face * (1 - 0.5 * k));
        p.setMaskFilter(k > 0.01 ? Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2 * k, true) : null);
        glyphs(c, l, gx, nb.base, r.number, p);
        p.setMaskFilter(null);
        c.restore();
      }
      if (k > 0.45 && run < 1) {
        const a = smooth01((k - 0.45) / 0.3) * (1 - run * run * run);
        p.setAlphaf(0.92 * s.face * a);
        c.drawCircle(cx + (lens[0] - cx) * run, cy + (lens[1] - cy) * run, 1.8 * (1 - 0.4 * run), p);
      }
    } else if (rolling) {
      const pos: { x: number; y: number }[] = [];
      for (let j = 0; j < l.pos.length; j++) {
        const d0 = N.roll0 + (i * 4 + j) * N.rollStagger;
        const e = easeOutCubic(seg(t, d0, d0 + N.roll));
        pos.push({ x: l.pos[j].x, y: (1 - e) * nb.size * 1.05 });
      }
      c.save();
      c.clipRect(Skia.XYWHRect(gx - 2, nb.base - nb.size * 0.95, l.width + 4, nb.size * 1.2), ClipOp.Intersect, true);
      p.setAlphaf(0.92 * s.face);
      c.drawGlyphs(l.ids, pos, gx, nb.base, r.number, p);
      c.restore();
    } else if (s.textNumber * s.face > 0.003) {
      p.setAlphaf(0.92 * s.textNumber * s.face);
      glyphs(c, l, gx, nb.base, r.number, p);
    }
  }
}

/** The printed face, in card-local pt (drawn under the card's matrix). */
function drawFace(c: SkCanvas, r: Res, s: Scene) {
  'worklet';
  const st = r.stroke;
  const ink = (a: number) => {
    st.setColor(Skia.Color(C.ink));
    st.setAlphaf(0.92 * a * s.face);
  };
  st.setStrokeWidth(CARD.stroke);

  // contactless: three concentric arcs bulging left
  if (s.contactless * s.face > 0.003) {
    ink(s.contactless);
    const k = CARD.contactless;
    for (let i = 0; i < k.radii.length; i++) {
      const rr = k.radii[i];
      const cx = k.x + 8;
      const sweep = 2 * k.spread * (180 / Math.PI);
      c.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(cx - rr, k.y - rr, rr * 2, rr * 2), 180 - sweep / 2, sweep).build(), st);
    }
  }
  // chip: outline, then its lines drawn on
  if (s.textChip * s.face > 0.003) {
    ink(s.textChip);
    c.drawPath(r.chipOutline, st);
    if (s.chipLines > 0.001) {
      for (let i = 0; i < r.chipLines.length; i++) c.drawPath(trimmed(r.chipLines[i], s.chipLines), st);
    }
    // the Solune mark as two rings: large, and the small one on its upper-left
    if (s.rings > 0.001) {
      const g = CARD.rings;
      const big = Skia.PathBuilder.Make()
        .addArc(Skia.XYWHRect(g.big.x - g.big.r, g.big.y - g.big.r, g.big.r * 2, g.big.r * 2), -60, 359.9 * s.rings)
        .build();
      const sm = Skia.PathBuilder.Make()
        .addArc(Skia.XYWHRect(g.small.x - g.small.r, g.small.y - g.small.r, g.small.r * 2, g.small.r * 2), 120, 359.9 * s.rings)
        .build();
      c.drawPath(big, st);
      c.drawPath(sm, st);
    }
  }
  const p = r.paint;
  p.setColor(Skia.Color(C.ink));
  drawNumber(c, r, s, p);
  if (s.textName * s.face > 0.003) {
    p.setAlphaf(0.92 * s.textName * s.face);
    glyphs(c, r.nameLine, CARD.name.x, CARD.name.base, r.name, p);
  }
}

/** Three contactless arcs leaving the pill (the pay pulse). */
function drawPulse(c: SkCanvas, r: Res, s: Scene) {
  'worklet';
  if (s.pulse < 0 || s.pulse > 0.75) return;
  const st = r.stroke;
  st.setStrokeWidth(1.5);
  for (let i = 0; i < 3; i++) {
    const u = (s.pulse - i * 0.09) / 0.5;
    if (u <= 0 || u >= 1) continue;
    const e = easeOutCubic(u);
    const rad = 100 + 52 * e;
    st.setColor(Skia.Color('#ffffff'));
    st.setAlphaf(0.5 * (1 - e) * clamp01(u * 8));
    const sweep = 44;
    const rect = Skia.XYWHRect(s.cx - rad, s.cy - rad, rad * 2, rad * 2);
    c.drawPath(Skia.PathBuilder.Make().addArc(rect, -sweep / 2, sweep).addArc(rect, 180 - sweep / 2, sweep).build(), st);
  }
}

function drawChrome(c: SkCanvas, r: Res, s: Scene, g: Geo) {
  'worklet';
  const p = r.paint;
  // top row: the wallet's mark and name, the card's last four
  const base = g.top + CHROME.barTop;
  const mx = CHROME.side + CHROME.markR;
  const my = base - 6;
  p.setAlphaf(1);
  p.setColor(Skia.Color(C.amberLo));
  c.drawCircle(mx + 1.4, my + 0.6, CHROME.markR, p);
  p.setColor(Skia.Color(C.redHi));
  p.setAlphaf(0.92);
  c.drawCircle(mx - 2.6, my - 1.9, CHROME.markR * 0.68, p);
  p.setColor(Skia.Color(C.ink));
  glyphs(c, r.brandLine, CHROME.side + CHROME.markR * 2 + 8, base, r.brand, p);
  p.setColor(Skia.Color(C.inkMute));
  glyphs(c, r.last4Line, REF_W - CHROME.side - r.last4Line.width, base, r.small, p);

  // captions: "Hold near reader" ↔ "Paid $24.80", a 6 pt rise and a cross-fade
  const k = s.caption;
  const lines = r.capLines;
  const draw = (i: number, font: SkFont, y: number, alpha: number, ink: string) => {
    if (alpha <= 0.003) return;
    p.setColor(Skia.Color(ink));
    p.setAlphaf(alpha * (ink === C.ink ? 0.92 : 0.5));
    glyphs(c, lines[i], REF_W / 2 - lines[i].width / 2, y, font, p);
  };
  const out = CHROME.rise * k;
  const inn = CHROME.rise * (1 - k);
  draw(0, r.cap1, CHROME.cap1.base - out, 1 - k, C.ink);
  draw(1, r.cap2, CHROME.cap2.base - out, 1 - k, C.inkMute);
  draw(2, r.cap1, CHROME.cap1.base + inn, k, C.ink);
  draw(3, r.cap2, CHROME.cap2.base + inn, k, C.inkMute);
}

/** Uniforms, in declaration order (gpm-shader). */
function uniforms(s: Scene, g: Geo, lens: number, pools: number, core: number, layer: number): number[] {
  'worklet';
  const u: number[] = [g.px, lens];
  for (let i = 0; i < TRAIL * 3; i++) u.push(s.a[i]);
  for (let i = 0; i < TRAIL * 3; i++) u.push(s.rd[i]);
  const bothFront = (1 - s.frostA) * (1 - s.frostR);
  u.push(s.frostA, s.frostR, s.dock, 18 * bothFront);
  const inv = cardInverse(s);
  for (let i = 0; i < 9; i++) u.push(inv[i]);
  u.push(s.w / 2, s.h / 2, s.r, s.tint);
  u.push(s.flash, s.bright, s.sheen, s.glint);
  // the slab's edge shows while the glass is between 35 and 145 degrees of its turn
  const tn = Math.abs(s.turn);
  const edge = tn > 35 && tn < 145 ? Math.sin(((tn - 35) / 110) * Math.PI) : 0;
  u.push(s.crawl, edge, pools, core);
  u.push(layer, 0);
  return u;
}

export type Look = { lens: number; pools: number; core: number };

/** The bounds of the lights' trails, for the front pass. */
function lightBounds(s: Scene) {
  'worklet';
  let x0 = 1e9;
  let y0 = 1e9;
  let x1 = -1e9;
  let y1 = -1e9;
  const take = (tr: number[], on: boolean) => {
    if (!on) return;
    for (let i = 0; i < TRAIL; i++) {
      const r = tr[i * 3 + 2] + 3;
      x0 = Math.min(x0, tr[i * 3] - r);
      y0 = Math.min(y0, tr[i * 3 + 1] - r);
      x1 = Math.max(x1, tr[i * 3] + r);
      y1 = Math.max(y1, tr[i * 3 + 1] + r);
    }
  };
  take(s.a, s.frostA < 0.999);
  take(s.rd, s.frostR < 0.999);
  return x1 > x0 ? Skia.XYWHRect(x0, y0, x1 - x0, y1 - y0) : null;
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, s: Scene, look: Look) {
  'worklet';
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  // 1. page, lights behind the glass, the glass
  const p = Skia.Paint();
  p.setShader(r.effect.makeShader(uniforms(s, g, look.lens, look.pools, look.core, 0)));
  c.drawRect(Skia.XYWHRect(0, -g.dy / g.s, REF_W, g.h / g.s), p);
  // 2. the printed face, on the glass (it scales with the glass as it condenses)
  if (s.face > 0.003 && Math.abs(s.turn) < 90) {
    c.save();
    c.concat(cardMatrix(s));
    c.scale(s.w / CARD.w, s.h / CARD.h);
    drawFace(c, r, s);
    c.restore();
  }
  // 3. the lights on top of the glass
  const lb = lightBounds(s);
  if (lb) {
    const f = Skia.Paint();
    f.setShader(r.effect.makeShader(uniforms(s, g, look.lens, look.pools, look.core, 1)));
    c.drawRect(lb, f);
  }
  drawPulse(c, r, s);
  drawChrome(c, r, s, g);
  c.restore();
}

/** Reduced motion: the card and the pill cross-fade (k = 0 … 1). */
export function drawCrossfade(c: SkCanvas, r: Res, g: Geo, a: Scene, b: Scene, look: Look, k: number) {
  'worklet';
  const lay = Skia.Paint();
  drawScene(c, r, g, a, look);
  if (k > 0.001) {
    lay.setAlphaf(k);
    lay.setBlendMode(BlendMode.SrcOver);
    c.saveLayer(lay);
    drawScene(c, r, g, b, look);
    c.restore();
  }
}
