// Aurora Teammates Onboarding — the WebGPU layer: the light dust (shaders and data layout in TypeGPU,
// see ato-dust.ts).
//
// Full screen, transparent, untouchable, over the tgfx canvas; the dust is masked to stay above the
// sheet's edge. TypeGPU writes the WGSL from the TypeScript shaders once on the JS thread; the GPU
// objects are then handed to the UI thread, where every frame (a reaction to the scene) writes one
// 272-byte uniform (the light, the palette, the tilt, the finale's sink, and where each leaving and
// arriving object is and how far along it is), dispatches the compute pass and draws the motes. Optional by design:
// without the native module, a software adapter or any setup error it draws nothing and the block
// stays whole.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry, View } from 'react-native';
import { type SharedValue, useAnimatedReaction, useSharedValue } from 'react-native-reanimated';
import type { CanvasRef, RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync } from 'react-native-worklets';

import { GL, STRIDE } from '../ato-motion';
import type { Layout } from '../ato-theme';
import { COUNT, drawWGSL, entryName, MOTE_BYTES, OBJECTS, PARAM_FLOATS, simulateWGSL, WORKGROUP } from './ato-dust';

type WebGPU = typeof import('react-native-webgpu');

type Engine = {
  device: GPUDevice;
  context: RNCanvasContext;
  simulate: GPUComputePipeline;
  draw: GPURenderPipeline;
  simulateGroup: GPUBindGroup;
  drawGroup: GPUBindGroup;
  params: GPUBuffer;
  ratio: number;
};

/** First object index and count per page in the scene's object table (2 icons, 5 cards, 6 tiles). */
const PAGE_FIRST = [0, 2, 7];
const PAGE_COUNT = [2, 5, 6];

type Props = { scene: SharedValue<number[]>; L: Layout; disabled?: boolean };

