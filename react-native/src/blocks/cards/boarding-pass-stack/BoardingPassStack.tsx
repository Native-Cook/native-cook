import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, Line, LinearGradient as SvgLinearGradient, Path, Polygon, Polyline, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type BoardingPass = {
  /** The pass's colour at its top and at its bottom. */
  color: string;
  deep: string;
  fromCity: string;
  fromCode: string;
  toCity: string;
  toCode: string;
  /** Arrival and local time at the destination, shown in the header. */
  arrival: string;
  localTime: string;
  flight: string;
  date: string;
  boarding: string;
  cabin: string;
  gate: string;
  seat: string;
  status: string;
  sequence: string;
  bags: string;
};

type Props = {
  /** The passes, front first. At least three. */
  passes?: BoardingPass[];
  /** Brings the next pass to the front every 2.4 seconds, as if a finger pulled it. */
  autoPlay?: boolean;
  airline?: string;
  passenger?: string;
  destinationLabel?: string;
  arrivalLabel?: string;
  localTimeLabel?: string;
  rideLabel?: string;
  /** Called with the index of the pass that is now in front. */
  onChange?: (index: number) => void;
  onRide?: () => void;
};

const WIDTH = 340;
const HEIGHT = 600;
const RADIUS = 32;
const GLOW_RADIUS = 190;

// Header: the city's box, the row of three items, and the boxes its values roll through.
const CITY_BOX = 54;
const ROW_Y = 112;
const ROW_HEIGHT = 44;
const COLUMN = 100;
const TIME_BOX = 28;

const PASS_X = 20;
const PASS_WIDTH = 300;
const PASS_HEIGHT = 330;
const PASS_RADIUS = 12;
// Height of the strip with the airline, the gate and the seat.
const STRIP = 52;
// Where the stub tears off, and the radius of the notch at each end of that line.
const TEAR = 230;
const NOTCH = 7;
// Top of the front pass in the panel, and of the area that takes the stack's touches.
const FRONT_Y = 246;
const STACK_Y = 170;

// Where a pass rests behind the front one: its top edge above the front's, scale and darkening.
const SLOT_A = { top: -34, scale: 0.94, shade: 0.16, alpha: 1 };
const SLOT_B = { top: -68, scale: 0.88, shade: 0.3, alpha: 1 };
const SLOT_FRONT = { top: 0, scale: 1, shade: 0, alpha: 1 };
// Passes deeper than the second stay hidden under it.
const SLOT_HIDDEN = { ...SLOT_B, alpha: 0 };
// How far the front pass ducks down, and how far the next one is pulled up, before they swap.
const SINK = 190;
const LIFT = 130;
// Degrees the pulled pass tips its top edge towards the viewer, and the eye's distance in points.
const TILT = 14;
const PERSPECTIVE = 900;
// Points the plane flies in from the left.
const FLIGHT = 56;

// Seconds one shuffle takes, and the share of it after which the two passes have swapped depth.
const SHUFFLE = 1.3;
const SHUFFLE_REDUCED = 0.35;
const SWAP = 0.45;
// The spring the pulled pass lands on.
const STIFFNESS = 170;
const DAMPING = 16;
const MASS = 1;

// autoPlay (seconds): the three default passes go round once per LOOP, the first pull at FIRST
// and the next ones every STEP.
const LOOP = 7.2;
const FIRST = 0.8;
const STEP = LOOP / 3;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 2.5;
// Finger travel upwards that pulls the next pass, and the most a tap may move.
const SWIPE = 36;
const TAP_SLOP = 8;

const INK = '#1b2233';
const ink = (alpha: number) => `rgba(27,34,51,${alpha})`;

