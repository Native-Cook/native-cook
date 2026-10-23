import { BlurStyle, Canvas, Picture, type SkTypeface, Skia, createPicture, useTypeface } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Falling Word Stickers: a studio's landing screen whose call to action, "your next big project starts
 here", is six pastel word stickers that drop from above the screen, bounce and settle into a pile
 at the bottom, tilted and overlapping.

 Layout (pt, W × H the whole screen): the words are Inter Tight Medium at S = 0.13 W (51 pt on a
 393 pt screen), "big" at 1.4 S; a sticker is the word's advance plus 0.24 em on each side, 1.12 em
 tall, corners 0.1 em, the baseline 0.262 em below its centre (half-way between cap height and the
 descender of "p", read from the font). The pile rests on a floor 20 pt above the bottom safe area:
 "your", "next" and "big" on the floor (centres at 0.165, 0.48 and 0.82 W, kept 8 pt inside the
 screen), "project" on the first two (0.36 W, 1.06 S up), "starts" on "big" (0.80 W, 1.52 S up) and
 "here" on "starts" (0.70 W, 2.58 S up), tilted −5°, 3°, −3°, −6°, 5° and −12°.

 Timeline of the self-playing loop (LOOP = 6 s), gravity g = 2600 pt/s²:
   0.00–1.20  The pile at rest (the first frame).
   1.20–1.95  The floor gives way: from the top sticker down, 0.05 s apart, each pops up at 250 pt/s
              and falls out of the bottom of the screen under g, spinning at its own rate (30–70°/s)
              and drifting sideways.
   2.00–2.70  The words drop in reading order, 0.14 s apart, from just above the screen: free fall
              under g to their place, sliding in from their own side offset (20–50 pt, ease-out
              cubic) and turning from their own spin (20–55°) to their resting tilt.
   on impact  Two bounces with restitution 0.3 (about 60 pt and 5 pt high) and a wobble of ±7°
              (sin 18 t · e^(−5 t), faded out over 1.6 s). The last word has settled by 5.1 s.
   5.10–6.00  At rest again; 6 s is the same frame as 0.

 Touch: a sticker can be picked up (light haptic, it lifts and grows 5%) and dragged; it leans with
 the finger's speed and is thrown on release with the finger's velocity. Thrown stickers fall under
 the same gravity, bounce off the screen's sides and come to rest on their own floor height with
 restitution 0.3, a spring pulling them back to their tilt; a hard landing gives a rigid haptic. A
 tap without a drag makes the sticker hop (900 pt/s up, with a spin). 2 s after the last touch the
 stickers glide back to the pile (0.6 s, ease-in-out) and the loop starts over. Reduced motion: the
 pile stays at rest and does not move.
*/

/** The self-playing loop, in seconds. */
export const LOOP = 6;

const INK = '#141414';
const FONT = 'InterTight-Medium';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

const COLORS = [0xf2655b, 0xff9a6b, 0x6fd0f0, 0xd9a0f4, 0xb9f0e6, 0x9be58b];
/** Per word: centre x (fraction of W), height of its bottom above the floor (in S), tilt (degrees). */
const PILE = [
  [0.165, 0, -5],
  [0.48, 0, 3],
  [0.82, 0, -3],
  [0.36, 1.06, -6],
  [0.8, 1.52, 5],
  [0.7, 2.58, -12],
];
const DRIFTS = [-40, 30, -20, 50, -30, 40];
const SPINS = [-35, 28, -20, 40, -30, 55];
const EXIT_SPINS = [-50, 40, -30, 60, -45, 70];

const GRAVITY = 2600;
const BOUNCE = 0.3;
const IDLE = 2;
const GLIDE = 0.6;
const RAD = Math.PI / 180;

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}
function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

type Pose = { x: number; y: number; a: number };

type Sticker = {
  word: string;
  color: number;
  size: number;
  width: number;
  height: number;
  /** Where it rests in the pile (centre) and how it tilts there (radians). */
  rest: Pose;
  drift: number;
  spin: number;
  wobble: number;
  exitSpin: number;
};

type Layout = { stickers: Sticker[]; w: number; h: number; baseline: number };

type Sim = {
  mode: 0 | 1 | 2; // auto, physics, gliding
  loopStart: number;
  last: number;
  poses: Pose[];
  velocity: Pose[];
  floored: boolean[];
  order: number[];
  dragged: number;
  grab: { x: number; y: number };
  finger: { x: number; y: number };
  fingerVelocity: { x: number; y: number };
  fingerAt: number;
  downAt: number;
  travel: number;
  released: number;
  glideFrom: Pose[];
  glideStart: number;
  /** A hard landing happened this frame. */
  landed: boolean;
};

