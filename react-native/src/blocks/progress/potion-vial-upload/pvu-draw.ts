// Potion Vial Upload — drawing. Two pictures per frame: the art (run through the pixel filter)
// and the type (crisp pixel font on top). Every function here is a worklet, and helpers sit above
// their callers.

import {
  BlurStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  type SkRuntimeEffect,
  Skia,
  StrokeCap,
  StrokeJoin,
} from '@shopify/react-native-skia';

import {
  ANCHOR,
  clamp01,
  type Driver,
  easeIn,
  easeInOut,
  easeOutBack,
  hash,
  LOOP,
  mbText,
  mix,
  MODE,
  MOUTH,
  NEAR_R,
  progressAt,
  seg,
  smooth,
  T,
} from './pvu-motion';
import { C, FILE, LAYOUT, REF_W, V } from './pvu-theme';

export type Res = {
  liquid: SkRuntimeEffect;
  smoke: SkRuntimeEffect;
  outer: SkPath;
  inner: SkPath;
  bold: SkFont; // digits
  boldSmall: SkFont; // "%"
  regular: SkFont; // lines under the counter
  medium: SkFont; // card name, header
  small: SkFont; // card status, ghost label
  title: SkFont;
  file: SkImage; // the smooth document icon (3× pixels: 84 × 102 for 28 × 34 pt)
};

export type Geo = {
  w: number;
  h: number;
  s: number; // reference points → screen points
  ox: number; // screen x of reference x = 0
  cy: number; // screen y of the vial's centre line
  top: number; // safe area top
  bottom: number; // safe area bottom
  cardY: number; // vial-local y of the card's top
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const s = Math.min(w / REF_W, 1.2);
  const ox = (w - REF_W * s) / 2;
  const cardTop = h - bottom - LAYOUT.cardBottom - LAYOUT.cardH * s;
  const headBottom = top + 52 + 76; // header row, then the title and its line
  const cy = headBottom + (cardTop - headBottom) * LAYOUT.vialY;
  return { w, h, s, ox, cy, top, bottom, cardY: (cardTop - cy) / s };
}

/** The vial's outline around its centre line (outer) and the glass's inside (inner). */
export function makeVialPaths() {
  const outline = (inset: number) => {
    const half = V.half - inset;
    const nh = V.neckHalf - inset;
    const x0 = V.x0 + inset;
    const p = Skia.PathBuilder.Make();
    p.moveTo(V.x1, -half);
    p.lineTo(x0 + half, -half);
    p.arcToOval(Skia.XYWHRect(x0, -half, half * 2, half * 2), -90, -180, false);
    p.lineTo(V.x1, half);
    p.cubicTo(V.x1 + 13, half, V.shoulder - 9, nh, V.shoulder, nh);
    p.lineTo(V.neck + (inset > 0 ? 2 : 0), nh);
    p.lineTo(V.neck + (inset > 0 ? 2 : 0), -nh);
    p.lineTo(V.shoulder, -nh);
    p.cubicTo(V.shoulder - 9, -nh, V.x1 + 13, -half, V.x1, -half);
    p.close();
    return p.build();
  };
  return { outer: outline(0), inner: outline(V.wall) };
}

// ------------------------------------------------------------------------------------------
// Frame: everything the two pictures need, derived from the loop time and the driver.

export type Frame = {
  t: number;
  p: number; // upload 0…1
  front: number; // liquid edge (vial-local x)
  bulge: number;
  flow: number;
  glow: number;
  gone: number; // dissolve 0…1
  liquidOn: number;
  mist: number;
  streak: number; // -1 none, else 0…1
  splash: number; // -1 none, else seconds since the splash
  burst: number; // -1 none, else seconds since 100 %
  // the file
  ghostX: number; // vial-local
  ghostY: number;
  ghostScale: number;
  ghostAlpha: number;
  ghostRot: number;
  slotEmpty: number; // 0 the card holds the file … 1 it is lifted out
  hot: number; // 0…1 the file is over the mouth
  // the gem (vial-local)
  gemX: number;
  gemY: number;
  gemRot: number;
  gemVis: number; // the cube's drawn roll (corked: corner to the right; free: tilting with the chain)
  // spider (vial-local)
  spX: number;
  spY: number;
  spHead: number;
  spWalk: number;
  spPhase: number;
  spIdle: number;
  spHop: number;
  // type
  restA: number;
  readyA: number;
  counterA: number;
  doneA: number;
  cardState: number; // 0 ready, 1 brewing, 2 added
  cardDoneA: number;
  shake: number; // the vial's jolt when the emerald clicks home
  pixOut: number; // -1 none, else 0…1 the file breaking into pixels sucked into the mouth
  pixX: number; // where it broke
  pixY: number;
  pixIn: number; // -1 none, else 0…1 the file re-forming from pixels on its card
  dp: number; // upload speed (fraction per second)
  silkY: number; // the spider's thread: from y = -V.neckHalf - 7 down to here (NaN: none)
  shine: number; // -1 none, else 0…1 a shine sweeping the vial at 100 %
  trail: number; // 0…1 strength of the sparkle trail behind the flying file
  cloud: number; // -1 none, else 0…1 the green cloud streaming down the tube
  cloudA: number; // its strength (it melts into the potion)
  fade: number; // 0…1 the screen fading to black (end of the loop) or from it (start)
};

