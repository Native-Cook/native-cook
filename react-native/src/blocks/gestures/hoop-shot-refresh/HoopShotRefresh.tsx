import { Ionicons } from '@expo/vector-icons';
import { Canvas, PaintStyle, Picture, Skia, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

/** One drill card of the Courtside feed. */
export type HoopShotRefreshDrill = { id: number; title: string; coach: string; focus: string; minutes: number; tint: [string, string] };

type Props = {
  drills?: HoopShotRefreshDrill[];
  /** Pulls, shoots and closes by itself every LOOP seconds. */
  autoPlay?: boolean;
  title?: string;
  sectionLabel?: string;
  /** Called when a pull commits to a refresh. */
  onRefresh?: () => void;
};

/** Four cards: the whole feed fits above the tab bar at 393×852 pt, so no card is ever cut. */
export const DRILLS: HoopShotRefreshDrill[] = [
  { id: 0, title: 'Crossover Ladder', coach: 'Coach Dana Reyes', focus: 'Ball handling', minutes: 12, tint: ['#FF7A9E', '#E8457A'] },
  { id: 1, title: 'Corner Three Reps', coach: 'Coach Malik Osei', focus: 'Shooting', minutes: 18, tint: ['#6C6CFF', '#3A3AD6'] },
  { id: 2, title: 'Mikan Drill', coach: 'Coach Priya Nair', focus: 'Finishing', minutes: 8, tint: ['#FFB347', '#FF8A3D'] },
  { id: 3, title: 'Defensive Slides', coach: 'Coach Tom Varga', focus: 'Footwork', minutes: 10, tint: ['#3CC8A8', '#1FA88A'] },
];

// Palette, the same hex values as the SwiftUI reference.
const BAR = '#E8457A';
const BALL = '#F0558A';
const BALL_LIGHT = '#FF8DB1';
const BALL_DARK = '#D63A6F';
const SEAM = '#C83468';
const COURT = '#2B2B30';
const COURT_EDGE = '#1F1F24';
const CARD = '#FFFFFF';
const CARD_TINT = '#FCE4EC';
const INK = '#1E1E22';
const MUTE = '#7A7A82';
const LABEL = '#9A9AA2';
const TAB = '#F2F2F4';
const TAB_LINE = '#E2E2E6';
const TAB_ICON = '#B0B0B6';
const BOARD = '#F4F4F6';
const BOARD_SQUARE = '#D6D6DC';
const BRACKET = '#C9C9D0';
const NET = 'rgba(255,255,255,0.85)';

// Sizes in pt, the same numbers as the SwiftUI reference.
const BAR_CONTENT = 56;
const TAB_CONTENT = 56;
const MARGIN = 20;
const CARD_HEIGHT = 118;
const CARD_GAP = 14;
const CARD_RADIUS = 18;
const LABEL_TOP = 18;
const FIRST_CARD = 44;
const THRESHOLD = 150;
const RUBBER_LIMIT = 420;
const HOOP_TOP = 14;
const BOARD_WIDTH = 72;
const BOARD_HEIGHT = 44;
const RIM_GAP = 8;
const RIM_RX = 28;
const RIM_RY = 7;
const NET_LENGTH = 40;
const NET_STRETCH = 28;
const CHARGE_MIN = 24;
const CHARGE_MAX = 140;
const FLIGHT_RADIUS = 15;
const DROP_RADIUS = 9;
const DROP_DEPTH = 100;

/** The self-playing loop, in seconds: rest, pull, shot, close, rest. */
export const LOOP = 8;
const PULL_FROM = 1.0;
const PULL_TO = 2.5;
const RELEASE_AT = 2.8;
// The shot sequence, in seconds after the release.
const FLIGHT_AT = 0.2;
const FLIGHT_FOR = 0.8;
const DROP_AT = 1.0;
const DROP_FOR = 0.6;
const NET_AT = 1.1;
const NET_FOR = 0.45;
const CLOSE_AT = 2.0;
const CLOSE_FOR = 0.6;
const FLASH_AT = 2.15;
const FLASH_FOR = 0.35;
const STAGGER = 0.06;
const POP_FOR = 0.6;
const DONE = 3.1;
/** The "Updated just now" note stays this long after the feed has closed. */
const NOTE_FOR = 1.5;
const NOTE_FADE = 0.3;
const REDUCED_CLOSE_AT = 0.1;
const REDUCED_DONE = 0.9;
/** Seconds after the last touch at which the screen eases back to rest and the loop restarts. */
const RESUME = 2.0;
/** The ease back to rest: the feed closes as after a shot, the cards still flash, the note shows briefly. */
const SETTLE_FOR = 1.0;
const SETTLE_FADE = 0.3;
/** A drag starts once the finger has travelled this far; before that a touch only holds the screen. */
const DRAG_SLOP = 8;

// Modes: what the frame is derived from.
const MODE_LOOP = 0;
const MODE_PULLING = 1;
const MODE_CANCEL = 2;
const MODE_SHOT = 3;
/** Easing what the screen showed at the resume deadline back to rest, before the loop restarts. */
const MODE_SETTLE = 4;

type Ball = { x: number; y: number; r: number; opacity: number; seam: number; front: boolean };
/** Everything one frame draws, derived from a time (the loop's or a touch's). */
type Frame = { pull: number; ball: Ball | null; net: number; flash: number; refresh: number; hint: number; note: number };
const REST: Frame = { pull: 0, ball: null, net: 0, flash: 0, refresh: -1, hint: 0, note: 0 };

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutQuad(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x);
}
function easeInQuad(x: number) {
  'worklet';
  return x * x;
}
function easeOutBack(x: number) {
  'worklet';
  return 1 + 2.2 * Math.pow(x - 1, 3) + 1.2 * Math.pow(x - 1, 2);
}
/** Finger travel to feed offset: direct at first, giving less the further it goes. */
function rubber(dy: number) {
  'worklet';
  const d = Math.max(0, dy) * 0.6;
  return d / (1 + d / RUBBER_LIMIT);
}

