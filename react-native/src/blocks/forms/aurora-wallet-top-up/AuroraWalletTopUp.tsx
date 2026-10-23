import { Canvas, Circle, Group, LinearGradient, Path, Rect, Skia, vec } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  Aurora,
  BLUR_IN,
  COUNT_AFTER,
  COUNT_FOR,
  FORM_BACK_AFTER,
  FORM_BACK_IN,
  LOOP,
  PRESS_AT,
  PRESS_FOR,
  RESULT_AFTER,
  RESULT_IN,
  STATUS_OUT,
  SWEEP,
  USER_HOLD,
  easeOut,
  endAt,
  returnAt,
  smooth,
} from './Aurora';

export type Wallet = {
  name: string;
  /** What the wallet holds, in units of the currency. */
  balance: number;
  /** The sky of its round picture, top to bottom. */
  sky: string[];
  /** The far ridge and the near mountain of the picture. */
  ridge: string;
  peak: string;
};

type Props = {
  /** The amount the pad starts with. */
  amount?: number;
  currency?: string;
  /** Written before a balance. */
  symbol?: string;
  /** The wallets the "Wallets" button goes through; the first one is shown. */
  wallets?: Wallet[];
  /** Presses Continue by itself every LOOP seconds and leaves the balance as it was. */
  autoPlay?: boolean;
  /** Seconds from Continue to the moment the light sweeps up and the result shows. */
  processingDuration?: number;
  title?: string;
  availableLabel?: string;
  walletsLabel?: string;
  continueLabel?: string;
  processingLabel?: string;
  doneLabel?: string;
  /** Called with the amount and the wallet when Continue is released and the wait starts. */
  onContinue?: (amount: number, wallet: Wallet) => void;
  /** Called when the result shows. */
  onDone?: (amount: number, wallet: Wallet) => void;
  onBack?: () => void;
};

const WALLETS: Wallet[] = [
  { name: 'Wallet 02', balance: 478, sky: ['#c59af2', '#7f73ee', '#3d5bd8'], ridge: '#2f49c4', peak: '#14246e' },
  { name: 'Wallet 03', balance: 1250.4, sky: ['#f2734f', '#f7a65a', '#fbdc9a'], ridge: '#b5486a', peak: '#5a2152' },
  { name: 'Wallet 01', balance: 62.15, sky: ['#1f9e8f', '#5cc7a4', '#d7efb0'], ridge: '#1d7a80', peak: '#0f3f52' },
];

const BACKGROUND = '#171717';
// The screen behind the spinner and the result.
const DIM = '#121212';
// The wallet row and the currency pill sit in wells darker than the screen.
const WELL = '#101010';
const CHIP = '#222222';
const INK = '#ededed';
const MUTE = '#7c7c7c';
const STATUS = '#d6d6d6';
const BUTTON_FILL = '#f9f9f9';
const BUTTON_PRESSED = '#8c8c8c';
const BUTTON_INK = '#262626';

const MARGIN = 16;
const HEADER = 44;
const AMOUNT = 84;
// The widest the amount may get before it shrinks.
const AMOUNT_WIDTH = 330;
const CARD = 80;
const PAD_GAP = 24;
const KEY_ROW = 68;
const KEY_ROW_MIN = 48;
const KEY_WIDTH = 80;
const FLASH = 64;
const BUTTON_GAP = 58;
const BUTTON = 56;
const BUTTON_MARGIN = 14;
// Everything but the pad's rows, with 160 points for the amount: what is left sets the row height.
const FIXED_HEIGHT = HEADER + 2 + 160 + CARD + PAD_GAP + BUTTON_GAP + BUTTON;
// The form behind the spinner. iOS lays a blur view over it: its strength, and how much brightness
// goes on top of its dark tint. Android blurs the form itself (the blur view comes out as a pale
// sheet there) by this radius, from Android 12 on, and dims it as the native versions do; before
// that it only dims, more.
const IOS = Platform.OS === 'ios';
const BLUR_INTENSITY = 60;
const BLUR_RADIUS = 16;
const CAN_BLUR = IOS || Number(Platform.Version) >= 31;
const FORM_DIM = IOS ? 0.3 : CAN_BLUR ? 0.5 : 0.8;
// Degrees a second.
const SPIN = 400;
const DIGITS = 6;
const DECIMALS = 2;
const KEYS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['.', '0', 'back'],
];

