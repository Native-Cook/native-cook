import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  Easing,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, Ellipse, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DeckCardFace, type DeckCard } from './DeckCardFace';
import { ICON_MENU, ICON_SEARCH, ICON_TRAY, ICON_UP, StrokeIcon } from './DeckArt';

export type { DeckCard } from './DeckCardFace';

type Props = {
  cards?: DeckCard[];
  title?: string;
  /** The filter chips; the first one shows every card, the others match a card's `filters`. */
  chips?: string[];
  hint?: string;
  deckLabel?: string;
  /** Stashes and browses cards by itself every LOOP seconds, then empties the deck again. */
  autoPlay?: boolean;
  /** Called when a card is put in the deck. */
  onAdd?: (card: DeckCard) => void;
  /** Called when another card comes to the front. */
  onSelect?: (card: DeckCard) => void;
  onMenu?: () => void;
};

const CARDS: DeckCard[] = [
  {
    title: 'Ice Bath',
    color: '#2f5be6',
    art: 'barrel',
    level: 1,
    badge: 'Updated',
    tags: ['Protocols', 'Recovery'],
    filters: ['Protocols'],
    description: 'Ice baths involve immersion in cold water to reduce inflammation, enhance recovery, and build mental resilience.',
  },
  {
    title: 'Magnesium',
    color: '#f07a25',
    art: 'pills',
    level: 2,
    badge: 'Updated',
    tags: ['Supplements', 'Sleep'],
    filters: ['Supplements'],
    description: 'Magnesium is a mineral used in over 300 processes in the body, important for sleep, muscles and calm nerves.',
  },
  {
    title: 'Zinc',
    color: '#2f5be6',
    art: 'capsules',
    level: 1,
    badge: 'New',
    tags: ['Supplements', 'Immunity'],
    filters: ['New', 'Supplements'],
    description: 'Zinc is an essential trace mineral that supports immune function, wound healing and protein production.',
  },
  {
    title: 'Vitamin D',
    color: '#f0b21e',
    art: 'tablet',
    level: 3,
    badge: 'Updated',
    tags: ['Supplements', 'Immunity'],
    filters: ['Supplements'],
    description: 'Vitamin D, often called the sunshine vitamin, is crucial for bone health, immune function and mood.',
  },
  {
    title: 'Carnivore Diet',
    color: '#c4451c',
    art: 'plate',
    level: 2,
    badge: 'Updated',
    tags: ['Diets', 'Performance'],
    filters: ['Diets'],
    description: 'The carnivore diet focuses on animal-based foods, said to reduce inflammation and improve digestion for some.',
  },
  {
    title: 'Vegan Diet',
    color: '#7fd43a',
    art: 'bowl',
    level: 1,
    badge: 'Updated',
    tags: ['Diets', 'Longevity'],
    filters: ['Diets'],
    description: 'A vegan diet excludes all animal products, relying on plant foods rich in fibre, vitamins and antioxidants.',
  },
  {
    title: 'Water Fast',
    color: '#2ea8e8',
    art: 'cup',
    level: 3,
    badge: 'New',
    tags: ['Protocols', 'Longevity'],
    filters: ['New', 'Protocols'],
    description: 'Water fasting means consuming only water for a set period, to promote autophagy and cellular repair.',
  },
];
const CHIPS = ['All', 'New', 'Protocols', 'Supplements', 'Diets'];

const BACKGROUND = '#121212';
const INK = '#ffffff';
const CHIP = '#222222';
const CHIP_INK = '#8a8a8a';
const CHIP_ON = '#ededed';
const CHIP_ON_INK = '#111111';
const COUNT = '#2a2a2a';
const TRAY = '#1b1b1b';
const POCKET = '#232323';
const HINT = '#e5e5e5';

// Layout, in points.
const MARGIN = 24;
// How much of the cards on either side shows at the screen's edges.
const PEEK = 6;
const HEADER = 44;
const CHIPS_GAP = 4;
const CHIP_H = 32;
const CARD_GAP = 16;
const CARD_RATIO = 1.4;
const TRAY_H = 92;
// The least room between the card and the tray, for the drag down.
const TRAY_GAP = 28;
// Where the deck's front card shows its top edge, below the tray's top, and how the cards behind it
// step up and shrink; three of them show.
const DECK_TOP = 18;
const DECK_STEP = 7;
const DECK_SCALE = 0.9;
const DECK_SHRINK = 0.04;
const DECK_SHOWN = 3;
const PILL_Y = 52;
// The tray rises this much and glows while a card comes down to it.
const LIFT = 8;
const GLOW_H = 160;

