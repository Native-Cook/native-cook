import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type ImageSourcePropType, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { SensorType, useAnimatedSensor, interpolateColor, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, ImageShader, Rect, Shader, Skia, useFont, useImage, type SkFont, type SkImage } from '@shopify/react-native-skia';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { useStampFeedback } from './psc-audio';
import { clamp, cardImpulse, flipFrame, frameAt, LOOP, mix, out, restFrame, seg, stampEvent, type Frame } from './psc-motion';
import { BUTTON_FX, PAPER_FX, RELIEF_FX } from './psc-shaders';
import { C, CARD, FONT, LABEL, LIST, orderAt, sealCX, sealCY, SPRING, wellX, wellY } from './psc-theme';

type Props = {
  autoPlay?: boolean;
  /** This four-by-two composition has eight wells. */
  stamps?: number;
  onStamp?: (count: number) => void;
  onComplete?: () => void;
  seal?: ImageSourcePropType;
  sealText?: { top: string; bottom: string };
  tilt?: boolean;
};
const IDS = [0, 1, 2, 3, 4, 5, 6, 7];
const COUNTS = [0, 1, 2, 3, 4, 5, 6, 7, 8];
const P3 = Platform.OS === 'ios' ? 1 : 0;
const FONTS = {
  [FONT.serif]: require('./assets/fonts/PSCSerif-SemiBold.ttf'),
  [FONT.regular]: require('./assets/fonts/PSCInter-Regular.ttf'),
  [FONT.medium]: require('./assets/fonts/PSCInter-Medium.ttf'),
  [FONT.semibold]: require('./assets/fonts/PSCInter-SemiBold.ttf'),
  [FONT.bold]: require('./assets/fonts/PSCInter-Bold.ttf'),
};

