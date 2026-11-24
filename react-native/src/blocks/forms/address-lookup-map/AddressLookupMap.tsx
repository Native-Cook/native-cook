import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDecay,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { ADDRESSES, type Address, WORLD, matchRange, rank } from './addressLookupData';
import {
  CHOOSE_LEN,
  DROP_LEN,
  type Dot,
  FILL_GAP,
  FILL_LEN,
  FLY_LEN,
  IDLE,
  LOOP,
  MAP_ROT,
  MAX_SCALE,
  MIN_SCALE_K,
  NEAR,
  OVERVIEW_X,
  OVERVIEW_Y,
  type Pose,
  RETURN_LEN,
  RING_LEN,
  ROW_H,
  SCRIPT,
  TYPED,
  T_STILL,
  autoDot,
  autoPose,
  clamp01,
  dragToWorld,
  dropSquash,
  dropY,
  overviewScale,
  seg,
  stepAt,
} from './addressLookupMotion';
import { DoorToggle } from './DoorToggle';
import { type Camera, type PinPose, StreetMap } from './StreetMap';

/*
 Address Lookup Map — the delivery step of Parcelo's checkout.

 Typing an address shows suggestions that rise in with the matched letters in bold and re-rank as
 the query grows. Picking one folds the list into the field, the drawn map (StreetMap.tsx, no map
 tiles) flies to the address, the pin drops with a bounce and a soft ring, and unit, postcode and
 city fill in one after the other. Dragging the map slides the town under the pin to fine-tune the
 drop-off point; pinching zooms.

 The loop (addressLookupMotion.ts) and a person drive the same values: while the loop plays every
 value comes from `autoPose(t)`; a touch copies the frame at that moment into shared values and the
 person takes over. Two seconds after the last touch it eases back and the loop starts again.
*/

type Props = {
  /** Types, picks and fine-tunes an address every LOOP seconds until the screen is touched. */
  autoPlay?: boolean;
  /** Called by Continue with the chosen address and the details as filled in. */
  onContinue?: (address: Address, details: { unit: string; postcode: string; city: string; leaveAtDoor: boolean }) => void;
};

const ACCENT = '#E0562E';
const INK = '#1E1D1A';
const MUTED = '#77736A';
const PAGE = '#F6F4EF';
const CARD = '#FFFEFB';
const HAIRLINE = 'rgba(40,34,24,0.12)';
const DOT_SIZE = 44;
const ICON_X = 44;
const EASE = Easing.bezier(0.23, 1, 0.32, 1);
/** The switch's state when the screen opens and after the idle return. */
const LEAVE_AT_DOOR_DEFAULT = true;
const EASE_IN_OUT = Easing.inOut(Easing.cubic);
const EMPTY: number[] = [];

function makeGeo(W: number, H: number, top: number, bottom: number) {
  const compact = H < 740;
  const margin = Math.max(20, (W - 440) / 2);
  const width = W - margin * 2;
  const headerTop = top + 2;
  const titleTop = headerTop + (compact ? 52 : 62);
  const titleSize = compact ? 23 : 27;
  const fieldTop = titleTop + (compact ? 38 : 46);
  const fieldH = compact ? 50 : 54;
  const mapTop = fieldTop + fieldH + 12;
  const buttonH = compact ? 50 : 54;
  const buttonTop = H - Math.max(bottom, 14) - 8 - buttonH;
  const boxH = compact ? 54 : 58;
  const row2 = buttonTop - 14 - boxH;
  const row1 = row2 - 10 - boxH;
  const mapH = row1 - 14 - mapTop;
  return { W, H, compact, margin, width, headerTop, titleTop, titleSize, fieldTop, fieldH, mapTop, mapH, boxH, row1, row2, buttonTop, buttonH, listTop: fieldTop + fieldH + 6 };
}

type Geo = ReturnType<typeof makeGeo>;

function rubber(x: number, lo: number, hi: number) {
  'worklet';
  if (x < lo) return lo - (lo - x) / (1 + (lo - x) / 160);
  if (x > hi) return hi + (x - hi) / (1 + (x - hi) / 160);
  return x;
}

/**
 * How far the camera's centre may go on one axis at scale s: far enough that the (turned) map window stays on the
 * drawn town, so a pan or a fling never shows the blank paper past its edge.
 */
function cameraBound(s: number, w: number, h: number, axis: 0 | 1) {
  'worklet';
  const c = Math.abs(Math.cos(MAP_ROT));
  const n = Math.abs(Math.sin(MAP_ROT));
  const half = (axis === 0 ? w * c + h * n : w * n + h * c) / 2 / s;
  const lo = Math.min(WORLD / 2, half);
  return [lo, WORLD - lo];
}

function lightTap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function distanceKm(a: Address) {
  return `${(Math.hypot(a.x - OVERVIEW_X, a.y - OVERVIEW_Y) * 0.0042 + 0.6).toFixed(1)} km`;
}

/** Values a person drives; each mirrors a field of `Pose`. */
function useUserValues() {
  const focus = useSharedValue(0);
  const caret = useSharedValue(0);
  const text = useSharedValue(1);
  const placeholder = useSharedValue(1);
  const list = useSharedValue(0);
  const count = useSharedValue(0);
  const choose = useSharedValue(0);
  const chosen = useSharedValue(0);
  const camX = useSharedValue(OVERVIEW_X);
  const camY = useSharedValue(OVERVIEW_Y);
  const camS = useSharedValue(0.3);
  const drop = useSharedValue(0);
  const pin = useSharedValue(0);
  const ring1 = useSharedValue(0);
  const ring2 = useSharedValue(0);
  const lift = useSharedValue(0);
  const callA = useSharedValue(0);
  const callB = useSharedValue(0);
  const fill0 = useSharedValue(0);
  const fill1 = useSharedValue(0);
  const fill2 = useSharedValue(0);
  const ready = useSharedValue(0);
  return useMemo(
    () => ({ focus, caret, text, placeholder, list, count, choose, chosen, camX, camY, camS, drop, pin, ring1, ring2, lift, callA, callB, fill0, fill1, fill2, ready }),
    [focus, caret, text, placeholder, list, count, choose, chosen, camX, camY, camS, drop, pin, ring1, ring2, lift, callA, callB, fill0, fill1, fill2, ready],
  );
}

