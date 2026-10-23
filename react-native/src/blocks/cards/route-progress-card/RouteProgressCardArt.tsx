import { BlurMask, Canvas, Circle, DashPathEffect, Group, LinearGradient, Path, RadialGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

/** How a leg is travelled: picks the icon and the drawing in the middle of the card. */
export type Travel = 'walk' | 'drive' | 'fly';

/** The colours of a leg, as six-digit hex. */
export type Tones = {
  /** The glow across the top of the card. */
  glow: string;
  /** The brighter colour of the marker, the ring and the bar. */
  accent: string;
};

const INK = 'rgba(255,255,255,0.9)';

// Two footprints, each a sole and a heel.
const STEPS =
  'M 3.7 8.2 C 3.7 5.77 5 3.8 6.6 3.8 C 8.2 3.8 9.5 5.77 9.5 8.2 C 9.5 10.63 8.2 12.6 6.6 12.6 C 5 12.6 3.7 10.63 3.7 8.2 Z ' +
  'M 5 15.7 C 5 14.54 5.9 13.6 7 13.6 C 8.1 13.6 9 14.54 9 15.7 C 9 16.86 8.1 17.8 7 17.8 C 5.9 17.8 5 16.86 5 15.7 Z ' +
  'M 10.7 6 C 10.7 3.57 12 1.6 13.6 1.6 C 15.2 1.6 16.5 3.57 16.5 6 C 16.5 8.43 15.2 10.4 13.6 10.4 C 12 10.4 10.7 8.43 10.7 6 Z ' +
  'M 11.2 13.5 C 11.2 12.34 12.1 11.4 13.2 11.4 C 14.3 11.4 15.2 12.34 15.2 13.5 C 15.2 14.66 14.3 15.6 13.2 15.6 C 12.1 15.6 11.2 14.66 11.2 13.5 Z';

// A car seen from the front: body, windscreen line and wheels are stroked, the headlights filled.
const CAR =
  'M 3 15 L 3 10 Q 3 8.5 4.2 8 L 5.5 4.5 Q 6 3 7.6 3 L 12.4 3 Q 14 3 14.5 4.5 L 15.8 8 Q 17 8.5 17 10 L 17 15 Z ' +
  'M 5.2 8 L 14.8 8 M 5.2 15 L 5.2 17 M 14.8 15 L 14.8 17';
const CAR_LIGHTS =
  'M 5.3 11.4 C 5.3 10.79 5.79 10.3 6.4 10.3 C 7.01 10.3 7.5 10.79 7.5 11.4 C 7.5 12.01 7.01 12.5 6.4 12.5 C 5.79 12.5 5.3 12.01 5.3 11.4 Z ' +
  'M 12.5 11.4 C 12.5 10.79 12.99 10.3 13.6 10.3 C 14.21 10.3 14.7 10.79 14.7 11.4 C 14.7 12.01 14.21 12.5 13.6 12.5 C 12.99 12.5 12.5 12.01 12.5 11.4 Z';

// A plane seen from above, nose up.
const PLANE =
  'M 10 1.5 Q 11.4 1.5 11.4 4 L 11.4 7.6 L 18 11.6 L 18 13.4 L 11.4 11.4 L 11.4 15.4 L 13.6 17 L 13.6 18.4 L 10 17.4 ' +
  'L 6.4 18.4 L 6.4 17 L 8.6 15.4 L 8.6 11.4 L 2 13.4 L 2 11.6 L 8.6 7.6 L 8.6 4 Q 8.6 1.5 10 1.5 Z';

// The ring fills clockwise from the top.
const RING = Skia.Path.Make().addArc(Skia.XYWHRect(152, 15, 26, 26), -90, 359.9);

const ROAD_TOP = 92;
const ROAD_BOTTOM = 140;
const ROAD = 'M 160 92 L 170 92 L 241 140 L 89 140 Z';
const ROAD_EDGES = 'M 160 92 L 89 140 M 170 92 L 241 140';
const POINTER = 'M 165 111 L 175.5 131 L 165 126.5 L 154.5 131 Z';

// The flight's arc: a quadratic curve between two dots.
const ARC_FROM = { x: 40, y: 132 };
const ARC_TO = { x: 290, y: 132 };
const ARC_CONTROL = { x: 165, y: 56 };
const ARC = 'M 40 132 Q 165 56 290 132';

/** `hex` with an opacity, as eight-digit hex. */
export function fade(hex: string, opacity: number) {
  return `${hex}${Math.round(opacity * 255)
    .toString(16)
    .padStart(2, '0')}`;
}

/** Cubic ease-in-out, for the marker's run. */
export function inOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** The leg's icon, drawn in a 20 × 20 box. */
export function TravelIcon({ travel }: { travel: Travel }) {
  return (
    <Canvas style={styles.icon}>
      {travel === 'walk' ? (
        <Path path={STEPS} color={INK} />
      ) : travel === 'drive' ? (
        <>
          <Path path={CAR} style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color={INK} />
          <Path path={CAR_LIGHTS} color={INK} />
        </>
      ) : (
        <Path path={PLANE} color={INK} />
      )}
    </Canvas>
  );
}

type Art = Tones & {
  /** How far along the leg the marker is, 0…1. */
  along: SharedValue<number>;
};

/** Walking: a thick track filled up to a glowing knob. */
function Track({ glow, accent, along }: Art) {
  const knob = useDerivedValue(() => 27 + 276 * along.get());
  const filled = useDerivedValue(() => knob.get() - 12);
  const end = useDerivedValue(() => vec(knob.get(), 0));

  return (
    <>
      <RoundedRect x={18} y={96} width={294} height={12} r={6} color="rgba(255,255,255,0.1)" />
      <RoundedRect x={18} y={96} width={filled} height={12} r={6}>
        <LinearGradient start={vec(18, 0)} end={end} colors={[glow, accent]} />
      </RoundedRect>
      <Circle cx={knob} cy={102} r={12} color={fade(accent, 0.9)}>
        <BlurMask blur={5} style="normal" />
      </Circle>
      <Circle cx={knob} cy={102} r={9} color="#ece8ff" />
      <Circle cx={knob} cy={102} r={9} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.7)" />
      <Circle cx={knob} cy={102} r={4} color="rgba(255,255,255,0.6)" />
    </>
  );
}

