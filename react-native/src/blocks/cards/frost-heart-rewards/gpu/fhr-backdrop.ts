import { BlendMode, FilterMode, MipmapMode, Skia, TileMode } from '@shopify/react-native-skia';
import { Asset } from 'expo-asset';
import { File } from 'expo-file-system';

import { ART } from '../fhr-art';
import { RAYS_SKSL } from '../fhr-shaders';

/** Upload the two authored poses and the exact static page rays once per GPU lifetime.
 * The gem composites them with the native stage's live transforms, masks and hue front.
 */
export async function createFhrBackdrop(device: GPUDevice, own: GPUTexture[]) {
  const upload = async (bytes: Uint8Array | ArrayBuffer, label: string) => {
    const bitmap = await createImageBitmap(bytes, { premultiplyAlpha: 'none' });
    try {
      const texture = device.createTexture({
        label, size: [bitmap.width, bitmap.height], format: 'rgba8unorm',
        usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT,
      });
      own.push(texture);
      device.queue.copyExternalImageToTexture({ source: bitmap }, { texture }, [bitmap.width, bitmap.height]);
      return texture.createView();
    } finally {
      bitmap.close();
    }
  };
  const image = async (source: number, label: string) => {
    const asset = Asset.fromModule(source);
    await asset.downloadAsync();
    return upload(await new File(asset.localUri ?? asset.uri).arrayBuffer(), label);
  };
  const bear = await image(require('../assets/fhr-bear.png'), 'FHR refracted bear');
  const hug = await image(require('../assets/fhr-bear-hug.png'), 'FHR refracted open arms');
  // The outer skin uses the same screen registration, blur and floor mask as tgfx.
  // Rasterize once at native 3× density; material optics retain their cheaper target.
  const skinSurface = Skia.Surface.MakeOffscreen(1206, 1320);
  if (!skinSurface) throw new Error('FHR skin rasterization is unavailable');
  const source = Asset.fromModule(require('../assets/fhr-heart.png'));
  await source.downloadAsync();
  const imageData = Skia.Data.fromBytes(new Uint8Array(await new File(source.localUri ?? source.uri).arrayBuffer()));
  const heart = Skia.Image.MakeImageFromEncoded(imageData);
  if (!heart) throw new Error('FHR frost skin is unavailable');
  const skinCanvas = skinSurface.getCanvas();
  skinCanvas.scale(3, 3);
  const skinPaint = Skia.Paint();
  const blur = Skia.ImageFilter.MakeBlur(.55, .55, TileMode.Decal);
  skinPaint.setImageFilter(blur);
  const box = ART.heart, cx = box.x + box.width / 2, cy = box.y + box.height / 2;
  skinCanvas.save();
  skinCanvas.translate(cx, cy); skinCanvas.rotate(box.rotation, 0, 0); skinCanvas.translate(-cx, -cy);
  skinCanvas.drawImageRectOptions(heart, Skia.XYWHRect(0, 0, heart.width(), heart.height()), Skia.XYWHRect(box.x, box.y, box.width, box.height), FilterMode.Linear, MipmapMode.None, skinPaint);
  skinCanvas.restore();
  const floor = Skia.RuntimeEffect.Make('half4 main(float2 p) { float a=1.-smoothstep(306.,368.,p.y); return half4(a); }');
  if (!floor) throw new Error('FHR skin mask is unavailable');
  const maskPaint = Skia.Paint(); const floorShader = floor.makeShader([]);
  maskPaint.setShader(floorShader); maskPaint.setBlendMode(BlendMode.DstIn); skinCanvas.drawPaint(maskPaint);
  skinSurface.flush(); const skinSnapshot = skinSurface.makeImageSnapshot();
  const skin = await upload(skinSnapshot.encodeToBytes(), 'FHR registered screen-space frost skin');
  skinSnapshot.dispose(); skinSurface.dispose(); heart.dispose(); imageData.dispose(); skinPaint.dispose(); blur.dispose(); maskPaint.dispose(); floorShader.dispose(); floor.dispose();
  const rounded = await image(require('../assets/fhr-rounded-normal.png'), 'FHR alpha-distance rounded normals');
  const surface = Skia.Surface.MakeOffscreen(402, 440);
  const effect = Skia.RuntimeEffect.Make(RAYS_SKSL);
  if (!surface || !effect) throw new Error('FHR backdrop rasterization is unavailable');
  const paint = Skia.Paint();
  const shader = effect.makeShader([0, 0, 0, 0, 0, 0, 0, 0]);
  paint.setShader(shader);
  surface.getCanvas().drawPaint(paint);
  surface.flush();
  const snapshot = surface.makeImageSnapshot();
  try {
    const page = await upload(snapshot.encodeToBytes(), 'FHR refracted page rays');
    return { bear, hug, page, skin, rounded };
  } finally {
    snapshot.dispose();
    shader.dispose();
    paint.dispose();
    effect.dispose();
    surface.dispose();
  }
}