/** The ball as it charges with the pull, rising from behind the tab bar. */
function charge(pull: number, cx: number, tabTop: number): Ball {
  'worklet';
  const k = clamp01(pull / THRESHOLD);
  const r = CHARGE_MIN + (CHARGE_MAX - CHARGE_MIN) * k;
  return { x: cx, y: tabTop + 30 - r * 0.15, r, opacity: 1, seam: 0.3 + k * 0.4, front: true };
}

/** The loop's pull: out to the threshold with a small overshoot, then held until the release. */
function loopPull(t: number) {
  'worklet';
  const u = seg(t, PULL_FROM, PULL_TO);
  if (u < 0.77) return 162 * easeInOutCubic(u / 0.77);
  return 162 - 12 * easeOutQuad((u - 0.77) / 0.23);
}

/** The shot, `s` seconds after the release from a feed offset `p0`. */
function sequence(s: number, p0: number, cx: number, cavityTop: number, tabTop: number, reduced: boolean): Frame {
  'worklet';
  if (reduced) {
    const close = easeInOutCubic(seg(s, REDUCED_CLOSE_AT, REDUCED_CLOSE_AT + 0.3));
    return {
      pull: p0 * (1 - close),
      ball: null,
      net: 0,
      flash: 0,
      refresh: s >= REDUCED_CLOSE_AT + 0.05 ? s - (REDUCED_CLOSE_AT + 0.05) : -1,
      hint: 0,
      note: seg(s, REDUCED_CLOSE_AT, REDUCED_CLOSE_AT + NOTE_FADE) * (1 - seg(s, REDUCED_DONE + NOTE_FOR, REDUCED_DONE + NOTE_FOR + NOTE_FADE)),
    };
  }
  // The feed settles on the threshold and holds there while the ball is in the air.
  const held = THRESHOLD + (p0 - THRESHOLD) * Math.exp(-s * 10);
  const close = easeInOutCubic(seg(s, CLOSE_AT, CLOSE_AT + CLOSE_FOR));
  const pull = held * (1 - close);
  const hint = 1 - seg(s, 0, FLIGHT_AT);

  const rimY = cavityTop + HOOP_TOP + BOARD_HEIGHT + RIM_GAP + RIM_RY;
  const apexY = rimY - 14;
  let ball: Ball | null = null;
  if (s < FLIGHT_AT) {
    // Anticipation: a short dip before the launch.
    const b = charge(THRESHOLD, cx, tabTop);
    ball = { x: b.x, y: b.y + 6 * Math.sin((Math.PI * s) / FLIGHT_AT), r: b.r, opacity: 1, seam: b.seam, front: true };
  } else if (s < DROP_AT) {
    const u = seg(s, FLIGHT_AT, FLIGHT_AT + FLIGHT_FOR);
    const start = charge(THRESHOLD, cx, tabTop);
    const y = start.y + (apexY - start.y) * easeOutQuad(u);
    const r = start.r + (FLIGHT_RADIUS - start.r) * easeOutQuad(u);
    ball = { x: cx + 26 * Math.sin(Math.PI * u), y, r, opacity: 1, seam: 0.7 + u * 2.2, front: true };
  } else if (s < DROP_AT + DROP_FOR) {
    const d = seg(s, DROP_AT, DROP_AT + DROP_FOR);
    const y = apexY + DROP_DEPTH * easeInQuad(d);
    const r = FLIGHT_RADIUS + (DROP_RADIUS - FLIGHT_RADIUS) * d;
    ball = { x: cx, y, r, opacity: 1 - seg(d, 0.7, 1), seam: 2.9 + d, front: false };
  }
  // The net stretches as the ball passes, then wobbles back.
  let net = 0;
  if (s >= NET_AT && s < NET_AT + NET_FOR) {
    net = Math.sin(Math.PI * seg(s, NET_AT, NET_AT + NET_FOR));
  } else if (s >= NET_AT + NET_FOR) {
    const e = s - (NET_AT + NET_FOR);
    net = 0.12 * Math.exp(-8 * e) * Math.sin(2 * Math.PI * e * 5);
  }
  return {
    pull,
    ball,
    net,
    flash: seg(s, NET_AT, NET_AT + 0.3),
    refresh: s >= FLASH_AT ? s - FLASH_AT : -1,
    hint,
    note: seg(s, FLASH_AT, FLASH_AT + NOTE_FADE) * (1 - seg(s, DONE + NOTE_FOR, DONE + NOTE_FOR + NOTE_FADE)),
  };
}

