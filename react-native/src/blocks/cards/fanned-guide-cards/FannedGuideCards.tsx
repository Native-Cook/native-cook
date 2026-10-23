import { Canvas, Path, PathOp, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
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
import { scheduleOnRN } from 'react-native-worklets';

type Place = {
  name: string;
  location: string;
  /** Address of the photo. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Props = {
  /** Taps the side cards in turn, in a loop that ends where it began. */
  autoPlay?: boolean;
  /** Three places: the left, the front and the right card. */
  places?: [Place, Place, Place];
  tag?: string;
  action?: string;
  /** Draws the faint diagonal grid behind the cards. */
  grid?: boolean;
  /** Called with the place's index when a tap or a drag brings its card to the front. */
  onSelect?: (index: number) => void;
  /** Called with the place's index when the pill of the front card is tapped. */
  onBook?: (index: number) => void;
};

// The box the fan is laid out in; the side cards reach a little beyond it.
const WIDTH = 350;
const HEIGHT = 330;

const CARD_WIDTH = 200;
const CARD_HEIGHT = 300;
const RADIUS = 20;
// The white frame around the photo.
const FRAME = 8;
const PHOTO_WIDTH = 184;
const PHOTO_HEIGHT = 216;
const PHOTO_RADIUS = 14;
const NAME_HEIGHT = 34;
const BUTTON_HEIGHT = 34;
// The round badge in the photo's top right corner: its radius, its inset from the card's edges,
// the gap the photo leaves around it and the radius that rounds the cut's shoulders.
const BADGE = 16;
const BADGE_INSET = 6;
const BADGE_GAP = 4;
const SHOULDER = 5;

type Pose = {
  x: number;
  y: number;
  /** Degrees. */
  angle: number;
  scale: number;
  /** 1 in front, 0 at the sides. */
  front: number;
};

// The left, front and right slot.
const SLOTS: Pose[] = [
  { x: -100, y: -26, angle: -8, scale: 0.7, front: 0 },
  { x: 0, y: 6, angle: -3, scale: 0.9, front: 1 },
  { x: 100, y: 12, angle: 12, scale: 0.7, front: 0 },
];

// The spring every card moves on (mass 1), and when it counts as settled.
const STIFFNESS = 180;
const DAMPING = 16;
const SETTLED = 1.4;
// In a swap the card that leaves the front starts this long after the one that comes in, and the
// third card is nudged this long after that.
const SWAP_STAGGER = 0.04;
// How far the third card leans away from a swap, in points and degrees.
const KICK_X = 9;
const KICK_ANGLE = 4;

// Dragging the front card: it follows the finger, a third as much vertically, tilts with the
// distance, and goes to the side once released this far out.
const DRAG_SLOP = 8;
const DRAG_Y = 0.3;
const TILT_PER_POINT = 0.07;
const TILT_MAX = 12;
const DRAG_THROW = 70;

// On appear the cards deal out from one pile.
const DEAL_AT = [0.35, 0.43, 0.51];
const PILE_SCALE = 0.8;
const PILE_ANGLES = [-7, 0, 5];

// autoPlay loop (seconds): taps on the right, left, left and right card bring every card to the
// front once and leave the fan as it started.
const TAPS_AT = [1.5, 3.7, 5.9, 8.1];
const TAP_SIDES = [2, 0, 0, 2];
const LOOP = 8.8;
// After a touch the loop waits this long before it starts again from rest.
const USER_HOLD = 2;

const GRID_SIZE = 500;
const GRID_PITCH = 24;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// The arrow pointing up and to the right, in a 12-point box.
const ARROW = 'M2 10L10 2M3.5 2L10 2L10 8.5';

type Card = {
  /** 0 left, 1 front, 2 right. */
  slot: number;
  from: Pose;
  /** When it set off for its slot. */
  at: number;
  /** A nudge away from a swap it took no part in: its direction and when it began. */
  kick: number;
  kickAt: number;
};

/**
 * Where the three cards are and how they got there. A card's place on screen is a function of
 * time: it springs from the pose it had when it last changed slot to the pose of its slot.
 */
type Deck = {
  cards: Card[];
  /** The card under the finger (or -1) and how far it has been dragged. */
  drag: { card: number; x: number; y: number };
};

const NO_DRAG = { card: -1, x: 0, y: 0 };

/** The fan at rest, or, with `dealtAt`, a pile that deals itself out from that moment. */
function newDeck(dealtAt: number | null): Deck {
  return {
    cards: SLOTS.map((slot, index) => {
      if (dealtAt == null) return { slot: index, from: slot, at: -10, kick: 0, kickAt: 0 };
      const pile = { x: 0, y: SLOTS[1].y, angle: PILE_ANGLES[index], scale: PILE_SCALE, front: slot.front };
      return { slot: index, from: pile, at: dealtAt + DEAL_AT[index], kick: 0, kickAt: 0 };
    }),
    drag: NO_DRAG,
  };
}

/** A spring let go at 0 and heading for 1, `t` seconds on (zero before it starts). */
function springStep(t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= SETTLED) return 1;
  const decay = DAMPING / 2;
  const pulse = Math.sqrt(STIFFNESS - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(pulse * t) + (decay / pulse) * Math.sin(pulse * t));
}

