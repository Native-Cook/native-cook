import { Canvas, Circle, Group, Path, Shadow } from '@shopify/react-native-skia';
import { Pressable, StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { HEART, HEART_PATH, ICON, ICON_SHADOW, ICON_SHADOW_RADIUS, ICON_SHADOW_Y, INFO, INFO_DOT, SHARE, SIGMA, TRASH, Glyph } from './Parts';
import {
  BURST_DOTS,
  type Events,
  ICON_RISE,
  ICON_SIZE,
  ICON_STROKE,
  type Point,
  burst,
  burstDot,
  heartFill,
  heartScale,
  iconShown,
  pressScale,
} from './motion';

/** The canvas an icon is drawn on: room for the heart's burst and the shadows. */
const BOX = 56;
/** The touch target. */
const HIT = 44;
/** Points per point of the 20-point glyph box. */
const UNIT = ICON_SIZE / 20;
export const HEART_INDEX = 1;
const PATHS = [SHARE, HEART_PATH, INFO, TRASH];

type Props = {
  index: number;
  label: string;
  center: Point;
  clock: SharedValue<number>;
  events: SharedValue<Events>;
  still: boolean;
  /** Folded on screen: the icons take touches and are read out. */
  folded: boolean;
  liked: boolean;
  onPressIn: (index: number) => void;
  onPressOut: (index: number) => void;
  onPress: (index: number) => void;
};

/** One icon under the card: fades in and rises with the fold, shrinks while pressed. */
export function Icon({ index, label, center, clock, events, still, folded, liked, onPressIn, onPressOut, onPress }: Props) {
  const style = useAnimatedStyle(() => {
    const e = events.get();
    const now = clock.get();
    const shown = iconShown(index, e, now, still);
    const rise = still ? 0 : ICON_RISE * (1 - shown);
    return {
      opacity: shown,
      transform: [{ translateY: rise }, { scale: pressScale(index, e, now) }],
    };
  });
  return (
    <Animated.View
      pointerEvents={folded ? 'box-none' : 'none'}
      style={[styles.box, { left: center.x - BOX / 2, top: center.y - BOX / 2 }, style]}
      accessibilityElementsHidden={!folded}
      importantForAccessibility={folded ? 'auto' : 'no-hide-descendants'}
    >
      <Canvas style={styles.canvas} pointerEvents="none">
        {index === HEART_INDEX ? (
          <HeartGlyph clock={clock} events={events} still={still} />
        ) : (
          <Group transform={[{ translateX: (BOX - ICON_SIZE) / 2 }, { translateY: (BOX - ICON_SIZE) / 2 }, { scale: UNIT }]}>
            <Glyph d={PATHS[index]} color={ICON} stroke={ICON_STROKE} unit={UNIT} />
            {index === 2 ? (
              <Circle cx={INFO_DOT.x} cy={INFO_DOT.y} r={INFO_DOT.r} color={ICON}>
                <Shadow dx={0} dy={ICON_SHADOW_Y / UNIT} blur={(ICON_SHADOW_RADIUS * SIGMA) / UNIT} color={ICON_SHADOW} />
              </Circle>
            ) : null}
          </Group>
        )}
      </Canvas>
      <Pressable
        style={styles.hit}
        onPressIn={() => onPressIn(index)}
        onPressOut={() => onPressOut(index)}
        onPress={() => onPress(index)}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityState={index === HEART_INDEX ? { selected: liked } : undefined}
      />
    </Animated.View>
  );
}

/** The heart: grey outline, the red fill fading in and out over it, the pop, and the dots behind. */
function HeartGlyph({ clock, events, still }: { clock: SharedValue<number>; events: SharedValue<Events>; still: boolean }) {
  const fill = useDerivedValue(() => heartFill(events.get(), clock.get()));
  const scale = useDerivedValue(() => [
    { translateX: BOX / 2 },
    { translateY: BOX / 2 },
    { scale: heartScale(events.get(), clock.get(), still) * UNIT },
    { translateX: -10 },
    { translateY: -10 },
  ]);
  const progress = useDerivedValue(() => burst(events.get(), clock.get(), still));
  const burstOpacity = useDerivedValue(() => (progress.get() < 0 ? 0 : 1 - progress.get()));
  const dots = [];
  for (let k = 0; k < BURST_DOTS; k++) dots.push(k);
  return (
    <Group>
      {/* The dots fly out from the heart's centre, unscaled by its pop. */}
      <Group opacity={burstOpacity} transform={[{ translateX: BOX / 2 }, { translateY: BOX / 2 }]}>
        {dots.map((k) => (
          <Dot key={k} k={k} progress={progress} />
        ))}
      </Group>
      <Group transform={scale}>
        <Glyph d={HEART_PATH} color={ICON} stroke={ICON_STROKE} unit={UNIT} />
        <Group opacity={fill}>
          <Path path={HEART_PATH} color={HEART} />
          <Path path={HEART_PATH} color={HEART} style="stroke" strokeWidth={ICON_STROKE / UNIT} strokeJoin="round" />
        </Group>
      </Group>
    </Group>
  );
}

function Dot({ k, progress }: { k: number; progress: SharedValue<number> }) {
  const cx = useDerivedValue(() => burstDot(k, Math.max(0, progress.get())).x);
  const cy = useDerivedValue(() => burstDot(k, Math.max(0, progress.get())).y);
  const r = useDerivedValue(() => burstDot(k, Math.max(0, progress.get())).size / 2);
  return <Circle cx={cx} cy={cy} r={r} color={HEART} />;
}

const styles = StyleSheet.create({
  box: {
    position: 'absolute',
    width: BOX,
    height: BOX,
    alignItems: 'center',
    justifyContent: 'center',
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BOX,
    height: BOX,
  },
  hit: {
    width: HIT,
    height: HIT,
  },
});
