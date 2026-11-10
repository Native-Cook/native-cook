import { Canvas, Circle } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Image, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSequence, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { depthAt } from './gac-depth';
import { clamp01, fAt, frameAt, LAND, LOOP, NEAR, SHOT, shotAt, T, uOf } from './gac-motion';
import { COLOR, COPY, D, F_REST, makeLayout } from './gac-theme';
import { GacGpu } from './gpu/GacGpu';
import { useGacAudio } from './use-gac-audio';

/*
 Glass Aperture Camera — the viewfinder of an invented social camera, laid out from the reference pin
 (389420699016707976): a street at dusk filling the screen, a row of tools, flash, the shutter and the
 camera flip, and the friends the shot goes to.

 The viewfinder is drawn on WebGPU (TypeGPU-resolved WGSL): real depth of field from the photo and a
 hand-made depth of it, gathered over taps laid out in the iris's nine-sided opening, so the street's
 lamps bloom into nonagons. Pinch the viewfinder and an iris of nine smoked-glass blades appears over
 it and follows the fingers from ƒ1.4 to ƒ16, a detent at each stop: the bokeh grows as it opens and
 the street sharpens as it closes. Tap to focus on what is under the finger. Press the shutter and the
 blades snap shut and open again; the shot drops into the friends it is sent to.

 autoPlay: LOOP = 10 s (gac-motion.ts); frame 10 s equals frame 0, the reference's screen. A touch takes
 over; 2 s after the last one the camera goes back to ƒ2.8 on the van and the loop restarts. Reduced
 motion: no loop; the iris and the shot fade instead of moving.
*/

const pixelRatio = PixelRatio.get();
const PHOTO = require('./assets/gac-street.jpg');
const PHOTO_AR = 2622 / 1206;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

