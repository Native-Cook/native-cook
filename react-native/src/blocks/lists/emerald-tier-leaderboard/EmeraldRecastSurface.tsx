import { Canvas, Fill, RoundedRect, Shader, Skia, type Uniforms } from '@shopify/react-native-skia';
import { Component, memo, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState, PixelRatio, Platform, StyleSheet, TurboModuleRegistry, View } from 'react-native';
import Animated, { useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { createWorkletRuntime, runOnRuntimeAsync, runOnUIAsync, scheduleOnRN, scheduleOnRuntime, scheduleOnUI, type WorkletRuntime } from 'react-native-worklets';
import type { RNCanvasContext } from 'react-native-webgpu';

import { RECAST_SURFACE_ORDER, type RecastActorId, type RecastFrame } from './recastMotion';
import { RECAST_HEIGHT, RECAST_SKSL, RECAST_UNIFORM_FLOATS, RECAST_WIDTH, RECAST_WGSL, recastCopyUniforms, recastWriteUniforms } from './recastOptics';

export type EmeraldRecastSurfaceProps = {
  frame: SharedValue<RecastFrame>;
  renderer: 'auto' | 'skia';
  solid: boolean;
  onRendererStatus?: (status: string) => void;
};
const BASE = Skia.RuntimeEffect.Make(RECAST_SKSL);
const sceneStyle = { width: RECAST_WIDTH, height: RECAST_HEIGHT };

function FlatBody({ frame, id }: { frame: SharedValue<RecastFrame>; id: RecastActorId }) {
  const rect = useDerivedValue(() => {
    const body = frame.get().surfaces[id];
    return { rect: { x: body.x, y: body.y, width: body.width, height: body.height }, rx: body.radius, ry: body.radius };
  });
  return <RoundedRect rect={rect} color="#0A1B18" />;
}

/** Four glass bodies share a single bounded scene. Native labels/photos are separate siblings above it. */
export const EmeraldRecastSurface = memo(function EmeraldRecastSurface({ frame, renderer, solid, onRendererStatus }: EmeraldRecastSurfaceProps) {
  const gpuActive = useSharedValue(false);
  const reporter = useRef(onRendererStatus);
  const lastStatus = useRef(solid ? 'skia-solid' : 'skia');
  const report = useCallback((status: string) => {
    lastStatus.current = status;
    try { reporter.current?.(status); } catch { /* Reporting cannot break the fallback. */ }
  }, []);
  useEffect(() => { reporter.current = onRendererStatus; report(lastStatus.current); }, [onRendererStatus, report]);
  const ready = useCallback(() => report('webgpu'), [report]);
  const preparing = useCallback(() => report('skia'), [report]);
  const unavailable = useCallback(() => report('skia-fallback'), [report]);
  useEffect(() => { gpuActive.set(false); report(solid ? 'skia-solid' : 'skia'); }, [gpuActive, renderer, report, solid]);
  const uniforms = useDerivedValue<Uniforms>(() => {
    const values = new Float32Array(RECAST_UNIFORM_FLOATS);
    recastWriteUniforms(frame.get(), values);
    return { motion: values.subarray(0, 4), flags: [solid ? 1 : 0, gpuActive.get() ? 0 : 1],
      rect0: values.subarray(4, 8), rect1: values.subarray(8, 12), rect2: values.subarray(12, 16), rect3: values.subarray(16, 20),
      material0: values.subarray(20, 24), material1: values.subarray(24, 28), material2: values.subarray(28, 32), material3: values.subarray(32, 36) };
  });
  return <View pointerEvents="none" accessible={false} style={sceneStyle}>
    <Canvas pointerEvents="none" accessible={false} style={StyleSheet.absoluteFill}>
      {BASE ? <Fill><Shader source={BASE} uniforms={uniforms} /></Fill> : RECAST_SURFACE_ORDER.map(id => <FlatBody key={id} id={id} frame={frame} />)}
    </Canvas>
    {renderer === 'auto' && !solid && <RecastOptics frame={frame} active={gpuActive} onReady={ready} onPreparing={preparing} onUnavailable={unavailable} />}
  </View>;
});


type WebGPU = typeof import('react-native-webgpu');
type OpticsProps = { frame: SharedValue<RecastFrame>; active: SharedValue<boolean>; onReady: () => void; onPreparing: () => void; onUnavailable: () => void };
type Resources = {
  device: GPUDevice; pipeline: GPURenderPipeline; group: GPUBindGroup; uniform: GPUBuffer; values: Float32Array;
  pixelWidth: number; pixelHeight: number;
};
type Commands = Resources & { context: RNCanvasContext; generation: number };

/** Returns without acquiring a swapchain texture when the scene has not changed. */
export function recastPresent(commands: Commands, frame: RecastFrame, force = false) {
  'worklet';
  const packet = new Float32Array(RECAST_UNIFORM_FLOATS);
  recastWriteUniforms(frame, packet);
  return recastPresentPacket(commands, packet, force);
}

export function recastPresentPacket(commands: Commands, packet: Float32Array, force = false) {
  'worklet';
  if (!recastCopyUniforms(packet, commands.values) && !force) return false;
  commands.device.queue.writeBuffer(commands.uniform, 0, commands.values);
  const encoder = commands.device.createCommandEncoder({ label: 'Rank Recast bounded optics' });
  const pass = encoder.beginRenderPass({ colorAttachments: [{ view: commands.context.getCurrentTexture().createView(), clearValue: [0, 0, 0, 0], loadOp: 'clear', storeOp: 'store' }] });
  pass.setPipeline(commands.pipeline); pass.setBindGroup(0, commands.group); pass.draw(3); pass.end();
  commands.device.queue.submit([encoder.finish()]);
  commands.context.present();
  return true;
}

async function createResources(device: GPUDevice, format: GPUTextureFormat): Promise<Resources> {
  const module = device.createShaderModule({ label: 'Rank Recast procedural refraction', code: RECAST_WGSL });
  const compilation = await module.getCompilationInfo();
  const errors = compilation.messages.filter(message => message.type === 'error');
  if (errors.length) throw new Error(errors.map(message => message.message).join('; '));
  const pipeline = await device.createRenderPipelineAsync({
    label: 'Rank Recast optics pipeline', layout: 'auto', vertex: { module, entryPoint: 'recastVertex' },
    fragment: { module, entryPoint: 'recastFragment', targets: [{ format }] }, primitive: { topology: 'triangle-list' },
  });
  const uniform = device.createBuffer({ label: 'Rank Recast 144-byte uniforms', size: RECAST_UNIFORM_FLOATS * 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST });
  try {
    const group = device.createBindGroup({ layout: pipeline.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: uniform } }] });
    const scale = Math.min(PixelRatio.get(), 1.5);
    return { device, pipeline, group, uniform, values: new Float32Array(RECAST_UNIFORM_FLOATS).fill(Number.NaN),
      pixelWidth: Math.max(1, Math.floor(RECAST_WIDTH * scale)), pixelHeight: Math.max(1, Math.floor(RECAST_HEIGHT * scale)) };
  } catch (error) { uniform.destroy(); throw error; }
}

