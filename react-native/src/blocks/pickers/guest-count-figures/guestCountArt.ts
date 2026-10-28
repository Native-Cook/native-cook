import { Skia, StrokeCap, PaintStyle, TileMode, type SkCanvas, type SkColor } from '@shopify/react-native-skia';

/*
 Guest Count Figures — the little people, drawn in code (a port of GuestCountFiguresArt.swift,
 same shapes and colours). Worklets; helpers first.

 Each figure stands with its feet on (0, 0), x centred, up is negative y, in points. Every part is
 a rounded rectangle or an ellipse filled with a left-lit gradient (14 % lighter on the left, 22 %
 darker on the right), so the flat colours read like painted figurines. Adults are 115 pt tall,
 children 76 pt, each scaled a little by its own height.
*/

export function hex(v: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

/** Mixes two hex colours. */
export function mix(a: number, b: number, t: number, alpha = 1): SkColor {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  const r0 = ((a >> 16) & 0xff) / 255;
  const g0 = ((a >> 8) & 0xff) / 255;
  const b0 = (a & 0xff) / 255;
  const r1 = ((b >> 16) & 0xff) / 255;
  const g1 = ((b >> 8) & 0xff) / 255;
  const b1 = (b & 0xff) / 255;
  return Float32Array.of(r0 + (r1 - r0) * k, g0 + (g1 - g0) * k, b0 + (b1 - b0) * k, alpha);
}

function lighten(v: number, k: number): SkColor {
  'worklet';
  const r = ((v >> 16) & 0xff) / 255;
  const g = ((v >> 8) & 0xff) / 255;
  const b = (v & 0xff) / 255;
  return Float32Array.of(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, 1);
}

function darken(v: number, k: number): SkColor {
  'worklet';
  const r = ((v >> 16) & 0xff) / 255;
  const g = ((v >> 8) & 0xff) / 255;
  const b = (v & 0xff) / 255;
  return Float32Array.of(r * (1 - k), g * (1 - k), b * (1 - k), 1);
}

function darkenHex(v: number, k: number): number {
  'worklet';
  const r = Math.round(((v >> 16) & 0xff) * (1 - k));
  const g = Math.round(((v >> 8) & 0xff) * (1 - k));
  const b = Math.round((v & 0xff) * (1 - k));
  return (r << 16) | (g << 8) | b;
}

// Primitives.

function shadePaint(v: number, x: number, w: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y: 0 },
      { x: x + w, y: 0 },
      [lighten(v, 0.14), hex(v), darken(v, 0.22)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  return p;
}

function rr(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, v: number) {
  'worklet';
  const rad = Math.min(r, w / 2, h / 2);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), rad, rad), shadePaint(v, x, w));
}

function el(c: SkCanvas, x: number, y: number, w: number, h: number, v: number) {
  'worklet';
  c.drawOval(Skia.XYWHRect(x, y, w, h), shadePaint(v, x, w));
}

function line(c: SkCanvas, x0: number, y0: number, x1: number, y1: number, w: number, v: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v));
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  c.drawLine(x0, y0, x1, y1, p);
}

// Shadows.

function blob(c: SkCanvas, cx: number, w: number, h: number, a: number) {
  'worklet';
  if (w <= 0) return;
  c.save();
  c.translate(cx, 0);
  c.scale(1, h / w);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: 0, y: 0 },
      w / 2,
      [hex(0x2a2420, a), hex(0x2a2420, a * 0.45), hex(0x2a2420, 0)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(0, 0, w / 2, p);
  c.restore();
}

/** The tight contact shadow and the long soft one to the right, under a figure at (0, 0). */
export function drawShadow(c: SkCanvas, slot: number, k: number, alpha: number) {
  'worklet';
  blob(c, slot * 0.55, slot * 1.7 * k, 10, 0.07 * alpha);
  blob(c, 0, slot * 0.9 * k, 7, 0.22 * alpha);
}

// Looks. hair: 0 beanie, 1 short, 2 bun, 3 long, 4 puffs · outer: 0 none, 1 vest, 2 coat ·
// extra: 0 none, 1 bag, 2 lanyard, 3 ring, 4 dots.

export type Look = {
  kid: boolean;
  scale: number;
  skin: number;
  hair: number;
  hairKind: number;
  hat: number;
  top: number;
  sleeve: number;
  outer: number;
  outerColor: number;
  bottom: number;
  shorts: boolean;
  shoes: number;
  extra: number;
  extraColor: number;
};

const L = (o: Partial<Look> & Pick<Look, 'kid' | 'scale' | 'skin' | 'hair' | 'hairKind' | 'top' | 'sleeve' | 'bottom' | 'shoes'>): Look => ({
  hat: 0,
  outer: 0,
  outerColor: 0,
  shorts: false,
  extra: 0,
  extraColor: 0,
  ...o,
});

