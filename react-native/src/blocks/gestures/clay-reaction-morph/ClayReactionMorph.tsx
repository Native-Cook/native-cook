import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { FLY_S, frameAt, LOOP, REACTION_TINT, REACTIONS, REST_T, smooth, START_REACTION } from './crm-motion';
import { CRM_SKSL } from './crm-shader';
import { BUBBLE_R, C, FONT, heroOf, LONG_PRESS_MS, MESSAGES, PAGE_LUMA, SEAT_R, SEAT_S, STEP, TARGET, TRAY_H } from './crm-theme';

/*
 Clay Reaction Morph — hold a message and its reaction lifts out of the bubble as a big piece of clay.

 The screen: a chat (header with the contact, four bubbles, the composer). The third bubble carries a
 small clay ❤️ on its corner.

 Signature: hold that bubble and the chat dims; the little clay heart lifts out of its seat, grows to
 more than half the screen's width and hangs above the bubble with a tray of the four reactions under
 it. Slide the finger and the clay melts from one reaction into the next — a blue thumbs-up, the red
 heart, a laughing face with tears, a surprised face — following the finger 1:1, holding wherever it
 stops, leaning with the finger's speed; a tick at each reaction. Let go and the chosen reaction flies
 home along the same arc, shrinking into the seat, and lands with a squash. While it is big each one
 is alive: the thumb waves, the heart beats, Haha shakes, Wow breathes.

 autoPlay: LOOP = 11 s (two holds: Love → Haha → Wow → Like → Haha, let go; Haha → Love, let go);
 frame 11 s equals frame 0. A touch takes over (anything in the air flies home); 2 s after it has
 landed the badge melts back to Love and the loop starts again.
 Reduced motion: no loop; the reaction lifts and flies home without overshoot, squash, lean or the
 expressions' own motion.
*/

