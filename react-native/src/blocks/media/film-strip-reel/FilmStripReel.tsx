import {
  BlurStyle,
  Canvas,
  ClipOp,
  FillType,
  FilterMode,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  TileMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One frame of the roll: an Unsplash photo, how it is framed, and what its print says. */
export type ReelFrame = { id: string; shape: 'portrait' | 'landscape'; title: string; line: string };

type Props = {
  frames?: ReelFrame[];
  /** Winds a dozen frames on, lifts the centred one into a print and drops it back, twice per LOOP. */
  autoPlay?: boolean;
  /** Shown in the corner and on every print's stamp. */
  roll?: string;
  /** Called with a frame's index when it is lifted into a print. */
  onPrint?: (index: number) => void;
};

// A portrait original loses a third of its height to the 3:2 frame, so it needs less width than a
// landscape one for the same sharpness; both leave enough for the print, ~320 pt wide at 3x.
const FETCH_WIDTH = { portrait: 720, landscape: 900 } as const;
const unsplash = (frame: ReelFrame) =>
  `https://images.unsplash.com/${frame.id}?auto=format&fit=crop&w=${FETCH_WIDTH[frame.shape]}&q=80`;

const p = (id: string, title: string, line: string): ReelFrame => ({ id: `photo-${id}`, shape: 'portrait', title, line });
const l = (id: string, title: string, line: string): ReelFrame => ({ id: `photo-${id}`, shape: 'landscape', title, line });

// A roll shot between the skatepark and the beach, three frames at a time.
const FRAMES: ReelFrame[] = [
  l('1520045892732-304bc3ac5d8e', 'Rail Grind', 'Rooftop ledge, golden hour'),
  p('1723236900134-63561e5832b3', 'Kickflip', 'Blue sky over Marlow Park'),
  p('1597019558926-3eef445fdf60', 'Bowl at Dusk', 'Concrete bowls, Dune Lane'),
  l('1498330177096-689e3fb901ca', 'The Barrel', 'Reef break, six feet'),
  l('1529410804717-979530809e26', 'Spray', 'Offshore wind, Kelp Point'),
  l('1468931467769-06a09c69aad3', 'Long Line', 'North swell at noon'),
  p('1547447134-cd3f5c716030', 'Yellow Wall', 'A deck painted by hand'),
  p('1517124792960-0f4701b9bd20', 'Deep End', 'Pool session, Harlan Street'),
  p('1569135579442-d37b7a0ea74e', 'Silhouette', 'Last light, Brightwater'),
  l('1454123253751-1fe2b9e0c10d', 'The Lip', 'Shorebreak at low tide'),
  l('1461503312594-019be44dd599', 'Green Room', 'Cloud break, early'),
  l('1471079688237-3ac9a55f1d6f', 'Glass', 'Inside the tube'),
  p('1499083773823-5000fa2b23e4', 'Drop In', 'Indoor ramp, Unit 9'),
  p('1597769555495-c54a15cd8c3f', 'Studio Air', 'Kickflip on white'),
  p('1601247309037-a3e434b07906', 'Teal Deck', 'Flip on the boardwalk'),
  l('1550071510-35e941e4888e', 'The Wall', 'One rider, Kelp Point'),
  l('1486869801134-25b6bc85fc0d', 'Gold Water', 'Backlit, at dusk'),
  l('1556316782-1ec9b261c156', 'Blue Peak', 'Point break, west side'),
  p('1591311337241-cecfd26f1da1', 'Crosswalk', 'Ollie, midtown'),
  l('1536318431364-5cc762cfc8ec', 'Wheels Up', 'Rooftop, after the rain'),
  p('1503417680882-163c1609fd2f', 'Handrail', 'Noon at Pier 4'),
  l('1506988038666-496173f39dea', 'Grey Morning', 'Overcast, four feet'),
  l('1613450861177-c281f178b920', 'Three Up', 'Paddling out at sunset'),
  l('1457234068269-b58092525794', 'Inside', 'Barrel, Little Harbor'),
];

const NIGHT = '#0b0a09';
const BASE = '#1a1512';
const CREAM = '#efe6d8';
const ORANGE = '#ff7a1f';
const PAPER = '#f5f2eb';
const PRINT_INK = '#1d1a17';
// The light table under the strip, brightest under the loupe.
const GLOW = '#ffe6c4';

// The strip crosses the screen at ANGLE rad, its middle STRIP_Y of the way down. A frame is
// FRAME_W of the screen wide at 3:2, frames are FRAME_GAP pt apart, and the rails above and below
// are RAIL pt deep: EDGE pt of film, a perforation PERF_H pt tall, then the edge printing.
const ANGLE = -0.13;
const STRIP_Y = 0.5;
const FRAME_W = 0.58;
const FRAME_GAP = 14;
const RAIL = 34;
const EDGE = 4;
const PERF_H = 11;
const PERFS = 8;
// The frame under the loupe stands GROW larger, its neighbours pushed apart to make room.
const GROW = 0.06;
const LOUPE = 0.66;
// Far frames darken by FADE_STEP per frame from the middle, up to FADE_MAX.
const FADE_STEP = 0.26;
const FADE_MAX = 0.62;

// Speed, in frames per second, smoothed over ~1/SMOOTH s: it shears the strip by SHEAR per frame/s,
// bows its ends up by BOW pt per frame/s, and blurs the frames along it by BLUR px per frame/s.
const SMOOTH = 12;
const SHEAR = 0.012;
const SHEAR_MAX = 0.16;
const BOW = 1.4;
const BOW_MAX = 18;
const BLUR = 0.95;
const BLUR_MAX = 11;

// Dragging winds the strip with the finger; on release it is thrown PROJECT s ahead at its speed
// (never more than THROW_MAX frames) and springs onto the nearest frame.
const PROJECT = 0.32;
const THROW_MAX = 9;
const SNAP_SPRING = { duration: 700, dampingRatio: 0.86 } as const;

// The print: PRINT_MARGIN pt from the screen's sides, a BORDER pt white border and CAPTION pt of
// paper under the photo for its caption, turned PRINT_TILT rad, lifted on LIFT_SPRING; the strip
// behind dims by DIM and shrinks by SHRINK.
const PRINT_MARGIN = 26;
const BORDER = 12;
const CAPTION = 66;
const PRINT_TILT = 0.03;
const PRINT_Y = 0.47;
const LIFT_SPRING = { duration: 620, dampingRatio: 0.74 } as const;
const DROP_SPRING = { duration: 520, dampingRatio: 0.92 } as const;
const DIM = 0.66;
const SHRINK = 0.04;

// autoPlay: two runs of WIND frames, each taking WIND_TIME s on an ease-in-out, each followed by a
// print that rises and drops back; LOOP s in all, so the roll comes round to where it began.
const LOOP = 14;
const WIND = 12;
const WIND_TIME = 2.2;
const RUNS = [0.5, 7.0];
const LIFTS = [3.2, 9.7];
const DROPS = [5.6, 12.1];
const USER_HOLD = 3;
const IMAGE_FADE = 0.4;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
const MONO = process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace';

type Loaded = { image: SkImage; at: number };
type Fonts = { code: SkFont; label: SkFont; title: SkFont; line: SkFont; stamp: SkFont };
/** Where a frame stands on screen: its middle, turn, scale and shear. */
type Pose = { x: number; y: number; angle: number; scale: number; shear: number };

function clamp(value: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, value));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** `value` wrapped into -n/2 .. n/2, so the roll has no ends. */
function wrap(value: number, n: number) {
  'worklet';
  return value - n * Math.floor(value / n + 0.5);
}