/** Sample a sagging chain from a to b with `slack` extra length; returns link centres. */
const chainLinks = (ax: number, ay: number, bx: number, by: number, length: number) => {
  'worklet';
  const d = Math.max(0.01, Math.hypot(bx - ax, by - ay));
  const slack = Math.max(0, length - d);
  const depth = Math.sqrt((3 * d * slack) / 8);
  const pts: { x: number; y: number }[] = [];
  const N = 28;
  for (let i = 0; i <= N; i++) {
    const s = i / N;
    pts.push({ x: mix(ax, bx, s), y: mix(ay, by, s) + depth * 4 * s * (1 - s) });
  }
  // walk the polyline, dropping a link every `step` points
  const step = 4.4;
  const links: { x: number; y: number; a: number }[] = [];
  let acc = 0;
  let next = step * 0.5;
  for (let i = 1; i < pts.length; i++) {
    const sx = pts[i].x - pts[i - 1].x;
    const sy = pts[i].y - pts[i - 1].y;
    const l = Math.hypot(sx, sy);
    while (acc + l >= next) {
      const k = (next - acc) / l;
      links.push({ x: pts[i - 1].x + sx * k, y: pts[i - 1].y + sy * k, a: Math.atan2(sy, sx) });
      next += step;
    }
    acc += l;
  }
  return links;
};

const SPIDER_REST = { x: V.x0 + 17, y: -V.half + 5, head: 1.75 };
const NECK_TOP = { x: V.shoulder - 6, y: -V.neckHalf - 7 };

/** The spider's walk (keyframed in loop time). The climb down the chain is placed by frameAt. */
const spiderPath = (t: number) => {
  'worklet';
  const frontX = (tt: number) => {
    'worklet';
    return mix(V.x0 + V.wall - 6, V.neck + 2, progressAt(tt));
  };
  if (t < T.splash + 0.12) return { x: SPIDER_REST.x, y: SPIDER_REST.y };
  // it drops over the far end onto the potion's edge and rides it, wandering over the tube
  const k = smooth(seg(t, T.splash + 0.12, T.splash + 0.6));
  const wander = Math.sin((2 * Math.PI * (t - T.splash)) / 2.4) * 11;
  const fx = Math.min(frontX(t) - 3, V.x1 + 4);
  const tx = mix(SPIDER_REST.x, Math.max(fx, SPIDER_REST.x), k);
  const ty = mix(SPIDER_REST.y, wander, k);
  // near the end it walks up onto the neck and waits there
  const c = easeInOut(seg(t, 7.6, 8.15));
  return { x: mix(tx, NECK_TOP.x, c), y: mix(ty, NECK_TOP.y, c) };
};

/** A point on the file's flight from its card to the mouth (auto ghost), vial-local. */
const ghostPath = (k: number, cardX: number, cardY: number) => {
  'worklet';
  const tx = MOUTH.x + 18;
  const ty = -50;
  const cx = 372;
  const cyy = cardY - 110;
  const u = 1 - k;
  return {
    x: u * u * cardX + 2 * u * k * cx + k * k * tx,
    y: u * u * cardY + 2 * u * k * cyy + k * k * ty,
  };
};

/** The card's icon, vial-local (where the file rests). */
export const cardIcon = (g: Geo) => {
  'worklet';
  return { x: REF_W / 2 - LAYOUT.cardW / 2 + 30, y: g.cardY + LAYOUT.cardH / 2 };
};