function glyphWidth(face: SkTypeface, text: string, size: number) {
  const font = Skia.Font(face, size);
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, w) => sum + w, 0);
}

/** The largest size up to `cap` at which every line fits `width`. */
function fitSize(face: SkTypeface, lines: string[], width: number, cap: number) {
  const widest = Math.max(0, ...lines.map((line) => glyphWidth(face, line, 100)));
  return widest > 0 ? Math.min(cap, (100 * width) / widest) : cap;
}

function makeLayout(face: SkTypeface, words: string[], w: number, h: number, bottom: number): Layout {
  // Cap height and the depth of "p", per unit of size, from the font's outlines.
  const unit = Skia.Font(face, 100);
  const cap = -(Skia.Path.MakeFromText('H', 0, 0, unit)?.computeTightBounds().y ?? -72.8) / 100;
  const p = Skia.Path.MakeFromText('p', 0, 0, unit)?.computeTightBounds();
  const descent = p ? (p.y + p.height) / 100 : 0.204;
  const s = 0.13 * w;
  const floor = h - bottom - 20;
  const stickers = words.slice(0, 6).map((word, i) => {
    const pile = PILE[i];
    const size = i === 2 ? 1.4 * s : s;
    const width = glyphWidth(face, word, size) + 0.48 * size;
    const height = 1.12 * size;
    const x = Math.min(Math.max(pile[0] * w, width / 2 + 8), Math.max(width / 2 + 8, w - width / 2 - 8));
    return {
      word,
      color: COLORS[i],
      size,
      width,
      height,
      rest: { x, y: floor - pile[1] * s - height / 2, a: pile[2] * RAD },
      drift: DRIFTS[i],
      spin: SPINS[i] * RAD,
      wobble: (SPINS[i] > 0 ? 7 : -7) * RAD,
      exitSpin: EXIT_SPINS[i] * RAD,
    };
  });
  return { stickers, w, h, baseline: (cap - descent) / 2 };
}

/** Where sticker `i` is `t` seconds into the loop. */
function poseAt(layout: Layout, i: number, t: number): Pose {
  'worklet';
  const c = layout.stickers[i];
  const g = GRAVITY;
  const exit = 1.2 + 0.05 * (layout.stickers.length - 1 - i);
  const drop = 2.0 + 0.14 * i;
  if (t < exit) return { x: c.rest.x, y: c.rest.y, a: c.rest.a };
  if (t < drop) {
    const tau = t - exit;
    const y = c.rest.y - 250 * tau + 0.5 * g * tau * tau;
    return { x: c.rest.x + 1.5 * c.drift * tau, y: Math.min(y, layout.h + 400), a: c.rest.a + c.exitSpin * tau };
  }
  const tau = t - drop;
  const y0 = -Math.max(c.width, c.height) / 2 - 20;
  const fall = Math.sqrt((2 * (c.rest.y - y0)) / g);
  if (tau < fall) {
    const p = tau / fall;
    return { x: c.rest.x + c.drift * (1 - easeOutCubic(p)), y: y0 + 0.5 * g * tau * tau, a: c.rest.a + c.spin * (1 - p) };
  }
  const after = tau - fall;
  const v1 = BOUNCE * g * fall;
  const t1 = (2 * v1) / g;
  const v2 = BOUNCE * v1;
  const t2 = (2 * v2) / g;
  let height = 0;
  if (after < t1) {
    height = v1 * after - 0.5 * g * after * after;
  } else if (after < t1 + t2) {
    const u = after - t1;
    height = v2 * u - 0.5 * g * u * u;
  }
  const wobble = c.wobble * Math.sin(18 * after) * Math.exp(-5 * after) * (1 - smoothstep(0, 1.6, after));
  return { x: c.rest.x, y: c.rest.y - height, a: c.rest.a + wobble };
}

function contains(layout: Layout, sim: Sim, x: number, y: number, i: number) {
  'worklet';
  const c = layout.stickers[i];
  const p = sim.poses[i];
  const dx = x - p.x;
  const dy = y - p.y;
  const lx = dx * Math.cos(-p.a) - dy * Math.sin(-p.a);
  const ly = dx * Math.sin(-p.a) + dy * Math.cos(-p.a);
  return Math.abs(lx) <= c.width / 2 + 6 && Math.abs(ly) <= c.height / 2 + 6;
}

