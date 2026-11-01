import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CARD, CLEAR, DROP, type Hand, handPose, LOOP, makeHand, N, PASS, script, springTo } from './tfc-motion';
import { type Cue, useTfcAudio } from './use-tfc-audio';

/*
 Trophy Fan Case — the trophy case of an invented running app.

 Look (the pin, a still): "TROPHY CASE" over a black page that burns to orange at the bottom, with
 grain; four dark cards fanned like a hand of playing cards, each with a glossy diamond badge (an
 orange PR medal, a black trophy with a 3), the trophy's name and a big time. The pin's badges carry
 the app's own mark; ours were drawn for this block and carry none.

 Motion (ours): the cards start as a deck and fan out about a pivot far below the screen, like a
 hand being opened. Run a finger along the hand and the card under it lifts. Lift a card out (drag
 it up, or tap it) and it flies to the middle, grows and turns over to show the run behind the
 trophy: pace, splits, the previous best. Tap it or pull it down and it turns back and slides into
 its place in the hand. Every card is a spring on x, y, rotation, scale and flip, so any of this can
 be grabbed mid-flight and reversed.

 autoPlay: LOOP = 8 s (marks in tfc-motion.ts): deck → 0.45 s fan → 1.5-2.6 s a finger runs along the
 hand → 2.85 s card 2 lifts and turns over → 5.25 s back into the hand → 6.45 s the hand closes.
 Frame 8 s equals frame 0. Reduced motion: the hand stays fanned, a chosen card turns without flying.
*/

let debugState = '';

const TROPHIES = [
  {
    badge: require('./assets/badges/badge-pr.png'),
    title: 'Half marathon PR',
    time: '1:28:14',
    when: 'Apr 12 · Riverside loop',
    pace: '4:11',
    splits: [0.82, 0.86, 0.9, 0.88, 0.95],
    best: 'Previous best 1:31:02',
    delta: '−2:48',
  },
  {
    badge: require('./assets/badges/badge-third.png'),
    title: '3rd fastest 10 mile',
    time: '1:17:55',
    when: 'May 3 · Harbour 10',
    pace: '4:50',
    splits: [0.88, 0.84, 0.86, 0.8, 0.9],
    best: 'Your fastest 1:14:20',
    delta: '+3:35',
  },
  {
    badge: require('./assets/badges/badge-streak.png'),
    title: '2026 longest streak',
    time: '41 days',
    when: 'Feb 2 – Mar 14',
    pace: '5:24',
    splits: [0.6, 0.72, 0.68, 0.8, 0.76],
    best: 'Previous streak 27 days',
    delta: '+14',
  },
  {
    badge: require('./assets/badges/badge-second.png'),
    title: '2nd fastest 10K',
    time: '45:09',
    when: 'Jun 21 · Night 10K',
    pace: '4:30',
    splits: [0.9, 0.92, 0.88, 0.94, 0.97],
    best: 'Your fastest 44:41',
    delta: '+0:28',
  },
];

// black at the top burning to orange at the bottom, with film grain
const BG = Skia.RuntimeEffect.Make(`
uniform float2 size;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float2 uv = xy / size;
  float glow = exp(-pow(length((uv - float2(0.62, 1.08)) * float2(1.1, 1.6)), 2.0) * 2.4);
  float3 c = mix(float3(0.0), float3(0.22, 0.05, 0.01), smoothstep(0.3, 0.75, uv.y));
  c = mix(c, float3(0.86, 0.33, 0.07), glow * 0.9);
  c += float3(1.0, 0.75, 0.45) * pow(glow, 6.0) * 0.5;
  float g = hash(floor(xy * 1.0)) - 0.5;
  c += g * (0.025 + 0.05 * glow);
  return half4(half3(max(c, 0.0)), 1.0);
}`);

type Props = { autoPlay?: boolean };

