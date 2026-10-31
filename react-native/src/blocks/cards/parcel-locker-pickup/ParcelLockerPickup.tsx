import {
  BackdropBlur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  LinearGradient,
  Path,
  Rect,
  RoundedRect,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useMotionPreference } from '@/shared/useMotionPreference';

import {
  DONE,
  LOOP,
  OPEN,
  S,
  SPRING,
  TAKE,
  digitsAt,
  easeInOut,
  lightAt,
  openAngle,
  script,
  seg,
  slamAngle,
  smooth,
  springStep,
  springTo,
} from './plp-motion';
import { type Cue, usePlpAudio } from './use-plp-audio';

/*
 Parcel Locker Pickup. A lobby wall of graphite parcel lockers at dusk; yours (middle column, third row)
 blinks amber. The sheet at the bottom holds the pickup code and Open locker. Open: the code's digits
 light one by one, the LED turns green, the latch lets go with a kick and the door swings open on its
 hinge in true perspective (its thickness and then its inside showing past square); the compartment's
 light flickers on and spills onto the doors around it. Take the parcel (tap or drag it out): it is
 lifted toward you and flies down into the sheet, which grows into the receipt, while the door swings
 shut behind it, hits the frame and bounces. Done folds the receipt back.
 Art: the wall, the compartment and the parcel are image renders; the door is the wall's own pixels
 mapped onto a perspective mesh; lights, the LED and every move are Skia; all text is real text.
 autoPlay: LOOP = 10 s: rest 0-1.2, press 1.2-1.4, digits 1.55-1.9, latch 2.12, swing 2.35, light
 2.45-3.0, take 4.0, door shut ~4.6-5.2, receipt 4.8, done 8.4, rest from 9.2.
*/

const WALL = { w: 1206, h: 2622, gridTop: 329, door: { x0: 435, y0: 1057, x1: 771, y1: 1392 }, led: { x: 306, y: 35 } };
const PARCEL = { w: 640, h: 523 };
const C = {
  ink: '#F4EFE8',
  mute: '#A39A90',
  amber: '#FFB25E',
  green: '#5BE39A',
  sheet: 'rgba(22,20,19,0.62)',
};
const NX = 10; // the door's mesh: NX strips across, enough for the perspective to read straight
const FOCAL = 1000;
const THICK = 5; // the door's thickness, pt

const REST = 0;
const OPENING = 1; // from Open locker until the parcel is taken
const TAKING = 2;
const DONEING = 3;

type St = {
  auto: number;
  mode: number;
  t: number;
  now: number;
  touchAt: number;
  openAt: number;
  takeAt: number;
  doneAt: number;
  angle: number;
  vang: number;
  dragging: number;
  dragAngle: number;
  takeFrom: number;
  press: number;
  pose: { digits: number; led: number; light: number; parcel: number; sheet: number; takeAge: number; fold: number };
  open: number;
};

/** A point on the door (u across from the hinge, y down, t into the door) seen in perspective. */
function project(hx: number, cx: number, cy: number, th: number, u: number, y: number, t: number) {
  'worklet';
  const X = hx + u * Math.cos(th) + t * Math.sin(th);
  const Z = u * Math.sin(th) - t * Math.cos(th);
  const s = FOCAL / (FOCAL - Z);
  return [cx + (X - cx) * s, cy + (y - cy) * s];
}

type Props = { autoPlay?: boolean };

