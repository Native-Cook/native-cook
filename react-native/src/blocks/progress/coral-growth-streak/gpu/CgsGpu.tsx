// Coral Growth Streak — the WebGPU reef.
//
// An untouchable round canvas. Each frame the block's ticker hands over how many simulation steps to
// run, the open radius, the look (level, bloom, phase) and up to eight seeds (cgs-motion.ts); this
// component runs those steps on two ping-ponged textures and draws the result, all encoded on the UI
// thread in the same frame as the views. Optional by design: without the native module (Expo Go,
// web), a software adapter, a setup error or a lost device it reports false and the block falls back
// to CgsSkiaFallback. The setup follows Ripple Pond Photo's, which ran on a device as written.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { SIM } from '../cgs-motion';
import { STEP_FLOATS, STEP_WGSL, VIEW_FLOATS, VIEW_WGSL } from './cgs-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** the ticker's frame: serial, epoch (a change clears the reef), steps, open radius (cells), level, bloom, phase, then eight seeds of x, y, strength, - */
export const FRAME = { serial: 0, epoch: 1, steps: 2, radius: 3, level: 4, bloom: 5, phase: 6, seeds: 7, length: 7 + SIM.seedSlots * 4 } as const;

/** an empty reef: u = 1, v = 0 */
const EMPTY = [1, 0, 0, 1];

/** everything the UI thread needs to step and draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  /** the step's uniforms with the frame's seeds, and without them */
  seedBuf: GPUBuffer;
  plainBuf: GPUBuffer;
  viewBuf: GPUBuffer;
  step: GPURenderPipeline;
  view: GPURenderPipeline;
  /** the two field textures as render targets */
  targetA: GPUTextureView;
  targetB: GPUTextureView;
  /** a step reading A (so writing B) with and without seeds, and the same reading B */
  fromASeed: GPUBindGroup;
  fromAPlain: GPUBindGroup;
  fromBSeed: GPUBindGroup;
  fromBPlain: GPUBindGroup;
  /** the reef drawn from A, and from B */
  viewA: GPUBindGroup;
  viewB: GPUBindGroup;
  pxW: number;
  pxH: number;
  pr: number;
};