function easeInOut(t: number) {
  'worklet';
  const c = clamp(t, 0, 1);
  return c < 0.5 ? 16 * c * c * c * c * c : 1 - Math.pow(-2 * c + 2, 5) / 2;
}

/** How far the script has wound the strip at `t`, in frames. */
function scriptWound(t: number) {
  'worklet';
  let wound = 0;
  for (const start of RUNS) wound += WIND * easeInOut((t - start) / WIND_TIME);
  return wound;
}

/** Whether the looping clock, moving from `before` to `t`, passed `at`. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return (before < at && t >= at) || (t < before && (at > before || at <= t));
}

/** A frame `d` frames from the middle of the strip: where it stands, along a strip that bows with speed. */
function framePose(d: number, pitch: number, speed: number, cx: number, cy: number, motion: number): Pose {
  'worklet';
  const near = Math.max(0, 1 - Math.abs(d));
  const scale = 1 + GROW * near;
  const push = (GROW * pitch) / 2;
  const along = d * pitch + Math.sign(d) * push * Math.min(Math.abs(d), 1);
  // The ends lift as the strip runs, bending it into a shallow smile.
  const bow = clamp(Math.abs(speed) * BOW, 0, BOW_MAX) * motion;
  const reach = pitch * 1.6;
  const across = -bow * (along / reach) * (along / reach);
  const tangent = Math.atan((-2 * bow * along) / (reach * reach));
  const c = Math.cos(ANGLE);
  const s = Math.sin(ANGLE);
  return {
    x: cx + along * c - across * s,
    y: cy + along * s + across * c,
    angle: ANGLE + tangent,
    scale,
    shear: clamp(-speed * SHEAR, -SHEAR_MAX, SHEAR_MAX) * motion,
  };
}

