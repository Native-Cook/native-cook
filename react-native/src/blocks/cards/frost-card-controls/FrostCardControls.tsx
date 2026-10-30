import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './fcc-draw';
import { type Driver, frameAt, freezeAt, liveFrame, makeDriver, meltNow, reissueNow, shredNow, stepDriver, takeOver } from './fcc-motion';
import { FROST_SKSL } from './fcc-shaders';
import { C, REF_H, REF_W, UI } from './fcc-theme';
import { type Cue, useFccAudio } from './use-fcc-audio';

/*
 Frost Card Controls — the card screen of an invented bank, its metal card over Freeze, Reissue
 and Discard.

 Look (the pin, a still): the balance and spending limit, a brushed-silver card with its brand
 in big orange letters across the top, "Physical card 5371" and a network mark, a black wallet
 button, three white tiles and a Transactions card on a pale grey page.

 Motion (ours): Freeze — ice grows over the card from a corner as a ragged front of crystal plates
 and feathers with bright tips, the card frosting under it (its number blurs), a cold tint and a
 Frozen chip; the snowflake spins and the tile turns into Unfreeze. Unfreeze — the ice clears in
 patches with wet edges and water runs down. Discard — the card is cut into strips that part and
 fall away turning; a new card drops in from above and a shine runs over it.

 autoPlay: LOOP = 9 s (fcc-motion.ts); frame 9 s equals frame 0.
 Touch: tap Freeze/Unfreeze; tap the card to freeze it from where you touch (tap again to thaw);
 drag the card to tilt it (its sheen follows); Discard shreds it and a new one arrives; Reissue
 sends it off to the right and a new one drops in. 2 s after the last touch the loop resumes.
 Reduced motion: no ice growth, no shredding; Freeze cross-fades the iced card in 220 ms.
*/

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2 | 3 | 4; // none, card, freeze, reissue, discard

const frostEffect = Skia.RuntimeEffect.Make(FROST_SKSL);
let debugState = '';

export function FrostCardControls({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);
  const brand = useFont(require('./assets/fonts/Poppins-SemiBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold || !brand || !frostEffect) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), brand: brand.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold || !f.brand) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold, brand: f.brand }, frostEffect);
  }, [regular, medium, semi, bold, brand]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W, dy: 0 }), [w, h]);

  const play = useFccAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue(frameAt(0));
  const live = useSharedValue<Live>({ amb: 0, tiltX: 0, tiltY: 0 });
  const iced = useSharedValue(0); // reduced motion: 0 plain … 1 iced
  const target = useSharedValue<Target>(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__frostCard.at(1.8)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __frostCard?: unknown };
    g.__frostCard = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__frostCard;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameStep = (since: number) => {
    'worklet';
    const dt = Math.min(since / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
      stepDriver(d, 0, now, false);
    } else stepDriver(d, dt, now, auto);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} frozen=${d.frozen} tilt=${d.tiltX.toFixed(2)} tg=${target.get()}`);
    }
    frame.set(liveFrame(d, now));
    live.set({ amb: d.amb, tiltX: d.tiltX, tiltY: d.tiltY });
  };
  const frameCb = useFrameCallback((info) => {
    frameStep(info.timeSincePreviousFrame ?? 16);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      const k = iced.get();
      const still = { amb: 0, tiltX: 0, tiltY: 0 };
      drawScene(c, res, geo, frameAt(0), still);
      if (k > 0.001) {
        const p = Skia.Paint();
        p.setAlphaf(k);
        c.saveLayer(p);
        drawScene(c, res, geo, frameAt(3.0), still);
        c.restore();
      }
    } else if (res) drawScene(c, res, geo, frame.get(), live.get());
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const start = useSharedValue({ x: 0, y: 0 });
  const moved = useSharedValue(0);
  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const x = e.x / geo.s;
      const y = e.y / geo.s;
      start.set({ x, y });
      moved.set(0);
      const cd = UI.card;
      const t = UI.tiles;
      let tg: Target = 0;
      if (x > cd.x && x < cd.x + cd.w && y > cd.y && y < cd.y + cd.h) tg = 1;
      else if (y > t.y && y < t.y + t.h) {
        for (let i = 0; i < 3; i++) if (x > t.x[i] && x < t.x[i] + t.w) tg = (i + 2) as Target;
      }
      target.set(tg);
      if (reduced) return;
      const d = driver.get();
      takeOver(d, clock.get());
      if (tg >= 2) d.pressAim[tg - 2] = 1;
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      d.last = clock.get();
      if (Math.hypot(e.translationX, e.translationY) > 8) moved.set(1);
      if (target.get() === 1 && moved.get() === 1) {
        d.aimX = Math.max(-1, Math.min(1, e.translationX / 120));
        d.aimY = Math.max(-1, Math.min(1, e.translationY / 120));
      }
    })
    .onFinalize(() => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const tap = moved.get() === 0;
      target.set(0);
      d.pressAim = [0, 0, 0];
      d.aimX = 0;
      d.aimY = 0;
      d.last = now;
      if (reduced) {
        if (tap && (tg === 1 || tg === 2)) iced.set(withTiming(iced.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      if (!tap) return;
      const busy = now < d.busy;
      if ((tg === 1 || tg === 2) && !busy) {
        if (d.frozen) {
          meltNow(d, now);
          scheduleOnRN(cue, 'melt');
          scheduleOnRN(buzz, 0);
        } else {
          const s = start.get();
          const cx = tg === 1 ? s.x - UI.card.x : 46;
          const cy = tg === 1 ? s.y - UI.card.y : 186;
          freezeAt(d, now, cx, cy);
          scheduleOnRN(cue, 'freeze');
          scheduleOnRN(buzz, 1);
        }
      } else if (tg === 3 && !busy) {
        reissueNow(d, now);
        scheduleOnRN(cue, 'reissue');
        scheduleOnRN(buzz, 0);
      } else if (tg === 4 && !busy) {
        shredNow(d, now);
        scheduleOnRN(cue, 'shred');
        scheduleOnRN(buzz, 3);
      }
    });

  const freezeForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        iced.set(withTiming(iced.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      if (d.frozen) meltNow(d, now);
      else freezeAt(d, now, 46, 186);
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Physical card ending 5371. Balance minus 3,116 dollars 58. No spending limit."
          accessibilityHint="Freezes the card, or unfreezes it when it is frozen."
          onAccessibilityTap={freezeForAccessibility}
        >
          {size ? (
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
  screen: { flex: 1, backgroundColor: C.bg },
});

