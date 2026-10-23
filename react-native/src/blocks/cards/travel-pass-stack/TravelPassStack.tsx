import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type TravelPass = {
  name: string;
  /** Address of a wide photo, about 2 : 1. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
  /** Path data of the glyph in a 20 × 20 box: absolute M, L, Q and Z commands separated by spaces. */
  glyph: string;
};

type Props = {
  /** The passes, first on top. Four fit at full size; the rest wait under the bottom edge. */
  passes?: TravelPass[];
  /** Drags the stack up by two cards, pauses, drags it back down and starts over. */
  autoPlay?: boolean;
  /** The panel's gradient, top and bottom, as `#rrggbb`. */
  skyTop?: string;
  skyBottom?: string;
  cardColor?: string;
  textColor?: string;
  /** Called with the index of the card the stack snaps to when a drag ends. */
  onSelect?: (index: number) => void;
  /** Called with the index of a tapped card. */
  onOpen?: (index: number) => void;
};

const PANEL_WIDTH = 350;
const PANEL_HEIGHT = 620;
const PANEL_RADIUS = 40;

const CARD_WIDTH = 310;
const CARD_HEIGHT = 184;
const CARD_RADIUS = 18;
// Height of the row with the glyph and the name.
const HEADER = 48;
const GLYPH = 20;
const GLYPH_STROKE = 1.6;
const GLYPH_LEFT = 22;
const TITLE_LEFT = 50;
const PHOTO_INSET = 24;
const PHOTO_WIDTH = CARD_WIDTH - PHOTO_INSET * 2;
const PHOTO_HEIGHT = 116;
const PHOTO_RADIUS = 12;
// A Gaussian of sigma 16, which CSS writes as a 32 px blur.
const CARD_SHADOW = '0 0 32px rgba(0,0,0,0.3)';

// Top of the first full-size card, and the distance from one card's top to the next.
const TOP = 84;
const STEP = 106;
// Cards that fit at full size.
const SLOTS = 4;

// A card pushed one step past the top: how far it still rises, and what is left of it.
const RECEDE_LIFT = 62;
const RECEDE_EASE = 12;
const RECEDE_SCALE = 0.19;
const RECEDE_OPACITY = 0.7;
const RECEDE_BLUR = 2.5;
// Steps past the first over which a receding card fades out completely.
const RECEDE_FADE = 0.7;

// The photo is taller than its window by this much on each side and slides inside it.
const PARALLAX_ROOM = 10;
const PARALLAX_STEP = 6;

const HAZE_HEIGHT = 64;
const HAZE_OPACITY = 0.5;

// The snap after a release: a spring on the scroll position, in cards.
const MASS = 1;
const STIFFNESS = 180;
const DAMPING = 20;
// Seconds of speed a release throws the stack ahead by.
const THROW_TIME = 0.15;
const RUBBER = 0.35;
const DRAG_SLOP = 6;

// The gaps open by this share of a step per card/second of speed, up to a limit.
const STRETCH_GAIN = 0.03;
const STRETCH_MAX = 0.1;
const STRETCH_RATE = 10;

// autoPlay loop (seconds): a drag up by two cards, a pause, a drag back down, a rest.
const AUTO_CARDS = 2;
const DRAG_UP_AT = 0.8;
const DRAG_DOWN_AT = 3.4;
const DRAG_TIME = 0.9;
// Share of the way the finger covers before it lets go; the throw does the rest.
const DRAG_SHARE = 0.85;
const FLICK_ANGLE = 2.2;
const LOOP = 6.2;
// After a touch the loop waits this long, then goes home and rests this long before it restarts.
const USER_HOLD = 2;
const RESUME_REST = 1.2;

