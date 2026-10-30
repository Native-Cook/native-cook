// Listening Buddy Paywall — drawing, every frame, in the pin's pixels (see lbp-theme.ts). The buddy is
// our own character, drawn in code: a round orange pebble with a sprout, wearing headphones.

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, StrokeJoin } from '@shopify/react-native-skia';

import { BEAT, bump, clamp01, easeOut, FACES, type Frame, LOOP, mix, smooth, spring } from './lbp-motion';
import { C, COPY, DY, PIN_W, PLANS, S, UI } from './lbp-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface; round: SkTypeface };
export type Res = { f: { title: SkFont; sub: SkFont; name: SkFont; price: SkFont; per: SkFont; popular: SkFont; line: SkFont; link: SkFont; cta: SkFont } };

export function makeRes(f: Faces): Res {
  return {
    f: {
      title: Skia.Font(f.round, 46),
      sub: Skia.Font(f.medium, 26),
      name: Skia.Font(f.bold, 22),
      price: Skia.Font(f.round, 44),
      per: Skia.Font(f.medium, 19),
      popular: Skia.Font(f.bold, 19),
      line: Skia.Font(f.medium, 22),
      link: Skia.Font(f.medium, 22),
      cta: Skia.Font(f.bold, 26),
    },
  };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;

function col(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}
function mixCol(a: string, b: string, k: number, alpha = 1) {
  'worklet';
  const x = col(a);
  const y = col(b);
  return Float32Array.of(mix(x[0], y[0], k), mix(x[1], y[1], k), mix(x[2], y[2], k), alpha);
}
function paint(h: string, a = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(h, a));
  return p;
}
function stroke(h: string, w: number, a = 1) {
  'worklet';
  const p = paint(h, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}
function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}
/** Text with letter spacing (track), its left (ax 0), centre (0.5) or right (1) at x. */
function text(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0, track = 0) {
  'worklet';
  if (track === 0) {
    const w = font.measureText(s).width;
    c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
    return w;
  }
  let w = 0;
  for (let i = 0; i < s.length; i++) w += font.measureText(s[i]).width + (i < s.length - 1 ? track : 0);
  let px = x - w * ax;
  for (let i = 0; i < s.length; i++) {
    c.drawText(s[i], px, cy + font.getSize() * 0.36, p, font);
    px += font.measureText(s[i]).width + track;
  }
  return w;
}
function note(c: SkCanvas, x: number, y: number, s: number, color: string, a: number, double: boolean) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(s, s);
  c.rotate(-18, 0, 0);
  c.drawOval(Skia.XYWHRect(-11, -7, 22, 15), paint(color, a));
  c.restore();
  c.save();
  c.translate(x, y);
  c.scale(s, s);
  c.drawLine(9, -2, 9, -40, stroke(color, 3.4, a));
  if (double) {
    c.drawOval(Skia.XYWHRect(18, -12, 22, 15), paint(color, a));
    c.drawLine(38, -6, 38, -44, stroke(color, 3.4, a));
    c.drawLine(9, -40, 38, -44, stroke(color, 6, a));
  } else {
    const fl = Skia.Path.Make();
    fl.moveTo(9, -40);
    fl.cubicTo(22, -34, 24, -24, 18, -16);
    c.drawPath(fl, stroke(color, 3.4, a));
  }
  c.restore();
}

// ------------------------------------------------------------------ the scene