function loopFrame(t: number, cx: number, cavityTop: number, tabTop: number): Frame {
  'worklet';
  if (t < PULL_FROM) return REST;
  if (t < RELEASE_AT) {
    const pull = loopPull(t);
    return { pull, ball: charge(pull, cx, tabTop), net: 0, flash: 0, refresh: -1, hint: seg(pull, 110, 140), note: 0 };
  }
  const s = t - RELEASE_AT;
  // The same sequence as a hand shot, note included; rest again at 7.7 s.
  if (s >= DONE + NOTE_FOR + NOTE_FADE) return REST;
  return sequence(s, THRESHOLD, cx, cavityTop, tabTop, false);
}

/** The feed springing back from `p0` after a release short of the threshold: critically damped, at rest after about 0.6 s. */
function cancelFrame(e: number, p0: number, cx: number, tabTop: number, reduced: boolean): Frame {
  'worklet';
  const k = 9;
  const pull = p0 * (1 + k * e) * Math.exp(-k * e);
  if (pull < 0.5) return REST;
  return { pull, ball: reduced ? null : charge(pull, cx, tabTop), net: 0, flash: 0, refresh: -1, hint: seg(pull, 110, 140), note: 0 };
}

/**
 * Whatever the screen showed at the resume deadline (`f0`), `e` seconds later, on its way back to rest.
 * The feed closes as after a shot; the ball, net, hint and note fade. A shot that had not refreshed
 * the feed yet (`refreshing`) still does: the cards flash as the feed closes and the note shows briefly.
 */
function settleFrame(e: number, f0: Frame, refreshing: boolean): Frame {
  'worklet';
  const fade = 1 - seg(e, 0, SETTLE_FADE);
  let pull = f0.pull * (1 - easeInOutCubic(seg(e, 0, CLOSE_FOR)));
  if (pull < 0.5) pull = 0;
  const b = f0.ball;
  const ball: Ball | null = b && fade > 0 ? { x: b.x, y: b.y, r: b.r, opacity: b.opacity * fade, seam: b.seam, front: b.front } : null;
  let refresh = -1;
  let note = f0.note * fade;
  if (f0.refresh >= 0) {
    refresh = f0.refresh + e;
  } else if (refreshing) {
    const flashAt = FLASH_AT - CLOSE_AT;
    refresh = e >= flashAt ? e - flashAt : -1;
    note = seg(e, flashAt, flashAt + NOTE_FADE) * (1 - seg(e, SETTLE_FOR - NOTE_FADE, SETTLE_FOR));
  }
  return { pull, ball, net: f0.net * fade, flash: 0, refresh, hint: f0.hint * fade, note };
}

function strokePaint(color: string, width: number, alpha = 1) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  paint.setColor(Skia.Color(color));
  if (alpha < 1) paint.setAlphaf(alpha);
  return paint;
}
function fillPaint(color: string, alpha = 1) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  if (alpha < 1) paint.setAlphaf(alpha);
  return paint;
}
function ovalRect(cx: number, cy: number, rx: number, ry: number) {
  'worklet';
  return Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2);
}

