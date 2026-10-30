import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './sca-draw';
import { D, dismiss, type Driver, loopState, makeDriver, N, NEVER, openness, skip, stepDriver, takeOver, toggle, toggleRow } from './sca-motion';
import { C, ITEMS, REF_W, UI } from './sca-theme';
import { type Cue, useScaAudio } from './use-sca-audio';

/*
 Setup Checklist All Set — the setup checklist of a link-in-bio app, and what happens when it is done.

 Look (the pin, a flow of four screens): the profile page under a dimmed bottom sheet, "Your setup
 checklist" with a purple 1/6 badge and a blue-to-purple bar, six rows with round checks (the open
 one shows a line of help, an action button and Skip; done ones are green with their title struck
 through), and at the end a "You're all set!" sheet with a party popper in a lilac circle.

 Motion (ours): each tick is drawn — the green fills in with a little overshoot and a ring pulses
 out, the white tick is stroked, then the title is struck through left to right as it greys; the
 badge's number rolls, the bar springs forward and a gloss runs along it; the row folds shut and
 the next one opens. After the last, the whole sheet folds down into a lilac bubble where the icon
 will be, and the all-set sheet blooms back out of it; the popper pops, confetti flies, and the text
 rises in one line after another.

 autoPlay: LOOP = 9 s (sca-motion.ts); frame 9 s equals frame 0: five items ticked, all set,
 "Maybe later", the sheet down and a fresh checklist up.
 Touch: tap a check to tick or untick it; tap a row to open it; its button ticks it, Skip moves on;
 the close button sends the sheet away and back; on the all-set sheet the button throws confetti
 again and "Maybe later" starts over. 2 s after the last touch the list goes back to its first state
 and the loop resumes.
 Reduced motion: no loop; ticks, rows and the all-set sheet change with short fades only.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function SetupChecklistAllSet({ autoPlay = false, previewTime }: Props) {
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
  const heavy = useFont(require('./assets/fonts/Inter-ExtraBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold || !heavy) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), heavy: heavy.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold || !f.heavy) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold, heavy: f.heavy });
  }, [regular, medium, semi, bold, heavy]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useScaAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const later = (c: Cue, ms: number) => setTimeout(() => play(c), ms);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const live = useSharedValue<Live>({ now: 0, amb: 0, s: loopState(0) });
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__setupList.at(4.4)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __setupList?: unknown };
    g.__setupList = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__setupList;
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
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      const s = d.mode === 0 ? loopState(d.t) : d.s;
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} done=${s.done.map((x) => (x ? 1 : 0)).join('')} open=${s.open.map((x) => (x ? 1 : 0)).join('')} allSet=${s.allSetAt > NEVER ? 1 : 0}`);
    }
    if (d.mode === 0) live.set({ now: d.t, amb: d.amb, s: loopState(d.t) });
    else live.set({ now: reduced ? now + 10 : now, amb: d.amb, s: d.s });
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
    if (res) drawScene(c, res, geo, live.get());
    else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const start = useSharedValue({ x: 0, y: 0 });

  /** What a tap at (x, y) in points means: [kind, row]. Kinds: 1 check, 2 row, 3 action, 4 skip, 5 close, 6 cta, 7 later. */
  const hit = (x: number, y: number, now: number) => {
    'worklet';
    const d = driver.get();
    const s = d.s;
    if (s.leaveAt > NEVER && now < s.backAt + D.enter) return [0, -1];
    const allSet = s.allSetAt > NEVER;
    if (allSet) {
      if (now - s.allSetAt < D.morph + D.bloom) return [0, -1];
      const Ct = UI.allSet.cta;
      if (x > Ct.x && x < Ct.x + Ct.w && y > Ct.y - 6 && y < Ct.y + Ct.h + 6) return [6, -1];
      if (Math.abs(y - UI.allSet.later) < 24 && Math.abs(x - 201) < 80) return [7, -1];
      return [0, -1];
    }
    if (Math.hypot(x - UI.close.x, y - UI.close.y) < 24) return [5, -1];
    const R = UI.rows;
    let top = R.y0;
    for (let i = 0; i < N; i++) {
      const e = openness(s, i, now);
      const hh = R.h + R.open * e;
      if (y >= top && y < top + hh) {
        if (y < top + R.h) return [x < 46 ? 1 : 2, i];
        if (e > 0.6) {
          const cy = top + R.h / 2;
          const ay = cy + UI.action.dy;
          if (Math.abs(y - ay) < 22) {
            const aw = 26 + ITEMS[i].action.length * 8.4;
            if (x > R.textX - 6 && x < R.textX + aw + 6) return [3, i];
            if (x > R.textX + aw + 10 && x < R.textX + aw + 90) return [4, i];
          }
        }
        return [0, i];
      }
      top += hh;
    }
    return [0, -1];
  };

  const after = (outcome: string) => {
    if (outcome === 'check') {
      play('check');
      buzz(0);
    } else if (outcome === 'uncheck') {
      play('uncheck');
      buzz(2);
    } else if (outcome === 'complete') {
      play('check');
      buzz(0);
      later('complete', Math.round((0.4 + D.morph) * 1000));
      later('pop', Math.round((0.4 + D.morph + 0.2) * 1000));
      setTimeout(() => buzz(3), Math.round((0.4 + D.morph + 0.2) * 1000));
    }
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      takeOver(d, clock.get());
      start.set({ x: e.x / geo.s, y: e.y / geo.s });
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const x = e.x / geo.s;
      const y = e.y / geo.s;
      const s0 = start.get();
      if (Math.hypot(x - s0.x, y - s0.y) > 14) return;
      const [kind, row] = hit(x, y, now);
      if (kind === 1 || kind === 3) {
        if (kind === 3) d.s.pressRow = row;
        scheduleOnRN(after, toggle(d, row, now));
      } else if (kind === 2) {
        toggleRow(d, row, now);
        scheduleOnRN(buzz, 2);
      } else if (kind === 4) {
        skip(d, row, now);
        scheduleOnRN(buzz, 2);
      } else if (kind === 5) {
        if (dismiss(d, now, false)) scheduleOnRN(buzz, 0);
      } else if (kind === 6) {
        d.s.ctaAt = now;
        scheduleOnRN(cue, 'pop');
        scheduleOnRN(buzz, 3);
      } else if (kind === 7) {
        d.s.laterAt = now;
        if (dismiss(d, now + 0.15, true)) scheduleOnRN(buzz, 0);
      }
    });

  // VoiceOver: a double tap ticks the next item.
  const tickNext = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      for (let i = 0; i < N; i++) {
        if (!d.s.done[i]) {
          toggle(d, i, now);
          return;
        }
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
          accessibilityLabel="Your setup checklist, 1 of 6 done. Add your name and bio."
          accessibilityHint="Ticks the next item."
          onAccessibilityTap={tickNext}
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
