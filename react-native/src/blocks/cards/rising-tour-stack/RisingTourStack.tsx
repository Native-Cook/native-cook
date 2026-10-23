import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export type RisingTourDestination = {
  /** Set in huge capitals above the stack. */
  city: string;
  title: string;
  text: string;
  rating: string;
  price: string;
  /** Address of the photo, and the flat colour shown until it arrives. */
  photo: string;
  tint: string;
};

type Props = {
  destinations?: RisingTourDestination[];
  /** Swipes through the destinations by itself and deals the deck again, in a loop. */
  autoPlay?: boolean;
  heading?: string;
  availableLabel?: string;
  mapLabel?: string;
  /** Called with the index of the destination that comes to the front. */
  onChange?: (index: number) => void;
  /** Called with the destination's index and whether it is now liked. */
  onLike?: (index: number, liked: boolean) => void;
  onMap?: (index: number) => void;
  onBook?: (index: number) => void;
  onBack?: () => void;
  onSearch?: () => void;
};

const WIDTH = 340;
const HEIGHT = 600;
const RADIUS = 32;

const BAR_Y = 36;
// The name is set large in the system serif and squeezed sideways into a condensed face.
const NAME_Y = 104;
const NAME_SIZE = 88;
const NAME_LINE = 104;
const NAME_SQUEEZE = 0.62;
// Each letter fades in over LETTER_SECONDS while it slides in from this offset, LETTER_GAP after the one before.
const LETTER_FROM = { x: 22, y: -10 };
const LETTER_SECONDS = 0.45;
const LETTER_GAP = 0.07;
const TYPE_SECONDS = 1.2;
// The previous name is gone after this long.
const NAME_FADE = 0.18;

const CARD_WIDTH = 316;
const CARD_HEIGHT = 290;
const CARD_TOP = 232;
const CARD_RADIUS = 22;
const PHOTO_INSET = 4;
const PHOTO_HEIGHT = 178;
const PHOTO_RADIUS = 18;
// Each place further back shows this much above the card in front, and is this much narrower.
const PEEK = 22;
const SHRINK = 0.115;
// Degrees the third card leans.
const LEAN = 3;
// Points a thrown card travels, and degrees it turns on the way.
const AWAY = 560;
const AWAY_TURN = 7;
// How far below its place the deck starts when it is dealt.
const RISE = 380;
// A card's face clears as it comes forward: the grey over it is this many times its depth.
const COVER_RATE = 1.2;
const COVER_NEAR = '#9aa3a3';
const COVER_FAR = '#7d8787';

const STACK_TOP = 180;
const BUTTONS_TOP = 536;
const BUTTON_HEIGHT = 44;
const BUTTONS_RISE = 8;

// A release throws the card past this distance or this speed (points, points per second).
const THROW_DISTANCE = 80;
const THROW_SPEED = 500;
const DRAG_SLOP = 6;

// The front of the stack follows a spring of mass 1, damped just short of critically: it must
// not run past its target, or the next card would start to leave.
const FRONT_SPRING = { mass: 1, stiffness: 144, damping: 23.5 };
// Dealing (seconds from its start): the deck rises on one spring, the cards behind pop on another.
const RISE_AT = 0.1;
const RISE_SPRING = { stiffness: 120, damping: 17 };
const POP_AT = 0.55;
const POP_GAP = 0.17;
const POP_SPRING = { stiffness: 260, damping: 16 };
const BUTTONS_AT = 0.95;
const BUTTONS_SECONDS = 0.35;
const DEAL_SECONDS = 1.8;
// Pause between the last card leaving and the next deal.
const DEAL_PAUSE = 0.45;
const HEART_SPRING = { stiffness: 420, damping: 13 };

// autoPlay (seconds): the first throw, then one per destination spread evenly, then the deal.
const FIRST = 0.8;
const TAIL = 3.2;
const LOOP = 8;
// After a touch the loop waits this long before it deals again and starts over.
const IDLE = 2.5;

const STAR = '#ffe14d';
const HEART = '#ff5a79';
const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const BACK_ICON = 'M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5';
const SEARCH_ICON =
  'M9 2.8 C12.42 2.8 15.2 5.58 15.2 9 C15.2 12.42 12.42 15.2 9 15.2 C5.58 15.2 2.8 12.42 2.8 9 C2.8 5.58 5.58 2.8 9 2.8 Z M13.6 13.6 L17.5 17.5';
