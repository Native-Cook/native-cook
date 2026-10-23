import {
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolate,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type RolodexItem = {
  title: string;
  /** One word, shown between "Because" and "Matters" while the card is in front. */
  category: string;
  /** A line under the title once the card is open. */
  caption: string;
  /** Address of a photo, about 1 : 0.93. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

/** How the cards already seen leave: lying back face up towards the viewer, or tipping forward. */
export type Fall = 'back' | 'forward';

type Props = {
  /** The cards, in the order they come forward. Sixteen or more keep the far stack reaching the top. */
  items?: RolodexItem[];
  /** Brings the next card forward every 1.4 s, like a rolodex turned by hand. */
  autoPlay?: boolean;
  /** How the cards already seen leave the front. */
  fall?: Fall;
  background?: string;
  textColor?: string;
  /** Called with the index of a card when it opens. */
  onOpen?: (index: number) => void;
};

// The camera: its focal length in screen widths, and the height (as a share of the screen) it
// looks along, which is where the middle of the card in front sits.
const FOCAL = 2.24;
const EYE = 0.42;
// A card is one unit wide; this is its height and the radius of its corners.
const ASPECT = 0.93;
const RADIUS = 0.06;
// The card in front stands this far from the camera.
const FRONT_Z = 4;

// Where a card sits for `d` = its index minus the scroll. 0 is in front; the cards already seen
// (negative d) have come towards the viewer and down. Y is the height of the card's middle (down
// is positive), Z its distance, pitch its lean in degrees, positive tipping the top edge towards
// the viewer. Seen cards either lie back face up, overlapping like cards dealt on a table in front
// of the viewer ('back'), or tip forward with their top edges towards the lens ('forward').
const NEAR_FROM = -5;
const NEAR: Record<Fall, { y: number[]; z: number[]; pitch: number[] }> = {
  back: {
    y: [1.4, 1.3, 1.16, 1.0, 0.74, 0],
    z: [0.95, 1.4, 1.9, 2.45, 3.25, FRONT_Z],
    pitch: [-78, -75, -72, -68, -62, -4],
  },
  forward: {
    y: [2.6, 1.85, 1.36, 1.04, 0.82, 0],
    z: [1.15, 1.55, 1.98, 2.32, 2.62, FRONT_Z],
    pitch: [70, 62, 52, 46, 40, -4],
  },
};
// Cards already seen darken as they come nearer, up to a limit, and the bottom of the screen
// sinks into the background over BOTTOM_FADE pt so the labels stay clear.
const NEAR_SHADE = 0.14;
const NEAR_SHADE_MAX = 0.45;
const BOTTOM_FADE = 240;
// The cards still to come stand in a line that climbs away behind the one in front, each a step
// higher and further, so only a sliver of each shows above the next.
const FAR_Y = -0.13;
const FAR_Z = 4.3;
const FAR_STEP_Y = -0.19;
const FAR_STEP_Z = 0.31;
const FAR_PITCH = -7;
// The card falling out of the front stays upright a little longer before it tips: the share of
// the way, raised to this power, that its lean has turned.
const TIP_LAG = 1.8;
// Cards further back darken a little, up to a limit.
const FAR_SHADE = 0.035;
const FAR_SHADE_MAX = 0.42;
// The range of `d` that cards wrap around in: a card leaving the bottom comes back at the far end.
const WRAP_FROM = -4.5;
// How far, in cards, the far end of the stack fades in.
const FAR_FADE = 1.5;
// A card is dropped once any corner comes this close to the lens.
const NEAR_CLIP = 0.2;

// Finger travel that moves the stack by one card, as a share of the card in front's height.
const DRAG_STEP = 0.8;
const DRAG_SLOP = 6;
// The snap after a release: a spring on the scroll position, in cards.
const MASS = 1;
const STIFFNESS = 170;
const DAMPING = 24;
// Seconds of speed a release throws the stack ahead by.
const THROW_TIME = 0.18;

// The self-playing turn: a card comes forward every STEP_EVERY seconds over STEP_TIME.
const STEP_EVERY = 1.4;
const STEP_TIME = 0.46;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 3;

// An open card: where its middle sits as a share of the screen, and its side margin.
const OPEN_AT = 0.4;
const OPEN_MARGIN = 20;
const OPEN_SPRING = { duration: 560, dampingRatio: 0.86 } as const;
// While a card opens the rest of the stack parts: the far cards rise, the near ones drop.
const OPEN_RISE = 1.4;
const OPEN_DROP = 1.2;
// A drag down by this share of the screen closes an open card all the way.
const DISMISS_SHARE = 0.45;
const DISMISS_SPEED = 600;
const DISMISS_KEEP = 0.65;
const OPEN_RUBBER = 0.2;
const CAPTION_RISE = 18;

// The labels: their size, and how far the middle word rolls per card.
const LABEL_SIZE = 12;
const LINE = 16;
const IMAGE_FADE = 0.35;
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=760&h=706&q=82`;

const ITEMS: RolodexItem[] = [
  { title: 'Swell', category: 'Ocean', caption: 'A wave held at the top of its curl.', photo: photo('photo-1616141893496-fbc65370493e'), tint: '#245d6e' },
  { title: 'Seat No. 4', category: 'Design', caption: 'A black shell chair on white.', photo: photo('photo-1581539250439-c96689b516dd'), tint: '#dcdcdc' },
  { title: 'Ember', category: 'Colour', caption: 'Red light, one face, no shadow.', photo: photo('photo-1727341556787-7292f7515aec'), tint: '#c42a1c' },
  { title: 'Red Room', category: 'Light', caption: 'A neon cube left in the trees.', photo: photo('photo-1629440951880-8c81df77e2d0'), tint: '#5a1810' },
  { title: 'Open Field', category: 'Space', caption: 'One cloud over a green hill.', photo: photo('photo-1648219124133-8e53e8a9577c'), tint: '#7da35a' },
  { title: 'Plain Oak', category: 'Craft', caption: 'A bentwood chair against yellow.', photo: photo('photo-1640938776314-4d303f8a1380'), tint: '#d8b832' },
  { title: 'Bloom', category: 'Nature', caption: 'Orange gerberas in a white jar.', photo: photo('photo-1604435719097-7f73bfc6304a'), tint: '#8aa3a6' },
  { title: 'Level −2', category: 'Motion', caption: 'A white car under strip lights.', photo: photo('photo-1611760399750-bf3b95ac8f0a'), tint: '#3f3f3f' },
  { title: 'Signal', category: 'Culture', caption: 'A sneaker drawn in pink neon.', photo: photo('photo-1628558422453-576457df4b0d'), tint: '#5b2a5c' },
  { title: 'Flare', category: 'Fashion', caption: 'Red silk against a burning sky.', photo: photo('photo-1767049606807-2fcc71c865f2'), tint: '#7c6a6c' },
  { title: 'Tulip', category: 'Form', caption: 'A white pedestal, a red seat.', photo: photo('photo-1659246973603-748505504837'), tint: '#e4e1dc' },
  { title: 'Soft', category: 'Beauty', caption: 'Skin, close, in morning light.', photo: photo('photo-1643630661247-2474f10e4f70'), tint: '#caa38c' },
  { title: 'Noon', category: 'Style', caption: 'A black shirt against a yellow wall.', photo: photo('photo-1680967494203-789f70ddcfdf'), tint: '#f0c02a' },
  { title: 'Midnight', category: 'Detail', caption: 'Dutch flowers out of the dark.', photo: photo('photo-1783680599080-abe4d5634c3a'), tint: '#2a2118' },
  { title: 'High Sky', category: 'Freedom', caption: 'Red wool under a blue noon.', photo: photo('photo-1662532577856-e8ee8b138a8b'), tint: '#2d55c8' },
  { title: 'Loop', category: 'Material', caption: 'A ceramic ring in sand tones.', photo: photo('photo-1643569556871-91ec60671ed7'), tint: '#d9d0c3' },
  { title: 'Navy', category: 'Comfort', caption: 'One moulded chair, grey room.', photo: photo('photo-1748887522222-ae67e761fb64'), tint: '#c9ccd0' },
  { title: 'Scarlet', category: 'Feeling', caption: 'A hand to the cheek, red behind.', photo: photo('photo-1727341557531-343cd4364501'), tint: '#b5201a' },
  { title: 'Arcs', category: 'Balance', caption: 'Half circles leaning on a wall.', photo: photo('photo-1620987278429-ab178d6eb547'), tint: '#c9a27a' },
  { title: 'Cut Out', category: 'Shape', caption: 'Black wool on a white page.', photo: photo('photo-1603189343302-e603f7add05a'), tint: '#efefef' },
  { title: 'Peach', category: 'Warmth', caption: 'A tufted armchair, soft light.', photo: photo('photo-1722357533644-9b20e356ba5f'), tint: '#e8cdb2' },
  { title: 'Eclipse', category: 'Mood', caption: 'A profile against blue light.', photo: photo('photo-1691679769924-4430510fbc4f'), tint: '#0c1733' },
];

type Loaded = { image: SkImage; at: number };

type Stage = { width: number; height: number; top: number; bottom: number };

type Shown = {
  index: number;
  z: number;
  matrix: number[];
  quad: number[];
  alpha: number;
  shade: number;
};

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** `x` moved by whole turns of `n` into [from, from + n). */
function wrap(x: number, n: number, from: number) {
  'worklet';
  return x - n * Math.floor((x - from) / n);
}

function easeInOut(p: number) {
  'worklet';
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}

/** The card's place at a whole step `i` of the path: [y, z, pitch]. */
function key(i: number, fall: Fall) {
  'worklet';
  if (i <= 0) {
    const k = Math.max(0, i - NEAR_FROM);
    const near = NEAR[fall];
    return [near.y[k], near.z[k], near.pitch[k]];
  }
  return [FAR_Y + FAR_STEP_Y * (i - 1), FAR_Z + FAR_STEP_Z * (i - 1), FAR_PITCH];
}

/** Catmull-Rom through four values, at `t` between the middle two. */
function spline(a: number, b: number, c: number, d: number, t: number) {
  'worklet';
  const t2 = t * t;
  const t3 = t2 * t;
  return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3);
}

/** Where a card sits for `d`, smoothly between the steps of the path. */
function place(d: number, fall: Fall) {
  'worklet';
  const i = Math.floor(d);
  const t = d - i;
  const a = key(i - 1, fall);
  const b = key(i, fall);
  const c = key(i + 1, fall);
  const e = key(i + 2, fall);
  let pitch = spline(a[2], b[2], c[2], e[2], t);
  if (d > -1 && d < 0) {
    // Falling out of the front: the lean follows the fall late.
    const s = Math.pow(-d, TIP_LAG);
    const lean = NEAR[fall].pitch;
    pitch = lean[-1 - NEAR_FROM] * s + lean[-NEAR_FROM] * (1 - s);
  }
  return { y: spline(a[0], b[0], c[0], e[0], t), z: spline(a[1], b[1], c[1], e[1], t), pitch };
}

/**
 * Every card the camera sees, far to near: the perspective matrix that maps its face (a hundred
 * units wide, about its middle) onto the screen, its corners there, how faded and how dark it is.
 */
function frame(count: number, scroll: number, open: number, openIndex: number, stage: Stage, fall: Fall): Shown[] {
  'worklet';
  const { width, height } = stage;
  const f = width * FOCAL;
  const cx = width / 2;
  const cy = height * EYE;
  const shown: Shown[] = [];
  const farEnd = WRAP_FROM + count;
  for (let index = 0; index < count; index += 1) {
    const d = wrap(index - scroll, count, WRAP_FROM);
    const p = place(d, fall);
    let y = p.y;
    let z = p.z;
    let pitch = p.pitch;
    let alpha = clamp01((farEnd - d) / FAR_FADE);
    let shade = d > 0 ? Math.min(FAR_SHADE_MAX, Math.max(0, d - 0.5) * FAR_SHADE) : Math.min(NEAR_SHADE_MAX, -d * NEAR_SHADE);
    if (open > 0) {
      const o = Math.min(open, 1);
      if (index === openIndex) {
        // Upright, nearly the width of the screen, a little above the middle.
        const openZ = f / (width - OPEN_MARGIN * 2);
        const openY = ((height * OPEN_AT - cy) * openZ) / f;
        y += (openY - y) * open;
        z += (openZ - z) * open;
        pitch *= 1 - o;
        shade *= 1 - o;
      } else {
        y += (d > 0 ? -OPEN_RISE : OPEN_DROP) * o;
        alpha *= Math.max(0, 1 - o * 1.4);
      }
    }
    if (alpha <= 0.004) continue;
    const rad = (pitch * Math.PI) / 180;
    const s = Math.sin(rad);
    const c = Math.cos(rad);
    const k = 1 / 100;
    const matrix = [(f * k) / z, (cx * k * s) / z, cx, 0, (cy * k * s + f * k * c) / z, cy + (f * y) / z, 0, (k * s) / z, 1];
    const hw = 50;
    const hh = 50 * ASPECT;
    const quad: number[] = [];
    let behind = false;
    for (const [u, v] of [
      [-hw, -hh],
      [hw, -hh],
      [hw, hh],
      [-hw, hh],
    ]) {
      const w = matrix[7] * v + 1;
      if (w * z <= NEAR_CLIP) behind = true;
      quad.push((matrix[0] * u + matrix[1] * v + matrix[2]) / w, (matrix[4] * v + matrix[5]) / w);
    }
    if (behind) continue;
    const top = Math.min(quad[1], quad[3], quad[5], quad[7]);
    const bottom = Math.max(quad[1], quad[3], quad[5], quad[7]);
    if (bottom < 0 || top > height) continue;
    // The open card is drawn last, over everything.
    shown.push({ index, z: index === openIndex && open > 0.01 ? 0 : z, matrix, quad, alpha, shade });
  }
  return shown.sort((a, b) => b.z - a.z);
}

/** Whether a point lies inside a convex quad given corner by corner. */
function inside(quad: number[], x: number, y: number) {
  'worklet';
  let sign = 0;
  for (let i = 0; i < 4; i += 1) {
    const j = (i + 1) % 4;
    const cross = (quad[j * 2] - quad[i * 2]) * (y - quad[i * 2 + 1]) - (quad[j * 2 + 1] - quad[i * 2 + 1]) * (x - quad[i * 2]);
    if (cross === 0) continue;
    if (sign === 0) sign = Math.sign(cross);
    else if (Math.sign(cross) !== sign) return false;
  }
  return true;
}

/** The card's face: its tint, then its photo cropped to fill once it arrives, then its shade. */
function drawCard(canvas: SkCanvas, item: RolodexItem, loaded: Loaded | null, shade: number, now: number) {
  'worklet';
  const w = 100;
  const h = 100 * ASPECT;
  const rect = Skia.XYWHRect(-w / 2, -h / 2, w, h);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(rect, RADIUS * 100, RADIUS * 100), ClipOp.Intersect, true);
  const paint = Skia.Paint();
  paint.setColor(Skia.Color(item.tint));
  canvas.drawRect(rect, paint);
  if (loaded) {
    const image = loaded.image;
    const iw = image.width();
    const ih = image.height();
    const fit = Math.max(w / iw, h / ih);
    const sw = w / fit;
    const sh = h / fit;
    const art = Skia.Paint();
    art.setAntiAlias(true);
    art.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
    canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), rect, FilterMode.Linear, MipmapMode.Linear, art);
  }
  if (shade > 0.004) {
    const dark = Skia.Paint();
    dark.setColor(Skia.Color('#000'));
    dark.setAlphaf(shade);
    canvas.drawRect(rect, dark);
  }
  canvas.restore();
}

/** Text at `x`, set to its left (align 0), middle (0.5) or right (1). */
function drawLabel(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, align: number, paint: SkPaint) {
  'worklet';
  const width = font.measureText(text).width;
  canvas.drawText(text, x - width * align, y, paint, font);
}

/**
 * An editorial rolodex of photo cards on a black screen. The card in front stands a little above
 * the middle; the cards to come wait behind it in a tall stack that climbs away to the top of the
 * screen, a sliver of each showing; and the cards already seen have tipped forward towards the
 * viewer, large and leaning, until they leave the bottom edge. Dragging turns the stack and a
 * release snaps a card to the front; the stack never ends. Tapping the card in front stands it up,
 * lifts it out to nearly the full width and shows its title; a drag down puts it back.
 */
export function PerspectiveRolodex({
  items = ITEMS,
  autoPlay = false,
  fall = 'back',
  background = '#000000',
  textColor = '#f2f1ec',
  onOpen,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = items.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  // The card whose title is shown while it is open.
  const [shown, setShown] = useState(0);
  const font = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: LABEL_SIZE, fontWeight: '300' }), []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const frontHeight = ((width * FOCAL) / FRONT_Z) * ASPECT;
  const step = Math.max(1, frontHeight * DRAG_STEP);
  const dismiss = Math.max(1, height * DISMISS_SHARE);
  const openHeight = (width - OPEN_MARGIN * 2) * ASPECT;

  // `scroll` counts cards that have come forward; it is kept within one turn of the stack.
  const scroll = useSharedValue(0);
  const seen = useSharedValue(0);
  const velocity = useSharedValue(0);
  const target = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // 0 shut, 1 open; the card it applies to.
  const open = useSharedValue(0);
  const openIndex = useSharedValue(-1);
  const isOpen = useSharedValue(false);
  const openFrom = useSharedValue(0);
  // What a drag moves: 0 the stack, 1 the open card.
  const dragMode = useSharedValue(0);
  // A finger, not the loop, moved the stack last; only then does it tick.
  const byUser = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The self-playing step under way: where it started, and how long ago (−1 for none).
  const stepFrom = useSharedValue(0);
  const stepT = useSharedValue(-1);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[]>(items.map(() => null));
  // The screen for the gestures and the picture, which cannot read `size` as it changes.
  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    stepT.set(-1);
  }, [auto, clock, hold, looping, stepT]);

  useEffect(() => {
    stage.set({ width, height, top: insets.top, bottom: insets.bottom });
  }, [width, height, insets.top, insets.bottom, stage]);

  // Each photo arrives on its own and fades in; until then its card shows its tint.
  useEffect(() => {
    let alive = true;
    loaded.set(items.map(() => null));
    items.forEach((item, i) => {
      Skia.Data.fromURI(item.photo)
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: elapsed.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [items, loaded, elapsed]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const opened = (index: number) => {
    setShown(index);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onOpen?.(index);
  };

  const settleOpen = (to: number, speed: number) => {
    'worklet';
    isOpen.set(to === 1);
    open.set(
      reducedMotion
        ? withTiming(to, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) })
        : withSpring(to, { ...OPEN_SPRING, velocity: speed }),
    );
  };
  const openFront = (front: number) => {
    'worklet';
    target.set(front);
    openIndex.set(wrap(front, count, 0));
    settleOpen(1, 0);
    scheduleOnRN(opened, wrap(front, count, 0));
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    elapsed.set(elapsed.get() + dt);
    if (dragging.get()) {
      velocity.set(velocity.get() + ((scroll.get() - seen.get()) / dt - velocity.get()) * 0.5);
      seen.set(scroll.get());
      return;
    }
    if (hold.get() > 0) {
      // An open card keeps the loop waiting until it is put back.
      if (!isOpen.get()) hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get() && stepT.get() < 0) {
      clock.set(clock.get() + dt);
      if (clock.get() >= STEP_EVERY) {
        clock.set(0);
        byUser.set(false);
        stepFrom.set(Math.round(target.get()));
        stepT.set(0);
      }
    }
    if (stepT.get() >= 0) {
      const t = stepT.get() + dt;
      const p = Math.min(1, t / STEP_TIME);
      const next = stepFrom.get() + easeInOut(p);
      velocity.set((next - scroll.get()) / dt);
      scroll.set(next);
      if (p >= 1) {
        stepT.set(-1);
        target.set(stepFrom.get() + 1);
        velocity.set(0);
      } else {
        stepT.set(t);
      }
    } else if (reducedMotion) {
      scroll.set(target.get());
      velocity.set(0);
    } else {
      const v = velocity.get() + ((STIFFNESS * (target.get() - scroll.get()) - DAMPING * velocity.get()) / MASS) * dt;
      velocity.set(v);
      scroll.set(scroll.get() + v * dt);
    }
    // Whole turns of the stack change nothing on screen, so the numbers are kept small.
    if (stepT.get() < 0) {
      const turns = Math.floor(target.get() / count) * count;
      if (turns !== 0) {
        scroll.set(scroll.get() - turns);
        target.set(target.get() - turns);
      }
    }
    seen.set(scroll.get());
  });

  // A selection tick each time another card reaches the front under the user's hand.
  useAnimatedReaction(
    () => Math.round(scroll.get()),
    (now, before) => {
      if (before !== null && now !== before && byUser.get() && open.get() < 0.5) scheduleOnRN(tick);
    },
  );

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    clock.set(0);
    byUser.set(true);
    if (stepT.get() >= 0) {
      // A step under way hands its motion to the spring and lands where it was going.
      stepT.set(-1);
      target.set(stepFrom.get() + 1);
    }
  };

  const pan = Gesture.Pan()
    .minDistance(DRAG_SLOP)
    .onStart(() => {
      touched();
      if (isOpen.get() || open.get() > 0.5) {
        dragMode.set(1);
        openFrom.set(open.get());
        return;
      }
      dragMode.set(0);
      dragFrom.set(scroll.get());
      seen.set(scroll.get());
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (dragMode.get() === 1) {
        const p = openFrom.get() - event.translationY / dismiss;
        open.set(p > 1 ? 1 + (p - 1) * OPEN_RUBBER : Math.max(0, p));
        return;
      }
      scroll.set(dragFrom.get() + event.translationY / step);
    })
    .onEnd((event) => {
      if (dragMode.get() === 1) {
        const shut = event.velocityY > DISMISS_SPEED || open.get() < DISMISS_KEEP;
        settleOpen(shut ? 0 : 1, -event.velocityY / dismiss);
        return;
      }
      dragging.set(false);
      target.set(Math.round(scroll.get() + velocity.get() * THROW_TIME));
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (isOpen.get()) {
        settleOpen(0, 0);
        return;
      }
      // The card under the finger: the nearest one whose outline holds the point.
      const cards = frame(count, scroll.get(), 0, -1, stage.get(), fall);
      for (let i = cards.length - 1; i >= 0; i -= 1) {
        if (!inside(cards[i].quad, event.x, event.y)) continue;
        const d = wrap(cards[i].index - scroll.get(), count, WRAP_FROM);
        // The card that was tapped, even if the stack was still turning under the finger.
        const front = Math.round(scroll.get() + d);
        if (Math.abs(d) < 0.5) openFront(front);
        else target.set(front);
        return;
      }
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
    if (room.width <= 0) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    const at = scroll.get();
    const o = open.get();
    const images = loaded.get();

    const ground = Skia.Paint();
    ground.setColor(Skia.Color(background));
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), ground);

    const chrome = 1 - 0.6 * clamp01(o);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color(textColor));
    ink.setAlphaf(chrome);
    const topLine = room.top + 14;
    const bottomLine = room.height - room.bottom - 10;
    // The middle label at the top sits behind the stack, which climbs past it.
    drawLabel(canvas, 'Content', font, room.width / 2, topLine, 0.5, ink);

    for (const card of frame(count, at, o, openIndex.get(), room, fall)) {
      const q = card.quad;
      if (card.alpha < 0.995) {
        const left = Math.min(q[0], q[2], q[4], q[6]);
        const top = Math.min(q[1], q[3], q[5], q[7]);
        const right = Math.max(q[0], q[2], q[4], q[6]);
        const bottom = Math.max(q[1], q[3], q[5], q[7]);
        const layer = Skia.Paint();
        layer.setAlphaf(card.alpha);
        canvas.saveLayer(layer, Skia.XYWHRect(left - 2, top - 2, right - left + 4, bottom - top + 4));
      } else {
        canvas.save();
      }
      canvas.concat(Skia.Matrix(card.matrix));
      drawCard(canvas, items[card.index], images[card.index], card.shade, now);
      canvas.restore();
    }

    // The near cards sink into the background towards the bottom edge.
    const sink = Skia.Paint();
    sink.setShader(
      Skia.Shader.MakeLinearGradient(
        vec(0, room.height - BOTTOM_FADE),
        vec(0, room.height),
        [Skia.Color(`${background}00`), Skia.Color(`${background}f2`)],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, room.height - BOTTOM_FADE, room.width, BOTTOM_FADE), sink);

    drawLabel(canvas, 'Selected', font, OPEN_MARGIN, topLine, 0, ink);
    drawLabel(canvas, 'For You', font, room.width - OPEN_MARGIN, topLine, 1, ink);
    drawLabel(canvas, 'Because', font, OPEN_MARGIN, bottomLine, 0, ink);
    drawLabel(canvas, 'Matters', font, room.width - OPEN_MARGIN, bottomLine, 1, ink);
    // The middle word rolls up a line per card: the front card's category.
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(0, bottomLine - LINE, room.width, LINE + 5), ClipOp.Intersect, true);
    const front = Math.round(at);
    for (let k = front - 1; k <= front + 1; k += 1) {
      const d = k - at;
      const fade = clamp01(1 - Math.abs(d) / 0.7);
      if (fade <= 0) continue;
      const word = Skia.Paint();
      word.setAntiAlias(true);
      word.setColor(Skia.Color(textColor));
      word.setAlphaf(fade * chrome);
      drawLabel(canvas, items[wrap(k, count, 0)].category, font, room.width / 2, bottomLine + d * LINE, 0.5, word);
    }
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const captionStyle = useAnimatedStyle(() => {
    const o = open.get();
    return {
      opacity: interpolate(o, [0.55, 1], [0, 1], 'clamp'),
      transform: [{ translateY: (1 - Math.min(o, 1)) * CAPTION_RISE }],
    };
  });

  const item = items[shown] ?? items[0];

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        accessible
        accessibilityLabel={`Rolodex of ${count} photo cards. Swipe down to bring the next card forward; tap the front card to open it.`}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
        {size && (
          <Animated.View
            pointerEvents="none"
            style={[styles.caption, { top: height * OPEN_AT + openHeight / 2 + 22 }, captionStyle]}
          >
            <Text style={[styles.meta, { color: textColor }]}>
              {String(shown + 1).padStart(2, '0')} — {item.category}
            </Text>
            <Text style={[styles.title, { color: textColor }]}>{item.title}</Text>
            <Text style={[styles.body, { color: textColor }]}>{item.caption}</Text>
          </Animated.View>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  caption: {
    position: 'absolute',
    left: 24,
    right: 24,
    gap: 6,
  },
  meta: {
    fontSize: 12,
    letterSpacing: 0.4,
    opacity: 0.6,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: 34,
    fontWeight: '300',
    letterSpacing: -0.6,
  },
  body: {
    fontSize: 15,
    lineHeight: 21,
    opacity: 0.7,
  },
});
