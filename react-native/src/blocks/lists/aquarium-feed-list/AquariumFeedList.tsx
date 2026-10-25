import {
  BlurMask, Canvas, Circle, Fill, Group, LinearGradient, Oval, Path, Rect, Shader, Skia, vec, type SkPath,
} from '@shopify/react-native-skia';
import { LinearGradient as ViewGradient } from 'expo-linear-gradient';
import { memo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue, scrollTo, useAnimatedRef, useAnimatedScrollHandler, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import { LogCard, type Focus } from './AquariumCard';
import {
  CARD_H, ENTRIES, FISH, LOOP, ROW, TAP_AT, TAP_CARD, type Area, autoFeed, autoScroll, autoStir, bubblePaths, easeInOutCubic, fishAt,
  fishBands, fishBody,
} from './aquariumScene';
import { AQUARIUM_LIGHT_SKSL } from './aquariumShader';

/*
 Aquarium Feed List — "Reef Log · Tank 2", a reef keeper's log whose entries float inside the
 tank: glass cards behind which (and in front of which) six fish cruise, tails waving. Scrolling
 stirs the water: the fish scatter away from the scroll and bubbles stream up; caustic light plays
 over everything; the list fades into water at both ends. Tap an entry and the fish come to it.

 autoPlay (LOOP = 9 s): rest 0–1.4 (fish cruising) · scripted scroll down 1.4–4.4 (scatter,
 bubbles) · settle 4.4–5.6 (fish regroup) · tap the fifth card 5.8 (fish gather round it) ·
 scroll back 6.6–8.2 · rest. A touch (drag or tap) takes over at once; 2 s after the last one the
 list scrolls home and the fish swim back to their places (0.9 s), and the loop restarts.
 Reduced motion: no loop, fish hold still, no bubbles; the list scrolls as usual.

 Drawing: water is a view gradient; on it a Canvas with the fish (paths rebuilt on the UI thread
 each frame) and the bubbles; over that the Animated.ScrollView of frosted glass cards, so the fish
 pass behind the glass and never over the text; and on top a second Canvas (pointer events off,
 transparent where nothing falls) with the light — a RuntimeEffect whose alpha is its brightness —
 and the edge fades. A Canvas cannot blend with the views under it, hence two.
*/

type Props = {
  /** Scrolls the log, stirs the fish, taps an entry so they gather, scrolls back. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
const HEADER = 150;
const light = Skia.RuntimeEffect.Make(AQUARIUM_LIGHT_SKSL);
const WATER = ['#2C97A8', '#177587', '#0D5367', '#083A4B'] as const;
/** Draw the far fish first. */
const ORDER = FISH.map((_, i) => i).sort((a, b) => FISH[a].near - FISH[b].near);

type FishPose = { x: number; y: number; rot: number; sx: number; body: SkPath; bands: SkPath };
type Scene = { fish: FishPose[]; rings: SkPath; glints: SkPath; ph: number; stir: number; scroll: number };

/** One fish: a soft shadow, the body (gradient from back to belly), bands, an eye. */
const Fish = memo(function Fish({ index, scene }: { index: number; scene: SharedValue<Scene | null> }) {
  const f = FISH[index];
  const transform = useDerivedValue(() => {
    const p = scene.get()?.fish[index];
    if (!p) return [{ translateX: -200 }];
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rot }, { scaleX: p.sx * f.near }, { scaleY: f.near }];
  });
  const body = useDerivedValue(() => scene.get()?.fish[index].body ?? Skia.Path.Make());
  const bands = useDerivedValue(() => scene.get()?.fish[index].bands ?? Skia.Path.Make());
  return (
    <Group transform={transform} opacity={0.6 + 0.4 * f.near}>
      <Oval x={-f.len * 0.42} y={f.dep * 0.9} width={f.len * 0.9} height={f.dep * 0.5} color="rgba(2,18,26,0.28)">
        <BlurMask blur={6} style="normal" />
      </Oval>
      <Path path={body}>
        <LinearGradient start={vec(0, -f.dep / 2)} end={vec(0, f.dep / 2)} colors={f.colors} positions={[0, 0.5, 1]} />
      </Path>
      {f.bands.length ? <Path path={bands} color="rgba(255,250,240,0.92)" /> : null}
      <Circle cx={f.len * 0.31} cy={-f.dep * 0.06} r={f.dep * 0.11} color="#F4F8F2" />
      <Circle cx={f.len * 0.32} cy={-f.dep * 0.06} r={f.dep * 0.07} color="#0B1A20" />
    </Group>
  );
});

