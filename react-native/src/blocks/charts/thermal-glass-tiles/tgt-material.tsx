import type { ReactNode } from 'react';
import {
  ColorShader,
  Fill,
  Group,
  FilterMode,
  ImageShader,
  MipmapMode,
  RoundedRect,
  Shader,
  Skia,
  useImage,
  type DataSourceParam,
} from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { FIELD_SKSL, GLASS_SKSL, INK_SKSL } from './tgt-shaders';
import { smooth } from './tgt-motion';
import { sys, type TileRect } from './tgt-theme';

const fieldEffect = Skia.RuntimeEffect.Make(FIELD_SKSL);
const glassEffect = Skia.RuntimeEffect.Make(GLASS_SKSL);
const inkEffect = Skia.RuntimeEffect.Make(INK_SKSL);
export const SHADERS_READY = !!fieldEffect && !!glassEffect && !!inkEffect;
export type ThermalFieldProps = {
  source?: DataSourceParam | { atlas: DataSourceParam; cols: number; rows: number };
  frame?: SharedValue<number>;
  effort?: SharedValue<number>;
  streak?: SharedValue<number>;
  direction?: SharedValue<number>;
  phase?: SharedValue<number>;
  time?: SharedValue<number>;
  peak?: SharedValue<number>;
  touch?: SharedValue<number[]>;
  palette?: 'thermal' | 'cool' | 'mono' | 'acid' | DataSourceParam;
  wake?: number;
  grain?: number;
  touchHeat?: boolean;
  soft?: boolean;
  width?: number;
  height?: number;
  scale?: number;
  offset?: [number, number];
};
/** Skia shader node: put in Fill, or pass as GlassTile.backdrop. No image is required. */
export function ThermalField({
  source,
  frame,
  effort,
  streak,
  direction,
  phase,
  time,
  peak,
  touch,
  palette = 'thermal',
  wake = sys.hero.field.peakWake,
  grain = sys.hero.field.grain,
  touchHeat = true,
  soft = false,
  width = 402,
  height = 874,
  scale = 1,
  offset = [0, 0],
}: ThermalFieldProps) {
  const actualSource = source ?? {
    atlas: soft ? require('./assets/runner-heat-atlas-blur.png') : require('./assets/runner-heat-atlas.png'),
    cols: 6,
    rows: 4,
  };
  const packed = typeof actualSource === 'object' && actualSource !== null && 'atlas' in actualSource ? actualSource : null;
  const image = useImage(packed ? packed.atlas : (actualSource as DataSourceParam | undefined));
  const isPreset = typeof palette === 'string' && ['thermal', 'cool', 'mono', 'acid'].includes(palette);
  const lut = useImage(isPreset ? require('./assets/thermal-palette.png') : (palette as DataSourceParam));
  const cols = packed?.cols ?? 1,
    rows = packed?.rows ?? 1;
  const mode = isPreset ? ['thermal', 'cool', 'mono', 'acid'].indexOf(palette as string) : 4;
  const paletteRows = lut?.height() ?? 1;
  const imageWidth = image?.width() ?? 1,
    imageHeight = image?.height() ?? 1;
  const uniforms = useDerivedValue(() => ({
    size: [width, height],
    offset,
    scale,
    phase: phase?.get() ?? 0.72,
    effort: effort?.get() ?? 0.39655,
    streak: streak?.get() ?? 0,
    time: time?.get() ?? 0,
    peak: peak?.get() ?? 0,
    touch: touchHeat ? (touch?.get() ?? [0, 0, -1]) : [0, 0, -1],
    grain,
    wake: wake === 0 ? 0 : 20 + (wake - 20) * smooth(0.4, 0.8, effort?.get() ?? 0.39655),
    direction: direction?.get() ?? 1,
    sourceMode: image ? 1 : 0,
    sourceSize: [imageWidth, imageHeight],
    atlas: [cols, rows],
    frame: frame?.get() ?? ((phase?.get() ?? 0.72) * 24) / (Math.PI * 2),
    paletteMode: mode,
    paletteRows,
    outputP3: 1,
    figure: [sys.hero.field.figure.x, sys.hero.field.figure.y, sys.hero.field.figure.k],
  }));
  if (!fieldEffect) return <ColorShader color={sys.surface.page} />;
  return (
    <Shader source={fieldEffect} uniforms={uniforms}>
      {image ? (
        <ImageShader image={image} tx="clamp" ty="clamp" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      ) : (
        <ColorShader color={sys.surface.page} />
      )}
      {lut ? (
        <ImageShader image={lut} tx="clamp" ty="clamp" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      ) : (
        <ColorShader color={sys.surface.page} />
      )}
    </Shader>
  );
}
export type GlassTileProps = {
  rect: TileRect;
  shape?: 'squircle' | 'circle';
  radius?: number;
  frost?: number;
  refraction?: number;
  dispersion?: number;
  lens?: number;
  ink?: 'auto' | string;
  darkInk?: SharedValue<number>;
  backdrop: ReactNode;
  time?: SharedValue<number>;
  peak?: SharedValue<number>;
  labelRect?: [number, number, number, number];
  scale?: number;
  children?: ReactNode;
};
/** A lens over an arbitrary Skia backdrop shader. Children remain sharp above the lens. */
export function GlassTile({
  rect,
  shape = 'squircle',
  radius = rect.r,
  frost = sys.hero.material.frost,
  refraction = sys.hero.material.refraction,
  dispersion = sys.hero.material.dispersion,
  lens = sys.hero.material.lens,
  ink = 'auto',
  darkInk,
  backdrop,
  time,
  peak,
  labelRect = [rect.x + 12, rect.y + 12, rect.w - 24, rect.h - 24],
  scale = 1,
  children,
}: GlassTileProps) {
  const dark = ink !== 'auto' && ink.toLowerCase() === sys.ink.dark.toLowerCase();
  const uniforms = useDerivedValue(() => ({
    rect: [rect.x, rect.y, rect.w, rect.h],
    radius,
    circle: shape === 'circle' ? 1 : 0,
    frost,
    refraction: refraction * scale,
    dispersion: dispersion * scale,
    lens,
    peak: peak?.get() ?? 0,
    time: time?.get() ?? 0,
    grain: sys.hero.field.grain,
    inkDark: darkInk?.get() ?? (ink === 'auto' ? -1 : dark ? 1 : 0),
    labelRect,
    materialScale: scale,
  }));
  const lightColor = Array.from(Skia.Color(sys.ink.primary)),
    darkColor = Array.from(Skia.Color(sys.ink.dark));
  const inheritedInk = useDerivedValue(() => ({ labelRect, lightInk: lightColor, darkInk: darkColor }));
  return (
    <>
      <RoundedRect x={rect.x} y={rect.y} width={rect.w} height={rect.h} r={radius} color={sys.surface.page}>
        {glassEffect ? (
          <Shader source={glassEffect} uniforms={uniforms}>
            {backdrop}
          </Shader>
        ) : null}
      </RoundedRect>
      <Group color={ink === 'auto' ? undefined : ink}>
        {ink === 'auto' && inkEffect ? (
          <Shader source={inkEffect} uniforms={inheritedInk}>
            {backdrop}
          </Shader>
        ) : null}
        {children}
      </Group>
    </>
  );
}
/** Convenience base layer for a consumer composing their own canvas. */
export function ThermalBackdrop(props: ThermalFieldProps) {
  return (
    <Fill>
      <ThermalField {...props} />
    </Fill>
  );
}
