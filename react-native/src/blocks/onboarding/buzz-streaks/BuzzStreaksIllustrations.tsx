import { useMemo } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { Canvas, FillType, Group, LinearGradient, Oval, Path, Skia } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { BuzzStreaksShimmer } from './BuzzStreaksTextMotion';

type Gradient = { start: number[]; end: number[]; colors: number[][] };
type Layer = { paths: number[][]; curves: number[][]; fillRGB?: number[]; gradient?: Gradient };
type BonusFrame = { flame: number[][]; white: number[][]; visible: boolean };
type Illustrations = {
  prize: { size: number[]; layers: Layer[] };
  bonus: { coin: number[]; colors: Record<string, number[]>; frames: BonusFrame[]; glyphPaths: number[][]; glyphCurves: number[][] };
};
const ILLUSTRATIONS: Illustrations = require('./assets/illustrations.json');

function color(rgb: number[], alpha = 1) {
  return new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, alpha]);
}

function cubicPath(contours: number[][]) {
  const path = Skia.PathBuilder.Make();
  for (const points of contours) {
    path.moveTo(points[0], points[1]);
    for (let i = 2; i < points.length; i += 6) {
      path.cubicTo(points[i], points[i + 1], points[i + 2], points[i + 3], points[i + 4], points[i + 5]);
    }
    path.close();
  }
  return path.setFillType(FillType.EvenOdd).build();
}

function softPath(points: number[][]) {
  const path = Skia.PathBuilder.Make();
  if (points.length <= 2) return path.build();
  const first = points[0], last = points[points.length - 1];
  path.moveTo((last[0] + first[0]) / 2, (last[1] + first[1]) / 2);
  for (let i = 0; i < points.length; i++) {
    const a = points[i], b = points[(i + 1) % points.length];
    path.quadTo(a[0], a[1], (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
  }
  return path.close().build();
}

/** The source's five vector layers retain their full 190 × 99 padded coordinate space. */
export function BuzzStreaksPrize({ style, clock, enhanced = false }: { style?: StyleProp<ViewStyle>; clock: SharedValue<number>; enhanced?: boolean }) {
  const layers = useMemo(() => ILLUSTRATIONS.prize.layers.map(layer => ({ ...layer, path: cubicPath(layer.curves) })), []);
  return <Canvas pointerEvents="none" style={[s.prize, style]}>{layers.map((layer, i) => <Path key={i} path={layer.path} fillType="evenOdd" color={layer.fillRGB ? color(layer.fillRGB) : undefined}>
    {layer.gradient && <LinearGradient start={{ x: layer.gradient.start[0], y: layer.gradient.start[1] }}
      end={{ x: layer.gradient.end[0], y: layer.gradient.end[1] }} colors={layer.gradient.colors.map(rgb => color(rgb))}/>}
  </Path>)}{enhanced && <BuzzStreaksShimmer clock={clock} start={.45} width={190} height={99} peak={.42} clip={layers[3].path} blendMode="screen"/>}</Canvas>;
}

/** Measured flame acting stays independent of the stable coin and source-traced glyph. */
export function BuzzStreaksBonus({ clock }: { clock: SharedValue<number> }) {
  const { coin, colors, glyphCurves } = ILLUSTRATIONS.bonus;
  const glyph = useMemo(() => cubicPath(glyphCurves), [glyphCurves]);
  const frames = useMemo(() => {
    const empty = Skia.Path.Make();
    return ILLUSTRATIONS.bonus.frames.map(frame => ({ visible: frame.visible,
      flame: frame.flame.length > 2 ? softPath(frame.flame) : empty,
      white: frame.white.length > 2 ? softPath(frame.white) : empty }));
  }, []);
  const frame = useDerivedValue(() => Math.max(0, Math.min(frames.length - 1, Math.floor(clock.get() * 60 + 1e-7))));
  const flame = useDerivedValue(() => frames[frame.get()].flame);
  const white = useDerivedValue(() => frames[frame.get()].white);
  const opacity = useDerivedValue(() => frames[frame.get()].visible ? 1 : 0);
  return <View pointerEvents="none" style={s.bonus}>
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Group opacity={opacity}>
      <Oval x={67} y={90} width={76} height={86} color={color(colors.halo, .18)}/>
      <Path path={flame}><LinearGradient start={{ x: 110, y: 75 }} end={{ x: 125, y: 158 }} colors={[color(colors.flameTop), color(colors.flameBottom)]}/></Path>
      <Path path={white} color={color(colors.white)}/>
      <Group transform={[{ translateX: coin[0] }, { translateY: coin[1] }, { rotate: coin[4] * Math.PI / 180 }]}>
        <Oval x={-coin[2]} y={-coin[3]} width={2 * coin[2]} height={2 * coin[3]}><LinearGradient start={{ x: 0, y: -coin[3] }} end={{ x: 0, y: coin[3] }} colors={[color(colors.coinTop), color(colors.coinBottom)]}/></Oval>
        <Oval x={-coin[2]} y={-coin[3]} width={2 * coin[2]} height={2 * coin[3]} style="stroke" strokeWidth={.6} color={color(colors.coinTop, .65)}/>
      </Group>
      <Path path={glyph} fillType="evenOdd" color={color(colors.glyph)}/>
    </Group></Canvas>
  </View>;
}

const s = StyleSheet.create({
  prize: { width: 190, height: 99 },
  bonus: { position: 'absolute', left: 0, top: 0, width: 157, height: 158 },
});
