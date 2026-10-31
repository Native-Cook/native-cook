import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
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

import { type Cue, useWfaAudio } from './use-wfa-audio';

/*
 Wavy Frame Auction — the first screen of an invented NFT auction app.

 Look (the pin, a still of three screens; this is the first): a near-black page, an artwork in a
 dark frame whose edges ripple like a postage stamp's, "Perhaps The Most Convenient NFT Auction" with
 lime highlights, and a lime tab at the bottom with a wavy top and up-chevrons. The pin's palette is
 #21251F, #C8F560, #4E515C and #7862F8; the pin's artworks are illustrations, ours is a generative
 piece drawn by a shader.

 Motion (ours): swipe the lime tab up and the screen unfolds into the auction: the frame grows to
 fill the top of the screen while its ripples smooth out into straight edges, the artwork's flow
 speeds up, the headline gives way to the piece's title, creator, bid and a ticking countdown, and
 the tab's wavy top flattens into a Place a Bid pill. Swipe down to fold it back. The wave is part of
 the frame's path at every moment, so the morph never cuts.

 autoPlay: LOOP = 9 s. 1.2-2.2 s the tab is pulled up, 2.2 s let go and it unfolds; 5.8-6.8 s it
 folds back. Frame 9 s equals frame 0. Reduced motion: the screen switches with a short fade.
*/

let debugState = '';
const LOOP = 9;

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
/** The script's unfold progress at loop time t. */
const scriptS = (t: number) => {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 2.2) return 0.42 * easeInOut((t - 1.2) / 1.0);
  if (t < 5.8) {
    // let go: a spring-like settle to 1
    const k = (t - 2.2) / 0.7;
    if (k >= 1) return 1;
    const x = clamp01(k);
    return mix(0.42, 1, 1 - Math.pow(1 - x, 3) + Math.sin(x * Math.PI) * 0.06);
  }
  if (t < 6.8) return 1 - easeInOut((t - 5.8) / 1.0);
  return 0;
};

/** How lifted the frame is at loop time t: up while the script holds it (1.2-2.2 s, 5.8-6.8 s). */
const scriptLift = (t: number) => {
  'worklet';
  let l = 0;
  const wins = [1.2, 2.2, 5.8, 6.8];
  for (let i = 0; i < 4; i += 2) {
    const a = wins[i];
    const b = wins[i + 1];
    if (t >= a && t < b) l = Math.max(l, 1 - Math.exp(-(t - a) * 10));
    else if (t >= b) l = Math.max(l, (1 - Math.exp(-(b - a) * 10)) * Math.exp(-(t - b) * 10));
  }
  return l;
};

// the generative piece: domain-warped noise in the pin's palette, with grain
const ART = Skia.RuntimeEffect.Make(`
uniform float2 o;
uniform float2 sz;
uniform float ph;
uniform float flow;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { s += a * vnoise(p); p = p * 2.02 + 3.1; a *= 0.5; } return s; }
half4 main(float2 xy) {
  float2 uv = (xy - o) / sz.y * 2.4;
  float2 drift = float2(cos(ph), sin(ph)) * (0.5 + 0.6 * flow);
  float2 q = float2(fbm(uv + drift), fbm(uv + float2(5.2, 1.3) - drift));
  float2 r = float2(fbm(uv + 3.0 * q + float2(1.7, 9.2) + drift * 0.6), fbm(uv + 3.0 * q + float2(8.3, 2.8)));
  float f = fbm(uv + 3.5 * r);
  float3 deep = float3(0.13, 0.145, 0.12);
  float3 violet = float3(0.47, 0.38, 0.97);
  float3 lime = float3(0.78, 0.96, 0.38);
  float3 pink = float3(1.0, 0.44, 0.71);
  float3 amber = float3(1.0, 0.7, 0.3);
  float3 col = mix(deep, violet, smoothstep(0.2, 0.6, f));
  col = mix(col, pink, smoothstep(0.45, 0.8, length(q)) * 0.75);
  col = mix(col, lime, smoothstep(0.62, 0.9, r.x) * 0.85);
  col = mix(col, amber, smoothstep(0.7, 0.95, f * r.y * 1.6) * 0.6);
  // flowing contour lines
  col += 0.08 * smoothstep(0.45, 0.5, fract(f * 9.0)) * (1.0 - smoothstep(0.5, 0.55, fract(f * 9.0)));
  col += (hash(floor(xy)) - 0.5) * 0.06;
  return half4(half3(col), 1.0);
}`);

