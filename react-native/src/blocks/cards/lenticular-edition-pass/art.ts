import { ClipOp, FilterMode, MipmapMode, Skia, type SkColor } from '@shopify/react-native-skia';

import { corners, project, restPose, rotation, type Frame } from './motion';
import type { Resources } from './resources';

function color(hex: number, alpha = 1): SkColor { 'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha); }
function spanX(value: Frame) { 'worklet'; const p = corners(value.pose); let min = p[0].x, max = min; for (let i = 1; i < 4; i++) { min = Math.min(min, p[i].x); max = Math.max(max, p[i].x); } return max - min; }
function outline(value: Frame) {
  'worklet'; const centers = [{ x: 160.5, y: -99.5 }, { x: 160.5, y: 99.5 }, { x: -160.5, y: 99.5 }, { x: -160.5, y: -99.5 }], path = Skia.PathBuilder.Make();
  for (let corner = 0; corner < 4; corner++) for (let step = 0; step <= 12; step++) {
    const a = (-90 + corner * 90 + step * 7.5) * Math.PI / 180;
    const p = project({ x: centers[corner].x + 6 * Math.cos(a), y: centers[corner].y + 6 * Math.sin(a) }, value.pose);
    if (corner === 0 && step === 0) path.moveTo(p.x, p.y); else path.lineTo(p.x, p.y);
  }
  return path.close().detach();
}
/** One bounded inverse-ray shader; ridges stay attached to the bowed printed surface. */
export function draw(value: Frame, art: Resources | null, height: number) {
  'worklet';
  const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, height)), p = Skia.Paint();
  p.setAntiAlias(true); p.setColor(color(0xEDEFE9)); c.drawRect(Skia.XYWHRect(0, 0, 393, height), p);
  if (!art) return recorder.finishRecordingAsPicture();
  c.drawPicture(art.paper);
  const currentSpan = spanX(value), restPoints = corners(restPose());
  let min = restPoints[0].x, max = min; for (let i = 1; i < 4; i++) { min = Math.min(min, restPoints[i].x); max = Math.max(max, restPoints[i].x); }
  const center = project({ x: 0, y: 0 }, value.pose), width = 250 * Math.min(1.04, Math.max(0.92, currentSpan / (max - min))), x = 196.5 + 0.18 * (center.x - 196.5);
  p.setColor(color(0x192C35, 0.14)); p.setMaskFilter(art.ambientBlur);
  c.drawOval(Skia.XYWHRect(x - width / 2, 487, width, 30), p);
  p.setMaskFilter(art.contactBlur); p.setColor(color(0x192C35, 0.23));
  c.save(); c.translate(0, 1.5); c.drawPath(outline(value), p); c.restore();
  p.setMaskFilter(null); p.setColor(color(0xFFFFFF));
  p.setShader(art.lens.makeShaderWithChildren([...rotation(value.pose), value.pose.yaw, value.explicitViewB ?? -1], [art.atlasShader]));
  c.save(); c.clipRect(Skia.XYWHRect(21.5, 242.5, 350, 265), ClipOp.Intersect, true); c.drawRect(Skia.XYWHRect(21.5, 242.5, 350, 265), p); c.restore();
  return recorder.finishRecordingAsPicture();
}
export function thumbnail(art: Resources, index: number) {
  'worklet'; const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 58, 37)), image = art.prints[index];
  c.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, 58, 37), FilterMode.Linear, MipmapMode.None, null);
  return recorder.finishRecordingAsPicture();
}