type UserValues = ReturnType<typeof useUserValues>;

/** Copies a pose into the person's values (the moment a touch takes over, or the rest pose). */
function writePose(u: UserValues, p: Pose) {
  'worklet';
  u.focus.set(p.focus);
  u.caret.set(0);
  u.text.set(p.text);
  u.placeholder.set(p.placeholder);
  u.list.set(p.list);
  u.count.set(p.count);
  u.choose.set(p.choose);
  u.chosen.set(p.chosen);
  u.camX.set(p.camX);
  u.camY.set(p.camY);
  u.camS.set(p.camS);
  u.drop.set(p.drop);
  u.pin.set(p.pin);
  u.ring1.set(0);
  u.ring2.set(0);
  u.lift.set(0);
  u.callA.set(p.callA);
  u.callB.set(p.callB);
  u.fill0.set(p.fill0);
  u.fill1.set(p.fill1);
  u.fill2.set(p.fill2);
  u.ready.set(p.ready);
}

/** Eases every value to the loop's first frame. */
function easeToRest(u: UserValues, p: Pose, d: number) {
  'worklet';
  const o = { duration: d, easing: EASE_IN_OUT };
  const quick = { duration: d * 0.4 };
  u.focus.set(withTiming(0, quick));
  u.caret.set(0);
  u.text.set(withTiming(0, quick));
  u.list.set(withTiming(0, quick));
  u.callA.set(withTiming(0, quick));
  u.callB.set(withTiming(0, quick));
  u.fill0.set(withTiming(0, quick));
  u.fill1.set(withTiming(0, quick));
  u.fill2.set(withTiming(0, quick));
  u.ready.set(withTiming(0, quick));
  u.pin.set(withTiming(0, quick));
  u.lift.set(withTiming(0, quick));
  u.camX.set(withTiming(p.camX, o));
  u.camY.set(withTiming(p.camY, o));
  u.camS.set(withTiming(p.camS, o));
}