export function AquariumFeedList({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const W = size.width;
  const H = size.height;
  const listTop = insets.top + HEADER;
  const area: Area = { x: 22, y: listTop + 8, w: W - 44, h: H - insets.bottom - 36 - (listTop + 8) };
  const bottomY = H - insets.bottom;

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const lastScroll = useSharedValue(0);
  const dragging = useSharedValue(0);
  const tau = useSharedValue(0);
  const stir = useSharedValue(0);
  const dir = useSharedValue(1);
  const feedAt = useSharedValue(-10);
  const feedCard = useSharedValue(-1);
  const returnAt = useSharedValue(0);
  const returnScroll = useSharedValue(0);
  const scene = useSharedValue<Scene | null>(null);
  const focus = useSharedValue<Focus>({ card: -1, amt: 0, press: 0 });

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    if (mode.get() === MODE_AUTO) {
      // Keep the fish where the loop had them: same fish-time, same stir and feeding.
      const t = clock.get();
      tau.set(t);
      const s = autoStir(t);
      stir.set(s.stir);
      dir.set(s.dir);
      if (t > TAP_AT - 0.2 && t < 7.6) {
        feedAt.set(now.get() - (t - TAP_AT));
        feedCard.set(TAP_CARD);
      } else feedCard.set(-1);
    }
    mode.set(MODE_USER);
  }

  function tapCard(i: number) {
    'worklet';
    touched();
    feedAt.set(now.get());
    feedCard.set(i);
  }

  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (e) => {
      scrollY.set(e.contentOffset.y);
      if (dragging.get()) lastTouch.set(now.get());
    },
    onBeginDrag: () => {
      touched();
      dragging.set(1);
    },
    onEndDrag: () => {
      lastTouch.set(now.get());
    },
    onMomentumEnd: () => {
      dragging.set(0);
      lastTouch.set(now.get());
    },
  });

  /** User feeding: up over 0.8 s from the tap, held, gone 3.9 s after it. */
  function userFeed(at: number) {
    'worklet';
    if (feedCard.get() < 0) return 0;
    const k = at - feedAt.get();
    return easeInOutCubic(k / 0.8) * (1 - easeInOutCubic((k - 3) / 0.9));
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const m = mode.get();
    const sy = scrollY.get();
    const v = (sy - lastScroll.get()) / Math.max(dt, 0.001);
    lastScroll.set(sy);

    let ft = 0;
    let st = 0;
    let dr = 1;
    let fd = 0;
    let card = -1;
    let press = 0;
    let blend = 0;
    let scroll = sy;
    if (m === MODE_AUTO) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      ft = t;
      const s = autoStir(t);
      st = s.stir;
      dr = s.dir;
      fd = autoFeed(t);
      card = TAP_CARD;
      press = Math.sin(Math.PI * Math.min(1, Math.max(0, (t - TAP_AT) / 0.18)));
      scroll = auto ? autoScroll(t) : 0;
      if (auto) scrollTo(scrollRef, 0, scroll, false);
    } else {
      // Under touch the fish keep their own clock and feel the real scroll.
      ft = (tau.get() + (reduced ? 0 : dt)) % LOOP;
      tau.set(ft);
      const target = reduced ? 0 : Math.min(1, Math.abs(v) / 1400);
      const s0 = stir.get();
      st = s0 + (target - s0) * Math.min(1, (target > s0 ? 6 : 1.4) * dt);
      stir.set(st);
      if (Math.abs(v) > 60) dir.set(v > 0 ? 1 : -1);
      dr = dir.get();
      fd = userFeed(at);
      card = feedCard.get();
      press = card >= 0 ? Math.sin(Math.PI * Math.min(1, Math.max(0, (at - feedAt.get()) / 0.18))) : 0;
      if (m === MODE_USER && auto && !dragging.get() && at - lastTouch.get() > IDLE) {
        returnAt.set(at);
        returnScroll.set(sy);
        mode.set(MODE_RETURN);
      }
      if (mode.get() === MODE_RETURN) {
        blend = easeInOutCubic((at - returnAt.get()) / RETURN);
        scroll = returnScroll.get() * (1 - blend);
        scrollTo(scrollRef, 0, scroll, false);
        if (blend >= 1) {
          mode.set(MODE_AUTO);
          clock.set(0);
          stir.set(0);
          feedCard.set(-1);
        }
      }
    }
    focus.set({ card, amt: fd, press });

    // The fed card's centre on screen (fish circle it, and follow it if the list moves).
    const fc = card >= 0 ? card : TAP_CARD;
    const tx = W / 2;
    const ty = listTop + 16 + fc * ROW + CARD_H / 2 - scroll;
    const a = [0, 0];
    const b = [0, 0];
    const r = [0, 0];
    const h = 1 / 60;
    const fish: FishPose[] = [];
    for (let i = 0; i < FISH.length; i++) {
      const f = FISH[i];
      fishAt(i, ft, area, st, dr, fd, tx, ty, a);
      fishAt(i, ft - h, area, st, dr, fd, tx, ty, b);
      let x = a[0];
      let y = a[1];
      let vx = (a[0] - b[0]) / h;
      let vy = (a[1] - b[1]) / h;
      if (blend > 0) {
        fishAt(i, 0, area, 0, 1, 0, tx, ty, r);
        x += (r[0] - x) * blend;
        y += (r[1] - y) * blend;
        fishAt(i, -h, area, 0, 1, 0, tx, ty, b);
        vx += ((r[0] - b[0]) / h - vx) * blend;
        vy += ((r[1] - b[1]) / h - vy) * blend;
      }
      // Facing: the body narrows to edge-on as the fish turns round (tanh of its sideways speed).
      const sx = Math.tanh(vx / 22);
      const rot = Math.max(-0.45, Math.min(0.45, Math.atan2(vy, Math.abs(vx) + 12) * 0.7)) * (vx >= 0 ? 1 : -1);
      const phase = (2 * Math.PI * f.beats * ft) / LOOP;
      const amp = reduced ? 0 : 0.16 + 0.24 * st + 0.1 * fd;
      fish.push({ x, y, rot, sx: Math.abs(sx) < 0.08 ? (sx < 0 ? -0.08 : 0.08) : sx, body: fishBody(f, phase, amp), bands: fishBands(f, phase, amp) });
    }
    const bub = bubblePaths(ft, reduced ? 0 : st, W, listTop, bottomY - 10);
    scene.set({ fish, rings: bub.rings, glints: bub.glints, ph: (2 * Math.PI * ft) / LOOP, stir: st, scroll });
  });

  const uniforms = useDerivedValue(() => {
    const s = scene.get();
    return { size: [W, H], ph: s?.ph ?? 0, stir: s?.stir ?? 0, scroll: s?.scroll ?? 0, listTop, row: ROW, cardH: CARD_H, cards: ENTRIES.length };
  });
  const rings = useDerivedValue(() => scene.get()?.rings ?? Skia.Path.Make());
  const glints = useDerivedValue(() => scene.get()?.glints ?? Skia.Path.Make());

  const onPressCard = (i: number) => scheduleOnUI(tapCard, i);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <ViewGradient colors={WATER} locations={[0, 0.35, 0.7, 1]} style={StyleSheet.absoluteFill} />
      {/* Behind the cards' glass: the fish and the bubbles. */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {ORDER.map((i) => (
          <Fish key={i} index={i} scene={scene} />
        ))}
        <Path path={rings} style="stroke" strokeWidth={1} color="rgba(214,246,255,0.6)" />
        <Path path={rings} color="rgba(190,236,250,0.1)" />
        <Path path={glints} color="rgba(255,255,255,0.75)" />
      </Canvas>
      <Animated.ScrollView
        ref={scrollRef}
        onScroll={scrollHandler}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
        style={StyleSheet.absoluteFill}
        contentContainerStyle={{ paddingTop: listTop + 16, paddingBottom: insets.bottom + 48, paddingHorizontal: 16, gap: ROW - CARD_H }}
      >
        {ENTRIES.map((e, i) => (
          <LogCard key={i} index={i} entry={e} focus={focus} onPress={onPressCard} />
        ))}
      </Animated.ScrollView>

      {/* Over everything: the light (transparent where none falls) and the fades into water. */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {light ? (
          <Fill>
            <Shader source={light} uniforms={uniforms} />
          </Fill>
        ) : null}
        <Rect x={0} y={listTop - 30} width={W} height={70}>
          <LinearGradient start={vec(0, listTop - 30)} end={vec(0, listTop + 40)} colors={['rgba(30,130,149,1)', 'rgba(30,130,149,0)']} />
        </Rect>
        <Rect x={0} y={bottomY - 90} width={W} height={H - bottomY + 90}>
          <LinearGradient start={vec(0, bottomY - 90)} end={vec(0, H)} colors={['rgba(8,58,75,0)', 'rgba(8,58,75,0.9)', 'rgba(8,58,75,1)']} positions={[0, 0.7, 1]} />
        </Rect>
      </Canvas>

      <View pointerEvents="box-none" style={[styles.header, { paddingTop: insets.top + 14, height: listTop - 18 }]}>
        <ViewGradient pointerEvents="none" colors={['#2C97A8', '#1F8497', 'rgba(30,130,149,0)']} locations={[0, 0.75, 1]} style={StyleSheet.absoluteFill} />
        <Text style={styles.kicker}>REEF LOG</Text>
        <Text style={styles.title}>Tank 2</Text>
        <Text style={styles.subtitle}>220 L mixed reef · 6 fish · 14 corals</Text>
        <View style={styles.stats}>
          {['25.6 °C', 'pH 8.2', 'SG 1.025', 'NO₃ 5 ppm'].map((s) => (
            <Text key={s} style={styles.stat}>{s}</Text>
          ))}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: WATER[2] },
  header: { position: 'absolute', left: 0, right: 0, top: 0, paddingHorizontal: 22, gap: 3 },
  kicker: { color: '#8FE3F0', fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  title: { color: '#EAF9FB', fontSize: 34, fontWeight: '800', letterSpacing: -0.6 },
  subtitle: { color: 'rgba(222,246,250,0.66)', fontSize: 15, fontWeight: '500' },
  stats: { flexDirection: 'row', gap: 8, marginTop: 10 },
  stat: {
    color: '#DDF6FA',
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: 'rgba(190,240,250,0.12)',
  },
});