const PASSES: TravelPass[] = [
  {
    name: 'Kestrel Heli Transfer',
    photo: 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#7c7c7c',
    glyph:
      'M 4 4.5 L 16 4.5 M 10 4.5 L 10 7.5 M 7 7.5 L 12 7.5 Q 16.5 7.5 16.5 11.5 Q 16.5 14 14 14 L 9 14 Q 6 14 6 11 L 6 8.5 Q 6 7.5 7 7.5 Z M 6 10 L 2 9 M 2 7.5 L 2 10.5 M 8 16.5 L 15 16.5',
  },
  {
    name: 'First Light Lounge',
    photo: 'https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#978477',
    glyph:
      'M 6 9 L 6 6.5 Q 6 4.5 8 4.5 L 12 4.5 Q 14 4.5 14 6.5 L 14 9 M 4 9 Q 4 8 5 8 Q 6 8 6 9 L 6 11.5 L 14 11.5 L 14 9 Q 14 8 15 8 Q 16 8 16 9 L 16 14 L 4 14 Z M 6 14 L 6 16 M 14 14 L 14 16',
  },
  {
    name: 'Northwind Charter',
    photo: 'https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#adb3b8',
    glyph: 'M 3 10 L 17 10 M 11.5 10 L 7.5 3.5 M 11.5 10 L 7.5 16.5 M 5 10 L 3.2 7 M 5 10 L 3.2 13',
  },
  {
    name: 'Hollow Pine Cabins',
    photo: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#454732',
    glyph: 'M 3 16 L 10 4 L 17 16 Z M 8.5 16 L 8.5 12.5 L 11.5 12.5 L 11.5 16',
  },
  {
    name: 'Quiet Hours Spa',
    photo: 'https://images.unsplash.com/photo-1573843981267-be1999ff37cd?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#80a7a0',
    glyph:
      'M 10 4 Q 13.5 8.5 10 14 Q 6.5 8.5 10 4 Z M 10 14 Q 4.5 14 3 8.5 Q 6 8.5 7.4 10 M 10 14 Q 15.5 14 17 8.5 Q 14 8.5 12.6 10 M 5 16.5 L 15 16.5',
  },
  {
    name: 'Coastliner Rail',
    photo: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=640&h=330&q=80',
    tint: '#7e726b',
    glyph:
      'M 5.5 3.5 L 14.5 3.5 Q 16 3.5 16 5 L 16 12.5 Q 16 14 14.5 14 L 5.5 14 Q 4 14 4 12.5 L 4 5 Q 4 3.5 5.5 3.5 Z M 4 8.5 L 16 8.5 M 7 11.2 L 7.1 11.2 M 13 11.2 L 12.9 11.2 M 6.5 14 L 5 17 M 13.5 14 L 15 17',
  },
];

/**
 * Where a card sits for `u` = its index minus the scroll: 0 is the top full-size slot, negative
 * values have been pushed past it and recede.
 */
function placement(u: number, stretch: number) {
  'worklet';
  const r = Math.min(Math.max(-u, 0), 2);
  const top =
    TOP + Math.max(u, 0) * STEP - (RECEDE_LIFT * r - RECEDE_EASE * r * r) + STEP * stretch * (Math.max(u, -2) - 1);
  const opacity = r <= 1 ? 1 - (1 - RECEDE_OPACITY) * r : Math.max(0, RECEDE_OPACITY * (1 - (r - 1) / RECEDE_FADE));
  const parallax = Math.min(PARALLAX_ROOM, Math.max(-PARALLAX_ROOM, (1.5 - u) * PARALLAX_STEP));
  return { top, scale: 1 - RECEDE_SCALE * r, opacity, blur: RECEDE_BLUR * r, parallax };
}

/** A finger's stroke, 0…1: it starts from rest, speeds up and is still moving when it lets go. */
function flick(p: number) {
  'worklet';
  return (1 - Math.cos(p * FLICK_ANGLE)) / (1 - Math.cos(FLICK_ANGLE));
}

/** Where the loop's finger holds the stack at `t` seconds, or null while it is lifted. */
function scriptFinger(t: number, travel: number) {
  'worklet';
  if (t >= DRAG_UP_AT && t < DRAG_UP_AT + DRAG_TIME) return travel * DRAG_SHARE * flick((t - DRAG_UP_AT) / DRAG_TIME);
  if (t >= DRAG_DOWN_AT && t < DRAG_DOWN_AT + DRAG_TIME) {
    return travel * (1 - DRAG_SHARE * flick((t - DRAG_DOWN_AT) / DRAG_TIME));
  }
  return null;
}

/** The card a release lands on: the nearest one to where the speed would carry the stack. */
function snapTarget(scroll: number, velocity: number, limit: number) {
  'worklet';
  return Math.min(limit, Math.max(0, Math.round(scroll + velocity * THROW_TIME)));
}

