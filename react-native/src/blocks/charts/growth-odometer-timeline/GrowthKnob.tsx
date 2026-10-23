import { BlurMask, Circle, Group, Path, RadialGradient, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { type GrowthState, visuals } from './growth-sim';
import { type Metrics } from './growth-theme';

const KNOB_RADIUS_PT = 20;
const KNOB_DIAMETER_PT = 40;
const KNOB_RING_RADIUS_PT = 34;
const KNOB_RING_SOLID_STOP = 0.59;
const KNOB_LIGHT_OFFSET_X_PT = 10;
const KNOB_LIGHT_OFFSET_Y_PT = 12;
const KNOB_LIGHT_RADIUS_PT = 42;
const KNOB_PRESS_SCALE = 0.04;
const RIM_BASE_ALPHA = 0.08;
const RIM_HALO_GAIN = 0.5;
const RIM_STROKE_PT = 0.7;
const EDGE_STROKE_PT = 1;
const CHEVRON_OFFSET_Y_PT = 7;
const CHEVRON_SPACING_PT = 5.5;
const CHEVRON_HALF_WIDTH_PT = 6;
const CHEVRON_BOTTOM_PT = 3;
const CHEVRON_TOP_PT = 2;
const CHEVRON_COUNT = 3;
const CHEVRON_PULSE_POWER = 8;
const CHEVRON_IDLE_ALPHA = 0.22;
const CHEVRON_PULSE_ALPHA = 0.78;
const CHEVRON_HERO_SECONDS = 0.6;
const CHEVRON_REDUCED_HERO_SECONDS = 0.2;
const CHEVRON_GLOW_STROKE_PT = 2.4;
const CHEVRON_STROKE_PT = 1.75;
const CHEVRON_BLUR_PT = 2;
const ARC_DEGREES = { top: [200, 105], bottom: [20, 100], left: [155, 55], right: [-35, 70] };

type Props = { layout: Metrics; state: SharedValue<GrowthState> };

function Chevron({ index, layout, state }: Props & { index: number }) {
  const path = useMemo(() => {
    const y = layout.cy - CHEVRON_OFFSET_Y_PT + index * CHEVRON_SPACING_PT;
    return Skia.PathBuilder.Make()
      .moveTo(layout.knobX - CHEVRON_HALF_WIDTH_PT, y + CHEVRON_BOTTOM_PT)
      .lineTo(layout.knobX, y - CHEVRON_TOP_PT)
      .lineTo(layout.knobX + CHEVRON_HALF_WIDTH_PT, y + CHEVRON_BOTTOM_PT)
      .build();
  }, [index, layout]);
  const brightness = useDerivedValue(() => {
    const wheel = state.get();
    const effects = visuals(wheel);
    const phase = ((effects.phase + index / CHEVRON_COUNT) % 1 + 1) % 1;
    const pulse = Math.pow(Math.max(0, Math.cos(phase * Math.PI * 2)), CHEVRON_PULSE_POWER);
    const flareDuration = wheel.reduced ? CHEVRON_REDUCED_HERO_SECONDS : CHEVRON_HERO_SECONDS;
    return wheel.pressed || effects.age < flareDuration
      ? 1 : CHEVRON_IDLE_ALPHA + CHEVRON_PULSE_ALPHA * pulse;
  });
  return <Group>
    <Path
      path={path}
      color="#B8F53E"
      style="stroke"
      strokeWidth={CHEVRON_GLOW_STROKE_PT}
      opacity={brightness}
    >
      <BlurMask blur={CHEVRON_BLUR_PT} style="normal" />
    </Path>
    <Path
      path={path}
      color="#B8F53E"
      style="stroke"
      strokeWidth={CHEVRON_STROKE_PT}
      strokeCap="round"
      strokeJoin="round"
      opacity={brightness}
    />
  </Group>;
}

/** The knob is a fixed machined disc; only its press scale and reflected light animate. */
export function GrowthKnob({ layout, state }: Props) {
  const transform = useDerivedValue(() => [{ scale: 1 - KNOB_PRESS_SCALE * state.get().press }]);
  const rim = useDerivedValue(() => RIM_BASE_ALPHA + RIM_HALO_GAIN * state.get().halo);
  const arcs = useMemo(() => {
    const bounds = Skia.XYWHRect(
      layout.knobX - KNOB_RADIUS_PT, layout.cy - KNOB_RADIUS_PT, KNOB_DIAMETER_PT, KNOB_DIAMETER_PT,
    );
    return {
      top: Skia.PathBuilder.Make().addArc(bounds, ARC_DEGREES.top[0], ARC_DEGREES.top[1]).build(),
      bottom: Skia.PathBuilder.Make().addArc(bounds, ARC_DEGREES.bottom[0], ARC_DEGREES.bottom[1]).build(),
      left: Skia.PathBuilder.Make().addArc(bounds, ARC_DEGREES.left[0], ARC_DEGREES.left[1]).build(),
      right: Skia.PathBuilder.Make().addArc(bounds, ARC_DEGREES.right[0], ARC_DEGREES.right[1]).build(),
    };
  }, [layout]);
  const center = { x: layout.knobX, y: layout.cy };
  return <Group origin={center} transform={transform}>
    <Circle cx={layout.knobX} cy={layout.cy} r={KNOB_RING_RADIUS_PT}>
      <RadialGradient
        c={center}
        r={KNOB_RING_RADIUS_PT}
        colors={['#161C24E6', '#161C24E6', '#161C2400']}
        positions={[0, KNOB_RING_SOLID_STOP, 1]}
      />
    </Circle>
    <Circle cx={layout.knobX} cy={layout.cy} r={KNOB_RADIUS_PT}>
      <RadialGradient
        c={{ x: layout.knobX - KNOB_LIGHT_OFFSET_X_PT, y: layout.cy - KNOB_LIGHT_OFFSET_Y_PT }}
        r={KNOB_LIGHT_RADIUS_PT} colors={['#252C36', '#141920']}
      />
    </Circle>
    <Path
      path={arcs.top}
      color="#FFFFFF1A"
      style="stroke"
      strokeWidth={EDGE_STROKE_PT}
    />
    <Path
      path={arcs.bottom}
      color="#05080B88"
      style="stroke"
      strokeWidth={EDGE_STROKE_PT}
    />
    <Path
      path={arcs.left}
      color="#A8F03C40"
      style="stroke"
      strokeWidth={RIM_STROKE_PT}
    />
    <Path
      path={arcs.right}
      color="#E5FF70"
      opacity={rim}
      style="stroke"
      strokeWidth={RIM_STROKE_PT}
    />
    <Chevron index={0} layout={layout} state={state} />
    <Chevron index={1} layout={layout} state={state} />
    <Chevron index={2} layout={layout} state={state} />
  </Group>;
}
