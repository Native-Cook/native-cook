import {
  BlurMask,
  Canvas,
  Circle,
  Fill,
  Group,
  ImageShader,
  Path,
  RadialGradient,
  Shader,
  Skia,
  type SkImage,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
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

export type InkPlate = {
  /** Unsplash photo id, printed through the ink. */
  photo: string;
  title: string;
  line: string;
  /** Small caps over the print, e.g. a place. */
  place: string;
};

type Props = {
  /** Up to four plates, printed in turn. */
  plates?: InkPlate[];
  /** Drops ink at a few spots in turn, then lets the prints dry back to paper and starts over. */
  autoPlay?: boolean;
  /** Called with a plate's index when its ink has fully spread. */
  onReveal?: (index: number) => void;
};

const PLATES: InkPlate[] = [
  {
    photo: 'photo-1506536329413-d2f0d31ceb9f',
    title: 'Still water',
    line: 'Mornings on Lake Orsel, where every peak is printed twice.',
    place: 'Lake Orsel',
  },
  {
    photo: 'photo-1433086966358-54859d0ed716',
    title: 'Falling light',
    line: 'Hollin Falls after three days of rain, loud enough to hush you.',
    place: 'Hollin Falls',
  },
  {
    photo: 'photo-1596003903067-bf5762ad5c19',
    title: 'The long mirror',
    line: 'A tarn above the treeline that holds the whole sky still.',
    place: 'Aster Tarn',
  },
  {
    photo: 'photo-1525824236856-8c0a31dfe3be',
    title: 'White thread',
    line: 'The upper cascade of the Vell, thin as a stitch in the moss.',
    place: 'River Vell',
  },
];

const INTRO = {
  title: 'Ink & Water',
  line: 'Four quiet places, printed by hand. Drop a little ink to begin.',
  place: 'Volume I',
};

// Photos are drawn about 370 pt wide on a phone; 1100 px covers that at 3x.
const PHOTO_WIDTH = 1100;
const photoUri = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${PHOTO_WIDTH}&q=80`;

const PAPER = '#f3efe7';
const INK = '#161a23';
const INK_SOFT = '#59606e';
// A sturdy book serif for the captions; small labels in the system sans, tracked capitals.
const SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';

// The print: the wet area of the paper that takes the ink, inset from the screen.
const PRINT_SIDE = 18;
const PRINT_TOP = 56; // below the top inset
const PRINT_HEIGHT = 0.58; // share of the screen's height
const CROP_MARK = 12; // length of the crop marks at the print's corners, pt
const CROP_GAP = 7;

// The ink drop: a bead hangs where it will fall, then drops.
const BEAD_RADIUS = 7; // pt, a light drop; a fully charged one is twice as wide
const BEAD_LIFT = 46; // a held bead rises this far above the finger, so the finger does not hide it
const LIFT_TIME = 0.35; // s; a tap lets the bead fall right under the finger
const BEAD_FOLLOW = 22; // 1/s, how quickly the hanging bead catches up with the finger
const HOLD_FULL = 1.2; // s held for a full, heavy drop
const BEAD_APPEAR = 0.22; // s
const FALL_TIME = 0.26; // s from release to contact, falling under gravity
const FALL_DRIFT = 0.08; // s of the finger's release speed the drop carries as it falls
const FALL_DRIFT_MAX = 48; // pt
// Satellite droplets thrown at contact, then soaking into the paper.
const SPLAT_COUNT = 9;
const SPLAT_OUT = 0.28; // s
const SPLAT_SOAK = 1.5; // s

// The bleed: the ink front spreads from the drop, fast at first and slowing like ink in paper.
const BLEED_TIME = 2.3; // s for a light drop; a heavy one spreads up to 35% faster
const BAND = 40; // pt of dark wet ink behind the front before the photo comes through
const LOBES = 0.16; // lobe depth as a share of the front's radius

// Reduced motion: the next print crossfades in over this long.
const FADE_TIME = 0.6;
// The prints dry back to paper at the end of the loop.
const DRY_TIME = 1.1;

// autoPlay (seconds): from bare paper, a drop every 3.55 s at these spots (shares of the print),
// then the prints dry away and the paper rests.
const LOOP = 16;
const DROPS = [
  { at: 0.35, x: 0.34, y: 0.32, weight: 0.45 },
  { at: 3.9, x: 0.7, y: 0.66, weight: 0.8 },
  { at: 7.45, x: 0.28, y: 0.74, weight: 0.35 },
  { at: 11.0, x: 0.64, y: 0.24, weight: 1 },
];
const DROP_HANG = 0.38; // s the auto bead hangs before it falls
const DRY_AT = 14.4;
// A touch pauses the loop this long; it then dries the paper and starts over.
const USER_HOLD = 7;

// Captions rise in this long after their ink lands, and leave in CAPTION_OUT when the next one does.
const CAPTION_DELAY = 160; // ms
const CAPTION_IN = 520; // ms
const CAPTION_OUT = 220; // ms

const ROMAN = ['I', 'II', 'III', 'IV'];
const COUNT_WORDS = ['One', 'Two', 'Three', 'Four'];

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const NOISE = `
float hash(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.07 + 17.3;
    a *= 0.5;
  }
  return v / 0.875;
}
`;

// Warm paper: fine grain, a few long fibres and a soft cloudiness, darker towards the corners.
const PAPER_FN = `
uniform float2 size;
half3 paper(float2 p) {
  float grain = noise(p * 1.7) - 0.5;
  float fibre = noise(float2(p.x * 0.05 + p.y * 0.02, p.y * 0.9)) - 0.5;
  float cloud = noise(p * 0.012) - 0.5;
  half3 c = half3(0.953, 0.937, 0.906);
  c += half(grain * 0.03 + fibre * 0.02 + cloud * 0.03);
  float2 q = p / size - 0.5;
  c *= half(1.0 - dot(q, q) * 0.16);
  return c;
}
`;

const PAPER_EFFECT = Skia.RuntimeEffect.Make(`${NOISE}${PAPER_FN}
half4 main(float2 p) { return half4(paper(p), 1.0); }
`)!;

// The paper with the printed photo and the ink bleeding over it. `base` is the photo already
// printed (-1 for bare paper) and `top` the one the ink is bringing in (-1 for none). The ink
// front is a circle around `drop` of radius `front`, its edge pushed in and out by noise sampled
// around the drop (large lobes) and along fine radial streaks (fibres). Behind the front the
// paper is dark with wet ink for BAND pt, then the photo comes through. Ink only takes inside
// the print, whose edge is ragged and darker, as watercolour dries.
const INK_EFFECT = Skia.RuntimeEffect.Make(`${NOISE}${PAPER_FN}
uniform shader photo0;
uniform shader photo1;
uniform shader photo2;
uniform shader photo3;
uniform float4 print;
uniform float base;
uniform float top;
uniform float2 drop;
uniform float front;
uniform float seed;
uniform float dry;
uniform float blend;

