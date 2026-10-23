import { BlurMask, Canvas, Circle, DashPathEffect, Group, Line, Path, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** One end of the flight. */
type Stop = { code: string; city: string; time: string };

/** One of the three small fields at the bottom of the panel. */
type Field = { label: string; value: string };

type Props = {
  /** Address of the photo, three wide by two high. */
  photo?: string;
  /** The photo's main colour, shown until it arrives. */
  tint?: string;
  from?: Stop;
  to?: Stop;
  flight?: string;
  /** Length of the flight in minutes: what is left of it counts down under the line. */
  minutes?: number;
  /** The chip's wording before take-off, in the air and after landing. */
  statuses?: [string, string, string];
  /** The words after the hours and the minutes left: for one, and for any other number. */
  hourWords?: [string, string];
  minuteWords?: [string, string];
  /** Shown in place of the time left once the plane has landed. */
  arrived?: string;
  fields?: Field[];
  /** Plays the flight, holds on the landing, then rewinds to the gate, in a loop. */
  autoPlay?: boolean;
  /** Called with the share of the flight, 0…1, while the badge is dragged. */
  onScrub?: (progress: number) => void;
  /** Called when the plane lands. */
  onLand?: () => void;
};

const SIZE = 340;
const RADIUS = 28;

// The photo, 400 × 267, hangs over the card's sides and top so that it can slide and grow.
const PHOTO_W = 400;
const PHOTO_H = 267;
const PHOTO_Y = -60;
// How far the photo slides to either side of the middle, and how much it grows by the landing.
const DRIFT = 16;
const ZOOM = 0.05;
// Where the card starts to fade from the photo into a plain dusk.
const FADE_Y = 166;

const PANEL_W = 324;
const PANEL_H = 160;
const PANEL_Y = 172;
// The line the badge travels along, inside the panel, and the badge's radius.
const LINE_X = 98;
const LINE_W = 128;
const LINE_Y = 44;
const BADGE = 11;
const TRAVEL = LINE_W - BADGE * 2;

// The share of the flight at which the plane has left the gate.
const AIRBORNE = 0.04;
// The chip's flip: the old status tips out, then the new one tips in. Seconds.
const FLIP_OUT = 0.16;
const FLIP_IN = 0.22;
// Length of the cross-fade that replaces the flip under reduced motion.
const FADE_TIME = 0.2;
const GREEN_IN = 0.25;
const GREEN_OUT = 0.2;
// The landing knocks the badge's scale at this speed (per second); a spring of mass 1 brings it back.
const BOUNCE_KICK = 5;
const BOUNCE_STIFFNESS = 260;
const BOUNCE_DAMPING = 12;
// The clocks of the flip and of the bounce run this long: both are over well before.
const CLOCK = 1.5;

// autoPlay (seconds): take-off, the flight, a look at the landing, a quick rewind, a rest.
const TAKE_OFF_AT = 0.8;
const FLIGHT_TIME = 4.6;
const REWIND_AT = 7.2;
const REWIND_TIME = 0.7;
const LOOP = 9;
// After a touch the loop waits this long, then rewinds and starts over.
const RESUME_AFTER = 2;

const FLIGHT_CURVE = Easing.bezier(0.45, 0, 0.55, 1);
const REWIND_CURVE = Easing.bezier(0.5, 0, 0.2, 1);

const INK = '#121316';
const GREY = '#8b8f97';
// The dot in the chip: amber at the gate, sky blue in the air, green on the ground.
const CHIP_DOTS = ['#fbbf24', '#7dd3fc', '#4ade80'];

// A plane seen from above, nose up, in a 20 × 20 box.
const PLANE =
  'M 10 1.5 Q 11.4 1.5 11.4 4 L 11.4 7.6 L 18 11.6 L 18 13.4 L 11.4 11.4 L 11.4 15.4 L 13.6 17 L 13.6 18.4 L 10 17.4 ' +
  'L 6.4 18.4 L 6.4 17 L 8.6 15.4 L 8.6 11.4 L 2 13.4 L 2 11.6 L 8.6 7.6 L 8.6 4 Q 8.6 1.5 10 1.5 Z';

const FIELDS: Field[] = [
  { label: 'Airline', value: 'Nimbus Air' },
  { label: 'Flight Code', value: 'NB 880' },
  { label: 'Class', value: 'Economy' },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** 0 at the gate, 1 in the air, 2 landed. */
function statusAt(progress: number) {
  'worklet';
  return progress >= 0.999 ? 2 : progress > AIRBORNE ? 1 : 0;
}

/** How far the knocked spring is from rest `t` seconds after the landing. */
function kick(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const decay = BOUNCE_DAMPING / 2;
  const ring = Math.sqrt(BOUNCE_STIFFNESS - decay * decay);
  return (BOUNCE_KICK / ring) * Math.exp(-decay * t) * Math.sin(ring * t);
}

/** Angle in degrees and opacity of chip `k`: the old status tips back out of sight, then the new one tips in. */
function chipLook(k: number, from: number, shown: number, flip: number, still: boolean) {
  'worklet';
  if (still) {
    const seen = clamp01(flip / FADE_TIME);
    return { angle: 0, opacity: k === shown ? seen : k === from ? 1 - seen : 0 };
  }
  if (k === shown) {
    const turn = clamp01((flip - FLIP_OUT) / FLIP_IN);
    return { angle: -90 * (1 - turn) * (1 - turn), opacity: flip >= FLIP_OUT ? 1 : 0 };
  }
  if (k === from) {
    const turn = clamp01(flip / FLIP_OUT);
    return { angle: 90 * turn * turn, opacity: flip < FLIP_OUT ? 1 : 0 };
  }
  return { angle: 0, opacity: 0 };
}

type Chips = {
  /** The status the chip is leaving, the one it shows, and the seconds since that flip began. */
  statusFrom: SharedValue<number>;
  status: SharedValue<number>;
  flip: SharedValue<number>;
  still: boolean;
};

function Chip({ k, label, statusFrom, status, flip, still }: Chips & { k: number; label: string }) {
  const style = useAnimatedStyle(() => {
    const look = chipLook(k, statusFrom.get(), status.get(), flip.get(), still);
    return { opacity: look.opacity, transform: [{ perspective: 300 }, { rotateX: `${look.angle}deg` }] };
  });

  return (
    <Animated.View style={[styles.chip, style]}>
      <View style={[styles.chipDot, { backgroundColor: CHIP_DOTS[k] }]} />
      <Text style={styles.chipLabel} numberOfLines={1}>
        {label}
      </Text>
    </Animated.View>
  );
}

type LineProps = {
  progress: SharedValue<number>;
  /** 0 black … 1 green. */
  green: SharedValue<number>;
  /** Seconds since the plane landed. */
  landing: SharedValue<number>;
  still: boolean;
};

/** The dashed line, its solid part behind the badge, and the badge with its plane. */
function FlightLine({ progress, green, landing, still }: LineProps) {
  const end = useDerivedValue(() => vec(LINE_X + BADGE + TRAVEL * progress.get(), LINE_Y));
  const badge = useDerivedValue(() => [
    { translateX: LINE_X + BADGE + TRAVEL * progress.get() },
    { translateY: LINE_Y },
    { scale: 1 + (still ? 0 : kick(landing.get())) },
  ]);

  return (
    <Canvas style={styles.canvas} pointerEvents="none">
      <Line p1={vec(LINE_X, LINE_Y)} p2={vec(LINE_X + LINE_W, LINE_Y)} style="stroke" strokeWidth={1.2} color="#cfd2d6">
        <DashPathEffect intervals={[3, 3]} />
      </Line>
      <Line p1={vec(LINE_X, LINE_Y)} p2={end} style="stroke" strokeWidth={1.6} strokeCap="round" color={INK} />
      <Group transform={badge}>
        <Circle cx={0} cy={2} r={BADGE} color="rgba(0,0,0,0.22)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={BADGE} color={INK} />
        <Circle cx={0} cy={0} r={BADGE} color="#1fa055" opacity={green} />
        {/* The plane is drawn nose up: turned to face the way it flies. */}
        <Group transform={[{ rotate: Math.PI / 2 }, { scale: 0.62 }, { translateX: -10 }, { translateY: -10 }]}>
          <Path path={PLANE} color="#ffffff" />
        </Group>
      </Group>
    </Canvas>
  );
}

type RemainingProps = {
  progress: SharedValue<number>;
  minutes: number;
  hourWords: [string, string];
  minuteWords: [string, string];
  arrived: string;
};

/** The time left, counting down in minutes with the flight. */
function Remaining({ progress, minutes, hourWords, minuteWords, arrived }: RemainingProps) {
  const [left, setLeft] = useState(minutes);

  useAnimatedReaction(
    () => Math.round(minutes * (1 - progress.get())),
    (now, old) => {
      if (now !== old) scheduleOnRN(setLeft, now);
    },
  );

  const hours = Math.floor(left / 60);
  const rest = left % 60;
  return (
    <Text style={[styles.small, styles.at, styles.center, { left: 82, top: 62, width: 160, height: 14, lineHeight: 14 }]} numberOfLines={1}>
      {left > 0 ? `${hours} ${hourWords[hours === 1 ? 0 : 1]} ${rest < 10 ? '0' : ''}${rest} ${minuteWords[rest === 1 ? 0 : 1]}` : arrived}
    </Text>
  );
}

function StopColumn({ stop, x, right = false }: { stop: Stop; x: number; right?: boolean }) {
  const align = right && styles.right;
  return (
    <>
      <Text style={[styles.small, styles.at, align, { left: x, top: 14, width: 180, height: 14, lineHeight: 14 }]} numberOfLines={1}>
        {stop.time}
      </Text>
      <Text style={[styles.code, styles.at, align, { left: x, top: 30, width: 180, height: 28, lineHeight: 28 }]} numberOfLines={1}>
        {stop.code}
      </Text>
      <Text style={[styles.city, styles.at, align, { left: x, top: 60, width: 180, height: 16, lineHeight: 16 }]} numberOfLines={1}>
        {stop.city}
      </Text>
    </>
  );
}

/**
 * A flight on a square photo card: a view over the wing, and a white panel with the two airports, a
 * dashed line the plane's badge travels along, the time left and three small fields. As the flight
 * progresses the line turns solid behind the badge, the status chip on the photo flips from Boarding
 * to In flight to Landed, the time counts down and the clouds drift past; on landing the badge turns
 * green with a small bounce. Dragging the badge scrubs the flight.
 */
export function FlightStatusCard({
  photo = 'https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=1200&h=800&q=80',
  tint = '#a4abb1',
  from = { code: 'SUB', city: 'Surabaya', time: '08:45 PM' },
  to = { code: 'SIN', city: 'Singapore', time: '12:10 AM' },
  flight = 'NB-880',
  minutes = 205,
  statuses = ['Boarding', 'In flight', 'Landed'],
  hourWords = ['Hour', 'Hours'],
  minuteWords = ['Minute', 'Minutes'],
  arrived = 'Arrived',
  fields = FIELDS,
  autoPlay = false,
  onScrub,
  onLand,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;

  // The share of the flight, 0 at the gate … 1 landed.
  const progress = useSharedValue(0);
  const statusFrom = useSharedValue(0);
  const status = useSharedValue(0);
  const flip = useSharedValue(CLOCK);
  const green = useSharedValue(0);
  const landing = useSharedValue(CLOCK);

  // A touch stops the loop; `count` restarts the wait before it resumes.
  const [touch, setTouch] = useState({ down: false, count: 0 });
  const drag = useRef({ x: 0, from: 0, down: false });
  const [shown, setShown] = useState(0);

  const landed = () => {
    if (drag.current.down && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onLand?.();
  };

  // Keeps the chip and the landing in step with the flight.
  useAnimatedReaction(
    () => statusAt(progress.get()),
    (now, old) => {
      if (old === null || now === old) return;
      const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
      statusFrom.set(old);
      status.set(now);
      flip.set(0);
      flip.set(withTiming(CLOCK, { duration: CLOCK * 1000, ...linear }));
      scheduleOnRN(setShown, now);
      if (now === 2) {
        green.set(withTiming(1, { duration: GREEN_IN * 1000, ...linear }));
        landing.set(0);
        landing.set(withTiming(CLOCK, { duration: CLOCK * 1000, ...linear }));
        scheduleOnRN(landed);
      } else if (old === 2) {
        green.set(withTiming(0, { duration: GREEN_OUT * 1000, ...linear }));
      }
    },
  );

  // The flight plays, holds on the landing and rewinds; after a touch it first goes back to the gate.
  useEffect(() => {
    if (!auto || touch.down) return;
    const fly = (target: number, time: number, easing: typeof FLIGHT_CURVE) => {
      progress.set(withTiming(target, { duration: time * 1000, easing, reduceMotion: ReduceMotion.Never }));
    };
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const rewind = n % 2 === 1;
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / 2) * LOOP + (rewind ? REWIND_AT : TAKE_OFF_AT)) * 1000;
      timer = setTimeout(() => {
        if (rewind) fly(0, REWIND_TIME, REWIND_CURVE);
        else fly(1, FLIGHT_TIME, FLIGHT_CURVE);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touch.count === 0) {
      run(Date.now(), 0);
    } else {
      timer = setTimeout(() => {
        fly(0, REWIND_TIME, REWIND_CURVE);
        run(Date.now() + REWIND_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touch, progress]);

  // --- Dragging along the line scrubs the flight. ---
  const onGrant = (e: GestureResponderEvent) => {
    cancelAnimation(progress);
    drag.current = { x: e.nativeEvent.pageX, from: progress.get(), down: true };
    setTouch((state) => ({ ...state, down: true }));
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const onMove = (e: GestureResponderEvent) => {
    const next = Math.min(1, Math.max(0, drag.current.from + (e.nativeEvent.pageX - drag.current.x) / TRAVEL));
    progress.set(next);
    onScrub?.(next);
  };

  const onEnd = () => {
    drag.current.down = false;
    setTouch((state) => ({ down: false, count: state.count + 1 }));
  };

  // The photo is wider than the card: it slides past and grows a touch as the flight goes on.
  const photoStyle = useAnimatedStyle(() => ({
    transform: still ? [] : [{ translateX: DRIFT * (1 - 2 * progress.get()) }, { scale: 1 + ZOOM * progress.get() }],
  }));

  const chips: Chips = { statusFrom, status, flip, still };

  return (
    <View style={styles.shadow}>
      <View
        style={[styles.card, { backgroundColor: tint }]}
        accessible
        accessibilityLabel={`${flight}, ${from.city} to ${to.city}`}
        accessibilityValue={{ text: statuses[shown] }}
      >
        <Animated.View style={[styles.photo, photoStyle]}>
          <Image source={photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        </Animated.View>
        {/* The photo ends behind the panel: below it the card fades into the colour of the dusk. */}
        <LinearGradient style={styles.dusk} colors={['#7c7a8600', '#7c7a86', '#4a5068']} locations={[0, 0.23, 1]} />

        {statuses.map((label, k) => (
          <Chip key={k} k={k} label={label} {...chips} />
        ))}

        <View style={styles.panel}>
          <StopColumn stop={from} x={20} />
          <StopColumn stop={to} x={124} right />

          <Text style={[styles.small, styles.at, styles.center, { left: 102, top: 14, width: 120, height: 14, lineHeight: 14 }]} numberOfLines={1}>
            {flight}
          </Text>
          <FlightLine progress={progress} green={green} landing={landing} still={still} />
          <Remaining progress={progress} minutes={minutes} hourWords={hourWords} minuteWords={minuteWords} arrived={arrived} />

          <View style={styles.fields} />
          {fields.slice(0, 3).map((field, k) => (
            <View key={field.label} style={[styles.field, { left: 12 + k * 100 }]}>
              {k > 0 && <View style={styles.divider} />}
              <Text style={[styles.small, styles.center, styles.fieldLabel]} numberOfLines={1}>
                {field.label}
              </Text>
              <Text style={[styles.value, styles.center]} numberOfLines={1}>
                {field.value}
              </Text>
            </View>
          ))}

          <View
            style={styles.scrub}
            onStartShouldSetResponder={() => true}
            onResponderTerminationRequest={() => false}
            onResponderGrant={onGrant}
            onResponderMove={onMove}
            onResponderRelease={onEnd}
            onResponderTerminate={onEnd}
          />
        </View>

        <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.rim]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  shadow: {
    width: SIZE,
    height: SIZE,
    borderRadius: RADIUS,
    boxShadow: '0 16 30 0 rgba(0,0,0,0.12)',
  },
  card: {
    width: SIZE,
    height: SIZE,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  rim: {
    borderRadius: RADIUS,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.9)',
  },
  photo: {
    position: 'absolute',
    left: (SIZE - PHOTO_W) / 2,
    top: PHOTO_Y,
    width: PHOTO_W,
    height: PHOTO_H,
  },
  dusk: {
    position: 'absolute',
    left: 0,
    top: FADE_Y,
    width: SIZE,
    height: SIZE - FADE_Y,
  },
  chip: {
    position: 'absolute',
    left: 14,
    top: 14,
    height: 26,
    paddingHorizontal: 10,
    borderRadius: 13,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
    backgroundColor: 'rgba(16,20,30,0.5)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chipDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  chipLabel: {
    color: '#ffffff',
    fontSize: 11.5,
    fontWeight: '600',
  },
  panel: {
    position: 'absolute',
    left: (SIZE - PANEL_W) / 2,
    top: PANEL_Y,
    width: PANEL_W,
    height: PANEL_H,
    borderRadius: 22,
    backgroundColor: '#ffffff',
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PANEL_W,
    height: PANEL_H,
  },
  at: {
    position: 'absolute',
  },
  center: {
    textAlign: 'center',
  },
  right: {
    textAlign: 'right',
  },
  small: {
    color: GREY,
    fontSize: 10.5,
    fontVariant: ['tabular-nums'],
  },
  code: {
    color: INK,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  city: {
    color: '#6f7379',
    fontSize: 12,
  },
  fields: {
    position: 'absolute',
    left: 12,
    top: 88,
    width: 300,
    height: 60,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#ececee',
    backgroundColor: '#fafafa',
  },
  field: {
    position: 'absolute',
    top: 100,
    width: 100,
    height: 36,
  },
  divider: {
    position: 'absolute',
    left: -0.5,
    top: 0,
    width: 1,
    height: 36,
    backgroundColor: '#ececee',
  },
  fieldLabel: {
    height: 14,
    lineHeight: 14,
  },
  value: {
    marginTop: 5,
    height: 17,
    lineHeight: 17,
    color: INK,
    fontSize: 12.5,
    fontWeight: '500',
  },
  scrub: {
    position: 'absolute',
    left: LINE_X - 16,
    top: 22,
    width: LINE_W + 32,
    height: 44,
  },
});