export function ParcelLockerPickup({ autoPlay = false }: Props) {
  const reduced = useMotionPreference();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [phase, setPhase] = useState(0); // 0 pickup, 1 receipt (for touch targets and VoiceOver)
  const [fontsOk] = useFonts({
    'PLP-Archivo-ExtraBold': require('./assets/fonts/Archivo-ExtraBold.ttf'),
    'PLP-Archivo-SemiBold': require('./assets/fonts/Archivo-SemiBold.ttf'),
    'PLP-Archivo-Medium': require('./assets/fonts/Archivo-Medium.ttf'),
    'PLP-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
  });
  const wall = useImage(require('./assets/wall.jpg'));
  const comp = useImage(require('./assets/compartment.webp'));
  const parcelImg = useImage(require('./assets/parcel.webp'));
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  // ------------------------------------------------------------------ geometry
  const W = size?.width ?? 402;
  const H = size?.height ?? 874;
  const top = insets.top;
  const k = W / WALL.w; // pt per wall pixel
  const OY = top + 118 - WALL.gridTop * k; // the wall drops so the header has room above the lockers
  const DX = WALL.door.x0 * k;
  const DY = OY + WALL.door.y0 * k;
  const DW = (WALL.door.x1 - WALL.door.x0) * k;
  const DH = (WALL.door.y1 - WALL.door.y0) * k;
  const DCX = DX + DW / 2;
  const DCY = DY + DH / 2;
  const SHEET_H = 248;
  const RECEIPT_H = 300;
  const SX = 16;
  const SW = W - 32;
  const SB = H - insets.bottom - 14; // the sheet's bottom edge
  const PW = DW * 0.8; // the parcel inside its compartment
  const PH = (PW * PARCEL.h) / PARCEL.w;
  const PCX = DCX;
  const PCY = DY + DH - PH / 2 - DH * 0.07;
  const THUMB = 64; // the parcel's place in the receipt
  const TCX = SX + 22 + THUMB / 2;
  const TCY = SB - RECEIPT_H + 89 + THUMB / 2;

  // ------------------------------------------------------------------ state
  const st = useSharedValue<St>({
    auto: 0, mode: REST, t: 0, now: 0, touchAt: -10, openAt: -10, takeAt: -10, doneAt: -10,
    angle: 0, vang: 0, dragging: 0, dragAngle: 0, takeFrom: 0, press: 0,
    pose: { digits: 0, led: 0, light: 0, parcel: 0, sheet: 0, takeAge: -10, fold: 0 }, open: 0,
  });
  const tick = useSharedValue(0);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __parcelLocker?: unknown };
    g.__parcelLocker = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), manual: () => debugT.set(-3) };
    return () => {
      delete g.__parcelLocker;
    };
  }, [debugT]);

  const play = usePlpAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (kind === 2) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  // ------------------------------------------------------------------ the clock
  /** The digits lit at the previous frame (to fire one tick per digit). */
  function digitsAtPrev(s: St, prev: number) {
    'worklet';
    return s.mode === OPENING ? digitsAt(prev - s.openAt) : s.pose.digits;
  }

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const s = st.get();
    const prev = s.now;
    s.now += dt;
    const now = s.now;
    const dbg = debugT.get();

    if (dbg === -3 && s.auto === 1) {
      s.auto = 0;
      s.mode = REST;
      s.angle = 0;
      s.vang = 0;
      s.openAt = -10;
      s.takeAt = -10;
      s.doneAt = -10;
    }
    // back to the script 2 s after the last touch, once everything rests (the script's rest is the same)
    if (auto && dbg !== -3 && s.auto === 0 && s.mode === REST && now - s.touchAt > 2 && s.angle < 0.01 && s.pose.sheet < 0.001) {
      s.auto = 1;
      s.t = 0;
    }
    if (dbg >= 0 && s.auto === 0) s.auto = 1;

    if (s.auto === 1) {
      let t = s.t;
      if (dbg >= 0) t = dbg;
      else if (dbg !== -2) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
      }
      s.t = t;
      const p = script(t);
      s.press = p.press;
      s.angle = p.angle;
      s.vang = 0;
      s.pose = { digits: p.digits, led: p.led, light: p.light, parcel: p.parcel, sheet: p.sheet, takeAge: p.takeAge, fold: p.fold };
    } else {
      const to = now - s.openAt;
      const tk = now - s.takeAt;
      const td = now - s.doneAt;
      // events crossing in this frame: the latch, the slam
      if (s.mode === OPENING && to - dt < OPEN.latch && to >= OPEN.latch) {
        scheduleOnRN(buzz, 1);
        scheduleOnRN(cue, 'latch');
        s.vang += 120; // the kick
      }
      if (s.mode === OPENING && to - dt < OPEN.flicker[0] && to >= OPEN.flicker[0]) scheduleOnRN(cue, 'hum');
      if (s.mode === OPENING && to - dt < OPEN.swing && to >= OPEN.swing) scheduleOnRN(cue, 'creak');
      // the door: a spring toward where it should be, or the finger
      let target = 0;
      let sp: { duration: number; ratio: number } = SPRING.swing;
      if (s.mode === OPENING && to >= OPEN.swing) target = OPEN.angle;
      if (s.mode === TAKING && tk < TAKE.slam) target = s.takeFrom;
      if (s.mode === TAKING && tk >= TAKE.slam) sp = SPRING.slam;
      if (s.dragging === 1) {
        target = s.dragAngle;
        sp = SPRING.drag;
      }
      const n = Math.max(1, Math.ceil(dt / (1 / 240)));
      for (let i = 0; i < n; i++) {
        const r = springTo(s.angle, s.vang, target, sp.duration, sp.ratio, dt / n);
        s.angle = r[0];
        s.vang = r[1];
        if (s.angle < 0) {
          // the frame: the door bounces off it
          s.angle = -s.angle * 0.3;
          if (s.vang < -40) {
            scheduleOnRN(buzz, 2);
            scheduleOnRN(cue, 'thud');
          }
          s.vang = -s.vang * 0.3;
        }
      }
      const opened = s.mode === OPENING || s.mode === TAKING;
      const light = opened ? lightAt(to) * (s.mode === TAKING ? 1 - smooth(TAKE.slam + 0.15, TAKE.slam + 0.4, tk) : 1) : 0;
      const led = s.mode === OPENING ? (to < OPEN.latch ? 0 : 1) : s.mode === TAKING ? (tk > TAKE.slam + 0.7 && s.angle < 3 ? 2 : 1) : s.mode === DONEING ? 2 : 0;
      const parcel = s.mode === TAKING || s.mode === DONEING ? easeInOut(seg(tk, TAKE.lift[0], TAKE.fly[1])) : 0;
      let sheet = s.mode === TAKING ? (reduced ? (tk > TAKE.receipt ? 1 : 0) : springStep(tk - TAKE.receipt, SPRING.receipt.duration, SPRING.receipt.ratio)) : 0;
      if (s.mode === DONEING) sheet = 1 - easeInOut(seg(td, DONE.fold[0], DONE.fold[1]));
      const digits = s.mode === OPENING || s.mode === TAKING ? digitsAt(to) : s.mode === DONEING ? 4 * (1 - seg(td, 0, 0.3)) : 0;
      s.pose = { digits, led, light, parcel, sheet, takeAge: s.mode === TAKING ? tk : -10, fold: s.mode === DONEING ? easeInOut(seg(td, DONE.fold[0], DONE.fold[1])) : 0 };
      s.press = Math.max(0, s.press - dt * 6);
      if (s.mode === TAKING && tk - dt < TAKE.receipt && tk >= TAKE.receipt) {
        scheduleOnRN(buzz, 3);
        scheduleOnRN(cue, 'done');
      }
      if (s.mode === DONEING && td > DONE.fold[1] + 0.05) {
        s.mode = REST;
        s.openAt = -10;
        s.takeAt = -10;
      }
    }
    // the digits tick as they light, under a finger
    if (s.auto === 0 && Math.floor(s.pose.digits) !== Math.floor(digitsAtPrev(s, prev)) && s.mode === OPENING) {
      scheduleOnRN(buzz, 0);
      scheduleOnRN(cue, 'beep');
    }
    const open = s.pose.sheet > 0.5 ? 1 : 0;
    if (open !== s.open) {
      s.open = open;
      scheduleOnRN(setPhase, open);
    }
    if (dbg >= 0) debugT.set(-2);
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ------------------------------------------------------------------ actions
  /** A touch takes over from the script at the same pose. */
  const interrupt = (s: St) => {
    'worklet';
    s.touchAt = s.now;
    if (s.auto === 0) return;
    s.auto = 0;
    const t = s.t;
    if (t >= S.done && t < S.rest) {
      s.mode = DONEING;
      s.doneAt = s.now - (t - S.done);
      s.takeAt = s.now - (t - S.take);
    } else if (t >= S.take && t < S.rest) {
      s.mode = TAKING;
      s.takeAt = s.now - (t - S.take);
      s.openAt = s.now - (t - S.open);
      s.takeFrom = openAngle(S.take - S.open);
    } else if (t >= S.open && t < S.rest) {
      s.mode = OPENING;
      s.openAt = s.now - (t - S.open);
    } else s.mode = REST;
  };
  const openLocker = () => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.mode === REST) {
        s.mode = OPENING;
        s.openAt = s.now;
        s.press = 1;
      }
      return s;
    });
  };
  const done = () => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.mode === TAKING) {
        s.mode = DONEING;
        s.doneAt = s.now;
      }
      return s;
    });
    cue('fold');
  };

  const take = (s: St) => {
    'worklet';
    if (s.mode === OPENING && s.now - s.openAt > OPEN.flicker[0] && s.angle > 60) {
      s.mode = TAKING;
      s.takeAt = s.now;
      s.takeFrom = s.angle;
      scheduleOnRN(cue, 'lift');
    }
  };
  // the door: drag it (once unlatched) to swing it by hand; the parcel: tap or pull it out
  const doorPan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      const s = st.get();
      interrupt(s);
    })
    .onStart(() => {
      const s = st.get();
      if (s.mode === OPENING && s.now - s.openAt > OPEN.latch) {
        s.dragging = 1;
        s.dragAngle = s.angle;
      }
    })
    .onChange((e) => {
      const s = st.get();
      s.touchAt = s.now;
      if (s.dragging === 1) s.dragAngle = Math.max(0, Math.min(118, s.dragAngle - e.changeX * 0.9));
    })
    .onFinalize(() => {
      const s = st.get();
      s.dragging = 0;
      s.touchAt = s.now;
    });
  const parcelTap = Gesture.Tap().onEnd(() => {
    const s = st.get();
    interrupt(s);
    take(s);
  });
  const parcelPan = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      const s = st.get();
      interrupt(s);
      take(s);
    });
  const doorGesture = Gesture.Race(parcelPan, parcelTap, doorPan);

  // ------------------------------------------------------------------ derived drawing
  const doorGeo = useDerivedValue(() => {
    tick.get();
    const th = (st.get().angle * Math.PI) / 180;
    const pts = [];
    let backFace = false;
    for (let i = 0; i <= NX; i++) {
      const u = (DW * i) / NX;
      const a = project(DX, DX, DCY, th, u, DY, 0);
      const b = project(DX, DX, DCY, th, u, DY + DH, 0);
      pts.push(vec(a[0], a[1]), vec(b[0], b[1]));
    }
    if (th > Math.PI / 2) backFace = true;
    // the free edge's thickness: from the front face to the back face at u = DW
    const e0 = project(DX, DX, DCY, th, DW, DY, 0);
    const e1 = project(DX, DX, DCY, th, DW, DY, THICK);
    const e2 = project(DX, DX, DCY, th, DW, DY + DH, THICK);
    const e3 = project(DX, DX, DCY, th, DW, DY + DH, 0);
    const edge = Skia.PathBuilder.Make().moveTo(e0[0], e0[1]).lineTo(e1[0], e1[1]).lineTo(e2[0], e2[1]).lineTo(e3[0], e3[1]).close().build();
    const led = project(DX, DX, DCY, th, WALL.led.x * k, DY + WALL.led.y * k, 0);
    return { pts, backFace, edge, led, th };
  });
  const doorT = useDerivedValue(() => {
    const th = doorGeo.get().th;
    return [{ translateX: DX }, { translateY: DCY }, { perspective: FOCAL }, { rotateY: -th }, { translateX: -DX }, { translateY: -DCY }];
  });
  const doorShade = useDerivedValue(() => {
    const th = doorGeo.get().th;
    return Math.min(0.4, 0.3 * Math.sin(th));
  });
  const doorWarm = useDerivedValue(() => Math.max(0, Math.sin(doorGeo.get().th)) * 0.45 * (doorGeo.get().th < Math.PI / 2 ? 1 : 0));
  // the open door's shadow falls across the door to its left
  const castA = useDerivedValue(() => Math.max(0, Math.sin(doorGeo.get().th)) * 0.45);
  const backLit = useDerivedValue(() => {
    tick.get();
    return 0.16 * st.get().pose.light;
  });
  const frontA = useDerivedValue(() => (doorGeo.get().backFace ? 0 : 1));
  const backA = useDerivedValue(() => (doorGeo.get().backFace ? 1 : 0));
  const doorEdge = useDerivedValue(() => doorGeo.get().edge);
  const edgeA = useDerivedValue(() => Math.min(1, Math.sin(doorGeo.get().th) * 3));
  // the LED, carried by the door
  const ledXY = useDerivedValue(() => doorGeo.get().led);
  const ledX = useDerivedValue(() => ledXY.get()[0]);
  const ledY = useDerivedValue(() => ledXY.get()[1]);
  const ledColor = useDerivedValue(() => {
    tick.get();
    return st.get().pose.led === 1 ? C.green : C.amber;
  });
  const ledA = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    if (s.pose.led === 2) return 0;
    if (s.pose.led === 1) return 1;
    // the amber blink: a soft breath, twice per 4 s so it repeats in the loop
    const ph = ((s.auto === 1 ? s.t : s.now) % 2) / 2;
    return 0.25 + 0.75 * Math.pow(Math.sin(Math.PI * ph), 2);
  });
  const ledGlowA = useDerivedValue(() => ledA.get() * 0.8);
  // the compartment's light, and its spill onto the doors around it
  const lightA = useDerivedValue(() => {
    tick.get();
    return st.get().pose.light;
  });
  const darkA = useDerivedValue(() => 1 - 0.82 * lightA.get());
  const spillA = useDerivedValue(() => {
    tick.get();
    return lightA.get() * Math.min(1, Math.sin(doorGeo.get().th) * 1.4) * 0.5;
  });
  // the parcel: in the compartment, lifted toward you, then into the sheet
  const parcelT = useDerivedValue(() => {
    tick.get();
    const p = st.get().pose.parcel;
    const lift = smooth(0, 0.35, p);
    const fly = smooth(0.3, 1, p);
    const x0 = PCX;
    const y0 = PCY - 10 * lift;
    const sc0 = 1 + 0.32 * lift;
    // an arc down to the receipt's thumbnail
    const mx = (x0 + TCX) / 2 - 40;
    const my = Math.min(y0, TCY) - 30;
    const q = fly;
    const x = (1 - q) * (1 - q) * x0 + 2 * (1 - q) * q * mx + q * q * TCX;
    const y = (1 - q) * (1 - q) * y0 + 2 * (1 - q) * q * my + q * q * TCY;
    const sc = sc0 + ((THUMB * 0.92) / PW - sc0) * fly;
    return [{ translateX: x }, { translateY: y }, { rotate: -0.12 * Math.sin(Math.PI * fly) }, { scale: sc }];
  });
  const parcelInside = useDerivedValue(() => {
    tick.get();
    return st.get().pose.parcel < 0.02 ? 1 : 0;
  });
  const parcelOut = useDerivedValue(() => {
    tick.get();
    const pose = st.get().pose;
    return pose.parcel >= 0.02 ? 1 - pose.fold : 0;
  });
  const parcelShadowA = useDerivedValue(() => {
    tick.get();
    return 0.5 * (1 - smooth(0, 0.25, st.get().pose.parcel));
  });

  // the sheet: from the pickup card to the receipt
  const sheetRect = useDerivedValue(() => {
    tick.get();
    const m = st.get().pose.sheet;
    const h = SHEET_H + (RECEIPT_H - SHEET_H) * m;
    return Skia.RRectXY(Skia.XYWHRect(SX, SB - h, SW, h), 28, 28);
  });
  const sheetClip = useDerivedValue(() => {
    const r = sheetRect.get();
    return Skia.Path.RRect(r);
  });

  // ------------------------------------------------------------------ RN layers
  const pickupStyle = useAnimatedStyle(() => {
    tick.get();
    const m = st.get().pose.sheet;
    const kk = smooth(0, 0.45, m);
    return { opacity: 1 - kk, transform: [{ translateY: -12 * kk }] };
  });
  const receiptStyle = useAnimatedStyle(() => {
    tick.get();
    const m = st.get().pose.sheet;
    const kk = smooth(0.45, 1, m);
    return { opacity: kk, transform: [{ translateY: 14 * (1 - kk) }] };
  });
  const sheetBox = useAnimatedStyle(() => {
    tick.get();
    const m = st.get().pose.sheet;
    const h = SHEET_H + (RECEIPT_H - SHEET_H) * m;
    return { top: SB - h, height: h };
  });
  const pressStyle = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ scale: 1 - 0.035 * st.get().press }] };
  });
  const digitStyle = (i: number) => {
    'worklet';
    const d = st.get().pose.digits;
    const lit = Math.min(1, Math.max(0, d - i));
    return lit;
  };
  const d0 = useAnimatedStyle(() => {
    tick.get();
    return chip(digitStyle(0));
  });
  const d1 = useAnimatedStyle(() => {
    tick.get();
    return chip(digitStyle(1));
  });
  const d2 = useAnimatedStyle(() => {
    tick.get();
    return chip(digitStyle(2));
  });
  const d3 = useAnimatedStyle(() => {
    tick.get();
    return chip(digitStyle(3));
  });
  const headA = useAnimatedStyle(() => {
    tick.get();
    const kk = smooth(0.3, 0.7, st.get().pose.sheet);
    return { opacity: 1 - kk };
  });
  const headB = useAnimatedStyle(() => {
    tick.get();
    const kk = smooth(0.5, 0.9, st.get().pose.sheet);
    return { opacity: kk };
  });
  const checkStyle = useAnimatedStyle(() => {
    tick.get();
    const kk = smooth(0.55, 1, st.get().pose.sheet);
    return { transform: [{ scale: 0.6 + 0.4 * kk }], opacity: kk };
  });

  const ready = fontsOk && size !== null;
  const doorHit = { left: DX - 30, top: DY - 20, width: DW + 60, height: DH + 40 };

  return (
    <View style={styles.screen} onLayout={(e) => setSize({ width: e.nativeEvent.layout.width, height: e.nativeEvent.layout.height })}>
      {size && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Rect x={0} y={0} width={W} height={H} color="#141312" />
          {wall && <Image image={wall} x={0} y={OY} width={W} height={WALL.h * k} fit="fill" />}

          {/* the compartment behind the door, its light, the parcel in it */}
          <Group clip={Skia.XYWHRect(DX, DY, DW, DH)}>
            {comp && <Image image={comp} x={DX} y={DY} width={DW} height={DH} fit="fill" />}
            <Rect x={DX} y={DY} width={DW} height={DH} color="#05060A" opacity={darkA} />
            <Group opacity={parcelInside}>
              <RoundedRect x={PCX - PW * 0.46} y={PCY + PH * 0.36} width={PW * 0.92} height={PH * 0.2} r={PH * 0.1} color="#000" opacity={parcelShadowA}>
                <BlurMask blur={6} style="normal" />
              </RoundedRect>
              {parcelImg && <Image image={parcelImg} x={PCX - PW / 2} y={PCY - PH / 2} width={PW} height={PH} fit="fill" />}
              <Rect x={PCX - PW / 2} y={PCY - PH / 2} width={PW} height={PH} color="#05060A" opacity={darkA} />
            </Group>
            <Rect x={DX} y={DY} width={DW} height={DH * 0.35} opacity={lightA} blendMode="plus">
              <LinearGradient start={vec(0, DY)} end={vec(0, DY + DH * 0.35)} colors={['rgba(170,200,255,0.22)', 'rgba(170,200,255,0)']} />
            </Rect>
          </Group>
          {/* the light spilling out over the doors around it */}
          <RoundedRect x={DX - 26} y={DY - 18} width={DW + 52} height={DH + 52} r={20} color="rgba(185,205,255,0.55)" opacity={spillA} blendMode="plus">
            <BlurMask blur={26} style="normal" />
          </RoundedRect>
          {/* the frame's shadow inside the opening when the door is away */}
          <Rect x={DX} y={DY} width={DW} height={DH} style="stroke" strokeWidth={2} color="rgba(0,0,0,0.55)" />

          <Rect x={DX - DW * 0.75} y={DY - 4} width={DW * 0.75} height={DH + 8} opacity={castA}>
            <LinearGradient start={vec(DX, 0)} end={vec(DX - DW * 0.75, 0)} colors={['rgba(0,0,0,0.85)', 'rgba(0,0,0,0)']} />
          </Rect>
          {/* the door: the wall's own pixels turned about the hinge in perspective; past square, its inside */}
          <Group transform={doorT}>
            <Group opacity={frontA} clip={Skia.XYWHRect(DX, DY, DW, DH)}>
              {wall && <Image image={wall} x={0} y={OY} width={W} height={WALL.h * k} fit="fill" />}
              <Rect x={DX} y={DY} width={DW} height={DH} color="#000" opacity={doorShade} />
              {/* turned toward the lobby's amber light, the face warms */}
              <Rect x={DX} y={DY} width={DW} height={DH} opacity={doorWarm} blendMode="plus">
                <LinearGradient start={vec(DX, DY)} end={vec(DX + DW, DY + DH)} colors={['rgba(255,170,90,0.55)', 'rgba(255,170,90,0.05)']} />
              </Rect>
            </Group>
            <Group opacity={backA}>
              <Rect x={DX} y={DY} width={DW} height={DH}>
                <LinearGradient start={vec(DX, DY)} end={vec(DX + DW, DY + DH)} colors={['#3A3E45', '#24272C']} />
              </Rect>
              <Rect x={DX + DW * 0.08} y={DY + DH * 0.36} width={DW * 0.16} height={DH * 0.28} color="#15171A" />
              <Rect x={DX} y={DY} width={DW} height={DH} color="rgba(185,205,255,1)" opacity={backLit} blendMode="plus" />
            </Group>
          </Group>
          <Path path={doorEdge} opacity={edgeA}>
            <LinearGradient start={vec(0, DY)} end={vec(0, DY + DH)} colors={['#A9AEB6', '#5E636B']} />
          </Path>
          <Circle cx={ledX} cy={ledY} r={7} color={ledColor} opacity={ledGlowA} blendMode="plus">
            <BlurMask blur={6} style="normal" />
          </Circle>
          <Circle cx={ledX} cy={ledY} r={2.4} color={ledColor} opacity={ledA} />

          {/* the header keeps clear air over the wall */}
          <Rect x={0} y={0} width={W} height={top + 190}>
            <LinearGradient start={vec(0, 0)} end={vec(0, top + 190)} colors={['rgba(14,13,12,0.98)', 'rgba(14,13,12,0.82)', 'rgba(14,13,12,0)']} positions={[0, 0.55, 1]} />
          </Rect>

          {/* the sheet: frosted glass over the wall */}
          <BackdropBlur blur={18} clip={sheetClip}>
            <Path path={sheetClip} color={C.sheet} />
          </BackdropBlur>
          <Path path={sheetClip} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, SB - RECEIPT_H)} end={vec(0, SB)} colors={['rgba(255,255,255,0.22)', 'rgba(255,255,255,0.05)']} />
          </Path>

          {/* the parcel on its way out, over everything */}
          <Group transform={parcelT} opacity={parcelOut}>
            {parcelImg && <Image image={parcelImg} x={-PW / 2} y={-PH / 2} width={PW} height={PH} fit="fill" />}
          </Group>
        </Canvas>
      )}

      {ready && (
        <>
          <View style={[styles.head, { top: top + 14 }]} pointerEvents="none">
            <Text style={styles.eyebrow} maxFontSizeMultiplier={1.3}>NORTHWIND LOCKERS · LOBBY</Text>
            <View>
              <Animated.View style={headA}>
                <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.2}>Locker 14B</Text>
                <Text style={styles.sub} maxFontSizeMultiplier={1.3}>Middle column, third row · blinking amber</Text>
              </Animated.View>
              <Animated.View style={[StyleSheet.absoluteFill, headB]}>
                <Text style={styles.title} maxFontSizeMultiplier={1.2}>Enjoy your parcel</Text>
                <Text style={styles.sub} maxFontSizeMultiplier={1.3}>Locker 14B is closed and free again</Text>
              </Animated.View>
            </View>
          </View>

          <GestureDetector gesture={doorGesture}>
            <View
              style={[styles.abs, doorHit]}
              accessible
              accessibilityRole="button"
              accessibilityLabel="Locker 14B"
              accessibilityHint="Once it is open, takes the parcel out"
              onAccessibilityTap={() => st.modify((s) => {
                'worklet';
                interrupt(s);
                take(s);
                return s;
              })}
            />
          </GestureDetector>

          <Animated.View style={[styles.sheet, { left: SX, width: SW }, sheetBox]} pointerEvents="box-none">
            <Animated.View style={[styles.pickup, pickupStyle]} pointerEvents={phase === 0 ? 'box-none' : 'none'}>
              <View style={styles.row}>
                <Text style={styles.mono}>NORTHWIND PARCELS</Text>
                <Text style={styles.mono}>MEDIUM · 2.4 KG</Text>
              </View>
              <Text style={styles.sheetTitle}>Your parcel is ready</Text>
              <Text style={styles.sheetSub}>Order 48-1173 · picked up by 9 Nov</Text>
              <View style={styles.codeRow} accessible accessibilityLabel="Pickup code 4 8 1 7">
                {['4', '8', '1', '7'].map((d, i) => (
                  <Animated.View key={i} style={[styles.chip, [d0, d1, d2, d3][i]]}>
                    <Text style={styles.chipText}>{d}</Text>
                  </Animated.View>
                ))}
              </View>
              <Animated.View style={pressStyle}>
                <Pressable onPress={openLocker} style={styles.cta} accessibilityRole="button" accessibilityLabel="Open locker 14B">
                  <Text style={styles.ctaText}>Open locker</Text>
                </Pressable>
              </Animated.View>
            </Animated.View>
            <Animated.View style={[styles.receipt, receiptStyle]} pointerEvents={phase === 1 ? 'box-none' : 'none'} accessible={phase === 1} accessibilityLabel="Picked up at 6:42 PM. Locker 14B closed.">
              <View style={styles.rowStart}>
                <Animated.View style={[styles.check, checkStyle]}>
                  <Svg width={16} height={16} viewBox="0 0 16 16">
                    <SvgPath d="M3.5 8.4 L6.6 11.3 L12.6 4.9" stroke="#0E1A13" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                  </Svg>
                </Animated.View>
                <Text style={styles.receiptTitle}>Picked up</Text>
              </View>
              <Text style={styles.sheetSub}>6:42 PM · Locker 14B closed</Text>
              <View style={styles.itemRow}>
                <View style={styles.thumb} />
                <View style={styles.itemText}>
                  <Text style={styles.itemName}>Order 48-1173</Text>
                  <Text style={styles.itemMeta}>1 parcel · medium · 2.4 kg</Text>
                </View>
              </View>
              <Pressable onPress={done} style={styles.ghost} accessibilityRole="button" accessibilityLabel="Done">
                <Text style={styles.ghostText}>Done</Text>
              </Pressable>
            </Animated.View>
          </Animated.View>
        </>
      )}
    </View>
  );
}

