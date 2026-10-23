import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path as SkiaPath,
  RadialGradient,
  RoundedRect,
  Skia,
  SweepGradient,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as ViewGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export type ModeIcon = 'bolt' | 'scales' | 'bulb' | 'flask';

export type Mode = {
  /** The name in the menu, and the shorter one on the pill. */
  label: string;
  short: string;
  icon: ModeIcon;
  /** The colours this mode gives the glows and the border: left end and right end. */
  warm: string;
  cool: string;
};

type Props = {
  /** The field's text when the parent owns it. Leave it out and the composer keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /**
   * Changes mode, types `script` and sends it by itself in a loop, without the keyboard, until
   * the composer is touched.
   */
  autoPlay?: boolean;
  placeholder?: string;
  /** What autoPlay types. */
  script?: string;
  modes?: Mode[];
  /** The mode at rest. autoPlay goes from it to the next one and back. */
  initialMode?: number;
  /** The tone pill steps through these. */
  tones?: string[];
  /** Called with the trimmed text and the index of the mode. */
  onSend?: (text: string, mode: number) => void;
  onModeChange?: (mode: number) => void;
  onToneChange?: (tone: number) => void;
  onAdd?: () => void;
  onVoice?: () => void;
};

const WIDTH = 350;
const HEIGHT = 100;
const RADIUS = 22;
// The canvas of the glows overflows the composer by this much on every side.
const BLEED = 160;
const CONTROL = 32;
const PILL = 30;
// Height of one line in a pill's rolling label.
const ROLL_H = 18;
const MENU_W = 172;
const ROW_H = 34;
const BLINK = 530;
const PLACEHOLDER_DELAY = 200;
// The light on the box's rim: a stroke this wide, blurred by this much.
const RIM_WIDTH = 7;
const RIM_BLUR = 7;
// The glows swell and fade over this many seconds, and sway over twice that.
const BREATH_PERIOD = 5;
const SWAY_PERIOD = 10;
// How long the colours take to change, and the light to run around the border (milliseconds).
const SHIFT = 600;
const LAP = 1200;
// The light starts at the send button: degrees clockwise from three o'clock.
const LAP_START = 16;
const DOT_STEP = 13;

const INK = '#e6e8ee';
const LABEL = '#c9cdd6';
const CHIP = '#12141b';
const EDGE = '#2a2d38';

const ROLL_SPRING = { mass: 1, stiffness: 300, damping: 24 };
const MENU_SPRING = { mass: 1, stiffness: 380, damping: 26 };

// autoPlay loop, in seconds from its start.
const OPEN_AT = 0.8;
// A scripted tap holds its button down this long before it acts.
const PRESS_LEAD = 0.15;
// After the pill is pressed: when the finger rests on a row, and when it chooses it.
const POINT_AFTER = 0.9;
const CHOOSE_AFTER = 1.15;
const FOCUS_AT = 2.9;
const TYPE_AT = 3.2;
// The longest the typing may take; a longer script is typed faster.
const TYPE_SPAN = 2.2;
const SEND_AT = 5.9;
const REOPEN_AT = 7.3;
const LOOP = 10;
// After the last touch, the loop waits this long before it starts again.
const RESUME = 3;

// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
const RHYTHM = [90, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90];

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN = Easing.bezier(0.42, 0, 1, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const QUICK = { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const CSS_EASE_OUT_STRONG = cubicBezier(0.23, 1, 0.32, 1);

// Outlines in 16 pt boxes (the chevron in a 10 pt one), the same strings in the three stacks.
const PLUS = 'M 8 3.6 L 8 12.4 M 3.6 8 L 12.4 8';
const FEATHER =
  'M 12.8 3.2 C 9.6 2.6 6.6 3.8 5.4 6.4 C 4.8 7.8 4.9 9.4 5.4 10.6 L 8.6 10.6 C 10.6 10.2 12.2 8.8 12.8 6.8 C 13.2 5.6 13.2 4.4 12.8 3.2 Z M 2.8 13.2 L 9.6 6.4';
const WAVE = 'M 2.8 6.6 L 2.8 9.4 M 5.4 4.6 L 5.4 11.4 M 8 2.8 L 8 13.2 M 10.6 5.2 L 10.6 10.8 M 13.2 6.8 L 13.2 9.2';
const PLANE = 'M 13.6 2.4 L 2.4 6.9 L 6.9 9.1 L 9.1 13.6 Z';
const CHECK = 'M 3.4 8.4 L 6.6 11.4 L 12.6 4.8';
const CHEVRON = 'M 2.5 3.8 L 5 6.3 L 7.5 3.8';
const MODE_ICONS: Record<ModeIcon, string> = {
  bolt: 'M 9 1.8 L 3.8 9 L 7.6 9 L 7 14.2 L 12.2 7 L 8.4 7 Z',
  scales:
    'M 8 2.6 L 8 13.4 M 4.8 13.4 L 11.2 13.4 M 3.2 4.6 L 12.8 4.6 M 1.6 9.2 L 3.4 4.8 L 5.2 9.2 C 5.2 10.03 4.39 10.7 3.4 10.7 C 2.41 10.7 1.6 10.03 1.6 9.2 M 10.8 9.2 L 12.6 4.8 L 14.4 9.2 C 14.4 10.03 13.59 10.7 12.6 10.7 C 11.61 10.7 10.8 10.03 10.8 9.2',
  bulb: 'M 6 10.4 C 4.6 9.6 3.8 8.2 3.8 6.6 C 3.8 4.3 5.7 2.4 8 2.4 C 10.3 2.4 12.2 4.3 12.2 6.6 C 12.2 8.2 11.4 9.6 10 10.4 L 10 11.6 L 6 11.6 Z M 6.6 13.8 L 9.4 13.8',
  flask:
    'M 6 2.4 L 10 2.4 M 6.8 2.4 L 6.8 6.4 L 3.4 12 C 2.9 12.9 3.5 13.8 4.5 13.8 L 11.5 13.8 C 12.5 13.8 13.1 12.9 12.6 12 L 9.2 6.4 L 9.2 2.4 M 5 10.2 L 11 10.2',
};

const DEFAULT_MODES: Mode[] = [
  { label: 'Quick answer', short: 'Quick', icon: 'bolt', warm: '#ffb648', cool: '#ff5c8a' },
  { label: 'Balanced', short: 'Balanced', icon: 'scales', warm: '#ff7a59', cool: '#4d6bff' },
  { label: 'Deep think', short: 'Deep think', icon: 'bulb', warm: '#9b6bff', cool: '#2fd3ff' },
  { label: 'Research', short: 'Research', icon: 'flask', warm: '#54e08a', cool: '#19c7c0' },
];
const DEFAULT_TONES = ['Normal', 'Concise', 'Formal', 'Playful'];

// The grid of dots behind the composer, in the canvas's coordinates.
const DOTS = (() => {
  const path = Skia.Path.Make();
  for (let y = DOT_STEP / 2; y < HEIGHT + BLEED * 2; y += DOT_STEP) {
    for (let x = DOT_STEP / 2; x < WIDTH + BLEED * 2; x += DOT_STEP) path.addCircle(x, y, 1);
  }
  return path;
})();

const BOX = { x: BLEED, y: BLEED, width: WIDTH, height: HEIGHT };
const CENTER = vec(BLEED + WIDTH / 2, BLEED + HEIGHT / 2);
const ACROSS = { start: vec(BLEED, BLEED + HEIGHT / 2), end: vec(BLEED + WIDTH, BLEED + HEIGHT / 2) };
const GLINT = ['rgba(255,255,255,0)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0.55)', '#ffffff'];

/** A colour as its three channels, 0…1, so two of them can be mixed frame by frame. */
function channels(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [((value >> 16) & 0xff) / 255, ((value >> 8) & 0xff) / 255, (value & 0xff) / 255];
}

function mix(from: number[], to: number[], share: number) {
  'worklet';
  return [from[0] + (to[0] - from[0]) * share, from[1] + (to[1] - from[1]) * share, from[2] + (to[2] - from[2]) * share];
}

function tinted(tint: number[], alpha: number) {
  'worklet';
  return [tint[0], tint[1], tint[2], alpha];
}

/** Halfway to white: the colour of what a glow lights up. */
function pale(tint: number[]) {
  'worklet';
  return mix(tint, [1, 1, 1], 0.55);
}

/** The moment each letter of the script lands, in seconds from the start of the loop. */
function keyTimes(count: number) {
  const sums: number[] = [];
  let total = 0;
  for (let i = 0; i < count; i++) {
    total += RHYTHM[i % RHYTHM.length] / 1000;
    sums.push(total);
  }
  const pace = total > TYPE_SPAN ? TYPE_SPAN / total : 1;
  return sums.map((sum) => TYPE_AT + sum * pace);
}

/** A stroked outline. */
function Glyph({ d, color = LABEL, size = 16 }: { d: string; color?: string; size?: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} stroke={color} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

type AuraProps = {
  /** Seconds since the composer appeared. */
  clock: SharedValue<number>;
  /** The colours the glows are leaving and heading for, and how far the change has come. */
  fromWarm: SharedValue<number[]>;
  fromCool: SharedValue<number[]>;
  toWarm: SharedValue<number[]>;
  toCool: SharedValue<number[]>;
  shift: SharedValue<number>;
  /** The light that runs around the border after a send: 0 at the start of its lap, 1 at the end. */
  lap: SharedValue<number>;
};

/** Glows, the dots they light up, the box, its border and the light running around it. */
function Aura({ clock, fromWarm, fromCool, toWarm, toCool, shift, lap }: AuraProps) {
  const warm = useDerivedValue(() => mix(fromWarm.get(), toWarm.get(), shift.get()));
  const cool = useDerivedValue(() => mix(fromCool.get(), toCool.get(), shift.get()));
  // The glows breathe against each other and sway a little.
  const breath = useDerivedValue(() => 0.5 + 0.5 * Math.sin((clock.get() * Math.PI * 2) / BREATH_PERIOD));
  const sway = useDerivedValue(() => Math.sin((clock.get() * Math.PI * 2) / SWAY_PERIOD));
  const bob = useDerivedValue(() => Math.cos((clock.get() * Math.PI * 2) / BREATH_PERIOD));

  const warmAt = useDerivedValue(() => vec(BOX.x + 30 + 8 * sway.get(), BOX.y + 16 + 5 * bob.get()));
  const warmRadius = useDerivedValue(() => 128 * (0.94 + 0.12 * breath.get()));
  const warmGlow = useDerivedValue(() => {
    const strength = 0.6 + 0.2 * breath.get();
    return [tinted(warm.get(), strength), tinted(warm.get(), strength * 0.38), tinted(warm.get(), 0)];
  });
  const warmDots = useDerivedValue(() => {
    const strength = 0.6 + 0.2 * breath.get();
    return [tinted(pale(warm.get()), 0.55 * strength), tinted(pale(warm.get()), 0)];
  });

  const coolAt = useDerivedValue(() => vec(BOX.x + WIDTH - 30 - 8 * sway.get(), BOX.y + HEIGHT - 16 - 5 * bob.get()));
  const coolRadius = useDerivedValue(() => 138 * (1.06 - 0.12 * breath.get()));
  const coolGlow = useDerivedValue(() => {
    const strength = 0.8 - 0.2 * breath.get();
    return [tinted(cool.get(), strength), tinted(cool.get(), strength * 0.38), tinted(cool.get(), 0)];
  });
  const coolDots = useDerivedValue(() => {
    const strength = 0.8 - 0.2 * breath.get();
    return [tinted(pale(cool.get()), 0.55 * strength), tinted(pale(cool.get()), 0)];
  });

  const rim = useDerivedValue(() => [tinted(warm.get(), 0.95), tinted(warm.get(), 0), tinted(cool.get(), 0), tinted(cool.get(), 0.95)]);
  const border = useDerivedValue(() => [tinted(warm.get(), 1), [0.851, 0.831, 0.902, 0.55], tinted(cool.get(), 1)]);

  // After a send a light runs once around the border, from the send button and back to it.
  const lightOpacity = useDerivedValue(() => {
    const run = lap.get();
    return run > 0 && run < 1 ? Math.min(1, Math.min(run, 1 - run) * 5) : 0;
  });
  const haloOpacity = useDerivedValue(() => lightOpacity.get() * 0.25);
  const turn = useDerivedValue(() => [{ rotate: ((LAP_START + 360 * lap.get()) * Math.PI) / 180 }]);

  return (
    <Canvas style={styles.aura} pointerEvents="none">
      <Circle c={warmAt} r={warmRadius}>
        <RadialGradient c={warmAt} r={warmRadius} colors={warmGlow} positions={[0, 0.42, 1]} />
      </Circle>
      {/* A grid of dots shows only where a glow falls on it. */}
      <SkiaPath path={DOTS}>
        <RadialGradient c={warmAt} r={warmRadius} colors={warmDots} positions={[0, 0.85]} />
      </SkiaPath>
      <Circle c={coolAt} r={coolRadius}>
        <RadialGradient c={coolAt} r={coolRadius} colors={coolGlow} positions={[0, 0.42, 1]} />
      </Circle>
      <SkiaPath path={DOTS}>
        <RadialGradient c={coolAt} r={coolRadius} colors={coolDots} positions={[0, 0.85]} />
      </SkiaPath>

      {/* The two ends of the box are rimmed with light, as if the glows sat right behind it. */}
      <RoundedRect {...BOX} r={RADIUS} style="stroke" strokeWidth={RIM_WIDTH}>
        <LinearGradient start={ACROSS.start} end={ACROSS.end} colors={rim} positions={[0, 0.42, 0.58, 1]} />
        <BlurMask blur={RIM_BLUR} style="normal" />
      </RoundedRect>

      <RoundedRect {...BOX} r={RADIUS} color="#07080c" />
      <RoundedRect x={BOX.x + 0.5} y={BOX.y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient start={ACROSS.start} end={ACROSS.end} colors={border} positions={[0, 0.5, 1]} />
      </RoundedRect>

      <Group opacity={haloOpacity}>
        <RoundedRect {...BOX} r={RADIUS} style="stroke" strokeWidth={5}>
          <SweepGradient c={CENTER} colors={GLINT} positions={[0, 0.7, 0.93, 1]} origin={CENTER} transform={turn} />
        </RoundedRect>
      </Group>
      <Group opacity={lightOpacity}>
        <RoundedRect {...BOX} r={RADIUS} style="stroke" strokeWidth={1.5}>
          <SweepGradient c={CENTER} colors={GLINT} positions={[0, 0.7, 0.93, 1]} origin={CENTER} transform={turn} />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

type RollProps = { labels: string[]; index: number; reducedMotion: boolean };

/**
 * A pill's label: all the names stand one above the other and the strip rolls to the one in use,
 * while the pill takes that name's width.
 */
function Roll({ labels, index, reducedMotion }: RollProps) {
  const [widths, setWidths] = useState<Record<number, number>>({});
  const width = useSharedValue(0);
  const row = useSharedValue(index);
  const target = widths[index] ?? 0;

  useEffect(() => {
    // The first measure is taken as it is; after that the pill's width moves with the roll.
    if (width.get() === 0) width.set(target);
    else width.set(reducedMotion ? withTiming(target, QUICK) : withSpring(target, ROLL_SPRING));
    row.set(reducedMotion ? withTiming(index, QUICK) : withSpring(index, ROLL_SPRING));
  }, [index, target, reducedMotion, width, row]);

  const clipStyle = useAnimatedStyle(() => ({ width: Math.max(0, width.get()) }));
  const stripStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -row.get() * ROLL_H }] }));

  const measure = (slot: number) => (e: LayoutChangeEvent) => {
    const measured = e.nativeEvent.layout.width;
    setWidths((old) => (old[slot] === measured ? old : { ...old, [slot]: measured }));
  };

  return (
    <Animated.View style={[styles.rollClip, clipStyle]}>
      <Animated.View style={[styles.rollStrip, stripStyle]}>
        {labels.map((label, slot) => (
          <View key={slot} style={styles.rollRow}>
            <Text style={styles.pillLabel} numberOfLines={1} onLayout={measure(slot)}>
              {label}
            </Text>
          </View>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** The mode pill's icon: it rolls with the label, one mode above the other. */
function IconRoll({ icons, index, reducedMotion }: { icons: ModeIcon[]; index: number; reducedMotion: boolean }) {
  const row = useSharedValue(index);

  useEffect(() => {
    row.set(reducedMotion ? withTiming(index, QUICK) : withSpring(index, ROLL_SPRING));
  }, [index, reducedMotion, row]);

  const stripStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -row.get() * ROLL_H }] }));

  return (
    <View style={styles.iconClip}>
      <Animated.View style={stripStyle}>
        {icons.map((icon, slot) => (
          <View key={slot} style={styles.iconRow}>
            <Glyph d={MODE_ICONS[icon]} />
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

/** The composer's own caret, shown while the script types. `beat` restarts the blink at every key. */
function Caret({ beat }: { beat: number }) {
  const on = useSharedValue(1);

  useEffect(() => {
    const jump = { duration: 1, reduceMotion: ReduceMotion.Never };
    on.set(1);
    on.set(withRepeat(withSequence(withDelay(BLINK, withTiming(0, jump)), withDelay(BLINK, withTiming(1, jump))), -1));
  }, [beat, on]);

  const style = useAnimatedStyle(() => ({ opacity: on.get() }));
  return <Animated.View style={[styles.caret, style]} />;
}

/**
 * Dark prompt composer lit by two slow glows, with a hairline border that runs from one glow's
 * colour to the other's. Its mode pill opens a menu; choosing a mode rolls the pill's icon and
 * label and shifts glows and border to that mode's colours, and sending runs a light once around
 * the border.
 */
export function ModeSwitcherInput({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Ask anything...',
  script = 'Plan three days in Lisbon',
  modes = DEFAULT_MODES,
  initialMode = 1,
  tones = DEFAULT_TONES,
  onSend,
  onModeChange,
  onToneChange,
  onAdd,
  onVoice,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion && modes.length > 1;
  const home = Math.min(Math.max(initialMode, 0), modes.length - 1);

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  // A touch hands the composer to the user until it has been left alone for a while.
  const [taken, setTaken] = useState(false);
  const [activity, setActivity] = useState(0);
  // The script's stand-in for focus.
  const [engaged, setEngaged] = useState(false);
  const [mode, setMode] = useState<number | null>(null);
  const [tone, setTone] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  // The script's finger.
  const [heldPill, setHeldPill] = useState(false);
  const [heldRow, setHeldRow] = useState<number | null>(null);
  const [heldSend, setHeldSend] = useState(false);
  // The last message sent; `count` tells two sends of the same text apart.
  const [outbox, setOutbox] = useState<{ text: string; count: number } | null>(null);

  const text = value ?? inner;
  // The script is running: the field is not focused and the composer draws its own caret.
  const playing = auto && !taken;
  const current = Math.min(Math.max(mode ?? home, 0), modes.length - 1);
  const idle = auto && taken && !focused && text.length === 0;
  const away = (home + 1) % modes.length;
  const warmHex = modes[current].warm;
  const coolHex = modes[current].cool;

  const clock = useSharedValue(0);
  const fromWarm = useSharedValue(channels(warmHex));
  const fromCool = useSharedValue(channels(coolHex));
  const toWarm = useSharedValue(channels(warmHex));
  const toCool = useSharedValue(channels(coolHex));
  const shift = useSharedValue(1);
  const lap = useSharedValue(1);
  const menu = useSharedValue(0);
  const ghost = useSharedValue(1);

  useFrameCallback((frame) => {
    clock.set(clock.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !reducedMotion);

  // A new mode: the glows and the border leave the colours they show now for that mode's pair.
  useEffect(() => {
    const k = shift.get();
    fromWarm.set(mix(fromWarm.get(), toWarm.get(), k));
    fromCool.set(mix(fromCool.get(), toCool.get(), k));
    toWarm.set(channels(warmHex));
    toCool.set(channels(coolHex));
    if (reducedMotion) {
      shift.set(1);
    } else {
      shift.set(0);
      shift.set(withTiming(1, { duration: SHIFT, easing: EASE_IN_OUT }));
    }
  }, [warmHex, coolHex, reducedMotion, fromWarm, fromCool, toWarm, toCool, shift]);

  // The menu grows out of the pill's corner.
  useEffect(() => {
    if (reducedMotion) menu.set(withTiming(menuOpen ? 1 : 0, QUICK));
    else if (menuOpen) menu.set(withSpring(1, MENU_SPRING));
    else menu.set(withTiming(0, { duration: 140, easing: EASE_IN, reduceMotion: ReduceMotion.Never }));
  }, [menuOpen, reducedMotion, menu]);

  // The text leaves the field and a light runs around the border.
  useEffect(() => {
    if (!outbox) return;
    ghost.set(0);
    ghost.set(withTiming(1, { duration: 250, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    lap.set(0);
    lap.set(withTiming(1, { duration: LAP, easing: EASE_IN_OUT }));
  }, [outbox, ghost, lap]);

  // One loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing) return;
    const keys = keyTimes(script.length);
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = (start: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, start + seconds * 1000 - performance.now())));
      };
      /** Opens the menu from the pill, rests on a row, and chooses it. */
      const pick = (slot: number, seconds: number) => {
        at(seconds, () => setHeldPill(true));
        at(seconds + PRESS_LEAD, () => {
          setHeldPill(false);
          setMenuOpen(true);
        });
        at(seconds + POINT_AFTER, () => setHeldRow(slot));
        at(seconds + CHOOSE_AFTER, () => {
          setHeldRow(null);
          setMode(slot);
          setMenuOpen(false);
        });
      };
      timers = [];
      pick(away, OPEN_AT);
      at(FOCUS_AT, () => setEngaged(true));
      keys.forEach((key, index) => at(key, () => setInner(script.slice(0, index + 1))));
      at(SEND_AT - PRESS_LEAD, () => setHeldSend(true));
      at(SEND_AT, () => {
        setHeldSend(false);
        setEngaged(false);
        setInner('');
        setOutbox((last) => ({ text: script, count: (last?.count ?? 0) + 1 }));
      });
      pick(home, REOPEN_AT);
      at(LOOP, () => run(start + LOOP * 1000));
    };
    run(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, script, home, away]);

  // The loop starts over from rest a few seconds after the last touch.
  useEffect(() => {
    if (!idle) return;
    let restart: ReturnType<typeof setTimeout> | undefined;
    const wait = setTimeout(() => {
      setMenuOpen(false);
      setMode(home);
      restart = setTimeout(() => setTaken(false), 900);
    }, RESUME * 1000);
    return () => {
      clearTimeout(wait);
      clearTimeout(restart);
    };
  }, [idle, activity, home]);

  const tap = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Any touch takes the composer away from the script. */
  const interrupt = () => {
    setActivity((count) => count + 1);
    if (!playing) return;
    setTaken(true);
    setEngaged(false);
    setHeldPill(false);
    setHeldRow(null);
    setHeldSend(false);
  };

  const write = (next: string) => {
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  /** A touch on the field ends the script: the field is emptied and the keyboard takes over. */
  const onFocus = () => {
    setFocused(true);
    if (playing) setInner('');
    interrupt();
    setMenuOpen(false);
  };

  const submit = () => {
    interrupt();
    const message = text.trim();
    if (message.length === 0) return;
    tap();
    onSend?.(message, current);
    write('');
    setOutbox((last) => ({ text: message, count: (last?.count ?? 0) + 1 }));
  };

  const choose = (slot: number) => {
    interrupt();
    tap();
    setMenuOpen(false);
    if (slot === current) return;
    setMode(slot);
    onModeChange?.(slot);
  };

  const nextTone = () => {
    interrupt();
    if (tones.length === 0) return;
    const next = (tone + 1) % tones.length;
    setTone(next);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onToneChange?.(next);
  };

  const menuStyle = useAnimatedStyle(() => {
    const m = menu.get();
    return {
      opacity: Math.min(1, Math.max(0, m)),
      transform: reducedMotion ? [] : [{ translateY: -6 * (1 - m) }, { scale: 0.86 + 0.14 * m }],
    };
  });
  const chevronStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${180 * menu.get()}deg` }] }));
  const ghostStyle = useAnimatedStyle(() => {
    const t = ghost.get();
    return { opacity: 1 - t, transform: [{ translateY: reducedMotion ? 0 : -12 * t }] };
  });

  // A control dips under a finger, or under the script's.
  const dip = (down: boolean) => ({
    transform: [{ scale: down ? 0.94 : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: 130,
    transitionTimingFunction: CSS_EASE_OUT_STRONG,
  });
  // A trailing space must keep its width in the row that mirrors the typed text.
  const mirror = text.endsWith(' ') ? `${text.slice(0, -1)} ` : text;
  const hintShown = text.length === 0;

  return (
    <View style={styles.box}>
      <Aura clock={clock} fromWarm={fromWarm} fromCool={fromCool} toWarm={toWarm} toCool={toCool} shift={shift} lap={lap} />

      <View style={styles.field}>
        <Animated.Text
          style={[
            styles.placeholder,
            {
              opacity: hintShown ? 1 : 0,
              // It waits for the sent text to clear the field before it returns.
              transitionProperty: 'opacity',
              transitionDuration: hintShown ? 150 : 0,
              transitionDelay: hintShown ? PLACEHOLDER_DELAY : 0,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
          numberOfLines={1}
        >
          {placeholder}
        </Animated.Text>
        <Animated.Text style={[styles.ghost, ghostStyle]} numberOfLines={1}>
          {outbox?.text ?? ''}
        </Animated.Text>
        <TextInput
          value={text}
          onChangeText={write}
          onFocus={onFocus}
          onBlur={() => {
            setFocused(false);
            setActivity((count) => count + 1);
          }}
          onSubmitEditing={submit}
          returnKeyType="send"
          selectionColor={INK}
          accessibilityLabel={placeholder}
          // While the script types, the letters are drawn by the row above it, with its caret.
          style={[styles.input, { color: playing ? 'transparent' : INK }]}
        />
        {playing && engaged ? (
          <View style={styles.mirror} pointerEvents="none">
            <Text style={styles.typed} numberOfLines={1}>
              {mirror}
            </Text>
            <Caret beat={text.length} />
          </View>
        ) : null}
      </View>

      <View style={styles.toolbar}>
        <Pressable
          onPress={() => {
            interrupt();
            onAdd?.();
          }}
          accessibilityRole="button"
          accessibilityLabel="Add"
        >
          {({ pressed }) => (
            <Animated.View style={[styles.round, dip(pressed)]}>
              <Glyph d={PLUS} />
            </Animated.View>
          )}
        </Pressable>

        <Pressable onPress={nextTone} accessibilityRole="button" accessibilityLabel="Tone" accessibilityValue={{ text: tones[tone] ?? '' }}>
          {({ pressed }) => (
            <Animated.View style={[styles.pill, dip(pressed)]}>
              <Glyph d={FEATHER} size={14} />
              <Roll labels={tones} index={tone} reducedMotion={reducedMotion} />
              <Glyph d={CHEVRON} size={10} />
            </Animated.View>
          )}
        </Pressable>

        <View>
          <Pressable
            onPress={() => {
              interrupt();
              tap();
              setMenuOpen((open) => !open);
            }}
            accessibilityRole="button"
            accessibilityLabel="Mode"
            accessibilityValue={{ text: modes[current].label }}
            accessibilityState={{ expanded: menuOpen }}
          >
            {({ pressed }) => (
              <Animated.View style={[styles.pill, dip(pressed || heldPill)]}>
                <IconRoll icons={modes.map((item) => item.icon)} index={current} reducedMotion={reducedMotion} />
                <Roll labels={modes.map((item) => item.short)} index={current} reducedMotion={reducedMotion} />
                <Animated.View style={chevronStyle}>
                  <Glyph d={CHEVRON} size={10} />
                </Animated.View>
              </Animated.View>
            )}
          </Pressable>

          {/* The menu hangs under the pill, over the composer's bottom edge. */}
          <Animated.View
            style={[styles.menu, menuStyle]}
            pointerEvents={menuOpen ? 'auto' : 'none'}
            accessibilityElementsHidden={!menuOpen}
            importantForAccessibility={menuOpen ? 'auto' : 'no-hide-descendants'}
          >
            {modes.map((item, slot) => {
              const lit = slot === current || slot === heldRow;
              return (
                <Pressable key={item.label} onPress={() => choose(slot)} accessibilityRole="menuitem" accessibilityState={{ selected: slot === current }}>
                  {({ pressed }) => {
                    const down = pressed || heldRow === slot;
                    return (
                      <Animated.View
                        style={[
                          styles.row,
                          {
                            // A row lights up under a finger, and more softly while it is the mode in use.
                            backgroundColor: down ? '#262a36' : slot === current && heldRow === null ? '#1e212b' : 'rgba(30,33,43,0)',
                            // The rows follow the panel in, one after another.
                            opacity: menuOpen ? 1 : 0,
                            transform: [{ translateY: menuOpen || reducedMotion ? 0 : 6 }],
                            transitionProperty: ['backgroundColor', 'opacity', 'transform'],
                            transitionDuration: [120, menuOpen ? 200 : 100, menuOpen ? 200 : 100],
                            transitionDelay: [0, menuOpen ? 50 + slot * 40 : 0, menuOpen ? 50 + slot * 40 : 0],
                            transitionTimingFunction: CSS_EASE_OUT,
                          },
                        ]}
                      >
                        <Glyph d={MODE_ICONS[item.icon]} color={lit ? '#ffffff' : '#aeb3bf'} />
                        <Text style={[styles.rowLabel, { color: lit ? '#ffffff' : '#aeb3bf' }]} numberOfLines={1}>
                          {item.label}
                        </Text>
                        {/* The mode in use is ticked. */}
                        <View style={{ opacity: slot === current ? 1 : 0 }}>
                          <Glyph d={CHECK} color="#ffffff" />
                        </View>
                      </Animated.View>
                    );
                  }}
                </Pressable>
              );
            })}
          </Animated.View>
        </View>

        <View style={styles.spacer} />

        <Pressable
          onPress={() => {
            interrupt();
            onVoice?.();
          }}
          accessibilityRole="button"
          accessibilityLabel="Voice"
        >
          {({ pressed }) => (
            <Animated.View style={[styles.voice, dip(pressed)]}>
              <Glyph d={WAVE} />
            </Animated.View>
          )}
        </Pressable>

        <Pressable onPress={submit} accessibilityRole="button" accessibilityLabel="Send">
          {({ pressed }) => (
            <Animated.View style={[styles.send, dip(pressed || heldSend)]}>
              <ViewGradient colors={['#7b4dff', '#ff7a59']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.sendFill} />
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <Path d={PLANE} fill="#ffffff" />
              </Svg>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: WIDTH,
    height: HEIGHT,
  },
  aura: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  field: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 13,
    height: 22,
    justifyContent: 'center',
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    right: 0,
    color: '#8a8f9c',
    fontSize: 15,
  },
  ghost: {
    position: 'absolute',
    left: 0,
    right: 0,
    color: INK,
    fontSize: 15,
  },
  input: {
    height: 22,
    paddingVertical: 0,
    fontSize: 15,
  },
  mirror: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  typed: {
    flexShrink: 1,
    color: INK,
    fontSize: 15,
  },
  caret: {
    width: 2,
    height: 18,
    borderRadius: 1,
    backgroundColor: INK,
  },
  toolbar: {
    position: 'absolute',
    left: 10,
    right: 10,
    bottom: 10,
    height: CONTROL,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  round: {
    width: CONTROL,
    height: CONTROL,
    borderRadius: CONTROL / 2,
    borderWidth: 1,
    borderColor: EDGE,
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pill: {
    height: PILL,
    // The hairline takes one point of the padding on each side.
    paddingLeft: 7,
    paddingRight: 6,
    borderRadius: PILL / 2,
    borderWidth: 1,
    borderColor: EDGE,
    backgroundColor: CHIP,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  pillLabel: {
    color: LABEL,
    fontSize: 13,
    fontWeight: '500',
  },
  rollClip: {
    height: ROLL_H,
    overflow: 'hidden',
  },
  // Wide enough for any name, so each one is measured at its own width.
  rollStrip: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 240,
    alignItems: 'flex-start',
  },
  rollRow: {
    height: ROLL_H,
    justifyContent: 'center',
  },
  iconClip: {
    width: 16,
    height: ROLL_H,
    overflow: 'hidden',
  },
  iconRow: {
    width: 16,
    height: ROLL_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menu: {
    position: 'absolute',
    left: 0,
    top: CONTROL + 5,
    width: MENU_W,
    // The hairline takes one point of the padding.
    padding: 5,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: EDGE,
    backgroundColor: '#14161d',
    boxShadow: '0 10px 36px rgba(0,0,0,0.5)',
    transformOrigin: 'left top',
  },
  row: {
    height: ROW_H,
    paddingHorizontal: 8,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  rowLabel: {
    flex: 1,
    fontSize: 14,
  },
  spacer: {
    flex: 1,
  },
  voice: {
    width: 36,
    height: PILL,
    borderRadius: PILL / 2,
    borderWidth: 1,
    borderColor: EDGE,
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  send: {
    width: CONTROL,
    height: CONTROL,
    borderRadius: CONTROL / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendFill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
});
