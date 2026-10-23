import { Canvas, Circle, ColorMatrix, FractalNoise, Group, matchFont, Path, RadialGradient, Rect, Shader, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { type EdgeInsets, useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { FOIL_SHADER } from './scratchTicketArt';
import { BRUSH, type Geometry, PAPER, PEN_UP, type ScratchSlot, ScratchTicketCard, type Ticket } from './ScratchTicketCard';
import { addConfetti, addFlakes, piecesPath, stepPieces } from './scratchTicketParticles';

const TICKETS: Ticket[] = [
  { photo: 'photo-1536633125620-8a3245c11ffa', prize: 'A picnic in the sunflowers', detail: 'Hollin Farm · for two' },
  { photo: 'photo-1552160793-cbaf3ebcba72', prize: 'Golden hour photo walk', detail: 'Wren Valley · any Saturday' },
  { photo: 'photo-1627891374362-c6ca6c675cb3', prize: 'A year of Friday bouquets', detail: 'Marrow Hill Flowers' },
  { photo: 'photo-1530655937400-7f00928c51e1', prize: 'Two nights at Alder Barn', detail: 'Breakfast included' },
];
// Photos are drawn about 330 pt wide: 1000 px covers 3× screens.
const PHOTO_WIDTH = 1000;
const photoUrl = (k: number) => `https://images.unsplash.com/${TICKETS[k].photo}?auto=format&fit=crop&w=${PHOTO_WIDTH}&q=80`;

const BACKGROUND = '#13251b';
const BACKGROUND_EDGE = '#0a150f';
const CONFETTI_COLOURS = ['#eea41c', '#f7e9c6', '#e2673d', '#5f9c46', '#8ec5e6'];
const TAU = Math.PI * 2;

// Layout (pt): the ticket is at most TICKET_MAX wide with SIDE margins; the scratch window is
// SCRATCH_ASPECT as tall as it is wide; the button sits BUTTON_GAP under the ticket.
const TICKET_MAX = 370;
const SIDE = 20;
const SCRATCH_TOP = 114;
const SCRATCH_ASPECT = 0.7;
const STUB = 100;
const BUTTON_GAP = 26;
const BUTTON_HEIGHT = 54;

// Scratching: the foil is split into GRID_X × GRID_Y cells; a cell counts as cleared when the
// brush's middle passes over it, and at REVEAL_AT of them the rest dissolves over DISSOLVE_TIME ms.
const GRID_X = 16;
const GRID_Y = 12;
const REVEAL_AT = 0.6;
const DISSOLVE_TIME = 1100;
const DISSOLVE_EASE = Easing.bezier(0.33, 0, 0.2, 1);
// A flake tears off for every FLAKE_EVERY pt the finger scratches through fresh foil.
const FLAKE_EVERY = 7;
const FLAKE_MAX = 240;
// The finger's path is kept as points; past this many the rest of the foil dissolves, so a long
// scrub over one corner never grows the path (and its per-frame rebuild) without bound.
const POINT_MAX = 4000;
const FLAKE_GRAVITY = 1300;
const FLAKE_DRAG = 2.6;
const CONFETTI = 110;
const CONFETTI_GRAVITY = 1150;
const CONFETTI_DRAG = 1.8;
// The foil's light follows the finger, easing at this rate (1/s); left alone it drifts slowly.
const TILT_RATE = 6;

// Tickets change places: the new one springs in from the right, the old one is thrown left.
// Dragging the ticket left past COMMIT_DISTANCE of the screen, or flicking faster than
// COMMIT_SPEED (pt/s), throws it too; to the right it only gives a RUBBER band.
const ENTER_SPRING = { duration: 800, dampingRatio: 0.8 };
const LEAVE_SPRING = { duration: 650, dampingRatio: 1 };
const BACK_SPRING = { duration: 450, dampingRatio: 0.8 };
const COMMIT_DISTANCE = 0.3;
const COMMIT_SPEED = 800;
const RUBBER = 70;
const FADE_TIME = 320;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// autoPlay loop (seconds): a virtual finger scratches a zigzag over the foil, the prize shows,
// then "New ticket" brings the next one in and it rests, unscratched, as the loop began.
const LOOP = 9;
const FINGER_DOWN_AT = 0.7;
const FINGER_UP_AT = 3.9;
const ZIGZAG_LEGS = 7;
const ZIGZAG_INSET = 22;
const NEW_AT = 6.5;
// A touch pauses the loop this long; then it resumes from a fresh ticket.
const USER_HOLD = 4;

type Props = {
  /** Scratches, reveals and deals a new ticket on its own, on a loop of LOOP s. */
  autoPlay?: boolean;
  /** Called with the prize when a ticket is revealed. */
  onReveal?: (prize: string) => void;
};

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

/** True when the clock went past `at` between `before` and `now`, wrapping round the loop. */
function passed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : at > before || at <= now;
}

/** Resistance past an edge: follows the finger at first, never further than RUBBER. */
function rubber(x: number) {
  'worklet';
  return RUBBER * (1 - 1 / (1 + x / RUBBER));
}

/** The virtual finger's point at progress `u` (0…1): side to side, ZIGZAG_LEGS times, going down. */
function zigzag(u: number, w: number, h: number) {
  'worklet';
  const legs = u * ZIGZAG_LEGS;
  const leg = Math.min(ZIGZAG_LEGS - 1, Math.floor(legs));
  const f = legs - leg;
  const across = leg % 2 === 0 ? f : 1 - f;
  return [ZIGZAG_INSET + across * (w - ZIGZAG_INSET * 2), ZIGZAG_INSET + u * (h - ZIGZAG_INSET * 2)];
}

/** Marks the grid cells the brush covers between two points; returns how many were new. */
function markCells(grid: number[], x0: number, y0: number, x1: number, y1: number, w: number, h: number) {
  'worklet';
  const cw = w / GRID_X;
  const ch = h / GRID_Y;
  const reach = BRUSH * 0.45;
  const steps = Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 6));
  let added = 0;
  for (let k = 0; k <= steps; k += 1) {
    const x = x0 + ((x1 - x0) * k) / steps;
    const y = y0 + ((y1 - y0) * k) / steps;
    const c0 = Math.max(0, Math.floor((x - reach) / cw));
    const c1 = Math.min(GRID_X - 1, Math.floor((x + reach) / cw));
    const r0 = Math.max(0, Math.floor((y - reach) / ch));
    const r1 = Math.min(GRID_Y - 1, Math.floor((y + reach) / ch));
    for (let r = r0; r <= r1; r += 1) {
      for (let c = c0; c <= c1; c += 1) {
        const at = r * GRID_X + c;
        if (grid[at] === 0 && Math.hypot((c + 0.5) * cw - x, (r + 0.5) * ch - y) <= reach) {
          grid[at] = 1;
          added += 1;
        }
      }
    }
  }
  return added;
}

