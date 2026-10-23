/**
 * A transaction. Swipe right and keep pulling: every 60 pt past the first 72 adds a person and the row's bar breaks
 * into equal shares, one selection tick per break; let go on two or more and the shares heal into one row with your
 * share, the others flying off as a request. Swipe left for Flag and Delete (iOS Mail's layer). The finger, the demo
 * script and the accessibility actions all call the same worklets.
 */
import { memo, useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  FadeIn,
  Keyframe,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PIECE_SCALE, type FlightApi, type Point } from './Flight';
import { Glyph } from './Glyphs';
import { Monogram } from './Monogram';
import { EASE_OUT, SPLIT, SWIPE, TIMING } from './motion';
import { RollText } from './RollText';
import { RowActions } from './RowActions';
import { avatarIn, BAR, compactness } from './split-geometry';
import { frameOf, SplitBar, SplitHud, type RowMotion } from './SplitBar';
import { playSwipeHaptic } from './swipe-haptics';
import { COLOR, DIDONE_DROP, DIDONE_ROOM, LAYOUT, OPEN_X, TYPE } from './theme';
import { MAX_PEOPLE, money, moneyText, others, PEOPLE, shares, type Transaction } from './transactions';

/** The demo's finger: one event per frame, for the row named by `key`. */
export type ScriptTouch = { seq: number; key: string; kind: 'down' | 'move' | 'up' | 'tap' | 'cancel'; dx: number; vx: number; x: number };
/** Work the screen asks a row to do on the UI thread (Undo, accessibility). */
/** `key` '*' addresses every row (the demo's restart: 'reset'). */
export type RowCommand = { seq: number; key: string; kind: 'split' | 'delete' | 'close' | 'reset'; n: number };

type Props = {
  item: Transaction;
  top: number; // the slot's top inside the sheet
  screenTop: number; // the slot's top on the screen, for the avatars' flights
  rowLeft: number; // the sheet's left edge on the screen
  split: number; // people the bill is split with (0: not split)
  flagged: boolean;
  hidden: boolean; // below the rows the sheet shows right now (the toast takes the room)
  first: boolean; // the first row of the ledger: no rule over it (the ledger's own top rule is there)
  leaving: boolean; // deleted, its slot folding
  restoring: boolean; // put back by Undo
  hudBelow: boolean;
  instant: boolean; // jump to the slot (the demo's restart), no slide // the first row has no row above it for the HUD to ride over
  width: number;
  reduceMotion: boolean;
  activeKey: SharedValue<string>;
  dim: SharedValue<number>;
  script: SharedValue<ScriptTouch>;
  command: SharedValue<RowCommand>;
  flight: FlightApi;
  onSplit: (key: string, n: number, silent: boolean) => void;
  onDelete: (key: string, silent: boolean) => void;
  onFlag: (key: string) => void;
  onUndoSplit: (key: string) => void;
};

const IDLE = 0;
const PRESSED = 1;
const SPLITTING = 2;
const REVEALING = 3;
const BUSY = 4;
const OPEN = 5;

const NEVER = { reduceMotion: ReduceMotion.Never };
const NONE: Point = { x: 0, y: 0, s: 1 };
const DELETE_X = BAR - LAYOUT.tile; // Delete's left edge in the row, when open
const FLAG_X = DELETE_X - LAYOUT.tile;
const LAST_DETENT = SPLIT.detentStart + (MAX_PEOPLE - 2) * SPLIT.detentStep;

function clamp01(v: number): number {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
}

function detentPull(n: number): number {
  'worklet';
  return SPLIT.detentStart + (n - 2) * SPLIT.detentStep;
}

/** The people the pull asks for, holding the current count until the pull falls `hysteresis` below its detent. */
function detentFor(pull: number, current: number): number {
  'worklet';
  let n = 1;
  for (let k = 2; k <= MAX_PEOPLE; k++) if (pull >= detentPull(k)) n = k;
  if (n < current && current >= 2 && pull > detentPull(current) - SPLIT.hysteresis) return current;
  return n;
}

