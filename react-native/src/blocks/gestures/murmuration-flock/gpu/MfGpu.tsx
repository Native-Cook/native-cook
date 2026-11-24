// Murmuration Flock — the WebGPU canvas.
//
// An untouchable full-screen canvas. Each frame the block's ticker writes one small array (FRAME); this
// component turns it into the uniforms, runs the compute pass over every bird and draws them, all
// encoded on the UI thread in the same frame as the views. Optional by design: without the native
// module (Expo Go, web), a software adapter, a setup error or a lost device it reports false and the
// block falls back to MfSkiaFallback. The setup follows Perfume Ink Bloom's and Coral Growth Streak's.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';

import { FLOCK } from '../mf-motion';
import { BIRD_BYTES, FRAME_FLOATS, MF_WGSL } from './mf-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** the ticker's frame */
export const FRAME = { serial: 0, epoch: 1, t: 2, dt: 3, hold: 4, touchX: 5, touchY: 6, touchOn: 7, length: 8 } as const;

/** everything the UI thread needs to step and draw a frame */
type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  frameBuf: GPUBuffer;
  sim: GPUComputePipeline;
  draw: GPURenderPipeline;
  simGroup: GPUBindGroup;
  drawGroup: GPUBindGroup;
};

type Props = {
  /** written by the block's ticker every frame, see FRAME */
  frame: SharedValue<number[]>;
  width: number;
  height: number;
  /** the flock's centre and one pose unit, pt */
  centreX: number;
  centreY: number;
  unit: number;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function MfGpu(props: Props) {
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
      <Flock {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function Flock({ webgpu, frame, width, height, centreX, centreY, unit, onAvailable }: Props & { webgpu: WebGPU }) {
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
  const seen = useSharedValue(-1);
  const epoch = useSharedValue(0);
  const ready = () => {
    shown.set(withTiming(1, { duration: 220 }));
    report.current(true);
  };
  const fail = () => report.current(false);

  const draw = useFrameCallback(() => {
    const c = commands.get();
    const f = frame.get();
    if (!c || !f || f.length < FRAME.length) return;
    const fresh = f[FRAME.serial] !== seen.get();
    // a still frame (reduced motion) is drawn a few times, then left alone
    if (!fresh && presented.get() >= 3) return;
    try {
      seen.set(f[FRAME.serial]);
      const clear = f[FRAME.epoch] !== epoch.get();
      epoch.set(f[FRAME.epoch]);
      const data = new Float32Array(FRAME_FLOATS);
      data[0] = f[FRAME.t];
      data[1] = fresh ? f[FRAME.dt] : 0;
      data[2] = clear ? 1 : 0;
      data[3] = f[FRAME.hold];
      data[4] = width;
      data[5] = height;
      data[6] = centreX;
      data[7] = centreY;
      data[8] = unit;
      data[12] = f[FRAME.touchX];
      data[13] = f[FRAME.touchY];
      data[14] = f[FRAME.touchOn];
      c.device.queue.writeBuffer(c.frameBuf, 0, data);
      const encoder = c.device.createCommandEncoder();
      const cp = encoder.beginComputePass();
      cp.setPipeline(c.sim);
      cp.setBindGroup(0, c.simGroup);
      cp.dispatchWorkgroups(Math.ceil(FLOCK.count / FLOCK.group));
      cp.end();
      const pass = encoder.beginRenderPass({ colorAttachments: [{ view: c.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
      pass.setPipeline(c.draw);
      pass.setBindGroup(0, c.drawGroup);
      pass.draw(6, FLOCK.count);
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
      const module = device.createShaderModule({ label: 'Flock', code: MF_WGSL });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${m.lineNum}:${m.linePos} ${m.message}`).join('; '));
      const frameBuf = device.createBuffer({ label: 'Flock frame', size: FRAME_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      // zeroed at creation: nobody has been pushed yet
      const birdBuf = device.createBuffer({ label: 'Birds', size: FLOCK.count * BIRD_BYTES, usage: GPUBufferUsage.STORAGE });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const sim = device.createComputePipeline({ label: 'Flock step', layout: 'auto', compute: { module, entryPoint: 'flockCs' } });
      const drawPipe = device.createRenderPipeline({
        label: 'Flock draw',
        layout: 'auto',
        vertex: { module, entryPoint: 'birdVs' },
        fragment: { module, entryPoint: 'birdFs', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const simGroup = device.createBindGroup({
        layout: sim.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 1, resource: { buffer: birdBuf } },
        ],
      });
      const drawGroup = device.createBindGroup({
        layout: drawPipe.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: frameBuf } },
          { binding: 2, resource: { buffer: birdBuf } },
        ],
      });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) {
        device.destroy();
        return;
      }

      const prepared: Commands = { device, context, frameBuf, sim, draw: drawPipe, simGroup, drawGroup };
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
          frameBuf.destroy();
          birdBuf.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Flock GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cleanup();
    };
  }, [ref, width, height, centreX, centreY, unit, webgpu, commands, draw]);

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