/** The same spring struck at rest: it swings out and back to 0. */
function springKick(t: number) {
  'worklet';
  if (t <= 0 || t >= SETTLED) return 0;
  const decay = DAMPING / 2;
  const pulse = Math.sqrt(STIFFNESS - decay * decay);
  return Math.exp(-decay * t) * Math.sin(pulse * t);
}

function poseOf(deck: Deck, index: number, time: number, calm: boolean): Pose {
  'worklet';
  const card = deck.cards[index];
  const to = SLOTS[card.slot];
  // Reduced motion: a card jumps to its slot.
  const p = calm ? (time >= card.at ? 1 : 0) : springStep(time - card.at);
  const nudge = calm ? 0 : card.kick * springKick(time - card.kickAt);
  const pose = {
    x: card.from.x + (to.x - card.from.x) * p + KICK_X * nudge,
    y: card.from.y + (to.y - card.from.y) * p,
    angle: card.from.angle + (to.angle - card.from.angle) * p + KICK_ANGLE * nudge,
    scale: card.from.scale + (to.scale - card.from.scale) * p,
    front: card.from.front + (to.front - card.from.front) * p,
  };
  if (deck.drag.card === index) {
    pose.x += deck.drag.x;
    pose.y += deck.drag.y * DRAG_Y;
    pose.angle += Math.min(TILT_MAX, Math.max(-TILT_MAX, deck.drag.x * TILT_PER_POINT));
  }
  return pose;
}

/**
 * The card at `side` (0 left, 2 right) comes to the front, the front card takes its slot and the
 * third card leans away for a moment.
 */
function swapped(deck: Deck, side: number, time: number, calm: boolean): Deck {
  'worklet';
  const coming = deck.cards.findIndex((card) => card.slot === side);
  const leaving = deck.cards.findIndex((card) => card.slot === 1);
  if (coming < 0 || leaving < 0) return deck;
  const third = 3 - coming - leaving;
  const cards = deck.cards.slice();
  cards[coming] = { slot: 1, from: poseOf(deck, coming, time, calm), at: time, kick: 0, kickAt: 0 };
  cards[leaving] = { slot: side, from: poseOf(deck, leaving, time, calm), at: time + SWAP_STAGGER, kick: 0, kickAt: 0 };
  cards[third] = { ...cards[third], kick: side === 2 ? -1 : 1, kickAt: time + SWAP_STAGGER * 2 };
  return { cards, drag: NO_DRAG };
}

/** The dragged card is let go and springs back to its slot. */
function settled(deck: Deck, time: number, calm: boolean): Deck {
  const index = deck.drag.card;
  if (index < 0) return deck;
  const cards = deck.cards.slice();
  cards[index] = { slot: cards[index].slot, from: poseOf(deck, index, time, calm), at: time, kick: 0, kickAt: 0 };
  return { cards, drag: NO_DRAG };
}

/**
 * What the photo gives up to the badge: the piece between its rounded top right corner and a cut
 * that curves in around the badge. Painted in the card's white over the photo.
 */
