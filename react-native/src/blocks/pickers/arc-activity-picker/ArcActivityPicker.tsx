import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, LinearGradient, Path, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** The drawing on an activity's chip, in a 24 × 24 box: its main lines, and a second set drawn fainter. */
type Icon = {
  /** Path commands. */
  lines?: string;
  /** Circles, each a centre and a radius. */
  rings?: [number, number, number][];
  soft?: string;
  softRings?: [number, number, number][];
};

type Item = {
  name: string;
  /** The icon's colour, and the selected chip's outline and tint. */
  color: string;
  icon: Icon;
};

type Props = {
  items?: Item[];
  /** Index of the row that rests at the knob. */
  initial?: number;
  /** Turns the wheel through a short script by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the row the wheel is settling on. */
  onSelect?: (index: number) => void;
};

const HEIGHT = 700;

// The rows ride an ellipse (a wheel seen at an angle), one every 15°.
const STEP = Math.PI / 12;
const RADIUS_X = 205;
const RADIUS_Y = 362;
// The selected chip's centre, right of the panel's centre line.
const CHIP_X = 44;
// Rows further than this from the knob are not drawn; their opacity reaches 0 there.
const REACH = 4.5;
// Degrees a row leans per row of distance, and how much it shrinks.
const TILT = 4.8;
const SHRINK = 0.035;

const CHIP_W = 68;
const CHIP_SEATED = 76;
const CHIP_H = 52;
const CHIP_RADIUS = 21;
const LABEL_GAP = 28;
const LABEL_W = 190;
const PLATE_PAD = 10;
// The icons are drawn in a 24 pt box and shown this much larger.
const GLYPH = 1.15;
const PEN = 1.7;

// The rim is a circle whose leftmost point is `RIM_X` right of the centre line, and the knob sits on it.
const RIM_X = 116;
const RIM_R = 480;
const KNOB_X = 125;
const KNOB_W = 46;
const KNOB_H = 17;
// Degrees the knob rocks as a row passes.
const ROCK = 7;
// Half the height of the stretch of rim that darkens.
const GLINT = 64;

// Dragging: points of travel per row, the travel that makes a touch a drag, and how far a flick carries.
const PITCH = 94;
const SLOP = 8;
const GLIDE = 0.16;
const MAX_SPEED = 24;

// autoPlay (seconds): when the wheel sets off, and to which row, counted from the resting one.
const SCRIPT = [
  { at: 1.0, rows: 2 },
  { at: 2.6, rows: -2 },
  { at: 4.4, rows: -3 },
  { at: 5.65, rows: 0 },
];
const LOOP = 8;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const WHEEL_SPRING = { mass: 1, stiffness: 180, damping: 17.5, reduceMotion: ReduceMotion.Never };
const SEAT_SPRING = { mass: 1, stiffness: 300, damping: 20, reduceMotion: ReduceMotion.Never };

