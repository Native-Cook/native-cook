import { Canvas, Circle, Group, Line, Path, RadialGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolate,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

// A time is a count of half hours since midnight: 38 is 7:00 pm.
export const TIMES = 48;
/** The panel's height: the two options, then the clock and the wheels. */
export const TIME_PANEL = 218;

const BLUE = '#2f6bff';
const INK = '#ffffff';
const HAIRLINE = '#232326';
const OPTIONS = 58;
const CHIP = 28;
const DOT = 9;
const CLOCK = 118;
const CLOCK_LEFT = 16;
// From the clock's centre: where the ticks start and end, the blue arc, and the two hands.
const TICK_FROM = 46;
const TICK_TO = 52;
const ARC = 51;
const ARC_STROKE = 4;
const LONG_HAND = 40;
const SHORT_HAND = 24;
const LINE = 23;
const COLUMN = 92;
// With two wheels, each sits this far from where the single one was.
const COLUMN_SHIFT = 48;
// How bright a line is by its distance from the middle, in lines.
const FADE_AT = [0, 1, 2, 2.7];
const FADE = [1, 0.72, 0.32, 0];

/** A released wheel settles on a line without bouncing. */
export const WHEEL_SPRING = { damping: 24, stiffness: 190, mass: 1, reduceMotion: ReduceMotion.Never };
// How far a flick carries: the wheel lands this many seconds of travel ahead.
const FLICK = 0.14;

const TICKS = Array.from({ length: 12 }, (_, hour) => {
  const angle = (hour * Math.PI) / 6;
  return { hour, sin: Math.sin(angle), cos: -Math.cos(angle) };
});

export function timeLabel(half: number) {
  const hour = Math.floor(half / 2);
  return `${((hour + 11) % 12) + 1}:${half % 2 ? '30' : '00'} ${hour < 12 ? 'am' : 'pm'}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One line of a wheel: it rides past the middle as the wheel turns. */
function WheelLine({ index, position }: { index: number; position: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const away = index - position.get();
    const opacity = interpolate(Math.abs(away), FADE_AT, FADE, 'clamp');
    return { opacity, transform: [{ translateY: away * LINE }] };
  });
  return (
    <Animated.Text style={[styles.line, style]} numberOfLines={1}>
      {timeLabel(index)}
    </Animated.Text>
  );
}

/** A column of times in half-hour steps. `position` is the time in the middle, in half hours. */
function Wheel({
  position,
  label,
  onTouch,
  onSettle,
}: {
  position: SharedValue<number>;
  label: string;
  onTouch: () => void;
  /** Called with the time each time another line reaches the middle. */
  onSettle: (half: number, byHand: boolean) => void;
}) {
  const grabbed = useSharedValue(0);
  const byHand = useSharedValue(false);

  useAnimatedReaction(
    () => Math.min(TIMES - 1, Math.max(0, Math.round(position.get()))),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(onSettle, now, byHand.get());
    },
  );

  const pan = Gesture.Pan()
    .onBegin(() => {
      cancelAnimation(position);
      grabbed.set(position.get());
      byHand.set(true);
      scheduleOnRN(onTouch);
    })
    .onUpdate((event) => {
      const to = grabbed.get() - event.translationY / LINE;
      // Past either end the wheel gives a little, then stops.
      const over = to < 0 ? to : to > TIMES - 1 ? to - (TIMES - 1) : 0;
      position.set(to - over + over / (1 + Math.abs(over)) / 2);
    })
    .onFinalize((event) => {
      const carried = position.get() - (event.velocityY * FLICK) / LINE;
      position.set(withSpring(Math.min(TIMES - 1, Math.max(0, Math.round(carried))), WHEEL_SPRING));
      scheduleOnRN(onTouch);
    });

  const step = (by: number) => {
    onTouch();
    byHand.set(true);
    position.set(withSpring(Math.min(TIMES - 1, Math.max(0, Math.round(position.get()) + by)), WHEEL_SPRING));
  };

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.wheel}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => step(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
      >
        {Array.from({ length: TIMES }, (_, index) => (
          <WheelLine key={index} index={index} position={position} />
        ))}
      </View>
    </GestureDetector>
  );
}

function Option({ label, chosen, onPress }: { label: string; chosen: boolean; onPress: () => void }) {
  return (
    <Pressable style={styles.chip} hitSlop={6} accessibilityRole="radio" accessibilityState={{ selected: chosen }} onPress={onPress}>
      {/* The dot takes its room only on the chosen option. */}
      <Animated.View
        style={[
          styles.dot,
          {
            width: chosen ? DOT : 0,
            marginRight: chosen ? 7 : 0,
            opacity: chosen ? 1 : 0,
            transitionProperty: ['width', 'marginRight', 'opacity'],
            transitionDuration: 220,
            transitionTimingFunction: 'ease-out',
          },
        ]}
      />
      <Text style={styles.chipLabel} numberOfLines={1}>
        {label}
      </Text>
    </Pressable>
  );
}

type Props = {
  /** The time, or the start of the range, in half hours. The wheel and the clock both read it. */
  start: SharedValue<number>;
  /** The end of the range. */
  end: SharedValue<number>;
  /** 0 with one time, 1 with a range; in between while the second wheel slides in. */
  range: SharedValue<number>;
  ranged: boolean;
  singleLabel: string;
  rangeLabel: string;
  onMode: (ranged: boolean) => void;
  onStart: (half: number, byHand: boolean) => void;
  onEnd: (half: number, byHand: boolean) => void;
  onTouch: () => void;
  /** 0 while the panel is shut, 1 when it is open: the options show first, then the clock and the wheels. */
  shown: SharedValue<number>;
};

/**
 * An analog clock tied to a wheel of times. With one time the clock tells it: turning the wheel
 * one line moves the long hand half a turn and the short one half an hour. With a range a second
 * wheel slides in, the short hand points at the start and the long one at the end, and a blue arc
 * joins them along the rim.
 */
export function TimeDial({ start, end, range, ranged, singleLabel, rangeLabel, onMode, onStart, onEnd, onTouch, shown }: Props) {
  const centre = CLOCK / 2;

  const shortTurn = useDerivedValue(() => [{ rotate: (start.get() * Math.PI) / 12 }]);
  // The long hand is a minute hand that becomes the hand of the end time, by the shorter way round.
  const longTurn = useDerivedValue(() => {
    const minutes = ((start.get() * 180) % 360) + 360;
    const hours = ((end.get() * 15) % 360) + 360;
    const way = ((hours - minutes + 540) % 360) - 180;
    return [{ rotate: ((minutes + way * range.get()) * Math.PI) / 180 }];
  });
  const arc = useDerivedValue(() => {
    const from = (start.get() * Math.PI) / 12;
    // A range of nothing is a dot on the rim; twelve hours or more is the whole rim.
    const sweep = (Math.min(359.9, Math.max(0.1, (end.get() - start.get()) * 15)) * Math.PI) / 180;
    const x0 = centre + ARC * Math.sin(from);
    const y0 = centre - ARC * Math.cos(from);
    const x1 = centre + ARC * Math.sin(from + sweep);
    const y1 = centre - ARC * Math.cos(from + sweep);
    return `M${x0} ${y0}A${ARC} ${ARC} 0 ${sweep > Math.PI ? 1 : 0} 1 ${x1} ${y1}`;
  });

  const optionsStyle = useAnimatedStyle(() => ({ opacity: interpolate(shown.get(), [0.15, 0.6], [0, 1], 'clamp') }));
  const bodyStyle = useAnimatedStyle(() => {
    const there = interpolate(shown.get(), [0.45, 1], [0, 1], 'clamp');
    return { opacity: there, transform: [{ scale: 0.96 + 0.04 * there }] };
  });
  const startStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -COLUMN_SHIFT * range.get() }] }));
  const endStyle = useAnimatedStyle(() => ({
    opacity: range.get(),
    transform: [{ translateX: COLUMN_SHIFT + (1 - range.get()) * 28 }],
  }));

  const choose = (next: boolean) => {
    onTouch();
    if (next !== ranged) {
      tick();
      onMode(next);
    }
  };

  return (
    <View style={styles.panel}>
      <Animated.View style={[styles.options, optionsStyle]} accessibilityRole="radiogroup">
        <Option label={singleLabel} chosen={!ranged} onPress={() => choose(false)} />
        <Option label={rangeLabel} chosen={ranged} onPress={() => choose(true)} />
      </Animated.View>

      <Animated.View style={[styles.body, bodyStyle]}>
        <Canvas style={styles.clock}>
          <Circle cx={centre} cy={centre} r={centre}>
            <RadialGradient c={vec(centre, centre)} r={centre} colors={['#242427', '#202023', '#1b1b1e']} positions={[0, 0.8, 1]} />
          </Circle>
          {TICKS.map(({ hour, sin, cos }) => (
            <Line
              key={hour}
              p1={vec(centre + sin * TICK_FROM, centre + cos * TICK_FROM)}
              p2={vec(centre + sin * TICK_TO, centre + cos * TICK_TO)}
              strokeWidth={1.5}
              strokeCap="round"
              color={hour % 3 === 0 ? '#77777d' : '#434348'}
            />
          ))}
          <Path path={arc} style="stroke" strokeWidth={ARC_STROKE} strokeCap="round" color={BLUE} opacity={range} />
          <Group transform={longTurn} origin={vec(centre, centre)}>
            <Line p1={vec(centre, centre)} p2={vec(centre, centre - LONG_HAND)} strokeWidth={2} strokeCap="round" color={INK} />
          </Group>
          <Group transform={shortTurn} origin={vec(centre, centre)}>
            <Line p1={vec(centre, centre)} p2={vec(centre, centre - SHORT_HAND)} strokeWidth={3.5} strokeCap="round" color={INK} />
          </Group>
          <Circle cx={centre} cy={centre} r={3} color={INK} />
        </Canvas>

        <View style={styles.wheels}>
          <Animated.View style={[styles.column, startStyle]}>
            <Wheel position={start} label={ranged ? 'Start time' : 'Time'} onTouch={onTouch} onSettle={onStart} />
          </Animated.View>
          <Animated.View style={[styles.column, endStyle]} pointerEvents={ranged ? 'auto' : 'none'} accessibilityElementsHidden={!ranged}>
            <Wheel position={end} label="End time" onTouch={onTouch} onSettle={onEnd} />
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    height: TIME_PANEL,
  },
  options: {
    height: OPTIONS,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: HAIRLINE,
  },
  chip: {
    height: CHIP,
    paddingHorizontal: 11,
    borderRadius: CHIP / 2,
    borderWidth: 1,
    borderColor: '#29292d',
    backgroundColor: '#1a1a1d',
    flexDirection: 'row',
    alignItems: 'center',
  },
  dot: {
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: BLUE,
  },
  chipLabel: {
    color: '#ececee',
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '500',
  },
  body: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  clock: {
    width: CLOCK,
    height: CLOCK,
    marginLeft: CLOCK_LEFT,
  },
  wheels: {
    flex: 1,
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'center',
  },
  column: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: COLUMN,
  },
  wheel: {
    flex: 1,
    overflow: 'hidden',
  },
  line: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: '50%',
    marginTop: -LINE / 2,
    height: LINE,
    lineHeight: LINE,
    color: INK,
    fontSize: 17,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
});
