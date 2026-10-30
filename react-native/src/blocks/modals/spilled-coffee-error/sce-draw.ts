// Spilled Coffee Error — drawing, every frame, in the pin's pixels (see sce-theme.ts). The cup is a
// real tapered cylinder turned in 3D and projected; the spilled coffee is a metaball puddle.

import { BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkRuntimeEffect, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, easeOut, LOOP, mix, type Pose, smooth } from './sce-motion';
import { C, COPY, DY, PIN_W, S, SCENE, UI } from './sce-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface };
export type Res = { grain: SkRuntimeEffect; f: { title: SkFont; body: SkFont; button: SkFont } };

const GRAIN_SKSL = `
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float g = h(floor(p * 0.9));
  // premultiplied: a faint cool grain
  return half4(0.18, 0.2, 0.3, 1.0) * (g * 0.05);
}`;

export function makeRes(f: Faces): Res | null {
  const grain = Skia.RuntimeEffect.Make(GRAIN_SKSL);
  if (!grain) return null;
  return { grain, f: { title: Skia.Font(f.semi, 34), body: Skia.Font(f.medium, 25), button: Skia.Font(f.semi, 25) } };
}

export type Geo = { w: number; h: number; s: number };

const TAU = Math.PI * 2;
type V3 = { x: number; y: number; z: number };
type P2 = { x: number; y: number };

function col(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
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
function blurred(h: string, a: number, sigma: number) {
  'worklet';
  const p = paint(h, a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}
function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}
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

// ------------------------------------------------------------------ 3D

const CE = Math.cos(SCENE.elev);
const SE = Math.sin(SCENE.elev);
const FALL: V3 = { x: -Math.cos(SCENE.yaw), y: 0, z: Math.sin(SCENE.yaw) };
const SIDE: V3 = { x: -FALL.z, y: 0, z: FALL.x }; // horizontal, across the fall
const VIEW: V3 = { x: 0, y: SE, z: CE }; // toward the eye

function proj(p: V3): P2 {
  'worklet';
  return { x: SCENE.cup.base.x + p.x, y: SCENE.cup.base.y - p.y * CE + p.z * SE };
}
function dot(a: V3, b: V3) {
  'worklet';
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

/** The cup's frame at a tilt of θ degrees: the base centre, the axis and the two directions across it. */
function cupFrame(theta: number) {
  'worklet';
  const t = (theta * Math.PI) / 180;
  const ct = Math.cos(t);
  const st = Math.sin(t);
  const rb = SCENE.cup.rb;
  const axis: V3 = { x: st * FALL.x, y: ct, z: st * FALL.z };
  const v: V3 = { x: ct * FALL.x, y: -st, z: ct * FALL.z };
  const base: V3 = { x: rb * (1 - ct) * FALL.x, y: rb * st, z: rb * (1 - ct) * FALL.z };
  return { axis, u: SIDE, v, base };
}
function ringPoint(fr: ReturnType<typeof cupFrame>, h: number, r: number, ang: number): V3 {
  'worklet';
  const ca = Math.cos(ang);
  const sa = Math.sin(ang);
  return {
    x: fr.base.x + fr.axis.x * h + r * (fr.u.x * ca + fr.v.x * sa),
    y: fr.base.y + fr.axis.y * h + r * (fr.u.y * ca + fr.v.y * sa),
    z: fr.base.z + fr.axis.z * h + r * (fr.u.z * ca + fr.v.z * sa),
  };
}
function ring(fr: ReturnType<typeof cupFrame>, h: number, r: number, n = 40) {
  'worklet';
  const pts: P2[] = [];
  for (let i = 0; i < n; i++) pts.push(proj(ringPoint(fr, h, r, (i / n) * TAU)));
  return pts;
}
function polyPath(pts: P2[]) {
  'worklet';
  const p = Skia.Path.Make();
  for (let i = 0; i < pts.length; i++) {
    if (i === 0) p.moveTo(pts[i].x, pts[i].y);
    else p.lineTo(pts[i].x, pts[i].y);
  }
  p.close();
  return p;
}
function hull(pts: P2[]) {
  'worklet';
  const s = pts.slice().sort((a, b) => (a.x === b.x ? a.y - b.y : a.x - b.x));
  const cross = (o: P2, a: P2, b: P2) => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
  const lower: P2[] = [];
  for (const p of s) {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) lower.pop();
    lower.push(p);
  }
  const upper: P2[] = [];
  for (let i = s.length - 1; i >= 0; i--) {
    const p = s[i];
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) upper.pop();
    upper.push(p);
  }
  upper.pop();
  lower.pop();
  return lower.concat(upper);
}
/** The part of a band around the cup (heights h0…h1) that faces the eye. */
function band(fr: ReturnType<typeof cupFrame>, h0: number, h1: number) {
  'worklet';
  const L = SCENE.cup.L;
  const r0 = mix(SCENE.cup.rb, SCENE.cup.rm, h0 / L);
  const r1 = mix(SCENE.cup.rb, SCENE.cup.rm, h1 / L);
  const n = 48;
  const vis: boolean[] = [];
  for (let i = 0; i < n; i++) {
    const ang = (i / n) * TAU;
    const nrm: V3 = {
      x: fr.u.x * Math.cos(ang) + fr.v.x * Math.sin(ang),
      y: fr.u.y * Math.cos(ang) + fr.v.y * Math.sin(ang),
      z: fr.u.z * Math.cos(ang) + fr.v.z * Math.sin(ang),
    };
    vis.push(dot(nrm, VIEW) > -0.02);
  }
  // walk the visible arc from where it starts, so it never wraps round 0 in the middle
  let start = 0;
  for (let i = 0; i < n; i++) {
    if (!vis[i] && vis[(i + 1) % n]) {
      start = (i + 1) % n;
      break;
    }
  }
  const front: P2[] = [];
  const back: P2[] = [];
  for (let k = 0; k <= n; k++) {
    const i = (start + k) % n;
    if (!vis[i] && k > 0) break;
    const ang = (i / n) * TAU;
    front.push(proj(ringPoint(fr, h0, r0, ang)));
    back.push(proj(ringPoint(fr, h1, r1, ang)));
  }
  if (front.length < 2) return null;
  return { front, back };
}

