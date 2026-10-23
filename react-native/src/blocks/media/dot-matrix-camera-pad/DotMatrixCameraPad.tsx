import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { drawScreen } from './draw';
import {
  ISO_LABELS,
  LENS_LABELS,
  LENS_ZOOMS,
  type Layout,
  REST_X,
  REST_Y,
  SHUTTER_LABELS,
  type State,
  chipX,
  chipsY,
  clamp01,
  easeInOut,
  easeOutBack,
  evLabel,
  leftCapsule,
  leftToggle,
  mix,
  readoutY,
  rest,
  rightCapsule,
  rightToggle,
  shutterCenter,
  smoothstep,
  stateAt,
} from './model';

/*
 Dot Matrix Camera Pad — a manual camera screen whose controls are hardware-like pieces: a rubbery
 exposure wheel that turns into a glowing 11 × 8 dot-matrix pad (shutter across, ISO up), an A/M
 switch beside it, a warmth wheel, and a viewfinder showing a night street that answers every
 setting: exposure and ISO brighten it, slow shutters stretch the cars' lights into trails, high
 ISO adds grain, warmth tints it. A port of the SwiftUI block (DotMatrixCameraPad.swift); the
 numbers are the same.

 Look: black screen; viewfinder (16 pt margins, 22 pt corners, white corner brackets) over the top
 62 %; lens chips 13 / 24 / 77 MM (amber #F4C02A when chosen) at its foot. Readouts (label 12 pt,
 mono value 15 pt) above two modules: a 48 × 86 drum wheel (8 pt ridges on a cylinder, an amber
 centre line) with a 26 × 58 A/M pill (orange #F04A12 knob), and the same for warmth. A glossy
 orange #EE4313 shutter in a white ring, a gallery thumbnail and a chevron button at the bottom.

 Pad: dots 1.3 pt, swelling to 3.9 pt and brightening near the hot spot (Gaussian, σ 16 pt), the
 hot spot's row and column lit as a crosshair, the chosen dot 3.2 pt warm white, a 46 pt orange
 glow and a faint finger ring while pressed. Wheel ⇄ pad: the capsule widens 48 → 120 pt and its
 corners 15 → 18 pt, the drum fades over the first half and the dots ripple in from the left over
 the second.

 autoPlay timeline (LOOP = 10 s), every step easeInOut (cubic) unless noted:
   0.6 – 1.8   exposure wheel turns +0.0 → +0.6 (the street brightens)
   2.3 – 2.55  A → M;  2.4 – 3.0 the wheel opens into the pad
   3.1 – 3.4   the finger lands on 1/60 · ISO 400 (glow in)
   3.4 – 4.6   slides to 1/8 · ISO 100 (long light trails), holds to 5.0
   5.0 – 6.4   arcs to 1/2000 · ISO 6400 (frozen cars, grain), 6.4 – 7.2 back to 1/60 · 400
   7.2 – 7.6   lifts (glow out);  7.5 – 7.75 M → A;  7.6 – 8.2 the pad closes into the wheel
   8.2 – 9.0   exposure back to +0.0
   9.2         the shutter: press 0.25 s, the viewfinder blinks (0.36 s), the thumbnail pops
   9.6 – 10    rest (same frame as 0)
 The cars cross a whole number of times per loop, and the grain is a hash of the frame number.

 Touch: drag a wheel up or down (8 pt per step: 0.1 stop or 100 K) with a tick haptic per step;
 tap an A/M switch to swap wheel and pad (0.25 s knob, 0.6 s morph); drag on the pad to place the
 hot spot (a haptic per cell; glow in 0.15 s, out 0.4 s on release); tap a lens chip to zoom
 (0.45 s); tap the shutter to take a shot. 2 s after the last touch everything eases back to rest
 over 0.7 s and the loop starts again.
 Reduced motion: no loop; the street is still (no traffic, fixed grain); changes jump, and a shot
 only blinks.
 */

const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });
/** Skia cannot match the system's mono (ui-monospace) by name: it gets an empty typeface and draws nothing. */
const SKIA_MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const AMBER = '#F5BF29';

/** A tween: [from, to, start time, duration]. */
type Tween = number[];