export function AddressLookupMap({ autoPlay = true, onContinue }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const still = autoPlay && reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);
  const s0 = overviewScale(g.width);

  // ---- UI thread -----------------------------------------------------------------------------------
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  /** 0 = the loop, 1 = a person, 2 = easing back to rest, 3 = waiting for React to reset. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  /** When a pick's own film (fly, drop, fills) ends: the idle return also waits IDLE after this. */
  const filmEnd = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const editing = useSharedValue(0);
  const lift = useSharedValue(0);
  const u = useUserValues();
  const loopTime = useDerivedValue(() => (still ? T_STILL : clock.get()));

  // ---- React: changes on keystrokes and picks only -------------------------------------------------------
  const [driver, setDriver] = useState<'auto' | 'user'>('auto');
  const [step, setStep] = useState(0);
  const [takeT, setTakeT] = useState(0);
  const [resetTick, setResetTick] = useState(0);
  const [uQuery, setUQuery] = useState('');
  const [uSelected, setUSelected] = useState<number | null>(null);
  const [unit, setUnit] = useState('');
  const [postcode, setPostcode] = useState('');
  const [city, setCity] = useState('');
  const [leaveAtDoor, setLeaveAtDoor] = useState(LEAVE_AT_DOOR_DEFAULT);
  const inputRef = useRef<TextInput | null>(null);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const keyboard = useRef(0);

  const user = driver === 'user';
  const query = user ? uQuery : SCRIPT[step].query;
  const selected = user ? uSelected : SCRIPT[step].selected;
  const picked = selected === null ? null : ADDRESSES[selected];
  const filled = picked !== null && SCRIPT[step].filled;
  const shown = user ? { unit, postcode, city } : { unit: filled ? picked.unit : '', postcode: filled ? picked.postcode : '', city: filled ? picked.city : '' };
  const listOpen = user ? uQuery.length > 0 && uSelected === null : step >= 1 && step <= TYPED;
  const slots = useMemo(() => {
    const q = user ? (listOpen ? uQuery : '') : SCRIPT[Math.min(step, TYPED)].query;
    const list = rank(q);
    return ADDRESSES.map((_, i) => list.indexOf(i));
  }, [listOpen, user, uQuery, step]);

  const pose = useDerivedValue<Pose>(() => {
    if (mode.get() === 0) return autoPose(loopTime.get(), s0);
    return {
      focus: u.focus.get(),
      caret: u.caret.get(),
      text: u.text.get(),
      placeholder: u.placeholder.get(),
      list: u.list.get(),
      count: u.count.get(),
      choose: u.choose.get(),
      chosen: u.chosen.get(),
      rowY: EMPTY,
      rowA: EMPTY,
      camX: u.camX.get(),
      camY: u.camY.get(),
      camS: u.camS.get(),
      drop: u.drop.get(),
      pin: u.pin.get(),
      ring1: u.ring1.get(),
      ring2: u.ring2.get(),
      lift: u.lift.get(),
      callA: u.callA.get(),
      callB: u.callB.get(),
      fill0: u.fill0.get(),
      fill1: u.fill1.get(),
      fill2: u.fill2.get(),
      ready: u.ready.get(),
    };
  });
  const camera = useDerivedValue<Camera>(() => {
    const p = pose.get();
    return { x: p.camX, y: p.camY, s: p.camS };
  });
  const pinPose = useDerivedValue<PinPose>(() => {
    const p = pose.get();
    return { y: dropY(p.drop), squash: dropSquash(p.drop), alpha: p.pin, lift: p.lift, ring1: p.ring1, ring2: p.ring2 };
  });
  const dot = useDerivedValue<Dot>(() => (mode.get() === 0 && auto ? autoDot(clock.get()) : { target: 0, alpha: 0, scale: 1, dx: 0, dy: 0 }));

  // The loop's script step reaches React only when it changes (a keystroke, a pick, the clear).
  useAnimatedReaction(
    () => (mode.get() === 0 ? stepAt(loopTime.get()) : -1),
    (k, prev) => {
      if (k >= 0 && k !== prev) scheduleOnRN(setStep, k);
    },
  );

  const resetToAuto = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    setDriver('auto');
    setStep(0);
    setUQuery('');
    setUSelected(null);
    setUnit('');
    setPostcode('');
    setCity('');
    setLeaveAtDoor(LEAVE_AT_DOOR_DEFAULT);
    setResetTick((n) => n + 1);
  }, []);

  const startLoop = useCallback(() => {
    'worklet';
    clock.set(0);
    mode.set(0);
  }, [clock, mode]);

  useEffect(() => {
    if (resetTick > 0) scheduleOnUI(startLoop);
  }, [resetTick, startLoop]);

  useEffect(() => {
    const list = timers.current;
    return () => list.forEach(clearTimeout);
  }, []);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (m === 1) {
      if (editing.get() === 0 && now.get() - lastTouch.get() > IDLE && now.get() - filmEnd.get() > IDLE) {
        mode.set(2);
        returnAt.set(now.get());
        easeToRest(u, autoPose(0, s0), reduced ? 0 : RETURN_LEN * 1000);
      }
      return;
    }
    if (m === 2 && now.get() - returnAt.get() > RETURN_LEN) {
      mode.set(3);
      scheduleOnRN(resetToAuto);
    }
  });

  // ---- Hand-over -----------------------------------------------------------------------------------
  const freeze = useCallback(
    (t: number) => {
      'worklet';
      writePose(u, autoPose(t, s0));
      mode.set(1);
      lastTouch.set(now.get());
    },
    [lastTouch, mode, now, s0, u],
  );

  /** React side of a take-over at loop time t: the loop's values become the person's. */
  const adopt = useCallback(
    (t: number) => {
      const s = SCRIPT[stepAt(t)];
      const a = s.selected === null ? null : ADDRESSES[s.selected];
      setUQuery(s.query);
      setUSelected(s.selected);
      setUnit(s.filled && a ? a.unit : '');
      setPostcode(s.filled && a ? a.postcode : '');
      setCity(s.filled && a ? a.city : '');
      setTakeT(t);
      setDriver('user');
    },
    [],
  );

  const touch = useCallback(() => {
    lastTouch.set(now.get());
  }, [lastTouch, now]);

  /** Any touch: the loop stops where it is and the person drives from there. */
  const takeOver = useCallback(() => {
    const m = mode.get();
    if (m === 0) {
      const t = still ? T_STILL : auto ? clock.get() : 0;
      adopt(t);
      scheduleOnUI(freeze, t);
      return;
    }
    if (m === 2) mode.set(1);
    touch();
  }, [adopt, auto, clock, freeze, mode, still, touch]);

  const adoptFromGesture = useCallback(
    (t: number) => {
      adopt(t);
    },
    [adopt],
  );

  // ---- The person's actions ---------------------------------------------------------------------------
  const listUI = useCallback(
    (open: boolean, n: number, d: number) => {
      'worklet';
      u.list.set(withTiming(open ? 1 : 0, { duration: d * 0.3, easing: EASE }));
      u.count.set(withSpring(n, { duration: d, dampingRatio: 0.82 }));
      u.choose.set(0);
      u.text.set(1);
      u.placeholder.set(1);
    },
    [u],
  );

  const onChangeQuery = (v: string) => {
    takeOver();
    setUQuery(v);
    if (uSelected !== null) setUSelected(null);
    scheduleOnUI(listUI, v.length > 0, rank(v).length, reduced ? 0 : 420);
  };

  const chooseUI = useCallback(
    (i: number, x: number, y: number, fast: boolean) => {
      'worklet';
      const k = fast ? 0 : 1;
      u.chosen.set(i);
      u.choose.set(0);
      u.choose.set(withTiming(1, { duration: CHOOSE_LEN * 1000 * k, easing: EASE_IN_OUT }));
      u.focus.set(withTiming(0, { duration: 200 * k }));
      u.text.set(0);
      u.text.set(withDelay(200 * k, withTiming(1, { duration: 230 * k })));
      u.camX.set(withTiming(x, { duration: FLY_LEN * 1000 * k, easing: EASE_IN_OUT }));
      u.camY.set(withTiming(y, { duration: FLY_LEN * 1000 * k, easing: EASE_IN_OUT }));
      u.camS.set(withTiming(NEAR, { duration: FLY_LEN * 1000 * k, easing: EASE_IN_OUT }));
      const land = (FLY_LEN - 0.1) * 1000 * k;
      u.pin.set(0);
      u.drop.set(0);
      u.pin.set(withDelay(land, withTiming(1, { duration: 80 * k })));
      u.drop.set(withDelay(land, withTiming(1, { duration: DROP_LEN * 1000 * k, easing: Easing.linear })));
      u.ring1.set(0);
      u.ring2.set(0);
      u.ring1.set(withDelay(land + 330 * k, withTiming(1, { duration: RING_LEN * 1000 * k, easing: Easing.linear })));
      u.ring2.set(withDelay(land + 750 * k, withTiming(1, { duration: RING_LEN * 1000 * k, easing: Easing.linear })));
      u.callB.set(withTiming(0, { duration: 150 * k }));
      u.callA.set(0);
      u.callA.set(withDelay(land + 550 * k, withTiming(1, { duration: 300 * k, easing: EASE })));
      const fills = [u.fill0, u.fill1, u.fill2];
      for (let j = 0; j < 3; j += 1) {
        fills[j].set(0);
        fills[j].set(withDelay(land + (600 + j * FILL_GAP * 1000) * k, withTiming(1, { duration: FILL_LEN * 1000 * k, easing: EASE })));
      }
      u.ready.set(withDelay(land + 1050 * k, withTiming(1, { duration: 400 * k })));
      // The idle return counts from the end of the pick's film (the form filled, Continue ready), not from the tap,
      // or it would ease the screen back while the fields are still filling in.
      filmEnd.set(now.get() + (land + 1450 * k) / 1000);
    },
    [filmEnd, now, u],
  );

  const choose = (i: number) => {
    takeOver();
    const a = ADDRESSES[i];
    setUQuery(a.street);
    setUSelected(i);
    setUnit(a.unit);
    setPostcode(a.postcode);
    setCity(a.city);
    inputRef.current?.blur();
    Keyboard.dismiss();
    Haptics.selectionAsync().catch(() => {});
    scheduleOnUI(chooseUI, i, a.x, a.y, reduced);
    timers.current.forEach(clearTimeout);
    if (!reduced) timers.current = [setTimeout(lightTap, (FLY_LEN - 0.1 + DROP_LEN * 0.5) * 1000)];
  };

  const clearQuery = () => {
    takeOver();
    setUQuery('');
    setUSelected(null);
    scheduleOnUI(listUI, false, 0, reduced ? 0 : 300);
    inputRef.current?.focus();
  };

  const recenterUI = useCallback(
    (x: number, y: number, s: number) => {
      'worklet';
      u.camX.set(withTiming(x, { duration: 700, easing: EASE_IN_OUT }));
      u.camY.set(withTiming(y, { duration: 700, easing: EASE_IN_OUT }));
      u.camS.set(withTiming(s, { duration: 700, easing: EASE_IN_OUT }));
    },
    [u],
  );

  const recenter = () => {
    takeOver();
    lightTap();
    if (picked) scheduleOnUI(recenterUI, picked.x, picked.y, NEAR);
    else scheduleOnUI(recenterUI, OVERVIEW_X, OVERVIEW_Y, s0);
  };

  const fillUI = useCallback(
    (j: number) => {
      'worklet';
      [u.fill0, u.fill1, u.fill2][j].set(1);
    },
    [u],
  );

  const onDetail = (j: number, v: string) => {
    takeOver();
    if (j === 0) setUnit(v);
    if (j === 1) setPostcode(v.replace(/\D/g, '').slice(0, 4));
    if (j === 2) setCity(v);
    scheduleOnUI(fillUI, j);
  };

  const ok = user ? uSelected !== null && postcode.length === 4 && city.trim().length > 0 : true;
  useEffect(() => {
    if (!user) return;
    u.ready.set(withTiming(ok ? 1 : 0, { duration: reduced ? 0 : 300 }));
  }, [ok, reduced, u.ready, user]);

  const onContinuePress = () => {
    takeOver();
    if (!ok || !picked) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
      return;
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onContinue?.(picked, { unit, postcode, city, leaveAtDoor });
  };

  // Keyboard: the lower fields rise above it while one of them is being edited.
  const raise = useCallback(
    (top: number) => {
      const need = top < 0 || keyboard.current === 0 ? 0 : top + g.boxH + 16 - (g.H - keyboard.current);
      lift.set(withTiming(Math.max(0, need), { duration: reduced ? 0 : 280, easing: Easing.out(Easing.cubic) }));
    },
    [g, lift, reduced],
  );
  const raised = useRef(-1);
  useEffect(() => {
    const show = Keyboard.addListener('keyboardWillShow', (e) => {
      keyboard.current = e.endCoordinates.height;
      raise(raised.current);
    });
    const hide = Keyboard.addListener('keyboardWillHide', () => {
      keyboard.current = 0;
      raise(-1);
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [raise]);

  const focusUI = useCallback(
    (on: boolean) => {
      'worklet';
      editing.set(on ? 1 : 0);
      u.focus.set(withTiming(on ? 1 : 0, { duration: 200 }));
      lastTouch.set(now.get());
    },
    [editing, lastTouch, now, u.focus],
  );
  const editingUI = useCallback(
    (on: boolean) => {
      'worklet';
      editing.set(on ? 1 : 0);
      lastTouch.set(now.get());
    },
    [editing, lastTouch, now],
  );

  // ---- Map gestures -------------------------------------------------------------------------------------
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const startS = useSharedValue(1);
  const mapW = g.width;
  const mapH = g.mapH;
  const grabMap = () => {
    'worklet';
    if (mode.get() === 0) {
      const t = still ? T_STILL : auto ? clock.get() : 0;
      writePose(u, autoPose(t, s0));
      mode.set(1);
      scheduleOnRN(adoptFromGesture, t);
    } else if (mode.get() === 2) {
      mode.set(1);
    }
    lastTouch.set(now.get());
  };
  const pan = Gesture.Pan()
    .minDistance(4)
    .averageTouches(true)
    .onStart(() => {
      grabMap();
      startX.set(u.camX.get());
      startY.set(u.camY.get());
      u.lift.set(withSpring(1, { duration: 300, dampingRatio: 0.8 }));
      u.callA.set(withTiming(0, { duration: 150 }));
      u.callB.set(withTiming(0, { duration: 150 }));
    })
    .onUpdate((e) => {
      const sc = u.camS.get();
      const o = dragToWorld(e.translationX, e.translationY, sc);
      const bx = cameraBound(sc, mapW, mapH, 0);
      const by = cameraBound(sc, mapW, mapH, 1);
      u.camX.set(rubber(startX.get() + o[0], bx[0], bx[1]));
      u.camY.set(rubber(startY.get() + o[1], by[0], by[1]));
      lastTouch.set(now.get());
    })
    .onEnd((e) => {
      const sc = u.camS.get();
      const v = dragToWorld(e.velocityX, e.velocityY, sc);
      const bx = cameraBound(sc, mapW, mapH, 0);
      const by = cameraBound(sc, mapW, mapH, 1);
      u.camX.set(withDecay({ velocity: v[0], clamp: [bx[0], bx[1]], rubberBandEffect: true, rubberBandFactor: 0.7 }));
      u.camY.set(withDecay({ velocity: v[1], clamp: [by[0], by[1]], rubberBandEffect: true, rubberBandFactor: 0.7 }));
      u.lift.set(withSpring(0, { duration: 450, dampingRatio: 0.55 }));
      if (u.pin.get() > 0.5) {
        u.callB.set(withDelay(260, withTiming(1, { duration: 280 })));
        scheduleOnRN(lightTap);
      }
      lastTouch.set(now.get());
    });
  const pinch = Gesture.Pinch()
    .onStart(() => {
      grabMap();
      startS.set(u.camS.get());
    })
    .onUpdate((e) => {
      const lo = s0 * MIN_SCALE_K;
      u.camS.set(rubber(startS.get() * e.scale, lo, MAX_SCALE));
      lastTouch.set(now.get());
    })
    .onEnd(() => {
      const lo = s0 * MIN_SCALE_K;
      const target = Math.min(MAX_SCALE, Math.max(lo, u.camS.get()));
      u.camS.set(withSpring(target, { duration: 400, dampingRatio: 0.85 }));
      // Zooming out widens the window: bring the centre back inside the town at the scale it settles on.
      const bx = cameraBound(target, mapW, mapH, 0);
      const by = cameraBound(target, mapW, mapH, 1);
      const cx = u.camX.get();
      const cy = u.camY.get();
      if (cx < bx[0] || cx > bx[1]) u.camX.set(withSpring(Math.min(bx[1], Math.max(bx[0], cx)), { duration: 400, dampingRatio: 0.85 }));
      if (cy < by[0] || cy > by[1]) u.camY.set(withSpring(Math.min(by[1], Math.max(by[0], cy)), { duration: 400, dampingRatio: 0.85 }));
      lastTouch.set(now.get());
    });
  const mapGesture = Gesture.Simultaneous(pan, pinch);

  // ---- Styles from the pose -------------------------------------------------------------------------------
  const liftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -lift.get() }] }));
  const fieldStyle = useAnimatedStyle(() => ({
    borderColor: interpolateColor(pose.get().focus, [0, 1], [HAIRLINE, ACCENT]),
  }));
  const ringStyle = useAnimatedStyle(() => ({ opacity: pose.get().focus }));
  const textStyle = useAnimatedStyle(() => ({ opacity: pose.get().text }));
  const placeholderStyle = useAnimatedStyle(() => ({ opacity: pose.get().placeholder }));
  const searchIcon = useAnimatedStyle(() => ({ opacity: 1 - pose.get().pin }));
  const pinIcon = useAnimatedStyle(() => {
    const p = pose.get().pin;
    return { opacity: p, transform: [{ scale: 0.6 + 0.4 * p }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const c = p.choose;
    return {
      opacity: p.list * (1 - seg(c, 0.5, 1)),
      height: (8 + p.count * ROW_H) * (1 - c * c),
    };
  });

  // Caret of the loop's typing: the width of the typed text, measured with a trailing bar so a
  // trailing space is not trimmed, minus the bar's own width.
  const caretW = useSharedValue(0);
  const textW = useRef(0);
  const barW = useRef(0);
  const caretStyle = useAnimatedStyle(() => ({
    opacity: pose.get().caret,
    transform: [{ translateX: ICON_X + caretW.get() }],
  }));

  const showClear = user && uQuery.length > 0;

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <Animated.View style={[StyleSheet.absoluteFill, liftStyle]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={Keyboard.dismiss} accessible={false} />

        <Header g={g} />

        <Text style={[styles.title, { left: g.margin, top: g.titleTop, fontSize: g.titleSize, lineHeight: g.titleSize + 6, width: g.width }]} numberOfLines={1}>
          Where should we deliver?
        </Text>

        {/* The map */}
        <View style={[styles.mapShadow, { left: g.margin, top: g.mapTop, width: g.width, height: g.mapH }]}>
          <GestureDetector gesture={mapGesture}>
            <View style={[styles.mapClip, { width: g.width, height: g.mapH }]}>
              <StreetMap width={g.width} height={g.mapH} camera={camera} pin={pinPose} rotation={MAP_ROT} accent={ACCENT} />
              <Callouts pose={pose} w={g.width} h={g.mapH} />
              <Pressable style={styles.recenter} onPress={recenter} accessibilityRole="button" accessibilityLabel="Recenter map" hitSlop={8}>
                <SymbolView name={{ ios: 'location.fill', android: 'my_location', web: 'my_location' }} size={15} tintColor={INK} />
              </Pressable>
              <View style={styles.mapEdge} pointerEvents="none" />
            </View>
          </GestureDetector>
        </View>

        {/* The details */}
        <View style={[styles.row, { left: g.margin, top: g.row1, width: g.width }]}>
          <DetailBox label="Unit or flat" value={shown.unit} fill={pose} index={0} h={g.boxH} flex={1.25}>
            <TextInput
              value={shown.unit}
              onChangeText={(v) => onDetail(0, v)}
              onFocus={() => {
                takeOver();
                raised.current = g.row1;
                scheduleOnUI(editingUI, true);
                raise(g.row1);
              }}
              onBlur={() => scheduleOnUI(editingUI, false)}
              placeholder="Optional"
              placeholderTextColor="#B3AEA3"
              style={styles.detailInput}
              autoCorrect={false}
            />
          </DetailBox>
          <DetailBox label="Postcode" value={shown.postcode} fill={pose} index={1} h={g.boxH} flex={1}>
            <TextInput
              value={shown.postcode}
              onChangeText={(v) => onDetail(1, v)}
              onFocus={() => {
                takeOver();
                raised.current = g.row1;
                scheduleOnUI(editingUI, true);
                raise(g.row1);
              }}
              onBlur={() => scheduleOnUI(editingUI, false)}
              placeholder="0000"
              placeholderTextColor="#B3AEA3"
              keyboardType="number-pad"
              style={[styles.detailInput, styles.tabular]}
            />
          </DetailBox>
        </View>
        <View style={[styles.row, { left: g.margin, top: g.row2, width: g.width }]}>
          <DetailBox label="City" value={shown.city} fill={pose} index={2} h={g.boxH} flex={1.25}>
            <TextInput
              value={shown.city}
              onChangeText={(v) => onDetail(2, v)}
              onFocus={() => {
                takeOver();
                raised.current = g.row2;
                scheduleOnUI(editingUI, true);
                raise(g.row2);
              }}
              onBlur={() => scheduleOnUI(editingUI, false)}
              placeholder="Town or city"
              placeholderTextColor="#B3AEA3"
              style={styles.detailInput}
              autoCorrect={false}
            />
          </DetailBox>
          <View style={[styles.box, styles.toggleBox, { height: g.boxH }]}>
            <Text style={styles.toggleLabel} numberOfLines={2}>
              Leave at door
            </Text>
            <DoorToggle
              label="Leave at door"
              value={leaveAtDoor}
              accent={ACCENT}
              onChange={(v) => {
                takeOver();
                setLeaveAtDoor(v);
              }}
            />
          </View>
        </View>

        <ContinueButton g={g} pose={pose} onPress={onContinuePress} />

        {/* Suggestions sit over the map. */}
        <Animated.View style={[styles.listCard, { left: g.margin, top: g.listTop, width: g.width }, cardStyle]} pointerEvents="none" />
        {ADDRESSES.map((a, i) => (
          <SuggestionRow
            key={a.street}
            index={i}
            address={a}
            query={query}
            slot={slots[i]}
            open={listOpen}
            picked={user && uSelected === i}
            driver={driver}
            takeT={takeT}
            s0={s0}
            pose={pose}
            g={g}
            onPress={() => choose(i)}
          />
        ))}

        {/* The field */}
        <View style={[styles.fieldWrap, { left: g.margin, top: g.fieldTop, width: g.width, height: g.fieldH }]}>
          <Animated.View style={[styles.focusRing, ringStyle]} pointerEvents="none" />
          <Animated.View style={[styles.field, fieldStyle]}>
            <Animated.View style={[styles.fieldIcon, searchIcon]} pointerEvents="none">
              <SymbolView name={{ ios: 'magnifyingglass', android: 'search', web: 'search' }} size={17} tintColor={MUTED} />
            </Animated.View>
            <Animated.View style={[styles.fieldIcon, pinIcon]} pointerEvents="none">
              <SymbolView name={{ ios: 'mappin.circle.fill', android: 'location_on', web: 'location_on' }} size={20} tintColor={ACCENT} />
            </Animated.View>
            {query.length === 0 ? (
              <Animated.View style={[styles.placeholderBox, placeholderStyle]} pointerEvents="none">
                <Text style={styles.placeholder} numberOfLines={1}>
                  Street, building or postcode
                </Text>
              </Animated.View>
            ) : null}
            <Animated.View style={[StyleSheet.absoluteFill, textStyle]}>
              <TextInput
                ref={inputRef}
                value={query}
                onChangeText={onChangeQuery}
                onFocus={() => {
                  takeOver();
                  raised.current = -1;
                  scheduleOnUI(focusUI, true);
                }}
                onBlur={() => scheduleOnUI(focusUI, false)}
                onSubmitEditing={() => {
                  const first = rank(uQuery)[0];
                  if (first !== undefined) choose(first);
                }}
                returnKeyType="search"
                autoCorrect={false}
                autoCapitalize="words"
                textContentType="fullStreetAddress"
                selectionColor={ACCENT}
                accessibilityLabel="Delivery address"
                style={[styles.input, { height: g.fieldH, paddingRight: showClear ? 40 : 14 }]}
              />
            </Animated.View>
            <Animated.View style={[styles.caret, { top: (g.fieldH - 22) / 2 }, caretStyle]} pointerEvents="none" />
            {showClear ? (
              <Pressable style={styles.clear} onPress={clearQuery} accessibilityLabel="Clear address" hitSlop={10}>
                <SymbolView name={{ ios: 'xmark.circle.fill', android: 'cancel', web: 'cancel' }} size={17} tintColor="#B3AEA3" />
              </Pressable>
            ) : null}
          </Animated.View>
          <Text
            style={styles.measure}
            numberOfLines={1}
            onLayout={(e) => {
              textW.current = e.nativeEvent.layout.width;
              caretW.set(Math.max(0, textW.current - barW.current) + 1);
            }}
            accessible={false}
          >
            {query}|
          </Text>
          <Text
            style={styles.measure}
            numberOfLines={1}
            onLayout={(e) => {
              barW.current = e.nativeEvent.layout.width;
              caretW.set(Math.max(0, textW.current - barW.current) + 1);
            }}
            accessible={false}
          >
            |
          </Text>
        </View>
      </Animated.View>

      <GhostFinger dot={dot} g={g} />
    </View>
  );
}

// ---- Pieces -------------------------------------------------------------------------------------------

function Header({ g }: { g: Geo }) {
  return (
    <View style={[styles.header, { top: g.headerTop, left: g.margin - 6, right: g.margin }]} pointerEvents="box-none">
      <View style={styles.headerSide}>
        <SymbolView name={{ ios: 'chevron.left', android: 'arrow_back', web: 'arrow_back' }} size={19} tintColor={INK} />
      </View>
      <View style={styles.headerMid}>
        <Text style={styles.headerTitle}>Checkout</Text>
        <View style={styles.steps}>
          <View style={[styles.stepBar, styles.stepDone]} />
          <View style={[styles.stepBar, styles.stepNow]} />
          <View style={styles.stepBar} />
        </View>
      </View>
      <View style={[styles.headerSide, styles.headerRight]}>
        <Text style={styles.headerStep}>2 of 3</Text>
      </View>
    </View>
  );
}

type RowProps = {
  index: number;
  address: Address;
  query: string;
  slot: number;
  open: boolean;
  /** The person picked this row: it stays visible while it flies into the field. */
  picked: boolean;
  driver: 'auto' | 'user';
  takeT: number;
  s0: number;
  pose: SharedValue<Pose>;
  g: Geo;
  onPress: () => void;
};

/** One suggestion: matched letters in bold. Placed by the loop, or by springs when a person types. */
const SuggestionRow = memo(function SuggestionRow({ index, address, query, slot, open, picked, driver, takeT, s0, pose, g, onPress }: RowProps) {
  const y = useSharedValue(0);
  const a = useSharedValue(0);
  const userMode = useSharedValue(0);
  const wasDriver = useRef(driver);

  useEffect(() => {
    if (driver === 'auto') {
      userMode.set(0);
      wasDriver.current = driver;
      return;
    }
    if (wasDriver.current === 'auto') {
      const p = autoPose(takeT, s0);
      y.set(p.rowY[index]);
      a.set(p.rowA[index]);
      wasDriver.current = driver;
    }
    userMode.set(1);
    const visible = (open && slot >= 0) || picked;
    if (slot >= 0) y.set(withSpring(slot, { duration: 420, dampingRatio: 0.78 }));
    a.set(withTiming(visible ? 1 : 0, { duration: visible ? 260 : 160 }));
  }, [a, driver, index, open, picked, s0, slot, takeT, userMode, y]);

  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const yy = userMode.get() ? y.get() : p.rowY[index] ?? 0;
    const aa = userMode.get() ? a.get() : p.rowA[index] ?? 0;
    const mine = p.chosen === index;
    const c = p.choose;
    const fade = mine ? 1 - seg(c, 0.55, 0.9) : 1 - seg(c, 0, 0.45);
    const rowTop = g.listTop + 4 + yy * ROW_H;
    const fly = mine ? -c * (rowTop - g.fieldTop + (ROW_H - g.fieldH) / 2) : 0;
    return {
      opacity: clamp01(aa) * fade * p.list,
      transform: [{ translateY: yy * ROW_H + 10 * (1 - clamp01(aa)) + fly }, { scale: mine ? 1 - 0.04 * c : 1 }],
    };
  });

  const range = matchRange(address.street, query);
  const visible = open && slot >= 0;
  return (
    <Animated.View style={[styles.rowWrap, { left: g.margin + 4, top: g.listTop + 4, width: g.width - 8 }, style]} pointerEvents={visible ? 'auto' : 'none'}>
      <Pressable onPress={onPress} style={({ pressed }) => [styles.rowPress, pressed && styles.rowPressed]} accessibilityRole="button" accessibilityLabel={`${address.street}, ${address.area}`}>
        <View style={styles.rowIcon}>
          <SymbolView name={{ ios: 'mappin', android: 'location_on', web: 'location_on' }} size={14} tintColor={MUTED} />
        </View>
        <View style={styles.rowText}>
          <Text style={styles.rowStreet} numberOfLines={1}>
            {range ? (
              <>
                {address.street.slice(0, range[0])}
                <Text style={styles.rowMatch}>{address.street.slice(range[0], range[1])}</Text>
                {address.street.slice(range[1])}
              </>
            ) : (
              address.street
            )}
          </Text>
          <Text style={styles.rowArea} numberOfLines={1}>
            {address.area} {address.postcode}
          </Text>
        </View>
        <Text style={styles.rowKm}>{distanceKm(address)}</Text>
      </Pressable>
    </Animated.View>
  );
});