// ------------------------------------------------------------------ the room

function drawRoom(c: SkCanvas, amb: number, topY: number, bottomY: number) {
  'worklet';
  const wall = Skia.Paint();
  wall.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: topY }, { x: 0, y: SCENE.floorY }, [col(C.wallTop), col(C.wallLow)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-10, topY, PIN_W + 20, SCENE.floorY - topY), wall);
  const floor = Skia.Paint();
  floor.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: SCENE.floorY }, { x: 0, y: SCENE.floorY + 160 }, [col(C.floorFar), col(C.floor)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-10, SCENE.floorY, PIN_W + 20, bottomY - SCENE.floorY), floor);
  // the wall darkens toward the floor, the floor meets it in a soft shade
  c.drawRect(Skia.XYWHRect(-10, SCENE.floorY - 2, PIN_W + 20, 10), blurred('#D5DAE1', 0.6, 6));
  // a soft light from the upper right on the wall
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: 560, y: 60 }, 380, [col('#FFFFFF', 0.9), col('#FFFFFF', 0)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(150, topY, PIN_W, SCENE.floorY - topY), glow);
  // the books, lying spine out on the right
  const book = (x: number, y: number, w: number, h: number, d: number, circleX: number) => {
    c.drawRect(Skia.XYWHRect(x - 6, y + h - 4, w + 30, 18), blurred(C.shadow, 0.5, 9));
    const top = Skia.Path.Make();
    top.moveTo(x, y);
    top.lineTo(x + d, y - d * 0.55);
    top.lineTo(x + w + d, y - d * 0.55);
    top.lineTo(x + w, y);
    top.close();
    c.drawPath(top, paint(C.bookTop));
    c.drawRRect(rrect(x, y, w, h, 6), paint(C.book));
    c.drawRect(Skia.XYWHRect(x, y + h - 10, w, 10), paint(C.bookSide, 0.7));
    c.drawCircle(circleX, y + h * 0.42, h * 0.17, paint('#FFFFFF'));
    c.drawCircle(circleX, y + h * 0.42, h * 0.17, stroke('#C6CDD6', 2));
    c.drawRRect(rrect(circleX + h * 0.3, y + h * 0.36, w - (circleX - x) - h * 0.4, h * 0.12, 3), paint('#FFFFFF'));
    c.drawRRect(rrect(circleX + h * 0.3, y + h * 0.36, w - (circleX - x) - h * 0.4, h * 0.12, 3), stroke('#C6CDD6', 1.5));
  };
  book(446, 386, 240, 112, 26, 474);
  book(516, 312, 170, 86, 22, 544);
  // the plant in the corner, its leaves swaying a little
  c.drawOval(Skia.XYWHRect(-40, 420, 170, 34), blurred(C.shadow, 0.6, 12));
  const pot = Skia.Path.Make();
  pot.moveTo(-34, 196);
  pot.lineTo(112, 196);
  pot.lineTo(98, 438);
  pot.quadTo(40, 450, -20, 438);
  pot.close();
  const pp = Skia.Paint();
  pp.setAntiAlias(true);
  pp.setShader(Skia.Shader.MakeLinearGradient({ x: -34, y: 0 }, { x: 112, y: 0 }, [col('#FFFFFF'), col(C.pot), col(C.potShade)], [0, 0.55, 1], TileMode.Clamp));
  c.drawPath(pot, pp);
  c.drawOval(Skia.XYWHRect(-34, 184, 146, 26), paint('#E3E7EC'));
  c.drawOval(Skia.XYWHRect(-28, 188, 134, 18), paint('#C9CED6'));
  const sway = (TAU * amb) / LOOP;
  const leaf = (bx: number, by: number, len: number, wid: number, ang: number, ca: string, cb: string, k: number) => {
    c.save();
    c.translate(bx, by);
    c.rotate(ang + 2.2 * Math.sin(sway + k), 0, 0);
    const l = Skia.Path.Make();
    l.moveTo(0, 0);
    l.cubicTo(-wid, -len * 0.3, -wid * 0.6, -len * 0.85, 0, -len);
    l.cubicTo(wid * 0.7, -len * 0.8, wid, -len * 0.3, 0, 0);
    l.close();
    const lp = Skia.Paint();
    lp.setAntiAlias(true);
    lp.setShader(Skia.Shader.MakeLinearGradient({ x: -wid, y: 0 }, { x: wid, y: 0 }, [col(ca), col(cb)], null, TileMode.Clamp));
    c.drawPath(l, lp);
    c.drawLine(0, 0, 0, -len * 0.92, stroke('#FFFFFF', 1.4, 0.25));
    c.restore();
  };
  leaf(20, 196, 280, 66, -14, C.leafC, C.leafA, 0);
  leaf(46, 196, 250, 58, 12, C.leafA, C.leafB, 1.3);
  leaf(8, 196, 200, 50, -38, C.leafA, C.leafB, 2.1);
  leaf(74, 196, 170, 44, 34, C.leafB, C.leafA, 3.4);
  leaf(-10, 196, 236, 54, -2, C.leafB, C.leafC, 4.6);
}