function drawSky(c: SkCanvas, f: Frame, top: number) {
  'worklet';
  c.drawRect(Skia.XYWHRect(-10, top, PIN_W + 20, UI.white - top), paint(C.sky));
  const drift = 26 * Math.sin((TAU * f.amb) / LOOP);
  const cloud = (x: number, y: number, s: number) => {
    const p = Skia.Path.Make();
    p.moveTo(x - 140 * s, y);
    p.cubicTo(x - 140 * s, y - 40 * s, x - 90 * s, y - 50 * s, x - 70 * s, y - 40 * s);
    p.cubicTo(x - 60 * s, y - 95 * s, x + 30 * s, y - 100 * s, x + 40 * s, y - 50 * s);
    p.cubicTo(x + 80 * s, y - 70 * s, x + 130 * s, y - 40 * s, x + 130 * s, y);
    p.close();
    c.drawPath(p, paint(C.cloud));
  };
  cloud(140 + drift, 155, 1.05);
  cloud(640 - drift * 0.6, 238, 0.9);
  // the hills behind the buddy
  const h1 = Skia.Path.Make();
  h1.moveTo(-10, 470);
  h1.cubicTo(140, 380, 300, 420, 420, 480);
  h1.cubicTo(520, 530, 600, 420, 746, 440);
  h1.lineTo(746, UI.white + 2);
  h1.lineTo(-10, UI.white + 2);
  h1.close();
  c.drawPath(h1, paint(C.hillB));
  const h2 = Skia.Path.Make();
  h2.moveTo(330, UI.white + 2);
  h2.cubicTo(420, 560, 470, 520, 540, 500);
  h2.cubicTo(620, 470, 690, 440, 746, 470);
  h2.lineTo(746, UI.white + 2);
  h2.close();
  c.drawPath(h2, paint(C.hillA));
}

