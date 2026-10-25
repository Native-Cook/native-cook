import { BlendMode, Skia, type SkPicture, TileMode } from '@shopify/react-native-skia';

/** Noise is coloured; this makes it grey so grain reads as texture, not as confetti. */
const GRAY = [0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0.33, 0.34, 0.33, 0, 0, 0, 0, 0, 1, 0];

/** The room behind the form: deep green-black enamel with grain, a lamp's warmth and a vignette. */
export function recordRoom(w: number, h: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const base = Skia.Paint();
  base.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h }, ['#1A201C', '#141915', '#0E120F'].map((c2) => Skia.Color(c2)), [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), base);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.85, 0.85, 2, 3, 0, 0));
  grain.setColorFilter(Skia.ColorFilter.MakeMatrix(GRAY));
  grain.setBlendMode(BlendMode.SoftLight);
  grain.setAlphaf(0.25);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), grain);
  const lamp = Skia.Paint();
  lamp.setShader(
    Skia.Shader.MakeRadialGradient({ x: w * 0.3, y: h * 0.38 }, w * 1.1, ['rgba(255,206,140,0.10)', 'rgba(255,206,140,0)'].map((c2) => Skia.Color(c2)), null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), lamp);
  const v = Skia.Paint();
  v.setShader(
    Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.45 }, h * 0.75, ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(2,4,3,0.6)'].map((c2) => Skia.Color(c2)), [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), v);
  return rec.finishRecordingAsPicture();
}
