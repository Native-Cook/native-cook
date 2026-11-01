import { BlurStyle, Canvas, Path, Picture, Skia, type SkRSXform, StrokeCap, StrokeJoin, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  BLUE,
  centreLine,
  easeIn,
  easeInOut,
  FONT,
  LOOP,
  makeField,
  N,
  PATTERN,
  RIBBON,
  S,
  seg,
  smooth,
  SPEED,
  stepField,
  WHITE,
  Z,
  type Field,
} from './rro-motion';
import { type Cue, useRroAudio } from './use-rro-audio';

/*
 Ribbon Road Onboarding — the first screen of an invented events app.

 Look (the pin, a still of three screens; this is the first): a dark screen, "Try new social
 connections with our → assistance" at the top with "social" on a blue pill, two thick ribbons, one
 electric blue and one white, winding across the middle like a road with "NEW EVENTS FOR YOU" printed
 along them, then a line of copy and a yellow Get started button with two round sign-in buttons.

 Motion (ours): the ribbons unroll from the left edge and the words run along them, the white one the
 other way. Pluck a ribbon and it stretches toward the finger like a rubber band, the words slowing as
 it is held; let go and it wobbles back. Get started makes the words race and the
 tails chase the heads off the screen, then the road unrolls again.

 autoPlay: LOOP = 6.4 s. 0-1.1 s unroll; 1.75-2.5 s a finger plucks the blue ribbon and lets go;
 3.8 s Get started; 4.05-5.25 s the tails race off; empty until 6.4 s, which equals frame 0.
 Reduced motion: the ribbons are drawn still; Get started works without the race.
*/

let debugState = '';

type Props = { autoPlay?: boolean };

