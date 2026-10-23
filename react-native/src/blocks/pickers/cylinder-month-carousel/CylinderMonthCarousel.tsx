import Ionicons from '@expo/vector-icons/Ionicons';
import * as Haptics from 'expo-haptics';
import { memo, useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, Ellipse, G, LinearGradient, Path, RadialGradient, Rect, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** The landscape drawn on a card. */
type Scene = {
  /** The sky, from the top of the card down to the horizon. */
  sky: string;
  haze: string;
  sun: string;
  /** Three ranges of hills, from the furthest to the ground in front. */
  far: string;
  mid: string;
  near: string;
  /** The round trees and the pines that stand on the middle range. */
  tree: string;
  pine: string;
  /** Where the sun stands across the card, 0…1. */
  sunX: number;
  /** Any number: it shapes the hills and places the trees. */
  seed: number;
};

type Card = {
  title: string;
  artist: string;
  /** A lock in the card's corner instead of a heart. */
  locked: boolean;
  /** The colour the card casts under the carousel. */
  glow: string;
  scene: Scene;
};

type Props = {
  month?: string;
  cards?: Card[];
  /** Index of the card in front at first, and the one the self-playing demo rests on. */
  initial?: number;
  /** Turns and flings the drum by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the card the drum is sent to. */
  onSelect?: (index: number) => void;
};

const MAX_WIDTH = 430;
const HEIGHT = 510;

// The cards and the drum they stand on.
const CARD_W = 225;
const CARD_H = 400;
const CARD_RADIUS = 28;
const RADIUS = 210;
// Degrees between two cards.
const TURN = 56;
const EYE = 800;
// How dark a card one place from the front is.
const SHADE = 0.4;
const CAPTION_RISE = 8;

// The ticks, the month and the glow.
const TICKS_Y = 436;
const TICK_GAP = 18;
// How many places from the front the ticks start to bunch.
const TICK_BUNCH = 4.5;
const HAND_OVER = 0.15;
const MONTH_Y = 482;
const GLOW_Y = 448;
const GLOW_W = 200;
const GLOW_H = 62;

// Turning.
// Points of drag that turn the drum by one card.
const DRAG_STEP = 205;
// Seconds of the finger's speed the drum coasts for.
const COAST = 0.2;
const STIFFNESS = 180;
const DAMPING_RATIO = 0.63;
const SPIN_TIME = 1.2;
const BAND = 0.35;
const TAP_SLOP = 6;
// The drum has landed once it is this close (in cards) to its target.
const LAND_WITHIN = 0.03;
const CAPTION_IN = 0.25;
const CAPTION_OUT = 0.12;

// autoPlay (seconds): when each turn starts, and how many cards from the resting one it goes to.
const LOOP = 8;
const SCRIPT = [
  { at: 1.0, by: 1, spin: false },
  { at: 2.3, by: 4, spin: false },
  { at: 4.1, by: 5, spin: false },
  { at: 5.3, by: 0, spin: true },
];
// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting card.
const RESUME_AFTER = 2;
const HOME_TIME = 1.2;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
// Ease-in-out that runs a little past the end and comes back.
const SPIN_CURVE = Easing.bezier(0.5, 0, 0.3, 1.1);

const ARTIST = 'Mara Lindqvist';

const CARDS: Card[] = [
  {
    title: 'Autumn Aura',
    artist: ARTIST,
    locked: true,
    glow: '#b3261a',
    scene: { sky: '#3a3d42', haze: '#b9a58c', sun: '#f3d9a4', far: '#5a4a44', mid: '#8f2f1c', near: '#c2691f', tree: '#c8331b', pine: '#1f2326', sunX: 0.7, seed: 0.4 },
  },
  {
    title: 'Rust Valley',
    artist: ARTIST,
    locked: true,
    glow: '#c2541d',
    scene: { sky: '#2b2f3a', haze: '#e09a5a', sun: '#ffd9a0', far: '#6b3a2e', mid: '#a8431f', near: '#3b2a25', tree: '#e2582a', pine: '#2a1d1a', sunX: 0.3, seed: 1.7 },
  },
  {
    title: 'First Frost',
    artist: ARTIST,
    locked: false,
    glow: '#6f8fa8',
    scene: { sky: '#5b6b7a', haze: '#dfe6ea', sun: '#ffffff', far: '#8394a3', mid: '#51616f', near: '#c9d3d9', tree: '#a23a26', pine: '#2b3a45', sunX: 0.55, seed: 2.9 },
  },
  {
    title: 'Ember Lake',
    artist: ARTIST,
    locked: true,
    glow: '#d2441c',
    scene: { sky: '#1c1e2a', haze: '#e2552b', sun: '#ffb36b', far: '#40222b', mid: '#7a1f1f', near: '#1a1416', tree: '#f07a2a', pine: '#120e10', sunX: 0.42, seed: 4.2 },
  },
  {
    title: 'Cinder Ridge',
    artist: ARTIST,
    locked: false,
    glow: '#8a3a22',
    scene: { sky: '#23252b', haze: '#7a7f88', sun: '#d8d2c4', far: '#4a4d55', mid: '#2e3036', near: '#8a3a22', tree: '#b8421f', pine: '#16181c', sunX: 0.78, seed: 5.6 },
  },
  {
    title: 'Maple Hollow',
    artist: ARTIST,
    locked: true,
    glow: '#e07a1f',
    scene: { sky: '#40342f', haze: '#f0c27a', sun: '#fff0c0', far: '#a05a2c', mid: '#d1452a', near: '#5a2a1c', tree: '#f2a23a', pine: '#3a1c14', sunX: 0.25, seed: 6.8 },
  },
  {
    title: 'Amber Dusk',
    artist: ARTIST,
    locked: false,
    glow: '#c8641f',
    scene: { sky: '#2a2440', haze: '#f2a65a', sun: '#ffe1a8', far: '#5a3a5a', mid: '#8a3a3a', near: '#2a1c26', tree: '#f26a3a', pine: '#1c1422', sunX: 0.62, seed: 8.1 },
  },
  {
    title: 'Charcoal Pines',
    artist: ARTIST,
    locked: true,
    glow: '#56606a',
    scene: { sky: '#15181c', haze: '#56606a', sun: '#c8d0d6', far: '#2e353c', mid: '#1d2227', near: '#3a4148', tree: '#7a2a1c', pine: '#0e1114', sunX: 0.35, seed: 9.3 },
  },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** One layer of a stack that cross-fades with the drum: the layer of the card below shows through the next one fading in. */
function layerOpacity(index: number, p: number) {
  'worklet';
  if (p >= index) return p - index < 1 ? 1 : 0;
  return clamp01(1 - (index - p));
}

const TAU = Math.PI * 2;

/** A range of hills: a sine wave across the card at the height `level`, filled down to the bottom. */
function ridge(level: number, swell: number, waves: number, turn: number) {
  let d = `M0 ${CARD_H}`;
  for (let step = 0; step <= 24; step++) {
    const u = step / 24;
    d += `L${(u * CARD_W).toFixed(1)} ${(CARD_H * (level + swell * Math.sin(u * TAU * waves + turn))).toFixed(1)}`;
  }
  return `${d}L${CARD_W} ${CARD_H}Z`;
}

/** The picture on a card. It never changes, so it is drawn once and only moved afterwards. */
const Art = memo(function Art({ scene }: { scene: Scene }) {
  const w = CARD_W;
  const h = CARD_H;
  const { seed } = scene;
  const sunX = w * scene.sunX;
  const sunY = h * (0.16 + 0.04 * Math.sin(seed * 5));
  const trees = [0, 1, 2, 3, 4, 5].map((k) => {
    const u = (k + 0.5 + 0.35 * Math.sin(seed * 7 + k * 2.4)) / 6;
    const x = u * w;
    const base = h * (0.62 + 0.06 * Math.sin(u * TAU * 0.8 + seed * 2.3 + 1)) + 8;
    const tall = 44 + 26 * (0.5 + 0.5 * Math.sin(seed * 3 + k * 1.7));
    if (k % 2 === 1) return <Path key={k} d={`M${x - tall * 0.24} ${base}L${x} ${base - tall * 1.15}L${x + tall * 0.24} ${base}Z`} fill={scene.pine} />;
    return (
      <G key={k}>
        <Rect x={x - 1.5} y={base - tall * 0.5} width={3} height={tall * 0.5} fill={scene.pine} />
        <Ellipse cx={x} cy={base - tall * 0.61} rx={tall * 0.3} ry={tall * 0.39} fill={scene.tree} />
      </G>
    );
  });
  return (
    <Svg width={w} height={h}>
      <Defs>
        <LinearGradient id="sky" x1={0} y1={0} x2={0} y2={h * 0.62} gradientUnits="userSpaceOnUse">
          <Stop offset={0} stopColor={scene.sky} />
          <Stop offset={1} stopColor={scene.haze} />
        </LinearGradient>
        {/* The bottom darkens so the artist's pill reads on any picture. */}
        <LinearGradient id="foot" x1={0} y1={h * 0.6} x2={0} y2={h} gradientUnits="userSpaceOnUse">
          <Stop offset={0} stopColor="#000000" stopOpacity={0} />
          <Stop offset={1} stopColor="#000000" stopOpacity={0.4} />
        </LinearGradient>
      </Defs>
      <Rect width={w} height={h} fill="url(#sky)" />
      <Circle cx={sunX} cy={sunY} r={62} fill={scene.sun} opacity={0.22} />
      <Circle cx={sunX} cy={sunY} r={30} fill={scene.sun} />
      <Path d={ridge(0.5, 0.05, 1.1, seed)} fill={scene.far} />
      <Path d={ridge(0.62, 0.06, 0.8, seed * 2.3 + 1)} fill={scene.mid} />
      {trees}
      <Path d={ridge(0.8, 0.035, 0.6, seed * 3.7 + 2)} fill={scene.near} />
      <Rect y={h * 0.6} width={w} height={h * 0.4} fill="url(#foot)" />
    </Svg>
  );
});

type Motion = {
  /** The drum's position as a card index; whole numbers are the snap points. */
  pos: SharedValue<number>;
  /** The front card's title and artist: 0 hidden … 1 shown. */
  caption: SharedValue<number>;
  /** The card that carries the captions: the one the drum last landed on. */
  captioned: SharedValue<number>;
};

/** One card: its picture, the shade it takes as it turns away, its badge and, in front, its captions. */
function CardView({ index, card, width, pos, caption, captioned }: Motion & { index: number; card: Card; width: number }) {
  // Where the card stands: on a drum of radius `RADIUS`, seen from `EYE` points away, so it moves
  // sideways less and shrinks as it goes round.
  const place = useAnimatedStyle(() => {
    const d = index - pos.get();
    const angle = (d * TURN * Math.PI) / 180;
    const scale = EYE / (EYE + RADIUS * (1 - Math.cos(angle)));
    return {
      opacity: Math.abs(d) * TURN < 89 ? 1 : 0,
      zIndex: Math.round(1000 - Math.abs(d) * 100),
      transform: [{ translateX: RADIUS * Math.sin(angle) * scale }, { scale }, { perspective: EYE }, { rotateY: `${d * TURN}deg` }],
    };
  });
  const shade = useAnimatedStyle(() => ({ opacity: SHADE * Math.min(1, Math.abs(index - pos.get())) }));
  const words = useAnimatedStyle(() => {
    const shown = captioned.get() === index ? caption.get() : 0;
    return { opacity: shown, transform: [{ translateY: (1 - shown) * CAPTION_RISE }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.card, { left: (width - CARD_W) / 2 }, place]}>
      <Art scene={card.scene} />
      <Animated.View style={[StyleSheet.absoluteFill, styles.shade, shade]} />
      <View style={styles.badge}>
        <Ionicons name={card.locked ? 'lock-closed' : 'heart-outline'} size={card.locked ? 14 : 16} color="#ffffff" />
      </View>
      <Animated.View style={[StyleSheet.absoluteFill, words]}>
        <Text style={styles.title} numberOfLines={1}>
          {card.title}
        </Text>
        <View style={styles.artist}>
          <Text style={styles.artistName} numberOfLines={1}>
            {card.artist}
          </Text>
        </View>
      </Animated.View>
    </Animated.View>
  );
}

/** One tick of the strip. The tick under the front card is white, thicker and taller; it hands over half-way. */
function Tick({ index, width, pos }: { index: number; width: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = index - pos.get();
    const lit = clamp01((0.5 - Math.abs(d)) / HAND_OVER + 0.5);
    return {
      opacity: mix(0.35, 1, lit),
      transform: [{ translateX: TICK_GAP * TICK_BUNCH * Math.tanh(d / TICK_BUNCH) }, { scaleX: mix(2 / 3, 1, lit) }, { scaleY: mix(20 / 26, 1, lit) }],
    };
  });
  return <Animated.View style={[styles.tick, { left: width / 2 - 1.5 }, style]} />;
}

/** The glow one card casts under the carousel; the stack of them cross-fades as the drum turns. */
function Glow({ index, card, width, pos }: { index: number; card: Card; width: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: layerOpacity(index, pos.get()) }));
  return (
    <Animated.View pointerEvents="none" style={[styles.glow, { left: width / 2 - GLOW_W }, style]}>
      <Svg width={GLOW_W * 2} height={GLOW_H * 2}>
        <Defs>
          <RadialGradient id="glow" cx="50%" cy="50%" rx="50%" ry="50%">
            <Stop offset={0} stopColor={card.glow} stopOpacity={0.55} />
            <Stop offset={0.5} stopColor={card.glow} stopOpacity={0.2} />
            <Stop offset={1} stopColor={card.glow} stopOpacity={0} />
          </RadialGradient>
        </Defs>
        <Ellipse cx={GLOW_W} cy={GLOW_H} rx={GLOW_W} ry={GLOW_H} fill="url(#glow)" />
      </Svg>
    </Animated.View>
  );
}

