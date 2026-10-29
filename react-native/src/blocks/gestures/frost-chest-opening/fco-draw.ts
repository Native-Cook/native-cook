// Frost Chest Opening — everything the canvas draws, as UI-thread worklets.
// drawScene() paints one frame from a Frame (fco-motion.ts): the sky and its rays or the frost
// backdrop, the chest, the card, the hold button with its orb, the rewards, the snow and the flash.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  type SkRuntimeEffect,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';

import { easeOut, type Frame, LOOP, mix, ORB, rand, seg, T } from './fco-motion';
import { C, COPY, L, rgba } from './fco-theme';

export type Geo = { w: number; h: number; top: number; bottom: number; sx: number; sy: number };

/** A line of text with its font already sized to the measured width. */
export type Label = { font: SkFont; width: number };

export type Res = {
  chestClosed: SkImage;
  chestOpen: SkImage;
  jacket: SkImage;
  items: [SkImage, SkImage, SkImage];
  coin: SkImage;
  crystal: SkImage;
  backdrop: SkImage;
  floor: SkImage;
  cardFront: SkImage;
  cardBack: SkImage;
  rays: SkRuntimeEffect;
  rayLight: SkRuntimeEffect;
  light: SkRuntimeEffect;
  orb: SkRuntimeEffect;
  text: {
    balance: Label;
    eyebrow: Label;
    title: Label;
    drop: Label;
    hint1: Label;
    hint2: Label;
    reward1: Label;
    reward2: Label;
    rarity: Label;
    others: Label;
    items: [Label, Label, Label];
    claim: Label;
  };
  arrow: SkPath;
  hand: SkPath;
  star: SkPath;
};

// ── small helpers ─────────────────────────────────────────────────────────────────────────────

function paint(color: number, a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(rgba(color, a)));
  return p;
}

function X(g: Geo, x: number) {
  'worklet';
  return x * g.sx;
}

function Y(g: Geo, y: number) {
  'worklet';
  return y * g.sy;
}

function S(g: Geo, v: number) {
  'worklet';
  return v * g.sx;
}

function text(c: SkCanvas, l: Label, str: string, cx: number, base: number, color: number, a: number) {
  'worklet';
  if (a <= 0.003) return;
  c.drawText(str, cx - l.width / 2, base, paint(color, a), l.font);
}

function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function drawImageFit(c: SkCanvas, img: SkImage, cx: number, cy: number, w: number, a: number, extra?: (p: ReturnType<typeof Skia.Paint>) => void) {
  'worklet';
  const iw = img.width();
  const ih = img.height();
  const h = (w * ih) / iw;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setAlphaf(a);
  if (extra) extra(p);
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, iw, ih), Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h), 1, 1, p);
}

/** A soft white-glass surface: milky fill, brighter top edge, white rim, a faint blue shadow. */
function glass(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, a: number, fillA = 0.62) {
  'worklet';
  if (a <= 0.003) return;
  const shadow = paint(0x7d9bd6, 0.22 * a);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  c.drawRRect(rrect(x, y + 6, w, h, r), shadow);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y },
      { x, y: y + h },
      [Skia.Color(rgba(0xffffff, (fillA + 0.18) * a)), Skia.Color(rgba(0xf2f6fd, fillA * a)), Skia.Color(rgba(0xe6eefb, (fillA - 0.06) * a))],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(rrect(x, y, w, h, r), fill);
  const rim = paint(0xffffff, 0.95 * a);
  rim.setStyle(1);
  rim.setStrokeWidth(1.5);
  c.drawRRect(rrect(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r - 0.75), rim);
}

/**
 * The reference's sparkle: a four-point star with concave sides. kind 0 is the cyan star with a white
 * core, kind 2 the same with a paler rim (the burst's), kind 1 a white star with a faint cyan edge.
 * `size` is tip to tip, in px.
 */
function star(c: SkCanvas, R: Res, x: number, y: number, size: number, a: number, kind = 0) {
  'worklet';
  if (a <= 0.01 || size <= 0.3) return;
  c.save();
  c.translate(x, y);
  c.scale(size / 20, size / 20);
  if (kind === 0 || kind === 2) {
    c.drawPath(R.star, paint(kind === 0 ? C.starEdge : C.starEdgeSoft, a));
    c.scale(0.58, 0.58);
    c.drawPath(R.star, paint(0xffffff, a));
  } else {
    c.drawPath(R.star, paint(C.starEdge, 0.45 * a));
    c.scale(0.78, 0.78);
    c.drawPath(R.star, paint(0xffffff, a));
  }
  c.restore();
}

// ── background ────────────────────────────────────────────────────────────────────────────────