export function TrophyFanCase({ autoPlay = false }: Props) {
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
    'TFC-Regular': require('./assets/fonts/Archivo-Regular.ttf'),
    'TFC-Medium': require('./assets/fonts/Archivo-Medium.ttf'),
    'TFC-SemiBold': require('./assets/fonts/Archivo-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const FY = insets.top + 352; // the hand's resting line (card centres)
  const SY = insets.top + 336; // a chosen card's centre
  const BIG = Math.min(1.55, (w - 56) / CARD.w);

  const play = useTfcAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const hand = useSharedValue<Hand>(makeHand());
  const tick = useSharedValue(0);
  const want = useSharedValue({ spread: reduced ? 1 : 0, focus: -9, focusOn: 0, sel: -1 });
  const touch = useSharedValue({ startX: 0, startY: 0, at: 0, card: -1, mode: 0, detent: -9 }); // mode 0 scrub, 1 holding a card
  const debugT = useSharedValue(-1);
  const [top, setTop] = useState(-1); // the card drawn above the others

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __trophyFan?: unknown };
    g.__trophyFan = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__trophyFan;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** One physics step of the whole hand toward the wanted state. */
  const stepHand = (hd: Hand, dt: number, wnt: { spread: number; focus: number; focusOn: number; sel: number }) => {
    'worklet';
    let r = springTo(hd.spread, hd.vspread, wnt.spread, 90, 0.85, dt);
    hd.spread = r[0];
    hd.vspread = r[1];
    if (wnt.focusOn > 0 && hd.focusOn < 0.02) {
      hd.focus = wnt.focus;
      hd.vfocus = 0;
    }
    if (wnt.focusOn > 0) {
      r = springTo(hd.focus, hd.vfocus, wnt.focus, 260, 1, dt);
      hd.focus = r[0];
      hd.vfocus = r[1];
    }
    r = springTo(hd.focusOn, hd.vfocusOn, wnt.focusOn, 220, 0.9, dt);
    hd.focusOn = r[0];
    hd.vfocusOn = r[1];
    hd.sel = wnt.sel;
    // the hand makes room while a card is out of it
    let away = hd.sel >= 0 || hd.held >= 0;
    for (let i = 0; i < N; i++) {
      const ph = hd.cards[i].ph;
      if (ph === 1 || ph === 2 || ph === 3) away = true;
    }
    for (let i = 0; i < N; i++) {
      const b = hd.cards[i];
      const home = handPose(i, hd.spread, hd.focus, hd.focusOn, cx, FY);
      // a card leaves the hand by rising out from behind the cards in front of it, and comes back
      // over the top of its slot before sliding down into it, so the drawing order never pops
      if (hd.held === i) {
        if (b.ph !== 2) b.ph = b.y < home.y - PASS ? 2 : 1;
        const nx = hd.hx;
        const ny = hd.hy;
        b.vx = dt > 0 ? b.vx * 0.5 + ((nx - b.x) / dt) * 0.5 : b.vx;
        b.vy = dt > 0 ? b.vy * 0.5 + ((ny - b.y) / dt) * 0.5 : b.vy;
        b.x = nx;
        b.y = ny;
        const rr = springTo(b.r, b.vr, Math.max(-14, Math.min(14, b.vx * 0.012)), 300, 0.8, dt);
        b.r = rr[0];
        b.vr = rr[1];
        const rs = springTo(b.s, b.vs, 1.08, 300, 0.8, dt);
        b.s = rs[0];
        b.vs = rs[1];
        continue;
      }
      if (hd.sel === i) {
        if (b.ph === 0 || b.ph === 4) b.ph = 1;
        else if (b.ph === 3) b.ph = 2;
      } else if (b.ph === 2) b.ph = 3;
      else if (b.ph === 1) b.ph = 0;
      if (b.ph === 1 && b.y < home.y - PASS) b.ph = 2;
      if (b.ph === 3 && Math.abs(b.x - home.x) < 24 && Math.abs(b.f) < 25 && b.y < home.y - PASS) b.ph = 4;
      if (b.ph === 4 && Math.hypot(b.x - home.x, b.y - home.y) < 3) b.ph = 0;

      let tx = home.x;
      let ty = home.y;
      let tr = home.r;
      let ts = home.s;
      let tf = 0;
      let k = 150;
      if (b.ph === 1 || b.ph === 3) {
        ty = home.y - CLEAR;
        tr = home.r * (b.ph === 1 ? 0.4 : 1);
        ts = 1.04;
        k = 170;
      } else if (b.ph === 2) {
        tx = cx;
        ty = SY;
        tr = 0;
        ts = BIG;
        k = 120;
        // turn over once it is nearly there
        tf = Math.hypot(b.x - cx, b.y - SY) < 70 ? 180 : 0;
      } else if (away) {
        ty += DROP;
        ts *= 0.92;
      }
      r = springTo(b.x, b.vx, tx, k, 0.82, dt);
      b.x = r[0];
      b.vx = r[1];
      r = springTo(b.y, b.vy, ty, k, 0.82, dt);
      b.y = r[0];
      b.vy = r[1];
      r = springTo(b.r, b.vr, tr, k, 0.82, dt);
      b.r = r[0];
      b.vr = r[1];
      r = springTo(b.s, b.vs, ts, k, 0.82, dt);
      b.s = r[0];
      b.vs = r[1];
      r = springTo(b.f, b.vf, tf, 110, 0.86, dt);
      b.f = r[0];
      b.vf = r[1];
    }
  };

  /** A fresh hand placed at rest in the deck (or in the fan under reduced motion). */
  const settle = (hd: Hand) => {
    'worklet';
    const sp = reduced ? 1 : 0;
    hd.spread = sp;
    for (let i = 0; i < N; i++) {
      const p = handPose(i, sp, -9, 0, cx, FY);
      const b = hd.cards[i];
      b.x = p.x;
      b.y = p.y;
      b.r = p.r;
      b.s = p.s;
      b.f = 0;
    }
  };

  const lastTop = useSharedValue(-1);
  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    let hd = hand.get();
    if (hd.cards[0].x === 0) settle(hd);
    let t = time.get();
    if (dbg >= 0) {
      // replay the script from the start, so a pinned frame is what the loop shows
      const sim = makeHand();
      settle(sim);
      let st = 0;
      while (st < dbg) {
        const d = Math.min(1 / 120, dbg - st);
        stepHand(sim, d, script(st));
        st += d;
      }
      hd = sim;
      hand.set(sim);
      t = dbg;
      debugT.set(-2); // replayed once; hold until the next pin or play
    } else if (dbg === -2) {
      // pinned: hold
    } else {
      const touched = hd.held >= 0 || now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
        const sc = script(t);
        want.set(sc);
      }
      stepHand(hd, dt, want.get());
    }
    time.set(t);
    // the card drawn above the others: the one free above the hand or going back over it
    let tp = -1;
    for (let i = 0; i < N; i++) {
      const ph = hd.cards[i].ph;
      if (ph === 2 || ph === 3) tp = i;
    }
    if (tp !== lastTop.get()) {
      lastTop.set(tp);
      scheduleOnRN(setTop, tp);
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} spread=${hd.spread.toFixed(2)} sel=${hd.sel} f1=${hd.cards[1].f.toFixed(0)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ---------------------------------------------------------------- touch
  /** The topmost card under a point (selected first, then the higher index), -1 none. */
  const cardAt = (hd: Hand, x: number, y: number) => {
    'worklet';
    const order = [3, 2, 1, 0];
    const lt = lastTop.get();
    if (lt >= 0) order.unshift(lt);
    for (let n = 0; n < order.length; n++) {
      const i = order[n];
      const b = hd.cards[i];
      const a = (-b.r * Math.PI) / 180;
      const dx = x - b.x;
      const dy = y - b.y;
      const lx = dx * Math.cos(a) - dy * Math.sin(a);
      const ly = dx * Math.sin(a) + dy * Math.cos(a);
      if (Math.abs(lx) < (CARD.w / 2) * b.s && Math.abs(ly) < (CARD.h / 2) * b.s) return i;
    }
    return -1;
  };
  const choose = (i: number) => {
    'worklet';
    const wt = want.get();
    want.set({ ...wt, spread: 1, focusOn: 0, sel: i });
  };
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const hd = hand.get();
      const now = clock.get();
      last.set(now);
      const i = cardAt(hd, e.x, e.y);
      touch.set({ startX: e.x, startY: e.y, at: now, card: i, mode: 0, detent: -9 });
      // stop the script where it is; keep the hand as it looks
      const wt = want.get();
      want.set({ ...wt, spread: hd.spread > 0.5 || hd.sel >= 0 ? 1 : 0 });
    })
    .onUpdate((e) => {
      const hd = hand.get();
      const tc = touch.get();
      last.set(clock.get());
      if (hd.sel >= 0) {
        // a chosen card follows a downward pull
        if (tc.card === hd.sel && e.translationY > 0) {
          const b = hd.cards[hd.sel];
          b.y = SY + e.translationY * 0.6;
        }
        return;
      }
      if (tc.mode === 1) {
        hd.hx = e.x;
        hd.hy = e.y;
        return;
      }
      // scrub along the hand
      const wt = want.get();
      if (wt.spread < 0.5) {
        want.set({ ...wt, spread: 1 });
        scheduleOnRN(cue, 'fan');
        return;
      }
      const f = Math.max(-0.4, Math.min(N - 1 + 0.4, ((e.x - cx) / 100) + (N - 1) / 2));
      want.set({ ...wt, focus: f, focusOn: 1 });
      const d = Math.round(Math.max(0, Math.min(N - 1, f)));
      if (d !== tc.detent) {
        touch.set({ ...tc, detent: d });
        scheduleOnRN(cue, 'tick');
        scheduleOnRN(buzz, 0);
      }
      // pulled up out of the hand: hold that card
      if (e.translationY < -36 && tc.card >= 0) {
        hd.held = d;
        hd.hx = e.x;
        hd.hy = e.y;
        touch.set({ ...tc, mode: 1, card: d, detent: d });
        want.set({ ...wt, focusOn: 0 });
        scheduleOnRN(cue, 'lift');
        scheduleOnRN(buzz, 1);
      }
    })
    .onFinalize((e) => {
      const hd = hand.get();
      const tc = touch.get();
      const now = clock.get();
      last.set(now);
      const wt = want.get();
      const tap = Math.hypot(e.translationX, e.translationY) < 8 && now - tc.at < 0.35;
      if (hd.sel >= 0) {
        if (tap || e.translationY > 60 || e.velocityY > 700) {
          want.set({ ...wt, sel: -1 });
          scheduleOnRN(cue, 'flip');
          scheduleOnRN(buzz, 1);
        }
        return;
      }
      if (tc.mode === 1) {
        const i = hd.held;
        hd.held = -1;
        const b = hd.cards[i];
        if (b.ph === 1) b.ph = 0;
        b.vx = e.velocityX;
        b.vy = e.velocityY;
        if (e.translationY < -90 || e.velocityY < -600) {
          choose(i);
          scheduleOnRN(cue, 'flip');
          scheduleOnRN(buzz, 2);
        } else want.set({ ...wt, focusOn: 0 });
        return;
      }
      if (tap) {
        if (wt.spread < 0.5) {
          want.set({ ...wt, spread: 1 });
          scheduleOnRN(cue, 'fan');
          scheduleOnRN(buzz, 1);
        } else if (tc.card >= 0) {
          choose(tc.card);
          scheduleOnRN(cue, 'lift');
          scheduleOnRN(buzz, 2);
        }
        return;
      }
      want.set({ ...wt, focusOn: 0 });
    });

  const openCard = (i: number) => {
    last.set(clock.get());
    const wt = want.get();
    want.set({ ...wt, spread: 1, focusOn: 0, sel: wt.sel === i ? -1 : i });
    buzz(1);
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const order = [0, 1, 2, 3];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size && BG ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Fill>
            <Shader source={BG} uniforms={{ size: [w, h] }} />
          </Fill>
        </Canvas>
      ) : null}
      <Text style={[styles.kicker, { top: insets.top + 64 }]} allowFontScaling={false}>
        TROPHY CASE
      </Text>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {/* two layers: a card turning in 3D is flattened into its own layer, so it never cuts through the hand */}
          <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
            {order.map((i) =>
              i === top ? null : <TrophyCard key={i} i={i} hand={hand} tick={tick} onOpen={openCard} />,
            )}
          </View>
          <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
            {top >= 0 ? <TrophyCard key={top} i={top} hand={hand} tick={tick} onOpen={openCard} /> : null}
          </View>
        </View>
      </GestureDetector>
      <Text style={[styles.foot, { bottom: insets.bottom + 28 }]} allowFontScaling={false}>
        4 of 12 trophies · 2026 season
      </Text>
    </View>
  );
}

