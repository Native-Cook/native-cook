// Glyph runs are measured once. UI frames only change transforms, visibility and fixed digit slots.
import { BlurMask, Circle, Glyphs, Group, type SkFont } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useAnimatedReaction, useDerivedValue, useSharedValue } from 'react-native-reanimated';
import { M, captionOpacity } from './supercharge-constants';
import { CAPTION, PRINT, cardTransform } from './supercharge-layout';
import type { SuperchargeValues } from './supercharge-types';
export type PrintFonts = { title: SkFont; body: SkFont; mono: SkFont; caption: SkFont };
const CAPTIONS = ['HOLD TO CHARGE', 'RELEASE', 'TAP TO DRAIN'];
export function glyphRun(font: SkFont, text: string, tracking = 0) {
  const ids = font.getGlyphIDs(text), widths = font.getGlyphWidths(ids);
  let width = 0;
  const glyphs = [];
  for (let i = 0; i < ids.length; i++) { glyphs.push({ id: ids[i], pos: { x: width, y: 0 } }); width += widths[i] + (i < ids.length - 1 ? tracking : 0); }
  return { glyphs, width };
}
function Run({ font, text, x, y, tracking = 0, color = '#17181A' }: { font: SkFont; text: string; x: number; y: number; tracking?: number; color?: string }) {
  const run = useMemo(() => glyphRun(font, text, tracking), [font, text, tracking]);
  return <Glyphs glyphs={run.glyphs} font={font} x={x} y={y} color={color} />;
}
function Digit({ values, font, slot, x }: { values: SuperchargeValues; font: SkFont; slot: number; x: number }) {
  const { meter } = values;
  const all = useMemo(() => {
    const runs = [];
    for (let i = 0; i < 10; i++) runs.push(glyphRun(font, String(i)).glyphs);
    return runs;
  }, [font]);
  const glyphs = useDerivedValue(() => all[Math.floor(Math.round(meter.get() * 100) / 10 ** slot) % 10]);
  return <Glyphs font={font} glyphs={glyphs} x={x} y={PRINT.readout.baseline} color="#17181A" />;
}
function Dot({ values, index }: { values: SuperchargeValues; index: number }) {
  const { meter, clock } = values;
  const litAt = useSharedValue(-1);
  useAnimatedReaction(() => meter.get() + 1e-9 >= (index + 1) / PRINT.dots.count, (on, wasOn) => {
    if (on !== wasOn) litAt.set(on ? clock.get() : -1);
  });
  const lit = useDerivedValue(() => litAt.get() < 0 ? 0 : 1);
  const pop = useDerivedValue(() => litAt.get() < 0 ? 0 : Math.min(1, Math.max(0, (clock.get() - litAt.get()) / M.print.dotPop)));
  const radius = useDerivedValue(() => PRINT.dots.radius * (0.6 + 0.4 * pop.get()));
  const x = PRINT.dots.x + index * PRINT.dots.gap;
  return <><Circle cx={x} cy={PRINT.dots.y} r={PRINT.dots.radius} color="#17181A" opacity={0.26} />
    <Group opacity={lit}><Circle cx={x} cy={PRINT.dots.y} r={radius} color="#C79A00"><BlurMask blur={1.5} style="normal" /></Circle>
      <Circle cx={x} cy={PRINT.dots.y} r={radius} color="#C79A00" /></Group></>;
}
function Caption({ values, fonts, index }: { values: SuperchargeValues; fonts: PrintFonts; index: number }) {
  const { caption, clock } = values;
  const run = useMemo(() => glyphRun(fonts.caption, CAPTIONS[index], CAPTION.tracking), [fonts.caption, index]);
  const transition = useSharedValue({ from: 0, to: 0, at: 0 });
  useAnimatedReaction(() => caption.get(), (to, previous) => {
    transition.set({ from: previous ?? to, to, at: clock.get() });
  });
  const visibility = useDerivedValue(() => {
    const t = transition.get();
    return captionOpacity(t.from, t.to, clock.get() - t.at, index);
  });
  const alpha = useDerivedValue(() => visibility.get() * (index === 1 ? 0.85 + 0.15 * Math.sin(clock.get() * 2 * Math.PI * M.print.releaseHz) : 0.46));
  const transform = useDerivedValue(() => [{ translateY: (1 - visibility.get()) * M.print.captionRise }]);
  return <Group opacity={alpha} transform={transform}><Glyphs glyphs={run.glyphs} font={fonts.caption} x={CAPTION.x - run.width / 2} y={CAPTION.baseline} color={index === 1 ? '#FFF27A' : '#FFFFFF'} /></Group>;
}
export function SuperchargePrint({ values, fonts }: { values: SuperchargeValues; fonts: PrintFonts }) {
  const { card, charged } = values;
  const transform = useDerivedValue(() => cardTransform(card.get()));
  const resting = useDerivedValue(() => Math.max(0, 1 - 2 * charged.get()));
  const incoming = useDerivedValue(() => Math.max(0, 2 * charged.get() - 1));
  const title = useMemo(() => glyphRun(fonts.title, 'Supercharge', PRINT.title.tracking), [fonts.title]);
  const advance = title.width + PRINT.title.tracking;
  const monoAdvance = useMemo(() => fonts.mono.getGlyphWidths(fonts.mono.getGlyphIDs('0'))[0] + PRINT.readout.tracking, [fonts.mono]);
  const stamp = useDerivedValue(() => {
    const c = charged.get();
    const spring = c >= 1 ? 0 : Math.exp(-M.print.stampDamping * c) * Math.cos(M.print.stampFrequency * c);
    return [{ translateX: PRINT.title.x + advance },
      { translateY: PRINT.title.baseline - M.print.stampDrop * spring },
      { scale: 1 + M.print.stampScale * spring }];
  });
  const bodyRest = useDerivedValue(() => [{ translateY: -M.print.bodyRise * charged.get() }]);
  const bodyCharged = useDerivedValue(() => [{ translateY: M.print.bodyRise * (1 - charged.get()) }]);
  return <>
    <Group transform={transform}>
      <Group opacity={0.56}>
        <Run font={fonts.mono} text="CHG " x={PRINT.readout.x} y={PRINT.readout.baseline} tracking={PRINT.readout.tracking} />
        <Group opacity={resting} transform={bodyRest}>
          {[2, 1, 0].map((slot, i) => <Digit key={slot} values={values} font={fonts.mono} slot={slot} x={PRINT.readout.x + (4 + i) * monoAdvance} />)}
          <Run font={fonts.mono} text="%" x={PRINT.readout.x + 7 * monoAdvance} y={PRINT.readout.baseline} />
        </Group>
        <Group opacity={incoming} transform={bodyCharged}><Run font={fonts.mono} text="MAX" x={PRINT.readout.x + 4 * monoAdvance} y={PRINT.readout.baseline} tracking={PRINT.readout.tracking} /></Group>
      </Group>
      {Array.from({ length: PRINT.dots.count }, (_, i) => <Dot key={i} values={values} index={i} />)}
      <Glyphs font={fonts.title} glyphs={title.glyphs} x={PRINT.title.x} y={PRINT.title.baseline} color="#17181A" />
      <Group opacity={charged} transform={stamp}><Run font={fonts.title} text="d" x={0} y={0} /></Group>
      <Group opacity={resting} transform={bodyRest}>
        <Run font={fonts.body} text="Press and hold the card" x={PRINT.body.x} y={PRINT.body.baseline} color="#17181A8F" />
        <Run font={fonts.body} text="to build up a charge" x={PRINT.body.x} y={PRINT.body.baseline + PRINT.body.line} color="#17181A8F" />
      </Group>
      <Group opacity={incoming} transform={bodyCharged}>
        <Run font={fonts.body} text="Full power, held in glass." x={PRINT.body.x} y={PRINT.body.baseline} color="#17181A8F" />
        <Run font={fonts.body} text="Tap the card to let it go." x={PRINT.body.x} y={PRINT.body.baseline + PRINT.body.line} color="#17181A8F" />
      </Group>
    </Group>
    {[0, 1, 2].map((index) => <Caption key={index} index={index} values={values} fonts={fonts} />)}
  </>;
}
