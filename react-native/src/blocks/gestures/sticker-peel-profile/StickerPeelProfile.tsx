import { BlurStyle, Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawPeeled, HALF } from './spp-stickers';
import { type Cue, useSppAudio } from './use-spp-audio';

/*
 Sticker Peel Profile — the profile screen of an invented music-sharing app.

 Look (the pin, a still): a black profile with an avatar made of vinyl stickers (a cat-like bun with
 a heart, a "slay" label and a flame stuck round it, each with a white die-cut border), the name and
 handle, a friends pill, a purple Music Compatibility banner, Recently played with covers, and a round
 close button. The pin's app, people and albums are real; ours are invented and the covers are drawn.

 Motion (ours): the stickers peel. Pull one by a corner and the corner folds back along a line,
 showing the pale backing with a shadow under the flap; pull further and it comes off, lifts and
 follows the finger, tilting as it moves; let go anywhere and it slaps down with a squash. Let go
 before it comes off and the corner springs back flat.

 autoPlay: LOOP = 9 s. 0.8-1.7 s the "slay" sticker is peeled from its lower right corner, 1.7-2.6 s
 carried onto the banner and slapped down; 5.2-6.0 s peeled again, 6.0-6.9 s carried back home.
 Frame 9 s equals frame 0. Reduced motion: no peel animation; a sticker moves without lifting.
*/

let debugState = '';
const LOOP = 9;
const N = 4;

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

type Sticker = { x: number; y: number; rot: number; f: number; vf: number; corner: number; lift: number; sq: number; vsq: number; vx: number; k: number };

type Props = { autoPlay?: boolean };

