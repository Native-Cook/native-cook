import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  interpolate,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useAutoPlay } from './useAutoPlay';

type Method = {
  icon: ComponentProps<typeof Ionicons>['name'];
  title: string;
  subtitle: string;
  /** Shown in the trailing pill. */
  badge: string;
  instant?: boolean;
  /** The pill carries a green tick. */
  enabled?: boolean;
};

type Props = {
  /** The methods of the sheet; the last one only shows at the large detent. */
  methods?: Method[];
  pageTitle?: string;
  pageCaption?: string;
  buttonLabel?: string;
  sheetTitle?: string;
  sheetCaption?: string;
  /** Opens and closes the sheet, pulls it to the large detent and picks a method by itself. */
  autoPlay?: boolean;
  /** Called with the index of the method that was tapped. */
  onSelect?: (index: number) => void;
  /** Called when the sheet settles at a detent: 'closed', 'medium' or 'large'. */
  onDetentChange?: (detent: 'closed' | 'medium' | 'large') => void;
};

/** Seconds for one pass of the autoPlay loop. */
export const LOOP = 10;
// The timeline, in seconds from the start of a loop.
const TOUCH_BUTTON_AT = 0.35;
const PRESS_BUTTON_AT = 0.5;
const OPEN_AT = 0.65;
const TOUCH_GRABBER_AT = 2.2;
const DRAG_UP_AT = 2.4;
const RELEASE_UP_AT = 3.15;
const TOUCH_ROW_AT = 5.0;
const PRESS_ROW_AT = 5.4;
const SELECT_AT = 5.7;
const TOUCH_GRABBER_AGAIN_AT = 6.5;
const DRAG_DOWN_AT = 6.7;
const RELEASE_DOWN_AT = 7.6;
const CLEAR_AT = 8.3;

const METHODS: Method[] = [
  { icon: 'business-outline', title: 'Connect bank account', subtitle: '4-5 business days', badge: 'Free' },
  { icon: 'card-outline', title: 'Debit card', subtitle: 'Instant transfer', badge: '2% fee', instant: true },
  { icon: 'swap-horizontal', title: 'Regular bank transfer', subtitle: '0-1 business days', badge: 'Free' },
  { icon: 'diamond', title: 'Get paid from partner', subtitle: 'Instant transfer', badge: 'Enabled', instant: true, enabled: true },
  { icon: 'wallet-outline', title: 'Crypto wallet', subtitle: 'Instant transfer', badge: '1% fee', instant: true },
];

const PAGE = '#ffffff';
const INK = '#111113';
const MUTE = '#6b6b70';
const FAINT = '#9a9aa0';
const LINE = '#ebebee';
const PILL_LINE = '#e2e2e6';
const ICON_WELL = '#f3f3f5';
const BOLT = '#f5a623';
const GREEN = '#1fa855';
const TEAL = '#0fb5a4';
const TEAL_WELL = '#e3f7f4';
const GRABBER = '#d6d6da';

type Account = { name: string; digits: string; letter: string; colors: [string, string]; primary?: boolean };
const ACCOUNTS: Account[] = [
  { name: 'Northgate Bank', digits: '4793', letter: 'N', colors: ['#2c5cc5', '#1b3a8a'], primary: true },
  { name: 'Harbor Credit Union', digits: '2208', letter: 'H', colors: ['#c2410c', '#8a2c08'] },
];
const RECENT = [
  { title: 'From Northgate Bank', when: 'Today, 9:41 AM', amount: '+$250.00' },
  { title: 'From debit card', when: 'Mon, 6:12 PM', amount: '+$80.00' },
];

const SHEET_RADIUS = 28;
const PAGE_RADIUS = 24;
const GRABBER_WIDTH = 36;
const GRABBER_HEIGHT = 4;
const MARGIN = 20;
// From the sheet's top edge to the first row.
const HEADER = 128;
const ROW_MEDIUM = 68;
const ROW_LARGE = 76;
const ROW_GAP = 10;
const ROW_RADIUS = 18;
// Grabber, title and caption take 108 points; the first row's own gap and this one reach HEADER.
const ROWS_TOP = HEADER - 108 - ROW_GAP;
const ICON = 40;
const BUTTON_HEIGHT = 52;
const FINGER = 40;

