// Where three.js and Skia meet. With a Skia Graphite build (react-native-skia
// 3), Skia and react-native-webgpu share one Dawn device, so textures pass
// between them without a copy. The demo app's Skia (2.6, Ganesh) has no such
// device: three then renders on its own WebGPU device and the pixels are
// copied across (Skia → GPU texture with writeTexture, GPU texture → SkImage
// with a buffer read-back). The copies cost a few milliseconds a frame, and
// only run while the console is lifted.
import { importDevice } from "react-native-webgpu";
import {
  AlphaType,
  ColorType,
  Skia,
  type SkImage,
} from "@shopify/react-native-skia";

type GraphiteSkia = {
  getNativeDevice?: () => bigint;
  Surface: { MakeFromNativeTexture?: unknown };
  Image: { MakeImageFromNativeTexture?: unknown };
};

/** Skia's own WebGPU device when this is a Graphite build, else null. */
export const skiaGraphiteDevice = (): GPUDevice | null => {
  const skia = Skia as unknown as GraphiteSkia;
  if (
    typeof skia.getNativeDevice !== "function" ||
    typeof skia.Surface.MakeFromNativeTexture !== "function" ||
    typeof skia.Image.MakeImageFromNativeTexture !== "function"
  ) {
    return null;
  }
  try {
    return importDevice(skia.getNativeDevice());
  } catch {
    return null;
  }
};

/** A device to render on: Skia's when textures can be shared with it
 * (`shared`), otherwise a device of react-native-webgpu's own. */
export const requestSceneDevice = async (): Promise<{
  device: GPUDevice;
  shared: boolean;
}> => {
  const graphite = skiaGraphiteDevice();
  if (graphite) return { device: graphite, shared: true };
  const adapter = await navigator.gpu.requestAdapter();
  if (!adapter) throw new Error("No WebGPU adapter");
  return { device: await adapter.requestDevice(), shared: false };
};

/** Skia's colour type for pixels laid out as the given texture format. */
export const skiaColorType = (format: GPUTextureFormat) =>
  format.startsWith("bgra") ? ColorType.BGRA_8888 : ColorType.RGBA_8888;

/** Reads an 8-bit RGBA/BGRA texture (it needs COPY_SRC) back into a raster
 * SkImage. Resolves to null if the read-back fails. */
export const readTextureToImage = async (
  device: GPUDevice,
  texture: GPUTexture,
  width: number,
  height: number,
  format: GPUTextureFormat,
): Promise<SkImage | null> => {
  // Buffer copies want rows aligned to 256 bytes.
  const bytesPerRow = Math.ceil((width * 4) / 256) * 256;
  const buffer = device.createBuffer({
    size: bytesPerRow * height,
    usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
  });
  try {
    const encoder = device.createCommandEncoder();
    encoder.copyTextureToBuffer(
      { texture },
      { buffer, bytesPerRow, rowsPerImage: height },
      [width, height],
    );
    device.queue.submit([encoder.finish()]);
    await buffer.mapAsync(GPUMapMode.READ);
    const bytes = new Uint8Array(buffer.getMappedRange().slice(0));
    buffer.unmap();
    return Skia.Image.MakeImage(
      {
        width,
        height,
        colorType: skiaColorType(format),
        alphaType: AlphaType.Premul,
      },
      Skia.Data.fromBytes(bytes),
      bytesPerRow,
    );
  } catch {
    return null;
  } finally {
    buffer.destroy();
  }
};
