import { Ionicons } from '@expo/vector-icons';
import { BlurMask, Canvas, Group, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useDerivedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP, ParticleCard } from './ParticleCard';

export type CardDetails = { name: string; securityCode: string };

type Props = {
  /** Fills the form in by itself every LOOP seconds, presses Continue and waits for the form to return. */
  autoPlay?: boolean;
  /** What autoPlay types into the two fields. */
  demoName?: string;
  demoSecurityCode?: string;
  /** Digits a security code has; Continue wakes up once the name and that many digits are in. */
  securityCodeLength?: number;
  /** Seconds from Continue to the moment the form returns. */
  processingDuration?: number;
  title?: string;
  subtitle?: string;
  nameLabel?: string;
  securityCodeLabel?: string;
  continueLabel?: string;
  addingTitle?: string;
  addingSubtitle?: string;
  manualLabel?: string;
  /** Called with the details when Continue is released and the wait starts. */
  onSubmit?: (details: CardDetails) => void;
  /** Called when the wait is over and the form comes back, emptied. */
  onDone?: (details: CardDetails) => void;
  onManual?: () => void;
  onClose?: () => void;
};

// autoPlay: the name field is tapped at TAP_AT and typed from TYPE_AT, one letter after each of
// these pauses in turn (seconds); then the caret moves to the code, which is typed evenly.
const TAP_AT = 0.8;
const TYPE_AT = 1.05;
const LETTER_GAPS = [0.11, 0.09, 0.13, 0.1, 0.16, 0.12, 0.09, 0.11];
const NEXT_FIELD_AFTER = 0.3;
const CODE_AFTER = 0.24;
const DIGIT_EVERY = 0.17;
const PRESS_AFTER = 0.5;
const PRESS_FOR = 0.15;
// After a touch autoPlay waits this long, and for the form to be idle, before it starts over.
const USER_HOLD = 3;

// The clock of a submission, in seconds since Continue was released.
const TEXT_SWAP = 0.14;
const FIELDS_OUT = 0.28;
const CHROME_OUT = 0.2;
// The form returns `processingDuration` later: texts and fields first, then the buttons.
const FIELDS_IN = 0.32;
const CHROME_AFTER = 0.2;
const CHROME_IN = 0.26;
// The spinner turns one of its eight bars every SPIN_STEP.
const SPIN_STEP = 0.1;
const BARS = 8;

const SCREEN = '#000000';
const INK = '#FFFFFF';
const MUTE = '#98989F';
const FAINT = '#48484C';
const GROUP = '#2C2C2E';
// The field group as it blurs away: a violet ghost.
const GROUP_GHOST = '#35314C';
const HAIRLINE = '#444447';
const CLOSE_FILL = '#222224';
const BUTTON_OFF = '#38383A';
const BUTTON_OFF_INK = '#8E8E93';
const BUTTON_ON = '#F2F2F7';
const BUTTON_PRESSED = '#B9B9BF';
const BUTTON_ON_INK = '#000000';
const CARET = '#0A84FF';

// The screen, in points, for a 402 × 874 screen.
const CLOSE = 44;
const CLOSE_LEFT = 15;
const CLOSE_TOP = 3;
const CARD_MARGIN = 20;
// From the safe area's top edge to the card's.
const CARD_TOP = 75;
// A payment card's proportions.
const CARD_RATIO = 1.586;
const TEXT_GAP = 76;
const TEXT_LEFT = 38;
const TEXTS = 71;
const FIELDS_GAP = 29;
const FIELDS_MARGIN = 22;
const ROW = 48.5;
const VALUE_LEFT = 131;
const BUTTON = 51;
const BUTTON_MARGIN = 38;
// With the keyboard up, Continue rides this far above it and the fields this far above Continue.
const KEYBOARD_GAP = 12;
const FIELDS_CLEAR = 16;
// The field group shrinks to this as it leaves, and returns from this.
const FIELDS_SHRINK = 0.9;
// iOS says the keyboard is coming before it moves, Android once it is up.
const IOS = Platform.OS === 'ios';
// Behind the fields a blurred copy of the group's shape takes over as they leave: it softens by
// up to GHOST_BLUR and needs GHOST_SPILL of room round the group.
const FIELDS_RADIUS = 23;
const GHOST_BLUR = 16;
const GHOST_SPILL = 40;

