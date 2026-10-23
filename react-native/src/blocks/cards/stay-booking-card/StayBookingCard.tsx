import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Path, Polyline } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type Stay = {
  name: string;
  address: string;
  beds: number;
  baths: number;
  /** Floor area in square feet. */
  area: number;
  /** Price per night in whole dollars. */
  price: number;
  photo: string;
  /** The deep colour the photo sinks into under the texts, as #rrggbb; also shown flat until the photo arrives. */
  tint: string;
};

type Props = {
  /** The stays, in a ring. At least three. */
  stays?: Stay[];
  /** Swipes through the stays and reserves the last one, as if a finger did it. */
  autoPlay?: boolean;
  bedsLabel?: string;
  bathsLabel?: string;
  areaLabel?: string;
  currency?: string;
  reserveLabel?: string;
  reservedLabel?: string;
  /** Called with the index of the stay the card is settling on. */
  onChange?: (index: number) => void;
  /** Called with the index of the stay when it is reserved. */
  onReserve?: (index: number) => void;
};

const WIDTH = 330;
const HEIGHT = 440;
const RADIUS = 26;
const PAD = 16;
// The photo is wider than the card, so it can lag behind its slide without showing an edge.
const PHOTO_WIDTH = WIDTH * 1.6;
// Share of a card's width the photo stays behind over one stay.
const PARALLAX = 0.3;
// The photo is blurred from BLUR_FROM down, fully so at BLUR_TO; the tint starts at TINT_Y.
const BLUR = 14;
const BLUR_FROM = 250;
const BLUR_TO = 304;
const TINT_Y = 215;

const DOTS_Y = 267;
const NAME_Y = 293;
const ADDRESS_Y = 324;
const FACTS_Y = 349;
// The row with the price and the button.
const ROW_Y = 382;
const ROW_HEIGHT = 42;
const PRICE_WIDTH = 82;
const BUTTON_WIDTH = 204;
const DIGIT = 12.5;
const PRICE_LINE = 26;
// How far the check moves left to make room for "Reserved", and where that word sits.
const CHECK_SHIFT = 37;
const RESERVED_SHIFT = 12;
// Length of the check mark's two strokes.
const CHECK_LENGTH = 18.04;

// Points the name travels over one stay; each line below travels STAGGER more.
const TRAVEL = 70;
const STAGGER = 34;

// The spring that carries the stays to rest (mass 1).
const STIFFNESS = 150;
const DAMPING = 20;
// Finger travel that changes one stay.
const DRAG_SPAN = 260;
const DRAG_SLOP = 8;
// A release lands on the stay the slide would reach after this many seconds at its speed.
const THROW_TIME = 0.18;

// Seconds the button takes to confirm, and to go back when the stay changes.
const RESERVE = 1.5;
const UNRESERVE = 0.35;

