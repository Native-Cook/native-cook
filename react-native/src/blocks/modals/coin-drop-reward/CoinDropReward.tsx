import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, LinearGradient, Path as SvgPath, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { COOLDOWN, FLY_S, frameAt, LOOP, meltOf, PULL_FULL, REWARD, smooth, T, wobbleAt } from './cdr-motion';
import { CDR_SKSL } from './cdr-shader';
import { BONUS, C, COIN_D, COIN_S, DAYS, FONT, HERO_S, makeLayout, MISSIONS, ROW_H, STAMPED, TILE, TODAY, WALLET } from './cdr-theme';

/*
 Coin Drop Reward — the reward of a shopping app's daily missions, poured into the wallet by hand.

 The screen: Today's visit missions, two missions (a coin each), a bonus mission that opens later, the
 week's stamps, and a Points wallet in the corner.

 Signature: tap a mission and its coin pops out of the row into a white toast in the middle of the
 screen, grown to a glossy blue 3D coin with a raised P, and turns once. Pull it toward the wallet: as
 the finger drags, the coin melts into a drop of the same blue liquid, its tail trailing back toward the
 toast, holding wherever the finger stops; let go past a short way (or flick, or just tap it) and the
 drop finishes its change and is poured into the wallet along an arc, a small droplet trailing it on a
 neck that thins and lets go. The wallet wobbles like jelly, "+5 P" rises from it, a success haptic,
 and the mission cools down for 4 s. Let go too soon and the drop springs back into a coin.

 autoPlay: LOOP = 9 s (tap the first mission; the coin pops and turns; a finger pulls it a third of the
 way, holds, lets go; it pours into the wallet; the mission counts down and comes back); frame 9 s
 equals frame 0. A touch takes over (a coin in the air carries on into the wallet if it was already
 let go, else waits in the toast); 2 s after everything rests the loop starts again.
 Reduced motion: no loop; the coin appears in the toast without the pop or the turn, and the pour is a
 short fade into the wallet.
*/

