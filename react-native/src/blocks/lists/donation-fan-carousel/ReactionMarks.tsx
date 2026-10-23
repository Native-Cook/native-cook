import Svg, { Defs, LinearGradient, Path, Stop } from 'react-native-svg';
import Animated, { interpolate, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { coinAt, heartsAt } from './motion';
import { colors, fan } from './theme';

type ReactionMarksProps = {
  clock: SharedValue<number>;
  page: SharedValue<number>;
  scale: number;
};

function Heart({ clock, index, scale }: Omit<ReactionMarksProps, 'page'> & { index: number }) {
  const positions = [
    { x: 45, y: -59, rotate: 13, size: 24, rise: 16 },
    { x: -54, y: -15, rotate: -10, size: 25, rise: 9 },
    { x: 16, y: 34, rotate: 16, size: 28, rise: 13 },
  ];
  const p = positions[index];
  const style = useAnimatedStyle(() => {
    const amount = heartsAt(clock.get());
    const delayed = Math.max(0, Math.min(1, amount * 1.35 - index * 0.08));
    return {
      opacity: delayed,
      transform: [
        { translateX: p.x * scale },
        { translateY: (p.y + p.rise * (1 - delayed)) * scale },
        { rotate: `${p.rotate}deg` },
        { scale: interpolate(delayed, [0, 1], [0.72, 1]) },
      ],
    };
  });

  return (
    <Animated.View pointerEvents="none" style={[styles.heart, { width: p.size * scale, height: p.size * scale }, style]}>
      <Svg width="100%" height="100%" viewBox="0 0 32 30">
        <Defs>
          <LinearGradient id={`heart-${index}`} x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor="#FF8B8E" />
            <Stop offset="0.42" stopColor={colors.heart} />
            <Stop offset="1" stopColor={colors.heartDark} />
          </LinearGradient>
        </Defs>
        <Path
          d="M16 28C12.8 23.8 2 17.5 2 9.4C2 4.9 5.2 2 9.1 2C12 2 14.3 3.7 16 6.2C17.7 3.7 20 2 22.9 2C26.8 2 30 4.9 30 9.4C30 17.5 19.2 23.8 16 28Z"
          fill={`url(#heart-${index})`}
        />
        <Path d="M7.3 6.8C9 4.9 11.5 5.1 13.1 6.7" fill="none" stroke="#FFD2D2" strokeWidth="1.7" strokeLinecap="round" />
      </Svg>
    </Animated.View>
  );
}

export function ReactionMarks({ clock, page, scale }: ReactionMarksProps) {
  const coinStyle = useAnimatedStyle(() => {
    const amount = coinAt(clock.get());
    const t = ((clock.get() % 5366) + 5366) % 5366;
    const selected = t < 2500 ? 0 : 3;
    const distance = selected - page.get();
    const absolute = Math.min(3, Math.abs(distance));
    const direction = distance < 0 ? -1 : 1;
    const x = interpolate(absolute, [0, 1, 2, 3], fan.x) * direction * scale;
    const y = interpolate(absolute, [0, 1, 2, 3], fan.y) * scale;
    const cardScale = interpolate(absolute, [0, 1, 2, 3], fan.scale);
    return {
      opacity: amount,
      transform: [
        { translateX: x },
        { translateY: y },
        { scale: cardScale * interpolate(amount, [0, 1], [0.74, 1]) },
      ],
    };
  });

  return (
    <Animated.View pointerEvents="none" style={styles.layer}>
      {[0, 1, 2].map((index) => (
        <Heart key={index} clock={clock} index={index} scale={scale} />
      ))}
      <Animated.View style={[styles.coin, { width: 22 * scale, height: 22 * scale, borderRadius: 11 * scale }, coinStyle]}>
        <Animated.View style={[styles.coinInner, { borderRadius: 8 * scale }]}>
          <Animated.View style={[styles.seed, { height: 4 * scale, width: 2 * scale }]} />
          <Animated.View style={[styles.seed, { height: 6 * scale, width: 2 * scale }]} />
          <Animated.View style={[styles.seed, { height: 3.5 * scale, width: 2 * scale }]} />
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = {
  layer: {
    position: 'absolute' as const,
    left: '50%' as const,
    top: '50%' as const,
    width: 0,
    height: 0,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    zIndex: 20,
  },
  heart: {
    position: 'absolute' as const,
    shadowColor: '#8B0F18',
    shadowOpacity: 0.22,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 2 },
  },
  coin: {
    position: 'absolute' as const,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: colors.accent,
    borderColor: colors.accentDark,
    borderWidth: 1,
    shadowColor: '#4A691E',
    shadowOpacity: 0.22,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
  coinInner: {
    width: '72%' as const,
    height: '72%' as const,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.72)',
    flexDirection: 'row' as const,
    alignItems: 'flex-end' as const,
    justifyContent: 'center' as const,
    gap: 2,
    paddingBottom: 3,
  },
  seed: { backgroundColor: 'rgba(255,255,255,0.75)', borderRadius: 2 },
};