type Props = {
  /** written by the block's ticker every frame, see FRAME */
  frame: SharedValue<number[]>;
  /** the canvas's side, pt */
  size: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function CgsGpu(props: Props) {
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
      <Reef {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function Reef({ webgpu, frame, size, onAvailable }: Props & { webgpu: WebGPU }) {
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
  /** the last frame serial that was stepped, and which texture holds the reef now (0 = A) */
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
        // an empty reef: both textures cleared to u = 1, v = 0
        const a = encoder.beginRenderPass({ colorAttachments: [{ view: c.targetA, clearValue: EMPTY, loadOp: 'clear', storeOp: 'store' }] });
        a.end();
        const b = encoder.beginRenderPass({ colorAttachments: [{ view: c.targetB, clearValue: EMPTY, loadOp: 'clear', storeOp: 'store' }] });
        b.end();
      }
      if (steps > 0) {
        const withSeeds = new Float32Array(STEP_FLOATS);
        withSeeds[0] = SIM.feed;
        withSeeds[1] = SIM.kill;
        withSeeds[2] = SIM.grid;
        withSeeds[3] = SIM.seedRadius;
        withSeeds[4] = f[FRAME.radius];
        withSeeds[5] = SIM.wallKill;
        withSeeds[6] = SIM.wallWidth;
        const plain = new Float32Array(withSeeds);
        for (let i = 0; i < SIM.seedSlots * 4; i++) withSeeds[8 + i] = f[FRAME.seeds + i];
        c.device.queue.writeBuffer(c.seedBuf, 0, withSeeds);
        c.device.queue.writeBuffer(c.plainBuf, 0, plain);
        for (let s = 0; s < steps; s++) {
          const fromA = at === 0;
          const pass = encoder.beginRenderPass({ colorAttachments: [{ view: fromA ? c.targetB : c.targetA, clearValue: EMPTY, loadOp: 'clear', storeOp: 'store' }] });
          pass.setPipeline(c.step);
          // the frame's seeds go in with its first step only
          pass.setBindGroup(0, fromA ? (s === 0 ? c.fromASeed : c.fromAPlain) : s === 0 ? c.fromBSeed : c.fromBPlain);
          pass.draw(3);
          pass.end();
          at = 1 - at;
        }
        current.set(at);
      }
      const look = new Float32Array(VIEW_FLOATS);
      look[0] = c.pxW;
      look[1] = c.pxH;
      look[2] = SIM.grid;
      look[3] = c.pr;
      look[4] = f[FRAME.level];
      look[5] = f[FRAME.bloom];
      look[6] = f[FRAME.phase];
      c.device.queue.writeBuffer(c.viewBuf, 0, look);
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
      const px = Math.round(size * pr);

      device.pushErrorScope('validation');
      const stepModule = await compile(device, 'Reef step', STEP_WGSL);
      const viewModule = await compile(device, 'Reef view', VIEW_WGSL);
      const fieldFormat: GPUTextureFormat = 'rgba16float';
      const fieldUsage = GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING;
      const fieldA = device.createTexture({ label: 'Reef A', size: [SIM.grid, SIM.grid], format: fieldFormat, usage: fieldUsage });
      const fieldB = device.createTexture({ label: 'Reef B', size: [SIM.grid, SIM.grid], format: fieldFormat, usage: fieldUsage });
      const readA = fieldA.createView();
      const readB = fieldB.createView();
      const seedBuf = device.createBuffer({ label: 'Reef step, seeds', size: STEP_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const plainBuf = device.createBuffer({ label: 'Reef step, plain', size: STEP_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const viewBuf = device.createBuffer({ label: 'Reef view', size: VIEW_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });

      const step = device.createRenderPipeline({ label: 'Reef step', layout: 'auto', vertex: { module: stepModule, entryPoint: 'fullVs' }, fragment: { module: stepModule, entryPoint: 'stepFs', targets: [{ format: fieldFormat }] }, primitive: { topology: 'triangle-list' } });
      const view = device.createRenderPipeline({ label: 'Reef view', layout: 'auto', vertex: { module: viewModule, entryPoint: 'fullVs' }, fragment: { module: viewModule, entryPoint: 'viewFs', targets: [{ format }] }, primitive: { topology: 'triangle-list' } });
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
            { binding: 2, resource: samp },
          ],
        });
      const prepared: Commands = {
        device,
        context,
        seedBuf,
        plainBuf,
        viewBuf,
        step,
        view,
        targetA: fieldA.createView(),
        targetB: fieldB.createView(),
        fromASeed: stepGroup(seedBuf, readA),
        fromAPlain: stepGroup(plainBuf, readA),
        fromBSeed: stepGroup(seedBuf, readB),
        fromBPlain: stepGroup(plainBuf, readB),
        viewA: viewGroup(readA),
        viewB: viewGroup(readB),
        pxW: px,
        pxH: px,
        pr,
      };

      // both textures start as an empty reef
      const clear = device.createCommandEncoder();
      clear.beginRenderPass({ colorAttachments: [{ view: prepared.targetA, clearValue: EMPTY, loadOp: 'clear', storeOp: 'store' }] }).end();
      clear.beginRenderPass({ colorAttachments: [{ view: prepared.targetB, clearValue: EMPTY, loadOp: 'clear', storeOp: 'store' }] }).end();
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
        px,
        px,
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
          seedBuf.destroy();
          plainBuf.destroy();
          viewBuf.destroy();
          fieldA.destroy();
          fieldB.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Coral Growth GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cleanup();
    };
  }, [ref, size, webgpu, commands, draw]);

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
