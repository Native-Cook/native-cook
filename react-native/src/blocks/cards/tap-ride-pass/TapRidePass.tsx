import {
  BlendColor,
  Blur,
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  Image,
  LinearGradient,
  Oval,
  Path,
  PathOp,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Text as SkText,
  interpolateColors,
  useFont,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Line, Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useMotionPreference } from '@/shared/useMotionPreference';

import {
  AFTER,
  CADENCE,
  FARE,
  LOOP,
  NEAR,
  S,
  SPRING,
  START_BALANCE,
  TICKET,
  easeInOut,
  easeOut,
  rollAfter,
  script,
  seg,
  smooth,
  springTo,
  ticketAfter,
  type Geo,
} from './trp-motion';
import { type Cue, useTrpAudio } from './use-trp-audio';

/*
 Tap Ride Pass. A night station: a graphite gate validator in the middle of the screen, a clear acrylic
 transit card (its copper antenna inside) held at the bottom. Bring the card to the reader and it lies
 down onto the glass in perspective; the reader ticks faster and its ring brightens, the copper coil
 inside the card glows as it is powered; inside 150 pt a magnet takes it. On the reader it is read:
 a flash and a ripple across the glass, the card is lifted away, and a paper ticket prints out of the
 slot, stands up, then rises to the middle of the screen as the ride ticket (the balance rolls).
 Done slides the ticket back into the slot.
 Art: the station, the validator and the card are image renders (assets/); everything that moves or
 lights up is drawn over them in Skia, all text is real text.
 autoPlay: LOOP = 9 s: rest 0-1.2, lift, approach 1.45-2.8, magnet 2.8, read 3.12, the card is lifted
 home and the ticket prints 3.7-4.45, rises 4.45-5.15, balance 5.35-5.95, back into the slot 7.05-7.8.
*/

const C = {
  ink: '#F3EEE6',
  mute: '#9A9084',
  amber: '#FFB25E',
  ok: '#62E3A6',
  red: '#E8452C',
  paper: '#F4F0E8',
  paperInk: '#17140F',
  paperMute: '#7C746A',
};

// image sizes (px) and the measured features of the art
const VAL = { w: 697, h: 1440, headW: 694, headTop: 2, rx: 346, ry: 206, erx: 166, ery: 105, slotY: 380, slot0: 148, slot1: 547 };
const CARD = { w: 1600, h: 1009 };
const DOCK_TILT = Math.acos(VAL.ery / VAL.erx); // the reader's ellipse says how far the glass leans back

const PAPER_FX = Skia.RuntimeEffect.Make(`
uniform float2 uSize;
half4 main(float2 p) {
  float2 uv = p / uSize + 0.5;
  // thermal paper: warm white, fibre grain, a faint sheen across the middle, edges a touch darker
  float n = fract(sin(dot(floor(p * 2.0), float2(12.9898, 78.233))) * 43758.5453);
  float f = fract(sin(dot(floor(p * float2(0.5, 3.0)), float2(39.346, 11.135))) * 24634.6345);
  half3 c = half3(0.960, 0.945, 0.915) - half3(0.045, 0.040, 0.034) * half(uv.y);
  c += half((n - 0.5) * 0.026 + (f - 0.5) * 0.012);
  float sheen = exp(-pow((uv.x * 0.6 + uv.y * 0.4 - 0.42) / 0.18, 2.0));
  c += half3(0.03, 0.03, 0.028) * half(sheen);
  float2 e = min(uv, 1.0 - uv) * uSize;
  float edge = 1.0 - smoothstep(0.0, 6.0, min(e.x, e.y));
  c -= half3(0.06, 0.055, 0.05) * half(edge);
  // a slight curl: the lower half catches a little less light
  c -= half3(0.025, 0.024, 0.022) * half(smoothstep(0.55, 1.0, uv.y));
  return half4(c, 1.0);
}
`)!;

let debugState = '';

const REST = 0;
const DRAG = 1;
const RETURN = 2;
const ASSIST = 3;
const DOCK = 4;
const READ = 5; // read: the card goes home, the ticket prints and rises, then stays up
const CLOSE = 6;

type St = {
  auto: number;
  mode: number;
  t: number;
  bt: number;
  now: number;
  touchAt: number;
  held: number;
  cx: number;
  cy: number;
  vx: number;
  vy: number;
  fx: number;
  fy: number;
  sx: number;
  sy: number;
  lift: number;
  rz: number;
  vrz: number;
  m: number;
  roll: number;
  dockAt: number;
  tapAt: number;
  tapAge: number;
  closeAt: number;
  closeFrom: number;
  phase: number;
  tickAt: number;
  p: number;
  open: number;
};

type Props = { autoPlay?: boolean };

