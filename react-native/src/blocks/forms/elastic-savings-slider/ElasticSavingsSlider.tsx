import { Canvas, Group, LinearGradient, Path, Rect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type SavingOption = {
  label: string;
  /** What it takes off the yearly bill, in units of the currency. */
  saving: number;
};

type Props = {
  /** The yearly bill with nothing switched on. */
  bill?: number;
  /** Written before an amount. */
  symbol?: string;
  /**
   * The options in the order the slider switches them on. The grid shows them in pairs from its
   * bottom row up, so the first two are its last row.
   */
  options?: SavingOption[];
  address?: string;
  hint?: string;
  fineTuneLabel?: string;
  billLabel?: string;
  reportLabel?: string;
  /** Drags the slider by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with which options are on, in the order of `options`, and the bill that leaves. */
  onChange?: (on: boolean[], bill: number) => void;
  onFineTune?: () => void;
  onReport?: (on: boolean[], bill: number) => void;
};

const OPTIONS: SavingOption[] = [
  { label: 'Daily savings', saving: 35 },
  { label: 'LED lighting', saving: 70 },
  { label: 'Thermostat', saving: 53 },
  { label: 'Smart thermostat', saving: 76 },
  { label: 'Solar panels', saving: 407 },
  { label: 'Heat pump', saving: 222 },
];

const BACKDROP = '#202622';
const BANNER_FILL = '#333e38';
const CARD_FILL = '#fbfcf7';
const SAGE = '#edf0e7';
const SAGE_PRESSED = '#e1e6da';
const CORAL = '#f65a3a';
const INK = '#1d231f';
const RING = '#cbd6ce';
const STOP_DOT = '#515753';

const SIDE = 18;
const BANNER = 92;
const PICTURE = 64;
const CARD_PADDING = 25;
const CARD_RADIUS = 46;
const PANEL = 180;
const AMOUNT = 86;
// The system's heaviest weight, drawn taller than it is: the bill reads as a poster face.
const AMOUNT_STRETCH = 1.24;
const CHIP = 44;
const CHIP_GAP = 6;
const CHECK = 22;
const TRACK = 60;
const TRACK_INSET = 5;
// The fill at rest with nothing on: a disc.
const THUMB = TRACK - TRACK_INSET * 2;
// The bolt sits this far inside the fill's leading end.
const BOLT_INSET = THUMB / 2;
const STOP_DOTS = 4;
const BUTTON = 60;

const NEVER = ReduceMotion.Never;
// The fill's end is the finger; the bolt follows it on this spring and so trails behind.
const BOLT_SPRING = { damping: 13, stiffness: 150, mass: 1, reduceMotion: NEVER };
// Let go, the fill snaps to the nearest stop and wobbles there.
const SNAP_SPRING = { damping: 11, stiffness: 210, mass: 1, reduceMotion: NEVER };
// While the bolt trails by `lag` points the fill thins, and the bolt leans back and grows.
const THIN_PER_POINT = 0.2;
const THIN_MOST = 9;
const LEAN_PER_POINT = 0.9;
const LEAN_MOST = 28;
// Past either end the fill gives this many points at most.
const GIVE = 14;
const CHECK_SPRING = { damping: 15, stiffness: 260, mass: 0.8, reduceMotion: NEVER };
const CHIP_MS = 200;
// A digit that changes lands squashed to this height and width, then springs up.
const SQUASH_Y = 0.72;
const SQUASH_X = 1.1;
const DIGIT_SPRING = { damping: 9, stiffness: 300, mass: 0.7, reduceMotion: NEVER };
const COUNT_MS = 360;

/** autoPlay: one round every LOOP seconds. Each drag starts at `at`, lasts `lasts`, ends with `to` options on. */
export const LOOP = 7.5;
const DRAGS = [
  { at: 0.8, to: 1, lasts: 0.35 },
  { at: 1.9, to: 2, lasts: 0.3 },
  { at: 2.4, to: 4, lasts: 0.55 },
  { at: 3.8, to: 5, lasts: 0.35 },
  { at: 4.45, to: 6, lasts: 0.35 },
  { at: 6, to: 0, lasts: 0.55 },
];
/** After a touch autoPlay waits this long before it starts over. */
const USER_HOLD = 3;

// A lightning bolt in an 18 × 22 box, and a check in a 22 × 22 one.
const BOLT = 'M11.2 1L2.6 12.6L8.3 12.6L6.6 21L15.4 9.2L9.7 9.2Z';
const CHECK_MARK = 'M6.4 11.4L9.7 14.6L15.7 8';
// The house in the banner, in its 64 × 64 box: a dark roof with its orange edge over a glass
// wall, and a lower wing in front.
const ROOF = 'M0 20L45 11L49 16L0 28Z';
const ROOF_EDGE = 'M0 28L49 16L49 21L0 34Z';
const GLASS = 'M0 34L38 24.5L38 64L0 64Z';
const MULLIONS = 'M12 31L12 64M25 27.8L25 64M0 47L38 42';
const WING_ROOF = 'M28 33L64 28L64 36L28 40Z';
const WING = 'M31 40L64 36L64 64L31 64Z';
const PICTURE_CLIP = Skia.RRectXY(Skia.XYWHRect(0, 0, PICTURE, PICTURE), 18, 18);

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One character of the bill. A new one lands squashed on the baseline and springs up. */
function Digit({ char, still }: { char: string; still: boolean }) {
  const risen = useSharedValue(1);
  const first = useRef(true);

  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    risen.set(0);
    risen.set(still ? withTiming(1, { duration: 160, reduceMotion: NEVER }) : withSpring(1, DIGIT_SPRING));
  }, [char, still, risen]);

  const style = useAnimatedStyle(() => {
    const r = risen.get();
    if (still) return { opacity: r };
    return { transform: [{ scaleX: SQUASH_X + (1 - SQUASH_X) * r }, { scaleY: SQUASH_Y + (1 - SQUASH_Y) * r }] };
  });

  return <Animated.Text style={[styles.digit, style]}>{char}</Animated.Text>;
}