function drawSky(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  // Rotated so the dark wedges sit where the video's do (every 45° from −2°).
  const sh = R.rays.makeShader([g.w, g.h, X(g, L.rayCX), Y(g, L.rayCY), 0.4276, f.sky]);
  const p = Skia.Paint();
  p.setShader(sh);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), p);
  // The snow floor in front of the rays: snow, ice crystals and bokeh, fading out upwards.
  const fl = R.floor;
  const fh = (g.w * fl.height()) / fl.width();
  const fp = Skia.Paint();
  fp.setAlphaf(0.92);
  c.drawImageRectOptions(fl, Skia.XYWHRect(0, 0, fl.width(), fl.height()), Skia.XYWHRect(0, g.h - fh, g.w, fh), 1, 1, fp);
  // A white haze over it, like the reference's bright, out-of-focus foreground.
  const haze = Skia.Paint();
  haze.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: g.h - fh }, { x: 0, y: g.h }, [Skia.Color('rgba(246,249,255,0)'), Skia.Color('rgba(246,249,255,0.45)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, g.h - fh, g.w, fh), haze);
  if (f.sky > 0.01) {
    const lp = Skia.Paint();
    lp.setShader(R.rayLight.makeShader([g.w, g.h, X(g, L.rayCX), Y(g, L.rayCY), 0.4276, f.sky]));
    c.drawRect(Skia.XYWHRect(0, Y(g, L.rayCY), g.w, g.h - Y(g, L.rayCY)), lp);
  }
}

function drawFrost(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.frost <= 0.003) return;
  const fade = f.frost < 1;
  if (fade) {
    const lp = Skia.Paint();
    lp.setAlphaf(f.frost);
    c.saveLayer(lp);
  }
  const img = R.backdrop;
  // Cover the screen; a slow breathing zoom keeps the frost alive.
  const breathe = 1.03 + 0.012 * Math.sin((f.idle / LOOP) * Math.PI * 4);
  const dw = g.w * breathe;
  const dh = g.h * breathe;
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect((g.w - dw) / 2, (g.h - dh) / 2, dw, dh), 1, 1, Skia.Paint());
  // Light from the open chest washes the middle of the page.
  const cx = X(g, L.openCX);
  const cy = Y(g, L.mouthY - 40);
  const wash = Skia.Paint();
  wash.setShader(
    Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, S(g, 330), [Skia.Color('rgba(255,255,255,0.7)'), Skia.Color('rgba(240,246,255,0.25)'), Skia.Color('rgba(240,246,255,0)')], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), wash);
  if (fade) c.restore();
}

// ── chest ─────────────────────────────────────────────────────────────────────────────────────

function drawChestGlow(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  // The white light behind the chest's lid; the charge makes it bigger and stronger.
  const cx = X(g, L.chestCX + 3);
  const cy = Y(g, 375);
  const a = (0.4 + 0.6 * f.charge) * (1 - f.reveal);
  if (a <= 0.01) return;
  const r = S(g, 220 + 90 * f.charge);
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r, [Skia.Color(rgba(0xffffff, 0.85 * a)), Skia.Color(rgba(0xd2f4ff, 0.4 * a)), Skia.Color(rgba(0xd2f4ff, 0))], [0, 0.42, 1], TileMode.Clamp));
  c.drawCircle(cx, cy, r, p);
}

function drawClosedChest(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  // It comes back a little large after Claim and settles; otherwise it stands still, as in the video.
  const back = 1 + 0.04 * (1 - easeOut(seg(f.t, T.swapBack, T.restIn[1]))) * (f.t >= T.swapBack ? 1 : 0);
  const cx = X(g, L.chestCX);
  const cy = Y(g, L.chestCY);
  const w = S(g, L.chestW) * back;
  const img = R.chestClosed;
  const h = ((w * img.height()) / img.width()) * L.chestSY;
  const dst = Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h);
  const src = Skia.XYWHRect(0, 0, img.width(), img.height());
  const sprite = (p: ReturnType<typeof Skia.Paint>) => {
    'worklet';
    c.drawImageRectOptions(img, src, dst, 1, 1, p);
  };
  sprite(Skia.Paint());
  // Lit from inside: green and blue climb toward full while red stays, so the body turns a
  // saturated glowing cyan and the pale lid a cyan-white (the video's colours: the front panel goes
  // from (65,196,236) to (113,254,255), the side from (88,157,209) to (74,189,251)).
  const lit = f.charge;
  if (lit > 0.01) {
    const p = Skia.Paint();
    p.setAlphaf(lit);
    p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.95, 0, 0, 0, 0, 0, 0.4, 0, 0, 0.6, 0, 0, 0.2, 0, 0.8, 0, 0, 0, 1, 0]));
    sprite(p);
  }
  // Its light blooms over the lid and the upper body.
  if (lit > 0.01) {
    const bp = Skia.Paint();
    const by = Y(g, 385);
    bp.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: by }, S(g, 175), [Skia.Color(rgba(0xffffff, 0.6 * lit)), Skia.Color(rgba(0xe6fbff, 0.32 * lit)), Skia.Color(rgba(0xe6fbff, 0))], [0, 0.5, 1], TileMode.Clamp));
    c.save();
    c.translate(cx, by);
    c.scale(1.25, 0.8);
    c.translate(-cx, -by);
    c.drawCircle(cx, by, S(g, 175), bp);
    c.restore();
  }
  // The orb is swallowed: the chest burns toward white from inside.
  if (f.seam > 0.01) {
    const p = Skia.Paint();
    p.setAlphaf(f.seam);
    p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.3, 0, 0, 0, 0.3, 0, 0.3, 0, 0, 0.66, 0, 0, 0.3, 0, 0.7, 0, 0, 0, 1, 0]));
    p.setBlendMode(BlendMode.Screen);
    sprite(p);
  }
}

/**
 * The diagonal light shaft of the full charge: a white band leaning like "/" (22°) that sweeps the
 * chest from left to right. Measured on the video: its core crosses y = 445 at a steady 920 pt/s,
 * about 65 pt wide in the middle of the sweep and narrow at both ends.
 */
