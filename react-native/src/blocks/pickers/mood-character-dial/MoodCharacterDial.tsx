import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { memo, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Ellipse, G, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** The characters the stage can show. */
type Figure = 'joyful' | 'sleepy' | 'recovery' | 'satisfied' | 'foodie' | 'thoughtful' | 'nostalgic' | 'shopping' | 'scrolling';

type Mood = {
  name: string;
  figure: Figure;
  /** The character's colour. */
  body: string;
  /** The panel behind the character. */
  stage: string;
  /** The two colours of the needle (top to bottom) and of the selected chip (left to right). */
  from: string;
  to: string;
};

type Props = {
  title?: string;
  moods?: Mood[];
  /** Index of the mood selected at first, and the one the self-playing demo rests on. */
  initial?: number;
  /** Steps and flicks through the moods by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the mood the dial is sent to. */
  onSelect?: (index: number) => void;
};

// The width the sizes below were drawn for; the characters scale with a narrower screen.
const DESIGN = 402;
const MAX_WIDTH = 430;
const HEIGHT = 630;
const PANEL = '#141b1c';

// The pastel panel and its characters.
const CARD_H = 340;
const CARD_INSET = 8;
const CARD_RADIUS = 32;
// Distance between two characters, and how far a character's centre sits above the panel's bottom edge.
const FIGURE_STEP = 340;
const FIGURE_RISE = 125;
// The box a character is drawn in: its body's centre is `FIGURE_W / 2` across and `FIGURE_RISE` above the bottom.
const FIGURE_W = 400;
const BREATH_TIME = 4;

const TITLE_Y = 392;

// The bars: 31 across, sliding five places per mood.
const BARS_Y = 494;
const BARS_H = 96;
const NEEDLE_DROP = 6;
const BAR_GAP = 12;
const BARS_ASIDE = 15;
const BARS_PER_MOOD = 5;
const BAR_LOW = 10;
const BAR_HIGH = 84;
const BELL_WIDTH = 95;

// The chips.
const CHIPS_Y = 598;
const CHIP_H = 48;
const CHIP_PAD = 22;
const CHIP_GAP = 12;
const CHIP_FONT = 15;

// Snapping.
const STIFFNESS = 220;
const DAMPING_RATIO = 0.74;
const GLIDE_TIME = 1.1;
// Seconds of the finger's speed the row coasts for.
const COAST = 0.2;
const BAND = 0.35;
const TAP_SLOP = 6;
// The dial has landed once it is this close (in moods) to its target.
const LAND_WITHIN = 0.03;
const FILL_IN = 0.2;
const FILL_OUT = 0.12;
// The landing's squash rings out within this long.
const SQUASH_TIME = 0.6;

// autoPlay (seconds): when each move starts, and how many moods from the resting one it goes to.
const LOOP = 8;
const SCRIPT = [
  { at: 1.0, by: 1, glide: false },
  { at: 2.4, by: 4, glide: false },
  { at: 4.1, by: 5, glide: false },
  { at: 5.4, by: 0, glide: true },
];
// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting mood.
const RESUME_AFTER = 2;
const HOME_TIME = 1.2;

const EASE_IN_OUT = Easing.bezier(0.45, 0, 0.55, 1);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const INK = '#2b1b17';
const BLUSH = '#ff8fa0';
const ROSE = '#e8384f';
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

const MOODS: Mood[] = [
  { name: 'Joyful', figure: 'joyful', body: '#57c24a', stage: '#d3ebb4', from: '#8fd96b', to: '#f5d34a' },
  { name: 'Sleepy', figure: 'sleepy', body: '#f5b70f', stage: '#efe2b4', from: '#f5c63c', to: '#f29ac0' },
  { name: 'Recovery Mode', figure: 'recovery', body: '#f26a5e', stage: '#cfd2c4', from: '#c9a8f0', to: '#9ed98a' },
  { name: 'Satisfied', figure: 'satisfied', body: '#3fb5a8', stage: '#cde6df', from: '#5fd0c0', to: '#f5d34a' },
  { name: 'Foodie', figure: 'foodie', body: '#f5980f', stage: '#f8d9a8', from: '#f5a53c', to: '#f26a5e' },
  { name: 'Thoughtful', figure: 'thoughtful', body: '#5b8def', stage: '#cfddf7', from: '#7fa8f5', to: '#b56bf0' },
  { name: 'Nostalgic', figure: 'nostalgic', body: '#b265f0', stage: '#e4d2f7', from: '#b56bf0', to: '#f08a7a' },
  { name: 'Shopping', figure: 'shopping', body: '#ff8fe0', stage: '#f9d5fb', from: '#ff8fe0', to: '#b56bf0' },
  { name: 'Scroll Mode', figure: 'scrolling', body: '#f0434a', stage: '#f8c3b0', from: '#f0434a', to: '#f5980f' },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** The row's offset when the dial is at `p`: chip centres, joined by straight lines. */
function offsetAt(centres: number[], p: number) {
  'worklet';
  if (centres.length < 2) return centres[0] ?? 0;
  const i = Math.min(Math.max(Math.floor(p), 0), centres.length - 2);
  return centres[i] + (centres[i + 1] - centres[i]) * (p - i);
}

/** The inverse of `offsetAt`. */
function indexAt(centres: number[], x: number) {
  'worklet';
  if (centres.length < 2) return 0;
  let i = 0;
  while (i < centres.length - 2 && x > centres[i + 1]) i++;
  return i + (x - centres[i]) / (centres[i + 1] - centres[i]);
}

/** The landing: the character dips 4% within 80 ms of arriving and rings out in about half a second. */
function squashAt(t: number) {
  'worklet';
  if (t <= 0 || t >= SQUASH_TIME) return 0;
  return (0.04 / 0.524) * Math.exp(-7 * t) * Math.sin(14.5 * t);
}

/** One layer of a stack that cross-fades with the dial: the layer of the mood below shows through the next one fading in. */
function layerOpacity(index: number, p: number) {
  'worklet';
  if (p >= index) return p - index < 1 ? 1 : 0;
  return clamp01(1 - (index - p));
}

// The characters

/** A circle of radius 165 whose edge swells in and out `count` times by the share `depth`. */
function lumps(count: number, depth: number, turn: number) {
  let d = '';
  for (let step = 0; step < 96; step++) {
    const angle = (step / 96) * Math.PI * 2;
    const radius = 165 * (1 + depth * Math.sin(count * angle + turn));
    d += `${step === 0 ? 'M' : 'L'}${(Math.cos(angle) * radius).toFixed(1)} ${(Math.sin(angle) * radius).toFixed(1)}`;
  }
  return `${d}Z`;
}

function sparkle(x: number, y: number, size: number) {
  return `M${x} ${y - size}Q${x} ${y} ${x + size} ${y}Q${x} ${y} ${x} ${y + size}Q${x} ${y} ${x - size} ${y}Q${x} ${y} ${x} ${y - size}Z`;
}

function heart(x: number, y: number, s: number) {
  return (
    `M${x} ${y + s * 0.9}` +
    `C${x - s * 0.5} ${y + s * 0.55} ${x - s} ${y + s * 0.15} ${x - s} ${y - s * 0.2}` +
    `C${x - s} ${y - s * 0.75} ${x - s * 0.3} ${y - s * 0.9} ${x} ${y - s * 0.45}` +
    `C${x + s * 0.3} ${y - s * 0.9} ${x + s} ${y - s * 0.75} ${x + s} ${y - s * 0.2}` +
    `C${x + s} ${y + s * 0.15} ${x + s * 0.5} ${y + s * 0.55} ${x} ${y + s * 0.9}Z`
  );
}

const LINE = { stroke: INK, strokeWidth: 5.5, strokeLinecap: 'round', strokeLinejoin: 'round', fill: 'none' } as const;

/** `asleep`: closed, curving down. `smiling`: closed, curving up. Otherwise open, looking towards `x`, `y` (each −1…1). */
type EyesKind = 'asleep' | 'smiling' | [number, number];

function Eyes({ kind }: { kind: EyesKind }) {
  return (
    <>
      {[-46, 46].map((x) => {
        if (kind === 'asleep') return <Path key={x} d={`M${x - 19} -80Q${x} -60 ${x + 19} -80`} {...LINE} />;
        if (kind === 'smiling') return <Path key={x} d={`M${x - 19} -70Q${x} -98 ${x + 19} -70`} {...LINE} />;
        const px = x + kind[0] * 7;
        const py = -78 + kind[1] * 7;
        return (
          <G key={x}>
            <Circle cx={x} cy={-78} r={18} fill="#ffffff" />
            <Circle cx={px} cy={py} r={9} fill={INK} />
            <Circle cx={px - 3} cy={py - 3} r={3} fill="#ffffff" />
          </G>
        );
      })}
    </>
  );
}

type MouthKind = 'round' | 'smile' | 'grin' | 'flat' | 'smirk' | 'lick';

function Mouth({ kind }: { kind: MouthKind }) {
  switch (kind) {
    case 'round':
      return (
        <>
          <Ellipse cx={0} cy={-24} rx={14} ry={17} fill={INK} />
          <Ellipse cx={0} cy={-15} rx={9} ry={6} fill="#f26a5e" />
        </>
      );
    case 'smile':
      return <Path d="M-22 -32Q0 -8 22 -32" {...LINE} />;
    case 'lick':
      return (
        <>
          <Ellipse cx={12} cy={-16} rx={10} ry={9} fill={ROSE} />
          <Path d="M-22 -32Q0 -8 22 -32" {...LINE} />
        </>
      );
    case 'grin':
      return (
        <>
          <Path d="M-28 -36L28 -36Q0 14 -28 -36Z" fill={INK} stroke={INK} strokeWidth={4} strokeLinejoin="round" />
          <Ellipse cx={0} cy={-18.5} rx={11} ry={5.5} fill="#f26a5e" />
        </>
      );
    case 'flat':
      return <Path d="M-13 -26L13 -26" {...LINE} />;
    case 'smirk':
      return <Path d="M-14 -30Q6 -14 18 -34" {...LINE} />;
  }
}

function Cheeks() {
  return (
    <>
      <Circle cx={-84} cy={-44} r={14} fill={BLUSH} opacity={0.6} />
      <Circle cx={84} cy={-44} r={14} fill={BLUSH} opacity={0.6} />
    </>
  );
}

/** A rounded rectangle whose top edge is where the circle's would be. */
function Block({ width, radius, fill }: { width: number; radius: number; fill: string }) {
  return <Rect x={-width / 2} y={-165} width={width} height={360} rx={radius} fill={fill} />;
}

/** One character, drawn around the centre of its 330 pt body. */
const FigureArt = memo(function FigureArt({ figure, body }: { figure: Figure; body: string }) {
  let parts;
  switch (figure) {
    case 'joyful':
      parts = (
        <>
          <Path d={lumps(6, 0.045, 0)} fill={body} />
          <Cheeks />
          <Eyes kind="smiling" />
          <Mouth kind="grin" />
        </>
      );
      break;
    case 'sleepy':
      parts = (
        <>
          <Circle cx={0} cy={0} r={165} fill={body} />
          <Eyes kind="asleep" />
          <Mouth kind="round" />
        </>
      );
      break;
    case 'recovery':
      parts = (
        <>
          <Path d={lumps(4, 0.06, 0.6)} fill={body} />
          <Rect x={-4.5} y={-143} width={9} height={30} rx={3} fill="#ffffff" />
          <Rect x={-15} y={-132.5} width={30} height={9} rx={3} fill="#ffffff" />
          <Eyes kind="asleep" />
          <Mouth kind="smile" />
        </>
      );
      break;
    case 'satisfied':
      parts = (
        <>
          <Block width={300} radius={110} fill={body} />
          <Cheeks />
          <Eyes kind="smiling" />
          <Mouth kind="smile" />
        </>
      );
      break;
    case 'foodie':
      parts = (
        <>
          <Path d={lumps(3, 0.07, 0)} fill={body} />
          <Cheeks />
          <Eyes kind={[0, 0.5]} />
          <Mouth kind="lick" />
        </>
      );
      break;
    case 'thoughtful':
      parts = (
        <>
          <Block width={290} radius={145} fill={body} />
          <Circle cx={92} cy={-108} r={7} fill="#ffffff" />
          <Circle cx={116} cy={-138} r={10} fill="#ffffff" />
          <Circle cx={146} cy={-172} r={14} fill="#ffffff" />
          <Eyes kind={[0.6, -0.6]} />
          <Mouth kind="flat" />
        </>
      );
      break;
    case 'nostalgic':
      parts = (
        <>
          <Path d={lumps(5, 0.05, 0.3)} fill={body} />
          <Path d={sparkle(92, -118, 15)} fill="#ffffff" />
          <Path d={sparkle(116, -88, 8)} fill="#ffffff" />
          <Eyes kind={[-0.5, -0.6]} />
          <Mouth kind="smirk" />
        </>
      );
      break;
    case 'shopping':
      // A bag: two handles behind a rounded square, and a heart under the face.
      parts = (
        <>
          <Path d="M-58 -163A26 26 0 0 1 -6 -163" stroke={ROSE} strokeWidth={8} strokeLinecap="round" fill="none" />
          <Path d="M6 -163A26 26 0 0 1 58 -163" stroke={ROSE} strokeWidth={8} strokeLinecap="round" fill="none" />
          <Block width={280} radius={44} fill={body} />
          <Eyes kind="smiling" />
          <Mouth kind="smile" />
          <Path d={heart(0, 30, 26)} fill={ROSE} />
        </>
      );
      break;
    case 'scrolling':
      // A phone held up at the side, seen from the back.
      parts = (
        <>
          <Path d={lumps(8, 0.04, 0)} fill={body} />
          <Eyes kind={[0.8, 0.2]} />
          <Mouth kind="flat" />
          <G transform="translate(100 -24) rotate(-10)">
            <Rect x={-23} y={-39} width={46} height={78} rx={10} fill="#1e1e24" />
            <Circle cx={-8.5} cy={-24.5} r={6.5} fill="#3a3a44" />
          </G>
        </>
      );
      break;
  }
  return (
    <Svg width={FIGURE_W} height={CARD_H} viewBox={`${-FIGURE_W / 2} ${FIGURE_RISE - CARD_H} ${FIGURE_W} ${CARD_H}`}>
      {parts}
    </Svg>
  );
});

type Motion = {
  /** The dial's position as a mood index; whole numbers are the snap points. */
  pos: SharedValue<number>;
  /** The selected chip's gradient: 0 an outline … 1 filled. */
  fill: SharedValue<number>;
  /** The mood the dial is at, or heading for. */
  target: SharedValue<number>;
  /** 0…1 over `SQUASH_TIME` from the moment the dial reaches its target. */
  land: SharedValue<number>;
  seconds: SharedValue<number>;
  centres: SharedValue<number[]>;
};

/** One of the letters that rise from the sleeper, growing and fading as they go. */
function Zed({ letter, index, pos, seconds }: { letter: number; index: number; pos: SharedValue<number>; seconds: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const u = (seconds.get() / 2 + letter / 3) % 1;
    return {
      opacity: Math.sin(Math.PI * u) * clamp01(1 - Math.abs(index - pos.get()) * 1.5),
      transform: [{ translateX: 40 * u }, { translateY: -70 * u }, { scale: (5 + 7 * u) / 8 }],
    };
  });
  return (
    <Animated.View style={[styles.zed, style]}>
      <Svg width={24} height={24} viewBox="-12 -12 24 24">
        <Path d="M-8 -8L8 -8L-8 8L8 8" stroke="#2b3fd6" strokeWidth={4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </Svg>
    </Animated.View>
  );
}

/** A character on the panel: it slides with the dial, breathes, and squashes when the dial lands on it. */
function Character({ index, mood, width, still, pos, target, land, seconds }: Motion & { index: number; mood: Mood; width: number; still: boolean }) {
  const scale = width / DESIGN;
  const style = useAnimatedStyle(() => {
    const away = index - pos.get();
    const breath = still ? 0 : 0.012 * Math.sin((Math.PI * 2 * seconds.get()) / BREATH_TIME);
    const press = (target.get() === index && !still ? squashAt(land.get() * SQUASH_TIME) : 0) - breath;
    return {
      opacity: Math.abs(away) < 1.7 ? 1 : 0,
      transform: [{ translateX: away * FIGURE_STEP * scale }, { scaleX: scale * (1 + press) }, { scaleY: scale * (1 - press) }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.figure, { left: (width - CARD_INSET * 2 - FIGURE_W) / 2 }, style]}>
      <FigureArt figure={mood.figure} body={mood.body} />
      {mood.figure === 'sleepy' && [0, 1, 2].map((letter) => <Zed key={letter} letter={letter} index={index} pos={pos} seconds={seconds} />)}
    </Animated.View>
  );
}

/** One bar of the dial. The bars slide under an envelope that stays put, so each grows towards the middle. */
function Bar({ place, width, pos }: { place: number; width: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const shift = pos.get() * BARS_PER_MOOD;
    const x = (place - (shift - Math.floor(shift))) * BAR_GAP;
    const bell = Math.exp(-((x / BELL_WIDTH) ** 2));
    return {
      height: mix(BAR_LOW, BAR_HIGH, bell),
      opacity: Math.abs(x) > BARS_ASIDE * BAR_GAP + 0.5 ? 0 : mix(0.05, 0.45, bell),
      transform: [{ translateX: x }],
    };
  });
  return <Animated.View style={[styles.bar, { left: width / 2 - 1 }, style]} />;
}

/** The needle's gradient for one mood; the stack of them cross-fades as the dial moves. */
function NeedleTone({ index, mood, pos }: { index: number; mood: Mood; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: layerOpacity(index, pos.get()) }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <LinearGradient style={StyleSheet.absoluteFill} colors={[mood.from, mood.to]} />
    </Animated.View>
  );
}

type ChipProps = {
  index: number;
  mood: Mood;
  fill: SharedValue<number>;
  /** The chip that carries the gradient: the one the dial last landed on. */
  filled: SharedValue<number>;
  selected: boolean;
  onLayout: (event: LayoutChangeEvent) => void;
  onPress: () => void;
};

/** A chip of the row. Taps are found by the gesture below, which knows where the row is; `onPress` serves assistive taps. */

function Chip({ index, mood, fill, filled, selected, onLayout, onPress }: ChipProps) {
  const tone = useAnimatedStyle(() => ({ opacity: filled.get() === index ? fill.get() : 0 }));
  const rim = useAnimatedStyle(() => ({ opacity: filled.get() === index ? 1 - fill.get() : 1 }));
  const label = useAnimatedStyle(() => ({
    color: interpolateColor(filled.get() === index ? fill.get() : 0, [0, 1], ['#ffffff', PANEL]),
  }));
  return (
    <View style={styles.chip} accessible accessibilityRole="button" accessibilityLabel={mood.name} accessibilityState={{ selected }} onLayout={onLayout} onAccessibilityTap={onPress}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, tone]}>
        <LinearGradient style={StyleSheet.absoluteFill} colors={[mood.from, mood.to]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.chipRim, rim]} />
      <Animated.Text style={[styles.chipLabel, label]} numberOfLines={1}>
        {mood.name}
      </Animated.Text>
    </View>
  );
}