const effect = Skia.RuntimeEffect.Make(CDR_SKSL);
if (!effect) throw new Error('Coin Drop Reward: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_POUR = Easing.bezier(0.55, 0, 0.75, 0.2);
const NEVER = ReduceMotion.Never;
const AUTO_COLLECT = 3; // s a waiting reward stays in the toast before it pours by itself

function buzz(kind: 'tap' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tap') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function CoinDropReward({ autoPlay = false }: Props) {
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
  const [size, setSize] = useState({ w: 402, h: 874 });
  const L = useMemo(() => makeLayout(size.w, size.h, insets.top, insets.bottom), [size, insets.top, insets.bottom]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastT = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const stage = useSharedValue(0); // 0 rest, 1 popping, 2 waiting in the toast, 3 pulled, 4 pouring
  const sel = useSharedValue(0); // which mission's reward is out
  const out = useSharedValue(0);
  const launch = useSharedValue(0);
  const flip = useSharedValue(0);
  const pull = useSharedValue(0);
  const melt = useSharedValue(0);
  const fly = useSharedValue(0);
  const trail = useSharedValue(0);
  const toast = useSharedValue(0);
  const text = useSharedValue(0);
  const wobbleU = useSharedValue(1);
  const plus = useSharedValue(1);
  const left0 = useSharedValue(0); // cooldown left per mission (s)
  const left1 = useSharedValue(0);
  const end0 = useSharedValue(0);
  const end1 = useSharedValue(0);
  const waitStart = useSharedValue(0);
  const press = useSharedValue(0);
  const finger = useSharedValue(0);
  const onCoin = useSharedValue(0);
  const fingerPull = useSharedValue(0);
  const p0 = useSharedValue(0);

  const [rewardOut, setRewardOut] = useState(false); // the toast takes touches only while a reward is out
  useAnimatedReaction(
    () => out.get() === 1,
    (now, before) => {
      if (now !== before) scheduleOnRN(setRewardOut, now);
    },
  );

  const dir = useMemo(() => {
    const dx = L.walletIcon.x - L.toastCoin.x;
    const dy = L.walletIcon.y - L.toastCoin.y;
    const d = Math.hypot(dx, dy);
    return { x: dx / d, y: dy / d, d };
  }, [L]);

  /* ------------------------------------------------------------ the reward (declared before the clock: worklets capture what is above them) */
  const onDone = useCallback(() => buzz('done'), []);
  const onTap = useCallback(() => buzz('tap'), []);

  function absorb() {
    'worklet';
    stage.set(0);
    out.set(0);
    if (sel.get() === 0) end0.set(clock.get() + COOLDOWN);
    else end1.set(clock.get() + COOLDOWN);
    wobbleU.set(0);
    wobbleU.set(withTiming(1, { duration: 700, easing: Easing.linear }));
    plus.set(0);
    plus.set(withTiming(1, { duration: 900, easing: Easing.linear, reduceMotion: NEVER }));
    launch.set(0);
    flip.set(0);
    pull.set(0);
    melt.set(0);
    fly.set(0);
    trail.set(0);
    scheduleOnRN(onDone);
  }

  // the drop finishes its change and is poured into the wallet
  function collect() {
    'worklet';
    if (stage.get() === 4) return;
    stage.set(4);
    const rest = Math.max(0.25, 1 - fly.get());
    melt.set(withTiming(1, { duration: 300, easing: EASE_OUT, reduceMotion: NEVER }));
    trail.set(withTiming(1, { duration: FLY_S * 900 * rest, easing: EASE_OUT, reduceMotion: NEVER }));
    toast.set(withTiming(0, { duration: 350, easing: EASE_OUT, reduceMotion: NEVER }));
    text.set(withTiming(0, { duration: 250, easing: EASE_OUT, reduceMotion: NEVER }));
    fly.set(
      withTiming(1, { duration: (reduced ? 300 : FLY_S * 1000) * rest, easing: EASE_POUR, reduceMotion: NEVER }, (done) => {
        if (done) absorb();
      }),
    );
  }

  function settleInToast() {
    'worklet';
    stage.set(2);
    waitStart.set(clock.get());
    pull.set(withSpring(0, { duration: 500, dampingRatio: 0.6 }));
    melt.set(withSpring(0, { duration: 500, dampingRatio: 0.8 }));
  }

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    const t = lastT.get();
    finger.set(withTiming(0, { duration: 150 }));
    press.set(withTiming(0, { duration: 150 }));
    const f = frameAt(t);
    if (f.cooldown > 0) end0.set(clock.get() + f.cooldown);
    if (t >= T.launch && t < T.absorb) {
      sel.set(0);
      out.set(1);
      launch.set(withTiming(1, { duration: 200 }));
      flip.set(withTiming(1, { duration: 250 }));
      if (t >= T.release) {
        stage.set(3);
        collect();
      } else settleInToast();
    } else stage.set(0);
  }

  function open(i: number) {
    'worklet';
    if (stage.get() !== 0) return;
    if ((i === 0 ? left0.get() : left1.get()) > 0) return;
    sel.set(i);
    stage.set(1);
    out.set(1);
    pull.set(0);
    melt.set(0);
    fly.set(0);
    trail.set(0);
    flip.set(0);
    launch.set(0);
    if (reduced) {
      launch.set(1);
      flip.set(1);
      toast.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      text.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      stage.set(2);
      waitStart.set(clock.get());
      return;
    }
    launch.set(withSpring(1, { duration: 600, dampingRatio: 0.62 }));
    toast.set(withTiming(1, { duration: 350, easing: EASE_OUT }));
    text.set(withDelay(300, withTiming(1, { duration: 300, easing: EASE_OUT })));
    flip.set(
      withDelay(
        650,
        withTiming(1, { duration: 800, easing: EASE_IN_OUT }, (done) => {
          if (done && stage.get() === 1) {
            stage.set(2);
            waitStart.set(clock.get());
          }
        }),
      ),
    );
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      lastT.set(t);
      const s = frameAt(t);
      sel.set(0);
      out.set(t >= T.launch && t < T.absorb ? 1 : 0);
      launch.set(s.launch);
      flip.set(s.flip);
      pull.set(s.pull);
      melt.set(s.melt);
      fly.set(s.fly);
      trail.set(s.trail);
      toast.set(s.toast);
      text.set(s.text);
      wobbleU.set(s.wobbleU);
      plus.set(s.plus);
      left0.set(s.cooldown);
      left1.set(0);
      press.set(s.press);
      finger.set(s.finger);
      onCoin.set(s.onCoin);
      fingerPull.set(s.fingerPull);
      return;
    }
    left0.set(Math.max(0, end0.get() - clock.get()));
    left1.set(Math.max(0, end1.get() - clock.get()));
    if (stage.get() === 2 && clock.get() - waitStart.get() > AUTO_COLLECT) collect();
    const busy = stage.get() !== 0 || left0.get() > 0 || left1.get() > 0 || wobbleU.get() < 1 || plus.get() < 1;
    if (busy) lastTouch.set(clock.get());
    if (auto && stay.get() === 0 && clock.get() - lastTouch.get() > 2) {
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__cdr = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), stage: stage.get(), launch: +launch.get().toFixed(2), pull: +pull.get().toFixed(2), melt: +melt.get().toFixed(2), fly: +fly.get().toFixed(2), left0: +left0.get().toFixed(1) }),
    };
  }, [pin, stay, mode, stage, launch, pull, melt, fly, left0]);

  /* ------------------------------------------------------------ touch */
  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const tapMission = (i: number) => {
    takeOver();
    if (stage.get() !== 0 || (i === 0 ? left0.get() : left1.get()) > 0) return;
    onTap();
    open(i);
  };

  // pull the coin toward the wallet; it melts into a drop as it goes
  const coinPan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (stage.get() !== 1 && stage.get() !== 2) return;
      stage.set(3);
      flip.set(withTiming(1, { duration: 200 }));
      pull.set(pull.get());
      p0.set(pull.get());
    })
    .onUpdate((e) => {
      if (stage.get() !== 3) return;
      lastTouch.set(clock.get());
      let v = p0.get() + (e.translationX * dir.x + e.translationY * dir.y) / dir.d;
      if (v < 0) v = -0.04 * (1 - Math.exp(v / 0.04));
      if (v > 0.45) v = 0.45 + 0.1 * (1 - Math.exp(-(v - 0.45) / 0.1));
      pull.set(v);
      melt.set(meltOf(v));
    })
    .onFinalize((e) => {
      if (stage.get() !== 3) return;
      lastTouch.set(clock.get());
      const speed = (e.velocityX * dir.x + e.velocityY * dir.y) / dir.d;
      if (pull.get() > 0.16 || speed > 0.9) collect();
      else settleInToast();
    });
  const coinTap = Gesture.Tap().onEnd(() => {
    if (stage.get() === 1 || stage.get() === 2) collect();
  });
  const coinGesture = Gesture.Exclusive(coinPan, coinTap);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const i = sel.get();
    const P0 = L.rowCoin[i];
    const l = launch.get();
    const lc = Math.max(0, Math.min(1, l));
    const bx = P0.x + (L.toastCoin.x - P0.x) * lc;
    const by = P0.y + (L.toastCoin.y - P0.y) * lc - 30 * Math.sin(Math.PI * lc);
    const k = pull.get();
    const f = fly.get();
    const px = bx + dir.x * dir.d * k;
    const py = by + dir.y * dir.d * k;
    const x = px + (L.walletIcon.x - px) * f;
    const y = py + (L.walletIcon.y - py) * f - 46 * Math.sin(Math.PI * f);
    const size = (COIN_S + (HERO_S - COIN_S) * l) * (1 - 0.82 * f);
    // the tail trails back toward the toast
    const a = Math.atan2(dir.y, -dir.x);
    const stretch = 0.6 * Math.max(0, Math.min(1, k / PULL_FULL)) * (1 - f) + 0.7 * Math.sin(Math.PI * f);
    return {
      uObj: [x, y, size, out.get() * (1 - smooth(0.88, 1, f))],
      uShape: [melt.get(), 2 * Math.PI * flip.get(), Math.PI / 2 - a, stretch],
      uLive: [trail.get() * (1 - smooth(0.85, 1, f)), pixelRatio, 0, 0],
    };
  });

  const tileStyle = useAnimatedStyle(() => ({
    opacity: toast.get(),
    transform: [{ scale: 0.86 + 0.14 * toast.get() }],
  }));
  const tileText = useAnimatedStyle(() => ({ opacity: text.get(), transform: [{ translateY: 6 * (1 - text.get()) }] }));
  const walletStyle = useAnimatedStyle(() => {
    const w = wobbleAt(wobbleU.get());
    return { transform: [{ scaleX: 1 + 0.1 * w }, { scaleY: 1 - 0.08 * w }] };
  });
  const plusStyle = useAnimatedStyle(() => ({
    opacity: Math.sin(Math.PI * plus.get()),
    transform: [{ translateX: -10 * plus.get() }],
  }));
  const fingerStyle = useAnimatedStyle(() => {
    const c = onCoin.get() === 1;
    const x = c ? L.toastCoin.x + dir.x * dir.d * fingerPull.get() : L.w * 0.62;
    const y = c ? L.toastCoin.y + dir.y * dir.d * fingerPull.get() : L.rowCoin[0].y;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((s) => (s.w === width && s.h === height ? s : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="dark" />

        {/* header */}
        <View style={[styles.header, { top: L.titleTop, left: L.side, right: L.side }]}>
          <Text style={styles.title} numberOfLines={1} accessibilityRole="header">Today&apos;s visit missions</Text>
          <Text style={styles.sub} numberOfLines={2}>Browse a product each day to earn points.</Text>
        </View>
        <Animated.View style={[styles.wallet, walletStyle, { left: L.walletLeft, top: L.walletTop }]} accessible accessibilityLabel="Points wallet">
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <SvgPath d="M3 6.5A2.5 2.5 0 015.5 4h9A1.5 1.5 0 0116 5.5V7h.5A1.5 1.5 0 0118 8.5v6a2 2 0 01-2 2H5.5A2.5 2.5 0 013 14z" fill={C.blue} />
            <Circle cx={14.5} cy={11.5} r={1.4} fill="#FFFFFF" />
          </Svg>
          <Text style={styles.walletText}>Points</Text>
        </Animated.View>
        <Animated.Text style={[styles.plus, plusStyle, { left: L.walletLeft - 44, top: L.walletTop + 10 }]} accessibilityLiveRegion="polite">
          +{REWARD} P
        </Animated.Text>

        {/* missions */}
        <Text style={[styles.section, { top: L.sectionTop, left: L.side }]}>{MISSIONS.length} missions</Text>
        {MISSIONS.map((m, i) => (
          <MissionRow key={m.title} index={i} title={m.title} sub={m.sub} top={L.rowsTop + i * ROW_H} side={L.side} left={i === 0 ? left0 : left1} out={out} sel={sel} onPress={() => tapMission(i)} />
        ))}
        <Text style={[styles.section, { top: L.bonusLabelTop, left: L.side }]}>Bonus mission</Text>
        <View style={[styles.row, { top: L.bonusTop, left: L.side, right: L.side }]} accessible accessibilityLabel={`${BONUS.title}, ${BONUS.when}`} accessibilityState={{ disabled: true }}>
          <View style={styles.dotted}>
            <Text style={styles.dots}>···</Text>
          </View>
          <View style={styles.rowText}>
            <Text style={styles.when}>{BONUS.when}</Text>
            <Text style={styles.rowTitle}>{BONUS.title}</Text>
          </View>
        </View>

        {/* the week */}
        <View style={[styles.week, { top: L.daysTop, left: L.side, right: L.side }]} accessible accessibilityLabel={`This week: ${STAMPED.length} days collected`}>
          {DAYS.map((d, i) => (
            <View key={d} style={styles.day}>
              <Text style={[styles.dayText, i === TODAY ? { color: C.accent } : null]}>{d}</Text>
              <View style={[styles.stamp, i === TODAY ? styles.stampToday : null]}>{STAMPED.includes(i) ? <CoinIcon size={26} /> : null}</View>
            </View>
          ))}
        </View>

        {/* the toast */}
        <Animated.View pointerEvents="none" style={[styles.tile, tileStyle, { left: L.tileCx - TILE / 2, top: L.tileCy - TILE / 2 }]}>
          <Animated.Text style={[styles.tileText, tileText]}>You got {REWARD} points</Animated.Text>
        </Animated.View>
        <GestureDetector gesture={coinGesture}>
          <View pointerEvents={rewardOut ? 'auto' : 'none'} style={[styles.coinHit, { left: L.tileCx - TILE / 2, top: L.tileCy - TILE / 2 }]} accessible={rewardOut} accessibilityRole="button" accessibilityLabel={`Collect ${REWARD} points`} onAccessibilityTap={() => collect()} />
        </GestureDetector>

        {/* the coin, the drop */}
        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

function CoinIcon({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 44 44">
      <Defs>
        <LinearGradient id="cdrCoin" x1="0.2" y1="0" x2="0.8" y2="1">
          <Stop offset="0" stopColor="#6FA4FF" />
          <Stop offset="1" stopColor="#1E55F0" />
        </LinearGradient>
      </Defs>
      <Circle cx={22} cy={22} r={21} fill="url(#cdrCoin)" />
      <Circle cx={22} cy={22} r={16} fill="none" stroke="rgba(255,255,255,0.35)" strokeWidth={1.5} />
      <SvgPath d="M17.5 31V13h6a5.5 5.5 0 010 11h-6" fill="none" stroke="#FFFFFF" strokeWidth={3.6} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

type RowProps = {
  index: number;
  title: string;
  sub: string;
  top: number;
  side: number;
  left: SharedValue<number>;
  out: SharedValue<number>;
  sel: SharedValue<number>;
  onPress: () => void;
};

function MissionRow({ index, title, sub, top, side, left, out, sel, onPress }: RowProps) {
  const [secs, setSecs] = useState(0);
  useAnimatedReaction(
    () => Math.ceil(left.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSecs, now);
    },
  );
  const coinStyle = useAnimatedStyle(() => {
    const hidden = out.get() === 1 && sel.get() === index;
    const l = left.get();
    // dim while it cools down: it fades in as the drop lands, and back to full in the last quarter second
    return { opacity: hidden ? 0 : (0.35 + 0.65 * (1 - smooth(0, 0.25, l))) * (1 - smooth(COOLDOWN - 0.3, COOLDOWN, l)) };
  });
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.row, { top, left: side, right: side }, pressed ? styles.rowPressed : null]}
      accessibilityRole="button"
      accessibilityLabel={`${title}, ${sub}`}
      accessibilityState={{ disabled: secs > 0 }}
      accessibilityHint={secs > 0 ? `Again in ${secs} seconds` : 'Collects the reward'}
    >
      <Animated.View style={coinStyle}>
        <CoinIcon size={COIN_D} />
      </Animated.View>
      <View style={styles.rowText}>
        <Text style={styles.rowTitle} numberOfLines={1}>{title}</Text>
        <Text style={styles.rowSub} numberOfLines={1}>{sub}</Text>
      </View>
      {secs > 0 ? (
        <View style={styles.cool}>
          <Text style={styles.coolText}>Again in 0:0{secs}</Text>
        </View>
      ) : (
        <Svg width={8} height={13} viewBox="0 0 8 13">
          <SvgPath d="M1.5 1.5l5 5-5 5" stroke="#B5B6C2" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </Svg>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  header: { position: 'absolute', gap: 4 },
  title: { fontFamily: FONT.semibold, fontSize: 22, lineHeight: 30, color: C.ink },
  sub: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: C.mute },
  wallet: { position: 'absolute', width: WALLET.w, height: WALLET.h, borderRadius: WALLET.h / 2, backgroundColor: C.tile, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, gap: 8, boxShadow: '0 4px 14px rgba(40,40,90,0.10)' },
  walletText: { fontFamily: FONT.semibold, fontSize: 14, color: C.ink },
  plus: { position: 'absolute', fontFamily: FONT.semibold, fontSize: 14, color: C.blue },
  section: { position: 'absolute', fontFamily: FONT.medium, fontSize: 14, lineHeight: 18, color: C.accent },
  row: { position: 'absolute', height: ROW_H, flexDirection: 'row', alignItems: 'center', gap: 14, borderRadius: 16 },
  rowPressed: { backgroundColor: '#F2F1FA' },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 22, color: C.ink },
  rowSub: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 18, color: C.mute },
  when: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, color: C.blue },
  dotted: { width: COIN_D, height: COIN_D, borderRadius: COIN_D / 2, backgroundColor: C.stamp, alignItems: 'center', justifyContent: 'center' },
  dots: { fontFamily: FONT.semibold, fontSize: 16, color: C.mute, letterSpacing: 1 },
  cool: { height: 28, paddingHorizontal: 10, borderRadius: 14, backgroundColor: C.blueSoft, justifyContent: 'center' },
  coolText: { fontFamily: FONT.medium, fontSize: 12, color: C.blue, fontVariant: ['tabular-nums'] },
  week: { position: 'absolute', height: 60, flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: C.line, paddingTop: 12 },
  day: { alignItems: 'center', gap: 6, width: 40 },
  dayText: { fontFamily: FONT.medium, fontSize: 12, color: C.mute },
  stamp: { width: 30, height: 30, borderRadius: 15, backgroundColor: C.stamp, alignItems: 'center', justifyContent: 'center' },
  stampToday: { borderWidth: 1.5, borderColor: C.accent, backgroundColor: C.page },
  tile: { position: 'absolute', width: TILE, height: TILE, borderRadius: 36, borderCurve: 'continuous', backgroundColor: C.tile, alignItems: 'center', justifyContent: 'flex-end', paddingBottom: 28, boxShadow: '0 18px 50px rgba(50,60,140,0.16)' },
  tileText: { fontFamily: FONT.semibold, fontSize: 16, color: C.blue },
  coinHit: { position: 'absolute', width: TILE, height: TILE },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(30,40,80,0.18)', borderWidth: 1.5, borderColor: 'rgba(30,40,80,0.35)' },
});
