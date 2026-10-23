import { Canvas, Group, Path, RoundedRect, Shadow, Skia } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { type ComponentProps, useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolate,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Drags the sheet up to half, to full, and back down to the peek, over and over. */
  autoPlay?: boolean;
  /** Called with the detent the sheet settles on: 0 peek, 1 half, 2 full. */
  onDetentChange?: (detent: number) => void;
};

const unsplash = (id: string, w: number, h: number) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

const HERO = unsplash('photo-1549294413-26f195200c16', 1100, 2300);
const ROOMS = [
  { name: 'Pool Villa', price: '€312', uri: unsplash('photo-1562778612-e1e0cda9915c', 330, 380) },
  { name: 'Garden Suite', price: '€248', uri: unsplash('photo-1587870306141-4f19861e6c73', 330, 380) },
  { name: 'Sea Loft', price: '€286', uri: unsplash('photo-1586611292717-f828b167408c', 330, 380) },
];
type SymbolName = ComponentProps<typeof SymbolView>['name'];
const AMENITIES: { label: string; icon: SymbolName }[] = [
  { label: 'Pool', icon: { ios: 'figure.pool.swim', android: 'pool', web: 'pool' } },
  { label: 'Breakfast', icon: { ios: 'cup.and.saucer', android: 'coffee', web: 'coffee' } },
  { label: 'Wi-Fi', icon: { ios: 'wifi', android: 'wifi', web: 'wifi' } },
  { label: 'Bar', icon: { ios: 'wineglass', android: 'wine_bar', web: 'wine_bar' } },
];

// Colours: a warm paper sheet, one terracotta accent.
const SHEET = '#f7f3ee';
const INK = '#1d1b18';
const MUTED = '#736d65';
const LINE = '#e5ded4';
const TILE = '#eee7dd';
const ACCENT = '#b4532a';
const GRABBER = '#cfc5b8';

// The sheet's three resting places, as the y of its top edge: the peek leaves PEEK_SHOW pt of
// the sheet above the footer; half sits at HALF of the screen; full stops FULL_GAP below the
// top inset.
const FOOTER = 84;
const PEEK_SHOW = 124;
const HALF = 0.47;
const FULL_GAP = 100;
const CORNER = 28;

// The jelly edge is a membrane: NODES points across the width, pinned at both corners, each
// lifted d pt off the sheet's straight edge. Each node is tied to its rest place (ANCHOR, 1/s²)
// and to its neighbours (TENSION), with damping of its own (DAMP) and between neighbours
// (SHEAR_DAMP, which calms the short ripples first), so the edge rings at about 3 Hz in one
// soft arch, never in a zig-zag. It is stepped in fixed substeps of SUBSTEP s.
const NODES = 21;
const ANCHOR = 60;
const TENSION = 12000;
const DAMP = 2;
const SHEAR_DAMP = 170;
const SUBSTEP = 1 / 720;
// What moves it: the air the sheet moves through holds every node back by LAG × the sheet's
// speed (pt/s²), so the edge trails a rising sheet; the nodes' own weight carries INERTIA × the
// sheet's acceleration (smoothed over ACCEL_SMOOTH s), so the edge swells as it stops; a finger holds the
// nodes under it (a bell GRIP of the width wide) with a spring (HOLD, HOLD_DAMP), so the edge
// near the finger leads and the rest trails; dragged past an end detent, the finger lifts them
// by STRETCH_PULL of the stretch. A tap on the head kicks the nodes under it up at KICK pt/s.
const LAG = 4.5;
const INERTIA = 0.45;
const ACCEL_SMOOTH = 0.04;
const GRIP = 0.22;
const HOLD = 900;
const HOLD_DAMP = 40;
const STRETCH_PULL = 0.55;
const KICK = 320;
// Drawn through a soft limit, so the edge never lifts or sags more than LIFT_MAX pt.
const LIFT_MAX = 34;
const SAMPLES = 64;
// The content under the edge gives with the middle of it by this share, so a sag never covers it.
const CONTENT_GIVE = 0.5;
// The grabber stretches with the edge's local slope, up to GRAB_STRETCH pt, and tilts with it.
const GRAB_W = 40;
const GRAB_STRETCH = 14;