/**
 * A mood picker in two halves. On top, a pastel panel holds one large flat character per mood, laid
 * out as a carousel. Below, on a near-black panel, a title, a row of thin bars under a bell-shaped
 * envelope with a gradient needle in the middle, and a row of outlined chips. Dragging anywhere moves
 * the chips with the finger, the bars under the fixed envelope and the characters one panel per mood,
 * while the panel's colour and the needle's gradient cross-fade. On release the row snaps a chip
 * under the needle; it fills with the mood's gradient and the character lands with a small squash.
 */
export function MoodCharacterDial({ title = 'Select your\nweekend mood', moods = MOODS, initial = 1, autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && moods.length > 1;
  const last = moods.length - 1;
  const home = Math.min(Math.max(initial, 0), last);
  const width = Math.min(useWindowDimensions().width, MAX_WIDTH);

  const pos = useSharedValue(home);
  const fill = useSharedValue(1);
  const target = useSharedValue(home);
  const filled = useSharedValue(home);
  const land = useSharedValue(1);
  const seconds = useSharedValue(0);
  // Each chip's width and its centre along the row: a guess from its name until the row has been laid out.
  const guessed = moods.map((mood) => mood.name.length * 7.6 + CHIP_PAD * 2);
  const widths = useSharedValue(guessed);
  const centres = useSharedValue(guessed.map((chip, index) => guessed.slice(0, index).reduce((sum, before) => sum + before + CHIP_GAP, 0) + chip / 2));
  // A move is under way; it ends when the dial first reaches its target.
  const moving = useSharedValue(false);
  const held = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // Where the finger went down, to find the chip under a tap.
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  // The mood under the needle at the last frame, for the haptic ticks.
  const ticked = useSharedValue(home);
  // The moves come from a finger, so they tick; the script's do not.
  const felt = useSharedValue(false);
  const motion: Motion = { pos, fill, target, land, seconds, centres };

  const [selected, setSelected] = useState(home);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  const measured = useRef<{ x: number; width: number }[]>([]);

  useFrameCallback((frame) => {
    seconds.set(seconds.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !still);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((count) => count + 1);
  const chosen = (index: number) => {
    setSelected(index);
    onSelect?.(index);
  };

  /**
   * Sends the dial to mood `index`: on a spring that is softer the further it has to go, or, for the
   * script's long way home, on an ease-in-out glide.
   */
  const go = (index: number, velocity: number, glide: boolean) => {
    'worklet';
    const to = Math.min(Math.max(Math.round(index), 0), last);
    const far = Math.abs(to - pos.get());
    if (far > LAND_WITHIN) fill.set(withTiming(0, { duration: FILL_OUT * 1000, ...LINEAR }));
    const changed = to !== target.get();
    target.set(to);
    moving.set(true);
    if (still) {
      pos.set(to);
    } else if (glide) {
      pos.set(withTiming(to, { duration: GLIDE_TIME * 1000, easing: EASE_IN_OUT, reduceMotion: ReduceMotion.Never }));
    } else {
      const stiffness = STIFFNESS / Math.max(1, far) ** 0.75;
      pos.set(withSpring(to, { mass: 1, stiffness, damping: DAMPING_RATIO * 2 * Math.sqrt(stiffness), velocity, reduceMotion: ReduceMotion.Never }));
    }
    return changed;
  };

  const select = (index: number, glide = false) => {
    if (go(index, 0, glide)) chosen(Math.min(Math.max(index, 0), last));
  };

  // Ticks as moods pass the needle, and notices the landing: the chip fills and the character squashes.
  useAnimatedReaction(
    () => {
      // Read here so that a new move or a lifted finger is noticed even while the dial stands still.
      moving.get();
      held.get();
      return pos.get();
    },
    (p) => {
      const now = Math.min(Math.max(Math.round(p), 0), last);
      if (now !== ticked.get()) {
        ticked.set(now);
        if (felt.get()) scheduleOnRN(tick);
      }
      if (moving.get() && !held.get() && Math.abs(p - target.get()) < LAND_WITHIN) {
        moving.set(false);
        filled.set(target.get());
        fill.set(withTiming(1, { duration: FILL_IN * 1000, ...LINEAR }));
        land.set(0);
        land.set(withTiming(1, { duration: SQUASH_TIME * 1000, ...LINEAR }));
      }
    },
  );

  const pan = Gesture.Pan()
    .minDistance(TAP_SLOP)
    .onBegin((event) => {
      // The finger catches the dial wherever it is.
      cancelAnimation(pos);
      held.set(true);
      felt.set(true);
      downX.set(event.x);
      downY.set(event.y);
      scheduleOnRN(touch);
    })
    .onStart(() => {
      dragFrom.set(offsetAt(centres.get(), pos.get()));
      moving.set(true);
      fill.set(withTiming(0, { duration: FILL_OUT * 1000, ...LINEAR }));
    })
    .onUpdate((event) => {
      // Past either end the row follows the finger at a third of its speed.
      const p = indexAt(centres.get(), dragFrom.get() - event.translationX);
      pos.set(p < 0 ? p * BAND : p > last ? last + (p - last) * BAND : p);
    })
    .onEnd((event) => {
      // The row keeps the finger's speed and settles on the chip it would coast to.
      held.set(false);
      const p = pos.get();
      const spacing = Math.max(1, offsetAt(centres.get(), p + 0.5) - offsetAt(centres.get(), p - 0.5));
      const speed = -event.velocityX / spacing;
      const to = Math.min(Math.max(Math.round(p + speed * COAST), 0), last);
      if (go(to, speed, false)) scheduleOnRN(chosen, to);
    })
    .onFinalize((_event, success) => {
      held.set(false);
      if (!success) {
        // A tap on a chip selects it; anywhere else the dial settles where it was caught.
        let to = Math.round(pos.get());
        if (Math.abs(downY.get() - CHIPS_Y) <= CHIP_H / 2 + 8) {
          const x = downX.get() - width / 2 + offsetAt(centres.get(), pos.get());
          const spots = centres.get();
          const sizes = widths.get();
          for (let i = 0; i < spots.length; i++) {
            if (Math.abs(x - spots[i]) <= ((sizes[i] ?? 0) + CHIP_GAP) / 2) to = i;
          }
        }
        if (go(to, 0, false)) scheduleOnRN(chosen, Math.min(Math.max(to, 0), last));
      }
      scheduleOnRN(touch);
    });

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ select });
  useEffect(() => {
    steps.current = { select };
  });

  // The script a finger would follow: a step, a flick, a step, and a long glide home.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        steps.current.select(home + step.by, step.glide);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the resting mood, and start over from there.
      timer = setTimeout(() => {
        if (held.get()) return;
        felt.set(false);
        steps.current.select(home);
        run(Date.now() + HOME_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, home, held, felt]);

  const onChip = (index: number) => (event: LayoutChangeEvent) => {
    const { x, width: w } = event.nativeEvent.layout;
    measured.current[index] = { x, width: w };
    if (measured.current.filter(Boolean).length !== moods.length) return;
    widths.set(measured.current.map((box) => box.width));
    centres.set(measured.current.map((box) => box.x + box.width / 2));
  };

  const stageStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(
      pos.get(),
      moods.map((_, index) => index),
      moods.map((mood) => mood.stage),
    ),
  }));
  const rowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: width / 2 - offsetAt(centres.get(), pos.get()) }] }));

  const bars = [];
  for (let place = -BARS_ASIDE - 1; place <= BARS_ASIDE + 1; place++) bars.push(<Bar key={place} place={place} width={width} pos={pos} />);

  return (
    <GestureDetector gesture={pan}>
      <View style={[styles.root, { width }]}>
        <Animated.View style={[styles.card, { width: width - CARD_INSET * 2 }, stageStyle]}>
          {moods.map((mood, index) => (
            <Character key={index} index={index} mood={mood} width={width} still={still} {...motion} />
          ))}
        </Animated.View>

        <Text style={[styles.title, { width }]}>{title}</Text>

        <View pointerEvents="none" style={[styles.bars, { width }]}>
          {bars}
          <View style={[styles.needle, { left: width / 2 - 1.5 }]}>
            {moods.map((mood, index) => (
              <NeedleTone key={index} index={index} mood={mood} pos={pos} />
            ))}
          </View>
        </View>

        <Animated.View style={[styles.row, rowStyle]}>
          {moods.map((mood, index) => (
            <Chip
              key={index}
              index={index}
              mood={mood}
              fill={fill}
              filled={filled}
              selected={index === selected}
              onLayout={onChip(index)}
              onPress={() => select(index)}
            />
          ))}
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    height: HEIGHT,
    overflow: 'hidden',
  },
  card: {
    position: 'absolute',
    left: CARD_INSET,
    top: 0,
    height: CARD_H,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  figure: {
    position: 'absolute',
    top: 0,
    width: FIGURE_W,
    height: CARD_H,
    // Squash and breathe about the point where the character meets the panel's bottom edge.
    transformOrigin: 'bottom',
  },
  zed: {
    position: 'absolute',
    left: FIGURE_W / 2 + 84 - 12,
    top: CARD_H - FIGURE_RISE - 128 - 12,
    width: 24,
    height: 24,
  },
  title: {
    position: 'absolute',
    left: 0,
    top: TITLE_Y - 36,
    height: 72,
    color: '#ffffff',
    fontFamily: SERIF,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
    textAlign: 'center',
  },
  bars: {
    position: 'absolute',
    left: 0,
    top: BARS_Y - BARS_H / 2,
    height: BARS_H,
  },
  bar: {
    position: 'absolute',
    bottom: NEEDLE_DROP,
    width: 2,
    borderRadius: 1,
    backgroundColor: '#ffffff',
  },
  needle: {
    position: 'absolute',
    top: 0,
    width: 3,
    height: BARS_H,
    borderRadius: 1.5,
    overflow: 'hidden',
  },
  row: {
    position: 'absolute',
    left: 0,
    top: CHIPS_Y - CHIP_H / 2,
    // Wider than any screen, so no chip is squeezed by the edge.
    width: 4000,
    flexDirection: 'row',
    gap: CHIP_GAP,
  },
  chip: {
    height: CHIP_H,
    paddingHorizontal: CHIP_PAD,
    borderRadius: CHIP_H / 2,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  chipRim: {
    borderRadius: CHIP_H / 2,
    borderWidth: 1,
    borderColor: '#5a6264',
  },
  chipLabel: {
    fontSize: CHIP_FONT,
    fontWeight: '300',
  },
});
