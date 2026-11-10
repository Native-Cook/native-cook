import { Canvas, Circle, Group, LinearGradient, Path, Rect, Skia, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { BppGpu } from './gpu/BppGpu';
import { frameAt, GAP, LIFT, LOOP, OPEN, PERIOD } from './bpp-motion';
import { COLOR, COPY, D, FONT, makeLayout } from './bpp-theme';
import { makeFrame, makeGeo } from './bpp-uniforms';
import { useBppAudio } from './use-bpp-audio';

/*
 Bell Permission Primer — the notifications primer of an invented plant-care app, Mosswell, laid out
 from the reference pin (95771929563137692): the pin's faint shapes round the top, the hero in a blue
 disc with its horizon, "Notifications", three lines of copy, the round ALLOW button with its glow and
 SKIP.

 Where the pin has a paper scroll, the hero is a bell on WebGPU (TypeGPU-resolved WGSL, sphere-traced):
 porcelain with a gold lip and crown, hollow, with a pink clapper on its rod. It is a real pendulum:
 push it and it swings from its crown with weight; the clapper lags on a longer arm and strikes the
 rim, and every strike sends a ring over the page that nudges the floating shapes (a chime and a tap
 of haptics). ALLOW and the bell flies to the top of the screen, its field blending into a glass slab
 that becomes the notification it grants, shedding sparks on the way (a compute pass). Tap the
 notification and it melts back into the bell.

 autoPlay: LOOP = 10 s (bpp-motion.ts); frame 10 s equals frame 0. A touch takes over; 2 s after the
 last one the notification goes back into the bell and the loop restarts. Reduced motion: no loop
 and no swing; the notification fades in and out.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const W_BELL = (2 * Math.PI) / PERIOD;

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'light') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function BellPermissionPrimer({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.title]: require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'),
    [FONT.regular]: require('./assets/fonts/Barlow-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Barlow-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Barlow-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Barlow-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const play = useBppAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const g = useMemo(() => makeGeo(L, insets.top), [L, insets.top]);
  const discX = D.disc.x * sx;
  const discY = L.Y(D.disc.y);
  const discR = D.disc.r * sx;
  const allowY = L.Y(D.allow.y);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const swing = useSharedValue(0);
  const swingV = useSharedValue(0);
  const clap = useSharedValue(0);
  const clapV = useSharedValue(0);
  // drawn up, swallowed by the island, the island opened into the notification
  const liftX = useSharedValue(0);
  const liftY = useSharedValue(0);
  const suck = useSharedValue(0);
  const expand = useSharedValue(0);
  const lifting = useSharedValue(0);
  const ring0 = useSharedValue(0);
  const ring1 = useSharedValue(0);
  const ring2 = useSharedValue(0);
  const ringNext = useSharedValue(0);
  const bob = useSharedValue(0);
  const dragging = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [allowed, setAllowed] = useState(false);

  /* ------------------------------------------------------------ the bell */
  const strike = () => {
    'worklet';
    const k = ringNext.get();
    const r = k === 0 ? ring0 : k === 1 ? ring1 : ring2;
    r.set(0.001);
    r.set(
      withTiming(1, { duration: 1100, easing: Easing.linear, reduceMotion: NEVER }, (fin) => {
        if (fin) r.set(0);
      }),
    );
    ringNext.set((k + 1) % 3);
    scheduleOnRN(buzz, 'light');
    scheduleOnRN(play, 'ding');
  };
  // the island takes the bell, then opens into the notification
  const open = (touch: boolean) => {
    'worklet';
    if (suck.get() > 0.5 && expand.get() > 0.5) return;
    if (touch) {
      scheduleOnRN(play, 'allow');
      scheduleOnRN(buzz, 'success');
    }
    if (reduced) {
      suck.set(withTiming(1, { duration: 1, reduceMotion: NEVER }));
      expand.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      return;
    }
    const fly = Math.max(0, 1 - Math.abs(liftY.get()) / LIFT);
    // drawn up first if it is not already near
    liftY.set(withTiming(-LIFT, { duration: 400 * fly, easing: EASE_IN_OUT }));
    liftX.set(withTiming(0, { duration: 400 * fly, easing: EASE_IN_OUT }));
    suck.set(
      withDelay(
        400 * fly,
        withTiming(1, { duration: 300, easing: Easing.in(Easing.quad) }, (fin) => {
          if (!fin) return;
          // the island opens on a spring: it overshoots a little and settles, like the Dynamic Island
          if (touch) scheduleOnRN(play, 'banner');
          expand.set(withSpring(1, { mass: 1, stiffness: OPEN.omega * OPEN.omega, damping: 2 * OPEN.zeta * OPEN.omega }));
        }),
      ),
    );
  };
  // tapped, the notification closes and the island drops the bell back to its hook
  const close = (touch: boolean) => {
    'worklet';
    if (reduced) {
      expand.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
      suck.set(withDelay(200, withTiming(0, { duration: 1, reduceMotion: NEVER })));
      liftY.set(0);
      liftX.set(0);
      return;
    }
    liftY.set(0);
    liftX.set(0);
    expand.set(
      withTiming(0, { duration: 400, easing: EASE_IN_OUT }, (fin) => {
        if (!fin) return;
        suck.set(
          withTiming(0, { duration: 600, easing: Easing.bezier(0.3, 1.45, 0.5, 1) }, (f2) => {
            if (f2) {
              swingV.set(swingV.get() + 1.2);
              clapV.set(clapV.get() + 2.0);
            }
          }),
        );
      }),
    );
    if (touch) scheduleOnRN(buzz, 'light');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      bob.set(withTiming(0, { duration: 500, easing: EASE_OUT }));
      swingV.set(0);
      clapV.set(0);
      // finish what the loop was doing
      if (suck.get() > 0.001 && expand.get() < 0.999) {
        if (suck.get() > 0.5) open(false);
        else close(false);
      } else if (expand.get() > 0.001 && expand.get() < 0.999) open(false);
    }
  };

  /* ------------------------------------------------------------ clock and physics */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const lt = clock.get() % LOOP;
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      swing.set(s.swing);
      clap.set(s.clap);
      liftX.set(0);
      liftY.set(-LIFT * s.lift);
      suck.set(s.suck);
      expand.set(s.expand);
      ring0.set(s.rings[0]);
      ring1.set(s.rings[1]);
      ring2.set(s.rings[2]);
      bob.set(s.bob);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerAt === 0) {
        fingerX.set(g.px + s.fingerX * g.unit * 0.6);
        fingerY.set(g.py + g.unit * 0.95 - LIFT * s.lift);
      } else if (s.fingerAt === 1) {
        fingerX.set(L.w / 2);
        fingerY.set(allowY + D.allow.h / 2);
      } else {
        fingerX.set(L.w * 0.6);
        fingerY.set(g.slab[1] + g.slab[3] * 0.55);
      }
      frame.set(makeFrame(g, L.w, L.h, pixelRatio, { swing: s.swing, clap: s.clap, suck: s.suck, expand: s.expand, liftX: 0, liftY: -LIFT * s.lift, bob: s.bob, rings: s.rings, t }));
      return;
    }
    // a damped pendulum for the bell (unless the finger holds it), and a looser one for the clapper,
    // which hits the rim when it leans GAP away from the bell
    const steps = 4;
    const h2 = dt / steps;
    for (let i = 0; i < steps; i++) {
      if (dragging.get() === 0 && lifting.get() === 0 && !reduced) {
        const a = -W_BELL * W_BELL * swing.get() - 2 * 0.12 * W_BELL * swingV.get();
        swingV.set(swingV.get() + a * h2);
        swing.set(swing.get() + swingV.get() * h2);
      }
      const ac = -W_BELL * W_BELL * 0.8 * clap.get() - 2 * 0.1 * W_BELL * clapV.get();
      clapV.set(clapV.get() + ac * h2);
      clap.set(clap.get() + clapV.get() * h2);
      const rel = clap.get() - swing.get();
      if (Math.abs(rel) > GAP) {
        const side = rel > 0 ? 1 : -1;
        clap.set(swing.get() + side * GAP);
        const relV = clapV.get() - swingV.get();
        if (relV * side > 0) {
          if (relV * side > 0.6) strike();
          clapV.set(swingV.get() - relV * 0.35);
        }
      }
    }
    frame.set(makeFrame(g, L.w, L.h, pixelRatio, { swing: swing.get(), clap: clap.get(), suck: suck.get(), expand: expand.get(), liftX: liftX.get(), liftY: liftY.get(), bob: bob.get(), rings: [ring0.get(), ring1.get(), ring2.get()], t: lt }));
    if (!auto || dragging.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (suck.get() > 0.001 || expand.get() > 0.001) close(false);
      return;
    }
    const still = suck.get() < 0.001 && expand.get() < 0.001 && Math.abs(liftY.get()) < 0.5 && Math.abs(swing.get()) < 0.004 && Math.abs(swingV.get()) < 0.02 && Math.abs(clap.get()) < 0.01 && Math.abs(clapV.get()) < 0.05;
    if (still) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      const cfg = { duration: 200, reduceMotion: NEVER };
      const isOpen = expand.get() > 0.5;
      expand.set(withTiming(isOpen ? 1 : 0, cfg));
      suck.set(withTiming(isOpen ? 1 : 0, cfg));
      liftY.set(withTiming(0, cfg));
      liftX.set(withTiming(0, cfg));
      swing.set(withTiming(0, cfg));
      clap.set(withTiming(0, cfg));
      swingV.set(0);
      clapV.set(0);
      bob.set(0);
      finger.set(0);
      fingerPress.set(0);
      ring0.set(0);
      ring1.set(0);
      ring2.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__bellPrimer = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), swing: +swing.get().toFixed(3), clap: +clap.get().toFixed(3), suck: +suck.get().toFixed(3), expand: +expand.get().toFixed(3), lift: +liftY.get().toFixed(1) }),
    };
  }, [pin, rate, autoStart, clock, mode, swing, clap, suck, expand, liftY]);

  useAnimatedReaction(
    () => expand.get() > 0.5,
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setAllowed, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // sideways it swings the bell; upwards it draws the bell towards the island, stretching, and let go
  // near enough the island takes it
  const push = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (reduced || suck.get() > 0.01 || expand.get() > 0.01) return;
      dragging.set(1);
      dragFrom.set(swing.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      if (lifting.get() === 0 && e.translationY < -24 && Math.abs(e.translationY) > Math.abs(e.translationX)) lifting.set(1);
      if (lifting.get() === 1) {
        // the higher, the harder: a rubber band past the island's reach
        const up = -e.translationY;
        const y = up < LIFT ? up : LIFT + (up - LIFT) * 0.35;
        liftY.set(-y);
        liftX.set(e.translationX * 0.35);
        swing.set(Math.max(-0.5, Math.min(0.5, e.velocityX / 4000)));
        return;
      }
      // the bell follows the finger about its crown, harder to lift the further it goes
      const raw = dragFrom.get() + e.translationX / (g.unit * 1.6);
      const sgn = raw < 0 ? -1 : 1;
      const prev = swing.get();
      swing.set(sgn * (Math.abs(raw) / (1 + Math.abs(raw) / 0.8)));
      swingV.set((swing.get() - prev) * 60);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      dragging.set(0);
      if (lifting.get() === 1) {
        lifting.set(0);
        if (-liftY.get() > LIFT * 0.75 || e.velocityY < -900) {
          open(true);
        } else {
          // it falls back on its spring, carrying the finger's speed as it let go
          const band = -liftY.get() < LIFT ? 1 : 0.35;
          liftY.set(withSpring(0, { duration: 650, dampingRatio: 0.45, velocity: e.velocityY * band }));
          liftX.set(withSpring(0, { duration: 650, dampingRatio: 0.6, velocity: e.velocityX * 0.35 }));
          swingV.set(swingV.get() + e.velocityX / 1500);
          clapV.set(clapV.get() + 1.6);
        }
        return;
      }
      swingV.set(Math.max(-6, Math.min(6, e.velocityX / (g.unit * 1.6))));
    });
  const tap = Gesture.Tap().onEnd(() => {
    takeOver();
    if (reduced || suck.get() > 0.01) return;
    swingV.set(swingV.get() + 2.6);
  });
  const bell = Gesture.Exclusive(push, tap);

  const allow = () =>
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      open(true);
    });
  const dismiss = () =>
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      close(true);
    });

  /* ------------------------------------------------------------ drawing */
  // the disc: a sky over a curved horizon of deeper blue
  const ground = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const hy = L.Y(D.disc.horizon);
    b.moveTo(discX - discR * 1.2, hy + 16 * sx);
    b.quadTo(discX, hy - 10 * sx, discX + discR * 1.2, hy + 16 * sx);
    b.lineTo(discX + discR * 1.2, discY + discR * 1.2);
    b.lineTo(discX - discR * 1.2, discY + discR * 1.2);
    b.close();
    return b.build();
  }, [L, discX, discY, discR, sx]);
  const clip = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    b.addCircle(discX, discY, discR);
    return b.build();
  }, [discX, discY, discR]);
  // the flat bell, while WebGPU is not up
  const flat = useMemo(() => {
    const u = g.unit;
    const b = Skia.PathBuilder.Make();
    b.moveTo(-0.08 * u, 0.22 * u);
    b.cubicTo(-0.42 * u, 0.25 * u, -0.36 * u, 0.85 * u, -0.48 * u, 1.05 * u);
    b.cubicTo(-0.55 * u, 1.17 * u, -0.62 * u, 1.22 * u, -0.62 * u, 1.27 * u);
    b.lineTo(0.62 * u, 1.27 * u);
    b.cubicTo(0.62 * u, 1.22 * u, 0.55 * u, 1.17 * u, 0.48 * u, 1.05 * u);
    b.cubicTo(0.36 * u, 0.85 * u, 0.42 * u, 0.25 * u, 0.08 * u, 0.22 * u);
    b.close();
    return b.build();
  }, [g]);
  const flatTransform = useDerivedValue(() => [{ translateX: g.px + liftX.get() }, { translateY: g.py + liftY.get() }, { rotate: swing.get() }, { scale: 1 - suck.get() }]);
  const flatOpacity = useDerivedValue(() => 1 - suck.get());
  const banner = useAnimatedStyle(() => {
    const m = expand.get();
    return { opacity: Math.max(0, Math.min(1, (m - 0.8) / 0.2)), transform: [{ scale: 0.96 + 0.04 * m }] };
  });
  const allowGlow = useAnimatedStyle(() => ({ opacity: 1 - 0.7 * expand.get() }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(20,40,90,${0.08 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(20,40,90,${0.35 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" hidden={allowed} animated />

      {/* the hero's disc */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={L.w} height={L.Y(D.top.h) + 30}>
          <LinearGradient start={vec(0, L.Y(D.top.h) - 30)} end={vec(0, L.Y(D.top.h) + 30)} colors={[COLOR.top, COLOR.page]} />
        </Rect>
        <Group clip={clip}>
          <Circle cx={discX} cy={discY} r={discR}>
            <LinearGradient start={vec(discX - discR * 0.5, discY - discR)} end={vec(discX + discR * 0.3, discY + discR * 0.2)} colors={[COLOR.skyA, COLOR.skyB]} />
          </Circle>
          <Path path={ground}>
            <LinearGradient start={vec(discX, L.Y(D.disc.horizon))} end={vec(discX, discY + discR)} colors={[COLOR.groundA, COLOR.groundB]} />
          </Path>
        </Group>
        {!gpuOk && (
          <Group transform={flatTransform} opacity={flatOpacity}>
            <Path path={flat} color="#FFFFFF" />
            <Circle cx={0} cy={1.27 * g.unit} r={0.11 * g.unit} color="#F07BA1" />
          </Group>
        )}
      </Canvas>

      {fontsLoaded && (
        <>
          <Text style={[styles.title, { top: L.Y(D.title.y) - 30 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.title}
          </Text>
          <Text style={[styles.body, { top: L.Y(D.body.y) - 20, left: (L.w - D.body.w * sx) / 2, width: D.body.w * sx }]} allowFontScaling={false}>
            {COPY.body}
          </Text>
        </>
      )}
      <Animated.View style={[styles.allowGlow, { left: D.allow.x * sx + 14, top: allowY + 8, width: D.allow.w * sx - 28, height: D.allow.h - 16 }, allowGlow]} pointerEvents="none" />
      <PressScale style={[styles.allow, { left: D.allow.x * sx, top: allowY, width: D.allow.w * sx, height: D.allow.h, borderRadius: D.allow.h / 2 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Allow notifications" onPress={allow}>
        {fontsLoaded && (
          <Text style={styles.allowText} allowFontScaling={false}>
            {COPY.allow}
          </Text>
        )}
      </PressScale>
      <PressScale style={[styles.skip, { top: L.Y(D.skip.y) - 18, left: L.w / 2 - 60 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Skip" hitSlop={8}>
        {fontsLoaded && (
          <Text style={styles.skipText} allowFontScaling={false}>
            {COPY.skip}
          </Text>
        )}
      </PressScale>

      {size && <BppGpu frame={frame} width={L.w} height={L.h} disabled={reduced} onAvailable={setGpuOk} />}

      {/* the bell takes the finger */}
      <GestureDetector gesture={bell}>
        <View
          style={[styles.abs, { left: g.px - g.unit * 0.9, top: g.py - 10, width: g.unit * 1.8, height: g.unit * 1.45 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Bell"
          accessibilityHint="Swipe to ring it"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              if (!reduced && suck.get() < 0.01) swingV.set(swingV.get() + 2.6);
            })
          }
        />
      </GestureDetector>

      {/* the notification the bell becomes */}
      <Animated.View style={[styles.banner, { left: g.slab[0], top: g.slab[1], width: g.slab[2], height: g.slab[3], borderRadius: D.banner.r }, banner]} pointerEvents={allowed ? 'auto' : 'none'}>
        <Pressable style={styles.bannerRow} onPress={dismiss} accessibilityRole="button" accessibilityLabel={`${COPY.app}: ${COPY.banner}. Tap to put the bell back`}>
          <View style={styles.appIcon}>
            <Svg width={22} height={22} viewBox="0 0 22 22">
              <SvgRect x={0} y={0} width={22} height={22} rx={6} fill="#3FA56B" />
              <SvgPath d="M11 17.5 C11 12 7.5 9.5 4.5 9.5 C4.5 13.5 7 16 11 16 M11 17.5 C11 11.5 14 8 17.5 7.5 C17.5 12 15 15.5 11 15.5" stroke="#FFFFFF" strokeWidth={1.5} fill="none" strokeLinecap="round" />
            </Svg>
          </View>
          {fontsLoaded && (
            <View style={styles.bannerText}>
              <View style={styles.bannerHead}>
                <Text style={styles.bannerApp} allowFontScaling={false}>
                  {COPY.app}
                </Text>
                <Text style={styles.bannerTime} allowFontScaling={false}>
                  now
                </Text>
              </View>
              <Text style={styles.bannerTitle} allowFontScaling={false} numberOfLines={1}>
                {COPY.banner}
              </Text>
              <Text style={styles.bannerSub} allowFontScaling={false} numberOfLines={1}>
                {COPY.bannerSub}
              </Text>
            </View>
          )}
        </Pressable>
      </Animated.View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.title, fontSize: D.title.size, lineHeight: 52, color: COLOR.ink },
  body: { position: 'absolute', textAlign: 'center', fontFamily: FONT.regular, fontSize: D.body.size, lineHeight: D.body.line, color: COLOR.body },
  // the glow is the shadow of a blue pill hidden under the button: a near-transparent pill left a hole
  // in its own shadow (CSS box shadows are not drawn under the box), which read as a pale ghost button
  allowGlow: { position: 'absolute', borderRadius: 14, backgroundColor: COLOR.blue, boxShadow: '0 14px 22px rgba(72,155,255,0.45)' },
  allow: { position: 'absolute', backgroundColor: COLOR.blue, alignItems: 'center', justifyContent: 'center' },
  allowText: { fontFamily: FONT.semibold, fontSize: D.allow.size, letterSpacing: 0.6, color: '#FFFFFF' },
  skip: { position: 'absolute', width: 120, height: 36, alignItems: 'center', justifyContent: 'center' },
  skipText: { fontFamily: FONT.bold, fontSize: D.skip.size, letterSpacing: 0.4, color: COLOR.skip },
  banner: { position: 'absolute' },
  bannerRow: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, gap: 12 },
  appIcon: { width: 44, height: 44, borderRadius: 12, backgroundColor: '#1C2A22', alignItems: 'center', justifyContent: 'center' },
  bannerText: { flex: 1 },
  bannerHead: { flexDirection: 'row', justifyContent: 'space-between' },
  bannerApp: { fontFamily: FONT.semibold, fontSize: 12, letterSpacing: 0.8, color: '#8E949C' },
  bannerTime: { fontFamily: FONT.regular, fontSize: 12, color: '#8E949C' },
  bannerTitle: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 21, color: '#FFFFFF' },
  bannerSub: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 18, color: '#C9CED4' },
});
