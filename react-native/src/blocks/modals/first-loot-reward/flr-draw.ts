// First Loot Reward — the Skia layer: rays, aura, the badge (a ring that becomes a hexagon), the
// chest, sparkles and the burst. Worklets; helpers above their callers.

import { BlurStyle, type SkCanvas, type SkRuntimeEffect, Skia, StrokeCap, StrokeJoin } from '@shopify/react-native-skia';

import { type Chest, clamp01, easeOut, hash, LOOP, mix, seg, smooth } from './flr-motion';
import { BADGE, C, SPARKLES } from './flr-theme';

export type Scene = {
  t: number;
  bx: number; // badge centre
  by: number;
  badgeA: number; // badge opacity
  badgeS: number; // badge scale
  morph: number; // 0 ring … 1 hexagon
  ring: number; // the seed ring's sweep 0…1 (drawn while morph is low)
  sceneA: number; // rays, aura and sparkles
  sparkA: number; // sparkles' entrance 0…1
  chest: Chest;
  tiltX: number; // -1…1 (finger or phone)
  tiltY: number;
  press: number; // 0…1 pressed in
  sweep: number; // -1 none, else 0…1 a light sweep across the rim
  power: number; // strength of the burst (1, more after a long press)
  charge: number; // 0…1 while a press is held
  spin: number; // extra turn of the rays, in wedges (whole wedges keep the loop seamless)
  waveR: number; // shockwave radius (pt)
  waveA: number; // shockwave strength
  dust: number; // -1 none, else 0…1 through the disperse → converge of the badge's dust
  lootX: number; // where the loot flies to (the View loot button)
  lootY: number;
  w: number;
  h: number;
};

const paint = (color: string, alpha = 1) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(Math.max(0, alpha));
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

const linear = (x0: number, y0: number, x1: number, y1: number, cols: string[], pos: number[]) => {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    { x: x0, y: y0 },
    { x: x1, y: y1 },
    cols.map((c) => Skia.Color(c)),
    pos,
    0,
  );
};

/**
 * A hexagon standing on a point (circumradius r) with corners rounded by `rc`, blended toward a
 * circle by 1 - k. The outline is walked by its outward normal: inside each corner's cone the point
 * is the inset corner plus rc along the normal (an arc); between cones it jumps to the next corner,
 * which the path draws as the straight edge.
 */
export const hexPath = (r: number, k: number, rc: number) => {
  'worklet';
  const a = r * Math.cos(Math.PI / 6); // apothem
  const ri = (a - rc) / Math.cos(Math.PI / 6); // circumradius of the inset hexagon
  const rCircle = a * 1.035;
  const b = Skia.PathBuilder.Make();
  let first = true;
  for (let i = 0; i < 6; i++) {
    const al = Math.PI / 6 + (i * Math.PI) / 3; // corners at 30°, 90°, … (90° is the bottom point)
    for (let j = 0; j <= 10; j++) {
      const nu = al - Math.PI / 6 + (j / 10) * (Math.PI / 3);
      const hx = ri * Math.cos(al) + rc * Math.cos(nu);
      const hy = ri * Math.sin(al) + rc * Math.sin(nu);
      const x = mix(rCircle * Math.cos(nu), hx, k);
      const y = mix(rCircle * Math.sin(nu), hy, k);
      if (first) {
        b.moveTo(x, y);
        first = false;
      } else b.lineTo(x, y);
    }
  }
  b.close();
  return b.build();
};

const fourStar = (cx: number, cy: number, s: number) => {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const w = s * 0.28;
  b.moveTo(cx, cy - s);
  b.quadTo(cx + w * 0.35, cy - w * 0.35, cx + s, cy);
  b.quadTo(cx + w * 0.35, cy + w * 0.35, cx, cy + s);
  b.quadTo(cx - w * 0.35, cy + w * 0.35, cx - s, cy);
  b.quadTo(cx - w * 0.35, cy - w * 0.35, cx, cy - s);
  b.close();
  return b.build();
};

const drawRays = (c: SkCanvas, rays: SkRuntimeEffect, s: Scene) => {
  'worklet';
  if (s.sceneA <= 0.01) return;
  const p = Skia.Paint();
  p.setShader(rays.makeShader([s.bx, s.by, s.spin * 0.5, s.sceneA, s.tiltX, s.tiltY, s.waveR, s.waveA]));
  c.drawRect(Skia.XYWHRect(0, 0, s.w, s.h), p);
};