function drawBuddy(c: SkCanvas, f: Frame) {
  'worklet';
  // the face blends from the last plan's to this one's on a spring
  const k = spring(f.selAt, 0.45, 0.62);
  const A = FACES[f.from];
  const B = FACES[f.plan];
  const eyes = mix(A.eyes, B.eyes, k);
  const smile = mix(A.smile, B.smile, k);
  const open = mix(A.open, B.open, k);
  const blush = mix(A.blush, B.blush, k);
  // the beat: a sway and a nod; a happy hop on Continue or a poke
  const beat = (f.amb % BEAT) / BEAT;
  const nod = Math.pow(Math.sin(Math.PI * beat), 2);
  const sway = 2.2 * Math.sin((TAU * f.amb) / (BEAT * 2));
  const hop = Math.max(f.go >= 0 ? 34 * bump(f.go, 0, 0.5) + 16 * bump(f.go, 0.5, 0.35) : 0, f.poke >= 0 ? 26 * bump(f.poke, 0, 0.42) : 0);
  const squeeze = f.poke >= 0 ? bump(f.poke, 0, 0.3) : 0;
  const U = UI.buddy;
  c.save();
  c.translate(U.x, U.y + 6 * nod - hop);
  c.rotate(-6 + sway, 0, 0);
  c.scale(1 + 0.04 * squeeze, 1 - 0.05 * squeeze);
  // the sprout, wiggling on the beat
  for (let i = 0; i < 2; i++) {
    c.save();
    c.translate(-12 + i * 22, -238);
    c.rotate((i === 0 ? -28 : 26) + 8 * Math.sin(TAU * beat + i), 0, 0);
    const lf = Skia.Path.Make();
    lf.moveTo(0, 0);
    lf.cubicTo(-24, -14, -22, -50, 0, -66);
    lf.cubicTo(22, -50, 24, -14, 0, 0);
    lf.close();
    c.drawPath(lf, paint(i === 0 ? C.leaf : C.leafDark));
    c.drawLine(0, -4, 0, -56, stroke('#FFFFFF', 2.4, 0.35));
    c.restore();
  }
  c.drawLine(-2, -232, 2, -246, stroke(C.leafDark, 6));
  // the body: a tall pebble
  const body = Skia.Path.Make();
  body.moveTo(-150, 60);
  body.cubicTo(-158, -60, -150, -170, -100, -215);
  body.cubicTo(-50, -255, 70, -250, 115, -195);
  body.cubicTo(155, -145, 150, -40, 145, 60);
  body.close();
  c.drawPath(body, paint(C.body));
  c.save();
  c.clipPath(body, ClipOp.Intersect, true);
  c.drawOval(Skia.XYWHRect(-120, -235, 150, 110), paint(C.bodyLight, 0.55));
  c.drawOval(Skia.XYWHRect(60, -160, 140, 260), paint(C.bodyShade, 0.4));
  c.restore();
  // cheeks
  c.drawCircle(-92, -78, 26, paint(C.cheek, 0.55 * blush + 0.2));
  c.drawCircle(98, -72, 24, paint(C.cheek, 0.55 * blush + 0.2));
  // eyes: closed happy arcs, open dots, or wide with a shine
  const ink = C.face;
  for (let e = 0; e < 2; e++) {
    const ex = e === 0 ? -34 : 40;
    const ey = -128;
    const closed = clamp01(1 - eyes);
    const wide = clamp01(eyes - 1);
    if (closed > 0.01) {
      const arc = Skia.Path.Make();
      arc.moveTo(ex - 16, ey);
      arc.quadTo(ex, ey + 14 * closed, ex + 16, ey);
      c.drawPath(arc, stroke(ink, 6, closed));
    }
    if (closed < 0.99) {
      const r = mix(7, 13, wide);
      // blinks now and then
      const blink = Math.max(0, 1 - Math.abs(((f.amb + 0.4) % 3) - 0.08) / 0.08);
      // they look down toward the chosen plan
      const lookX = mix(FACES.length ? (f.from - 1) * 4 : 0, (f.plan - 1) * 4, k);
      c.save();
      c.translate(ex + lookX, ey + 2.5);
      c.scale(1, Math.max(0.12, (1 - blink) * (1 - closed)));
      c.drawCircle(0, 0, r, paint(ink));
      if (wide > 0.05) c.drawCircle(-r * 0.35, -r * 0.35, r * 0.32, paint('#FFFFFF', wide));
      c.restore();
    }
  }
  // the mouth: a smile that widens, or an open "o"
  const mw = mix(18, 46, smile) * (1 - 0.6 * open);
  const mp = Skia.Path.Make();
  mp.moveTo(4 - mw, -86);
  mp.quadTo(4, -86 + mix(10, 34, smile), 4 + mw, -86);
  if (open < 0.05) c.drawPath(mp, stroke(ink, 6));
  else {
    c.drawPath(mp, stroke(ink, 6, 1 - open));
    c.drawOval(Skia.XYWHRect(4 - 12 * open, -92, 24 * open, 26 * open), paint(ink, open));
  }
  // headphones: the band over the top, the cups on either side
  const band = Skia.Path.Make();
  band.moveTo(-150, -110);
  band.cubicTo(-160, -290, 150, -300, 140, -100);
  c.drawPath(band, stroke(C.phones, 15));
  c.drawPath(band, stroke(C.phonesLight, 4, 0.6));
  for (const side of [-1, 1]) {
    c.save();
    c.translate(side < 0 ? -150 : 142, -100);
    c.drawRRect(rrect(-26, -44, 52, 88, 24), paint(C.phones));
    c.drawRRect(rrect(-14, -34, 18, 68, 9), paint(C.phonesLight, 0.6));
    c.restore();
  }
  c.restore();
  // the beat rings out of the right cup
  for (let k2 = 0; k2 < 2; k2++) {
    const q = (beat + k2 * 0.5) % 1;
    const rx = U.x + 142 + 30 + 44 * q;
    const a = 0.45 * (1 - q);
    const arc = Skia.Path.Make();
    // small arcs beside the cup, clear of the pager dots below
    arc.addArc(Skia.XYWHRect(rx - 30, U.y - 150 - 14 * q, 60, 64 + 28 * q), -50, 100);
    c.drawPath(arc, stroke(C.phones, 4, a));
  }
}

function drawBushes(c: SkCanvas, f: Frame) {
  'worklet';
  const bob = 3 * Math.sin((TAU * f.amb) / LOOP);
  const bush = (x: number, y: number, s: number, color: string) => {
    const p = Skia.Path.Make();
    p.moveTo(x - 75 * s, y);
    p.cubicTo(x - 80 * s, y - 40 * s, x - 40 * s, y - 60 * s, x - 15 * s, y - 45 * s);
    p.cubicTo(x, y - 85 * s, x + 50 * s, y - 80 * s, x + 50 * s, y - 40 * s);
    p.cubicTo(x + 80 * s, y - 45 * s, x + 85 * s, y - 10 * s, x + 80 * s, y);
    p.close();
    c.drawPath(p, paint(color));
  };
  bush(640, UI.white + 12 + bob, 1, C.bushB);
  bush(540, UI.white + 14 - bob, 1.05, C.bushA);
}

