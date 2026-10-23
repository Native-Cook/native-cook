import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { FadeIn, FadeOut, type WithSpringConfig, useDerivedValue, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BOX_HEIGHT, BOX_WIDTH, CarPicture } from './CarPicture';

export type Clearance = {
  /** The chip. */
  label: string;
  /** The line in bold under the chips, and the text under it. */
  heading: string;
  text: string;
  /** The grey line of typical cars. */
  examples: string;
  /** How high the car in the picture rides: 0 the lowest, 1 the highest. */
  height: number;
  /** How many cars match, for the button. */
  count: number;
};

type Props = {
  /** The choices, in the order of the chips. */
  options?: Clearance[];
  /** The one chosen at first, by its place in `options`; Reset comes back to it. */
  selected?: number;
  /** How many chips go in a row. */
  perRow?: number;
  /** Goes through the chips by itself every LOOP seconds. */
  autoPlay?: boolean;
  title?: string;
  resetLabel?: string;
  examplesLabel?: string;
  /** The button's label for a number of matching cars. */
  applyLabel?: (count: number) => string;
  /** The colour of the Reset link. */
  linkColor?: string;
  onChange?: (index: number, option: Clearance) => void;
  onReset?: () => void;
  onApply?: (index: number, option: Clearance) => void;
  onClose?: () => void;
};

const OPTIONS: Clearance[] = [
  {
    label: 'Sport',
    heading: 'Up to 12 cm',
    text: 'Made for race tracks and perfect roads. Speed bumps have to be taken very, very carefully.',
    examples: 'coupés, roadsters, hot hatches',
    height: 0,
    count: 128,
  },
  {
    label: 'Low',
    heading: 'From 12 to 15 cm',
    text: 'These cars are built for good roads. Be a little careful with kerbs when you park.',
    examples: 'saloons, estates, city hatchbacks',
    height: 0.28,
    count: 342,
  },
  {
    label: 'Medium',
    heading: 'From 15 to 20 cm',
    text: 'Kerbs stop being a worry, and a track through the woods is fine. Real off-road is still out of reach.',
    examples: 'crossovers, compact SUVs',
    height: 0.55,
    count: 517,
  },
  {
    label: 'High',
    heading: 'From 20 to 24 cm',
    text: 'Proper off-roaders start here, next to quite ordinary city crossovers that only look the part.',
    examples: 'large SUVs, pickups',
    height: 0.78,
    count: 266,
  },
  {
    label: 'Off-road',
    heading: 'More than 24 cm',
    text: 'Aerodynamics suffer, but it will go almost anywhere. This is the clearance of big 4x4s.',
    examples: 'body-on-frame 4x4s, lifted pickups',
    height: 1,
    count: 94,
  },
];

const BACKDROP = '#101012';
const BEHIND = '#3a3a3e';
const SHEET = '#ffffff';
const INK = '#141414';
const TEXT = '#222222';
const MUTE = '#8a8a8f';
const GRABBER = '#d6d6d9';
const CHIP = '#efefef';
const CHIP_PRESSED = '#dcdcdc';
const CHIP_ON = '#141414';
const LINK = '#2ba6c9';

// Sizes in points, for a 402 × 874 screen.
const SHEET_TOP = 14;
const SHEET_RADIUS = 26;
const MARGIN = 16;
const HEADER = 52;
const CHIP_HEIGHT = 46;
const CHIP_RADIUS = 13;
const CHIP_GAP = 8;
const APPLY = 56;

// The body's travel from the lowest chip to the highest, in units of the picture's box, and the
// spring it rides on: soft enough to overshoot and settle like a car on its suspension.
const LIFT = 18;
const SUSPENSION: WithSpringConfig = { mass: 1, stiffness: 170, damping: 13 };
// A stiffer spring runs ahead of it; the gap between the two leans the body about its wheel, so
// the nose leads on the way up and dips first on the way down.
const LEAD: WithSpringConfig = { mass: 1, stiffness: 330, damping: 24 };
const PITCH = 0.0045;
// Chips and text, in ms.
const CHIP_FADE = 300;
const TEXT_FADE = 160;

