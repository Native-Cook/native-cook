import {
  Canvas,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Picture,
  type SkCanvas,
  type SkImage,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  createPicture,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  FadeIn,
  FadeOut,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type ShatterPhoto = {
  /** An Unsplash photo id (`photo-…`), portrait. */
  id: string;
  title: string;
  line: string;
};

type Props = {
  photos?: ShatterPhoto[];
  /** Breaks to the next photo every couple of seconds, and once mends back, on a LOOP-second loop. */
  autoPlay?: boolean;
  /** Called with the index of the photo that ends up in front. */
  onChange?: (index: number) => void;
};

const PHOTOS: ShatterPhoto[] = [
  { id: 'photo-1711560705654-325ba859c38b', title: 'Ember Nursery', line: 'A cloud of newborn stars, 7,500 light-years out' },
  { id: 'photo-1543007168-5fa9b3c5f5fb', title: 'Sea Nettle', line: 'Drifting twelve metres down, open Pacific' },
  { id: 'photo-1759179075678-d65678f1050e', title: 'Mercury Fold', line: 'Liquid chrome, study no. 4' },
  { id: 'photo-1628944682084-831f35256163', title: 'Mauve Stinger', line: 'Night swarm off the Atlantic shelf' },
];

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const PHOTO_PX = 1000;

function photoUrl(id: string) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${PHOTO_PX}&q=80`;
}

// The fracture: RAYS cracks run out from the point of impact, crossed by rings at RINGS pt
// (jittered), so the glass breaks into small shards near the impact and large ones far from it.
const RAYS = 13;
const RINGS = [0, 20, 52, 104, 180, 285, 425, 610, 850, 1150];
const RAY_JITTER = 0.55;
const RING_JITTER = 0.3;
// Rings from this one outwards split their shards in two along a diagonal.
const SPLIT_FROM = 5;

// Timeline of a break, in seconds: the cracks race out over CRACK_TIME, the glass holds until
// FALL_AT, then the shards fall; by END every shard has left the screen or faded.
const CRACK_TIME = 0.14;
const FALL_AT = 0.26;
const END = 1.5;
const FADE_TAIL = 0.3;
// Shards: gravity in pt/s², the outward push (strongest at the impact, falling off over
// PUSH_REACH pt), and each shard's start delayed by its distance from the impact.
const GRAVITY = 2600;
const PUSH_NEAR = 560;
const PUSH_FAR = 140;
const PUSH_REACH = 260;
const DELAY_PER_PT = 0.00032;
// The next photo waits behind: it starts this much larger and darker and settles as it shows.
const REVEAL_ZOOM = 0.06;
const REVEAL_DIM = 0.5;
// The whole pane shakes on impact: a damped oscillation of SHAKE pt.
const SHAKE = 5;

// Mending (swiping back): the previous photo's shards rise from where they would be REVERSE_FROM
// seconds into a fall and fit back together, following the finger across MEND_SPAN of the width.
const REVERSE_FROM = 0.78;
const MEND_SPAN = 0.75;
const MEND_TIME = 1000;
const MEND_EASE = Easing.bezier(0.45, 0, 0.2, 1);

// autoPlay (seconds): three breaks, one mend back, two more breaks: four photos forward, so the
// loop ends on the photo it started from.
const LOOP = 13.2;
type Cue = { at: number; kind: 'break' | 'mend'; x: number; y: number };
const CUES: Cue[] = [
  { at: 0.6, kind: 'break', x: 0.62, y: 0.38 },
  { at: 2.8, kind: 'break', x: 0.3, y: 0.56 },
  { at: 5.0, kind: 'break', x: 0.55, y: 0.3 },
  { at: 7.2, kind: 'mend', x: 0.5, y: 0.45 },
  { at: 8.9, kind: 'break', x: 0.42, y: 0.42 },
  { at: 11.0, kind: 'break', x: 0.68, y: 0.6 },
];
const USER_HOLD = 4;

type Shatter = {
  x: number;
  y: number;
  /** Each crack as a flat list of points from the impact outwards, and their distances. */
  rays: number[][];
  reach: number[][];
  /** Each shard as a flat list of points, and how it flies. */
  polys: number[][];
  cx: number[];
  cy: number[];
  vx: number[];
  vy: number[];
  delay: number[];
  spin: number[];
  flip: number[];
  grow: number[];
};

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function smooth(x: number) {
  'worklet';
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
}

/** A polygon (flat points) cut to the rectangle 0…w × 0…h (Sutherland–Hodgman). */
function clipToRect(points: number[], w: number, h: number) {
  'worklet';
  let out = points;
  for (let edge = 0; edge < 4; edge += 1) {
    const input = out;
    out = [];
    const n = input.length / 2;
    if (n === 0) break;
    for (let i = 0; i < n; i += 1) {
      const ax = input[i * 2];
      const ay = input[i * 2 + 1];
      const bx = input[((i + 1) % n) * 2];
      const by = input[((i + 1) % n) * 2 + 1];
      // Signed distance inside the edge: left x ≥ 0, right x ≤ w, top y ≥ 0, bottom y ≤ h.
      const da = edge === 0 ? ax : edge === 1 ? w - ax : edge === 2 ? ay : h - ay;
      const db = edge === 0 ? bx : edge === 1 ? w - bx : edge === 2 ? by : h - by;
      if (da >= 0) out.push(ax, ay);
      if (da >= 0 !== db >= 0) {
        const t = da / (da - db);
        out.push(ax + (bx - ax) * t, ay + (by - ay) * t);
      }
    }
  }
  return out;
}

/** Breaks a w × h pane from (px, py): its cracks and its shards, the same for the same seed. */
function buildShatter(px: number, py: number, w: number, h: number, seed: number): Shatter {
  'worklet';
  let state = (seed * 2654435761) % 4294967296;
  function random() {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  }
  const turn = random() * Math.PI * 2;
  const verts: number[][] = [];
  const rays: number[][] = [];
  const reach: number[][] = [];
  for (let i = 0; i < RAYS; i += 1) {
    const angle = turn + ((i + (random() - 0.5) * RAY_JITTER) * Math.PI * 2) / RAYS;
    const ring: number[] = [];
    const dist: number[] = [];
    for (let j = 0; j < RINGS.length; j += 1) {
      const r = j === 0 ? 0 : RINGS[j] * (1 + (random() - 0.5) * RING_JITTER);
      const a = angle + (random() - 0.5) * 0.14;
      ring.push(px + Math.cos(a) * r, py + Math.sin(a) * r);
      dist.push(r);
    }
    verts.push(ring);
    rays.push(ring);
    reach.push(dist);
  }
  const shatter: Shatter = {
    x: px,
    y: py,
    rays,
    reach,
    polys: [],
    cx: [],
    cy: [],
    vx: [],
    vy: [],
    delay: [],
    spin: [],
    flip: [],
    grow: [],
  };
  const add = (pts: number[]) => {
    const poly = clipToRect(pts, w, h);
    const n = poly.length / 2;
    if (n < 3) return;
    let cx = 0;
    let cy = 0;
    for (let k = 0; k < n; k += 1) {
      cx += poly[k * 2];
      cy += poly[k * 2 + 1];
    }
    cx /= n;
    cy /= n;
    const dx = cx - px;
    const dy = cy - py;
    const d = Math.max(1, Math.hypot(dx, dy));
    const near = Math.exp(-d / PUSH_REACH);
    const push = (PUSH_FAR + PUSH_NEAR * near) * (0.7 + 0.6 * random());
    shatter.polys.push(poly);
    shatter.cx.push(cx);
    shatter.cy.push(cy);
    shatter.vx.push((dx / d) * push + (random() - 0.5) * 90);
    shatter.vy.push((dy / d) * push * 0.6 - 180 * random());
    shatter.delay.push(d * DELAY_PER_PT + random() * 0.05);
    shatter.spin.push((random() - 0.5) * 2 * (2.5 + 5 * near));
    shatter.flip.push((random() - 0.5) * 2 * (3 + 7 * near));
    shatter.grow.push(0.08 + 0.3 * near * random());
  };
  for (let j = 0; j < RINGS.length - 1; j += 1) {
    for (let i = 0; i < RAYS; i += 1) {
      const a = verts[i];
      const b = verts[(i + 1) % RAYS];
      if (j === 0) {
        add([px, py, a[2], a[3], b[2], b[3]]);
      } else if (j >= SPLIT_FROM) {
        const p = [a[j * 2], a[j * 2 + 1], a[j * 2 + 2], a[j * 2 + 3], b[j * 2 + 2], b[j * 2 + 3], b[j * 2], b[j * 2 + 1]];
        if (random() > 0.5) {
          add([p[0], p[1], p[2], p[3], p[4], p[5]]);
          add([p[0], p[1], p[4], p[5], p[6], p[7]]);
        } else {
          add([p[0], p[1], p[2], p[3], p[6], p[7]]);
          add([p[2], p[3], p[4], p[5], p[6], p[7]]);
        }
      } else {
        add([a[j * 2], a[j * 2 + 1], a[j * 2 + 2], a[j * 2 + 3], b[j * 2 + 2], b[j * 2 + 3], b[j * 2], b[j * 2 + 1]]);
      }
    }
  }
  return shatter;
}

/** The rectangle an image fills to cover w × h, centred. */
function coverOf(image: SkImage, w: number, h: number) {
  'worklet';
  const iw = image.width();
  const ih = image.height();
  const s = Math.max(w / iw, h / ih);
  return { s, x: (w - iw * s) / 2, y: (h - ih * s) / 2, iw, ih };
}

function drawCover(canvas: SkCanvas, image: SkImage, w: number, h: number, zoom: number, alpha: number) {
  'worklet';
  const c = coverOf(image, w, h);
  const paint = Skia.Paint();
  paint.setAlphaf(alpha);
  const dw = c.iw * c.s * zoom;
  const dh = c.ih * c.s * zoom;
  canvas.drawImageRectOptions(
    image,
    Skia.XYWHRect(0, 0, c.iw, c.ih),
    Skia.XYWHRect((w - dw) / 2, (h - dh) / 2, dw, dh),
    FilterMode.Linear,
    MipmapMode.None,
    paint,
  );
}

/** The cracks, drawn out to `reachPt` from the impact. */
function drawCracks(canvas: SkCanvas, s: Shatter, reachPt: number, alpha: number) {
  'worklet';
  if (alpha <= 0 || reachPt <= 0) return;
  const glow = Skia.Paint();
  glow.setStyle(PaintStyle.Stroke);
  glow.setStrokeWidth(3.6);
  glow.setStrokeCap(StrokeCap.Round);
  glow.setStrokeJoin(StrokeJoin.Round);
  glow.setAntiAlias(true);
  glow.setColor(Skia.Color('white'));
  glow.setAlphaf(0.16 * alpha);
  const line = glow.copy();
  line.setStrokeWidth(1.1);
  line.setAlphaf(0.9 * alpha);
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < s.rays.length; i += 1) {
    const ray = s.rays[i];
    const dist = s.reach[i];
    builder.moveTo(ray[0], ray[1]);
    for (let j = 1; j < dist.length; j += 1) {
      if (dist[j - 1] >= reachPt) break;
      const t = Math.min(1, (reachPt - dist[j - 1]) / Math.max(1, dist[j] - dist[j - 1]));
      const x0 = ray[(j - 1) * 2];
      const y0 = ray[(j - 1) * 2 + 1];
      builder.lineTo(x0 + (ray[j * 2] - x0) * t, y0 + (ray[j * 2 + 1] - y0) * t);
    }
  }
  // The rings join neighbouring cracks once both have run past them.
  for (let i = 0; i < s.rays.length; i += 1) {
    const a = s.rays[i];
    const b = s.rays[(i + 1) % s.rays.length];
    for (let j = 1; j < s.reach[i].length - 2; j += 1) {
      if (s.reach[i][j] * 1.25 > reachPt || s.reach[(i + 1) % s.rays.length][j] * 1.25 > reachPt) break;
      builder.moveTo(a[j * 2], a[j * 2 + 1]);
      builder.lineTo(b[j * 2], b[j * 2 + 1]);
    }
  }
  const path = builder.build();
  canvas.drawPath(path, glow);
  canvas.drawPath(path, line);
  // A frosted bruise where the glass was struck.
  const bruise = Skia.Paint();
  bruise.setAntiAlias(true);
  bruise.setColor(Skia.Color('white'));
  bruise.setAlphaf(0.4 * alpha);
  canvas.drawCircle(s.x, s.y, 4 + 10 * clamp01(reachPt / 60), bruise);
}

/** The shards of `image` at `time` seconds into the fall. */
function drawShards(canvas: SkCanvas, s: Shatter, image: SkImage, w: number, h: number, time: number, alpha: number) {
  'worklet';
  const c = coverOf(image, w, h);
  const matrix = Skia.Matrix();
  matrix.translate(c.x, c.y);
  matrix.scale(c.s, c.s);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(image.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, matrix));
  fill.setAlphaf(alpha);
  const shade = Skia.Paint();
  shade.setAntiAlias(true);
  shade.setColor(Skia.Color('black'));
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.2);
  edge.setStrokeJoin(StrokeJoin.Round);
  edge.setColor(Skia.Color('white'));
  for (let k = 0; k < s.polys.length; k += 1) {
    const tt = Math.max(0, time - FALL_AT - s.delay[k]);
    const x = s.cx[k] + s.vx[k] * tt;
    const y = s.cy[k] + s.vy[k] * tt + 0.5 * GRAVITY * tt * tt;
    if (y > h + 260) continue;
    const spin = s.spin[k] * tt;
    const flip = s.flip[k] * tt;
    const facing = Math.cos(flip);
    const grow = 1 + s.grow[k] * Math.min(1, tt * 1.5);
    const poly = s.polys[k];
    const builder = Skia.PathBuilder.Make();
    builder.moveTo(poly[0], poly[1]);
    for (let p = 2; p < poly.length; p += 2) builder.lineTo(poly[p], poly[p + 1]);
    builder.close();
    const path = builder.build();
    canvas.save();
    canvas.translate(x, y);
    canvas.rotate((spin * 180) / Math.PI, 0, 0);
    // Turning about its own axis, a shard narrows to an edge and comes back mirrored.
    canvas.scale(grow * (Math.abs(facing) < 0.04 ? 0.04 * Math.sign(facing || 1) : facing), grow);
    canvas.translate(-s.cx[k], -s.cy[k]);
    canvas.drawPath(path, fill);
    // Darker the more it is turned away, as glass seen at a slant.
    shade.setAlphaf(alpha * (0.55 * (1 - Math.abs(facing)) + (facing < 0 ? 0.18 : 0) + 0.08 * Math.min(1, tt * 4)));
    canvas.drawPath(path, shade);
    // Light glints along the edges when the shard turns through the light.
    const glint = Math.pow(Math.abs(Math.sin(flip + spin * 0.5)), 6);
    edge.setAlphaf(alpha * (tt > 0 ? 0.28 + 0.7 * glint : 0.75));
    canvas.drawPath(path, edge);
    canvas.restore();
  }
}

/**
 * A full-screen photo behind glass. Tapping (or a hard swipe) cracks it from the touch point, and
 * the pane breaks into shards that fall and spin away with the light catching their edges,
 * showing the next photo underneath. Swiping back fits the previous photo's shards together.
 */
export function ShatterGlassTransition({ photos = PHOTOS, autoPlay = false, onChange }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const count = photos.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [images, setImages] = useState<(SkImage | null)[]>(() => photos.map(() => null));
  const [shown, setShown] = useState(0);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  // 0 at rest, 1 breaking the front photo, 2 mending the previous one over it.
  const mode = useSharedValue(0);
  const current = useSharedValue(0);
  // Seconds into a break; and 0 … 1 through a mend.
  const time = useSharedValue(0);
  const mend = useSharedValue(0);
  const shatter = useSharedValue<Shatter | null>(null);
  const seed = useSharedValue(1);
  const scrubbing = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
    clock.set(0);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  // Every photo decoded up front, so a break never waits for the next one.
  useEffect(() => {
    let alive = true;
    photos.forEach((photo, index) => {
      Skia.Data.fromURI(photoUrl(photo.id))
        .then((data) => {
          const image = Skia.Image.MakeImageFromEncoded(data);
          if (alive) setImages((list) => list.map((img, k) => (k === index ? image : img)));
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [photos]);

  const announce = (index: number) => {
    setShown(index);
    onChange?.(index);
  };
  const thud = (heavy: boolean) => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(heavy ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Breaks the front photo from (x, y). */
  const strike = (x: number, y: number, user: boolean) => {
    'worklet';
    if (mode.get() !== 0 || width === 0) return;
    seed.set(seed.get() + 1);
    shatter.set(buildShatter(x, y, width, height, seed.get()));
    mode.set(1);
    time.set(0);
    const next = (current.get() + 1) % count;
    scheduleOnRN(announce, next);
    if (user) scheduleOnRN(thud, true);
    time.set(
      withTiming(END, { duration: END * 1000, easing: Easing.linear }, (done) => {
        if (!done) return;
        current.set(next);
        mode.set(0);
        time.set(0);
      }),
    );
  };

  /** Starts mending the previous photo from shards broken at (x, y); `mend` drives it. */
  const beginMend = (x: number, y: number) => {
    'worklet';
    if (mode.get() !== 0 || width === 0) return false;
    seed.set(seed.get() + 1);
    shatter.set(buildShatter(x, y, width, height, seed.get()));
    mend.set(0);
    mode.set(2);
    return true;
  };

  const finishMend = (user: boolean) => {
    'worklet';
    const back = (current.get() - 1 + count) % count;
    current.set(back);
    mode.set(0);
    mend.set(0);
    scheduleOnRN(announce, back);
    if (user) scheduleOnRN(thud, false);
  };

  const settleMend = (to: 0 | 1, user: boolean, ms: number) => {
    'worklet';
    mend.set(
      withTiming(to, { duration: ms, easing: MEND_EASE }, (done) => {
        if (!done) return;
        if (to === 1) finishMend(user);
        else {
          mode.set(0);
          mend.set(0);
        }
      }),
    );
  };

  /** Ends a break or a mend in flight at once, so a new touch always gets a response. */
  const settleNow = () => {
    'worklet';
    const m = mode.get();
    if (m === 1) {
      cancelAnimation(time);
      current.set((current.get() + 1) % count);
      mode.set(0);
      time.set(0);
    } else if (m === 2 && !scrubbing.get()) {
      cancelAnimation(mend);
      if (mend.get() > 0.5) finishMend(false);
      else {
        mode.set(0);
        mend.set(0);
      }
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || scrubbing.get()) return;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const t = (before + dt) % LOOP;
    clock.set(t);
    for (let k = 0; k < CUES.length; k += 1) {
      const cue = CUES[k];
      const passed = (before < cue.at && t >= cue.at) || (t < before && (cue.at > before || cue.at <= t));
      if (!passed) continue;
      if (cue.kind === 'break') strike(cue.x * width, cue.y * height, false);
      else if (beginMend(cue.x * width, cue.y * height)) settleMend(1, false, MEND_TIME);
    }
  });

  const pan = Gesture.Pan()
    .activeOffsetX([-14, 14])
    .onBegin(() => {
      hold.set(USER_HOLD);
    })
    .onStart((event) => {
      hold.set(USER_HOLD);
      settleNow();
      // Swiping back (to the right) mends the previous photo under the finger.
      if (event.translationX > 0 && beginMend(event.x - event.translationX, event.y - event.translationY)) {
        scrubbing.set(true);
      }
    })
    .onUpdate((event) => {
      if (!scrubbing.get()) return;
      mend.set(clamp01(event.translationX / (width * MEND_SPAN)));
    })
    .onEnd((event) => {
      if (scrubbing.get()) {
        scrubbing.set(false);
        const projected = mend.get() + (event.velocityX / (width * MEND_SPAN)) * 0.2;
        const to = projected > 0.5 ? 1 : 0;
        const left = to === 1 ? 1 - mend.get() : mend.get();
        settleMend(to, true, Math.max(180, left * 700));
        return;
      }
      // A hard swipe forward breaks the glass where the finger first landed.
      if (event.velocityX < -700 || event.translationX < -width * 0.3) {
        strike(event.x - event.translationX, event.y - event.translationY, true);
      }
    })
    .onFinalize(() => {
      if (scrubbing.get()) {
        scrubbing.set(false);
        settleMend(0, true, 260);
      }
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      if (!success) return;
      hold.set(USER_HOLD);
      settleNow();
      strike(event.x, event.y, true);
    });

  const picture = useDerivedValue(() => {
    const w = width;
    const h = height;
    const m = mode.get();
    const n = images.length;
    const front = current.get();
    // A mend plays a break backwards from REVERSE_FROM seconds, on the previous photo.
    const t = m === 2 ? REVERSE_FROM * (1 - mend.get()) : time.get();
    const top = images[m === 2 ? (front - 1 + n) % n : front];
    const under = m === 0 ? null : images[m === 2 ? front : (front + 1) % n];
    const s = shatter.get();
    return createPicture(
      (canvas) => {
        canvas.drawColor(Skia.Color('#05060a'));
        if (w === 0) return;
        const reveal = smooth((t - FALL_AT) / (END - FALL_AT - 0.2));
        if (under) {
          drawCover(canvas, under, w, h, reduced ? 1 : 1 + REVEAL_ZOOM * (1 - reveal), 1);
          const dim = Skia.Paint();
          dim.setColor(Skia.Color('black'));
          dim.setAlphaf(REVEAL_DIM * (1 - reveal));
          canvas.drawRect(Skia.XYWHRect(0, 0, w, h), dim);
        }
        if (!top) return;
        if (m === 0 || s === null) {
          drawCover(canvas, top, w, h, 1, 1);
          return;
        }
        // The pane shakes once on impact.
        const shake = reduced ? 0 : SHAKE * Math.exp(-t * 16) * Math.sin(t * 70);
        canvas.save();
        canvas.translate(shake, shake * 0.4);
        const fade = 1 - smooth((t - (END - FADE_TAIL)) / FADE_TAIL);
        // While mending, the shards fade in as they come.
        const enter = m === 2 ? clamp01(mend.get() / 0.18) : 1;
        if (reduced || t < FALL_AT) {
          // Reduced motion: nothing falls; the cracked photo fades to the next one.
          const alpha = reduced ? 1 - smooth((t - FALL_AT) / 0.6) : 1;
          drawCover(canvas, top, w, h, 1, alpha * enter);
        } else {
          drawShards(canvas, s, top, w, h, t, fade * enter);
        }
        if (t < FALL_AT + (reduced ? 0.6 : 0)) {
          const reach = Math.hypot(w, h) * smooth(t / CRACK_TIME);
          drawCracks(canvas, s, reach, enter * (reduced ? 1 - smooth((t - FALL_AT) / 0.6) : 1));
        }
        canvas.restore();
      },
      { width: Math.max(1, w), height: Math.max(1, h) },
    );
  });

  const photo = photos[shown];

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${photo.title}. Tap to break the glass, swipe right to mend it.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>

        <LinearGradient
          pointerEvents="none"
          colors={['rgba(3,4,8,0.45)', 'rgba(3,4,8,0)']}
          style={[styles.topShade, { height: insets.top + 90 }]}
        />
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(3,4,8,0)', 'rgba(3,4,8,0.7)']}
          style={[styles.bottomShade, { height: insets.bottom + 220 }]}
        />

        <View pointerEvents="none" style={[styles.top, { top: insets.top + 14 }]}>
          <Text style={styles.eyebrow}>BEHIND GLASS</Text>
          <Text style={styles.hint}>Tap to break · swipe back to mend</Text>
        </View>

        <View pointerEvents="none" style={[styles.caption, { bottom: insets.bottom + 34 }]}>
          <Animated.View
            key={shown}
            style={styles.captionBody}
            entering={FadeIn.duration(420).delay(200)}
            exiting={FadeOut.duration(140)}
          >
            <Text style={styles.index}>
              {String(shown + 1).padStart(2, '0')}
              <Text style={styles.indexOf}> / {String(count).padStart(2, '0')}</Text>
            </Text>
            <Text style={styles.title}>{photo.title}</Text>
            <Text style={styles.line}>{photo.line}</Text>
          </Animated.View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#05060a',
  },
  topShade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  bottomShade: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  top: {
    position: 'absolute',
    left: 24,
    right: 24,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  eyebrow: {
    color: 'rgba(255,255,255,0.92)',
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2.4,
  },
  hint: {
    color: 'rgba(255,255,255,0.62)',
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '500',
  },
  caption: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 120,
  },
  captionBody: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  index: {
    color: '#ffffff',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.2,
    fontVariant: ['tabular-nums'],
  },
  indexOf: {
    color: 'rgba(255,255,255,0.5)',
  },
  title: {
    marginTop: 8,
    color: '#ffffff',
    fontFamily: FAMILY,
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: -0.8,
  },
  line: {
    marginTop: 4,
    color: 'rgba(255,255,255,0.72)',
    fontFamily: FAMILY,
    fontSize: 14,
    fontWeight: '500',
  },
});
