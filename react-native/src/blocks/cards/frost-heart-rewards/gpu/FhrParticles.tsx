import { Component, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { AppState, PixelRatio, Platform, StyleSheet, TurboModuleRegistry } from 'react-native';
import { useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import type { RNCanvasContext } from 'react-native-webgpu';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';
import { d, tgpu, type TgpuRoot } from 'typegpu';

import { COUNT, COVERAGE_VERTICES, coverageVertices, drawWGSL, entryName, Piece, pieceValues, PARAM_FLOATS, Params, SECTOR_COUNT } from './fhr-particles';
import { type Scene } from '../fhr-motion';
import { createFhrBackdrop } from './fhr-backdrop';
import { fhrPerfSubmitStarted, installFhrGPUObserver, recordFhrGPUFrame, recordFhrGPUFromRN, removeFhrGPUObserver } from '../fhr-perf';

type WebGPU = typeof import('react-native-webgpu');
const pixelRatio = PixelRatio.get();
// Contains the gem, its bloom/caustic and every claim trajectory at the original scale.
const VIEWPORT = { x: 20, y: 140, width: 375, height: 415 };
// The optical target only covers the projected gem. Keep the larger native
// viewport above so the five chip paths and display-space caustic are intact.
// Exported-vertex bounds across tilt, ring, spread and push are inside this box.
const CRYSTAL_VIEWPORT = { x: 27, y: 151, width: 298, height: 244 };
const CRYSTAL_RESOLUTION = .5;
function reportCrystal(message: string) {
  if (__DEV__) Object.defineProperty(globalThis, '__fhrCrystal', { value: message, configurable: true });
}

export type FhrParticlesProps = {
  scene: SharedValue<Scene>;
  time: SharedValue<number>;
  width: number;
  height: number;
  /** Screen-space vertical inset, in RN points, applied after scaling the 402 × 874 design. */
  offsetY?: number;
  /** Extra damped gem rotation, in radians. */
  ring: SharedValue<number>;
  /** Hero parallax in design points (each axis is clamped to ±8). */
  tilt?: SharedValue<{ x: number; y: number }>;
  reducedMotion: boolean;
  jelly?: SharedValue<number>;
  /** Deterministic development snapshots settle the highlight envelope immediately. */
  pinned?: SharedValue<boolean>;
  /** Local event timestamps for chip values 10, 15, 20, 25, 50; -1000 means unclaimed. */
  claims?: SharedValue<number[]>;
  manualTime?: SharedValue<number>;
  interactive?: SharedValue<boolean>;
  /** Live manual polish progress, from 0 to 1. */
  charge?: SharedValue<number>;
  /** Manual opacity return progress, from 0 to 1. */
  reset?: SharedValue<number>;
  onReady?: (ready: boolean) => void;
};

type Commands = {
  generation: number;
  device: GPUDevice;
  context: RNCanvasContext;
  draw: GPURenderPipeline;
  drawGroup: GPUBindGroup;
  overlay: GPURenderPipeline;
  composite: GPURenderPipeline;
  breath: GPURenderPipeline;
  glintSearch: GPUComputePipeline;
  compositeGroup: GPUBindGroup;
  breathGroup: GPUBindGroup;
  glintGroup: GPUBindGroup;
  crystalView: GPUTextureView;
  materialView: GPUTextureView;
  coverage: GPURenderPipeline;
  coverageBuffer: GPUBuffer;
  coverageGroup: GPUBindGroup;
  coverageView: GPUTextureView;
  coverageMSAA: GPUTextureView;
  params: GPUBuffer;
  values: Float32Array;
};

/** Optional transparent native overlay. Expo Go never imports the unavailable native module. */
export function FhrParticles(props: FhrParticlesProps) {
  const [api, setApi] = useState<WebGPU | null>(null);
  const [failed, setFailed] = useState(false);
  const readiness = useRef(props.onReady);
  useEffect(() => { readiness.current = props.onReady; }, [props.onReady]);
  const onReadiness = useCallback((ready: boolean) => readiness.current?.(ready), []);
  const onUnavailable = useCallback(() => {
    setFailed(true);
    readiness.current?.(false);
  }, []);
  useEffect(() => {
    if (!api || failed) onReadiness(false);
  }, [api, failed, onReadiness]);
  useEffect(() => {
    if (Platform.OS === 'web') return;
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      // Requiring this package before the TurboModule check throws in Expo Go.
      if (!TurboModuleRegistry.get('WebGPUModule')) return;
      try {
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const native = require('react-native-webgpu') as WebGPU;
        if (live) setApi(native);
      } catch {
        if (live) setFailed(true);
      }
    });
    return () => { live = false; };
  }, []);
  if (!api || failed || props.width <= 0 || props.height <= 0) return null;
  return (
    <ParticleBoundary onFailure={onUnavailable}>
      <NativeParticles {...props} onReady={onReadiness} webgpu={api} onUnavailable={onUnavailable} />
    </ParticleBoundary>
  );
}