export function StickerPeelProfile({ autoPlay = false }: Props) {
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
    'SPP-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'SPP-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'SPP-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
    'SPP-Bold': require('./assets/fonts/Inter-Bold.ttf'),
  });
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 24);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const T = insets.top;
  const BANNER_Y = T + 318;
  // home places: avatar, heart, slay, fire
  const HOME = [
    { x: cx, y: T + 128, rot: 0 },
    { x: cx - 56, y: T + 86, rot: -14 },
    { x: cx - 50, y: T + 170, rot: -10 },
    { x: cx + 52, y: T + 168, rot: 10 },
  ];
  const AWAY = { x: w - 74, y: BANNER_Y + 6, rot: 14 };

  const play = useSppAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const st = useSharedValue<Sticker[]>(HOME.map((p) => ({ x: p.x, y: p.y, rot: p.rot, f: 0, vf: 0, corner: 2, lift: 0, sq: 0, vsq: 0, vx: 0, k: 1 })));
  const order = useSharedValue([0, 1, 2, 3]); // drawing order, last on top
  const drag = useSharedValue({ i: -1, gx: 0, gy: 0, sx: 0, sy: 0, ox: 0, oy: 0, peeling: 0, px: 0 });
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __stickerPeel?: unknown };
    g.__stickerPeel = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__stickerPeel;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** The script for the "slay" sticker at loop time t: peel, position, rotation, lift, squash. */
  const scriptSlay = (t: number) => {
    'worklet';
    const H = HOME[2];
    const out = (a: number) => {
      // peel a..a+0.9, carry a+0.9..a+1.8, slap at a+1.8
      const f = t < a + 0.9 ? 0.62 * easeInOut((t - a) / 0.85) : 0;
      const lift = t >= a + 0.85 ? 1 - smooth((t - (a + 1.75)) / 0.08) : 0;
      const k = easeInOut((t - (a + 0.9)) / 0.85);
      const land = t - (a + 1.8);
      const sq = land >= 0 && land < 0.5 ? Math.sin(land * 22) * Math.exp(-land * 9) * 0.1 : 0;
      return { f, lift, k, sq };
    };
    if (t < 5.2) {
      const o = out(0.8);
      const x = mix(H.x, AWAY.x, o.k) + Math.sin(o.k * Math.PI) * 0;
      const y = mix(H.y, AWAY.y, o.k) - Math.sin(o.k * Math.PI) * 40;
      return { f: o.f, corner: 2, x, y, rot: mix(H.rot, AWAY.rot, o.k) + Math.sin(o.k * Math.PI) * -12, lift: o.lift, sq: o.sq };
    }
    const o = out(5.2);
    const x = mix(AWAY.x, H.x, o.k);
    const y = mix(AWAY.y, H.y, o.k) - Math.sin(o.k * Math.PI) * 40;
    return { f: o.f, corner: 0, x, y, rot: mix(AWAY.rot, H.rot, o.k) + Math.sin(o.k * Math.PI) * 12, lift: o.lift, sq: o.sq };
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const ss = st.get();
    const dg = drag.get();
    let t = time.get();
    const scripted = dbg >= 0 || dbg === -2 || (auto && dg.i < 0 && now - last.get() >= 2);
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
    } else if (scripted && dbg !== -2) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    if (scripted) {
      const s = scriptSlay(t);
      const b = ss[2];
      b.f = reduced ? 0 : s.f;
      b.corner = s.corner;
      b.x = s.x;
      b.y = s.y;
      b.rot = s.rot;
      b.lift = reduced ? 0 : s.lift;
      b.sq = s.sq;
      b.k = 1;
      order.set([0, 1, 3, 2]);
      for (let i = 0; i < N; i++) {
        if (i === 2) continue;
        const o = ss[i];
        o.x = HOME[i].x;
        o.y = HOME[i].y;
        o.rot = HOME[i].rot;
        o.f = 0;
        o.lift = 0;
        o.sq = 0;
        o.k = 1;
      }
    } else {
      for (let i = 0; i < N; i++) {
        const o = ss[i];
        // a corner let go springs back flat with a little flutter; a slap squashes and settles
        if (!(dg.i === i && dg.peeling === 1)) {
          o.vf += (-300 * o.f - 2 * Math.sqrt(300) * 0.35 * o.vf) * dt;
          o.f = Math.max(0, o.f + o.vf * dt);
        }
        if (!(dg.i === i && o.lift > 0.5)) o.lift = Math.max(0, o.lift - dt * 10);
        o.vsq += (-500 * o.sq - 2 * Math.sqrt(500) * 0.3 * o.vsq) * dt;
        o.sq += o.vsq * dt;
        o.vx *= Math.exp(-dt * 8);
      }
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} slay f=${ss[2].f.toFixed(2)} lift=${ss[2].lift.toFixed(2)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const ss = st.get();
    const ord = order.get();
    const font = { bold };
    for (let n = 0; n < N; n++) {
      const i = ord[n];
      const s = ss[i];
      const lift = s.lift;
      c.save();
      c.translate(s.x, s.y);
      c.rotate(s.rot + Math.max(-14, Math.min(14, s.vx * 0.015)), 0, 0);
      const sc = (1 + 0.1 * lift) * s.k;
      c.scale(sc * (1 + s.sq), sc * (1 - s.sq));
      if (lift > 0.01) {
        // a lifted sticker casts a wider, softer shadow
        const sh = Skia.Paint();
        sh.setColor(Skia.Color('#000000'));
        sh.setAlphaf(0.4 * lift);
        sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 + 10 * lift, true));
        c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-HALF[i][0] + 6, -HALF[i][1] + 14 * lift, HALF[i][0] * 2 - 12, HALF[i][1] * 2), 16, 16), sh);
      }
      drawPeeled(c, i, s.corner, s.f, font);
      c.restore();
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, bold]);

  // ---------------------------------------------------------------- touch: peel, lift, carry, slap
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      last.set(clock.get());
      const ss = st.get();
      const ord = order.get();
      for (let n = N - 1; n >= 0; n--) {
        const i = ord[n];
        const s = ss[i];
        // into the sticker's own frame
        const a = (-s.rot * Math.PI) / 180;
        const dx = e.x - s.x;
        const dy = e.y - s.y;
        const lx = dx * Math.cos(a) - dy * Math.sin(a);
        const ly = dx * Math.sin(a) + dy * Math.cos(a);
        if (Math.abs(lx) < HALF[i][0] + 8 && Math.abs(ly) < HALF[i][1] + 8) {
          // peel from the corner nearest the finger
          const corner = ly < 0 ? (lx < 0 ? 0 : 1) : lx < 0 ? 3 : 2;
          s.corner = corner;
          drag.set({ i, gx: e.x, gy: e.y, sx: s.x, sy: s.y, ox: s.x - e.x, oy: s.y - e.y, peeling: reduced ? 0 : 1, px: e.x });
          const no: number[] = [];
          for (let m = 0; m < N; m++) if (ord[m] !== i) no.push(ord[m]);
          no.push(i);
          order.set(no);
          scheduleOnRN(buzz, 0);
          return;
        }
      }
    })
    .onUpdate((e) => {
      const dg = drag.get();
      if (dg.i < 0) return;
      last.set(clock.get());
      const s = st.get()[dg.i];
      const dist = Math.hypot(e.x - dg.gx, e.y - dg.gy);
      if (dg.peeling === 1) {
        s.f = Math.min(0.62, dist / (Math.hypot(HALF[dg.i][0], HALF[dg.i][1]) * 1.6));
        if (dist > 52) {
          // off it comes
          dg.peeling = 0;
          s.lift = 1;
          s.f = 0;
          scheduleOnRN(cue, 'peel');
          scheduleOnRN(buzz, 1);
        }
      } else {
        s.lift = 1;
        s.vx = (e.x - dg.px) / 0.016;
        dg.px = e.x;
        s.x = e.x + dg.ox;
        s.y = e.y + dg.oy;
      }
    })
    .onFinalize(() => {
      const dg = drag.get();
      if (dg.i < 0) return;
      last.set(clock.get());
      const s = st.get()[dg.i];
      if (dg.peeling === 0 && s.lift > 0.5) {
        s.lift = 0;
        s.sq = 0.1;
        s.vsq = 0;
        scheduleOnRN(cue, 'slap');
        scheduleOnRN(buzz, 1);
      }
      drag.set({ ...dg, i: -1, peeling: 0 });
    });

  // two fingers on a sticker turn and resize it (as story-sticker editors do), from 0.6× to 1.8×
  const twist = useSharedValue({ i: -1, k0: 1, r0: 0 });
  const pickAt = (x: number, y: number) => {
    'worklet';
    const ss = st.get();
    const ord = order.get();
    for (let n = N - 1; n >= 0; n--) {
      const i = ord[n];
      const s = ss[i];
      if (Math.abs(x - s.x) < HALF[i][0] * s.k + 30 && Math.abs(y - s.y) < HALF[i][1] * s.k + 30) return i;
    }
    return -1;
  };
  const pinch = Gesture.Pinch()
    .onStart((e) => {
      const i = drag.get().i >= 0 ? drag.get().i : pickAt(e.focalX, e.focalY);
      if (i < 0) return;
      const tw = twist.get();
      twist.set({ ...tw, i, k0: st.get()[i].k });
      last.set(clock.get());
    })
    .onUpdate((e) => {
      const tw = twist.get();
      if (tw.i < 0) return;
      st.get()[tw.i].k = Math.max(0.6, Math.min(1.8, tw.k0 * e.scale));
      last.set(clock.get());
    })
    .onEnd(() => {
      twist.set({ ...twist.get(), i: -1 });
    });
  const rotate = Gesture.Rotation()
    .onStart((e) => {
      const i = drag.get().i >= 0 ? drag.get().i : pickAt(e.anchorX, e.anchorY);
      if (i < 0) return;
      const tw = twist.get();
      twist.set({ ...tw, i, r0: st.get()[i].rot });
      last.set(clock.get());
    })
    .onUpdate((e) => {
      const tw = twist.get();
      if (tw.i < 0) return;
      st.get()[tw.i].rot = tw.r0 + (e.rotation * 180) / Math.PI;
      last.set(clock.get());
    })
    .onEnd(() => {
      scheduleOnRN(buzz, 0);
    });
  const gestures = Gesture.Simultaneous(pan, pinch, rotate);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  const SONGS = [
    { title: 'Glass Garden', artist: 'Mira Sol', cols: ['#2ad4c4', '#3a5bff'] as const },
    { title: 'Night Bus Home', artist: 'Ode Kim', cols: ['#ffcf6a', '#ff5a8a'] as const },
    { title: 'Fizz - Remix', artist: 'Tavi, Rook', cols: ['#ff7ac8', '#7a3cff'] as const },
  ];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.topBtns, { top: T + 10 }]}>
        <View style={styles.roundBtn} accessible accessibilityRole="button" accessibilityLabel="Message">
          <Text style={styles.btnGlyph} allowFontScaling={false}>
            ◌
          </Text>
        </View>
        <View style={styles.roundBtn} accessible accessibilityRole="button" accessibilityLabel="More">
          <Text style={styles.btnGlyph} allowFontScaling={false}>
            ⋮
          </Text>
        </View>
      </View>
      <View style={[styles.who, { top: T + 214 }]} pointerEvents="none">
        <Text style={styles.name} allowFontScaling={false}>
          Rio Vega
        </Text>
        <Text style={styles.handle} allowFontScaling={false}>
          @riovega
        </Text>
        <View style={styles.friends}>
          <LinearGradient colors={['#ffb36a', '#ff5a8a']} style={styles.friendDot} />
          <Text style={styles.friendsText} allowFontScaling={false}>
            1 friend
          </Text>
        </View>
      </View>
      <LinearGradient colors={['#3a1170', '#7a2fc9', '#b04cff']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[styles.banner, { top: BANNER_Y }]}>
        <Text style={styles.bannerTitle} allowFontScaling={false}>
          Music Compatibility
        </Text>
        <Text style={styles.bannerSub} allowFontScaling={false}>
          Discover your score!
        </Text>
        <View style={styles.qBox}>
          <Text style={styles.q} allowFontScaling={false}>
            ?
          </Text>
        </View>
      </LinearGradient>
      <View style={[styles.list, { top: BANNER_Y + 92 }]}>
        <View style={styles.listHead}>
          <Text style={styles.listTitle} allowFontScaling={false}>
            Recently played
          </Text>
          <Text style={styles.seeMore} allowFontScaling={false}>
            See more
          </Text>
        </View>
        {SONGS.map((sg) => (
          <View key={sg.title} style={styles.row} accessible accessibilityLabel={`${sg.title} by ${sg.artist}, 4 hours ago`}>
            <LinearGradient colors={sg.cols} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.cover}>
              <View style={styles.coverDisc} />
            </LinearGradient>
            <View style={styles.rowText}>
              <Text style={styles.song} allowFontScaling={false}>
                {sg.title}
              </Text>
              <Text style={styles.artist} allowFontScaling={false}>
                {sg.artist}
              </Text>
            </View>
            <View style={styles.ago}>
              <Text style={styles.agoText} allowFontScaling={false}>
                4 hours ago
              </Text>
            </View>
          </View>
        ))}
      </View>
      <GestureDetector gesture={gestures}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Profile stickers: a bun, a heart, a slay label and a flame. Drag one to peel it off and stick it somewhere else; use two fingers to turn or resize it.">
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <Pressable accessibilityRole="button" accessibilityLabel="Close" onPress={() => buzz(2)} style={({ pressed }) => [styles.close, { bottom: insets.bottom + 14 }, pressed && styles.pressed]}>
        <Text style={styles.closeGlyph} allowFontScaling={false}>
          ✕
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#151517' },
  topBtns: { position: 'absolute', right: 18, flexDirection: 'row', gap: 10 },
  roundBtn: { width: 42, height: 42, borderRadius: 14, backgroundColor: '#2a2a2e', alignItems: 'center', justifyContent: 'center' },
  btnGlyph: { color: '#ffffff', fontSize: 18, fontFamily: 'SPP-Bold' },
  who: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  name: { color: '#ffffff', fontSize: 24, fontFamily: 'SPP-Bold' },
  handle: { color: 'rgba(255,255,255,0.55)', fontSize: 14, fontFamily: 'SPP-Medium', marginTop: 2 },
  friends: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 18, height: 44, paddingHorizontal: 14, borderRadius: 14, backgroundColor: '#242427' },
  friendDot: { width: 26, height: 26, borderRadius: 8 },
  friendsText: { color: '#ffffff', fontSize: 15, fontFamily: 'SPP-SemiBold' },
  banner: { position: 'absolute', left: 18, right: 18, height: 70, borderRadius: 16, paddingHorizontal: 16, justifyContent: 'center', overflow: 'hidden' },
  bannerTitle: { color: '#ffffff', fontSize: 18, fontFamily: 'SPP-Bold' },
  bannerSub: { color: 'rgba(255,255,255,0.75)', fontSize: 13, fontFamily: 'SPP-Medium', marginTop: 2 },
  qBox: { position: 'absolute', right: 26, top: 8, width: 52, height: 60, borderRadius: 10, backgroundColor: '#5a1fa8', alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '6deg' }] },
  q: { color: '#1a0838', fontSize: 34, fontFamily: 'SPP-Bold' },
  list: { position: 'absolute', left: 18, right: 18, gap: 14 },
  listHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  listTitle: { color: 'rgba(255,255,255,0.6)', fontSize: 15, fontFamily: 'SPP-SemiBold' },
  seeMore: { color: '#ffffff', fontSize: 15, fontFamily: 'SPP-SemiBold' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  cover: { width: 52, height: 52, borderRadius: 6, alignItems: 'center', justifyContent: 'center' },
  coverDisc: { width: 26, height: 26, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.35)' },
  rowText: { flex: 1 },
  song: { color: '#ffffff', fontSize: 16, fontFamily: 'SPP-Medium' },
  artist: { color: 'rgba(255,255,255,0.55)', fontSize: 14, fontFamily: 'SPP-Medium', marginTop: 2 },
  ago: { height: 30, paddingHorizontal: 12, borderRadius: 15, backgroundColor: '#242427', justifyContent: 'center' },
  agoText: { color: 'rgba(255,255,255,0.7)', fontSize: 12, fontFamily: 'SPP-SemiBold' },
  close: { position: 'absolute', alignSelf: 'center', width: 56, height: 56, borderRadius: 18, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  closeGlyph: { color: '#151517', fontSize: 20, fontFamily: 'SPP-Bold' },
  pressed: { transform: [{ scale: 0.96 }] },
});

export { LOOP };
