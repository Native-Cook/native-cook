import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { COLOR, fingerAt, FONT, FUSED_G, FUSED_W, linkAt, linked, LOOP, makeLayout, pressAt, pullAt, recoilAt, rest, SNAP_DIST, stickAt, T, TENSION } from './gtf-motion';
import { GTF_SKSL } from './gtf-shader';

/*
 Glass Tiles Fuse — the "connect an account" sheet of an invented assistant app.

 Look (the reference, a recording of a shipped app): a dark list of apps to connect, dimmed behind a
 dark sheet; in the sheet our app's white tile and the mail service's smoky glass tile overlap, the
 glass one floating and bending the white tile where it covers it; a title, two lines of copy and a
 white pill button.

 Ours, with the tiles 1.35 times the reference's: Connect slides the glass over the white tile and
 the two melt into one liquid glass tile over 1.2 s (a smooth union of their shapes) while a light
 runs across it. Then pull the glass out with a finger: the white tile is dragged a little after
 it, a liquid neck stretches between them and thins, and past 44 pt it breaks — the neck runs back
 into both tiles, the white one recoils, the glass carries on into its slot and a green chip with
 a check joins them. Left alone, the tiles part by themselves after a moment. Done runs it back.
 At rest the glass tile can be dragged: it lifts, its shadow grows, it refracts whatever it passes
 over, and it springs home with the finger's speed.

 autoPlay: LOOP = 9 s (rest, Connect, the fusion, a finger pulls the glass out until the neck
 snaps, connected, Done, back to rest); frame 9 s equals frame 0. A touch takes over; 2 s after the last one it eases back and the loop starts again.
 Reduced motion: no loop and no flight; Connect cross-fades to the connected state.
*/

const effect = Skia.RuntimeEffect.Make(GTF_SKSL);
if (!effect) throw new Error('Glass Tiles Fuse: the tiles shader failed to compile.');
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const PRESS = { transitionProperty: 'transform', transitionDuration: 120, transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1) } as const;

