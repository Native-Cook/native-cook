import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './cad-draw';
import { type Driver, type Frame, frameAt, frameOf, levelAt, makeDriver, pmOf, stepDriver, takeOver } from './cad-motion';
import { C, LEVELS, REF_W, S, UI, X0, Y0 } from './cad-theme';
import { type Cue, useCadAudio } from './use-cad-audio';

/*
 Clean Air Dial — the control screen of an air purifier.

 Look (the pin, a phone mock-up): a ring of sixty ticks, green from the top for the fan speed, around
 a black disk reading "PM 2.5 012", temperature, humidity and particles, with a green "Moderate" cap
 at its foot; Speed, Timer and Scenes below, then Auto, Power and Settings.

 Motion (ours): the air in the dial is visible. Three hundred motes of dust drift in it, turning with
 the air (the inner ones twice as fast). Turn the fan up and the green ticks run round with a little
 overshoot, faint swirls show in the disk, and the dust spirals into the core and is gone while the
 reading rolls down 012 → 004 and the cap slides from Moderate to Good; turn it down and dust drifts
 back in from the edge. Too low, or off, and the room gets dustier: more motes come in and the cap
 turns Poor.

 autoPlay: LOOP = 9 s (cad-motion.ts); frame 9 s equals frame 0.
 Touch: drag round the ring to set the speed (it follows the finger tick by tick, then snaps to Low,
 Medium, High or Turbo); tap Speed (its arrows step up or down); Timer, Scenes, Auto and Power toggle.
 2 s after the last touch it goes back to Medium and, once the air has settled, the loop resumes.
 Reduced motion: the dust stands still; changes land without springs.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function CleanAirDial({ autoPlay = false, previewTime }: Props) {
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

  const play = useCadAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0, 0));
  const quality = useSharedValue(1);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__airDial.at(3.0)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __airDial?: unknown };
    g.__airDial = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__airDial;
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
    } else {
      stepDriver(d, dt, now, auto);
      if (reduced && d.mode === 2 && d.dragging === 0) {
        // no springs: the speed and the air land at once
        d.g = d.power ? LEVELS[d.level].ticks : 0;
        d.gv = 0;
        d.f = d.power ? LEVELS[d.level].target : -0.5;
      }
    }
    const f = frameOf(d, now);
    if (reduced) {
      f.amb = 0;
      f.phi = 0;
    }
    // a chime when a finger's work clears the air
    const pm = pmOf(f.f);
    const q = pm < 6.5 ? 0 : pm < 15.5 ? 1 : 2;
    if (q !== quality.get()) {
      if (q === 0 && d.mode === 2) {
        scheduleOnRN(cue, 'chime');
        scheduleOnRN(buzz, 3);
      }
      quality.set(q);
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} g=${f.g.toFixed(2)} f=${f.f.toFixed(3)} pm=${pm.toFixed(1)} level=${d.level} power=${d.power ? 1 : 0} home=${d.homing}`);
    }
    frame.set(f);
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
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const zone = useSharedValue(0); // 1 ring
  const start = useSharedValue({ x: 0, y: 0 });
  const lastTick = useSharedValue(0);

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S + X0, y: (y / geo.s - Y0) / S };
  };
  const ringTick = (p: { x: number; y: number }) => {
    'worklet';
    const D = UI.dial;
    let a = (Math.atan2(p.x - D.x, -(p.y - D.y)) * 180) / Math.PI;
    if (a < 0) a += 360;
    return Math.max(1, Math.min(36, a / 6 + 0.5));
  };
  /** Which button a point is on: 0 speed, 1 timer, 2 scenes, 3 auto, 4 power, 5 settings, −1 none. */
  const buttonAt = (p: { x: number; y: number }) => {
    'worklet';
    const P = UI.chips;
    for (let i = 0; i < 3; i++) {
      if (Math.abs(p.x - P.x[i]) < P.w / 2 + 6 && Math.abs(p.y - P.y) < P.h / 2 + 6) return i;
      if (Math.abs(p.x - UI.bottom.x[i]) < P.w / 2 + 6 && Math.abs(p.y - UI.bottom.y) < P.h / 2 + 6) return 3 + i;
    }
    return -1;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const p = toPin(e.x, e.y);
      start.set(p);
      const D = UI.dial;
      const dist = Math.hypot(p.x - D.x, p.y - D.y);
      if (dist > D.disk - 30 && dist < D.r1 + 60 && d.power) {
        zone.set(1);
        d.dragging = 1;
        d.dragG = ringTick(p);
        lastTick.set(Math.round(d.dragG));
        scheduleOnRN(buzz, 2);
        scheduleOnRN(cue, 'tick');
      } else {
        zone.set(0);
        const b = buttonAt(p);
        if (b >= 0) {
          d.press = b;
          d.pressLast = b;
          d.pressAt = now;
        }
      }
    })
    .onUpdate((e) => {
      const d = driver.get();
      if (zone.get() !== 1) return;
      d.last = clock.get();
      d.dragG = ringTick(toPin(e.x, e.y));
      const tk = Math.round(d.dragG);
      if (tk !== lastTick.get()) {
        lastTick.set(tk);
        scheduleOnRN(buzz, 2);
        scheduleOnRN(cue, 'tick');
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      if (zone.get() === 1) {
        const before = d.level;
        d.level = levelAt(d.dragG);
        d.g = d.dragG;
        d.dragging = 0;
        d.auto = false;
        if (d.level > before) scheduleOnRN(cue, 'whoosh');
        scheduleOnRN(buzz, 0);
        zone.set(0);
        return;
      }
      const b = d.press;
      if (b >= 0) {
        d.press = -1;
        d.releaseAt = now;
      }
      const p = toPin(e.x, e.y);
      const s0 = start.get();
      if (b < 0 || Math.hypot(p.x - s0.x, p.y - s0.y) > 24 || buttonAt(p) !== b) return;
      scheduleOnRN(cue, 'tap');
      if (b === 0) {
        if (!d.power) return;
        const P = UI.chips;
        const arrows = p.x > P.x[0] + 14;
        const up = !arrows || p.y < P.y;
        const next = up ? (arrows ? Math.min(LEVELS.length - 1, d.level + 1) : (d.level + 1) % LEVELS.length) : Math.max(0, d.level - 1);
        if (next > d.level) scheduleOnRN(cue, 'whoosh');
        d.level = next;
        d.auto = false;
        d.autoAt = now - 1;
        scheduleOnRN(buzz, 2);
      } else if (b === 1) {
        d.timer = !d.timer;
        d.timerAt = now;
        scheduleOnRN(buzz, 0);
      } else if (b === 2) {
        d.scenes = !d.scenes;
        d.scenesAt = now;
        scheduleOnRN(buzz, 0);
      } else if (b === 3) {
        d.auto = !d.auto;
        d.autoAt = now;
        scheduleOnRN(buzz, 0);
      } else if (b === 4) {
        d.power = !d.power;
        d.powerAt = now;
        scheduleOnRN(buzz, 1);
        if (d.power) scheduleOnRN(cue, 'whoosh');
      } else scheduleOnRN(buzz, 0);
    });

  // VoiceOver: a double tap steps the speed up (and round).
  const stepSpeed = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      takeOver(d, clock.get());
      d.level = (d.level + 1) % LEVELS.length;
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Air purifier. PM 2.5: 12, moderate. Speed: medium."
          accessibilityHint="Double tap to change the fan speed."
          onAccessibilityTap={stepSpeed}
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
