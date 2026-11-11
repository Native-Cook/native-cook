import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';
import { AppState, PixelRatio } from 'react-native';
import Animated, { useAnimatedStyle, useFrameCallback, useSharedValue, type FrameInfo, type SharedValue } from 'react-native-reanimated';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';
import type { CanvasRef, RNCanvasContext } from 'react-native-webgpu';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { creaturePose, type CreatureMotion } from '../HabitatCreature';
import { EXCHANGE_DRIFT, LAND_MS, LOOP } from '../HabitatMotion';
import { bodyPoint, bodyPointFrom, RIGS } from '../HabitatRig';
import type { HabitatWebGPU } from './HabitatGpuField';
import { createParticleResources, particleFrame, type ParticleEngine, type ParticleResources } from './HabitatParticleEngine';

const ART = 512;
/** Where each move leaves the posed body (source-art pixels) and where it aims; see HabitatSignature. */
const EMITTERS = [
  { a: [452, 402], b: [452, 402], aimA: [0, -1], aimB: [0, -1] },
  { a: [99, 154], b: [409, 196], aimA: [-0.67, -0.74], aimB: [1, -0.06] },
  { a: [377, 152], b: [352, 215], aimA: [0, -1], aimB: [0, -1] },
] as const;

export type ParticleTouch = {
  /** Finger in the stage's own points: x, y, velocity x, y, down (0/1). */
  touch: SharedValue<readonly number[]>;
  /** The last tap: x, y (stage points) and when (frame clock, ms). */
  tap: SharedValue<readonly number[]>;
};
export type HabitatParticleProps = ParticleTouch & {
  webgpu: HabitatWebGPU;
  motion: CreatureMotion;
  /** Device tilt, high-passed (useKeyLight): x, y. */
  tilt: SharedValue<readonly [number, number]>;
  /** True while the GPU draws the particles, so the Skia layer leaves them out. */
  running: SharedValue<boolean>;
  front: RefObject<CanvasRef | null>;
  frontLaidOut: boolean;
  /** Both canvases span the screen from its top to below the stage. */
  width: number;
  height: number;
  heroTop: number;
  heroHeight: number;
  screenHeight: number;
  scale: number;
  /** The creature's art square (left on screen, top inside the stage) and its canvas margin. */
  size: number;
  left: number;
  top: number;
  pad: number;
  reducedMotion: boolean;
  onReady?: () => void;
  onUnavailable?: () => void;
};

/** The front canvas: drawn into by HabitatParticleField, placed above the creatures. */
export function HabitatParticleFront({ webgpu, canvas, width, height, onLaidOut }: {
  webgpu: HabitatWebGPU; canvas: RefObject<CanvasRef | null>; width: number; height: number; onLaidOut: () => void;
}) {
  const { Canvas } = webgpu;
  return <Animated.View pointerEvents="none" accessible={false} style={{ position: 'absolute', left: 0, top: 0, width, height, zIndex: 21 }}>
    <Canvas ref={canvas} pointerEvents="none" opaque={false} style={{ width, height }} onLayout={onLaidOut} />
  </Animated.View>;
}

/**
 * The particle world (HabitatParticleShader): owns the GPU device, steps the simulation once a
 * frame on the UI thread and draws its two layers, the back one here (behind the creatures) and the
 * front one into HabitatParticleFront's canvas. Paused when unfocused, in the background or under
 * Reduced Motion; if WebGPU is missing it reports and the Skia layer draws the move's particles.
 */
