import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, Polyline, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type DestinationIcon = 'snow' | 'sun' | 'hills' | 'sail';

export type Destination = {
  title: string;
  /** Second line of the title, in grey. */
  subtitle: string;
  caption: string;
  /** The small pill on the photo. */
  tag: string;
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
  /** Colour of the glow behind the stack while this card is in front. */
  glow: string;
  icon: DestinationIcon;
};

type Props = {
  /** The cards, front first. At least four. */
  items?: Destination[];
  /** Swipes a card away every two seconds, as if a finger did it. */
  autoPlay?: boolean;
  /** Colour of the surface the stack sits on, as #rrggbb: the dropped card fades into it at the bottom edge. */
  canvas?: string;
  /** Called with the index of the card that is now in front. */
  onChange?: (index: number) => void;
  /** Called with the index of the front card when its arrow button is tapped. */
  onOpen?: (index: number) => void;
};

const WIDTH = 340;
const HEIGHT = 615;
// Centre of the stack, measured from the top of the component.
const CENTER_Y = 270;
const CARD_WIDTH = 236;
const CARD_HEIGHT = 342;
const CARD_RADIUS = 20;
const ICON_SIZE = 18;
// The cards may reach this far past the sides and the top before they are cut.
const BLEED = 40;
// Height of the strip at the bottom where the dropped card fades into the canvas.
const FADE = 96;
const GLOW_RADIUS = 250;
const GLOW_STRETCH = 1.15;

// Where a card rests in each place of the deck: offset from the centre, angle in degrees, scale.
const GHOST_A = { x: 16, y: -8, rotation: 12, scale: 0.97, ghost: 0.34 };
const GHOST_B = { x: -18, y: 2, rotation: -15, scale: 0.94, ghost: 0.2 };
const DROP_X = 12;
const DROP_Y = 359;
const DROP_ROTATION = -7;
const DROP_SCALE = 0.84;
const DROP_ALPHA = 0.5;
const DROP_BLUR = 5;

// Seconds one swipe takes from the first tilt to the last ghost settling.
const SWIPE = 1.1;
const SWIPE_REDUCED = 0.35;
// autoPlay (seconds): the default deck of four goes round once per LOOP, the first swipe starting
// at FIRST and the next ones every STEP.
const LOOP = 8;
const FIRST = 0.8;
const STEP = LOOP / 4;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 2.5;

// The spring the arriving cards turn on.
const STIFFNESS = 160;
const DAMPING = 15;
const MASS = 1;
// The spring that brings a card back when it is let go too early.
const RETURN_STIFFNESS = 260;
const RETURN_DAMPING = 24;

// Share of the finger's travel the front card follows, sideways and down, and upwards.
const DRAG_GAIN = 0.55;
const DRAG_GAIN_UP = 0.2;
// Degrees of tilt per point of sideways drag.
const DRAG_TILT = 0.06;
// Finger travel that sends the card away on release.
const COMMIT = 56;
const DRAG_SLOP = 6;

