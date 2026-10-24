import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { BlurMask, Canvas, Group, Image, LinearGradient, Mask, Rect, Text, useFont, useImage, type SkFont, type SkImage, type SkPath, type Transforms3d } from '@shopify/react-native-skia';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

// Small arrival and sweep primitives adapted from Gleam Line Intro / Thinking Word Shimmer.
// The scene owns the only clock; these accents never schedule or repeat an animation.
function clamp(value: number) {
  'worklet';
  return Math.max(0, Math.min(1, value));
}
function smooth(value: number) {
  'worklet';
  const p = clamp(value);
  return p * p * (3 - 2 * p);
}
function easeOut(value: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(value), 3);
}

export function useBuzzStreaksTextAssets(enabled: boolean) {
  const font = useFont(enabled ? require('./assets/Gilroy-SemiBold.ttf') : null, 19.6);
  const mask = useImage(enabled ? require('./assets/start-label.png') : null);
  return { font, mask };
}

type WordClip = { x: number; y: number; width: number; height: number };
function CaptionWord({ text, font, x, y, clip, index, start, clock, animate }: {
  text: string; font: SkFont; x: number; y: number; clip: WordClip; index: number;
  start: number; clock: SharedValue<number>; animate: boolean;
}) {
  const pose = useDerivedValue(() => {
    const time = clock.get();
    if (!animate && time <= 0) return { alpha: 1, blur: 0, dy: 0 };
    const p = (time - start - index * .035) / .24;
    return { alpha: smooth(p / .6), blur: 3 * (1 - easeOut(p / .8)), dy: 4 * (1 - easeOut(p)) };
  });
  const opacity = useDerivedValue(() => pose.get().alpha);
  const blur = useDerivedValue(() => pose.get().blur);
  const transform = useDerivedValue<Transforms3d>(() => [{ translateY: pose.get().dy }]);
  return <Group clip={clip}><Group transform={transform} opacity={opacity}>
    <Text text={text} font={font} x={x} y={y} color="white"><BlurMask blur={blur} style="normal"/></Text>
  </Group></Group>;
}

/** Clip repeated full-phrase draws so every word keeps the same advances and centered alignment. */
export function BuzzStreaksCaption({ text, start, clock, animate, font }: {
  text: string; start: number; clock: SharedValue<number>; animate: boolean; font: SkFont | null;
}) {
  const run = useMemo(() => {
    if (!font) return null;
    const width = (value: string) => font.getGlyphWidths(font.getGlyphIDs(value)).reduce((a, b) => a + b, 0);
    const left = 180 - width(text) / 2, metrics = font.getMetrics();
    const words = text.split(' ');
    let prefix = '';
    let boundary = left - 6;
    const clips = words.map((word, index) => {
      const end = index === words.length - 1 ? left + width(text) + 6
        : left + width(prefix + word) + width(' ') / 2;
      const clip = { x: boundary, y: 0, width: end - boundary, height: 36 };
      boundary = end;
      prefix += word + (index < words.length - 1 ? ' ' : '');
      return clip;
    });
    return { left, baseline: 18 - (metrics.ascent + metrics.descent) / 2, clips };
  }, [font, text]);
  const settledAt = start + (text.split(' ').length - 1) * .035 + .24;
  const settled = useDerivedValue(() => (!animate && clock.get() <= 0) || clock.get() >= settledAt ? 1 : 0);
  const revealing = useDerivedValue(() => 1 - settled.get());
  const settledStyle = useAnimatedStyle(() => ({ opacity: settled.get() }));
  return <><Canvas style={s.caption} pointerEvents="none">{font && run && <>
    <Group opacity={revealing}>{run.clips.map((clip, index) =>
      <CaptionWord key={index} text={text} font={font} x={run.left} y={run.baseline} clip={clip}
        index={index} start={start} clock={clock} animate={animate}/>)}</Group>
  </>}</Canvas><Animated.Text accessible={false} style={[s.settledCaption, settledStyle]}>{text}</Animated.Text></>;
}

/** A single sweep over a cached vector clip, with the same five stops as the source primitive. */
export function BuzzStreaksShimmer({ clock, start, width, height, peak, clip, blendMode = 'srcOver' }: {
  clock: SharedValue<number>; start: number; width: number; height: number; peak: number;
  clip?: SkPath; blendMode?: 'screen' | 'srcOver';
}) {
  const center = useDerivedValue(() => -.35 + 1.7 * smooth((clock.get() - start) / .7));
  const opacity = useDerivedValue(() => clock.get() > start && clock.get() < start + .7 ? 1 : 0);
  const from = useDerivedValue(() => ({ x: (center.get() - .5) * width, y: height / 2 }));
  const to = useDerivedValue(() => ({ x: (center.get() + .5) * width, y: height / 2 }));
  return <Group clip={clip} opacity={opacity} blendMode={blendMode}><Rect x={0} y={0} width={width} height={height}>
    <LinearGradient start={from} end={to} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0)', `rgba(255,255,255,${peak})`, 'rgba(255,255,255,0)', 'rgba(255,255,255,0)']} positions={[0,.2,.5,.8,1]}/>
  </Rect></Group>;
}

export function BuzzStreaksLabelShimmer({ clock, mask }: { clock: SharedValue<number>; mask: SkImage | null }) {
  const opacity = useDerivedValue(() => clock.get() > 9.45 && clock.get() < 10.15 ? 1 : 0);
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><Group opacity={opacity}><Mask mode="alpha" clip={false} mask={<Image image={mask} x={0} y={0} width={106} height={23} fit="fill"/>}>
    <BuzzStreaksShimmer clock={clock} start={9.45} width={106} height={23} peak={.16}/>
  </Mask></Group></Canvas>;
}

const s = StyleSheet.create({
  // Six points of internal padding preserve the source's y=-6...30 blur clip around a 24pt line.
  caption: { position: 'absolute', left: 0, top: -6, width: 360, height: 36 },
  settledCaption: { position: 'absolute', left: 0, top: 0, width: 360, height: 24,
    fontFamily: 'BuzzSemiBold', fontSize: 19.6, lineHeight: 24, textAlign: 'center', color: 'white' },
});
