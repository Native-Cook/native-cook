import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { Bubble, type BubbleItem } from './Bubble';
import {
  CLUSTER_HEIGHT,
  CLUSTER_WIDTH,
  CURVE,
  type Events,
  NEVER,
  type Place,
  SETTLE,
  SLOTS,
  USER_HOLD,
  autoEvents,
  buttonScale,
  plusTurn,
} from './motion';

export type { BubbleItem } from './Bubble';

type Props = {
  title?: string;
  hint?: string;
  /** Up to five photos, from the big bubble to the tiny one. */
  items?: BubbleItem[];
  background?: string;
  /** The fill of the "+". */
  accent?: string;
  /** Opens the bubbles, picks one and closes them again by itself every LOOP seconds. */
  autoPlay?: boolean;
  openLabel?: string;
  closeLabel?: string;
  onOpenChange?: (open: boolean) => void;
  onSelect?: (item: BubbleItem, selected: boolean) => void;
};

function photo(id: string, size: number) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${size}&h=${size}&q=80`;
}

const ITEMS: BubbleItem[] = [
  {
    title: 'Portrait',
    photo: photo('1517841905240-472988babdf9', 640),
    tint: '#3f6f86',
    glow: '#bfe3f2',
  },
  {
    title: 'Balloons',
    photo: photo('1641128324972-af3212f0f6bd', 320),
    tint: '#2f7fd0',
    glow: '#ffd1b8',
  },
  {
    title: 'Forest',
    photo: photo('1441974231531-c6227db76b6e', 320),
    tint: '#5d6b3a',
    glow: '#e3f0a8',
  },
  {
    title: 'City lights',
    photo: photo('1496442226666-8d4d0e62e6e9', 320),
    tint: '#3a2a26',
    glow: '#ffb38a',
  },
  {
    title: 'Sky',
    photo: photo('1506905925346-21bda4d32df4', 120),
    tint: '#9aa6c4',
    glow: '#ffd6e4',
  },
];

const BACKGROUND = '#efefef';
const ACCENT = '#b9b4cf';
const INK = '#1a1a1a';
const MUTE = '#8a8a8a';
// Light from above on the "+": a white wash fading to a faint shade at the bottom.
const BUTTON_SHEEN = ['rgba(255,255,255,0.22)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.06)'] as const;
const BUTTON_RIM = 'rgba(255,255,255,0.3)';
const BUTTON_SHADOW = 'rgba(70,60,110,0.18)';
const BUTTON_SHADOW_Y = 8;
const BUTTON_SHADOW_BLUR = 24;

const SIDE = 24;
const HEAD_TOP = 24;
const TITLE_LINE = 34;
const HINT_GAP = 6;
const HINT_LINE = 20;
/** The centre of the "+" sits this far above the bottom safe area. */
const BUTTON_LIFT = 120;
/** Room kept between the hint and the top of the cluster. */
const CLUSTER_GAP = 24;
/** The "+" at cluster scale 1: its diameter, and the length and weight of its strokes. */
const BUTTON = 56;
const PLUS = 20;
const PLUS_STROKE = 2;
/** The cluster grows with the screen up to this scale, and shrinks on small ones down to the other. */
const MAX_SCALE = 1.3;
const MIN_SCALE = 0.6;

const REST: Events = {
  openAt: NEVER,
  closeAt: NEVER,
  downAt: NEVER,
  upAt: NEVER,
  pickOn: [],
  pickAt: [],
};

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A media "add" screen: a round "+" that opens a cluster of photos in glass bubbles. They are born
 * behind the "+", travel up along a curve to their places with a springy overshoot, float there, and
 * fall back into it one after the other when it is pressed again; tapping a bubble picks it.
 */
export function BubbleMediaBurst({
  title = 'Add to your mix',
  hint = 'Tap + to open your library',
  items = ITEMS,
  background = BACKGROUND,
  accent = ACCENT,
  autoPlay = false,
  openLabel = 'Open library',
  closeLabel = 'Close library',
  onOpenChange,
  onSelect,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{
    width: number;
    height: number;
  } | null>(null);
  const { width, height } = laidOut ?? window;
  const shown = items.slice(0, SLOTS.length);
  const count = shown.length;

  // What the screen shows, mirrored from the clock for touches and screen readers.
  const [open, setOpen] = useState(false);
  const [picked, setPicked] = useState<boolean[]>([]);
  const reported = useRef({ open: false, picked: [] as boolean[] });

  // The clock in seconds; the events it is drawn from; the autoPlay's loop start (-1 while a touch
  // holds it off) and the last touch.
  const clock = useSharedValue(0);
  const events = useSharedValue<Events>({
    ...REST,
    pickOn: shown.map(() => 0),
    pickAt: shown.map(() => NEVER),
  });
  const loopStart = useSharedValue(-1);
  const touchedAt = useSharedValue(NEVER);
  const mirrored = useSharedValue('');

  const bx = width / 2;
  const by = height - insets.bottom - BUTTON_LIFT;
  const headBottom = insets.top + HEAD_TOP + TITLE_LINE + HINT_GAP + HINT_LINE;
  const scale = Math.max(
    MIN_SCALE,
    Math.min(MAX_SCALE, (width - SIDE * 2) / CLUSTER_WIDTH, (by - headBottom - CLUSTER_GAP) / CLUSTER_HEIGHT),
  );
  const button = BUTTON * scale;

  const places = useMemo(
    () =>
      SLOTS.map((slot): Place => {
        const restX = bx + slot.x * scale;
        const restY = by - slot.y * scale;
        // The path bows to the left of the straight line from the "+", so the bubbles swirl out.
        const dx = restX - bx;
        const dy = restY - by;
        return {
          restX,
          restY,
          ctrlX: bx + dx / 2 + CURVE * dy,
          ctrlY: by + dy / 2 - CURVE * dx,
          bx,
          by,
        };
      }),
    [bx, by, scale],
  );

  const report = (nextOpen: boolean, nextPicked: boolean[]) => {
    const before = reported.current;
    reported.current = { open: nextOpen, picked: nextPicked };
    setOpen(nextOpen);
    setPicked(nextPicked);
    if (before.open !== nextOpen) onOpenChange?.(nextOpen);
    nextPicked.forEach((on, i) => {
      if (on !== (before.picked[i] ?? false)) onSelect?.(shown[i], on);
    });
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (autoPlay) {
      let start = loopStart.get();
      if (start < 0 && now - touchedAt.get() >= USER_HOLD) {
        const e = events.get();
        // Back to rest the way a tap on "+" goes there, then the loop starts again.
        if (e.openAt > e.closeAt) events.set({ ...e, closeAt: now });
        else if (now >= e.closeAt + SETTLE) {
          loopStart.set(now);
          start = now;
        }
      }
      if (start >= 0) events.set(autoEvents(now, start, count));
    }
    const e = events.get();
    let key = e.openAt > e.closeAt ? 'o' : 'c';
    for (let i = 0; i < count; i++) key += e.pickOn[i] ? '1' : '0';
    if (key !== mirrored.get()) {
      mirrored.set(key);
      const next: boolean[] = [];
      for (let i = 0; i < count; i++) next.push(e.pickOn[i] === 1);
      scheduleOnRN(report, key[0] === 'o', next);
    }
  });

  /** A touch takes over from the autoPlay, which comes back from rest a while after the last one. */
  const takeOver = () => {
    'worklet';
    touchedAt.set(clock.get());
    loopStart.set(-1);
  };
  const pressPlus = (down: boolean) => {
    'worklet';
    takeOver();
    const e = events.get();
    events.set(down ? { ...e, downAt: clock.get() } : { ...e, upAt: clock.get() });
  };
  const togglePlus = () => {
    'worklet';
    takeOver();
    const e = events.get();
    events.set(e.openAt > e.closeAt ? { ...e, closeAt: clock.get() } : { ...e, openAt: clock.get() });
  };
  const toggleBubble = (index: number) => {
    'worklet';
    takeOver();
    const e = events.get();
    const pickOn = e.pickOn.slice();
    const pickAt = e.pickAt.slice();
    pickOn[index] = pickOn[index] ? 0 : 1;
    pickAt[index] = clock.get();
    events.set({ ...e, pickOn, pickAt });
  };

  const buttonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: buttonScale(events.get(), clock.get(), still) }],
  }));
  const plusStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${plusTurn(events.get(), clock.get(), still)}deg` }],
  }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const arm = PLUS * scale;
  const stroke = PLUS_STROKE * scale;

  return (
    <View style={[styles.screen, { backgroundColor: background }]} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={[styles.head, { paddingTop: insets.top + HEAD_TOP }]}>
        <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
          {title}
        </Text>
        <Text style={styles.hint} numberOfLines={1}>
          {hint}
        </Text>
      </View>

      {shown.map((item, i) => (
        <Bubble
          key={item.title + i}
          index={i}
          item={item}
          slot={SLOTS[i]}
          place={places[i]}
          size={SLOTS[i].size * scale}
          clock={clock}
          events={events}
          still={still}
          open={open}
          picked={picked[i] ?? false}
          onPress={() => {
            select();
            scheduleOnUI(toggleBubble, i);
          }}
        />
      ))}

      <Animated.View
        style={[
          styles.button,
          {
            left: bx - button / 2,
            top: by - button / 2,
            width: button,
            height: button,
            borderRadius: button / 2,
            backgroundColor: accent,
          },
          buttonStyle,
        ]}
      >
        <Pressable
          style={[styles.buttonFace, { borderRadius: button / 2 }]}
          accessibilityRole="button"
          accessibilityLabel={open ? closeLabel : openLabel}
          accessibilityState={{ expanded: open }}
          onPressIn={() => {
            tap();
            scheduleOnUI(pressPlus, true);
          }}
          onPressOut={() => scheduleOnUI(pressPlus, false)}
          onPress={() => scheduleOnUI(togglePlus)}
        >
          <LinearGradient colors={BUTTON_SHEEN} style={StyleSheet.absoluteFill} />
          <Animated.View style={[{ width: arm, height: arm }, plusStyle]}>
            <View
              style={[
                styles.bar,
                {
                  left: 0,
                  top: (arm - stroke) / 2,
                  width: arm,
                  height: stroke,
                  borderRadius: stroke / 2,
                },
              ]}
            />
            <View
              style={[
                styles.bar,
                {
                  left: (arm - stroke) / 2,
                  top: 0,
                  width: stroke,
                  height: arm,
                  borderRadius: stroke / 2,
                },
              ]}
            />
          </Animated.View>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  head: {
    paddingHorizontal: SIDE,
  },
  title: {
    color: INK,
    fontSize: 28,
    lineHeight: TITLE_LINE,
    fontWeight: '600',
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  hint: {
    marginTop: HINT_GAP,
    color: MUTE,
    fontSize: 15,
    lineHeight: HINT_LINE,
    includeFontPadding: false,
  },
  button: {
    position: 'absolute',
    boxShadow: [
      {
        offsetX: 0,
        offsetY: BUTTON_SHADOW_Y,
        blurRadius: BUTTON_SHADOW_BLUR,
        color: BUTTON_SHADOW,
      },
    ],
  },
  buttonFace: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: BUTTON_RIM,
  },
  bar: {
    position: 'absolute',
    backgroundColor: '#ffffff',
  },
});