/** The frames near enough to the middle to be on screen, the farthest first, so the middle one is drawn on top. */
function farthestFirst(o: number, n: number) {
  'worklet';
  const order: number[] = [];
  for (let i = 0; i < n; i += 1) if (Math.abs(wrap(i - o, n)) < 3.2) order.push(i);
  // Insertion sort: a handful of frames.
  for (let a = 1; a < order.length; a += 1) {
    const item = order[a];
    const d = Math.abs(wrap(item - o, n));
    let b = a - 1;
    while (b >= 0 && Math.abs(wrap(order[b] - o, n)) < d) {
      order[b + 1] = order[b];
      b -= 1;
    }
    order[b + 1] = item;
  }
  return order;
}

/** A strip segment: film base with its perforations cut out, a frame wide, centred on the origin. */
function makeSegment(pitch: number, frameH: number) {
  const path = Skia.PathBuilder.Make();
  const half = pitch / 2 + 1;
  const height = frameH + RAIL * 2;
  path.addRect(Skia.XYWHRect(-half, -height / 2, half * 2, height));
  const perfW = (pitch / PERFS) * 0.5;
  for (let k = 0; k < PERFS; k += 1) {
    const x = -pitch / 2 + (k + 0.25) * (pitch / PERFS);
    for (const y of [-height / 2 + EDGE, height / 2 - EDGE - PERF_H]) {
      path.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, perfW, PERF_H), 2, 2));
    }
  }
  path.setFillType(FillType.EvenOdd);
  return path.build();
}

/** A photo cropped to fill `rect`, faded in once it has loaded. */
function drawPhoto(canvas: SkCanvas, loaded: Loaded | null, x: number, y: number, w: number, h: number, now: number, paint: SkPaint, alpha: number) {
  'worklet';
  if (!loaded) return;
  const image = loaded.image;
  const iw = image.width();
  const ih = image.height();
  const fit = Math.max(w / iw, h / ih);
  const sw = w / fit;
  const sh = h / fit;
  paint.setAlphaf(alpha * clamp((now - loaded.at) / IMAGE_FADE, 0, 1));
  canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.Linear, paint);
}

