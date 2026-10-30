// Frost Card Controls — drawing. The card is baked once (sharp, and blurred for under the ice); the
// frost is a runtime effect over it; shredding cuts the baked card into strips.

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  processTransform3d,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPicture,
  type SkRuntimeEffect,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import { type Frame, mix, smooth } from './fcc-motion';
import { C, CARD, COPY, REF_W, UI } from './fcc-theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semi: SkTypeface; bold: SkTypeface; brand: SkTypeface };

export type Res = {
  card: SkImage;
  frosted: SkImage;
  page: SkPicture;
  frost: SkRuntimeEffect;
  f: { tile: SkFont; badge: SkFont };
};

function fill(color: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function bake(w: number, h: number, draw: (c: SkCanvas) => void, blur = 0): SkImage | null {
  const k = 3;
  const s = Skia.Surface.MakeOffscreen(Math.ceil(w * k), Math.ceil(h * k)) ?? Skia.Surface.Make(Math.ceil(w * k), Math.ceil(h * k));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(k, k);
  if (blur > 0) {
    const p = Skia.Paint();
    p.setImageFilter(Skia.ImageFilter.MakeBlur(blur, blur, TileMode.Clamp, null));
    c.saveLayer(p);
    draw(c);
    c.restore();
  } else draw(c);
  s.flush();
  return s.makeImageSnapshot().makeNonTextureImage();
}

/** The card itself, in its own space: brushed metal, the brand across its top, the number, the mark. */
function drawCard(c: SkCanvas, f: Faces) {
  const w = UI.card.w;
  const h = UI.card.h;
  const rr = Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), UI.card.r, UI.card.r);
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  const metal = Skia.Paint();
  metal.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w, y: h }, [Skia.Color('#F4F4F6'), Skia.Color('#D9DADF'), Skia.Color('#EDEEF1'), Skia.Color('#C9CBD1')], [0, 0.4, 0.7, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), metal);
  // brushed grain
  const grain = Skia.Paint();
  grain.setStrokeWidth(0.5);
  for (let y = 0; y < h; y += 1.6) {
    const a = 0.03 + 0.03 * Math.sin(y * 3.7) * Math.sin(y * 0.41);
    grain.setColor(Skia.Color(`rgba(255,255,255,${Math.max(0, a).toFixed(3)})`));
    c.drawLine(0, y, w, y, grain);
  }
  const soft = Skia.Paint();
  soft.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.42, y: h * 0.55 }, w * 0.6, [Skia.Color('rgba(255,255,255,0.55)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), soft);
  // the brand runs right across the top, cropped by the card's edge
  const probe = Skia.Font(f.brand, 100);
  const bw = probe.measureText(COPY.brand).width;
  const size = (100 * (w + 6)) / bw;
  const font = Skia.Font(f.brand, size);
  const bp = Skia.Paint();
  bp.setAntiAlias(true);
  bp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w, y: 60 }, [Skia.Color(C.brandA), Skia.Color(C.brandB), Skia.Color(C.brandA)], [0, 0.55, 1], TileMode.Clamp));
  c.drawText(COPY.brand, -3, size * 0.6, bp, font); // its top runs off the card's edge, as in the pin
  // number
  c.drawText(COPY.physical, CARD.physical.x, CARD.physical.y + 5.4, fill('#8F9097'), Skia.Font(f.regular, 15));
  c.drawText(COPY.last4, CARD.last4.x, CARD.last4.y + 6.1, fill('#26272B'), Skia.Font(f.medium, 17));
  // an invented network mark: an orange ring with a red orb tucked into it
  const m = CARD.mark;
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(6);
  ring.setShader(Skia.Shader.MakeLinearGradient({ x: m.x, y: m.y - 16 }, { x: m.x + 30, y: m.y + 16 }, [Skia.Color('#FFB020'), Skia.Color('#FF6A1A')], null, TileMode.Clamp));
  c.drawCircle(m.x + 14, m.y, 13, ring);
  c.drawCircle(m.x - 2, m.y, 11, fill('#E5262B'));
  c.restore();
  // a hairline edge
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.8);
  edge.setColor(Skia.Color('rgba(0,0,0,0.08)'));
  c.drawRRect(rr, edge);
}