const drawSparkles = (c: SkCanvas, s: Scene) => {
  'worklet';
  if (s.sceneA <= 0.01 || s.sparkA <= 0.01) return;
  for (let i = 0; i < SPARKLES.length; i++) {
    const [x, y, size, kind, tone] = SPARKLES[i];
    // pop in one after another, then breathe (whole cycles per loop)
    const pop = easeOut(seg(s.sparkA, i * 0.04, i * 0.04 + 0.45));
    const breathe = 1;
    const k = pop * breathe * s.sceneA;
    if (k <= 0.01) continue;
    // parallax: the sparkles drift against the tilt
    const dist = Math.max(1, Math.hypot(x, y));
    const push = s.waveA * 16 * Math.exp(-Math.pow((s.waveR - dist) / 36, 2));
    const px = s.bx + x * (1 + push / dist) - s.tiltX * (4 + size);
    const py = s.by + y * (1 + push / dist) - s.tiltY * (4 + size);
    const col = tone === 0 ? C.starPurple : C.starLight;
    if (kind === 1) c.drawPath(fourStar(px, py, size * (0.6 + 0.4 * pop) * breathe), paint(col, Math.min(1, k * 1.2)));
    else c.drawCircle(px, py, size * pop, paint(tone === 0 ? C.starPurple : C.dot, Math.min(1, k * 1.1)));
  }
};

/** A rounded block of the chest with straps: fill, darker straps, a highlight, an outline. */
const block = (c: SkCanvas, x: number, y: number, w: number, h: number, rt: number, rb: number, top: string, bot: string) => {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.addRRect({
    rect: Skia.XYWHRect(x, y, w, h),
    topLeft: { x: rt, y: rt },
    topRight: { x: rt, y: rt },
    bottomRight: { x: rb, y: rb },
    bottomLeft: { x: rb, y: rb },
  });
  const path = b.build();
  c.drawPath(path, stroke(C.chestLine, 2.6));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(linear(x, y, x + w * 0.25, y + h, [top, bot], [0, 1]));
  c.drawPath(path, p);
  c.save();
  c.clipPath(path, 1, true);
  // straps
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setShader(linear(0, y, 0, y + h, ['#9a6ff0', C.gap], [0, 1]));
  c.drawRect(Skia.XYWHRect(-12.5, y, 6, h), sp);
  c.drawRect(Skia.XYWHRect(6.5, y, 6, h), sp);
  c.drawRect(Skia.XYWHRect(-12.5, y, 1.2, h), paint(C.chestLine, 0.35));
  c.drawRect(Skia.XYWHRect(6.5, y, 1.2, h), paint(C.chestLine, 0.35));
  // puff: a light band down each plank's left, shade on the right edge of the block
  c.drawRect(Skia.XYWHRect(x + 3, y + 2, 3.5, h - 4), paint('#ffffff', 0.4));
  c.drawRect(Skia.XYWHRect(-4.5, y + 2, 2.5, h - 4), paint('#ffffff', 0.3));
  c.drawRect(Skia.XYWHRect(15, y + 2, 2.5, h - 4), paint('#ffffff', 0.25));
  c.drawRect(Skia.XYWHRect(x + w - 4, y, 4, h), paint(C.gap, 0.3));
  c.restore();
};

