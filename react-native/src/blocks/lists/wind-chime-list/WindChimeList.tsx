import { Canvas, Picture, Skia, TileMode, vec } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useMemo, useState } from 'react';
import { type ListRenderItemInfo, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Extrapolation,
  interpolate,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  LOOP,
  PLAY_LEN,
  RING_LEN,
  ROW_DELAY,
  ROW_H,
  SESSIONS,
  STRIKE_AT,
  STRIKE_ROW,
  type Session,
  TOUCH,
  easeInOutCubic,
  hash,
  ringAt,
  rowDir,
  rowOmega,
  scrollAt,
  swingAt,
} from './windChimeMotion';
import { ChimeRow, ListHeader } from './ChimeRow';

/*
 Wind Chime List — a sound-bath app's sessions as a hanging wind chime: every row is a brass or
 bamboo tube on two strings, hanging from the one above, the first from an oak beam under the
 title. Scrolling swings them: each tube is a damped pendulum driven by the list's acceleration,
 40 ms later per row and at its own pitch (shorter tubes swing faster), neighbours in opposite
 directions; when two swing far enough apart they touch and a ring of light spreads from the
 point of contact. Tapping a tube strikes it: it swings hard and its waveform plays.

 Views only (rows are memoised; per frame only transforms and opacities change, from two shared
 arrays the frame callback writes), and one static Skia Canvas behind the list for the grain.
 The large title scrolls away and a blurred compact bar fades in; both edges fade.
*/

type Props = {
  /** Flings the list down, strikes a tube, scrolls back, every LOOP seconds. */
  autoPlay?: boolean;
};

const BG = ['#13262a', '#0f1d20', '#0a1315'] as const;
const COUNT = SESSIONS.length;
const IDLE = 2;
const RETURN_LEN = 0.8;
/** How strongly the list's acceleration (pt/s²) kicks a tube (rad/s² per pt/s²). */
const GAIN = 0.0018;
const MAX_SWING = 0.6;

const AnimatedFlatList = Animated.FlatList<Session>;

type Sim = { th: number[]; w: number[]; touchAt: number[]; was: number[]; side: number[]; hist: number[]; histT: number[]; lastY: number; vs: number };

function createSim(): Sim {
  'worklet';
  return {
    th: new Array(COUNT).fill(0),
    w: new Array(COUNT).fill(0),
    touchAt: new Array(COUNT).fill(-9),
    was: new Array(COUNT).fill(0),
    side: new Array(COUNT).fill(1),
    hist: [],
    histT: [],
    lastY: 0,
    vs: 0,
  };
}

/** The list's acceleration `delay` seconds ago, from the sampled history. */
function accelAgo(s: Sim, now: number, delay: number) {
  'worklet';
  const want = now - delay;
  for (let k = s.histT.length - 1; k >= 0; k -= 1) if (s.histT[k] <= want) return s.hist[k];
  return 0;
}

function soft(x: number) {
  'worklet';
  return MAX_SWING * Math.tanh(x / MAX_SWING);
}

function tick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function makeGrain(W: number, H: number) {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, W, H));
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient(vec(W * 0.5, H * 0.12), W * 0.9, [Skia.Color('rgba(120,190,180,0.14)'), Skia.Color('rgba(120,190,180,0.03)'), Skia.Color('rgba(0,0,0,0)')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), glow);
  const dot = Skia.Paint();
  dot.setColor(Skia.Color('rgba(220,240,235,0.03)'));
  for (let i = 0; i < 1500; i += 1) c.drawCircle(hash(i * 1.3) * W, hash(i * 2.9 + 4) * H, 0.6, dot);
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient(vec(W / 2, H * 0.45), H * 0.75, [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.2)'), Skia.Color('rgba(0,0,0,0.55)')], [0.45, 0.78, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), vig);
  return rec.finishRecordingAsPicture();
}

