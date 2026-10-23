// Individually falling athletic glyphs; the date shares their tilted plane, body stays level.
import { Glyphs, Group, LinearGradient, Shadow, type SkFont } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { View } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { COPY, PALETTE, TIMING, type TrophyFrame } from './trophy-contract';
import { type TrophyFonts } from './trophy-fonts';
import { enter, uiProgress, type TrophyState } from './trophy-timeline';

export type PositionedGlyph = { id: number; pos: { x: number; y: number } };
/** Exact advances, plus deliberate tracking; also used by the offline proof renderer. */
export function centredGlyphs(font: SkFont, text: string, spacing: number): PositionedGlyph[] {
  const ids = font.getGlyphIDs(text); const widths = font.getGlyphWidths(ids);
  let width = Math.max(0, ids.length - 1) * spacing;
  for (let i = 0; i < widths.length; i++) width += widths[i];
  let x = -width / 2;
  const glyphs: PositionedGlyph[] = [];
  for (let i = 0; i < ids.length; i++) {
    glyphs.push({ id: ids[i], pos: { x, y: 0 } }); x += widths[i] + spacing;
  }
  return glyphs;
}
export function capBaseline(font: SkFont, center: number): number {
  const bounds = font.measureText('H');
  return center - bounds.y - bounds.height / 2;
}
function Letter({ glyph, index, baseline, frame, font, state }: {
  glyph: PositionedGlyph; index: number; baseline: number; frame: TrophyFrame; font: SkFont; state: SharedValue<TrophyState>;
}) {
  const glyphs = useMemo(() => [glyph], [glyph]);
  const opacity = useDerivedValue(() => {
    const s = state.get();
    if (s.reduced) return s.ui;
    const age = (s.titleTime - TIMING.title.start - index * TIMING.title.stagger) / TIMING.title.letter;
    return enter(age / 0.45) * s.ui;
  });
  const transform = useDerivedValue(() => {
    const s = state.get();
    const p = enter((s.titleTime - TIMING.title.start - index * TIMING.title.stagger) / TIMING.title.letter);
    return [{ translateY: s.reduced ? 0 : -26 * frame.k * (1 - p) + 8 * (1 - s.ui) }];
  });
  return <Group opacity={opacity} transform={transform}>
    <Glyphs glyphs={glyphs} x={frame.titleX} y={baseline} font={font}>
      <LinearGradient start={{ x: 0, y: baseline - frame.titleSize * 0.72 }} end={{ x: 0, y: baseline }} colors={['#FFFFFF', '#DCE6FF']} />
      <Shadow dx={0} dy={6} blur={12} color="rgba(0, 5, 35, 0.45)" />
    </Glyphs>
  </Group>;
}
const TITLE_LANDED = TIMING.title.start + 11 * TIMING.title.stagger + TIMING.title.letter;
function HeadlineGlint({ glyphs, font, frame, state }: {
  glyphs: PositionedGlyph[]; font: SkFont; frame: TrophyFrame; state: SharedValue<TrophyState>;
}) {
  const alpha = useDerivedValue(() => {
    const s = state.get();
    if (s.reduced || s.titleTime < TITLE_LANDED) return 0;
    if (s.titleTime < TITLE_LANDED + 0.52) return 0.65 * s.ui;
    return s.flashAge < 0.52 ? 0.34 * s.ui : 0;
  });
  const start = useDerivedValue(() => {
    const s = state.get();
    const age = s.titleTime < TITLE_LANDED + 0.52 ? s.titleTime - TITLE_LANDED : s.flashAge;
    return { x: frame.titleX - frame.titleSize * 2.8 + age / 0.52 * frame.titleSize * 5.6,
      y: frame.titleY - frame.titleSize };
  });
  const end = useDerivedValue(() => ({ x: start.get().x + 45 * frame.k, y: frame.titleY + frame.titleSize }));
  return <Group opacity={alpha}>
    <Glyphs glyphs={glyphs} x={frame.titleX} y={frame.titleY - 7 * frame.k} font={font}>
      <LinearGradient start={start} end={end}
        colors={['#FFFFFF00', '#BCDCFF66', '#FFFFFF', '#BCDCFF66', '#FFFFFF00']} positions={[0, 0.3, 0.5, 0.7, 1]} />
    </Glyphs>
  </Group>;
}
export function TrophyUi({ frame, fonts, state }: { frame: TrophyFrame; fonts: TrophyFonts; state: SharedValue<TrophyState> }) {
  const lines = useMemo(() => COPY.titleLines.map((text) => centredGlyphs(fonts.title, text, 0.5)), [fonts.title]);
  const glintGlyphs = useMemo(() => {
    const result: PositionedGlyph[] = [];
    for (let line = 0; line < lines.length; line++) {
      for (let i = 0; i < lines[line].length; i++) {
        const glyph = lines[line][i];
        result.push({ id: glyph.id, pos: { x: glyph.pos.x, y: line * frame.titleLine } });
      }
    }
    return result;
  }, [frame.titleLine, lines]);
  const dateGlyphs = useMemo(() => centredGlyphs(fonts.date, COPY.date, 0), [fonts.date]);
  const hintGlyphs = useMemo(() => centredGlyphs(fonts.hint, COPY.hint, 3), [fonts.hint]);
  const dateAlpha = useDerivedValue(() => uiProgress(state.get(), TIMING.date));
  const dateTransform = useDerivedValue(() => {
    const s = state.get();
    return [{ translateY: s.reduced ? 0 : 8 * (1 - uiProgress(s, TIMING.date)) }];
  });
  const hintAlpha = useDerivedValue(() => state.get().phase === 'closed' ? state.get().hint : 0);
  return <>
    <Group origin={{ x: frame.titleX, y: frame.titleY }} transform={[{ rotate: frame.titleTilt }]}>
      {lines.map((glyphs, line) => glyphs.map((glyph, i) => <Letter key={`${line}-${i}`} glyph={glyph}
        index={line === 0 ? i : lines[0].length + i} baseline={frame.titleY - 7 * frame.k + line * frame.titleLine}
        frame={frame} font={fonts.title} state={state} />))}
      <HeadlineGlint glyphs={glintGlyphs} font={fonts.title} frame={frame} state={state} />
      <Group opacity={dateAlpha} transform={dateTransform}>
        <Glyphs glyphs={dateGlyphs} x={frame.titleX} y={capBaseline(fonts.date, frame.dateY)} font={fonts.date} color="#E5EBFF" />
      </Group>
    </Group>
    <Group opacity={hintAlpha}>
      <Glyphs glyphs={hintGlyphs} x={frame.width / 2} y={frame.share.y + frame.share.height / 2} font={fonts.hint} color={PALETTE.inkMute} />
    </Group>
  </>;
}
function BodyCopy({ frame, state }: { frame: TrophyFrame; state: SharedValue<TrophyState> }) {
  const style = useAnimatedStyle(() => {
    const s = state.get(); const p = uiProgress(s, TIMING.body);
    return { opacity: p, transform: [{ translateY: s.reduced ? 0 : 8 * (1 - p) }] };
  });
  return <Animated.Text numberOfLines={2} adjustsFontSizeToFit minimumFontScale={0.8}
    style={[{ position: 'absolute', left: 24, right: 24, top: frame.bodyTop,
      fontSize: frame.bodySize, lineHeight: frame.bodyLine, textAlign: 'center', color: PALETTE.inkSoft }, style]}>
    {COPY.body.join('\n')}
  </Animated.Text>;
}
export function TrophyCopy({ frame, state, accessible }: { frame: TrophyFrame; state: SharedValue<TrophyState>; accessible: boolean }) {
  return <View pointerEvents="none" style={{ position: 'absolute', inset: 0 }} accessibilityElementsHidden={!accessible}
    importantForAccessibility={accessible ? 'auto' : 'no-hide-descendants'}>
    <View accessible accessibilityRole="header" accessibilityLabel="Perfect Month. Won on Oct 13, 2026"
      style={{ position: 'absolute', left: 32, right: 32, top: frame.titleY - frame.titleSize, height: frame.titleSize * 2.2 }} />
    <BodyCopy frame={frame} state={state} />
  </View>;
}
