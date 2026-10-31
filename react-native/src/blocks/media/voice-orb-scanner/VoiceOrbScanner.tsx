import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { orbFx } from './vos-orb';
import { type Cue, useVosAudio } from './use-vos-audio';

/*
 Voice Orb Scanner — the voice scan of an invented singing coach.

 Look (the pin, a still of three screens; this is the third): a dark aubergine page with a warm glow,
 "AI Voice Scanning" and a Completed chip, a big glass orb with pink and orange fire swirling inside,
 three small dark cards (voice tier, vocal range, breath capacity) and a lilac "Your voice, your
 playlist" card.

 Motion (ours): hold the orb and sing. While it listens the orb's surface wobbles with the voice and
 a ring fills round it; at the end it splits: three droplets pull out of it on necks of goo that thin
 and snap, fly to the three result cards and become the beads on them while the cards fade up. Scan
 again and the droplets fly back and merge into the orb. The orb is one SkSL metaball field.

 autoPlay: LOOP = 9 s. 1.0-3.4 s listening; 3.4-4.5 s the split; 6.9-7.9 s the droplets merge back.
 Frame 9 s equals frame 0. Reduced motion: no wobble; the split is a crossfade.
*/

let debugState = '';
const LOOP = 9;
const HOLD = 2.4; // seconds of singing for a scan

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
/** The script: listening L (0..1) and split S (0..1) at loop time t. */
const scriptAt = (t: number) => {
  'worklet';
  const L = t >= 1.0 && t < 3.4 ? (t - 1.0) / 2.4 : t >= 3.4 && t < 7.9 ? 1 : 0;
  let S = 0;
  if (t >= 3.4 && t < 6.9) S = easeInOut((t - 3.4) / 1.1);
  else if (t >= 6.9 && t < 7.9) S = 1 - easeInOut((t - 6.9) / 1.0);
  return { L, S, listening: t >= 1.0 && t < 3.4 ? 1 : 0 };
};

const CARDS = [
  { label: 'Voice tier', value: 'Soprano', tint: '#ff6fb7' },
  { label: 'Vocal range', value: '2 octaves', tint: '#ff9a4a' },
  { label: 'Breath', value: '2.2 sec', tint: '#b78cff' },
];

type Props = { autoPlay?: boolean };

