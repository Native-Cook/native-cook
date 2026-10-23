import { BlurMask, Canvas, Group, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  cancelAnimation,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

type Place = {
  name: string;
  /** The small line under the name. */
  caption: string;
  /** Address of the photo. */
  photo: string;
  /** The photo's main colour, as six-digit hex: the lower half of the card melts into it, and its shadow is cast in it. */
  tint: string;
  /** The dot after the name, in a colour of the country's flag. */
  dot: string;
};

type Props = {
  places?: Place[];
  /** The card selected at rest. */
  initialIndex?: number;
  exploreLabel?: string;
  /** Steps to the right three times and back again by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the card that became the selected one. */
  onSelect?: (index: number) => void;
  /** Called with the index of the card whose bar was tapped. */
  onExplore?: (index: number) => void;
};

const WIDTH = 350;
const HEIGHT = 380;
const RADIUS = 30;

const CARD_W = 176;
const CARD_H = 244;
const CARD_RADIUS = 16;
// From one card's middle to the next.
const STEP = CARD_W + 14;
// Top of a waiting card; the selected one is `LIFT` higher.
const CARD_Y = 78;
const LIFT = 20;
// How much the selected card's photo grows.
const ZOOM = 0.07;
// Opacity of the text on a waiting card.
const DIM = 0.72;

// The shadow: narrower than the card and lower, growing and darkening under the raised card.
const SHADOW_INSET = 18;
const SHADOW_DROP = 16;
const SHADOW_GROW = 0.1;
const SHADOW_DIM = 0.4;
const SHADOW_FULL = 0.75;
// The shadow's canvas overflows the card by this much on every side.
const BLEED = 52;

// Past either end the row follows the finger at this share of its speed.
const RUBBER = 0.35;
// A flick carries the row on for this many seconds of its speed.
const COAST = 0.18;
// A touch becomes a drag once it has moved this far sideways.
const SLOP = 8;

const SCROLL_SPRING = { mass: 1, stiffness: 170, damping: 20, reduceMotion: ReduceMotion.Never };
const LIFT_SPRING = { mass: 1, stiffness: 260, damping: 18, reduceMotion: ReduceMotion.Never };

// autoPlay (seconds): three steps to the right, then three back, `STEP_TIME` apart.
const AUTO_STEPS = 3;
const STEP_AT = 0.8;
const STEP_TIME = 1.1;
const BACK_AT = 4.4;
const LOOP = 8.4;
// After a touch the loop waits this long, then goes back to the first card and starts over.
const RESUME_AFTER = 2;

function photoUrl(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=540&h=750&q=80`;
}

const PLACES: Place[] = [
  { name: 'Turkey', caption: '1,991 Hotels • 42 Packages', photo: photoUrl('1641128324972-af3212f0f6bd'), tint: '#a8672d', dot: '#e30a17' },
  { name: 'Indonesia', caption: '1,345 Hotels • 24 Packages', photo: photoUrl('1537996194471-e657df975ab4'), tint: '#3f6b54', dot: '#ff4d4d' },
  { name: 'Dubai', caption: '2,345 Hotels • 54 Packages', photo: photoUrl('1518684079-3c830dcef090'), tint: '#155e75', dot: '#22c55e' },
  { name: 'Vietnam', caption: '2,178 Hotels • 32 Packages', photo: photoUrl('1528127269322-539801943592'), tint: '#2e7366', dot: '#ffcd00' },
  { name: 'Greece', caption: '1,764 Hotels • 38 Packages', photo: photoUrl('1570077188670-e3a8d69ac5ff'), tint: '#4a5aa8', dot: '#60a5fa' },
];

/** `hex` with an opacity, as eight-digit hex. */
function fade(hex: string, opacity: number) {
  return `${hex}${Math.round(opacity * 255)
    .toString(16)
    .padStart(2, '0')}`;
}

type Row = {
  /** The row's position, in cards: 2 means the third card is in the middle. */
  scroll: SharedValue<number>;
  index: number;
  count: number;
  /** The card selected at rest. */
  first: number;
  still: boolean;
};

/** How far card `index` is raised, 0…1: it springs up when it is the nearest to the middle, and back down. */
function useLift({ scroll, index, count, first, still }: Row) {
  const lift = useSharedValue(index === first ? 1 : 0);
  useAnimatedReaction(
    () => Math.min(Math.max(Math.round(scroll.get()), 0), count - 1) === index,
    (raised, was) => {
      if (was === null || raised === was) return;
      lift.set(still ? (raised ? 1 : 0) : withSpring(raised ? 1 : 0, LIFT_SPRING));
    },
  );
  return lift;
}

/** The panel takes on the colour of whichever card is raised. */
function Tint({ tint, ...row }: Row & { tint: string }) {
  const lift = useLift(row);
  const style = useAnimatedStyle(() => ({ opacity: lift.get() }));

  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
      <LinearGradient style={StyleSheet.absoluteFill} colors={[fade(tint, 0.34), fade(tint, 0.1)]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} />
    </Animated.View>
  );
}

/** A card's shadow, in its colour. It stays on the panel while the card rises, and spreads. */
function Shadow({ tint, ...row }: Row & { tint: string }) {
  const { scroll, index } = row;
  const lift = useLift(row);
  const style = useAnimatedStyle(() => ({
    transform: [{ translateX: WIDTH / 2 + (index - scroll.get()) * STEP - CARD_W / 2 - BLEED }],
  }));
  const grow = useDerivedValue(() => [
    { translateX: CARD_W / 2 + BLEED },
    { translateY: CARD_H / 2 + BLEED },
    { scale: 1 + SHADOW_GROW * lift.get() },
  ]);
  const opacity = useDerivedValue(() => SHADOW_DIM + (SHADOW_FULL - SHADOW_DIM) * lift.get());

  return (
    <Animated.View pointerEvents="none" style={[styles.shadow, style]}>
      <Canvas style={styles.shadowCanvas}>
        <Group transform={grow} opacity={opacity}>
          <RoundedRect
            x={-CARD_W / 2 + SHADOW_INSET}
            y={-CARD_H / 2 + SHADOW_INSET}
            width={CARD_W - SHADOW_INSET * 2}
            height={CARD_H - SHADOW_INSET}
            r={CARD_RADIUS}
            color={tint}
          >
            <BlurMask blur={14} style="normal" />
          </RoundedRect>
        </Group>
      </Canvas>
    </Animated.View>
  );
}

type CardProps = Row & {
  place: Place;
  exploreLabel: string;
  onPress: () => void;
  onExplore: () => void;
};

function Card({ place, exploreLabel, onPress, onExplore, ...row }: CardProps) {
  const { scroll, index } = row;
  const lift = useLift(row);
  const position = useAnimatedStyle(() => ({
    transform: [{ translateX: WIDTH / 2 + (index - scroll.get()) * STEP - CARD_W / 2 }, { translateY: CARD_Y - LIFT * lift.get() }],
  }));
  const zoom = useAnimatedStyle(() => ({ transform: [{ scale: 1 + ZOOM * lift.get() }] }));
  // The text is dimmer on the cards that wait.
  const text = useAnimatedStyle(() => ({ opacity: DIM + (1 - DIM) * Math.min(1, Math.max(0, lift.get())) }));

  return (
    <Animated.View style={[styles.card, position]}>
      <Pressable style={[styles.cardBody, { backgroundColor: place.tint }]} accessibilityRole="button" accessibilityLabel={`${place.name}, ${place.caption}`} onPress={onPress}>
        <Animated.View style={[StyleSheet.absoluteFill, zoom]}>
          <Image source={place.photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        </Animated.View>
        {/* The lower half melts into the photo's colour, a little darker towards the bottom. */}
        <LinearGradient style={StyleSheet.absoluteFill} colors={[fade(place.tint, 0), fade(place.tint, 0.94), place.tint]} locations={[0.36, 0.6, 1]} />
        <LinearGradient style={StyleSheet.absoluteFill} colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.28)']} locations={[0.55, 1]} />

        <Animated.View pointerEvents="none" style={[styles.text, text]}>
          <View style={styles.nameRow}>
            <Text style={styles.name} numberOfLines={1}>
              {place.name}
            </Text>
            <View style={[styles.dot, { backgroundColor: place.dot }]} />
          </View>
          <Text style={styles.caption} numberOfLines={1}>
            {place.caption}
          </Text>
        </Animated.View>

        <Pressable style={styles.explore} accessibilityRole="button" accessibilityLabel={`${exploreLabel}, ${place.name}`} onPress={onExplore}>
          <Text style={styles.exploreLabel} numberOfLines={1}>
            {exploreLabel}
          </Text>
          <View style={styles.chevron} />
        </Pressable>

        <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.cardRim]} />
      </Pressable>
    </Animated.View>
  );
}

/**
 * A row of tall photo cards on a rounded panel. The lower half of each card melts into a frosted
 * tint of its photo, under the country's name, a small line and an "Explore Now" bar. The row
 * scrolls sideways and snaps: the card that becomes the selected one rises on a spring over a
 * growing shadow of its own colour, its text brightens and its photo zooms a little, while the panel
 * behind takes on its colour.
 */
export function FrostedDestinationCarousel({
  places = PLACES,
  initialIndex = 1,
  exploreLabel = 'Explore Now',
  autoPlay = false,
  onSelect,
  onExplore,
}: Props) {
  const still = useReducedMotion();
  const count = places.length;
  const first = Math.min(Math.max(initialIndex, 0), Math.max(count - 1, 0));
  const auto = autoPlay && !still && count > 1;

  const scroll = useSharedValue(first);
  // A touch stops the loop; `count` restarts the wait before it resumes.
  const [touch, setTouch] = useState({ down: false, count: 0 });
  // Where the row is heading, and the drag in progress.
  const target = useRef(first);
  const drag = useRef({ x: 0, y: 0, from: 0, position: 0, nearest: 0, last: 0, lastAt: 0, speed: 0 });

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  /** Scrolls to card `index`, carrying on at `speed` cards per second if a drag just ended. */
  const scrollTo = useCallback(
    (index: number, speed?: number) => {
      const next = Math.min(Math.max(index, 0), count - 1);
      if (next !== target.current) onSelect?.(next);
      target.current = next;
      scroll.set(still ? next : withSpring(next, speed == null ? SCROLL_SPRING : { ...SCROLL_SPRING, velocity: speed }));
    },
    [count, onSelect, scroll, still],
  );

  const touched = () => setTouch((state) => ({ ...state, count: state.count + 1 }));

  const select = (index: number) => {
    touched();
    if (index === target.current) return;
    tick();
    scrollTo(index);
  };

  // The script a finger would follow: three steps to the right, then three back.
  useEffect(() => {
    const steps = Math.min(AUTO_STEPS, count - 1 - first);
    if (!auto || touch.down || steps < 1) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const k = n % (steps * 2);
      const back = k >= steps;
      // Every step is timed from the script's start, so the loop keeps its period.
      const at = back ? BACK_AT + (k - steps) * STEP_TIME : STEP_AT + k * STEP_TIME;
      const due = begin + (Math.floor(n / (steps * 2)) * LOOP + at) * 1000;
      timer = setTimeout(() => {
        scrollTo(back ? first + steps * 2 - k - 1 : first + k + 1);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touch.count === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, then go back to the first card before the script restarts.
      timer = setTimeout(() => {
        scrollTo(first);
        run(Date.now() + STEP_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touch, count, first, scrollTo]);

  // --- Dragging sideways scrolls the row; taps go to the cards. ---
  const onTouchStart = (e: GestureResponderEvent) => {
    drag.current.x = e.nativeEvent.pageX;
    drag.current.y = e.nativeEvent.pageY;
    return false;
  };

  const isDrag = (e: GestureResponderEvent) => {
    const dx = Math.abs(e.nativeEvent.pageX - drag.current.x);
    return dx > SLOP && dx > Math.abs(e.nativeEvent.pageY - drag.current.y);
  };

  const onGrant = (e: GestureResponderEvent) => {
    cancelAnimation(scroll);
    const from = scroll.get();
    drag.current = { ...drag.current, x: e.nativeEvent.pageX, from, position: from, nearest: Math.round(from), last: e.nativeEvent.pageX, lastAt: e.nativeEvent.timestamp, speed: 0 };
    setTouch((state) => ({ ...state, down: true }));
  };

  const onMove = (e: GestureResponderEvent) => {
    const d = drag.current;
    const free = d.from - (e.nativeEvent.pageX - d.x) / STEP;
    // Past either end the row follows the finger at a third of its speed.
    const last = count - 1;
    d.position = free < 0 ? free * RUBBER : free > last ? last + (free - last) * RUBBER : free;
    scroll.set(d.position);
    if (Math.round(d.position) !== d.nearest) {
      d.nearest = Math.round(d.position);
      tick();
    }
    const seconds = (e.nativeEvent.timestamp - d.lastAt) / 1000;
    if (seconds > 0) d.speed = -(e.nativeEvent.pageX - d.last) / STEP / seconds;
    d.last = e.nativeEvent.pageX;
    d.lastAt = e.nativeEvent.timestamp;
  };

  const onEnd = () => {
    const d = drag.current;
    // A flick carries the row on, but never more than one card past where the drag began.
    const landing = Math.round(d.position + d.speed * COAST);
    const nearest = Math.round(d.from);
    scrollTo(Math.min(Math.max(landing, nearest - 1), nearest + 1), d.speed);
    setTouch((state) => ({ down: false, count: state.count + 1 }));
  };

  const row = { scroll, count, first, still };

  return (
    <View
      style={styles.panel}
      onStartShouldSetResponderCapture={onTouchStart}
      onMoveShouldSetResponderCapture={isDrag}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={onEnd}
      onResponderTerminate={onEnd}
    >
      <LinearGradient style={StyleSheet.absoluteFill} colors={['#f1f4f9', '#dfe5ee']} />
      {places.map((place, index) => (
        <Tint key={index} tint={place.tint} index={index} {...row} />
      ))}
      {places.map((place, index) => (
        <Shadow key={index} tint={place.tint} index={index} {...row} />
      ))}
      {places.map((place, index) => (
        <Card
          key={index}
          place={place}
          index={index}
          exploreLabel={exploreLabel}
          onPress={() => select(index)}
          onExplore={() => {
            if (index !== target.current) select(index);
            else {
              touched();
              onExplore?.(index);
            }
          }}
          {...row}
        />
      ))}
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.rim]} />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  rim: {
    borderRadius: RADIUS,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.7)',
  },
  shadow: {
    position: 'absolute',
    left: 0,
    top: CARD_Y + SHADOW_DROP - BLEED,
  },
  shadowCanvas: {
    width: CARD_W + BLEED * 2,
    height: CARD_H + BLEED * 2,
  },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD_W,
    height: CARD_H,
  },
  cardBody: {
    flex: 1,
    borderRadius: CARD_RADIUS,
    overflow: 'hidden',
  },
  cardRim: {
    borderRadius: CARD_RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  text: {
    position: 'absolute',
    left: 14,
    top: 150,
    width: CARD_W - 28,
  },
  nameRow: {
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  name: {
    color: '#ffffff',
    fontSize: 17,
    fontWeight: '700',
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.9)',
  },
  caption: {
    marginTop: 3,
    height: 12,
    lineHeight: 12,
    color: 'rgba(255,255,255,0.85)',
    fontSize: 9.5,
    fontVariant: ['tabular-nums'],
  },
  explore: {
    position: 'absolute',
    left: 12,
    top: 196,
    width: CARD_W - 24,
    height: 36,
    // With the hairline border the label starts 12 points in.
    paddingHorizontal: 11,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    backgroundColor: 'rgba(0,0,0,0.22)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  exploreLabel: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 12.5,
    fontWeight: '500',
  },
  // Two sides of a small square turned 45°: a chevron pointing right.
  chevron: {
    width: 6,
    height: 6,
    marginRight: 1,
    borderTopWidth: 1.5,
    borderRightWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.92)',
    transform: [{ rotate: '45deg' }],
  },
});