/** The chest, centred on (0, 0) in its own 64 × 58 box: a domed lid, a body, a band, a lock. */
const drawChest = (c: SkCanvas, ch: Chest, tiltX: number) => {
  'worklet';
  const sq = ch.gather * 0.07 + ch.bump * 0.05;
  const shake = ch.gather > 0.5 ? Math.sin(ch.gather * 40) * 1.6 * (ch.gather - 0.5) : 0;
  c.save();
  c.translate(shake, 23);
  c.scale(1 + sq * 0.8, 1 - sq);
  c.translate(0, -23);
  block(c, -27, 4, 54, 19, 3, 7, C.bodyTop, C.bodyBot);
  // light inside the open chest
  if (ch.light > 0.01) {
    const gl = paint('#ffffff', ch.light);
    gl.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    c.drawOval(Skia.XYWHRect(-30, -10, 60, 26), gl);
    c.drawOval(Skia.XYWHRect(-22, -1, 44, 11), paint('#ffffff', ch.light));
    const halo = paint('#f2e6ff', 0.55 * ch.light);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
    c.drawCircle(0, 0, 30, halo);
  }
  // the lid hinges at its back right: it lifts, tips back and leans
  c.save();
  const lift = ch.lid;
  c.translate(mix(0, -6, lift), mix(0, -15, lift));
  c.rotate(mix(0, -24, lift) + tiltX * 2, 0, 6);
  block(c, -31, -25, 62, 28, 15, 4, C.lidTop, C.lidBot);
  // band and lock
  const band = Skia.RRectXY(Skia.XYWHRect(-32, -1, 64, 11), 5, 5);
  c.drawRRect(band, stroke(C.chestLine, 2.6));
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(linear(0, -1, 0, 10, ['#f1e9ff', C.band, '#bfa4f6'], [0, 0.5, 1]));
  c.drawRRect(band, bp);
  c.save();
  c.translate(0, 4.5);
  c.rotate(45, 0, 0);
  const lock = Skia.RRectXY(Skia.XYWHRect(-10, -10, 20, 20), 6, 6);
  c.drawRRect(lock, stroke(C.chestLine, 2.8));
  c.drawRRect(lock, paint(C.lock));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-8, -8, 16, 16), 5, 5), stroke('#d5c3fb', 1.2));
  c.restore();
  c.drawPath(fourStar(0, 4.5, 6.4), paint(C.lockStar));
  c.restore();
  c.restore();
};

const drawBurst = (c: SkCanvas, s: Scene) => {
  'worklet';
  const o = s.chest.since;
  if (o < 0 || o > 1.6) return;
  // bloom (drawn behind the badge by drawBloom)
  const bloom = 0;
  if (bloom > 0.01) {
    const g = Skia.Paint();
    g.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: s.bx, y: s.by },
        165,
        [Skia.Color('rgba(225,200,255,0.95)'), Skia.Color('rgba(160,105,245,0.6)'), Skia.Color('rgba(120,70,220,0)')],
        [0, 0.5, 1],
        0,
      ),
    );
    g.setAlphaf(bloom);
    c.drawCircle(s.bx, s.by, 165, g);
  }
  // a thin ring travelling out
  const rw = seg(o, 0.08, 0.6);
  if (rw > 0 && rw < 1) c.drawCircle(s.bx, s.by, 92 + rw * 80, stroke(C.starLight, 1.4, 0.45 * (1 - rw)));
  // tiny sparkles rising out of the open chest, inside the recess
  for (let i = 0; i < 12; i++) {
    const st = 0.12 + hash(i * 3.1) * 0.45;
    const k = (o - st) / 0.6;
    if (k <= 0 || k >= 1) continue;
    const x = s.bx + (hash(i * 7.3) - 0.5) * 46 + Math.sin(k * 6 + i) * 2;
    const y = s.by - 4 - k * (26 + hash(i * 1.9) * 22);
    const al = Math.sin(k * Math.PI) * 0.95;
    if (i % 3 === 0) c.drawPath(fourStar(x, y, 2.4), paint('#ffffff', al));
    else c.drawCircle(x, y, 0.8 + hash(i * 5.5) * 0.7, paint(i % 2 ? C.starLight : '#ffffff', al));
  }
};

/** The thin ring the reveal starts with (it fades as the hexagon arrives over it). */
const drawSeedRing = (c: SkCanvas, s: Scene) => {
  'worklet';
  if (s.ring <= 0.01) return;
  const g = stroke(C.bloom, 4, 0.35 * s.ring);
  g.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawCircle(s.bx, s.by, BADGE.seed, g);
  c.drawCircle(s.bx, s.by, BADGE.seed, stroke('#b9a2e8', 1.5, 0.9 * s.ring));
};