export const frameAt = (t: number, d: Driver, now: number, g: Geo, still: boolean): Frame => {
  'worklet';
  const p = progressAt(t);
  const filling = t >= T.splash;
  const front = mix(V.x0 + V.wall - 6, V.neck + 2, p);
  const dp = (progressAt(t + 0.05) - progressAt(t - 0.05)) * 10;
  const bulge = 5 + Math.min(dp * 9, 4);
  // the glow builds with the potion and is full at 100 %
  const glow = filling ? 0.12 + 0.88 * smooth(seg(p, 0.35, 1)) : 0;

  const icon = cardIcon(g);
  const fingerFile = d.mode === MODE.file && d.fileX === d.fileX;
  let ghostX = icon.x;
  let ghostY = icon.y;
  let ghostScale = 1;
  let ghostAlpha = 0;
  let ghostRot = 0;
  let slotEmpty = 0;
  let hot = 0;
  let mist = 0;
  if (fingerFile) {
    ghostX = d.fileX;
    ghostY = d.fileY;
    ghostAlpha = 1;
    slotEmpty = 1;
    const dist = Math.hypot(ghostX - MOUTH.x, ghostY - MOUTH.y);
    hot = 1 - smooth((dist - NEAR_R * 0.55) / (NEAR_R * 0.6));
    mist = d.gem.corked === 0 ? 0.35 + 0.65 * hot : 0;
    ghostRot = -0.12;
  } else if (d.fileBack < 1) {
    const k = easeOutBack(d.fileBack);
    ghostX = mix(d.fileFromX, icon.x, k);
    ghostY = mix(d.fileFromY, icon.y, k);
    ghostAlpha = 1;
    slotEmpty = 1 - smooth(seg(d.fileBack, 0.75, 1));
  } else if (t >= T.lift && t < T.drop + 0.14) {
    // the recording's drag: the file glides up to the mouth and hovers, tilted a little
    const fly = easeInOut(seg(t, T.lift, T.near + 0.15));
    const pos = ghostPath(fly, icon.x, icon.y);
    const hover = seg(t, T.near + 0.15, T.drop);
    ghostX = pos.x + Math.sin(hover * Math.PI) * 6;
    ghostY = pos.y - Math.sin(hover * Math.PI * 2) * 3;
    ghostAlpha = 1;
    ghostRot = -0.14 * smooth(seg(t, T.lift, T.lift + 0.3));
    slotEmpty = smooth(seg(t, T.lift, T.lift + 0.15));
    hot = smooth(seg(t, T.near - 0.2, T.near));
    mist = smooth(seg(t, T.near, T.near + 0.2));
    if (t >= T.drop) {
      // let go: it slips into the mouth, shrinking (1.75–1.88 s)
      const k = easeIn(seg(t, T.drop, T.drop + 0.13));
      const sx = d.dropX === d.dropX ? d.dropX : ghostX;
      const sy = d.dropY === d.dropY ? d.dropY : ghostY;
      ghostX = mix(sx, MOUTH.x + 8, k);
      ghostY = mix(sy, MOUTH.y - 4, k);
      ghostScale = mix(1, 0.45, k);
      ghostAlpha = 1 - smooth(seg(t, T.drop + 0.07, T.drop + 0.13));
    }
  } else if (t >= T.drop + 0.14 && d.dropX === d.dropX && t < T.drop + 0.3) {
    ghostAlpha = 0;
  }
  // a finger-dropped file starts from where it was let go
  if (!fingerFile && d.dropX === d.dropX && t >= T.drop && t < T.drop + 0.14) {
    const k = easeIn(seg(t, T.drop, T.drop + 0.13));
    ghostX = mix(d.dropX, MOUTH.x + 8, k);
    ghostY = mix(d.dropY, MOUTH.y - 4, k);
    ghostScale = mix(1, 0.45, k);
    ghostAlpha = 1 - smooth(seg(t, T.drop + 0.07, T.drop + 0.13));
    ghostRot = -0.12;
    slotEmpty = 1;
  }
  // the mist dies away as the file goes in (1.88–1.97 s); the completion's sparks bring a breath back
  if (!fingerFile) mist = mist * (1 - smooth(seg(t, T.drop + 0.13, T.drop + 0.22)));
  if (t >= T.done && t < T.done + 1.2) mist = Math.max(mist, 0.4 * (1 - smooth(seg(t, T.done + 0.1, T.done + 1.1))));
  if (t >= T.drop + 0.1 && !fingerFile && d.fileBack >= 1) slotEmpty = t < T.fill ? 1 : 1 - smooth(seg(t, T.fill, T.fill + 0.3));

  // the gem (physics) and the spider
  const gm = d.gem;
  const gemX = ANCHOR.x + gm.x;
  const gemY = ANCHOR.y + gm.y;
  let sp = spiderPath(t);
  let sp2 = spiderPath(Math.max(0, t - 0.06));
  let head = SPIDER_REST.head;
  let walk = 0;
  if (t >= T.climb[0] && gm.corked === 0) {
    // down the chain, link by link, then riding the gem
    const bx = gemX + Math.sin(gm.rot) * 16;
    const by = gemY - Math.cos(gm.rot) * 16;
    const links = chainLinks(ANCHOR.x, ANCHOR.y, bx, by, V.chain - 16);
    const k = easeInOut(seg(t, T.climb[0], T.climb[1]));
    const n = links.length;
    if (t < T.climb[1] && n > 1) {
      const fi = k * (n - 1);
      const a = links[Math.floor(fi)];
      const b = links[Math.min(n - 1, Math.floor(fi) + 1)];
      const e = fi - Math.floor(fi);
      const k0 = smooth(seg(t, T.climb[0], T.climb[0] + 0.12));
      sp = { x: mix(NECK_TOP.x, mix(a.x, b.x, e), k0), y: mix(NECK_TOP.y, mix(a.y, b.y, e), k0) };
      head = Math.atan2(b.y - a.y, b.x - a.x);
      walk = 1;
    } else {
      sp = { x: bx - Math.sin(gm.rot) * 4, y: by + Math.cos(gm.rot) * 4 };
      head = Math.PI / 2 + gm.rot;
    }
    sp2 = sp;
  } else {
    const vx = (sp.x - sp2.x) / 0.06;
    const vy = (sp.y - sp2.y) / 0.06;
    const speed = Math.hypot(vx, vy);
    walk = smooth((speed - 4) / 22);
    if (t >= T.splash && t < 7.6) head = 0.05;
    else if (t >= 7.6) head = mix(0.05, Math.PI * 0.75, smooth(seg(t, 8.0, 8.3)));
    const velHead = Math.atan2(vy, vx);
    let dh = velHead - head;
    dh -= Math.round(dh / (Math.PI * 2)) * Math.PI * 2;
    head += dh * walk;
  }
  // a poke makes it scurry a step along the tube and come back
  const ka = now - d.kickAt;
  const kick = ka >= 0 && ka < 1.2 ? Math.sin(Math.min(ka / 0.35, 1) * Math.PI * 0.5) * Math.exp(-Math.max(0, ka - 0.35) * 4) : 0;
  const hopK = ka >= 0 && ka < 0.4 ? Math.sin((ka / 0.4) * Math.PI) : 0;

  // the words under the vial, as the recording swaps them
  const restA = still ? 0 : 1 - smooth(seg(t, T.near - 0.1, T.near));
  let readyA = still ? 0 : smooth(seg(t, T.near - 0.1, T.near)) * (1 - smooth(seg(t, T.near + 0.6, T.near + 0.7)));
  const dropA = still ? 0 : smooth(seg(t, T.near + 0.6, T.near + 0.7)) * (1 - smooth(seg(t, 2.07, 2.2)));
  if (fingerFile) readyA = smooth((hot - 0.4) / 0.4);
  const counterA = still ? 1 : smooth(seg(t, 2.07, 2.2));
  const doneA = still ? 1 : smooth(seg(t, T.fill, T.fill + 0.1));
  const cardState = t >= T.fill ? 2 : t >= T.drop ? 1 : 0;
  const cardDoneA = cardState === 2 ? smooth(seg(t, T.fill, T.fill + 0.3)) : 0;

  // the emerald clicking home jolts the vial (a short decaying shake)
  const sinceHome = now - d.gem.lastHome - d.gem.homeDur;
  const shake = sinceHome >= 0 && sinceHome < 0.4 ? Math.sin(sinceHome * 60) * Math.exp(-sinceHome * 11) * 1.2 : 0;
  const fade = still
    ? 0
    : t < T.fadeIn[1]
      ? 1 - smooth(seg(t, T.fadeIn[0], T.fadeIn[1]))
      : smooth(seg(t, T.fade[0], T.fade[1]));

  return {
    t,
    p,
    front,
    bulge,
    flow: p * 120,
    glow,
    gone: 0,
    liquidOn: filling ? 1 : 0,
    mist: (restA + readyA + dropA >= 0 ? 1 : 0) * mist,
    streak: -1,
    splash: -1,
    burst: t >= T.fill && t < T.fill + 1.4 ? t - T.fill : -1,
    ghostX,
    ghostY,
    ghostScale,
    ghostAlpha,
    ghostRot,
    slotEmpty,
    hot,
    gemX,
    gemY,
    gemRot: gm.rot,
    gemVis: mix(-Math.atan2(gm.x, gm.y) * 0.9, -0.35, gm.corked === 1 ? (gm.homeAt < 0 ? 1 : clamp01((now - gm.homeAt) / gm.homeDur)) : 0),
    spX: sp.x + kick * 22 * d.kickDir,
    spY: sp.y,
    spHead: head,
    spWalk: Math.max(walk, kick > 0.02 && ka < 0.5 ? 1 : 0),
    spPhase: (sp.x + sp.y * 0.6) / 9 + kick * 4.5,
    spIdle: t,
    spHop: hopK,
    restA: Math.max(restA, dropA),
    readyA,
    counterA,
    doneA,
    cardState,
    cardDoneA,
    shake,
    trail: 0,
    pixOut: -1,
    pixX: 0,
    pixY: 0,
    pixIn: -1,
    dp: Math.max(0, dp),
    silkY: NaN,
    shine: -1,
    cloud: t >= T.streak[0] && t < T.streak[1] + 0.4 ? seg(t, T.streak[0], T.streak[1]) : -1,
    cloudA: 1 - smooth(seg(t, T.streak[1] - 0.05, T.streak[1] + 0.4)),
    fade,
  };
};

