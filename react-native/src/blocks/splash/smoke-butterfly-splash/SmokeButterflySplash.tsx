import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  BlendMode,
  BlurStyle,
  Canvas,
  matchFont,
  Picture,
  Rect,
  Shader,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkPaint,
} from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  beginTouch,
  easeOut,
  endTouch,
  type Frame,
  frameAt,
  IDLE,
  initialTouch,
  LOOP,
  moveTouch,
  PEAK,
  seg,
} from './smokeButterflyMotion';
import { SMOKE_BUTTERFLY_SHADER } from './smokeButterflyShader';

/*
 Smoke Butterfly Splash — the launch splash of Driftwing, a wind-down app. On a dark stage lit
 softly from the top, a wisp of cyan smoke rises, curls and condenses into a glowing veined
 butterfly; "Driftwing" and its tagline fade in, the wings beat twice, then the butterfly blows
 apart into billowing smoke and sparkling dust that clears back to the dark stage (LOOP = 8 s).
 The smoke is one Skia runtime shader (domain-warped fbm whose noise loops on a circle) masked by
 a butterfly distance field; the dust and type are a Picture on top.

 Drag to stir the smoke and pull the butterfly apart; it re-forms on release, and 2 s later
 dissolves and the loop carries on. Reduced motion: the formed butterfly, still.
 */

const BG = '#02040A';
const effect = Skia.RuntimeEffect.Make(SMOKE_BUTTERFLY_SHADER);
if (!effect) throw new Error('Smoke Butterfly Splash shader did not compile.');
const P3 = Platform.OS === 'ios' ? 1 : 0;
const FAMILY = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Fonts = { title: SkFont; tagline: SkFont; footer: SkFont };

