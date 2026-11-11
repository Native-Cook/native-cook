import {
  BlurStyle,
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, ClipPath, Defs, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** Seconds. The reference frame at LOOP is exactly the frame at zero. */
export const LOOP = 8;
const TAU = Math.PI * 2;
const INK = '#FFF3DC';
const CREAM = '#FFE9C4';
const SECONDARY = 'rgba(255,243,220,0.62)';
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

type GoldDuneContoursSpring = {
  from: number;
  velocity: number;
  target: number;
  stamp: number;
  response: number;
  damping: number;
};

type GoldDuneContoursInteraction = {
  lastTouch: number | null;
  dragging: boolean;
  paused: boolean;
  heldByTerrain: boolean;
  held: number;
  x: GoldDuneContoursSpring;
  y: GoldDuneContoursSpring;
  ridge: GoldDuneContoursSpring;
};

type GoldDuneContoursFrame = {
  time: number;
  playing: boolean;
  ridge: number;
  x: number;
  y: number;
  lightBlend: number;
};

/** Wall time is sampled only by frame callbacks, gesture events and mount initialization. */
function clockSeconds() {
  'worklet';
  return Date.now() / 1000;
}

function clamp(value: number) {
  'worklet';
  return Math.max(0, Math.min(1, value));
}

function spring(value = 0): GoldDuneContoursSpring {
  'worklet';
  return { from: value, velocity: 0, target: value, stamp: 0, response: 0.3, damping: 0.72 };
}

/** Closed-form spring; only a gesture event changes its starting conditions. */
function sample(s: GoldDuneContoursSpring, now: number) {
  'worklet';
  const t = Math.max(0, now - s.stamp);
  const w = TAU / s.response;
  const d = s.from - s.target;
  if (t > 3) return { value: s.target, velocity: 0 };
  if (s.damping >= 1) {
    const b = s.velocity + w * d;
    const decay = Math.exp(-w * t);
    return { value: s.target + (d + b * t) * decay, velocity: (b - w * (d + b * t)) * decay };
  }
  const a = s.damping * w;
  const wd = w * Math.sqrt(1 - s.damping * s.damping);
  const b = (s.velocity + a * d) / wd;
  const decay = Math.exp(-a * t);
  const wave = d * Math.cos(wd * t) + b * Math.sin(wd * t);
  return {
    value: s.target + decay * wave,
    velocity: decay * (-a * wave - d * wd * Math.sin(wd * t) + b * wd * Math.cos(wd * t)),
  };
}

function retarget(s: GoldDuneContoursSpring, target: number, now: number, response: number, damping: number) {
  'worklet';
  const current = sample(s, now);
  return { from: current.value, velocity: current.velocity, target, stamp: now, response, damping };
}

function initialInteraction(): GoldDuneContoursInteraction {
  'worklet';
  return {
    lastTouch: null, dragging: false, paused: false, heldByTerrain: false, held: 0,
    x: spring(196.5), y: spring(500), ridge: spring(),
  };
}

function isPlaying(s: GoldDuneContoursInteraction, now: number) {
  'worklet';
  return !s.paused || (!s.dragging && now - (s.lastTouch ?? now) >= 2);
}

function lightBlend(s: GoldDuneContoursInteraction, now: number) {
  'worklet';
  if (s.dragging) return 1;
  if (s.heldByTerrain && s.lastTouch !== null) {
    const u = clamp((now - s.lastTouch) / 0.65);
    return 1 - u * u * (3 - 2 * u);
  }
  return 0;
}

function releaseState(s: GoldDuneContoursInteraction, stamp: number, reduced: boolean) {
  'worklet';
  const ridge = retarget(s.ridge, 0, stamp, 0.65, reduced ? 1 : 0.55);
  if (reduced) ridge.velocity = 0;
  return { ...s, dragging: false, lastTouch: stamp, ridge,
    x: spring(sample(s.x, stamp).value), y: spring(sample(s.y, stamp).value),
  };
}

function timeAt(s: GoldDuneContoursInteraction, now: number, epoch: number, autoPlay: boolean) {
  'worklet';
  if (s.lastTouch === null) return autoPlay ? Math.max(0, now - epoch) % LOOP : 0;
  if (s.dragging) return s.held;
  const elapsed = Math.max(0, now - s.lastTouch);
  if (elapsed < 2) return s.paused || s.heldByTerrain ? s.held : (s.held + elapsed) % LOOP;
  const start = s.paused || s.heldByTerrain ? s.held : (s.held + 2) % LOOP;
  if (elapsed < 2.8) {
    const u = (elapsed - 2) / 0.8;
    return start + (LOOP - start) * u * u * (3 - 2 * u);
  }
  return autoPlay ? (elapsed - 2.8) % LOOP : 0;
}

function hash(seed: number) {
  'worklet';
  const value = Math.sin(seed * 12.9898) * 43758.5453;
  return value - Math.floor(value);
}

function rgba(r: number, g: number, b: number, a = 1) {
  'worklet';
  return Float32Array.of(r / 255, g / 255, b / 255, clamp(a));
}

function baseColor(u: number) {
  'worklet';
  const a = u < 0.5 ? [255, 233, 196] : [232, 179, 106];
  const b = u < 0.5 ? [232, 179, 106] : [201, 138, 107];
  const f = u < 0.5 ? u * 2 : (u - 0.5) * 2;
  return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];
}

