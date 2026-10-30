import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './ltk-draw';
import { type Driver, flipCard, type Frame, frameAt, frameOf, hopTrophy, kickWheel, landed, makeDriver, stepDriver, takeOver } from './ltk-motion';
import { C, DY, REF_W, S, UI } from './ltk-theme';
import { type Cue, useLtkAudio } from './use-ltk-audio';

/*
 Lucky Task Hub — the rewards hub of a casual-games app.

 Look (the pin, a screen recording): a yellow Ranking card with a bronze trophy on a podium, a pink
 Lucky Flip card with a hand-sign card, a green Lucky Spin card with a wheel peeking out, the
 newbie tasks' striped progress bar at 3/14, game offers, and a chest with its timer.

 Motion (ours): every reward is alive and pays into one place. The wheel spins 2¼ turns and lands
 with a flash, its $ $ eyes going wide; five bills fly off it along their own arcs into the balance
 in the header, which bumps and rolls up a hundred per bill; a step of the newbie tasks is done
 (the count rolls, the stripes stretch, sparks at the star); the Lucky Flip card turns over from
 peace to fist; the trophy crouches, hops and lands silver in a burst of sparkles while its arrow
 pops. Stripes march, the fire flickers, the mascot blinks and watches the trophy.

 autoPlay: LOOP = 9 s (ltk-motion.ts); frame 9 s equals frame 0.
 Touch: fling the wheel round with a finger (or tap its card): it coasts, settles on a slice and,
 after a real spin, pays out; tap Lucky Flip to turn the card; tap Ranking to hop up a tier;
 tap the chest. 2 s after the last touch everything eases back and the loop resumes.
 Reduced motion: no loop; touches change state at once, without flights or hops.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

function inRect(x: number, y: number, k: { x: number; y: number; w: number; h: number }, pad = 0) {
  'worklet';
  return x > k.x - pad && x < k.x + k.w + pad && y > k.y - pad && y < k.y + k.h + pad;
}

export function LuckyTaskHub({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const atlas = useImage(require('./assets/atlas.webp'));
  const black = useFont(require('./assets/fonts/Nunito-Black.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!atlas || !black || !bold || !semi || !medium) return null;
    const f = { black: black.getTypeface(), bold: bold.getTypeface(), semi: semi.getTypeface(), medium: medium.getTypeface() };
    if (!f.black || !f.bold || !f.semi || !f.medium) return null;
    return makeRes({ black: f.black, bold: f.bold, semi: f.semi, medium: f.medium }, atlas);
  }, [atlas, black, bold, semi, medium]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useLtkAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const billsSeen = useSharedValue(-10);
  const landedSeen = useSharedValue(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__luckyHub.at(2.0)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __luckyHub?: unknown };
    g.__luckyHub = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__luckyHub;
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
    } else stepDriver(d, dt, now, auto);
    // a payout a finger caused: one tick per bill landing, the cash chime with the first
    if (d.mode === 2 && d.billsAt > 0) {
      if (billsSeen.get() !== d.billsAt) {
        billsSeen.set(d.billsAt);
        landedSeen.set(0);
      }
      const n = Math.floor(landed(now - d.billsAt) + 0.5);
      if (n > landedSeen.get()) {
        if (landedSeen.get() === 0) scheduleOnRN(cue, 'cash');
        landedSeen.set(n);
        scheduleOnRN(buzz, n === 5 ? 3 : 2);
      }
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} wheel=${d.wheel.toFixed(0)} v=${d.wheelV.toFixed(0)} bal=${d.balance.toFixed(0)} prog=${d.progTo} tier=${d.tierTo} flip=${d.flipTo} home=${d.homeAt > 0 ? 1 : 0}`);
    }
    frame.set(frameOf(d, now));
  };
  const frameCb = useFrameCallback((info) => {
    frameStep(info.timeSincePreviousFrame ?? 16);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) drawScene(c, res, geo, frame.get());
    else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const zone = useSharedValue(0); // 1 wheel, 2 spin card, 3 flip, 4 ranking, 5 chest
  const start = useSharedValue({ x: 0, y: 0 });
  const lastAng = useSharedValue(0);
  const lastT = useSharedValue(0);

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S, y: (y / geo.s - DY) / S };
  };
  const wheelAngle = (p: { x: number; y: number }) => {
    'worklet';
    const W = UI.spin.wheel;
    return (Math.atan2(p.y - W.y, p.x - W.x) * 180) / Math.PI;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const p = toPin(e.x, e.y);
      takeOver(d, now);
      start.set(p);
      const W = UI.spin.wheel;
      const goes = [UI.ranking.go, UI.flip.go, UI.spin.go];
      d.press = -1;
      for (let i = 0; i < 3; i++) {
        if (inRect(p.x, p.y, goes[i], 8)) {
          d.press = i;
          d.pressLast = i;
          d.pressAt = now;
        }
      }
      if (d.press < 0 && Math.hypot(p.x - W.x, p.y - W.y) < W.r + 26 && inRect(p.x, p.y, UI.spin.card, 10)) {
        zone.set(1);
        if (!reduced && d.billsAt < 0) {
          d.dragging = 1;
          d.spinning = 0;
          d.wheelV = 0;
          lastAng.set(wheelAngle(p));
          lastT.set(now);
        }
      } else if (inRect(p.x, p.y, UI.spin.card)) zone.set(2);
      else if (inRect(p.x, p.y, UI.flip.card)) zone.set(3);
      else if (inRect(p.x, p.y, UI.ranking.card)) zone.set(4);
      else if (inRect(p.x, p.y, UI.inProgress.chest, 10)) zone.set(5);
      else zone.set(0);
    })
    .onUpdate((e) => {
      const d = driver.get();
      if (zone.get() !== 1 || d.dragging === 0) return;
      const now = clock.get();
      d.last = now;
      const a = wheelAngle(toPin(e.x, e.y));
      let da = a - lastAng.get();
      if (da > 180) da -= 360;
      if (da < -180) da += 360;
      const dtm = Math.max(1 / 240, now - lastT.get());
      d.wheel += da;
      d.wheelV = d.wheelV * 0.5 + (da / dtm) * 0.5;
      lastAng.set(a);
      lastT.set(now);
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      if (d.press >= 0) {
        d.press = -1;
        d.releaseAt = now;
      }
      const p = toPin(e.x, e.y);
      const s0 = start.get();
      const tap = Math.hypot(p.x - s0.x, p.y - s0.y) < 16;
      const z = zone.get();
      if (z === 1 && d.dragging === 1) {
        d.dragging = 0;
        if (tap) {
          if (kickWheel(d)) {
            scheduleOnRN(cue, 'spin');
            scheduleOnRN(buzz, 0);
          }
        } else {
          d.spinning = 1;
          d.spun = 0;
          d.wheelV = Math.max(-2400, Math.min(2400, d.wheelV));
          if (Math.abs(d.wheelV) > 300) scheduleOnRN(cue, 'spin');
        }
        return;
      }
      if (!tap) return;
      if (z === 1 || z === 2) {
        if (reduced) {
          // no flight: the payout lands at once
          d.balance += 500;
          d.balFrom = d.balance;
          d.progFrom = d.progTo = Math.min(14, d.progTo + 1);
          d.progAt = now - 1;
          scheduleOnRN(cue, 'cash');
          scheduleOnRN(buzz, 3);
          return;
        }
        if (kickWheel(d)) {
          scheduleOnRN(cue, 'spin');
          scheduleOnRN(buzz, 0);
        }
      } else if (z === 3) {
        if (flipCard(d, now)) {
          if (reduced) d.flipAt = now - 1;
          scheduleOnRN(cue, 'flip');
          scheduleOnRN(buzz, 0);
        }
      } else if (z === 4) {
        if (hopTrophy(d, now)) {
          if (reduced) d.hopAt = now - 2;
          scheduleOnRN(cue, 'flip');
          scheduleOnRN(buzz, 1);
        }
      } else if (z === 5 && !reduced) {
        d.chestAt = now;
        scheduleOnRN(buzz, 0);
      }
    });

  // VoiceOver: a double tap spins the wheel.
  const spinForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      takeOver(d, clock.get());
      kickWheel(d);
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
          accessibilityLabel="Rewards. Ranking: Bronze. Lucky Flip: 26 of 30 left. Lucky Spin. Newbie tasks: 3 of 14."
          accessibilityHint="Spins the lucky wheel."
          onAccessibilityTap={spinForAccessibility}
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
  screen: { flex: 1, backgroundColor: C.page },
});
