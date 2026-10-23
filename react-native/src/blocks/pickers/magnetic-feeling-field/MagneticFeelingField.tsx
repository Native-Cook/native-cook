import { Atlas, BlurMask, BlurStyle, Canvas, Circle, FilterMode, Group, LinearGradient, MipmapMode, Oval, RadialGradient, Rect, RoundedRect, Shadow, Skia, type SkImage, matchFont, rrect, rect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = {
  autoPlay?: boolean;
  /** Freeze the same deterministic frame as the Swift reference, in seconds. */
  previewTime?: number;
};

type Point = { x: number; y: number };
type Tone = [number, number, number];
type Scene = { focus: Point; wake: Point; tone: Tone; activity: number };
type LabelGlyph = { character: string; center: number; width: number; scaleX: number; blur: number; opacity: number };
type LabelTransition = { from: LabelGlyph[]; target: number; started: number };
type LabelLayout = LabelGlyph[][];

export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const BACKGROUND = '#eef0ea';
const INK = '#262e2b';
const SERIF = Platform.OS === 'ios' ? 'ui-serif' : 'serif';
const MONO = Platform.OS === 'ios' ? 'ui-monospace' : 'monospace';
const WORDS = ['Serene', 'Wonder', 'Tender'];
const LABEL_CHARACTERS = Array.from(new Set(WORDS.join('')));
const LABEL_PADDING = 12;
const LABEL_RASTER = 3;
const LABEL_ATLAS_ROWS = 16;
const PANEL_CLIP = rrect(rect(7, 370, 379, 371), 38, 38);

function wallTime() {
  'worklet';
  return Date.now() / 1000;
}

function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function smooth(value: number) {
  'worklet';
  const t = clamp(value);
  return t * t * (3 - 2 * t);
}

function script(t: number): Point {
  'worklet';
  const points = [{ x: 0.5, y: 0.5 }, { x: 0.82, y: 0.42 }, { x: 0.38, y: 0.18 }, { x: 0.20, y: 0.66 }, { x: 0.60, y: 0.82 }, { x: 0.85, y: 0.36 }, { x: 0.80, y: 0.68 }, { x: 0.72, y: 0.48 }, { x: 0.5, y: 0.5 }];
  const times = [1.1, 1.85, 2.6, 3.4, 4.2, 5.0, 5.8, 6.5, 7.45];
  if (t <= times[0]) return points[0];
  for (let index = 1; index < times.length; index++) {
    if (t < times[index]) { const u = smooth((t - times[index - 1]) / (times[index] - times[index - 1])); const a = points[index - 1]; const b = points[index]; return { x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u }; }
  }
  return points[points.length - 1];
}

function toneAt(point: Point): Tone {
  'worklet';
  const angle = (Math.atan2(point.y - 0.5, point.x - 0.5) / (2 * Math.PI) + 1) % 1;
  const colors = [[0.45, 0.72, 0.80], [0.55, 0.63, 0.83], [0.68, 0.59, 0.79], [0.83, 0.58, 0.73], [0.88, 0.61, 0.57], [0.88, 0.73, 0.43], [0.73, 0.78, 0.35], [0.47, 0.76, 0.66], [0.45, 0.72, 0.80]];
  const position = angle * 8; const index = Math.min(7, Math.floor(position)); const u = position - index; const a = colors[index]; const b = colors[index + 1];
  return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u];
}

function rgba(tone: Tone, opacity: number) {
  'worklet';
  return `rgba(${(tone[0] * 255).toFixed(3)},${(tone[1] * 255).toFixed(3)},${(tone[2] * 255).toFixed(3)},${opacity.toFixed(6)})`;
}

function moodAt(x: number) {
  'worklet';
  return x > 0.65 ? 1 : x < 0.35 ? 2 : 0;
}

function selectionHaptic() {
  if (Platform.OS !== 'web') void Haptics.selectionAsync().catch(() => {});
}

const DOTS = Array.from({ length: 121 }, (_, index) => { const col = index % 11; const row = Math.floor(index / 11); return { x: col * 29.6 + 24, y: row * 20.8 + 23.5, tone: toneAt({ x: col / 10, y: row / 10 }) }; });

