import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, memo, useCallback, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, type StyleProp, StyleSheet, Text, View, type ViewStyle, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

type Props = {
  /** The daily budget the screen opens on, in dollars; the loop comes back to it. */
  value?: number;
  /** Drags the thumb up and down by itself every LOOP seconds. */
  autoPlay?: boolean;
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  cardLabel?: string;
  nights?: number;
  hint?: string;
  buttonLabel?: string;
  /** Called with the new amount every time the thumb crosses a $5 step. */
  onChange?: (value: number) => void;
  onSubmit?: (value: number) => void;
};

// Palette, the same hex values as the SwiftUI reference.
const PAGE = '#F3F4F9';
const CARD = '#FFFFFF';
const PURPLE = '#5B4FD6';
const TRACK_REST = '#E6E7F2';
const INK = '#1B1B2F';
const BODY = '#6E6E85';
const MUTE = '#9A9AB0';
const ICON_DISC = '#EEEDFB';

// Sizes in pt, the same numbers as the SwiftUI reference.
const MARGIN = 20;
const CARD_TOP = 212;
const CARD_HEIGHT = 164;
const CARD_RADIUS = 22;
const INSET = 22;
const TRACK_WIDTH = 218;
const TRACK_HEIGHT = 6;
const ROW_TOP = 52;
const ROW_HEIGHT = 44;
const PRICE_GAP = 16;
const PRICE_WIDTH = 75;
const THUMB = 24;
const THUMB_GROWN = 28;
const RING = 3;
const PRICE_SIZE = 26;
const DOLLAR_SIZE = 22;
const MIN_VALUE = 40;
const MAX_VALUE = 600;
const STEP = 5;
const SCATTER_Y = 14;
const SCATTER_X = 4;
const SCATTER_ROTATION = 14;
/** Thumb speed (pt/s along the track) that scatters the glyphs fully. */
const FULL_SPEED = 220;
const BUTTON_HEIGHT = 54;
const TILE_TOP = 52;
const TILE_HEIGHT = 84;
const TILE_GAP = 12;

/** The self-playing loop, in seconds: rest, three drags with their settles, rest. */
export const LOOP = 8;
/** The loop's drags: start and end times. The first leaves the configured value, the last returns to it. */
const SEGMENTS: [number, number][] = [
  [0.9, 2.1],
  [3.3, 4.5],
  [5.6, 6.6],
];
/** Where the drags stop in between (as 0…1 along the track): $520, then $95. */
const STOPS = [0.857, 0.098];
const WOBBLE_FOR = 0.8;
const SMOOTHING = 0.05;
const RELEASE_RESPONSE = 0.42;
const RELEASE_DAMPING = 0.55;
const GROW = 0.2;
const SHRINK = 0.25;
const RESUME = 2.0;
const RETURN_FOR = 0.6;

const MODE_LOOP = 0;
const MODE_TOUCHING = 1;
const MODE_RELEASED = 2;
const MODE_RETURNING = 3;

/** How far a glyph leaves its place at full amplitude: y, x and rotation factors, counted from the right. */
const DIGIT_FACTORS: [number, number, number][] = [
  [-1.0, 0.6, 1.0],
  [0.7, -0.4, -0.8],
  [-0.55, 0.5, 0.6],
];
const DOLLAR_FACTOR: [number, number, number] = [0.35, -0.2, -0.4];