const PASSES: BoardingPass[] = [
  {
    color: '#f0bd2e',
    deep: '#dba616',
    fromCity: 'Berlin',
    fromCode: 'BER',
    toCity: 'Lisbon',
    toCode: 'LIS',
    arrival: '19:30',
    localTime: '18:30',
    flight: 'TN582',
    date: '03 OCT',
    boarding: '17:35',
    cabin: 'First',
    gate: 'Z52',
    seat: '2A',
    status: 'Gold',
    sequence: '042',
    bags: '2 × 32 kg',
  },
  {
    color: '#f58468',
    deep: '#e5674a',
    fromCity: 'Oslo',
    fromCode: 'OSL',
    toCity: 'Tokyo',
    toCode: 'HND',
    arrival: '08:05',
    localTime: '16:05',
    flight: 'TN204',
    date: '11 NOV',
    boarding: '12:10',
    cabin: 'Business',
    gate: 'C14',
    seat: '7K',
    status: 'Gold',
    sequence: '118',
    bags: '2 × 23 kg',
  },
  {
    color: '#86dcbb',
    deep: '#62c79f',
    fromCity: 'Madrid',
    fromCode: 'MAD',
    toCity: 'Reykjavik',
    toCode: 'KEF',
    arrival: '23:45',
    localTime: '21:45',
    flight: 'TN931',
    date: '24 JAN',
    boarding: '20:15',
    cabin: 'Economy',
    gate: 'A7',
    seat: '23F',
    status: 'Gold',
    sequence: '207',
    bags: '1 × 23 kg',
  },
];

// The outline of a pass: a rounded rectangle with a half-round notch at each end of the tear line.
const OUTLINE =
  `M${PASS_RADIUS} 0H${PASS_WIDTH - PASS_RADIUS}A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 ${PASS_WIDTH} ${PASS_RADIUS}` +
  `V${TEAR - NOTCH}A${NOTCH} ${NOTCH} 0 0 0 ${PASS_WIDTH} ${TEAR + NOTCH}V${PASS_HEIGHT - PASS_RADIUS}` +
  `A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 ${PASS_WIDTH - PASS_RADIUS} ${PASS_HEIGHT}H${PASS_RADIUS}` +
  `A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 0 ${PASS_HEIGHT - PASS_RADIUS}V${TEAR + NOTCH}A${NOTCH} ${NOTCH} 0 0 0 0 ${TEAR - NOTCH}` +
  `V${PASS_RADIUS}A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 ${PASS_RADIUS} 0Z`;
const STRIP_OUTLINE =
  `M${PASS_RADIUS} 0H${PASS_WIDTH - PASS_RADIUS}A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 ${PASS_WIDTH} ${PASS_RADIUS}` +
  `V${STRIP}H0V${PASS_RADIUS}A${PASS_RADIUS} ${PASS_RADIUS} 0 0 1 ${PASS_RADIUS} 0Z`;

// A plane seen from above, flying to the right, in a 24 × 24 box.
const PLANE =
  '22,12 20.6,12.75 15,12.9 10.2,20.5 8.2,20.5 10.6,12.9 5.6,12.85 4.2,16.2 2.8,16.2 3.4,12 2.8,7.8 4.2,7.8 5.6,11.15 10.6,11.1 8.2,3.5 10.2,3.5 15,11.1 20.6,11.25';

/** Whether square (x, y) of the code is dark. The pattern only has to look right: it encodes nothing. */
function codeSquare(x: number, y: number, seed: number) {
  // The corner marks: a 7 × 7 ring around a 3 × 3 block, with a clear band around them.
  for (const [cx, cy] of [
    [0, 0],
    [14, 0],
    [0, 14],
  ]) {
    const lx = x - cx;
    const ly = y - cy;
    if (lx >= -1 && lx <= 7 && ly >= -1 && ly <= 7) {
      if (lx < 0 || lx > 6 || ly < 0 || ly > 6) return false;
      return lx === 0 || lx === 6 || ly === 0 || ly === 6 || (lx >= 2 && lx <= 4 && ly >= 2 && ly <= 4);
    }
  }
  return ((x * 73 + y * 151 + x * y * 17 + (seed + 1) * 31) % 97) % 2 === 0;
}

/** A drawn stand-in for the pass's code: 21 × 21 squares with the three corner marks of a QR code. */
function codePath(seed: number) {
  let d = '';
  for (let y = 0; y < 21; y++) {
    for (let x = 0; x < 21; x++) if (codeSquare(x, y, seed)) d += `M${x} ${y}h1v1h-1z`;
  }
  return d;
}

type Pose = {
  /** Top edge, measured from where the front pass rests. */
  top: number;
  scale: number;
  /** Degrees around the horizontal axis; negative tips the top edge towards the viewer. */
  tilt: number;
  /** Opacity of the dark veil on a pass behind. */
  shade: number;
  alpha: number;
  /** How far the plane has flown, 0…1. */
  flight: number;
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

/** Step response of the landing spring `seconds` after it is released: 0 → 1 with one overshoot. */
function spring(seconds: number) {
  'worklet';
  if (seconds <= 0) return 0;
  const omega = Math.sqrt(STIFFNESS / MASS);
  const zeta = DAMPING / (2 * Math.sqrt(STIFFNESS * MASS));
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * seconds) * (Math.cos(damped * seconds) + ((zeta * omega) / damped) * Math.sin(damped * seconds));
}

