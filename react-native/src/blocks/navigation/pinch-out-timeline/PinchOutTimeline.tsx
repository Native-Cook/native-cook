import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import {
  type LayoutChangeEvent,
  Platform,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
  useWindowDimensions,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type Message = {
  /** Written by the person holding the phone: a white bubble at the right. */
  mine?: boolean;
  text?: string;
  /** A square photo sent instead of text. */
  photo?: string;
};

export type Day = {
  /** On the day's card: "Tuesday, Jun 25". */
  label: string;
  /** The two lines shown alone on the page while the day opens. */
  weekday: string;
  date: string;
  /** Above the conversation: "Tue, Jun 25 at 1:03 PM". */
  stamp: string;
  /** The last lines of the message before, out of focus under the top bar. */
  earlier: string[];
  /** The card's title in pieces; a piece with `key` is underlined. */
  title: { text: string; key?: boolean }[];
  /** Up to two photos that peek over the bottom edge of the card. */
  photos?: string[];
  messages: Message[];
};

type Props = {
  days?: Day[];
  /** The day whose conversation is open at the start. */
  initialDay?: number;
  /** Pinches out to the days and taps back into the conversation by itself every LOOP seconds. */
  autoPlay?: boolean;
  placeholder?: string;
  footnote?: string;
  unlockLabel?: string;
  /** The page, top to bottom: the first colour holds to 60% of the height. */
  pageColors?: [string, string, string];
  ink?: string;
  /** Called when a day's conversation opens. */
  onSelect?: (index: number, day: Day) => void;
  onUnlock?: () => void;
  onMenu?: () => void;
};

const BALLOONS = 'https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=360&h=360&q=80';
const LAGOON = 'https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=360&h=360&q=80';

const DAYS: Day[] = [
  {
    label: 'Saturday, Jun 22',
    weekday: 'Saturday',
    date: 'June 22',
    stamp: 'Sat, Jun 22 at 9:12 AM',
    earlier: [],
    title: [{ text: 'Introduction to ' }, { text: 'Mira’s values', key: true }],
    messages: [
      { mine: true, text: 'Hi' },
      { text: 'Hello Mira, I’m Sol. I’d love to know what matters most to you these days.' },
      { mine: true, text: 'Slow mornings' },
      {
        text: 'That sounds lovely. Unhurried time has a way of making room for everything else. What does a slow morning look like when it goes just right?',
      },
      { mine: true, text: 'Tea, a walk, no phone' },
      {
        text: 'A warm cup, some fresh air and nothing asking for your attention. I’ll remember that. Is there anything that tends to get in the way of mornings like these?',
      },
    ],
  },
  {
    label: 'Tuesday, Jun 25',
    weekday: 'Tuesday',
    date: 'June 25',
    stamp: 'Tue, Jun 25 at 1:03 PM',
    earlier: ['and I hope that feeling of ease stays close,', 'a small calm to carry through your week.'],
    title: [{ text: 'Shared a ' }, { text: 'balloon sunrise', key: true }, { text: ', playing ' }, { text: 'Harbor Racers', key: true }],
    photos: [LAGOON, BALLOONS],
    messages: [
      { mine: true, text: 'Yo' },
      { text: 'Hey Mira, good to see you. How is your day going so far?' },
      { mine: true, text: 'Doing well' },
      {
        text: 'I’m so glad to hear it! A day that starts gently often stays that way, and I know how much a calm rhythm matters to you. What has been helping you feel settled today?',
      },
      { mine: true, photo: BALLOONS },
      {
        text: 'Oh, that’s a lovely one! All those balloons drifting over the valley in the first light, in no hurry at all. I can see why you kept it. Are you still up for a round of Harbor Racers tonight, or is today for slower things?',
      },
    ],
  },
  {
    label: 'Wednesday, Jun 26',
    weekday: 'Wednesday',
    date: 'June 26',
    stamp: 'Wed, Jun 26 at 8:40 PM',
    earlier: ['Sleep well, Mira. The balloons will still be', 'there in the morning, and so will I.'],
    title: [{ text: 'Options for discussing ' }, { text: 'Harbor Racers', key: true }],
    messages: [
      { mine: true, text: 'Quick question' },
      { text: 'Of course, Mira. What’s on your mind this evening?' },
      { mine: true, text: 'How do I tell Jo I need a break from Harbor Racers?' },
      {
        text: 'It’s kind of you to think it through first. You could say it plainly and warmly: you love playing together, and you need a few quiet evenings. Or offer a smaller ritual in its place, like one short race on Sundays.',
      },
      { mine: true, text: 'The Sunday idea' },
      { text: 'I like it too. It keeps what you share, at a gentler pace. Shall we try the words out together first?' },
    ],
  },
];

const PAGE: [string, string, string] = ['#E5E5E5', '#EBD2CE', '#D9ADA9'];
const PAGE_STOPS = [0.6, 0.85, 1] as const;
const INK = '#1E1C1F';
const MUTE = '#8E8A8C';
const PLACEHOLDER = '#A39A9B';
const GLASS = 'rgba(255,255,255,0.45)';
const CARD_FILL = 'rgba(255,255,255,0.38)';
const CARD_EDGE = 'rgba(255,255,255,0.5)';
const PHOTO_TINT = '#D8CFC9';
const FINGER = 'rgba(30,28,31,0.16)';
const FINGER_EDGE = 'rgba(255,255,255,0.7)';

const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });

