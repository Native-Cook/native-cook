import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  PathOp,
  RadialGradient,
  Rect,
  type SkPath,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
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
import { scheduleOnRN } from 'react-native-worklets';

type Field = { label: string; value: string };

type Airport = {
  /** Drawn in board dots; letters A to Z. */
  code: string;
  name: string;
  city: string;
};

type Props = {
  /** Flaps the board, tears the stub off and lets it snap back, in a loop. */
  autoPlay?: boolean;
  fields?: Field[];
  origin?: Airport;
  destination?: Airport;
  airline?: string;
  flight?: string;
  cabin?: string;
  /** The date as the stub spells it. */
  date?: string;
  /** Called when the ticket is tapped. */
  onPress?: () => void;
  /** Called when a drag tears the stub off. */
  onTear?: () => void;
};

const WIDTH = 328;
const HEIGHT = 574;
const RADIUS = 24;
// Where the perforation runs.
const TEAR_Y = 480;
// Radius of the half circles cut out of both sides, and of the holes of the perforation.
const NOTCH = 13;
const HOLE = 3;
const HOLES = 28;
const HOLE_INSET = 26;

const PAD = 30;
const STUB_PAD = 22;
const FIELD_INSET = 14;
// The shadows' canvases overflow the ticket by this much on every side.
const BLEED = 64;

// The board: a 5 x 7 grid of square dots per letter.
const CELL = 8;
const DOT = 6.6;
const LETTER_GAP = 9;
const ORIGIN_TOP = 104;
const DESTINATION_TOP = 299;
const SMALL_CELL = 1.55;
const SMALL_DOT = 1.15;
const BARCODE_WIDTH = 92;

const PALE = '#b9d6ff';
// The aurora starts to wash out into the pale blue here.
const PALE_FROM = 440;
const STUB_INK = '#1f2f5c';
const STUB_MUTE = '#6c85b0';

// A flap: every FLAP_STEP seconds a letter shows another glyph, FLAPS times, and each letter
// (then each field) starts two steps after the one before it.
const FLAP_STEP = 0.075;
const FLAPS = 8;
const FLAP_STAGGER = 2;
const FIELD_FLAPS = 6;
// Steps until the last of six letters has landed.
const FLAP_STEPS = 18;
const GRID_IN = 0.12;
const GRID_OUT = 0.45;

// The stub. It tears off once it is pulled TEAR_AT points down.
const TEAR_AT = 46;
// Degrees the stub swings on its top left corner before it tears off.
const SWING = 6.5;
// Torn off, it hangs this far below the ticket, at this angle, and follows the pull by FOLLOW.
const DROP = 20;
const TILT = 2.5;
const FOLLOW = 0.5;
// The spring the stub's drop and angle chase their targets on (mass 1).
const STIFFNESS = 240;
const DAMPING = 19;

// autoPlay loop (seconds): the board flaps, a finger pulls the stub down and off, then lets go.
const FLAP_AT = 0.8;
const TEAR_START = 2.5;
const PULL_TIME = 1;
const PULL_MAX = 84;
const RELEASE_AT = 4.7;
const LOOP = 7;
// After a touch the loop waits this long before it starts again from rest.
const USER_HOLD = 2;
// Without the loop the board flaps once, this long after the ticket appears.
const APPEAR_FLAP_AT = 0.3;

// Three lights that circle slowly, once per loop: violet on the right, royal blue in the middle, cyan on the left.
const AURORA = [
  { color: '#5a35c8', x: 300, y: 392, radius: 150, driftX: 14, driftY: -12, phase: 2.2 },
  { color: '#1b2fd0', x: 170, y: 424, radius: 176, driftX: -20, driftY: 10, phase: 1 },
  { color: '#19c8f0', x: 14, y: 462, radius: 150, driftX: 16, driftY: 9, phase: 0 },
];