// The drag.
const PAN_SLOP = 8;
const STASH_AT = 130;
const STASH_MIN = 50;
const FLING = 900;
const BROWSE_AT = 70;
const RUBBER = 0.3;
// A card dragged down leans after the finger by this share of the sideways movement, shrinks and tilts.
const LEAN = 0.35;
const DRAG_SHRINK = 0.1;
const TILT = 3;

// Motion, in seconds.
const SPRING = { mass: 1, stiffness: 195, damping: 23.74 };
const FLY = 0.42;
const FLY_EASE = Easing.bezier(0.4, 0, 0.2, 1);
const PULSE_OUT = 0.7;
const PRESENCE = 0.22;
const TINT_FADE = 0.4;
const CLEAR_FOR = 0.5;
const CLEAR_STAGGER = 0.06;
const CLEAR_TOTAL = CLEAR_FOR + CLEAR_STAGGER * 3;
const VEIL_OUT = 0.3;
const VEIL_IN = 0.45;
const VEIL_DROP = 14;
const VEIL_SHRINK = 0.05;

// The autoPlay loop: two cards stashed, a browse past the third, two more stashed, then the deck
// empties and the first card comes back. Each drag takes DRAG_FOR, each browse BROWSE_FOR.
const LOOP = 10;
const SCRIPT: [number, 'stash' | 'next'][] = [
  [0.9, 'stash'],
  [2.5, 'stash'],
  [4.1, 'next'],
  [5.3, 'stash'],
  [6.9, 'stash'],
];
const CLEAR_AT = 8.55;
const DRAG_FOR = 0.55;
const DRAG_DX = 14;
const DRAG_DY = 170;
const BROWSE_FOR = 0.34;
const BROWSE_DX = 120;
const DRAG_EASE = Easing.bezier(0.45, 0, 0.25, 1);
const USER_HOLD = 3;

type Layout = { width: number; cardW: number; cardH: number; pitch: number; cardTop: number; trayTop: number };

type Motion = {
  scroll: SharedValue<number>;
  bx: SharedValue<number>;
  dx: SharedValue<number>;
  dy: SharedValue<number>;
  focusId: SharedValue<number>;
  flyId: SharedValue<number>;
  fly: SharedValue<number>;
  flyX: SharedValue<number>;
  flyY: SharedValue<number>;
  flyP: SharedValue<number>;
  flyR: SharedValue<number>;
  clear: SharedValue<number>;
  veil: SharedValue<number>;
  glow: SharedValue<number>;
};

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function dragProgress(dy: number) {
  'worklet';
  return clamp01(dy / STASH_AT);
}

function dragTilt(dx: number, progress: number) {
  'worklet';
  return TILT * progress + Math.min(1, Math.max(-1, dx / 120)) * TILT;
}