/** Shuffles done `clock` seconds into the autoPlay loop. */
function script(clock: number, count: number) {
  'worklet';
  let done = 0;
  for (let index = 0; index < count; index++) {
    done += segment(clock, FIRST + STEP * index, FIRST + STEP * index + SHUFFLE);
  }
  return done;
}

/** Place of pass `index` in the stack: 0 in front, 1 and 2 behind it, and below 0 while it ducks away. */
function relative(index: number, pos: number, count: number) {
  'worklet';
  let place = (index - pos) % count;
  if (place <= -1) place += count;
  if (place > count - 1) place -= count;
  return place;
}

/** The same place counted from the nearest side, for things that cross-fade: −0.5 … 0.5 around the front. */
function centered(index: number, pos: number, count: number) {
  'worklet';
  let place = (index - pos) % count;
  if (place <= -count / 2) place += count;
  if (place > count / 2) place -= count;
  return place;
}

/** The header changes later than the stack: its position moves only during a part of each shuffle. */
function headerPosition(pos: number, from: number, to: number) {
  'worklet';
  const whole = Math.floor(pos);
  return whole + easeInOut(segment(pos - whole, from, to));
}

/** Opacity of a value that rolls through its box at `place`: it fades only as it leaves the box. */
function rollFade(place: number) {
  'worklet';
  return smooth(Math.min(1, Math.max(0, (0.75 - Math.abs(place)) / 0.3)));
}

/** Where a pass rests `depth` places behind the front. */
function slot(depth: number) {
  'worklet';
  if (depth === 0) return SLOT_FRONT;
  if (depth === 1) return SLOT_A;
  return depth === 2 ? SLOT_B : SLOT_HIDDEN;
}

function pose(at: number, count: number, reduced: boolean): Pose {
  'worklet';
  // Reduced motion: every pass jumps to its next place half-way while the stack fades through.
  let place = at;
  let fade = 1;
  if (reduced) {
    const nearest = Math.round(place);
    fade = 1 - 2 * Math.abs(place - nearest);
    place = nearest;
  }

  const p: Pose = { top: 0, scale: 1, tilt: 0, shade: 0, alpha: 1, flight: 1, z: 0 };
  if (place <= 0) {
    // The front pass ducks down, and once the next one is over it, comes up again at the back.
    const t = -place;
    const back = slot(count - 1);
    if (t < SWAP) {
      const down = easeInOut(segment(t, 0, 0.42));
      p.top = SINK * down;
      p.scale = 1 - 0.03 * down;
      p.shade = 0.1 * down;
      p.z = 3;
    } else {
      const up = easeOut(segment(t, SWAP, 0.95));
      p.top = mix(SINK, back.top, up);
      p.scale = mix(0.97, back.scale, up);
      p.shade = mix(0.1, back.shade, up);
      p.alpha = mix(1, back.alpha, up);
    }
  } else if (place <= 1) {
    // The next pass is pulled up out of the stack, tips towards the viewer and drops in front.
    const part = 1 - place;
    const from = slot(1);
    const rise = easeInOut(segment(part, 0, 0.42));
    if (part < SWAP) {
      p.top = from.top - LIFT * rise;
      p.scale = mix(from.scale, 1.03, rise);
      p.z = 2;
    } else {
      const landed = mix(spring((part - SWAP) * SHUFFLE), 1, smooth(segment(part, 0.88, 1)));
      p.top = mix(from.top - LIFT, 0, landed);
      p.scale = mix(1.03, 1, landed);
      p.z = 4;
    }
    p.tilt = -TILT * Math.sin(Math.PI * segment(part, 0.08, 0.85));
    p.shade = from.shade * (1 - rise);
    p.flight = easeOut(segment(part, 0.6, 1));
  } else {
    // The passes further back move up one place.
    const depth = Math.ceil(place);
    const from = slot(depth);
    const to = slot(depth - 1);
    const step = easeInOut(segment(depth - place, 0.3, 0.8));
    p.top = mix(from.top, to.top, step);
    p.scale = mix(from.scale, to.scale, step);
    p.shade = mix(from.shade, to.shade, step);
    p.alpha = mix(from.alpha, to.alpha, step);
    p.z = depth === 2 ? 1 : 0;
  }
  p.alpha *= fade;
  return p;
}

