import {
  Canvas,
  Fill,
  ImageShader,
  makeImageFromView,
  Shader,
  Skia,
  type SkCanvas,
  type SkImage,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  backFrame,
  beginBack,
  clearAt,
  DROPS,
  dropAt,
  type Frost,
  IDLE_BEFORE_BACK,
  lay,
  LOOP,
  makeFrost,
  MODE_AUTO,
  MODE_BACK,
  MODE_LIVE,
  regrow,
  scriptFrame,
  STEP,
  TRAIL,
} from './frostMotion';
import { FROST_SKSL, FROST_TEX_SKSL } from './frostShader';

/*
 Frost Wipe Alert — a weather app's frost advisory seen through a frozen window.

 Look: a night weather screen for "Hollow Creek" (−4°, hourly strip) dimmed under an alert card
 ("Frost advisory — −4 °C tonight in Hollow Creek. Cover your plants by 18:00.", Remind me /
 Dismiss). A frosted pane covers everything, alert included: the app behind it is a blurred snapshot
 pulled toward a cold white, with thin ice feathers growing in from the edges and sparkles on top (baked once at mount,
 frostShader.ts), so only the alert's shape and its blue button show through. One canvas and one
 per-frame shader over the real views.

 autoPlay (LOOP = 9 s, frostMotion.ts): 0–1.4 rest (fully frosted), 1.4–4.0 a hand wipes a zig-zag
 down the alert (32 points, brush 44 pt), 4.0–5.4 it stays clear, 4.2–6.0 six droplets gather at
 the wipe's lower edge and run down (sag 0.3 s, then y = 95·t^1.6) leaving clear streaks,
 5.4–8.4 the frost grows back from the edges of the wipe along a crystalline front, 8.4–9 rest.

 Touch: drag to wipe (a point every 18 pt; the wipe stays clear 1.2 s, then refreezes over 6 s);
 drops gather at the lower edge now and then. The buttons work only where the glass is clear
 (a tap through frost does nothing): Remind me (light haptic) turns into "Reminder at 17:30",
 Dismiss lowers the alert. 2 s after the last touch the pane frosts over (0.9 s), the alert comes
 back and the loop restarts from t = 0. Reduced motion: no loop, the alert shown wiped clear.
 */

type Props = {
  /** Plays the 9 s wipe-and-refreeze loop until touched. */
  autoPlay?: boolean;
};

const TEX = Skia.RuntimeEffect.Make(FROST_TEX_SKSL);
const PANE = Skia.RuntimeEffect.Make(FROST_SKSL);
/** A frozen frame of the loop shown under reduced motion: the alert wiped clear. */
const REDUCED_AT = 4.6;
/** Pixels per point of the ice image (crisp needles), and of the haze and the blurred app. */
const ICE_SCALE = 2;
const SOFT_SCALE = 0.5;
/** Blur of the app under the glass (pt): enough that the alert's text cannot be read. */
const CONTENT_BLUR = 9;

type Ice = { ice: SkImage; haze: SkImage };