function emptyGrid() {
  return new Array<number>(GRID_X * GRID_Y).fill(0);
}

function makeGeometry(width: number, height: number, insets: EdgeInsets): Geometry {
  const w = Math.min(width - SIDE * 2, TICKET_MAX);
  const sw = w - 44;
  const sh = Math.round(sw * SCRATCH_ASPECT);
  const perfY = SCRATCH_TOP + sh + 24;
  const h = perfY + STUB;
  const block = h + BUTTON_GAP + BUTTON_HEIGHT;
  const room = height - insets.top - insets.bottom;
  const top = insets.top + Math.max(12, (room - block) / 2);
  return {
    width,
    height,
    ticket: { x: (width - w) / 2, y: top, w, h },
    scratch: { x: 22, y: SCRATCH_TOP, w: sw, h: sh },
    perfY,
    buttonY: top + h + BUTTON_GAP,
  };
}

function useSlot(offset: number, shown: number): ScratchSlot {
  return {
    points: useSharedValue<number[]>([]),
    grid: useSharedValue<number[]>(emptyGrid()),
    cleared: useSharedValue(0),
    revealed: useSharedValue(false),
    dissolve: useSharedValue(0),
    pop: useSharedValue(0),
    offset: useSharedValue(offset),
    shown: useSharedValue(shown),
  };
}