export const LOOKS: Look[] = [
  L({ kid: false, scale: 1.0, skin: 0xc68a62, hair: 0x3a2a22, hairKind: 0, hat: 0x2fb5a8, top: 0xf1ece2, sleeve: 0xc68a62, bottom: 0x8c8648, shoes: 0x5a3b28, extra: 1, extraColor: 0x6b4a33 }),
  L({ kid: false, scale: 1.04, skin: 0xe2b48e, hair: 0x6b4428, hairKind: 1, top: 0x9fc0de, sleeve: 0x9fc0de, outer: 1, outerColor: 0x263447, bottom: 0xc49a5a, shoes: 0x2b2b2b, extra: 2, extraColor: 0x3a6fd8 }),
  L({ kid: false, scale: 0.97, skin: 0x8d5a3c, hair: 0x1f1a1a, hairKind: 2, top: 0xede6da, sleeve: 0xd9a33a, outer: 2, outerColor: 0xd9a33a, bottom: 0x3c3f48, shoes: 0x1e1e1e }),
  L({ kid: false, scale: 1.0, skin: 0xf0c9a8, hair: 0xa2512e, hairKind: 3, top: 0xb9a3d6, sleeve: 0xb9a3d6, bottom: 0x4b6a93, shoes: 0xece8e1 }),
  L({ kid: true, scale: 1.0, skin: 0x7a4b32, hair: 0x2a1e1a, hairKind: 4, top: 0xf08ca6, sleeve: 0x7a4b32, bottom: 0xf08ca6, shorts: true, shoes: 0xf2c94c, extra: 3, extraColor: 0x6fd3c2 }),
  L({ kid: true, scale: 0.9, skin: 0xd9a07a, hair: 0x3b2a20, hairKind: 1, top: 0xe8873a, sleeve: 0xe8873a, bottom: 0x5b4a8c, shoes: 0x2b2b2b, extra: 4, extraColor: 0xf7c58f }),
  L({ kid: true, scale: 0.95, skin: 0xf2cbaa, hair: 0xe3b04b, hairKind: 1, top: 0xd9534f, sleeve: 0xd9534f, bottom: 0x2e3a59, shorts: true, shoes: 0x3d7dd8 }),
];

// Body: h, headW, headH, torsoTop, torsoBot, torsoW, armW, legW, shoeW, shoeH, neckW, headDrop.
const ADULT = [118, 17, 19, -92, -56, 24, 7, 10, 13, 5, 6, 3];
const CHILD = [76, 15.5, 16.5, -58, -33, 19, 6, 8, 11, 4, 5, 0];
const DOTS = [-5, 6, 3.5, 10, -2, 16, 5, 19, -6, 21];