const effect = Skia.RuntimeEffect.Make(CRM_SKSL);
if (!effect) throw new Error('Clay Reaction Morph: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const HEADER_H = 56;
const STOPS = [0, 1, 2, 3];
const rgba = (hex: string, a: number) => `rgba(${parseInt(hex.slice(1, 3), 16)},${parseInt(hex.slice(3, 5), 16)},${parseInt(hex.slice(5, 7), 16)},${a})`;
const PILL_FILL = REACTION_TINT.map((c) => rgba(c, 0.24));
const PILL_LINE = REACTION_TINT.map((c) => rgba(c, 0.6));

function buzz(kind: 'tick' | 'lift' | 'land') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'lift' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Rect = { x: number; y: number; w: number; h: number };

type Props = { autoPlay?: boolean };

export function ClayReactionMorph({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [width, setWidth] = useState(402);
  const [threadTop, setThreadTop] = useState(0);
  const [row, setRow] = useState({ x: 16, y: 0 });
  const [inRow, setInRow] = useState<Rect>({ x: 0, y: 14, w: 240, h: 64 });
  const [badge, setBadge] = useState(START_REACTION); // the reaction on the bubble, for VoiceOver

  const bubble: Rect = { x: row.x + inRow.x, y: threadTop + row.y + inRow.y, w: inRow.w, h: inRow.h };
  const hero = heroOf(width, bubble.y, insets.top + HEADER_H);
  const seatX = bubble.x + bubble.w - 4;
  const seatY = bubble.y + 2;

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1); // 0 the loop, 1 the finger
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0); // dev: keep the finger's state (no resume), for testing touches

  const lift = useSharedValue(0); // 0 in its seat … 1 above the bubble
  const r = useSharedValue<number>(START_REACTION); // the reaction, 0 … 3, continuous while sliding
  const hold = useSharedValue(0); // the dim
  const press = useSharedValue(0);
  const squash = useSharedValue(0);
  const lean = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerDx = useSharedValue(0);
  const dragging = useSharedValue(0);
  const flying = useSharedValue(0);
  const r0 = useSharedValue(0);
  const x0 = useSharedValue(0);

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      lift.set(s.lift);
      r.set(s.r);
      hold.set(s.hold);
      press.set(s.press);
      squash.set(s.squash);
      lean.set(s.lean);
      finger.set(s.finger);
      fingerDx.set(s.fingerDx);
      return;
    }
    // a reaction in the air or settling counts as activity: the 2 s start once it rests
    if (dragging.get() === 1 || flying.get() === 1 || lift.get() > 0.001) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      // the loop starts with Love on the bubble: melt the badge back first
      if (Math.abs(r.get() - START_REACTION) > 0.001) r.set(withTiming(START_REACTION, { duration: 600, easing: EASE_IN_OUT }));
    } else if (Math.abs(r.get() - START_REACTION) < 0.002 && Math.abs(squash.get()) < 0.002) {
      resumeAsked.set(0);
      r.set(START_REACTION);
      squash.set(0);
      lean.set(0);
      hold.set(0);
      autoStart.set(clock.get() - REST_T); // pick the loop up at its rest
      mode.set(0);
      scheduleOnRN(setBadge, START_REACTION);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__crm = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), lift: +lift.get().toFixed(3), r: +r.get().toFixed(3), hold: +hold.get().toFixed(2), sq: +squash.get().toFixed(3) }),
    };
  }, [pin, stay, mode, lift, r, hold, squash]);

  /* ------------------------------------------------------------ the finger */
  const onTick = useCallback(() => buzz('tick'), []);
  const onLift = useCallback(() => buzz('lift'), []);
  const onLand = useCallback((k: number) => {
    buzz('land');
    setBadge(k);
  }, []);

  // a tick at each reaction the finger passes
  useAnimatedReaction(
    () => Math.round(Math.max(0, Math.min(3, r.get()))),
    (now, before) => {
      if (dragging.get() === 1 && before !== null && now !== before) scheduleOnRN(onTick);
    },
  );

  const land = (k: number) => {
    'worklet';
    flying.set(0);
    if (!reduced) {
      squash.set(withSequence(withTiming(0.7, { duration: 90, easing: EASE_OUT }), withSpring(0, { duration: 650, dampingRatio: 0.35 })));
    }
    scheduleOnRN(onLand, k);
  };

  // the chosen reaction flies home along the arc it came by, finishing its morph on the way
  const sendHome = (k: number) => {
    'worklet';
    flying.set(1);
    r.set(withSpring(k, { duration: 450, dampingRatio: 1, reduceMotion: NEVER }));
    hold.set(withTiming(0, { duration: 300, easing: EASE_OUT, reduceMotion: NEVER }));
    lean.set(withSpring(0, { duration: 400, dampingRatio: 1 }));
    const span = Math.max(0.3, Math.min(1, lift.get()));
    lift.set(
      withTiming(0, { duration: (reduced ? 240 : FLY_S * 1000) * span, easing: EASE_IN_OUT, reduceMotion: NEVER }, (done) => {
        if (done) land(k);
      }),
    );
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    press.set(withTiming(0, { duration: 150 }));
    squash.set(withTiming(0, { duration: 200 }));
    if (lift.get() > 0.001) sendHome(Math.round(Math.max(0, Math.min(3, r.get()))));
    else {
      hold.set(withTiming(0, { duration: 200 }));
      lean.set(withTiming(0, { duration: 200 }));
      r.set(withTiming(Math.round(r.get()), { duration: 200 }));
    }
  };

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const holdPan = Gesture.Pan()
    .activateAfterLongPress(LONG_PRESS_MS)
    .onBegin(() => {
      takeOver();
      press.set(withTiming(1, { duration: 120, easing: EASE_OUT }));
    })
    .onStart((e) => {
      flying.set(0);
      dragging.set(1);
      r.set(r.get()); // catch it where it is
      r0.set(r.get());
      x0.set(e.absoluteX);
      lift.set(reduced ? withTiming(1, { duration: 220, easing: EASE_OUT, reduceMotion: NEVER }) : withSpring(1, { duration: 550, dampingRatio: 0.62 }));
      hold.set(withTiming(1, { duration: 200, easing: EASE_OUT, reduceMotion: NEVER }));
      press.set(withSpring(0, { duration: 350, dampingRatio: 0.7 }));
      if (!reduced) squash.set(withSequence(withTiming(-0.3, { duration: 120, easing: EASE_OUT }), withSpring(0, { duration: 500, dampingRatio: 0.5 })));
      scheduleOnRN(onLift);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let v = r0.get() + (e.absoluteX - x0.get()) / STEP;
      // a little give past the first and the last reaction
      if (v < 0) v = -0.25 * (1 - Math.exp(v / 0.25));
      if (v > 3) v = 3 + 0.25 * (1 - Math.exp(-(v - 3) / 0.25));
      r.set(v);
      if (!reduced) lean.set(withSpring(Math.max(-1, Math.min(1, e.velocityX / 1400)), { duration: 300, dampingRatio: 0.8 }));
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 150, easing: EASE_OUT }));
      if (dragging.get() === 1) {
        dragging.set(0);
        sendHome(Math.round(Math.max(0, Math.min(3, r.get()))));
      }
    });

  const choose = useCallback(
    (k: number) => {
      takeOver();
      r.set(withTiming(k, { duration: 350, easing: EASE_IN_OUT, reduceMotion: NEVER }));
      setBadge(k);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const l = lift.get();
    const lc = Math.max(0, Math.min(1, l));
    const arc = Math.sin(Math.PI * lc);
    const x = seatX + (hero.x - seatX) * l + 36 * arc;
    const y = seatY + (hero.y - seatY) * l - 24 * arc;
    const size = SEAT_S + (hero.size - SEAT_S) * l;
    const k = r.get();
    const frac = k - Math.floor(k);
    const live = reduced ? 0 : smooth(0.7, 1, l);
    const yaw = (reduced ? 0 : 0.4 * Math.sin(Math.PI * frac)) + 0.12 * live * Math.sin(clock.get() * 1.3);
    return {
      uObj: [x, y, size, 1],
      uMorph: [k, yaw, lean.get(), squash.get()],
      uLive: [clock.get(), live, smooth(0.35, 1, l), pixelRatio],
      uHole: [bubble.x, bubble.y, bubble.w, bubble.h],
      uDim: [hold.get(), BUBBLE_R, 1 - 0.03 * press.get(), PAGE_LUMA],
      uSeat: [seatX, seatY, SEAT_R, 1 - smooth(0, 0.22, l)],
    };
  });

  const bubbleStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * press.get() }] }));
  const trayStyle = useAnimatedStyle(() => ({
    opacity: smooth(0.2, 1, hold.get()),
    transform: [{ translateY: 8 * (1 - hold.get()) }],
  }));
  const pillStyle = useAnimatedStyle(() => {
    const k = Math.max(0, Math.min(3, r.get()));
    return {
      backgroundColor: interpolateColor(k, STOPS, PILL_FILL),
      borderColor: interpolateColor(k, STOPS, PILL_LINE),
      transform: [{ translateX: k * STEP }],
    };
  });
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: 0.85 * finger.get(),
    transform: [
      { translateX: bubble.x + bubble.w * 0.42 + fingerDx.get() * STEP - 22 },
      { translateY: bubble.y + bubble.h / 2 - 22 },
      { scale: 1 - 0.12 * press.get() },
    ],
  }));

  const onBubble = useCallback((e: LayoutChangeEvent) => {
    const { x, y, width: w, height: h } = e.nativeEvent.layout;
    setInRow((p) => (p.x === x && p.y === y && p.w === w && p.h === h ? p : { x, y, w, h }));
  }, []);

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    setWidth((v) => (v === w ? v : w));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  const trayW = STEP * REACTIONS.length;
  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />

        {/* the contact */}
        <View style={[styles.header, { paddingTop: insets.top, height: insets.top + HEADER_H }]}>
          <Svg width={12} height={20} viewBox="0 0 12 20" accessibilityLabel="Back" accessibilityRole="button">
            <SvgPath d="M10 2L2 10l8 8" stroke={C.head} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
          <View style={styles.avatar}>
            <Text style={styles.avatarInk}>M</Text>
          </View>
          <View style={styles.who}>
            <Text style={styles.name} accessibilityRole="header">Mai Tran</Text>
            <Text style={styles.status}>Active now</Text>
          </View>
        </View>

        {/* the thread */}
        <View style={[styles.thread, { top: insets.top + HEADER_H, bottom: Math.max(insets.bottom, 12) + 60 }]} onLayout={(e) => setThreadTop(e.nativeEvent.layout.y)}>
          <Text style={styles.stamp}>Today 9:14 AM</Text>
          {MESSAGES.map((m, i) => {
            const isTarget = i === TARGET;
            const body = (
              <Animated.View
                style={[styles.bubble, m.from === 'in' ? styles.bubbleIn : styles.bubbleOut, isTarget ? bubbleStyle : null]}
                onLayout={isTarget ? onBubble : undefined}
                accessible
                accessibilityLabel={`${m.from === 'in' ? 'Mai' : 'You'}: ${m.text}`}
                accessibilityRole={isTarget ? 'adjustable' : 'text'}
                accessibilityValue={isTarget ? { text: `Your reaction: ${REACTIONS[badge]}` } : undefined}
                accessibilityHint={isTarget ? 'Swipe up or down to change your reaction' : undefined}
                accessibilityActions={isTarget ? [{ name: 'increment' }, { name: 'decrement' }] : undefined}
                onAccessibilityAction={isTarget ? (e) => choose(Math.max(0, Math.min(3, badge + (e.nativeEvent.actionName === 'increment' ? 1 : -1)))) : undefined}
              >
                <Text style={[styles.text, { color: m.from === 'in' ? C.inInk : C.outInk }]}>{m.text}</Text>
              </Animated.View>
            );
            return (
              <View
                key={i}
                style={[styles.row, m.from === 'out' ? styles.rowOut : null, isTarget ? styles.rowTarget : null]}
                onLayout={isTarget ? (e) => setRow({ x: e.nativeEvent.layout.x, y: e.nativeEvent.layout.y }) : undefined}
              >
                {isTarget ? <GestureDetector gesture={holdPan}>{body}</GestureDetector> : body}
              </View>
            );
          })}
        </View>

        {/* the composer */}
        <View style={[styles.composer, { bottom: Math.max(insets.bottom, 12) }]}>
          <View style={styles.plus} accessible accessibilityRole="button" accessibilityLabel="Attach">
            <Svg width={16} height={16} viewBox="0 0 16 16">
              <SvgPath d="M8 2v12M2 8h12" stroke={C.head} strokeWidth={2} strokeLinecap="round" />
            </Svg>
          </View>
          <View style={styles.field}>
            <Text style={styles.placeholder}>Message</Text>
          </View>
        </View>

        {/* the dim, the halo, the seat and the clay */}
        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        {/* the tray of reactions */}
        <Animated.View
          pointerEvents="none"
          style={[styles.tray, trayStyle, { left: (width - trayW) / 2 - 4, top: hero.trayTop, width: trayW + 8 }]}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          <Animated.View style={[styles.pill, pillStyle]} />
          {REACTIONS.map((name, i) => (
            <TrayLabel key={name} name={name} index={i} r={r} />
          ))}
        </Animated.View>

        {/* the loop's finger */}
        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

