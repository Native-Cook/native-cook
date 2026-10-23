import {
  BlurMask, Canvas, Group, ImageShader, LinearGradient, Path, Rect, Shader, Skia, useCanvasRef, useImage,
} from '@shopify/react-native-skia';
import { useEffect, useMemo } from 'react';
import { PixelRatio } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { GrowthKnob } from './GrowthKnob';
import { GrowthMotes } from './GrowthMotes';
import {
  numberUniforms, ODOMETER_EFFECT, stageLayoutUniforms, stageUniforms, STAGE_EFFECT,
} from './growth-shaders';
import { clamp, smooth, visuals, type GrowthState } from './growth-sim';
import { MONTH_SPACING } from './growth-physics';
import { railAt, type Metrics } from './growth-theme';

const DPR = PixelRatio.get();
const RAIL_SEGMENT_PT = 2;
const RAIL_STROKE_PT = 1.5;
const RAIL_GLOW_STROKE_PT = 5;
const RAIL_BLUR_PT = 6;
const RAIL_GLOW_RADIUS_PT = 100;
const RAIL_EDGE_FADE_PT = 40;
const RAIL_COLOR_RADIUS_PT = 120;
const RAIL_GLOW_ALPHA = 0.6;
const GRIP_LIGHT_GAIN = 0.35;
const TICK_LIGHT_GAIN = 0.4;
const TICK_INTERVAL_COUNT = 60;
const TICKS_PER_MONTH = 5;
const TICK_INSET_PT = 16;
const MAJOR_TICK_LENGTH_PT = 14;
const MINOR_TICK_LENGTH_PT = 7;
const TICK_STROKE_PT = 1;
const MINOR_TICK_ALPHA = 0.35;
const TICK_EDGE_FADE_PT = 72;
const TICK_COLOR_RADIUS_PT = 70;
const HERO_RAIL_DELAY_SECONDS = 0.3;
const HERO_RAIL_EXPAND_SECONDS = 0.5;
const HERO_RAIL_FADE_START_SECONDS = 0.5;
const HERO_RAIL_FADE_END_SECONDS = 1.3;
const HERO_RAIL_ATTACK_SECONDS = 0.08;
const HERO_RAIL_MIN_RADIUS_PT = 1;
const HERO_RAIL_GLOW_STROKE_PT = 4;
const HERO_RAIL_BLUR_PT = 5;
const HERO_RAIL_STOPS = [0, 0.12, 0.88, 1];
const NUMBER_HALF_HEIGHT_PT = 26;
const NUMBER_WIDTH_PT = 122;
const NUMBER_HEIGHT_PT = 52;
const RAIL_COLORS = [
  '#27323A00', '#27323A80', '#5E8F2A8C', '#A8F03C', '#5E8F2A8C', '#27323A80', '#27323A00',
];
const TICK_COLORS = [
  '#39424A00', '#39424A73', '#74806A80', '#A8F03CE6', '#74806A80', '#39424A73', '#39424A00',
];
const GLOW_COLORS = ['#A8F03C00', '#A8F03C', '#A8F03C00'];
const HERO_RAIL_COLORS = ['#CAFF6300', '#CAFF63', '#CAFF63', '#CAFF6300'];

type Props = { m: Metrics; state: SharedValue<GrowthState>; onReady: (ready: boolean) => void };