function roadHalf(y: number) {
  'worklet';
  return 5 + (71 * (y - ROAD_TOP)) / (ROAD_BOTTOM - ROAD_TOP);
}

/** One dash of the centre line, running towards the viewer. */
function RoadDash({ k, seconds }: { k: number; seconds: SharedValue<number> }) {
  const near = useDerivedValue(() => (k + ((seconds.get() * 1.3) % 1)) / 5);
  const path = useDerivedValue(() => {
    // Depth is squared, so the dashes spread and speed up as they come closer.
    const far = Math.min(1, near.get() + 0.09);
    const y0 = ROAD_TOP + (ROAD_BOTTOM - ROAD_TOP) * near.get() * near.get();
    const y1 = ROAD_TOP + (ROAD_BOTTOM - ROAD_TOP) * far * far;
    const dash = Skia.Path.Make();
    dash.moveTo(165 - roadHalf(y0) * 0.06, y0);
    dash.lineTo(165 + roadHalf(y0) * 0.06, y0);
    dash.lineTo(165 + roadHalf(y1) * 0.06, y1);
    dash.lineTo(165 - roadHalf(y1) * 0.06, y1);
    dash.close();
    return dash;
  });
  const opacity = useDerivedValue(() => 0.25 + 0.6 * near.get());

  return <Path path={path} color="#ffffff" opacity={opacity} />;
}

/** Driving: a road in perspective whose centre dashes run towards the viewer, with a pointer swaying in its lane. */
function Road({ accent, seconds }: Tones & { seconds: SharedValue<number> }) {
  const sway = useDerivedValue(() => [{ translateX: 3 * Math.sin(seconds.get() * 1.7) }]);

  return (
    <>
      <Path path={ROAD}>
        <LinearGradient start={vec(0, ROAD_TOP)} end={vec(0, ROAD_BOTTOM)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.1)']} />
      </Path>
      <Path path={ROAD_EDGES} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.3)" />
      {[0, 1, 2, 3, 4].map((k) => (
        <RoadDash key={k} k={k} seconds={seconds} />
      ))}
      <Group transform={sway}>
        <Path path={POINTER} color={fade(accent, 0.9)}>
          <BlurMask blur={4} style="normal" />
        </Path>
        <Path path={POINTER} color={accent} />
      </Group>
    </>
  );
}