export function drawFigure(c: SkCanvas, look: Look) {
  'worklet';
  const b = look.kid ? CHILD : ADULT;
  const [h, headW, headH, torsoTop, torsoBot, torsoW, armW, legW, shoeW, shoeH, neckW, headDrop] = b;
  const headTop = -h + headDrop;
  const headCY = headTop + headH / 2;

  // Hair behind the head.
  if (look.hairKind === 3) rr(c, -headW / 2 - 1.5, headTop - 1, headW + 3, headH + 12, 9, look.hair);
  else if (look.hairKind === 2) el(c, -5.5, headTop - 7.5, 11, 10, look.hair);
  else if (look.hairKind === 4) {
    el(c, -headW / 2 - 5, headTop + 1, 10, 10, look.hair);
    el(c, headW / 2 - 5, headTop + 1, 10, 10, look.hair);
  }

  // Legs and shoes.
  const legTop = torsoBot - 4;
  for (let si = 0; si < 2; si++) {
    const s = si === 0 ? -1 : 1;
    const lx = s * (legW / 2 + 0.5);
    if (look.shorts) {
      const lw = legW * 0.78;
      rr(c, lx - lw / 2, legTop, lw, -legTop - shoeH + 1.5, lw / 2, look.skin);
      rr(c, lx - legW / 2 - 0.5, legTop, legW + 1, -legTop * 0.36, 3, look.bottom);
    } else {
      rr(c, lx - legW / 2, legTop, legW, -legTop - shoeH + 1.5, legW * 0.4, look.bottom);
    }
    rr(c, lx - shoeW / 2 + s * 1, -shoeH, shoeW, shoeH, shoeH / 2, look.shoes);
  }
  // Hips.
  rr(c, -torsoW / 2 + 1, torsoBot - 6, torsoW - 2, 10, 4, look.bottom);

  // Arms, then hands.
  const sleeve = look.outer === 2 ? look.outerColor : look.sleeve;
  for (let si = 0; si < 2; si++) {
    const s = si === 0 ? -1 : 1;
    const ax = s * (torsoW / 2 + armW / 2 - 1.5);
    rr(c, ax - armW / 2, torsoTop + 1, armW, torsoBot - torsoTop + 1, armW / 2, sleeve);
    const hr = armW * 0.55;
    el(c, ax - hr, torsoBot + 3 - hr, 2 * hr, 2 * hr, look.skin);
  }

  // Neck and torso.
  const neckTop = headCY + headH * 0.3;
  rr(c, -neckW / 2, neckTop, neckW, torsoTop + 3 - neckTop, 1, look.skin);
  rr(c, -torsoW / 2, torsoTop, torsoW, torsoBot - torsoTop + 2, torsoW * 0.3, look.top);

  if (look.outer === 2) {
    rr(c, -torsoW / 2 - 1, torsoTop, torsoW + 2, torsoBot - torsoTop + 22, 7, look.outerColor);
    line(c, 0.5, torsoTop + 5, 0.5, torsoBot + 20, 0.9, 0xa8761f);
    rr(c, -3.5, torsoTop - 0.5, 7, 5, 2.5, look.top);
  } else if (look.outer === 1) {
    const hw = torsoW / 2 - 2;
    rr(c, -torsoW / 2 - 0.5, torsoTop + 1, hw, torsoBot - torsoTop, 5, look.outerColor);
    rr(c, torsoW / 2 + 0.5 - hw, torsoTop + 1, hw, torsoBot - torsoTop, 5, look.outerColor);
    for (let i = 0; i < 2; i++) {
      const y = torsoTop + (i === 0 ? 13 : 23);
      line(c, -torsoW / 2 + 1, y, -2.5, y, 0.8, 0x18212e);
      line(c, 2.5, y, torsoW / 2 - 1, y, 0.8, 0x18212e);
    }
  }

  if (look.extra === 1) {
    line(c, -torsoW / 2 + 2, torsoTop + 2, torsoW / 2 + 2, torsoBot - 6, 2, look.extraColor);
    rr(c, torsoW / 2 - 2, torsoBot - 9, 9, 8, 2, look.extraColor);
  } else if (look.extra === 2) {
    line(c, -3, torsoTop + 1, 0, torsoTop + 13, 0.9, look.extraColor);
    line(c, 3, torsoTop + 1, 0, torsoTop + 13, 0.9, look.extraColor);
    rr(c, -3, torsoTop + 13, 6, 8, 1, 0xf7f5f0);
  } else if (look.extra === 4) {
    for (let i = 0; i < 5; i++) el(c, DOTS[2 * i] - 1.3, torsoTop + DOTS[2 * i + 1] - 1.3, 2.6, 2.6, look.extraColor);
  } else if (look.extra === 3) {
    const y = torsoBot - 6;
    el(c, -17, y - 7, 34, 14, look.extraColor);
    el(c, -10.5, y - 4, 21, 6, 0x3fa595);
    el(c, -17, y - 1, 34, 8, look.extraColor);
    for (let i = -1; i <= 1; i++) rr(c, i * 11 - 1.3, y, 2.6, 6, 1.3, 0xf7fffd);
    el(c, 12.5, y - 13, 9, 10, look.extraColor);
    el(c, 17.8, y - 10, 1.8, 1.8, 0x1e3b36);
  }

  // Head, face, hair in front.
  el(c, -headW / 2, headTop, headW, headH, look.skin);
  const eye = Skia.Paint();
  eye.setAntiAlias(true);
  eye.setColor(hex(0x2a1e1a, 0.75));
  const eyeY = headCY + 1.5;
  c.drawOval(Skia.XYWHRect(-3.6 - 0.8, eyeY - 0.8, 1.6, 1.6), eye);
  c.drawOval(Skia.XYWHRect(3.0 - 0.8, eyeY - 0.8, 1.6, 1.6), eye);

  if (look.hairKind === 0) {
    rr(c, -headW / 2 - 0.5, headTop + 5, 3.5, 8, 1.5, look.hair);
    rr(c, headW / 2 - 3, headTop + 5, 3.5, 8, 1.5, look.hair);
    rr(c, -headW / 2 - 1, headTop - 3, headW + 2, 11, 7, look.hat);
    rr(c, -headW / 2 - 1.5, headTop + 5, headW + 3, 4, 2, darkenHex(look.hat, 0.18));
  } else {
    el(c, -headW / 2 - 0.5, headTop - 1.5, headW + 1, headH * 0.46, look.hair);
    rr(c, -headW / 2 - 0.5, headTop + 2, 3, headH * 0.38, 1.5, look.hair);
    rr(c, headW / 2 - 2.5, headTop + 2, 3, headH * 0.38, 1.5, look.hair);
  }
}