// Bars and gaps of the barcode, in turn, in units.
const BARCODE = [2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 1, 2, 2, 1, 1, 3, 1, 2, 1, 1, 2, 1, 3, 1, 2, 1, 1, 2, 1];

// 5 x 7 dot letters A to Z, one number per row with the left dot as the high bit.
const GLYPHS = [
  [14, 17, 17, 31, 17, 17, 17], [30, 17, 17, 30, 17, 17, 30], [14, 17, 16, 16, 16, 17, 14], [30, 17, 17, 17, 17, 17, 30],
  [31, 16, 16, 30, 16, 16, 31], [31, 16, 16, 30, 16, 16, 16], [14, 17, 16, 23, 17, 17, 14], [17, 17, 17, 31, 17, 17, 17],
  [14, 4, 4, 4, 4, 4, 14], [7, 2, 2, 2, 2, 18, 12], [17, 18, 20, 24, 20, 18, 17], [16, 16, 16, 16, 16, 16, 31],
  [17, 27, 21, 21, 17, 17, 17], [17, 25, 21, 19, 17, 17, 17], [14, 17, 17, 17, 17, 17, 14], [30, 17, 17, 30, 16, 16, 16],
  [14, 17, 17, 17, 21, 18, 13], [30, 17, 17, 30, 20, 18, 17], [15, 16, 16, 14, 1, 1, 30], [31, 4, 4, 4, 4, 4, 4],
  [17, 17, 17, 17, 17, 17, 14], [17, 17, 17, 17, 17, 10, 4], [17, 17, 17, 21, 21, 27, 17], [17, 17, 10, 4, 10, 17, 17],
  [17, 17, 10, 4, 4, 4, 4], [31, 1, 2, 4, 8, 16, 31],
];
const BLANK_GLYPH = [0, 0, 0, 0, 0, 0, 0];

// How far a flapping letter or digit is from the one it lands on, step after step. Fixed, so every
// flap shows the same glyphs.
const LETTER_SHIFTS = [7, 19, 3, 14, 23, 10, 5, 17, 12, 21];
const DIGIT_SHIFTS = [3, 7, 1, 8, 5, 9, 2, 6];

