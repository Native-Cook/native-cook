import { BlurMask, Canvas, Path as SkiaPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Circle, Ellipse, Path, Polygon, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** A passport stamp: an outline with three short lines of text in it. */
export type TicketStamp = {
  /** `ring` is two rings, with the first line running along the top of the inner one. */
  kind: 'ring' | 'plate' | 'oval' | 'triangle';
  /** A small line, the date in large type, and another small line. */
  lines: [string, string, string];
  ink: string;
  /** Centre of the stamp on its ticket, and its turn in degrees. */
  x: number;
  y: number;
  angle: number;
};

export type TripTicket = {
  color: string;
  date: string;
  /** One label strip per line. */
  title: string[];
  place: string;
  stamp: TicketStamp;
};

type Props = {
  /** Four tickets, the top of the pile first. */
  tickets?: TripTicket[];
  /** Shuffles through the pile, drops it, deals it again and starts over, every 8.6 s. */
  autoPlay?: boolean;
  labelColor?: string;
  labelTextColor?: string;
  inkColor?: string;
  /** Called with the index of the ticket a tap brings to the top. */
  onShuffle?: (index: number) => void;
};

const WIDTH = 350;
const HEIGHT = 460;

const TICKET_WIDTH = 196;
const TICKET_HEIGHT = 280;
const TICKET_RADIUS = 18;
// Radius of the half circle cut out of the bottom edge.
const NOTCH = 20;
// Where the tickets lie, the top of the pile first: offset from the middle, and turn in degrees.
const SLOTS = [
  [34, 66, -9],
  [-44, 24, 8],
  [40, -34, 16],
  [-40, -60, -15],
];
// Two shadows cross-faded: a tight one on the pile, a wide one while the ticket is lifted.
const SHADOW_OPACITY = 0.16;
const SHADOW_RADIUS = 9;
const SHADOW_Y = 6;
const LIFT_SHADOW_OPACITY = 0.2;
const LIFT_SHADOW_RADIUS = 18;
const LIFT_SHADOW_Y = 16;
// Each ticket's canvas overflows it by this much on every side to fit the shadows.
const BLEED = 60;

const LEFT = 20;
const CHIP_TOP = 24;
const CHIP_HEIGHT = 18;
const CHIP_PAD = 7;
const CHIP_RADIUS = 6;
const TITLE_TOP = 50;
const STRIP_LEFT = 16;
const STRIP_HEIGHT = 30;
const STRIP_STEP = 27;
const STRIP_PAD = 9;
const STRIP_RADIUS = 7;
const STRIP_TILT = -2;
const PLACE_GAP = 12;

const STAMP_BOX = 100;
const STAMP_STROKE = 2.2;
const STAMP_HAIRLINE = 0.9;
const STAMP_DASH = [2.2, 2.2];
// The ring's first line: the radius it runs along, and the angle from one letter to the next.
const ARC_RADIUS = 28.5;
const ARC_STEP = 14;
const TRIANGLE = '50,6 96,86 4,86';
const TRIANGLE_INNER = '50,20 84,79 16,79';
// Where a kind of stamp puts its three lines, and how large it sets them.
const STAMP_LOOKS = {
  ring: { topY: 0, topSize: 8, topTracking: 0, mainY: 52, mainSize: 14, bottomY: 66, bottomSize: 7.5, bottomTracking: 1 },
  plate: { topY: 33, topSize: 7, topTracking: 1.2, mainY: 49.5, mainSize: 11.5, bottomY: 66, bottomSize: 6.5, bottomTracking: 0.8 },
  oval: { topY: 36, topSize: 6.5, topTracking: 1.2, mainY: 50, mainSize: 12.5, bottomY: 64, bottomSize: 6.5, bottomTracking: 0.8 },
  triangle: { topY: 47, topSize: 7, topTracking: 0.8, mainY: 61, mainSize: 11.5, bottomY: 73, bottomSize: 6, bottomTracking: 0.6 },
};

// Tickets settle on a spring with a small overshoot.
const MASS = 1;
const STIFFNESS = 210;
const DAMPING = 19;

// A shuffle: the top ticket goes out to the side for OUT_TIME, then springs back in underneath.
const OUT_TIME = 0.34;
const SIDE_X = 330;
const SIDE_Y = -26;
const SIDE_TURN = 16;
const LIFT_SCALE = 0.05;
// The other tickets wait this long before they move up.
const SHIFT_DELAY = 0.1;
// Under reduced motion the shuffled ticket fades in at the back instead.
const FADE_TIME = 0.25;

// The drop: one ticket after another falls out of the bottom, speeding up.
const DROP_STAGGER = 0.07;
const DROP_TIME = 0.5;
const DROP_Y = 760;
const DROP_DRIFT = 30;
const DROP_TURN = 24;

// The deal: a ticket every DEAL_GAP flies in from below, and is stamped STAMP_AFTER its start.
const DEAL_GAP = 0.65;
const DEAL_Y = 640;
const DEAL_DRIFT = 46;
const DEAL_TURN = 28;
const DEAL_SCALE = 0.08;
const STAMP_AFTER = 0.44;
// Seconds a whole deal takes, the last stamp included.
const DEAL_TIME = 2.9;

// The stamp comes down from STAMP_SCALE in SLAM seconds while its ink appears, and shakes the ticket.
const SLAM = 0.14;
const STAMP_SCALE = 1.6;
const INK_IN = 0.07;
const INK_FRESH = 0.96;
const INK_DRY = 0.8;
const INK_DRY_RATE = 8;
const SHAKE = 1.8;
const SHAKE_DECAY = 16;
const SHAKE_RATE = 46;
const DIP = 0.02;
const DIP_DECAY = 20;

// autoPlay loop (seconds): four shuffles bring the pile back to its order, it drops, and it is dealt again.
const FIRST_AT = 0.8;
const SHUFFLE_GAP = 0.8;
const DROP_AT = 4.2;
const DEAL_AT = 5;
const LOOP = 8.6;
// After a tap the loop waits this long once the shuffle is over.
const USER_HOLD = 2;
// Without autoPlay the pile is dealt this long after it appears.
const ENTRANCE_AT = 0.2;

const TICKETS: TripTicket[] = [
  {
    color: '#ffb877',
    date: 'Jul 11 – Sep 6',
    title: ['Surfing and', 'beaches'],
    place: 'Denpasar, Kuta and Umalas',
    stamp: { kind: 'triangle', lines: ['ENTRY', '11 JUL', 'GATE 7'], ink: '#1d1b1a', x: 118, y: 196, angle: 14 },
  },
  {
    color: '#eae1da',
    date: 'Jan 7 – Jan 16',
    title: ['Ancient Egypt', 'adventure'],
    place: 'Cairo and Alexandria',
    stamp: { kind: 'ring', lines: ['ARRIVAL', '07 JAN', '2025'], ink: '#34389a', x: 120, y: 198, angle: -14 },
  },
  {
    color: '#dfe4ff',
    date: 'Apr 21 – Jun 7',
    title: ['Road trip', 'with friends'],
    place: 'Lisbon, Porto and Faro',
    stamp: {
      kind: 'plate',
      lines: ['ENTRY PERMIT', '21 APR 2025', 'VISITOR · 90 DAYS'],
      ink: '#a5362c',
      x: 112,
      y: 200,
      angle: -9,
    },
  },
  {
    color: '#ffe500',
    date: 'Mar 28 – Apr 4',
    title: ['Spring in', 'the Alps'],
    place: 'Chamonix and Annecy',
    stamp: { kind: 'oval', lines: ['DEPARTED', '28 MAR', 'TERMINAL 2'], ink: '#1d1b1a', x: 116, y: 198, angle: 11 },
  },
];

// Control-point distance that makes a cubic curve a quarter circle.
const K = 0.5523;

/** A rounded rectangle with a half circle cut out of the middle of its bottom edge, drawn at (x, y). */
function ticketOutline(x: number, y: number) {
  const w = TICKET_WIDTH;
  const h = TICKET_HEIGHT;
  const r = TICKET_RADIUS;
  const n = NOTCH;
  const at = (px: number, py: number) => `${x + px} ${y + py}`;
  return [
    `M${at(r, 0)}L${at(w - r, 0)}`,
    `C${at(w - r + r * K, 0)} ${at(w, r - r * K)} ${at(w, r)}L${at(w, h - r)}`,
    `C${at(w, h - r + r * K)} ${at(w - r + r * K, h)} ${at(w - r, h)}L${at(w / 2 + n, h)}`,
    `C${at(w / 2 + n, h - n * K)} ${at(w / 2 + n * K, h - n)} ${at(w / 2, h - n)}`,
    `C${at(w / 2 - n * K, h - n)} ${at(w / 2 - n, h - n * K)} ${at(w / 2 - n, h)}L${at(r, h)}`,
    `C${at(r - r * K, h)} ${at(0, h - r + r * K)} ${at(0, h - r)}L${at(0, r)}`,
    `C${at(0, r - r * K)} ${at(r - r * K, 0)} ${at(r, 0)}Z`,
  ].join('');
}

const TICKET_OUTLINE = ticketOutline(BLEED, BLEED);
const TICKET_SHADOW = ticketOutline(BLEED, BLEED + SHADOW_Y);
const TICKET_LIFT_SHADOW = ticketOutline(BLEED, BLEED + LIFT_SHADOW_Y);
// A map pin, 8 × 11, with its tip at the bottom.
const PIN = 'M4 0C1.8 0 0 1.8 0 4C0 7 4 11 4 11C4 11 8 7 8 4C8 1.8 6.2 0 4 0Z';

// What a ticket is doing.
const REST = 0;
const SETTLE = 1;
const TUCK = 2;
const DROP = 3;
const DEAL = 4;
const FADE = 5;

/** Where one ticket is going: its place in the pile, how it gets there, and where it set off from. */
type Motion = {
  /** 0 is the top of the pile. */
  slot: number;
  kind: number;
  start: number;
  /** +1 or -1: the side it leaves by, or drifts to. */
  side: number;
  fromX: number;
  fromY: number;
  fromTurn: number;
  fromScale: number;
  /** When its stamp starts coming down. */
  stampAt: number;
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number) {
  'worklet';
  const p = clamp01(t);
  return p * p * (3 - 2 * p);
}

/** A spring let go at 0 and settling at 1, `t` seconds on, with a small overshoot. */
function spring(t: number) {
  'worklet';
  const decay = DAMPING / (2 * MASS);
  const omega = Math.sqrt(STIFFNESS / MASS - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(omega * t) + (decay / omega) * Math.sin(omega * t));
}

function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** Where a ticket is, without the shake of its stamp. */
function poseAt(m: Motion, now: number, count: number) {
  'worklet';
  const home = SLOTS[m.slot];
  const homeZ = count - m.slot;
  const t = now - m.start;
  const pose = { x: home[0], y: home[1], turn: home[2], scale: 1, opacity: 1, lift: 0, z: homeZ };
  if (m.kind === SETTLE) {
    const p = t > 0 ? spring(t) : 0;
    pose.x = mix(m.fromX, home[0], p);
    pose.y = mix(m.fromY, home[1], p);
    pose.turn = mix(m.fromTurn, home[2], p);
    pose.scale = mix(m.fromScale, 1, p);
  } else if (m.kind === TUCK) {
    // Out past the edge of the pile, lifted and turning, then in again underneath.
    const outX = m.fromX + m.side * SIDE_X;
    const outY = m.fromY + SIDE_Y;
    const outTurn = m.fromTurn + m.side * SIDE_TURN;
    const outScale = m.fromScale + LIFT_SCALE;
    if (t < OUT_TIME) {
      const p = smooth(t / OUT_TIME);
      pose.x = mix(m.fromX, outX, p);
      pose.y = mix(m.fromY, outY, p);
      pose.turn = mix(m.fromTurn, outTurn, p);
      pose.scale = mix(m.fromScale, outScale, p);
      pose.lift = Math.sin(Math.PI * p);
      pose.z = count + 1;
    } else {
      const p = spring(t - OUT_TIME);
      pose.x = mix(outX, home[0], p);
      pose.y = mix(outY, home[1], p);
      pose.turn = mix(outTurn, home[2], p);
      pose.scale = mix(outScale, 1, p);
    }
  } else if (m.kind === DROP) {
    const p = clamp01(t / DROP_TIME);
    pose.x = m.fromX + m.side * DROP_DRIFT * p * p;
    pose.y = m.fromY + DROP_Y * p * p;
    pose.turn = m.fromTurn + m.side * DROP_TURN * p * p;
    pose.scale = m.fromScale;
    pose.opacity = 1 - smooth((p - 0.6) / 0.4);
  } else if (m.kind === DEAL) {
    const p = t > 0 ? spring(t) : 0;
    pose.x = mix(home[0] + m.side * DEAL_DRIFT, home[0], p);
    pose.y = mix(home[1] + DEAL_Y, home[1], p);
    pose.turn = mix(home[2] + m.side * DEAL_TURN, home[2], p);
    pose.scale = mix(1 + DEAL_SCALE, 1, p);
    pose.opacity = t > 0 ? 1 : 0;
    pose.lift = t > 0 ? clamp01(1 - p) : 0;
  } else if (m.kind === FADE) {
    pose.opacity = smooth(t / FADE_TIME);
  }
  return pose;
}

/** Where a ticket is, shaken by the blow of its stamp. */
function poseOf(m: Motion, now: number, count: number) {
  'worklet';
  const pose = poseAt(m, now, count);
  const since = now - m.stampAt - SLAM;
  if (since > 0) {
    pose.turn += SHAKE * Math.exp(-SHAKE_DECAY * since) * Math.sin(SHAKE_RATE * since);
    pose.scale *= 1 - DIP * Math.exp(-DIP_DECAY * since);
  }
  return pose;
}

/** The pile at rest, in the order given and already stamped. */
function restingPile(count: number): Motion[] {
  'worklet';
  const pile: Motion[] = [];
  for (let index = 0; index < count; index++) {
    pile.push({
      slot: index,
      kind: REST,
      start: 0,
      side: 1,
      fromX: SLOTS[index][0],
      fromY: SLOTS[index][1],
      fromTurn: SLOTS[index][2],
      fromScale: 1,
      stampAt: -100,
    });
  }
  return pile;
}

/** Every ticket setting off from where it is at `now`. */
function settingOff(pile: Motion[], now: number) {
  'worklet';
  return pile.map((m) => {
    const pose = poseAt(m, now, pile.length);
    return { ...m, fromX: pose.x, fromY: pose.y, fromTurn: pose.turn, fromScale: pose.scale };
  });
}

/** The top ticket leaves by the side and comes back under the pile; the others move up a place. */
function shuffled(pile: Motion[], now: number, side: number, reduced: boolean) {
  'worklet';
  return settingOff(pile, now).map((m) =>
    m.slot === 0
      ? { ...m, slot: pile.length - 1, kind: reduced ? FADE : TUCK, start: now, side }
      : { ...m, slot: m.slot - 1, kind: reduced ? REST : SETTLE, start: now + SHIFT_DELAY },
  );
}

/** The tickets fall out of the bottom, the top one first. */
function dropped(pile: Motion[], now: number) {
  'worklet';
  return settingOff(pile, now).map((m) => ({
    ...m,
    kind: DROP,
    start: now + m.slot * DROP_STAGGER,
    side: m.slot % 2 === 0 ? 1 : -1,
  }));
}

/** The tickets fly in from below, the bottom one first, and each is stamped as it lands. */
function dealt(pile: Motion[], now: number) {
  'worklet';
  return pile.map((m) => {
    const start = now + (pile.length - 1 - m.slot) * DEAL_GAP;
    return { ...m, kind: DEAL, start, side: m.slot % 2 === 0 ? 1 : -1, stampAt: start + STAMP_AFTER };
  });
}

/**
 * The stamp `since` seconds after it starts coming down: it shrinks onto the paper as its ink
 * appears, then the ink dries a little.
 */
function stampLook(since: number) {
  'worklet';
  if (since <= 0) return { scale: STAMP_SCALE, opacity: 0 };
  if (since < SLAM) {
    const p = since / SLAM;
    return { scale: STAMP_SCALE - (STAMP_SCALE - 1) * p * p, opacity: INK_FRESH * clamp01(since / INK_IN) };
  }
  return { scale: 1, opacity: INK_DRY + (INK_FRESH - INK_DRY) * Math.exp(-INK_DRY_RATE * (since - SLAM)) };
}

/** A stamp in a 100-point box: its outline, and its three lines of text. */
function Mark({ stamp }: { stamp: TicketStamp }) {
  const look = STAMP_LOOKS[stamp.kind];
  const letters = Array.from(stamp.lines[0]);
  const line = (text: string, y: number, size: number, weight: '700' | '800', tracking: number) => (
    <Text
      style={[styles.stampLine, { top: y - size, lineHeight: size * 2, fontSize: size, fontWeight: weight, letterSpacing: tracking, color: stamp.ink }]}
      numberOfLines={1}
    >
      {text}
    </Text>
  );
  return (
    <View style={styles.stampBox}>
      <Svg width={STAMP_BOX} height={STAMP_BOX} style={StyleSheet.absoluteFill}>
        {stamp.kind === 'ring' && (
          <>
            <Circle cx={50} cy={50} r={42} fill="none" stroke={stamp.ink} strokeWidth={STAMP_STROKE} />
            <Circle cx={50} cy={50} r={36} fill="none" stroke={stamp.ink} strokeWidth={STAMP_HAIRLINE} strokeDasharray={STAMP_DASH} />
          </>
        )}
        {stamp.kind === 'plate' && (
          <>
            <Rect x={2} y={20} width={96} height={60} rx={8} fill="none" stroke={stamp.ink} strokeWidth={STAMP_STROKE} />
            <Rect x={7} y={25} width={86} height={50} rx={4} fill="none" stroke={stamp.ink} strokeWidth={STAMP_HAIRLINE} />
          </>
        )}
        {stamp.kind === 'oval' && (
          <>
            <Ellipse cx={50} cy={50} rx={48} ry={31} fill="none" stroke={stamp.ink} strokeWidth={STAMP_STROKE} />
            <Ellipse cx={50} cy={50} rx={42.5} ry={25.5} fill="none" stroke={stamp.ink} strokeWidth={STAMP_HAIRLINE} strokeDasharray={STAMP_DASH} />
          </>
        )}
        {stamp.kind === 'triangle' && (
          <>
            <Polygon points={TRIANGLE} fill="none" stroke={stamp.ink} strokeWidth={STAMP_STROKE} strokeLinejoin="round" />
            <Polygon points={TRIANGLE_INNER} fill="none" stroke={stamp.ink} strokeWidth={STAMP_HAIRLINE} strokeLinejoin="round" />
          </>
        )}
      </Svg>
      {stamp.kind === 'ring'
        ? // The first line follows the inner ring, one letter at a time.
          letters.map((letter, index) => {
            const angle = (index - (letters.length - 1) / 2) * ARC_STEP;
            const radians = (angle * Math.PI) / 180;
            return (
              <Text
                key={index}
                style={[
                  styles.stampLetter,
                  {
                    left: STAMP_BOX / 2 + ARC_RADIUS * Math.sin(radians) - 8,
                    top: STAMP_BOX / 2 - ARC_RADIUS * Math.cos(radians) - 8,
                    fontSize: look.topSize,
                    color: stamp.ink,
                    transform: [{ rotate: `${angle}deg` }],
                  },
                ]}
              >
                {letter}
              </Text>
            );
          })
        : line(stamp.lines[0], look.topY, look.topSize, '800', look.topTracking)}
      {line(stamp.lines[1], look.mainY, look.mainSize, '800', 0.4)}
      {line(stamp.lines[2], look.bottomY, look.bottomSize, '700', look.bottomTracking)}
    </View>
  );
}

type TicketProps = {
  ticket: TripTicket;
  index: number;
  count: number;
  pile: SharedValue<Motion[]>;
  time: SharedValue<number>;
  labelColor: string;
  labelTextColor: string;
  inkColor: string;
};

/** One ticket: dates, the trip's name on label strips, the place and the stamp. */
function Ticket({ ticket, index, count, pile, time, labelColor, labelTextColor, inkColor }: TicketProps) {
  const pose = useDerivedValue(() => poseOf(pile.get()[index], time.get(), count));
  const ticketStyle = useAnimatedStyle(() => {
    const now = pose.get();
    return {
      opacity: now.opacity,
      zIndex: now.z,
      transform: [{ translateX: now.x }, { translateY: now.y }, { rotate: `${now.turn}deg` }, { scale: now.scale }],
    };
  });
  const onPile = useDerivedValue(() => 1 - pose.get().lift);
  const lifted = useDerivedValue(() => pose.get().lift);
  const stampStyle = useAnimatedStyle(() => {
    const look = stampLook(time.get() - pile.get()[index].stampAt);
    return { opacity: look.opacity, transform: [{ rotate: `${ticket.stamp.angle}deg` }, { scale: look.scale }] };
  });

  return (
    <Animated.View style={[styles.ticket, ticketStyle]}>
      <Canvas style={styles.ticketCanvas}>
        <SkiaPath path={TICKET_SHADOW} color={`rgba(0,0,0,${SHADOW_OPACITY})`} opacity={onPile}>
          <BlurMask blur={SHADOW_RADIUS} style="normal" />
        </SkiaPath>
        <SkiaPath path={TICKET_LIFT_SHADOW} color={`rgba(0,0,0,${LIFT_SHADOW_OPACITY})`} opacity={lifted}>
          <BlurMask blur={LIFT_SHADOW_RADIUS} style="normal" />
        </SkiaPath>
        <SkiaPath path={TICKET_OUTLINE} color={ticket.color} />
      </Canvas>

      <View style={styles.chip}>
        <Text style={[styles.chipText, { color: inkColor }]} numberOfLines={1}>
          {ticket.date}
        </Text>
      </View>

      {ticket.title.map((text, line) => (
        <View
          key={line}
          style={[
            styles.strip,
            {
              top: TITLE_TOP + line * STRIP_STEP,
              backgroundColor: labelColor,
              // The strips are stuck on by hand, each a little askew.
              transform: [{ rotate: `${line % 2 === 0 ? STRIP_TILT : -STRIP_TILT * 0.75}deg` }],
            },
          ]}
        >
          <Text style={[styles.stripText, { color: labelTextColor }]} numberOfLines={1}>
            {text}
          </Text>
        </View>
      ))}

      <View style={[styles.place, { top: TITLE_TOP + ticket.title.length * STRIP_STEP + PLACE_GAP }]}>
        <Svg width={8} height={11} viewBox="0 0 8 11">
          <Path d={PIN} fill={inkColor} />
          <Circle cx={4} cy={4} r={1.5} fill={ticket.color} />
        </Svg>
        <Text style={[styles.placeText, { color: inkColor }]} numberOfLines={1}>
          {ticket.place}
        </Text>
      </View>

      <Animated.View style={[styles.stamp, { left: ticket.stamp.x - STAMP_BOX / 2, top: ticket.stamp.y - STAMP_BOX / 2 }, stampStyle]}>
        <Mark stamp={ticket.stamp} />
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A loose pile of four trip tickets, each a different colour and angle, with the trip's dates,
 * its name on black label strips, a place and a passport stamp. The tickets are dealt from below
 * one by one and stamped as they land; a tap sends the top one out to the side and under the
 * pile while the others move up.
 */
export function StampedTicketPile({
  tickets = TICKETS,
  autoPlay = false,
  labelColor = '#0e0e10',
  labelTextColor = '#ffffff',
  inkColor = '#1c1c1c',
  onShuffle,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = Math.min(tickets.length, SLOTS.length);

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  // The next step of the loop's script.
  const step = useSharedValue(0);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  // Taps are ignored until then: the pile is dropping or being dealt.
  const busyUntil = useSharedValue(0);
  // Shuffles so far: every other one leaves by the other side.
  const shuffles = useSharedValue(0);
  // Where every ticket is, and where it is going.
  const pile = useSharedValue(restingPile(count));
  // Taps on the pile, and how many of them the frame loop has acted on.
  const taps = useSharedValue(0);
  const handled = useSharedValue(0);
  // The ticket on top of the pile, for the accessibility label.
  const [front, setFront] = useState(0);
  useAnimatedReaction(
    () => pile.get().findIndex((m) => m.slot === 0),
    (now, old) => {
      if (now !== old) scheduleOnRN(setFront, now);
    },
  );

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    step.set(0);
    hold.set(0);
    shuffles.set(0);
    // Without the loop the pile makes its entrance once: dealt and stamped.
    const entrance = !auto && !reducedMotion;
    const now = time.get();
    pile.set(entrance ? dealt(restingPile(count), now + ENTRANCE_AT) : restingPile(count));
    busyUntil.set(entrance ? now + ENTRANCE_AT + DEAL_TIME : 0);
  }, [auto, count, reducedMotion, busyUntil, clock, hold, looping, pile, shuffles, step, time]);

  const shuffledByHand = (index: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onShuffle?.(index);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const now = time.get() + dt;
    time.set(now);
    if (taps.get() !== handled.get()) {
      handled.set(taps.get());
      // A tap on the pile: the top ticket goes to the back, unless the pile is being dealt.
      if (count > 1 && now >= busyUntil.get()) {
        const next = shuffled(pile.get(), now, shuffles.get() % 2 === 0 ? 1 : -1, reducedMotion);
        pile.set(next);
        shuffles.set(shuffles.get() + 1);
        hold.set(SHUFFLE_GAP + USER_HOLD);
        scheduleOnRN(
          shuffledByHand,
          next.findIndex((m) => m.slot === 0),
        );
      }
    }
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // Time is up: the loop starts over from its rest, with the pile as it is.
      if (hold.get() <= 0) {
        clock.set(0);
        step.set(0);
      }
    } else if (looping.get()) {
      let t = clock.get() + dt;
      if (t >= LOOP) {
        t -= LOOP;
        step.set(0);
      }
      clock.set(t);
      // The script: one shuffle per ticket, then the pile drops and is dealt again.
      while (step.get() < count + 2) {
        const s = step.get();
        const at = s < count ? FIRST_AT + s * SHUFFLE_GAP : s === count ? DROP_AT : DEAL_AT;
        if (t < at) break;
        const then = now - (t - at);
        if (s < count) {
          pile.set(shuffled(pile.get(), then, shuffles.get() % 2 === 0 ? 1 : -1, false));
          shuffles.set(shuffles.get() + 1);
        } else if (s === count) {
          pile.set(dropped(pile.get(), then));
          busyUntil.set(then + DEAL_AT - DROP_AT + DEAL_TIME);
        } else {
          pile.set(dealt(pile.get(), then));
          busyUntil.set(Math.max(busyUntil.get(), then + DEAL_TIME));
        }
        step.set(s + 1);
      }
    }
  });

  return (
    <Pressable
      style={styles.pile}
      accessibilityRole="button"
      accessibilityLabel={tickets[front]?.title.join(' ')}
      accessibilityHint="Shows the next ticket"
      onPress={() => taps.set(taps.get() + 1)}
    >
      {tickets.slice(0, count).map((ticket, index) => (
        <Ticket
          key={index}
          ticket={ticket}
          index={index}
          count={count}
          pile={pile}
          time={time}
          labelColor={labelColor}
          labelTextColor={labelTextColor}
          inkColor={inkColor}
        />
      ))}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pile: {
    width: WIDTH,
    height: HEIGHT,
  },
  ticket: {
    position: 'absolute',
    left: (WIDTH - TICKET_WIDTH) / 2,
    top: (HEIGHT - TICKET_HEIGHT) / 2,
    width: TICKET_WIDTH,
    height: TICKET_HEIGHT,
  },
  ticketCanvas: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: TICKET_WIDTH + BLEED * 2,
    height: TICKET_HEIGHT + BLEED * 2,
  },
  chip: {
    position: 'absolute',
    left: LEFT,
    top: CHIP_TOP,
    height: CHIP_HEIGHT,
    paddingHorizontal: CHIP_PAD,
    borderRadius: CHIP_RADIUS,
    backgroundColor: 'rgba(0,0,0,0.07)',
    justifyContent: 'center',
  },
  chipText: {
    fontSize: 9.5,
    lineHeight: 13,
    fontWeight: '500',
  },
  strip: {
    position: 'absolute',
    left: STRIP_LEFT,
    height: STRIP_HEIGHT,
    paddingHorizontal: STRIP_PAD,
    borderRadius: STRIP_RADIUS,
    justifyContent: 'center',
    transformOrigin: 'left center',
  },
  stripText: {
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  place: {
    position: 'absolute',
    left: LEFT,
    height: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  placeText: {
    fontSize: 9.5,
    lineHeight: 12,
  },
  stamp: {
    position: 'absolute',
    width: STAMP_BOX,
    height: STAMP_BOX,
  },
  stampBox: {
    width: STAMP_BOX,
    height: STAMP_BOX,
  },
  stampLine: {
    position: 'absolute',
    left: -STAMP_BOX / 2,
    width: STAMP_BOX * 2,
    textAlign: 'center',
  },
  stampLetter: {
    position: 'absolute',
    width: 16,
    height: 16,
    lineHeight: 16,
    textAlign: 'center',
    fontWeight: '800',
  },
});
