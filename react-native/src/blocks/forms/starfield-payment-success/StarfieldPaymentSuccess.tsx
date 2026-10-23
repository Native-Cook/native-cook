import { Canvas, Circle, Group, LinearGradient as Fade, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as Dot, Ellipse, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BAR_AFTER,
  BAR_IN,
  CHECK_AFTER,
  DISSOLVE_AT,
  DISSOLVE_FOR,
  DROP,
  FIRST_KEY_AT,
  KEY_EVERY,
  KEY_FOR,
  LEAVE_AFTER,
  LOOP,
  PRESS_AT,
  PRESS_FOR,
  RESULT_AFTER,
  RESULT_IN,
  RETURN,
  Space,
  TEXT_AT,
  TEXT_IN,
  USER_HOLD,
  WAIT_OUT,
  backOut,
  clamp01,
  smooth,
} from './Space';

type Props = {
  /** Types an amount, confirms it and comes back to the empty form by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** The digits autoPlay types. */
  autoPlayAmount?: string;
  currency?: string;
  /** Written before an amount of `currency`. */
  symbol?: string;
  /** The second currency, shown in the chip: its sign, and what one unit of it costs in `currency`. */
  quoteSymbol?: string;
  rate?: number;
  /** The most digits the pad takes. */
  maxDigits?: number;
  cardNumber?: string;
  cardName?: string;
  /** Seconds from Confirm to the moment the paper plane leaves and the check lands. */
  processingDuration?: number;
  /** The colour of the caret and of the two wide buttons. */
  accent?: string;
  title?: string;
  payingFromLabel?: string;
  confirmLabel?: string;
  waitingTitle?: string;
  waitingHint?: string;
  doneLabel?: string;
  reviewLabel?: string;
  /** Called with the amount when Confirm is released and the wait starts: the real request goes here. */
  onConfirm?: (amount: number) => void;
  /** Called when the check lands. */
  onSuccess?: (amount: number) => void;
  /** Review and the share button on the result; both go back to the empty form. */
  onReview?: (amount: number) => void;
  onShare?: (amount: number) => void;
  onCurrencyPress?: () => void;
  onCardPress?: () => void;
  onClose?: () => void;
};

const BLACK = '#000000';
// The top of the form is tinted, and light comes up from under its bottom edge.
const TINT = '#1a0c0c';
const EMBER = [255, 122, 26];
// The light is an ellipse centred under the bottom edge: its centre and its height as fractions of
// the screen's height, its half-width as a fraction of the screen's width.
const EMBER_Y = 1.09;
const EMBER_WIDTH = 1.25;
const EMBER_HEIGHT = 0.3;
const TILE = '#1c1c1e';
const LABEL = '#77777c';
const PLACEHOLDER = '#6b6b70';
const UNIT = '#8a8a8e';
const CARD = ['#2b3139', '#474a50'] as const;
const CARD_NAME = '#a4a7ad';
const SHARE = '#2a2a2e';
const PRESSED = 0.72;

const CLOSE = 38;
const CLOSE_INSET = 36;
const TOP_GAP = 15;
const AMOUNT = 54;
const AMOUNT_ROW = 66;
const CARET = 60;
const CHIP = 32;
const CARD_HEIGHT = 72;
const CARD_MARGIN = 24;
const KEY = 64;
const KEY_FLASH = 72;
const PAD_MARGIN = 11;
// From the card to the pad's first row, and the height of a row: at rest, and closed up for Confirm.
const PAD_GAP = 59;
const PAD_GAP_CLOSED = 25;
const ROW_MIN = 50;
const BUTTON = 56;
const CONFIRM_MARGIN = 32;
const BAR_MARGIN = 28;
const BAR_GAP = 10;
// The bottom buttons sit this far above the safe area; the pad ends this far above them, or above it.
const BUTTON_LIFT = 2;
const PAD_END = 8;