/** Flying: an arc between two dots, drawn solid behind a glowing marker that travels along it. */
function Arc({ accent, along }: Art) {
  // The first `along` of a quadratic curve is itself one: its control point slides along the first leg.
  const marker = useDerivedValue(() => {
    const t = along.get();
    const bend = { x: mix(ARC_FROM.x, ARC_CONTROL.x, t), y: mix(ARC_FROM.y, ARC_CONTROL.y, t) };
    const rest = { x: mix(ARC_CONTROL.x, ARC_TO.x, t), y: mix(ARC_CONTROL.y, ARC_TO.y, t) };
    return { bend, x: mix(bend.x, rest.x, t), y: mix(bend.y, rest.y, t) };
  });
  const flown = useDerivedValue(() => {
    const { bend, x, y } = marker.get();
    const path = Skia.Path.Make();
    path.moveTo(ARC_FROM.x, ARC_FROM.y);
    path.quadTo(bend.x, bend.y, x, y);
    return path;
  });
  const x = useDerivedValue(() => marker.get().x);
  const y = useDerivedValue(() => marker.get().y);

  return (
    <>
      <Path path={ARC} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.25)">
        <DashPathEffect intervals={[3, 4]} />
      </Path>
      <Path path={flown} style="stroke" strokeWidth={2} strokeCap="round" color={accent} />
      <Circle cx={ARC_FROM.x} cy={ARC_FROM.y} r={3} color="rgba(255,255,255,0.9)" />
      <Circle cx={ARC_TO.x} cy={ARC_TO.y} r={3} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.6)" />
      <Circle cx={x} cy={y} r={9} color={fade(accent, 0.9)}>
        <BlurMask blur={5} style="normal" />
      </Circle>
      <Circle cx={x} cy={y} r={4.5} color="#ffffff" />
    </>
  );
}

type FaceArtProps = Tones & {
  travel: Travel;
  /** How far along the leg the marker stops, 0…1. */
  share: number;
  /** The marker's run, 0…1, before easing. */
  progress: SharedValue<number>;
  /** A clock for the road. */
  seconds: SharedValue<number>;
  nextPress: SharedValue<number>;
};

/**
 * Everything drawn on an open card, in its 330 × 204 box: the glow, the progress ring, the outline of
 * Next Step, the bar beside the stops, and the leg's own drawing.
 */
export function FaceArt({ travel, glow, accent, share, progress, seconds, nextPress }: FaceArtProps) {
  const along = useDerivedValue(() => share * inOut(progress.get()));
  const pressed = useDerivedValue(() => 0.14 * nextPress.get());

  return (
    <Canvas style={styles.face} pointerEvents="none">
      <Group transform={[{ translateX: 165 }, { translateY: -14 }, { scaleX: 240 }, { scaleY: 138 }]}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} r={1} colors={[glow, fade(glow, 0.55), fade(glow, 0)]} positions={[0, 0.5, 1]} />
        </Circle>
      </Group>

      <Circle cx={165} cy={28} r={13} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.16)" />
      <Path path={RING} style="stroke" strokeWidth={1.5} strokeCap="round" color={accent} start={0} end={along} />

      <RoundedRect x={242} y={16} width={70} height={24} r={12} color="#ffffff" opacity={pressed} />
      <RoundedRect x={242} y={16} width={70} height={24} r={12} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.35)" />
      <Path path="M 298.5 24.5 L 304 28 L 298.5 31.5 Z" color={INK} />

      <RoundedRect x={18} y={155} width={2.5} height={31} r={1.25} color={accent} />

      {travel === 'walk' ? (
        <Track glow={glow} accent={accent} along={along} />
      ) : travel === 'drive' ? (
        <Road glow={glow} accent={accent} seconds={seconds} />
      ) : (
        <Arc glow={glow} accent={accent} along={along} />
      )}
    </Canvas>
  );
}

const styles = StyleSheet.create({
  icon: {
    width: 20,
    height: 20,
  },
  face: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 330,
    height: 204,
  },
});