const photo = (id: string) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=600&h=460&q=80`;

const DESTINATIONS: Destination[] = [
  {
    title: 'Ama Dablam',
    subtitle: 'Himalaya',
    caption: 'A sharp granite peak above the Khumbu valley, best seen at first light.',
    tag: '12 days · $2,490',
    photo: photo('1454496522488-7a8e488e8606'),
    tint: '#8a9bb4',
    glow: '#a9a4f5',
    icon: 'snow',
  },
  {
    title: 'Fire Valley',
    subtitle: 'Nevada',
    caption: 'Red sandstone walls and an empty road running straight into the heat.',
    tag: '4 days · $640',
    photo: photo('1500530855697-b586d89ba3ee'),
    tint: '#9a5a44',
    glow: '#f5a95c',
    icon: 'sun',
  },
  {
    title: 'Fairy Glen',
    subtitle: 'Isle of Skye',
    caption: 'Grassy cones and a spiral of stones in the hills above the village.',
    tag: '3 days · $380',
    photo: photo('1472214103451-9374bd1c798e'),
    tint: '#5c8a3a',
    glow: '#9fd67c',
    icon: 'hills',
  },
  {
    title: 'Blue Lagoon',
    subtitle: 'Maldives',
    caption: 'Water villas on a ring of coral, a short seaplane hop from the capital.',
    tag: '7 days · $3,150',
    photo: photo('1514282401047-d79a71a590e8'),
    tint: '#3fb6c4',
    glow: '#62d3d6',
    icon: 'sail',
  },
];

// Polylines of each icon in a 20 × 20 box, as "x,y x,y" points, and its circles as [cx, cy, r].
const STROKES: Record<DestinationIcon, string[]> = {
  snow: [
    '10,2.5 10,17.5',
    '3.5,6.25 16.5,13.75',
    '3.5,13.75 16.5,6.25',
    '7.8,4.2 10,6.2 12.2,4.2',
    '13.92,5.19 13.29,8.1 16.12,9',
    '16.12,11 13.29,11.9 13.92,14.81',
    '12.2,15.8 10,13.8 7.8,15.8',
    '6.08,14.81 6.71,11.9 3.88,11',
    '3.88,9 6.71,8.1 6.08,5.19',
  ],
  sun: [
    '10,1.8 10,3.8',
    '10,16.2 10,18.2',
    '1.8,10 3.8,10',
    '16.2,10 18.2,10',
    '4.2,4.2 5.6,5.6',
    '15.8,4.2 14.4,5.6',
    '4.2,15.8 5.6,14.4',
    '15.8,15.8 14.4,14.4',
  ],
  hills: ['1.8,16.2 7.4,6.2 10.6,11.4 12.9,8.2 18.2,16.2 1.8,16.2'],
  sail: [
    '10,2.6 10,12',
    '10,3.4 15.6,12 10,12',
    '8.4,6 4.6,12 8.4,12 8.4,6',
    '2.8,14.6 17.2,14.6 14.6,17.6 5.4,17.6 2.8,14.6',
  ],
};
const CIRCLES: Record<DestinationIcon, number[][]> = {
  snow: [],
  sun: [[10, 10, 3.3]],
  hills: [[14.9, 4.6, 1.5]],
  sail: [],
};

type Pose = {
  x: number;
  y: number;
  rotation: number;
  scale: number;
  /** Opacity of the whole card. */
  alpha: number;
  /** Opacity of the grey body a card shows while it waits behind. */
  ghost: number;
  /** Opacity of the dark face with the photo and the texts. */
  face: number;
  blur: number;
  z: number;
};

function wrapIndex(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

function segment(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}

function easeOut(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(2 - 2 * t, 3) / 2;
}

function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Step response of the arriving cards' spring `seconds` after it is released: 0 → 1 with one overshoot. */
function spring(seconds: number) {
  'worklet';
  if (seconds <= 0) return 0;
  const omega = Math.sqrt(STIFFNESS / MASS);
  const zeta = DAMPING / (2 * Math.sqrt(STIFFNESS * MASS));
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * seconds) * (Math.cos(damped * seconds) + ((zeta * omega) / damped) * Math.sin(damped * seconds));
}

/** The spring started `delay` into a swipe of which `part` (0…1) has passed, landing exactly on 1. */
function settle(part: number, delay: number) {
  'worklet';
  return mix(spring((part - delay) * SWIPE), 1, smooth(segment(part, 0.85, 1)));
}

/** Cards swiped away `clock` seconds into the autoPlay loop. */
function script(clock: number, count: number) {
  'worklet';
  let done = 0;
  for (let index = 0; index < count; index++) {
    done += segment(clock, FIRST + STEP * index, FIRST + STEP * index + SWIPE);
  }
  return done;
}

/**
 * Place of card `index` in the deck: 0 in front, 1 and 2 the ghosts behind, `count - 1` the dropped
 * card, and below 0 on its way down.
 */
function relative(index: number, pos: number, count: number) {
  'worklet';
  let place = (index - pos) % count;
  if (place <= -1) place += count;
  if (place > count - 1) place -= count;
  return place;
}

/** Share of the glow that belongs to the card at `place`. */
function glowWeight(place: number) {
  'worklet';
  return smooth(Math.max(0, 1 - Math.abs(place)));
}

function pose(place: number, count: number, dragX: number, dragY: number, reduced: boolean): Pose {
  'worklet';
  // Reduced motion: every card jumps to its next place half-way while the deck fades through.
  let fade = 1;
  if (reduced) {
    const nearest = Math.round(place);
    fade = 1 - 2 * Math.abs(place - nearest);
    place = nearest;
    dragX = 0;
    dragY = 0;
  }

  const p: Pose = { x: 0, y: 0, rotation: 0, scale: 1, alpha: 1, ghost: GHOST_A.ghost, face: 0, blur: 0, z: 1 };
  const last = count - 1;
  if (place <= 0) {
    // The front card, on its way down: a quick tilt one way, then the fall turns it the other way.
    const t = -place;
    const fall = easeInOut(segment(t, 0, 0.62));
    p.x = DROP_X * fall + dragX;
    p.y = DROP_Y * fall + dragY;
    p.rotation = 10 * easeOut(segment(t, 0, 0.28)) + (DROP_ROTATION - 10) * easeInOut(segment(t, 0.22, 0.8)) + dragX * DRAG_TILT;
    p.scale = mix(1, DROP_SCALE, fall);
    p.alpha = mix(1, DROP_ALPHA, smooth(segment(t, 0.1, 0.62)));
    p.blur = DROP_BLUR * smooth(segment(t, 0.08, 0.6));
    p.face = 1;
    p.z = 4;
  } else if (place <= 1) {
    // The first ghost turns upright and becomes the front card, with a small lift on the way.
    const part = 1 - place;
    const s = settle(part, 0.1);
    p.x = GHOST_A.x * (1 - s);
    p.y = GHOST_A.y * (1 - s);
    p.rotation = GHOST_A.rotation * (1 - s);
    p.scale = mix(GHOST_A.scale, 1, s) + 0.035 * Math.sin(Math.PI * segment(part, 0.1, 0.75));
    p.face = easeOut(segment(part, 0.08, 0.5));
    p.z = 3;
  } else if (place <= 2) {
    // The second ghost swings over to the first ghost's angle a moment later.
    const s = settle(2 - place, 0.2);
    p.x = mix(GHOST_B.x, GHOST_A.x, s);
    p.y = mix(GHOST_B.y, GHOST_A.y, s);
    p.rotation = mix(GHOST_B.rotation, GHOST_A.rotation, s);
    p.scale = mix(GHOST_B.scale, GHOST_A.scale, s);
    p.ghost = mix(GHOST_B.ghost, GHOST_A.ghost, s);
    p.z = 2;
  } else if (place > last - 1 && last - place < 0.3) {
    // The dropped card sinks out of sight before it comes back as a ghost.
    const gone = segment(last - place, 0, 0.3);
    p.x = DROP_X;
    p.y = DROP_Y + 26 * gone;
    p.rotation = DROP_ROTATION;
    p.scale = DROP_SCALE;
    p.alpha = DROP_ALPHA * (1 - gone);
    p.blur = DROP_BLUR;
    p.face = 1;
    p.z = 4;
  } else {
    // A new ghost swings in at the back of the stack. Cards further back stay hidden.
    const part = 3 - place;
    const s = settle(part, 0.36);
    p.x = GHOST_B.x - 14 * (1 - s);
    p.y = GHOST_B.y + 10 * (1 - s);
    p.rotation = GHOST_B.rotation - 16 * (1 - s);
    p.scale = GHOST_B.scale - 0.03 * (1 - s);
    p.alpha = easeOut(segment(part, 0.36, 0.8));
    p.ghost = GHOST_B.ghost;
  }
  p.alpha *= fade;
  return p;
}

/** A small line icon, drawn from the same strokes in every stack. */
function Glyph({ icon, color }: { icon: DestinationIcon; color: string }) {
  return (
    <Svg width={ICON_SIZE} height={ICON_SIZE} viewBox="0 0 20 20" fill="none">
      {STROKES[icon].map((points) => (
        <Polyline key={points} points={points} stroke={color} strokeWidth={1.39} strokeLinecap="round" strokeLinejoin="round" />
      ))}
      {CIRCLES[icon].map(([cx, cy, r]) => (
        <Circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={r} stroke={color} strokeWidth={1.39} />
      ))}
    </Svg>
  );
}

type Motion = {
  pos: SharedValue<number>;
  dragX: SharedValue<number>;
  dragY: SharedValue<number>;
  count: number;
  reduced: boolean;
};

/** The soft light behind the stack in one card's colour, as strong as that card is close to the front. */
function Glow({ color, index, pos, count }: { color: string; index: number } & Pick<Motion, 'pos' | 'count'>) {
  const style = useAnimatedStyle(() => {
    const travel = pos.get() - Math.floor(pos.get());
    // The glow swells a little while the deck is moving.
    const swell = 1 + 0.07 * Math.sin(Math.PI * travel);
    return {
      opacity: glowWeight(relative(index, pos.get(), count)),
      transform: [{ scaleX: swell }, { scaleY: GLOW_STRETCH * swell }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.glow, style]}>
      <Svg width={GLOW_RADIUS * 2} height={GLOW_RADIUS * 2}>
        <Defs>
          <RadialGradient id="glow" cx="50%" cy="50%" r="50%">
            <Stop offset={0} stopColor={color} stopOpacity={0.62} />
            <Stop offset={0.55} stopColor={color} stopOpacity={0.4} />
            <Stop offset={1} stopColor={color} stopOpacity={0} />
          </RadialGradient>
        </Defs>
        <Circle cx={GLOW_RADIUS} cy={GLOW_RADIUS} r={GLOW_RADIUS} fill="url(#glow)" />
      </Svg>
    </Animated.View>
  );
}

/** One card: the grey body it shows as a ghost, and the dark face that fades in over it. */
function Card({
  item,
  index,
  front,
  onOpen,
  pos,
  dragX,
  dragY,
  count,
  reduced,
}: { item: Destination; index: number; front: boolean; onOpen: () => void } & Motion) {
  const current = useDerivedValue(() => pose(relative(index, pos.get(), count), count, dragX.get(), dragY.get(), reduced));

  const cardStyle = useAnimatedStyle(() => {
    const p = current.get();
    return {
      opacity: p.alpha,
      zIndex: p.z,
      transform: [{ translateX: p.x }, { translateY: p.y }, { rotate: `${p.rotation}deg` }, { scale: p.scale }],
      filter: [{ blur: p.blur }],
    };
  });
  const ghostStyle = useAnimatedStyle(() => ({ opacity: current.get().ghost }));
  const faceStyle = useAnimatedStyle(() => ({ opacity: current.get().face }));

  return (
    <Animated.View
      style={[styles.card, cardStyle]}
      pointerEvents={front ? 'box-none' : 'none'}
      accessibilityElementsHidden={!front}
      importantForAccessibility={front ? 'auto' : 'no-hide-descendants'}
    >
      <Animated.View style={[styles.ghost, ghostStyle]}>
        <View style={styles.icon}>
          <Glyph icon={item.icon} color="rgba(255,255,255,0.9)" />
        </View>
      </Animated.View>

      <Animated.View style={[styles.face, faceStyle]}>
        <LinearGradient colors={['#2c2c34', '#17171b']} style={styles.faceFill} />
        <View style={styles.icon}>
          <Glyph icon={item.icon} color="#8d8d99" />
        </View>
        <Pressable style={styles.open} hitSlop={8} onPress={onOpen} accessibilityRole="button" accessibilityLabel={`Open ${item.title}`}>
          <Svg width={24} height={24} fill="none">
            <Polyline points="8.8,15.2 15.2,8.8" stroke="rgba(255,255,255,0.88)" strokeWidth={1.3} strokeLinecap="round" />
            <Polyline
              points="10.2,8.8 15.2,8.8 15.2,13.8"
              stroke="rgba(255,255,255,0.88)"
              strokeWidth={1.3}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </Svg>
        </Pressable>

        <View style={styles.titles} accessible accessibilityLabel={`${item.title}, ${item.subtitle}`}>
          <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>
            {item.title}
          </Text>
          <Text style={[styles.title, styles.subtitle]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>
            {item.subtitle}
          </Text>
        </View>

        {/* The photo over a flat tint of its main colour, faded in when it arrives. */}
        <View style={[styles.photo, { backgroundColor: item.tint }]}>
          <Image source={item.photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} accessible={false} />
          <View style={styles.tag}>
            <View style={[styles.tagDot, { backgroundColor: item.glow }]} />
            <Text style={styles.tagLabel} numberOfLines={1}>
              {item.tag}
            </Text>
          </View>
        </View>

        <Text style={styles.caption} numberOfLines={2}>
          {item.caption}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A deck of destination cards. A sideways or downward swipe sends the front card away: it tilts,
 * drops below the stack and stays there blurred and dim, while the ghost card behind it turns
 * upright and becomes solid, the other ghosts shuffle their angles and the glow behind the stack
 * takes the new card's colour. The deck is a ring: after the last card the first one comes back.
 */
export function DestinationSwipeStack({ items = DESTINATIONS, autoPlay = false, canvas = '#f4f4f7', onChange, onOpen }: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = items.length;
  const swipe = reducedMotion ? SWIPE_REDUCED : SWIPE;

  // `pos` counts the cards swiped away so far; its fraction is how far the running swipe has come.
  const pos = useSharedValue(0);
  const target = useSharedValue(0);
  const looping = useSharedValue(auto);
  // True while the loop is the one moving the deck.
  const live = useSharedValue(false);
  // Where the loop started counting from, and its clock.
  const base = useSharedValue(0);
  const clock = useSharedValue(0);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  // Swipes asked for by a finger that the next frame has to start.
  const requests = useSharedValue(0);
  const touching = useSharedValue(false);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const velocityX = useSharedValue(0);
  const velocityY = useSharedValue(0);

  const [front, setFront] = useState(0);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
    base.set(Math.round(pos.get()));
  }, [auto, base, clock, hold, looping, pos]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    let p = pos.get();
    let goal = target.get();
    if (looping.get() && hold.get() <= 0) {
      const loop = STEP * count;
      let t = clock.get() + dt;
      if (t >= loop) t -= loop;
      clock.set(t);
      p = base.get() + script(t, count);
      goal = p;
      live.set(true);
    } else {
      if (live.get()) {
        // A touch took over: the swipe the loop was in the middle of finishes first.
        goal = Math.ceil(p - 0.000001);
        live.set(false);
      }
      if (requests.get() > 0) {
        goal = Math.min(goal + requests.get(), Math.floor(p) + 2);
        requests.set(0);
      }
      if (p < goal) {
        p = Math.min(goal, p + dt / swipe);
      } else if (hold.get() > 0 && !touching.get()) {
        hold.set(hold.get() - dt);
        if (hold.get() <= 0) {
          p = wrapIndex(Math.round(p), count);
          goal = p;
          base.set(p);
          clock.set(0);
        }
      }
    }
    pos.set(p);
    target.set(goal);

    if (touching.get()) {
      velocityX.set(0);
      velocityY.set(0);
    } else {
      const vx = velocityX.get() + (-RETURN_STIFFNESS * dragX.get() - RETURN_DAMPING * velocityX.get()) * dt;
      const vy = velocityY.get() + (-RETURN_STIFFNESS * dragY.get() - RETURN_DAMPING * velocityY.get()) * dt;
      velocityX.set(vx);
      velocityY.set(vy);
      dragX.set(dragX.get() + vx * dt);
      dragY.set(dragY.get() + vy * dt);
    }
  });

  const changed = (index: number) => {
    setFront(index);
    onChange?.(index);
  };
  useAnimatedReaction(
    () => wrapIndex(Math.round(pos.get()), count),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(changed, now);
    },
  );

  // A touch takes over from the loop, which starts again from rest a little later.
  const next = () => {
    hold.set(USER_HOLD);
    requests.set(requests.get() + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const finger = useRef({ x: 0, y: 0, active: false });

  const onGrant = (e: GestureResponderEvent) => {
    hold.set(USER_HOLD);
    // A swipe that is still running finishes first.
    const resting = Math.abs(pos.get() - Math.round(pos.get())) < 0.001;
    finger.current = { x: e.nativeEvent.pageX, y: e.nativeEvent.pageY, active: resting };
  };

  const onMove = (e: GestureResponderEvent) => {
    const f = finger.current;
    if (!f.active) return;
    const dx = e.nativeEvent.pageX - f.x;
    const dy = e.nativeEvent.pageY - f.y;
    if (!touching.get() && Math.hypot(dx, dy) < DRAG_SLOP) return;
    touching.set(true);
    hold.set(USER_HOLD);
    dragX.set(dx * DRAG_GAIN);
    dragY.set(dy * (dy > 0 ? DRAG_GAIN : DRAG_GAIN_UP));
  };

  const onEnd = (e: GestureResponderEvent) => {
    const f = finger.current;
    if (!f.active || !touching.get()) return;
    touching.set(false);
    const dx = e.nativeEvent.pageX - f.x;
    const dy = e.nativeEvent.pageY - f.y;
    if (Math.abs(dx) > COMMIT || dy > COMMIT) next();
  };

  const motion: Motion = { pos, dragX, dragY, count, reduced: reducedMotion };

  return (
    <View style={styles.root} accessibilityActions={[{ name: 'activate', label: 'Next destination' }]} onAccessibilityAction={next}>
      {items.map((item, index) => (
        <Glow key={index} color={item.glow} index={index} pos={pos} count={count} />
      ))}

      {/* Cut at the bottom edge only: the ghosts and a dragged card may reach past the sides. */}
      <View
        style={styles.deck}
        onStartShouldSetResponder={() => true}
        onResponderTerminationRequest={() => false}
        onResponderGrant={onGrant}
        onResponderMove={onMove}
        onResponderRelease={onEnd}
        onResponderTerminate={onEnd}
      >
        {items.map((item, index) => (
          <Card key={index} item={item} index={index} front={index === front} onOpen={() => onOpen?.(index)} {...motion} />
        ))}
      </View>

      <LinearGradient pointerEvents="none" colors={[`${canvas}00`, canvas]} style={styles.fade} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  glow: {
    position: 'absolute',
    left: WIDTH / 2 - GLOW_RADIUS,
    top: CENTER_Y - GLOW_RADIUS,
    width: GLOW_RADIUS * 2,
    height: GLOW_RADIUS * 2,
  },
  deck: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED,
    overflow: 'hidden',
  },
  fade: {
    position: 'absolute',
    left: 0,
    bottom: 0,
    width: WIDTH,
    height: FADE,
  },
  card: {
    position: 'absolute',
    left: BLEED + (WIDTH - CARD_WIDTH) / 2,
    top: BLEED + CENTER_Y - CARD_HEIGHT / 2,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  ghost: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#3a3a46',
  },
  face: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#17171b',
    boxShadow: '0 16px 44px rgba(24,24,40,0.3)',
  },
  faceFill: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
  },
  icon: {
    position: 'absolute',
    left: 14,
    top: 14,
  },
  open: {
    position: 'absolute',
    right: 12,
    top: 12,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.16)',
  },
  titles: {
    position: 'absolute',
    left: 12,
    top: 47,
    width: CARD_WIDTH - 24,
  },
  title: {
    color: '#ffffff',
    fontSize: 25,
    lineHeight: 26,
    fontWeight: '300',
    letterSpacing: 0.3,
    textAlign: 'center',
    textTransform: 'uppercase',
  },
  subtitle: {
    color: '#b4b4c0',
  },
  photo: {
    position: 'absolute',
    left: 6,
    top: 112,
    width: CARD_WIDTH - 12,
    height: 172,
    borderRadius: 14,
    borderCurve: 'continuous',
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  tag: {
    height: 20,
    marginBottom: 7,
    paddingHorizontal: 9,
    borderRadius: 10,
    backgroundColor: 'rgba(22,22,26,0.82)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  tagDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
  },
  tagLabel: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '500',
  },
  caption: {
    position: 'absolute',
    left: (CARD_WIDTH - 196) / 2,
    top: 295,
    width: 196,
    color: '#8c8c98',
    fontSize: 10,
    lineHeight: 13.5,
    textAlign: 'center',
  },
});
