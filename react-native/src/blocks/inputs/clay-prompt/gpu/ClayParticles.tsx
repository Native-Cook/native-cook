import { Component, useEffect, useRef, useState, type ReactNode } from 'react';
import { AppState, PixelRatio, Platform, StyleSheet, TurboModuleRegistry } from 'react-native';
import { type SharedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync } from 'react-native-worklets';

import { look } from '../cpr-motion';
import { COUNT, drawWGSL, entryName, simulateWGSL, SLOTS, WORKGROUP } from './clay-dust';

/*
 The dust layer: a transparent WebGPU canvas over the clay, simulated and drawn on the UI thread.
 Optional by design. Without the native module (Expo Go, web), a software adapter, a setup error or
 a lost device, nothing is drawn and the block is complete without it: the clay, the melt and the
 picture are all Skia.
*/

type WebGPU = typeof import('react-native-webgpu');
const pixelRatio = PixelRatio.get(); // read here: PixelRatio does not exist on the UI runtime

export type ClayParticlesProps = {
  /** A burst: from x, y, to x, y (pt), kind (−1 = the swirl round the blob), start time. */
  emit: SharedValue<number[]>;
  g: SharedValue<number>;
  clear: SharedValue<number>;
  blobX: number;
  blobY: number;
  width: number;
  height: number;
  enabled: boolean;
};

type Commands = {
  device: GPUDevice;
  context: RNCanvasContext;
  simulate: GPUComputePipeline;
  simulateGroup: GPUBindGroup;
  draw: GPURenderPipeline;
  drawGroup: GPUBindGroup;
  params: GPUBuffer;
  generation: number;
};

export function ClayParticles(props: ClayParticlesProps) {
  const [api, setApi] = useState<WebGPU | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!props.enabled) return;
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (Platform.OS !== 'web' && !TurboModuleRegistry.get('WebGPUModule')) {
          setFailed(true);
          return;
        }
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const native = require('react-native-webgpu') as WebGPU;
        if (live) setApi(native);
      } catch {
        setFailed(true);
      }
    });
    return () => {
      live = false;
    };
  }, [props.enabled]);
  if (!props.enabled || !api || failed) return null;
  return (
    <Boundary onFailure={() => setFailed(true)}>
      <NativeDust {...props} webgpu={api} />
    </Boundary>
  );
}