/** autoPlay, in seconds: the chips it presses (by their place) every STEP from FIRST_AT, each held for PRESS_FOR. */
const LOOP = 8.6;
const FIRST_AT = 0.8;
const STEP = 1.4;
const PRESS_FOR = 0.14;
const TOUR = [4, 1, 2, 3, 0];
/** After a touch autoPlay waits this long before it starts over from the first choice. */
const USER_HOLD = 3;
// The body back at rest after the last change, in seconds.
const RETURN = 0.9;

function haptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type ChipProps = { label: string; on: boolean; held: boolean; onPress: () => void };

function Chip({ label, on, held, onPress }: ChipProps) {
  return (
    <Pressable onPress={onPress} accessibilityRole="radio" accessibilityState={{ selected: on }}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.chip,
            {
              backgroundColor: on ? CHIP_ON : pressed || held ? CHIP_PRESSED : CHIP,
              transform: [{ scale: pressed || held ? 0.96 : 1 }],
              transitionProperty: ['backgroundColor', 'transform'],
              transitionDuration: [CHIP_FADE, 120],
            },
          ]}
        >
          <Animated.Text style={[styles.chipLabel, { color: on ? '#ffffff' : TEXT, transitionProperty: 'color', transitionDuration: CHIP_FADE }]} numberOfLines={1}>
            {label}
          </Animated.Text>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A filter sheet for a car's ground clearance: five chips, and a picture that answers the choice.
 * The front of a car stands by a kerb; choosing a chip raises or lowers its body on the suspension
 * while the wheel stays on the ground, and the heading and text under the chips change with it.
 */
