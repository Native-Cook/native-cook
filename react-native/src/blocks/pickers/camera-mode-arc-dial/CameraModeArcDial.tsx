import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** How a mode is drawn on the ring. */
type Mark =
  /** A tulip, for close-ups. */
  | { kind: 'macro' }
  /** Two peaks and a sun. */
  | { kind: 'landscape' }
  /** A word in an outlined capsule. */
  | { kind: 'capsule'; text: string }
  /** A letter on a white disc. */
  | { kind: 'disc'; text: string }
  /** Bare monospaced text. */
  | { kind: 'plain'; text: string };

type Mode = {
  mark: Mark;
  /** Shown in the middle of the dial while the mode is under the index. */
  name: string;
};

type Props = {
  /** The modes, in the order they sit on the ring (clockwise). */
  modes?: Mode[];
  /** Index of the mode under the index at first, and where `autoPlay` rests. */
  initial?: number;
  /** The small label over the mode's name. */
  label?: string;
  /** Clicks through the modes and sweeps back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the mode the ring settles on. */
  onChange?: (index: number) => void;
};

const WIDTH = 336;
const HEIGHT = 304;
const CENTER_X = 168;
const CENTER_Y = 166;

// Outer radius of the ticks, their length and the index's.
const RADIUS = 160;
const TICK = 10;
const INDEX = 26;
// How far the index reaches outward as a mark clicks in.
const KICK = 5;
const TICKS = 49;

// Radius of the ring of marks, and the degrees from one mark to the next.
const RING = 106;
const STEP = 19;
const CAPSULE_W = 46;
const CAPSULE_H = 23;
const DISC = 27;
// The mark under the index is this much larger.
const GROW = 0.12;
// A tap this close to a mark's centre picks it.
const REACH = 20;
// A touch that moves less than this is a tap.
const SLOP = 8;
// A flick carries the ring on by its speed times this many seconds.
const CARRY = 0.18;
// The box a mark is laid out in.
const BOX = 60;

const CAPTION_FADE = 120;
const SWEEP_MAX = 0.9;

// autoPlay (seconds): rest, a click every `STEP_GAP` with one gap skipped halfway, the sweep back.
const REST = 0.9;
const STEP_GAP = 0.6;
const LOOP = 8;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const CLICK_SPRING = { mass: 1, stiffness: 190, damping: 18, reduceMotion: ReduceMotion.Never };
const FLING_SPRING = { mass: 1, stiffness: 140, damping: 20, reduceMotion: ReduceMotion.Never };
// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
const SWEEP_CURVE = Easing.bezier(0.5, 0, 0.2, 1.3);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

const MODES: Mode[] = [
  { mark: { kind: 'macro' }, name: 'Macro' },
  { mark: { kind: 'landscape' }, name: 'Landscape' },
  { mark: { kind: 'capsule', text: 'AUTO' }, name: 'Auto' },
  { mark: { kind: 'disc', text: 'P' }, name: 'Program' },
  { mark: { kind: 'disc', text: 'S' }, name: 'Shutter priority' },
  { mark: { kind: 'disc', text: 'A' }, name: 'Aperture priority' },
  { mark: { kind: 'disc', text: 'M' }, name: 'Manual' },
  { mark: { kind: 'plain', text: 'C1' }, name: 'Custom 1' },
  { mark: { kind: 'plain', text: 'C2' }, name: 'Custom 2' },
  { mark: { kind: 'plain', text: 'C3' }, name: 'Custom 3' },
];

// The macro tulip and the landscape's two peaks, each in a 24 pt box around the origin.
const TULIP =
  'M-6.5 -10.5 L-3.25 -7.5 L0 -10.5 L3.25 -7.5 L6.5 -10.5 L6.5 -4.5 A6.5 6.5 0 0 1 -6.5 -4.5 Z ' +
  'M-1 1.5 H1 V11.5 H-1 Z M0 11.5 Q-9 11.5 -9 3.5 Q-2 5.5 0 11.5 Z M0 11.5 Q9 11.5 9 3.5 Q2 5.5 0 11.5 Z';
const PEAKS = 'M-11 9 L-3.5 -8 L4 9 Z M1.5 9 L6.5 0 L11.5 9 Z';

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