/** One frame of film: the base and its perforations, the photo, and the orange edge printing. */
function drawSegment(
  canvas: SkCanvas,
  segment: SkPath,
  index: number,
  pose: Pose,
  frameW: number,
  frameH: number,
  pitch: number,
  loaded: Loaded | null,
  empty: boolean,
  shade: number,
  blur: number,
  now: number,
  fonts: Fonts,
  roll: string,
) {
  'worklet';
  canvas.save();
  canvas.translate(pose.x, pose.y);
  canvas.rotate((pose.angle * 180) / Math.PI, 0, 0);
  canvas.scale(pose.scale, pose.scale);
  canvas.skew(pose.shear, 0);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(BASE));
  canvas.drawPath(segment, paint);

  // The photo, blurred along the strip at speed; an empty window once it has been lifted out.
  const fx = -frameW / 2;
  const fy = -frameH / 2;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(fx, fy, frameW, frameH), ClipOp.Intersect, true);
  paint.setColor(Skia.Color('#0e0b09'));
  canvas.drawRect(Skia.XYWHRect(fx, fy, frameW, frameH), paint);
  if (!empty) {
    const photo = Skia.Paint();
    photo.setAntiAlias(true);
    if (blur > 0.4) photo.setImageFilter(Skia.ImageFilter.MakeBlur(blur, 0, TileMode.Clamp, null));
    drawPhoto(canvas, loaded, fx, fy, frameW, frameH, now, photo, 1);
  } else {
    paint.setColor(Skia.Color('#2a221c'));
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(1);
    canvas.drawRect(Skia.XYWHRect(fx + 6, fy + 6, frameW - 12, frameH - 12), paint);
    paint.setStyle(PaintStyle.Fill);
  }
  canvas.restore();

  // Edge printing: the frame number by the perforations above, the stock and roll below.
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(ORANGE));
  ink.setAlphaf(0.85);
  const number = String(index + 1);
  const top = fy - RAIL + EDGE + PERF_H + 11;
  const bottom = fy + frameH + RAIL - EDGE - PERF_H - 4;
  canvas.drawText(`${number}`, fx + 4, top, ink, fonts.code);
  canvas.drawText(`▸ ${number}A`, fx + frameW * 0.5, top, ink, fonts.code);
  ink.setAlphaf(0.6);
  canvas.drawText(`NC 400T  5207  ${roll}`, fx + 4, bottom, ink, fonts.code);
  canvas.drawText(`${number}A`, fx + frameW - 22, bottom, ink, fonts.code);

  // Frames away from the loupe fall into the dark.
  if (shade > 0.005) {
    paint.setColor(Skia.Color(NIGHT));
    paint.setAlphaf(shade);
    canvas.drawRect(Skia.XYWHRect(-pitch / 2 - 1, -frameH / 2 - RAIL, pitch + 2, frameH + RAIL * 2), paint);
  }
  canvas.restore();
}

/** A letter's advance; Skia measures a lone space as nothing, so it is taken from between two letters. */
function advance(font: SkFont, letter: string) {
  'worklet';
  if (letter !== ' ') return font.measureText(letter).width;
  return font.measureText('a a').width - font.measureText('aa').width;
}

function trackedWidth(font: SkFont, text: string, tracking: number) {
  'worklet';
  let width = 0;
  for (const letter of text) width += advance(font, letter) + tracking;
  return width - tracking;
}

function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: SkPaint) {
  'worklet';
  let at = x;
  for (const letter of text) {
    canvas.drawText(letter, at, y, paint, font);
    at += advance(font, letter) + tracking;
  }
}

/**
 * A strip of 35mm film crossing a dark light table at a slant: drawn perforations, frame numbers
 * and orange edge printing, a loupe of light in the middle where the frame under it stands a
 * little larger. Dragging winds the strip with momentum; at speed it shears and bows and the
 * frames blur along it. Tapping the centred frame lifts it out into a white-bordered print with a
 * caption while the strip dims behind; a tap drops it back.
 */