/** The package's native value import is guarded, so Expo Go can still use the Skia scene. */
function RecastOptics(props: OpticsProps) {
  const [loaded, setLoaded] = useState<{ api: WebGPU; runtime: WorkletRuntime } | null>(null);
  const [failed, setFailed] = useState(false);
  const callbacks = useRef(props);
  useEffect(() => { callbacks.current = props; });
  const unavailable = useCallback(() => {
    callbacks.current.active.set(false);
    setFailed(true);
    callbacks.current.onUnavailable();
  }, []);
  useEffect(() => {
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (Platform.OS !== 'web' && !TurboModuleRegistry.get('WebGPUModule')) { unavailable(); return; }
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const native = require('react-native-webgpu') as WebGPU;
        const runtime = createWorkletRuntime({ name: 'Rank Recast GPU', enableEventLoop: true });
        if (live) setLoaded({ api: native, runtime });
      } catch { if (live) unavailable(); }
    });
    return () => { live = false; callbacks.current.active.set(false); };
  }, [unavailable]);
  return loaded && !failed ? <OpticsBoundary onFailure={unavailable}>
    <NativeOptics {...props} api={loaded.api} runtime={loaded.runtime} onUnavailable={unavailable} />
  </OpticsBoundary> : null;
}

type GPUWorkerGlobal = typeof globalThis & { __rankRecastCommands?: Commands };
type CompleteOnRN = (owner: number, error: string | null) => void;