function drawShaft(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const u = f.shaft;
  if (u <= 0 || u >= 1) return;
  // It shines on the chest only: the band is drawn in a layer masked by the chest sprite.
  const img = R.chestClosed;
  const cw = S(g, L.chestW);
  const chh = ((cw * img.height()) / img.width()) * L.chestSY;
  const chest = Skia.XYWHRect(X(g, L.chestCX) - cw / 2, Y(g, L.chestCY) - chh / 2, cw, chh);
  c.saveLayer(undefined, chest);
  const env = seg(u, 0, 0.08) * (1 - seg(u, 0.9, 1));
  const x = X(g, -15 + 413 * u);
  const y = Y(g, 445);
  const half = S(g, 8 + 40 * Math.sin(Math.PI * u));
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: -half * 1.7, y: 0 }, { x: half * 1.7, y: 0 }, [Skia.Color('rgba(255,255,255,0)'), Skia.Color(rgba(0xffffff, env)), Skia.Color(rgba(0xffffff, env)), Skia.Color('rgba(255,255,255,0)')], [0, 0.3, 0.7, 1], TileMode.Clamp));
  const fade = Skia.Paint();
  fade.setBlendMode(BlendMode.DstIn);
  fade.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -S(g, 300) }, { x: 0, y: S(g, 260) }, [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,1)'), Skia.Color('rgba(0,0,0,1)'), Skia.Color('rgba(0,0,0,0)')], [0, 0.25, 0.8, 1], TileMode.Clamp));
  c.save();
  c.translate(x, y);
  c.rotate(22, 0, 0);
  const box = Skia.XYWHRect(-half * 1.7, -S(g, 300), half * 3.4, S(g, 560));
  c.saveLayer(undefined, box);
  c.drawRect(box, p);
  c.drawRect(box, fade);
  c.restore();
  c.restore();
  const mask = Skia.Paint();
  mask.setBlendMode(BlendMode.DstIn);
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), chest, 1, 1, mask);
  c.restore();
}

function drawOpenChest(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const cx = X(g, L.openCX);
  const cy = Y(g, L.openCY + f.openDY);
  const wd = S(g, L.openW);
  // Measured colours: during the burst the body is the sprite a little darker (118,189,220); once
  // the light settles it is 10 % toward a warm white (141,200,228).
  const k = 0.94 - 0.04 * f.pale;
  const w = 0.1 * f.pale;
  drawImageFit(c, R.chestOpen, cx, cy, wd, 1, (p) => {
    p.setColorFilter(Skia.ColorFilter.MakeMatrix([k, 0, 0, 0, w * 1.0, 0, k, 0, 0, w * 0.94, 0, 0, k, 0, w * 0.9, 0, 0, 0, 1, 0]));
  });
  if (f.glare > 0.01) {
    drawImageFit(c, R.chestOpen, cx, cy, wd, 0.9 * f.glare, (p) => {
      p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.3, 0, 0, 0, 0.3, 0, 0.3, 0, 0, 0.66, 0, 0, 0.3, 0, 0.7, 0, 0, 0, 1, 0]));
      p.setBlendMode(BlendMode.Screen);
    });
  }
}

/** The glow that stays in the open chest's mouth, in front of the lid. */
function drawMouthGlow(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const ma = f.mouth * (1 - f.sink);
  if (ma <= 0.01) return;
  const cx = X(g, L.openCX);
  const mouth = Y(g, L.mouthY + f.openDY);
  const pool = Skia.Paint();
  const r = S(g, 170);
  pool.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: mouth }, r, [Skia.Color(rgba(0xffffff, ma)), Skia.Color(rgba(0xffffff, 0.9 * ma)), Skia.Color(rgba(0xeefcff, 0.45 * ma)), Skia.Color(rgba(0xeefcff, 0))], [0, 0.35, 0.65, 1], TileMode.Clamp));
  c.save();
  c.translate(cx, mouth);
  c.scale(1, 0.5);
  c.translate(-cx, -mouth);
  c.drawCircle(cx, mouth, r, pool);
  c.restore();
}

// ── the reveal's light ──────────────────────────────────────────────────────────────────────────
// One additive pass of LIGHT_SKSL: core, god rays, pillar, anamorphic streak and shockwave ring.

function drawLight(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.lCore < 0.004 && f.lRays < 0.004 && f.lStreak < 0.004 && f.lRingA < 0.004 && f.lPillar < 0.004) return;
  const p = Skia.Paint();
  p.setBlendMode(BlendMode.Plus);
  p.setShader(R.light.makeShader([g.w, g.h, X(g, f.lX), Y(g, f.lY), f.t, f.lCore, f.lRays, f.lReach, f.lStreak, S(g, f.lRingR), f.lRingA, f.lPillar, Y(g, L.mouthY + f.openDY), f.lBelow]));
  c.drawRect(Skia.XYWHRect(-40, -40, g.w + 80, g.h + 80), p);
}

function drawVignette(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.vignette <= 0.004) return;
  const p = Skia.Paint();
  const cx = X(g, f.lX);
  const cy = Y(g, f.lY - 60);
  const r = Math.hypot(g.w, g.h) * 0.62;
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r, [Skia.Color('rgba(10,30,70,0)'), Skia.Color('rgba(10,30,70,0)'), Skia.Color(rgba(0x0a1e46, f.vignette))], [0, 0.45, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-40, -40, g.w + 80, g.h + 80), p);
}

/**
 * The scene's grade: `e` above 1 pushes colours past white so the highlights blow out; `dim` sinks
 * them to a deep, saturated blue (red falls most, blue least).
 */
function gradeFilter(e: number, dim: number) {
  'worklet';
  const lift = (e - 1) * 0.08;
  const r = e * (1 - 0.62 * dim);
  const gr = e * (1 - 0.5 * dim);
  const b = e * (1 - 0.22 * dim);
  return Skia.ColorFilter.MakeMatrix([r, 0, 0, 0, lift, 0, gr, 0, 0, lift, 0, 0, b, 0, lift, 0, 0, 0, 1, 0]);
}