type Motion = {
  pos: SharedValue<number>;
  index: number;
  count: number;
  reduced: boolean;
};

function Glow({ color, opacity, x, y }: { color: string; opacity: number; x: number; y: number }) {
  return (
    <Svg width={GLOW_RADIUS * 2} height={GLOW_RADIUS * 2} style={{ position: 'absolute', left: x - GLOW_RADIUS, top: y - GLOW_RADIUS }}>
      <Defs>
        <RadialGradient id="glow" cx="50%" cy="50%" r="50%">
          <Stop offset={0} stopColor={color} stopOpacity={opacity} />
          <Stop offset={1} stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Circle cx={GLOW_RADIUS} cy={GLOW_RADIUS} r={GLOW_RADIUS} fill="url(#glow)" />
    </Svg>
  );
}

/** A second light in the colour of the pass in front. */
function PassGlow({ color, pos, index, count }: { color: string } & Motion) {
  const style = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(centered(index, pos.get(), count))) }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <Glow color={color} opacity={0.2} x={290} y={120} />
    </Animated.View>
  );
}

/** One of the header's values in the box it rolls through: the old one leaves upwards as the new one comes up from below. */
function Rolling({
  text,
  style,
  box,
  from,
  to,
  pos,
  index,
  count,
  reduced,
}: { text: string; style: TextStyle; box: number; from: number; to: number } & Motion) {
  const motion = useAnimatedStyle(() => {
    const place = centered(index, headerPosition(pos.get(), from, to), count);
    return {
      opacity: reduced ? Math.max(0, 1 - Math.abs(place)) : rollFade(place),
      transform: [{ translateY: reduced ? 0 : place * box }],
    };
  });
  return (
    <Animated.Text style={[styles.rolling, style, { lineHeight: box }, motion]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

function Field({ label, value, large = false, end = false }: { label: string; value: string; large?: boolean; end?: boolean }) {
  return (
    <View style={end ? styles.fieldEnd : null}>
      <Text style={styles.fieldLabel} numberOfLines={1}>
        {label}
      </Text>
      <Text style={large ? styles.fieldLarge : styles.fieldValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

/** One boarding pass: strip, route, fields, and the stub with its code below the tear line. */
function PassCard({
  pass,
  airline,
  passenger,
  front,
  pos,
  index,
  count,
  reduced,
}: { pass: BoardingPass; airline: string; passenger: string; front: boolean } & Motion) {
  const current = useDerivedValue(() => pose(relative(index, pos.get(), count), count, reduced));

  const cardStyle = useAnimatedStyle(() => {
    const p = current.get();
    return {
      opacity: p.alpha,
      zIndex: p.z,
      transform: [
        { translateY: p.top - ((1 - p.scale) * PASS_HEIGHT) / 2 },
        { perspective: PERSPECTIVE },
        { rotateX: `${p.tilt}deg` },
        { scale: p.scale },
      ],
    };
  });
  const shadeStyle = useAnimatedStyle(() => ({ opacity: current.get().shade }));
  // The plane flies in from the origin's side each time the pass arrives in front.
  const planeStyle = useAnimatedStyle(() => {
    const flight = current.get().flight;
    return {
      opacity: segment(flight, 0, 0.3),
      transform: [{ translateX: -FLIGHT * (1 - flight) }, { scale: 0.8 + 0.2 * flight }],
    };
  });

  return (
    <Animated.View
      style={[styles.pass, cardStyle]}
      accessible={front}
      accessibilityElementsHidden={!front}
      importantForAccessibility={front ? 'yes' : 'no-hide-descendants'}
      accessibilityLabel={`${airline}, ${pass.fromCity} to ${pass.toCity}, flight ${pass.flight}, gate ${pass.gate}, seat ${pass.seat}`}
    >
      <Svg width={PASS_WIDTH} height={PASS_HEIGHT} style={StyleSheet.absoluteFill}>
        <Defs>
          <SvgLinearGradient id="paper" x1="0" y1="0" x2="0" y2="1">
            <Stop offset={0} stopColor={pass.color} />
            <Stop offset={1} stopColor={pass.deep} />
          </SvgLinearGradient>
        </Defs>
        <Path d={OUTLINE} fill="url(#paper)" />
        {/* The strip is a shade lighter than the body. */}
        <Path d={STRIP_OUTLINE} fill="rgba(255,255,255,0.1)" />
        <Line x1={0} y1={STRIP + 0.5} x2={PASS_WIDTH} y2={STRIP + 0.5} stroke={ink(0.14)} strokeWidth={1} />
        {/* The perforation between the two notches. */}
        <Line
          x1={NOTCH + 6}
          y1={TEAR}
          x2={PASS_WIDTH - NOTCH - 6}
          y2={TEAR}
          stroke={ink(0.3)}
          strokeWidth={1.2}
          strokeLinecap="round"
          strokeDasharray="1 5"
        />
      </Svg>

      <View style={styles.brand}>
        {/* The airline's mark: a tern's wing in a ring, in a 20 × 20 box. */}
        <Svg width={20} height={20} fill="none">
          <Circle cx={10} cy={10} r={8.5} stroke={INK} strokeWidth={1.6} />
          <Polyline points="5,12.6 8.8,8.4 10.8,11 15,6.6" stroke={INK} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
        <Text style={styles.airline} numberOfLines={1}>
          {airline}
        </Text>
      </View>
      <View style={styles.gate}>
        <Field label="GATE" value={pass.gate} large end />
        <Field label="SEAT" value={pass.seat} large end />
      </View>

      <View style={styles.cities}>
        <Text style={styles.city} numberOfLines={1}>
          {pass.fromCity}
        </Text>
        <Text style={styles.city} numberOfLines={1}>
          {pass.toCity}
        </Text>
      </View>
      <View style={styles.codes}>
        <Text style={styles.code} numberOfLines={1}>
          {pass.fromCode}
        </Text>
        <Text style={styles.code} numberOfLines={1}>
          {pass.toCode}
        </Text>
      </View>
      <Animated.View style={[styles.plane, planeStyle]}>
        <Svg width={26} height={26} viewBox="0 0 24 24">
          <Polygon points={PLANE} fill={ink(0.7)} />
        </Svg>
      </Animated.View>

      <View style={styles.fields}>
        <View style={styles.flightColumn}>
          <Field label="FLIGHT" value={pass.flight} />
        </View>
        <View style={styles.dateColumn}>
          <Field label="DATE" value={pass.date} />
        </View>
        <View style={styles.boardingColumn}>
          <Field label="BOARDING" value={pass.boarding} />
        </View>
        <View style={styles.spacer} />
        <Field label="CLASS" value={pass.cabin} end />
      </View>
      <View style={styles.person}>
        <Field label="PASSENGER" value={passenger} />
        <Field label="STATUS" value={pass.status} end />
      </View>

      <View style={styles.codeTile}>
        <Svg width={58} height={58} viewBox="0 0 21 21">
          <Path d={codePath(index)} fill={INK} />
        </Svg>
      </View>
      <View style={styles.stub}>
        <Field label="SEQUENCE" value={pass.sequence} />
        <Field label="BAGS" value={pass.bags} />
      </View>
      <Text style={styles.route} numberOfLines={1}>
        {pass.fromCode} – {pass.toCode}
      </Text>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, shadeStyle]}>
        <Svg width={PASS_WIDTH} height={PASS_HEIGHT}>
          <Path d={OUTLINE} fill="#000000" />
        </Svg>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A wallet of boarding passes on a dark panel, under a header with the destination of the pass in
 * front. A tap on a pass behind, or a swipe up, shuffles the stack: the front pass ducks down, the
 * next one is pulled up out of the stack, tips towards the viewer and drops in front, and the old
 * one tucks in at the back. The header rolls to the new city and times, and the small plane
 * between the airport codes flies across once.
 */
export function BoardingPassStack({
  passes = PASSES,
  autoPlay = false,
  airline = 'Tern Airways',
  passenger = 'Lena Hartwell',
  destinationLabel = 'DESTINATION',
  arrivalLabel = 'Arrival time',
  localTimeLabel = 'Local time',
  rideLabel = 'Book a ride',
  onChange,
  onRide,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = passes.length;
  const duration = reducedMotion ? SHUFFLE_REDUCED : SHUFFLE;

  // `pos` counts the shuffles so far; its fraction is how far the running one has come.
  const pos = useSharedValue(0);
  const target = useSharedValue(0);
  const looping = useSharedValue(auto);
  // True while the loop is the one moving the stack.
  const live = useSharedValue(false);
  // Shuffles asked for by a finger that the next frame has to start.
  const requests = useSharedValue(0);
  // Where the loop started counting from, and its clock.
  const base = useSharedValue(0);
  const clock = useSharedValue(0);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);

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
        // A touch took over: the shuffle the loop was in the middle of finishes first.
        goal = Math.ceil(p - 0.000001);
        live.set(false);
      }
      if (requests.get() > 0) {
        goal = Math.min(goal + requests.get(), Math.floor(p) + 2);
        requests.set(0);
      }
      if (p < goal) {
        p = Math.min(goal, p + dt / duration);
      } else if (hold.get() > 0) {
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
  const pull = (by: number) => {
    hold.set(USER_HOLD);
    requests.set(requests.get() + by);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const finger = useRef({ x: 0, y: 0, at: 0 });

  const onGrant = (e: GestureResponderEvent) => {
    finger.current = { x: e.nativeEvent.pageX, y: e.nativeEvent.pageY, at: e.nativeEvent.locationY };
  };

  const onRelease = (e: GestureResponderEvent) => {
    const f = finger.current;
    const dx = e.nativeEvent.pageX - f.x;
    const dy = e.nativeEvent.pageY - f.y;
    if (dy < -SWIPE && Math.abs(dy) > Math.abs(dx)) {
      pull(1);
    } else if (Math.abs(dx) < TAP_SLOP && Math.abs(dy) < TAP_SLOP) {
      // A tap: the strips of the two passes behind lie above the front pass.
      const resting = Math.abs(pos.get() - Math.round(pos.get())) < 0.001;
      if (f.at < FRONT_Y - STACK_Y && resting) pull(f.at < FRONT_Y + SLOT_B.top - STACK_Y ? 2 : 1);
    }
  };

  const motion = { pos, count, reduced: reducedMotion };

  return (
    <View style={styles.panel}>
      <LinearGradient colors={['#161d30', '#0a0e18']} style={StyleSheet.absoluteFill} />
      <Glow color="#4a6fe0" opacity={0.2} x={60} y={30} />
      {passes.map((pass, index) => (
        <PassGlow key={index} color={pass.color} index={index} {...motion} />
      ))}

      <View style={styles.cityBox} accessibilityRole="header">
        {passes.map((pass, index) => (
          <Rolling key={index} text={pass.toCity} style={styles.cityName} box={CITY_BOX} from={0.3} to={0.75} index={index} {...motion} />
        ))}
      </View>
      <Text style={styles.destination} numberOfLines={1}>
        {destinationLabel}
      </Text>

      {[0, 1].map((side) => (
        <View key={side} style={[styles.column, { left: 20 + side * COLUMN * 2 }]} accessible>
          <View style={styles.timeBox}>
            {passes.map((pass, index) => (
              <Rolling
                key={index}
                text={side === 0 ? pass.arrival : pass.localTime}
                style={styles.time}
                box={TIME_BOX}
                from={0.55}
                to={0.98}
                index={index}
                {...motion}
              />
            ))}
          </View>
          <Text style={styles.timeLabel} numberOfLines={1}>
            {side === 0 ? arrivalLabel : localTimeLabel}
          </Text>
        </View>
      ))}
      <Pressable style={styles.ride} onPress={onRide} accessibilityRole="button">
        <Text style={styles.rideLabel} numberOfLines={1}>
          {rideLabel}
        </Text>
      </Pressable>
      {/* Dashed rules between the three items. */}
      <Svg width={WIDTH} height={ROW_HEIGHT} style={styles.rules} pointerEvents="none">
        {[20 + COLUMN, 20 + COLUMN * 2].map((x) => (
          <Line key={x} x1={x} y1={3} x2={x} y2={ROW_HEIGHT - 3} stroke="rgba(255,255,255,0.22)" strokeWidth={1} strokeDasharray="3 3" />
        ))}
      </Svg>

      {passes.map((pass, index) => (
        <PassCard key={index} pass={pass} airline={airline} passenger={passenger} front={index === front} index={index} {...motion} />
      ))}

      {/* Touches on the stack: a tap on a strip behind, or a swipe up anywhere on it. */}
      <View
        style={styles.touch}
        accessibilityActions={[{ name: 'activate', label: 'Next pass' }]}
        onAccessibilityAction={() => pull(1)}
        onStartShouldSetResponder={() => true}
        onResponderTerminationRequest={() => false}
        onResponderGrant={onGrant}
        onResponderRelease={onRelease}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#0a0e18',
  },
  cityBox: {
    position: 'absolute',
    left: 20,
    top: 26,
    width: WIDTH - 40,
    height: CITY_BOX,
    overflow: 'hidden',
  },
  rolling: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    textAlign: 'center',
    color: '#ffffff',
  },
  cityName: {
    fontSize: 42,
    fontWeight: '200',
  },
  destination: {
    position: 'absolute',
    left: 0,
    top: 82,
    width: WIDTH,
    color: 'rgba(255,255,255,0.45)',
    fontSize: 10,
    lineHeight: 14,
    fontWeight: '600',
    letterSpacing: 1.5,
    textAlign: 'center',
  },
  column: {
    position: 'absolute',
    top: ROW_Y,
    width: COLUMN,
    alignItems: 'center',
  },
  timeBox: {
    width: COLUMN,
    height: TIME_BOX,
    overflow: 'hidden',
  },
  time: {
    fontSize: 22,
    fontWeight: '200',
    fontVariant: ['tabular-nums'],
  },
  timeLabel: {
    marginTop: 2,
    color: 'rgba(255,255,255,0.5)',
    fontSize: 10,
    lineHeight: 13,
  },
  ride: {
    position: 'absolute',
    left: 20 + COLUMN,
    top: ROW_Y,
    width: COLUMN,
    height: ROW_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rideLabel: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '300',
  },
  rules: {
    position: 'absolute',
    left: 0,
    top: ROW_Y,
  },
  touch: {
    position: 'absolute',
    left: 0,
    top: STACK_Y,
    width: WIDTH,
    height: HEIGHT - STACK_Y,
    zIndex: 9,
  },
  pass: {
    position: 'absolute',
    left: PASS_X,
    top: FRONT_Y,
    width: PASS_WIDTH,
    height: PASS_HEIGHT,
    borderRadius: PASS_RADIUS,
    boxShadow: '0 -4px 28px rgba(0,0,0,0.3)',
  },
  brand: {
    position: 'absolute',
    left: 18,
    top: 0,
    height: STRIP,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  airline: {
    color: INK,
    fontSize: 15,
    fontWeight: '700',
  },
  gate: {
    position: 'absolute',
    right: 18,
    top: 0,
    height: STRIP,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  fieldEnd: {
    alignItems: 'flex-end',
  },
  fieldLabel: {
    color: ink(0.55),
    fontSize: 7.5,
    lineHeight: 10,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  fieldValue: {
    marginTop: 1,
    color: INK,
    fontSize: 13,
    lineHeight: 17,
    fontVariant: ['tabular-nums'],
  },
  fieldLarge: {
    marginTop: 1,
    color: INK,
    fontSize: 16,
    lineHeight: 20,
    fontVariant: ['tabular-nums'],
  },
  cities: {
    position: 'absolute',
    left: 18,
    top: 66,
    width: PASS_WIDTH - 36,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  city: {
    color: ink(0.62),
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
  },
  codes: {
    position: 'absolute',
    left: 16,
    top: 78,
    width: PASS_WIDTH - 32,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  code: {
    color: ink(0.92),
    fontSize: 44,
    lineHeight: 48,
    fontWeight: '200',
  },
  plane: {
    position: 'absolute',
    left: PASS_WIDTH / 2 - 13,
    top: 89,
  },
  fields: {
    position: 'absolute',
    left: 18,
    top: 140,
    width: PASS_WIDTH - 36,
    flexDirection: 'row',
  },
  flightColumn: {
    width: 74,
  },
  dateColumn: {
    width: 72,
  },
  boardingColumn: {
    width: 62,
  },
  spacer: {
    flex: 1,
  },
  person: {
    position: 'absolute',
    left: 18,
    top: 184,
    width: PASS_WIDTH - 36,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  codeTile: {
    position: 'absolute',
    left: 18,
    top: 244,
    width: 72,
    height: 72,
    borderRadius: 8,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.92)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stub: {
    position: 'absolute',
    left: 108,
    top: 247,
    gap: 9,
  },
  route: {
    position: 'absolute',
    right: 18,
    top: 247,
    color: ink(0.62),
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
});
