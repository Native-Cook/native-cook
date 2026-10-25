import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Art, BEAD_COLORS, drawAbacus, recordBead, recordBeadShadow, recordBoard } from './abacusArt';
import {
  BEADS,
  BUDGETS,
  CATEGORIES,
  LOOP,
  PRICES,
  REST_COUNTS,
  RODS,
  WEEK_BUDGET,
  countBeads,
  easeInOut,
  loopBeads,
  loopFinger,
  loopUndoPress,
  makeGeo,
  restBeads,
  seg,
  settled,
  stepBeads,
} from './abacusMotion';
import { Meter, RollingAmount } from './AbacusRolling';
import { AbacusWeek, WEEK_H } from './AbacusWeek';

/*
 Abacus Expense Counter — "Tally", a weekly budget logged on a wooden abacus. Each rod is a
 category, each lacquered bead one purchase at that category's price; flick a bead to the right
 to log it. Beads slide with momentum and friction, knock into their neighbours and bounce off
 the frame with a light click; the rod's amount, the week's total, what is left and the daily
 pace roll to the new numbers, and each category's meter springs.

 One Skia canvas (abacusArt.ts: the board and one bead per colour recorded once; 100 translated
 pictures per frame). Beads: abacusMotion.ts, analytic in autoPlay (LOOP = 8 s), a small physics
 step under the finger. Amounts are digit wheels on shared values (AbacusRolling.tsx), so React
 never re-renders while beads move.

 Modes: 0 loop · 1 touch physics · 2 gliding back to rest (0.9 s ease-in-out: "Undo today", or
 2 s after the last touch once the beads have settled), then the loop restarts.
 */

type Props = {
  /** Plays the 8 s demo: six flicks across three rods, a hold on the totals, then "Undo today". */
  autoPlay?: boolean;
};

const BACKGROUND = '#16110d';
const INK = '#f4ece0';
const INK_SOFT = '#ad9e8e';
const ACCENT = '#d9a441';
const RETURN_AFTER = 2;
const RETURN_FOR = 0.9;
const HEADER_H = 128;
const TILES_H = 70;
/** Room kept under the abacus: the two tiles, the week strip and the hint. */
const FOOTER_H = 20 + TILES_H + 12 + WEEK_H + 44;
const REST_CENTS = REST_COUNTS.map((n, r) => n * PRICES[r]);
const REST_TOTAL = REST_CENTS.reduce((a, b) => a + b, 0);
const hexString = (c: number) => `#${c.toString(16).padStart(6, '0')}`;