export function RideHeightFilter({
  options = OPTIONS,
  selected = 0,
  perRow = 3,
  autoPlay = false,
  title = 'Ground clearance',
  resetLabel = 'Reset',
  examplesLabel = 'Typical cars',
  applyLabel = (count) => `Show ${count} cars`,
  linkColor = LINK,
  onChange,
  onReset,
  onApply,
  onClose,
}: Props) {
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's width stands in until then.
  const [laidOut, setLaidOut] = useState<number | null>(null);
  const width = laidOut ?? window.width;
  const [chosen, setChosen] = useState(selected);
  // The chip held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState<number | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // How far up the body is drawn to, and where it is: behind it, on its suspension.
  const target = useSharedValue(options[selected].height * LIFT);
  const lift = useDerivedValue(() => withSpring(target.get(), SUSPENSION));
  const lead = useDerivedValue(() => withSpring(target.get(), LEAD));
  const pitch = useDerivedValue(() => (lead.get() - lift.get()) * PITCH);

  const choose = (index: number) => {
    setChosen(index);
    target.set(options[index].height * LIFT);
    onChange?.(index, options[index]);
  };

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      TOUR.forEach((stop, order) => {
        const index = stop % options.length;
        const at = origin + (FIRST_AT + order * STEP) * 1000;
        after(at - PRESS_FOR * 1000 - Date.now(), () => setAutoPressed(index));
        after(at - Date.now(), () => {
          setAutoPressed(null);
          setChosen(index);
          target.set(options[index].height * LIFT);
        });
      });
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      // The loop starts over from the choice it was given.
      setChosen(selected);
      target.set(options[selected].height * LIFT);
      after(RETURN * 1000, () => cycle(Date.now()));
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, options, selected, target]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(null);
    setTouches((n) => n + 1);
  };

  const onLayout = (event: LayoutChangeEvent) => setLaidOut(event.nativeEvent.layout.width);

  const option = options[chosen];
  const rows: number[][] = [];
  options.forEach((_, index) => {
    if (index % perRow === 0) rows.push([]);
    rows[rows.length - 1].push(index);
  });
  const sheetTop = insets.top + SHEET_TOP;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {/* The page the sheet came up over, pushed back. */}
      <View style={[styles.behind, { top: sheetTop - 10 }]} />

      <View style={[styles.sheet, { top: sheetTop, paddingBottom: Math.max(insets.bottom, 16) + 8 }]}>
        <View style={styles.grabber} />
        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          <Pressable
            style={styles.close}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Close"
            onPress={() => {
              touched();
              onClose?.();
            }}
          >
            {({ pressed }) => <Ionicons name="close" size={28} color={INK} style={{ opacity: pressed ? 0.4 : 1 }} />}
          </Pressable>
          <Pressable
            style={styles.reset}
            hitSlop={8}
            accessibilityRole="button"
            onPress={() => {
              touched();
              haptic();
              choose(selected);
              onReset?.();
            }}
          >
            {({ pressed }) => (
              <Text style={[styles.resetLabel, { color: linkColor, opacity: pressed ? 0.4 : 1 }]} numberOfLines={1}>
                {resetLabel}
              </Text>
            )}
          </Pressable>
        </View>

        <View style={[styles.picture, { height: (BOX_HEIGHT / BOX_WIDTH) * width }]} accessible accessibilityRole="image" accessibilityLabel={`A car with ${option.label} clearance beside a kerb`}>
          <CarPicture width={width} lift={lift} pitch={pitch} maxLift={LIFT} />
        </View>

        <View style={styles.chips} accessibilityRole="radiogroup">
          {rows.map((row) => (
            <View key={row[0]} style={styles.row}>
              {row.map((index) => (
                <Chip
                  key={index}
                  label={options[index].label}
                  on={index === chosen}
                  held={index === autoPressed}
                  onPress={() => {
                    touched();
                    haptic();
                    choose(index);
                  }}
                />
              ))}
            </View>
          ))}
        </View>

        <View style={styles.copy}>
          {/* A new choice's words fade in over the old ones. */}
          <Animated.View key={chosen} style={styles.copyLayer} entering={FadeIn.duration(TEXT_FADE)} exiting={FadeOut.duration(TEXT_FADE)}>
            <Text style={styles.heading} numberOfLines={1}>
              {option.heading}
            </Text>
            <Text style={styles.text} numberOfLines={3}>
              {option.text}
            </Text>
            <Text style={styles.examples} numberOfLines={1}>
              {examplesLabel}: {option.examples}
            </Text>
          </Animated.View>
        </View>

        <View style={styles.space} />

        <Pressable
          accessibilityRole="button"
          onPress={() => {
            touched();
            haptic();
            onApply?.(chosen, option);
          }}
        >
          {({ pressed }) => (
            <Animated.View style={[styles.apply, { opacity: pressed ? 0.75 : 1, transitionProperty: 'opacity', transitionDuration: 110 }]}>
              <Animated.Text key={chosen} style={styles.applyLabel} numberOfLines={1} entering={FadeIn.duration(TEXT_FADE)} exiting={FadeOut.duration(TEXT_FADE)}>
                {applyLabel(option.count)}
              </Animated.Text>
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
  },
  behind: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: 40,
    borderTopLeftRadius: 18,
    borderTopRightRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: BEHIND,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: SHEET,
    overflow: 'hidden',
  },
  grabber: {
    alignSelf: 'center',
    marginTop: 8,
    width: 40,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: GRABBER,
  },
  header: {
    height: HEADER,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '700',
  },
  close: {
    position: 'absolute',
    left: MARGIN - 4,
    top: 0,
    height: HEADER,
    justifyContent: 'center',
  },
  reset: {
    position: 'absolute',
    right: MARGIN,
    top: 0,
    height: HEADER,
    justifyContent: 'center',
  },
  resetLabel: {
    fontSize: 16,
    lineHeight: 21,
  },
  picture: {
    marginTop: 18,
  },
  chips: {
    marginTop: 26,
    paddingHorizontal: MARGIN,
    gap: CHIP_GAP,
  },
  row: {
    flexDirection: 'row',
    gap: CHIP_GAP,
  },
  chip: {
    height: CHIP_HEIGHT,
    paddingHorizontal: 22,
    borderRadius: CHIP_RADIUS,
    borderCurve: 'continuous',
    justifyContent: 'center',
  },
  chipLabel: {
    fontSize: 16,
    lineHeight: 21,
  },
  copy: {
    height: 128,
    marginTop: 30,
    marginHorizontal: MARGIN,
  },
  copyLayer: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  heading: {
    color: INK,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '700',
  },
  text: {
    marginTop: 4,
    color: TEXT,
    fontSize: 16,
    lineHeight: 22,
  },
  examples: {
    marginTop: 10,
    color: MUTE,
    fontSize: 14,
    lineHeight: 19,
  },
  space: {
    flex: 1,
  },
  apply: {
    height: APPLY,
    marginHorizontal: MARGIN,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  applyLabel: {
    position: 'absolute',
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
});