// Defined before `update`, which calls it: a worklet function is not hoisted, so `update` would
// capture it as undefined (Expo Go crashed on the first frame after a sticker was picked up).
function simulate(layout: Layout, sim: Sim, dt: number) {
  'worklet';
  for (let i = 0; i < layout.stickers.length; i++) {
    const c = layout.stickers[i];
    const p = sim.poses[i];
    if (i === sim.dragged) {
      const target = c.rest.a + Math.min(0.45, Math.max(-0.45, sim.fingerVelocity.x * 0.0005));
      p.a += (target - p.a) * Math.min(1, dt * 12);
      continue;
    }
    const v = sim.velocity[i];
    v.y += GRAVITY * dt;
    p.x += v.x * dt;
    p.y += v.y * dt;
    // A spring pulls the tilt back to the pile's.
    v.a += (-(p.a - c.rest.a) * 30 - v.a * 6) * dt;
    p.a += v.a * dt;
    const half = c.width / 2;
    if (p.x < half) {
      p.x = half;
      v.x = Math.abs(v.x) * 0.5;
    }
    if (p.x > layout.w - half) {
      p.x = layout.w - half;
      v.x = -Math.abs(v.x) * 0.5;
    }
    if (sim.floored[i] && p.y > c.rest.y) {
      p.y = c.rest.y;
      if (v.y > 700) sim.landed = true;
      v.y = v.y > 60 ? -v.y * BOUNCE : 0;
      v.x *= 0.8;
    }
    if (sim.floored[i] && p.y >= c.rest.y - 0.5 && v.y === 0) v.x *= Math.exp(-8 * dt);
  }
}

function update(layout: Layout, sim: Sim, now: number, autoPlay: boolean) {
  'worklet';
  const n = layout.stickers.length;
  if (sim.poses.length !== n) {
    sim.poses = layout.stickers.map((c) => ({ ...c.rest }));
    sim.velocity = layout.stickers.map(() => ({ x: 0, y: 0, a: 0 }));
    sim.floored = layout.stickers.map(() => true);
    sim.order = layout.stickers.map((_, i) => i);
  }
  if (sim.mode === 0) {
    const t = autoPlay ? (((now - sim.loopStart) % LOOP) + LOOP) % LOOP : 0;
    for (let i = 0; i < n; i++) sim.poses[i] = poseAt(layout, i, t);
  } else if (sim.mode === 1) {
    let dt = Math.min(Math.max(now - sim.last, 0), 0.05);
    while (dt > 0) {
      const step = Math.min(dt, 1 / 120);
      simulate(layout, sim, step);
      dt -= step;
    }
    if (sim.dragged < 0 && now - sim.released >= IDLE) {
      sim.mode = 2;
      sim.glideFrom = sim.poses.map((p) => ({ ...p }));
      sim.glideStart = now;
      sim.order = layout.stickers.map((_, i) => i);
    }
  } else {
    const k = easeInOut(Math.min((now - sim.glideStart) / GLIDE, 1));
    for (let i = 0; i < n; i++) {
      const a = sim.glideFrom[i];
      const b = layout.stickers[i].rest;
      sim.poses[i] = { x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k, a: a.a + (b.a - a.a) * k };
    }
    if (k >= 1) {
      sim.mode = 0;
      sim.loopStart = now;
    }
  }
  sim.last = now;
}

type Props = {
  badge?: string;
  headline?: string[];
  subtitle?: string;
  action?: string;
  /** The six words of the pile, in reading order. */
  words?: string[];
  /** Drops the words again every `LOOP` seconds. */
  autoPlay?: boolean;
  onAction?: () => void;
};

/**
 * A studio's landing screen whose call to action is six pastel word stickers that drop in, bounce
 * and settle into a pile; pick one up and throw it.
 */