type Field = 'name' | 'code';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** The eight-bar activity spinner: the bars keep their brightness and the whole turns a bar at a time. */
function Spinner({ elapsed }: { elapsed: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({
    transform: [{ rotate: `${(Math.floor(Math.max(0, elapsed.get()) / SPIN_STEP) % BARS) * (360 / BARS)}deg` }],
  }));
  return (
    <Animated.View style={[styles.spinner, style]}>
      {Array.from({ length: BARS }, (_, index) => (
        <View
          key={index}
          style={[
            styles.bar,
            { opacity: 0.2 + (0.8 * index) / (BARS - 1), transform: [{ rotate: `${index * (360 / BARS)}deg` }, { translateY: -5 }] },
          ]}
        />
      ))}
    </Animated.View>
  );
}

type RowProps = {
  label: string;
  value: string;
  secure?: boolean;
  /** autoPlay is typing here: a caret is drawn after the text. */
  caret: boolean;
  focused: boolean;
  maxLength?: number;
  inputRef: React.RefObject<TextInput | null>;
  onChangeText: (text: string) => void;
  onFocus: () => void;
  onBlur: () => void;
  onSubmitEditing: () => void;
  last?: boolean;
};

/**
 * One row of the group. The text field is real, but it only shows itself while it is focused;
 * otherwise the row draws its value (and autoPlay's caret) itself, so that autoPlay never needs
 * the keyboard.
 */
function Row({ label, value, secure, caret, focused, maxLength, inputRef, onChangeText, onFocus, onBlur, onSubmitEditing, last }: RowProps) {
  const shown = secure ? '•'.repeat(value.length) : value;
  return (
    <Pressable style={styles.row} onPress={() => inputRef.current?.focus()} accessible={false}>
      <Text style={styles.label} numberOfLines={1}>
        {label}
      </Text>
      <View style={styles.value}>
        {!focused && (
          <View style={styles.mirror} pointerEvents="none">
            <Text style={[styles.input, secure && styles.secure]} numberOfLines={1}>
              {shown}
            </Text>
            {caret && <View style={styles.caret} />}
          </View>
        )}
        <TextInput
          ref={inputRef}
          value={value}
          onChangeText={onChangeText}
          onFocus={onFocus}
          onBlur={onBlur}
          onSubmitEditing={onSubmitEditing}
          style={[styles.input, styles.field, { opacity: focused ? 1 : 0 }]}
          secureTextEntry={secure}
          keyboardType={secure ? 'number-pad' : 'default'}
          keyboardAppearance="dark"
          autoCapitalize="words"
          autoCorrect={false}
          autoComplete={secure ? 'cc-csc' : 'cc-name'}
          textContentType={secure ? 'creditCardSecurityCode' : 'creditCardName'}
          returnKeyType={last ? 'done' : 'next'}
          maxLength={maxLength}
          selectionColor={CARET}
          accessibilityLabel={label}
        />
      </View>
    </Pressable>
  );
}

type GhostProps = { width: number; height: number; gone: SharedValue<number>; still: boolean };

