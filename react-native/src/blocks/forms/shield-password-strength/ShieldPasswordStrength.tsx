import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, CornerPathEffect, Group, Path, RadialGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  Keyframe,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** 0: too short to judge, 1: weak, 2: fair, 3: strong. */
export type Strength = 0 | 1 | 2 | 3;

type Four = [string, string, string, string];

type Props = {
  /** Types a password in three bursts every LOOP seconds, secures it and empties the field again. */
  autoPlay?: boolean;
  /** What autoPlay types, burst by burst; each burst should reach the next strength. */
  demoPassword?: string[];
  label?: string;
  /** The line under the field for each strength, from 0 to 3. */
  hints?: Four;
  /** The colour of the shield and its glow for each strength, as #RRGGBB. */
  colors?: Four;
  /** The colour of the hint for each strength. */
  hintColors?: Four;
  /** How strong a password is. */
  strengthOf?: (password: string) => Strength;
  onChangePassword?: (password: string, strength: Strength) => void;
  /** Called when a strong password is submitted, as the keyhole becomes a check. */
  onSecured?: (password: string) => void;
};

/** autoPlay: one password every LOOP seconds. The glow's breath and the caret's blink divide it. */
export const LOOP = 10.5;
// The bursts start at these times and type a character every TYPE_EVERY.
const BURST_AT = [0.8, 2.5, 3.9];
const TYPE_EVERY = 0.11;
const SUBMIT_AT = 6;
const RESET_AT = 8.7;
// After a touch autoPlay waits this long, and for the screen to be idle, before it starts over.
const USER_HOLD = 3;
const BREATH = LOOP / 5;
const BLINK = LOOP / 10;

// A change of strength, in seconds: the shield and its glow go pale by PALE_AT and come back in
// the new colour by SHIFT; the colour itself turns between COLOR_FROM and COLOR_TO.
const SHIFT = 0.9;
const PALE_AT = 0.28;
const COLOR_FROM = 0.15;
const COLOR_TO = 0.7;
// How pale: what the shield, the glow, and the label and field lose at the palest moment.
const PALE = { shield: 0.62, glow: 0.7, form: 0.45 };
// The old hint is cleared after HINT_CLEAR and the new one typed a letter every HINT_STEP.
const HINT_CLEAR = 0.25;
const HINT_STEP = 0.022;

// The clock of securing, in seconds since a strong password was submitted: the form leaves, the
// shield glides to the middle of the screen, the keyhole becomes a check with a small pop.
const FORM_OUT = 0.4;
const GLIDE_AT = 0.45;
const GLIDE_FOR = 0.9;
const CHECK_AT = 1.5;
const CHECK_FOR = 0.45;
const POP = 0.12;
const SECURED_END = CHECK_AT + CHECK_FOR + 0.2;
// Going back: the check leaves first, the shield glides up, the form returns in the second half.
const RESET_FOR = 0.75;

const BACKGROUND = '#F5F9FC';
const INK = '#1F2933';
const DOT = '#5A6472';
const ICON = '#64748B';
const CARET = '#8A94A0';
const FIELD = '#FFFFFF';
const HINTS: Four = ['Enter Your Password', 'Add more characters to strengthen it', 'Nice, try adding a symbol', 'Perfect! Strong & secure'];
// Idle cyan, weak red, fair burnt orange, strong green.
const COLORS: Four = ['#2FE0EA', '#E5173F', '#C85A0A', '#1FC955'];
// The hints: grey while idle, then the shield's colours, the green a little darker to stay readable.
const HINT_COLORS: Four = ['#8A94A0', '#E5173F', '#C85A0A', '#17A34A'];
const DEMO = ['maple', 'Sy7', '-river'];

// Sizes in points.
const SHIELD = { width: 53, height: 61, corner: 7 };
const GLOW = 104;
// The glow's strength at its centre, falling off as cos² to its edge; it breathes by BREATH_BY.
const GLOW_ALPHA = 0.44;
const FALLOFF_AT = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1];
const FALLOFF = [1, 0.976, 0.905, 0.794, 0.655, 0.5, 0.345, 0.206, 0.095, 0.024, 0];
const BREATH_BY = 0.06;
// From the shield's centre down to the field's.
const SHIELD_TO_FIELD = 154;
// Where the shield's centre sits, as a share of the screen's height.
const SHIELD_AT = 0.4;
const FIELD_WIDTH = 346;
const FIELD_MARGIN = 28;
const FIELD_HEIGHT = 40;
const LABEL_GAP = 10;
const HINT_GAP = 7;
const TEXT_LEFT = 28;
const DOT_SIZE = 6;
const DOT_GAP = 4;
const ICONS = 15;
const HINT_HEIGHT = 14;
// With the keyboard up, the hint stays this far above it.
const KEYBOARD_GAP = 16;
// iOS says the keyboard is coming before it moves, Android once it is up.
const IOS = Platform.OS === 'ios';

