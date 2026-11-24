// Louver Theme Switch — a theme change where the whole screen turns like a louver.
//
// The reusable piece. Give it `Screen`, a component that draws your screen for a theme; it keeps both
// themes mounted as real views, one over the other, snapshots each once (and again when `contentKey`
// changes), and during a change one Skia runtime effect (lts-shader.ts) turns fourteen slats in
// perspective, each with the light screen on its front and the dark one on its back. At rest the
// canvas is hidden and the live screen takes touches.
//
// autoPlay: LOOP = 8 s. A ghost finger taps Dark, the slats turn top to bottom, the dark screen holds,
// the finger taps Light and they turn back; frame 8 s equals frame 0. The control, or a vertical drag
// that scrubs the turn and commits or springs back on release, takes over at once; IDLE seconds after
// the last touch the screen returns to light and the loop restarts at t = 0. Reduced motion: the theme
// changes at once, no turn and no loop.
//
// Thread model: one useFrameCallback on the UI thread writes `amount` and the ghost. React state
// changes only when the turn crosses its middle, under the canvas, to swap which live view is on top.

import { Canvas, Fill, ImageShader, makeImageFromView, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentType, type RefObject, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { IDLE, LOOP, PROJECT, REST, SCRUB, TAP_TURN, clamp01, filmAmount, filmDir, ghostAt, seg, settleAt } from './lts-motion';
import { CAVITY_TONE, LTS_SKSL } from './lts-shader';

export { LOOP };

export type LouverTheme = 'light' | 'dark';

export type LouverScreenProps = {
  theme: LouverTheme;
  /** ask for a theme; the louver turns to it */
  select: (theme: LouverTheme) => void;
  /** put this on the control, so the autoplay's finger knows where to tap (the light screen's is measured) */
  controlRef: RefObject<View | null>;
  /** changes when this screen has gone out of sight: remount a native control with it, so it shows this screen's theme again */
  resetKey: number;
};

type Props<P extends object> = {
  /** your screen; it is mounted twice, once per theme */
  Screen: ComponentType<LouverScreenProps & P>;
  /** whatever else your screen takes */
  screenProps: P;
  /** change this when what the screens show changes, to take new snapshots */
  contentKey?: string | number;
  autoPlay?: boolean;
  onThemeChange?: (theme: LouverTheme) => void;
};

const EFFECT = Skia.RuntimeEffect.Make(LTS_SKSL);
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
const RUN_NONE = 0;
const RUN_LINEAR = 1;
const RUN_SPRING = 2;
const GHOST = 44;
const PIXEL_RATIO = PixelRatio.get();
/** let native controls lay out and finish animating before a snapshot, ms */
const SETTLE_MS = 380;

function tick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function LouverThemeSwitch<P extends object>({ Screen, screenProps, contentKey, autoPlay = true, onThemeChange }: Props<P>) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const canTurn = !reduced && EFFECT != null;
  const auto = autoPlay && canTurn;

  const rootRef = useRef<View>(null);
  const lightRef = useRef<View>(null);
  const darkRef = useRef<View>(null);
  const controlRef = useRef<View>(null);
  const spareControlRef = useRef<View>(null);
  const [images, setImages] = useState<{ light: SkImage; dark: SkImage } | null>(null);
  const [side, setSide] = useState<0 | 1>(0);
  const [resets, setResets] = useState({ light: 0, dark: 0 });

  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const ready = useSharedValue(0);
  const amount = useSharedValue(0);
  const dir = useSharedValue(1);
  /** which side React has put on top, and which side the UI thread last asked for */
  const committed = useSharedValue(0);
  const asked = useSharedValue(0);
  /** a theme asked for from JS: 0, 1, or -1 for nothing */
  const request = useSharedValue(-1);
  const down = useSharedValue(0);
  const grabFrom = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const running = useSharedValue(RUN_NONE);
  const runAt = useSharedValue(0);
  const runFrom = useSharedValue(0);
  const runTo = useSharedValue(0);
  const runSpeed = useSharedValue(0);
  const runFor = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const ghostP = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  /** the control in the root's coordinates: x, y, width, height */
  const control = useSharedValue<number[]>([0, 0, 0, 0]);

  const showSide = useCallback((next: number) => {
    setSide(next === 1 ? 1 : 0);
    // the screen going out of sight gets a fresh control
    setResets((r) => (next === 1 ? { ...r, light: r.light + 1 } : { ...r, dark: r.dark + 1 }));
    onThemeChange?.(next === 1 ? 'dark' : 'light');
  }, [onThemeChange]);

  useLayoutEffect(() => {
    committed.set(side);
  }, [side, committed]);

  // snapshot both screens once they have laid out, and again when their content changes
  useEffect(() => {
    if (!canTurn) return;
    let live = true;
    const timer = setTimeout(() => {
      void Promise.all([makeImageFromView(lightRef), makeImageFromView(darkRef)])
        .then(([light, dark]) => {
          if (!live || !light || !dark) return;
          setImages({ light, dark });
          const root = rootRef.current;
          const target = controlRef.current;
          if (root && target) {
            root.measureInWindow((rx, ry) => {
              target.measureInWindow((x, y, w, h) => {
                if (live) control.set([x - rx, y - ry, w, h]);
              });
            });
          }
          ready.set(1);
        })
        .catch(() => {});
    }, SETTLE_MS);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [canTurn, contentKey, width, height, control, ready]);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();

    const wanted = request.get();
    if (wanted >= 0) {
      request.set(-1);
      mode.set(MODE_TOUCH);
      lastTouch.set(now);
      ghostA.set(0);
      const from = amount.get();
      if (from !== wanted) {
        if (from <= REST || from >= 1 - REST) dir.set(wanted > from ? 1 : -1);
        runFrom.set(from);
        runTo.set(wanted);
        runFor.set(TAP_TURN * Math.abs(wanted - from));
        runAt.set(now);
        running.set(RUN_LINEAR);
      }
    }

    if (mode.get() === MODE_AUTO) {
      if (ready.get() === 0 || !auto) {
        autoStart.set(now);
      } else {
        const t = (now - autoStart.get()) % LOOP;
        amount.set(filmAmount(t));
        dir.set(filmDir(t));
        const g = ghostAt(t);
        const c = control.get();
        ghostA.set(g.alpha);
        ghostP.set(g.press);
        ghostX.set(c[0] + c[2] * (g.segment === 1 ? 0.75 : 0.25));
        ghostY.set(c[1] + c[3] / 2);
      }
    } else if (running.get() !== RUN_NONE) {
      const tau = now - runAt.get();
      const to = runTo.get();
      let x;
      let done;
      if (running.get() === RUN_LINEAR) {
        x = runFrom.get() + (to - runFrom.get()) * seg(tau, 0, Math.max(runFor.get(), 0.001));
        done = tau >= runFor.get();
      } else {
        x = clamp01(settleAt(runFrom.get(), runSpeed.get(), to, tau));
        done = Math.abs(x - to) < 0.002 && tau > 0.05;
      }
      amount.set(done ? to : x);
      if (done) {
        running.set(RUN_NONE);
        if (mode.get() === MODE_RETURN) {
          autoStart.set(now);
          mode.set(MODE_AUTO);
        }
      }
    } else if (mode.get() === MODE_TOUCH && auto && down.get() === 0 && now - lastTouch.get() >= IDLE) {
      if (amount.get() <= REST) {
        autoStart.set(now);
        mode.set(MODE_AUTO);
      } else {
        dir.set(-1);
        runFrom.set(amount.get());
        runTo.set(0);
        runFor.set(TAP_TURN * amount.get());
        runAt.set(now);
        running.set(RUN_LINEAR);
        mode.set(MODE_RETURN);
      }
    }

    const nextSide = amount.get() > 0.5 ? 1 : 0;
    if (nextSide !== asked.get()) {
      asked.set(nextSide);
      scheduleOnRN(showSide, nextSide);
      if (mode.get() !== MODE_AUTO) scheduleOnRN(tick);
    }
  }, false);

  useEffect(() => {
    ticker.setActive(true);
    return () => ticker.setActive(false);
  }, [ticker]);

  const select = useCallback((theme: LouverTheme) => {
    const to = theme === 'dark' ? 1 : 0;
    if (canTurn) {
      request.set(to);
    } else {
      // no turn: the theme changes at once
      mode.set(MODE_TOUCH);
      amount.set(to);
    }
  }, [canTurn, request, mode, amount]);

  const scrub = Gesture.Pan()
    .enabled(canTurn)
    .maxPointers(1)
    .activeOffsetY([-12, 12])
    .failOffsetX([-18, 18])
    .onStart(() => {
      const from = amount.get();
      mode.set(MODE_TOUCH);
      running.set(RUN_NONE);
      down.set(1);
      grabFrom.set(from);
      lastTouch.set(clock.get());
      ghostA.set(0);
      if (from <= REST || from >= 1 - REST) dir.set(from < 0.5 ? 1 : -1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      amount.set(clamp01(grabFrom.get() + (dir.get() * Math.abs(e.translationY)) / (SCRUB * height)));
    })
    .onEnd((e) => {
      const away = e.translationY >= 0 ? e.velocityY : -e.velocityY;
      const speed = (dir.get() * away) / (SCRUB * height);
      const at = amount.get();
      const to = at + speed * PROJECT > 0.5 ? 1 : 0;
      runFrom.set(at);
      runSpeed.set(Math.max(-5, Math.min(5, speed)));
      runTo.set(to);
      runAt.set(clock.get());
      running.set(RUN_SPRING);
    })
    .onFinalize(() => {
      if (down.get() === 1 && running.get() === RUN_NONE && amount.get() > REST && amount.get() < 1 - REST) {
        // cancelled mid-drag: go back to where it began
        runFrom.set(amount.get());
        runSpeed.set(0);
        runTo.set(grabFrom.get() > 0.5 ? 1 : 0);
        runAt.set(clock.get());
        running.set(RUN_SPRING);
      }
      down.set(0);
      lastTouch.set(clock.get());
    });

  const uniforms = useDerivedValue(() => ({
    res: [width, height],
    turn: [amount.get(), dir.get()],
    px: PIXEL_RATIO,
    cavityTone: CAVITY_TONE,
  }));

  const cover = useAnimatedStyle(() => {
    const a = amount.get();
    const atRest = a <= REST || a >= 1 - REST;
    // the canvas stays until React has put the matching live screen on top
    return { opacity: atRest && committed.get() === (a > 0.5 ? 1 : 0) ? 0 : 1 };
  });
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostA.get(),
    transform: [{ translateX: ghostX.get() - GHOST / 2 }, { translateY: ghostY.get() - GHOST / 2 }, { scale: 1 - 0.16 * ghostP.get() }],
  }));

  const rect = { x: 0, y: 0, width, height };

  return (
    <GestureDetector gesture={scrub}>
      <View ref={rootRef} collapsable={false} style={styles.screen}>
        <StatusBar style={side === 1 ? 'light' : 'dark'} />
        {/* both screens stay live and opaque; the one in front is chosen under the canvas */}
        <View ref={darkRef} collapsable={false} pointerEvents={side === 1 ? 'auto' : 'none'} style={[StyleSheet.absoluteFill, { zIndex: side === 1 ? 2 : 1 }]}>
          <Screen {...screenProps} theme="dark" select={select} controlRef={spareControlRef} resetKey={resets.dark} />
        </View>
        <View ref={lightRef} collapsable={false} pointerEvents={side === 0 ? 'auto' : 'none'} style={[StyleSheet.absoluteFill, { zIndex: side === 0 ? 2 : 1 }]}>
          <Screen {...screenProps} theme="light" select={select} controlRef={controlRef} resetKey={resets.light} />
        </View>
        {images && EFFECT ? (
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.canvas, cover]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms}>
                  <ImageShader image={images.light} fit="fill" rect={rect} />
                  <ImageShader image={images.dark} fit="fill" rect={rect} />
                </Shader>
              </Fill>
            </Canvas>
          </Animated.View>
        ) : null}
        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]}>
          <View style={styles.ghostCore} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden', backgroundColor: '#0E0F15' },
  canvas: { zIndex: 3 },
  // reads on both themes: a pale core inside a dark ring
  ghost: {
    position: 'absolute',
    zIndex: 4,
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(120,118,112,0.2)',
    borderWidth: 1.5,
    borderColor: 'rgba(120,118,112,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 12px rgba(20,18,16,0.22)',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(120,118,112,0.55)' },
});
