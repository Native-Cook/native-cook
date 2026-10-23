import { BlurStyle, Canvas, ClipOp, Fill, matchFont, Picture, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BIN_BODY, BIN_FLOOR, BIN_HOVER, BIN_MOUTH_DROP, binFrame, binPoint, binRest, type BinState, drawBin, makeBinArt, ROLL_TIME } from './StickerBoardBin';
import {
  clamp01,
  crumplePath,
  drawCrumpled,
  drawFace,
  drawPeeled,
  drawShadow,
  easeInOut,
  easeOut,
  keepSide,
  type Loaded,
  rubber,
  spring,
  toLocal,
  toScreen,
} from './StickerBoardDraw';
import { CORK, photo, shapes, type Sticker, STICKERS } from './StickerBoardShapes';

export type { Sticker } from './StickerBoardShapes';

type Props = {
  stickers?: Sticker[];
  /** Plays a loop of LOOP seconds: two stickers moved, turned and dropped, one peeled, crumpled and binned. */
  autoPlay?: boolean;
  /** The words on the tape at the top of the board. */
  title?: string;
};

// Length of the self-playing loop, in seconds. It starts and ends with every sticker at home.
const LOOP = 15;
// Moves in the loop: which sticker, when it is picked up and dropped (s), and where to, as an
// offset from home in points of a 402 pt wide screen, a turn in degrees and a scale.
const MOVES: { index: number; at: number; end: number; dx: number; dy: number; turn: number; scale: number }[] = [
  { index: 0, at: 0.6, end: 2.2, dx: 150, dy: 250, turn: 18, scale: 1 },
  { index: 1, at: 3.0, end: 4.6, dx: -170, dy: 165, turn: -26, scale: 1.28 },
  { index: 0, at: 9.7, end: 11.1, dx: 0, dy: 0, turn: 0, scale: 1 },
  { index: 1, at: 11.4, end: 12.8, dx: 0, dy: 0, turn: 0, scale: 1 },
];
// The peel in the loop: the sticker, when it is pressed, when the corner lifts, when the finger has
// pulled the corner past the middle, when it has carried it near the bin, and when it lets go.
const PEEL_INDEX = 5;
const PEEL_PRESS = 5.0;
const PEEL_START = 5.45;
const PEEL_PULLED = 6.9;
const PEEL_CARRIED = 8.0;
const PEEL_DROP = 8.5;
// How far past the middle the scripted finger pulls the corner, as a share of the corner's distance.
const PEEL_REACH = 0.7;
// The scripted carry ends this far off the bin's hover point (pt), inside its reach, so the magnet
// visibly pulls the sticker in the last bit.
const CARRY_TO = [54, -56];
// A carried sticker travels on an arc this far off the straight line (pt).
const ARC = 40;
// After a touch the loop waits this long (s), then tidies every sticker home over TIDY_TIME s.
const USER_HOLD = 3.5;
const TIDY_TIME = 0.9;
const TIDY_REST = 0.3;

const DESIGN_WIDTH = 402;
// Picked up: grows by LIFT_GROW and its shadow falls further and softer.
const LIFT_GROW = 0.06;
const LIFT_TIME = 0.12;
// A sticker picked up from under others does not jump over them: a copy stays where it was in the
// pile while the one on top fades in over RISE_TIME s on an ease-in-out curve, so it surfaces
// through the stickers that covered it as it lifts.
const RISE_TIME = 0.3;
// Dropped: it keeps a little of its speed (time constant in s) while it falls for FALL_TIME s, then
// slaps down with a squash that springs out.
const GLIDE = 0.09;
const FALL_TIME = 0.16;
const MAX_THROW = 1500;
const SQUASH = 0.085;
const SQUASH_STIFFNESS = 480;
const SQUASH_DAMPING = 19;
// While carried it swings with the finger's speed: degrees per pt/s, up to SWAY_MAX.
const SWAY_PER_SPEED = 0.012;
const SWAY_MAX = 10;
// Pinch limits, rubber-banded beyond.
const MIN_SCALE = 0.55;
const MAX_SCALE = 2.2;
// Holding this long (ms) lifts a corner instead; the corner follows the finger, and pulled further
// than DETACH times its distance from the middle the sticker comes off.
const LONG_PRESS = 420;
// Frames a touch that ended is given for one of the gestures to start before it counts as a tap.
const PENDING_FRAMES = 3;
const DETACH = 1.25;
const FLATTEN_TIME = 0.22;
const DETACHED_LIFT = 1.4;
const FOLLOW = 0.05;
// The bin notices a loose sticker within APPROACH pt of the point over its mouth and opens its lid
// the nearer it comes; within REACH pt it pulls the sticker in (let go of beyond REACH_OUT times that).
const APPROACH = 300;
const REACH = 92;
const REACH_OUT = 1.3;
// Pulled in, the sticker moves this share of the way to the mouth and shrinks by MAGNET_SHRINK.
const MAGNET_PULL = 0.82;
const MAGNET_SHRINK = 0.3;
const MAGNET_STIFFNESS = 360;
const MAGNET_DAMPING = 19;
// A flick towards the bin from within FLING_RANGE pt, faster than FLING_SPEED pt/s and within
// about 25° of it, throws the sticker away too.
const FLING_SPEED = 800;
const FLING_RANGE = 460;
const FLING_AIM = 0.9;
// Thrown away: it crumples into a ball of BALL pt over CRUMPLE s while it moves over the mouth, then
// drops in on a short arc over DROP s.
const CRUMPLE = 0.34;
const DROP = 0.3;
const TOSS_END = CRUMPLE + DROP;
const BALL = 16;
const BALL_HOP = 16;
const BALL_SPIN = 170;
// The bin's springs (stiffness, damping): rising, the lid, its slam and bounce, leaning and growing
// towards a sticker, the squash and wobble when the lid slams, and how long it stays up after.
const SHOW = [300, 21];
const LID = [240, 15];
const SLAM = [1500, 14];
const LID_BOUNCE = 0.32;
const LEAN = [260, 20];
const LEAN_MAX = 9;
const GROW = [300, 20];
const GROW_NEAR = 0.07;
const GROW_MAGNET = 0.05;
const BIN_SQUASH = [520, 12];
const BIN_SQUASH_KICK = 2.6;
const WOBBLE = [220, 5];
const WOBBLE_KICK = 70;
const LINGER = 0.9;
// A sticker thrown away comes back home after RESPAWN s, slapped back on over SPAWN_TIME s.
const RESPAWN = 4;
const SPAWN_TIME = 0.22;

const ROUNDED = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const HAND = process.env.EXPO_OS === 'ios' ? 'Marker Felt' : 'sans-serif';

