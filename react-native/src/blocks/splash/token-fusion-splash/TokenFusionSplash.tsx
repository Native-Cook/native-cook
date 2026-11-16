import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { forwardRef, isValidElement, useCallback, useEffect, useImperativeHandle, useMemo, useRef, type ReactNode } from 'react';
import { AppState, Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useAnimatedProps,
  withTiming,
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { Canvas, Circle, Fill, FilterMode, MipmapMode, ImageShader, Shader, Skia, type SkPath } from '@shopify/react-native-skia';

import { makeGlyphAtlas } from './tfs-atlas';
import { haptic } from './tfs-haptics';
import {
  LOOP,
  PHYSICS,
  capture,
  clamp,
  grab,
  liveFrom,
  copyLive,
  mix,
  movePointer,
  releasePointer,
  sceneAt,
  sceneOf,
  seg,
  stepLive,
  type Scene,
} from './tfs-motion';
import { TFS_SKSL } from './tfs-shaders';
import { COPY, DEFAULT_TOKENS, MARK, comp, makeLayout, ref, rgb, sys } from './tfs-theme';
import { uniformsFor } from './tfs-uniforms';

export type TokenFusionHandle = { play(): void; reset(): void; merge(id: string): void; flood(): void };
export type TokenFusionToken = { id: string; color: string; glyph: SkPath | string | ReactNode; label?: string };
export type TokenFusionProps = {
  tokens?: TokenFusionToken[];
  core?: { color: string; hot?: string; shade?: string; mark: SkPath | string };
  wordmark?: string;
  mode?: 'splash' | 'interactive';
  merged?: string[];
  defaultMerged?: string[];
  onMerge?: (id: string, merged: string[]) => void;
  onRelease?: (id: string) => void;
  onComplete?: () => void;
  idleMs?: number | null;
  touchCore?: boolean;
  theme?: { background?: string; ring?: string; ink?: string; wordmarkFont?: string; grain?: number };
  autoPlay?: boolean;
};
const defaultTokens: TokenFusionToken[] = DEFAULT_TOKENS.map((token) => ({ ...token }));
const defaultCore = { color: sys.accent.base, hot: sys.hero.hot, shade: sys.hero.shade, mark: MARK };
const EMPTY: string[] = [];
function isPath(value: unknown): value is SkPath {
  return !!value && typeof value === 'object' && 'toSVGString' in value;
}
function blendScene(a: Scene, b: Scene, p: number): Scene {
  'worklet';
  return {
    x: mix(a.x, b.x, p),
    y: mix(a.y, b.y, p),
    r: mix(a.r, b.r, p),
    sphere: mix(a.sphere, b.sphere, p),
    stretch: mix(a.stretch, b.stretch, p),
    axis: mix(a.axis, b.axis, p),
    ringScale: mix(a.ringScale, b.ringScale, p),
    mark: mix(a.mark, b.mark, p),
    markSize: mix(a.markSize, b.markSize, p),
    cut: mix(a.cut, b.cut, p),
    word: mix(a.word, b.word, p),
    wordY: mix(a.wordY, b.wordY, p),
    flood: mix(a.flood, b.flood, p),
    coins: b.coins.map((c, i) => ({
      ...c,
      x: mix(a.coins[i].x, c.x, p),
      y: mix(a.coins[i].y, c.y, p),
      r: mix(a.coins[i].r, c.r, p),
      reach: mix(a.coins[i].reach, c.reach, p),
      dye: mix(a.coins[i].dye, c.dye, p),
    })),
  };
}
/** A live SDF splash. The default export demo below opts into repeating the 7.5 s story. */
export const TokenFusionSplash = forwardRef<TokenFusionHandle, TokenFusionProps>(function TokenFusionSplash(
  {
    tokens = defaultTokens,
    core = defaultCore,
    wordmark = COPY.wordmark,
    mode = 'interactive',
    merged,
    defaultMerged = EMPTY,
    onMerge,
    onRelease,
    onComplete,
    idleMs = 5000,
    touchCore = true,
    theme,
    autoPlay = false,
  },
  handle,
) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotion();
  const [fontReady] = useFonts({ TFSOutfit: require('./assets/fonts/Outfit-Regular.ttf') });
  const items = useMemo(() => {
    const unique = tokens.filter((t, i, a) => a.findIndex((v) => v.id === t.id) === i).slice(0, 6);
    return unique.length >= 2 ? unique : defaultTokens;
  }, [tokens]);
  const count = items.length;
  const layout = useMemo(() => makeLayout(width, height, insets.top, insets.bottom), [width, height, insets.top, insets.bottom]);
  const effect = useMemo(() => {
    try {
      return Skia.RuntimeEffect.Make(TFS_SKSL);
    } catch {
      return null;
    }
  }, []);
  const atlas = useMemo(
    () => makeGlyphAtlas([core.mark, ...items.map((t) => (typeof t.glyph === 'string' || isPath(t.glyph) ? t.glyph : null))]),
    [core.mark, items],
  );
  // SkImage is a GC-owned host object. Do not dispose it from an effect cleanup:
  // Strict Mode replays that cleanup while the retained Skia tree still references it.
  const colors = useMemo(() => items.map((t) => rgb(t.color)), [items]);
  const palette = useMemo(
    () => ({
      base: rgb(core.color),
      hot: rgb(core.hot ?? sys.hero.hot),
      shade: rgb(core.shade ?? sys.hero.shade),
      page: rgb(theme?.background ?? sys.surface.page),
      ring: rgb(theme?.ring ?? sys.stroke.ring),
      ink: rgb(theme?.ink ?? sys.ink.primary),
    }),
    [core.color, core.hot, core.shade, theme?.background, theme?.ring, theme?.ink],
  );
  const grain = clamp(theme?.grain ?? ref.material.grain, 0, 0.06);
  const initial = useMemo(() => {
    const m = liveFrom(sceneAt(0, count));
    for (let i = 0; i < count; i++)
      if ((merged ?? defaultMerged).includes(items[i].id)) {
        m.coins[i].state = 4;
        m.coins[i].r = 0;
        m.mergedMask |= 1 << i;
      }
    return m;
    // Initial selection only; controlled updates are reconciled below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [count]);
  const live = useSharedValue(initial);
  const scene = useSharedValue<Scene>(sceneOf(initial, reduce));
  const lagOne = useSharedValue(sceneOf(initial, reduce));
  const lagTwo = useSharedValue(sceneOf(initial, reduce));
  const time = useSharedValue(0);
  const playing = useSharedValue(autoPlay || mode === 'splash');
  const pinned = useSharedValue(-1);
  const complete = useSharedValue(false);
  const canvasOpacity = useSharedValue(1);
  useEffect(() => {
    live.set(initial);
    scene.set(sceneOf(initial, reduce));
    complete.set(false);
  }, [initial, live, scene, reduce, complete]);

  const resumeStart = useSharedValue(-1);
  const resumeScene = useSharedValue(sceneAt(0, count));
  const controlledMask = useSharedValue(-1);
  const command = useSharedValue({ type: 0, index: 0, serial: 0 });
  const pendingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stopFrame = useRef<() => void>(() => undefined);
  const propsRef = useRef({ onMerge, onRelease, onComplete, items });
  useEffect(() => {
    propsRef.current = { onMerge, onRelease, onComplete, items };
  }, [onMerge, onRelease, onComplete, items]);
  const dispatchEvents = useCallback((add: number, remove: number, mask: number, cue: number, finished: boolean, stop = true) => {
    const props = propsRef.current;
    const ids = props.items.filter((_, i) => (mask & (1 << i)) !== 0).map((t) => t.id);
    props.items.forEach((token, i) => {
      if (add & (1 << i)) props.onMerge?.(token.id, ids);
      if (remove & (1 << i)) props.onRelease?.(token.id);
    });
    if (cue) haptic(cue);
    if (finished) {
      if (stop) stopFrame.current();
      props.onComplete?.();
    }
  }, []);
  const frame = useFrameCallback((info) => {
    'worklet';
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.667) / 1000, 0.05);
    if (pinned.get() >= 0 || complete.get()) return;
    let current: Scene;
    if (playing.get() && !reduce) {
      const next = time.get() + dt;
      if (mode === 'splash' && next >= 5.7) {
        time.set(5.5);
        playing.set(false);
        current = sceneAt(5.5, count);
        if (!complete.get()) {
          complete.set(true);
          scheduleOnRN(dispatchEvents, 0, 0, 0, 0, true);
        }
      } else {
        time.set(next % LOOP);
        current = sceneAt(next, count);
      }
    } else {
      const m = copyLive(live.get());
      stepLive(m, dt, reduce, controlledMask.get() < 0 || controlledMask.get() === (1 << count) - 1);
      const cmd = command.get();
      if (cmd.type & 1) for (let i = 0; i < count; i++) if (cmd.index & (1 << i)) capture(m, i);
      if (cmd.type & 2) m.flood = Math.max(m.flood, 0.001);
      if (cmd.type) command.set({ ...cmd, type: 0, index: 0 });
      const mask = controlledMask.get();
      if (mask >= 0)
        for (let i = 0; i < count; i++) {
          const c = m.coins[i];
          if (mask & (1 << i) && c.state !== 3 && c.state !== 4) {
            capture(m, i);
            m.mergeEvents &= ~(1 << i);
            m.cue = 0;
          }
          if (!(mask & (1 << i)) && c.state === 4 && c.age > 0.5) {
            c.state = 5;
            c.age = 0;
            m.mergedMask &= ~(1 << i);
          }
        }
      const held = m.corePointer >= 0 || m.coins.some((c) => c.pointer >= 0);
      if (!held && idleMs !== null && !autoPlay && m.now - m.lastTouch >= idleMs / 1000) m.flood = Math.max(m.flood, 0.001);
      current = sceneOf(m, reduce);
      if (!held && autoPlay && mode === 'interactive' && mask < 0 && m.now - m.lastTouch > PHYSICS.resumeAfter) {
        if (resumeStart.get() < 0) {
          resumeStart.set(m.now);
          resumeScene.set(current);
        }
        const p = seg(m.now - resumeStart.get(), 0, PHYSICS.resumeDuration);
        current = blendScene(resumeScene.get(), sceneAt(0, count), p);
        if (p >= 1) {
          time.set(0);
          playing.set(true);
          const fresh = liveFrom(sceneAt(0, count));
          Object.assign(m, fresh);
          resumeStart.set(-1);
        }
      }
      const finished = m.flood > 0.995 && !m.completed;
      if (finished) {
        if (mode === 'splash' || !autoPlay) complete.set(true);
        m.completed = true;
      }
      if (m.mergeEvents || m.releaseEvents || m.cue || finished) {
        scheduleOnRN(
          dispatchEvents,
          m.mergeEvents,
          m.releaseEvents,
          m.mergedMask,
          finished ? 5 : m.cue,
          finished,
          mode === 'splash' || !autoPlay,
        );
        m.mergeEvents = 0;
        m.releaseEvents = 0;
        m.cue = 0;
      }
      live.set(m);
    }
    lagTwo.set(lagOne.get());
    lagOne.set(scene.get());
    scene.set(current);
  }, false);
  useEffect(() => {
    stopFrame.current = () => frame.setActive(false);
  }, [frame]);
  const awaken = useCallback(() => {
    if (!reduce) frame.setActive(true);
  }, [frame, reduce]);
  const reset = useCallback(() => {
    if (pendingTimer.current) clearTimeout(pendingTimer.current);
    const m = liveFrom(sceneAt(0, count));
    live.set(m);
    scene.set(sceneAt(0, count));
    lagOne.set(sceneAt(0, count));
    lagTwo.set(sceneAt(0, count));
    pinned.set(-1);
    time.set(0);
    complete.set(false);
    resumeStart.set(-1);
    playing.set(false);
    frame.setActive(!reduce && mode === 'interactive');
  }, [count, live, scene, lagOne, lagTwo, pinned, time, complete, resumeStart, playing, frame, reduce, mode]);
  // Reduced Motion changes meaning with fades only, without subscribing a perpetual frame callback.
  const applyReduced = useCallback(
    (index: number) => {
      const m = copyLive(live.get());
      if (index >= 0) {
        m.coins[index].state = 4;
        m.coins[index].r = 0;
        m.mergedMask |= 1 << index;
        dispatchEvents(1 << index, 0, m.mergedMask, 3, false);
      }
      if (index < 0 || m.coins.every((c) => c.state === 4)) {
        m.flood = 1;
        complete.set(true);
        dispatchEvents(0, 0, m.mergedMask, 5, true);
      }
      live.set(m);
      scene.set(sceneOf(m, true));
    },
    [live, scene, complete, dispatchEvents],
  );
  const reducedAction = useCallback(
    (index: number) => {
      canvasOpacity.set(withTiming(0, { duration: 80, reduceMotion: ReduceMotion.Never }));
      if (pendingTimer.current) clearTimeout(pendingTimer.current);
      pendingTimer.current = setTimeout(() => {
        applyReduced(index);
        canvasOpacity.set(withTiming(1, { duration: 160, reduceMotion: ReduceMotion.Never }));
      }, 80);
    },
    [applyReduced, canvasOpacity],
  );
  const play = useCallback(() => {
    reset();
    if (reduce) reducedAction(-1);
    else {
      playing.set(true);
      awaken();
    }
  }, [reset, reduce, reducedAction, playing, awaken]);
  const request = useCallback(
    (type: number, index = -1) => {
      if (reduce) {
        reducedAction(index);
        return;
      }
      if (playing.get()) live.set(liveFrom(scene.get(), sceneAt(time.get() + 1 / 120, count)));
      complete.set(false);
      live.modify((m) => {
        m.lastTouch = m.now;
        return m;
      });
      pinned.set(-1);
      playing.set(false);
      resumeStart.set(-1);
      command.modify((c) => ({ type: c.type | type, index: c.index | (index < 0 ? 0 : 1 << index), serial: c.serial + 1 }));
      awaken();
    },
    [reduce, reducedAction, playing, live, scene, pinned, resumeStart, command, awaken, complete, time, count],
  );
  useImperativeHandle(
    handle,
    () => ({
      play,
      reset,
      merge: (id) => {
        const i = items.findIndex((t) => t.id === id);
        if (i >= 0) request(1, i);
      },
      flood: () => request(2),
    }),
    [play, reset, items, request],
  );
  useEffect(() => {
    controlledMask.set(merged === undefined ? -1 : items.reduce((mask, t, i) => (merged.includes(t.id) ? mask | (1 << i) : mask), 0));
    if (merged !== undefined) {
      playing.set(false);
      if (reduce) {
        const m = copyLive(live.get());
        for (let i = 0; i < items.length; i++) {
          const yes = merged.includes(items[i].id);
          m.coins[i].r = yes ? 0 : 22;
          m.coins[i].state = yes ? 4 : 0;
        }
        live.set(m);
        scene.set(sceneOf(m, true));
      } else awaken();
    }
  }, [merged, items, controlledMask, playing, awaken, reduce, live, scene]);
  useEffect(() => {
    frame.setActive(!!effect && !!atlas && fontReady && !reduce && !complete.get());
    return () => {
      frame.setActive(false);
      if (pendingTimer.current) clearTimeout(pendingTimer.current);
    };
  }, [effect, atlas, fontReady, reduce, frame, complete]);
  useEffect(() => {
    if (!reduce || mode !== 'splash') return;
    pendingTimer.current = setTimeout(() => reducedAction(-1), 240);
    return () => {
      if (pendingTimer.current) clearTimeout(pendingTimer.current);
    };
  }, [reduce, mode, reducedAction]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => frame.setActive(state === 'active' && !reduce && !complete.get()));
    return () => sub.remove();
  }, [frame, reduce, complete]);
  useEffect(() => {
    if (!__DEV__) return;
    const hook = {
      pin: (t: number) => {
        frame.setActive(false);
        pinned.set(t);
        time.set(((t % LOOP) + LOOP) % LOOP);
        scene.set(sceneAt(t, count));
        lagTwo.set(sceneAt(Math.max(0, t - 2 / 60), count));
      },
      at: (t: number) => sceneAt(t, count),
      play,
      reset,
      progress: (p: number) => {
        frame.setActive(false);
        pinned.set(p * LOOP);
        time.set((((p % 1) + 1) % 1) * LOOP);
        scene.set(sceneAt(p * LOOP, count));
        lagTwo.set(sceneAt(Math.max(0, p * LOOP - 2 / 60), count));
      },
      state: () => ({ time: time.get(), scene: scene.get(), live: live.get() }),
    };
    (globalThis as Record<string, unknown>).__tfs = hook;
    return () => {
      if ((globalThis as Record<string, unknown>).__tfs === hook) delete (globalThis as Record<string, unknown>).__tfs;
    };
  }, [frame, pinned, scene, lagTwo, count, play, reset, time, live]);
  const skip = useCallback(() => {
    const m = copyLive(live.get());
    m.flood = 1;
    m.completed = true;
    live.set(m);
    scene.set(sceneOf(m, true));
    complete.set(true);
    frame.setActive(false);
    onComplete?.();
  }, [live, scene, complete, frame, onComplete]);
  const minX = (comp.touch.edge + 22 * layout.scale - layout.offsetX) / layout.scale;
  const maxX = (width - comp.touch.edge - 22 * layout.scale - layout.offsetX) / layout.scale;
  const minY = (insets.top + comp.touch.edge + 22 * layout.scale - layout.offsetY) / layout.scale;
  const maxY = (height - insets.bottom - comp.touch.edge - 22 * layout.scale - layout.offsetY) / layout.scale;
  // Gesture builder stores these callbacks; it does not call the RN callback during render.
  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .hitSlop({ left: -comp.touch.backInset, bottom: -insets.bottom })
        .onTouchesDown((event, manager) => {
          manager.activate();
          if (reduce) return;
          const m =
            playing.get() || pinned.get() >= 0
              ? liveFrom(scene.get(), sceneAt((pinned.get() >= 0 ? pinned.get() : time.get()) + 1 / 120, count))
              : copyLive(live.get());
          let any = false;
          for (const touch of event.changedTouches)
            any = grab(m, touch.id, (touch.x - layout.offsetX) / layout.scale, (touch.y - layout.offsetY) / layout.scale, touchCore) || any;
          if (!any) return;
          playing.set(false);
          pinned.set(-1);
          resumeStart.set(-1);
          live.set(m);
          scene.set(sceneOf(m));
          scheduleOnRN(awaken);
        })
        .onTouchesMove((event) => {
          if (reduce) return;
          const m = copyLive(live.get());
          for (const touch of event.changedTouches)
            movePointer(
              m,
              touch.id,
              (touch.x - layout.offsetX) / layout.scale,
              (touch.y - layout.offsetY) / layout.scale,
              minX,
              maxX,
              minY,
              maxY,
            );
          live.set(m);
          scene.set(sceneOf(m));
        })
        // Callback is stored by RNGH; scheduling the RN handler does not read refs during render.
        // eslint-disable-next-line react-hooks/refs
        .onTouchesUp((event, manager) => {
          if (reduce) {
            for (const touch of event.changedTouches) {
              const x = (touch.x - layout.offsetX) / layout.scale,
                y = (touch.y - layout.offsetY) / layout.scale;
              const s = scene.get();
              const i = s.coins.findIndex((c) => c.r > 1 && Math.hypot(c.x - x, c.y - y) < 30);
              if (i >= 0) scheduleOnRN(reducedAction, i);
            }
          } else {
            const m = copyLive(live.get());
            for (const touch of event.changedTouches) releasePointer(m, touch.id);
            live.set(m);
          }
          if (event.allTouches.length === 0) manager.end();
        })
        .onTouchesCancelled((event, manager) => {
          const m = copyLive(live.get());
          for (const touch of event.changedTouches) releasePointer(m, touch.id, true);
          live.set(m);
          manager.fail();
        }),
    [
      reduce,
      playing,
      pinned,
      scene,
      live,
      layout,
      touchCore,
      resumeStart,
      awaken,
      minX,
      maxX,
      minY,
      maxY,
      reducedAction,
      time,
      count,
      insets.bottom,
    ],
  );
  const outputP3 = Platform.OS === 'ios' ? 1 : 0;
  const uniforms = useDerivedValue(() => uniformsFor(scene.get(), lagTwo.get(), layout, colors, palette, grain, outputP3));
  const wordAccessibility = useAnimatedProps(() => ({ accessibilityElementsHidden: scene.get().word < 0.01 }));
  const wordStyle = useAnimatedStyle(() => ({
    opacity: fontReady ? scene.get().word : 0,
    transform: [{ translateY: (scene.get().wordY - comp.wordmark.y) * layout.scale }],
  }));
  const canvasStyle = useAnimatedStyle(() => ({ opacity: canvasOpacity.get() }));
  const fallbackRadius = useDerivedValue(() => scene.get().r * layout.scale);
  const fallbackX = useDerivedValue(() => scene.get().x * layout.scale + layout.offsetX);
  const fallbackY = useDerivedValue(() => scene.get().y * layout.scale + layout.offsetY);
  return (
    <View style={[styles.screen, { backgroundColor: theme?.background ?? sys.surface.page }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessibilityActions={[{ name: 'escape', label: COPY.skip }]}
          onAccessibilityAction={(event) => {
            if (event.nativeEvent.actionName === 'escape') skip();
          }}
        >
          <Animated.View style={[StyleSheet.absoluteFill, canvasStyle]} pointerEvents="none">
            <Canvas style={StyleSheet.absoluteFill} accessible={false}>
              {effect && atlas ? (
                <Fill>
                  <Shader source={effect} uniforms={uniforms}>
                    <ImageShader
                      image={atlas}
                      tx="clamp"
                      ty="clamp"
                      fit="none"
                      rect={{ x: 0, y: 0, width: 896, height: 128 }}
                      sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
                    />
                  </Shader>
                </Fill>
              ) : (
                <Circle cx={fallbackX} cy={fallbackY} r={fallbackRadius} color={core.color} />
              )}
            </Canvas>
          </Animated.View>
          <CoreAccessibility scene={scene} layout={layout} onActivate={skip} />
          {items.map((token, i) => (
            <TokenAccessibility key={token.id} token={token} index={i} scene={scene} layout={layout} onActivate={() => request(1, i)} />
          ))}
        </View>
      </GestureDetector>
      <Animated.Text
        animatedProps={wordAccessibility}
        pointerEvents="none"
        accessibilityRole="header"
        style={[
          styles.word,
          {
            top: layout.offsetY + comp.wordmark.y * layout.scale,
            left: 16,
            right: 16,
            color: theme?.ink ?? sys.ink.primary,
            fontFamily: theme?.wordmarkFont ?? ref.type.wordmark.family,
            fontSize: ref.type.wordmark.size * layout.scale,
            letterSpacing: ref.type.wordmark.tracking * ref.type.wordmark.size * layout.scale,
          },
          wordStyle,
        ]}
      >
        {wordmark}
      </Animated.Text>
    </View>
  );
});
type OverlayProps = { scene: SharedValue<Scene>; layout: ReturnType<typeof makeLayout>; onActivate: () => void };
function CoreAccessibility({ scene, layout, onActivate }: OverlayProps) {
  const style = useAnimatedStyle(() => ({
    transform: [
      { translateX: scene.get().x * layout.scale + layout.offsetX - 60 },
      { translateY: scene.get().y * layout.scale + layout.offsetY - 60 },
    ],
  }));
  return (
    <Animated.View
      accessible
      accessibilityRole="button"
      accessibilityLabel={COPY.core}
      accessibilityHint="Activate to finish linking accounts"
      onAccessibilityTap={onActivate}
      style={[styles.coreTarget, style]}
      pointerEvents="none"
    />
  );
}
function TokenAccessibility({ token, index, scene, layout, onActivate }: OverlayProps & { token: TokenFusionToken; index: number }) {
  const style = useAnimatedStyle(() => {
    const c = scene.get().coins[index];
    if (!c) return { opacity: 0 };
    return {
      opacity: c.r > 1 ? 1 : 0,
      transform: [{ translateX: c.x * layout.scale + layout.offsetX - 24 }, { translateY: c.y * layout.scale + layout.offsetY - 24 }],
    };
  });
  const glyphStyle = useAnimatedStyle(() => {
    const c = scene.get().coins[index];
    return {
      opacity: c ? clamp((c.r - 10) / 8) : 0,
      transform: [{ scale: c ? (c.r / 22) * layout.scale : 1 }, { rotate: `${c?.lean ?? 0}rad` }],
    };
  });
  const animatedProps = useAnimatedProps(() => ({
    accessible: (scene.get().coins[index]?.r ?? 0) > 1,
    accessibilityElementsHidden: (scene.get().coins[index]?.r ?? 0) <= 1,
  }));
  return (
    <Animated.View
      animatedProps={animatedProps}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`Add ${token.label ?? token.id}`}
      accessibilityHint="Drag toward the core, or activate to merge"
      onAccessibilityTap={onActivate}
      style={[styles.tokenTarget, style]}
      pointerEvents="none"
    >
      {isValidElement(token.glyph) ? <Animated.View style={[styles.customGlyph, glyphStyle]}>{token.glyph}</Animated.View> : null}
    </Animated.View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1 },
  word: { position: 'absolute', textAlign: 'center' },
  coreTarget: { position: 'absolute', left: 0, top: 0, width: 120, height: 120 },
  tokenTarget: { position: 'absolute', left: 0, top: 0, width: 48, height: 48 },
  customGlyph: { position: 'absolute', left: 12, top: 12, width: 24, height: 24, alignItems: 'center', justifyContent: 'center' },
});
export { LOOP };
