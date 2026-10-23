import {
  BlendMode,
  Canvas,
  ClipOp,
  type SkCanvas,
  type SkColor,
  Picture,
  Skia,
  TileMode,
  createPicture,
  useFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Block, type Frame, layoutBlock, makeFrame, random, strips } from './glitch';

/*
 RGB Glitch Headline: a still, light geometric headline that is hit by bursts of signal interference.

 Timeline of the self-playing loop (LOOP = 4.8 s); the headline is clean and still outside the bursts,
 which is also the resting frame:
   0.90–1.18  burst, strength 0.7
   2.05–2.21  short burst, strength 0.45
   3.30–3.74  long burst, strength 1.0
 A burst is stepped, never eased: every 40 ms it picks a new frame from a seeded hash (the same every
 loop): 2–5 horizontal bands of the headline knocked sideways by 8–50 % of the cap height, a red copy and
 a cyan copy split apart by 4–14 % of it (added together they make white again), sometimes a 10° shear of
 the whole block, a band whose white layer drops out, and up to two thin noise lines. Each step's
 strength is the burst's strength times 0.55–1.
 The "LIVE" dot pulses with a 1.2 s period, four times a loop.

 Touch: a tap fires a 0.3 s burst (strength 0.85). Dragging tears the band under the finger sideways
 with the finger (0.9 × the drag, at most 40 % of the width) while the rest jams lightly (strength
 0.35), the colour split growing with the tear; letting go snaps it back with a 0.25 s burst. 2 s after
 the last touch the loop starts over. Reduced motion: the headline stays clean, the dot still, and touch
 does nothing.
*/

/** The self-playing loop, in seconds. */
export const LOOP = 4.8;

/** The loop's bursts: start in the loop, duration, strength, seed. */
const BURSTS = [
  [0.9, 0.28, 0.7, 11],
  [2.05, 0.16, 0.45, 23],
  [3.3, 0.44, 1.0, 37],
];

const STEP = 0.04;
const TAP = 0.3;
const TAP_AMOUNT = 0.85;
const RELEASE = 0.25;
const RELEASE_AMOUNT = 0.7;
const JAM = 0.35;
const IDLE = 2;
const PULSE = 1.2;
/** Where the middle of the headline sits, as a share of the screen's height. */
const CENTRE = 0.43;

const BACK = '#050506';
const LIFT = '#111116';
const RED = '#ff2a55';
const CYAN = '#00f0ff';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

type Tear = { active: boolean; y: number; dx: number; start: number };
type Shot = { start: number; duration: number; amount: number; seed: number };

function rgba(hex: string, alpha: number): SkColor {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, alpha);
}

function stepFrame(seed: number, e: number, amount: number, block: Block): Frame {
  'worklet';
  const step = Math.floor(e / STEP);
  const strength = amount * (0.55 + 0.45 * random((Math.imul(seed, 977) + step) | 0));
  return makeFrame(seed, step, strength, block.top, block.bottom, block.capHeight);
}

function glitchAt(now: number, block: Block, tear: Tear, shot: Shot, loopStart: number, held: boolean, playing: boolean): Frame | null {
  'worklet';
  if (tear.active) {
    const frame = makeFrame(101, Math.floor((now - tear.start) / STEP), JAM, block.top, block.bottom, block.capHeight);
    const half = block.capHeight * 0.28;
    frame.bands.push({ y0: tear.y - half, y1: tear.y + half, dx: tear.dx, hidden: false });
    const split = Math.min(Math.abs(tear.dx) * 0.12, block.capHeight * 0.2);
    frame.split = tear.dx < 0 ? -Math.max(Math.abs(frame.split), split) : Math.max(Math.abs(frame.split), split);
    return frame;
  }
  const e = now - shot.start;
  if (shot.duration > 0 && e >= 0 && e < shot.duration) return stepFrame(shot.seed, e, shot.amount, block);
  if (!playing || held) return null;
  const t = (((now - loopStart) % LOOP) + LOOP) % LOOP;
  for (const [start, duration, amount, seed] of BURSTS) {
    if (t >= start && t < start + duration) return stepFrame(seed, t - start, amount, block);
  }
  return null;
}