function click() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function AbacusExpenseCounter({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const boardY = insets.top + 12 + HEADER_H;
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, boardY, FOOTER_H + Math.max(insets.bottom, 12)) : null),
    [size, boardY, insets.bottom],
  );
  const art = useMemo<Art | null>(() => (g ? { board: recordBoard(g), beads: BEAD_COLORS.map((c) => recordBead(g, c)), shadow: recordBeadShadow(g) } : null), [g]);

  const now = useSharedValue(-1);
  const loopStart = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const beadX = useSharedValue<number[]>([]);
  const beadV = useSharedValue<number[]>([]);
  /** [rod, bead, target x] while a bead is held. */
  const grab = useSharedValue<number[]>([]);
  const grabOffset = useSharedValue(0);
  const finger = useSharedValue<number[]>([0, 0, 0]);
  const undoPress = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const lastClick = useSharedValue(0);
  /** Mode 2: [start time, x × 50 at the start]. */
  const glide = useSharedValue<number[]>([]);
  const rod0 = useSharedValue(REST_CENTS[0]);
  const rod1 = useSharedValue(REST_CENTS[1]);
  const rod2 = useSharedValue(REST_CENTS[2]);
  const rod3 = useSharedValue(REST_CENTS[3]);
  const rod4 = useSharedValue(REST_CENTS[4]);
  const rodCents = [rod0, rod1, rod2, rod3, rod4];
  const total = useSharedValue(REST_TOTAL);

  useFrameCallback((info) => {
    if (!g) return;
    const t0 = info.timestamp / 1000;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (now.get() < 0) loopStart.set(t0);
    now.set(t0);
    const m = mode.get();
    let xs = beadX.get();
    if (xs.length !== RODS * BEADS) xs = restBeads(g, new Array(RODS * BEADS).fill(0));
    if (m === 0) {
      const t = (t0 - loopStart.get()) % LOOP;
      xs = loopBeads(g, t, xs.slice());
      finger.set(loopFinger(g, t));
      undoPress.set(loopUndoPress(t));
    } else if (m === 1) {
      xs = xs.slice();
      let vs = beadV.get();
      vs = vs.length === xs.length ? vs.slice() : new Array(xs.length).fill(0);
      const hit = stepBeads(g, xs, vs, grab.get(), dt);
      beadV.set(vs);
      if (hit > 0 && t0 - lastClick.get() > 0.1) {
        lastClick.set(t0);
        scheduleOnRN(click);
      }
      finger.set([0, 0, 0]);
      undoPress.set(0);
      if (auto && grab.get().length === 0 && t0 - lastTouch.get() > RETURN_AFTER && settled(g, xs, vs)) {
        glide.set([t0, ...xs]);
        mode.set(2);
      }
    } else {
      const gl = glide.get();
      const k = easeInOut(seg(t0 - gl[0], 0, reduced ? 0.3 : RETURN_FOR));
      const rest = restBeads(g, new Array(RODS * BEADS).fill(0));
      xs = rest.map((r, i) => gl[i + 1] + (r - gl[i + 1]) * k);
      if (k >= 1) {
        beadV.set(new Array(RODS * BEADS).fill(0));
        if (auto && t0 - lastTouch.get() > RETURN_AFTER) {
          mode.set(0);
          loopStart.set(t0);
        } else mode.set(1);
      }
    }
    beadX.set(xs);
    // Amounts: only written when a count changes, so the wheels roll once per bead.
    const counts = countBeads(g, xs, [0, 0, 0, 0, 0]);
    let sum = 0;
    for (let r = 0; r < RODS; r++) {
      const c = counts[r] * PRICES[r];
      sum += c;
      if (rodCents[r].get() !== c) rodCents[r].set(c);
    }
    if (total.get() !== sum) total.set(sum);
  });

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    const xs = beadX.get();
    if (g && art && xs.length === RODS * BEADS) drawAbacus(canvas, g, art, xs, finger.get());
    return rec.finishRecordingAsPicture();
  });

  const gesture = useMemo(() => {
    if (!g) return Gesture.Pan();
    function takeOver() {
      'worklet';
      if (mode.get() !== 1) {
        mode.set(1);
        beadV.set(new Array(RODS * BEADS).fill(0));
      }
    }
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        takeOver();
        lastTouch.set(now.get());
        const x = e.x + g.boardX;
        const y = e.y + g.boardY;
        let r = 0;
        for (let k = 1; k < RODS; k++) if (Math.abs(g.rodY[k] - y) < Math.abs(g.rodY[r] - y)) r = k;
        if (Math.abs(g.rodY[r] - y) > g.band * 0.55) return;
        const xs = beadX.get();
        let best = -1;
        let bestD = g.bw * 0.85;
        for (let j = 0; j < BEADS; j++) {
          const d = Math.abs(xs[r * BEADS + j] - x);
          if (d < bestD) {
            bestD = d;
            best = j;
          }
        }
        if (best < 0) return;
        grabOffset.set(x - xs[r * BEADS + best]);
        grab.set([r, best, xs[r * BEADS + best]]);
      })
      .onUpdate((e) => {
        const gr = grab.get();
        lastTouch.set(now.get());
        if (gr.length !== 3) return;
        grab.set([gr[0], gr[1], e.x + g.boardX - grabOffset.get()]);
      })
      .onFinalize((e) => {
        const gr = grab.get();
        lastTouch.set(now.get());
        if (gr.length !== 3) return;
        grab.set([]);
        // The flick: the bead leaves with the finger's speed (the push chain follows by contact).
        const vs = beadV.get().slice();
        vs[gr[0] * BEADS + gr[1]] = reduced ? 0 : Math.max(-2200, Math.min(2200, e.velocityX));
        beadV.set(vs);
      });
    // Shared values are stable; the gesture depends on the geometry only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced]);

  const undo = () => {
    scheduleOnUI(() => {
      'worklet';
      lastTouch.set(now.get());
      grab.set([]);
      glide.set([now.get(), ...beadX.get()]);
      mode.set(2);
    });
    click();
  };

  const leftCents = useDerivedValue(() => Math.max(0, WEEK_BUDGET - total.get()));
  const paceCents = useDerivedValue(() => Math.round(total.get() / 7));
  const budgetShare = useDerivedValue(() => total.get() / WEEK_BUDGET);
  const shares = [
    useDerivedValue(() => rod0.get() / BUDGETS[0]),
    useDerivedValue(() => rod1.get() / BUDGETS[1]),
    useDerivedValue(() => rod2.get() / BUDGETS[2]),
    useDerivedValue(() => rod3.get() / BUDGETS[3]),
    useDerivedValue(() => rod4.get() / BUDGETS[4]),
  ];
  const chip = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.05 * undoPress.get() }],
    backgroundColor: `rgba(217,164,65,${0.14 + 0.16 * undoPress.get()})`,
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>

          <View style={[styles.header, { top: insets.top + 12 }]}>
            <View style={styles.topRow}>
              <Text style={styles.kicker}>TALLY · WEEK 43</Text>
              <Pressable accessibilityRole="button" onPress={undo} hitSlop={8}>
                {({ pressed }) => (
                  <Animated.View style={[styles.chip, chip, pressed && styles.chipPressed]}>
                    <Text style={styles.chipText}>Undo today</Text>
                  </Animated.View>
                )}
              </Pressable>
            </View>
            <Text style={styles.caption}>Weekly spending</Text>
            <RollingAmount cents={total} initial={REST_TOTAL} size={44} color={INK} reduced={reduced} />
            <View style={styles.budgetRow}>
              <Meter value={budgetShare} color={ACCENT} height={6} reduced={reduced} />
              <Text style={styles.budgetText}>of $520 · Oct 20 – 26</Text>
            </View>
          </View>

          <GestureDetector gesture={gesture}>
            <View
              style={[styles.board, { left: g.boardX, top: g.boardY, width: g.boardW, height: g.boardH }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Abacus. Flick a bead right to log a purchase, push it back to remove it."
            />
          </GestureDetector>

          {CATEGORIES.map((name, r) => (
            <View key={name} pointerEvents="none" style={[styles.rodLabel, { left: g.innerL, right: g.w - g.innerR, top: g.boardY + g.frame + r * g.band + 4 }]}>
              <View style={[styles.dot, { backgroundColor: hexString(BEAD_COLORS[r]) }]} />
              <Text style={styles.rodName}>{name}</Text>
              <View style={styles.rodMeter}>
                <Meter value={shares[r]} color={hexString(BEAD_COLORS[r])} height={3} reduced={reduced} />
              </View>
              <RollingAmount cents={rodCents[r]} initial={REST_CENTS[r]} size={13} weight="600" color={INK} reduced={reduced} />
            </View>
          ))}

          <View pointerEvents="none" style={[styles.footer, { top: g.boardY + g.boardH + 20 }]}>
            <View style={styles.tile}>
              <Text style={styles.tileLabel}>Left this week</Text>
              <RollingAmount cents={leftCents} initial={WEEK_BUDGET - REST_TOTAL} size={20} color={INK} reduced={reduced} />
            </View>
            <View style={styles.tile}>
              <Text style={styles.tileLabel}>Daily pace</Text>
              <RollingAmount cents={paceCents} initial={Math.round(REST_TOTAL / 7)} size={20} color={INK} intDigits={2} reduced={reduced} />
            </View>
          </View>

          {g.boardY + g.boardH + 20 + TILES_H + 12 + WEEK_H <= size.height - Math.max(insets.bottom, 12) - 34 ? (
            <View pointerEvents="none" style={[styles.week, { top: g.boardY + g.boardH + 20 + TILES_H + 12 }]}>
              <AbacusWeek total={total} base={REST_TOTAL} color={ACCENT} reduced={reduced} />
            </View>
          ) : null}

          <Text pointerEvents="none" style={[styles.hint, { bottom: Math.max(insets.bottom, 12) + 10 }]}>
            Flick a bead right to log it · push it back to remove it
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, height: HEADER_H, gap: 2 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 30 },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '800', letterSpacing: 2 },
  chip: { height: 30, paddingHorizontal: 14, borderRadius: 15, justifyContent: 'center', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(217,164,65,0.45)' },
  chipPressed: { opacity: 0.85 },
  chipText: { color: '#f1d79e', fontSize: 13, fontWeight: '600' },
  caption: { color: INK_SOFT, fontSize: 14, fontWeight: '500', marginTop: 2 },
  budgetRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 4 },
  budgetText: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  board: { position: 'absolute' },
  rodLabel: { position: 'absolute', height: 16, flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: { width: 7, height: 7, borderRadius: 3.5 },
  rodName: { color: '#e9dccb', fontSize: 13, fontWeight: '600', width: 58 },
  rodMeter: { flex: 1, maxWidth: 120, marginLeft: 'auto' },
  footer: { position: 'absolute', left: 16, right: 16, height: TILES_H, flexDirection: 'row', gap: 12 },
  week: { position: 'absolute', left: 16, right: 16 },
  tile: {
    flex: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 4,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(244,236,224,0.05)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(244,236,224,0.1)',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05), 0 8px 24px rgba(0,0,0,0.25)',
  },
  tileLabel: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(244,236,224,0.45)', fontSize: 12, fontWeight: '500' },
});
