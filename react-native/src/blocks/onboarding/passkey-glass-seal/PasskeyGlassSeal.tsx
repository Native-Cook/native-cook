import { Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Rect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { PgsGpu } from './gpu/PgsGpu';
import { FLIP, frameAt, HOLD, LOOP, SCAN, turnAt } from './pgs-motion';
import { ridgeLines } from './pgs-print';
import { COLOR, COPY, D, makeLayout } from './pgs-theme';

/*
 Passkey Glass Seal — the passkey sign-in of an invented wallet, Orvane, laid out from the reference
 pin (506303183093434291): the brand's mark and name, a raised card with a fingerprint and an orange
 scan line sweeping it, "Scanning…".

 The print, the key and the scan line are drawn on WebGPU (TypeGPU-resolved WGSL), inside the card.
 Touch and hold the print: a glow runs out from its core along the ridges, with a tick at each quarter.
 Held long enough, it is read (a success haptic): the ridges come off the print, outer ones first, and
 swirl into the outline of a key, which fills with rose-gold glass, sphere-traced, and turns in its lock
 with a click. The card turns over to the welcome on its back. Tap the card to turn it back; the key
 winds back into the print.

 autoPlay: LOOP = 10 s (pgs-motion.ts); frame 10 s equals frame 0, the reference's screen. A touch takes
 over; 2 s after the last one the card turns back and the loop restarts. Reduced motion: no loop, no
 swirl; the print lights at once and the card fades to the welcome.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
/** the card's turn as a spring (pgs-motion.ts FLIP): ~10° past the face, settled in 0.75 s */
const FLIP_SPRING = { mass: 1, stiffness: FLIP.omega * FLIP.omega, damping: 2 * FLIP.zeta * FLIP.omega } as const;
/** a drag of this many card widths turns the card half over */
const SWING_W = 1.1;

/** flip's nearest face: even the print, odd the welcome */
function faceOf(f: number) {
  'worklet';
  return Math.abs(Math.round(f)) % 2;
}

function buzz(kind: 'tick' | 'success' | 'click') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function PasskeyGlassSeal({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const card = useMemo(() => {
    // the card keeps its square shape on a shorter or wider screen: one scale for both sides, centred where
    // the reference's card is centred
    const s = Math.min(L.sx, L.k);
    const cw = D.card.w * s;
    const ch = D.card.h * s;
    const cx = (D.card.x + D.card.w / 2) * L.sx;
    const cy = L.Y(D.card.y + D.card.h / 2);
    return { x: cx - cw / 2, y: cy - ch / 2, w: cw, h: ch, px: D.print.x * s, py: D.print.y * s, sc: s };
  }, [L]);
  const printPath = useMemo(() => {
    const p = Skia.Path.Make();
    for (const r of ridgeLines()) {
      r.pts.forEach(([x, y], i) => (i === 0 ? p.moveTo(card.px + x * card.sc, card.py + y * card.sc) : p.lineTo(card.px + x * card.sc, card.py + y * card.sc)));
    }
    return p;
  }, [card]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const light = useSharedValue(0);
  const unwind = useSharedValue(0);
  const keyGlass = useSharedValue(0);
  const turnRaw = useSharedValue(0);
  const flip = useSharedValue(0);
  const scan = useSharedValue(1);
  const holding = useSharedValue(0);
  const swinging = useSharedValue(0); // 1 while a finger turns the card by hand
  const swingFrom = useSharedValue(0);
  const read = useSharedValue(0); // 1 once the print has been read
  const ticks = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [status, setStatus] = useState<'scan' | 'hold' | 'read' | 'open'>('scan');

  /* ------------------------------------------------------------ actions */
  const open = () => {
    'worklet';
    read.set(1);
    scheduleOnRN(buzz, 'success');
    scan.set(withTiming(0, { duration: 200 }));
    if (reduced) {
      flip.set(withTiming(1, { duration: 250 }));
      lastTouch.set(clock.get() + 0.25);
      return;
    }
    unwind.set(withTiming(1, { duration: 1200, easing: EASE_IN_OUT }));
    keyGlass.set(withDelay(1000, withTiming(1, { duration: 600, easing: Easing.out(Easing.cubic) })));
    turnRaw.set(
      withDelay(
        1700,
        withTiming(1, { duration: 600, easing: Easing.linear }, (fin) => {
          if (fin) scheduleOnRN(buzz, 'click');
        }),
      ),
    );
    flip.set(withDelay(2500, withSpring(1, FLIP_SPRING)));
    // the welcome is shown for a while before the loop may take over again
    lastTouch.set(clock.get() + 3.2);
  };
  // the key winds back into the print, `lead` ms after the card starts its turn back to the front
  const relock = (lead: number) => {
    'worklet';
    read.set(0);
    const fast = reduced ? 1 : 0;
    const d = (ms: number) => (fast ? 0 : Math.max(0, ms - lead));
    turnRaw.set(withDelay(d(500), withTiming(0, { duration: fast ? 1 : 400 })));
    keyGlass.set(withDelay(d(750), withTiming(0, { duration: fast ? 1 : 400 })));
    unwind.set(withDelay(d(700), withTiming(0, { duration: fast ? 1 : 1200, easing: EASE_IN_OUT })));
    light.set(withDelay(d(1200), withTiming(0, { duration: fast ? 1 : 900 })));
    scan.set(withDelay(d(1700), withTiming(1, { duration: 400 })));
  };
  const close = () => {
    'worklet';
    flip.set(reduced ? withTiming(0, { duration: 250 }) : withSpring(0, FLIP_SPRING));
    relock(0);
  };
  // a turn by hand let go: the card springs on to the face the flick carries it to, with its speed
  const swingTo = (v: number) => {
    'worklet';
    const from = Math.round(swingFrom.get());
    const goal = Math.max(from - 1, Math.min(from + 1, Math.round(flip.get() + v * 0.18)));
    const front = Math.abs(goal) % 2 === 0;
    if (front && read.get() === 1) relock(150);
    flip.set(
      withSpring(goal, { ...FLIP_SPRING, velocity: v }, (fin) => {
        if (fin) flip.set(Math.abs(goal) % 2 === 0 ? 0 : 1);
      }),
    );
    if (!front) scheduleOnRN(buzz, 'tick');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // the loop's card settles on the side it was turning to
    const f = flip.get();
    if (f > 0.5) {
      read.set(1);
      for (const v of [flip, unwind, keyGlass, turnRaw, light]) v.set(withTiming(1, { duration: 300 }));
      scan.set(withTiming(0, { duration: 200 }));
    } else {
      read.set(0);
      // the glow goes out at once, so a finger starts its own hold from the core
      light.set(0);
      for (const v of [flip, unwind, keyGlass, turnRaw]) v.set(withTiming(0, { duration: 400 }));
      scan.set(withTiming(1, { duration: 300 }));
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const lt = clock.get() - autoStart.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : ((lt % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      light.set(s.light);
      unwind.set(s.unwind);
      keyGlass.set(s.key);
      turnRaw.set(s.turn);
      flip.set(s.flip);
      scan.set(s.scan);
      finger.set(s.finger);
      fingerPress.set(s.press);
    } else if (holding.get() === 1 && read.get() === 0 && unwind.get() < 0.01) {
      // held: the glow runs out; a tick at each quarter, read at the end
      const l = Math.min(1, light.get() + (reduced ? 1 : dt / HOLD));
      light.set(l);
      const q = Math.floor(l * 4);
      if (q > ticks.get() && q < 4) {
        ticks.set(q);
        scheduleOnRN(buzz, 'tick');
      }
      if (l >= 1) open();
    } else if (read.get() === 0 && holding.get() === 0 && light.get() > 0 && unwind.get() < 0.001) {
      light.set(Math.max(0, light.get() - dt * 1.6));
    }
    const sp = pin.get() >= 0 ? pin.get() : lt;
    frame.set([
      card.w, card.h, pixelRatio, clock.get() % 600,
      card.px, card.py, card.sc, card.py + 60 * Math.sin((2 * Math.PI * sp) / SCAN + Math.asin((D.scan.y - D.print.y) / 60)) * card.sc,
      light.get(), scan.get(), 0, 0,
      unwind.get(), keyGlass.get(), turnAt(turnRaw.get()), 0,
    ]);
    if (mode.get() === 0) return;

    if (!auto || holding.get() === 1 || swinging.get() === 1 || (read.get() === 1 && Math.abs(flip.get() - 1) > 0.001) || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (read.get() === 1) close();
      return;
    }
    const still = flip.get() < 0.001 && unwind.get() < 0.001 && light.get() < 0.001 && scan.get() > 0.999;
    if (still && clock.get() - lastTouch.get() > 3.6) {
      resumeAsked.set(0);
      // the scan line keeps its place: the loop restarts on a whole number of sweeps
      autoStart.set(autoStart.get() + Math.ceil((clock.get() - autoStart.get()) / SCAN) * SCAN);
      mode.set(0);
    }
  }, false);
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      for (const v of [light, unwind, keyGlass, turnRaw, flip, finger, fingerPress, read]) v.set(0);
      scan.set(1);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__passkeySeal = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), light: +light.get().toFixed(3), unwind: +unwind.get().toFixed(3), key: +keyGlass.get().toFixed(3), flip: +flip.get().toFixed(3) }),
    };
  }, [pin, rate, autoStart, clock, mode, light, unwind, keyGlass, flip]);

  useAnimatedReaction(
    () => (faceOf(flip.get()) === 1 ? 3 : unwind.get() > 0.02 ? 2 : light.get() > 0.02 ? 1 : 0),
    (now, before) => {
      if (now !== before) scheduleOnRN(setStatus, now === 3 ? 'open' : now === 2 ? 'read' : now === 1 ? 'hold' : 'scan');
    },
  );

  /* ------------------------------------------------------------ gestures */
  const hold = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      if (read.get() === 1) {
        // on the welcome a finger takes the card by its face and turns it by hand
        swingFrom.set(flip.get());
        return;
      }
      ticks.set(0);
      holding.set(1);
      scan.set(withTiming(0, { duration: 250 }));
    })
    .onUpdate((e) => {
      if (read.get() !== 1 || reduced) return;
      if (swinging.get() === 0) {
        if (Math.abs(e.translationX) < 6) return;
        swinging.set(1);
        swingFrom.set(flip.get());
      }
      // a soft stop past a whole turn either way
      const raw = e.translationX / (card.w * SWING_W);
      const lim = 1.15;
      flip.set(swingFrom.get() + lim * Math.tanh(raw / lim));
    })
    .onEnd((e) => {
      if (swinging.get() === 1) swingTo(e.velocityX / (card.w * SWING_W));
    })
    .onFinalize(() => {
      swinging.set(0);
      lastTouch.set(Math.max(lastTouch.get(), clock.get()));
      if (holding.get() === 1) {
        holding.set(0);
        if (read.get() === 0) scan.set(withDelay(300, withTiming(1, { duration: 300 })));
      }
    });
  const turnBack = Gesture.Tap()
    .maxDistance(10)
    .onEnd(() => {
      takeOver();
      if (read.get() === 1 && Math.abs(flip.get() - 1) < 0.01) close();
    });
  const cardTouch = Gesture.Exclusive(turnBack, hold);

  /* ------------------------------------------------------------ drawing */
  // the turn in perspective about the card's upright axis. Past edge-on the other face shows, turned the
  // rest of the way (a − 1/2 … 1/2 of a half turn from the nearest face), so neither face reads mirrored.
  // The card rises toward the eye as it turns, its shadow spreading and falling away.
  const cardStyle = useAnimatedStyle(() => {
    const f = flip.get();
    if (reduced) return { transform: [{ perspective: 1100 }, { scale: 1 }, { rotateY: '0deg' }], shadowOpacity: 0.5, shadowRadius: 22, shadowOffset: { width: 0, height: 12 } };
    const a = f - Math.round(f);
    const lift = Math.abs(Math.sin(Math.PI * a));
    return {
      transform: [{ perspective: 1100 }, { scale: 1 + 0.07 * lift }, { rotateY: `${180 * a}deg` }],
      shadowOpacity: 0.5 - 0.2 * lift,
      shadowRadius: 22 + 18 * lift,
      shadowOffset: { width: 0, height: 12 + 22 * lift },
    };
  });
  // Lambert: the face darkens as it turns away from the light in front of it
  const shadeStyle = useAnimatedStyle(() => ({ opacity: reduced ? 0 : 0.42 * (1 - Math.cos(Math.PI * (flip.get() - Math.round(flip.get())))) }));
  const frontStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 - flip.get() : faceOf(flip.get()) === 0 ? 1 : 0 }));
  const backStyle = useAnimatedStyle(() => ({ opacity: reduced ? flip.get() : faceOf(flip.get()) === 1 ? 1 : 0 }));
  // a glint of the light above-left slides across the glass as the face swings through its reflection
  const glintX = useDerivedValue(() => {
    const a = flip.get() - Math.round(flip.get());
    return card.w * (0.5 - 1.9 * Math.sin(Math.PI * a + 0.42));
  });
  const glintOpacity = useDerivedValue(() => {
    if (reduced) return 0;
    const a = flip.get() - Math.round(flip.get());
    return 0.5 * Math.pow(Math.max(0, Math.cos(Math.PI * a + 0.42)), 6) * Math.min(1, Math.abs(Math.sin(Math.PI * a)) * 6);
  });
  const glintStart = useDerivedValue(() => vec(glintX.get() - card.w * 0.45, 0));
  const glintEnd = useDerivedValue(() => vec(glintX.get() + card.w * 0.45, card.h * 0.35));
  const cardClip = useMemo(() => Skia.RRectXY(Skia.XYWHRect(0, 0, card.w, card.h), D.card.r, D.card.r), [card.w, card.h]);
  const flatScan = useDerivedValue(() => scan.get());
  const flatPrint = useDerivedValue(() => `rgba(255,255,255,${0.62 + 0.38 * light.get()})`);
  const flatScanY = useDerivedValue(() => {
    const f = frame.get();
    return f.length > 7 ? f[7] : card.py + 26;
  });
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.1 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.55 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const statusText = status === 'open' ? COPY.verified : status === 'read' ? COPY.read : status === 'hold' ? COPY.hold : COPY.scanning;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={[COLOR.top, COLOR.mid, COLOR.bottom]} positions={[0, 0.5, 1]} />
        </Rect>
        <Circle cx={w * 0.5} cy={-h * 0.05} r={w * 0.9}>
          <RadialGradient c={vec(w * 0.5, -h * 0.05)} r={w * 0.9} colors={['rgba(230,112,146,0.55)', 'rgba(230,112,146,0)']} />
        </Circle>
      </Canvas>

      {/* the brand: an invented mark and name */}
      <View style={[styles.brandRow, { top: L.Y(D.mark.y) - D.mark.size / 2, left: D.mark.x * L.sx - D.mark.size / 2 }]} accessible accessibilityRole="header" accessibilityLabel={COPY.brand}>
        <Svg width={D.mark.size} height={D.mark.size} viewBox="0 0 54 54">
          <SvgPath d="M27 6 A21 21 0 1 0 48 27" stroke="#FFFFFF" strokeWidth={7} fill="none" strokeLinecap="butt" />
          <SvgPath d="M37 5 L49 5 L49 17 Z" fill="#FFFFFF" />
          <SvgCircle cx={27} cy={27} r={5} fill="#FFFFFF" />
        </Svg>
        <Text style={[styles.brand, { marginLeft: (D.brand.x - D.mark.x - D.mark.size / 2) * L.sx }]} allowFontScaling={false}>
          {COPY.brand}
        </Text>
      </View>

      {/* the card turns in its own layer: a perspective turn among the screen's other layers would cut
          them along the card's plane (Core Animation sorts siblings in depth) */}
      <View style={StyleSheet.absoluteFill} pointerEvents="box-none" collapsable={false}>
      <GestureDetector gesture={cardTouch}>
        <Animated.View
          style={[styles.card, { left: card.x, top: card.y, width: card.w, height: card.h }, cardStyle]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={status === 'open' ? `${COPY.welcome}, ${COPY.name}. Tap to lock` : 'Passkey. Touch and hold to sign in'}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              if (read.get() === 1) close();
              else {
                light.set(1);
                open();
              }
            })
          }
        >
          <Animated.View style={[StyleSheet.absoluteFill, styles.face, frontStyle]}>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Rect x={0} y={0} width={card.w} height={card.h}>
                <LinearGradient start={vec(0, 0)} end={vec(0, card.h)} colors={[COLOR.card, COLOR.cardBottom]} />
              </Rect>
              {!gpuOk && (
                <Group>
                  <Path path={printPath} color={flatPrint} style="stroke" strokeWidth={1.8 * card.sc} strokeCap="round" strokeJoin="round" />
                  <Group opacity={flatScan}>
                    <Rect x={20} y={flatScanY} width={card.w - 40} height={2} color={COLOR.scan} />
                  </Group>
                </Group>
              )}
            </Canvas>
            {size && <PgsGpu frame={frame} width={card.w} height={card.h} disabled={reduced} onAvailable={setGpuOk} />}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, styles.face, styles.back, backStyle]} pointerEvents="none">
            <Svg width={64} height={64} viewBox="0 0 64 64">
              <SvgCircle cx={32} cy={32} r={30} stroke="rgba(255,255,255,0.85)" strokeWidth={1.5} fill="rgba(255,255,255,0.08)" />
              <SvgPath d="M20 33 L28 41 L44 24" stroke="#FFFFFF" strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
            <Text style={styles.welcome} allowFontScaling={false}>
              {COPY.welcome}
            </Text>
            <Text style={styles.name} allowFontScaling={false}>
              {COPY.name}
            </Text>
            <Text style={styles.saved} allowFontScaling={false}>
              {COPY.saved}
            </Text>
          </Animated.View>
          {/* the turn shades the card as it goes edge-on to the light, and a glint crosses its glass */}
          <Animated.View style={[StyleSheet.absoluteFill, styles.face, styles.shade, shadeStyle]} pointerEvents="none" />
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Group clip={cardClip} opacity={glintOpacity}>
              <Rect x={0} y={0} width={card.w} height={card.h}>
                <LinearGradient start={glintStart} end={glintEnd} colors={['rgba(255,236,244,0)', 'rgba(255,236,244,0.9)', 'rgba(255,236,244,0)']} positions={[0.3, 0.5, 0.7]} />
              </Rect>
            </Group>
          </Canvas>
        </Animated.View>
      </GestureDetector>
      </View>

      <Text style={[styles.status, { top: L.Y(D.status.y) - 13 }]} allowFontScaling={false} accessibilityLiveRegion="polite">
        {statusText}
      </Text>
      {status === 'open' && (
        <PressScale style={[styles.done, { top: L.Y(D.status.y) + 34 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel={COPY.done}>
          <Text style={styles.doneText} allowFontScaling={false}>
            {COPY.done}
          </Text>
        </PressScale>
      )}

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={card.x + card.px} cy={card.y + card.py} r={fingerR} color={fingerFill} />
        <Circle cx={card.x + card.px} cy={card.y + card.py} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.mid, overflow: 'hidden' },
  brandRow: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  brand: { fontSize: D.brand.size, fontWeight: '300', letterSpacing: D.brand.spacing, color: COLOR.ink },
  card: {
    position: 'absolute',
    borderRadius: D.card.r,
    shadowColor: '#2B1430',
  },
  face: { borderRadius: D.card.r, overflow: 'hidden' },
  shade: { backgroundColor: '#1E0E22' },
  back: { backgroundColor: COLOR.card, alignItems: 'center', justifyContent: 'center', gap: 6 },
  welcome: { marginTop: 14, fontSize: 15, fontWeight: '300', letterSpacing: 2.5, color: 'rgba(255,255,255,0.7)' },
  name: { fontSize: 26, fontWeight: '300', color: COLOR.ink },
  saved: { marginTop: 8, fontSize: 13, fontWeight: '400', color: 'rgba(255,255,255,0.72)' },
  status: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: D.status.size, fontWeight: '300', letterSpacing: 0.5, color: COLOR.mute },
  done: { position: 'absolute', alignSelf: 'center', height: 44, paddingHorizontal: 28, borderRadius: 22, borderWidth: 1, borderColor: 'rgba(255,255,255,0.5)', justifyContent: 'center' },
  doneText: { fontSize: 15, fontWeight: '400', letterSpacing: 1.5, color: COLOR.ink },
});