/** Light dust rising in the beam (the Skia version; WebGPU draws more of it when it is there). */
function drawMotes(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.motes < 0) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  for (let i = 0; i < 60; i++) {
    const born = 0.03 + 0.9 * Math.pow(rand(i, 91), 1.4);
    const life = 0.9 + 0.8 * rand(i, 92);
    const age = f.motes - born;
    if (age < 0 || age > life) continue;
    const k = age / life;
    const side = rand(i, 97) > 0.5 ? 1 : -1;
    const x = X(g, 198 + side * (38 + 110 * rand(i, 93)) + 14 * Math.sin(age * 2.2 + i));
    const y = Y(g, 395 - 20 * rand(i, 94) - (70 + 120 * rand(i, 95)) * age);
    const rad = S(g, 0.8 + 2.2 * rand(i, 96));
    const a = Math.sin(Math.PI * k) * (0.6 + 0.4 * Math.sin(age * 9 + i * 3));
    p.setColor(Skia.Color(rgba(0xeafcff, Math.max(0, a))));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, rad * 0.8, true));
    c.drawCircle(x, y, rad, p);
  }
}

// ── sparkle burst ─────────────────────────────────────────────────────────────────────────────
// Out of the opened chest stream ~40 stars (cyan with a white core, some all white) drifting up
// while they twinkle out, and small cyan ice chips tumbling outwards; a few glints sit on the
// chest's front. All seeded, so the burst is the same every loop. WebGPU draws the chips in 3D when
// it is there (FcoShardBurst.tsx); this is the Skia version of them.

export const CHIPS = 26;

export function drawChips(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.sparkle < 0) return;
  for (let i = 0; i < CHIPS; i++) {
    const born = 0.3 * rand(i, 71);
    const life = 0.35 + 0.22 * rand(i, 72);
    const age = f.sparkle - born;
    if (age < 0 || age > life) continue;
    const x0 = 196 + (rand(i, 73) - 0.5) * 250;
    const y0 = 395 + (rand(i, 74) - 0.6) * 90;
    const ang = Math.atan2(y0 - 470, x0 - 196) + (rand(i, 75) - 0.5) * 0.6;
    const v = 40 + 90 * rand(i, 76);
    const travel = (v * (1 - Math.exp(-2.5 * age))) / 2.5;
    const x = X(g, x0 + Math.cos(ang) * travel);
    const y = Y(g, y0 + Math.sin(ang) * travel - 18 * age);
    const k = age / life;
    const a = seg(k, 0, 0.12) * (1 - seg(k, 0.6, 1)) * 0.85;
    const cw = S(g, 2.6 + 2 * rand(i, 77));
    const ch = cw * (1.4 + 0.6 * rand(i, 78));
    const turn = Math.cos(age * (7 + 8 * rand(i, 79)) + i);
    c.save();
    c.translate(x, y);
    c.rotate(rand(i, 80) * 180 + age * 220, 0, 0);
    c.scale(0.35 + 0.65 * Math.abs(turn), 1);
    c.drawRect(Skia.XYWHRect(-cw / 2, -ch / 2, cw, ch), paint(C.chip, a));
    c.restore();
  }
}

function drawBurstStars(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.sparkle < 0) return;
  for (let i = 0; i < 64; i++) {
    const born = 0.02 + 0.4 * Math.pow(rand(i, 61), 1.3);
    const life = 0.26 + 0.24 * rand(i, 62);
    const age = f.sparkle - born;
    if (age < 0 || age > life) continue;
    const x0 = 40 + 300 * rand(i, 63);
    const y0 = 282 + 170 * Math.pow(rand(i, 64), 1.25);
    const vy = 25 + 55 * rand(i, 65);
    const vx = (x0 - 196) * 0.22 + (rand(i, 66) - 0.5) * 24;
    const k = age / life;
    const pop = easeOut(seg(k, 0, 0.18));
    const out = 1 - seg(k, 0.6, 1);
    const size = S(g, 16 + 34 * Math.pow(rand(i, 67), 1.4)) * mix(0.45, 1, pop) * mix(0.6, 1, out);
    star(c, R, X(g, x0 + vx * age), Y(g, y0 - vy * age), size, pop * out, rand(i, 68) < 0.72 ? 2 : 1);
  }
  // Glints on the chest's front, twinkling while the light pours out.
  const spots: [number, number, number, number][] = [[120, 480, 26, 0.05], [152, 517, 32, 0.12], [245, 442, 26, 0.0], [297, 477, 22, 0.2], [322, 520, 22, 0.3], [230, 560, 30, 0.26]];
  for (let i = 0; i < spots.length; i++) {
    const [x, y, s0, at] = spots[i];
    const u = seg(f.sparkle, at, at + 0.36);
    if (u > 0 && u < 1) star(c, R, X(g, x), Y(g, y + f.openDY), S(g, s0) * Math.sin(u * Math.PI), 1, 1);
  }
}

/** Stars of the rest and the charge, where and when the video shows them (pt, s, size). */
const CHARGE_STARS: [number, number, number, number, number][] = [
  // x, y, size, from, to
  [188, 338, 34, 0.84, 1.42],
  [268, 351, 60, 1.08, 1.78],
  [127, 291, 42, 1.4, 1.95],
  [288, 477, 54, 1.94, 2.6],
  [306, 481, 26, 2.4, 2.66],
];

function drawSparkles(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.reveal < 0.5) {
    for (let i = 0; i < CHARGE_STARS.length; i++) {
      const [x, y, s0, from, to] = CHARGE_STARS[i];
      const u = seg(f.t, from, to);
      if (u > 0 && u < 1) star(c, R, X(g, x), Y(g, y), S(g, s0) * Math.sin(Math.pow(u, 0.7) * Math.PI), 1);
    }
    // The rest's small stars on and round the chest.
    const restA = f.header * (1 - seg(f.t, 0.84, 0.9));
    star(c, R, X(g, 188), Y(g, 338), S(g, 16), restA, 1);
    star(c, R, X(g, 45), Y(g, 505), S(g, 12), restA, 1);
    star(c, R, X(g, 100), Y(g, 525), S(g, 16), restA, 1);
  } else {
    drawBurstStars(c, R, g, f);
  }
}

