import { useEffect, useMemo, useRef } from 'react';
import { PixelRatio } from 'react-native';
import { BlurMask, Canvas, Fill, Glyphs, Group, LinearGradient, Rect, Shader, Skia, Text, useFont } from '@shopify/react-native-skia';
import type { SkFont } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import type { SharedValue } from 'react-native-reanimated';
import { CAPTION, PERIODS, PILL, STATUS, STATUS_TEXT } from './peak-contract';
import type { Frame, PeakCanvasProps } from './peak-contract';
import { PEAK_SKSL, peakUniforms } from './peak-shader';
import { labelRoll, numberLayout, peakMatrix, softRange } from './peak-type';
import type { NumberMetrics } from './peak-type';

/** Compiles once and gives shader failures a useful module-load error. */
function compileMaterial() {
  const compiled = Skia.RuntimeEffect.Make(PEAK_SKSL);
  if (!compiled) throw new Error('Glass Peak Chart: the glass material shader failed to compile.');
  return compiled;
}
const effect = compileMaterial();
const ink = ['#F6FFF8', '#CFFFD9'];
const numberTop = { x: 201, y: 521 }, numberBottom = { x: 201, y: 576 };

/** Reads real font advances once, keeping measurement out of animation worklets. */
function advance(font: SkFont, text: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i];
  return total;
}

/** Draws one tabular slot; leading slots fade independently of the stable trailing digits. */
function NumberSlot({ frame, font, metrics, slot }: { frame: SharedValue<Frame>; font: SkFont; metrics: NumberMetrics; slot: number }) {
  const glyphs = useDerivedValue(() => numberLayout(frame.get().value, metrics).glyphs[slot]);
  const alpha = useDerivedValue(() => numberLayout(frame.get().value, metrics).alpha[slot]);
  return <Group opacity={alpha}>
    <Glyphs font={font} glyphs={glyphs}><LinearGradient start={numberTop} end={numberBottom} colors={ink} /></Glyphs>
  </Group>;
}

