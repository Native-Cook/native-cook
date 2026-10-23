import { BlurMask, Canvas, Circle, Group, LinearGradient, RadialGradient, RoundedRect, Shadow, Skia, SweepGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Disc = {
  /** Shown under the tray while the disc is in it. */
  name: string;
  /** The print, as four six-digit hex colours: the first two blend corner to corner, the third and the fourth are soft spots on it. */
  colors: string[];
  /** How strong the rainbow sheen is on this print, 0…1. */
  sheen: number;
};

type Props = {
  title?: string;
  /** Shown under the tray while it is empty. */
  prompt?: string;
  action?: string;
  /** Up to six discs, laid out three to a row. */
  discs?: Disc[];
  /** Index of the disc in the tray at first; null leaves the tray empty. */
  initial?: number | null;
  /** Loads three discs one after the other and empties the tray by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the disc that goes into the tray, or null when the tray is emptied. */
  onSelect?: (index: number | null) => void;
  /** Called with the index of the loaded disc when the button is tapped. */
  onContinue?: (index: number | null) => void;
};

const WIDTH = 330;
const HEIGHT = 612;

const TITLE_Y = 12;
// The player: a rounded square with a round well in it.
const TRAY = 240;
const TRAY_Y = 44;
const TRAY_RADIUS = 42;
const WELL_SIZE = 202;
const WELL_X = WIDTH / 2;
const WELL_Y = TRAY_Y + TRAY / 2;
const CAPTION_Y = 308;
// A line under the tray rises this far as it fades in, and sinks as it fades out.
const CAPTION_SHIFT = 4;
const CAPTION_TIME = 0.2;

// A disc in the grid, the gap between two, and a disc in the tray.
const DISC = 94;
const GAP = 12;
const GRID_Y = 336;
const LOADED = 186;
const BUTTON_W = 126;
const BUTTON_H = 52;
const BUTTON_Y = 560;

// The flight (seconds): a dip under the finger, up to the tray, back down to the grid.
const DIP_TIME = 0.08;
const UP_TIME = 0.45;
const DOWN_TIME = 0.4;
// How far a tapped disc shrinks, and how far it swells as it lands.
const DIP = 0.05;
const SWELL = 0.03;
// How far the path bows sideways at its middle.
const BOW = 18;
// Degrees a second the print turns in the tray: once round in six seconds.
const SPIN_RATE = 60;
// The print keeps turning for this many turns, far longer than a disc stays in the tray.
const SPIN_TURNS = 1000;
// The sheen turns this many degrees on the way to the tray, and sways this many in the grid.
const SHEEN_TURN = -40;
const SHIMMER = 5;
const SHIMMER_TIME = 4;

// autoPlay (seconds): rest, a disc every `STEP_GAP`, the tray emptied one gap after the last.
const REST = 0.9;
const STEP_GAP = 1.8;
const LOOP = 8;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const FLY_CURVE = Easing.bezier(0.45, 0, 0.2, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const DISCS: Disc[] = [
  { name: 'Olive Hours', colors: ['#3a3716', '#16150a', '#d9b24a', '#6b5a1e'], sheen: 0.7 },
  { name: 'Crimson Static', colors: ['#8f1020', '#14040a', '#f0364c', '#3a0a12'], sheen: 1 },
  { name: 'Rose Signal', colors: ['#f7a8e0', '#d63fb8', '#ffe1f6', '#b3279a'], sheen: 0.55 },
  { name: 'Ember Coast', colors: ['#ff5a1f', '#c81e0c', '#ffa040', '#9c1408'], sheen: 0.6 },
  { name: 'Silver Tide', colors: ['#2a2d34', '#0b0c0f', '#c9d0da', '#5a6170'], sheen: 0.95 },
  { name: 'Aqua Motel', colors: ['#1a8f93', '#0a4b52', '#b5fff4', '#52d6cd'], sheen: 0.65 },
];

/** `hex` with an opacity, as eight-digit hex. */
function fade(hex: string, opacity: number) {
  return `${hex}${Math.round(opacity * 255)
    .toString(16)
    .padStart(2, '0')}`;
}

// Two opposite rainbow wedges and two faint white ones between them, clockwise from 3 o'clock.
const CLEAR = '#ffffff00';
const RAINBOW = ['#ff2d55', '#ffd60a', '#30d158', '#40c8ff', '#7d5cff'];
const SHEEN_COLORS: string[] = [CLEAR];
const SHEEN_STOPS: number[] = [0];
for (const start of [0.07, 0.57]) {
  SHEEN_COLORS.push(CLEAR, fade('#ffffff', 0.3), CLEAR, CLEAR);
  SHEEN_STOPS.push(start - 0.07, start - 0.02, start + 0.03, start + 0.1);
  RAINBOW.forEach((color, k) => {
    SHEEN_COLORS.push(fade(color, 0.85));
    SHEEN_STOPS.push(start + 0.13 + 0.03 * k);
  });
  SHEEN_COLORS.push(CLEAR);
  SHEEN_STOPS.push(start + 0.29);
}
SHEEN_COLORS.push(CLEAR);
SHEEN_STOPS.push(1);

const UNIT_CIRCLE = Skia.Path.Make().addCircle(0, 0, 1);
const ORIGIN = vec(0, 0);

/** The centre of disc `index`'s place in the grid. */
function slot(index: number) {
  'worklet';
  const pitch = DISC + GAP;
  return { x: WIDTH / 2 + ((index % 3) - 1) * pitch, y: GRID_Y + DISC / 2 + Math.floor(index / 3) * pitch };
}

/** Up to 1 in `up` seconds and back to 0 in `down`. */
function pulse(up: number, down: number) {
  return withSequence(withTiming(1, { duration: up * 1000, ...LINEAR }), withTiming(0, { duration: down * 1000, ...LINEAR }));
}

type DiscArtProps = {
  index: number;
  disc: Disc;
  /** The disc in the tray, or null. */
  tray: number | null;
  /** A clock for the shimmer. */
  seconds: SharedValue<number>;
  still: boolean;
};

/** One compact disc, wherever it is between its place in the grid and the tray. */
function DiscArt({ index, disc, tray, seconds, still }: DiscArtProps) {
  const loaded = tray === index;
  // The way from the grid, 0, to the tray, 1.
  const flight = useSharedValue(loaded ? 1 : 0);
  // The dip as it is tapped, and the swell as it lands in the tray.
  const press = useSharedValue(0);
  const seat = useSharedValue(0);
  // The print's angle, in degrees.
  const angle = useSharedValue(0);
  const was = useRef<boolean | null>(null);

  useEffect(() => {
    const before = was.current;
    was.current = loaded;
    if (before === loaded) return;
    if (still) {
      flight.set(loaded ? 1 : 0);
      angle.set(0);
      return;
    }
    if (loaded) {
      // At first sight the disc is already in the tray; after a tap it dips, then lifts.
      const lift = before === null ? 0 : DIP_TIME;
      const fly = before === null ? 0 : UP_TIME;
      if (before !== null) {
        press.set(pulse(0.08, 0.12));
        flight.set(withDelay(lift * 1000, withTiming(1, { duration: fly * 1000, easing: FLY_CURVE, reduceMotion: ReduceMotion.Never })));
        seat.set(withDelay((lift + fly - 0.06) * 1000, pulse(0.06, 0.22)));
      }
      angle.set(
        withDelay(
          (lift + fly) * 1000,
          withTiming(angle.get() + 360 * SPIN_TURNS, { duration: ((360 * SPIN_TURNS) / SPIN_RATE) * 1000, ...LINEAR }),
        ),
      );
    } else if (before !== null) {
      // It stops turning and comes to rest the way it was printed, by the shorter way round.
      const turned = angle.get() % 360;
      angle.set(turned > 180 ? turned - 360 : turned);
      angle.set(withTiming(0, { duration: DOWN_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      flight.set(withTiming(0, { duration: DOWN_TIME * 1000, easing: FLY_CURVE, reduceMotion: ReduceMotion.Never }));
      seat.set(0);
      if (tray === null) press.set(pulse(0.08, 0.12));
    }
  }, [loaded, tray, still, flight, press, seat, angle]);

  const from = slot(index);
  // The path bows away from the middle column, so two discs swapping pass side by side.
  const side = from.x < WIDTH / 2 ? -1 : 1;

  const place = useDerivedValue(() => {
    const way = flight.get();
    // Off the ground in the middle of the flight.
    const lift = Math.sin(Math.PI * Math.min(1, Math.max(0, way)));
    const size = (DISC + (LOADED - DISC) * way) * (1 - DIP * press.get()) * (1 + SWELL * seat.get());
    return [
      { translateX: from.x + (WELL_X - from.x) * way + side * BOW * lift },
      { translateY: from.y + (WELL_Y - from.y) * way },
      { scale: size / 2 },
    ];
  });
  // The shadow is drawn in the disc's own units, where its radius is 1.
  const shadowDrop = useDerivedValue(() => {
    const way = flight.get();
    const lift = Math.sin(Math.PI * Math.min(1, Math.max(0, way)));
    return (3 + 10 * lift + 2 * way) / ((DISC + (LOADED - DISC) * way) / 2);
  });
  const shadowBlur = useDerivedValue(() => {
    const way = flight.get();
    const lift = Math.sin(Math.PI * Math.min(1, Math.max(0, way)));
    return (4 + 8 * lift + 3 * way) / ((DISC + (LOADED - DISC) * way) / 2);
  });
  const shadowOpacity = useDerivedValue(() => 0.2 + 0.08 * Math.sin(Math.PI * Math.min(1, Math.max(0, flight.get()))));
  const spin = useDerivedValue(() => [{ rotate: (angle.get() * Math.PI) / 180 }]);
  // The sheen belongs to the light: it shifts as the disc changes place, and shimmers a little at rest.
  const sheen = useDerivedValue(() => {
    const way = flight.get();
    const shimmer = still ? 0 : SHIMMER * Math.sin(2 * Math.PI * (seconds.get() / SHIMMER_TIME + index / 3));
    return [{ rotate: ((SHEEN_TURN * way + shimmer * (1 - way)) * Math.PI) / 180 }];
  });

  const colors = [...disc.colors, '#808080', '#808080', '#808080', '#808080'];

  return (
    <Group transform={place}>
      <Circle cx={0} cy={shadowDrop} r={0.99} color="#000000" opacity={shadowOpacity}>
        <BlurMask blur={shadowBlur} style="normal" />
      </Circle>
      <Group clip={UNIT_CIRCLE}>
        <Group transform={spin}>
          <Circle cx={0} cy={0} r={1}>
            <LinearGradient start={vec(-0.75, -0.75)} end={vec(0.75, 0.75)} colors={[colors[0], colors[1]]} />
          </Circle>
          <Circle cx={0} cy={0} r={1}>
            <RadialGradient c={vec(0.42, -0.36)} r={0.8} colors={[colors[2], fade(colors[2], 0)]} />
          </Circle>
          <Circle cx={0} cy={0} r={1}>
            <RadialGradient c={vec(-0.5, 0.45)} r={0.75} colors={[colors[3], fade(colors[3], 0)]} />
          </Circle>
          <Circle cx={-0.3} cy={0.45} r={0.65} style="stroke" strokeWidth={0.035} color="rgba(255,255,255,0.16)" />
          <Circle cx={0.4} cy={-0.55} r={0.5} style="stroke" strokeWidth={0.05} color="rgba(0,0,0,0.14)" />
        </Group>
        <Group transform={sheen}>
          <Circle cx={0} cy={0} r={1} blendMode="screen" opacity={disc.sheen}>
            <SweepGradient c={ORIGIN} colors={SHEEN_COLORS} positions={SHEEN_STOPS} />
          </Circle>
        </Group>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={ORIGIN} r={1} colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.22)']} positions={[0.7, 1]} />
        </Circle>
      </Group>
      <Circle cx={0} cy={0} r={0.985} style="stroke" strokeWidth={0.02} color="rgba(255,255,255,0.45)" />

      {/* The hub: a white ring with a dark edge, a grey ring inside it and the hole. */}
      <Circle cx={0} cy={0} r={0.32} color="#f6f6f6" />
      <Circle cx={0} cy={0} r={0.32} style="stroke" strokeWidth={0.022} color="#1e1e1e" />
      <Circle cx={0} cy={0} r={0.21} color="#cfcfcf" />
      <Circle cx={0} cy={0} r={0.21} style="stroke" strokeWidth={0.016} color="#8f8f8f" />
      <Circle cx={0} cy={0} r={0.105} color="#a6a6a6" />
      <Circle cx={0} cy={0} r={0.105} style="stroke" strokeWidth={0.02} color="#2a2a2a" />
    </Group>
  );
}

/** One of the lines under the tray: it rises in while it applies and sinks out when it does not. */
function Caption({ text, shown, still }: { text: string; shown: boolean; still: boolean }) {
  const style = useAnimatedStyle(() => {
    const timing = { duration: still ? 0 : CAPTION_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    return {
      opacity: withTiming(shown ? 1 : 0, timing),
      transform: [{ translateY: withTiming(shown ? 0 : CAPTION_SHIFT, timing) }],
    };
  });
  return (
    <Animated.Text style={[styles.caption, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function thud() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A disc player seen from above, over a grid of compact discs. Tapping a disc lifts it out of the
 * grid and flies it into the tray, where it grows to the well's size and turns under a rainbow sheen
 * that stays with the light; tapping another one swaps them in mid-air, and tapping the disc in the
 * tray sends it back to its place. The name of the loaded disc shows under the tray.
 */
export function DiscTrayPicker({
  title = 'Welcome to Sidetrack',
  prompt = 'Choose a disc',
  action = 'Continue',
  discs = DISCS,
  initial = null,
  autoPlay = false,
  onSelect,
  onContinue,
}: Props) {
  const still = useReducedMotion();
  const count = Math.min(discs.length, 6);
  const home = initial !== null && initial >= 0 && initial < count ? initial : null;
  const auto = autoPlay && !still && count > 1;

  // The disc in the tray, or null.
  const [tray, setTray] = useState(home);
  // The order the discs are drawn in; the one that moved last is on top.
  const [order, setOrder] = useState(() => Array.from({ length: count }, (_, i) => i).sort((a, b) => Number(a === home) - Number(b === home)));
  // Counts taps: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  const state = useRef({ tray: home, landing: undefined as ReturnType<typeof setTimeout> | undefined });

  const seconds = useSharedValue(0);
  const buttonPress = useSharedValue(0);

  useFrameCallback((frame) => {
    seconds.set(seconds.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !still);

  /**
   * Puts disc `index` in the tray (null empties it). The disc that was there flies back to its
   * place while the new one flies up.
   */
  const select = (index: number | null) => {
    if (index === state.current.tray || (index !== null && (index < 0 || index >= count))) return;
    clearTimeout(state.current.landing);
    state.current.tray = index;
    setTray(index);
    if (index !== null) setOrder((drawn) => [...drawn.filter((i) => i !== index), index]);
    onSelect?.(index);
  };

  /** A tap on disc `index`: into the tray, or back out of it when it is the one loaded. */
  const pick = (index: number) => {
    setTouches((n) => n + 1);
    tick();
    const next = state.current.tray === index ? null : index;
    select(next);
    // A disc put in by hand is felt as it lands.
    if (next !== null && !still) state.current.landing = setTimeout(thud, (DIP_TIME + UP_TIME) * 1000);
  };

  const proceed = () => {
    setTouches((n) => n + 1);
    tick();
    if (!still) buttonPress.set(pulse(0.08, 0.16));
    onContinue?.(state.current.tray);
  };

  // The timers below call whatever `select` is at the time they fire.
  const steps = useRef({ select });
  useEffect(() => {
    steps.current = { select };
  });

  useEffect(() => {
    const waiting = state.current;
    return () => clearTimeout(waiting.landing);
  }, []);

  // The script a finger would follow: the second disc, the last, the one before it, then the tray is emptied.
  useEffect(() => {
    if (!auto) return;
    const picks: number[] = [];
    for (const disc of [1, count - 1, count - 2]) {
      if (disc >= 0 && disc !== (picks.length > 0 ? picks[picks.length - 1] : home)) picks.push(disc);
    }
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = n % (picks.length + 1);
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / (picks.length + 1)) * LOOP + REST + step * STEP_GAP) * 1000;
      timer = setTimeout(() => {
        steps.current.select(step < picks.length ? picks[step] : home);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, put the tray back the way it began and start over.
      timer = setTimeout(() => {
        steps.current.select(home);
        run(Date.now() + 700, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, count, home]);

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * buttonPress.get() }] }));

  return (
    <View style={styles.root}>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      <Caption text={prompt} shown={tray === null} still={still} />
      {discs.slice(0, count).map((disc, index) => (
        <Caption key={index} text={disc.name} shown={tray === index} still={still} />
      ))}

      {/* The discs fly over the lines above, so the canvas comes after them. */}
      <Canvas style={styles.canvas} pointerEvents="none">
        <RoundedRect x={(WIDTH - TRAY) / 2} y={TRAY_Y} width={TRAY} height={TRAY} r={TRAY_RADIUS}>
          <LinearGradient start={vec(0, TRAY_Y)} end={vec(0, TRAY_Y + TRAY)} colors={['#e1e1e1', '#d5d5d5']} />
          <Shadow dx={0} dy={10} blur={15} color="rgba(0,0,0,0.1)" />
        </RoundedRect>
        <Circle cx={WELL_X} cy={WELL_Y} r={WELL_SIZE / 2}>
          <RadialGradient c={vec(WELL_X, WELL_Y)} r={WELL_SIZE / 2} colors={['#bababa', '#b0b0b0']} />
        </Circle>
        <Circle cx={WELL_X} cy={WELL_Y} r={WELL_SIZE / 2 - 1.5} style="stroke" strokeWidth={3} color="#c6c6c6" />
        <Circle cx={WELL_X} cy={WELL_Y} r={WELL_SIZE / 2 - 3.5} style="stroke" strokeWidth={1} color="rgba(0,0,0,0.05)" />
        <Circle cx={WELL_X} cy={WELL_Y} r={5} color="#9c9c9c" />
        {discs.slice(0, count).map((_, index) => (
          <Circle key={index} cx={slot(index).x} cy={slot(index).y} r={DISC / 2} color="#e6e6e6" />
        ))}
        {order.map((index) => (
          <DiscArt key={index} index={index} disc={discs[index]} tray={tray} seconds={seconds} still={still} />
        ))}
      </Canvas>

      <Pressable
        style={styles.well}
        disabled={tray === null}
        accessibilityRole="button"
        accessibilityLabel={tray === null ? prompt : discs[tray].name}
        onPress={() => {
          if (tray !== null) pick(tray);
        }}
      />
      {discs.slice(0, count).map((disc, index) => (
        <Pressable
          key={index}
          style={[styles.slot, { left: slot(index).x - (DISC + GAP) / 2, top: slot(index).y - (DISC + GAP) / 2 }]}
          disabled={tray === index}
          accessibilityRole="button"
          accessibilityLabel={disc.name}
          accessibilityState={{ selected: tray === index }}
          onPress={() => pick(index)}
        />
      ))}
      <Animated.View style={[styles.button, buttonStyle]}>
        <Pressable style={styles.buttonTouch} accessibilityRole="button" onPress={proceed}>
          <Text style={styles.buttonLabel} numberOfLines={1}>
            {action}
          </Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  title: {
    position: 'absolute',
    left: 0,
    top: TITLE_Y - 12,
    width: WIDTH,
    height: 24,
    lineHeight: 24,
    textAlign: 'center',
    color: '#1a1a1a',
    fontSize: 17,
    fontWeight: '700',
    includeFontPadding: false,
  },
  caption: {
    position: 'absolute',
    left: 0,
    top: CAPTION_Y - 10,
    width: WIDTH,
    height: 20,
    lineHeight: 20,
    textAlign: 'center',
    color: '#8a8a8a',
    fontSize: 13,
    fontWeight: '500',
    includeFontPadding: false,
  },
  well: {
    position: 'absolute',
    left: WELL_X - LOADED / 2,
    top: WELL_Y - LOADED / 2,
    width: LOADED,
    height: LOADED,
    borderRadius: LOADED / 2,
  },
  slot: {
    position: 'absolute',
    width: DISC + GAP,
    height: DISC + GAP,
  },
  button: {
    position: 'absolute',
    left: WIDTH / 2 - BUTTON_W / 2,
    top: BUTTON_Y,
    width: BUTTON_W,
    height: BUTTON_H,
    borderRadius: BUTTON_H / 2,
    backgroundColor: '#111111',
  },
  buttonTouch: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
});
