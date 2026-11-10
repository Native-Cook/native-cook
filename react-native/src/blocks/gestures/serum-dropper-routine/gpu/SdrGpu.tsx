// Serum Dropper Routine — the WebGPU oil (data layout and shader resolution by TypeGPU, see
// sdr-wgsl.ts).
//
// A transparent, untouchable canvas over the page, encoded on the UI thread in the same frame as the
// views, from the frame the block's ticker writes. Optional by design: without the native module
// (Expo Go, web), a software adapter, a setup error or a lost device it reports false, and the
// block's flat drop stays.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { resolveShaders } from './sdr-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** everything the UI thread needs to draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  frameBuf: GPUBuffer;
  oil: GPURenderPipeline;
  oilGroup: GPUBindGroup;
};

type Props = {
  /** FrameU as 16 floats, written by the UI thread every frame */
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function SdrGpu(props: Props) {
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
    if (!c || !f || f.length < 16) return;
    try {
      const data = new Float32Array(f);
      c.device.queue.writeBuffer(c.frameBuf, 0, data);
      const encoder = c.device.createCommandEncoder();
      const pass = encoder.beginRenderPass({ colorAttachments: [{ view: c.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
      pass.setPipeline(c.oil);
      pass.setBindGroup(0, c.oilGroup);
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
      const module = device.createShaderModule({ label: 'Oil', code: resolveShaders() });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${m.lineNum}:${m.linePos} ${m.message}`).join('; '));
      const frameBuf = device.createBuffer({ label: 'Oil frame', size: 64, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const oil = device.createRenderPipeline({ layout: 'auto', vertex: { module, entryPoint: 'fullVs' }, fragment: { module, entryPoint: 'oilFs', targets: [{ format, blend }] }, primitive: { topology: 'triangle-list' } });
      const oilGroup = device.createBindGroup({ layout: oil.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: frameBuf } }] });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) return;

      const prepared: Commands = { device, context, frameBuf, oil, oilGroup };
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
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Oil GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
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