export function FilmStripReel({ frames = FRAMES, autoPlay = false, roll = '07', onPrint }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const motion = reducedMotion ? 0 : 1;
  const n = frames.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo<Fonts>(
    () => ({
      code: matchFont({ fontFamily: MONO, fontSize: 8, fontWeight: '600' }),
      label: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '500' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: 18, fontWeight: '500' }),
      line: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '400' }),
      stamp: matchFont({ fontFamily: MONO, fontSize: 11, fontWeight: '700' }),
    }),
    [],
  );
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const frameW = width * FRAME_W;
  const frameH = (frameW * 2) / 3;
  const pitch = frameW + FRAME_GAP;
  const segment = useMemo(() => makeSegment(pitch || 1, frameH || 1), [pitch, frameH]);

  const stage = useSharedValue({ width: 0, height: 0 });
  const now = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[]>(frames.map(() => null));
  // How far the roll is wound, in frames (frame i sits in the middle when it equals i), and its
  // smoothed speed in frames per second.
  const offset = useSharedValue(0);
  const lastOffset = useSharedValue(0);
  const speed = useSharedValue(0);
  // The print: how far it is lifted (0 in the strip, 1 a print), and which frame it is.
  const lift = useSharedValue(0);
  const lifted = useSharedValue(-1);
  const printing = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // The self-playing loop: its clock, and the offset it winds from.
  const looping = useSharedValue(auto);
  const clock = useSharedValue(0);
  const base = useSharedValue(0);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    let alive = true;
    loaded.set(frames.map(() => null));
    frames.forEach((frame, i) => {
      Skia.Data.fromURI(unsplash(frame))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          loaded.modify((list) => {
            'worklet';
            const next = list.slice();
            next[i] = { image, at: now.get() };
            return next;
          });
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [frames, loaded, now]);

  const haptic = (kind: 'snap' | 'lift' | 'drop') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'snap') Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 'lift' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const printed = (index: number) => onPrint?.(index);

  /** The frame in the middle of the strip. */
  const centred = () => {
    'worklet';
    return ((Math.round(offset.get()) % n) + n) % n;
  };

  const raise = (user: boolean) => {
    'worklet';
    const index = centred();
    lifted.set(index);
    printing.set(true);
    lift.set(reducedMotion ? withTiming(1, { duration: 240 }) : withSpring(1, LIFT_SPRING));
    if (user) scheduleOnRN(haptic, 'lift');
    scheduleOnRN(printed, index);
  };

  const drop = (user: boolean) => {
    'worklet';
    printing.set(false);
    lift.set(reducedMotion ? withTiming(0, { duration: 200 }) : withSpring(0, DROP_SPRING));
    if (user) scheduleOnRN(haptic, 'drop');
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || stage.get().width === 0) return;
    now.set(now.get() + dt);

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        // Pick the loop up where the strip stands: at rest, or holding a print.
        const at = Math.round(offset.get());
        const holding = printing.get();
        clock.set(holding ? 4.4 : 0);
        base.set(holding ? at - WIND : at);
      }
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      // The loop winds two dozen frames in all, so it ends where it began, a whole roll on.
      if (t < before) base.set(base.get() + WIND * RUNS.length);
      offset.set(base.get() + scriptWound(t));
      for (const at of LIFTS) if (crossed(before, t, at)) raise(false);
      for (const at of DROPS) if (crossed(before, t, at)) drop(false);
    }

    // Speed, wrapped so the roll coming round does not count as a jump.
    const o = offset.get();
    const moved = wrap(o - lastOffset.get(), n) / dt;
    lastOffset.set(o);
    speed.set(mix(speed.get(), moved, 1 - Math.exp(-SMOOTH * dt)));
  });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      touched();
      if (printing.get()) drop(true);
      cancelAnimation(offset);
      dragFrom.set(offset.get());
    })
    .onUpdate((event) => {
      // Only the drag along the strip winds it.
      const along = event.translationX * Math.cos(ANGLE) + event.translationY * Math.sin(ANGLE);
      offset.set(dragFrom.get() - along / pitch);
    })
    .onEnd((event) => {
      touched();
      const velocity = -(event.velocityX * Math.cos(ANGLE) + event.velocityY * Math.sin(ANGLE)) / pitch;
      const from = offset.get();
      const to = Math.round(from + clamp(velocity * PROJECT, -THROW_MAX, THROW_MAX));
      offset.set(withSpring(to, { ...SNAP_SPRING, velocity }));
      scheduleOnRN(haptic, 'snap');
    });

  const tap = Gesture.Tap()
    .maxDuration(320)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (printing.get()) {
        drop(true);
        return;
      }
      // Where the tap falls along and across the strip.
      const { width: w, height: h } = stage.get();
      const dx = event.x - w / 2;
      const dy = event.y - h * STRIP_Y;
      const along = dx * Math.cos(ANGLE) + dy * Math.sin(ANGLE);
      const across = -dx * Math.sin(ANGLE) + dy * Math.cos(ANGLE);
      if (Math.abs(across) > frameH / 2 + RAIL) return;
      const steps = Math.round(along / pitch);
      if (steps === 0 && Math.abs(offset.get() - Math.round(offset.get())) < 0.08) {
        raise(true);
      } else {
        // A frame off to the side winds to the middle.
        cancelAnimation(offset);
        offset.set(withSpring(Math.round(offset.get()) + steps, SNAP_SPRING));
        scheduleOnRN(haptic, 'snap');
      }
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    // A zero-sized layout would ask Skia for a radial gradient of radius 0 below.
    if (!size || width <= 0 || height <= 0 || n === 0) return recorder.finishRecordingAsPicture();
    const t = now.get();
    const images = loaded.get();
    const o = offset.get();
    const v = speed.get();
    const up = lift.get();
    const lifting = lifted.get();
    const cx = width / 2;
    const cy = height * STRIP_Y;

    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(NIGHT));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);

    // The light table, glowing under the loupe; it shows through the perforations.
    const glow = Skia.Paint();
    const warm = Skia.Color(GLOW);
    glow.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx, y: cy },
        width * 0.9,
        [Float32Array.of(warm[0], warm[1], warm[2], 0.5), Float32Array.of(warm[0], warm[1], warm[2], 0.12), Float32Array.of(warm[0], warm[1], warm[2], 0)],
        [0, 0.4, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), glow);

    // The strip, shrinking back a little while a print is up; the middle frame drawn last.
    canvas.save();
    const back = 1 - SHRINK * clamp(up, 0, 1) * motion;
    canvas.translate(cx, cy);
    canvas.scale(back, back);
    canvas.translate(-cx, -cy);
    const blur = clamp(Math.abs(v) * BLUR, 0, BLUR_MAX) * motion;
    for (const i of farthestFirst(o, n)) {
      const d = wrap(i - o, n);
      const pose = framePose(d, pitch, v, cx, cy, motion);
      const shade = Math.min(FADE_MAX, Math.max(0, Math.abs(d) - 0.15) * FADE_STEP);
      drawSegment(canvas, segment, i, pose, frameW, frameH, pitch, images[i], i === lifting && up > 0.001, shade, blur, t, fonts, roll);
    }
    canvas.restore();

    // The loupe: a ring of light over the middle frame.
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(1.25);
    ring.setColor(Skia.Color(CREAM));
    ring.setAlphaf(0.22 * (1 - clamp(up, 0, 1)));
    canvas.drawCircle(cx, cy, frameW * LOUPE, ring);
    ring.setAlphaf(0.08 * (1 - clamp(up, 0, 1)));
    canvas.drawCircle(cx, cy, frameW * LOUPE + 6, ring);

    // The room dims behind a print.
    if (up > 0.001) {
      paint.setColor(Skia.Color(NIGHT));
      paint.setAlphaf(DIM * clamp(up, 0, 1));
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
    }

    // The print, flying from its window in the strip to the middle of the screen.
    if (lifting >= 0 && up > 0.001) {
      const from = framePose(wrap(lifting - o, n), pitch, v, cx, cy, motion);
      const k = reducedMotion ? 1 : up;
      const printW = width - PRINT_MARGIN * 2;
      const photoW = printW - BORDER * 2;
      const photoH = (photoW * 2) / 3;
      const s = mix(from.scale * (frameW / photoW), 1, k);
      const border = BORDER * clamp(up, 0, 1.2);
      const caption = CAPTION * clamp(up, 0, 1.2);
      const toY = height * PRINT_Y;
      const x = mix(from.x, cx, k);
      const y = mix(from.y, toY, k);
      const angle = mix(from.angle, PRINT_TILT, k);
      const alpha = reducedMotion ? clamp(up, 0, 1) : 1;
      canvas.save();
      canvas.translate(x, y);
      canvas.rotate((angle * 180) / Math.PI, 0, 0);
      canvas.scale(s, s);
      const left = -photoW / 2 - border;
      const top = -photoH / 2 - border;
      const paperW = photoW + border * 2;
      const paperH = photoH + border + caption;
      // A soft shadow under the paper.
      const shadow = Skia.Paint();
      shadow.setColor(Skia.Color('#000000'));
      shadow.setAlphaf(0.5 * clamp(up, 0, 1) * alpha);
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
      canvas.drawRect(Skia.XYWHRect(left + 4, top + 18 * clamp(up, 0, 1), paperW - 8, paperH), shadow);
      paint.setColor(Skia.Color(PAPER));
      paint.setAlphaf(alpha);
      canvas.drawRect(Skia.XYWHRect(left, top, paperW, paperH), paint);
      const photoPaint = Skia.Paint();
      photoPaint.setAntiAlias(true);
      drawPhoto(canvas, images[lifting], -photoW / 2, -photoH / 2, photoW, photoH, t, photoPaint, alpha);
      // The date stamp, burnt in orange in the photo's corner, and the caption on the paper.
      const words = clamp((up - 0.55) / 0.4, 0, 1) * alpha;
      if (words > 0.01) {
        const ink = Skia.Paint();
        ink.setAntiAlias(true);
        ink.setColor(Skia.Color(ORANGE));
        ink.setAlphaf(0.9 * words);
        const stamp = `'98 7 ${String((lifting % 28) + 1).padStart(2, ' ')}`;
        canvas.drawText(stamp, photoW / 2 - 12 - fonts.stamp.measureText(stamp).width, photoH / 2 - 12, ink, fonts.stamp);
        ink.setColor(Skia.Color(PRINT_INK));
        ink.setAlphaf(words);
        const item = frames[lifting];
        if (item) {
          canvas.drawText(item.title, left + BORDER + 2, photoH / 2 + 30, ink, fonts.title);
          ink.setAlphaf(0.55 * words);
          canvas.drawText(item.line, left + BORDER + 2, photoH / 2 + 48, ink, fonts.line);
        }
        const number = `${lifting + 1}A / ${roll}`;
        drawTracked(canvas, number, fonts.label, photoW / 2 - trackedWidth(fonts.label, number, 1) - 2, photoH / 2 + 30, 1, ink);
      }
      canvas.restore();
    }

    // Corners: the roll, the frame count, and how to use it.
    const label = Skia.Paint();
    label.setAntiAlias(true);
    label.setColor(Skia.Color(CREAM));
    const topY = insets.top + 24;
    label.setAlphaf(0.75);
    drawTracked(canvas, `ROLL ${roll} — COASTLINE`, fonts.label, 20, topY, 1.4, label);
    const count = `${String((((Math.round(o) % n) + n) % n) + 1).padStart(2, '0')}/${n}`;
    label.setAlphaf(0.5);
    drawTracked(canvas, count, fonts.label, width - 20 - trackedWidth(fonts.label, count, 1.4), topY, 1.4, label);
    const hint = up > 0.5 ? 'TAP TO RETURN IT TO THE ROLL' : 'DRAG TO WIND · TAP THE FRAME TO PRINT';
    label.setAlphaf(0.38);
    drawTracked(canvas, hint, fonts.label, (width - trackedWidth(fonts.label, hint, 1.4)) / 2, height - insets.bottom - 22, 1.4, label);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Film strip, roll ${roll}, ${n} frames`}
        accessibilityHint="Drag to wind the film; tap the frame in the middle to lift it into a print"
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
          stage.set({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: NIGHT,
  },
});