type Manual = {
  ev: number;
  warmth: Tween;
  knob: Tween;
  morph: Tween;
  knobR: Tween;
  glow: Tween;
  zoom: Tween;
  px: number;
  py: number;
  captureAt: number;
};

/** Touch zones: 0 none, 1 left wheel, 2 pad, 3 left switch, 4 right wheel, 5 right switch, 6 shutter, 7–9 lens chips. */
const ZONE = { none: 0, wheelL: 1, pad: 2, toggleL: 3, wheelR: 4, toggleR: 5, shutter: 6, chip: 7 };

function still(v: number): Tween {
  'worklet';
  return [v, v, 0, 1];
}
function tweenAt(t: Tween, now: number) {
  'worklet';
  return t[0] + (t[1] - t[0]) * easeInOut((now - t[2]) / t[3]);
}
function freshManual(): Manual {
  'worklet';
  return {
    ev: 0,
    warmth: still(5200),
    knob: still(0),
    morph: still(0),
    knobR: still(0),
    glow: still(0),
    zoom: still(1),
    px: REST_X,
    py: REST_Y,
    captureAt: -1,
  };
}

function inside(p: { x: number; y: number }, r: { x: number; y: number; w: number; h: number }, pad: number) {
  'worklet';
  return p.x >= r.x - pad && p.x <= r.x + r.w + pad && p.y >= r.y - pad && p.y <= r.y + r.h + pad;
}