// ── card ──────────────────────────────────────────────────────────────────────────────────────

function drawCardFront(c: SkCanvas, R: Res, g: Geo, w: number, h: number) {
  'worklet';
  const r = S(g, L.cardR);
  const halo = paint(0xffffff, 0.35);
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, S(g, 10), true));
  c.drawRRect(rrect(-w / 2, -h / 2, w, h, r), halo);
  const plate = Skia.Paint();
  plate.setAlphaf(0.82);
  c.drawImageRectOptions(R.cardFront, Skia.XYWHRect(0, 0, R.cardFront.width(), R.cardFront.height()), Skia.XYWHRect(-w / 2, -h / 2, w, h), 1, 1, plate);

  const k = w / S(g, L.cardW);
  const oy = (y: number) => (Y(g, y) - Y(g, L.cardCY)) * k;
  drawImageFit(c, R.jacket, S(g, L.jacketCX - L.cardCX) * k, oy(L.jacketCY), S(g, L.jacketW) * k, 1);
  star(c, R, S(g, 135 - L.cardCX) * k, oy(135), S(g, 20) * k, 1, 0);
  c.save();
  c.scale(k, k);
  text(c, R.text.reward1, COPY.reward1, 0, oy(L.rewardBase) / k, C.deepBlue, 1);
  text(c, R.text.reward2, COPY.reward2, 0, oy(L.rewardBase2) / k, C.deepBlue, 1);
  // The rarity chip.
  const cw = S(g, L.rarityW);
  const ch = S(g, L.rarityH);
  const cy = oy(L.rarityCY) / k;
  const chip = paint(0xffffff, 0.78);
  const chipShadow = paint(0x8aa6da, 0.25);
  chipShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawRRect(rrect(-cw / 2, cy - ch / 2 + 2, cw, ch, ch / 2), chipShadow);
  c.drawRRect(rrect(-cw / 2, cy - ch / 2, cw, ch, ch / 2), chip);
  const tw = R.text.rarity.width;
  const icon = S(g, 9);
  const gap = S(g, 3);
  const startX = -(icon + gap + tw) / 2;
  drawImageFit(c, R.crystal, startX + icon / 2, cy, icon, 1);
  const base = cy + R.text.rarity.font.getSize() * 0.36;
  c.drawText(COPY.rarity, startX + icon + gap, base, paint(C.inkSoft, 1), R.text.rarity.font);
  c.restore();

}

function drawCardBack(c: SkCanvas, R: Res, g: Geo, w: number, h: number) {
  'worklet';
  const r = S(g, L.cardR);
  const halo = paint(0xbfeaff, 0.7);
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, S(g, 14), true));
  c.drawRRect(rrect(-w / 2, -h / 2, w, h, r), halo);
  c.drawImageRectOptions(R.cardBack, Skia.XYWHRect(0, 0, R.cardBack.width(), R.cardBack.height()), Skia.XYWHRect(-w / 2, -h / 2, w, h), 1, 1, Skia.Paint());
}

function drawCard(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.reveal < 0.5 || f.t < T.card) return;
  const mouthX = X(g, L.openCX);
  const mouthY = Y(g, L.mouthY);
  const fx = X(g, f.cardX);
  let cx = fx;
  let cy = Y(g, f.cardY);
  let s = f.cardS;
  let a = f.cardA;
  if (f.sink > 0) {
    cx = mix(fx, mouthX, f.sink);
    cy = mix(cy, mouthY + S(g, 10), f.sink);
    s *= mix(1, 0.3, f.sink);
    a *= 1 - seg(f.sink, 0.7, 1);
  }
  if (a <= 0.01) return;
  const w = S(g, L.cardW);
  const h = S(g, L.cardH);
  const th = (f.spin * Math.PI) / 180;
  const cos = Math.cos(th);
  const sin = Math.sin(th);
  const d = S(g, 700);
  const blur = S(g, f.cardBlur) + f.sink * 4;
  // Born in the light: a white-hot halo behind it, and the card itself white until it cools.
  if (f.cardHalo > 0.01) {
    const hw = w * s * Math.max(0.25, Math.abs(cos)) * 1.18;
    const hh = h * s * 1.12;
    const halo = paint(0xffffff, 0.85 * f.cardHalo * a);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, S(g, 22), true));
    c.drawRRect(rrect(cx - hw / 2, cy - hh / 2, hw, hh, S(g, 30)), halo);
  }
  const layer = Skia.Paint();
  layer.setAlphaf(a);
  if (blur > 0.4) layer.setImageFilter(Skia.ImageFilter.MakeBlur(blur, blur, TileMode.Decal, null));
  const hot = f.cardHot;
  if (hot > 0.01) layer.setColorFilter(Skia.ColorFilter.MakeMatrix([1 - hot, 0, 0, 0, hot, 0, 1 - hot * 0.97, 0, 0, hot * 0.99, 0, 0, 1 - hot * 0.94, 0, hot, 0, 0, 0, 1, 0]));
  c.saveLayer(layer);
  c.translate(cx, cy);
  c.concat(Skia.Matrix([cos, 0, 0, 0, 1, 0, sin / d, 0, 1]));
  c.scale(s, s);
  if (cos >= 0) drawCardFront(c, R, g, w, h);
  else {
    c.scale(-1, 1);
    drawCardBack(c, R, g, w, h);
  }
  c.restore();
}

// ── hold button and orb ───────────────────────────────────────────────────────────────────────

