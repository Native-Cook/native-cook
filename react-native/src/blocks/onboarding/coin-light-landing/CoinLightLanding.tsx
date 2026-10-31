import { Canvas, Fill, Picture, Shader, Skia } from '@shopify/react-native-skia';
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

import { clamp01, COIN, easeInOut, easeOut, easeOutBack, LOOP, mix } from './cll-coin';
import { type Cue, useCllAudio } from './use-cll-audio';

/*
 Coin Light Landing — the creator page of an invented marketplace, Plinth.

 Look (the pin, a still of a real company's page; ours is invented): a black page, the mark and a
 menu, "Get paid to create", four benefit rows with small icons, a white and a dark pill button, and
 a glowing gold coin tilted at the bottom.

 Motion (ours): the coin is the page's only light. Drag it and whatever is near it lights up warm,
 the rest sinks into shadow; the buttons' rims catch the light on the side facing it. Let go and it
 floats home; flick it and it spins. Drop it on Become a creator and it sinks into the button, which
 lights up gold, and the payout figure counts up; then a coin rises again from the bottom.

 autoPlay: LOOP = 8 s. 1.0-3.3 s the coin is carried up through the benefits and onto the button,
 3.3 s it sinks in, 3.4-4.4 s the figure counts, 4.6-5.4 s a coin rises home, 5.8-7.2 s it spins two
 turns, 7.2-7.7 s the figure dips back. Frame 8 s equals frame 0. Reduced motion: no autoplay; the page
 is evenly lit and the coin moves without trails or spin.
*/

let debugState = '';
const BASE = 733000;
const GAIN = 48;

type Props = { autoPlay?: boolean };

