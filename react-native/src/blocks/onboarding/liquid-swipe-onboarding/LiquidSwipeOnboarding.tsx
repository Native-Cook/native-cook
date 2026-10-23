import {
  Canvas,
  ClipOp,
  Group,
  matchFont,
  PaintStyle,
  Path,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  type SkPicture,
  StrokeCap,
  StrokeJoin,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { type EdgeInsets, useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type LiquidPage = {
  /** Unsplash photo id, shown in the arch. */
  photo: string;
  /** The page's colour, and the colour of its type. */
  color: string;
  ink: string;
  /** The headline, one string per line. */
  title: string[];
  body: string;
};

type Props = {
  pages?: LiquidPage[];
  /** The label of the button on the last page. */
  action?: string;
  /** Swipes through every page and back again, then rests and starts over. */
  autoPlay?: boolean;
  /** Called with a page's index once it fills the screen. */
  onChange?: (index: number) => void;
  /** Called when the button on the last page is tapped. */
  onDone?: () => void;
};

const PAGES: LiquidPage[] = [
  {
    photo: 'photo-1582721478779-0ae163c05a60',
    color: '#ff5b1f',
    ink: '#1d0b04',
    title: ['Find the', 'fire.'],
    body: 'Guided treks to forty active craters, from your first ridge to the rim.',
  },
  {
    photo: 'photo-1628632577468-645afac21c82',
    color: '#17120f',
    ink: '#ffd9c1',
    title: ['Read the', 'ground.'],
    body: 'Live heat maps and vent alerts on every trail you walk.',
  },
  {
    photo: 'photo-1663640397122-d50df280b1db',
    color: '#f4c9a4',
    ink: '#2b1109',
    title: ['Climb at', 'first light.'],
    body: 'Small groups, local guides and hot coffee waiting at the crater.',
  },
  {
    photo: 'photo-1623093386041-a0915e5a1ca4',
    color: '#7c1c0f',
    ink: '#ffe4d4',
    title: ['Stand at', 'the edge.'],
    body: 'Your first trek is on us. Pick a volcano and we will do the rest.',
  },
];

const BRAND = 'CALDERA';

// The arch holds a photo about 360 pt wide on a phone; 1000 px covers that at 3x.
const PHOTO_WIDTH = 1000;
const photoUri = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${PHOTO_WIDTH}&q=80`;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const CONDENSED = process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed';

// Layout, pt.
const SIDE = 24;
const PHOTO_TOP = 60; // below the top inset
const PHOTO_HEIGHT = 0.4; // share of the screen's height
const TITLE_SIZE = 56;
const TITLE_LEADING = 53;
const BODY_SIZE = 16;
const BODY_LEADING = 23;
const DOTS_LEFT = 46;
const BUTTON_HEIGHT = 52;

// The tab: a smooth bump of the next page sticking out of the right edge (the previous page's on
// the left), REST_REACH deep and TAB_RADIUS tall either side of its centre, which rests
// TAB_ABOVE pt above the bottom inset.
const REST_REACH = 26;
const TAB_RADIUS = 52;
const TAB_ABOVE = 40;
// While dragged, the bump's tip follows the finger and it widens as it goes.
const PULL_REACH = 0.96; // pt of reach per pt dragged
const PULL_RADIUS = 0.78; // pt of half-height per pt dragged
const FOLLOW = 16; // 1/s, how quickly the bump's centre follows the finger up and down
// Past COMMIT_SHARE of the width, or flicked faster than COMMIT_SPEED, the liquid sweeps across.
const COMMIT_SHARE = 0.34;
const COMMIT_SPEED = 500; // pt/s
const RELEASE_FADE = 70; // ms; a finger held still this long before letting go has lost most of its speed
const SAMPLES = 44;
// The sweep: a spring with a little overshoot, carried by the release speed; the new page's
// content rides in PARALLAX of the width behind the liquid and bounces into place.
const SWEEP_SPRING = { duration: 1000, dampingRatio: 0.7 };
const RETURN_SPRING = { duration: 520, dampingRatio: 0.55 };
const TAB_SPRING = { duration: 420, dampingRatio: 0.6 };
const PARALLAX = 0.55;
// Share of the release speed (in widths per second) the sweep carries on with.
const SWEEP_CARRY = 0.5;
const PUSH = 0.18; // how far the covered page is pushed away, share of the width

// autoPlay (seconds): three swipes forward and three back, then a rest. Each scripted drag runs
// DRAG_TIME s and speeds up as it goes, so it lets go with a flick.
const LOOP = 15;
const FORWARD = [1.0, 3.2, 5.4];
const BACK = [8.6, 10.8, 13.0];
const STARTS = [...FORWARD, ...BACK];
const DRAG_TIME = 0.62;
const DRAG_SHARE = 0.5; // of the width
const DRAG_RISE = 110; // pt the scripted finger rises over a drag
const USER_HOLD = 4;

/** The bump that shapes the liquid's edge: 1 at its centre, 0 at ±1, smooth throughout. */
function bump(u: number) {
  'worklet';
  if (u <= -1 || u >= 1) return 0;
  return 0.5 + 0.5 * Math.cos(Math.PI * u);
}

/** Whether a clock that went from `before` to `now`, wrapping at LOOP, passed `at`. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** Diminishing pull past a limit. */
function rubber(x: number, limit: number) {
  'worklet';
  if (x <= limit) return x;
  const over = x - limit;
  return limit + over / (1 + over / 120);
}

/** `inset` pt in from the right edge (side 1) or the left edge (side -1). */
function fromSide(side: number, width: number, inset: number) {
  'worklet';
  return side > 0 ? width - inset : inset;
}

/**
 * The liquid page coming in from one side. `side` is 1 for the next page (from the right) and
 * -1 for the previous one (from the left). Its edge is straight at `edge` pt from that side,
 * with a bump `reach` deep and `radius` tall either side of `cy`.
 */
function liquid(side: number, width: number, height: number, edge: number, reach: number, radius: number, cy: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(fromSide(side, width, -2), -2);
  b.lineTo(fromSide(side, width, edge), -2);
  const r = Math.max(1, radius);
  const top = cy - r;
  if (top > 0) b.lineTo(fromSide(side, width, edge), top);
  for (let k = 0; k <= SAMPLES; k += 1) {
    const y = top + (2 * r * k) / SAMPLES;
    b.lineTo(fromSide(side, width, edge + reach * bump((y - cy) / r)), y);
  }
  b.lineTo(fromSide(side, width, edge), Math.max(height + 2, cy + r));
  b.lineTo(fromSide(side, width, -2), Math.max(height + 2, cy + r));
  b.close();
  return b.detach();
}

/** A chevron at `x, y` pointing toward the middle of the screen from `side`. */
function chevron(side: number, x: number, y: number, size: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(x + side * size * 0.45, y - size);
  b.lineTo(x - side * size * 0.45, y);
  b.lineTo(x + side * size * 0.45, y + size);
  return b.detach();
}

type LiquidState = {
  width: number;
  height: number;
  restY: number;
  current: number;
  count: number;
  side: number;
  drag: number;
  sweep: number;
  tab: number;
  tabY: number;
  reduced: boolean;
};
type LiquidShape = { edge: number; reach: number; radius: number; cy: number };

/** How much of a resting tab shows: it grows back after a page lands, and the tab on the far
 * side tucks away while the other side's liquid sweeps in, since it will bring another page. */
function tabShown(direction: number, side: number, tab: number, sweep: number) {
  'worklet';
  const other = side !== 0 && side !== direction;
  return tab * (other ? Math.max(0, 1 - sweep * 2.5) : 1);
}

/** The liquid's shape on one side: its tab at rest, pulled after the finger, then sweeping across. */
function shapeOf(direction: number, s: LiquidState): LiquidShape | null {
  'worklet';
  const next = s.current + direction;
  if (next < 0 || next >= s.count || s.width <= 0) return null;
  const active = s.side === direction;
  const d = active ? s.drag : 0;
  const f = active ? s.sweep : 0;
  const cy = active ? s.tabY : s.restY;
  const reach = Math.max(active ? 3 : 0, REST_REACH * (active ? 1 : tabShown(direction, s.side, s.tab, s.sweep)) + d * PULL_REACH);
  const radius = TAB_RADIUS + Math.max(0, d) * PULL_RADIUS;
  if (s.reduced && f > 0) return { edge: s.width + 4, reach: 0, radius: s.height, cy };
  return {
    edge: (s.width + 60) * f,
    reach: reach * (1 - f),
    radius: radius + s.height * 1.3 * f,
    cy: cy + (s.height / 2 - cy) * f,
  };
}

/** The chevron near the tip of a tab. */
function chevronOn(direction: number, g: LiquidShape | null, width: number) {
  'worklet';
  if (!g) return Skia.Path.Make();
  const tip = g.edge + g.reach;
  return chevron(direction, direction > 0 ? width - tip + 11 : tip - 11, g.cy, 5.5);
}

/** The chevron fades as its tab is pulled, and with the tab. */
function chevronShown(direction: number, g: { side: number; drag: number; sweep: number; tab: number }) {
  'worklet';
  const active = g.side === direction;
  return Math.max(0, 1 - (active ? g.drag / 60 + g.sweep * 4 : 0)) * tabShown(direction, g.side, g.tab, g.sweep);
}

type Fonts = { title: SkFont; body: SkFont; label: SkFont; button: SkFont };
type Layout = { width: number; height: number; insets: EdgeInsets };

/** Breaks `text` into lines no wider than `max`. */
function wrap(text: string, font: SkFont, max: number) {
  const lines: string[] = [];
  let line = '';
  for (const word of text.split(' ')) {
    const next = line ? `${line} ${word}` : word;
    if (line && font.measureText(next).width > max) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function drawTracked(canvas: SkCanvas, text: string, font: SkFont, x: number, y: number, tracking: number, paint: ReturnType<typeof Skia.Paint>) {
  let at = x;
  for (const letter of text) {
    canvas.drawText(letter, at, y, paint, font);
    at += (letter === ' ' ? font.measureText('a a').width - font.measureText('aa').width : font.measureText(letter).width) + tracking;
  }
}

function trackedWidth(text: string, font: SkFont, tracking: number) {
  let w = 0;
  for (const letter of text) w += font.measureText(letter).width + tracking;
  return w - tracking;
}

/** Where the button on the last page sits. */
function buttonRect(layout: Layout, font: SkFont, label: string) {
  const w = font.measureText(label).width + 66;
  const cy = layout.height - layout.insets.bottom - TAB_ABOVE;
  return { x: layout.width - SIDE - w, y: cy - BUTTON_HEIGHT / 2, width: w, height: BUTTON_HEIGHT };
}

/** Records one page: its colour, the photo in an arch, the headline and copy, and the dots. */
function recordPage(page: LiquidPage, index: number, count: number, image: SkImage | null, layout: Layout, fonts: Fonts, action: string) {
  const { width, height, insets } = layout;
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(-width, -40, width * 3, height + 80));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(page.color));
  canvas.drawRect(Skia.XYWHRect(-width, -40, width * 3, height + 80), paint);

  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color(page.ink));

  // Header: the brand and the page number.
  const headerY = insets.top + 28;
  drawTracked(canvas, BRAND, fonts.label, SIDE, headerY, 3, ink);
  const number = `${String(index + 1).padStart(2, '0')} / ${String(count).padStart(2, '0')}`;
  drawTracked(canvas, number, fonts.label, width - SIDE - trackedWidth(number, fonts.label, 1.5), headerY, 1.5, ink);

  // The photo in an arch: a semicircle on top of a rectangle.
  const arch = { x: SIDE, y: insets.top + PHOTO_TOP, width: width - SIDE * 2, height: height * PHOTO_HEIGHT };
  const r = arch.width / 2;
  const shape = Skia.PathBuilder.Make()
    .moveTo(arch.x, arch.y + arch.height)
    .lineTo(arch.x, arch.y + r)
    .arcToOval(Skia.XYWHRect(arch.x, arch.y, arch.width, r * 2), 180, 180, false)
    .lineTo(arch.x + arch.width, arch.y + arch.height)
    .close()
    .detach();
  canvas.save();
  canvas.clipPath(shape, ClipOp.Intersect, true);
  if (image) {
    const iw = image.width();
    const ih = image.height();
    const scale = Math.max(arch.width / iw, arch.height / ih);
    const sw = arch.width / scale;
    const sh = arch.height / scale;
    canvas.drawImageRect(image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(arch.x, arch.y, arch.width, arch.height), paint);
  } else {
    const wash = Skia.Paint();
    wash.setColor(Skia.Color(page.ink));
    wash.setAlphaf(0.12);
    canvas.drawPath(shape, wash);
  }
  canvas.restore();

  // Headline and copy.
  let y = arch.y + arch.height + TITLE_SIZE + 14;
  for (const line of page.title) {
    canvas.drawText(line, SIDE - 2, y, ink, fonts.title);
    y += TITLE_LEADING;
  }
  y += BODY_LEADING - TITLE_LEADING + 14;
  const soft = Skia.Paint();
  soft.setAntiAlias(true);
  soft.setColor(Skia.Color(page.ink));
  soft.setAlphaf(0.78);
  for (const line of wrap(page.body, fonts.body, width - SIDE * 2 - 30)) {
    canvas.drawText(line, SIDE, y, soft, fonts.body);
    y += BODY_LEADING;
  }

  // Dots: the current page is a longer bar.
  const dotsY = height - insets.bottom - TAB_ABOVE;
  let x = DOTS_LEFT;
  for (let i = 0; i < count; i += 1) {
    const w = i === index ? 22 : 7;
    const dot = Skia.Paint();
    dot.setAntiAlias(true);
    dot.setColor(Skia.Color(page.ink));
    dot.setAlphaf(i === index ? 1 : 0.35);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, dotsY - 3.5, w, 7), 3.5, 3.5), dot);
    x += w + 7;
  }

  // The last page has a button where the tab would be.
  if (index === count - 1) {
    const b = buttonRect(layout, fonts.button, action);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x, b.y, b.width, b.height), b.height / 2, b.height / 2), ink);
    const label = Skia.Paint();
    label.setAntiAlias(true);
    label.setColor(Skia.Color(page.color));
    canvas.drawText(action, b.x + 24, b.y + b.height / 2 + 5.5, label, fonts.button);
    const arrow = Skia.Paint();
    arrow.setAntiAlias(true);
    arrow.setStyle(PaintStyle.Stroke);
    arrow.setStrokeWidth(2);
    arrow.setStrokeCap(StrokeCap.Round);
    arrow.setStrokeJoin(StrokeJoin.Round);
    arrow.setColor(Skia.Color(page.color));
    const ax = b.x + b.width - 30;
    const ay = b.y + b.height / 2;
    const path = Skia.PathBuilder.Make().moveTo(ax - 7, ay).lineTo(ax + 7, ay).moveTo(ax + 1, ay - 6).lineTo(ax + 7, ay).lineTo(ax + 1, ay + 6).detach();
    canvas.drawPath(path, arrow);
  }
  return recorder.finishRecordingAsPicture();
}

/**
 * Onboarding pages in bold colours that pour over each other like liquid. A tab of the next
 * page sticks out of the right edge; dragging it pulls a smooth wave of that page after the
 * finger, and past a third of the width the wave sweeps across and fills the screen with a
 * springy overshoot. The previous page waits as a tab on the left.
 */
export function LiquidSwipeOnboarding({ pages = PAGES, action = 'Pick a volcano', autoPlay = false, onChange, onDone }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = pages.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [images, setImages] = useState<(SkImage | null)[]>(() => pages.map(() => null));
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: CONDENSED, fontSize: TITLE_SIZE, fontWeight: '800' }),
      body: matchFont({ fontFamily: SANS, fontSize: BODY_SIZE, fontWeight: '500' }),
      label: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '700' }),
      button: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '600' }),
    }),
    [],
  );

  useEffect(() => {
    let alive = true;
    pages.forEach((page, i) => {
      Skia.Data.fromURI(photoUri(page.photo))
        .then((data) => Skia.Image.MakeImageFromEncoded(data))
        .then((image) => {
          if (!alive || !image) return;
          setImages((list) => list.map((old, k) => (k === i ? image : old)));
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
  }, [pages]);

  const layout = useMemo<Layout>(() => ({ width, height, insets }), [width, height, insets]);
  const pictures = useMemo<SkPicture[]>(
    () => (width > 0 ? pages.map((page, i) => recordPage(page, i, count, images[i] ?? null, layout, fonts, action)) : []),
    [pages, count, images, layout, fonts, action, width],
  );
  const empty = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    recorder.beginRecording(Skia.XYWHRect(0, 0, 1, 1));
    return recorder.finishRecordingAsPicture();
  }, []);
  const button = useMemo(() => (width > 0 ? buttonRect(layout, fonts.button, action) : null), [layout, fonts, action, width]);

  const restY = height - insets.bottom - TAB_ABOVE;
  const current = useSharedValue(0);
  // Which side is moving: 1 the next page from the right, -1 the previous one from the left, 0 none.
  const side = useSharedValue(0);
  const drag = useSharedValue(0); // pt dragged toward the middle
  const sweep = useSharedValue(0); // 0 … 1 as the liquid fills the screen
  const busy = useSharedValue(false);
  const tab = useSharedValue(1); // the tabs grow back in after a page lands
  const fingerY = useSharedValue(0);
  const tabY = useSharedValue(0);
  const dragging = useSharedValue(false);
  const pressed = useSharedValue(0);
  // Where the finger was last seen moving, when, and how fast (pt/s).
  const track = useSharedValue({ x: 0, t: 0, v: 0 });
  // The frame clock, ms, for timing the finger.
  const elapsed = useSharedValue(0);

  // The scripted finger.
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const scripted = useSharedValue(-1);
  const stage = useSharedValue({ width: 0, height: 0, restY: 0 });

  useEffect(() => {
    stage.set({ width, height, restY });
    if (!dragging.get()) {
      fingerY.set(restY);
      tabY.set(restY);
    }
  }, [width, height, restY, stage, dragging, fingerY, tabY]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const committed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const landed = (index: number) => onChange?.(index);
  const done = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    onDone?.();
  };

  /** The page under the liquid has filled the screen: it becomes the current page. */
  function settle() {
    'worklet';
    const s = side.get();
    current.set(current.get() + s);
    side.set(0);
    drag.set(0);
    sweep.set(0);
    busy.set(false);
    tab.set(0);
    tab.set(withSpring(1, TAB_SPRING));
    scheduleOnRN(landed, current.get());
  }

  /** Lets go of the liquid: it sweeps across, or falls back into its tab. */
  function letGo(velocity: number, user: boolean) {
    'worklet';
    const s = stage.get();
    const d = drag.get();
    dragging.set(false);
    if (side.get() === 0) return;
    if (d > s.width * COMMIT_SHARE || (velocity > COMMIT_SPEED && d > 24)) {
      // Nothing else moves the sweep while it runs, so it always ends in `settle`, even if it is
      // cut short.
      busy.set(true);
      if (user) scheduleOnRN(committed);
      if (reducedMotion) {
        sweep.set(withTiming(1, { duration: 280 }, () => settle()));
      } else {
        sweep.set(withSpring(1, { ...SWEEP_SPRING, velocity: (Math.max(0, velocity) / s.width) * SWEEP_CARRY }, () => settle()));
      }
    } else {
      drag.set(
        withSpring(0, { ...RETURN_SPRING, velocity: -velocity }, () => {
          if (!dragging.get()) side.set(0);
        }),
      );
    }
  }

  function grab(direction: number) {
    'worklet';
    const next = current.get() + direction;
    // Not before the first layout: letting go divides by the width.
    if (busy.get() || next < 0 || next >= count || stage.get().width <= 0) return false;
    side.set(direction);
    dragging.set(true);
    return true;
  }

  useFrameCallback((frame) => {
    elapsed.set(frame.timestamp);
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const s = stage.get();
    if (s.width <= 0) return;

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let k = 0; k < STARTS.length; k += 1) {
        if (crossed(before, t, STARTS[k]) && grab(k < FORWARD.length ? 1 : -1)) scripted.set(STARTS[k]);
      }
      const at = scripted.get();
      if (at >= 0) {
        // The finger speeds up as it drags, rising a little like a thumb's arc.
        const u = Math.min(1, Math.max(0, (t - at) / DRAG_TIME));
        drag.set(s.width * DRAG_SHARE * u * u);
        fingerY.set(s.restY - DRAG_RISE * Math.sin((u * Math.PI) / 2));
        if (u >= 1) {
          scripted.set(-1);
          letGo((2 * s.width * DRAG_SHARE) / DRAG_TIME, false);
        }
      }
    }

    // The bump's centre follows the finger, and drifts home when let go.
    const target = dragging.get() ? fingerY.get() : s.restY;
    tabY.set(tabY.get() + (target - tabY.get()) * (1 - Math.exp(-dt * FOLLOW)));
  });

  // Every shared value the liquid depends on is read here, directly, so the shapes redraw
  // whenever one of them changes.
  const geometry = useDerivedValue(() => {
    const state: LiquidState = {
      width: stage.get().width,
      height: stage.get().height,
      restY: stage.get().restY,
      current: current.get(),
      count,
      side: side.get(),
      drag: drag.get(),
      sweep: sweep.get(),
      tab: tab.get(),
      tabY: tabY.get(),
      reduced: reducedMotion,
    };
    return { forward: shapeOf(1, state), back: shapeOf(-1, state), side: state.side, drag: state.drag, sweep: state.sweep, tab: state.tab };
  });

  const forwardPath = useDerivedValue<SkPath>(() => {
    const g = geometry.get().forward;
    return g ? liquid(1, width, height, g.edge, g.reach, g.radius, g.cy) : Skia.Path.Make();
  });
  const backPath = useDerivedValue<SkPath>(() => {
    const g = geometry.get().back;
    return g ? liquid(-1, width, height, g.edge, g.reach, g.radius, g.cy) : Skia.Path.Make();
  });

  const basePicture = useDerivedValue(() => pictures[current.get()] ?? empty);
  const forwardPicture = useDerivedValue(() => pictures[current.get() + 1] ?? empty);
  const backPicture = useDerivedValue(() => pictures[current.get() - 1] ?? empty);

  // The covered page is pushed away from the liquid; the incoming page rides in behind its edge.
  const baseTransform = useDerivedValue(() => {
    if (reducedMotion) return [{ translateX: 0 }];
    const s = side.get();
    return [{ translateX: -s * (drag.get() * 0.1 + width * PUSH * Math.min(1, sweep.get())) }];
  });
  const forwardTransform = useDerivedValue(() => [
    { translateX: reducedMotion ? 0 : width * PARALLAX * (1 - (side.get() === 1 ? sweep.get() : 0)) },
  ]);
  const backTransform = useDerivedValue(() => [
    { translateX: reducedMotion ? 0 : -width * PARALLAX * (1 - (side.get() === -1 ? sweep.get() : 0)) },
  ]);
  const forwardOpacity = useDerivedValue(() => (reducedMotion && side.get() === 1 && sweep.get() > 0 ? sweep.get() : 1));
  const backOpacity = useDerivedValue(() => (reducedMotion && side.get() === -1 && sweep.get() > 0 ? sweep.get() : 1));

  // A chevron on each tab, in the colour of the page it brings, fading as the tab is pulled.
  const forwardChevron = useDerivedValue(() => chevronOn(1, geometry.get().forward, width));
  const backChevron = useDerivedValue(() => chevronOn(-1, geometry.get().back, width));
  const forwardChevronOpacity = useDerivedValue(() => chevronShown(1, geometry.get()));
  const backChevronOpacity = useDerivedValue(() => chevronShown(-1, geometry.get()));
  const forwardInk = useDerivedValue(() => pages[current.get() + 1]?.ink ?? '#000000');
  const backInk = useDerivedValue(() => pages[current.get() - 1]?.ink ?? '#000000');

  const buttonPath = useMemo(() => {
    if (!button) return Skia.Path.Make();
    return Skia.PathBuilder.Make()
      .addRRect(Skia.RRectXY(Skia.XYWHRect(button.x, button.y, button.width, button.height), button.height / 2, button.height / 2))
      .detach();
  }, [button]);
  const buttonShade = useDerivedValue(() => (current.get() === count - 1 && side.get() === 0 ? pressed.get() * 0.22 : 0));

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
    scripted.set(-1);
  }

  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart((event) => {
      touched();
      if (busy.get()) return;
      // A drag can start anywhere: to the left brings the next page, to the right the previous.
      const direction = event.translationX < 0 ? 1 : -1;
      // Catch a tab that is still falling back.
      if (side.get() !== 0 && side.get() !== direction) return;
      if (!grab(direction)) return;
      // Setting the drag stops a spring that was taking it back.
      drag.set(drag.get());
      fingerY.set(event.y);
      track.set({ x: event.translationX, t: elapsed.get(), v: 0 });
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      const s = stage.get();
      const raw = side.get() * -event.translationX;
      // Pulled the wrong way, the tab gives a little; far past the screen it resists.
      drag.set(raw < 0 ? -rubber(-raw, 0) * 0.3 : rubber(raw, s.width * 0.85));
      fingerY.set(Math.min(s.height - TAB_RADIUS * 0.6, Math.max(TAB_RADIUS * 0.6, event.y)));
      // The finger's own speed, smoothed, so a drag that stopped before letting go counts as still.
      const last = track.get();
      const now = elapsed.get();
      const dt = (now - last.t) / 1000;
      // Measured across whole frames: two touches within one frame wait for the next.
      if (dt > 0.004 && event.translationX !== last.x) {
        track.set({ x: event.translationX, t: now, v: last.v + ((event.translationX - last.x) / dt - last.v) * 0.6 });
      }
    })
    .onEnd(() => {
      if (!dragging.get()) return;
      hold.set(USER_HOLD);
      const last = track.get();
      const still = Math.exp(-(elapsed.get() - last.t) / RELEASE_FADE);
      letGo(side.get() * -last.v * still, true);
    })
    .onFinalize(() => {
      if (dragging.get()) letGo(0, true);
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onBegin((event) => {
      if (button && current.get() === count - 1 && event.x >= button.x && event.x <= button.x + button.width && Math.abs(event.y - (button.y + button.height / 2)) < button.height) {
        pressed.set(withTiming(1, { duration: 100 }));
      }
    })
    .onFinalize(() => {
      pressed.set(withTiming(0, { duration: 180 }));
    })
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      const s = stage.get();
      const near = Math.abs(event.y - s.restY) < TAB_RADIUS + 24;
      if (current.get() === count - 1 && button && event.x >= button.x && near) {
        scheduleOnRN(done);
        return;
      }
      // Tapping a tab pours its page in.
      const direction = event.x > s.width - 64 ? 1 : event.x < 64 ? -1 : 0;
      if (direction === 0 || !near || !grab(direction)) return;
      fingerY.set(s.restY);
      drag.set(s.width * 0.12);
      letGo(COMMIT_SPEED * 2, true);
    });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: pages[0]?.color ?? '#000000' }]}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={pages.map((p) => p.title.join(' ')).join('. ')}
        accessibilityActions={[
          { name: 'increment', label: 'Next page' },
          { name: 'decrement', label: 'Previous page' },
        ]}
        onAccessibilityAction={(event) => {
          const direction = event.nativeEvent.actionName === 'increment' ? 1 : -1;
          if (!grab(direction)) return;
          drag.set(width * 0.12);
          letGo(COMMIT_SPEED * 2, true);
        }}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size && pictures.length > 0 && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={baseTransform}>
              <Picture picture={basePicture} />
              <Path path={buttonPath} color="#000000" opacity={buttonShade} />
            </Group>
            <Group clip={backPath} opacity={backOpacity}>
              <Group transform={backTransform}>
                <Picture picture={backPicture} />
              </Group>
            </Group>
            <Group clip={forwardPath} opacity={forwardOpacity}>
              <Group transform={forwardTransform}>
                <Picture picture={forwardPicture} />
              </Group>
            </Group>
            <Path path={backChevron} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color={backInk} opacity={backChevronOpacity} />
            <Path path={forwardChevron} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color={forwardInk} opacity={forwardChevronOpacity} />
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
  },
});