// The conversation.
const SIDE = 22;
// From the safe area's top edge to the time stamp, and between two messages.
const CHAT_TOP = 70;
const MESSAGE_GAP = 22;
const TEXT_TRAIL = 56;
const BUBBLE_RADIUS = 18;
const PHOTO = 90;
const INPUT = 40;
const INPUT_SIDE = 11;
const INPUT_LIFT = 2;
const DOWN = 34;
const DOWN_LIFT = 22;

// The days.
const MARGIN = 16;
const CARDS_TOP = 25;
const GAP = 8;
const RADIUS = 20;
const CARD_MIN = 150;
const CARD_MAX = 240;
const CARD_PAD = 22;
const THUMB = 84;
const THUMBS = [
  { left: 72, turn: -8 },
  { left: 165, turn: 6 },
];
// The room the two lines under the cards take, above the safe area's bottom edge.
const FOOTER = 52;

// Going out: the conversation shrinks to this scale on its day's card while the fingers close; the
// spring finishes the move when they lift. The other cards arrive from SLIDE points away.
const SHEET_SCALE = 0.44;
const SHEET_BLUR = 14;
const SPRING = { mass: 1, stiffness: 180, damping: 19 };
// How far the fingers must have taken it for the release to go on to the days.
const COMMIT = 0.3;
const SLIDE = 80;
const SLIDE_SCALE = 0.9;
// What is on the cards shows after they have landed, top card first.
const REVEAL_AFTER = 0.32;
const REVEAL_STAGGER = 0.06;
const REVEAL_IN = 0.3;
const REVEAL_BLUR = 6;
const REVEAL_END = 0.75;
const REVEAL_OUT = 0.15;
const THUMB_RISE = 34;

// Going in, in seconds from the tap: the cards blow up and blur away, the date shows alone, the
// conversation arrives message by message.
const BLOW = 0.32;
const GROW = 2.3;
const FLY = 0.55;
const BLOW_BLUR = 16;
const DATE_AT = 0.22;
const DATE_FADE = 0.2;
const DATE_HOLD = 0.35;
const CHAT_AT = 0.85;
const CHAT_STAGGER = 0.03;
const CHAT_ITEM_IN = 0.25;
const CHAT_CLEAR = 0.35;
const CHAT_FROM = 0.97;
const CHAT_BLUR = 10;
// The stagger stops growing after this many messages, so a long day still arrives in time.
const CHAT_ITEMS = 8;
const ENTER_END = 1.4;
// A spread of the fingers takes a card this far into the blow-up, and past half of it the day opens.
const SCRUB = 0.8;
// With reduced motion the two screens cross-fade.
const FADE = 0.25;

// The loop: fingers close on the conversation, lift, and later one taps the day's card.
export const LOOP = 9;
const PINCH_AT = 1.2;
const PINCH_FOR = 0.5;
const PINCH_TO = 0.62;
const PINCH_EASE = [0.455, 0.03, 0.515, 0.955] as const;
const TAP_AT = 5;
const FINGER_IN = 0.25;
const FINGER_OUT = 0.2;
const FINGER_SIZE = 44;
// Half the distance between the two fingers before they close, across and down.
const FINGER_REACH = { x: 58, y: 78 };
const USER_HOLD = 3;
const HOME = 0.7;

// iOS has no blur filter for a view, so a blur view lies over the two screens; Android blurs the
// screens themselves, from Android 12 on, and only fades and scales before that.
const IOS = Platform.OS === 'ios';
const CAN_FILTER = Platform.OS === 'android' && Number(Platform.Version) >= 31;
const BLUR_INTENSITY = 36;

// Icons, drawn in boxes of 16 points.
const COLLAPSE = 'M1 1L6.5 6.5M6.5 2.5V6.5H2.5M15 15L9.5 9.5M9.5 13.5V9.5H13.5';
const ARROW_DOWN = 'M8 2.5V13.5M3 8.5L8 13.5L13 8.5';
const PLUS = 'M8 2V14M2 8H14';
const WAVE = 'M2 6.5V9.5M5 4V12M8 1.5V14.5M11 4.5V11.5M14 6.5V9.5';
// The bubble's tail, in a box that hangs 4 points over its bottom right corner.
const TAIL = 'M4 0C4 6 7 10 14 12C8 13 3 11 0 8Z';

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function smooth(x: number, from: number, to: number) {
  'worklet';
  const t = clamp01((x - from) / (to - from));
  return t * t * (3 - 2 * t);
}