/** Genuine glyph advances on circular baselines; @3x even at the largest hovering size. */
function makeInkAtlas(image: SkImage, font: SkFont, top: string, bottom: string) {
  const surface = Skia.Surface.MakeOffscreen(336, 336);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(Skia.Color('#2E1F0E'));
  canvas.clear(Skia.Color('transparent')); canvas.scale(6, 6);
  canvas.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, 56, 56), paint);
  const arc = (text: string, upper: boolean) => {
    const glyphs = font.getGlyphIDs(text); const widths = font.getGlyphWidths(glyphs);
    const radius = upper ? 20.8 : 24.4; const tracking = 0.25;
    const width = widths.reduce((a, b) => a + b + tracking, -tracking);
    let pen = -width / 2;
    glyphs.forEach((glyph, i) => {
      const a = (pen + widths[i] / 2) / radius;
      const theta = upper ? -Math.PI / 2 + a : Math.PI / 2 - a;
      canvas.save(); canvas.translate(28 + Math.cos(theta) * radius, 28 + Math.sin(theta) * radius);
      canvas.rotate((upper ? theta + Math.PI / 2 : theta - Math.PI / 2) * 180 / Math.PI, 0, 0);
      canvas.drawGlyphs([glyph], [{ x: -widths[i] / 2, y: 0 }], 0, 0, font, paint);
      canvas.restore(); pen += widths[i] + tracking;
    });
  };
  arc(top, true); arc(bottom, false); surface.flush();
  return surface.makeImageSnapshot();
}
const ICONS = {
  cup: 'M4 4.5Q4 3 5.5 3h13Q20 3 20 4.5T18.5 6h-13Q4 6 4 4.5ZM5.5 7l2 14h9l2-14M6.5 12h11l-.6 4H7.1',
  home: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9',
  card: 'M3 6h18v14H3V6ZM3 10h18M6 15h4',
  gift: 'M3 9h18v5H3V9ZM5 14v7h14v-7M12 9v12M12 9C4 9 5 2 8 3c2 0 4 6 4 6ZM12 9s2-6 4-6c3-1 4 6-4 6Z',
  person: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2',
  qr: 'M3 3h6v6H3V3ZM15 3h6v6h-6V3ZM3 15h6v6H3v-6ZM15 15h2v2h-2v-2ZM20 14v3M14 20h3v-2M20 20h1v1',
} as const;
function Icon({ name, size = 22, color = C.ink }: { name: keyof typeof ICONS; size?: number; color?: string }) {
  return <Svg width={size} height={size} viewBox="0 0 24 24"><Path d={ICONS[name]} fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" /></Svg>;
}
function Digit({ n, frame }: { n: number; frame: SharedValue<Frame> }) {
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const same = f.oldCount === f.count; const incoming = n === f.count;
    return { opacity: same ? (incoming ? 1 : 0) : incoming ? f.roll : n === f.oldCount ? 1 - f.roll : 0,
      transform: [{ translateY: same ? 0 : incoming ? 40 * (1 - f.roll) : -40 * f.roll }, { scale: f.count === 8 ? 1 + 0.03 * Math.sin(f.phase * Math.PI * 2) : 1 }], color: f.gold > 0.4 ? C.gold : C.caramelDark };
  });
  return <Animated.Text style={[styles.counter, style]}>{n}/8</Animated.Text>;
}
function Sentence({ count, frame }: { count: number; frame: SharedValue<Frame> }) {
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const incoming = f.textTo === count;
    return { opacity: incoming ? 1 : f.textFrom === count && f.text < 1 ? 1 : 0,
      transform: [{ translateY: incoming ? 20 * (1 - f.text) : -20 * f.text }] };
  });
  return <Animated.Text style={[styles.sentenceDigit, style]}>{8 - count}</Animated.Text>;
}
function CoffeeSuffix({ frame }: { frame: SharedValue<Frame> }) {
  const regular = useAnimatedStyle(() => ({ opacity: frame.get().count < 8 ? 1 : 0 }));
  // The noun follows the digit that is showing, so it changes half way through the roll.
  const one = () => { 'worklet'; const f = frame.get(); return f.textTo === 7 && f.text >= 0.5 || f.textFrom === 7 && f.textTo !== 7 && f.text < 0.5; };
  const singular = useAnimatedStyle(() => ({ opacity: one() ? 1 : 0 }));
  const plural = useAnimatedStyle(() => ({ opacity: one() ? 0 : 1 }));
  const complete = useAnimatedStyle(() => ({ opacity: frame.get().count === 8 ? 1 : 0 }));
  return <>
    <Animated.View style={[styles.sentenceLine, regular]}><View style={styles.numberWindow}>{COUNTS.slice(0, 8).map(n => <Sentence key={n} count={n} frame={frame} />)}</View>
      <View style={{ width: 295 }}><Animated.Text style={[styles.sentenceWords, plural]}>more stamps until your free coffee</Animated.Text><Animated.Text style={[styles.sentenceWords, singular]}>more stamp until your free coffee</Animated.Text></View>
    </Animated.View><Animated.Text style={[styles.complete, complete]} numberOfLines={1}>Complete! Your next coffee is on us.</Animated.Text>
  </>;
}
function OrderRow({ j, frame, origin }: { j: number; frame: SharedValue<Frame>; origin: SharedValue<{ x: number; y: number }> }) {
  const order = orderAt(j);
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const pos = f.orders - j;
    const jolt = cardImpulse(f.jolt - (Math.max(0, Math.round(pos)) + 1) * 0.03, 2);
    return { transform: [{ translateY: Math.round((pos * LIST.pitch + jolt) * 3) / 3 }], opacity: 1 };
  });
  const tag = useAnimatedStyle(() => {
    const f = frame.get(); const taken = j - 1 >= 5 && (f.count > j - 1 || (f.target === j - 1 && f.event >= 0.25)); return { opacity: taken ? 0 : 1 };
  });
  return <Animated.View style={[styles.order, style]}>
    <View style={styles.orderDisc}><Icon name="cup" size={23} color={C.cup} /></View>
    <Text style={styles.orderName} numberOfLines={1}>{order.name}</Text><Text style={styles.orderTime}>{order.time}</Text>
    <Text style={styles.orderPrice}>{order.price}</Text><Animated.Text onLayout={e => { const { width, height } = e.nativeEvent.layout; origin.set({ x: LIST.x + LIST.w - LABEL.right - width / 2, y: 37 + height / 2 }); }} style={[styles.orderTag, tag]}>+1 stamp</Animated.Text>
  </Animated.View>;
}
function WellNumber({ i, frame }: { i: number; frame: SharedValue<Frame> }) {
  const style = useAnimatedStyle(() => ({ opacity: 1 - frame.get().seals[i].a }));
  return <Animated.Text style={[styles.wellNumber, { left: wellX(i) - 15, top: wellY(i) - 12 }, style]}>{`0${i + 1}`}</Animated.Text>;
}