// A thin left arrow in an 18 × 14 box, and a check in an 18 × 18 box.
const ARROW = 'M17 7L1 7M7 1L1 7L7 13';
const CHECK = 'M5.2 9.4L8 12.1L12.9 6.4';
// The spinner's arc: three tenths of the ring, starting at the top.
const ARC = Skia.PathBuilder.Make().addArc({ x: 0.75, y: 0.75, width: 16.5, height: 16.5 }, -90, 108).build();

// The wallet's picture in a unit box: a far ridge, a mountain, and the snow on it.
const RIDGE = [[0, 0.6], [0.16, 0.5], [0.3, 0.57], [0.5, 0.4], [0.68, 0.55], [0.84, 0.47], [1, 0.56], [1, 1], [0, 1]];
const PEAK = [[0, 0.88], [0.2, 0.7], [0.42, 0.5], [0.52, 0.6], [0.62, 0.53], [0.82, 0.76], [1, 0.84], [1, 1], [0, 1]];
const SNOW = [[0.42, 0.5], [0.52, 0.6], [0.455, 0.63], [0.41, 0.59], [0.35, 0.625], [0.33, 0.585]];
const PICTURE = 48;

function outline(points: number[][]) {
  return `M${points.map(([x, y]) => `${x * PICTURE} ${y * PICTURE}`).join('L')}Z`;
}

const PICTURE_CLIP = Skia.Path.Circle(PICTURE / 2, PICTURE / 2, PICTURE / 2);

/** "1250.5" → "1,250.5": thousands separators on the whole part, the rest as typed. */
function grouped(draft: string) {
  const [whole, fraction] = draft.split('.');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction === undefined ? '' : `.${fraction}`);
}

function money(value: number) {
  const cents = Math.round(value * 100);
  return `${grouped(String(Math.floor(cents / 100)))}.${String(cents % 100).padStart(2, '0')}`;
}

function draftFor(amount: number) {
  const cents = Math.round(Math.max(0, amount) * 100);
  const whole = String(Math.floor(cents / 100));
  if (cents % 100 === 0) return whole;
  return `${whole}.${String(cents % 100).padStart(2, '0').replace(/0$/, '')}`;
}