const ITEMS: Item[] = [
  {
    name: 'Running',
    color: '#3FC59D',
    icon: {
      lines: 'M 7.5 10.8 L 11 8.6 L 14.2 8.8 L 16.4 11.6 L 19.4 11.6 M 14.2 8.8 L 11.4 14 L 14.6 16.6 L 13.8 21 M 11.4 14 L 8.6 17.6 L 4.8 17.6',
      rings: [[15.8, 4.8, 2]],
      soft: 'M 2.6 7.6 L 6 7.6 M 2 12.6 L 4.6 12.6',
    },
  },
  {
    name: 'Sleep',
    color: '#5B9BF0',
    icon: {
      lines: 'M 6.6 13.2 Q 8 14.6 9.4 13.2 M 11.6 13.2 Q 13 14.6 14.4 13.2 M 9.4 17 Q 10.5 17.8 11.6 17',
      rings: [[10.5, 13.8, 7.4]],
      soft: 'M 16.6 2.6 L 21 2.6 L 16.6 7.6 L 21 7.6',
    },
  },
  {
    name: 'Skipping',
    color: '#F08A4B',
    icon: {
      lines: 'M 12 8.4 L 12 14.4 M 12 14.4 L 10 19.4 M 12 14.4 L 14 19.4 M 7.6 12.4 L 12 9.8 L 16.4 12.4',
      rings: [[12, 4.8, 2]],
      soft: 'M 7.6 12.4 C 1.6 15.4 5 22.4 12 22.4 C 19 22.4 22.4 15.4 16.4 12.4',
    },
  },
  {
    name: 'Power Training',
    color: '#EE5A5A',
    icon: {
      lines: 'M 8.6 12 L 15.4 12 M 5.6 7 L 8.6 7 L 8.6 17 L 5.6 17 Z M 15.4 7 L 18.4 7 L 18.4 17 L 15.4 17 Z',
      soft: 'M 5.6 9.6 L 2.8 9.6 L 2.8 14.4 L 5.6 14.4 M 18.4 9.6 L 21.2 9.6 L 21.2 14.4 L 18.4 14.4',
    },
  },
  {
    name: 'Cycling',
    color: '#D94FD0',
    icon: {
      lines: 'M 6 15.6 L 9.8 9 L 15.2 9 L 18 15.6 M 9.8 9 L 12 15.6 L 15.2 9 M 8 6.4 L 11 6.4 M 9.4 6.4 L 9.8 9 M 15.2 9 L 14.4 5.8 L 17 5.8',
      softRings: [
        [6, 15.6, 3.9],
        [18, 15.6, 3.9],
      ],
    },
  },
  {
    name: 'Swimming',
    color: '#58AEEF',
    icon: {
      lines: 'M 4.4 12.2 L 9.4 7.8 L 13.6 11.4 M 2.5 16 Q 5 13.8 7.5 16 Q 10 18.2 12.5 16 Q 15 13.8 17.5 16 Q 19.6 17.8 21.5 16.4',
      rings: [[16.8, 7.6, 2]],
      soft: 'M 2.5 20.2 Q 5 18 7.5 20.2 Q 10 22.4 12.5 20.2 Q 15 18 17.5 20.2 Q 19.6 22 21.5 20.6',
    },
  },
  {
    name: 'Tennis',
    color: '#F5B63C',
    icon: {
      rings: [[12, 12, 8.5]],
      soft: 'M 5.4 6.6 C 9.2 9 9.2 15 5.4 17.4 M 18.6 6.6 C 14.8 9 14.8 15 18.6 17.4',
    },
  },
  {
    name: 'Basketball',
    color: '#9C95F2',
    icon: {
      rings: [[12, 12, 8.5]],
      soft: 'M 12 3.5 L 12 20.5 M 3.5 12 L 20.5 12 M 6 6 C 9.4 9.4 9.4 14.6 6 18 M 18 6 C 14.6 9.4 14.6 14.6 18 18',
    },
  },
  {
    name: 'Walking',
    color: '#58C487',
    icon: {
      lines:
        'M 3 16.4 L 3 10.4 C 4.6 11.6 6.6 11.4 8 9.8 L 12.6 14 C 15.2 15 18.6 14.8 20.6 16.6 C 21.4 17.4 21 18.6 20 18.6 L 4.6 18.6 C 3.6 18.6 3 17.6 3 16.4 Z',
      soft: 'M 9.6 11.4 L 11.2 10 M 11.4 13 L 13 11.6 M 3 15.4 L 14 15.4',
    },
  },
  {
    name: 'Hiking',
    color: '#33B8C4',
    icon: {
      lines: 'M 2.6 19.6 L 9 7.6 L 13 14.6 L 15.6 11 L 21.4 19.6 Z',
      soft: 'M 7.1 11.2 L 9 12.8 L 10.7 10.6',
      softRings: [[18, 5.6, 2]],
    },
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

function wrapped(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

/** How many rows `index` is below the knob (above it when negative), taking the short way round. */
function distance(index: number, wheel: number, count: number) {
  'worklet';
  const away = (index - wheel) % count;
  return away > count / 2 ? away - count : away < -count / 2 ? away + count : away;
}

/**
 * A row's opacity `far` rows from the knob: 1, 0.8, 0.57, 0.33, 0.1 and gone half a row later.
 * A short list also fades where it wraps, half a turn away.
 */
function fade(far: number, count: number) {
  'worklet';
  const seam = clamp01((count / 2 - far) / 0.5);
  if (far < 1) return Math.min(seam, 1 - 0.2 * far);
  if (far < 4) return Math.min(seam, 0.8 - (0.7 / 3) * (far - 1));
  return Math.min(seam, Math.max(0, 0.1 * (1 - (far - 4) / 0.5)));
}

/** `#rrggbb` at the given opacity. */
function tint(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

function Glyph({ icon, color }: { icon: Icon; color: string }) {
  const pen = { fill: 'none', strokeWidth: PEN, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24">
      {icon.soft ? <Path d={icon.soft} stroke={color} strokeOpacity={0.45} {...pen} /> : null}
      {icon.softRings?.map(([cx, cy, r], i) => (
        <Circle key={i} cx={cx} cy={cy} r={r} stroke={color} strokeOpacity={0.45} {...pen} />
      ))}
      {icon.lines ? <Path d={icon.lines} stroke={color} {...pen} /> : null}
      {icon.rings?.map(([cx, cy, r], i) => (
        <Circle key={i} cx={cx} cy={cy} r={r} stroke={color} {...pen} />
      ))}
    </Svg>
  );
}

type RowProps = {
  item: Item;
  index: number;
  count: number;
  /** This row starts at the knob. */
  first: boolean;
  wheel: SharedValue<number>;
  still: boolean;
};

/** One activity, laid out around its chip's centre, and the bead where its spoke meets the rim. */
function Row({ item, index, count, first, wheel, still }: RowProps) {
  // How far the row has seated as the selection, 0…1.
  const seat = useSharedValue(first ? 1 : 0);

  useAnimatedReaction(
    () => wrapped(Math.round(wheel.get()), count) === index,
    (on, was) => {
      if (on === was) return;
      // Under reduced motion nothing eases: the seat jumps.
      seat.set(still ? (on ? 1 : 0) : withSpring(on ? 1 : 0, SEAT_SPRING));
    },
  );

  // Rows above the knob lean clockwise and rows below it the other way, each along its spoke.
  const place = useAnimatedStyle(() => {
    const away = distance(index, wheel.get(), count);
    const far = Math.min(Math.abs(away), REACH);
    return {
      opacity: fade(far, count),
      transform: [
        { translateX: CHIP_X + RADIUS_X * (1 - Math.cos(away * STEP)) },
        { translateY: RADIUS_Y * Math.sin(away * STEP) },
        { rotate: `${-TILT * away}deg` },
        { scale: 1 - SHRINK * far },
      ],
    };
  });
  const bead = useAnimatedStyle(() => {
    const away = distance(index, wheel.get(), count);
    const far = Math.min(Math.abs(away), REACH);
    const y = RADIUS_Y * Math.sin(away * STEP);
    return {
      opacity: fade(far, count),
      transform: [{ translateX: RIM_X + RIM_R - Math.sqrt(RIM_R * RIM_R - y * y) }, { translateY: y }],
    };
  });
  const chip = useAnimatedStyle(() => {
    const width = mix(CHIP_W, CHIP_SEATED, seat.get());
    return { left: (CHIP_W - width) / 2, width, borderRadius: mix(CHIP_RADIUS, CHIP_H / 2, seat.get()) };
  });
  const round = useAnimatedStyle(() => ({ borderRadius: mix(CHIP_RADIUS, CHIP_H / 2, seat.get()) }));
  const seen = useAnimatedStyle(() => ({ opacity: clamp01(seat.get()) }));
  const name = useAnimatedStyle(() => ({ color: interpolateColor(clamp01(seat.get()), [0, 1], ['#9B9B9B', '#2B2B2B']) }));
  const glyph = useAnimatedStyle(() => ({ transform: [{ scale: GLYPH * (1 + 0.1 * seat.get()) }] }));

  return (
    <>
      <Animated.View pointerEvents="none" style={[styles.bead, bead]} />
      <Animated.View pointerEvents="none" style={[styles.row, place]}>
        <View style={styles.label}>
          <View style={styles.plateBox}>
            <Animated.View style={[StyleSheet.absoluteFill, styles.plate, seen]} />
            <Animated.Text style={[styles.name, name]} numberOfLines={1}>
              {item.name}
            </Animated.Text>
          </View>
        </View>

        <Animated.View style={[styles.chip, { boxShadow: `0 5 18 0 ${tint(item.color, 0.24)}` }, chip, seen]} />
        <Animated.View style={[styles.chip, styles.chipFace, chip]}>
          <Animated.View style={[StyleSheet.absoluteFill, styles.chipLit, seen]}>
            <Animated.View
              style={[StyleSheet.absoluteFill, { backgroundColor: tint(item.color, 0.17), borderWidth: 1.5, borderColor: tint(item.color, 0.85) }, round]}
            />
          </Animated.View>
          <Animated.View style={glyph}>
            <Glyph icon={item.icon} color={item.color} />
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </>
  );
}

/**
 * A wheel of activities seen from its left flank. The rows ride an arc whose centre is far off the
 * right edge: each is a label and an icon chip, tilted along its spoke and fading towards the ends.
 * Dragging turns the wheel, and the row that reaches the knob on the rim becomes the selection: its
 * chip widens into a tinted pill and its label gets a chip of its own. Ten rows or more wrap around.
 */
export function ArcActivityPicker({ items = ITEMS, initial = 4, autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const count = Math.max(items.length, 1);
  const start = wrapped(initial, count);
  const auto = autoPlay && !still && items.length > 1;

  // The wheel: the index at the knob, continuous while it turns and unbounded as it wraps.
  const wheel = useSharedValue(start);
  // The knob's reaction to a row arriving, 1 falling to 0.
  const knock = useSharedValue(0);
  // Half the panel's width: the centre line everything is measured from.
  const half = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const dragZero = useSharedValue(0);
  // A finger is on the wheel and has not yet sent it anywhere.
  const held = useSharedValue(false);
  // The wheel is turning under a finger, not under the script: only then it ticks.
  const byHand = useSharedValue(false);
  // Counts touches: each one stops the script and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((n) => n + 1);
  const chosen = (target: number) => onSelect?.(wrapped(target, count));

  // When another row comes nearest the knob, the knob reacts.
  useAnimatedReaction(
    () => Math.round(wheel.get()),
    (now, before) => {
      if (before === null || now === before) return;
      if (!still) {
        const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
        knock.set(withSequence(withTiming(1, { duration: 50, ...linear }), withTiming(0, { duration: 150, ...linear })));
      }
      if (byHand.get()) scheduleOnRN(tick);
    },
  );

  /** Brings row `index` to the knob the short way round. */
  const select = (index: number) => {
    const now = wheel.get();
    const target = Math.round(now + distance(index, now, count));
    wheel.set(still ? target : withSpring(target, WHEEL_SPRING));
    chosen(target);
  };

  // The timers below call whatever this is at the time they fire.
  const actions = useRef({ select });
  useEffect(() => {
    actions.current = { select };
  });

  // The script a finger would follow: it turns the wheel to four rows in turn and ends where it began.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        actions.current.select(start + step.rows);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, bring the first row back, and start over from rest.
      timer = setTimeout(() => {
        if (held.get()) return;
        byHand.set(false);
        actions.current.select(start);
        run(Date.now(), 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, start, held, byHand]);

  // A touch catches the wheel, a drag turns it and lets go with momentum.
  const pan = Gesture.Pan()
    .minDistance(SLOP)
    .onBegin(() => {
      cancelAnimation(wheel);
      held.set(true);
      byHand.set(true);
      dragFrom.set(wheel.get());
      scheduleOnRN(touch);
    })
    .onStart((event) => {
      dragZero.set(event.translationY);
    })
    .onUpdate((event) => {
      wheel.set(dragFrom.get() - (event.translationY - dragZero.get()) / PITCH);
    })
    .onEnd((event) => {
      const speed = Math.min(Math.max(-event.velocityY / PITCH, -MAX_SPEED), MAX_SPEED);
      const target = Math.round(wheel.get() + speed * GLIDE);
      held.set(false);
      wheel.set(still ? target : withSpring(target, { ...WHEEL_SPRING, velocity: speed }));
      scheduleOnRN(chosen, target);
    })
    .onFinalize(() => {
      // A touch that neither dragged nor tapped a row lets the wheel fall onto the nearest one.
      if (held.get()) {
        held.set(false);
        const target = Math.round(wheel.get());
        wheel.set(still ? target : withSpring(target, WHEEL_SPRING));
      }
      scheduleOnRN(touch);
    });

  // A touch that never moved selects the row under it, if it landed on the row's label or chip.
  const tap = Gesture.Tap()
    .maxDistance(SLOP)
    .onEnd((event) => {
      const lift = (event.y - HEIGHT / 2) / RADIUS_Y;
      const away = Math.abs(lift) < 1 ? Math.round(Math.asin(lift) / STEP) : REACH;
      const reach = event.x - (half.get() + CHIP_X + RADIUS_X * (1 - Math.cos(away * STEP)));
      const hit = Math.abs(away) < REACH - 0.5 && reach > -LABEL_W - CHIP_W && reach < CHIP_W;
      const target = Math.round(wheel.get() + (hit ? away : 0));
      held.set(false);
      wheel.set(still ? target : withSpring(target, WHEEL_SPRING));
      scheduleOnRN(chosen, target);
    });

  const knob = useAnimatedStyle(() => {
    const turn = wheel.get() - Math.round(wheel.get());
    return {
      transform: [
        { rotate: `${ROCK * Math.sin(2 * Math.PI * turn)}deg` },
        { scaleX: 1 - 0.08 * knock.get() },
        { scaleY: 1 + 0.06 * knock.get() },
      ],
    };
  });
  const glint = useAnimatedStyle(() => ({ opacity: 0.7 * knock.get() }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.panel}
        onLayout={(event) => half.set(event.nativeEvent.layout.width / 2)}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Activity"
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          touch();
          select(Math.round(wheel.get()) + (event.nativeEvent.actionName === 'increment' ? 1 : -1));
        }}
      >
        {/* Everything is placed from the middle of the panel. */}
        <View style={styles.center}>
          <View style={styles.rim} />
          <Animated.View pointerEvents="none" style={[styles.glint, glint]}>
            <Svg width={12} height={GLINT * 2}>
              <Defs>
                <LinearGradient id="glint" gradientUnits="userSpaceOnUse" x1={0} y1={0} x2={0} y2={GLINT * 2}>
                  <Stop offset={0} stopColor="#4A4D52" stopOpacity={0} />
                  <Stop offset={0.5} stopColor="#4A4D52" stopOpacity={1} />
                  <Stop offset={1} stopColor="#4A4D52" stopOpacity={0} />
                </LinearGradient>
              </Defs>
              <Circle cx={2 + RIM_R} cy={GLINT} r={RIM_R} fill="none" stroke="url(#glint)" strokeWidth={1.6} />
            </Svg>
          </Animated.View>

          {items.map((item, index) => (
            <Row key={index} item={item} index={index} count={count} first={index === start} wheel={wheel} still={still} />
          ))}

          {/* The knob rocks like a pawl as rows pass it, and dips when one arrives. */}
          <Animated.View pointerEvents="none" style={[styles.knob, knob]} />
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  panel: {
    alignSelf: 'stretch',
    height: HEIGHT,
    overflow: 'hidden',
  },
  center: {
    position: 'absolute',
    left: '50%',
    top: HEIGHT / 2,
    width: 0,
    height: 0,
  },
  rim: {
    position: 'absolute',
    left: RIM_X,
    top: -RIM_R,
    width: RIM_R * 2,
    height: RIM_R * 2,
    borderRadius: RIM_R,
    borderWidth: 1.2,
    borderColor: '#D9D9D9',
  },
  glint: {
    position: 'absolute',
    left: RIM_X - 2,
    top: -GLINT,
  },
  bead: {
    position: 'absolute',
    left: -2,
    top: -2,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#BDBDBD',
  },
  row: {
    position: 'absolute',
    left: -CHIP_W / 2,
    top: -CHIP_H / 2,
    width: CHIP_W,
    height: CHIP_H,
  },
  // The label ends 28 pt to the chip's left; its plate reaches 10 pt further.
  label: {
    position: 'absolute',
    right: CHIP_W + LABEL_GAP - PLATE_PAD,
    top: 0,
    width: LABEL_W,
    height: CHIP_H,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  plateBox: {
    paddingHorizontal: PLATE_PAD,
    paddingVertical: 5,
  },
  plate: {
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#E0E0E0',
    backgroundColor: '#F8F8F8',
  },
  name: {
    fontSize: 18,
    fontWeight: '500',
  },
  chip: {
    position: 'absolute',
    top: 0,
    height: CHIP_H,
  },
  chipFace: {
    backgroundColor: '#EBEBEB',
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipLit: {
    backgroundColor: '#FDFDFD',
  },
  knob: {
    position: 'absolute',
    left: KNOB_X - KNOB_W / 2,
    top: -KNOB_H / 2,
    width: KNOB_W,
    height: KNOB_H,
    borderRadius: KNOB_H / 2,
    backgroundColor: '#4A4D52',
    boxShadow: '0 3 10 0 rgba(0,0,0,0.2)',
  },
});
