import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolate,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { HANDLE_BOTTOM, HANDLE_HEIGHT, PAGES, type PageInfo } from './SwitcherDeckScreens';

type Props = {
  /** Opens the deck, scrolls it, opens another page and goes back, on a loop. */
  autoPlay?: boolean;
  /** The pages of the app, in deck order. */
  pages?: PageInfo[];
  /** Called with a page's title when it is opened from the deck. */
  onOpenPage?: (title: string) => void;
};

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const SERIF = process.env.EXPO_OS === 'ios' ? 'Iowan Old Style' : 'serif';

// The deck. A page in the deck is DECK_SCALE of the screen, centred DECK_Y pt below the middle.
// Its place depends on p, its slot minus the deck's scroll (0 = the page in front): pages to the
// right lie on top and spread wide, pages to the left tuck under, recede and darken, like the
// pages of a fanned deck. Every page is turned about its upright axis (right edge away), more so
// the further left it lies.
const DECK_SCALE = 0.6;
const DECK_Y = 22;
const SPREAD = 0.56;
const SPREAD_SKEW = 0.15;
const RECEDE = 0.045;
const TURN_LEFT = 30;
const TURN_FRONT = 17;
const TURN_RIGHT = 8;
const PERSPECTIVE = 1100;
const DIM_PER_PAGE = 0.2;
const DIM_MAX = 0.55;
// Corner radius of a page in its own (unscaled) points: none on the full screen, then rounded
// as soon as it starts to shrink.
const RADIUS_SHRINKING = 50;
const RADIUS_DECK = 38;
// The title chip above each page, in screen points (drawn inside the page, so divided by its scale).
const CHIP_HEIGHT = 28;
const CHIP_GAP = 12;

// Pulling the handle: the finger travels OPEN_DIST of the screen's height to open the deck all
// the way. A release is projected 0.18 s ahead; past OPEN_AT it opens, otherwise it falls back.
const HANDLE_ZONE = 70;
const OPEN_DIST = 0.42;
const OPEN_AT = 0.38;
const THROW = 0.18;
const FOLLOW_X = 0.55;
// Scrolling: one page per SCROLL_STEP of the page width; a release is thrown 0.22 s ahead.
const SCROLL_STEP = 0.55;
const SCROLL_THROW = 0.22;
// Flicking a page up closes it: faster than DISMISS_SPEED pt/s or past DISMISS_AT of its height.
const DISMISS_SPEED = 800;
const DISMISS_AT = 0.28;
const DISMISS_TIME = 300;

const OPEN_SPRING = { duration: 560, dampingRatio: 0.82 };
const ZOOM_SPRING = { duration: 540, dampingRatio: 0.9 };
const SCROLL_SPRING = { duration: 620, dampingRatio: 0.88 };
const SLOT_SPRING = { duration: 520, dampingRatio: 0.84 };
const BACK_SPRING = { duration: 420, dampingRatio: 0.8 };
const REDUCED_TIMING = { duration: 220, easing: Easing.bezier(0.23, 1, 0.32, 1) };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);

// autoPlay (seconds). The loop starts and ends on the journal at full screen. Each stroke is a
// virtual finger: the handle pulled up, a flick across the deck (to `to`), or a tap on a page.
const LOOP = 12;
type Stroke = { kind: 'handle' | 'flick' | 'tap'; t0: number; t1: number; from: number; to: number };
const STROKES: Stroke[] = [
  { kind: 'handle', t0: 0.9, t1: 1.7, from: 0, to: 0 },
  { kind: 'flick', t0: 2.75, t1: 3.05, from: 0, to: 2 },
  { kind: 'flick', t0: 3.95, t1: 4.25, from: 2, to: 3 },
  { kind: 'flick', t0: 5.0, t1: 5.3, from: 3, to: 2 },
  { kind: 'tap', t0: 6.15, t1: 6.25, from: 2, to: 2 },
  { kind: 'handle', t0: 7.55, t1: 8.35, from: 2, to: 2 },
  { kind: 'flick', t0: 9.25, t1: 9.55, from: 2, to: 0 },
  { kind: 'tap', t0: 10.35, t1: 10.45, from: 0, to: 0 },
];
// A touch stops the loop for USER_HOLD s; it then goes back to the journal over LEAD s and restarts.
const USER_HOLD = 5;
const LEAD = 2.2;