// autoPlay (seconds): a swipe at FIRST and every STEP until the last stay, which is reserved one
// step later and kept for KEEP before the swipe back to the first. With three stays one round
// takes LOOP.
const LOOP = 9.6;
const FIRST = 0.8;
const STEP = 2.2;
const KEEP = 2.4;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 2.5;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const photo = (id: string) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=1056&h=880&q=80`;

const STAYS: Stay[] = [
  {
    name: 'The Hill Guest House',
    address: 'Alpweg 12, Lauterbrunnen, 3822',
    beds: 2,
    baths: 1,
    area: 1150,
    price: 620,
    photo: photo('1464822759023-fed622ff2c3b'),
    tint: '#2f4a1c',
  },
  {
    name: 'Casa Caldera',
    address: 'Nomikou 7, Oia, Santorini 847 02',
    beds: 3,
    baths: 2,
    area: 1480,
    price: 895,
    photo: photo('1570077188670-e3a8d69ac5ff'),
    tint: '#1c3a5e',
  },
  {
    name: 'Lagoon Water Villa',
    address: 'Jetty 3, Baa Atoll, Maldives',
    beds: 1,
    baths: 1,
    area: 920,
    price: 740,
    photo: photo('1573843981267-be1999ff37cd'),
    tint: '#0e4a52',
  },
];

type Icon = 'map' | 'bed' | 'bath' | 'area';

// Polylines of each icon in a 16 × 16 box, as "x,y x,y" points.
const STROKES: Record<Icon, string[]> = {
  map: ['2,4 6,2.5 10,4 14,2.5 14,12 10,13.5 6,12 2,13.5 2,4', '6,2.5 6,12', '10,4 10,13.5'],
  bed: ['1.8,13 1.8,4.5', '1.8,10.5 14.2,10.5', '14.2,13 14.2,8.2 7.2,8.2 7.2,10.5', '3.4,8.4 5.6,8.4'],
  bath: ['1.5,8.5 14.5,8.5', '2.5,8.5 3.2,12 4.6,13 11.4,13 12.8,12 13.5,8.5', '4,8.5 4,3.6 5.2,2.6 6.6,3.6'],
  area: ['2.5,2.5 13.5,2.5 13.5,13.5 2.5,13.5 2.5,2.5', '5.5,10.5 10.5,5.5', '8,5.5 10.5,5.5 10.5,8', '5.5,8 5.5,10.5 8,10.5'],
};

// The blurred copy of the photo fades in over three bands, as [top, bottom, opacity].
const BLUR_BANDS = [
  [BLUR_FROM, (BLUR_FROM + BLUR_TO) / 2, 1 / 3],
  [(BLUR_FROM + BLUR_TO) / 2, BLUR_TO, 2 / 3],
  [BLUR_TO, HEIGHT, 1],
];

const AnimatedPath = Animated.createAnimatedComponent(Path);

function wrapIndex(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

function segment(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}

function easeOut(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(2 - 2 * t, 3) / 2;
}

/** Ease-out that runs about 10% past its end before it settles. */
function easeOutBack(t: number) {
  'worklet';
  return 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2);
}

function smooth(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

/** Where stay `index` stands: 0 in view, 1 one card to the right, −1 one card to the left. */
function centered(index: number, pos: number, count: number) {
  'worklet';
  let place = (index - pos) % count;
  if (place <= -count / 2) place += count;
  if (place > count / 2) place -= count;
  return place;
}

/** How much the stay at `place` is the one in view, 0…1. */
function weight(place: number) {
  'worklet';
  return smooth(1 - Math.abs(place));
}

/** Opacity of text line number `order` of the stay at `place`: the lower the line, the sooner it goes. */
function lineFade(place: number, order: number) {
  'worklet';
  return smooth(1 - Math.abs(place) * (2.2 + 0.5 * order));
}

/** Reduced motion: the photos stay in place and the one further away fades over the nearer one. */
function crossFade(place: number) {
  'worklet';
  return Math.abs(place) <= 0.5 ? 1 : Math.max(0, 1 - Math.abs(place));
}

/** Opacity of a digit that rolls through its column at `place`: it fades only as it leaves. */
function rollFade(place: number) {
  'worklet';
  return smooth((0.75 - Math.abs(place)) / 0.3);
}

/** Each column of the price changes during its own part of the slide, the last digit first. */
function columnPosition(pos: number, column: number, digits: number) {
  'worklet';
  const whole = Math.floor(pos);
  const from = 0.1 * (digits - 1 - column);
  return whole + easeInOut(segment(pos - whole, from, from + 0.6));
}

/** The character of `price` in `column` of a right-aligned field `digits` wide. */
function digit(price: number, column: number, digits: number) {
  const text = String(price);
  const index = column - (digits - text.length);
  return index < 0 ? '' : text[index];
}

type Motion = {
  pos: SharedValue<number>;
  index: number;
  count: number;
  reduced: boolean;
};

/** A small line icon, drawn from the same strokes in every stack. */
function Glyph({ icon }: { icon: Icon }) {
  return (
    <Svg width={14} height={14} viewBox="0 0 16 16" fill="none">
      {STROKES[icon].map((points) => (
        <Polyline key={points} points={points} stroke="rgba(255,255,255,0.74)" strokeWidth={1.37} strokeLinecap="round" strokeLinejoin="round" />
      ))}
    </Svg>
  );
}

/** One stay's slide: it moves a full card per stay while the photo inside it lags behind. */
function Slide({ stay, pos, index, count, reduced }: { stay: Stay } & Motion) {
  const slide = useAnimatedStyle(() => {
    const place = centered(index, pos.get(), count);
    return {
      opacity: reduced ? crossFade(place) : 1,
      zIndex: Math.abs(place) > 0.5 ? 1 : 0,
      transform: [{ translateX: reduced ? 0 : place * WIDTH }],
    };
  });
  const picture = useAnimatedStyle(() => ({
    transform: [{ translateX: reduced ? 0 : -centered(index, pos.get(), count) * WIDTH * PARALLAX }],
  }));
  return (
    <Animated.View style={[styles.slide, slide]}>
      {/* The photo over a flat tint, faded in when it arrives. */}
      <Animated.View style={[styles.picture, { backgroundColor: stay.tint }, picture]}>
        <Image source={stay.photo} style={styles.photo} contentFit="cover" transition={200} accessible={false} />
        {BLUR_BANDS.map(([top, bottom, opacity]) => (
          <View key={top} style={[styles.band, { top, height: bottom - top, opacity }]}>
            <Image source={stay.photo} style={[styles.photo, { top: -top }]} contentFit="cover" blurRadius={BLUR} accessible={false} />
          </View>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** The tint under the texts, in the colour of one stay. */
function Tint({ color, pos, index, count }: { color: string } & Motion) {
  const style = useAnimatedStyle(() => ({ opacity: weight(centered(index, pos.get(), count)) }));
  return (
    <Animated.View pointerEvents="none" style={[styles.tint, style]}>
      <LinearGradient colors={[`${color}00`, `${color}8c`, `${color}eb`, `${color}f7`]} locations={[0, 0.38, 0.66, 1]} style={StyleSheet.absoluteFill} />
    </Animated.View>
  );
}

function Dot({ pos, index, count }: Motion) {
  const style = useAnimatedStyle(() => {
    const on = weight(centered(index, pos.get(), count));
    return { width: 6 + 10 * on, opacity: 0.45 + 0.55 * on };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

function Fact({ icon, label, value }: { icon: Icon; label: string; value: number }) {
  return (
    <View style={styles.fact}>
      <Glyph icon={icon} />
      <Text style={styles.factLabel} numberOfLines={1}>
        {label}: <Text style={styles.factValue}>{value}</Text>
      </Text>
    </View>
  );
}

/** Name, address and facts of one stay: each line travels a little further than the one above. */
function Texts({
  stay,
  labels,
  pos,
  index,
  count,
  reduced,
}: { stay: Stay; labels: [string, string, string] } & Motion) {
  const name = useAnimatedStyle(() => {
    const place = centered(index, pos.get(), count);
    return { opacity: lineFade(place, 0), transform: [{ translateX: reduced ? 0 : place * TRAVEL }] };
  });
  const address = useAnimatedStyle(() => {
    const place = centered(index, pos.get(), count);
    return { opacity: lineFade(place, 1), transform: [{ translateX: reduced ? 0 : place * (TRAVEL + STAGGER) }] };
  });
  const facts = useAnimatedStyle(() => {
    const place = centered(index, pos.get(), count);
    return { opacity: lineFade(place, 2), transform: [{ translateX: reduced ? 0 : place * (TRAVEL + STAGGER * 2) }] };
  });
  return (
    <>
      <Animated.Text style={[styles.name, name]} numberOfLines={1}>
        {stay.name}
      </Animated.Text>
      <Animated.View style={[styles.address, address]}>
        <Glyph icon="map" />
        <Text style={styles.addressLabel} numberOfLines={1}>
          {stay.address}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.facts, facts]}>
        <View style={styles.factStart}>
          <Fact icon="bed" label={labels[0]} value={stay.beds} />
        </View>
        <View style={styles.rule} />
        <View style={styles.factMiddle}>
          <Fact icon="bath" label={labels[1]} value={stay.baths} />
        </View>
        <View style={styles.rule} />
        <View style={styles.factEnd}>
          <Fact icon="area" label={labels[2]} value={stay.area} />
        </View>
      </Animated.View>
    </>
  );
}

/** One digit of one stay's price, rolling through its column. */
function Digit({ text, column, digits, pos, index, count, reduced }: { text: string; column: number; digits: number } & Motion) {
  const style = useAnimatedStyle(() => {
    const place = centered(index, columnPosition(pos.get(), column, digits), count);
    return {
      opacity: reduced ? Math.max(0, 1 - Math.abs(place)) : rollFade(place),
      transform: [{ translateY: reduced ? 0 : place * PRICE_LINE }],
    };
  });
  return (
    <Animated.Text style={[styles.price, styles.digit, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/**
 * A photo card for a place to stay: its lower part sinks, blurred, into a deep tint that carries the
 * name, the address, three facts, the price and a "Reserve Now" button. A sideways swipe changes the
 * stay: the photo slides with parallax, the texts follow one after another, the tint changes colour
 * and the price rolls like a counter. A tap on the button shrinks it to a circle, draws a check and
 * widens it again as "Reserved".
 */
export function StayBookingCard({
  stays = STAYS,
  autoPlay = false,
  bedsLabel = 'Bed',
  bathsLabel = 'Baths',
  areaLabel = 'Sqft',
  currency = '$',
  reserveLabel = 'Reserve Now',
  reservedLabel = 'Reserved',
  onChange,
  onReserve,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = stays.length;
  const digits = String(Math.max(...stays.map((stay) => stay.price))).length;

  // The stay in view, with fractions while it slides.
  const pos = useSharedValue(0);
  const target = useSharedValue(0);
  // Stays per second.
  const velocity = useSharedValue(0);
  // The stay that is reserved, or −1, and how far its button has come, 0…1.
  const reserved = useSharedValue(-1);
  const confirm = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Where the loop started counting from, and its clock.
  const base = useSharedValue(0);
  const clock = useSharedValue(0);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  const touching = useSharedValue(false);
  const previous = useSharedValue(0);

  const [current, setCurrent] = useState(0);
  const [booked, setBooked] = useState(false);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
    base.set(target.get());
  }, [auto, base, clock, hold, looping, target]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    let p = pos.get();
    let goal = target.get();
    let v = velocity.get();
    let before = previous.get();
    let taken = reserved.get();

    if (looping.get() && hold.get() <= 0) {
      const reserveAt = FIRST + STEP * (count - 1);
      const backAt = reserveAt + KEEP;
      const loop = LOOP + STEP * (count - 3);
      let t = clock.get() + dt;
      if (t >= loop) {
        // Once round the ring: the same stays under smaller numbers.
        t -= loop;
        p -= count;
        before -= count;
      }
      clock.set(t);
      let swipes = t >= backAt ? 1 : 0;
      for (let index = 0; index < count - 1; index++) if (t >= FIRST + STEP * index) swipes += 1;
      goal = base.get() + swipes;
      taken = t >= reserveAt && t < backAt ? wrapIndex(base.get() + count - 1, count) : -1;
    } else if (hold.get() > 0 && !touching.get()) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        if (p === goal && (confirm.get() === 0 || confirm.get() === 1)) {
          p = wrapIndex(Math.round(p), count);
          before = p;
          goal = p;
          base.set(p);
          clock.set(0);
        } else {
          hold.set(0.01);
        }
      }
    }

    if (touching.get()) {
      // The finger moves the stays; keep a smoothed speed for the release.
      v += ((p - before) / dt - v) * Math.min(1, dt * 30);
    } else {
      v += (STIFFNESS * (goal - p) - DAMPING * v) * dt;
      p += v * dt;
      if (Math.abs(goal - p) < 0.0004 && Math.abs(v) < 0.004) {
        p = goal;
        v = 0;
      }
    }

    // The button confirms while its stay is the one in view, and goes back as soon as it is not.
    const here = taken >= 0 && taken === wrapIndex(Math.round(goal), count);
    confirm.set(here ? Math.min(1, confirm.get() + dt / RESERVE) : Math.max(0, confirm.get() - dt / UNRESERVE));

    pos.set(p);
    previous.set(p);
    target.set(goal);
    velocity.set(v);
    reserved.set(taken);
  });

  const changed = (index: number) => {
    setCurrent(index);
    onChange?.(index);
  };
  useAnimatedReaction(
    () => wrapIndex(Math.round(target.get()), count),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(changed, now);
    },
  );
  useAnimatedReaction(
    () => reserved.get() >= 0 && reserved.get() === wrapIndex(Math.round(target.get()), count),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(setBooked, now);
    },
  );

  const pillStyle = useAnimatedStyle(() => {
    const t = confirm.get();
    const shrink = easeInOut(segment(t, 0, 0.22));
    const widen = easeOutBack(segment(t, 0.6, 0.92));
    return { width: reducedMotion ? BUTTON_WIDTH : BUTTON_WIDTH - (BUTTON_WIDTH - ROW_HEIGHT) * (shrink - widen) };
  });
  const reserveStyle = useAnimatedStyle(() => ({ opacity: 1 - segment(confirm.get(), 0, 0.12) }));
  const reservedStyle = useAnimatedStyle(() => ({ opacity: segment(confirm.get(), 0.72, 0.95) }));
  const checkStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: -CHECK_SHIFT * easeOutBack(segment(confirm.get(), 0.6, 0.92)) }],
  }));
  const checkProps = useAnimatedProps(() => ({
    strokeDashoffset: CHECK_LENGTH * (1 - easeOut(segment(confirm.get(), 0.24, 0.52))),
    opacity: confirm.get() > 0.24 ? 1 : 0,
  }));

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // A touch takes over from the loop, which starts again from rest a little later.
  const reserve = () => {
    tick();
    hold.set(USER_HOLD);
    if (reserved.get() === current) {
      reserved.set(-1);
    } else {
      reserved.set(current);
      onReserve?.(current);
    }
  };

  const finger = useRef({ x: 0, grabbed: 0, moved: false });

  const onGrant = (e: GestureResponderEvent) => {
    finger.current = { x: e.nativeEvent.pageX, grabbed: 0, moved: false };
  };

  const onMove = (e: GestureResponderEvent) => {
    const f = finger.current;
    const dx = e.nativeEvent.pageX - f.x;
    if (!f.moved) {
      if (Math.abs(dx) < DRAG_SLOP) return;
      f.moved = true;
      f.grabbed = pos.get();
      touching.set(true);
    }
    hold.set(USER_HOLD);
    // One stay at most per swipe.
    const home = Math.round(f.grabbed);
    pos.set(Math.min(home + 1, Math.max(home - 1, f.grabbed - dx / DRAG_SPAN)));
  };

  const onEnd = () => {
    const f = finger.current;
    if (!f.moved) return;
    f.moved = false;
    touching.set(false);
    hold.set(USER_HOLD);
    const home = Math.round(f.grabbed);
    const landing = Math.round(pos.get() + velocity.get() * THROW_TIME);
    const next = Math.min(home + 1, Math.max(home - 1, landing));
    if (next !== Math.round(target.get()) && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    target.set(next);
  };

  const turn = (by: number) => {
    hold.set(USER_HOLD);
    target.set(Math.round(target.get()) + by);
  };

  const motion = { pos, count, reduced: reducedMotion };
  const tint = stays[current].tint;

  return (
    <View style={styles.card}>
      <View
        style={styles.clip}
        accessibilityRole="adjustable"
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => turn(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
        onStartShouldSetResponder={() => true}
        onResponderTerminationRequest={() => false}
        onResponderGrant={onGrant}
        onResponderMove={onMove}
        onResponderRelease={onEnd}
        onResponderTerminate={onEnd}
      >
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          {stays.map((stay, index) => (
            <Slide key={index} stay={stay} index={index} {...motion} />
          ))}
        </View>
        {stays.map((stay, index) => (
          <Tint key={index} color={stay.tint} index={index} {...motion} />
        ))}

        <View pointerEvents="none" style={styles.dots}>
          {stays.map((_, index) => (
            <Dot key={index} index={index} {...motion} />
          ))}
        </View>
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          {stays.map((stay, index) => (
            <Texts key={index} stay={stay} labels={[bedsLabel, bathsLabel, areaLabel]} index={index} {...motion} />
          ))}
        </View>

        {/* The price: each digit rolls in its own column, the last one first. */}
        <View style={styles.pricePill} accessible accessibilityLabel={`${currency}${stays[current].price} per night`}>
          <Text style={styles.price}>{currency}</Text>
          {Array.from({ length: digits }, (_, column) => (
            <View key={column} style={styles.column}>
              {stays.map((stay, index) => (
                <Digit key={index} text={digit(stay.price, column, digits)} column={column} digits={digits} index={index} {...motion} />
              ))}
            </View>
          ))}
        </View>

        <Pressable style={styles.buttonSlot} onPress={reserve} accessibilityRole="button" accessibilityLabel={booked ? reservedLabel : reserveLabel}>
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.button,
                {
                  transform: [{ scale: pressed ? 0.97 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Animated.View style={[styles.pill, pillStyle]} />
              <Animated.Text style={[styles.buttonLabel, reserveStyle]} numberOfLines={1}>
                {reserveLabel}
              </Animated.Text>
              <Animated.View style={[styles.check, checkStyle]}>
                <Svg width={16} height={16} fill="none">
                  <AnimatedPath
                    d="M2 8.5L6 12.5L14.5 3.5"
                    stroke={tint}
                    strokeWidth={2.4}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeDasharray={CHECK_LENGTH}
                    animatedProps={checkProps}
                  />
                </Svg>
              </Animated.View>
              <Animated.Text style={[styles.buttonLabel, styles.reserved, { color: tint }, reservedStyle]} numberOfLines={1}>
                {reservedLabel}
              </Animated.Text>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    boxShadow: '0 18px 52px rgba(30,34,40,0.22)',
  },
  clip: {
    ...StyleSheet.absoluteFill,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  slide: {
    ...StyleSheet.absoluteFill,
    overflow: 'hidden',
  },
  picture: {
    position: 'absolute',
    left: -(PHOTO_WIDTH - WIDTH) / 2,
    top: 0,
    width: PHOTO_WIDTH,
    height: HEIGHT,
  },
  photo: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PHOTO_WIDTH,
    height: HEIGHT,
  },
  band: {
    position: 'absolute',
    left: 0,
    width: PHOTO_WIDTH,
    overflow: 'hidden',
  },
  tint: {
    position: 'absolute',
    left: 0,
    top: TINT_Y,
    width: WIDTH,
    height: HEIGHT - TINT_Y,
  },
  dots: {
    position: 'absolute',
    left: 0,
    top: DOTS_Y,
    width: WIDTH,
    height: 6,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 5,
  },
  dot: {
    height: 6,
    borderRadius: 3,
    backgroundColor: '#ffffff',
  },
  name: {
    position: 'absolute',
    left: PAD,
    top: NAME_Y,
    color: '#ffffff',
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '600',
  },
  address: {
    position: 'absolute',
    left: PAD,
    top: ADDRESS_Y,
    height: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  addressLabel: {
    color: 'rgba(255,255,255,0.74)',
    fontSize: 13,
    lineHeight: 18,
  },
  facts: {
    position: 'absolute',
    left: PAD,
    top: FACTS_Y,
    width: WIDTH - PAD * 2,
    height: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  factStart: {
    flex: 1,
    alignItems: 'flex-start',
  },
  factMiddle: {
    flex: 1,
    alignItems: 'center',
  },
  factEnd: {
    flex: 1,
    alignItems: 'flex-end',
  },
  rule: {
    width: 1,
    height: 20,
    backgroundColor: 'rgba(255,255,255,0.25)',
  },
  fact: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  factLabel: {
    color: 'rgba(255,255,255,0.74)',
    fontSize: 12.5,
    lineHeight: 17,
    fontVariant: ['tabular-nums'],
  },
  factValue: {
    color: '#ffffff',
    fontWeight: '600',
  },
  pricePill: {
    position: 'absolute',
    left: PAD,
    top: ROW_Y,
    width: PRICE_WIDTH,
    height: ROW_HEIGHT,
    borderRadius: ROW_HEIGHT / 2,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    backgroundColor: 'rgba(255,255,255,0.2)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  price: {
    color: '#ffffff',
    fontSize: 20,
    lineHeight: PRICE_LINE,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  column: {
    width: DIGIT,
    height: PRICE_LINE,
    overflow: 'hidden',
  },
  digit: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DIGIT,
    textAlign: 'center',
  },
  buttonSlot: {
    position: 'absolute',
    left: WIDTH - PAD - BUTTON_WIDTH,
    top: ROW_Y,
  },
  button: {
    width: BUTTON_WIDTH,
    height: ROW_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pill: {
    position: 'absolute',
    height: ROW_HEIGHT,
    borderRadius: ROW_HEIGHT / 2,
    backgroundColor: '#ffffff',
  },
  buttonLabel: {
    position: 'absolute',
    color: '#111111',
    fontSize: 14.5,
    lineHeight: 20,
    fontWeight: '600',
  },
  reserved: {
    transform: [{ translateX: RESERVED_SHIFT }],
  },
  check: {
    position: 'absolute',
  },
});
