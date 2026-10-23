import { Canvas, Fill, ImageShader, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type RipplePhoto = {
  /** Unsplash photo id. */
  photo: string;
  title: string;
  place: string;
};

type Props = {
  /** Six photos, shown in turn. */
  photos?: RipplePhoto[];
  /** Drops rain at scattered spots and swipes through every photo, then starts over. */
  autoPlay?: boolean;
  /** Called with a photo's index once it has filled the screen. */
  onChange?: (index: number) => void;
};

const PHOTOS: RipplePhoto[] = [
  { photo: 'photo-1625934036482-2dccff707564', title: 'Lavender, after rain', place: 'Sorell Plateau · July' },
  { photo: 'photo-1493589976221-c2357c31ad77', title: 'Blossom hour', place: 'Hanaoka Canal · April' },
  { photo: 'photo-1593715857983-5531aa640471', title: 'Rows of hum', place: 'Valmont Ridge · June' },
  { photo: 'photo-1522748906645-95d8adfd52c7', title: 'Petal weather', place: 'Old Mill Lane · April' },
  { photo: 'photo-1625215393266-d09797f4a441', title: 'The purple hour', place: 'Linden Farm · August' },
  { photo: 'photo-1538943186303-104afadcbb16', title: 'Pink quiet', place: 'Kiyose Garden · March' },
];
// The shader holds six photos; fewer repeat, more are left out.
const SLOTS = 6;

const NO_IMAGES: (SkImage | null)[] = Array.from({ length: SLOTS }, () => null);

// The photo fills a phone screen, about 400 pt wide; 1200 px covers that at 3x.
const PHOTO_WIDTH = 1200;
const photoUri = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${PHOTO_WIDTH}&q=80`;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const BACKDROP = '#0d0c11';

// Ripples: up to RIPPLES rings at once, each a packet of waves running out from where it was
// made at SPEED pt/s, WAVELENGTH apart, widening and fading as it goes.
const RIPPLES = 16;
const SPEED = 190; // pt/s
const WAVELENGTH = 24; // pt
const LIFE = 3.2; // s a ripple lives
const DAMP = 1.15; // 1/s, amplitude decay
const REFRACT = 9; // pt of image shift at a ripple's crest, for a full-strength ripple
// What a touch leaves: a strong ring where it lands, a lighter one every TRAIL_STEP pt it drags,
// and one every STILL_EVERY s while it rests on the water.
const TOUCH_STRENGTH = 1;
const TRAIL_STRENGTH = 0.5;
const TRAIL_STEP = 26;
const STILL_EVERY = 0.6;
const STILL_STRENGTH = 0.55;

// The swell to the next photo: a big ring from where the swipe let go, its front carrying the
// next photo. It runs on a critically damped spring toward a little past the farthest corner,
// taking the swipe's speed as its own.
const SWIPE_DISTANCE = 50; // pt up
const SWIPE_SPEED = 650; // pt/s up
const SWELL_OMEGA = 2.3; // 1/s, natural frequency of the spring
const SWELL_OVER = 1.18; // spring target, as a multiple of the distance that covers the screen
const SWELL_MIN_SPEED = 520; // pt/s, the swell's speed when the swipe was slow
const SWELL_MAX_SPEED = 1500;
const CROSSFADE = 0.5; // s, the change under reduced motion

// autoPlay: six beats of LOOP / 6 s; in each, four raindrops, then a swipe up to the next photo.
const LOOP = 21;
const BEAT = LOOP / SLOTS;
const RAIN = [0.25, 0.8, 1.35, 1.8];
const SWIPE_AT = 2.15; // s into a beat: a short drag up that leaves four ripples, then the swell
const SWIPE_TRAIL = 0.2;
const USER_HOLD = 4;

// The entrance, once the first photo has arrived: it fades in over PHOTO_FADE ms on a gentle
// ease-out (FADE_EASE, so the fade reads as a fade) while settling from PHOTO_SCALE to full size
// over PHOTO_SETTLE ms on the strong ease-out; the header, the caption and the hint follow
// it in, each rising TEXT_RISE pt over TEXT_FADE ms, TEXT_DELAYS ms after the photo starts. The
// autoPlay script waits ENTRANCE s, so the first raindrop falls on a settled picture.
const PHOTO_FADE = 700;
const PHOTO_SETTLE = 1400;
const PHOTO_SCALE = 1.06;
const TEXT_FADE = 700;
const TEXT_RISE = 10;
const TEXT_DELAYS = { header: 350, caption: 520, hint: 700 };
const ENTRANCE = 1.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const FADE_EASE = Easing.out(Easing.quad);

// The photo as seen through moving water. For each ripple, the height of the surface is a wave
// packet around a ring of radius SPEED·age; its slope bends the light, shifting the image along
// the ring's radius, and its curvature focuses light into bright caustic lines. The swell to the
// next photo is one more, much larger packet whose front reveals the next photo.
const WATER = Skia.RuntimeEffect.Make(`
uniform shader photo0;
uniform shader photo1;
uniform shader photo2;
uniform shader photo3;
uniform shader photo4;
uniform shader photo5;
uniform float2 size;
uniform float time;
uniform float4 ripples[${RIPPLES}];
uniform float current;
uniform float next;
uniform float2 origin;
uniform float swell;
uniform float mixing;
uniform float still;

const float PI = 3.14159265;
const float K = 2.0 * PI / ${WAVELENGTH}.0;

half3 photo(float i, float2 p) {
  p = clamp(p, float2(0.5), size - 0.5);
  if (i < 0.5) return photo0.eval(p).rgb;
  if (i < 1.5) return photo1.eval(p).rgb;
  if (i < 2.5) return photo2.eval(p).rgb;
  if (i < 3.5) return photo3.eval(p).rgb;
  if (i < 4.5) return photo4.eval(p).rgb;
  return photo5.eval(p).rgb;
}

half4 main(float2 p) {
  float2 shift = float2(0.0);
  float light = 0.0;
  float motion = 1.0 - still;

  // A slow swell all over, so the surface is never quite still.
  shift += motion * 1.4 * float2(sin(p.y * 0.031 + time * 1.3), cos(p.x * 0.027 + time * 1.05));

  for (int i = 0; i < ${RIPPLES}; i++) {
    float4 r = ripples[i];
    float age = time - r.z;
    if (r.w <= 0.0 || age < 0.0 || age > ${LIFE}) continue;
    float2 v = p - r.xy;
    float d = length(v);
    float x = d - ${SPEED}.0 * age;
    float width = 14.0 + 26.0 * age;
    float env = exp(-x * x / (width * width)) * r.w * exp(-${DAMP} * age) / sqrt(1.0 + d * 0.012);
    // Fades in over the first moments so a ring does not pop.
    env *= smoothstep(0.0, 0.06, age);
    float phase = x * K;
    shift += (v / max(d, 1.0)) * cos(phase) * env * ${REFRACT}.0 * motion;
    // Under reduced motion the rings only glint softly.
    light -= sin(phase) * env * (0.35 + 0.65 * motion);
  }

  // The swell's front, with a train of smaller waves behind it.
  float mask = mixing;
  if (swell > 0.0) {
    float2 v = p - origin;
    float d = length(v);
    float x = d - swell;
    float front = exp(-x * x / (60.0 * 60.0));
    float behind = x < 0.0 ? exp(x / 160.0) : 0.0;
    float wave = cos(x * 0.075) * front * 17.0 + sin(x * 0.15) * behind * 4.0;
    shift += (v / max(d, 1.0)) * wave * motion;
    light -= (sin(x * 0.075) * front * 0.38 + cos(x * 0.15) * behind * 0.22) * motion;
    float edge = x + sin(atan(v.y, v.x) * 7.0 + swell * 0.01) * 10.0;
    mask = 1.0 - smoothstep(-26.0, 26.0, edge);
  }

  half3 a = photo(current, p + shift);
  half3 col = a;
  if (mask > 0.001) col = mix(a, photo(next, p + shift), half(mask));

  // Caustics: bright where the surface focuses light, a little darker where it spreads it.
  float glint = max(light, 0.0);
  col += half3(1.0, 0.98, 0.95) * half(pow(glint, 1.4) * 0.3);
  col *= half(1.0 + min(light, 0.0) * 0.16);
  return half4(col, 1.0);
}
`)!;

/** Random in [0, 1) from a number, the same on every run. */
function scatter(n: number) {
  'worklet';
  const s = Math.sin(n * 91.345 + 47.853) * 43758.5453;
  return s - Math.floor(s);
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** The swell's radius that reaches every corner of the screen from `x, y`. */
function coverFrom(x: number, y: number, width: number, height: number) {
  'worklet';
  const dx = Math.max(x, width - x);
  const dy = Math.max(y, height - y);
  return Math.hypot(dx, dy) + 60;
}

/**
 * A photo that behaves like water. Touches send rings out across it that bend the image and
 * catch the light; dragging leaves a trail of them. Swiping up sends a big swell from the finger
 * that carries in the next photo.
 */
export function WaterRipplePhoto({ photos = PHOTOS, autoPlay = false, onChange }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = Math.min(SLOTS, photos.length);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  // Each photo arrives on its own; the screen comes in as soon as the first one has.
  // Kept with the photos they belong to, so a new set never shows the old one's pictures.
  const [arrivals, setArrivals] = useState<{ photos: RipplePhoto[]; list: (SkImage | null)[] } | null>(null);
  const images = arrivals && arrivals.photos === photos ? arrivals.list : NO_IMAGES;
  const first = images[0] ?? null;
  const shown = first !== null;
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const screen = useMemo(() => Skia.XYWHRect(0, 0, width, height), [width, height]);

  const time = useSharedValue(0);
  // x, y, birth time, strength for each ripple slot; `head` is the next slot to write.
  const ripples = useSharedValue<number[][]>(Array.from({ length: RIPPLES }, () => [0, 0, -10, 0]));
  const head = useSharedValue(0);

  const current = useSharedValue(0);
  const next = useSharedValue(1);
  const originX = useSharedValue(0);
  const originY = useSharedValue(0);
  const swell = useSharedValue(0);
  const swellSpeed = useSharedValue(0);
  const swellCover = useSharedValue(1);
  const swelling = useSharedValue(false);
  const mixing = useSharedValue(0);
  const caption = useSharedValue(0);

  const touching = useSharedValue(false);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const trailX = useSharedValue(0);
  const trailY = useSharedValue(0);
  const stillFor = useSharedValue(0);

  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const ready = useSharedValue(false);
  // Which photos have arrived, so the swell never carries in one that is not there yet.
  const arrived = useSharedValue<boolean[]>(Array.from({ length: SLOTS }, () => false));
  const entrance = useSharedValue(0);
  const photoIn = useSharedValue(0);
  const stage = useSharedValue({ width: 0, height: 0 });

  useEffect(() => {
    stage.set({ width, height });
  }, [width, height, stage]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    let alive = true;
    arrived.set(Array.from({ length: SLOTS }, () => false));
    for (let i = 0; i < SLOTS; i += 1) {
      Skia.Data.fromURI(photoUri(photos[i % photos.length].photo))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          setArrivals((prev) => {
            const list = prev && prev.photos === photos ? prev.list : NO_IMAGES;
            return { photos, list: list.map((item, k) => (k === i ? image : item)) };
          });
          arrived.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = true;
            return next;
          });
        })
        .catch(() => {});
    }
    return () => {
      alive = false;
    };
  }, [photos, arrived]);

  // The first photo is here: it fades and settles in, and the script waits for it to finish.
  useEffect(() => {
    if (!shown) return;
    entrance.set(withTiming(1, { duration: PHOTO_SETTLE, easing: EASE_OUT }));
    photoIn.set(withTiming(1, { duration: PHOTO_FADE, easing: FADE_EASE }));
    hold.set(ENTRANCE);
    ready.set(true);
  }, [shown, entrance, photoIn, hold, ready]);

  const photoStyle = useAnimatedStyle(() => {
    const v = entrance.get();
    return {
      opacity: photoIn.get(),
      transform: [{ scale: reducedMotion ? 1 : PHOTO_SCALE + (1 - PHOTO_SCALE) * v }],
    };
  });

  const splashed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const swept = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const changed = (index: number) => onChange?.(index);

  /** Starts a ring at `x, y`. */
  const ripple = (x: number, y: number, strength: number) => {
    'worklet';
    const slot = head.get();
    ripples.modify((list) => {
      'worklet';
      list[slot] = [x, y, time.get(), strength];
      return list;
    });
    head.set((slot + 1) % RIPPLES);
  };

  /** Sends the swell from `x, y` toward the next photo, starting at `speed` pt/s. */
  const sweep = (x: number, y: number, speed: number, user: boolean) => {
    'worklet';
    if (swelling.get() || mixing.get() > 0) return;
    const upcoming = (current.get() + 1) % count;
    if (!arrived.get()[upcoming]) return;
    const s = stage.get();
    next.set(upcoming);
    caption.set(next.get());
    if (reducedMotion) {
      mixing.set(0.001);
    } else {
      originX.set(x);
      originY.set(y);
      swell.set(1);
      swellSpeed.set(Math.max(SWELL_MIN_SPEED, Math.min(SWELL_MAX_SPEED, speed)));
      swellCover.set(coverFrom(x, y, s.width, s.height));
      swelling.set(true);
      ripple(x, y, 1.2);
    }
    if (user) scheduleOnRN(swept);
  };

  /** The next photo has filled the screen: it becomes the current one. */
  const settle = () => {
    'worklet';
    current.set(next.get());
    swelling.set(false);
    swell.set(0);
    mixing.set(0);
    scheduleOnRN(changed, current.get());
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const now = time.get() + dt;
    time.set(now);
    const s = stage.get();

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get() && ready.get() && s.width > 0) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let beat = 0; beat < SLOTS; beat += 1) {
        const start = beat * BEAT;
        for (let k = 0; k < RAIN.length; k += 1) {
          if (crossed(before, t, start + RAIN[k])) {
            const n = beat * 11 + k * 3;
            const x = s.width * (0.14 + 0.72 * scatter(n));
            const y = s.height * (0.16 + 0.6 * scatter(n + 0.5));
            ripple(x, y, 0.6 + 0.4 * scatter(n + 0.25));
          }
        }
        // A drag up from low on the screen: four ripples, then the swell from where it let go.
        const x = s.width * (0.35 + 0.3 * scatter(beat * 5 + 2));
        for (let k = 0; k < 4; k += 1) {
          if (crossed(before, t, start + SWIPE_AT + (k * SWIPE_TRAIL) / 3)) {
            ripple(x, s.height * (0.8 - 0.06 * k), TRAIL_STRENGTH);
          }
        }
        if (crossed(before, t, start + SWIPE_AT + SWIPE_TRAIL + 0.02)) sweep(x, s.height * 0.6, 760, false);
      }
    }

    // A finger resting on the water keeps sending out rings.
    if (touching.get()) {
      stillFor.set(stillFor.get() + dt);
      if (stillFor.get() >= STILL_EVERY) {
        stillFor.set(0);
        ripple(fingerX.get(), fingerY.get(), STILL_STRENGTH);
      }
    }

    if (swelling.get()) {
      // Critically damped spring toward a little past the farthest corner.
      const target = swellCover.get() * SWELL_OVER;
      const w = SWELL_OMEGA;
      const v = swellSpeed.get() + (w * w * (target - swell.get()) - 2 * w * swellSpeed.get()) * dt;
      swellSpeed.set(v);
      swell.set(swell.get() + v * dt);
      if (swell.get() >= swellCover.get()) settle();
    } else if (mixing.get() > 0) {
      mixing.set(Math.min(1, mixing.get() + dt / CROSSFADE));
      if (mixing.get() >= 1) settle();
    }
  });

  const uniforms = useDerivedValue(() => ({
    size: [width, height],
    time: time.get(),
    ripples: ripples.get(),
    current: current.get(),
    next: next.get(),
    origin: [originX.get(), originY.get()],
    swell: swell.get(),
    mixing: mixing.get(),
    still: reducedMotion ? 1 : 0,
  }));

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      if (!ready.get()) return;
      touched();
      touching.set(true);
      stillFor.set(0);
      fingerX.set(event.x);
      fingerY.set(event.y);
      trailX.set(event.x);
      trailY.set(event.y);
      ripple(event.x, event.y, TOUCH_STRENGTH);
      scheduleOnRN(splashed);
    })
    .onUpdate((event) => {
      if (!touching.get()) return;
      fingerX.set(event.x);
      fingerY.set(event.y);
      const moved = Math.hypot(event.x - trailX.get(), event.y - trailY.get());
      if (moved >= TRAIL_STEP) {
        trailX.set(event.x);
        trailY.set(event.y);
        stillFor.set(0);
        ripple(event.x, event.y, TRAIL_STRENGTH);
      }
    })
    .onFinalize((event) => {
      if (!touching.get()) return;
      touching.set(false);
      hold.set(USER_HOLD);
      const up = -(event.translationY ?? 0);
      const speed = -(event.velocityY ?? 0);
      if (up > SWIPE_DISTANCE && speed > SWIPE_SPEED) sweep(event.x, event.y, speed * 0.8, true);
    });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Photo on water"
        accessibilityHint="Touch to make ripples, swipe up for the next photo"
        accessibilityActions={[{ name: 'increment', label: 'Next photo' }]}
        onAccessibilityAction={() => sweep(width / 2, height / 2, SWELL_MIN_SPEED, true)}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        {size && first && (
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, photoStyle]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={WATER} uniforms={uniforms}>
                  {images.map((image, i) => (
                    // A photo still on its way stands in as the first one; the swell waits for it.
                    <ImageShader key={i} image={image ?? first} fit="cover" rect={screen} tx="clamp" ty="clamp" />
                  ))}
                </Shader>
              </Fill>
            </Canvas>
          </Animated.View>
        )}

        <LinearGradient
          pointerEvents="none"
          colors={['rgba(8,6,14,0.42)', 'rgba(8,6,14,0)']}
          style={[styles.scrimTop, { height: insets.top + 110 }]}
        />
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(8,6,14,0)', 'rgba(8,6,14,0.55)']}
          style={[styles.scrimBottom, { height: insets.bottom + 260 }]}
        />

        <Entrance shown={shown} delay={TEXT_DELAYS.header} style={[styles.header, { top: insets.top + 16 }]}>
          <Text style={styles.brand}>STILL WATER</Text>
          {photos.slice(0, count).map((item, i) => (
            <Caption key={i} index={i} caption={caption} style={styles.counter}>
              <Text style={styles.brand}>{`${String(i + 1).padStart(2, '0')} / ${String(count).padStart(2, '0')}`}</Text>
            </Caption>
          ))}
        </Entrance>

        <Entrance shown={shown} delay={TEXT_DELAYS.caption} style={[styles.captions, { bottom: insets.bottom + 74 }]}>
          {photos.slice(0, count).map((item, i) => (
            <Caption key={i} index={i} caption={caption} style={styles.caption}>
              <Text style={styles.place}>{item.place.toUpperCase()}</Text>
              <Text style={styles.title}>{item.title}</Text>
            </Caption>
          ))}
        </Entrance>

        <Entrance shown={shown} delay={TEXT_DELAYS.hint} style={[styles.hintBox, { bottom: insets.bottom + 24 }]}>
          <Text style={styles.hint}>Touch the water · swipe up for the next</Text>
        </Entrance>
      </View>
    </GestureDetector>
  );
}

type EntranceProps = {
  shown: boolean;
  delay: number;
  style: StyleProp<ViewStyle>;
  children: ReactNode;
};

/** Text over the photo, hidden until the photo arrives, then fading and rising in after `delay` ms. */
function Entrance({ shown, delay, style, children }: EntranceProps) {
  const reducedMotion = useReducedMotion();
  const v = useSharedValue(0);
  useEffect(() => {
    if (shown) v.set(withDelay(delay, withTiming(1, { duration: TEXT_FADE, easing: EASE_OUT })));
  }, [shown, delay, v]);
  const animated = useAnimatedStyle(() => ({
    opacity: v.get(),
    transform: [{ translateY: reducedMotion ? 0 : (1 - v.get()) * TEXT_RISE }],
  }));
  return (
    <Animated.View pointerEvents="none" style={[style, animated]}>
      {children}
    </Animated.View>
  );
}

type CaptionProps = {
  index: number;
  caption: SharedValue<number>;
  style: object;
  children: ReactNode;
};

/** A caption that rises in with its photo and fades as the next one arrives. */
function Caption({ index, caption, style, children }: CaptionProps) {
  const reducedMotion = useReducedMotion();
  const animated = useAnimatedStyle(() => {
    const on = Math.round(caption.get()) === index;
    return {
      opacity: on ? withDelay(260, withTiming(1, { duration: 650, easing: EASE_OUT })) : withTiming(0, { duration: 220 }),
      transform: [
        {
          translateY: reducedMotion
            ? 0
            : on
              ? withDelay(260, withTiming(0, { duration: 750, easing: EASE_OUT }))
              : withTiming(-8, { duration: 220 }),
        },
      ],
    };
  });
  const initial = index === 0 ? { opacity: 1 } : { opacity: 0, transform: [{ translateY: 14 }] };
  return <Animated.View style={[style, initial, animated]}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: BACKDROP,
  },
  scrimTop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  scrimBottom: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 18,
  },
  counter: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  brand: {
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2.4,
    color: 'rgba(255,255,255,0.86)',
    fontVariant: ['tabular-nums'],
  },
  captions: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 92,
  },
  caption: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  place: {
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2,
    color: 'rgba(255,255,255,0.78)',
    marginBottom: 6,
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 38,
    lineHeight: 42,
    fontWeight: '500',
    letterSpacing: -0.8,
    color: '#ffffff',
  },
  hintBox: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  hint: {
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
    color: 'rgba(255,255,255,0.6)',
  },
});
