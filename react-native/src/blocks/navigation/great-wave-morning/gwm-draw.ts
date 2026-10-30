// Great Wave Morning — drawing, every frame, in the pin's pixels (see gwm-theme.ts). The sea is cut
// paper: eight wavy layers, a winding river with a paper boat, clouds and three curling crests, each
// layer lit from above with a soft shadow on the one behind it.

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkRuntimeEffect, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, easeOut, type Frame, LOOP, mix, PULL, smooth } from './gwm-motion';
import { C, COLLAPSE, COPY, DY, PIN_W, S, UI } from './gwm-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = {
  paper: SkRuntimeEffect;
  f: { hello: SkFont; avatar: SkFont; pill: SkFont; cardLabel: SkFont; big: SkFont; cardSub: SkFont; focus: SkFont; row: SkFont; time: SkFont; status: SkFont; tab: SkFont };
};

const PAPER_SKSL = `
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float grain = h(floor(p * 0.75));
  float fiber = h(floor(p * float2(0.08, 1.4)));
  return half4(0, 0, 0, (grain * 0.55 + fiber * 0.45) * 0.07);
}`;

export function makeRes(f: Faces): Res | null {
  const paper = Skia.RuntimeEffect.Make(PAPER_SKSL);
  if (!paper) return null;
  return {
    paper,
    f: {
      hello: Skia.Font(f.semi, 40),
      avatar: Skia.Font(f.semi, 30),
      pill: Skia.Font(f.medium, 21),
      cardLabel: Skia.Font(f.medium, 23),
      big: Skia.Font(f.semi, 70),
      cardSub: Skia.Font(f.medium, 20),
      focus: Skia.Font(f.semi, 27),
      row: Skia.Font(f.medium, 20.5),
      time: Skia.Font(f.medium, 22),
      status: Skia.Font(f.medium, 19),
      tab: Skia.Font(f.medium, 19),
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
function text(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, ax = 0) {
  'worklet';
  const w = font.measureText(s).width;
  c.drawText(s, x - w * ax, cy + font.getSize() * 0.36, p, font);
  return w;
}
function shadowPaint(a: number, blur: number) {
  'worklet';
  const p = paint('#06142B', a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

// ------------------------------------------------------------------ the sea

/** The header's geometry for a scroll: its bottom edge, how folded it is, how far it is pulled. */
export function seaOf(scroll: number) {
  'worklet';
  const pull = Math.max(0, -scroll);
  const fold = smooth(Math.max(0, scroll) / COLLAPSE);
  const bottom = mix(UI.header.bottom, UI.header.collapsed, fold) + pull;
  return { pull, e: pull / PULL, fold, bottom };
}

/** The river's centre x and half width at a height y (it winds down from the horizon). */
function riverAt(y: number, sea: { pull: number; fold: number }) {
  'worklet';
  const top = 252 + sea.pull * 0.35;
  const u = clamp01((y - top) / (UI.header.bottom + sea.pull - top));
  return { x: 360 + 46 * Math.sin(u * 6.4 + 0.3) * (0.5 + 0.5 * u), w: mix(3, 70, Math.pow(u, 1.6)) };
}

const BASES = [216, 246, 280, 318, 356, 398, 440, 478];
const AMPS = [10, 14, 16, 18, 20, 22, 20, 16];
const LAMS = [150, 120, 170, 140, 190, 150, 210, 170];

function bandPath(i: number, sea: { pull: number; e: number; fold: number; bottom: number }, amb: number) {
  'worklet';
  const k = i / (BASES.length - 1);
  const ph = (TAU * amb) / LOOP;
  // pulled: the sea drops with the content and swells; folded: every layer gathers into one line
  const rest = BASES[i] + sea.pull * (0.25 + 0.75 * k);
  const line = sea.bottom - 22 + (i - 7) * 1.5;
  const base = mix(rest, line, sea.fold);
  const amp = AMPS[i] * (1 + 0.7 * sea.e * (0.4 + k)) * (1 - 0.82 * sea.fold);
  const lam = LAMS[i];
  const p = Skia.Path.Make();
  const step = 12;
  const dip = 0;
  for (let x = -24; x <= PIN_W + 24; x += step) {
    const hill = Math.pow(Math.abs(Math.sin((Math.PI * x) / lam + i * 1.7 + ph * (i % 2 === 0 ? 1 : -1))), 0.8);
    const roll = Math.sin((TAU * x) / (lam * 2.3) + i + ph * 2);
    let y = base - amp * (0.75 * hill + 0.35 * roll);
    if (dip > 0 && sea.fold < 0.999) {
      // the front layers part where the river runs
      const r = riverAt(base, sea);
      const d = (x - r.x) / (r.w * 1.15 + 12);
      y += 130 * Math.exp(-d * d) * (1 - sea.fold);
    }
    if (x === -24) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.lineTo(PIN_W + 24, sea.bottom + 60);
  p.lineTo(-24, sea.bottom + 60);
  p.close();
  return p;
}

function drawClouds(c: SkCanvas, sea: { pull: number; fold: number }, amb: number) {
  'worklet';
  const a = 1 - smooth(sea.fold * 1.6);
  if (a < 0.01) return;
  const drift = 6 * Math.sin((TAU * amb) / LOOP);
  const sink = sea.fold * 60 + sea.pull * 0.15;
  const puff = (cx: number, cy: number, rr: number) => {
    c.drawCircle(cx, cy + 6, rr, paint(C.cloudShade, a));
    c.drawCircle(cx, cy, rr, paint(C.cloud, a));
  };
  // left
  const lx = drift;
  puff(lx + 20, 236 + sink, 34);
  puff(lx + 70, 222 + sink, 30);
  puff(lx + 118, 238 + sink, 24);
  puff(lx + 150, 254 + sink, 16);
  // right
  const rx = -drift;
  puff(rx + 520, 228 + sink, 22);
  puff(rx + 570, 206 + sink, 32);
  puff(rx + 630, 186 + sink, 38);
  puff(rx + 700, 196 + sink, 40);
  puff(rx + 740, 224 + sink, 30);
}

function drawRiver(c: SkCanvas, sea: { pull: number; e: number; fold: number; bottom: number }, amb: number) {
  'worklet';
  const a = 1 - smooth(sea.fold * 1.4);
  if (a < 0.01) return;
  const top = 252 + sea.pull * 0.35;
  const left = Skia.Path.Make();
  const right: { x: number; y: number }[] = [];
  const n = 28;
  for (let i = 0; i <= n; i++) {
    const y = mix(top, sea.bottom + 10, i / n);
    const r = riverAt(y, sea);
    if (i === 0) left.moveTo(r.x - r.w, y);
    else left.lineTo(r.x - r.w, y);
    right.push({ x: r.x + r.w, y });
  }
  for (let i = right.length - 1; i >= 0; i--) left.lineTo(right[i].x, right[i].y);
  left.close();
  const rp = Skia.Paint();
  rp.setAntiAlias(true);
  rp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: 0, y: sea.bottom }, [col(C.river, a), col(C.riverLow, a)], null, TileMode.Clamp));
  c.drawPath(left, shadowPaint(0.5 * a, 6));
  c.drawPath(left, rp);
  c.drawPath(left, stroke('#B8D2EE', 2.5, 0.5 * a));
  // ripples drifting down the river
  const ph = amb / LOOP;
  for (let k = 0; k < 7; k++) {
    const u = (k / 7 + ph) % 1;
    const y = mix(top + 20, sea.bottom - 10, u);
    const r = riverAt(y, sea);
    c.drawLine(r.x - r.w * 0.45, y, r.x + r.w * 0.2, y + 1.5, stroke('#8DB3DB', 2, 0.55 * a * Math.sin(Math.PI * u)));
  }
}

function drawBoat(c: SkCanvas, sea: { pull: number; e: number; fold: number; bottom: number }, amb: number) {
  'worklet';
  const ph = (TAU * 3 * amb) / LOOP;
  const B = UI.boat;
  const restY = B.y + sea.pull * 0.6 + 4 * Math.sin(ph);
  const lineY = sea.bottom - 34;
  const y = mix(restY, lineY, sea.fold);
  const x = mix(riverAt(restY, sea).x, 600, sea.fold);
  const sc = mix(1, 0.62, sea.fold) * (1 + 0.08 * sea.e);
  const tilt = 5 * Math.sin(ph + 1) + 8 * sea.e * Math.sin(ph * 2);
  c.save();
  c.translate(x, y);
  c.rotate(tilt, 0, 0);
  c.scale(sc, sc);
  c.drawOval(Skia.XYWHRect(-40, 14, 80, 10), paint('#173763', 0.35));
  // its wake: two soft lines opening out behind it, with ripples running down them
  const wa = 1 - sea.fold;
  if (wa > 0.02) {
    for (const side of [-1, 1]) {
      c.drawLine(side * 26, 16, side * 70, 64, stroke('#CFE0F4', 3, 0.45 * wa));
      for (let k = 0; k < 3; k++) {
        const u = ((amb / LOOP) * 6 + k / 3) % 1;
        const px = mix(side * 26, side * 70, u);
        const py = mix(16, 64, u);
        c.drawLine(px - side * 6, py - 2, px + side * 2, py + 4, stroke('#FFFFFF', 2.4, 0.55 * wa * Math.sin(Math.PI * u)));
      }
    }
  }
  const hull = Skia.Path.Make();
  hull.moveTo(-38, 0);
  hull.lineTo(38, 0);
  hull.lineTo(26, 16);
  hull.lineTo(-28, 16);
  hull.close();
  c.drawPath(hull, paint(C.boat));
  c.drawLine(-30, 6, 32, 6, stroke(C.sailShade, 2));
  c.drawLine(2, 0, 2, -62, stroke('#2B3B55', 2.4));
  const sail = Skia.Path.Make();
  sail.moveTo(4, -58);
  sail.lineTo(4, -6);
  sail.lineTo(30, -6);
  sail.close();
  c.drawPath(sail, paint(C.sail));
  const sail2 = Skia.Path.Make();
  sail2.moveTo(0, -50);
  sail2.lineTo(0, -6);
  sail2.lineTo(-22, -6);
  sail2.close();
  c.drawPath(sail2, paint(C.sailShade));
  const flag = Skia.Path.Make();
  flag.moveTo(2, -62);
  flag.lineTo(14, -58 + 2 * Math.sin(ph * 3));
  flag.lineTo(2, -54);
  flag.close();
  c.drawPath(flag, paint('#21406E'));
  c.restore();
}

/** A curling crest, cut from paper: a blue body rising into a white ribbon of foam that winds inward. */
function drawCurl(c: SkCanvas, cx: number, cy: number, R: number, dir: number, roll: number, a: number) {
  'worklet';
  if (a < 0.01) return;
  c.save();
  c.translate(cx, cy);
  c.scale(dir, 1);
  // the body: a swell rising from the left into the curl
  const body = Skia.Path.Make();
  body.moveTo(-2.6 * R, 1.25 * R);
  body.cubicTo(-1.7 * R, 1.0 * R, -1.2 * R, -0.1 * R, -0.35 * R, -0.85 * R);
  body.cubicTo(0.25 * R, -1.25 * R, 1.15 * R, -1.05 * R, 1.2 * R, -0.25 * R);
  body.cubicTo(1.22 * R, 0.25 * R, 0.8 * R, 0.5 * R, 0.45 * R, 0.35 * R);
  body.cubicTo(0.9 * R, 0.75 * R, 1.3 * R, 1.0 * R, 1.8 * R, 1.25 * R);
  body.close();
  c.drawPath(body, shadowPaint(0.4 * a, 8));
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: -R, y: -R }, { x: R, y: R }, [col('#5C8CC8', a), col('#2C5590', a), col('#1C3F72', a)], [0, 0.55, 1], TileMode.Clamp));
  c.drawPath(body, bp);
  // inner lines of the swell, like layered paper
  for (let k = 0; k < 3; k++) {
    const q = Skia.Path.Make();
    const o = 0.22 * R * (k + 1);
    q.moveTo(-2.2 * R + o, 1.2 * R);
    q.cubicTo(-1.4 * R + o, 0.9 * R, -0.9 * R + o * 0.6, 0.1 * R + o * 0.4, -0.25 * R + o * 0.5, -0.5 * R + o * 0.6);
    c.drawPath(q, stroke('#7FA7DA', 3, 0.35 * a));
  }
  // the foam: a white ribbon, wide at the lip, winding into the eye of the curl
  const ox = 0.42 * R;
  const oy = -0.2 * R;
  const n = 36;
  const outer = Skia.Path.Make();
  const inner: { x: number; y: number }[] = [];
  for (let i = 0; i <= n; i++) {
    const u = i / n;
    const ang = Math.PI * 1.02 + u * Math.PI * 1.65 + roll * 0.25;
    const ro = R * (1.0 - 0.62 * u);
    const wv = R * (0.4 * Math.pow(1 - u, 1.1) + 0.04);
    const x = ox + Math.cos(ang) * ro * 0.95;
    const y = oy + Math.sin(ang) * ro * 0.82;
    if (i === 0) outer.moveTo(x - 0.12 * R, y + 0.25 * R);
    outer.lineTo(x, y);
    inner.push({ x: ox + Math.cos(ang) * (ro - wv) * 0.95, y: oy + Math.sin(ang) * (ro - wv) * 0.82 });
  }
  for (let i = inner.length - 1; i >= 0; i--) outer.lineTo(inner[i].x, inner[i].y);
  outer.close();
  c.drawPath(outer, shadowPaint(0.3 * a, 5));
  const fp = Skia.Paint();
  fp.setAntiAlias(true);
  fp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -R }, { x: 0, y: R * 0.4 }, [col('#FBF8F2', a), col(C.crest, a), col(C.crestShade, a)], [0, 0.6, 1], TileMode.Clamp));
  c.drawPath(outer, fp);
  // the lip breaks into a few round lumps of foam
  for (let k = 0; k < 3; k++) {
    const u = 0.02 + k * 0.07;
    const ang = Math.PI * 1.02 + u * Math.PI * 1.65 + roll * 0.25;
    const ro = R * (1.0 - 0.62 * u);
    c.drawCircle(ox + Math.cos(ang) * ro * 0.98, oy + Math.sin(ang) * ro * 0.86, R * (0.13 - k * 0.025), paint(C.crest, a));
  }
  c.restore();
}