const NONE = 0;
const UNDECIDED = 1;
const HANDLE = 2;
const SCROLL = 3;
const LIFT = 4;

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

/** Pushing past [lo, hi] meets a soft resistance that never quite gives. */
function rubber(x: number, lo: number, hi: number, give: number) {
  'worklet';
  if (x > hi) return hi + (1 - 1 / (((x - hi) * 0.55) / give + 1)) * give;
  if (x < lo) return lo - (1 - 1 / (((lo - x) * 0.55) / give + 1)) * give;
  return x;
}

/** Horizontal offset of a page's centre from the screen's, for a page p places from the front. */
function deckX(p: number, cardWidth: number) {
  'worklet';
  return cardWidth * p * (SPREAD + SPREAD_SKEW * Math.tanh(p));
}

function deckScale(p: number) {
  'worklet';
  return DECK_SCALE * (1 - RECEDE * clamp(-p, 0, 3));
}

function deckTurn(p: number) {
  'worklet';
  return interpolate(p, [-3, 0, 3], [TURN_LEFT, TURN_FRONT, TURN_RIGHT], 'clamp');
}

/** The slot of the topmost page under a point in the deck, or -1. Pages to the right lie on top. */
function hitSlot(x: number, y: number, count: number, scroll: number, width: number, height: number) {
  'worklet';
  const cardWidth = width * DECK_SCALE;
  for (let k = count - 1; k >= 0; k -= 1) {
    const p = k - scroll;
    const s = deckScale(p) / DECK_SCALE;
    const cx = width / 2 + deckX(p, cardWidth);
    const cy = height / 2 + DECK_Y;
    if (Math.abs(x - cx) < (cardWidth * s) / 2 && Math.abs(y - cy) < (height * DECK_SCALE * s) / 2) return k;
  }
  return -1;
}

/** Whether a clock that went from `before` to `now` (wrapping at LOOP) crossed `at`. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

function ease(u: number) {
  'worklet';
  return EASE_IN_OUT(clamp(u, 0, 1));
}

function haptic(kind: 'light' | 'medium' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'light' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type CardProps = {
  id: number;
  /** Place in the deck, or -1 once the page has been flicked away. */
  slotIndex: number;
  page: PageInfo;
  width: number;
  height: number;
  top: number;
  bottom: number;
  open: SharedValue<number>;
  scroll: SharedValue<number>;
  active: SharedValue<number>;
  dragX: SharedValue<number>;
  liftId: SharedValue<number>;
  lift: SharedValue<number>;
  reduced: boolean;
};