export function FallingWordStickers({
  badge = 'Open for new briefs',
  headline = ['Product sites that', 'say it straight —', 'clear, direct,', 'no fluff.'],
  subtitle = 'A two-person studio for sites and apps.',
  action = 'Start a project →',
  words = ['your', 'next', 'big', 'project', 'starts', 'here'],
  autoPlay = false,
  onAction,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const face = useTypeface(require('./assets/InterTight-Medium.ttf'));
  const [fontsLoaded] = useFonts({ [FONT]: require('./assets/InterTight-Medium.ttf') });
  const [size, setSize] = useState({ width: 0, height: 0 });
  const key = words.join(' ');
  const layout = useMemo(
    () => (face && size.width > 0 ? makeLayout(face, key.split(' '), size.width, size.height, insets.bottom) : null),
    [face, key, size.width, size.height, insets.bottom],
  );
  const headlineSize = face && size.width > 0 ? fitSize(face, headline, size.width - 48, 36) : 36;
  const play = autoPlay && !reduced;

  const clock = useSharedValue(0);
  const sim = useSharedValue<Sim>({
    mode: 0,
    loopStart: -1,
    last: 0,
    poses: [],
    velocity: [],
    floored: [],
    order: [],
    dragged: -1,
    grab: { x: 0, y: 0 },
    finger: { x: 0, y: 0 },
    fingerVelocity: { x: 0, y: 0 },
    fingerAt: 0,
    downAt: 0,
    travel: 0,
    released: -100,
    glideFrom: [],
    glideStart: 0,
    landed: false,
  });

  const lift = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const land = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    if (!layout) return;
    sim.modify((s) => {
      'worklet';
      if (s.loopStart < 0) {
        s.loopStart = now;
        s.last = now;
      }
      update(layout, s, now, play);
      if (s.landed) {
        s.landed = false;
        scheduleOnRN(land);
      }
      return s;
    });
    clock.set(now);
  });

  const picture = useDerivedValue(() => {
    clock.get();
    const s = sim.get();
    return createPicture((canvas) => {
      if (!layout || !face || s.poses.length !== layout.stickers.length) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const ink = Skia.Paint();
      ink.setAntiAlias(true);
      ink.setColor(Skia.Color(INK));
      for (const i of s.order) {
        const c = layout.stickers[i];
        const p = s.poses[i];
        if (p.y - Math.max(c.width, c.height) >= layout.h) continue;
        const up = s.dragged === i ? 1 : 0;
        const scale = 1 + 0.05 * up;
        const rect = Skia.RRectXY(Skia.XYWHRect(-c.width / 2, -c.height / 2, c.width, c.height), 0.1 * c.size, 0.1 * c.size);

        // Shadow: the same shape, blurred (sigma 6, 10 when lifted) and dropped 4 pt (12 lifted).
        const shadow = Skia.Paint();
        shadow.setAntiAlias(true);
        shadow.setColor(Float32Array.of(0, 0, 0, 0.14 + 0.06 * up));
        shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 + 4 * up, true));
        canvas.save();
        canvas.translate(p.x, p.y + 4 + 8 * up);
        canvas.rotate(p.a / RAD, 0, 0);
        canvas.scale(scale, scale);
        canvas.drawRRect(rect, shadow);
        canvas.restore();

        canvas.save();
        canvas.translate(p.x, p.y);
        canvas.rotate(p.a / RAD, 0, 0);
        canvas.scale(scale, scale);
        paint.setColor(Float32Array.of(((c.color >> 16) & 0xff) / 255, ((c.color >> 8) & 0xff) / 255, (c.color & 0xff) / 255, 1));
        canvas.drawRRect(rect, paint);
        canvas.drawText(c.word, -c.width / 2 + 0.24 * c.size, layout.baseline * c.size, ink, Skia.Font(face, c.size));
        canvas.restore();
      }
    });
  });

  const gesture = Gesture.Manual()
    .onTouchesDown((e, manager) => {
      const touch = e.changedTouches[0];
      if (reduced || !layout || !touch || e.numberOfTouches !== 1) {
        manager.fail();
        return;
      }
      const now = clock.get();
      sim.modify((s) => {
        'worklet';
        update(layout, s, now, play);
        let hit = -1;
        for (let k = s.order.length - 1; k >= 0; k--) {
          if (contains(layout, s, touch.x, touch.y, s.order[k])) {
            hit = s.order[k];
            break;
          }
        }
        if (hit < 0) return s;
        // Plain loops: arrow functions inside this worklet would be moved out of it by the React
        // Compiler, and the UI thread could not call them.
        const n = layout.stickers.length;
        if (s.mode === 0) {
          const t = play ? (((now - s.loopStart) % LOOP) + LOOP) % LOOP : 0;
          for (let i = 0; i < n; i++) {
            const a = poseAt(layout, i, t - 1 / 60);
            const b = poseAt(layout, i, t);
            s.velocity[i] = { x: (b.x - a.x) * 60, y: (b.y - a.y) * 60, a: (b.a - a.a) * 60 };
          }
        } else if (s.mode === 2) {
          for (let i = 0; i < n; i++) s.velocity[i] = { x: 0, y: 0, a: 0 };
        }
        for (let i = 0; i < n; i++) s.floored[i] = s.poses[i].y <= layout.stickers[i].rest.y + 1;
        s.mode = 1;
        s.dragged = hit;
        s.floored[hit] = true;
        const order: number[] = [];
        for (let k = 0; k < s.order.length; k++) if (s.order[k] !== hit) order.push(s.order[k]);
        order.push(hit);
        s.order = order;
        s.grab = { x: s.poses[hit].x - touch.x, y: s.poses[hit].y - touch.y };
        s.finger = { x: touch.x, y: touch.y };
        s.fingerVelocity = { x: 0, y: 0 };
        s.fingerAt = now;
        s.downAt = now;
        s.travel = 0;
        s.last = now;
        return s;
      });
      // The sticker under the finger, if any, is now the dragged one.
      if (sim.get().dragged < 0) {
        manager.fail();
        return;
      }
      scheduleOnRN(lift);
      manager.activate();
    })
    .onTouchesMove((e) => {
      const touch = e.changedTouches[0];
      if (!touch) return;
      const now = clock.get();
      sim.modify((s) => {
        'worklet';
        const i = s.dragged;
        if (i < 0 || !layout) return s;
        const dt = Math.max(now - s.fingerAt, 1 / 240);
        const vx = (touch.x - s.finger.x) / dt;
        const vy = (touch.y - s.finger.y) / dt;
        s.fingerVelocity = { x: s.fingerVelocity.x * 0.6 + vx * 0.4, y: s.fingerVelocity.y * 0.6 + vy * 0.4 };
        s.travel += Math.hypot(touch.x - s.finger.x, touch.y - s.finger.y);
        s.finger = { x: touch.x, y: touch.y };
        s.fingerAt = now;
        s.poses[i] = { x: touch.x + s.grab.x, y: Math.min(touch.y + s.grab.y, layout.stickers[i].rest.y), a: s.poses[i].a };
        return s;
      });
    })
    .onTouchesUp((_e, manager) => {
      manager.end();
    })
    .onFinalize(() => {
      const now = clock.get();
      sim.modify((s) => {
        'worklet';
        const i = s.dragged;
        if (i < 0 || !layout) return s;
        s.dragged = -1;
        s.released = now;
        if (s.travel < 8 && now - s.downAt < 0.3) {
          // A tap: the sticker hops.
          s.velocity[i] = { x: 0, y: -900, a: layout.stickers[i].spin > 0 ? 4 : -4 };
        } else {
          const still = now - s.fingerAt > 0.1;
          const vx = still ? 0 : Math.min(3000, Math.max(-3000, s.fingerVelocity.x));
          const vy = still ? 0 : Math.min(3000, Math.max(-3000, s.fingerVelocity.y));
          s.velocity[i] = { x: vx, y: vy, a: vx * 0.004 };
        }
        return s;
      });
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const family = fontsLoaded ? FONT : undefined;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="dark" />
        <View style={[styles.copy, { paddingTop: insets.top + 16 }]}>
          <View style={styles.badge}>
            <View style={styles.dot} />
            <Text style={styles.badgeText} numberOfLines={1}>
              {badge.toUpperCase()}
            </Text>
          </View>
          <View style={styles.headline}>
            {headline.map((line) => (
              <View key={line} style={[styles.line, { height: 1.08 * headlineSize }]}>
                <Text
                  style={{ color: INK, fontFamily: family, fontSize: headlineSize, letterSpacing: -0.02 * headlineSize }}
                  numberOfLines={1}
                >
                  {line}
                </Text>
              </View>
            ))}
          </View>
          <Text style={[styles.subtitle, { fontFamily: family }]} numberOfLines={2}>
            {subtitle}
          </Text>
          <Pressable accessibilityRole="button" onPress={onAction} style={styles.button}>
            <Text style={[styles.action, { fontFamily: family }]} numberOfLines={1}>
              {action}
            </Text>
          </Pressable>
        </View>

        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityLabel={words.join(' ')}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f3f2ee' },
  copy: { paddingHorizontal: 24, alignItems: 'flex-start' },
  badge: { height: 26, borderRadius: 13, backgroundColor: INK, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#3ddc84' },
  badgeText: { color: '#ffffff', fontFamily: MONO, fontSize: 11, fontWeight: '500', letterSpacing: 1 },
  headline: { marginTop: 20 },
  line: { justifyContent: 'center' },
  subtitle: { marginTop: 14, color: 'rgba(20,20,20,0.55)', fontSize: 15 },
  button: { marginTop: 24, height: 52, borderRadius: 26, backgroundColor: INK, paddingHorizontal: 24, justifyContent: 'center' },
  action: { color: '#ffffff', fontSize: 17 },
});
