import { Component, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Image, PixelRatio, Platform, TurboModuleRegistry } from 'react-native';
import { createWorkletRuntime, runOnRuntimeAsync, scheduleOnRN, scheduleOnRuntime } from 'react-native-worklets';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';
import type { MedalCanvasProps } from '../MedalCanvas';
import type { RNCanvasContext } from 'react-native-webgpu';

import { setupSlices } from './setup';
import { createMedalScene, type RenderCommands, type encodeMedalScene } from './medal-scene';
import { verifyStrip } from './strip-data';
import { frontStrips, backStrip, type StripFingerprint } from './face-data';

export type MedalGpuCanvasProps = MedalCanvasProps & {
  visible: SharedValue<number>;
  presented: SharedValue<number>;
  spin: SharedValue<number>;
  clock: SharedValue<number>;
  struck: SharedValue<number>;
  stageBottom: number;
  stageHalf: number;
  stageCentreY: number;
  sheetTop: number;
  onUnavailable?: (reason: string) => void;
};

type WebGPU = typeof import('react-native-webgpu');
const reason = (error: unknown) => error instanceof Error ? error.message : String(error);

// A missing native module throws during import, before any adapter request. Load it inside a
// guarded effect so merely importing this optional renderer cannot break the Skia fallback.
export function MedalGpuCanvas(props: MedalGpuCanvasProps) {
  const [webgpu, setWebgpu] = useState<WebGPU | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const callbacks = useRef(props);
  useEffect(() => { callbacks.current = props; });
  useEffect(() => {
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (Platform.OS !== 'web' && !TurboModuleRegistry.get('WebGPUModule')) {
          setFailure('Native WebGPU module is unavailable.');
          return;
        }
        // Expo Go has no native module: keep loading inside this guard.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const module = require('react-native-webgpu') as WebGPU;
        setWebgpu(module);
      } catch (error) { setFailure(reason(error)); }
    });
    return () => { live = false; };
  }, []);
  useEffect(() => {
    if (failure !== null) {
      try { callbacks.current.onUnavailable?.(failure); } catch { /* Fallback reporting must not crash the sheet. */ }
    }
  }, [failure]);
  if (failure !== null || !webgpu) return null;
  return (
    <CanvasBoundary onFailure={setFailure}>
      <NativeMedalCanvas {...props} webgpu={webgpu} onFailure={setFailure} />
    </CanvasBoundary>
  );
}

class CanvasBoundary extends Component<{ children: ReactNode; onFailure: (reason: string) => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: Error) { this.props.onFailure(reason(error)); }
  render() { return this.state.failed ? null : this.props.children; }
}