function easeInOut(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** How far the loop's finger has pulled the stub down at `t` seconds. */
function scriptPull(t: number) {
  'worklet';
  if (t < TEAR_START || t >= RELEASE_AT) return 0;
  return PULL_MAX * easeInOut(Math.min(1, (t - TEAR_START) / PULL_TIME));
}

/** How visible the unlit dots are `elapsed` seconds into a flap. */
function gridGlow(elapsed: number) {
  'worklet';
  const rise = Math.min(1, Math.max(0, elapsed / GRID_IN));
  const fall = Math.min(1, Math.max(0, (elapsed - FLAP_STEPS * FLAP_STEP) / GRID_OUT));
  return rise * (1 - fall);
}

/** 0 for A … 25 for Z, -1 for anything else. */
function letterIndex(code: number) {
  'worklet';
  return code >= 65 && code <= 90 ? code - 65 : -1;
}

/** The glyph the board shows for a letter, the `position`-th on the ticket, `step` steps into a flap. */
function flapGlyph(code: number, position: number, step: number) {
  'worklet';
  const final = letterIndex(code);
  if (final < 0) return BLANK_GLYPH;
  const local = step - position * FLAP_STAGGER;
  if (local < 0 || local >= FLAPS) return GLYPHS[final];
  return GLYPHS[(final + LETTER_SHIFTS[(position + local) % LETTER_SHIFTS.length]) % 26];
}

/** A field's value `step` steps into a flap: its digits and letters show others until the field lands. */
function flapText(value: string, field: number, step: number) {
  const local = step - (field + 1) * FLAP_STAGGER;
  if (local < 0 || local >= FIELD_FLAPS) return value;
  let out = '';
  for (let n = 0; n < value.length; n++) {
    const code = value.charCodeAt(n);
    const letter = LETTER_SHIFTS[(n + local) % LETTER_SHIFTS.length];
    if (code >= 48 && code <= 57) out += String.fromCharCode(48 + ((code - 48 + DIGIT_SHIFTS[(n + local) % DIGIT_SHIFTS.length]) % 10));
    else if (code >= 65 && code <= 90) out += String.fromCharCode(65 + ((code - 65 + letter) % 26));
    else if (code >= 97 && code <= 122) out += String.fromCharCode(97 + ((code - 97 + letter) % 26));
    else out += value[n];
  }
  return out;
}

/**
 * The dots of one airport code on the board, lit or unlit. `first` is the position of its first
 * letter among all the letters on the ticket, which decides when each one starts to flap.
 */
function addCode(path: SkPath, code: string, first: number, top: number, step: number, lit: boolean) {
  'worklet';
  const pitch = CELL * 5 + LETTER_GAP;
  const left = (WIDTH - (pitch * code.length - LETTER_GAP)) / 2;
  for (let n = 0; n < code.length; n++) {
    const rows = flapGlyph(code.charCodeAt(n), first + n, step);
    for (let row = 0; row < 7; row++) {
      for (let column = 0; column < 5; column++) {
        if (((rows[row] & (16 >> column)) !== 0) !== lit) continue;
        const x = left + pitch * n + CELL * column + (CELL - DOT) / 2;
        const y = top + CELL * row + (CELL - DOT) / 2;
        path.addRRect({ rect: { x, y, width: DOT, height: DOT }, rx: 0.9, ry: 0.9 });
      }
    }
  }
}

/**
 * The ticket's outline cut in two along the perforation: a rounded rectangle with a half circle
 * taken out of each side and a row of holes between them. The stub is in its own coordinates.
 */
function ticketPieces() {
  const outline = Skia.Path.Make();
  outline.addRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, WIDTH, HEIGHT), RADIUS, RADIUS));
  const holes = Skia.Path.Make();
  holes.addCircle(0, TEAR_Y, NOTCH);
  holes.addCircle(WIDTH, TEAR_Y, NOTCH);
  const pitch = (WIDTH - HOLE_INSET * 2) / (HOLES - 1);
  for (let n = 0; n < HOLES; n++) holes.addCircle(HOLE_INSET + pitch * n, TEAR_Y, HOLE);
  const cut = (y: number, height: number) => {
    const half = Skia.Path.Make();
    half.addRect(Skia.XYWHRect(0, y, WIDTH, height));
    const piece = Skia.Path.MakeFromOp(outline, half, PathOp.Intersect) ?? half;
    return Skia.Path.MakeFromOp(piece, holes, PathOp.Difference) ?? piece;
  };
  const stub = cut(TEAR_Y, HEIGHT - TEAR_Y);
  stub.offset(0, -TEAR_Y);
  return { body: cut(0, TEAR_Y), stub };
}

/** What is printed on the stub in ink: the route in small dots with its arrow, and the barcode. */
function stubPrint(origin: string, destination: string) {
  const dots = Skia.Path.Make();
  const smallCode = (code: string, x: number) => {
    let left = x;
    for (let n = 0; n < code.length; n++) {
      const index = letterIndex(code.charCodeAt(n));
      const rows = index < 0 ? BLANK_GLYPH : GLYPHS[index];
      for (let row = 0; row < 7; row++) {
        for (let column = 0; column < 5; column++) {
          if ((rows[row] & (16 >> column)) === 0) continue;
          dots.addRect(Skia.XYWHRect(left + SMALL_CELL * column, 22 + SMALL_CELL * row, SMALL_DOT, SMALL_DOT));
        }
      }
      left += SMALL_CELL * 5 + 2.6;
    }
    return left - 2.6;
  };
  const x = smallCode(origin, STUB_PAD);
  smallCode(destination, x + 26);
  const arrow = `M${x + 7} 27.25L${x + 19} 27.25M${x + 15.5} 24L${x + 19} 27.25L${x + 15.5} 30.5`;

  const unit = BARCODE_WIDTH / BARCODE.reduce((sum, width) => sum + width, 0);
  let barX = WIDTH - STUB_PAD - BARCODE_WIDTH;
  BARCODE.forEach((width, n) => {
    if (n % 2 === 0) dots.addRect(Skia.XYWHRect(barX, 22, width * unit, 40));
    barX += width * unit;
  });
  return { dots, arrow };
}