type Stage = { width: number; height: number; top: number; bottom: number };

/** One sticker's live state. Positions in screen points, turns in degrees. */
type Live = {
  x: number;
  y: number;
  turn: number;
  scale: number;
  vx: number;
  vy: number;
  sway: number;
  lift: number;
  held: boolean;
  byUser: boolean;
  falling: number;
  squash: number;
  squashSpeed: number;
  // Seconds since it was thrown away (−1 on the board), and where, how turned and how big it was.
  gone: number;
  goneX: number;
  goneY: number;
  goneTurn: number;
  goneScale: number;
  goneByUser: boolean;
  // Seconds since it was slapped back on (−1 for none).
  spawn: number;
  // Rising through the pile: 0…1 (1 settled on top), and the sticker its old copy is drawn under.
  rise: number;
  riseUnder: number;
  // Where the tidy-up starts it from.
  fromX: number;
  fromY: number;
  fromTurn: number;
  fromScale: number;
};

/** A sticker lying still, before it is placed. */
function resting(): Live {
  return {
    x: 0,
    y: 0,
    turn: 0,
    scale: 1,
    vx: 0,
    vy: 0,
    sway: 0,
    lift: 0,
    held: false,
    byUser: false,
    falling: 0,
    squash: 0,
    squashSpeed: 0,
    gone: -1,
    goneX: 0,
    goneY: 0,
    goneTurn: 0,
    goneScale: 1,
    goneByUser: false,
    spawn: -1,
    rise: 1,
    riseUnder: -1,
    fromX: 0,
    fromY: 0,
    fromTurn: 0,
    fromScale: 1,
  };
}

/** The peeled corner: the sticker, its corner (sticker units), the finger (screen), and more. */
type Peel = {
  index: number;
  cx: number;
  cy: number;
  fx: number;
  fy: number;
  detached: boolean;
  // Seconds since the flap began to lie flat again (−1 for not), and the corner it starts from.
  flatten: number;
  flatX: number;
  flatY: number;
  anchorX: number;
  anchorY: number;
  byUser: boolean;
};

const NO_PEEL: Peel = { index: -1, cx: 0, cy: 0, fx: 0, fy: 0, detached: false, flatten: -1, flatX: 0, flatY: 0, anchorX: 0, anchorY: 0, byUser: false };

/** Moves sticker `i` to the top of the pile. */
function raise(order: number[], i: number) {
  'worklet';
  const next = order.filter((j) => j !== i);
  next.push(i);
  return next;
}

/** The sticker right above `i` in the pile, or −1 when `i` is already on top. */
function above(order: number[], i: number) {
  'worklet';
  const at = order.indexOf(i);
  return at >= 0 && at < order.length - 1 ? order[at + 1] : -1;
}

/**
 * A cork board covered in die-cut stickers: photo stickers in a thick white border and drawn ones,
 * a star, a smiley, a speech bubble, a seal and a heart. A sticker can be dragged, turned with two
 * fingers and pinched, all at once; it lifts when picked up and slaps down with a squash when let
 * go. A long press lifts a corner that follows the finger, curling back over the sticker, until the
 * sticker comes off. A steel pedal bin rises from the bottom edge: it leans towards a loose sticker,
 * opens its lid as it nears and pulls it in; let go there, the sticker crumples into a paper ball and
 * drops in, the lid slams and the counter on the bin ticks up. A little later a new one is slapped
 * back in its place.
 */