function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function withAlpha(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

function haptic(kind: 'select' | 'light' | 'medium') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'light' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

/**
 * A card browser with a deck at the bottom: swipe sideways to browse, swipe a card down to put it in
 * the deck. The card shrinks and tilts as it is pulled, the tray lifts and glows in its colour, and on
 * release the card drops into the tray as the front of a stack while the next one springs in.
 */
export function SwipeDownDeck({
  cards = CARDS,
  title = 'Available Cards',
  chips = CHIPS,
  hint = 'Swipe the card down to add to your deck',
  deckLabel = 'My Deck',
  autoPlay = false,
  onAdd,
  onSelect,
  onMenu,
}: Props) {
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [chip, setChip] = useState(0);
  // Cards in the deck, in the order they went in.
  const [stashed, setStashed] = useState<number[]>([]);
  const [focus, setFocus] = useState(0);
  // Cards that have landed in the deck, for its count.
  const [landed, setLanded] = useState(0);
  // Changes when the deck is emptied, so every card snaps back to its place unseen.
  const [epoch, setEpoch] = useState(0);
  const [touches, setTouches] = useState(0);
  // The card whose colour the tray glows in.
  const [glowId, setGlowId] = useState(0);

  const visible = cards
    .map((_, i) => i)
    .filter((i) => !stashed.includes(i) && (chip === 0 || cards[i].filters.includes(chips[chip])));

  const width = size.width;
  const cardW = width - MARGIN * 2;
  const cardTop = insets.top + HEADER + CHIPS_GAP + CHIP_H + CARD_GAP;
  const trayTop = size.height - Math.max(insets.bottom, 16) - TRAY_H;
  const cardH = Math.min(cardW * CARD_RATIO, trayTop - TRAY_GAP - cardTop);
  const layout: Layout = { width, cardW, cardH, pitch: cardW + MARGIN - PEEK, cardTop, trayTop };

  // The carousel's position, in cards; the drags; the card in flight to the deck and where it left from.
  const scroll = useSharedValue(0);
  const scrollTarget = useSharedValue(0);
  const bx = useSharedValue(0);
  const dx = useSharedValue(0);
  const dy = useSharedValue(0);
  // 0 no drag, 1 browsing sideways, 2 pulling a card down.
  const mode = useSharedValue(0);
  const focusId = useSharedValue(0);
  const flyId = useSharedValue(-1);
  const fly = useSharedValue(1);
  const flyX = useSharedValue(0);
  const flyY = useSharedValue(0);
  const flyP = useSharedValue(0);
  const flyR = useSharedValue(0);
  const pulse = useSharedValue(0);
  // Seconds into the deck's slide out, and the carousel's fade while the loop starts over.
  const clear = useSharedValue(0);
  const veil = useSharedValue(0);
  const visibleIds = useSharedValue<number[]>(visible);
  const glow = useDerivedValue(() => Math.max(dragProgress(dy.get()), pulse.get()));
  const motion: Motion = { scroll, bx, dx, dy, focusId, flyId, fly, flyX, flyY, flyP, flyR, clear, veil, glow };

  // Timers and flags of the autoPlay loop.
  const scripted = useRef(false);
  const clearing = useRef(false);
  const restoreTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    visibleIds.set(visible);
  }, [visible, visibleIds]);

  useEffect(() => {
    const target = Math.max(0, visible.indexOf(focus));
    if (scrollTarget.get() !== target) {
      scrollTarget.set(target);
      scroll.set(withSpring(target, SPRING));
    }
  }, [visible, focus, scroll, scrollTarget]);

  useEffect(() => {
    if (epoch === 0) return;
    clear.set(0);
    veil.set(withTiming(0, { duration: VEIL_IN * 1000, easing: Easing.out(Easing.cubic) }));
  }, [epoch, clear, veil]);

  const didStash = (id: number, next: number, auto: boolean) => {
    setStashed((list) => [...list, id]);
    setFocus(next);
    if (!auto) haptic('medium');
    onAdd?.(cards[id]);
    if (next >= 0) onSelect?.(cards[next]);
  };

  const didLand = (auto: boolean) => {
    setLanded((n) => n + 1);
    if (!auto) haptic('light');
  };

  const didBrowse = (id: number, auto: boolean) => {
    setFocus(id);
    if (!auto) haptic('select');
    onSelect?.(cards[id]);
  };

  /**
   * Ends a drag: a card pulled far enough drops into the deck, a sideways drag pages. `force` makes
   * the outcome certain whatever the distance: the add button, and the loop, whose timers can run late.
   */
  const release = (vx: number, vy: number, auto: boolean, force: boolean) => {
    'worklet';
    const kind = mode.get();
    mode.set(0);
    const ids = visibleIds.get();
    if (kind === 2 || (force && kind === 0)) {
      const id = focusId.get();
      const y = dy.get();
      const k = ids.indexOf(id);
      if (k >= 0 && fly.get() >= 1 && (force || y > STASH_AT || (vy > FLING && y > STASH_MIN))) {
        const x = dx.get();
        const p = dragProgress(y);
        flyX.set(x * LEAN);
        flyY.set(y);
        flyP.set(p);
        flyR.set(dragTilt(x, p));
        dx.set(0);
        dy.set(0);
        const next = k + 1 < ids.length ? ids[k + 1] : k > 0 ? ids[k - 1] : -1;
        focusId.set(next);
        flyId.set(id);
        fly.set(0);
        fly.set(
          withTiming(1, { duration: FLY * 1000, easing: FLY_EASE }, (done) => {
            if (done) scheduleOnRN(didLand, auto);
          }),
        );
        pulse.set(
          withSequence(
            withTiming(1, { duration: FLY * 1000, easing: Easing.in(Easing.quad) }),
            withTiming(0, { duration: PULSE_OUT * 1000, easing: Easing.out(Easing.quad) }),
          ),
        );
        scheduleOnRN(didStash, id, next, auto);
        return;
      }
      dx.set(withSpring(0, SPRING));
      dy.set(withSpring(0, SPRING));
      return;
    }
    if (kind === 1) {
      const k = scrollTarget.get();
      const offset = bx.get();
      let target = k;
      if (force || offset < -BROWSE_AT || vx < -FLING) target = k + 1;
      else if (offset > BROWSE_AT || vx > FLING) target = k - 1;
      target = Math.min(ids.length - 1, Math.max(0, target));
      // The drag becomes part of the position, so the spring starts where the finger let go.
      scroll.set(scroll.get() - offset / layout.pitch);
      bx.set(0);
      scrollTarget.set(target);
      scroll.set(withSpring(target, { ...SPRING, velocity: -vx / layout.pitch }));
      if (target !== k) {
        focusId.set(ids[target]);
        scheduleOnRN(didBrowse, ids[target], auto);
      }
    }
  };

  const restore = () => {
    clearTimeout(restoreTimer.current);
    clearing.current = false;
    cancelAnimation(fly);
    fly.set(1);
    flyId.set(-1);
    pulse.set(0);
    scroll.set(0);
    scrollTarget.set(0);
    bx.set(0);
    dx.set(0);
    dy.set(0);
    mode.set(0);
    const first = cards.length > 0 ? 0 : -1;
    focusId.set(first);
    setStashed([]);
    setFocus(first);
    setLanded(0);
    setChip(0);
    setEpoch((n) => n + 1);
  };

  /** Slides the deck out to the left and fades the carousel, then puts every card back. */
  const clearDeck = () => {
    clearing.current = true;
    clear.set(0);
    clear.set(withTiming(CLEAR_TOTAL, { duration: CLEAR_TOTAL * 1000, easing: Easing.linear }));
    veil.set(withTiming(1, { duration: VEIL_OUT * 1000, easing: Easing.in(Easing.quad) }));
    restoreTimer.current = setTimeout(restore, CLEAR_TOTAL * 1000);
  };

  useEffect(() => () => clearTimeout(restoreTimer.current), []);

  /** The loop's hand: starts the same drags a finger makes. */
  const play = (step: 'stash' | 'next') => {
    if (focusId.get() < 0) return;
    scripted.current = true;
    if (step === 'stash') {
      setGlowId(focusId.get());
      mode.set(2);
      dx.set(withTiming(DRAG_DX, { duration: DRAG_FOR * 1000, easing: DRAG_EASE }));
      dy.set(withTiming(DRAG_DY, { duration: DRAG_FOR * 1000, easing: DRAG_EASE }));
    } else {
      mode.set(1);
      bx.set(withTiming(-BROWSE_DX, { duration: BROWSE_FOR * 1000, easing: DRAG_EASE }));
    }
  };

  const letGo = () => {
    if (!scripted.current) return;
    scripted.current = false;
    scheduleOnUI(release, 0, 0, true, true);
  };

  const atRest = stashed.length === 0 && focus === 0 && chip === 0;
  const latest = useRef({ play, letGo, clearDeck, atRest });
  useEffect(() => {
    latest.current = { play, letGo, clearDeck, atRest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (time: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, time - Date.now())));
    const cycle = (origin: number) => {
      for (const [time, step] of SCRIPT) {
        at(origin + time * 1000, () => latest.current.play(step));
        at(origin + (time + (step === 'stash' ? DRAG_FOR : BROWSE_FOR)) * 1000, () => latest.current.letGo());
      }
      at(origin + CLEAR_AT * 1000, () => latest.current.clearDeck());
      at(origin + LOOP * 1000, () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      if (mode.get() !== 0) return at(Date.now() + 500, rest);
      if (latest.current.atRest) return cycle(Date.now());
      // Back to rest the way the loop ends, then the loop starts over from there.
      latest.current.clearDeck();
      cycle(Date.now() + (LOOP - CLEAR_AT) * 1000);
    };
    if (touches === 0) cycle(Date.now());
    else at(Date.now() + USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, mode]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setTouches((n) => n + 1);
    if (scripted.current) {
      scripted.current = false;
      mode.set(0);
      dx.set(withSpring(0, SPRING));
      dy.set(withSpring(0, SPRING));
      bx.set(withSpring(0, SPRING));
    }
    if (clearing.current) restore();
  };

  const pan = Gesture.Pan()
    .minDistance(PAN_SLOP)
    .onStart((e) => {
      const vertical = Math.abs(e.translationY) > Math.abs(e.translationX);
      if (focusId.get() < 0) return;
      mode.set(vertical ? 2 : 1);
      if (vertical) scheduleOnRN(setGlowId, focusId.get());
    })
    .onUpdate((e) => {
      const kind = mode.get();
      if (kind === 2) {
        dx.set(e.translationX);
        dy.set(e.translationY > 0 ? e.translationY : e.translationY * RUBBER);
      } else if (kind === 1) {
        const k = scrollTarget.get();
        const last = visibleIds.get().length - 1;
        const t = e.translationX;
        bx.set((k <= 0 && t > 0) || (k >= last && t < 0) ? t * RUBBER : t);
      }
    })
    .onFinalize((e) => {
      release(e.velocityX, e.velocityY, false, false);
    });

  const addFocused = (id: number) => {
    touched();
    if (focusId.get() !== id) return;
    setGlowId(id);
    scheduleOnUI(release, 0, 0, false, true);
  };

  const selectChip = (index: number) => {
    touched();
    if (index === chip) return;
    haptic('select');
    const next = cards
      .map((_, i) => i)
      .filter((i) => !stashed.includes(i) && (index === 0 || cards[i].filters.includes(chips[index])));
    const id = next.includes(focus) ? focus : (next[0] ?? -1);
    setChip(index);
    setFocus(id);
    focusId.set(id);
  };

  const trayStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -LIFT * glow.get() }] }));
  const glowStyle = useAnimatedStyle(() => ({ opacity: glow.get() }));
  // The pill gives way to a card falling into the deck and comes back with the new count.
  const pillStyle = useAnimatedStyle(() => ({
    opacity: (1 - clamp01(clear.get() / CLEAR_FOR)) * (1 - pulse.get()),
    transform: [{ translateY: -LIFT * glow.get() }],
  }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const tintColor = cards[glowId]?.color ?? INK;

  return (
    // Any touch, on a card or a chip, takes over from the autoPlay loop.
    <GestureHandlerRootView style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="light" />

      {cards.map((card, i) => (
        <Animated.View
          key={card.title + i}
          pointerEvents="none"
          style={[
            styles.tint,
            { left: width / 2 - cardW * 0.8, top: cardTop + cardH * 0.42 - cardH * 0.62, width: cardW * 1.6, height: cardH * 1.24 },
            {
              opacity: focus === i && !stashed.includes(i) ? 1 : 0,
              transitionProperty: 'opacity',
              transitionDuration: TINT_FADE * 1000,
              transitionTimingFunction: 'ease-in-out',
            },
          ]}
        >
          <Svg width="100%" height="100%">
            <Defs>
              <RadialGradient id={`tint-${i}`} cx="50%" cy="50%" rx="50%" ry="50%">
                <Stop offset="0" stopColor={card.color} stopOpacity={0.42} />
                <Stop offset="1" stopColor={card.color} stopOpacity={0} />
              </RadialGradient>
            </Defs>
            <Ellipse cx="50%" cy="50%" rx="50%" ry="50%" fill={`url(#tint-${i})`} />
          </Svg>
        </Animated.View>
      ))}

      <View style={[styles.header, { marginTop: insets.top }]}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.count}>
          <Text style={styles.countLabel}>{cards.length - stashed.length}</Text>
        </View>
        <View style={styles.spacer} />
        <Pressable
          style={styles.menu}
          accessibilityRole="button"
          accessibilityLabel="Menu"
          onPress={() => {
            touched();
            onMenu?.();
          }}
        >
          <StrokeIcon d={ICON_MENU} color="rgba(255,255,255,0.8)" size={18} />
        </Pressable>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chips}
        contentContainerStyle={styles.chipsContent}
      >
        <View style={styles.search}>
          <StrokeIcon d={ICON_SEARCH} color={CHIP_INK} size={15} />
        </View>
        {chips.map((label, i) => (
          <Pressable key={label} onPress={() => selectChip(i)} accessibilityRole="button" accessibilityState={{ selected: chip === i }}>
            <Animated.View
              style={[
                styles.chip,
                { backgroundColor: chip === i ? CHIP_ON : CHIP, transitionProperty: 'backgroundColor', transitionDuration: 200 },
              ]}
            >
              <Text style={[styles.chipLabel, { color: chip === i ? CHIP_ON_INK : CHIP_INK }]}>{label}</Text>
            </Animated.View>
          </Pressable>
        ))}
      </ScrollView>

      <Animated.View pointerEvents="none" style={[styles.trayGlow, { top: trayTop - GLOW_H, height: GLOW_H }, glowStyle]}>
        <LinearGradient colors={[withAlpha(tintColor, 0), withAlpha(tintColor, 0.5)]} style={StyleSheet.absoluteFill} />
      </Animated.View>

      <Animated.View style={[styles.tray, { top: trayTop }, trayStyle]}>
        <Animated.View style={[styles.trayEdge, { backgroundColor: tintColor }, glowStyle]} />
        <Animated.View
          style={[
            styles.pocket,
            {
              opacity: stashed.length === 0 ? 1 : 0,
              transitionProperty: 'opacity',
              transitionDuration: 250,
            },
          ]}
        >
          <View style={styles.pocketIcon}>
            <StrokeIcon d={ICON_TRAY} color={HINT} size={16} width={1.5} />
          </View>
          <Text style={styles.hint} numberOfLines={1}>
            {hint}
          </Text>
        </Animated.View>
      </Animated.View>

      <GestureDetector gesture={pan}>
        <View style={[styles.layer, { top: cardTop, height: trayTop - cardTop }]}>
          {cards.map((card, i) => {
            const order = stashed.indexOf(i);
            return (
              <DeckSlot
                key={card.title + i}
                index={i}
                card={card}
                layout={layout}
                motion={motion}
                visibleIndex={visible.indexOf(i)}
                inDeck={order >= 0}
                order={order}
                depthTarget={order >= 0 ? stashed.length - 1 - order : 0}
                focused={focus === i}
                epoch={epoch}
                onAdd={() => addFocused(i)}
              />
            );
          })}
        </View>
      </GestureDetector>

      <Animated.View
        pointerEvents="none"
        style={[
          styles.pill,
          { top: trayTop + DECK_TOP + PILL_Y },
          // It fades in with the first card and goes at once when the deck is emptied.
          { opacity: landed > 0 ? 1 : 0, transitionProperty: 'opacity', transitionDuration: landed > 0 ? 200 : 0 },
        ]}
      >
        <Animated.View style={[styles.pillBody, pillStyle]}>
          <StrokeIcon d={ICON_UP} color={INK} size={12} width={1.6} />
          <Text style={styles.pillLabel}>
            {deckLabel} · {landed}
          </Text>
        </Animated.View>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

type SlotProps = {
  index: number;
  card: DeckCard;
  layout: Layout;
  motion: Motion;
  /** Its place in the carousel, or -1 when it is in the deck or filtered out. */
  visibleIndex: number;
  inDeck: boolean;
  order: number;
  /** 0 for the deck's front card, 1 for the one behind it, … */
  depthTarget: number;
  focused: boolean;
  epoch: number;
  onAdd: () => void;
};

/** One card, wherever it is: in the carousel, pulled by a finger, in flight, or in the deck. */
function DeckSlot({ index, card, layout, motion, visibleIndex, inDeck, order, depthTarget, focused, epoch, onAdd }: SlotProps) {
  const shown = visibleIndex >= 0;
  const slot = useSharedValue(Math.max(0, visibleIndex));
  const depth = useSharedValue(0);
  const presence = useSharedValue(shown ? 1 : 0);
  const seen = useRef({ epoch, shown });

  useEffect(() => {
    const before = seen.current;
    // After the loop starts over, or when a filtered-out card comes back, it is put in place unseen.
    const snap = before.epoch !== epoch;
    seen.current = { epoch, shown };
    if (visibleIndex >= 0) slot.set(snap || !before.shown ? visibleIndex : withSpring(visibleIndex, SPRING));
    presence.set(snap ? (shown ? 1 : 0) : withTiming(shown ? 1 : 0, { duration: PRESENCE * 1000 }));
    depth.set(snap || !inDeck ? 0 : withSpring(depthTarget, SPRING));
  }, [visibleIndex, shown, inDeck, depthTarget, epoch, slot, presence, depth]);

  const style = useAnimatedStyle(() => {
    const flying = motion.flyId.get() === index;
    if (inDeck || flying) {
      const d = depth.get();
      const lift = LIFT * motion.glow.get();
      const deckScale = DECK_SCALE - d * DECK_SHRINK;
      const deckTop = layout.trayTop + DECK_TOP - d * DECK_STEP - lift;
      const deckY = deckTop + (layout.cardH * deckScale) / 2 - (layout.cardTop + layout.cardH / 2);
      const out = clamp01((motion.clear.get() - d * CLEAR_STAGGER) / CLEAR_FOR);
      const deckX = -(layout.width + 40) * out * out * out;
      const t = flying ? motion.fly.get() : 1;
      return {
        opacity: clamp01(DECK_SHOWN - d),
        transform: [
          { translateX: mix(motion.flyX.get(), deckX, t) },
          { translateY: mix(motion.flyY.get(), deckY, t) },
          { scale: mix(1 - DRAG_SHRINK * motion.flyP.get(), deckScale, t) },
          { rotate: `${mix(motion.flyR.get(), 0, t)}deg` },
        ],
      };
    }
    const veil = motion.veil.get();
    let x = (slot.get() - motion.scroll.get()) * layout.pitch + motion.bx.get();
    let y = VEIL_DROP * veil;
    let s = 1 - VEIL_SHRINK * veil;
    let r = 0;
    if (motion.focusId.get() === index) {
      const dx = motion.dx.get();
      const dy = motion.dy.get();
      const p = dragProgress(dy);
      x += dx * LEAN;
      y += dy;
      s *= 1 - DRAG_SHRINK * p;
      r = dragTilt(dx, p);
    }
    return {
      opacity: presence.get() * (1 - veil),
      transform: [{ translateX: x }, { translateY: y }, { scale: s }, { rotate: `${r}deg` }],
    };
  });

  return (
    <Animated.View
      pointerEvents={inDeck || !shown ? 'none' : 'auto'}
      style={[
        styles.slot,
        { left: (layout.width - layout.cardW) / 2, width: layout.cardW, height: layout.cardH, zIndex: inDeck ? 20 + order : focused ? 40 : 10 },
        style,
      ]}
    >
      <DeckCardFace card={card} index={index} width={layout.cardW} height={layout.cardH} onAdd={onAdd} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  tint: {
    position: 'absolute',
  },
  header: {
    height: HEADER,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 16,
    paddingRight: 4,
  },
  title: {
    color: INK,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '500',
    includeFontPadding: false,
  },
  count: {
    height: 22,
    minWidth: 30,
    marginLeft: 8,
    paddingHorizontal: 8,
    borderRadius: 11,
    backgroundColor: COUNT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  countLabel: {
    color: '#dadada',
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  spacer: {
    flex: 1,
  },
  menu: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chips: {
    marginTop: CHIPS_GAP,
    height: CHIP_H,
    flexGrow: 0,
  },
  chipsContent: {
    paddingHorizontal: 16,
    gap: 8,
  },
  search: {
    width: CHIP_H,
    height: CHIP_H,
    borderRadius: CHIP_H / 2,
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chip: {
    height: CHIP_H,
    paddingHorizontal: 14,
    borderRadius: CHIP_H / 2,
    justifyContent: 'center',
  },
  chipLabel: {
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '600',
    includeFontPadding: false,
  },
  trayGlow: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  tray: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: -LIFT,
    backgroundColor: TRAY,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderCurve: 'continuous',
    alignItems: 'center',
  },
  trayEdge: {
    position: 'absolute',
    left: 28,
    right: 28,
    top: 0,
    height: 1.5,
    borderRadius: 1,
  },
  pocket: {
    position: 'absolute',
    left: 12,
    right: 12,
    top: 12,
    height: TRAY_H - 24,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: POCKET,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  pocketIcon: {
    width: 30,
    height: 30,
    borderRadius: 9,
    borderCurve: 'continuous',
    backgroundColor: '#333333',
    alignItems: 'center',
    justifyContent: 'center',
  },
  hint: {
    color: HINT,
    fontSize: 12,
    lineHeight: 15,
    includeFontPadding: false,
  },
  layer: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  slot: {
    position: 'absolute',
    top: 0,
    borderRadius: 28,
    borderCurve: 'continuous',
    // On the wrapper: the card clips its own content, which would clip a shadow too.
    boxShadow: '0 18 36 0 rgba(0,0,0,0.45)',
  },
  pill: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  pillBody: {
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.32)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  pillLabel: {
    color: INK,
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
});