export const SwipeRow = memo(function SwipeRow(props: Props) {
  const {
    item, top, screenTop, rowLeft, split, flagged, hidden, first, leaving, restoring, hudBelow, instant, width, reduceMotion,
    activeKey, dim, script, command, flight, onSplit, onDelete, onFlag, onUndoSplit,
  } = props;
  const { key } = item;

  const mode = useSharedValue(IDLE);
  const pull = useSharedValue(0);
  const tx = useSharedValue(0);
  const startTx = useSharedValue(0);
  const lift = useSharedValue(0);
  const onHold = useSharedValue(0);
  const veilHold = useSharedValue(0);
  const count = useSharedValue(1);
  const target = useSharedValue(1);
  const prevTarget = useSharedValue(1);
  const dir = useSharedValue(0);
  const rollP = useSharedValue(1);
  const trail = useSharedValue(0);
  const close = useSharedValue(0);
  const contentOut = useSharedValue(0);
  const armed = useSharedValue(0);
  const armP = useSharedValue(0);
  const press = useSharedValue(0);
  const panned = useSharedValue(false); // this touch moved the row: it is not a tap
  const kick = useSharedValue(0); // a timer on the UI thread
  const y = useSharedValue(top);
  const shown = useSharedValue(hidden ? 0 : 1);
  const fold = useSharedValue(0);
  const enter = useSharedValue(restoring ? 0 : 1);
  const amountY = useSharedValue(split >= 2 ? 1 : 0); // 1: the amount moves up for "of $96.60" under it

  const piecesOn = useDerivedValue(() => Math.max(clamp01((pull.get() - SPLIT.piecesIn[0]) / (SPLIT.piecesIn[1] - SPLIT.piecesIn[0])), onHold.get()));
  const veil = useDerivedValue(() => Math.max(clamp01((pull.get() - SPLIT.contentFade[0]) / (SPLIT.contentFade[1] - SPLIT.contentFade[0])), veilHold.get()));
  const hud = useSharedValue(0); // the HUD arrives with the first break and leaves with the release
  const m: RowMotion = useMemo(
    () => ({ pull, tx, lift, piecesOn, veil, hud, count, target, prevTarget, dir, rollP, trail, close, contentOut }),
    [close, contentOut, count, dir, hud, lift, piecesOn, prevTarget, pull, rollP, target, trail, tx, veil],
  );

  // The row moves to its slot when one above it is deleted or restored; shows or hides when the toast takes room.
  // The demo's restart puts every row back at once (`instant`): sliding them would cross a re-added row.
  useEffect(() => { y.set(instant ? top : withTiming(top, { duration: reduceMotion ? TIMING.reduced : TIMING.sheet, easing: EASE_OUT, ...NEVER })); }, [instant, reduceMotion, top, y]);
  // Hidden fast when the toast takes the room; shown again once the sheet has grown under the row.
  useEffect(() => {
    shown.set(hidden
      ? withTiming(0, { duration: TIMING.toastOut * 0.7, easing: EASE_OUT })
      : withDelay(TIMING.sheet / 2, withTiming(1, { duration: TIMING.sheet - TIMING.toastOut, easing: EASE_OUT })));
  }, [hidden, shown]);
  useEffect(() => { amountY.set(withTiming(split >= 2 ? 1 : 0, { duration: reduceMotion ? TIMING.reduced : 300, easing: EASE_OUT, ...NEVER })); }, [amountY, reduceMotion, split]);
  useEffect(() => {
    if (leaving) fold.set(withTiming(1, { duration: reduceMotion ? TIMING.reduced : SWIPE.fold, easing: EASE_OUT, ...NEVER }));
  }, [fold, leaving, reduceMotion]);
  useEffect(() => {
    if (!restoring) return;
    tx.set(0);
    armP.set(0);
    armed.set(0);
    lift.set(0);
    mode.set(IDLE);
    fold.set(0);
    enter.set(0);
    // It arrives once the rows below have mostly made room (ease-out: ~90 % of the move in the first 60 %).
    enter.set(withDelay(reduceMotion ? 0 : TIMING.sheet * 0.6, withTiming(1, { duration: reduceMotion ? TIMING.reduced : TIMING.toastIn, easing: EASE_OUT, ...NEVER })));
  }, [armP, armed, enter, fold, lift, mode, reduceMotion, restoring, tx]);

  const api = useMemo(() => {
    const anim = (to: number, config: { duration: number; dampingRatio: number }, velocity = 0, done?: () => void) => {
      'worklet';
      const finish = (finished?: boolean) => {
        'worklet';
        if (finished && done) done();
      };
      return reduceMotion
        ? withTiming(to, { duration: TIMING.reduced, easing: EASE_OUT, ...NEVER }, finish)
        : withSpring(to, { ...config, velocity }, finish);
    };

    const fadeTo = (to: number, duration: number, delay = 0, done?: () => void) => {
      'worklet';
      const t = withTiming(to, { duration: reduceMotion ? Math.min(duration, TIMING.reduced) : duration, easing: EASE_OUT, ...NEVER }, (finished) => {
        if (finished && done) done();
      });
      return delay > 0 ? withDelay(delay, t) : t;
    };

    const setTarget = (n: number, silent: boolean) => {
      'worklet';
      const was = target.get();
      if (n === was) return;
      prevTarget.set(was);
      dir.set(n > was ? 1 : -1);
      target.set(n);
      rollP.set(0);
      rollP.set(withTiming(1, { duration: reduceMotion ? TIMING.reducedSwap : SPLIT.roll, easing: EASE_OUT, ...NEVER }));
      count.set(reduceMotion ? withTiming(n, { duration: TIMING.reducedCount, easing: EASE_OUT, ...NEVER }) : withSpring(n, SPLIT.count));
      if (mode.get() === SPLITTING) hud.set(fadeTo(n >= 2 ? 1 : 0, n >= 2 ? SPLIT.hudIn : SPLIT.contentOut));
      if (!silent) scheduleOnRN(playSwipeHaptic, 'detent');
    };

    /** Back to rest after a split has settled (nothing of it is visible any more). */
    const restSplit = () => {
      'worklet';
      prevTarget.set(1);
      target.set(1);
      count.set(1);
      dir.set(0);
      rollP.set(1);
      close.set(0);
      contentOut.set(0);
      trail.set(0);
      mode.set(IDLE);
    };

    /** The requested people's avatars, where they are right now, on the screen. */
    const avatarsNow = (n: number) => {
      'worklet';
      const pts: Point[] = [NONE];
      for (let k = 1; k < n; k++) {
        const f = frameOf(m, k);
        const a = avatarIn(f.w, compactness(f.cs));
        pts.push({ x: rowLeft + f.x + a.x, y: screenTop + LAYOUT.inset + a.y, s: a.s * PIECE_SCALE });
      }
      return pts;
    };

    /** Release on two or more: the seams heal, your share stays, the others fly to the toast as a request. */
    const commit = (n: number, silent: boolean) => {
      'worklet';
      mode.set(BUSY);
      hud.set(fadeTo(0, SPLIT.contentOut));
      // The neighbours come back once the HUD has gone, so the row above never shows through a fading HUD.
      dim.set(fadeTo(0, SPLIT.settle, SPLIT.contentOut));
      onHold.set(1);
      veilHold.set(1);
      pull.set(fadeTo(0, 260));
      trail.set(0);
      close.set(anim(1, SPLIT.close));
      contentOut.set(fadeTo(1, SPLIT.contentOut));
      kick.set(0);
      kick.set(withDelay(TIMING.flightDelay, withTiming(1, { duration: 0 }, (finished) => {
        if (finished) flight.launch(key, n, avatarsNow(n));
      })));
      veilHold.set(fadeTo(0, 260, TIMING.flightDelay + 140)); // after the avatars have left the row
      onHold.set(fadeTo(0, SPLIT.settle, 340));
      lift.set(fadeTo(0, SPLIT.settle, 340, restSplit));
      if (!silent) scheduleOnRN(playSwipeHaptic, 'split');
      scheduleOnRN(onSplit, key, n, silent);
    };

    /** Released before the first break: everything springs back with the finger's velocity. */
    const cancelSplit = (vx: number) => {
      'worklet';
      setTarget(1, true);
      hud.set(fadeTo(0, SPLIT.contentOut));
      dim.set(fadeTo(0, SPLIT.settle, SPLIT.contentOut));
      pull.set(anim(0, SPLIT.cancel, vx));
      trail.set(0);
      lift.set(fadeTo(0, SPLIT.settle));
      mode.set(IDLE);
    };

    /** Accessibility: break the bar into n shares without a finger, then send it. */
    const splitTo = (n: number) => {
      'worklet';
      mode.set(BUSY);
      lift.set(fadeTo(1, SPLIT.lift.duration));
      onHold.set(fadeTo(1, 150));
      veilHold.set(fadeTo(1, SPLIT.contentOut));
      setTarget(n, true);
      kick.set(0);
      kick.set(withDelay(520, withTiming(1, { duration: 0 }, (finished) => {
        if (finished) commit(n, false);
      })));
    };

    const disarm = () => {
      'worklet';
      if (armed.get() === 0) return;
      armed.set(0);
      armP.set(anim(0, SWIPE.arm));
    };

    const closeRow = (vx: number) => {
      'worklet';
      disarm();
      mode.set(IDLE);
      tx.set(anim(0, SWIPE.close, vx));
      lift.set(fadeTo(0, SPLIT.settle));
    };

    const openRow = (vx: number) => {
      'worklet';
      disarm();
      mode.set(OPEN);
      tx.set(anim(OPEN_X, SWIPE.open, vx));
    };

    /** Delete: the red fill swallows the strip and the row leaves with the finger's velocity. */
    const deleteRow = (vx: number, silent: boolean) => {
      'worklet';
      mode.set(BUSY);
      armed.set(1);
      armP.set(anim(1, SWIPE.arm));
      const distance = width + LAYOUT.inset * 4 + tx.get();
      const fast = vx < -200 ? Math.min(Math.max((distance / -vx) * 1000 * 1.6, 140), SWIPE.slideOut) : SWIPE.slideOut;
      tx.set(withTiming(-(width + LAYOUT.inset * 4), { duration: reduceMotion ? TIMING.reduced : fast, easing: EASE_OUT, ...NEVER }, (finished) => {
        if (finished) scheduleOnRN(onDelete, key, silent);
      }));
    };

    const begin = () => {
      'worklet';
      activeKey.set(key);
      panned.set(false);
      if (dim.get() > 0.001 && mode.get() !== SPLITTING) dim.set(fadeTo(0, SPLIT.settle));
      if (mode.get() === IDLE) {
        mode.set(PRESSED);
        lift.set(fadeTo(1, SPLIT.lift.duration));
      }
    };

    const move = (dx: number, silent: boolean) => {
      'worklet';
      const now = mode.get();
      if (now === BUSY || now === IDLE) return;
      if (now === PRESSED && dx === 0) return; // no direction yet
      panned.set(true);
      if (now === PRESSED || now === OPEN) {
        cancelAnimation(tx);
        if (now === PRESSED && dx > 0 && Math.abs(tx.get()) < 1) {
          mode.set(SPLITTING);
        } else {
          mode.set(REVEALING);
          startTx.set(tx.get());
          lift.set(fadeTo(1, SPLIT.lift.duration));
        }
      }
      if (mode.get() === SPLITTING) {
        const raw = Math.max(dx, 0);
        const p = raw > LAST_DETENT ? LAST_DETENT + (raw - LAST_DETENT) * SPLIT.overRubber : raw;
        pull.set(p);
        const n = detentFor(p, target.get());
        setTarget(n, silent);
        const over = n >= 2 ? Math.max(p - detentPull(n), 0) : 0;
        trail.set(reduceMotion ? 0 : withSpring(Math.min(SPLIT.trailMax, over * SPLIT.trailPer), SPLIT.trail));
        return;
      }
      // Revealing: 1:1 left, resisted past 85 % of the width and to the right of rest.
      let x = startTx.get() + dx;
      if (x > 0) x *= SWIPE.rightRubber;
      const soft = -width * 0.85;
      if (x < soft) x = soft + (x - soft) * SWIPE.fullRubber;
      tx.set(x);
      const full = width * SWIPE.fullAt;
      if (-x > full && armed.get() === 0) {
        armed.set(1);
        armP.set(anim(1, SWIPE.arm));
        if (!silent) scheduleOnRN(playSwipeHaptic, 'arm');
      } else if (-x < full - SPLIT.hysteresis * 2 && armed.get() === 1) {
        disarm();
      }
    };

    const end = (vx: number, silent: boolean) => {
      'worklet';
      const now = mode.get();
      if (now === SPLITTING) {
        if (target.get() >= 2) commit(target.get(), silent);
        else cancelSplit(vx);
        return;
      }
      if (now !== REVEALING) return;
      const w = -tx.get();
      if (armed.get() === 1 || (vx < SWIPE.deleteFlick && w > width * SWIPE.deleteFlickAt)) deleteRow(vx, silent);
      else if ((w > SWIPE.openAt && vx < 300) || vx < SWIPE.openFlick) openRow(vx);
      else closeRow(vx);
    };

    /** A touch that never moved: on an open row it presses Flag or Delete, or closes it. */
    const settleTouch = () => {
      'worklet';
      if (mode.get() === PRESSED) {
        mode.set(IDLE);
        lift.set(fadeTo(0, SPLIT.settle));
      }
    };

    /**
     * The touch was taken away (cancelled by the system, or the demo's finger stopped by a real one): nothing is
     * committed. A split springs back; a reveal settles open or shut by where it is, never into a delete.
     */
    const interrupt = () => {
      'worklet';
      const now = mode.get();
      if (now === SPLITTING) cancelSplit(0);
      else if (now === REVEALING) {
        if (-tx.get() > SWIPE.openAt) openRow(0);
        else closeRow(0);
      } else settleTouch();
    };

    /** The demo's restart: every value of the row back at rest, at once. */
    const resetRow = () => {
      'worklet';
      cancelAnimation(pull);
      cancelAnimation(tx);
      cancelAnimation(lift);
      cancelAnimation(onHold);
      cancelAnimation(veilHold);
      cancelAnimation(hud);
      cancelAnimation(trail);
      cancelAnimation(close);
      cancelAnimation(contentOut);
      cancelAnimation(armP);
      cancelAnimation(count);
      cancelAnimation(rollP);
      cancelAnimation(kick);
      pull.set(0);
      tx.set(0);
      lift.set(0);
      onHold.set(0);
      veilHold.set(0);
      hud.set(0);
      armed.set(0);
      armP.set(0);
      press.set(0);
      restSplit();
    };

    const tapAt = (x: number, silent: boolean) => {
      'worklet';
      // A swipe is never a tap: a quick flick used to end as a tap at its last point and flag, delete or shut the row.
      if (mode.get() !== OPEN || panned.get()) return;
      if (x >= DELETE_X && x <= DELETE_X + LAYOUT.tile) {
        if (!silent) scheduleOnRN(playSwipeHaptic, 'arm'); // the destructive action fires: one Medium, in its frame
        deleteRow(0, silent);
      } else if (x >= FLAG_X && x <= FLAG_X + LAYOUT.tile) {
        scheduleOnRN(onFlag, key);
        if (!silent) scheduleOnRN(playSwipeHaptic, 'flag');
        closeRow(0);
      } else {
        closeRow(0);
      }
    };

    const pressAt = (x: number) => {
      'worklet';
      if (mode.get() !== OPEN) return;
      press.set(x >= DELETE_X && x <= DELETE_X + LAYOUT.tile ? 2 : x >= FLAG_X && x <= FLAG_X + LAYOUT.tile ? 1 : 0);
    };

    return { begin, move, end, settleTouch, interrupt, resetRow, tapAt, pressAt, closeRow, splitTo, deleteRow };
  }, [
    activeKey, armP, armed, dim, hud, panned, close, contentOut, count, dir, flight, key, kick, lift, m, mode, onDelete, onFlag, onHold,
    onSplit, prevTarget, press, pull, reduceMotion, rollP, rowLeft, screenTop, startTx, target, trail, tx, veilHold, width,
  ]);

  // Another row took the touch: an open row closes.
  useAnimatedReaction(() => activeKey.get(), (now, before) => {
    if (now !== before && now !== key && (mode.get() === OPEN || (mode.get() === IDLE && tx.get() < -1))) api.closeRow(0);
  });
  // The neighbours dim while this row is being split.
  useAnimatedReaction(() => clamp01((pull.get() - SPLIT.dimIn[0]) / (SPLIT.dimIn[1] - SPLIT.dimIn[0])), (v) => {
    if (activeKey.get() === key && mode.get() === SPLITTING) dim.set(v);
  });
  useAnimatedReaction(() => script.get(), (s, before) => {
    if (s.key !== key || (before && s.seq === before.seq)) return;
    // The script writes at most one event a frame, but a frame can be dropped (a cold start): a move or a release whose
    // touch-down was never seen starts the touch itself, and a release first applies its own position, so the row
    // always ends where the script put it.
    if (s.kind === 'down') api.begin();
    else if (s.kind === 'move') {
      if (mode.get() === IDLE) api.begin();
      api.move(s.dx, true);
    } else if (s.kind === 'up') {
      if (mode.get() === IDLE) api.begin();
      api.move(s.dx, true);
      api.end(s.vx, true);
      api.settleTouch();
    } else if (s.kind === 'cancel') api.interrupt();
    else {
      // A scripted tap is a touch of its own: it starts fresh (not a continuation of the swipe that opened the row).
      api.begin();
      api.tapAt(s.x, true);
      api.settleTouch();
    }
  });
  useAnimatedReaction(() => command.get(), (c, before) => {
    if ((c.key !== key && c.key !== '*') || (before && c.seq === before.seq)) return;
    if (c.kind === 'reset') api.resetRow();
    else if (c.kind === 'split') api.splitTo(c.n);
    else if (c.kind === 'delete') api.deleteRow(0, false);
    else api.closeRow(0);
  });

  const gesture = useMemo(() => {
    const pan = Gesture.Pan()
      .activeOffsetX([-SWIPE.activeOffsetX, SWIPE.activeOffsetX])
      .failOffsetY([-SWIPE.failOffsetY, SWIPE.failOffsetY])
      .onBegin(() => api.begin())
      .onUpdate((e) => api.move(e.translationX, false))
      .onEnd((e) => api.end(e.velocityX, false))
      // Cancelled (or never activated): settle without committing anything.
      .onFinalize((_e, success) => (success ? api.settleTouch() : api.interrupt()));
    // A tap only counts when the pan did not take the touch: it waits for the pan to fail and allows 8 pt of slop.
    const tap = Gesture.Tap()
      .maxDuration(500)
      .maxDistance(SWIPE.tapSlop)
      .onBegin((e) => {
        // A new touch: whichever recogniser hears it first clears the last swipe's "moved" mark.
        panned.set(false);
        api.pressAt(e.x);
      })
      .onEnd((e) => api.tapAt(e.x, false))
      .onFinalize(() => press.set(0));
    return Gesture.Exclusive(pan, tap);
  }, [api, panned, press]);

  const row = useAnimatedStyle(() => {
    const active = activeKey.get() === key;
    const f = fold.get();
    return {
      zIndex: active ? 2 : 1,
      opacity: shown.get() * (active ? 1 : 1 - (1 - SPLIT.dimTo) * dim.get()) * (1 - clamp01((f - 0.7) / 0.3)) * enter.get(),
      transform: [{ translateY: y.get() }, { translateX: (1 - enter.get()) * -24 }],
    };
  });
  // A deleted slot closes like a blind: the clip's bottom edge rises with the rows below (same curve, same time), and
  // what is inside stays put, so nothing is squashed and the next row never slides over a half-faded slab.
  // (While folding, the clip's spare room below the row, kept for the shares' shadows, is dropped: nothing is there.)
  const blind = useAnimatedStyle(() => {
    const f = fold.get();
    return { transform: [{ translateY: f > 0 ? -(CLIP_PAD + LAYOUT.row * f) : 0 }] };
  });
  const unblind = useAnimatedStyle(() => {
    const f = fold.get();
    return { transform: [{ translateY: f > 0 ? CLIP_PAD + LAYOUT.row * f : 0 }] };
  });
  const follow = (p: number) => {
    'worklet';
    return reduceMotion ? 0 : SPLIT.follow * Math.min(p, SPLIT.detentStart);
  };
  // The row moves as one piece. Swiped left, its name and icon fade over the first 36 pt, so nothing of them is ever
  // left as a fragment at the ledger's edge; the amount stays, 16 pt clear of the tiles once open.
  const lead = useAnimatedStyle(() => {
    const w = -Math.min(tx.get(), 0);
    const f = clamp01(w / LEAD_FADE);
    return { opacity: (1 - veil.get()) * (1 - f * (2 - f)), transform: [{ translateX: tx.get() + follow(pull.get()) }] };
  });
  const trailing = useAnimatedStyle(() => {
    const w = -Math.min(tx.get(), 0);
    return {
      opacity: 1 - veil.get(),
      transform: [{ translateX: tx.get() + follow(pull.get()) - OPEN_GAP * clamp01(w / -OPEN_X) }, { translateY: -10 * amountY.get() }],
    };
  });
  // The split's mark in the gap the content leaves behind, before the bar breaks.
  const well = useAnimatedStyle(() => {
    const f = follow(pull.get());
    return { opacity: clamp01((f - 16) / 14) * (1 - clamp01((pull.get() - 44) / 12)) * (1 - piecesOn.get()), transform: [{ translateX: f / 2 - WELL / 2 }] };
  });
  const ruleStyle = useAnimatedStyle(() => ({ opacity: 1 - veil.get() }));

  const split2 = split >= 2 ? split : 0;
  const share = split2 ? -shares(item.amount, split2)[0] : item.amount;
  const amount = money(share);
  const label = `${item.title}, ${item.category}, ${item.day}, ${split2 ? `your share ${moneyText(share)} of ${moneyText(item.amount, false)}, split with ${others(split2, ' and ')}` : moneyText(item.amount)}${flagged ? ', flagged' : ''}`;
  const actions = [
    ...(split2
      ? [{ name: 'unsplit', label: 'Undo split' }]
      : [2, 3, 4, 5].map((n) => ({ name: `split${n}`, label: `Split between ${n} people` }))),
    { name: 'flag', label: flagged ? 'Unflag' : 'Flag' },
    { name: 'delete', label: 'Delete' },
  ];

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[styles.row, { width }, row]}
        pointerEvents={hidden || leaving ? 'none' : 'auto'}
        accessible
        accessibilityLabel={label}
        accessibilityHint="Swipe right and keep pulling to split, left for Flag and Delete"
        accessibilityActions={actions}
        onAccessibilityAction={(e) => {
          const name = e.nativeEvent.actionName;
          if (name === 'flag') onFlag(key);
          else if (name === 'unsplit') onUndoSplit(key);
          else if (name === 'delete') scheduleOnUI(api.deleteRow, 0, false);
          else if (name.startsWith('split')) scheduleOnUI(api.splitTo, Number(name.slice(5)));
        }}
      >
        {/* The row's own layers slide inside the ledger's width (clipped left and right, never top and bottom: the
            shares' shadows fall below the slot). */}
        <Animated.View style={[styles.slide, { width }, blind]} pointerEvents="none">
          <Animated.View style={[styles.slideInner, unblind]}>
            <RowActions tx={tx} armP={armP} press={press} flagged={flagged} />
            <SplitBar m={m} amount={item.amount} rowKey={key} flight={flight} />
            <Animated.View style={[styles.well, well]}>
              <Glyph name="users" color={COLOR.ink2} size={WELL} />
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, lead]}>
              <View style={styles.circle}>
                <Glyph name={item.icon} color={COLOR.ink} />
              </View>
              <View style={styles.titleRow}>
                <Text style={styles.title} numberOfLines={1}>{item.title}</Text>
                {flagged && (
                  <Animated.View entering={reduceMotion ? FadeIn.duration(TIMING.reduced) : FLAG_IN}>
                    <Glyph name="flag" color={COLOR.brassPaper} size={14} />
                  </Animated.View>
                )}
              </View>
              <RollText id={split2 ? `split-${split2}` : 'plain'} reduceMotion={reduceMotion} align="left" duration={300} style={styles.sub}>
                {split2 ? (
                  <View style={styles.subRow}>
                    <View style={styles.minis}>
                      {PEOPLE.slice(1, Math.min(split2, 4)).map((p, i) => (
                        <View key={p.name} style={i > 0 ? styles.miniOver : null}>
                          <Monogram person={p} size={LAYOUT.mini} mini ring={i > 0 ? COLOR.paper : undefined} />
                        </View>
                      ))}
                    </View>
                    <Text style={styles.subText} numberOfLines={1}>{split2 <= 3 ? `Split with ${others(split2, ', ')}` : `Split ${split2} ways`}</Text>
                  </View>
                ) : (
                  <Text style={styles.subText} numberOfLines={1}>
                    {item.category}
                    <Text style={styles.dotSep}> · </Text>
                    {item.day}
                  </Text>
                )}
              </RollText>
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, trailing]}>
              <RollText id={moneyText(share)} reduceMotion={reduceMotion} direction={split2 ? 'down' : 'up'} style={styles.amount}>
                <Text style={styles.amountText}>
                  {amount.whole}
                  <Text style={styles.cents}>{amount.cents}</Text>
                </Text>
              </RollText>
              <RollText id={split2 ? `of-${split2}` : 'none'} reduceMotion={reduceMotion} duration={300} style={styles.of}>
                {split2 ? <Text style={styles.ofText}>{`of ${moneyText(item.amount, false)}`}</Text> : null}
              </RollText>
            </Animated.View>
          </Animated.View>
        </Animated.View>
        {/* The ledger rule over this row, above its tiles (as the mock draws it). */}
        {!first && <Animated.View style={[styles.rule, ruleStyle]} pointerEvents="none" />}
        <SplitHud m={m} amount={item.amount} top={hudBelow ? LAYOUT.row + LAYOUT.hudOver - LAYOUT.hud : -LAYOUT.hudOver} />
      </Animated.View>
    </GestureDetector>
  );
});

