import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './sce-draw';
import { type Driver, grabCup, makeDriver, type Pose, poseAt, poseOf, retry, stepDriver, T, takeOver } from './sce-motion';
import { C, DY, REF_W, S, SCENE, UI } from './sce-theme';
import { type Cue, useSceAudio } from './use-sce-audio';

/*
 Spilled Coffee Error — an error screen: a paper cup knocked over on a desk, coffee on the paper.

 Look (the pin, an illustrated error screen): a quiet grey room with a plant and two books, a white
 cup with a green sleeve lying on its side, its coffee spilled over a sheet of paper and its lid
 beside it; "Error!", "Something went wrong, Please try again later" and a white RETRY pill.

 Motion (ours): Retry runs time backwards. Faint tracking lines run down the screen while the coffee
 flows back off the paper into the cup, the cup stands itself up with a wobble and its lid flies back
 on; "Trying again…", a steaming cup and loading dots in the button. Then it fails again: the cup
 tips over, faster and faster, lands with a bounce, the lid pops off and the coffee splashes out.
 The cup is a tapered cylinder turned in 3D and projected each frame, so every angle is real; the
 puddle is a metaball of blobs that slide out of (and back into) the mouth.

 autoPlay: LOOP = 9 s (sce-motion.ts); frame 9 s equals frame 0.
 Touch: Retry (presses in); drag the cup sideways to tip it (it pours past 55°, falls past 45°,
 rights itself below) or to stand it up; tap the lid on the floor and it hops. 2 s after the last
 touch the cup goes back on its side, spilled, and the loop resumes.
 Reduced motion: no rewind effect or steam; the retry swaps states with short fades.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

// A retry's sounds, timed to its script; a finger on the cup cancels the rest.
const scriptTimers: ReturnType<typeof setTimeout>[] = [];
function cancelScript() {
  for (const t of scriptTimers.splice(0)) clearTimeout(t);
}
function playScript(play: (c: Cue) => void, buzz: (kind: number) => void) {
  cancelScript();
  const at = (s: number, f: () => void) => scriptTimers.push(setTimeout(f, Math.round(s * 1000)));
  play('rewind');
  at(T.rise - T.press + 0.25, () => play('clack'));
  at(T.land - T.press - 0.05, () => {
    play('tip');
    buzz(1);
  });
  at(T.land - T.press + 0.1, () => {
    play('splash');
    buzz(3);
  });
}

export function SpilledCoffeeError({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Manrope_400Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Manrope_500Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Manrope_600SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Manrope_700Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold });
  }, [regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useSceAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const scriptSounds = () => playScript(play, buzz);
  useEffect(() => cancelScript, []);

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Pose>(poseAt(0));
  const prevTheta = useSharedValue(90);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__spilledCoffee.at(2.0)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __spilledCoffee?: unknown };
    g.__spilledCoffee = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__spilledCoffee;
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
    const p = poseOf(d, now);
    if (reduced) {
      p.rewind = 0;
      p.steam = 0;
    }
    // sounds for a cup a finger moved (the retry script has its own)
    if (d.mode === 2 && d.retryAt < 0) {
      const before = prevTheta.get();
      if (before < 86 && p.theta >= 86) {
        scheduleOnRN(cue, 'tip');
        scheduleOnRN(buzz, 1);
      } else if (before > 3 && p.theta <= 3 && d.dragging === 0) {
        scheduleOnRN(cue, 'clack');
        scheduleOnRN(buzz, 0);
      }
    }
    prevTheta.set(p.theta);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} theta=${p.theta.toFixed(1)} spill=${p.spill.toFixed(2)} lid=${p.lid.toFixed(2)} retry=${d.retryAt > 0 ? (now - d.retryAt).toFixed(2) : '-'}`);
    }
    frame.set(p);
  };
  const frameCb = useFrameCallback((tick) => {
    // a frame that throws must not stop the callback for good
    try {
      frameStep(tick.timeSincePreviousFrame ?? 16);
    } catch (err) {
      if (__DEV__) scheduleOnRN(mirror, `error ${String(err)}`);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) drawScene(c, res, geo, frame.get());
    else c.drawColor(Skia.Color(C.floor));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const zone = useSharedValue(0); // 1 button, 2 cup, 3 lid
  const start = useSharedValue({ x: 0, y: 0, theta: 0 });

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S, y: (y / geo.s - DY) / S };
  };
  /** Is a point on the cup? (Near the segment from its base to its mouth, as it stands now.) */
  const onCup = (p: { x: number; y: number }, theta: number) => {
    'worklet';
    const t = (theta * Math.PI) / 180;
    const K = SCENE.cup;
    const fx = -Math.cos(SCENE.yaw);
    const b = { x: K.base.x + K.rb * (1 - Math.cos(t)) * fx, y: K.base.y - K.rb * Math.sin(t) };
    const m = { x: b.x + Math.sin(t) * fx * K.L, y: b.y - Math.cos(t) * K.L * Math.cos(SCENE.elev) };
    const vx = m.x - b.x;
    const vy = m.y - b.y;
    const u = Math.max(0, Math.min(1, ((p.x - b.x) * vx + (p.y - b.y) * vy) / (vx * vx + vy * vy)));
    return Math.hypot(p.x - (b.x + vx * u), p.y - (b.y + vy * u)) < K.rm + 24;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const p = toPin(e.x, e.y);
      const B = UI.button;
      const pose = poseOf(d, now);
      if (p.x > B.x - 10 && p.x < B.x + B.w + 10 && p.y > B.y - 10 && p.y < B.y + B.h + 10) {
        zone.set(1);
        d.pressing = 1;
        d.pressAt = now;
      } else if (onCup(p, pose.theta)) {
        zone.set(2);
        grabCup(d, now);
        scheduleOnRN(cancelScript);
        start.set({ x: p.x, y: p.y, theta: d.theta });
      } else if (pose.lid > 0.95 && Math.hypot(p.x - SCENE.lid.x, (p.y - SCENE.lid.y) * 2.5) < SCENE.lid.r + 20) zone.set(3);
      else zone.set(0);
    })
    .onUpdate((e) => {
      const d = driver.get();
      d.last = clock.get();
      if (zone.get() !== 2) return;
      const p = toPin(e.x, e.y);
      // dragging left tips the cup over; right stands it up
      d.dragTheta = Math.max(0, Math.min(95, start.get().theta - (p.x - start.get().x) * 0.5));
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const z = zone.get();
      zone.set(0);
      if (z === 2) {
        d.dragging = 0;
        return;
      }
      if (z === 1) {
        d.pressing = 0;
        const p = toPin(e.x, e.y);
        const B = UI.button;
        if (p.x > B.x - 20 && p.x < B.x + B.w + 20 && p.y > B.y - 20 && p.y < B.y + B.h + 20) {
          retry(d, now);
          scheduleOnRN(cue, 'tap');
          scheduleOnRN(buzz, 0);
          if (!reduced) scheduleOnRN(scriptSounds);
        }
        return;
      }
      if (z === 3) {
        d.hopAt = now;
        scheduleOnRN(cue, 'tap');
        scheduleOnRN(buzz, 2);
      }
    });

  // VoiceOver: a double tap retries.
  const retryForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      retry(d, now);
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
          accessibilityLabel="Error! Something went wrong, please try again later. Retry."
          accessibilityHint="Tries again."
          onAccessibilityTap={retryForAccessibility}
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
  screen: { flex: 1, backgroundColor: C.floor },
});