/** "Front door" after the pin lands; "Side gate · 9 m" after the map was nudged under it. */
function Callouts({ pose, w, h }: { pose: SharedValue<Pose>; w: number; h: number }) {
  const a = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.callA, transform: [{ translateY: 6 * (1 - p.callA) }, { scale: 0.94 + 0.06 * p.callA }] };
  });
  const b = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.callB, transform: [{ translateY: 6 * (1 - p.callB) }, { scale: 0.94 + 0.06 * p.callB }] };
  });
  const top = h / 2 - 50 - CALLOUT_H - 10;
  return (
    <View style={[StyleSheet.absoluteFill]} pointerEvents="none">
      <Animated.View style={[styles.callout, { top, left: w / 2 - CALLOUT_W / 2 }, a]}>
        <Text style={styles.calloutTitle}>Front door</Text>
        <Text style={styles.calloutSub}>Drop-off point</Text>
        <View style={styles.calloutTail} />
      </Animated.View>
      <Animated.View style={[styles.callout, { top, left: w / 2 - CALLOUT_W / 2 }, b]}>
        <Text style={styles.calloutTitle}>Side gate</Text>
        <Text style={[styles.calloutSub, styles.tabular]}>Moved 9 m</Text>
        <View style={styles.calloutTail} />
      </Animated.View>
    </View>
  );
}