function easeIn(t: number) {
  'worklet';
  const c = clamp01(t);
  return c * c * c;
}

function easeOut(t: number) {
  'worklet';
  const c = 1 - clamp01(t);
  return 1 - c * c * c;
}

/** How visible the conversation's piece number `index` is, `enter` seconds after a card was tapped. */
function arrived(enter: number, index: number) {
  'worklet';
  return smooth(enter - CHAT_AT - Math.min(index, CHAT_ITEMS) * CHAT_STAGGER, 0, CHAT_ITEM_IN);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** The three numbers every moving part reads, and where the cards are. */
type Motion = {
  /** 0 with the conversation open, 1 with the days; follows the fingers in between. */
  out: SharedValue<number>;
  /** Seconds since the cards landed. */
  reveal: SharedValue<number>;
  /** Seconds since a card was tapped; 0 when no day is opening. */
  enter: SharedValue<number>;
  /** The card the conversation shrinks onto and grows out of. */
  picked: SharedValue<number>;
  scrollY: SharedValue<number>;
};

type Frame = { width: number; height: number; cardsTop: number; cardHeight: number };

function Icon({ path, color, stroke = 1.5, size = 16 }: { path: string; color: string; stroke?: number; size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 16 16">
      <Path d={path} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** A piece of the conversation: it arrives in its turn when a day opens. */
function Piece({
  index,
  motion,
  early = false,
  style,
  children,
}: {
  index: number;
  motion: Motion;
  /** The first to go when the fingers close. */
  early?: boolean;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const { out, enter } = motion;
  const arriving = useAnimatedStyle(() => {
    const e = enter.get();
    if (e > 0) return { opacity: arrived(e, index) };
    return { opacity: early ? 1 - smooth(out.get(), 0, 0.3) : 1 };
  });
  return <Animated.View style={[style, arriving]}>{children}</Animated.View>;
}

function Bubble({ message, ink, maxWidth }: { message: Message; ink: string; maxWidth: number }) {
  if (message.photo) {
    return (
      <View style={styles.photo}>
        <Image source={{ uri: message.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      </View>
    );
  }
  if (message.mine) {
    return (
      <View style={[styles.bubble, { maxWidth }]}>
        <Svg width={14} height={13} viewBox="0 0 14 13" style={styles.tail}>
          <Path d={TAIL} fill="#ffffff" />
        </Svg>
        <Text style={[styles.bubbleText, { color: ink }]}>{message.text}</Text>
      </View>
    );
  }
  return (
    <Text style={[styles.reply, { color: ink }]} lineBreakStrategyIOS="standard">
      {message.text}
    </Text>
  );
}

/** One day of the timeline. The picked one is also the sheet the conversation shrinks into. */
function DayCard({
  index,
  day,
  frame,
  motion,
  still,
  ink,
  onPress,
}: {
  index: number;
  day: Day;
  frame: Frame;
  motion: Motion;
  still: boolean;
  ink: string;
  onPress: (index: number) => void;
}) {
  const { out, reveal, enter, picked, scrollY } = motion;
  const { width, height, cardsTop, cardHeight } = frame;
  const cardWidth = width - MARGIN * 2;
  const top = cardsTop + index * (cardHeight + GAP);

  const placed = useAnimatedStyle(() => {
    const p = out.get();
    const e = enter.get();
    if (still) {
      return { left: MARGIN, top, width: cardWidth, height: cardHeight, borderRadius: RADIUS, opacity: clamp01(p), transform: [{ translateY: 0 }, { scale: 1 }] };
    }
    const k = easeIn(e / BLOW);
    const leaving = 1 - smooth(e, BLOW * 0.3, BLOW);
    const at = picked.get();
    if (at === index) {
      // The sheet: the whole screen at 0, this card at 1; opening, it grows over the screen again.
      const middle = top - scrollY.get() + cardHeight / 2;
      return {
        left: MARGIN * p,
        top: scrollY.get() + (top - scrollY.get()) * p,
        width: width + (cardWidth - width) * p,
        height: height + (cardHeight - height) * p,
        borderRadius: RADIUS * smooth(p, 0, 0.5),
        opacity: smooth(p, 0.1, 0.7) * leaving,
        transform: [{ translateY: (height / 2 - middle) * k }, { scale: 1 + (GROW - 1) * k }],
      };
    }
    // The others come in from above and below, and leave the same way.
    const side = index < at ? -1 : 1;
    return {
      left: MARGIN,
      top,
      width: cardWidth,
      height: cardHeight,
      borderRadius: RADIUS,
      opacity: smooth(p, 0.25, 0.85) * leaving,
      transform: [
        { translateY: side * (SLIDE * (1 - p) + height * FLY * k) },
        { scale: (SLIDE_SCALE + (1 - SLIDE_SCALE) * p) * (1 + (GROW - 1) * 0.3 * k) },
      ],
    };
  });

  const written = useAnimatedStyle(() => {
    const shown = still ? 1 : smooth(reveal.get() - index * REVEAL_STAGGER, 0, REVEAL_IN);
    if (!CAN_FILTER) return { opacity: shown };
    return { opacity: shown, filter: [{ blur: REVEAL_BLUR * (1 - shown) }] as unknown as string };
  });

  const risen = useAnimatedStyle(() => {
    const up = still ? 1 : easeOut((reveal.get() - 0.1) / 0.45);
    return { opacity: clamp01(up * 2), transform: [{ translateY: THUMB_RISE * (1 - up) }] };
  });

  return (
    <Animated.View style={[styles.card, placed]}>
      <Pressable
        style={{ width: cardWidth, height: cardHeight }}
        onPress={() => onPress(index)}
        accessibilityRole="button"
        accessibilityLabel={`${day.label}. ${day.title.map((piece) => piece.text).join('')}`}
      >
        <Animated.View style={[styles.cardText, written]}>
          <Text style={styles.cardLabel} numberOfLines={1}>
            {day.label}
          </Text>
          <Text style={[styles.cardTitle, { color: ink }]} numberOfLines={2} lineBreakStrategyIOS="standard">
            {day.title.map((piece) => (
              <Text key={piece.text} style={piece.key ? styles.key : undefined}>
                {piece.text}
              </Text>
            ))}
          </Text>
        </Animated.View>
        {day.photos && (
          <Animated.View style={[StyleSheet.absoluteFill, risen]}>
            {day.photos.slice(0, THUMBS.length).map((photo, at) => (
              <View
                key={photo}
                style={[styles.thumb, { left: THUMBS[at].left, top: cardHeight - THUMB / 2, transform: [{ rotate: `${THUMBS[at].turn}deg` }] }]}
              >
                <Image source={{ uri: photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
              </View>
            ))}
          </Animated.View>
        )}
      </Pressable>
    </Animated.View>
  );
}

/** The fingers of the loop that plays by itself: two that close, and one that taps. */
function Fingers({
  out,
  shown,
  tapShown,
  tapDown,
  center,
  target,
}: {
  out: SharedValue<number>;
  shown: SharedValue<number>;
  tapShown: SharedValue<number>;
  tapDown: SharedValue<number>;
  center: { x: number; y: number };
  target: { x: number; y: number };
}) {
  const first = useAnimatedStyle(() => {
    const reach = 1 - (1 - SHEET_SCALE) * Math.min(out.get(), PINCH_TO);
    return { opacity: shown.get(), transform: [{ translateX: -FINGER_REACH.x * reach }, { translateY: -FINGER_REACH.y * reach }] };
  });
  const second = useAnimatedStyle(() => {
    const reach = 1 - (1 - SHEET_SCALE) * Math.min(out.get(), PINCH_TO);
    return { opacity: shown.get(), transform: [{ translateX: FINGER_REACH.x * reach }, { translateY: FINGER_REACH.y * reach }] };
  });
  const tapping = useAnimatedStyle(() => ({ opacity: tapShown.get(), transform: [{ scale: 1 - 0.18 * tapDown.get() }] }));
  const pinchAt = { left: center.x - FINGER_SIZE / 2, top: center.y - FINGER_SIZE / 2 };
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.finger, pinchAt, first]} />
      <Animated.View style={[styles.finger, pinchAt, second]} />
      <Animated.View style={[styles.finger, { left: target.x - FINGER_SIZE / 2, top: target.y - FINGER_SIZE / 2 }, tapping]} />
    </View>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A journal-like conversation that pinches out into a list of days. Closing two fingers shrinks the
 * conversation onto its day's card while the other days slide in around it; tapping a card (or
 * spreading two fingers on it) blows the cards up, shows the date alone for a moment, and brings
 * that day's conversation in message by message.
 */
export function PinchOutTimeline({
  days = DAYS,
  initialDay = 1,
  autoPlay = false,
  placeholder = 'Share with Sol…',
  footnote = 'Chronicles are only available to subscribers',
  unlockLabel = 'Unlock your chronicles',
  pageColors = PAGE,
  ink = INK,
  onSelect,
  onUnlock,
  onMenu,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const home = Math.min(Math.max(initialDay, 0), days.length - 1);

  // The day whose conversation is drawn, and which of the two screens takes the touches.
  const [day, setDay] = useState(home);
  const [timeline, setTimeline] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const out = useSharedValue(0);
  const reveal = useSharedValue(0);
  const enter = useSharedValue(0);
  const picked = useSharedValue(home);
  const scrollY = useSharedValue(0);
  // The same as `timeline`, for the gesture: 0 the conversation, 1 the days.
  const place = useSharedValue(0);
  // What two fingers on the screen are doing: 0 nothing, 1 closing the conversation, 2 opening a card.
  const grip = useSharedValue(0);
  const fingers = useSharedValue(0);
  const tapShown = useSharedValue(0);
  const tapDown = useSharedValue(0);
  const scroll = useAnimatedRef<Animated.ScrollView>();
  // The loop's fingers are on the screen, half way out.
  const held = useSharedValue(false);

  const top = insets.top;
  const bottom = Math.max(insets.bottom, 12);
  const cardsTop = top + CARDS_TOP;
  const cardHeight = Math.min(CARD_MAX, Math.max(CARD_MIN, (height - cardsTop - bottom - FOOTER - GAP * 2) / 3));
  const frame: Frame = { width, height, cardsTop, cardHeight };
  const motion: Motion = { out, reveal, enter, picked, scrollY };
  const pitch = cardHeight + GAP;
  const count = days.length;
  const shownDay = days[Math.min(day, count - 1)];
  const last = shownDay.messages.length + 1;

  /** The fingers lift: the spring carries the screen on to the days, or back to the conversation. */
  const release = (toDays: boolean) => {
    held.set(false);
    place.set(toDays ? 1 : 0);
    setTimeline(toDays);
    const target = toDays ? 1 : 0;
    out.set(
      reducedMotion
        ? withTiming(target, { duration: FADE * 1000, reduceMotion: ReduceMotion.Never })
        : withSpring(target, { ...SPRING, reduceMotion: ReduceMotion.Never }),
    );
    reveal.set(
      toDays
        ? withDelay(
            REVEAL_AFTER * 1000,
            withTiming(REVEAL_END, { duration: REVEAL_END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
          )
        : withTiming(0, { duration: REVEAL_OUT * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
    );
  };

  /** Plays the way in from `from` seconds, and ends on the conversation. */
  const play = (from: number) => {
    enter.set(
      withTiming(ENTER_END, { duration: (ENTER_END - from) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        // The conversation is complete under the last frame of the way in: swap the two in one frame.
        out.set(0);
        reveal.set(0);
        enter.set(0);
        place.set(0);
        scheduleOnRN(setTimeline, false);
      }),
    );
  };

  /** A day opens, from its card. */
  const open = (index: number, auto: boolean, from = 0) => {
    if (!auto) tick();
    picked.set(index);
    setDay(index);
    onSelect?.(index, days[index]);
    if (reducedMotion) {
      release(false);
      return;
    }
    play(from);
  };

  /** A spread that did not go far enough: the cards come back. */
  const close = (from: number) => {
    enter.set(withTiming(0, { duration: Math.max(from, 0.05) * 1000, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
  };

  /** The state every loop starts from, set at once. */
  const rest = () => {
    cancelAnimation(out);
    cancelAnimation(reveal);
    cancelAnimation(enter);
    held.set(false);
    out.set(0);
    reveal.set(0);
    enter.set(0);
    place.set(0);
    picked.set(home);
    fingers.set(0);
    tapShown.set(0);
    tapDown.set(0);
    setDay(home);
    setTimeline(false);
    scroll.current?.scrollTo({ y: 0, animated: false });
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ release, open, rest, timeline });
  useEffect(() => {
    latest.current = { release, open, rest, timeline };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      latest.current.rest();
      at(PINCH_AT - FINGER_IN, () => fingers.set(withTiming(1, { duration: FINGER_IN * 1000, reduceMotion: ReduceMotion.Never })));
      at(PINCH_AT, () => {
        held.set(true);
        out.set(0);
        out.set(withTiming(PINCH_TO, { duration: PINCH_FOR * 1000, easing: Easing.bezier(...PINCH_EASE), reduceMotion: ReduceMotion.Never }));
      });
      at(PINCH_AT + PINCH_FOR, () => {
        fingers.set(withTiming(0, { duration: FINGER_OUT * 1000, reduceMotion: ReduceMotion.Never }));
        latest.current.release(true);
      });
      at(TAP_AT - FINGER_IN, () => tapShown.set(withTiming(1, { duration: FINGER_IN * 1000, reduceMotion: ReduceMotion.Never })));
      at(TAP_AT, () => {
        tapDown.set(
          withSequence(
            withTiming(1, { duration: 90, reduceMotion: ReduceMotion.Never }),
            withTiming(0, { duration: FINGER_OUT * 1000, reduceMotion: ReduceMotion.Never }),
          ),
        );
        tapShown.set(withDelay(120, withTiming(0, { duration: FINGER_OUT * 1000, reduceMotion: ReduceMotion.Never })));
        // The step sets what it starts from: the days, landed and written.
        out.set(1);
        reveal.set(REVEAL_END);
        latest.current.open(home, true);
      });
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    /** After a touch: back to the first day's conversation, then the loop again. */
    const back = () => {
      if (enter.get() > 0) return after(100, back);
      if (latest.current.timeline) {
        picked.set(home);
        setDay(home);
        latest.current.release(false);
        return after(HOME * 1000, () => cycle(Date.now()));
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, back);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, home, out, reveal, enter, picked, held, fingers, tapShown, tapDown]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    fingers.set(0);
    tapShown.set(0);
    if (held.get()) release(true);
    setTouches((n) => n + 1);
  };

  /** Two fingers opened on a card and lifted `from` seconds into the way in. */
  const spread = (index: number, from: number, wide: boolean) => {
    if (reducedMotion) {
      if (wide) open(index, false);
    } else if (from > BLOW * SCRUB * 0.5) open(index, false, from);
    else close(from);
  };

  const pinch = Gesture.Pinch()
    .onStart((event) => {
      if (enter.get() > 0) return;
      if (place.get() === 0) {
        grip.set(1);
        return;
      }
      // The card under the fingers is the one that opens.
      const index = Math.floor((event.focalY + scrollY.get() - cardsTop) / pitch);
      if (index < 0 || index >= count) return;
      picked.set(index);
      scheduleOnRN(setDay, index);
      grip.set(2);
    })
    .onUpdate((event) => {
      if (grip.get() === 1) out.set(clamp01((1 - event.scale) / (1 - SHEET_SCALE)));
      else if (grip.get() === 2 && !reducedMotion) enter.set(clamp01((event.scale - 1) / (GROW - 1)) * BLOW * SCRUB);
    })
    .onEnd((event) => {
      if (grip.get() === 1) scheduleOnRN(release, out.get() > COMMIT);
      else if (grip.get() === 2) scheduleOnRN(spread, picked.get(), enter.get(), event.scale > 1.2);
    })
    .onFinalize(() => {
      grip.set(0);
    });

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollY.set(event.contentOffset.y);
  });

  const chatStyle = useAnimatedStyle(() => {
    const p = out.get();
    const e = enter.get();
    let opacity = 1;
    let shift = 0;
    let scale = 1;
    let blur = 0;
    if (reducedMotion) {
      opacity = 1 - clamp01(p);
    } else if (e > 0) {
      // Arriving: its pieces fade in by themselves while it comes into focus.
      const clear = smooth(e, CHAT_AT, CHAT_AT + CHAT_CLEAR);
      scale = CHAT_FROM + (1 - CHAT_FROM) * clear;
      blur = CHAT_BLUR * (1 - clear);
    } else {
      // Leaving: it shrinks towards the middle of its day's card.
      const middle = cardsTop + picked.get() * pitch - scrollY.get() + cardHeight / 2;
      opacity = 1 - smooth(p, 0.12, 0.62);
      shift = (middle - height / 2) * p;
      scale = 1 - (1 - SHEET_SCALE) * p;
      blur = SHEET_BLUR * smooth(p, 0, 0.5);
    }
    const style = { opacity, transform: [{ translateY: shift }, { scale }] };
    return CAN_FILTER ? { ...style, filter: [{ blur }] as unknown as string } : style;
  });

  const daysStyle = useAnimatedStyle(() => {
    if (!CAN_FILTER) return {};
    return { filter: [{ blur: reducedMotion ? 0 : BLOW_BLUR * easeIn(enter.get() / BLOW) }] as unknown as string };
  });

  // The blur view's strength: up while a screen goes out of focus, and down again once that screen
  // has faded, so that the other one is sharp under it.
  const blurProps = useAnimatedProps(() => {
    const p = out.get();
    const e = enter.get();
    let amount = 0;
    if (reducedMotion) amount = 0;
    else if (e > 0) {
      amount = Math.max(
        easeIn(e / BLOW) * (1 - smooth(e, BLOW, BLOW + 0.15)),
        smooth(e, CHAT_AT - 0.15, CHAT_AT) * (1 - smooth(e, CHAT_AT, CHAT_AT + CHAT_CLEAR)),
      );
    } else amount = smooth(p, 0, 0.45) * (1 - smooth(p, 0.55, 0.95));
    return { intensity: BLUR_INTENSITY * amount };
  });

  const dateStyle = useAnimatedStyle(() => {
    const e = enter.get();
    return { opacity: smooth(e, DATE_AT, DATE_AT + DATE_FADE) * (1 - smooth(e, DATE_AT + DATE_FADE + DATE_HOLD, DATE_AT + DATE_FADE * 2 + DATE_HOLD)) };
  });

  const footerStyle = useAnimatedStyle(() => ({
    opacity: reducedMotion ? clamp01(out.get()) : smooth(out.get(), 0.7, 1) * (1 - smooth(enter.get(), 0, 0.15)),
  }));

  // The round button stays through the pinch, and leaves and returns with the conversation's last piece.
  const downStyle = useAnimatedStyle(() => {
    const e = enter.get();
    return { opacity: e > 0 ? Math.max(1 - smooth(e, 0, 0.15), arrived(e, last)) : 1 };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const inputTop = height - bottom - INPUT_LIFT - INPUT;
  const bubbleWidth = width * 0.7;

  return (
    <GestureDetector gesture={pinch}>
      <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
        <StatusBar style="dark" />
        <LinearGradient colors={[pageColors[0], pageColors[0], pageColors[1], pageColors[2]]} locations={[0, ...PAGE_STOPS]} style={StyleSheet.absoluteFill} />

        <Animated.View
          style={[StyleSheet.absoluteFill, chatStyle]}
          pointerEvents={timeline ? 'none' : 'auto'}
          accessibilityElementsHidden={timeline}
          importantForAccessibility={timeline ? 'no-hide-descendants' : 'auto'}
        >
          <Piece index={0} motion={motion} style={[styles.earlier, { top: top - 10 }]}>
            {shownDay.earlier.slice(-2).map((line, at, lines) => (
              <Text key={line} style={[styles.reply, styles.soft, { color: ink, opacity: at === lines.length - 1 ? 0.4 : 0.1 }]} numberOfLines={1}>
                {line}
              </Text>
            ))}
          </Piece>

          <View style={[styles.thread, { top: top + CHAT_TOP - MESSAGE_GAP, height: inputTop - 6 - (top + CHAT_TOP - MESSAGE_GAP) }]}>
            <Piece index={0} motion={motion} early style={styles.message}>
              <Text style={styles.stamp} numberOfLines={1}>
                {shownDay.stamp}
              </Text>
            </Piece>
            {shownDay.messages.map((message, at) => (
              <Piece
                key={`${day}-${at}`}
                index={at + 1}
                motion={motion}
                style={[styles.message, message.mine ? styles.mine : { paddingRight: TEXT_TRAIL }]}
              >
                <Bubble message={message} ink={ink} maxWidth={bubbleWidth} />
              </Piece>
            ))}
          </View>

          <Piece index={last} motion={motion} early style={[styles.inputRow, { top: inputTop }]}>
            <View style={styles.plus}>
              <Icon path={PLUS} color={PLACEHOLDER} />
            </View>
            <View style={styles.input}>
              <Text style={styles.placeholder} numberOfLines={1}>
                {placeholder}
              </Text>
              <Icon path={WAVE} color={PLACEHOLDER} />
            </View>
          </Piece>
        </Animated.View>

        <Animated.View
          style={[StyleSheet.absoluteFill, daysStyle]}
          pointerEvents={timeline ? 'auto' : 'none'}
          accessibilityElementsHidden={!timeline}
          importantForAccessibility={timeline ? 'auto' : 'no-hide-descendants'}
        >
          <Animated.ScrollView
            ref={scroll}
            onScroll={onScroll}
            scrollEventThrottle={16}
            scrollEnabled={timeline}
            showsVerticalScrollIndicator={false}
            contentContainerStyle={{ height: cardsTop + count * pitch - GAP + FOOTER + bottom }}
          >
            {days.map((item, index) => (
              <DayCard
                key={item.label}
                index={index}
                day={item}
                frame={frame}
                motion={motion}
                still={reducedMotion}
                ink={ink}
                onPress={(at) => {
                  if (enter.get() === 0) open(at, false);
                }}
              />
            ))}
          </Animated.ScrollView>
        </Animated.View>

        {/* A light material whatever the app's appearance: the default one is dark in a dark app and greys the page. */}
        {IOS && (
          <AnimatedBlurView pointerEvents="none" tint="systemUltraThinMaterialLight" style={StyleSheet.absoluteFill} animatedProps={blurProps} />
        )}

        <Animated.View pointerEvents="none" style={[styles.date, { top: height * 0.453 - 34 }, dateStyle]}>
          <Text style={[styles.dateLine, { color: ink }]}>{shownDay.weekday}</Text>
          <Text style={[styles.dateLine, { color: ink }]}>{shownDay.date}</Text>
        </Animated.View>

        <Animated.View style={[styles.footer, { bottom: bottom + 14 }, footerStyle]} pointerEvents={timeline ? 'auto' : 'none'}>
          <Text style={styles.footnote} numberOfLines={1}>
            {footnote}
          </Text>
          <Pressable onPress={onUnlock} hitSlop={10} accessibilityRole="button">
            <Text style={[styles.unlock, { color: ink }]} numberOfLines={1}>
              {unlockLabel} ›
            </Text>
          </Pressable>
        </Animated.View>

        <Animated.View pointerEvents="none" style={[styles.down, { left: (width - DOWN) / 2, top: inputTop - DOWN_LIFT - DOWN }, downStyle]}>
          <Icon path={ARROW_DOWN} color={ink} size={14} />
        </Animated.View>

        <Pressable
          style={[styles.barButton, { left: SIDE, top: top + 1 }]}
          hitSlop={14}
          accessibilityRole="button"
          accessibilityLabel={timeline ? 'Open the day' : 'Show all days'}
          onPress={() => {
            if (enter.get() > 0) return;
            if (timeline) open(picked.get(), false);
            else {
              tick();
              release(true);
            }
          }}
        >
          <Icon path={COLLAPSE} color={MUTE} stroke={1.4} />
        </Pressable>
        <Pressable style={[styles.barButton, { right: SIDE, top: top + 1 }]} hitSlop={14} accessibilityRole="button" accessibilityLabel="More" onPress={onMenu}>
          <Svg width={16} height={16} viewBox="0 0 16 16">
            <Circle cx={2.5} cy={8} r={1.4} fill={MUTE} />
            <Circle cx={8} cy={8} r={1.4} fill={MUTE} />
            <Circle cx={13.5} cy={8} r={1.4} fill={MUTE} />
          </Svg>
        </Pressable>

        {autoPlay && (
          <Fingers
            out={out}
            shown={fingers}
            tapShown={tapShown}
            tapDown={tapDown}
            center={{ x: width / 2, y: height * 0.52 }}
            target={{ x: width / 2, y: cardsTop + home * pitch + cardHeight / 2 }}
          />
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  earlier: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
  },
  soft: {
    textShadowColor: 'rgba(30,28,31,0.5)',
    textShadowRadius: 4,
    textShadowOffset: { width: 0, height: 0 },
  },
  thread: {
    position: 'absolute',
    left: 0,
    right: 0,
    overflow: 'hidden',
  },
  message: {
    marginTop: MESSAGE_GAP,
    paddingHorizontal: SIDE,
  },
  mine: {
    alignItems: 'flex-end',
  },
  stamp: {
    height: 16,
    lineHeight: 16,
    fontSize: 12,
    color: MUTE,
    textAlign: 'center',
    includeFontPadding: false,
  },
  reply: {
    fontFamily: SERIF,
    fontSize: 16,
    lineHeight: 22,
    includeFontPadding: false,
  },
  bubble: {
    minHeight: 36,
    paddingHorizontal: 15,
    paddingVertical: 8,
    borderRadius: BUBBLE_RADIUS,
    backgroundColor: '#ffffff',
  },
  tail: {
    position: 'absolute',
    right: -4,
    bottom: -1,
  },
  bubbleText: {
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  photo: {
    width: PHOTO,
    height: PHOTO,
    borderRadius: 8,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: PHOTO_TINT,
  },
  inputRow: {
    position: 'absolute',
    left: INPUT_SIDE,
    right: INPUT_SIDE,
    height: INPUT,
    flexDirection: 'row',
  },
  plus: {
    width: INPUT,
    height: INPUT,
    borderRadius: INPUT / 2,
    backgroundColor: GLASS,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: INPUT,
    borderRadius: INPUT / 2,
    backgroundColor: GLASS,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 16,
    paddingRight: 14,
  },
  placeholder: {
    flex: 1,
    fontSize: 15,
    lineHeight: 20,
    color: PLACEHOLDER,
    includeFontPadding: false,
  },
  card: {
    position: 'absolute',
    overflow: 'hidden',
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: CARD_EDGE,
    backgroundColor: CARD_FILL,
  },
  cardText: {
    paddingTop: CARD_PAD - 1,
    paddingHorizontal: CARD_PAD - 1,
  },
  cardLabel: {
    fontSize: 11,
    lineHeight: 14,
    color: MUTE,
    includeFontPadding: false,
  },
  cardTitle: {
    marginTop: 8,
    fontFamily: SERIF,
    fontSize: 19,
    lineHeight: 26,
    includeFontPadding: false,
  },
  key: {
    textDecorationLine: 'underline',
    textDecorationColor: 'rgba(30,28,31,0.45)',
  },
  thumb: {
    position: 'absolute',
    width: THUMB,
    height: THUMB,
    borderRadius: 10,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: PHOTO_TINT,
  },
  date: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  dateLine: {
    fontFamily: SERIF,
    fontSize: 26,
    lineHeight: 34,
    includeFontPadding: false,
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  footnote: {
    fontSize: 10,
    lineHeight: 14,
    color: MUTE,
    includeFontPadding: false,
  },
  unlock: {
    marginTop: 2,
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '600',
    includeFontPadding: false,
  },
  down: {
    position: 'absolute',
    width: DOWN,
    height: DOWN,
    borderRadius: DOWN / 2,
    backgroundColor: 'rgba(255,255,255,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  barButton: {
    position: 'absolute',
    width: 16,
    height: 16,
  },
  finger: {
    position: 'absolute',
    width: FINGER_SIZE,
    height: FINGER_SIZE,
    borderRadius: FINGER_SIZE / 2,
    backgroundColor: FINGER,
    borderWidth: 1,
    borderColor: FINGER_EDGE,
  },
});