function drawDunes(canvas: SkCanvas, frame: GoldDuneContoursFrame, reduced: boolean) {
  'worklet';
  const t = frame.time % LOOP;
  const tau = TAU * t / LOOP;
  const bounds = Skia.XYWHRect(0, 0, 393, 852);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 0 }, { x: 0, y: 852 }, [rgba(10, 8, 6), rgba(18, 13, 9)], null, TileMode.Clamp,
  ));
  canvas.drawRect(bounds, paint);
  const halo = reduced ? 190 : 190 + 16 * Math.pow(Math.sin(tau / 2), 2);
  paint.setShader(Skia.Shader.MakeRadialGradient(
    { x: 268, y: 318 }, halo, [rgba(255, 180, 94, 0.34), rgba(255, 180, 94, 0)], null, TileMode.Clamp,
  ));
  canvas.drawRect(bounds, paint);
  paint.setShader(Skia.Shader.MakeRadialGradient(
    { x: 268, y: 318 }, 34, [rgba(255, 246, 224, 0.92), rgba(255, 217, 160, 0.92)], null, TileMode.Clamp,
  ));
  canvas.drawCircle(268, 318, 34, paint);
  paint.setShader(null);
  for (let k = 0; k < 26; k++) {
    const seed = hash(k + 1);
    // Each mote wraps an 18 × 8 pt lane and fades at its own seam.
    const age = (seed + (reduced ? 0 : t / LOOP)) % 1;
    const x = (hash(k + 31) * 393 + age * 18) % 393;
    const y = 90 + hash(k + 71) * 330 - age * 8;
    const radius = 0.6 + 0.8 * hash(k + 111);
    const alpha = (0.1 + 0.28 * seed) * (0.5 + 0.5 * Math.sin(2 * tau + seed * TAU)) * Math.pow(Math.sin(Math.PI * age), 2);
    paint.setColor(rgba(255, 233, 196, alpha));
    canvas.drawCircle(x, y, radius, paint);
  }

  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setStrokeJoin(StrokeJoin.Round);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setStyle(PaintStyle.Stroke);
  glow.setStrokeCap(StrokeCap.Round);
  glow.setStrokeJoin(StrokeJoin.Round);
  const blur6 = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true);
  const blur12 = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true);
  const line = Skia.Path.Make();
  const fill = Skia.Path.Make();
  for (let i = 0; i < 46; i++) {
    const v = i / 45;
    const baseline = 352 + 548 * Math.pow(v, 1.55);
    const amplitude = 22 + 150 * Math.pow(v, 1.3);
    line.rewind();
    fill.rewind();
    for (let n = 0; n <= 74; n++) {
      const x = Math.min(417, -24 + n * 6);
      const u = x / 393;
      const crest = 1 - 2 * Math.sqrt(Math.pow(Math.sin(Math.PI * (0.8 * u + 0.9 * v) + tau / 2), 2) + 0.02);
      const h = 0.52 * crest
        + 0.30 * Math.sin(TAU * (1.6 * u - 1.05 * v) - tau + 1.3)
        + 0.18 * Math.sin(TAU * (2.9 * u + 2.2 * v) + 2 * tau + 0.4);
      const lift = frame.ridge * 78 * (0.35 + 0.65 * v)
        * Math.exp(-Math.pow((x - frame.x) / 86, 2)) * Math.exp(-Math.pow((baseline - frame.y) / 110, 2));
      const y = baseline - amplitude * (0.5 + 0.5 * h) - lift;
      if (n === 0) { line.moveTo(x, y); fill.moveTo(x, y); }
      else { line.lineTo(x, y); fill.lineTo(x, y); }
    }
    fill.lineTo(417, 960).lineTo(-24, 960).close();
    // Opaque far-to-near fills hide every line and the moon behind this ridge.
    paint.setColor(rgba(10 + 8 * v, 8 + 5 * v, 6 + 3 * v));
    canvas.drawPath(fill, paint);
    const automatic = reduced ? 0.55 : Math.pow(Math.max(0, Math.sin(Math.PI * t / LOOP)), 0.6);
    const blend = frame.lightBlend;
    const center = (reduced ? 0.62 : -0.45 + 1.9 * t / LOOP + 0.22 * v) * (1 - blend) + frame.x / 393 * blend;
    const strength = automatic * (1 - blend) + clamp(frame.ridge) * blend;
    if (i % 3 === 0) {
      const nearLight = Math.max(0, 1 - Math.abs(center - 0.5) / 0.76) * strength;
      glow.setMaskFilter(blur6);
      glow.setStrokeWidth(3 + 3 * v);
      glow.setColor(rgba(255, 201, 120, (0.16 + 0.30 * v) * (0.6 + 0.4 * nearLight)));
      canvas.drawPath(line, glow);
    }
    if (i >= 40) {
      glow.setMaskFilter(blur12);
      glow.setStrokeWidth(6);
      glow.setColor(rgba(255, 201, 120, 0.16));
      canvas.drawPath(line, glow);
    }
    const positions = [0, 0.5, 1, clamp(center - 0.26), clamp(center), clamp(center + 0.26)].sort((a, b) => a - b);
    const colors = positions.map((u) => {
      const base = baseColor(u);
      const light = Math.max(0, 1 - Math.abs(u - center) / 0.26) * strength;
      const alpha = 0.30 + 0.65 * v;
      return rgba(base[0] + (255 - base[0]) * light, base[1] + (246 - base[1]) * light,
        base[2] + (224 - base[2]) * light, alpha + (1 - alpha) * light);
    });
    stroke.setColor(rgba(255, 255, 255));
    stroke.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 393, y: 0 }, colors, positions, TileMode.Clamp));
    stroke.setStrokeWidth(0.6 + 0.9 * v);
    canvas.drawPath(line, stroke);
  }
  paint.setColor(rgba(255, 255, 255));
  paint.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 702 }, { x: 0, y: 852 }, [rgba(18, 13, 9, 0), rgba(18, 13, 9, 0.7)], null, TileMode.Clamp,
  ));
  canvas.drawRect(Skia.XYWHRect(0, 702, 393, 150), paint);
}