function chip(lit: number) {
  'worklet';
  return {
    backgroundColor: `rgba(255,178,94,${0.06 + 0.16 * lit})`,
    borderColor: `rgba(255,178,94,${0.18 + 0.62 * lit})`,
    transform: [{ translateY: -2 * lit * (1 - lit) * 4 }],
  };
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#141312', overflow: 'hidden' },
  abs: { position: 'absolute' },
  head: { position: 'absolute', left: 24, right: 24 },
  eyebrow: { fontFamily: 'PLP-Mono', fontSize: 11, letterSpacing: 1.6, color: C.mute },
  title: { fontFamily: 'PLP-Archivo-ExtraBold', fontSize: 36, lineHeight: 40, color: C.ink, letterSpacing: -0.7, marginTop: 10 },
  sub: { fontFamily: 'PLP-Archivo-Medium', fontSize: 15, lineHeight: 20, color: C.mute, marginTop: 4 },
  sheet: { position: 'absolute', overflow: 'hidden' },
  pickup: { position: 'absolute', left: 0, right: 0, top: 0, paddingHorizontal: 22, paddingTop: 20 },
  receipt: { position: 'absolute', left: 0, right: 0, top: 0, paddingHorizontal: 22, paddingTop: 22 },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  rowStart: { flexDirection: 'row', alignItems: 'center' },
  mono: { fontFamily: 'PLP-Mono', fontSize: 10.5, letterSpacing: 1.4, color: C.mute },
  sheetTitle: { fontFamily: 'PLP-Archivo-SemiBold', fontSize: 22, lineHeight: 28, color: C.ink, marginTop: 12 },
  sheetSub: { fontFamily: 'PLP-Archivo-Medium', fontSize: 14, lineHeight: 19, color: C.mute, marginTop: 2 },
  codeRow: { flexDirection: 'row', marginTop: 16 },
  chip: { width: 54, height: 50, borderRadius: 14, borderWidth: 1, marginRight: 10, alignItems: 'center', justifyContent: 'center' },
  chipText: { fontFamily: 'PLP-Mono', fontSize: 24, color: C.ink },
  cta: { height: 52, borderRadius: 26, backgroundColor: C.amber, alignItems: 'center', justifyContent: 'center', marginTop: 18 },
  ctaText: { fontFamily: 'PLP-Archivo-SemiBold', fontSize: 17, color: '#1B1308' },
  check: { width: 28, height: 28, borderRadius: 14, backgroundColor: C.green, alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  receiptTitle: { fontFamily: 'PLP-Archivo-ExtraBold', fontSize: 26, color: C.ink, letterSpacing: -0.4 },
  itemRow: { flexDirection: 'row', alignItems: 'center', marginTop: 18 },
  thumb: { width: 64, height: 64, borderRadius: 16, backgroundColor: 'rgba(255,255,255,0.06)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.10)' },
  itemText: { marginLeft: 14 },
  itemName: { fontFamily: 'PLP-Archivo-SemiBold', fontSize: 17, color: C.ink },
  itemMeta: { fontFamily: 'PLP-Archivo-Medium', fontSize: 13, color: C.mute, marginTop: 3 },
  ghost: { height: 50, borderRadius: 25, marginTop: 20, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.10)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.16)' },
  ghostText: { fontFamily: 'PLP-Archivo-SemiBold', fontSize: 16, color: C.ink },
});

export { LOOP, slamAngle };