function drawNotes(c: SkCanvas, f: Frame) {
  'worklet';
  // a note drifts up from the cup every 1.5 s
  const U = UI.buddy;
  for (let i = 0; i < 6; i++) {
    const q = (f.amb / 1.5 + i) % 6;
    if (q > 1.6) continue;
    const u = q / 1.6;
    // they stay in the sky between the buddy and the hills, clear of the text above
    const x = U.x + 210 + 24 * Math.sin(u * 5 + i) + 50 * u;
    const y = U.y - 150 - 130 * u;
    note(c, x, y, 0.7 + 0.2 * (i % 2), i % 2 ? C.phones : C.bodyShade, 0.55 * Math.sin(Math.PI * u), i % 3 === 0);
  }
  // Continue: a burst of notes from the bar and the buddy
  if (f.go >= 0 && f.go < 2.4) {
    for (let i = 0; i < 9; i++) {
      const t0 = i * 0.06;
      const u = clamp01((f.go - t0) / 1.8);
      if (u <= 0 || u >= 1) continue;
      const fromBar = i % 3 !== 0;
      const x0 = fromBar ? 120 + i * 60 : U.x + 140;
      const y0 = fromBar ? UI.bar.y : U.y - 160;
      const x = x0 + 50 * Math.sin(u * 6 + i) * u;
      const y = y0 - 520 * easeOut(u);
      note(c, x, y, 0.9 + 0.3 * Math.sin(i), i % 2 ? C.border : C.title, 1 - smooth((u - 0.5) / 0.5), i % 2 === 0);
    }
  }
}