function haptic(soft = false) {
  if (Platform.OS !== 'web') {
    Haptics.impactAsync(soft ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }
}

type GoldDuneContoursProps = {
  autoPlay?: boolean;
  /** A still frame for visual comparison; no running clock. */
  previewTime?: number;
};

export function GoldDuneContours({ autoPlay = false, previewTime }: GoldDuneContoursProps) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 393, height: 852 });
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [settling, setSettling] = useState(false);
  const [playing, setPlaying] = useState(true);
  const [start] = useState(() => clockSeconds());
  const epoch = useSharedValue(start);
  const now = useSharedValue(start);
  const interaction = useSharedValue(initialInteraction());
  const finishSent = useSharedValue(false);
  const sx = size.width / 393;
  const sy = size.height / 852;
  const top = Math.max(0, insets.top - 59);
  const bottom = Math.max(0, insets.bottom - 34);
  const cardTop = 716 * sy - bottom;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      setActive(state === 'active');
      const s = interaction.get();
      if (state !== 'active' && s.dragging) {
        const stamp = clockSeconds();
        interaction.set(releaseState(s, stamp, reduced));
        now.set(stamp);
        haptic(true);
      }
    });
    return () => subscription.remove();
  }, [interaction, now, reduced]);

  const clock = useFrameCallback(() => {
    const stamp = clockSeconds();
    now.set(stamp);
    const s = interaction.get();
    if (!s.dragging && s.lastTouch !== null && stamp - s.lastTouch >= 2.8 && !finishSent.get()) {
      finishSent.set(true);
      scheduleOnRN(setSettling, false);
    }
  }, false);
  useEffect(() => {
    clock.setActive(active && previewTime === undefined && (settling || (autoPlay && !reduced)));
    return () => clock.setActive(false);
  }, [active, autoPlay, clock, previewTime, reduced, settling]);

  const frame = useDerivedValue<GoldDuneContoursFrame>(() => {
    const s = interaction.get();
    const stamp = now.get();
    return {
      time: reduced ? 0 : previewTime === undefined ? timeAt(s, stamp, epoch.get(), autoPlay) : previewTime % LOOP,
      playing: isPlaying(s, stamp), ridge: sample(s.ridge, stamp).value,
      x: sample(s.x, stamp).value, y: sample(s.y, stamp).value, lightBlend: lightBlend(s, stamp),
    };
  });
  useAnimatedReaction(() => frame.get().playing, (value, before) => {
    if (value !== before) scheduleOnRN(setPlaying, value);
  });
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    canvas.scale(sx, sy);
    drawDunes(canvas, frame.get(), reduced);
    return recorder.finishRecordingAsPicture();
  }, [size.width, size.height, sx, sy, reduced]);

  const touch = Gesture.Pan().minDistance(0)
    .onBegin((event) => {
      if (event.y <= 300 * sy || (event.x >= 20 * sx && event.x <= 373 * sx && event.y >= cardTop && event.y <= cardTop + 84 * sy)) return;
      const stamp = clockSeconds();
      const s = interaction.get();
      interaction.set({
        ...s, held: reduced ? 0 : timeAt(s, stamp, epoch.get(), autoPlay), paused: !isPlaying(s, stamp),
        dragging: true, heldByTerrain: true, lastTouch: stamp,
        x: spring(event.x / sx), y: spring(event.y / sy),
        ridge: retarget(s.ridge, 1, stamp, 0.35, reduced ? 1 : 0.70),
      });
      finishSent.set(false);
      now.set(stamp);
      scheduleOnRN(setSettling, true);
      scheduleOnRN(haptic, false);
    })
    .onUpdate((event) => {
      const s = interaction.get();
      if (!s.dragging) return;
      const stamp = clockSeconds();
      interaction.set({ ...s,
        x: retarget(s.x, event.x / sx, stamp, 0.30, reduced ? 1 : 0.72),
        y: retarget(s.y, event.y / sy, stamp, 0.30, reduced ? 1 : 0.72),
      });
      now.set(stamp);
    })
    .onFinalize(() => {
      const s = interaction.get();
      if (!s.dragging) return;
      const stamp = clockSeconds();
      interaction.set(releaseState(s, stamp, reduced));
      now.set(stamp);
      scheduleOnRN(haptic, true);
    });

  const toggle = () => {
    const stamp = clockSeconds();
    const s = interaction.get();
    interaction.set({ ...s, paused: isPlaying(s, stamp), heldByTerrain: false, lastTouch: stamp,
      held: reduced ? 0 : timeAt(s, stamp, epoch.get(), autoPlay),
    });
    finishSent.set(false);
    now.set(stamp);
    setSettling(true);
    haptic();
  };
  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Dune contours" accessibilityHint="Drag to raise a ridge in the sand">
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Text style={[styles.overline, { left: 28 * sx, top: 76 * sy + top }]}>TONIGHT · 23:40</Text>
        <Text style={[styles.title, { left: 28 * sx, top: 104 * sy + top }]}>Dune Wind</Text>
        <Text style={[styles.subtitle, { left: 28 * sx, top: 158 * sy + top, width: 250 * sx }]}>Low wind over warm sand, recorded at dusk.</Text>
        <View style={[styles.badge, { left: 325 * sx, top: 64 * sy + top, width: 40 * sx, height: 40 * sy }]}>
          <Svg width={16 * sx} height={16 * sy} viewBox="0 0 16 16">
            <Defs><ClipPath id="goldDuneMoon"><Circle cx={8} cy={8} r={8} /></ClipPath></Defs>
            <Path d="M 0 8 A 8 8 0 1 0 16 8 A 8 8 0 1 0 0 8 M 5 5 A 8 8 0 1 0 21 5 A 8 8 0 1 0 5 5" fill={CREAM} fillRule="evenodd" clipPath="url(#goldDuneMoon)" />
          </Svg>
        </View>
      </View>
      <View style={[styles.player, { left: 20 * sx, top: cardTop, width: 353 * sx, height: 84 * sy }]}>
        <BlurView intensity={30} tint="dark" style={StyleSheet.absoluteFill} />
        <View pointerEvents="none" style={styles.glassGround} />
        <View pointerEvents="none" style={styles.glassFill} />
        <Pressable onPress={toggle} accessibilityRole="button" accessibilityLabel={playing ? 'Pause Dune Wind' : 'Play Dune Wind'} style={[styles.button, { left: 16 * sx, top: 16 * sy, width: 52 * sx, height: 52 * sy }]}>
          <LinearGradient colors={[CREAM, '#E8B36A']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
          <Svg width={52 * sx} height={52 * sy} viewBox="0 0 52 52">
            {playing ? <Path d="M 22 18 Q 20 18 20 20 V 32 Q 20 34 22 34 Q 24 34 24 32 V 20 Q 24 18 22 18 M 30 18 Q 28 18 28 20 V 32 Q 28 34 30 34 Q 32 34 32 32 V 20 Q 32 18 30 18" fill="#1A120A" /> : <Path d="M 21 17 L 35 26 L 21 35 Z" fill="#1A120A" />}
          </Svg>
        </Pressable>
        <Text style={[styles.playerTitle, { left: 84 * sx, top: 22 * sy }]}>Dune Wind</Text>
        <Text style={[styles.playerSubtitle, { left: 84 * sx, top: 44 * sy }]}>45 min · fades out</Text>
        <View pointerEvents="none" style={{ position: 'absolute', left: 302 * sx, top: 31 * sy, width: 27 * sx, height: 22 * sy }}>
          {[0, 1, 2, 3, 4].map((index) => <GoldDuneContoursBar key={index} index={index} frame={frame} sx={sx} sy={sy} reduced={reduced} />)}
        </View>
      </View>
    </View>
  );
}