const drawBadge = (c: SkCanvas, s: Scene) => {
  'worklet';
  if (s.badgeA <= 0.005) return;
  const Ro = BADGE.outerW / Math.sqrt(3);
  const Ri = BADGE.innerW / Math.sqrt(3);
  c.save();
  c.translate(s.bx, s.by);
  // tilt: a little perspective, plus the press
  const sc = s.badgeS * (1 - s.press * 0.04);
  c.scale(sc, sc);
  // skew-based fake 3D tilt (cheap, keeps the hexagon crisp)
  c.skew(s.tiltX * 0.05, s.tiltY * 0.05);
  const layer = paint('#000000', s.badgeA);
  c.saveLayer(layer);
  const k = smooth(s.morph);
  if (k > 0.001) {
    const grow = mix(BADGE.seed / Ro, 1, easeOut(k));
    c.save();
    c.scale(grow, grow);
    c.saveLayer(paint('#000000', 0.45 + 0.55 * smooth(seg(k, 0.2, 0.9))));
    // drop glow
    const glow = paint(C.glow, 0.35 + 0.5 * s.charge);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 20 + 12 * s.charge, true));
    const outer = hexPath(Ro, k, BADGE.outerCorner);
    c.drawPath(outer, glow);
    // outer rim
    const op = Skia.Paint();
    op.setAntiAlias(true);
    op.setShader(linear(0, -Ro, 0, Ro, [C.rimHi, C.outerTop, C.outerMid, C.outerBot], [0, 0.12, 0.55, 1]));
    c.drawPath(outer, op);
    // holographic film on the rim: a hue sweep that turns with the tilt (and once per loop)
    const holo = Skia.Paint();
    holo.setAntiAlias(true);
    const turn = (s.t / LOOP) * 360 + s.tiltX * 120 + s.tiltY * 60;
    const hm = Skia.Matrix();
    hm.rotate((turn * Math.PI) / 180);
    holo.setShader(
      Skia.Shader.MakeSweepGradient(
        0,
        0,
        ['#ffb3f0', '#b9a2ff', '#8fe8ff', '#c8ffd9', '#ffd7a8', '#ffb3f0'].map((h) => Skia.Color(h)),
        null,
        0,
        hm,
      ),
    );
    holo.setAlphaf(0.45 * Math.min(1, Math.abs(s.tiltX) + Math.abs(s.tiltY)) + 0.3 * s.charge);
    c.drawPath(outer, holo);
    // face (smaller, so the rim reads as a bevel)
    const face = hexPath(Ro - 9, k, BADGE.outerCorner - 4);
    const fp = Skia.Paint();
    fp.setAntiAlias(true);
    fp.setShader(linear(-Ro * 0.4, -Ro, Ro * 0.4, Ro, [C.faceTop, C.faceBot], [0, 1]));
    c.drawPath(face, fp);
    // light sweep across the face, steered by the tilt
    const sw = s.sweep;
    if (sw >= 0 || Math.abs(s.tiltX) + Math.abs(s.tiltY) > 0.02) {
      const pos = sw >= 0 ? mix(-1.4, 1.4, sw) : s.tiltX * 0.9;
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      const strength = sw >= 0 ? Math.sin(sw * Math.PI) * 0.55 : Math.min(0.4, (Math.abs(s.tiltX) + Math.abs(s.tiltY)) * 0.4);
      sp.setShader(
        linear(
          (pos - 0.35) * Ro,
          -Ro,
          (pos + 0.35) * Ro,
          Ro,
          ['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)'],
          [0, 0.5, 1],
        ),
      );
      sp.setAlphaf(strength);
      c.drawPath(face, sp);
    }
    // the recess
    const inner = hexPath(Ri, k, BADGE.innerCorner);
    const ip = Skia.Paint();
    ip.setAntiAlias(true);
    ip.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: 0, y: 6 },
        Ri * 1.05,
        [Skia.Color(C.innerCenter), Skia.Color(C.innerEdge)],
        [0, 1],
        0,
      ),
    );
    c.drawPath(inner, ip);
    // inner shadow at the top, a lit lip at the bottom
    c.save();
    const clip = hexPath(Ri, k, BADGE.innerCorner);
    c.clipPath(clip, 1, true);
    const ish = Skia.Paint();
    ish.setAntiAlias(true);
    ish.setStyle(1);
    ish.setStrokeWidth(9);
    ish.setShader(linear(0, -Ri, 0, Ri, ['rgba(30,12,80,0.85)', 'rgba(30,12,80,0.15)', 'rgba(170,140,240,0.55)'], [0, 0.55, 1]));
    c.drawPath(inner, ish);
    // a purple glow in the recess, swelling with the light of the chest
    const ig = paint(C.bloom, 0.18 + 0.5 * s.chest.light);
    ig.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
    c.drawCircle(0, 4, 26, ig);
    c.restore();
    // the chest
    const ca = smooth(seg(k, 0.4, 1));
    if (ca > 0.01) {
      c.save();
      c.translate(s.tiltX * 3, 2 + s.tiltY * 3);
      const cs = mix(0.7, 1, ca) * 0.94;
      c.scale(cs, cs);
      drawChest(c, s.chest, s.tiltX);
      c.restore();
    }
    c.restore();
    c.restore();
  }
  c.restore();
  c.restore();
};