type Props = { autoPlay?: boolean };

export function WavyFrameAuction({ autoPlay = false }: Props) {
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
    'WFA-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'WFA-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'WFA-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const T = insets.top;
  const B = insets.bottom;

  const play = useWfaAudio(!reduced);
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
  const un = useSharedValue({ s: 0, v: 0, target: 0, held: 0, start: 0, lift: 0 });
  const debugT = useSharedValue(-1);
  const [open, setOpen] = useState(false);
  const [secs, setSecs] = useState(22 * 3600 + 52 * 60 + 34);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __wavyFrame?: unknown };
    g.__wavyFrame = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__wavyFrame;
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
    const u = un.get();
    let t = time.get();
    if (dbg >= 0 || dbg === -2) {
      if (dbg >= 0) {
        t = dbg;
        debugT.set(-2);
      }
      u.s = scriptS(t);
    } else if (auto && u.held === 0 && now - last.get() >= 2) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
      u.s = scriptS(t);
      u.target = u.s > 0.5 ? 1 : 0;
      u.v = 0;
    } else if (u.held === 0) {
      const k = reduced ? 600 : 170;
      u.v += (k * (u.target - u.s) - 2 * Math.sqrt(k) * 0.72 * u.v) * dt;
      u.s += u.v * dt;
    }
    // the frame lifts off the page while it is held, like a stamp picked up
    if (u.held === 1) u.lift += (1 - u.lift) * (1 - Math.exp(-dt * 10));
    else if (!reduced && (dbg === -2 || (auto && now - last.get() >= 2))) u.lift = scriptLift(t);
    else u.lift += (0 - u.lift) * (1 - Math.exp(-dt * 10));
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} s=${u.s.toFixed(3)}`);
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
      return un.get().s > 0.6;
    },
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setOpen, n);
    },
  );
  // the countdown ticks while the auction is open
  useEffect(() => {
    if (!open) return;
    const id = setInterval(() => setSecs((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(id);
  }, [open]);

  /** The frame's rectangle at progress s: a card in the middle, then the top of the screen. */
  const frameRect = (s: number) => {
    'worklet';
    const k = smooth(s);
    const cw = mix(250, w - 32, k);
    const chh = mix(300, h * 0.56, k);
    const cy = mix(T + 230, T + 64 + chh / 2, k);
    return { x: w / 2 - cw / 2, y: cy - chh / 2, w: cw, h: chh, r: mix(30, 28, k) };
  };

  /** A rounded rectangle whose edges ripple with amplitude a (pt), n waves per edge. */
  const wavyRect = (x: number, y: number, rw: number, rh: number, r: number, a: number, n: number, ph: number) => {
    'worklet';
    const pb = Skia.PathBuilder.Make();
    const per = 2 * (rw + rh - 4 * r) + 2 * Math.PI * r;
    const steps = 220;
    for (let i = 0; i <= steps; i++) {
      const d = (i / steps) * per;
      // walk the rounded rectangle's perimeter, starting at the top-left straight edge
      let px = 0;
      let py = 0;
      let nx = 0;
      let ny = 0;
      let wave = 0;
      const top = rw - 2 * r;
      const side = rh - 2 * r;
      const arc = (Math.PI / 2) * r;
      let dd = d;
      if (dd < top) {
        px = x + r + dd;
        py = y;
        ny = -1;
        wave = dd / top;
      } else if ((dd -= top) < arc) {
        const an = -Math.PI / 2 + dd / r;
        px = x + rw - r + Math.cos(an) * r;
        py = y + r + Math.sin(an) * r;
        nx = Math.cos(an);
        ny = Math.sin(an);
        wave = -1;
      } else if ((dd -= arc) < side) {
        px = x + rw;
        py = y + r + dd;
        nx = 1;
        wave = dd / side;
      } else if ((dd -= side) < arc) {
        const an = dd / r;
        px = x + rw - r + Math.cos(an) * r;
        py = y + rh - r + Math.sin(an) * r;
        nx = Math.cos(an);
        ny = Math.sin(an);
        wave = -1;
      } else if ((dd -= arc) < top) {
        px = x + rw - r - dd;
        py = y + rh;
        ny = 1;
        wave = dd / top;
      } else if ((dd -= top) < arc) {
        const an = Math.PI / 2 + dd / r;
        px = x + r + Math.cos(an) * r;
        py = y + rh - r + Math.sin(an) * r;
        nx = Math.cos(an);
        ny = Math.sin(an);
        wave = -1;
      } else if ((dd -= arc) < side) {
        px = x;
        py = y + rh - r - dd;
        nx = -1;
        wave = dd / side;
      } else {
        dd -= side;
        const an = Math.PI + Math.min(dd, arc) / r;
        px = x + r + Math.cos(an) * r;
        py = y + r + Math.sin(an) * r;
        nx = Math.cos(an);
        ny = Math.sin(an);
        wave = -1;
      }
      // ripples on the straight edges, fading to nothing at the corners
      const o = wave >= 0 ? a * Math.sin(wave * n * 2 * Math.PI + ph) * Math.sin(wave * Math.PI) : 0;
      if (i === 0) pb.moveTo(px + nx * o, py + ny * o);
      else pb.lineTo(px + nx * o, py + ny * o);
    }
    pb.close();
    return pb.build();
  };

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = un.get().s;
    const t = time.get();
    const ph = (2 * Math.PI * (auto ? t : clock.get())) / LOOP;
    const f = frameRect(s);
    const amp = mix(9, 0, smooth(s * 1.25));
    // lifted: a soft shadow under it, a little bigger and turned a couple of degrees
    const lift = un.get().lift;
    c.save();
    c.translate(f.x + f.w / 2, f.y + f.h / 2);
    c.rotate(-2.2 * lift, 0, 0);
    c.scale(1 + 0.025 * lift, 1 + 0.025 * lift);
    c.translate(-(f.x + f.w / 2), -(f.y + f.h / 2));
    if (lift > 0.01) {
      const sh = Skia.Paint();
      sh.setColor(Skia.Color('#000000'));
      sh.setAlphaf(0.55 * lift);
      sh.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 22, true));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(f.x, f.y + 18 * lift, f.w, f.h), 30, 30), sh);
    }
    // the dark frame, rippling, then the artwork inside it
    const outer = wavyRect(f.x - 12, f.y - 12, f.w + 24, f.h + 24, f.r + 10, amp * 1.15, 5, ph * 2);
    const fp = Skia.Paint();
    fp.setAntiAlias(true);
    fp.setColor(Skia.Color('#2b2f29'));
    c.drawPath(outer, fp);
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(1);
    edge.setStrokeWidth(1.5);
    edge.setColor(Skia.Color('#4e515c'));
    c.drawPath(outer, edge);
    const inner = wavyRect(f.x, f.y, f.w, f.h, f.r, amp, 5, ph * 2 + 0.6);
    if (ART) {
      c.save();
      c.clipPath(inner, 1, true);
      const ap = Skia.Paint();
      ap.setShader(ART.makeShader([f.x + f.w / 2, f.y + f.h / 2, f.w, f.h, ph, s]));
      c.drawRect(Skia.XYWHRect(f.x - 20, f.y - 20, f.w + 40, f.h + 40), ap);
      c.restore();
    }
    c.restore();
    // the tab: a wavy-topped lime tab that flattens into the bid pill
    const k = smooth(s);
    const tw = mix(150, w - 40, k);
    const th = mix(76, 58, k);
    const tx = w / 2 - tw / 2;
    const ty = h - B - mix(4, 22, k) - th;
    const tab = Skia.PathBuilder.Make();
    const waveA = mix(10, 0, k);
    const rr = mix(18, th / 2, k);
    tab.moveTo(tx, ty + rr);
    tab.quadTo(tx, ty, tx + rr, ty);
    for (let i = 0; i <= 30; i++) {
      const u = i / 30;
      const px = tx + rr + u * (tw - 2 * rr);
      const py = ty - waveA * Math.sin(u * Math.PI) * (0.6 + 0.4 * Math.cos(u * Math.PI * 4 + ph * 3));
      tab.lineTo(px, py);
    }
    tab.quadTo(tx + tw, ty, tx + tw, ty + rr);
    tab.lineTo(tx + tw, ty + th - rr * k);
    if (k > 0.01) tab.quadTo(tx + tw, ty + th, tx + tw - rr * k, ty + th);
    else tab.lineTo(tx + tw, ty + th + 40);
    tab.lineTo(tx + rr * k, ty + th + (k > 0.01 ? 0 : 40));
    if (k > 0.01) tab.quadTo(tx, ty + th, tx, ty + th - rr * k);
    tab.close();
    const tp = Skia.Paint();
    tp.setAntiAlias(true);
    tp.setColor(Skia.Color('#c8f560'));
    c.drawPath(tab.build(), tp);
    // chevrons on the tab
    const ch = 1 - smooth(s * 2.5);
    if (ch > 0.01) {
      const cp = Skia.Paint();
      cp.setAntiAlias(true);
      cp.setStyle(1);
      cp.setStrokeWidth(2.4);
      cp.setStrokeCap(1);
      cp.setStrokeJoin(1);
      cp.setColor(Skia.Color('#21251f'));
      cp.setAlphaf(ch);
      const bob = Math.sin(ph * 4) * 2;
      for (let j = 0; j < 2; j++) {
        const yy = ty + 28 + j * 10 + bob;
        const pc = Skia.PathBuilder.Make();
        pc.moveTo(w / 2 - 14, yy + 6);
        pc.lineTo(w / 2, yy);
        pc.lineTo(w / 2 + 14, yy + 6);
        c.drawPath(pc.build(), cp);
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, T, B]);

  // ---------------------------------------------------------------- copy
  const introStyle = useAnimatedStyle(() => {
    tick.get();
    const k = 1 - smooth(un.get().s * 2.2);
    return { opacity: k, transform: [{ translateY: (1 - k) * 24 }] };
  });
  const detailStyle = useAnimatedStyle(() => {
    tick.get();
    const k = smooth((un.get().s - 0.55) / 0.45);
    return { opacity: k, transform: [{ translateY: (1 - k) * 20 }] };
  });
  const bidStyle = useAnimatedStyle(() => {
    tick.get();
    return { opacity: smooth((un.get().s - 0.7) / 0.3) };
  });

  // ---------------------------------------------------------------- touch: swipe the tab up, or the page down
  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      const u = un.get();
      u.held = 1;
      u.start = u.s;
      last.set(clock.get());
    })
    .onUpdate((e) => {
      const u = un.get();
      last.set(clock.get());
      u.s = clamp01(u.start - e.translationY / 360);
    })
    .onFinalize((e) => {
      const u = un.get();
      last.set(clock.get());
      u.held = 0;
      u.v = -e.velocityY / 360;
      const proj = u.s + u.v * 0.18;
      const to = proj > 0.5 ? 1 : 0;
      if (to !== u.target) {
        scheduleOnRN(cue, to ? 'unfold' : 'fold');
        scheduleOnRN(buzz, 1);
      }
      u.target = to;
    });
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const toggleUI = () => {
    'worklet';
    const u = un.get();
    last.set(clock.get());
    u.target = u.target > 0.5 ? 0 : 1;
    u.v = 0;
    scheduleOnRN(cue, u.target ? 'unfold' : 'fold');
    scheduleOnRN(buzz, 1);
  };
  const toggle = () => scheduleOnUI(toggleUI);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const hh = Math.floor(secs / 3600);
  const mm = Math.floor((secs % 3600) / 60);
  const ss = secs % 60;
  const pad = (n: number) => `${n}`.padStart(2, '0');
  const detailTop = T + 64 + h * 0.56 + 22;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel={open ? 'Lumen Tide number 42, current bid 0.34 ETH. Swipe down to close.' : 'Swipe up to open the auction'}
          onAccessibilityTap={toggle}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
          <Animated.View style={[styles.intro, { top: T + 430 }, introStyle]} pointerEvents="none">
            <Text style={styles.headline} allowFontScaling={false}>
              <Text style={styles.hl}> Perh</Text>aps The
            </Text>
            <Text style={styles.headline} allowFontScaling={false}>
              Most <Text style={styles.hlSoft}>Convenient</Text>
            </Text>
            <Text style={styles.headline} allowFontScaling={false}>
              NFT Auction
            </Text>
          </Animated.View>
          <Animated.View style={[styles.detail, { top: detailTop }, detailStyle]} pointerEvents="none">
            <View style={styles.creator}>
              <View style={styles.avatar} />
              <View>
                <Text style={styles.piece} allowFontScaling={false}>
                  Lumen Tide #042
                </Text>
                <Text style={styles.by} allowFontScaling={false}>
                  @nadia.studio
                </Text>
              </View>
            </View>
            <View style={styles.bidRow}>
              <View style={styles.chip}>
                <Text style={styles.chipText} allowFontScaling={false}>
                  0.34 ETH
                </Text>
              </View>
              <View style={[styles.chip, styles.chipDark]}>
                <Text style={[styles.chipText, styles.chipTextLight]} allowFontScaling={false}>
                  $1,284
                </Text>
              </View>
              <Text style={styles.ends} allowFontScaling={false}>
                Ends in {pad(hh)} : {pad(mm)} : {pad(ss)}
              </Text>
            </View>
          </Animated.View>
          <Animated.Text style={[styles.bidText, { bottom: B + 22 + 18 }, bidStyle]} allowFontScaling={false}>
            Place a Bid
          </Animated.Text>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1a1d18' },
  intro: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  headline: { color: '#ffffff', fontSize: 32, lineHeight: 40, fontFamily: 'WFA-Title', textAlign: 'center' },
  hl: { backgroundColor: '#c8f560', color: '#21251f' },
  hlSoft: { color: '#c8f560' },
  detail: { position: 'absolute', left: 22, right: 22, gap: 16 },
  creator: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: { width: 42, height: 42, borderRadius: 21, backgroundColor: '#7862f8', borderWidth: 2, borderColor: '#c8f560' },
  piece: { color: '#ffffff', fontSize: 22, fontFamily: 'WFA-Title' },
  by: { color: 'rgba(255,255,255,0.55)', fontSize: 13, fontFamily: 'WFA-Medium', marginTop: 2 },
  bidRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  chip: { height: 32, paddingHorizontal: 12, borderRadius: 16, backgroundColor: '#c8f560', justifyContent: 'center' },
  chipDark: { backgroundColor: '#4e515c' },
  chipText: { color: '#21251f', fontSize: 14, fontFamily: 'WFA-SemiBold' },
  chipTextLight: { color: '#ffffff' },
  ends: { marginLeft: 'auto', color: 'rgba(255,255,255,0.75)', fontSize: 13, fontFamily: 'WFA-Medium', fontVariant: ['tabular-nums'] },
  bidText: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#21251f', fontSize: 17, fontFamily: 'WFA-SemiBold' },
});

export { LOOP };