// ------------------------------------------------------------------------------------------
// Art pieces (vial-local coordinates unless noted).

const paint = (color: string, alpha = 1) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(false);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
};

const stroke = (color: string, width: number, alpha = 1) => {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(1);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
};

const vgrad = (y0: number, y1: number, cols: string[], pos: number[]) => {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    { x: 0, y: y0 },
    { x: 0, y: y1 },
    cols.map((c) => Skia.Color(c)),
    pos,
    0,
  );
};

const drawStars = (c: SkCanvas, g: Geo, f: Frame) => {
  'worklet';
  // the dust of the page: still dim dots, and "+" twinkles that wake up when the potion glows
  const dot = paint(C.star, 0.22);
  for (let i = 0; i < 46; i++) {
    const x = hash(i * 3.1) * g.w;
    const y = g.top + hash(i * 7.7 + 1) * (g.h - g.top - g.bottom);
    const r = hash(i * 1.3) > 0.8 ? 1.1 : 0.7;
    c.drawRect(Skia.XYWHRect(x, y, r, r), dot);
  }
  for (let i = 0; i < 14; i++) {
    const a = hash(i * 5.3 + 2) * Math.PI * 2;
    const rr = 120 + hash(i * 2.9) * 130;
    const x = g.ox + (190 + Math.cos(a) * rr * 1.15) * g.s;
    const y = g.cy + Math.sin(a) * rr * 0.62 * g.s;
    const tw = Math.pow(Math.max(0, Math.sin((2 * Math.PI * (3 * f.t)) / LOOP + hash(i) * 6.28)), 6);
    const level = 0.25 + 0.75 * f.glow;
    const al = tw * level;
    if (al < 0.03) continue;
    const big = hash(i * 9.1) > 0.55;
    const col = hash(i * 4.4) > 0.45 ? C.star : C.starGreen;
    const pp = paint(col, al);
    const arm = big ? 4.5 : 2.6;
    c.drawRect(Skia.XYWHRect(x - arm, y - 0.7, arm * 2, 1.4), pp);
    c.drawRect(Skia.XYWHRect(x - 0.7, y - arm, 1.4, arm * 2), pp);
  }
};

const drawGlow = (c: SkCanvas, r: Res, f: Frame) => {
  'worklet';
  if (f.glow < 0.01) return;
  const p = paint(C.glow, 0.95 * f.glow);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.drawPath(r.outer, p);
  const q = paint(C.glow, 0.32 * f.glow);
  q.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 24, true));
  c.drawPath(r.outer, q);
};