// The sheet itself: a spring to its detent (ζ ≈ 0.75, a small overshoot that sets the jelly
// going), with the release velocity handed in. A release is thrown THROW seconds ahead to pick
// the detent; anything faster than FLICK pt/s moves one detent in its direction at least.
const STIFFNESS = 230;
const DAMPING = 23;
const THROW = 0.18;
const FLICK = 900;
const RUBBER = 0.35;
// The photo behind, when the sheet is fully open: scaled back and blurred.
const PHOTO_SCALE = 0.9;
const PHOTO_RADIUS = 26;

// autoPlay (seconds). Each move is a finger dragging from one detent towards the next for
// `time` seconds at x (share of the width), letting go with the speed it had.
const LOOP = 11;
const MOVES = [
  { at: 0.8, time: 0.55, to: 1, x: 0.66 },
  { at: 3.0, time: 0.5, to: 2, x: 0.36 },
  { at: 5.6, time: 0.45, to: 1, x: 0.58 },
  { at: 7.6, time: 0.42, to: 0, x: 0.44 },
];
// Where the script picks up again after a touch, by the detent the sheet rests on.
const RESUME_AT = [0, 2.2, 4.8];
const USER_HOLD = 4;
// Share of a scripted drag the finger covers before letting go; the spring does the rest.
const DRAG_SHARE = 0.8;

function rubber(over: number) {
  'worklet';
  return (over * RUBBER) / (1 + Math.abs(over) / 400);
}

function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

type Membrane = { d: number[]; u: number[]; acc: number; speed: number; accel: number };

function makeMembrane(): Membrane {
  'worklet';
  const d: number[] = [];
  const u: number[] = [];
  for (let i = 0; i < NODES; i += 1) {
    d.push(0);
    u.push(0);
  }
  return { d, u, acc: 0, speed: 0, accel: 0 };
}

/** The share of a finger's hold on node i, for a finger at fx on a sheet `width` wide. */
function grip(i: number, fx: number, width: number) {
  'worklet';
  const x = (width * i) / (NODES - 1);
  const k = (x - fx) / (width * GRIP);
  return Math.exp(-k * k);
}

/**
 * Advances the membrane by `dt` s. `speed` is the sheet's speed (pt/s, down positive); `held`
 * says a finger holds it at fx, lifting the nodes under it to `lift`.
 */
function stepMembrane(m: Membrane, dt: number, speed: number, accel: number, held: boolean, fx: number, lift: number, width: number) {
  'worklet';
  const n = NODES;
  const d = m.d;
  const u = m.u;
  m.acc = Math.min(m.acc + dt, 0.05);
  while (m.acc >= SUBSTEP) {
    m.acc -= SUBSTEP;
    // Velocities first (from this substep's places), then places: semi-implicit Euler.
    for (let i = 1; i < n - 1; i += 1) {
      let a =
        TENSION * (d[i - 1] + d[i + 1] - 2 * d[i]) +
        SHEAR_DAMP * (u[i - 1] + u[i + 1] - 2 * u[i]) -
        ANCHOR * d[i] -
        DAMP * u[i] +
        LAG * speed +
        INERTIA * accel;
      if (held) {
        const w = grip(i, fx, width);
        a += w * (HOLD * (lift - d[i]) - HOLD_DAMP * u[i]);
      }
      u[i] += a * SUBSTEP;
    }
    for (let i = 1; i < n - 1; i += 1) d[i] += u[i] * SUBSTEP;
  }
  // A guard: anything not finite (a bad frame time) puts the edge back flat.
  for (let i = 0; i < n; i += 1) {
    if (!Number.isFinite(d[i]) || !Number.isFinite(u[i])) {
      for (let j = 0; j < n; j += 1) {
        d[j] = 0;
        u[j] = 0;
      }
      return;
    }
  }
}

