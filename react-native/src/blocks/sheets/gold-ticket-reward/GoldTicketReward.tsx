import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  SensorType,
  useAnimatedSensor,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { blurPage, drawPage, drawScene, type Geo, makeDust, makeLabels, makeStatics, type Res } from './gtr-draw';
import {
  bAmountAt,
  closeB,
  coinExtra,
  COMMIT,
  ctaTwinkle,
  type Driver,
  flickCoin,
  frameAt,
  inRestA,
  inRewardB,
  LOOP,
  makeDriver,
  PEAK,
  pressA,
  releaseA,
  scrubEnd,
  scrubTo,
  stepDriver,
  T,
  touched,
} from './gtr-motion';
import { DISSOLVE_SKSL, FOIL_SKSL } from './gtr-shaders';
import { A, B, C, REF_H, REF_W } from './gtr-theme';
import { type Cue, useGtrAudio } from './use-gtr-audio';

/*
 Gold Ticket Reward — the "thanks for your interest" sheet of an invented car-loan app, after a
 car inspection has been booked, turning into the gold reward it promotes.

 Look (the pin, both phones): a blurred, dimmed "Inspection details" page; over it a cream sheet
 with four SUVs on a golden stage, "THANKS FOR YOUR INTEREST!", a white ticket with a gold coin in
 a cradle ("Book Test Drive to Win · 1 Gm Gold Coin"), three benefits and a violet Check Now (A).
 The other phone is the redesign: a purple glow rising from the bottom, the ticket in gold foil
 with the coin on its edge, gold and white stars, two benefits and a white Check Now (B).

 Motion (ours; the pin is two stills joined by an arrow): A becomes B. The cars burn away from
 the wheels up and their gold drifts as dust onto the ticket, a purple dawn rises from below and
 swallows the cream sheet, gold pours across the ticket where the dust lands, the coin sinks into
 its cradle, jumps out spinning (Blender renders) and lands on the gold with a stamp: the ticket
 squashes, sparks kick off its edge, a ring spreads, a warm light flares behind it and the stars
 burst out of the coin to where the pin has them. While it rests: one foil sweep, one glint on the
 coin, one breath of light round Check Now. Then everything goes home, the dust flying back.

 autoPlay: LOOP = 9 s (timeline in gtr-motion.ts); frame 9 s equals frame 0 (rest A).
 Touch: drag the ticket up to scrub the reveal with your finger (past 40 % or a flick commits,
 otherwise it rewinds), or tap it / Check Now; in the reward drag the ticket to tilt the foil (the
 stars and coin move in parallax, and on a phone the foil follows the device's tilt too), flick or
 tap the coin to spin it (it slows and wobbles face up), tap Check Now for a twinkle, tap × to go
 back. 2 s after the last touch the loop resumes from where it is.
 Reduced motion: no loop; the reward shows still, and taps crossfade between A and B.
*/

const foilEffect = Skia.RuntimeEffect.Make(FOIL_SKSL);
let debugState = '';
const dissolveEffect = Skia.RuntimeEffect.Make(DISSOLVE_SKSL);

/** previewTime pins the timeline at one moment (seconds), for still captures and debugging. */
type Props = { autoPlay?: boolean; previewTime?: number };

type Target = 0 | 1 | 2 | 3 | 4 | 5 | 6; // none, ticket A (tap or scrub), close, coin, cta B, tilt, cta A