// Confirm rises this long after the last key, so the pad does not move under a finger that types.
const CONFIRM_AFTER = 0.4;
const CONFIRM_SPRING = { damping: 17, stiffness: 190, mass: 1 };
const CONFIRM_OUT = 220;
const FLASH_ON = 40;
const FLASH_OFF = 350;
// How far the form's halves travel as it comes apart, and how far off they come back from.
const FLOAT = 140;
const SINK = 100;
const BACK_TRAVEL = 28;
const ROW_STAGGER = 0.06;
const TEXT_RISE = 26;
const RESULT_RISE = 14;
const RESULT_STAGGER = 0.08;
// The clock stops here if the result is never left.
const CLOCK_END = 3600;

const KEYS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['', '0', 'back'],
];

// Icons, drawn in a 24 × 24 box.
const ICON_CLOSE = 'M18 6L6 18M6 6l12 12';
const ICON_SELECT = 'M7 15l5 5 5-5M7 9l5-5 5 5';
const ICON_SWAP = 'M17 1l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3';
const ICON_CHEVRON = 'M9 18l6-6-6-6';
const ICON_DELETE = 'M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zM18 9l-6 6M12 9l6 6';
const ICON_ARROW = 'M5 12h14M12 5l7 7-7 7';
const ICON_SHARE = 'M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13';

// The light under the form fades from its centre to its edge as cos², so it has no rim.
const FALLOFF_AT = [0, 0.2, 0.4, 0.6, 0.8, 1];
const FALLOFF = [1, 0.905, 0.655, 0.345, 0.095, 0];
const EMBER_COLORS = FALLOFF.map((f) => `rgba(${EMBER[0]},${EMBER[1]},${EMBER[2]},${0.9 * f})`);

function easeIn(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u;
}