function drawPaper(c: SkCanvas) {
  'worklet';
  const P = SCENE.paper;
  const p = polyPath(P);
  c.save();
  c.translate(4, 7);
  c.drawPath(p, blurred(C.shadow, 0.55, 7));
  c.restore();
  c.drawPath(p, paint(C.paper));
}

// the puddle: blobs on the floor, revealed one after another as the coffee spills
const BLOBS = [
  [282, 566, 24],
  [258, 574, 30],
  [228, 581, 34],
  [197, 587, 30],
  [170, 590, 24],
  [212, 595, 20],
  [244, 590, 18],
  [148, 592, 16],
  [298, 576, 14],
];

function drawPuddle(c: SkCanvas, pose: Pose, mouth: P2) {
  'worklet';
  const s = pose.spill;
  if (s <= 0.002) return;
  const n = BLOBS.length;
  const layer = Skia.Paint();
  layer.setColorFilter(Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 22, -9]));
  c.saveLayer(layer);
  const bp = blurred(C.coffee, 1, 7);
  const wob = (TAU * pose.amb) / LOOP;
  for (let i = 0; i < n; i++) {
    const k = smooth(s * n * 1.25 - i);
    if (k <= 0.01) continue;
    const b = BLOBS[i];
    // while it grows (or flows back) a blob slides from the mouth to its place
    const x = mix(mouth.x, b[0], easeOut(k)) + 1.5 * Math.sin(wob * 2 + i);
    const y = mix(mouth.y + 30, b[1], easeOut(k));
    const r = b[2] * k;
    c.drawOval(Skia.XYWHRect(x - r * 1.35, y - r * 0.42, r * 2.7, r * 0.84), bp);
  }
  c.restore();
  // a highlight on the coffee's surface
  for (let i = 0; i < 4; i++) {
    const k = smooth(s * n * 1.25 - i);
    if (k <= 0.2) continue;
    const b = BLOBS[i];
    c.drawOval(Skia.XYWHRect(b[0] - b[2] * 0.6, b[1] - b[2] * 0.22, b[2] * 0.9, b[2] * 0.16), paint(C.coffeeLight, 0.55 * k));
  }
  // rings where a drop or the stream hits
  const hit = (t: number, x: number, y: number) => {
    if (t < 0 || t > 1.1) return;
    for (let k = 0; k < 2; k++) {
      const q = clamp01((t - k * 0.18) / 0.9);
      if (q <= 0) continue;
      c.drawOval(Skia.XYWHRect(x - 30 * q, y - 9 * q, 60 * q, 18 * q), stroke(C.coffeeLight, 2 * (1 - q), 0.8 * (1 - q)));
    }
  };
  hit(pose.splash, 236, 584);
  // droplets thrown up where the coffee lands, falling back on their arcs
  if (pose.splash >= 0 && pose.splash < 0.75) {
    const t = pose.splash;
    for (let i = 0; i < 12; i++) {
      const h1 = Math.sin((i + 1) * 12.9898) * 43758.5453;
      const r1 = h1 - Math.floor(h1);
      const ang = -Math.PI / 2 + (r1 - 0.5) * 2.2;
      const v = 160 + 140 * ((i * 0.37) % 1);
      const x = 236 + Math.cos(ang) * v * t * 1.3;
      const y = 580 + Math.sin(ang) * v * t * 0.9 + 520 * t * t;
      if (y > 600) continue;
      const a = 1 - clamp01((t - 0.45) / 0.3);
      c.drawOval(Skia.XYWHRect(x - 3, y - 2.2, 6, 4.4), paint(C.coffee, a));
    }
  }
  hit(pose.drip - 0.35, 262, 576);
}