export function CoinLightLanding({ autoPlay = false }: Props) {
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
    'CLL-Display': require('./assets/fonts/InterDisplay-Bold.ttf'),
    'CLL-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'CLL-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'CLL-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const T = insets.top;
  const R = 70; // coin radius
  const REST = { x: w / 2, y: h * 0.74 };
  const ROWS_Y = T + 168; // first benefit row's centre
  const ROW_H = 40;
  const BTN = { x: 22 + 82, y: T + 168 + 4 * ROW_H + 26 }; // Become a creator's centre
  const BTN_W = 164;

  const play = useCllAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  // the coin: position, velocity, held, spin (yaw) and its speed, alpha/scale while it sinks into the button
  const coin = useSharedValue({ x: 0, y: 0, vx: 0, vy: 0, held: 0, gx: 0, gy: 0, yaw: 0, vyaw: 0, a: 1, s: 1, mode: 0, at: 0, tiltX: 0, tiltY: 0 });
  // mode 0 free, 1 sinking into the button, 2 rising home again
  const pay = useSharedValue({ v: BASE, from: BASE, at: -10, btn: 0 });
  const debugT = useSharedValue(-1);
  const [paid, setPaid] = useState(BASE);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __coinLight?: unknown };
    g.__coinLight = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__coinLight;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** The autoplay's coin at loop time t. */
  const scriptCoin = (t: number) => {
    'worklet';
    const bob = Math.sin((2 * Math.PI * 2 * t) / LOOP) * 4;
    let x = REST.x;
    let y = REST.y + bob;
    let a = 1;
    let s = 1;
    let yaw = 0.12 * Math.sin((2 * Math.PI * t) / LOOP);
    let tx = 0;
    if (t >= 1.0 && t < 3.3) {
      // up through the benefits (left, then right), then onto the button
      const P = [
        [REST.x, REST.y],
        [w * 0.3, ROWS_Y + ROW_H * 2.6],
        [w * 0.72, ROWS_Y + ROW_H * 0.6],
        [w * 0.55, ROWS_Y - 10],
        [BTN.x, BTN.y],
      ];
      const k = easeInOut((t - 1.0) / 2.3) * 4;
      const i = Math.min(3, Math.floor(k));
      const u = k - i;
      const p0 = P[Math.max(0, i - 1)];
      const p1 = P[i];
      const p2 = P[i + 1];
      const p3 = P[Math.min(4, i + 2)];
      const u2 = u * u;
      const u3 = u2 * u;
      x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * u + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * u2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * u3);
      y = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * u + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * u2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * u3);
      tx = (p2[0] - p1[0]) * 0.002;
    } else if (t >= 3.3 && t < 4.6) {
      const k = easeOut((t - 3.3) / 0.3);
      x = BTN.x;
      y = BTN.y;
      s = mix(1, 0.3, k);
      a = 1 - k;
    } else if (t >= 4.6 && t < 5.4) {
      const k = easeOutBack((t - 4.6) / 0.8);
      y = mix(h + R * 2, REST.y + bob, k);
    } else if (t >= 5.8 && t < 7.2) {
      yaw += 4 * Math.PI * easeOut((t - 5.8) / 1.4);
    }
    return { x, y, a, s, yaw, tx };
  };
  /** The payout figure at loop time t. */
  const scriptPay = (t: number) => {
    'worklet';
    if (t >= 3.4 && t < 7.45) return BASE + GAIN * easeInOut((t - 3.4) / 1.0); // 7.45: the dip's lowest point
    return BASE;
  };
  const scriptBtn = (t: number) => {
    'worklet';
    return t >= 3.3 ? Math.exp(-Math.max(0, t - 3.4) * 1.6) * (t < 3.4 ? (t - 3.3) / 0.1 : 1) : 0;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const cn = coin.get();
    const py = pay.get();
    let t = time.get();
    const scripted = dbg >= 0 || dbg === -2 || (auto && cn.held === 0 && cn.mode === 0 && now - last.get() >= 2);
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
    } else if (scripted && dbg !== -2) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    if (scripted) {
      const sc = scriptCoin(t);
      cn.x = sc.x;
      cn.y = sc.y;
      cn.a = sc.a;
      cn.s = sc.s;
      cn.yaw = sc.yaw;
      cn.tiltX = sc.tx;
      cn.vx = 0;
      cn.vy = 0;
      cn.vyaw = 0;
      py.v = scriptPay(t);
      py.btn = scriptBtn(t);
    } else {
      if (cn.x === 0 && cn.y === 0) {
        cn.x = REST.x;
        cn.y = REST.y;
      }
      if (cn.mode === 1) {
        // sinking into the button, then a new coin rises home
        const k = easeOut((now - cn.at) / 0.3);
        cn.x = mix(cn.x, BTN.x, 0.35);
        cn.y = mix(cn.y, BTN.y, 0.35);
        cn.s = mix(1, 0.3, k);
        cn.a = 1 - k;
        if (now - cn.at > 1.3) {
          cn.mode = 2;
          cn.at = now;
          cn.x = REST.x;
          cn.y = h + R * 2;
          cn.a = 1;
          cn.s = 1;
        }
      } else if (cn.mode === 2) {
        const k = easeOutBack((now - cn.at) / 0.8);
        cn.y = mix(h + R * 2, REST.y, k);
        if (now - cn.at >= 0.8) cn.mode = 0;
      } else if (cn.held === 0) {
        // home on a soft spring, keeping the release velocity
        const k = reduced ? 300 : 90;
        const z = 0.62;
        cn.vx += (k * (REST.x - cn.x) - 2 * Math.sqrt(k) * z * cn.vx) * dt;
        cn.vy += (k * (REST.y - cn.y) - 2 * Math.sqrt(k) * z * cn.vy) * dt;
        cn.x += cn.vx * dt;
        cn.y += cn.vy * dt;
      }
      // the spin decays; it settles face-on
      cn.vyaw *= Math.exp(-dt * 1.6);
      cn.yaw += cn.vyaw * dt;
      if (Math.abs(cn.vyaw) < 0.6) {
        const target = Math.round(cn.yaw / (2 * Math.PI)) * 2 * Math.PI;
        cn.yaw += (target - cn.yaw) * (1 - Math.exp(-dt * 4));
      }
      cn.tiltX += (Math.max(-0.5, Math.min(0.5, cn.vx * 0.0012)) - cn.tiltX) * (1 - Math.exp(-dt * 10));
      // the payout figure counts after a drop
      py.v = py.at > 0 ? mix(py.from, py.from + GAIN, easeInOut((now - py.at - 0.1) / 1.0)) : py.v;
      py.btn = py.at > 0 ? Math.exp(-Math.max(0, now - py.at - 0.1) * 1.6) : 0;
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} x=${cn.x.toFixed(0)} y=${cn.y.toFixed(0)} mode=${cn.mode} pay=${py.v.toFixed(0)}`);
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
      return Math.round(pay.get().v);
    },
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setPaid, n);
    },
  );

  // ---------------------------------------------------------------- the light the coin casts over the page
  const light = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const cn = coin.get();
    const py = pay.get();
    // the light sits where the coin is; while the coin is inside the button the button is the light
    const lx = mix(BTN.x, cn.x, cn.a);
    const ly = mix(BTN.y, cn.y, cn.a);
    const power = reduced ? 0 : Math.max(cn.a, py.btn);
    // darkness everywhere the light does not reach (alpha over the page)
    const dark = Skia.Paint();
    dark.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: lx, y: ly },
        mix(220, 360, power),
        [Skia.Color('rgba(5,5,6,0)'), Skia.Color('rgba(5,5,6,0.2)'), Skia.Color('rgba(5,5,6,0.48)')],
        [0, 0.55, 1],
        0,
      ),
    );
    dark.setAlphaf(reduced ? 0 : 1);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), dark);
    // a warm wash near the light
    const warm = Skia.Paint();
    warm.setShader(Skia.Shader.MakeRadialGradient({ x: lx, y: ly }, 190, [Skia.Color('rgba(255,170,70,0.22)'), Skia.Color('rgba(255,170,70,0)')], null, 0));
    warm.setAlphaf(power);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), warm);
    // the buttons' rims catch the light on the side facing it
    const rims = [
      { x: BTN.x, y: BTN.y, w: BTN_W, hh: 44 },
      { x: BTN.x + BTN_W / 2 + 10 + 88, y: BTN.y, w: 176, hh: 44 },
    ];
    for (let i = 0; i < rims.length; i++) {
      const r = rims[i];
      const dx = lx - r.x;
      const dy = ly - r.y;
      const d = Math.max(1, Math.hypot(dx, dy));
      const k = clamp01(1 - d / 360) * power;
      if (k < 0.02) continue;
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setStyle(1);
      p.setStrokeWidth(1.6);
      p.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: r.x + (dx / d) * r.w * 0.5, y: r.y + (dy / d) * r.hh * 0.5 },
          { x: r.x - (dx / d) * r.w * 0.5, y: r.y - (dy / d) * r.hh * 0.5 },
          [Skia.Color('rgba(255,205,120,1)'), Skia.Color('rgba(255,205,120,0)')],
          [0, 0.7],
          0,
        ),
      );
      p.setAlphaf(k);
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r.x - r.w / 2, r.y - r.hh / 2, r.w, r.hh), r.hh / 2, r.hh / 2), p);
    }
    // the button lit up gold after a drop
    if (py.btn > 0.01) {
      const g = Skia.Paint();
      g.setColor(Skia.Color('#ffc24a'));
      g.setAlphaf(0.55 * py.btn);
      g.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 18, true));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(BTN.x - BTN_W / 2, BTN.y - 22, BTN_W, 44), 22, 22), g);
    }
    // the coin's own halo
    if (cn.a > 0.01) {
      const halo = Skia.Paint();
      halo.setShader(
        Skia.Shader.MakeRadialGradient({ x: cn.x, y: cn.y }, R * 2.6 * cn.s, [Skia.Color('rgba(255,190,90,0.55)'), Skia.Color('rgba(255,150,50,0.12)'), Skia.Color('rgba(255,150,50,0)')], [0, 0.45, 1], 0),
      );
      halo.setAlphaf(cn.a);
      c.drawRect(Skia.XYWHRect(0, 0, w, h), halo);
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, R]);

  const coinUniforms = useDerivedValue(() => {
    tick.get();
    const cn = coin.get();
    return { c: [cn.x, cn.y], R: R * cn.s, yaw: cn.yaw + cn.tiltX, pitch: 0.55, glow: pay.get().btn };
  });
  const coinStyle = useAnimatedStyle(() => {
    tick.get();
    return { opacity: coin.get().a };
  });
  // the payout row dips while its figure goes back to the start of the loop
  const payRowStyle = useAnimatedStyle(() => {
    const t = time.get();
    const k = t >= 7.2 && t < 7.7 && coin.get().held === 0 ? Math.sin(((t - 7.2) / 0.5) * Math.PI) : 0;
    return { opacity: 1 - k, transform: [{ translateY: k * 4 }] };
  });
  const btnStyle = useAnimatedStyle(() => {
    tick.get();
    const b = pay.get().btn;
    return { transform: [{ scale: 1 + 0.04 * b }], backgroundColor: `rgb(255,${Math.round(255 - 50 * b)},${Math.round(255 - 150 * b)})` };
  });

  // ---------------------------------------------------------------- touch
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const cn = coin.get();
      last.set(clock.get());
      time.set(0); // the loop resumes from its first frame, where the coin rests
      if (cn.mode !== 0 || Math.hypot(e.x - cn.x, e.y - cn.y) > R + 30) return;
      cn.held = 1;
      cn.gx = cn.x - e.x;
      cn.gy = cn.y - e.y;
      scheduleOnRN(cue, 'hum');
      scheduleOnRN(buzz, 0);
    })
    .onUpdate((e) => {
      const cn = coin.get();
      if (cn.held !== 1) return;
      last.set(clock.get());
      const nx = e.x + cn.gx;
      const ny = e.y + cn.gy;
      cn.vx = cn.vx * 0.5 + (nx - cn.x) * 30;
      cn.x = nx;
      cn.y = ny;
    })
    .onFinalize((e) => {
      const cn = coin.get();
      if (cn.held !== 1) return;
      last.set(clock.get());
      cn.held = 0;
      if (Math.abs(cn.x - BTN.x) < BTN_W / 2 + 10 && Math.abs(cn.y - BTN.y) < 50) {
        // dropped on Become a creator
        cn.mode = 1;
        cn.at = clock.get();
        const py = pay.get();
        py.from = py.v;
        py.at = clock.get();
        scheduleOnRN(cue, 'snap');
        scheduleOnRN(buzz, 2);
        return;
      }
      cn.vx = e.velocityX;
      cn.vy = e.velocityY;
      // a flick spins it
      if (!reduced && Math.abs(e.velocityX) > 500) {
        cn.vyaw = e.velocityX * 0.012;
        scheduleOnRN(cue, 'spin');
        scheduleOnRN(buzz, 1);
      }
    });
  const becomeUI = () => {
    'worklet';
    const cn = coin.get();
    last.set(clock.get());
    time.set(0);
    if (cn.mode !== 0) return;
    cn.mode = 1;
    cn.at = clock.get();
    const py = pay.get();
    py.from = py.v;
    py.at = clock.get();
    scheduleOnRN(cue, 'snap');
    scheduleOnRN(buzz, 2);
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const ROWS = [
    ['▣', 'Sell your work on the ', 'Plinth Market'],
    ['♥', 'Keep 100% of your ', 'Market earnings'],
    ['∞', 'Refer creators and get up to ', '50% commission'],
    ['$', `$${paid.toLocaleString('en-US')} paid to creators `, 'in October'],
  ];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.bar, { top: T + 10 }]}>
        <View style={styles.mark} accessible accessibilityLabel="Plinth">
          <View style={[styles.markBar, { width: 10 }]} />
          <View style={[styles.markBar, { width: 16 }]} />
          <View style={[styles.markBar, { width: 22 }]} />
        </View>
        <View style={styles.menu} accessible accessibilityRole="button" accessibilityLabel="Menu">
          <View style={styles.menuLine} />
          <View style={styles.menuLine} />
        </View>
      </View>
      <Text style={[styles.title, { top: T + 58 }]} allowFontScaling={false} accessibilityRole="header">
        Get paid{'\n'}to create
      </Text>
      {ROWS.map((r, i) => (
        <Animated.View key={i} style={[styles.row, { top: ROWS_Y + i * ROW_H - 12 }, i === 3 ? payRowStyle : null]} accessible accessibilityLabel={`${r[1]}${r[2]}`}>
          <View style={styles.rowIcon}>
            <Text style={styles.rowGlyph} allowFontScaling={false}>
              {r[0]}
            </Text>
          </View>
          <Text style={styles.rowText} allowFontScaling={false}>
            {r[1]}
            <Text style={styles.rowStrong}>{r[2]}</Text>
          </Text>
        </Animated.View>
      ))}
      <Animated.View style={[styles.primary, { left: BTN.x - BTN_W / 2, top: BTN.y - 22, width: BTN_W }, btnStyle]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Become a creator" onPress={() => scheduleOnUI(becomeUI)} style={styles.fill}>
          <Text style={styles.primaryText} allowFontScaling={false}>
            Become a creator
          </Text>
        </Pressable>
      </Animated.View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Watch the overview"
        onPress={() => buzz(0)}
        style={({ pressed }) => [styles.secondary, { left: BTN.x + BTN_W / 2 + 10, top: BTN.y - 22 }, pressed && styles.pressed]}
      >
        <Text style={styles.secondaryText} allowFontScaling={false}>
          Watch the overview
        </Text>
      </Pressable>
      <View style={[styles.domain, { bottom: insets.bottom + 18 }]} pointerEvents="none">
        <Text style={styles.domainText} allowFontScaling={false}>
          plinth.market
        </Text>
      </View>
      {size ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={light} />
        </Canvas>
      ) : null}
      {size && COIN ? (
        <Animated.View style={[StyleSheet.absoluteFill, coinStyle]} pointerEvents="none">
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={COIN} uniforms={coinUniforms} />
            </Fill>
          </Canvas>
        </Animated.View>
      ) : null}
      <GestureDetector gesture={pan}>
        <View
          style={[styles.coinHit, { left: REST.x - 90, top: REST.y - 90 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Gold coin. Drag it around to light the page, or drop it on Become a creator."
          onAccessibilityTap={() => scheduleOnUI(becomeUI)}
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#050506' },
  bar: { position: 'absolute', left: 22, right: 22, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  mark: { gap: 3, alignItems: 'center' },
  markBar: { height: 5, borderRadius: 1.5, backgroundColor: '#ffffff' },
  menu: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', gap: 5 },
  menuLine: { width: 20, height: 2, borderRadius: 1, backgroundColor: '#ffffff' },
  title: { position: 'absolute', left: 22, color: '#ffffff', fontSize: 36, lineHeight: 40, fontFamily: 'CLL-Display', letterSpacing: -0.8 },
  row: { position: 'absolute', left: 22, right: 22, flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  rowIcon: { width: 18, height: 18, borderRadius: 4, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center', marginTop: 2 },
  rowGlyph: { color: '#050506', fontSize: 11, lineHeight: 13, fontFamily: 'CLL-SemiBold' },
  rowText: { flex: 1, color: 'rgba(255,255,255,0.6)', fontSize: 15, lineHeight: 20, fontFamily: 'CLL-Regular' },
  rowStrong: { color: '#ffffff', fontFamily: 'CLL-Medium' },
  primary: { position: 'absolute', height: 44, borderRadius: 22, backgroundColor: '#ffffff' },
  fill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  primaryText: { color: '#050506', fontSize: 15, fontFamily: 'CLL-SemiBold' },
  secondary: { position: 'absolute', height: 44, width: 176, borderRadius: 22, backgroundColor: '#1d1d21', alignItems: 'center', justifyContent: 'center' },
  secondaryText: { color: '#ffffff', fontSize: 15, fontFamily: 'CLL-SemiBold' },
  domain: { position: 'absolute', alignSelf: 'center', height: 30, paddingHorizontal: 14, borderRadius: 15, backgroundColor: '#17171a', justifyContent: 'center' },
  domainText: { color: 'rgba(255,255,255,0.75)', fontSize: 12, fontFamily: 'CLL-Medium' },
  coinHit: { position: 'absolute', width: 180, height: 180 },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