function haptic(kind: 'success' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A scratch-off lottery ticket on a dark green table. A finger rubs the holographic foil away
 * along a soft brush, tearing off little foil flakes that fall with gravity; once enough is
 * cleared the rest dissolves with a golden edge, the prize photo settles into view, the stub
 * prints the prize and confetti bursts. "New ticket" (or throwing the ticket left) deals the next.
 */
export function ScratchTicket({ autoPlay = false, onReveal }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay;

  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(() => (size ? makeGeometry(size.width, size.height, insets) : null), [size, insets]);
  const font = useMemo(
    () => matchFont({ fontFamily: process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed', fontSize: 13, fontWeight: '700' }),
    [],
  );
  // Every prize photo is fetched up front, so a new ticket never waits for its picture.
  const image0 = useImage(photoUrl(0));
  const image1 = useImage(photoUrl(1));
  const image2 = useImage(photoUrl(2));
  const image3 = useImage(photoUrl(3));
  const images = [image0, image1, image2, image3];

  // Two slots take turns being the ticket in play; the other waits off screen to the right.
  const slotA = useSlot(0, 1);
  const slotB = useSlot(10000, 0);
  const [tickets, setTickets] = useState<[number, number]>([0, 1]);
  const [revealed, setRevealed] = useState<[boolean, boolean]>([false, false]);
  // How many tickets have been dealt, and which one each slot holds.
  const dealt = useSharedValue(1);
  const dealtTo = useSharedValue([0, 1]);
  const active = useSharedValue(0);
  const switching = useSharedValue(false);

  const phase = useSharedValue(0);
  const tilt = useSharedValue([0, 0]);
  const tiltGoal = useSharedValue([0, 0]);
  const flakes = useSharedValue<number[]>([]);
  const confetti = useSharedValue<number[]>([]);
  // What the finger is doing: 0 nothing, 1 scratching, 2 dragging the ticket.
  const mode = useSharedValue(0);
  const finger = useSharedValue([0, 0]);
  const fingerOn = useSharedValue(false);
  const fingerFresh = useSharedValue(false);
  const fingerAlpha = useSharedValue(0);
  const press = useSharedValue(1);
  const emphasis = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const markRevealed = (slot: number, user: boolean) => {
    setRevealed((now) => (slot === 0 ? [true, now[1]] : [now[0], true]));
    if (user) haptic('success');
    onReveal?.(TICKETS[dealtTo.get()[slot]].prize);
  };

  /** Deals the next ticket into the waiting slot and swaps the two (runs on the JS thread). */
  const deal = (user: boolean, velocity: number) => {
    if (!geo) return;
    const from = active.get();
    const to = 1 - from;
    const leaving = from === 0 ? slotA : slotB;
    const coming = to === 0 ? slotA : slotB;
    const next = (dealt.get() + 1) % TICKETS.length;
    dealt.set(dealt.get() + 1);
    dealtTo.set(to === 0 ? [next, dealtTo.get()[1]] : [dealtTo.get()[0], next]);
    setTickets((now) => (to === 0 ? [next, now[1]] : [now[0], next]));
    setRevealed((now) => (to === 0 ? [false, now[1]] : [now[0], false]));
    coming.points.set([]);
    coming.grid.set(emptyGrid());
    coming.cleared.set(0);
    coming.revealed.set(false);
    coming.dissolve.set(0);
    coming.pop.set(0);
    active.set(to);
    emphasis.set(withTiming(0, { duration: 240 }));
    if (user) haptic('light');
    // Cleared even when the move is cut short, so a cancelled spring never leaves the table stuck.
    const done = () => {
      'worklet';
      switching.set(false);
    };
    if (reducedMotion) {
      coming.offset.set(0);
      coming.shown.set(withTiming(1, { duration: FADE_TIME }, done));
      leaving.shown.set(withTiming(0, { duration: FADE_TIME }));
      return;
    }
    coming.shown.set(1);
    // Parked off the right edge first, so it always comes in from there.
    coming.offset.set(withSequence(withTiming(geo.width * 1.15, { duration: 0 }), withSpring(0, ENTER_SPRING, done)));
    leaving.offset.set(withSpring(-geo.width * 1.25, { ...LEAVE_SPRING, velocity }));
  };

  // The waiting slot sits just off the right edge once the screen is measured.
  useEffect(() => {
    if (geo && !reducedMotion && active.get() !== 1) slotB.offset.set(geo.width * 1.15);
  }, [geo, reducedMotion, slotB.offset, active]);

  function slotOf(k: number) {
    'worklet';
    return k === 0 ? slotA : slotB;
  }

  function newTicket(user: boolean, velocity: number) {
    'worklet';
    if (switching.get()) return;
    switching.set(true);
    fingerOn.set(false);
    fingerAlpha.set(withTiming(0, { duration: 160 }));
    scheduleOnRN(deal, user, velocity);
  }

  function reveal(k: number, user: boolean) {
    'worklet';
    if (!geo) return;
    const s = slotOf(k);
    s.revealed.set(true);
    s.dissolve.set(withTiming(1, { duration: DISSOLVE_TIME, easing: DISSOLVE_EASE }));
    emphasis.set(withTiming(1, { duration: 300 }));
    if (!reducedMotion) {
      s.pop.set(withSequence(withTiming(1, { duration: 140, easing: EASE_OUT }), withSpring(0, { duration: 600, dampingRatio: 0.45 })));
      const S = geo.scratch;
      const cx = geo.ticket.x + S.x + S.w / 2;
      const cy = geo.ticket.y + S.y + S.h / 2;
      confetti.set(addConfetti(confetti.get(), CONFETTI, cx, cy, S.w, CONFETTI_COLOURS.length));
    }
    scheduleOnRN(markRevealed, k, user);
  }

  /** The finger at (x, y) on screen: rubs the foil there, counts what it cleared, sheds flakes. */
  function scratchAt(x: number, y: number, vx: number, vy: number, begin: boolean, user: boolean) {
    'worklet';
    if (!geo || switching.get()) return;
    const k = active.get();
    const s = slotOf(k);
    if (s.revealed.get()) return;
    const S = geo.scratch;
    const lx = x - geo.ticket.x - S.x - s.offset.get();
    const ly = y - geo.ticket.y - S.y;
    const points = s.points.get();
    const n = points.length;
    const joined = !begin && n >= 2 && points[n - 2] !== PEN_UP;
    const px = joined ? points[n - 2] : lx;
    const py = joined ? points[n - 1] : ly;
    if (joined && Math.hypot(lx - px, ly - py) < 2.5) return;
    const next = points.slice();
    if (begin && n > 0) next.push(PEN_UP, PEN_UP);
    next.push(lx, ly);
    s.points.set(next);
    tiltGoal.set([clamp((lx / S.w) * 2 - 1, -1, 1), clamp((ly / S.h) * 2 - 1, -1, 1)]);

    const grid = s.grid.get().slice();
    const added = markCells(grid, px, py, lx, ly, S.w, S.h);
    if (added === 0) {
      if (next.length >= POINT_MAX * 2) reveal(k, user);
      return;
    }
    s.grid.set(grid);
    s.cleared.set(s.cleared.get() + added);
    if (!reducedMotion && flakes.get().length < FLAKE_MAX * 11) {
      const count = Math.min(5, 1 + Math.floor(Math.hypot(lx - px, ly - py) / FLAKE_EVERY));
      flakes.set(addFlakes(flakes.get(), count, x, y, vx, vy, BRUSH * 0.35));
    }
    if (s.cleared.get() >= REVEAL_AT * GRID_X * GRID_Y || next.length >= POINT_MAX * 2) reveal(k, user);
  }

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
    if (fingerOn.get()) {
      fingerOn.set(false);
      fingerAlpha.set(withTiming(0, { duration: 160 }));
    }
  }

  useFrameCallback((frame) => {
    if (!geo) return;
    // Never 0: the virtual finger's speed divides by it.
    const dt = clamp((frame.timeSincePreviousFrame ?? 16) / 1000, 0.001, 0.05);
    const p = (phase.get() + (dt * TAU) / LOOP) % TAU;
    phase.set(p);

    // autoPlay: a fixed script on a clock, paused for a while by any touch.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        const s = slotOf(active.get());
        if (s.cleared.get() > 0) {
          newTicket(false, 0);
          clock.set(NEW_AT);
        } else clock.set(0);
      }
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      if (passed(before, t, FINGER_DOWN_AT) && !switching.get()) {
        fingerOn.set(true);
        fingerFresh.set(true);
        fingerAlpha.set(withTiming(1, { duration: 160 }));
      }
      if (fingerOn.get()) {
        const S = geo.scratch;
        const u = clamp((t - FINGER_DOWN_AT) / (FINGER_UP_AT - FINGER_DOWN_AT), 0, 1);
        const at = zigzag(u, S.w, S.h);
        const x = geo.ticket.x + S.x + at[0];
        const y = geo.ticket.y + S.y + at[1];
        const was = finger.get();
        const fresh = fingerFresh.get();
        finger.set([x, y]);
        fingerFresh.set(false);
        scratchAt(x, y, fresh ? 0 : (x - was[0]) / dt, fresh ? 0 : (y - was[1]) / dt, fresh, false);
        if (u >= 1 || slotOf(active.get()).revealed.get()) {
          fingerOn.set(false);
          fingerAlpha.set(withTiming(0, { duration: 220 }));
        }
      }
      if (passed(before, t, NEW_AT)) {
        press.set(withSequence(withTiming(0.97, { duration: 110 }), withTiming(1, { duration: 180 })));
        newTicket(false, 0);
      }
    }

    // The foil's light: towards the finger while scratching, otherwise a slow drift on the loop.
    if (mode.get() !== 1 && !fingerOn.get()) tiltGoal.set([Math.sin(p) * 0.45, Math.cos(p) * 0.3]);
    const goal = tiltGoal.get();
    const now = tilt.get();
    const ease = 1 - Math.exp(-dt * TILT_RATE);
    tilt.set([now[0] + (goal[0] - now[0]) * ease, now[1] + (goal[1] - now[1]) * ease]);

    if (flakes.get().length > 0) flakes.set(stepPieces(flakes.get(), dt, FLAKE_GRAVITY, FLAKE_DRAG, geo.height + 20));
    if (confetti.get().length > 0) confetti.set(stepPieces(confetti.get(), dt, CONFETTI_GRAVITY, CONFETTI_DRAG, geo.height + 20));
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((event) => {
      touched();
      mode.set(0);
      if (!geo || switching.get()) return;
      const s = slotOf(active.get());
      const T = geo.ticket;
      const S = geo.scratch;
      const left = T.x + S.x;
      const top = T.y + S.y;
      const inWindow = event.x > left - 6 && event.x < left + S.w + 6 && event.y > top - 6 && event.y < top + S.h + 6;
      if (inWindow && !s.revealed.get()) {
        mode.set(1);
        scratchAt(event.x, event.y, 0, 0, true, true);
      } else if (event.x > T.x && event.x < T.x + T.w && event.y > T.y && event.y < T.y + T.h) {
        mode.set(2);
      }
    })
    .onUpdate((event) => {
      if (mode.get() === 1) scratchAt(event.x, event.y, event.velocityX, event.velocityY, false, true);
      // A deal that started mid-drag (the button, or autoPlay resuming) owns the offsets now.
      else if (mode.get() === 2 && !switching.get()) {
        const tx = event.translationX;
        slotOf(active.get()).offset.set(tx < 0 ? tx : rubber(tx));
      }
    })
    .onEnd((event) => {
      if (mode.get() !== 2 || !geo || switching.get()) return;
      const s = slotOf(active.get());
      if (event.translationX < -geo.width * COMMIT_DISTANCE || event.velocityX < -COMMIT_SPEED) newTicket(true, event.velocityX);
      else s.offset.set(withSpring(0, { ...BACK_SPRING, velocity: event.velocityX }));
    })
    .onFinalize(() => {
      mode.set(0);
    });

  const onNewTicket = () => {
    hold.set(USER_HOLD);
    if (fingerOn.get()) {
      fingerOn.set(false);
      fingerAlpha.set(withTiming(0, { duration: 160 }));
    }
    if (switching.get()) return;
    switching.set(true);
    deal(true, 0);
  };

  const flakeUniforms = useDerivedValue(() => ({ size: [geo?.width ?? 1, geo?.height ?? 1], time: phase.get(), tilt: tilt.get(), dissolve: 0 }));
  const flakePath = useDerivedValue(() => piecesPath(flakes.get(), -1, 0.8));
  const confetti0 = useDerivedValue(() => piecesPath(confetti.get(), 0, 1.7));
  const confetti1 = useDerivedValue(() => piecesPath(confetti.get(), 1, 1.7));
  const confetti2 = useDerivedValue(() => piecesPath(confetti.get(), 2, 1.7));
  const confetti3 = useDerivedValue(() => piecesPath(confetti.get(), 3, 1.7));
  const confetti4 = useDerivedValue(() => piecesPath(confetti.get(), 4, 1.7));
  const confettiPaths = [confetti0, confetti1, confetti2, confetti3, confetti4];
  const fingerX = useDerivedValue(() => finger.get()[0]);
  const fingerY = useDerivedValue(() => finger.get()[1]);

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: press.get() }] }));
  const buttonFill = useAnimatedStyle(() => ({ opacity: emphasis.get() }));
  const buttonText = useAnimatedStyle(() => ({ color: interpolateColor(emphasis.get(), [0, 1], [PAPER, '#14231a']) }));
  const arrowStyle = useAnimatedStyle(() => ({ opacity: emphasis.get() }));

  return (
    <View
      style={[styles.screen, { backgroundColor: BACKGROUND }]}
      onLayout={(event: LayoutChangeEvent) => {
        const { width, height } = event.nativeEvent.layout;
        // A zero first layout would put NaN into the ticket's tilt and the coverage grid.
        if (width > 0 && height > 0) setSize({ width, height });
      }}
    >
      {geo && (
        <Canvas style={StyleSheet.absoluteFill}>
          <Rect x={0} y={0} width={geo.width} height={geo.height}>
            <RadialGradient c={vec(geo.width / 2, geo.height * 0.42)} r={geo.height * 0.7} colors={['#1d3a29', BACKGROUND, BACKGROUND_EDGE]} positions={[0, 0.55, 1]} />
          </Rect>
          <Rect x={0} y={0} width={geo.width} height={geo.height} blendMode="overlay" opacity={0.16}>
            <FractalNoise freqX={0.8} freqY={0.8} octaves={2} seed={5} />
            <ColorMatrix matrix={[0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, 1]} />
          </Rect>
        </Canvas>
      )}

      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          {geo &&
            [0, 1].map((k) => (
              <ScratchTicketCard
                key={k}
                ticket={TICKETS[tickets[k]]}
                image={images[tickets[k]]}
                slot={k === 0 ? slotA : slotB}
                geo={geo}
                phase={phase}
                tilt={tilt}
                font={font}
                revealed={revealed[k]}
                reducedMotion={reducedMotion}
              />
            ))}
        </View>
      </GestureDetector>

      {geo && (
        <Animated.View style={[styles.buttonWrap, { top: geo.buttonY, left: geo.ticket.x, width: geo.ticket.w }, buttonStyle]}>
          <Pressable
            onPress={onNewTicket}
            onPressIn={() => press.set(withTiming(0.97, { duration: 110 }))}
            onPressOut={() => press.set(withTiming(1, { duration: 160 }))}
            accessibilityRole="button"
            accessibilityLabel="New ticket"
            style={styles.button}
          >
            <Animated.View style={[StyleSheet.absoluteFill, styles.buttonFill, buttonFill]} />
            <Animated.Text style={[styles.buttonLabel, buttonText]}>New ticket</Animated.Text>
            <Animated.View style={arrowStyle}>
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <SvgPath d="M2 8h11M9 3.5 13.5 8 9 12.5" stroke="#14231a" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </Svg>
            </Animated.View>
          </Pressable>
        </Animated.View>
      )}

      {geo && (
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas style={StyleSheet.absoluteFill}>
          <Path path={flakePath}>
            <Shader source={FOIL_SHADER} uniforms={flakeUniforms} />
          </Path>
          {confettiPaths.map((path, k) => (
            <Path key={k} path={path} color={CONFETTI_COLOURS[k]} />
          ))}
          {/* The virtual finger of autoPlay, so a recording shows what is scratching. */}
          <Group opacity={fingerAlpha}>
            <Circle cx={fingerX} cy={fingerY} r={22} color="rgba(255,255,255,0.22)" />
            <Circle cx={fingerX} cy={fingerY} r={22} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.6)" />
          </Group>
        </Canvas>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  buttonWrap: {
    position: 'absolute',
    height: BUTTON_HEIGHT,
  },
  button: {
    flex: 1,
    borderRadius: BUTTON_HEIGHT / 2,
    borderWidth: 1,
    borderColor: 'rgba(244,236,217,0.28)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    overflow: 'hidden',
  },
  buttonFill: {
    backgroundColor: PAPER,
  },
  buttonLabel: {
    fontFamily: process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif',
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
});