const drawBloom = (c: SkCanvas, s: Scene) => {
  'worklet';
  const o = s.chest.since;
  if (o < 0 || o > 1.2) return;
  const bloom = smooth(o / 0.06) * (1 - smooth(seg(o, 0.35, 0.8)));
  if (bloom <= 0.01) return;
  const R0 = 150 * (0.85 + 0.15 * s.power);
  const g = Skia.Paint();
  g.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: s.bx, y: s.by },
      R0,
      [Skia.Color('rgba(235,215,255,1)'), Skia.Color('rgba(175,120,250,0.82)'), Skia.Color('rgba(120,70,220,0)')],
      [0, 0.5, 1],
      0,
    ),
  );
  g.setAlphaf(bloom);
  c.drawCircle(s.bx, s.by, R0, g);
};

/** The badge's dust: it peels off as the screen leaves, swirls, and gathers into the seed ring. */
const drawDust = (c: SkCanvas, s: Scene) => {
  'worklet';
  if (s.dust < 0 || s.dust > 1) return;
  const k = s.dust;
  const Ro = BADGE.outerW / Math.sqrt(3);
  for (let i = 0; i < 110; i++) {
    const a0 = hash(i * 1.37) * Math.PI * 2;
    const r0 = Math.sqrt(hash(i * 2.71)) * Ro * 0.92;
    const x0 = Math.cos(a0) * r0;
    const y0 = Math.sin(a0) * r0;
    const r1 = 120 + hash(i * 5.3) * 110;
    const a1 = a0 + 0.9 + hash(i * 7.9) * 0.6;
    const x1 = Math.cos(a1) * r1;
    const y1 = Math.sin(a1) * r1 * 0.85 - 40;
    const a2 = (i / 110) * Math.PI * 2;
    const x2 = Math.cos(a2) * BADGE.seed;
    const y2 = Math.sin(a2) * BADGE.seed;
    // out (0 → 0.48), then in along a curl (0.48 → 1)
    let x = 0;
    let y = 0;
    if (k < 0.48) {
      const e = 1 - Math.pow(1 - k / 0.48, 3);
      x = mix(x0, x1, e);
      y = mix(y0, y1, e);
    } else {
      const u = (k - 0.48) / 0.52;
      const e = u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
      const curl = Math.sin(e * Math.PI) * 30;
      x = mix(x1, x2, e) + Math.cos(a1 + Math.PI / 2) * curl;
      y = mix(y1, y2, e) + Math.sin(a1 + Math.PI / 2) * curl;
    }
    const al = smooth(k / 0.06) * (1 - smooth((k - 0.93) / 0.07));
    const size = 1 + hash(i * 3.3) * 1.6;
    const col = i % 4 === 0 ? '#ffffff' : i % 4 === 1 ? C.bloom : i % 4 === 2 ? C.outerTop : C.starLight;
    c.drawCircle(s.bx + x, s.by + y, size, paint(col, al * (0.6 + 0.4 * hash(i))));
  }
};



export const drawScene = (c: SkCanvas, rays: SkRuntimeEffect, s: Scene) => {
  'worklet';
  c.drawColor(Skia.Color(C.page));
  drawRays(c, rays, s);
  // aura behind the badge
  const aura = Skia.Paint();
  aura.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: s.bx, y: s.by },
      230,
      [Skia.Color('rgba(58,32,110,0.5)'), Skia.Color('rgba(34,18,66,0.2)'), Skia.Color('rgba(20,10,40,0)')],
      [0, 0.4, 1],
      0,
    ),
  );
  aura.setAlphaf(clamp01(s.sceneA));
  c.drawCircle(s.bx, s.by, 230, aura);
  drawSparkles(c, s);
  drawBloom(c, s);
  drawSeedRing(c, s);
  drawBadge(c, s);
  drawDust(c, s);
  drawBurst(c, s);
};
