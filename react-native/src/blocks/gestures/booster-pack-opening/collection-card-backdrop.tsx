import { Blur, Canvas, Fill, Group, Paint, Path } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { AppState, StyleSheet, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  Extrapolation,
  interpolate,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

type CollectionCardBackdropProps = Readonly<{
  active?: boolean;
  height: number;
  opacityProgress: SharedValue<number>;
  /** When this number changes, the turn and the particles go back to where they began. */
  rewind: SharedValue<number>;
  zoomProgress: SharedValue<number>;
  width: number;
}>;

type ParticleSpec = Readonly<{
  drift: number;
  duration: number;
  id: number;
  left: number;
  maxOpacity: number;
  phase: number;
  size: number;
  waveOffset: number;
}>;

const BACKDROP = {
  background: '#e5e9ef',
  pattern: '#b9c3cf',
} as const;

const ART_WIDTH = 218;
const ART_HEIGHT = ART_WIDTH * (88 / 63);
const ROTATION_DURATION = 38_000;
const START_ANGLE = 0;
const ENTRY_SCALE = 1.5;
const RESTING_SCALE = 1;
const BACKDROP_BLUR = 16;
const PREWARM_OPACITY = 0.001;

// A coarse swirl in card proportions. It is drawn blurred, large and slowly
// turning, so only its light and dark masses read behind the cards.
const PATTERN_PATHS = [
  'M27.1 4.9 L6.2 4.9 L6.9 0 L0 0 L0 8.4 L1.4 5.6 L4.9 6.3 L4.9 43.2 L30.5 13.9 Z',
  'M5.6 66.2 L34 44.6 L56.9 36.2 L88.9 18.8 L102.8 22.3 L152.7 23.7 L164.5 22.3 L174.3 16 L175 22.3 L184 30 L212.4 41.1 L212.4 7 L206.9 4.2 L55.5 4.9 L52.1 12.5 L11.1 54.4 Z',
  'M108.3 34.1 L91.6 36.2 L85.4 42.5 L50.7 52.3 L34 62.7 L20.1 77.3 L11.1 98.3 L11.1 108.7 L19.4 120.5 L19.4 124.7 L4.9 142.8 L4.9 170 L9 185.4 L4.9 186.7 L4.9 212.5 L18.1 216.7 L18.1 209 L12.5 195.8 L16 195.1 L35.4 212.5 L55.5 208.3 L54.2 227.9 L61.1 239 L67.3 241.8 L90.9 239 L97.2 246 L115.2 247.4 L145.1 234.1 L170.1 218.1 L188.8 200 L197.9 186.7 L195.8 174.9 L202.7 157.5 L203.4 140.8 L198.6 127.5 L190.2 118.5 L177.7 117.8 L164.5 105.2 L152 100.3 L136.1 101 L134 103.8 L136.1 111.5 L155.5 115.7 L160.4 127.5 L179.1 142.8 L182.6 151.2 L181.2 163.8 L165.9 171.4 L153.4 188.8 L150.7 188.8 L148.6 183.3 L143.7 181.2 L128.4 198.6 L118 203.5 L102.8 204.9 L89.6 202.1 L86.8 205.6 L82.6 204.9 L77.8 198.6 L56.2 147.7 L51.4 141.5 L43 139.4 L46.5 110.8 L59 83.6 L54.8 72.5 L78.5 60.6 L115.2 54.4 L120.1 51.6 L121.5 46 L145.1 48.8 L164.5 55.7 L169.4 54.4 L163.8 46 L152.7 40.4 Z',
  'M181.9 65.5 L177 71.1 L177 81.5 L192.3 100.3 L212.4 114.3 L212.4 70.4 L193.7 63.4 Z',
  'M154.1 80.8 L126.4 72.5 L115.2 73.2 L107.6 77.3 L90.3 77.3 L81.2 81.5 L69.4 94.8 L61.8 109.4 L62.5 117.1 L68 118.5 L76.4 115 L81.9 110.1 L86.8 97.6 L97.2 96.9 L128.4 81.5 L143 81.5 L153.4 86.4 Z',
  'M213.1 201.4 L211.8 199.3 L206.9 208.3 L187.5 225.1 L179.1 237.6 L164.5 241.8 L152 249.5 L134 271.8 L93 272.5 L86.8 264.8 L58.3 248.8 L35.4 239.7 L11.1 235.5 L4.2 244.6 L4.2 291.3 L6.2 297.5 L21.5 295.4 L31.2 298.2 L70.1 298.2 L84.7 294.1 L115.9 294.8 L152.7 282.2 L175.6 269 L202 244.6 L207.6 236.2 L212.4 219.5 Z',
  'M212.4 288.5 L182.6 289.9 L168.7 297.5 L206.2 298.2 L212.4 296.1 Z',
] as const;

const PARTICLE_COUNT = 26;
const PARTICLE_SIZES = [3, 4, 6, 3, 8, 5, 11, 4, 7, 3, 5, 9] as const;
const TWO_PI = Math.PI * 2;

function fraction(index: number, multiplier: number, offset: number) {
  return ((index * multiplier + offset) % 997) / 997;
}

const particles: ParticleSpec[] = Array.from({ length: PARTICLE_COUNT }, (_, index) => {
  const size = PARTICLE_SIZES[index % PARTICLE_SIZES.length];

  return {
    drift: (index % 2 === 0 ? -1 : 1) * (4 + fraction(index, 179, 41) * 10),
    duration: 11_500 + fraction(index, 263, 73) * 6_000,
    id: index,
    left: 0.03 + fraction(index, 619, 17) * 0.94,
    maxOpacity: Math.min(0.54, 0.22 + size * 0.022 + fraction(index, 131, 59) * 0.08),
    phase: fraction(index, 397, 29),
    size,
    waveOffset: fraction(index, 449, 97) * TWO_PI,
  };
});

function RisingParticle({
  height,
  motionActive,
  particle,
  reduceMotion,
  rewind,
  width,
}: Readonly<{
  height: number;
  particle: ParticleSpec;
  motionActive: SharedValue<boolean>;
  reduceMotion: boolean;
  rewind: SharedValue<number>;
  width: number;
}>) {
  const progress = useSharedValue(particle.phase);

  const duration = particle.duration;
  const phase = particle.phase;
  useAnimatedReaction(
    () => rewind.get(),
    (next, previous) => {
      if (previous === null || next === previous) return;
      cancelAnimation(progress);
      progress.set(phase);
    },
    [phase, progress, rewind],
  );
  useAnimatedReaction(
    () => motionActive.get(),
    (running, previous) => {
      if (running === previous) return;
      cancelAnimation(progress);
      if (running) {
        // Resume the current phase; hiding the scene must not move particles.
        progress.set(withRepeat(withTiming(progress.get() + 1, {
          duration,
          easing: Easing.linear,
        }), -1, false));
      }
    },
    [duration, motionActive, progress],
  );
  useEffect(() => () => cancelAnimation(progress), [progress]);

  const animatedStyle = useAnimatedStyle(() => {
    const cycle = progress.get() % 1;
    const translateY = interpolate(
      cycle,
      [0, 1],
      [height + particle.size, -particle.size],
      Extrapolation.CLAMP,
    );
    const opacity = interpolate(
      cycle,
      [0, 0.12, 0.58, 1],
      reduceMotion
        ? [0, particle.maxOpacity * 0.34, particle.maxOpacity * 0.24, 0]
        : [0, particle.maxOpacity, particle.maxOpacity * 0.68, 0],
      Extrapolation.CLAMP,
    );
    const translateX = reduceMotion
      ? 0
      : Math.sin(cycle * TWO_PI + particle.waveOffset) * particle.drift;

    return {
      opacity,
      transform: [{ translateX }, { translateY }],
    };
  });

  return (
    <Animated.View
      style={[
        styles.particle,
        {
          borderRadius: particle.size / 2,
          boxShadow: `0 0 ${Math.max(4, particle.size)}px rgba(255, 255, 255, 0.58)`,
          height: particle.size,
          left: particle.left * width - particle.size / 2,
          width: particle.size,
        },
        animatedStyle,
      ]}
    />
  );
}

function RisingParticles({
  height,
  motionActive,
  rewind,
  width,
}: Readonly<{
  height: number; motionActive: SharedValue<boolean>; rewind: SharedValue<number>; width: number;
}>) {
  const reduceMotion = useReducedMotion();

  return (
    <View style={[StyleSheet.absoluteFill, styles.particleLayer]}>
      {particles.map((particle) => (
        <RisingParticle
          height={height}
          key={particle.id}
          motionActive={motionActive}
          particle={particle}
          reduceMotion={reduceMotion}
          rewind={rewind}
          width={width}
        />
      ))}
    </View>
  );
}

export function CollectionCardBackdrop({
  active = true,
  height,
  opacityProgress,
  rewind,
  width,
  zoomProgress,
}: CollectionCardBackdropProps) {
  const reduceMotion = useReducedMotion();
  const rotation = useSharedValue(START_ANGLE);
  const foreground = useSharedValue(AppState.currentState === 'active' || AppState.currentState === null);
  const motionActive = useDerivedValue(() =>
    active && foreground.get() && !reduceMotion && opacityProgress.get() > 0,
  );

  useEffect(() => {
    const update = () => foreground.set(AppState.currentState === 'active' || AppState.currentState === null);
    update();
    const subscription = AppState.addEventListener('change', update);
    return () => subscription.remove();
  }, [foreground]);
  const planeSize = Math.ceil(Math.hypot(width, height) * 1.08);
  const artSize = Math.max(width * 1.55, height * 0.82);
  const artOffset = (planeSize - artSize) / 2;
  const scaleX = artSize / ART_WIDTH;
  const scaleY = artSize / ART_HEIGHT;

  useAnimatedReaction(
    () => motionActive.get(),
    (running, previous) => {
      if (running === previous) return;
      cancelAnimation(rotation);
      if (running) {
        rotation.set(withRepeat(withTiming(rotation.get() - 360, {
          duration: ROTATION_DURATION,
          easing: Easing.linear,
        }), -1, false));
      }
    },
    [motionActive, rotation],
  );
  useEffect(() => () => cancelAnimation(rotation), [rotation]);
  // The autoPlay script resets the scene with the backdrop hidden; rewinding
  // it there makes one loop draw the same backdrop as the next.
  useAnimatedReaction(
    () => rewind.get(),
    (next, previous) => {
      if (previous === null || next === previous) return;
      cancelAnimation(rotation);
      rotation.set(START_ANGLE);
    },
    [rewind, rotation],
  );

  // Keep one nearly transparent frame rendered before reveal so Skia has
  // already compiled the blur and uploaded the artwork at the transition.
  const animatedContainerStyle = useAnimatedStyle(() => ({
    opacity:
      PREWARM_OPACITY + opacityProgress.get() * (1 - PREWARM_OPACITY),
  }));

  const animatedPlaneStyle = useAnimatedStyle(() => ({
    transform: [
      { rotateZ: `${rotation.get()}deg` },
      {
        scale: reduceMotion
          ? RESTING_SCALE
          : interpolate(
              zoomProgress.get(),
              [0, 1],
              [ENTRY_SCALE, RESTING_SCALE],
              Extrapolation.CLAMP,
            ),
      },
    ],
  }));

  return (
    <Animated.View style={[styles.container, animatedContainerStyle]}>
      <Animated.View
        style={[
          styles.plane,
          {
            height: planeSize,
            left: (width - planeSize) / 2,
            top: (height - planeSize) / 2,
            width: planeSize,
          },
          animatedPlaneStyle,
        ]}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill color={BACKDROP.background} />
          <Group
            layer={
              <Paint>
                <Blur blur={BACKDROP_BLUR} mode="clamp" />
              </Paint>
            }
          >
            <Group transform={[{ translateX: artOffset }, { translateY: artOffset }]}>
              <Group transform={[{ scaleX }, { scaleY }]}>
                {PATTERN_PATHS.map((path) => (
                  <Path color={BACKDROP.pattern} key={path} path={path} />
                ))}
              </Group>
            </Group>
          </Group>
        </Canvas>
      </Animated.View>
      <RisingParticles height={height} motionActive={motionActive} rewind={rewind} width={width} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: BACKDROP.background,
    bottom: 0,
    left: 0,
    overflow: 'hidden',
    pointerEvents: 'none',
    position: 'absolute',
    right: 0,
    top: 0,
  },
  particleLayer: {
    pointerEvents: 'none',
  },
  particle: {
    backgroundColor: 'rgba(255, 255, 255, 0.96)',
    position: 'absolute',
    top: 0,
  },
  plane: {
    position: 'absolute',
  },
});
