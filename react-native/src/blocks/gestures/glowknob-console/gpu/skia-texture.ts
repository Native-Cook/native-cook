// Vendored from Shopify/react-native-skia apps/example (PR #4068, v3.0), with
// a copying path for Skia builds without Graphite (see skia-gpu.ts).
import * as THREE from "three/webgpu";
import type { SkCanvas, SkSurface } from "@shopify/react-native-skia";
import { AlphaType, Skia } from "@shopify/react-native-skia";

import { skiaColorType } from "./skia-gpu";

export interface SkiaTexture {
  texture: THREE.ExternalTexture;
  // Draws into the texture with Skia and flushes; three samples the result
  // from the next render on. Can be called every frame.
  draw: (callback: (canvas: SkCanvas) => void) => void;
  destroy: () => void;
}

type NativeTextureSurfaces = {
  MakeFromNativeTexture: (pointer: unknown) => SkSurface;
};

// A three.js texture whose content is drawn by Skia. When `shared` is true
// the GPUTexture is created on Skia's Graphite device (which `device` must
// be, see skiaGraphiteDevice()), Skia renders straight into it through
// Skia.Surface.MakeFromNativeTexture, and three wraps the same texture in a
// THREE.ExternalTexture, without any copy. Otherwise Skia draws on an
// offscreen surface of its own and each draw uploads the pixels.
export const makeSkiaTexture = (
  device: GPUDevice,
  width: number,
  height: number,
  shared: boolean,
): SkiaTexture => {
  // three's canvas format, which Skia also renders to natively
  const format = navigator.gpu.getPreferredCanvasFormat();
  const gpuTexture = device.createTexture({
    size: [width, height],
    format,
    usage:
      GPUTextureUsage.RENDER_ATTACHMENT |
      GPUTextureUsage.TEXTURE_BINDING |
      GPUTextureUsage.COPY_DST,
  });
  const surface = shared
    ? (Skia.Surface as unknown as NativeTextureSurfaces).MakeFromNativeTexture(
        gpuTexture.nativePointer,
      )
    : Skia.Surface.MakeOffscreen(width, height) ??
      Skia.Surface.Make(width, height);
  if (!surface) {
    throw new Error("Could not make a Skia surface for the texture");
  }
  const canvas = surface.getCanvas();

  const texture = new THREE.ExternalTexture(gpuTexture);
  // The pixels are sRGB encoded in a non-sRGB texture format, which three's
  // WebGPU backend does not decode on its own: sample it with
  // texture(t).colorSpaceToWorking(THREE.SRGBColorSpace) in a node material.
  texture.colorSpace = THREE.SRGBColorSpace;

  const upload = () => {
    const snapshot = surface.makeImageSnapshot();
    const pixels = snapshot.readPixels(0, 0, {
      width,
      height,
      colorType: skiaColorType(format),
      alphaType: AlphaType.Premul,
    });
    snapshot.dispose();
    if (!(pixels instanceof Uint8Array)) return;
    device.queue.writeTexture(
      { texture: gpuTexture },
      pixels,
      { bytesPerRow: width * 4, rowsPerImage: height },
      [width, height],
    );
  };

  return {
    texture,
    draw: (callback) => {
      callback(canvas);
      // Submits Skia's work (on the shared queue when shared), ahead of
      // three's next render.
      surface.flush();
      if (!shared) upload();
    },
    destroy: () => {
      texture.dispose();
      // Drop the surface before the texture it draws into.
      surface.dispose();
      gpuTexture.destroy();
    },
  };
};
