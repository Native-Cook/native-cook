import { BlurMask, Canvas, Circle, Group, Path, Picture, Rect, Skia, type SkPath, type SkPicture, rect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = { autoPlay?: boolean; previewTime?: number };
type Point = { x: number; y: number };
type CardPose = { guide: number; oldProgress: number; oldAlpha: number; progress: number; alpha: number; selection: number; lift: number; tip: number };
type Pose = { cards: CardPose[]; selected: number; detailFrom: number; detailTo: number; detailMix: number };
type Mark = { path: SkPath; lookup: Point[] };

export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const PAPER = '#f6f2e9';
const RUST = '#985b40';
const SERIF = Platform.OS === 'ios' ? 'ui-serif' : 'serif';
const ITEMS = [
  { name: 'GROUND', discipline: 'PILATES', color: '#c7a186', className: 'GROUNDING PILATES', time: 'Today · 10:30 · 45 min', line: 'A little strength. A softer landing.', price: '18' },
  { name: 'BREATHE', discipline: 'BREATHWORK', color: RUST, className: 'OPEN AIR BREATHWORK', time: 'Today · 12:00 · 20 min', line: 'Find a little more room inside.', price: '12' },
  { name: 'KINDLE', discipline: 'MOVEMENT', color: '#c5a15b', className: 'EMBER MOVEMENT', time: 'Today · 17:30 · 50 min', line: 'Move with warmth, leave with light.', price: '22' },
  { name: 'FLOW', discipline: 'SLOW YOGA', color: '#8e9f96', className: 'SLOW CURRENT YOGA', time: 'Today · 18:15 · 40 min', line: 'Let the day loosen its grip.', price: '20' },
];
const ITEM_RGB = ITEMS.map((item) => [parseInt(item.color.slice(1, 3), 16), parseInt(item.color.slice(3, 5), 16), parseInt(item.color.slice(5, 7), 16)]);

function seconds() { 'worklet'; return Date.now() / 1000; }
function clamp(value: number) { 'worklet'; return Math.min(1, Math.max(0, value)); }
function ease(value: number) { 'worklet'; const u = clamp(value); return u * u * (3 - 2 * u); }
function hash(value: number) { const n = Math.sin(value * 12.9898) * 43758.5453; return n - Math.floor(n); }
function emptyCard(): CardPose { 'worklet'; return { guide: 0.44, oldProgress: 1, oldAlpha: 0, progress: 0, alpha: 0, selection: 0, lift: 0, tip: 0 }; }
function completeCard(): CardPose { 'worklet'; return { ...emptyCard(), guide: 0.13, progress: 1, alpha: 1 }; }
function restPose(): Pose {
  'worklet';
  const cards = [completeCard(), emptyCard(), emptyCard(), emptyCard()];
  cards[0].selection = 1;
  return { cards, selected: 0, detailFrom: 0, detailTo: 0, detailMix: 1 };
}
function reducedPose(index: number): Pose {
  'worklet';
  const cards = [completeCard(), completeCard(), completeCard(), completeCard()];
  cards[index].selection = 1;
  return { cards, selected: index, detailFrom: index, detailTo: index, detailMix: 1 };
}
function liftSpring(age: number) {
  'worklet';
  return age >= 0.65 ? 1 : Math.max(0, Math.min(1.045, 1 - Math.exp(-10 * age) * (Math.cos(14 * age) + (10 / 14) * Math.sin(14 * age))));
}
function transition(index: number, previous: number, progress: number, tip: number, lift: number, previousLift: number): Pose {
  'worklet';
  const cards = [emptyCard(), emptyCard(), emptyCard(), emptyCard()];
  const selection = ease(progress / 0.6);
  cards[previous].oldAlpha = 1 - ease(progress / 0.65);
  cards[previous].selection = 1 - selection;
  cards[previous].lift = previousLift * (1 - selection);
  cards[index] = { ...cards[index], guide: 0.44 - 0.31 * ease(progress / 0.3), progress, alpha: 1, selection, lift, tip };
  return { cards, selected: index, detailFrom: previous, detailTo: index, detailMix: progress };
}
function manualPose(base: Pose, index: number, progress: number, tip: number, lift: number): Pose {
  'worklet';
  const fade = ease(progress / 0.65);
  const cards = base.cards.map((old) => ({
    ...old,
    oldProgress: old.alpha >= old.oldAlpha ? old.progress : old.oldProgress,
    oldAlpha: Math.max(old.alpha, old.oldAlpha) * (1 - fade), alpha: 0,
    guide: old.guide + (0.44 - old.guide) * fade,
    selection: old.selection * (1 - fade), lift: old.lift * (1 - fade), tip: 0,
  }));
  const original = base.cards[index];
  cards[index] = { ...cards[index], guide: original.guide + (0.13 - original.guide) * fade, progress, alpha: 1, selection: original.selection + (1 - original.selection) * fade, lift: original.lift + (lift - original.lift) * fade, tip };
  return { cards, selected: index, detailFrom: base.detailMix < 0.5 ? base.detailFrom : base.detailTo, detailTo: index, detailMix: ease((progress - 0.75) / 0.25) };
}
function returningToRest(pose: Pose, amount: number): Pose {
  'worklet';
  if (amount <= 0) return pose;
  const rest = restPose();
  return {
    cards: pose.cards.map((card, index) => {
      const target = rest.cards[index];
      return { ...card, guide: card.guide + (target.guide - card.guide) * amount, oldAlpha: card.oldAlpha * (1 - amount), progress: card.progress + (target.progress - card.progress) * amount, alpha: card.alpha + (target.alpha - card.alpha) * amount, selection: card.selection + (target.selection - card.selection) * amount, lift: card.lift * (1 - amount), tip: card.tip * (1 - amount) };
    }),
    selected: amount < 0.5 ? pose.selected : 0, detailFrom: pose.detailTo, detailTo: 0, detailMix: amount,
  };
}
function loopPose(raw: number): Pose {
  'worklet';
  const t = (raw % LOOP + LOOP) % LOOP;
  if (t < 1.4 || t >= 7.6) return restPose();
  const index = t < 2.15 ? 1 : t < 2.9 ? 2 : t < 5 ? 3 : 0;
  const previous = t < 2.15 ? 0 : t < 2.9 ? 1 : t < 5 ? 2 : 3;
  const start = t < 2.15 ? 1.4 : t < 2.9 ? 2.15 : t < 5 ? 2.9 : 5;
  const duration = index === 1 || index === 2 ? 0.65 : index === 3 ? 0.9 : 1.1;
  const detailStart = index === 1 ? 1.9 : index === 2 ? 2.65 : index === 3 ? 3.65 : 5.95;
  const detailEnd = index === 1 ? 2.15 : index === 2 ? 2.9 : index === 3 ? 3.95 : 6.25;
  const age = t - start;
  const u = clamp(age / duration);
  const progress = index === 1 || index === 2 ? 1 - (1 - u) ** 3 : ease(u);
  const tip = clamp(age / 0.04) * (1 - clamp((age - duration) / 0.12));
  const pose = transition(index, previous, progress, tip, 4 * liftSpring(age), previous === 0 ? 0 : 4);
  pose.detailFrom = previous;
  pose.detailTo = index;
  pose.detailMix = ease((t - detailStart) / (detailEnd - detailStart));
  if (t >= 6.25) pose.cards[0].lift = 4 * (1 - ease((t - 6.25) / 1.35));
  return pose;
}
function detailLayer(index: number, pose: Pose) {
  'worklet';
  const mix = pose.detailMix;
  if (index === pose.detailFrom && mix < 0.5) return { opacity: Math.max(0, 1 - mix * 2), y: -4 * mix };
  if (index === pose.detailTo && mix >= 0.5) return { opacity: Math.max(0, mix * 2 - 1), y: 4 * (1 - mix) };
  return { opacity: 0, y: 0 };
}
function pointAt(mark: Mark, progress: number): Point {
  'worklet';
  const p = Math.min(127, Math.max(0, progress * 127));
  const low = Math.floor(p);
  const high = Math.min(127, low + 1);
  const u = p - low;
  return { x: mark.lookup[low].x + (mark.lookup[high].x - mark.lookup[low].x) * u, y: mark.lookup[low].y + (mark.lookup[high].y - mark.lookup[low].y) * u };
}

/** Match Swift's 24 cubic samples and 128-entry arc-length tip table once. */
function markBuilder() {
  const path = Skia.Path.Make();
  const points: Point[] = [];
  const distances: number[] = [];
  let length = 0;
  let current = { x: 0, y: 0 };
  const move = (x: number, y: number) => { current = { x, y }; path.moveTo(x, y); points.push(current); distances.push(length); };
  const curve = (x1: number, y1: number, x2: number, y2: number, x: number, y: number) => {
    const start = current;
    path.cubicTo(x1, y1, x2, y2, x, y);
    for (let step = 1; step <= 24; step++) {
      const u = step / 24;
      const v = 1 - u;
      const p = { x: v ** 3 * start.x + 3 * v * v * u * x1 + 3 * v * u * u * x2 + u ** 3 * x, y: v ** 3 * start.y + 3 * v * v * u * y1 + 3 * v * u * u * y2 + u ** 3 * y };
      length += Math.hypot(p.x - current.x, p.y - current.y);
      points.push(p); distances.push(length); current = p;
    }
  };
  const circle = (x: number, y: number, r: number) => {
    const k = r * 0.5522847498;
    move(x, y - r);
    curve(x + k, y - r, x + r, y - k, x + r, y);
    curve(x + r, y + k, x + k, y + r, x, y + r);
    curve(x - k, y + r, x - r, y + k, x - r, y);
    curve(x - r, y - k, x - k, y - r, x, y - r);
  };
  const finish = (): Mark => {
    let cursor = 1;
    const lookup: Point[] = [];
    for (let step = 0; step < 128; step++) {
      const wanted = step / 127 * length;
      while (cursor < distances.length - 1 && distances[cursor] < wanted) cursor++;
      const previous = Math.max(0, cursor - 1);
      const span = distances[cursor] - distances[previous];
      const u = span > 0 ? (wanted - distances[previous]) / span : 1;
      lookup.push({ x: points[previous].x + (points[cursor].x - points[previous].x) * u, y: points[previous].y + (points[cursor].y - points[previous].y) * u });
    }
    return { path, lookup };
  };
  return { move, curve, circle, finish };
}
function makeMarks(): Mark[] {
  const ground = markBuilder();
  ground.move(50, 9); ground.curve(17, 32, 16, 65, 50, 91); ground.curve(84, 65, 83, 32, 50, 9);
  ground.move(50, 21); ground.curve(39, 42, 40, 63, 50, 81); ground.circle(79, 14, 3.5);
  const breathe = markBuilder();
  breathe.move(50, 40); breathe.curve(34, 43, 31, 56, 48, 64); breathe.curve(62, 71, 74, 75, 81, 82);
  breathe.curve(89, 91, 66, 96, 50, 78); breathe.curve(34, 96, 11, 91, 19, 82);
  breathe.curve(28, 72, 42, 70, 55, 63); breathe.curve(69, 54, 66, 43, 50, 40); breathe.circle(50, 25, 8);
  const kindle = markBuilder();
  kindle.move(43, 92); kindle.curve(66, 72, 62, 53, 48, 35); kindle.curve(40, 23, 33, 15, 23, 12);
  kindle.move(20, 39); kindle.curve(36, 45, 47, 48, 73, 36);
  kindle.move(49, 60); kindle.curve(36, 74, 35, 83, 33, 92); kindle.circle(58, 16, 4.5); kindle.circle(80, 28, 3);
  const flow = markBuilder();
  flow.move(9, 66); flow.curve(22, 25, 37, 29, 49, 55); flow.curve(62, 83, 77, 82, 91, 43);
  flow.move(14, 47); flow.curve(29, 17, 44, 32, 55, 46); flow.circle(84, 19, 3.5);
  return [ground.finish(), breathe.finish(), kindle.finish(), flow.finish()];
}
function makePaper(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(rect(0, 0, WIDTH, HEIGHT));
  const pen = Skia.Paint();
  pen.setAntiAlias(true); pen.setColor(Skia.Color('rgba(152,91,64,0.025)')); pen.setStrokeWidth(0.55);
  for (let index = 0; index < 160; index++) {
    const x = hash(index + 0.3) * WIDTH;
    const y = hash(index + 24.7) * HEIGHT;
    const length = 2 + hash(index + 92.1) * 5;
    canvas.drawLine(x, y, x + length, y - 0.8, pen);
  }
  return recorder.finishRecordingAsPicture();
}
function haptic() { if (Platform.OS !== 'web') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

function MarkDrawing({ index, mark, pose }: { index: number; mark: Mark; pose: SharedValue<Pose> }) {
  const card = useDerivedValue(() => pose.get().cards[index]);
  const guide = useDerivedValue(() => card.get().guide);
  const oldAlpha = useDerivedValue(() => card.get().oldAlpha > 0.001 ? card.get().oldAlpha : 0);
  const oldProgress = useDerivedValue(() => card.get().oldProgress);
  const progress = useDerivedValue(() => card.get().progress);
  const alpha = useDerivedValue(() => card.get().progress > 0.0001 ? card.get().alpha : 0);
  const width = useDerivedValue(() => 3.1 + 0.5 * card.get().tip);
  const tailStart = useDerivedValue(() => Math.max(0, card.get().progress - 0.12));
  const tipAlpha = useDerivedValue(() => card.get().tip > 0.001 && card.get().progress > 0 && card.get().progress < 1.0001 ? card.get().tip : 0);
  const tailAlpha = useDerivedValue(() => 0.35 * tipAlpha.get());
  const glowAlpha = useDerivedValue(() => 0.22 * tipAlpha.get());
  const coreAlpha = useDerivedValue(() => 0.65 * tipAlpha.get());
  const tip = useDerivedValue(() => pointAt(mark, card.get().progress));
  const underline = useDerivedValue(() => rect(79.5 - 23 * card.get().progress, 123.6, 46 * card.get().progress, 0.8));
  const underlineAlpha = useDerivedValue(() => 0.38 * card.get().selection);
  const stroke = { style: 'stroke', strokeWidth: 3.1, strokeCap: 'round', strokeJoin: 'round', color: ITEMS[index].color } as const;
  return <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Group transform={[{ translateX: 37.5 }, { translateY: 19 }, { scale: 0.84 }]} clip={rect(0, 0, 100, 100)}>
      <Path path={mark.path} {...stroke} opacity={guide} />
      <Path path={mark.path} {...stroke} end={oldProgress} opacity={oldAlpha} />
      <Path path={mark.path} {...stroke} end={progress} strokeWidth={width} opacity={alpha} />
      <Path path={mark.path} {...stroke} start={tailStart} end={progress} strokeWidth={1.5} color="white" opacity={tailAlpha} />
      <Circle c={tip} r={6} color={ITEMS[index].color} opacity={glowAlpha}><BlurMask blur={4} style="normal" /></Circle>
      <Circle c={tip} r={3.2} color={ITEMS[index].color} opacity={tipAlpha} />
      <Circle c={tip} r={1} color="white" opacity={coreAlpha} />
    </Group>
    <Rect rect={underline} color={ITEMS[index].color} opacity={underlineAlpha} />
  </Canvas>;
}
function Tile({ index, mark, pose, selected, onChoose }: { index: number; mark: Mark; pose: SharedValue<Pose>; selected: boolean; onChoose: () => void }) {
  const item = ITEMS[index];
  const liftStyle = useAnimatedStyle(() => {
    const card = pose.get().cards[index];
    return { transform: [{ translateY: -card.lift }], boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(0,0,0,0.07)' },
      { offsetX: 0, offsetY: 5 + card.selection * 4, blurRadius: 8 + card.selection * 6, color: `rgba(137,114,94,${(0.10 + card.selection * 0.04).toFixed(6)})` },
    ] };
  });
  const selectedStyle = useAnimatedStyle(() => {
    const selection = pose.get().cards[index].selection;
    const rgb = ITEM_RGB[index];
    return { backgroundColor: `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${(0.035 * selection).toFixed(6)})`, borderColor: `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${(0.4 * selection).toFixed(6)})` };
  });
  return <Animated.View style={[styles.tile, { left: (index % 2) * 172, top: Math.floor(index / 2) * 175 }, liftStyle]} accessible accessibilityRole="button" accessibilityLabel={`${item.name}, ${item.discipline}`} accessibilityValue={{ text: selected ? 'Selected' : '' }} onAccessibilityTap={onChoose} accessibilityActions={[{ name: 'activate' }]} onAccessibilityAction={onChoose}>
    <View pointerEvents="none" style={styles.tileBorder} />
    <Animated.View style={[styles.tileTint, selectedStyle]} />
    <MarkDrawing index={index} mark={mark} pose={pose} />
    <Text accessible={false} style={[styles.tileName, { color: item.color }]}>{item.name}</Text>
    <Text accessible={false} style={styles.discipline}>{item.discipline}</Text>
  </Animated.View>;
}
function DetailContent({ index, mark, pose }: { index: number; mark: Mark; pose: SharedValue<Pose> }) {
  const style = useAnimatedStyle(() => {
    const layer = detailLayer(index, pose.get());
    return { opacity: layer.opacity, transform: [{ translateY: layer.y }] };
  });
  const item = ITEMS[index];
  return <Animated.View style={[StyleSheet.absoluteFill, style]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Canvas style={{ position: 'absolute', left: 18, top: 21, width: 26, height: 26 }}>
      <Group transform={[{ scale: 0.26 }]}><Path path={mark.path} color={item.color} style="stroke" strokeWidth={3.1} strokeCap="round" strokeJoin="round" /></Group>
    </Canvas>
    <Text style={styles.className}>{item.className}</Text>
    <Text style={styles.classTime}>{item.time}</Text>
    <Text style={styles.classLine}>{item.line}</Text>
  </Animated.View>;
}
function Price({ index, pose }: { index: number; pose: SharedValue<Pose> }) {
  const style = useAnimatedStyle(() => ({ opacity: detailLayer(index, pose.get()).opacity }));
  return <Animated.Text accessible={false} style={[styles.price, style]}>{ITEMS[index].price}</Animated.Text>;
}

/** Four elemental classes, traced by a moving ink tip on warm paper. */
export function FourElementsClassPicker({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const [started] = useState(seconds);
  const [manualLive, setManualLive] = useState(false);
  const [heldLabel, setHeldLabel] = useState(false);
  const [ax, setAx] = useState({ selected: 0, detail: 0 });
  const reduced = useReducedMotion();
  const clock = useSharedValue(started);
  const epoch = useSharedValue(started);
  const lastTouch = useSharedValue(-1);
  const traceStarted = useSharedValue(-1);
  const traceDuration = useSharedValue(0.65);
  const manualBase = useSharedValue<Pose>(restPose());
  const manualIndex = useSharedValue(0);
  const live = useSharedValue(false);
  const dragging = useSharedValue(false);
  const dragIndex = useSharedValue(-1);
  const detailStartIndex = useSharedValue(0);
  const detailDragging = useSharedValue(false);
  const spaceHeld = useSharedValue(false);
  const lastHaptic = useSharedValue(-1);
  const marks = useMemo(() => makeMarks(), []);
  const paper = useMemo(() => makePaper(), []);
  const pose = useDerivedValue<Pose>(() => {
    if (previewTime !== undefined) return reduced ? reducedPose(manualIndex.get()) : loopPose(previewTime);
    if (reduced) return reducedPose(manualIndex.get());
    const age = Math.max(0, clock.get() - lastTouch.get());
    if ((lastTouch.get() >= 0 && age < 2.6) || dragging.get() || detailDragging.get()) {
      const traceAge = Math.max(0, clock.get() - traceStarted.get());
      const duration = traceDuration.get();
      const progress = ease(traceAge / duration);
      const tip = clamp(traceAge / 0.04) * (1 - clamp((traceAge - duration) / 0.12));
      const manual = manualPose(manualBase.get(), manualIndex.get(), progress, tip, 4 * liftSpring(traceAge));
      return dragging.get() || detailDragging.get() ? manual : returningToRest(manual, ease((age - 2) / 0.6));
    }
    if (!autoPlay) return restPose();
    return loopPose(lastTouch.get() < 0 ? clock.get() - epoch.get() : age - 2.6);
  });
  const frames = useFrameCallback(() => {
    const now = seconds();
    clock.set(now);
    if (live.get() && !dragging.get() && !detailDragging.get() && now - lastTouch.get() >= 2.65) {
      live.set(false);
      scheduleOnRN(setManualLive, false);
    }
  }, false);
  useEffect(() => {
    frames.setActive((autoPlay && !reduced && previewTime === undefined) || manualLive);
    return () => frames.setActive(false);
  }, [autoPlay, reduced, previewTime, manualLive, frames]);
  useAnimatedReaction(() => {
    const current = pose.get();
    return current.selected * 4 + (current.detailMix < 0.5 ? current.detailFrom : current.detailTo);
  }, (value, before) => {
    if (value !== before) scheduleOnRN(setAx, { selected: Math.floor(value / 4), detail: value % 4 });
  });
  useAnimatedReaction(() => spaceHeld.get() && clock.get() - lastTouch.get() < 2.4, (held, before) => {
    if (held !== before) scheduleOnRN(setHeldLabel, held);
  });

  const select = (index: number, duration: number) => {
    'worklet';
    const now = seconds();
    manualBase.set(pose.get());
    manualIndex.set(index);
    traceStarted.set(now);
    traceDuration.set(duration);
    lastTouch.set(now);
    clock.set(now);
    live.set(true);
    spaceHeld.set(false);
    scheduleOnRN(setManualLive, true);
    if (now - lastHaptic.get() > 0.12) { lastHaptic.set(now); scheduleOnRN(haptic); }
  };
  const updateGrid = (x: number, y: number, velocityX: number, velocityY: number) => {
    'worklet';
    const index = (y >= 175 ? 2 : 0) + (x >= 172 ? 1 : 0);
    if (index !== dragIndex.get()) {
      dragIndex.set(index);
      select(index, Math.hypot(velocityX, velocityY) > 500 ? 0.42 : 0.65);
    }
    dragging.set(true);
    const now = seconds();
    lastTouch.set(now); clock.set(now); live.set(true);
  };
  const gridPan = Gesture.Pan().minDistance(3)
    .onStart((event) => updateGrid(event.x, event.y, event.velocityX, event.velocityY))
    .onUpdate((event) => updateGrid(event.x, event.y, event.velocityX, event.velocityY))
    .onFinalize(() => {
      if (!dragging.get()) return;
      dragging.set(false); dragIndex.set(-1);
      const now = seconds(); lastTouch.set(now); clock.set(now);
    });
  const gridTap = Gesture.Tap().maxDistance(3).maxDuration(10000).onEnd((event, success) => {
    const column = event.x >= 172 ? 1 : 0;
    const row = event.y >= 175 ? 1 : 0;
    const inside = event.x - column * 172 <= 159 && event.y - row * 175 <= 157;
    if (success && inside) select(row * 2 + column, 0.65);
  });
  const gridGesture = Gesture.Exclusive(gridPan, gridTap);
  const detailGesture = Gesture.Pan().minDistance(4)
    .onStart(() => {
      const index = pose.get().selected;
      detailStartIndex.set(index); select(index, 0.65); detailDragging.set(true);
    })
    .onUpdate((event) => {
      const index = Math.min(3, Math.max(0, Math.round(detailStartIndex.get() - event.translationX / 86)));
      if (index !== manualIndex.get() || !live.get()) select(index, 0.65);
      const now = seconds(); lastTouch.set(now); clock.set(now); live.set(true);
    })
    .onEnd((event) => {
      const index = Math.min(3, Math.max(0, Math.round(detailStartIndex.get() - event.translationX / 86 - event.velocityX * 0.0009)));
      if (index !== manualIndex.get()) select(index, 0.65);
    })
    .onFinalize(() => {
      if (!detailDragging.get()) return;
      detailDragging.set(false);
      const now = seconds(); lastTouch.set(now); clock.set(now);
    });
  const reserve = () => {
    'worklet';
    select(pose.get().selected, 0.65);
    traceStarted.set(seconds() - traceDuration.get());
    spaceHeld.set(true);
  };
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT);

  return <View style={styles.screen} onLayout={(event) => {
    const { width, height } = event.nativeEvent.layout;
    setSize((before) => before.width === width && before.height === height ? before : { width, height });
  }}>
    <StatusBar style="dark" />
    <View style={[styles.design, { left: (size.width - WIDTH * scale) / 2, top: (size.height - HEIGHT * scale) / 2, transform: [{ scale }] }]}>
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Picture picture={paper} /></Canvas>
      <Svg pointerEvents="none" style={{ position: 'absolute', left: 26.5, top: 89 }} width={22} height={20} viewBox="26.5 89 22 20"><SvgPath d="M28 92Q35 90 46 92M28 99Q35 97 41 99M28 106Q35 104 36 106" stroke={RUST} strokeWidth={2.5} strokeLinecap="round" fill="none" /></Svg>
      <Text style={styles.wordmark}>Veyra</Text>
      <Text style={styles.tagline}>WHOLE DAYS</Text>
      <Svg pointerEvents="none" style={{ position: 'absolute', left: 347.5, top: 91.5 }} width={15} height={15} viewBox="0 0 16 16"><SvgPath d="M3 11.5h10c-1.4-1.7-1.8-3-1.8-5.1a3.2 3.2 0 0 0-6.4 0c0 2.1-.4 3.4-1.8 5.1ZM6.5 13.4a1.6 1.6 0 0 0 3 0M8 2.7V1.8" stroke={RUST} strokeWidth={1.1} strokeLinecap="round" strokeLinejoin="round" fill="none" /></Svg>
      <Text style={styles.question}>{'What feels right\nfor you today?'}</Text>
      <GestureDetector gesture={gridGesture}>
        <View style={styles.grid}>
          {ITEMS.map((_, index) => <Tile key={index} index={index} mark={marks[index]} pose={pose} selected={ax.selected === index} onChoose={() => scheduleOnUI(select, index, 0.65)} />)}
        </View>
      </GestureDetector>
      <GestureDetector gesture={detailGesture}>
        <View style={styles.details} accessible accessibilityLabel={ITEMS[ax.detail].className} accessibilityHint="Swipe left or right to choose a class">
          <View pointerEvents="none" style={styles.detailBorder} />
          {ITEMS.map((_, index) => <DetailContent key={index} index={index} mark={marks[index]} pose={pose} />)}
        </View>
      </GestureDetector>
      <Pressable style={styles.reserve} onPress={() => scheduleOnUI(reserve)} accessibilityRole="button" accessibilityLabel={heldLabel ? 'Space held for you' : 'Reserve your space'}>
        <View pointerEvents="none" style={styles.reserveBorder} />
        <Text style={styles.reserveLabel}>{heldLabel ? 'Space held for you' : 'Reserve your space'}</Text>
        <View style={styles.prices}>{ITEMS.map((_, index) => <Price key={index} index={index} pose={pose} />)}</View>
        <Svg width={14} height={14} viewBox="0 0 16 16" fill="none"><SvgPath d={heldLabel ? 'm3 8 3.3 3.3L13 4.5' : 'M2.5 8h10M8 3.5 12.5 8 8 12.5'} stroke="#fcf6ed" strokeWidth={1.2} strokeLinecap="round" strokeLinejoin="round" /></Svg>
      </Pressable>
      <Text accessible={false} style={styles.footer}>Swipe across the elements</Text>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  design: { position: 'absolute', width: WIDTH, height: HEIGHT, transformOrigin: 'top left', overflow: 'hidden' },
  wordmark: { position: 'absolute', left: 96.5, top: 65.8, width: 200, fontFamily: SERIF, fontSize: 42, lineHeight: 50.4, letterSpacing: -2, color: RUST, textAlign: 'center', includeFontPadding: false },
  tagline: { position: 'absolute', left: 98, top: 126.2, width: 200, fontSize: 8, lineHeight: 9.6, fontWeight: '500', letterSpacing: 3, color: '#ae8b71', textAlign: 'center', includeFontPadding: false },
  question: { position: 'absolute', left: 45, top: 165.45, width: 303, height: 62, fontSize: 21, lineHeight: 28.35, fontWeight: '500', color: '#9d7b64', textAlign: 'center', textAlignVertical: 'center', includeFontPadding: false },
  grid: { position: 'absolute', left: 31, top: 250, width: 331, height: 332 },
  tile: { position: 'absolute', width: 159, height: 157, borderRadius: 12, borderCurve: 'continuous', backgroundColor: '#fffdfa' },
  tileBorder: { ...StyleSheet.absoluteFill, borderRadius: 12, borderCurve: 'continuous', borderWidth: 1, borderColor: '#f4efe8' },
  tileTint: { ...StyleSheet.absoluteFill, borderRadius: 12, borderCurve: 'continuous', borderWidth: 1 },
  tileName: { position: 'absolute', left: 0, top: 109.6, width: 159, fontSize: 9, lineHeight: 10.8, fontWeight: '600', letterSpacing: 2, textAlign: 'center', includeFontPadding: false },
  discipline: { position: 'absolute', left: 0, top: 129.8, width: 159, fontSize: 7, lineHeight: 8.4, fontWeight: '500', letterSpacing: 1.3, color: '#a99786', textAlign: 'center', includeFontPadding: false },
  details: { position: 'absolute', left: 31, top: 610, width: 331, height: 101, borderRadius: 15, borderCurve: 'continuous', backgroundColor: '#ebe3d4' },
  detailBorder: { ...StyleSheet.absoluteFill, borderRadius: 15, borderCurve: 'continuous', borderWidth: 1, borderColor: 'rgba(255,255,255,0.4)' },
  className: { position: 'absolute', left: 57, top: 16, width: 246, height: 15, fontSize: 10, lineHeight: 15, fontWeight: '600', letterSpacing: 1.1, color: '#8a684f', includeFontPadding: false },
  classTime: { position: 'absolute', left: 57, top: 36, width: 246, height: 16, fontSize: 11, lineHeight: 16, color: '#9a8571', includeFontPadding: false },
  classLine: { position: 'absolute', left: 18, top: 63, width: 295, height: 17, fontSize: 11, lineHeight: 17, color: '#9e8772', includeFontPadding: false },
  reserve: { position: 'absolute', left: 31, top: 730, width: 331, height: 48, borderRadius: 24, backgroundColor: '#9d6045', paddingHorizontal: 24, flexDirection: 'row', alignItems: 'center', gap: 12 },
  reserveBorder: { ...StyleSheet.absoluteFill, borderRadius: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)' },
  reserveLabel: { flex: 1, fontSize: 12, lineHeight: 16, fontWeight: '500', color: '#fcf6ed' },
  prices: { width: 18, height: 18 },
  price: { position: 'absolute', width: 18, fontSize: 13, lineHeight: 18, fontWeight: '500', color: '#fcf6ed', textAlign: 'center' },
  footer: { position: 'absolute', left: 46.5, top: 795, width: 300, fontSize: 10, lineHeight: 12, color: '#a89787', textAlign: 'center', includeFontPadding: false },
});