export function WindChimeList({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const grain = useMemo(() => makeGrain(W, H), [W, H]);
  const listRef = useAnimatedRef<Animated.FlatList<Session>>();

  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const dragging = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const sim = useSharedValue<Sim | null>(null);
  /** Per row: swing angle (rad). Per row i: [seconds since it touched row i + 1 (−1 none), side]. */
  const swing = useSharedValue<number[]>(new Array(COUNT).fill(0));
  const rings = useSharedValue<number[]>(new Array(COUNT * 2).fill(-1));
  /** The tube playing: [row, seconds since its strike] (row −1: none). */
  const play = useSharedValue<number[]>([-1, 0]);
  const strikeAt = useSharedValue(-9);
  const strikeRow = useSharedValue(-1);
  const [playingRow, setPlayingRow] = useState(-1);

  /** Leaves autoPlay: the oscillators start from the loop's current angles and speeds. */
  function takeOver() {
    'worklet';
    lastTouch.set(now.get());
    if (mode.get() === 1) return;
    const s = createSim();
    const t = clock.get();
    for (let i = 0; i < COUNT; i += 1) {
      s.th[i] = mode.get() === 0 && auto ? swingAt(t, i) : swing.get()[i];
      s.w[i] = mode.get() === 0 && auto ? (swingAt(t, i) - swingAt(Math.max(0, t - 1 / 120), i)) * 120 : 0;
    }
    s.lastY = scrollY.get();
    sim.set(s);
    if (mode.get() === 0 && auto && strikeRow.get() < 0 && t > STRIKE_AT && t < STRIKE_AT + PLAY_LEN) {
      strikeRow.set(STRIKE_ROW);
      strikeAt.set(now.get() - (t - STRIKE_AT));
    }
    mode.set(1);
  }

  function strike(i: number) {
    'worklet';
    takeOver();
    const s = sim.get();
    if (!s) return;
    if (!reduced) s.w[i] += rowDir(i) * 0.32 * rowOmega(i);
    sim.set(s);
    strikeRow.set(i);
    strikeAt.set(now.get());
    scheduleOnRN(tick);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const n = now.get() + dt;
    now.set(n);
    const m = mode.get();
    const th = new Array(COUNT).fill(0);
    const rg = new Array(COUNT * 2).fill(-1);
    if (m === 0) {
      if (!auto) return;
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      scrollTo(listRef, 0, scrollAt(t), false);
      for (let i = 0; i < COUNT; i += 1) th[i] = swingAt(t, i);
      for (let i = 0; i < COUNT - 1; i += 1) {
        const r = ringAt(t, i);
        rg[i * 2] = r[0];
        rg[i * 2 + 1] = r[1];
      }
      swing.set(th);
      rings.set(rg);
      play.set(t >= STRIKE_AT && t < STRIKE_AT + PLAY_LEN ? [STRIKE_ROW, t - STRIKE_AT] : [-1, 0]);
      return;
    }
    const s = sim.get();
    if (!s) return;
    // The list's speed and acceleration, smoothed, with a short history for the per-row delay.
    const y = scrollY.get();
    const v = (y - s.lastY) / Math.max(dt, 1e-3);
    s.lastY = y;
    const vs = s.vs + (v - s.vs) * 0.35;
    const a = Math.max(-9000, Math.min(9000, (vs - s.vs) / Math.max(dt, 1e-3)));
    s.vs = vs;
    s.hist.push(a);
    s.histT.push(n);
    while (s.histT.length > 0 && s.histT[0] < n - COUNT * ROW_DELAY - 0.1) {
      s.hist.shift();
      s.histT.shift();
    }
    const zeta = m === 2 ? 0.7 : 0.22;
    let energy = 0;
    for (let i = 0; i < COUNT; i += 1) {
      const w0 = rowOmega(i);
      const z = i === strikeRow.get() && n - strikeAt.get() < PLAY_LEN && m === 1 ? 0.12 : zeta;
      const acc = -w0 * w0 * s.th[i] - 2 * z * w0 * s.w[i] + GAIN * rowDir(i) * accelAgo(s, n, i * ROW_DELAY) * (m === 2 || reduced ? 0 : 1);
      s.w[i] += acc * dt;
      s.th[i] += s.w[i] * dt;
      th[i] = soft(s.th[i]);
      energy = Math.max(energy, Math.abs(s.th[i]) + Math.abs(s.w[i]) * 0.1);
    }
    for (let i = 0; i < COUNT - 1; i += 1) {
      const d = th[i] - th[i + 1];
      const touching = Math.abs(d) > TOUCH ? 1 : 0;
      if (touching === 1 && s.was[i] === 0) {
        s.touchAt[i] = n;
        s.side[i] = d > 0 ? 1 : -1;
      }
      s.was[i] = touching;
      const age = n - s.touchAt[i];
      rg[i * 2] = age <= RING_LEN ? age : -1;
      rg[i * 2 + 1] = s.side[i];
    }
    sim.set(s);
    swing.set(th);
    rings.set(rg);
    const sr = strikeRow.get();
    play.set(sr >= 0 && n - strikeAt.get() < PLAY_LEN ? [sr, n - strikeAt.get()] : [-1, 0]);

    if (m === 1 && dragging.get() === 0 && n - lastTouch.get() > IDLE) {
      mode.set(2);
      returnAt.set(n);
      returnFrom.set(y);
      strikeRow.set(-1);
    }
    if (m === 2) {
      const u = Math.min(1, (n - returnAt.get()) / RETURN_LEN);
      scrollTo(listRef, 0, returnFrom.get() * (1 - easeInOutCubic(u)), false);
      if (u >= 1 && energy < 0.01) {
        clock.set(0);
        mode.set(0);
      }
    }
  });

  // Mount the animated waveform only on the row that plays (changes twice a loop at most).
  useAnimatedReaction(
    () => play.get()[0],
    (row, prev) => {
      if (row !== prev) scheduleOnRN(setPlayingRow, row);
    },
  );

  const onScroll = useAnimatedScrollHandler({
    onScroll: (e) => {
      scrollY.set(e.contentOffset.y);
    },
    onBeginDrag: () => {
      dragging.set(1);
      takeOver();
    },
    onEndDrag: () => {
      dragging.set(0);
      lastTouch.set(now.get());
    },
    onMomentumEnd: () => {
      lastTouch.set(now.get());
    },
  });

  // `strike` reads only shared values, `auto` and `reduced`; keeping this stable keeps the memoised rows still.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const onStrike = useCallback((i: number) => scheduleOnUI(strike, i), [auto, reduced]);

  const headerH = insets.top + 168;
  const renderItem = useCallback(
    ({ item, index }: ListRenderItemInfo<Session>) => (
      <ChimeRow item={item} index={index} W={W} swing={swing} rings={rings} play={play} playing={playingRow === index} onStrike={onStrike} />
    ),
    [W, onStrike, play, playingRow, rings, swing],
  );
  const header = useMemo(() => <ListHeader insetTop={insets.top} W={W} scrollY={scrollY} />, [W, insets.top, scrollY]);

  const barStyle = useAnimatedStyle(() => ({ opacity: interpolate(scrollY.get(), [60, 110], [0, 1], Extrapolation.CLAMP) }));

  return (
    <View style={[styles.root, { backgroundColor: BG[1] }]}>
      <LinearGradient colors={BG} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={grain} />
      </Canvas>
      <AnimatedFlatList
        ref={listRef}
        data={SESSIONS}
        renderItem={renderItem}
        keyExtractor={keyOf}
        extraData={playingRow}
        getItemLayout={layoutOf(headerH)}
        ListHeaderComponent={header}
        onScroll={onScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: insets.bottom + 72 }}
        initialNumToRender={10}
      />
      <LinearGradient pointerEvents="none" colors={['rgba(10,19,21,0)', 'rgba(10,19,21,0.92)']} style={[styles.fadeBottom, { height: 90 + insets.bottom }]} />
      <Animated.View pointerEvents="none" style={[styles.topBar, { height: insets.top + 48 }, barStyle]}>
        <BlurView intensity={40} tint="dark" style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(15,29,32,0.55)' }]} />
        <Text style={[styles.barTitle, { marginTop: insets.top }]}>Sound baths</Text>
        <View style={styles.barLine} />
      </Animated.View>
      <LinearGradient pointerEvents="none" colors={['rgba(15,29,32,0.9)', 'rgba(15,29,32,0)']} style={[styles.fadeTop, { top: 0, height: insets.top + 12 }]} />
    </View>
  );
}

function keyOf(item: Session) {
  return item.title;
}

function layoutOf(headerH: number) {
  return (_: ArrayLike<Session> | null | undefined, index: number) => ({ length: ROW_H, offset: headerH + ROW_H * index, index });
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  fadeBottom: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  fadeTop: { position: 'absolute', left: 0, right: 0 },
  topBar: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  barTitle: { color: '#eef3ee', fontSize: 17, fontWeight: '600', textAlign: 'center', lineHeight: 48 },
  barLine: { position: 'absolute', left: 0, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(238,243,238,0.15)' },
});