export function VoiceOrbScanner({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [fontsOk] = useFonts({
    'VOS-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'VOS-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'VOS-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'VOS-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const OY = insets.top + 270; // orb centre
  const R = Math.min(118, w * 0.29);
  const CW = (w - 48 - 16) / 3;
  const CY = OY + R + 100; // the cards' top
  const bead = (i: number) => {
    'worklet';
    return { x: 24 + i * (CW + 8) + 26, y: CY + 28 };
  };

  const play = useVosAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  // L listening progress, S split progress, sDir where S is heading (touch), held finger on the orb
  const st = useSharedValue({ L: 0, S: 0, sDir: 0, held: 0, listening: 0 });
  const debugT = useSharedValue(-1);
  const [phase, setPhase] = useState(0); // 0 idle, 1 listening, 2 done

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __voiceOrb?: unknown };
    g.__voiceOrb = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__voiceOrb;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const s = st.get();
    let t = time.get();
    if (dbg >= 0) {
      t = dbg;
      const sc = scriptAt(t);
      s.L = sc.L;
      s.S = sc.S;
      s.listening = sc.listening;
      debugT.set(-2);
    } else if (dbg !== -2) {
      const touched = s.held === 1 || now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
        const sc = scriptAt(t);
        s.L = sc.L;
        s.S = sc.S;
        s.listening = sc.listening;
        s.sDir = 0;
      } else {
        // a finger: listen while held, fall back when let go early; the split runs on its own
        if (s.held === 1 && s.S < 0.01) {
          s.listening = 1;
          s.L = Math.min(1, s.L + dt / HOLD);
          if (s.L >= 1) {
            s.listening = 0;
            s.sDir = 1;
            scheduleOnRN(cue, 'split');
            scheduleOnRN(buzz, 2);
          }
        } else if (s.sDir === 0 && s.S < 0.01) {
          s.listening = 0;
          s.L = Math.max(0, s.L - dt * 2.5);
        }
        if (s.sDir !== 0) {
          s.S = clamp01(s.S + (s.sDir * dt) / (s.sDir > 0 ? 1.1 : 1.0));
          if (s.sDir < 0 && s.S <= 0) {
            s.sDir = 0;
            s.L = 0;
          }
          if (s.sDir > 0 && s.S >= 1) s.sDir = 0;
        }
      }
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} L=${s.L.toFixed(2)} S=${s.S.toFixed(2)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  useAnimatedReaction(
    () => {
      tick.get();
      const s = st.get();
      return s.S > 0.5 ? 2 : s.listening === 1 ? 1 : 0;
    },
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setPhase, n);
    },
  );

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = st.get();
    const now = clock.get();
    const t = time.get();
    const sp = reduced ? (s.S > 0.5 ? 1 : 0) : s.S;
    // a warm glow on the page behind the orb
    const warm = Skia.Paint();
    warm.setShader(
      Skia.Shader.MakeRadialGradient({ x: cx, y: OY + 40 }, w * 0.85, [Skia.Color('rgba(120,46,92,0.55)'), Skia.Color('rgba(60,24,70,0.25)'), Skia.Color('rgba(23,18,31,0)')], [0, 0.5, 1], 0),
    );
    c.drawRect(Skia.XYWHRect(0, 0, w, h), warm);
    // while listening, soft rings pulse out from the orb with the voice (a voice-wave pulse)
    if (s.listening && !reduced) {
      const ringP = Skia.Paint();
      ringP.setAntiAlias(true);
      ringP.setStyle(1);
      for (let i = 0; i < 3; i++) {
        const k = ((now * 0.9 + i / 3) % 1 + 1) % 1;
        const rr = R * (1.02 + 0.55 * k);
        ringP.setStrokeWidth(2 + 6 * (1 - k));
        ringP.setColor(Skia.Color('#ff7ad0'));
        ringP.setAlphaf(0.35 * (1 - k) * Math.min(1, s.L * 6));
        ringP.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 3 + 6 * k, true));
        c.drawCircle(cx, OY, rr, ringP);
      }
    }
    // the ring that fills while listening, fading once the scan is done
    if (s.L > 0.001) {
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(1);
      ring.setStrokeWidth(4);
      ring.setStrokeCap(1);
      ring.setShader(
        Skia.Shader.MakeSweepGradient(cx, OY, [Skia.Color('#ff6fb7'), Skia.Color('#ffb04a'), Skia.Color('#b78cff'), Skia.Color('#ff6fb7')], null, 0),
      );
      ring.setAlphaf(1 - smooth(sp * 3));
      const rr = R + 22;
      c.drawArc(Skia.XYWHRect(cx - rr, OY - rr, rr * 2, rr * 2), -90, 360 * s.L, false, ring);
    }
    if (orbFx) {
      // the voice: a wobble while listening, quick and uneven
      const vo = reduced ? 0 : s.listening ? 0.55 + 0.45 * Math.abs(Math.sin(now * 5.3) * Math.sin(now * 2.1 + 1)) : 0.12;
      const orbR = R * (1 - 0.14 * sp);
      // the swirl's phase: one turn per loop in autoplay, running on with the clock otherwise
      const ph = (2 * Math.PI * (auto && now - last.get() >= 2 ? t : now)) / LOOP;
      const u: number[] = [ph, cx, OY, orbR, vo];
      for (let i = 0; i < 3; i++) {
        // each droplet leaves a little later than the one before and arcs up on its way down
        const k = reduced ? sp : easeInOut((sp - i * 0.08) / 0.84);
        const b = bead(i);
        const sx = cx + (i - 1) * R * 0.25;
        const sy = OY + R * 0.3;
        const x = mix(sx, b.x, k);
        const y = mix(sy, b.y, k) - Math.sin(k * Math.PI) * 60;
        const r = k <= 0.001 ? 0 : mix(R * 0.42, 17, smooth(k * 1.2));
        u.push(x, y, r, 0);
      }
      u.push(s.listening ? 1 : 0);
      const p = Skia.Paint();
      p.setShader(orbFx.makeShader(u));
      c.drawRect(Skia.XYWHRect(0, insets.top + 40, w, h - insets.top - 40), p);
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, OY, R, CW, CY]);

  const cardStyle = (i: number) => {
    'worklet';
    const s = st.get();
    const k = smooth((s.S - 0.55 - i * 0.08) / 0.3);
    return { opacity: k, transform: [{ translateY: (1 - k) * 14 }, { scale: mix(0.94, 1, k) }] };
  };
  const card0 = useAnimatedStyle(() => {
    tick.get();
    return cardStyle(0);
  });
  const card1 = useAnimatedStyle(() => {
    tick.get();
    return cardStyle(1);
  });
  const card2 = useAnimatedStyle(() => {
    tick.get();
    return cardStyle(2);
  });
  const cardStyles = [card0, card1, card2];
  const hintStyle = useAnimatedStyle(() => {
    tick.get();
    return { opacity: 1 - smooth(st.get().S * 2) };
  });
  const pressStyle = useAnimatedStyle(() => {
    tick.get();
    return { transform: [{ scale: st.get().held ? 0.97 : 1 }] };
  });

  const orbGesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const s = st.get();
      last.set(clock.get());
      if (s.S > 0.99 && s.sDir === 0) {
        // tap the finished orb to scan again
        s.sDir = -1;
        scheduleOnRN(cue, 'merge');
        scheduleOnRN(buzz, 0);
        return;
      }
      if (s.S > 0.01) return;
      s.held = 1;
      scheduleOnRN(cue, 'listen');
      scheduleOnRN(buzz, 1);
    })
    .onFinalize(() => {
      const s = st.get();
      last.set(clock.get());
      s.held = 0;
    });
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const rescanUI = () => {
    'worklet';
    const s = st.get();
    last.set(clock.get());
    if (s.S > 0.5) {
      s.sDir = -1;
      scheduleOnRN(cue, 'merge');
      scheduleOnRN(buzz, 0);
    }
  };
  const rescan = () => scheduleOnUI(rescanUI);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const chip = phase === 2 ? 'Completed' : phase === 1 ? 'Listening…' : 'Ready';

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.bar, { top: insets.top + 8 }]}>
        <View style={styles.close} accessible accessibilityRole="button" accessibilityLabel="Close">
          <Text style={styles.closeGlyph} allowFontScaling={false}>
            ✕
          </Text>
        </View>
      </View>
      <View style={[styles.titleBox, { top: insets.top + 56 }]}>
        <Text style={styles.title} allowFontScaling={false}>
          AI Voice Scanning
        </Text>
        <View style={styles.chip}>
          <View style={[styles.chipDot, phase === 1 && styles.chipLive, phase === 2 && styles.chipDone]} />
          <Text style={styles.chipText} allowFontScaling={false} accessibilityLiveRegion="polite">
            {chip}
          </Text>
        </View>
      </View>
      <Animated.Text style={[styles.hint, { top: OY + R + 30 }, hintStyle]} allowFontScaling={false}>
        Hold the orb and sing a note
      </Animated.Text>
      <Text style={[styles.section, { top: CY - 32 }]} allowFontScaling={false}>
        Voice resume
      </Text>
      {CARDS.map((cd, i) => (
        <Animated.View
          key={cd.label}
          style={[styles.card, { left: 24 + i * (CW + 8), top: CY, width: CW }, cardStyles[i]]}
          accessible={phase === 2}
          accessibilityLabel={`${cd.label}: ${cd.value}`}
        >
          <View style={styles.beadSlot} />
          <Text style={styles.cardLabel} allowFontScaling={false}>
            {cd.label}
          </Text>
          <Text style={styles.cardValue} allowFontScaling={false}>
            {cd.value}
          </Text>
        </Animated.View>
      ))}
      {/* the orb over the cards, so the droplets land on them as their beads */}
      {size ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      <GestureDetector gesture={orbGesture}>
        <Animated.View
          style={[styles.orbHit, { left: cx - R - 20, top: OY - R - 20, width: 2 * R + 40, height: 2 * R + 40 }, pressStyle]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={phase === 2 ? 'Scan again' : 'Hold to sing and scan your voice'}
        />
      </GestureDetector>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Your voice, your playlist. Scan again"
        onPress={rescan}
        style={({ pressed }) => [styles.playlist, { bottom: insets.bottom + 18 }, pressed && styles.pressed]}
      >
        <Text style={styles.plTitle} allowFontScaling={false}>
          Your voice, your playlist
        </Text>
        <Text style={styles.plSub} allowFontScaling={false}>
          Songs picked for your range · tap to scan again
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#17121f' },
  bar: { position: 'absolute', right: 20, height: 40, flexDirection: 'row' },
  close: { width: 38, height: 38, borderRadius: 19, backgroundColor: 'rgba(255,255,255,0.1)', alignItems: 'center', justifyContent: 'center' },
  closeGlyph: { color: '#ffffff', fontSize: 15, fontFamily: 'VOS-Medium' },
  titleBox: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 10 },
  title: { color: '#ffffff', fontSize: 22, fontFamily: 'VOS-Title' },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 26, paddingHorizontal: 10, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.1)' },
  chipDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: 'rgba(255,255,255,0.5)' },
  chipLive: { backgroundColor: '#ff6fb7' },
  chipDone: { backgroundColor: '#8ff0b4' },
  chipText: { color: '#ffffff', fontSize: 12, fontFamily: 'VOS-Medium' },
  orbHit: { position: 'absolute', borderRadius: 999 },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.6)', fontSize: 14, fontFamily: 'VOS-Regular' },
  section: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#ffffff', fontSize: 17, fontFamily: 'VOS-Title' },
  card: { position: 'absolute', height: 128, borderRadius: 20, backgroundColor: 'rgba(28,22,38,0.92)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.08)', padding: 12, justifyContent: 'flex-end' },
  beadSlot: { position: 'absolute', left: 9, top: 11, width: 34, height: 34, borderRadius: 17, backgroundColor: 'rgba(255,255,255,0.05)' },
  cardLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 12, fontFamily: 'VOS-Regular' },
  cardValue: { color: '#ffffff', fontSize: 16, fontFamily: 'VOS-SemiBold', marginTop: 4 },
  playlist: { position: 'absolute', left: 20, right: 20, height: 76, borderRadius: 22, backgroundColor: '#d9c9ff', paddingHorizontal: 18, justifyContent: 'center' },
  plTitle: { color: '#1b1326', fontSize: 18, fontFamily: 'VOS-Title' },
  plSub: { color: 'rgba(27,19,38,0.65)', fontSize: 12.5, fontFamily: 'VOS-Regular', marginTop: 3 },
  pressed: { transform: [{ scale: 0.98 }] },
});

export { LOOP };
