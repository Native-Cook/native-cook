import {
  Blur, Canvas, Circle, Group, LinearGradient, Path, Rect, Shader, Skia, SweepGradient, vec, type Uniforms,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { nightAtNightSleepScore } from './motion';
import {
  NIGHT_SLEEP_SCORE_RAYS_EFFECT, NIGHT_SLEEP_SCORE_RIBBON, NIGHT_SLEEP_SCORE_RIBBON_GRADIENT, NIGHT_SLEEP_SCORE_RIBBON_PATH,
  NIGHT_SLEEP_SCORE_RING_GRADIENT, NIGHT_SLEEP_SCORE_SKY_EFFECT, ribbonYNightSleepScore,
} from './sky';
import { NIGHT_SLEEP_SCORE_AURORA as A, NIGHT_SLEEP_SCORE_CARD as CARD, NIGHT_SLEEP_SCORE_COLORS as COLORS } from './theme';

/** Shared values the night layers read; all of them are written by the block's frame callback on the UI thread. */
export type NightSkyValues = Readonly<{
  night: SharedValue<number>; reveal: SharedValue<number>; phase: SharedValue<number>; meteor: SharedValue<number>;
  clock: SharedValue<number>; twinkle: SharedValue<number>; cursor: SharedValue<number>; presence: SharedValue<number>;
}>;

const chart = CARD.chart;
const ring = CARD.ring;
const RING_PATH = Skia.PathBuilder.Make()
  .addArc(Skia.XYWHRect(ring.cx - ring.radius, ring.cy - ring.radius, ring.radius * 2, ring.radius * 2), 0, 359.99).build();
const RIBBON_START = vec(chart.left, 0);
const RIBBON_END = vec(chart.left + chart.width, 0);
const RING_CENTRE = vec(ring.cx, ring.cy);
const RING_TRANSFORM = [{ rotate: -Math.PI / 2 }];
const PLAYHEAD_COLORS = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.7)', 'rgba(255,255,255,0)'];

/** The sky behind the card's content (shader). Transparent at rest. */
export function NightSkyBackdrop({ scale, values }: { scale: number; values: NightSkyValues }) {
  const uniforms = useDerivedValue<Uniforms>(() => ({
    res: [CARD.width, CARD.height], night: values.night.get(), phase: values.phase.get(), clock: values.clock.get(),
    meteor: values.meteor.get(), twinkle: values.twinkle.get(),
  }));
  const opacity = useDerivedValue(() => (values.night.get() > 0.0005 ? 1 : 0));
  if (!NIGHT_SLEEP_SCORE_SKY_EFFECT) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Group transform={[{ scale }]} opacity={opacity}>
        <Rect x={0} y={0} width={CARD.width} height={CARD.height}>
          <Shader source={NIGHT_SLEEP_SCORE_SKY_EFFECT} uniforms={uniforms} />
        </Rect>
      </Group>
    </Canvas>
  );
}