function NativeMedalCanvas({ webgpu, onFailure, ...props }: MedalGpuCanvasProps & { webgpu: WebGPU; onFailure: (reason: string) => void }) {
  const { Canvas, useCanvasRef } = webgpu;
  const ref = useCanvasRef();
  const [laidOut, setLaidOut] = useState(false);
  const latest = useRef(props);
  useLayoutEffect(() => { latest.current = props; });
  const { width, height, half, centre, rotation, light, glint, heat, charge, stamp, morph,
    sweep, engrave, spin, clock, struck, stageBottom, sheetTop, stageHalf, stageCentreY, visible, presented } = props;
  // One UI-thread object prevents a JS read from combining different simulation frames.
  const snapshot = useDerivedValue(() => {
    'worklet';
    return { centre: centre.get(), half: half.get(), rotation: rotation.get(), light: light.get(),
      glint: glint.get(), heat: heat.get(), charge: charge.get(), stamp: stamp.get(), morph: morph.get(),
      sweep: sweep.get(), engrave: engrave.get(), spin: spin.get(), time: clock.get(), struck: struck.get() };
  });

  useEffect(() => {
    if (!laidOut) return;
    let active = true;
    let animation = 0;
    let device: GPUDevice | undefined;
    let context: RNCanvasContext | null = null;
    let scene: ReturnType<typeof createMedalScene> | undefined;
    const textures: GPUTexture[] = [];
    let pendingProbe: Promise<void> | undefined;
    let releasing = false;
    const slice = setupSlices(() => visible.get() === 0, () => active);
    const loading = new AbortController();
    const dispose = () => {
      cancelAnimationFrame(animation);
      loading.abort();
      if (releasing) return;
      releasing = true;
      const release = () => {
        // Each release is independent: a lost device must not prevent the rest from being freed.
        const releases = [() => scene?.destroy(), ...textures.map(texture => () => texture.destroy()),
          () => context?.unconfigure(), () => device?.destroy()];
        for (const release of releases) { try { release(); } catch { /* Already lost or detached. */ } }
        textures.length = 0;
        scene = undefined;
      };
      // A worker must finish encoding before its buffers/device are torn down.
      if (pendingProbe) void pendingProbe.then(release, release);
      else release();
    };
    const fail = (error: unknown) => {
      if (!active) return;
      active = false;
      dispose();
      onFailure(reason(error));
    };
    const uncaptured = (event: GPUUncapturedErrorEvent) => fail(event.error);
    const start = async () => {
      if (!active) return;
      latest.current.onReady?.(false);
      await slice('install', () => webgpu.installWebGPU());
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('WebGPU is unavailable.');
      const compiler = Platform.OS === 'web' ? undefined : await slice('compiler runtime', () => createWorkletRuntime({ name: 'Medal GPU setup' }));
      const adapter = await slice('adapter', () => compiler
        ? new Promise<GPUAdapter | null>((resolve, reject) => scheduleOnRuntime(compiler, acquireAdapter, gpu, resolve, reject))
        : gpu.requestAdapter());
      if (!active) return;
      if (!adapter) throw new Error('No WebGPU adapter.');
      const requested = await slice('device', () => adapter.requestDevice());
      if (!active) { requested.destroy(); return; }
      device = requested;
      device.addEventListener('uncapturederror', uncaptured);
      void device.lost.then(info => { if (active) fail(`WebGPU device lost: ${info.message || info.reason}`); }).catch(fail);
      context = ref.current?.getContext('webgpu') ?? null;
      if (!context) throw new Error('No WebGPU canvas context.');
      const ratio = PixelRatio.get();
      const pixelWidth = Math.round(width * ratio);
      const pixelHeight = Math.round(height * ratio);
      const format = gpu.getPreferredCanvasFormat();
      await slice('configure', () => {
        context!.canvas.width = pixelWidth;
        context!.canvas.height = pixelHeight;
        context!.configure({ device: requested, format, alphaMode: 'premultiplied' });
      });

      const upload = async (asset: number, fingerprint: StripFingerprint, label: string) => {
        const source = Image.resolveAssetSource(asset);
        if (!source?.uri) throw new Error('Medal strip asset is unavailable.');
        const response = await fetch(source.uri, { signal: loading.signal });
        if (!response.ok && response.status !== 0) throw new Error('Could not load medal strip.');
        const encoded = await response.arrayBuffer();
        const bitmap = await slice(`${label} decode`, () => createImageBitmap(encoded, {
          colorSpaceConversion: 'none', premultiplyAlpha: 'none',
        }));
        try {
          if (!active) return null;
          if (bitmap.width !== fingerprint.width || bitmap.height !== fingerprint.height) {
            throw new Error('Invalid medal strip dimensions.');
          }
          const texture = await slice(`${label} texture`, () => requested.createTexture({ label, size: [bitmap.width, bitmap.height], format: 'rgba8unorm',
            usage: GPUTextureUsage.COPY_DST | GPUTextureUsage.COPY_SRC | GPUTextureUsage.TEXTURE_BINDING
              | GPUTextureUsage.RENDER_ATTACHMENT }));
          textures.push(texture);
          for (let row = 0; row < bitmap.height; row += 64) {
            const rows = Math.min(64, bitmap.height - row);
            await slice(`${label} upload rows ${row}`, () => requested.queue.copyExternalImageToTexture(
              { source: bitmap, origin: [0, row] }, { texture, origin: [0, row], premultipliedAlpha: false }, [bitmap.width, rows]));
          }
          // This platform ignores colorSpaceConversion: never trust the option alone.
          await slice(`${label} fingerprint`, () => verifyStrip(requested, texture, fingerprint));
          return active ? texture : null;
        } finally { bitmap.close(); }
      };
      const fronts: GPUTexture[] = [];
      const assets = [require('../assets/medal-front-1.png'), require('../assets/medal-front-7.png'),
        require('../assets/medal-front-30.png')];
      for (let i = 0; i < assets.length; i++) {
        const texture = await upload(assets[i], frontStrips[i], `Medal front ${i}`);
        if (!active || !texture) return;
        fronts.push(texture);
      }
      const back = await upload(require('../assets/medal-back.png'), backStrip, 'Medal back data');
      if (!active || !back) return;
      const initial = { ...snapshot.get(), from: latest.current.from, to: latest.current.to };
      const options = { width: pixelWidth, height: pixelHeight, pixelRatio: ratio,
        half: stageHalf * ratio, centre: [width / 2 * ratio, stageCentreY * ratio] as [number, number],
        front: fronts[0], fronts, back, stageBottom, sheetTop, strikeSparks: true };
      const compileShader = compiler ? (target: GPUDevice, code: string) => runOnRuntimeAsync(compiler, compileModule, target, code) : undefined;
      const dispatchProbe = compiler ? (encode: typeof encodeMedalScene, commands: RenderCommands) => runOnRuntimeAsync(compiler, encode, commands) : undefined;
      requested.pushErrorScope('validation');
      try {
        scene = await slice('scene resources', () => createMedalScene(requested, format, { ...options, deferred: true, compileShader }));
        await scene.prepare(slice);
      } finally {
        const error = await requested.popErrorScope();
        if (error) fail(error);
      }
      if (!active) return;
      // Measure actual encoding + queue completion before revealing the GPU surface. Native
      // timing is deliberately local to this surface; desktop proof timings never select it.
      const samples: number[] = [];
      const probe = { ...initial, centre: { x: width / 2, y: stageCentreY }, half: stageHalf,
        stamp: 1, heat: 1, glint: 3, spin: 29, struck: 0, time: 0.12, dt: 1 / 60 };
      const probeTarget = await slice('probe target', () => requested.createTexture({ size: [pixelWidth, pixelHeight],
        format, usage: GPUTextureUsage.RENDER_ATTACHMENT }));
      textures.push(probeTarget);
      const probeView = probeTarget.createView();
      for (let i = 0; i < 14; i++) {
        if (!active || !context || !scene) return;
        await slice(`probe ${i}`, async () => {
          const began = performance.now();
          probe.time = 0.12 + i / 60;
          pendingProbe = scene!.probe(probeView, probe, dispatchProbe);
          try { await pendingProbe; } finally { pendingProbe = undefined; }
          await requested.queue.onSubmittedWorkDone();
          if (i >= 2) samples.push(performance.now() - began);
        });
      }
      if (!active || !scene) return;
      samples.sort((a, b) => a - b);
      const p90 = samples[Math.floor(samples.length * 0.9)];
      const quality = p90 > 14 ? 'low' : p90 > 7 ? 'medium' : 'high';
      await slice('reset probe', () => { scene!.setQuality(quality); scene!.resetParticles(); probeTarget.destroy(); });
      let previous = initial.time;
      let ready = false;
      const draw = () => {
        if (!active || !context || !scene) return;
        try {
          const frame = snapshot.get();
          scene.render(context.getCurrentTexture().createView(), {
            ...frame, from: latest.current.from, to: latest.current.to, dt: Math.max(0, Math.min(frame.time - previous, 1 / 30)),
          });
          previous = frame.time;
          context.present();
          presented.set(frame.time);
          if (!ready) { ready = true; latest.current.onReady?.(true); }
          animation = requestAnimationFrame(draw);
        } catch (error) { fail(error); }
      };
      animation = requestAnimationFrame(draw);
    };
    void Promise.resolve().then(start).catch(fail);
    return () => {
      active = false;
      device?.removeEventListener('uncapturederror', uncaptured);
      dispose();
    };
  }, [laidOut, width, height, ref, webgpu, onFailure, snapshot, sheetTop, stageBottom, stageHalf, stageCentreY, visible, presented]);

  return <Canvas ref={ref} opaque={false} android={{ surfaceType: 'TextureView' }}
    pointerEvents="none" style={{ width, height, backgroundColor: 'transparent' }}
    onLayout={() => setLaidOut(true)} />;
}

// GPU host objects are transferred by react-native-webgpu's installed serializer.
// Device creation/loss observation stays on RN; only indivisible WGSL parsing runs here.
function compileModule(device: GPUDevice, code: string) {
  'worklet';
  return device.createShaderModule({ code });
}

// Adapter enumeration can synchronously enter the driver even though its API returns
// a promise. Keep that cold-start cost on the same setup worker as WGSL parsing.
async function acquireAdapter(gpu: GPU, resolve: (adapter: GPUAdapter | null) => void, reject: (message: string) => void) {
  'worklet';
  try {
    const adapter = await gpu.requestAdapter();
    scheduleOnRN(resolve, adapter);
  } catch (error) {
    scheduleOnRN(reject, error instanceof Error ? error.message : String(error));
  }
}