function drawHeadline(canvas: SkCanvas, width: number, height: number, block: Block, frame: Frame | null) {
  'worklet';
  const back = Skia.Paint();
  back.setShader(
    Skia.Shader.MakeRadialGradient(Skia.Point(block.centreX, block.centreY), height * 0.7, [rgba(LIFT, 1), rgba(BACK, 1)], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), back);

  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const cap = block.capHeight;
  const top = block.top - cap * 0.5;
  const bottom = block.bottom + cap * 0.5;
  if (frame) {
    const cut = strips(frame, top, bottom);
    const layers: [string, number, boolean][] = [
      [RED, frame.split, true],
      [CYAN, -frame.split, true],
      ['#ffffff', 0, false],
    ];
    for (const [colour, shift, additive] of layers) {
      paint.setColor(rgba(colour, 1));
      paint.setBlendMode(additive ? BlendMode.Plus : BlendMode.SrcOver);
      for (const strip of cut) {
        if (strip.hidden && !additive) continue;
        canvas.save();
        canvas.clipRect(Skia.XYWHRect(0, strip.y0, width, strip.y1 - strip.y0), ClipOp.Intersect, true);
        canvas.translate(strip.dx + frame.jolt + shift, 0);
        // x' = x − shear · (y − centre): the tops lean right.
        if (frame.shear !== 0) canvas.concat([1, -frame.shear, frame.shear * block.centreY, 0, 1, 0, 0, 0, 1]);
        canvas.drawPath(block.path, paint);
        canvas.restore();
      }
    }
    paint.setBlendMode(BlendMode.SrcOver);
    for (const [y, alpha] of frame.noise) {
      paint.setColor(rgba('#ffffff', alpha));
      canvas.drawRect(Skia.XYWHRect(0, y, width, 1), paint);
      paint.setColor(rgba(CYAN, alpha * 0.6));
      canvas.drawRect(Skia.XYWHRect(0, y + 2, width, 1), paint);
    }
  } else {
    paint.setColor(rgba('#ffffff', 1));
    canvas.drawPath(block.path, paint);
  }

  // Scan lines over the headline: a 1 pt dark line every 4 pt.
  const scan = Skia.Path.Make();
  for (let y = top; y < bottom; y += 4) scan.addRect(Skia.XYWHRect(0, y, width, 1));
  paint.setColor(rgba(BACK, 0.2));
  canvas.drawPath(scan, paint);
}

type Props = {
  lines?: string[];
  channel?: string;
  programme?: string;
  live?: string;
  subtitle?: string;
  action?: string;
  hint?: string;
  /** Plays the interference by itself, every `LOOP` seconds round. */
  autoPlay?: boolean;
  onAction?: () => void;
};

/**
 * A still, light geometric headline hit by stepped bursts of signal interference: bands knocked
 * sideways, a red and a cyan copy split apart, a shear, scan lines. Tap to interfere, drag to tear.
 */