/** Degrees clockwise of 12 o'clock at which the point lies from the dial's centre. */
function degreesOf(x: number, y: number) {
  'worklet';
  return (Math.atan2(x - CENTER_X, CENTER_Y - y) * 180) / Math.PI;
}

/** Past either end the ring gives less and less, up to 0.6 of a mark. */
function rubber(turn: number, last: number) {
  'worklet';
  const over = turn < 0 ? -turn : Math.max(0, turn - last);
  if (over <= 0) return turn;
  const give = 0.6 * (1 - 1 / (over * 0.9 + 1));
  return turn < 0 ? -give : last + give;
}

/** The fixed half circle of ticks, without the one at 12 o'clock: that is the index. */
const TICK_PATH = Array.from({ length: TICKS }, (_, i) => i)
  .filter((i) => i !== (TICKS - 1) / 2)
  .map((i) => {
    const angle = ((-90 + (180 * i) / (TICKS - 1)) * Math.PI) / 180;
    const x = Math.sin(angle);
    const y = -Math.cos(angle);
    const from = RADIUS - TICK;
    return `M${(CENTER_X + from * x).toFixed(2)} ${(CENTER_Y + from * y).toFixed(2)} L${(CENTER_X + RADIUS * x).toFixed(2)} ${(CENTER_Y + RADIUS * y).toFixed(2)}`;
  })
  .join(' ');

function MarkArt({ mark }: { mark: Mark }) {
  switch (mark.kind) {
    case 'macro':
      return (
        <Svg width={24} height={24} viewBox="-12 -12 24 24">
          <Path d={TULIP} fill="#ffffff" />
        </Svg>
      );
    case 'landscape':
      return (
        <Svg width={24} height={24} viewBox="-12 -12 24 24">
          <Path d={PEAKS} fill="#ffffff" />
          <Circle cx={8} cy={-6.4} r={2.6} fill="#ffffff" />
        </Svg>
      );
    case 'capsule':
      return (
        <View style={styles.capsule}>
          <Text style={styles.capsuleText} numberOfLines={1}>
            {mark.text}
          </Text>
        </View>
      );
    case 'disc':
      return (
        <View style={styles.disc}>
          <Text style={styles.discText} numberOfLines={1}>
            {mark.text}
          </Text>
        </View>
      );
    case 'plain':
      return (
        <Text style={styles.plain} numberOfLines={1}>
          {mark.text}
        </Text>
      );
  }
}

/**
 * One mode's mark on the ring. Its top points along the ring, so it stands upright at 9 o'clock and
 * reads top to bottom under the index.
 */
function RingMark({ index, mark, turn }: { index: number; mark: Mark; turn: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const away = index - turn.get();
    const degrees = away * STEP;
    const angle = (degrees * Math.PI) / 180;
    const scale = 1 + GROW * Math.max(0, 1 - Math.abs(away));
    return {
      // Marks more than one place from the index dim a little, down to 60% four places away.
      opacity: Math.abs(degrees) < 200 ? 1 - 0.4 * clamp((Math.abs(away) - 1) / 3, 0, 1) : 0,
      transform: [
        { translateX: RING * Math.sin(angle) },
        { translateY: -RING * Math.cos(angle) },
        { rotate: `${degrees + 90}deg` },
        { scale },
      ],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.mark, style]}>
      <MarkArt mark={mark} />
    </Animated.View>
  );
}

/** A mode's name in the middle of the ring: it fades in while its mark is the one under the index. */
function Caption({ index, name, last, turn, still }: { index: number; name: string; last: number; turn: SharedValue<number>; still: boolean }) {
  const style = useAnimatedStyle(() => ({
    opacity: withTiming(clamp(Math.round(turn.get()), 0, last) === index ? 1 : 0, { duration: still ? 0 : CAPTION_FADE, ...LINEAR }),
  }));
  return (
    <Animated.Text style={[styles.name, style]} numberOfLines={1}>
      {name}
    </Animated.Text>
  );
}

/**
 * The top of a camera's mode dial, drawn flat: a fixed half circle of ticks with a long index at
 * 12 o'clock, and inside it a ring of mode marks that turns in clicks. Dragging around the centre
 * turns the ring, a flick carries it on, and it always settles with one mark under the index;
 * tapping a mark brings it there. The mode's name shows in the middle.
 */