function drawPage(f: Faces): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, REF_W, 1100));
  const ink = fill(C.ink);
  c.drawText(COPY.balance, UI.balance.x, UI.balance.y + 9, ink, Skia.Font(f.bold, UI.balance.size));
  c.drawText(COPY.noLimit, UI.noLimit.x, UI.noLimit.y + 5.8, ink, Skia.Font(f.regular, 16));
  const sf = Skia.Font(f.semi, 16);
  c.drawText(COPY.setLimit, UI.setLimit.x - sf.measureText(COPY.setLimit).width, UI.setLimit.y + 5.8, fill(C.blue), sf);
  // hint: a little hand and the line
  const hf = Skia.Font(f.medium, 15.5);
  const hw = hf.measureText(COPY.hint).width;
  const hx = UI.hint.x - hw / 2 + 12;
  c.drawText(COPY.hint, hx, UI.hint.y + 5.6, fill('#7E7F86'), hf);
  const hand = Skia.Path.MakeFromSVGString('M2 9 L2 1.5 C2 0.4 3.6 0.4 3.6 1.5 L3.6 6 L3.6 4.6 C3.6 3.5 5.2 3.5 5.2 4.6 L5.2 6.2 L5.2 5.2 C5.2 4.1 6.8 4.1 6.8 5.2 L6.8 6.6 L6.8 6 C6.8 4.9 8.4 4.9 8.4 6 L8.4 10 C8.4 12.6 6.6 14 4.6 14 L4 14 C2.6 14 1.8 13.2 1 12 L-0.6 9.2 C-1.2 8.2 0.2 7.2 1 8.2 Z')!;
  hand.offset(hx - 18, UI.hint.y - 7);
  const hp = Skia.Paint();
  hp.setAntiAlias(true);
  hp.setStyle(PaintStyle.Stroke);
  hp.setStrokeWidth(1.2);
  hp.setStrokeJoin(StrokeJoin.Round);
  hp.setColor(Skia.Color('#7E7F86'));
  c.drawPath(hand, hp);
  // Add to Wallet
  const wb = UI.wallet;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(wb.x, wb.y, wb.w, wb.h), wb.r, wb.r), fill(C.button));
  const wf = Skia.Font(f.semi, 20);
  const ww = wf.measureText(COPY.wallet).width;
  const tx = REF_W / 2 - ww / 2 + 18;
  const cy = wb.y + wb.h / 2;
  c.drawText(COPY.wallet, tx, cy + 7, fill('#FFFFFF'), wf);
  // a generic wallet: three coloured cards in a cream pocket
  const ix = tx - 44;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ix, cy - 11, 30, 22), 4, 4), fill('#E9E2D2'));
  const bands = ['#F04E3E', '#F9B233', '#3DB36B', '#3B82F6'];
  for (let i = 0; i < 4; i++) c.drawRect(Skia.XYWHRect(ix + 2, cy - 9 + i * 3.2, 26, 3.2), fill(bands[i]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ix, cy - 1, 30, 12), 3, 3), fill('#E9E2D2'));
  // transactions
  const t = UI.tx;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(t.x, t.y, t.w, t.h), t.r, t.r), fill('#FFFFFF'));
  c.drawText(COPY.transactions, 41.5, 711 + 8, ink, Skia.Font(f.bold, 22));
  c.drawText(COPY.nothing, 41.5, 737 + 5.8, ink, Skia.Font(f.regular, 16));
  c.drawCircle(340, 724, 27, fill('#EEF0F5'));
  return rec.finishRecordingAsPicture();
}

export function makeRes(f: Faces, frost: SkRuntimeEffect): Res | null {
  const card = bake(UI.card.w, UI.card.h, (c) => drawCard(c, f));
  const frosted = bake(UI.card.w, UI.card.h, (c) => drawCard(c, f), 7);
  if (!card || !frosted) return null;
  return { card, frosted, page: drawPage(f), frost, f: { tile: Skia.Font(f.semi, 15), badge: Skia.Font(f.semi, 12) } };
}

// ------------------------------------------------------------------ the scene

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = { amb: number; tiltX: number; tiltY: number };