function NativeParticles(props: FhrParticlesProps & { webgpu: WebGPU; onUnavailable: () => void }) {
  const { Canvas, useCanvasRef } = props.webgpu;
  const canvas = useCanvasRef();
  const [laidOut, setLaidOut] = useState(false);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => subscription.remove();
  }, []);
  useFhrParticlePipeline({ ...props, canvas, ready: laidOut && active });
  const scale = props.width / 402;
  return (
    <Canvas
      ref={canvas}
      opaque={false}
      pointerEvents="none"
      accessible={false}
      style={[StyleSheet.absoluteFill, {
        left: VIEWPORT.x * scale, top: (props.offsetY ?? 0) + VIEWPORT.y * scale,
        width: VIEWPORT.width * scale, height: VIEWPORT.height * scale,
      }]}
      onLayout={() => setLaidOut(true)}
    />
  );
}

/** Owns the GPU resources and the UI frame subscription; intentionally private to this block. */
function useFhrParticlePipeline({
  scene, time, ring, tilt, jelly, pinned, width, height, offsetY = 0, claims, manualTime, interactive, charge, reset, onReady, webgpu, onUnavailable, canvas, ready, reducedMotion,
}: FhrParticlesProps & {
  webgpu: WebGPU;
  onUnavailable: () => void;
  canvas: ReturnType<WebGPU['useCanvasRef']>;
  ready: boolean;
}) {
  const commands = useSharedValue<Commands | null>(null);
  const epoch = useSharedValue(0);
  const lastTime = useSharedValue(-1);
  const lastScene = useSharedValue(-1);
  const lastRing = useSharedValue(-1);
  const lastManualTime = useSharedValue(-1);
  const lastInteractive = useSharedValue(false);
  const lastCharge = useSharedValue(0);
  const lastReset = useSharedValue(0);
  const lastReducedMotion = useSharedValue(false);
  const lastTiltX = useSharedValue(0);
  const lastTiltY = useSharedValue(0);
  const lastJelly = useSharedValue(0);
  const lastDisplay = useSharedValue(-1);
  const lastSurfaceReset = useSharedValue(-1);
  const lastPinned = useSharedValue(false);
  const stillTilt = useSharedValue({ x: 0, y: 0 });
  const emptyClaims = useSharedValue([-1000, -1000, -1000, -1000, -1000]);
  const automatic = useSharedValue(false);
  const idleProgress = useSharedValue(0);
  const claimTimes = claims ?? emptyClaims;
  const manualClock = manualTime ?? time;
  const touched = interactive ?? automatic;
  const manualCharge = charge ?? idleProgress;
  const manualReset = reset ?? idleProgress;
  const parallax = tilt ?? stillTilt;
  const squash = jelly ?? idleProgress;
  const snapshot = pinned ?? automatic;
  const serial = useRef(0);

  const frame = useFrameCallback(frameInfo => {
    const c = commands.get();
    if (!c) return;
    const t = time.get();
    const state = scene.get();
    const rotation = ring.get();
    const shift = parallax.get();
    const bounce = squash.get();
    const isSnapshot = snapshot.get();
    const isInteractive = touched.get();
    const touchTime = isInteractive && !reducedMotion ? manualClock.get() : 0;
    const chargeProgress = isInteractive ? manualCharge.get() : 0;
    const resetProgress = isInteractive ? manualReset.get() : 0;
    const loopTime = t - Math.floor(t / 10) * 10;
    // The joined hold keeps its registered pose; only the projected glints travel.
    const opticalTime = state.t;
    const sceneKey = (opticalTime <= 0.6 ? 0 : opticalTime) + state.reset * 20 + state.polish * 30 + state.polishX * .01 + state.gem * 40 + state.breath * 50 + state.breathX * .0001 + state.breathY * .00001;
    const renderTime = !isInteractive && (loopTime <= 0.6 || loopTime >= 9.999) ? 0 : t;
    // Rest is completely transparent: clear once even while the page's clock or sensor runs.
    // A pinned frame costs one draw, then no GPU work until the shared values change.
    if (isSnapshot === lastPinned.get() && reducedMotion === lastReducedMotion.get() && sceneKey === lastScene.get() && renderTime === lastTime.get() && rotation === lastRing.get() && shift.x === lastTiltX.get() && shift.y === lastTiltY.get() && bounce === lastJelly.get() && touchTime === lastManualTime.get() && isInteractive === lastInteractive.get() && chargeProgress === lastCharge.get() && resetProgress === lastReset.get()) return;
    try {
      const values = c.values;
      values[3] = opticalTime;
      values[5] = shift.y / 8 * 0.25;
      values[6] = Math.max(-0.61, Math.min(0.61, rotation + shift.x / 8 * 0.18));
      values[7] = isInteractive ? 1 : 0;
      values[8] = state.gem;
      values[9] = state.display;
      values[10] = state.spotlight;
      values[11] = state.reset;
      values[12] = state.push;
      values[13] = state.polish;
      values[14] = state.hue;
      values[15] = reducedMotion ? 1 : 0;
      if (isInteractive) {
        const events = claimTimes.get();
        for (let index = 0; index < 5; index++) values[16 + index] = events[index] >= 0 ? touchTime - events[index] : -1000;
      }
      values[21] = isInteractive ? 1 : 0;
      values[24] = state.polishX;
      values[28] = shift.x;
      values[29] = shift.y;
      values[30] = bounce;
      values[44] = state.breathX; values[45] = state.breathY; values[46] = state.breath; values[47] = state.core;
      const jumped = Math.abs(renderTime - lastTime.get()) > .1 || Math.abs(sceneKey - lastScene.get()) > .25 && renderTime === lastTime.get() || Math.abs(shift.x - lastTiltX.get()) > 1 || Math.abs(shift.y - lastTiltY.get()) > 1;
      values[31] = jumped || isSnapshot ? 1 : Math.min(.1, (frameInfo.timeSincePreviousFrame ?? 16.667) / 1000);
      c.device.queue.writeBuffer(c.params, 0, values);
      const encoder = c.device.createCommandEncoder({ label: 'FHR Atelier frame' });
      const priorLoopTime = lastTime.get() - Math.floor(lastTime.get() / 10) * 10;
      const stableHold = !isInteractive && !lastInteractive.get() && !reducedMotion && !lastReducedMotion.get() && state.t >= 7.4 && state.t < 8.9 && priorLoopTime >= 7.4 && priorLoopTime < 8.9 && rotation === lastRing.get() && shift.x === lastTiltX.get() && shift.y === lastTiltY.get() && bounce === lastJelly.get();
      if ((state.t >= 2.1 || state.breath > .0001 || reducedMotion && state.gem > 0) && !stableHold) {
        const pass = encoder.beginRenderPass({
          colorAttachments: [{
            view: c.crystalView,
            loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0],
          }, { view: c.materialView, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }],
        });
        pass.setPipeline(c.draw);
        pass.setBindGroup(0, c.drawGroup);
        pass.draw(6, SECTOR_COUNT);
        pass.end();
        const sameProjection = shift.x * (1 - state.reset) === lastTiltX.get() * (1 - lastSurfaceReset.get()) && shift.y * (1 - state.reset) === lastTiltY.get() * (1 - lastSurfaceReset.get());
        // the pieces' spring settles by ≈ 6.9 s: until then the silhouette moves even with display at 0
        const settled = (u: number) => u < 3.8 || u >= 6.9;
        const sameCoverage = lastTime.get() >= 2.1 && settled(renderTime) && settled(lastTime.get()) && (renderTime >= 6.9) === (lastTime.get() >= 6.9) && state.display === 0 && lastDisplay.get() === 0 && sameProjection && rotation === lastRing.get() && bounce === lastJelly.get();
        if (!sameCoverage) {
          const mask = encoder.beginRenderPass({ colorAttachments: [{ view: c.coverageMSAA, resolveTarget: c.coverageView, loadOp: 'clear', storeOp: 'discard', clearValue: [0, 0, 0, 0] }] });
          mask.setPipeline(c.coverage); mask.setBindGroup(0, c.coverageGroup); mask.setVertexBuffer(0, c.coverageBuffer); mask.draw(COVERAGE_VERTICES); mask.end();
        }
      }
      if (state.gem > .001 && !reducedMotion) {
        const search = encoder.beginComputePass({ label: 'FHR three brightest crystal pixels' });
        search.setPipeline(c.glintSearch);
        search.setBindGroup(0, c.glintGroup);
        search.dispatchWorkgroups(1);
        search.end();
      }
      const composite = encoder.beginRenderPass({
        colorAttachments: [{ view: c.context.getCurrentTexture().createView(), loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }],
      });
      if (state.t >= 2.1 || reducedMotion && state.gem > 0) {
        const density = values[2];
        composite.setScissorRect(Math.floor((CRYSTAL_VIEWPORT.x - VIEWPORT.x) * density), Math.floor((CRYSTAL_VIEWPORT.y - VIEWPORT.y) * density), Math.ceil(CRYSTAL_VIEWPORT.width * density), Math.ceil(CRYSTAL_VIEWPORT.height * density));
        composite.setPipeline(c.composite);
        composite.setBindGroup(0, c.compositeGroup);
        composite.draw(3);
      }
      if (state.t < 2.4 && state.breath > .0001) {
        const density = values[2];
        const left = Math.floor((Math.min(CRYSTAL_VIEWPORT.x + CRYSTAL_VIEWPORT.width, Math.max(CRYSTAL_VIEWPORT.x, state.breathX - 40)) - VIEWPORT.x) * density);
        const top = Math.floor((Math.min(CRYSTAL_VIEWPORT.y + CRYSTAL_VIEWPORT.height, Math.max(CRYSTAL_VIEWPORT.y, state.breathY - 40)) - VIEWPORT.y) * density);
        const right = Math.ceil((Math.max(CRYSTAL_VIEWPORT.x, Math.min(CRYSTAL_VIEWPORT.x + CRYSTAL_VIEWPORT.width, state.breathX + 40)) - VIEWPORT.x) * density);
        const bottom = Math.ceil((Math.max(CRYSTAL_VIEWPORT.y, Math.min(CRYSTAL_VIEWPORT.y + CRYSTAL_VIEWPORT.height, state.breathY + 40)) - VIEWPORT.y) * density);
        composite.setScissorRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        composite.setPipeline(c.breath);
        composite.setBindGroup(0, c.breathGroup);
        composite.draw(3);
      }
      composite.setScissorRect(0, 0, values[0], values[1]);
      // Claim pearls and the low caustic retain their original display-space colors.
      composite.setPipeline(c.overlay);
      composite.setBindGroup(0, c.drawGroup);
      composite.draw(6, COUNT - SECTOR_COUNT, 0, SECTOR_COUNT);
      composite.end();
      const submitStarted = __DEV__ ? fhrPerfSubmitStarted() : 0;
      const submitEpoch = submitStarted > 0 ? Date.now() : 0;
      c.device.queue.submit([encoder.finish()]);
      if (__DEV__) {
        recordFhrGPUFrame(c.device.queue, loopTime, submitStarted);
        if (submitEpoch > 0) scheduleOnRN(recordFhrGPUFromRN, loopTime, submitEpoch, c.generation);
      }
      c.context.present();
      lastReducedMotion.set(reducedMotion);
      lastScene.set(sceneKey);
      lastTime.set(renderTime);
      lastRing.set(rotation);
      lastManualTime.set(touchTime);
      lastInteractive.set(isInteractive);
      lastCharge.set(chargeProgress);
      lastReset.set(resetProgress);
      lastTiltX.set(shift.x);
      lastTiltY.set(shift.y);
      lastJelly.set(bounce);
      lastDisplay.set(state.display);
      lastSurfaceReset.set(state.reset);
      lastPinned.set(isSnapshot);
    } catch (error) {
      scheduleOnRN(reportCrystal, String(error));
      commands.set(null);
      scheduleOnRN(onUnavailable);
    }
  }, false);

  useEffect(() => {
    if (!ready) return;
    const generation = ++serial.current;
    epoch.set(generation);
    let live = true;
    let device: GPUDevice | undefined;
    let root: TgpuRoot | undefined;
    let context: RNCanvasContext | null = null;
    const buffers: GPUBuffer[] = [];
    const textures: GPUTexture[] = [];
    let readGlints: (() => Promise<number[]>) | undefined;

    const clear = () => runOnUIAsync(
      (store: typeof commands, version: typeof epoch, id: number, ctx: RNCanvasContext | null) => {
        'worklet';
        const current = store.get();
        if (current?.generation === id || (!current && version.get() === -id)) {
          store.set(null);
          try { ctx?.unconfigure(); } catch { /* The native surface may already be detached. */ }
        }
      },
      commands, epoch, generation, context,
    ).catch(() => {});

    const start = async () => {
      reportCrystal('requesting device');
      webgpu.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('WebGPU is unavailable');
      const adapter = await gpu.requestAdapter();
      if (!live) return;
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('No hardware WebGPU adapter');
      device = await adapter.requestDevice();
      if (!live) return;
      const thisDevice = device;
      if (__DEV__) Object.defineProperty(globalThis, '__fhrGpuInfo', {
        configurable: true,
        value: {
          adapter: {
            vendor: adapter.info?.vendor, architecture: adapter.info?.architecture,
            device: adapter.info?.device, description: adapter.info?.description,
            features: Array.from(adapter.features),
          },
          device: {
            features: Array.from(device.features),
            maxTextureDimension2D: device.limits.maxTextureDimension2D,
            maxBufferSize: device.limits.maxBufferSize,
            maxStorageBufferBindingSize: device.limits.maxStorageBufferBindingSize,
          },
        },
      });
      void device.lost.then(() => {
        if (live) onUnavailable();
      }).catch(() => {});
      root = tgpu.initFromDevice({ device });
      const format = gpu.getPreferredCanvasFormat();
      const drawCode = drawWGSL();
      const drawing = device.createShaderModule({ label: 'FHR Blender brilliant optics', code: drawCode });
      reportCrystal('compiling shader');
      const compilation = await drawing.getCompilationInfo();
      const shaderErrors = compilation.messages.filter(message => message.type === 'error');
      if (shaderErrors.length) throw new Error(shaderErrors.map(message => `${message.lineNum}:${message.linePos} ${message.message} ${drawCode.split('\n')[message.lineNum - 1]}`).join('; '));
      if (!live) return;
      reportCrystal('creating pipelines');
      const materialLayout = device.createBindGroupLayout({ entries: [
        { binding: 0, visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT, buffer: { type: 'uniform' } },
        { binding: 1, visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT, buffer: { type: 'read-only-storage' } },
        ...[2, 3, 4, 8, 9, 12].map(binding => ({ binding, visibility: GPUShaderStage.FRAGMENT, texture: {} })),
        { binding: 5, visibility: GPUShaderStage.FRAGMENT, sampler: {} },
      ] });
      const materialPipelineLayout = device.createPipelineLayout({ bindGroupLayouts: [materialLayout] });
      const blend: GPUBlendState = {
        color: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
        alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' },
      };
      const pipeline = (target: GPUTextureFormat) => device!.createRenderPipelineAsync({
        label: 'FHR clear dispersive crystal', layout: materialPipelineLayout,
        vertex: { module: drawing, entryPoint: entryName(drawCode, 'vertex') },
        fragment: { module: drawing, entryPoint: target === 'rgba16float' ? 'fragment' : 'overlayFragment', targets: target === 'rgba16float' ? [{ format: target }, { format: 'rgba16float' }] : [{ format: target, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const coverage = await device.createRenderPipelineAsync({
        label: 'FHR native-resolution four-sample silhouette', layout: materialPipelineLayout,
        vertex: { module: drawing, entryPoint: 'coverageVertex', buffers: [{ arrayStride: 16, attributes: [{ shaderLocation: 0, offset: 0, format: 'float32x4' }] }] },
        fragment: { module: drawing, entryPoint: 'coverageFragment', targets: [{ format: 'rgba8unorm' }] },
        primitive: { topology: 'triangle-list' }, multisample: { count: 4 },
      });
      const coverageBuffer = device.createBuffer({ label: 'FHR native silhouette vertex buffer', size: coverageVertices.length * 4, usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST });
      device.queue.writeBuffer(coverageBuffer, 0, new Float32Array(coverageVertices)); buffers.push(coverageBuffer);
      const draw = await pipeline('rgba16float');
      const overlay = await pipeline(format);
      const composite = await device.createRenderPipelineAsync({
        label: 'FHR filmic highlight bloom', layout: 'auto',
        vertex: { module: drawing, entryPoint: 'compositeVertex' },
        fragment: { module: drawing, entryPoint: 'compositeFragment', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const breath = await device.createRenderPipelineAsync({
        label: 'FHR localized damp glass', layout: 'auto',
        vertex: { module: drawing, entryPoint: 'compositeVertex' },
        fragment: { module: drawing, entryPoint: 'breathFragment', targets: [{ format, blend }] },
        primitive: { topology: 'triangle-list' },
      });
      const glintSearch = await device.createComputePipelineAsync({
        label: 'FHR brightest-pixel reduction', layout: 'auto', compute: { module: drawing, entryPoint: 'findGlints' },
      });
      if (!live) return;
      const params = root.createBuffer(Params).$usage('uniform').$name('FHR params').buffer;
      buffers.push(params);
      const geometry = root.createBuffer(d.arrayOf(Piece, SECTOR_COUNT), pieceValues()).$usage('storage').$name('FHR Blender planes').buffer;
      buffers.push(geometry);
      const peaks = device.createBuffer({ label: 'FHR three glints', size: 48, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC | GPUBufferUsage.COPY_DST });
      buffers.push(peaks);
      reportCrystal('uploading backdrop');
      if (__DEV__) {
        readGlints = async () => {
          const staging = thisDevice.createBuffer({ size: 48, usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ });
          try {
            const encoder = thisDevice.createCommandEncoder();
            encoder.copyBufferToBuffer(peaks, 0, staging, 0, 48);
            thisDevice.queue.submit([encoder.finish()]);
            await staging.mapAsync(GPUMapMode.READ);
            return Array.from(new Float32Array(staging.getMappedRange().slice(0)));
          } finally { staging.destroy(); }
        };
        Object.defineProperty(globalThis, '__fhrReadGlints', { configurable: true, value: readGlints });
      }
      const backdrop = await createFhrBackdrop(device, textures);
      if (!live) return;
      const sampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear' });
      const displayScale = width / 402 * pixelRatio;
      const crystalWidth = Math.max(1, Math.round(CRYSTAL_VIEWPORT.width * displayScale * CRYSTAL_RESOLUTION));
      const crystalHeight = Math.max(1, Math.round(CRYSTAL_VIEWPORT.height * displayScale * CRYSTAL_RESOLUTION));
      const crystal = device.createTexture({
        label: 'FHR bounded half-density crystal HDR', size: [crystalWidth, crystalHeight], format: 'rgba16float',
        usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING,
      });
      textures.push(crystal);
      const crystalView = crystal.createView();
      const material = device.createTexture({ label: 'FHR facet clearing and etch', size: [crystalWidth, crystalHeight], format: 'rgba16float', usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
      const maskSize = [Math.round(VIEWPORT.width * displayScale), Math.round(VIEWPORT.height * displayScale)];
      const coverageTexture = device.createTexture({ label: 'FHR full-resolution silhouette', size: maskSize, format: 'rgba8unorm', usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
      const coverageAA = device.createTexture({ label: 'FHR 4x silhouette samples', size: maskSize, format: 'rgba8unorm', sampleCount: 4, usage: GPUTextureUsage.RENDER_ATTACHMENT });
      textures.push(material, coverageTexture, coverageAA);
      const materialView = material.createView(), coverageView = coverageTexture.createView(), coverageMSAA = coverageAA.createView();
      const frozen = device.createTexture({ label: 'FHR joined cut surface carried by case pieces', size: [crystalWidth, crystalHeight], format: 'rgba16float', usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING });
      const placeholder = device.createTexture({ label: 'FHR nonrecursive bake input', size: [1, 1], format: 'rgba16float', usage: GPUTextureUsage.TEXTURE_BINDING });
      textures.push(frozen, placeholder);
      const groupFor = (cut: GPUTextureView) => device!.createBindGroup({ layout: materialLayout, entries: [
        { binding: 0, resource: { buffer: params } }, { binding: 1, resource: { buffer: geometry } },
        { binding: 2, resource: backdrop.bear }, { binding: 3, resource: backdrop.hug }, { binding: 4, resource: backdrop.page },
        { binding: 5, resource: sampler }, { binding: 8, resource: backdrop.skin }, { binding: 9, resource: backdrop.rounded }, { binding: 12, resource: cut },
      ] });
      const drawGroup = groupFor(frozen.createView());
      const bakeGroup = groupFor(placeholder.createView());
      const compositeGroup = device.createBindGroup({ layout: composite.getBindGroupLayout(0), entries: [
        { binding: 0, resource: { buffer: params } }, { binding: 5, resource: sampler },
        { binding: 6, resource: crystalView }, { binding: 7, resource: { buffer: peaks } },
        { binding: 8, resource: backdrop.skin }, { binding: 10, resource: materialView }, { binding: 11, resource: coverageView },
      ] });
      const breathGroup = device.createBindGroup({ layout: breath.getBindGroupLayout(0), entries: [
        { binding: 0, resource: { buffer: params } }, { binding: 5, resource: sampler },
        { binding: 2, resource: backdrop.bear }, { binding: 3, resource: backdrop.hug }, { binding: 4, resource: backdrop.page },
        { binding: 6, resource: crystalView }, { binding: 8, resource: backdrop.skin }, { binding: 11, resource: coverageView },
      ] });
      const glintGroup = device.createBindGroup({ layout: glintSearch.getBindGroupLayout(0), entries: [
        { binding: 0, resource: { buffer: params } }, { binding: 6, resource: crystalView }, { binding: 7, resource: { buffer: peaks } },
      ] });
      context = canvas.current?.getContext('webgpu') ?? null;
      if (!context) throw new Error('FHR particle canvas did not mount');
      const values = new Float32Array(PARAM_FLOATS);
      values[0] = Math.round(VIEWPORT.width * width / 402 * pixelRatio);
      values[1] = Math.round(VIEWPORT.height * width / 402 * pixelRatio);
      values[2] = width / 402 * pixelRatio;
      values[4] = 0;
      values[32] = VIEWPORT.x;
      values[33] = VIEWPORT.y;
      values[34] = VIEWPORT.width;
      values[35] = VIEWPORT.height;
      values[36] = CRYSTAL_VIEWPORT.x;
      values[37] = CRYSTAL_VIEWPORT.y;
      values[38] = CRYSTAL_VIEWPORT.width;
      values[39] = CRYSTAL_VIEWPORT.height;
      values[40] = crystalWidth;
      values[41] = crystalHeight;
      // Rounding each target axis can produce slightly different pixel scales.
      values[42] = crystalWidth / CRYSTAL_VIEWPORT.width;
      values[43] = crystalHeight / CRYSTAL_VIEWPORT.height;
      // Exercise every pipeline against hidden textures before declaring the canvas
      // ready. The visible surface never presents this synthetic warm-up state.
      const warmTarget = thisDevice.createTexture({
        label: 'FHR hidden pipeline warm-up', size: [values[0], values[1]], format,
        usage: GPUTextureUsage.RENDER_ATTACHMENT,
      });
      try {
        const bakeValues = values.slice(); bakeValues[3] = 3.9; bakeValues[8] = 1; bakeValues[10] = 1;
        thisDevice.queue.writeBuffer(params, 0, bakeValues);
        const bake = thisDevice.createCommandEncoder({ label: 'FHR bake joined case front once' });
        const bakePass = bake.beginRenderPass({ colorAttachments: [{ view: frozen.createView(), loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }, { view: materialView, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }] });
        bakePass.setPipeline(draw); bakePass.setBindGroup(0, bakeGroup); bakePass.draw(6, SECTOR_COUNT); bakePass.end(); thisDevice.queue.submit([bake.finish()]);
        const warmValues = values.slice();
        warmValues[3] = 1.1;
        warmValues[8] = 1;
        warmValues[9] = 1;
        warmValues[10] = 1;
        warmValues[31] = 1;
        warmValues[44] = 133; warmValues[45] = 258; warmValues[46] = 1;
        thisDevice.queue.writeBuffer(params, 0, warmValues);
        const encoder = thisDevice.createCommandEncoder({ label: 'FHR prewarm all pipelines' });
        const gemPass = encoder.beginRenderPass({ colorAttachments: [{
          view: crystalView, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0],
        }, { view: materialView, loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0] }] });
        gemPass.setPipeline(draw);
        gemPass.setBindGroup(0, drawGroup);
        gemPass.draw(6, SECTOR_COUNT);
        gemPass.end();
        const mask = encoder.beginRenderPass({ colorAttachments: [{ view: coverageMSAA, resolveTarget: coverageView, loadOp: 'clear', storeOp: 'discard', clearValue: [0, 0, 0, 0] }] });
        mask.setPipeline(coverage); mask.setBindGroup(0, drawGroup); mask.setVertexBuffer(0, coverageBuffer); mask.draw(COVERAGE_VERTICES); mask.end();
        const search = encoder.beginComputePass();
        search.setPipeline(glintSearch);
        search.setBindGroup(0, glintGroup);
        search.dispatchWorkgroups(1);
        search.end();
        const compositePass = encoder.beginRenderPass({ colorAttachments: [{
          view: warmTarget.createView(), loadOp: 'clear', storeOp: 'store', clearValue: [0, 0, 0, 0],
        }] });
        compositePass.setPipeline(composite);
        compositePass.setBindGroup(0, compositeGroup);
        compositePass.draw(3);
        compositePass.setPipeline(breath);
        compositePass.setBindGroup(0, breathGroup);
        compositePass.draw(3);
        compositePass.setPipeline(overlay);
        compositePass.setBindGroup(0, drawGroup);
        compositePass.draw(6, COUNT - SECTOR_COUNT, 0, SECTOR_COUNT);
        compositePass.end();
        thisDevice.queue.submit([encoder.finish()]);
        if (typeof thisDevice.queue.onSubmittedWorkDone === 'function') await thisDevice.queue.onSubmittedWorkDone();
        // Restore the same initial uniforms and empty temporal glint buffer as a cold mount.
        thisDevice.queue.writeBuffer(params, 0, values);
        thisDevice.queue.writeBuffer(peaks, 0, new Float32Array(12));
      } finally { warmTarget.destroy(); }
      if (!live) return;
      const prepared: Commands = {
        generation, device: thisDevice, context, draw, drawGroup, overlay, composite, breath, glintSearch, compositeGroup, breathGroup, glintGroup, crystalView, materialView, coverage, coverageBuffer, coverageGroup: drawGroup, coverageView, coverageMSAA, params, values,
      };
      const installed = await runOnUIAsync(
        (target: Commands, install: WebGPU['installWebGPU'], store: typeof commands, version: typeof epoch, seenTime: typeof lastTime, seenRing: typeof lastRing, textureFormat: GPUTextureFormat) => {
          'worklet';
          if (version.get() !== target.generation) return false;
          install();
          target.context.canvas.width = target.values[0];
          target.context.canvas.height = target.values[1];
          target.context.configure({ device: target.device, format: textureFormat, alphaMode: 'premultiplied' });
          seenTime.set(-1);
          seenRing.set(-1);
          store.set(target);
          return true;
        },
        prepared, webgpu.installWebGPU, commands, epoch, lastTime, lastRing, format,
      );
      if (live && installed) {
        reportCrystal('ready');
        if (__DEV__) installFhrGPUObserver(thisDevice.queue, generation, webgpu.installWebGPU);
        frame.setActive(true);
        onReady?.(true);
      }
    };

    const started = start().catch((error: unknown) => {
      if (live) {
        reportCrystal(error instanceof Error ? error.message : String(error));
        if (__DEV__) console.info(`[FHR particles unavailable] ${error instanceof Error ? error.message : String(error)}`);
        onUnavailable();
      }
    });
    return () => {
      live = false;
      if (__DEV__) removeFhrGPUObserver(generation);
      if (__DEV__ && Reflect.get(globalThis, '__fhrReadGlints') === readGlints) Reflect.deleteProperty(globalThis, '__fhrReadGlints');
      epoch.set(-generation);
      frame.setActive(false);
      onReady?.(false);
      // Invalidate immediately. Late setup also sees the generation change and cannot install.
      void clear();
      // Wait for an outstanding adapter/device/pipeline request before destroying its resources.
      void started.finally(async () => {
        await clear();
        for (const buffer of buffers) {
          try { buffer.destroy(); } catch { /* Already lost. */ }
        }
        for (const texture of textures) { try { texture.destroy(); } catch { /* Already lost. */ } }
        try { root?.destroy(); } catch { /* A lost device may have released the resources. */ }
        try { device?.destroy(); } catch { /* Already lost. */ }
      });
    };
  }, [ready, width, height, offsetY, webgpu, onUnavailable, onReady, canvas, commands, epoch, lastTime, lastRing, frame]);
}

class ParticleBoundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure(); }
  render() { return this.state.failed ? null : this.props.children; }
}