function drawFoam(c: SkCanvas, f: Frame, crests: { x: number; y: number; d: number }[]) {
  'worklet';
  if (f.foam < 0) return;
  const t = f.foam;
  for (let k = 0; k < crests.length; k++) {
    const cr = crests[k];
    for (let i = 0; i < 16; i++) {
      const h1 = Math.sin((i + 1) * 12.9898 + k * 7.1) * 43758.5453;
      const r1 = h1 - Math.floor(h1);
      const h2 = Math.sin((i + 1) * 39.35 + k * 3.3) * 24634.63;
      const r2 = h2 - Math.floor(h2);
      const ang = -Math.PI / 2 + (r1 - 0.5) * 1.6 + cr.d * 0.4;
      const v = (180 + 220 * r2) * f.foamPow;
      const x = cr.x + Math.cos(ang) * v * t;
      const y = cr.y + Math.sin(ang) * v * t + 420 * t * t;
      const a = (1 - smooth(t / 1.2)) * 0.9;
      if (a < 0.02) continue;
      c.drawCircle(x, y, 3 + 4 * r1 * (1 - t), paint(C.crest, a));
    }
  }
}

function drawSea(c: SkCanvas, f: Frame) {
  'worklet';
  const sea = seaOf(f.scroll);
  const amb = f.amb;
  // the page's torn edge: the sea is cut along it
  const edgeY = (x: number) => sea.bottom + 4 * Math.sin(x * 0.07 + 1.3) + 2 * Math.sin(x * 0.23);
  const above = Skia.Path.Make();
  above.moveTo(-24, -DY / S - 20);
  above.lineTo(PIN_W + 24, -DY / S - 20);
  for (let x = PIN_W + 24; x >= -24; x -= 16) above.lineTo(x, edgeY(x));
  above.close();
  c.save();
  c.clipPath(above, ClipOp.Intersect, true);
  // sky
  const sp = Skia.Paint();
  sp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -DY / S }, { x: 0, y: sea.bottom }, [col(C.sky), col(C.skyLow)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-30, -DY / S - 10, PIN_W + 60, sea.bottom + DY / S + 70), sp);
  // tilt: far layers barely move, near ones move most, so the sea has depth under the hand
  const tx = f.tiltX ?? 0;
  const ty = f.tiltY ?? 0;
  c.save();
  c.translate(tx * 4, ty * 2);
  drawClouds(c, sea, amb);
  c.restore();
  for (let i = 0; i < BASES.length; i++) {
    const p = bandPath(i, sea, amb);
    const depth = (i + 1) / BASES.length;
    c.save();
    c.translate(tx * 18 * depth, ty * 8 * depth);
    c.save();
    c.translate(0, -5);
    c.drawPath(p, shadowPaint(0.45, 6));
    c.restore();
    c.drawPath(p, paint(C.bands[i]));
    // a paler lip along each cut edge, where the light catches the paper
    c.save();
    c.clipPath(p, ClipOp.Intersect, true);
    c.translate(0, 3);
    c.drawPath(p, stroke('#FFFFFF', 3, 0.08));
    c.restore();
    c.restore();
  }
  c.save();
  c.translate(tx * 12, ty * 5);
  drawRiver(c, sea, amb);
  drawBoat(c, sea, amb);
  // the crests: they swell and roll forward when the sea is pulled
  const ph = (TAU * amb) / LOOP;
  const roll = 0.18 * Math.sin(ph * 2) + 0.5 * sea.e;
  const ca = 1 - smooth(sea.fold * 1.5);
  const crests = [
    { x: 120 - 10 * sea.e, y: 380 + sea.pull * 0.75, R: 78 * (1 + 0.25 * sea.e), d: 1 },
    { x: 600 + 10 * sea.e, y: 330 + sea.pull * 0.55, R: 64 * (1 + 0.25 * sea.e), d: -1 },
    { x: 650 + 8 * sea.e, y: 462 + sea.pull * 0.9, R: 52 * (1 + 0.3 * sea.e), d: -1 },
  ];
  for (let k = 0; k < crests.length; k++) drawCurl(c, crests[k].x, crests[k].y - 20 * sea.fold, crests[k].R * (1 - 0.6 * sea.fold), crests[k].d, roll + k * 0.3, ca);
  c.restore();
  c.restore();
  // the paper sea casts a soft shadow on the page below its edge
  const line = Skia.Path.Make();
  for (let x = -24; x <= PIN_W + 24; x += 16) {
    if (x === -24) line.moveTo(x, edgeY(x) + 3);
    else line.lineTo(x, edgeY(x) + 3);
  }
  const lp = stroke('#06142B', 6, 0.18);
  lp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  c.drawPath(line, lp);
  // the foam flies over the page too
  drawFoam(
    c,
    f,
    crests.map((cr) => ({ x: cr.x - cr.d * cr.R * 0.2, y: cr.y - cr.R, d: cr.d })),
  );
}