/** A dot's drawing is derived entirely on the UI thread, with no React frame updates. */
const FieldDot = memo(function FieldDot({ x, y, tone, scene, reduced }: { x: number; y: number; tone: Tone; scene: SharedValue<Scene>; reduced: boolean }) {
  const bounds = useDerivedValue(() => {
    const s = scene.get();
    const dx = 24 + s.focus.x * 296 - x;
    const dy = 23.5 + s.focus.y * 208 - y;
    const influence = Math.exp(-(dx * dx + dy * dy) / 2550);
    const radius = 1.35 + influence * 7.65 * s.activity;
    const px = x + dx * influence * (reduced ? 0 : 0.04) - s.wake.y * influence * 0.18;
    const py = y + dy * influence * (reduced ? 0 : 0.04) + s.wake.x * influence * 0.18;
    return rect(24.5 + px - radius, 467 + py - radius, radius * 2, radius * 2);
  });
  const color = useDerivedValue(() => {
    const s = scene.get();
    const dx = 24 + s.focus.x * 296 - x;
    const dy = 23.5 + s.focus.y * 208 - y;
    const influence = Math.exp(-(dx * dx + dy * dy) / 2550);
    const amount = smooth(influence * s.activity * 2);
    const tint: Tone = [0.64 + (tone[0] - 0.64) * amount, 0.66 + (tone[1] - 0.66) * amount, 0.67 + (tone[2] - 0.67) * amount];
    return rgba(tint, 0.20 + (reduced ? 0.23 : 0.63) * influence * s.activity);
  });
  return <Oval rect={bounds} color={color} />;
});

/** Per-glyph poses are plain shareable data; native shaping happens once on the RN thread. */
function makeLabelTypography() {
  const font = matchFont({ fontFamily: Platform.OS === 'ios' ? '.AppleSystemUIFont' : 'sans-serif', fontSize: 20.5, fontWeight: '600' });
  const provider = Skia.TypefaceFontProvider.Make();
  const face = font.getTypeface();
  if (face) provider.registerFont(face, 'MagneticFeelingFieldLabel');
  const layouts = WORDS.map((name) => {
    const paragraph = Skia.ParagraphBuilder.Make({}, provider)
      .pushStyle({ fontFamilies: ['MagneticFeelingFieldLabel'], fontSize: 20.5, fontStyle: { weight: 600 }, letterSpacing: -0.4 })
      .addText(name).build();
    paragraph.layout(323);
    return Array.from(name).map((character, index): LabelGlyph => {
      // Shape the whole word so pairs such as Te and Wo keep their native kerning.
      const left = paragraph.getRectsForRange(index, index + 1)[0]?.x ?? 0;
      const width = font.getGlyphWidths(font.getGlyphIDs(character))[0] ?? 0;
      return { character, center: left + width / 2, width, scaleX: 1, blur: 0, opacity: 1 };
    });
  });
  const metrics = font.getMetrics();
  const baseline = (24.5 - metrics.descent + metrics.ascent) / 2 - metrics.ascent;
  const cellWidth = Math.ceil(Math.max(...font.getGlyphWidths(font.getGlyphIDs(LABEL_CHARACTERS.join('')))) + LABEL_PADDING * 2);
  const cellHeight = Math.ceil(24.5 + LABEL_PADDING * 2);
  const surface = Skia.Surface.MakeOffscreen(cellWidth * LABEL_RASTER * LABEL_CHARACTERS.length * 2, cellHeight * LABEL_RASTER * LABEL_ATLAS_ROWS);
  if (!surface) throw new Error('Magnetic Feeling Field: could not allocate glyph atlas.');
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('transparent'));
  canvas.scale(LABEL_RASTER, LABEL_RASTER);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color('white'));
  for (let step = 0; step <= 30; step++) {
    paint.setMaskFilter(step === 0 ? null : Skia.MaskFilter.MakeBlur(BlurStyle.Normal, step / 10, true));
    LABEL_CHARACTERS.forEach((character, index) => {
      const column = Math.floor(step / LABEL_ATLAS_ROWS) * LABEL_CHARACTERS.length + index;
      const row = step % LABEL_ATLAS_ROWS;
      canvas.drawText(character, column * cellWidth + LABEL_PADDING, row * cellHeight + LABEL_PADDING + baseline, paint, font);
    });
  }
  surface.flush();
  // Cross to the UI renderer as a raster image, avoiding GPU-context snapshot lifetime issues.
  const snapshot = surface.makeImageSnapshot();
  const atlas = snapshot.makeNonTextureImage() ?? snapshot;
  return { layouts, atlas, cellWidth, cellHeight };
}

