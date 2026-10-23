import { Canvas, Path as SkiaPath, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export type ExpandingTripCardDay = {
  label: string;
  date: string;
  activity: string;
  duration: string;
  distance: string;
};

export type ExpandingTripCardTrip = {
  /** The name in capitals; a line break splits it over two lines. */
  title: string;
  dates: string;
  /** The number in the pink badge, and the word under it. */
  length: string;
  lengthUnit: string;
  arrivalLabel: string;
  arrivalPlace: string;
  arrivalTime: string;
  /** Address of the photo, and the flat colour shown until it arrives. */
  photo: string;
  tint: string;
  /** The day cards shown once the trip is open. */
  days: ExpandingTripCardDay[];
};

type Props = {
  trips?: ExpandingTripCardTrip[];
  /** Plays the interaction by itself: next trip, back, open, close. */
  autoPlay?: boolean;
  heading?: string;
  activitiesLabel?: string;
  tabLabel?: string;
  /** Called with the index of the trip the row snaps to. */
  onSelect?: (index: number) => void;
  /** Called with the index of the trip that was opened or closed. */
  onOpen?: (index: number) => void;
  onClose?: (index: number) => void;
  onSearch?: () => void;
};

const WIDTH = 340;
const HEIGHT = 600;
const RADIUS = 28;

const CARD_WIDTH = 232;
const CARD_HEIGHT = 290;
const CARD_LEFT = 22;
const CARD_TOP = 108;
const CARD_RADIUS = 10;
// Distance from one card to the next.
const STEP = 244;
// Past either end the row follows the finger at a third of its speed.
const RESISTANCE = 0.3;
const DRAG_SLOP = 8;
// Seconds of travel a release is credited with, as SwiftUI predicts the end of a drag.
const THROW = 0.25;

// The line under the row, and the notch that points up at the current card.
const LINE_Y = 424;
const NOTCH_X = 46;
const NOTCH_HEIGHT = 9;
const ARRIVAL_TOP = 446;
// How far the arrival details of a neighbouring card sit to the side.
const ARRIVAL_SHIFT = 36;
const TAB_Y = 548;

// Open: the title's place and size, and the day cards.
const TITLE_INSET = { x: 16, y: 16 };
const OPEN_TITLE = { x: 24, y: 78 };
const OPEN_TITLE_SCALE = 1.13;
const ACTIVITIES_Y = 304;
const DAY_TOP = 322;
const DAY_LEFT = 24;
const DAY_SIZE = { width: 146, height: 172 };
const MAP_SIZE = { width: 122, height: 56 };
const DAY_GAP = 12;
const DAY_RISE = 120;

const SLIDE_SPRING = { mass: 1, stiffness: 170, damping: 24 };
const OPEN_SPRING = { mass: 1, stiffness: 170, damping: 22 };
// The day cards spring up one after another, on a clock that runs while the trip opens.
const DAY_AT = 0.28;
const DAY_GAP_SECONDS = 0.09;
const DAY_SPRING = { stiffness: 210, damping: 19 };
const STAGE_SECONDS = 1.3;
const STAGE_LEAVE = 0.3;
const REDUCED_MS = 200;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// autoPlay (seconds): next trip, back to the first, open it, close it.
const NEXT_AT = 0.8;
const BACK_AT = 2.3;
const OPEN_AT = 3.8;
const CLOSE_AT = 6.2;
const LOOP = 8;
// After a touch the loop waits this long, returns to the first card, and starts over.
const IDLE = 2.5;
const SETTLE = 1.2;

const INK = '#14171a';
const MUTE = '#9a9fa6';
const LINE = '#e1e3e7';
const MINT = '#5ed3b0';
const BLUE = '#7f9cf5';
const PINK = '#f0688a';
const PINK_PALE = '#fde4ea';

const SEARCH_ICON =
  'M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5';
const BACK_ICON = 'M12.5 4.5 L7 10 L12.5 15.5';
const BOOT_ICON =
  'M5 3 L9 3 L9 8.5 L13.5 10.5 C14.6 11 15.2 11.8 15.2 13 L15.2 13.8 L3.6 13.8 L3.6 10.5 C3.6 9.4 5 8.6 5 7 Z M2.8 15.6 L15.6 15.6';
const ARRIVAL_ICON = 'M5 5 L13 13 M13 7 L13 13 L7 13 M4 16.5 L16 16.5';
const TRIPS_ICON =
  'M11 3.5 C15.14 3.5 18.5 6.86 18.5 11 C18.5 15.14 15.14 18.5 11 18.5 C6.86 18.5 3.5 15.14 3.5 11 C3.5 6.86 6.86 3.5 11 3.5 Z M3.5 11 L18.5 11 M11 3.5 C8 6 8 16 11 18.5 C14 16 14 6 11 3.5';
const SAVED_ICON = 'M6.5 4 L15.5 4 L15.5 18 L11 14.5 L6.5 18 Z';
const ALERTS_ICON = 'M6 15 L6 10 C6 7 8 5 11 5 C14 5 16 7 16 10 L16 15 L17.5 16.5 L4.5 16.5 Z M9.5 18.5 L12.5 18.5';
// The little map on a day card: water, a route and a pin with its dot.
const MAP_WATER = 'M70 0 L126 0 L126 30 C110 34 96 22 84 26 C76 28 72 12 70 0 Z';
const MAP_ROUTE = 'M10 44 C30 30 44 46 62 34 C74 26 80 30 88 22';
const MAP_PIN = 'M88 24 C84 19 82.5 17 82.5 14.5 C82.5 11.5 85 9 88 9 C91 9 93.5 11.5 93.5 14.5 C93.5 17 92 19 88 24 Z';
const MAP_DOT = 'M88 12.7 C89 12.7 89.8 13.5 89.8 14.5 C89.8 15.5 89 16.3 88 16.3 C87 16.3 86.2 15.5 86.2 14.5 C86.2 13.5 87 12.7 88 12.7 Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/**
 * How far day card number `order` has come up, `stage` seconds after the trip was opened: a
 * spring of mass 1 released at rest, so it runs a little past its place and settles.
 */
function dayAt(stage: number, order: number, reduced: boolean) {
  'worklet';
  const t = stage - DAY_AT - order * DAY_GAP_SECONDS;
  if (t <= 0) return 0;
  if (reduced) return Math.min(1, t / 0.2);
  const decay = DAY_SPRING.damping / 2;
  const omega = Math.sqrt(DAY_SPRING.stiffness - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(omega * t) + (decay / omega) * Math.sin(omega * t));
}

type Motion = {
  /** Index of the card at the left of the row; fractional while the row moves. */
  pos: SharedValue<number>;
  open: SharedValue<number>;
  /** Seconds since the trip was opened: the clock the day cards arrive on. */
  stage: SharedValue<number>;
  /** Under reduced motion the card jumps to the panel's size halfway through the fade. */
  reduced: boolean;
};

function Icon({ path, size, color, width = 1.5 }: { path: string; size: number; color: string; width?: number }) {
  return (
    <Svg width={size} height={size}>
      <Path d={path} stroke={color} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/**
 * A hairline across the panel with a bump under the card that is current; the bump shrinks as
 * its card leaves and the next one's grows.
 */
function NotchLine({ count, pos }: { count: number; pos: SharedValue<number> }) {
  const path = useDerivedValue(() => {
    const line = Skia.Path.Make();
    line.moveTo(0, LINE_Y);
    for (let i = 0; i < count; i++) {
      const height = NOTCH_HEIGHT * Math.max(0, 1 - Math.abs(i - pos.get()));
      const x = CARD_LEFT + NOTCH_X + (i - pos.get()) * STEP;
      if (height <= 0.01 || x <= 16 || x >= WIDTH - 16) continue;
      line.lineTo(x - 16, LINE_Y);
      line.cubicTo(x - 8, LINE_Y, x - 6, LINE_Y - height, x, LINE_Y - height);
      line.cubicTo(x + 6, LINE_Y - height, x + 8, LINE_Y, x + 16, LINE_Y);
    }
    line.lineTo(WIDTH, LINE_Y);
    return line;
  });
  return (
    <Canvas style={styles.fill}>
      <SkiaPath path={path} style="stroke" strokeWidth={1.2} color={LINE} />
    </Canvas>
  );
}

function Arrival({ trip, index, pos }: { trip: ExpandingTripCardTrip; index: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({
    opacity: Math.max(0, 1 - Math.abs(index - pos.get()) * 1.6),
    transform: [{ translateX: (index - pos.get()) * ARRIVAL_SHIFT }],
  }));
  return (
    <Animated.View style={[styles.arrival, style]}>
      <View style={styles.arrivalDisc}>
        <Icon path={ARRIVAL_ICON} size={20} color={PINK} width={1.6} />
      </View>
      <View>
        <Text style={styles.arrivalLabel} numberOfLines={1}>
          {trip.arrivalLabel}
        </Text>
        <Text style={styles.arrivalPlace} numberOfLines={1}>
          {trip.arrivalPlace}
        </Text>
        <Text style={styles.arrivalTime} numberOfLines={1}>
          {trip.arrivalTime}
        </Text>
      </View>
    </Animated.View>
  );
}

function DayCard({ day, order, stage, reduced }: { day: ExpandingTripCardDay; order: number; stage: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const e = dayAt(stage.get(), order, reduced);
    return { opacity: clamp01(e * 2.5), transform: [{ translateY: reduced ? 0 : (1 - e) * DAY_RISE }] };
  });
  return (
    <Animated.View style={[styles.day, { left: DAY_LEFT + order * (DAY_SIZE.width + DAY_GAP) }, style]}>
      <Text style={styles.dayLabel} numberOfLines={1}>
        {day.label}
      </Text>
      <Text style={styles.dayDate} numberOfLines={1}>
        {day.date}
      </Text>
      <Text style={styles.dayActivity} numberOfLines={1}>
        {day.activity}
      </Text>
      <View style={styles.map}>
        <Svg width={MAP_SIZE.width} height={MAP_SIZE.height}>
          <Path d={MAP_WATER} fill="#b9def0" />
          <Path d={MAP_ROUTE} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" fill="none" />
          <Path d={MAP_PIN} fill="#1c1f24" />
          <Path d={MAP_DOT} fill="#ffffff" />
        </Svg>
      </View>
      <View style={styles.dayFacts}>
        <Text style={styles.dayFact}>{day.duration}</Text>
        <Text style={styles.dayFact}>{day.distance}</Text>
      </View>
    </Animated.View>
  );
}

/** One trip. It grows from a card in the row to the photo filling the panel. */
function Card({
  trip,
  index,
  opened,
  touchable,
  activitiesLabel,
  onClose,
  pos,
  open,
  stage,
  reduced,
}: { trip: ExpandingTripCardTrip; index: number; opened: boolean; touchable: boolean; activitiesLabel: string; onClose: () => void } & Motion) {
  const frame = useAnimatedStyle(() => {
    const o = clamp01(open.get());
    const g = !opened ? 0 : reduced ? (open.get() > 0.5 ? 1 : 0) : open.get();
    const left = CARD_LEFT + (index - pos.get()) * STEP;
    return {
      left: left * (1 - g),
      top: CARD_TOP * (1 - g),
      width: CARD_WIDTH + (WIDTH - CARD_WIDTH) * g,
      height: CARD_HEIGHT + (HEIGHT - CARD_HEIGHT) * g,
      borderRadius: CARD_RADIUS + (RADIUS - CARD_RADIUS) * clamp01(g),
      // What is behind the open card fades over the first half of its growth.
      opacity: opened ? 1 : 1 - Math.min(1, o * 2),
      zIndex: opened ? 1 : 0,
    };
  });
  const titleStyle = useAnimatedStyle(() => {
    const g = !opened ? 0 : reduced ? (open.get() > 0.5 ? 1 : 0) : open.get();
    return {
      transform: [
        { translateX: TITLE_INSET.x + (OPEN_TITLE.x - TITLE_INSET.x) * g },
        { translateY: TITLE_INSET.y + (OPEN_TITLE.y - TITLE_INSET.y) * g },
        { scale: 1 + (OPEN_TITLE_SCALE - 1) * g },
      ],
    };
  });
  const badgesStyle = useAnimatedStyle(() => {
    const g = !opened ? 0 : reduced ? (open.get() > 0.5 ? 1 : 0) : open.get();
    return { opacity: 1 - Math.min(1, Math.max(0, g) * 2.5) };
  });
  const closeStyle = useAnimatedStyle(() => ({ opacity: clamp01(clamp01(open.get()) * 2 - 1) }));
  const activitiesStyle = useAnimatedStyle(() => ({ opacity: clamp01((stage.get() - 0.2) / 0.25) }));

  return (
    <Animated.View style={[styles.card, { backgroundColor: trip.tint }, frame]} pointerEvents="box-none">
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Image source={{ uri: trip.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        <LinearGradient colors={['rgba(0,0,0,0.34)', 'rgba(0,0,0,0)']} style={styles.shade} />
        <Animated.View style={[styles.titleBlock, titleStyle]}>
          <Text style={styles.title}>{trip.title}</Text>
          <Text style={styles.dates}>{trip.dates}</Text>
        </Animated.View>
        <Animated.View style={[styles.badges, badgesStyle]}>
          <View style={[styles.badge, { backgroundColor: BLUE }]}>
            <Icon path={BOOT_ICON} size={18} color="#ffffff" width={1.4} />
          </View>
          <View style={[styles.badge, { backgroundColor: PINK }]}>
            <Text style={styles.length}>{trip.length}</Text>
            <Text style={styles.lengthUnit}>{trip.lengthUnit}</Text>
          </View>
        </Animated.View>
        {opened && (
          <>
            <Animated.Text style={[styles.activities, activitiesStyle]} numberOfLines={1}>
              {activitiesLabel}
            </Animated.Text>
            {trip.days.map((day, order) => (
              <DayCard key={order} day={day} order={order} stage={stage} reduced={reduced} />
            ))}
          </>
        )}
      </View>
      {opened && (
        <Animated.View style={[styles.close, closeStyle]} pointerEvents={touchable ? 'auto' : 'none'}>
          <Pressable onPress={onClose} hitSlop={8} accessibilityRole="button" accessibilityLabel="Close" style={styles.closeDisc}>
            <Icon path={BACK_ICON} size={20} color="#ffffff" width={1.8} />
          </Pressable>
        </Animated.View>
      )}
    </Animated.View>
  );
}

/**
 * A white panel with a row of tall photo cards that snaps card by card; a notch in the line under
 * the row and the arrival details follow the current card. Tapping that card grows it from its
 * place until the photo fills the panel: its title moves to the top left, the rest fades out and a
 * row of white day cards springs up from the bottom. A close button brings everything back.
 */
export function ExpandingTripCard({
  trips = TRIPS,
  autoPlay = false,
  heading = 'TRIPS',
  activitiesLabel = 'ACTIVITIES',
  tabLabel = 'Trips',
  onSelect,
  onOpen,
  onClose,
  onSearch,
}: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const count = trips.length;

  const pos = useSharedValue(0);
  const open = useSharedValue(0);
  const stage = useSharedValue(0);
  // Where the row is heading, for the code that runs outside the animation.
  const state = useRef({ target: 0, open: false, from: 0, pressed: false, dragging: false, startX: 0, lastX: 0, lastTime: 0, velocity: 0 });
  const [openIndex, setOpenIndex] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  // Counts touches: a change restarts the autoPlay loop after a pause.
  const [touches, setTouches] = useState(0);

  const slide = useCallback(
    (target: number, velocity = 0) => {
      state.current.target = target;
      pos.set(reduced ? target : withSpring(target, { ...SLIDE_SPRING, velocity }));
    },
    [reduced, pos],
  );

  const grow = useCallback(
    (to: number) => {
      open.set(
        reduced ? withTiming(to, { duration: REDUCED_MS, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }) : withSpring(to, OPEN_SPRING),
      );
    },
    [reduced, open],
  );

  const openCard = useCallback(
    (index: number) => {
      state.current.open = true;
      setOpenIndex(index);
      setIsOpen(true);
      grow(1);
      stage.set(
        withTiming(STAGE_SECONDS, {
          duration: (STAGE_SECONDS - stage.get()) * 1000,
          easing: Easing.linear,
          reduceMotion: ReduceMotion.Never,
        }),
      );
    },
    [grow, stage],
  );

  const closeCard = useCallback(() => {
    state.current.open = false;
    setIsOpen(false);
    grow(0);
    stage.set(withTiming(0, { duration: STAGE_LEAVE * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  }, [grow, stage]);

  useEffect(() => {
    if (!auto || count < 2) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const cycle = () => {
      timers.length = 0;
      timers.push(setTimeout(() => slide(1), NEXT_AT * 1000));
      timers.push(setTimeout(() => slide(0), BACK_AT * 1000));
      timers.push(setTimeout(() => openCard(0), OPEN_AT * 1000));
      timers.push(setTimeout(closeCard, CLOSE_AT * 1000));
      timers.push(setTimeout(cycle, LOOP * 1000));
    };
    if (touches === 0) {
      cycle();
    } else {
      // After a touch the loop waits, returns to the first card and starts over.
      timers.push(
        setTimeout(() => {
          if (state.current.pressed) return;
          if (state.current.open) closeCard();
          slide(0);
          timers.push(setTimeout(cycle, SETTLE * 1000));
        }, IDLE * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [auto, touches, count, slide, openCard, closeCard]);

  const rubber = (value: number) => {
    const last = count - 1;
    if (value < 0) return value * RESISTANCE;
    if (value > last) return last + (value - last) * RESISTANCE;
    return value;
  };

  // --- The row: a horizontal drag moves it, a tap opens the current card or brings a neighbour forward. ---
  const onGrant = (e: GestureResponderEvent) => {
    const s = state.current;
    s.pressed = true;
    s.dragging = false;
    s.startX = e.nativeEvent.pageX;
    s.lastX = e.nativeEvent.pageX;
    s.lastTime = e.nativeEvent.timestamp;
    s.velocity = 0;
    setTouches((n) => n + 1);
  };

  const onMove = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.open) return;
    const dx = e.nativeEvent.pageX - s.startX;
    if (!s.dragging) {
      if (Math.abs(dx) < DRAG_SLOP) return;
      s.dragging = true;
      s.from = s.target;
      cancelAnimation(pos);
    }
    // Cards per second, smoothed over the last few events.
    const dt = e.nativeEvent.timestamp - s.lastTime;
    if (dt > 0) s.velocity = 0.6 * ((-(e.nativeEvent.pageX - s.lastX) / dt) * 1000) / STEP + 0.4 * s.velocity;
    s.lastX = e.nativeEvent.pageX;
    s.lastTime = e.nativeEvent.timestamp;
    pos.set(rubber(s.from - dx / STEP));
  };

  const onRelease = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      // A swipe moves one card at most, however far it is thrown.
      const thrown = Math.round(s.from - (e.nativeEvent.pageX - s.startX) / STEP + s.velocity * THROW);
      const target = Math.min(count - 1, Math.max(0, Math.min(s.from + 1, Math.max(s.from - 1, thrown))));
      slide(target, s.velocity);
      if (target !== s.from) {
        if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
        onSelect?.(target);
      }
    } else if (!s.open) {
      // The card under the finger: the current one opens, a neighbour comes forward.
      const index = Math.floor((e.nativeEvent.locationX - CARD_LEFT) / STEP + s.target);
      if (index === s.target) {
        if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        openCard(index);
        onOpen?.(index);
      } else if (index >= 0 && index < count) {
        if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
        slide(index);
        onSelect?.(index);
      }
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const onTerminate = () => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      slide(s.from);
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const close = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    closeCard();
    setTouches((n) => n + 1);
    onClose?.(openIndex);
  };

  // What is behind the open card fades over the first half of its growth.
  const baseStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, clamp01(open.get()) * 2) }));

  if (count === 0) return null;
  const motion: Motion = { pos, open, stage, reduced };

  return (
    <View style={styles.shadow}>
      <View style={styles.panel}>
        <Animated.View style={[styles.fill, baseStyle]}>
          <Pressable style={styles.search} onPress={() => onSearch?.()} accessibilityRole="button" accessibilityLabel="Search">
            <Icon path={SEARCH_ICON} size={20} color="#ffffff" width={1.7} />
          </Pressable>
          <Text style={styles.heading} numberOfLines={1}>
            {heading}
          </Text>
          <View pointerEvents="none" style={styles.fill}>
            <NotchLine count={count} pos={pos} />
          </View>
          {trips.map((trip, index) => (
            <Arrival key={index} trip={trip} index={index} pos={pos} />
          ))}
          <View pointerEvents="none" style={styles.tabs}>
            <Icon path={TRIPS_ICON} size={22} color={INK} />
            <Icon path={SAVED_ICON} size={22} color="#b9bcc2" />
            <Icon path={ALERTS_ICON} size={22} color="#b9bcc2" />
          </View>
          <Text style={styles.tabLabel} numberOfLines={1}>
            {tabLabel}
          </Text>
        </Animated.View>

        <View
          style={styles.row}
          accessible
          accessibilityRole="button"
          accessibilityLabel={`${trips[0].title.replace('\n', ' ')}, ${trips[0].dates}`}
          onStartShouldSetResponder={() => !isOpen}
          onResponderTerminationRequest={() => false}
          onResponderGrant={onGrant}
          onResponderMove={onMove}
          onResponderRelease={onRelease}
          onResponderTerminate={onTerminate}
        />

        <View pointerEvents="box-none" style={styles.fill}>
          {trips.map((trip, index) => (
            <Card
              key={index}
              trip={trip}
              index={index}
              opened={index === openIndex}
              touchable={isOpen}
              activitiesLabel={activitiesLabel}
              onClose={close}
              {...motion}
            />
          ))}
        </View>
      </View>
    </View>
  );
}

function photoUrl(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=900&q=80`;
}

const TRIPS: ExpandingTripCardTrip[] = [
  {
    title: 'SEILAND\nNORWAY',
    dates: '3 AUG - 9 AUG',
    length: '6',
    lengthUnit: 'DAYS',
    arrivalLabel: 'ARRIVAL',
    arrivalPlace: 'OSLO LUFTHAVN',
    arrivalTime: '3 Aug, 12:00 PM',
    photo: photoUrl('1464822759023-fed622ff2c3b'),
    tint: '#6f8aa0',
    days: [
      { label: 'DAY 1', date: '3 Aug.', activity: 'Hiking', duration: '8 hours', distance: '22 km' },
      { label: 'DAY 2', date: '4 Aug.', activity: 'Kayaking', duration: '5 hours', distance: '14 km' },
      { label: 'DAY 3', date: '5 Aug.', activity: 'Fishing', duration: '4 hours', distance: '6 km' },
    ],
  },
  {
    title: 'MALLORCA\nSPAIN',
    dates: '18 AUG - 25 AUG',
    length: '7',
    lengthUnit: 'DAYS',
    arrivalLabel: 'ARRIVAL',
    arrivalPlace: 'PALMA DE MALLORCA',
    arrivalTime: '18 Aug, 9:40 AM',
    photo: photoUrl('1506929562872-bb421503ef21'),
    tint: '#3fb3b8',
    days: [
      { label: 'DAY 1', date: '18 Aug.', activity: 'Sailing', duration: '6 hours', distance: '30 km' },
      { label: 'DAY 2', date: '19 Aug.', activity: 'Snorkelling', duration: '3 hours', distance: '2 km' },
      { label: 'DAY 3', date: '20 Aug.', activity: 'Cycling', duration: '5 hours', distance: '48 km' },
    ],
  },
  {
    title: 'DOLOMITES\nITALY',
    dates: '2 SEP - 8 SEP',
    length: '6',
    lengthUnit: 'DAYS',
    arrivalLabel: 'ARRIVAL',
    arrivalPlace: 'VENICE MARCO POLO',
    arrivalTime: '2 Sep, 2:15 PM',
    photo: photoUrl('1501785888041-af3ef285b470'),
    tint: '#4aa39c',
    days: [
      { label: 'DAY 1', date: '2 Sep.', activity: 'Rowing', duration: '2 hours', distance: '4 km' },
      { label: 'DAY 2', date: '3 Sep.', activity: 'Hiking', duration: '7 hours', distance: '18 km' },
      { label: 'DAY 3', date: '4 Sep.', activity: 'Climbing', duration: '6 hours', distance: '9 km' },
    ],
  },
];

const styles = StyleSheet.create({
  shadow: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    boxShadow: '0px 16px 48px rgba(0,0,0,0.16)',
  },
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#ffffff',
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  search: {
    position: 'absolute',
    left: WIDTH - 22 - 36,
    top: 22,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: MINT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heading: {
    position: 'absolute',
    left: CARD_LEFT,
    top: 76,
    width: 200,
    color: INK,
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '800',
    letterSpacing: 1.2,
  },
  arrival: {
    position: 'absolute',
    left: CARD_LEFT,
    top: ARRIVAL_TOP,
    width: WIDTH - CARD_LEFT * 2,
    height: 42,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  arrivalDisc: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: PINK_PALE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arrivalLabel: {
    color: PINK,
    fontSize: 8,
    lineHeight: 11,
    fontWeight: '800',
    letterSpacing: 1,
  },
  arrivalPlace: {
    color: INK,
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '800',
    letterSpacing: 0.8,
  },
  arrivalTime: {
    color: MUTE,
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '400',
  },
  tabs: {
    position: 'absolute',
    left: 68 - 11,
    top: TAB_Y - 11,
    width: 204 + 22,
    height: 22,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  tabLabel: {
    position: 'absolute',
    left: 68 - 30,
    top: TAB_Y + 20 - 6,
    width: 60,
    textAlign: 'center',
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '700',
  },
  row: {
    position: 'absolute',
    left: 0,
    top: CARD_TOP,
    width: WIDTH,
    height: CARD_HEIGHT,
  },
  card: {
    position: 'absolute',
    overflow: 'hidden',
  },
  shade: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    height: 150,
  },
  titleBlock: {
    position: 'absolute',
    left: 0,
    top: 0,
    gap: 6,
    transformOrigin: 'left top',
  },
  title: {
    color: '#ffffff',
    fontSize: 23,
    lineHeight: 27,
    fontWeight: '800',
    letterSpacing: 1.5,
  },
  dates: {
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 12,
    fontWeight: '700',
    letterSpacing: 1,
  },
  badges: {
    position: 'absolute',
    left: 14,
    bottom: 14,
    flexDirection: 'row',
    gap: 6,
  },
  badge: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
  },
  length: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 14,
    fontWeight: '800',
  },
  lengthUnit: {
    color: '#ffffff',
    fontSize: 5.5,
    lineHeight: 7,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  close: {
    position: 'absolute',
    left: 20,
    top: 22,
  },
  closeDisc: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(20,24,28,0.36)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  activities: {
    position: 'absolute',
    left: DAY_LEFT,
    top: ACTIVITIES_Y,
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '800',
    letterSpacing: 1.5,
  },
  day: {
    position: 'absolute',
    top: DAY_TOP,
    width: DAY_SIZE.width,
    height: DAY_SIZE.height,
    borderRadius: CARD_RADIUS,
    backgroundColor: '#ffffff',
  },
  dayLabel: {
    position: 'absolute',
    left: 12,
    top: 12,
    color: INK,
    fontSize: 7.5,
    lineHeight: 10,
    fontWeight: '800',
    letterSpacing: 0.8,
  },
  dayDate: {
    position: 'absolute',
    left: 12,
    top: 25,
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '700',
  },
  dayActivity: {
    position: 'absolute',
    left: 12,
    top: 62,
    color: INK,
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '600',
  },
  map: {
    position: 'absolute',
    left: 12,
    top: 82,
    width: MAP_SIZE.width,
    height: MAP_SIZE.height,
    borderRadius: 6,
    backgroundColor: '#cfe8c6',
    overflow: 'hidden',
  },
  dayFacts: {
    position: 'absolute',
    left: 12,
    top: 148,
    width: MAP_SIZE.width,
    height: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  dayFact: {
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '700',
  },
});
