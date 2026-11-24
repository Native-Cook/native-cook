// Ripple Pond Photo — the WebGPU water.
//
// An untouchable canvas over the photo. Each frame the block's ticker hands over how many simulation
// steps to run, the damping and up to four drops (rpp-motion.ts); this component runs those steps on
// two ping-ponged height textures and draws the photo through the result, all encoded on the UI
// thread in the same frame as the views. Optional by design: without the native module (Expo Go, web),
// a software adapter, a setup error or a lost device it reports false and the block falls back to
// RppSkiaFallback.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { Image, PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { LOOK, SIM } from '../rpp-motion';
import { STEP_FLOATS, STEP_WGSL, VIEW_FLOATS, VIEW_WGSL } from './rpp-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** the ticker's frame: serial, epoch (a change clears the water), steps, c², damping, then four drops of x, y, radius, depth */
export const FRAME = { serial: 0, epoch: 1, steps: 2, c2: 3, damp: 4, drops: 5, length: 21 } as const;

/** everything the UI thread needs to step and draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  /** the step's uniforms with the frame's drops, and without them */
  dropBuf: GPUBuffer;
  plainBuf: GPUBuffer;
  step: GPURenderPipeline;
  view: GPURenderPipeline;
  /** the two height textures as render targets */
  targetA: GPUTextureView;
  targetB: GPUTextureView;
  /** a step reading A (so writing B) with and without drops, and the same reading B */
  fromADrop: GPUBindGroup;
  fromAPlain: GPUBindGroup;
  fromBDrop: GPUBindGroup;
  fromBPlain: GPUBindGroup;
  /** the photo seen through A, and through B */
  viewA: GPUBindGroup;
  viewB: GPUBindGroup;
  gridW: number;
  gridH: number;
};