function hex(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}

function drawIcon(c: SkCanvas, i: number, x: number, y: number, spin: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setStrokeWidth(2.4);
  c.save();
  c.translate(x, y);
  if (i === 0) {
    // a snowflake (it spins when the card freezes or thaws)
    c.rotate(spin, 0, 0);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: -12, y: -12 }, { x: 12, y: 12 }, [hex('#4B2BE0'), hex('#8B46FF')], null, TileMode.Clamp));
    for (let k = 0; k < 6; k++) {
      c.save();
      c.rotate(k * 60, 0, 0);
      c.drawLine(0, 0, 0, -12, p);
      c.drawLine(0, -7.5, -3.6, -10.6, p);
      c.drawLine(0, -7.5, 3.6, -10.6, p);
      c.restore();
    }
  } else if (i === 1) {
    // two arrows chasing round
    const a = Skia.Path.Make();
    a.addArc(Skia.XYWHRect(-11, -11, 22, 22), 200, 130);
    p.setColor(hex('#FF6A1A'));
    c.drawPath(a, p);
    const b = Skia.Path.Make();
    b.addArc(Skia.XYWHRect(-11, -11, 22, 22), 20, 130);
    p.setColor(hex('#5B2BE6'));
    c.drawPath(b, p);
    const head = Skia.Path.Make();
    head.moveTo(6.5, -12.5);
    head.lineTo(10.5, -8);
    head.lineTo(4.5, -6.5);
    p.setColor(hex('#FF6A1A'));
    c.drawPath(head, p);
    const head2 = Skia.Path.Make();
    head2.moveTo(-6.5, 12.5);
    head2.lineTo(-10.5, 8);
    head2.lineTo(-4.5, 6.5);
    p.setColor(hex('#5B2BE6'));
    c.drawPath(head2, p);
  } else {
    // a circle struck through
    p.setShader(Skia.Shader.MakeLinearGradient({ x: -12, y: -12 }, { x: 12, y: 12 }, [hex('#5B2BE6'), hex('#FF6A1A')], null, TileMode.Clamp));
    c.drawCircle(0, 0, 11, p);
    c.drawLine(-8, -8, 8, 8, p);
  }
  c.restore();
}

function drawTiles(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const t = UI.tiles;
  const lp = Skia.Paint();
  lp.setAntiAlias(true);
  for (let i = 0; i < 3; i++) {
    const x = t.x[i];
    const s = 1 - 0.04 * f.press[i];
    c.save();
    c.translate(x + t.w / 2, t.y + t.h / 2);
    c.scale(s, s);
    c.translate(-(x + t.w / 2), -(t.y + t.h / 2));
    const bg = Skia.Paint();
    bg.setAntiAlias(true);
    // the Freeze tile turns icy while the card is frozen
    bg.setColor(i === 0 ? Float32Array.of(mix(1, 0.9, f.frozenLabel), mix(1, 0.95, f.frozenLabel), 1, 1) : hex('#FFFFFF'));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, t.y, t.w, t.h), t.r, t.r), bg);
    drawIcon(c, i, x + t.w / 2, t.iconY, i === 0 ? f.spin : 0);
    if (i === 0) {
      const k = f.frozenLabel;
      lp.setColor(hex(C.ink, 1 - k));
      const w0 = r.f.tile.measureText(COPY.tiles[0]).width;
      c.drawText(COPY.tiles[0], x + t.w / 2 - w0 / 2, t.labelY + 5.4 - 6 * k, lp, r.f.tile);
      lp.setColor(hex(C.ink, k));
      const w1 = r.f.tile.measureText(COPY.unfreeze).width;
      c.drawText(COPY.unfreeze, x + t.w / 2 - w1 / 2, t.labelY + 5.4 + 6 * (1 - k), lp, r.f.tile);
    } else {
      lp.setColor(hex(C.ink));
      const w = r.f.tile.measureText(COPY.tiles[i]).width;
      c.drawText(COPY.tiles[i], x + t.w / 2 - w / 2, t.labelY + 5.4, lp, r.f.tile);
    }
    c.restore();
  }
}

