import { Canvas, ClipOp, Picture, Skia, type SkCanvas, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  autoFrame,
  CELL,
  type Frame,
  makeSim,
  markPath,
  N,
  PITCH,
  type Sim,
  simFrame,
  simRelease,
  simTouch,
} from './ditherAsteriskMotion';

/*
 Dither Asterisk Sign In — the sign-in screen of "Footnote", an invented reading app (a port of
 DitherAsteriskSignin.swift, the reference).

 A black stage, a big white asterisk built from a 31 × 31 grid of square cells, the heavy wordmark
 "FOOTNOTE", "Read between the lines", three dark pill buttons (passkey, email, phone, with glyphs
 drawn in code) and "or browse without an account".

 Hero: the mark is sliced. Vertical slices of the grid slide up and down by whole cells, their
 cells thin into a glyph dither (■ 1 0 +) and leave glyph streaks where they were, row bands shear
 sideways at the peak, then every slice springs home and the asterisk is whole again. The mark is
 one Skia path per frame (ditherAsteriskMotion.ts, markPath).

 autoPlay: LOOP = 7 s (ditherAsteriskMotion.ts). Touch: drag across the mark to slice the columns
 under the finger (offset and dither follow the finger's movement and speed); release and they spring back.
 Light haptics on the first slice of a drag and when the mark is whole again. 2 s after the
 release, once whole, the loop restarts from t = 0. Reduced motion: the whole mark, still; no loop,
 no touch.
*/

type Props = {
  /** Plays the 7 s slice-and-reassemble loop until touched. */
  autoPlay?: boolean;
};

const BG = '#060606';
const INK = '#F4F4F2';

type Geo = {
  w: number;
  h: number;
  markLeft: number;
  markTop: number;
  markCY: number;
  span: number;
  wordTop: number;
  tagTop: number;
  footerCY: number;
  touchTop: number;
  touchBottom: number;
};

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const span = N * PITCH - 1;
  const markLeft = Math.round(((w - span) / 2) * 3) / 3;
  const markTop = Math.round(top + 64);
  return {
    w,
    h,
    span,
    markLeft,
    markTop,
    markCY: markTop + span / 2,
    wordTop: markTop + span + 34,
    tagTop: markTop + span + 34 + 82,
    footerCY: h - bottom - 24,
    touchTop: Math.max(top, markTop - 40),
    touchBottom: markTop + span + 34,
  };
}

function buttonTop(g: Geo, i: number): number {
  return g.footerCY - 32 - 54 - (2 - i) * 66;
}

function drawStage(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const cx = g.w / 2;
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeRadialGradient({ x: cx, y: g.markCY }, 300, [Skia.Color('#151515'), Skia.Color(BG)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  if (f.glow > 0.001) {
    const glow = Skia.Paint();
    glow.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx, y: g.markCY },
        190,
        [Skia.Color(`rgba(255,255,255,${f.glow})`), Skia.Color('rgba(255,255,255,0)')],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawCircle(cx, g.markCY, 190, glow);
  }
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(INK));
  // Streaks never reach into the wordmark, even under a hard drag.
  c.save();
  c.clipRect(Skia.XYWHRect(0, 0, g.w, g.wordTop + 4), ClipOp.Intersect, true);
  c.drawPath(markPath(f, g.markLeft, g.markTop), ink);
  c.restore();
}

/** The sign-in glyphs in a 20 × 20 box: 0 key, 1 envelope, 2 phone. */
function Glyph({ kind }: { kind: number }) {
  const stroke = { stroke: INK, strokeWidth: 1.6, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      {kind === 0 ? (
        <>
          <Circle cx={6} cy={10} r={4} {...stroke} />
          <Path d="M10 10 L18.5 10 M15 10 L15 13 M18 10 L18 13.5" {...stroke} />
        </>
      ) : kind === 1 ? (
        <>
          <Rect x={1.5} y={4} width={17} height={12} rx={2} {...stroke} />
          <Path d="M2.5 5.5 L10 11 L17.5 5.5" {...stroke} />
        </>
      ) : (
        <>
          <Rect x={5} y={1.5} width={10} height={17} rx={2.5} {...stroke} />
          <Path d="M8.5 15.2 L11.5 15.2" {...stroke} />
        </>
      )}
    </Svg>
  );
}