/** What is left of the field group as it blurs away: its shape, violet, softer and fainter. */
function Ghost({ width, height, gone, still }: GhostProps) {
  const blur = useDerivedValue(() => 1 + GHOST_BLUR * gone.get());
  const opacity = useDerivedValue(() => smooth(gone.get(), 0, 0.35) * (1 - smooth(gone.get(), 0.4, 1)));
  const transform = useDerivedValue(() => [{ scale: still ? 1 : 1 - (1 - FIELDS_SHRINK) * gone.get() }]);
  return (
    <Canvas pointerEvents="none" style={[styles.ghost, { width: width + GHOST_SPILL * 2, height: height + GHOST_SPILL * 2 }]}>
      <Group transform={transform} origin={{ x: GHOST_SPILL + width / 2, y: GHOST_SPILL + height / 2 }} opacity={opacity}>
        <RoundedRect x={GHOST_SPILL} y={GHOST_SPILL} width={width} height={height} r={FIELDS_RADIUS} color={GROUP_GHOST}>
          <BlurMask blur={blur} style="normal" />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

/**
 * "Card Details": where the card will be there is a card-shaped cloud of twinkling particles, with
 * a name and a security code to fill in under it. Continue switches to "Adding Card": the fields
 * shrink and blur away while a solid card flashes in and crumbles from the top down into dust,
 * back to the cloud; after the wait the form returns. Nothing is sent anywhere: `onSubmit` is
 * where the real request goes.
 */
export function ParticleCardDetails({
  autoPlay = false,
  demoName = 'Maya Chen',
  demoSecurityCode = '482',
  securityCodeLength = 3,
  processingDuration = 2.7,
  title = 'Card Details',
  subtitle = 'Verify and complete your card information.',
  nameLabel = 'Name',
  securityCodeLabel = 'Security Code',
  continueLabel = 'Continue',
  addingTitle = 'Adding Card',
  addingSubtitle = 'Contacting the Card Issuer…',
  manualLabel = 'Enter Card Details Manually',
  onSubmit,
  onDone,
  onManual,
  onClose,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [focused, setFocused] = useState<Field | null>(null);
  // The field autoPlay is typing in, and whether it holds Continue down.
  const [autoField, setAutoField] = useState<Field | null>(null);
  const [autoPressed, setAutoPressed] = useState(false);
  // A submission is in progress, from Continue until the form is back.
  const [busy, setBusy] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The field group's height as laid out, for its ghost.
  const [fieldsHeight, setFieldsHeight] = useState(ROW * 2);

  // Seconds since Continue was released; negative while the form is idle.
  const elapsed = useSharedValue(-1);
  // The twinkle's clock: 0 to LOOP, again and again.
  const time = useSharedValue(0);
  // How far the keyboard pushes the form up, and Continue.
  const lift = useSharedValue(0);
  const raise = useSharedValue(0);
  const nameRef = useRef<TextInput>(null);
  const codeRef = useRef<TextInput>(null);
  // The bottom edge of the field group, from the top of the screen.
  const fieldsBottom = useRef(0);
  const sent = useRef<CardDetails>({ name: '', securityCode: '' });

  const cardWidth = size.width - CARD_MARGIN * 2;
  const cardHeight = cardWidth / CARD_RATIO;
  const bottom = Math.max(insets.bottom, 16) + 1;
  const ready = name.trim().length >= 2 && code.length === securityCodeLength;
  const back = processingDuration;
  // Where Continue's top edge rests, from the top of the screen.
  const buttonRest = useRef(0);
  useEffect(() => {
    buttonRest.current = size.height - bottom - BUTTON;
  });
  const end = back + CHROME_AFTER + CHROME_IN;

  useEffect(() => {
    time.set(0);
    time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false));
    return () => cancelAnimation(time);
  }, [time]);

  // Continue rides above the keyboard, and the form rises until the fields clear it.
  useEffect(() => {
    const show = Keyboard.addListener(IOS ? 'keyboardWillShow' : 'keyboardDidShow', (event) => {
      const timing = { duration: event.duration || 250, easing: Easing.out(Easing.cubic) };
      const buttonTop = event.endCoordinates.screenY - KEYBOARD_GAP - BUTTON;
      raise.set(withTiming(Math.min(0, buttonTop - buttonRest.current), timing));
      lift.set(withTiming(Math.min(0, buttonTop - FIELDS_CLEAR - fieldsBottom.current), timing));
    });
    const hide = Keyboard.addListener(IOS ? 'keyboardWillHide' : 'keyboardDidHide', (event) => {
      const timing = { duration: event.duration || 250, easing: Easing.out(Easing.cubic) };
      raise.set(withTiming(0, timing));
      lift.set(withTiming(0, timing));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [lift, raise]);

  /** The wait is over: the fields come back empty. */
  const emptied = () => {
    setName('');
    setCode('');
    onDone?.(sent.current);
  };

  const finish = () => {
    elapsed.set(-1);
    setBusy(false);
  };

  const submit = (byHand: boolean) => {
    if (busy || !ready) return;
    if (byHand) tick();
    Keyboard.dismiss();
    sent.current = { name: name.trim(), securityCode: code };
    setAutoField(null);
    setBusy(true);
    onSubmit?.(sent.current);
    elapsed.set(0);
    elapsed.set(
      withTiming(end, { duration: end * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(finish);
      }),
    );
  };

  useAnimatedReaction(
    () => elapsed.get() >= back,
    (now, old) => {
      if (now && old === false) scheduleOnRN(emptied);
    },
  );

  useEffect(() => () => cancelAnimation(elapsed), [elapsed]);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ submit, busy, focused });
  useEffect(() => {
    latest.current = { submit, busy, focused };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      at(TAP_AT, () => setAutoField('name'));
      let t = TYPE_AT;
      demoName.split('').forEach((_, index) => {
        if (index > 0) t += LETTER_GAPS[(index - 1) % LETTER_GAPS.length];
        at(t, () => setName(demoName.slice(0, index + 1)));
      });
      t += NEXT_FIELD_AFTER;
      at(t, () => setAutoField('code'));
      t += CODE_AFTER;
      demoSecurityCode.split('').forEach((_, index) => {
        if (index > 0) t += DIGIT_EVERY;
        at(t, () => setCode(demoSecurityCode.slice(0, index + 1)));
      });
      t += PRESS_AFTER;
      at(t, () => {
        setAutoField(null);
        setAutoPressed(true);
      });
      at(t + PRESS_FOR, () => {
        setAutoPressed(false);
        latest.current.submit(false);
      });
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch the loop starts over from an idle, empty form with the keyboard down.
    const rest = () => {
      if (latest.current.busy) return after(100, rest);
      Keyboard.dismiss();
      setName('');
      setCode('');
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, demoName, demoSecurityCode]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoField(null);
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  const liftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: lift.get() }] }));
  const raiseStyle = useAnimatedStyle(() => ({ transform: [{ translateY: raise.get() }] }));
  const askStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { opacity: e < 0 ? 1 : e < back ? 1 - smooth(e, 0, TEXT_SWAP) : smooth(e, back, back + TEXT_SWAP) };
  });
  const addingStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { opacity: e < 0 ? 0 : e < back ? smooth(e, 0, TEXT_SWAP) : 1 - smooth(e, back, back + TEXT_SWAP) };
  });
  // The field group shrinks and blurs away, a violet ghost for a moment, and returns the same way.
  // How far the field group has gone: 0 in place, 1 away.
  const gone = useDerivedValue(() => {
    const e = elapsed.get();
    return e < 0 ? 0 : e < back ? smooth(e, 0, FIELDS_OUT) : 1 - smooth(e, back, back + FIELDS_IN);
  });
  // The group shrinks and turns violet while it fades into its blurred ghost, and returns the same way.
  const fieldsStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(gone.get(), 0, 0.55),
    backgroundColor: interpolateColor(Math.min(1, gone.get() * 2), [0, 1], [GROUP, GROUP_GHOST]),
    transform: [{ scale: reducedMotion ? 1 : 1 - (1 - FIELDS_SHRINK) * gone.get() }],
  }));
  const chromeStyle = useAnimatedStyle(() => {
    const e = elapsed.get();
    return { opacity: e < 0 ? 1 : e < back ? 1 - smooth(e, 0, CHROME_OUT) : smooth(e, back + CHROME_AFTER, back + CHROME_AFTER + CHROME_IN) };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const onFieldsLayout = (event: LayoutChangeEvent) => {
    const { y, height } = event.nativeEvent.layout;
    fieldsBottom.current = y + height;
    setFieldsHeight(height - FIELDS_GAP);
  };

  const focus = (field: Field) => {
    touched();
    setFocused(field);
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Animated.View style={[styles.fill, liftStyle]}>
        {/* A touch outside the fields puts the keyboard away. */}
        <Pressable style={styles.fill} onPress={() => Keyboard.dismiss()} accessible={false} />
        <View style={{ height: insets.top + CARD_TOP }} />
        <View style={{ height: cardHeight, marginHorizontal: CARD_MARGIN }}>
          <ParticleCard width={cardWidth} height={cardHeight} time={time} period={LOOP} elapsed={elapsed} still={reducedMotion} />
        </View>
        <View style={styles.textGap} />

        <View style={styles.texts}>
          <Animated.View style={askStyle} accessibilityElementsHidden={busy}>
            <Text style={styles.title} numberOfLines={1}>
              {title}
            </Text>
            <Text style={styles.subtitle} numberOfLines={2}>
              {subtitle}
            </Text>
          </Animated.View>
          <Animated.View style={[styles.adding, addingStyle]} pointerEvents={busy ? 'auto' : 'none'} accessibilityElementsHidden={!busy}>
            <View style={styles.addingTitle}>
              <Text style={styles.title} numberOfLines={1}>
                {addingTitle}
              </Text>
              <Spinner elapsed={elapsed} />
            </View>
            <Text style={styles.subtitle} numberOfLines={1}>
              {addingSubtitle}
            </Text>
            <Pressable
              hitSlop={10}
              style={styles.manual}
              accessibilityRole="link"
              onPress={() => {
                touched();
                onManual?.();
              }}
            >
              <Text style={styles.manualLabel}>{manualLabel}</Text>
            </Pressable>
          </Animated.View>
        </View>

        <View onLayout={onFieldsLayout} pointerEvents={busy ? 'none' : 'auto'}>
          {busy && <Ghost width={size.width - FIELDS_MARGIN * 2} height={fieldsHeight} gone={gone} still={reducedMotion} />}
          <Animated.View style={[styles.fields, fieldsStyle]}>
            <Row
              label={nameLabel}
              value={name}
              caret={autoField === 'name'}
              focused={focused === 'name'}
              inputRef={nameRef}
              onChangeText={(text) => {
                touched();
                setName(text);
              }}
              onFocus={() => focus('name')}
              onBlur={() => setFocused((field) => (field === 'name' ? null : field))}
              onSubmitEditing={() => codeRef.current?.focus()}
            />
            <View style={styles.hairline} />
            <Row
              label={securityCodeLabel}
              value={code}
              secure
              last
              caret={autoField === 'code'}
              focused={focused === 'code'}
              maxLength={securityCodeLength}
              inputRef={codeRef}
              onChangeText={(text) => {
                touched();
                setCode(text.replace(/\D/g, ''));
              }}
              onFocus={() => focus('code')}
              onBlur={() => setFocused((field) => (field === 'code' ? null : field))}
              onSubmitEditing={() => Keyboard.dismiss()}
            />
          </Animated.View>
        </View>
      </Animated.View>

      <Animated.View style={[styles.closeSlot, { top: insets.top + CLOSE_TOP }, chromeStyle]} pointerEvents={busy ? 'none' : 'auto'}>
        <Pressable
          style={styles.close}
          accessibilityRole="button"
          accessibilityLabel="Close"
          onPress={() => {
            touched();
            Keyboard.dismiss();
            onClose?.();
          }}
        >
          <Ionicons name="close" size={25} color={INK} />
        </Pressable>
      </Animated.View>

      <Animated.View style={[styles.continueSlot, { bottom }, raiseStyle]} pointerEvents={busy ? 'none' : 'auto'}>
        <Animated.View style={chromeStyle}>
          <Pressable
            disabled={!ready}
            accessibilityRole="button"
            accessibilityState={{ disabled: !ready }}
            onPress={() => {
              touched();
              submit(true);
            }}
          >
            {({ pressed }) => (
              <Animated.View
                style={[
                  styles.continue,
                  {
                    backgroundColor: !ready ? BUTTON_OFF : pressed || autoPressed ? BUTTON_PRESSED : BUTTON_ON,
                    transform: [{ scale: ready && (pressed || autoPressed) ? 0.97 : 1 }],
                    transitionProperty: ['backgroundColor', 'transform'],
                    transitionDuration: [ready && (pressed || autoPressed) ? 80 : 220, 120],
                  },
                ]}
              >
                <Animated.Text
                  style={[
                    styles.continueLabel,
                    { color: ready ? BUTTON_ON_INK : BUTTON_OFF_INK, transitionProperty: 'color', transitionDuration: 220 },
                  ]}
                  numberOfLines={1}
                >
                  {continueLabel}
                </Animated.Text>
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  textGap: {
    flexGrow: 1,
    flexBasis: 0,
    maxHeight: TEXT_GAP,
  },
  texts: {
    height: TEXTS,
    marginLeft: TEXT_LEFT,
    marginRight: TEXT_LEFT + 14,
  },
  adding: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  addingTitle: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    color: INK,
    fontSize: 19,
    lineHeight: 23,
    fontWeight: '700',
  },
  subtitle: {
    color: MUTE,
    fontSize: 19,
    lineHeight: 24,
  },
  spinner: {
    width: 16,
    height: 16,
    marginLeft: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bar: {
    position: 'absolute',
    width: 1.8,
    height: 4.6,
    borderRadius: 0.9,
    backgroundColor: MUTE,
  },
  manual: {
    marginTop: 18,
    alignSelf: 'flex-start',
  },
  manualLabel: {
    color: FAINT,
    fontSize: 12,
    lineHeight: 16,
  },
  fields: {
    marginTop: FIELDS_GAP,
    marginHorizontal: FIELDS_MARGIN,
    borderRadius: FIELDS_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: GROUP,
  },
  ghost: {
    position: 'absolute',
    left: FIELDS_MARGIN - GHOST_SPILL,
    top: FIELDS_GAP - GHOST_SPILL,
  },
  row: {
    height: ROW,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: TEXT_LEFT - FIELDS_MARGIN,
  },
  label: {
    width: VALUE_LEFT - (TEXT_LEFT - FIELDS_MARGIN),
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  value: {
    flex: 1,
    height: ROW,
    justifyContent: 'center',
    marginRight: 16,
  },
  mirror: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  input: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    padding: 0,
  },
  secure: {
    fontSize: 22,
    letterSpacing: 1,
  },
  field: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  caret: {
    width: 2,
    height: 19,
    marginLeft: 1,
    borderRadius: 1,
    backgroundColor: CARET,
  },
  hairline: {
    height: StyleSheet.hairlineWidth,
    marginHorizontal: TEXT_LEFT - FIELDS_MARGIN,
    backgroundColor: HAIRLINE,
  },
  closeSlot: {
    position: 'absolute',
    left: CLOSE_LEFT,
  },
  close: {
    width: CLOSE,
    height: CLOSE,
    borderRadius: CLOSE / 2,
    backgroundColor: CLOSE_FILL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  continueSlot: {
    position: 'absolute',
    left: BUTTON_MARGIN,
    right: BUTTON_MARGIN,
  },
  continue: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  continueLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
});