function transitionPose(transition: LabelTransition, time: number, layouts: LabelLayout): LabelGlyph[] {
  'worklet';
  return transition.from.map((old, index) => {
    const next = layouts[transition.target][index];
    const u = clamp((time - transition.started - index * 0.019) / 0.12);
    if (u <= 0) return old;
    if (u >= 1) return next;
    const travel = smooth(u);
    const center = old.center + (next.center - old.center) * travel;
    if (old.character === next.character) return { ...next, center, scaleX: old.scaleX + (1 - old.scaleX) * travel, blur: old.blur * (1 - travel), opacity: old.opacity + (1 - old.opacity) * travel };
    if (u < 0.5) {
      const p = smooth(u * 2);
      return { ...old, center, scaleX: old.scaleX + (0.25 - old.scaleX) * p, blur: old.blur + (3 - old.blur) * p, opacity: old.opacity + (0.38 - old.opacity) * p };
    }
    const p = smooth((u - 0.5) * 2);
    return { ...next, center, scaleX: 0.25 + 0.75 * p, blur: 3 * (1 - p), opacity: 0.38 + 0.62 * p };
  });
}

function makeLabelEvents(layouts: LabelLayout) {
  const events: LabelTransition[] = [{ from: layouts[0], target: 0, started: 0 }];
  let previous = 0;
  for (let step = 1; step <= 1920; step++) {
    const time = step / 240;
    const next = moodAt(script(time).x);
    if (next === previous) continue;
    let lower = (step - 1) / 240;
    let upper = time;
    for (let i = 0; i < 24; i++) {
      const mid = (lower + upper) / 2;
      if (moodAt(script(mid).x) === previous) lower = mid; else upper = mid;
    }
    const start = (lower + upper) / 2;
    events.push({ from: transitionPose(events[events.length - 1], start, layouts), target: next, started: start });
    previous = next;
  }
  return events;
}

function loopLabelPose(raw: number, layouts: LabelLayout, events: LabelTransition[]): LabelGlyph[] {
  'worklet';
  const t = ((raw % LOOP) + LOOP) % LOOP;
  let event = events[0];
  for (let i = 1; i < events.length && events[i].started <= t; i++) event = events[i];
  return transitionPose(event, t, layouts);
}

function returnCrossing(x: number): number {
  'worklet';
  if (x >= 0.35 && x <= 0.65) return -1;
  const p = x > 0.65 ? (x - 0.65) / (x - 0.5) : (0.35 - x) / (0.5 - x);
  let lower = 0;
  let upper = 1;
  for (let i = 0; i < 24; i++) {
    const mid = (lower + upper) / 2;
    if (smooth(mid) < p) lower = mid; else upper = mid;
  }
  return (lower + upper) / 2;
}

function currentLabelPose(time: number, focus: Point, touch: number, held: boolean, fingerX: number, epoch: number, reduced: boolean, autoPlay: boolean, previewTime: number | undefined, transition: LabelTransition, layouts: LabelLayout, events: LabelTransition[]): LabelGlyph[] {
  'worklet';
  if (reduced) return layouts[moodAt(focus.x)];
  if (previewTime !== undefined) return loopLabelPose(previewTime, layouts, events);
  if (touch >= 0) {
    const crossing = returnCrossing(fingerX);
    if (!held && crossing >= 0) {
      const start = touch + 2 + 0.6 * crossing;
      if (time >= start) return transitionPose({ from: transitionPose(transition, start, layouts), target: 0, started: start }, time, layouts);
    }
    return transitionPose(transition, time, layouts);
  }
  return autoPlay ? loopLabelPose(Math.max(0, time - epoch), layouts, events) : layouts[0];
}