const drawLiquid = (c: SkCanvas, r: Res, f: Frame) => {
  'worklet';
  const x0 = V.x0 + V.wall;
  const y0 = -V.half + V.wall;
  const x1 = V.neck + 2;
  const y1 = V.half - V.wall;
  c.save();
  c.clipPath(r.inner, 1, true);
  if (f.liquidOn && f.front > x0 - 6) {
    const p = Skia.Paint();
    p.setShader(r.liquid.makeShader([x0, y0, x1, y1, f.front, f.bulge, f.t, LOOP, f.flow, f.glow, f.gone, f.p]));
    c.drawRect(Skia.XYWHRect(x0 - 2, y0, x1 - x0 + 4, y1 - y0), p);
  }
  // the drop streaming down the tube: a green cloud, brightest at its head (it melts into the potion)
  if (f.cloud >= 0 && f.cloudA > 0.01) {
    const k = 1 - Math.pow(1 - f.cloud, 2);
    const hx = mix(V.neck - 6, x0 + 6, k);
    const len = mix(24, 230, Math.min(1, f.cloud * 1.4));
    for (let i = 0; i < 40; i++) {
      const u = i / 39;
      const x = hx + u * len;
      if (x > V.neck + 2) continue;
      const wob = Math.sin(u * 9 + f.t * 14 + i) * 3.5 * u;
      const r = mix(8, 5, u) + Math.sin(i * 1.7) * 1.4;
      const al = (u < 0.15 ? 0.95 : 0.75 * (1 - u * 0.75)) * f.cloudA;
      const col = i < 2 ? '#f0ffe0' : i < 7 ? '#a8f870' : u < 0.5 ? C.liquidDeep : '#3f8f2c';
      c.drawCircle(x, wob + (hash(i) - 0.5) * 5, r, paint(col, al));
    }
  }
  // splash droplets bouncing inside the far end
  if (f.splash >= 0) {
    const s = f.splash;
    for (let i = 0; i < 12; i++) {
      const a = -Math.PI / 2 + (hash(i * 3.3) - 0.5) * 2.4;
      const sp = 70 + hash(i * 6.1) * 90;
      const x = x0 + 6 + Math.cos(a) * sp * s + Math.abs(Math.cos(a)) * 20 * s;
      const y = 8 + Math.sin(a) * sp * s + 260 * s * s;
      const al = 1 - smooth(seg(s, 0.35, 0.8));
      if (al <= 0) continue;
      const sz = 2 + hash(i) * 1.6;
      c.drawRect(Skia.XYWHRect(x, Math.min(y, y1 - 2), sz, sz), paint(i % 3 === 0 ? '#eaffd0' : C.liquid, al));
    }
  }
  // bubbles drifting with the flow
  if (f.liquidOn && f.gone < 1) {
    const bp = stroke('#eaffd0', 1.1, 0.6 * (1 - f.gone));
    for (let i = 0; i < 9; i++) {
      const life = ((f.t * (0.35 + hash(i) * 0.3) + hash(i * 2.2)) % 1 + 1) % 1;
      const x = x0 + 10 + hash(i * 4.7) * (f.front - x0 - 18) + life * 10;
      const y = y1 - 5 - life * (y1 - y0 - 10) * 0.85;
      if (x > f.front - 6) continue;
      c.drawCircle(x, y, 1.3 + hash(i * 8.8) * 1.2, bp);
    }
  }
  c.restore();
};

const drawGlass = (c: SkCanvas, r: Res, f: Frame) => {
  'worklet';
  c.drawPath(r.outer, stroke(C.glass, 2.2));
  c.drawPath(r.inner, stroke(C.glassDark, 1.3, 0.9));
  // the glass's top glare: dashes along the empty tube, hidden where the potion is
  const gp = stroke(C.glare, 1.4, 0.5);
  const y = -V.half + V.wall + 3;
  const dashes = [
    [48, 120],
    [134, 196],
    [210, 228],
    [242, 252],
    [264, 270],
  ];
  for (let i = 0; i < dashes.length; i++) {
    const a = Math.max(dashes[i][0], f.liquidOn ? f.front + 8 : 0);
    const b = dashes[i][1];
    if (b > a) c.drawLine(a, y, b, y, gp);
  }
  c.drawLine(V.x1 + 2, -V.half + 6.5, V.x1 + 10, -V.half + 9, stroke(C.glare, 1.3, 0.4));
};

const band = (c: SkCanvas, x0: number, x1: number, half: number, hi: string, mid: string, lo: string, round: number) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(vgrad(-half, half, [hi, mid, mid, lo], [0, 0.28, 0.62, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, -half, x1 - x0, half * 2), round, round), p);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, -half, x1 - x0, half * 2), round, round), stroke('#1a1014', 1.1, 0.55));
};

const drawStopper = (c: SkCanvas) => {
  'worklet';
  band(c, V.band1[0], V.band1[1], V.band1[2], C.brassHi, C.brass, C.brassLo, 2.2);
  band(c, V.disc[0], V.disc[1], V.disc[2], C.steelHi, C.steel, C.steelLo, 3.4);
  band(c, V.band2[0], V.band2[1], V.band2[2], C.brassHi, C.brass, C.brassLo, 2.2);
};

const drawChain = (c: SkCanvas, f: Frame) => {
  'worklet';
  // the chain is tied to the gem's "top" corner, which turns with the gem
  const ax = ANCHOR.x;
  const ay = ANCHOR.y;
  const bx = f.gemX + Math.sin(f.gemRot) * 16;
  const by = f.gemY - Math.cos(f.gemRot) * 16;
  const links = chainLinks(ax, ay, bx, by, V.chain - 16);
  const dark = stroke('#5a2c0e', 2.6);
  const gold = stroke(C.brass, 1.5);
  const hi = paint(C.brassHi, 0.9);
  for (let i = 0; i < links.length; i++) {
    const l = links[i];
    c.save();
    c.translate(l.x, l.y);
    c.rotate((l.a * 180) / Math.PI, 0, 0);
    if (i % 2 === 0) {
      // a link seen face-on: a ring
      const o = Skia.XYWHRect(-3, -2.1, 6, 4.2);
      c.drawOval(o, dark);
      c.drawOval(o, gold);
      c.drawRect(Skia.XYWHRect(-1.2, -2.6, 1.6, 1), hi);
    } else {
      // seen edge-on: a bar
      c.drawLine(-2.6, 0, 2.6, 0, stroke('#5a2c0e', 2.8));
      c.drawLine(-2.4, 0, 2.4, 0, stroke(C.brass, 1.5));
    }
    c.restore();
  }
};