export function RibbonRoadOnboarding({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [fontsOk] = useFonts({
    'RRO-Regular': require('./assets/fonts/Manrope_400Regular.ttf'),
    'RRO-SemiBold': require('./assets/fonts/Manrope_600SemiBold.ttf'),
  });
  const font = useFont(require('./assets/fonts/Archivo-ExtraBold.ttf'), FONT);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const lines = useMemo(() => ({ b: centreLine(BLUE, w, h, N), wh: centreLine(WHITE, w, h, N) }), [w, h]);
  const bx = lines.b.xs;
  const by = lines.b.ys;
  const wx = lines.wh.xs;
  const wy = lines.wh.ys;
  // the pattern's glyphs, advances and length
  const glyphs = useMemo(() => {
    if (!font) return { ids: [] as number[], adv: [] as number[], len: 1, cap: FONT * 0.7 };
    const ids = font.getGlyphIDs(PATTERN);
    const adv = font.getGlyphWidths(ids).map((a) => a + 0.6);
    return { ids, adv, len: adv.reduce((a, b) => a + b, 0), cap: FONT * 0.7 };
  }, [font]);

  const play = useRroAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const field = useSharedValue<Field>(makeField());
  const tick = useSharedValue(0);
  // the road: e seconds since it began to unroll, z seconds since Get started (-1 none), phase of the words
  const road = useSharedValue({ e: 0, z: -1, phase: 0, flow: 1 });
  const finger = useSharedValue({ on: 0, x: 0, y: 0, press: 0, vx: 0, grab: -1, gi: 0, gx: 0, gy: 0 });
  const btn = useSharedValue(0); // Get started pressed, 0..1
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __ribbonRoad?: unknown };
    g.__ribbonRoad = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__ribbonRoad;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** The script's virtual finger at loop time t: it plucks the blue ribbon at sample 100 and pulls. */
  const scriptFinger = (t: number) => {
    'worklet';
    const k = smooth(seg(t, S.press[0], S.press[0] + 0.55));
    const on = t >= S.press[0] && t < S.press[1];
    const i = 100;
    return { on: on ? 1 : 0, gi: i, gx: bx[i], gy: by[i], x: bx[i] + 70 * k, y: by[i] + 44 * k };
  };

  /** One frame of the whole scene; `scripted` drives it from loop time t. */
  const advance = (t: number, dt: number, now: number, scripted: boolean) => {
    'worklet';
    const rd = road.get();
    const fg = finger.get();
    if (scripted) {
      rd.e = t;
      rd.z = t >= S.go + 0.15 ? t - S.go - 0.15 : -1;
      const sf = scriptFinger(t);
      fg.on = sf.on;
      if (sf.on) {
        if (fg.grab !== 0) {
          fg.grab = 0;
          fg.gi = sf.gi;
          fg.gx = sf.gx;
          fg.gy = sf.gy;
        }
        fg.x = sf.x;
        fg.y = sf.y;
      }
      fg.vx = 0;
      btn.set(t >= S.go && t < S.go + 0.18 ? 1 : 0);
    } else {
      rd.e += dt;
      if (rd.z >= 0) {
        rd.z += dt;
        if (rd.z > Z.again) {
          rd.z = -1;
          rd.e = 0;
        }
      }
      btn.set(btn.get() * Math.exp(-dt * 14));
    }
    // how hard the finger presses (a quick spring in, a quick release)
    fg.press += ((fg.on ? 1 : 0) - fg.press) * (1 - Math.exp(-dt * (fg.on ? 18 : 30)));
    // the words: a cruise speed, carried by a finger dragging along, boosted after Get started
    const boost = rd.z >= 0 && !reduced ? 1 + 5 * smooth(seg(rd.z, Z.boost[0], Z.boost[1])) : 1;
    // a plucked ribbon holds its words still while it is stretched
    const want = (fg.on && fg.grab >= 0 ? 0.35 : 1) * boost;
    rd.flow += (want - rd.flow) * (1 - Math.exp(-dt * 4));
    if (!reduced) rd.phase += SPEED * rd.flow * dt;
    stepField(field.get(), fg.grab, fg.gi, fg.x - fg.gx, fg.y - fg.gy, fg.press, dt);
    if (!fg.on && fg.press < 0.01) fg.grab = -1;
    void now;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    let t = time.get();
    if (dbg >= 0) {
      // replay from 0 so the pinned frame holds the wobble and the words where the loop has them
      field.set(makeField());
      road.set({ e: 0, z: -1, phase: 0, flow: 1 });
      finger.set({ on: 0, x: 0, y: 0, press: 0, vx: 0, grab: -1, gi: 0, gx: 0, gy: 0 });
      let st = 0;
      while (st < dbg) {
        const d = Math.min(1 / 60, dbg - st);
        st += d;
        advance(st, d, st, true);
      }
      t = dbg;
      debugT.set(-2);
    } else if (dbg === -2) {
      // pinned
    } else {
      const touched = finger.get().on === 1 || now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) {
          t -= LOOP;
          road.get().phase = 0;
        }
        advance(t, dt, now, true);
      } else advance(t, dt, now, false);
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      const rd = road.get();
      scheduleOnRN(mirror, `t=${t.toFixed(2)} e=${rd.e.toFixed(2)} z=${rd.z.toFixed(2)} flow=${rd.flow.toFixed(2)} grab=${finger.get().grab} on=${finger.get().on} press=${finger.get().press.toFixed(2)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (!font) return rec.finishRecordingAsPicture();
    const rd = road.get();
    const f = field.get();
    const head = reduced ? 1 : easeInOut(seg(rd.e, S.draw[0], S.draw[1]));
    const tail = rd.z >= 0 && !reduced ? easeIn(seg(rd.z, Z.tail[0], Z.tail[1])) : 0;
    if (head <= tail + 0.001) return rec.finishRecordingAsPicture();

    const ribbon = (xs: number[], ys: number[], off: number, fill: string, ink: string, dir: number, shadow: boolean) => {
      // the deformed centre line, smoothed with quadratic segments through the midpoints
      const pb = Skia.PathBuilder.Make();
      pb.moveTo(xs[0] + f.ox[off], ys[0] + f.oy[off]);
      for (let i = 1; i < N - 1; i++) {
        const x0 = xs[i] + f.ox[off + i];
        const y0 = ys[i] + f.oy[off + i];
        const x1 = xs[i + 1] + f.ox[off + i + 1];
        const y1 = ys[i + 1] + f.oy[off + i + 1];
        pb.quadTo(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2);
      }
      pb.lineTo(xs[N - 1] + f.ox[off + N - 1], ys[N - 1] + f.oy[off + N - 1]);
      const it = Skia.ContourMeasureIter(pb.build(), false, 1);
      const cm = it.next();
      if (!cm) return;
      const C = cm.length();
      const a = tail * C;
      const b = head * C;
      const part = cm.getSegment(a, b, true);
      if (shadow) {
        const sp = Skia.Paint();
        sp.setAntiAlias(true);
        sp.setStyle(1);
        sp.setStrokeWidth(RIBBON + 4);
        sp.setStrokeCap(StrokeCap.Round);
        sp.setStrokeJoin(StrokeJoin.Round);
        sp.setColor(Skia.Color('#000000'));
        sp.setAlphaf(0.45);
        sp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
        c.save();
        c.translate(0, 6);
        c.drawPath(part, sp);
        c.restore();
      }
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setStyle(1);
      p.setStrokeWidth(RIBBON);
      p.setStrokeCap(StrokeCap.Round);
      p.setStrokeJoin(StrokeJoin.Round);
      p.setColor(Skia.Color(fill));
      c.drawPath(part, p);
      // the words, glyph by glyph along the line, clear of the rounded head and tail
      const L = glyphs.len;
      const n = glyphs.ids.length;
      const ph = (((dir * rd.phase) % L) + L) % L;
      const ids: number[] = [];
      const xf: SkRSXform[] = [];
      let s = ph - L;
      let j = 0;
      while (s < b) {
        const adv = glyphs.adv[j];
        const mid = s + adv / 2;
        if (mid > a + 14 && mid < b - 14) {
          const pt = cm.getPosTan(mid);
          const tx = pt[1].x;
          const ty = pt[1].y;
          const ox = pt[0].x - tx * (adv / 2) - ty * (glyphs.cap / 2);
          const oy = pt[0].y - ty * (adv / 2) + tx * (glyphs.cap / 2);
          ids.push(glyphs.ids[j]);
          xf.push(Skia.RSXform(tx, ty, ox, oy));
        }
        s += adv;
        j = (j + 1) % n;
      }
      if (ids.length > 0) {
        const blob = Skia.TextBlob.MakeFromRSXformGlyphs(ids, xf, font);
        const tp = Skia.Paint();
        tp.setAntiAlias(true);
        tp.setColor(Skia.Color(ink));
        c.drawTextBlob(blob, 0, 0, tp);
      }
    };
    ribbon(bx, by, 0, '#3d34ec', '#ffffff', 1, false);
    ribbon(wx, wy, N, '#f3f3f1', '#17171a', -1, true);
    return rec.finishRecordingAsPicture();
  }, [w, h, font, bx, by, wx, wy, glyphs]);

  // ---------------------------------------------------------------- touch
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      last.set(clock.get());
      const fg = finger.get();
      const f = field.get();
      // the nearest sample of either ribbon (the white one is on top, so it wins a tie)
      let best = 44;
      let grab = -1;
      let gi = 0;
      for (let i = 0; i < N * 2; i++) {
        const x = (i < N ? bx[i] : wx[i - N]) + f.ox[i];
        const y = (i < N ? by[i] : wy[i - N]) + f.oy[i];
        const d = Math.hypot(x - e.x, y - e.y) - (i >= N ? 2 : 0);
        if (d < best) {
          best = d;
          grab = i < N ? 0 : 1;
          gi = i < N ? i : i - N;
        }
      }
      fg.on = 1;
      fg.x = e.x;
      fg.y = e.y;
      fg.vx = 0;
      if (grab >= 0) {
        fg.grab = grab;
        fg.gi = gi;
        fg.gx = e.x;
        fg.gy = e.y;
        scheduleOnRN(cue, 'stretch');
        scheduleOnRN(buzz, 0);
      } else fg.grab = -1;
    })
    .onUpdate((e) => {
      last.set(clock.get());
      const fg = finger.get();
      fg.x = e.x;
      fg.y = e.y;
      fg.vx = e.velocityX;
    })
    .onFinalize(() => {
      last.set(clock.get());
      const fg = finger.get();
      fg.on = 0;
      fg.vx = 0;
      if (fg.grab >= 0) {
        scheduleOnRN(cue, 'twang');
        scheduleOnRN(buzz, 1);
      }
    });

  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const startUI = () => {
    'worklet';
    last.set(clock.get());
    const rd = road.get();
    if (rd.z >= 0) return;
    rd.z = 0;
    btn.set(1);
    scheduleOnRN(buzz, 2);
    if (!reduced) scheduleOnRN(cue, 'zip');
  };
  const start = () => scheduleOnUI(startUI);
  const btnStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * btn.get() }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  const arrow = useMemo(() => {
    const p = Skia.PathBuilder.Make();
    p.moveTo(5, 11);
    p.lineTo(17, 11);
    p.moveTo(12, 6);
    p.lineTo(17, 11);
    p.lineTo(12, 16);
    return p.build();
  }, []);
  const diag = useMemo(() => {
    const p = Skia.PathBuilder.Make();
    p.moveTo(4, 14);
    p.lineTo(14, 4);
    p.moveTo(6, 4);
    p.lineTo(14, 4);
    p.lineTo(14, 12);
    return p.build();
  }, []);
  const key = useMemo(() => {
    const p = Skia.PathBuilder.Make();
    p.addCircle(8, 12, 4.5);
    p.moveTo(12.5, 12);
    p.lineTo(20, 12);
    p.moveTo(17, 12);
    p.lineTo(17, 15.5);
    p.moveTo(20, 12);
    p.lineTo(20, 15);
    return p.build();
  }, []);

  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible={false}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View style={[styles.head, { top: insets.top + 22 }]} pointerEvents="none" accessible accessibilityRole="header">
        <View style={styles.line}>
          <Text style={styles.headText} allowFontScaling={false}>
            Try new{' '}
          </Text>
          <View style={styles.pill}>
            <Text style={styles.pillText} allowFontScaling={false}>
              social
            </Text>
          </View>
        </View>
        <Text style={styles.headText} allowFontScaling={false}>
          connections with
        </Text>
        <View style={styles.line}>
          <Text style={styles.headText} allowFontScaling={false}>
            our{' '}
          </Text>
          <View style={styles.arrowDot}>
            <Canvas style={styles.icon22}>
              <Path path={arrow} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
            </Canvas>
          </View>
          <Text style={styles.headText} allowFontScaling={false}>
            {' '}assistance
          </Text>
        </View>
      </View>

      <Text style={[styles.copy, { bottom: insets.bottom + 108 }]} allowFontScaling={false}>
        Your personal assistant to enrich your social life
      </Text>
      <View style={[styles.actions, { bottom: insets.bottom + 24 }]}>
        <Animated.View style={[styles.goWrap, btnStyle]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Get started"
            onPress={start}
            style={({ pressed }) => [styles.go, pressed && styles.pressed]}
          >
            <Text style={styles.goText} allowFontScaling={false}>
              Get started
            </Text>
            <Canvas style={styles.icon18}>
              <Path path={diag} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color="#17171a" />
            </Canvas>
          </Pressable>
        </Animated.View>
        <Pressable accessibilityRole="button" accessibilityLabel="Continue with email" style={({ pressed }) => [styles.round, pressed && styles.pressed]}>
          <Text style={styles.at} allowFontScaling={false}>
            @
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Continue with a passkey" style={({ pressed }) => [styles.round, pressed && styles.pressed]}>
          <Canvas style={styles.icon24}>
            <Path path={key} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#ffffff" />
          </Canvas>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#18181b' },
  head: { position: 'absolute', left: 28, right: 28 },
  line: { flexDirection: 'row', alignItems: 'center' },
  headText: { color: '#ffffff', fontSize: 28, lineHeight: 36, fontFamily: 'RRO-SemiBold', letterSpacing: -0.4 },
  pill: { backgroundColor: '#3d34ec', borderRadius: 10, paddingHorizontal: 8, height: 32, justifyContent: 'center' },
  pillText: { color: '#ffffff', fontSize: 28, lineHeight: 34, fontFamily: 'RRO-SemiBold', letterSpacing: -0.4 },
  arrowDot: { width: 30, height: 30, borderRadius: 15, backgroundColor: '#ff6a3d', alignItems: 'center', justifyContent: 'center' },
  icon22: { width: 22, height: 22 },
  icon18: { width: 18, height: 18 },
  icon24: { width: 24, height: 24 },
  copy: { position: 'absolute', left: 88, right: 88, textAlign: 'center', color: 'rgba(255,255,255,0.78)', fontSize: 15, lineHeight: 21, fontFamily: 'RRO-Regular' },
  actions: { position: 'absolute', left: 24, right: 24, height: 56, flexDirection: 'row', gap: 10 },
  goWrap: { flex: 1 },
  go: { flex: 1, borderRadius: 28, backgroundColor: '#ffe14d', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  goText: { color: '#17171a', fontSize: 17, fontFamily: 'RRO-SemiBold' },
  round: { width: 56, height: 56, borderRadius: 28, backgroundColor: '#2a2a2f', alignItems: 'center', justifyContent: 'center' },
  at: { color: '#ffffff', fontSize: 22, lineHeight: 26, fontFamily: 'RRO-SemiBold' },
  pressed: { transform: [{ scale: 0.96 }], opacity: 0.92 },
});

export { LOOP };
