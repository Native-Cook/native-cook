import { Blur, Canvas, LinearGradient, RoundedRect, SweepGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as Fade } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Rect, Stop } from 'react-native-svg';

type Props = {
  /** Controlled text; leave it out and the bar keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Types `prompt`, sends it and starts over, without ever opening the keyboard. */
  autoPlay?: boolean;
  greeting?: string;
  title?: string;
  /** The grey word in front of the model picker. */
  lead?: string;
  placeholder?: string;
  /** A tap on the picker moves to the next of these. */
  models?: string[];
  chips?: string[];
  /** What autoPlay types; about thirty characters fit the field. */
  prompt?: string;
  /** Colour behind the bar as `#rrggbb`: the chip row fades into it at its right end. */
  backdrop?: string;
  /** Called with the text when the send button or the keyboard's send key is pressed. */
  onSend?: (text: string) => void;
  onModelChange?: (model: string) => void;
  onChip?: (chip: string) => void;
  onAttach?: () => void;
  onTools?: () => void;
  onMic?: () => void;
};

const WIDTH = 350;
const HEIGHT = 92;
const RADIUS = 22;
// The canvas overflows the field by this much on every side to fit the bloom.
const BLEED = 44;
// The greeting lines up with the field's text, not with its border.
const INSET = 16;
const SEND = 34;
// Width of the fade at the right end of the chip row.
const FADE = 40;

// Seconds the light takes to go around once at rest.
const LAP = 3.5;
// Extra laps a sent prompt adds, and the seconds they take.
const BOOST_LAPS = 1;
const BOOST = 1.6;
// Where the hot spot stays under reduced motion, in laps from the middle of the top edge.
const STILL_LAP = 0.07;
// Sideways stretch of the light's gradient: the square root of the field's proportions.
const STRETCH = Math.sqrt(WIDTH / HEIGHT);
// How much of the bloom inside the field is covered again.
const VEIL = 0.5;

// autoPlay (seconds): the prompt is typed, sent, and the bar rests until the loop closes.
// The loop is a whole number of laps, so the light is back where it started.
const LOOP = 7;
const TYPE_AT = 0.8;
// The typing never takes longer than this, however long the prompt.
const TYPE_SPAN = 2;
const SEND_AT = 3.25;
const PRESS = 0.13;
// After a touch the script waits this long, then starts again from rest.
const RESUME_AFTER = 4;
// Seconds between two letters of the typed prompt: uneven, and the same every loop.
const TYPE_GAPS = [0.07, 0.055, 0.09, 0.06, 0.075, 0.05, 0.11, 0.065];

const GHOST = 0.28;

// The send button lights up with a little overshoot.
const POP = { mass: 1, stiffness: 320, damping: 18 };

const FIELD = ['#242424', '#1e1e1e'];
const RIM = '#3d3547';
const LILAC = '#d9a8ff';
const PINK = '#ff7ad9';
const VIOLET = '#a855f7';
const CLEAR = 'rgba(168,85,247,0)';

// Sweep positions run clockwise from 3 o'clock. The hot spot sits at 0.5 with a short lilac nose
// ahead of it and a long pink and violet tail behind; a dimmer pink streak runs on the opposite side.
const LIGHT = [PINK, 'rgba(168,85,247,0.55)', CLEAR, CLEAR, 'rgba(168,85,247,0.7)', PINK, '#ffffff', '#ffffff', LILAC, CLEAR, CLEAR, 'rgba(168,85,247,0.5)', PINK];
const LIGHT_STOPS = [0, 0.025, 0.06, 0.33, 0.37, 0.43, 0.475, 0.505, 0.525, 0.56, 0.88, 0.93, 1];

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function smooth(t: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, t));
  return u * u * (3 - 2 * u);
}

/** 0…1 over time: up in `rise` seconds, held, then down in `fall`. */
function envelope(t: number, rise: number, hold: number, fall: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t < rise) return smooth(t / rise);
  return 1 - smooth((t - rise - hold) / fall);
}