export function GoldTicketReward({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const cars = useImage(require('./assets/cars.webp'));
  const coin = useImage(require('./assets/coin-atlas.webp'));
  const regular = useFont(require('./assets/fonts/Poppins-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Poppins-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Poppins-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Poppins-Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!cars || !coin || !regular || !medium || !semi || !bold || !foilEffect || !dissolveEffect) return null;
    const faces = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!faces.regular || !faces.medium || !faces.semi || !faces.bold) return null;
    const labels = makeLabels({ regular: faces.regular, medium: faces.medium, semi: faces.semi, bold: faces.bold });
    const page = blurPage(drawPage(labels));
    if (!page) return null;
    return { page, labels, cars, coin, foil: foilEffect, dissolve: dissolveEffect, dust: makeDust(cars), ...makeStatics(cars) };
  }, [cars, coin, regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: h - REF_H * s };
  }, [w, h]);

  const play = useGtrAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(reduced ? PEAK : 0));
  const clock = useSharedValue(0);
  const time = useSharedValue(reduced ? PEAK : 0);
  const extra = useSharedValue({ spin: 0, hop: 0, twinkle: 0, cta: 0, tiltX: 0, tiltY: 0 });
  // The foil also answers the phone's tilt, around wherever the phone is held (a slow baseline).
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const gyro = useSharedValue({ init: 0, pitch: 0, roll: 0, x: 0, y: 0 });
  // Reduced motion: the two states crossfade (0 A … 1 B) instead of playing the timeline.
  const fade = useSharedValue(1);
  const ready = res !== null && size !== null;

  useEffect(() => {
    const t0 = previewTime ?? (reduced ? PEAK : 0);
    driver.set(makeDriver(t0));
    clock.set(0);
    time.set(t0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__goldTicket.at(2.6)` pins the timeline at a
  // moment, `.play()` lets it run again; used to capture frames for the review sheets.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __goldTicket?: unknown };
    g.__goldTicket = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      // park at a loop time without autoPlay taking over for a minute (touch tests)
      park: (t: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.t = t;
          d.mode = 2;
          d.last = clock.get() + 60;
        }),
    };
    return () => {
      delete g.__goldTicket;
    };
  }, [debugT, driver, clock]);

  const mirror = (s: string) => {
    debugState = s;
  };
  const frameCb = useFrameCallback((info) => {
    if (debugT.get() >= 0) {
      time.set(debugT.get());
      return;
    }
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    const before = d.t;
    const t = stepDriver(d, dt, now, auto);
    const c = coinExtra(d, now);
    const tw = ctaTwinkle(d, now);
    const r = rotation.sensor.get();
    const gy = gyro.get();
    if (gy.init === 0 && (r.pitch !== 0 || r.roll !== 0)) {
      gy.init = 1;
      gy.pitch = r.pitch;
      gy.roll = r.roll;
    }
    const kb = Math.min(1, dt * 0.6); // the rest pose drifts after the hand
    gy.pitch += (r.pitch - gy.pitch) * kb;
    gy.roll += (r.roll - gy.roll) * kb;
    const ks = 1 - Math.exp(-dt * 10);
    gy.x += (Math.max(-1, Math.min(1, (r.roll - gy.roll) / 0.35)) - gy.x) * ks;
    gy.y += (Math.max(-1, Math.min(1, (r.pitch - gy.pitch) / 0.35)) - gy.y) * ks;
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} mode=${d.mode} stop=${d.stop} rate=${d.rate} spin=${d.spinPos.toFixed(1)}`);
    }
    const ba = bAmountAt(t) * 0.6;
    extra.set({ spin: c.spin, hop: c.hop, twinkle: tw, cta: d.ctaP, tiltX: d.tiltX + gy.x * ba, tiltY: d.tiltY + gy.y * ba });
    time.set(t);
    // a finger started this: the landing answers with sound and a medium tap; autoPlay stays silent
    if (d.mode === 1 && before < T.land && t >= T.land && t < T.hold[0]) {
      scheduleOnRN(cue, 'coin');
      scheduleOnRN(buzz, 1);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      const k = fade.get();
      const still = { tiltX: 0, tiltY: 0, cta: 0 };
      if (k < 0.999) drawScene(c, res, geo, frameAt(0), still);
      if (k > 0.001) {
        const p = Skia.Paint();
        p.setAlphaf(k);
        c.saveLayer(p);
        drawScene(c, res, geo, frameAt(PEAK), still);
        c.restore();
      }
    } else if (res) {
      const x = extra.get();
      const f = frameAt(time.get(), x.spin, x.hop, x.twinkle);
      drawScene(c, res, geo, f, { tiltX: x.tiltX, tiltY: x.tiltY, cta: x.cta });
    } else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // Touch targets, in the reference space of the sheet (it stands on the bottom edge).
  const target = useSharedValue<Target>(0);
  const start = useSharedValue({ x: 0, y: 0 });
  const scrubbed = useSharedValue(0);
  const inBox = (x: number, y: number, b: { x: number; y: number; w: number; h: number }, pad: number) => {
    'worklet';
    return x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad;
  };
  const near = (x: number, y: number, cx: number, cy: number, r: number) => {
    'worklet';
    return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
  };
  const toggleReduced = (toB: boolean) => {
    'worklet';
    fade.set(withTiming(toB ? 1 : 0, { duration: 220, reduceMotion: ReduceMotion.Never }));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      start.set({ x, y });
      scrubbed.set(0);
      const showingA = reduced ? fade.get() < 0.5 : inRestA(d.t);
      const showingB = reduced ? fade.get() >= 0.5 : inRewardB(d.t);
      let tg: Target = 0;
      if (showingA) {
        if (inBox(x, y, A.button, 6)) tg = 6;
        else if (inBox(x, y, A.ticket, 10) || near(x, y, A.coin.x, A.coin.y, A.halo + 6)) tg = 1;
      } else if (showingB) {
        if (near(x, y, B.close.x, B.close.y, 24)) tg = 2;
        else if (near(x, y, B.coin.x, B.coin.y, B.coin.r + 10)) tg = 3;
        else if (inBox(x, y, B.button, 6)) tg = 4;
        else if (inBox(x, y, B.ticket, 12)) tg = 5;
      }
      target.set(tg);
      if (reduced) return;
      touched(d, now);
      if (tg === 1 || tg === 6) pressA(d);
      if (tg === 4 || tg === 6) d.ctaAim = 1;
    })
    .onUpdate((e) => {
      if (reduced) return;
      const tg = target.get();
      const d = driver.get();
      d.last = clock.get();
      if (tg === 1 && (e.translationY < -6 || scrubbed.get() > 0)) {
        // the finger scrubs the reveal; one tick as it passes the point of no return
        const before = scrubbed.get();
        const p = scrubTo(d, e.translationY);
        scrubbed.set(Math.max(p, 0.0001));
        if ((before < COMMIT) !== (p < COMMIT) && before > 0) scheduleOnRN(buzz, 2);
      } else if (tg === 5) {
        const cx = B.ticket.x + B.ticket.w / 2;
        const cy = B.ticket.y + B.ticket.h / 2;
        const x = e.x / geo.s;
        const y = (e.y - geo.dy) / geo.s;
        d.aimX = Math.max(-1, Math.min(1, (x - cx) / (B.ticket.w / 2)));
        d.aimY = Math.max(-1, Math.min(1, (y - cy) / (B.ticket.h * 1.2)));
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      const s0 = start.get();
      const tap = Math.hypot(x - s0.x, y - s0.y) < 12;
      target.set(0);
      d.ctaAim = 0;
      if (reduced) {
        if (tap && (tg === 1 || tg === 6)) toggleReduced(true);
        else if (tap && tg === 2) toggleReduced(false);
        return;
      }
      if (tg === 1 && scrubbed.get() > 0) {
        if (scrubEnd(d, e.translationY, e.velocityY)) scheduleOnRN(cue, 'reveal');
      } else if (tg === 1 || tg === 6) {
        const onTarget =
          tap && (inBox(x, y, A.ticket, 16) || near(x, y, A.coin.x, A.coin.y, A.halo + 10) || inBox(x, y, A.button, 10));
        releaseA(d, onTarget);
        if (onTarget) scheduleOnRN(cue, 'reveal');
      } else if (tg === 2 && tap) {
        if (closeB(d)) scheduleOnRN(buzz, 0);
      } else if (tg === 3) {
        flickCoin(d, tap ? 0 : e.velocityX, now);
        scheduleOnRN(cue, 'coin');
        scheduleOnRN(buzz, 0);
      } else if (tg === 4 && tap) {
        d.ctaAt = now;
        scheduleOnRN(cue, 'sparkle');
        scheduleOnRN(buzz, 0);
      }
      d.aimX = 0;
      d.aimY = 0;
      d.last = now;
    });

  const toggleForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      if (reduced) {
        toggleReduced(fade.get() < 0.5);
        return;
      }
      d.last = clock.get();
      if (inRestA(d.t)) {
        d.t = T.commit;
        d.mode = 1;
        d.rate = 1;
        d.stop = PEAK;
      } else if (!closeB(d)) {
        d.mode = 1;
        d.rate = 1;
        d.stop = d.t < PEAK ? PEAK : LOOP;
      }
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
          accessibilityLabel="Thanks for your interest. Book a test drive to win a 1 gram gold coin."
          accessibilityHint="Reveals the gold reward, or closes it when it is open."
          onAccessibilityTap={toggleForAccessibility}
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
