import { Canvas, Fill, ImageShader, Shader, makeImageFromView, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PixelRatio, StyleSheet, View, useWindowDimensions, type AccessibilityActionEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, SensorType, cancelAnimation, useAnimatedSensor, useAnimatedStyle, useDerivedValue, useFrameCallback,
  useReducedMotion, useSharedValue, withDelay, withSequence, withSpring, withTiming, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { clampTip, rollRamp, strokePoint, turnProgress } from './curl-math';
import { curlEffect } from './curl-shader';
import { foilEffect } from './foil-shader';
import { CoverPage, ForewordPage, PrefacePage, TitlePage } from './JournalPages';
import {
  FADE_MS, FOIL, GRAIN, HOLD_A, IDLE_RESUME_MS, LIFT, LOOP_MS, NUDGE, QA_HOLD, QA_SPEED, QA_START, RIFFLE, SCRIPT, STACK, TURN,
  TWINKLE, ms, sp,
} from './motion';
import { COPY, CURL, PAPER, REF, SERIF, SPACE, STACK_INK, TYPE } from './theme';

type Props = { autoPlay?: boolean };

const LAST = 3;
// Interaction modes, on the UI thread.
const IDLE = 0;
const PENDING = 1; // finger down, direction not picked yet
const DRAG = 2;
const SETTLE = 3;
const HOLD = 4;
const RIFFLING = 5;
const NUDGING = 6;

const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const NUDGE_AT = LOOP_MS + 1; // autoplay: no idle nudge inside the 10 s loop

const HAPTIC_TICK = 0;
const HAPTIC_HALF = 1;
const HAPTIC_LAND = 2;
function haptic(kind: number) {
  if (kind === HAPTIC_TICK) Haptics.selectionAsync();
  else if (kind === HAPTIC_HALF) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

// One turning sheet: its grabbed point, where that point is now, and which page it is.
type Slot = { front: SharedValue<number>; gx: SharedValue<number>; gy: SharedValue<number>; tx: SharedValue<number>; ty: SharedValue<number>; on: SharedValue<number> };

function useSlot(width: number): Slot {
  return {
    front: useSharedValue(0),
    gx: useSharedValue(width),
    gy: useSharedValue(0),
    tx: useSharedValue(width),
    ty: useSharedValue(0),
    on: useSharedValue(0),
  };
}

/** The book's type is bundled; the pages are snapshotted, so nothing is drawn before it is loaded. */
export function PageCurlJournal({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const [loaded, failed] = useFonts({
    [SERIF.roman]: require('./assets/Newsreader.ttf'),
    [SERIF.italic]: require('./assets/NewsreaderItalic.ttf'),
  });
  if (!loaded && !failed) return <View style={styles.root} />;
  // Reduce Motion: no demo; a finger still turns the pages (they cross-fade).
  return <Journal autoPlay={autoPlay && !reduced} />;
}

function Journal({ autoPlay }: { autoPlay: boolean }) {
  const { width, height } = useWindowDimensions();
  const s = width / REF.width;
  const reduced = useReducedMotion();
  const px = PixelRatio.get();

  const fromStart = QA_START < SCRIPT[1].t0 ? 0 : QA_START < SCRIPT[2].t0 ? 1 : QA_START < SCRIPT[3].t0 ? 2 : LAST;
  const initial = QA_HOLD === 'b' ? LAST : QA_START > 0 ? fromStart : QA_HOLD === 'p1' ? 1 : QA_HOLD === 'p2' ? 2 : 0;
  const [view, setView] = useState({ page: initial, tick: 0 });
  const refs = [useRef<View>(null), useRef<View>(null), useRef<View>(null), useRef<View>(null)];
  const [snaps, setSnaps] = useState<(SkImage | null)[]>([null, null, null, null]);
  // Riffle canvases exist only while a riffle runs: an idle full-screen shader still costs a frame.
  const [riffling, setRiffling] = useState(false);

  // Slot 0 is the sheet a finger turns; slots 1–2 only fly during a riffle, on top of it.
  const slot0 = useSlot(width);
  const slot1 = useSlot(width);
  const slot2 = useSlot(width);
  const { front: frontIdx, gx, gy, tx, ty, on: show } = slot0;
  const underIdx = useSharedValue(1);

  const cur = useSharedValue(initial);
  const mode = useSharedValue(IDLE);
  const baseX = useSharedValue(0);
  const baseY = useSharedValue(0);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const gain = useSharedValue(1);
  const preLifted = useSharedValue(0);
  const ready = useSharedValue(0);
  const fade = useSharedValue(-1);
  const flex = useSharedValue(0);
  const flexV = useSharedValue(0);
  const lastTip = useSharedValue<[number, number]>([width, 0]);
  const scripted = useSharedValue(0); // 1 while the autoplay finger is driving
  const lastTouch = useSharedValue(-1e9);
  const clock = useSharedValue(QA_START);
  const playing = useSharedValue(1);
  const riffleLeft = useSharedValue(0);
  const riffleTo = useSharedValue(0);
  const grainAt = useSharedValue<[number, number]>([0, 0]);
  const grainTime = useSharedValue(-1e9);
  const pastHalf = useSharedValue(0);
  const lastNudge = useSharedValue(-1e9);
  const twinkle = useSharedValue(1);
  const foilT = useSharedValue(0);
  const lastFoil = useSharedValue(0);
  const scriptAt = useSharedValue(-1);
  const nudgeDone = useSharedValue(0);
  const tilt = useAnimatedSensor(SensorType.ROTATION);

  // Snapshot the four pages once they are laid out: the curl samples them as textures. On a cold start the
  // views may not be drawable yet, so retry until all four come back.
  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    const attempt = (n: number) => {
      // A snapshot can stall while the app is still starting: give each attempt a second.
      const stall = new Promise<null[]>((resolve) => setTimeout(() => resolve([null]), 1000));
      Promise.race([Promise.all(refs.map((r) => makeImageFromView(r))), stall])
        .then((images) => {
          if (!alive) return;
          if (images.length === 4 && images.every((im) => im !== null)) {
            setSnaps(images);
            ready.set(1);
          } else if (n < 30) timer = setTimeout(() => attempt(n + 1), 300);
        })
        .catch(() => {
          if (alive && n < 30) timer = setTimeout(() => attempt(n + 1), 300);
        });
    };
    timer = setTimeout(() => attempt(0), 450);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [width, height]);

  // After React has put the landed page on top, the curl canvases step aside (two frames, so the views are drawn).
  useEffect(() => {
    let b = 0;
    const a = requestAnimationFrame(() => {
      b = requestAnimationFrame(() => {
        if (mode.get() !== IDLE) return;
        show.set(0);
        slot1.on.set(0);
        slot2.on.set(0);
        setRiffling(false);
      });
    });
    return () => {
      cancelAnimationFrame(a);
      cancelAnimationFrame(b);
    };
  }, [view, mode, show, slot1.on, slot2.on]);

  const landed = useCallback((page: number, changed: boolean, feel: boolean) => {
    setView((v) => ({ page, tick: v.tick + 1 }));
    if (changed && feel) haptic(HAPTIC_LAND);
  }, []);

  // Payoffs on arrival: the title's sparkles twinkle, the cover's foil catches the light once.
  const arrive = useCallback((page: number) => {
    'worklet';
    if (reduced) return;
    if (page === 0) {
      twinkle.set(0);
      twinkle.set(withTiming(1, { duration: ms(TWINKLE.durationMs), easing: EASE_IN_OUT }));
    }
    if (page === LAST) {
      foilT.set(0);
      lastFoil.set(clock.get());
      foilT.set(withTiming(1, { duration: ms(FOIL.sweepMs), easing: EASE_IN_OUT }, (f) => {
        if (f) foilT.set(0);
      }));
    }
  }, [reduced, twinkle, foilT, lastFoil, clock]);

  const settleDone = useCallback((over: boolean) => {
    'worklet';
    const page = over ? underIdx.get() : frontIdx.get();
    const changed = page !== cur.get();
    cur.set(page);
    mode.set(IDLE);
    fade.set(-1);
    preLifted.set(0);
    if (changed) arrive(page);
    scheduleOnRN(landed, page, changed, scripted.get() === 0);
  }, [underIdx, frontIdx, cur, mode, fade, preLifted, arrive, landed, scripted]);

  // A riffle ends when its last sheet lands.
  const riffleLanded = useCallback(() => {
    'worklet';
    riffleLeft.set(riffleLeft.get() - 1);
    if (riffleLeft.get() > 0) return;
    const page = riffleTo.get();
    cur.set(page);
    mode.set(IDLE);
    arrive(page);
    scheduleOnRN(landed, page, true, scripted.get() === 0);
  }, [riffleLeft, riffleTo, cur, mode, arrive, landed, scripted]);

  // Riffle: the sheet in hand comes back, and every earlier sheet follows it, staggered, each on its own canvas.
  const startRiffle = useCallback((vx: number, vy: number) => {
    'worklet';
    const f0 = frontIdx.get();
    const n = Math.min(f0, RIFFLE.slots - 1);
    mode.set(RIFFLING);
    scheduleOnRN(setRiffling, true);
    riffleLeft.set(n + 1);
    riffleTo.set(f0 - n);
    flexV.set(flexV.get() + Math.abs(vx) * TURN.releaseKick);
    ty.set(withSpring(gy.get(), { ...sp(RIFFLE.spring), velocity: vy }));
    tx.set(withSpring(gx.get(), { ...sp(RIFFLE.spring), velocity: vx }, (f) => {
      if (f) riffleLanded();
    }));
    const slots = [slot1, slot2];
    for (let i = 1; i <= n; i++) {
      const sl = slots[i - 1];
      const y = gy.get() + (i % 2 === 1 ? -1 : 1) * 18 * i * s;
      sl.front.set(f0 - i);
      sl.gx.set(width);
      sl.gy.set(y);
      sl.tx.set(-width);
      sl.ty.set(y);
      sl.on.set(1);
      sl.tx.set(withDelay(ms(RIFFLE.staggerMs * i), withSpring(width, { ...sp(RIFFLE.spring), velocity: RIFFLE.launch * s * QA_SPEED }, (f) => {
        if (f) riffleLanded();
      })));
    }
  }, [frontIdx, mode, riffleLeft, riffleTo, flexV, ty, gy, tx, gx, riffleLanded, slot1, slot2, s, width]);

  // Release the sheet with the finger's velocity: it goes over or falls back on a mass-spring.
  const release = useCallback((over: boolean, vx: number, vy: number) => {
    'worklet';
    const [cx, cy] = clampTip(gx.get(), gy.get(), tx.get(), ty.get(), height);
    tx.set(cx);
    ty.set(cy);
    if (!over && gain.get() === TURN.backFactor && vx / QA_SPEED > RIFFLE.velocity && frontIdx.get() > 0 && !reduced) {
      startRiffle(vx, vy);
      return;
    }
    mode.set(SETTLE);
    // The flung sheet billows: its speed kicks the roll's flex spring.
    flexV.set(flexV.get() + Math.hypot(vx, vy) * TURN.releaseKick);
    const cfg = sp(over ? TURN.over : TURN.back);
    ty.set(withSpring(gy.get(), { ...cfg, velocity: vy }));
    tx.set(withSpring(over ? -gx.get() : gx.get(), { ...cfg, velocity: vx }, (finished) => {
      if (finished) settleDone(over);
    }));
  }, [gx, gy, tx, ty, height, gain, frontIdx, reduced, startRiffle, mode, flexV, settleDone]);

  // Turn by button, tap or accessibility action: the right edge lifts at 62 % and is flicked over.
  const turnBy = useCallback((step: number) => {
    'worklet';
    if (mode.get() !== IDLE || ready.get() === 0) return;
    const c = cur.get();
    const target = c + step;
    if (target < 0 || target > LAST) return;
    show.set(1);
    if (reduced) {
      frontIdx.set(c);
      underIdx.set(target);
      fade.set(0);
      mode.set(SETTLE);
      fade.set(withTiming(1, { duration: FADE_MS, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }, (f) => {
        if (f) {
          frontIdx.set(target);
          settleDone(false);
        }
      }));
      return;
    }
    gain.set(1);
    if (step > 0) {
      if (preLifted.get() === 0) {
        // Not already lifted by the finger: lift the right edge at 62 %.
        const y = height * TURN.buttonGrabY;
        gx.set(width);
        gy.set(y);
        tx.set(width - 1);
        ty.set(y);
      }
      frontIdx.set(c);
      underIdx.set(target);
      release(true, -1600 * s * QA_SPEED, -500 * s * QA_SPEED);
    } else {
      const y = height * TURN.buttonGrabY;
      frontIdx.set(target);
      underIdx.set(c);
      gx.set(width);
      gy.set(y);
      tx.set(-width);
      ty.set(y);
      release(false, 1200 * s * QA_SPEED, -400 * s * QA_SPEED);
    }
  }, [mode, ready, cur, show, reduced, frontIdx, underIdx, fade, settleDone, gain, preLifted, height, gx, gy, width, tx, ty, release, s]);

  const down = useCallback((x: number, y: number) => {
    'worklet';
    const m = mode.get();
    if (m === HOLD || m === RIFFLING || ready.get() === 0) return;
    if ((m === SETTLE || m === NUDGING) && fade.get() < 0) {
      // Catch the sheet mid-flight: it stays where it is and follows the finger from here.
      cancelAnimation(tx);
      cancelAnimation(ty);
      const [cx, cy] = clampTip(gx.get(), gy.get(), tx.get(), ty.get(), height);
      baseX.set(cx);
      baseY.set(cy);
      startX.set(x);
      startY.set(y);
      if (m === NUDGING) gain.set(1);
      mode.set(DRAG);
      return;
    }
    if (m !== IDLE) return;
    startX.set(x);
    startY.set(y);
    mode.set(PENDING);
    pastHalf.set(0);
    grainAt.set([x, y]);
    // Anticipation: a touch near the right edge lifts the corner within a frame.
    const c = cur.get();
    if (!reduced && c < LAST && x > width * TURN.preLiftZone) {
      frontIdx.set(c);
      underIdx.set(c + 1);
      gx.set(x);
      gy.set(y);
      tx.set(x);
      ty.set(y);
      gain.set(1);
      preLifted.set(1);
      show.set(1);
      tx.set(withSpring(x - TURN.preLift * s, sp({ duration: 150, dampingRatio: 1 })));
    }
  }, [mode, ready, fade, tx, ty, gx, gy, height, baseX, baseY, startX, startY, gain, pastHalf, grainAt, cur, reduced, width, frontIdx, underIdx, preLifted, show, s]);

  const move = useCallback((x: number, y: number) => {
    'worklet';
    const m = mode.get();
    if (m === PENDING) {
      const dx = x - startX.get();
      if (Math.abs(dx) < TURN.pickSlop && Math.abs(y - startY.get()) < TURN.pickSlop) return;
      const c = cur.get();
      if (reduced) return;
      cancelAnimation(tx);
      if (dx < 0 && c < LAST) {
        // Forward: the point under the finger is the point of the sheet that follows it.
        const lift = preLifted.get() === 1 ? TURN.preLift * s : 0;
        frontIdx.set(c);
        underIdx.set(c + 1);
        gx.set(startX.get());
        gy.set(startY.get());
        baseX.set(startX.get() - lift);
        baseY.set(startY.get());
        gain.set(1);
      } else if (dx > 0 && c > 0) {
        // Back: the previous sheet lies turned over the spine; its fold follows the finger 1:1.
        frontIdx.set(c - 1);
        underIdx.set(c);
        gx.set(width);
        gy.set(startY.get());
        baseX.set(-width);
        baseY.set(startY.get());
        gain.set(TURN.backFactor);
      } else return;
      preLifted.set(0);
      mode.set(DRAG);
      show.set(1);
    }
    if (mode.get() !== DRAG) return;
    const nx = baseX.get() + gain.get() * (x - startX.get());
    const ny = baseY.get() + gain.get() * (y - startY.get());
    tx.set(nx);
    ty.set(ny);
    if (scripted.get() === 1) return;
    // Haptic grain: a tick per step of fold travel, and a light catch where the sheet passes half a turn.
    const g = grainAt.get();
    const now = clock.get();
    if (Math.hypot(x - g[0], y - g[1]) >= GRAIN.stepPt * s && now - grainTime.get() >= GRAIN.minGapMs) {
      grainAt.set([x, y]);
      grainTime.set(now);
      scheduleOnRN(haptic, HAPTIC_TICK);
    }
    const [cx, cy] = clampTip(gx.get(), gy.get(), nx, ny, height);
    const half = turnProgress(gx.get(), gy.get(), cx, cy) > TURN.commitProgress ? 1 : 0;
    if (half !== pastHalf.get()) {
      pastHalf.set(half);
      scheduleOnRN(haptic, HAPTIC_HALF);
    }
  }, [mode, startX, startY, cur, reduced, tx, ty, preLifted, s, frontIdx, underIdx, gx, gy, baseX, baseY, gain, width, show, scripted, grainAt, clock, grainTime, height, pastHalf]);

  const up = useCallback((x: number, y: number, vx: number, vy: number) => {
    'worklet';
    const m = mode.get();
    if (m === PENDING) {
      mode.set(IDLE);
      const dx = x - startX.get();
      const c = cur.get();
      if (reduced && Math.abs(dx) > 30) {
        turnBy(dx < 0 ? 1 : -1);
        return;
      }
      // A tap: the right margin or the hint turns forward, the left margin back; elsewhere a lifted corner drops.
      const hintTop = (TYPE.hint.top - 12) * s;
      const onHint = c === 0 && y > hintTop && y < hintTop + 44 * s;
      if (x > width * 0.66 || onHint) turnBy(1);
      else if (x < width * 0.33) turnBy(-1);
      else if (preLifted.get() === 1) {
        gain.set(1);
        release(false, 0, 0);
      }
      return;
    }
    if (m !== DRAG) return;
    const g = gain.get();
    const p = turnProgress(gx.get(), gy.get(), tx.get(), ty.get());
    const v = (g * vx) / QA_SPEED; // thresholds are in real time, also in slow-motion review
    const over = v < -TURN.commitVelocity || (p > TURN.commitProgress && v < TURN.commitVelocity);
    release(over, g * vx, g * vy);
  }, [mode, startX, cur, reduced, turnBy, s, width, preLifted, gain, release, gx, gy, tx, ty]);

  // Idle nudge: the bottom-right corner lifts and falls back with a wobble.
  const nudge = useCallback(() => {
    'worklet';
    const c = cur.get();
    if (mode.get() !== IDLE || c >= LAST || reduced || ready.get() === 0) return;
    lastNudge.set(clock.get());
    mode.set(NUDGING);
    frontIdx.set(c);
    underIdx.set(c + 1);
    gain.set(1);
    gx.set(width);
    gy.set(height);
    tx.set(width);
    ty.set(height);
    show.set(1);
    const lx = width + NUDGE.lift[0] * s;
    const ly = height + NUDGE.lift[1] * s;
    ty.set(withSequence(withSpring(ly, sp(NUDGE.up)), withDelay(ms(NUDGE.holdMs), withSpring(height, sp(NUDGE.down)))));
    tx.set(withSequence(withSpring(lx, sp(NUDGE.up)), withDelay(ms(NUDGE.holdMs), withSpring(width, sp(NUDGE.down), (g) => {
      if (!g) return;
      flexV.set(flexV.get() + NUDGE.kick);
      settleDone(false);
    }))));
  }, [cur, mode, reduced, ready, lastNudge, clock, frontIdx, underIdx, gain, gx, gy, tx, ty, show, width, height, s, flexV, settleDone]);

  const touchDown = useCallback((x: number, y: number) => {
    'worklet';
    lastTouch.set(clock.get());
    playing.set(0);
    if (scripted.get() === 1) {
      // A real finger takes over from the script mid-stroke.
      scripted.set(0);
      if (mode.get() === DRAG) {
        baseX.set(tx.get());
        baseY.set(ty.get());
        startX.set(x);
        startY.set(y);
        return;
      }
      if (mode.get() === PENDING) mode.set(IDLE);
    }
    down(x, y);
  }, [lastTouch, clock, playing, scripted, mode, baseX, baseY, tx, ty, startX, startY, down]);

  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => touchDown(e.x, e.y))
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      move(e.x, e.y);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      up(e.x, e.y, e.velocityX, e.velocityY);
    }), [touchDown, move, up, lastTouch, clock]);

  // One clock: the sheet's flex (a damped spring driven by the tip's speed), the ambient timers and the autoplay script.
  useFrameCallback((f) => {
    'worklet';
    const dt = (Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000)) * QA_SPEED;
    clock.set(clock.get() + dt * 1000);
    if (QA_HOLD === 'a') {
      // Re-assert the held frame every frame, so nothing at start-up can move it.
      mode.set(HOLD);
      frontIdx.set(0);
      underIdx.set(1);
      gx.set(HOLD_A.grab[0] * s);
      gy.set(HOLD_A.grab[1] * s);
      tx.set(HOLD_A.tip[0] * s);
      ty.set(HOLD_A.tip[1] * s);
      show.set(1);
    }

    const [cx, cy] = clampTip(gx.get(), gy.get(), tx.get(), ty.get(), height);
    const lt = lastTip.get();
    const speed = Math.hypot(cx - lt[0], cy - lt[1]) / Math.max(dt, 1e-3);
    lastTip.set([cx, cy]);
    const target = mode.get() === HOLD || reduced ? 0 : Math.min(TURN.flexMax, speed * TURN.flexPerSpeed);
    const k = TURN.flex.stiffness / TURN.flex.mass;
    const c = TURN.flex.damping / TURN.flex.mass;
    if (target !== 0 || Math.abs(flex.get()) > 1e-4 || Math.abs(flexV.get()) > 1e-4) {
      flexV.set(flexV.get() + (k * (target - flex.get()) - c * flexV.get()) * dt);
      flex.set(Math.max(-0.5, flex.get() + flexV.get() * dt));
    } else if (flex.get() !== 0) {
      flex.set(0);
      flexV.set(0);
    }

    if (QA_HOLD || ready.get() === 0) return;
    const now = clock.get();
    // Ambient: the cover's foil sweeps every few seconds while it rests.
    if (!reduced && cur.get() === LAST && mode.get() === IDLE && now - lastFoil.get() > FOIL.everyMs) arrive(LAST);

    if (!autoPlay) {
      // At most NUDGE.max hints per idle stretch: a hint that repeats forever becomes noise.
      const idle = now - lastTouch.get();
      if (idle > NUDGE.afterMs && idle < NUDGE.afterMs + NUDGE.everyMs * NUDGE.max && now - lastNudge.get() > NUDGE.everyMs) nudge();
      return;
    }
    if (playing.get() === 0) {
      if (mode.get() !== IDLE || now - lastTouch.get() < IDLE_RESUME_MS) return;
      // Resume from rest: from the title page at the top of the loop, otherwise at the riffle back from the cover.
      const page = cur.get();
      playing.set(1);
      scriptAt.set(-1);
      clock.set(page === 0 ? 0 : page === LAST ? SCRIPT[4].t0 - 400 : SCRIPT[page + 1].t0 - 400);
      return;
    }
    const t = clock.get() % LOOP_MS;
    if (t < 100) nudgeDone.set(0);
    if (t >= NUDGE_AT && nudgeDone.get() === 0) {
      nudgeDone.set(1);
      nudge();
    }
    let active = -1;
    for (let i = 0; i < SCRIPT.length; i++) {
      if (t >= SCRIPT[i].t0 && t <= SCRIPT[i].t1) active = i;
    }
    const prev = scriptAt.get();
    if (prev >= 0 && prev !== active) {
      // The stroke ended: lift with the path's speed at its end.
      const st = SCRIPT[prev];
      const flick = st.t1 - st.t0 < 700;
      const a = strokePoint(st.from, st.via, st.to, 0.98, flick);
      const b = strokePoint(st.from, st.via, st.to, 1, flick);
      const span = (0.02 * (st.t1 - st.t0)) / 1000;
      move(b[0] * s, b[1] * s);
      up(b[0] * s, b[1] * s, (((b[0] - a[0]) * s) / span) * QA_SPEED, (((b[1] - a[1]) * s) / span) * QA_SPEED);
      scripted.set(0);
    }
    if (active >= 0) {
      const st = SCRIPT[active];
      const u = (t - st.t0) / (st.t1 - st.t0);
      const pt = strokePoint(st.from, st.via, st.to, u, st.t1 - st.t0 < 700);
      if (prev !== active) {
        scripted.set(1);
        down(pt[0] * s, pt[1] * s);
      } else {
        move(pt[0] * s, pt[1] * s);
      }
    }
    scriptAt.set(active);
  });

  const makeUniforms = (sl: Slot, under: SharedValue<number> | null) => {
    'worklet';
    const g0 = sl.gx.value;
    const g1 = sl.gy.value;
    const [cx, cy] = clampTip(g0, g1, sl.tx.value, sl.ty.value, height);
    const d = Math.hypot(g0 - cx, g1 - cy);
    const hold = mode.value === HOLD;
    const ramp = hold ? 1 : rollRamp(d, 2 * g0, TURN.rampIn * s, TURN.rampOut * s);
    // The higher the sheet, the softer and wider its shadow (equal to the fitted values at the source's lift).
    const p = Math.min(1, d / Math.max(1, 2 * g0));
    return {
      size: [width, height],
      grab: [g0, g1],
      tip: [cx, cy],
      r0: CURL.radius * s * ramp * (1 + flex.value),
      r1: CURL.radiusSlope,
      rmin: CURL.radiusMin * s,
      frontIdx: sl.front.value,
      underIdx: under ? under.value : 0,
      shA: CURL.shadow * (LIFT.darkBase + LIFT.darkPerTurn * p),
      shW: CURL.shadowWidth * s * (LIFT.widthBase + LIFT.widthPerTurn * p),
      eA: CURL.edgeShadow,
      eW: CURL.edgeShadowWidth * s,
      amb1: CURL.frontLight,
      amb2: CURL.backLight,
      g2: CURL.backGamma,
      bg: CURL.backPaper,
      ba: CURL.showThrough,
      grain: CURL.grain,
      edgeInk: CURL.edgeInk,
      px,
      fade: under ? fade.value : -1,
      clearUnder: under ? 0 : 1,
      spec: CURL.sheen,
    };
  };
  const uniforms0 = useDerivedValue(() => makeUniforms(slot0, underIdx));
  const uniforms1 = useDerivedValue(() => makeUniforms(slot1, null));
  const uniforms2 = useDerivedValue(() => makeUniforms(slot2, null));

  const canvasStyle0 = useAnimatedStyle(() => ({ opacity: show.value * ready.value }));
  const canvasStyle1 = useAnimatedStyle(() => ({ opacity: slot1.on.value * ready.value }));
  const canvasStyle2 = useAnimatedStyle(() => ({ opacity: slot2.on.value * ready.value }));

  // Foil on the cover title: the sweep, or (with no sweep running) the phone's tilt.
  const foilRect = { x: 40 * s, y: 120 * s, width: 322 * s, height: 180 * s };
  const angle = (FOIL.angleDeg * Math.PI) / 180;
  const foilUniforms = useDerivedValue(() => {
    const sweeping = foilT.value > 0;
    const roll = tilt.sensor.value.roll;
    const center = sweeping
      ? (FOIL.rest + (FOIL.reach - FOIL.rest) * foilT.value) * s
      : (150 + roll * FOIL.tiltGain) * s;
    const amount = sweeping ? FOIL.amount : reduced ? 0 : FOIL.amount * Math.min(1, Math.abs(roll) * 6);
    return { dir: [Math.cos(angle), Math.sin(angle)], center, width: FOIL.width * s, amount };
  });
  const foilStyle = useAnimatedStyle(() => ({ opacity: cur.value === LAST && show.value === 0 ? 1 : 0 }));

  // Page-stack edge: one hairline per sheet still to turn, fading as each one goes.
  const stackLeft = useDerivedValue(() => withTiming(LAST - cur.value, { duration: 260, easing: EASE_OUT }));
  const stackStyles = [
    useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, stackLeft.value - 0)) })),
    useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, stackLeft.value - 1)) })),
    useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, stackLeft.value - 2)) })),
  ];

  const onAction = useCallback((e: AccessibilityActionEvent) => {
    scheduleOnUI(turnBy, e.nativeEvent.actionName === 'increment' ? 1 : -1);
  }, [turnBy]);

  const rect = { x: 0, y: 0, width, height };
  const allReady = snaps.every((im) => im !== null);
  const pages = [TitlePage, PrefacePage, ForewordPage, CoverPage];
  const curls = [
    { style: canvasStyle0, uniforms: uniforms0, z: 3 },
    { style: canvasStyle1, uniforms: uniforms1, z: 4 },
    { style: canvasStyle2, uniforms: uniforms2, z: 5 },
  ];

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={styles.root}
        collapsable={false}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Journal"
        accessibilityValue={{ text: `${COPY.pages[view.page]}, page ${view.page + 1} of 4` }}
        accessibilityActions={[{ name: 'increment', label: 'Next page' }, { name: 'decrement', label: 'Previous page' }]}
        onAccessibilityAction={onAction}
      >
        <StatusBar style="dark" />
        {pages.map((PageView, i) => (
          <View key={i} style={[StyleSheet.absoluteFill, { zIndex: i === view.page ? 2 : 1 }]} pointerEvents="none">
            <PageView ref={refs[i]} s={s} hidden={i !== view.page} twinkle={twinkle} />
          </View>
        ))}
        {allReady ? (
          <>
            {view.page === LAST ? (
            <Animated.View style={[styles.foil, foilRect, foilStyle]} pointerEvents="none">
              <Canvas style={StyleSheet.absoluteFill}>
                <Fill>
                  <Shader source={foilEffect} uniforms={foilUniforms}>
                    <ImageShader image={snaps[LAST]} fit="fill" rect={{ x: -foilRect.x, y: -foilRect.y, width, height }} />
                  </Shader>
                </Fill>
              </Canvas>
            </Animated.View>
            ) : null}
            {curls.slice(0, riffling ? 3 : 1).map((c, k) => (
              <Animated.View key={k} style={[StyleSheet.absoluteFill, { zIndex: c.z }, c.style]} pointerEvents="none">
                <Canvas style={StyleSheet.absoluteFill}>
                  <Fill>
                    <Shader source={curlEffect} uniforms={c.uniforms}>
                      {snaps.map((im, i) => (
                        <ImageShader key={i} image={im} fit="fill" rect={rect} />
                      ))}
                    </Shader>
                  </Fill>
                </Canvas>
              </Animated.View>
            ))}
          </>
        ) : null}
        {/* The binding's shadow and the edges of the pages still to turn sit over everything, like the book itself. */}
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(70,48,24,0.08)', 'rgba(70,48,24,0)']}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
          style={[styles.spine, { width: SPACE.md * s }]}
        />
        {stackStyles.map((st, i) => (
          <Animated.View key={i} pointerEvents="none" style={[styles.stack, { right: (i * STACK.gap + 0.4) * s, width: STACK.line }, st]} />
        ))}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAPER.base },
  foil: { position: 'absolute', zIndex: 2 },
  spine: { position: 'absolute', left: 0, top: 0, bottom: 0, zIndex: 6 },
  stack: { position: 'absolute', top: 0, bottom: 0, backgroundColor: STACK_INK, zIndex: 6 },
});