function tactile(kind: 'tick' | 'thump') {
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

export function DotMatrixCameraPad({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [labels, setLabels] = useState({ ev: '+0.0', shutter: '1/60', iso: '400', warmth: '5200K', lens: 1, warmAmber: false });
  const font = useMemo(() => matchFont({ fontFamily: SKIA_MONO, fontSize: 11, fontWeight: '700' }), []);

  const layout: Layout = { w: size.width, h: size.height, top: insets.top, bottom: insets.bottom };

  const clock = useSharedValue(-1);
  const appeared = useSharedValue(0);
  const autoStart = useSharedValue(-1);
  /** The ease back to rest: when it started (-1: none) and what it started from. */
  const returningAt = useSharedValue(-1);
  const returningFrom = useSharedValue<State>(rest());
  const manual = useSharedValue<Manual>(freshManual());
  const zone = useSharedValue(0);
  const start = useSharedValue({ ev: 0, warmth: 5200, x: 0, y: 0 });
  const last = useSharedValue(-1);
  const resumeAt = useSharedValue(-1);

  const shownAt = (now: number): State => {
    'worklet';
    const a = autoStart.get();
    if (a >= 0 && now >= a && !reduced) return stateAt(now - a);
    const r = returningAt.get();
    if (r >= 0) return mix(returningFrom.get(), rest(), easeInOut((now - r) / 0.7));
    const m = manual.get();
    const s: State = {
      ev: m.ev,
      warmth: tweenAt(m.warmth, now),
      knob: tweenAt(m.knob, now),
      morph: tweenAt(m.morph, now),
      knobR: tweenAt(m.knobR, now),
      px: m.px,
      py: m.py,
      glow: tweenAt(m.glow, now),
      zoom: tweenAt(m.zoom, now),
      press: 0,
      flash: 0,
      pop: 1,
    };
    if (m.captureAt >= 0) {
      const d = now - m.captureAt;
      if (!reduced) {
        if (d >= 0 && d < 0.25) s.press = Math.sin((Math.PI * d) / 0.25);
        if (d >= 0.05 && d < 0.5) s.pop = 0.82 + 0.18 * easeOutBack((d - 0.05) / 0.4);
      }
      if (d >= 0.04 && d < 0.4) s.flash = 0.85 * Math.pow(1 - (d - 0.04) / 0.36, 2);
    }
    return s;
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (clock.get() < 0) {
      appeared.set(now);
      if (autoPlay && !reduced) autoStart.set(now);
    }
    clock.set(now);
    const r = resumeAt.get();
    if (r >= 0 && now >= r) {
      resumeAt.set(-1);
      returningFrom.set(shownAt(now));
      returningAt.set(now);
      autoStart.set(now + 0.7);
    }
  });

  const shown = useDerivedValue(() => shownAt(clock.get()));
  const sceneClock = useDerivedValue(() => (reduced ? 0 : clock.get() - appeared.get()));

  // Text on the JS side changes only when a label does.
  useAnimatedReaction(
    () => {
      const s = shown.get();
      const lens = LENS_ZOOMS.findIndex((z) => Math.abs(s.zoom - z) < 0.05);
      return [
        evLabel(s.ev),
        SHUTTER_LABELS[Math.min(10, Math.max(0, Math.round(s.px * 10)))],
        ISO_LABELS[Math.min(7, Math.max(0, Math.round(s.py * 7)))],
        `${Math.round(s.warmth)}K`,
        String(lens),
        s.knobR > 0.5 ? '1' : '0',
      ].join('|');
    },
    (key, previous) => {
      if (key === previous) return;
      const [ev, shutter, iso, warmth, lens, amber] = key.split('|');
      scheduleOnRN(setLabels, { ev, shutter, iso, warmth, lens: Number(lens), warmAmber: amber === '1' });
    },
  );

  const scheduleResume = (now: number) => {
    'worklet';
    if (autoPlay && !reduced) resumeAt.set(now + 2);
  };

  const takeOver = (now: number) => {
    'worklet';
    resumeAt.set(-1);
    const s = shownAt(now);
    const m = freshManual();
    m.ev = s.ev;
    m.warmth = still(s.warmth);
    m.knob = still(s.knob);
    m.morph = still(s.morph);
    m.knobR = still(s.knobR);
    m.glow = still(s.glow);
    m.zoom = still(s.zoom);
    m.px = s.px;
    m.py = s.py;
    m.captureAt = manual.get().captureAt;
    manual.set(m);
    autoStart.set(-1);
    returningAt.set(-1);
  };

  const gesture = useMemo(() => {
    const dur = (d: number) => {
      'worklet';
      return reduced ? 0.001 : d;
    };
    const step = (v: number) => {
      'worklet';
      if (v !== last.get()) {
        last.set(v);
        scheduleOnRN(tactile, 'tick');
      }
    };
    const padAt = (x: number, y: number) => {
      'worklet';
      const r = leftCapsule(layout, 1);
      const m = { ...manual.get() };
      m.px = clamp01((x - (r.x + 10)) / (r.w - 20));
      m.py = clamp01((r.y + r.h - 10 - y) / (r.h - 20));
      manual.set(m);
      step(Math.round(m.px * 10) * 10 + Math.round(m.py * 7));
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = clock.get();
        takeOver(now);
        const s = shownAt(now);
        const p = { x: e.x, y: e.y };
        const sc = shutterCenter(layout);
        let z = ZONE.none;
        if (inside(p, leftToggle(layout, s.morph), 6)) z = ZONE.toggleL;
        else if (inside(p, rightToggle(layout), 6)) z = ZONE.toggleR;
        else if (inside(p, leftCapsule(layout, s.morph), 8)) z = s.morph >= 0.5 ? ZONE.pad : ZONE.wheelL;
        else if (inside(p, rightCapsule(layout), 8)) z = ZONE.wheelR;
        else if (inside(p, { x: sc.x - 42, y: sc.y - 32, w: 84, h: 64 }, 8)) z = ZONE.shutter;
        else {
          for (let i = 0; i < 3; i++) {
            if (inside(p, { x: chipX(layout, i) - 24, y: chipsY(layout) - 18, w: 48, h: 36 }, 0)) z = ZONE.chip + i;
          }
        }
        zone.set(z);
        const m = manual.get();
        const warmth = tweenAt(m.warmth, now);
        start.set({ ev: m.ev, warmth, x: e.x, y: e.y });
        last.set(z === ZONE.wheelL ? Math.round(m.ev * 10) : z === ZONE.wheelR ? Math.round(warmth / 100) : -1);
        if (z === ZONE.pad) {
          manual.set({ ...m, glow: [tweenAt(m.glow, now), 1, now, dur(0.15)] });
          padAt(e.x, e.y);
        }
      })
      .onUpdate((e) => {
        const z = zone.get();
        const dy = e.y - start.get().y;
        if (z === ZONE.wheelL) {
          const ev = Math.min(2, Math.max(-2, start.get().ev - dy / 80));
          manual.set({ ...manual.get(), ev });
          step(Math.round(ev * 10));
        } else if (z === ZONE.wheelR) {
          const now = clock.get();
          const w = Math.min(9000, Math.max(2500, start.get().warmth - (dy / 8) * 100));
          const m = { ...manual.get(), warmth: still(w) };
          if (m.knobR[1] < 1) m.knobR = [tweenAt(m.knobR, now), 1, now, dur(0.25)];
          manual.set(m);
          step(Math.round(w / 100));
        } else if (z === ZONE.pad) {
          padAt(e.x, e.y);
        }
      })
      .onFinalize(() => {
        const now = clock.get();
        const z = zone.get();
        zone.set(0);
        const m = { ...manual.get() };
        if (z === ZONE.wheelL) m.ev = Math.round(m.ev * 10) / 10;
        else if (z === ZONE.wheelR) m.warmth = still(Math.round(m.warmth[1] / 100) * 100);
        else if (z === ZONE.pad) m.glow = [tweenAt(m.glow, now), 0, now, dur(0.4)];
        else if (z === ZONE.toggleL) {
          const to = m.knob[1] < 0.5 ? 1 : 0;
          m.knob = [tweenAt(m.knob, now), to, now, dur(0.25)];
          m.morph = [tweenAt(m.morph, now), to, now + (reduced ? 0 : 0.1), dur(0.6)];
          scheduleOnRN(tactile, 'thump');
        } else if (z === ZONE.toggleR) {
          const to = m.knobR[1] < 0.5 ? 1 : 0;
          m.knobR = [tweenAt(m.knobR, now), to, now, dur(0.25)];
          if (to === 0) m.warmth = [tweenAt(m.warmth, now), 5200, now, dur(0.5)];
          scheduleOnRN(tactile, 'thump');
        } else if (z === ZONE.shutter) {
          m.captureAt = now;
          scheduleOnRN(tactile, 'thump');
        } else if (z >= ZONE.chip) {
          m.zoom = [tweenAt(m.zoom, now), LENS_ZOOMS[z - ZONE.chip], now, dur(0.45)];
          scheduleOnRN(tactile, 'tick');
        }
        manual.set(m);
        scheduleResume(now);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [size.width, size.height, insets.top, insets.bottom, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, layout.w, layout.h));
    if (layout.w > 0) drawScreen(canvas, layout, shown.get(), sceneClock.get(), font);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const ry = readoutY(layout) + 18;
  const exposureStyle = useReadout(shown, (s) => {
    'worklet';
    const cap = leftCapsule(layout, s.morph);
    return [cap.x + cap.w / 2, 1 - smoothstep(0, 0.5, s.morph)];
  });
  const shutterStyle = useReadout(shown, (s) => {
    'worklet';
    return [leftCapsule(layout, s.morph).x + 30, smoothstep(0.5, 1, s.morph)];
  });
  const isoStyle = useReadout(shown, (s) => {
    'worklet';
    return [leftCapsule(layout, s.morph).x + 92, smoothstep(0.5, 1, s.morph)];
  });
  const rc = rightCapsule(layout);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size.width > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View
              style={StyleSheet.absoluteFill}
              accessible
              accessibilityLabel={`Camera. Exposure ${labels.ev}, shutter ${labels.shutter}, ISO ${labels.iso}, warmth ${labels.warmth.replace('K', '')} kelvin`}
            >
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <Animated.View style={[styles.readout, { top: ry - 22 }, exposureStyle]} pointerEvents="none">
            <Readout label="Exposure" value={labels.ev} amber={false} />
          </Animated.View>
          <Animated.View style={[styles.readout, { top: ry - 22 }, shutterStyle]} pointerEvents="none">
            <Readout label="Shutter" value={labels.shutter} amber />
          </Animated.View>
          <Animated.View style={[styles.readout, { top: ry - 22 }, isoStyle]} pointerEvents="none">
            <Readout label="ISO" value={labels.iso} amber />
          </Animated.View>
          <View style={[styles.readout, { top: ry - 22, left: rc.x + rc.w / 2 - 60 }]} pointerEvents="none">
            <Readout label="Warmth" value={labels.warmth} amber={labels.warmAmber} />
          </View>

          {/* Top bar. */}
          <View style={[styles.topBar, { top: insets.top + 8 }]} pointerEvents="none">
            <Svg width={14} height={20} viewBox="0 0 14 20">
              <SvgPath d="M8.68 0 L0 11.6 L6.72 11.6 L5.32 20 L14 8.4 L7.28 8.4 Z" stroke="rgba(255,255,255,0.9)" strokeWidth={1.6} strokeLinejoin="round" fill="none" />
            </Svg>
            <Svg width={18} height={18} viewBox="0 0 18 18" style={styles.grid}>
              <SvgPath d="M6 0 V18 M12 0 V18 M0 6 H18 M0 12 H18" stroke="rgba(255,255,255,0.9)" strokeWidth={1.6} strokeLinecap="round" />
            </Svg>
            <View style={styles.flex} />
            <Text style={styles.topText}>HD</Text>
            <Text style={[styles.topText, styles.fps]}>30 FPS</Text>
          </View>

          {/* Lens chips. */}
          {LENS_LABELS.map((label, i) => {
            const color = labels.lens === i ? AMBER : '#FFFFFF';
            return (
              <View key={label} style={[styles.chip, { left: chipX(layout, i) - 24, top: chipsY(layout) - 18 }]} pointerEvents="none">
                <Text style={[styles.chipNumber, { color }]}>{label}</Text>
                <Text style={[styles.chipUnit, { color }]}>MM</Text>
              </View>
            );
          })}
          <View style={[styles.person, { left: chipX(layout, 3) - 8, top: chipsY(layout) - 9 }]} pointerEvents="none">
            <Svg width={16} height={18} viewBox="0 0 16 18">
              <SvgCircle cx={8} cy={3.52} r={3.52} stroke="#FFFFFF" strokeWidth={1.5} fill="none" />
              <SvgPath d="M0 18 C0 8.1 16 8.1 16 18" stroke="#FFFFFF" strokeWidth={1.5} strokeLinecap="round" fill="none" />
            </Svg>
          </View>
        </>
      ) : null}
    </View>
  );
}