/** The edge's lift at x (pt): a Catmull-Rom curve through the nodes, through a soft limit. */
function liftAt(d: number[], x: number, width: number) {
  'worklet';
  const n = NODES;
  const f = clamp((x / Math.max(1, width)) * (n - 1), 0, n - 1);
  const i = Math.min(n - 2, Math.floor(f));
  const t = f - i;
  const p0 = d[Math.max(0, i - 1)];
  const p1 = d[i];
  const p2 = d[i + 1];
  const p3 = d[Math.min(n - 1, i + 2)];
  const t2 = t * t;
  const t3 = t2 * t;
  const raw = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
  return LIFT_MAX * Math.tanh(raw / LIFT_MAX);
}

/**
 * A hotel's page: a tall photo with a booking sheet over it. The sheet's top edge is jelly —
 * it bulges towards the finger while dragged and wobbles when let go — and it rests at three
 * heights: a peek with the hotel's name, half with the stay, full with the rooms. Fully open,
 * the photo behind blurs and sinks back.
 */
export function JellySheet({ autoPlay = false, onDetentChange }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const { width, height } = size;

  const footer = FOOTER + insets.bottom;
  const detents = [height - footer - PEEK_SHOW, height * HALF, insets.top + FULL_GAP];

  const y = useSharedValue(0);
  const v = useSharedValue(0);
  const target = useSharedValue(0);
  const detent = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const dragSpeed = useSharedValue(0);
  const fingerX = useSharedValue(0);
  // The membrane lives on the UI thread (made there on the first frame); `tick` redraws it.
  const membrane = useSharedValue<Membrane | null>(null);
  const tick = useSharedValue(0);
  // A kick waiting for the next frame: where (x) and how hard (pt/s).
  const kickX = useSharedValue(0);
  const kickV = useSharedValue(0);
  const ready = useSharedValue(false);
  // autoPlay's clock and the scripted finger.
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);
  const scripted = useSharedValue(-1);
  const scriptFrom = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
  }, [autoPlay, looping]);

  // The detents follow the layout; the sheet starts at the peek.
  useEffect(() => {
    if (height <= 0) return;
    const at = detents[detent.get()];
    if (!ready.get()) {
      y.set(at);
      ready.set(true);
    }
    target.set(at);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [height, width, insets.top, insets.bottom]);

  const settled = (index: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onDetentChange?.(index);
  };

  const scriptSettled = (index: number) => onDetentChange?.(index);

  /** Lets go of the sheet with speed `speed` towards detent `to`. */
  const release = (to: number, speed: number, byUser: boolean) => {
    'worklet';
    dragging.set(false);
    v.set(speed);
    if (to !== detent.get()) scheduleOnRN(byUser ? settled : scriptSettled, to);
    detent.set(to);
    target.set(detents[to]);
  };

  /** The detent a release at y with speed `speed` lands on. */
  const pick = (at: number, speed: number) => {
    'worklet';
    const thrown = at + speed * THROW;
    let best = 0;
    for (let k = 1; k < detents.length; k += 1) {
      if (Math.abs(detents[k] - thrown) < Math.abs(detents[best] - thrown)) best = k;
    }
    // A flick always moves at least one detent its way from where the sheet is now.
    if (Math.abs(speed) > FLICK) {
      let near = 0;
      for (let k = 1; k < detents.length; k += 1) {
        if (Math.abs(detents[k] - at) < Math.abs(detents[near] - at)) near = k;
      }
      const step = speed < 0 ? 1 : -1;
      const further = clamp(near + step, 0, detents.length - 1);
      if (speed < 0 ? best < further : best > further) best = further;
    }
    return best;
  };

  /** Where a drag puts the sheet: with the finger, stretching past the end detents. */
  const follow = (raw: number) => {
    'worklet';
    const top = detents[2];
    const bottom = detents[0];
    if (raw < top) return top - rubber(top - raw);
    if (raw > bottom) return bottom + rubber(raw - bottom);
    return raw;
  };

  useFrameCallback((frame) => {
    if (!ready.get()) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;

    // The script: a finger drags the sheet towards the next detent and lets go.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(RESUME_AT[detent.get()]);
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let k = 0; k < MOVES.length; k += 1) {
        const move = MOVES[k];
        const end = move.at + move.time;
        if (scripted.get() === k) {
          const u = clamp((t - move.at) / move.time, 0, 1);
          const span = (detents[move.to] - scriptFrom.get()) * DRAG_SHARE;
          y.set(scriptFrom.get() + span * (0.4 * u + 0.6 * u * u));
          dragSpeed.set((span * (0.4 + 1.2 * u)) / move.time);
          fingerX.set(move.x * width);
          if (t >= end || t < before) {
            scripted.set(-1);
            release(move.to, dragSpeed.get(), false);
          }
        } else if (scripted.get() < 0 && before < move.at && t >= move.at) {
          scripted.set(k);
          scriptFrom.set(y.get());
          dragging.set(true);
          fingerX.set(move.x * width);
        }
      }
    }

    // The sheet: with the finger, or on its spring.
    let speed = v.get();
    if (dragging.get()) {
      speed = dragSpeed.get();
    } else if (reducedMotion) {
      const next = y.get() + (target.get() - y.get()) * (1 - Math.exp(-dt * 14));
      speed = (next - y.get()) / dt;
      y.set(next);
      v.set(speed);
    } else {
      speed = v.get() + (STIFFNESS * (target.get() - y.get()) - DAMPING * v.get()) * dt;
      v.set(speed);
      y.set(y.get() + speed * dt);
    }

    // The jelly edge.
    let m = membrane.get();
    // Made afresh when missing or unusable (a fast refresh can leave an older shape behind).
    if (!m || m.d.length !== NODES || !Number.isFinite(m.accel) || !Number.isFinite(m.speed)) {
      m = makeMembrane();
      membrane.set(m);
    }
    if (reducedMotion) {
      for (let i = 0; i < NODES; i += 1) {
        m.d[i] = 0;
        m.u[i] = 0;
      }
    } else {
      if (kickV.get() !== 0) {
        for (let i = 1; i < NODES - 1; i += 1) m.u[i] += kickV.get() * grip(i, kickX.get(), width);
        kickV.set(0);
      }
      const held = dragging.get();
      const at = y.get();
      const stretch = !held ? 0 : at < detents[2] ? detents[2] - at : at > detents[0] ? detents[0] - at : 0;
      const v = Number.isFinite(speed) ? clamp(speed, -6000, 6000) : 0;
      // The sheet's acceleration, smoothed so a jittery finger does not shake the edge.
      const raw = (v - m.speed) / dt;
      m.speed = v;
      m.accel += (clamp(raw, -40000, 40000) - m.accel) * (1 - Math.exp(-dt / ACCEL_SMOOTH));
      stepMembrane(m, dt, v, m.accel, held, fingerX.get(), stretch * STRETCH_PULL, width);
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    // A scripted drag under way is let go of where it is, and springs back to its detent.
    if (scripted.get() >= 0) {
      scripted.set(-1);
      dragging.set(false);
      v.set(dragSpeed.get());
    }
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin((event) => {
      touched();
      fingerX.set(event.absoluteX);
    })
    .onStart(() => {
      // The sheet is caught wherever it is, mid-spring included.
      dragFrom.set(y.get());
      dragSpeed.set(0);
      dragging.set(true);
    })
    .onUpdate((event) => {
      y.set(follow(dragFrom.get() + event.translationY));
      dragSpeed.set(event.velocityY);
      fingerX.set(event.absoluteX);
    })
    .onEnd((event) => {
      release(pick(y.get(), event.velocityY), event.velocityY, true);
    })
    .onFinalize(() => {
      if (dragging.get()) release(pick(y.get(), 0), 0, true);
    });

  // A tap on the sheet's head opens it a detent; from full it goes back to half.
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((event, success) => {
      if (!success || event.y > 90) return;
      touched();
      fingerX.set(event.absoluteX);
      // The tap sends a ripple out from under the finger as the sheet moves off.
      kickX.set(event.absoluteX);
      kickV.set(KICK);
      const at = detent.get();
      release(at === 2 ? 1 : at + 1, 0, true);
    });

  const sheetPath = useDerivedValue(() => {
    tick.get();
    const top = y.get();
    const m = membrane.get();
    const b = Skia.PathBuilder.Make();
    if (!m || width <= 0 || !Number.isFinite(top)) {
      b.addRect(Skia.XYWHRect(0, Number.isFinite(top) ? top : height, Math.max(1, width), height + 80));
      return b.build();
    }
    // The corners are pinned, so the rounded corners stay round; the edge between them rides
    // the membrane.
    b.moveTo(0, top + CORNER);
    b.quadTo(0, top, CORNER, top - liftAt(m.d, CORNER, width));
    for (let k = 1; k <= SAMPLES; k += 1) {
      const x = CORNER + ((width - 2 * CORNER) * k) / SAMPLES;
      b.lineTo(x, top - liftAt(m.d, x, width));
    }
    b.quadTo(width, top, width, top + CORNER);
    b.lineTo(width, height + 80);
    b.lineTo(0, height + 80);
    b.close();
    return b.build();
  });
  // The grabber rides the edge in the middle, tilts with its slope and stretches with it.
  const grabberShape = useDerivedValue(() => {
    tick.get();
    const m = membrane.get();
    const mid = width / 2;
    const lift = m ? liftAt(m.d, mid, width) : 0;
    const slope = m ? (liftAt(m.d, mid + 12, width) - liftAt(m.d, mid - 12, width)) / 24 : 0;
    const stretch = Math.min(GRAB_STRETCH, Math.abs(slope) * 60 + Math.abs(lift) * 0.12);
    return { lift, angle: -Math.atan(slope), w: GRAB_W + stretch };
  });
  const grabber = useDerivedValue(() => {
    const g = grabberShape.get();
    return [{ translateX: width / 2 }, { translateY: y.get() - g.lift + 11.5 }, { rotate: g.angle }];
  });
  const grabberRect = useDerivedValue(() => {
    const w = grabberShape.get().w;
    return Skia.RRectXY(Skia.XYWHRect(-w / 2, -2.5, w, 5), 2.5, 2.5);
  });

  // 0 at half or below, 1 fully open.
  const openness = (at: number) => {
    'worklet';
    return clamp((detents[1] - at) / (detents[1] - detents[2]), 0, 1);
  };
  const photoStyle = useAnimatedStyle(() => {
    const o = openness(y.get());
    return {
      borderRadius: o * PHOTO_RADIUS,
      transform: reducedMotion
        ? []
        : [{ translateY: interpolate(o, [0, 1], [0, insets.top * 0.4]) }, { scale: 1 - (1 - PHOTO_SCALE) * o }],
    };
  });
  const blurStyle = useAnimatedStyle(() => ({ opacity: openness(y.get()) }));
  const chromeStyle = useAnimatedStyle(() => ({ opacity: 1 - openness(y.get()) * 1.6 }));
  const counterStyle = useAnimatedStyle(() => ({
    opacity: clamp((y.get() - detents[1]) / (detents[0] - detents[1]), 0, 1),
    transform: [{ translateY: y.get() - 46 }],
  }));
  // The content rides the sheet, and the jelly nudges it a little so it feels part of it.
  const contentStyle = useAnimatedStyle(() => {
    tick.get();
    const m = membrane.get();
    const sag = m ? Math.min(0, liftAt(m.d, width / 2, width)) : 0;
    return { transform: [{ translateY: y.get() - sag * CONTENT_GIVE }] };
  });

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        setSize({ width: w, height: h });
      }}
    >
      {height > 0 && (
        <>
          <Animated.View style={[StyleSheet.absoluteFill, styles.photo, photoStyle]}>
            <Image source={{ uri: HERO }} style={StyleSheet.absoluteFill} contentFit="cover" transition={350} />
            <LinearGradient
              colors={['rgba(0,0,0,0.38)', 'rgba(0,0,0,0)']}
              style={[styles.topShade, { height: insets.top + 110 }]}
            />
            <Animated.View style={[StyleSheet.absoluteFill, blurStyle]}>
              <BlurView intensity={38} tint="dark" style={StyleSheet.absoluteFill} />
            </Animated.View>
          </Animated.View>

          <Animated.View style={[styles.topBar, { top: insets.top + 6 }, chromeStyle]}>
            <Glass icon={{ ios: 'chevron.left', android: 'arrow_back', web: 'arrow_back' }} />
            <View style={styles.topRight}>
              <Glass icon={{ ios: 'square.and.arrow.up', android: 'share', web: 'share' }} />
              <Glass icon={{ ios: 'heart', android: 'favorite_border', web: 'favorite_border' }} />
            </View>
          </Animated.View>

          <Animated.View style={[styles.counter, counterStyle]}>
            <SymbolView name={{ ios: 'photo.on.rectangle', android: 'photo_library', web: 'photo_library' }} size={13} tintColor="#ffffff" />
            <Text style={styles.counterText}>1 / 24</Text>
          </Animated.View>

          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Path path={sheetPath} color={SHEET}>
              <Shadow dx={0} dy={-4} blur={22} color="rgba(0,0,0,0.28)" />
            </Path>
            <Group transform={grabber}>
              <RoundedRect rect={grabberRect} color={GRABBER} />
            </Group>
          </Canvas>

          <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
            <Animated.View
              style={[styles.sheet, { height }, contentStyle]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Booking details for Casa Albera"
              accessibilityHint="Drag up for the rooms, down to close"
            >
              <SheetContent />
            </Animated.View>
          </GestureDetector>

          <View style={[styles.footer, { height: footer, paddingBottom: insets.bottom }]}>
            <View style={styles.priceBlock}>
              <Text style={styles.price}>
                €248<Text style={styles.night}> / night</Text>
              </Text>
              <Text style={styles.priceNote}>12 – 16 May · 2 guests</Text>
            </View>
            <View style={styles.reserve}>
              <Text style={styles.reserveText}>Reserve</Text>
            </View>
          </View>
        </>
      )}
    </View>
  );
}