// Where the sheet's top edge rests, as a share of the screen's height; the page's scale there.
const MEDIUM_AT = 0.32;
const LARGE_AT = 0.14;
const SCALE_MEDIUM = 0.94;
const SCALE_LARGE = 0.92;
const SCRIM = 0.36;
// The sheet follows a finger 1:1 until the large detent, and a quarter of the way beyond it.
const RUBBER = 0.25;
// Points a second above which a release picks the next detent in its direction; how far ahead the
// release looks otherwise.
const FLING = 600;
const PROJECT = 0.12;
const NEAR = 24;
// Response 0.5 s with damping ratios 0.8 and 0.7: the large detent overshoots a little more.
const MEDIUM_SPRING = { damping: 20, stiffness: 158, mass: 1, reduceMotion: ReduceMotion.Never };
const LARGE_SPRING = { damping: 17.6, stiffness: 158, mass: 1, reduceMotion: ReduceMotion.Never };
const CLOSE_SPRING = { damping: 28, stiffness: 190, mass: 1, overshootClamping: true, reduceMotion: ReduceMotion.Never };
const FADE = 180;
const ENTER_AFTER = 650;
const ENTER_IN = 450;
const ROW_STAGGER = 70;
const ROW_ROOM = 24;
const DRAG_UP = 150;
const DRAG_UP_MS = 700;
const DRAG_DOWN_MS = 900;
const FINGER_IN = 150;
const FINGER_OUT = 220;
const REST_FADE = 350;

const EASE_OUT = Easing.out(Easing.cubic);

function timing(ms: number, easing = EASE_OUT) {
  return { duration: ms, easing, reduceMotion: ReduceMotion.Never };
}

type Detent = 0 | 1 | 2;

/**
 * A page with a button that raises a sheet of transfer methods. The sheet rests at a middle
 * detent and a large one and follows a finger, letting go on a fling or on the nearest detent;
 * the page behind scales and darkens with the sheet's position, and at the large detent the rows
 * grow and a fifth one appears. One value, the sheet's top edge, drives all of it.
 */
