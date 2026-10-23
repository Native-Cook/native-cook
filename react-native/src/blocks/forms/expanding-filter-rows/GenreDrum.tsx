import { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** The panel's height. */
export const GENRE_PANEL = 236;

const BLUE = '#2f6bff';
// Each column is the rim of a wheel whose hub lies far outside the panel: the chosen names on a
// wheel to the left, the others on a wheel to the right. A name is a spoke, so it tilts as it
// leaves the middle, and the two rims are closest there.
const RADIUS = 280;
// Degrees between two names on a rim; at the middle that is RADIUS × sin(STEP) ≈ 36.5 pt.
const STEP = 7.5;
const STEP_RAD = (STEP * Math.PI) / 180;
const PITCH = RADIUS * Math.sin(STEP_RAD);
const LINE = 30;
const LINE_WIDTH = 150;
// Where each rim touches the middle row, from the panel's centre.
const LEFT_RIM = -24;
const RIGHT_RIM = 27;
const RING = 12;
// How bright a name is by its distance from the middle, in names.
const FADE_AT = [0, 1.2, 2, 3, 3.6];
const FADE = [1, 1, 0.82, 0.32, 0];
// While the panel opens the wheels turn into place from this many names away, in opposite ways.
const SWING = 2.5;

const NEVER = ReduceMotion.Never;
// A released wheel settles on a name without bouncing.
const DRUM_SPRING = { damping: 24, stiffness: 190, mass: 1, reduceMotion: NEVER };
// A name takes its new place on the rim when the list around it changes.
const SLOT_SPRING = { damping: 20, stiffness: 210, mass: 1, reduceMotion: NEVER };
const FLICK = 0.14;
/** A picked name fills its ring and fades for this long before it changes column. */
export const PICK_MS = 260;

type Side = 'left' | 'right';

function DrumLine({
  name,
  index,
  side,
  position,
  shown,
  moving,
  fresh,
  onActivate,
}: {
  name: string;
  index: number;
  side: Side;
  position: SharedValue<number>;
  shown: SharedValue<number>;
  /** It has been tapped and is on its way to the other column. */
  moving: boolean;
  /** It has just arrived from the other column. */
  fresh: boolean;
  onActivate: () => void;
}) {
  const slot = useSharedValue(index);
  const present = useSharedValue(fresh ? 0 : 1);

  useEffect(() => {
    slot.set(withSpring(index, SLOT_SPRING));
  }, [index, slot]);

  useEffect(() => {
    present.set(withTiming(moving ? 0 : 1, { duration: moving ? PICK_MS : 320, reduceMotion: NEVER }));
  }, [moving, present]);

  const style = useAnimatedStyle(() => {
    const turn = side === 'left' ? -SWING : SWING;
    const away = slot.get() - position.get() + turn * (1 - Math.min(1, shown.get()));
    const angle = away * STEP_RAD;
    const out = RADIUS * (1 - Math.cos(angle));
    return {
      opacity: interpolate(Math.abs(away), FADE_AT, FADE, 'clamp') * present.get(),
      transform: [
        { translateX: side === 'left' ? -out : out },
        { translateY: RADIUS * Math.sin(angle) },
        { rotate: `${side === 'left' ? angle : -angle}rad` },
      ],
    };
  });

  return (
    <Animated.View
      style={[styles.line, side === 'left' ? styles.lineLeft : styles.lineRight, style]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={name}
      accessibilityHint={side === 'left' ? 'Chosen. Activate to remove.' : 'Activate to choose.'}
      accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={onActivate}
    >
      {side === 'right' && (
        <Animated.View
          style={[
            styles.ring,
            {
              backgroundColor: moving ? BLUE : 'rgba(47,107,255,0)',
              transform: [{ scale: moving ? 1.25 : 1 }],
              transitionProperty: ['backgroundColor', 'transform'],
              transitionDuration: 140,
            },
          ]}
        >
          <View style={[styles.pip, moving && styles.pipOn]} />
        </Animated.View>
      )}
      <Text style={[styles.name, side === 'left' && styles.nameLeft]} numberOfLines={1}>
        {name}
      </Text>
    </Animated.View>
  );
}

function Column({
  side,
  names,
  position,
  shown,
  moving,
  fresh,
  onTap,
  onTouch,
}: {
  side: Side;
  names: string[];
  position: SharedValue<number>;
  shown: SharedValue<number>;
  moving: string | null;
  fresh: string | null;
  onTap: (name: string) => void;
  onTouch: () => void;
}) {
  const grabbed = useSharedValue(0);
  const last = Math.max(0, names.length - 1);

  // A list that got shorter pulls the wheel back onto its last name.
  useEffect(() => {
    if (position.get() > last) position.set(withSpring(last, DRUM_SPRING));
  }, [last, position]);

  const tapped = (slot: number) => {
    if (slot >= 0 && slot < names.length) onTap(names[slot]);
  };

  const pan = Gesture.Pan()
    .onBegin(() => {
      cancelAnimation(position);
      grabbed.set(position.get());
      scheduleOnRN(onTouch);
    })
    .onUpdate((event) => {
      const to = grabbed.get() - event.translationY / PITCH;
      // Past either end the wheel gives a little, then stops.
      const over = to < 0 ? to : to > last ? to - last : 0;
      position.set(to - over + over / (1 + Math.abs(over)) / 2);
    })
    .onFinalize((event) => {
      const carried = position.get() - (event.velocityY * FLICK) / PITCH;
      position.set(withSpring(Math.min(last, Math.max(0, Math.round(carried))), DRUM_SPRING));
    });

  // A tap finds its name by the height it landed at: the rim is a circle, so the arcsine.
  const tap = Gesture.Tap().onEnd((event) => {
    const height = Math.min(1, Math.max(-1, (event.y - GENRE_PANEL / 2) / RADIUS));
    scheduleOnRN(tapped, Math.round(position.get() + Math.asin(height) / STEP_RAD));
  });

  return (
    <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
      <View style={[styles.column, side === 'left' ? styles.columnLeft : styles.columnRight]}>
        {names.map((name, index) => (
          <DrumLine
            key={name}
            name={name}
            index={index}
            side={side}
            position={position}
            shown={shown}
            moving={name === moving}
            fresh={name === fresh}
            onActivate={() => onTap(name)}
          />
        ))}
      </View>
    </GestureDetector>
  );
}

type Props = {
  /** The chosen genres, in the order the left wheel shows them. */
  chosen: string[];
  /** The genres not chosen, in the order the right wheel shows them. */
  others: string[];
  /** The name in the middle of each wheel, as a place in its list. */
  leftPosition: SharedValue<number>;
  rightPosition: SharedValue<number>;
  /** 0 while the panel is shut, 1 when it is open: the wheels turn into place with it. */
  shown: SharedValue<number>;
  /** The genre on its way from one column to the other, and the one that just arrived. */
  moving: string | null;
  fresh: string | null;
  emptyLabel: string;
  /** A name on the right wheel was tapped. */
  onPick: (name: string) => void;
  /** A name on the left wheel was tapped. */
  onDrop: (name: string) => void;
  onTouch: () => void;
};

/**
 * Two lists facing each other on the rims of two wheels: the chosen genres on the left, the
 * others, each with a ring, on the right. Each column turns under the finger; a tap moves a name
 * to the other column.
 */
export function GenreDrum({ chosen, others, leftPosition, rightPosition, shown, moving, fresh, emptyLabel, onPick, onDrop, onTouch }: Props) {
  return (
    <View style={styles.panel}>
      {chosen.length === 0 && <Text style={styles.empty}>{emptyLabel}</Text>}
      <Column side="left" names={chosen} position={leftPosition} shown={shown} moving={moving} fresh={fresh} onTap={onDrop} onTouch={onTouch} />
      <Column side="right" names={others} position={rightPosition} shown={shown} moving={moving} fresh={fresh} onTap={onPick} onTouch={onTouch} />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    height: GENRE_PANEL,
    overflow: 'hidden',
  },
  column: {
    position: 'absolute',
    top: 0,
    bottom: 0,
  },
  columnLeft: {
    left: 0,
    right: '50%',
  },
  columnRight: {
    left: '50%',
    right: 0,
  },
  line: {
    position: 'absolute',
    top: (GENRE_PANEL - LINE) / 2,
    width: LINE_WIDTH,
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  lineLeft: {
    right: -LEFT_RIM,
    justifyContent: 'flex-end',
    transformOrigin: 'right center',
  },
  lineRight: {
    left: RIGHT_RIM,
    transformOrigin: 'left center',
  },
  ring: {
    width: RING,
    height: RING,
    borderRadius: RING / 2,
    borderWidth: 1.5,
    borderColor: BLUE,
    marginRight: 9,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pip: {
    width: 3.5,
    height: 3.5,
    borderRadius: 2,
    backgroundColor: BLUE,
    opacity: 0.7,
  },
  pipOn: {
    backgroundColor: '#ffffff',
    opacity: 1,
  },
  name: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
  },
  nameLeft: {
    textAlign: 'right',
  },
  empty: {
    position: 'absolute',
    left: 0,
    right: '50%',
    top: (GENRE_PANEL - 20) / 2,
    paddingRight: -LEFT_RIM,
    color: '#5c5c62',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'right',
  },
});
