// Vitamin Capsule Dose — the WebGPU hero (data layouts and shader resolution by TypeGPU, see
// vcd-wgsl.ts): the morphing glass object and the beads it streams into the list.
//
// A transparent, untouchable canvas over the whole screen (the beads fly to the list), encoded on the UI
// thread in the same frame as the app's views, from the hero frame the block's ticker writes. Optional by design: without the native module
// (Expo Go, web), a software adapter, a setup error or a lost device it reports false, and the
// block's flat Skia hero stays.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { BEADS, resolveShaders, SPILL } from './vcd-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** everything the UI thread needs to draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  frameBuf: GPUBuffer;
  hero: GPURenderPipeline;
  bead: GPURenderPipeline;
  comp: GPUComputePipeline;
  heroGroup: GPUBindGroup;
  beadGroup: GPUBindGroup;
  compGroup: GPUBindGroup;
  pearls: GPUComputePipeline;
  pearlGroup: GPUBindGroup;
};

type Props = {
  /** FrameU as 48 floats, written by the UI thread every frame */
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function VcdGpu(props: Props) {
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

function Hero({ webgpu, frame, width, height, onAvailable }: Props & { webgpu: WebGPU }) {
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
  const ready = () => {
    shown.set(withTiming(1, { duration: 220 }));
    setTimeout(() => report.current(true), 260);
  };
  const fail = () => report.current(false);
  const draw = useFrameCallback(() => {
    const c = commands.get();
    const f = frame.get();
    if (!c || !f || f.length < 48) return;
    try {
      const data = new Float32Array(f);
      c.device.queue.writeBuffer(c.frameBuf, 0, data);
      const encoder = c.device.createCommandEncoder();
      const taking = data[24] > 0;
      // the pearls are rigid balls with state: one workgroup steps them every frame
      const cp = encoder.beginComputePass();
      if (taking) {
        cp.setPipeline(c.comp);
        cp.setBindGroup(0, c.compGroup);
        cp.dispatchWorkgroups(Math.ceil(BEADS / 64));
      }
      cp.setPipeline(c.pearls);
      cp.setBindGroup(0, c.pearlGroup);
      cp.dispatchWorkgroups(1);
      cp.end();
      const pass = encoder.beginRenderPass({ colorAttachments: [{ view: c.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
      pass.setPipeline(c.hero);
      pass.setBindGroup(0, c.heroGroup);
      pass.draw(3);
      pass.setPipeline(c.bead);
      pass.setBindGroup(0, c.beadGroup);
      pass.draw(6, BEADS);
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
      const module = device.createShaderModule({ label: 'Vitamin hero', code: resolveShaders() });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${m.lineNum}:${m.linePos} ${m.message}`).join('; '));
      const frameBuf = device.createBuffer({ label: 'Vitamin frame', size: 192, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const beadBuf = device.createBuffer({ label: 'Vitamin beads', size: BEADS * 32, usage: GPUBufferUsage.STORAGE });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const hero = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'fullVs' },
        fragment: { module, entryPoint: 'heroFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const bead = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'beadVs' },
        fragment: { module, entryPoint: 'beadFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const comp = device.createComputePipeline({ layout: 'auto', compute: { module, entryPoint: 'beadsCs' } });
      const pearls = device.createComputePipeline({ layout: 'auto', compute: { module, entryPoint: 'pearlsCs' } });
      const pearlBuf = device.createBuffer({ label: 'Vitamin pearls', size: SPILL * 32, usage: GPUBufferUsage.STORAGE });
      const heroGroup = device.createBindGroup({ layout: hero.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: frameBuf } }] });
      const beadGroup = device.createBindGroup({
        layout: bead.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 2, resource: { buffer: beadBuf } },
        ],
      });
      const compGroup = device.createBindGroup({
        layout: comp.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 1, resource: { buffer: beadBuf } },
        ],
      });
      const pearlGroup = device.createBindGroup({
        layout: pearls.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 1, resource: { buffer: beadBuf } },
          { binding: 3, resource: { buffer: pearlBuf } },
        ],
      });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) return;

      const prepared: Commands = { device, context, frameBuf, hero, bead, comp, heroGroup, beadGroup, compGroup, pearls, pearlGroup };
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
          beadBuf.destroy();
          pearlBuf.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Vitamin GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
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
