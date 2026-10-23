/**
 * Vitals Chip Morph — three screens of one health app (Readiness, Fitness, Heart) driven by the
 * metric chips. The hero is one shape that becomes the next metric: the Readiness arc closes into
 * the Move ring, the ring fills in to become the heart, the heart opens at its tip back into the arch.
 * Tap a chip, or drag the hero sideways (velocity hand-off, rubber band at both ends).
 */

import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  type FrameInfo,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { Hero } from './Hero';
import { BrowseIcon, FitnessIcon, HeartGlyph, LongevityIcon, ReadinessIcon, SleepIcon, SummaryIcon, VitalsIcon } from './icons';
import {
  BEAT,
  DRAG,
  LOOP,
  RESUME_AFTER,
  SPRINGS,
  bump,
  clamp01,
  easeInOut,
  heroFrame,
  lerp,
  LIQUID,
  loopBeatCount,
  loopBlend,
  loopSpeed,
  pulse,
  seg,
  spring,
  springVelocity,
} from './motion';
import { CAP_OFFSET, FitnessPage, HeartPage, ReadinessPage, ROUNDED } from './Pages';
import { CHIP_SCREEN, REF_INSET, REF_PT, REF_W, SCREEN_CHIP, chips, colors, layout, lens, radius, space, type as typeScale } from './theme';

export { LOOP };

type Props = { autoPlay?: boolean };

const AUTO = 0;
const MANUAL = 1;
/** Spring kinds for a manual transition. */
const TAP = 0;
const RELEASE = 1;
const IDLE = 2;
const FADE = 3;

const SCREEN_NAMES = ['Readiness', 'Fitness', 'Heart'];
/** iOS 26 Liquid Glass (expo-glass-effect); older systems keep the blur and the plain pill. */
const GLASS = isLiquidGlassAvailable();
const SCREEN_VALUES = ['7 out of 10, ready', '375 of 500 calories, 19 of 30 exercise minutes, 4 of 12 stand hours', '62 beats per minute'];

function selectionHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function springFor(kind: number) {
  'worklet';
  if (kind === RELEASE) return SPRINGS.release;
  if (kind === IDLE) return SPRINGS.idle;
  return SPRINGS.tap;
}

type Controller = {
  lo: SharedValue<number>;
  hi: SharedValue<number>;
  u: SharedValue<number>;
  target: SharedValue<number>;
  press: SharedValue<number>;
  pressChip: SharedValue<number>;
  quiet: SharedValue<boolean>;
};

// ─── chips ────────────────────────────────────────────────────────────────────────────────────

function chipIcon(key: string, s: number): ReactNode {
  switch (key) {
    case 'summary':
      return <SummaryIcon s={s} />;
    case 'sleep':
      return <SleepIcon s={s} />;
    case 'readiness':
      return <ReadinessIcon s={s} />;
    case 'fitness':
      return <FitnessIcon s={s} />;
    case 'heart':
      return <HeartGlyph size={31 * s} color={colors.heart} />;
    default:
      return <VitalsIcon s={s} />;
  }
}