export function AtoMotes(props: Props) {
  const [webgpu, setWebgpu] = useState<WebGPU | null>(null);
  useEffect(() => {
    if (props.disabled) return;
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (!TurboModuleRegistry.get('WebGPUModule')) return;
        // The package is only loaded once its native module is known to exist.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        setWebgpu(require('react-native-webgpu') as WebGPU);
      } catch {
        // No dust; the rest of the block is unaffected.
      }
    });
    return () => {
      live = false;
    };
  }, [props.disabled]);
  if (!webgpu || props.disabled) return null;
  return (
    <Boundary>
      <Field {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function dustFrame(e: Engine, values: Float32Array) {
  'worklet';
  e.device.queue.writeBuffer(e.params, 0, values);
  const encoder = e.device.createCommandEncoder();
  const step = encoder.beginComputePass();
  step.setPipeline(e.simulate);
  step.setBindGroup(0, e.simulateGroup);
  step.dispatchWorkgroups(Math.ceil(COUNT / WORKGROUP));
  step.end();
  const pass = encoder.beginRenderPass({
    colorAttachments: [{ view: e.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }],
  });
  pass.setPipeline(e.draw);
  pass.setBindGroup(0, e.drawGroup);
  pass.draw(6, COUNT);
  pass.end();
  e.device.queue.submit([encoder.finish()]);
  e.context.present();
}

function Field({ webgpu, scene, L }: Props & { webgpu: WebGPU }) {
  const { Canvas } = webgpu;
  const canvas = useRef<CanvasRef>(null);
  const [laidOut, setLaidOut] = useState(false);
  const engine = useSharedValue<Engine | null>(null);

  useAnimatedReaction(
    () => scene.get(),
    (sc) => {
      const e = engine.get();
      if (!e) return;
      const k = e.ratio;
      const v = new Float32Array(PARAM_FLOATS);
      v[0] = L.W * k;
      v[1] = L.H * k;
      v[2] = k;
      v[3] = (sc[GL.phase] / (2 * Math.PI)) * 9;
      v[4] = sc[GL.coreX] * k;
      v[5] = sc[GL.coreY] * k;
      v[6] = sc[GL.flare];
      v[7] = sc[GL.u];
      v[8] = sc[GL.w0];
      v[9] = sc[GL.w1];
      v[10] = sc[GL.w2];
      v[11] = sc[GL.sheetTop] * k;
      v[12] = L.notch.half * k;
      v[13] = sc[GL.dip] * k;
      v[14] = sc[GL.tiltX] * 10 * k;
      v[15] = sc[GL.tiltY] * 7 * k;
      const finale = sc[GL.sinkMix] > 0;
      v[16] = sc[GL.sinkX] * k;
      v[17] = sc[GL.sinkY] * k;
      v[18] = finale ? 1 : 0;
      // where the leaving and arriving objects are now, and how far along each is
      const moving = sc[GL.u] > 0 || finale;
      const from = sc[GL.from];
      const to = sc[GL.to];
      for (let j = 0; j < OBJECTS; j++) {
        const a = 20 + j * 4;
        const b = 20 + OBJECTS * 4 + j * 4;
        if (finale) {
          // the tiles' light streams into the button, then bursts out of it in a fountain
          const o = (PAGE_FIRST[2] + j) * STRIDE;
          v[a] = sc[o] * k;
          v[a + 1] = sc[o + 1] * k;
          v[a + 2] = Math.max(sc[o + 6], sc[o + 7], 18) * sc[o + 4] * k;
          v[a + 3] = sc[o + 11];
          const ang = Math.PI * (0.1 + (0.8 * j) / (OBJECTS - 1));
          v[b] = (sc[GL.sinkX] + Math.cos(ang) * L.W * 0.4) * k;
          v[b + 1] = (sc[GL.sinkY] - Math.sin(ang) * L.S * 70) * k;
          v[b + 2] = 26 * L.S * k;
          v[b + 3] = Math.min(1, Math.max(0, (sc[GL.burst] - 0.05 * Math.abs(j - 2.5)) / 0.8));
          continue;
        }
        if (moving && j < PAGE_COUNT[from]) {
          const o = (PAGE_FIRST[from] + j) * STRIDE;
          v[a] = sc[o] * k;
          v[a + 1] = sc[o + 1] * k;
          v[a + 2] = Math.max(sc[o + 6], sc[o + 7], 18) * sc[o + 4] * k;
          v[a + 3] = sc[o + 11];
        }
        if (moving && j < PAGE_COUNT[to]) {
          const o = (PAGE_FIRST[to] + j) * STRIDE;
          v[b] = sc[o] * k;
          v[b + 1] = sc[o + 1] * k;
          v[b + 2] = Math.max(sc[o + 6], sc[o + 7], 18) * sc[o + 4] * k;
          v[b + 3] = sc[o + 12];
        }
      }
      try {
        dustFrame(e, v);
      } catch {
        engine.set(null);
      }
    },
  );

  useEffect(() => {
    if (!laidOut) return;
    let live = true;
    let device: GPUDevice | undefined;
    const start = async () => {
      webgpu.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) return;
      const adapter = await gpu.requestAdapter();
      if (!live || !adapter || adapter.info?.isFallbackAdapter) return;
      device = await adapter.requestDevice();
      if (!live) return;
      const ratio = Math.min(PixelRatio.get(), 2);
      const format = gpu.getPreferredCanvasFormat();
      device.pushErrorScope('validation');
      // TypeGPU turns the TypeScript shaders into WGSL here, once, off the UI thread
      const simCode = simulateWGSL();
      const drawCode = drawWGSL();
      const simModule = device.createShaderModule({ label: 'ATO dust simulation', code: simCode });
      const drawModule = device.createShaderModule({ label: 'ATO dust draw', code: drawCode });
      for (const m of [simModule, drawModule]) {
        const info = await m.getCompilationInfo();
        const errors = info.messages.filter((x) => x.type === 'error');
        if (errors.length) throw new Error(errors.map((x) => `${x.lineNum}:${x.linePos} ${x.message}`).join('; '));
      }
      const simulate = await device.createComputePipelineAsync({
        label: 'ATO dust simulation',
        layout: 'auto',
        compute: { module: simModule, entryPoint: entryName(simCode, 'compute') },
      });
      const draw = await device.createRenderPipelineAsync({
        label: 'ATO dust draw',
        layout: 'auto',
        vertex: { module: drawModule, entryPoint: entryName(drawCode, 'vertex') },
        fragment: {
          module: drawModule,
          entryPoint: entryName(drawCode, 'fragment'),
          targets: [
            {
              format,
              blend: {
                color: { srcFactor: 'one', dstFactor: 'one', operation: 'add' },
                alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
              },
            },
          ],
        },
        primitive: { topology: 'triangle-list' },
      });
      const params = device.createBuffer({
        label: 'ATO dust params',
        size: PARAM_FLOATS * 4,
        usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
      });
      const motes = device.createBuffer({ label: 'ATO dust motes', size: COUNT * MOTE_BYTES, usage: GPUBufferUsage.STORAGE });
      const simulateGroup = device.createBindGroup({
        layout: simulate.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: params } },
          { binding: 1, resource: { buffer: motes } },
        ],
      });
      const drawGroup = device.createBindGroup({
        layout: draw.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: { buffer: params } },
          { binding: 1, resource: { buffer: motes } },
        ],
      });
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      const context = canvas.current?.getContext('webgpu') ?? null;
      if (!context || !live) return;
      const prepared: Engine = { device, context, simulate, draw, simulateGroup, drawGroup, params, ratio };
      await runOnUIAsync(
        (target: Engine, install: WebGPU['installWebGPU'], stored: typeof engine, w: number, h: number, fmt: GPUTextureFormat) => {
          'worklet';
          install();
          target.context.canvas.width = w;
          target.context.canvas.height = h;
          target.context.configure({ device: target.device, format: fmt, alphaMode: 'premultiplied' });
          stored.set(target);
        },
        prepared,
        webgpu.installWebGPU,
        engine,
        Math.round(L.W * ratio),
        Math.round(L.H * ratio),
        format,
      );
      if (__DEV__) console.info('[ATO dust] TypeGPU + WebGPU ready', { simulate: entryName(simCode, 'compute'), format });
    };
    void start().catch((error) => {
      if (__DEV__) console.info('[ATO dust unavailable]', error instanceof Error ? error.message : String(error));
    });
    return () => {
      live = false;
      void runOnUIAsync((stored: typeof engine) => {
        'worklet';
        stored.set(null);
      }, engine)
        .catch(() => {})
        .finally(() => {
          try {
            device?.destroy();
          } catch {
            // already lost
          }
        });
    };
  }, [L, engine, laidOut, webgpu]);

  return (
    <View pointerEvents="none" accessible={false} style={StyleSheet.absoluteFill}>
      <Canvas ref={canvas} pointerEvents="none" opaque={false} style={{ width: L.W, height: L.H }} onLayout={() => setLaidOut(true)} />
    </View>
  );
}

class Boundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}