/** Each sprite already contains its natural-width blur; only the cached bitmap is compressed. */
const FeelingGlyph = memo(function FeelingGlyph({ index, glyphs, scene, atlas, cellWidth, cellHeight }: { index: number; glyphs: SharedValue<LabelGlyph[]>; scene: SharedValue<Scene>; atlas: SkImage; cellWidth: number; cellHeight: number }) {
  const transforms = useMemo(() => [Skia.RSXform(1 / LABEL_RASTER, 0, 0, 0)], []);
  const sprites = useDerivedValue(() => {
    const glyph = glyphs.get()[index];
    const column = LABEL_CHARACTERS.indexOf(glyph.character);
    const row = Math.round(clamp(glyph.blur, 0, 3) * 10);
    return [rect((Math.floor(row / LABEL_ATLAS_ROWS) * LABEL_CHARACTERS.length + column) * cellWidth * LABEL_RASTER, (row % LABEL_ATLAS_ROWS) * cellHeight * LABEL_RASTER, cellWidth * LABEL_RASTER, cellHeight * LABEL_RASTER)];
  });
  const transform = useDerivedValue(() => {
    const glyph = glyphs.get()[index];
    return [{ translateX: 35 + glyph.center }, { translateY: 421.5 }, { scaleX: glyph.scaleX }, { translateX: -glyph.width / 2 - LABEL_PADDING }, { translateY: -LABEL_PADDING }];
  });
  const colors = useDerivedValue(() => [Skia.Color(rgba(scene.get().tone, 0.83 * glyphs.get()[index].opacity))]);
  return <Group transform={transform}><Atlas image={atlas} sprites={sprites} transforms={transforms} colors={colors} colorBlendMode="modulate" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Group>;
});