function Chip({ index, c, k, s, onSelect }: { index: number; c: Controller; k: number; s: number; onSelect: (chip: number) => void }) {
  const spec = chips[index];
  const screen = SCREEN_CHIP.indexOf(index as 2 | 3 | 4);
  const [pressed, setPressed] = useState(false);
  const outer = useAnimatedStyle(() => {
    const auto = c.pressChip.get() === index ? c.press.get() : 0;
    return { transform: [{ scale: 1 - 0.03 * auto }] };
  });
  const border = useAnimatedStyle(() => {
    if (screen < 0) return { opacity: 1 };
    const lo = c.lo.get();
    const hi = c.hi.get();
    const u = c.u.get();
    const w = lo === hi ? (lo === screen ? 1 : 0) : screen === lo ? 1 - u : screen === hi ? u : 0;
    return { opacity: 1 - w };
  });
  const icon = useAnimatedStyle(() => {
    if (screen < 0 || c.quiet.get()) return { transform: [{ scale: 1 }] };
    const lo = c.lo.get();
    const hi = c.hi.get();
    const target = c.target.get();
    if (lo === hi || target !== screen) return { transform: [{ scale: 1 }] };
    const toward = target === hi ? c.u.get() : 1 - c.u.get();
    return { transform: [{ scale: 1 + 0.12 * bump(toward, 0.5, 0.95) }] };
  });
  const isScreen = screen >= 0;
  return (
    <Animated.View style={[{ position: 'absolute', left: spec.x * k, top: 0, width: spec.w * k, height: layout.chipH * k }, outer]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={spec.value ? `${spec.title}, ${spec.value}` : 'Summary'}
        accessibilityHint={isScreen ? undefined : `Shows ${SCREEN_NAMES[CHIP_SCREEN[index]]}`}
        hitSlop={4}
        onPressIn={() => setPressed(true)}
        onPressOut={() => setPressed(false)}
        onPress={() => onSelect(index)}
        style={{ flex: 1 }}
      >
        <Animated.View
          style={{
            flex: 1,
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: pressed ? 90 : 160,
            transitionTimingFunction: 'ease-out',
          }}
        >
          <Animated.View
            pointerEvents="none"
            style={[
              StyleSheet.absoluteFill,
              { borderRadius: radius.chip * k, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: colors.chipBorder, backgroundColor: colors.chip, borderCurve: 'continuous' },
              border,
            ]}
          />
          {spec.key === 'summary' ? (
            <View style={styles.summaryIcon}>{chipIcon(spec.key, s)}</View>
          ) : (
            <>
              <Animated.View style={[styles.chipIcon, { left: space.chipIconCentre * k, top: (layout.chipH / 2) * k }, icon]}>
                {chipIcon(spec.key, s)}
              </Animated.View>
              <Text allowFontScaling={false} numberOfLines={1} style={[styles.chipTitle, { left: space.chipLabel * k, top: space.chipTitleCap * k - CAP_OFFSET * typeScale.chipTitle.size * s, fontSize: typeScale.chipTitle.size * s }]}>
                {spec.title}
              </Text>
              <Text allowFontScaling={false} numberOfLines={1} style={[styles.chipValue, { left: space.chipLabel * k, top: space.chipValueCap * k - CAP_OFFSET * typeScale.chipValue.size * s, fontSize: typeScale.chipValue.size * s }]}>
                {spec.value}
              </Text>
            </>
          )}
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** The selection's edges for a blend: the edge in the direction of travel leads, the other follows. */
function pillEdges(lo: number, hi: number, u: number, quiet: boolean) {
  'worklet';
  const a = chips[SCREEN_CHIP[lo]];
  const b = chips[SCREEN_CHIP[hi]];
  const lead = quiet ? (u < 0.5 ? 0 : 1) : easeInOut(seg(u, 0, 0.7));
  const trail = quiet ? lead : easeInOut(seg(u, 0.15, 0.85));
  const left = lerp(a.x, b.x, b.x > a.x ? trail : lead);
  const right = lerp(a.x + a.w, b.x + b.w, b.x > a.x ? lead : trail);
  return { left, right };
}

/** How far the selection is in flight: 0 settled, 1 mid-way (the lens is lifted). */
function travel(lo: number, hi: number, u: number, quiet: boolean) {
  'worklet';
  return lo === hi || quiet ? 0 : bump(u, 0, 1);
}

/**
 * The Liquid Glass capsule. Its on/off switch is this component's own state, so a lens toggle re-renders only this
 * GlassView, not the chip row and its six chips (that commit cost a frame twice per morph).
 */
function LensGlass({ c, k }: { c: Controller; k: number }) {
  const [lensOn, setLensOn] = useState(false);
  // The glass appears and leaves natively (its own fade); only the switch crosses to JS.
  useAnimatedReaction(
    () => {
      if (!GLASS || c.quiet.get() || c.lo.get() === c.hi.get()) return false;
      const u = c.u.get();
      return u > lens.onFrom && u < lens.offAt;
    },
    (on, was) => {
      if (on !== was) scheduleOnRN(setLensOn, on);
    },
  );
  return (
    <GlassView
      glassEffectStyle={{ style: lensOn ? 'clear' : 'none', animate: true, animationDuration: lens.fade }}
      tintColor={lens.tint}
      style={[StyleSheet.absoluteFill, { borderRadius: radius.chip * k }]}
    />
  );
}

function ChipRow({ c, k, s, onSelect }: { c: Controller; k: number; s: number; onSelect: (chip: number) => void }) {
  const row = useAnimatedStyle(() => {
    const lo = c.lo.get();
    const hi = c.hi.get();
    const e = c.quiet.get() ? (c.u.get() < 0.5 ? 0 : 1) : easeInOut(seg(c.u.get(), 0, 0.85));
    return { transform: [{ translateX: lerp(layout.rowOffset[lo], layout.rowOffset[hi], e) * k }] };
  });
  const pill = useAnimatedStyle(() => {
    const quiet = c.quiet.get();
    const e = pillEdges(c.lo.get(), c.hi.get(), c.u.get(), quiet);
    // While it travels the white pill thins and the glass capsule under the labels carries the selection.
    const fly = GLASS ? travel(c.lo.get(), c.hi.get(), c.u.get(), quiet) : 0;
    return { left: e.left * k, width: (e.right - e.left) * k, opacity: 1 - 0.8 * fly };
  });
  const lensStyle = useAnimatedStyle(() => {
    const quiet = c.quiet.get();
    const e = pillEdges(c.lo.get(), c.hi.get(), c.u.get(), quiet);
    const fly = travel(c.lo.get(), c.hi.get(), c.u.get(), quiet);
    return { left: e.left * k, width: (e.right - e.left) * k, transform: [{ scale: 1 + (lens.liftScale - 1) * fly }] };
  });
  return (
    <Animated.View style={[{ position: 'absolute', left: 0, top: layout.chipTop * k, height: layout.chipH * k, width: 2400 * k }, row]}>
      <Animated.View pointerEvents="none" style={[styles.pill, { top: 0, height: layout.chipH * k, borderRadius: radius.chip * k }, pill]} />
      {/* The travelling selection turns into a Liquid Glass capsule under the labels (iOS 26). */}
      {GLASS && (
        <Animated.View pointerEvents="none" style={[{ position: 'absolute', top: 0, height: layout.chipH * k }, lensStyle]}>
          <LensGlass c={c} k={k} />
        </Animated.View>
      )}
      {chips.map((spec, index) => (
        <Chip key={spec.key} index={index} c={c} k={k} s={s} onSelect={onSelect} />
      ))}
    </Animated.View>
  );
}

// ─── hero numbers ─────────────────────────────────────────────────────────────────────────────

function ReadinessNumber({ c, k, s }: { c: Controller; k: number; s: number }) {
  const size = typeScale.readiness.size * s;
  const lineH = size * 1.2;
  const strip = useAnimatedStyle(() => {
    const f = heroFrame(c.lo.get(), c.hi.get(), c.quiet.get() ? (c.u.get() < 0.5 ? 0 : 1) : c.u.get());
    const roll = c.quiet.get() ? 7 : f.sevenRoll;
    return { opacity: f.sevenOpacity, transform: [{ translateY: -roll * lineH }] };
  });
  const top = layout.seven.capTop * k - CAP_OFFSET * size;
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: (layout.seven.cx - 150) * k, top, width: 300 * k, height: lineH, overflow: 'hidden' }}>
      <Animated.View style={strip}>
        {[0, 1, 2, 3, 4, 5, 6, 7].map((digit) => (
          <Text key={digit} allowFontScaling={false} style={{ height: lineH, fontSize: size, fontWeight: typeScale.readiness.weight, color: colors.blue, textAlign: 'center', fontFamily: ROUNDED }}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

function BpmPart({ c, which, children, style }: { c: Controller; which: 0 | 1 | 2; children: ReactNode; style: object }) {
  const rise = (style as { fontSize: number }).fontSize * 0.3;
  const animated = useAnimatedStyle(() => {
    const f = heroFrame(c.lo.get(), c.hi.get(), c.quiet.get() ? (c.u.get() < 0.5 ? 0 : 1) : c.u.get());
    const a = which === 0 ? f.bpm0 : which === 1 ? f.bpm1 : f.bpmUnit;
    return { opacity: a, transform: [{ translateY: (1 - a) * rise }] };
  });
  return (
    <Animated.Text allowFontScaling={false} style={[style, animated]}>
      {children}
    </Animated.Text>
  );
}

function BpmNumber({ c, k, s }: { c: Controller; k: number; s: number }) {
  const size = typeScale.bpm.size * s;
  const unit = typeScale.bpmUnit.size * s;
  const digit = { fontSize: size, fontWeight: typeScale.bpm.weight, letterSpacing: typeScale.bpm.ls * s, color: colors.ink, fontVariant: ['tabular-nums' as const] };
  const top = layout.bpm.capTop * k - CAP_OFFSET * size;
  const unitTop = (layout.bpm.unitCapTop - layout.bpm.capTop) * k + CAP_OFFSET * size - CAP_OFFSET * unit;
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top, alignItems: 'center' }}>
      <View style={{ flexDirection: 'row' }}>
        <BpmPart c={c} which={0} style={digit}>6</BpmPart>
        <BpmPart c={c} which={1} style={digit}>2</BpmPart>
      </View>
      <BpmPart c={c} which={2} style={{ position: 'absolute', top: unitTop, fontSize: unit, fontWeight: typeScale.bpmUnit.weight, color: colors.bpmUnit }}>
        BPM
      </BpmPart>
    </View>
  );
}

// ─── tab bar ──────────────────────────────────────────────────────────────────────────────────

function TabBar({ k, s }: { k: number; s: number }) {
  const t = layout.tabBar;
  const items = [
    { title: 'Insights', icon: <HeartGlyph size={27 * s} color={colors.tabBlue} /> },
    { title: 'Longevity', icon: <LongevityIcon s={s} /> },
    { title: 'Browse', icon: <BrowseIcon s={s} /> },
  ];
  const label = typeScale.tab.size * s;
  return (
    <View style={[styles.tabBar, { left: t.x * k, top: t.y * k, width: t.w * k, height: t.h * k, borderRadius: radius.tabBar * k, boxShadow: [{ offsetX: 0, offsetY: 6 * s, blurRadius: 18 * s, color: 'rgba(40,40,80,0.10)' }] }]}>
      {GLASS ? (
        <GlassView glassEffectStyle="regular" tintColor={colors.tabGlassTint} style={[StyleSheet.absoluteFill, { borderRadius: radius.tabBar * k }]} />
      ) : (
        <>
          <BlurView intensity={28} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.tabGlass }]} />
        </>
      )}
      <View style={{ position: 'absolute', left: (t.pill.x - t.x) * k, top: (t.pill.y - t.y) * k, width: t.pill.w * k, height: t.pill.h * k, borderRadius: radius.tabPill * k, backgroundColor: colors.tabActive }} />
      {items.map((item, index) => {
        const active = index === 0;
        const cx = (t.centres[index] - t.x) * k;
        return (
          <View key={item.title} accessible accessibilityRole="tab" accessibilityLabel={item.title} accessibilityState={{ selected: active }} style={{ position: 'absolute', left: cx - (t.pill.w / 2) * k, top: 0, width: t.pill.w * k, height: t.h * k }}>
            <View style={[styles.tabIcon, { left: (t.pill.w / 2) * k, top: (t.iconCy - t.y) * k }]}>{item.icon}</View>
            <Text allowFontScaling={false} style={{ position: 'absolute', left: 0, right: 0, textAlign: 'center', top: (t.labelCapTop - t.y) * k - CAP_OFFSET * label, fontSize: label, fontWeight: typeScale.tab.weight, color: active ? colors.tabBlue : colors.ink }}>
              {item.title}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

// ─── the block ────────────────────────────────────────────────────────────────────────────────

export function VitalsChipMorph({ autoPlay = false }: Props) {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const k = width / REF_W;
  const s = width / REF_PT;
  const initialReduced = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const [foregroundState, setForegroundState] = useState(AppState.currentState === 'active');
  const [current, setCurrent] = useState(0);
  const quietNow = reducedMotion || screenReader;

  const lo = useSharedValue(0);
  const hi = useSharedValue(0);
  const u = useSharedValue(0);
  const target = useSharedValue(0);
  const beat = useSharedValue(0);
  const press = useSharedValue(0);
  const pressChip = useSharedValue(-1);
  const quiet = useSharedValue(quietNow);
  const autoShared = useSharedValue(autoPlay);
  const shift = useSharedValue(0);

  const clock = useSharedValue(0);
  const lastTs = useSharedValue(-1);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const mode = useSharedValue(autoPlay && !quietNow ? AUTO : MANUAL);
  const loopOrigin = useSharedValue(0);
  const animating = useSharedValue(false);
  const animStart = useSharedValue(0);
  const animFrom = useSharedValue(0);
  const animV0 = useSharedValue(0);
  const animKind = useSharedValue(TAP);
  const pending = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const dragStartState = useSharedValue(-1);
  const dragBaseU = useSharedValue(0);
  const lastTouch = useSharedValue(-1e9);
  const settledAt = useSharedValue(0);
  const published = useSharedValue(0);
  // The liquid material: its clock, the shape's speed, the heart's beat count.
  const fxTime = useSharedValue(0);
  const speed = useSharedValue(0);
  const beatCount = useSharedValue(-1);
  const prevU = useSharedValue(0);
  const prevPair = useSharedValue(-1);

  const c: Controller = { lo, hi, u, target, press, pressChip, quiet };

  const publish = useCallback((screen: number) => setCurrent(screen), []);

  /** Start (or retarget) a spring towards screen `to`. Mid-transition to a third screen: queue it. */
  const goTo = useCallback(
    (to: number, kind: number, velocity: number) => {
      'worklet';
      const a = lo.get();
      const b = hi.get();
      const now = clock.get();
      let x0 = 0;
      let v0 = velocity;
      if (a === b) {
        if (to === a) return;
        lo.set(Math.min(a, to));
        hi.set(Math.max(a, to));
        u.set(a < to ? 0 : 1);
        x0 = 0;
      } else if (to === a || to === b) {
        const towardNow = target.get() === b ? u.get() : 1 - u.get();
        if (animating.get() && !dragging.get()) {
          const sp = springFor(animKind.get());
          const vNow = springVelocity(now - animStart.get(), animFrom.get(), animV0.get(), sp.omega, sp.zeta);
          v0 = target.get() === to ? vNow : -vNow;
        }
        x0 = to === b ? u.get() : 1 - u.get();
        if (target.get() === to) x0 = towardNow;
      } else {
        pending.set(to);
        return;
      }
      target.set(to);
      animating.set(true);
      animStart.set(now);
      animFrom.set(x0);
      animV0.set(v0);
      animKind.set(quiet.get() ? FADE : kind);
      pending.set(-1);
    },
    [animFrom, animKind, animStart, animV0, animating, clock, dragging, hi, lo, pending, quiet, target, u],
  );

  const touch = useCallback(() => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === AUTO) {
      mode.set(MANUAL);
      beat.set(0);
      press.set(0);
      // The loop holds a screen as the end of a pair (u = 0 or 1): make that a settled screen, so
      // a drag starts from it. A running morph keeps going to its screen.
      if (lo.get() !== hi.get() && (u.get() < 0.001 || u.get() > 0.999)) {
        const at = u.get() < 0.001 ? lo.get() : hi.get();
        lo.set(at);
        hi.set(at);
        u.set(0);
        target.set(at);
        settledAt.set(clock.get());
        if (published.get() !== at) {
          published.set(at);
          scheduleOnRN(publish, at);
        }
      } else if (lo.get() !== hi.get()) {
        const to = target.get();
        const x = to === hi.get() ? u.get() : 1 - u.get();
        animating.set(true);
        animStart.set(clock.get());
        animFrom.set(x);
        animV0.set(0);
        animKind.set(TAP);
      }
    }
  }, [animFrom, animKind, animStart, animV0, animating, beat, clock, hi, lastTouch, lo, mode, press, publish, published, settledAt, target, u]);

  const selectChip = useCallback(
    (chip: number) => {
      const screen = CHIP_SCREEN[chip] ?? -1;
      scheduleOnUI(() => {
        'worklet';
        touch();
        if (screen < 0) return;
        const settled = lo.get() === hi.get() ? lo.get() : target.get();
        if (settled !== screen) scheduleOnRN(selectionHaptic);
        goTo(screen, TAP, 0);
      });
    },
    [goTo, hi, lo, target, touch],
  );

  // ── the frame loop ──
  const frame = useCallback(
    (info: FrameInfo) => {
      'worklet';
      if (!foreground.get()) {
        lastTs.set(-1);
        return;
      }
      const now = info.timestamp / 1000;
      const prev = lastTs.get();
      lastTs.set(now);
      if (prev < 0) return;
      const t = clock.get() + Math.min(Math.max(now - prev, 0), 0.1);
      clock.set(t);

      if (mode.get() === AUTO) {
        if (!autoShared.get() || quiet.get()) {
          mode.set(MANUAL);
          return;
        }
        const lt = (((t - loopOrigin.get()) % LOOP) + LOOP) % LOOP;
        const b = loopBlend(lt);
        fxTime.set(lt);
        speed.set(loopSpeed(lt));
        beatCount.set(loopBeatCount(lt));
        prevU.set(b.u);
        prevPair.set(b.lo * 3 + b.hi);
        lo.set(b.lo);
        hi.set(b.hi);
        u.set(b.u);
        target.set(b.target);
        beat.set(b.beat);
        press.set(b.press);
        pressChip.set(b.pressChip);
        return;
      }

      // Manual: springs, settling, the heart's beat and the idle return.
      const dt = Math.min(Math.max(now - prev, 0), 0.1);
      fxTime.set(fxTime.get() + dt);
      if (animating.get() && !dragging.get()) {
        const kind = animKind.get();
        const tau = t - animStart.get();
        let x: number;
        let v = 0;
        if (kind === FADE) {
          x = clamp01(animFrom.get() + tau / SPRINGS.reducedFade);
        } else {
          const sp = springFor(kind);
          x = spring(tau, animFrom.get(), animV0.get(), sp.omega, sp.zeta);
          v = springVelocity(tau, animFrom.get(), animV0.get(), sp.omega, sp.zeta);
        }
        const to = target.get();
        const done = kind === FADE ? x >= 1 : tau > 0.1 && Math.abs(1 - x) < 0.0015 && Math.abs(v) < 0.03;
        if (done) {
          lo.set(to);
          hi.set(to);
          u.set(0);
          animating.set(false);
          settledAt.set(t);
          if (published.get() !== to) {
            published.set(to);
            scheduleOnRN(publish, to);
          }
          const next = pending.get();
          if (next >= 0 && next !== to) goTo(next, TAP, 0);
        } else {
          const xc = clamp01(x);
          u.set(to === hi.get() ? xc : 1 - xc);
        }
      }

      const settled = lo.get() === hi.get();
      // The shape's speed, smoothed (a finger's velocity is noisy); 0 across a change of pair.
      const pairNow = lo.get() * 3 + hi.get();
      const raw = pairNow === prevPair.get() && dt > 0 ? (u.get() - prevU.get()) / dt : 0;
      speed.set(speed.get() + (raw - speed.get()) * LIQUID.speedSmoothing);
      prevU.set(u.get());
      prevPair.set(pairNow);
      beatCount.set(settled && lo.get() === 2 && !quiet.get() ? Math.max(t - settledAt.get(), 0) / BEAT : -1);
      beat.set(settled && lo.get() === 2 && !quiet.get() ? pulse((((t - settledAt.get()) / BEAT) % 1 + 1) % 1) * clamp01((t - settledAt.get()) / 0.3) : 0);

      if (autoShared.get() && !quiet.get() && !dragging.get() && !animating.get() && t - lastTouch.get() > RESUME_AFTER) {
        if (settled && lo.get() !== 0) {
          goTo(0, IDLE, 0);
        } else if (settled) {
          mode.set(AUTO);
          loopOrigin.set(t);
        }
      }
    },
    [animFrom, animKind, animStart, animV0, animating, autoShared, beat, beatCount, clock, fxTime, prevPair, prevU, speed, dragging, foreground, goTo, hi, lastTouch, lastTs, lo, loopOrigin, mode, pending, press, pressChip, publish, published, quiet, settledAt, target, u],
  );

  const frameCallback = useFrameCallback(frame, false);
  useEffect(() => {
    lastTs.set(-1);
    frameCallback.setActive(foregroundState);
  }, [frameCallback, foregroundState, lastTs]);

  useEffect(() => {
    quiet.set(quietNow);
    autoShared.set(autoPlay);
    scheduleOnUI(() => {
      'worklet';
      if (quietNow || !autoPlay) {
        if (mode.get() === AUTO) {
          // Reduced motion / no autoPlay: the readable rest, no loop.
          mode.set(MANUAL);
          lo.set(0);
          hi.set(0);
          u.set(0);
          target.set(0);
          beat.set(0);
          press.set(0);
          animating.set(false);
        }
      } else if (mode.get() === MANUAL && lo.get() === 0 && hi.get() === 0 && !animating.get()) {
        mode.set(AUTO);
        loopOrigin.set(clock.get());
      }
    });
  }, [animating, autoPlay, autoShared, beat, clock, hi, lo, loopOrigin, mode, press, quiet, quietNow, target, u]);

  useEffect(() => {
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((on) => mounted && setScreenReader(on));
    const sr = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      mounted = false;
      sub.remove();
      sr.remove();
    };
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      const active = state === 'active';
      setForegroundState(active);
      scheduleOnUI(() => {
        'worklet';
        foreground.set(active);
        lastTs.set(-1);
      });
    });
    return () => sub.remove();
  }, [foreground, lastTs]);

  // ── the hero drag ──
  const span = DRAG.span * k;
  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-14, 14])
    .onBegin(() => {
      touch();
      animating.set(false);
      dragging.set(true);
      dragStartState.set(lo.get() === hi.get() ? lo.get() : -1);
      dragBaseU.set(u.get());
    })
    .onUpdate((e) => {
      const d = e.translationX / span;
      const start = dragStartState.get();
      if (start >= 0) {
        const next = d < 0 ? start + 1 : start - 1;
        if (next < 0 || next > 2) {
          // Rubber band past either end.
          lo.set(start);
          hi.set(start);
          u.set(0);
          const over = e.translationX;
          shift.set((over * DRAG.rubber) / (1 + Math.abs(over) / (DRAG.rubberFalloff * k)));
          return;
        }
        shift.set(0);
        lo.set(Math.min(start, next));
        hi.set(Math.max(start, next));
        target.set(next);
        const p = Math.min(Math.abs(d), 1);
        u.set(next > start ? p : 1 - p);
      } else {
        u.set(clamp01(dragBaseU.get() - d));
      }
    })
    .onEnd((e) => {
      dragging.set(false);
      shift.set(withSpring(0, { duration: 400, dampingRatio: 0.8 }));
      if (lo.get() === hi.get()) return;
      const vu = -e.velocityX / span;
      const forward = vu > DRAG.commitVelocity || (u.get() > DRAG.commitDistance && vu > -DRAG.commitVelocity);
      const to = forward ? hi.get() : lo.get();
      const startState = dragStartState.get();
      if (startState >= 0 && to !== startState) scheduleOnRN(selectionHaptic);
      target.set(to);
      animating.set(true);
      animStart.set(clock.get());
      animFrom.set(to === hi.get() ? u.get() : 1 - u.get());
      animV0.set(to === hi.get() ? vu : -vu);
      animKind.set(quiet.get() ? FADE : RELEASE);
    })
    .onFinalize(() => {
      dragging.set(false);
      lastTouch.set(clock.get());
    });

  const heroShift = useAnimatedStyle(() => ({ transform: [{ translateX: shift.get() }] }));
  const offsetY = insets.top - REF_INSET * k;

  const blendProps = { lo, hi, u, quiet };
  const geometry = { k, s };

  const adjust = (event: { nativeEvent: { actionName: string } }) => {
    const step = event.nativeEvent.actionName === 'increment' ? 1 : -1;
    selectChip(SCREEN_CHIP[Math.min(Math.max(current + step, 0), 2)]);
  };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <View style={{ position: 'absolute', left: 0, right: 0, top: offsetY, bottom: 0 }}>
        <ChipRow c={c} k={k} s={s} onSelect={selectChip} />

        <GestureDetector gesture={pan}>
          <Animated.View
            style={[{ position: 'absolute', left: 0, top: layout.heroTop * k, width, height: layout.heroTouchH * k }, heroShift]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={SCREEN_NAMES[current]}
            accessibilityValue={{ text: SCREEN_VALUES[current] }}
            accessibilityHint="Swipe up or down to switch metric"
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={adjust}
          >
            <Hero width={width} scale={k} lo={lo} hi={hi} u={u} beat={beat} quiet={quiet} fxTime={fxTime} speed={speed} beatCount={beatCount} />
            <View style={[StyleSheet.absoluteFill, { top: -layout.heroTop * k }]} pointerEvents="none">
              <ReadinessNumber c={c} k={k} s={s} />
              <BpmNumber c={c} k={k} s={s} />
            </View>
          </Animated.View>
        </GestureDetector>

        <View style={StyleSheet.absoluteFill} pointerEvents="none" accessible={false} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          <ReadinessPage blend={blendProps} g={geometry} />
          <FitnessPage blend={blendProps} g={geometry} />
          <HeartPage blend={blendProps} g={geometry} />
        </View>

        <TabBar k={k} s={s} />
      </View>
      <PageText current={current} />
    </GestureHandlerRootView>
  );
}