function NativeDust({ webgpu, emit, g, clear, blobX, blobY, width, height }: ClayParticlesProps & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const canvas = useCanvasRef();
  const [laidOut, setLaidOut] = useState(false);
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  const commands = useSharedValue<Commands | null>(null);
  const lastSpawn = useSharedValue(-1);
  const spawned = useSharedValue(0);
  const time = useSharedValue(0);
  const busyUntil = useSharedValue(0);
  const token = useRef(0);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setAppActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const frame = useFrameCallback((f) => {
    const c = commands.get();
    if (!c) return;
    const dt = Math.min(0.033, (f.timeSincePreviousFrame ?? 16) / 1000);
    time.set(time.get() + dt);
    const e = emit.get();
    let slot = -1;
    if (e[5] !== lastSpawn.get()) {
      lastSpawn.set(e[5]);
      slot = spawned.get() % SLOTS;
      spawned.set(spawned.get() + 1);
      busyUntil.set(time.get() + 3.5);
    }
    const gNow = g.get();
    const moving = gNow > 0.15 && gNow < 0.8;
    if (moving) busyUntil.set(Math.max(busyUntil.get(), time.get() + 2.5));
    // nothing alive: one last cleared frame was already presented, so rest
    if (time.get() > busyUntil.get() + 0.1) return;
    try {
      const l = look(gNow);
      const values = new Float32Array([
        width, height, pixelRatio, time.get(),
        dt, slot, e[4], gNow,
        e[0], e[1], e[2], e[3],
        blobX, blobY, l.blobR, clear.get(),
      ]);
      c.device.queue.writeBuffer(c.params, 0, values);
      const encoder = c.device.createCommandEncoder({ label: 'Clay dust frame' });
      const sim = encoder.beginComputePass({ label: 'Clay dust simulation' });
      sim.setPipeline(c.simulate);
      sim.setBindGroup(0, c.simulateGroup);
      sim.dispatchWorkgroups(Math.ceil(COUNT / WORKGROUP));
      sim.end();
      const pass = encoder.beginRenderPass({
        colorAttachments: [{ view: c.context.getCurrentTexture().createView(), loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }],
      });
      pass.setPipeline(c.draw);
      pass.setBindGroup(0, c.drawGroup);
      pass.draw(6, COUNT);
      pass.end();
      c.device.queue.submit([encoder.finish()]);
      c.context.present();
    } catch (error) {
      console.info(`[Clay dust frame failed] ${String(error)}`);
      commands.set(null);
    }
  }, false);

  useEffect(() => {
    if (!laidOut || !appActive) return;
    let live = true;
    const generation = ++token.current;
    let device: GPUDevice | undefined;
    let buffers: GPUBuffer[] = [];
    let context: RNCanvasContext | null = null;
    const start = async () => {
      webgpu.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('navigator.gpu is missing');
      const adapter = await gpu.requestAdapter();
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('no hardware adapter');
      device = await adapter.requestDevice();
      if (!live) return;
      const format = gpu.getPreferredCanvasFormat();
      device.pushErrorScope('validation');
      // TypeGPU writes the WGSL from the TypeScript shaders once, here, off the UI thread
      const simCode = simulateWGSL();
      const drawCode = drawWGSL();
      const simModule = device.createShaderModule({ label: 'Clay dust simulation', code: simCode });
      const drawModule = device.createShaderModule({ label: 'Clay dust draw', code: drawCode });
      for (const m of [simModule, drawModule]) {
        const info = await m.getCompilationInfo();
        const errors = info.messages.filter((x) => x.type === 'error');
        if (errors.length) throw new Error(errors.map((x) => x.message).join('; '));
      }
      const simulate = await device.createComputePipelineAsync({ label: 'Clay dust simulation', layout: 'auto', compute: { module: simModule, entryPoint: entryName(simCode, 'compute') } });
      const draw = await device.createRenderPipelineAsync({
        label: 'Clay dust draw',
        layout: 'auto',
        vertex: { module: drawModule, entryPoint: entryName(drawCode, 'vertex') },
        fragment: {
          module: drawModule,
          entryPoint: entryName(drawCode, 'fragment'),
          targets: [{ format, blend: { color: { srcFactor: 'one', dstFactor: 'one', operation: 'add' }, alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' } } }],
        },
        primitive: { topology: 'triangle-list' },
      });
      const params = device.createBuffer({ label: 'Clay dust params', size: 64, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
      const particles = device.createBuffer({ label: 'Clay dust particles', size: COUNT * 48, usage: GPUBufferUsage.STORAGE });
      buffers = [params, particles];
      const simulateGroup = device.createBindGroup({ layout: simulate.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: params } }, { binding: 1, resource: { buffer: particles } }] });
      const drawGroup = device.createBindGroup({ layout: draw.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: params } }, { binding: 1, resource: { buffer: particles } }] });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      context = canvas.current?.getContext('webgpu') ?? null;
      if (!context || !live) throw new Error('canvas is not mounted');
      const scale = Math.min(pixelRatio, 2);
      const prepared: Commands = { device, context, simulate, simulateGroup, draw, drawGroup, params, generation };
      await runOnUIAsync(
        (target: Commands, install: WebGPU['installWebGPU'], store: typeof commands, w: number, h: number) => {
          'worklet';
          install();
          target.context.canvas.width = w;
          target.context.canvas.height = h;
          target.context.configure({ device: target.device, format, alphaMode: 'premultiplied' });
          store.set(target);
        },
        prepared,
        webgpu.installWebGPU,
        commands,
        Math.round(width * scale),
        Math.round(height * scale),
      );
      if (!live) return;
      if (__DEV__) console.info('[Clay dust] WebGPU ready', { simulate: entryName(simCode, 'compute'), format });
      frame.setActive(true);
    };
    const fail = (error?: unknown) => {
      if (__DEV__) console.info(`[Clay dust unavailable] ${error instanceof Error ? error.message : String(error)}`);
      if (token.current === generation) commands.set(null);
    };
    void start().catch(fail);
    device?.lost.then(fail).catch(() => {});
    return () => {
      live = false;
      frame.setActive(false);
      void runOnUIAsync((store: typeof commands, ctx: RNCanvasContext | null) => {
        'worklet';
        store.set(null);
        try {
          ctx?.unconfigure();
        } catch {
          /* already detached */
        }
      }, commands, context)
        .catch(() => {})
        .finally(() => {
          for (const b of buffers) {
            try {
              b.destroy();
            } catch {
              /* lost */
            }
          }
          try {
            device?.destroy();
          } catch {
            /* lost */
          }
        });
    };
  }, [laidOut, appActive, webgpu, canvas, commands, frame, width, height]);

  return <Canvas ref={canvas} opaque={false} pointerEvents="none" style={[StyleSheet.absoluteFill]} onLayout={() => setLaidOut(true)} />;
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
