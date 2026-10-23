import { BackdropBlur, BlurMask, Canvas, Circle, Fill, Group, LinearGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { DOCK_APPS, type DockApp } from './MagnifyDockArt';
import { DockIconArt } from './MagnifyDockIcons';
import { MagnifyDockWallpaper } from './MagnifyDockWallpaper';

type Props = {
  /** Sweeps a virtual finger along the dock, launches an app and rearranges the icons, on a loop. */
  autoPlay?: boolean;
  apps?: DockApp[];
  /** Called with an app's name when it is launched. */
  onLaunch?: (name: string) => void;
};

// The dock. Icons are ICON pt at rest, GAP apart, inside PAD of glass that sits DOCK_LIFT pt
// above the home indicator. Icons are vector art, redrawn at every size, so they stay sharp.
const ICON = 40;
const GAP = 7;
const PAD = 8;
const PITCH = ICON + GAP;
const DOCK_LIFT = 8;
const DOCK_RADIUS = 18;
// Magnification: an icon grows with a cosine bell of its resting centre's distance from the
// focus, up to MAX_SCALE under it and nothing past REACH pt. Grown icons push the others out
// from the dock's centre; on a screen too narrow for that the whole dock is scaled to fit
// within EDGE pt of its sides.
const MAX_SCALE = 1.55;
const REACH = 110;
const EDGE = 6;
// The focus follows the finger on a critically damped spring of natural frequency FOCUS_OMEGA
// (rad/s, ~40 ms behind a moving finger), and the magnification eases in and out on another
// (MAG_OMEGA), letting go when the finger rises more than LEAVE pt above the dock. Both springs are
// stepped with their exact solution, so they stay smooth at any frame rate.
const FOCUS_OMEGA = 50;
const MAG_OMEGA = 15;
const LEAVE = 120;
// The name popover: one pill that glides between icons on a critically damped spring (LABEL_OMEGA),
// resizing to each name while the old name fades out (NAME_OUT s) and the new one in (NAME_FADE s,
// once the old is below NAME_GATE); it appears and leaves with a
// fade, a 4 pt rise and a 0.94 → 1 scale on its own spring (SHOW_OMEGA). It sits LABEL_GAP pt
// above the icon under the focus.
const LABEL_OMEGA = 26;
const SHOW_OMEGA = 18;
const NAME_FADE = 0.07;
const NAME_OUT = 0.035;
const NAME_GATE = 0.25;
const LABEL_GAP = 12;
const LABEL_HEIGHT = 28;
const LABEL_PAD = 12;
// Launching: the icon jumps BOUNCES times, each BOUNCE_TIME s and BOUNCE_DECAY as high as the one
// before, then its running dot fades in.
const BOUNCE_HEIGHT = 22;
const BOUNCE_TIME = 0.42;
const BOUNCES = 2;
const BOUNCE_DECAY = 0.5;
const DOT_AT = 0.35;
// Editing: a press held LONG_PRESS s without moving more than SLOP pt wiggles every icon by
// ±WIGGLE_DEG at WIGGLE_HZ and lifts the pressed one to LIFT_SCALE. Moving it past half a slot
// reorders the others, which spring to their new places (SLOT_STIFFNESS, SLOT_DAMPING per unit mass).
const LONG_PRESS = 0.5;
const SLOP = 8;
const TAP_TIME = 0.4;
const WIGGLE_DEG = 2.4;
const WIGGLE_HZ = 4.2;
const LIFT_SCALE = 1.25;
const SLOT_STIFFNESS = 320;
const SLOT_DAMPING = 30;

// autoPlay (seconds): the finger lands on the first icon, slides to the last and back to Hum,
// lifts and taps it to launch it. Then it holds Wayfare until the dock starts wiggling, carries
// it two places right, puts it down, carries it back and taps Done; Hum quits before the loop
// starts over.
const LOOP = 12;
const SWEEP: [number, number][] = [
  [0.8, 0],
  [3.0, 6],
  [4.2, 2],
];
const LIFT_AT = 4.35;
const TAP_AT = 4.95;
const HOLD_AT = 6.3;
const HOLD_SLOT = 3;
const CARRY: [number, number, number, number][] = [
  // start, end, from slot, to slot
  [6.95, 7.75, 3, 5],
  [8.45, 9.25, 5, 3],
];
const DROP_LAG = 0.15;
const DONE_AT = 9.95;
const QUIT_AT = 10.4;
const USER_HOLD = 4;
// The script reaches up to the seventh slot; with fewer apps the dock does not play by itself.
const SCRIPT_APPS = 7;

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

function smooth(u: number) {
  'worklet';
  const t = clamp(u, 0, 1);
  return t * t * (3 - 2 * t);
}

/**
 * One frame of a critically damped spring of natural frequency `omega`, solved exactly: the value
 * and its speed after `dt` s, heading for `target`.
 */
function critical(x: number, v: number, target: number, omega: number, dt: number) {
  'worklet';
  const d = x - target;
  const e = Math.exp(-omega * dt);
  const k = (v + omega * d) * dt;
  return [target + (d + k) * e, (v - omega * k) * e];
}

function crossed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** 1 while a virtual touch from t0 to t1 is down, fading in and out over 0.2 s around it. */
function touchWindow(c: number, t0: number, t1: number) {
  'worklet';
  return clamp(Math.min((c - t0 + 0.25) / 0.2, (t1 + 0.2 - c) / 0.2), 0, 1);
}

/** Width of the dock at rest, glass included. */
function restingWidth(count: number) {
  'worklet';
  return count * ICON + (count - 1) * GAP + 2 * PAD;
}

/** Centre of slot `slot` at rest, for a dock centred on a screen `width` wide. */
function slotCentre(slot: number, count: number, width: number) {
  'worklet';
  return width / 2 - restingWidth(count) / 2 + PAD + ICON / 2 + slot * PITCH;
}

type Layout = { cx: number[]; scale: number[]; dockWidth: number; fit: number };

/** Where every icon sits and how big it is, for icons resting at `slots` and the focus at `focusX`. */
function layoutDock(slots: number[], focusX: number, mag: number, width: number): Layout {
  'worklet';
  const count = slots.length;
  const base: number[] = [];
  const extra: number[] = [];
  let total = 0;
  for (let i = 0; i < count; i += 1) {
    const c = slotCentre(slots[i], count, width);
    base.push(c);
    const d = Math.abs(c - focusX);
    const bell = d < REACH ? (Math.cos((Math.PI * d) / REACH) + 1) / 2 : 0;
    const e = ICON * (MAX_SCALE - 1) * bell * Math.max(0, mag);
    extra.push(e);
    total += e;
  }
  const full = restingWidth(count) + total;
  const fit = full > 0 ? Math.min(1, (width - 2 * EDGE) / full) : 1;
  const cx: number[] = [];
  const scale: number[] = [];
  for (let i = 0; i < count; i += 1) {
    // Everything grown to this icon's left pushes it right, and the dock stays centred.
    let before = 0;
    for (let j = 0; j < count; j += 1) {
      if (j !== i && (slots[j] < slots[i] || (slots[j] === slots[i] && j < i))) before += extra[j];
    }
    const c = base[i] + before + extra[i] / 2 - total / 2;
    cx.push(width / 2 + (c - width / 2) * fit);
    scale.push((1 + extra[i] / ICON) * fit);
  }
  return { cx, scale, dockWidth: full * fit, fit };
}

/** The icon under `x`, or -1. */
function iconAt(x: number, layout: Layout) {
  'worklet';
  let best = -1;
  let bestD = Infinity;
  for (let i = 0; i < layout.cx.length; i += 1) {
    const d = Math.abs(x - layout.cx[i]);
    if (d < (ICON * layout.scale[i]) / 2 + GAP / 2 && d < bestD) {
      best = i;
      bestD = d;
    }
  }
  return best;
}

/** Height of a launch bounce `age` s after the tap: BOUNCES arches, each lower than the last. */
function bounceAt(age: number) {
  'worklet';
  if (age < 0 || age >= BOUNCES * BOUNCE_TIME) return 0;
  const k = Math.floor(age / BOUNCE_TIME);
  const u = age / BOUNCE_TIME - k;
  return BOUNCE_HEIGHT * Math.pow(BOUNCE_DECAY, k) * 4 * u * (1 - u);
}

/** Where icon `id` is drawn: its centre x, its size, how far it is lifted and turned. */
function iconPose(
  id: number,
  L: Layout,
  width: number,
  t: number,
  fingerX: number,
  dragging: boolean,
  dragY: number,
  wiggle: number,
  launchedAt: number,
  reduced: boolean,
) {
  'worklet';
  const count = L.cx.length;
  const half = restingWidth(count) / 2 - PAD - ICON / 2;
  const cx = dragging ? clamp(fingerX, width / 2 - half, width / 2 + half) : (L.cx[id] ?? 0);
  const size = ICON * (dragging ? LIFT_SCALE * L.fit : (L.scale[id] ?? 1));
  const hop = reduced ? 0 : bounceAt(t - launchedAt);
  const wob = dragging || reduced ? 0 : wiggle;
  const phase = t * WIGGLE_HZ * Math.PI * 2 + id * 1.7;
  const lift = dragging ? dragY - 8 : 0;
  return { cx, size, rise: hop - lift - wob * 0.7 * Math.sin(phase * 0.5), turn: wob * WIGGLE_DEG * Math.sin(phase) };
}

function haptic(kind: 'tick' | 'light' | 'medium') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'light' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type IconProps = {
  id: number;
  app: DockApp;
  /** The copy drawn above the others, shown only while this icon is carried. */
  top: boolean;
  layout: SharedValue<Layout>;
  baseline: number;
  width: number;
  time: SharedValue<number>;
  wiggle: SharedValue<number>;
  fingerX: SharedValue<number>;
  dragId: SharedValue<number>;
  dragY: SharedValue<number>;
  launchedAt: SharedValue<number[]>;
  reduced: boolean;
};

/** One icon on the canvas, its 100 × 100 art scaled to its size and stood on the dock's baseline. */
function DockIconNode({ id, app, top, layout, baseline, width, time, wiggle, fingerX, dragId, dragY, launchedAt, reduced }: IconProps) {
  const transform = useDerivedValue(() => {
    const dragging = dragId.get() === id;
    const p = iconPose(id, layout.get(), width, time.get(), fingerX.get(), dragging, dragY.get(), wiggle.get(), launchedAt.get()[id] ?? -100, reduced);
    const s = Math.max(0.01, p.size / 100);
    return [
      { translateX: p.cx },
      { translateY: baseline - p.size / 2 - p.rise },
      { rotate: (p.turn * Math.PI) / 180 },
      { scale: s },
      { translateX: -50 },
      { translateY: -50 },
    ];
  });
  const opacity = useDerivedValue(() => ((dragId.get() === id) === top ? 1 : 0));
  return (
    <Group transform={transform} opacity={opacity}>
      <DockIconArt app={app} />
    </Group>
  );
}

type DotProps = { id: number; layout: SharedValue<Layout>; y: number; running: SharedValue<number[]> };

/** The running dot under a launched app. */
function RunningDot({ id, layout, y, running }: DotProps) {
  const cx = useDerivedValue(() => layout.get().cx[id] ?? 0);
  const opacity = useDerivedValue(() => running.get()[id] ?? 0);
  return <Circle cx={cx} cy={y} r={2.2} color="rgba(255,255,255,0.92)" opacity={opacity} />;
}

type LabelState = { x: number; xv: number; y: number; w: number; wv: number; show: number; showV: number; names: number[] };

type NameProps = { id: number; name: string; label: SharedValue<LabelState> };

/** One app's name inside the popover, faded in while the popover is over its icon. */
function PopoverName({ id, name, label }: NameProps) {
  const style = useAnimatedStyle(() => {
    const a = label.get().names[id] ?? 0;
    return { opacity: a, transform: [{ translateY: (1 - a) * 3 }] };
  });
  return (
    <Animated.Text style={[styles.labelText, styles.labelName, style]} numberOfLines={1}>
      {name}
    </Animated.Text>
  );
}

type PopoverProps = { apps: DockApp[]; label: SharedValue<LabelState>; width: number };

/**
 * The name popover: a single dark pill with a tip, gliding from icon to icon and resizing to the
 * name it shows while the names cross-fade, so moving along the dock never makes it blink.
 */
function DockPopover({ apps, label, width }: PopoverProps) {
  const boxStyle = useAnimatedStyle(() => {
    const l = label.get();
    const w = Math.max(LABEL_PAD * 2, l.w);
    // The pill stays on the screen; its tip keeps pointing at the icon.
    const cx = clamp(l.x, w / 2 + 8, width - w / 2 - 8);
    return {
      opacity: l.show,
      width: w,
      transform: [{ translateX: cx - w / 2 }, { translateY: l.y + (1 - l.show) * 4 }, { scale: 0.94 + 0.06 * l.show }],
    };
  });
  const tipStyle = useAnimatedStyle(() => {
    const l = label.get();
    const w = Math.max(LABEL_PAD * 2, l.w);
    const cx = clamp(l.x, w / 2 + 8, width - w / 2 - 8);
    return { transform: [{ translateX: clamp(l.x - cx, -w / 2 + 10, w / 2 - 10) }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.label, boxStyle]}>
      <View style={styles.labelPill}>
        {apps.map((app, id) => (
          <PopoverName key={app.name} id={id} name={app.name} label={label} />
        ))}
      </View>
      <Animated.View style={[styles.labelTip, tipStyle]} />
    </Animated.View>
  );
}

/**
 * A desktop dock over a blue-hour mountain wallpaper. Sliding a finger along it swells the icons
 * near the finger on a smooth falloff, pushing their neighbours apart, with the name of the one
 * under the finger floating above it. A tap launches an app with two bounces and a running dot;
 * a long press sets the icons wiggling, and an icon can then be dragged to a new place in the row.
 */
export function MagnifyDock({ autoPlay = false, apps = DOCK_APPS, onLaunch }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const count = apps.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const dockBottom = height - insets.bottom - DOCK_LIFT;
  const baseline = dockBottom - PAD;
  const dockTop = dockBottom - ICON - 2 * PAD;

  const ids = apps.map((_, i) => i);
  const time = useSharedValue(0);
  const widthSV = useSharedValue(0);
  const fingerX = useSharedValue(-1000);
  const fingerY = useSharedValue(0);
  const focusX = useSharedValue(-1000);
  const focusV = useSharedValue(0);
  const mag = useSharedValue(0);
  const magV = useSharedValue(0);
  const magTarget = useSharedValue(0);
  // Resting slot of each app (by id), springing towards its index in `order`.
  const slots = useSharedValue<number[]>(ids);
  const slotSpeed = useSharedValue<number[]>(ids.map(() => 0));
  const order = useSharedValue<number[]>(ids);
  const launchedAt = useSharedValue<number[]>(ids.map(() => -100));
  const runningOn = useSharedValue<number[]>(ids.map(() => 0));
  const running = useSharedValue<number[]>(ids.map(() => 0));
  const editing = useSharedValue(false);
  const wiggle = useSharedValue(0);
  const dragId = useSharedValue(-1);
  const dragY = useSharedValue(0);
  const pressing = useSharedValue(false);
  const moved = useSharedValue(false);
  const inDock = useSharedValue(false);
  const pressAt = useSharedValue(0);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  // autoPlay
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const autoId = useSharedValue(-1);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostAlpha = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  useEffect(() => {
    widthSV.set(width);
  }, [width, widthSV]);
  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const layout = useDerivedValue(() => layoutDock(slots.get(), focusX.get(), mag.get(), widthSV.get()));
  // The popover's state, and the measured width of every name.
  const label = useSharedValue<LabelState>({ x: 0, xv: 0, y: 0, w: 0, wv: 0, show: 0, showV: 0, names: ids.map(() => 0) });
  const nameWidths = useSharedValue<number[]>(apps.map((a) => a.name.length * 7.5));
  const baselineSV = useSharedValue(0);
  const [measured, setMeasured] = useState<number[]>([]);
  useEffect(() => {
    baselineSV.set(baseline);
  }, [baseline, baselineSV]);
  useEffect(() => {
    if (measured.length === count && measured.every((w) => w > 0)) nameWidths.set(measured.slice());
  }, [measured, count, nameWidths]);

  const launched = (id: number, user: boolean) => {
    if (!user) return;
    haptic('light');
    onLaunch?.(apps[id]?.name ?? '');
  };

  const launch = (id: number, user: boolean) => {
    'worklet';
    if (id < 0 || id >= count) return;
    const on = runningOn.get().slice();
    if (on[id] < 1) {
      const at = launchedAt.get().slice();
      at[id] = time.get();
      launchedAt.set(at);
      on[id] = 1;
      runningOn.set(on);
    }
    scheduleOnRN(launched, id, user);
  };

  /** Puts a carried icon down where it is; its slot spring carries it home. */
  const drop = (user: boolean) => {
    'worklet';
    const id = dragId.get();
    if (id < 0) return;
    const W = widthSV.get();
    const half = restingWidth(count) / 2 - PAD - ICON / 2;
    const x = clamp(fingerX.get(), W / 2 - half, W / 2 + half);
    const s = slots.get().slice();
    s[id] = (x - slotCentre(0, count, W)) / PITCH;
    slots.set(s);
    dragId.set(-1);
    if (user) scheduleOnRN(haptic, 'light');
  };

  /** Moves a carried icon with the finger; past half a slot it takes the new place. */
  const carryTo = (x: number, y: number) => {
    'worklet';
    const id = dragId.get();
    if (id < 0) return;
    const dy = y - startY.get();
    dragY.set(dy < -70 ? -70 + (dy + 70) * 0.2 : dy > 12 ? 12 + (dy - 12) * 0.2 : dy);
    const slot = clamp(Math.round((x - slotCentre(0, count, widthSV.get())) / PITCH), 0, count - 1);
    const ord = order.get();
    if (ord.indexOf(id) !== slot) {
      const next: number[] = [];
      for (let k = 0; k < ord.length; k += 1) if (ord[k] !== id) next.push(ord[k]);
      next.splice(slot, 0, id);
      order.set(next);
      if (!looping.get() || hold.get() > 0) scheduleOnRN(haptic, 'tick');
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const t = time.get() + dt;
    time.set(t);

    // A press held still on the dock starts editing and picks the icon up.
    if (pressing.get() && !moved.get() && inDock.get() && !editing.get() && t - pressAt.get() > LONG_PRESS) {
      editing.set(true);
      magTarget.set(0);
      dragId.set(iconAt(fingerX.get(), layout.get()));
      startY.set(fingerY.get());
      dragY.set(0);
      scheduleOnRN(haptic, 'medium');
    }

    // The focus trails the finger on a stiff, critically damped spring; from rest it starts there.
    if ((mag.get() < 0.02 && magTarget.get() > 0) || reduced) {
      focusX.set(fingerX.get());
      focusV.set(0);
    } else {
      const f = critical(focusX.get(), focusV.get(), fingerX.get(), FOCUS_OMEGA, dt);
      focusX.set(f[0]);
      focusV.set(f[1]);
    }
    // The magnification swells and settles on a critically damped spring.
    const mm = critical(mag.get(), magV.get(), magTarget.get(), MAG_OMEGA, dt);
    magV.set(mm[1]);
    mag.set(Math.abs(mm[0] - magTarget.get()) < 0.0005 && Math.abs(mm[1]) < 0.01 ? magTarget.get() : clamp(mm[0], 0, 1));

    // The popover: over the icon under the focus while the dock is swollen, gliding between icons.
    const L = layout.get();
    const over = mag.get() > 0.3 && magTarget.get() > 0 && !editing.get() && dragId.get() < 0 ? iconAt(focusX.get(), L) : -1;
    const was = label.get();
    const lb = { ...was, names: was.names.slice() };
    if (over >= 0) {
      const tx = L.cx[over] ?? 0;
      const ty = baselineSV.get() - ICON * (L.scale[over] ?? 1) - LABEL_GAP - LABEL_HEIGHT - 6;
      const widths = nameWidths.get();
      const tw = (widths[over] ?? 60) + LABEL_PAD * 2;
      if (lb.show < 0.02 || reduced) {
        // Appearing: it starts right over its icon, showing its name.
        lb.x = tx;
        lb.xv = 0;
        lb.y = ty;
        lb.w = tw;
        lb.wv = 0;
        for (let i = 0; i < lb.names.length; i += 1) lb.names[i] = i === over ? 1 : 0;
      } else {
        const nx = critical(lb.x, lb.xv, tx, LABEL_OMEGA, dt);
        lb.x = nx[0];
        lb.xv = nx[1];
        lb.y += (ty - lb.y) * (1 - Math.exp(-dt * LABEL_OMEGA));
        const nw = critical(lb.w, lb.wv, tw, LABEL_OMEGA, dt);
        lb.w = Math.max(LABEL_PAD * 2, nw[0]);
        lb.wv = nw[1];
        // The old name goes first and quickly; the new one comes in once it has mostly gone, so
        // two names never sit on top of each other.
        const out = 1 - Math.exp(-dt / NAME_OUT);
        let left = 0;
        for (let i = 0; i < lb.names.length; i += 1) {
          if (i === over) continue;
          lb.names[i] = lb.names[i] < 0.01 ? 0 : lb.names[i] * (1 - out);
          left = Math.max(left, lb.names[i]);
        }
        if (left < NAME_GATE) lb.names[over] += (1 - lb.names[over]) * (1 - Math.exp(-dt / NAME_FADE));
      }
    }
    const sh = critical(lb.show, lb.showV, over >= 0 ? 1 : 0, SHOW_OMEGA, dt);
    lb.show = reduced ? (over >= 0 ? 1 : 0) : clamp(sh[0], 0, 1);
    lb.showV = reduced ? 0 : sh[1];
    // Hidden and settled, it is left alone, so nothing redraws for it.
    if (over >= 0 || was.show !== 0 || was.showV !== 0) {
      if (lb.show < 0.001 && Math.abs(lb.showV) < 0.01 && over < 0) {
        lb.show = 0;
        lb.showV = 0;
      }
      label.set(lb);
    }

    const wiggleTarget = editing.get() ? 1 : 0;
    wiggle.set(wiggle.get() + (wiggleTarget - wiggle.get()) * (1 - Math.exp(-dt * 10)));
    if (dragId.get() < 0 && dragY.get() !== 0) {
      const y = dragY.get() * Math.exp(-dt * 14);
      dragY.set(Math.abs(y) < 0.1 ? 0 : y);
    }

    // Each icon's slot springs to its place in `order`; a carried icon is left to the finger.
    const ord = order.get();
    const s = slots.get();
    const v = slotSpeed.get();
    let still = true;
    for (let i = 0; i < s.length; i += 1) {
      if (i === dragId.get()) continue;
      if (Math.abs(ord.indexOf(i) - s[i]) > 0.001 || Math.abs(v[i]) > 0.001) still = false;
    }
    if (!still) {
      const ns = s.slice();
      const nv = v.slice();
      for (let i = 0; i < ns.length; i += 1) {
        if (i === dragId.get()) continue;
        const goal = ord.indexOf(i);
        if (reduced) {
          ns[i] = goal;
          nv[i] = 0;
          continue;
        }
        // Small steps, so a long frame cannot make the spring overshoot and fling the icon away.
        const steps = Math.max(1, Math.ceil(dt / 0.004));
        const h = dt / steps;
        for (let k = 0; k < steps; k += 1) {
          nv[i] += (SLOT_STIFFNESS * (goal - ns[i]) - SLOT_DAMPING * nv[i]) * h;
          ns[i] += nv[i] * h;
        }
        if (Math.abs(goal - ns[i]) < 0.001 && Math.abs(nv[i]) < 0.01) {
          ns[i] = goal;
          nv[i] = 0;
        }
      }
      slots.set(ns);
      slotSpeed.set(nv);
    }

    // Running dots fade in once the launch bounce is under way, and out when an app quits.
    const on = runningOn.get();
    const at = launchedAt.get();
    const r = running.get();
    let changed = false;
    const nr = r.slice();
    for (let i = 0; i < nr.length; i += 1) {
      const goal = on[i] > 0 && t - at[i] > DOT_AT ? 1 : 0;
      nr[i] = Math.abs(goal - nr[i]) > 0.002 ? nr[i] + (goal - nr[i]) * (1 - Math.exp(-dt * 9)) : goal;
      if (nr[i] !== r[i]) changed = true;
    }
    if (changed) running.set(nr);

    // autoPlay
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get() && !pressing.get()) {
        // The loop starts over from rest: editing ends, apps quit and the icons go home.
        clock.set(0);
        editing.set(false);
        dragId.set(-1);
        magTarget.set(0);
        const quit: number[] = [];
        const home: number[] = [];
        for (let i = 0; i < count; i += 1) {
          quit.push(0);
          home.push(i);
        }
        runningOn.set(quit);
        order.set(home);
      } else if (hold.get() <= 0 && pressing.get()) {
        hold.set(USER_HOLD);
      }
      return;
    }
    if (!looping.get() || count < SCRIPT_APPS) return;
    const W = widthSV.get();
    if (W === 0) return;
    const before = clock.get();
    let c = before + dt;
    if (c >= LOOP) c -= LOOP;
    clock.set(c);

    const dockY = baseline - ICON / 2;
    // The sweep: eased from stop to stop.
    if (c >= SWEEP[0][0] && c < LIFT_AT) {
      let x = slotCentre(SWEEP[0][1], count, W);
      for (let k = 1; k < SWEEP.length; k += 1) {
        const [t0, s0] = SWEEP[k - 1];
        const [t1, s1] = SWEEP[k];
        if (c >= t0) x = slotCentre(s0 + (s1 - s0) * smooth((c - t0) / (t1 - t0)), count, W);
      }
      fingerX.set(x);
      ghostX.set(x);
      ghostY.set(dockY);
    }
    if (crossed(before, c, SWEEP[0][0])) magTarget.set(1);
    if (crossed(before, c, LIFT_AT)) magTarget.set(0);
    if (crossed(before, c, TAP_AT - 0.3)) {
      ghostX.set(slotCentre(SWEEP[SWEEP.length - 1][1], count, W));
      ghostY.set(dockY);
    }
    if (crossed(before, c, TAP_AT)) {
      const id = order.get()[SWEEP[SWEEP.length - 1][1]];
      autoId.set(id);
      launch(id, false);
    }

    // Editing: a long hold, two carries and Done.
    if (crossed(before, c, HOLD_AT - 0.25)) {
      ghostX.set(slotCentre(HOLD_SLOT, count, W));
      ghostY.set(dockY);
    }
    if (crossed(before, c, HOLD_AT + LONG_PRESS)) {
      editing.set(true);
      fingerX.set(slotCentre(HOLD_SLOT, count, W));
      startY.set(dockY);
      dragId.set(order.get()[HOLD_SLOT]);
    }
    for (let k = 0; k < CARRY.length; k += 1) {
      const [t0, t1, s0, s1] = CARRY[k];
      if (k > 0 && crossed(before, c, t0 - 0.2)) {
        fingerX.set(slotCentre(s0, count, W));
        startY.set(dockY);
        dragId.set(order.get()[s0]);
      }
      if (c >= t0 && c <= t1 + DROP_LAG) {
        const u = smooth((c - t0) / (t1 - t0));
        const x = slotCentre(s0 + (s1 - s0) * u, count, W);
        const y = dockY - 16 * Math.sin(Math.PI * u);
        fingerX.set(x);
        ghostX.set(x);
        ghostY.set(y);
        carryTo(x, y);
      }
      if (crossed(before, c, t1 + DROP_LAG)) drop(false);
    }
    if (crossed(before, c, DONE_AT - 0.3)) {
      ghostX.set(W - 52);
      ghostY.set(insets.top + 31);
    }
    if (crossed(before, c, DONE_AT)) editing.set(false);
    if (crossed(before, c, QUIT_AT) && autoId.get() >= 0) {
      const off = runningOn.get().slice();
      off[autoId.get()] = 0;
      runningOn.set(off);
    }

    // The virtual finger shows while it touches: the sweep, the tap, the hold and carries, Done.
    let alpha = touchWindow(c, SWEEP[0][0], LIFT_AT);
    alpha = Math.max(alpha, touchWindow(c, TAP_AT - 0.08, TAP_AT + 0.08));
    alpha = Math.max(alpha, touchWindow(c, HOLD_AT, CARRY[0][1] + DROP_LAG));
    alpha = Math.max(alpha, touchWindow(c, CARRY[1][0] - 0.2, CARRY[1][1] + DROP_LAG));
    alpha = Math.max(alpha, touchWindow(c, DONE_AT - 0.08, DONE_AT + 0.08));
    ghostAlpha.set(alpha);
    const press = Math.max(clamp(1 - Math.abs(c - TAP_AT) / 0.12, 0, 1), clamp(1 - Math.abs(c - DONE_AT) / 0.12, 0, 1));
    ghostPress.set(c > HOLD_AT && c < HOLD_AT + LONG_PRESS ? smooth((c - HOLD_AT) / 0.2) : press);
  });

  // One pan handles everything, starting at touch down (onBegin), so taps and still long
  // presses reach it too: hover, tap to launch, hold to edit, carry to reorder.
  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      // An icon the demo was carrying is put down where it is, not handed to this finger.
      if (dragId.get() >= 0) drop(false);
      hold.set(USER_HOLD);
      ghostAlpha.set(0);
      pressing.set(true);
      moved.set(false);
      pressAt.set(time.get());
      startX.set(event.x);
      startY.set(event.y);
      fingerX.set(event.x);
      fingerY.set(event.y);
      const W = widthSV.get();
      const dockHalf = layout.get().dockWidth / 2 + 10;
      const onDock = event.y > dockTop - 24 && Math.abs(event.x - W / 2) < dockHalf;
      inDock.set(onDock);
      if (editing.get()) {
        if (onDock) {
          dragId.set(iconAt(event.x, layout.get()));
          dragY.set(0);
        }
      } else if (onDock) {
        magTarget.set(1);
      }
    })
    .onUpdate((event) => {
      fingerX.set(event.x);
      fingerY.set(event.y);
      if (Math.hypot(event.x - startX.get(), event.y - startY.get()) > SLOP) moved.set(true);
      if (dragId.get() >= 0) {
        carryTo(event.x, event.y);
      } else if (!editing.get() && inDock.get()) {
        // Rising well above the dock lets go of it, as a pointer leaving it would.
        magTarget.set(event.y < dockTop - LEAVE ? 0 : 1);
      }
    })
    .onFinalize((_event, success) => {
      const wasPressing = pressing.get();
      pressing.set(false);
      magTarget.set(0);
      hold.set(USER_HOLD);
      if (!wasPressing) return;
      const quick = !moved.get() && time.get() - pressAt.get() < TAP_TIME;
      if (dragId.get() >= 0) {
        drop(true);
        return;
      }
      if (!success && !quick) return;
      if (editing.get()) {
        // A tap anywhere off the icons (or on Done) ends editing.
        if (quick && (!inDock.get() || iconAt(startX.get(), layout.get()) < 0)) {
          editing.set(false);
          scheduleOnRN(haptic, 'tick');
        }
        return;
      }
      if (quick && inDock.get()) launch(iconAt(startX.get(), layout.get()), true);
    });

  // The dock's glass grows with the icons.
  const dockRect = useDerivedValue(() => {
    const L = layout.get();
    const W = widthSV.get();
    return Skia.RRectXY(Skia.XYWHRect(W / 2 - L.dockWidth / 2, dockTop, Math.max(1, L.dockWidth), dockBottom - dockTop), DOCK_RADIUS, DOCK_RADIUS);
  });
  const shadowRect = useDerivedValue(() => {
    const L = layout.get();
    const W = widthSV.get();
    return Skia.RRectXY(Skia.XYWHRect(W / 2 - L.dockWidth / 2 + 6, dockTop + 10, Math.max(1, L.dockWidth - 12), dockBottom - dockTop), DOCK_RADIUS, DOCK_RADIUS);
  });

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostAlpha.get() * 0.85,
    transform: [{ translateX: ghostX.get() - 21 }, { translateY: ghostY.get() - 21 }, { scale: 1 - 0.2 * ghostPress.get() }],
  }));
  const doneStyle = useAnimatedStyle(() => ({
    opacity: wiggle.get(),
    transform: [{ translateY: (1 - wiggle.get()) * -8 }],
  }));

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessibilityLabel={`Dock: ${apps.map((a) => a.name).join(', ')}`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <MagnifyDockWallpaper width={width} height={height} />
            <RoundedRect rect={shadowRect} color="rgba(4,8,18,0.45)">
              <BlurMask blur={12} style="normal" />
            </RoundedRect>
            <BackdropBlur blur={18} clip={dockRect}>
              <Fill color="rgba(214,224,240,0.2)" />
            </BackdropBlur>
            <RoundedRect rect={dockRect} style="stroke" strokeWidth={1}>
              <LinearGradient start={vec(0, dockTop)} end={vec(0, dockBottom)} colors={['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.12)']} />
            </RoundedRect>
            {apps.map((_, id) => (
              <RunningDot key={`dot-${id}`} id={id} layout={layout} y={dockBottom - 3.5} running={running} />
            ))}
            {[false, true].map((top) =>
              apps.map((app, id) => (
                <DockIconNode
                  key={`${top ? 'top' : 'base'}-${app.name}`}
                  id={id}
                  app={app}
                  top={top}
                  layout={layout}
                  baseline={baseline}
                  width={width}
                  time={time}
                  wiggle={wiggle}
                  fingerX={fingerX}
                  dragId={dragId}
                  dragY={dragY}
                  launchedAt={launchedAt}
                  reduced={reduced}
                />
              )),
            )}
          </Canvas>
        )}

        <View pointerEvents="none" style={[styles.desk, { top: insets.top + 22 }]}>
          <Text style={styles.date}>Tuesday 14 October</Text>
          <Text style={styles.time}>9:41</Text>
        </View>

        <Animated.View pointerEvents="none" style={[styles.done, { top: insets.top + 14 }, doneStyle]}>
          <Text style={styles.doneText}>Done</Text>
        </Animated.View>

        {/* The names, measured once off screen so the popover can size itself to each. */}
        <View pointerEvents="none" style={styles.measure}>
          {apps.map((app, id) => (
            <Text
              key={`measure-${app.name}`}
              style={styles.labelText}
              onLayout={(event: LayoutChangeEvent) => {
                const w = event.nativeEvent.layout.width;
                setMeasured((list) => {
                  if (list[id] === w) return list;
                  const next = apps.map((_, i) => list[i] ?? 0);
                  next[id] = w;
                  return next;
                });
              }}
            >
              {app.name}
            </Text>
          ))}
        </View>
        {size && <DockPopover apps={apps} label={label} width={width} />}

        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1d3150', overflow: 'hidden' },
  desk: { position: 'absolute', left: 26, right: 26 },
  date: { fontSize: 17, fontWeight: '600', color: 'rgba(255,255,255,0.88)', letterSpacing: 0.1 },
  time: {
    fontSize: 84,
    fontWeight: '700',
    color: '#ffffff',
    letterSpacing: -3,
    fontVariant: ['tabular-nums'],
    marginTop: -4,
  },
  done: {
    position: 'absolute',
    right: 20,
    height: 34,
    paddingHorizontal: 16,
    borderRadius: 17,
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.5)',
  },
  doneText: { fontSize: 15, fontWeight: '600', color: '#ffffff' },
  label: { position: 'absolute', left: 0, top: 0, alignItems: 'center' },
  labelPill: {
    alignSelf: 'stretch',
    height: LABEL_HEIGHT,
    overflow: 'hidden',
    borderRadius: 8,
    borderCurve: 'continuous',
    justifyContent: 'center',
    backgroundColor: 'rgba(22,26,36,0.86)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.22)',
  },
  labelText: { fontSize: 13, fontWeight: '600', color: '#ffffff' },
  labelName: { position: 'absolute', left: -40, right: -40, top: 0, lineHeight: LABEL_HEIGHT, textAlign: 'center' },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0, flexDirection: 'row', alignItems: 'flex-start' },
  labelTip: {
    width: 0,
    height: 0,
    borderLeftWidth: 6,
    borderRightWidth: 6,
    borderTopWidth: 6,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    borderTopColor: 'rgba(22,26,36,0.86)',
  },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: 'rgba(255,255,255,0.28)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.7)',
  },
});