// ------------------------------------------------------------------ the cup and its lid

function drawCup(c: SkCanvas, pose: Pose) {
  'worklet';
  const K = SCENE.cup;
  const fr = cupFrame(pose.theta);
  const baseRing = ring(fr, 0, K.rb);
  const mouthRing = ring(fr, K.L, K.rm);
  const mouthC = proj({ x: fr.base.x + fr.axis.x * K.L, y: fr.base.y + fr.axis.y * K.L, z: fr.base.z + fr.axis.z * K.L });
  const baseC = proj(fr.base);
  // its shadow on the floor: the cup's footprint, squashed and blurred
  const fp = Skia.Path.Make();
  const shadowPts = hull(
    baseRing
      .concat(mouthRing)
      .map((p) => ({ x: p.x + 14, y: SCENE.cup.base.y + 6 + (p.y - SCENE.cup.base.y) * 0.08 })),
  );
  for (let i = 0; i < shadowPts.length; i++) {
    if (i === 0) fp.moveTo(shadowPts[i].x, shadowPts[i].y);
    else fp.lineTo(shadowPts[i].x, shadowPts[i].y);
  }
  fp.close();
  c.drawPath(fp, blurred(C.shadow, 0.55, 10));
  // the body: the hull of its two rims, shaded across its axis
  const body = polyPath(hull(baseRing.concat(mouthRing)));
  const ax = { x: mouthC.x - baseC.x, y: mouthC.y - baseC.y };
  const al = Math.max(1, Math.hypot(ax.x, ax.y));
  const nx = -ax.y / al;
  const ny = ax.x / al;
  const mid = { x: (mouthC.x + baseC.x) / 2, y: (mouthC.y + baseC.y) / 2 };
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: mid.x + nx * 70, y: mid.y + ny * 70 }, { x: mid.x - nx * 70, y: mid.y - ny * 70 }, [col(C.cupShade), col(C.cup), col('#FFFFFF'), col(C.cupShade)], [0, 0.4, 0.6, 1], TileMode.Clamp));
  c.drawPath(body, bp);
  // the base, when it turns toward us
  if (dot(fr.axis, VIEW) < 0) c.drawPath(polyPath(baseRing), paint('#E9ECEF'));
  // the sleeve and its bean
  const sl = band(fr, K.sleeve[0] * K.L, K.sleeve[1] * K.L);
  if (sl) {
    const sp = Skia.Path.Make();
    sp.moveTo(sl.front[0].x, sl.front[0].y);
    for (let i = 1; i < sl.front.length; i++) sp.lineTo(sl.front[i].x, sl.front[i].y);
    for (let i = sl.back.length - 1; i >= 0; i--) sp.lineTo(sl.back[i].x, sl.back[i].y);
    sp.close();
    c.save();
    c.clipPath(body, ClipOp.Intersect, true);
    const slp = Skia.Paint();
    slp.setAntiAlias(true);
    slp.setShader(Skia.Shader.MakeLinearGradient({ x: mid.x + nx * 70, y: mid.y + ny * 70 }, { x: mid.x - nx * 70, y: mid.y - ny * 70 }, [col(C.sleeveShade), col(C.sleeve), col('#D6E6C0'), col(C.sleeveShade)], [0, 0.4, 0.6, 1], TileMode.Clamp));
    c.drawPath(sp, slp);
    c.restore();
  }
  // the bean, on the side of the sleeve that faces us most
  let best = 0;
  let bestDot = -2;
  for (let i = 0; i < 24; i++) {
    const ang = (i / 24) * TAU;
    const nrm: V3 = { x: fr.u.x * Math.cos(ang) + fr.v.x * Math.sin(ang), y: fr.u.y * Math.cos(ang) + fr.v.y * Math.sin(ang), z: fr.u.z * Math.cos(ang) + fr.v.z * Math.sin(ang) };
    const dd = dot(nrm, VIEW);
    if (dd > bestDot) {
      bestDot = dd;
      best = ang;
    }
  }
  const hb = (K.sleeve[0] + K.sleeve[1]) * 0.5 * K.L;
  const rb = mix(K.rb, K.rm, hb / K.L);
  const bc = proj(ringPoint(fr, hb, rb, best));
  const along = { x: ax.x / al, y: ax.y / al };
  c.save();
  c.translate(bc.x, bc.y);
  c.rotate((Math.atan2(along.y, along.x) * 180) / Math.PI, 0, 0);
  c.drawOval(Skia.XYWHRect(-14, -10, 28, 20), paint(C.bean, 0.95));
  const crease = Skia.Path.Make();
  crease.moveTo(-10, 2);
  crease.cubicTo(-3, -6, 3, 6, 10, -2);
  c.drawPath(crease, stroke(C.sleeve, 2.2));
  c.restore();
  // the mouth: its rim, and inside it the dark of the cup with coffee in it
  if (dot(fr.axis, VIEW) > 0) {
    const mp = polyPath(mouthRing);
    c.drawPath(mp, paint(C.cupInside));
    // coffee lies in the low side of the mouth while there is any left
    const left = 1 - pose.spill;
    if (left > 0.02 && pose.theta > 30) {
      c.save();
      c.clipPath(mp, ClipOp.Intersect, true);
      const low = mouthRing.reduce((a, p) => (p.y > a.y ? p : a), mouthRing[0]);
      c.drawOval(Skia.XYWHRect(low.x - K.rm * 0.9, low.y - K.rm * 0.5 * left - 10, K.rm * 1.8, K.rm * 1.1 * left + 14), paint(C.coffee));
      c.restore();
    }
    c.drawPath(mp, stroke('#FFFFFF', 4.5));
  }
  return { mouthC, low: mouthRing.reduce((a, p) => (p.y > a.y ? p : a), mouthRing[0]), fr };
}