function drawBall(canvas: SkCanvas, ball: Ball) {
  'worklet';
  if (ball.opacity <= 0) return;
  const r = ball.r;
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: ball.x - 0.35 * r, y: ball.y - 0.35 * r },
      1.5 * r,
      [Skia.Color(BALL_LIGHT), Skia.Color(BALL), Skia.Color(BALL_DARK)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  body.setAlphaf(ball.opacity);
  canvas.drawCircle(ball.x, ball.y, r, body);
  const w = Math.min(3, Math.max(1.2, r / 45));
  canvas.save();
  canvas.clipPath(Skia.PathBuilder.Make().addOval(ovalRect(ball.x, ball.y, r - 0.5, r - 0.5)).detach(), 1, true);
  canvas.translate(ball.x, ball.y);
  canvas.rotate((ball.seam * 180) / Math.PI, 0, 0);
  const seam = strokePaint(SEAM, w, 0.8 * ball.opacity);
  canvas.drawOval(ovalRect(0, 0, 0.42 * r, r), seam);
  canvas.drawOval(ovalRect(0, 0, r, 0.42 * r), seam);
  canvas.restore();
  canvas.drawCircle(ball.x, ball.y, r - w / 2, strokePaint(BALL_DARK, w, 0.5 * ball.opacity));
}

function drawNet(canvas: SkCanvas, cx: number, rimY: number, s: number) {
  'worklet';
  const bottomY = rimY + NET_LENGTH + NET_STRETCH * s;
  const bottomRx = 15 + 5 * s;
  const bottomRy = 4;
  const strands = Skia.PathBuilder.Make();
  for (let i = 0; i < 7; i += 1) {
    const a = (Math.PI * i) / 6;
    const topX = cx + RIM_RX * Math.cos(a);
    const topY = rimY + RIM_RY * Math.sin(a);
    const botX = cx + bottomRx * Math.cos(a);
    const botY = bottomY + bottomRy * Math.sin(a);
    const midX = (topX + botX) / 2;
    const midY = (topY + botY) / 2;
    const inward = (4 * (1 - s) - 6 * s) * Math.cos(a);
    strands.moveTo(topX, topY);
    strands.quadTo(midX - inward, midY, botX, botY);
  }
  canvas.drawPath(strands.detach(), strokePaint(NET, 1.5));
  const rings = [0.38, 0.72, 1];
  for (let i = 0; i < 3; i += 1) {
    const f = rings[i];
    const rx = RIM_RX + (bottomRx - RIM_RX) * f;
    const ry = RIM_RY + (bottomRy - RIM_RY) * f;
    const y = rimY + (bottomY - rimY) * f;
    canvas.drawOval(ovalRect(cx, y, rx, ry), strokePaint(NET, f === 1 ? 1.5 : 1.2));
  }
}

/** The court's vignette, the hoop and the ball falling away: everything behind the feed. */
function drawCourt(canvas: SkCanvas, f: Frame, width: number, height: number, cx: number, cavityTop: number) {
  'worklet';
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cavityTop + 90 },
      width * 0.9,
      [Skia.Color('rgba(43,43,48,0)'), Skia.Color(COURT_EDGE)],
      [0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), vignette);
  if (f.pull <= 0.5) return;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, cavityTop, width, f.pull), 1, true);
  const boardTop = cavityTop + HOOP_TOP;
  const rimY = boardTop + BOARD_HEIGHT + RIM_GAP + RIM_RY;
  const rim = ovalRect(cx, rimY, RIM_RX, RIM_RY);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - BOARD_WIDTH / 2, boardTop, BOARD_WIDTH, BOARD_HEIGHT), 6, 6), fillPaint(BOARD));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 15, boardTop + 14, 30, 22), 2, 2), strokePaint(BOARD_SQUARE, 2));
  canvas.drawRect(Skia.XYWHRect(cx - 5, boardTop + BOARD_HEIGHT - 2, 10, RIM_GAP + 2), fillPaint(BRACKET));
  // Back half of the rim.
  canvas.drawArc(rim, 180, 180, false, strokePaint(BALL_DARK, 4));
  if (f.flash > 0 && f.flash < 1) {
    const k = 1 + 0.6 * easeOutQuad(f.flash);
    canvas.save();
    canvas.translate(cx, rimY);
    canvas.scale(k, k);
    canvas.translate(-cx, -rimY);
    canvas.drawOval(rim, strokePaint('#FFFFFF', 1.5 / k, 0.6 * (1 - f.flash)));
    canvas.restore();
  }
  if (f.ball && !f.ball.front) drawBall(canvas, f.ball);
  // Front half of the rim and the net.
  canvas.drawArc(rim, 0, 180, false, strokePaint(BAR, 4));
  drawNet(canvas, cx, rimY, f.net);
  canvas.restore();
}

