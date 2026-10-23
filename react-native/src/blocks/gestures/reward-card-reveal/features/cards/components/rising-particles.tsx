import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  Extrapolation,
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type RisingParticlesProps = {
  height: number;
  width: number;
};

type ParticleSpec = {
  drift: number;
  duration: number;
  id: number;
  left: number;
  maxOpacity: number;
  phase: number;
  size: number;
  waveOffset: number;
};

// Stable inputs keep the ambient field deterministic across renders.
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

type RisingParticleProps = {
  height: number;
  particle: ParticleSpec;
  reduceMotion: boolean;
  width: number;
};

function RisingParticle({ height, particle, reduceMotion, width }: RisingParticleProps) {
  const progress = useSharedValue(particle.phase);

  useEffect(() => {
    cancelAnimation(progress);
    progress.set(particle.phase);

    if (!reduceMotion) {
      progress.set(
        withRepeat(
          withTiming(particle.phase + 1, {
            duration: particle.duration,
            easing: Easing.linear,
          }),
          -1,
          false
        )
      );
    }

    return () => cancelAnimation(progress);
  }, [particle, progress, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => {
    const cycle = progress.get() % 1;
    const translateY = interpolate(
      cycle,
      [0, 1],
      [height + particle.size, -particle.size],
      Extrapolation.CLAMP
    );
    const opacity = interpolate(
      cycle,
      [0, 0.12, 0.58, 1],
      reduceMotion
        ? [0, particle.maxOpacity * 0.34, particle.maxOpacity * 0.24, 0]
        : [0, particle.maxOpacity, particle.maxOpacity * 0.68, 0],
      Extrapolation.CLAMP
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

export function RisingParticles({ height, width }: RisingParticlesProps) {
  const reduceMotion = useReducedMotion();

  return (
    <View style={[StyleSheet.absoluteFill, styles.layer]}>
      {particles.map((particle) => (
        <RisingParticle
          height={height}
          key={particle.id}
          particle={particle}
          reduceMotion={reduceMotion}
          width={width}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  layer: {
    pointerEvents: 'none',
  },
  particle: {
    backgroundColor: 'rgba(255, 255, 255, 0.96)',
    position: 'absolute',
    top: 0,
  },
});
