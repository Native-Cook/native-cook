import { Canvas, Fill, ImageShader, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { bakeMessage, MESSAGE } from './smokeArt';
import {
  HEADER_H,
  INPUT_H,
  makeSim,
  makeSmokeGeo,
  MODE_LIVE,
  readable,
  type SmokeGeo,
  type SmokeSim,
  simDrag,
  simDragEnd,
  simOpen,
  simStep,
  simTouch,
  smokeUniforms,
} from './smokeMotion';
import { SMOKE_SKSL } from './smokeShader';

/*
 Smoke Signal Toast — "Campfire", an outdoor group's chat, where a notification arrives as smoke.

 Look: a night sky with stars over a pine treeline, a campfire on the ground at the bottom (flames,
 crossed logs, a ring of stones, sparks, firelight), the group's last messages under the header.
 The fire puffs a cloud that rises and condenses into the message "Dinner's ready at the lake"
 from Kai, which holds readable, then a gust tears it away to the right. One full-screen shader
 (smokeShader.ts) with the message baked once as two masks (smokeArt.ts); views for the chat.

 autoPlay: LOOP = 9 s (script in smokeMotion.ts).
 Touch: tap the message to open it (a card with quick replies; the smoke drifts up out of its
 way); swipe it to blow it away in the swipe's direction (past a third of the way, or a fling,
 it goes; otherwise it gathers back). 2 s after the last touch the smoke clears (0.9 s) and the
 loop restarts. Reduced motion: the message shown still and readable, no loop.
*/

type Props = {
  /** Plays the 9 s puff-form-disperse loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(SMOKE_SKSL);
const INK = '#EDE7DF';
const SOFT = 'rgba(237,231,223,0.6)';
const EMBER = '#F08A3C';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const THREAD = [
  { who: 'Mira', text: 'Tents are up by the pines', time: '7:58 PM', mine: false, tint: '#7FA58A' },
  { who: 'You', text: 'Bringing the kettle and cocoa', time: '8:05 PM', mine: true, tint: EMBER },
  { who: 'Theo', text: 'Who has the lantern?', time: '8:21 PM', mine: false, tint: '#8C9AC8' },
];

export function SmokeSignalToast({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [open, setOpen] = useState(false);
  const geo = useMemo<SmokeGeo | null>(
    () => (size ? makeSmokeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const art = useMemo(() => (size && geo ? bakeMessage(size.width, size.height, geo.toastY) : null), [size, geo]);
  const sim = useSharedValue<SmokeSim>(makeSim());
  const tick = useSharedValue(0);
  const card = useSharedValue(0);

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, sim]);

  useEffect(() => {
    card.set(withTiming(open ? 1 : 0, { duration: open ? 320 : 220, easing: EASE_OUT }));
  }, [open, card]);

  useFrameCallback((frame) => {
    if (!geo) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    simStep(sim.get(), dt, geo, auto, reduced);
    tick.set(tick.get() + 1);
  });

  const light = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // The card follows the simulation (the loop's return closes it).
  useAnimatedReaction(
    () => {
      tick.get();
      return sim.get().open;
    },
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setOpen, now);
    },
  );
  // A light tick when a swipe has blown the message away.
  useAnimatedReaction(
    () => {
      tick.get();
      const s = sim.get();
      return s.mode === MODE_LIVE && !s.open && s.frame.wind[2] > 0.9;
    },
    (now, before) => {
      if (now && before === false) scheduleOnRN(light);
    },
  );

  const swipe = Gesture.Pan()
    .minDistance(8)
    .onBegin(() => {
      simTouch(sim.get());
    })
    .onUpdate((e) => {
      const s = sim.get();
      if (s.open || s.frame.text[3] < 0.3) return;
      simDrag(s, e.translationX, e.translationY);
    })
    .onEnd((e) => {
      simDragEnd(sim.get(), e.velocityX, e.velocityY);
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s.dragging) simDragEnd(s, 0, 0);
    });
  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd(() => {
      const s = sim.get();
      if (!readable(s) || s.open) return;
      simOpen(s, true);
      scheduleOnRN(light);
    });
  const toastGesture = Gesture.Exclusive(swipe, tap);

  const close = () => {
    light();
    setOpen(false);
    // The simulation lives on the UI thread: change it there.
    scheduleOnUI(() => {
      simOpen(sim.get(), false);
    });
  };

  const uniforms = useDerivedValue(() => {
    tick.get();
    return smokeUniforms(sim.get().frame, geo ?? makeSmokeGeo(1, 1, 0, 0));
  });

  const cardStyle = useAnimatedStyle(() => ({
    opacity: card.get(),
    transform: [{ translateY: (1 - card.get()) * 16 }, { scale: 0.96 + 0.04 * card.get() }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;

  return (
    <View style={styles.root} onLayout={onLayout}>
      {size && EFFECT && art ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms}>
              <ImageShader image={art.sharp} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
              <ImageShader image={art.soft} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
            </Shader>
          </Fill>
        </Canvas>
      ) : null}

      <View style={[styles.header, { top: insets.top + 4, height: HEADER_H }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back" style={styles.back}>
          <Svg width={12} height={20} viewBox="0 0 12 20">
            <Path d="M9.5 2.5 L2.5 10 L9.5 17.5" stroke={INK} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </Pressable>
        <View style={styles.avatars}>
          {['K', 'M', 'T'].map((l, i) => (
            <View key={l} style={[styles.avatar, { marginLeft: i === 0 ? 0 : -10, backgroundColor: ['#C9733A', '#5F8A6C', '#6573A6'][i] }]}>
              <Text style={styles.avatarText}>{l}</Text>
            </View>
          ))}
        </View>
        <View style={styles.headText}>
          <Text style={styles.group}>Lakeside Crew</Text>
          <Text style={styles.members}>6 around the fire · Pine Hollow</Text>
        </View>
      </View>

      <View pointerEvents="none" style={[styles.thread, { top: insets.top + HEADER_H + 16 }]}>
        <Text style={styles.day}>TONIGHT</Text>
        {THREAD.map((m) => (
          <View key={m.text} style={[styles.msgRow, m.mine && styles.msgRowMine]}>
            <View style={[styles.bubble, m.mine ? styles.bubbleMine : styles.bubbleOther]}>
              {!m.mine ? <Text style={[styles.who, { color: m.tint }]}>{m.who}</Text> : null}
              <Text style={[styles.msg, m.mine && styles.msgMine]}>{m.text}</Text>
            </View>
            <Text style={styles.time}>{m.time}</Text>
          </View>
        ))}
      </View>

      {art && !reduced ? (
        <GestureDetector gesture={toastGesture}>
          <View
            style={[styles.toastHit, { left: art.box.x, top: art.box.y, width: art.box.width, height: art.box.height }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={`Kai: ${MESSAGE}. Tap to open, swipe to dismiss.`}
          />
        </GestureDetector>
      ) : null}

      {geo ? (
        <Animated.View
          pointerEvents={open ? 'auto' : 'none'}
          style={[styles.card, { top: geo.toastY - 92 }, cardStyle]}
        >
          <View style={styles.cardHead}>
            <View style={[styles.avatar, styles.cardAvatar]}>
              <Text style={styles.avatarText}>K</Text>
            </View>
            <View style={styles.cardHeadText}>
              <Text style={styles.cardWho}>Kai</Text>
              <Text style={styles.cardTime}>8:42 PM · Lakeside Crew</Text>
            </View>
          </View>
          <Text style={styles.cardMsg}>{MESSAGE}. Grilled corn and trout, bring your mugs.</Text>
          <View style={styles.replies}>
            <Pressable accessibilityRole="button" onPress={close} style={[styles.reply, styles.replyMain]}>
              <Text style={styles.replyMainText}>On my way</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={close} style={styles.reply}>
              <Text style={styles.replyText}>Save me a plate</Text>
            </Pressable>
          </View>
        </Animated.View>
      ) : null}

      {geo ? (
        <View style={[styles.input, { top: geo.inputTop, height: INPUT_H }]}>
          <View style={styles.field}>
            <Text style={styles.placeholder}>Message Lakeside Crew</Text>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="Send" style={styles.send}>
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <Path d="M9 14.5 V3.8 M4.2 8.4 L9 3.6 L13.8 8.4" stroke="#2A1608" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </Svg>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0B0E1A', overflow: 'hidden' },
  header: { position: 'absolute', left: 12, right: 20, flexDirection: 'row', alignItems: 'center', gap: 10 },
  back: { width: 36, height: 44, alignItems: 'center', justifyContent: 'center' },
  avatars: { flexDirection: 'row' },
  avatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#0E1220',
  },
  avatarText: { color: '#FFF6EC', fontSize: 13, fontWeight: '700' },
  headText: { flex: 1 },
  group: { color: INK, fontSize: 18, fontWeight: '700' },
  members: { color: SOFT, fontSize: 13, fontWeight: '500', marginTop: 1 },
  thread: { position: 'absolute', left: 16, right: 16, gap: 8 },
  day: { alignSelf: 'center', color: 'rgba(237,231,223,0.4)', fontSize: 11, fontWeight: '700', letterSpacing: 1.4, marginBottom: 2 },
  msgRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  msgRowMine: { flexDirection: 'row-reverse' },
  bubble: { maxWidth: '76%', paddingHorizontal: 14, paddingVertical: 9, borderRadius: 18, borderCurve: 'continuous' },
  bubbleOther: {
    backgroundColor: 'rgba(237,231,223,0.09)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(237,231,223,0.14)',
    borderBottomLeftRadius: 6,
  },
  bubbleMine: { backgroundColor: 'rgba(240,138,60,0.88)', borderBottomRightRadius: 6, boxShadow: '0 4px 14px rgba(240,138,60,0.25)' },
  who: { fontSize: 12, fontWeight: '700', marginBottom: 2 },
  msg: { color: INK, fontSize: 15, lineHeight: 20 },
  msgMine: { color: '#2A1608', fontWeight: '500' },
  time: { color: 'rgba(237,231,223,0.38)', fontSize: 11, fontVariant: ['tabular-nums'], marginBottom: 4 },
  toastHit: { position: 'absolute' },
  card: {
    position: 'absolute',
    left: 20,
    right: 20,
    padding: 18,
    gap: 12,
    borderRadius: 26,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(30,26,30,0.94)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(237,231,223,0.18)',
    boxShadow: '0 2px 4px rgba(0,0,0,0.4), 0 24px 48px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.08)',
  },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  cardAvatar: { backgroundColor: '#C9733A', borderColor: 'rgba(255,255,255,0.15)', width: 36, height: 36, borderRadius: 18 },
  cardHeadText: { flex: 1 },
  cardWho: { color: INK, fontSize: 16, fontWeight: '700' },
  cardTime: { color: SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  cardMsg: { color: INK, fontSize: 17, lineHeight: 23, fontWeight: '500' },
  replies: { flexDirection: 'row', gap: 10 },
  reply: {
    flex: 1,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(237,231,223,0.1)',
  },
  replyMain: { backgroundColor: EMBER },
  replyText: { color: INK, fontSize: 15, fontWeight: '600' },
  replyMainText: { color: '#2A1608', fontSize: 15, fontWeight: '700' },
  input: { position: 'absolute', left: 16, right: 16, flexDirection: 'row', alignItems: 'center', gap: 10 },
  field: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    paddingHorizontal: 18,
    backgroundColor: 'rgba(20,16,18,0.72)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(237,231,223,0.16)',
  },
  placeholder: { color: 'rgba(237,231,223,0.45)', fontSize: 16 },
  send: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: EMBER,
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 6px 16px rgba(240,138,60,0.35), inset 0 1px 0 rgba(255,255,255,0.3)',
  },
});