function buzz(kind: 'tick' | 'snap' | 'focus' | 'land') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'land') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else if (kind === 'snap') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function GlassApertureCamera({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [, setGpuOk] = useState(false);
  const play = useGacAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const geo = useMemo(() => {
    const viewH = L.Y(D.view.bottom);
    return {
      viewH,
      photoH: L.w * PHOTO_AR,
      irisX: D.iris.x * L.sx,
      irisY: L.Y(D.iris.y),
      irisR: D.iris.r * L.sx,
      shutterX: D.shutter.x * L.sx,
      shutterY: L.Y(D.shutter.y),
      footerY: L.Y(D.footer.to),
      footerTop: L.Y(D.footer.top),
      farX: 0.36 * L.w,
      farY: 0.6 * L.w * PHOTO_AR,
      nearX: 0.75 * L.w,
      nearY: 0.64 * L.w * PHOTO_AR,
    };
  }, [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const fu = useSharedValue(uOf(F_REST)); // the f-number on its log scale, 0 … 1
  const iris = useSharedValue(0);
  const focus = useSharedValue(NEAR);
  const snap = useSharedValue(0);
  const shot = useSharedValue(-1); // s since the shutter, −1 none (shotAt in gac-motion.ts)
  const shotStart = useSharedValue(-1);
  const sent = useSharedValue(0);
  const ringT = useSharedValue(-1);
  const ringX = useSharedValue(0);
  const ringY = useSharedValue(0);
  const pinchU = useSharedValue(0);
  const lastStop = useSharedValue(0);
  const pinching = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const spread = useSharedValue(80);
  const fingerPress = useSharedValue(0);
  const ringTime = useSharedValue(-1);
  const frame = useSharedValue<number[]>([]);
  const [fLabel, setFLabel] = useState('2.8');
  const [sentShown, setSentShown] = useState(false);

  /* ------------------------------------------------------------ actions */
  const takeShot = () => {
    'worklet';
    scheduleOnRN(buzz, 'snap');
    scheduleOnRN(play, 'shutter');
    if (!reduced) snap.set(withSequence(withTiming(1, { duration: 110, easing: Easing.in(Easing.cubic) }), withTiming(0, { duration: 230, easing: Easing.out(Easing.cubic) })));
    // the flight runs on the clock (shotAt): springs from the shutter's press, so a second press restarts it
    shotStart.set(clock.get());
    shot.set(0);
    // the footer reads "Sent" as the shot drops into it
    sent.set(withSequence(withDelay(reduced ? 300 : Math.round((SHOT.go + LAND) * 1000), withTiming(1, { duration: 250 })), withDelay(1500, withTiming(0, { duration: 250 }))));
  };
  const focusAt = (x: number, y: number) => {
    'worklet';
    const dd = depthAt(x / L.w, y / geo.photoH);
    focus.set(withTiming(dd, { duration: reduced ? 1 : 520, easing: EASE_IN_OUT }));
    ringT.set(clock.get());
    ringX.set(x);
    ringY.set(y);
    scheduleOnRN(buzz, 'focus');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    iris.set(withTiming(0, { duration: 300 }));
    shot.set(-1);
    shotStart.set(-1);
    snap.set(0);
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    let ring = -1;
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      fu.set(uOf(s.fnum));
      iris.set(s.iris);
      focus.set(s.focus);
      snap.set(s.snap);
      shot.set(s.shot);
      sent.set(s.sent);
      ring = s.ring;
      ringX.set(t < T.near[0] ? geo.farX : geo.nearX);
      ringY.set(t < T.near[0] ? geo.farY : geo.nearY);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      spread.set(s.spread);
      fingerPress.set(s.press);
    } else {
      ring = ringT.get() >= 0 && clock.get() - ringT.get() < 1.2 ? clock.get() - ringT.get() : -1;
      if (shotStart.get() >= 0) {
        const before = shot.get();
        const s = clock.get() - shotStart.get();
        if (s >= SHOT.end) {
          shot.set(-1);
          shotStart.set(-1);
        } else {
          shot.set(s);
          // the tap of the shot landing in the footer
          if (!reduced && before - SHOT.go < LAND && s - SHOT.go >= LAND) scheduleOnRN(buzz, 'land');
        }
      }
    }
    ringTime.set(ring);
    frame.set([
      L.w, L.h, pixelRatio, clock.get() % 600,
      geo.viewH, geo.photoH, fAt(fu.get()), focus.get(),
      iris.get(), snap.get(), geo.irisX, geo.irisY,
      geo.irisR, 0.2, 0, 0,
    ]);
    if (mode.get() === 0) return;
    if (!auto || pinching.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      fu.set(withTiming(uOf(F_REST), { duration: 600, easing: EASE_IN_OUT }));
      focus.set(withTiming(NEAR, { duration: 600, easing: EASE_IN_OUT }));
      return;
    }
    const still = Math.abs(fu.get() - uOf(F_REST)) < 0.002 && Math.abs(focus.get() - NEAR) < 0.002 && iris.get() < 0.001 && shot.get() < 0 && sent.get() < 0.001 && snap.get() < 0.001;
    if (still && clock.get() - lastTouch.get() > 2.8) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
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
      fu.set(uOf(F_REST));
      focus.set(NEAR);
      for (const v of [iris, snap, sent, finger, fingerPress]) v.set(0);
      shot.set(-1);
      shotStart.set(-1);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__apertureCam = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), f: +fAt(fu.get()).toFixed(2), focus: +focus.get().toFixed(3), iris: +iris.get().toFixed(3), shot: +shot.get().toFixed(3) }),
    };
  }, [pin, rate, autoStart, clock, mode, fu, focus, iris, shot]);

  // the f-number on its chip, in the camera's own stops
  useAnimatedReaction(
    () => {
      const f = fAt(fu.get());
      return f < 9.95 ? Math.round(f * 10) / 10 : Math.round(f);
    },
    (now, before) => {
      if (now !== before) scheduleOnRN(setFLabel, now < 9.95 ? now.toFixed(1) : String(now));
    },
  );
  useAnimatedReaction(
    () => sent.get() > 0.5,
    (now, before) => {
      if (now !== before) scheduleOnRN(setSentShown, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // pinch: the iris shows and follows the fingers, a detent at each third of a stop
  const pinch = Gesture.Pinch()
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      pinching.set(1);
      pinchU.set(fu.get());
      lastStop.set(Math.round(fu.get() * 21));
      iris.set(withTiming(1, { duration: reduced ? 1 : 220 }));
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      // fingers apart open it (a smaller f-number), together close it
      const u = clamp01(pinchU.get() - Math.log(e.scale) * 0.55);
      fu.set(u);
      const stop = Math.round(u * 21);
      if (stop !== lastStop.get()) {
        lastStop.set(stop);
        scheduleOnRN(buzz, 'tick');
        scheduleOnRN(play, 'tick');
      }
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      pinching.set(0);
      iris.set(withDelay(500, withTiming(0, { duration: reduced ? 1 : 360 })));
    });
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e) => {
      takeOver();
      focusAt(e.x, e.y);
    });
  const viewTouch = Gesture.Exclusive(pinch, tap);

  const shoot = () => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      takeShot();
    });
  };

  /* ------------------------------------------------------------ drawing */
  const chipStyle = useAnimatedStyle(() => ({ opacity: iris.get(), transform: [{ translateY: (1 - iris.get()) * 6 }] }));
  // the shot: the whole viewfinder recoiling in the shutter's kick, then tossed into the footer on springs
  // (gac-motion.ts shotAt), stretched along its speed, tilting as it goes
  const shotStyle = useAnimatedStyle(() => {
    const s = shot.get();
    if (s < 0) return { opacity: 0, transform: [{ translateY: 0 }, { rotate: '0deg' }, { scaleX: 1 }, { scaleY: 1 }] };
    if (reduced) return { opacity: s < SHOT.go ? 1 : 1 - clamp01((s - SHOT.go) / 0.25), transform: [{ translateY: 0 }, { rotate: '0deg' }, { scaleX: 1 }, { scaleY: 1 }] };
    const p = shotAt(s);
    const st = 1 + 0.14 * Math.abs(p.stretch);
    return {
      opacity: p.alpha,
      // it falls into the footer as into a slot: its centre comes to rest just inside the footer's top
      // edge, where the footer (drawn over it) hides it
      transform: [{ translateY: (geo.footerTop + (SHOT.k.to * geo.viewH) / 2 + 6 - geo.viewH / 2) * p.y }, { rotate: `${p.rot}deg` }, { scaleX: p.k / Math.sqrt(st) }, { scaleY: p.k * st }],
    };
  });
  // the footer gives under the landing shot and springs back
  const bumpY = useDerivedValue(() => (reduced || shot.get() < 0 ? 0 : 5 * shotAt(shot.get()).bump));
  const sentStyle = useAnimatedStyle(() => ({ opacity: sent.get(), transform: [{ translateY: (1 - sent.get()) * 6 + bumpY.get() }] }));
  const restStyle = useAnimatedStyle(() => ({ opacity: 1 - sent.get(), transform: [{ translateY: bumpY.get() }, { scale: 1 - 0.012 * bumpY.get() }] }));
  const ringR = useDerivedValue(() => {
    const t = ringTime.get();
    return t < 0 ? 0 : 46 - 18 * Math.min(1, t / 0.25);
  });
  const ringColor = useDerivedValue(() => {
    const t = ringTime.get();
    return `rgba(255,214,90,${t < 0 ? 0 : t < 0.9 ? 0.95 : 0.95 * (1 - (t - 0.9) / 0.3)})`;
  });
  const fA = useDerivedValue(() => {
    const at = fingerAt.get();
    if (at === 0) return { x: geo.irisX - spread.get() / 2, y: geo.irisY + spread.get() * 0.35 };
    if (at === 1) return { x: geo.farX, y: geo.farY };
    if (at === 3) return { x: geo.nearX, y: geo.nearY };
    return { x: geo.shutterX, y: geo.shutterY };
  });
  const fAx = useDerivedValue(() => fA.get().x);
  const fAy = useDerivedValue(() => fA.get().y);
  const fBx = useDerivedValue(() => (fingerAt.get() === 0 ? geo.irisX + spread.get() / 2 : -100));
  const fBy = useDerivedValue(() => (fingerAt.get() === 0 ? geo.irisY - spread.get() * 0.35 : -100));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.12 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.6 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={viewTouch}>
        <View style={[styles.view, { height: geo.viewH }]} accessible accessibilityRole="image" accessibilityLabel={`Viewfinder, a street at dusk, aperture f ${fLabel}`} accessibilityHint="Pinch to set the aperture, tap to focus">
          <Image source={PHOTO} style={{ width: L.w, height: geo.photoH }} accessibilityIgnoresInvertColors />
          {size && <GacGpu frame={frame} width={L.w} height={geo.viewH} photoAsset={PHOTO} disabled={false} onAvailable={setGpuOk} />}
        </View>
      </GestureDetector>

      <Animated.View style={[styles.chip, { left: geo.irisX - 34, top: geo.irisY - geo.irisR - 44 }, chipStyle]} pointerEvents="none">
        <Text style={styles.chipText} allowFontScaling={false}>{`ƒ${fLabel}`}</Text>
      </Animated.View>

      {/* the tools */}
      <View style={[styles.tool, { left: D.tools.star * L.sx - 22, top: Y(D.tools.y) - 22 }]} accessible accessibilityLabel="Favourites">
        <Svg width={18} height={18} viewBox="0 0 24 24">
          <SvgPath d="M12 1.8 L15.1 8.3 L22.2 9.2 L17 14.1 L18.3 21.2 L12 17.8 L5.7 21.2 L7 14.1 L1.8 9.2 L8.9 8.3 Z" fill={COLOR.star} />
        </Svg>
      </View>
      <View style={[styles.tool, { left: D.tools.dot * L.sx - 22, top: Y(D.tools.y) - 22 }]} accessible accessibilityLabel="One shot">
        <View style={styles.dot} />
      </View>
      <View style={[styles.tool, styles.gridTool, { left: D.tools.grid * L.sx - 22, top: Y(D.tools.y) - 22 }]} accessible accessibilityLabel="Burst of 5">
        <Svg width={14} height={14} viewBox="0 0 14 14">
          {[0, 1, 2].flatMap((r) => [0, 1, 2].map((c) => <SvgRect key={`${r}${c}`} x={c * 5} y={r * 5} width={3} height={3} rx={0.6} fill="#FFFFFF" />))}
        </Svg>
        <Text style={styles.gridText} allowFontScaling={false}>
          5
        </Text>
      </View>
      <View style={[styles.tool, { left: D.tools.pen * L.sx - 22, top: Y(D.tools.y) - 22 }]} accessible accessibilityLabel="Edit">
        <Svg width={16} height={16} viewBox="0 0 16 16">
          <SvgPath d="M2 14 L3 10.5 L11.5 2 L14 4.5 L5.5 13 Z" fill="#FFFFFF" />
        </Svg>
      </View>

      {/* flash, the shutter, the flip */}
      <PressScale style={[styles.well, { left: D.flash.x * L.sx - D.flash.r, top: Y(D.shutter.y) - D.flash.r, width: D.flash.r * 2, height: D.flash.r * 2, borderRadius: D.flash.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Flash">
        <Svg width={12} height={18} viewBox="0 0 12 18">
          <SvgPath d="M7.5 1 L1.5 10 H6 L4.5 17 L10.5 7.5 H6 Z" fill="#FFFFFF" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.shutter, { left: geo.shutterX - D.shutter.r, top: geo.shutterY - D.shutter.r, width: D.shutter.r * 2, height: D.shutter.r * 2, borderRadius: D.shutter.r }]} scale={0.9} reduced={reduced} onPress={shoot} accessibilityRole="button" accessibilityLabel="Take photo">
        <View />
      </PressScale>
      <PressScale style={[styles.well, { left: D.flip.x * L.sx - D.flip.r, top: Y(D.shutter.y) - D.flip.r, width: D.flip.r * 2, height: D.flip.r * 2, borderRadius: D.flip.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Flip camera">
        <Svg width={18} height={18} viewBox="0 0 18 18">
          <SvgPath d="M14.5 9 A5.5 5.5 0 1 1 9 3.5 H12.5 M10.5 1 L13 3.5 L10.5 6" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>

      {/* the shot, on its way: under the footer, which it drops into */}
      <Animated.View style={[styles.shot, { width: L.w, height: geo.viewH }, shotStyle]} pointerEvents="none">
        <Image source={PHOTO} style={{ width: L.w, height: geo.photoH }} />
      </Animated.View>

      {/* the friends the shot goes to */}
      <View style={[styles.footer, { top: Y(D.footer.top) }]}>
        <Animated.View style={[styles.footerBox, restStyle]}>
          <Text style={styles.to} allowFontScaling={false}>
            {COPY.to}
          </Text>
          <Text style={styles.share} allowFontScaling={false}>
            {COPY.share}
          </Text>
        </Animated.View>
        <Animated.View style={[styles.footerBox, StyleSheet.absoluteFill, sentStyle]} accessibilityLiveRegion="polite">
          {sentShown && (
            <View style={styles.sentRow}>
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <SvgCircle cx={8} cy={8} r={7.2} fill={COLOR.star} />
                <SvgPath d="M4.6 8.3 L7 10.6 L11.4 5.8" stroke="#0B0B0B" strokeWidth={1.7} fill="none" strokeLinecap="round" strokeLinejoin="round" />
              </Svg>
              <Text style={styles.to} allowFontScaling={false}>
                {COPY.sent}
              </Text>
            </View>
          )}
        </Animated.View>
      </View>


      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={ringX} cy={ringY} r={ringR} color={ringColor} style="stroke" strokeWidth={1.5} />
        <Circle cx={fAx} cy={fAy} r={fingerR} color={fingerFill} />
        <Circle cx={fAx} cy={fAy} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
        <Circle cx={fBx} cy={fBy} r={fingerR} color={fingerFill} />
        <Circle cx={fBx} cy={fBy} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  view: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  chip: { position: 'absolute', width: 68, height: 30, borderRadius: 15, backgroundColor: 'rgba(12,12,12,0.66)', alignItems: 'center', justifyContent: 'center' },
  chipText: { fontSize: 14, fontWeight: '600', color: COLOR.ink, fontVariant: ['tabular-nums'] },
  tool: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  gridTool: { flexDirection: 'row', gap: 6, width: 56 },
  gridText: { fontSize: 13, fontWeight: '500', color: COLOR.ink },
  dot: { width: 9, height: 9, borderRadius: 4.5, backgroundColor: '#FFFFFF' },
  well: { position: 'absolute', backgroundColor: COLOR.well, alignItems: 'center', justifyContent: 'center' },
  shutter: { position: 'absolute', backgroundColor: COLOR.shutter },
  footer: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLOR.footer },
  footerBox: { height: 70, alignItems: 'center', justifyContent: 'center', gap: 5 },
  sentRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  to: { fontSize: 13, fontWeight: '600', color: COLOR.ink },
  share: { fontSize: 12, fontWeight: '400', color: COLOR.mute },
  // a print: the frame's white edge reads against the live viewfinder it flies over
  shot: { position: 'absolute', left: 0, top: 0, overflow: 'hidden', borderRadius: 18, borderWidth: 7, borderColor: 'rgba(255,255,255,0.92)' },
});