const half3 INK = half3(0.086, 0.102, 0.137);

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half3 photo(float i, float2 p) {
  if (i < 0.5) return photo0.eval(p).rgb;
  if (i < 1.5) return photo1.eval(p).rgb;
  if (i < 2.5) return photo2.eval(p).rgb;
  return photo3.eval(p).rgb;
}

// The photo as pigment on this paper: a little less saturated, multiplied over the grain, with
// a thin darker line at the print's edge where the ink pooled as it dried.
half3 printed(float i, float2 p, half3 sheet, float edge) {
  half3 c = photo(i, p);
  half l = dot(c, half3(0.299, 0.587, 0.114));
  c = mix(half3(l), c, 0.86) * 0.95 + 0.025;
  c *= sheet * 1.05;
  return mix(c, INK * 0.9 + c * 0.25, half(0.38 * exp(-pow((edge + 2.0) / 2.6, 2.0))));
}

half4 main(float2 p) {
  half3 sheet = paper(p);
  half3 col = sheet;

  float2 centre = print.xy + print.zw * 0.5;
  float edge = sdBox(p - centre, print.zw * 0.5 - 12.0, 34.0);
  edge += (fbm(p * 0.008 + 3.1) - 0.5) * 38.0;
  edge += (fbm(p * 0.05 + 7.7) - 0.5) * 7.0;
  float feather = (noise(p * 0.32) - 0.5) * 2.6;
  float inside = 1.0 - smoothstep(-1.0, 1.0, edge + feather);

  if (base > -0.5) {
    // Drying back to paper goes in blotches, not all at once.
    float blot = fbm(p * 0.008 + 11.0);
    float keep = smoothstep(blot - 0.03, blot + 0.03, dry * 1.3 - 0.15) * smoothstep(0.0, 0.45, dry);
    col = mix(col, printed(base, p, sheet, edge), half(inside * keep));
  }

  float2 v = p - drop;
  float r = length(v);
  // Past the farthest the ragged front can reach, the new ink has not arrived: skip its noise.
  if (top > -0.5 && r < front * ${(1 + LOBES + 0.02).toFixed(2)} + 34.0) {
    float2 dir = v / max(r, 0.001);
    float grow = smoothstep(0.0, 120.0, front);
    // Big soft lobes around the drop, blotches from the paper's own absorbency, and a fine
    // feathery fringe.
    float lobes = (fbm(dir * 1.5 + seed) - 0.5) * (front * ${LOBES} * 2.0 + 4.0);
    float blotch = (fbm(p * 0.022 + seed * 1.7) - 0.5) * 46.0 * grow;
    float fringe = (fbm(p * 0.21 + seed) - 0.5) * 9.0 * grow;
    float d = r + lobes + blotch + fringe - front;
    float wet = (1.0 - smoothstep(-0.9, 0.9, d)) * inside;
    // Behind the front the ink dries unevenly, letting the photo through in a mottle.
    float mottle = (fbm(p * 0.03 + seed * 2.3) - 0.5) * 24.0;
    float resolve = smoothstep(0.0, ${BAND}.0, -d + mottle);
    float ring = exp(-pow((d + 2.5) / 3.2, 2.0));
    half3 wash = mix(col, INK, 0.86);
    half3 layer = mix(wash, printed(top, p, sheet, edge), half(resolve));
    layer = mix(layer, INK * 0.55, half(ring * 0.8));
    col = mix(col, layer, half(wet * blend));
  }
  return half4(col, 1.0);
}
`)!;

/** Ease-out for the ink front: off at full speed the moment the drop lands, slowing as the paper drinks it. */
function spread(u: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, u));
  return 1 - (1 - k) * (1 - k) * (1 - k);
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

function clampDrift(v: number) {
  'worklet';
  return Math.max(-FALL_DRIFT_MAX, Math.min(FALL_DRIFT_MAX, v * FALL_DRIFT));
}

let placeholder: SkImage | null = null;

/** A plain blue-grey wash that stands in for a photo that could not be loaded. */
function placeholderPrint(): SkImage | null {
  if (placeholder) return placeholder;
  const surface = Skia.Surface.Make(64, 96);
  if (!surface) return null;
  const paint = Skia.Paint();
  paint.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, 96), [Skia.Color('#5d6b7c'), Skia.Color('#26303d')], null, TileMode.Clamp));
  surface.getCanvas().drawRect(Skia.XYWHRect(0, 0, 64, 96), paint);
  surface.flush();
  placeholder = surface.makeImageSnapshot();
  return placeholder;
}

/** Random in [0, 1) from an integer, the same on every run. */
function scatter(n: number) {
  'worklet';
  const s = Math.sin(n * 91.345 + 47.853) * 43758.5453;
  return s - Math.floor(s);
}

type Rect = { x: number; y: number; width: number; height: number };

/** How far the ink front must travel from `x, y` to have soaked every corner of the print. */
function reachFrom(x: number, y: number, print: Rect) {
  'worklet';
  let far = 0;
  for (const cx of [print.x, print.x + print.width]) {
    for (const cy of [print.y, print.y + print.height]) far = Math.max(far, Math.hypot(cx - x, cy - y));
  }
  return (far + BAND + 60) / (1 - LOBES * 0.6);
}

/**
 * Ink over warm paper. A drop falls, and the next photo bleeds into the print through an
 * organic, fibrous ink front that spreads from where it landed, dark and wet at the edge before
 * the image comes through. Tap anywhere to drop ink; hold first for a heavier drop.
 */
export function InkBleedReveal({ plates: given = PLATES, autoPlay = false, onReveal }: Props) {
  // An empty list falls back to the built-in plates, so there is always something to print.
  const plates = given.length > 0 ? given : PLATES;
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  // The ink shader takes four photos; fewer plates repeat, more are left out.
  const count = Math.min(4, plates.length);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [images, setImages] = useState<SkImage[] | null>(null);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const print = useMemo(
    () => ({ x: PRINT_SIDE, y: insets.top + PRINT_TOP, width: width - PRINT_SIDE * 2, height: height * PRINT_HEIGHT }),
    [insets.top, width, height],
  );
  const printRect = useMemo(() => Skia.XYWHRect(print.x, print.y, print.width, print.height), [print]);

  // The bead: 0 none, 1 hanging, 2 falling.
  const beadPhase = useSharedValue(0);
  const beadX = useSharedValue(0);
  const beadY = useSharedValue(0);
  const beadAim = useSharedValue({ x: 0, y: 0 });
  const beadShow = useSharedValue(0);
  const beadWeight = useSharedValue(0);
  const beadHeld = useSharedValue(0);
  const beadZ = useSharedValue(1);
  const fallClock = useSharedValue(0);
  const fallFrom = useSharedValue({ x: 0, y: 0, dx: 0, dy: 0 });
  const holding = useSharedValue(false);
  const byUser = useSharedValue(false);

  // The prints and the ink.
  const base = useSharedValue(-1);
  const top = useSharedValue(-1);
  const dropX = useSharedValue(0);
  const dropY = useSharedValue(0);
  const progress = useSharedValue(0);
  const rate = useSharedValue(1 / BLEED_TIME);
  const reach = useSharedValue(1);
  const seed = useSharedValue(0);
  const dry = useSharedValue(1);
  const drying = useSharedValue(false);
  const drops = useSharedValue(0);

  // Droplets thrown at contact.
  const splatAge = useSharedValue(10);
  const splatAt = useSharedValue({ x: 0, y: 0, dx: 0, dy: 0, weight: 0, seed: 0 });

  // 0 shows the intro, k + 1 the caption of plate k.
  const caption = useSharedValue(0);

  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const ready = useSharedValue(false);
  const printBox = useSharedValue(print);

  useEffect(() => {
    printBox.set(print);
  }, [print, printBox]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  // Photos load one by one; the ink starts as soon as the first is in, and a photo that fails
  // prints as a plain blue-grey wash instead.
  useEffect(() => {
    let alive = true;
    const loaded: (SkImage | null)[] = [null, null, null, null];
    const settle = (i: number, image: SkImage | null) => {
      if (!alive) return;
      loaded[i] = image ?? placeholderPrint();
      const first = loaded.find((im) => im !== null) ?? null;
      if (!first) return;
      setImages(loaded.map((im) => im ?? first) as SkImage[]);
      ready.set(true);
    };
    for (let i = 0; i < 4; i++) {
      const plate = plates[i % plates.length];
      Skia.Data.fromURI(photoUri(plate.photo))
        .then((data) => settle(i, Skia.Image.MakeImageFromEncoded(data)))
        .catch(() => settle(i, null));
    }
    return () => {
      alive = false;
    };
  }, [plates, ready]);

  const landed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const revealed = (index: number) => onReveal?.(index);

  /** Hangs a bead of ink over `x, y`, ready to fall. */
  const hang = (x: number, y: number, weight: number) => {
    'worklet';
    beadPhase.set(1);
    beadX.set(x);
    beadY.set(y);
    beadAim.set({ x, y });
    beadWeight.set(weight);
    beadZ.set(1);
    beadShow.set(0);
    beadShow.set(withTiming(1, { duration: BEAD_APPEAR * 1000, easing: EASE_OUT }));
  };

  /** Lets the bead go, carrying a little of the finger's speed. */
  const release = (vx: number, vy: number) => {
    'worklet';
    if (beadPhase.get() !== 1) return;
    fallFrom.set({ x: beadX.get(), y: beadY.get(), dx: clampDrift(vx), dy: clampDrift(vy) });
    fallClock.set(0);
    beadPhase.set(2);
    // An unfinished bleed hurries to finish as this drop lands.
    if (top.get() >= 0) rate.set(Math.max(rate.get(), (1 - progress.get()) / FALL_TIME));
  };

  /** The drop hits the paper: the next photo starts to bleed in from here. */
  const contact = (x: number, y: number, user: boolean) => {
    'worklet';
    if (top.get() >= 0) base.set(top.get());
    const from = top.get() >= 0 ? top.get() : base.get();
    const next = (from + 1 + count) % count;
    const weight = beadWeight.get();
    drops.set(drops.get() + 1);
    top.set(next);
    dropX.set(x);
    dropY.set(y);
    progress.set(0);
    rate.set(reducedMotion ? 1 / FADE_TIME : (0.85 + 0.35 * weight) / BLEED_TIME);
    reach.set(reachFrom(x, y, printBox.get()));
    seed.set(scatter(drops.get()) * 40);
    const f = fallFrom.get();
    const len = Math.hypot(f.dx, f.dy);
    splatAt.set({ x, y, dx: len > 1 ? f.dx / len : 0, dy: len > 1 ? f.dy / len : 0, weight, seed: drops.get() });
    splatAge.set(0);
    caption.set(next + 1);
    beadPhase.set(0);
    if (user) scheduleOnRN(landed);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const box = printBox.get();

    // The script, which waits for the photos.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        clock.set(0);
        if (base.get() >= 0 || top.get() >= 0) drying.set(true);
      }
    } else if (looping.get() && ready.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (const drop of DROPS) {
        if (crossed(before, t, drop.at) && beadPhase.get() === 0) {
          byUser.set(false);
          hang(box.x + box.width * drop.x, box.y + box.height * drop.y, drop.weight);
        }
        if (crossed(before, t, drop.at + DROP_HANG) && !byUser.get()) release(0, 0);
      }
      if (crossed(before, t, DRY_AT) && base.get() >= 0) drying.set(true);
    }

    // The hanging bead follows the finger and swells while it is held.
    if (beadPhase.get() === 1) {
      if (holding.get()) {
        beadHeld.set(beadHeld.get() + dt);
        beadWeight.set(Math.min(1, beadHeld.get() / HOLD_FULL));
      }
      // Under a finger the bead rises clear of it once the press turns into a hold.
      const aim = beadAim.get();
      const lift = holding.get() ? BEAD_LIFT * Math.min(1, beadHeld.get() / LIFT_TIME) : 0;
      const k = 1 - Math.exp(-dt * BEAD_FOLLOW);
      beadX.set(beadX.get() + (aim.x - beadX.get()) * k);
      beadY.set(beadY.get() + (aim.y - lift - beadY.get()) * k);
    } else if (beadPhase.get() === 2) {
      // Falling: height drops with the square of time, as under gravity.
      const tf = fallClock.get() + dt;
      fallClock.set(tf);
      const u = Math.min(1, tf / FALL_TIME);
      const f = fallFrom.get();
      beadZ.set(1 - u * u);
      beadX.set(f.x + f.dx * u);
      beadY.set(f.y + f.dy * u);
      if (u >= 1) contact(f.x + f.dx, f.y + f.dy, byUser.get());
    }

    if (top.get() >= 0) {
      const p = Math.min(1, progress.get() + rate.get() * dt);
      progress.set(p);
      if (p >= 1) {
        // A print that is still drying away gives way to the new one.
        drying.set(false);
        dry.set(1);
        base.set(top.get());
        top.set(-1);
        progress.set(0);
        scheduleOnRN(revealed, base.get());
      }
    }

    if (drying.get()) {
      const d = Math.max(0, dry.get() - dt / DRY_TIME);
      dry.set(d);
      if (d <= 0) {
        drying.set(false);
        base.set(-1);
        dry.set(1);
        if (top.get() < 0) caption.set(0);
      }
    }
    if (drying.get() && top.get() < 0 && dry.get() < 0.6) caption.set(0);

    splatAge.set(splatAge.get() + dt);
  });

  const uniforms = useDerivedValue(() => {
    const p = progress.get();
    return {
      size: [width, height],
      print: [print.x, print.y, print.width, print.height],
      base: base.get(),
      top: top.get(),
      drop: [dropX.get(), dropY.get()],
      front: reducedMotion ? reach.get() : reach.get() * spread(p),
      seed: seed.get(),
      dry: dry.get(),
      blend: reducedMotion ? p : 1,
    };
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    byUser.set(true);
  };

  const press = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      if (beadPhase.get() === 2 || !ready.get()) return;
      touched();
      holding.set(true);
      beadHeld.set(0);
      hang(event.x, event.y, 0);
    })
    .onUpdate((event) => {
      if (!holding.get()) return;
      beadAim.set({ x: event.x, y: event.y });
    })
    .onFinalize((event) => {
      if (!holding.get()) return;
      holding.set(false);
      hold.set(USER_HOLD);
      release(event.velocityX ?? 0, event.velocityY ?? 0);
    });

  // The bead: bigger and lifted while it hangs, its shadow sliding in under it as it falls.
  const beadRadius = useDerivedValue(() => BEAD_RADIUS * (1 + beadWeight.get()) * (0.7 + 0.3 * beadShow.get()));
  const beadTransform = useDerivedValue(() => {
    const z = beadZ.get();
    const breathe = beadPhase.get() === 1 ? 1 + 0.04 * Math.sin(clock.get() * 9 + beadHeld.get() * 7) : 1;
    return [
      { translateX: beadX.get() },
      { translateY: beadY.get() - z * 10 },
      { scale: (1 + 0.45 * z) * breathe },
      // A falling drop stretches along its fall.
      { scaleY: beadPhase.get() === 2 ? 1 + 0.18 * Math.sin(Math.PI * (1 - z)) : 1 },
    ];
  });
  const beadOpacity = useDerivedValue(() => (beadPhase.get() === 0 ? 0 : beadShow.get()));
  const shadowTransform = useDerivedValue(() => {
    const z = beadZ.get();
    return [{ translateX: beadX.get() + 9 * z }, { translateY: beadY.get() + 16 * z }, { scale: 1 + 0.6 * z }];
  });
  const shadowOpacity = useDerivedValue(() => beadOpacity.get() * (0.32 - 0.14 * beadZ.get()));
  const shadowBlur = useDerivedValue(() => 1.5 + beadZ.get() * 7);

  const marks = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const { x, y, width: w, height: h } = print;
    const corners: [number, number, number, number][] = [
      [x, y, -1, -1],
      [x + w, y, 1, -1],
      [x, y + h, -1, 1],
      [x + w, y + h, 1, 1],
    ];
    for (const [cx, cy, sx, sy] of corners) {
      b.moveTo(cx + sx * CROP_GAP, cy).lineTo(cx + sx * (CROP_GAP + CROP_MARK), cy);
      b.moveTo(cx, cy + sy * CROP_GAP).lineTo(cx, cy + sy * (CROP_GAP + CROP_MARK));
    }
    return b.detach();
  }, [print]);

  return (
    <GestureDetector gesture={press}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Ink and water: drop ink to reveal the next print"
        accessibilityActions={[{ name: 'activate', label: 'Drop ink' }]}
        onAccessibilityAction={() => {
          const x = print.x + print.width / 2;
          const y = print.y + print.height / 2;
          hold.set(USER_HOLD);
          byUser.set(true);
          beadPhase.set(1);
          beadX.set(x);
          beadY.set(y);
          beadAim.set({ x, y });
          beadWeight.set(0.5);
          beadShow.set(1);
          release(0, 0);
        }}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              {images ? (
                <Shader source={INK_EFFECT} uniforms={uniforms}>
                  {images.map((image, i) => (
                    <ImageShader key={i} image={image} fit="cover" rect={printRect} />
                  ))}
                </Shader>
              ) : (
                <Shader source={PAPER_EFFECT} uniforms={{ size: [width, height] }} />
              )}
            </Fill>
            <Path path={marks} style="stroke" strokeWidth={0.8} color={INK} opacity={0.32} />
            {Array.from({ length: SPLAT_COUNT }, (_, i) => (
              <Droplet key={i} index={i} age={splatAge} at={splatAt} />
            ))}
            <Group transform={shadowTransform} opacity={shadowOpacity}>
              <Circle cx={0} cy={0} r={beadRadius} color={INK}>
                <BlurMask blur={shadowBlur} style="normal" />
              </Circle>
            </Group>
            <Group transform={beadTransform} opacity={beadOpacity}>
              <Circle cx={0} cy={0} r={beadRadius}>
                <RadialGradient c={vec(-3, -4)} r={BEAD_RADIUS * 2.4} colors={['#4a5266', INK, '#07080c']} positions={[0, 0.55, 1]} />
              </Circle>
              <Highlight radius={beadRadius} />
            </Group>
          </Canvas>
        )}

        <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
          <Text style={styles.headerLabel}>Field Journal</Text>
          {[INTRO, ...plates.slice(0, count)].map((plate, i) => (
            <Caption key={i} index={i} caption={caption} style={styles.headerRight}>
              <Text style={styles.headerLabel}>{i === 0 ? `${COUNT_WORDS[count - 1] ?? count} prints` : `${ROMAN[i - 1] ?? i} / ${ROMAN[count - 1] ?? count}`}</Text>
            </Caption>
          ))}
        </View>

        <View pointerEvents="none" style={[styles.captions, { top: print.y + print.height + 30 }]}>
          {[INTRO, ...plates.slice(0, count)].map((plate, i) => (
            <Caption key={i} index={i} caption={caption} style={styles.caption}>
              <Text style={styles.place}>{plate.place}</Text>
              <Text style={styles.title}>{plate.title}</Text>
              <Text style={styles.line}>{plate.line}</Text>
            </Caption>
          ))}
        </View>

        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 22 }]}>
          Tap to drop ink · hold for a heavier drop
        </Text>
      </View>
    </GestureDetector>
  );
}

type CaptionProps = {
  index: number;
  caption: SharedValue<number>;
  style: object;
  children: ReactNode;
};

/** One caption: it rises in once its ink has landed, and fades out quickly when the next one lands. */
function Caption({ index, caption, style, children }: CaptionProps) {
  const reducedMotion = useReducedMotion();
  const animated = useAnimatedStyle(() => {
    const on = Math.round(caption.get()) === index;
    return {
      opacity: on
        ? withDelay(CAPTION_DELAY, withTiming(1, { duration: CAPTION_IN, easing: EASE_OUT }))
        : withTiming(0, { duration: CAPTION_OUT, easing: EASE_OUT }),
      transform: [
        {
          translateY: reducedMotion
            ? 0
            : on
              ? withDelay(CAPTION_DELAY, withTiming(0, { duration: CAPTION_IN + 80, easing: EASE_OUT }))
              : withTiming(-6, { duration: CAPTION_OUT, easing: EASE_OUT }),
        },
      ],
    };
  });
  return <Animated.View style={[style, { opacity: 0, transform: [{ translateY: 12 }] }, animated]}>{children}</Animated.View>;
}

type DropletProps = {
  index: number;
  age: SharedValue<number>;
  at: SharedValue<{ x: number; y: number; dx: number; dy: number; weight: number; seed: number }>;
};

/** A droplet flung from the drop as it hits: it flies out, then soaks into the paper. */
function Droplet({ index, age, at }: DropletProps) {
  const spot = useDerivedValue(() => {
    const s = at.get();
    const a = (index / SPLAT_COUNT) * Math.PI * 2 + (scatter(s.seed * 13 + index) - 0.5) * 2.2;
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    // Thrown further in the direction the drop was moving.
    const lean = 1 + 0.9 * Math.max(0, ca * s.dx + sa * s.dy);
    const reach = scatter(s.seed * 7 + index * 3);
    const far = (10 + 46 * reach * reach) * (0.8 + 0.6 * s.weight) * lean;
    const u = Math.min(1, age.get() / SPLAT_OUT);
    const out = 1 - (1 - u) * (1 - u) * (1 - u);
    return { x: s.x + ca * far * out, y: s.y + sa * far * out, r: (1.1 + 2.6 * scatter(s.seed * 5 + index * 11)) * (0.7 + 0.5 * s.weight) };
  });
  const cx = useDerivedValue(() => spot.get().x);
  const cy = useDerivedValue(() => spot.get().y);
  const r = useDerivedValue(() => spot.get().r);
  const opacity = useDerivedValue(() => {
    const t = age.get();
    if (t > SPLAT_OUT + SPLAT_SOAK) return 0;
    return 0.92 * (1 - Math.max(0, (t - SPLAT_OUT) / SPLAT_SOAK));
  });
  return <Circle cx={cx} cy={cy} r={r} color={INK} opacity={opacity} />;
}

/** The glint on the bead. */
function Highlight({ radius }: { radius: SharedValue<number> }) {
  const r = useDerivedValue(() => radius.get() * 0.28);
  const cx = useDerivedValue(() => -radius.get() * 0.36);
  const cy = useDerivedValue(() => -radius.get() * 0.38);
  return <Circle cx={cx} cy={cy} r={r} color="rgba(255,255,255,0.75)" />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: PAPER,
  },
  header: {
    position: 'absolute',
    left: PRINT_SIDE + 2,
    right: PRINT_SIDE + 2,
    height: 20,
  },
  headerRight: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  headerLabel: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.8,
    textTransform: 'uppercase',
    color: INK_SOFT,
  },
  captions: {
    position: 'absolute',
    left: PRINT_SIDE + 4,
    right: PRINT_SIDE + 4,
  },
  caption: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  place: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.8,
    textTransform: 'uppercase',
    color: INK_SOFT,
    marginBottom: 6,
  },
  title: {
    fontFamily: SERIF,
    fontStyle: 'italic',
    fontWeight: '700',
    fontSize: 36,
    lineHeight: 42,
    letterSpacing: -0.3,
    color: INK,
  },
  line: {
    marginTop: 8,
    fontFamily: SERIF,
    fontSize: 17,
    lineHeight: 24,
    color: INK_SOFT,
    maxWidth: 330,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: 13,
    fontWeight: '500',
    color: INK_SOFT,
    opacity: 0.85,
  },
});