/** The emerald: a cube seen from above, rolled by `rot`, lit from the top left. */
const drawGem = (c: SkCanvas, x: number, y: number, rot: number, t: number) => {
  'worklet';
  const e = V.gemEdge / 2;
  const yaw = 0.68;
  const pitch = -0.52;
  const cyw = Math.cos(yaw);
  const syw = Math.sin(yaw);
  const cp = Math.cos(pitch);
  const spt = Math.sin(pitch);
  const cr = Math.cos(rot);
  const sr = Math.sin(rot);
  const proj = (px: number, py: number, pz: number) => {
    'worklet';
    // yaw about y, pitch about x, then roll in the screen plane
    const x1 = px * cyw + pz * syw;
    const z1 = -px * syw + pz * cyw;
    const y2 = py * cp - z1 * spt;
    const z2 = py * spt + z1 * cp;
    return { x: x1 * cr - y2 * sr, y: x1 * sr + y2 * cr, z: z2 };
  };
  // faces: [normal, 4 corners]
  const F = [
    [0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1],
    [0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1],
    [1, 0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1],
    [-1, 0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1],
    [0, -1, 0, -1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1],
    [0, 1, 0, -1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1],
  ];
  const lx = -0.45;
  const ly = -0.75;
  const lz = 0.48;
  c.save();
  c.translate(x, y);
  let rightmost = { x: -999, y: 0 };
  for (let i = 0; i < 6; i++) {
    const f = F[i];
    const n = proj(f[0], f[1], f[2]);
    if (n.z <= 0.02) continue;
    const pts = [];
    for (let k = 0; k < 4; k++) {
      const q = proj(f[3 + k * 3] * e, f[4 + k * 3] * e, f[5 + k * 3] * e);
      pts.push(q);
      if (q.x > rightmost.x) rightmost = q;
    }
    const lit = clamp01(0.35 + 0.75 * (n.x * lx + n.y * ly + n.z * lz));
    const face = Skia.PathBuilder.Make();
    face.moveTo(pts[0].x, pts[0].y);
    for (let k = 1; k < 4; k++) face.lineTo(pts[k].x, pts[k].y);
    face.close();
    const base = lit > 0.78 ? C.gemLight : lit > 0.5 ? C.gem : C.gemDark;
    c.drawPath(face.build(), paint(base));
    // the inner facet: a smaller, darker square, offset to read as depth
    const mx = (pts[0].x + pts[1].x + pts[2].x + pts[3].x) / 4;
    const my = (pts[0].y + pts[1].y + pts[2].y + pts[3].y) / 4;
    const inner = Skia.PathBuilder.Make();
    for (let k = 0; k < 4; k++) {
      const ix = mix(mx, pts[k].x, 0.52) + 1.2;
      const iy = mix(my, pts[k].y, 0.52) + 1.4;
      if (k === 0) inner.moveTo(ix, iy);
      else inner.lineTo(ix, iy);
    }
    inner.close();
    c.drawPath(inner.build(), paint(lit > 0.78 ? C.gem : C.gemDeep, 0.75));
    // a bright top edge on the lit face
    if (lit > 0.7) c.drawLine(pts[3].x, pts[3].y, pts[2].x, pts[2].y, stroke(C.gemHi, 1.4, 0.9));
    c.drawPath(face.build(), stroke('#0b3a1a', 1, 0.55));
  }
  // the glint: a white cross on the rightmost corner, twinkling
  const tw = 0.55 + 0.45 * Math.sin((2 * Math.PI * 4 * t) / LOOP);
  const arm = 3 + 2.5 * tw;
  const gp = paint('#ffffff', 0.95);
  c.drawRect(Skia.XYWHRect(rightmost.x - arm, rightmost.y - 0.7, arm * 2, 1.4), gp);
  c.drawRect(Skia.XYWHRect(rightmost.x - 0.7, rightmost.y - arm, 1.4, arm * 2), gp);
  c.restore();
};

/** A black widow seen from above, heading `head` (radians, 0 = +x). */
const drawSpider = (c: SkCanvas, f: Frame) => {
  'worklet';
  c.save();
  c.translate(f.spX, f.spY - f.spHop * 5);
  const sc = 1.42 + f.spHop * 0.12;
  c.scale(sc, sc);
  c.rotate((f.spHead * 180) / Math.PI, 0, 0);
  // legs: four a side, hips round the head, knees bent outwards
  const leg = stroke(C.spiderLeg, 1.8);
  const legLo = stroke('#2a2832', 2.6);
  const REST = [
    [13, 15],
    [5, 19],
    [-5, 19],
    [-14, 15],
  ];
  for (let side = -1; side <= 1; side += 2) {
    for (let j = 0; j < 4; j++) {
      const grp = (j + (side > 0 ? 1 : 0)) % 2;
      const ph = f.spPhase * Math.PI * 2 + grp * Math.PI;
      const sw = Math.sin(ph) * 5.5 * f.spWalk;
      const idle = Math.sin((2 * Math.PI * 4 * f.spIdle) / LOOP + j * 1.7 + side) * 0.9 * (1 - f.spWalk);
      const hipX = 3 - j * 2.2;
      const hipY = side * 3.5;
      const fx = REST[j][0] + sw + idle;
      const fy = side * (REST[j][1] + Math.cos(ph) * 1.2 * f.spWalk);
      const kx = (hipX + fx) / 2 + (j < 2 ? 3 : -3);
      const ky = (hipY + fy) / 2 + side * 6.5;
      const path = Skia.PathBuilder.Make();
      path.moveTo(hipX, hipY);
      path.lineTo(kx, ky);
      path.lineTo(fx, fy);
      c.drawPath(path.build(), legLo);
      c.drawPath(path.build(), leg);
    }
  }
  // abdomen with the red hourglass, then the head with green eyes
  c.drawOval(Skia.XYWHRect(-19, -8.5, 17, 17), paint(C.spider));
  c.drawOval(Skia.XYWHRect(-16, -6.5, 8, 5), paint(C.spiderHi, 0.7));
  const hg = Skia.PathBuilder.Make();
  hg.moveTo(-14.5, -3);
  hg.lineTo(-6.5, -3);
  hg.lineTo(-10.5, 0);
  hg.lineTo(-6.5, 3);
  hg.lineTo(-14.5, 3);
  hg.lineTo(-10.5, 0);
  hg.close();
  c.drawPath(hg.build(), paint(C.hourglass));
  c.drawCircle(1.5, 0, 6.2, paint(C.spider));
  c.drawCircle(0.5, -2, 2, paint(C.spiderHi, 0.6));
  c.drawRect(Skia.XYWHRect(5, -3, 2, 2), paint(C.spiderEye));
  c.drawRect(Skia.XYWHRect(5, 1, 2, 2), paint(C.spiderEye));
  c.restore();
};