const CALLOUT_W = 112;
const CALLOUT_H = 44;

type BoxProps = { label: string; value: string; fill: SharedValue<Pose>; index: number; h: number; flex: number; children: React.ReactNode };

/** A detail field: label on top, value under it, filled in by the lookup with a short rise. */
function DetailBox({ label, value, fill, index, h, flex, children }: BoxProps) {
  const style = useAnimatedStyle(() => {
    const p = fill.get();
    const f = index === 0 ? p.fill0 : index === 1 ? p.fill1 : p.fill2;
    if (value.length === 0) return { opacity: p.placeholder * (1 - f), transform: [{ translateY: 0 }] };
    return { opacity: f, transform: [{ translateY: 7 * (1 - f) }] };
  });
  const flash = useAnimatedStyle(() => {
    const p = fill.get();
    const f = index === 0 ? p.fill0 : index === 1 ? p.fill1 : p.fill2;
    return { opacity: value.length === 0 ? 0 : 0.9 * Math.sin(Math.PI * f) };
  });
  return (
    <View style={[styles.box, { height: h, flex }]}>
      <Animated.View style={[styles.boxFlash, flash]} pointerEvents="none" />
      <Text style={styles.boxLabel}>{label}</Text>
      <Animated.View style={style}>{children}</Animated.View>
    </View>
  );
}

