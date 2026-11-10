import { Canvas, Circle, FillType, Group, Path, RadialGradient, Rect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDecay, withDelay, withSequence, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { PdlGpu } from './gpu/PdlGpu';
import { frameAt, LOOP, T } from './pdl-motion';
import { COLOR, COPY, D, makeLayout, PLACES } from './pdl-theme';

/*
 Pin Drop Location — the location primer of an invented city guide, laid out from the reference pin
 (542613455106454309): Skip; a little night map in a disc with a pink pin on it; LOCATION and a line;
 Allow.

 The map and the pin are drawn on WebGPU (TypeGPU-resolved WGSL). Drag the map and it bends into a
 globe with an atmosphere that turns under the finger, while the pin flies up out of the way; let go
 and the globe spins on, flattens onto the place it stopped at, and the pin falls onto it from above
 with a squash, rings across the map, sparks (a compute pass) and the city's lights coming on round it
 in a ring that runs outwards. Tap the map and the pin hops and drops again. Allow zooms the map in on
 the pin and three places rise round it.

 autoPlay: LOOP = 10 s (pdl-motion.ts); frame 10 s equals frame 0. A touch takes over; 2 s after the
 last one the map turns home, zooms out, and the loop restarts. Reduced motion: no loop; the map
 never bends, the pin moves without falling, and Allow zooms in 200 ms.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** the pin's outline, tip at the origin, y down, one unit tall (the WGSL pin's silhouette) */
function pinPath(H: number) {
  const cy = 0.62 * H;
  const r = 0.36 * H;
  const sa = r / cy;
  const ca = Math.sqrt(1 - sa * sa);
  const len = Math.sqrt(cy * cy - r * r);
  const p = Skia.Path.Make();
  p.moveTo(0, 0);
  p.lineTo(len * sa, -len * ca);
  const a0 = (Math.atan2(-len * ca + cy, len * sa) * 180) / Math.PI;
  p.arcToOval({ x: -r, y: -cy - r, width: 2 * r, height: 2 * r }, a0, -(180 + 2 * a0), false);
  p.lineTo(0, 0);
  p.close();
  p.addCircle(0, -cy, 0.135 * H);
  p.setFillType(FillType.EvenOdd);
  return p;
}

type Props = { autoPlay?: boolean };

export function PinDropLocation({ autoPlay = false }: Props) {
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
  const geo = useMemo(() => {
    const R = D.disc.r * L.sx;
    const cx = D.disc.x * L.sx;
    const cy = L.Y(D.disc.y);
    return { cx, cy, R, tipY: cy + 0.49 * R, allowX: L.w / 2, allowY: L.Y(D.allow.y) + (D.allow.h * L.k) / 2 };
  }, [L]);
  const pinShape = useMemo(() => pinPath(geo.R * 1.23), [geo.R]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const bend = useSharedValue(0);
  const lon = useSharedValue(0);
  const lat = useSharedValue(0);
  const fall = useSharedValue(1);
  const landedAt = useSharedValue(-1);
  const lights = useSharedValue(0);
  const zoom = useSharedValue(0);
  const places = useSharedValue(0);
  const since = useSharedValue(-1);
  const dragging = useSharedValue(0);
  const lon0 = useSharedValue(0);
  const lat0 = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const allowedNow = useSharedValue(false);
  const [allowed, setAllowed] = useState(false);

  /* ------------------------------------------------------------ actions */
  const landed = () => {
    'worklet';
    landedAt.set(clock.get());
    lights.set(1);
    scheduleOnRN(buzz, 'light');
  };
  const dropIn = (delay: number) => {
    'worklet';
    if (reduced) {
      fall.set(1);
      landed();
      return;
    }
    fall.set(
      withDelay(
        delay,
        withTiming(1, { duration: 420, easing: Easing.in(Easing.cubic) }, (fin) => {
          if (fin) landed();
        }),
      ),
    );
  };
  const hop = () => {
    'worklet';
    if (reduced) return;
    fall.set(
      withSequence(
        withTiming(0.82, { duration: 220, easing: Easing.out(Easing.cubic) }),
        withTiming(1, { duration: 260, easing: Easing.in(Easing.cubic) }, (fin) => {
          if (fin) landed();
        }),
      ),
    );
    scheduleOnRN(buzz, 'select');
  };
  const setAllow = (open: boolean) => {
    'worklet';
    const cfg = reduced ? { duration: 200 } : { duration: 900, easing: EASE_IN_OUT };
    zoom.set(withTiming(open ? 1 : 0, cfg));
    places.set(withTiming(open ? 1 : 0, reduced ? cfg : { duration: open ? 900 : 300, easing: Easing.out(Easing.cubic) }));
    scheduleOnRN(setAllowed, open);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // the loop's state settles where it was going
    const s = since.get();
    landedAt.set(s >= 0 ? clock.get() - s : -1);
    if (bend.get() > 0.001 && dragging.get() === 0) bend.set(withTiming(0, { duration: 400 }));
    if (fall.get() < 0.999 && dragging.get() === 0) dropIn(200);
    const z = zoom.get();
    if (z > 0.001 && z < 0.999) setAllow(z > 0.5);
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      bend.set(s.bend);
      lon.set(s.lon);
      lat.set(s.lat);
      fall.set(s.fall);
      since.set(s.since);
      lights.set(s.lights);
      zoom.set(s.zoom);
      places.set(s.places);
      finger.set(s.finger);
      fingerPress.set(s.press);
      if (s.fingerAt === 1) {
        fingerX.set(geo.allowX);
        fingerY.set(geo.allowY);
      } else if (s.fingerU < 0) {
        fingerX.set(geo.cx + 60);
        fingerY.set(geo.cy - 40 + 60 * (s.fingerU + 1));
      } else {
        fingerX.set(geo.cx + 60 - 120 * s.fingerU);
        fingerY.set(geo.cy + 20 + 22 * Math.sin(Math.PI * s.fingerU));
      }
      if (t >= T.allow && t < T.zoomOut[0]) {
        if (!allowedNow.get()) {
          allowedNow.set(true);
          scheduleOnRN(setAllowed, true);
        }
      } else if (allowedNow.get()) {
        allowedNow.set(false);
        scheduleOnRN(setAllowed, false);
      }
    } else {
      const la = landedAt.get();
      since.set(la >= 0 ? clock.get() - la : -1);
    }
    frame.set([
      L.w, L.h, pixelRatio, clock.get() % 600,
      geo.cx, geo.cy, geo.R, bend.get(),
      lon.get(), lat.get(), zoom.get(), places.get(),
      fall.get(), 0, since.get(), lights.get(),
    ]);
    if (mode.get() === 0) return;

    if (!auto || dragging.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (zoom.get() > 0.001) setAllow(false);
      const cfg = { duration: 1200, easing: EASE_IN_OUT };
      lon.set(withTiming(0, cfg));
      lat.set(withTiming(0, cfg));
      lights.set(withTiming(0, cfg));
      bend.set(withTiming(0, { duration: 300 }));
      return;
    }
    const still = zoom.get() < 0.001 && bend.get() < 0.001 && Math.abs(lon.get()) < 0.001 && Math.abs(lat.get()) < 0.001 && fall.get() > 0.999 && lights.get() < 0.001;
    if (still && clock.get() - lastTouch.get() > 3.4) {
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
      for (const v of [bend, lon, lat, zoom, places, finger, fingerPress, lights]) v.set(0);
      fall.set(1);
      landedAt.set(-1);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__pinDrop = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), bend: +bend.get().toFixed(3), lon: +lon.get().toFixed(3), fall: +fall.get().toFixed(3), zoom: +zoom.get().toFixed(3), since: +since.get().toFixed(2) }),
    };
  }, [pin, rate, autoStart, clock, mode, bend, lon, fall, zoom, since]);

  /* ------------------------------------------------------------ gestures */
  // drag: the map bends into a globe that turns under the finger; the pin flies up out of the way
  const spin = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      // a zoomed map zooms back out to spin
      if (zoom.get() > 0.01) setAllow(false);
      dragging.set(1);
      lon0.set(lon.get());
      lat0.set(lat.get());
      if (reduced) return;
      bend.set(withTiming(1, { duration: 380, easing: Easing.out(Easing.cubic) }));
      fall.set(withTiming(0, { duration: 340, easing: Easing.in(Easing.cubic) }));
      scheduleOnRN(buzz, 'select');
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      lon.set(lon0.get() - e.translationX / geo.R);
      lat.set(Math.max(-0.9, Math.min(0.9, lat0.get() + e.translationY / geo.R)));
    })
    .onEnd((e) => {
      if (dragging.get() === 0) return;
      if (!reduced) lon.set(withDecay({ velocity: -e.velocityX / geo.R, deceleration: 0.994 }));
      bend.set(withDelay(reduced ? 0 : 250, withTiming(0, { duration: reduced ? 1 : 520, easing: EASE_IN_OUT })));
      dropIn(reduced ? 0 : 520);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      if (dragging.get() === 1 && fall.get() < 0.999 && bend.get() > 0.5) {
        // cancelled: still bring it all back
        bend.set(withTiming(0, { duration: 400 }));
        dropIn(300);
      }
      dragging.set(0);
    });
  const tapMap = Gesture.Tap()
    .maxDistance(8)
    .onEnd(() => {
      takeOver();
      if (fall.get() > 0.999 && bend.get() < 0.01) hop();
    });
  const mapTouch = Gesture.Exclusive(spin, tapMap);

  const allow = () => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      setAllow(true);
    });
    buzz('success');
  };

  /* ------------------------------------------------------------ drawing */
  const flatDrop = useDerivedValue(() => [{ translateX: geo.cx }, { translateY: geo.tipY - (1 - fall.get()) * (geo.tipY + 140) }]);
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.08 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.55 * finger.get()})`);
  const flat = useMemo(() => {
    // the reference's map, flat: the dark half, the lit half beyond the coast and the road
    const { cx, cy, R } = geo;
    const lit = Skia.Path.Make();
    const k = 0.03 / 2;
    lit.moveTo(cx + (-1.5 + k) * R, cy + (-1.5 - k) * R + 3 * R);
    lit.lineTo(cx + (1.5 + k) * R, cy + (-1.5 - k) * R);
    lit.lineTo(cx + 2 * R, cy + 2 * R);
    lit.close();
    const road = Skia.Path.Make();
    road.moveTo(cx - 0.5 * R, cy + 0.86 * R);
    road.quadTo(cx + 0.05 * R, cy + 0.05 * R, cx + 0.9 * R, cy - 0.5 * R);
    const clip = Skia.Path.Make();
    clip.addCircle(cx, cy, R);
    return { lit, road, clip };
  }, [geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h} color={COLOR.page} />
        <Circle cx={geo.cx} cy={geo.cy + 40} r={w * 0.9}>
          <RadialGradient c={vec(geo.cx, geo.cy + 40)} r={w * 0.9} colors={[COLOR.glow, COLOR.page]} />
        </Circle>
        {!gpuOk && (
          <>
            <Group clip={flat.clip}>
              <Circle cx={geo.cx} cy={geo.cy} r={geo.R} color={COLOR.discA} />
              <Path path={flat.lit} color={COLOR.discB} />
              <Path path={flat.road} color="#C9C3F1" style="stroke" strokeWidth={4} strokeCap="round" />
            </Group>
            <Group transform={flatDrop}>
              <Path path={pinShape} color={COLOR.pink} />
            </Group>
          </>
        )}
      </Canvas>
      {size && <PdlGpu frame={frame} width={L.w} height={L.h} disabled={reduced} onAvailable={setGpuOk} />}

      <GestureDetector gesture={mapTouch}>
        <View
          style={[styles.abs, { left: geo.cx - geo.R - 20, top: geo.cy - geo.R - 20, width: geo.R * 2 + 40, height: geo.R * 2 + 40, borderRadius: geo.R + 20 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={allowed ? 'Map zoomed in on your pin, with three places nearby' : 'Map with your pin'}
          accessibilityHint="Drag to spin the globe, tap to drop the pin again"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]}
          onAccessibilityAction={(ev) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              if (ev.nativeEvent.actionName === 'activate') hop();
              else lon.set(withTiming(lon.get() + (ev.nativeEvent.actionName === 'increment' ? 0.6 : -0.6), { duration: 400 }));
            })
          }
        />
      </GestureDetector>

      {PLACES.map((p, i) => (
        <Place key={p.name} index={i} name={p.name} side={p.side} x={geo.cx + p.x * geo.R} y={geo.cy + p.y * geo.R} shown={places} />
      ))}

      <PressScale style={[styles.skip, { left: D.skip.x * L.sx, top: L.Y(D.skip.y), width: D.skip.w, height: D.skip.h }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Skip">
        <Text style={styles.skipText} allowFontScaling={false}>
          {COPY.skip}
        </Text>
      </PressScale>

      <Text style={[styles.title, { top: L.Y(D.title.y) - 11 }]} allowFontScaling={false} accessibilityRole="header">
        {COPY.title}
      </Text>
      <Text style={[styles.body, { top: L.Y(D.body.y) - D.body.line / 2, left: (L.w - D.body.w * L.sx) / 2, width: D.body.w * L.sx }]} allowFontScaling={false}>
        {COPY.body}
      </Text>

      <PressScale
        style={[styles.allow, { left: D.allow.x * L.sx, top: L.Y(D.allow.y), width: D.allow.w * L.sx, height: D.allow.h * L.k }]}
        scale={0.98}
        reduced={reduced}
        onPress={allowed ? undefined : allow}
        accessibilityRole="button"
        accessibilityLabel={allowed ? COPY.done : COPY.allow}
        accessibilityState={{ disabled: allowed }}
      >
        <Svg width={24} height={24} viewBox="0 0 24 24">
          <SvgCircle cx={12} cy={12} r={10} stroke={COLOR.pink} strokeWidth={2} fill="none" />
          <SvgPath d="M7.4 12.4 L10.6 15.4 L16.8 8.8" stroke={COLOR.pink} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
        <Text style={styles.allowText} allowFontScaling={false}>
          {allowed ? COPY.done : COPY.allow}
        </Text>
      </PressScale>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

type PlaceProps = { index: number; name: string; side: number; x: number; y: number; shown: SharedValue<number> };

function Place({ index, name, side, x, y, shown }: PlaceProps) {
  const style = useAnimatedStyle(() => {
    // each place rises in turn with its dot on the map (the WGSL draws the dot)
    const k = Math.max(0, Math.min(1, shown.get() * 1.6 - index * 0.25));
    return { opacity: k, transform: [{ translateX: side * (1 - k) * -8 }, { scale: 0.9 + 0.1 * k }] };
  });
  return (
    <Animated.View style={[styles.placeBox, { left: side < 0 ? x - 190 : x + 10, top: y - 13, alignItems: side < 0 ? 'flex-end' : 'flex-start' }, style]} pointerEvents="none" accessible accessibilityLabel={name}>
      <View style={styles.place}>
        <Text style={styles.placeName} allowFontScaling={false}>
          {name}
        </Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  skip: { position: 'absolute', borderRadius: 21, backgroundColor: COLOR.skipBg, alignItems: 'center', justifyContent: 'center' },
  skipText: { fontSize: 16, fontWeight: '600', color: COLOR.skip },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: D.title.size, lineHeight: 22, fontWeight: '700', letterSpacing: D.title.spacing, color: COLOR.title },
  body: { position: 'absolute', textAlign: 'center', fontSize: D.body.size, lineHeight: D.body.line, color: COLOR.body },
  allow: { position: 'absolute', borderRadius: D.allow.r, backgroundColor: COLOR.allowBg, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 14 },
  allowText: { fontSize: 17, fontWeight: '600', color: COLOR.allow },
  placeBox: { position: 'absolute', width: 180, height: 26, alignItems: 'flex-start' },
  place: { height: 26, paddingHorizontal: 10, borderRadius: 13, backgroundColor: 'rgba(22,19,42,0.88)', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(201,195,234,0.35)', justifyContent: 'center' },
  placeName: { fontSize: 12, fontWeight: '600', color: COLOR.title },
});