export function TapRidePass({ autoPlay = false }: Props) {
  const reduced = useMotionPreference();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [ticketOpen, setTicketOpen] = useState(false);
  const [balance, setBalance] = useState(START_BALANCE);
  const [fontsOk] = useFonts({
    'TRP-Archivo-ExtraBold': require('./assets/fonts/Archivo-ExtraBold.ttf'),
    'TRP-Archivo-SemiBold': require('./assets/fonts/Archivo-SemiBold.ttf'),
    'TRP-Archivo-Medium': require('./assets/fonts/Archivo-Medium.ttf'),
    'TRP-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
  });
  const backdrop = useImage(require('./assets/backdrop.jpg'));
  const validator = useImage(require('./assets/validator.webp'));
  const cardImg = useImage(require('./assets/card.webp'));
  const coilImg = useImage(require('./assets/card-coil.webp'));
  const fMono = useFont(require('./assets/fonts/JetBrainsMono-Medium.ttf'), 11);
  const fMonoS = useFont(require('./assets/fonts/JetBrainsMono-Medium.ttf'), 9);
  const fName = useFont(require('./assets/fonts/Archivo-SemiBold.ttf'), 15);
  const fBal = useFont(require('./assets/fonts/Archivo-ExtraBold.ttf'), 24);
  const fRoundel = useFont(require('./assets/fonts/Archivo-ExtraBold.ttf'), 8.5);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  // ------------------------------------------------------------------ geometry
  const W = size?.width ?? 402;
  const H = size?.height ?? 874;
  const top = insets.top;
  const vs = Math.min(W * 0.78, 330) / VAL.headW; // the validator's head spans 78% of the width
  const VW = VAL.w * vs;
  const VH = VAL.h * vs;
  const VX = (W - VW) / 2;
  const VY = H * 0.3 - VAL.headTop * vs;
  const ERX = VX + VAL.rx * vs;
  const ERY = VY + VAL.ry * vs;
  const erx = VAL.erx * vs;
  const ery = VAL.ery * vs;
  const SY = VY + VAL.slotY * vs;
  const SW = (VAL.slot1 - VAL.slot0) * vs;
  const CW = Math.min(W - 32, 372);
  const CH = (CW * CARD.h) / CARD.w;
  const HX = W / 2;
  const HY = H - insets.bottom - 18 - CH / 2;
  const DS = (erx * 2 * 1.22) / CW; // the card's scale lying on the glass
  const DX = ERX;
  const DY = ERY - 2;
  const DHOME = Math.hypot(HX - DX, HY - DY);
  const TW = Math.min(W - 48, 340);
  const TH = 430;
  const TX = W / 2;
  const TY = top + 140 + TH / 2;
  const PW = SW * 0.86; // the ticket's width as it prints out of the slot
  const PH = (PW * TH) / TW; // its height at that width
  const PE = PH * 0.42; // how much of it shows once printed
  const geo: Geo = useMemo(() => ({ hx: HX, hy: HY, rx: DX, ry: DY }), [HX, HY, DX, DY]);

  // ------------------------------------------------------------------ state
  const st = useSharedValue<St>({
    auto: 0, mode: REST, t: 0, bt: 0, now: 0, touchAt: -10, held: 0,
    cx: HX, cy: HY, vx: 0, vy: 0, fx: HX, fy: HY, sx: HX, sy: HY,
    lift: 0, rz: 0, vrz: 0, m: 0, roll: 0, dockAt: -10, tapAt: -10, tapAge: -10, closeAt: -10, closeFrom: 0,
    phase: 0, tickAt: -10, p: 0, open: 0,
  });
  const tick = useSharedValue(0);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    st.modify((s) => {
      'worklet';
      if (s.mode === REST && s.auto === 0) {
        s.cx = HX;
        s.cy = HY;
      }
      return s;
    });
  }, [HX, HY, st]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tapRide?: unknown };
    g.__tapRide = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      manual: () => debugT.set(-3),
      state: () => debugState,
    };
    return () => {
      delete g.__tapRide;
    };
  }, [debugT, st]);

  const mirror = (v: string) => {
    debugState = v;
  };
  const play = useTrpAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };
  const commitRide = () => setBalance((b) => {
    const n = Math.round((b - FARE) * 100) / 100;
    return n < FARE ? n + 20 : n; // auto-reload keeps the demo riding
  });

  // ------------------------------------------------------------------ the clock
  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const s = st.get();
    s.now += dt;
    const now = s.now;
    const dbg = debugT.get();
    const px = s.cx;

    if (dbg === -3 && s.auto === 1) {
      // dev: hand the screen to the finger for a test, at rest
      s.auto = 0;
      s.mode = REST;
      s.cx = geo.hx;
      s.cy = geo.hy;
      s.m = 0;
      s.roll = 0;
      s.lift = 0;
      s.tapAge = -10;
    }
    // the script takes over again 2 s after the last touch, once everything rests, at a loop time
    // whose pose is the rest pose and whose breathing matches
    if (auto && dbg !== -3 && s.auto === 0 && s.mode === REST && now - s.touchAt > 2 && s.m < 0.001) {
      const tm = s.bt % 3;
      if (tm < 1.15) {
        s.t = tm;
        s.auto = 1;
      } else if (tm >= 2.3) {
        s.t = tm + 6;
        s.auto = 1;
      }
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
      s.bt = t;
      const pose = script(t, geo);
      s.vx = (pose.cx - px) / dt;
      s.cx = pose.cx;
      s.cy = pose.cy;
      s.lift = pose.lift;
      s.m = pose.m;
      s.roll = pose.roll;
      s.tapAge = pose.tapAge;
    } else {
      s.bt = (s.bt + dt) % LOOP;
      const n = Math.max(1, Math.ceil(dt / (1 / 240)));
      const h = dt / n;
      for (let i = 0; i < n; i++) {
        let tx = geo.hx;
        let ty = geo.hy;
        let sp: { duration: number; ratio: number } = SPRING.home;
        if (s.mode === DRAG) {
          // the finger, with the reader's magnet taking over as the card nears
          const dx = geo.rx - s.fx;
          const dy = geo.ry - s.fy;
          const d = Math.sqrt(dx * dx + dy * dy);
          const pull = NEAR.pullMax * Math.pow(smooth(NEAR.pull, NEAR.snap - 6, d), 1.4);
          tx = s.fx + dx * pull;
          ty = s.fy + dy * pull;
          sp = SPRING.follow;
        } else if (s.mode === ASSIST) {
          tx = geo.rx;
          ty = geo.ry;
          sp = SPRING.assist;
        } else if (s.mode === DOCK || (s.mode === READ && now - s.tapAt < AFTER.away)) {
          tx = geo.rx;
          ty = geo.ry;
          sp = SPRING.dock;
        }
        const a = springTo(s.cx, s.vx, tx, sp.duration, sp.ratio, h);
        s.cx = a[0];
        s.vx = a[1];
        const b = springTo(s.cy, s.vy, ty, sp.duration, sp.ratio, h);
        s.cy = b[0];
        s.vy = b[1];
      }
      const liftT = s.mode === ASSIST || (s.held === 1 && (s.mode === DRAG || s.mode === REST || s.mode === RETURN)) ? 1 : 0;
      s.lift += (liftT - s.lift) * (1 - Math.exp(-dt * (liftT > s.lift ? 18 : 12)));

      const ddx = s.cx - geo.rx;
      const ddy = s.cy - geo.ry;
      const d = Math.sqrt(ddx * ddx + ddy * ddy);
      if ((s.mode === DRAG || s.mode === ASSIST) && d < NEAR.snap) {
        s.mode = DOCK;
        s.dockAt = now;
        s.held = 0;
        scheduleOnRN(buzz, 1);
      }
      if (s.mode === DOCK && now - s.dockAt > NEAR.dwell) {
        s.mode = READ;
        s.tapAt = now;
        scheduleOnRN(buzz, 2);
        scheduleOnRN(cue, 'chime');
      }
      if (s.mode === READ) {
        const tt = now - s.tapAt;
        if (tt - dt < AFTER.print[0] && tt >= AFTER.print[0]) scheduleOnRN(cue, 'print');
        s.m = reduced ? (tt > 0.3 ? 1 : 0) : ticketAfter(tt);
        s.roll = rollAfter(tt);
      }
      if (s.mode === CLOSE) {
        const k = easeInOut(seg(now - s.closeAt, 0, reduced ? 0.25 : TICKET.close));
        s.m = s.closeFrom * (1 - k);
        if (k >= 1 && Math.abs(s.cx - geo.hx) + Math.abs(s.cy - geo.hy) < 0.4) {
          s.mode = REST;
          s.m = 0;
          s.roll = 0;
          s.tapAt = -10;
        }
      }
      if (s.mode === RETURN && Math.abs(s.cx - geo.hx) + Math.abs(s.cy - geo.hy) < 0.4 && Math.abs(s.vx) + Math.abs(s.vy) < 4) {
        s.mode = REST;
        s.cx = geo.hx;
        s.cy = geo.hy;
        s.vx = 0;
        s.vy = 0;
      }
      s.tapAge = s.tapAt > 0 ? now - s.tapAt : -10;
    }
    const open = s.m > 0.9 ? 1 : 0;
    if (open !== s.open) {
      s.open = open;
      scheduleOnRN(setTicketOpen, open === 1);
    }

    // the card sways with its sideways speed while it is held up, not lying on the glass
    const rzT = reduced ? 0 : Math.max(-6, Math.min(6, s.vx * 0.01));
    const r = springTo(s.rz, s.vrz, rzT, SPRING.tilt.duration, SPRING.tilt.ratio, dt);
    s.rz = r[0];
    s.vrz = r[1];

    // the reader's ticks: faster as the card nears; one light haptic and click each under a finger
    const ddx2 = s.cx - geo.rx;
    const ddy2 = s.cy - geo.ry;
    const near = 1 - Math.min(1, Math.sqrt(ddx2 * ddx2 + ddy2 * ddy2) / DHOME);
    const sensing = s.auto === 1 ? s.t < S.tap : s.mode === DRAG || s.mode === ASSIST || s.mode === RETURN || s.mode === DOCK;
    s.p = sensing ? Math.pow(near, 1.3) : 0;
    const rate = s.p > 0.05 && !reduced ? CADENCE.idle + (CADENCE.max - CADENCE.idle) * Math.pow(s.p, 1.6) : 0;
    const before = Math.floor(s.phase);
    s.phase += rate * dt;
    if (Math.floor(s.phase) !== before) {
      s.tickAt = now;
      if (s.auto === 0) {
        scheduleOnRN(buzz, 0);
        scheduleOnRN(cue, 'tick');
      }
    }
    if (s.auto === 1 && s.t < 0.05) s.phase = 0;
    if (dbg >= 0) debugT.set(-2);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `mode=${s.mode} auto=${s.auto} m=${s.m.toFixed(2)} cx=${s.cx.toFixed(0)} cy=${s.cy.toFixed(0)} tapAge=${s.tapAge.toFixed(2)} now=${now.toFixed(1)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ------------------------------------------------------------------ actions
  /** A touch takes the pose over from the script exactly where it is. */
  const interrupt = (s: St) => {
    'worklet';
    s.touchAt = s.now;
    if (s.auto === 0) return;
    s.auto = 0;
    if (s.t >= S.close[0] && s.m > 0.001) {
      s.mode = CLOSE;
      s.closeAt = s.now;
      s.closeFrom = s.m;
    } else if (s.t >= S.tap) {
      s.mode = READ;
      s.tapAt = s.now - (s.t - S.tap);
    } else if (s.t >= S.pull) {
      s.mode = DOCK;
      s.dockAt = s.now - (s.t - S.pull) * 0.5;
    } else s.mode = RETURN;
  };
  const closeTicket = () => {
    if (st.get().auto === 0) commitRide();
    cue('close');
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.mode === READ || s.m > 0.001) {
        s.mode = CLOSE;
        s.closeAt = s.now;
        s.closeFrom = s.m;
      }
      return s;
    });
  };
  const assist = () => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.mode === REST || s.mode === RETURN) s.mode = ASSIST;
      return s;
    });
  };

  const pan = Gesture.Pan()
    .minDistance(3)
    .onBegin(() => {
      const s = st.get();
      interrupt(s);
      s.held = 1;
    })
    .onStart(() => {
      const s = st.get();
      if (s.mode === REST || s.mode === RETURN || s.mode === ASSIST) {
        s.mode = DRAG;
        s.sx = s.cx;
        s.sy = s.cy;
        s.fx = s.cx;
        s.fy = s.cy;
      }
    })
    .onUpdate((e) => {
      const s = st.get();
      s.touchAt = s.now;
      if (s.mode !== DRAG) return;
      s.fx = s.sx + e.translationX;
      s.fy = s.sy + e.translationY;
    })
    .onFinalize(() => {
      const s = st.get();
      s.held = 0;
      s.touchAt = s.now;
      if (s.mode === DRAG) s.mode = RETURN;
    });
  const tap = Gesture.Tap().onEnd(() => {
    const s = st.get();
    interrupt(s);
    if (s.mode === REST || s.mode === RETURN) s.mode = ASSIST;
  });
  const cardGesture = Gesture.Race(pan, tap);

  // ------------------------------------------------------------------ derived drawing
  /** The card between the hand (u 0, facing you) and lying on the reader (u 1). */
  const card = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const d = Math.hypot(s.cx - DX, s.cy - DY);
    const u = smooth(0, 1, 1 - Math.min(1, d / DHOME));
    const sc = (1 + (DS - 1) * u) * (1 + 0.035 * s.lift);
    const pocket = smooth(0.45, 0.95, s.m) * CH * 0.78; // the pass goes back into the pocket while the ticket is up
    return { x: s.cx, y: s.cy + pocket, u, sc, rx: DOCK_TILT * u, rz: ((s.rz * Math.PI) / 180) * (1 - u), lift: s.lift, p: s.p };
  });
  const cardT = useDerivedValue(() => {
    const c = card.get();
    return [{ translateX: c.x }, { translateY: c.y }, { perspective: 1100 }, { rotateX: c.rx }, { rotateZ: c.rz }, { scale: c.sc }];
  });
  const shadowT = useDerivedValue(() => {
    const c = card.get();
    const drop = (14 + 30 * c.lift) * (1 - 0.75 * c.u);
    return [{ translateX: c.x + 4 * (1 - c.u) }, { translateY: c.y + drop }, { perspective: 1100 }, { rotateX: c.rx }, { rotateZ: c.rz }, { scale: c.sc * 0.97 }];
  });
  const shadowA = useDerivedValue(() => 0.55 - 0.15 * card.get().lift);
  const shadowBlur = useDerivedValue(() => 10 + 14 * card.get().lift * (1 - card.get().u));
  // the coil lights with the reader's field, flares on every tick and blazes as it is read
  const coilA = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const tk = Math.max(0, 1 - (s.now - s.tickAt) / 0.22) * s.p;
    const read = s.tapAge > 0 ? Math.max(0, 1 - s.tapAge / 0.9) : 0;
    return Math.min(1, 0.85 * Math.pow(s.p, 1.2) + 0.35 * tk + read);
  });
  const coilGlowA = useDerivedValue(() => coilA.get() * 0.9);
  // the powered coil lights the glass under the card
  const spillA = useDerivedValue(() => coilA.get() * smooth(0.5, 1, card.get().u) * 0.55);

  // the reader: its ring, a ripple per tick across the glass, the read flash and its shockwave
  const ringColor = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const ok = s.tapAge > 0 ? smooth(0.15, 0.45, s.tapAge) * (s.mode === REST && s.auto === 0 ? 0 : 1) : 0;
    return interpolateColors(Math.min(ok, s.auto === 1 ? 1 - smooth(S.close[0], S.close[1], s.t) : 1), [0, 1], [C.amber, C.ok]);
  });
  const ringA = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const tk = Math.max(0, 1 - (s.now - s.tickAt) / 0.25) * s.p;
    return Math.min(1, 0.32 + 0.1 * Math.sin((2 * Math.PI * s.bt) / 3) + 0.45 * s.p + 0.3 * tk + 0.5 * smooth(0, 0.3, s.m));
  });
  const ripple = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const e = easeOut(Math.min(Math.max((s.now - s.tickAt) / 0.6, 0), 1));
    const k = 1 + 0.55 * e;
    return Skia.XYWHRect(ERX - erx * k, ERY - ery * k, erx * 2 * k, ery * 2 * k);
  });
  const rippleA = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    return reduced ? 0 : Math.max(0, 1 - (s.now - s.tickAt) / 0.6) * 0.7 * s.p;
  });
  const flash = useDerivedValue(() => {
    tick.get();
    const a = st.get().tapAge;
    return a < 0 ? 0 : smooth(0, 0.05, a) * (1 - smooth(0.05, 0.6, a));
  });
  const shock = useDerivedValue(() => {
    tick.get();
    const a = st.get().tapAge;
    const e = easeOut(Math.min(Math.max(a / 0.8, 0), 1));
    const k = 1 + 3.2 * e;
    return Skia.XYWHRect(ERX - erx * k, ERY - ery * k, erx * 2 * k, ery * 2 * k);
  });
  const shockA = useDerivedValue(() => {
    tick.get();
    const a = st.get().tapAge;
    return a < 0 || reduced ? 0 : Math.max(0, 1 - a / 0.8) * 0.85;
  });
  const flashA = useDerivedValue(() => flash.get());
  const flashR = useDerivedValue(() => erx * (1.1 + 0.9 * flash.get()));
  const sceneLight = useDerivedValue(() => flash.get() * 0.1);
  const parallax = useDerivedValue(() => {
    const c = card.get();
    return reduced ? [{ translateX: 0 }] : [{ translateX: (c.x - W / 2) * -0.04 }, { translateY: (c.y - HY) * -0.015 }];
  });

  // the ticket's paper: a strip printing out of the slot, then the whole ticket
  const paper = useDerivedValue(() => {
    tick.get();
    const m = st.get().m;
    // printing: the ticket's top comes up out of the slot; then it is pulled clear and flies to the middle
    const k = m <= TICKET.printed ? 0 : (m - TICKET.printed) / (1 - TICKET.printed);
    const e = m <= TICKET.printed ? PE * (m / TICKET.printed) : PE + (PH + 6 - PE) * smooth(0, 0.4, k);
    const yPull = SY - e + PH / 2;
    const pulled = smooth(0, 0.4, k);
    const b = smooth(0.4, 1, k);
    const w = PW + (TW - PW) * b;
    const h = (w * TH) / TW;
    return { x: ERX + (TX - ERX) * b, y: yPull + (TY - yPull) * b, w, h, r: (18 * w) / TW, k: b, inSlot: pulled < 1 ? 1 : 0 };
  });
  const paperPath = useDerivedValue(() => {
    const f = paper.get();
    const p = Skia.Path.RRect(Skia.RRectXY(Skia.XYWHRect(-f.w / 2, -f.h / 2, f.w, Math.max(0.01, f.h)), f.r, f.r));
    const notch = (12 * f.w) / TW;
    if (notch > 0.3) {
      const ny = -f.h / 2 + f.h * 0.74;
      const cut = Skia.PathBuilder.Make().addCircle(-f.w / 2, ny, notch).addCircle(f.w / 2, ny, notch).build();
      const out = Skia.Path.MakeFromOp(p, cut, PathOp.Difference);
      if (out) return out;
    }
    return p;
  });
  const paperT = useDerivedValue(() => [{ translateX: paper.get().x }, { translateY: paper.get().y }]);
  const paperU = useDerivedValue(() => ({ uSize: [paper.get().w, Math.max(1, paper.get().h)] }));
  const paperUnderA = useDerivedValue(() => (st.get().m > 0.0005 && paper.get().inSlot === 1 ? 1 : 0));
  const paperOverA = useDerivedValue(() => (st.get().m > 0.0005 && paper.get().inSlot === 0 ? 1 : 0));
  const band = useDerivedValue(() => {
    const f = paper.get();
    return Skia.XYWHRect(-f.w / 2, -f.h / 2, f.w, (6 * f.w) / TW);
  });
  const perf = useDerivedValue(() => {
    const f = paper.get();
    const y = -f.h / 2 + f.h * 0.74;
    const pad = (22 * f.w) / TW;
    return Skia.PathBuilder.Make().moveTo(-f.w / 2 + pad, y).lineTo(f.w / 2 - pad, y).build();
  });
  const perfA = useDerivedValue(() => 1);
  const paperShadow = useDerivedValue(() => {
    const f = paper.get();
    return Skia.RRectXY(Skia.XYWHRect(-f.w / 2 + 6, -f.h / 2 + 18 * f.k, f.w - 12, f.h), f.r, f.r);
  });
  const paperShadowA = useDerivedValue(() => 0.55 * paper.get().k);
  const scrimA = useDerivedValue(() => {
    tick.get();
    return 0.62 * smooth(0.4, 0.85, st.get().m);
  });

  // ------------------------------------------------------------------ RN layers
  const hitStyle = useAnimatedStyle(() => {
    const c = card.get();
    return {
      transform: [
        { translateX: c.x - CW / 2 },
        { translateY: c.y - CH / 2 },
        { scaleX: c.sc },
        { scaleY: c.sc * Math.cos(c.rx) },
      ],
    };
  });
  const ticketIn = useAnimatedStyle(() => {
    tick.get();
    const f = paper.get();
    const m = st.get().m;
    const show = !reduced && m > 0.0005 && f.inSlot === 1;
    return {
      opacity: show ? 1 : 0,
      transform: [{ translateX: f.x - TW / 2 }, { translateY: f.y - TH / 2 }, { scale: f.w / TW }],
    };
  });
  const ticketOut = useAnimatedStyle(() => {
    tick.get();
    const f = paper.get();
    const m = st.get().m;
    const show = reduced ? smooth(0.5, 1, m) : m > 0.0005 && f.inSlot === 0 ? 1 : 0;
    return {
      opacity: show,
      transform: [{ translateX: f.x - TW / 2 }, { translateY: f.y - TH / 2 }, { scale: f.w / TW }],
    };
  });
  const oldBal = useAnimatedStyle(() => {
    tick.get();
    const r = st.get().roll;
    return { opacity: 1 - r, transform: [{ translateY: -16 * r }] };
  });
  const newBal = useAnimatedStyle(() => {
    tick.get();
    const r = st.get().roll;
    return { opacity: r, transform: [{ translateY: 16 * (1 - r) }] };
  });
  const headA = useAnimatedStyle(() => {
    tick.get();
    const k = smooth(0.4, 0.7, st.get().m);
    return { opacity: 1 - k, transform: [{ translateY: -6 * k }] };
  });
  const headB = useAnimatedStyle(() => {
    tick.get();
    const k = smooth(0.62, 0.92, st.get().m);
    return { opacity: k, transform: [{ translateY: 6 * (1 - k) }] };
  });
  const doneStyle = useAnimatedStyle(() => {
    tick.get();
    const k = smooth(0.85, 1, st.get().m);
    return { opacity: k, transform: [{ translateY: 10 * (1 - k) }] };
  });
  const gatePulse = useAnimatedStyle(() => {
    tick.get();
    const u = (st.get().bt % 1.5) / 1.5;
    return { opacity: reduced ? 0 : 0.5 * (1 - u), transform: [{ scale: 1 + 1.6 * easeOut(u) }] };
  });

  const after = Math.round((balance - FARE) * 100) / 100;
  const shownAfter = after < FARE ? after + 20 : after;
  const money = (v: number) => `$${v.toFixed(2)}`;
  const ready = fontsOk && size !== null;
  const balText = money(balance);

  const ticketFace = (
    <>
      <View style={styles.tRow} pointerEvents="none">
        <View style={styles.lineRow}>
          <Roundel size={18} />
          <Text style={styles.tMono}>SINGLE RIDE</Text>
        </View>
        <Text style={styles.tMono}>31 OCT · 08:42</Text>
      </View>
      <View style={styles.codes} pointerEvents="none">
        <View>
          <Text style={styles.tCode}>HBG</Text>
          <Text style={styles.tStation}>Harbor Gate</Text>
        </View>
        <Svg width={60} height={20} style={styles.arrow}>
          <Line x1={0} y1={10} x2={52} y2={10} stroke={C.red} strokeWidth={2.2} />
          <SvgCircle cx={14} cy={10} r={2.8} fill={C.paper} stroke={C.red} strokeWidth={1.8} />
          <SvgCircle cx={27} cy={10} r={2.8} fill={C.paper} stroke={C.red} strokeWidth={1.8} />
          <SvgCircle cx={40} cy={10} r={2.8} fill={C.paper} stroke={C.red} strokeWidth={1.8} />
          <SvgPath d="M51 4 L58 10 L51 16" fill="none" stroke={C.red} strokeWidth={2.2} strokeLinejoin="round" />
        </Svg>
        <View style={styles.codeRight}>
          <Text style={styles.tCode}>OML</Text>
          <Text style={styles.tStation}>Old Mill</Text>
        </View>
      </View>
      <View style={styles.grid} pointerEvents="none">
        <View style={[styles.cell, styles.cellBottom]}>
          <Text style={styles.tLabel}>FARE</Text>
          <Text style={styles.tFare}>{money(FARE)}</Text>
        </View>
        <View style={[styles.cell, styles.cellBottom, styles.cellRight]}>
          <Text style={styles.tLabel}>BALANCE</Text>
          <View style={styles.balRoll}>
            <Animated.Text style={[styles.tValue, oldBal]}>{balText}</Animated.Text>
            <Animated.Text style={[styles.tValue, styles.balNew, newBal]}>{money(shownAfter)}</Animated.Text>
          </View>
        </View>
        <View style={styles.cell}>
          <Text style={styles.tLabel}>STOPS</Text>
          <Text style={styles.tValue}>4 · 11 min</Text>
        </View>
        <View style={[styles.cell, styles.cellRight]}>
          <Text style={styles.tLabel}>GATE</Text>
          <Text style={styles.tValue}>3</Text>
        </View>
      </View>
      <View style={[styles.stub, { top: TH * 0.74 + 20 }]} pointerEvents="none">
        <View>
          <View style={styles.lineRow}>
            <View style={styles.dotWrap}>
              <Animated.View style={[styles.dot, styles.dotHalo, gatePulse]} />
              <View style={styles.dot} />
            </View>
            <Text style={styles.tGate}>Gate 3 open</Text>
          </View>
          <Text style={styles.tSmall}>Valid until 10:42</Text>
        </View>
        <Barcode />
      </View>
    </>
  );

  return (
    <View style={styles.screen} onLayout={(e) => setSize({ width: e.nativeEvent.layout.width, height: e.nativeEvent.layout.height })}>
      {size && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {/* the station, a little wider than the screen so it can drift with the card */}
          <Group transform={parallax}>
            {backdrop && <Image image={backdrop} x={-W * 0.04} y={-H * 0.02} width={W * 1.08} height={H * 1.04} fit="cover" />}
          </Group>
          <Rect x={0} y={0} width={W} height={top + 170}>
            <LinearGradient start={vec(0, 0)} end={vec(0, top + 170)} colors={['rgba(6,6,8,0.92)', 'rgba(6,6,8,0.55)', 'rgba(6,6,8,0)']} positions={[0, 0.55, 1]} />
          </Rect>
          <Rect x={0} y={0} width={W} height={H} color="#FFD9A8" opacity={sceneLight} blendMode="plus" />

          {/* the validator on its pedestal */}
          {validator && <Image image={validator} x={VX} y={VY} width={VW} height={VH} fit="fill" />}

          {/* the reader: a recessed pad of darker glass inside its ring, then its light */}
          <Oval x={ERX - erx * 0.97} y={ERY - ery * 0.97} width={erx * 1.94} height={ery * 1.94}>
            <RadialGradient c={vec(ERX, ERY - ery * 0.3)} r={erx} colors={['rgba(22,22,26,0.35)', 'rgba(8,8,10,0.55)']} />
          </Oval>
          <Oval x={ERX - erx * 0.97} y={ERY - ery * 0.97} width={erx * 1.94} height={ery * 1.94} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, ERY - ery)} end={vec(0, ERY + ery)} colors={['rgba(0,0,0,0.6)', 'rgba(255,255,255,0.12)']} />
          </Oval>
          {/* the reader lights up */}
          <Oval x={ERX - erx} y={ERY - ery} width={erx * 2} height={ery * 2} style="stroke" strokeWidth={6} color={ringColor} opacity={ringA}>
            <BlurMask blur={7} style="normal" />
          </Oval>
          <Oval x={ERX - erx} y={ERY - ery} width={erx * 2} height={ery * 2} style="stroke" strokeWidth={1.4} color={ringColor} opacity={ringA} />
          <Oval rect={ripple} style="stroke" strokeWidth={1.2} color={C.amber} opacity={rippleA} />
          <Group transform={[{ translateX: ERX }, { translateY: ERY }, { scaleY: ery / erx }, { translateX: -ERX }, { translateY: -ERY }]}>
            <Circle cx={ERX} cy={ERY} r={flashR} opacity={flashA} blendMode="plus">
              <RadialGradient c={vec(ERX, ERY)} r={flashR} colors={['rgba(255,244,220,1)', 'rgba(255,178,94,0.55)', 'rgba(255,178,94,0)']} positions={[0, 0.4, 1]} />
            </Circle>
          </Group>
          <Oval rect={shock} style="stroke" strokeWidth={2} color="#FFE2B8" opacity={shockA}>
            <BlurMask blur={2} style="normal" />
          </Oval>

          {/* the printed strip, standing out of the slot */}
          <Group clip={Skia.XYWHRect(0, 0, W, SY)}>
            <Group transform={paperT} opacity={paperUnderA}>
              <Path path={paperPath}>
                <Shader source={PAPER_FX} uniforms={paperU} />
              </Path>
              <Group clip={paperPath}>
                <Rect rect={band} color={C.red} />
              </Group>
              <Path path={perf} style="stroke" strokeWidth={1} color="#C5BCAB">
                <DashPathEffect intervals={[4, 4]} />
              </Path>
            </Group>
          </Group>

          <Oval x={ERX - erx * 1.45} y={ERY - ery * 1.45} width={erx * 2.9} height={ery * 2.9} opacity={spillA} blendMode="plus">
            <RadialGradient c={vec(ERX, ERY)} r={erx * 1.45} colors={['rgba(255,170,80,0.9)', 'rgba(255,140,50,0.25)', 'rgba(255,140,50,0)']} positions={[0, 0.5, 1]} />
          </Oval>
          {/* the card: its shadow, the acrylic, the coil lighting up, its printing */}
          <Group transform={shadowT} opacity={shadowA}>
            <RoundedRect x={-CW / 2 + 6} y={-CH / 2 + 6} width={CW - 12} height={CH - 8} r={18} color="#000">
              <BlurMask blur={shadowBlur} style="normal" />
            </RoundedRect>
          </Group>
          <Group transform={cardT}>
            {/* a light smoke in the acrylic so the printing reads over whatever is behind the card */}
            <RoundedRect x={-CW / 2 + 3} y={-CH / 2 + 3} width={CW - 6} height={CH - 6} r={CW * 0.034} color="rgba(12,13,18,0.42)" />
            {cardImg && <Image image={cardImg} x={-CW / 2} y={-CH / 2} width={CW} height={CH} fit="fill" />}
            {coilImg && (
              <Image image={coilImg} x={-CW / 2} y={-CH / 2} width={CW} height={CH} fit="fill" opacity={coilGlowA} blendMode="plus">
                <BlendColor color="#FF9A3C" mode="srcIn" />
                <Blur blur={5} />
              </Image>
            )}
            {coilImg && (
              <Image image={coilImg} x={-CW / 2} y={-CH / 2} width={CW} height={CH} fit="fill" opacity={coilA} blendMode="plus">
                <BlendColor color="#FFD9A0" mode="srcIn" />
              </Image>
            )}
            {fMono && fMonoS && fName && fBal && fRoundel && (
              <Group transform={[{ translateX: -CW / 2 }, { translateY: -CH / 2 }]}>
                <Circle cx={60} cy={62} r={10} color={C.red} />
                <SkText x={60 - fRoundel.measureText('R2').width / 2} y={65.2} text="R2" font={fRoundel} color="#FFFFFF" />
                <SkText x={77} y={66} text="RIVERLINE" font={fMono} color={C.ink} />
                <SkText x={CW - 58 - fMono.measureText('30 DAYS').width} y={66} text="30 DAYS" font={fMono} color="#C9BFB1" />
                <Path path={routeLine(CW, CH)} style="stroke" strokeWidth={1.6} color={C.red} />
                <Path path={routeStops(CW, CH)} color={C.red} />
                <SkText x={104} y={CH * 0.47 + 20} text="HBG · OML · 4 STOPS" font={fMonoS} color="#C9BFB1" />
                <SkText x={104} y={CH - 72} text="Commuter" font={fName} color={C.ink} />
                <SkText x={104} y={CH - 55} text="0417 2048" font={fMono} color="#C9BFB1" />
                <SkText x={CW - 58 - fMonoS.measureText('BALANCE').width} y={CH - 82} text="BALANCE" font={fMonoS} color="#C9BFB1" />
                <SkText x={CW - 58 - fBal.measureText(balText).width} y={CH - 55} text={balText} font={fBal} color={C.ink} />
              </Group>
            )}
          </Group>

          {/* the ride ticket over the dimmed station */}
          <Rect x={0} y={0} width={W} height={H} color="#050506" opacity={scrimA} />
          <Group transform={paperT} opacity={paperOverA}>
            <RoundedRect rect={paperShadow} color="#000" opacity={paperShadowA}>
              <BlurMask blur={22} style="normal" />
            </RoundedRect>
            <Path path={paperPath}>
              <Shader source={PAPER_FX} uniforms={paperU} />
            </Path>
            <Group clip={paperPath}>
              <Rect rect={band} color={C.red} />
            </Group>
            <Path path={perf} style="stroke" strokeWidth={1.3} color="#C5BCAB" opacity={perfA}>
              <DashPathEffect intervals={[5, 5]} />
            </Path>
          </Group>
        </Canvas>
      )}

      {ready && (
        <>
          <View style={[styles.head, { top: top + 14 }]} pointerEvents="none">
            <View style={styles.lineRow}>
              <Roundel size={20} />
              <Text style={styles.eyebrow} maxFontSizeMultiplier={1.3}>RIVERLINE · NORTHBOUND</Text>
            </View>
            <View>
              <Animated.View style={headA}>
                <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.2}>Harbor Gate</Text>
                <Text style={styles.sub} maxFontSizeMultiplier={1.3}>Platform 2 · next train in 2 min</Text>
              </Animated.View>
              <Animated.View style={[StyleSheet.absoluteFill, headB]}>
                <Text style={styles.title} maxFontSizeMultiplier={1.2}>You’re through</Text>
                <Text style={styles.sub} maxFontSizeMultiplier={1.3}>Gate 3 open · next train in 2 min</Text>
              </Animated.View>
            </View>
          </View>

          <GestureDetector gesture={cardGesture}>
            <Animated.View
              style={[styles.hit, { width: CW, height: CH }, hitStyle]}
              pointerEvents={ticketOpen ? 'none' : 'auto'}
              accessible
              accessibilityRole="button"
              accessibilityLabel={`Riverline commuter card, balance ${balText}`}
              accessibilityHint="Touches the card to the reader to pay the fare"
              onAccessibilityTap={assist}
            />
          </GestureDetector>

          {/* while it prints, the ticket is cut off at the slot */}
          <View style={[styles.slotClip, { width: W, height: SY }]} pointerEvents="none">
            <Animated.View style={[styles.ticket, { width: TW, height: TH }, ticketIn]} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
              {ticketFace}
            </Animated.View>
          </View>
          <Animated.View
            style={[styles.ticket, { width: TW, height: TH }, ticketOut]}
            pointerEvents={ticketOpen ? 'auto' : 'none'}
            accessible={ticketOpen}
            accessibilityLabel={`Single ride ticket, Harbor Gate to Old Mill, fare ${money(FARE)}, balance ${money(shownAfter)}. Gate 3 open.`}
          >
            <Pressable style={StyleSheet.absoluteFill} onPress={closeTicket} accessibilityElementsHidden />
            {ticketFace}
          </Animated.View>

          <Animated.View style={[styles.doneWrap, { top: TY + TH / 2 + 22, width: W }, doneStyle]} pointerEvents={ticketOpen ? 'box-none' : 'none'}>
            <Pressable onPress={closeTicket} style={({ pressed }) => [styles.done, pressed && styles.donePressed]} accessibilityRole="button" accessibilityLabel="Done">
              <Text style={styles.doneText}>Done</Text>
            </Pressable>
          </Animated.View>
        </>
      )}
    </View>
  );
}

function routeLine(cw: number, ch: number) {
  const y = ch * 0.47;
  return Skia.PathBuilder.Make().moveTo(104, y).lineTo(cw - 60, y).build();
}

function routeStops(cw: number, ch: number) {
  const y = ch * 0.47;
  const b = Skia.PathBuilder.Make();
  const step = (cw - 60 - 104) / 5;
  for (let i = 0; i < 6; i++) b.addCircle(104 + step * i, y, i === 2 ? 4.2 : 2.6);
  return b.build();
}

function Roundel({ size }: { size: number }) {
  return (
    <View style={[styles.roundel, { width: size, height: size, borderRadius: size / 2 }]}>
      <Text style={[styles.roundelText, { fontSize: size * 0.42 }]}>R2</Text>
    </View>
  );
}

const BARS = [2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 2, 2, 1, 1, 3, 1, 1, 2, 1, 2, 3, 1, 1, 2, 1, 1, 2, 3, 1, 2];
function Barcode() {
  let x = 0;
  const rects = [];
  for (let i = 0; i < BARS.length; i++) {
    if (i % 2 === 0) rects.push(<SvgRect key={i} x={x} y={0} width={BARS[i]} height={38} fill={C.paperInk} />);
    x += BARS[i] + (i % 3 === 0 ? 1.5 : 1);
  }
  return (
    <Svg width={x} height={38}>
      {rects}
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#070708', overflow: 'hidden' },
  head: { position: 'absolute', left: 24, right: 24 },
  lineRow: { flexDirection: 'row', alignItems: 'center' },
  roundel: { backgroundColor: C.red, alignItems: 'center', justifyContent: 'center', marginRight: 8 },
  roundelText: { fontFamily: 'TRP-Archivo-ExtraBold', color: '#FFFFFF' },
  eyebrow: { fontFamily: 'TRP-Mono', fontSize: 11, letterSpacing: 1.6, color: C.mute },
  title: { fontFamily: 'TRP-Archivo-ExtraBold', fontSize: 38, lineHeight: 42, color: C.ink, letterSpacing: -0.8, marginTop: 12 },
  sub: { fontFamily: 'TRP-Archivo-Medium', fontSize: 15, lineHeight: 20, color: C.mute, marginTop: 4 },
  hit: { position: 'absolute', left: 0, top: 0 },
  slotClip: { position: 'absolute', left: 0, top: 0, overflow: 'hidden' },
  ticket: { position: 'absolute', left: 0, top: 0, paddingHorizontal: 22, paddingTop: 22 },
  tRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  tMono: { fontFamily: 'TRP-Mono', fontSize: 10.5, letterSpacing: 1.4, color: C.paperMute },
  codes: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: 26 },
  codeRight: { alignItems: 'flex-end' },
  arrow: { marginTop: 18 },
  tCode: { fontFamily: 'TRP-Archivo-ExtraBold', fontSize: 46, lineHeight: 50, color: C.paperInk, letterSpacing: -1.2 },
  tStation: { fontFamily: 'TRP-Archivo-Medium', fontSize: 13, color: C.paperMute, marginTop: 2 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 22, borderTopWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(0,0,0,0.18)' },
  cell: { width: '50%', paddingTop: 12, paddingBottom: 10 },
  cellBottom: { borderBottomWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(0,0,0,0.18)' },
  cellRight: { paddingLeft: 16, borderLeftWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(0,0,0,0.18)' },
  tLabel: { fontFamily: 'TRP-Mono', fontSize: 10, letterSpacing: 1.6, color: C.paperMute },
  tFare: { fontFamily: 'TRP-Archivo-ExtraBold', fontSize: 28, lineHeight: 32, color: C.paperInk, marginTop: 4, letterSpacing: -0.4 },
  tValue: { fontFamily: 'TRP-Archivo-SemiBold', fontSize: 19, lineHeight: 26, color: C.paperInk, marginTop: 4 },
  balRoll: { height: 30, overflow: 'hidden' },
  balNew: { position: 'absolute', left: 0, top: 0 },
  stub: { position: 'absolute', left: 22, right: 22, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  dotWrap: { width: 9, height: 9, marginRight: 9 },
  dot: { width: 9, height: 9, borderRadius: 4.5, backgroundColor: '#1FAF6A' },
  dotHalo: { position: 'absolute', left: 0, top: 0 },
  tGate: { fontFamily: 'TRP-Archivo-SemiBold', fontSize: 18, color: C.paperInk },
  tSmall: { fontFamily: 'TRP-Mono', fontSize: 10.5, color: C.paperMute, marginTop: 6, letterSpacing: 0.4 },
  doneWrap: { position: 'absolute', left: 0, alignItems: 'center' },
  done: { height: 48, minWidth: 140, paddingHorizontal: 28, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.10)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.16)' },
  donePressed: { transform: [{ scale: 0.97 }], backgroundColor: 'rgba(255,255,255,0.16)' },
  doneText: { fontFamily: 'TRP-Archivo-SemiBold', fontSize: 16, color: C.ink },
});

export { LOOP };