export function DetentMethodSheet({
  methods = METHODS,
  pageTitle = 'Select your source of fund',
  pageCaption = 'Pick a method to add money to your account',
  buttonLabel = 'Add money',
  sheetTitle = 'Select transfer method',
  sheetCaption = 'Select your preferred transfer method to add your money into your account',
  autoPlay = false,
  onSelect,
  onDetentChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [detent, setDetent] = useState<Detent>(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [pressedRow, setPressedRow] = useState<number | null>(null);
  const [buttonPressed, setButtonPressed] = useState(false);

  // The title and every row rise in turn over this long.
  const enterMs = ENTER_IN + ROW_STAGGER * methods.length;
  const medium = Math.round(height * MEDIUM_AT);
  const large = Math.max(Math.round(height * LARGE_AT), insets.top + 8);

  // The sheet's top edge, in points from the top of the screen: `height` is closed.
  const top = useSharedValue(height);
  const detentValue = useSharedValue<Detent>(0);
  const enter = useSharedValue(0);
  // The simulated finger of the autoPlay loop.
  const fingerX = useSharedValue(width / 2);
  const fingerY = useSharedValue(0);
  const fingerOn = useSharedValue(0);
  const fingerDown = useSharedValue(0);
  const fingerFollows = useSharedValue(0);

  /** Springs to a detent (0 closed, 1 medium, 2 large), carrying the finger's speed in. */
  const go = (target: Detent, velocity: number) => {
    'worklet';
    detentValue.set(target);
    const to = target === 2 ? large : target === 1 ? medium : height;
    if (reducedMotion) {
      top.set(withTiming(to, { duration: FADE, reduceMotion: ReduceMotion.Never }));
      return;
    }
    const spring = target === 2 ? LARGE_SPRING : target === 1 ? MEDIUM_SPRING : CLOSE_SPRING;
    top.set(withSpring(to, { ...spring, velocity }));
  };

  /** Lets go of the sheet: a fling picks the next detent that way, a slow release the nearest. */
  const settle = (velocity: number) => {
    'worklet';
    const y = top.get();
    let target: Detent;
    if (Math.abs(velocity) > FLING) {
      if (velocity < 0) target = y > medium + NEAR ? 1 : 2;
      else target = y < medium - NEAR ? 1 : 0;
    } else {
      const ahead = y + velocity * PROJECT;
      const toLarge = Math.abs(ahead - large);
      const toMedium = Math.abs(ahead - medium);
      const toClosed = Math.abs(ahead - height);
      target = toLarge < toMedium && toLarge < toClosed ? 2 : toMedium < toClosed ? 1 : 0;
    }
    go(target, velocity);
  };

  const open = () => {
    if (detentValue.get() !== 0) return;
    enter.set(0);
    top.set(height);
    go(1, 0);
    enter.set(withDelay(reducedMotion ? 0 : ENTER_AFTER, withTiming(1, timing(reducedMotion ? FADE : enterMs, Easing.linear))));
  };

  const close = () => go(0, 0);

  const choose = (index: number) => {
    setSelected(index);
    onSelect?.(index);
  };

  // The rows rise again the next time the sheet opens.
  useEffect(() => {
    if (detent === 0) enter.set(withDelay(250, withTiming(0, timing(FADE))));
  }, [detent, enter]);

  useAnimatedReaction(
    () => detentValue.get(),
    (now, before) => {
      if (now === before) return;
      scheduleOnRN(setDetent, now);
    },
  );

  // The same moves a touch makes, played by the loop with a finger that follows them.
  const touchAt = (x: number, y: number, follows = false) => {
    fingerX.set(x);
    fingerY.set(y);
    fingerFollows.set(follows ? 1 : 0);
    fingerOn.set(withTiming(1, timing(FINGER_IN)));
  };
  const lift = () => {
    fingerDown.set(withTiming(0, timing(120)));
    fingerOn.set(withTiming(0, timing(FINGER_OUT)));
  };
  const rowCenter = (index: number) => large + HEADER + index * (ROW_LARGE + ROW_GAP) + ROW_LARGE / 2;

  const rest = () => {
    for (const value of [top, enter, fingerOn, fingerDown]) cancelAnimation(value);
    detentValue.set(0);
    top.set(withTiming(height, timing(REST_FADE)));
    enter.set(withTiming(0, timing(FADE)));
    fingerOn.set(withTiming(0, timing(FADE)));
    setSelected(null);
    setPressedRow(null);
    setButtonPressed(false);
  };

  const touched = useAutoPlay(
    autoPlay,
    LOOP,
    [
      [TOUCH_BUTTON_AT, () => touchAt(width / 2, height - insets.bottom - 16 - BUTTON_HEIGHT / 2)],
      [
        PRESS_BUTTON_AT,
        () => {
          setButtonPressed(true);
          fingerDown.set(withTiming(1, timing(100)));
        },
      ],
      [
        OPEN_AT,
        () => {
          setButtonPressed(false);
          lift();
          open();
        },
      ],
      [TOUCH_GRABBER_AT, () => touchAt(width / 2, medium + 14, true)],
      [
        DRAG_UP_AT,
        () => {
          fingerDown.set(withTiming(1, timing(100)));
          cancelAnimation(top);
          top.set(withTiming(medium - DRAG_UP, timing(DRAG_UP_MS, Easing.inOut(Easing.quad))));
        },
      ],
      [
        RELEASE_UP_AT,
        () => {
          settle(-700);
          lift();
        },
      ],
      [TOUCH_ROW_AT, () => touchAt(width * 0.72, rowCenter(1))],
      [
        PRESS_ROW_AT,
        () => {
          setPressedRow(1);
          fingerDown.set(withTiming(1, timing(100)));
        },
      ],
      [
        SELECT_AT,
        () => {
          setPressedRow(null);
          choose(1);
          lift();
          go(1, 0);
        },
      ],
      [TOUCH_GRABBER_AGAIN_AT, () => touchAt(width / 2, medium + 14, true)],
      [
        DRAG_DOWN_AT,
        () => {
          fingerDown.set(withTiming(1, timing(100)));
          cancelAnimation(top);
          top.set(withTiming(height, timing(DRAG_DOWN_MS, Easing.in(Easing.quad))));
        },
      ],
      [
        RELEASE_DOWN_AT,
        () => {
          settle(900);
          lift();
        },
      ],
      [CLEAR_AT, () => setSelected(null)],
    ],
    rest,
  );

  const pan = Gesture.Pan()
    .onBegin(() => {
      cancelAnimation(top);
    })
    .onUpdate((event) => {
      const start = detentValue.get() === 2 ? large : detentValue.get() === 1 ? medium : height;
      const next = start + event.translationY;
      top.set(next < large ? large - (large - next) * RUBBER : next);
    })
    .onEnd((event) => {
      settle(event.velocityY);
    });

  const pageStyle = useAnimatedStyle(() => {
    const y = top.get();
    return {
      transform: [{ scale: interpolate(y, [large, medium, height], [SCALE_LARGE, SCALE_MEDIUM, 1], 'clamp') }],
      borderTopLeftRadius: interpolate(y, [medium, height], [PAGE_RADIUS, 0], 'clamp'),
      borderTopRightRadius: interpolate(y, [medium, height], [PAGE_RADIUS, 0], 'clamp'),
    };
  });
  const scrimStyle = useAnimatedStyle(() => ({ opacity: interpolate(top.get(), [medium, height], [SCRIM, 0], 'clamp') }));
  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: top.get() - large }] }));
  const headStyle = useAnimatedStyle(() => {
    const t = Math.min(1, (enter.get() * enterMs) / ENTER_IN);
    return { opacity: t, transform: [{ translateY: 8 * (1 - t) }] };
  });
  const fingerStyle = useAnimatedStyle(() => {
    const y = fingerFollows.get() === 1 ? top.get() + 14 : fingerY.get();
    return {
      opacity: fingerOn.get(),
      transform: [
        { translateX: fingerX.get() - FINGER / 2 },
        { translateY: y - FINGER / 2 },
        { scale: 1 - 0.14 * fingerDown.get() },
      ],
    };
  });

  const bottom = Math.max(insets.bottom, 16);

  return (
    <GestureHandlerRootView style={styles.root} onTouchStart={touched}>
      <StatusBar style={detent === 0 ? 'dark' : 'light'} />

      <Animated.View style={[styles.page, { paddingTop: insets.top + 8 }, pageStyle]}>
        <View style={styles.topBar}>
          <Ionicons name="chevron-back" size={26} color={INK} />
          <View style={styles.balance}>
            <Text style={styles.balanceLabel}>Balance</Text>
            <Text style={styles.balanceValue}>$1,284.50</Text>
          </View>
        </View>
        <Text style={styles.pageTitle}>{pageTitle}</Text>
        <Text style={styles.pageCaption}>{pageCaption}</Text>
        <View style={styles.sectionRow}>
          <Text style={styles.section}>Bank accounts</Text>
          <Pressable onPress={open} style={styles.add} accessibilityRole="button" accessibilityLabel="Add a bank account">
            <Text style={styles.addLabel}>Add</Text>
            <Ionicons name="add" size={16} color={INK} />
          </Pressable>
        </View>
        {ACCOUNTS.map((account) => (
          <View key={account.name} style={styles.account}>
            <View style={[styles.logo, { backgroundColor: account.colors[1] }]}>
              <View style={[styles.logoInner, { backgroundColor: account.colors[0] }]} />
              <Text style={styles.logoLetter}>{account.letter}</Text>
            </View>
            <View style={styles.accountText}>
              <View style={styles.accountNameRow}>
                <Text style={styles.accountName}>{account.name}</Text>
                {account.primary && (
                  <View style={styles.primary}>
                    <Text style={styles.primaryLabel}>Primary</Text>
                  </View>
                )}
              </View>
              <Text style={styles.accountDigits}>{`Checking  ••••  ${account.digits}`}</Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={FAINT} />
          </View>
        ))}
        <View style={[styles.sectionRow, styles.sectionSpaced]}>
          <Text style={styles.section}>Recent top-ups</Text>
          <Text style={styles.seeAll}>See all</Text>
        </View>
        {RECENT.map((item) => (
          <View key={item.title} style={styles.account}>
            <View style={[styles.logo, styles.recentIcon]}>
              <Ionicons name="arrow-down" size={18} color={GREEN} />
            </View>
            <View style={styles.accountText}>
              <Text style={styles.accountName}>{item.title}</Text>
              <Text style={styles.accountDigits}>{item.when}</Text>
            </View>
            <Text style={styles.amount}>{item.amount}</Text>
          </View>
        ))}
        <View style={styles.tip}>
          <View style={styles.tipIcon}>
            <Ionicons name="flash" size={18} color={BOLT} />
          </View>
          <View style={styles.accountText}>
            <Text style={styles.tipTitle}>Instant top-ups are on</Text>
            <Text style={styles.tipText}>Free up to $500 a month with a debit card.</Text>
          </View>
        </View>
        <View style={[styles.buttonSlot, { bottom }]}>
          <Pressable onPress={open} accessibilityRole="button" accessibilityLabel={buttonLabel}>
            {({ pressed }) => (
              <View style={[styles.button, { backgroundColor: pressed || buttonPressed ? '#333333' : INK }]}>
                <Text style={styles.buttonLabel}>{buttonLabel}</Text>
              </View>
            )}
          </Pressable>
        </View>
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} pointerEvents={detent === 0 ? 'none' : 'auto'}>
        <Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityLabel="Close" />
      </Animated.View>

      <GestureDetector gesture={pan}>
        <Animated.View
          style={[styles.sheet, { top: large, height: height - large + 200 }, sheetStyle]}
          pointerEvents={detent === 0 ? 'none' : 'auto'}
        >
          <View style={styles.grabber} />
          <Animated.View style={headStyle}>
            <Text style={styles.sheetTitle}>{sheetTitle}</Text>
            <Text style={styles.sheetCaption}>{sheetCaption}</Text>
          </Animated.View>
          <View style={styles.rows}>
            {methods.map((method, index) => (
              <MethodRow
                key={method.title}
                method={method}
                index={index}
                last={index === methods.length - 1}
                top={top}
                enter={enter}
                medium={medium}
                large={large}
                enterMs={enterMs}
                selected={selected === index}
                pressed={pressedRow === index}
                onPress={() => choose(index)}
              />
            ))}
          </View>
        </Animated.View>
      </GestureDetector>

      <Animated.View style={[styles.finger, fingerStyle]} pointerEvents="none" />
    </GestureHandlerRootView>
  );
}