// The shield in its box: a low peak at the top, straight sides and a point at the bottom, the
// corners rounded by SHIELD.corner. Then the keyhole and the check that replaces it.
const OUTLINE = `M${SHIELD.width / 2} 0L${SHIELD.width} 3.5L${SHIELD.width} 40L${SHIELD.width / 2} ${SHIELD.height}L0 40L0 3.5Z`;
const KEYHOLE = { x: SHIELD.width / 2, y: 23, radius: 7.6, stem: 5, bottom: 41 };
const CHECK = 'M15.5 30.5L23 37.8L37.8 22.8';
const CHECK_STROKE = 4.2;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** 0 at both ends and 1 at `peak`: up and down again with smooth shoulders. */
function bump(t: number, from: number, peak: number, to: number) {
  'worklet';
  return smooth(t, from, peak) * (1 - smooth(t, peak, to));
}

function rgb(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

/** Length first, then variety: five characters to be judged, eight and two kinds for fair, twelve and three kinds for strong. */
function defaultStrength(password: string): Strength {
  if (password.length < 5) return 0;
  const kinds = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((kind) => kind.test(password)).length;
  if (password.length >= 12 && kinds >= 3) return 3;
  if (password.length >= 8 && kinds >= 2) return 2;
  return 1;
}

// A new dot arrives small and pale, and darkens.
const DOT_IN = new Keyframe({
  0: { opacity: 0.15, transform: [{ scale: 0.5 }] },
  35: { opacity: 0.35, transform: [{ scale: 1 }] },
  100: { opacity: 1, transform: [{ scale: 1 }] },
}).duration(420);

/**
 * A password field under a glowing shield. As the password grows stronger the shield and its glow
 * change colour, going pale in between, and the line under the field retypes itself. Submitting a
 * strong password sends the field away: the shield glides to the middle of the screen and its
 * keyhole becomes a check. Nothing is stored or sent: `onSecured` is where the password goes.
 */
export function ShieldPasswordStrength({
  autoPlay = false,
  demoPassword = DEMO,
  label = 'Password',
  hints = HINTS,
  colors = COLORS,
  hintColors = HINT_COLORS,
  strengthOf = defaultStrength,
  onChangePassword,
  onSecured,
}: Props) {
  const reducedMotion = useReducedMotion();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [password, setPassword] = useState('');
  // The eye: the password as letters instead of dots.
  const [revealed, setRevealed] = useState(false);
  const [focused, setFocused] = useState(false);
  // 'form' while typing, 'secured' from a strong password's submission, 'leaving' on the way back.
  const [phase, setPhase] = useState<'form' | 'secured' | 'leaving'>('form');
  // The line under the field: its text, how much of it is typed, and its strength.
  const [hint, setHint] = useState({ level: 0 as Strength, count: hints[0].length });
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The loop's clock, 0 to LOOP again and again: the glow's breath and the caret's blink.
  const time = useSharedValue(0);
  // The colours a change of strength goes between, and how far it is (seconds).
  const tint = useSharedValue({ from: rgb(colors[0]), to: rgb(colors[0]) });
  const shift = useSharedValue(SHIFT);
  // Seconds since a strong password was submitted; negative while the form is in use.
  const secured = useSharedValue(-1);
  // 0 → 1 on the way back to the form.
  const leaving = useSharedValue(0);
  // How far the keyboard pushes everything up.
  const lift = useSharedValue(0);
  const input = useRef<TextInput>(null);
  const byHand = useRef(false);
  const hintTimers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const level = strengthOf(password);
  const fieldWidth = Math.min(FIELD_WIDTH, size.width - FIELD_MARGIN * 2);
  const shieldY = size.height * SHIELD_AT;
  // How far the shield glides: to the middle of the screen.
  const drop = size.height / 2 - shieldY;

  useEffect(() => {
    time.set(0);
    time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false));
    return () => cancelAnimation(time);
  }, [time]);

  // Everything rises until the hint clears the keyboard.
  const hintBottom = useRef(0);
  useEffect(() => {
    hintBottom.current = shieldY + SHIELD_TO_FIELD + FIELD_HEIGHT / 2 + HINT_GAP + HINT_HEIGHT;
  });
  useEffect(() => {
    const show = Keyboard.addListener(IOS ? 'keyboardWillShow' : 'keyboardDidShow', (event) => {
      const room = event.endCoordinates.screenY - KEYBOARD_GAP - hintBottom.current;
      lift.set(withTiming(Math.min(0, room), { duration: event.duration || 250, easing: Easing.out(Easing.cubic) }));
    });
    const hide = Keyboard.addListener(IOS ? 'keyboardWillHide' : 'keyboardDidHide', (event) => {
      lift.set(withTiming(0, { duration: event.duration || 250, easing: Easing.out(Easing.cubic) }));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [lift]);

  // A new strength: the colours shift and the hint is cleared and typed again.
  const shown = useRef<Strength>(0);
  useEffect(() => {
    if (shown.current === level) return;
    tint.set({ from: rgb(colors[shown.current]), to: rgb(colors[level]) });
    shown.current = level;
    shift.set(0);
    shift.set(withTiming(SHIFT, { duration: SHIFT * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    hintTimers.current.forEach(clearTimeout);
    hintTimers.current = Array.from({ length: hints[level].length + 1 }, (_, count) =>
      setTimeout(() => setHint({ level, count }), (HINT_CLEAR + count * HINT_STEP) * 1000),
    );
  }, [level, colors, hints, tint, shift]);

  useEffect(
    () => () => {
      hintTimers.current.forEach(clearTimeout);
      cancelAnimation(shift);
      cancelAnimation(secured);
      cancelAnimation(leaving);
    },
    [shift, secured, leaving],
  );

  const change = (next: string) => {
    setPassword(next);
    onChangePassword?.(next, strengthOf(next));
  };

  /** A strong password is submitted: the form leaves and the shield takes the screen. */
  const secure = (hand: boolean) => {
    if (phase !== 'form' || level < 3) return;
    byHand.current = hand;
    Keyboard.dismiss();
    setPhase('secured');
    secured.set(0);
    secured.set(withTiming(SECURED_END, { duration: SECURED_END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  const checked = () => {
    if (byHand.current && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onSecured?.(password);
  };

  const home = () => setPhase('form');

  /** Back to an empty form. */
  const reset = () => {
    if (phase !== 'secured') return;
    setPhase('leaving');
    change('');
    leaving.set(
      withTiming(1, { duration: RESET_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        secured.set(-1);
        leaving.set(0);
        scheduleOnRN(home);
      }),
    );
  };

  useAnimatedReaction(
    () => secured.get() >= CHECK_AT + CHECK_FOR / 2,
    (now, old) => {
      if (now && old === false) scheduleOnRN(checked);
    },
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ change, secure, reset, phase, password, hints });
  useEffect(() => {
    latest.current = { change, secure, reset, phase, password, hints };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      let typed = '';
      demoPassword.forEach((burst, index) => {
        burst.split('').forEach((letter, place) => {
          typed += letter;
          const text = typed;
          at((BURST_AT[index] ?? BURST_AT[BURST_AT.length - 1]) + place * TYPE_EVERY, () => latest.current.change(text));
        });
      });
      at(SUBMIT_AT, () => latest.current.secure(false));
      at(RESET_AT, () => latest.current.reset());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch the loop starts over from an empty form with the keyboard down.
    const rest = () => {
      const now = latest.current;
      if (now.phase === 'secured') now.reset();
      if (now.phase !== 'form') return after(100, rest);
      Keyboard.dismiss();
      setRevealed(false);
      if (now.password !== '') {
        now.change('');
        // The idle hint has to be typed back before the loop starts.
        return after((HINT_CLEAR + now.hints[0].length * HINT_STEP) * 1000 + 200, rest);
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, demoPassword]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // How pale the change of strength makes things right now, and the colour it has reached.
  const pale = useDerivedValue(() => bump(shift.get(), 0, PALE_AT, SHIFT));
  const colour = useDerivedValue(() => {
    const { from, to } = tint.get();
    const u = smooth(shift.get(), COLOR_FROM, COLOR_TO);
    return [from[0] + (to[0] - from[0]) * u, from[1] + (to[1] - from[1]) * u, from[2] + (to[2] - from[2]) * u];
  });
  // 0: keyhole, 1: check. The shield goes pale once more while one becomes the other.
  const mark = useDerivedValue(() => smooth(secured.get(), CHECK_AT, CHECK_AT + CHECK_FOR) * (1 - smooth(leaving.get(), 0, 0.35)));
  const swap = useDerivedValue(() => bump(secured.get(), CHECK_AT - 0.1, CHECK_AT + 0.12, CHECK_AT + CHECK_FOR) * (leaving.get() > 0 ? 0 : 1));
  const glide = useDerivedValue(() =>
    reducedMotion ? 0 : smooth(secured.get(), GLIDE_AT, GLIDE_AT + GLIDE_FOR) * (1 - smooth(leaving.get(), 0.1, 0.9)),
  );

  const shieldColor = useDerivedValue(() => {
    const [r, g, b] = colour.get();
    const alpha = 1 - PALE.shield * Math.max(pale.get(), 0.75 * swap.get());
    return `rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${alpha})`;
  });
  const glowColors = useDerivedValue(() => {
    const [r, g, b] = colour.get();
    const paint = `${Math.round(r)},${Math.round(g)},${Math.round(b)}`;
    const strength = GLOW_ALPHA * (1 - PALE.glow * pale.get());
    return FALLOFF.map((f) => `rgba(${paint},${f * strength})`);
  });
  const shieldTransform = useDerivedValue(() => {
    const pop = reducedMotion ? 0 : POP * bump(secured.get(), CHECK_AT, CHECK_AT + CHECK_FOR * 0.45, CHECK_AT + CHECK_FOR + 0.15);
    return [{ translateY: drop * glide.get() }, { scale: 1 + pop }];
  });
  const glowTransform = useDerivedValue(() => {
    const breath = reducedMotion ? 0 : BREATH_BY * Math.sin((2 * Math.PI * time.get()) / BREATH);
    return [{ translateY: drop * glide.get() }, { scale: 1 + breath }];
  });
  const keyholeOpacity = useDerivedValue(() => 1 - smooth(mark.get(), 0, 0.5));
  const checkOpacity = useDerivedValue(() => smooth(mark.get(), 0.3, 0.6));

  // The label, the field and the hint: dimmed for a moment by a change of strength, gone once secured.
  const formStyle = useAnimatedStyle(() => {
    const s = secured.get();
    const there = s < 0 ? 1 : leaving.get() > 0 ? smooth(leaving.get(), 0.5, 1) : 1 - smooth(s, 0, FORM_OUT);
    return { opacity: there };
  });
  const dimStyle = useAnimatedStyle(() => ({ opacity: 1 - PALE.form * pale.get() }));
  const liftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: lift.get() }] }));
  const caretStyle = useAnimatedStyle(() => ({ opacity: time.get() % BLINK < BLINK / 2 ? 1 : 0 }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const inForm = phase === 'form';
  const centre = vec(size.width / 2, shieldY);
  const left = size.width / 2 - SHIELD.width / 2;
  const top = shieldY - SHIELD.height / 2;
  // The caret stands in the empty field, and after the text while the field is focused.
  const caret = inForm && (focused || password === '');

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Pressable style={styles.fill} onPress={() => Keyboard.dismiss()} accessible={false} />

      <Animated.View style={[styles.fill, liftStyle]} pointerEvents="box-none">
        <Canvas pointerEvents="none" style={styles.fill}>
          <Group transform={glowTransform} origin={centre}>
            <Circle c={centre} r={GLOW} dither>
              <RadialGradient c={centre} r={GLOW} colors={glowColors} positions={FALLOFF_AT} />
            </Circle>
          </Group>
          <Group transform={shieldTransform} origin={centre}>
            <Group transform={[{ translateX: left }, { translateY: top }]}>
              <Path path={OUTLINE} color={shieldColor}>
                <CornerPathEffect r={SHIELD.corner} />
              </Path>
              <Group opacity={keyholeOpacity}>
                <Circle cx={KEYHOLE.x} cy={KEYHOLE.y} r={KEYHOLE.radius} color="#FFFFFF" />
                <RoundedRect
                  x={KEYHOLE.x - KEYHOLE.stem / 2}
                  y={KEYHOLE.y}
                  width={KEYHOLE.stem}
                  height={KEYHOLE.bottom - KEYHOLE.y}
                  r={KEYHOLE.stem / 2}
                  color="#FFFFFF"
                />
              </Group>
              <Path
                path={CHECK}
                style="stroke"
                strokeWidth={CHECK_STROKE}
                strokeCap="round"
                strokeJoin="round"
                color="#FFFFFF"
                end={mark}
                opacity={checkOpacity}
              />
            </Group>
          </Group>
        </Canvas>

        {/* Once secured, a touch on the shield brings the form back. */}
        {phase === 'secured' && (
          <Pressable
            style={[styles.shieldTouch, { left: left - 16, top: size.height / 2 - SHIELD.height / 2 - 16 }]}
            accessibilityRole="button"
            accessibilityLabel="Change password"
            onPress={() => {
              touched();
              reset();
            }}
          />
        )}

        <Animated.View
          style={[styles.form, { top: shieldY + SHIELD_TO_FIELD - FIELD_HEIGHT / 2, width: fieldWidth }, formStyle]}
          pointerEvents={inForm ? 'auto' : 'none'}
        >
          <Animated.View style={dimStyle}>
            <Text style={styles.label}>{label}</Text>
            <Pressable
              style={[styles.field, level === 3 && { borderColor: `${colors[3]}73` }]}
              onPress={() => input.current?.focus()}
              accessible={false}
            >
              <View style={styles.text} pointerEvents="none">
                {revealed ? (
                  <Text style={styles.letters} numberOfLines={1}>
                    {password}
                  </Text>
                ) : (
                  password.split('').map((_, index) => <Animated.View key={index} entering={DOT_IN} style={styles.dot} />)
                )}
                {caret && <Animated.View style={[styles.caret, caretStyle]} />}
              </View>
              {/* The real field takes the typing; what it holds is drawn by the row above. */}
              <TextInput
                ref={input}
                value={password}
                onChangeText={(text) => {
                  touched();
                  change(text);
                }}
                onFocus={() => {
                  touched();
                  setFocused(true);
                }}
                onBlur={() => setFocused(false)}
                onSubmitEditing={() => {
                  touched();
                  secure(true);
                }}
                style={styles.input}
                secureTextEntry
                caretHidden
                selectionColor="transparent"
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="new-password"
                textContentType="newPassword"
                returnKeyType="done"
                maxLength={24}
                accessibilityLabel={label}
              />
              <Pressable
                hitSlop={8}
                style={styles.eye}
                accessibilityRole="button"
                accessibilityLabel={revealed ? 'Hide password' : 'Show password'}
                onPress={() => {
                  touched();
                  setRevealed((on) => !on);
                }}
              >
                <Ionicons name={revealed ? 'eye' : 'eye-off'} size={ICONS} color={ICON} />
              </Pressable>
              <Ionicons name="lock-closed" size={ICONS - 1} color={ICON} style={styles.lock} />
            </Pressable>
          </Animated.View>
          <Text style={[styles.hint, { color: hintColors[hint.level] }]} numberOfLines={1} accessibilityLiveRegion="polite">
            {hints[hint.level].slice(0, hint.count)}
          </Text>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  shieldTouch: {
    position: 'absolute',
    width: SHIELD.width + 32,
    height: SHIELD.height + 32,
  },
  form: {
    position: 'absolute',
    alignSelf: 'center',
  },
  label: {
    position: 'absolute',
    left: 0,
    bottom: FIELD_HEIGHT + LABEL_GAP,
    color: INK,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '700',
  },
  field: {
    height: FIELD_HEIGHT,
    borderRadius: FIELD_HEIGHT / 2,
    backgroundColor: FIELD,
    borderWidth: 1,
    borderColor: 'transparent',
    flexDirection: 'row',
    alignItems: 'center',
    boxShadow: [
      { offsetX: 0, offsetY: 4, blurRadius: 14, color: 'rgba(100,116,139,0.14)' },
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(100,116,139,0.08)' },
    ],
  },
  text: {
    flex: 1,
    height: FIELD_HEIGHT,
    marginLeft: TEXT_LEFT - 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: DOT_GAP,
    overflow: 'hidden',
  },
  dot: {
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: DOT,
  },
  letters: {
    color: DOT,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
  },
  caret: {
    width: 1.5,
    height: 15,
    borderRadius: 0.75,
    backgroundColor: CARET,
  },
  input: {
    position: 'absolute',
    left: TEXT_LEFT,
    right: 72,
    top: 0,
    bottom: 0,
    padding: 0,
    color: 'transparent',
    fontSize: 14,
  },
  eye: {
    width: 26,
    height: FIELD_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  lock: {
    marginLeft: 1,
    marginRight: 13,
  },
  hint: {
    marginTop: HINT_GAP,
    height: HINT_HEIGHT,
    fontSize: 10,
    lineHeight: HINT_HEIGHT,
  },
});