function nearest(scroll: number, limit: number) {
  'worklet';
  return Math.min(limit, Math.max(0, Math.round(scroll)));
}

/** Past either end the stack follows the finger at a third of its speed. */
function rubber(scroll: number, limit: number) {
  'worklet';
  if (scroll < 0) return scroll * RUBBER;
  if (scroll > limit) return limit + (scroll - limit) * RUBBER;
  return scroll;
}

/** `#rrggbb` with an alpha, so a gradient can fade to the same hue instead of to black. */
function withAlpha(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

type CardProps = {
  pass: TravelPass;
  index: number;
  scroll: SharedValue<number>;
  stretch: SharedValue<number>;
  cardColor: string;
  textColor: string;
  onPress: () => void;
};

/** One pass: the glyph and the name over a wide photo. */
function Card({ pass, index, scroll, stretch, cardColor, textColor, onPress }: CardProps) {
  const cardStyle = useAnimatedStyle(() => {
    const place = placement(index - scroll.get(), stretch.get());
    return {
      opacity: place.opacity,
      transform: [{ translateY: place.top }, { scale: place.scale }],
      // React Native's types give a text style only the string form of `filter`; views take this list form.
      filter: [{ blur: place.blur }] as unknown as string,
    };
  });
  const photoStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: placement(index - scroll.get(), stretch.get()).parallax }],
  }));

  return (
    <Animated.View style={[styles.card, { backgroundColor: cardColor }, cardStyle]}>
      <Pressable style={styles.cardBody} onPress={onPress} accessibilityRole="button" accessibilityLabel={pass.name}>
        <Svg style={styles.glyph} width={GLYPH} height={GLYPH} viewBox="0 0 20 20">
          <Path
            d={pass.glyph}
            fill="none"
            stroke={textColor}
            strokeWidth={GLYPH_STROKE}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </Svg>
        <Text style={[styles.name, { color: textColor }]} numberOfLines={1}>
          {pass.name}
        </Text>
        <View style={[styles.photoWindow, { backgroundColor: pass.tint }]}>
          <Animated.View style={[styles.photo, photoStyle]}>
            <Image source={{ uri: pass.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
          </Animated.View>
        </View>
      </Pressable>
    </Animated.View>
  );
}

/**
 * A wallet of travel passes seen in perspective: wide photo cards overlap down a dusk panel, and
 * dragging scrolls them. Cards pushed past the top shrink, dim and blur as if they moved away,
 * new ones rise from under the bottom edge, and the gaps stretch with the speed of the finger.
 * Releasing snaps the stack to a card.
 */
export function TravelPassStack({
  passes = PASSES,
  autoPlay = false,
  skyTop = '#3e4063',
  skyBottom = '#7a7594',
  cardColor = '#3a3a40',
  textColor = '#ffffff',
  onSelect,
  onOpen,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  // The furthest the stack scrolls: the last card then sits in the lowest full slot.
  const limit = Math.max(0, passes.length - SLOTS);

  // `scroll` counts cards pushed past the top.
  const scroll = useSharedValue(0);
  // `scroll` at the previous frame, to measure the finger's speed.
  const seen = useSharedValue(0);
  const velocity = useSharedValue(0);
  const target = useSharedValue(0);
  // 0…STRETCH_MAX: how far the gaps are pulled open by the speed.
  const stretch = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  // A real finger is on the stack.
  const dragging = useSharedValue(false);
  // The loop's own finger is on the stack.
  const scripted = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const dragIndex = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
    scripted.set(false);
    target.set(0);
    if (auto) {
      scroll.set(0);
      velocity.set(0);
    }
  }, [auto, clock, hold, looping, scripted, scroll, target, velocity]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    if (dragging.get()) {
      velocity.set(velocity.get() + ((scroll.get() - seen.get()) / dt - velocity.get()) * 0.5);
    } else {
      if (hold.get() > 0) {
        hold.set(hold.get() - dt);
        // Time is up: the stack goes home and the loop starts over from its rest.
        if (hold.get() <= 0 && looping.get()) {
          target.set(0);
          clock.set(LOOP - RESUME_REST);
        }
      } else if (looping.get()) {
        let t = clock.get() + dt;
        if (t >= LOOP) {
          t -= LOOP;
          scroll.set(0);
          velocity.set(0);
          target.set(0);
        }
        clock.set(t);
        const finger = scriptFinger(t, Math.min(AUTO_CARDS, limit));
        if (finger != null) {
          velocity.set((finger - scroll.get()) / dt);
          scroll.set(finger);
          scripted.set(true);
        } else if (scripted.get()) {
          scripted.set(false);
          target.set(snapTarget(scroll.get(), velocity.get(), limit));
        }
      }
      if (!scripted.get()) {
        if (reducedMotion) {
          scroll.set(target.get());
          velocity.set(0);
        } else {
          const v = velocity.get() + ((STIFFNESS * (target.get() - scroll.get()) - DAMPING * velocity.get()) / MASS) * dt;
          velocity.set(v);
          scroll.set(scroll.get() + v * dt);
        }
      }
    }
    seen.set(scroll.get());
    const pull = reducedMotion ? 0 : Math.min(Math.abs(velocity.get()) * STRETCH_GAIN, STRETCH_MAX);
    stretch.set(stretch.get() + (pull - stretch.get()) * (1 - Math.exp(-dt * STRETCH_RATE)));
  });

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const select = (index: number) => onSelect?.(index);

  const pan = Gesture.Pan()
    .minDistance(DRAG_SLOP)
    .onStart(() => {
      dragFrom.set(scroll.get());
      dragIndex.set(nearest(scroll.get(), limit));
      dragging.set(true);
      scripted.set(false);
      seen.set(scroll.get());
    })
    .onUpdate((event) => {
      scroll.set(rubber(dragFrom.get() - event.translationY / STEP, limit));
      const index = nearest(scroll.get(), limit);
      if (index === dragIndex.get()) return;
      dragIndex.set(index);
      scheduleOnRN(tick);
    })
    .onEnd(() => {
      dragging.set(false);
      target.set(snapTarget(scroll.get(), velocity.get(), limit));
      hold.set(USER_HOLD);
      scheduleOnRN(select, target.get());
    });

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.panel}>
        <LinearGradient colors={[skyTop, skyBottom]} style={StyleSheet.absoluteFill} />
        {passes.map((pass, index) => (
          <Card
            key={index}
            pass={pass}
            index={index}
            scroll={scroll}
            stretch={stretch}
            cardColor={cardColor}
            textColor={textColor}
            onPress={() => {
              hold.set(USER_HOLD);
              onOpen?.(index);
            }}
          />
        ))}
        {/* A little dusk haze over the bottom edge, where the next cards wait. */}
        <LinearGradient
          pointerEvents="none"
          colors={[withAlpha(skyBottom, 0), withAlpha(skyBottom, HAZE_OPACITY)]}
          style={styles.haze}
        />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: PANEL_WIDTH,
    height: PANEL_HEIGHT,
    borderRadius: PANEL_RADIUS,
    overflow: 'hidden',
  },
  card: {
    position: 'absolute',
    left: (PANEL_WIDTH - CARD_WIDTH) / 2,
    top: 0,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
    boxShadow: CARD_SHADOW,
    transformOrigin: 'top',
  },
  // Children are placed from the card's outer edge, so the hairline border is taken back out.
  cardBody: {
    position: 'absolute',
    left: -1,
    top: -1,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  glyph: {
    position: 'absolute',
    left: GLYPH_LEFT,
    top: (HEADER - GLYPH) / 2,
  },
  name: {
    position: 'absolute',
    left: TITLE_LEFT,
    top: (HEADER - 20) / 2,
    width: CARD_WIDTH - TITLE_LEFT - PHOTO_INSET,
    fontSize: 15.5,
    lineHeight: 20,
    fontWeight: '500',
    letterSpacing: -0.1,
  },
  photoWindow: {
    position: 'absolute',
    left: PHOTO_INSET,
    top: HEADER,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  photo: {
    position: 'absolute',
    left: 0,
    top: -PARALLAX_ROOM,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT + PARALLAX_ROOM * 2,
  },
  haze: {
    position: 'absolute',
    left: 0,
    bottom: 0,
    width: PANEL_WIDTH,
    height: HAZE_HEIGHT,
  },
});