function photoCorner() {
  const w = PHOTO_WIDTH;
  const h = PHOTO_HEIGHT;
  const r = PHOTO_RADIUS;
  // The badge's centre in the photo, the radius of the cut around it and of its shoulders.
  const cx = w + FRAME - BADGE_INSET - BADGE;
  const cy = BADGE_INSET + BADGE - FRAME;
  const cut = BADGE + BADGE_GAP;
  const f = SHOULDER;
  const reach = Math.sqrt((cut + f) * (cut + f) - (cy - f) * (cy - f));
  const lean = (Math.atan2(cy - f, reach) * 180) / Math.PI;
  const circle = (x: number, y: number, radius: number) => Skia.XYWHRect(x - radius, y - radius, radius * 2, radius * 2);

  const shape = Skia.Path.Make();
  shape.moveTo(r, 0);
  shape.arcToOval(circle(cx - reach, f, f), -90, 90 + lean, false);
  shape.arcToOval(circle(cx, cy, cut), 180 + lean, -(90 + lean * 2), false);
  shape.arcToOval(circle(w - f, cy + reach, f), 270 - lean, 90 + lean, false);
  shape.arcToOval(circle(w - r, h - r, r), 0, 90, false);
  shape.arcToOval(circle(r, h - r, r), 90, 90, false);
  shape.arcToOval(circle(r, r, r), 180, 90, false);
  shape.close();

  const whole = Skia.Path.Make();
  whole.addRect(Skia.XYWHRect(0, 0, w, h));
  return Skia.Path.MakeFromOp(whole, shape, PathOp.Difference) ?? Skia.Path.Make();
}

function gridLines() {
  let lines = '';
  for (let offset = -GRID_SIZE; offset <= GRID_SIZE; offset += GRID_PITCH) {
    lines += `M${offset} 0L${offset + GRID_SIZE} ${GRID_SIZE}M${offset + GRID_SIZE} 0L${offset} ${GRID_SIZE}`;
  }
  return lines;
}

/** A diagonal grid that fades out towards its edges, so it needs no frame. */
function Grid() {
  const lines = useMemo(() => gridLines(), []);
  return (
    <Canvas pointerEvents="none" style={styles.grid}>
      <Path path={lines} style="stroke" strokeWidth={1}>
        <RadialGradient c={vec(GRID_SIZE / 2, GRID_SIZE / 2)} r={GRID_SIZE / 2} colors={['rgba(125,138,130,0.34)', 'rgba(125,138,130,0)']} />
      </Path>
    </Canvas>
  );
}

type CardProps = {
  place: Place;
  index: number;
  tag: string;
  action: string;
  /** Whether this card is the one in front, for its pill. */
  isFront: boolean;
  deck: SharedValue<Deck>;
  time: SharedValue<number>;
  calm: boolean;
  onTap: (index: number) => void;
  onDrag: (index: number, x: number, y: number) => void;
  onDrop: (index: number, x: number) => void;
  onBook: (index: number) => void;
};

