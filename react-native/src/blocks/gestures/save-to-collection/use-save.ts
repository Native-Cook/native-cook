/**
 * Saving cards from the pile into the bookmark. A finger and the demo script call the same
 * functions: dragBegin(card) → dragMove(tx, ty) → dragEnd() for a long-press drag, fly(cards) for a tap
 * on a card, saveAll() for a tap on the bookmark. When a pile is empty the bookmark rises to the centre
 * (the source's ending) and the next pile deals in.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CARDS } from './gallery';
import type { Rect } from './layout';
import { DEMO, EASE_IN_OUT, EASE_OUT, LOOP_MS, QA_HOLD_AT, REPLICA_ONLY, RESUME_MS, RISE_EASE, SAVE, SPRING, TIMING } from './motion';
import { playSaveHaptic, type SaveHaptic } from './save-haptics';

export type FlightSpec = { id: string; card: number; from: Rect; delay: number; duration: number };

const NEVER = { reduceMotion: ReduceMotion.Never };
const PILE0 = CARDS.filter((c) => c.pile === 0).length;

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

type Options = {
  autoPlay: boolean;
  reduceMotion: boolean;
  rects: Rect[]; // screen rectangles of CARDS
  bookmark: { x: number; y: number };
};

let flightSeq = 0;

export function useSave({ autoPlay, reduceMotion, rects, bookmark }: Options) {
  const [pile, setPile] = useState<0 | 1>(0);
  const [gone, setGone] = useState<Set<number>>(() => new Set());
  const [flights, setFlights] = useState<FlightSpec[]>([]);
  const [dragCard, setDragCard] = useState(-1);
  const [saved, setSaved] = useState(0);
  const [deal, setDeal] = useState(0); // bumps when a pile deals in, so its cards replay their entrance

  const lifted = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const baseX = useSharedValue(0);
  const baseY = useSharedValue(0);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const dragScale = useSharedValue(1);
  const dragOn = useSharedValue(0);
  const open = useSharedValue(0); // magnet pull, 0..1
  const bounce = useSharedValue(0);
  const rise = useSharedValue(0); // the bookmark at the centre
  // Press feedback within a frame: which target is down (-2 = the bookmark, a card index, -1 none) and how far.
  const press = useSharedValue(0);
  const pressCard = useSharedValue(-1);
  const ring = useSharedValue(1); // 0→1 a light ring leaves the bookmark when a card lands
  const tilt = useSharedValue(0); // the lifted card leans with the finger's horizontal speed
  const clock = useSharedValue(0); // ambient float, on the loop clock in the demo
  const lastVX = useSharedValue(0);
  const lastVY = useSharedValue(0);
  const [pileCount, setPileCount] = useState(0); // the payoff: how many cards the pile just saved

  // Autoplay.
  const t = useSharedValue(0);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const scriptDrag = useSharedValue(false);
  const lastTx = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  const visible = useMemo(() => CARDS.map((c, i) => i).filter((i) => CARDS[i].pile === pile && !gone.has(i)), [gone, pile]);
  const nextPileTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (nextPileTimer.current) clearTimeout(nextPileTimer.current); }, []);

  const haptic = (kind: SaveHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playSaveHaptic, kind);
  };

  // ——— JS side ———
  const land = useCallback((silent: boolean) => {
    setSaved((n) => n + 1);
    bounce.set(reduceMotion ? 0 : withSequence(withTiming(1, { duration: 70 }), withSpring(0, SPRING.bounce)));
    if (!reduceMotion) {
      ring.set(0);
      ring.set(withTiming(1, { duration: TIMING.landRing, easing: EASE_OUT }));
    }
    if (!silent) playSaveHaptic('land');
  }, [bounce, reduceMotion, ring]);

  // A pile is done once every card has gone and nothing is still flying: rise, then deal the next pile.
  // The rise runs on the clock (not a timing), so a QA hold freezes it and the loop cannot drift.
  const riseAt = useSharedValue(-1);
  const startRise = useCallback(() => {
    'worklet';
    riseAt.set(clock.get());
  }, [clock, riseAt]);
  const nextPile = useCallback(() => {
    setPile((p) => (p === 0 ? 1 : 0));
    setGone(new Set());
    setDeal((d) => d + 1);
  }, []);

  // By hand: a pile is done once every card has gone and nothing is still flying; rise, then deal the next.
  // In the demo the script does both at fixed times, so the loop does not drift with the JS thread.
  useEffect(() => {
    if (autoPlay && !paused.get()) return;
    const left = CARDS.some((c, i) => c.pile === pile && !gone.has(i));
    if (left || flights.length > 0 || dragCard >= 0 || nextPileTimer.current) return;
    setPileCount(CARDS.filter((c) => c.pile === pile).length);
    playSaveHaptic('success');
    startRise();
    nextPileTimer.current = setTimeout(() => {
      nextPileTimer.current = null;
      nextPile();
    }, TIMING.rise + TIMING.riseHold);
  }, [autoPlay, dragCard, flights.length, gone, nextPile, paused, pile, startRise]);

  const [flying] = useState(() => new Set<number>()); // cards in the air (a stable mutable set)
  const onFlightStart = useCallback((card: number) => {
    setGone((set) => (set.has(card) ? set : new Set(set).add(card)));
  }, []);

  const onLand = useCallback((id: string) => {
    const f = Number(id.split(':')[1]);
    if (!Number.isNaN(f)) flying.delete(f);
    setFlights((list) => list.filter((x) => x.id !== id));
    land(autoPlay && !paused.get());
  }, [flying, autoPlay, land, paused]);

  const fly = useCallback((cards: number[], silent: boolean) => {
    // Double trigger: a card already in flight is not sent again.
    cards = cards.filter((c) => !flying.has(c));
    if (!cards.length) return;
    cards.forEach((c) => flying.add(c));
    // A card's tile stays until its flight has mounted (onFlightStart), so nothing blinks out under load.
    if (reduceMotion) {
      setGone((set) => {
        const next = new Set(set);
        cards.forEach((c) => next.add(c));
        return next;
      });
      cards.forEach(() => land(silent));
      return;
    }
    const start = flightSeq;
    flightSeq += cards.length;
    setFlights((list) => list.concat(cards.map((card, i) => ({
      id: `f${start + i}:${card}`,
      card,
      from: rects[card],
      delay: i * TIMING.flockStagger,
      duration: TIMING.flight,
    }))));
  }, [flying, land, rects, reduceMotion]);

  // Front cards first, as in the source's rush into the glyph.
  // Each save-all pours the source's stream of streaked cards down the column (Stream.tsx).
  const [rush, setRush] = useState(0);
  const saveAll = useCallback((silent: boolean) => {
    if (!reduceMotion && visible.length > 0) setRush((r) => r + 1);
    fly(visible.slice().reverse(), silent);
  }, [fly, reduceMotion, visible]);

  const dropped = useCallback((card: number, silent: boolean) => {
    setGone((set) => new Set(set).add(card));
    setDragCard(-1);
    land(silent);
  }, [land]);

  // ——— Long-press drag, on the UI thread ———
  const visibleSV = useSharedValue<number[]>([]);
  useEffect(() => { visibleSV.set(visible); }, [visible, visibleSV]);

  /** The front-most visible card under a point. */
  const hit = (x: number, y: number) => {
    'worklet';
    const list = visibleSV.get();
    for (let k = list.length - 1; k >= 0; k--) {
      const r = rects[list[k]];
      if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) return list[k];
    }
    return -1;
  };

  const dragBegin = (card: number, silent: boolean) => {
    'worklet';
    if (card < 0 || dragging.get()) return;
    const r = rects[card];
    dragging.set(true);
    lifted.set(card);
    baseX.set(r.x + r.w / 2);
    baseY.set(r.y + r.h / 2);
    dragX.set(baseX.get());
    dragY.set(baseY.get());
    dragScale.set(1);
    dragOn.set(1);
    if (!reduceMotion) dragScale.set(withSpring(SAVE.liftScale, SPRING.lift));
    scheduleOnRN(setDragCard, card);
    haptic('lift', silent);
  };

  const dragMove = (tx: number, ty: number, silent: boolean, vx = 0, vy = 0) => {
    'worklet';
    if (!dragging.get()) return;
    lastVX.set(vx);
    lastVY.set(vy);
    // Secondary motion: the card leans into its horizontal speed, like a card held by one corner.
    if (!reduceMotion) tilt.set(withSpring(Math.max(-SAVE.tiltMax, Math.min(SAVE.tiltMax, vx * SAVE.tiltPerVelocity)), SPRING.tilt));
    const fx = baseX.get() + tx;
    const fy = baseY.get() + ty;
    const d = Math.hypot(bookmark.x - fx, bookmark.y - fy);
    const raw = Math.min(Math.max(1 - d / SAVE.magnetRadius, 0), 1);
    const m = raw * raw * (3 - 2 * raw);
    if (open.get() === 0 && m > 0) haptic('magnet', silent);
    open.set(m);
    dragX.set(fx + (bookmark.x - fx) * m * SAVE.magnetPull);
    dragY.set(fy + (bookmark.y - fy) * m * SAVE.magnetPull);
    if (!reduceMotion) dragScale.set(SAVE.liftScale * (1 - SAVE.magnetShrink * m));
  };

  const dragEnd = (silent: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    dragging.set(false);
    tilt.set(withSpring(0, SPRING.tilt));
    const card = lifted.get();
    // Flick to save: a fast throw that points at the bookmark counts, wherever it starts.
    const vx = lastVX.get();
    const vy = lastVY.get();
    const speed = Math.hypot(vx, vy);
    const toX = bookmark.x - dragX.get();
    const toY = bookmark.y - dragY.get();
    const aim = speed > 0 ? (vx * toX + vy * toY) / (speed * Math.hypot(toX, toY)) : 0;
    const flicked = speed > SAVE.flickSpeed && aim > SAVE.flickAim;
    if (open.get() > 0.05 || flicked) {
      // Into the bookmark; a flick keeps its speed for the first part of the way.
      const duration = reduceMotion ? TIMING.reduced : flicked && open.get() <= 0.05 ? TIMING.flickFlight : TIMING.dropFlight;
      const into = { duration, easing: EASE_IN_OUT, ...NEVER };
      haptic('commit', silent);
      open.set(withTiming(0, into));
      dragX.set(withTiming(bookmark.x, into));
      dragScale.set(withTiming(SAVE.endScale, into));
      dragY.set(withTiming(bookmark.y, into, (done) => {
        if (!done) return;
        dragOn.set(0);
        lifted.set(-1);
        scheduleOnRN(dropped, card, silent);
      }));
      return;
    }
    open.set(withTiming(0, { duration: TIMING.dim }));
    const finish = (done?: boolean) => {
      'worklet';
      if (!done) return;
      dragOn.set(0);
      lifted.set(-1);
      scheduleOnRN(setDragCard, -1);
    };
    if (reduceMotion) {
      const back = { duration: TIMING.reduced, ...NEVER };
      dragScale.set(withTiming(1, back));
      dragX.set(withTiming(baseX.get(), back));
      dragY.set(withTiming(baseY.get(), back, finish));
      return;
    }
    dragScale.set(withSpring(1, SPRING.back));
    dragX.set(withSpring(baseX.get(), SPRING.back));
    dragY.set(withSpring(baseY.get(), SPRING.back, finish));
  };

  // ——— Autoplay ———
  const show = (on: boolean) => {
    'worklet';
    ghostO.set(withTiming(on ? 1 : 0, { duration: DEMO.fingerFade * 1000 }));
  };
  const dragIndex = CARDS.findIndex((c) => c.id === DEMO.drag.card);

  useFrameCallback((frame) => {
    'worklet';
    clock.set(autoPlay && !paused.get() ? t.get() : clock.get() + Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05));
    if (riseAt.get() >= 0) {
      // Up, hold, down: cubic in-out each way (Reduce Motion: a short fade-length move).
      const e = clock.get() - riseAt.get();
      const up = (reduceMotion ? TIMING.reduced : TIMING.rise) / 1000;
      const down = (reduceMotion ? TIMING.reduced : TIMING.riseDown) / 1000;
      const hold = TIMING.riseHold / 1000;
      const io = (u: number) => (u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2);
      if (e < 0 || e > up + hold + down) {
        riseAt.set(-1);
        rise.set(0);
      } else if (e < up) rise.set(RISE_EASE(e / up));
      else if (e < up + hold || (QA_HOLD_AT !== null && REPLICA_ONLY)) rise.set(1);
      else rise.set(io(1 - (e - up - hold) / down));
    }
    if (!autoPlay || rects.length === 0) return;
    const dt = QA_HOLD_AT !== null && t.get() >= QA_HOLD_AT ? 0 : Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    if (paused.get()) {
      if (!touching.get()) quiet.set(quiet.get() + dt);
      // Resume at the start of a loop: the script waits for pile 0 to be dealt and still.
      if (quiet.get() >= RESUME_MS / 1000 && !dragging.get() && rise.get() === 0 && visibleSV.get().length === PILE0 && CARDS[visibleSV.get()[0]].pile === 0) {
        t.set(0);
        paused.set(false);
      }
      return;
    }
    const t0 = t.get();
    const t1 = t0 + dt;
    const cross = (at: number) => t0 < at && t1 >= at;

    // 1. Long press the front card and carry it to the bookmark.
    const d = DEMO.drag;
    if (cross(d.down)) {
      const r = rects[dragIndex];
      ghostX.set(r.x + r.w / 2);
      ghostY.set(r.y + r.h / 2);
      show(true);
      ghostPress.set(withTiming(1, { duration: (d.lifted - d.down) * 1000 }));
    }
    if (cross(d.lifted)) {
      dragBegin(dragIndex, true);
      scriptDrag.set(true);
    }
    if (scriptDrag.get() && t1 >= d.lifted && t1 < d.up) {
      const u = ease((t1 - d.lifted) / (d.end - d.lifted));
      const tx = (bookmark.x - baseX.get()) * u + Math.sin(Math.PI * u) * 40;
      const ty = (bookmark.y - baseY.get()) * u;
      dragMove(tx, ty, true, (tx - lastTx.get()) / dt, 0);
      lastTx.set(tx);
      ghostX.set(baseX.get() + tx);
      ghostY.set(baseY.get() + ty);
    }
    if (cross(d.up) && scriptDrag.get()) {
      scriptDrag.set(false);
      dragEnd(true);
      ghostPress.set(withTiming(0, { duration: 120 }));
      show(false);
    }
    // 2. Tap the bookmark: the rest of the pile rushes in; later the next pile does too.
    for (let k = 0; k < 2; k++) {
      const r = k === 0 ? DEMO.rise : DEMO.riseAgain;
      if (cross(r)) {
        scheduleOnRN(setPileCount, k === 0 ? PILE0 : CARDS.length - PILE0);
        startRise();
      }
      if (cross(r + (TIMING.rise + TIMING.riseHold) / 1000)) scheduleOnRN(nextPile);
    }
    for (let k = 0; k < 2; k++) {
      const at = k === 0 ? DEMO.saveAll : DEMO.saveAllAgain;
      if (cross(at - 0.25)) {
        ghostX.set(bookmark.x);
        ghostY.set(bookmark.y);
        show(true);
        ghostPress.set(withSequence(withTiming(1, { duration: 120 }), withTiming(0, { duration: 160 })));
        // The demo's tap presses the glyph like a finger would.
        pressCard.set(-2);
        press.set(withSequence(withTiming(1, { duration: TIMING.pressIn, easing: EASE_OUT }), withTiming(0, { duration: TIMING.pressOut, easing: EASE_OUT })));
      }
      if (cross(at)) {
        show(false);
        scheduleOnRN(saveAll, true);
      }
    }
    t.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
  });

  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      quiet.set(0);
      if (paused.get()) return;
      paused.set(true);
      show(false);
      if (scriptDrag.get()) {
        scriptDrag.set(false);
        dragEnd(true);
      }
    });
    // show/dragEnd only touch shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
  }, [quiet, touching]);

  const gesture = useMemo(() => {
    const drag = Gesture.Pan()
      .activateAfterLongPress(SAVE.holdMs)
      .onStart((e) => dragBegin(hit(e.x, e.y), false))
      .onUpdate((e) => dragMove(e.translationX, e.translationY, false, e.velocityX, e.velocityY))
      .onFinalize(() => dragEnd(false));
    const tap = Gesture.Tap().onBegin((e) => {
      pressCard.set(Math.hypot(e.x - bookmark.x, e.y - bookmark.y) < 44 ? -2 : hit(e.x, e.y));
      press.set(withTiming(1, { duration: TIMING.pressIn, easing: EASE_OUT }));
    }).onFinalize(() => {
      press.set(withTiming(0, { duration: TIMING.pressOut, easing: EASE_OUT }));
    }).onEnd((e) => {
      const near = Math.hypot(e.x - bookmark.x, e.y - bookmark.y) < 44;
      if (near) {
        scheduleOnRN(saveAll, false);
        return;
      }
      const card = hit(e.x, e.y);
      if (card >= 0) scheduleOnRN(fly, [card], false);
    });
    return Gesture.Race(drag, tap);
    // The worklets read shared values only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rects, fly, saveAll, bookmark.x, bookmark.y]);

  return {
    gesture, touchStart, touchEnd, onLand, onFlightStart, fly, saveAll,
    visible, flights, dragCard, saved, deal,
    pileCount, rush,
    values: { press, pressCard, lifted, dragX, dragY, dragScale, dragOn, open, bounce, rise, ring, tilt, clock, ghostX, ghostY, ghostO, ghostPress },
  };
}