function sameRest(a: Frame, b: Frame) {
  'worklet';
  return a.pull === 0 && b.pull === 0 && a.ball === null && b.ball === null && a.note === 0 && b.note === 0 && a.refresh < 0 && b.refresh < 0;
}

function haptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A basketball feed whose pull-to-refresh charges a ball and, on release, shoots it through the hoop. */
export function HoopShotRefresh({ drills = DRILLS, autoPlay = false, title = 'Courtside', sectionLabel = 'Latest drills', onRefresh }: Props) {
  const reducedMotion = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const width = laidOut?.width ?? window.width;
  const height = laidOut?.height ?? window.height;
  const barHeight = insets.top + BAR_CONTENT;
  const tabTop = height - insets.bottom - TAB_CONTENT;
  const cx = width / 2;
  const [touches, setTouches] = useState(0);

  const frame = useSharedValue<Frame>(REST);
  const mode = useSharedValue(MODE_LOOP);
  const modeP = useSharedValue(0);
  const modeAt = useSharedValue(0);
  const settleFrom = useSharedValue<Frame>(REST);
  const settleRefreshing = useSharedValue(false);
  const origin = useSharedValue(0);
  const originPending = useSharedValue(true);
  const clock = useSharedValue(0);
  /** A finger is on the screen: the loop never restarts while it is. */
  const touchDown = useSharedValue(false);
  const touchId = useSharedValue(-1);
  const touchStartY = useSharedValue(0);
  /** The finger has travelled past the slop and owns the pull. */
  const dragging = useSharedValue(false);
  const base = useSharedValue(0);
  const hapticFired = useSharedValue(false);

  /** Counts touch ends (lifts and cancellations); each one restarts the resume deadline. */
  const touched = () => setTouches((n) => n + 1);
  const refreshed = () => onRefresh?.();

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    clock.set(now);
    if (originPending.get()) {
      originPending.set(false);
      origin.set(now);
    }
    let next: Frame;
    const m = mode.get();
    if (m === MODE_LOOP) {
      next = !autoPlay || reducedMotion ? REST : loopFrame(Math.max(0, now - origin.get()) % LOOP, cx, barHeight, tabTop);
    } else if (m === MODE_PULLING) {
      const p = modeP.get();
      next = { pull: p, ball: reducedMotion ? null : charge(p, cx, tabTop), net: 0, flash: 0, refresh: -1, hint: seg(p, 110, 140), note: 0 };
    } else if (m === MODE_CANCEL) {
      next = cancelFrame(now - modeAt.get(), modeP.get(), cx, tabTop, reducedMotion);
    } else if (m === MODE_SETTLE) {
      const e = now - modeAt.get();
      next = e >= SETTLE_FOR ? REST : settleFrame(e, settleFrom.get(), settleRefreshing.get());
    } else {
      const s = now - modeAt.get();
      next = s >= (reducedMotion ? REDUCED_DONE : DONE) + NOTE_FOR + NOTE_FADE ? REST : sequence(s, modeP.get(), cx, barHeight, tabTop, reducedMotion);
    }
    if (!sameRest(frame.get(), next)) frame.set(next);
  });

  // Two seconds after the last touch the screen eases back to rest (at once if it is there already),
  // and the loop starts again from rest. A finger landing in the meantime holds everything.
  useEffect(() => {
    if (touches === 0) return;
    let settleTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = setTimeout(() => {
      if (touchDown.get()) return;
      const f0 = frame.get();
      if (sameRest(f0, REST)) {
        originPending.set(true);
        mode.set(MODE_LOOP);
        return;
      }
      settleFrom.set(f0);
      settleRefreshing.set(mode.get() === MODE_SHOT);
      modeAt.set(clock.get());
      mode.set(MODE_SETTLE);
      settleTimer = setTimeout(() => {
        if (touchDown.get()) return;
        originPending.set(true);
        mode.set(MODE_LOOP);
      }, SETTLE_FOR * 1000);
    }, RESUME * 1000);
    return () => {
      clearTimeout(timer);
      if (settleTimer !== undefined) clearTimeout(settleTimer);
    };
  }, [touches, frame, mode, modeAt, clock, settleFrom, settleRefreshing, touchDown, originPending]);

  // The finger lifts or the touch is cancelled. Only a lift past the threshold refreshes; a cancelled
  // touch springs back without a shot.
  const finishTouch = (cancelled: boolean) => {
    'worklet';
    touchDown.set(false);
    touchId.set(-1);
    if (mode.get() === MODE_PULLING) {
      const p = modeP.get();
      modeAt.set(clock.get());
      if (!cancelled && dragging.get() && p >= THRESHOLD) {
        mode.set(MODE_SHOT);
        scheduleOnRN(refreshed);
      } else {
        mode.set(MODE_CANCEL);
      }
    }
    dragging.set(false);
    scheduleOnRN(touched);
  };

  // A manual gesture, not a Pan: a tap or a hold must pause the loop too, and a cancelled touch must
  // be told apart from a lift, which only onTouchesCancelled does. Only the first finger counts.
  const touch = Gesture.Manual()
    .shouldCancelWhenOutside(false)
    .onTouchesDown((event, manager) => {
      manager.activate();
      if (touchDown.get() || event.changedTouches.length === 0) return;
      const first = event.changedTouches[0];
      touchDown.set(true);
      touchId.set(first.id);
      touchStartY.set(first.y);
      dragging.set(false);
      hapticFired.set(false);
      // The finger takes the screen over where it is, so nothing jumps: a pull in progress is held,
      // a shot in the air keeps flying, and a settle hands its offset to the finger.
      const m = mode.get();
      if (m === MODE_LOOP) {
        const now = clock.get();
        const t = !autoPlay || reducedMotion || originPending.get() ? 0 : Math.max(0, now - origin.get()) % LOOP;
        if (!autoPlay || reducedMotion || t < RELEASE_AT) {
          modeP.set(autoPlay && !reducedMotion ? loopPull(t) : 0);
          mode.set(MODE_PULLING);
        } else {
          modeP.set(THRESHOLD);
          modeAt.set(now - (t - RELEASE_AT));
          mode.set(MODE_SHOT);
        }
      } else if (m === MODE_SETTLE) {
        modeP.set(frame.get().pull);
        mode.set(MODE_PULLING);
      }
    })
    .onTouchesMove((event) => {
      if (!touchDown.get()) return;
      const id = touchId.get();
      let found = false;
      let y = 0;
      for (let i = 0; i < event.changedTouches.length; i += 1) {
        if (event.changedTouches[i].id === id) {
          found = true;
          y = event.changedTouches[i].y;
        }
      }
      if (!found) return;
      const dy = y - touchStartY.get();
      if (!dragging.get()) {
        if (Math.abs(dy) < DRAG_SLOP) return;
        dragging.set(true);
        base.set(frame.get().pull);
      }
      const p = base.get() + rubber(dy);
      mode.set(MODE_PULLING);
      modeP.set(p);
      if (p >= THRESHOLD && !hapticFired.get()) {
        hapticFired.set(true);
        scheduleOnRN(haptic);
      }
    })
    .onTouchesUp((event, manager) => {
      const id = touchId.get();
      let lifted = false;
      for (let i = 0; i < event.changedTouches.length; i += 1) {
        if (event.changedTouches[i].id === id) lifted = true;
      }
      if (lifted && touchDown.get()) finishTouch(false);
      if (event.numberOfTouches === 0) manager.end();
    })
    .onTouchesCancelled((_event, manager) => {
      if (touchDown.get()) finishTouch(true);
      manager.end();
    })
    .onFinalize(() => {
      // However the gesture ends, no finger stays counted as down.
      if (touchDown.get()) finishTouch(true);
    });

  const court = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    drawCourt(canvas, frame.get(), width, height, cx, barHeight);
    return recorder.finishRecordingAsPicture();
  });
  const front = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    const ball = frame.get().ball;
    if (ball && ball.front) drawBall(canvas, ball);
    return recorder.finishRecordingAsPicture();
  });

  const feedStyle = useAnimatedStyle(() => ({ transform: [{ translateY: barHeight + frame.get().pull }] }));
  const scrimStyle = useAnimatedStyle(() => ({ opacity: 0.35 * seg(frame.get().pull, 0, THRESHOLD) }));
  const cavityStyle = useAnimatedStyle(() => ({ height: Math.max(0, frame.get().pull) }));
  const pullHintStyle = useAnimatedStyle(() => {
    const f = frame.get();
    return { opacity: f.pull >= THRESHOLD ? 0 : 0.55 * f.hint };
  });
  const releaseHintStyle = useAnimatedStyle(() => {
    const f = frame.get();
    return { opacity: f.pull >= THRESHOLD ? 0.55 * f.hint : 0 };
  });
  const todayStyle = useAnimatedStyle(() => ({ opacity: 1 - frame.get().note }));
  const noteStyle = useAnimatedStyle(() => ({ opacity: frame.get().note }));
  const refresh = useDerivedValue(() => frame.get().refresh);

  const onLayout = (event: LayoutChangeEvent) => setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  const refreshByHand = () => {
    dragging.set(false);
    modeP.set(THRESHOLD);
    modeAt.set(clock.get());
    mode.set(MODE_SHOT);
    refreshed();
    touched();
  };
  const rimY = barHeight + HOOP_TOP + BOARD_HEIGHT + RIM_GAP + RIM_RY;
  const hintTop = rimY + NET_LENGTH + 22 - barHeight - 8;

  return (
    <GestureDetector gesture={touch}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="light" />
        <Canvas style={styles.fill} pointerEvents="none">
          <Picture picture={court} />
        </Canvas>
        {/* The hints sit in the cavity the pull opens, clipped to it. */}
        <Animated.View pointerEvents="none" style={[styles.cavity, { top: barHeight, width }, cavityStyle]}>
          <Animated.Text style={[styles.hint, { top: hintTop }, pullHintStyle]}>Pull to shoot</Animated.Text>
          <Animated.Text style={[styles.hint, { top: hintTop }, releaseHintStyle]}>Release to refresh</Animated.Text>
        </Animated.View>

        <View style={styles.feedClip} pointerEvents="none">
          <Animated.View style={[styles.feed, { width }, feedStyle]}>
            <View style={styles.sectionRow}>
              <Text style={styles.sectionLabel}>{sectionLabel.toUpperCase()}</Text>
              {/* The one focusable control: a screen reader refreshes from here without a pull. */}
              <View
                style={styles.sectionRight}
                accessible
                accessibilityRole="button"
                accessibilityLabel="Refresh"
                accessibilityHint="Reloads the latest drills"
                accessibilityActions={[{ name: 'activate' }]}
                onAccessibilityAction={refreshByHand}
              >
                <Animated.Text style={[styles.today, todayStyle]}>Today</Animated.Text>
                <Animated.Text style={[styles.note, noteStyle]} numberOfLines={1}>Updated just now</Animated.Text>
              </View>
            </View>
            <View style={{ height: FIRST_CARD - LABEL_TOP - 14 }} />
            {drills.map((drill, index) => (
              <DrillCard key={drill.id} drill={drill} index={index} refresh={refresh} reducedMotion={reducedMotion} />
            ))}
          </Animated.View>
        </View>

        {/* The feed steps back while the court is open, so the shot owns the screen. */}
        <Animated.View pointerEvents="none" style={[styles.scrim, { top: barHeight, height: Math.max(0, tabTop - barHeight) }, scrimStyle]} />

        {/* The ball charging and flying: in front of the feed. */}
        <Canvas style={styles.fill} pointerEvents="none">
          <Picture picture={front} />
        </Canvas>

        <View pointerEvents="none" style={[styles.tabBar, { top: tabTop, paddingBottom: insets.bottom }]}>
          <View style={styles.tabLine} />
          <View style={styles.tabRow}>
            <TabItem icon="home" label="Home" active />
            <TabItem icon="fitness" label="Drills" />
            <TabItem icon="stats-chart" label="Stats" />
            <TabItem icon="person" label="Me" />
          </View>
        </View>

        <View pointerEvents="none" style={[styles.topBar, { height: barHeight, paddingTop: insets.top }]}>
          <View style={styles.topRow}>
            <View style={styles.avatar}>
              <View style={styles.avatarDot} />
            </View>
            <View style={styles.flex} />
            <View style={styles.iconDisc}>
              <Ionicons name="notifications" size={14} color="#FFFFFF" />
            </View>
            <View style={styles.iconDisc}>
              <Ionicons name="search" size={14} color="#FFFFFF" />
            </View>
          </View>
          <View style={[styles.titleRow, { top: insets.top }]}>
            <Text style={styles.title}>{title}</Text>
          </View>
        </View>
      </View>
    </GestureDetector>
  );
}