export function StickerBoard({ stickers = STICKERS, autoPlay = false, title = 'WEEKEND · Nº 14' }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = stickers.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const shaped = useMemo(() => stickers.map(shapes), [stickers]);
  const arts = useMemo(() => shaped.map((s) => s.art), [shaped]);
  const outlines = useMemo(() => shaped.map((s) => s.outline), [shaped]);
  const extents = useMemo(() => shaped.map((s) => s.extent), [shaped]);
  const rings = useMemo(() => shaped.map((s) => s.ring), [shaped]);
  const balls = useMemo(() => shaped.map((s) => s.ball), [shaped]);
  const shades = useMemo(() => shaped.map((s) => s.shades), [shaped]);
  const binArt = useMemo(() => makeBinArt(), []);
  const fonts = useMemo(
    () => ({
      bold: matchFont({ fontFamily: ROUNDED, fontSize: 18, fontWeight: '800' }),
      small: matchFont({ fontFamily: ROUNDED, fontSize: 13, fontWeight: '800' }),
      hand: matchFont({ fontFamily: HAND, fontSize: 17, fontWeight: '700' }),
      digits: matchFont({ fontFamily: ROUNDED, fontSize: 11.5, fontWeight: '700' }),
    }),
    [],
  );

  const live = useSharedValue<Live[]>(stickers.map(resting));
  const order = useSharedValue<number[]>(stickers.map((_, i) => i));
  const peel = useSharedValue<Peel>(NO_PEEL);
  // The sticker under the fingers, whether a drag, pinch, turn or peel began, and a release waiting
  // for the next frame to see whether any of them did.
  const active = useSharedValue(-1);
  const started = useSharedValue(false);
  const pending = useSharedValue(0);
  // A peel the finger is still holding.
  const peeling = useSharedValue(false);
  const dragFrom = useSharedValue([0, 0]);
  const turnFrom = useSharedValue(0);
  const scaleFrom = useSharedValue(1);
  const bin = useSharedValue<BinState>(binRest());
  const script = useSharedValue(0);
  const hold = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const scriptFinger = useSharedValue([0, 0]);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[]>(stickers.map(() => null));
  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  useEffect(() => {
    if (width <= 0 || height <= 0) return;
    const room = { width, height, top: insets.top, bottom: insets.bottom };
    stage.set(room);
    order.set(stickers.map((_, i) => i));
    peel.set(NO_PEEL);
    active.set(-1);
    peeling.set(false);
    bin.set(binRest());
    live.set(
      stickers.map((s) => ({
        ...resting(),
        x: s.home[0] * width,
        y: room.top + s.home[1] * (height - room.top - room.bottom),
        turn: s.turn,
      })),
    );
  }, [width, height, insets.top, insets.bottom, stickers, live, stage, order, peel, active, peeling, bin]);

  useEffect(() => {
    script.set(0);
    hold.set(0);
  }, [auto, script, hold]);

  useEffect(() => {
    let alive = true;
    loaded.set(stickers.map(() => null));
    stickers.forEach((s, i) => {
      if (!s.photo) return;
      Skia.Data.fromURI(photo(s.photo))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            if (i >= list.length) return list;
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [stickers, loaded, elapsed]);

  const thud = (kind: 'drop' | 'lift' | 'rip' | 'snap' | 'slam') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'slam') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 'snap') Haptics.selectionAsync().catch(() => {});
    else if (kind === 'drop') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (kind === 'rip') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Home of sticker `i` on the screen. */
  const homeOf = (i: number) => {
    'worklet';
    const room = stage.get();
    const def = stickers[i];
    if (!def) return [room.width / 2, room.height / 2];
    return [def.home[0] * room.width, room.top + def.home[1] * (room.height - room.top - room.bottom)];
  };

  /** The point over the bin's mouth where a sticker is pulled in, with the bin standing up straight. */
  const hoverPoint = () => {
    'worklet';
    const room = stage.get();
    const k = room.width / DESIGN_WIDTH;
    return [room.width / 2, room.height - room.bottom - (BIN_FLOOR + BIN_BODY + BIN_HOVER) * k];
  };

  /** The top sticker under a point, or −1. */
  const hit = (x: number, y: number) => {
    'worklet';
    const k = stage.get().width / DESIGN_WIDTH;
    const list = live.get();
    const stack = order.get();
    for (let j = stack.length - 1; j >= 0; j -= 1) {
      const i = stack[j];
      const s = list[i];
      if (!s || i >= outlines.length || s.gone >= 0) continue;
      const p = toLocal(s, k, x, y);
      if (outlines[i].contains(p[0], p[1])) return i;
    }
    return -1;
  };

  /** Puts sticker `i` on top of the pile, rising through the ones that covered it. */
  const bringUp = (i: number) => {
    'worklet';
    const stack = order.get();
    const under = above(stack, i);
    if (under < 0) return;
    order.set(raise(stack, i));
    live.modify((list) => {
      'worklet';
      const s = list[i];
      // Still rising from before: the copy stays where it was, under the same sticker.
      if (s.rise >= 1) s.riseUnder = under;
      s.rise = 0;
      return list;
    });
  };

  /** Picks up sticker `i` and puts it on top of the pile. */
  const pickUp = (i: number, byUser: boolean) => {
    'worklet';
    if (i < 0 || i >= live.get().length) return -1;
    active.set(i);
    started.set(false);
    bringUp(i);
    live.modify((list) => {
      'worklet';
      const s = list[i];
      s.held = true;
      s.byUser = byUser;
      s.falling = 0;
      s.vx = 0;
      s.vy = 0;
      return list;
    });
    return i;
  };

  /** Picks up the sticker under a finger, once per touch. */
  const grab = (x: number, y: number, byUser: boolean) => {
    'worklet';
    if (active.get() >= 0) return active.get();
    return pickUp(hit(x, y), byUser);
  };

  /** Lets go of a carried sticker: it keeps a little speed, falls, and slaps down. */
  const release = (i: number, vx: number, vy: number) => {
    'worklet';
    if (i < 0 || i >= live.get().length) return;
    const room = stage.get();
    live.modify((list) => {
      'worklet';
      const s = list[i];
      if (!s.held) return list;
      s.held = false;
      s.falling = 1;
      let tx = reducedMotion || !Number.isFinite(vx) ? 0 : Math.max(-MAX_THROW, Math.min(MAX_THROW, vx));
      let ty = reducedMotion || !Number.isFinite(vy) ? 0 : Math.max(-MAX_THROW, Math.min(MAX_THROW, vy));
      // Aim the glide so the sticker lands on the board.
      const endX = Math.max(16, Math.min(room.width - 16, s.x + tx * GLIDE));
      const endY = Math.max(room.top + 16, Math.min(room.height - room.bottom - 16, s.y + ty * GLIDE));
      tx = (endX - s.x) / GLIDE;
      ty = (endY - s.y) / GLIDE;
      s.vx = tx;
      s.vy = ty;
      return list;
    });
    if (active.get() === i) active.set(-1);
  };

  /** Throws sticker `i` away: from where it hangs now it crumples into a ball and drops in the bin. */
  const toss = (i: number, byUser: boolean) => {
    'worklet';
    if (i < 0 || i >= live.get().length) return;
    const m = bin.get().magnet;
    live.modify((list) => {
      'worklet';
      const s = list[i];
      const grow = reducedMotion ? 1 : 1 + LIFT_GROW * Math.min(s.lift, 1.4);
      s.held = false;
      s.falling = 0;
      s.gone = 0;
      s.goneX = s.x;
      s.goneY = s.y;
      s.goneTurn = s.turn + s.sway;
      s.goneScale = s.scale * grow * (1 - MAGNET_SHRINK * clamp01(m));
      s.goneByUser = byUser;
      s.vx = 0;
      s.vy = 0;
      return list;
    });
  };

  const startPeel = (i: number, x: number, y: number, byUser: boolean) => {
    'worklet';
    if (i < 0 || i >= live.get().length) return;
    const k = stage.get().width / DESIGN_WIDTH;
    const s = live.get()[i];
    // The corner: the point of the sticker's reach in the direction of the finger.
    const p = toLocal(s, k, x, y);
    const len = Math.max(1, Math.hypot(p[0], p[1]));
    const reach = extents[i] * 0.9;
    const cx = (p[0] / len) * reach;
    const cy = (p[1] / len) * reach;
    const corner = toScreen(s, k, cx, cy);
    peel.set({ index: i, cx, cy, fx: corner[0], fy: corner[1], detached: false, flatten: -1, flatX: cx, flatY: cy, anchorX: 0, anchorY: 0, byUser });
    peeling.set(true);
    started.set(true);
  };

  /**
   * Lets go of a peel. Off and held over the bin, or flicked at it, it is thrown away; off elsewhere,
   * it is dropped; still on, the flap lies back down.
   */
  const releasePeel = (vx: number, vy: number) => {
    'worklet';
    const p = peel.get();
    const i = p.index;
    if (i < 0 || !peeling.get() || i >= live.get().length) return;
    peeling.set(false);
    const k = stage.get().width / DESIGN_WIDTH;
    if (p.detached) {
      const h = hoverPoint();
      const tx = p.fx + p.anchorX;
      const ty = p.fy + p.anchorY;
      const dx = h[0] - tx;
      const dy = h[1] - ty;
      const dist = Math.min(Math.hypot(dx, dy), Math.hypot(h[0] - p.fx, h[1] - p.fy));
      const speed = Number.isFinite(vx) && Number.isFinite(vy) ? Math.hypot(vx, vy) : 0;
      const aimed = speed > FLING_SPEED && dist < FLING_RANGE * k && dist > 1 && (vx * dx + vy * dy) / (speed * dist) > FLING_AIM;
      if (bin.get().snapped || aimed) toss(i, p.byUser);
      else release(i, vx, vy);
      peel.set(NO_PEEL);
    } else {
      const s = live.get()[i];
      const f = toLocal(s, k, p.fx, p.fy);
      peel.set({ ...p, flatten: 0, flatX: f[0], flatY: f[1] });
    }
    if (active.get() === i) active.set(-1);
  };

  /** A touch takes over from the loop: anything it was carrying is put down. */
  const touched = () => {
    'worklet';
    if (auto && hold.get() <= 0) {
      const list = live.get();
      for (let i = 0; i < list.length; i += 1) if (list[i].held && !list[i].byUser && i !== peel.get().index) release(i, 0, 0);
      if (peel.get().index >= 0 && !peel.get().byUser) releasePeel(0, 0);
      tidy.set(-1);
    }
    hold.set(USER_HOLD);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (!(dt > 0)) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);
    const room = stage.get();
    if (room.width <= 0 || live.get().length !== count) return;
    const k = room.width / DESIGN_WIDTH;

    // A touch that ended without becoming a drag, pinch, turn or peel puts its sticker down. The
    // gestures settle in no fixed order, so this waits a few frames for one of them to start.
    if (pending.get() > 0) {
      pending.set(pending.get() - 1);
      if (pending.get() === 0 && !started.get() && active.get() >= 0 && peel.get().index < 0) release(active.get(), 0, 0);
    }

    // The loop, or the tidy-up before it starts again.
    if (auto) {
      if (hold.get() > 0) {
        if (active.get() < 0 && peel.get().index < 0) hold.set(hold.get() - dt);
        if (hold.get() <= 0) tidy.set(0);
      } else if (tidy.get() >= 0) {
        const t = tidy.get() + dt;
        if (tidy.get() === 0) {
          live.modify((list) => {
            'worklet';
            for (let i = 0; i < list.length; i += 1) {
              const s = list[i];
              s.fromX = s.x;
              s.fromY = s.y;
              s.fromTurn = s.turn;
              s.fromScale = s.scale;
              if (s.gone >= 0) s.gone = Math.max(s.gone, RESPAWN);
            }
            return list;
          });
        }
        const p = easeInOut(clamp01(t / TIDY_TIME));
        live.modify((list) => {
          'worklet';
          for (let i = 0; i < list.length; i += 1) {
            const s = list[i];
            if (s.gone >= 0 || s.spawn >= 0) continue;
            const home = homeOf(i);
            const moved = Math.hypot(s.fromX - home[0], s.fromY - home[1]) > 1 || Math.abs(s.fromTurn - stickers[i].turn) > 0.5 || Math.abs(s.fromScale - 1) > 0.01;
            if (!moved) continue;
            // The tidy-up carries it now, so it is put down at the end like the loop's own moves.
            if (t - dt <= 0) {
              s.held = true;
              s.byUser = false;
            }
            s.x = s.fromX + (home[0] - s.fromX) * p;
            s.y = s.fromY + (home[1] - s.fromY) * p;
            s.turn = s.fromTurn + (stickers[i].turn - s.fromTurn) * p;
            s.scale = s.fromScale + (1 - s.fromScale) * p;
          }
          return list;
        });
        if (t >= TIDY_TIME && tidy.get() < TIDY_TIME) {
          const list = live.get();
          for (let i = 0; i < list.length; i += 1) if (list[i].held && !list[i].byUser) release(i, 0, 0);
        }
        if (t >= TIDY_TIME + TIDY_REST) {
          tidy.set(-1);
          script.set(0);
          // The loop starts again with an empty bin.
          bin.modify((b) => {
            'worklet';
            b.count = 0;
            b.prev = 0;
            b.roll = 1;
            b.pending = 0;
            return b;
          });
        } else {
          tidy.set(t);
        }
      } else {
        const was = script.get();
        let t = was + dt;
        if (t >= LOOP) {
          t -= LOOP;
          bin.modify((b) => {
            'worklet';
            b.count = 0;
            b.prev = 0;
            b.roll = 1;
            b.pending = 0;
            return b;
          });
        }
        const crossed = (at: number) => {
          'worklet';
          return t >= was ? was < at && t >= at : was < at || t >= at;
        };
        for (let m = 0; m < MOVES.length; m += 1) {
          const move = MOVES[m];
          if (move.index >= count) continue;
          if (crossed(move.at)) {
            bringUp(move.index);
            live.modify((list) => {
              'worklet';
              list[move.index].held = true;
              list[move.index].byUser = false;
              list[move.index].falling = 0;
              return list;
            });
          }
          if (t >= move.at && t <= move.end) {
            // Where the sticker was before this move: its last move's end, or home.
            let fx = 0;
            let fy = 0;
            let fTurn = 0;
            let fScale = 1;
            for (let q = 0; q < m; q += 1) {
              if (MOVES[q].index === move.index) {
                fx = MOVES[q].dx;
                fy = MOVES[q].dy;
                fTurn = MOVES[q].turn;
                fScale = MOVES[q].scale;
              }
            }
            const p = easeInOut((t - move.at) / (move.end - move.at));
            const home = homeOf(move.index);
            const ex = (move.dx - fx) * k;
            const ey = (move.dy - fy) * k;
            const len = Math.max(1, Math.hypot(ex, ey));
            const bulge = Math.sin(Math.PI * p) * ARC * k;
            live.modify((list) => {
              'worklet';
              const s = list[move.index];
              s.x = home[0] + fx * k + ex * p + (-ey / len) * bulge;
              s.y = home[1] + fy * k + ey * p + (ex / len) * bulge;
              s.turn = stickers[move.index].turn + fTurn + (move.turn - fTurn) * p;
              s.scale = fScale + (move.scale - fScale) * p;
              return list;
            });
          }
          if (crossed(move.end)) release(move.index, 0, 0);
        }
        // The peel: press, lift the corner, pull it off, carry it over the bin, let go.
        if (PEEL_INDEX < count) {
          if (crossed(PEEL_PRESS)) {
            if (live.get()[PEEL_INDEX].gone < 0 && active.get() < 0) pickUp(PEEL_INDEX, false);
          }
          if (crossed(PEEL_START) && active.get() === PEEL_INDEX) {
            const s = live.get()[PEEL_INDEX];
            const def = stickers[PEEL_INDEX];
            const corner = toScreen(s, k, def.size[0], def.size[1]);
            startPeel(PEEL_INDEX, corner[0], corner[1], false);
          }
          const p = peel.get();
          if (p.index === PEEL_INDEX && !p.byUser) {
            if (t >= PEEL_START && t < PEEL_PULLED) {
              // The finger pulls the corner back across the sticker, past its middle.
              const e = easeInOut((t - PEEL_START) / (PEEL_PULLED - PEEL_START));
              const s = live.get()[PEEL_INDEX];
              const lx = p.cx + (-PEEL_REACH * p.cx - p.cx) * e;
              const ly = p.cy + (-PEEL_REACH * p.cy - p.cy) * e;
              const home = homeOf(PEEL_INDEX);
              const f = p.detached ? toScreen({ x: home[0], y: home[1], turn: stickers[PEEL_INDEX].turn, scale: 1 }, k, lx, ly) : toScreen(s, k, lx, ly);
              peel.set({ ...p, fx: f[0], fy: f[1] });
            }
            if (crossed(PEEL_PULLED)) scriptFinger.set([p.fx, p.fy]);
            if (t >= PEEL_PULLED && t < PEEL_DROP && p.detached) {
              // Carried on a curve to just off the bin's mouth, where the magnet takes it.
              const e = easeInOut(clamp01((t - PEEL_PULLED) / (PEEL_CARRIED - PEEL_PULLED)));
              const a = scriptFinger.get();
              const h = hoverPoint();
              const bx = h[0] + CARRY_TO[0] * k - p.anchorX;
              const by = h[1] + CARRY_TO[1] * k - p.anchorY;
              const cx = (a[0] + bx) / 2 + 40 * k;
              const cy = Math.min(a[1], by) - 60 * k;
              const u = 1 - e;
              peel.set({ ...p, fx: u * u * a[0] + 2 * u * e * cx + e * e * bx, fy: u * u * a[1] + 2 * u * e * cy + e * e * by });
            }
            if (crossed(PEEL_DROP)) releasePeel(0, 0);
          }
        }
        script.set(t);
      }
    }

    // The peel: a lifted corner comes off once pulled far enough; a released flap lies back down.
    let p = peel.get();
    if (p.index >= count) {
      peel.set(NO_PEEL);
      p = NO_PEEL;
    }
    if (p.index >= 0 && p.byUser && !peeling.get() && p.flatten < 0) {
      // A user's peel whose finger has gone (the gestures can end in any order) is let go of,
      // so the board can never be left holding a flap.
      const s = live.get()[p.index];
      if (p.detached) {
        peel.set(NO_PEEL);
        release(p.index, 0, 0);
      } else {
        const f = toLocal(s, k, p.fx, p.fy);
        peel.set({ ...p, flatten: 0, flatX: f[0], flatY: f[1] });
      }
      if (active.get() === p.index) active.set(-1);
      p = peel.get();
    }
    if (p.index >= 0 && p.index < count) {
      const i = p.index;
      const s = live.get()[i];
      if (p.flatten >= 0) {
        const f = p.flatten + dt;
        if (f >= FLATTEN_TIME) {
          if (p.detached) {
            peel.set({ ...p, flatten: -1, flatX: p.cx, flatY: p.cy });
          } else {
            peel.set(NO_PEEL);
            release(i, 0, 0);
          }
        } else {
          peel.set({ ...p, flatten: f });
        }
      } else if (!p.detached) {
        const f = toLocal(s, k, p.fx, p.fy);
        if (Math.hypot(f[0] - p.cx, f[1] - p.cy) > DETACH * extents[i]) {
          peel.set({ ...p, detached: true, flatten: 0, flatX: f[0], flatY: f[1], anchorX: s.x - p.fx, anchorY: s.y - p.fy });
          if (p.byUser) scheduleOnRN(thud, 'rip');
        }
      }
    }
    const q = peel.get();

    // A loose sticker near the bin: how near, whether the bin has it, and its pull on it.
    const hover = hoverPoint();
    const loose = q.index >= 0 && q.index < count && q.detached;
    const tx = loose ? q.fx + q.anchorX : hover[0];
    const ty = loose ? q.fy + q.anchorY : hover[1];
    // Near when either the sticker or the finger holding its corner is near.
    const dist = loose ? Math.min(Math.hypot(tx - hover[0], ty - hover[1]), Math.hypot(q.fx - hover[0], q.fy - hover[1])) : 0;
    const near = loose ? clamp01((APPROACH * k - dist) / ((APPROACH - REACH) * k)) : 0;
    // Results of the modifiers below are written into this object, which they share by reference.
    const out = { snap: false, landed: false, ballIn: 0, ballByUser: false, tossing: false, slammed: false };
    bin.modify((b) => {
      'worklet';
      const was = b.snapped;
      if (!loose) b.snapped = false;
      else if (dist < REACH * k) b.snapped = true;
      else if (dist > REACH * REACH_OUT * k) b.snapped = false;
      if (b.snapped && !was) out.snap = true;
      if (reducedMotion) {
        b.magnet = b.snapped ? 1 : 0;
        b.magnetV = 0;
      } else {
        const m = spring(b.magnet, b.magnetV, b.snapped ? 1 : 0, MAGNET_STIFFNESS, MAGNET_DAMPING, dt);
        b.magnet = m[0];
        b.magnetV = m[1];
      }
      return b;
    });
    if (out.snap && q.byUser) scheduleOnRN(thud, 'snap');
    const magnet = loose ? bin.get().magnet : 0;

    // Every sticker: carried ones lift and follow; dropped ones glide, fall and slap down; thrown
    // ones crumple, drop in and come back later.
    live.modify((list) => {
      'worklet';
      for (let i = 0; i < list.length; i += 1) {
        const s = list[i];
        if (s.rise < 1) {
          s.rise = Math.min(1, s.rise + dt / RISE_TIME);
          if (s.rise >= 1) s.riseUnder = -1;
        }
        if (s.gone >= 0) {
          const before = s.gone;
          s.gone += dt;
          if (before < TOSS_END && s.gone >= TOSS_END) {
            out.ballIn += 1;
            if (s.goneByUser) out.ballByUser = true;
          } else if (s.gone < TOSS_END) {
            out.tossing = true;
          }
          if (s.gone >= RESPAWN) {
            const home = homeOf(i);
            s.gone = -1;
            s.x = home[0];
            s.y = home[1];
            s.turn = stickers[i].turn;
            s.scale = 1;
            s.lift = 1;
            s.sway = 0;
            s.held = false;
            s.falling = 0;
            s.spawn = 0;
            s.squash = 0;
            s.squashSpeed = 0;
          }
          continue;
        }
        if (s.spawn >= 0) {
          s.spawn += dt;
          s.lift = 1 - clamp01(s.spawn / SPAWN_TIME);
          if (s.spawn >= SPAWN_TIME) {
            s.spawn = -1;
            s.lift = 0;
            s.squash = reducedMotion ? 0 : SQUASH;
            s.squashSpeed = 0;
          }
          continue;
        }
        const peeled = q.index === i;
        if (peeled && q.detached) {
          // Off the board: it follows the finger, held by the point it was pulled from, and is
          // pulled towards the bin's mouth once the bin has it.
          const gx = tx + (hover[0] - tx) * MAGNET_PULL * magnet;
          const gy = ty + (hover[1] - ty) * MAGNET_PULL * magnet;
          const follow = 1 - Math.exp(-dt / FOLLOW);
          s.vx = ((gx - s.x) * follow) / dt;
          s.x += (gx - s.x) * follow;
          s.y += (gy - s.y) * follow;
          s.lift += (DETACHED_LIFT - s.lift) * (1 - Math.exp(-dt / LIFT_TIME));
        } else if (s.held) {
          const target = peeled ? 0.15 : 1;
          s.lift += (target - s.lift) * (1 - Math.exp(-dt / LIFT_TIME));
        } else if (s.falling > 0) {
          if (!reducedMotion) {
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            const keep = Math.exp(-dt / GLIDE);
            s.vx *= keep;
            s.vy *= keep;
          }
          s.lift = Math.max(0, s.lift - dt / FALL_TIME);
          if (s.lift <= 0) {
            s.falling = 0;
            s.squash = reducedMotion ? 0 : SQUASH;
            s.squashSpeed = 0;
            s.vx = 0;
            s.vy = 0;
            if (s.byUser) out.landed = true;
          }
        }
        // It swings with its speed while carried, and settles when put down.
        const swayTo = s.held || (peeled && q.detached) ? Math.max(-SWAY_MAX, Math.min(SWAY_MAX, s.vx * SWAY_PER_SPEED)) : 0;
        s.sway += (swayTo - s.sway) * (1 - Math.exp(-dt / 0.12));
        if (!Number.isFinite(s.sway)) s.sway = 0;
        if (s.squash !== 0 || s.squashSpeed !== 0) {
          const sq = spring(s.squash, s.squashSpeed, 0, SQUASH_STIFFNESS, SQUASH_DAMPING, dt);
          s.squash = sq[0];
          s.squashSpeed = sq[1];
          if (Math.abs(s.squash) < 0.0005 && Math.abs(s.squashSpeed) < 0.01) {
            s.squash = 0;
            s.squashSpeed = 0;
          }
        }
      }
      return list;
    });
    if (out.landed) scheduleOnRN(thud, 'drop');

    // The bin: up while a corner is lifted or a ball is on its way; it leans towards a loose sticker,
    // grows a little, opens its lid as the sticker nears, and slams it once the last ball is in.
    bin.modify((b) => {
      'worklet';
      const tossing = out.tossing;
      if (out.ballIn > 0) {
        b.landed = true;
        b.pending += out.ballIn;
        if (out.ballByUser) b.byUser = true;
      }
      if (b.landed && !tossing) {
        b.landed = false;
        b.slam = true;
        b.slamHit = false;
      }
      if (b.slam) {
        const l = reducedMotion ? [0, -1] : spring(b.lid, b.lidV, 0, SLAM[0], SLAM[1], dt);
        b.lid = l[0];
        b.lidV = l[1];
        if (b.lid <= 0 && b.lidV <= 0) {
          b.lid = 0;
          b.lidV = reducedMotion ? 0 : -b.lidV * LID_BOUNCE;
          if (!b.slamHit) {
            b.slamHit = true;
            out.slammed = b.byUser;
            b.byUser = false;
            b.prev = b.count;
            b.count = (b.count + Math.max(1, b.pending)) % 100;
            b.pending = 0;
            b.roll = reducedMotion ? 1 : 0;
            b.linger = LINGER;
            if (!reducedMotion) {
              b.squashV += BIN_SQUASH_KICK;
              b.wobbleV += b.count % 2 === 0 ? WOBBLE_KICK : -WOBBLE_KICK;
            }
          }
        }
        if (b.slamHit && b.lid < 0.004 && Math.abs(b.lidV) < 0.06) {
          b.slam = false;
          b.lid = 0;
          b.lidV = 0;
        }
      } else {
        const open = tossing || b.landed || b.snapped ? 1 : Math.pow(near, 1.3) * 0.85;
        if (reducedMotion) {
          b.lid = open;
          b.lidV = 0;
        } else {
          const l = spring(b.lid, b.lidV, open, LID[0], LID[1], dt);
          b.lid = l[0];
          b.lidV = l[1];
          if (b.lid < 0) {
            b.lid = 0;
            b.lidV = 0;
          }
        }
      }
      if (!b.slam && b.linger > 0) b.linger = Math.max(0, b.linger - dt);
      const up = q.index >= 0 || tossing || b.landed || b.slam || b.linger > 0 ? 1 : 0;
      if (reducedMotion) {
        b.show += (up - b.show) * (1 - Math.exp(-dt / 0.12));
        b.showV = 0;
      } else {
        const sh = spring(b.show, b.showV, up, SHOW[0], SHOW[1], dt);
        b.show = sh[0];
        b.showV = sh[1];
        const lean = near * Math.max(-1, Math.min(1, ((tx + q.fx) / 2 - hover[0]) / (120 * k))) * LEAN_MAX;
        const ln = spring(b.lean, b.leanV, loose ? lean : 0, LEAN[0], LEAN[1], dt);
        b.lean = ln[0];
        b.leanV = ln[1];
        const gr = spring(b.grow, b.growV, 1 + GROW_NEAR * near + GROW_MAGNET * magnet, GROW[0], GROW[1], dt);
        b.grow = gr[0];
        b.growV = gr[1];
        const sq = spring(b.squash, b.squashV, 0, BIN_SQUASH[0], BIN_SQUASH[1], dt);
        b.squash = Math.max(-0.2, Math.min(0.25, sq[0]));
        b.squashV = sq[1];
        const wb = spring(b.wobble, b.wobbleV, 0, WOBBLE[0], WOBBLE[1], dt);
        b.wobble = Math.max(-12, Math.min(12, wb[0]));
        b.wobbleV = wb[1];
      }
      if (b.roll < 1) b.roll = Math.min(1, b.roll + dt / ROLL_TIME);
      return b;
    });
    if (out.slammed) scheduleOnRN(thud, 'slam');
  });

  const move = Gesture.Pan()
    .minDistance(4)
    .averageTouches(true)
    .onBegin((event) => {
      touched();
      grab(event.x, event.y, true);
    })
    .onStart(() => {
      started.set(true);
      const i = active.get();
      if (i < 0 || peel.get().index >= 0 || i >= live.get().length) return;
      const s = live.get()[i];
      dragFrom.set([s.x, s.y]);
    })
    .onUpdate((event) => {
      const i = active.get();
      if (i < 0 || peel.get().index >= 0 || i >= live.get().length) return;
      const room = stage.get();
      const from = dragFrom.get();
      live.modify((list) => {
        'worklet';
        const s = list[i];
        s.x = rubber(from[0] + event.translationX, 12, room.width - 12, 40);
        s.y = rubber(from[1] + event.translationY, room.top + 12, room.height - room.bottom - 12, 40);
        s.vx = event.velocityX;
        s.vy = event.velocityY;
        return list;
      });
    })
    .onEnd((event) => {
      if (peel.get().index < 0) release(active.get(), event.velocityX, event.velocityY);
    })
    .onFinalize(() => {
      // A drag that ended without onEnd still puts its sticker down.
      if (started.get() && peel.get().index < 0) release(active.get(), 0, 0);
      touched();
      pending.set(PENDING_FRAMES);
    });

  const pinch = Gesture.Pinch()
    .onBegin((event) => {
      touched();
      grab(event.focalX, event.focalY, true);
    })
    .onStart(() => {
      started.set(true);
      const i = active.get();
      if (i >= 0 && i < live.get().length) scaleFrom.set(live.get()[i].scale);
    })
    .onUpdate((event) => {
      const i = active.get();
      if (i < 0 || peel.get().index >= 0 || i >= live.get().length || !Number.isFinite(event.scale)) return;
      live.modify((list) => {
        'worklet';
        list[i].scale = rubber(scaleFrom.get() * event.scale, MIN_SCALE, MAX_SCALE, 0.25);
        return list;
      });
    })
    .onEnd(() => {
      // Past a limit, the scale springs back inside it as the sticker lands.
      const i = active.get();
      if (i < 0 || i >= live.get().length) return;
      live.modify((list) => {
        'worklet';
        list[i].scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, list[i].scale));
        return list;
      });
    });

  const rotate = Gesture.Rotation()
    .onBegin((event) => {
      touched();
      grab(event.anchorX, event.anchorY, true);
    })
    .onStart(() => {
      started.set(true);
      const i = active.get();
      if (i >= 0 && i < live.get().length) turnFrom.set(live.get()[i].turn);
    })
    .onUpdate((event) => {
      const i = active.get();
      if (i < 0 || peel.get().index >= 0 || i >= live.get().length || !Number.isFinite(event.rotation)) return;
      live.modify((list) => {
        'worklet';
        list[i].turn = turnFrom.get() + (event.rotation * 180) / Math.PI;
        return list;
      });
    });

  const lift = Gesture.Pan()
    .activateAfterLongPress(LONG_PRESS)
    .onBegin((event) => {
      touched();
      grab(event.x, event.y, true);
    })
    .onStart((event) => {
      const i = active.get();
      // A sticker already being dragged, pinched or turned is not peeled as well.
      if (i < 0 || peel.get().index >= 0 || started.get() || !Number.isFinite(event.x) || !Number.isFinite(event.y)) return;
      startPeel(i, event.x, event.y, true);
      scheduleOnRN(thud, 'lift');
    })
    .onUpdate((event) => {
      const p = peel.get();
      if (p.index < 0 || !p.byUser || !peeling.get()) return;
      if (p.flatten >= 0 && !p.detached) return;
      if (!Number.isFinite(event.x) || !Number.isFinite(event.y)) return;
      peel.set({ ...p, fx: event.x, fy: event.y });
    })
    .onEnd((event) => {
      if (peel.get().byUser) releasePeel(event.velocityX, event.velocityY);
    })
    .onFinalize(() => {
      // However the gesture ended, a peel still held is let go.
      if (peel.get().byUser) releasePeel(0, 0);
      touched();
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, room.width), Math.max(1, room.height)));
    const list = live.get();
    if (room.width <= 0 || list.length !== count) return recorder.finishRecordingAsPicture();
    const k = room.width / DESIGN_WIDTH;
    const now = elapsed.get();
    const images = loaded.get();
    const p = peel.get();
    const b = bin.get();

    // A strip of masking tape across the top, with the board's name in marker.
    canvas.save();
    canvas.translate(room.width / 2, room.top + 30 * k);
    canvas.rotate(-2.5, 0, 0);
    const tape = Skia.Paint();
    tape.setAntiAlias(true);
    tape.setColor(Skia.Color('rgba(244, 233, 196, 0.92)'));
    const tw = 184 * k;
    const th = 30 * k;
    const jag = Skia.PathBuilder.Make().moveTo(-tw / 2, -th / 2).lineTo(tw / 2, -th / 2);
    for (let j = 0; j <= 6; j += 1) jag.lineTo(tw / 2 + (j % 2 === 0 ? 0 : -3 * k), -th / 2 + (th * j) / 6);
    jag.lineTo(-tw / 2, th / 2);
    for (let j = 6; j >= 0; j -= 1) jag.lineTo(-tw / 2 + (j % 2 === 0 ? 0 : 3 * k), -th / 2 + (th * j) / 6);
    const tapePath = jag.close().build();
    const tapeShadow = Skia.Paint();
    tapeShadow.setColor(Skia.Color('rgba(60, 30, 5, 0.25)'));
    tapeShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, false));
    canvas.save();
    canvas.translate(0, 1.5);
    canvas.drawPath(tapePath, tapeShadow);
    canvas.restore();
    canvas.drawPath(tapePath, tape);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#3f2d1b'));
    const tw2 = fonts.hand.measureText(title).width;
    canvas.drawText(title, -tw2 / 2, 6, ink, fonts.hand);
    canvas.restore();

    const shrink = 1 - MAGNET_SHRINK * clamp01(b.magnet);
    const drawOne = (i: number, fade: number) => {
      'worklet';
      const s = list[i];
      const def = stickers[i];
      const sq = s.squash;
      const grow = reducedMotion ? 1 : 1 + LIFT_GROW * Math.min(s.lift, 1.4);
      const peeled = p.index === i;
      const base = k * s.scale * grow * (peeled && p.detached ? shrink : 1);
      const turn = s.turn + s.sway;
      let alpha = fade;
      let extra = 1;
      if (s.spawn >= 0) {
        const e = clamp01(s.spawn / SPAWN_TIME);
        alpha *= e;
        extra = reducedMotion ? 1 : 1 + 0.25 * (1 - easeOut(e));
      }
      if (alpha < 0.004) return;
      const layer = alpha < 0.995;
      if (layer) {
        const paint = Skia.Paint();
        paint.setAlphaf(alpha);
        canvas.saveLayer(paint);
      }
      canvas.save();
      canvas.translate(s.x, s.y);
      canvas.rotate(turn, 0, 0);
      canvas.scale(base * extra * (1 + sq), base * extra * (1 - sq));
      let cx = 0;
      let cy = 0;
      let fx = 0;
      let fy = 0;
      if (peeled) {
        cx = p.cx;
        cy = p.cy;
        if (p.flatten >= 0) {
          const e = easeOut(clamp01(p.flatten / FLATTEN_TIME));
          fx = p.flatX + (p.cx - p.flatX) * e;
          fy = p.flatY + (p.cy - p.flatY) * e;
        } else if (p.detached) {
          fx = p.cx;
          fy = p.cy;
        } else {
          const f = toLocal(s, k, p.fx, p.fy);
          fx = f[0];
          fy = f[1];
        }
      }
      const fold = Math.hypot(fx - cx, fy - cy);
      if (peeled && fold > 0.5) {
        // The shadow of the part still stuck down, then the face with its flap.
        const nx = (fx - cx) / fold;
        const ny = (fy - cy) / fold;
        canvas.save();
        canvas.clipPath(keepSide((cx + fx) / 2, (cy + fy) / 2, nx, ny), ClipOp.Intersect, true);
        drawShadow(canvas, outlines[i], s.lift, turn, base, alpha);
        canvas.restore();
        const face = () => {
          'worklet';
          drawFace(canvas, def, arts[i], outlines[i], extents[i], images[i] ?? null, now, turn, fonts);
        };
        drawPeeled(canvas, outlines[i], cx, cy, fx, fy, face, base);
      } else {
        drawShadow(canvas, outlines[i], s.lift, turn, base, alpha);
        drawFace(canvas, def, arts[i], outlines[i], extents[i], images[i] ?? null, now, turn, fonts);
      }
      canvas.restore();
      if (layer) canvas.restore();
    };

    const stack = order.get();
    for (let j = 0; j < stack.length; j += 1) {
      const i = stack[j];
      if (i < 0 || i >= list.length) continue;
      // A sticker rising out of the pile keeps a copy right under the one that covered it.
      for (let r = 0; r < list.length; r += 1) {
        const up = list[r];
        if (up.rise < 1 && up.riseUnder === i && up.gone < 0 && !(p.index === r && p.detached)) drawOne(r, 1);
      }
      if (list[i].gone >= 0) continue;
      if (p.index === i && p.detached) continue;
      drawOne(i, list[i].rise < 1 ? easeInOut(list[i].rise) : 1);
    }

    // The bin, a ball dropping into it, a sticker crumpling over it, and a loose one above it all.
    const frame = binFrame(b, k, room.width, room.height, room.bottom, reducedMotion);
    const inside = () => {
      'worklet';
      if (reducedMotion) return;
      for (let i = 0; i < list.length; i += 1) {
        const s = list[i];
        if (s.gone < CRUMPLE || s.gone >= TOSS_END) continue;
        const e = clamp01((s.gone - CRUMPLE) / DROP);
        const y0 = -(BIN_BODY + BIN_HOVER);
        const y1 = -BIN_BODY + BIN_MOUTH_DROP + 6;
        const turn = s.goneTurn + BALL_SPIN + 120 * e;
        canvas.save();
        canvas.translate(0, y0 + (y1 - y0) * e * e - Math.sin(Math.PI * e) * BALL_HOP);
        canvas.scale(1 / Math.max(0.0001, frame.sx), 1 / Math.max(0.0001, frame.sy));
        canvas.rotate(turn - frame.rot, 0, 0);
        drawCrumpled(canvas, stickers[i], arts[i], outlines[i], extents[i], rings[i], balls[i], shades[i], images[i] ?? null, now, fonts, 1, k * s.goneScale, BALL * k * (1 - 0.15 * e), turn);
        canvas.restore();
      }
    };
    drawBin(canvas, binArt, frame, b, fonts.digits, k, inside);

    for (let i = 0; i < list.length; i += 1) {
      const s = list[i];
      if (s.gone < 0 || s.gone >= CRUMPLE) continue;
      const e = clamp01(s.gone / CRUMPLE);
      if (reducedMotion) {
        // No crumple: it fades where it was let go.
        const paint = Skia.Paint();
        paint.setAlphaf(1 - e);
        canvas.saveLayer(paint);
        canvas.save();
        canvas.translate(s.goneX, s.goneY);
        canvas.rotate(s.goneTurn, 0, 0);
        canvas.scale(k * s.goneScale, k * s.goneScale);
        drawFace(canvas, stickers[i], arts[i], outlines[i], extents[i], images[i] ?? null, now, s.goneTurn, fonts);
        canvas.restore();
        canvas.restore();
        continue;
      }
      const c = easeInOut(e);
      const m = easeOut(e);
      const to = binPoint(frame, 0, -(BIN_BODY + BIN_HOVER));
      const x = s.goneX + (to[0] - s.goneX) * m;
      const y = s.goneY + (to[1] - s.goneY) * m;
      const turn = s.goneTurn + BALL_SPIN * c;
      const from = k * s.goneScale;
      const radius = BALL * k;
      canvas.save();
      canvas.translate(x, y);
      canvas.rotate(turn, 0, 0);
      // Its shadow on the board, shrinking with it.
      const shadow = Skia.Paint();
      shadow.setAntiAlias(true);
      shadow.setColor(Skia.Color('#2a1505'));
      shadow.setAlphaf(0.3);
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7 * k, true));
      const a = (-turn * Math.PI) / 180;
      const ox = 5 * k;
      const oy = 14 * k;
      canvas.save();
      canvas.translate(ox * Math.cos(a) - oy * Math.sin(a), ox * Math.sin(a) + oy * Math.cos(a));
      canvas.drawPath(crumplePath(rings[i], balls[i], c, from, radius), shadow);
      canvas.restore();
      drawCrumpled(canvas, stickers[i], arts[i], outlines[i], extents[i], rings[i], balls[i], shades[i], images[i] ?? null, now, fonts, c, from, radius, turn);
      canvas.restore();
    }
    if (p.index >= 0 && p.index < list.length && p.detached && list[p.index].gone < 0) drawOne(p.index, 1);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(lift, Gesture.Simultaneous(move, pinch, rotate))}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel={`A cork board with ${count} stickers. Drag, pinch and turn a sticker with two fingers; press and hold to peel one off, and let go of it over the bin to throw it away.`}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>
            <Shader source={CORK} uniforms={{ size: [Math.max(1, width), Math.max(1, height)] }} />
          </Fill>
        </Canvas>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#8a6440',
  },
});
