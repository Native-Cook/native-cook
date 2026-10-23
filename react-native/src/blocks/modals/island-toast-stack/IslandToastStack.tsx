import { Canvas, Fill, Group, LinearGradient, Path, RadialGradient, Rect, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { type ComponentProps, type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, type PanGesture } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Pushes four toasts out of the island, opens and trims the stack, pulls the island open, then draws everything back in. */
  autoPlay?: boolean;
};

type Kind = 'call' | 'message' | 'upload' | 'timer';
const KINDS: Kind[] = ['call', 'message', 'upload', 'timer'];

const avatar = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=144&h=144&q=80`;
const CALLER = { name: 'Theo Marsh', initials: 'TM', photo: avatar('photo-1587397845856-e6cf49176c70') };
const SENDER = {
  name: 'Rafael Ortiz',
  initials: 'RO',
  body: 'Running 5 min late, grab us the corner table?',
  photo: avatar('photo-1600603406200-5b2a104684ac'),
};
const UPLOAD = { album: 'Lisbon, spring', count: 24 };
const TIMER = { title: 'Sourdough proof', place: 'Kitchen' };

// A dark lock screen: deep ink with a few wide pools of colour, so the frosted cards have something
// to refract. The island stays pure black, like the hardware it sits on.
const BACKGROUND = '#07080e';
const WALL_TOP = '#07080e';
const WALL_BOTTOM = '#0c0b13';
// Pools of light in the wallpaper: centre (share of width, height), radius (share of width), rgb, alpha.
const POOLS: { x: number; y: number; r: number; rgb: string; a: number }[] = [
  { x: 0.92, y: 0.1, r: 1.0, rgb: '58,84,196', a: 0.55 },
  { x: 0.02, y: 0.34, r: 0.9, rgb: '124,58,138', a: 0.42 },
  { x: 0.82, y: 0.62, r: 0.85, rgb: '40,128,150', a: 0.22 },
];
const INK = '#ffffff';
const ISLAND = '#000000';
// The system's notification material: the wallpaper seen through the card under a grey tint, so a
// card never shows the ones behind it. Cards further back in the closed stack are darker, and the
// ones past the third melt away.
const MATERIAL = 'rgba(128,128,138,0.4)';
const BACK_SHADE = 0.38; // black over the third card back (less over the second)
const SECONDARY = 'rgba(235,235,245,0.6)';
const CONTROL = 'rgba(128,128,138,0.4)'; // the header's Show less and clear buttons
const ROUNDED = process.env.EXPO_OS === 'ios' ? 'ui-rounded' : undefined;
const ANDROID = process.env.EXPO_OS === 'android'; // read here: worklets don't see process.env
const HINT = `Tap or pull ${ANDROID ? 'down the top' : 'the island'} · Tap the stack · Swipe a card away`;

// ── Island geometry (pt) ─────────────────────────────────────────────────────────────────────────
// At rest the pill is the hardware Dynamic Island's own frame: 126 × 37 pt, centred half way down
// the top safe area. Older iPhones get a pill of the same size just under the notch; Android, which
// keeps its status bar, has none at rest, and toasts grow out of that spot under it.
const ISLAND_W = 126;
const ISLAND_H = 37;
const SIDE = 10; // gap between an open toast and the screen's edges
const TOAST_H = 99; // the island's own band (beside the camera) plus a 62 pt row under it
const BIG_H = 228;
const MAX_RADIUS = 44; // continuous corners, never more than half the height
const SMOOTHING = 0.6; // corner smoothing of the continuous corners (0 = circular)

// Island springs: a lively one going out, a calmer one coming home. Width leads height a little,
// so the pill stretches sideways before it drops.
const OPEN_W = { duration: 520, dampingRatio: 0.7 };
const OPEN_H = { duration: 600, dampingRatio: 0.74 };
const CLOSE = { duration: 500, dampingRatio: 0.86 };
const CONTENT_DELAY = 210; // ms the shape gets to settle before its content fades in
const CONTENT_IN = 240;
const CONTENT_OUT = 110;
const SHOW_MS = 1900; // a toast stays open this long before it drops into the stack

// Pulling the island down opens it into the big timer card.
const BIG_DRAG = 170; // pt of travel to open it fully
const BIG_COMMIT = 0.42; // share of BIG_DRAG past which a release opens it
const BIG_FLICK = 600; // pt/s
// The island sits in the system's top-edge swipe (Notification Center on iOS, the shade on Android),
// which can take a pull that starts on it. A pull also starts anywhere in this band under the closed
// island, the closed stack's front card included, where the system leaves it alone.
const PULL_REACH = 108; // pt below the closed island
const PULL_START = 10; // pt down before a pull from the band takes over

// ── Stack ────────────────────────────────────────────────────────────────────────────────────────
// Cards are the system's notifications: an icon, a title with the time beside it, and a line or two.
const CARD_H = 80;
const CARD_RADIUS = 22;
const CARD_GAP = 8;
const DECK_GAP = 12; // between the island and the stack
const PEEK = 9; // pt each card behind shows under the one in front
const PEEK_INSET = 11; // pt each card behind is narrower on either side
const ICON = 38;
const HEADER = 46; // room for the header when the stack is open
// A toast leaves the island as a card: the island closes while the card springs down to the front
// of the stack. While they overlap, concave fillets round off the corners where the island meets
// the card; once they part, a neck joins them, thins and snaps at BRIDGE_BREAK pt apart.
const ARRIVE = { duration: 680, dampingRatio: 0.76 };
const SLOT = { duration: 480, dampingRatio: 0.84 };
const EXPAND = { duration: 520, dampingRatio: 0.82 };
const FILLET = 9; // pt, the concave fillets while the island and the card overlap
const BRIDGE_BREAK = 24; // pt of gap at which the neck snaps
const BRIDGE_FLARE = 0.35; // the neck flares out onto the card by this share of the gap
// Clearing: cards rise back into the island one after another and it gulps each one.
const ABSORB_MS = 460;
const ABSORB_STAGGER = 120;
const GULP = { duration: 420, dampingRatio: 0.45, velocity: 320 };
const DISMISS_DISTANCE = 0.32; // share of the width
const DISMISS_FLICK = 900; // pt/s

const UPLOAD_MS = 9000;
const TIMER_SECONDS = 25 * 60;

// autoPlay (seconds): four toasts, the stack opened and trimmed, the island pulled open, all drawn
// back into the island. Everything is at rest again before the loop ends.
const LOOP = 16;
const SCRIPT: [number, (a: Actions) => void][] = [
  [0.6, (a) => a.present('call')],
  [3.0, (a) => a.present('message')],
  [5.4, (a) => a.present('upload')],
  [7.8, (a) => a.present('timer')],
  [10.3, (a) => a.expand(true)],
  [11.4, (a) => a.dismiss('message', 1)],
  [12.3, (a) => a.expand(false)],
  [12.9, (a) => a.openBig()],
  [14.2, (a) => a.closeBig()],
  [14.7, (a) => a.clearAll()],
];
const USER_HOLD = 4000; // ms a touch pauses the script
const REST_MS = 1200; // ms to settle back to rest before the script starts over

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

type Actions = {
  present: (kind: Kind) => void;
  expand: (open: boolean) => void;
  dismiss: (kind: Kind, direction: number) => void;
  openBig: () => void;
  closeBig: () => void;
  clearAll: () => void;
};

type Layout = {
  width: number;
  height: number;
  islandTop: number;
  toastW: number;
  cardW: number;
  deckTop: number;
};

type Motion = {
  vis: SharedValue<number>;
  slot: SharedValue<number>;
  arrive: SharedValue<number>;
  tx: SharedValue<number>;
  absorb: SharedValue<number>;
  content: SharedValue<number>;
};

// ── Worklet helpers (named, defined before use, so the compiler leaves them alone) ───────────────
function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function radiusFor(w: number, h: number) {
  'worklet';
  return Math.max(0, Math.min(MAX_RADIUS, h / 2, w / 2));
}

/**
 * A rounded rectangle with continuous ("squircle") corners, as iOS draws them: each corner eases
 * from the straight edge into a shorter circular arc over (1 + smoothing) × radius. When the sides
 * are too short for that, the smoothing gives way, down to a plain capsule.
 */
function squircle(x: number, y: number, w: number, h: number, radius: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const width = Math.max(0, w);
  const height = Math.max(0, h);
  const budget = Math.min(width, height) / 2;
  const r = Math.max(0, Math.min(radius, budget));
  if (!(r > 0.5) || !Number.isFinite(x + y + width + height)) {
    b.addRect(Skia.XYWHRect(Number.isFinite(x) ? x : 0, Number.isFinite(y) ? y : 0, width, height));
    return b.detach();
  }
  const smooth = Math.max(0, Math.min(SMOOTHING, budget / r - 1));
  const p = Math.min((1 + smooth) * r, budget);
  const arc = (90 * (1 - smooth) * Math.PI) / 180;
  const arcLen = Math.sin(arc / 2) * r * Math.SQRT2;
  const alpha = (Math.PI / 2 - arc) / 2;
  const p34 = r * Math.tan(alpha / 2);
  const beta = (Math.PI / 4) * smooth;
  const c = p34 * Math.cos(beta);
  const d = c * Math.tan(beta);
  const bb = (p - arcLen - c - d) / 3;
  const a = 2 * bb;
  const k = (4 / 3) * Math.tan(arc / 4) * r;
  // One corner in its own frame: u along the edge arriving at the corner, v along the next edge.
  const pts = [
    a, 0, a + bb, 0, a + bb + c, d,
    a + bb + c + Math.cos(beta) * k, d + Math.sin(beta) * k,
    a + bb + c + arcLen - Math.cos(beta + arc) * k, d + arcLen - Math.sin(beta + arc) * k,
    a + bb + c + arcLen, d + arcLen,
    a + bb + c + arcLen + d, d + arcLen + c,
    a + bb + c + arcLen + d, d + arcLen + bb + c,
    p, p,
  ];
  // Corners clockwise from the top right: [corner x, corner y, ux, uy, vx, vy].
  const corners = [
    [x + width, y, 1, 0, 0, 1],
    [x + width, y + height, 0, 1, -1, 0],
    [x, y + height, -1, 0, 0, -1],
    [x, y, 0, -1, 1, 0],
  ];
  b.moveTo(x + p, y);
  for (let i = 0; i < 4; i += 1) {
    const [cx, cy, ux, uy, vx, vy] = corners[i];
    const sx = cx - ux * p;
    const sy = cy - uy * p;
    b.lineTo(sx, sy);
    for (let j = 0; j < 18; j += 6) {
      b.cubicTo(
        sx + pts[j] * ux + pts[j + 1] * vx,
        sy + pts[j] * uy + pts[j + 1] * vy,
        sx + pts[j + 2] * ux + pts[j + 3] * vx,
        sy + pts[j + 2] * uy + pts[j + 3] * vy,
        sx + pts[j + 4] * ux + pts[j + 5] * vx,
        sy + pts[j + 4] * uy + pts[j + 5] * vy,
      );
    }
  }
  b.close();
  return b.detach();
}

/** How far the island reaches either side of its centre at height `y` (its outline is a rounded rect). */
function islandHalfAt(y: number, top: number, half: number, h: number, r: number) {
  'worklet';
  const k = Math.min(Math.max(y, top), top + h);
  const corner = k < top + r ? top + r : k > top + h - r ? top + h - r : k;
  const dy = k - corner;
  return half - r + Math.sqrt(Math.max(0, r * r - dy * dy));
}

/**
 * The shape that joins the island (centred on `cx`, top `top`, half width `ih`, height `hh`,
 * corner radius `ir`) to a card under it (top `ct`, half width `ch`, height `chh`, radius `cr`).
 * Overlapping, concave fillets round off the corners where the island's sides meet the card's top
 * edge. Apart, a neck leaves the island's bottom edge and lands on the card's top edge, both
 * tangentially, pinching in at the waist; it thins to nothing at BRIDGE_BREAK pt apart.
 */
function bridgePath(cx: number, top: number, ih: number, hh: number, ir: number, ct: number, ch: number, chh: number, cr: number, cl: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const bottom = top + hh;
  const gap = ct - bottom;
  const cardFlat = ch - cr; // the card's top edge is straight out to here
  if (!Number.isFinite(gap + cx + ih + ch) || cl > cx || chh <= 0) return b.detach();
  if (gap < 0) {
    // Overlap: fillet each corner where the island's side comes down onto the card's top edge.
    const depth = -gap;
    const side = islandHalfAt(ct, top, ih, hh, ir);
    const f = FILLET * Math.min(1, depth / FILLET, Math.max(0, (cardFlat - side) / FILLET));
    if (f < 0.5 || ct <= top) return b.detach();
    const upper = islandHalfAt(ct - f, top, ih, hh, ir);
    for (let s = -1; s <= 1; s += 2) {
      b.moveTo(cx + s * upper, ct - f);
      b.cubicTo(cx + s * upper, ct - f * 0.45, cx + s * (side + f * 0.45), ct, cx + s * (side + f), ct);
      b.lineTo(cx + s * (side + f), ct + 2);
      b.lineTo(cx + s * (side - 2), ct + 2);
      b.lineTo(cx + s * (upper - 2), ct - f);
      b.close();
    }
    return b.detach();
  }
  if (gap >= BRIDGE_BREAK) return b.detach();
  // Apart: a neck from the island's flat bottom to the card's flat top.
  const p = gap / BRIDGE_BREAK;
  const anchor = Math.max(0, ih - ir); // the island's bottom edge is straight out to here
  const land = Math.min(cardFlat, anchor + FILLET + gap * BRIDGE_FLARE);
  const waist = anchor * Math.pow(1 - p, 1.3) * 0.82;
  if (anchor < 1 || land < 1) return b.detach();
  // Each side is one cubic leaving and landing horizontally; its handles set where the waist is.
  const pull = Math.max(0, ((anchor + land) / 2 - waist) / 0.75);
  b.moveTo(cx - anchor, bottom - 2);
  b.lineTo(cx - anchor, bottom);
  b.cubicTo(cx - anchor + pull, bottom, cx - land + pull, ct, cx - land, ct);
  b.lineTo(cx - land, ct + 2);
  b.lineTo(cx + land, ct + 2);
  b.lineTo(cx + land, ct);
  b.cubicTo(cx + land - pull, ct, cx + anchor - pull, bottom, cx + anchor, bottom);
  b.lineTo(cx + anchor, bottom - 2);
  b.close();
  return b.detach();
}

/** Opacity of a card's colour toward the background `slot` places behind the front (0 = black). */
function slotFade(slot: number) {
  'worklet';
  if (slot <= 1) return slot * 0.2;
  if (slot <= 2) return mix(0.2, 0.42, slot - 1);
  return mix(0.42, 1, clamp01(slot - 2));
}

/** A card's rectangle [x, y, w, h, r, fade] in the stack, closed (e = 0) or open (e = 1). */
function slotRect(slot: number, e: number, off: number, L: Layout): number[] {
  'worklet';
  const s = Math.max(0, slot);
  // Closed, the cards behind are narrower and only their bottom edge shows under the front one.
  const closedW = L.cardW - 2 * PEEK_INSET * Math.min(s, 3);
  const closedY = L.deckTop + off + PEEK * Math.min(s, 2.4);
  const openY = L.deckTop + off + HEADER + s * (CARD_H + CARD_GAP);
  const w = mix(closedW, L.cardW, e);
  return [(L.width - w) / 2, mix(closedY, openY, e), w, CARD_H, CARD_RADIUS, mix(slotFade(s), 0, e)];
}

/** Where a card is drawn: from the open toast to its slot, and from its slot into the island. */
function cardRect(m: { slot: number; arrive: number; absorb: number }, e: number, islandH: number, L: Layout): number[] {
  'worklet';
  const off = Math.max(0, islandH - ISLAND_H);
  const rest = slotRect(m.slot, e, off, L);
  const toast = [(L.width - L.toastW) / 2, L.islandTop, L.toastW, TOAST_H, radiusFor(L.toastW, TOAST_H), 0];
  const out: number[] = [];
  for (let i = 0; i < 6; i += 1) out.push(mix(toast[i], rest[i], m.arrive));
  if (m.absorb > 0) {
    const q = m.absorb;
    const island = [(L.width - ISLAND_W) / 2, L.islandTop, ISLAND_W, ISLAND_H, ISLAND_H / 2, 0];
    for (let i = 0; i < 5; i += 1) out[i] = mix(out[i], island[i], q);
    // It darkens to the island's black well before it gets there.
    out[5] = mix(out[5], 0, Math.min(1, q * 2.5));
  }
  return out;
}

type SymbolName = ComponentProps<typeof SymbolView>['name'];
const symbol = (ios: string, android: string) => ({ ios, android, web: android }) as SymbolName;

function haptic(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

function useMotion(): Motion {
  return {
    vis: useSharedValue(0),
    slot: useSharedValue(0),
    arrive: useSharedValue(1),
    tx: useSharedValue(0),
    absorb: useSharedValue(0),
    content: useSharedValue(0),
  };
}

/**
 * The Dynamic Island as the source of every notification. A black pill sitting exactly on the
 * hardware island morphs into live toasts (an incoming call, a message, an upload with a progress
 * ring, a kitchen timer): it stretches out of the island's frame on a spring with continuous
 * corners, and the content fades in once the shape has settled. Each toast then drops out of the
 * island as a card, pulling a neck that thins and snaps, into a stack below it that falls off in
 * size and tone. Tap the stack to open it into a list, swipe a card away, tap the island for the
 * next toast, pull it down for the timer as a big card; at the end every card rises back into it.
 */
export function IslandToastStack({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  // The block fills the screen, so the window gives the right width on the very first frame and the
  // wallpaper never pops in; the layout corrects it if the block is placed in something narrower.
  const window = useWindowDimensions();
  const [width, setWidth] = useState(window.width);
  const [height, setHeight] = useState(window.height);

  // The island's frame: on an iPhone with a Dynamic Island its centre sits half way down the top
  // safe area; elsewhere (Android keeps its status bar) it sits just under the top inset.
  const hasIsland = process.env.EXPO_OS === 'ios' && insets.top >= 51;
  const islandTop = Math.max(6, hasIsland ? insets.top / 2 + 1 - ISLAND_H / 2 : insets.top + 6);
  const toastW = Math.max(ISLAND_W, Math.min(420, width - SIDE * 2));
  const cardW = Math.max(ISLAND_W, Math.min(410, width - 24));
  const L: Layout = useMemo(
    () => ({ width, height, islandTop, toastW, cardW, deckTop: islandTop + ISLAND_H + DECK_GAP }),
    [width, height, islandTop, toastW, cardW],
  );

  const [stack, setStack] = useState<Kind[]>([]);
  const [expandedState, setExpandedState] = useState(false);
  const [presenting, setPresenting] = useState<Kind | null>(null);
  const [big, setBig] = useState(false);
  const [timerStart, setTimerStart] = useState<number | null>(null);
  const [uploadStart, setUploadStart] = useState<number | null>(null);

  const islandW = useSharedValue(ISLAND_W);
  const islandH = useSharedValue(ISLAND_H);
  const bigOpacity = useSharedValue(0);
  const expanded = useSharedValue(0);
  const upload = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const canPull = useSharedValue(true);
  const bigShared = useSharedValue(false);
  const call = useMotion();
  const message = useMotion();
  const uploadMotion = useMotion();
  const timer = useMotion();
  const motion: Record<Kind, Motion> = { call, message, upload: uploadMotion, timer };

  // The controller: every action the script and the gestures share. Its bookkeeping lives in this
  // closure, and React state only mirrors what has to render.
  const api = useMemo(() => {
    const m: Record<Kind, Motion> = { call, message, upload: uploadMotion, timer };
    const live = {
      stack: [] as Kind[],
      presenting: null as Kind | null,
      big: false,
      expanded: false,
      clearing: false,
      timerStart: null as number | null,
      dead: false,
    };
    const timers: ReturnType<typeof setTimeout>[] = [];
    const script: ReturnType<typeof setTimeout>[] = [];
    const flags = { playing: false };
    const later = (ms: number, fn: () => void) => {
      const id = setTimeout(() => {
        const i = timers.indexOf(id);
        if (i >= 0) timers.splice(i, 1);
        if (!live.dead) fn();
      }, ms);
      timers.push(id);
    };
    const morph = (w: number, h: number, open: boolean, velocity = 0) => {
      if (reduced) {
        const t = { duration: 220, easing: EASE_OUT };
        islandW.set(withTiming(w, t));
        islandH.set(withTiming(h, t));
        return;
      }
      islandW.set(withSpring(w, open ? OPEN_W : CLOSE));
      islandH.set(withSpring(h, { ...(open ? OPEN_H : CLOSE), velocity }));
    };
    const commitStack = (next: Kind[]) => {
      live.stack = next;
      setStack(next);
      next.forEach((kind, i) => {
        m[kind].slot.set(reduced ? withTiming(i, { duration: 200 }) : withSpring(i, SLOT));
      });
    };
    const setPresentingKind = (kind: Kind | null) => {
      live.presenting = kind;
      canPull.set(kind === null && !live.clearing);
      setPresenting(kind);
    };
    const startTimer = () => {
      if (live.timerStart !== null) return;
      live.timerStart = Date.now();
      setTimerStart(live.timerStart);
    };

    const expand = (open: boolean) => {
      if (open && live.stack.length === 0) return;
      live.expanded = open;
      setExpandedState(open);
      expanded.set(reduced ? withTiming(open ? 1 : 0, { duration: 220 }) : withSpring(open ? 1 : 0, EXPAND));
    };

    /** The toast drops out of the island as a card at the front of the stack. */
    const drop = (kind: Kind) => {
      // A tap can drop a toast just before its own timer does: the second drop is a no-op.
      if (live.presenting !== kind || live.stack.includes(kind)) return;
      const k = m[kind];
      k.content.set(withTiming(0, { duration: CONTENT_OUT }));
      later(CONTENT_OUT - 20, () => {
        if (live.presenting !== kind || live.stack.includes(kind)) return;
        k.tx.set(0);
        k.absorb.set(0);
        k.slot.set(0);
        k.arrive.set(0);
        k.vis.set(1);
        k.arrive.set(reduced ? withTiming(1, { duration: 260, easing: EASE_OUT }) : withSpring(1, ARRIVE));
        morph(ISLAND_W, ISLAND_H, false);
        commitStack([kind, ...live.stack.filter((s) => s !== kind)]);
        later(320, () => setPresentingKind(null));
      });
    };

    const present = (kind: Kind, user = false) => {
      if (live.presenting || live.big || live.clearing || live.stack.includes(kind) || !width) return;
      setPresentingKind(kind);
      if (kind === 'timer') startTimer();
      if (kind === 'upload') {
        setUploadStart(Date.now());
        upload.set(0);
        upload.set(withTiming(1, { duration: UPLOAD_MS, easing: Easing.linear }));
      }
      if (user) haptic(Haptics.ImpactFeedbackStyle.Light);
      morph(toastW, TOAST_H, true);
      m[kind].content.set(withDelay(reduced ? 60 : CONTENT_DELAY, withTiming(1, { duration: CONTENT_IN, easing: EASE_OUT })));
      later(SHOW_MS, () => drop(kind));
    };

    const remove = (kind: Kind) => {
      m[kind].vis.set(0);
      m[kind].tx.set(0);
      const next = live.stack.filter((k) => k !== kind);
      commitStack(next);
      if (next.length === 0 && live.expanded) expand(false);
      if (kind === 'upload') setUploadStart(null);
    };

    const dismiss = (kind: Kind, direction: number, user = false, velocity = 0) => {
      if (!live.stack.includes(kind) || live.clearing) return;
      if (user) haptic(Haptics.ImpactFeedbackStyle.Light);
      if (reduced) {
        remove(kind);
        return;
      }
      m[kind].tx.set(
        withSpring(direction * width * 1.2, { duration: 380, dampingRatio: 1, velocity, overshootClamping: true }, (done) => {
          if (done) scheduleOnRN(remove, kind);
        }),
      );
    };

    const openBig = (user = false, velocity = 0) => {
      if (live.presenting || live.clearing || !width) return;
      live.big = true;
      bigShared.set(true);
      setBig(true);
      startTimer();
      if (user) haptic(Haptics.ImpactFeedbackStyle.Medium);
      morph(toastW, BIG_H, true, velocity);
      bigOpacity.set(withDelay(reduced ? 60 : CONTENT_DELAY - 40, withTiming(1, { duration: CONTENT_IN, easing: EASE_OUT })));
    };

    const closeBig = (velocity = 0) => {
      if (!live.big) return;
      live.big = false;
      bigShared.set(false);
      bigOpacity.set(withTiming(0, { duration: CONTENT_OUT }));
      later(CONTENT_OUT - 30, () => morph(ISLAND_W, ISLAND_H, false, velocity));
      later(420, () => {
        if (!live.big) setBig(false);
      });
    };

    const gulp = () => {
      if (reduced) return;
      islandW.set(withSpring(ISLAND_W, GULP));
      islandH.set(withSpring(ISLAND_H, { ...GULP, velocity: GULP.velocity * 0.25 }));
    };

    /** Every card rises back into the island, front first, and the island gulps each one. */
    const clearAll = () => {
      if (live.clearing) return;
      const cards = live.stack.slice();
      if (live.expanded) expand(false);
      if (cards.length === 0) return;
      live.clearing = true;
      canPull.set(false);
      cards.forEach((kind, i) =>
        later(i * ABSORB_STAGGER, () => {
          const k = m[kind];
          if (reduced) {
            k.absorb.set(withTiming(1, { duration: 220 }));
            return;
          }
          k.absorb.set(
            withTiming(1, { duration: ABSORB_MS, easing: EASE_IN_OUT }, (done) => {
              if (done) scheduleOnRN(gulp);
            }),
          );
        }),
      );
      later((cards.length - 1) * ABSORB_STAGGER + ABSORB_MS + 60, () => {
        cards.forEach((kind) => {
          m[kind].vis.set(0);
          m[kind].absorb.set(0);
          m[kind].content.set(0);
        });
        commitStack([]);
        live.clearing = false;
        canPull.set(live.presenting === null);
        live.timerStart = null;
        setTimerStart(null);
        setUploadStart(null);
      });
    };

    const actions: Actions = {
      present: (k) => present(k),
      expand,
      dismiss: (k, d) => dismiss(k, d),
      openBig: () => openBig(),
      closeBig: () => closeBig(),
      clearAll,
    };

    // ── The script ──────────────────────────────────────────────────────────────────────────────
    const stopScript = () => {
      script.splice(0).forEach(clearTimeout);
    };
    const runScript = () => {
      stopScript();
      SCRIPT.forEach(([at, act]) => script.push(setTimeout(() => !live.dead && act(actions), at * 1000)));
      script.push(setTimeout(() => !live.dead && runScript(), LOOP * 1000));
    };
    /** A touch pauses the script; a while later everything settles back to rest and it starts over. */
    const touched = () => {
      if (!flags.playing) return;
      stopScript();
      script.push(
        setTimeout(() => {
          if (live.dead) return;
          closeBig();
          expand(false);
          later(450, clearAll);
          script.push(setTimeout(() => !live.dead && runScript(), 450 + REST_MS));
        }, USER_HOLD),
      );
    };

    return {
      start: () => {
        flags.playing = true;
        runScript();
      },
      stop: () => {
        flags.playing = false;
        stopScript();
      },
      /** Live again after a dispose (effects re-run in development and on fast refresh). */
      revive: () => {
        live.dead = false;
      },
      dispose: () => {
        live.dead = true;
        flags.playing = false;
        stopScript();
        timers.splice(0).forEach(clearTimeout);
      },
      touched,
      tapIsland: () => {
        touched();
        if (live.big) closeBig();
        else if (live.presenting) {
          // A tap on an open toast sends it to the stack straight away.
          if (m[live.presenting].content.get() > 0.5) drop(live.presenting);
        } else {
          const next = KINDS.find((k) => !live.stack.includes(k));
          if (next) present(next, true);
        }
      },
      beginPull: () => {
        touched();
        if (!live.big && !live.presenting && !live.clearing) setBig(true);
      },
      endPull: (open: boolean, velocity: number) => {
        if (live.presenting || live.clearing) return;
        if (open) {
          if (!live.big) openBig(true, velocity);
          else morph(toastW, BIG_H, true, velocity);
          bigOpacity.set(withTiming(1, { duration: 160 }));
        } else {
          live.big = true;
          closeBig(velocity);
        }
      },
      toggleStack: () => {
        touched();
        expand(!live.expanded);
      },
      clearStack: () => {
        touched();
        clearAll();
      },
      swipeAway: (kind: Kind, direction: number, velocity: number) => {
        touched();
        dismiss(kind, direction, true, velocity);
      },
    };
  }, [width, reduced, toastW, islandW, islandH, bigOpacity, expanded, upload, canPull, bigShared, call, message, uploadMotion, timer]);

  // Pending timers die with the controller; the script follows autoPlay, and an action under way
  // finishes even when it stops.
  useEffect(() => {
    api.revive();
    return api.dispose;
  }, [api]);
  useEffect(() => {
    if (!autoPlay || !width) return;
    api.start();
    return api.stop;
  }, [api, autoPlay, width]);

  // ── Island gestures ─────────────────────────────────────────────────────────────────────────────
  const { beginPull, endPull, tapIsland, toggleStack, clearStack, swipeAway, touched } = api;
  /**
   * The pull that opens the island into the big timer: on the island itself (both ways, so the big
   * card can be pushed back up), or down from the band under it. Each place gets its own gesture
   * object; they all drive the same island.
   */
  const makePull = (fromBand: boolean) => {
    const pan = Gesture.Pan()
      .onStart(() => {
        if (!canPull.get()) return;
        dragging.set(true);
        dragFrom.set(bigShared.get() ? 1 : 0);
        scheduleOnRN(beginPull);
      })
      .onUpdate((e) => {
        if (!dragging.get()) return;
        // Pulling down opens the island with the finger; past fully open it stretches on a band.
        const raw = dragFrom.get() + e.translationY / BIG_DRAG;
        const p = raw <= 1 ? Math.max(0, raw) : 1 + 0.22 * Math.tanh((raw - 1) * 2);
        const q = Math.min(1, p);
        islandW.set(mix(ISLAND_W, toastW, Math.min(1, q * 1.6)));
        islandH.set(mix(ISLAND_H, BIG_H, p));
        bigOpacity.set(clamp01((p - 0.5) / 0.4));
      })
      .onEnd((e) => {
        if (!dragging.get()) return;
        const p = dragFrom.get() + e.translationY / BIG_DRAG;
        const v = Number.isFinite(e.velocityY) ? e.velocityY : 0;
        const open = v > BIG_FLICK || (v > -BIG_FLICK && p > (dragFrom.get() > 0.5 ? 1 - BIG_COMMIT : BIG_COMMIT));
        scheduleOnRN(endPull, open, v);
      })
      .onFinalize(() => {
        dragging.set(false);
      });
    // From the band only a downward drag pulls, and a sideways one is left to the cards.
    return fromBand ? pan.maxPointers(1).activeOffsetY(PULL_START).failOffsetX([-16, 16]) : pan.activeOffsetY([-8, 8]);
  };
  const islandPan = makePull(false);
  const bandPull = makePull(true);
  const islandTap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((_e, ok) => {
      if (ok) scheduleOnRN(tapIsland);
    });
  // The island itself sits where the system takes touches, so a tap just under it counts as a tap on it.
  const bandTap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((_e, ok) => {
      if (ok) scheduleOnRN(tapIsland);
    });

  // ── Shapes ──────────────────────────────────────────────────────────────────────────────────────
  // Android has no island: the pill is there only while a toast or the big card grows out of it.
  const islandShown = useDerivedValue(() => (ANDROID ? smoothstep((islandH.get() - ISLAND_H - 4) / 30) : 1));
  const islandPath = useDerivedValue(() => {
    const w = islandW.get();
    const h = islandH.get();
    return squircle((L.width - w) / 2, L.islandTop, w, h, radiusFor(w, h));
  });
  const clipStyle = useAnimatedStyle(() => {
    const w = islandW.get();
    const h = islandH.get();
    return { left: (L.width - w) / 2, width: w, height: h, borderRadius: radiusFor(w, h) };
  });
  const innerStyle = useAnimatedStyle(() => ({ transform: [{ translateX: (islandW.get() - L.toastW) / 2 }] }));
  const headerStyle = useAnimatedStyle(() => ({
    opacity: clamp01(expanded.get() * 1.6 - 0.6),
    transform: [{ translateY: Math.max(0, islandH.get() - ISLAND_H) + (reduced ? 0 : mix(-10, 0, expanded.get())) }],
  }));
  const bigStyle = useAnimatedStyle(() => ({
    opacity: bigOpacity.get(),
    transform: [{ scale: reduced ? 1 : mix(0.96, 1, bigOpacity.get()) }],
  }));

  // Draw order: the stack back to front, then anything leaving or arriving on top of it.
  const drawOrder = [...stack.slice().reverse(), ...KINDS.filter((k) => !stack.includes(k))];

  return (
    <View
      style={styles.screen}
      onLayout={(e: LayoutChangeEvent) => {
        setWidth(e.nativeEvent.layout.width);
        setHeight(e.nativeEvent.layout.height);
      }}
    >
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={toggleStack}
        disabled={!expandedState}
        accessibilityElementsHidden={!expandedState}
        accessibilityLabel="Close the stack"
      />

      {/* One canvas for the wallpaper, the cards and the island, so the cards' backdrop blur frosts
          the wallpaper (and the cards behind them) under it. */}
      {width > 0 && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Wallpaper width={width} height={height} />
          {drawOrder.map((kind) => (
            <CardShape key={kind} m={motion[kind]} expanded={expanded} islandH={islandH} L={L} />
          ))}
          <Group opacity={islandShown}>
            {KINDS.map((kind) => (
              <Bridge key={kind} m={motion[kind]} expanded={expanded} islandW={islandW} islandH={islandH} L={L} reduced={reduced} />
            ))}
            <Path path={islandPath} color={ISLAND} />
          </Group>
        </Canvas>
      )}

      <View style={[styles.footer, { bottom: insets.bottom + 28 }]} pointerEvents="none">
        <Text style={styles.date}>Tuesday, 14 October</Text>
        <Text style={styles.clock}>9:41</Text>
        <Text style={styles.hint}>{HINT}</Text>
      </View>

      {width > 0 && (
        <Animated.View style={[styles.header, { top: L.deckTop, left: (width - cardW) / 2 + 6, width: cardW - 12 }, headerStyle]} pointerEvents={expandedState ? 'box-none' : 'none'}>
          <Text style={styles.headerTitle}>Notifications</Text>
          <View style={styles.headerButtons}>
            <Pressable onPress={toggleStack} style={styles.showLess} hitSlop={8} accessibilityRole="button">
              <Text style={styles.showLessLabel}>Show less</Text>
            </Pressable>
            <Pressable onPress={clearStack} style={styles.clear} hitSlop={8} accessibilityRole="button" accessibilityLabel="Clear all">
              <SymbolView name={symbol('xmark', 'close')} size={12} weight="bold" tintColor={INK} />
            </Pressable>
          </View>
        </Animated.View>
      )}

      {width > 0 && (
        // The band under the closed island, where a pull (or a tap) on the island starts too; the cards lie over it.
        <GestureDetector gesture={Gesture.Exclusive(bandPull, bandTap)}>
          <View
            style={[styles.pullBand, { top: L.islandTop + ISLAND_H, left: (width - toastW) / 2, width: toastW, height: PULL_REACH }]}
            pointerEvents={expandedState ? 'none' : 'auto'}
            accessible={false}
          />
        </GestureDetector>
      )}

      {width > 0 &&
        drawOrder.map((kind) => (
          <CardContent
            key={kind}
            kind={kind}
            m={motion[kind]}
            expanded={expanded}
            islandH={islandH}
            L={L}
            interactive={stack.includes(kind) && (expandedState || stack[0] === kind)}
            pull={!expandedState && stack[0] === kind ? makePull(true) : null}
            onTouch={touched}
            onToggle={toggleStack}
            onDismiss={(direction, velocity) => swipeAway(kind, direction, velocity)}
            label={cardLabel(kind)}
          >
            <DeckBody kind={kind} upload={upload} uploadStart={uploadStart} timerStart={timerStart} />
          </CardContent>
        ))}

      {width > 0 && (
        <GestureDetector gesture={Gesture.Exclusive(islandPan, islandTap)}>
          <Animated.View
            style={[styles.islandClip, { top: L.islandTop }, clipStyle]}
            hitSlop={{ top: L.islandTop, bottom: 14, left: 24, right: 24 }}
            accessible
            accessibilityRole="button"
            accessibilityLabel={big ? 'Timer, open' : presenting ? cardLabel(presenting) : 'Island'}
            accessibilityHint="Tap for the next toast, pull down to open the timer"
          >
            <Animated.View style={[styles.islandInner, { width: toastW }, innerStyle]}>
              {KINDS.map((kind) => (
                <IslandContent key={kind} content={motion[kind].content} reduced={reduced}>
                  <ToastBody kind={kind} upload={upload} uploadStart={uploadStart} timerStart={timerStart} />
                </IslandContent>
              ))}
              {big && (
                <Animated.View style={[styles.bigContent, { height: BIG_H }, bigStyle]}>
                  <BigTimer start={timerStart} />
                </Animated.View>
              )}
            </Animated.View>
          </Animated.View>
        </GestureDetector>
      )}
    </View>
  );
}

function cardLabel(kind: Kind) {
  if (kind === 'call') return `Missed call from ${CALLER.name}`;
  if (kind === 'message') return `${SENDER.name}: ${SENDER.body}`;
  if (kind === 'upload') return `Uploading ${UPLOAD.count} photos to ${UPLOAD.album}`;
  return `${TIMER.title} timer`;
}

type ShapeProps = { m: Motion; expanded: SharedValue<number>; islandH: SharedValue<number>; L: Layout };

function smoothstep(t: number) {
  'worklet';
  const k = clamp01(t);
  return k * k * (3 - 2 * k);
}

const EMPTY_PATH = Skia.PathBuilder.Make().detach();

/**
 * A card's body. Leaving the island it is still the island's black, so the neck between them reads
 * as one shape; as it settles into the stack it clears into the notification material, and rising
 * back into the island it darkens to black again before it is swallowed. The material is the
 * wallpaper itself drawn through the card's shape under a grey tint (the wallpaper is only soft pools
 * of colour, so blurring it would change nothing), which keeps the cards behind out of sight.
 */
function CardShape({ m, expanded, islandH, L }: ShapeProps) {
  const rect = useDerivedValue(() =>
    cardRect({ slot: m.slot.get(), arrive: m.arrive.get(), absorb: m.absorb.get() }, expanded.get(), islandH.get(), L),
  );
  // 0 the island's black … 1 the material.
  const clear = useDerivedValue(() => clamp01((m.arrive.get() - 0.5) / 0.4) * (1 - clamp01(m.absorb.get() * 2.5)));
  // How far back the card sits (0 front … 1 the third card), and how much of it is left past that.
  const depth = useDerivedValue(() => clamp01(rect.get()[5] / 0.42));
  const present = useDerivedValue(() => 1 - smoothstep((rect.get()[5] - 0.42) / 0.58));
  const path = useDerivedValue(() => {
    if (m.vis.get() < 0.5 || m.absorb.get() > 0.995) return EMPTY_PATH;
    const [x, y, w, h, r] = rect.get();
    return squircle(x, y, w, h, r);
  });
  // The island's black lies over the material and thins away as the card clears (mixing the two
  // colours instead would flash a light grey half way).
  const dark = useDerivedValue(() => (1 - clear.get()) * present.get());
  const shade = useDerivedValue(() => BACK_SHADE * depth.get() * present.get());
  // The card turns a little as it is swiped; the wallpaper inside it is turned back so it stays put.
  const turn = useDerivedValue(() => {
    const [x, y, w, h] = rect.get();
    return { cx: x + w / 2, cy: y + h / 2, tx: m.tx.get(), a: (m.tx.get() * 0.035 * Math.PI) / 180 };
  });
  const transform = useDerivedValue(() => {
    const { cx, cy, tx, a } = turn.get();
    return [{ translateX: cx + tx }, { translateY: cy }, { rotate: a }, { translateX: -cx }, { translateY: -cy }];
  });
  const untransform = useDerivedValue(() => {
    const { cx, cy, tx, a } = turn.get();
    return [{ translateX: cx }, { translateY: cy }, { rotate: -a }, { translateX: -cx - tx }, { translateY: -cy }];
  });
  return (
    <Group transform={transform}>
      <Group clip={path}>
        <Group transform={untransform}>
          <Wallpaper width={L.width} height={L.height} />
        </Group>
        <Fill color={MATERIAL} opacity={present} />
        <Fill color={ISLAND} opacity={shade} />
        <Fill color={ISLAND} opacity={dark} />
      </Group>
    </Group>
  );
}

/** The lock screen's wallpaper: deep ink with wide pools of colour, dithered so the dark gradients never band. */
function Wallpaper({ width, height }: { width: number; height: number }) {
  return (
    <Group>
      <Rect x={0} y={0} width={width} height={height} dither>
        <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[WALL_TOP, WALL_BOTTOM]} />
      </Rect>
      {POOLS.map((pool) => (
        <Rect key={pool.rgb} x={0} y={0} width={width} height={height} dither>
          <RadialGradient
            c={vec(pool.x * width, pool.y * height)}
            r={Math.max(1, pool.r * width)}
            colors={[`rgba(${pool.rgb},${pool.a})`, `rgba(${pool.rgb},${pool.a * 0.45})`, `rgba(${pool.rgb},0)`]}
            positions={[0, 0.45, 1]}
          />
        </Rect>
      ))}
    </Group>
  );
}

type BridgeProps = ShapeProps & { islandW: SharedValue<number>; reduced: boolean };

/** The fillets and the neck between the island and a card leaving or rejoining it. */
function Bridge({ m, expanded, islandW, islandH, L, reduced }: BridgeProps) {
  const path = useDerivedValue(() => {
    const moving = !reduced && m.vis.get() > 0 && (m.arrive.get() < 0.999 || m.absorb.get() > 0.001);
    if (!moving) return Skia.PathBuilder.Make().detach();
    const [x, y, w, h, r] = cardRect({ slot: m.slot.get(), arrive: m.arrive.get(), absorb: m.absorb.get() }, expanded.get(), islandH.get(), L);
    const iw = islandW.get();
    const ih = islandH.get();
    return bridgePath(L.width / 2, L.islandTop, iw / 2, ih, radiusFor(iw, ih), y, w / 2, h, r, x);
  });
  return <Path path={path} color={ISLAND} />;
}

type ContentProps = ShapeProps & {
  kind: Kind;
  interactive: boolean;
  /** The island's pull, when this card is the front of the closed stack: a drag down on it opens the big card. */
  pull: PanGesture | null;
  label: string;
  onTouch: () => void;
  onToggle: () => void;
  onDismiss: (direction: number, velocity: number) => void;
  children: ReactNode;
};

/** A card's words and pictures, laid over its shape, and the gestures that move it. */
function CardContent({ m, expanded, islandH, L, interactive, pull, label, onTouch, onToggle, onDismiss, children }: ContentProps) {
  const style = useAnimatedStyle(() => {
    const [x, y, w, h] = cardRect({ slot: m.slot.get(), arrive: m.arrive.get(), absorb: m.absorb.get() }, expanded.get(), islandH.get(), L);
    const tx = m.tx.get();
    const e = expanded.get();
    // A card behind keeps its words hidden until the one in front has slid clear of them, about
    // two thirds of the way open: the cards are solid, so they would show through it.
    const front = mix(1 - clamp01(m.slot.get()), 1, smoothstep((e - 0.62) / 0.36));
    const landed = clamp01((m.arrive.get() - 0.72) / 0.28);
    const leaving = 1 - clamp01(m.absorb.get() * 3);
    const swiped = 1 - Math.min(0.8, (Math.abs(tx) / Math.max(1, L.width)) * 0.9);
    return {
      opacity: m.vis.get() * front * landed * leaving * swiped,
      transform: [
        { translateX: x + w / 2 - L.cardW / 2 + tx },
        { translateY: y + h / 2 - CARD_H / 2 },
        { rotate: `${tx * 0.035}deg` },
        { scale: w / L.cardW },
      ],
    };
  });

  const pan = Gesture.Pan()
    .enabled(interactive)
    .activeOffsetX([-14, 14])
    .failOffsetY([-12, 12])
    .onStart(() => {
      scheduleOnRN(onTouch);
    })
    .onUpdate((e) => {
      m.tx.set(e.translationX);
    })
    .onEnd((e) => {
      const v = Number.isFinite(e.velocityX) ? e.velocityX : 0;
      const far = Math.abs(e.translationX) > L.width * DISMISS_DISTANCE;
      const fast = Math.abs(v) > DISMISS_FLICK;
      const direction = Math.sign(fast ? v : e.translationX);
      if ((far || fast) && direction !== 0 && direction === Math.sign(e.translationX || v)) {
        scheduleOnRN(onDismiss, direction, v);
      } else {
        m.tx.set(withSpring(0, { duration: 420, dampingRatio: 0.72, velocity: v }));
      }
    });
  const tap = Gesture.Tap()
    .enabled(interactive)
    .maxDuration(300)
    .onEnd((_e, ok) => {
      if (ok) scheduleOnRN(onToggle);
    });

  return (
    <GestureDetector gesture={Gesture.Exclusive(pull ? Gesture.Race(pan, pull) : pan, tap)}>
      <Animated.View
        style={[styles.card, { width: L.cardW }, style]}
        pointerEvents={interactive ? 'auto' : 'none'}
        accessible={interactive}
        accessibilityLabel={label}
        accessibilityHint="Tap to open or close the stack, swipe sideways to dismiss"
      >
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

/** One toast's content inside the island: it fades and lifts in once the shape has settled. */
function IslandContent({ content, reduced, children }: { content: SharedValue<number>; reduced: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => ({
    opacity: content.get(),
    transform: [{ translateY: reduced ? 0 : mix(-6, 0, content.get()) }, { scale: reduced ? 1 : mix(0.96, 1, content.get()) }],
  }));
  return (
    <Animated.View style={[styles.toastContent, style]} pointerEvents="none">
      {children}
    </Animated.View>
  );
}

type BodyProps = { kind: Kind; upload: SharedValue<number>; uploadStart: number | null; timerStart: number | null };

function Avatar({ photo, initials, size }: { photo: string; initials: string; size: number }) {
  return (
    <View style={[styles.avatar, { width: size, height: size, borderRadius: size / 2 }]}>
      <Text style={styles.initials}>{initials}</Text>
      <Image source={photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={150} />
    </View>
  );
}

/** A toast as the island shows it: a top band either side of the camera, and a row under it. */
function ToastBody({ kind, upload, uploadStart, timerStart }: BodyProps) {
  let lead: ReactNode;
  let trail: ReactNode;
  let row: ReactNode;
  if (kind === 'call') {
    lead = <Text style={styles.band}>Incoming call</Text>;
    trail = <Text style={styles.band}>mobile</Text>;
    row = (
      <>
        <Avatar photo={CALLER.photo} initials={CALLER.initials} size={46} />
        <Text style={[styles.title, styles.grow]} numberOfLines={1}>
          {CALLER.name}
        </Text>
        <View style={[styles.round, { backgroundColor: '#ff3b30' }]}>
          <SymbolView name={symbol('phone.down.fill', 'call_end')} size={20} tintColor="#ffffff" />
        </View>
        <View style={[styles.round, { backgroundColor: '#30d158' }]}>
          <SymbolView name={symbol('phone.fill', 'call')} size={19} tintColor="#ffffff" />
        </View>
      </>
    );
  } else if (kind === 'message') {
    lead = (
      <View style={styles.bandRow}>
        <SymbolView name={symbol('message.fill', 'chat')} size={13} tintColor="#30d158" />
        <Text style={styles.band}>Message</Text>
      </View>
    );
    trail = <Text style={styles.band}>now</Text>;
    row = (
      <>
        <Avatar photo={SENDER.photo} initials={SENDER.initials} size={46} />
        <View style={styles.grow}>
          <Text style={styles.title} numberOfLines={1}>
            {SENDER.name}
          </Text>
          <Text style={styles.body} numberOfLines={1}>
            {SENDER.body}
          </Text>
        </View>
      </>
    );
  } else if (kind === 'upload') {
    lead = <Text style={styles.band}>Uploading</Text>;
    trail = <UploadCount start={uploadStart} />;
    row = (
      <>
        <UploadRing progress={upload} size={40} />
        <View style={styles.grow}>
          <Text style={styles.title} numberOfLines={1}>
            {UPLOAD.album}
          </Text>
          <Text style={styles.subtle} numberOfLines={1}>
            {UPLOAD.count} photos · Shared album
          </Text>
        </View>
        <UploadPercent start={uploadStart} />
      </>
    );
  } else {
    lead = (
      <View style={styles.bandRow}>
        <SymbolView name={symbol('timer', 'timer')} size={13} tintColor="#ff9f0a" />
        <Text style={styles.band}>Timer</Text>
      </View>
    );
    trail = <Text style={styles.band}>{TIMER.place}</Text>;
    row = (
      <>
        <View style={styles.grow}>
          <Text style={styles.title} numberOfLines={1}>
            {TIMER.title}
          </Text>
          <Text style={styles.subtle} numberOfLines={1}>
            Then shape and bake
          </Text>
        </View>
        <TimerText start={timerStart} style={styles.toastDigits} />
      </>
    );
  }
  return (
    <>
      <View style={styles.bandRowOuter}>
        {lead}
        {trail}
      </View>
      <View style={styles.toastRow}>{row}</View>
    </>
  );
}

/** An app's icon as the system draws it: a white symbol on a squircle of the app's colour. */
function AppIcon({ color, name, tint = '#ffffff' }: { color: string; name: SymbolName; tint?: string }) {
  return (
    <View style={[styles.appIcon, { backgroundColor: color }]}>
      <SymbolView name={name} size={ICON * 0.5} weight="semibold" tintColor={tint} />
    </View>
  );
}

/** A person's picture with the app's icon tucked into its corner, as the system shows a call or a message. */
function ContactIcon({ photo, initials, color, name }: { photo: string; initials: string; color: string; name: SymbolName }) {
  return (
    <View style={styles.contactIcon}>
      <Avatar photo={photo} initials={initials} size={ICON} />
      <View style={[styles.badge, { backgroundColor: color }]}>
        <SymbolView name={name} size={10} weight="bold" tintColor="#ffffff" />
      </View>
    </View>
  );
}

/** A notification's words: the title with the time beside it, and a line or two under it. */
function Note({ icon, title, lines = 1, children }: { icon: ReactNode; title: string; lines?: number; children: ReactNode }) {
  return (
    <View style={styles.note}>
      {icon}
      <View style={styles.noteText}>
        <View style={styles.noteHead}>
          <Text style={styles.noteTitle} numberOfLines={1}>
            {title}
          </Text>
          <Text style={styles.noteTime}>now</Text>
        </View>
        <Text style={styles.noteBody} numberOfLines={lines}>
          {children}
        </Text>
      </View>
    </View>
  );
}

/** A toast as it rests in the stack: one of the system's notifications. */
function DeckBody({ kind, uploadStart, timerStart }: BodyProps) {
  if (kind === 'call') {
    return (
      <Note
        icon={<ContactIcon photo={CALLER.photo} initials={CALLER.initials} color="#34c759" name={symbol('phone.fill', 'call')} />}
        title={CALLER.name}
      >
        Missed call · mobile
      </Note>
    );
  }
  if (kind === 'message') {
    return (
      <Note
        icon={<ContactIcon photo={SENDER.photo} initials={SENDER.initials} color="#34c759" name={symbol('message.fill', 'chat')} />}
        title={SENDER.name}
        lines={2}
      >
        {SENDER.body}
      </Note>
    );
  }
  if (kind === 'upload') {
    return (
      <Note icon={<AppIcon color="#0a84ff" name={symbol('icloud.and.arrow.up.fill', 'cloud_upload')} />} title={UPLOAD.album}>
        <UploadLine start={uploadStart} />
      </Note>
    );
  }
  return (
    <Note icon={<AppIcon color="#1c1c1e" name={symbol('timer', 'timer')} tint="#ff9f0a" />} title={TIMER.title}>
      <TimerLine start={timerStart} />
    </Note>
  );
}

/** Seconds since `start`, re-read every `every` ms while mounted (a clock, not an animation). */
function useSince(start: number | null, every: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (start === null) return;
    const id = setInterval(() => setNow(Date.now()), every);
    return () => clearInterval(id);
  }, [every, start]);
  return start === null ? 0 : Math.max(0, (now - start) / 1000);
}

function formatTime(seconds: number) {
  const s = Math.max(0, Math.ceil(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function TimerText({ start, style }: { start: number | null; style: object }) {
  const left = TIMER_SECONDS - useSince(start, 250);
  return <Text style={style}>{formatTime(left)}</Text>;
}

function UploadPercent({ start }: { start: number | null }) {
  const p = Math.min(1, (useSince(start, 120) * 1000) / UPLOAD_MS);
  return <Text style={styles.percent}>{p >= 1 ? 'Done' : `${Math.floor(p * 100)}%`}</Text>;
}

function UploadCount({ start }: { start: number | null }) {
  const p = Math.min(1, (useSince(start, 200) * 1000) / UPLOAD_MS);
  return <Text style={styles.band}>{`${Math.floor(p * UPLOAD.count)} of ${UPLOAD.count}`}</Text>;
}

function UploadLine({ start }: { start: number | null }) {
  const p = Math.min(1, (useSince(start, 200) * 1000) / UPLOAD_MS);
  return p >= 1 ? `${UPLOAD.count} photos uploaded` : `Uploading ${Math.floor(p * UPLOAD.count)} of ${UPLOAD.count} photos`;
}

function TimerLine({ start }: { start: number | null }) {
  const left = TIMER_SECONDS - useSince(start, 250);
  return (
    <>
      <Text style={styles.noteDigits}>{formatTime(left)}</Text> left · {TIMER.place}
    </>
  );
}

const RING_STROKE = 3.5;

/** The upload's progress, a ring that closes clockwise from the top. */
function UploadRing({ progress, size }: { progress: SharedValue<number>; size: number }) {
  const path = useMemo(
    () =>
      Skia.PathBuilder.Make()
        .addArc({ x: RING_STROKE / 2, y: RING_STROKE / 2, width: size - RING_STROKE, height: size - RING_STROKE }, -90, 359.9)
        .build(),
    [size],
  );
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Path path={path} style="stroke" strokeWidth={RING_STROKE} color="rgba(255,255,255,0.16)" />
        <Path path={path} style="stroke" strokeWidth={RING_STROKE} strokeCap="round" color="#0a84ff" start={0} end={progress} />
      </Canvas>
      <SymbolView name={symbol('arrow.up', 'arrow_upward')} size={size * 0.38} tintColor="#0a84ff" />
    </View>
  );
}

/** The island pulled open: the kitchen timer as a big live card. */
function BigTimer({ start }: { start: number | null }) {
  const since = useSince(start, 250);
  const left = TIMER_SECONDS - since;
  const share = Math.max(0, Math.min(1, left / TIMER_SECONDS));
  return (
    <>
      {/* The band beside the camera only has room for a short word either side of it. */}
      <View style={styles.bandRowOuter}>
        <View style={styles.bandRow}>
          <SymbolView name={symbol('timer', 'timer')} size={13} tintColor="#ff9f0a" />
          <Text style={styles.band}>Timer</Text>
        </View>
        <Text style={styles.band}>{TIMER.place}</Text>
      </View>
      <View style={styles.big}>
        <Text style={styles.bigDigits}>{formatTime(left)}</Text>
        <View style={styles.track}>
          <View style={[styles.fill, { width: `${share * 100}%` }]} />
        </View>
        <View style={styles.bigButtons}>
          <View style={[styles.bigButton, { backgroundColor: 'rgba(255,255,255,0.14)' }]}>
            <Text style={styles.bigButtonLabel}>Pause</Text>
          </View>
          <View style={[styles.bigButton, { backgroundColor: '#ff9f0a' }]}>
            <Text style={[styles.bigButtonLabel, { color: '#1a1000' }]}>+5 min</Text>
          </View>
        </View>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: BACKGROUND,
  },
  footer: {
    position: 'absolute',
    left: 26,
    right: 26,
  },
  date: {
    fontSize: 17,
    fontWeight: '600',
    color: 'rgba(255,255,255,0.7)',
  },
  clock: {
    fontFamily: ROUNDED,
    fontSize: 96,
    lineHeight: 104,
    fontWeight: '700',
    letterSpacing: -3,
    color: INK,
    fontVariant: ['tabular-nums'],
  },
  hint: {
    marginTop: 6,
    fontSize: 13,
    fontWeight: '500',
    color: 'rgba(255,255,255,0.46)',
  },
  pullBand: {
    position: 'absolute',
  },
  islandClip: {
    position: 'absolute',
    overflow: 'hidden',
    borderCurve: 'continuous',
  },
  islandInner: {
    position: 'absolute',
    top: 0,
    left: 0,
  },
  toastContent: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: TOAST_H,
  },
  bigContent: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  bandRowOuter: {
    height: ISLAND_H,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 24,
  },
  bandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  band: {
    fontSize: 13,
    fontWeight: '600',
    color: 'rgba(235,235,245,0.62)',
    fontVariant: ['tabular-nums'],
  },
  toastRow: {
    height: TOAST_H - ISLAND_H - 10,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    gap: 12,
  },
  header: {
    position: 'absolute',
    height: 34,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
    color: INK,
  },
  headerButtons: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  showLess: {
    paddingHorizontal: 13,
    height: 30,
    borderRadius: 15,
    justifyContent: 'center',
    backgroundColor: CONTROL,
  },
  clear: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: CONTROL,
  },
  showLessLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: INK,
  },
  card: {
    position: 'absolute',
    top: 0,
    left: 0,
    height: CARD_H,
  },
  row: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    gap: 12,
  },
  note: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    gap: 11,
  },
  noteText: {
    flex: 1,
    gap: 1,
  },
  noteHead: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 8,
  },
  noteTitle: {
    flex: 1,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    color: INK,
  },
  noteTime: {
    fontSize: 13,
    color: SECONDARY,
  },
  noteBody: {
    fontSize: 15,
    lineHeight: 19,
    color: INK,
  },
  noteDigits: {
    fontWeight: '600',
    color: '#ff9f0a',
    fontVariant: ['tabular-nums'],
  },
  appIcon: {
    width: ICON,
    height: ICON,
    borderRadius: ICON * 0.225,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  contactIcon: {
    width: ICON,
    height: ICON,
  },
  badge: {
    position: 'absolute',
    right: -3,
    bottom: -3,
    width: 17,
    height: 17,
    borderRadius: 4,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  grow: {
    flex: 1,
    gap: 1,
  },
  avatar: {
    overflow: 'hidden',
    backgroundColor: '#3a3a3c',
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    fontSize: 15,
    fontWeight: '600',
    color: 'rgba(255,255,255,0.8)',
  },
  title: {
    fontSize: 17,
    fontWeight: '600',
    color: '#ffffff',
    letterSpacing: -0.2,
  },
  subtle: {
    fontSize: 13,
    fontWeight: '500',
    color: SECONDARY,
  },
  body: {
    fontSize: 14,
    fontWeight: '400',
    color: 'rgba(235,235,245,0.82)',
  },
  round: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
  },
  toastDigits: {
    fontFamily: ROUNDED,
    fontSize: 36,
    fontWeight: '600',
    color: '#ff9f0a',
    fontVariant: ['tabular-nums'],
    letterSpacing: -0.5,
  },
  percent: {
    fontSize: 17,
    fontWeight: '600',
    color: '#0a84ff',
    fontVariant: ['tabular-nums'],
  },
  big: {
    flex: 1,
    paddingHorizontal: 22,
    paddingBottom: 20,
  },
  bigDigits: {
    fontFamily: ROUNDED,
    fontSize: 72,
    lineHeight: 84,
    fontWeight: '600',
    letterSpacing: -1.5,
    color: '#ff9f0a',
    fontVariant: ['tabular-nums'],
  },
  track: {
    height: 5,
    borderRadius: 2.5,
    backgroundColor: 'rgba(255,159,10,0.2)',
    overflow: 'hidden',
  },
  fill: {
    height: 5,
    borderRadius: 2.5,
    backgroundColor: '#ff9f0a',
  },
  bigButtons: {
    marginTop: 18,
    flexDirection: 'row',
    gap: 10,
  },
  bigButton: {
    flex: 1,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bigButtonLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ffffff',
  },
});