/** A readout's position (its centre's x) and opacity, on the UI thread. */
function useReadout(shown: SharedValue<State>, place: (s: State) => number[]) {
  return useAnimatedStyle(() => {
    const [x, opacity] = place(shown.get());
    return { opacity, transform: [{ translateX: x - 60 }] };
  });
}

function Readout({ label, value, amber }: { label: string; value: string; amber: boolean }) {
  return (
    <>
      <Text style={styles.readoutLabel} numberOfLines={1}>
        {label}
      </Text>
      <Text style={[styles.readoutValue, amber ? styles.amber : null]} numberOfLines={1}>
        {value}
      </Text>
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  readout: { position: 'absolute', left: 0, width: 120, height: 44, alignItems: 'center', justifyContent: 'center', gap: 4 },
  readoutLabel: { fontSize: 12, fontWeight: '500', color: 'rgba(255,255,255,0.85)' },
  readoutValue: { fontFamily: MONO, fontSize: 15, fontWeight: '600', color: '#FFFFFF' },
  amber: { color: AMBER },
  topBar: { position: 'absolute', left: 24, right: 24, height: 28, flexDirection: 'row', alignItems: 'center' },
  grid: { marginLeft: 20 },
  flex: { flex: 1 },
  topText: { fontFamily: MONO, fontSize: 13, fontWeight: '600', color: 'rgba(255,255,255,0.9)' },
  fps: { marginLeft: 20 },
  chip: { position: 'absolute', width: 48, height: 36, alignItems: 'center', justifyContent: 'center' },
  chipNumber: { fontFamily: MONO, fontSize: 14, fontWeight: '600' },
  chipUnit: { fontFamily: MONO, fontSize: 8, fontWeight: '600' },
  person: { position: 'absolute', width: 16, height: 18 },
});