/** The card (card space) with its ice; or cut into strips while it is shredded. */
function drawCardLayer(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const w = UI.card.w;
  const h = UI.card.h;
  const src = Skia.XYWHRect(0, 0, r.card.width(), r.card.height());
  if (f.shred >= 0) {
    // cut into 14 strips that part, then fall away one after another, turning
    const n = 14;
    const sw = w / n;
    const kx = r.card.width() / w;
    for (let i = 0; i < n; i++) {
      const order = (i * 7) % n;
      const u = Math.max(0, (f.shred - 0.18 - order * 0.025) / 0.6);
      const gap = smooth(f.shred / 0.18) * (i - (n - 1) / 2) * 0.9;
      const fall = 520 * u * u;
      const rot = (i % 2 === 0 ? 1 : -1) * 18 * u;
      const a = 1 - smooth((u - 0.6) / 0.4);
      if (a <= 0.01) continue;
      const ip = Skia.Paint();
      ip.setAntiAlias(true);
      ip.setAlphaf(a);
      c.save();
      c.translate(i * sw + sw / 2 + gap, h / 2 + fall);
      c.rotate(rot, 0, 0);
      c.drawImageRectOptions(r.card, Skia.XYWHRect(i * sw * kx, 0, sw * kx, r.card.height()), Skia.XYWHRect(-sw / 2, -h / 2, sw, h), FilterMode.Linear, MipmapMode.None, ip);
      c.restore();
    }
    return;
  }
  const rr = Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), UI.card.r, UI.card.r);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.1, 0.12, 0.2, 0.16));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(6, 12, w - 12, h), UI.card.r, UI.card.r), sh);
  c.drawImageRectOptions(r.card, src, Skia.XYWHRect(0, 0, w, h), FilterMode.Linear, MipmapMode.None, null);
  // a sheen that follows the tilt, and the shine over a new card
  const sx = w * (0.5 + 0.45 * live.tiltX) + (f.shine >= 0 ? mix(-w, w, f.shine) : 0);
  const shp = Skia.Paint();
  shp.setBlendMode(BlendMode.Plus);
  shp.setShader(
    Skia.Shader.MakeLinearGradient({ x: sx - 60, y: 0 }, { x: sx + 60, y: h }, [hex('#FFFFFF', 0), hex('#FFFFFF', f.shine >= 0 ? 0.5 : 0.18), hex('#FFFFFF', 0)], null, TileMode.Clamp),
  );
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), shp);
  if (f.frost > 0.001) {
    const uni = [w, h, f.cx, f.cy, f.frost, f.melt, live.amb, 1];
    // the frosted copy of the card under the ice
    c.saveLayer();
    c.drawImageRectOptions(r.frosted, src, Skia.XYWHRect(0, 0, w, h), FilterMode.Linear, MipmapMode.None, null);
    const m = Skia.Paint();
    m.setBlendMode(BlendMode.DstIn);
    m.setShader(r.frost.makeShader(uni));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), m);
    c.restore();
    // a cold tint, then the ice itself
    const tint = Skia.Paint();
    tint.setColor(Float32Array.of(0.55, 0.75, 1, 0.12 * Math.min(1, f.frost) * (1 - f.melt)));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), tint);
    const ice = Skia.Paint();
    uni[7] = 0;
    ice.setShader(r.frost.makeShader(uni));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), ice);
    // ice crystals catch the light in turn, twinkling as the phone tilts
    const iced = smooth((f.frost - 0.85) / 0.3) * (1 - f.melt);
    if (iced > 0.01) {
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      sp.setBlendMode(BlendMode.Plus);
      for (let k = 0; k < 9; k++) {
        const hx = Math.sin((k + 1) * 78.233) * 43758.5453;
        const hy = Math.sin((k + 1) * 12.9898) * 43758.5453;
        const px = (hx - Math.floor(hx)) * (w - 30) + 15;
        const py = (hy - Math.floor(hy)) * (h - 30) + 15;
        const tw = Math.max(0, Math.sin(Math.PI * 2 * (live.amb / 3 + k * 0.37) + live.tiltX * 3));
        const a = iced * tw * tw * tw;
        if (a < 0.02) continue;
        const arm = 3 + 5 * tw;
        sp.setColor(hex('#FFFFFF', 0.85 * a));
        sp.setStrokeWidth(1.3);
        c.drawLine(px - arm, py, px + arm, py, sp);
        c.drawLine(px, py - arm, px, py + arm, sp);
        c.drawCircle(px, py, 1.6, sp);
      }
    }
  }
  // melt water running down
  if (f.drips >= 0) {
    const dp = Skia.Paint();
    dp.setAntiAlias(true);
    for (let k = 0; k < 7; k++) {
      const x = 24 + ((k * 53) % (w - 40));
      const st = 0.12 * ((k * 3) % 5);
      const u = Math.max(0, (f.drips - st) / (1 - st));
      if (u <= 0) continue;
      const len = 26 + 34 * u;
      const yb = 20 + (h + 10) * u * u;
      const a = 0.5 * (1 - smooth((u - 0.75) / 0.25));
      const g = Skia.Paint();
      g.setShader(Skia.Shader.MakeLinearGradient({ x, y: yb - len }, { x, y: yb }, [hex('#CFE6FF', 0), hex('#BFDDFF', a)], null, TileMode.Clamp));
      g.setStrokeWidth(3);
      g.setStrokeCap(StrokeCap.Round);
      c.drawLine(x, yb - len, x, yb, g);
      dp.setColor(hex('#E8F4FF', a * 1.4));
      c.drawCircle(x, yb + 1, 2.6, dp);
    }
  }
  c.restore();
  // cold air sinks off a frozen card: soft wisps slide down from its bottom edge and fade
  const cold = smooth((f.frost - 0.85) / 0.3) * (1 - f.melt);
  if (cold > 0.01) {
    const mp = Skia.Paint();
    mp.setAntiAlias(true);
    mp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    for (let k = 0; k < 5; k++) {
      const u = (live.amb / 4.5 + k / 5) % 1;
      const x = w * (0.12 + 0.19 * k) + 10 * Math.sin(Math.PI * 2 * (u + k * 0.3));
      const y = h - 6 + 46 * u;
      const a = cold * 0.32 * Math.sin(Math.PI * u);
      mp.setColor(Float32Array.of(0.86, 0.93, 1, a));
      c.drawOval(Skia.XYWHRect(x - 26 - 14 * u, y - 7, 52 + 28 * u, 14 + 6 * u), mp);
    }
  }
  // a "Frozen" chip on the card while iced
  const k = f.frozenLabel * (1 - f.melt);
  if (k > 0.01) {
    c.save();
    c.translate(w - 58, 16);
    c.scale(0.9 + 0.1 * k, 0.9 + 0.1 * k);
    const chip = Skia.Paint();
    chip.setAntiAlias(true);
    chip.setColor(Float32Array.of(1, 1, 1, 0.75 * k));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-4, -3, 52, 22), 11, 11), chip);
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(hex('#2E5DD8', k));
    c.drawText(COPY.frozen, 5, 12.5, tp, r.f.badge);
    c.restore();
  }
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live) {
  'worklet';
  c.drawColor(hex(C.bg));
  c.save();
  c.scale(g.s, g.s);
  c.drawPicture(r.page);
  drawTiles(c, r, f);
  // the card: tilts under the finger, flies off right on reissue, drops in from above when new
  const cd = UI.card;
  if (f.card > 0.5) {
    c.save();
    const drop = (1 - f.drop) * -330;
    const outX = f.out * 460;
    c.translate(cd.x + cd.w / 2 + outX, cd.y + cd.h / 2 + drop);
    c.rotate(f.out * 24 + (1 - f.drop) * -8, 0, 0);
    const sc = 0.94 + 0.06 * f.drop;
    c.scale(sc, sc);
    if (Math.abs(live.tiltX) > 0.001 || Math.abs(live.tiltY) > 0.001) {
      c.concat(processTransform3d([{ perspective: 700 }, { rotateY: live.tiltX * 0.28 }, { rotateX: -live.tiltY * 0.22 }]));
    }
    c.translate(-cd.w / 2, -cd.h / 2);
    drawCardLayer(c, r, f, live);
    c.restore();
  }
  c.restore();
}