/**
 * A month of tall picture cards standing on a drum seen from outside. The card in front faces the
 * viewer; its neighbours are turned away around the vertical axis, pushed back and darkened. A
 * horizontal drag turns the drum, which snaps to the nearest card with a small overshoot. Under it a
 * strip of ticks, one per card and bunched towards its far end, slides to keep the front card's tick
 * in the middle, above the month's name and a glow in the front card's colour.
 */
export function CylinderMonthCarousel({ month = 'Sep 2024', cards = CARDS, initial = 0, autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && cards.length > 1;
  const last = cards.length - 1;
  const home = Math.min(Math.max(initial, 0), last);
  const width = Math.min(useWindowDimensions().width, MAX_WIDTH);

  const pos = useSharedValue(home);
  const caption = useSharedValue(1);
  // The card the drum is at, or heading for.
  const target = useSharedValue(home);
  const captioned = useSharedValue(home);
  // A turn is under way; it ends when the drum first reaches its target.
  const moving = useSharedValue(false);
  const held = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // Where the finger went down, to tell a tap on a side card.
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  // The card in front at the last frame, for the haptic ticks.
  const ticked = useSharedValue(home);
  // The turns come from a finger, so they tick; the script's do not.
  const felt = useSharedValue(false);
  const motion: Motion = { pos, caption, captioned };

  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((count) => count + 1);
  const chosen = (index: number) => onSelect?.(index);

  /**
   * Sends the drum to card `index`: on a spring that is softer the further it has to turn, or, for the
   * script's long way home, on an ease-in-out spin that overshoots a little.
   */
  const go = (index: number, velocity: number, spin: boolean) => {
    'worklet';
    const to = Math.min(Math.max(Math.round(index), 0), last);
    const far = Math.abs(to - pos.get());
    if (far > LAND_WITHIN) caption.set(withTiming(0, { duration: CAPTION_OUT * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    const changed = to !== target.get();
    target.set(to);
    moving.set(true);
    if (still) {
      pos.set(to);
    } else if (spin) {
      pos.set(withTiming(to, { duration: SPIN_TIME * 1000, easing: SPIN_CURVE, reduceMotion: ReduceMotion.Never }));
    } else {
      const stiffness = STIFFNESS / Math.max(1, far) ** 0.75;
      pos.set(withSpring(to, { mass: 1, stiffness, damping: DAMPING_RATIO * 2 * Math.sqrt(stiffness), velocity, reduceMotion: ReduceMotion.Never }));
    }
    return changed;
  };

  const select = (index: number, spin = false) => {
    if (go(index, 0, spin)) chosen(Math.min(Math.max(index, 0), last));
  };

  // Ticks as cards pass the front, and notices the landing: the captions come back.
  useAnimatedReaction(
    () => {
      // Read here so that a new turn or a lifted finger is noticed even while the drum stands still.
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
        captioned.set(target.get());
        caption.set(withTiming(1, { duration: CAPTION_IN * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      }
    },
  );

  const pan = Gesture.Pan()
    .minDistance(TAP_SLOP)
    .onBegin((event) => {
      // The finger catches the drum wherever it is.
      cancelAnimation(pos);
      held.set(true);
      felt.set(true);
      downX.set(event.x);
      downY.set(event.y);
      scheduleOnRN(touch);
    })
    .onStart(() => {
      dragFrom.set(pos.get());
      moving.set(true);
      caption.set(withTiming(0, { duration: CAPTION_OUT * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    })
    .onUpdate((event) => {
      // Past either end the drum follows the finger at a third of its speed.
      const p = dragFrom.get() - event.translationX / DRAG_STEP;
      pos.set(p < 0 ? p * BAND : p > last ? last + (p - last) * BAND : p);
    })
    .onEnd((event) => {
      // The drum keeps the finger's speed and settles on the card it would coast to.
      held.set(false);
      const speed = -event.velocityX / DRAG_STEP;
      const to = Math.min(Math.max(Math.round(pos.get() + speed * COAST), 0), last);
      if (go(to, speed, false)) scheduleOnRN(chosen, to);
    })
    .onFinalize((_event, success) => {
      held.set(false);
      if (!success) {
        // A tap on a card at the side brings it to the front; anywhere else the drum settles where it was caught.
        const side = downY.get() < CARD_H && Math.abs(downX.get() - width / 2) > CARD_W / 2 ? Math.sign(downX.get() - width / 2) : 0;
        const to = Math.min(Math.max(Math.round(pos.get()) + side, 0), last);
        if (go(to, 0, false)) scheduleOnRN(chosen, to);
      }
      scheduleOnRN(touch);
    });

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ select });
  useEffect(() => {
    steps.current = { select };
  });

  // The script a finger would follow: a turn, a fling, a turn, and a long spin home.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        steps.current.select(home + step.by, step.spin);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the resting card, and start over from there.
      timer = setTimeout(() => {
        if (held.get()) return;
        felt.set(false);
        steps.current.select(home);
        run(Date.now() + HOME_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, home, held, felt]);

  return (
    <GestureDetector gesture={pan}>
      <View style={[styles.root, { width }]}>
        {cards.map((card, index) => (
          <Glow key={index} index={index} card={card} width={width} pos={pos} />
        ))}
        {cards.map((_, index) => (
          <Tick key={index} index={index} width={width} pos={pos} />
        ))}
        {cards.map((card, index) => (
          <CardView key={index} index={index} card={card} width={width} {...motion} />
        ))}
        <Text style={[styles.month, { width }]} numberOfLines={1}>
          {month}
        </Text>
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
    top: 0,
    width: CARD_W,
    height: CARD_H,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  shade: {
    backgroundColor: '#000000',
  },
  badge: {
    position: 'absolute',
    right: 12,
    top: 12,
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: CARD_H / 3 - 13,
    height: 26,
    lineHeight: 26,
    color: '#ffffff',
    fontSize: 20,
    fontWeight: '500',
    letterSpacing: 1.5,
    textAlign: 'center',
    textShadowColor: 'rgba(0, 0, 0, 0.35)',
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 6,
  },
  artist: {
    position: 'absolute',
    alignSelf: 'center',
    top: CARD_H - 54,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    justifyContent: 'center',
  },
  artistName: {
    color: '#ffffff',
    fontSize: 11,
  },
  tick: {
    position: 'absolute',
    top: TICKS_Y - 13,
    width: 3,
    height: 26,
    borderRadius: 1,
    backgroundColor: '#ffffff',
  },
  glow: {
    position: 'absolute',
    top: GLOW_Y - GLOW_H,
    width: GLOW_W * 2,
    height: GLOW_H * 2,
  },
  month: {
    position: 'absolute',
    left: 0,
    top: MONTH_Y - 14,
    height: 28,
    lineHeight: 28,
    color: '#ffffff',
    fontSize: 21,
    fontWeight: '600',
    textAlign: 'center',
  },
});