function drawHeaderText(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const sea = seaOf(f.scroll);
  const fold = sea.fold;
  // the greeting shrinks a little into the folded bar; the sprint line folds away
  const s = mix(1, 0.86, fold);
  c.save();
  c.translate(UI.hello.x, UI.hello.y + sea.pull * 0.15 + 4 * fold);
  c.scale(s, s);
  text(c, COPY.hello, 0, 0, r.f.hello, paint('#FFFFFF'));
  c.restore();
  const pa = 1 - smooth(fold * 2.2);
  if (pa > 0.01) {
    const P = UI.pill;
    const f0 = r.f.pill;
    const w0 = f0.measureText(COPY.sprint[0]).width;
    const w1 = f0.measureText(COPY.sprint[1]).width;
    const w2 = f0.measureText(COPY.sprint[2]).width;
    const pw = w0 + w1 + w2 + 24 + 36;
    const py = P.y + sea.pull * 0.15 - 12 * fold;
    c.drawRRect(rrect(P.x, py, pw, P.h, P.h / 2), paint(C.pill, pa));
    let x = P.x + 18;
    x += text(c, COPY.sprint[0], x, py + P.h / 2, f0, paint('#FFFFFF', pa)) + 12;
    x += text(c, COPY.sprint[1], x, py + P.h / 2, f0, paint('#FFFFFF', 0.7 * pa)) + 12;
    text(c, COPY.sprint[2], x, py + P.h / 2, f0, paint('#FFFFFF', pa));
  }
  const A = UI.avatar;
  const as = mix(1, 0.78, fold);
  c.save();
  c.translate(A.x, A.y + sea.pull * 0.15);
  c.scale(as, as);
  c.drawCircle(0, 0, A.r + 4, paint(C.avatarRing));
  c.drawCircle(0, 0, A.r, paint(C.avatar));
  text(c, COPY.initials, 0, 0, r.f.avatar, paint(C.navy), 0.5);
  c.restore();
}