/** A dream's emotional trace, gathered by a finger into a field of colored dots. */
export function MagneticFeelingField({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const [interacting, setInteracting] = useState(false);
  const [saved, setSaved] = useState(false);
  const [mood, setMood] = useState(0);
  const [resetAt, setResetAt] = useState(-1);
  const reduced = useReducedMotion();
  const typography = useMemo(() => makeLabelTypography(), []);
  const layouts = typography.layouts;
  const events = useMemo(() => makeLabelEvents(layouts), [layouts]);
  const labelTransition = useSharedValue<LabelTransition>({ from: layouts[0], target: 0, started: 0 });
  const [now] = useState(wallTime);
  const clock = useSharedValue(now);
  const epoch = useSharedValue(now);
  const touched = useSharedValue(-1);
  const held = useSharedValue(false);
  const finger = useSharedValue<Point>({ x: 0.5, y: 0.5 });
  const velocity = useSharedValue<Point>({ x: 0, y: 0 });
  const lastMood = useSharedValue(0);
  const lastHaptic = useSharedValue(-1);

  const scene = useDerivedValue<Scene>(() => {
    const time = clock.get();
    const touch = touched.get();
    const elapsed = touch < 0 ? 0 : Math.max(0, time - touch);
    const t = ((previewTime ?? Math.max(0, time - epoch.get())) % LOOP + LOOP) % LOOP;
    let focus: Point = { x: 0.5, y: 0.5 };
    if (previewTime !== undefined) {
      focus = script(t);
    } else if (reduced) {
      if (touch >= 0) focus = finger.get();
    } else if (touch >= 0 && (held.get() || elapsed < 2.6)) {
      const from = finger.get();
      const p = held.get() || elapsed < 2 ? 0 : smooth((elapsed - 2) / 0.6);
      focus = { x: from.x + (0.5 - from.x) * p, y: from.y + (0.5 - from.y) * p };
    } else if (autoPlay) {
      focus = script(t);
    }
    let wake: Point = { x: 0, y: 0 };
    if (!reduced && touch >= 0) {
      const fade = Math.exp(-elapsed * 8);
      wake = { x: velocity.get().x * fade, y: velocity.get().y * fade };
    } else if (!reduced && autoPlay) {
      const a = script(t);
      const b = script(Math.max(0, t - 0.08));
      wake = { x: (a.x - b.x) * 296 * 0.35, y: (a.y - b.y) * 208 * 0.35 };
    }
    let activity = smooth(Math.hypot(focus.x - 0.5, focus.y - 0.5) / 0.22);
    if (touch >= 0) activity = Math.max(activity, held.get() ? 1 : 1 - smooth((elapsed - 2) / 0.6));
    return { focus, wake, tone: toneAt(focus), activity: 0.18 + 0.82 * activity };
  });

  const glyphs = useDerivedValue(() => currentLabelPose(clock.get(), scene.get().focus, touched.get(), held.get(), finger.get().x, epoch.get(), reduced, autoPlay, previewTime, labelTransition.get(), layouts, events));

  const retargetLabel = (point: Point, time: number, capturing: boolean) => {
    'worklet';
    const target = moodAt(point.x);
    if (!capturing && target === labelTransition.get().target) return;
    const from = currentLabelPose(time, scene.get().focus, touched.get(), held.get(), finger.get().x, epoch.get(), reduced, autoPlay, previewTime, labelTransition.get(), layouts, events);
    labelTransition.set({ from, target, started: time });
  };

  const frames = useFrameCallback(() => {
    const time = Date.now() / 1000;
    if (touched.get() >= 0 && !held.get() && time - touched.get() >= 2.6) {
      touched.set(-1);
      scheduleOnRN(setInteracting, false);
    }
    clock.set(time);
  }, false);

  useEffect(() => {
    frames.setActive(!reduced && previewTime === undefined && (autoPlay || interacting));
    return () => frames.setActive(false);
  }, [autoPlay, reduced, previewTime, interacting, frames]);

  useAnimatedReaction(() => moodAt(scene.get().focus.x), (value, before) => {
    if (value !== before) scheduleOnRN(setMood, value);
  });

  useEffect(() => {
    if (resetAt < 0) return;
    const timer = setTimeout(() => scheduleOnUI(() => {
      'worklet';
      if (!held.get() && touched.get() === resetAt) { touched.set(-1); clock.set(wallTime()); scheduleOnRN(setInteracting, false); }
    }), 2600);
    return () => clearTimeout(timer);
  }, [resetAt, held, touched, clock]);
  const beginInteraction = useCallback(() => { setResetAt(-1); setInteracting(true); }, []);
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const time = wallTime();
      clock.set(time);
      const point = { x: clamp((event.x - 24) / 296), y: clamp((event.y - 23.5) / 208) };
      retargetLabel(point, time, true);
      held.set(true);
      touched.set(time);
      velocity.set({ x: 0, y: 0 });
      finger.set(point);
      const next = moodAt(point.x);
      if (next !== lastMood.get() && time - lastHaptic.get() > 0.18) {
        lastMood.set(next);
        lastHaptic.set(time);
        scheduleOnRN(selectionHaptic);
      }
      scheduleOnRN(beginInteraction);
    })
    .onUpdate((event) => {
      const time = wallTime();
      clock.set(time);
      const point = { x: clamp((event.x - 24) / 296), y: clamp((event.y - 23.5) / 208) };
      retargetLabel(point, time, false);
      touched.set(time);
      // Swift's predicted-end translation supplies a short velocity projection.
      // A 0.2 s projection gives that same bounded, 0.06-strength wake here.
      velocity.set({ x: clamp(event.velocityX * 0.2 * 0.06, -12, 12), y: clamp(event.velocityY * 0.2 * 0.06, -12, 12) });
      finger.set(point);
      const next = moodAt(point.x);
      if (next !== lastMood.get() && time - lastHaptic.get() > 0.18) {
        lastMood.set(next);
        lastHaptic.set(time);
        scheduleOnRN(selectionHaptic);
      }
    })
    .onFinalize(() => {
      const time = wallTime();
      held.set(false);
      touched.set(time);
      clock.set(time);
      epoch.set(time + 2.6);
      scheduleOnRN(setResetAt, time);
    });

  const backgroundTint = useDerivedValue(() => rgba(scene.get().tone, 0.10 * scene.get().activity));
  const gradientCenter = useDerivedValue(() => ({ x: 7 + scene.get().focus.x * 379, y: 370 + scene.get().focus.y * 371 }));
  const gradientColors = useDerivedValue(() => {
    const s = scene.get();
    return [rgba(s.tone, 0.43 * s.activity), rgba(s.tone, 0.43 * s.activity), rgba(s.tone, 0.19 * s.activity), rgba(s.tone, 0)];
  });
  const haloCenter = useDerivedValue(() => ({ x: 48.5 + scene.get().focus.x * 296, y: 490.5 + scene.get().focus.y * 208 }));
  const haloRadius = useDerivedValue(() => 12 + 6 * scene.get().activity);
  const haloStart = useDerivedValue(() => { const c = haloCenter.get(); return { x: c.x - 10, y: c.y - 14 }; });
  const haloEnd = useDerivedValue(() => { const c = haloCenter.get(); return { x: c.x + 12, y: c.y + 16 }; });
  const haloColors = useDerivedValue(() => ['rgba(255,255,255,0.46)', rgba(scene.get().tone, 0.23), 'rgba(255,255,255,0.30)']);
  const haloRim = useDerivedValue(() => rgba([1, 1, 1], 0.79 * scene.get().activity));
  const haloShadow = useDerivedValue(() => rgba([1, 1, 1], 0.72 * scene.get().activity));
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT);

  const save = () => {
    scheduleOnUI(() => {
      'worklet';
      const time = wallTime();
      clock.set(time);
      const shown = scene.get().focus;
      retargetLabel(shown, time, true);
      finger.set(shown);
      velocity.set({ x: 0, y: 0 });
      touched.set(time);
      epoch.set(time + 2.6);
      scheduleOnRN(setInteracting, true);
      scheduleOnRN(setResetAt, time);
    });
    setSaved((value) => !value);
    if (Platform.OS !== 'web') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  return (
    <View style={styles.screen} onLayout={(event) => {
      const { width, height } = event.nativeEvent.layout;
      setSize((before) => before.width === width && before.height === height ? before : { width, height });
    }}>
      <StatusBar style="dark" />
      <View style={[styles.design, { left: (size.width - WIDTH * scale) / 2, top: (size.height - HEIGHT * scale) / 2, transform: [{ scale }] }]}>
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Circle dither cx={215} cy={590} r={210} color={backgroundTint}><BlurMask blur={75} style="normal" /></Circle>
        </Canvas>
        <View style={styles.brandRow}>
          <Text style={styles.brand}>REVERIE / JOURNAL</Text>
          <Svg width={22} height={22} viewBox="0 0 24 24" fill="none">
            <Path d="M15.5 4.8A8 8 0 1 0 20 15a7 7 0 0 1-4.5-10.2Z" stroke={INK} strokeWidth={1.1} strokeLinejoin="round" />
            <Path d="m18.3 2.5.8 2.1 2.1.8-2.1.8-.8 2.1-.8-2.1-2.1-.8 2.1-.8Zm3 7.5.4 1.1 1.1.4-1.1.4-.4 1.1-.4-1.1-1.1-.4 1.1-.4Z" fill={INK} />
          </Svg>
        </View>
        <Text style={styles.title}>{'A trace of\nlast night.'}</Text>
        <Text style={styles.date}>FRIDAY, OCTOBER 16</Text>
        <View style={styles.entry}>
          <View style={styles.entryRow}>
            <Text style={styles.entryTitle}>THE FLOATING GARDEN</Text>
            <Text style={styles.entryTime}>06:42</Text>
          </View>
          <Text style={styles.entryBody}>{'I followed a paper boat through a garden\nwhere the flowers floated in the air.'}</Text>
        </View>
        <View style={styles.panel} />
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Group clip={PANEL_CLIP}>
            <Rect dither x={7} y={370} width={379} height={371}>
              <RadialGradient c={gradientCenter} r={315} colors={gradientColors} positions={[0, 12 / 315, 163.5 / 315, 1]} />
            </Rect>
          </Group>
          <RoundedRect x={7} y={370} width={379} height={371} r={38} style="stroke" strokeWidth={0.8} color="rgba(255,255,255,0.62)" />
          <RoundedRect x={24.5} y={467} width={344} height={255} r={30} color="rgba(255,255,255,0.29)" />
          <RoundedRect x={24.5} y={467} width={344} height={255} r={30} style="stroke" strokeWidth={0.8}>
            <LinearGradient start={{ x: 24.5, y: 467 }} end={{ x: 368.5, y: 722 }} colors={['rgba(255,255,255,0.72)', 'rgba(255,255,255,0.11)', 'rgba(255,255,255,0.58)']} />
          </RoundedRect>
          {DOTS.map((dot, index) => <FieldDot key={index} {...dot} scene={scene} reduced={reduced} />)}
          {!reduced && <>
            <Circle c={haloCenter} r={haloRadius}><LinearGradient start={haloStart} end={haloEnd} colors={haloColors} /><Shadow dx={0} dy={0} blur={9} color={haloShadow} /></Circle>
            <Circle c={haloCenter} r={haloRadius} style="stroke" strokeWidth={1.3} color={haloRim} />
          </>}
        </Canvas>
        <Text style={styles.panelTitle}>This dream left me feeling</Text>
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill} accessible={false} colorSpace={Platform.OS === 'ios' ? 'srgb' : 'p3'}>
          {Array.from({ length: 6 }, (_, index) => <FeelingGlyph key={index} index={index} glyphs={glyphs} scene={scene} atlas={typography.atlas} cellWidth={typography.cellWidth} cellHeight={typography.cellHeight} />)}
        </Canvas>
        <GestureDetector gesture={pan}>
          <View style={styles.fieldTouch} accessible accessibilityLabel="Feeling field" accessibilityValue={{ text: WORDS[mood] }} />
        </GestureDetector>
        <Text style={styles.caption}>SLIDE TO FIND THE FEELING</Text>
        <Pressable style={styles.saveTouch} onPress={save} accessibilityRole="button" accessibilityLabel={saved ? 'Feeling saved' : 'Keep this feeling'}>
          <View style={styles.savePill}>
            <Text style={styles.saveText}>{saved ? 'Feeling saved' : 'Keep this feeling'}</Text>
            <Svg width={15} height={15} viewBox="0 0 16 16" fill="none">
              <Path d={saved ? 'm3 8 3.2 3.2L13 4.4' : 'M4 12 12 4M4.5 4H12v7.5'} stroke="#f2f5ed" strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
          </View>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  design: { position: 'absolute', width: WIDTH, height: HEIGHT, transformOrigin: 'top left' },
  brandRow: { position: 'absolute', left: 24, top: 80, width: 345, height: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  brand: { color: INK, fontSize: 10, lineHeight: 12, fontWeight: '600', fontFamily: MONO, letterSpacing: 2 },
  title: { position: 'absolute', left: 24, top: 113.5, color: INK, fontFamily: SERIF, fontSize: 38, lineHeight: 45.2, letterSpacing: -1.4, includeFontPadding: false },
  date: { position: 'absolute', left: 24, top: 218, color: 'rgba(38.25,45.9,43.35,0.48)', fontSize: 10, lineHeight: 12, fontWeight: '500', fontFamily: MONO, letterSpacing: 1.5 },
  entry: { position: 'absolute', left: 24, top: 245, width: 345, height: 112, padding: 20, gap: 12, borderRadius: 22, borderCurve: 'continuous', backgroundColor: 'rgba(255,255,255,0.42)' },
  entryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  entryTitle: { color: 'rgba(38.25,45.9,43.35,0.65)', fontSize: 10, lineHeight: 12, fontWeight: '600', letterSpacing: 1.5 },
  entryTime: { color: 'rgba(38.25,45.9,43.35,0.65)', fontSize: 10, lineHeight: 12, fontFamily: MONO },
  entryBody: { color: 'rgba(38.25,45.9,43.35,0.65)', fontSize: 15, lineHeight: 23, marginTop: -2.5, includeFontPadding: false },
  panel: { position: 'absolute', left: 7, top: 370, width: 379, height: 371, borderRadius: 38, borderCurve: 'continuous', backgroundColor: 'rgba(255,255,255,0.64)', boxShadow: '0 2px 2px rgba(38,46,43,0.025), 0 12px 21px rgba(38,46,43,0.045)' },
  panelTitle: { position: 'absolute', left: 35, top: 396, color: INK, fontSize: 20.5, lineHeight: 24.5, fontWeight: '600', letterSpacing: -0.4, includeFontPadding: false },
  fieldTouch: { position: 'absolute', left: 24.5, top: 467, width: 344, height: 255, borderRadius: 30 },
  caption: { position: 'absolute', top: 747, width: 393, color: 'rgba(38.25,45.9,43.35,0.4)', fontSize: 9, lineHeight: 12, fontWeight: '500', fontFamily: MONO, letterSpacing: 1.6, textAlign: 'center' },
  saveTouch: { position: 'absolute', left: 24, top: 774, width: 345, height: 44, justifyContent: 'center' },
  savePill: { height: 42, width: 345, borderRadius: 21, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: INK },
  saveText: { color: '#f2f5ed', fontSize: 14, lineHeight: 18, fontWeight: '500' },
});