const MAP_ICON = 'M1.5 4 L6.5 2 L11.5 4 L16.5 2 L16.5 14 L11.5 16 L6.5 14 L1.5 16 Z M6.5 2 L6.5 14 M11.5 4 L11.5 16';
const STAR_ICON = 'M8 0.8 L9.82 5.49 L14.85 5.78 L10.95 8.96 L12.23 13.83 L8 11.1 L3.77 13.83 L5.05 8.96 L1.15 5.78 L6.18 5.49 Z';
const HEART_ICON =
  'M10 17 C4.5 13.2 2 10.4 2 7.3 C2 4.9 3.9 3.2 6.1 3.2 C7.7 3.2 9.1 4 10 5.4 C10.9 4 12.3 3.2 13.9 3.2 C16.1 3.2 18 4.9 18 7.3 C18 10.4 15.5 13.2 10 17 Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** Where a spring of mass 1 released at rest has got to after `t` seconds, on its way from 0 to 1. */
function springAt(t: number, spring: { stiffness: number; damping: number }) {
  'worklet';
  if (t <= 0) return 0;
  const decay = spring.damping / 2;
  const omega = Math.sqrt(spring.stiffness - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(omega * t) + (decay / omega) * Math.sin(omega * t));
}

/** How far letter number `order` has arrived, `type` seconds after the name changed. */
function letterAt(type: number, order: number) {
  'worklet';
  const p = clamp01((type - order * LETTER_GAP) / LETTER_SECONDS);
  return 1 - Math.pow(1 - p, 3);
}

/** The buttons fade in at the end of a deal, and out while the last card leaves. */
function buttonsAt(front: number, deal: number, count: number) {
  'worklet';
  const p = clamp01((deal - BUTTONS_AT) / BUTTONS_SECONDS);
  const leaving = clamp01((front - (count - 1)) / 0.3);
  return Math.min(1 - Math.pow(1 - p, 3), 1 - leaving);
}

/** Where card `i` is: `front` is the index of the card at the front and `deal` the seconds since the deck was dealt. */
function poseOf(i: number, front: number, deal: number, reduced: boolean) {
  'worklet';
  const rel = i - front;
  if (rel < 0) {
    // Thrown: up and away, turning a little. Under reduced motion it fades where it is.
    const gone = Math.min(1, -rel);
    return {
      shift: reduced ? 0 : -gone * AWAY,
      scale: 1,
      rotation: reduced ? 0 : gone * AWAY_TURN,
      opacity: reduced ? 1 - gone : 1,
      cover: 0,
      coverFar: 0,
    };
  }
  const moving = reduced ? DEAL_SECONDS : deal;
  const rise = springAt(moving - RISE_AT, RISE_SPRING);
  // A card behind stays hidden under the front one until its turn to pop up.
  const pop = i === 0 ? 1 : springAt(moving - POP_AT - (i - 1) * POP_GAP, POP_SPRING);
  const depth = Math.min(rel, 2) * pop;
  const scale = 1 - SHRINK * depth;
  return {
    // Scaling about the centre lifts the top edge; the shift puts it back, PEEK above the card in front.
    shift: -PEEK * depth - ((1 - scale) * CARD_HEIGHT) / 2 + (1 - rise) * RISE,
    scale,
    rotation: LEAN * clamp01(depth - 1),
    opacity: rel > 2.5 ? 0 : reduced ? Math.min(1, deal / 0.2) : 1,
    // The grey that hides a card's face while it waits behind, and how much of the darker grey is over it.
    cover: clamp01(depth * COVER_RATE),
    coverFar: clamp01(depth - 1),
  };
}

type Motion = {
  /** Index of the card at the front; fractional while one is on its way out. */
  front: SharedValue<number>;
  /** Seconds since the deck was dealt. */
  deal: SharedValue<number>;
  reduced: boolean;
};

