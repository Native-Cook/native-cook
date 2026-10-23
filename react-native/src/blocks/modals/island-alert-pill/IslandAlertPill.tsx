import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import Svg, { Circle } from 'react-native-svg';

type Device = {
  name: string;
  detail: string;
  /** Charge in percent; the first device follows the island's charge instead. */
  charge: number;
  icon: keyof typeof Ionicons.glyphMap;
  /** Background of the icon's tile and the colour of its glyph. */
  tint: string;
  ink: string;
};

type Props = {
  /** Plays the alert by itself, in a loop, and leaves the pill compact. */
  autoPlay?: boolean;
  /** The charge the alert opens at, in percent. */
  level?: number;
  /** The charge the autoPlay run reaches. */
  chargedTo?: number;
  /** Written at the right of the expanded island. */
  remaining?: string;
  /** What the page under the island lists. */
  devices?: Device[];
  /** Called with the percent while the knob is dragged. */
  onLevelChange?: (percent: number) => void;
};

const DEVICES: Device[] = [
  { name: 'This Phone', detail: 'Low Power Mode', charge: 10, icon: 'phone-portrait', tint: '#ffe3e0', ink: '#ff3b30' },
  { name: 'Watch', detail: 'On wrist · 2 days left', charge: 64, icon: 'watch', tint: '#e2ecff', ink: '#2f6bff' },
  { name: 'Earbuds', detail: 'In case · Connected', charge: 82, icon: 'headset', tint: '#e0f5e6', ink: '#24a148' },
  { name: 'Tablet', detail: 'At home · Charging', charge: 47, icon: 'tablet-portrait', tint: '#fff1d6', ink: '#e08a00' },
  { name: 'Keyboard', detail: 'Paired · Desk', charge: 91, icon: 'keypad', tint: '#eee6ff', ink: '#7a4dff' },
  { name: 'Pencil', detail: 'Paired · Last used today', charge: 28, icon: 'pencil', tint: '#ddf4f6', ink: '#0f9fae' },
];
// How many devices the widget at the top shows as rings.
const RINGS = 4;

// The timeline of one loop, in seconds from its start: the pill rests, the alert grows it into the
// island, the track draws, the battery charges, the knob snaps back, the island shrinks.
export const LOOP = 9;
const PULSE_AT = 0;
const EXPAND_AT = 0.8;
const DRAW_AT = 1.6;
const CHARGE_AT = 2.4;
const CHARGE_FOR = 2.5;
const SNAP_AT = 5.4;
const COLLAPSE_AT = 6.2;
// After a touch the loop waits this long, then starts again from the compact pill.
const USER_HOLD = 3;

const PULSE_MS = 400;
const PULSE_LOW = 0.5;
const SHAKE_MS = 40;
const SHAKE_POINTS = 2;
const EXPAND_SPRING = { duration: 450, dampingRatio: 0.72, reduceMotion: ReduceMotion.Never };
const COLLAPSE_SPRING = { duration: 450, dampingRatio: 0.85, reduceMotion: ReduceMotion.Never };
const POP_SPRING = { duration: 350, dampingRatio: 0.6, reduceMotion: ReduceMotion.Never };
const SNAP_SPRING = { duration: 700, dampingRatio: 0.45, reduceMotion: ReduceMotion.Never };
const DRAW_MS = 500;
const LABEL_MS = 300;
const CONTENT_OUT_MS = 150;
const FADE_MS = 200;
const BREATH_MS = 600;
const GLOW_LOW = 0.55;
const GLOW_REST = 0.8;

const COMPACT_WIDTH = 280;
const COMPACT_HEIGHT = 40;
const EXPANDED_HEIGHT = 100;
const EXPANDED_MARGIN = 20;
const EXPANDED_GAP = 51;
const COMPACT_RADIUS = 20;
const EXPANDED_RADIUS = 40;
const SIDE_PADDING = 20;
const LABEL_WIDTH = 74;
const TRACK_HEIGHT = 8;
const CHIP_WIDTH = 44;
const CHIP_HEIGHT = 24;
const GRAB_HEIGHT = 48;

const RED = '#ff3b30';
const TRACK = '#333333';
const PAGE = '#f2f2f5';
const GREEN = '#34c759';
const RING_TRACK = '#ececf0';
const INK = '#111114';
const MUTE = '#7c7c83';

const clamp = (value: number, low: number, high: number) => {
  'worklet';
  return Math.min(high, Math.max(low, value));
};

