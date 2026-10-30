// Setup Checklist All Set — drawing, every frame, in the 402 × 874 reference space.

import { ClipOp, PaintStyle, type SkCanvas, type SkFont, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { clamp01, countRolled, D, doneness, easeInOut, easeOut, mix, N, NEVER, openness, smooth, spring, type State } from './sca-motion';
import { C, COPY, ITEMS, REF_H, UI } from './sca-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface; heavy: SkTypeface };
export type Res = {
  f: {
    handle: SkFont;
    url: SkFont;
    chip: SkFont;
    pro: SkFont;
    title: SkFont;
    badge: SkFont;
    row: SkFont;
    desc: SkFont;
    action: SkFont;
    allSet: SkFont;
    body: SkFont;
    cta: SkFont;
    tab: SkFont;
    small: SkFont;
    ring: SkFont;
  };
};

export function makeRes(f: Faces): Res {
  return {
    f: {
      handle: Skia.Font(f.heavy, 33),
      url: Skia.Font(f.regular, 16.5),
      chip: Skia.Font(f.medium, 15),
      pro: Skia.Font(f.semi, 15),
      title: Skia.Font(f.medium, 15.5),
      badge: Skia.Font(f.semi, 13.5),
      row: Skia.Font(f.medium, 14.8),
      desc: Skia.Font(f.regular, 14.6),
      action: Skia.Font(f.medium, 14.4),
      allSet: Skia.Font(f.bold, 21),
      body: Skia.Font(f.regular, 15.5),
      cta: Skia.Font(f.semi, 16),
      tab: Skia.Font(f.medium, 10.5),
      small: Skia.Font(f.medium, 12.5),
      ring: Skia.Font(f.semi, 12),
    },
  };
}

export type Geo = { w: number; h: number; s: number };
export type Live = { now: number; amb: number; s: State };

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
/** Text squeezed (never stretched) to fit `maxW`. */
function textFit(c: SkCanvas, s: string, x: number, cy: number, font: SkFont, p: ReturnType<typeof Skia.Paint>, maxW: number, ax = 0) {
  'worklet';
  const w = font.measureText(s).width;
  const k = Math.min(1, maxW / w);
  c.save();
  c.translate(x, cy);
  c.scale(k, k);
  text(c, s, 0, 0, font, p, ax);
  c.restore();
  return w * k;
}

// ------------------------------------------------------------------ the page behind

function drawPage(c: SkCanvas, r: Res) {
  'worklet';
  const P = UI.page;
  c.drawColor(col(C.page));
  // avatar
  c.drawCircle(P.avatar.x, P.avatar.y, P.avatar.r, paint('#C7C7C3'));
  c.save();
  c.clipRRect(rrect(P.avatar.x - P.avatar.r, P.avatar.y - P.avatar.r, P.avatar.r * 2, P.avatar.r * 2, P.avatar.r), ClipOp.Intersect, true);
  c.drawCircle(P.avatar.x, P.avatar.y - 4, 8, paint('#EDEDEA'));
  c.drawOval(Skia.XYWHRect(P.avatar.x - 15, P.avatar.y + 8, 30, 22), paint('#EDEDEA'));
  c.restore();
  const pr = P.pro;
  c.drawRRect(rrect(pr.x, pr.y, pr.w, pr.h, 12), stroke(C.pro, 1.5));
  text(c, COPY.pro, pr.x + pr.w / 2, pr.y + pr.h / 2, r.f.pro, paint(C.pro), 0.5);
  // share and bell
  const sh = stroke(C.ink, 1.8);
  c.drawLine(P.share.x, P.share.y - 12, P.share.x, P.share.y + 3, sh);
  c.drawLine(P.share.x - 5, P.share.y - 7, P.share.x, P.share.y - 12, sh);
  c.drawLine(P.share.x + 5, P.share.y - 7, P.share.x, P.share.y - 12, sh);
  const box = Skia.Path.Make();
  box.moveTo(P.share.x - 5, P.share.y - 4);
  box.lineTo(P.share.x - 10, P.share.y - 4);
  box.lineTo(P.share.x - 10, P.share.y + 12);
  box.lineTo(P.share.x + 10, P.share.y + 12);
  box.lineTo(P.share.x + 10, P.share.y - 4);
  box.lineTo(P.share.x + 5, P.share.y - 4);
  c.drawPath(box, sh);
  const bell = Skia.Path.Make();
  bell.moveTo(P.bell.x - 10, P.bell.y + 7);
  bell.lineTo(P.bell.x - 8, P.bell.y + 3);
  bell.cubicTo(P.bell.x - 8, P.bell.y - 13, P.bell.x + 8, P.bell.y - 13, P.bell.x + 8, P.bell.y + 3);
  bell.lineTo(P.bell.x + 10, P.bell.y + 7);
  bell.close();
  c.drawPath(bell, sh);
  c.drawLine(P.bell.x - 3, P.bell.y + 11, P.bell.x + 3, P.bell.y + 11, sh);
  text(c, COPY.handle, P.handle.x, P.handle.y, r.f.handle, paint(C.ink));
  const uw = text(c, COPY.url, P.url.x, P.url.y, r.f.url, paint(C.ink2));
  c.drawRRect(rrect(P.url.x + uw + 6, P.url.y - 5, 8, 9, 1.5), stroke(C.ink2, 1.2));
  c.drawRRect(rrect(P.url.x + uw + 9, P.url.y - 8, 8, 9, 1.5), stroke(C.ink2, 1.2));
  for (let i = 0; i < 3; i++) {
    const x = P.chips.x[i];
    const w = P.chips.w[i];
    c.drawRRect(rrect(x, P.chips.y - P.chips.h / 2, w, P.chips.h, P.chips.h / 2), paint(C.chip));
    const ic = stroke(C.ink, 1.4);
    const ix = x + 22;
    const iy = P.chips.y;
    const g = Skia.Path.Make();
    if (i === 0) {
      // a T-shirt
      g.moveTo(ix - 4, iy - 7);
      g.quadTo(ix, iy - 4, ix + 4, iy - 7);
      g.lineTo(ix + 9, iy - 4);
      g.lineTo(ix + 7, iy);
      g.lineTo(ix + 5, iy - 1);
      g.lineTo(ix + 5, iy + 7);
      g.lineTo(ix - 5, iy + 7);
      g.lineTo(ix - 5, iy - 1);
      g.lineTo(ix - 7, iy);
      g.lineTo(ix - 9, iy - 4);
      g.close();
    } else if (i === 1) {
      // a paint roller
      g.addRRect(rrect(ix - 8, iy - 7, 16, 7, 2));
      g.moveTo(ix + 8, iy - 4);
      g.lineTo(ix + 10, iy - 4);
      g.lineTo(ix + 10, iy + 1);
      g.lineTo(ix, iy + 2);
      g.lineTo(ix, iy + 8);
    } else {
      // a gear
      for (let k = 0; k < 8; k++) {
        const a0 = (k * TAU) / 8;
        g.moveTo(ix + Math.cos(a0) * 5.5, iy + Math.sin(a0) * 5.5);
        g.lineTo(ix + Math.cos(a0) * 8, iy + Math.sin(a0) * 8);
      }
      g.addCircle(ix, iy, 5.5);
      g.addCircle(ix, iy, 2.2);
    }
    c.drawPath(g, ic);
    text(c, COPY.chips[i], x + 37, P.chips.y, r.f.chip, paint(C.ink));
  }
  // the cards, mostly under the sheet
  const cy = P.cards.y;
  c.drawRRect(rrect(17, cy, 169, P.cards.h, 14), paint('#E8E8E5'));
  c.drawRRect(rrect(17, cy, 169, P.cards.h, 14), stroke('#D2D2CE', 1));
  c.drawCircle(101, cy + 64, 20, paint('#B9B9B5'));
  text(c, '@' + COPY.handle, 101, cy + 104, r.f.small, paint(C.ink), 0.5);
  c.drawRRect(rrect(54, cy + 146, 94, 22, 11), paint('#FFFFFF'));
  c.drawRRect(rrect(196, cy, 190, P.cards.h, 14), paint('#E1E1DE'));
  c.drawCircle(291, cy + 72, 34, paint('#C48BF0'));
  c.drawCircle(291, cy + 72, 18, paint('#FFFFFF', 0.85));
  text(c, 'Set up your Shop', 291, cy + 132, r.f.chip, paint(C.ink), 0.5);
  text(c, 'Links', 17, cy + P.cards.h + 18, r.f.chip, paint(C.ink));
  text(c, 'Shop', 196, cy + P.cards.h + 18, r.f.chip, paint(C.ink));
  c.drawRRect(rrect(330, cy + P.cards.h + 6, 56, 26, 13), paint(C.chip));
  text(c, 'Set up', 358, cy + P.cards.h + 19, r.f.small, paint(C.ink), 0.5);
  // the setup ring
  const rg = P.ring;
  c.drawCircle(rg.x, rg.y, rg.r + 9, paint('#FFFFFF'));
  c.drawCircle(rg.x, rg.y, rg.r + 9, stroke('#C9B6F7', 2));
  c.drawCircle(rg.x, rg.y, rg.r, stroke('#EDE7FB', 5));
  const arc = Skia.Path.Make();
  arc.addArc(Skia.XYWHRect(rg.x - rg.r, rg.y - rg.r, rg.r * 2, rg.r * 2), -90, 360 / 6);
  c.drawPath(arc, stroke(C.barFrom, 5));
  text(c, COPY.ring[0], rg.x, rg.y - 7, r.f.ring, paint(C.ink), 0.5);
  text(c, COPY.ring[1], rg.x, rg.y + 8, r.f.ring, paint(C.ink), 0.5);
  // tab bar
  c.drawRect(Skia.XYWHRect(0, 784, 402, 90), paint('#FFFFFF'));
  c.drawLine(0, 784, 402, 784, stroke('#E3E3E0', 1));
  const tabs = ['My Tapline', 'Earn', 'Insights', 'Audience', 'More'];
  for (let i = 0; i < 5; i++) {
    const x = 40 + i * 80.5;
    const on = i === 0;
    c.drawRRect(rrect(x - 10, 798, 20, 18, 5), stroke(on ? C.badge : C.grey, 1.6));
    text(c, tabs[i], x, 832, r.f.tab, paint(on ? C.badge : C.grey), 0.5);
  }
}

// ------------------------------------------------------------------ the checklist

function drawCheck(c: SkCanvas, x: number, y: number, rad: number, p: number) {
  'worklet';
  // the empty ring
  c.drawCircle(x, y, rad, stroke(C.ring, 1.6, 1 - smooth(p * 3)));
  if (p <= 0) return;
  // a soft green ring pulses out as it is ticked
  if (p > 0.15 && p < 0.9) {
    const q = (p - 0.15) / 0.75;
    c.drawCircle(x, y, rad * (1 + 0.9 * easeOut(q)), stroke(C.green, 2 * (1 - q), 0.5 * (1 - q)));
  }
  const fill = spring(p * D.tick, 0.32, 0.55);
  const s = mix(0.4, 1, fill);
  c.drawCircle(x, y, rad * s, paint(C.green, Math.min(1, p * 4)));
  // the tick, drawn
  const k = clamp01((p - 0.25) / 0.45);
  if (k > 0) {
    const a = { x: x - rad * 0.42, y: y + rad * 0.02 };
    const b = { x: x - rad * 0.1, y: y + rad * 0.34 };
    const e = { x: x + rad * 0.46, y: y - rad * 0.3 };
    const l1 = Math.hypot(b.x - a.x, b.y - a.y);
    const l2 = Math.hypot(e.x - b.x, e.y - b.y);
    const d = easeOut(k) * (l1 + l2);
    const tk = Skia.Path.Make();
    tk.moveTo(a.x, a.y);
    if (d <= l1) tk.lineTo(mix(a.x, b.x, d / l1), mix(a.y, b.y, d / l1));
    else {
      tk.lineTo(b.x, b.y);
      const q = (d - l1) / l2;
      tk.lineTo(mix(b.x, e.x, q), mix(b.y, e.y, q));
    }
    c.drawPath(tk, stroke('#FFFFFF', 2.3));
  }
}

function chevron(c: SkCanvas, x: number, y: number, up: number, a: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(180 * up, 0, 0);
  const p = Skia.Path.Make();
  p.moveTo(-6.5, -3);
  p.lineTo(0, 3.5);
  p.lineTo(6.5, -3);
  c.drawPath(p, stroke(C.ink, 1.5, a));
  c.restore();
}

function drawChecklist(c: SkCanvas, r: Res, L: Live, fade: number) {
  'worklet';
  const s = L.s;
  const now = L.now;
  const a = 1 - fade;
  const count = countRolled(s, now);
  // header
  const tw = text(c, COPY.title, UI.title.x, UI.title.y, r.f.title, paint(C.ink, a));
  const B = UI.badge;
  const bx = UI.title.x + tw + 8;
  // the badge pops as its number changes
  let changed = NEVER;
  for (let i = 0; i < N; i++) if (s.doneAt[i] > changed) changed = s.doneAt[i];
  const pq = clamp01((now - changed - 0.06) / 0.32);
  const pop = changed > NEVER && pq > 0 && pq < 1 ? Math.sin(Math.PI * pq) * (1 - pq * 0.4) : 0;
  c.save();
  c.translate(bx + B.w / 2, B.y + B.h / 2);
  c.scale(1 + 0.15 * pop, 1 + 0.15 * pop);
  c.translate(-(bx + B.w / 2), -(B.y + B.h / 2));
  c.drawRRect(rrect(bx, B.y, B.w, B.h, B.h / 2), paint(C.badge, a));
  // the count rolls
  const n0 = Math.floor(count + 1e-4);
  const k = smooth(count - n0);
  c.save();
  c.clipRect(Skia.XYWHRect(bx, B.y, B.w, B.h), ClipOp.Intersect, true);
  const wp = paint('#FFFFFF', a);
  const slash = `/${N}`;
  const sw = r.f.badge.measureText(slash).width;
  const dw = r.f.badge.measureText(String(n0)).width;
  const x0 = bx + B.w / 2 - (dw + sw) / 2;
  text(c, String(n0), x0, B.y + B.h / 2 - k * 14, r.f.badge, wp);
  if (k > 0.001) text(c, String(Math.min(N, n0 + 1)), x0, B.y + B.h / 2 + (1 - k) * 14, r.f.badge, wp);
  text(c, slash, x0 + dw, B.y + B.h / 2, r.f.badge, wp);
  c.restore();
  c.restore();
  // close
  const X = UI.close;
  c.drawCircle(X.x, X.y, X.r, paint(C.close, a));
  c.drawLine(X.x - 5, X.y - 5, X.x + 5, X.y + 5, stroke(C.ink, 1.7, a));
  c.drawLine(X.x + 5, X.y - 5, X.x - 5, X.y + 5, stroke(C.ink, 1.7, a));
  // the bar: its fill follows the count on a spring; a gloss runs along it after each change
  const Br = UI.bar;
  c.drawRRect(rrect(Br.x, Br.y, Br.w, Br.h, Br.h / 2), paint(C.track, a));
  let last = NEVER;
  for (let i = 0; i < N; i++) if (s.doneAt[i] > last) last = s.doneAt[i];
  const fw = Math.max(Br.h, (Br.w * count) / N);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setAlphaf(a);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: Br.x, y: 0 }, { x: Br.x + fw, y: 0 }, [col(C.barFrom), col(C.barTo)], null, TileMode.Clamp));
  c.drawRRect(rrect(Br.x, Br.y, fw, Br.h, Br.h / 2), bp);
  const gl = now - last;
  if (last > NEVER && gl > 0.1 && gl < 0.9) {
    const gx = Br.x + fw * easeInOut((gl - 0.1) / 0.8);
    c.save();
    c.clipRRect(rrect(Br.x, Br.y, fw, Br.h, Br.h / 2), ClipOp.Intersect, true);
    const gp = Skia.Paint();
    gp.setShader(Skia.Shader.MakeLinearGradient({ x: gx - 40, y: 0 }, { x: gx + 10, y: 0 }, [col('#FFFFFF', 0), col('#FFFFFF', 0.55 * a), col('#FFFFFF', 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(gx - 40, Br.y, 50, Br.h), gp);
    c.restore();
  }
  // rows
  const R = UI.rows;
  let y = R.y0;
  for (let i = 0; i < N; i++) {
    const e = openness(s, i, now);
    const h = R.h + R.open * e;
    const cy = y + R.h / 2;
    const dn = doneness(s, i, now);
    drawCheck(c, R.check.x, cy, R.check.r, dn);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    const strikeK = clamp01((dn - 0.45) / 0.55);
    ink.setColor(mixCol(C.ink, C.mute, smooth(dn * 1.4), a));
    const tw2 = text(c, ITEMS[i].title, R.textX, cy, r.f.row, ink);
    if (strikeK > 0) c.drawLine(R.textX, cy + 1, R.textX + tw2 * easeOut(strikeK), cy + 1, stroke(C.mute, 1.4, a));
    // done rows lose their chevron
    chevron(c, R.chevronX, cy, e, a * (1 - smooth(dn * 2)));
    if (e > 0.005) {
      c.save();
      c.clipRect(Skia.XYWHRect(0, y + R.h - 6, 402, h - R.h + 6), ClipOp.Intersect, true);
      const ca = a * smooth((e - 0.15) / 0.85);
      const lift = (1 - e) * -12;
      for (let l = 0; l < 2; l++) textFit(c, ITEMS[i].body[l], R.textX, cy + UI.desc.dy[l] + lift, r.f.desc, paint(C.ink2, ca), 332);
      const A = UI.action;
      const aw = r.f.action.measureText(ITEMS[i].action).width + A.padX * 2;
      const press = s.pressRow === i ? Math.max(0, 1 - Math.abs(now - s.pressAt - 0.08) / 0.12) : 0;
      const ps = 1 - 0.04 * press;
      c.save();
      c.translate(R.textX + aw / 2, cy + A.dy + lift);
      c.scale(ps, ps);
      c.drawRRect(rrect(-aw / 2, -A.h / 2, aw, A.h, 12), paint(C.purple, ca));
      text(c, ITEMS[i].action, 0, 0, r.f.action, paint('#FFFFFF', ca), 0.5);
      c.restore();
      text(c, COPY.skip, R.textX + aw + UI.skipDx, cy + A.dy + lift, r.f.action, paint(C.ink, ca));
      c.restore();
    }
    if (i < N - 1) c.drawLine(16.5, y + h, 385.5, y + h, stroke(C.divider, 1, a));
    y += h;
  }
}

// ------------------------------------------------------------------ all set

function drawPopper(c: SkCanvas, x: number, y: number, k: number, amb: number) {
  'worklet';
  if (k <= 0) return;
  c.save();
  c.translate(x, y);
  c.scale(k, k);
  // the cone, pointing down-left
  const cone = Skia.Path.Make();
  cone.moveTo(-15, 15);
  cone.lineTo(-3, -11);
  cone.lineTo(11, 3);
  cone.close();
  c.drawPath(cone, paint(C.popper));
  c.drawLine(-9, 2, 1, 12, stroke('#FFFFFF', 1.6, 0.8));
  // sparkles above it, twinkling
  const tw = (i: number) => 1 + 0.18 * Math.sin((TAU * 2 * amb) / 9 + i * 2);
  const plus = (px: number, py: number, rr: number) => {
    c.drawLine(px - rr, py, px + rr, py, stroke(C.popper, 1.8));
    c.drawLine(px, py - rr, px, py + rr, stroke(C.popper, 1.8));
  };
  plus(1, -17, 3.6 * tw(0));
  plus(15, -13, 4.4 * tw(1));
  plus(18, 4, 3.4 * tw(2));
  c.drawCircle(9, -3, 1.8, paint(C.popper));
  c.drawCircle(-6, -20, 1.5, paint(C.popper));
  c.restore();
}

/** Confetti from the popper: a fixed scatter, `tau` seconds after it fired. */
function drawConfetti(c: SkCanvas, x: number, y: number, tau: number, seed: number) {
  'worklet';
  if (tau < 0 || tau > 2.2) return;
  for (let i = 0; i < 46; i++) {
    const h1 = Math.sin((i + 1) * 12.9898 + seed * 78.233) * 43758.5453;
    const r1 = h1 - Math.floor(h1);
    const h2 = Math.sin((i + 1) * 39.3468 + seed * 11.135) * 24634.6345;
    const r2 = h2 - Math.floor(h2);
    const h3 = Math.sin((i + 1) * 73.156 + seed * 52.235) * 12345.6789;
    const r3 = h3 - Math.floor(h3);
    const ang = -Math.PI / 2 + (r1 - 0.45) * 2.6;
    const v = 260 + 260 * r2;
    const drag = 1 - Math.exp(-tau * 2.6);
    const px = x + (Math.cos(ang) * v * drag) / 2.6;
    const py = y + (Math.sin(ang) * v * drag) / 2.6 + 140 * tau * tau;
    const fade = 1 - smooth((tau - 1.2) / 1);
    const cc = C.confetti[i % C.confetti.length];
    c.save();
    c.translate(px, py);
    c.rotate((r3 * 360 + tau * 520 * (r1 - 0.5)) % 360, 0, 0);
    const flutter = Math.abs(Math.cos(tau * (6 + 8 * r3) + r2 * 6));
    if (i % 3 === 0) c.drawCircle(0, 0, 2.6, paint(cc, fade));
    else c.drawRect(Skia.XYWHRect(-3.5, -1.8 * flutter - 0.4, 7, 3.6 * flutter + 0.8), paint(cc, fade));
    c.restore();
  }
}

function drawAllSet(c: SkCanvas, r: Res, L: Live, a: number) {
  'worklet';
  const A = UI.allSet;
  const I = A.icon;
  const now = L.now;
  const s = L.s;
  const m0 = D.morph;
  const m1 = D.morph + D.bloom;
  const tx = (i: number) => smooth((a - m0 - 0.3 - i * 0.07) / 0.35);
  // the white sheet grows out of the bubble
  const g = a < m0 ? 0 : spring(a - m0, 0.5, 0.78);
  const top = mix(I.y - I.r, A.y, g);
  const left = mix(I.x - I.r, 0, g);
  const right = mix(I.x + I.r, 402, g);
  const bottom = mix(I.y + I.r, REF_H + 60, g);
  const rad = mix(I.r, 34, clamp01(g));
  if (a >= m0) c.drawRRect(rrect(left, top, right - left, bottom - top, rad), paint(C.sheet));
  // the bubble: during the morph it is the shrinking sheet itself (see drawScene); after, the icon
  if (a >= m0) {
    const settle = spring(a - m0, 0.38, 0.55);
    c.drawCircle(I.x, I.y, I.r * mix(1.08, 1, settle), paint(C.lilac));
    drawPopper(c, I.x, I.y, spring(a - m0 - 0.12, 0.42, 0.5), L.amb);
  }
  drawConfetti(c, I.x + 10, I.y - 14, a - m0 - 0.2, 1);
  if (s.ctaAt > NEVER) drawConfetti(c, 201, UI.allSet.cta.y, now - s.ctaAt, 2);
  if (a < m1 - 0.3) return;
  const t0 = tx(0);
  text(c, COPY.allSet, 201, A.title + 8 * (1 - t0), r.f.allSet, paint(C.ink, t0), 0.5);
  for (let l = 0; l < 3; l++) {
    const k = tx(1 + l * 0.4);
    text(c, COPY.allSetBody[l], 201, A.body[l] + 8 * (1 - k), r.f.body, paint(C.grey, k), 0.5);
  }
  const kc = tx(3);
  const cp = s.ctaAt > NEVER ? Math.max(0, 1 - Math.abs(now - s.ctaAt - 0.08) / 0.14) : 0;
  const cs = 1 - 0.04 * cp;
  const Ct = A.cta;
  c.save();
  c.translate(Ct.x + Ct.w / 2, Ct.y + Ct.h / 2 + 10 * (1 - kc));
  c.scale(cs, cs);
  c.drawRRect(rrect(-Ct.w / 2, -Ct.h / 2, Ct.w, Ct.h, Ct.h / 2), paint(C.cta, kc));
  text(c, COPY.cta, 0, 0, r.f.cta, paint('#FFFFFF', kc), 0.5);
  c.restore();
  const kl = tx(3.6);
  const lp = s.laterAt > NEVER ? Math.max(0, 1 - Math.abs(now - s.laterAt - 0.1) / 0.18) : 0;
  if (lp > 0) c.drawRRect(rrect(150, A.later - 18, 102, 36, 18), paint('#000000', 0.06 * lp));
  text(c, COPY.later, 201, A.later + 8 * (1 - kl), r.f.cta, paint(C.ink, kl), 0.5);
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, L: Live) {
  'worklet';
  const s = L.s;
  const now = L.now;
  c.save();
  c.scale(g.s, g.s);
  drawPage(c, r);
  // how far the sheet is down (dismissed) and which sheet it is
  let down = 0;
  if (s.leaveAt > NEVER && now >= s.leaveAt) {
    if (s.backAt > NEVER && now >= s.backAt) down = 1 - easeOut((now - s.backAt) / D.enter);
    else down = easeInOut((now - s.leaveAt) / D.leave);
  }
  const allSet = s.allSetAt > NEVER && now >= s.allSetAt && !(s.backAt > NEVER && now >= s.backAt);
  c.drawRect(Skia.XYWHRect(0, 0, 402, REF_H), paint(C.dim, 0.5 * (1 - down)));
  const off = down * (REF_H - UI.allSet.y + 40);
  c.save();
  c.translate(0, off);
  if (!allSet) {
    c.drawRRect(rrect(0, UI.sheet.y, 402, REF_H - UI.sheet.y + 80, UI.sheet.r), paint(C.sheet));
    drawChecklist(c, r, L, 0);
  } else {
    const a = now - s.allSetAt;
    if (a < D.morph) {
      // the list fades and shrinks first; the sheet folds into a bubble at the icon
      const I = UI.allSet.icon;
      const fade = smooth(a / 0.22);
      const m = easeInOut(a / D.morph);
      const top = mix(UI.sheet.y, I.y - I.r, m);
      const left = mix(0, I.x - I.r, m);
      const right = mix(402, I.x + I.r, m);
      const bottom = mix(REF_H + 80, I.y + I.r, m);
      const rad = mix(UI.sheet.r, I.r, m);
      const sc = Skia.Paint();
      sc.setAntiAlias(true);
      sc.setColor(mixCol(C.sheet, C.lilac, smooth((m - 0.4) / 0.6)));
      c.drawRRect(rrect(left, top, right - left, bottom - top, rad), sc);
      if (fade < 1) {
        c.save();
        c.clipRRect(rrect(left, top, right - left, bottom - top, rad), ClipOp.Intersect, true);
        c.translate(I.x, I.y);
        c.scale(1 - 0.08 * fade, 1 - 0.08 * fade);
        c.translate(-I.x, -I.y);
        drawChecklist(c, r, L, fade);
        c.restore();
      }
    } else drawAllSet(c, r, L, a);
  }
  c.restore();
  c.restore();
}
