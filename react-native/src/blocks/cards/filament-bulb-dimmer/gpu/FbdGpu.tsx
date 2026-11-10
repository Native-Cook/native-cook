// Filament Bulb Dimmer — the WebGPU layer: the room, the cord, the 3D bulb, the dust and the bloom
// (data layouts and shader resolution by TypeGPU, see fbd-wgsl.ts).
//
// Full screen, opaque once it draws, untouchable, under the app's views. It renders every frame from
// the scene the UI thread publishes. Optional by design: without the native module (Expo Go, web), a
// software adapter, a setup error or a lost device it reports false, and the Skia scene under it
// stays.

import { Component, type ReactNode, useEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, TurboModuleRegistry } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { d, tgpu } from 'typegpu';

import type { Layout } from '../fbd-theme';
import { FrameU, HISTORY, HistU, MOTES, resolveShaders, seedMotes } from './fbd-wgsl';

type WebGPU = typeof import('react-native-webgpu');

/** what the UI thread publishes every frame, by index */
export const S = {
  t: 0,
  hx: 1,
  hy: 2,
  tilt: 3,
  twist: 4,
  sag: 5,
  heat: 6,
  lum: 7,
  E: 8,
  lr: 9,
  on: 12,
  ar: 13,
  fx: 16,
  fy: 17,
  size: 18,
} as const;

type Props = {
  scene: SharedValue<number[]>;
  L: Layout;
  dialR: number;
  /** Display P3 output (the surface is tagged P3) */
  p3: boolean;
  onAvailable: (ok: boolean) => void;
  disabled?: boolean;
};

export function FbdGpu(props: Props) {
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
      <Scene {...props} webgpu={webgpu} />
    </Boundary>
  );
}

