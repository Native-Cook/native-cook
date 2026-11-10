// Perfume Ink Bloom — the WebGPU bottle and mist (data layouts and shader resolution by TypeGPU, see
// pib-wgsl.ts).
//
// A transparent, untouchable canvas over the page, encoded on the UI thread in the same frame as the
// views, from the frame the block's ticker writes. The notes' points are written when they change.
// Optional by design: without the native module (Expo Go, web), a software adapter, a setup error or
// a lost device it reports false, and the block's flat Skia bottle stays.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { DROPS, resolveShaders } from './pib-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** everything the UI thread needs to draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  frameBuf: GPUBuffer;
  noteBuf: GPUBuffer;
  bottle: GPURenderPipeline;
  drop: GPURenderPipeline;
  sim: GPUComputePipeline;
  bottleGroup: GPUBindGroup;
  dropGroup: GPUBindGroup;
  simGroup: GPUBindGroup;
};

type Props = {
  /** the notes' points (x, y per droplet, screen pt) */
  notes: Float32Array | null;
  /** FrameU as 16 floats, written by the UI thread every frame */
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function PibGpu(props: Props) {
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
      <Hero {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function Hero({ webgpu, frame, notes, width, height, onAvailable }: Props & { webgpu: WebGPU }) {
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
  // the notes' points, handed to the UI thread and written there when they change
  const notesSV = useSharedValue<number[] | null>(null);
  const notesVer = useSharedValue(0);
  const written = useSharedValue(-1);
  useEffect(() => {
    if (!notes) return;
    notesSV.set(Array.from(notes));
    notesVer.set(notesVer.get() + 1);
  }, [notes, notesSV, notesVer]);

  const ready = () => {
    shown.set(withTiming(1, { duration: 220 }));
    setTimeout(() => report.current(true), 260);
  };
  const fail = () => report.current(false);
  const draw = useFrameCallback(() => {
    const c = commands.get();
    const f = frame.get();
    if (!c || !f || f.length < 16) return;
    try {
      const data = new Float32Array(f);
      c.device.queue.writeBuffer(c.frameBuf, 0, data);
      const nv = notesVer.get();
      const np = notesSV.get();
      if (np && nv !== written.get()) {
        c.device.queue.writeBuffer(c.noteBuf, 0, new Float32Array(np));
        written.set(nv);
      }
      const encoder = c.device.createCommandEncoder();
      const cp = encoder.beginComputePass();
      cp.setPipeline(c.sim);
      cp.setBindGroup(0, c.simGroup);
      cp.dispatchWorkgroups(Math.ceil(DROPS / 64));
      cp.end();
      const pass = encoder.beginRenderPass({ colorAttachments: [{ view: c.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
      pass.setPipeline(c.bottle);
      pass.setBindGroup(0, c.bottleGroup);
      pass.draw(3);
      pass.setPipeline(c.drop);
      pass.setBindGroup(0, c.dropGroup);
      pass.draw(6, DROPS);
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

      device.pushErrorScope('validation');
      const module = device.createShaderModule({ label: 'Perfume', code: resolveShaders() });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${m.lineNum}:${m.linePos} ${m.message}`).join('; '));
      const frameBuf = device.createBuffer({ label: 'Perfume frame', size: 64, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const dropBuf = device.createBuffer({ label: 'Mist', size: DROPS * 32, usage: GPUBufferUsage.STORAGE });
      const noteBuf = device.createBuffer({ label: 'Notes', size: DROPS * 8, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const bottle = device.createRenderPipeline({ layout: 'auto', vertex: { module, entryPoint: 'fullVs' }, fragment: { module, entryPoint: 'bottleFs', targets: [{ format, blend }] }, primitive: { topology: 'triangle-list' } });
      const drop = device.createRenderPipeline({ layout: 'auto', vertex: { module, entryPoint: 'dropVs' }, fragment: { module, entryPoint: 'dropFs', targets: [{ format, blend }] }, primitive: { topology: 'triangle-list' } });
      const sim = device.createComputePipeline({ layout: 'auto', compute: { module, entryPoint: 'mistCs' } });
      const bottleGroup = device.createBindGroup({ layout: bottle.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: frameBuf } }] });
      const dropGroup = device.createBindGroup({
        layout: drop.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 2, resource: { buffer: dropBuf } },
        ],
      });
      const simGroup = device.createBindGroup({
        layout: sim.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 1, resource: { buffer: dropBuf } },
          { binding: 3, resource: { buffer: noteBuf } },
        ],
      });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) return;

      const prepared: Commands = { device, context, frameBuf, noteBuf, bottle, drop, sim, bottleGroup, dropGroup, simGroup };
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
        Math.round(width * pr),
        Math.round(height * pr),
        format,
      );
      if (!live) return;
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
          frameBuf.destroy();
          dropBuf.destroy();
          noteBuf.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Perfume GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cleanup();
    };
  }, [ref, width, height, webgpu, commands, draw]);

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