/**
 * Direction, seen from the centre of the field, of the point `laps` of the way around its outline
 * (clockwise from the middle of the top edge). Turning the gradient by this angle instead of a
 * steady one makes the light travel at a constant speed along the border.
 */
function angleAt(laps: number) {
  'worklet';
  const halfW = WIDTH / 2;
  const halfH = HEIGHT / 2;
  const d = (laps - Math.floor(laps)) * 2 * (WIDTH + HEIGHT);
  let x = -halfW;
  let y = -halfH;
  if (d < halfW) {
    x = d;
  } else if (d < halfW + HEIGHT) {
    x = halfW;
    y = d - halfW - halfH;
  } else if (d < halfW + HEIGHT + WIDTH) {
    x = halfW - (d - halfW - HEIGHT);
    y = halfH;
  } else if (d < halfW + 2 * HEIGHT + WIDTH) {
    y = halfH - (d - halfW - HEIGHT - WIDTH);
  } else {
    x = d - 2 * HEIGHT - WIDTH - 3 * halfW;
  }
  // Measured in the gradient's stretched space.
  return Math.atan2(y, x / STRETCH);
}

/** When each letter of the prompt appears, in seconds from the start of the loop. */
function keyTimes(prompt: string) {
  const at: number[] = [];
  let t = 0;
  for (let index = 0; index < prompt.length; index++) {
    at.push(t);
    t += TYPE_GAPS[index % TYPE_GAPS.length];
  }
  const scale = Math.min(1, TYPE_SPAN / Math.max(t, 0.001));
  return at.map((seconds) => TYPE_AT + seconds * scale);
}

/** The drawn caret stays lit for a moment after a letter, then blinks once a second. */
function caretOpacity(sinceKey: number) {
  'worklet';
  return sinceKey % 1 < 0.55 ? 1 : 0;
}

type Light = {
  time: SharedValue<number>;
  flareAt: SharedValue<number>;
  boostAt: SharedValue<number>;
  still: boolean;
};

/** The field and its border: the travelling light, the flare of a new prompt, the lap of a sent one. */
function Border({ time, flareAt, boostAt, still }: Light) {
  const x = BLEED;
  const y = BLEED;
  const c = vec(x + WIDTH / 2, y + HEIGHT / 2);
  const top = vec(x + WIDTH / 2, y);
  const bottom = vec(x + WIDTH / 2, y + HEIGHT);

  // The gradient is drawn in a space stretched sideways, so its streaks keep a similar length on
  // the long and the short edges. Whole laps bring the hot spot back to the middle of the top edge.
  const spin = useDerivedValue(() => {
    const laps = still ? STILL_LAP : time.get() / LAP + BOOST_LAPS * smooth((time.get() - boostAt.get()) / BOOST);
    return [{ scaleX: STRETCH }, { rotate: angleAt(laps) - Math.PI }];
  });
  const flare = useDerivedValue(() => envelope(time.get() - flareAt.get(), 0.16, 0.45, 0.8));
  const halo = useDerivedValue(() => flare.get() * 0.8);
  const boost = useDerivedValue(() => envelope(time.get() - boostAt.get(), 0.25, 0.9, 0.45));

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient start={top} end={bottom} colors={FIELD} />
      </RoundedRect>

      {/* The blurs are filters (applied after shading), so the bloom follows the border instead of smearing into an angular wedge. */}
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} style="stroke" strokeWidth={5} opacity={0.85}>
        <SweepGradient c={c} origin={c} transform={spin} colors={LIGHT} positions={LIGHT_STOPS} />
        <Blur blur={7} />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} style="stroke" strokeWidth={9} opacity={boost}>
        <SweepGradient c={c} origin={c} transform={spin} colors={LIGHT} positions={LIGHT_STOPS} />
        <Blur blur={14} />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} style="stroke" strokeWidth={10} color={VIOLET} opacity={halo}>
        <Blur blur={16} />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} style="stroke" strokeWidth={5} color={LILAC} opacity={flare}>
        <Blur blur={6} />
      </RoundedRect>

      {/* A veil of the field's own colour keeps the bloom that spills inwards quieter than the one outside. */}
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} opacity={VEIL}>
        <LinearGradient start={top} end={bottom} colors={FIELD} />
      </RoundedRect>

      <RoundedRect x={x + 0.75} y={y + 0.75} width={WIDTH - 1.5} height={HEIGHT - 1.5} r={RADIUS - 0.75} style="stroke" strokeWidth={1.5} color={RIM} />
      <RoundedRect x={x + 1} y={y + 1} width={WIDTH - 2} height={HEIGHT - 2} r={RADIUS - 1} style="stroke" strokeWidth={2}>
        <SweepGradient c={c} origin={c} transform={spin} colors={LIGHT} positions={LIGHT_STOPS} />
      </RoundedRect>
      <RoundedRect x={x + 1} y={y + 1} width={WIDTH - 2} height={HEIGHT - 2} r={RADIUS - 1} style="stroke" strokeWidth={2} color="#ffffff" opacity={flare} />
    </Canvas>
  );
}