function buzz(kind: 'light' | 'medium' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

const ROWS = [
  { name: 'Contacts', sub: 'On this iPhone', icon: 'contacts' },
  { name: 'Mail', sub: 'iCloud, Outlook', icon: 'mail' },
  { name: 'Calendar', sub: 'Work, Family', icon: 'calendar' },
  { name: 'Cloud Drive', sub: '2 folders shared', icon: 'drive' },
] as const;

function RowIcon({ kind }: { kind: (typeof ROWS)[number]['icon'] }) {
  return (
    <Svg width={36} height={36} viewBox="0 0 36 36">
      {kind === 'contacts' && (
        <>
          <SvgRect width={36} height={36} rx={9} fill="#8E8E93" />
          <Circle cx={18} cy={14} r={5.5} fill="#F2F2F2" />
          <Path d="M8 29c1.5-5.5 5.5-8 10-8s8.5 2.5 10 8" fill="#F2F2F2" />
        </>
      )}
      {kind === 'mail' && (
        <>
          <SvgRect width={36} height={36} rx={9} fill="#2F6BFF" />
          <SvgRect x={8} y={11} width={20} height={14} rx={2.5} fill="#FFFFFF" />
          <Path d="M8.8 12l9.2 7 9.2-7" stroke="#2F6BFF" strokeWidth={1.8} fill="none" />
        </>
      )}
      {kind === 'calendar' && (
        <>
          <SvgRect width={36} height={36} rx={9} fill="#FFFFFF" />
          <SvgRect width={36} height={10} rx={0} fill="#F2554C" />
          <Path d="M13 17h4l-2.5 3.2c1.6 0 2.7 1 2.7 2.4 0 1.5-1.2 2.6-2.8 2.6-1.1 0-2-.5-2.4-1.3M21 18.5l2.4-1.5V25" stroke="#1C1C1E" strokeWidth={1.7} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </>
      )}
      {kind === 'drive' && (
        <>
          <SvgRect width={36} height={36} rx={9} fill="#1F1F21" />
          <Path d="M18 9l9 15.5H9z" fill="#4BC27D" />
          <Path d="M18 9l4.5 7.8H13.5z" fill="#F4C443" />
          <Path d="M9 24.5h18l-2.6 3.5H11.6z" fill="#3D8BFF" />
        </>
      )}
    </Svg>
  );
}

type Props = { autoPlay?: boolean };

export function GlassTilesFuse({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);

  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const link = useSharedValue(0);
  const press = useSharedValue(0); // the pill button
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const lift = useSharedValue(0);
  const fade = useSharedValue(0); // reduced motion: 0 apart … 1 connected, cross-faded
  const pull = useSharedValue(0); // the finger's pull on the melted glass, pt
  const pullY = useSharedValue(0);
  const recoil = useSharedValue(0); // the white tile dragged after the glass, pt
  const stick = useSharedValue(1); // the neck: 1 holding … 0 broken
  const pulling = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerOn = useSharedValue(0);
  const [connected, setConnected] = useState(false);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      link.set(linkAt(t));
      press.set(Math.max(pressAt(t, T.press), pressAt(t, T.done)));
      pull.set(pullAt(t));
      pullY.set(0);
      recoil.set(recoilAt(t));
      stick.set(stickAt(t));
      const fg = fingerAt(t);
      finger.set(fg.finger);
      fingerOn.set(fg.at);
    } else if (auto && resumeAsked.get() === 0 && clock.get() - lastTouch.get() > 2 && lift.get() < 0.01) {
      resumeAsked.set(1);
      stick.set(1);
      link.set(withTiming(0, { duration: link.get() > 0.01 ? 1100 : 0, easing: EASE_IN_OUT }));
      dragX.set(withSpring(0, { duration: 500, dampingRatio: 0.8 }));
      dragY.set(withSpring(0, { duration: 500, dampingRatio: 0.8 }));
    } else if (resumeAsked.get() === 1 && link.get() < 0.001 && clock.get() - lastTouch.get() > 3.2) {
      resumeAsked.set(0);
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
    (globalThis as Record<string, unknown>).__glassTiles = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: () => {
        mode.set(1);
        lastTouch.set(1e9);
      },
      state: () => JSON.stringify({ mode: mode.get(), link: +link.get().toFixed(3), lift: +lift.get().toFixed(2), pull: +pull.get().toFixed(1), stick: +stick.get().toFixed(2) }),
    };
  }, [pin, mode, link, lift, lastTouch, pull, stick]);

  // copy follows the connection; a haptic when the neck snaps and when the chip lands
  useAnimatedReaction(
    () => link.get() > 0.56,
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setConnected, now);
    },
  );
  useAnimatedReaction(
    () => (link.get() > 0.86 ? 2 : link.get() > 0.4 ? 1 : 0),
    (now, before) => {
      if (before === null || now <= before || mode.get() === 0) return;
      scheduleOnRN(buzz, now === 2 ? 'success' : 'light');
    },
  );

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
    }
  };

  // the neck breaks: it runs back into both tiles, the white one recoils, the glass goes to its slot
  const snap = (vx: number) => {
    'worklet';
    pulling.set(0);
    stick.set(withTiming(0, { duration: 140, easing: Easing.out(Easing.quad) }));
    recoil.set(withSpring(0, { duration: 650, dampingRatio: 0.35 }));
    link.set(withTiming(1, { duration: 800, easing: Easing.out(Easing.cubic) }));
    pull.set(withSpring(0, { duration: 800, dampingRatio: 0.9, velocity: vx }));
    pullY.set(withSpring(0, { duration: 600, dampingRatio: 0.8 }));
    scheduleOnRN(buzz, 'medium');
  };

  const connect = useCallback(() => {
    if (reduced) {
      fade.set(withTiming(1, { duration: 260 }));
      link.set(1);
      return;
    }
    buzz('light');
    stick.set(1);
    // melt together (1.2 s of the 1.6), wait a moment for a finger to pull them apart, else part
    link.set(
      withSequence(
        withTiming(0.5, { duration: 1600, easing: EASE_IN_OUT }),
        withDelay(900, withTiming(1, { duration: 900, easing: Easing.inOut(Easing.cubic) })),
      ),
    );
  }, [reduced, fade, link, stick]);
  const done = useCallback(() => {
    if (reduced) {
      fade.set(withTiming(0, { duration: 260 }));
      link.set(0);
      return;
    }
    stick.set(1);
    link.set(withTiming(0, { duration: 1150, easing: EASE_IN_OUT }));
  }, [reduced, fade, link, stick]);

  /* ------------------------------------------------------------ gestures */
  const drag = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      // melted together: this is a pull on the liquid
      if (!reduced && link.get() > 0.4 && link.get() < 0.6 && stick.get() > 0.99) {
        link.set(link.get()); // hold the melt where it is (cancels the parting that was waiting)
        link.set(withTiming(0.5, { duration: 200 }));
        pulling.set(1);
        return;
      }
      if (link.get() > 0.02) return;
      lift.set(withSpring(1, { duration: 260, dampingRatio: 0.8 }));
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (pulling.get() === 1) {
        // the liquid gives: it follows the finger fully to the right, a little in other directions
        const px = e.translationX > 0 ? e.translationX : 0.25 * e.translationX;
        pull.set(px);
        pullY.set(0.4 * e.translationY);
        recoil.set(TENSION * Math.max(0, px));
        const dx = FUSED_W + FUSED_G + px - recoil.get();
        if (Math.hypot(dx, pullY.get()) > SNAP_DIST) snap(e.velocityX);
        return;
      }
      if (link.get() > 0.02) return;
      dragX.set(e.translationX);
      dragY.set(e.translationY);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (pulling.get() === 1) {
        // let go before it broke: the liquid pulls the glass back in
        pulling.set(0);
        pull.set(withSpring(0, { duration: 600, dampingRatio: 0.55, velocity: e.velocityX }));
        pullY.set(withSpring(0, { duration: 600, dampingRatio: 0.55 }));
        recoil.set(withSpring(0, { duration: 600, dampingRatio: 0.55 }));
        // and, left alone, the tiles part by themselves a moment later
        link.set(withDelay(900, withTiming(1, { duration: 900, easing: Easing.inOut(Easing.cubic) })));
        return;
      }
      lift.set(withSpring(0, { duration: 420, dampingRatio: 0.8 }));
      dragX.set(withSpring(0, { velocity: e.velocityX, duration: 520, dampingRatio: 0.62 }));
      dragY.set(withSpring(0, { velocity: e.velocityY, duration: 520, dampingRatio: 0.62 }));
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const t = mode.get() === 0 && pin.get() >= 0 ? pin.get() : clock.get();
    const r = rest(L, t % LOOP, LOOP);
    r.gx += dragX.get();
    r.gy += dragY.get();
    r.ga += dragX.get() * 0.0015;
    const k = link.get();
    const p = linked(L, r, k);
    const gs = L.glass * p.size * (1 + 0.06 * lift.get());
    return {
      uWhite: [p.wx + recoil.get(), p.wy - L.sheetTop, L.white * p.size, p.wa],
      uGlass: [p.gx + pull.get(), p.gy + pullY.get() - L.sheetTop - 6 * lift.get(), gs, p.ga + pull.get() * 0.0008],
      uFuse: p.fuse * stick.get(),
      uSeam: p.seam,
      uCheck: p.check,
      uPixelRatio: 3,
      uLift: lift.get(),
    };
  });

  const press$ = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * press.get() }] }));
  const finger$ = useAnimatedStyle(() => {
    const onGlass = fingerOn.get() === 1;
    const t = pin.get() >= 0 ? pin.get() : clock.get();
    const r = rest(L, t % LOOP, LOOP);
    const p = linked(L, r, link.get());
    const x = onGlass ? p.gx + pull.get() : L.w / 2;
    const y = onGlass ? p.gy : L.buttonTop + 26;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });
  const ready = fontsLoaded && size !== null;
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {ready && (
        <>
          <Text style={[styles.h1, { top: L.titleTop }]} accessibilityRole="header">Connections</Text>
          <Text style={[styles.lede, { top: L.titleTop + 40 }]}>Let the assistant use what you already have.</Text>
          {ROWS.map((row, i) => (
            <View key={row.name} style={[styles.row, { top: L.rowsTop + i * L.rowH }]}>
              <RowIcon kind={row.icon} />
              <View style={styles.rowText}>
                <Text style={styles.rowName}>{row.name}</Text>
                <Text style={styles.rowSub}>{row.sub}</Text>
              </View>
              <View style={styles.rowPill}><Text style={styles.rowPillText}>Connect</Text></View>
            </View>
          ))}
          <View style={[StyleSheet.absoluteFill, { backgroundColor: COLOR.dim }]} pointerEvents="none" />

          <View style={[styles.sheet, { top: L.sheetTop }]} accessibilityViewIsModal>
            <View style={styles.grabber} />
            <Canvas style={{ position: 'absolute', left: 0, top: 0, width: L.w, height: L.stageH + 12 }} pointerEvents="none">
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
            <GestureDetector gesture={drag}>
              <View
                style={{ position: 'absolute', left: 0, top: 24, width: L.w, height: L.stageH - 24 }}
                accessible
                accessibilityLabel="Mailbox tile"
                accessibilityHint={connected ? undefined : 'Drag to move it. Once connecting, pull it out to finish.'}
              />
            </GestureDetector>
            <View style={[styles.copy, { top: L.textTop - L.sheetTop }]}>
              <Text style={styles.title} accessibilityLiveRegion="polite">{connected ? 'Mailbox connected' : 'Mailbox'}</Text>
              <Text style={styles.body}>
                {connected ? 'Mina’s inbox is ready to use across your chats and apps.' : 'Connect a Mailbox account for Orbit to use across your chats and apps.'}
              </Text>
            </View>
            <Animated.View style={[styles.buttonWrap, PRESS, { top: L.buttonTop - L.sheetTop, left: L.side, right: L.side }, press$]}>
              <Pressable
                onPressIn={() => {
                  press.set(withTiming(1, { duration: 100 }));
                }}
                onPressOut={() => press.set(withTiming(0, { duration: 180 }))}
                onPress={() => {
                  takeOverJS();
                  if (link.get() > 0.5) done();
                  else connect();
                }}
                style={styles.button}
                accessibilityRole="button"
              >
                <Text style={styles.buttonText}>{connected ? 'Done' : 'Connect Mailbox'}</Text>
              </Pressable>
            </Animated.View>
          </View>
          <Animated.View pointerEvents="none" style={[styles.finger, finger$]} />
        </>
      )}
    </View>
  );

  function takeOverJS() {
    mode.set(1);
    lastTouch.set(clock.get());
    resumeAsked.set(0);
  }
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  h1: { position: 'absolute', left: 20, fontFamily: FONT.bold, fontSize: 30, letterSpacing: -0.8, color: COLOR.title },
  lede: { position: 'absolute', left: 20, right: 20, fontFamily: FONT.regular, fontSize: 15, color: COLOR.rowSub },
  row: { position: 'absolute', left: 20, right: 20, height: 63, flexDirection: 'row', alignItems: 'center', gap: 14 },
  rowText: { flex: 1, gap: 2 },
  rowName: { fontFamily: FONT.semibold, fontSize: 16, color: COLOR.rowName },
  rowSub: { fontFamily: FONT.regular, fontSize: 13, color: COLOR.rowSub },
  rowPill: { height: 30, paddingHorizontal: 14, borderRadius: 15, backgroundColor: COLOR.pill, alignItems: 'center', justifyContent: 'center' },
  rowPillText: { fontFamily: FONT.semibold, fontSize: 13, color: COLOR.pillInk },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: -40,
    backgroundColor: COLOR.sheet,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    overflow: 'hidden',
  },
  grabber: { alignSelf: 'center', marginTop: 8, width: 36, height: 5, borderRadius: 3, backgroundColor: COLOR.grabber },
  copy: { position: 'absolute', left: 32, right: 32, alignItems: 'center', gap: 8 },
  title: { fontFamily: FONT.semibold, fontSize: 18, letterSpacing: -0.3, color: COLOR.title, textAlign: 'center' },
  body: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.body, textAlign: 'center' },
  buttonWrap: { position: 'absolute', height: 52 },
  button: { flex: 1, borderRadius: 26, backgroundColor: COLOR.button, alignItems: 'center', justifyContent: 'center' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)' },
  buttonText: { fontFamily: FONT.semibold, fontSize: 16, letterSpacing: -0.2, color: COLOR.buttonInk },
});