/** One page of the app: full screen while it is the open one, a turned card in the deck otherwise. */
function DeckCard({ id, slotIndex, page, width, height, top, bottom, open, scroll, active, dragX, liftId, lift, reduced }: CardProps) {
  const slot = useSharedValue(Math.max(0, slotIndex));
  const presence = useSharedValue(slotIndex >= 0 ? 1 : 0);
  const wasVisible = useRef(slotIndex >= 0);

  // A page that stays in the deck slides to its new slot; one that comes back fades in at it.
  useEffect(() => {
    const visible = slotIndex >= 0;
    if (visible && wasVisible.current) {
      slot.set(reduced ? withTiming(slotIndex, REDUCED_TIMING) : withSpring(slotIndex, SLOT_SPRING));
    } else if (visible) {
      slot.set(slotIndex);
      presence.set(0);
      presence.set(withTiming(1, { duration: 420, easing: EASE_OUT }));
    } else {
      presence.set(0);
    }
    wasVisible.current = visible;
  }, [slotIndex, slot, presence, reduced]);

  const cardStyle = useAnimatedStyle(() => {
    const o = open.get();
    const shown = clamp(o, 0, 1);
    const p = slot.get() - scroll.get();
    const cardWidth = width * DECK_SCALE;
    const dx = deckX(p, cardWidth);
    const ds = deckScale(p);
    const turn = deckTurn(p);
    const lifted = liftId.get() === id ? lift.get() : 0;
    let tx: number;
    let ty: number;
    let s: number;
    let r: number;
    let alpha = 1;
    if (active.get() === id) {
      // The open page shrinks from the full screen into its place in the deck, following the finger.
      tx = dx * o + dragX.get();
      ty = DECK_Y * o;
      s = 1 + (ds - 1) * o;
      r = turn * o;
    } else {
      // The other pages wait off to their side and slide in as the deck opens.
      tx = dx + (1 - shown) * width * 1.15 * (p >= 0 ? 1 : -1);
      ty = DECK_Y;
      s = ds;
      r = turn;
      alpha = Math.min(1, shown * 1.6);
    }
    const presenceNow = presence.get();
    alpha *= presenceNow * (1 - clamp(-lifted / (height * 0.5), 0, 1));
    return {
      opacity: alpha,
      transform: [
        { perspective: PERSPECTIVE },
        { translateX: tx },
        { translateY: ty + lifted },
        { scale: s * (0.92 + 0.08 * presenceNow) },
        { rotateY: `${r}deg` },
      ],
    };
  });

  const radiusStyle = useAnimatedStyle(() => ({
    borderRadius: interpolate(open.get(), [0, 0.08, 1], [0, RADIUS_SHRINKING, RADIUS_DECK], 'clamp'),
  }));
  const dimStyle = useAnimatedStyle(() => ({
    opacity: clamp(-(slot.get() - scroll.get()) * DIM_PER_PAGE, 0, DIM_MAX) * clamp(open.get(), 0, 1),
  }));
  const chipStyle = useAnimatedStyle(() => {
    const shown = clamp((open.get() - 0.55) / 0.45, 0, 1);
    return { opacity: shown, transform: [{ translateY: (1 - shown) * 30 }] };
  });

  const unit = 1 / DECK_SCALE;
  return (
    // Each turned page sits in its own flat wrapper, so iOS does not depth-sort the pages into
    // each other; their order on screen is the order of the wrappers.
    <View collapsable={false} pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[{ width, height }, cardStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.shadow, { backgroundColor: page.tint }, radiusStyle]} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.clip, radiusStyle]}>
          <page.Page width={width} height={height} top={top} bottom={bottom} />
          <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dimStyle]} />
        </Animated.View>
        <Animated.View
          style={[
            styles.chip,
            {
              top: -(CHIP_HEIGHT + CHIP_GAP) * unit,
              height: CHIP_HEIGHT * unit,
              borderRadius: (CHIP_HEIGHT / 2) * unit,
              paddingHorizontal: 11 * unit,
              gap: 7 * unit,
            },
            chipStyle,
          ]}
        >
          <View
            style={{
              width: 14 * unit,
              height: 14 * unit,
              borderRadius: 4 * unit,
              backgroundColor: page.tint,
              borderWidth: 1.2 * unit,
              borderColor: 'rgba(255,255,255,0.35)',
            }}
          />
          <Text style={[styles.chipText, { fontSize: 13 * unit }]}>{page.title}</Text>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

/**
 * A photo journal with four pages and an app switcher of its own. Pulling up the handle at the
 * bottom shrinks the page under the finger into a card while the other pages slide in beside
 * it, fanned out as a deck in perspective with a title chip over each. The deck scrolls
 * sideways with momentum; tapping a page zooms it back to the full screen, and flicking one up
 * closes it while the rest close the gap.
 */
