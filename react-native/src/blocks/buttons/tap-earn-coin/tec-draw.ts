// Tap Earn Coin — the coins thrown by each payout and the "+N¢" pills, drawn into one picture
// per frame on the UI thread. Plain paints (no shaders) so forty coins cost little.
import { type SkCanvas, type SkFont, type SkImage, Skia, type SkPicture } from '@shopify/react-native-skia';

import { BURST_LIFE, COINS, COINS_PER_POWER, coinPose, easeIn, easeOut, mix, pillPose, ring, seg, STRIDE } from './tec-motion';

export type CoinGeo = {
  w: number;
  h: number;
  ox: number; // cap centre, screen
  oy: number;
  R: number; // base half width
  tx: number[]; // centres of the three digit boxes
  ty: number;
  pillY: number;
  floor: number;
  labelX: number;
  labelY: number;
};

/** Live inputs besides the bursts: the object pulling coins in as it melts, and the refill drop. */
export type DrawExtra = { keep: number; pull: number; px: number; py: number; refillAge: number; atlas: SkImage | null };

/** A coin from the 24-frame spin atlas (96 px cells, coin radius 40 px), picked by its roll. */
const CELL = 96;
const FRAMES = 24;
function drawAtlasCoin(c: SkCanvas, img: SkImage, x: number, y: number, r: number, roll: number, spin: number, alpha: number) {
  'worklet';
  if (alpha <= 0.01 || r < 0.5) return;
  const tau = Math.PI * 2;
  const f = Math.round((((roll % tau) + tau) % tau) / tau * FRAMES) % FRAMES;
  const size = (r / 40) * CELL;
  const p = Skia.Paint();
  p.setAlphaf(alpha);
  c.save();
  c.translate(x, y);
  c.rotate((spin * 180) / Math.PI, 0, 0);
  c.drawImageRect(img, Skia.XYWHRect(f * CELL, 0, CELL, CELL), Skia.XYWHRect(-size / 2, -size / 2, size, size), p);
  c.restore();
}

function drawCoin(
  c: SkCanvas,
  x: number, y: number, r: number, tilt: number, spin: number, alpha: number, lit: number,
) {
  'worklet';
  if (alpha <= 0.01 || r < 0.5) return;
  const face = Math.max(0.12, Math.abs(tilt));
  const ry = r * face;
  const thick = r * 0.2 * Math.sqrt(1 - face * face);
  c.save();
  c.translate(x, y);
  c.rotate((spin * 180) / Math.PI, 0, 0);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // rim: the coin's edge, seen as a darker band under the face
  p.setColor(Skia.Color(`rgba(108, 108, 102, ${alpha})`));
  c.drawOval(Skia.XYWHRect(-r, -ry + thick, 2 * r, 2 * ry), p);
  // face: pewter with a soft light from the upper left that brightens as it turns to the light
  const hi = Math.round(200 + 52 * lit);
  const lo = Math.round(118 + 40 * lit);
  p.setShader(Skia.Shader.MakeLinearGradient(
    { x: -r * 0.8, y: -ry }, { x: r * 0.7, y: ry },
    [Skia.Color(`rgba(${hi}, ${hi}, ${hi - 6}, ${alpha})`), Skia.Color(`rgba(${lo}, ${lo + 2}, ${lo - 4}, ${alpha})`)],
    null, 0,
  ));
  c.drawOval(Skia.XYWHRect(-r, -ry, 2 * r, 2 * ry), p);
  p.setShader(null);
  // struck ring
  p.setStyle(1);
  p.setStrokeWidth(Math.max(0.6, r * 0.09));
  p.setColor(Skia.Color(`rgba(120, 120, 112, ${alpha * 0.7})`));
  c.drawOval(Skia.XYWHRect(-r * 0.7, -ry * 0.7, 1.4 * r, 1.4 * ry), p);
  // glint
  p.setStyle(0);
  p.setColor(Skia.Color(`rgba(255, 255, 250, ${alpha * 0.85 * lit * lit})`));
  c.drawOval(Skia.XYWHRect(-r * 0.62, -ry * 0.78, r * 0.7, ry * 0.5), p);
  c.restore();
}

function drawPill(
  c: SkCanvas,
  font: SkFont, x: number, y: number, scale: number, alpha: number, pay: number,
) {
  'worklet';
  if (alpha <= 0.01) return;
  const text = `+${pay}¢`;
  const tw = font.measureText(text).width;
  const pw = tw + 22;
  const ph = 30;
  c.save();
  c.translate(x, y);
  c.scale(scale, scale);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(`rgba(52, 56, 64, ${0.88 * alpha})`));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-pw / 2, -ph / 2, pw, ph), ph / 2, ph / 2), p);
  p.setColor(Skia.Color(`rgba(255, 255, 255, ${alpha})`));
  c.drawText(text, -tw / 2, 4.6, p, font);
  c.restore();
}

/**
 * bursts: flat [age, seed, pay, …]. Coins fly behind nothing: they are drawn above the page, the
 * pills above the coins.
 */
export function drawBursts(bursts: number[], g: CoinGeo, font: SkFont | null, x: DrawExtra): SkPicture {
  'worklet';
  const keep = x.keep;
  // coins still in the air are drawn into the button as it lifts off and melts
  const pull = easeIn(x.pull);
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  for (let b = 0; b + 3 < bursts.length; b += STRIDE) {
    const age = bursts[b];
    const seed = bursts[b + 1];
    const power = bursts[b + 3];
    if (age < 0 || age > BURST_LIFE) continue;
    const count = COINS + COINS_PER_POWER * Math.round(power);
    for (let i = 0; i < count; i++) {
      const box = (i + Math.floor(seed)) % 3;
      const pose = coinPose(age, seed, i, power, g.ox, g.oy, g.R, g.tx[box], g.ty, g.floor);
      const cx = mix(pose.x, x.px, pull);
      const cy = mix(pose.y, x.py, pull);
      const cr = pose.r * (1 - 0.85 * pull);
      const ca = pose.alpha * keep * (1 - seg(x.pull, 0.75, 1));
      if (x.atlas) drawAtlasCoin(c, x.atlas, cx, cy, cr, pose.roll, pose.spin, ca);
      else drawCoin(c, cx, cy, cr, pose.tilt, pose.spin, ca, pose.lit);
    }
  }
  // refill: three small coins drop into the "Taps left" label, one after another
  if (x.refillAge >= 0 && x.refillAge < 0.75) {
    for (let i = 0; i < 3; i++) {
      const a = x.refillAge - i * 0.07;
      if (a < 0) continue;
      const fall = easeIn(seg(a, 0, 0.38));
      const settle = seg(a, 0.38, 0.6);
      const y = g.labelY - 150 + 150 * fall - 6 * ring(Math.max(0, a - 0.38), 3, 10);
      const r = 7 * (1 - easeOut(settle));
      drawCoin(c, g.labelX + (i - 1) * 16, y, r, 0.55 + 0.45 * Math.cos(a * 18 + i), a * 2, 1 - settle, 0.5 + 0.5 * Math.sin(a * 21 + i));
    }
  }
  if (font) {
    for (let b = 0; b + 3 < bursts.length; b += STRIDE) {
      const age = bursts[b];
      if (age < 0 || age > 1.0) continue;
      const pp = pillPose(age, bursts[b + 1]);
      const big = 1 + 0.12 * bursts[b + 3];
      drawPill(c, font, g.ox + pp.dx * g.R, g.pillY - pp.rise * g.R * 0.32, pp.scale * big, pp.alpha * keep, bursts[b + 2]);
    }
  }
  return rec.finishRecordingAsPicture();
}