// ------------------------------------------------------------------ the content

function drawContent(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const off = -f.scroll;
  c.save();
  c.translate(0, off);
  const K = UI.cards;
  for (let i = 0; i < 3; i++) {
    const st = COPY.stats[i];
    const x = K.x[i];
    c.drawRRect(rrect(x + 2, K.y + 6, K.w, K.h, K.r), paint(C.cardEdge));
    c.drawRRect(rrect(x, K.y, K.w, K.h, K.r), paint(C.card));
    // a little cut-paper wave in its corner, rocking
    c.save();
    c.clipRRect(rrect(x, K.y, K.w, K.h, K.r), ClipOp.Intersect, true);
    const w = Skia.Path.Make();
    const ph = (TAU * f.amb) / LOOP + i;
    w.moveTo(x + K.w * 0.42, K.y + K.h);
    w.cubicTo(x + K.w * 0.6, K.y + K.h - 18 - 3 * Math.sin(ph), x + K.w * 0.72, K.y + K.h - 46 + 4 * Math.sin(ph), x + K.w, K.y + K.h - 58);
    w.lineTo(x + K.w, K.y + K.h);
    w.close();
    c.drawPath(w, paint(st.wave));
    c.restore();
    text(c, st.label, x + 26, K.y + 38, r.f.cardLabel, paint(C.navy));
    text(c, st.value, x + 24, K.y + 96, r.f.big, paint(st.color));
    const sw = st.sub[0] ? text(c, st.sub[0], x + 26, K.y + 142, r.f.cardSub, paint(C.tan)) : 0;
    text(c, st.sub[1], x + 26 + sw, K.y + 142, r.f.cardSub, paint(C.navy));
  }
  for (let i = 0; i < 3; i++) c.drawCircle(UI.dots.x[i], UI.dots.y, 5.5, paint(i === 0 ? C.blue : '#D6C9AE'));
  text(c, COPY.focus, 42, UI.focus.y, r.f.focus, paint(C.navy));
  c.drawLine(220, UI.focus.lineY, 648, UI.focus.lineY, stroke(C.line, 1.5));
  // a tiny wave at the end of the line
  const tw = Skia.Path.Make();
  tw.moveTo(652, UI.focus.lineY);
  tw.cubicTo(660, UI.focus.lineY - 12, 676, UI.focus.lineY - 12, 680, UI.focus.lineY - 4);
  tw.cubicTo(672, UI.focus.lineY - 8, 668, UI.focus.lineY - 2, 674, UI.focus.lineY);
  tw.lineTo(704, UI.focus.lineY);
  c.drawPath(tw, stroke(C.line, 1.5));
  const R = UI.rows;
  for (let i = 0; i < COPY.rows.length; i++) {
    const row = COPY.rows[i];
    const y = R.y0 + i * R.pitch;
    const dn = f.done[i];
    const press = f.pressRow === i ? f.pressK : 0;
    c.save();
    c.drawRRect(rrect(R.x + 2, y + 5, R.w, R.h, 6), paint(C.cardEdge, 0.9));
    // a full-width row highlights its paper when pressed (it does not scale)
    c.drawRRect(rrect(R.x, y, R.w, R.h, 6), paint(C.row));
    if (press > 0.01) c.drawRRect(rrect(R.x, y, R.w, R.h, 6), paint('#E9DFC9', 0.7 * press));
    const bp = Skia.Paint();
    bp.setAntiAlias(true);
    bp.setColor(mixCol(row.bar, COPY.done.bar, dn));
    c.drawRRect(rrect(R.x, y, 14, R.h, 4), bp);
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(mixCol(C.navy, C.grey, dn));
    const tw2 = text(c, row.title, 74, y + R.h / 2, r.f.row, tp);
    if (dn > 0.01) c.drawLine(74, y + R.h / 2 + 1, 74 + tw2 * easeOut(dn), y + R.h / 2 + 1, stroke(C.grey, 1.6));
    text(c, row.time, 538, y + R.h / 2, r.f.time, paint(C.time), 1);
    // the status pill flips to Done
    const label = dn > 0.5 ? COPY.done.status : row.status;
    const pw = Math.max(98, r.f.status.measureText(label).width + 30);
    const flip = Math.abs(Math.cos(Math.PI * dn));
    c.save();
    c.translate(632, y + R.h / 2);
    c.scale(1, Math.max(0.05, flip));
    const pp = Skia.Paint();
    pp.setAntiAlias(true);
    pp.setColor(dn > 0.5 ? col(COPY.done.pill) : col(row.pill));
    c.drawRRect(rrect(-pw / 2, -21, pw, 42, 21), pp);
    text(c, label, 0, 0, r.f.status, paint(dn > 0.5 ? COPY.done.ink : row.ink), 0.5);
    c.restore();
    c.restore();
  }
  c.restore();
}