const drawCard = (c: SkCanvas, g: Geo, f: Frame) => {
  'worklet';
  const x = REF_W / 2 - LAYOUT.cardW / 2;
  const y = g.cardY;
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, LAYOUT.cardW, LAYOUT.cardH), 14, 14);
  c.drawRRect(rr, paint(C.card));
  const line = f.cardDoneA > 0 ? C.cardLineHot : C.cardLine;
  c.drawRRect(rr, stroke(line, 1.3, 1));
  const icon = cardIcon(g);
  // the slot the file left: a dashed outline
  if (f.slotEmpty > 0.01) {
    const dash = stroke(C.faint, 1.5, f.slotEmpty);
    const sx = icon.x - 14;
    const sy = icon.y - 17;
    for (let i = 0; i < 4; i++) {
      c.drawLine(sx + i * 8, sy, sx + i * 8 + 4, sy, dash);
      c.drawLine(sx + i * 8, sy + 34, sx + i * 8 + 4, sy + 34, dash);
    }
    for (let i = 0; i < 5; i++) {
      c.drawLine(sx, sy + i * 8, sx, sy + i * 8 + 4, dash);
      c.drawLine(sx + 28, sy + i * 8, sx + 28, sy + i * 8 + 4, dash);
    }
  }
  // a done badge on the card
  if (f.cardDoneA > 0.01) {
    const bx = icon.x + 11;
    const by = icon.y + 12;
    c.drawCircle(bx, by, 6.5 * easeOutBack(f.cardDoneA), paint(C.zip, f.cardDoneA));
    const ck = Skia.PathBuilder.Make();
    ck.moveTo(bx - 3, by);
    ck.lineTo(bx - 0.8, by + 2.3);
    ck.lineTo(bx + 3.2, by - 2.2);
    c.drawPath(ck.build(), stroke('#0d2a10', 1.6, f.cardDoneA));
  }
};

const drawBurst = (c: SkCanvas, f: Frame) => {
  'worklet';
  if (f.burst < 0) return;
  const s = f.burst;
  // the recording: a fountain of green pixel sparks out of the mouth, up and to the right, drifting
  for (let i = 0; i < 90; i++) {
    const st = hash(i * 2.9) * 0.45;
    const a = s - st;
    if (a <= 0) continue;
    const life = 0.6 + hash(i * 5.5) * 0.7;
    if (a > life) continue;
    const ang = -Math.PI * 0.36 + (hash(i * 1.7) - 0.5) * 1.5;
    const v = 90 + hash(i * 3.9) * 170;
    const drag = (1 - Math.exp(-a * 3.2)) / 3.2;
    const x = MOUTH.x + 4 + Math.cos(ang) * v * drag + Math.sin(a * 6 + i) * 3;
    const y = -4 + Math.sin(ang) * v * drag - 14 * a;
    const al = 1 - smooth(seg(a, life * 0.55, life));
    const col = hash(i * 2.2) > 0.82 ? '#ffffff' : hash(i) > 0.45 ? C.mist : C.starGreen;
    const sz = hash(i * 7.1) > 0.7 ? 2.6 : 1.5;
    c.drawRect(Skia.XYWHRect(x, y, sz, sz), paint(col, al));
  }
};



/** The soft layer: page, smoke, mist, glow and twinkles (dithered by the post shader). */
export const drawSoft = (c: SkCanvas, r: Res, g: Geo, f: Frame) => {
  'worklet';
  c.drawColor(Skia.Color(C.page));
  drawStars(c, g, f);
  c.save();
  c.translate(g.ox, g.cy);
  c.scale(g.s, g.s);
  const sm = Skia.Paint();
  sm.setShader(r.smoke.makeShader([0, MOUTH.x, MOUTH.y, f.t, LOOP, 1, f.mist, f.glow, f.pixOut >= 0 ? -1 : 1]));
  c.drawRect(Skia.XYWHRect(-20, -150, REF_W + 40, 300), sm);
  drawGlow(c, r, f);
  c.drawPath(r.inner, paint('#c4bed2', 0.05));
  const halo = paint(C.glow, 0.3 + 0.25 * f.glow);
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 11, true));
  c.drawCircle(f.gemX, f.gemY, V.gemEdge * 0.8, halo);
  c.restore();
};