function DrillCard({ drill, index, refresh, reducedMotion }: { drill: HoopShotRefreshDrill; index: number; refresh: SharedValue<number>; reducedMotion: boolean }) {
  const cardStyle = useAnimatedStyle(() => {
    const at = refresh.get() - index * STAGGER;
    const pop = reducedMotion || at < 0 ? 1 : 0.94 + 0.06 * easeOutBack(clamp01(at / POP_FOR));
    const fade = reducedMotion && at >= 0 ? 0.55 + 0.45 * clamp01(at / 0.3) : 1;
    return { transform: [{ scale: pop }], opacity: fade };
  });
  const tintStyle = useAnimatedStyle(() => {
    const at = refresh.get() - index * STAGGER;
    const flash = reducedMotion || at < 0 ? 0 : Math.sin(Math.PI * clamp01(at / FLASH_FOR));
    return { opacity: flash * 0.8 };
  });
  return (
    <Animated.View style={[styles.card, cardStyle]}>
      <Animated.View pointerEvents="none" style={[styles.cardTint, tintStyle]} />
      <LinearGradient colors={drill.tint} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.thumb}>
        <Ionicons name="basketball" size={22} color="rgba(255,255,255,0.9)" />
      </LinearGradient>
      <View style={styles.cardText}>
        <Text style={styles.cardTitle} numberOfLines={1}>{drill.title}</Text>
        <Text style={styles.cardCoach} numberOfLines={1}>{drill.coach}</Text>
        <View style={styles.flex} />
        <View style={styles.cardRow}>
          <View style={styles.chip}>
            <Text style={styles.chipText}>{drill.minutes} min</Text>
          </View>
          <Text style={styles.focus} numberOfLines={1}>{drill.focus}</Text>
        </View>
      </View>
    </Animated.View>
  );
}