function Card({ item, index, front, deal, reduced }: { item: RisingTourDestination; index: number } & Motion) {
  const style = useAnimatedStyle(() => {
    const pose = poseOf(index, front.get(), deal.get(), reduced);
    return {
      opacity: pose.opacity,
      transform: [{ translateY: pose.shift }, { rotate: `${pose.rotation}deg` }, { scale: pose.scale }],
    };
  });
  const coverStyle = useAnimatedStyle(() => ({ opacity: poseOf(index, front.get(), deal.get(), reduced).cover }));
  const farStyle = useAnimatedStyle(() => ({ opacity: poseOf(index, front.get(), deal.get(), reduced).coverFar }));

  return (
    <Animated.View style={[styles.card, style]}>
      <View style={[styles.photo, { backgroundColor: item.tint }]}>
        <Image source={{ uri: item.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      </View>
      <View style={styles.chip}>
        <Svg width={16} height={16}>
          <Path d={STAR_ICON} fill={STAR} />
        </Svg>
        <Text style={styles.rating}>{item.rating}</Text>
      </View>
      <Text style={styles.title} numberOfLines={2}>
        {item.title}
      </Text>
      <View style={styles.price}>
        <Text style={styles.priceLabel}>{item.price}</Text>
      </View>
      <Text style={styles.text} numberOfLines={2}>
        {item.text}
      </Text>
      <Animated.View style={[styles.cover, coverStyle]}>
        <Animated.View style={[styles.coverFar, farStyle]} />
      </Animated.View>
    </Animated.View>
  );
}

type Naming = {
  /** The destination whose letters are arriving, the one that fades behind them, and the seconds since they changed. */
  nameIndex: SharedValue<number>;
  prevIndex: SharedValue<number>;
  type: SharedValue<number>;
  reduced: boolean;
};

function Letter({ letter, order, index, nameIndex, prevIndex, type, reduced }: { letter: string; order: number; index: number } & Naming) {
  const style = useAnimatedStyle(() => {
    if (nameIndex.get() === index) {
      const e = letterAt(type.get(), order);
      return {
        opacity: e,
        transform: [{ translateX: reduced ? 0 : (1 - e) * LETTER_FROM.x }, { translateY: reduced ? 0 : (1 - e) * LETTER_FROM.y }],
      };
    }
    return {
      opacity: prevIndex.get() === index ? 1 - Math.min(1, type.get() / NAME_FADE) : 0,
      transform: [{ translateX: 0 }, { translateY: 0 }],
    };
  });
  return <Animated.Text style={[styles.letter, style]}>{letter}</Animated.Text>;
}

/** A tappable part of the panel: scales down a little while pressed. */
function Press({ label, onPress, children }: { label: string; onPress: () => void; children: ReactNode }) {
  return (
    <Pressable onPress={onPress} hitSlop={4} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          style={{
            transform: [{ scale: pressed ? 0.96 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          }}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

function Icon({ path, size, width = 1.6 }: { path: string; size: number; width?: number }) {
  return (
    <Svg width={size} height={size}>
      <Path d={path} stroke="#ffffff" strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/**
 * A dark panel with a destination in huge condensed serif capitals and a stack of three tour cards.
 * The deck is dealt on appear: the letters arrive one after another, the front card rises from the
 * bottom edge, the two cards behind it pop up and the buttons fade in. Swiping the front card up
 * sends it away and brings the next destination forward; after the last one the deck is dealt again.
 */
export function RisingTourStack({
  destinations = DESTINATIONS,
  autoPlay = false,
  heading = 'Your Destination',
  availableLabel = 'Available',
  mapLabel = 'See on Map',
  onChange,
  onLike,
  onMap,
  onBook,
  onBack,
  onSearch,
}: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const count = destinations.length;

  // A self-playing deck starts dealt, so its loop begins and ends on the same frame.
  const front = useSharedValue(0);
  const deal = useSharedValue(auto ? DEAL_SECONDS : 0);
  const type = useSharedValue(auto ? TYPE_SECONDS : 0);
  const nameIndex = useSharedValue(0);
  const prevIndex = useSharedValue(-1);
  // Seconds since the heart was tapped.
  const heart = useSharedValue(1);

  // The card the stack is heading for; `count` once the last one is thrown.
  const state = useRef({ at: 0, pressed: false, dragging: false, startY: 0, lastY: 0, lastTime: 0, velocity: 0 });
  const dealing = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [shown, setShown] = useState(0);
  const [liked, setLiked] = useState<number[]>([]);
  // Counts touches: a change restarts the autoPlay loop after a pause.
  const [touches, setTouches] = useState(0);

  const run = useCallback((clock: SharedValue<number>, seconds: number) => {
    clock.set(0);
    clock.set(withTiming(seconds, { duration: seconds * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  }, []);

  const dealDeck = useCallback(() => {
    state.current.at = 0;
    front.set(0);
    prevIndex.set(-1);
    nameIndex.set(0);
    run(deal, DEAL_SECONDS);
    run(type, TYPE_SECONDS);
  }, [deal, front, nameIndex, prevIndex, run, type]);

  /** Throws the front card. The next destination takes its place, or the deck is dealt again. */
  const advance = useCallback(
    (velocity = 0) => {
      const s = state.current;
      if (s.at >= count) return -1;
      prevIndex.set(s.at);
      s.at += 1;
      nameIndex.set(s.at < count ? s.at : -1);
      run(type, TYPE_SECONDS);
      front.set(reduced ? s.at : withSpring(s.at, { ...FRONT_SPRING, velocity }));
      if (s.at === count) {
        if (dealing.current) clearTimeout(dealing.current);
        dealing.current = setTimeout(() => {
          dealDeck();
          setShown(0);
        }, DEAL_PAUSE * 1000);
      } else {
        setShown(s.at);
      }
      return s.at % count;
    },
    [count, dealDeck, front, nameIndex, prevIndex, reduced, run, type],
  );

  useEffect(() => {
    if (!auto) dealDeck();
    return () => {
      if (dealing.current) clearTimeout(dealing.current);
    };
  }, [auto, dealDeck]);

  useEffect(() => {
    if (!auto || count === 0) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const gap = count > 1 ? (LOOP - TAIL - FIRST) / (count - 1) : 0;
    const cycle = () => {
      timers.length = 0;
      for (let i = 0; i < count; i++) timers.push(setTimeout(() => advance(), (FIRST + gap * i) * 1000));
      timers.push(setTimeout(cycle, (FIRST + gap * (count - 1) + TAIL) * 1000));
    };
    if (touches === 0) {
      cycle();
    } else {
      // After a touch the loop waits, throws what is left of the deck and starts over.
      timers.push(
        setTimeout(() => {
          if (state.current.pressed) return;
          if (state.current.at === 0) {
            cycle();
          } else {
            while (state.current.at < count) advance();
            timers.push(setTimeout(cycle, TAIL * 1000));
          }
        }, IDLE * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [auto, touches, count, advance]);

  // --- Swiping up anywhere on the stack throws the front card. ---
  const onGrant = (e: GestureResponderEvent) => {
    const s = state.current;
    s.pressed = true;
    s.dragging = false;
    s.startY = e.nativeEvent.pageY;
    s.lastY = e.nativeEvent.pageY;
    s.lastTime = e.nativeEvent.timestamp;
    s.velocity = 0;
    setTouches((n) => n + 1);
  };

  const onMove = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.at >= count) return;
    const dy = e.nativeEvent.pageY - s.startY;
    if (!s.dragging) {
      if (Math.abs(dy) < DRAG_SLOP) return;
      s.dragging = true;
    }
    // Points per second, smoothed over the last few events.
    const dt = e.nativeEvent.timestamp - s.lastTime;
    if (dt > 0) s.velocity = 0.6 * (((e.nativeEvent.pageY - s.lastY) / dt) * 1000) + 0.4 * s.velocity;
    s.lastY = e.nativeEvent.pageY;
    s.lastTime = e.nativeEvent.timestamp;
    // Only upwards: the card follows the finger point for point.
    front.set(s.at + clamp01(-dy / AWAY));
  };

  const onRelease = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      const dy = e.nativeEvent.pageY - s.startY;
      const progress = clamp01(-dy / AWAY);
      if (-dy > THROW_DISTANCE || -s.velocity > THROW_SPEED) {
        if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        // Faster than this and even this spring would overshoot.
        const limit = Math.sqrt(FRONT_SPRING.stiffness) * (1 - progress);
        const index = advance(Math.min(limit, -s.velocity / AWAY));
        if (index >= 0) onChange?.(index);
      } else {
        front.set(reduced ? s.at : withSpring(s.at, FRONT_SPRING));
      }
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const onTerminate = () => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      front.set(reduced ? s.at : withSpring(s.at, FRONT_SPRING));
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const buttonsStyle = useAnimatedStyle(() => {
    const visible = buttonsAt(front.get(), deal.get(), count);
    return { opacity: visible, transform: [{ translateY: reduced ? 0 : (1 - visible) * BUTTONS_RISE }] };
  });
  const heartStyle = useAnimatedStyle(() => ({
    transform: [{ scale: reduced ? 1 : 0.7 + 0.3 * springAt(heart.get(), HEART_SPRING) }],
  }));

  if (count === 0) return null;
  const motion: Motion = { front, deal, reduced };
  const naming: Naming = { nameIndex, prevIndex, type, reduced };
  const isLiked = liked.includes(shown);
  const current = destinations[shown];

  const toggleLike = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    setLiked((list) => (isLiked ? list.filter((i) => i !== shown) : [...list, shown]));
    run(heart, 1);
    setTouches((n) => n + 1);
    onLike?.(shown, !isLiked);
  };

  return (
    <View style={styles.shadow}>
      <View style={styles.panel}>
        <LinearGradient colors={['#2b3238', '#1c2126']} style={StyleSheet.absoluteFill} />

        <View style={styles.bar}>
          <Press label="Back" onPress={() => onBack?.()}>
            <View style={styles.barButton}>
              <Icon path={BACK_ICON} size={20} />
            </View>
          </Press>
          <Press label="Search" onPress={() => onSearch?.()}>
            <View style={styles.barButton}>
              <Icon path={SEARCH_ICON} size={20} />
            </View>
          </Press>
        </View>
        <View pointerEvents="none" style={styles.heading}>
          <Text style={styles.headingLabel}>{heading}</Text>
        </View>

        {/* The name: the previous one fades while the letters of the next arrive one by one. */}
        {destinations.map((item, index) => (
          <View key={index} pointerEvents="none" style={styles.name} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            {Array.from(item.city).map((letter, order) => (
              <Letter key={order} letter={letter} order={order} index={index} {...naming} />
            ))}
          </View>
        ))}

        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          {destinations
            .map((item, index) => <Card key={index} item={item} index={index} {...motion} />)
            .reverse()}
        </View>

        <View
          style={styles.stack}
          accessible
          accessibilityLabel={`${current.city}: ${current.title}, ${current.price}`}
          onStartShouldSetResponder={() => true}
          onResponderTerminationRequest={() => false}
          onResponderGrant={onGrant}
          onResponderMove={onMove}
          onResponderRelease={onRelease}
          onResponderTerminate={onTerminate}
        />

        <Animated.View style={[styles.buttons, buttonsStyle]}>
          <Press label={availableLabel} onPress={() => onBook?.(shown)}>
            <View style={styles.available}>
              <Text style={styles.availableLabel}>{availableLabel}</Text>
            </View>
          </Press>
          <Press label={mapLabel} onPress={() => onMap?.(shown)}>
            <View style={styles.map}>
              <View style={styles.mapDisc}>
                <Icon path={MAP_ICON} size={18} width={1.4} />
              </View>
              <Text style={styles.mapLabel}>{mapLabel}</Text>
            </View>
          </Press>
          <Press label={isLiked ? 'Unlike' : 'Like'} onPress={toggleLike}>
            <View style={styles.heart}>
              <Animated.View style={heartStyle}>
                <Svg width={20} height={20}>
                  <Path d={HEART_ICON} fill={isLiked ? HEART : 'none'} stroke={isLiked ? HEART : '#ffffff'} strokeWidth={1.5} strokeLinejoin="round" />
                </Svg>
              </Animated.View>
            </View>
          </Press>
        </Animated.View>
      </View>
    </View>
  );
}

function photoUrl(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=800&q=80`;
}

const DESTINATIONS: RisingTourDestination[] = [
  {
    city: 'PARIS',
    title: 'Eiffel Tower 2nd Floor or Summit Guided Tour',
    text: 'Make the best use of your precious time in Paris with pre-reserved tickets to the Eiffel Tower. Choose from a guided tour of the 2nd floor or the summit.',
    rating: '4.5',
    price: '€ 99',
    photo: photoUrl('1502602898657-3e91760cbb34'),
    tint: '#8a7f9c',
  },
  {
    city: 'ROME',
    title: 'Colosseum Arena Floor and Roman Forum Tour',
    text: "Step onto the arena floor through the gladiators' gate, then follow your guide across the Roman Forum and up the Palatine Hill.",
    rating: '4.8',
    price: '€ 64',
    photo: photoUrl('1552832230-c0197dd311b5'),
    tint: '#8d8478',
  },
  {
    city: 'VENICE',
    title: 'Grand Canal Gondola Ride with Live Commentary',
    text: 'Glide under the Rialto Bridge and along the quiet side canals while your gondolier points out the palaces that line the water.',
    rating: '4.6',
    price: '€ 38',
    photo: photoUrl('1523906834658-6e24ef2386f9'),
    tint: '#7fa3ad',
  },
];

const styles = StyleSheet.create({
  shadow: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    boxShadow: '0px 16px 48px rgba(0,0,0,0.22)',
  },
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  bar: {
    position: 'absolute',
    left: 16,
    top: BAR_Y - 18,
    width: WIDTH - 32,
    height: 36,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  barButton: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heading: {
    position: 'absolute',
    left: 60,
    top: BAR_Y - 18,
    width: WIDTH - 120,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headingLabel: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '400',
  },
  name: {
    position: 'absolute',
    left: 0,
    top: NAME_Y - NAME_LINE / 2,
    width: WIDTH,
    height: NAME_LINE,
    flexDirection: 'row',
    justifyContent: 'center',
    transform: [{ scaleX: NAME_SQUEEZE }],
  },
  letter: {
    color: '#ffffff',
    fontFamily: SERIF,
    fontSize: NAME_SIZE,
    lineHeight: NAME_LINE,
    fontWeight: '400',
  },
  card: {
    position: 'absolute',
    left: (WIDTH - CARD_WIDTH) / 2,
    top: CARD_TOP,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    backgroundColor: '#ffffff',
    boxShadow: '0px 10px 28px rgba(0,0,0,0.22)',
  },
  photo: {
    position: 'absolute',
    left: PHOTO_INSET,
    top: PHOTO_INSET,
    width: CARD_WIDTH - PHOTO_INSET * 2,
    height: PHOTO_HEIGHT,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  chip: {
    position: 'absolute',
    left: 14,
    top: 14,
    width: 60,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(38,42,46,0.6)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  rating: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  title: {
    position: 'absolute',
    left: 20,
    top: 196,
    width: 200,
    color: '#14171a',
    fontSize: 15,
    lineHeight: 18,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  price: {
    position: 'absolute',
    left: CARD_WIDTH - 20 - 62,
    top: 199,
    width: 62,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#eeeff0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  priceLabel: {
    color: '#14171a',
    fontSize: 14,
    lineHeight: 17,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  text: {
    position: 'absolute',
    left: 20,
    top: 243,
    width: CARD_WIDTH - 40,
    color: '#6a7076',
    fontSize: 10,
    lineHeight: 14,
    fontWeight: '400',
  },
  cover: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    backgroundColor: COVER_NEAR,
    overflow: 'hidden',
  },
  coverFar: {
    flex: 1,
    backgroundColor: COVER_FAR,
  },
  stack: {
    position: 'absolute',
    left: 0,
    top: STACK_TOP,
    width: WIDTH,
    height: BUTTONS_TOP - STACK_TOP,
  },
  buttons: {
    position: 'absolute',
    left: 0,
    top: BUTTONS_TOP,
    width: WIDTH,
    height: BUTTON_HEIGHT,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
  },
  available: {
    width: 88,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: '#1d3b2b',
    alignItems: 'center',
    justifyContent: 'center',
  },
  availableLabel: {
    color: '#86e3a6',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
  },
  map: {
    width: 140,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: '#30363c',
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 6,
    gap: 9,
  },
  mapDisc: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#474e55',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mapLabel: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
  },
  heart: {
    width: BUTTON_HEIGHT,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: '#30363c',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
