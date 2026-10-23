import { Blur, Canvas, Circle, Group, LinearGradient, Path, Rect, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { GLYPH_BOX, PAPERCLIP } from './glyphs';
import {
  DISABLED_ALPHA,
  type Events,
  FLARE_GLOW,
  FLARE_SCALE,
  HALO,
  HALO_ALPHA_HIGH,
  HALO_ALPHA_LOW,
  HALO_ALPHA_STILL,
  HALO_SCALE_HIGH,
  HALO_SCALE_LOW,
  PILL_PRESS,
  badgeTurn,
  drift,
  flare,
  glowLevel,
  pressScale,
  pulse,
  selectOff,
  wave,
} from './motion';

export const ZONE_HEIGHT = 162;
const RADIUS = 28;
const FILL = '#1B1E22';
/** Room around the zone in its canvas, for the ring that leaves the badge. */
const PAD = 16;
// Diagonal hairlines "/" at 45°, 10 pt apart (so 10·√2 along a row), drifting to the right.
const HAIRLINE_STEP = 10 * Math.SQRT2;
const HAIRLINE = 'rgba(255,255,255,0.028)';
// The inner glow: the outline drawn as two white strokes, a wide one blurred a lot and a thin one
// blurred a little, kept inside the shape, with a crisp rim; all of it fades to GLOW_TOP at the top.
const GLOW_LAYERS = [
  { stroke: 13, blur: 11 },
  { stroke: 3.3, blur: 3.5 },
];
const GLOW_FADE = ['rgba(255,255,255,0.78)', 'rgba(255,255,255,1)', 'rgba(255,255,255,1)'];
const GLOW_FADE_STOPS = [0, 0.45, 1];
const RIM = 'rgba(255,255,255,0.25)';
// The clip badge, its halo and the ring a new file sends out of it.
const BADGE = 32;
const BADGE_CENTER = 34;
const BADGE_FILL = '#0F1114';
const BADGE_BORDER = '#D9DADC';
const BADGE_BORDER_WIDTH = 1.5;
const BADGE_GLYPH = 17;
const BADGE_GLYPH_STROKE = 1.3;
const HALO_SIZE = 46;
const HALO_BLUR = 8;
const PULSE_STROKE = 1.5;
// The copy and the Select files pill under the badge, stacked with these gaps between their boxes.
const PROMPT_GAP = 12;
const PROMPT_SIZE = 15;
const HINT_GAP = 2;
const HINT_SIZE = 10.5;
const PILL_GAP = 13;
const PILL_WIDTH = 104;
const PILL_HEIGHT = 34;
const PILL_LABEL = 11.5;
const INK = '#F4F4F5';
const HINT = '#7C7F85';
const LINK = '#9A9DA2';
const PILL_FILL = 'rgba(35,38,42,0.85)';
const PILL_BORDER = '#474A4F';

/**
 * A rounded rectangle with the continuous corners iOS draws (the curve starts 1.53 radii from the
 * corner and eases into the arc), so the zone matches the rows and buttons that use `borderCurve`.
 */
export function continuousRect(x: number, y: number, width: number, height: number, radius: number): SkPath {
  const r = Math.min(radius, Math.min(width, height) / 2 / 1.52866483);
  const right = x + width;
  const bottom = y + height;
  // One corner from the edge it comes from to the edge it goes to, in units of r: [along, across].
  const corner: number[][] = [
    [1.08849323, 0, 0.86840689, 0, 0.66993427, 0.065496],
    [0.63149399, 0.074911],
    [0.37282392, 0.16905899, 0.16906013, 0.37282401, 0.07491176, 0.63149399],
    [0.06549569, 0.66993445],
    [0, 0.86840701, 0, 1.08849299, 0, 1.52866483],
  ];
  // Where a point [a, b] of the corner lands for each corner, going clockwise from the top right.
  const maps = [
    (a: number, b: number) => [right - a * r, y + b * r],
    (a: number, b: number) => [right - b * r, bottom - a * r],
    (a: number, b: number) => [x + a * r, bottom - b * r],
    (a: number, b: number) => [x + b * r, y + a * r],
  ];
  const path = Skia.PathBuilder.Make();
  const [sx, sy] = maps[0](1.52866483, 0);
  path.moveTo(sx, sy);
  for (const map of maps) {
    const [lx, ly] = map(1.52866483, 0);
    path.lineTo(lx, ly);
    for (const step of corner) {
      const points = [];
      for (let i = 0; i < step.length; i += 2) points.push(...map(step[i], step[i + 1]));
      if (points.length === 2) path.lineTo(points[0], points[1]);
      else path.cubicTo(points[0], points[1], points[2], points[3], points[4], points[5]);
    }
  }
  path.close();
  return path.build();
}

function hairlines(width: number, height: number) {
  const path = Skia.PathBuilder.Make();
  // From below the left edge to past the right one, so a drift of one step never shows a gap.
  for (let x = -height - HAIRLINE_STEP; x <= width + HAIRLINE_STEP; x += HAIRLINE_STEP) {
    path.moveTo(x, height);
    path.lineTo(x + height, 0);
  }
  return path.build();
}

type Props = {
  width: number;
  clock: SharedValue<number>;
  origin: SharedValue<number>;
  events: SharedValue<Events>;
  still: boolean;
  /** The list is full: Select files is disabled and the zone ignores taps. */
  full: boolean;
  onPillIn: () => void;
  onPillOut: () => void;
  onSelect: () => void;
};

/** The drop zone: glowing rim, drifting hairlines, the clip badge, the copy and Select files. */
export function DropZone({ width, clock, origin, events, still, full, onPillIn, onPillOut, onSelect }: Props) {
  const height = ZONE_HEIGHT;
  const cx = PAD + width / 2;
  const cy = PAD + BADGE_CENTER;
  const paths = useMemo(
    () => ({
      shape: continuousRect(PAD, PAD, width, height, RADIUS),
      rim: continuousRect(PAD + 0.5, PAD + 0.5, width - 1, height - 1, RADIUS - 0.5),
      clip: (() => {
        const m = Skia.Matrix();
        m.translate(PAD + width / 2, PAD + BADGE_CENTER);
        m.scale(BADGE_GLYPH / GLYPH_BOX, BADGE_GLYPH / GLYPH_BOX);
        m.translate(-GLYPH_BOX / 2, -GLYPH_BOX / 2);
        const p = Skia.Path.MakeFromSVGString(PAPERCLIP);
        return p ? p.transform(m) : null;
      })(),
      hairlines: hairlines(width, height),
    }),
    [width, height],
  );

  // The glow breathes; a flare lays a second one over it.
  const glowA = useDerivedValue(() => glowLevel(clock.get() - origin.get(), still));
  const glowB = useDerivedValue(() => FLARE_GLOW * flare(events.get(), clock.get(), still));
  const hairShift = useDerivedValue(() => [
    {
      translateX: PAD + drift(clock.get() - origin.get(), HAIRLINE_STEP, still),
    },
    { translateY: PAD },
  ]);
  const haloWave = useDerivedValue(() => (still ? -1 : wave(clock.get() - origin.get(), HALO)));
  const haloTransform = useDerivedValue(() => {
    const k = haloWave.get();
    return [
      {
        scale: k < 0 ? 1 : HALO_SCALE_LOW + (HALO_SCALE_HIGH - HALO_SCALE_LOW) * k,
      },
    ];
  });
  const haloAlpha = useDerivedValue(() => {
    const k = haloWave.get();
    return k < 0 ? HALO_ALPHA_STILL : HALO_ALPHA_LOW + (HALO_ALPHA_HIGH - HALO_ALPHA_LOW) * k;
  });
  const ring = useDerivedValue(() => pulse(events.get(), clock.get(), still));
  const ringRadius = useDerivedValue(() => (BADGE / 2) * ring.get().scale);
  const ringAlpha = useDerivedValue(() => ring.get().alpha);
  const badgeTransform = useDerivedValue(() => [{ rotate: (badgeTurn(events.get(), clock.get(), still) * Math.PI) / 180 }]);

  const zoneStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + FLARE_SCALE * flare(events.get(), clock.get(), still) }],
  }));
  const pillStyle = useAnimatedStyle(() => {
    const e = events.get();
    return {
      transform: [{ scale: pressScale(e.pillDown, e.pillUp, clock.get(), PILL_PRESS) }],
    };
  });
  const pillDim = useAnimatedStyle(() => ({
    opacity: 1 - (1 - DISABLED_ALPHA) * selectOff(events.get(), clock.get()),
  }));

  return (
    <Animated.View style={[{ width, height }, zoneStyle]}>
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={onSelect}
        disabled={full}
        accessible={false}
        importantForAccessibility="no"
      />
      <Canvas
        style={{
          position: 'absolute',
          left: -PAD,
          top: -PAD,
          width: width + PAD * 2,
          height: height + PAD * 2,
        }}
        pointerEvents="none"
      >
        <Group clip={paths.shape}>
          <Path path={paths.shape} color={FILL} />
          <Group transform={hairShift}>
            <Path path={paths.hairlines} style="stroke" strokeWidth={1} color={HAIRLINE} />
          </Group>
          {[glowA, glowB].map((alpha, i) => (
            <Group key={i} opacity={alpha} layer>
              {GLOW_LAYERS.map((layer) => (
                <Path key={layer.stroke} path={paths.shape} style="stroke" strokeWidth={layer.stroke} color="#ffffff">
                  <Blur blur={layer.blur} />
                </Path>
              ))}
              {i === 0 && <Path path={paths.rim} style="stroke" strokeWidth={1} color={RIM} />}
              <Rect x={PAD} y={PAD} width={width} height={height} blendMode="dstIn">
                <LinearGradient start={vec(0, PAD)} end={vec(0, PAD + height)} colors={GLOW_FADE} positions={GLOW_FADE_STOPS} />
              </Rect>
            </Group>
          ))}
        </Group>

        <Group transform={haloTransform} origin={vec(cx, cy)} opacity={haloAlpha}>
          <Circle cx={cx} cy={cy} r={HALO_SIZE / 2} color="#ffffff">
            <Blur blur={HALO_BLUR} />
          </Circle>
        </Group>
        <Circle cx={cx} cy={cy} r={ringRadius} style="stroke" strokeWidth={PULSE_STROKE} color="#ffffff" opacity={ringAlpha} />
        <Group transform={badgeTransform} origin={vec(cx, cy)}>
          <Circle cx={cx} cy={cy} r={BADGE / 2} color={BADGE_FILL} />
          <Circle
            cx={cx}
            cy={cy}
            r={BADGE / 2 - BADGE_BORDER_WIDTH / 2}
            style="stroke"
            strokeWidth={BADGE_BORDER_WIDTH}
            color={BADGE_BORDER}
          />
          {paths.clip && (
            <Path
              path={paths.clip}
              style="stroke"
              strokeWidth={BADGE_GLYPH_STROKE}
              strokeCap="round"
              strokeJoin="round"
              color="#ffffff"
            />
          )}
        </Group>
      </Canvas>

      <View style={styles.copy} pointerEvents="box-none">
        <Text style={styles.prompt} pointerEvents="none">
          Add files to upload
        </Text>
        <Text style={styles.hint} pointerEvents="none">
          or <Text style={styles.link}>browse</Text>, 4 MB max
        </Text>
        <Animated.View style={[styles.pill, pillStyle]}>
          <Pressable
            style={styles.pillFace}
            disabled={full}
            onPressIn={onPillIn}
            onPressOut={onPillOut}
            onPress={onSelect}
            accessibilityRole="button"
            accessibilityLabel="Select files"
            accessibilityState={{ disabled: full }}
          >
            <Animated.View style={[styles.pillBorder, pillDim]} />
            <Animated.Text style={[styles.pillLabel, pillDim]}>Select files</Animated.Text>
          </Pressable>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  copy: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: BADGE_CENTER + BADGE / 2,
    alignItems: 'center',
  },
  prompt: {
    marginTop: PROMPT_GAP,
    color: INK,
    fontSize: PROMPT_SIZE,
    fontWeight: '600',
    includeFontPadding: false,
  },
  hint: {
    marginTop: HINT_GAP,
    color: HINT,
    fontSize: HINT_SIZE,
    includeFontPadding: false,
  },
  link: {
    color: LINK,
    textDecorationLine: 'underline',
  },
  pill: {
    marginTop: PILL_GAP,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
  },
  pillFace: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: PILL_HEIGHT / 2,
    backgroundColor: PILL_FILL,
  },
  pillBorder: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: PILL_HEIGHT / 2,
    borderWidth: 1,
    borderColor: PILL_BORDER,
  },
  pillLabel: {
    color: INK,
    fontSize: PILL_LABEL,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