function TabItem({ icon, label, active = false }: { icon: 'home' | 'fitness' | 'stats-chart' | 'person'; label: string; active?: boolean }) {
  const color = active ? BAR : TAB_ICON;
  return (
    <View style={styles.tabItem}>
      <View style={styles.tabIcon}>
        <Ionicons name={icon} size={22} color={color} />
      </View>
      <Text style={[styles.tabLabel, { color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COURT, overflow: 'hidden' },
  flex: { flex: 1 },
  fill: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 },
  cavity: { position: 'absolute', left: 0, overflow: 'hidden' },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#FFFFFF', fontSize: 12, fontWeight: '500', lineHeight: 16 },
  feedClip: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, overflow: 'hidden' },
  feed: { position: 'absolute', left: 0, top: 0 },
  sectionRow: { flexDirection: 'row', alignItems: 'center', height: 14, marginTop: LABEL_TOP, paddingHorizontal: MARGIN + 2 },
  sectionLabel: { color: LABEL, fontSize: 12, fontWeight: '700', letterSpacing: 1.2 },
  sectionRight: { marginLeft: 'auto', alignItems: 'flex-end', justifyContent: 'center' },
  today: { color: LABEL, fontSize: 12, fontWeight: '500' },
  note: { position: 'absolute', right: 0, width: 160, textAlign: 'right', color: BAR, fontSize: 12, fontWeight: '600' },
  card: {
    flexDirection: 'row', alignItems: 'flex-start', gap: 16, height: CARD_HEIGHT, marginHorizontal: MARGIN, marginBottom: CARD_GAP, padding: 18,
    backgroundColor: CARD, borderRadius: CARD_RADIUS, borderCurve: 'continuous', boxShadow: '0 6px 14px rgba(0,0,0,0.18)',
  },
  cardTint: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: CARD_TINT, borderRadius: CARD_RADIUS, borderCurve: 'continuous' },
  thumb: { width: 52, height: 52, borderRadius: 14, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  cardText: { flex: 1, alignSelf: 'stretch', gap: 4 },
  cardTitle: { color: INK, fontSize: 17, fontWeight: '600', lineHeight: 22 },
  cardCoach: { color: MUTE, fontSize: 13, lineHeight: 18 },
  cardRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  chip: { height: 22, borderRadius: 11, paddingHorizontal: 9, backgroundColor: CARD_TINT, alignItems: 'center', justifyContent: 'center' },
  chipText: { color: BAR, fontSize: 11, fontWeight: '700' },
  focus: { color: MUTE, fontSize: 12 },
  scrim: { position: 'absolute', left: 0, right: 0, backgroundColor: COURT_EDGE },
  tabBar: { position: 'absolute', left: 0, right: 0, backgroundColor: TAB },
  tabLine: { height: 1, backgroundColor: TAB_LINE },
  tabRow: { flexDirection: 'row', height: TAB_CONTENT - 1, alignItems: 'center' },
  tabItem: { flex: 1, alignItems: 'center', gap: 4 },
  tabIcon: { height: 24, alignItems: 'center', justifyContent: 'center' },
  tabLabel: { fontSize: 10, fontWeight: '600' },
  topBar: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: BAR },
  topRow: { flexDirection: 'row', alignItems: 'center', gap: 10, height: BAR_CONTENT, paddingHorizontal: MARGIN },
  avatar: { width: 28, height: 28, borderRadius: 14, backgroundColor: '#2A2A2E', alignItems: 'center', justifyContent: 'center' },
  avatarDot: { width: 12, height: 12, borderRadius: 6, backgroundColor: '#F7D7E2' },
  iconDisc: { width: 28, height: 28, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center' },
  titleRow: { position: 'absolute', left: 0, right: 0, height: BAR_CONTENT, alignItems: 'center', justifyContent: 'center' },
  title: { color: '#FFFFFF', fontSize: 22, fontWeight: '600', letterSpacing: -0.3 },
});