function Mark() {
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      <Defs>
        <SvgGradient id="mark" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor={LILAC} />
          <Stop offset="1" stopColor={VIOLET} />
        </SvgGradient>
      </Defs>
      <Path d="M10 0C10.9 6 14 9.1 20 10C14 10.9 10.9 14 10 20C9.1 14 6 10.9 0 10C6 9.1 9.1 6 10 0Z" fill="url(#mark)" />
    </Svg>
  );
}

function Stroke({ d, width, size = 18, color = '#ffffff', children }: { d: string; width: number; size?: number; color?: string; children?: ReactNode }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} fill="none">
      {children}
      <Path d={d} stroke={color} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

const PLUS = 'M9 3.5V14.5M3.5 9H14.5';
const SLIDERS = 'M3 6H8.2M13.2 6H15M3 12H4.8M9.8 12H15';
const MIC = 'M13.8 8.6A4.8 4.8 0 0 1 4.2 8.6M9 13.4V16';
const CHEVRON = 'M2 3.5L5 6.5L8 3.5';
const ARROW = 'M9 15.5V2.5M3.8 7.7L9 2.5L14.2 7.7';

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A tappable part of the bar: dips while pressed (or while the script presses it), with a light haptic. */
function Tap({
  scale,
  label,
  onPress,
  held = false,
  style,
  children,
}: {
  scale: number;
  label: string;
  onPress?: () => void;
  held?: boolean;
  style?: ViewStyle;
  children: ReactNode;
}) {
  return (
    <Pressable style={style} onPress={onPress} onPressIn={tick} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          style={{
            transform: [{ scale: pressed || held ? scale : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          }}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * Dark assistant chat bar whose border is alive: a white hot spot with pink and violet tails keeps
 * travelling around the rounded field, the whole border flares when a prompt starts, and the light
 * runs a faster, brighter lap after it is sent. The model picker sits inside the placeholder
 * sentence, the mic gives way to the send button once there is text, and the chips fill the field.
 */
export function GlowBorderChatbar({
  value,
  onChangeText,
  autoPlay = false,
  greeting = 'Hi Alex',
  title = 'What’s the opening move?',
  lead = 'Ask',
  placeholder = 'anything...',
  models = ['Swift', 'Balanced', 'Deep'],
  chips = ['Create image', 'Compose music', 'Help me learn', 'Boost my day'],
  prompt = 'Plan a weekend in Lisbon',
  backdrop = '#000000',
  onSend,
  onModelChange,
  onChip,
  onAttach,
  onTools,
  onMic,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [draft, setDraft] = useState('');
  const [model, setModel] = useState(0);
  const [previous, setPrevious] = useState(0);
  // False once a touch has taken the field from the autoPlay script.
  const [handsOff, setHandsOff] = useState(true);
  const [scriptPress, setScriptPress] = useState(false);
  // The text that was just sent, on its way out.
  const [ghost, setGhost] = useState('');
  const input = useRef<TextInput>(null);
  const focused = useRef(false);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const playing = auto && handsOff;
  const text = value ?? draft;
  const hasText = text.length > 0;
  const modelName = models.length ? models[model % models.length] : '';
  const previousName = models.length ? models[previous % models.length] : '';

  // Seconds since the bar appeared, and the moments its border and overlays react to.
  const time = useSharedValue(0);
  const flareAt = useSharedValue(-10);
  const boostAt = useSharedValue(-10);
  const keyAt = useSharedValue(-10);
  const sentAt = useSharedValue(-10);
  const arm = useSharedValue(0);
  const roll = useSharedValue(1);

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  });

  useEffect(() => {
    // The border flares whenever a prompt begins, whoever types it.
    if (hasText) flareAt.set(time.get());
    arm.set(reducedMotion ? withTiming(hasText ? 1 : 0, { duration: 150 }) : withSpring(hasText ? 1 : 0, POP));
  }, [hasText, reducedMotion, arm, flareAt, time]);

  useEffect(() => {
    if (!playing) return;
    const keys = keyTimes(prompt);
    const timers: ReturnType<typeof setTimeout>[] = [];
    const epoch = Date.now();
    const run = (lap: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, epoch + (lap * LOOP + seconds) * 1000 - Date.now())));
      };
      keys.forEach((seconds, index) =>
        at(seconds, () => {
          setDraft(prompt.slice(0, index + 1));
          keyAt.set(time.get());
        }),
      );
      at(SEND_AT, () => setScriptPress(true));
      at(SEND_AT + PRESS, () => {
        setScriptPress(false);
        setGhost(prompt);
        sentAt.set(time.get());
        boostAt.set(time.get());
        setDraft('');
      });
      at(LOOP, () => {
        timers.length = 0;
        run(lap + 1);
      });
    };
    run(0);
    return () => timers.forEach(clearTimeout);
  }, [playing, prompt, boostAt, keyAt, sentAt, time]);

  useEffect(
    () => () => {
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    },
    [],
  );

  /** Any touch stops the script; it comes back a few seconds after the last one. */
  const touch = () => {
    setHandsOff(false);
    setScriptPress(false);
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    if (!auto) return;
    resumeTimer.current = setTimeout(() => {
      if (focused.current) return;
      setDraft('');
      setHandsOff(true);
    }, RESUME_AFTER * 1000);
  };

  const edit = (next: string) => {
    if (!controlled) setDraft(next);
    onChangeText?.(next);
  };

  /** Clears the field with the sent text lifting away, and sends the light on its fast lap. */
  const send = () => {
    if (!hasText) return;
    touch();
    setGhost(text);
    sentAt.set(time.get());
    boostAt.set(time.get());
    edit('');
    input.current?.blur();
    onSend?.(text);
  };

  const nextModel = () => {
    if (!models.length) return;
    touch();
    const next = (model + 1) % models.length;
    setPrevious(model);
    setModel(next);
    // The next name rolls in from below as the old one leaves upwards.
    roll.set(0);
    roll.set(withTiming(1, { duration: 180, easing: EASE_OUT }));
    onModelChange?.(models[next]);
  };

  const rollIn = useAnimatedStyle(() => ({
    opacity: roll.get(),
    transform: [{ translateY: reducedMotion ? 0 : 24 * (1 - roll.get()) }],
  }));
  const rollOut = useAnimatedStyle(() => ({
    opacity: 1 - roll.get(),
    transform: [{ translateY: reducedMotion ? 0 : -24 * roll.get() }],
  }));
  // It makes way at once, and comes back after the sent text has lifted off.
  const placeholderStyle = {
    opacity: hasText ? 0 : 1,
    transitionProperty: 'opacity' as const,
    transitionDuration: hasText ? 0 : 180,
    transitionDelay: hasText ? 0 : 150,
    transitionTimingFunction: 'ease-out' as const,
  };
  const ghostStyle = useAnimatedStyle(() => {
    const sent = Math.min(1, Math.max(0, (time.get() - sentAt.get()) / GHOST));
    const out = 1 - Math.pow(1 - sent, 3);
    return { opacity: 1 - out, transform: [{ translateY: reducedMotion ? 0 : -10 * out }] };
  });
  const caretStyle = useAnimatedStyle(() => ({ opacity: caretOpacity(time.get() - keyAt.get()) }));
  // The mic steps aside as soon as there is something to send, and the send button lights up.
  const micStyle = useAnimatedStyle(() => {
    const on = Math.min(1, Math.max(0, arm.get()));
    return {
      opacity: 1 - on,
      transform: [{ translateX: reducedMotion ? 0 : 12 * arm.get() }, { scale: 1 - 0.4 * arm.get() }],
    };
  });
  const litStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, arm.get())),
    transform: [{ scale: 0.6 + 0.4 * arm.get() }],
  }));

  return (
    <View style={styles.root}>
      <View style={styles.greeting}>
        <Mark />
        <Text style={styles.greetingText} numberOfLines={1}>
          {greeting}
        </Text>
      </View>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>

      <View style={styles.bar}>
        <View pointerEvents="none" style={styles.surface}>
          <Border time={time} flareAt={flareAt} boostAt={boostAt} still={reducedMotion} />
        </View>

        <View style={styles.line}>
          <Text style={styles.lead} numberOfLines={1}>
            {lead}
          </Text>
          <Tap scale={0.96} label={`Model: ${modelName}`} onPress={nextModel} style={styles.pickerSlot}>
            <View style={styles.picker}>
              <View>
                <Animated.Text style={[styles.model, rollIn]} numberOfLines={1}>
                  {modelName}
                </Animated.Text>
                <Animated.Text style={[styles.model, styles.modelOut, rollOut]} numberOfLines={1}>
                  {previousName}
                </Animated.Text>
              </View>
              <Stroke d={CHEVRON} width={1.7} size={10} />
            </View>
          </Tap>

          <View style={styles.input}>
            <Animated.Text style={[styles.placeholder, placeholderStyle]} numberOfLines={1}>
              {placeholder}
            </Animated.Text>
            <TextInput
              ref={input}
              style={styles.text}
              value={text}
              onChangeText={(next) => {
                touch();
                edit(next);
              }}
              onFocus={() => {
                // A tap on the field takes it over from the script and starts from empty.
                if (playing) setDraft('');
                focused.current = true;
                touch();
              }}
              onBlur={() => {
                focused.current = false;
                touch();
              }}
              onSubmitEditing={send}
              returnKeyType="send"
              submitBehavior="blurAndSubmit"
              selectionColor={LILAC}
              cursorColor={LILAC}
              keyboardAppearance="dark"
              underlineColorAndroid="transparent"
              accessibilityLabel={`${lead} ${modelName} ${placeholder}`}
            />
            <View pointerEvents="none" style={styles.overlay}>
              <Animated.Text style={[styles.ghost, ghostStyle]} numberOfLines={1}>
                {ghost}
              </Animated.Text>
              {playing && hasText && (
                // The script does not focus the field, so it draws the caret itself: an invisible
                // copy of the text pushes it to the end of the line.
                <View style={styles.caretRow}>
                  <Text style={styles.sizer} numberOfLines={1}>
                    {text}
                  </Text>
                  <Animated.View style={[styles.caret, caretStyle]} />
                </View>
              )}
            </View>
          </View>
        </View>

        <View style={styles.tools}>
          <Tap scale={0.9} label="Attach" onPress={onAttach} style={styles.icon}>
            <Stroke d={PLUS} width={1.7} />
          </Tap>
          <Tap scale={0.9} label="Tools" onPress={onTools} style={styles.icon}>
            <Stroke d={SLIDERS} width={1.5}>
              <Circle cx={10.7} cy={6} r={2.1} stroke="#ffffff" strokeWidth={1.5} />
              <Circle cx={7.3} cy={12} r={2.1} stroke="#ffffff" strokeWidth={1.5} />
            </Stroke>
          </Tap>
        </View>

        <Animated.View style={[styles.mic, micStyle]} pointerEvents={hasText ? 'none' : 'auto'}>
          <Tap scale={0.9} label="Dictate" onPress={onMic} style={styles.icon}>
            <Stroke d={MIC} width={1.5}>
              <Rect x={6.4} y={2} width={5.2} height={8.6} rx={2.6} fill="#ffffff" />
            </Stroke>
          </Tap>
        </Animated.View>

        <Tap scale={0.9} label="Send" onPress={send} held={scriptPress} style={styles.send}>
          <View style={styles.sendIdle}>
            <Stroke d={ARROW} width={2.1} color="rgba(255,255,255,0.55)" />
            <Animated.View style={[styles.sendLit, litStyle]}>
              <Stroke d={ARROW} width={2.1} color="#000000" />
            </Animated.View>
          </View>
        </Tap>
      </View>

      <View style={styles.chips}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.chipRow}>
          {chips.map((chip) => (
            <Tap
              key={chip}
              scale={0.96}
              label={chip}
              onPress={() => {
                touch();
                edit(chip);
                onChip?.(chip);
              }}
            >
              <View style={styles.chip}>
                <Text style={styles.chipText} numberOfLines={1}>
                  {chip}
                </Text>
              </View>
            </Tap>
          ))}
        </ScrollView>
        <Fade pointerEvents="none" colors={[`${backdrop}00`, backdrop]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={styles.fade} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
  },
  greeting: {
    height: 24,
    paddingLeft: INSET,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  greetingText: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
  },
  title: {
    marginTop: 6,
    paddingLeft: INSET,
    color: '#ffffff',
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
  bar: {
    marginTop: 14,
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  line: {
    position: 'absolute',
    left: 16,
    top: 14,
    width: WIDTH - 32,
    height: 24,
    flexDirection: 'row',
    alignItems: 'center',
  },
  lead: {
    marginRight: 6,
    color: '#8a8a8a',
    fontSize: 15,
    lineHeight: 20,
  },
  pickerSlot: {
    marginRight: 8,
  },
  picker: {
    height: 24,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    overflow: 'hidden',
  },
  model: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  modelOut: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  input: {
    flex: 1,
    height: 24,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    color: '#6e6e6e',
    fontSize: 15,
    lineHeight: 20,
  },
  text: {
    height: 24,
    padding: 0,
    color: '#ffffff',
    fontSize: 15,
  },
  overlay: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'center',
  },
  ghost: {
    position: 'absolute',
    left: 0,
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 20,
  },
  caretRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  sizer: {
    opacity: 0,
    fontSize: 15,
    lineHeight: 20,
  },
  caret: {
    width: 2,
    height: 18,
    borderRadius: 1,
    backgroundColor: LILAC,
  },
  tools: {
    position: 'absolute',
    left: 9,
    top: HEIGHT - 10 - 33,
    flexDirection: 'row',
    gap: 2,
  },
  icon: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mic: {
    position: 'absolute',
    left: WIDTH - 10 - SEND - 6 - 32,
    top: HEIGHT - 10 - 33,
  },
  send: {
    position: 'absolute',
    left: WIDTH - 10 - SEND,
    top: HEIGHT - 10 - SEND,
  },
  sendIdle: {
    width: SEND,
    height: SEND,
    borderRadius: SEND / 2,
    backgroundColor: 'rgba(255,255,255,0.14)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendLit: {
    ...StyleSheet.absoluteFill,
    borderRadius: SEND / 2,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  chips: {
    marginTop: 12,
    width: WIDTH,
    height: 36,
  },
  chipRow: {
    gap: 10,
    // Room for the last chip to clear the fade once the row is scrolled to its end.
    paddingRight: FADE,
  },
  chip: {
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    backgroundColor: '#1c1c1c',
    justifyContent: 'center',
  },
  chipText: {
    color: '#7a7a7a',
    fontSize: 14,
    lineHeight: 18,
  },
  fade: {
    position: 'absolute',
    right: 0,
    top: 0,
    width: FADE,
    height: 36,
  },
});