function hash(x: number) {
  'worklet';
  const v = Math.sin(x * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

function dot(c: SkCanvas, paint: SkPaint, x: number, y: number, r: number, color: string, alpha: number) {
  'worklet';
  if (alpha <= 0.004) return;
  paint.setColor(Skia.Color(color));
  paint.setAlphaf(Math.min(1, alpha));
  c.drawCircle(x, y, r, paint);
}

/** Centred text whose vertical centre sits on y, with kerning in points between glyphs. */
function spaced(c: SkCanvas, font: SkFont, text: string, y: number, kerning: number, paint: SkPaint) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + kerning;
  const metrics = font.getMetrics();
  const baseline = y - (metrics.ascent + metrics.descent) / 2;
  const positions: { x: number; y: number }[] = [];
  let x = 196.5 - total / 2 + kerning / 2;
  for (let i = 0; i < widths.length; i++) {
    positions.push({ x, y: 0 });
    x += widths[i] + kerning;
  }
  c.drawGlyphs(ids, positions, 0, baseline, font, paint);
}

function drawArt(c: SkCanvas, fonts: Fonts, s: Frame) {
  'worklet';
  const tau = 2 * Math.PI;
  const glowing = Skia.Paint();
  glowing.setAntiAlias(true);
  glowing.setBlendMode(BlendMode.Plus);
  // Ambient motes drifting up through the light; each rises once or twice per loop.
  for (let i = 0; i < 26; i++) {
    const h1 = hash(i + 1);
    const h2 = hash(i + 11);
    const h3 = hash(i + 21);
    const phase = (h1 + (s.clock / LOOP) * (1 + (i % 2))) % 1;
    const x = 30 + 333 * h2 + 14 * Math.sin(tau * phase + h3 * 6);
    const y = 830 - 720 * phase;
    const twinkle = 0.6 + 0.4 * Math.sin((tau * 3 * s.clock) / LOOP + h1 * 6);
    const a = Math.pow(Math.sin(Math.PI * phase), 1.5) * (0.16 + 0.3 * h3) * twinkle;
    dot(c, glowing, x, y, 0.5 + 0.8 * h3, '#BFEFFF', a);
  }
  // Sparkles: they gather into the wings as it forms and burst out and up as it dissolves.
  const hide = 1 - s.engage;
  if (hide > 0.01) {
    for (let i = 0; i < 140; i++) {
      const side = i % 2 === 0 ? 1 : -1;
      const u = hash(i * 3.1 + 2);
      const v = hash(i * 7.7 + 5);
      const h4 = hash(i + 41);
      const h5 = hash(i + 77);
      const ang = -1.25 + 2.3 * u;
      const rad = (0.15 + 0.85 * Math.sqrt(v)) * (ang < 0 ? 1 : 0.72);
      const ox = 196.5 + 150 * side * Math.cos(ang) * rad;
      const oy = 360 + 150 * Math.sin(ang) * rad * 0.9;
      let dx = ox - 196.5;
      let dy = oy - 360 + 20;
      const len = Math.max(1, Math.hypot(dx, dy));
      dx /= len;
      dy /= len;
      const turn = (h4 - 0.5) * 0.8;
      const ux = dx * Math.cos(turn) - dy * Math.sin(turn);
      const uy = dx * Math.sin(turn) + dy * Math.cos(turn);
      const twinkle = 0.55 + 0.45 * Math.sin((tau * 24 * s.clock) / LOOP + h5 * 6.28);
      const r = 0.6 + 1.2 * h5;
      const color = i % 3 === 0 ? '#CFF8FF' : '#7FE3FF';
      const gStart = 1.8 + 0.4 * h4;
      const g = seg(s.story, gStart, gStart + 0.8);
      if (g > 0 && g < 1) {
        const away = (1 - easeOut(g)) * 90 * (0.5 + h5);
        dot(c, glowing, ox + ux * away, oy + uy * away, r, color, Math.sin(Math.PI * g) * 0.8 * twinkle * hide);
      }
      const bStart = 5.0 + 0.35 * h4;
      const b = seg(s.story, bStart, bStart + 1.5 + 0.4 * h5);
      if (b > 0 && b < 1) {
        const travel = easeOut(b);
        const dist = travel * (70 + 120 * h5);
        const x = ox + ux * dist + 12 * Math.sin(tau * b + h4 * 6);
        const y = oy + uy * dist - 60 * travel;
        const a = Math.pow(Math.sin(Math.PI * b), 0.8) * twinkle * hide;
        if (h5 > 0.8) dot(c, glowing, x, y, r * 3, color, a * 0.22);
        dot(c, glowing, x, y, r, color, a);
      }
    }
  }
  // Type.
  const p = s.params;
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  if (p.title > 0.002) {
    const y = 584 + 8 * (1 - p.title);
    const glow = Skia.Paint();
    glow.setAntiAlias(true);
    glow.setColor(Skia.Color('#3CCBFF'));
    glow.setAlphaf(0.55 * p.title);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    spaced(c, fonts.title, 'Driftwing', y, 1.5, glow);
    ink.setColor(Skia.Color('#E6F7FF'));
    ink.setAlphaf(p.title);
    spaced(c, fonts.title, 'Driftwing', y, 1.5, ink);
  }
  if (p.tagline > 0.002) {
    ink.setColor(Skia.Color('#86A9C8'));
    ink.setAlphaf(p.tagline);
    spaced(c, fonts.tagline, 'Breathe out. Let the day dissolve.', 624 + 8 * (1 - p.tagline), 0, ink);
  }
  ink.setColor(Skia.Color('#4D6A8C'));
  ink.setAlphaf(1);
  spaced(c, fonts.footer, 'SLEEP · BREATHE · DRIFT', 792, 3, ink);
}

type Props = {
  /** Plays the smoke → butterfly → smoke story on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function SmokeButterflySplash({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);

  const frame = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const s = touch.get();
    if (!autoPlay && !asleep.get() && !s.touching && s.release >= 0 && now > s.release + IDLE + (LOOP - PEAK) + 0.2) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = !reduced && active && (autoPlay || awake);
  useEffect(() => {
    startedAt.set(-1);
    frame.setActive(running);
    return () => frame.setActive(false);
  }, [running, frame, startedAt]);

  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: FAMILY, fontSize: 40, fontWeight: '300' }),
      tagline: matchFont({ fontFamily: FAMILY, fontSize: 15, fontWeight: '400' }),
      footer: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '500' }),
    }),
    [],
  );

  const state = useDerivedValue(() => frameAt(clock.get(), touch.get(), autoPlay, reduced));
  const uniforms = useDerivedValue(() => {
    const s = state.get();
    const p = s.params;
    const speed = Math.hypot(s.velX, s.velY);
    const k = speed > 1500 ? 1500 / speed : 1;
    return {
      uP3: P3,
      uFrame: [width, height, (2 * Math.PI * s.clock) / LOOP, p.light],
      uA: [p.rise, p.column, p.halo, p.form],
      uB: [p.spread, p.flap, p.veins, s.pull],
      uTouch: [s.fingerX, s.fingerY, s.stir, p.ember],
      uDrag: [(s.velX * k) / 1500, (s.velY * k) / 1500, s.dragX, s.dragY],
    };
  });
  const scale = Math.min(width / 393, height / 852);
  const ox = (width - 393 * scale) / 2;
  const oy = (height - 852 * scale) / 2;
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.translate(ox, oy);
    canvas.scale(scale, scale);
    drawArt(canvas, fonts, state.get());
    return recorder.finishRecordingAsPicture();
  });

  const enabled = !reduced;
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .enabled(enabled)
        .onBegin((e) => {
          touch.set(beginTouch(touch.get(), (e.x - ox) / scale, (e.y - oy) / scale, clock.get()));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          const result = moveTouch(touch.get(), (e.x - ox) / scale, (e.y - oy) / scale, clock.get());
          touch.set(result.next);
          if (result.tore) scheduleOnRN(tick);
        })
        .onFinalize(() => {
          if (!touch.get().touching) return;
          touch.set(endTouch(touch.get(), clock.get()));
          scheduleOnRN(tick);
        }),
    [enabled, autoPlay, ox, oy, scale, touch, clock, wake, asleep],
  );

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.screen} accessible accessibilityRole="image" accessibilityLabel="Driftwing. Breathe out. Let the day dissolve.">
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Rect x={0} y={0} width={width} height={height}>
            <Shader source={effect!} uniforms={uniforms} />
          </Rect>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