function tabIcon(c: SkCanvas, i: number, x: number, y: number, color: string) {
  'worklet';
  const p = stroke(color, 3.4);
  const fp = paint(color);
  if (i === 0) {
    const h = Skia.Path.Make();
    h.moveTo(x - 18, y);
    h.lineTo(x, y - 17);
    h.lineTo(x + 18, y);
    h.lineTo(x + 13, y);
    h.lineTo(x + 13, y + 16);
    h.lineTo(x + 4, y + 16);
    h.lineTo(x + 4, y + 6);
    h.lineTo(x - 4, y + 6);
    h.lineTo(x - 4, y + 16);
    h.lineTo(x - 13, y + 16);
    h.lineTo(x - 13, y);
    h.close();
    c.drawPath(h, fp);
  } else if (i === 1) {
    for (let k = 0; k < 3; k++) {
      c.drawCircle(x - 15, y - 12 + k * 12, 3, fp);
      c.drawLine(x - 6, y - 12 + k * 12, x + 18, y - 12 + k * 12, p);
    }
  } else if (i === 2) {
    c.drawRRect(rrect(x - 20, y - 10, 40, 28, 4), fp);
    c.drawRRect(rrect(x - 7, y - 17, 14, 9, 2), stroke(color, 3));
    c.drawLine(x - 20, y + 2, x + 20, y + 2, stroke(C.tabBg, 2));
  } else if (i === 3) {
    c.drawCircle(x - 8, y - 8, 7, fp);
    c.drawCircle(x + 10, y - 6, 6, fp);
    c.drawRRect(rrect(x - 20, y + 2, 24, 14, 7), fp);
    c.drawRRect(rrect(x + 2, y + 3, 20, 13, 6), fp);
  } else {
    c.drawRect(Skia.XYWHRect(x - 16, y + 2, 7, 14), fp);
    c.drawRect(Skia.XYWHRect(x - 4, y - 6, 7, 22), fp);
    c.drawRect(Skia.XYWHRect(x + 8, y - 16, 7, 32), fp);
    c.drawLine(x - 20, y + 17, x + 20, y + 17, stroke(color, 2.5));
  }
}