export function SwitcherDeckNav({ autoPlay = false, pages = PAGES, onOpenPage }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const all = pages.map((_, i) => i);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [order, setOrder] = useState<number[]>(all);
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const bottomInset = insets.bottom;

  // 0 = the active page fills the screen, 1 = the deck is open.
  const open = useSharedValue(0);
  // Which slot of the deck is in front, continuously.
  const scroll = useSharedValue(0);
  const active = useSharedValue(0);
  const dragX = useSharedValue(0);
  const liftId = useSharedValue(-1);
  const lift = useSharedValue(0);
  const orderSV = useSharedValue<number[]>(all);
  const widthSV = useSharedValue(0);
  const heightSV = useSharedValue(0);
  const mode = useSharedValue(NONE);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const startOpen = useSharedValue(0);
  const startScroll = useSharedValue(0);
  // autoPlay: the loop's clock, a pause after a touch, and the virtual finger.
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerAlpha = useSharedValue(0);
  const fingerPress = useSharedValue(0);

  useEffect(() => {
    orderSV.set(order);
  }, [order, orderSV]);
  useEffect(() => {
    widthSV.set(width);
    heightSV.set(height);
  }, [width, height, widthSV, heightSV]);
  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const removePage = (id: number) => setOrder((now) => now.filter((x) => x !== id));
  const restoreAll = () => {
    // The flicked-away page is still parked off screen; it comes back from its slot instead.
    liftId.set(-1);
    lift.set(0);
    setOrder(all);
  };
  const opened = (id: number, user: boolean) => {
    if (!user) return;
    haptic('light');
    onOpenPage?.(pages[id]?.title ?? '');
  };

  const animate = (to: number, spring: { duration: number; dampingRatio: number }, velocity = 0) => {
    'worklet';
    return reduced ? withTiming(to, REDUCED_TIMING) : withSpring(to, { ...spring, velocity });
  };

  const openDeck = (velocity: number, user: boolean) => {
    'worklet';
    open.set(animate(1, OPEN_SPRING, velocity));
    dragX.set(animate(0, BACK_SPRING));
    if (user) scheduleOnRN(haptic, 'light');
  };
  const closeDeck = (velocity: number) => {
    'worklet';
    open.set(animate(0, BACK_SPRING, velocity));
    dragX.set(animate(0, BACK_SPRING));
  };
  /** Zooms the page in `slot` back to the full screen. */
  const openSlot = (slot: number, user: boolean) => {
    'worklet';
    const ids = orderSV.get();
    if (ids.length === 0) return;
    const k = clamp(slot, 0, ids.length - 1);
    active.set(ids[k]);
    scroll.set(animate(k, ZOOM_SPRING));
    open.set(animate(0, ZOOM_SPRING));
    scheduleOnRN(opened, ids[k], user);
  };
  const scrollTo = (slot: number, velocity: number) => {
    'worklet';
    scroll.set(animate(clamp(slot, 0, orderSV.get().length - 1), SCROLL_SPRING, velocity));
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    fingerAlpha.set(withTiming(0, { duration: 120 }));
  };

  // A selection tick each time another page reaches the front under the user's finger.
  useAnimatedReaction(
    () => Math.round(scroll.get()),
    (now, before) => {
      if (before !== null && now !== before && mode.get() === SCROLL) scheduleOnRN(haptic, 'tick');
    },
  );

  // The script, driven from the frame clock so a recording of one loop joins up cleanly.
  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (mode.get() !== NONE) hold.set(USER_HOLD);
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) clock.set(-LEAD);
      return;
    }
    if (!looping.get()) return;
    const W = widthSV.get();
    const H = heightSV.get();
    if (W === 0) return;
    const before = clock.get();
    let t = before + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);

    // The lead-in after a touch: everything put back, then the journal opened from the deck.
    if (before < 0) {
      if (crossed(before, t, -LEAD + 0.001)) {
        if (orderSV.get().length < all.length) scheduleOnRN(restoreAll);
        if (active.get() !== 0 || open.get() > 0.02) openDeck(0, false);
        else clock.set(0);
      }
      if (crossed(before, t, -1.1)) openSlot(0, false);
      return;
    }

    const cardWidth = W * DECK_SCALE;
    const step = cardWidth * SCROLL_STEP;
    // The virtual finger lands on the handle pill's centre.
    const handleY = H - bottomInset - HANDLE_BOTTOM - HANDLE_HEIGHT / 2;
    const deckCY = H / 2 + DECK_Y;
    let alpha = 0;
    for (let i = 0; i < STROKES.length; i += 1) {
      const st = STROKES[i];
      const u = (t - st.t0) / (st.t1 - st.t0);
      // The finger shows a little before it moves and stays a moment after it lifts.
      const near = clamp(Math.min((t - st.t0 + 0.3) / 0.2, (st.t1 + 0.35 - t) / 0.2), 0, 1);
      if (near > alpha) alpha = near;
      if (t < st.t0 - 0.3 || t > st.t1 + 0.35) continue;
      if (st.kind === 'handle') {
        const e = ease(u);
        fingerX.set(W / 2 + 26 * e);
        fingerY.set(handleY - e * 0.5 * OPEN_DIST * H);
        if (u >= 0 && u <= 1) {
          open.set(0.5 * e);
          dragX.set(26 * e * FOLLOW_X);
        }
        if (crossed(before, t, st.t1)) openDeck(2.2, false);
      } else if (st.kind === 'flick') {
        // The deck follows the finger for part of the way; the release throws it the rest.
        const travel = -(st.to - st.from) * step * 0.7;
        const e = ease(u);
        fingerX.set(W / 2 - travel / 2 + travel * e);
        fingerY.set(deckCY + 40);
        if (u >= 0 && u <= 1) scroll.set(st.from - (travel * e) / step);
        if (crossed(before, t, st.t1)) scrollTo(st.to, Math.sign(st.to - st.from) * 2.2);
      } else {
        fingerX.set(W / 2 + deckX(st.to - scroll.get(), cardWidth) - cardWidth * 0.08);
        fingerY.set(deckCY + 30);
        fingerPress.set(clamp(1 - Math.abs(t - (st.t0 + st.t1) / 2) / 0.25, 0, 1));
        if (crossed(before, t, st.t1)) openSlot(st.to, false);
      }
    }
    fingerAlpha.set(alpha);
  });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart((event) => {
      touched();
      startX.set(event.x - event.translationX);
      startY.set(event.y - event.translationY);
      mode.set(UNDECIDED);
    })
    .onUpdate((event) => {
      const W = widthSV.get();
      const H = heightSV.get();
      const count = orderSV.get().length;
      if (mode.get() === UNDECIDED) {
        const vertical = Math.abs(event.translationY) > Math.abs(event.translationX);
        if (open.get() < 0.5) {
          // Closed: only the handle at the bottom answers, and only to a pull upwards.
          if (startY.get() > H - bottomInset - HANDLE_ZONE && vertical && event.translationY < 0) {
            mode.set(HANDLE);
            startOpen.set(open.get());
          } else {
            mode.set(NONE);
          }
        } else if (vertical && event.translationY < 0) {
          const k = hitSlot(startX.get(), startY.get(), count, scroll.get(), W, H);
          if (k >= 0) {
            liftId.set(orderSV.get()[k]);
            lift.set(0);
            mode.set(LIFT);
          } else {
            mode.set(NONE);
          }
        } else if (!vertical) {
          startScroll.set(scroll.get());
          mode.set(SCROLL);
        } else {
          mode.set(NONE);
        }
      }
      const m = mode.get();
      if (m === HANDLE) {
        const raw = startOpen.get() - event.translationY / (H * OPEN_DIST);
        open.set(rubber(raw, 0, 1, 0.12));
        dragX.set(event.translationX * FOLLOW_X);
      } else if (m === SCROLL) {
        const raw = startScroll.get() - event.translationX / (W * DECK_SCALE * SCROLL_STEP);
        scroll.set(rubber(raw, 0, count - 1, 0.35));
      } else if (m === LIFT) {
        const ty = event.translationY;
        // The last page left cannot be closed: it only gives a little.
        lift.set(ty < 0 && count > 1 ? ty : rubber(ty, 0, 0, 40));
      }
    })
    .onEnd((event) => {
      const W = widthSV.get();
      const H = heightSV.get();
      const m = mode.get();
      mode.set(NONE);
      if (m === HANDLE) {
        const v = -event.velocityY / (H * OPEN_DIST);
        if (open.get() + v * THROW > OPEN_AT) {
          openDeck(v, true);
        } else {
          closeDeck(v);
        }
      } else if (m === SCROLL) {
        const v = -event.velocityX / (W * DECK_SCALE * SCROLL_STEP);
        scrollTo(Math.round(scroll.get() + v * SCROLL_THROW), v);
      } else if (m === LIFT) {
        const id = liftId.get();
        const count = orderSV.get().length;
        if (count > 1 && (event.velocityY < -DISMISS_SPEED || lift.get() < -H * DECK_SCALE * DISMISS_AT)) {
          scheduleOnRN(haptic, 'medium');
          lift.set(
            withTiming(-H * 0.8, { duration: DISMISS_TIME, easing: EASE_OUT }, (done) => {
              if (done) scheduleOnRN(removePage, id);
            }),
          );
          // The front slot stays in range once the page is gone.
          if (scroll.get() > count - 2) scrollTo(count - 2, 0);
        } else {
          lift.set(animate(0, BACK_SPRING, event.velocityY));
        }
      }
    })
    .onFinalize(() => {
      mode.set(NONE);
    });

  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (open.get() < 0.85) return;
      const W = widthSV.get();
      const H = heightSV.get();
      const count = orderSV.get().length;
      // The strip under the deck reopens the pages that were closed.
      if (count < all.length && event.y > H - bottomInset - 64) {
        scheduleOnRN(restoreAll);
        return;
      }
      const k = hitSlot(event.x, event.y, count, scroll.get(), W, H);
      openSlot(k >= 0 ? k : Math.round(scroll.get()), true);
    });

  const deckStyle = useAnimatedStyle(() => ({ opacity: clamp(open.get(), 0, 1) }));
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: fingerAlpha.get() * 0.9,
    transform: [
      { translateX: fingerX.get() - 23 },
      { translateY: fingerY.get() - 23 },
      { scale: 1 - 0.18 * fingerPress.get() },
    ],
  }));

  const closed = all.length - order.length;
  const rest = all.filter((id) => !order.includes(id));
  const top = insets.top;

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, deckStyle]}>
          <LinearGradient colors={['#23201c', '#0d0c0b']} style={StyleSheet.absoluteFill} />
          <View style={[styles.deckHeader, { top: top + 14 }]}>
            <Text style={styles.deckKicker}>SLOW DEPARTURES</Text>
            <Text style={styles.deckTitle}>
              {order.length} open {order.length === 1 ? 'page' : 'pages'}
            </Text>
          </View>
          <View style={[styles.deckFooter, { bottom: bottomInset + 18 }]}>
            {closed > 0 ? (
              <View style={styles.restore}>
                <Text style={styles.restoreText}>
                  Reopen {closed} closed {closed === 1 ? 'page' : 'pages'}
                </Text>
              </View>
            ) : (
              <Text style={styles.deckHint}>Tap a page to open it · Flick it up to close</Text>
            )}
          </View>
        </Animated.View>

        {size && (
          <>
            {/* Closed pages first, then the deck from left to right, so pages to the right lie on top. */}
            {[...rest, ...order].map((id) => (
              <DeckCard
                key={id}
                id={id}
                slotIndex={order.indexOf(id)}
                page={pages[id]}
                width={width}
                height={height}
                top={top}
                bottom={bottomInset}
                open={open}
                scroll={scroll}
                active={active}
                dragX={dragX}
                liftId={liftId}
                lift={lift}
                reduced={reduced}
              />
            ))}
          </>
        )}

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0d0c0b', overflow: 'hidden' },
  shadow: { boxShadow: '0 24px 60px rgba(0,0,0,0.55)' },
  clip: { overflow: 'hidden' },
  dim: { backgroundColor: '#0d0c0b' },
  chip: {
    position: 'absolute',
    left: 0,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,250,240,0.12)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,250,240,0.18)',
  },
  chipText: { fontFamily: SANS, fontWeight: '600', color: '#f4efe6', letterSpacing: 0.2 },
  deckHeader: { position: 'absolute', left: 24, right: 24, gap: 2 },
  deckKicker: { fontFamily: SANS, fontSize: 11, fontWeight: '700', letterSpacing: 1.8, color: '#c98a64' },
  deckTitle: { fontFamily: SERIF, fontSize: 26, fontWeight: '700', color: '#f4efe6', letterSpacing: -0.3 },
  deckFooter: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  deckHint: { fontFamily: SANS, fontSize: 13, fontWeight: '500', color: 'rgba(244,239,230,0.5)' },
  restore: {
    height: 36,
    paddingHorizontal: 16,
    borderRadius: 18,
    justifyContent: 'center',
    backgroundColor: 'rgba(244,239,230,0.12)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(244,239,230,0.25)',
  },
  restoreText: { fontFamily: SANS, fontSize: 13, fontWeight: '600', color: '#f4efe6' },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: 'rgba(255,255,255,0.35)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.7)',
  },
});
