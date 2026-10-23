import * as Haptics from 'expo-haptics';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  type SharedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  band,
  clamp01,
  CLUSTER,
  clusterAt,
  discAt,
  DRAG,
  GRAB,
  grabAt,
  LOOP,
  popScale,
  PRESS,
  pressAt,
  RESUME_AFTER,
  RISE,
  sheetAt,
  SPRINGS,
  tiltOf,
  TOGGLE_GAP,
  visitAt,
  VISIT,
  visitFrame,
} from './motion';
import { depthRank, layout, marker, markers, sheet as sheetBox, tiles } from './theme';

const AUTO = 0;
const TOUCH = 1;
const RETURN = 2;

/** Hit codes. */
export const HIT = { none: 0, checkbox: 1, tile: 2, marker: 10 } as const;

/** Half of a 44 pt touch target, in ref px. */
export const HIT_SLOP = 66;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
function physics(s: { omega: number; zeta: number }) {
  'worklet';
  return { stiffness: s.omega * s.omega, damping: 2 * s.zeta * s.omega, mass: 1 };
}

function hapticDetent() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function hapticVisit(dir: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (dir === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}
function hapticSelect() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

export type PlacesMotion = {
  sheet: SharedValue<number>;
  clus: SharedValue<number>;
  fill: SharedValue<number>;
  draw: SharedValue<number>;
  roll: SharedValue<number>;
  badge: SharedValue<number>;
  visitPop: SharedValue<number>;
  tapIdx: SharedValue<number>;
  tapPop: SharedValue<number>;
  press: SharedValue<number>;
  grab: SharedValue<number>;
  pressK: SharedValue<number>;
};

/**
 * One clock for the loop and the hand-off to touch. `u` = dp per ref px, `k` = dp per pt.
 * AUTO: every value is the timeline at loop time t. TOUCH: the sheet follows the finger and its springs,
 * the clusters chase the sheet at the loop's speed, taps toggle "visited" and pop markers.
 * RETURN: 2 s after the last touch everything settles to the pin's frame and the loop restarts at t = 0.
 * `calm` (Reduce Motion or a screen reader, followed live) stops the loop: a running loop settles to the
 * pin's frame and stays there; touch and the accessibility actions still work.
 */
export function usePlacesMotion({ autoPlay, calm, reduced, u, k }: { autoPlay: boolean; calm: boolean; reduced: boolean; u: number; k: number }) {
  const loop = autoPlay && !calm;
  const mode = useSharedValue(loop ? AUTO : TOUCH);
  const time = useSharedValue(0);
  const last = useSharedValue(-1);
  const now = useSharedValue(0);
  const touchedAt = useSharedValue(-1e9);

  const sheet = useSharedValue(0);
  const clus = useSharedValue(0);
  const fill = useSharedValue(0);
  const draw = useSharedValue(0);
  const roll = useSharedValue(0);
  const badge = useSharedValue(0);
  const visitPop = useSharedValue(1);
  const tapIdx = useSharedValue(-1);
  const tapPop = useSharedValue(1);
  const tapT0 = useSharedValue(-1e9);
  const press = useSharedValue(0);
  const grab = useSharedValue(0);
  const pressK = useSharedValue<number>(HIT.checkbox);

  const visitDir = useSharedValue(0);
  const visitT0 = useSharedValue(-1e9);
  const armed = useSharedValue(false);
  const dragging = useSharedValue(false);
  const start = useSharedValue(0);

  const put = (sv: SharedValue<number>, v: number) => {
    'worklet';
    if (Math.abs(sv.get() - v) > 1e-5) sv.set(v);
  };

  const applyVisit = (dir: number, tau: number) => {
    'worklet';
    const f = visitFrame(dir, tau, reduced);
    put(fill, f.fill);
    put(draw, f.draw);
    put(roll, f.roll);
    put(badge, f.badge);
    put(visitPop, f.pop);
  };

  useFrameCallback((info) => {
    const n = info.timestamp;
    now.set(n);
    const before = last.get();
    last.set(n);
    const dt = before < 0 ? 0 : Math.min(0.25, (n - before) / 1000);
    const m = mode.get();

    if (m === AUTO && !loop) {
      // The loop was switched off (Reduce Motion or a screen reader came on): settle to the pin's frame.
      const v = visitAt(time.get());
      visitDir.set(v.dir);
      visitT0.set(v.tau > 50 ? -1e9 : n - v.tau * 1000);
      if (v.dir === 1) {
        visitDir.set(0);
        visitT0.set(n);
      }
      if (press.get() > 0) press.set(withTiming(0, { duration: PRESS.inMs }));
      sheet.set(reduced ? withTiming(0, { duration: VISIT.reducedFade * 1000, easing: EASE_OUT }) : withSpring(0, physics(SPRINGS.sheetFall)));
      mode.set(RETURN);
      return;
    }
    if (m === AUTO) {
      const t = (time.get() + dt) % LOOP;
      time.set(t);
      put(sheet, sheetAt(t));
      put(clus, clusterAt(t));
      const v = visitAt(t);
      applyVisit(v.dir, v.tau);
      pressK.set(HIT.checkbox);
      put(press, pressAt(t));
      put(tapPop, 1);
      put(grab, grabAt(t));
      return;
    }

    // Touch: the clusters chase the sheet at the loop's own rate (reduced motion: they follow at once).
    const target = clamp01(sheet.get());
    const c = clus.get();
    if (reduced) put(clus, target);
    else if (c !== target) {
      const step = dt / CLUSTER.dur;
      put(clus, Math.abs(target - c) <= step ? target : c + Math.sign(target - c) * step);
    }
    applyVisit(visitDir.get(), (n - visitT0.get()) / 1000);
    put(tapPop, reduced ? 1 : popScale((n - tapT0.get()) / 1000));

    if (m === TOUCH && loop && !armed.get() && n - touchedAt.get() >= RESUME_AFTER) {
      mode.set(RETURN);
      sheet.set(withSpring(0, physics(SPRINGS.sheetFall)));
      if (visitDir.get() === 1) {
        visitDir.set(0);
        visitT0.set(n);
      }
    } else if (m === RETURN && Math.abs(sheet.get()) < 1e-3 && clus.get() === 0 && n - visitT0.get() > 600) {
      cancelAnimation(sheet);
      sheet.set(0);
      time.set(0);
      visitT0.set(-1e9);
      mode.set(loop ? AUTO : TOUCH);
    }
  });

  const springTo = (target: number, velocity: number) => {
    'worklet';
    return reduced
      ? withTiming(target, { duration: VISIT.reducedFade * 1000, easing: EASE_OUT })
      : withSpring(target, { ...physics(SPRINGS.sheetRise), velocity });
  };

  /** The loop hands over without a jump: values stay where they are, a sheet mid-move finishes into a detent. */
  const enterTouch = () => {
    'worklet';
    const n = now.get();
    if (mode.get() === AUTO) {
      const v = visitAt(time.get());
      visitDir.set(v.dir);
      visitT0.set(v.tau > 50 ? -1e9 : n - v.tau * 1000);
      const s = sheet.get();
      const to = s > 0.5 ? 1 : 0;
      if (Math.abs(s - to) > 1e-3) sheet.set(springTo(to, 0));
      if (press.get() > 0) press.set(withSpring(0, physics(SPRINGS.press)));
    }
    mode.set(TOUCH);
    touchedAt.set(n);
  };

  const hit = (x: number, y: number) => {
    'worklet';
    const X = x / u;
    const Y = y / u;
    const s = sheet.get();
    const off = RISE * 3 * s;
    const slop = HIT_SLOP;
    const cb = layout.checkbox;
    if (Math.abs(X - (cb.x + cb.d / 2)) <= slop && Math.abs(Y - (cb.y + cb.d / 2 - off)) <= slop) return HIT.checkbox;
    if (X >= tiles.xs[0] && X <= tiles.xs[0] + tiles.w && Y >= tiles.y - off && Y <= tiles.y + tiles.h - off) return HIT.tile;
    if (Y < sheetBox.top * 3 - off) {
      // Hit the marker where it is drawn: on its ground spot, lifted and scaled while the map is pitched.
      const tp = reduced ? 0 : tiltOf(s);
      for (let i = markers.length - 1; i >= 0; i--) {
        const ax = markers[i].x - marker.discDx;
        const ay = markers[i].base - marker.discDy;
        const disc = discAt(ax, ay, tp, depthRank[markers[i].key], marker.disc / 2);
        const right = disc.x + (marker.discDx + marker.hitW) * disc.m;
        if (X >= disc.x - slop && X <= right && Math.abs(Y - disc.y) <= slop * Math.max(1, disc.m)) return HIT.marker + i;
      }
    }
    return HIT.none;
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-DRAG.slop, DRAG.slop])
    .onBegin((e) => {
      const top = (sheetBox.top * 3 - RISE * 3 * sheet.get()) * u;
      if (e.y < top) return;
      armed.set(true);
      enterTouch();
    })
    .onStart(() => {
      if (!armed.get()) return;
      grab.set(withTiming(1, { duration: GRAB.in * 1000, easing: EASE_OUT }));
      cancelAnimation(sheet);
      start.set(sheet.get());
      dragging.set(true);
    })
    .onUpdate((e) => {
      if (!dragging.get()) return;
      sheet.set(band(start.get() - e.translationY / (RISE * k)));
    })
    .onEnd((e) => {
      if (!dragging.get()) return;
      const v = -e.velocityY / (RISE * k);
      const s = sheet.get();
      const to = s + v * DRAG.project > 0.5 ? 1 : 0;
      sheet.set(springTo(to, v));
      if (to !== Math.round(clamp01(start.get()))) scheduleOnRN(hapticDetent);
    })
    .onFinalize(() => {
      if (grab.get() > 0) grab.set(withTiming(0, { duration: GRAB.out * 1000, easing: EASE_OUT }));
      armed.set(false);
      dragging.set(false);
      touchedAt.set(now.get());
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onBegin((e) => {
      const h = hit(e.x, e.y);
      if (h !== HIT.checkbox && h !== HIT.tile) return;
      enterTouch();
      pressK.set(h);
      press.set(withTiming(1, { duration: PRESS.inMs, easing: EASE_OUT }));
    })
    .onEnd((e, ok) => {
      if (!ok) return;
      enterTouch();
      const h = hit(e.x, e.y);
      const n = now.get();
      if (h === HIT.checkbox || h === HIT.tile) {
        if (n - visitT0.get() < TOGGLE_GAP * 1000) return;
        const dir = visitDir.get() === 1 ? 0 : 1;
        visitDir.set(dir);
        visitT0.set(n);
        scheduleOnRN(hapticVisit, dir);
      } else if (h >= HIT.marker) {
        tapIdx.set(h - HIT.marker);
        tapT0.set(n);
        scheduleOnRN(hapticSelect);
      }
    })
    .onFinalize(() => {
      if (press.get() > 0) press.set(reduced ? withTiming(0, { duration: PRESS.inMs }) : withSpring(0, physics(SPRINGS.press)));
      touchedAt.set(now.get());
    });

  /* ---- Accessibility actions: the same moves a finger makes, for VoiceOver / TalkBack ---- */
  const sheetToUI = (to: number) => {
    'worklet';
    enterTouch();
    const from = Math.round(clamp01(sheet.get()));
    cancelAnimation(sheet);
    sheet.set(springTo(to, 0));
    if (to !== from) scheduleOnRN(hapticDetent);
    touchedAt.set(now.get());
  };
  const toggleVisitUI = () => {
    'worklet';
    enterTouch();
    const n = now.get();
    if (n - visitT0.get() < TOGGLE_GAP * 1000) return;
    const dir = visitDir.get() === 1 ? 0 : 1;
    visitDir.set(dir);
    visitT0.set(n);
    scheduleOnRN(hapticVisit, dir);
  };
  const tapMarkerUI = (i: number) => {
    'worklet';
    enterTouch();
    tapIdx.set(i);
    tapT0.set(now.get());
    scheduleOnRN(hapticSelect);
  };
  const actions = {
    sheetTo: (to: number) => scheduleOnUI(sheetToUI, to),
    toggleVisit: () => scheduleOnUI(toggleVisitUI),
    tapMarker: (i: number) => scheduleOnUI(tapMarkerUI, i),
  };

  const motion: PlacesMotion = { sheet, clus, fill, draw, roll, badge, visitPop, tapIdx, tapPop, press, grab, pressK };
  return { motion, gesture: Gesture.Race(pan, tap), actions };
}