function AuroraLight({ light, turn }: { light: (typeof AURORA)[number]; turn: SharedValue<number> }) {
  const drift = useDerivedValue(() => [
    { translateX: light.driftX * Math.sin(turn.get() + light.phase) },
    { translateY: light.driftY * Math.cos(turn.get() + light.phase) },
  ]);
  return (
    <Group transform={drift}>
      <Circle cx={light.x} cy={light.y} r={light.radius}>
        <RadialGradient c={vec(light.x, light.y)} r={light.radius} colors={[light.color, `${light.color}80`, `${light.color}00`]} positions={[0, 0.5, 1]} />
      </Circle>
    </Group>
  );
}

/**
 * A tall boarding pass: a black top that runs into a drifting aurora, two airport codes drawn in
 * square dots like a departure board, and a pale stub under a perforated line. The codes and the
 * five fields flap through other glyphs before they land, on appear and on a tap; dragging the stub
 * down tears it off along the perforation, and letting go snaps it back.
 */
export function DepartureBoardTicket({
  autoPlay = false,
  fields = [
    { label: 'GATE', value: '42' },
    { label: 'SEAT', value: '11A' },
    { label: 'BOARDING', value: '22:10' },
    { label: 'DEPARTURE', value: '22:55' },
    { label: 'DATE', value: '4 Sep 26' },
  ],
  origin = { code: 'HKG', name: 'Hong Kong International Airport', city: 'HONG KONG' },
  destination = { code: 'LHR', name: 'London Heathrow Airport', city: 'LONDON, UNITED KINGDOM' },
  airline = 'NORTHWIND AIR',
  flight = 'NW 29',
  cabin = 'ECONOMY',
  date = '4 Sep 2026',
  onPress,
  onTear,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const originCode = origin.code.toUpperCase();
  const destinationCode = destination.code.toUpperCase();

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const touching = useSharedValue(false);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  const flapAt = useSharedValue(-10);
  // How far the stub is pulled down, in points, by a finger or by the loop.
  const pull = useSharedValue(0);
  const detached = useSharedValue(false);
  // The stub's drop and angle chase their targets on a spring.
  const drop = useSharedValue(0);
  const dropSpeed = useSharedValue(0);
  const angle = useSharedValue(0);
  const spin = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    // Without the loop the board flaps once as the ticket appears.
    if (!auto && !reducedMotion && time.get() === 0) flapAt.set(APPEAR_FLAP_AT);
  }, [auto, reducedMotion, clock, flapAt, hold, looping, time]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const now = time.get() + dt;
    if (touching.get()) {
      hold.set(USER_HOLD);
    } else if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // Time is up: the loop starts again from rest.
      if (hold.get() <= 0 && looping.get()) clock.set(0);
    } else if (looping.get()) {
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      if (clock.get() < FLAP_AT && t >= FLAP_AT) flapAt.set(now);
      pull.set(scriptPull(t));
      detached.set(pull.get() >= TEAR_AT);
      clock.set(t);
    }

    // Attached, the stub swings on its top left corner; torn off, it hangs below the ticket.
    const dropTarget = detached.get() ? DROP + (pull.get() - TEAR_AT) * FOLLOW : 0;
    const angleTarget = detached.get() ? TILT : SWING * easeInOut(Math.min(1, pull.get() / TEAR_AT));
    if (reducedMotion) {
      drop.set(dropTarget);
      angle.set(angleTarget);
    } else {
      const v = dropSpeed.get() + (STIFFNESS * (dropTarget - drop.get()) - DAMPING * dropSpeed.get()) * dt;
      dropSpeed.set(v);
      drop.set(drop.get() + v * dt);
      const w = spin.get() + (STIFFNESS * (angleTarget - angle.get()) - DAMPING * spin.get()) * dt;
      spin.set(w);
      angle.set(angle.get() + w * dt);
    }
    time.set(now);
  });

  // Steps into the flap, held between -1 and FLAP_STEPS, where every glyph is the final one. Under
  // reduced motion the board never flaps.
  const step = useDerivedValue(() => {
    if (reducedMotion) return FLAP_STEPS;
    return Math.min(FLAP_STEPS, Math.max(-1, Math.floor((time.get() - flapAt.get()) / FLAP_STEP)));
  });
  const grid = useDerivedValue(() => (reducedMotion ? 0 : 0.1 * gridGlow(time.get() - flapAt.get())));
  const turn = useDerivedValue(() => (reducedMotion ? 0 : (time.get() / LOOP) * Math.PI * 2));
  const lift = useDerivedValue(() => Math.min(1, Math.max(0, drop.get() / DROP)));

  const [shownStep, setShownStep] = useState(FLAP_STEPS);
  useAnimatedReaction(
    () => step.get(),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShownStep, now);
    },
  );

  const litDots = useDerivedValue(() => {
    const path = Skia.Path.Make();
    addCode(path, originCode, 0, ORIGIN_TOP, step.get(), true);
    addCode(path, destinationCode, originCode.length, DESTINATION_TOP, step.get(), true);
    return path;
  });
  const darkDots = useDerivedValue(() => {
    const path = Skia.Path.Make();
    addCode(path, originCode, 0, ORIGIN_TOP, step.get(), false);
    addCode(path, destinationCode, originCode.length, DESTINATION_TOP, step.get(), false);
    return path;
  });

  const pieces = useMemo(() => ticketPieces(), []);
  const print = useMemo(() => stubPrint(originCode, destinationCode), [originCode, destinationCode]);

  const stubPose = useAnimatedStyle(() => ({
    transform: [{ translateY: drop.get() }, { rotate: `${angle.get()}deg` }],
  }));
  const stubShadow = useDerivedValue(() => [{ translateX: BLEED }, { translateY: BLEED + 14 + 8 * lift.get() }]);
  const stubShade = useDerivedValue(() => 0.2 + 0.14 * lift.get());

  const impact = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  /** A touch takes the stub out of the loop's hands. */
  const takeOver = () => {
    hold.set(USER_HOLD);
    pull.set(0);
    detached.set(false);
  };

  const tap = () => {
    takeOver();
    flapAt.set(time.get());
    impact(Haptics.ImpactFeedbackStyle.Light);
    onPress?.();
  };

  // --- Dragging the stub down swings it, then tears it off. ---
  const drag = useRef({ y: 0, torn: false });

  const onGrant = (e: GestureResponderEvent) => {
    drag.current = { y: e.nativeEvent.pageY, torn: false };
    takeOver();
    touching.set(true);
  };

  const onMove = (e: GestureResponderEvent) => {
    const d = drag.current;
    const pulled = Math.max(0, e.nativeEvent.pageY - d.y);
    pull.set(pulled);
    if (!d.torn && pulled >= TEAR_AT) {
      d.torn = true;
      detached.set(true);
      impact(Haptics.ImpactFeedbackStyle.Medium);
      onTear?.();
    }
  };

  const onEnd = () => {
    if (drag.current.torn) impact(Haptics.ImpactFeedbackStyle.Light);
    touching.set(false);
    pull.set(0);
    detached.set(false);
  };

  const weights = fields.map((field) => 4 + Math.max(field.label.length, field.value.length));
  const details = fields.map((field) => `${field.label} ${field.value}`).join(', ');

  return (
    <View
      style={styles.ticket}
      accessible
      accessibilityLabel={`${airline} ${flight}, ${origin.name} to ${destination.name}, ${cabin}, ${date}. ${details}`}
    >
      <Canvas pointerEvents="none" style={styles.shadow}>
        <Group transform={[{ translateX: BLEED }, { translateY: BLEED + 14 }]}>
          <Path path={pieces.body} color="rgba(24,32,72,0.2)">
            <BlurMask blur={18} style="normal" />
          </Path>
        </Group>
      </Canvas>
      <Animated.View pointerEvents="none" style={[styles.stub, stubPose]}>
        <Canvas style={styles.stubShadow}>
          <Group transform={stubShadow}>
            <Path path={pieces.stub} color="rgb(24,32,72)" opacity={stubShade}>
              <BlurMask blur={18} style="normal" />
            </Path>
          </Group>
        </Canvas>
      </Animated.View>

      <Pressable style={styles.top} onPress={tap}>
        <Canvas style={styles.top}>
          <Group clip={pieces.body}>
            <Rect x={0} y={0} width={WIDTH} height={TEAR_Y} color="#000000" />
            {AURORA.map((light) => (
              <AuroraLight key={light.color} light={light} turn={turn} />
            ))}
            {/* The colours wash out into the stub's pale blue just above the perforation. */}
            <Rect x={0} y={PALE_FROM} width={WIDTH} height={TEAR_Y - PALE_FROM}>
              <LinearGradient start={vec(0, PALE_FROM)} end={vec(0, TEAR_Y)} colors={[`${PALE}00`, `${PALE}4d`, PALE]} positions={[0, 0.5, 1]} />
            </Rect>
            {/* The unlit dots of the board show faintly while it flaps. */}
            <Path path={darkDots} color="#ffffff" opacity={grid} />
            <Path path={litDots} color="#ffffff" />
          </Group>
        </Canvas>

        <View style={styles.fields}>
          {fields.map((field, index) => (
            <View key={index} style={[styles.field, { flex: weights[index] }]}>
              {index > 0 ? <View style={styles.divider} /> : null}
              <Text style={styles.fieldLabel} numberOfLines={1}>
                {field.label}
              </Text>
              <Text style={styles.fieldValue} numberOfLines={1}>
                {flapText(field.value, index, shownStep)}
              </Text>
            </View>
          ))}
        </View>

        {[
          { airport: origin, top: ORIGIN_TOP },
          { airport: destination, top: DESTINATION_TOP },
        ].map(({ airport, top }) => (
          <View key={top} style={[styles.airport, { top: top + CELL * 7 + 24 }]}>
            <Text style={styles.airportName} numberOfLines={1}>
              {airport.name}
            </Text>
            <Text style={styles.airportCity} numberOfLines={1}>
              {airport.city}
            </Text>
          </View>
        ))}

        <View style={styles.carrier}>
          <Text style={styles.airline} numberOfLines={1}>
            {airline}
          </Text>
          <Text style={styles.flight} numberOfLines={1}>
            {flight}
          </Text>
        </View>
        <View style={styles.cabinChip}>
          <Text style={styles.cabinChipLabel} numberOfLines={1}>
            {cabin}
          </Text>
        </View>
      </Pressable>

      <Animated.View
        style={[styles.stub, stubPose]}
        onStartShouldSetResponder={() => true}
        onResponderTerminationRequest={() => false}
        onResponderGrant={onGrant}
        onResponderMove={onMove}
        onResponderRelease={onEnd}
        onResponderTerminate={onEnd}
      >
        <Canvas style={styles.stubCanvas}>
          <Group clip={pieces.stub}>
            <Rect x={0} y={0} width={WIDTH} height={HEIGHT - TEAR_Y}>
              <LinearGradient start={vec(0, 0)} end={vec(0, HEIGHT - TEAR_Y)} colors={[PALE, '#a9c8f7']} />
            </Rect>
            {/* A last trace of the aurora in the bottom left corner. */}
            <Circle cx={0} cy={HEIGHT - TEAR_Y} r={110}>
              <RadialGradient c={vec(0, HEIGHT - TEAR_Y)} r={110} colors={['#4a5cc480', '#4a5cc400']} />
            </Circle>
            <Path path={print.dots} color={STUB_INK} />
            <Path path={print.arrow} style="stroke" strokeWidth={1.1} color={STUB_INK} />
          </Group>
        </Canvas>
        <View pointerEvents="none" style={styles.stubText}>
          <Text style={styles.stubDate} numberOfLines={1}>
            {date}
          </Text>
          <Text style={styles.stubCabin} numberOfLines={1}>
            {cabin}
          </Text>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  ticket: {
    width: WIDTH,
    height: HEIGHT,
  },
  shadow: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: WIDTH + BLEED * 2,
    height: TEAR_Y + BLEED * 2,
  },
  top: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: TEAR_Y,
  },
  // The stub turns on its top left corner.
  stub: {
    position: 'absolute',
    left: 0,
    top: TEAR_Y,
    width: WIDTH,
    height: HEIGHT - TEAR_Y,
    transformOrigin: 'left top',
  },
  stubShadow: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: WIDTH + BLEED * 2,
    height: HEIGHT - TEAR_Y + BLEED * 2,
  },
  stubCanvas: {
    width: WIDTH,
    height: HEIGHT - TEAR_Y,
  },
  fields: {
    position: 'absolute',
    left: FIELD_INSET,
    top: 14,
    width: WIDTH - FIELD_INSET * 2,
    height: 34,
    flexDirection: 'row',
  },
  field: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  divider: {
    position: 'absolute',
    left: 0,
    top: 4,
    width: 1,
    height: 26,
    backgroundColor: 'rgba(255,255,255,0.28)',
  },
  fieldLabel: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 6.5,
    lineHeight: 8,
    fontWeight: '600',
    letterSpacing: 0.9,
  },
  fieldValue: {
    color: '#ffffff',
    fontSize: 12.5,
    lineHeight: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  airport: {
    position: 'absolute',
    left: PAD,
    width: WIDTH - PAD * 2,
    alignItems: 'center',
    gap: 7,
  },
  airportName: {
    color: '#ffffff',
    fontSize: 11.5,
    lineHeight: 14,
    fontWeight: '600',
  },
  airportCity: {
    color: 'rgba(255,255,255,0.45)',
    fontSize: 7.5,
    lineHeight: 9,
    fontWeight: '500',
    letterSpacing: 1.2,
  },
  carrier: {
    position: 'absolute',
    left: PAD,
    top: 427,
    gap: 5,
  },
  airline: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: 9.5,
    lineHeight: 11.5,
    fontWeight: '600',
    letterSpacing: 2.2,
  },
  flight: {
    color: '#ffffff',
    fontSize: 12.5,
    lineHeight: 15,
    fontWeight: '700',
  },
  cabinChip: {
    position: 'absolute',
    right: PAD,
    top: 427,
    height: 20,
    paddingHorizontal: 10,
    borderRadius: 5,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.24)',
    justifyContent: 'center',
  },
  cabinChipLabel: {
    color: '#ffffff',
    fontSize: 7.5,
    lineHeight: 9,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  stubText: {
    position: 'absolute',
    left: STUB_PAD,
    top: 43,
    gap: 8,
  },
  stubDate: {
    color: STUB_MUTE,
    fontSize: 12,
    lineHeight: 14.5,
    fontWeight: '700',
  },
  stubCabin: {
    color: STUB_MUTE,
    fontSize: 6.5,
    lineHeight: 8,
    fontWeight: '600',
    letterSpacing: 1.6,
  },
});
