import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Image, PixelRatio } from 'react-native';
import Animated, { useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { runOnUIAsync, scheduleOnRN } from 'react-native-worklets';
import type { RNCanvasContext } from 'react-native-webgpu';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { createDiamondResources, presentDiamond, type DiamondCommands, type DiamondResources } from './renderer';
import type { DiamondGpuProps } from './types';

type WebGPU = typeof import('react-native-webgpu');
type Commands = DiamondCommands & { generation: number };
const message = (error: unknown) => error instanceof Error ? error.message : String(error);

export function NativeDiamondGpu({ webgpu, ...props }: DiamondGpuProps & { webgpu: WebGPU }) {
  const { Canvas, useCanvasRef } = webgpu;
  const canvas = useCanvasRef();
  const [laidOut, setLaidOut] = useState(false);
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  const systemReduced = useReducedMotionPreference();
  const callbacks = useRef(props);
  const token = useRef(0);
  const handle = useRef<{ setActive: (active: boolean) => void } | null>(null);
  const failure = useRef<(generation: number, reason: string) => void>(() => {});
  const commands = useSharedValue<Commands | null>(null);
  const enabled = useSharedValue(false);
  const ready = useSharedValue(false);
  const running = useSharedValue(false);
  const allowed = useSharedValue(appActive && !systemReduced);
  const { frame, asset, size } = props;

  useEffect(() => { callbacks.current = props; });
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => setAppActive(state === 'active'));
    return () => subscription.remove();
  }, []);
  useEffect(() => { allowed.set(appActive && !systemReduced); }, [allowed, appActive, systemReduced]);

  const wake = useCallback((generation: number) => {
    if (token.current === generation && allowed.get() && ready.get()) {
      running.set(true); handle.current?.setActive(true);
    }
  }, [allowed, ready, running]);
  const stop = useCallback((generation: number) => {
    if (token.current !== generation) return;
    const current = frame.get();
    if (allowed.get() && enabled.get() && current.active && current.visible && !current.reduced) return;
    handle.current?.setActive(false);
  }, [allowed, enabled, frame]);
  const reportFailure = useCallback((generation: number, reason: string) => failure.current(generation, reason), []);

  const clock = useFrameCallback(() => {
    const current = commands.get();
    if (!current || !enabled.get() || !allowed.get()) {
      if (running.get()) { running.set(false); scheduleOnRN(stop, current?.generation ?? 0); }
      return;
    }
    const snapshot = frame.get();
    try {
      if (!snapshot.active || !snapshot.visible || snapshot.reduced) {
        // Paused material remains in the surface; the reaction below presents
        // the frozen final pose once, without substituting a clean base.
        if (running.get()) { running.set(false); scheduleOnRN(stop, current.generation); }
        return;
      }
      // One UI runtime owns configure/currentTexture/encoders/present.
      presentDiamond(current, snapshot, true);
    } catch (error) {
      enabled.set(false); running.set(false); ready.set(false);
      scheduleOnRN(reportFailure, current.generation, String(error));
    }
  }, false);
  useEffect(() => { handle.current = clock; return () => clock.setActive(false); }, [clock]);

  useAnimatedReaction(() => ({ snapshot: frame.get(), ready: ready.get(), allowed: allowed.get() }), (now, before) => {
    if (!now.ready || !now.allowed || !enabled.get()) return;
    const current = commands.get();
    if (!current) return;
    const snapshot = now.snapshot;
    const previous = before?.snapshot;
    const visible = snapshot.visible && !snapshot.reduced;
    const advancing = snapshot.active && visible;
    if (advancing) {
      if (!previous?.active || !previous.visible || previous.reduced || !before?.ready || !before?.allowed || !running.get()) {
        running.set(true); scheduleOnRN(wake, current.generation);
      }
      return;
    }
    // A paused preview/overlay may acquire readiness or receive a new pinned
    // pose. Present that snapshot on UI once, with no JS frame bridge/clock.
    const changed = !before?.ready || !before?.allowed || !previous || previous.active !== snapshot.active ||
      previous.visible !== snapshot.visible || previous.reduced !== snapshot.reduced || previous.phase !== snapshot.phase ||
      previous.progress !== snapshot.progress || previous.lightX !== snapshot.lightX || previous.lightY !== snapshot.lightY;
    if (changed) {
      try { presentDiamond(current, snapshot, visible); }
      catch (error) {
        enabled.set(false); ready.set(false);
        scheduleOnRN(reportFailure, current.generation, String(error));
      }
    }
    if (running.get()) { running.set(false); scheduleOnRN(stop, current.generation); }
  }, [reportFailure, stop, wake]);

  useEffect(() => {
    if (!laidOut || !appActive || systemReduced) return;
    let live = true, failed = false, releasing = false;
    let device: GPUDevice | undefined;
    let resources: DiamondResources | undefined;
    let context: RNCanvasContext | null = null;
    const generation = ++token.current;
    const loading = new AbortController();
    ready.set(false); enabled.set(false); running.set(false);
    callbacks.current.onReady?.(false);
    const release = () => {
      if (releasing) return;
      releasing = true; loading.abort(); clock.setActive(false);
      enabled.set(false); ready.set(false); running.set(false);
      void runOnUIAsync((stored: typeof commands, ownedContext: RNCanvasContext | null, owner: number) => {
        'worklet';
        const latest = stored.get();
        if (latest?.generation === owner) stored.set(null);
        if (!latest || latest.generation <= owner) {
          try { ownedContext?.unconfigure(); } catch { /* The surface may already be detached. */ }
        }
      }, commands, context, generation).catch(() => { /* Detached surface; release owned resources below. */ }).finally(() => {
        try { resources?.texture.destroy(); } catch { /* Already lost. */ }
        try { resources?.uniform.destroy(); } catch { /* Already lost. */ }
        try { device?.destroy(); } catch { /* Already lost. */ }
      });
    };
    const fail = (error: unknown) => {
      if (!live || failed || token.current !== generation) return;
      failed = true; clearTimeout(deadline); release();
      if (__DEV__) console.info(`[Prismatic WebGPU unavailable] ${message(error)}`);
      try { callbacks.current.onReady?.(false); callbacks.current.onUnavailable?.(message(error)); } catch { /* Reporting cannot break the fallback. */ }
    };
    const deadline = setTimeout(() => fail('WebGPU initialization exceeded its bounded setup window.'), 15000);
    failure.current = (owner, reason) => { if (owner === generation) fail(reason); };
    const isLive = () => live && !failed && token.current === generation;
    const start = async () => {
      webgpu.installWebGPU();
      const gpu = globalThis.navigator?.gpu;
      if (!gpu) throw new Error('WebGPU installation did not expose navigator.gpu.');
      const adapter = await gpu.requestAdapter();
      if (!isLive()) return;
      if (!adapter || adapter.info?.isFallbackAdapter) throw new Error('A native hardware WebGPU adapter is unavailable.');
      const requested = await adapter.requestDevice();
      if (!isLive()) { requested.destroy(); return; }
      device = requested;
      device.addEventListener('uncapturederror', event => fail(event.error));
      void device.lost.then(info => { if (isLive()) fail(`WebGPU device lost: ${info.message || info.reason}`); }).catch(fail);
      const uri = typeof asset === 'string' ? asset : Image.resolveAssetSource(asset)?.uri;
      if (!uri) throw new Error('Diamond source image is unavailable.');
      const response = await fetch(uri, { signal: loading.signal });
      if (!response.ok && response.status !== 0) throw new Error('Diamond source image could not be loaded.');
      const bitmap = await createImageBitmap(await response.arrayBuffer(), { colorSpaceConversion: 'none', premultiplyAlpha: 'none' });
      try {
        if (!isLive()) return;
        const pixels = Math.max(1, Math.round(size * PixelRatio.get()));
        const format = gpu.getPreferredCanvasFormat();
        device.pushErrorScope('validation');
        try { resources = await createDiamondResources(device, bitmap, format, size, pixels); }
        finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
        if (!isLive()) { resources.texture.destroy(); resources.uniform.destroy(); return; }
        context = canvas.current?.getContext('webgpu') ?? null;
        if (!context) throw new Error('Diamond native canvas is not mounted.');
        const { texture: _texture, ...render } = resources;
        const prepared: Commands = { ...render, context, generation };
        device.pushErrorScope('validation');
        try {
          await runOnUIAsync((target: Commands, install: WebGPU['installWebGPU'], sourceFrame: typeof frame) => {
            'worklet';
            install();
            target.context.canvas.width = target.pixels;
            target.context.canvas.height = target.pixels;
            target.context.configure({ device: target.device, format, alphaMode: 'premultiplied' });
            const snapshot = sourceFrame.get();
            presentDiamond(target, snapshot, snapshot.visible && !snapshot.reduced);
          }, prepared, webgpu.installWebGPU, frame);
          await device.queue.onSubmittedWorkDone();
        } finally { const error = await device.popErrorScope(); if (error) throw new Error(error.message); }
        if (!isLive()) return;
        const initialSnapshot = await runOnUIAsync((stored: typeof commands, target: Commands, enabledValue: typeof enabled, readyValue: typeof ready, sourceFrame: typeof frame) => {
          'worklet';
          const snapshot = sourceFrame.get();
          presentDiamond(target, snapshot, snapshot.visible && !snapshot.reduced);
          stored.set(target); enabledValue.set(true); readyValue.set(true);
          return { phase: snapshot.phase, strength: snapshot.visible && !snapshot.reduced ? Math.min(1,Math.max(0,snapshot.progress)) : 0,
            progress: snapshot.progress, visible: snapshot.visible, active: snapshot.active, reduced: snapshot.reduced,
            lightX: snapshot.lightX, lightY: snapshot.lightY };
        }, commands, prepared, enabled, ready, frame);
        clearTimeout(deadline);
        if (isLive()) {
          console.info('[Prismatic WebGPU] native device, WGSL probe and first surface present succeeded.', { pixels, logicalSize: size, format });
          if (__DEV__) console.info(`[Prismatic WebGPU snapshot] ${JSON.stringify(initialSnapshot)}`);
          callbacks.current.onReady?.(true);
          wake(generation);
        }
      } finally { bitmap.close(); }
    };
    void start().catch(fail);
    return () => {
      live = false; clearTimeout(deadline); release();
      try { callbacks.current.onReady?.(false); } catch { /* Parent may already be unmounted. */ }
    };
  }, [allowed, appActive, asset, canvas, clock, commands, enabled, frame, laidOut, ready, running, size, systemReduced, wake, webgpu]);

  const visibility = useAnimatedStyle(() => ({ opacity: ready.get() && allowed.get() && frame.get().visible && !frame.get().reduced ? 1 : 0 }));
  return <Animated.View pointerEvents="none" accessible={false} style={[{ width: size, height: size }, visibility]}>
    <Canvas ref={canvas} opaque={false} style={{ width: size, height: size }} onLayout={() => setLaidOut(true)} />
  </Animated.View>;
}