function TrophyCard({
  i,
  hand,
  tick,
  onOpen,
}: {
  i: number;
  hand: SharedValue<Hand>;
  tick: SharedValue<number>;
  onOpen: (i: number) => void;
}) {
  const d = TROPHIES[i];
  const place = useAnimatedStyle(() => {
    tick.get();
    const hd = hand.get();
    const b = hd.cards[i];
    const dim = hd.sel >= 0 && hd.sel !== i ? 0.55 : 1;
    return {
      opacity: dim,
      transform: [
        { translateX: b.x - CARD.w / 2 },
        { translateY: b.y - CARD.h / 2 },
        { rotateZ: `${b.r}deg` },
        // the turn is drawn flat (width = |cos|, a slight skew for depth): a real rotateY puts the card
        // in the same 3D context as the hand on iOS and it cuts through the cards behind it
        { scaleX: b.s * Math.max(0.02, Math.abs(Math.cos((b.f * Math.PI) / 180))) },
        { scaleY: b.s * (1 + 0.06 * Math.abs(Math.sin((b.f * Math.PI) / 180))) },
        { skewY: `${Math.sin((b.f * Math.PI) / 90) * -6}deg` },
        // a thrown card lags at its top like stiff card stock
        { skewX: `${Math.max(-7, Math.min(7, -b.vx * 0.006))}deg` },
      ],
    };
  });
  const front = useAnimatedStyle(() => {
    tick.get();
    const f = hand.get().cards[i].f;
    return { opacity: f < 90 ? 1 : 0 };
  });
  const backSide = useAnimatedStyle(() => {
    tick.get();
    const f = hand.get().cards[i].f;
    return { opacity: f >= 90 ? 1 : 0 };
  });
  const shade = useAnimatedStyle(() => {
    tick.get();
    const f = hand.get().cards[i].f;
    return { opacity: Math.abs(Math.sin((f * Math.PI) / 180)) * 0.55 };
  });
  const top = Math.max(...d.splits);
  return (
    <Animated.View
      style={[styles.card, place]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`${d.title}, ${d.time}`}
      accessibilityHint="Turns the card over"
      onAccessibilityTap={() => onOpen(i)}
    >
      <Animated.View style={[styles.face, front]}>
        <Image source={d.badge} style={styles.badge} contentFit="contain" />
        <View style={styles.copy}>
          <Text style={styles.title} numberOfLines={2} allowFontScaling={false}>
            {d.title}
          </Text>
          <Text style={styles.time} allowFontScaling={false}>
            {d.time}
          </Text>
        </View>
      </Animated.View>
      <Animated.View style={[styles.face, styles.back, backSide]}>
        <Text style={styles.backTitle} numberOfLines={1} allowFontScaling={false}>
          {d.title}
        </Text>
        <Text style={styles.backWhen} allowFontScaling={false}>
          {d.when}
        </Text>
        <View style={styles.paceRow}>
          <Text style={styles.pace} allowFontScaling={false}>
            {d.pace}
          </Text>
          <Text style={styles.paceUnit} allowFontScaling={false}>
            /km
          </Text>
        </View>
        <View style={styles.splits}>
          {d.splits.map((s, k) => (
            <View key={k} style={[styles.split, { height: 6 + 28 * (s / top) }, s === top && styles.splitTop]} />
          ))}
        </View>
        <Text style={styles.best} allowFontScaling={false}>
          {d.best}
        </Text>
        <Text style={styles.delta} allowFontScaling={false}>
          {d.delta}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.shade, shade]} pointerEvents="none" />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  kicker: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#f2f2f2', fontSize: 13, letterSpacing: 1.6, fontFamily: 'TFC-Medium' },
  foot: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,235,220,0.7)', fontSize: 13, fontFamily: 'TFC-Regular' },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD.w,
    height: CARD.h,
    shadowColor: '#000',
    shadowOpacity: 0.55,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
  },
  face: {
    position: 'absolute', left: 0, top: 0, right: 0, bottom: 0,
    borderRadius: 20,
    backgroundColor: '#15161a',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
    overflow: 'hidden',
    padding: 14,
  },
  badge: { width: 88, height: 88, marginLeft: -6, marginTop: -2 },
  copy: { position: 'absolute', left: 14, right: 12, bottom: 18 },
  title: { color: '#ffffff', fontSize: 14, lineHeight: 18, fontFamily: 'TFC-SemiBold' },
  time: { color: '#ffffff', fontSize: 28, lineHeight: 34, marginTop: 4, fontFamily: 'TFC-Medium', letterSpacing: -0.5, fontVariant: ['tabular-nums'] },
  back: { backgroundColor: '#1a1310', borderColor: 'rgba(255,120,50,0.35)' },
  backTitle: { color: '#ffffff', fontSize: 13, lineHeight: 17, fontFamily: 'TFC-SemiBold' },
  backWhen: { color: 'rgba(255,255,255,0.55)', fontSize: 10, lineHeight: 14, fontFamily: 'TFC-Regular', marginTop: 2 },
  paceRow: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 14, gap: 3 },
  pace: { color: '#ffffff', fontSize: 30, lineHeight: 34, fontFamily: 'TFC-Medium', fontVariant: ['tabular-nums'] },
  paceUnit: { color: 'rgba(255,255,255,0.6)', fontSize: 12, lineHeight: 20, fontFamily: 'TFC-Regular' },
  splits: { flexDirection: 'row', alignItems: 'flex-end', gap: 5, height: 36, marginTop: 14 },
  split: { flex: 1, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.22)' },
  splitTop: { backgroundColor: '#ff6a1f' },
  shade: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: 20, backgroundColor: '#000000' },
  best: { color: 'rgba(255,255,255,0.6)', fontSize: 10, lineHeight: 14, fontFamily: 'TFC-Regular', marginTop: 14 },
  delta: { color: '#ff7a33', fontSize: 16, lineHeight: 20, fontFamily: 'TFC-SemiBold', marginTop: 2 },
});

export { LOOP };
