import { AlphaType, Blur, Canvas, Circle, ColorType, FilterMode, Group, Image, MipmapMode, RadialGradient, Rect, Skia, type SkImage, Text as SkiaText, matchFont, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { Platform, StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { CLOUD_A_FIELD, CLOUD_B_FIELD, CLOUD_GRID } from './cloudColours';
import { COLLAPSE, DRIFT_PERIOD, EXPAND, SPIN_AT, SPIN_PERIOD } from './motion';
import { COLORS, DISCOVER, FONT, SHEET } from './theme';

// The light sheet's art: a soft colour cloud that collapses into a spinning sphere while the
// amount is "discovered", then melts back into a wider cloud carrying the amount.
// The headline is drawn with colour-dodge so it picks up the cloud's hues like the pin's text.

type Blob = { x: number; y: number; r: number; color: string; alpha: number; dx: number; dy: number; phase: number };

// Positions in screen points (402 × 874 pin frame), radii in points.
// Inside the sphere, offsets from its centre.
// Inside the sphere, offsets from its centre as the pin shows them 4.2 s in (green up, blue
// lower right, orange lower left, red and pink at the core); the group turns from there.
const SPHERE_BLOBS: Blob[] = [
  { x: 7.5, y: -47.4, r: 100, color: '#c2d63a', alpha: 1, dx: 4, dy: 4, phase: 0 },
  { x: 40.2, y: 22.3, r: 62, color: '#4f86e6', alpha: 0.85, dx: 4, dy: 4, phase: 1.7 },
  { x: -39.8, y: 23.0, r: 86, color: '#f08a2c', alpha: 0.95, dx: 4, dy: 4, phase: 3.2 },
  { x: -21.3, y: 5.7, r: 46, color: '#ea4a2a', alpha: 0.6, dx: 4, dy: 4, phase: 4.4 },
  { x: 4.0, y: 6.9, r: 40, color: '#d0679f', alpha: 0.7, dx: 4, dy: 4, phase: 5.6 },
];

const TAU = Math.PI * 2;

function withAlpha(hex: string, a: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}
function easeIn(t: number) {
  'worklet';
  return t * t;
}

// stage 0 = first cloud, 1 = sphere, 2 = amount cloud. Linear stage time → the pin's beats.
export function cloudALook(stage: number) {
  'worklet';
  const s = clamp01(stage);
  let scale = 1;
  if (s <= COLLAPSE.settle) scale = lerp(1, 0.93, easeOut(s / COLLAPSE.settle));
  else scale = lerp(0.93, 0.2, easeIn(clamp01((s - COLLAPSE.settle) / (COLLAPSE.vanish - COLLAPSE.settle))));
  const opacity = stage >= 1 ? 0 : 1 - clamp01((s - COLLAPSE.settle) / (COLLAPSE.vanish - COLLAPSE.settle));
  const text = 1 - clamp01((s - COLLAPSE.settle) / ((COLLAPSE.vanish - COLLAPSE.settle) * 0.8));
  return { scale, opacity, text };
}

export function sphereLook(stage: number) {
  'worklet';
  if (stage <= 1) {
    const s = clamp01(stage);
    const k = clamp01((s - COLLAPSE.sphereIn) / (1 - COLLAPSE.sphereIn));
    return { scale: lerp(COLLAPSE.sphereFrom, 1, easeOut(k)), opacity: clamp01((s - COLLAPSE.sphereIn) / 0.12) };
  }
  const e = clamp01(stage - 1);
  let scale = 1;
  if (e <= EXPAND.shrink) scale = lerp(1, 0.93, easeOut(e / EXPAND.shrink));
  else if (e <= EXPAND.fall) scale = lerp(0.93, 0.6, easeIn((e - EXPAND.shrink) / (EXPAND.fall - EXPAND.shrink)));
  else scale = lerp(0.6, 0.5, clamp01((e - EXPAND.fall) / 0.2));
  return { scale, opacity: 1 - clamp01((e - EXPAND.blobIn) / (EXPAND.fadeEnd - EXPAND.blobIn)) };
}

export function cloudBLook(stage: number) {
  'worklet';
  const e = clamp01(stage - 1);
  const k = clamp01((e - EXPAND.blobIn) / (1 - EXPAND.blobIn));
  const opacity = stage <= 1 ? 0 : clamp01((e - EXPAND.blobIn) / (EXPAND.fadeEnd - EXPAND.blobIn));
  return { scale: lerp(EXPAND.blobFrom, 1, easeOut(k)), opacity };
}

type Props = {
  width: number;
  height: number; // canvas height (sheet height)
  ky: number; // vertical scale for screens that are not 874 pt tall
  stage: SharedValue<number>;
  time: SharedValue<number>;
  reduceMotion: boolean;
};

export function Orb({ width, height, ky, stage, time, reduceMotion }: Props) {
  const fieldA = useMemo(() => makeField(CLOUD_A_FIELD), []);
  const fieldB = useMemo(() => makeField(CLOUD_B_FIELD), []);
  const fonts = useMemo(() => {
    const family = Platform.select({ ios: FONT.skia, default: 'sans-serif' });
    return {
      title: matchFont({ fontFamily: family, fontSize: DISCOVER.titleSize, fontWeight: '700' }),
      sub: matchFont({ fontFamily: family, fontSize: DISCOVER.subSize, fontWeight: '400' }),
      amount: matchFont({ fontFamily: family, fontSize: DISCOVER.amountSize, fontWeight: '700' }),
    };
  }, []);

  const sx = width / 402;
  const px = (x: number) => x * sx;
  // pin screen y → canvas y (the canvas starts at the sheet's top, which is y 62 in the pin)
  const yOf = (y: number) => (y - SHEET.top) * ky;

  const ca = { x: px(DISCOVER.orbCenter.x), y: yOf(DISCOVER.orbCenter.y) };
  const sc = { x: px(DISCOVER.sphere.x), y: yOf(DISCOVER.sphere.y) };
  const sr = DISCOVER.sphere.r * sx;

  const lineA = ['Discover your', 'gift amount'];
  const subA = "Then you'll choose a company";
  const lineB = ['Now it’s time to choose', 'your gift stock'];
  const amount = 'USD 3';

  const centre = (font: typeof fonts.title, text: string) => (width - font.measureText(text).width) / 2;
  const baseline = (font: typeof fonts.title, centreY: number) => {
    const m = font.getMetrics();
    return centreY - (m.ascent + m.descent) / 2;
  };

  // Reduce Motion: nothing scales; each look cross-fades over 30 % of its stage (about 200 ms).
  const lookA = useDerivedValue(() => {
    if (!reduceMotion) return cloudALook(stage.get());
    const o = 1 - clamp01((stage.get() - 0.35) / 0.3);
    return { scale: 1, opacity: o, text: o };
  });
  const lookS = useDerivedValue(() => {
    if (!reduceMotion) return sphereLook(stage.get());
    const v = stage.get();
    return { scale: 1, opacity: v <= 1 ? clamp01((v - 0.35) / 0.3) : 1 - clamp01((v - 1.35) / 0.3) };
  });
  const lookB = useDerivedValue(() => (reduceMotion ? { scale: 1, opacity: clamp01((stage.get() - 1.35) / 0.3) } : cloudBLook(stage.get())));

  const tA = useDerivedValue(() => [{ translateX: ca.x }, { translateY: ca.y }, { scale: lookA.get().scale }, { translateX: -ca.x }, { translateY: -ca.y }]);
  const oA = useDerivedValue(() => lookA.get().opacity);
  const textA = useDerivedValue(() => lookA.get().text);
  const tB = useDerivedValue(() => [{ translateX: ca.x }, { translateY: ca.y }, { scale: lookB.get().scale }, { translateX: -ca.x }, { translateY: -ca.y }]);
  const oB = useDerivedValue(() => lookB.get().opacity);
  const tS = useDerivedValue(() => [{ translateX: sc.x }, { translateY: sc.y }, { scale: lookS.get().scale }, { translateX: -sc.x }, { translateY: -sc.y }]);
  const oS = useDerivedValue(() => lookS.get().opacity);
  const spin = useDerivedValue(() => [
    { translateX: sc.x },
    { translateY: sc.y },
    { rotate: reduceMotion ? 0 : -((time.get() - SPIN_AT) / SPIN_PERIOD) * TAU },
    { translateX: -sc.x },
    { translateY: -sc.y },
  ]);

  const titleColor = '#4e4a50';

  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height} color={COLORS.sheetLight} />

      {/* the clouds are the pin's own colour fields, blurred smooth */}
      <Group transform={tA} opacity={oA}>
        <FieldImage image={fieldA} y={-yOf(SHEET.top)} width={width} height={height} />
      </Group>
      <Group transform={tB} opacity={oB}>
        <FieldImage image={fieldB} y={-yOf(SHEET.top)} width={width} height={height} />
      </Group>

      <Group transform={tS} opacity={oS}>
        {/* the glass disc: a crisp edge, faintly darker than the page */}
        <Circle cx={sc.x} cy={sc.y} r={sr}>
          <RadialGradient
            c={vec(sc.x, sc.y)}
            r={sr}
            colors={['#e9e9ee', '#eaeaee', '#ececf0', '#eeeef1']}
            positions={[0, 0.7, 0.93, 1]}
          />
        </Circle>
        <Group clip={{ rect: { x: sc.x - sr, y: sc.y - sr, width: sr * 2, height: sr * 2 }, rx: sr, ry: sr }}>
          <Group transform={spin}>
            {SPHERE_BLOBS.map((b, i) => (
              <CloudBlob key={`s${i}`} blob={b} cx={sc.x + b.x * sx} cy={sc.y + b.y * sx} r={b.r * sx} time={time} still={reduceMotion} />
            ))}
          </Group>
          <Circle cx={sc.x} cy={sc.y} r={sr}>
            <RadialGradient
              c={vec(sc.x, sc.y)}
              r={sr}
              colors={['rgba(233,233,238,0)', 'rgba(233,233,238,0)', 'rgba(234,234,239,0.85)', 'rgba(238,238,241,1)']}
              positions={[0, 0.55, 0.86, 1]}
            />
          </Circle>
        </Group>
      </Group>

      {/* Headlines: colour-dodge brightens the cloud underneath into the pin's pastel lettering. */}
      <Group transform={tA} opacity={textA}>
        {lineA.map((t, i) => (
          <SkiaText key={t} x={centre(fonts.title, t)} y={baseline(fonts.title, yOf(DISCOVER.titleY[i]))} text={t} font={fonts.title} color={titleColor} blendMode="colorDodge" />
        ))}
        <SkiaText x={centre(fonts.sub, subA)} y={baseline(fonts.sub, yOf(DISCOVER.subY))} text={subA} font={fonts.sub} color="#4a4648" blendMode="colorDodge" />
      </Group>
      <Group transform={tB} opacity={oB}>
        <SkiaText x={centre(fonts.amount, amount)} y={baseline(fonts.amount, yOf(DISCOVER.amountY))} text={amount} font={fonts.amount} color={titleColor} blendMode="colorDodge" />
        {lineB.map((t, i) => (
          <SkiaText key={t} x={centre(fonts.sub, t)} y={baseline(fonts.sub, yOf(DISCOVER.nowY[i]))} text={t} font={fonts.sub} color="#4a4648" blendMode="colorDodge" />
        ))}
      </Group>
    </Canvas>
  );
}

