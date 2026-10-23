import { useEffect } from 'react';
import { useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';

import { referenceTracePoint } from './geometry';
import {
  REFERENCE_LAYOUT,
  TRACE_PROGRESS_KEYFRAMES,
  traceProgressAtFrame,
  type Point,
  type Rect,
} from './model';
import type { IdlePrime } from './opening-effects-motion';

/** Seconds in one loop of the script. */
const LOOP = 15;

// The script, in seconds from the start of a loop: a tear from the left edge
// to the right, four cards advanced, then "Open another pack". The opening
// (2 s) and the cards' arrivals run on the scene's own clocks in between.
const TEAR_FROM = 1;
const TEAR_TO = 2.1;
const ADVANCE_AT = [5.2, 6.3, 7.4, 10.4];
const REPLAY_AT = 13.6;

// After a touch the scene is the user's. This long after the last one lifts,
// the script takes it back.
const RESUME_AFTER_MS = 2500;
// A gap this long between two frames means the app was away.
const STALL_MS = 1000;

// The finger of the reference video moves between these two frames.
const STROKE_FIRST_FRAME = TRACE_PROGRESS_KEYFRAMES[0][0];
const STROKE_LAST_FRAME = TRACE_PROGRESS_KEYFRAMES[TRACE_PROGRESS_KEYFRAMES.length - 2][0];

const STEP_TEAR = 0;
const STEP_TEARING = 1;
const STEP_CARDS = 2;
const STEP_REPLAY = STEP_CARDS + ADVANCE_AT.length;
const STEP_REST = STEP_REPLAY + 1;

const NOT_RUNNING = -1;

const TOUCH_NONE = 0;
const TOUCH_DOWN = 1;
const TOUCH_LIFTED = 2;
const TOUCH_WAITING = 3;

/**
 * The scripted finger: the reference trace with its own easing and slight
 * rise. The recorded finger lifts short of the far edge, so its travel is
 * stretched to end on that edge.
 */
function scriptedFinger(seconds: number, pack: Rect, width: number, height: number): Point {
  'worklet';
  const stroke = (seconds - TEAR_FROM) / (TEAR_TO - TEAR_FROM);
  const progress = traceProgressAtFrame(
    STROKE_FIRST_FRAME + (STROKE_LAST_FRAME - STROKE_FIRST_FRAME) * stroke,
  );
  const point = referenceTracePoint(progress, width, height);
  const startX = REFERENCE_LAYOUT.trace.startX * width;
  const endX = REFERENCE_LAYOUT.trace.endX * width;
  const travelled = (point.x - startX) / (endX - startX);
  return { x: startX + (pack.x + pack.width - startX) * travelled, y: point.y };
}

/**
 * Plays the block by itself in a loop of LOOP seconds, through the entry
 * points a finger uses: the tear's begin and update, a card's advance, the
 * replay button's reset. Every cue is timed against the moment its loop
 * began, and a loop begins a whole number of periods after the one before, so
 * the period is exact.
 *
 * A loop starts and ends on the sealed pack. The script resets the pack
 * LOOP - REPLAY_AT seconds before a loop ends, so the idle sheen is that far
 * into its cycle when the next loop begins; a loop that follows no other
 * starts it there too (`idlePrime`). Every loop is then the same from its
 * first frame, and its last frame leads into the first.
 *
 * `touchDown` and `touchUp` are called from the scene's gesture. A touch
 * stops the script, and ends a tear it had under way the way a lifted finger
 * would. 2.5 s after the last touch the scene resets and the loop starts over.
 */
export function useAutoPlay({
  enabled, pack, width, height, scripted, resets, idlePrime,
  beginCut, updateCut, cancelCut, advance, replay,
}: Readonly<{
  enabled: boolean; pack: Rect; width: number; height: number;
  /** 1 while the script drives the scene. */
  scripted: SharedValue<number>;
  /** Counts the script's resets, for state that a reset by hand keeps. */
  resets: SharedValue<number>;
  idlePrime: SharedValue<IdlePrime>;
  beginCut: (point: Point) => void;
  updateCut: (point: Point) => void;
  cancelCut: (velocityX: number) => void;
  advance: () => void;
  replay: () => void;
}>) {
  const origin = useSharedValue(NOT_RUNNING);
  const step = useSharedValue(STEP_TEAR);
  const touch = useSharedValue(TOUCH_NONE);
  const liftedAt = useSharedValue(0);

  // A loop from its first frame, on a pack that is sealed whatever came before.
  const begin = (now: number) => {
    'worklet';
    touch.set(TOUCH_NONE);
    scripted.set(1);
    resets.set(resets.get() + 1);
    replay();
    idlePrime.set({ count: idlePrime.get().count + 1, ms: (LOOP - REPLAY_AT) * 1000 });
    origin.set(now);
    step.set(STEP_TEAR);
  };

  const frame = useFrameCallback((info) => {
    'worklet';
    const now = info.timestamp;
    const period = LOOP * 1000;

    const touched = touch.get();
    if (touched === TOUCH_DOWN) return;
    if (touched === TOUCH_LIFTED) {
      liftedAt.set(now);
      touch.set(TOUCH_WAITING);
      return;
    }
    if (touched === TOUCH_WAITING) {
      if (now - liftedAt.get() >= RESUME_AFTER_MS) begin(now);
      return;
    }

    let start = origin.get();
    if (start === NOT_RUNNING || (info.timeSincePreviousFrame ?? 0) > STALL_MS) {
      begin(now);
      return;
    }
    if (now - start >= period) {
      start += period * Math.floor((now - start) / period);
      origin.set(start);
      step.set(STEP_TEAR);
    }

    const seconds = (now - start) / 1000;
    let next = step.get();
    if (next === STEP_TEAR && seconds >= TEAR_FROM) {
      beginCut(scriptedFinger(TEAR_FROM, pack, width, height));
      next = STEP_TEARING;
    }
    if (next === STEP_TEARING) {
      if (seconds < TEAR_TO) {
        updateCut(scriptedFinger(seconds, pack, width, height));
      } else {
        // One last event past the far edge commits the cut, as a fast swipe does.
        updateCut({
          x: pack.x + pack.width + width * 0.02,
          y: scriptedFinger(TEAR_TO, pack, width, height).y,
        });
        next = STEP_CARDS;
      }
    } else if (next >= STEP_CARDS && next < STEP_REPLAY) {
      if (seconds >= ADVANCE_AT[next - STEP_CARDS]) {
        advance();
        next += 1;
      }
    } else if (next === STEP_REPLAY && seconds >= REPLAY_AT) {
      resets.set(resets.get() + 1);
      replay();
      next = STEP_REST;
    }
    if (next !== step.get()) step.set(next);
  }, false);

  useEffect(() => {
    if (!enabled) return;
    frame.setActive(true);
    return () => {
      frame.setActive(false);
      origin.set(NOT_RUNNING);
    };
  }, [enabled, frame, origin]);

  const touchDown = () => {
    'worklet';
    touch.set(TOUCH_DOWN);
    if (scripted.get() === 0) return;
    scripted.set(0);
    cancelCut(0);
  };
  const touchUp = () => {
    'worklet';
    touch.set(TOUCH_LIFTED);
  };

  return { touchDown, touchUp };
}
