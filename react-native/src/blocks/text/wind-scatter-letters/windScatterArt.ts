import { BlurStyle, PaintStyle, Skia, TileMode, vec, type SkPath, type SkPicture } from '@shopify/react-native-skia';

import { hash } from './windScatterMotion';

/*
 Wind Scatter Letters — static artwork: an autumn dusk sky over two ridges, recorded once.

 Sky: a four-stop gradient from plum-grey to ember near the horizon, a low sun glow, a few long
 cloud bands, grain and a vignette. Two ridge silhouettes, the near one with a lit rim. The leaf
 is one path (blade + stem) centred on its middle, 24 pt long, drawn three times per frame in
 three colours with per-leaf matrices.
*/

export const PALETTE = {
  sky: ['#16151b', '#25202a', '#4a2d2c', '#9a5333'],
  cream: '#f4e7d4',
  creamSoft: 'rgba(244,231,212,0.72)',
  ember: '#e8a86c',
  leaves: ['#c4572e', '#d99a3a', '#8f2f23'],
  ridgeFar: '#2a1d20',
  ridgeNear: '#170f12',
};

/** y of the near ridge's crest at x (a few slow sines), shared by the art and the resting leaves. */
export function ridgeY(x: number, W: number, H: number) {
  'worklet';
  const base = H * 0.8;
  return base - 18 * Math.sin((x / W) * Math.PI * 1.3 + 0.4) - 7 * Math.sin((x / W) * Math.PI * 4.1 + 1.2);
}

export function makeBackground(W: number, H: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, W, H));
  const sky = Skia.Paint();
  sky.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, H * 0.84), PALETTE.sky.map((x) => Skia.Color(x)), [0, 0.38, 0.72, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), sky);
  const sun = Skia.Paint();
  sun.setShader(
    Skia.Shader.MakeRadialGradient(vec(W * 0.72, H * 0.79), W * 0.75, [Skia.Color('rgba(255,170,96,0.42)'), Skia.Color('rgba(232,120,70,0.14)'), Skia.Color('rgba(0,0,0,0)')], [0, 0.4, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), sun);
  // Cloud bands.
  for (let i = 0; i < 5; i += 1) {
    const y = H * (0.5 + 0.05 * i) + (hash(i) - 0.5) * 20;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color(i % 2 ? 'rgba(255,190,140,0.07)' : 'rgba(30,20,30,0.18)'));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-40 + hash(i + 3) * W * 0.4, y, W * (0.6 + 0.5 * hash(i + 7)), 8 + 10 * hash(i + 9)), 10, 10), p);
  }
  // Ridges.
  const farB = Skia.PathBuilder.Make();
  farB.moveTo(0, H);
  for (let x = 0; x <= W; x += 8) farB.lineTo(x, H * 0.75 - 26 * Math.sin((x / W) * Math.PI * 2.2 + 2.1) - 10 * Math.sin((x / W) * 9.1));
  farB.lineTo(W, H).close();
  const far = farB.build();
  const fp = Skia.Paint();
  fp.setAntiAlias(true);
  fp.setShader(Skia.Shader.MakeLinearGradient(vec(0, H * 0.7), vec(0, H), [Skia.Color('#3a2526'), Skia.Color(PALETTE.ridgeFar)], null, TileMode.Clamp));
  c.drawPath(far, fp);
  const nearB = Skia.PathBuilder.Make();
  nearB.moveTo(0, H);
  for (let x = 0; x <= W; x += 6) nearB.lineTo(x, ridgeY(x, W, H));
  nearB.lineTo(W, H).close();
  const near = nearB.build();
  const np = Skia.Paint();
  np.setAntiAlias(true);
  np.setShader(Skia.Shader.MakeLinearGradient(vec(0, H * 0.76), vec(0, H), [Skia.Color('#241619'), Skia.Color(PALETTE.ridgeNear), Skia.Color('#0e0a0c')], [0, 0.4, 1], TileMode.Clamp));
  c.drawPath(near, np);
  const rimB = Skia.PathBuilder.Make().moveTo(0, ridgeY(0, W, H));
  for (let x = 6; x <= W; x += 6) rimB.lineTo(x, ridgeY(x, W, H));
  const rim = rimB.build();
  const rp = Skia.Paint();
  rp.setAntiAlias(true);
  rp.setStyle(PaintStyle.Stroke);
  rp.setStrokeWidth(1.2);
  rp.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(W, 0), [Skia.Color('rgba(255,170,100,0.05)'), Skia.Color('rgba(255,170,100,0.45)'), Skia.Color('rgba(255,170,100,0.2)')], [0, 0.72, 1], TileMode.Clamp));
  c.drawPath(rim, rp);
  // Grain and vignette.
  const dot = Skia.Paint();
  dot.setColor(Skia.Color('rgba(255,240,220,0.035)'));
  for (let i = 0; i < 1400; i += 1) c.drawCircle(hash(i * 1.37) * W, hash(i * 2.71 + 5) * H, 0.6, dot);
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient(vec(W / 2, H * 0.42), H * 0.78, [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.22)'), Skia.Color('rgba(0,0,0,0.6)')], [0.45, 0.78, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), vig);
  return rec.finishRecordingAsPicture();
}

/** The leaf blade, centred on (0, 0), pointing along +x. */
export function makeLeaf(): { blade: SkPath; rib: SkPath } {
  const blade = Skia.PathBuilder.Make()
    .moveTo(-12, 0)
    .cubicTo(-7, -7.5, 4, -8, 12, 0)
    .cubicTo(4, 7.2, -7, 6.6, -12, 0)
    .close()
    .build();
  const rib = Skia.PathBuilder.Make().moveTo(-16, 0.8).quadTo(-12, 0.3, -10, 0).lineTo(10, 0);
  for (const x of [-5, 0, 5]) rib.moveTo(x, 0).lineTo(x + 4, -3.6).moveTo(x, 0).lineTo(x + 4, 3.4);
  return { blade, rib: rib.build() };
}

/** Faint wind streaks: long, slightly curved strokes across the upper half (trimmed as they blow). */
export function makeStreaks(W: number, H: number): SkPath[] {
  const out: SkPath[] = [];
  for (let i = 0; i < 6; i += 1) {
    const y = H * (0.2 + 0.1 * i) + (hash(i * 4.1) - 0.5) * 30;
    out.push(Skia.PathBuilder.Make().moveTo(-20, y).cubicTo(W * 0.3, y - 30 * hash(i + 2), W * 0.6, y + 26 * hash(i + 5), W + 20, y - 12).build());
  }
  return out;
}