function money(value: number) {
  const cents = Math.round(value * 100);
  const whole = String(Math.floor(cents / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return [whole, String(cents % 100).padStart(2, '0')];
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Icon({ path, size, color, stroke = 2 }: { path: string; size: number; color: string; stroke?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={path} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** The card's picture: a ringed planet on a white tile. */
function CardArt() {
  return (
    <View style={styles.cardArt}>
      <Svg width={34} height={26} viewBox="0 0 34 26">
        <Dot cx={17} cy={13} r={8} fill="#ff8a1e" />
        <Ellipse cx={17} cy={13} rx={14.5} ry={4.2} stroke="#2a2c30" strokeWidth={1.8} fill="none" transform="rotate(-20 17 13)" />
        <Path d="M10.2 9.2A8 8 0 0 1 24.9 11.6" stroke="#ff8a1e" strokeWidth={3.4} fill="none" />
      </Svg>
    </View>
  );
}

function Chip({ label }: { label: string }) {
  return (
    <View style={styles.chip}>
      <Icon path={ICON_SWAP} size={13} color={UNIT} />
      <Text style={styles.chipLabel} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

type KeyProps = { label: string; held: boolean; onPress: () => void };

function Key({ label, held, onPress }: KeyProps) {
  if (label === '') return <View style={styles.key} />;
  return (
    <Pressable onPress={onPress} style={styles.key} accessibilityRole="button" accessibilityLabel={label === 'back' ? 'Delete' : label}>
      {({ pressed }) => (
        <>
          {/* On at once, off slowly: a soft round light under the key. */}
          <Animated.View
            style={[
              styles.flash,
              { opacity: pressed || held ? 1 : 0, transitionProperty: 'opacity', transitionDuration: pressed || held ? FLASH_ON : FLASH_OFF },
            ]}
          />
          {label === 'back' ? <Icon path={ICON_DELETE} size={24} color="#ffffff" stroke={1.8} /> : <Text style={styles.digit}>{label}</Text>}
        </>
      )}
    </Pressable>
  );
}

type RowProps = {
  index: number;
  keys: string[];
  /** The first row's top and a row's height: at rest, and closed up for Confirm. */
  top: number;
  row: number;
  topClosed: number;
  rowClosed: number;
  confirm: SharedValue<number>;
  elapsed: SharedValue<number>;
  leaving: SharedValue<number>;
  still: boolean;
  held: string | null;
  onKey: (key: string) => void;
};

function Row({ index, keys, top, row, topClosed, rowClosed, confirm, elapsed, leaving, still, held, onKey }: RowProps) {
  const style = useAnimatedStyle(() => {
    const c = confirm.get();
    const height = row + (rowClosed - row) * c;
    const y = top + (topClosed - top) * c + height * (index + 0.5) - KEY / 2;
    const e = elapsed.get();
    const b = leaving.get();
    if (e < 0) return { opacity: 1, transform: [{ translateY: y }] };
    if (b > 0) {
      // Back from below, one row after the other.
      const p = smooth(b, 0.3 + index * ROW_STAGGER, 0.8 + index * ROW_STAGGER);
      return { opacity: p, transform: [{ translateY: y + (still ? 0 : BACK_TRAVEL * (1 - p)) }] };
    }
    const o = clamp01((e - DISSOLVE_AT) / DISSOLVE_FOR);
    return { opacity: 1 - smooth(o, 0, 0.85), transform: [{ translateY: y + (still ? 0 : SINK * o * o) }] };
  });
  return (
    <Animated.View style={[styles.row, style]}>
      {keys.map((key, column) => (
        <Key key={column} label={key} held={held === key} onPress={() => onKey(key)} />
      ))}
    </Animated.View>
  );
}

/**
 * A payment form on black: an amount typed on a number pad, the card it is paid from, and a
 * Confirm button that rises once there is an amount. Confirm takes the form apart and leaves a
 * field of stars flying past, with a paper plane on a wobbling light while the payment waits; then
 * a ring bursts, a green check lands, swells and turns over, and what comes round is a spinning
 * gold coin over the amount sent. Nothing is paid here: `onConfirm` is where the real request goes.
 */
export function StarfieldPaymentSuccess({
  autoPlay = false,
  autoPlayAmount = '100',
  currency = 'USD',
  symbol = '$',
  quoteSymbol = '€',
  rate = 1.087,
  maxDigits = 6,
  cardNumber = '**** 3256',
  cardName = 'ORBIT DEBIT',
  processingDuration = 2.7,
  accent = '#5b2eff',
  title = 'Enter Amount',
  payingFromLabel = 'Paying from',
  confirmLabel = 'Confirm',
  waitingTitle = 'Starting your transaction',
  waitingHint = 'Please hold on..',
  doneLabel = 'Successfully sent',
  reviewLabel = 'Review',
  onConfirm,
  onSuccess,
  onReview,
  onShare,
  onCurrencyPress,
  onCardPress,
  onClose,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // Where the card ends, which is where the pad's space starts.
  const [formEnd, setFormEnd] = useState(insets.top + 392);
  // The amount as typed: digits only, empty until a key is pressed.
  const [digits, setDigits] = useState('');
  const [confirmUp, setConfirmUp] = useState(false);
  // Where a payment is: the form, the wait from Confirm to the check, the result, the way back.
  const [phase, setPhase] = useState<'form' | 'waiting' | 'done' | 'leaving'>('form');
  // What the payment in progress sends.
  const [sent, setSent] = useState(0);
  // The key and the button the autoPlay loop holds down.
  const [autoKey, setAutoKey] = useState<string | null>(null);
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Seconds since Confirm was released; negative while the form is idle.
  const elapsed = useSharedValue(-1);
  // 0 to 1 over RETURN while the result leaves and the form comes back.
  const leaving = useSharedValue(0);
  // How far Confirm has risen: a spring, so it and the pad's rows overshoot a little.
  const confirm = useSharedValue(0);

  const busy = phase !== 'form';
  const arrived = phase === 'done';
  const amount = Number(digits) || 0;
  const duration = processingDuration;
  const check = duration + CHECK_AFTER;
  const bottom = Math.max(insets.bottom, 12) + BUTTON_LIFT;
  // Off the bottom edge.
  const rise = BUTTON + bottom + 16;

  const padEnd = size.height - Math.max(insets.bottom, 12) - PAD_END;
  const row = Math.max(ROW_MIN, (padEnd - formEnd - PAD_GAP) / KEYS.length);
  const rowClosed = Math.max(ROW_MIN - 6, (padEnd - BUTTON - formEnd - PAD_GAP_CLOSED) / KEYS.length);

  const confirmTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // The payment in progress was started by the autoPlay loop, which is silent.
  const auto = useRef(false);
  // When the check landed, on the clock of Date.now().
  const arrivedAt = useRef(0);

  const showConfirm = (up: boolean) => {
    setConfirmUp(up);
    confirm.set(up ? withSpring(1, CONFIRM_SPRING) : withTiming(0, { duration: CONFIRM_OUT }));
  };

  /** Puts a key's digit into the amount, and lets Confirm follow once the typing pauses. */
  const type = (key: string) => {
    const next = key === 'back' ? digits.slice(0, -1) : digits === '' && key === '0' ? '' : digits.length < maxDigits ? digits + key : digits;
    setDigits(next);
    if (confirmTimer.current) clearTimeout(confirmTimer.current);
    if (next === '') showConfirm(false);
    else if (!confirmUp) confirmTimer.current = setTimeout(() => showConfirm(true), CONFIRM_AFTER * 1000);
  };

  const clear = () => {
    if (confirmTimer.current) clearTimeout(confirmTimer.current);
    setDigits('');
    showConfirm(false);
  };

  const finish = () => setPhase('form');

  const arrive = () => {
    setPhase('done');
    arrivedAt.current = Date.now();
    if (!auto.current && process.env.EXPO_OS !== 'web') {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    }
    onSuccess?.(sent);
  };

  const start = (byItself: boolean) => {
    if (busy || amount <= 0) return;
    auto.current = byItself;
    if (!byItself && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    setSent(amount);
    setPhase('waiting');
    onConfirm?.(amount);
    leaving.set(0);
    elapsed.set(0);
    elapsed.set(withTiming(CLOCK_END, { duration: CLOCK_END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  /** The result leaves and the empty form comes back. */
  const leave = () => {
    if (phase !== 'done') return;
    setPhase('leaving');
    clear();
    leaving.set(
      withTiming(1, { duration: RETURN * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        // Both at once, on the same frame: the form is whole again and the clock is idle.
        elapsed.set(-1);
        leaving.set(0);
        scheduleOnRN(finish);
      }),
    );
  };

  useAnimatedReaction(
    () => elapsed.get() >= check,
    (now, old) => {
      if (now && old === false) scheduleOnRN(arrive);
    },
  );

  useEffect(
    () => () => {
      cancelAnimation(elapsed);
      cancelAnimation(leaving);
      if (confirmTimer.current) clearTimeout(confirmTimer.current);
    },
    [elapsed, leaving],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ type, start, leave, clear, busy });
  useEffect(() => {
    latest.current = { type, start, leave, clear, busy };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, act: () => void) => timers.push(setTimeout(act, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, act: () => void) => after(origin + seconds * 1000 - Date.now(), act);
      autoPlayAmount.split('').forEach((key, index) => {
        at(FIRST_KEY_AT + index * KEY_EVERY, () => setAutoKey(key));
        at(FIRST_KEY_AT + index * KEY_EVERY + KEY_FOR, () => {
          setAutoKey(null);
          latest.current.type(key);
        });
      });
      at(PRESS_AT, () => setAutoPressed(true));
      at(PRESS_AT + PRESS_FOR, () => {
        setAutoPressed(false);
        latest.current.start(true);
      });
      at(PRESS_AT + PRESS_FOR + check + LEAVE_AFTER, () => latest.current.leave());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      // A payment left on screen is seen through to its result, which leaves after its usual time.
      if (latest.current.busy) {
        if (Date.now() - arrivedAt.current >= LEAVE_AFTER * 1000) latest.current.leave();
        return after(100, rest);
      }
      latest.current.clear();
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, autoPlayAmount, check, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoKey(null);
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  const backdropStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    const b = leaving.get();
    return { opacity: e < 0 ? 1 : b > 0 ? smooth(b, 0.3, 1) : 1 - smooth(e, DISSOLVE_AT, DISSOLVE_AT + DISSOLVE_FOR) };
  });
  const skyStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(leaving.get(), 0, 0.6) }));
  // The amount floats up and out; what is under it sinks.
  const headStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    const b = leaving.get();
    if (e < 0) return { opacity: 1, transform: [{ translateY: 0 }] };
    if (b > 0) {
      const p = smooth(b, 0.3, 0.8);
      return { opacity: p, transform: [{ translateY: still ? 0 : -BACK_TRAVEL * (1 - p) }] };
    }
    const o = clamp01((e - DISSOLVE_AT) / DISSOLVE_FOR);
    return { opacity: 1 - smooth(o, 0, 0.8), transform: [{ translateY: still ? 0 : -FLOAT * o * o }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    const b = leaving.get();
    if (e < 0) return { opacity: 1, transform: [{ translateY: 0 }] };
    if (b > 0) {
      const p = smooth(b, 0.3, 0.8);
      return { opacity: p, transform: [{ translateY: still ? 0 : BACK_TRAVEL * (1 - p) }] };
    }
    const o = clamp01((e - DISSOLVE_AT) / DISSOLVE_FOR);
    return { opacity: 1 - smooth(o, 0, 0.5), transform: [{ translateY: still ? 0 : SINK * o * o }] };
  });
  const confirmStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { transform: [{ translateY: rise * (1 - confirm.get()) + (e < 0 ? 0 : rise * easeIn(e / DROP)) }] };
  });
  const waitingStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    const p = smooth(e, TEXT_AT, TEXT_AT + TEXT_IN);
    return { opacity: p * (1 - smooth(e, duration, duration + WAIT_OUT)), transform: [{ translateY: still ? 0 : TEXT_RISE * (1 - p) }] };
  });
  const doneStyle = useAnimatedStyle(() => {
    const p = smooth(elapsed.get(), check + RESULT_AFTER, check + RESULT_AFTER + RESULT_IN);
    return { opacity: p * (1 - smooth(leaving.get(), 0, 0.5)), transform: [{ translateY: still ? 0 : RESULT_RISE * (1 - p) }] };
  });
  const sumStyle = useAnimatedStyle(() => {
    const from = check + RESULT_AFTER + RESULT_STAGGER;
    const p = smooth(elapsed.get(), from, from + RESULT_IN);
    return { opacity: p * (1 - smooth(leaving.get(), 0, 0.5)), transform: [{ translateY: still ? 0 : RESULT_RISE * (1 - p) }] };
  });
  const noteStyle = useAnimatedStyle(() => {
    const from = check + RESULT_AFTER + RESULT_STAGGER * 2;
    const p = smooth(elapsed.get(), from, from + RESULT_IN);
    return { opacity: p * (1 - smooth(leaving.get(), 0, 0.5)), transform: [{ translateY: still ? 0 : RESULT_RISE * (1 - p) }] };
  });
  // Review slides up from the bottom edge, the round button just behind it; both drop as the result leaves.
  const reviewStyle = useAnimatedStyle(() => {
    const p = backOut((elapsed.get() - check - BAR_AFTER) / BAR_IN);
    return { transform: [{ translateY: rise * (1 - p) + rise * easeIn(leaving.get() / 0.5) }] };
  });
  const shareStyle = useAnimatedStyle(() => {
    const p = backOut((elapsed.get() - check - BAR_AFTER - 0.07) / BAR_IN);
    return { transform: [{ translateY: rise * (1 - p) + rise * easeIn(leaving.get() / 0.5) }] };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const [sentWhole, sentCents] = money(sent);
  const quote = money(amount / rate).join('.');
  const sentQuote = money(sent / rate).join('.');
  const chipTail = `(${quoteSymbol}1 = ${symbol}${rate})`;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />

      <Animated.View pointerEvents="none" style={[styles.fill, backdropStyle]}>
        <Canvas style={styles.fill}>
          <Rect x={0} y={0} width={size.width} height={size.height * 0.36}>
            <Fade start={vec(0, 0)} end={vec(0, size.height * 0.36)} colors={[TINT, BLACK]} />
          </Rect>
          <Group transform={[{ translateX: size.width / 2 }, { translateY: size.height * EMBER_Y }, { scaleX: size.width * EMBER_WIDTH }, { scaleY: size.height * EMBER_HEIGHT }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={EMBER_COLORS} positions={FALLOFF_AT} />
            </Circle>
          </Group>
        </Canvas>
      </Animated.View>

      {busy && (
        <Animated.View pointerEvents="none" style={[styles.fill, skyStyle]}>
          <Space elapsed={elapsed} duration={duration} still={still} width={size.width} height={size.height} />
        </Animated.View>
      )}

      <View
        style={styles.fill}
        pointerEvents={busy ? 'none' : 'auto'}
        accessibilityElementsHidden={busy}
        importantForAccessibility={busy ? 'no-hide-descendants' : 'auto'}
      >
        <View
          style={{ paddingTop: insets.top + TOP_GAP + CLOSE }}
          onLayout={(event) => setFormEnd(event.nativeEvent.layout.height)}
        >
          <Animated.View style={[styles.head, headStyle]}>
            <Text style={styles.title} numberOfLines={1}>
              {title}
            </Text>
            <Pressable
              style={styles.amount}
              accessibilityRole="button"
              accessibilityLabel={`${title}: ${amount} ${currency}`}
              onPress={() => {
                touched();
                onCurrencyPress?.();
              }}
            >
              {digits === '' && <Caret color={accent} restart={`empty-${busy}`} />}
              {digits === '' ? (
                <Text style={[styles.figure, styles.placeholder]}>0.00</Text>
              ) : (
                digits.split('').map((digit, index) => (
                  <Animated.Text key={index} style={styles.figure} entering={FadeIn.duration(140)}>
                    {digit}
                  </Animated.Text>
                ))
              )}
              {digits !== '' && <Caret color={accent} restart={digits} />}
              <Text style={[styles.figure, styles.unit, digits === '' && styles.unitApart]}>{currency}</Text>
              <View style={styles.select}>
                <Icon path={ICON_SELECT} size={22} color={UNIT} stroke={1.8} />
              </View>
            </Pressable>
            <Chip label={`${quoteSymbol}${quote} ${chipTail}`} />
          </Animated.View>

          <Animated.View style={cardStyle}>
            <View style={styles.divider} />
            <Text style={[styles.title, styles.payingFrom]} numberOfLines={1}>
              {payingFromLabel}
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${payingFromLabel} ${cardName} ${cardNumber}`}
              onPress={() => {
                touched();
                tick();
                onCardPress?.();
              }}
            >
              {({ pressed }) => (
                <Animated.View style={[styles.cardSlot, { opacity: pressed ? PRESSED : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}>
                  <LinearGradient colors={CARD} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={styles.card}>
                    <CardArt />
                    <View style={styles.cardText}>
                      <Text style={styles.cardNumber} numberOfLines={1}>
                        {cardNumber}
                      </Text>
                      <Text style={styles.cardName} numberOfLines={1}>
                        {cardName}
                      </Text>
                    </View>
                    <Icon path={ICON_CHEVRON} size={20} color={CARD_NAME} />
                  </LinearGradient>
                </Animated.View>
              )}
            </Pressable>
          </Animated.View>
        </View>

        {KEYS.map((keys, index) => (
          <Row
            key={index}
            index={index}
            keys={keys}
            top={formEnd + PAD_GAP}
            row={row}
            topClosed={formEnd + PAD_GAP_CLOSED}
            rowClosed={rowClosed}
            confirm={confirm}
            elapsed={elapsed}
            leaving={leaving}
            still={still}
            held={autoKey}
            onKey={(key) => {
              touched();
              tick();
              type(key);
            }}
          />
        ))}

        <Animated.View style={[styles.confirmSlot, { bottom }, confirmStyle]}>
          <Pressable
            disabled={!confirmUp}
            accessibilityRole="button"
            accessibilityElementsHidden={!confirmUp}
            onPress={() => {
              touched();
              start(false);
            }}
          >
            {({ pressed }) => (
              <Animated.View
                style={[
                  styles.wide,
                  {
                    backgroundColor: accent,
                    opacity: pressed || autoPressed ? PRESSED : 1,
                    transform: [{ scale: pressed || autoPressed ? 0.97 : 1 }],
                    transitionProperty: ['opacity', 'transform'],
                    transitionDuration: 110,
                  },
                ]}
              >
                <Text style={styles.wideLabel} numberOfLines={1}>
                  {confirmLabel}
                </Text>
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </View>

      <Animated.View
        pointerEvents="none"
        accessible={phase === 'waiting'}
        accessibilityLabel={`${waitingTitle}. ${waitingHint}`}
        style={[styles.words, { top: size.height * 0.514 - 15 }, waitingStyle]}
      >
        <Text style={styles.waitingTitle} numberOfLines={1}>
          {waitingTitle}
        </Text>
        <Text style={styles.waitingHint} numberOfLines={1}>
          {waitingHint}
        </Text>
      </Animated.View>

      <View
        pointerEvents="none"
        accessible={arrived}
        accessibilityLabel={`${doneLabel}: ${symbol}${sentWhole}.${sentCents}`}
        style={[styles.words, { top: size.height * 0.517 - 14 }]}
      >
        <Animated.Text style={[styles.done, doneStyle]} numberOfLines={1}>
          {doneLabel}
        </Animated.Text>
        <Animated.View style={[styles.sum, sumStyle]}>
          <Text style={styles.sumSign}>−</Text>
          <Text style={styles.sumWhole}>
            {symbol}
            {sentWhole}
          </Text>
          <Text style={styles.sumCents}>.{sentCents}</Text>
        </Animated.View>
        <Animated.View style={noteStyle}>
          <Chip label={`${quoteSymbol}${sentQuote} ${chipTail}`} />
        </Animated.View>
      </View>

      <View style={[styles.bar, { bottom }]} pointerEvents={arrived ? 'box-none' : 'none'}>
        <Animated.View style={[styles.review, reviewStyle]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={reviewLabel}
            onPress={() => {
              touched();
              tick();
              onReview?.(sent);
              leave();
            }}
          >
            {({ pressed }) => (
              <Animated.View
                style={[styles.wide, { backgroundColor: accent, opacity: pressed ? PRESSED : 1, transitionProperty: 'opacity', transitionDuration: 110 }]}
              >
                <Text style={styles.wideLabel} numberOfLines={1}>
                  {reviewLabel}
                </Text>
                <Icon path={ICON_ARROW} size={17} color="#ffffff" />
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
        <Animated.View style={shareStyle}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Share"
            onPress={() => {
              touched();
              tick();
              onShare?.(sent);
              leave();
            }}
          >
            {({ pressed }) => (
              <Animated.View style={[styles.share, { opacity: pressed ? PRESSED : 1, transitionProperty: 'opacity', transitionDuration: 110 }]}>
                <Icon path={ICON_SHARE} size={19} color="#ffffff" stroke={1.8} />
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </View>

      <Pressable
        style={[styles.close, { top: insets.top + TOP_GAP }]}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel="Close"
        onPress={() => {
          touched();
          tick();
          onClose?.();
          if (busy) leave();
          else clear();
        }}
      >
        <Icon path={ICON_CLOSE} size={18} color="#ffffff" stroke={1.8} />
      </Pressable>
    </View>
  );
}

/** The text cursor: solid for half a second after it moves, then blinking. */
function Caret({ color, restart }: { color: string; restart: string }) {
  return (
    <Animated.View
      key={restart}
      style={[
        styles.caret,
        {
          backgroundColor: color,
          animationName: { '0%': { opacity: 1 }, '50%': { opacity: 1 }, '56%': { opacity: 0 }, '94%': { opacity: 0 }, '100%': { opacity: 1 } },
          animationDuration: '1s',
          animationIterationCount: 'infinite',
          animationTimingFunction: 'linear',
        },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BLACK,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  close: {
    position: 'absolute',
    right: CLOSE_INSET,
    width: CLOSE,
    height: CLOSE,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: TILE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  head: {
    alignItems: 'center',
    paddingTop: 26,
  },
  title: {
    color: LABEL,
    fontSize: 16,
    lineHeight: 20,
    textAlign: 'center',
  },
  amount: {
    height: AMOUNT_ROW,
    marginTop: 15,
    flexDirection: 'row',
    alignItems: 'center',
  },
  figure: {
    color: '#ffffff',
    fontSize: AMOUNT,
    lineHeight: AMOUNT_ROW,
    fontWeight: '300',
    letterSpacing: -1.2,
    fontVariant: ['tabular-nums'],
  },
  placeholder: {
    color: PLACEHOLDER,
  },
  unit: {
    color: UNIT,
    marginLeft: 1,
  },
  unitApart: {
    marginLeft: 12,
  },
  select: {
    marginLeft: 8,
  },
  caret: {
    width: 2.5,
    height: CARET,
    borderRadius: 1.25,
    marginHorizontal: 2,
  },
  chip: {
    height: CHIP,
    marginTop: 9,
    paddingHorizontal: 13,
    borderRadius: CHIP / 2,
    backgroundColor: TILE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  chipLabel: {
    color: UNIT,
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginTop: 29,
    backgroundColor: 'rgba(255,255,255,0.14)',
  },
  payingFrom: {
    marginTop: 32,
  },
  cardSlot: {
    marginTop: 17,
    marginHorizontal: CARD_MARGIN,
  },
  card: {
    height: CARD_HEIGHT,
    borderRadius: 18,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.22)',
    paddingLeft: 13,
    paddingRight: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  cardArt: {
    width: 50,
    height: 42,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardText: {
    flex: 1,
    marginLeft: 16,
  },
  cardNumber: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 21,
    fontWeight: '500',
  },
  cardName: {
    marginTop: 3,
    color: CARD_NAME,
    fontSize: 10.5,
    lineHeight: 13,
    fontWeight: '500',
    letterSpacing: 0.4,
  },
  row: {
    position: 'absolute',
    left: PAD_MARGIN,
    right: PAD_MARGIN,
    top: 0,
    height: KEY,
    flexDirection: 'row',
  },
  key: {
    flex: 1,
    height: KEY,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flash: {
    position: 'absolute',
    width: KEY_FLASH,
    height: KEY_FLASH,
    borderRadius: KEY_FLASH / 2,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  digit: {
    color: '#ffffff',
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '500',
  },
  confirmSlot: {
    position: 'absolute',
    left: CONFIRM_MARGIN,
    right: CONFIRM_MARGIN,
  },
  wide: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  wideLabel: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  words: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  waitingTitle: {
    color: '#ffffff',
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '500',
    letterSpacing: -0.4,
  },
  waitingHint: {
    marginTop: 19,
    color: LABEL,
    fontSize: 15,
    lineHeight: 20,
  },
  done: {
    color: '#ffffff',
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
  sum: {
    height: 60,
    marginTop: 22,
    marginBottom: 6,
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  sumSign: {
    color: PLACEHOLDER,
    fontSize: 40,
    lineHeight: 60,
    fontWeight: '200',
    marginRight: 4,
  },
  sumWhole: {
    color: '#ffffff',
    fontSize: 50,
    lineHeight: 60,
    letterSpacing: -1,
    fontVariant: ['tabular-nums'],
  },
  sumCents: {
    color: PLACEHOLDER,
    fontSize: 28,
    lineHeight: 60,
    fontVariant: ['tabular-nums'],
  },
  bar: {
    position: 'absolute',
    left: BAR_MARGIN,
    right: BAR_MARGIN,
    flexDirection: 'row',
    gap: BAR_GAP,
  },
  review: {
    flex: 1,
  },
  share: {
    width: BUTTON,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: SHARE,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