export function GrowthCanvas({ m: layout, state, onReady }: Props) {
  const digits = useImage(require('./assets/digits.png'));
  const grain = useImage(require('./assets/grain.png'));
  const mote = useImage(require('./assets/mote.png'));
  const canvasRef = useCanvasRef();
  const loaded = !!(digits && grain && mote);
  useEffect(() => {
    let cancelled = false;
    let secondFrame = 0;
    onReady(false);
    // A completed GPU frame gates both native overlays and the simulation's first tick.
    const firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(() => {
        if (!loaded || cancelled) return;
        void canvasRef.current?.makeImageSnapshotAsync().then(snapshot => {
          snapshot.dispose();
          if (!cancelled) onReady(true);
        }).catch(() => {
          // Keep overlays hidden if the surface detached during layout.
        });
      });
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(firstFrame);
      cancelAnimationFrame(secondFrame);
    };
  }, [canvasRef, loaded, layout.width, layout.height, onReady]);

  const rail = useMemo(() => {
    const builder = Skia.PathBuilder.Make();
    builder.moveTo(layout.railX, 0);
    for (let y = RAIL_SEGMENT_PT; y <= layout.height; y += RAIL_SEGMENT_PT) {
      builder.lineTo(railAt(y, layout), y);
    }
    builder.lineTo(layout.railX, layout.height);
    return builder.build();
  }, [layout]);
  const ticks = useDerivedValue(() => {
    const wheel = state.get();
    const major = Skia.PathBuilder.Make();
    const minor = Skia.PathBuilder.Make();
    for (let index = 0; index <= TICK_INTERVAL_COUNT; index++) {
      const y = layout.cy + (index / TICKS_PER_MONTH - wheel.p) * MONTH_SPACING;
      if (y < 0 || y > layout.height) continue;
      const right = railAt(y, layout) - TICK_INSET_PT;
      const isMajor = index % TICKS_PER_MONTH === 0;
      const builder = isMajor ? major : minor;
      builder.moveTo(right - (isMajor ? MAJOR_TICK_LENGTH_PT : MINOR_TICK_LENGTH_PT), y).lineTo(right, y);
    }
    return { major: major.build(), minor: minor.build() };
  });
  const majors = useDerivedValue(() => ticks.get().major);
  const minors = useDerivedValue(() => ticks.get().minor);
  const stageLayout = useMemo(() => stageLayoutUniforms(layout, DPR), [layout]);
  const stage = useDerivedValue(() => stageUniforms(state.get(), stageLayout));
  const number = useDerivedValue(() => numberUniforms(state.get(), layout, DPR));
  const glow = useDerivedValue(() => {
    const wheel = state.get();
    const effects = visuals(wheel);
    return RAIL_GLOW_ALPHA * (1 + GRIP_LIGHT_GAIN * wheel.grip + TICK_LIGHT_GAIN * effects.tick);
  });
  const tickAlpha = useDerivedValue(() =>
    1 + GRIP_LIGHT_GAIN * state.get().grip + TICK_LIGHT_GAIN * visuals(state.get()).tick,
  );
  const heroRail = useDerivedValue(() => {
    // The wave reaches the rail 0.3 seconds after landing; ignition expands from the notch.
    const wheel = state.get();
    const heroAge = visuals(wheel).age - HERO_RAIL_DELAY_SECONDS;
    return wheel.reduced ? 0
      : (1 - smooth(HERO_RAIL_FADE_START_SECONDS, HERO_RAIL_FADE_END_SECONDS, heroAge))
        * smooth(0, HERO_RAIL_ATTACK_SECONDS, heroAge);
  });
  const heroStart = useDerivedValue(() => {
    const radius = Math.max(
      HERO_RAIL_MIN_RADIUS_PT,
      clamp((visuals(state.get()).age - HERO_RAIL_DELAY_SECONDS) / HERO_RAIL_EXPAND_SECONDS, 0, 1)
        * Math.max(layout.cy, layout.height - layout.cy),
    );
    return { x: 0, y: layout.cy - radius };
  });
  const heroEnd = useDerivedValue(() => ({ x: 0, y: 2 * layout.cy - heroStart.get().y }));
  const railPositions = [
    0, RAIL_EDGE_FADE_PT / layout.height, (layout.cy - RAIL_COLOR_RADIUS_PT) / layout.height,
    layout.cy / layout.height, (layout.cy + RAIL_COLOR_RADIUS_PT) / layout.height,
    1 - RAIL_EDGE_FADE_PT / layout.height, 1,
  ];
  const tickPositions = [
    0, TICK_EDGE_FADE_PT / layout.height, (layout.cy - TICK_COLOR_RADIUS_PT) / layout.height,
    layout.cy / layout.height, (layout.cy + TICK_COLOR_RADIUS_PT) / layout.height,
    1 - TICK_EDGE_FADE_PT / layout.height, 1,
  ];
  const top = { x: 0, y: 0 };
  const bottom = { x: 0, y: layout.height };
  if (!loaded || !STAGE_EFFECT || !ODOMETER_EFFECT) return null;
  return <Canvas ref={canvasRef} style={{ width: layout.width, height: layout.height }} pointerEvents="none">
    <Rect
      x={0}
      y={0}
      width={layout.width}
      height={layout.height}
    >
      <Shader source={STAGE_EFFECT} uniforms={stage}>
        <ImageShader
          image={grain}
          fit="none"
          tx="repeat"
          ty="repeat"
        />
      </Shader>
    </Rect>
    <Path
      path={rail}
      style="stroke"
      strokeWidth={RAIL_GLOW_STROKE_PT}
      opacity={glow}
    >
      <LinearGradient
        start={{ x: 0, y: layout.cy - RAIL_GLOW_RADIUS_PT }}
        end={{ x: 0, y: layout.cy + RAIL_GLOW_RADIUS_PT }} colors={GLOW_COLORS}
      />
      <BlurMask blur={RAIL_BLUR_PT} style="normal" />
    </Path>
    <Path
      path={rail}
      style="stroke"
      strokeWidth={RAIL_STROKE_PT}
      strokeCap="round"
    >
      <LinearGradient
        start={top}
        end={bottom}
        positions={railPositions}
        colors={RAIL_COLORS}
      />
    </Path>
    <Group opacity={heroRail}>
      <Path path={rail} style="stroke" strokeWidth={RAIL_STROKE_PT}>
        <LinearGradient
          start={heroStart}
          end={heroEnd}
          colors={HERO_RAIL_COLORS}
          positions={HERO_RAIL_STOPS}
        />
      </Path>
      <Path path={rail} style="stroke" strokeWidth={HERO_RAIL_GLOW_STROKE_PT}>
        <LinearGradient start={heroStart} end={heroEnd} colors={GLOW_COLORS} />
        <BlurMask blur={HERO_RAIL_BLUR_PT} style="normal" />
      </Path>
    </Group>
    <Group opacity={tickAlpha}>
      <Path path={majors} style="stroke" strokeWidth={TICK_STROKE_PT}>
        <LinearGradient
          start={top}
          end={bottom}
          positions={tickPositions}
          colors={TICK_COLORS}
        />
      </Path>
      <Path
        path={minors}
        style="stroke"
        strokeWidth={TICK_STROKE_PT}
        opacity={MINOR_TICK_ALPHA}
      >
        <LinearGradient
          start={top}
          end={bottom}
          positions={tickPositions}
          colors={TICK_COLORS}
        />
      </Path>
    </Group>
    <GrowthKnob layout={layout} state={state} />
    <GrowthMotes image={mote!} m={layout} state={state} />
    <Rect
      x={layout.x0}
      y={layout.cy - NUMBER_HALF_HEIGHT_PT}
      width={NUMBER_WIDTH_PT}
      height={NUMBER_HEIGHT_PT}
      blendMode="plus"
    >
      <Shader source={ODOMETER_EFFECT} uniforms={number}>
        <ImageShader
          image={digits}
          fit="none"
          tx="clamp"
          ty="repeat"
        />
      </Shader>
    </Rect>
  </Canvas>;
}
