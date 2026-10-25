import {
  BlurMask, Canvas, Circle, FilterMode, Group, Image, ImageShader, LinearGradient, MipmapMode, RadialGradient, Rect, RoundedRect, Shader, Skia, vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing, FadeInDown, FadeOutUp, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { FRONT_PAD, INK, KCAL, MENU, MINUTES, bakeBack, bakeFront, bakeStrip, makeLayout } from './slotReelArt';
import {
  LEVER_MAX, LOOP, REST_ROWS, ROWS, STOPS, SPIN_AT, SWEEP_AT, WIN_ROWS, autoLever, autoOffset, autoWin, easeInOutCubic, easeOutCubic,
  hash, leverSpring, spinDistance, spinRows, wrap,
} from './slotReelMotion';
import { SLOT_REEL_SKSL } from './slotReelShader';

/*
 Slot Reel List — "Supper Slots", a spin-for-dinner picker. Three drums (mains, sides, drinks) turn
 behind a cherry-enamel cabinet's window; each is a list wrapped round a cylinder, so its rows
 squeeze and darken as they turn away, and blur with speed. Drag a drum to scroll it (it snaps to a
 row with a bounce); pull the chrome lever to spin all three: they stop one after another with a
 clunk, and the winning row lights up with a sweep while the card below rolls to the new supper.

 autoPlay (LOOP = 9 s, slotReelMotion.ts): rest 0–1.3 · lever 1.3–1.9 · spin 1.9–4.8 (stops 3.6,
 4.1, 4.6) · win sweep 4.8–5.8 · hold 5.8–7.0 · drums roll back to their resting rows 7.0–8.4 ·
 rest. A touch takes over at once; 2 s after the last one the drums roll back (0.9 s) and the loop
 starts again. Reduced motion: no loop, spins land at once, no sweep.

 Drawing: one Canvas — a baked back image, one RuntimeEffect for the three drums, a baked front
 image (shadows, glass, payline), and the lever (a few shapes on derived values).
*/

type Props = {
  /** Pulls the lever, spins to a supper, lights the win, rolls back and starts over. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
const TURNS = [3, 4, 5];
const effect = Skia.RuntimeEffect.Make(SLOT_REEL_SKSL);

function pickOf(rows: number[]) {
  const main = MENU[0][rows[0]].name.replace('\n', ' ');
  const side = MENU[1][rows[1]].name.replace('\n', ' ').toLowerCase();
  const drink = MENU[2][rows[2]].name.replace('\n', ' ').toLowerCase();
  const kcal = KCAL[0][rows[0]] + KCAL[1][rows[1]] + KCAL[2][rows[2]];
  return { key: rows.join('-'), main, line: `with ${side} and a ${drink}`, minutes: MINUTES[rows[0]] + 10, kcal };
}

export function SlotReelList({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const [rows, setRows] = useState<number[]>(REST_ROWS);

  const art = useMemo(() => {
    const L = makeLayout(size.width, size.height, insets.top, insets.bottom);
    const px = Math.min(3, PixelRatio.get());
    return { L, px, strip: bakeStrip(L, px), back: bakeBack(L, px), front: bakeFront(L, px) };
  }, [size.width, size.height, insets.top, insets.bottom]);
  const { L, px } = art;

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const off0 = useSharedValue(REST_ROWS[0]);
  const off1 = useSharedValue(REST_ROWS[1]);
  const off2 = useSharedValue(REST_ROWS[2]);
  /** Per drum under touch: 1 while a lever spin drives it; its start, length, base and distance. */
  const spin = useSharedValue({ kind: [0, 0, 0], start: [0, 0, 0], dur: [1, 1, 1], base: [0, 0, 0], dist: [0, 0, 0], count: 0, winAt: -10 });
  const lever = useSharedValue(0);
  const leverHeld = useSharedValue(0);
  const leverFrom = useSharedValue(0);
  const leverAt = useSharedValue(-10);
  const dragging = useSharedValue(-1);
  const returnAt = useSharedValue(0);
  const returnFrom = useSharedValue([0, 0, 0]);
  const shown = useSharedValue([...REST_ROWS]);
  const state = useSharedValue({ off: [...REST_ROWS], blur: [0, 0, 0], glow: 0, sweep: -1, lever: 0 });

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  // ——— UI-thread helpers (declared before their callers) ———

  function freeOf(c: number) {
    'worklet';
    return c === 0 ? off0 : c === 1 ? off1 : off2;
  }

  function offsetNow(c: number) {
    'worklet';
    const s = spin.get();
    if (s.kind[c] === 1) return s.base[c] + spinRows(now.get() - s.start[c], s.dur[c], s.dist[c]);
    return freeOf(c).get();
  }

  /** Takes the machine from the loop where it is: drums keep their offsets, the lever its angle. */
  function touched() {
    'worklet';
    lastTouch.set(now.get());
    if (mode.get() === MODE_USER) return;
    const t = clock.get();
    const s = spin.get();
    for (let c = 0; c < 3; c++) {
      const o = mode.get() === MODE_AUTO ? autoOffset(c, t) : state.get().off[c];
      freeOf(c).set(o);
      s.kind[c] = 0;
    }
    // A lever caught mid-swing keeps swinging from where it is.
    leverFrom.set(state.get().lever);
    leverAt.set(now.get());
    s.winAt = -10;
    spin.set({ ...s });
    mode.set(MODE_USER);
  }

  function startSpin() {
    'worklet';
    const s = spin.get();
    const at = now.get();
    s.count += 1;
    for (let c = 0; c < 3; c++) {
      const o = offsetNow(c);
      const from = Math.round(o);
      const to = Math.floor(hash(s.count * 3.1 + c * 7.7) * ROWS);
      const final = from + spinDistance(wrap(from, ROWS), to, TURNS[c]);
      if (reduced) {
        freeOf(c).set(final);
        s.kind[c] = 0;
        continue;
      }
      s.kind[c] = 1;
      s.base[c] = o;
      s.dist[c] = final - o;
      s.start[c] = at + 0.06 * c;
      s.dur[c] = STOPS[c] - SPIN_AT;
    }
    s.winAt = reduced ? at : at + 0.12 + s.dur[2] + 0.2;
    spin.set({ ...s });
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const prev = state.get();
    const off = [0, 0, 0];
    let lv = 0;
    let glow = 0;
    let sweep = -1;
    let settled = true;

    if (mode.get() === MODE_AUTO) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      for (let c = 0; c < 3; c++) off[c] = autoOffset(c, t);
      lv = autoLever(t);
      const w = autoWin(t);
      glow = w.glow;
      sweep = w.sweep;
      const pick = t >= SWEEP_AT && t < 8.4 ? WIN_ROWS : REST_ROWS;
      const sh = shown.get();
      if (sh[0] !== pick[0] || sh[1] !== pick[1] || sh[2] !== pick[2]) {
        shown.set([...pick]);
        scheduleOnRN(setRows, [...pick]);
      }
    } else if (mode.get() === MODE_RETURN) {
      const p = easeInOutCubic((at - returnAt.get()) / RETURN);
      const from = returnFrom.get();
      for (let c = 0; c < 3; c++) {
        // To the nearest offset that shows the resting row.
        const d = wrap(REST_ROWS[c] - from[c] + ROWS / 2, ROWS) - ROWS / 2;
        off[c] = from[c] + d * p;
      }
      lv = leverSpring(at - leverAt.get(), leverFrom.get());
      glow = prev.glow * (1 - Math.min(1, dt * 8));
      if (p >= 1) {
        mode.set(MODE_AUTO);
        clock.set(0);
      }
    } else {
      const s = spin.get();
      let stopped = false;
      for (let c = 0; c < 3; c++) {
        if (s.kind[c] === 1) {
          const end = s.start[c] + s.dur[c];
          if (at - dt < end && at >= end) stopped = true;
          if (at > end + 0.9) {
            freeOf(c).set(s.base[c] + s.dist[c]);
            s.kind[c] = 0;
          } else settled = false;
        }
        off[c] = offsetNow(c);
      }
      if (stopped) scheduleOnRN(feel, 1);
      lv = leverHeld.get() ? lever.get() : leverSpring(at - leverAt.get(), leverFrom.get());
      const k = at - s.winAt;
      if (k >= 0 && k < 2.6) {
        if (k < dt) scheduleOnRN(feel, 2);
        glow = easeOutCubic(k / 0.2) * (1 - easeOutCubic((k - 2.2) / 0.3));
        sweep = !reduced && k <= 1 ? easeInOutCubic(k) : -1;
        settled = false;
      }
      for (let c = 0; c < 3; c++) if (Math.abs(off[c] - Math.round(off[c])) > 0.01) settled = false;
      if (dragging.get() >= 0 || leverHeld.get()) settled = false;
      if (settled) {
        const pick = [0, 1, 2].map((c) => wrap(Math.round(off[c]), ROWS));
        const sh = shown.get();
        if (sh[0] !== pick[0] || sh[1] !== pick[1] || sh[2] !== pick[2]) {
          shown.set(pick);
          scheduleOnRN(setRows, pick);
        }
        if (auto && at - lastTouch.get() > IDLE) {
          returnFrom.set([...off]);
          returnAt.set(at);
          leverFrom.set(lv);
          leverAt.set(at);
          mode.set(MODE_RETURN);
        }
      }
    }

    // Blur from each drum's speed: the distance it covers in ~1/30 s.
    const blur = [0, 0, 0];
    let same = Math.abs(lv - prev.lever) < 1e-4 && Math.abs(glow - prev.glow) < 1e-4 && sweep === prev.sweep;
    for (let c = 0; c < 3; c++) {
      const v = (off[c] - prev.off[c]) / Math.max(dt, 0.001);
      blur[c] = reduced ? 0 : Math.min(0.9, Math.abs(v) * 0.033);
      if (Math.abs(off[c] - prev.off[c]) > 1e-5 || Math.abs(blur[c] - prev.blur[c]) > 1e-4) same = false;
    }
    if (!same) state.set({ off, blur, glow, sweep, lever: lv });
  });

  const uniforms = useDerivedValue(() => {
    const s = state.get();
    return {
      origin: [L.win.x, L.win.y],
      win: [L.win.w, L.win.h],
      colW: L.colW,
      gap: L.gap,
      rowH: L.rowH,
      radius: L.radius,
      rows: ROWS,
      px,
      off: s.off,
      blur: s.blur,
      sweep: s.sweep,
      glow: s.glow,
    };
  });

  // The lever: an arm from the hub to the knob; tipping toward the viewer shortens the arm and
  // swells the knob (it comes closer).
  const knob = useDerivedValue(() => {
    const a = state.get().lever;
    const z = Math.max(0, Math.sin(a));
    return { y: L.pivot.y - L.arm * Math.cos(a), r: 15 * (1 + 0.32 * z), w: 9 * (1 + 0.2 * z), z };
  });
  const armY = useDerivedValue(() => Math.min(L.pivot.y, knob.get().y));
  const armH = useDerivedValue(() => Math.abs(knob.get().y - L.pivot.y));
  const armX = useDerivedValue(() => L.pivot.x - knob.get().w / 2);
  const armW = useDerivedValue(() => knob.get().w);
  const knobY = useDerivedValue(() => knob.get().y);
  const knobR = useDerivedValue(() => knob.get().r);
  const shadowX = useDerivedValue(() => L.pivot.x + 4 + 6 * knob.get().z);
  const shadowY = useDerivedValue(() => knob.get().y + 6 + 10 * knob.get().z);
  const glint = useDerivedValue(() => vec(L.pivot.x - knob.get().r * 0.35, knob.get().y - knob.get().r * 0.4));

  // ——— Gestures ———

  const leverPan = Gesture.Pan()
    .minDistance(2)
    .onStart(() => {
      touched();
      leverHeld.set(1);
      lever.set(state.get().lever);
      scheduleOnRN(feel, 0);
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      const raw = Math.max(0, e.translationY) / (L.arm * 1.6);
      // Past the bottom it resists (rubber band).
      const over = Math.max(0, raw - 1);
      lever.set(LEVER_MAX * (Math.min(raw, 1) + over / (1 + over * 4) * 0.15));
    })
    .onFinalize(() => {
      if (!leverHeld.get()) return;
      leverHeld.set(0);
      lastTouch.set(now.get());
      const a = lever.get();
      leverFrom.set(a);
      leverAt.set(now.get());
      if (a > LEVER_MAX * 0.55) startSpin();
    });

  const reelPan = Gesture.Pan()
    .minDistance(4)
    .onStart((e) => {
      touched();
      const x0 = e.x - e.translationX;
      const c = Math.max(0, Math.min(2, Math.floor(x0 / (L.colW + L.gap))));
      const s = spin.get();
      if (s.kind[c] === 1) {
        freeOf(c).set(offsetNow(c));
        s.kind[c] = 0;
        spin.set({ ...s });
      }
      dragging.set(c);
      freeOf(c).set(freeOf(c).get());
    })
    .onChange((e) => {
      const c = dragging.get();
      if (c < 0) return;
      lastTouch.set(now.get());
      freeOf(c).set(freeOf(c).get() - e.changeY / L.rowH);
    })
    .onFinalize((e) => {
      const c = dragging.get();
      if (c < 0) return;
      dragging.set(-1);
      lastTouch.set(now.get());
      const v = -e.velocityY / L.rowH;
      const o = freeOf(c).get();
      const target = Math.round(o + v * 0.22);
      freeOf(c).set(reduced ? target : withSpring(target, { duration: 650, dampingRatio: 0.55, velocity: v }));
      if (target !== Math.round(o)) scheduleOnRN(feel, 0);
    });

  const pick = pickOf(rows);
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };
  const leverTop = L.pivot.y - L.arm - 30;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Image image={art.back} x={0} y={0} width={L.W} height={L.H} fit="fill" />
        {effect && art.strip ? (
          <Rect x={L.win.x} y={L.win.y} width={L.win.w} height={L.win.h}>
            <Shader source={effect} uniforms={uniforms}>
              <ImageShader image={art.strip} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
            </Shader>
          </Rect>
        ) : null}
        <Image image={art.front} x={L.win.x - FRONT_PAD} y={L.win.y - FRONT_PAD} width={L.win.w + FRONT_PAD * 2} height={L.win.h + FRONT_PAD * 2} fit="fill" />
        <Group>
          <Circle cx={shadowX} cy={shadowY} r={knobR} color="rgba(0,0,0,0.45)">
            <BlurMask blur={7} style="normal" />
          </Circle>
          <RoundedRect x={armX} y={armY} width={armW} height={armH} r={4}>
            <LinearGradient start={vec(L.pivot.x - 7, 0)} end={vec(L.pivot.x + 7, 0)} colors={['#5D6368', '#F4F6F7', '#B4BABE', '#4A4F53']} positions={[0, 0.35, 0.6, 1]} />
          </RoundedRect>
          <Circle cx={L.pivot.x} cy={knobY} r={knobR}>
            <RadialGradient c={glint} r={30} colors={['#FFB4A4', '#E2302C', '#9C1418', '#56080B']} positions={[0, 0.3, 0.75, 1]} />
          </Circle>
        </Group>
      </Canvas>

      <GestureDetector gesture={reelPan}>
        <View style={{ position: 'absolute', left: L.win.x, top: L.win.y, width: L.win.w, height: L.win.h }} accessibilityLabel="Reels: drag one to scroll it" />
      </GestureDetector>
      <GestureDetector gesture={leverPan}>
        <View style={{ position: 'absolute', left: L.W - 56, top: leverTop, width: 56, height: L.arm * 2 + 60 }} accessibilityRole="adjustable" accessibilityLabel="Lever: pull down to spin" />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>SUPPER SLOTS</Text>
        <Text style={styles.title}>Spin for dinner</Text>
        <Text style={styles.subtitle}>3 pulls left tonight · Serves 2</Text>
      </View>

      <View style={[styles.card, { top: L.cardY, bottom: insets.bottom + 12 }]}>
        <Text style={styles.cardLabel}>{"TONIGHT'S SUPPER"}</Text>
        <View style={styles.roll}>
          <Animated.View
            key={pick.key}
            entering={reduced ? undefined : FadeInDown.duration(320).easing(Easing.bezier(0.23, 1, 0.32, 1))}
            exiting={reduced ? undefined : FadeOutUp.duration(220)}
            style={styles.rollInner}
          >
            <Text style={styles.cardTitle} numberOfLines={1}>{pick.main}</Text>
            <Text style={styles.cardLine} numberOfLines={1}>{pick.line}</Text>
            <View style={styles.chips}>
              <Text style={styles.chip}>{`${pick.minutes} min`}</Text>
              <Text style={styles.chip}>{`${pick.kcal} kcal`}</Text>
              <Text style={styles.chip}>Serves 2</Text>
            </View>
          </Animated.View>
        </View>
        <View style={styles.actions}>
          <Pressable style={({ pressed }) => [styles.primary, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
            <Text style={styles.primaryLabel}>Cook this</Text>
          </Pressable>
          <Pressable style={({ pressed }) => [styles.secondary, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
            <Text style={styles.secondaryLabel}>Save combo</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: INK.wall },
  header: { position: 'absolute', left: 22, right: 22, gap: 4 },
  kicker: { color: INK.gold, fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  title: { color: INK.cream, fontSize: 32, fontWeight: '800', letterSpacing: -0.5 },
  subtitle: { color: INK.creamSoft, fontSize: 15, fontWeight: '500' },
  card: {
    position: 'absolute',
    left: 14,
    right: 14,
    borderRadius: 26,
    borderCurve: 'continuous',
    padding: 20,
    gap: 12,
    backgroundColor: INK.card,
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 16px 36px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,230,210,0.1)',
    justifyContent: 'space-between',
  },
  cardLabel: { color: INK.creamFaint, fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  roll: { height: 92, overflow: 'hidden' },
  rollInner: { position: 'absolute', left: 0, right: 0, top: 0, gap: 4 },
  cardTitle: { color: INK.cream, fontSize: 24, fontWeight: '800', letterSpacing: -0.3 },
  cardLine: { color: INK.creamSoft, fontSize: 15, fontWeight: '500' },
  chips: { flexDirection: 'row', gap: 8, marginTop: 8 },
  chip: {
    color: INK.cream,
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: 'rgba(245,237,224,0.08)',
  },
  actions: { flexDirection: 'row', gap: 12 },
  primary: { flex: 1, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: INK.gold, boxShadow: '0 6px 16px rgba(240,181,74,0.25)' },
  primaryLabel: { color: '#2B1708', fontSize: 16, fontWeight: '800' },
  secondary: { flex: 1, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'rgba(245,237,224,0.22)' },
  secondaryLabel: { color: INK.cream, fontSize: 16, fontWeight: '700' },
});