const TILES: { label: string; icon: 'restaurant' | 'bus' | 'ticket'; share: number }[] = [
  { label: 'Food', icon: 'restaurant', share: 0.4 },
  { label: 'Transport', icon: 'bus', share: 0.2 },
  { label: 'Tickets', icon: 'ticket', share: 0.4 },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutSine(x: number) {
  'worklet';
  return (1 - Math.cos(Math.PI * x)) / 2;
}
function easeInOutSineSlope(x: number) {
  'worklet';
  return (Math.PI / 2) * Math.sin(Math.PI * x);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
/** The glyphs' overshoot after a drag stops: starts at 0, swings the other way and dies out. */
function wobble(e: number, sign: number, peak: number) {
  'worklet';
  if (e < 0 || e >= WOBBLE_FOR) return 0;
  return -0.18 * sign * Math.min(1, peak) * Math.sin(2 * Math.PI * 2 * e) * Math.exp(-6 * e);
}
/** The amplitude after a release: a damped spring from `a0` to 0. */
function released(a0: number, e: number) {
  'worklet';
  const omega = (2 * Math.PI) / RELEASE_RESPONSE;
  const zeta = RELEASE_DAMPING;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return a0 * Math.exp(-zeta * omega * e) * (Math.cos(wd * e) + ((zeta * omega) / wd) * Math.sin(wd * e));
}
function amountAt(p: number) {
  'worklet';
  const raw = MIN_VALUE + (MAX_VALUE - MIN_VALUE) * clamp01(p);
  return Math.round(raw / STEP) * STEP;
}
function placeOf(value: number) {
  'worklet';
  return clamp01((value - MIN_VALUE) / (MAX_VALUE - MIN_VALUE));
}
/** One eased drag from `from` to `to` over `start…end`: the place and the amplitude at `t`. */
function dragAt(t: number, start: number, end: number, from: number, to: number) {
  'worklet';
  const u = seg(t, start, end);
  const p = from + (to - from) * easeInOutSine(u);
  const speed = (((to - from) * easeInOutSineSlope(u)) / (end - start)) * TRACK_WIDTH;
  return { p, a: Math.max(-1, Math.min(1, speed / FULL_SPEED)) };
}
/** The self-playing loop at `t`, leaving from and coming back to `home` (the configured value's place). */
function loopFrame(t: number, home: number) {
  'worklet';
  let p = home;
  let a = 0;
  for (let i = 0; i < SEGMENTS.length; i += 1) {
    const s = SEGMENTS[i];
    const from = i === 0 ? home : STOPS[i - 1];
    const to = i === SEGMENTS.length - 1 ? home : STOPS[i];
    if (t >= s[0] && t < s[1]) {
      const d = dragAt(t, s[0], s[1], from, to);
      p = d.p;
      a = d.a;
    } else if (t >= s[1]) {
      p = to;
      const peak = ((Math.abs(to - from) * Math.PI) / 2 / (s[1] - s[0])) * (TRACK_WIDTH / FULL_SPEED);
      a += wobble(t - s[1], to > from ? 1 : -1, peak);
    }
  }
  return { p, a, grow: 0 };
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}
function grouped(value: number) {
  return value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** The price in $5 steps as React state, for the few small parts that print it; it changes a few times a second at most. */
function useAmount(p: SharedValue<number>) {
  const [amount, setAmount] = useState(() => amountAt(p.get()));
  useAnimatedReaction(
    () => amountAt(p.get()),
    (now, was) => {
      if (now !== was) scheduleOnRN(setAmount, now);
    },
  );
  return amount;
}

/**
 * Counts touches so the resume timer restarts after each. `generation` is the same count bumped synchronously, so a
 * timer an earlier touch armed can tell it was superseded even when it fires before React re-ran the effect.
 * A hook, so the component gets `touched` as an opaque callback it may hand to gesture worklets via `scheduleOnRN`.
 */
function useTouchGeneration() {
  const [touches, setTouches] = useState(0);
  const generation = useRef(0);
  const touched = useCallback(() => {
    generation.current += 1;
    setTouches((n) => n + 1);
  }, []);
  return { touches, generation, touched };
}

/** A budget slider whose price readout breaks into glyphs that scatter with the thumb's speed and settle when it stops. */
export function PriceGlyphScatterSlider({
  value = 180,
  autoPlay = false,
  eyebrow = 'Weekender',
  title = 'Daily budget',
  subtitle = 'How much would you like to spend a day in Lisbon? Food, transport and tickets.',
  cardLabel = 'Per day',
  nights = 3,
  hint = 'Drag the dot — the price settles when you let go.',
  buttonLabel = 'Set budget',
  onChange,
  onSubmit,
}: Props) {
  const reducedMotion = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const width = laidOut?.width ?? window.width;
  const height = laidOut?.height ?? window.height;
  const cardWidth = width - MARGIN * 2;
  const homeP = placeOf(value);
  // Counts touches so the resume timer restarts after each; the price itself never passes through this component's state.
  const { touches, generation, touched } = useTouchGeneration();
  /** A finger on the button: the block holds where it is until it lifts. */
  const pressing = useRef(false);

  const p = useSharedValue(homeP);
  const amplitude = useSharedValue(0);
  const grow = useSharedValue(0);
  const mode = useSharedValue(MODE_LOOP);
  /** Where the thumb rests: the configured value, or the value the user chose when the block does not play by itself. */
  const restP = useSharedValue(homeP);
  const modeP = useSharedValue(homeP);
  const modeA = useSharedValue(0);
  const modeGrow = useSharedValue(0);
  const modeAt = useSharedValue(0);
  const origin = useSharedValue(0);
  const originPending = useSharedValue(true);
  const clock = useSharedValue(0);
  const touchP = useSharedValue(homeP);
  const lastP = useSharedValue(homeP);
  const lastTime = useSharedValue(-1);
  const smoothedSpeed = useSharedValue(0);
  const touchStart = useSharedValue(0);
  const grip = useSharedValue(0);
  const lastTick = useSharedValue(Math.floor(amountAt(homeP) / 50));

  const changed = (next: number) => onChange?.(next);

  // A controlled parent moves the rest explicitly; a touch in progress keeps going.
  useEffect(() => {
    restP.set(homeP);
  }, [homeP, restP]);

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    clock.set(now);
    if (originPending.get()) {
      originPending.set(false);
      origin.set(now);
    }
    const m = mode.get();
    if (m === MODE_LOOP) {
      if (!autoPlay || reducedMotion) {
        // At rest: write only when something differs, so no animated style re-evaluates per frame.
        const rest = restP.get();
        if (p.get() !== rest) p.set(rest);
        if (amplitude.get() !== 0) amplitude.set(0);
        if (grow.get() !== 0) grow.set(0);
      } else {
        const f = loopFrame(Math.max(0, now - origin.get()) % LOOP, restP.get());
        p.set(f.p);
        amplitude.set(f.a);
        grow.set(0);
      }
    } else if (m === MODE_TOUCHING) {
      // The thumb's speed, smoothed, drives the scatter; the value ticks by $50.
      const dt = lastTime.get() < 0 ? 1 / 60 : Math.min(0.064, Math.max(0.001, now - lastTime.get()));
      lastTime.set(now);
      const speed = ((touchP.get() - lastP.get()) * TRACK_WIDTH) / dt;
      lastP.set(touchP.get());
      smoothedSpeed.set(smoothedSpeed.get() + (speed - smoothedSpeed.get()) * (1 - Math.exp(-dt / SMOOTHING)));
      const step = Math.floor(amountAt(touchP.get()) / 50);
      if (step !== lastTick.get()) {
        lastTick.set(step);
        scheduleOnRN(tick);
      }
      p.set(touchP.get());
      amplitude.set(reducedMotion ? 0 : Math.max(-1, Math.min(1, smoothedSpeed.get() / FULL_SPEED)));
      grow.set(reducedMotion ? 1 : easeOutCubic(seg(now - touchStart.get(), 0, GROW)));
    } else if (m === MODE_RELEASED) {
      const e = now - modeAt.get();
      p.set(modeP.get());
      amplitude.set(reducedMotion ? 0 : released(modeA.get(), e));
      grow.set(reducedMotion ? 0 : modeGrow.get() * (1 - seg(e, 0, SHRINK)));
    } else {
      const d = dragAt(now - modeAt.get(), 0, RETURN_FOR, modeP.get(), restP.get());
      p.set(d.p);
      amplitude.set(reducedMotion ? 0 : d.a);
      grow.set(0);
    }
  });

  /** Holds the thumb at `at`: the scatter springs down from `a0`, the thumb shrinks from `grow0`. */
  const hold = (at: number, a0: number, grow0: number) => {
    modeP.set(at);
    modeA.set(a0);
    modeGrow.set(grow0);
    modeAt.set(clock.get());
    mode.set(MODE_RELEASED);
  };

  /**
   * Any touch outside the slider (the button) holds the block where it is, so it rests and resumes like a drag:
   * the playing loop, or a glide home that a resume started. The frame is captured as it is, so nothing jumps.
   */
  const pauseLoop = () => {
    const m = mode.get();
    if (m === MODE_LOOP ? !autoPlay || reducedMotion : m !== MODE_RETURNING) return;
    hold(p.get(), amplitude.get(), grow.get());
  };

  // 2 s after the last touch: a playing block glides home and its loop starts again from rest; a still one keeps the chosen value.
  // A finger on the button keeps the hold; the release counts as the last touch. A timer another touch superseded
  // does nothing, even when it fires before the effect's cleanup has cleared it.
  useEffect(() => {
    if (touches === 0) return;
    const armed = generation.current;
    const timers: ReturnType<typeof setTimeout>[] = [];
    timers.push(
      setTimeout(() => {
        if (generation.current !== armed || pressing.current || mode.get() !== MODE_RELEASED) return;
        if (autoPlay && Math.abs(modeP.get() - restP.get()) > 0.001) {
          modeAt.set(clock.get());
          mode.set(MODE_RETURNING);
          timers.push(
            setTimeout(() => {
              if (generation.current !== armed || mode.get() !== MODE_RETURNING) return;
              originPending.set(true);
              mode.set(MODE_LOOP);
            }, (RETURN_FOR + 0.05) * 1000),
          );
        } else {
          if (!autoPlay) restP.set(modeP.get());
          originPending.set(true);
          mode.set(MODE_LOOP);
        }
      }, RESUME * 1000),
    );
    return () => timers.forEach(clearTimeout);
  }, [touches, generation, autoPlay, mode, modeP, modeAt, clock, originPending, restP]);

  // Any touch on the row takes over, a tap included: onBegin fires on the touch down, onFinalize on its end, failure or cancellation.
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const current = p.get();
      const at = (event.x - INSET) / TRACK_WIDTH;
      grip.set(Math.abs(at - current) * TRACK_WIDTH <= THUMB ? at - current : 0);
      touchP.set(current);
      lastP.set(current);
      smoothedSpeed.set(0);
      lastTime.set(-1);
      touchStart.set(clock.get());
      lastTick.set(Math.floor(amountAt(current) / 50));
      mode.set(MODE_TOUCHING);
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      const next = clamp01((event.x - INSET) / TRACK_WIDTH - grip.get());
      if (amountAt(next) !== amountAt(touchP.get())) scheduleOnRN(changed, amountAt(next));
      touchP.set(next);
    })
    .onFinalize(() => {
      if (mode.get() !== MODE_TOUCHING) return;
      modeP.set(touchP.get());
      modeA.set(reducedMotion ? 0 : Math.max(-1, Math.min(1, smoothedSpeed.get() / FULL_SPEED)));
      modeGrow.set(1);
      modeAt.set(clock.get());
      mode.set(MODE_RELEASED);
      scheduleOnRN(touched);
    });

  const fillStyle = useAnimatedStyle(() => ({ width: Math.max(TRACK_HEIGHT, p.get() * TRACK_WIDTH) }));
  const thumbStyle = useAnimatedStyle(() => {
    const size = THUMB + (THUMB_GROWN - THUMB) * grow.get();
    return {
      transform: [{ translateX: INSET + p.get() * TRACK_WIDTH - THUMB_GROWN / 2 }, { translateY: ROW_HEIGHT / 2 - THUMB_GROWN / 2 }, { scale: size / THUMB_GROWN }],
    };
  });

  const onLayout = (event: LayoutChangeEvent) => setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  const adjust = (direction: 1 | -1) => {
    const next = clamp01(p.get() + (direction * STEP * 5) / (MAX_VALUE - MIN_VALUE));
    hold(next, 0, 0);
    changed(amountAt(next));
    touched();
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={[styles.back, { top: insets.top + 12 }]}>
        <Ionicons name="chevron-back" size={16} color={INK} />
      </View>
      <Text style={[styles.eyebrow, { top: insets.top + 72 }]}>{eyebrow.toUpperCase()}</Text>
      <Text style={[styles.title, { top: insets.top + 92 }]} numberOfLines={1}>{title}</Text>
      <Text style={[styles.subtitle, { top: insets.top + 134, width: cardWidth - 8 }]} numberOfLines={2}>{subtitle}</Text>

      <BudgetCard p={p} title={title} onAdjust={adjust} style={[styles.card, { top: insets.top + CARD_TOP, width: cardWidth }]}>
        <Text style={styles.cardLabel}>{cardLabel.toUpperCase()}</Text>
        <GestureDetector gesture={pan}>
          <View style={styles.row} hitSlop={12}>
            <View style={styles.trackRest} />
            <Animated.View style={[styles.trackFill, fillStyle]} />
            <Animated.View style={[styles.thumb, thumbStyle]}>
              <View style={styles.thumbDot} />
            </Animated.View>
            <Readout p={p} amplitude={amplitude} />
          </View>
        </GestureDetector>
        <Text style={[styles.limit, { left: INSET }]}>${MIN_VALUE}</Text>
        <Text style={[styles.limit, { left: INSET, width: TRACK_WIDTH, textAlign: 'right' }]}>${MAX_VALUE}</Text>
        <TotalLine p={p} nights={nights} />
      </BudgetCard>

      <Text style={[styles.hint, { top: insets.top + CARD_TOP + CARD_HEIGHT + 18, width: cardWidth }]} numberOfLines={2}>{hint}</Text>

      {/* Where a day's money goes, following the slider. */}
      <View style={[styles.tiles, { top: insets.top + CARD_TOP + CARD_HEIGHT + TILE_TOP, width: cardWidth }]}>
        {TILES.map((tile) => (
          <View key={tile.label} style={[styles.tile, { width: (cardWidth - TILE_GAP * 2) / 3 }]}>
            <View style={styles.tileIcon}>
              <Ionicons name={tile.icon} size={13} color={PURPLE} />
            </View>
            <View style={styles.flex} />
            <Text style={styles.tileLabel} numberOfLines={1}>{tile.label}</Text>
            <TileAmount p={p} share={tile.share} />
          </View>
        ))}
      </View>

      {/* The press holds the block from the touch down to the lift or cancel, which arms the 2 s resume; only a completed
          press submits. An activation without a press (a screen reader) pauses and arms from onPress itself. */}
      <Pressable
        accessibilityRole="button"
        style={({ pressed }) => [styles.button, { top: height - Math.max(insets.bottom, 16) - 20 - BUTTON_HEIGHT, width: cardWidth, opacity: pressed ? 0.8 : 1 }]}
        onPressIn={() => {
          pressing.current = true;
          pauseLoop();
        }}
        onPressOut={() => {
          pressing.current = false;
          touched();
        }}
        onPress={() => {
          pauseLoop();
          touched();
          onSubmit?.(amountAt(p.get()));
        }}
      >
        <Text style={styles.buttonLabel}>{buttonLabel}</Text>
      </Pressable>
    </View>
  );
}