/** Draws all type in the same tilted plane and from the same Frame as the glass. */
function PeakType({ frame, regular, label, caption, bold }: { frame: SharedValue<Frame>; regular: SkFont; label: SkFont; caption: SkFont; bold: SkFont }) {
  const metrics = useMemo(() => {
    const ids = bold.getGlyphIDs('0123456789%');
    const widths = bold.getGlyphWidths(ids);
    let widest = 0;
    for (let i = 0; i < 10; i++) widest = Math.max(widest, widths[i]);
    return { ids, widths, cell: widest - 2 };
  }, [bold]);
  const widths = useMemo(() => PERIODS.map(p => advance(regular, p.sub)), [regular]);
  const captionX = 201 - advance(caption, CAPTION) / 2;
  const presented = useDerivedValue(() => frame.get().presented);
  const oldStatus = useDerivedValue(() => STATUS_TEXT[frame.get().statusPrev]);
  const newStatus = useDerivedValue(() => STATUS_TEXT[frame.get().status]);
  const oldStatusA = useDerivedValue(() => labelRoll(frame.get().statusMix, 6).oldAlpha);
  const newStatusA = useDerivedValue(() => labelRoll(frame.get().statusMix, 6).newAlpha);
  const oldStatusY = useDerivedValue(() => STATUS.baseline + labelRoll(frame.get().statusMix, 6).oldY);
  const newStatusY = useDerivedValue(() => STATUS.baseline + labelRoll(frame.get().statusMix, 6).newY);
  const refreshTransform = useDerivedValue(() => [{ scale: 1 - .03 * frame.get().refreshPress }]);
  const pillTransform = useDerivedValue(() => [{ scale: 1 - .03 * frame.get().pillPress }]);
  const oldPeriod = useDerivedValue(() => PERIODS[frame.get().periodPrev].label);
  const newPeriod = useDerivedValue(() => PERIODS[frame.get().period].label);
  const oldPeriodA = useDerivedValue(() => labelRoll(frame.get().periodMix, 8).oldAlpha);
  const newPeriodA = useDerivedValue(() => labelRoll(frame.get().periodMix, 8).newAlpha);
  const oldPeriodY = useDerivedValue(() => PILL.baseline + labelRoll(frame.get().periodMix, 8).oldY);
  const newPeriodY = useDerivedValue(() => PILL.baseline + labelRoll(frame.get().periodMix, 8).newY);
  const oldSub = useDerivedValue(() => PERIODS[frame.get().periodPrev].sub);
  const newSub = useDerivedValue(() => PERIODS[frame.get().period].sub);
  const oldSubX = useDerivedValue(() => 201 - widths[frame.get().periodPrev] / 2);
  const newSubX = useDerivedValue(() => 201 - widths[frame.get().period] / 2);
  const oldSubA = useDerivedValue(() => 1 - softRange(frame.get().periodMix, 0, .5));
  const newSubA = useDerivedValue(() => softRange(frame.get().periodMix, .5, 1));
  const shadowSub = useDerivedValue(() => PERIODS[frame.get().periodMix < .5 ? frame.get().periodPrev : frame.get().period].sub);
  const shadowSubX = useDerivedValue(() => 201 - widths[frame.get().periodMix < .5 ? frame.get().periodPrev : frame.get().period] / 2);
  const shadowSubA = useDerivedValue(() => 1 - softRange(frame.get().periodMix, 0, .5) + softRange(frame.get().periodMix, .5, 1));
  const numberAlpha = useDerivedValue(() => frame.get().numberAlpha);
  const numberTransform = useDerivedValue(() => [{ scale: frame.get().numberScale }]);
  const glowAlpha = useDerivedValue(() => frame.get().numberGlow > .01 ? frame.get().numberGlow * .46 : 0);
  const glowGlyphs = useDerivedValue(() => frame.get().numberGlow > .01 ? numberLayout(frame.get().value, metrics).all : []);
  return <Group opacity={presented}>
    <Group transform={refreshTransform} origin={{ x: STATUS.glyphX, y: STATUS.glyphY }}>
      <Group opacity={oldStatusA}><Text text={oldStatus} x={STATUS.textX} y={oldStatusY} font={regular} color="rgba(255,255,255,0.62)" /></Group>
      <Group opacity={newStatusA}><Text text={newStatus} x={STATUS.textX} y={newStatusY} font={regular} color="rgba(255,255,255,0.62)" /></Group>
    </Group>
    <Group transform={pillTransform} origin={{ x: 296, y: 232 }} clip={{ x: 250, y: 218, width: 71, height: 27 }}>
      <Group opacity={oldPeriodA}><Text text={oldPeriod} x={PILL.textX} y={oldPeriodY} font={label} color="rgba(255,255,255,0.88)" /></Group>
      <Group opacity={newPeriodA}><Text text={newPeriod} x={PILL.textX} y={newPeriodY} font={label} color="rgba(255,255,255,0.88)" /></Group>
    </Group>
    <Text text={CAPTION} x={captionX} y={512} font={caption} color="rgba(255,255,255,0.78)" />
    <Group opacity={numberAlpha} transform={numberTransform} origin={{ x: 201, y: 552 }}>
      <Glyphs font={bold} glyphs={glowGlyphs} color="#2BFF5A" opacity={glowAlpha} blendMode="plus"><BlurMask blur={9} style="normal" /></Glyphs>
      <NumberSlot frame={frame} font={bold} metrics={metrics} slot={0} />
      <NumberSlot frame={frame} font={bold} metrics={metrics} slot={1} />
      <NumberSlot frame={frame} font={bold} metrics={metrics} slot={2} />
      <NumberSlot frame={frame} font={bold} metrics={metrics} slot={3} />
    </Group>
    <Group opacity={shadowSubA}><Text text={shadowSub} x={shadowSubX} y={604} font={regular} color="rgba(0,12,3,0.35)"><BlurMask blur={6} style="normal" /></Text></Group>
    <Group opacity={oldSubA}><Text text={oldSub} x={oldSubX} y={602} font={regular} color="rgba(255,255,255,0.86)" /></Group>
    <Group opacity={newSubA}><Text text={newSub} x={newSubX} y={602} font={regular} color="rgba(255,255,255,0.86)" /></Group>
  </Group>;
}

/** Letterboxes one material pass and vector type; only the supplied Frame can move the picture. */
export function PeakCanvas({ width, height, frame, still, onReady }: PeakCanvasProps) {
  const regular = useFont(require('./assets/Inter-Regular.ttf'), 12);
  const label = useFont(require('./assets/Inter-Regular.ttf'), 13);
  const caption = useFont(require('./assets/Inter-Regular.ttf'), 14);
  const bold = useFont(require('./assets/Inter-Bold.ttf'), 68);
  const ready = useRef(false);
  const k = Math.min(width / 402, height / 874);
  const px = PixelRatio.get() * k;
  const uniforms = useDerivedValue(() => peakUniforms(frame.get(), px, still));
  const matrix = useDerivedValue(() => Skia.Matrix(peakMatrix(frame.get())));
  useEffect(() => {
    if (regular && label && caption && bold && !ready.current) {
      ready.current = true;
      onReady();
    }
  }, [regular, label, caption, bold, onReady]);
  return <Canvas style={{ width, height }}>
    <Fill color="#000000" />
    <Group transform={[{ translateX: (width - 402 * k) / 2 }, { translateY: (height - 874 * k) / 2 }, { scale: k }]}>
      <Group matrix={matrix}>
        <Rect x={-80} y={-80} width={562} height={1034}><Shader source={effect} uniforms={uniforms} /></Rect>
        {regular && label && caption && bold && <PeakType frame={frame} regular={regular} label={label} caption={caption} bold={bold} />}
      </Group>
    </Group>
  </Canvas>;
}