/** The amount gets smaller as it gets longer, so that six digits and two decimals still fit. */
function amountSize(draft: string) {
  const shown = grouped(draft);
  const marks = shown.replace(/[^,.]/g, '').length;
  return Math.min(AMOUNT, Math.floor(AMOUNT_WIDTH / ((shown.length - marks * 0.6) * 0.62)));
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Arrow({ width, stroke, color }: { width: number; stroke: number; color: string }) {
  const scale = width / 18;
  return (
    <Canvas style={{ width, height: 14 * scale }}>
      <Path path={ARROW} transform={[{ scale }]} style="stroke" strokeWidth={stroke / scale} strokeCap="round" strokeJoin="round" color={color} />
    </Canvas>
  );
}

/** The wallet's round picture: a sky, a far ridge and a mountain with snow on it. */
function Picture({ wallet }: { wallet: Wallet }) {
  return (
    <Canvas style={styles.picture}>
      <Group clip={PICTURE_CLIP}>
        <Rect x={0} y={0} width={PICTURE} height={PICTURE}>
          <LinearGradient start={vec(0, 0)} end={vec(0, PICTURE * 0.7)} colors={wallet.sky} />
        </Rect>
        <Path path={outline(RIDGE)} color={wallet.ridge} />
        <Path path={outline(PEAK)} color={wallet.peak} />
        <Path path={outline(SNOW)} color="rgba(255,255,255,0.85)" />
      </Group>
    </Canvas>
  );
}

function Key({ label, height, onPress }: { label: string; height: number; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.key, { height }]}
      accessibilityRole="button"
      accessibilityLabel={label === 'back' ? 'Delete' : label === '.' ? 'Point' : label}
    >
      {({ pressed }) => (
        <>
          {/* On at once, off slowly: a flash. */}
          <Animated.View
            style={[styles.flash, { opacity: pressed ? 1 : 0, transitionProperty: 'opacity', transitionDuration: pressed ? 40 : 350 }]}
          />
          {label === 'back' ? <Arrow width={24} stroke={2.4} color="#ffffff" /> : <Text style={styles.digit}>{label}</Text>}
        </>
      )}
    </Pressable>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A top-up screen: an amount typed on a number pad, the wallet it goes to, and Continue. Continue
 * blurs the form behind a spinner while light grows out of the button, fills the bottom of the
 * screen and breathes there, then gathers and sweeps up through the screen, leaving the result.
 * Nothing is paid here: `onContinue` is where the real request goes.
 */
export function AuroraWalletTopUp({
  amount = 100,
  currency = 'USD',
  symbol = '$',
  wallets = WALLETS,
  autoPlay = false,
  processingDuration = 2.8,
  title = 'Add money',
  availableLabel = 'Available',
  walletsLabel = 'Wallets',
  continueLabel = 'Continue',
  processingLabel = 'Topping up wallet',
  doneLabel = 'Wallet topped up',
  onContinue,
  onDone,
  onBack,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // The amount as typed, digits with at most one point; null until a key is pressed.
  const [typed, setTyped] = useState<string | null>(null);
  const [walletIndex, setWalletIndex] = useState(0);
  // What was added to each wallet here, by its place in `wallets`.
  const [added, setAdded] = useState<Record<number, number>>({});
  // A top-up is in progress, from Continue until the form is back.
  const [busy, setBusy] = useState(false);
  const [arrived, setArrived] = useState(false);
  // Continue held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The balance while it counts up, in cents.
  const [counting, setCounting] = useState<number | null>(null);

  // Seconds since Continue was released; negative while the form is idle.
  const elapsed = useSharedValue(-1);
  // The wallet's balance before and after, for the count at the end.
  const count = useSharedValue({ from: 0, to: 0 });

  const draft = typed ?? draftFor(amount);
  const value = Number(draft) || 0;
  const wallet = wallets[walletIndex];
  // What the wallet holds now: its balance plus the top-ups made here.
  const held = wallet.balance + (added[walletIndex] ?? 0);
  const duration = processingDuration;
  const back = returnAt(duration);

  const bottom = Math.max(insets.bottom - 4, 16);
  const rowHeight = Math.min(KEY_ROW, Math.max(KEY_ROW_MIN, (size.height - insets.top - bottom - FIXED_HEIGHT) / 4));
  const bar = { x: BUTTON_MARGIN, y: size.height - bottom - BUTTON, width: size.width - BUTTON_MARGIN * 2, height: BUTTON };

  // What the run in progress is about, for the moment its result shows.
  const run = useRef({ sum: 0, index: 0, auto: false });

  const finish = () => {
    elapsed.set(-1);
    setBusy(false);
    setArrived(false);
    setCounting(null);
  };

  const arrive = () => {
    const { sum, index, auto } = run.current;
    setArrived(true);
    if (!auto) {
      if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      setAdded((list) => ({ ...list, [index]: (list[index] ?? 0) + sum }));
    }
    onDone?.(sum, wallets[index]);
  };

  const start = (auto: boolean) => {
    if (busy || value <= 0) return;
    // A loop that plays by itself must come back to the frame it started from, and is silent.
    run.current = { sum: value, index: walletIndex, auto };
    count.set({ from: held, to: auto ? held : held + value });
    if (!auto) tick();
    setBusy(true);
    onContinue?.(value, wallet);
    elapsed.set(0);
    elapsed.set(
      withTiming(endAt(duration), { duration: endAt(duration) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(finish);
      }),
    );
  };

  useAnimatedReaction(
    () => elapsed.get() >= duration + RESULT_AFTER,
    (now, old) => {
      if (now && old === false) scheduleOnRN(arrive);
    },
  );

  useAnimatedReaction(
    () => {
      const { from, to } = count.get();
      return Math.round((from + (to - from) * easeOut((elapsed.get() - back - COUNT_AFTER) / COUNT_FOR)) * 100);
    },
    (cents, old) => {
      if (cents !== old && elapsed.get() >= 0) scheduleOnRN(setCounting, cents);
    },
  );

  useEffect(() => () => cancelAnimation(elapsed), [elapsed]);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ start, busy });
  useEffect(() => {
    latest.current = { start, busy };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      after(origin + PRESS_AT * 1000 - Date.now(), () => setAutoPressed(true));
      after(origin + (PRESS_AT + PRESS_FOR) * 1000 - Date.now(), () => {
        setAutoPressed(false);
        latest.current.start(true);
      });
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      if (latest.current.busy) return after(100, rest);
      // The loop starts over from the amount it was given.
      setTyped(null);
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  const press = (key: string) => {
    touched();
    tick();
    if (key === 'back') setTyped(draft.length > 1 ? draft.slice(0, -1) : '0');
    else if (key === '.') {
      if (!draft.includes('.')) setTyped(`${draft}.`);
    } else if (draft.includes('.')) {
      if (draft.length - draft.indexOf('.') <= DECIMALS) setTyped(draft + key);
    } else if (draft === '0') setTyped(key);
    else if (draft.length < DIGITS) setTyped(draft + key);
  };

  const blur = useDerivedValue(() => (elapsed.get() < 0 ? 0 : smooth(elapsed.get(), 0, BLUR_IN)));
  const returned = useDerivedValue(() =>
    elapsed.get() < 0 ? 1 : smooth(elapsed.get(), back + FORM_BACK_AFTER, back + FORM_BACK_AFTER + FORM_BACK_IN),
  );
  const done = useDerivedValue(() => smooth(elapsed.get(), duration + RESULT_AFTER, duration + RESULT_AFTER + RESULT_IN));

  const litStyle = useAnimatedStyle(() => ({ opacity: 1 - blur.get() * (1 - returned.get()) }));
  const formStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    const waiting = e >= 0 && e < back;
    const opacity = e < 0 ? 1 : waiting ? (1 - FORM_DIM * blur.get()) * (1 - smooth((e - duration) / SWEEP, 0.05, 0.4)) : returned.get();
    // On Android the blur is the form's own filter; iOS has no blur filter, hence the blur view.
    if (IOS || !CAN_BLUR) return { opacity };
    return { opacity, filter: [{ blur: waiting ? BLUR_RADIUS * blur.get() : 0 }] as unknown as string };
  });
  const blurProps = useAnimatedProps(() => ({ intensity: elapsed.get() < back ? BLUR_INTENSITY * blur.get() : 0 }));
  const buttonStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { opacity: e < 0 ? 1 : e < back ? 1 - smooth(e, 0, 0.18) : returned.get() };
  });
  const statusStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { opacity: e < 0 ? 0 : smooth(e, 0.05, BLUR_IN) * (1 - smooth(e, back, back + STATUS_OUT)) };
  });
  const waitingStyle = useAnimatedStyle(() => ({ opacity: 1 - done.get() }));
  const doneStyle = useAnimatedStyle(() => ({ opacity: done.get() }));
  const ringOpacity = useDerivedValue(() => 0.3 * (1 - done.get()));
  const arcOpacity = useDerivedValue(() => 1 - done.get());
  const spin = useDerivedValue(() => [{ rotate: reducedMotion ? 0 : (Math.max(0, elapsed.get()) * SPIN * Math.PI) / 180 }]);
  const discRadius = useDerivedValue(() => 9 * (0.6 + 0.4 * done.get()));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const balance = counting === null ? held : counting / 100;
  const shown = grouped(draft);
  const fontSize = amountSize(draft);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Animated.View style={[styles.fill, styles.lit, litStyle]} />

      <View style={styles.fill}>
        <Animated.View
          pointerEvents={busy ? 'none' : 'auto'}
          accessibilityElementsHidden={busy}
          importantForAccessibility={busy ? 'no-hide-descendants' : 'auto'}
          style={[styles.fill, { paddingTop: insets.top + 2, paddingBottom: BUTTON_GAP + BUTTON + bottom }, formStyle]}
        >
          <View style={styles.header}>
            <Text style={styles.title} numberOfLines={1}>
              {title}
            </Text>
            <Pressable
              style={styles.back}
              accessibilityRole="button"
              accessibilityLabel="Back"
              onPress={() => {
                touched();
                onBack?.();
              }}
            >
              <Arrow width={18} stroke={2} color={INK} />
            </Pressable>
          </View>

          <View style={styles.amountArea}>
            <Text
              style={[styles.amount, { fontSize }]}
              numberOfLines={1}
              accessibilityLabel="Amount"
              accessibilityValue={{ text: `${shown} ${currency}` }}
            >
              {shown}
            </Text>
            <View style={styles.currency}>
              <Text style={styles.currencyLabel}>{currency}</Text>
            </View>
          </View>

          <View style={styles.card}>
            {/* A new wallet fades in over the old one. */}
            <Animated.View key={walletIndex} style={styles.wallet} entering={FadeIn.duration(180)} exiting={FadeOut.duration(180)}>
              <Picture wallet={wallet} />
              <View style={styles.walletText}>
                <Text style={styles.walletName} numberOfLines={1}>
                  {wallet.name}
                </Text>
                <Text style={styles.walletBalance} numberOfLines={1}>
                  {symbol}
                  {money(balance)} {availableLabel}
                </Text>
              </View>
            </Animated.View>
            <Pressable
              style={styles.walletsSlot}
              hitSlop={8}
              accessibilityRole="button"
              onPress={() => {
                touched();
                tick();
                setWalletIndex((index) => (index + 1) % wallets.length);
              }}
            >
              {({ pressed }) => (
                <Animated.View style={[styles.wallets, { opacity: pressed ? 0.6 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}>
                  <Text style={styles.walletsLabel}>{walletsLabel}</Text>
                </Animated.View>
              )}
            </Pressable>
          </View>

          <View style={styles.pad}>
            {KEYS.map((row) => (
              <View key={row[0]} style={styles.keys}>
                {row.map((key) => (
                  <Key key={key} label={key} height={rowHeight} onPress={() => press(key)} />
                ))}
              </View>
            ))}
          </View>
        </Animated.View>
      </View>

      {busy && IOS && <AnimatedBlurView style={styles.fill} tint="dark" animatedProps={blurProps} />}

      {busy && (
        <View pointerEvents="none" style={styles.fill}>
          <Aurora elapsed={elapsed} duration={duration} still={reducedMotion} width={size.width} height={size.height} bar={bar} />
        </View>
      )}

      <Animated.View
        pointerEvents={busy ? 'none' : 'auto'}
        style={[styles.continueSlot, { left: BUTTON_MARGIN, right: BUTTON_MARGIN, bottom }, buttonStyle]}
      >
        <Pressable
          disabled={value <= 0}
          accessibilityRole="button"
          accessibilityState={{ disabled: value <= 0 }}
          onPress={() => {
            touched();
            start(false);
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.continue,
                {
                  backgroundColor: pressed || autoPressed ? BUTTON_PRESSED : BUTTON_FILL,
                  opacity: value > 0 ? 1 : 0.35,
                  transitionProperty: ['backgroundColor', 'opacity'],
                  transitionDuration: [100, 150],
                },
              ]}
            >
              <Text style={styles.continueLabel} numberOfLines={1}>
                {continueLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>

      <Animated.View
        pointerEvents="none"
        accessible={busy}
        accessibilityLabel={arrived ? doneLabel : processingLabel}
        style={[styles.fill, styles.status, statusStyle]}
      >
        <Canvas style={styles.mark}>
          <Circle cx={9} cy={9} r={8.25} style="stroke" strokeWidth={1.5} color={STATUS} opacity={ringOpacity} />
          <Group transform={spin} origin={vec(9, 9)}>
            <Path path={ARC} style="stroke" strokeWidth={1.5} strokeCap="round" color={STATUS} opacity={arcOpacity} />
          </Group>
          <Circle cx={9} cy={9} r={discRadius} color="#ffffff" opacity={done} />
          <Path path={CHECK} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color={DIM} end={done} />
        </Canvas>
        <View>
          <Animated.Text style={[styles.statusLabel, waitingStyle]} numberOfLines={1}>
            {processingLabel}
          </Animated.Text>
          <Animated.Text style={[styles.statusLabel, styles.statusDone, doneStyle]} numberOfLines={1}>
            {doneLabel}
          </Animated.Text>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: DIM,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  lit: {
    backgroundColor: BACKGROUND,
  },
  header: {
    height: HEADER,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
  },
  back: {
    position: 'absolute',
    left: 4,
    top: 0,
    width: 44,
    height: HEADER,
    alignItems: 'center',
    justifyContent: 'center',
  },
  amountArea: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ translateY: -8 }],
  },
  amount: {
    height: 100,
    lineHeight: 100,
    color: '#ffffff',
    fontWeight: '600',
    letterSpacing: -2.5,
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
    marginBottom: 22,
  },
  currency: {
    height: 32,
    paddingHorizontal: 13,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 6, color: WELL }],
  },
  currencyLabel: {
    color: INK,
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '600',
  },
  card: {
    height: CARD,
    marginHorizontal: MARGIN,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    // The well has no hard edge: it sinks into the screen.
    boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 10, color: WELL }],
  },
  wallet: {
    position: 'absolute',
    left: 14,
    right: 100,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
  },
  picture: {
    width: PICTURE,
    height: PICTURE,
  },
  walletText: {
    flex: 1,
    marginLeft: 16,
  },
  walletName: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 21,
    fontWeight: '600',
  },
  walletBalance: {
    marginTop: 4,
    color: MUTE,
    fontSize: 14,
    lineHeight: 17,
    fontVariant: ['tabular-nums'],
  },
  walletsSlot: {
    position: 'absolute',
    right: 15,
    top: (CARD - 34) / 2,
  },
  wallets: {
    height: 34,
    paddingHorizontal: 14,
    borderRadius: 17,
    backgroundColor: CHIP,
    justifyContent: 'center',
  },
  walletsLabel: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  pad: {
    paddingTop: PAD_GAP,
    paddingHorizontal: MARGIN,
  },
  keys: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  key: {
    width: KEY_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flash: {
    position: 'absolute',
    width: FLASH,
    height: FLASH,
    borderRadius: FLASH / 2,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  digit: {
    color: '#ffffff',
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '500',
  },
  continueSlot: {
    position: 'absolute',
    height: BUTTON,
  },
  continue: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  continueLabel: {
    color: BUTTON_INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mark: {
    width: 18,
    height: 18,
    marginRight: 10,
  },
  statusLabel: {
    color: STATUS,
    fontSize: 20,
    lineHeight: 25,
  },
  statusDone: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});