function SignInPill({ label, kind, top, g }: { label: string; kind: number; top: number; g: Geo }) {
  return (
    <View style={[styles.pill, { top, width: g.w - 32 }]} accessibilityRole="button" accessible>
      <View style={styles.pillGlyph}>
        <Glyph kind={kind} />
      </View>
      <Text style={styles.pillLabel} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

export function DitherAsteriskSignin({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<Geo>(
    () => makeGeo(size?.width ?? 1, size?.height ?? 1, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );

  const sim = useSharedValue<Sim>(makeSim());
  const frame = useSharedValue<Frame>(autoFrame(0));
  const now = useSharedValue(0);
  const feel = useSharedValue(0);

  useEffect(() => {
    sim.set(makeSim());
    feel.set(0);
    if (reduced) frame.set(autoFrame(0));
  }, [auto, reduced, sim, frame, feel]);

  const buzz = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (reduced || !size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    const s = sim.get();
    frame.set(simFrame(s, t, auto));
    if (s.feel !== feel.get()) {
      feel.set(s.feel);
      scheduleOnRN(buzz);
    }
  });

  const markLeft = geo.markLeft;
  const touch = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      simTouch(sim.get(), e.x, e.y, (e.x - markLeft - CELL / 2) / PITCH, now.get());
    })
    .onUpdate((e) => {
      simTouch(sim.get(), e.x, e.y, (e.x - markLeft - CELL / 2) / PITCH, now.get());
    })
    .onFinalize(() => {
      simRelease(sim.get(), now.get());
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawStage(canvas, geo, frame.get());
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>
          <Text
            style={[styles.wordmark, { top: geo.wordTop, width: geo.w - 24 }]}
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
          >
            FOOTNOTE
          </Text>
          <Text
            style={[styles.tagline, { top: geo.tagTop, width: geo.w - 32 }]}
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
          >
            Read between the lines
          </Text>
          <SignInPill label="Continue with passkey" kind={0} top={buttonTop(geo, 0)} g={geo} />
          <SignInPill label="Continue with email" kind={1} top={buttonTop(geo, 1)} g={geo} />
          <SignInPill label="Continue with phone" kind={2} top={buttonTop(geo, 2)} g={geo} />
          <Text style={[styles.footer, { top: geo.footerCY - 10 }]} numberOfLines={1}>
            <Text style={styles.footerOr}>{'or  '}</Text>
            <Text style={styles.footerLink}>browse without an account</Text>
          </Text>
          <GestureDetector gesture={touch}>
            <View
              style={[styles.touch, { top: geo.touchTop, height: geo.touchBottom - geo.touchTop }]}
              accessible
              accessibilityLabel="Footnote logo, an asterisk made of pixels. Drag across it to slice it."
            />
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  wordmark: {
    position: 'absolute',
    left: 12,
    height: 78,
    lineHeight: 78,
    fontSize: 70,
    fontWeight: '900',
    letterSpacing: -1,
    textAlign: 'center',
    color: INK,
  },
  tagline: {
    position: 'absolute',
    left: 16,
    height: 30,
    lineHeight: 30,
    fontSize: 25,
    fontWeight: '400',
    textAlign: 'center',
    color: '#D6D6D6',
  },
  pill: {
    position: 'absolute',
    left: 16,
    height: 54,
    borderRadius: 27,
    backgroundColor: '#1C1C1E',
    borderWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(255,255,255,0.08)',
    borderLeftColor: 'rgba(255,255,255,0.03)',
    borderRightColor: 'rgba(255,255,255,0.03)',
    borderBottomColor: 'rgba(255,255,255,0)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillGlyph: { position: 'absolute', left: 20, top: 17, width: 20, height: 20 },
  pillLabel: { fontSize: 17, fontWeight: '600', color: INK },
  footer: { position: 'absolute', left: 0, right: 0, height: 20, lineHeight: 20, fontSize: 14, textAlign: 'center' },
  footerOr: { color: '#7C7C80' },
  footerLink: { color: INK, fontWeight: '600' },
  touch: { position: 'absolute', left: 0, right: 0 },
});
