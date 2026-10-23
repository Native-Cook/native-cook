import { Canvas, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  LayoutAnimationConfig,
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
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BAND_HEIGHT, BAND_INSET, CAPSULE_HEIGHT, CAPSULE_WIDTH, ICON_Y, MARK_INSET, Toolbar, iconX } from './Toolbar';

export type Profile = { name: string; note: string; photo: string };

export type Page = { title: string; rows: { title: string; note: string }[] };

type Props = {
  /** The people of the deck, top card first; they come round again after the last. */
  profiles?: Profile[];
  /** The screens of the first three tabs: profile, recent, likes. The fourth tab is the deck. */
  pages?: Page[];
  accent?: string;
  likeColor?: string;
  rejectColor?: string;
  /** Swipes two cards and goes through the tabs by itself every LOOP seconds. */
  autoPlay?: boolean;
  onSelectTab?: (index: number) => void;
  /** Called when a card has left the deck, with the verdict on it. */
  onVerdict?: (profile: Profile, liked: boolean) => void;
};

const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=626&h=840&q=80`;

const PROFILES: Profile[] = [
  { name: 'Lena, 27', note: '2 km away', photo: photo('photo-1544005313-94ddf0286df2') },
  { name: 'Theo, 31', note: '5 km away', photo: photo('photo-1506794778202-cad84cf45f1d') },
  { name: 'Mira, 29', note: '1 km away', photo: photo('photo-1517841905240-472988babdf9') },
];

const PAGES: Page[] = [
  {
    title: 'Profile',
    rows: [
      { title: 'Edit profile', note: 'Photos, bio and interests' },
      { title: 'Discovery', note: 'Within 10 km, ages 24 to 34' },
      { title: 'Notifications', note: 'Matches and messages' },
      { title: 'Privacy', note: 'Who can see you' },
    ],
  },
  {
    title: 'Recent',
    rows: [
      { title: 'Lena', note: 'Passed 2 minutes ago' },
      { title: 'Theo', note: 'Liked an hour ago' },
      { title: 'Mira', note: 'Liked yesterday' },
      { title: 'Jonas', note: 'Passed on Monday' },
    ],
  },
  {
    title: 'Likes',
    rows: [
      { title: 'Ada likes you', note: '3 km away' },
      { title: 'Noor likes you', note: '6 km away' },
      { title: 'Iris likes you', note: '2 km away' },
      { title: 'Sam likes you', note: '9 km away' },
    ],
  },
];

const PAGE = '#0B0D0E';
const GLOW = '#2C5C82';
const GLOW_FAR = '#172836';
// The glow is centred low on the screen, so that the page is at its bluest behind the band's valley.
const GLOW_AT = { x: 0.55, y: 0.78 };
const GLOW_MID = 260;
const GLOW_END = 600;

// The deck's tab is the last of the four.
const DECK = 3;
const MARGIN = 24;
const TITLE_TOP = 22;
const ROW = 72;
const ROW_GAP = 12;
const CARD_WIDTH = 313;
const CARD_HEIGHT = 420;
const CARD_RADIUS = 28;
// Each card behind the top one is this much lower, smaller and darker.
const BEHIND = 2;
const STACK_STEP = 12;
const STACK_SHRINK = 0.05;
const STACK_DIM = 0.3;
// Degrees of tilt for each point the top card is pulled.
const TILT = 1 / 18;
// A card let go this far from its place leaves; the verdict is lit fully a little further.
const THRESHOLD = 90;
const FULL = 120;
const TARGET = 64;

export const LOOP = 10;
const VALLEY = { mass: 1, stiffness: 180, damping: 20 };
const RING = { mass: 1, stiffness: 260, damping: 26 };
const BACK = { mass: 1, stiffness: 300, damping: 26 };
// A card leaves in FLY seconds, gathering speed; the cards behind move up meanwhile.
const FLY = 0.4;
const PAGE_FADE = 0.2;
// The fades are part of how the screen works, so they play under reduced motion too.
const NEVER = { reduceMotion: ReduceMotion.Never };
const FADE_IN = FadeIn.duration(PAGE_FADE * 1000).reduceMotion(ReduceMotion.Never);
const FADE_OUT = FadeOut.duration(PAGE_FADE * 1000).reduceMotion(ReduceMotion.Never);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
// The loop starts again this long after the last touch.
const USER_HOLD = 3;
// The autoPlay loop pulls a card this far over PULL seconds and lets go, presses tabs, and puts
// the first card back on top while the deck is out of sight.
const PULL_TO = 130;
const PULL = 0.8;
const SCRIPT: { at: number; pull?: number; tab?: number; restack?: true }[] = [
  { at: 1.0, pull: -1 },
  { at: 3.0, pull: 1 },
  { at: 5.2, tab: 0 },
  { at: 5.8, restack: true },
  { at: 6.2, tab: 1 },
  { at: 7.0, tab: 2 },
  { at: 8.0, tab: DECK },
];

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

type CardProps = {
  profile: Profile;
  /** The card's place in the endless deck; `first` is the one on top now. */
  place: number;
  first: SharedValue<number>;
  cardX: SharedValue<number>;
  fly: SharedValue<number>;
  still: boolean;
};

function Card({ profile, place, first, cardX, fly, still }: CardProps) {
  const style = useAnimatedStyle(() => {
    const behind = place - first.get();
    const q = fly.get();
    if (behind === 0) {
      return { opacity: still ? 1 - q : 1, transform: [{ translateX: cardX.get() }, { translateY: 0 }, { rotate: `${cardX.get() * TILT}deg` }, { scale: 1 }] };
    }
    // The cards behind move up one place while the top one leaves; a new one fades in at the back.
    const depth = behind - (still ? 0 : easeOut(q));
    const opacity = behind < 0 ? 0 : behind > BEHIND ? q : 1;
    return { opacity, transform: [{ translateX: 0 }, { translateY: depth * STACK_STEP }, { rotate: '0deg' }, { scale: 1 - depth * STACK_SHRINK }] };
  });
  const dim = useAnimatedStyle(() => {
    const behind = place - first.get();
    return { opacity: behind <= 0 ? 0 : Math.max(0, behind - (still ? 0 : easeOut(fly.get()))) * STACK_DIM };
  });
  return (
    <Animated.View style={[styles.card, style]}>
      <Image source={{ uri: profile.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      <LinearGradient colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.72)']} style={styles.shade} />
      <Text style={styles.name} numberOfLines={1}>
        {profile.name}
      </Text>
      <Text style={styles.note} numberOfLines={1}>
        {profile.note}
      </Text>
      <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dim]} />
    </Animated.View>
  );
}

/**
 * A swipe deck with a toolbar that changes shape for it. On the deck's tab the band's top edge
 * sinks into a valley and the two middle icons meet there as a cross and a heart in a glass
 * capsule; dragging the top card lights the side it leans to. On the other tabs the band is flat
 * and a ring slides to the tab that was pressed.
 */
export function SwipeDeckToolbar({
  profiles = PROFILES,
  pages = PAGES,
  accent = '#4FA3E0',
  likeColor = '#2FA56C',
  rejectColor = '#B5304A',
  autoPlay = false,
  onSelectTab,
  onVerdict,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [tab, setTab] = useState(DECK);
  // The place in the endless deck of the first card that is drawn.
  const [drawn, setDrawn] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // 0 flat, 1 with the valley open.
  const valley = useSharedValue(1);
  const ring = useSharedValue(DECK);
  const ringFrom = useSharedValue(DECK);
  const ringTo = useSharedValue(DECK);
  const deck = useSharedValue(1);
  // The card on top: its place in the endless deck, and how far a finger has pulled it.
  const first = useSharedValue(0);
  const pull = useSharedValue(0);
  // A card that is leaving: 0 to 1, from where it was let go, toward a side (-1 or 1).
  const fly = useSharedValue(0);
  const flyFrom = useSharedValue(0);
  const side = useSharedValue(0);
  // The autoPlay loop is pulling the card, and makes no haptics.
  const pulling = useSharedValue(false);
  const silent = useSharedValue(autoPlay);

  const away = size.width + 80;
  const cardX = useDerivedValue(() => {
    const q = fly.get();
    if (q <= 0) return pull.get();
    // Without movement the card stays where it was let go and fades.
    return reducedMotion ? flyFrom.get() : flyFrom.get() + (side.get() * away - flyFrom.get()) * q * q;
  });

  const tick = () => {
    if (!silent.get() && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const decided = (place: number, liked: boolean) => {
    setDrawn(place + 1);
    onVerdict?.(profiles[place % profiles.length], liked);
  };

  /** The top card leaves to a side, and the next one is on top when it has gone. */
  const decide = (to: number) => {
    'worklet';
    if (fly.get() > 0) return;
    cancelAnimation(pull);
    flyFrom.set(pull.get());
    side.set(to);
    fly.set(
      withTiming(1, { duration: FLY * 1000, easing: Easing.linear, ...NEVER }, (finished) => {
        if (!finished) return;
        const place = first.get();
        first.set(place + 1);
        pull.set(0);
        fly.set(0);
        scheduleOnRN(decided, place, to > 0);
      }),
    );
    scheduleOnRN(tick);
  };

  /** A card let go: off to the side it was pulled to, or back to its place. */
  const release = () => {
    'worklet';
    const x = pull.get();
    if (Math.abs(x) > THRESHOLD) decide(Math.sign(x));
    else pull.set(withSpring(0, { ...BACK, ...NEVER }));
  };

  const select = (index: number) => {
    if (index === tab) return;
    const open = index === DECK ? 1 : 0;
    ringFrom.set(ring.get());
    ringTo.set(index);
    ring.set(reducedMotion ? index : withSpring(index, { ...RING, ...NEVER }));
    valley.set(reducedMotion ? withTiming(open, { duration: PAGE_FADE * 1000, ...NEVER }) : withSpring(open, { ...VALLEY, ...NEVER }));
    deck.set(withTiming(open, { duration: PAGE_FADE * 1000, ...NEVER }));
    setTab(index);
    onSelectTab?.(index);
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    silent.set(false);
    if (pulling.get()) {
      pulling.set(false);
      pull.set(withSpring(0, { ...BACK, ...NEVER }));
    }
    setTouches((n) => n + 1);
  };

  const press = (index: number) => {
    touched();
    if (index === tab) return;
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    select(index);
  };

  // The loop does what a finger does, through the same functions.
  const script = {
    pull: (to: number) => {
      pulling.set(true);
      pull.set(
        withTiming(to * PULL_TO, { duration: PULL * 1000, easing: EASE_IN_OUT, ...NEVER }, (finished) => {
          if (!finished) return;
          pulling.set(false);
          release();
        }),
      );
    },
    select,
    /** The first card is on top again. */
    restack: () => {
      first.set(0);
      setDrawn(0);
    },
    /** Back to the deck with the first card on top, where the loop starts. */
    reset: () => {
      silent.set(true);
      first.set(0);
      setDrawn(0);
      select(DECK);
    },
  };
  // The loop reads the latest state when its timers fire.
  const latest = useRef(script);
  useEffect(() => {
    latest.current = script;
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const step of SCRIPT) {
        after(origin + step.at * 1000 - Date.now(), () => {
          if (step.pull !== undefined) latest.current.pull(step.pull);
          else if (step.tab !== undefined) latest.current.select(step.tab);
          else latest.current.restack();
        });
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      latest.current.reset();
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const drag = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onBegin(() => {
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      if (fly.get() === 0) pull.set(event.translationX);
    })
    .onEnd(() => {
      release();
    })
    .onFinalize(() => {
      scheduleOnRN(touched);
    });

  const judge = (to: number) => {
    touched();
    decide(to);
  };

  const bandHeight = BAND_HEIGHT + Math.max(BAND_INSET, insets.bottom);
  // The deck stands a little above the middle of the room over the band.
  const room = size.height - insets.top - bandHeight;
  const deckHeight = CARD_HEIGHT + BEHIND * STACK_STEP;
  const deckTop = insets.top + Math.max(12, (room - deckHeight) * 0.4);
  const deckStyle = useAnimatedStyle(() => ({ opacity: deck.get() }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const onDeck = tab === DECK;
  const page = pages[Math.min(tab, pages.length - 1)];
  const capsuleLeft = size.width / 2 - CAPSULE_WIDTH / 2;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill}>
        <Rect x={0} y={0} width={size.width} height={size.height}>
          <RadialGradient
            c={vec(size.width * GLOW_AT.x, size.height * GLOW_AT.y)}
            r={GLOW_END}
            colors={[GLOW, GLOW_FAR, PAGE]}
            positions={[0, GLOW_MID / GLOW_END, 1]}
          />
        </Rect>
      </Canvas>

      {/* A new tab's screen fades in over the old one; the deck stays, so its photos do too. */}
      <LayoutAnimationConfig skipEntering>
        {!onDeck && (
          <Animated.View key={tab} style={[styles.page, { top: insets.top + TITLE_TOP }]} entering={FADE_IN} exiting={FADE_OUT}>
            <Text style={styles.title} numberOfLines={1}>
              {page.title}
            </Text>
            {page.rows.map((row, i) => (
              <View key={i} style={styles.row}>
                <Text style={styles.rowTitle} numberOfLines={1}>
                  {row.title}
                </Text>
                <Text style={styles.rowNote} numberOfLines={1}>
                  {row.note}
                </Text>
              </View>
            ))}
          </Animated.View>
        )}
      </LayoutAnimationConfig>

      <Animated.View
        pointerEvents={onDeck ? 'auto' : 'none'}
        accessibilityElementsHidden={!onDeck}
        importantForAccessibility={onDeck ? 'auto' : 'no-hide-descendants'}
        style={[styles.deck, { top: deckTop, left: (size.width - CARD_WIDTH) / 2, height: deckHeight }, deckStyle]}
      >
        <GestureDetector gesture={drag}>
          <View style={StyleSheet.absoluteFill}>
            {/* The last card is drawn first, so that the top one lies over the others. */}
            {[3, 2, 1, 0].map((n) => (
              <Card
                key={drawn + n}
                profile={profiles[(drawn + n) % profiles.length]}
                place={drawn + n}
                first={first}
                cardX={cardX}
                fly={fly}
                still={reducedMotion}
              />
            ))}
          </View>
        </GestureDetector>
      </Animated.View>

      <View style={[styles.band, { height: bandHeight }]}>
        <Toolbar
          width={size.width}
          height={bandHeight}
          valley={valley}
          ring={ring}
          ringFrom={ringFrom}
          ringTo={ringTo}
          cardX={cardX}
          fly={fly}
          side={side}
          full={FULL}
          accent={accent}
          likeColor={likeColor}
          rejectColor={rejectColor}
          still={reducedMotion}
        />
        {['Profile', 'Recent', 'Likes', 'Deck'].map((label, i) => {
          // On the deck the two middle tabs have become the cross and the heart.
          const there = !onDeck || i === 0 || i === DECK;
          return (
            <Pressable
              key={label}
              disabled={!there}
              style={[styles.target, { left: iconX(i, size.width) - TARGET / 2, top: ICON_Y - TARGET / 2 }]}
              accessibilityRole="tab"
              accessibilityLabel={i < pages.length ? pages[i].title : label}
              accessibilityState={{ selected: i === tab }}
              accessibilityElementsHidden={!there}
              onPress={() => press(i)}
            />
          );
        })}
        {onDeck &&
          [-1, 1].map((to) => (
            <Pressable
              key={to}
              style={[
                styles.verdict,
                { left: (to < 0 ? capsuleLeft + MARK_INSET : capsuleLeft + CAPSULE_WIDTH - MARK_INSET) - TARGET / 2, top: ICON_Y - CAPSULE_HEIGHT / 2 },
              ]}
              accessibilityRole="button"
              accessibilityLabel={to < 0 ? 'Pass' : 'Like'}
              onPress={() => judge(to)}
            />
          ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  page: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  title: {
    marginBottom: 24,
    color: '#FFFFFF',
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '600',
    includeFontPadding: false,
  },
  row: {
    height: ROW,
    marginBottom: ROW_GAP,
    paddingHorizontal: 20,
    justifyContent: 'center',
    borderRadius: 20,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
    backgroundColor: 'rgba(255,255,255,0.06)',
  },
  rowTitle: {
    color: '#FFFFFF',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
  rowNote: {
    marginTop: 2,
    color: 'rgba(255,255,255,0.55)',
    fontSize: 14,
    lineHeight: 18,
    includeFontPadding: false,
  },
  deck: {
    position: 'absolute',
    width: CARD_WIDTH,
  },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#16222B',
    // A card behind shrinks toward its bottom edge, so that a strip of it shows under the one in front.
    transformOrigin: 'bottom',
  },
  shade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: 170,
  },
  name: {
    position: 'absolute',
    left: 22,
    right: 22,
    bottom: 46,
    color: '#FFFFFF',
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    includeFontPadding: false,
  },
  note: {
    position: 'absolute',
    left: 22,
    right: 22,
    bottom: 22,
    color: 'rgba(255,255,255,0.7)',
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  dim: {
    backgroundColor: '#000000',
  },
  band: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  target: {
    position: 'absolute',
    width: TARGET,
    height: TARGET,
  },
  verdict: {
    position: 'absolute',
    width: TARGET,
    height: CAPSULE_HEIGHT,
  },
});
