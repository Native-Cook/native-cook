import { AlphaType, ColorType, FilterMode, MipmapMode, Skia, TileMode, useImage, type SkImage, type SkShader } from '@shopify/react-native-skia';
import { useMemo } from 'react';

import geometry from './assets/geometry.json';
import { paletteTint } from './driver';
import { PATH_LENGTH, rest, type Field, type Frame } from './motion';
import { EFFECT } from './shader';
import type { Tint } from './driver';

export type Bindings = { image: SkImage; shader: SkShader; ids: number[] };
export type Resources = { atlas: SkImage; shadow: SkImage; atlasShader: SkShader; path: SkImage; pathShader: SkShader; empty: Bindings };
function dataImage(bytes: Uint8Array, width: number, height: number) {
  'worklet'; const data = Skia.Data.fromBytes(bytes), image = Skia.Image.MakeImage({ width, height, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque }, data, width * 4); data.dispose();
  if (!image) throw new Error('Velvet Nap Studio: numerical texture creation failed.'); return image;
}
function nearest(image: SkImage) { 'worklet'; return image.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Nearest, MipmapMode.None); }
export function prepareBindings(current: Bindings | null, fields: Field[]): Bindings {
  'worklet'; if (current && fields.every(f => current.ids.includes(f.id))) return current;
  const bytes = new Uint8Array(64 * 192 * 4);
  for (let row = 0; row < 3; row++) for (let i = 0; i < 4096; i++) { const at = (row * 4096 + i) * 4, field = fields[row]; bytes[at] = field ? field.bytes[i * 4] : 0; bytes[at + 1] = field ? field.bytes[i * 4 + 1] : 128; bytes[at + 2] = 0; bytes[at + 3] = 255; }
  const image = dataImage(bytes, 64, 192); return { image, shader: nearest(image), ids: fields.map(f => f.id) };
}
function makePath() {
  const bytes = new Uint8Array(512 * 64 * 4), raw = new ArrayBuffer(4), view = new DataView(raw);
  for (let i = 0; i < 4096; i++) for (let channel = 0; channel < 4; channel++) {
    view.setFloat32(0, geometry.values[i][channel], true); const x = i % 64 * 8 + channel * 2, y = Math.floor(i / 64), at = (y * 512 + x) * 4;
    bytes[at] = view.getUint8(0); bytes[at + 1] = view.getUint8(1); bytes[at + 2] = view.getUint8(2); bytes[at + 3] = 255; bytes[at + 4] = view.getUint8(3); bytes[at + 7] = 255;
  } return dataImage(bytes, 512, 64);
}
export function useVelvetResources(): Resources | null {
  const atlas = useImage(require('./assets/atlas.png')), shadow = useImage(require('./assets/shadow.png'));
  return useMemo(() => {
    if (!atlas || !shadow) return null; const path = makePath();
    return { atlas, shadow, atlasShader: atlas.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None), path, pathShader: nearest(path), empty: prepareBindings(null, []) };
  }, [atlas, shadow]);
}
export function draw(frame: Frame, tint: Tint, art: Resources | null, binding: Bindings | null) {
  'worklet'; const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(-24, -24, 355, 322));
  if (!art || !binding || !EFFECT) return recorder.finishRecordingAsPicture();
  c.drawImageRectOptions(art.shadow, Skia.XYWHRect(0, 0, 710, 644), Skia.XYWHRect(-24, -24, 355, 322), FilterMode.Linear, MipmapMode.None, null);
  const weights = [frame.authorWeight, 0, 0, 0]; for (let i = 0; i < frame.fields.length; i++) { const at = binding.ids.indexOf(frame.fields[i].id); if (at >= 0) weights[at + 1] += frame.weights[i]; }
  const shader = EFFECT.makeShaderWithChildren([frame.time, frame.strength, PATH_LENGTH, 0, ...weights, ...tint], [art.atlasShader, art.pathShader, binding.shader]), paint = Skia.Paint(); paint.setAntiAlias(true); paint.setShader(shader); c.drawRect(Skia.XYWHRect(0, 0, 307, 274), paint);
  return recorder.finishRecordingAsPicture();
}
export function thumbnails(art: Resources | null) {
  const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 852));
  if (!art) return recorder.finishRecordingAsPicture();
  for (let i = 0; i < 8; i++) { c.save(); c.translate(33 + 43 * i, 411.29); c.scale(24 / 307, 24 / 307); c.clipRect(Skia.XYWHRect(0, 0, 307, 274), 1, true); c.drawPicture(draw(rest(), paletteTint(i), art, art.empty)); c.restore(); }
  return recorder.finishRecordingAsPicture();
}