function drawTabs(c: SkCanvas, r: Res, f: Frame, screenH: number) {
  'worklet';
  const T = UI.tab;
  c.drawRect(Skia.XYWHRect(-10, T.y, PIN_W + 20, screenH), paint(C.tabBg));
  c.drawLine(0, T.y, PIN_W, T.y, stroke(C.line, 1.5));
  // the selected tile slides to its tab
  const pos = f.tab;
  const i0 = Math.max(0, Math.min(4, Math.floor(pos)));
  const i1 = Math.min(4, i0 + 1);
  const tx = mix(T.x[i0], T.x[i1], pos - i0);
  c.drawRRect(rrect(tx - T.tile / 2, T.y + 12, T.tile, 64, 14), paint(C.blue));
  for (let i = 0; i < 5; i++) {
    const on = Math.max(0, 1 - Math.abs(pos - i));
    const ink = mixCol(C.tabIcon, '#FFFFFF', on);
    const h = (v: number) => Math.round(v * 255).toString(16).padStart(2, '0');
    tabIcon(c, i, T.x[i], T.y + 44, `#${h(ink[0])}${h(ink[1])}${h(ink[2])}`);
    const lp = Skia.Paint();
    lp.setAntiAlias(true);
    lp.setColor(mixCol(C.tabIcon, C.navy, on));
    text(c, COPY.tabs[i], T.x[i], T.y + 94, r.f.tab, lp, 0.5);
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame) {
  'worklet';
  c.drawColor(col(C.page));
  c.save();
  c.scale(g.s, g.s);
  c.translate(0, DY);
  c.scale(S, S);
  const screenH = (g.h / g.s - DY) / S;
  drawContent(c, r, f);
  drawSea(c, f);
  drawHeaderText(c, r, f);
  drawTabs(c, r, f, screenH);
  c.restore();
  // the paper's grain over everything
  const gp = Skia.Paint();
  gp.setShader(r.paper.makeShader([]));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), gp);
}
