// On-demand three.js → Skia scene, adapted from react-native-skia's example
// useThreeScene (PR #4068, v3.0): `update` returns whether the frame needs a
// render, so an idle scene costs
// neither a three render nor a GPU pass. three's rAF loop keeps ticking on the
// JS thread (a no-op when nothing moves).
//
// The texture-backed SkImage is published ONCE per renderer (never a Skia
// host object per frame). A frame is pushed to the Canvas by `afterRender`,
// which writes plain numbers into shared values the Canvas draws with: a
// changed number re-runs Skia's mapper, which redraws the view, which samples
// the texture's latest contents. (Re-assigning the same SkImage to a shared
// value is a no-op in Reanimated's valueSetter, so it cannot trigger a redraw
// on its own.)
//
// Without a Graphite build (skia-gpu.ts) three renders on its own device, and
// each rendered frame is read back into a new raster SkImage that replaces
// the published one.
import { useEffect, useRef } from "react";
import type { DependencyList } from "react";
import { PixelRatio } from "react-native";
import type * as THREE from "three/webgpu";
import type { SkImage, SkSize } from "@shopify/react-native-skia";
import { Skia } from "@shopify/react-native-skia";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue } from "react-native-reanimated";

import { makeOffscreenTarget } from "./offscreen-target";
import { readTextureToImage, requestSceneDevice } from "./skia-gpu";
import { makeWebGPURenderer, disposeWebGPURenderer } from "./webgpu-renderer";

export interface OnDemandScene {
  scene: THREE.Object3D;
  camera: THREE.Camera;
  // Advances the simulation; returns true when the frame must be rendered.
  update: (time: number) => boolean;
  // Runs after a render was submitted (publish redraw-triggering numbers here).
  afterRender?: () => void;
  dispose?: () => void;
}

export type SceneDevice = {
  device: GPUDevice;
  /** The device is Skia's: textures pass between them without a copy. */
  shared: boolean;
};

export type OnDemandSceneFactory = (
  renderer: THREE.WebGPURenderer,
  size: SkSize,
  gpu: SceneDevice,
) => OnDemandScene | undefined;

export const useOnDemandThreeScene = (
  size: SkSize,
  makeScene: OnDemandSceneFactory,
  deps: DependencyList = [],
  {
    antialias = true,
    pixelRatio = PixelRatio.get(),
    active = true,
  }: {
    antialias?: boolean;
    pixelRatio?: number;
    /** False stops the loop (the screen is covered or away) without
     * tearing the scene down; true resumes it. */
    active?: boolean;
  } = {},
): SharedValue<SkImage | null> => {
  const image = useSharedValue<SkImage | null>(null);
  // The running loop, so `active` can stop and resume it in place.
  const loop = useRef<{
    renderer: THREE.WebGPURenderer;
    animate: (time: number) => void;
    ready: boolean;
  } | null>(null);
  const activeRef = useRef(active);

  useEffect(() => {
    const width = Math.floor(size.width * pixelRatio);
    const height = Math.floor(size.height * pixelRatio);
    if (width === 0 || height === 0) {
      return;
    }
    let disposed = false;
    let stop: (() => void) | undefined;

    const start = ({ device, shared }: SceneDevice) => {
      const target = makeOffscreenTarget(device, width, height);
      const renderer = makeWebGPURenderer({ ...target, antialias });
      const result = makeScene(renderer, { width, height }, { device, shared });
      if (!result) {
        target.destroy();
        return;
      }
      const { scene, camera, update, afterRender, dispose } = result;

      let published: SkImage | null = null;
      let first = true;
      // Copying path: one read-back at a time; a frame rendered meanwhile is
      // picked up by the next one.
      let reading = false;
      let stale = false;
      const readBack = () => {
        reading = true;
        stale = false;
        readTextureToImage(
          device,
          target.texture,
          width,
          height,
          target.texture.format,
        ).then((next) => {
          reading = false;
          if (disposed) {
            next?.dispose();
            return;
          }
          if (next) {
            const previous = published;
            published = next;
            image.value = next;
            // Let the UI runtime finish drawing the old frame first.
            if (previous) setTimeout(() => previous.dispose(), 250);
            afterRender?.();
          }
          if (stale) readBack();
        });
      };
      const animate = (time: number) => {
        const dirty = update(time);
        if (!dirty && !first) return;
        first = false;
        renderer.render(scene, camera);
        if (!shared) {
          if (reading) stale = true;
          else readBack();
          return;
        }
        if (!published) {
          published = (
            Skia.Image as unknown as {
              MakeImageFromNativeTexture: (pointer: unknown) => SkImage;
            }
          ).MakeImageFromNativeTexture(target.texture.nativePointer);
          image.value = published;
        }
        afterRender?.();
      };
      const current = { renderer, animate, ready: false };
      loop.current = current;
      renderer.init().then(() => {
        if (disposed) return;
        current.ready = true;
        if (activeRef.current) renderer.setAnimationLoop(animate);
      });

      stop = () => {
        if (loop.current === current) loop.current = null;
        dispose?.();
        disposeWebGPURenderer(renderer);
        image.value = null;
        // Give the UI runtime time to let go of the image before it and the
        // texture it references are released.
        const last = published;
        setTimeout(() => {
          last?.dispose();
          target.destroy();
        }, 250);
      };
    };

    requestSceneDevice().then(
      (gpu) => {
        if (!disposed) start(gpu);
      },
      (e) => console.warn("useOnDemandThreeScene has no WebGPU device", e),
    );

    return () => {
      disposed = true;
      stop?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [size.width, size.height, pixelRatio, antialias, image, ...deps]);

  useEffect(() => {
    activeRef.current = active;
    const l = loop.current;
    if (!l?.ready) return;
    l.renderer.setAnimationLoop(active ? l.animate : null);
  }, [active]);

  return image;
};