/** An offscreen surface (GPU when it can), drawn by `paint`, copied to the CPU for the UI thread. */
function render(w: number, h: number, scale: number, paint: (canvas: SkCanvas) => void): SkImage | null {
  const pw = Math.max(1, Math.ceil(w * scale));
  const ph = Math.max(1, Math.ceil(h * scale));
  const surface = Skia.Surface.MakeOffscreen(pw, ph) ?? Skia.Surface.Make(pw, ph);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  paint(canvas);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** The ice and the haze density, independent of the app: baked once, so frame one is frosted. */
function bakeIce(w: number, h: number): Ice | null {
  if (!TEX) return null;
  const draw = (mode: number) => (canvas: SkCanvas) => {
    const paint = Skia.Paint();
    paint.setShader(TEX.makeShader([w, h, mode]));
    canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
  };
  const ice = render(w, h, ICE_SCALE, draw(0));
  const haze = render(w, h, SOFT_SCALE, draw(1));
  return ice && haze ? { ice, haze } : null;
}

/** The app under the glass, blurred once per snapshot. */
function blurContent(snapshot: SkImage, w: number, h: number): SkImage | null {
  return render(w, h, SOFT_SCALE, (canvas) => {
    const paint = Skia.Paint();
    paint.setImageFilter(Skia.ImageFilter.MakeBlur(CONTENT_BLUR, CONTENT_BLUR, TileMode.Clamp, null));
    canvas.drawImageRect(snapshot, Skia.XYWHRect(0, 0, snapshot.width(), snapshot.height()), Skia.XYWHRect(0, 0, w, h), paint);
  });
}

/** Stands in for the blurred app until its snapshot lands (the shader ignores it then). */
function makePlaceholder() {
  const surface = Skia.Surface.Make(1, 1);
  if (!surface) return null;
  surface.getCanvas().drawColor(Skia.Color('#1a2a3a'));
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

export function FrostWipeAlert({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [blurred, setBlurred] = useState<SkImage | null>(null);
  const [reminded, setReminded] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const contentRef = useRef<View>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;

  const placeholder = useMemo(() => makePlaceholder(), []);
  const ice = useMemo(() => (size ? bakeIce(size.width, size.height) : null), [size]);
  const state = useSharedValue<Frost>(makeFrost(autoPlay));
  const tick = useSharedValue(0);
  /** The alert card (x, y, w, h) and its two buttons (x, y, w, h each), in screen points. */
  const card = useSharedValue([0, 0, 0, 0]);
  const buttons = useSharedValue([0, 0, 0, 0, 0, 0, 0, 0]);
  const shown = useSharedValue(1);

  useEffect(() => {
    state.set(makeFrost(autoPlay));
  }, [autoPlay, state]);

  // A fresh snapshot of the app under the glass whenever what it shows changes.
  useEffect(() => {
    if (!size) return undefined;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      makeImageFromView(contentRef)
        .then((snapshot) => {
          if (snapshot) setBlurred(blurContent(snapshot, size.width, size.height));
        })
        .catch(() => {});
    }, 420);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [size, reminded, dismissed]);

  useEffect(() => {
    shown.set(withTiming(dismissed ? 0 : 1, { duration: dismissed ? 260 : 380, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [dismissed, shown]);

  const tapButton = (index: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    if (index === 0) setReminded((r) => !r);
    else {
      setDismissed(true);
      // Without the loop to bring it back, the alert returns on its own after a moment.
      if (!auto) setTimeout(() => setDismissed(false), 3000);
    }
  };
  const restoreAlert = () => {
    setReminded(false);
    setDismissed(false);
  };

  useFrameCallback((frame) => {
    const f = state.get();
    const box = card.get();
    if (box[2] <= 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    f.now += dt;
    if (f.mode === MODE_AUTO) {
      if (auto) f.t = (f.t + dt) % LOOP;
      scriptFrame(f, reduced ? REDUCED_AT : f.t, box);
      if (reduced) for (let i = 0; i < DROPS; i += 1) f.db[i] = -100;
    } else if (f.mode === MODE_LIVE) {
      if (auto && !f.wiping && f.now - f.lastTouch > IDLE_BEFORE_BACK) {
        beginBack(f);
        scheduleOnRN(restoreAlert);
      }
    } else if (f.mode === MODE_BACK) {
      if (backFrame(f)) {
        f.mode = MODE_AUTO;
        f.t = 0;
      }
    }
    tick.set(tick.get() + 1);
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const f = state.get();
      f.lastTouch = f.now;
      if (f.mode !== MODE_LIVE) f.mode = MODE_LIVE;
      f.wiping = true;
      f.pen = false;
      lay(f, e.x, e.y);
    })
    .onUpdate((e) => {
      const f = state.get();
      f.lastTouch = f.now;
      const last = f.px.length - 1;
      if (last < 0 || Math.hypot(e.x - f.px[last], e.y - f.py[last]) >= STEP) lay(f, e.x, e.y);
    })
    .onEnd((e) => {
      // A short tap that barely moved presses a button, if the glass over it is clear.
      if (Math.hypot(e.translationX, e.translationY) > 10 || shown.get() < 0.5) return;
      const f = state.get();
      const b = buttons.get();
      for (let i = 0; i < 2; i += 1) {
        const x = b[i * 4];
        const y = b[i * 4 + 1];
        const inside = e.x >= x && e.x <= x + b[i * 4 + 2] && e.y >= y && e.y <= y + b[i * 4 + 3];
        if (inside && clearAt(f, e.x, e.y) > 0.5) scheduleOnRN(tapButton, i);
      }
    })
    .onFinalize(() => {
      const f = state.get();
      f.wiping = false;
      f.lastTouch = f.now;
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    const f = state.get();
    const floor = f.mode === MODE_BACK ? f.backK * 1.25 : -1;
    const wipe: number[] = [];
    for (let i = 0; i < TRAIL; i += 1) {
      if (i < f.px.length) wipe.push(f.px[i], f.py[i], f.pr[i], regrow(f, i, floor));
      else wipe.push(0, 0, 0, 2);
    }
    const drops: number[] = [];
    for (let i = 0; i < DROPS; i += 1) drops.push(...dropAt(f, i, f.mode === MODE_BACK ? 1 - f.backK : 1));
    return { size: [Math.max(W, 1), Math.max(H, 1)], ready: blurred ? 1 : 0, card: card.get(), wipe, drops };
  });

  const modalStyle = useAnimatedStyle(() => ({
    opacity: shown.get(),
    transform: [{ translateY: (1 - shown.get()) * 36 }, { scale: 0.96 + 0.04 * shown.get() }],
  }));

  const cardTop = Math.max(insets.top + 150, H * 0.5 - 150);
  // The buttons' boxes on screen = card + row + button (layouts are relative to the parent and
  // may arrive in any order, so each one recomputes all).
  const layouts = useRef({ card: [0, 0, 0, 0], row: [0, 0], b0: [0, 0, 0, 0], b1: [0, 0, 0, 0] });
  const publish = () => {
    const l = layouts.current;
    card.set(l.card.slice());
    const ox = l.card[0] + l.row[0];
    const oy = l.card[1] + l.row[1];
    buttons.set([ox + l.b0[0], oy + l.b0[1], l.b0[2], l.b0[3], ox + l.b1[0], oy + l.b1[1], l.b1[2], l.b1[3]]);
  };
  const onCardLayout = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    layouts.current.card = [x, y, width, height];
    publish();
  };
  const onRowLayout = (e: LayoutChangeEvent) => {
    layouts.current.row = [e.nativeEvent.layout.x, e.nativeEvent.layout.y];
    publish();
  };
  const onRemindLayout = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    layouts.current.b0 = [x, y, width, height];
    publish();
  };
  const onDismissLayout = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    layouts.current.b1 = [x, y, width, height];
    publish();
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const content = blurred ?? placeholder;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <View ref={contentRef} collapsable={false} style={StyleSheet.absoluteFill}>
        <LinearGradient colors={['#0a1424', '#16304b', '#2c4b68']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
        <View style={[styles.weather, { top: insets.top + 28 }]}>
          <Text style={styles.place}>Hollow Creek</Text>
          <Text style={styles.temp}>−4°</Text>
          <Text style={styles.sky}>Clear · frost likely after 22:00</Text>
          <Text style={styles.range}>H −1°   L −6°</Text>
        </View>
        <View style={[styles.hourly, { top: insets.top + 236 }]}>
          {HOURS.map(([time, temp]) => (
            <View key={time} style={styles.hour}>
              <Text style={styles.hourTime}>{time}</Text>
              <Svg width={18} height={18} viewBox="0 0 18 18">
                <Path d="M12.5 3.5 a6 6 0 1 0 2 9.5 a5 5 0 0 1 -2 -9.5 Z" fill="#c9d8ea" />
              </Svg>
              <Text style={styles.hourTemp}>{temp}</Text>
            </View>
          ))}
        </View>
        <View style={[styles.daily, { bottom: insets.bottom + 24 }]}>
          <Text style={styles.dailyTitle}>NEXT 3 DAYS</Text>
          {DAYS.map(([day, low, high, share]) => (
            <View key={day} style={styles.day}>
              <Text style={styles.dayName}>{day}</Text>
              <Text style={styles.dayLow}>{low}</Text>
              <View style={styles.dayTrack}>
                <LinearGradient
                  colors={['#7fb4e8', '#b9d6f0', '#f2d39a']}
                  locations={[0, 0.6, 1]}
                  start={{ x: 0, y: 0 }}
                  end={{ x: 1, y: 0 }}
                  style={[styles.dayFill, { left: `${Number(share) * 40}%`, right: `${(1 - Number(share)) * 30}%` }]}
                />
              </View>
              <Text style={styles.dayHigh}>{high}</Text>
            </View>
          ))}
        </View>
        <View style={styles.dim} />
        {size ? (
          <Animated.View style={[styles.card, { top: cardTop }, modalStyle]} onLayout={onCardLayout}>
            <View style={styles.cardHead}>
              <View style={styles.icon}>
                <Svg width={24} height={24} viewBox="0 0 24 24">
                  {[0, 60, 120].map((a) => (
                    <Path
                      key={a}
                      d="M12 2.5 V21.5 M12 6 L9.5 3.8 M12 6 L14.5 3.8 M12 18 L9.5 20.2 M12 18 L14.5 20.2"
                      stroke="#2a5d8f"
                      strokeWidth={1.7}
                      strokeLinecap="round"
                      fill="none"
                      transform={`rotate(${a} 12 12)`}
                    />
                  ))}
                  <Circle cx={12} cy={12} r={1.6} fill="#2a5d8f" />
                </Svg>
              </View>
              <View style={styles.badge}>
                <Text style={styles.badgeText}>ADVISORY</Text>
              </View>
            </View>
            <Text style={styles.title}>Frost advisory</Text>
            <Text style={styles.body}>−4 °C tonight in Hollow Creek. Cover your plants by 18:00.</Text>
            <Text style={styles.meta}>Issued 15:20 · until Sun 09:00</Text>
            <View style={styles.row} onLayout={onRowLayout}>
              <View style={[styles.button, styles.primary]} onLayout={onRemindLayout}>
                {reminded ? (
                  <Svg width={14} height={14} viewBox="0 0 14 14">
                    <Path d="M2.5 7.5 L5.6 10.4 L11.5 3.8" stroke="#f4f8fc" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
                  </Svg>
                ) : null}
                <Text style={styles.primaryText}>{reminded ? 'Reminder at 17:30' : 'Remind me'}</Text>
              </View>
              <View style={[styles.button, styles.secondary]} onLayout={onDismissLayout}>
                <Text style={styles.secondaryText}>Dismiss</Text>
              </View>
            </View>
          </Animated.View>
        ) : null}
      </View>

      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A frosted window over a frost advisory. Drag to wipe the glass; the buttons work where it is clear."
        >
          {size && PANE && ice && content ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={PANE} uniforms={uniforms}>
                  <ImageShader image={ice.ice} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
                  <ImageShader image={ice.haze} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
                  <ImageShader image={content} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
                </Shader>
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

const HOURS = [
  ['18:00', '−1°'],
  ['21:00', '−3°'],
  ['00:00', '−4°'],
  ['03:00', '−4°'],
  ['06:00', '−2°'],
];
const DAYS = [
  ['Sat', '−6°', '2°', '0.1'],
  ['Sun', '−4°', '4°', '0.35'],
  ['Mon', '0°', '7°', '0.8'],
];
const CARD_PAD = 24;

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0a1424', overflow: 'hidden' },
  weather: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  place: { color: '#e9f1f8', fontSize: 20, fontWeight: '600' },
  temp: { color: '#f2f7fb', fontSize: 92, fontWeight: '200', fontVariant: ['tabular-nums'], marginTop: -2 },
  sky: { color: 'rgba(233,241,248,0.75)', fontSize: 15, fontWeight: '500' },
  range: { color: 'rgba(233,241,248,0.55)', fontSize: 14, fontWeight: '500', marginTop: 4, fontVariant: ['tabular-nums'] },
  hourly: {
    position: 'absolute',
    left: 20,
    right: 20,
    height: 96,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(214,232,250,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(214,232,250,0.16)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
  },
  hour: { alignItems: 'center', gap: 8 },
  hourTime: { color: 'rgba(233,241,248,0.6)', fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hourTemp: { color: '#e9f1f8', fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  daily: {
    position: 'absolute',
    left: 20,
    right: 20,
    paddingHorizontal: 20,
    paddingVertical: 16,
    gap: 12,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(214,232,250,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(214,232,250,0.16)',
  },
  dailyTitle: { color: 'rgba(233,241,248,0.55)', fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  day: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  dayName: { color: '#e9f1f8', fontSize: 15, fontWeight: '600', width: 40 },
  dayLow: { color: 'rgba(233,241,248,0.6)', fontSize: 15, fontWeight: '500', width: 36, textAlign: 'right', fontVariant: ['tabular-nums'] },
  dayTrack: { flex: 1, height: 4, borderRadius: 2, backgroundColor: 'rgba(214,232,250,0.12)' },
  dayFill: { position: 'absolute', top: 0, bottom: 0, borderRadius: 2 },
  dayHigh: { color: '#e9f1f8', fontSize: 15, fontWeight: '600', width: 32, fontVariant: ['tabular-nums'] },
  dim: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: 'rgba(4,9,18,0.5)' },
  card: {
    position: 'absolute',
    left: 24,
    right: 24,
    padding: CARD_PAD,
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: '#eef3f7',
    boxShadow: '0 2px 4px rgba(2,8,18,0.35), 0 24px 48px rgba(2,8,18,0.45), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  cardHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 },
  icon: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#d8e6f2', alignItems: 'center', justifyContent: 'center' },
  badge: { paddingHorizontal: 10, height: 24, borderRadius: 12, backgroundColor: '#dbe7f2', justifyContent: 'center' },
  badgeText: { color: '#2a5d8f', fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  title: { color: '#0f1c2b', fontSize: 24, fontWeight: '700', letterSpacing: -0.3 },
  body: { color: '#3a4a5b', fontSize: 16, lineHeight: 22, marginTop: 8 },
  meta: { color: '#6a7989', fontSize: 13, fontWeight: '500', marginTop: 12, fontVariant: ['tabular-nums'] },
  row: { flexDirection: 'row', gap: 12, marginTop: 20 },
  button: { flex: 1, height: 48, borderRadius: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  primary: { backgroundColor: '#2f6fb3', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25), 0 1px 2px rgba(10,30,60,0.3)' },
  primaryText: { color: '#f4f8fc', fontSize: 15, fontWeight: '600' },
  secondary: { backgroundColor: '#dde6ee' },
  secondaryText: { color: '#21384f', fontSize: 15, fontWeight: '600' },
});