/** The solid layer: hard-edged pixel art (no anti-aliasing), drawn over the soft one. */
export const drawSolid = (c: SkCanvas, r: Res, g: Geo, f: Frame) => {
  'worklet';
  c.save();
  c.translate(g.ox, g.cy);
  c.scale(g.s, g.s);
  c.save();
  c.translate(f.shake, -Math.abs(f.shake) * 0.4);
  drawLiquid(c, r, f);
  drawGlass(c, r, f);
  drawChain(c, f);
  drawStopper(c);
  c.restore();
  drawGem(c, f.gemX, f.gemY, f.gemVis, f.t);
  drawSpider(c, f);
  drawBurst(c, f);
  drawCard(c, g, f);
  c.restore();
};

// ------------------------------------------------------------------------------------------
// Type (crisp, on top of the pixel layer).

const textWidth = (font: SkFont, s: string) => {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const ws = font.getGlyphWidths(ids);
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
};

const centered = (c: SkCanvas, font: SkFont, s: string, x: number, y: number, color: string, alpha: number) => {
  'worklet';
  if (alpha <= 0.01) return;
  c.drawText(s, x - textWidth(font, s) / 2, y, paint(color, alpha), font);
};

export const drawType = (c: SkCanvas, r: Res, g: Geo, f: Frame) => {
  'worklet';
  c.save();
  c.translate(g.ox, 0);
  c.scale(g.s, g.s);
  const top = g.top / g.s;
  // header
  const back = Skia.PathBuilder.Make();
  back.moveTo(26, top + 17);
  back.lineTo(20, top + 23);
  back.lineTo(26, top + 29);
  c.drawPath(back.build(), stroke(C.mute, 2));
  c.drawText('Grimoire', 34, top + 28, paint(C.ink, 0.92), r.medium);
  const slot = 'Slot 3 of 5';
  c.drawText(slot, REF_W - 20 - textWidth(r.small, slot), top + 27, paint(C.mute), r.small);
  // the screen's title and its line
  c.drawText('Add a spell pack', 20, top + 82, paint(C.ink), r.title);
  c.drawText('Zip files up to 200 MB. Every pack is checked for curses.', 20, top + 106, paint(C.mute), r.small);
  const cy = g.cy / g.s;
  const mid = REF_W / 2;
  // under the vial: the prompt, the counter, the result
  const ty = cy + LAYOUT.counterGap + 30;
  centered(c, r.regular, 'Drop a file into the potion', mid, ty, C.mute, f.restA);
  centered(c, r.regular, 'Release to drop it in', mid, ty, C.ready, f.readyA);
  if (f.counterA > 0.01) {
    const pct = `${Math.floor(f.p * 100 + 0.0001)}`;
    const wd = textWidth(r.bold, pct);
    const wp = textWidth(r.boldSmall, '%');
    const x = mid - (wd + wp + 2) / 2;
    const base = cy + LAYOUT.counterGap + LAYOUT.digit * 0.72;
    c.drawText(pct, x, base, paint(C.ink, f.counterA), r.bold);
    c.drawText('%', x + wd + 2, base - LAYOUT.digit * 0.42, paint(C.mute, f.counterA), r.boldSmall);
    const subY = base + 24;
    centered(c, r.regular, mbText(f.p), mid, subY, C.mute, f.counterA * (1 - smooth(f.doneA * 3)));
    centered(c, r.regular, 'Upload complete', mid, subY, C.done, f.counterA * smooth((f.doneA - 0.35) / 0.65));
  }
  // the card
  const cardTop = (g.cy + g.cardY * g.s) / g.s;
  const tx = REF_W / 2 - LAYOUT.cardW / 2 + 58;
  c.drawText(FILE.name, tx, cardTop + 30, paint(C.ink, 0.95), r.medium);
  const lines = ['48.2 MB · drag it into the potion', 'Brewing into your grimoire…', 'Added to your grimoire'];
  const cols = [C.mute, C.mute, C.done];
  const st = f.cardState;
  // the previous line gives way quickly, the new one comes in (100 ms each)
  const since = st === 2 ? f.t - T.fill : st === 1 ? f.t - T.drop : 1;
  const inK = smooth(seg(since, 0.1, 0.2));
  if (since < 0.1 && st > 0) c.drawText(lines[st - 1], tx, cardTop + 49, paint(cols[st - 1], 1 - smooth(since / 0.1)), r.small);
  c.drawText(lines[st], tx, cardTop + 49, paint(cols[st], st === 0 ? 1 : inK), r.small);
  // the file's icon on the card, and the one being carried
  const fileIcon = (x: number, y: number, sc: number, rot: number, a: number) => {
    'worklet';
    if (a <= 0.01) return;
    c.save();
    c.translate(x, y);
    c.rotate((rot * 180) / Math.PI, 0, 0);
    c.scale(sc, sc);
    const p = Skia.Paint();
    p.setAlphaf(a);
    c.drawImageRect(r.file, Skia.XYWHRect(0, 0, 84, 102), Skia.XYWHRect(-14, -17, 28, 34), p);
    c.restore();
  };
  const icon = cardIcon(g);
  fileIcon(icon.x, (g.cy + icon.y * g.s) / g.s, 1, 0, 1 - f.slotEmpty);
  // the file being carried, with its name and size under it (as in the recording's drag)
  if (f.ghostAlpha > 0.01) {
    const gy = (g.cy + f.ghostY * g.s) / g.s;
    fileIcon(f.ghostX, gy, f.ghostScale, f.ghostRot, f.ghostAlpha);
    const la = f.ghostAlpha * clamp01((f.ghostScale - 0.7) / 0.3);
    centered(c, r.small, FILE.name, f.ghostX, gy + 31, C.ink, la);
    centered(c, r.small, `${FILE.mb} MB`, f.ghostX, gy + 45, C.mute, la);
  }
  // the loop's fade to black (and back)
  if (f.fade > 0.003) {
    c.restore();
    c.save();
    c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), paint(C.page, f.fade));
  }
  c.restore();
};
