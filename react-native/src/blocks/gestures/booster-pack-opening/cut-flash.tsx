import {
  BlurMask,
  Group,
  LinearGradient,
  Oval,
  Path,
  Skia,
  SweepGradient,
  vec,
  type SkPath,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { clamp, interpolateKeyframes } from './model';

export type CutFlashProps = Readonly<{
  width: number;
  height: number;
  seamY: number;
  time: SharedValue<number>;
  committed: SharedValue<number>;
  packetMask?: SharedValue<SkPath>;
  reducedMotion?: boolean;
}>;

// The reference video's ring is a single-frame event at f124. The horizontal cut stays
// visible afterward, narrowing from 22 px at f125 to a fine line at f133.
const BEAM_TIMES = [0, 16.667, 33.333, 50, 66.667, 100, 150, 220];
const BEAM_WIDTHS = [0.083, 0.056, 0.04, 0.038, 0.029, 0.012, 0.0045, 0.0045];
const SPECTRUM = [
  '#A9FFFD',
  '#EFC8FF',
  '#F9DCFF',
  '#94FFFF',
  '#B7FFFF',
  '#F8BCFF',
  '#FF87ED',
  '#A9FFFD',
];
const SPECTRUM_POSITIONS = [0, 0.12, 0.25, 0.4, 0.5, 0.69, 0.84, 1];

function makeRing(
  centerX: number,
  centerY: number,
  radiusX: number,
  topRadius: number,
  bottomRadius: number,
) {
  const path = Skia.PathBuilder.Make();
  const arc = 0.55228475;
  path.moveTo(centerX - radiusX, centerY);
  path.cubicTo(
    centerX - radiusX, centerY - topRadius * arc,
    centerX - radiusX * arc, centerY - topRadius,
    centerX, centerY - topRadius,
  );
  path.cubicTo(
    centerX + radiusX * arc, centerY - topRadius,
    centerX + radiusX, centerY - topRadius * arc,
    centerX + radiusX, centerY,
  );
  path.cubicTo(
    centerX + radiusX, centerY + bottomRadius * arc,
    centerX + radiusX * arc, centerY + bottomRadius,
    centerX, centerY + bottomRadius,
  );
  path.cubicTo(
    centerX - radiusX * arc, centerY + bottomRadius,
    centerX - radiusX, centerY + bottomRadius * arc,
    centerX - radiusX, centerY,
  );
  path.close();
  return path.build();
}

function makeRingRibbon(width: number, height: number, cutY: number) {
  // At f124 the far rim is only a thin arc above the print; the near and side
  // rims spread into a much wider flare. A constant-width ellipse hides that
  // depth cue and washes out the printed cap.
  const path = Skia.PathBuilder.Make();
  path.moveTo(-width * 0.028, cutY);
  path.cubicTo(-width * 0.026, cutY - height * 0.064,
    width * 0.19, cutY - height * 0.108, width * 0.498, cutY - height * 0.108);
  path.cubicTo(width * 0.81, cutY - height * 0.108,
    width * 1.026, cutY - height * 0.06, width * 1.028, cutY + height * 0.002);
  path.cubicTo(width * 1.026, cutY + height * 0.054,
    width * 0.756, cutY + height * 0.076, width * 0.508, cutY + height * 0.07);
  path.cubicTo(width * 0.254, cutY + height * 0.07,
    -width * 0.026, cutY + height * 0.051, -width * 0.028, cutY);
  path.close();
  path.moveTo(width * 0.069, cutY);
  path.cubicTo(width * 0.073, cutY + height * 0.037,
    width * 0.282, cutY + height * 0.054, width * 0.506, cutY + height * 0.053);
  path.cubicTo(width * 0.738, cutY + height * 0.058,
    width * 0.93, cutY + height * 0.039, width * 0.931, cutY + height * 0.001);
  path.cubicTo(width * 0.929, cutY - height * 0.052,
    width * 0.793, cutY - height * 0.102, width * 0.499, cutY - height * 0.102);
  path.cubicTo(width * 0.216, cutY - height * 0.102,
    width * 0.074, cutY - height * 0.057, width * 0.069, cutY);
  path.close();
  return path.build();
}

export function CutFlash({
  width,
  height,
  seamY,
  time,
  committed,
  packetMask,
  reducedMotion = false,
}: CutFlashProps) {
  const cutY = seamY - height * 0.003;
  const center = useMemo(() => vec(width * 0.5, cutY), [cutY, width]);
  const beam = useDerivedValue(() => {
    const inset = reducedMotion
      ? 0
      : width * 0.11 * (1 - clamp(time.get() / 16.667, 0, 1));
    const path = Skia.PathBuilder.Make();
    path.moveTo(inset, cutY);
    path.lineTo(width - inset, cutY);
    return path.build();
  });
  const rings = useMemo(() => ({
    outer: makeRing(width * 0.5, cutY, width * 0.528, height * 0.105, height * 0.088),
    main: makeRingRibbon(width, height, cutY),
  }), [cutY, height, width]);
  const refractedFlare = useMemo(() => {
    // Light refracts through the torn packet in an uneven ribbon. Its lower
    // edge carries the warm-to-cyan diffraction visible around the artwork.
    const path = Skia.PathBuilder.Make();
    path.moveTo(width * 0.124, cutY - height * 0.012);
    path.cubicTo(
      width * 0.24, cutY - height * 0.057,
      width * 0.43, cutY - height * 0.059,
      width * 0.54, cutY - height * 0.052,
    );
    path.cubicTo(
      width * 0.72, cutY - height * 0.055,
      width * 0.8, cutY - height * 0.027,
      width * 0.87, cutY - height * 0.012,
    );
    path.lineTo(width * 0.862, cutY + height * 0.043);
    path.lineTo(width * 0.834, cutY + height * 0.07);
    path.lineTo(width * 0.816, cutY + height * 0.032);
    path.lineTo(width * 0.795, cutY + height * 0.043);
    path.lineTo(width * 0.774, cutY + height * 0.073);
    path.lineTo(width * 0.742, cutY + height * 0.08);
    path.lineTo(width * 0.725, cutY + height * 0.065);
    path.lineTo(width * 0.695, cutY + height * 0.052);
    path.lineTo(width * 0.653, cutY + height * 0.029);
    path.cubicTo(
      width * 0.625, cutY + height * 0.053,
      width * 0.606, cutY + height * 0.068,
      width * 0.576, cutY + height * 0.072,
    );
    path.lineTo(width * 0.554, cutY + height * 0.089);
    path.cubicTo(
      width * 0.509, cutY + height * 0.108,
      width * 0.459, cutY + height * 0.108,
      width * 0.44, cutY + height * 0.089,
    );
    path.lineTo(width * 0.431, cutY + height * 0.076);
    path.lineTo(width * 0.397, cutY + height * 0.083);
    path.lineTo(width * 0.352, cutY + height * 0.075);
    path.lineTo(width * 0.31, cutY + height * 0.072);
    path.lineTo(width * 0.286, cutY + height * 0.077);
    path.lineTo(width * 0.236, cutY + height * 0.064);
    path.lineTo(width * 0.218, cutY + height * 0.059);
    path.lineTo(width * 0.168, cutY + height * 0.057);
    path.lineTo(width * 0.137, cutY + height * 0.041);
    path.close();
    return path.build();
  }, [cutY, height, width]);
  const bounds = useMemo(
    () => ({ x: 0, y: cutY - height * 0.14, width, height: height * 0.28 }),
    [cutY, height, width],
  );
  const packetOcclusion = useMemo(
    () => ({
      x: width * 0.132,
      y: cutY - height * 0.097,
      width: width * 0.736,
      height,
    }),
    [cutY, height, width],
  );

  const ringOpacity = useDerivedValue(() =>
    clamp(committed.get(), 0, 1) *
    (reducedMotion ? 0.18 : 1) *
    interpolateKeyframes(time.get(), [0, 8.333, 16.667], [1, 0.38, 0]),
  );
  const ringTransform = useDerivedValue(() => {
    const travel = reducedMotion ? 0 : clamp(time.get() / 16.667, 0, 1);
    return [
      { translateY: -height * 0.002 * travel },
      { scaleX: 1 + travel * 0.06 },
      { scaleY: 1 + travel * 0.12 },
    ];
  });
  const beamWidth = useDerivedValue(() =>
    width *
    (reducedMotion ? 0.35 : 1) *
    interpolateKeyframes(time.get(), BEAM_TIMES, BEAM_WIDTHS),
  );
  const beamEdgeWidth = useDerivedValue(() => beamWidth.get() + width * 0.008);
  const beamGlowWidth = useDerivedValue(() => beamWidth.get() + width * 0.022);
  const beamBloomWidth = useDerivedValue(() => beamWidth.get() + width * 0.062);
  const beamCoreBlur = useDerivedValue(() =>
    width * interpolateKeyframes(time.get(), [0, 16.667, 66.667], [0.012, 0.002, 0.0008]),
  );
  const beamEdgeBlur = useDerivedValue(() =>
    width * interpolateKeyframes(time.get(), [0, 16.667], [0.016, 0.002]),
  );
  const beamBloomOpacity = useDerivedValue(() =>
    reducedMotion ? 0 : ringOpacity.get() * 0.92,
  );
  const beamOpacity = useDerivedValue(() =>
    clamp(committed.get(), 0, 1) *
    (reducedMotion ? 0.45 : 1) *
    interpolateKeyframes(time.get(), [0, 150, 183.333, 220], [1, 1, 0.55, 0]),
  );
  const glowOpacity = useDerivedValue(() =>
    clamp(committed.get(), 0, 1) *
    (reducedMotion ? 0.08 : 0.75) *
    interpolateKeyframes(time.get(), [0, 16.667, 66.667, 150], [1, 0.4, 0.18, 0]),
  );
  const beamEdgeOpacity = useDerivedValue(() =>
    beamOpacity.get() *
    interpolateKeyframes(time.get(), [0, 66.667, 150], [1, 0.55, 0]),
  );
  const warmEdgeOpacity = useDerivedValue(() =>
    beamEdgeOpacity.get() *
    interpolateKeyframes(time.get(), [0, 16.667, 50, 66.667], [1, 0.8, 0.15, 0]),
  );
  const beamGradientStart = useDerivedValue(() =>
    vec(0, cutY - beamEdgeWidth.get() * 0.5),
  );
  const beamGradientEnd = useDerivedValue(() =>
    vec(0, cutY + beamEdgeWidth.get() * 0.5),
  );

  return (
    <Group clip={bounds}>
      <Group opacity={ringOpacity} origin={center} transform={ringTransform}>
        {reducedMotion ? (
          <Oval
            x={width * 0.06}
            y={cutY - height * 0.078}
            width={width * 0.88}
            height={height * 0.156}
            style="stroke"
            strokeWidth={width * 0.012}
            color="#B6FFFA">
            <BlurMask blur={width * 0.009} style="normal" />
          </Oval>
        ) : (
          <>
            {/* The packet occludes the rear arc; refracted light below is a
                separate bloom, so the front never becomes a white hoop. */}
            <Group clip={packetMask ?? packetOcclusion} invertClip>
              <Path
                path={rings.outer}
                style="stroke"
                strokeWidth={width * 0.028}>
                <SweepGradient c={center} colors={SPECTRUM} positions={SPECTRUM_POSITIONS} />
                <BlurMask blur={width * 0.012} style="normal" />
              </Path>
              <Path
                path={rings.outer}
                style="stroke"
                strokeWidth={width * 0.011}>
                <SweepGradient c={center} colors={SPECTRUM} positions={SPECTRUM_POSITIONS} />
                <BlurMask blur={width * 0.006} style="normal" />
              </Path>
              <Path
                path={rings.main}
                style="fill"
                color="#E9FFF8">
                <BlurMask blur={width * 0.009} style="normal" />
              </Path>
              <Path path={rings.main} style="fill" color="#FFFFFF">
                <BlurMask blur={width * 0.004} style="normal" />
              </Path>
            </Group>
            <Oval
              x={width * 0.23}
              y={cutY - height * 0.09}
              width={width * 0.54}
              height={height * 0.069}
              color="#FFFFBC"
              opacity={0.87}
              blendMode="screen">
              <BlurMask blur={width * 0.04} style="normal" />
            </Oval>
            <Group transform={[{ translateY: -height * 0.008 }]}>
              <Path path={refractedFlare} style="fill" color="#FFFFF8" opacity={0.9}>
                <BlurMask blur={width * 0.014} style="normal" />
              </Path>
            </Group>
            <Path path={refractedFlare} style="fill">
              <LinearGradient
                start={vec(0, cutY - height * 0.061)}
                end={vec(0, cutY + height * 0.108)}
                colors={[
                  'rgba(255,89,191,0)',
                  'rgba(255,148,106,0.32)',
                  'rgba(255,220,133,0.92)',
                  '#FFFFFF',
                  '#FFFFFF',
                  'rgba(255,255,212,0.98)',
                  'rgba(255,251,99,0.93)',
                  'rgba(116,255,167,0.82)',
                  'rgba(97,223,255,0.74)',
                  'rgba(112,135,255,0)',
                ]}
                positions={[0, 0.12, 0.25, 0.36, 0.7, 0.76, 0.82, 0.88, 0.95, 1]}
              />
              <BlurMask blur={width * 0.012} style="normal" />
            </Path>
          </>
        )}
      </Group>

      <Path
        path={beam}
        style="stroke"
        strokeWidth={beamGlowWidth}
        color="#83FFFA"
        opacity={glowOpacity}>
        <BlurMask blur={width * 0.016} style="normal" />
      </Path>
      <Path
        path={beam}
        style="stroke"
        strokeCap="round"
        strokeWidth={beamBloomWidth}
        color="#FFFFED"
        opacity={beamBloomOpacity}>
        <BlurMask blur={width * 0.027} style="normal" />
      </Path>
      <Path
        path={beam}
        style="stroke"
        strokeCap="round"
        strokeWidth={beamEdgeWidth}
        opacity={beamEdgeOpacity}>
        <LinearGradient
          start={beamGradientStart}
          end={beamGradientEnd}
          colors={['#67FFF5', '#D5FFF2', '#FFFFFF', '#DCFFFF', '#87F9FF']}
          positions={[0, 0.18, 0.5, 0.8, 1]}
        />
        <BlurMask blur={beamEdgeBlur} style="normal" />
      </Path>
      <Path
        path={beam}
        style="stroke"
        strokeCap="round"
        strokeWidth={beamEdgeWidth}
        opacity={warmEdgeOpacity}>
        <LinearGradient
          start={beamGradientStart}
          end={beamGradientEnd}
          colors={['#67FFF5', '#D5FFF2', '#FFFFFF', '#FFFFB3', '#FFE66D']}
          positions={[0, 0.18, 0.5, 0.8, 1]}
        />
        <BlurMask blur={beamEdgeBlur} style="normal" />
      </Path>
      <Path
        path={beam}
        style="stroke"
        strokeCap="round"
        strokeWidth={beamWidth}
        color="#FFFFFF"
        opacity={beamOpacity}>
        <BlurMask blur={beamCoreBlur} style="normal" />
      </Path>
    </Group>
  );
}