function GuideCard({ place, index, tag, action, isFront, deck, time, calm, onTap, onDrag, onDrop, onBook }: CardProps) {
  const pose = useDerivedValue(() => poseOf(deck.get(), index, time.get(), calm));
  const corner = useMemo(() => photoCorner(), []);

  const placement = useAnimatedStyle(() => {
    const { x, y, angle, scale, front } = pose.get();
    return {
      transform: [{ translateX: x }, { translateY: y }, { rotate: `${angle}deg` }, { scale }],
      // The card that is more in front lies on top, so two cards trade places as they pass.
      zIndex: Math.round(Math.min(1, Math.max(0, front)) * 100) + index,
    };
  });
  const frontOnly = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, pose.get().front)) }));

  // --- A touch that does not move is a tap; one that moves drags the card, if it is in front. ---
  const touch = useRef({ x: 0, y: 0, moved: false });

  const onGrant = (e: GestureResponderEvent) => {
    touch.current = { x: e.nativeEvent.pageX, y: e.nativeEvent.pageY, moved: false };
  };

  const onMove = (e: GestureResponderEvent) => {
    const t = touch.current;
    const dx = e.nativeEvent.pageX - t.x;
    const dy = e.nativeEvent.pageY - t.y;
    if (!t.moved && Math.hypot(dx, dy) < DRAG_SLOP) return;
    t.moved = true;
    onDrag(index, dx, dy);
  };

  const onRelease = (e: GestureResponderEvent) => {
    if (touch.current.moved) onDrop(index, e.nativeEvent.pageX - touch.current.x);
    else onTap(index);
  };

  return (
    <Animated.View
      style={[styles.card, placement]}
      onStartShouldSetResponder={() => true}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={onRelease}
      onResponderTerminate={() => onDrop(index, 0)}
      accessible
      accessibilityLabel={`${place.name}, ${place.location}. ${tag}`}
    >
      {/* The front card casts a second, deeper shadow. */}
      <Animated.View style={[styles.sheet, styles.sheetFront, frontOnly]} />
      <View style={[styles.sheet, styles.sheetSide]} />

      <View style={[styles.photo, { backgroundColor: place.tint }]}>
        <Image source={{ uri: place.photo }} style={styles.fill} contentFit="cover" transition={200} />
        <Canvas style={styles.fill}>
          <Path path={corner} color="#ffffff" />
        </Canvas>
      </View>

      <View style={styles.tag}>
        <Text style={styles.tagLabel} numberOfLines={1}>
          {tag}
        </Text>
      </View>

      <View style={styles.badge}>
        <Canvas style={styles.arrow}>
          <Path path={ARROW} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round" color="#ffffff" />
        </Canvas>
      </View>

      <View style={styles.nameRow}>
        <Text style={styles.name} numberOfLines={1}>
          {place.name}
        </Text>
        <Text style={styles.location} numberOfLines={1}>
          {place.location}
        </Text>
      </View>

      <View pointerEvents={isFront ? 'auto' : 'none'} style={styles.buttonSlot}>
        <Pressable onPress={() => onBook(index)} accessibilityRole="button" accessibilityLabel={action}>
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
              <Text style={[styles.buttonLabel, styles.buttonLabelIdle]} numberOfLines={1}>
                {action}
              </Text>
              {/* The black pill fades onto the card that comes to the front. */}
              <Animated.View style={[styles.buttonFront, frontOnly]}>
                <Text style={styles.buttonLabel} numberOfLines={1}>
                  {action}
                </Text>
              </Animated.View>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </Animated.View>
  );
}

/**
 * Three white photo cards fanned out, the middle one in front. Tapping a side card brings it to
 * the front on a spring while the front card takes its place and the third leans away; dragging the
 * front card sideways tilts it and, far enough, sends it to that side. The black "Book Now" pill
 * belongs to whichever card is in front.
 */