/** Only the dedicated GPU runtime calls this. No Reanimated SharedValue crosses into it. */
export function recastSubmitOnWorker(packet: Float32Array, owner: number, complete: CompleteOnRN) {
  'worklet';
  const target = (globalThis as GPUWorkerGlobal).__rankRecastCommands;
  if (!target || target.generation !== owner) { scheduleOnRN(complete, owner, null); return; }
  try {
    if (!recastPresentPacket(target, packet)) { scheduleOnRN(complete, owner, null); return; }
    // Bound actual GPU work, not merely JS calls. The native promise settles on this runtime.
    void target.device.queue.onSubmittedWorkDone().then(() => {
      scheduleOnRN(complete, owner, null);
    }).catch(error => scheduleOnRN(complete, owner, String(error)));
  } catch (error) { scheduleOnRN(complete, owner, String(error)); }
}

function NativeOptics({ api, runtime, frame, active, onReady, onPreparing, onUnavailable }: OpticsProps & { api: WebGPU; runtime: WorkletRuntime }) {
  const { Canvas: GPUCanvas, useCanvasRef } = api;
  const canvas = useCanvasRef();
  const [laidOut, setLaidOut] = useState(false);
  const [focused, setFocused] = useState(false);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const generation = useRef(0);
  const callbacks = useRef({ onReady, onPreparing, onUnavailable });
  const failure = useRef<(owner: number, reason: string) => void>(() => {});
  // These controls live exclusively on the UI runtime. The worker receives a copied144B packet.
  const allowed = useSharedValue(false);
  const requestedOwner = useSharedValue(0);
  const latestPacket = useSharedValue<Float32Array | null>(null);
  const revision = useSharedValue(0);
  const submittedRevision = useSharedValue(0);
  const inFlight = useSharedValue(false);
  useEffect(() => { callbacks.current = { onReady, onPreparing, onUnavailable }; });
  useFocusEffect(useCallback(() => { setFocused(true); return () => setFocused(false); }, []));
  useEffect(() => {
    const listener = AppState.addEventListener('change', state => setForeground(state === 'active'));
    return () => listener.remove();
  }, []);
  const reportFailure = useCallback((owner: number, reason: string) => failure.current(owner, reason), []);
  const completeOnUI = useCallback((owner: number, error: string | null) => {
    'worklet';
    if (requestedOwner.get() !== owner) return;
    inFlight.set(false);
    if (error !== null) { active.set(false); scheduleOnRN(reportFailure, owner, error); }
  }, [active, inFlight, reportFailure, requestedOwner]);
  // Worklets0.10 non-Bundle Mode only accepts these dispatch APIs from the RN runtime.
  // A captured worklet loses its hash after deserialization; never reschedule it from UI/worker.
  const completeOnRN = useCallback((owner: number, error: string | null) => {
    scheduleOnUI(completeOnUI, owner, error);
  }, [completeOnUI]);
  const dispatchOnRN = useCallback((packet: Float32Array, owner: number) => {
    try { scheduleOnRuntime(runtime, recastSubmitOnWorker, packet, owner, completeOnRN); }
    catch (error) { completeOnRN(owner, String(error)); }
  }, [completeOnRN, runtime]);
  useAnimatedReaction(() => active.get() && allowed.get() ? frame.get() : null, snapshot => {
    if (!snapshot) return;
    const packet = new Float32Array(RECAST_UNIFORM_FLOATS);
    recastWriteUniforms(snapshot, packet);
    latestPacket.set(packet);
    revision.set(revision.get() + 1);
  });
  useAnimatedReaction(() => {
    if (!active.get() || !allowed.get() || inFlight.get() || revision.get() === submittedRevision.get()) return null;
    const packet = latestPacket.get();
    return packet ? { packet, revision: revision.get(), owner: requestedOwner.get() } : null;
  }, batch => {
    if (!batch || batch.owner === 0) return;
    inFlight.set(true);
    submittedRevision.set(batch.revision);
    scheduleOnRN(dispatchOnRN, batch.packet, batch.owner);
  });
  useEffect(() => {
    allowed.set(focused && foreground);
    if (!focused || !foreground) active.set(false);
  }, [active, allowed, focused, foreground]);

  useEffect(() => {
    if (!laidOut || !focused || !foreground) return;
    let live = true, failed = false, releasing = false;
    let device: GPUDevice | undefined;
    let resources: Resources | undefined;
    let context: RNCanvasContext | null = null;
    const owner = ++generation.current;
    callbacks.current.onPreparing();
    requestedOwner.set(owner);
    active.set(false);
    const current = () => live && !failed && generation.current === owner;
    const release = () => {
      if (releasing) return;
      releasing = true;
      // Invalidate UI scheduling immediately; cleanup never waits for GPU work on the UI thread.
      void runOnUIAsync((requested: typeof requestedOwner, visible: typeof active, busy: typeof inFlight, token: number) => {
        'worklet';
        if (requested.get() === token) { requested.set(0); visible.set(false); busy.set(false); }
      }, requestedOwner, active, inFlight, owner).catch(() => {});
      void runOnRuntimeAsync(runtime, (token: number) => {
        'worklet';
        const worker = globalThis as GPUWorkerGlobal;
        const target = worker.__rankRecastCommands;
        if (target?.generation === token) {
          delete worker.__rankRecastCommands;
          try { target.context.unconfigure(); } catch { /* Detached surface. */ }
        }
      }, owner).catch(() => {}).finally(() => {
        try { resources?.uniform.destroy(); } catch { /* Already lost. */ }
        try { device?.destroy(); } catch { /* Already lost. */ }
      });
    };
    const fail = (error: unknown) => {
      if (!current()) return;
      failed = true;
      clearTimeout(deadline);
      active.set(false);
      release();
      if (__DEV__) console.info('[Rank Recast WebGPU fallback]', error instanceof Error ? error.message : String(error));
      callbacks.current.onUnavailable();
    };
    const deadline = setTimeout(() => fail(new Error('Rank Recast GPU initialization timed out.')), 15000);
    failure.current = (token, reason) => { if (token === owner) fail(reason); };
    const start = async () => {
      api.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('WebGPU provider is unavailable.');
      const adapter = await gpu.requestAdapter();
      if (!current()) return;
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('A hardware WebGPU adapter is unavailable.');
      const requested = await adapter.requestDevice();
      if (!current()) { requested.destroy(); return; }
      device = requested;
      device.addEventListener('uncapturederror', event => fail(event.error));
      void device.lost.then(info => { if (current()) fail(new Error(`Rank Recast GPU lost: ${info.message || info.reason}`)); }).catch(fail);
      const format = gpu.getPreferredCanvasFormat();
      device.pushErrorScope('validation');
      try { resources = await createResources(device, format); }
      finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
      if (!current()) { resources.uniform.destroy(); return; }
      context = canvas.current?.getContext('webgpu') ?? null;
      if (!context) throw new Error('Rank Recast canvas has not mounted.');
      const prepared: Commands = { ...resources, context, generation: owner };
      const initialPacket = await runOnUIAsync((source: typeof frame) => {
        'worklet';
        const packet = new Float32Array(RECAST_UNIFORM_FLOATS);
        recastWriteUniforms(source.get(), packet);
        return packet;
      }, frame);
      if (!current()) return;
      device.pushErrorScope('validation');
      try {
        await runOnRuntimeAsync(runtime, (target: Commands, install: WebGPU['installWebGPU'], packet: Float32Array, pixelFormat: GPUTextureFormat) => {
          'worklet';
          install();
          target.context.canvas.width = target.pixelWidth; target.context.canvas.height = target.pixelHeight;
          target.context.configure({ device: target.device, format: pixelFormat, alphaMode: 'premultiplied' });
          (globalThis as GPUWorkerGlobal).__rankRecastCommands = target;
          recastPresentPacket(target, packet, true);
        }, prepared, api.installWebGPU, initialPacket, format);
        await device.queue.onSubmittedWorkDone();
      } finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
      if (!current()) return;
      const shown = await runOnUIAsync((requested: typeof requestedOwner, visible: typeof active, busy: typeof inFlight,
        latest: typeof latestPacket, received: typeof revision, sent: typeof submittedRevision, token: number) => {
        'worklet';
        if (requested.get() !== token) return false;
        latest.set(null); received.set(0); sent.set(0); busy.set(false);
        visible.set(true);
        return true;
      }, requestedOwner, active, inFlight, latestPacket, revision, submittedRevision, owner);
      if (!current() || !shown) return;
      clearTimeout(deadline);
      callbacks.current.onReady();
    };
    void start().catch(fail);
    return () => { live = false; clearTimeout(deadline); active.set(false); release(); };
  }, [active, api, canvas, focused, foreground, frame, inFlight, laidOut, latestPacket, requestedOwner, revision, runtime, submittedRevision]);

  const visibility = useAnimatedStyle(() => ({ opacity: active.get() && allowed.get() ? 1 : 0 }));
  return <Animated.View pointerEvents="none" accessible={false} collapsable={false} style={[StyleSheet.absoluteFill, visibility]}>
    <GPUCanvas ref={canvas} pointerEvents="none" opaque={false} style={sceneStyle} onLayout={() => setLaidOut(true)} />
  </Animated.View>;
}

class OpticsBoundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure(); }
  render() { return this.state.failed ? null : this.props.children; }
}