/** Screen-reader copy of the page under the hero (the drawn pages are hidden from accessibility). */
function PageText({ current }: { current: number }) {
  const text = [
    'Ready. You had a good night’s sleep last night and all your other metrics are typical. It looks like you’re rested and ready for today.',
    'Your Exercise Ring Looks Good. Your Exercise ring is ahead of where it usually is by now. Steps 5,253, Cardio Fitness 38 VO2 max.',
    'Cardio Fitness Trending Up. On average, your cardio fitness went up over the last month. Resting heart rate 55 BPM, recovery HRV 54 milliseconds.',
  ][current];
  return <View accessible accessibilityLabel={text} style={styles.srOnly} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.page },
  pill: { position: 'absolute', backgroundColor: colors.chipActive, borderCurve: 'continuous' },
  summaryIcon: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  chipIcon: { position: 'absolute', width: 0, height: 0, alignItems: 'center', justifyContent: 'center', overflow: 'visible' },
  chipTitle: { position: 'absolute', color: colors.muted, fontWeight: '400' },
  chipValue: { position: 'absolute', color: colors.ink, fontWeight: '600' },
  tabBar: { position: 'absolute', overflow: 'hidden', borderWidth: 1, borderColor: colors.tabRim, borderCurve: 'continuous' },
  tabIcon: { position: 'absolute', width: 0, height: 0, alignItems: 'center', justifyContent: 'center', overflow: 'visible' },
  srOnly: { position: 'absolute', left: 0, top: 0, width: 1, height: 1, opacity: 0 },
});