function CloudBlob({ blob, cx, cy, r, time, still }: { blob: Blob; cx: number; cy: number; r: number; time: SharedValue<number>; still: boolean }) {
  const c = useDerivedValue(() => {
    if (still) return vec(cx, cy);
    const a = (time.get() / DRIFT_PERIOD) * TAU + blob.phase;
    return vec(cx + Math.sin(a) * blob.dx, cy + Math.cos(a) * blob.dy);
  });
  return (
    <Circle c={c} r={r}>
      <RadialGradient
        c={c}
        r={r}
        colors={[withAlpha(blob.color, blob.alpha), withAlpha(blob.color, blob.alpha * 0.8), withAlpha(blob.color, blob.alpha * 0.42), withAlpha(blob.color, 0)]}
        positions={[0, 0.4, 0.72, 1]}
      />
    </Circle>
  );
}

function makeField(grid: string[][]) {
  const { cols, rows } = CLOUD_GRID;
  const bytes = new Uint8Array(cols * rows * 4);
  grid.forEach((row, r) =>
    row.forEach((hex, c) => {
      const n = parseInt(hex.slice(1), 16);
      const o = (r * cols + c) * 4;
      bytes[o] = (n >> 16) & 255;
      bytes[o + 1] = (n >> 8) & 255;
      bytes[o + 2] = n & 255;
      bytes[o + 3] = 255;
    }),
  );
  return Skia.Image.MakeImage({ width: cols, height: rows, alphaType: AlphaType.Opaque, colorType: ColorType.RGBA_8888 }, Skia.Data.fromBytes(bytes), cols * 4);
}

function FieldImage({ image, y, width, height }: { image: SkImage | null; y: number; width: number; height: number }) {
  if (!image) return null;
  return (
    <Image image={image} x={0} y={y} width={width} height={height} fit="fill" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}>
      <Blur blur={16} mode="clamp" />
    </Image>
  );
}