export function CameraModeArcDial({ modes = MODES, initial = 2, label = 'MODE', autoPlay = false, onChange }: Props) {
  const still = useReducedMotion();
  const last = Math.max(0, modes.length - 1);
  const home = clamp(initial, 0, last);
  const auto = autoPlay && !still && modes.length > 1;

  // The ring's position, in marks: at 3 the fourth mode is under the index.
  const turn = useSharedValue(home);
  // The index tick's kick as a mark clicks in under it, 0…1.
  const kick = useSharedValue(0);
  const grabTurn = useSharedValue(0);
  const lastAngle = useSharedValue(0);
  // Degrees the finger has gone round the centre since the drag began.
  const swept = useSharedValue(0);
  // The mode the ring is heading for.
  const heading = useSharedValue(home);
  // A finger moved the ring last, so its clicks are felt.
  const byHand = useSharedValue(false);
  // The finger is still down.
  const held = useSharedValue(false);

  // `heading`, for the screen reader.
  const [selected, setSelected] = useState(home);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const settle = (index: number) => {
    if (index === heading.get()) return;
    heading.set(index);
    setSelected(index);
    onChange?.(index);
  };

  /**
   * Turns the ring until mode `index` is under the index: one click on a spring to a neighbour,
   * one sweep that runs a little past and comes back to a mode further away.
   */
  const select = (index: number) => {
    const target = clamp(index, 0, last);
    const distance = Math.abs(target - turn.get());
    if (still) turn.set(target);
    else if (distance <= 1.2) turn.set(withSpring(target, CLICK_SPRING));
    else turn.set(withTiming(target, { duration: Math.min(SWEEP_MAX, 0.3 + 0.1 * distance) * 1000, easing: SWEEP_CURVE, reduceMotion: ReduceMotion.Never }));
    settle(target);
  };

  const touch = (down: boolean) => {
    byHand.set(true);
    held.set(down);
    setTouches((count) => count + 1);
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  // A mark has come under the index: the index kicks, and a ring turned by hand clicks.
  useAnimatedReaction(
    () => clamp(Math.round(turn.get()), 0, last),
    (now, old) => {
      if (old === null || now === old) return;
      if (!still) kick.set(withSequence(withTiming(1, { duration: 60, ...LINEAR }), withTiming(0, { duration: 220, ...LINEAR })));
      if (byHand.get()) scheduleOnRN(tick);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(SLOP)
    .onBegin(() => {
      scheduleOnRN(touch, true);
    })
    .onStart((event) => {
      grabTurn.set(turn.get());
      lastAngle.set(degreesOf(event.x, event.y));
      swept.set(0);
    })
    .onUpdate((event) => {
      // The finger's angle wraps at 6 o'clock; the turn it adds up to does not.
      const angle = degreesOf(event.x, event.y);
      let delta = angle - lastAngle.get();
      if (delta > 180) delta -= 360;
      if (delta < -180) delta += 360;
      lastAngle.set(angle);
      swept.set(swept.get() + delta);
      turn.set(rubber(grabTurn.get() - swept.get() / STEP, last));
    })
    .onEnd((event) => {
      if (still) {
        const target = clamp(Math.round(turn.get()), 0, last);
        turn.set(target);
        scheduleOnRN(settle, target);
        return;
      }
      // How fast the finger was going round the centre, in marks a second.
      const x = event.x - CENTER_X;
      const y = event.y - CENTER_Y;
      const reach = Math.max(x * x + y * y, 40 * 40);
      const speed = ((-(x * event.velocityY - y * event.velocityX) / reach) * 180) / Math.PI / STEP;
      const target = clamp(Math.round(turn.get() + speed * CARRY), 0, last);
      turn.set(withSpring(target, { ...FLING_SPRING, velocity: speed }));
      scheduleOnRN(settle, target);
    })
    .onFinalize(() => {
      scheduleOnRN(touch, false);
    });

  const pick = (x: number, y: number) => {
    const at = turn.get();
    const hit = modes.findIndex((_, i) => {
      const angle = ((i - at) * STEP * Math.PI) / 180;
      return Math.hypot(x - (CENTER_X + RING * Math.sin(angle)), y - (CENTER_Y - RING * Math.cos(angle))) < REACH;
    });
    if (hit >= 0) select(hit);
  };

  const tap = Gesture.Tap()
    .maxDistance(SLOP)
    .onEnd((event) => {
      scheduleOnRN(pick, event.x, event.y);
    });

  /** Turns back to the first mode once the finger has gone; false while it is still down. */
  const goHome = () => {
    if (held.get()) return false;
    byHand.set(false);
    select(home);
    return true;
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ select, goHome });
  useEffect(() => {
    steps.current = { select, goHome };
  });

  // The script a finger would follow: click through the modes after the first, pausing halfway,
  // then sweep back.
  useEffect(() => {
    if (!auto) return;
    const forward = home < last;
    const count = forward ? last - home : home;
    const gap = Math.min(STEP_GAP, 4.8 / (count + 1));
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const k = n % (count + 1);
      const slot = k >= Math.floor((count + 1) / 2) ? k + 1 : k;
      const at = k < count ? REST + slot * gap : REST + (count + 1) * gap + 0.5;
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / (count + 1)) * LOOP + at) * 1000;
      timer = setTimeout(() => {
        steps.current.select(k < count ? home + (forward ? k + 1 : -(k + 1)) : home);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, turn back to the first mode and start over.
      timer = setTimeout(() => {
        if (!steps.current.goHome()) return;
        run(Date.now() + (SWEEP_MAX + 0.2) * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, home, last]);

  const indexStyle = useAnimatedStyle(() => ({
    top: CENTER_Y - RADIUS - KICK * kick.get(),
    height: INDEX + KICK * kick.get(),
    transform: [{ scaleX: 1 + kick.get() / 3 }],
  }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.root}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityValue={{ text: modes[selected]?.name }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          touch(false);
          select(heading.get() + (event.nativeEvent.actionName === 'increment' ? 1 : -1));
        }}
      >
        <Svg width={WIDTH} height={HEIGHT} style={StyleSheet.absoluteFill} pointerEvents="none">
          <Path d={TICK_PATH} stroke="#ffffff" strokeOpacity={0.9} strokeWidth={1.5} strokeLinecap="round" />
        </Svg>
        <Animated.View pointerEvents="none" style={[styles.index, indexStyle]} />

        {modes.map((mode, index) => (
          <RingMark key={index} index={index} mark={mode.mark} turn={turn} />
        ))}

        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
        {modes.map((mode, index) => (
          <Caption key={index} index={index} name={mode.name} last={last} turn={turn} still={still} />
        ))}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  index: {
    position: 'absolute',
    left: CENTER_X - 0.75,
    width: 1.5,
    borderRadius: 1,
    backgroundColor: '#ffffff',
  },
  mark: {
    position: 'absolute',
    left: CENTER_X - BOX / 2,
    top: CENTER_Y - BOX / 2,
    width: BOX,
    height: BOX,
    alignItems: 'center',
    justifyContent: 'center',
  },
  capsule: {
    width: CAPSULE_W,
    height: CAPSULE_H,
    borderRadius: CAPSULE_H / 2,
    borderWidth: 1.5,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  capsuleText: {
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '800',
    letterSpacing: 0.4,
    includeFontPadding: false,
  },
  disc: {
    width: DISC,
    height: DISC,
    borderRadius: DISC / 2,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  discText: {
    color: '#000000',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
    includeFontPadding: false,
  },
  plain: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '700',
    fontFamily: MONO,
    includeFontPadding: false,
  },
  label: {
    position: 'absolute',
    left: 0,
    top: CENTER_Y - 17,
    width: WIDTH,
    height: 12,
    lineHeight: 12,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.45)',
    fontSize: 9.5,
    fontWeight: '600',
    // Tracking adds a gap after the last letter too; the padding puts the word back in the middle.
    letterSpacing: 2,
    paddingLeft: 2,
    includeFontPadding: false,
  },
  name: {
    position: 'absolute',
    left: 0,
    top: CENTER_Y - 3,
    width: WIDTH,
    height: 20,
    lineHeight: 20,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
