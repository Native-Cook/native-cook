import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './cwo-draw';
import { type Driver, frameAt, LINKS, linkOn, makeDriver, N, nodeHome, pluck, stepDriver, touched, vibration } from './cwo-motion';
import { BARS, BUTTON, C, FITS, NODES, REF_H, REF_W } from './cwo-theme';
import { type Cue, useCwoAudio } from './use-cwo-audio';

/*
 Career Web Onboarding — the first screen of an invented job-finder app.

 Look (the pin, a still): "Build Your Future, Build Your Dream" with a lavender marker behind
 "Dream", a hand-drawn arrow down to a web of three portraits and four role bubbles (two joined by
 thick white bars), and a white "Get Started !" pill with a dark arrow button.

 Motion (ours): the web is elastic. The marker lifts off "Dream" and sweeps it again, the arrow
 redraws itself and lands with a pop; then a person is picked, two links grow out of them to the
 roles that fit (they snap on), the person is tugged down and let go, and the whole web wobbles
 on its links, which stretch thin and ring like plucked strings. The links retract, and the
 button's arrow nudges forward.

 autoPlay: LOOP = 9 s (cwo-motion.ts); frame 9 s equals frame 0.
 Touch: drag any portrait or bubble (the links pull its neighbours along, everything bumps and
 springs home); tap a person to grow or retract their links; tap a link to pluck it; tap the
 button (its arrow shoots off and comes round again). 2 s after the last touch the loop resumes.
 Reduced motion: no loop and no wobble; the web holds still, taps still grow links (at once).
*/

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2 | 3; // none, node, link, button

let debugState = '';

