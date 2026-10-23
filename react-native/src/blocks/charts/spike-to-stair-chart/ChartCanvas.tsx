import {
  Blend,
  BlendColor,
  BlurMask,
  Canvas,
  Circle,
  Fill,
  Glyphs,
  Group,
  Image,
  ImageShader,
  LinearGradient,
  Path,
  Points,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  Text,
  type SkFont,
  type SkImage,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useDerivedValue } from 'react-native-reanimated';
import {
  AREA_PATHS,
  atU,
  AXIS_PATH,
  clamp,
  DOT_PATH,
  GLYPH_PATHS,
  GRID_PATH,
  LINE_PATHS,
  PIECES,
} from './chart-curves';
import { columnFlash, crackPose, piecePose, pulse, rgba, shardPose, smooth, sparkPoint } from './chart-film';
import { G, RISERS, SHARDS } from './chart-theme';
import type { ChartMotion } from './use-chart-film';

const linePaths = LINE_PATHS.map((p) => Skia.Path.MakeFromSVGString(p)!);
const areaPaths = AREA_PATHS.map((p) => Skia.Path.MakeFromSVGString(p)!);
const piecePaths = PIECES.map((p) => Skia.Path.MakeFromSVGString(p.path)!);
const glyphPaths = GLYPH_PATHS.map((p) => Skia.Path.MakeFromSVGString(p)!);
const axes = Skia.Path.MakeFromSVGString(AXIS_PATH)!;
const gridPath = Skia.Path.MakeFromSVGString(GRID_PATH)!;
const dotPath = Skia.Path.MakeFromSVGString(DOT_PATH)!;
const arrow = Skia.Path.MakeFromSVGString('M0 0L-9 -4L-7 0L-9 4Z')!;
const hotAxis = Skia.Path.MakeFromSVGString('M64 510H326.218')!;
const yAxis = Skia.Path.MakeFromSVGString('M64 236V510')!;
const xAxis = Skia.Path.MakeFromSVGString('M64 510H338')!;
const columns = RISERS.slice(0, 4).map((u) => ({ x: G.px + u * G.pw, y: atU(1, u).y }));
type Shared = Pick<ChartMotion, 'state' | 'marker' | 'intro' | 'elapsed' | 'columnTimes'>;
export type ChartAssets = {
  font: SkFont;
  small: SkFont;
  digits: SkFont;
  streaks: SkImage;
  glints: SkImage;
  shards: SkImage;
};