function drawLid(c: SkCanvas, pose: Pose, cup: ReturnType<typeof drawCup>) {
  'worklet';
  const K = SCENE.cup;
  const L = SCENE.lid;
  const k = pose.lid;
  // on the cup: a disc on the mouth ring; at rest: a disc flat on the floor; in between it flies
  const fr = cup.fr;
  const onC = proj({ x: fr.base.x + fr.axis.x * (K.L + 4), y: fr.base.y + fr.axis.y * (K.L + 4), z: fr.base.z + fr.axis.z * (K.L + 4) });
  const restC = { x: L.x, y: L.y };
  const e = easeOut(k);
  const arc = Math.sin(Math.PI * k) * 110;
  let hop = 0;
  if (pose.lidHop >= 0) hop = 26 * Math.abs(Math.sin(pose.lidHop * 9)) * Math.exp(-pose.lidHop * 5);
  const x = mix(onC.x, restC.x, e);
  const y = mix(onC.y, restC.y, e) - arc - hop;
  // its normal: the cup's axis on the cup, straight up at rest, tumbling in flight
  const tumble = Math.sin(Math.PI * k) * 1.4;
  const nOn = fr.axis;
  const nx = mix(nOn.x, 0, e) + Math.sin(tumble) * 0.4;
  const ny = mix(nOn.y, 1, e);
  const nz = mix(nOn.z, 0, e) + Math.cos(tumble) * 0.1 * Math.sin(Math.PI * k);
  const nl = Math.hypot(nx, ny, nz) || 1;
  const n: V3 = { x: nx / nl, y: ny / nl, z: nz / nl };
  // two directions across the normal
  const ref: V3 = Math.abs(n.y) < 0.9 ? { x: 0, y: 1, z: 0 } : { x: 1, y: 0, z: 0 };
  const u0: V3 = { x: n.y * ref.z - n.z * ref.y, y: n.z * ref.x - n.x * ref.z, z: n.x * ref.y - n.y * ref.x };
  const ul = Math.hypot(u0.x, u0.y, u0.z) || 1;
  const u: V3 = { x: u0.x / ul, y: u0.y / ul, z: u0.z / ul };
  const v: V3 = { x: n.y * u.z - n.z * u.y, y: n.z * u.x - n.x * u.z, z: n.x * u.y - n.y * u.x };
  const r = mix(K.rm + 5, L.r, e);
  const pts: P2[] = [];
  const top: P2[] = [];
  for (let i = 0; i < 40; i++) {
    const a = (i / 40) * TAU;
    const wx = r * (u.x * Math.cos(a) + v.x * Math.sin(a));
    const wy = r * (u.y * Math.cos(a) + v.y * Math.sin(a));
    const wz = r * (u.z * Math.cos(a) + v.z * Math.sin(a));
    pts.push({ x: x + wx, y: y - wy * CE + wz * SE });
    top.push({ x: x + wx * 0.8 - n.x * 6, y: y - (wy * 0.8 + n.y * 6) * CE + (wz * 0.8 + n.z * 6) * SE });
  }
  if (k > 0.98) c.drawOval(Skia.XYWHRect(L.x - L.r + 8, L.y - 6, L.r * 2, 22), blurred(C.shadow, 0.5, 7));
  c.drawPath(polyPath(pts), paint(C.lidShade));
  c.save();
  c.translate(0, -5);
  c.drawPath(polyPath(pts), paint(C.lid));
  c.restore();
  c.drawPath(polyPath(top), stroke('#E6E9F0', 2));
}