/** The card's shell: it alone reads the amount for accessibility, so its children stay as they are while the price changes. */
const BudgetCard = memo(function BudgetCard({ p, title, onAdjust, style, children }: { p: SharedValue<number>; title: string; onAdjust: (direction: 1 | -1) => void; style: StyleProp<ViewStyle>; children: ReactNode }) {
  const amount = useAmount(p);
  return (
    <View
      style={style}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={title}
      accessibilityValue={{ text: `$${amount} per day` }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(event) => onAdjust(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
    >
      {children}
    </View>
  );
});

/** "≈ $540 for 3 nights", following the amount on its own. */
const TotalLine = memo(function TotalLine({ p, nights }: { p: SharedValue<number>; nights: number }) {
  const amount = useAmount(p);
  return (
    <Text style={styles.total}>
      ≈ <Text style={styles.totalStrong}>${grouped(amount * nights)}</Text> for {nights} nights
    </Text>
  );
});

/** One tile's share of the day, following the amount on its own. */
const TileAmount = memo(function TileAmount({ p, share }: { p: SharedValue<number>; share: number }) {
  const amount = useAmount(p);
  return <Text style={styles.tileAmount} numberOfLines={1}>${Math.round(amount * share)}</Text>;
});

/** The price as separate glyphs, laid out right to left; each leaves its place by the amplitude. */
const Readout = memo(function Readout({ p, amplitude }: { p: SharedValue<number>; amplitude: SharedValue<number> }) {
  const amount = useAmount(p);
  const [digitWidth, setDigitWidth] = useState(0);
  const [dollarWidth, setDollarWidth] = useState(0);
  const digits = String(amount).split('');
  return (
    <View style={styles.readout} pointerEvents="none">
      {/* The glyph widths, measured once so the digits keep their slots as they change. */}
      <Text style={[styles.digit, styles.measure]} onLayout={(e) => setDigitWidth(Math.ceil(e.nativeEvent.layout.width))}>0</Text>
      <Text style={[styles.dollar, styles.measure]} onLayout={(e) => setDollarWidth(Math.ceil(e.nativeEvent.layout.width))}>$</Text>
      <Glyph text="$" width={dollarWidth} dollar factor={DOLLAR_FACTOR} amplitude={amplitude} />
      {digits.map((ch, index) => (
        <Glyph key={`${index}-${digits.length}`} text={ch} width={digitWidth} factor={DIGIT_FACTORS[Math.min(digits.length - 1 - index, DIGIT_FACTORS.length - 1)]} amplitude={amplitude} />
      ))}
    </View>
  );
});

function Glyph({ text, width, dollar = false, factor, amplitude }: { text: string; width: number; dollar?: boolean; factor: [number, number, number]; amplitude: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const a = amplitude.get();
    const m = Math.abs(a);
    return {
      opacity: 1 - 0.15 * m,
      transform: [{ translateX: -SCATTER_X * a * factor[1] }, { translateY: SCATTER_Y * m * factor[0] }, { rotate: `${SCATTER_ROTATION * a * factor[2]}deg` }],
    };
  });
  return (
    <Animated.View style={[styles.glyph, { width: width || undefined }, style]}>
      <Text style={dollar ? styles.dollar : styles.digit}>{text}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  flex: { flex: 1 },
  back: { position: 'absolute', left: MARGIN, width: 36, height: 36, borderRadius: 18, backgroundColor: CARD, alignItems: 'center', justifyContent: 'center' },
  eyebrow: { position: 'absolute', left: MARGIN + 4, color: PURPLE, fontSize: 12, fontWeight: '700', letterSpacing: 1.6 },
  title: { position: 'absolute', left: MARGIN + 4, color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.4, lineHeight: 36 },
  subtitle: { position: 'absolute', left: MARGIN + 4, color: BODY, fontSize: 15, lineHeight: 21 },
  card: {
    position: 'absolute', left: MARGIN, height: CARD_HEIGHT, borderRadius: CARD_RADIUS, borderCurve: 'continuous', backgroundColor: CARD,
    boxShadow: '0 10px 30px rgba(60,60,120,0.12), 0 1px 2px rgba(60,60,120,0.06)',
  },
  cardLabel: { position: 'absolute', left: INSET, top: INSET, color: MUTE, fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  row: { position: 'absolute', left: 0, top: ROW_TOP, width: INSET + TRACK_WIDTH + PRICE_GAP + PRICE_WIDTH, height: ROW_HEIGHT },
  trackRest: { position: 'absolute', left: INSET, top: ROW_HEIGHT / 2 - TRACK_HEIGHT / 2, width: TRACK_WIDTH, height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, backgroundColor: TRACK_REST },
  trackFill: { position: 'absolute', left: INSET, top: ROW_HEIGHT / 2 - TRACK_HEIGHT / 2, height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, backgroundColor: PURPLE },
  thumb: {
    position: 'absolute', left: 0, top: 0, width: THUMB_GROWN, height: THUMB_GROWN, borderRadius: THUMB_GROWN / 2, backgroundColor: CARD, padding: RING,
    boxShadow: '0 3px 8px rgba(91,79,214,0.35)',
  },
  thumbDot: { flex: 1, borderRadius: THUMB_GROWN / 2, backgroundColor: PURPLE },
  readout: { position: 'absolute', left: INSET + TRACK_WIDTH + PRICE_GAP, top: 0, width: PRICE_WIDTH, height: ROW_HEIGHT, flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center' },
  measure: { position: 'absolute', opacity: 0, left: 0, top: 0 },
  glyph: { height: ROW_HEIGHT, alignItems: 'center', justifyContent: 'center' },
  digit: { color: INK, fontSize: PRICE_SIZE, fontWeight: '600', fontVariant: ['tabular-nums'] },
  dollar: { color: MUTE, fontSize: DOLLAR_SIZE, fontWeight: '600' },
  limit: { position: 'absolute', top: 108, color: MUTE, fontSize: 12, fontWeight: '500' },
  total: { position: 'absolute', left: INSET, top: 128, color: BODY, fontSize: 13 },
  totalStrong: { color: INK, fontWeight: '600' },
  hint: { position: 'absolute', left: MARGIN, color: MUTE, fontSize: 13, textAlign: 'center' },
  tiles: { position: 'absolute', left: MARGIN, flexDirection: 'row', gap: TILE_GAP },
  tile: { height: TILE_HEIGHT, borderRadius: 16, borderCurve: 'continuous', backgroundColor: CARD, padding: 14, boxShadow: '0 4px 16px rgba(60,60,120,0.08)' },
  tileIcon: { width: 28, height: 28, borderRadius: 14, backgroundColor: ICON_DISC, alignItems: 'center', justifyContent: 'center' },
  tileLabel: { color: MUTE, fontSize: 12 },
  tileAmount: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'], marginTop: 2 },
  button: { position: 'absolute', left: MARGIN, height: BUTTON_HEIGHT, borderRadius: 18, borderCurve: 'continuous', backgroundColor: PURPLE, alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
});
