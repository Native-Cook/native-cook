import { BlendMode, BlurStyle, type SkCanvas, type SkPicture, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';

/*
 The tab bar's canvas: a ripstop-nylon bar (recorded once), the climbing rope pinned to two steel
 eyelets at the screen edges, the knot that marks the active tab, and tap ripples.
 Canvas-local coordinates: the canvas starts TOP_PAD above the bar.
 */

export const TOP_PAD = 30;
/** The rope's pinned line, below the bar's top edge. */
export const ROPE_LINE = 5;
const ROPE_W = 5.2;
const SAMPLES = 48;

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function stroke(c: Float32Array, width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(1);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setColor(c);
  return p;
}

function blurred(c: Float32Array, sigma: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

/** The bar: dark forest ripstop with a lit top edge, and the two eyelets the rope is tied to. */
export function recordBar(w: number, barH: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const H = barH + TOP_PAD;
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, w, H));
  const y0 = TOP_PAD;
  // Soft shadow the bar casts upward onto the content.
  const lift = Skia.Paint();
  lift.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: y0 }, [hexA(0x0b120e, 0), hexA(0x0b120e, 0.35)], [0, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, y0), lift);
  const body = Skia.Paint();
  body.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: H }, [hexA(0x24372d), hexA(0x1b2b23), hexA(0x14211b), hexA(0x101a15)], [0, 0.25, 0.7, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, y0, w, barH), body);
  // Ripstop: a fine grid, with a heavier thread every fourth line.
  const fine = stroke(hexA(0xd8e6dc, 0.035), 0.5);
  const heavy = stroke(hexA(0xd8e6dc, 0.06), 0.9);
  for (let x = 3, k = 0; x < w; x += 6, k++) canvas.drawLine(x, y0, x, H, k % 4 === 0 ? heavy : fine);
  for (let y = y0 + 3, k = 0; y < H; y += 6, k++) canvas.drawLine(0, y, w, y, k % 4 === 0 ? heavy : fine);
  const noise = Skia.Paint();
  noise.setShader(Skia.Shader.MakeFractalNoise(1.2, 1.2, 2, 11, 0, 0));
  noise.setAlphaf(0.05);
  noise.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(0, y0, w, barH), noise);
  canvas.drawLine(0, y0 + 0.5, w, y0 + 0.5, stroke(hexA(0xe9f2ea, 0.16), 1));
  // Eyelets at both edges.
  for (const x of [7, w - 7]) {
    const cy = y0 + ROPE_LINE;
    canvas.drawCircle(x, cy + 1.5, 6.5, blurred(hexA(0x000000, 0.5), 2));
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(1);
    ring.setStrokeWidth(2.6);
    ring.setShader(Skia.Shader.MakeLinearGradient({ x, y: cy - 6 }, { x, y: cy + 6 }, [hexA(0xf2f4f6), hexA(0x9aa1a9), hexA(0x4b5158)], [0, 0.5, 1], TileMode.Clamp));
    canvas.drawCircle(x, cy, 5, ring);
  }
  return rec.finishRecordingAsPicture();
}

/**
 Draws the rope from its heights `ys` (canvas y at SAMPLES + 1 evenly spaced x across the width),
 the knot at (kx, ky) leaning with the rope's slope, and a ripple (0–1, or −1) at (rx, ry).
 */
export function drawRope(canvas: SkCanvas, w: number, ys: number[], kx: number, ky: number, slope: number, ripple: number, rx: number, ry: number) {
  'worklet';
  const step = w / SAMPLES;
  const pathB = Skia.PathBuilder.Make();
  pathB.moveTo(0, ys[0]);
  for (let i = 1; i < SAMPLES; i++) {
    const mx = (i + 0.5) * step;
    const my = (ys[i] + ys[i + 1]) / 2;
    pathB.quadTo(i * step, ys[i], mx, my);
  }
  pathB.lineTo(w, ys[SAMPLES]);

  // Shadow on the bar, then the rope: dark core, dyed sheath, a lit ridge.
  canvas.save();
  canvas.translate(0, 3.5);
  const path = pathB.build();
  canvas.drawPath(path, (() => {
    const p = stroke(hexA(0x050a07, 0.55), ROPE_W + 1.5);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.4, true));
    return p;
  })());
  canvas.restore();
  canvas.drawPath(path, stroke(hexA(0x6e2810), ROPE_W + 1.2));
  canvas.drawPath(path, stroke(hexA(0xdc6430), ROPE_W));
  canvas.save();
  canvas.translate(0, -1.3);
  canvas.drawPath(path, stroke(hexA(0xffb37e, 0.55), 1.2));
  canvas.restore();

  // The braid: short diagonal strands every 4.5 pt, turned with the rope.
  const braidB = Skia.PathBuilder.Make();
  const navyB = Skia.PathBuilder.Make();
  for (let x = 4, k = 0; x < w - 2; x += 4.5, k++) {
    const i = Math.min(SAMPLES - 1, Math.floor(x / step));
    const f = x / step - i;
    const y = ys[i] + (ys[i + 1] - ys[i]) * f;
    const a = Math.atan2(ys[i + 1] - ys[i], step);
    const ca = Math.cos(a + 1.05);
    const sa = Math.sin(a + 1.05);
    const target = k % 5 === 2 ? navyB : braidB;
    target.moveTo(x - ca * 2.4, y - sa * 2.4);
    target.lineTo(x + ca * 2.4, y + sa * 2.4);
  }
  const braid = braidB.build();
  canvas.drawPath(braid, stroke(hexA(0x8f3414, 0.6), 1.1));
  const navy = navyB.build();
  canvas.drawPath(navy, stroke(hexA(0x22324f, 0.7), 1.2));

  // The knot: three wraps of a barrel knot around the rope, lit from above.
  canvas.save();
  canvas.translate(kx, ky);
  canvas.rotate((Math.atan(slope) * 180) / Math.PI, 0, 0);
  canvas.drawOval(Skia.XYWHRect(-15, -4, 30, 16), blurred(hexA(0x020503, 0.55), 3.5));
  for (let k = -1; k <= 1; k++) {
    canvas.save();
    canvas.translate(k * 6.2, 0);
    canvas.rotate(-24, 0, 0);
    const rect = Skia.RRectXY(Skia.XYWHRect(-4, -9, 8, 18), 4, 4);
    canvas.drawRRect(rect, (() => {
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setShader(Skia.Shader.MakeLinearGradient({ x: -4, y: -9 }, { x: 4, y: 9 }, [hexA(0xffa16a), hexA(0xe2672f), hexA(0xb3461c), hexA(0x6a240c)], [0, 0.35, 0.7, 1], TileMode.Clamp));
      return p;
    })());
    canvas.drawRRect(rect, stroke(hexA(0x4f1a08, 0.7), 0.8));
    canvas.drawLine(-1.6, -6.5, -1.6, 4, stroke(hexA(0xffd1ad, 0.5), 1));
    canvas.restore();
  }
  canvas.restore();

  if (ripple >= 0) {
    const r = 12 + 22 * (1 - Math.pow(1 - ripple, 3));
    const a = ripple < 0.15 ? ripple / 0.15 : 1 - (ripple - 0.15) / 0.85;
    canvas.drawCircle(rx, ry, r, stroke(hexA(0xf4ead8, 0.45 * a), 1.5));
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setColor(hexA(0xf4ead8, 0.1 * a));
    canvas.drawCircle(rx, ry, r, fill);
  }
}

export const ROPE_SAMPLES = SAMPLES;
