import {
  BlurMask,
  Canvas,
  ColorMatrix,
  DashPathEffect,
  FractalNoise,
  Group,
  Image,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  type SkFont,
  type SkImage,
  type SkPath,
  Text as SkiaText,
  vec,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { FadeIn, FadeInDown, FadeOut, type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { FOIL_SHADER, makeBarcode, makeSunflower, makeTicketShape } from './scratchTicketArt';

/** A ticket's prize: the photo under the foil and the line printed on the stub. */
export type Ticket = { photo: string; prize: string; detail: string };

/** Where things sit, in points. `ticket` is on screen; `scratch` and the rest are inside the ticket. */
export type Geometry = {
  width: number;
  height: number;
  ticket: { x: number; y: number; w: number; h: number };
  scratch: { x: number; y: number; w: number; h: number };
  perfY: number;
  buttonY: number;
};

/** The state of one ticket on the table. Two of these take turns: the one in play and the next. */
export type ScratchSlot = {
  /** Finger path inside the scratch area: x, y pairs, strokes split by a PEN_UP pair. */
  points: SharedValue<number[]>;
  /** Which cells of the coverage grid the finger has cleared (0/1), and how many. */
  grid: SharedValue<number[]>;
  cleared: SharedValue<number>;
  revealed: SharedValue<boolean>;
  /** 0 → 1 as the rest of the foil dissolves. */
  dissolve: SharedValue<number>;
  /** A short bump on reveal, 0 at rest. */
  pop: SharedValue<number>;
  /** Horizontal travel (pt) and opacity while tickets change places. */
  offset: SharedValue<number>;
  shown: SharedValue<number>;
};

export const PEN_UP = -1e6;
export const BRUSH = 46;

export const PAPER = '#f4ecd9';
const INK = '#1f2a22';
const MUTED = '#857c69';
const MARIGOLD = '#eea41c';
const SEED_BROWN = '#5a3a1c';
const RADIUS = 20;
const SCRATCH_RADIUS = 14;
// The canvas reaches this far past the ticket on every side, so its shadow is not cut off.
const BLEED = 40;
// The photo sits this much zoomed in under the foil and settles to 1 as the foil dissolves.
const PHOTO_ZOOM = 0.1;
const BAR_W = 92;
const BAR_H = 28;
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const CONDENSED = process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed';

// Paper grain: noise turned to a grey that only reaches GRAIN below white, for a multiply blend
// (a layer's opacity does not soften a blend mode, so the strength is in the matrix).
const GRAIN = 0.14;
const g = GRAIN / 3;
const GRAIN_MATRIX = [g, g, g, 0, 1 - GRAIN, g, g, g, 0, 1 - GRAIN, g, g, g, 0, 1 - GRAIN, 0, 0, 0, 0, 1];

/** The finger's strokes as one smooth path: each point is the control of a curve through midpoints. */
function strokePath(points: number[]): SkPath {
  'worklet';
  const path = Skia.PathBuilder.Make();
  let open = false;
  let px = 0;
  let py = 0;
  for (let i = 0; i < points.length; i += 2) {
    const x = points[i];
    const y = points[i + 1];
    if (x === PEN_UP) {
      open = false;
      continue;
    }
    if (!open) {
      path.moveTo(x, y);
      // A dot even if the finger never moves.
      path.lineTo(x + 0.1, y);
      open = true;
    } else {
      path.quadTo(px, py, (px + x) / 2, (py + y) / 2);
    }
    px = x;
    py = y;
  }
  return path.build();
}

type CardProps = {
  ticket: Ticket;
  image: SkImage | null;
  slot: ScratchSlot;
  geo: Geometry;
  phase: SharedValue<number>;
  tilt: SharedValue<number[]>;
  font: SkFont;
  revealed: boolean;
  reducedMotion: boolean;
};

/**
 * One lottery ticket: cream card stock with a tear-off stub, a holographic foil window over the
 * prize photo, and the printed type laid over the canvas. Its foil, scratches and motion come from
 * its slot, so the parent can drive it with a finger or the autoPlay script alike.
 */
export function ScratchTicketCard({ ticket, image, slot, geo, phase, tilt, font, revealed, reducedMotion }: CardProps) {
  const { w, h } = geo.ticket;
  const S = geo.scratch;
  const shape = useMemo(() => makeTicketShape(w, h, geo.perfY, RADIUS), [w, h, geo.perfY]);
  const barcode = useMemo(() => makeBarcode(BAR_W, BAR_H, 11), []);
  const mark = useMemo(() => makeSunflower(11), []);
  const emboss = useMemo(() => makeSunflower(24), []);

  const uniforms = useDerivedValue(() => ({
    size: [S.w, S.h],
    time: phase.get(),
    tilt: tilt.get(),
    dissolve: slot.dissolve.get(),
  }));
  const scratches = useDerivedValue(() => strokePath(slot.points.get()));
  const embossOpacity = useDerivedValue(() => Math.max(0, 1 - slot.dissolve.get() * 3));
  const photoTransform = useDerivedValue(() => [{ scale: 1 + (reducedMotion ? 0 : PHOTO_ZOOM * (1 - slot.dissolve.get())) }]);

  const style = useAnimatedStyle(() => ({
    opacity: slot.shown.get(),
    transform: [
      { translateX: slot.offset.get() },
      { rotate: `${(slot.offset.get() / geo.width) * 9}deg` },
      { scale: 1 + slot.pop.get() * 0.035 },
    ],
  }));

  const label = 'SCRATCH  TO  REVEAL';
  const labelWidth = font.measureText(label).width;

  return (
    <Animated.View style={[styles.card, { left: geo.ticket.x, top: geo.ticket.y, width: w, height: h }, style]} pointerEvents="none">
      <Canvas style={{ position: 'absolute', left: -BLEED, top: -BLEED, width: w + BLEED * 2, height: h + BLEED * 2 }}>
        <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
          {/* Card stock with a long soft shadow and a tight contact one. */}
          <Path path={shape} color={PAPER}>
            <Shadow dx={0} dy={18} blur={24} color="rgba(0,0,0,0.42)" />
            <Shadow dx={0} dy={2} blur={3} color="rgba(0,0,0,0.25)" />
          </Path>
          <Group clip={shape}>
            <Rect x={0} y={0} width={w} height={h} blendMode="multiply">
              <FractalNoise freqX={0.9} freqY={0.9} octaves={2} seed={3} />
              <ColorMatrix matrix={GRAIN_MATRIX} />
            </Rect>
            <Rect x={0} y={0} width={w} height={h}>
              <RadialGradient c={vec(w * 0.3, 0)} r={h} colors={['rgba(255,255,255,0.35)', 'rgba(255,255,255,0)', 'rgba(120,96,50,0.10)']} positions={[0, 0.6, 1]} />
            </Rect>
          </Group>
          {/* A printed frame round the face and round the stub. */}
          <RoundedRect x={10} y={10} width={w - 20} height={geo.perfY - 26} r={12} style="stroke" strokeWidth={1} color="rgba(31,42,34,0.22)">
            <DashPathEffect intervals={[3, 3]} />
          </RoundedRect>
          <RoundedRect x={10} y={geo.perfY + 16} width={w - 20} height={h - geo.perfY - 26} r={10} style="stroke" strokeWidth={1} color="rgba(31,42,34,0.22)">
            <DashPathEffect intervals={[3, 3]} />
          </RoundedRect>
          {/* The sunflower mark in the header. */}
          <Group transform={[{ translateX: w - 34 }, { translateY: 27 }]}>
            <Path path={mark.petals} color={MARIGOLD} />
            <Path path={mark.head} color={SEED_BROWN} />
          </Group>

          {/* The scratch window: the prize photo, and the foil over it. */}
          <Group transform={[{ translateX: S.x }, { translateY: S.y }]}>
            <Group clip={{ rect: { x: 0, y: 0, width: S.w, height: S.h }, rx: SCRATCH_RADIUS, ry: SCRATCH_RADIUS }}>
              <Rect x={0} y={0} width={S.w} height={S.h} color="#3b4a2c" />
              {image && (
                <Group transform={photoTransform} origin={vec(S.w / 2, S.h / 2)}>
                  <Image image={image} x={0} y={0} width={S.w} height={S.h} fit="cover" />
                </Group>
              )}
              <Rect x={0} y={0} width={S.w} height={S.h}>
                <RadialGradient c={vec(S.w / 2, S.h / 2)} r={S.w * 0.75} colors={['rgba(0,0,0,0)', 'rgba(20,14,4,0.32)']} />
              </Rect>
              <Group layer>
                <Rect x={0} y={0} width={S.w} height={S.h}>
                  <Shader source={FOIL_SHADER} uniforms={uniforms} />
                </Rect>
                <Group opacity={embossOpacity}>
                  {/* Pressed into the foil: a sunflower and the instruction, light catching their upper edge. */}
                  <Group transform={[{ translateX: S.w / 2 - 1 }, { translateY: S.h / 2 - 17 }]}>
                    <Path path={emboss.petals} color="rgba(255,255,255,0.45)" />
                  </Group>
                  <Group transform={[{ translateX: S.w / 2 }, { translateY: S.h / 2 - 16 }]}>
                    <Path path={emboss.petals} color="rgba(58,62,74,0.42)" />
                    <Path path={emboss.head} color="rgba(58,62,74,0.5)" />
                    <Path path={emboss.seeds} color="rgba(255,255,255,0.35)" />
                  </Group>
                  <SkiaText x={(S.w - labelWidth) / 2 - 0.8} y={S.h / 2 + 37} text={label} font={font} color="rgba(255,255,255,0.55)" />
                  <SkiaText x={(S.w - labelWidth) / 2} y={S.h / 2 + 38} text={label} font={font} color="rgba(48,52,64,0.7)" />
                </Group>
                {/* The finger rubs the foil away with a soft edge. */}
                <Path path={scratches} style="stroke" strokeWidth={BRUSH} strokeCap="round" strokeJoin="round" color="black" blendMode="dstOut">
                  <BlurMask blur={5} style="normal" />
                </Path>
              </Group>
            </Group>
            <RoundedRect x={0.5} y={0.5} width={S.w - 1} height={S.h - 1} r={SCRATCH_RADIUS} style="stroke" strokeWidth={1} color="rgba(31,42,34,0.28)" />
          </Group>

          <Group transform={[{ translateX: w - 22 - BAR_W }, { translateY: geo.perfY + 30 }]}>
            <Path path={barcode} color={INK} />
          </Group>
        </Group>
      </Canvas>

      <View style={styles.header}>
        <Text style={styles.caps}>FIELD DAY LOTTERY</Text>
        <Text style={[styles.caps, { marginRight: 28 }]}>Nº 07</Text>
      </View>
      <Text style={styles.title}>Lucky Bloom</Text>
      <Text style={styles.subtitle}>Scratch the foil. Every ticket grows something.</Text>

      <View style={[styles.stub, { top: geo.perfY + 26 }]}>
        <Text style={styles.caps}>YOUR PRIZE</Text>
        <View style={styles.prizeBox}>
          {revealed ? (
            <Animated.View key="prize" entering={reducedMotion ? FadeIn.duration(300) : FadeInDown.springify().damping(14).mass(0.7)}>
              <Text style={styles.prize} numberOfLines={1}>
                {ticket.prize}
              </Text>
              <Text style={styles.detail} numberOfLines={1}>
                {ticket.detail}
              </Text>
            </Animated.View>
          ) : (
            <Animated.View key="hidden" exiting={FadeOut.duration(120)}>
              <Text style={[styles.prize, { color: 'rgba(31,42,34,0.28)' }]}>Hidden under the foil</Text>
              <Text style={styles.detail}>Every ticket wins</Text>
            </Animated.View>
          )}
        </View>
      </View>
      <Text style={[styles.serial, { top: geo.perfY + 30 + BAR_H + 6, right: 22, width: BAR_W }]}>07 · 0427 1186</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute',
  },
  header: {
    position: 'absolute',
    top: 22,
    left: 22,
    right: 22,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  caps: {
    fontFamily: FAMILY,
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.8,
    color: MUTED,
  },
  title: {
    position: 'absolute',
    top: 38,
    left: 21,
    fontFamily: CONDENSED,
    fontSize: 42,
    lineHeight: 46,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: INK,
  },
  subtitle: {
    position: 'absolute',
    top: 86,
    left: 22,
    right: 22,
    fontFamily: FAMILY,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '500',
    color: MUTED,
  },
  stub: {
    position: 'absolute',
    left: 24,
    right: 130,
  },
  prizeBox: {
    marginTop: 5,
    height: 40,
  },
  prize: {
    fontFamily: FAMILY,
    fontSize: 17,
    lineHeight: 21,
    fontWeight: '700',
    letterSpacing: -0.2,
    color: INK,
  },
  detail: {
    fontFamily: FAMILY,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    color: MUTED,
  },
  serial: {
    position: 'absolute',
    textAlign: 'center',
    fontFamily: process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 8.5,
    letterSpacing: 0.6,
    color: INK,
  },
});