/** The flag mark arrives from 0.9 scale with its opacity (never from nothing), ease-out. */
const FLAG_IN = new Keyframe({
  0: { opacity: 0, transform: [{ scale: 0.9 }] },
  100: { opacity: 1, transform: [{ scale: 1 }], easing: EASE_OUT },
}).duration(TIMING.flagIn);
const WELL = 18;
const CLIP_PAD = 32;
const LEAD_FADE = 36; // gone before the icon's ring (at the ledger's edge) is cut by more than a few points
const OPEN_GAP = 16;

const styles = StyleSheet.create({
  row: { position: 'absolute', left: 0, top: 0, height: LAYOUT.row },
  slide: { position: 'absolute', left: 0, top: -CLIP_PAD, height: LAYOUT.row + 2 * CLIP_PAD, overflow: 'hidden' },
  slideInner: { position: 'absolute', left: 0, right: 0, top: CLIP_PAD, height: LAYOUT.row },
  rule: { position: 'absolute', left: LAYOUT.text, right: 0, top: 0, height: 1, backgroundColor: COLOR.rule },
  well: { position: 'absolute', left: 0, top: (LAYOUT.row - WELL) / 2 },
  circle: {
    position: 'absolute', left: 0, top: (LAYOUT.row - LAYOUT.circle) / 2, width: LAYOUT.circle, height: LAYOUT.circle,
    borderRadius: LAYOUT.circle / 2, borderWidth: 1, borderColor: COLOR.ring, alignItems: 'center', justifyContent: 'center',
  },
  titleRow: { position: 'absolute', left: LAYOUT.text, top: 16, right: 110, flexDirection: 'row', alignItems: 'center', gap: 6 },
  title: { ...TYPE.rowTitle, color: COLOR.ink, flexShrink: 1 },
  sub: { position: 'absolute', left: LAYOUT.text, top: 40, width: 220, height: 16 },
  subRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  minis: { flexDirection: 'row', marginRight: 4 },
  miniOver: { marginLeft: -2 }, // the mock's 4 pt flex gap less its 6 pt overlap
  subText: { ...TYPE.rowSub, color: COLOR.ink2 },
  dotSep: { letterSpacing: 0.5 }, // the mock's 1 px word spacing around the dot
  amount: { position: 'absolute', right: 0, top: 24 + DIDONE_DROP.money, width: 140, height: 24 },
  amountText: { ...TYPE.amount, color: COLOR.ink, marginTop: -DIDONE_ROOM.amount },
  cents: { ...TYPE.cents, color: COLOR.ink2 },
  of: { position: 'absolute', right: 0, top: 52, width: 140, height: 16 }, // 42 once the block has risen 10 pt
  ofText: { ...TYPE.amountOf, color: COLOR.ink2 },
});