function drawHold(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = f.pill;
  // The pill, pushed in a little while held.
  if (a > 0.003) {
    const pw = S(g, L.pillW);
    const ph = S(g, L.pillH);
    const px = X(g, L.pillX);
    const py = Y(g, L.pillY);
    const k = 1 - 0.03 * f.press;
    c.save();
    c.translate(px + pw / 2, py + ph / 2);
    c.scale(k, k);
    c.translate(-(px + pw / 2), -(py + ph / 2));
    glass(c, px, py, pw, ph, ph / 2, a, 0.7);
    drawImageFit(c, R.crystal, px + S(g, 37), py + ph / 2, S(g, L.pillIcon), a);
    const tx = X(g, L.pillTextX) + R.text.drop.width / 2;
    text(c, R.text.drop, COPY.drop, tx, Y(g, L.dropBase), C.ink, a);
    c.restore();
  }
  // Hint.
  if (f.hint > 0.003) {
    const hs = S(g, L.handSize) / 24;
    c.save();
    c.translate(X(g, 201), Y(g, L.handY));
    c.scale(hs, hs);
    c.translate(-12, -12);
    const stroke = paint(C.hint, f.hint);
    stroke.setStyle(1);
    stroke.setStrokeWidth(1.8);
    stroke.setStrokeCap(1);
    stroke.setStrokeJoin(1);
    c.drawPath(R.hand, stroke);
    c.restore();
    text(c, R.text.hint1, COPY.hint1, X(g, 201), Y(g, L.hintBase), C.hint, f.hint);
    text(c, R.text.hint2, COPY.hint2, X(g, 201), Y(g, L.hintBase2), C.hint, f.hint);
  }
  // The bead at rest, the orb while charging and flying.
  const showOrb = f.orb > 0;
  if (!showOrb && a <= 0.003) return;
  const cx = X(g, showOrb ? f.orbX : ORB.bead.x);
  const cy = Y(g, showOrb ? f.orbY : ORB.bead.y);
  const r = S(g, showOrb ? f.orbR : ORB.bead.r);
  const oa = showOrb ? 1 : a;
  // The bright pool under the orb stays on the pill and fades with it.
  if (showOrb) {
    const pr = S(g, ORB.end.r) * 1.25;
    const px = X(g, ORB.end.x);
    const py = Y(g, ORB.end.y + ORB.end.r * 0.8);
    const pool = Skia.Paint();
    pool.setShader(Skia.Shader.MakeRadialGradient({ x: px, y: py }, pr, [Skia.Color(rgba(0xffffff, 0.85 * a)), Skia.Color(rgba(0xffffff, 0))], null, TileMode.Clamp));
    c.drawCircle(px, py, pr, pool);
  }
  // A white bloom ring round the frosted glass.
  const bloom = Skia.Paint();
  bloom.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r * 1.2, [Skia.Color(rgba(0xffffff, 0.9 * oa)), Skia.Color(rgba(0xffffff, 0.9 * oa)), Skia.Color(rgba(0xffffff, 0))], [0, 0.8, 1], TileMode.Clamp));
  c.drawCircle(cx, cy, r * 1.2, bloom);
  const sh = R.orb.makeShader([cx, cy, r, f.fill, f.t, 0, oa]);
  const p = Skia.Paint();
  p.setShader(sh);
  c.drawRect(Skia.XYWHRect(cx - r - 2, cy - r - 2, r * 2 + 4, r * 2 + 4), p);
}

/** Cyan ice dashes converging on the orb while it charges. */
function drawStreaks(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  if (f.orb <= 0 || f.t < 0.72) return;
  const k = seg(f.t, 0.72, 0.85) * (1 - seg(f.fly, 0.5, 1));
  if (k <= 0.01) return;
  const cx = X(g, f.orbX);
  const cy = Y(g, f.orbY);
  const p = paint(C.streak, 1);
  p.setStyle(1);
  p.setStrokeCap(1);
  p.setStrokeWidth(S(g, 2.2));
  for (let i = 0; i < 26; i++) {
    const ang = rand(i, 21) * Math.PI * 2;
    const life = 0.42 + 0.3 * rand(i, 22);
    const u = (((f.t - 0.72) / life + rand(i, 23)) % 1 + 1) % 1;
    const rad = S(g, mix(165, 62, u * u));
    const len = S(g, 8 + 11 * rand(i, 24));
    p.setAlphaf(Math.sin(u * Math.PI) * k * (0.55 + 0.45 * rand(i, 25)));
    const dx = Math.cos(ang);
    const dy = Math.sin(ang);
    c.drawLine(cx + dx * rad, cy + dy * rad, cx + dx * (rad + len), cy + dy * (rad + len), p);
  }
}

// ── header and title ──────────────────────────────────────────────────────────────────────────

