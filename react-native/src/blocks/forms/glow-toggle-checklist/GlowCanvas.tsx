import {
  BlurMask, Canvas, Fill, FontEdging, FontHinting, Glyphs, Group, LinearGradient,
  Shader, Skia, type SkFont, useFont,
} from '@shopify/react-native-skia';
import { useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { GLASS_SKSL, HALO_SKSL, glassUniforms, haloUniforms } from './checklist-shader';
import { checklistLetterState, type InkGlyph, layoutChecklistText } from './checklist-text';
import { type ChecklistMotion, D, INK, LABELS, TITLE, checklistGeometry, rowCentreY } from './checklist-theme';

export type GlowCanvasProps = {
  motion: ChecklistMotion;
  /** Screen size in points and the layout scale from `checklistScale`. */
  width: number;
  height: number;
  k: number;
  /** Called once, on the JS thread, when fonts and shaders are ready and the first frame can draw. */
  onReady?: () => void;
};

const haloEffect = Skia.RuntimeEffect.Make(HALO_SKSL);
const glassEffect = Skia.RuntimeEffect.Make(GLASS_SKSL);
if (!haloEffect || !glassEffect) throw new Error('Glow Toggle Checklist: shader compilation failed.');
const FONT = require('./assets/Inter-Bold.ttf');

function textLayout(font: SkFont, text: string, tracking: number, centred: boolean) {
  font.setHinting(FontHinting.None);
  font.setEdging(FontEdging.AntiAlias);
  // The native binding reads this argument as a number; `true` throws on the phone.
  font.setSubpixel(1 as unknown as boolean);
  font.setLinearMetrics(true);
  const ids = font.getGlyphIDs(text);
  const advances = font.getGlyphWidths(ids);
  return layoutChecklistText(Array.from(text, (character, i) => {
    const bounds = font.measureText(character);
    return { id: ids[i], advance: advances[i], x: bounds.x, y: bounds.y,
      width: bounds.width, height: bounds.height };
  }), tracking, centred);
}

function TitleLetter({ font, glyph, index, letters }: {
  font: SkFont; glyph: InkGlyph; index: number; letters: SharedValue<number[]>;
}) {
  const opacity = useDerivedValue(() => checklistLetterState(letters.get()[index]).opacity);
  const transform = useDerivedValue(() => [{ scale: checklistLetterState(letters.get()[index]).scale }]);
  const glyphs = useMemo(() => [glyph], [glyph]);
  return <Group opacity={opacity} transform={transform} origin={{ x: glyph.centreX, y: 0 }}>
    <Glyphs font={font} glyphs={glyphs} x={0} y={0} color={INK} opacity={0.30}><BlurMask blur={5} style="normal" /></Glyphs>
    <Glyphs font={font} glyphs={glyphs} x={0} y={0} color={INK} opacity={0.35}><BlurMask blur={1.5} style="normal" /></Glyphs>
    <Glyphs font={font} glyphs={glyphs} x={0} y={0} color={INK} />
  </Group>;
}

function RowLabel({ font, text, index, open, width, height, k }: {
  font: SkFont; text: string; index: number; open: SharedValue<number>;
  width: number; height: number; k: number;
}) {
  const run = useMemo(() => textLayout(font, text, D.label.tracking, false), [font, text]);
  const y = useDerivedValue(() => {
    'worklet';
    const g = checklistGeometry(open.get(), width, height, k);
    return (rowCentreY(index, g.slabBottom, k) - g.capCy) / k;
  });
  const clip = { x: -D.cap.w / 2, y: D.cap.h / 2, width: D.cap.w, height: D.panelH };
  const x = -D.cap.w / 2 + D.label.x;
  // The paint's vertical alpha ramp fades each pixel as it emerges below the cap.
  const paint = <LinearGradient start={{ x: 0, y: D.cap.h / 2 }}
    end={{ x: 0, y: D.cap.h / 2 + 10 }} colors={['#F4F3EF00', INK]} />;
  return <Group clip={clip}>
    <Glyphs font={font} glyphs={run.glyphs} x={x} y={y} opacity={0.30}>
      {paint}<BlurMask blur={5} style="normal" />
    </Glyphs>
    <Glyphs font={font} glyphs={run.glyphs} x={x} y={y} opacity={0.35}>
      {paint}<BlurMask blur={1.5} style="normal" />
    </Glyphs>
    <Glyphs font={font} glyphs={run.glyphs} x={x} y={y}>{paint}</Glyphs>
  </Group>;
}

/** Two surfaces: one third-resolution gas field, one crisp glass-and-type drawing. */
export function GlowCanvas({ motion, width, height, k, onReady }: GlowCanvasProps) {
  const titleFont = useFont(FONT, D.title.size);
  const labelFont = useFont(FONT, D.label.size);
  const [laidOut, setLaidOut] = useState(false);
  const didSignal = useRef(false);
  // The caller may pass a larger controller object. Capture only its shared values.
  const { clock, present, letters, open, press, lead, trail, hold, fill, hue, tip, surge, flash, glare, still } = motion;
  const halo = useDerivedValue(() => {
    'worklet';
    return haloUniforms({ clock: clock.get(), present: present.get(), letters: letters.get(),
      open: open.get(), press: press.get(), lead: lead.get(), trail: trail.get(), hold: hold.get(),
      fill: fill.get(), hue: hue.get(), tip: tip.get(), surge: surge.get(), flash: flash.get(),
      glare: glare.get(), still: still.get() }, width, height, k);
  });
  const glass = useDerivedValue(() => {
    'worklet';
    return glassUniforms({ clock: clock.get(), present: present.get(), letters: letters.get(),
      open: open.get(), press: press.get(), lead: lead.get(), trail: trail.get(), hold: hold.get(),
      fill: fill.get(), hue: hue.get(), tip: tip.get(), surge: surge.get(), flash: flash.get(),
      glare: glare.get(), still: still.get() }, width, height, k);
  });
  const textTransform = useDerivedValue(() => {
    'worklet';
    const g = checklistGeometry(open.get(), width, height, k);
    return [{ translateX: g.cx }, { translateY: g.capCy }, { scale: k * (1 - 0.05 * press.get()) }];
  });
  const textOpacity = useDerivedValue(() => {
    'worklet';
    const p = Math.max(0, Math.min(1, (present.get() - 0.02) / 0.18));
    return p * p * (3 - 2 * p);
  });
  const capClip = useDerivedValue(() => {
    'worklet';
    const w = D.cap.w * Math.max(0, Math.min(1, present.get()));
    const r = Math.min(D.cap.r, w / 2);
    return Skia.RRectXY(Skia.XYWHRect(-w / 2, -D.cap.h / 2, w, D.cap.h), r, r);
  });
  const title = useMemo(() => titleFont ? textLayout(titleFont, TITLE, D.title.tracking, true) : null, [titleFont]);
  useEffect(() => {
    if (laidOut && titleFont && labelFont && !didSignal.current) {
      didSignal.current = true;
      onReady?.();
    }
  }, [laidOut, titleFont, labelFont, onReady]);

  return <View pointerEvents="none" style={StyleSheet.absoluteFill}
    onLayout={event => setLaidOut(event.nativeEvent.layout.width > 0 && event.nativeEvent.layout.height > 0)}>
    {titleFont && labelFont && title && <>
      <View pointerEvents="none" style={{ position: 'absolute', left: width / 3, top: height / 3,
        width: width / 3, height: height / 3, transform: [{ scale: 3 }] }}>
        <Canvas pointerEvents="none" style={{ width: width / 3, height: height / 3 }}>
          <Fill><Shader source={haloEffect!} uniforms={halo} /></Fill>
        </Canvas>
      </View>
      <Canvas pointerEvents="none" style={{ width, height }}>
        <Fill><Shader source={glassEffect!} uniforms={glass} /></Fill>
        <Group transform={textTransform} opacity={textOpacity}>
          {LABELS.map((text, index) => <RowLabel key={text} text={text} index={index} font={labelFont}
            open={open} width={width} height={height} k={k} />)}
          <Group clip={capClip}>
            {title.glyphs.map((glyph, index) => <TitleLetter key={index} font={titleFont} glyph={glyph} index={index} letters={letters} />)}
          </Group>
        </Group>
      </Canvas>
    </>}
  </View>;
}
