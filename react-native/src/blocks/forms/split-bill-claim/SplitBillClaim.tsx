import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeInDown,
  ReduceMotion,
  type SharedValue,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, ClipPath, Defs, Ellipse, G, Path, Rect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

export type BillItem = {
  name: string;
  /** In units of the currency. */
  price: number;
};

type Props = {
  /** Claims two items one after the other, then gives both back, every LOOP seconds. */
  autoPlay?: boolean;
  /** The rows autoPlay claims, in order; it gives them back in the reverse order. */
  autoPlayRows?: number[];
  items?: BillItem[];
  /** Written before an amount. */
  symbol?: string;
  title?: string;
  hint?: string;
  backLabel?: string;
  totalLabel?: string;
  payLabel?: string;
  /** The colour of the links, the badge and the Pay button. */
  accent?: string;
  /** Called with the rows that are now claimed, after each change. */
  onChange?: (claimed: number[]) => void;
  /** Called by the Pay button with the share and the claimed rows. Nothing is paid here. */
  onPay?: (share: number, claimed: number[]) => void;
  onBack?: () => void;
};

const ITEMS: BillItem[] = [
  { name: 'Sparkling Water - 0,5 l', price: 1.5 },
  { name: 'Ginger Lemonade - 0,5 l', price: 3.25 },
  { name: 'Pizza - Margherita', price: 21 },
];

/** autoPlay: a finger takes a row at each of GRABS (seconds), and the round repeats every LOOP. */
export const LOOP = 9.8;
const GRABS = [0.8, 2.9, 5.3, 7];
// The finger rests GRAB_FOR on the card, pulls it for PULL_FOR, holds it HOLD_FOR and lets go.
const GRAB_FOR = 0.15;
const PULL_FOR = 0.6;
const HOLD_FOR = 0.35;
const PULL_TO = 98;
/** After a touch autoPlay waits this long before it gives everything back and starts over. */
const USER_HOLD = 3;
const RESET_FOR = 0.9;

const SHEET = '#f3f5f8';
const INK = '#1f2a3d';
const MUTE = '#7c8798';
const GRIP = '#c3cad6';
const TINT = '#edf3ff';

const MARGIN = 22;
const CARD = 96;
const CARD_GAP = 8;
const CARD_RADIUS = 6;
const GRIP_X = 30;
const NAME_X = 54;
const PRICE_INSET = 26;
// The avatar behind a pulled card, and the small one a claimed card keeps under its name.
const AVATAR = 46;
const AVATAR_SMALL = 22;
const BADGE = 18;
// Where the small one sits in the card, from the card's left edge and from its middle line.
const SEAT_X = NAME_X + AVATAR_SMALL / 2;
const SEAT_Y = 15;
const NAME_LIFT = 12;
const NAME_PUSH = 22;
const TOUCH = 50;
const TOUCH_X = 88;
const PANEL = 60;
const PAY = 58;
const BAR_GAP = 6;

// A card follows the finger to PULL_MAX and a quarter as fast past it; let go past CLAIM_AT, it changes hands.
const PULL_MAX = 104;
const CLAIM_AT = 68;
const BACK = { stiffness: 260, damping: 22, mass: 1 };
// The avatar's hop into the card: its time, and how high its arc is.
const HOP = 460;
const HOP_HEIGHT = 20;
const BAR = { stiffness: 190, damping: 20, mass: 1 };
const BAR_AFTER = 320;
const COUNT = 520;
const RISE = 420;
const RISE_AFTER = 180;
const RISE_STAGGER = 90;