function Chip({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  const popped = useSharedValue(on ? 1 : 0);

  useEffect(() => {
    popped.set(on ? withSpring(1, CHECK_SPRING) : withTiming(0, { duration: 140, reduceMotion: NEVER }));
  }, [on, popped]);

  const checkStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, popped.get() * 3), transform: [{ scale: popped.get() }] }));

  return (
    <Pressable style={styles.chipSlot} accessibilityRole="switch" accessibilityState={{ checked: on }} accessibilityLabel={label} onPress={onPress}>
      {({ pressed }) => (
        <Animated.View style={[styles.chip, { backgroundColor: pressed ? SAGE_PRESSED : SAGE, transitionProperty: 'backgroundColor', transitionDuration: 120 }]}>
          <Animated.Text
            style={[styles.chipLabel, { opacity: on ? 1 : 0.16, transitionProperty: 'opacity', transitionDuration: CHIP_MS }]}
            numberOfLines={1}
          >
            {label}
          </Animated.Text>
          <View style={styles.ring} />
          <Animated.View style={[styles.check, checkStyle]}>
            <Canvas style={styles.checkMark}>
              <Path path={CHECK_MARK} style="stroke" strokeWidth={2.6} strokeCap="round" strokeJoin="round" color="#ffffff" />
            </Canvas>
          </Animated.View>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A savings estimator: six options, a giant bill and one fat slider. Dragging the slider's
 * elastic fill past each stop switches the next option on, counts the saving up and brings the
 * bill down a step, its heavy digits landing with a squash; dragging back switches them off, and
 * a tap on an option toggles it alone. Nothing is computed elsewhere: the savings are the props.
 */
export function ElasticSavingsSlider({
  bill = 1326,
  symbol = '$',
  options = OPTIONS,
  address = '13 Alondra Blvd',
  hint = 'Fine-tune your home\nfor more accurate results',
  fineTuneLabel = 'Fine-tune',
  billLabel = 'Estimated Annual Bill',
  reportLabel = 'See your full report',
  autoPlay = false,
  onChange,
  onFineTune,
  onReport,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const count = options.length;
  const [on, setOn] = useState<boolean[]>(() => options.map(() => false));
  // The saving on the small chip while it counts.
  const [counted, setCounted] = useState(0);
  // The slider's track as laid out.
  const [trackWidth, setTrackWidth] = useState(316);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const room = trackWidth - TRACK_INSET * 2;
  // The fill grows by this much for each option.
  const step = (room - THUMB) / count;

  // The fill's width, from a disc to the whole track.
  const reach = useSharedValue(THUMB);
  // The slider itself is being dragged, by a finger or by autoPlay: only then do its stops switch options.
  const driving = useSharedValue(false);
  const byHand = useSharedValue(false);
  // Where the finger took the fill's end, relative to it.
  const grip = useSharedValue(0);
  const saved = useSharedValue(0);

  const saving = options.reduce((sum, option, index) => sum + (on[index] ? option.saving : 0), 0);
  const left = Math.max(0, bill - saving);
  const amount = `${symbol}${left}`;

  const report = useRef({ onChange, on, left });
  useEffect(() => {
    report.current = { onChange, on, left };
  });
  useEffect(() => {
    report.current.onChange?.(report.current.on, report.current.left);
  }, [on]);

  useEffect(() => {
    saved.set(withTiming(saving, { duration: COUNT_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
  }, [saving, saved]);

  useAnimatedReaction(
    () => Math.round(saved.get()),
    (now, old) => {
      if (now !== old) scheduleOnRN(setCounted, now);
    },
  );

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  /** The slider reached a stop: the options up to it are on, the rest off. */
  const arrive = (level: number, hand: boolean) => {
    if (hand) tick();
    setOn(options.map((_, index) => index < level));
  };

  useAnimatedReaction(
    () => Math.min(count, Math.max(0, Math.round((reach.get() - THUMB) / step))),
    (now, old) => {
      if (old !== null && now !== old && driving.get()) scheduleOnRN(arrive, now, byHand.get());
    },
  );

  /** What autoPlay does: carries the fill's end to a stop as a finger would. */
  const dragTo = (level: number, seconds: number) => {
    driving.set(true);
    byHand.set(false);
    reach.set(withTiming(THUMB + level * step, { duration: seconds * 1000, easing: Easing.inOut(Easing.quad), reduceMotion: NEVER }));
  };

  /** A tap on an option switches it alone; the fill then shows how many are on. */
  const toggle = (index: number) => {
    touched();
    tick();
    const next = on.map((value, i) => (i === index ? !value : value));
    setOn(next);
    driving.set(false);
    reach.set(withSpring(THUMB + next.filter(Boolean).length * step, SNAP_SPRING));
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      cancelAnimation(reach);
      driving.set(true);
      byHand.set(true);
      const end = TRACK_INSET + reach.get();
      // A touch on the fill's end keeps hold of it; a touch anywhere else calls it over.
      const onEnd = event.x > end - THUMB - 8 && event.x < end + 16;
      grip.set(onEnd ? end - event.x : BOLT_INSET);
      if (!onEnd) {
        const to = Math.min(room, Math.max(THUMB, event.x - TRACK_INSET + BOLT_INSET));
        reach.set(withTiming(to, { duration: 140, easing: Easing.out(Easing.quad), reduceMotion: NEVER }));
      }
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      const to = event.x - TRACK_INSET + grip.get();
      // Past either end the fill stretches a little, like elastic, and no further.
      const over = to < THUMB ? to - THUMB : to > room ? to - room : 0;
      reach.set(to - over + (GIVE * over) / (GIVE + Math.abs(over)));
    })
    .onFinalize(() => {
      const level = Math.min(count, Math.max(0, Math.round((reach.get() - THUMB) / step)));
      reach.set(withSpring(THUMB + level * step, SNAP_SPRING));
      scheduleOnRN(touched);
    });

  // The loop reads the latest layout when its timers fire.
  const latest = useRef({ dragTo });
  useEffect(() => {
    latest.current = { dragTo };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      DRAGS.forEach(({ at, to, lasts }) => after(origin + at * 1000 - Date.now(), () => latest.current.dragTo(to, lasts)));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      // The loop starts over with everything off.
      after(USER_HOLD * 1000, () => latest.current.dragTo(0, 0.5));
      after(USER_HOLD * 1000 + 1300, () => cycle(Date.now()));
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  useEffect(() => () => cancelAnimation(reach), [reach]);

  // The bolt chases the fill's end; how far it trails is what stretches the fill.
  const bolt = useDerivedValue(() => (reducedMotion ? reach.get() : withSpring(reach.get(), BOLT_SPRING)));

  const fillStyle = useAnimatedStyle(() => {
    const lag = reach.get() - bolt.get();
    const height = THUMB - Math.min(THIN_MOST, Math.abs(lag) * THIN_PER_POINT);
    return { width: Math.max(height, reach.get()), height, borderRadius: height / 2, top: (TRACK - height) / 2 };
  });
  const boltStyle = useAnimatedStyle(() => {
    const lag = reach.get() - bolt.get();
    const lean = Math.max(-LEAN_MOST, Math.min(LEAN_MOST, lag * LEAN_PER_POINT));
    return {
      // The bolt never leaves the fill: going back it rides the end, leaning the other way.
      transform: [
        { translateX: Math.max(THUMB, Math.min(bolt.get(), reach.get())) - BOLT_INSET },
        { rotate: `${-lean}deg` },
        { scale: 1 + Math.abs(lean) / 160 },
      ],
    };
  });

  const onTrack = (event: LayoutChangeEvent) => setTrackWidth(event.nativeEvent.layout.width);

  // The grid shows the options in pairs from its bottom row up.
  const rows: number[][] = [];
  for (let index = 0; index < count; index += 2) rows.unshift(index + 1 < count ? [index, index + 1] : [index]);
  const digits = String(left).split('');
  const level = on.filter(Boolean).length;

  return (
    <View style={[styles.screen, { paddingTop: insets.top, paddingBottom: Math.max(insets.bottom, 12) }]} onTouchStart={touched}>
      <StatusBar style="light" />

      <View style={styles.banner}>
        <Canvas style={styles.picture}>
          <Group clip={PICTURE_CLIP}>
            <Rect x={0} y={0} width={PICTURE} height={PICTURE}>
              <LinearGradient start={vec(0, 0)} end={vec(0, PICTURE)} colors={['#6d98de', '#a9c6ee', '#d6e3f3']} />
            </Rect>
            <Path path={ROOF} color="#3a4763" />
            <Path path={ROOF_EDGE} color="#d4683b" />
            <Path path={GLASS} color="#cbd9ec" />
            <Path path={MULLIONS} style="stroke" strokeWidth={1.2} color="#f4f7fb" />
            <Path path={WING} color="#b99f9d" />
            <Path path={WING_ROOF} color="#dd7444" />
          </Group>
        </Canvas>
        <View style={styles.bannerText}>
          <Text style={styles.address} numberOfLines={1}>
            {address}
          </Text>
          <Text style={styles.hint} numberOfLines={2}>
            {hint}
          </Text>
        </View>
        <Pressable accessibilityRole="button" onPress={onFineTune}>
          {({ pressed }) => (
            <View style={[styles.fineTune, pressed && styles.fineTunePressed]}>
              <Text style={styles.fineTuneLabel}>{fineTuneLabel}</Text>
            </View>
          )}
        </Pressable>
      </View>

      <View style={styles.card}>
        <View style={styles.panel}>
          <Text style={styles.billLabel} numberOfLines={1}>
            {billLabel}
          </Text>
          <View style={styles.saved} accessible accessibilityLabel={`Saving ${symbol}${saving}`}>
            <Text style={styles.savedLabel}>
              {symbol}
              {counted}
            </Text>
            <View style={styles.triangle} />
          </View>
          {/* Each character keeps its place counted from the right, so the units stay the units. */}
          <View style={styles.amount} accessible accessibilityRole="text" accessibilityLabel={`${billLabel} ${amount}`}>
            <Digit char={symbol} still={reducedMotion} />
            {digits.map((char, index) => (
              <Digit key={digits.length - index} char={char} still={reducedMotion} />
            ))}
          </View>
        </View>

        <View style={styles.grid}>
          {rows.map((row) => (
            <View key={row[0]} style={styles.gridRow}>
              {row.map((index) => (
                <Chip key={options[index].label} label={options[index].label} on={on[index]} onPress={() => toggle(index)} />
              ))}
            </View>
          ))}
        </View>

        <GestureDetector gesture={pan}>
          <View
            style={styles.track}
            onLayout={onTrack}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Options switched on"
            accessibilityValue={{ min: 0, max: count, now: level }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(event) => {
              touched();
              dragTo(Math.min(count, Math.max(0, level + (event.nativeEvent.actionName === 'increment' ? 1 : -1))), 0.25);
            }}
          >
            {Array.from({ length: STOP_DOTS }, (_, index) => (
              <View key={index} style={[styles.stopDot, { left: TRACK_INSET + ((index + 1) * room) / (STOP_DOTS + 1) - 3.5 }]} />
            ))}
            <Animated.View style={[styles.fill, fillStyle]} />
            <Animated.View style={[styles.bolt, boltStyle]} pointerEvents="none">
              <Canvas style={styles.boltMark}>
                <Path path={BOLT} color="#ffffff" />
                <Path path={BOLT} style="stroke" strokeWidth={1.6} strokeJoin="round" color="#ffffff" />
              </Canvas>
            </Animated.View>
          </View>
        </GestureDetector>

        <Pressable accessibilityRole="button" onPress={() => onReport?.(on, left)}>
          {({ pressed }) => (
            <Animated.View style={[styles.report, { backgroundColor: pressed ? SAGE_PRESSED : SAGE, transitionProperty: 'backgroundColor', transitionDuration: 120 }]}>
              <Text style={styles.reportLabel}>{reportLabel}</Text>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKDROP,
    paddingHorizontal: SIDE,
    justifyContent: 'center',
  },
  banner: {
    height: BANNER,
    borderRadius: BANNER / 2,
    borderCurve: 'continuous',
    backgroundColor: BANNER_FILL,
    paddingLeft: 13,
    paddingRight: 19,
    flexDirection: 'row',
    alignItems: 'center',
  },
  picture: {
    width: PICTURE,
    height: PICTURE,
  },
  bannerText: {
    flex: 1,
    marginLeft: 14,
    marginRight: 4,
  },
  address: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 19,
    fontWeight: '800',
    letterSpacing: -0.4,
  },
  hint: {
    color: '#f2f5f0',
    fontSize: 13.5,
    lineHeight: 17.5,
    letterSpacing: -0.35,
  },
  fineTune: {
    height: 36,
    paddingHorizontal: 11,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#f2f5f0',
    justifyContent: 'center',
  },
  fineTunePressed: {
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  fineTuneLabel: {
    color: '#f2f5f0',
    fontSize: 14.5,
    lineHeight: 18,
    fontWeight: '500',
  },
  card: {
    marginTop: 8,
    padding: CARD_PADDING,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD_FILL,
  },
  panel: {
    height: PANEL,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: SAGE,
    overflow: 'hidden',
  },
  billLabel: {
    position: 'absolute',
    left: 21,
    top: 22,
    color: INK,
    fontSize: 14.5,
    lineHeight: 18,
    fontWeight: '500',
    letterSpacing: -0.2,
  },
  saved: {
    position: 'absolute',
    right: 16,
    top: 16,
    height: 31,
    minWidth: 71,
    paddingHorizontal: 10,
    borderRadius: 5,
    backgroundColor: CARD_FILL,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  savedLabel: {
    color: INK,
    fontSize: 15,
    lineHeight: 19,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  // A triangle pointing down, made of borders.
  triangle: {
    marginLeft: 6,
    width: 0,
    height: 0,
    borderLeftWidth: 5.5,
    borderRightWidth: 5.5,
    borderTopWidth: 10,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    borderTopColor: INK,
  },
  amount: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 15,
    height: AMOUNT,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    transform: [{ scaleY: AMOUNT_STRETCH }],
    transformOrigin: 'bottom',
  },
  digit: {
    height: AMOUNT,
    lineHeight: AMOUNT,
    color: CORAL,
    fontSize: AMOUNT,
    fontWeight: '900',
    letterSpacing: -4,
    transformOrigin: 'bottom',
  },
  grid: {
    marginTop: 10,
    gap: CHIP_GAP,
  },
  gridRow: {
    flexDirection: 'row',
    gap: CHIP_GAP + 1,
  },
  chipSlot: {
    flex: 1,
  },
  chip: {
    height: CHIP,
    borderRadius: CHIP / 2,
    paddingLeft: 15,
    paddingRight: 11,
    flexDirection: 'row',
    alignItems: 'center',
  },
  chipLabel: {
    flex: 1,
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
    letterSpacing: -0.45,
  },
  ring: {
    width: CHECK,
    height: CHECK,
    borderRadius: CHECK / 2,
    borderWidth: 5,
    borderColor: RING,
  },
  check: {
    position: 'absolute',
    right: 11,
    width: CHECK,
    height: CHECK,
    borderRadius: CHECK / 2,
    backgroundColor: INK,
  },
  checkMark: {
    width: CHECK,
    height: CHECK,
  },
  track: {
    marginTop: 27,
    height: TRACK,
    borderRadius: TRACK / 2,
    backgroundColor: BACKDROP,
  },
  stopDot: {
    position: 'absolute',
    top: TRACK / 2 - 3.5,
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: STOP_DOT,
  },
  fill: {
    position: 'absolute',
    left: TRACK_INSET,
    backgroundColor: CORAL,
  },
  bolt: {
    position: 'absolute',
    left: TRACK_INSET - 9,
    top: TRACK / 2 - 11,
    width: 18,
    height: 22,
  },
  boltMark: {
    width: 18,
    height: 22,
    transform: [{ scale: 1.12 }],
  },
  report: {
    marginTop: 9,
    height: BUTTON,
    borderRadius: 18,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  reportLabel: {
    color: INK,
    fontSize: 16.5,
    lineHeight: 21,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
});