function GoldDuneContoursBar({ index, frame, sx, sy, reduced }: {
  index: number; frame: SharedValue<GoldDuneContoursFrame>; sx: number; sy: number; reduced: boolean;
}) {
  const style = useAnimatedStyle(() => {
    const value = frame.get();
    const h = value.playing && !reduced ? 8 + 14 * (0.5 + 0.5 * Math.sin(4 * Math.PI * value.time / LOOP + index * 1.1)) : 8;
    return { height: h * sy, top: (22 - h) / 2 * sy };
  });
  return <Animated.View style={[styles.bar, { left: index * 6 * sx, width: 3 * sx }, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0A0806', overflow: 'hidden' },
  overline: { position: 'absolute', color: SECONDARY, fontSize: 12, fontWeight: '500', letterSpacing: 1.6, includeFontPadding: false },
  title: { position: 'absolute', color: INK, fontSize: 40, fontFamily: SERIF, fontWeight: '400', includeFontPadding: false },
  subtitle: { position: 'absolute', color: SECONDARY, fontSize: 15, lineHeight: 18, includeFontPadding: false },
  badge: { position: 'absolute', borderRadius: 100, borderWidth: 1, borderColor: 'rgba(255,233,196,0.22)', backgroundColor: 'rgba(255,255,255,0.07)', alignItems: 'center', justifyContent: 'center' },
  player: { position: 'absolute', borderRadius: 28, borderWidth: 1, borderColor: 'rgba(255,233,196,0.22)', overflow: 'hidden', backgroundColor: 'rgba(18,13,9,0.35)' },
  glassGround: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: 'rgba(18,13,9,0.46)' },
  glassFill: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: 'rgba(255,255,255,0.07)' },
  button: { position: 'absolute', borderRadius: 100, overflow: 'hidden' },
  playerTitle: { position: 'absolute', color: INK, fontSize: 16, fontWeight: '600', includeFontPadding: false },
  playerSubtitle: { position: 'absolute', color: SECONDARY, fontSize: 13, includeFontPadding: false },
  bar: { position: 'absolute', borderRadius: 1.5, backgroundColor: 'rgba(255,233,196,0.8)' },
});