/** The night ring, the aurora hypnogram and the playhead, drawn over the day chart and ring while they fade. */
export function NightSkyOverlay({ scale, values }: { scale: number; values: NightSkyValues }) {
  const { boundaries, halfWidths, levels, nightColors } = NIGHT_SLEEP_SCORE_RIBBON;
  const night = values.night;
  const rayUniforms = useDerivedValue<Uniforms>(() => ({ clock: values.clock.get(), edge: chart.left + chart.width * values.reveal.get() }));
  const revealClip = useDerivedValue(() => {
    const x = chart.left + chart.width * values.reveal.get();
    return Skia.XYWHRect(chart.left - 12, chart.top - 34, Math.max(0, x - chart.left + 12), chart.height + 46);
  });
  const ringEnd = useDerivedValue(() => values.reveal.get());
  const playheadX = useDerivedValue(() => chart.left + chart.width * values.cursor.get());
  const headY = useDerivedValue(() => ribbonYNightSleepScore(playheadX.get(), boundaries, halfWidths, levels));
  const headColor = useDerivedValue(() => {
    const x = playheadX.get(); let i = 0;
    while (i < boundaries.length && x > boundaries[i]) i += 1;
    return nightColors[i];
  });
  const playheadOpacity = useDerivedValue(() => values.presence.get());
  const playheadTop = useDerivedValue(() => vec(playheadX.get(), chart.top - 2));
  const playheadBottom = useDerivedValue(() => vec(playheadX.get(), chart.top + chart.height + 2));
  const playheadRect = useDerivedValue(() => Skia.XYWHRect(playheadX.get() - A.playheadWidth / 2, chart.top - 2, A.playheadWidth, chart.height + 4));
  const ringHead = useDerivedValue(() => {
    const turn = values.reveal.get() * Math.PI * 2 - Math.PI / 2;
    return vec(ring.cx + ring.radius * Math.cos(turn), ring.cy + ring.radius * Math.sin(turn));
  });
  const ringHeadOpacity = useDerivedValue(() => {
    const r = values.reveal.get();
    return Math.min(1, r * 30) * (1 - Math.min(1, Math.max(0, (r - .97) / .03)));
  });
  const ringHeadColor = useDerivedValue(() => headColor.get());
  const ringOpacity = useDerivedValue(() => nightAtNightSleepScore(night.get(), CARD.at.ring));
  const chartOpacity = useDerivedValue(() => nightAtNightSleepScore(night.get(), CARD.at.chart));

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Group transform={[{ scale }]}>
        <Group opacity={ringOpacity}>
        {/* Night ring: the night as a clock, filling with the stage colours as the playhead passes. */}
        <Circle cx={ring.cx} cy={ring.cy} r={ring.radius} style="stroke" strokeWidth={ring.stroke} color={COLORS.night.trackRing} />
        <Path path={RING_PATH} style="stroke" strokeWidth={ring.stroke} strokeCap="butt" end={ringEnd}>
          <SweepGradient c={RING_CENTRE} colors={NIGHT_SLEEP_SCORE_RING_GRADIENT.colors} positions={NIGHT_SLEEP_SCORE_RING_GRADIENT.positions} transform={RING_TRANSFORM} origin={RING_CENTRE} />
        </Path>
        <Group opacity={ringHeadOpacity}>
          <Circle c={ringHead} r={A.head.glow + 3} color={ringHeadColor}><Blur blur={A.head.glowBlur + 2} /></Circle>
          <Circle c={ringHead} r={A.head.core + 1} color={COLORS.night.playhead} />
        </Group>
        </Group>

        <Group opacity={chartOpacity}>
        {/* Aurora curtain: blurred, lifted copies of the ribbon cut into rays; the mask also fades it in softly at the playhead. */}
          <Group layer>
            {A.curtain.map((layer) => (
              <Path key={layer.lift} path={NIGHT_SLEEP_SCORE_RIBBON_PATH} style="stroke" strokeWidth={layer.width} strokeJoin="round"
                opacity={layer.opacity} transform={[{ translateY: -layer.lift }]}>
                <LinearGradient start={RIBBON_START} end={RIBBON_END} colors={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.colors} positions={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.positions} />
                <Blur blur={layer.blur} />
              </Path>
            ))}
            {NIGHT_SLEEP_SCORE_RAYS_EFFECT && (
              <Rect x={chart.left - 12} y={chart.top - 34} width={chart.width + 24} height={chart.height + 46} blendMode="dstIn">
                <Shader source={NIGHT_SLEEP_SCORE_RAYS_EFFECT} uniforms={rayUniforms} />
              </Rect>
            )}
          </Group>
        {/* Aurora ribbon, drawn up to the playhead. */}
        <Group clip={revealClip}>
          <Path path={NIGHT_SLEEP_SCORE_RIBBON_PATH} style="stroke" strokeWidth={A.glow.width} strokeJoin="round" strokeCap="round" opacity={A.glow.opacity}>
            <LinearGradient start={RIBBON_START} end={RIBBON_END} colors={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.colors} positions={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.positions} />
            <Blur blur={A.glow.blur} />
          </Path>
          <Path path={NIGHT_SLEEP_SCORE_RIBBON_PATH} style="stroke" strokeWidth={A.core} strokeJoin="round" strokeCap="round">
            <LinearGradient start={RIBBON_START} end={RIBBON_END} colors={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.colors} positions={NIGHT_SLEEP_SCORE_RIBBON_GRADIENT.positions} />
          </Path>
          <Path path={NIGHT_SLEEP_SCORE_RIBBON_PATH} style="stroke" strokeWidth={A.hotCore} strokeJoin="round" strokeCap="round" color="#FFFFFF" opacity={A.hotCoreOpacity} />
        </Group>

        {/* Playhead and its light where it meets the ribbon. */}
        <Group opacity={playheadOpacity}>
          <Rect rect={playheadRect}>
            <LinearGradient start={playheadTop} end={playheadBottom} colors={PLAYHEAD_COLORS} />
          </Rect>
          <Circle cx={playheadX} cy={headY} r={A.head.glow} color={headColor}><Blur blur={A.head.glowBlur} /></Circle>
          <Circle cx={playheadX} cy={headY} r={A.head.core} color={COLORS.night.playhead} />
        </Group>
        </Group>
      </Group>
    </Canvas>
  );
}
