import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { Pressable, type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type DestinationCity = {
  name: string;
  /** Address of a portrait photo, about 3 : 4. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Props = {
  /** The cities, typed one after another; the first is shown at rest. */
  cities?: DestinationCity[];
  /** Types its way through the cities by itself, one every 2.6 s. */
  autoPlay?: boolean;
  buttonLabel?: string;
  pillColor?: string;
  inkColor?: string;
  buttonColor?: string;
  buttonTextColor?: string;
  /** Called with the index of the city a tap on the pill moves to. */
  onSelect?: (index: number) => void;
  onCurrentLocation?: () => void;
};

const WIDTH = 320;
const HEIGHT = 418;

const PHOTO_WIDTH = 240;
const PHOTO_HEIGHT = 324;
const PHOTO_RADIUS = 14;
const PHOTO_LEFT = (WIDTH - PHOTO_WIDTH) / 2;

// The pill is wider than the photo and crosses its middle.
const PILL_WIDTH = 300;
const PILL_HEIGHT = 76;
const PILL_LEFT = (WIDTH - PILL_WIDTH) / 2;
const PILL_TOP = (PHOTO_HEIGHT - PILL_HEIGHT) / 2;
// A Gaussian of sigma 18, which CSS writes as a 36 px blur.
const PILL_SHADOW = '0 10px 36px rgba(0,0,0,0.14)';
const PIN_WIDTH = 20;
const PIN_HEIGHT = 27;
const PIN_HOLE = 3.7;
const PIN_LEFT = 29;
const TEXT_LEFT = 68;
const CARET_WIDTH = 2;
const CARET_HEIGHT = 33;
const CARET_GAP = 2;

const BUTTON_TOP = 364;
const BUTTON_HEIGHT = 54;
const BUTTON_GAP = 9;
const TARGET_RING = 5.5;
const TARGET_DOT = 2.2;
const TARGET_STROKE = 1.8;

// Seconds before each key lands, repeated along the name: an uneven but fixed rhythm.
const KEY_DELAYS = [0.12, 0.085, 0.14, 0.095, 0.07, 0.16, 0.09, 0.11];
const DELETE_STEP = 0.045;
// The beat between the last deleted letter and the first typed one.
const TYPE_PAUSE = 0.2;
// Half a blink of the caret: this long shown, this long hidden.
const CARET_BLINK = 0.53;

// When a name is complete the next photo fades in while it settles from a slight zoom.
const FADE_TIME = 0.5;
const ZOOM_TIME = 0.7;
const ZOOM = 0.1;

// The pin's hop: a spring at rest that is kicked upwards, in points per second.
const PIN_MASS = 1;
const PIN_STIFFNESS = 221;
const PIN_DAMPING = 10;
const PIN_KICK = 170;

// autoPlay: the first edit starts after FIRST_AT, then one city every CITY_TIME, so the three
// default cities come round every LOOP seconds.
const FIRST_AT = 0.8;
const LOOP = 7.8;
const CITY_TIME = LOOP / 3;
// After a tap the loop waits this long once the name is typed.
const USER_HOLD = 2;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// A map pin, 20 × 27, with its tip at the bottom.
const PIN = 'M10 0C4.48 0 0 4.48 0 10C0 17.5 10 27 10 27C10 27 20 17.5 20 10C20 4.48 15.52 0 10 0Z';
// The four ticks of a "locate me" target in a 20 × 20 box.
const TARGET_TICKS = 'M10 1.2L10 4.5M10 15.5L10 18.8M1.2 10L4.5 10M15.5 10L18.8 10';

const CITIES: DestinationCity[] = [
  {
    name: 'Paris',
    photo: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=480&h=648&q=80',
    tint: '#8c818d',
  },
  {
    name: 'Tokyo',
    photo: 'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?auto=format&fit=crop&w=480&h=648&q=80',
    tint: '#4a848e',
  },
  {
    name: 'New York',
    photo: 'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?auto=format&fit=crop&w=480&h=648&q=80',
    tint: '#6e6469',
  },
];

// The text in the pill travels between threads as one number: the city times this, plus the letters shown.
const CODE = 1000;

/** The text in the pill `elapsed` seconds into an edit: which city's name, and how many of its letters. */
function typedText(elapsed: number, fromCity: number, fromLength: number, to: number, toLength: number) {
  'worklet';
  const deleteTime = fromLength * DELETE_STEP;
  if (elapsed < deleteTime) return fromCity * CODE + fromLength - 1 - Math.floor(elapsed / DELETE_STEP);
  const typing = elapsed - deleteTime - TYPE_PAUSE;
  let count = 0;
  let at = 0;
  while (count < toLength) {
    at += KEY_DELAYS[count % KEY_DELAYS.length];
    if (at > typing) break;
    count += 1;
  }
  return to * CODE + count;
}

/** Seconds an edit takes: deleting what was there, a pause, and one key after another. */
function completeTime(fromLength: number, toLength: number) {
  'worklet';
  let time = fromLength * DELETE_STEP + TYPE_PAUSE;
  for (let key = 0; key < toLength; key++) time += KEY_DELAYS[key % KEY_DELAYS.length];
  return time;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number) {
  'worklet';
  const p = clamp01(t);
  return p * p * (3 - 2 * p);
}

function easeOut(t: number) {
  'worklet';
  const p = 1 - clamp01(t);
  return 1 - p * p * p;
}

/** Height of the pin `since` seconds after a name is completed: a spring kicked upwards. */
function pinHop(since: number) {
  'worklet';
  if (since <= 0) return 0;
  const decay = PIN_DAMPING / (2 * PIN_MASS);
  const omega = Math.sqrt(PIN_STIFFNESS / PIN_MASS - decay * decay);
  return (-PIN_KICK / omega) * Math.exp(-decay * since) * Math.sin(omega * since);
}

type PhotoProps = {
  city: DestinationCity;
  index: number;
  to: SharedValue<number>;
  base: SharedValue<number>;
  /** Seconds since the name was completed; negative while it is still being edited. */
  since: SharedValue<number>;
  still: boolean;
};

/** One city's photo: it fades in over the one before when its name is complete. */
function Photo({ city, index, to, base, since, still }: PhotoProps) {
  const style = useAnimatedStyle(() => {
    const incoming = index === to.get();
    const under = index === base.get();
    return {
      opacity: incoming ? smooth(since.get() / FADE_TIME) : under ? 1 : 0,
      zIndex: incoming ? 2 : under ? 1 : 0,
      transform: [{ scale: incoming && !still ? 1 + ZOOM * (1 - easeOut(since.get() / ZOOM_TIME)) : 1 }],
    };
  });
  return (
    <Animated.View style={[styles.photoLayer, { backgroundColor: city.tint }, style]}>
      <Image source={{ uri: city.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
    </Animated.View>
  );
}

type PressableProps = {
  style: StyleProp<ViewStyle>;
  scale: number;
  label: string;
  hint?: string;
  onPress: () => void;
  children: ReactNode;
};

/** A tappable pill: it dips while pressed, with a light haptic. */
function PressablePill({ style, scale, label, hint, onPress, children }: PressableProps) {
  return (
    <Pressable
      style={style}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      onPressIn={() => {
        if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }}
      onPress={onPress}
    >
      {({ pressed }) => (
        <Animated.View
          style={[
            StyleSheet.absoluteFill,
            {
              transform: [{ scale: pressed ? scale : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A destination picker for an onboarding screen: a portrait photo crossed by a white search pill
 * in which a city is typed and deleted letter by letter. When a name is complete the photo
 * cross-fades to that city with a slight zoom and the map pin hops. A black pill button for the
 * current location sits underneath. Tapping the search pill moves on to the next city.
 */
export function TypedDestinationCard({
  cities = CITIES,
  autoPlay = false,
  buttonLabel = 'Current location',
  pillColor = '#ffffff',
  inkColor = '#141414',
  buttonColor = '#171717',
  buttonTextColor = '#ffffff',
  onSelect,
  onCurrentLocation,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  // Letters rather than UTF-16 units, so an accent or an emoji is typed in one key.
  const letters = useMemo(() => cities.map((city) => Array.from(city.name)), [cities]);
  const lengths = useMemo(() => letters.map((name) => name.length), [letters]);
  // The first city fully typed, with the caret at the start of a blink.
  const restAt = -completeTime(0, lengths[0] ?? 0) - CARET_BLINK * 8;

  const time = useSharedValue(0);
  const looping = useSharedValue(auto);
  // When the loop moves on to the next city.
  const nextAt = useSharedValue(FIRST_AT);
  // What is being typed: the text left from `fromCity` is deleted, then `to` is typed.
  const startedAt = useSharedValue(restAt);
  const fromCity = useSharedValue(0);
  const fromLength = useSharedValue(0);
  const to = useSharedValue(0);
  // The photo under the one fading in.
  const base = useSharedValue(0);
  // Taps on the pill, and how many of them the frame loop has acted on.
  const taps = useSharedValue(0);
  const handled = useSharedValue(0);

  const [shown, setShown] = useState(lengths[0] ?? 0);

  useEffect(() => {
    looping.set(auto);
    fromCity.set(0);
    fromLength.set(0);
    to.set(0);
    base.set(0);
    startedAt.set(time.get() + restAt);
    nextAt.set(time.get() + FIRST_AT);
  }, [auto, restAt, base, fromCity, fromLength, looping, nextAt, startedAt, time, to]);

  const select = (index: number) => onSelect?.(index);

  /** Starts deleting whatever is shown at `now` and typing the next city. */
  const begin = (now: number) => {
    'worklet';
    if (lengths.length < 2) return;
    const elapsed = now - startedAt.get();
    const text = typedText(elapsed, fromCity.get(), fromLength.get(), to.get(), lengths[to.get()]);
    if (elapsed >= completeTime(fromLength.get(), lengths[to.get()])) base.set(to.get());
    fromCity.set(Math.floor(text / CODE));
    fromLength.set(text % CODE);
    to.set((to.get() + 1) % lengths.length);
    // Under reduced motion the name is swapped at once and only the photo fades.
    startedAt.set(reducedMotion ? now - completeTime(fromLength.get(), lengths[to.get()]) : now);
  };

  useFrameCallback((frame) => {
    const now = time.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(now);
    if (taps.get() !== handled.get()) {
      // A tap on the pill: on to the next city now; the loop waits before it carries on.
      handled.set(taps.get());
      begin(now);
      nextAt.set(now + completeTime(fromLength.get(), lengths[to.get()] ?? 0) + USER_HOLD);
      scheduleOnRN(select, to.get());
    } else if (looping.get() && now >= nextAt.get()) {
      begin(nextAt.get());
      nextAt.set(nextAt.get() + CITY_TIME);
    }
  });

  useAnimatedReaction(
    () => typedText(time.get() - startedAt.get(), fromCity.get(), fromLength.get(), to.get(), lengths[to.get()] ?? 0),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );

  const since = useDerivedValue(
    () => time.get() - startedAt.get() - completeTime(fromLength.get(), lengths[to.get()] ?? 0),
  );
  const pinStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: reducedMotion ? 0 : pinHop(since.get()) }],
  }));
  const caretStyle = useAnimatedStyle(() => ({
    opacity: reducedMotion || since.get() < 0 || since.get() % (CARET_BLINK * 2) < CARET_BLINK ? 1 : 0,
  }));

  const city = Math.floor(shown / CODE);
  const text = (letters[city] ?? []).slice(0, shown % CODE).join('');

  return (
    <View style={styles.card}>
      <View style={styles.photo} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        {cities.map((item, index) => (
          <Photo key={index} city={item} index={index} to={to} base={base} since={since} still={reducedMotion} />
        ))}
      </View>

      <PressablePill
        style={styles.pillSlot}
        scale={0.98}
        label={cities[city]?.name ?? ''}
        hint="Shows the next city"
        onPress={() => taps.set(taps.get() + 1)}
      >
        <View style={[styles.pill, { backgroundColor: pillColor }]}>
          <Animated.View style={[styles.pin, pinStyle]}>
            <Svg width={PIN_WIDTH} height={PIN_HEIGHT} viewBox={`0 0 ${PIN_WIDTH} ${PIN_HEIGHT}`}>
              <Path d={PIN} fill={inkColor} />
              <Circle cx={PIN_WIDTH / 2} cy={PIN_WIDTH / 2} r={PIN_HOLE} fill={pillColor} />
            </Svg>
          </Animated.View>
          <View style={styles.field}>
            <Text style={[styles.typed, { color: inkColor }]} numberOfLines={1}>
              {text}
            </Text>
            <Animated.View style={[styles.caret, { backgroundColor: inkColor }, caretStyle]} />
          </View>
        </View>
      </PressablePill>

      <PressablePill style={styles.buttonSlot} scale={0.97} label={buttonLabel} onPress={() => onCurrentLocation?.()}>
        <View style={[styles.button, { backgroundColor: buttonColor }]}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <Circle cx={10} cy={10} r={TARGET_RING} fill="none" stroke={buttonTextColor} strokeWidth={TARGET_STROKE} />
            <Circle cx={10} cy={10} r={TARGET_DOT} fill={buttonTextColor} />
            <Path d={TARGET_TICKS} stroke={buttonTextColor} strokeWidth={TARGET_STROKE} strokeLinecap="round" />
          </Svg>
          <Text style={[styles.buttonLabel, { color: buttonTextColor }]} numberOfLines={1}>
            {buttonLabel}
          </Text>
        </View>
      </PressablePill>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
  },
  photo: {
    position: 'absolute',
    left: PHOTO_LEFT,
    top: 0,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  photoLayer: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
  },
  pillSlot: {
    position: 'absolute',
    left: PILL_LEFT,
    top: PILL_TOP,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
  },
  pill: {
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    boxShadow: PILL_SHADOW,
  },
  pin: {
    position: 'absolute',
    left: PIN_LEFT,
    top: (PILL_HEIGHT - PIN_HEIGHT) / 2,
  },
  field: {
    position: 'absolute',
    left: TEXT_LEFT,
    top: 0,
    width: PILL_WIDTH - TEXT_LEFT,
    height: PILL_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: CARET_GAP,
  },
  typed: {
    fontSize: 27,
    lineHeight: 34,
    fontWeight: '400',
    letterSpacing: -0.4,
  },
  caret: {
    width: CARET_WIDTH,
    height: CARET_HEIGHT,
    borderRadius: CARET_WIDTH / 2,
  },
  buttonSlot: {
    position: 'absolute',
    left: 0,
    top: BUTTON_TOP,
    width: WIDTH,
    height: BUTTON_HEIGHT,
  },
  button: {
    width: WIDTH,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: BUTTON_GAP,
  },
  buttonLabel: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
});