function ContinueButton({ g, pose, onPress }: { g: Geo; pose: SharedValue<Pose>; onPress: () => void }) {
  const live = useAnimatedStyle(() => ({ opacity: pose.get().ready }));
  const label = useAnimatedStyle(() => ({ color: interpolateColor(pose.get().ready, [0, 1], ['#A29C90', '#FFF7F1']) }));
  const press = useSharedValue(0);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * press.get() }] }));
  return (
    <Pressable
      style={[styles.buttonWrap, { left: g.margin, top: g.buttonTop, width: g.width, height: g.buttonH }]}
      onPress={onPress}
      onPressIn={() => press.set(withTiming(1, { duration: 100 }))}
      onPressOut={() => press.set(withTiming(0, { duration: 160 }))}
      accessibilityRole="button"
      accessibilityLabel="Continue to payment"
    >
      <Animated.View style={[StyleSheet.absoluteFill, pressStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.buttonShadow, live]} />
        <View style={[StyleSheet.absoluteFill, styles.buttonFace]}>
          <Animated.View style={[StyleSheet.absoluteFill, live]}>
            <LinearGradient colors={['#EC6A40', ACCENT, '#C9461F']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
            <View style={styles.buttonEdge} />
          </Animated.View>
          <Animated.Text style={[styles.buttonText, label]}>Continue to payment</Animated.Text>
        </View>
      </Animated.View>
    </Pressable>
  );
}