type Props = {
  /** the photo (a require()d asset) */
  photoAsset: number;
  /** written by the block's ticker every frame, see FRAME */
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  /** grid rows (gridRows(width, height)) */
  rows: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function RppGpu(props: Props) {
  const [webgpu, setWebgpu] = useState<WebGPU | null>(null);
  const report = useRef(props.onAvailable);
  useEffect(() => {
    report.current = props.onAvailable;
  });
  useEffect(() => {
    if (props.disabled) return;
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (!TurboModuleRegistry.get('WebGPUModule')) {
          report.current(false);
          return;
        }
        // Expo Go has no native module, so the package is only loaded behind this check.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        setWebgpu(require('react-native-webgpu') as WebGPU);
      } catch {
        report.current(false);
      }
    });
    return () => {
      live = false;
    };
  }, [props.disabled]);
  if (!webgpu || props.disabled) return null;
  return (
    <Boundary onFailure={() => report.current(false)}>
      <Water {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function Water({ webgpu, frame, width, height, rows, photoAsset, onAvailable }: Props & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const ref = useCanvasRef();
  const report = useRef(onAvailable);
  useEffect(() => {
    report.current = onAvailable;
  });
  const shown = useSharedValue(0);
  const fade = useAnimatedStyle(() => ({ opacity: shown.get() }));
  const commands = useSharedValue<Commands | null>(null);
  const presented = useSharedValue(0);
  /** the last frame serial that was stepped, and which texture holds the water now (0 = A) */
  const seen = useSharedValue(-1);
  const epoch = useSharedValue(0);
  const current = useSharedValue(0);
  const ready = () => {
    shown.set(withTiming(1, { duration: 220 }));
    report.current(true);
  };
  const fail = () => report.current(false);
  const draw = useFrameCallback(() => {
    const c = commands.get();
    const f = frame.get();
    if (!c || !f || f.length < FRAME.length) return;
    try {
      const fresh = f[FRAME.serial] !== seen.get();
      seen.set(f[FRAME.serial]);
      const steps = fresh ? Math.min(SIM.maxSteps, Math.max(0, Math.round(f[FRAME.steps]))) : 0;
      const encoder = c.device.createCommandEncoder();
      let at = current.get();
      if (f[FRAME.epoch] !== epoch.get()) {
        epoch.set(f[FRAME.epoch]);
        // still water: both textures cleared to zero height
        const a = encoder.beginRenderPass({ colorAttachments: [{ view: c.targetA, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] });
        a.end();
        const b = encoder.beginRenderPass({ colorAttachments: [{ view: c.targetB, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] });
        b.end();
      }
      if (steps > 0) {
        const withDrops = new Float32Array(STEP_FLOATS);
        withDrops[0] = f[FRAME.c2];
        withDrops[1] = f[FRAME.damp];
        withDrops[2] = c.gridW;
        withDrops[3] = c.gridH;
        const plain = new Float32Array(withDrops);
        for (let i = 0; i < 16; i++) withDrops[4 + i] = f[FRAME.drops + i];
        c.device.queue.writeBuffer(c.dropBuf, 0, withDrops);
        c.device.queue.writeBuffer(c.plainBuf, 0, plain);
        for (let s = 0; s < steps; s++) {
          const fromA = at === 0;
          const pass = encoder.beginRenderPass({ colorAttachments: [{ view: fromA ? c.targetB : c.targetA, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] });
          pass.setPipeline(c.step);
          // the frame's drops go in with its first step only
          pass.setBindGroup(0, fromA ? (s === 0 ? c.fromADrop : c.fromAPlain) : s === 0 ? c.fromBDrop : c.fromBPlain);
          pass.draw(3);
          pass.end();
          at = 1 - at;
        }
        current.set(at);
      }
      const pass = encoder.beginRenderPass({ colorAttachments: [{ view: c.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
      pass.setPipeline(c.view);
      pass.setBindGroup(0, at === 0 ? c.viewA : c.viewB);
      pass.draw(3);
      pass.end();
      c.device.queue.submit([encoder.finish()]);
      c.context.present();
      presented.set(presented.get() + 1);
      if (presented.get() === 2) scheduleOnRN(ready);
    } catch {
      commands.set(null);
      scheduleOnRN(fail);
    }
  }, false);

  useEffect(() => {
    let live = true;
    let cleanup = () => {};
    const compile = async (device: GPUDevice, label: string, code: string) => {
      const module = device.createShaderModule({ label, code });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${label} ${m.lineNum}:${m.linePos} ${m.message}`).join('; '));
      return module;
    };
    const start = async () => {
      webgpu.installWebGPU?.();
      const nav = globalThis.navigator as Navigator & { gpu?: GPU };
      const adapter = await nav.gpu?.requestAdapter();
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('No hardware WebGPU adapter.');
      const device = await adapter.requestDevice();
      if (!live) {
        device.destroy();
        return;
      }
      const context = ref.current?.getContext('webgpu') as RNCanvasContext | null;
      if (!context) throw new Error('No WebGPU context.');
      const pr = PixelRatio.get();
      const format = nav.gpu!.getPreferredCanvasFormat();
      const pxW = Math.round(width * pr);
      const pxH = Math.round(height * pr);

      // the photo, uploaded once
      const src = Image.resolveAssetSource(photoAsset);
      const bytes = await (await fetch(src.uri)).arrayBuffer();
      const bitmap = await createImageBitmap(bytes as unknown as ImageBitmapSource, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
      if (!live) {
        device.destroy();
        return;
      }
      const photoTex = device.createTexture({ label: 'Cove', size: [bitmap.width, bitmap.height], format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT });
      device.queue.copyExternalImageToTexture({ source: bitmap }, { texture: photoTex, premultipliedAlpha: false }, [bitmap.width, bitmap.height]);

      device.pushErrorScope('validation');
      const stepModule = await compile(device, 'Water step', STEP_WGSL);
      const viewModule = await compile(device, 'Water view', VIEW_WGSL);
      const fieldFormat: GPUTextureFormat = 'rgba16float';
      const fieldUsage = GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING;
      const fieldA = device.createTexture({ label: 'Water A', size: [SIM.gridW, rows], format: fieldFormat, usage: fieldUsage });
      const fieldB = device.createTexture({ label: 'Water B', size: [SIM.gridW, rows], format: fieldFormat, usage: fieldUsage });
      const readA = fieldA.createView();
      const readB = fieldB.createView();
      const dropBuf = device.createBuffer({ label: 'Water step, drops', size: STEP_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const plainBuf = device.createBuffer({ label: 'Water step, plain', size: STEP_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const viewBuf = device.createBuffer({ label: 'Water view', size: VIEW_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });

      // the photo covers the screen: the axis that overflows is cropped evenly
      const screen = width / height;
      const picture = bitmap.width / bitmap.height;
      const scaleX = screen > picture ? 1 : screen / picture;
      const scaleY = screen > picture ? picture / screen : 1;
      device.queue.writeBuffer(
        viewBuf,
        0,
        new Float32Array([
          pxW, pxH, pr, 0,
          SIM.gridW, rows, scaleX, scaleY,
          LOOK.refract, LOOK.refractMax, LOOK.chroma, LOOK.normal,
          LOOK.specPower, LOOK.spec, LOOK.caustic, LOOK.shade,
        ]),
      );

      const step = device.createRenderPipeline({ label: 'Water step', layout: 'auto', vertex: { module: stepModule, entryPoint: 'fullVs' }, fragment: { module: stepModule, entryPoint: 'stepFs', targets: [{ format: fieldFormat }] }, primitive: { topology: 'triangle-list' } });
      const view = device.createRenderPipeline({ label: 'Water view', layout: 'auto', vertex: { module: viewModule, entryPoint: 'fullVs' }, fragment: { module: viewModule, entryPoint: 'viewFs', targets: [{ format }] }, primitive: { topology: 'triangle-list' } });
      const samp = device.createSampler({ magFilter: 'linear', minFilter: 'linear', addressModeU: 'clamp-to-edge', addressModeV: 'clamp-to-edge' });
      const stepGroup = (buffer: GPUBuffer, from: GPUTextureView) =>
        device.createBindGroup({
          layout: step.getBindGroupLayout(0),
          entries: [
            { binding: 0, resource: { buffer } },
            { binding: 1, resource: from },
          ],
        });
      const viewGroup = (from: GPUTextureView) =>
        device.createBindGroup({
          layout: view.getBindGroupLayout(0),
          entries: [
            { binding: 0, resource: { buffer: viewBuf } },
            { binding: 1, resource: from },
            { binding: 2, resource: photoTex.createView() },
            { binding: 3, resource: samp },
          ],
        });
      const prepared: Commands = {
        device,
        context,
        dropBuf,
        plainBuf,
        step,
        view,
        targetA: fieldA.createView(),
        targetB: fieldB.createView(),
        fromADrop: stepGroup(dropBuf, readA),
        fromAPlain: stepGroup(plainBuf, readA),
        fromBDrop: stepGroup(dropBuf, readB),
        fromBPlain: stepGroup(plainBuf, readB),
        viewA: viewGroup(readA),
        viewB: viewGroup(readB),
        gridW: SIM.gridW,
        gridH: rows,
      };

      // both textures start as still water
      const clear = device.createCommandEncoder();
      clear.beginRenderPass({ colorAttachments: [{ view: prepared.targetA, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] }).end();
      clear.beginRenderPass({ colorAttachments: [{ view: prepared.targetB, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] }).end();
      device.queue.submit([clear.finish()]);

      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) {
        device.destroy();
        return;
      }

      await runOnUIAsync(
        (target: Commands, install: WebGPU['installWebGPU'], store: typeof commands, w: number, h: number, fmt: GPUTextureFormat) => {
          'worklet';
          install();
          target.context.canvas.width = w;
          target.context.canvas.height = h;
          target.context.configure({ device: target.device, format: fmt, alphaMode: 'premultiplied' });
          store.set(target);
        },
        prepared,
        webgpu.installWebGPU,
        commands,
        pxW,
        pxH,
        format,
      );
      if (!live) {
        device.destroy();
        return;
      }
      draw.setActive(true);
      void device.lost.then(() => {
        if (live) {
          live = false;
          report.current(false);
        }
      });
      cleanup = () => {
        draw.setActive(false);
        commands.set(null);
        try {
          context.unconfigure();
          dropBuf.destroy();
          plainBuf.destroy();
          viewBuf.destroy();
          fieldA.destroy();
          fieldB.destroy();
          photoTex.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Ripple Pond GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cleanup();
    };
  }, [ref, width, height, rows, webgpu, commands, draw, photoAsset]);

  return (
    <Animated.View style={[StyleSheet.absoluteFill, fade]} pointerEvents="none">
      <Canvas ref={ref} style={StyleSheet.absoluteFill} opaque={false} />
    </Animated.View>
  );
}

class Boundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch() {
    this.props.onFailure();
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}