function drawHeader(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = f.header;
  if (a <= 0.003) return;
  const top = g.top;
  // Back button.
  const bx = X(g, L.backX);
  const by = top + S(g, L.backY);
  const br = S(g, L.backD / 2);
  glass(c, bx - br, by - br, br * 2, br * 2, br, a, 0.58);
  c.save();
  c.translate(bx, by);
  c.scale(S(g, 22) / 24, S(g, 22) / 24);
  c.translate(-12, -12);
  const ar = paint(C.ink, 0.85 * a);
  ar.setStyle(1);
  ar.setStrokeWidth(2);
  ar.setStrokeCap(1);
  ar.setStrokeJoin(1);
  c.drawPath(R.arrow, ar);
  c.restore();
  // Balance pill.
  const px = X(g, L.coinX);
  const py = top + S(g, L.coinY);
  const pw = S(g, L.coinW);
  const ph = S(g, L.coinH);
  glass(c, px, py, pw, ph, ph / 2, a, 0.5);
  drawImageFit(c, R.coin, px + S(g, 26), py + ph / 2, S(g, L.coinIcon), a);
  text(c, R.text.balance, COPY.balance, px + S(g, 47) + R.text.balance.width / 2, top + S(g, L.balanceBase), C.ink, a);
  const pr = S(g, L.plusD / 2);
  const pcx = px + pw - S(g, 4.5) - pr;
  const pcy = py + ph / 2;
  const plus = Skia.Paint();
  plus.setAntiAlias(true);
  plus.setShader(Skia.Shader.MakeLinearGradient({ x: pcx, y: pcy - pr }, { x: pcx, y: pcy + pr }, [Skia.Color(rgba(C.claimTop, a)), Skia.Color(rgba(C.claimLow, a))], null, TileMode.Clamp));
  c.drawCircle(pcx, pcy, pr, plus);
  const ring = paint(0xffffff, 0.7 * a);
  ring.setStyle(1);
  ring.setStrokeWidth(1.2);
  c.drawCircle(pcx, pcy, pr - 0.6, ring);
  const cross = paint(0xffffff, a);
  cross.setStyle(1);
  cross.setStrokeWidth(S(g, 2.6));
  cross.setStrokeCap(1);
  const arm = S(g, 8);
  c.drawLine(pcx - arm, pcy, pcx + arm, pcy, cross);
  c.drawLine(pcx, pcy - arm, pcx, pcy + arm, cross);
}

function drawTitle(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  const a = f.header;
  if (a <= 0.003) return;
  const cx = X(g, 201);
  const lift = S(g, 8) * (1 - a);
  const glow = paint(C.titleGlow, 0.75 * a);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, S(g, 7), true));
  const e = R.text.eyebrow;
  const t = R.text.title;
  c.drawText(COPY.eyebrow, cx - e.width / 2, Y(g, L.eyebrowBase) - lift, glow, e.font);
  c.drawText(COPY.title, cx - t.width / 2, Y(g, L.titleBase) - lift, glow, t.font);
  text(c, e, COPY.eyebrow, cx, Y(g, L.eyebrowBase) - lift, C.title, a);
  text(c, t, COPY.title, cx, Y(g, L.titleBase) - lift, C.title, a);
}

// ── rewards and claim ─────────────────────────────────────────────────────────────────────────

function drawRewards(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.panel <= 0.003) return;
  const x = X(g, L.panelX);
  const w = S(g, L.panelW);
  const h = S(g, L.panelH);
  const y = Y(g, L.panelY);
  const r = S(g, L.panelR);
  // Frosted: blur what is behind the panel (the blur grows in as the panel arrives).
  c.save();
  c.clipRRect(rrect(x, y, w, h, r), ClipOp.Intersect, true);
  c.saveLayer(undefined, Skia.XYWHRect(x, y, w, h), Skia.ImageFilter.MakeBlur(14 * f.panel, 14 * f.panel, TileMode.Clamp, null));
  c.restore();
  c.restore();
  glass(c, x, y, w, h, r, f.panel, 0.2);
  text(c, R.text.others, COPY.others, X(g, 201), Y(g, L.othersBase), C.inkSoft, f.others);
  for (let i = 0; i < 3; i++) {
    const k = f.tiles[i];
    if (k <= 0.003) continue;
    const d = S(g, L.tileD);
    const tx = X(g, L.tileXs[i]);
    const ty = Y(g, L.tileCY);
    const sc = mix(0.92, 1, k);
    const lp = Skia.Paint();
    if (k < 0.98) lp.setImageFilter(Skia.ImageFilter.MakeBlur(3 * (1 - k), 3 * (1 - k), TileMode.Decal, null));
    c.saveLayer(lp);
    c.save();
    c.translate(tx, ty);
    c.scale(sc, sc);
    glass(c, -d / 2, -d / 2, d, d, S(g, L.tileR), k, 0.6);
    const art = R.items[i];
    const aspect = art.width() / art.height();
    drawImageFit(c, art, 0, 0, S(g, L.tileArt) * Math.min(1, aspect), k);
    c.restore();
    text(c, R.text.items[i], COPY.items[i], tx, Y(g, L.itemBase), C.inkSoft, k);
    c.restore();
  }
}