function drawPlans(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const K = UI.cards;
  const Fr = UI.frame;
  const k = spring(f.selAt, 0.42, 0.78);
  // the orange frame slides from the old card to the new one
  const fx = mix(K.x[f.from], K.x[f.plan], k);
  // while it travels the frame stretches along its way and thins a little, like a drop
  const travel = f.from !== f.plan ? Math.sin(Math.PI * clamp01(k)) : 0;
  const fw = (K.w + Fr.pad * 2) * (1 + 0.22 * travel);
  const fh = (K.h + Fr.pad * 2) * (1 - 0.05 * travel);
  const fcx = fx + K.w / 2;
  const fcy = K.y + K.h / 2;
  c.drawRRect(rrect(fcx - fw / 2, fcy - fh / 2, fw, fh, Fr.r), stroke(C.border, Fr.w));
  for (let i = 0; i < 3; i++) {
    const on = i === f.plan ? k : i === f.from ? 1 - k : 0;
    const swell = i === f.plan ? 0.035 * bump(f.selAt, 0, 0.45) : 0;
    const cx = K.x[i] + K.w / 2;
    const cy = K.y + K.h / 2;
    c.save();
    c.translate(cx, cy);
    c.scale(1 + swell, 1 + swell);
    c.translate(-cx, -cy);
    c.drawRRect(rrect(K.x[i], K.y, K.w, K.h, K.r), paint(C.card));
    if (i === 1) {
      c.save();
      c.clipRRect(rrect(K.x[i], K.y, K.w, K.h, K.r), ClipOp.Intersect, true);
      c.drawRect(Skia.XYWHRect(K.x[i], K.y, K.w, UI.header.h), paint(C.header));
      c.restore();
      text(c, COPY.popular[0], cx, K.y + 20, r.f.popular, paint(C.white), 0.5, 4);
      text(c, COPY.popular[1], cx, K.y + 44, r.f.popular, paint(C.white), 0.5, 4);
    }
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(mixCol(C.label, '#3F4560', on));
    const pl = PLANS[i];
    text(c, pl.name, cx, K.y + 106, r.f.name, ink, 0.5, 5);
    // the price, fitted to the card
    const pw = r.f.price.measureText(pl.price).width;
    const sk = Math.min(1, (K.w - 16) / pw);
    c.save();
    c.translate(cx, K.y + 163);
    c.scale(sk, sk);
    text(c, pl.price, 0, 0, r.f.price, ink, 0.5);
    c.restore();
    text(c, pl.per, cx, K.y + 216, r.f.per, paint(C.per), 0.5);
    c.restore();
  }
  // the note under the plans: the old lines leave, the new ones arrive
  const lk = smooth((f.selAt - 0.05) / 0.3);
  for (let l = 0; l < 2; l++) {
    const y = UI.lines[l];
    const prev = PLANS[f.from].lines[l];
    const next = PLANS[f.plan].lines[l];
    if (prev === next || lk >= 1) {
      text(c, next, PIN_W / 2, y, r.f.line, paint(C.note), 0.5);
      continue;
    }
    text(c, prev, PIN_W / 2, y - 12 * lk, r.f.line, paint(C.note, 1 - lk), 0.5);
    text(c, next, PIN_W / 2, y + 12 * (1 - lk), r.f.line, paint(C.note, lk), 0.5);
  }
  const lw = r.f.link.measureText(COPY.links[0]).width;
  const gap = 48;
  const rw = r.f.link.measureText(COPY.links[1]).width;
  const x0 = PIN_W / 2 - (lw + gap + rw) / 2;
  text(c, COPY.links[0], x0, UI.links, r.f.link, paint(C.fine));
  c.drawCircle(x0 + lw + gap / 2, UI.links, 2.5, paint(C.fine));
  text(c, COPY.links[1], x0 + lw + gap, UI.links, r.f.link, paint(C.fine));
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.save();
  c.scale(g.s, g.s);
  c.translate(0, DY);
  c.scale(S, S);
  const top = -DY / S - 10;
  const bottom = (g.h / g.s - DY) / S + 10;
  c.drawRect(Skia.XYWHRect(-10, top, PIN_W + 20, bottom - top), paint(C.white));
  drawSky(c, f, top);
  // close
  const X = UI.close;
  const cs = 1 - 0.04 * f.closeK;
  c.save();
  c.translate(X.x, X.y);
  c.scale(cs, cs);
  const sh = paint('#B9A68F', 0.18);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawCircle(0, 4, X.r, sh);
  c.drawCircle(0, 0, X.r, paint(C.white));
  c.drawLine(-15, -15, 15, 15, stroke(C.close, 4));
  c.drawLine(15, -15, -15, 15, stroke(C.close, 4));
  c.restore();
  text(c, COPY.title, PIN_W / 2, UI.title.y, r.f.title, paint(C.title), 0.5);
  for (let l = 0; l < 2; l++) text(c, COPY.sub[l], PIN_W / 2, UI.sub[l], r.f.sub, paint(C.sub), 0.5);
  for (let i = 0; i < 3; i++) c.drawCircle(UI.dots.x[i], UI.dots.y, 7, paint(i === 0 ? C.dot : C.dotOff));
  // the buddy stands in the sky, cut off by the white sheet below
  c.save();
  c.clipRect(Skia.XYWHRect(-10, top, PIN_W + 20, UI.white - top), ClipOp.Intersect, true);
  drawBuddy(c, f);
  c.restore();
  drawBushes(c, f);
  drawPlans(c, r, f);
  // the bar, full width to the bottom edge
  const B = UI.bar;
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setColor(mixCol(C.bar, '#5B6077', f.press));
  c.drawRect(Skia.XYWHRect(-10, B.y, PIN_W + 20, bottom - B.y), bp);
  const ts = 1 - 0.04 * f.press;
  c.save();
  c.translate(PIN_W / 2, B.y + B.h / 2);
  c.scale(ts, ts);
  text(c, COPY.cta, 0, 0, r.f.cta, paint(C.white), 0.5, 6);
  c.restore();
  drawNotes(c, f);
  c.restore();
}