/** Eight engraved impressions share one light; the last press gilds the whole set. */
export function PressedStampCard({ autoPlay = true, stamps = 5, onStamp, onComplete, seal, sealText, tilt = true }: Props) {
  const reduced = useReducedMotionPreference(); const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ w: 402, h: 874 }); const [fontsOK] = useFonts(FONTS);
  const art = useImage(seal ?? require('./assets/ps-seal-art.png'));
  const leather = useImage(require('./assets/ps-leather.png')); const paper = useImage(require('./assets/ps-paper.png'));
  const ringFont = useFont(require('./assets/fonts/PSCSerif-Bold.ttf'), 5.6);
  const atlas = useMemo(() => art && ringFont ? makeInkAtlas(art, ringFont, sealText?.top ?? 'FRESHLY ROASTED', sealText?.bottom ?? 'COFFEE CLUB') : null, [art, ringFont, sealText?.top, sealText?.bottom]);
  const feedback = useStampFeedback(); const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 32 });
  const audit = useRef<{ impacts: number[]; completions: number }>({ impacts: [], completions: 0 });
  const tabsBottom = Math.max(10, insets.bottom - 10);
  const firstOrderEnd = LIST.top + LIST.rowH;
  const S = Math.max(0.01, Math.min(size.w / 402, (size.h - insets.top - tabsBottom) / (firstOrderEnd + 64 - 62)));
  const dx = (size.w - 402 * S) / 2; const dy = insets.top - 62 * S;
  const t = useSharedValue(0); const speed = useSharedValue(1); const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0); const base = useSharedValue(Math.round(clamp(stamps, 0, 8)));
  const event = useSharedValue(-1); const target = useSharedValue(5); const queued = useSharedValue(0);
  const impactSent = useSharedValue(0); const successSent = useSharedValue(0);
  const held = useSharedValue(0); const hx = useSharedValue(0); const hy = useSharedValue(0);
  const dragTilt = useSharedValue(0); const dragAz = useSharedValue(0); const dropDuration = useSharedValue(0.14); const redeemed = useSharedValue(0);
  const magnet = useSharedValue(0); const holdTime = useSharedValue(0); const cancel = useSharedValue(-1);
  const lastTouch = useSharedValue(0); const clock = useSharedValue(0); const pull = useSharedValue(0);
  const redeemThreshold = useSharedValue(0);
  const button = useSharedValue(1); const rock = useSharedValue(-1); const rockTime = useSharedValue(2);
  const reset = useSharedValue(-1); const resume = useSharedValue(-1); const liveAz = useSharedValue(315); const liveEl = useSharedValue(40);
  const lightOverride = useSharedValue(999); const reducedOverride = useSharedValue(-1);
  const labelOrigin = useSharedValue<{ x: number; y: number }>({ x: LABEL.cx, y: LABEL.cy });
  const isReduced = useDerivedValue(() => reducedOverride.get() < 0 ? reduced : reducedOverride.get() === 1);
  const dropFrom = useSharedValue<{ x: number; y: number } | null>(null);
  const frame = useDerivedValue(() => {
    const rm = isReduced.get();
    let f = auto.get() && !rm ? frameAt(t.get(), labelOrigin.get()) : restFrame(base.get(), rm ? 315 : liveAz.get());
    if (!auto.get() && event.get() >= 0) f = stampEvent(f, event.get(), target.get(), rm, labelOrigin.get());
    if (lightOverride.get() !== 999) f.light = lightOverride.get();
    const from = dropFrom.get(); const impact = target.get() === 7 ? 1.14 : 0.96;
    if (from && event.get() >= impact - 0.14 && event.get() < impact && !rm) {
      const p = seg(event.get(), impact - 0.14, impact); const fall = p * p * p;
      f.fly.x = mix(from.x, sealCX(target.get()), fall); f.fly.y = mix(from.y, sealCY(target.get()), fall);
    }
    if (from && !rm) f.dip *= 0.65 + clamp(holdTime.get() / 1.2) * 0.35;
    if (rock.get() >= 0 && rockTime.get() < 0.7 && !rm) {
      const i = rock.get(); const dt = rockTime.get();
      f.seals[i].rotation += 5 * Math.sin(dt * 24) * Math.exp(-dt * 7); f.seals[i].flash = Math.exp(-dt * 7) * 0.5;
    }
    if (held.get() || cancel.get() >= 0) {
      const leave = cancel.get() >= 0 ? out(seg(cancel.get(), 0, 0.32)) : 0;
      f.fly = { x: hx.get() + leave * 440, y: hy.get() - leave * 80, r: 52, a: rm ? 0 : 1, rotation: dragTilt.get(), gold: 0, flash: 0, lift: 1 };
      f.dieTilt = dragTilt.get();
    }
    if (reset.get() >= 0) f = flipFrame(reset.get(), f.light, rm);
    if (redeemed.get() && reset.get() < 0) f.orders = 5;
    if (!auto.get() && !rm) f.phase = clock.get();
    if (!auto.get() && dragAz.get() && f.count === 8 && !rm) {
      f.light = liveAz.get(); f.sweep = clamp((liveAz.get() - 270) / 90);
    }
    if (resume.get() >= 0) {
      const p = out(seg(resume.get(), 0, 0.6)); const rest = restFrame(5);
      for (let i = 0; i < 8; i++) { f.seals[i].a = mix(f.seals[i].a, rest.seals[i].a, p); f.seals[i].gold *= 1 - p; }
      f.gold *= 1 - p; f.orders = mix(f.orders, 5, p); if (p > 0.5) { f.count = 5; f.oldCount = 5; }
      f.textFrom = base.get(); f.textTo = 5; f.text = p;
    }
    return f;
  });
  const notifyStamp = (n: number) => { if (__DEV__) audit.current.impacts.push(n); feedback(n === 8 ? 'rigid' : 'impact'); if (n === 8) feedback('gold'); onStamp?.(n); };
  const notifyComplete = () => { if (__DEV__) audit.current.completions += 1; feedback('success'); onComplete?.(); };
  useEffect(() => { auto.set(autoPlay && !reduced ? 1 : 0); }, [autoPlay, reduced, auto]);
  useEffect(() => { base.set(Math.round(clamp(stamps, 0, 8))); }, [stamps, base]);
  const takeOver = () => {
    'worklet';
    if (auto.get()) {
      const f = frameAt(t.get(), labelOrigin.get()); base.set(f.count); liveAz.set(f.light); liveEl.set(40);
      if (f.target >= 0 && f.event >= 0 && f.event < (f.target === 7 ? 2.65 : 2.2)) {
        base.set(f.target); target.set(f.target); event.set(f.event); impactSent.set(f.count > f.target ? 1 : 0);
      }
      auto.set(0);
    }
    resume.set(-1); pinned.set(0); lastTouch.set(clock.get());
  };
  const startScan = () => {
    'worklet'; takeOver();
    if (held.get() || reset.get() >= 0) return;
    if (event.get() >= 0) { queued.set(1); return; }
    if (base.get() >= 8) return;
    redeemed.set(0); dragAz.set(0); dropFrom.set(null); target.set(base.get()); event.set(0); impactSent.set(0); successSent.set(0); scheduleOnRN(feedback, 'scan');
  };
  const moveHold = (x: number, y: number, vx = 0, vy = 0) => {
    'worklet'; const velocity = Math.hypot(vx, vy) * (vx < 0 ? -1 : 1); dragTilt.set(clamp(velocity / 90, -10, 10)); const i = Math.min(base.get(), 7); const dist = Math.hypot(x - sealCX(i), y - sealCY(i));
    const engaged = dist <= 48 ? 1 : 0; if (engaged && !magnet.get()) scheduleOnRN(feedback, 'magnet');
    magnet.set(engaged); hx.set(mix(x, sealCX(i), engaged ? 0.35 : 0)); hy.set(mix(y, sealCY(i), engaged ? 0.35 : 0));
  };
  const startHold = (x: number, y: number) => {
    'worklet'; takeOver();
    if (base.get() >= 8 || event.get() >= 0 || reset.get() >= 0) return;
    held.set(1); holdTime.set(0); cancel.set(-1); moveHold(x, y); scheduleOnRN(feedback, 'grab');
  };
  const releaseHold = (commit = true) => {
    'worklet'; if (!held.get()) return; held.set(0); lastTouch.set(clock.get());
    if (commit && magnet.get()) {
      dropFrom.set({ x: hx.get(), y: hy.get() }); target.set(base.get()); event.set(target.get() === 7 ? 1 : 0.82); dropDuration.set(0.1 + 0.1 * clamp(holdTime.get() / 1.2)); impactSent.set(0); successSent.set(0);
    } else { cancel.set(0); scheduleOnRN(feedback, 'cancel'); }
    magnet.set(0);
  };
  const redeem = (distance: number) => {
    'worklet'; takeOver();
    if (base.get() === 8 && event.get() < 0 && distance >= 72) { reset.set(0); redeemed.set(1); if (!redeemThreshold.get()) scheduleOnRN(feedback, 'scan'); }
    redeemThreshold.set(0);
    pull.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k })); lastTouch.set(clock.get());
  };
  useFrameCallback(info => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.67) / 1000, 0.064); clock.set(clock.get() + dt);
    if (pinned.get()) return;
    if (auto.get() && !isReduced.get()) t.set((t.get() + dt * speed.get()) % LOOP);
    else {
      if (tilt && !dragAz.get() && !isReduced.get() && lightOverride.get() === 999) {
        const roll = sensor.sensor.get().roll * 180 / Math.PI; const az = 315 + clamp(roll * 0.8, -25, 25);
        liveAz.set(mix(liveAz.get(), az, 1 - Math.exp(-dt * 7)));
        const pitch = sensor.sensor.get().pitch * 180 / Math.PI;
        liveEl.set(mix(liveEl.get(), clamp(40 - pitch * 0.5, 28, 55), 1 - Math.exp(-dt * 7)));
      }
      if (event.get() >= 0) {
        const falling = dropFrom.get() && event.get() < (target.get() === 7 ? 1.14 : 0.96);
        const next = event.get() + dt * (falling ? 0.14 / dropDuration.get() : 1); event.set(next);
        const impact = isReduced.get() ? 0.2 : target.get() === 7 ? 1.14 : 0.96;
        if (next >= impact && !impactSent.get()) { impactSent.set(1); scheduleOnRN(notifyStamp, target.get() + 1); }
        const end = isReduced.get() ? 0.4 : target.get() === 7 ? 2.65 : 2.2;
        if (next >= end) {
          base.set(target.get() + 1); event.set(-1); dropFrom.set(null);
          if (base.get() === 8 && !successSent.get()) { successSent.set(1); scheduleOnRN(notifyComplete); }
          if (queued.get()) { queued.set(0); if (base.get() < 8) { target.set(base.get()); event.set(0); impactSent.set(0); } }
        }
      }
      if (held.get()) holdTime.set(holdTime.get() + dt);
      if (cancel.get() >= 0) { cancel.set(cancel.get() + dt); if (cancel.get() >= 0.32) cancel.set(-1); }
      if (reset.get() >= 0) { reset.set(reset.get() + dt); if (reset.get() >= (isReduced.get() ? 0.2 : 0.7)) { base.set(0); reset.set(-1); } }
      if (rockTime.get() < 1) rockTime.set(rockTime.get() + dt);
      if (resume.get() >= 0) {
        resume.set(resume.get() + dt); if (resume.get() >= 0.6) { base.set(5); t.set(0); auto.set(1); resume.set(-1); }
      } else if (autoPlay && !redeemed.get() && !isReduced.get() && !held.get() && event.get() < 0 && reset.get() < 0 && clock.get() - lastTouch.get() > 2) resume.set(0);
    }
  });
  useEffect(() => {
    if (!__DEV__) return; const g = globalThis as { __psc?: unknown };
    g.__psc = {
      at: (time: number) => { redeemed.set(0); dragAz.set(0); lightOverride.set(999); event.set(-1); held.set(0); magnet.set(0); queued.set(0); dropFrom.set(null); cancel.set(-1); resume.set(-1); reset.set(-1); rock.set(-1); auto.set(1); t.set(time); pinned.set(1); },
      play: () => { pinned.set(0); auto.set(1); }, rate: (r: number) => speed.set(clamp(r, 0, 4)),
      state: () => ({ frame: frame.get(), reset: reset.get(), redeemed: redeemed.get(), t: t.get(), count: frame.get().count, orders: frame.get().orders, event: event.get(), target: target.get(), queued: queued.get(), held: held.get(), magnet: magnet.get(), gold: frame.get().gold, fly: frame.get().fly, light: frame.get().light, reduced: isReduced.get(), seals: frame.get().seals, auto: auto.get(), pinned: pinned.get(), audit: audit.current }),
      clearAudit: () => { audit.current = { impacts: [], completions: 0 }; },
      scan: () => scheduleOnUI(startScan), hold: (x: number, y: number) => scheduleOnUI(startHold, x, y),
      move: (x: number, y: number, velocity = 0) => scheduleOnUI(moveHold, x, y, velocity), release: () => scheduleOnUI(releaseHold),
      redeem: (d: number) => scheduleOnUI(redeem, d), light: (az: number) => lightOverride.set(az), foil: (x: number) => { dragAz.set(1); liveAz.set(315 + (x - 201) * 0.25); },
      reduced: (v: boolean) => { reducedOverride.set(v ? 1 : 0); auto.set(0); },
      live: (n: number) => { redeemed.set(0); dragAz.set(0); reset.set(-1); resume.set(-1); base.set(Math.round(clamp(n, 0, 8))); auto.set(0); event.set(-1); pinned.set(1); held.set(0); },
      step: (seconds: number) => { event.set(seconds); pinned.set(1); },
    }; return () => { delete g.__psc; };
  });
  const cardGesture = Gesture.Pan().activateAfterLongPress(180).onStart(e => {
    if (frame.get().count === 8) { takeOver(); return; } startHold(e.x / S, e.y / S);
  }).onUpdate(e => {
    if (held.get()) moveHold(e.x / S, e.y / S, e.velocityX / S, e.velocityY / S);
    else if (base.get() === 8) { dragAz.set(1); liveAz.set(315 + (e.x / S + CARD.x - 201) * 0.25); lastTouch.set(clock.get()); const d = Math.max(0, e.translationY / S); pull.set(d / (1 + d / 120)); }
  }).onEnd((e, ok) => {
    if (held.get()) releaseHold(ok);
    else if (base.get() === 8) redeem(ok ? e.translationY / S : 0);
  }).onFinalize(() => { if (held.get()) releaseHold(false); });
  const rockGesture = Gesture.Tap().onEnd((e, ok) => {
    if (!ok) return; takeOver();
    for (let i = 0; i < base.get(); i++) if (Math.hypot(e.x / S - sealCX(i), e.y / S - sealCY(i)) < 34) {
      rock.set(i); rockTime.set(0); scheduleOnRN(feedback, 'magnet'); break;
    }
  });
  const foilGesture = Gesture.Pan().activeOffsetX([-6, 6]).onTouchesDown((_e, manager) => {
    if (frame.get().count !== 8) manager.fail();
  }).onStart(() => takeOver()).onUpdate(e => {
    dragAz.set(1); liveAz.set(315 + (e.x / S + CARD.x - 201) * 0.25); lastTouch.set(clock.get());
  }).onEnd(() => lastTouch.set(clock.get()));
  const redeemGesture = Gesture.Pan().activeOffsetY(6).onTouchesDown((_e, manager) => {
    const f = frame.get();
    if (f.count !== 8 || (f.target === 7 && f.event < 2.65)) manager.fail();
  }).onStart(() => { takeOver(); redeemThreshold.set(0); }).onUpdate(e => {
    dragAz.set(1); liveAz.set(315 + (e.x / S + CARD.x - 201) * 0.25); lastTouch.set(clock.get());
    const d = Math.max(0, e.translationY / S); pull.set(d / (1 + d / 120));
    if (d >= 72 && !redeemThreshold.get()) { redeemThreshold.set(1); scheduleOnRN(feedback, 'scan'); }
  }).onEnd((e, ok) => redeem(ok ? e.translationY / S : 0));
  const cardStyle = useAnimatedStyle(() => { const f = frame.get(); return { transform: [{ perspective: f.flip ? 1100 : 900 }, { translateY: f.dip + pull.get() }, { rotateY: `${f.flip}deg` }, { rotateX: `${f.tilt}deg` }, { rotateZ: `${f.tilt * (f.target % 4 < 2 ? -1 : 1)}deg` }] }; });
  const screenPush = useAnimatedStyle(() => ({ transform: [{ scale: frame.get().push }] }));
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ translateY: cardImpulse(frame.get().jolt - 0.12, 2) }, { scale: button.get() }] }));
  const scanLabel = useAnimatedStyle(() => ({ opacity: 1 - frame.get().buttonRoll, color: interpolateColor(frame.get().buttonGold, [0, 1], [C.buttonText, C.ink]), transform: [{ translateY: -24 * frame.get().buttonRoll }] }));
  const redeemLabel = useAnimatedStyle(() => ({ opacity: frame.get().buttonRoll, color: C.ink, transform: [{ translateY: 24 * (1 - frame.get().buttonRoll) }] }));
  const touchRing = useAnimatedStyle(() => { const p = frame.get().redeemRing; return { opacity: p > 0 ? 0.4 * (1 - p) : 0, transform: [{ scale: 1 + p * 0.6 }] }; });
  const buttonMaterial = useDerivedValue(() => ({ uP3: P3, uGold: frame.get().buttonGold, uPhase: frame.get().phase, uLight: frame.get().light }));
  const material = useDerivedValue(() => {
    const f = frame.get(); const seals: number[] = []; const details: number[] = [];
    for (let i = 0; i < 8; i++) {
      const v = f.seals[i]; seals.push(v.x, v.y, v.r, v.a); details.push(v.rotation, v.gold, v.flash, v.lift);
    }
    return { uP3: P3, uLight: f.light, uElevation: auto.get() || isReduced.get() ? 40 : liveEl.get(),
      uSeals: seals, uDetail: details,
      uDent: f.dent, uSweep: f.sweep, uPhase: f.phase, uNext: f.count < 8 ? f.count : -1, uFlipFlash: f.flipFlash,
      uRipple: isReduced.get() ? [0, 0, 0, 0] : f.ripple, uGold: f.gold, uMagnet: magnet.get() ? base.get() : -1 };
  });
  const dieStyle = useAnimatedStyle(() => {
    const f = frame.get(); const v = f.fly; const lift = v.lift;
    return { opacity: v.a ? 1 : 0, shadowOpacity: mix(0.6, 0.35, lift), shadowRadius: mix(3, 30, lift), shadowOffset: { width: 18 * lift, height: mix(2, 26, lift) },
      transform: [{ translateX: v.x + CARD.x - 55 }, { translateY: v.y + CARD.y - 55 + f.dip }, { perspective: 900 }, { rotateX: `${f.dieTilt}deg` }, { rotateZ: `${v.rotation}deg` }, { scale: v.r / 55 }] };
  });
  const knobStyle = useAnimatedStyle(() => ({ transform: [{ translateX: frame.get().dieTilt / 8 * 9 }, { translateY: -frame.get().dieTilt / 8 * 9 }] }));
  const dieGlow = useAnimatedStyle(() => ({ opacity: Math.min(1, frame.get().dieGlow / 0.7) }));
  const dieHeat = useAnimatedStyle(() => ({ opacity: frame.get().dieGlow * 0.4 }));
  const flyTagStyle = useAnimatedStyle(() => {
    const f = frame.get(); const p = out(seg(f.event, 0.25, 0.37)); const origin = labelOrigin.get();
    return { opacity: f.target >= 0 && f.event >= 0.25 && f.event < (f.target === 7 ? 1.14 : 0.96) ? 1 : 0,
      transform: [{ translateX: mix(origin.x, CARD.x + f.fly.x + f.dieTilt / 8 * 9, p) - 40 },
        { translateY: mix(LIST.top + origin.y + (f.orders - f.target - 1) * LIST.pitch, CARD.y + f.fly.y - f.dieTilt / 8 * 9, p) - 9 - 6 * Math.sin(Math.PI * seg(f.event, 0.25, 0.37)) }, { scale: mix(1, 0.8, p) }] };
  });
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  return <View onLayout={onLayout} style={styles.root}>
    <StatusBar style="dark" />
    {paper && <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Rect x={0} y={0} width={size.w} height={size.h}><Shader source={PAPER_FX} uniforms={{ uP3: Platform.OS === 'ios' ? 1 : 0 }}><ImageShader image={paper} tx="repeat" ty="repeat" rect={Skia.XYWHRect(0, 0, 256, 256)} fit="fill" /></Shader></Rect></Canvas>}
    {fontsOK && <View style={{ position: 'absolute', left: dx, width: 402, height: (size.h - dy) / S, transformOrigin: 'top left', transform: [{ translateY: dy }, { scale: S }] }}><Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [311, 326, 0] }, screenPush]}>
      <Text style={styles.title}>Your card</Text>
      {/* Flat containers: iOS depth-sorts sibling layers with 3D transforms, which let the tilted card cut through the die. */}
      <View collapsable={false} style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View style={[styles.cardAmbient, cardStyle]}><View style={styles.cardContact} /></Animated.View>
      <Animated.View style={[styles.card, cardStyle]}><GestureDetector gesture={Gesture.Race(foilGesture, redeemGesture, cardGesture, rockGesture)}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Coffee Club stamp card. Hold and drag to press a stamp." accessibilityRole="adjustable" accessibilityActions={[{ name: 'increment', label: 'Add a stamp' }]} onAccessibilityAction={e => { if (e.nativeEvent.actionName === 'increment') scheduleOnUI(startScan); }}>
          {atlas && leather && <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Rect x={0} y={0} width={CARD.w} height={CARD.h}><Shader source={RELIEF_FX} uniforms={material}>
            <ImageShader image={atlas} rect={Skia.XYWHRect(0, 0, 56, 56)} fit="fill" tx="clamp" ty="clamp" />
            <ImageShader image={leather} rect={Skia.XYWHRect(0, 0, 128, 128)} fit="fill" tx="repeat" ty="repeat" />
          </Shader></Rect></Canvas>}
          <Text style={styles.cardLabel}>STAMP CARD</Text><View style={styles.counterWindow}>{COUNTS.map(n => <Digit key={n} n={n} frame={frame} />)}</View>
          {IDS.map(i => <WellNumber key={i} i={i} frame={frame} />)}<CoffeeSuffix frame={frame} />
        </View>
      </GestureDetector></Animated.View>
      </View>
      <Animated.View style={[styles.scanShadow, buttonStyle]}><Pressable accessibilityRole="button" accessibilityLabel="Scan to add a stamp, or redeem your completed card." onPressIn={() => button.set(withTiming(0.97, { duration: 80 }))} onPressOut={() => button.set(withSpring(1, { damping: 30.5, stiffness: 322.3 }))} onPress={() => { scheduleOnUI(() => { 'worklet'; if (frame.get().count === 8) { takeOver(); if (event.get() >= 0) { base.set(8); event.set(-1); } redeem(72); } else startScan(); }); }} style={styles.scan}>
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Rect x={0} y={0} width={362} height={56}><Shader source={BUTTON_FX} uniforms={buttonMaterial} /></Rect></Canvas>
        <Animated.View style={[{ position: 'absolute', left: 72, top: 17 }, scanLabel]}><Icon name="qr" color={C.buttonText} size={21} /></Animated.View>
        <View style={styles.buttonLabelWindow}><Animated.Text style={[styles.scanText, scanLabel]}>Scan at the counter</Animated.Text><Animated.Text style={[styles.scanText, redeemLabel]}>Redeem free coffee</Animated.Text></View>
        <Animated.View pointerEvents="none" style={[styles.touchRing, touchRing]} />
      </Pressable></Animated.View>
      <Text style={styles.section}>Recent orders</Text><View style={styles.viewAll}><Text style={styles.viewAllText}>View all</Text></View>
      <View style={styles.list} pointerEvents="none">{IDS.map(i => <OrderRow key={i} j={i + 1} frame={frame} origin={labelOrigin} />)}</View>
      <View collapsable={false} style={StyleSheet.absoluteFill} pointerEvents="none"><Animated.View style={[styles.die, dieStyle]}>
        <Animated.Image source={require('./assets/ps-die-base.png')} style={styles.dieBase} />
        <Animated.View style={[styles.dieHeat, dieHeat]} />
        <Animated.View style={[styles.dieGlow, dieGlow]} />
        <Animated.Image source={require('./assets/ps-die-knob.png')} style={[styles.dieKnob, knobStyle]} />
      </Animated.View></View>
      <Animated.Text pointerEvents="none" style={[styles.flyingTag, flyTagStyle]}>+1 stamp</Animated.Text>
    </Animated.View></View>}
    {fontsOK && <View style={[styles.tabs, { left: dx + 16 * S, width: 370 * S, bottom: tabsBottom, height: 64 * S }]}>
      {(['Home', 'Card', 'Rewards', 'Account'] as const).map((label, i) => <View key={label} accessibilityRole="tab" accessibilityState={{ selected: i === 1 }} style={[styles.tab, i === 1 && styles.tabSelected]}><Icon name={(['home', 'card', 'gift', 'person'] as const)[i]} size={21 * S} /><Text style={[styles.tabLabel, { fontSize: 13 * Math.min(1, S) }]}>{label}</Text></View>)}
    </View>}
  </View>;
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  title: { position: 'absolute', left: 20, top: 63, fontFamily: FONT.serif, fontSize: 33, letterSpacing: -0.5, color: C.ink },
  cardAmbient: { position: 'absolute', left: CARD.x, top: CARD.y, width: CARD.w, height: CARD.h, borderRadius: CARD.r, backgroundColor: '#31200C', shadowColor: C.cardShadow, shadowOffset: { width: 0, height: 18 }, shadowRadius: 28, shadowOpacity: 0.18 },
  cardContact: { flex: 1, borderRadius: CARD.r, backgroundColor: '#31200C', shadowColor: C.cardShadow, shadowOffset: { width: 0, height: 2 }, shadowRadius: 4, shadowOpacity: 0.3 },
  card: { position: 'absolute', left: CARD.x, top: CARD.y, width: CARD.w, height: CARD.h, borderRadius: CARD.r, borderCurve: 'continuous' },
  cardLabel: { position: 'absolute', left: 20, top: 26, fontFamily: FONT.semibold, fontSize: 13, letterSpacing: 1.8, color: C.stampLabel },
  counterWindow: { position: 'absolute', right: 14, top: 12, width: 84, height: 40, overflow: 'hidden' },
  counter: { position: 'absolute', right: 6, top: 2, lineHeight: 36, fontFamily: FONT.serif, fontSize: 29, fontVariant: ['tabular-nums', 'lining-nums'] },
  wellNumber: { position: 'absolute', width: 30, textAlign: 'center', fontFamily: FONT.serif, fontSize: 16, color: '#8A6A40' },
  sentenceLine: { position: 'absolute', left: 20, top: 286, height: 24, flexDirection: 'row', alignItems: 'center' },
  numberWindow: { width: 14, height: 20, overflow: 'hidden' },
  sentenceDigit: { position: 'absolute', fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: C.sentence },
  sentenceWords: { position: 'absolute', top: -10, fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: C.sentence },
  complete: { position: 'absolute', left: 20, right: 18, top: 288, fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: C.sentence },
  scanShadow: { position: 'absolute', left: 20, top: 478, width: 362, height: 56, borderRadius: 18, backgroundColor: C.button, shadowColor: C.buttonShadow, shadowOffset: { width: 0, height: 6 }, shadowOpacity: 0.15, shadowRadius: 8 },
  scan: { flex: 1, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10, borderRadius: 18, overflow: 'hidden', borderCurve: 'continuous', borderTopWidth: 1, borderColor: '#614A2D' },
  scanText: { position: 'absolute', width: 300, textAlign: 'center', fontFamily: FONT.semibold, fontSize: 16, lineHeight: 24, color: C.buttonText },
  buttonLabelWindow: { width: 300, height: 24, overflow: 'hidden' },
  touchRing: { position: 'absolute', width: 30, height: 30, borderRadius: 15, borderWidth: 2, borderColor: 'white', left: 166, top: 13 },
  section: { position: 'absolute', left: 20, top: 555, fontFamily: FONT.serif, fontSize: 26, color: C.ink, letterSpacing: -0.3 },
  viewAll: { position: 'absolute', right: 20, top: 550, height: 48, justifyContent: 'center' },
  viewAllText: { fontFamily: FONT.semibold, fontSize: 14, color: '#91622B' },
  list: { position: 'absolute', left: 20, top: LIST.top, width: LIST.w, height: 183, overflow: 'hidden' },
  order: { position: 'absolute', left: 0, top: 0, width: LIST.w, height: LIST.rowH, borderRadius: 20, borderCurve: 'continuous', backgroundColor: C.paperRow, borderColor: C.hairline, borderWidth: 1 },
  orderDisc: { position: 'absolute', left: 12, top: 11, width: 44, height: 44, borderRadius: 22, backgroundColor: C.disc, alignItems: 'center', justifyContent: 'center' },
  orderName: { position: 'absolute', left: 70, right: 86, top: 11, fontFamily: FONT.semibold, fontSize: 16, color: C.ink },
  orderTime: { position: 'absolute', left: 70, top: 37, fontFamily: FONT.regular, fontSize: 13, color: '#817361' },
  orderPrice: { position: 'absolute', right: 16, top: 11, fontFamily: FONT.bold, fontSize: 16, fontVariant: ['tabular-nums'], color: C.ink },
  orderTag: { position: 'absolute', right: LABEL.right, top: 37, fontFamily: FONT.semibold, fontSize: 13, lineHeight: 16, color: '#91622B' },
  die: { position: 'absolute', width: 110, height: 110, top: 0, left: 0, shadowColor: C.shadow, backgroundColor: '#93621F', borderRadius: 55 },
  dieBase: { position: 'absolute', width: 110, height: 110, resizeMode: 'contain' },
  dieKnob: { position: 'absolute', left: 23.1, top: 23.1, width: 63.8, height: 63.8, resizeMode: 'contain' },
  dieGlow: { position: 'absolute', width: 108, height: 108, left: 1, top: 1, borderRadius: 54, borderWidth: 2, borderColor: '#FF9A3C', shadowColor: '#FF8A24', shadowRadius: 16, shadowOpacity: 1, shadowOffset: { width: 0, height: 0 } },
  dieHeat: { position: 'absolute', width: 110, height: 110, borderRadius: 55, backgroundColor: '#FF6A10' },
  flyingTag: { position: 'absolute', width: 80, textAlign: 'center', left: 0, top: 0, fontFamily: FONT.semibold, fontSize: 13, lineHeight: 18, color: '#FFF1DC', textShadowColor: '#2A1B0C', textShadowRadius: 3, textShadowOffset: { width: 0, height: 1 } },
  tabs: { position: 'absolute', flexDirection: 'row', alignItems: 'center', padding: 8, backgroundColor: C.paperRow, borderRadius: 32, borderColor: C.hairline, borderWidth: 1, shadowColor: C.cardShadow, shadowOffset: { width: 0, height: 6 }, shadowRadius: 18, shadowOpacity: 0.12 },
  tab: { flex: 1, height: '100%', alignItems: 'center', justifyContent: 'center', gap: 3, borderRadius: 24 },
  tabSelected: { backgroundColor: C.hairline }, tabLabel: { fontFamily: FONT.medium, color: C.ink, fontSize: 13 },
});