export function CareerWebOnboarding({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const pa = useImage(require('./assets/person-a.webp'));
  const pb = useImage(require('./assets/person-b.webp'));
  const pc = useImage(require('./assets/person-c.webp'));
  const semi = useFont(require('./assets/fonts/Poppins-SemiBold.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Poppins-Medium.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!pa || !pb || !pc || !semi || !medium) return null;
    const s = semi.getTypeface();
    const m = medium.getTypeface();
    if (!s || !m) return null;
    return makeRes({ semi: s, medium: m }, [pa, pb, pc]);
  }, [pa, pb, pc, semi, medium]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: h - REF_H * s };
  }, [w, h]);
  // every link's resting length, from the pin's layout
  const homeLen = useMemo(() => LINKS.map(([a, b]) => Math.hypot(NODES[b].x - NODES[a].x, NODES[b].y - NODES[a].y)), []);

  const play = useCwoAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const target = useSharedValue<Target>(0);
  const zeros = (n: number, v = 0) => new Array<number>(n).fill(v);
  const live = useSharedValue<Live>({
    px: NODES.map((n) => n.x),
    py: NODES.map((n) => n.y),
    grow: zeros(3),
    vib: zeros(LINKS.length),
    hold: -1,
    press: 0,
    shoot: -1,
    pick: zeros(3, -1),
  });
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
    time.set(previewTime ?? 0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__careerWeb.at(3.4)` pins the loop, `.play()`
  // resumes, `.park(t)` parks it for touch tests, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __careerWeb?: unknown };
    g.__careerWeb = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      park: (t: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.t = t;
          d.mode = 2;
          d.last = clock.get() + 60;
        }),
    };
    return () => {
      delete g.__careerWeb;
    };
  }, [debugT, driver, clock]);
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
    }
    const f = frameAt(d.t);
    const out = stepDriver(d, debugT.get() >= 0 || reduced ? 0 : dt, now, auto && debugT.get() < 0, f.grow);
    const px: number[] = [];
    const py: number[] = [];
    for (let i = 0; i < N; i++) {
      const hm = nodeHome(i, out.t, d.amb);
      px.push(hm.x + d.ox[i]);
      py.push(hm.y + d.oy[i]);
    }
    const vib: number[] = [];
    for (let l = 0; l < LINKS.length; l++) {
      // the loop's tug rings the links it pulls on
      const loopRing = d.mode === 0 ? f.tugVib * (l >= BARS.length ? 7 : 3) * linkOn(l, out.grow) : 0;
      vib.push(vibration(d, l, now) + loopRing);
    }
    const pick: number[] = [];
    for (let p = 0; p < 3; p++) {
      const tau = now - d.pickAt[p];
      pick.push(d.pickAt[p] >= 0 && tau < 0.7 ? tau / 0.7 : p === 1 && d.mode === 0 ? f.pick : -1);
    }
    const shootT = d.shootAt >= 0 ? (now - d.shootAt) / 0.5 : -1;
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${out.t.toFixed(2)} mode=${d.mode} hold=${d.hold} tg=${target.get()} ox1=${d.ox[1].toFixed(1)} grow=${out.grow.map((g) => g.toFixed(2)).join(',')} vib0=${vib[0].toFixed(1)}`);
    }
    live.set({ px, py, grow: out.grow, vib, hold: d.hold, press: d.press, shoot: shootT >= 0 && shootT < 1 ? shootT : -1, pick });
    time.set(out.t);
  };

  const frameCb = useFrameCallback((info) => {
    try {
      frameStep(info.timeSincePreviousFrame ?? 16);
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
    if (res) {
      const f = frameAt(reduced ? 0 : time.get());
      drawScene(c, res, geo, f, live.get(), homeLen);
    } else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced, homeLen]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---- touch (reference space: scaled to the width, standing on the bottom edge)
  const start = useSharedValue({ x: 0, y: 0 });
  const moved = useSharedValue(0);
  const hitLink = useSharedValue(-1);

  const segDist = (x: number, y: number, ax: number, ay: number, bx: number, by: number) => {
    'worklet';
    const dx = bx - ax;
    const dy = by - ay;
    const l2 = dx * dx + dy * dy || 1;
    const u = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / l2));
    return Math.hypot(x - (ax + dx * u), y - (ay + dy * u));
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
      moved.set(0);
      hitLink.set(-1);
      const lv = live.get();
      let tg: Target = 0;
      const b = BUTTON;
      if (x > b.x - 6 && x < b.x + b.w + 6 && y > b.y - 6 && y < b.y + b.h + 6) tg = 3;
      else {
        let best = -1;
        let bestD = 1e9;
        for (let i = 0; i < N; i++) {
          const dist = Math.hypot(x - lv.px[i], y - lv.py[i]);
          if (dist < NODES[i].r + 6 && dist < bestD) {
            best = i;
            bestD = dist;
          }
        }
        if (best >= 0) {
          tg = 1;
          if (!reduced) {
            touched(d, now);
            d.hold = best;
            d.fx = x;
            d.fy = y;
            d.gdx = x - lv.px[best];
            d.gdy = y - lv.py[best];
          }
        } else {
          for (let l = 0; l < LINKS.length; l++) {
            if (linkOn(l, lv.grow) < 0.9) continue;
            const a = LINKS[l][0];
            const bb = LINKS[l][1];
            if (segDist(x, y, lv.px[a], lv.py[a], lv.px[bb], lv.py[bb]) < 16) {
              tg = 2;
              hitLink.set(l);
              break;
            }
          }
        }
      }
      target.set(tg);
      if (reduced) return;
      if (tg === 3) {
        touched(d, now);
        d.pressAim = 1;
      }
      if (tg === 2) touched(d, now);
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      d.last = clock.get();
      if (Math.hypot(e.translationX, e.translationY) > 8) moved.set(1);
      if (target.get() === 1) {
        d.fx = e.x / geo.s;
        d.fy = (e.y - geo.dy) / geo.s;
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const tap = moved.get() === 0;
      target.set(0);
      d.pressAim = 0;
      d.last = now;
      const held = d.hold;
      d.hold = -1;
      if (tg === 1 && held >= 0) {
        if (tap && NODES[held].kind === 'person') {
          // a person: grow (or retract) the links to the roles that fit them
          const p = held;
          const on = (d.grow[p] >= 0 ? d.grow[p] : 0) > 0.5;
          if (reduced) {
            d.grow[p] = on ? 0 : 1;
            d.growAim[p] = d.grow[p];
          } else {
            if (d.grow[p] < 0) d.grow[p] = on ? 1 : 0;
            d.growAim[p] = on ? 0 : 1;
            d.pickAt[p] = now;
          }
          scheduleOnRN(cue, on ? 'pluck' : 'attach');
          scheduleOnRN(buzz, 2);
        } else if (!tap && !reduced) {
          // let go: the node flies on with the finger's speed and its links ring
          d.vx[held] = e.velocityX / geo.s;
          d.vy[held] = e.velocityY / geo.s;
          const sp = Math.min(1, Math.hypot(d.vx[held], d.vy[held]) / 1400);
          for (let l = 0; l < LINKS.length; l++) {
            if (LINKS[l][0] === held || LINKS[l][1] === held) pluck(d, l, 5 + 9 * sp, now);
          }
          if (sp > 0.25) {
            scheduleOnRN(cue, 'pluck');
            scheduleOnRN(buzz, 0);
          }
        }
      } else if (tg === 2 && tap && hitLink.get() >= 0 && !reduced) {
        pluck(d, hitLink.get(), 11, now);
        scheduleOnRN(cue, 'pluck');
        scheduleOnRN(buzz, 0);
      } else if (tg === 3 && tap) {
        d.shootAt = now;
        scheduleOnRN(buzz, 1);
      }
    });

  const growForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      touched(d, clock.get());
      const on = (d.grow[0] >= 0 ? d.grow[0] : 0) > 0.5;
      for (let p = 0; p < FITS.length; p++) {
        if (d.grow[p] < 0) d.grow[p] = on ? 1 : 0;
        d.growAim[p] = on ? 0 : 1;
      }
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
          accessibilityLabel="Build your future, build your dream. Three people linked to the roles that fit them: UX/UI designer, product designer, software engineer, Flutter developer. Get started."
          accessibilityHint="Shows or hides the links between people and roles."
          onAccessibilityTap={growForAccessibility}
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