/** A round frosted button on the photo. */
function Glass({ icon }: { icon: SymbolName }) {
  return (
    <BlurView intensity={30} tint="light" style={styles.glass}>
      <SymbolView name={icon} size={17} weight="semibold" tintColor="#ffffff" />
    </BlurView>
  );
}

/** What the sheet holds, top to bottom: the hotel, the stay, the rooms, what's included. */
function SheetContent() {
  return (
    <View style={styles.content}>
      <View style={styles.titleRow}>
        <Text style={styles.title} numberOfLines={1}>
          Casa Albera
        </Text>
        <View style={styles.rating}>
          <SymbolView name={{ ios: 'star.fill', android: 'star', web: 'star' }} size={11} tintColor={INK} />
          <Text style={styles.ratingText}>4.92</Text>
        </View>
      </View>
      <View style={styles.locationRow}>
        <SymbolView name={{ ios: 'mappin.and.ellipse', android: 'place', web: 'place' }} size={13} tintColor={MUTED} />
        <Text style={styles.location}>Praia do Norte, Comporta · 214 reviews</Text>
      </View>

      <Text style={styles.label}>Your stay</Text>
      <View style={styles.stay}>
        <StayCell label="Check-in" value="Tue 12 May" />
        <View style={styles.stayDivider} />
        <StayCell label="Check-out" value="Sat 16 May" />
        <View style={styles.stayDivider} />
        <StayCell label="Guests" value="2 adults" />
      </View>

      <Text style={styles.label}>Rooms</Text>
      <View style={styles.rooms}>
        {ROOMS.map((room) => (
          <View key={room.name} style={styles.room}>
            <Image source={{ uri: room.uri }} style={styles.roomPhoto} contentFit="cover" transition={300} />
            <Text style={styles.roomName} numberOfLines={1}>
              {room.name}
            </Text>
            <Text style={styles.roomPrice}>{`from ${room.price}`}</Text>
          </View>
        ))}
      </View>

      <Text style={styles.label}>Included</Text>
      <View style={styles.amenities}>
        {AMENITIES.map((item) => (
          <View key={item.label} style={styles.amenity}>
            <View style={styles.amenityIcon}>
              <SymbolView name={item.icon} size={19} tintColor={INK} />
            </View>
            <Text style={styles.amenityText}>{item.label}</Text>
          </View>
        ))}
      </View>

      <Text style={styles.about}>
        Twelve whitewashed rooms between the rice fields and the dunes, a ten-minute walk from the sea.
      </Text>
    </View>
  );
}