export function FannedGuideCards({
  autoPlay = false,
  places = [
    {
      name: 'Great Pyramid',
      location: 'Giza, Egypt',
      photo: 'https://images.unsplash.com/photo-1503177119275-0aa32b3a9368?auto=format&fit=crop&w=400&h=470&q=80',
      tint: '#b5a794',
    },
    {
      name: 'Cappadocia',
      location: 'Göreme, Turkey',
      photo: 'https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=470&q=80',
      tint: '#7e8681',
    },
    {
      name: 'Taj Mahal',
      location: 'Agra, India',
      photo: 'https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=400&h=470&q=80',
      tint: '#969faf',
    },
  ],
  tag = 'Top rated',
  action = 'Book Now',
  grid = true,
  onSelect,
  onBook,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const touching = useSharedValue(false);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  const deck = useSharedValue<Deck>(newDeck(reducedMotion ? null : 0));
  // The card in front, for the pill that only it can press.
  const [front, setFront] = useState(1);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const now = time.get() + dt;
    if (touching.get()) {
      hold.set(USER_HOLD);
    } else if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // Time is up: the loop starts again from rest.
      if (hold.get() <= 0 && looping.get()) clock.set(0);
    } else if (looping.get()) {
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      for (let tap = 0; tap < TAPS_AT.length; tap++) {
        if (clock.get() < TAPS_AT[tap] && t >= TAPS_AT[tap]) deck.set(swapped(deck.get(), TAP_SIDES[tap], now, reducedMotion));
      }
      clock.set(t);
    }
    time.set(now);
  });

  useAnimatedReaction(
    () => deck.get().cards.findIndex((card) => card.slot === 1),
    (now, old) => {
      if (now !== old) scheduleOnRN(setFront, now);
    },
  );

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const bring = (side: number) => {
    const current = deck.get();
    const next = current.cards.findIndex((card) => card.slot === side);
    hold.set(USER_HOLD);
    deck.set(swapped(current, side, time.get(), reducedMotion));
    tick();
    if (next >= 0) onSelect?.(next);
  };

  const onTap = (index: number) => {
    const slot = deck.get().cards[index].slot;
    if (slot !== 1) bring(slot);
  };

  const onDrag = (index: number, x: number, y: number) => {
    const current = deck.get();
    if (current.cards[index].slot !== 1) return;
    touching.set(true);
    deck.set({ cards: current.cards, drag: { card: index, x, y } });
  };

  const onDrop = (index: number, x: number) => {
    const current = deck.get();
    if (current.drag.card !== index) return;
    touching.set(false);
    hold.set(USER_HOLD);
    if (Math.abs(x) > DRAG_THROW) bring(x > 0 ? 2 : 0);
    else deck.set(settled(current, time.get(), reducedMotion));
  };

  return (
    <View style={styles.fan}>
      {grid ? <Grid /> : null}
      {places.map((place, index) => (
        <GuideCard
          key={index}
          place={place}
          index={index}
          tag={tag}
          action={action}
          isFront={front === index}
          deck={deck}
          time={time}
          calm={reducedMotion}
          onTap={onTap}
          onDrag={onDrag}
          onDrop={onDrop}
          onBook={(booked) => onBook?.(booked)}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  fan: {
    width: WIDTH,
    height: HEIGHT,
  },
  grid: {
    position: 'absolute',
    left: (WIDTH - GRID_SIZE) / 2,
    top: (HEIGHT - GRID_SIZE) / 2,
    width: GRID_SIZE,
    height: GRID_SIZE,
  },
  card: {
    position: 'absolute',
    left: (WIDTH - CARD_WIDTH) / 2,
    top: (HEIGHT - CARD_HEIGHT) / 2,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
  },
  sheetFront: {
    boxShadow: '0 22px 40px rgba(28, 40, 34, 0.24)',
  },
  sheetSide: {
    boxShadow: '0 6px 14px rgba(28, 40, 34, 0.14)',
  },
  photo: {
    position: 'absolute',
    left: FRAME,
    top: FRAME,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  tag: {
    position: 'absolute',
    left: FRAME + 8,
    top: FRAME + 8,
    height: 20,
    paddingHorizontal: 9,
    borderRadius: 10,
    backgroundColor: 'rgba(20,20,20,0.55)',
    justifyContent: 'center',
  },
  tagLabel: {
    color: '#ffffff',
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '500',
  },
  badge: {
    position: 'absolute',
    right: BADGE_INSET,
    top: BADGE_INSET,
    width: BADGE * 2,
    height: BADGE * 2,
    borderRadius: BADGE,
    backgroundColor: '#161616',
    alignItems: 'center',
    justifyContent: 'center',
  },
  arrow: {
    width: 12,
    height: 12,
  },
  nameRow: {
    position: 'absolute',
    left: 14,
    top: FRAME + PHOTO_HEIGHT,
    width: CARD_WIDTH - 28,
    height: NAME_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 6,
  },
  name: {
    flexShrink: 1,
    color: '#1c1c1c',
    fontSize: 15,
    lineHeight: 19,
    letterSpacing: -0.3,
  },
  location: {
    color: '#8a8a8a',
    fontSize: 10.5,
    lineHeight: 14,
  },
  buttonSlot: {
    position: 'absolute',
    left: FRAME,
    top: FRAME + PHOTO_HEIGHT + NAME_HEIGHT,
  },
  button: {
    width: CARD_WIDTH - FRAME * 2,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: '#efefee',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonFront: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: BUTTON_HEIGHT / 2,
    backgroundColor: '#1b1b1b',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '600',
  },
  buttonLabelIdle: {
    color: '#a3a3a3',
  },
});
