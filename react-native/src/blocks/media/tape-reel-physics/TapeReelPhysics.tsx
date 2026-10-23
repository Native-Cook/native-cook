import { Canvas, Picture, Skia, TileMode, matchFont, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { AMBER, type Fonts, H, SPAN_X0, SPAN_X1, SPAN_Y, W, cassette, counter, fill, key, meter, rgb, rrect, shadow, stroke, text } from './tape-art';
import {
  FF,
  HOME,
  LOOP,
  PAUSE,
  PLAY,
  type Pluck,
  REW,
  SEEK,
  SLACK,
  STOP,
  type Segment,
  TOTAL,
  auto,
  clock,
  frameAt,
  level,
  moving,
} from './tape-model';

/*
 Tape Reel Physics — a cassette deck whose tape behaves like tape. A port of the SwiftUI block
 (TapeReelPhysics.swift); the numbers are the same.

 Look: dark walnut (#17140F) with a warm glow behind a big cream cassette (the screen's width
 less 32 pt, 1.58 : 1): a label with a red-and-amber band, side A, "Night Drive Mix" in a marker
 hand (Gochi Hand) and C60; a black glass window with two brown tape packs on white six-toothed
 hubs; the tape running from each pack down round two steel guides and straight across the head
 opening, with its felt pad. Under it six transport keys (rewind, play, fast forward, pause,
 stop, record), two backlit VU meters, the tape's time and progress, and a hint.

 Mechanics, all closed form (tape-model.ts):
   Reels    the packs' radii follow the tape played s (r_supply² + r_takeup² is constant, 20 to
            50 pt); each hub's angle is ∫ feed ds / r, so a fuller pack turns slower, and both
            return exactly when the tape is rewound. Spinning past 10 rad/s the teeth smear into rings.
   Slack    the runs from the packs to the guides bow out 7 pt when the transport stops and pull
            straight when it moves: a damped spring (γ 5, ω 16 rad/s) driven by steps, so it sags
            with a wobble and tightens with a twang.
   Span     pulled, the tape between the guides is a triangle through the finger; let go, it
            rings as a string (its first four modes, 5 Hz fundamental, dying in about a second);
            winding fast makes it hum (18 and 27 Hz standing waves). Pulling turns both hubs a hair.

 autoPlay timeline (LOOP = 9 s), resting stopped at 12:41 with the slack bowed:
   0.6         PLAY: the tape tightens with a twang, hubs turn, meters wake.
   1.45 – 2.35 a finger comes down on the tape at 38 %, pulls it 22 pt (1.6 – 1.95, ease-out),
               holds, and lets go at 2.1; the span rings.
   3.2         FAST FORWARD: 320×, the packs trade size, the hubs blur, the span hums.
   4.6         STOP: the slack sags with a wobble.
   5.2 – 7.4   REWIND back to 12:41 (ease in-out).
   7.4         STOP; rest to 9.0, the same frame as 0.0.

 Touch: tap a key (a light impact; play, fast forward, rewind and pause latch, stop and record
 flash); drag the tape between the guides to pull it, and let go to pluck it. 2 s after the last
 touch the tape winds back to 12:41 (1 s), stops, and the loop starts again 1 s later.
 Reduced motion: no loop, no ringing and no hum; the keys and the pull still work.
 */

const LABEL_FONT = require('./assets/GochiHand-Regular.ttf');
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const KEY_NAMES = ['Rewind', 'Play', 'Fast forward', 'Pause', 'Stop', 'Record'];

function impact(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Rigid : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function TapeReelPhysics({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const labelFont = useFont(LABEL_FONT, 24);
  const fonts = useMemo<Fonts | null>(() => {
    if (!labelFont) return null;
    const sans = (fontSize: number, fontWeight: '400' | '500' | '600' | '700') => matchFont({ fontFamily: SANS, fontSize, fontWeight });
    // Menlo has only Regular and Bold, and 500 picks Bold; Regular is closer to SF Mono Medium.
    const mono = (fontSize: number, fontWeight: '500' | '600') => matchFont({ fontFamily: MONO, fontSize, fontWeight: fontWeight === '500' ? '400' : '700' });
    return {
      label: labelFont,
      bold12: sans(12, '700'),
      semi10: sans(10, '600'),
      mono8: mono(8, '500'),
      bold13: sans(13, '700'),
      semi11: sans(11, '600'),
      semi24: sans(24, '600'),
      mono12: mono(12, '500'),
      mono17: mono(17, '600'),
      regular12: sans(12, '400'),
    };
  }, [labelFont]);

  // Layout, as the Swift block: a header, then the cassette, keys, meters and progress with four equal gaps.
  const w = size.width;
  const h = size.height;
  const top = insets.top;
  const k = (w - 32) / W;
  const ch = H * k;
  const keyW = (w - 32 - 40) / 6;
  const meterW = (w - 32 - 14) / 2;
  const meterH = 160;
  const hintY = h - Math.max(insets.bottom, 16) - 18;
  const gap = Math.max(20, (hintY - 28 - (top + 76) - (ch + 56 + meterH + 24)) / 4);
  const x0 = 16;
  const y0 = top + 76 + gap;
  const keysY = y0 + ch + gap;
  const metersY = keysY + 56 + gap;
  const progressY = metersY + meterH + gap;
  const spanX0 = x0 + SPAN_X0 * k;
  const spanX1 = x0 + SPAN_X1 * k;
  const spanY = y0 + SPAN_Y * k;

  // The block's clock: seconds since it appeared (-1 before the first frame).
  const clk = useSharedValue(-1);
  const origin = useSharedValue(-1);
  const autoStart = useSharedValue(autoPlay && !reduced ? 0 : -1);
  const segments = useSharedValue<Segment[]>([[0, STOP, HOME, 0, 0]]);
  const sagBase = useSharedValue(SLACK);
  const steps = useSharedValue<number[][]>([]);
  const pluck = useSharedValue<Pluck>([]);
  const flashUntil = useSharedValue(-1);
  const resumeAt = useSharedValue(-1);
  const lastNotch = useSharedValue(0);

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (origin.get() < 0) origin.set(now);
    const t = now - origin.get();
    clk.set(t);
    // 2 s after the last touch: wind back to 12:41 (1 s), stop, and the loop 1 s later.
    const r = resumeAt.get();
    if (r >= 0 && t >= r) {
      resumeAt.set(-1);
      const g = segments.get();
      const f = frameAt(t, g, sagBase.get(), steps.get(), pluck.get());
      const current = g[g.length - 1][1];
      segments.set([...g, [t, SEEK, f.s, HOME, 1], [t + 1, STOP, HOME, 0, 0]]);
      const st = steps.get().slice();
      if (!moving(current)) st.push([t, -SLACK]);
      st.push([t + 1, SLACK]);
      steps.set(st);
      autoStart.set(t + 2);
    }
  });

  /** The frame at a time, and the time its pluck and hum are measured on. */
  const frameOf = (t: number) => {
    'worklet';
    const start = autoStart.get();
    if (start >= 0 && t >= start && !reduced) {
      const lt = (t - start) % LOOP;
      return { f: auto(lt), at: lt };
    }
    return { f: frameAt(t, segments.get(), sagBase.get(), steps.get(), pluck.get()), at: t };
  };

  /** Takes the tape over from the loop (if it is playing) into the touch timeline. */
  const takeOver = () => {
    'worklet';
    resumeAt.set(-1);
    const t = Math.max(clk.get(), 0);
    const start = autoStart.get();
    if (start >= 0 && t >= start) {
      const lt = (t - start) % LOOP;
      const f = auto(lt);
      const p = f.pluck;
      pluck.set(p.length === 3 ? [p[0], p[1], p[2] < 0 ? t : t - (lt - p[2])] : []);
      const target = moving(f.mode) ? 0 : SLACK;
      segments.set([[t, f.mode, f.s, 0, 0]]);
      sagBase.set(f.sag);
      steps.set([[t, target - f.sag]]);
    }
    autoStart.set(-1);
    // Fold settled steps into the base.
    const st = steps.get();
    sagBase.set(sagBase.get() + st.filter((s) => t - s[0] > 3).reduce((a, s) => a + s[1], 0));
    steps.set(st.filter((s) => t - s[0] <= 3));
    const g = segments.get();
    if (g.length > 6) segments.set(g.slice(g.length - 6));
    return t;
  };

  const scheduleResume = () => {
    'worklet';
    if (autoPlay && !reduced) resumeAt.set(clk.get() + 2);
  };

  const keyGesture = (glyph: number) =>
    Gesture.Tap().onEnd(() => {
      const t = takeOver();
      scheduleOnRN(impact, glyph === 0 || glyph === 2);
      if (glyph === 5) {
        flashUntil.set(t + 0.25);
        scheduleResume();
        return;
      }
      const g = segments.get();
      const f = frameAt(t, g, sagBase.get(), steps.get(), pluck.get());
      const current = g[g.length - 1][1];
      const mode = glyph === 0 ? REW : glyph === 1 ? PLAY : glyph === 2 ? FF : glyph === 3 ? (current === PAUSE ? PLAY : PAUSE) : STOP;
      if (mode !== current) {
        segments.set([...g, [t, mode, f.s, 0, 0]]);
        const old = moving(current) ? 0 : SLACK;
        const now = moving(mode) ? 0 : SLACK;
        if (old !== now) steps.set([...steps.get(), [t, now - old]]);
      }
      scheduleResume();
    });

  const keyGestures = useMemo(
    () => [0, 1, 2, 3, 4, 5].map(keyGesture),
    // The shared values are stable; the gestures only depend on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay],
  );

  const pull = useMemo(
    () => {
      const at = (x: number, y: number) => {
        'worklet';
        // The touch area starts 10 pt left of the span and 40 pt above it.
        const u = Math.min(Math.max((x - 10) / (spanX1 - spanX0), 0.08), 0.92);
        const d = Math.min(Math.max((y - 40) / k, -36), 48);
        pluck.set([u, d, -1]);
        const notch = Math.floor(Math.abs(d) / 8);
        if (notch !== lastNotch.get()) {
          lastNotch.set(notch);
          scheduleOnRN(impact, false);
        }
      };
      return Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          takeOver();
          lastNotch.set(0);
          at(e.x, e.y);
        })
        .onUpdate((e) => at(e.x, e.y))
        .onFinalize(() => {
          const p = pluck.get();
          if (p.length === 3) pluck.set([p[0], p[1], clk.get()]);
          scheduleOnRN(impact, true);
          scheduleResume();
        });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay, k, spanX0, spanX1],
  );

  const scene = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (w <= 0 || !fonts) return recorder.finishRecordingAsPicture();
    const { f, at: t } = frameOf(Math.max(clk.get(), 0));
    const still = reduced;
    const bg = Skia.Paint();
    bg.setColor(rgb(0x17140f));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: y0 + ch / 2 }, w * 0.9, [rgb(0x3a2c1c, 0.6), rgb(0x3a2c1c, 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), glow);
    // Vignette: clear inside 0.3 h of its centre, half black at 0.75 h.
    const vig = Skia.Paint();
    vig.setShader(
      Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.45 }, h * 0.75, [rgb(0, 0), rgb(0, 0), rgb(0, 0.5)], [0, 0.4, 1], TileMode.Clamp),
    );
    c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);

    // Header.
    text(c, 'SIDE A · C60', 20, top + 22, fonts.semi11, rgb(0x9b907d), 0, 0.5, 1.4);
    text(c, 'Night Drive Mix', 20, top + 46, fonts.semi24, rgb(0xf1e8d6), 0);
    counter(c, w - 16 - 84, top + 22, 84, 34, f.s / 4, fonts);

    // Cassette, on its shadow.
    shadow(c, x0 + 0.06 * (w - 32), y0 + ch - 8, 0.88 * (w - 32), 26);
    c.save();
    c.translate(x0, y0);
    c.scale(k, k);
    cassette(c, f, t, still, fonts);
    c.restore();

    // The scripted finger.
    if (f.fingerAlpha > 0.01) {
      const px = x0 + (SPAN_X0 + f.fingerX * (SPAN_X1 - SPAN_X0)) * k;
      const py = y0 + (SPAN_Y + f.fingerD) * k;
      c.drawCircle(px, py, 20, fill(Float32Array.of(1, 1, 1, 0.2 * f.fingerAlpha)));
      c.drawCircle(px, py, 19.25, stroke(Float32Array.of(1, 1, 1, 0.55 * f.fingerAlpha), 1.5));
    }

    // Keys.
    const flashing = clk.get() < flashUntil.get();
    for (let i = 0; i < 6; i++) key(c, i, x0 + i * (keyW + 8), keysY, keyW, 56, f.key === i || (i === 5 && flashing));

    // Meters.
    for (let chn = 0; chn < 2; chn++) {
      const lv = f.meters * Math.min(1, Math.max(0, level(t, chn)));
      meter(c, x0 + chn * (meterW + 14), metersY, meterW, meterH, lv, f.meters, chn === 0 ? 'L' : 'R', fonts);
    }

    // Progress.
    text(c, clock(f.s), x0, progressY, fonts.mono12, rgb(0xf1e8d6), 0);
    text(c, clock(TOTAL), w - 16, progressY, fonts.mono12, rgb(0x9b907d), 1);
    c.drawRRect(rrect(x0, progressY + 16, w - 32, 4, 2), fill(rgb(0x3a342b)));
    c.drawRRect(rrect(x0, progressY + 16, (w - 32) * (f.s / TOTAL), 4, 2), fill(rgb(AMBER)));
    text(c, 'Pull the tape, or press a key', w / 2, hintY, fonts.regular12, rgb(0x7a705f));
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {w > 0 && fonts ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={scene} />
          </Canvas>
          {keyGestures.map((g, i) => (
            <GestureDetector key={KEY_NAMES[i]} gesture={g}>
              <View
                style={[styles.hit, { left: x0 + i * (keyW + 8), top: keysY, width: keyW, height: 60 }]}
                accessible
                accessibilityRole="button"
                accessibilityLabel={KEY_NAMES[i]}
              />
            </GestureDetector>
          ))}
          <GestureDetector gesture={pull}>
            <View style={[styles.hit, { left: spanX0 - 10, top: spanY - 40, width: spanX1 - spanX0 + 20, height: 100 }]} accessible accessibilityLabel="Tape" />
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#17140F' },
  hit: { position: 'absolute' },
});