function StayCell({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stayCell}>
      <Text style={styles.stayLabel}>{label}</Text>
      <Text style={styles.stayValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#000000',
    overflow: 'hidden',
  },
  photo: {
    overflow: 'hidden',
    borderCurve: 'continuous',
    backgroundColor: '#1d2a2c',
  },
  topShade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  topBar: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  topRight: {
    flexDirection: 'row',
    gap: 10,
  },
  glass: {
    width: 42,
    height: 42,
    borderRadius: 21,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  counter: {
    position: 'absolute',
    top: 0,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 28,
    paddingHorizontal: 11,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.38)',
  },
  counterText: {
    color: '#ffffff',
    fontSize: 12.5,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  sheet: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  content: {
    paddingTop: 30,
    paddingHorizontal: 20,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  title: {
    flexShrink: 1,
    color: INK,
    fontSize: 29,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.7,
  },
  rating: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    height: 28,
    paddingHorizontal: 10,
    borderRadius: 14,
    backgroundColor: TILE,
  },
  ratingText: {
    color: INK,
    fontSize: 13.5,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  locationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginTop: 6,
  },
  location: {
    color: MUTED,
    fontSize: 14.5,
  },
  label: {
    marginTop: 24,
    marginBottom: 10,
    color: MUTED,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.1,
    textTransform: 'uppercase',
  },
  stay: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: LINE,
  },
  stayCell: {
    flex: 1,
    paddingHorizontal: 14,
    gap: 3,
  },
  stayDivider: {
    width: 1,
    height: 34,
    backgroundColor: LINE,
  },
  stayLabel: {
    color: MUTED,
    fontSize: 12,
  },
  stayValue: {
    color: INK,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  rooms: {
    flexDirection: 'row',
    gap: 10,
  },
  room: {
    flex: 1,
    gap: 3,
  },
  roomPhoto: {
    height: 116,
    borderRadius: 14,
    borderCurve: 'continuous',
    marginBottom: 5,
    backgroundColor: TILE,
  },
  roomName: {
    color: INK,
    fontSize: 14,
    fontWeight: '600',
  },
  roomPrice: {
    color: MUTED,
    fontSize: 12.5,
    fontVariant: ['tabular-nums'],
  },
  amenities: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  amenity: {
    alignItems: 'center',
    gap: 6,
    width: 72,
  },
  amenityIcon: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: TILE,
  },
  amenityText: {
    color: INK,
    fontSize: 12,
    fontWeight: '500',
  },
  about: {
    marginTop: 20,
    color: MUTED,
    fontSize: 14.5,
    lineHeight: 21,
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    backgroundColor: SHEET,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: LINE,
  },
  priceBlock: {
    gap: 2,
  },
  price: {
    color: INK,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
    fontVariant: ['tabular-nums'],
  },
  night: {
    color: MUTED,
    fontSize: 14,
    fontWeight: '500',
    letterSpacing: 0,
  },
  priceNote: {
    color: MUTED,
    fontSize: 12.5,
  },
  reserve: {
    height: 52,
    paddingHorizontal: 34,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: ACCENT,
  },
  reserveText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
});