export function HabitatParticleField(props: HabitatParticleProps) {
  const { webgpu, motion, tilt, running, touch, tap, front, frontLaidOut, width, height, heroTop, heroHeight, screenHeight, scale, size, left, top, pad } = props;
  const { Canvas } = webgpu;
  const canvas = useRef<CanvasRef>(null);
  const [laidOut, setLaidOut] = useState(false);
  const [focused, setFocused] = useState(false);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const systemReduced = useReducedMotionPreference();
  const reduced = props.reducedMotion || systemReduced;
  const callbacks = useRef(props);
  const generation = useRef(0);
  const failure = useRef<(owner: number, reason: string) => void>(() => {});
  const engine = useSharedValue<ParticleEngine | null>(null);
  const allowed = useSharedValue(false);
  const lastFx = useSharedValue(-1);
  const contact = useSharedValue(0);
  const frameNumber = useSharedValue(0);

  useEffect(() => { callbacks.current = props; });
  useFocusEffect(useCallback(() => { setFocused(true); return () => setFocused(false); }, []));
  useEffect(() => {
    const listener = AppState.addEventListener('change', state => setForeground(state === 'active'));
    return () => listener.remove();
  }, []);
  const reportFailure = useCallback((owner: number, reason: string) => failure.current(owner, reason), []);
  const unit = size / 344;
  const k = size / ART;
  const stageTop = heroTop - 22 * scale;
  const stageBottom = heroTop + heroHeight + 8 * scale;
  const drift = EXCHANGE_DRIFT * width;

  const frame = useFrameCallback((info: FrameInfo) => {
    const current = engine.get();
    if (!current || !allowed.get()) return;
    // A frame repeated at the same timestamp has nothing to step (and must not spawn twice).
    if (info.timeSincePreviousFrame === 0) return;
    const v = current.values;
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 50) / 1000;
    const t = motion.time.get();
    const position = motion.position.get();
    const index = motion.powerIndex.get();
    const fx = motion.fx.get();
    const finger = touch.get();
    contact.set(contact.get() + ((finger[4] > 0 ? 1 : 0) - contact.get()) * Math.min(1, dt * 14));
    frameNumber.set((frameNumber.get() + 1) % 1000000);
    v[0] = width; v[1] = height; v[2] = current.pixelWidth / width; v[3] = dt;
    v[4] = t * LOOP; v[5] = position; v[6] = frameNumber.get(); v[7] = Math.PI * 2 * (t % 1);
    v[8] = screenHeight; v[9] = stageTop; v[10] = stageBottom; v[11] = drift;
    v[12] = finger[0]; v[13] = finger[1] + heroTop; v[14] = finger[2]; v[15] = finger[3];
    v[16] = contact.get(); v[17] = motion.pet.get(); v[18] = 70 * scale; v[19] = 1;
    const tapped = tap.get();
    v[20] = tapped[0]; v[21] = tapped[1] + heroTop; v[22] = tapped[2] > 0 ? (info.timestamp - tapped[2]) / 1000 : -1; v[23] = 0;
    const landing = motion.landing.get();
    const landed = motion.landingIndex.get();
    v[24] = width / 2; v[25] = heroTop + top + RIGS[landed].feet * k; v[26] = landing < 1 ? landing * LAND_MS / 1000 : -1; v[27] = 0;
    // Down follows the phone's tilt, so everything buoyant rises away from the ground it feels.
    const [tx] = tilt.get();
    const gx = tx * 0.8;
    const length = Math.hypot(gx, 1);
    v[28] = gx / length; v[29] = 1 / length; v[30] = 0; v[31] = 0;
    v[32] = lastFx.get() > fx ? -1 : lastFx.get(); v[33] = fx; v[34] = index; v[35] = unit;
    lastFx.set(fx);
    // Particles are born from the posed body at the moment they leave it.
    const emitter = EMITTERS[index] ?? EMITTERS[0];
    const { exchange, pose } = creaturePose(index, motion);
    const body = { index, size, pad, left, top, heroTop, width, screenHeight, scale, exchange, driftPx: drift, pose };
    const a = bodyPoint(body, emitter.a[0], emitter.a[1]);
    const b = bodyPoint(body, emitter.b[0], emitter.b[1]);
    const ta = bodyPoint(body, emitter.a[0] + emitter.aimA[0] * 24, emitter.a[1] + emitter.aimA[1] * 24);
    const tb = bodyPoint(body, emitter.b[0] + emitter.aimB[0] * 24, emitter.b[1] + emitter.aimB[1] * 24);
    v[36] = a[0]; v[37] = a[1]; v[38] = ta[0] - a[0]; v[39] = ta[1] - a[1];
    v[40] = b[0]; v[41] = b[1]; v[42] = tb[0] - b[0]; v[43] = tb[1] - b[1];
    const flame = RIGS[0].flame;
    // Embers leave the plume's tip, which rises straight up from the tail's tip (CREATURE_LIFE).
    const tip = flame && index === 0 ? bodyPointFrom(body, flame.base[0], flame.base[1], -flame.height * (1 + 1.25 * pose.flameGrow) * 0.06, -flame.height * (1 + 1.25 * pose.flameGrow) * 0.95) : a;
    v[44] = tip[0]; v[45] = tip[1]; v[46] = 15 * unit * (1 + 0.6 * pose.flameGrow); v[47] = heroTop + top + RIGS[index].feet * k;
    try { particleFrame(current); }
    catch (error) {
      engine.set(null);
      running.set(false);
      scheduleOnRN(reportFailure, current.generation, String(error));
    }
  }, false);

  useEffect(() => {
    allowed.set(focused && foreground && !reduced);
    if (!focused || !foreground || reduced) { frame.setActive(false); running.set(false); }
  }, [allowed, focused, foreground, frame, reduced, running]);

  useEffect(() => {
    if (!laidOut || !frontLaidOut || !focused || !foreground || reduced) return;
    let live = true;
    let failed = false;
    let releasing = false;
    let device: GPUDevice | undefined;
    let resources: ParticleResources | undefined;
    let contexts: RNCanvasContext[] = [];
    const owner = ++generation.current;
    const release = () => {
      if (releasing) return;
      releasing = true;
      frame.setActive(false);
      running.set(false);
      void runOnUIAsync((stored: typeof engine, owned: RNCanvasContext[], token: number) => {
        'worklet';
        const latest = stored.get();
        if (latest?.generation === token) stored.set(null);
        if (!latest || latest.generation <= token) {
          for (const context of owned) { try { context.unconfigure(); } catch { /* Surface may already be detached. */ } }
        }
      }, engine, contexts, owner).catch(() => { /* Release resources even if the surface was detached. */ }).finally(() => {
        for (const made of resources?.buffers ?? []) { try { made.destroy(); } catch { /* Already lost. */ } }
        try { device?.destroy(); } catch { /* Already lost. */ }
      });
    };
    const fail = (error: unknown) => {
      if (!live || failed || generation.current !== owner) return;
      failed = true;
      clearTimeout(deadline);
      release();
      if (__DEV__) console.info('[Habitat particles unavailable]', error instanceof Error ? error.message : String(error));
      callbacks.current.onUnavailable?.();
    };
    const deadline = setTimeout(() => fail(new Error('Habitat particle setup timed out.')), 15000);
    failure.current = (token, reason) => { if (token === owner) fail(reason); };
    const current = () => live && !failed && generation.current === owner;
    const start = async () => {
      webgpu.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('WebGPU did not expose a GPU provider.');
      const adapter = await gpu.requestAdapter();
      if (!current()) return;
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('A hardware WebGPU adapter is unavailable.');
      const requested = await adapter.requestDevice();
      if (!current()) { requested.destroy(); return; }
      device = requested;
      device.addEventListener('uncapturederror', event => fail(event.error));
      void device.lost.then(info => { if (current()) fail(new Error(`Habitat GPU lost: ${info.message || info.reason}`)); }).catch(fail);
      // Particles are small and bright, so they get the screen's density (up to 2x).
      const pixelScale = Math.min(PixelRatio.get(), 2, 1600 / width, 2000 / height);
      const format = gpu.getPreferredCanvasFormat();
      device.pushErrorScope('validation');
      try { resources = await createParticleResources(device, format, width, height, pixelScale); }
      finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
      if (!current()) return;
      const back = canvas.current?.getContext('webgpu') ?? null;
      const ahead = front.current?.getContext('webgpu') ?? null;
      if (!back || !ahead) throw new Error('Habitat particle canvases have not mounted.');
      contexts = [back, ahead];
      const prepared: ParticleEngine = { ...resources, back, front: ahead, generation: owner };
      device.pushErrorScope('validation');
      try {
        await runOnUIAsync((target: ParticleEngine, install: HabitatWebGPU['installWebGPU'], stored: typeof engine) => {
          'worklet';
          install();
          for (const context of [target.back, target.front]) {
            context.canvas.width = target.pixelWidth;
            context.canvas.height = target.pixelHeight;
            context.configure({ device: target.device, format, alphaMode: 'premultiplied' });
          }
          stored.set(target);
        }, prepared, webgpu.installWebGPU, engine);
        await device.queue.onSubmittedWorkDone();
      } finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
      if (!current()) return;
      clearTimeout(deadline);
      running.set(true);
      callbacks.current.onReady?.();
      frame.setActive(true);
    };
    void start().catch(fail);
    return () => { live = false; clearTimeout(deadline); release(); };
  }, [canvas, engine, focused, foreground, frame, front, frontLaidOut, height, laidOut, reduced, running, webgpu, width]);

  const visibility = useAnimatedStyle(() => ({ opacity: allowed.get() ? 1 : 0 }));
  return <Animated.View pointerEvents="none" collapsable={false} accessible={false} testID="habitat-particle-field" style={[{ position: 'absolute', left: 0, top: 0, width, height }, visibility]}>
    <Canvas ref={canvas} pointerEvents="none" opaque={false} style={{ width, height }} onLayout={() => setLaidOut(true)} />
  </Animated.View>;
}