function Scene({ webgpu, scene, L, dialR, p3, onAvailable }: Props & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const ref = useCanvasRef();
  const report = useRef(onAvailable);
  useEffect(() => {
    report.current = onAvailable;
  });
  const shown = useSharedValue(0);
  const fade = useAnimatedStyle(() => ({ opacity: shown.get() }));

  useEffect(() => {
    let live = true;
    let frame = 0;
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
      const W = Math.round(L.w * pr);
      const H = Math.round(L.h * pr);
      const format = nav.gpu!.getPreferredCanvasFormat();
      context.canvas.width = W;
      context.canvas.height = H;
      context.configure({ device, format, alphaMode: 'premultiplied' });

      const root = tgpu.initFromDevice({ device });
      const frameBuf = root.createBuffer(FrameU).$usage('uniform');
      const histBuf = root.createBuffer(HistU).$usage('uniform');
      device.pushErrorScope('validation');
      const code = resolveShaders();
      const module = device.createShaderModule({ label: 'Filament bulb', code });
      const info = await module.getCompilationInfo();
      const errors = info.messages.filter((m) => m.type === 'error');
      if (errors.length) throw new Error(errors.map((m) => `${m.lineNum}:${m.linePos} ${m.message}`).join('; '));

      const fx0 = L.anchorX;
      const fy0 = L.restY + 2.55 * L.R;
      const zl = 2.4 * L.R;
      const homes = seedMotes(L.w, L.anchorY - 20, L.arcCy, fx0, fy0, L.R, zl);
      const motes = device.createBuffer({ label: 'Motes', size: homes.byteLength, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST });
      device.queue.writeBuffer(motes, 0, homes);
      const sparks = device.createBuffer({ label: 'Sparks', size: MOTES * 32, usage: GPUBufferUsage.STORAGE });

      const hdrFormat: GPUTextureFormat = 'rgba16float';
      const tex = (w: number, h: number) =>
        device.createTexture({ size: [Math.max(1, w), Math.max(1, h)], format: hdrFormat, usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
      const hdr = tex(W, H);
      const LEVELS = 6;
      const downs: GPUTexture[] = [];
      const ups: GPUTexture[] = [];
      for (let i = 0; i < LEVELS; i++) {
        const w = Math.ceil(W / 2 ** (i + 1));
        const h = Math.ceil(H / 2 ** (i + 1));
        downs.push(tex(w, h));
        if (i < LEVELS - 1) ups.push(tex(w, h));
      }
      const samp = device.createSampler({ magFilter: 'linear', minFilter: 'linear', addressModeU: 'clamp-to-edge', addressModeV: 'clamp-to-edge' });

      const full = (fs: string, target: GPUTextureFormat, blend?: GPUBlendState) =>
        device.createRenderPipeline({
          layout: 'auto',
          vertex: { module, entryPoint: 'fullVs' },
          fragment: { module, entryPoint: fs, targets: [{ format: target, ...(blend ? { blend } : {}) }] },
          primitive: { topology: 'triangle-list' },
        });
      const scenePipe = full('sceneFs', hdrFormat);
      const downFirst = full('downFirstFs', hdrFormat);
      const downPipe = full('downFs', hdrFormat);
      const upPipe = full('upFs', hdrFormat);
      const outPipe = full('outFs', format);
      const add: GPUBlendState = { color: { srcFactor: 'one', dstFactor: 'one', operation: 'add' }, alpha: { srcFactor: 'one', dstFactor: 'one', operation: 'add' } };
      const dustPipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module, entryPoint: 'dustVs' },
        fragment: { module, entryPoint: 'dustFs', targets: [{ format: hdrFormat, blend: add }] },
        primitive: { topology: 'triangle-list' },
      });
      const computePipe = device.createComputePipeline({ layout: 'auto', compute: { module, entryPoint: 'dustCs' } });

      const frameRes = { buffer: root.unwrap(frameBuf) };
      const histRes = { buffer: root.unwrap(histBuf) };
      const sceneGroup = device.createBindGroup({ layout: scenePipe.getBindGroupLayout(0), entries: [{ binding: 0, resource: frameRes }] });
      const computeGroup = device.createBindGroup({
        layout: computePipe.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: frameRes },
          { binding: 1, resource: histRes },
          { binding: 2, resource: { buffer: motes } },
          { binding: 3, resource: { buffer: sparks } },
        ],
      });
      const dustGroup = device.createBindGroup({
        layout: dustPipe.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: frameRes },
          { binding: 4, resource: { buffer: sparks } },
        ],
      });
      const srcGroup = (pipe: GPURenderPipeline, src: GPUTexture) =>
        device.createBindGroup({ layout: pipe.getBindGroupLayout(0), entries: [{ binding: 5, resource: src.createView() }, { binding: 7, resource: samp }] });
      const pairGroup = (pipe: GPURenderPipeline, src: GPUTexture, low: GPUTexture, withFrame: boolean) =>
        device.createBindGroup({
          layout: pipe.getBindGroupLayout(0),
          entries: [
            ...(withFrame ? [{ binding: 0, resource: frameRes }] : []),
            { binding: 5, resource: src.createView() },
            { binding: 6, resource: low.createView() },
            { binding: 7, resource: samp },
          ],
        });
      const downGroups = downs.map((_, i) => (i === 0 ? srcGroup(downFirst, hdr) : srcGroup(downPipe, downs[i - 1])));
      // up[i] = down[i] + tent(lower), the lower being up[i + 1], or the last down level
      const upGroups = ups.map((_, i) => pairGroup(upPipe, downs[i], i === LEVELS - 2 ? downs[LEVELS - 1] : ups[i + 1], false));
      const outGroup = pairGroup(outPipe, hdr, ups[0], true);
      const error = await device.popErrorScope();
      if (error) throw new Error(error.message);
      if (!live) return;

      const hdrView = hdr.createView();
      const downViews = downs.map((t) => t.createView());
      const upViews = ups.map((t) => t.createView());
      const post = (encoder: GPUCommandEncoder, pipe: GPURenderPipeline, group: GPUBindGroup, view: GPUTextureView) => {
        const pass = encoder.beginRenderPass({ colorAttachments: [{ view, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] });
        pass.setPipeline(pipe);
        pass.setBindGroup(0, group);
        pass.draw(3);
        pass.end();
      };

      const history = Array.from({ length: HISTORY }, () => d.vec4f(fx0, fy0, 0, 0));
      const v4 = (a: number, b: number, c: number, e: number) => d.vec4f(a, b, c, e);
      let presented = 0;
      const render = () => {
        const s = scene.get();
        if (!s || s.length < S.size) return;
        history.pop();
        history.unshift(d.vec4f(s[S.fx], s[S.fy], 0, 0));
        frameBuf.write({
          view: v4(L.w, L.h, pr, s[S.t]),
          anchor: v4(L.anchorX, L.anchorY, L.R, zl),
          bulb: v4(s[S.hx], s[S.hy], s[S.tilt], s[S.twist]),
          glow: v4(s[S.sag], s[S.heat], s[S.lum], s[S.E]),
          light: v4(s[S.lr], s[S.lr + 1], s[S.lr + 2], s[S.on]),
          accent: v4(s[S.ar], s[S.ar + 1], s[S.ar + 2], p3 ? 1 : 0),
          arc: v4(L.arcCx, L.arcCy, L.rOut, L.rIn),
          card: v4(L.card.x, L.card.y, L.card.w, L.card.h),
          chips: v4(L.chips.l, L.chips.r, L.chips.y, L.chips.w),
          misc: v4(L.chips.h, dialR, L.leg, 2.4),
        });
        histBuf.write(history);
        const encoder = device.createCommandEncoder();
        const cp = encoder.beginComputePass();
        cp.setPipeline(computePipe);
        cp.setBindGroup(0, computeGroup);
        cp.dispatchWorkgroups(Math.ceil(MOTES / 64));
        cp.end();
        const pass = encoder.beginRenderPass({ colorAttachments: [{ view: hdrView, clearValue: [0, 0, 0, 1], loadOp: 'clear', storeOp: 'store' }] });
        pass.setPipeline(scenePipe);
        pass.setBindGroup(0, sceneGroup);
        pass.draw(3);
        pass.setPipeline(dustPipe);
        pass.setBindGroup(0, dustGroup);
        pass.draw(6, MOTES);
        pass.end();
        for (let i = 0; i < LEVELS; i++) post(encoder, i === 0 ? downFirst : downPipe, downGroups[i], downViews[i]);
        for (let i = LEVELS - 2; i >= 0; i--) post(encoder, upPipe, upGroups[i], upViews[i]);
        post(encoder, outPipe, outGroup, context.getCurrentTexture().createView());
        device.queue.submit([encoder.finish()]);
        context.present();
        presented += 1;
        if (presented === 2) {
          shown.set(withTiming(1, { duration: 260 }));
          // the Skia scene under it can go once this one has faded in
          setTimeout(() => live && report.current(true), 320);
        }
      };
      const stat = { frames: 0, sum: 0, worst: 0, last: 0, cpu: 0, hitches: 0 };
      const loop = () => {
        if (!live) return;
        try {
          const t0 = performance.now();
          render();
          if (__DEV__) {
            const dt = stat.last ? t0 - stat.last : 16;
            stat.last = t0;
            stat.frames += 1;
            stat.sum += dt;
            stat.worst = Math.max(stat.worst, dt);
            if (dt > 25) stat.hitches += 1;
            stat.cpu += performance.now() - t0;
            (globalThis as { __fbdGpu?: unknown }).__fbdGpu = { frames: stat.frames, avgMs: +(stat.sum / stat.frames).toFixed(2), worstMs: +stat.worst.toFixed(1), cpuMs: +(stat.cpu / stat.frames).toFixed(2), hitches: stat.hitches };
            if (stat.frames >= 300) Object.assign(stat, { frames: 0, sum: 0, worst: 0, cpu: 0, hitches: 0 });
          }
        } catch (e) {
          if (__DEV__) console.info(`[Filament bulb GPU frame failed] ${String(e)}`);
          live = false;
          report.current(false);
          return;
        }
        frame = requestAnimationFrame(loop);
      };
      loop();
      void device.lost.then(() => {
        if (live) {
          live = false;
          report.current(false);
        }
      });
      cleanup = () => {
        try {
          context.unconfigure();
          for (const t of [hdr, ...downs, ...ups]) t.destroy();
          motes.destroy();
          sparks.destroy();
          root.destroy();
          device.destroy();
        } catch {
          // Already lost.
        }
      };
    };
    start().catch((e) => {
      if (__DEV__) console.info(`[Filament bulb GPU unavailable] ${e instanceof Error ? e.message : String(e)}`);
      if (live) report.current(false);
    });
    return () => {
      live = false;
      cancelAnimationFrame(frame);
      cleanup();
    };
  }, [ref, scene, L, dialR, p3, webgpu, shown]);

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
