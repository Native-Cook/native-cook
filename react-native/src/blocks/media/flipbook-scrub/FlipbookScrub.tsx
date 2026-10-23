import {
  BlurStyle,
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  StrokeCap,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One leaf of the book: a photo printed on it, its caption and the chapter it belongs to. */
export type FlipbookPage = { id: string; title: string; chapter: string };

type Props = {
  /** The leaves, front to back. */
  pages?: FlipbookPage[];
  /** Turns a few leaves over one at a time, rests, turns them back one at a time and starts over. */
  autoPlay?: boolean;
  /** Called with a leaf's index when the book comes to rest on it by hand. */
  onPage?: (index: number) => void;
};

const PAGES: FlipbookPage[] = [
  { id: 'photo-1547153760-18fc86324498', title: 'Arabesque', chapter: 'The Dancers' },
  { id: 'photo-1511715282680-fbf93a50e721', title: 'Grand jeté', chapter: 'The Dancers' },
  { id: 'photo-1529229504105-4ea795dcbf59', title: 'Relevé', chapter: 'The Dancers' },
  { id: 'photo-1563775957383-f7b1abbe63ec', title: 'Pirouette', chapter: 'The Dancers' },
  { id: 'photo-1525426250015-4e6473cdbf17', title: 'Port de bras', chapter: 'The Dancers' },
  { id: 'photo-1531318701087-32c11653dd77', title: 'Attitude', chapter: 'The Dancers' },
  { id: 'photo-1611879531844-24b7ddf40b26', title: 'Développé', chapter: 'The Dancers' },
  { id: 'photo-1524330685423-3e1966445abe', title: 'Tour en l’air', chapter: 'The Dancers' },
  { id: 'photo-1690267286998-bae912638f40', title: 'Pas de chat', chapter: 'The Dancers' },
  { id: 'photo-1593078886626-aabc65c1d311', title: 'Fouetté', chapter: 'The Dancers' },
  { id: 'photo-1681152322586-7ddde43866a4', title: 'Plié', chapter: 'The Dancers' },
  { id: 'photo-1529154547048-4d1e71e6a760', title: 'Révérence', chapter: 'The Dancers' },
  { id: 'photo-1571008887538-b36bb32f4571', title: 'Set', chapter: 'The Runners' },
  { id: 'photo-1727094141271-9bea5bc8c757', title: 'Drive', chapter: 'The Runners' },
  { id: 'photo-1744060204728-f68e434a3edf', title: 'Stride', chapter: 'The Runners' },
  { id: 'photo-1480179087180-d9f0ec044897', title: 'Kick', chapter: 'The Runners' },
  { id: 'photo-1759674861540-afed9f86f94a', title: 'Cadence', chapter: 'The Runners' },
  { id: 'photo-1461896836934-ffe607ba8211', title: 'Lean', chapter: 'The Runners' },
  { id: 'photo-1581889470536-467bdbe30cd0', title: 'Pace', chapter: 'The Runners' },
  { id: 'photo-1594882645126-14020914d58d', title: 'Surge', chapter: 'The Runners' },
  { id: 'photo-1698671823406-035c77ff6fcd', title: 'Float', chapter: 'The Runners' },
  { id: 'photo-1728532483490-708f6562b738', title: 'Reach', chapter: 'The Runners' },
  { id: 'photo-1758922769578-68c5ba000d87', title: 'Push', chapter: 'The Runners' },
  { id: 'photo-1744706908605-ac30eb45f98c', title: 'Finish', chapter: 'The Runners' },
];

// Each photo is fetched at a little over twice the size it is printed, cropped to the print: 24
// prints stay near 60 MB decoded, so opening the book twice in a row stays well inside memory.
const photoUri = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=750&h=800&q=80`;

// The table, the book's paper and its binding.
const TABLE = '#17130f';
const TABLE_LIT = '#2c241d';
const PAPER = '#f2ece0';
const PAPER_BACK = '#f8f3ea';
const PAPER_EDGE = '#d9d0c0';
const INK = '#2b2620';
const TAPE = '#2a1f19';
const TAPE_SHEEN = '#4a382c';
const CHROME = '#efe6d8';

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const SERIF = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';

// The book, in pt: a leaf is the screen's width less SIDE a side, PAGE_ASPECT times as tall. It is
// bound along its top edge under a cloth tape TAPE_H deep, and the leaves under the top one show
// as a block THICK deep along its bottom edge. The photo is printed MARGIN in from the edges,
// with CAPTION_H under it for its caption.
const SIDE = 32;
const MAX_PAGE_W = 360;
const PAGE_ASPECT = 1.16;
const TAPE_H = 26;
const THICK = 18;
const MARGIN = 14;
const CAPTION_H = 34;
const CORNER = 3;
// The header above the book: the label line and the counter, from the top safe edge.
const LABEL_Y = 24;
const COUNTER_Y = 74;
const COUNTER_SIZE = 50;
// A leaf turning over the binding rises above it; this share of a leaf's height is kept clear
// between the header and the binding for it.
const RISE_ROOM = 0.36;

// The turn of one leaf, t = 0 (flat) … 1 (gone over the top). The thumb draws the leaf's
// bottom-right corner up along a path that bows inwards by BOW of the most the binding allows,
// and the leaf folds along the line halfway between the corner and the thumb. Past SQUASH_FROM
// the part above the binding folds down behind the book until it is gone at t = 1.
const BOW = 0.52;
const SQUASH_FROM = 0.55;
// The roll of paper along the fold, and the shadow the lifted part casts on the leaf under it.
const ROLL = 30;
const FOLD_SHADOW = 34;
const SHOW_THROUGH = 0.09;

// A slow drag turns one leaf every DRAG_STEP pt; dragging up turns forwards. Faster, more leaves
// are in the air at once: SPREAD_BASE leaves at rest, up to SPREAD_MAX, SPREAD_RATE more per
// leaf per second. Past either end the book gives RUBBER leaves at most.
const DRAG_STEP = 46;
const SPREAD_BASE = 1;
const SPREAD_MAX = 3.6;
const SPREAD_RATE = 0.16;
const SPEED_SMOOTH = 9;
const RUBBER = 1.2;
// After a release the book coasts, losing FRICTION of its speed per second, until it is slower
// than SETTLE_SPEED leaves/s; then a spring (stiffness, damping) lays the nearest leaf down.
const FRICTION = 1.7;
const SETTLE_SPEED = 3.2;
// Safety rails: a release is never faster than MAX_SPEED leaves/s, a coast that reaches an end
// hands at most END_SPEED leaves/s to the spring there, and the book's place never leaves the
// band SLACK leaves past either end, so one long frame cannot carry it off the book.
const MAX_SPEED = 60;
const END_SPEED = 10;
const SLACK = 1.6;
const STIFFNESS = 230;
const DAMPING = 23;
// A tick under the thumb every TICK_EVERY leaves.
const TICK_EVERY = 3;
const IMAGE_FADE = 0.4;

// autoPlay (seconds): the thumb turns LEAVES leaves over one at a time, rests, and turns them back
// one at a time. Each turn takes TURN s on a cubic ease-in-out, and a new one starts every STEP s,
// so the book lies still on every leaf for STEP − TURN s before the next turn begins.
const LEAVES = 5;
const TURN = 1.0;
const STEP = 1.7;
const REST = STEP - TURN;
// The rest on the first leaf before the first turn, and the longer rest on the last leaf before the
// way back.
const LEAD = 0.8;
const PAUSE = 0.8;
const BACK_AT = LEAD + LEAVES * STEP + PAUSE;
const LOOP = BACK_AT + LEAVES * STEP - REST + LEAD;
// Each segment moves the book from wherever it is to `aim` leaves past the leaf the loop started
// on; between segments the thumb lets go and the spring lays the leaf down.
type Segment = { from: number; to: number; aim: number; ease: 'out' | 'inOut' };
const SCRIPT: Segment[] = [
  ...Array.from({ length: LEAVES }, (_, k) => ({ from: LEAD + k * STEP, to: LEAD + k * STEP + TURN, aim: k + 1, ease: 'inOut' as const })),
  ...Array.from({ length: LEAVES }, (_, k) => ({
    from: BACK_AT + k * STEP,
    to: BACK_AT + k * STEP + TURN,
    aim: LEAVES - k - 1,
    ease: 'inOut' as const,
  })),
];
const USER_HOLD = 3.5;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

function ease(kind: 'out' | 'inOut', u: number) {
  'worklet';
  if (kind === 'out') return 1 - Math.pow(1 - u, 3);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** iOS-style resistance past an end: `x` leaves past it give at most `limit`. */
function rubber(x: number, limit: number) {
  'worklet';
  const sign = x < 0 ? -1 : 1;
  return sign * (1 - 1 / ((Math.abs(x) * 0.55) / limit + 1)) * limit;
}

/** The part of a polygon (flat x,y list) where (p·n − c) has the sign `keep`. */
function clipPolygon(points: number[], nx: number, ny: number, c: number, keep: 1 | -1) {
  'worklet';
  const out: number[] = [];
  const count = points.length / 2;
  for (let i = 0; i < count; i += 1) {
    const ax = points[i * 2];
    const ay = points[i * 2 + 1];
    const bx = points[((i + 1) % count) * 2];
    const by = points[((i + 1) % count) * 2 + 1];
    const sa = (ax * nx + ay * ny - c) * keep;
    const sb = (bx * nx + by * ny - c) * keep;
    if (sa >= 0) out.push(ax, ay);
    if ((sa >= 0) !== (sb >= 0)) {
      const k = sa / (sa - sb);
      out.push(ax + (bx - ax) * k, ay + (by - ay) * k);
    }
  }
  return out;
}

function polygonPath(points: number[]) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < points.length; i += 2) {
    if (i === 0) builder.moveTo(points[i], points[i + 1]);
    else builder.lineTo(points[i], points[i + 1]);
  }
  builder.close();
  return builder.build();
}

/** How far leaf `k` has turned with the book at `p` and `spread` leaves in the air. */
function turnOf(k: number, p: number, spread: number, last: number) {
  'worklet';
  // The back leaf never turns; pressed past the end, its corner only lifts a little.
  if (k >= last) return Math.min(0.12, Math.max(0, (p - last) * 0.3));
  const t = clamp01((p - k - 1 + spread) / spread);
  // Pulled back past the front, the first leaf's corner lifts as if to come back.
  if (k === 0 && p < 0) return Math.max(t, Math.min(0.12, -p * 0.3));
  return t;
}

/** The fold of a leaf w × h turned by t: the line's normal (towards the corner) and offset. */
function foldOf(t: number, w: number, h: number) {
  'worklet';
  const lift = 2 * h * t;
  // The thumb stays within a leaf's height of the binding's far corner, so the fold never
  // crosses the binding.
  const bow = BOW * Math.sqrt(Math.max(0, (2 * h - lift) * lift));
  const px = w - bow;
  const py = h - lift;
  const dx = w - px;
  const dy = h - py;
  const len = Math.max(0.0001, Math.hypot(dx, dy));
  const nx = dx / len;
  const ny = dy / len;
  const mx = (w + px) / 2;
  const my = (h + py) / 2;
  return { nx, ny, c: mx * nx + my * ny, mx, my, len };
}

type Loaded = { image: SkImage; at: number };
type Fonts = { label: SkFont; caption: SkFont; number: SkFont; counter: SkFont; chapter: SkFont; small: SkFont };

/** A leaf's face: paper, the photo printed on it, its caption and number. */
function drawLeaf(
  canvas: SkCanvas,
  page: FlipbookPage | undefined,
  index: number,
  total: number,
  loaded: Loaded | null | undefined,
  w: number,
  h: number,
  fonts: Fonts,
  now: number,
) {
  'worklet';
  // A leaf that is not there (an index off the book) draws nothing rather than throwing.
  if (!page) return;
  const paper = Skia.Paint();
  paper.setAntiAlias(true);
  paper.setColor(Skia.Color(PAPER));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), CORNER, CORNER), paper);
  const rect = Skia.XYWHRect(MARGIN, TAPE_H + 10, w - MARGIN * 2, h - TAPE_H - 10 - CAPTION_H);
  const blank = Skia.Paint();
  blank.setColor(Skia.Color('#e4dccd'));
  canvas.drawRect(rect, blank);
  const image = loaded ? loaded.image : null;
  const iw = image ? image.width() : 0;
  const ih = image ? image.height() : 0;
  if (loaded && image && iw > 0 && ih > 0) {
    const fit = Math.max(rect.width / iw, rect.height / ih);
    const sw = rect.width / fit;
    const sh = rect.height / fit;
    const art = Skia.Paint();
    art.setAntiAlias(true);
    art.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
    canvas.drawImageRectOptions(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), rect, FilterMode.Linear, MipmapMode.Linear, art);
  }
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(INK));
  const base = h - CAPTION_H / 2 + 5;
  canvas.drawText(page.title, MARGIN, base, ink, fonts.caption);
  const number = `${String(index + 1).padStart(2, '0')} / ${String(total).padStart(2, '0')}`;
  ink.setAlphaf(0.55);
  canvas.drawText(number, w - MARGIN - fonts.number.measureText(number).width, base, ink, fonts.number);
  // The paper curves into the binding, a little darker there.
  const curve = Skia.Paint();
  curve.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, TAPE_H), vec(0, TAPE_H + 22), [Skia.Color('rgba(60,40,20,0.16)'), Skia.Color('rgba(60,40,20,0)')], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, TAPE_H, w, 22), curve);
}

/** The lifted part of leaf `index`: its back, lighter paper, rolled along the fold. */
function drawFlap(
  canvas: SkCanvas,
  flap: SkPath,
  fold: { nx: number; ny: number; c: number; mx: number; my: number; len: number },
  squash: number,
  page: FlipbookPage | undefined,
  index: number,
  total: number,
  loaded: Loaded | null | undefined,
  w: number,
  h: number,
  fonts: Fonts,
  now: number,
) {
  'worklet';
  // Its shadow on whatever is under it, a little down and to the right.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(18,10,4,0.32)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
  canvas.save();
  canvas.translate(5, 9);
  canvas.drawPath(flap, shadow);
  canvas.restore();

  const back = Skia.Paint();
  back.setAntiAlias(true);
  back.setColor(Skia.Color(PAPER_BACK));
  canvas.drawPath(flap, back);

  canvas.save();
  canvas.clipPath(flap, ClipOp.Intersect, true);
  // The print shows faintly through the paper, mirrored by the fold.
  const through = SHOW_THROUGH * (1 - squash);
  if (through > 0.005 && loaded) {
    const { nx, ny, c } = fold;
    canvas.save();
    canvas.concat(Skia.Matrix([1 - 2 * nx * nx, -2 * nx * ny, 2 * c * nx, -2 * nx * ny, 1 - 2 * ny * ny, 2 * c * ny, 0, 0, 1]));
    drawLeaf(canvas, page, index, total, loaded, w, h, fonts, now);
    canvas.restore();
    const wash = Skia.Paint();
    wash.setColor(Skia.Color(PAPER_BACK));
    wash.setAlphaf(1 - through);
    canvas.drawPaint(wash);
  }
  // The roll: a crease in shadow at the fold, a highlight on the curve, then the paper turning
  // away from the light towards the far edge.
  const { nx, ny, mx, my, len } = fold;
  const roll = Skia.Paint();
  roll.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(mx, my),
      vec(mx - nx * ROLL, my - ny * ROLL),
      [Skia.Color('rgba(70,50,30,0.30)'), Skia.Color('rgba(255,255,255,0.75)'), Skia.Color('rgba(255,255,255,0)')],
      [0, 0.32, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPaint(roll);
  const away = Skia.Paint();
  const reach = Math.max(ROLL * 1.5, len * 0.5);
  away.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(mx - nx * ROLL, my - ny * ROLL),
      vec(mx - nx * reach, my - ny * reach),
      [Skia.Color('rgba(80,60,40,0)'), Skia.Color(`rgba(80,60,40,${0.12 + 0.12 * squash})`)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawPaint(away);
  canvas.restore();

  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.75);
  edge.setColor(Skia.Color('rgba(90,70,50,0.28)'));
  canvas.drawPath(flap, edge);
}

/** Text set to its left (align 0), middle (0.5) or right (1). */
function drawText(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, align: number, paint: SkPaint) {
  'worklet';
  canvas.drawText(text, x - font.measureText(text).width * align, y, paint, font);
}

/** Text with extra space between its letters. */
function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: SkPaint) {
  'worklet';
  let cursor = x;
  // A space measures as nothing on its own, so it is measured between two letters.
  const space = font.measureText('a a').width - font.measureText('aa').width;
  for (const letter of text) {
    if (letter !== ' ') canvas.drawText(letter, cursor, y, paint, font);
    cursor += (letter === ' ' ? space : font.measureText(letter).width) + tracking;
  }
}

/** The leaf number, each digit rolling up like an odometer as the leaves go by. */
function drawCounter(canvas: SkCanvas, value: number, font: SkFont, right: number, baseline: number, paint: SkPaint) {
  'worklet';
  const cell = font.measureText('0').width * 0.86;
  const whole = Math.floor(value);
  const frac = smoothstep(0.38, 0.62, value - whole);
  const line = COUNTER_SIZE * 1.05;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(right - cell * 2 - 4, baseline - COUNTER_SIZE * 0.92, cell * 2 + 8, COUNTER_SIZE * 1.12), ClipOp.Intersect, true);
  for (let place = 0; place < 2; place += 1) {
    const unit = place === 0 ? 1 : 10;
    const digit = Math.floor(whole / unit) % 10;
    // The tens roll only while the ones turn over from 9.
    const roll = place === 0 ? frac : whole % 10 === 9 ? frac : 0;
    const x = right - cell * (place + 1);
    for (let k = 0; k < 2; k += 1) {
      const shown = (digit + k) % 10;
      const y = baseline + (k - roll) * line;
      const alpha = clamp01(1 - Math.abs(k - roll) * 1.4);
      if (alpha <= 0.01) continue;
      paint.setAlphaf(alpha);
      const text = String(shown);
      canvas.drawText(text, x + (cell - font.measureText(text).width) / 2, y, paint, font);
    }
  }
  canvas.restore();
}

/**
 * A thick flipbook lying on a dark table, bound along its top edge under a cloth tape, a photo
 * printed on every leaf. Dragging up thumbs the leaves over: each one curls up from its
 * bottom-right corner, shows its lighter back as it rolls over the binding and falls behind the
 * book. A fast drag puts several leaves in the air at once, so the photos run like a film; the
 * book coasts on after a flick and lays the nearest leaf down on a spring. A counter at the top
 * rolls with the leaves.
 */
export function FlipbookScrub({ pages = PAGES, autoPlay = false, onPage }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const total = pages.length;
  const last = total - 1;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const fonts: Fonts = {
    label: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '600' }),
    caption: matchFont({ fontFamily: SERIF, fontSize: 15, fontStyle: 'italic' }),
    number: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '600' }),
    counter: matchFont({ fontFamily: SANS, fontSize: COUNTER_SIZE, fontWeight: '600' }),
    chapter: matchFont({ fontFamily: SERIF, fontSize: 24, fontStyle: 'italic', fontWeight: '600' }),
    small: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '500' }),
  };

  // The chapters in order, and each leaf's chapter as a number, for the rolling chapter line.
  const chapters = pages.map((leaf) => leaf.chapter).filter((name, i, all) => all.indexOf(name) === i);
  const chapterOf = pages.map((leaf) => chapters.indexOf(leaf.chapter));

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const pageW = Math.min(MAX_PAGE_W, width - SIDE * 2);
  const pageH = Math.round(pageW * PAGE_ASPECT);
  const bookX = (width - pageW) / 2;
  const headerBottom = insets.top + COUNTER_Y + 8;
  const bookY = Math.max(headerBottom + pageH * RISE_ROOM, (height - pageH - THICK) / 2 + 24);

  // The book's place in leaves: 0 has the first leaf on top, 0.5 has it half over.
  const page = useSharedValue(0);
  const velocity = useSharedValue(0);
  // |velocity|, smoothed: how many leaves are in the air.
  const speed = useSharedValue(0);
  const target = useSharedValue(0);
  const mode = useSharedValue<'drag' | 'coast' | 'settle' | 'script'>('settle');
  const dragFrom = useSharedValue(0);
  const byUser = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const segStart = useSharedValue(0);
  const segIndex = useSharedValue(-1);
  // The leaf the loop starts from and comes back to.
  const base = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const loaded = useSharedValue<(Loaded | null)[]>(pages.map(() => null));

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    base.set(0);
  }, [auto, base, clock, hold, looping]);

  // Each print arrives on its own and fades in; until then its leaf shows blank paper.
  useEffect(() => {
    let alive = true;
    loaded.set(pages.map(() => null));
    pages.forEach((leaf, i) => {
      Skia.Data.fromURI(photoUri(leaf.id))
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
  }, [pages, loaded, elapsed]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const rested = (index: number) => onPage?.(index);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    elapsed.set(elapsed.get() + dt);
    const before = page.get();

    if (mode.get() !== 'drag') {
      if (hold.get() > 0) {
        hold.set(hold.get() - dt);
        const resting = mode.get() === 'settle' && Math.abs(velocity.get()) < 0.05 && Math.abs(page.get() - target.get()) < 0.01;
        // A book still coasting or settling keeps the loop waiting until it lies still.
        if (hold.get() <= 0 && looping.get() && !resting) hold.set(0.1);
        if (hold.get() <= 0 && looping.get()) {
          // Back into the loop from the leaf the hand left the book on: it becomes one of the
          // loop's leaves (as near the start as the book allows) and the script picks up in the
          // rest on that leaf, so the book only ever turns one leaf at a time.
          const at = Math.min(last, Math.max(0, Math.round(page.get())));
          const from = Math.min(at, Math.max(0, last - LEAVES));
          const j = at - from;
          base.set(from);
          clock.set(j === 0 ? 0 : BACK_AT + (LEAVES - j) * STEP - REST);
          segIndex.set(-1);
        }
      } else if (looping.get()) {
        const t = (clock.get() + dt) % LOOP;
        clock.set(t);
        let active = -1;
        for (let i = 0; i < SCRIPT.length; i += 1) {
          if (t >= SCRIPT[i].from && t < SCRIPT[i].to) active = i;
        }
        if (active !== segIndex.get()) {
          if (active >= 0) {
            segStart.set(page.get());
            byUser.set(false);
            mode.set('script');
          } else if (segIndex.get() >= 0) {
            // A segment ended: the thumb lets go and the nearest leaf settles.
            target.set(Math.min(last, Math.max(0, Math.round(page.get()))));
            mode.set('settle');
          }
          segIndex.set(active);
        }
        if (active >= 0) {
          const s = SCRIPT[active];
          const u = (t - s.from) / (s.to - s.from);
          const aim = Math.min(last, Math.max(0, base.get() + s.aim));
          const p = segStart.get() + (aim - segStart.get()) * ease(s.ease, u);
          velocity.set((p - page.get()) / dt);
          page.set(p);
        }
      }

      const p = page.get();
      if (mode.get() === 'coast') {
        if (Math.abs(velocity.get()) < SETTLE_SPEED) {
          const v = velocity.get();
          target.set(Math.min(last, Math.max(0, v > 0.4 ? Math.ceil(p) : v < -0.4 ? Math.floor(p) : Math.round(p))));
          mode.set('settle');
        } else {
          velocity.set(velocity.get() * Math.exp(-FRICTION * dt));
          const next = p + velocity.get() * dt;
          if (next < 0 || next > last) {
            // The coast reaches an end: it stops on the end leaf, and the spring there takes what
            // is left of its speed (capped), so the leaf presses in a little and settles.
            const end = next < 0 ? 0 : last;
            page.set(end);
            velocity.set(Math.max(-END_SPEED, Math.min(END_SPEED, velocity.get())));
            target.set(end);
            mode.set('settle');
          } else {
            page.set(next);
          }
        }
      }
      if (mode.get() === 'settle') {
        if (reducedMotion) {
          page.set(target.get());
          velocity.set(0);
        } else {
          const v = velocity.get() + (STIFFNESS * (target.get() - p) - DAMPING * velocity.get()) * dt;
          velocity.set(v);
          page.set(p + v * dt);
        }
      }
    }

    // Whatever happened above, the place stays a finite number near the book.
    const placed = page.get();
    if (!Number.isFinite(placed) || !Number.isFinite(velocity.get())) {
      page.set(Math.min(last, Math.max(0, Math.round(target.get()) || 0)));
      velocity.set(0);
      mode.set('settle');
    } else if (placed < -SLACK || placed > last + SLACK) {
      page.set(Math.min(last + SLACK, Math.max(-SLACK, placed)));
    }

    // The script turns one leaf at a time, so only a hand puts several leaves in the air.
    const fast = reducedMotion || !byUser.get() ? 0 : Math.abs(velocity.get());
    speed.set(speed.get() + (fast - speed.get()) * (1 - Math.exp(-dt * SPEED_SMOOTH)));
    if (mode.get() === 'drag') {
      // The finger's speed, read from where it moved the book since the last frame.
      velocity.set(velocity.get() + ((page.get() - before) / dt - velocity.get()) * 0.4);
    }
  });

  // A tick every few leaves while a hand turns them.
  useAnimatedReaction(
    () => Math.floor(page.get() / TICK_EVERY),
    (now, previous) => {
      if (previous === null || now === previous) return;
      if (byUser.get()) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => mode.get() === 'settle' && Math.abs(velocity.get()) < 0.05 && Math.abs(page.get() - target.get()) < 0.01,
    (resting, previous) => {
      if (resting && previous === false && byUser.get()) scheduleOnRN(rested, target.get());
    },
  );

  const pan = Gesture.Pan()
    .minDistance(4)
    .onStart(() => {
      hold.set(USER_HOLD);
      byUser.set(true);
      segIndex.set(-1);
      // Caught wherever it is, mid-turn or mid-flight.
      dragFrom.set(Math.min(last, Math.max(0, page.get())));
      mode.set('drag');
    })
    .onUpdate((event) => {
      if (mode.get() !== 'drag') return;
      hold.set(USER_HOLD);
      const raw = dragFrom.get() - event.translationY / DRAG_STEP;
      if (raw < 0) page.set(rubber(raw, RUBBER));
      else if (raw > last) page.set(last + rubber(raw - last, RUBBER));
      else page.set(raw);
    })
    .onEnd((event) => {
      if (mode.get() !== 'drag') return;
      hold.set(USER_HOLD);
      const thrown = -event.velocityY / DRAG_STEP;
      velocity.set(Number.isFinite(thrown) ? Math.max(-MAX_SPEED, Math.min(MAX_SPEED, thrown)) : 0);
      mode.set('coast');
    })
    .onFinalize(() => {
      if (mode.get() === 'drag') {
        target.set(Math.min(last, Math.max(0, Math.round(page.get()))));
        mode.set('settle');
      }
    });

  // A tap on the book turns one leaf forwards, or back when tapped above the middle.
  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      if (!success) return;
      hold.set(USER_HOLD);
      byUser.set(true);
      segIndex.set(-1);
      const forward = event.y > bookY + pageH / 2;
      const from = mode.get() === 'settle' ? target.get() : Math.round(page.get());
      const next = Math.min(last, Math.max(0, from + (forward ? 1 : -1)));
      // A tap caught mid-coast stops the coast; the spring keeps the speed it had.
      velocity.set(Math.max(-END_SPEED, Math.min(END_SPEED, velocity.get())));
      target.set(next);
      mode.set('settle');
      if (next !== from) scheduleOnRN(tick);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    if (width <= 0 || total === 0) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    // Drawn from a place kept on the book, whatever the physics did this frame.
    const raw = page.get();
    const p = Number.isFinite(raw) ? Math.min(last + RUBBER, Math.max(-RUBBER, raw)) : 0;
    const images = loaded.get();
    const w = pageW;
    const h = pageH;

    // The table, lit from above the book.
    const table = Skia.Paint();
    table.setShader(
      Skia.Shader.MakeRadialGradient(vec(width * 0.42, bookY + h * 0.35), height * 0.62, [Skia.Color(TABLE_LIT), Skia.Color(TABLE)], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), table);

    // The header: the label, the chapter under it, and the counter on the right.
    const chrome = Skia.Paint();
    chrome.setAntiAlias(true);
    chrome.setColor(Skia.Color(CHROME));
    chrome.setAlphaf(0.5);
    drawTracked(canvas, 'MOTION STUDIES', fonts.label, SIDE, insets.top + LABEL_Y, 2.2, chrome);
    const at = Math.min(last, Math.max(0, p));
    // The chapter rolls up a line, like the counter, while the leaf between two chapters turns.
    const whole = Math.max(0, Math.min(last - 1, Math.floor(at)));
    const nextChapter = chapterOf[Math.min(last, whole + 1)];
    const chapterAt = chapterOf[whole] + (nextChapter - chapterOf[whole]) * smoothstep(0.2, 0.8, at - whole);
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(0, insets.top + LABEL_Y + 8, width * 0.6, 40), ClipOp.Intersect, true);
    for (let j = 0; j < chapters.length; j += 1) {
      const d = j - chapterAt;
      const alpha = clamp01(1 - Math.abs(d) * 1.4);
      if (alpha <= 0.01) continue;
      chrome.setAlphaf(alpha);
      canvas.drawText(chapters[j], SIDE, insets.top + LABEL_Y + 34 + d * 30, chrome, fonts.chapter);
    }
    canvas.restore();
    chrome.setAlphaf(0.5);
    const of = `/ ${String(total).padStart(2, '0')}`;
    drawText(canvas, of, fonts.small, width - SIDE, insets.top + COUNTER_Y, 1, chrome);
    chrome.setAlphaf(1);
    drawCounter(canvas, at + 1, fonts.counter, width - SIDE - fonts.small.measureText(of).width - 8, insets.top + COUNTER_Y, chrome);
    chrome.setAlphaf(0.42);
    drawText(canvas, 'Drag up to riffle  ·  Tap to turn a leaf', fonts.small, width / 2, height - insets.bottom - 22, 0.5, chrome);

    canvas.save();
    canvas.translate(bookX, bookY);

    // The book's shadow on the table, and the block of leaves under the top one.
    const cast = Skia.Paint();
    cast.setAntiAlias(true);
    cast.setColor(Skia.Color('rgba(0,0,0,0.55)'));
    cast.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(8, 14, w, h + THICK), 8, 8), cast);
    const block = Skia.Paint();
    block.setAntiAlias(true);
    block.setColor(Skia.Color(PAPER_EDGE));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h + THICK), CORNER + 2, CORNER + 2), block);
    // The edges of the leaves, a hairline each, darker towards the table.
    const leafEdge = Skia.Paint();
    leafEdge.setAntiAlias(true);
    leafEdge.setStrokeWidth(0.6);
    for (let y = h + 1.6; y < h + THICK - 1; y += 1.7) {
      const depth = (y - h) / THICK;
      leafEdge.setColor(Skia.Color(`rgba(90,72,52,${0.18 + depth * 0.32})`));
      canvas.drawLine(2, y, w - 2, y, leafEdge);
    }
    const underside = Skia.Paint();
    underside.setShader(
      Skia.Shader.MakeLinearGradient(vec(0, h), vec(0, h + THICK), [Skia.Color('rgba(40,28,16,0.05)'), Skia.Color('rgba(40,28,16,0.38)')], null, TileMode.Clamp),
    );
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, h, w, THICK), CORNER + 2, CORNER + 2), underside);

    const spread = reducedMotion ? 1 : Math.min(SPREAD_MAX, SPREAD_BASE + speed.get() * SPREAD_RATE);
    if (reducedMotion) {
      // No curl: the next leaf fades in over the one on top.
      const k = Math.min(last, Math.max(0, Math.floor(at)));
      drawLeaf(canvas, pages[k], k, total, images[k], w, h, fonts, now);
      const f = at - k;
      if (f > 0.01 && k < last) {
        const fade = Skia.Paint();
        fade.setAlphaf(f);
        canvas.saveLayer(fade, null);
        drawLeaf(canvas, pages[k + 1], k + 1, total, images[k + 1], w, h, fonts, now);
        canvas.restore();
      }
      drawTape(canvas, w);
    } else {
      // The first leaf still lying flat, with the tape over it; then the leaves in the air
      // above it, the one furthest over last. The turn falls leaf by leaf, so the first leaf
      // that has not started is found by walking up from the last one gone over.
      let top = Math.max(0, Math.floor(p - 1));
      while (top < last && turnOf(top, p, spread, last) > 0) top += 1;
      const flat = turnOf(top, p, spread, last) <= 0;
      if (flat) drawLeaf(canvas, pages[top], top, total, images[top], w, h, fonts, now);
      drawTape(canvas, w);
      const from = Math.min(last, flat ? top - 1 : top);
      const pageBox = [0, 0, w, 0, w, h, 0, h];
      const underTape = [-1, TAPE_H, w + 1, TAPE_H, w + 1, h + 1, -1, h + 1];
      for (let k = from; k >= Math.max(0, Math.floor(p - 1)); k -= 1) {
        const t = turnOf(k, p, spread, last);
        if (t >= 1) break;
        if (t <= 0) continue;
        const fold = foldOf(t, w, h);
        // What still lies flat, clipped clear of the tape that holds it.
        const keep = clipPolygon(underTape, fold.nx, fold.ny, fold.c, -1);
        if (keep.length >= 6) {
          canvas.save();
          canvas.clipPath(polygonPath(keep), ClipOp.Intersect, true);
          drawLeaf(canvas, pages[k], k, total, images[k], w, h, fonts, now);
          canvas.restore();
        }
        // The shadow the lifted part throws on the leaf it uncovers, deepest at the fold.
        const open = clipPolygon(pageBox, fold.nx, fold.ny, fold.c, 1);
        if (open.length >= 6) {
          canvas.save();
          canvas.clipPath(polygonPath(open), ClipOp.Intersect, true);
          const dark = Skia.Paint();
          dark.setShader(
            Skia.Shader.MakeLinearGradient(
              vec(fold.mx, fold.my),
              vec(fold.mx + fold.nx * FOLD_SHADOW, fold.my + fold.ny * FOLD_SHADOW),
              [Skia.Color(`rgba(30,18,8,${0.34 * (1 - smoothstep(0.75, 1, t))})`), Skia.Color('rgba(30,18,8,0)')],
              null,
              TileMode.Clamp,
            ),
          );
          canvas.drawPaint(dark);
          canvas.restore();
        }
        // The lifted part, mirrored over the fold; what rises above the binding folds down
        // behind the book towards the end of the turn.
        const lifted = clipPolygon(pageBox, fold.nx, fold.ny, fold.c, 1);
        if (lifted.length >= 6) {
          const squash = smoothstep(SQUASH_FROM, 1, t);
          const mirrored: number[] = [];
          const n = lifted.length / 2;
          for (let i = 0; i < n; i += 1) {
            const x = lifted[i * 2];
            const y = lifted[i * 2 + 1];
            const s = x * fold.nx + y * fold.ny - fold.c;
            mirrored.push(x - 2 * s * fold.nx, y - 2 * s * fold.ny);
          }
          // Split at the binding, so above it can fold away while below it stays put.
          const split: number[] = [];
          for (let i = 0; i < n; i += 1) {
            const ax = mirrored[i * 2];
            const ay = mirrored[i * 2 + 1];
            const bx = mirrored[((i + 1) % n) * 2];
            const by = mirrored[((i + 1) % n) * 2 + 1];
            split.push(ax, ay < 0 ? ay * (1 - squash) : ay);
            if ((ay < 0) !== (by < 0)) {
              const m = ay / (ay - by);
              split.push(ax + (bx - ax) * m, 0);
            }
          }
          if (1 - squash > 0.01) drawFlap(canvas, polygonPath(split), fold, squash, pages[k], k, total, images[k], w, h, fonts, now);
        }
      }
    }
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: TABLE }]}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Flipbook of ${total} photos. Drag up to riffle through the leaves.`}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

/** The cloth tape that binds the leaves along the top, with a row of stitches. */
function drawTape(canvas: SkCanvas, w: number) {
  'worklet';
  const cloth = Skia.Paint();
  cloth.setAntiAlias(true);
  cloth.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, -6), vec(0, TAPE_H), [Skia.Color(TAPE_SHEEN), Skia.Color(TAPE), Skia.Color('#1c1511')], [0, 0.45, 1], TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-3, -6, w + 6, TAPE_H + 6), 5, 5), cloth);
  const stitch = Skia.Paint();
  stitch.setAntiAlias(true);
  stitch.setStrokeWidth(1.2);
  stitch.setStrokeCap(StrokeCap.Round);
  stitch.setColor(Skia.Color('rgba(239,230,216,0.28)'));
  for (let x = 12; x < w - 8; x += 11) canvas.drawLine(x, TAPE_H - 7, x + 5, TAPE_H - 7, stitch);
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
});