function TrayLabel({ name, index, r }: { name: string; index: number; r: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(r.get() - index));
    return { color: interpolateColor(near, [0, 1], [C.mute, '#FFFFFF']) };
  });
  return (
    <View style={styles.trayItem}>
      <Animated.Text style={[styles.trayText, style]}>{name}</Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  header: { position: 'absolute', left: 0, right: 0, top: 0, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: C.fieldLine },
  avatar: { width: 36, height: 36, borderRadius: 18, backgroundColor: C.avatar, alignItems: 'center', justifyContent: 'center' },
  avatarInk: { fontFamily: FONT.semibold, fontSize: 16, color: C.avatarInk },
  who: { gap: 1 },
  name: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 21, color: C.head },
  status: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 17, color: C.mute },
  thread: { position: 'absolute', left: 0, right: 0, justifyContent: 'flex-end', paddingHorizontal: 16, paddingBottom: 12, gap: 8 },
  stamp: { alignSelf: 'center', fontFamily: FONT.medium, fontSize: 12, lineHeight: 16, color: C.mute, marginBottom: 8 },
  row: { flexDirection: 'row' },
  rowOut: { justifyContent: 'flex-end' },
  rowTarget: { paddingTop: 14 },
  bubble: { maxWidth: '74%', paddingHorizontal: 16, paddingVertical: 11, borderRadius: BUBBLE_R, borderCurve: 'continuous' },
  bubbleIn: { backgroundColor: C.inBubble },
  bubbleOut: { backgroundColor: C.outBubble },
  text: { fontFamily: FONT.regular, fontSize: 17, lineHeight: 23 },
  composer: { position: 'absolute', left: 16, right: 16, height: 44, flexDirection: 'row', alignItems: 'center', gap: 12 },
  plus: { width: 36, height: 36, borderRadius: 18, backgroundColor: C.field, alignItems: 'center', justifyContent: 'center' },
  field: { flex: 1, height: 40, borderRadius: 20, borderWidth: 1, borderColor: C.fieldLine, backgroundColor: C.field, justifyContent: 'center', paddingHorizontal: 16 },
  placeholder: { fontFamily: FONT.regular, fontSize: 16, color: C.mute },
  tray: { position: 'absolute', height: TRAY_H, borderRadius: TRAY_H / 2, backgroundColor: C.tray, borderWidth: 1, borderColor: C.trayLine, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 4 },
  pill: { position: 'absolute', left: 4, top: 4, width: STEP, height: TRAY_H - 10, borderRadius: (TRAY_H - 10) / 2, borderWidth: 1 },
  trayItem: { width: STEP, alignItems: 'center' },
  trayText: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 18 },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.4)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)', boxShadow: '0 1px 6px rgba(0,0,0,0.2)' },
});