function drawStream(c: SkCanvas, pose: Pose, low: P2) {
  'worklet';
  if (pose.pour <= 0.01) return;
  const p = Skia.Path.Make();
  p.moveTo(low.x, low.y - 6);
  p.quadTo(low.x - 30, low.y + 30, 236, 584);
  c.drawPath(p, stroke(C.coffee, 12 * pose.pour, 0.95));
}

function drawSteam(c: SkCanvas, pose: Pose, top: P2) {
  'worklet';
  if (pose.steam <= 0.01) return;
  for (let k = 0; k < 3; k++) {
    const p = Skia.Path.Make();
    const x0 = top.x - 22 + k * 22;
    const ph = pose.amb * 4 + k * 1.7;
    for (let i = 0; i <= 16; i++) {
      const u = i / 16;
      const y = top.y - 16 - u * 90;
      const x = x0 + 8 * Math.sin(ph + u * 6) * u;
      if (i === 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    c.drawPath(p, stroke(C.steam, 4, 0.55 * pose.steam * (0.6 + 0.4 * Math.sin(ph))));
  }
}

function drawText(c: SkCanvas, r: Res, pose: Pose) {
  'worklet';
  const k = pose.text;
  const cx = PIN_W / 2;
  const fade = (a: number, dy: number, s: string, y: number, font: SkFont, color: string) => {
    if (a < 0.01) return;
    text(c, s, cx, y + dy, font, paint(color, a), 0.5);
  };
  fade(1 - k, -10 * k, COPY.title, UI.title, r.f.title, C.title);
  fade(k, 10 * (1 - k), COPY.retrying, UI.title, r.f.title, C.title);
  for (let i = 0; i < 2; i++) {
    fade(1 - k, -10 * k, COPY.body[i], UI.body[i], r.f.body, C.body);
    fade(k, 10 * (1 - k), COPY.retryingBody[i], UI.body[i], r.f.body, C.body);
  }
  const B = UI.button;
  const s = 1 - 0.04 * pose.press;
  c.save();
  c.translate(B.x + B.w / 2, B.y + B.h / 2);
  c.scale(s, s);
  c.drawRRect(rrect(-B.w / 2 + 10, -B.h / 2 + 24, B.w - 20, B.h, B.h / 2), blurred('#B8BEE0', 0.6, 16));
  c.drawRRect(rrect(-B.w / 2, -B.h / 2, B.w, B.h, B.h / 2), paint(C.button));
  if (pose.dots < 0.99) text(c, COPY.button, 0, 0, r.f.button, paint(C.buttonText, 1 - pose.dots), 0.5, 2.5);
  if (pose.dots > 0.01) {
    for (let i = 0; i < 3; i++) {
      const b = Math.max(0, Math.sin(pose.amb * 7 - i * 0.7));
      c.drawCircle((i - 1) * 18, -6 * b, 5.5, paint(C.buttonText, pose.dots * (0.5 + 0.5 * b)));
    }
  }
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, pose: Pose) {
  'worklet';
  c.save();
  c.scale(g.s, g.s);
  c.translate(0, DY);
  c.scale(S, S);
  const topY = -DY / S - 10;
  const bottomY = (g.h / g.s - DY) / S + 10;
  drawRoom(c, pose.amb, topY, bottomY);
  drawPaper(c);
  // the cup first works out where its mouth is, so the puddle can flow from it
  const fr = cupFrame(pose.theta);
  const K = SCENE.cup;
  const mouth = proj({ x: fr.base.x + fr.axis.x * K.L, y: fr.base.y + fr.axis.y * K.L, z: fr.base.z + fr.axis.z * K.L });
  drawPuddle(c, pose, mouth);
  const cup = drawCup(c, pose);
  drawStream(c, pose, cup.low);
  // a drop falls from the mouth now and then
  if (pose.drip >= 0 && pose.drip < 0.35) {
    const q = pose.drip / 0.35;
    c.drawOval(Skia.XYWHRect(cup.low.x - 4, mix(cup.low.y, 576, q * q) - 5, 8, 10), paint(C.coffee));
  }
  drawLid(c, pose, cup);
  drawSteam(c, pose, proj({ x: fr.base.x + fr.axis.x * (K.L + 10), y: fr.base.y + fr.axis.y * (K.L + 10), z: fr.base.z + fr.axis.z * (K.L + 10) }));
  drawText(c, r, pose);
  // rewinding: faint tracking lines run down the screen
  if (pose.rewind > 0.01) {
    for (let i = 0; i < 6; i++) {
      const y = ((pose.amb * 900 + i * 260) % (bottomY - topY)) + topY;
      c.drawRect(Skia.XYWHRect(-10, y, PIN_W + 20, 3 + (i % 2) * 2), paint('#8FA2C8', 0.09 * pose.rewind));
    }
  }
  c.restore();
  const gp = Skia.Paint();
  gp.setShader(r.grain.makeShader([]));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), gp);
}