type SharedNumber = { get(): number };

function MethodRow({
  method,
  index,
  last,
  top,
  enter,
  medium,
  large,
  enterMs,
  selected,
  pressed,
  onPress,
}: {
  method: Method;
  index: number;
  last: boolean;
  top: SharedNumber;
  enter: SharedNumber;
  medium: number;
  large: number;
  enterMs: number;
  selected: boolean;
  pressed: boolean;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    // 0 at the middle detent, 1 at the large one.
    const g = interpolate(top.get(), [large, medium], [1, 0], 'clamp');
    const t = Math.min(1, Math.max(0, (enter.get() * enterMs - (index + 1) * ROW_STAGGER) / ENTER_IN));
    // The last row exists only at the large detent.
    const extra = last ? Math.min(1, Math.max(0, (g - 0.5) / 0.5)) : 1;
    return {
      height: (ROW_MEDIUM + (ROW_LARGE - ROW_MEDIUM) * g) * extra,
      marginTop: ROW_GAP * extra,
      opacity: t * extra,
      transform: [{ translateY: (1 - t) * ROW_ROOM }],
    };
  });
  const subtitleStyle = useAnimatedStyle(() => ({ opacity: 0.72 + 0.28 * interpolate(top.get(), [large, medium], [1, 0], 'clamp') }));

  return (
    <Animated.View style={[styles.rowSlot, style]}>
      <Pressable style={styles.rowFill} onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
        <Animated.View
          style={[
            styles.row,
            { borderColor: selected ? INK : LINE, borderWidth: selected ? 1.5 : 1, transform: [{ scale: pressed ? 0.97 : 1 }], transitionProperty: ['borderColor', 'transform'], transitionDuration: [150, 120] },
          ]}
        >
          <Animated.View style={[styles.icon, { backgroundColor: selected ? INK : method.icon === 'diamond' ? TEAL_WELL : ICON_WELL, transitionProperty: 'backgroundColor', transitionDuration: 150 }]}>
            <Ionicons name={selected ? 'checkmark' : method.icon} size={19} color={selected ? '#ffffff' : method.icon === 'diamond' ? TEAL : INK} />
          </Animated.View>
          <View style={styles.rowText}>
            <Text style={styles.rowTitle} numberOfLines={1}>
              {method.title}
            </Text>
            <Animated.View style={[styles.subtitleRow, subtitleStyle]}>
              {method.instant && <Ionicons name="flash" size={13} color={BOLT} />}
              <Text style={styles.rowSubtitle} numberOfLines={1}>
                {method.subtitle}
              </Text>
            </Animated.View>
          </View>
          <View style={styles.badge}>
            <Text style={styles.badgeLabel}>{method.badge}</Text>
            {method.enabled && <Ionicons name="checkmark-circle" size={16} color={GREEN} />}
          </View>
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000000' },
  page: { flex: 1, backgroundColor: PAGE, paddingHorizontal: MARGIN, borderCurve: 'continuous', overflow: 'hidden' },
  topBar: { marginTop: 4, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginLeft: -6 },
  balance: { alignItems: 'flex-end' },
  balanceLabel: { color: FAINT, fontSize: 12, lineHeight: 16, fontWeight: '500' },
  balanceValue: { color: INK, fontSize: 16, lineHeight: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  pageTitle: { marginTop: 16, color: INK, fontSize: 28, lineHeight: 34, fontWeight: '700', letterSpacing: -0.7 },
  pageCaption: { marginTop: 6, color: MUTE, fontSize: 16, lineHeight: 22 },
  sectionRow: { marginTop: 28, marginBottom: 4, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionSpaced: { marginTop: 24 },
  section: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.2 },
  seeAll: { color: MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  add: { height: 32, paddingLeft: 14, paddingRight: 10, borderRadius: 16, borderWidth: 1, borderColor: PILL_LINE, flexDirection: 'row', alignItems: 'center', gap: 2 },
  addLabel: { color: INK, fontSize: 14, lineHeight: 18, fontWeight: '600' },
  account: { height: 68, flexDirection: 'row', alignItems: 'center', gap: 14 },
  logo: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  logoInner: { position: 'absolute', left: -6, top: -10, width: 44, height: 44, borderRadius: 22 },
  logoLetter: { color: '#ffffff', fontSize: 18, lineHeight: 22, fontWeight: '800' },
  recentIcon: { backgroundColor: '#e8f7ee' },
  accountText: { flex: 1, gap: 2 },
  accountNameRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  accountName: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  primary: { height: 20, paddingHorizontal: 7, borderRadius: 10, backgroundColor: '#e8f7ee', justifyContent: 'center' },
  primaryLabel: { color: GREEN, fontSize: 11, lineHeight: 14, fontWeight: '700' },
  accountDigits: { color: FAINT, fontSize: 14, lineHeight: 19, fontVariant: ['tabular-nums'] },
  amount: { color: GREEN, fontSize: 16, lineHeight: 21, fontWeight: '700', fontVariant: ['tabular-nums'] },
  tip: {
    marginTop: 20,
    padding: 16,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: '#fff8e8',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  tipIcon: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#ffeec2', alignItems: 'center', justifyContent: 'center' },
  tipTitle: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '600' },
  tipText: { color: MUTE, fontSize: 14, lineHeight: 19 },
  buttonSlot: { position: 'absolute', left: MARGIN, right: MARGIN },
  button: {
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 20, color: 'rgba(17,17,19,0.22)' }],
  },
  buttonLabel: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '600' },
  scrim: { backgroundColor: '#000000' },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: PAGE,
    paddingHorizontal: MARGIN,
    boxShadow: [{ offsetX: 0, offsetY: -8, blurRadius: 32, color: 'rgba(0,0,0,0.14)' }],
  },
  grabber: { alignSelf: 'center', marginTop: 8, width: GRABBER_WIDTH, height: GRABBER_HEIGHT, borderRadius: GRABBER_HEIGHT / 2, backgroundColor: GRABBER },
  sheetTitle: { marginTop: 22, color: INK, fontSize: 21, lineHeight: 26, fontWeight: '700', letterSpacing: -0.4 },
  sheetCaption: { marginTop: 4, color: MUTE, fontSize: 15, lineHeight: 20, height: 40 },
  rows: { marginTop: ROWS_TOP },
  rowSlot: { overflow: 'visible' },
  rowFill: { flex: 1 },
  row: {
    flex: 1,
    borderRadius: ROW_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: PAGE,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    boxShadow: [
      { offsetX: 0, offsetY: 6, blurRadius: 16, color: 'rgba(17,17,19,0.05)' },
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(17,17,19,0.04)' },
    ],
  },
  icon: { width: ICON, height: ICON, borderRadius: ICON / 2, alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  subtitleRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  rowSubtitle: { color: MUTE, fontSize: 14, lineHeight: 19 },
  badge: { height: 30, paddingHorizontal: 12, borderRadius: 15, borderWidth: 1, borderColor: PILL_LINE, backgroundColor: PAGE, flexDirection: 'row', alignItems: 'center', gap: 5 },
  badgeLabel: { color: INK, fontSize: 13, lineHeight: 17, fontWeight: '600' },
  finger: { position: 'absolute', left: 0, top: 0, width: FINGER, height: FINGER, borderRadius: FINGER / 2, backgroundColor: '#ffffff', boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 10, color: 'rgba(0,0,0,0.28)' }] },
});