// Reveal the light curtain and soften only its reflected right edge.
function Curtain({ motion, d, mirror = false }: { motion: Shared; d: number; mirror?: boolean }) {
  const { state } = motion;
  const opacity = useDerivedValue(() => {
    const f = state.get();
    return (f.d === d ? 1 : 0) * clamp(f.area) * (mirror ? 0.9 : 1);
  });
  const clip = useDerivedValue(() => ({
    x: 64,
    y: 236,
    width: Math.max(0, Math.min(state.get().hx - 64, d ? 274 * 0.968 : 274)),
    height: 274,
  }));
  const colors = useDerivedValue(() => {
    const f = state.get();
    return [
      rgba(f.r, f.g, f.b, Math.min(0.75, 0.3 * Math.max(1, f.area))),
      rgba(f.r, f.g, f.b, 0.15 * Math.max(1, f.area)),
    ];
  });
  const color = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.r, f.g, f.b, 1);
  });
  return (
    <Group opacity={opacity} clip={clip}>
      <Group clip={areaPaths[d]}>
        <Rect x={64} y={236} width={274} height={274}>
          <Blend mode="dstIn">
            <LinearGradient start={{ x: 0, y: 236 }} end={{ x: 0, y: 510 }} colors={colors} />
            <LinearGradient
              start={{ x: 64, y: 0 }}
              end={{ x: 338, y: 0 }}
              colors={
                mirror && d
                  ? ['#ffffff73', '#ffffffff', '#ffffff00', '#ffffff00']
                  : ['#ffffff73', '#ffffffff']
              }
              positions={mirror && d ? [0, (274 * 0.968 - 10) / 274, 0.968, 1] : [0, 1]}
            />
          </Blend>
        </Rect>
        {!mirror && (
          <Path
            path={d ? gridPath : dotPath}
            style={d ? 'stroke' : 'fill'}
            strokeWidth={0.75}
            color={color}
            opacity={0.11}
          />
        )}
      </Group>
    </Group>
  );
}
// Draw the static curve with independent tube and reflected bloom passes.
function ChartLine({ motion, d, mirror = false }: { motion: Shared; d: number; mirror?: boolean }) {
  const { state } = motion;
  const opacity = useDerivedValue(() => {
    const f = state.get();
    return f.d !== d ? 0 : f.L >= 7.05 && f.L < 8.07 ? 1 - smooth((f.L - 7.05) / 0.04) : 1;
  });
  const end = useDerivedValue(() => state.get().end);
  const color = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.r, f.g, f.b, 1);
  });
  const core = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.coreR, f.coreG, f.coreB, 1);
  });
  const breath = useDerivedValue(() => state.get().glow);
  return (
    <Group opacity={opacity}>
      <Group opacity={breath}>
        {!mirror && (
          <Path
            path={linePaths[d]}
            end={end}
            style="stroke"
            strokeWidth={5}
            strokeCap="round"
            strokeJoin="round"
            color="#00000080"
            transform={[{ translateY: 3 }]}
          >
            <BlurMask blur={4} style="normal" />
          </Path>
        )}
        <Path
          path={linePaths[d]}
          end={end}
          style="stroke"
          strokeWidth={mirror ? 14 : 10}
          strokeCap="round"
          strokeJoin="round"
          color={color}
          opacity={mirror ? 0.4 : 0.42}
        >
          <BlurMask blur={mirror ? 16 : 12} style="normal" />
        </Path>
        <Path
          path={linePaths[d]}
          end={end}
          style="stroke"
          strokeWidth={mirror ? 6 : 4.5}
          strokeCap="round"
          strokeJoin="round"
          color={color}
          opacity={mirror ? 0.7 : 0.9}
        >
          <BlurMask blur={mirror ? 6 : 2.5} style="normal" />
        </Path>
      </Group>
      {!mirror && (
        <Path
          path={linePaths[d]}
          end={end}
          style="stroke"
          strokeWidth={2}
          strokeCap="round"
          strokeJoin="round"
          color={core}
        />
      )}
    </Group>
  );
}
// Keep each falling segment lit until its final landing compression.
function BrokenPiece({ motion, i, mirror = false }: { motion: Shared; i: number; mirror?: boolean }) {
  const { state } = motion;
  const transform = useDerivedValue(() => {
    const p = piecePose(state.get().L, i);
    return [
      { translateX: p.x },
      { translateY: p.y },
      { rotate: p.angle },
      { scaleY: p.sy },
      { translateX: -PIECES[i].cx },
      { translateY: -PIECES[i].cy },
    ];
  });
  const opacity = useDerivedValue(() => {
    const f = state.get();
    return f.broken * piecePose(f.L, i).alpha;
  });
  const width = useDerivedValue(() => 2 / Math.sqrt(piecePose(state.get().L, i).sy));
  const crack = useDerivedValue(() => crackPose(state.get().L, i));
  const crackTransform = useDerivedValue(() => [
    { translateX: crack.get().x },
    { translateY: crack.get().y },
  ]);
  const crackRadius = useDerivedValue(() => crack.get().r);
  const crackAlpha = useDerivedValue(() => crack.get().alpha * (mirror ? 0.5 : 1));
  return (
    <>
      <Group transform={transform} opacity={opacity}>
        <Path
          path={piecePaths[i]}
          style="stroke"
          strokeCap="round"
          strokeJoin="round"
          strokeWidth={mirror ? 14 : 10}
          color="#FF2D3D"
          opacity={mirror ? 0.4 : 0.42}
        >
          <BlurMask blur={mirror ? 16 : 12} style="normal" />
        </Path>
        <Path
          path={piecePaths[i]}
          style="stroke"
          strokeCap="round"
          strokeJoin="round"
          strokeWidth={mirror ? 6 : 4.5}
          color="#FF2D3D"
          opacity={mirror ? 0.7 : 0.9}
        >
          <BlurMask blur={mirror ? 6 : 2.5} style="normal" />
        </Path>
        {!mirror && (
          <Path
            path={piecePaths[i]}
            style="stroke"
            strokeCap="round"
            strokeJoin="round"
            strokeWidth={width}
            color="#FFA0A8"
          />
        )}
      </Group>
      <Group transform={crackTransform} opacity={crackAlpha}>
        <Circle cx={0} cy={0} r={crackRadius}>
          <RadialGradient
            c={{ x: 0, y: 0 }}
            r={crackRadius}
            colors={['#FFFFFF', '#FFD7AC', '#FF2D3D00']}
            positions={[0, 0.2, 1]}
          />
        </Circle>
      </Group>
    </>
  );
}
// Carry the landing heat home with a bright leading edge and two soft mirror passes.
function HeatTube({ motion, mirror = false }: { motion: Shared; mirror?: boolean }) {
  const { state } = motion;
  const opacity = useDerivedValue(() => state.get().heat * (mirror ? 0.7 : 1));
  const start = useDerivedValue(() => state.get().heatStart);
  const end = useDerivedValue(() => state.get().heatEnd);
  const near = useDerivedValue(() => ({ x: Math.max(64, state.get().hx - 80), y: 510 }));
  const far = useDerivedValue(() => ({ x: Math.max(65, state.get().hx), y: 510 }));
  return (
    <Group opacity={opacity}>
      <Path
        path={hotAxis}
        start={start}
        end={end}
        style="stroke"
        strokeCap="round"
        strokeWidth={10}
        color="#FF2D3D"
        opacity={0.5}
      >
        <BlurMask blur={10} style="normal" />
      </Path>
      <Path
        path={hotAxis}
        start={start}
        end={end}
        style="stroke"
        strokeCap="round"
        strokeWidth={4.5}
        color="#FF2D3D"
        opacity={0.9}
      >
        <BlurMask blur={2.5} style="normal" />
      </Path>
      {!mirror && (
        <Path path={hotAxis} start={start} end={end} style="stroke" strokeCap="round" strokeWidth={2}>
          <LinearGradient start={near} end={far} colors={['#FF8793', '#FFF4E9']} />
        </Path>
      )}
    </Group>
  );
}
// Let the compressed comet release a radial pulse across the start of the climb.
function GatherFlash({ motion, mirror = false }: { motion: Shared; mirror?: boolean }) {
  const { state } = motion;
  const radius = useDerivedValue(() => 10 + 34 * state.get().gatherTime);
  const ring = useDerivedValue(() => 6 + 24 * state.get().gatherTime);
  const alpha = useDerivedValue(() => state.get().gatherFlash * (mirror ? 0.5 : 1));
  return (
    <Group opacity={alpha}>
      <Circle cx={64} cy={510} r={radius} opacity={0.9}>
        <RadialGradient
          c={{ x: 64, y: 510 }}
          r={radius}
          colors={['#FFFFFF', '#FFBCAD', '#FF2D3D00']}
          positions={[0, 0.18, 1]}
        />
      </Circle>
      <Circle cx={64} cy={510} r={ring} style="stroke" strokeWidth={1.5} color="#FF9A91" opacity={0.7} />
    </Group>
  );
}
// Place a softly tinted atlas tile in front of or behind the tube.
function Shard({ motion, image, i, still }: { motion: Shared; image: SkImage; i: number; still: boolean }) {
  const { state } = motion;
  const size = SHARDS[i][2];
  const transform = useDerivedValue(() => {
    const p = shardPose(state.get().L, i, still, state.get().life);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.angle }];
  });
  const opacity = useDerivedValue(() => shardPose(state.get().L, i, still, state.get().life).alpha);
  const rim = useMemo(
    () => Skia.Path.MakeFromSVGString(i === 0 ? 'M43 53L175 33L219 177' : 'M62 31L211 87L162 220')!,
    [i],
  );
  const rimColor = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.r, f.g, f.b, 0.25);
  });
  const tint = useDerivedValue(() => {
    const f = state.get();
    return rgba(255 * 0.85 + f.r * 0.15, 255 * 0.85 + f.g * 0.15, 255 * 0.85 + f.b * 0.15, 1);
  });
  return (
    <Group
      transform={transform}
      opacity={opacity}
      clip={{ x: -size / 2, y: -size / 2, width: size, height: size }}
    >
      <Image image={image} x={-size / 2 - i * size} y={-size / 2} width={size * 6} height={size} fit="fill">
        <BlendColor color={tint} mode="modulate" />
      </Image>
      {i < 2 && (
        <Group transform={[{ translateX: -size / 2 }, { translateY: -size / 2 }, { scale: size / 256 }]}>
          <Path path={rim} style="stroke" strokeWidth={2} color={rimColor} />
        </Group>
      )}
    </Group>
  );
}
// Light each riser when the film head or scrub marker crosses it.
function Column({ motion, i }: { motion: Shared; i: number }) {
  const { state, elapsed, columnTimes } = motion;
  const c = columns[i];
  const opacity = useDerivedValue(() => {
    const f = state.get();
    return f.d === 1 && f.hx >= c.x
      ? 0.22 + 0.5 * Math.max(columnFlash(f.L, i), pulse(elapsed.get() - columnTimes.get()[i], 0.12, 0.4))
      : 0;
  });
  const colors = useDerivedValue(() => {
    const f = state.get();
    return [rgba(f.r, f.g, f.b, 1), rgba(f.r, f.g, f.b, 0)];
  });
  return (
    <Rect x={c.x} y={c.y} width={0.8} height={510 - c.y} opacity={opacity}>
      <LinearGradient start={{ x: 0, y: c.y }} end={{ x: 0, y: 510 }} colors={colors} />
    </Rect>
  );
}
// Keep the arrow inside the comet glow through the handoff at the origin.
function ChartHead({ motion, mirror = false }: { motion: Shared; mirror?: boolean }) {
  const { state } = motion;
  const transform = useDerivedValue(() => {
    const f = state.get();
    return [{ translateX: f.hx }, { translateY: f.hy }, { rotate: f.angle }];
  });
  const opacity = useDerivedValue(() => state.get().arrow * (mirror ? 0.35 : 1));
  const color = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.coreR, f.coreG, f.coreB, 1);
  });
  const colors = useDerivedValue(() => {
    const f = state.get();
    return [
      rgba(f.coreR, f.coreG, f.coreB, 0.6 + 0.3 * f.comet),
      rgba(f.r, f.g, f.b, 0.5),
      rgba(f.r, f.g, f.b, 0),
    ];
  });
  const radius = useDerivedValue(() => 14 + 8 * state.get().comet - 5 * state.get().gather);
  const sparkRadius = useDerivedValue(() => 3.5 + 3.5 * state.get().gather);
  const sparkAlpha = useDerivedValue(() => state.get().comet);
  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={radius}>
        <RadialGradient c={{ x: 0, y: 0 }} r={radius} colors={colors} positions={[0, 0.22, 1]} />
      </Circle>
      {!mirror && <Path path={arrow} color={color} />}
      <Circle cx={0} cy={0} r={sparkRadius} color="white" opacity={sparkAlpha} />
    </Group>
  );
}
// Share the plot layer order between the upright scene and its mirror.
function Scene({ motion, mirror = false }: { motion: Shared; mirror?: boolean }) {
  const { state, intro } = motion;
  const grid = useDerivedValue(() => state.get().grid);
  const dots = useDerivedValue(() => (1 - state.get().grid) * smooth(intro.get()));
  const dotClip = useDerivedValue(() => ({ x: 64, y: 236, width: 274 * smooth(intro.get()), height: 274 }));
  const axisY = useDerivedValue(() => clamp((intro.get() * 0.9) / 0.4));
  const axisX = useDerivedValue(() => clamp((intro.get() * 0.9 - 0.35) / 0.55));
  const particles = useDerivedValue(() => {
    const pts = [];
    for (let i = 0; i < 30; i++) pts.push(sparkPoint(state.get().L, i));
    return pts;
  });
  return (
    <>
      <Curtain motion={motion} d={0} mirror={mirror} />
      <Curtain motion={motion} d={1} mirror={mirror} />
      {!mirror && (
        <>
          <Path path={gridPath} style="stroke" strokeWidth={0.75} color="#FFFFFF12" opacity={grid} />
          <Group clip={dotClip}>
            <Path path={dotPath} color="#FFFFFF47" opacity={dots} />
          </Group>
          {columns.map((_, i) => (
            <Column key={i} motion={motion} i={i} />
          ))}
        </>
      )}
      <Group opacity={mirror ? 0.24 : 0.35}>
        <Path path={axes} style="stroke" strokeWidth={mirror ? 3 : 4} color="white" opacity={axisX}>
          <BlurMask blur={mirror ? 3 : 4} style="normal" />
        </Path>
      </Group>
      {!mirror && (
        <>
          <Path path={yAxis} end={axisY} style="stroke" strokeWidth={1.5} color="#FFFFFFEB" />
          <Path path={xAxis} end={axisX} style="stroke" strokeWidth={1.5} color="#FFFFFFEB" />
        </>
      )}
      <ChartLine motion={motion} d={0} mirror={mirror} />
      <ChartLine motion={motion} d={1} mirror={mirror} />
      {PIECES.map((_, i) => (
        <BrokenPiece key={i} motion={motion} i={i} mirror={mirror} />
      ))}
      <HeatTube motion={motion} mirror={mirror} />
      <Points
        points={particles}
        mode="points"
        color="#FF2D3D"
        opacity={mirror ? 0.4 : 0.75}
        strokeWidth={4.5}
        strokeCap="round"
      >
        <BlurMask blur={2} style="normal" />
      </Points>
      {!mirror && (
        <Points points={particles} mode="points" color="#FFF1D9" strokeWidth={1.6} strokeCap="round" />
      )}
      <GatherFlash motion={motion} mirror={mirror} />
      <ChartHead motion={motion} mirror={mirror} />
    </>
  );
}
// Measure tracked label glyphs once on the JavaScript runtime.
function tracked(font: SkFont, word: string) {
  const ids = font.getGlyphIDs(word),
    widths = font.getGlyphWidths(ids);
  let width = 0;
  for (let i = 0; i < widths.length; i++) width += widths[i] + (i ? G.tracking : 0);
  let x = 201 - width / 2;
  return ids.map((id, i) => {
    const pos = { x, y: 204 };
    x += widths[i] + G.tracking;
    return { id, pos };
  });
}
// Wipe between tracked labels while preserving their bloom and payoff glitch.
function Label({ motion, font, d }: { motion: Shared; font: SkFont; d: number }) {
  const { state, intro } = motion;
  const glyphs = useMemo(() => tracked(font, d ? 'DISCIPLINE' : 'MOTIVATION'), [font, d]);
  const edge = useDerivedValue(() => {
    const f = state.get();
    const reveal = intro.get() < 1 ? smooth(intro.get()) : f.label;
    return [
      { x: 64 - 24 + 322 * reveal, y: 0 },
      { x: 64 + 322 * reveal, y: 0 },
    ];
  });
  const start = useDerivedValue(() => edge.get()[0]);
  const end = useDerivedValue(() => edge.get()[1]);
  const transform = useDerivedValue(() => [
    { translateY: 4 * (d ? 1 - state.get().label : state.get().label) },
  ]);
  const introAlpha = useDerivedValue(() => (d ? (state.get().label > 0 ? 1 : 0) : 1));
  const wipeColors = useDerivedValue(() =>
    d || intro.get() < 1 ? ['#F4F6F5', 'transparent'] : ['transparent', '#F4F6F5'],
  );
  const glitch = useDerivedValue(() => (d ? state.get().glitch * 0.35 : 0));
  return (
    <Group transform={transform} opacity={introAlpha}>
      <Glyphs glyphs={glyphs} x={0} y={0} font={font} opacity={0.25}>
        <LinearGradient start={start} end={end} colors={wipeColors} />
        <BlurMask blur={14} style="normal" />
      </Glyphs>
      <Glyphs glyphs={glyphs} x={0} y={0} font={font} opacity={0.8}>
        <LinearGradient start={start} end={end} colors={wipeColors} />
        <BlurMask blur={6} style="normal" />
      </Glyphs>
      <Glyphs glyphs={glyphs} x={0} y={0} font={font}>
        <LinearGradient start={start} end={end} colors={wipeColors} />
      </Glyphs>
      <Group opacity={glitch} clip={{ x: 60, y: 187, width: 290, height: 8 }}>
        <Glyphs glyphs={glyphs} x={4} y={0} font={font} color="white" />
      </Group>
      <Group opacity={glitch} clip={{ x: 60, y: 195, width: 290, height: 9 }}>
        <Glyphs glyphs={glyphs} x={-4} y={0} font={font} color="white" />
      </Group>
    </Group>
  );
}
// Attach the value chip to the curve with precomputed digit advances.
function Marker({
  motion,
  assets,
  mirror = false,
}: {
  motion: Shared;
  assets: ChartAssets;
  mirror?: boolean;
}) {
  const { marker, state } = motion;
  const widths = useMemo(
    () => assets.digits.getGlyphWidths(assets.digits.getGlyphIDs('0123456789')),
    [assets.digits],
  );
  const transform = useDerivedValue(() => {
    const p = marker.get();
    return [{ translateX: p.x }, { translateY: p.y }, { scale: p.scale }];
  });
  const opacity = useDerivedValue(() => marker.get().alpha * (mirror ? 0.35 : 1));
  const colors = useDerivedValue(() => {
    const f = state.get();
    return [rgba(f.r, f.g, f.b, 0.5), rgba(f.r, f.g, f.b, 0.15)];
  });
  const color = useDerivedValue(() => {
    const f = state.get();
    return rgba(f.r, f.g, f.b, 1);
  });
  const stem = useDerivedValue(() => Math.max(0, 510 - marker.get().y));
  const chip = useDerivedValue(() => {
    const p = marker.get();
    return [{ translateX: clamp(p.x, 92, 310) - p.x - 36 }, { translateY: -42 }];
  });
  const day = useDerivedValue(() => `D${marker.get().day}`);
  const value = useDerivedValue(() => String(marker.get().value));
  const digitX = useDerivedValue(() => {
    const n = marker.get().value;
    let width = widths[n % 10];
    if (n >= 10) width += widths[Math.floor(n / 10) % 10];
    if (n >= 100) width += widths[1];
    return 62 - width;
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Rect x={-0.5} y={0} width={1} height={stem}>
        <LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: 274 }} colors={colors} />
      </Rect>
      <Circle cx={0} cy={0} r={15} opacity={0.5}>
        <RadialGradient c={{ x: 0, y: 0 }} r={15} colors={colors} />
      </Circle>
      <Circle cx={0} cy={0} r={3.5} color="white" />
      {!mirror && (
        <Group transform={chip}>
          <RoundedRect x={0} y={0} width={72} height={28} r={14} color="rgba(12,14,13,.88)" />
          <RoundedRect
            x={0}
            y={0}
            width={72}
            height={28}
            r={14}
            style="stroke"
            strokeWidth={1}
            color={color}
            opacity={0.55}
          />
          <Text x={10} y={18} text={day} font={assets.small} color="#FFFFFF99" />
          <Text x={digitX} y={19} text={value} font={assets.digits} color="white" />
        </Group>
      )}
    </Group>
  );
}
// Light the selected optical glyph and apply press compression.
function SwitchGlyph({ motion, d }: { motion: ChartMotion; d: number }) {
  const { thumbPosition, pressed, pressAmount } = motion;
  const transform = useDerivedValue(() => [
    { translateX: d ? 233 : 169 },
    { translateY: 792 },
    { scale: pressed.get() === d ? 1 - 0.03 * pressAmount.get() : 1 },
  ]);
  const color = useDerivedValue(() => {
    const active = d ? clamp(thumbPosition.get()) : 1 - clamp(thumbPosition.get());
    const press = pressed.get() === d ? pressAmount.get() * 0.2 : 0;
    return d
      ? rgba(
          255 - 215 * active * (1 - press),
          255 - 31 * active * (1 - press),
          255 - 133 * active * (1 - press),
          0.38 + 0.62 * active + press,
        )
      : rgba(
          255,
          255 - 210 * active * (1 - press),
          255 - 194 * active * (1 - press),
          0.38 + 0.62 * active + press,
        );
  });
  const glow = useDerivedValue(() => 0.6 * (d ? clamp(thumbPosition.get()) : 1 - clamp(thumbPosition.get())));
  return (
    <Group transform={transform}>
      <Path
        path={glyphPaths[d]}
        style="stroke"
        strokeWidth={1.75}
        strokeCap="round"
        strokeJoin="round"
        color={color}
        opacity={glow}
      >
        <BlurMask blur={2.5} style="normal" />
      </Path>
      <Path
        path={glyphPaths[d]}
        style="stroke"
        strokeWidth={1.75}
        strokeCap="round"
        strokeJoin="round"
        color={color}
      />
    </Group>
  );
}
// Draw the glass track and spring-driven thumb in the canvas.
function SwitchArt({ motion }: { motion: ChartMotion }) {
  const { thumbPosition, pressed, pressAmount } = motion;
  const transform = useDerivedValue(() => [
    { translateX: 169 + 64 * thumbPosition.get() },
    { translateY: 792 },
    { scale: pressed.get() === Math.round(thumbPosition.get()) ? 1 - 0.03 * pressAmount.get() : 1 },
    { translateX: -31 },
    { translateY: -18 },
  ]);
  const color = useDerivedValue(() => {
    const p = clamp(thumbPosition.get());
    return rgba(255 - 215 * p, 45 + 179 * p, 61 + 61 * p, 1);
  });
  return (
    <>
      <RoundedRect x={133} y={770} width={136} height={44} r={22} color="#FFFFFF0D" />
      <RoundedRect x={133} y={770} width={136} height={44} r={22} style="stroke" strokeWidth={1}>
        <LinearGradient start={{ x: 0, y: 770 }} end={{ x: 0, y: 814 }} colors={['#FFFFFF38', '#FFFFFF0F']} />
      </RoundedRect>
      <Group transform={transform}>
        <RoundedRect x={0} y={0} width={62} height={36} r={18} color={color} opacity={0.25}>
          <BlurMask blur={8} style="normal" />
        </RoundedRect>
        <RoundedRect x={0} y={0} width={62} height={36} r={18} color="#FFFFFF17" />
        <RoundedRect
          x={0}
          y={0}
          width={62}
          height={36}
          r={18}
          style="stroke"
          strokeWidth={1}
          color={color}
          opacity={0.55}
        />
      </Group>
      <SwitchGlyph motion={motion} d={0} />
      <SwitchGlyph motion={motion} d={1} />
    </>
  );
}
// Compose the opaque scene, polished floor, foreground glass, and switch.
export function ChartCanvas({
  width,
  height,
  k,
  ox,
  oy,
  motion,
  assets,
  still,
}: {
  width: number;
  height: number;
  k: number;
  ox: number;
  oy: number;
  motion: ChartMotion;
  assets: ChartAssets;
  still: boolean;
}) {
  const { state, presented, sceneOpacity } = motion;
  const opacity = useDerivedValue(() => presented.get() * sceneOpacity.get());
  const transform = useDerivedValue(() => {
    const f = state.get();
    return [
      { translateX: -8 * f.jolt },
      { translateY: 6 * f.jolt + f.dip },
      { translateX: 201 },
      { translateY: 400 },
      { rotate: ((-1 * Math.PI) / 180) * f.jolt },
      { scale: 1 + 0.025 * f.jolt },
      { translateX: -201 },
      { translateY: -400 },
    ];
  });
  const floorTint = useDerivedValue(() => {
    const f = state.get();
    return rgba(178.5 + f.r * 0.3, 178.5 + f.g * 0.3, 178.5 + f.b * 0.3, 1);
  });
  const pool = useDerivedValue(() => state.get().pool);
  const colors = useDerivedValue(() => {
    const f = state.get();
    return [rgba(f.r, f.g, f.b, 0.1 + 0.06 * f.axisLight), 'transparent'];
  });
  const floorColors = useDerivedValue(() => {
    const f = state.get();
    return [rgba(f.r, f.g, f.b, 0.16), 'transparent'];
  });
  const packetAlpha = useDerivedValue(() => state.get().packet * 0.6);
  const packetStart = useDerivedValue(() => state.get().packetStart),
    packetEnd = useDerivedValue(() => state.get().packetEnd);
  return (
    <Canvas style={{ width, height }} accessible={false}>
      {/* Multiply and plus need an opaque destination: on a transparent canvas the floor texture shows as itself. */}
      <Fill color="black" />
      <Group transform={[{ translateX: ox }, { translateY: oy }, { scale: k }]} opacity={opacity}>
        <Group transform={transform}>
          <Circle cx={201} cy={250} r={260}>
            <RadialGradient c={{ x: 201, y: 250 }} r={260} colors={['#FFFFFF12', 'transparent']} />
          </Circle>
          <Circle cx={201} cy={400} r={230}>
            <RadialGradient c={{ x: 201, y: 400 }} r={230} colors={colors} />
          </Circle>
          <Group transform={[{ translateX: 201 }, { translateY: 522 }, { scaleY: 26 / 190 }]} opacity={pool}>
            <Circle cx={0} cy={0} r={190}>
              <RadialGradient c={{ x: 0, y: 0 }} r={190} colors={floorColors} />
            </Circle>
          </Group>
          <Group clip={{ x: 24, y: 522, width: 354, height: 258 }}>
            <Group transform={[{ translateY: 1044 }, { scaleY: -1 }]}>
              <Scene motion={motion} mirror />
              <Marker motion={motion} assets={assets} mirror />
            </Group>
            {/* Wider than the clip: at the clip's anti-aliased edge the lit mirror image showed as a hairline. */}
            <Rect x={16} y={522} width={370} height={266}>
              <LinearGradient
                start={{ x: 0, y: 522 }}
                end={{ x: 0, y: 770 }}
                colors={['#00000000', '#00000038', '#000000AD', '#000000FF']}
                positions={[0, 90 / 248, 180 / 248, 1]}
              />
            </Rect>
            <Rect x={24} y={522} width={354} height={258} blendMode="multiply" opacity={0.62}>
              <ImageShader
                image={assets.streaks}
                tx="repeat"
                ty="repeat"
                fit="none"
                transform={[{ translateX: 24 }, { translateY: 522 }, { scale: 0.6 }]}
              />
            </Rect>
            <Image
              image={assets.glints}
              x={40}
              y={520}
              width={322}
              height={161}
              fit="fill"
              blendMode="plus"
              opacity={0.6}
            >
              <BlendColor color={floorTint} mode="modulate" />
            </Image>
          </Group>
          {[0, 2, 4, 5].map((i) => (
            <Shard key={i} motion={motion} image={assets.shards} i={i} still={still} />
          ))}
          <Scene motion={motion} />
          <Path
            path={linePaths[1]}
            start={packetStart}
            end={packetEnd}
            style="stroke"
            strokeWidth={2}
            strokeCap="round"
            color="white"
            opacity={packetAlpha}
          />
          {[1, 3].map((i) => (
            <Shard key={i} motion={motion} image={assets.shards} i={i} still={still} />
          ))}
          <Label motion={motion} font={assets.font} d={0} />
          <Label motion={motion} font={assets.font} d={1} />
          <Marker motion={motion} assets={assets} />
        </Group>
        <SwitchArt motion={motion} />
      </Group>
    </Canvas>
  );
}
