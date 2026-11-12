import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawCrossfade, drawScene, type Geo, type Look, makeRes, type Res } from './gpm-draw';
import {
  type Driver,
  isRest,
  makeDriver,
  modsOf,
  scrubEnd,
  scrubTo,
  stepDriver,
  takeOver,
  timeOf,
  toggle,
  TOUCH,
} from './gpm-driver';
import { AUTO_MODS, cardInverse, LOOP, progressOf, sceneAt, T } from './gpm-motion';
import { GPM_SKSL } from './gpm-shader';
import { C, REF_H, REF_W } from './gpm-theme';

/*
 Glass Pay Morph — the pay screen of an invented wallet, Solune: a frosted glass card over a lit
 navy page, an amber and a red light behind it. Paying condenses the card into the wallet's mark.

 Look (the pin, a Dribbble shot by Alex Gorbunov): the card of glass at −3°, a large amber disc
 upper-left and a smaller red one lower-right, soft and glowing where the glass covers them and
 crisp beside it; thin white numerals, chip, contactless and ring outlines. Substitution: the mark
 is ours (a large amber disc with a small red one on its upper-left), and so is the copy.

 Motion (the pin's): the lights set off first and travel half-way round the card while it shrinks
 to a glass pill, then dock on it as the mark; the pill opens again, the lights whip out as
 crescents and the glass turns half a revolution to bring the printed face back. Ours: the rim lens
 and Fresnel edge, liquid lights that fuse in the dock, the pay pulse, the turning slab's edge and
 sheen, the captions, the finger.

 autoPlay: LOOP = 4.4 s (gpm-motion.ts); frame 4.4 s equals frame 0.
 Touch: tap the card (or the pill) to pay (or open it again); drag up or down on it to scrub the
 morph; drag sideways on the card to tilt it; pick up a light and throw it. 2 s after the last
 touch it settles and the loop restarts.
 Reduced motion: the rest frame; a tap cross-fades card ↔ pill in 220 ms.
*/

type Props = {
  autoPlay?: boolean;
  /** Freezes the loop at this time (s), for stills. */
  previewTime?: number;
};

const effect = Skia.RuntimeEffect.Make(GPM_SKSL);
const LOOK: Look = { lens: 1, pools: 0.35, core: 1 };