/**
 * A page and, on top of it, a dynamic-island pill showing a low battery. An alert grows the pill into
 * an island with a track, a knob that carries the percent, and the time left; the knob can be
 * dragged to preview a charge and springs back. A long press expands the pill, a tap on the page
 * shrinks it.
 */
export function IslandAlertPill({
  autoPlay = false,
  level = 10,
  chargedTo = 38,
  remaining = '~15m',
  devices = DEVICES,
  onLevelChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [percent, setPercent] = useState(level);
  const [isOpen, setIsOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const expandedWidth = width - EXPANDED_MARGIN * 2;
  const trackWidth = expandedWidth - SIDE_PADDING * 2 - LABEL_WIDTH;
  const compactTop = insets.top >= 54 ? 10 : insets.top + 6;
  const expandedTop = compactTop + EXPANDED_GAP;

  // 0 is the compact pill, 1 the island; the spring may pass either end.
  const grow = useSharedValue(0);
  // How much of the track is drawn, how far the knob has popped in, and the label's opacity.
  const draw = useSharedValue(0);
  const pop = useSharedValue(0);
  const label = useSharedValue(0);
  const pulse = useSharedValue(1);
  const shake = useSharedValue(0);
  const glow = useSharedValue(GLOW_REST);
  const charge = useSharedValue(level);
  const expanded = useRef(false);

  const expand = () => {
    if (expanded.current) return;
    expanded.current = true;
    setIsOpen(true);
    if (reducedMotion) {
      grow.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
    } else {
      grow.set(withSpring(1, EXPAND_SPRING));
      shake.set(
        withSequence(
          withTiming(SHAKE_POINTS, { duration: SHAKE_MS }),
          withTiming(-SHAKE_POINTS, { duration: SHAKE_MS }),
          withTiming(SHAKE_POINTS, { duration: SHAKE_MS }),
          withTiming(-SHAKE_POINTS, { duration: SHAKE_MS }),
          withTiming(0, { duration: SHAKE_MS }),
        ),
      );
    }
    const wait = (DRAW_AT - EXPAND_AT) * 1000;
    draw.set(withDelay(wait, withTiming(1, { duration: reducedMotion ? FADE_MS : DRAW_MS, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never })));
    pop.set(withDelay(wait + DRAW_MS * 0.5, reducedMotion ? withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }) : withSpring(1, POP_SPRING)));
    label.set(withDelay(wait + DRAW_MS * 0.6, withTiming(1, { duration: LABEL_MS, reduceMotion: ReduceMotion.Never })));
  };

  const collapse = () => {
    if (!expanded.current) return;
    expanded.current = false;
    cancelAnimation(glow);
    glow.set(GLOW_REST);
    for (const value of [draw, pop, label]) {
      cancelAnimation(value);
      value.set(withTiming(0, { duration: CONTENT_OUT_MS, reduceMotion: ReduceMotion.Never }));
    }
    cancelAnimation(charge);
    charge.set(withTiming(level, { duration: CONTENT_OUT_MS, reduceMotion: ReduceMotion.Never }));
    grow.set(
      reducedMotion
        ? withTiming(0, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }, (finished) => {
            if (finished) scheduleOnRN(setIsOpen, false);
          })
        : withDelay(
            CONTENT_OUT_MS,
            withSpring(0, COLLAPSE_SPRING, (finished) => {
              if (finished) scheduleOnRN(setIsOpen, false);
            }),
          ),
    );
  };

  const startCharge = () => {
    glow.set(
      withRepeat(
        withSequence(
          withTiming(GLOW_LOW, { duration: BREATH_MS, easing: Easing.inOut(Easing.sin), reduceMotion: ReduceMotion.Never }),
          withTiming(1, { duration: BREATH_MS, easing: Easing.inOut(Easing.sin), reduceMotion: ReduceMotion.Never }),
        ),
        -1,
      ),
    );
    charge.set(withTiming(chargedTo, { duration: CHARGE_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  const snapBack = () => {
    cancelAnimation(glow);
    glow.set(withTiming(GLOW_REST, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
    cancelAnimation(charge);
    charge.set(
      reducedMotion
        ? withTiming(level, { duration: FADE_MS, reduceMotion: ReduceMotion.Never })
        : withSpring(level, SNAP_SPRING),
    );
  };

  const pulseOnce = () => {
    pulse.set(
      withSequence(
        withTiming(PULSE_LOW, { duration: PULSE_MS, reduceMotion: ReduceMotion.Never }),
        withTiming(1, { duration: PULSE_MS, reduceMotion: ReduceMotion.Never }),
      ),
    );
  };

  useEffect(
    () => () => {
      for (const value of [grow, draw, pop, label, pulse, shake, glow, charge]) cancelAnimation(value);
    },
    [grow, draw, pop, label, pulse, shake, glow, charge],
  );

  // The loop reads the latest functions when its timers fire.
  const latest = useRef({ expand, collapse, startCharge, snapBack, pulseOnce });
  useEffect(() => {
    latest.current = { expand, collapse, startCharge, snapBack, pulseOnce };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (origin: number, seconds: number, run: () => void) =>
      timers.push(setTimeout(run, origin + seconds * 1000 - Date.now()));
    const cycle = (origin: number) => {
      at(origin, PULSE_AT, () => latest.current.pulseOnce());
      at(origin, EXPAND_AT, () => latest.current.expand());
      at(origin, CHARGE_AT, () => latest.current.startCharge());
      at(origin, SNAP_AT, () => latest.current.snapBack());
      at(origin, COLLAPSE_AT, () => latest.current.collapse());
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      timers.push(
        setTimeout(() => {
          latest.current.collapse();
          cycle(Date.now() + 600);
        }, USER_HOLD * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  useAnimatedReaction(
    () => Math.round(clamp(charge.get(), 0, 100)),
    (now, old) => {
      if (now !== old) scheduleOnRN(setPercent, now);
    },
  );

  const reportLevel = (value: number) => onLevelChange?.(Math.round(value));
  const drag = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      cancelAnimation(charge);
      cancelAnimation(glow);
      glow.set(1);
      charge.set(clamp((event.x / trackWidth) * 100, 0, 100));
    })
    .onUpdate((event) => {
      charge.set(clamp((event.x / trackWidth) * 100, 0, 100));
      if (onLevelChange) scheduleOnRN(reportLevel, charge.get());
    })
    .onFinalize(() => {
      glow.set(withTiming(GLOW_REST, { duration: FADE_MS }));
      charge.set(withSpring(level, SNAP_SPRING));
    });

  const islandStyle = useAnimatedStyle(() => {
    const g = Math.max(grow.get(), 0);
    return {
      width: COMPACT_WIDTH + (expandedWidth - COMPACT_WIDTH) * g,
      height: COMPACT_HEIGHT + (EXPANDED_HEIGHT - COMPACT_HEIGHT) * g,
      borderRadius: COMPACT_RADIUS + (EXPANDED_RADIUS - COMPACT_RADIUS) * Math.min(g, 1.2),
      top: compactTop + (expandedTop - compactTop) * g,
      transform: [{ translateX: shake.get() }],
      boxShadow: [
        { offsetX: 0, offsetY: 4, blurRadius: 8, color: `rgba(0,0,0,${0.16 * clamp(g, 0, 1)})` },
        { offsetX: 0, offsetY: 18, blurRadius: 40, color: `rgba(0,0,0,${0.24 * clamp(g, 0, 1)})` },
      ],
    };
  });
  const compactStyle = useAnimatedStyle(() => ({ opacity: clamp(1 - grow.get() * 3, 0, 1) }));
  const boltStyle = useAnimatedStyle(() => ({ opacity: pulse.get() }));
  const trackStyle = useAnimatedStyle(() => ({ width: trackWidth * clamp(draw.get(), 0, 1) }));
  const fillStyle = useAnimatedStyle(() => ({ width: (clamp(charge.get(), 0, 100) / 100) * trackWidth }));
  const chipStyle = useAnimatedStyle(() => {
    const fillEnd = (clamp(charge.get(), 0, 100) / 100) * trackWidth;
    return {
      opacity: clamp(pop.get() * 2, 0, 1),
      transform: [{ translateX: clamp(fillEnd - 4, 0, trackWidth - CHIP_WIDTH) }, { scale: Math.max(pop.get(), 0) }],
    };
  });
  const glowStyle = useAnimatedStyle(() => {
    const fillEnd = (clamp(charge.get(), 0, 100) / 100) * trackWidth;
    return {
      opacity: glow.get() * clamp(pop.get(), 0, 1),
      transform: [{ translateX: clamp(fillEnd - 4, 0, trackWidth - CHIP_WIDTH) }],
    };
  });
  const labelStyle = useAnimatedStyle(() => ({ opacity: label.get() }));

  const touched = () => {
    if (autoPlay) setTouches((n) => n + 1);
  };

  return (
    <GestureHandlerRootView style={styles.screen} onTouchStart={touched}>
      <StatusBar hidden />
      <Pressable style={styles.page} onPress={collapse} accessibilityLabel="Page">
        <View style={[styles.header, { paddingTop: expandedTop + EXPANDED_HEIGHT + 20 }]}>
          <Text style={styles.title}>Batteries</Text>
          <Text style={styles.subtitle}>{devices.length} devices · Updated just now</Text>
        </View>

        <View style={[styles.card, styles.widget]}>
          {devices.slice(0, RINGS).map((device, i) => {
            const value = i === 0 ? percent : device.charge;
            return (
              <View key={device.name} style={styles.ringCell}>
                <Ring value={value} />
                <View style={styles.ringIcon}>
                  <Ionicons name={device.icon} size={22} color={INK} />
                </View>
                <Text style={[styles.ringValue, value < LOW && { color: RED }]}>{value}%</Text>
              </View>
            );
          })}
        </View>

        <Text style={styles.section}>Devices</Text>
        <View style={styles.card}>
          {devices.map((device, i) => {
            const value = i === 0 ? percent : device.charge;
            return (
              <View key={device.name} style={styles.row}>
                <View style={[styles.rowIcon, { backgroundColor: device.tint }]}>
                  <Ionicons name={device.icon} size={19} color={device.ink} />
                </View>
                <View style={[styles.rowBody, i > 0 && styles.rowRule]}>
                  <View style={styles.rowText}>
                    <Text style={styles.rowName} numberOfLines={1}>{device.name}</Text>
                    <Text style={styles.rowDetail} numberOfLines={1}>{device.detail}</Text>
                  </View>
                  <Text style={[styles.rowValue, value < LOW && { color: RED }]}>{value}%</Text>
                  <BatteryGlyph value={value} />
                </View>
              </View>
            );
          })}
        </View>
      </Pressable>

      <Animated.View style={[styles.island, islandStyle]}>
        <Pressable
          style={StyleSheet.absoluteFill}
          onLongPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
            expand();
          }}
          delayLongPress={350}
          accessibilityRole="button"
          accessibilityLabel={`Battery ${percent} percent`}
        />

        <Animated.View style={[styles.compact, compactStyle]} pointerEvents="none">
          <Animated.View style={boltStyle}>
            <Ionicons name="flash" size={16} color={RED} />
          </Animated.View>
          <Text style={styles.compactNumber}>{percent}</Text>
        </Animated.View>

        <View style={[styles.inner, { left: SIDE_PADDING }]} pointerEvents={isOpen ? 'auto' : 'none'}>
          <Animated.View style={[styles.trackClip, trackStyle]}>
            <View style={[styles.track, { width: trackWidth }]} />
            <Animated.View style={[styles.fill, fillStyle]} />
          </Animated.View>
          <Animated.View style={[styles.glow, glowStyle]} pointerEvents="none" />
          <Animated.View style={[styles.chip, chipStyle]} pointerEvents="none">
            <Ionicons name="flash" size={12} color="rgba(255,255,255,0.6)" />
            <Text style={styles.chipNumber}>{percent}</Text>
          </Animated.View>
          <GestureDetector gesture={drag}>
            <View style={[styles.grab, { width: trackWidth }]} />
          </GestureDetector>
          <Animated.Text style={[styles.remaining, { left: trackWidth + 12 }, labelStyle]} numberOfLines={1}>
            {remaining}
          </Animated.Text>
        </View>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

// Below this a charge is drawn red.
const LOW = 20;
const RING_SIZE = 62;
const RING_STROKE = 6;

/** A battery ring of the widget: a grey track and the charge drawn clockwise from the top. */
function Ring({ value }: { value: number }) {
  const r = (RING_SIZE - RING_STROKE) / 2;
  const length = 2 * Math.PI * r;
  const colour = value < LOW ? RED : GREEN;
  return (
    <Svg width={RING_SIZE} height={RING_SIZE}>
      <Circle cx={RING_SIZE / 2} cy={RING_SIZE / 2} r={r} stroke={RING_TRACK} strokeWidth={RING_STROKE} fill="none" />
      <Circle
        cx={RING_SIZE / 2}
        cy={RING_SIZE / 2}
        r={r}
        stroke={colour}
        strokeWidth={RING_STROKE}
        strokeLinecap="round"
        fill="none"
        strokeDasharray={`${length} ${length}`}
        strokeDashoffset={length * (1 - Math.min(100, Math.max(0, value)) / 100)}
        transform={`rotate(-90 ${RING_SIZE / 2} ${RING_SIZE / 2})`}
      />
    </Svg>
  );
}

/** The small horizontal battery at the end of a row. */
function BatteryGlyph({ value }: { value: number }) {
  const colour = value < LOW ? RED : value > 80 ? GREEN : INK;
  return (
    <View style={styles.battery}>
      <View style={styles.batteryBody}>
        <View style={[styles.batteryFill, { width: `${Math.max(8, value)}%`, backgroundColor: colour }]} />
      </View>
      <View style={styles.batteryCap} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  page: { flex: 1, backgroundColor: PAGE, paddingHorizontal: 20 },
  header: { paddingBottom: 16 },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1 },
  subtitle: { color: MUTE, fontSize: 15, lineHeight: 20, marginTop: 2 },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 26,
    borderCurve: 'continuous',
    boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(0,0,0,0.04)' },
      { offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(0,0,0,0.06)' },
    ],
  },
  widget: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 18, paddingTop: 18, paddingBottom: 14 },
  ringCell: { alignItems: 'center' },
  ringIcon: { position: 'absolute', top: 0, width: RING_SIZE, height: RING_SIZE, alignItems: 'center', justifyContent: 'center' },
  ringValue: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '700', marginTop: 8, fontVariant: ['tabular-nums'], letterSpacing: -0.2 },
  section: { color: MUTE, fontSize: 13, lineHeight: 18, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 24, marginBottom: 8, marginLeft: 16 },
  row: { flexDirection: 'row', alignItems: 'center', paddingLeft: 16, height: 62 },
  rowIcon: { width: 36, height: 36, borderRadius: 10, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  rowBody: { flex: 1, height: '100%', flexDirection: 'row', alignItems: 'center', marginLeft: 12, paddingRight: 16 },
  rowRule: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(60,60,67,0.22)' },
  rowText: { flex: 1 },
  rowName: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.3 },
  rowDetail: { color: MUTE, fontSize: 13, lineHeight: 17, marginTop: 1 },
  rowValue: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '600', marginRight: 8, fontVariant: ['tabular-nums'] },
  battery: { flexDirection: 'row', alignItems: 'center' },
  batteryBody: { width: 25, height: 12, borderRadius: 3.5, borderWidth: 1, borderColor: 'rgba(60,60,67,0.35)', padding: 1.5 },
  batteryFill: { height: '100%', borderRadius: 2 },
  batteryCap: { width: 2, height: 4, borderTopRightRadius: 1, borderBottomRightRadius: 1, backgroundColor: 'rgba(60,60,67,0.35)', marginLeft: 1 },

  island: { position: 'absolute', alignSelf: 'center', backgroundColor: '#000000', borderCurve: 'continuous' },
  compact: {
    ...StyleSheet.absoluteFill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  compactNumber: { color: RED, fontSize: 16, lineHeight: 20, fontWeight: '600', fontVariant: ['tabular-nums'] },
  inner: { position: 'absolute', top: 0, bottom: 0, right: 0 },
  trackClip: { position: 'absolute', top: EXPANDED_HEIGHT / 2 - TRACK_HEIGHT / 2, left: 0, height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, overflow: 'hidden' },
  track: { height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, backgroundColor: TRACK },
  fill: { position: 'absolute', left: 0, top: 0, height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, backgroundColor: RED },
  glow: {
    position: 'absolute',
    top: EXPANDED_HEIGHT / 2 - CHIP_HEIGHT / 2,
    left: 0,
    width: CHIP_WIDTH,
    height: CHIP_HEIGHT,
    borderRadius: CHIP_HEIGHT / 2,
    backgroundColor: RED,
    boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 18, spreadDistance: 2, color: 'rgba(255,59,48,0.7)' }],
  },
  chip: {
    position: 'absolute',
    top: EXPANDED_HEIGHT / 2 - CHIP_HEIGHT / 2,
    left: 0,
    width: CHIP_WIDTH,
    height: CHIP_HEIGHT,
    borderRadius: CHIP_HEIGHT / 2,
    backgroundColor: RED,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  chipNumber: { color: '#ffffff', fontSize: 16, lineHeight: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  grab: { position: 'absolute', left: 0, top: EXPANDED_HEIGHT / 2 - GRAB_HEIGHT / 2, height: GRAB_HEIGHT },
  remaining: { position: 'absolute', top: EXPANDED_HEIGHT / 2 - 11, color: RED, fontSize: 16, lineHeight: 22, fontWeight: '600' },
});