export function RgbGlitchHeadline({
  lines = ['NOTHING', 'IS', 'STATIC'],
  channel = 'CH 07',
  programme = 'Night broadcast',
  live = 'Live',
  subtitle = 'Season two airs Friday at midnight.',
  action = 'Remind me',
  hint = 'Tap to interfere · drag to tear',
  autoPlay = false,
  onAction,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const font = useFont(require('./assets/Jost-Light.ttf'), 100);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const key = lines.join('\n');
  const block = useMemo(
    () => (font && size.width > 0 ? layoutBlock(font, key.split('\n'), size.width, size.height * CENTRE) : null),
    [font, key, size.width, size.height],
  );

  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const shot = useSharedValue<Shot>({ start: 0, duration: 0, amount: 0, seed: 0 });
  const tear = useSharedValue<Tear>({ active: false, y: 0, dx: 0, start: 0 });
  /** From the first touch until the loop starts over. */
  const held = useSharedValue(false);
  const dragging = useSharedValue(false);
  const touchEnd = useSharedValue(-1);
  const touches = useSharedValue(0);
  const tick = useSharedValue(0);
  const playing = autoPlay && !reduced;

  const haptic = (kind: number) => {
    if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (loopStart.get() < 0) loopStart.set(now);
    // 2 s after the last touch the loop starts over from its clean frame.
    if (touchEnd.get() >= 0 && !dragging.get() && now - touchEnd.get() >= IDLE) {
      touchEnd.set(-1);
      shot.set({ start: 0, duration: 0, amount: 0, seed: 0 });
      held.set(false);
      loopStart.set(now);
    }
  });

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const frame = block && !reduced ? glitchAt(now, block, tear.get(), shot.get(), loopStart.get(), held.get(), playing) : null;
    return createPicture((canvas) => {
      if (!block) return;
      drawHeadline(canvas, size.width, size.height, block, frame);
    });
  });

  const dot = useAnimatedStyle(() => ({
    opacity: reduced ? 1 : 0.35 + 0.65 * (0.5 + 0.5 * Math.cos((2 * Math.PI * (clock.get() - loopStart.get())) / PULSE)),
  }));

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      dragging.set(true);
      held.set(true);
      touchEnd.set(-1);
      tick.set(0);
      scheduleOnRN(haptic, 0);
    })
    .onUpdate((e) => {
      if (reduced) return;
      const moved = Math.hypot(e.translationX, e.translationY) > 6;
      const t = tear.get();
      if (!moved && !t.active) return;
      const limit = size.width * 0.4;
      const dx = Math.min(Math.max(e.translationX * 0.9, -limit), limit);
      tear.set({ active: true, y: e.y, dx, start: t.active ? t.start : clock.get() });
      const notch = Math.trunc(dx / 40);
      if (notch !== tick.get()) {
        tick.set(notch);
        scheduleOnRN(haptic, 2);
      }
    })
    .onFinalize(() => {
      dragging.set(false);
      touches.set(touches.get() + 1);
      touchEnd.set(clock.get());
      if (reduced) return;
      const released = tear.get().active;
      tear.set({ active: false, y: 0, dx: 0, start: 0 });
      shot.set({
        start: clock.get(),
        duration: released ? RELEASE : TAP,
        amount: released ? RELEASE_AMOUNT : TAP_AMOUNT,
        seed: 300 + touches.get(),
      });
      scheduleOnRN(haptic, 1);
    });

  const press = () => {
    touches.set(touches.get() + 1);
    held.set(true);
    touchEnd.set(clock.get());
    if (!reduced) {
      shot.set({ start: clock.get(), duration: TAP, amount: TAP_AMOUNT, seed: 300 + touches.get() });
      haptic(1);
    }
    onAction?.();
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const bottomInset = Math.max(insets.bottom, 16);
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill} accessibilityRole="header" accessibilityLabel={lines.join(' ')}>
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.bar, { top: insets.top + 16 }]}>
        <Text style={[styles.mono, styles.channel]} numberOfLines={1}>
          {channel.toUpperCase()}
        </Text>
        <Text style={[styles.mono, styles.programme]} numberOfLines={1}>
          {programme.toUpperCase()}
        </Text>
        <View style={styles.spacer} />
        <Animated.View style={[styles.dot, dot]} />
        <Text style={[styles.mono, styles.live]} numberOfLines={1}>
          {live.toUpperCase()}
        </Text>
      </View>

      {block && (
        <View pointerEvents="box-none" style={[styles.below, { top: block.bottom + block.capHeight * 0.5 + 28 }]}>
          <Text style={styles.subtitle} numberOfLines={2}>
            {subtitle}
          </Text>
          <Pressable accessibilityRole="button" onPress={press} style={styles.button}>
            <Text style={styles.action} numberOfLines={1}>
              {action}
            </Text>
          </Pressable>
        </View>
      )}

      <Text pointerEvents="none" style={[styles.mono, styles.hint, { top: size.height - bottomInset - 24 - 14 }]} numberOfLines={1}>
        {hint.toUpperCase()}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACK },
  bar: { position: 'absolute', left: 0, right: 0, height: 20, paddingHorizontal: 24, flexDirection: 'row', alignItems: 'center' },
  mono: { fontFamily: MONO, fontSize: 12, letterSpacing: 2 },
  channel: { color: 'rgba(255,255,255,0.75)', fontWeight: '600', marginRight: 10 },
  programme: { color: 'rgba(255,255,255,0.38)', flexShrink: 1 },
  spacer: { flex: 1, minWidth: 12 },
  dot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: RED, marginRight: 10 },
  live: { color: RED, fontWeight: '600' },
  below: { position: 'absolute', left: 0, right: 0, paddingHorizontal: 32, alignItems: 'center', gap: 24 },
  subtitle: { color: 'rgba(255,255,255,0.6)', fontSize: 15, textAlign: 'center' },
  button: { width: 168, height: 48, borderRadius: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.4)', alignItems: 'center', justifyContent: 'center' },
  action: { color: '#ffffff', fontSize: 15, fontWeight: '500', letterSpacing: 0.4 },
  hint: { position: 'absolute', left: 0, right: 0, fontSize: 11, fontWeight: '500', letterSpacing: 1.6, color: 'rgba(255,255,255,0.34)', textAlign: 'center' },
});