export function GlassPayMorph({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const light = useFont(require('./assets/fonts/Outfit-Light.ttf'), 100);
  const regular = useFont(require('./assets/fonts/Outfit-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Outfit-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Outfit-SemiBold.ttf'), 100);
  const res = useMemo<Res | null>(() => {
    if (!light || !regular || !medium || !semi || !effect) return null;
    const f = { light: light.getTypeface(), regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface() };
    if (!f.light || !f.regular || !f.medium || !f.semi) return null;
    return makeRes(effect, { light: f.light, regular: f.regular, medium: f.medium, semi: f.semi });
  }, [light, regular, medium, semi]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    const dy = (h - REF_H * s) / 2;
    return { w, h, s, dy, top: (insets.top - dy) / s, px: 1 / (PixelRatio.get() * s) };
  }, [w, h, insets.top]);

  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 3) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const tick = useSharedValue(0);
  const paid = useSharedValue(0); // reduced motion: 0 card … 1 pill
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(0));
  }, [auto, reduced, driver]);

  // Debug (development builds only): `globalThis.__gpm.pin(1.5)` freezes the loop at t = 1.5 s,
  // `.play()` resumes.
  const debugT = useSharedValue(previewTime ?? -1);
  useEffect(() => {
    debugT.set(previewTime ?? -1);
  }, [previewTime, debugT]);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __gpm?: unknown };
    g.__gpm = { pin: (t: number) => debugT.set(t), play: () => debugT.set(-1), loop: LOOP, info: () => ({ effect: effect !== null, res: res !== null, size }) };
    return () => {
      delete g.__gpm;
    };
  }, [debugT, res, size]);

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    if (debugT.get() >= 0) {
      tick.set(tick.get() + 1);
      return;
    }
    const d = driver.get();
    const ev = stepDriver(d, dt, now, auto);
    if (ev !== 0) scheduleOnRN(buzz, ev);
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (!res) {
      c.drawColor(Skia.Color(C.page));
    } else if (reduced) {
      drawCrossfade(c, res, geo, sceneAt(0, AUTO_MODS), sceneAt(T.paidHold, AUTO_MODS), LOOK, paid.get());
    } else if (debugT.get() >= 0) {
      drawScene(c, res, geo, sceneAt(debugT.get(), AUTO_MODS), LOOK);
    } else {
      const d = driver.get();
      drawScene(c, res, geo, sceneAt(timeOf(d), modsOf(d)), LOOK);
    }
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // gesture bookkeeping (UI thread)
  const g0 = useSharedValue({ x: 0, y: 0, p0: 0, onCard: 0, lit: 0, p: 0, moved: 0, tx0: 0, ty0: 0 });
  const hitCard = (x: number, y: number, d: Driver) => {
    'worklet';
    const s = sceneAt(timeOf(d), modsOf(d));
    const m = cardInverse(s);
    const z = m[6] * x + m[7] * y + m[8];
    const lx = (m[0] * x + m[1] * y + m[2]) / z;
    const ly = (m[3] * x + m[4] * y + m[5]) / z;
    return Math.abs(lx) < s.w / 2 + 10 && Math.abs(ly) < s.h / 2 + 10;
  };
  const hitLight = (x: number, y: number, d: Driver) => {
    'worklet';
    const s = sceneAt(timeOf(d), modsOf(d));
    if (Math.hypot(x - s.a[0], y - s.a[1]) < s.a[2]) return 3;
    if (Math.hypot(x - s.rd[0], y - s.rd[1]) < s.rd[2]) return 4;
    return 0;
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      if (reduced) {
        g0.set({ x, y, p0: 0, onCard: 1, lit: 0, p: 0, moved: 0, tx0: 0, ty0: 0 });
        return;
      }
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const onCard = hitCard(x, y, d) ? 1 : 0;
      const lit = onCard ? 0 : hitLight(x, y, d);
      g0.set({ x, y, p0: progressOf(d.tau), onCard, lit, p: progressOf(d.tau), moved: 0, tx0: 0, ty0: 0 });
      d.held = lit !== 0 ? lit : 5;
      if (onCard) {
        d.pressTarget = TOUCH.pressIn;
        scheduleOnRN(buzz, 0);
      }
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      d.last = clock.get();
      const g = g0.get();
      const tx = e.translationX / geo.s;
      const ty = e.translationY / geo.s;
      if (d.held === 3 || d.held === 4) {
        const o = d.held === 3 ? d.aOff : d.rOff;
        o[0] = tx;
        o[1] = ty;
        o[2] = e.velocityX / geo.s;
        o[3] = e.velocityY / geo.s;
        return;
      }
      if (d.held === 5 && Math.hypot(tx, ty) > TOUCH.axisLock) {
        const t = d.tau;
        const midExpand = t > T.expand0 && t < T.expand1;
        if (Math.abs(ty) >= Math.abs(tx) && !midExpand) {
          d.held = 1;
          // grab the timeline where it stands, as a progress
          const p0 = progressOf(t);
          g0.set({ ...g, p0, p: p0, moved: 1, ty0: ty });
          d.pressTarget = 1;
        } else if (g.onCard && isRest(t) && Math.abs(tx) > Math.abs(ty)) {
          d.held = 2;
          g0.set({ ...g, moved: 1, tx0: tx });
          d.pressTarget = 1;
        } else {
          d.held = 6; // a drag that drives nothing
          g0.set({ ...g, moved: 1 });
          d.pressTarget = 1;
        }
        return;
      }
      if (d.held === 1) {
        const gg = g0.get();
        const p = scrubTo(d, gg.p0, ty - gg.ty0);
        g0.set({ ...gg, p });
      } else if (d.held === 2) {
        const over = ((tx - g.tx0) * TOUCH.yawPerPt) / TOUCH.yawMax;
        const sgn = over < 0 ? -1 : 1;
        const a = Math.abs(over);
        const y = a <= 1 ? a : 1 + (a - 1) / (1 + (a - 1) * 3);
        d.yaw = sgn * y * TOUCH.yawMax;
        d.yawV = 0;
      }
    })
    .onFinalize((e) => {
      const g = g0.get();
      if (reduced) {
        paid.set(withTiming(paid.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      const d = driver.get();
      d.last = clock.get();
      d.pressTarget = 1;
      const held = d.held;
      d.held = 0;
      if (held === 5) {
        if (g.onCard) toggle(d);
      } else if (held === 1) {
        scrubEnd(d, g.p, e.velocityY / geo.s);
      } else if (held === 2) {
        d.yawV = (e.velocityX / geo.s) * TOUCH.yawPerPt;
      }
    });

  const payForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        paid.set(withTiming(paid.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      const d = driver.get();
      takeOver(d, clock.get());
      toggle(d);
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Solune card ending 4417. Hold near reader, or tap the card to pay."
          accessibilityHint="Pays with the card, or shows the card again after paying."
          onAccessibilityTap={payForAccessibility}
        >
          {ready ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page },
});