type RowHandle = {
  /** autoPlay's finger: it lands on the card, pulls it, and lets go as a real one does. */
  grab: () => void;
  pull: (to: number, seconds: number) => void;
  release: () => void;
  /** Without a finger. */
  settle: () => void;
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/**
 * A finger leaves a card: the card springs back, and if it was pulled far enough the item changes
 * hands. `from` keeps how far it was pulled, which is where the avatar's hop starts.
 */
function letGo(x: SharedValue<number>, from: SharedValue<number>, touch: SharedValue<number>, toggle: () => void) {
  'worklet';
  const pulled = x.get();
  touch.set(withTiming(0, { duration: 160, reduceMotion: ReduceMotion.Never }));
  x.set(withSpring(0, BACK));
  if (pulled < CLAIM_AT) return;
  from.set(pulled);
  scheduleOnRN(toggle);
}

function money(cents: number, symbol: string) {
  return `${symbol}${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}

function tick(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

function nudge() {
  tick(Haptics.ImpactFeedbackStyle.Light);
}

/** The person the bill is shared with: a drawn portrait in a circle. */
function Avatar({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 48 48">
      <Defs>
        <ClipPath id="face">
          <Circle cx={24} cy={24} r={24} />
        </ClipPath>
      </Defs>
      <G clipPath="url(#face)">
        <Rect x={0} y={0} width={48} height={48} fill="#dfe7f5" />
        <Ellipse cx={24} cy={49} rx={19} ry={13} fill="#27364f" />
        <Rect x={20} y={28} width={8} height={10} rx={3} fill="#e7b38f" />
        <Ellipse cx={24} cy={21.5} rx={8.6} ry={10} fill="#f2c5a3" />
        <Path d="M14.6 21C13.4 12.6 17.6 8 24.2 8C31 8 34.6 12.4 33.4 20.6C32.4 17.6 30.4 15.6 27.4 15C23.6 16.8 18.6 16.6 15.8 15.6C15.2 17.2 15 19 14.6 21Z" fill="#232733" />
      </G>
    </Svg>
  );
}

function Badge({ color, minus }: { color: string; minus: boolean }) {
  return (
    <View style={[styles.badge, { backgroundColor: color }]}>
      <View style={styles.badgeBar} />
      {!minus && <View style={[styles.badgeBar, styles.badgeUp]} />}
    </View>
  );
}

type RowProps = {
  index: number;
  item: BillItem;
  claimed: boolean;
  symbol: string;
  accent: string;
  onToggle: (index: number, byHand: boolean) => void;
  onTouch: () => void;
  /** Hands the screen this row's finger, and takes it back when the row goes. */
  register: (index: number, handle: RowHandle | null) => void;
};

/**
 * One item of the bill. It is memoised, and what it is given never changes between renders: the
 * screen renders on every frame while the share counts, and a gesture made anew under a finger
 * would let the card go.
 */
const Row = memo(function Row({ index, item, claimed, symbol, accent, onToggle, onTouch, register }: RowProps) {
  // How far the card is pulled, and how far it was when it was let go.
  const x = useSharedValue(0);
  const from = useSharedValue(PULL_TO);
  // 0 the item is nobody's, 1 it is claimed: the avatar's hop runs on this clock.
  const mine = useSharedValue(claimed ? 1 : 0);
  // autoPlay's finger on the card.
  const touch = useSharedValue(0);
  // The pull has passed the point where letting go changes hands.
  const armed = useSharedValue(false);

  useEffect(() => {
    mine.set(withTiming(claimed ? 1 : 0, { duration: HOP, easing: Easing.linear }));
  }, [claimed, mine]);

  const byHand = useCallback(() => onToggle(index, true), [onToggle, index]);
  const byItself = useCallback(() => onToggle(index, false), [onToggle, index]);

  useEffect(() => {
    register(index, {
      grab: () => touch.set(withTiming(1, { duration: 140, reduceMotion: ReduceMotion.Never })),
      pull: (to, seconds) => x.set(withTiming(to, { duration: seconds * 1000, easing: Easing.bezier(0.3, 0, 0.2, 1), reduceMotion: ReduceMotion.Never })),
      release: () => scheduleOnUI(letGo, x, from, touch, byItself),
      settle: () => {
        touch.set(withTiming(0, { duration: 160 }));
        x.set(withSpring(0, BACK));
      },
    });
    return () => register(index, null);
  }, [register, index, x, from, touch, byItself]);

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX(10)
        .failOffsetY([-14, 14])
        .onBegin(() => {
          scheduleOnRN(onTouch);
        })
        .onChange((event) => {
          const pull = Math.max(0, event.translationX);
          x.set(pull < PULL_MAX ? pull : PULL_MAX + (pull - PULL_MAX) / 4);
          // A tick as the pull passes the point of no return, either way.
          const past = x.get() >= CLAIM_AT;
          if (past !== armed.get()) {
            armed.set(past);
            scheduleOnRN(nudge);
          }
        })
        .onFinalize(() => {
          armed.set(false);
          letGo(x, from, touch, byHand);
        }),
    [x, from, touch, armed, onTouch, byHand],
  );

  const cardStyle = useAnimatedStyle(() => {
    const lifted = smooth(x.get(), 0, 40);
    return {
      transform: [{ translateX: x.get() }],
      backgroundColor: interpolateColor(smooth(mine.get(), 0.3, 1), [0, 1], ['#ffffff', TINT]),
      boxShadow: [{ offsetX: 0, offsetY: 2 + 6 * lifted, blurRadius: 8 + 16 * lifted, color: `rgba(24,39,75,${0.05 + 0.07 * lifted})` }],
    };
  });
  // The avatar lands where the grip is and pushes the name aside, then shrinks and lets it back.
  const nameStyle = useAnimatedStyle(() => {
    const t = mine.get();
    return { transform: [{ translateX: NAME_PUSH * (smooth(t, 0, 0.3) - smooth(t, 0.6, 1)) }, { translateY: -NAME_LIFT * smooth(t, 0.25, 0.9) }] };
  });
  // Uncovered by the card: the avatar grows in the gap, and steps aside for the one that hops.
  const behindStyle = useAnimatedStyle(() => {
    const shown = smooth(x.get(), 18, CLAIM_AT);
    const hopping = mine.get() > 0.001 && mine.get() < 0.999;
    return {
      opacity: hopping ? 0 : shown,
      transform: [{ translateX: x.get() / 2 - 4 - AVATAR / 2 }, { scale: 0.5 + 0.5 * shown + (x.get() >= CLAIM_AT ? 0.06 : 0) }],
    };
  });
  // The avatar that hops: from the gap, up and over the card's edge early in the hop, down to its
  // seat under the name, shrinking in the middle of it. Given back, it runs the other way and fades.
  const hopStyle = useAnimatedStyle(() => {
    const t = mine.get();
    const e = smooth(t, 0, 1);
    const startX = from.get() / 2 - 4;
    const endX = x.get() + SEAT_X;
    return {
      opacity: claimed ? (t > 0.001 ? 1 : 0) : smooth(t, 0, 0.35),
      transform: [
        { translateX: startX + (endX - startX) * e - AVATAR / 2 },
        { translateY: SEAT_Y * e - HOP_HEIGHT * 6.75 * t * (1 - t) * (1 - t) },
        { scale: 1 + (AVATAR_SMALL / AVATAR - 1) * smooth(t, 0.2, 0.8) },
      ],
    };
  });
  const touchStyle = useAnimatedStyle(() => ({
    opacity: touch.get() * 0.5,
    transform: [{ translateX: x.get() + TOUCH_X - TOUCH / 2 }, { scale: 0.6 + 0.4 * touch.get() }],
  }));
  const gripStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(mine.get(), 0, 0.3) + smooth(mine.get(), 0.6, 1),
    backgroundColor: interpolateColor(mine.get(), [0, 1], [GRIP, accent]),
  }));

  return (
    <Animated.View style={styles.row} entering={FadeInDown.duration(RISE).delay(RISE_AFTER + index * RISE_STAGGER)}>
      <Animated.View style={[styles.behind, behindStyle]}>
        <View style={styles.portrait}>
          <Avatar size={AVATAR} />
        </View>
        <Badge color={accent} minus={claimed} />
      </Animated.View>

      <GestureDetector gesture={pan}>
        <Animated.View
          style={[styles.card, cardStyle]}
          accessible
          accessibilityLabel={`${item.name}, ${money(Math.round(item.price * 100), symbol)}${claimed ? ', yours' : ''}`}
          accessibilityHint={claimed ? 'Swipe right to give it back' : 'Swipe right to take it'}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={byHand}
        >
          <View style={styles.grip}>
            <Animated.View style={[styles.gripBar, gripStyle]} />
            <Animated.View style={[styles.gripBar, gripStyle]} />
          </View>
          <Animated.Text style={[styles.name, nameStyle]} numberOfLines={1}>
            {item.name}
          </Animated.Text>
          <Text style={styles.price}>{money(Math.round(item.price * 100), symbol)}</Text>
        </Animated.View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.hop, hopStyle]}>
        <Avatar size={AVATAR} />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.touch, touchStyle]} />
    </Animated.View>
  );
});

/**
 * One step of splitting a bill: the receipt's items as cards, each of which you take by swiping it
 * to the right. The card uncovers your portrait with a "+" badge; let go and the card springs back
 * while the portrait hops into it, your share counts up, and a bar with the Pay button rises from
 * the bottom. Swiping a card that is yours gives it back. Nothing is paid here.
 */
export function SplitBillClaim({
  autoPlay = false,
  autoPlayRows = [0, 2],
  items = ITEMS,
  symbol = '$',
  title = 'Pick your items',
  hint = 'Swipe right to assign an item to yourself.',
  backLabel = 'Back',
  totalLabel = 'In total',
  payLabel = 'Pay',
  accent = '#1e6bf2',
  onChange,
  onPay,
  onBack,
}: Props) {
  const insets = useSafeAreaInsets();
  const [claimed, setClaimed] = useState<boolean[]>(() => items.map(() => false));
  // The share as it counts, in cents; and how many items it was for, kept while the bar leaves.
  const [counted, setCounted] = useState(0);
  const [count, setCount] = useState(1);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const rows = useRef<(RowHandle | null)[]>([]);
  // The share in cents, on its way to what is claimed; and how far the bar has risen.
  const share = useSharedValue(0);
  const bar = useSharedValue(0);

  const cents = items.map((item) => Math.round(item.price * 100));
  const total = cents.reduce((sum, price) => sum + price, 0);
  const mine = claimed.flatMap((yes, index) => (yes ? [index] : []));
  const bottom = Math.max(insets.bottom, 12) + 6;
  const barHeight = PANEL + BAR_GAP + PAY;

  /** What follows a change of hands: the share counts to its new value and the bar comes or goes. */
  const apply = (next: boolean[]) => {
    const list = next.flatMap((yes, index) => (yes ? [index] : []));
    const sum = list.reduce((all, index) => all + cents[index], 0);
    setClaimed(next);
    if (list.length > 0) setCount(list.length);
    share.set(withTiming(sum, { duration: COUNT, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }));
    // The bar waits for the avatar to land; it leaves as soon as nothing is claimed.
    bar.set(list.length > 0 ? withDelay(bar.get() > 0 ? 0 : BAR_AFTER, withSpring(1, BAR)) : withDelay(BAR_AFTER, withTiming(0, { duration: 260 })));
    onChange?.(list);
  };

  const toggle = (index: number, byHand: boolean) => {
    if (byHand) tick(Haptics.ImpactFeedbackStyle.Medium);
    apply(claimed.map((yes, at) => (at === index ? !yes : yes)));
  };

  useAnimatedReaction(
    () => Math.round(share.get()),
    (now, old) => {
      if (now !== old) scheduleOnRN(setCounted, now);
    },
  );

  /** A touch takes over from the autoPlay loop, which starts again from an untouched bill a while later. */
  const touched = () => {
    rows.current.forEach((row) => row?.settle());
    setTouches((n) => n + 1);
  };

  // The loop and the rows read the latest handlers when they need them.
  const latest = useRef({ apply, toggle, touched });
  useEffect(() => {
    latest.current = { apply, toggle, touched };
  });
  const onToggle = useCallback((index: number, byHand: boolean) => latest.current.toggle(index, byHand), []);
  const onTouch = useCallback(() => latest.current.touched(), []);
  const register = useCallback((index: number, handle: RowHandle | null) => {
    rows.current[index] = handle;
  }, []);

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, act: () => void) => timers.push(setTimeout(act, ms));
    // Take the rows in order, then give them back in the reverse order.
    const order = [...autoPlayRows, ...[...autoPlayRows].reverse()];
    const cycle = (origin: number) => {
      const at = (seconds: number, act: () => void) => after(origin + seconds * 1000 - Date.now(), act);
      order.forEach((row, step) => {
        const start = GRABS[step % GRABS.length] + Math.floor(step / GRABS.length) * LOOP;
        at(start, () => rows.current[row]?.grab());
        at(start + GRAB_FOR, () => rows.current[row]?.pull(PULL_TO, PULL_FOR));
        at(start + GRAB_FOR + PULL_FOR + HOLD_FOR, () => rows.current[row]?.release());
      });
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        rows.current.forEach((row) => row?.settle());
        latest.current.apply(items.map(() => false));
        after(RESET_FOR * 1000, () => cycle(Date.now()));
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, autoPlayRows, items, touches]);

  const barStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (barHeight + bottom + 24) * (1 - bar.get()) }] }));

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 4 }]}>
        <View style={styles.bar}>
          <Pressable
            style={styles.back}
            hitSlop={10}
            accessibilityRole="button"
            onPress={() => {
              touched();
              onBack?.();
            }}
          >
            {({ pressed }) => (
              <>
                <Svg width={9} height={15} viewBox="0 0 9 15" opacity={pressed ? 0.5 : 1}>
                  <Path d="M7.5 1.5L1.5 7.5L7.5 13.5" stroke={accent} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                </Svg>
                <Text style={[styles.backLabel, { color: accent, opacity: pressed ? 0.5 : 1 }]}>{backLabel}</Text>
              </>
            )}
          </Pressable>
          <Avatar size={30} />
        </View>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.hint} numberOfLines={2}>
          {hint}
        </Text>
      </View>

      <View style={styles.sheet}>
        <View style={styles.total}>
          <Text style={styles.totalLabel}>{totalLabel}</Text>
          <Text style={styles.totalSum}>{money(total, symbol)}</Text>
        </View>
        {items.map((item, index) => (
          <Row
            key={index}
            index={index}
            item={item}
            claimed={claimed[index] ?? false}
            symbol={symbol}
            accent={accent}
            onToggle={onToggle}
            onTouch={onTouch}
            register={register}
          />
        ))}
      </View>

      <Animated.View
        style={[styles.pay, { bottom }, barStyle]}
        pointerEvents={mine.length > 0 ? 'auto' : 'none'}
        accessibilityElementsHidden={mine.length === 0}
        importantForAccessibility={mine.length > 0 ? 'auto' : 'no-hide-descendants'}
      >
        <View style={styles.panel}>
          <Text style={styles.count}>{count === 1 ? '1 item' : `${count} items`}</Text>
          <Text style={styles.share}>{money(counted, symbol)}</Text>
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={() => {
            touched();
            tick(Haptics.ImpactFeedbackStyle.Medium);
            onPay?.(counted / 100, mine);
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.payButton,
                { backgroundColor: accent, opacity: pressed ? 0.82 : 1, transform: [{ scale: pressed ? 0.985 : 1 }], transitionProperty: ['opacity', 'transform'], transitionDuration: 110 },
              ]}
            >
              <Text style={styles.payLabel} numberOfLines={1}>
                {money(counted, symbol)} - {payLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SHEET,
  },
  header: {
    paddingHorizontal: MARGIN + 2,
    paddingBottom: 28,
    backgroundColor: '#ffffff',
  },
  bar: {
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  back: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  backLabel: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
  },
  title: {
    marginTop: 18,
    color: INK,
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  hint: {
    marginTop: 6,
    color: MUTE,
    fontSize: 14,
    lineHeight: 19,
  },
  sheet: {
    flex: 1,
    paddingHorizontal: MARGIN,
  },
  total: {
    height: 74,
    paddingHorizontal: 4,
    paddingTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  totalLabel: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
  totalSum: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  row: {
    height: CARD,
    marginBottom: CARD_GAP,
    justifyContent: 'center',
  },
  card: {
    height: CARD,
    borderRadius: CARD_RADIUS,
    flexDirection: 'row',
    alignItems: 'center',
  },
  grip: {
    position: 'absolute',
    left: GRIP_X - 4,
    flexDirection: 'row',
    gap: 3,
  },
  gripBar: {
    width: 2,
    height: 13,
    borderRadius: 1,
  },
  name: {
    flex: 1,
    marginLeft: NAME_X,
    marginRight: 12,
    color: INK,
    fontSize: 15.5,
    lineHeight: 20,
    fontWeight: '500',
  },
  price: {
    marginRight: PRICE_INSET,
    color: INK,
    fontSize: 15.5,
    lineHeight: 20,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  behind: {
    position: 'absolute',
    left: 0,
    width: AVATAR,
    height: AVATAR,
  },
  portrait: {
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    backgroundColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: 6, blurRadius: 14, color: 'rgba(24,39,75,0.18)' }],
  },
  badge: {
    position: 'absolute',
    right: -3,
    bottom: -1,
    width: BADGE,
    height: BADGE,
    borderRadius: BADGE / 2,
    borderWidth: 2,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeBar: {
    position: 'absolute',
    width: 8,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#ffffff',
  },
  badgeUp: {
    transform: [{ rotate: '90deg' }],
  },
  hop: {
    position: 'absolute',
    left: 0,
    width: AVATAR,
    height: AVATAR,
  },
  touch: {
    position: 'absolute',
    left: 0,
    width: TOUCH,
    height: TOUCH,
    borderRadius: TOUCH / 2,
    backgroundColor: '#8a94a6',
  },
  pay: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  panel: {
    height: PANEL,
    marginBottom: BAR_GAP,
    paddingHorizontal: 24,
    borderRadius: CARD_RADIUS,
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 10, color: 'rgba(24,39,75,0.07)' }],
  },
  count: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
  share: {
    color: INK,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  payButton: {
    height: PAY,
    borderRadius: CARD_RADIUS,
    alignItems: 'center',
    justifyContent: 'center',
  },
  payLabel: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
});