/** A soft touch dot showing what the loop's finger does. */
function GhostFinger({ dot, g }: { dot: SharedValue<Dot>; g: Geo }) {
  const style = useAnimatedStyle(() => {
    const d = dot.get();
    let x = g.margin + g.width * 0.62;
    let y = g.fieldTop + g.fieldH / 2;
    if (d.target === 1) {
      x = g.margin + g.width * 0.45;
      y = g.listTop + 4 + ROW_H / 2;
    } else if (d.target === 2) {
      x = g.margin + g.width * 0.66 + d.dx;
      y = g.mapTop + g.mapH * 0.66 + d.dy;
    }
    return {
      opacity: d.alpha,
      transform: [{ translateX: x - DOT_SIZE / 2 }, { translateY: y - DOT_SIZE / 2 + (1 - d.alpha) * 8 }, { scale: d.scale }],
    };
  });
  return <Animated.View style={[styles.finger, style]} pointerEvents="none" />;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  header: { position: 'absolute', height: 44, flexDirection: 'row', alignItems: 'center' },
  headerSide: { width: 72, height: 44, justifyContent: 'center', paddingLeft: 6 },
  headerRight: { alignItems: 'flex-end', paddingLeft: 0 },
  headerMid: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 6 },
  headerTitle: { fontSize: 17, fontWeight: '600', color: INK, letterSpacing: -0.3 },
  headerStep: { fontSize: 13, fontWeight: '500', color: MUTED, fontVariant: ['tabular-nums'] },
  steps: { flexDirection: 'row', gap: 4 },
  stepBar: { width: 22, height: 3, borderRadius: 2, backgroundColor: 'rgba(40,34,24,0.14)' },
  stepDone: { backgroundColor: INK },
  stepNow: { backgroundColor: ACCENT },
  title: { position: 'absolute', fontWeight: '700', color: INK, letterSpacing: -0.6 },
  fieldWrap: { position: 'absolute' },
  focusRing: { position: 'absolute', left: -4, right: -4, top: -4, bottom: -4, borderRadius: 20, borderCurve: 'continuous', backgroundColor: 'rgba(224,86,46,0.12)' },
  field: {
    flex: 1,
    borderRadius: 16,
    borderCurve: 'continuous',
    borderWidth: 1,
    backgroundColor: CARD,
    boxShadow: '0 1px 2px rgba(40,30,18,0.08), 0 8px 22px rgba(40,30,18,0.07)',
    overflow: 'hidden',
  },
  fieldIcon: { position: 'absolute', left: 0, top: 0, bottom: 0, width: ICON_X, alignItems: 'center', justifyContent: 'center' },
  placeholderBox: { position: 'absolute', left: ICON_X, right: 14, top: 0, bottom: 0, justifyContent: 'center' },
  placeholder: { fontSize: 17, color: '#A9A498', letterSpacing: -0.2 },
  input: { paddingLeft: ICON_X, paddingVertical: 0, fontSize: 17, color: INK, letterSpacing: -0.2 },
  caret: { position: 'absolute', left: 0, width: 2, height: 22, borderRadius: 1, backgroundColor: ACCENT },
  clear: { position: 'absolute', right: 6, top: 0, bottom: 0, width: 32, alignItems: 'center', justifyContent: 'center' },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0, fontSize: 17, letterSpacing: -0.2 },
  listCard: {
    position: 'absolute',
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
    boxShadow: '0 2px 4px rgba(40,30,18,0.08), 0 18px 40px rgba(40,30,18,0.16)',
  },
  rowWrap: { position: 'absolute', height: ROW_H },
  rowPress: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, borderRadius: 12, borderCurve: 'continuous', gap: 12 },
  rowPressed: { backgroundColor: 'rgba(40,34,24,0.05)' },
  rowIcon: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#F1EEE7', alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, gap: 1 },
  rowStreet: { fontSize: 16, color: '#4E4A42', letterSpacing: -0.2 },
  rowMatch: { fontWeight: '700', color: INK },
  rowArea: { fontSize: 13, color: MUTED },
  rowKm: { fontSize: 13, color: MUTED, fontVariant: ['tabular-nums'] },
  mapShadow: {
    position: 'absolute',
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#ECE9E1',
    boxShadow: '0 1px 3px rgba(40,30,18,0.12), 0 14px 32px rgba(40,30,18,0.12)',
  },
  mapClip: { borderRadius: 22, borderCurve: 'continuous', overflow: 'hidden' },
  mapEdge: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: 22, borderCurve: 'continuous', borderWidth: 1, borderColor: 'rgba(255,255,255,0.5)' },
  recenter: {
    position: 'absolute',
    right: 12,
    bottom: 12,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: CARD,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(40,30,18,0.16), 0 6px 14px rgba(40,30,18,0.12)',
  },
  callout: {
    position: 'absolute',
    width: CALLOUT_W,
    height: CALLOUT_H,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(40,30,18,0.14), 0 8px 18px rgba(40,30,18,0.14)',
  },
  calloutTitle: { fontSize: 14, fontWeight: '600', color: INK, letterSpacing: -0.2 },
  calloutSub: { fontSize: 11.5, color: MUTED },
  calloutTail: { position: 'absolute', bottom: -5, left: CALLOUT_W / 2 - 6, width: 12, height: 12, backgroundColor: CARD, transform: [{ rotate: '45deg' }], borderRadius: 2 },
  row: { position: 'absolute', flexDirection: 'row', gap: 10 },
  box: {
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
    paddingHorizontal: 12,
    justifyContent: 'center',
    overflow: 'hidden',
    boxShadow: '0 1px 2px rgba(40,30,18,0.06)',
  },
  boxFlash: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: 'rgba(224,86,46,0.08)' },
  boxLabel: { fontSize: 12, fontWeight: '500', color: MUTED, marginBottom: 1 },
  detailInput: { fontSize: 16, color: INK, paddingVertical: 0, height: 22, letterSpacing: -0.2 },
  tabular: { fontVariant: ['tabular-nums'] },
  toggleBox: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  toggleLabel: { flex: 1, fontSize: 15, fontWeight: '500', color: INK, letterSpacing: -0.2 },
  buttonWrap: { position: 'absolute' },
  buttonShadow: { borderRadius: 27, backgroundColor: ACCENT, boxShadow: '0 1px 2px rgba(150,50,20,0.35), 0 10px 24px rgba(150,50,20,0.26)' },
  buttonFace: { borderRadius: 27, overflow: 'hidden', backgroundColor: '#E4DFD5', alignItems: 'center', justifyContent: 'center' },
  buttonEdge: { position: 'absolute', left: 0, right: 0, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.28)' },
  buttonText: { fontSize: 17, fontWeight: '600', letterSpacing: -0.2 },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: 'rgba(30,26,20,0.16)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.75)',
    boxShadow: '0 4px 12px rgba(30,26,20,0.14)',
  },
});