function drawClaim(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.claim <= 0.003) return;
  const x = X(g, L.claimX);
  const w = S(g, L.claimW);
  const h = S(g, L.claimH);
  const y = Y(g, L.claimY + f.claimY);
  const press = 1 - 0.03 * f.claimDown;
  const layer = Skia.Paint();
  layer.setAlphaf(f.claim);
  // It shows pale first and fills with its blue (the video's CLAIM text reaches 0.5 at 4.31 s).
  const ink = f.claimInk;
  layer.setColorFilter(Skia.ColorFilter.MakeMatrix([mix(0.35, 1, ink), 0, 0, 0, mix(0.65, 0, ink), 0, mix(0.35, 1, ink), 0, 0, mix(0.65, 0, ink), 0, 0, mix(0.35, 1, ink), 0, mix(0.65, 0, ink), 0, 0, 0, 1, 0]));
  c.saveLayer(layer);
  c.translate(x + w / 2, y + h / 2);
  c.scale(press, press);
  c.translate(-(x + w / 2), -(y + h / 2));
  const shadow = paint(0x1a8cff, 0.45);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawRRect(rrect(x + 10, y + 10, w - 20, h - 4, h / 2), shadow);
  const outer = paint(0xffffff, 0.85);
  c.drawRRect(rrect(x - 2, y - 2, w + 4, h + 4, h / 2 + 2), outer);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + h }, [Skia.Color(rgba(C.claimTop, 1)), Skia.Color(rgba(C.claimMid, 1)), Skia.Color(rgba(C.claimLow, 1))], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRRect(rrect(x, y, w, h, h / 2), fill);
  const shine = Skia.Paint();
  shine.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + h * 0.5 }, [Skia.Color('rgba(255,255,255,0.5)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawRRect(rrect(x + 6, y + 3, w - 12, h * 0.5, h * 0.25), shine);
  const glowBottom = Skia.Paint();
  glowBottom.setShader(Skia.Shader.MakeRadialGradient({ x: x + w / 2, y: y + h }, w * 0.5, [Skia.Color('rgba(130,220,255,0.55)'), Skia.Color('rgba(130,220,255,0)')], null, TileMode.Clamp));
  c.save();
  c.clipRRect(rrect(x, y, w, h, h / 2), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(x, y, w, h), glowBottom);
  c.restore();
  text(c, R.text.claim, COPY.claim, x + w / 2, Y(g, L.claimBase) + (y - Y(g, L.claimY)), C.claimInk, 1);
  c.restore();
}

// ── snow and flash ────────────────────────────────────────────────────────────────────────────

function drawSnow(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const life = LOOP / 2;
  const p = paint(0xffffff, 1);
  for (let i = 0; i < 44; i++) {
    const phase = f.idle / life + rand(i, 31);
    const cyc = Math.floor(phase);
    const u = phase - cyc;
    const seed = i * 7 + (cyc % 2);
    const x = rand(seed, 32) * g.w + S(g, 14) * Math.sin(u * Math.PI * 2 + i);
    const y = rand(seed, 33) * g.h * 0.96 + S(g, 46) * u;
    const big = rand(i, 34) > 0.86;
    const rad = big ? S(g, 2.6 + 2 * rand(i, 35)) : S(g, 0.9 + 1.3 * rand(i, 35));
    p.setAlphaf(Math.sin(u * Math.PI) * (big ? 0.55 : 0.85));
    if (big) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, rad * 0.6, true));
    else p.setMaskFilter(null);
    c.drawCircle(x, y, rad, p);
  }
}

function drawFlash(c: SkCanvas, R: Res, g: Geo, f: Frame) {
  'worklet';
  if (f.flash <= 0.003) return;
  const first = f.t < 6;
  const cx = X(g, first ? 200 : L.openCX);
  const cy = Y(g, first ? 430 : L.mouthY);
  // The first one is a burst out of the swallow point (measured radius: 90 pt at 2.64 s, 160 pt at
  // 2.68 s, past the screen's corners by 2.74 s); the second covers the screen at once.
  const rad = first
    ? S(g, 40 + 80 * seg(f.t, T.flashIn[0], 2.68) + 560 * Math.pow(seg(f.t, 2.68, T.flashIn[1]), 1.8))
    : Math.hypot(g.w, g.h) * 1.15;
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, rad, [Skia.Color(rgba(0xffffff, f.flash)), Skia.Color(rgba(0xfbfdff, f.flash * 0.92)), Skia.Color(rgba(0xeef8ff, f.flash * 0.55)), Skia.Color(rgba(0xeef8ff, 0))], [0, 0.35, 0.7, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), p);
  // A big white star marks the swallow point as the burst starts.
  if (first) {
    const u = seg(f.t, 2.61, 2.7);
    if (u > 0 && u < 1) star(c, R, cx, cy, S(g, 90) * Math.sin(Math.PI * u), 1, 1);
  }
}

// ── frame ─────────────────────────────────────────────────────────────────────────────────────

export function drawScene(c: SkCanvas, R: Res, g: Geo, f: Frame, gpuChips: boolean) {
  'worklet';
  // The burst's camera: a slow push-in while the orb charges, a punch and a short shake on impact.
  const pivotX = X(g, 200);
  const pivotY = Y(g, 430);
  c.save();
  c.translate(S(g, f.shakeX), S(g, f.shakeY));
  c.translate(pivotX, pivotY);
  c.scale(f.punch, f.punch);
  c.translate(-pivotX, -pivotY);
  const exposed = f.exposure > 1.002 || f.dim > 0.002;
  const ep = Skia.Paint();
  if (exposed) ep.setColorFilter(gradeFilter(f.exposure, f.dim));
  if (f.reveal < 0.5) {
    if (exposed) c.saveLayer(ep);
    drawSky(c, R, g, f);
    drawChestGlow(c, g, f);
    if (exposed) c.restore();
    // The light leaks out from behind the chest while the orb flies in (light is not graded).
    if (f.lBehind > 0.5) drawLight(c, R, g, f);
    if (exposed) c.saveLayer(ep);
    drawClosedChest(c, R, g, f);
    if (exposed) c.restore();
    drawVignette(c, g, f);
    if (f.lBehind < 0.5) drawLight(c, R, g, f);
  } else {
    if (exposed) c.saveLayer(ep);
    if (f.frost < 1) drawSky(c, R, g, { ...f, sky: 1 });
    drawFrost(c, R, g, f);
    const cardBehind = f.sink > 0.55;
    if (cardBehind) drawCard(c, R, g, f);
    drawOpenChest(c, R, g, f);
    if (exposed) c.restore();
    drawVignette(c, g, f);
    drawLight(c, R, g, f);
    drawMouthGlow(c, g, f);
    if (!gpuChips) {
      drawChips(c, g, f);
      drawMotes(c, g, f);
    }
    if (!cardBehind) drawCard(c, R, g, f);
  }
  drawSparkles(c, R, g, f);
  drawTitle(c, R, g, f);
  drawHeader(c, R, g, f);
  drawShaft(c, R, g, f);
  drawStreaks(c, g, f);
  drawHold(c, R, g, f);
  drawRewards(c, R, g, f);
  drawClaim(c, R, g, f);
  drawSnow(c, g, f);
  drawFlash(c, R, g, f);
  c.restore();
}
