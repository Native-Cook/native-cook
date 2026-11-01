import { BlurStyle, Canvas, Path, Picture, processTransform3d, Skia, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { LOOP, makeOdo, type Odo, S, scriptTilt, seg, START, stepOdo, TICK, ticksAt } from './hcc-motion';
import { type Cue, useHccAudio } from './use-hcc-audio';

/*
 Holo Cash Card — the home screen of an invented fintech app.

 Look (the pin, a still): a light grey page with a black card whose huge condensed wordmark is
 chrome with a holographic sheen, "YOUR BANK WON'T DO THIS" under it; a white balance card with the
 rate on a magenta tag and the balance in big bold digits whose last three, grey, are still
 accruing, a yellow BOOST sticker slapped on it; Add Balance and Withdraw; a loyalty card; a tab bar
 with a pink SEND button and a REFER AND EARN sticker. The pin's wordmark and card network are a real
 company's; ours are invented (LOUDCASH, "debit").

 Motion (ours): the card tilts under the finger and its wordmark is lit by a shader whose rainbow and
 highlight band follow the tilt; let go and it wobbles back. The balance accrues live: its last digit
 rolls up every 0.3 s and carries like an odometer. Tap BOOST and the rate rolls from 3 % to 8 %, the
 digits tick faster and the wordmark glows. Add Balance adds $50 with every column rolling to its new
 digit. The eye hides the balance.

 autoPlay: LOOP = 8 s. 1.0-2.4 s a finger tilts the card and lets go; 3.2 s boost on; 6.4 s boost off;
 7.25-7.75 s the accrued digits dip out and come back at 313. Frame 8 s equals frame 0.
 Reduced motion: no tilt from autoplay, digits change without rolling.
*/

let debugState = '';

const HOLO = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float2 tilt;   // radians about x and y
uniform float glow;    // 0..1 boost glow
float3 hue(float h) {
  // a pastel foil palette: pink, lilac, sky, mint, butter
  float3 a = float3(1.0, 0.70, 0.90);
  float3 b = float3(0.72, 0.66, 1.0);
  float3 c = float3(0.62, 0.92, 1.0);
  float3 d = float3(0.78, 1.0, 0.84);
  float3 e = float3(1.0, 0.95, 0.68);
  float x = fract(h) * 5.0;
  if (x < 1.0) return mix(a, b, x);
  if (x < 2.0) return mix(b, c, x - 1.0);
  if (x < 3.0) return mix(c, d, x - 2.0);
  if (x < 4.0) return mix(d, e, x - 3.0);
  return mix(e, a, x - 4.0);
}
half4 main(float2 xy) {
  float2 uv = xy / size;
  // chrome: bright sky above, a dark horizon a little under the middle, bright ground, moved by the tilt
  float y = uv.y + tilt.x * 0.9;
  float chrome = 0.86 - 0.62 * exp(-pow((y - 0.56) * 6.5, 2.0)) + 0.14 * smoothstep(0.62, 0.95, y);
  float3 foil = hue(uv.x * 0.9 + uv.y * 0.6 + tilt.y * 1.4 + tilt.x * 0.8);
  float3 col = mix(float3(chrome), foil * (0.55 + 0.5 * chrome), 0.62);
  // a sharp highlight band that slides with the tilt
  float band = exp(-pow((uv.x + uv.y * 0.45 - 0.62 - tilt.y * 1.6) * 9.0, 2.0));
  col += band * (0.5 + 0.2 * glow);
  // boosted: the foil gets richer rather than whiter
  col = mix(col, foil * 1.08, 0.28 * glow);
  return half4(half3(min(col, 1.0)), 1.0);
}`);
const RIM = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float2 tilt;
half4 main(float2 xy) {
  float2 uv = xy / size;
  float h = fract(uv.x * 0.7 - uv.y * 0.4 + tilt.y * 1.2 + tilt.x);
  float3 c = 0.5 + 0.5 * cos(6.2831 * (h + float3(0.0, 0.33, 0.67)));
  return half4(half3(mix(c, float3(1.0), 0.45)), 1.0);
}`);

// fine diagonal foil lines over the whole card, their rainbow sliding with the tilt (a holo card's
// "scratch" layer); strongest where the light band is
const LINES = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float2 tilt;
uniform float glow;
half4 main(float2 xy) {
  float2 uv = xy / size;
  float d = xy.x * 0.82 + xy.y * 0.57;
  float line = smoothstep(0.55, 1.0, sin(d * 1.15)) ;
  float h = fract(uv.x * 0.6 + uv.y * 0.3 + tilt.y * 1.3 + tilt.x * 0.9);
  float3 c = 0.5 + 0.5 * cos(6.2831 * (h + float3(0.0, 0.33, 0.67)));
  float band = exp(-pow((uv.x + uv.y * 0.45 - 0.62 - tilt.y * 1.6) * 3.0, 2.0));
  float a = line * (0.05 + 0.22 * band) * (1.0 + 0.6 * glow);
  return half4(half3(c * a), a);
}`);

const ICONS = {
  eye: 'M2 12c2.6-4.4 6-6.6 10-6.6S19.4 7.6 22 12c-2.6 4.4-6 6.6-10 6.6S4.6 16.4 2 12z M12 9a3 3 0 1 0 0.01 0',
  chat: 'M4 12a8 8 0 1 1 3.4 6.5L4 20l1.2-3.4A7.9 7.9 0 0 1 4 12z',
  down: 'M12 4v12 M6.5 11 12 16.5 17.5 11 M5 20h14',
  up: 'M12 20V8 M6.5 13 12 7.5 17.5 13 M5 4h14',
  info: 'M12 3a9 9 0 1 0 0.01 0 M12 11v6 M12 7.5v0.5',
  wallet: 'M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M3 7l12-3v3 M16 13.5h2',
  grid: 'M12 3l3 3-3 3-3-3z M6 9l3 3-3 3-3-3z M18 9l3 3-3 3-3-3z M12 15l3 3-3 3-3-3z',
  ring: 'M12 4a8 8 0 1 0 0.01 0 M12 8a4 4 0 1 0 0.01 0',
  check: 'M12 3a9 9 0 1 0 0.01 0 M8 12.5l2.8 2.8L16.5 9.5',
};
function Icon({ d, size = 22, color = '#111111', width = 1.9 }: { d: string; size?: number; color?: string; width?: number }) {
  const path = useMemo(() => Skia.Path.MakeFromSVGString(d), [d]);
  if (!path) return null;
  return (
    <Canvas style={{ width: size, height: size }}>
      <Path path={path} style="stroke" strokeWidth={width} strokeCap="round" strokeJoin="round" color={color} transform={[{ scale: size / 24 }]} />
    </Canvas>
  );
}

type Props = { autoPlay?: boolean };

export function HoloCashCard({ autoPlay = false }: Props) {
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
    'HCC-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'HCC-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
    'HCC-Bold': require('./assets/fonts/Inter-Bold.ttf'),
    'HCC-Anton': require('./assets/fonts/Anton-Regular.ttf'),
  });
  const w = size?.width ?? 402;
  const CW = w - 36;
  const CH = Math.round(CW / 1.6);
  const M = 26; // room round the card for its tilt and shadow
  const wordFont = useFont(require('./assets/fonts/Anton-Regular.ttf'), CH * 0.58);
  const tagFont = useFont(require('./assets/fonts/Inter-Bold.ttf'), 10);

  const play = useHccAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const tilt = useSharedValue({ rx: 0, ry: 0, vx: 0, vy: 0, on: 0, son: 0, tx: 0, ty: 0 }); // on: a finger, son: the script
  const money = useSharedValue({ v: START, acc: 0, boost: 0, boostK: 0, extra: 0, dip: 0 });
  const odo = useSharedValue<Odo>(makeOdo(START));
  const apy = useSharedValue({ d: 3, v: 3, vel: 0 }); // the rate's digit rolls 3 → 8
  const pop = useSharedValue({ at: -10 }); // sticker tap
  const hide = useSharedValue(0);
  const hideT = useSharedValue(0);
  const [hidden, setHidden] = useState(false);
  const [boosted, setBoosted] = useState(false);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __holoCash?: unknown };
    g.__holoCash = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__holoCash;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const setBoost = (on: boolean, now: number, hand: boolean) => {
    'worklet';
    const m = money.get();
    if ((m.boost === 1) === on) return;
    m.boost = on ? 1 : 0;
    pop.set({ at: now });
    scheduleOnRN(setBoosted, on);
    if (hand) {
      scheduleOnRN(cue, on ? 'boost' : 'tap');
      scheduleOnRN(buzz, on ? 2 : 0);
    }
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    let t = time.get();
    const tl = tilt.get();
    const m = money.get();
    const scripted = (dbg >= 0 || (auto && !(tl.on === 1 || now - last.get() < 2))) && dbg !== -2;
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
    } else if (scripted) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    if (scripted) {
      const st = scriptTilt(t);
      tl.son = st.on;
      tl.tx = st.rx;
      tl.ty = st.ry;
      const on = t >= S.boostOn && t < S.boostOff;
      setBoost(on, now, false);
      const target = START + m.extra + ticksAt(t);
      // the accrued digits dip out at the end of the loop and come back at the start value
      m.dip = Math.sin(seg(t, S.reset[0], S.reset[1]) * Math.PI);
      const mid = (S.reset[0] + S.reset[1]) / 2;
      if (t >= mid || t < S.reset[0]) {
        const v = t >= mid ? START + m.extra : target;
        if (t >= mid && m.v !== v) odo.set(makeOdo(v));
        m.v = v;
      } else m.v = target;
      if (dbg >= 0) odo.set(makeOdo(m.v));
    } else if (dbg !== -2) {
      m.dip = 0;
      m.acc += dt;
      const step = m.boost ? TICK.boost : TICK.base;
      while (m.acc >= step) {
        m.acc -= step;
        m.v += 1;
      }
    }
    // the card's tilt: a stiff spring while held, a wobbly one when let go
    const held = tl.on === 1 || tl.son === 1;
    const k = held ? 320 : 170;
    const z = held ? 0.9 : 0.32;
    const c = 2 * Math.sqrt(k) * z;
    const n = Math.max(1, Math.ceil(dt / (1 / 240)));
    const h = dt / n;
    for (let i = 0; i < n; i++) {
      tl.vx += (k * ((held ? tl.tx : 0) - tl.rx) - c * tl.vx) * h;
      tl.rx += tl.vx * h;
      tl.vy += (k * ((held ? tl.ty : 0) - tl.ry) - c * tl.vy) * h;
      tl.ry += tl.vy * h;
    }
    m.boostK += ((m.boost ? 1 : 0) - m.boostK) * (1 - Math.exp(-dt * 6));
    const a = apy.get();
    const ad = m.boost ? 8 : 3;
    if (reduced) a.v = ad;
    else {
      a.vel += (380 * (ad - a.v) - 2 * Math.sqrt(380) * 0.8 * a.vel) * dt;
      a.v += a.vel * dt;
    }
    if (reduced) odo.set(makeOdo(m.v));
    else stepOdo(odo.get(), m.v, dt);
    hide.set(hide.get() + (hideT.get() - hide.get()) * (1 - Math.exp(-dt * 14)));
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} v=${m.v} boost=${m.boost} rx=${tl.rx.toFixed(1)} ry=${tl.ry.toFixed(1)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ---------------------------------------------------------------- the card
  const card = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, CW + 2 * M, CH + 2 * M));
    const tl = tilt.get();
    const m = money.get();
    const tx = (tl.rx * Math.PI) / 180;
    const ty = (tl.ry * Math.PI) / 180;
    // the card is tilted in Skia (a RN perspective transform here broke the compositing on iOS):
    // its shadow first, flat and shifted away from the tilt, then the card in perspective
    c.translate(M, M);
    const sh = Skia.Paint();
    sh.setColor(Skia.Color('#000000'));
    sh.setAlphaf(0.26);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(8 - tl.ry * 0.5, 12 + tl.rx * 0.5, CW - 16, CH - 10), 16, 16), sh);
    c.concat(
      processTransform3d([
        { translateX: CW / 2 },
        { translateY: CH / 2 },
        { perspective: 700 },
        { rotateX: tx },
        { rotateY: ty },
        { translateX: -CW / 2 },
        { translateY: -CH / 2 },
      ]),
    );
    const rr = Skia.RRectXY(Skia.XYWHRect(0, 0, CW, CH), 16, 16);
    const bg = Skia.Paint();
    bg.setColor(Skia.Color('#0b0b0d'));
    c.drawRRect(rr, bg);
    if (LINES) {
      const lp = Skia.Paint();
      lp.setShader(LINES.makeShader([CW, CH, tx, ty, m.boostK]));
      c.save();
      c.clipRRect(rr, 1, true);
      c.drawRect(Skia.XYWHRect(0, 0, CW, CH), lp);
      c.restore();
    }
    if (RIM) {
      const rim = Skia.Paint();
      rim.setAntiAlias(true);
      rim.setStyle(1);
      rim.setStrokeWidth(1.5);
      rim.setShader(RIM.makeShader([CW, CH, tx, ty]));
      rim.setAlphaf(0.5 + 0.3 * m.boostK);
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.75, 0.75, CW - 1.5, CH - 1.5), 15.5, 15.5), rim);
    }
    if (wordFont && HOLO) {
      const word = 'LOUDCASH';
      const ids = wordFont.getGlyphIDs(word);
      const ws = wordFont.getGlyphWidths(ids);
      let tw = 0;
      for (let i = 0; i < ws.length; i++) tw += ws[i];
      const sx = (CW - 28) / tw;
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setShader(HOLO.makeShader([CW, CH, tx, ty, m.boostK]));
      c.save();
      c.translate(14, CH * 0.69);
      c.scale(sx, 1);
      c.drawText(word, 0, 0, p, wordFont);
      c.restore();
    }
    if (tagFont) {
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setColor(Skia.Color('#ffffff'));
      c.drawText('YOUR BANK', 14, CH - 26, tp, tagFont);
      c.drawText("WON'T DO THIS", 14, CH - 14, tp, tagFont);
      const dw = tagFont.getGlyphWidths(tagFont.getGlyphIDs('DEBIT'));
      let lw = 0;
      for (let i = 0; i < dw.length; i++) lw += dw[i];
      c.drawText('DEBIT', CW - 14 - lw, CH - 14, tp, tagFont);
    }
    return rec.finishRecordingAsPicture();
  }, [CW, CH, M, wordFont, tagFont]);

  const cardPan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      last.set(clock.get());
      const tl = tilt.get();
      tl.on = 1;
      tl.son = 0;
      tl.tx = tl.rx;
      tl.ty = tl.ry;
      scheduleOnRN(cue, 'shimmer');
    })
    .onUpdate((e) => {
      last.set(clock.get());
      const tl = tilt.get();
      tl.tx = Math.max(-16, Math.min(16, -e.translationY * 0.14));
      tl.ty = Math.max(-16, Math.min(16, e.translationX * 0.12));
    })
    .onFinalize(() => {
      last.set(clock.get());
      tilt.get().on = 0;
      scheduleOnRN(buzz, 1);
    });

  // ---------------------------------------------------------------- the balance
  const dipStyle = useAnimatedStyle(() => {
    tick.get();
    const d = money.get().dip;
    return { opacity: (1 - d) * (1 - hide.get()), transform: [{ translateY: d * 6 }] };
  });
  const mainStyle = useAnimatedStyle(() => ({ opacity: 1 - hide.get() }));
  const maskStyle = useAnimatedStyle(() => ({ opacity: hide.get() }));
  const apyStyle = useAnimatedStyle(() => {
    tick.get();
    const v = apy.get().v;
    // digits 3 and 8 on one strip: 0 shows 3, 1 shows 8
    return { transform: [{ translateY: -((v - 3) / 5) * 18 }] };
  });
  const sticker = useAnimatedStyle(() => {
    tick.get();
    const since = clock.get() - pop.get().at;
    const s = since >= 0 && since < 0.6 ? 1 + 0.16 * Math.sin(since * 22) * Math.exp(-since * 7) - 0.08 * Math.exp(-since * 30) : 1;
    return { transform: [{ rotate: `${12 + (money.get().boostK - 0) * -6}deg` }, { scale: s }] };
  });

  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const toggleBoostUI = () => {
    'worklet';
    last.set(clock.get());
    setBoost(money.get().boost !== 1, clock.get(), true);
  };
  const toggleBoost = () => scheduleOnUI(toggleBoostUI);
  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const addFiftyUI = () => {
    'worklet';
    last.set(clock.get());
    const m = money.get();
    m.extra += 5000000;
    m.v += 5000000;
    scheduleOnRN(cue, 'tap');
    scheduleOnRN(buzz, 2);
  };
  const addFifty = () => scheduleOnUI(addFiftyUI);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const top = insets.top + 6;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={[styles.bar, { top }]}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText} allowFontScaling={false}>
            LC
          </Text>
        </View>
        <View style={styles.barRight}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={hidden ? 'Show balance' : 'Hide balance'}
            hitSlop={8}
            style={styles.iconBtn}
            onPress={() => {
              hideT.set(hidden ? 0 : 1);
              setHidden(!hidden);
              buzz(0);
            }}
          >
            <Icon d={ICONS.eye} />
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="Support chat" hitSlop={8} style={styles.iconBtn}>
            <Icon d={ICONS.chat} />
          </Pressable>
        </View>
      </View>


      <View style={[styles.balance, { top: top + 52 + CH + 8 }]}>
        <View style={styles.balHead}>
          <View style={styles.row6}>
            <Text style={styles.label} allowFontScaling={false}>
              Balance
            </Text>
            <Icon d={ICONS.info} size={15} color="#8a8a8f" width={2} />
          </View>
          <View style={styles.row6}>
            <Text style={styles.small} allowFontScaling={false}>
              Rewards of
            </Text>
            <View style={styles.tag}>
              <View style={styles.apyWin}>
                <Animated.View style={apyStyle}>
                  <Text style={styles.tagText} allowFontScaling={false}>
                    3
                  </Text>
                  <Text style={styles.tagText} allowFontScaling={false}>
                    8
                  </Text>
                </Animated.View>
              </View>
              <Text style={styles.tagText} allowFontScaling={false}>
                %
              </Text>
            </View>
            <Text style={styles.small} allowFontScaling={false}>
              APY
            </Text>
          </View>
        </View>
        <View style={styles.amountRow} accessible accessibilityLabel={hidden ? 'Balance hidden' : 'Balance 5,020 dollars and 20 cents, accruing'}>
          <Animated.View style={[styles.amountRow, mainStyle]}>
            <Text style={styles.dollar} allowFontScaling={false}>
              $
            </Text>
            <Digit odo={odo} k={0} tick={tick} style={styles.big} lh={40} />
            <Text style={styles.big} allowFontScaling={false}>
              ,
            </Text>
            <Digit odo={odo} k={1} tick={tick} style={styles.big} lh={40} />
            <Digit odo={odo} k={2} tick={tick} style={styles.big} lh={40} />
            <Digit odo={odo} k={3} tick={tick} style={styles.big} lh={40} />
            <View style={styles.lift}>
              <Text style={[styles.mid, { height: 24, lineHeight: 24 }]} allowFontScaling={false}>
                .
              </Text>
              <Digit odo={odo} k={4} tick={tick} style={styles.mid} lh={24} />
              <Digit odo={odo} k={5} tick={tick} style={styles.mid} lh={24} />
              <Animated.View style={[styles.row0, dipStyle]}>
                <Digit odo={odo} k={6} tick={tick} style={styles.grey} lh={24} />
                <Digit odo={odo} k={7} tick={tick} style={styles.grey} lh={24} />
                <Digit odo={odo} k={8} tick={tick} style={styles.grey} lh={24} />
              </Animated.View>
            </View>
          </Animated.View>
          <Animated.Text style={[styles.mask, maskStyle]} allowFontScaling={false}>
            $ • • • • •
          </Animated.Text>
        </View>
        <Text style={styles.note} allowFontScaling={false}>
          Total rewarded: $100
        </Text>
        <Text style={styles.note} allowFontScaling={false}>
          Balance is held in full in dUSD
        </Text>
        <Animated.View style={[styles.boost, sticker]}>
          <Pressable
            accessibilityRole="switch"
            accessibilityLabel="Boost rewards to 8 percent"
            accessibilityState={{ checked: boosted }}
            onPress={toggleBoost}
            hitSlop={10}
          >
            <Text style={styles.boostSmall} allowFontScaling={false}>
              {boosted ? 'BOOSTED' : 'BOOST TO'}
            </Text>
            <Text style={styles.boostBig} allowFontScaling={false}>
              {boosted ? 'ON' : '8%'}
            </Text>
          </Pressable>
        </Animated.View>
      </View>

      {/* the card after the balance, so its tilted corners pass over it */}
      <GestureDetector gesture={cardPan}>
        <View
          style={[styles.card, { top: top + 52 - M, left: 18 - M, width: CW + 2 * M, height: CH + 2 * M }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel="LOUDCASH debit card"
        >
          <Canvas style={{ width: CW + 2 * M, height: CH + 2 * M }}>
            <Picture picture={card} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.actions, { top: top + 52 + CH + 8 + 136 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Add 50 dollars" onPress={addFifty} style={({ pressed }) => [styles.act, pressed && styles.pressed]}>
          <Icon d={ICONS.down} size={18} />
          <Text style={styles.actText} allowFontScaling={false}>
            Add Balance
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Withdraw" style={({ pressed }) => [styles.act, pressed && styles.pressed]}>
          <Icon d={ICONS.up} size={18} />
          <Text style={styles.actText} allowFontScaling={false}>
            Withdraw
          </Text>
        </Pressable>
      </View>

      <View style={[styles.loyal, { top: top + 52 + CH + 8 + 196 }]}>
        <View style={styles.balHead}>
          <Text style={styles.label} allowFontScaling={false}>
            Loyalty program
          </Text>
          <View style={styles.row6}>
            <View style={styles.tag}>
              <Text style={styles.tagText} allowFontScaling={false}>
                1%
              </Text>
            </View>
            <Text style={styles.small} allowFontScaling={false}>
              Cashback
            </Text>
          </View>
        </View>
        <View style={styles.row6}>
          <Text style={styles.points} allowFontScaling={false}>
            2,000
          </Text>
          <View style={styles.coin} />
        </View>
      </View>

      <View style={[styles.tabs, { bottom: insets.bottom + 8 }]}>
        <Icon d={ICONS.wallet} />
        <Icon d={ICONS.grid} color="#9a9aa0" />
        <View style={styles.sendWrap}>
          <View style={styles.send}>
            <Text style={styles.sendText} allowFontScaling={false}>
              SEND
            </Text>
          </View>
        </View>
        <Icon d={ICONS.ring} color="#9a9aa0" />
        <Icon d={ICONS.check} color="#9a9aa0" />
        <View style={styles.refer} pointerEvents="none">
          <Text style={styles.referText} allowFontScaling={false}>
            REFER{'\n'}AND EARN
          </Text>
        </View>
      </View>
    </View>
  );
}

/** One odometer column: 0-9 and 0 again stacked, scrolled by the column's spring. */
function Digit({ odo, k, tick, style, lh }: { odo: SharedValue<Odo>; k: number; tick: SharedValue<number>; style: object; lh: number }) {
  const roll = useAnimatedStyle(() => {
    tick.get();
    const d = odo.get().disp[k];
    const m = ((d % 10) + 10) % 10;
    return { transform: [{ translateY: -m * lh }] };
  });
  return (
    <View style={{ height: lh, overflow: 'hidden' }}>
      <Animated.View style={roll}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((n, i) => (
          <Text key={i} style={[style, { height: lh, lineHeight: lh }]} allowFontScaling={false}>
            {n}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#eeeeef' },
  bar: { position: 'absolute', left: 18, right: 18, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  avatar: { width: 30, height: 30, borderRadius: 8, backgroundColor: '#111111', alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '-6deg' }] },
  avatarText: { color: '#ffffff', fontSize: 11, fontFamily: 'HCC-Bold' },
  barRight: { flexDirection: 'row', gap: 6 },
  iconBtn: { width: 40, height: 40, borderRadius: 12, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  card: { position: 'absolute' },
  balance: { position: 'absolute', left: 18, right: 18, height: 126, borderRadius: 16, backgroundColor: '#ffffff', paddingHorizontal: 14, paddingTop: 12 },
  balHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  row6: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  row0: { flexDirection: 'row' },
  label: { color: '#111111', fontSize: 14, fontFamily: 'HCC-Bold' },
  small: { color: '#111111', fontSize: 13, fontFamily: 'HCC-Medium' },
  tag: { flexDirection: 'row', backgroundColor: '#e93ad8', borderRadius: 4, paddingHorizontal: 4, height: 18, alignItems: 'center' },
  apyWin: { height: 18, overflow: 'hidden' },
  tagText: { color: '#ffffff', fontSize: 12, lineHeight: 18, height: 18, fontFamily: 'HCC-Bold' },
  amountRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'center', marginTop: 12, height: 42 },
  dollar: { color: '#111111', fontSize: 20, lineHeight: 30, fontFamily: 'HCC-Bold', marginRight: 2 },
  big: { color: '#111111', fontSize: 38, fontFamily: 'HCC-Bold', letterSpacing: -1, fontVariant: ['tabular-nums'] },
  mid: { color: '#111111', fontSize: 20, fontFamily: 'HCC-Bold', fontVariant: ['tabular-nums'] },
  lift: { flexDirection: 'row', marginBottom: 4 },
  grey: { color: '#a5a5aa', fontSize: 20, fontFamily: 'HCC-Bold', fontVariant: ['tabular-nums'] },
  mask: { position: 'absolute', alignSelf: 'center', bottom: 4, color: '#111111', fontSize: 30, fontFamily: 'HCC-Bold', letterSpacing: 2 },
  note: { color: '#8a8a8f', fontSize: 12, lineHeight: 16, textAlign: 'center', fontFamily: 'HCC-Medium' },
  boost: { position: 'absolute', right: 18, top: 42, backgroundColor: '#f5ec3d', paddingHorizontal: 7, paddingVertical: 4, borderRadius: 3, alignItems: 'center' },
  boostSmall: { color: '#111111', fontSize: 7, fontFamily: 'HCC-Bold', textAlign: 'center' },
  boostBig: { color: '#111111', fontSize: 18, lineHeight: 20, fontFamily: 'HCC-Anton', textAlign: 'center' },
  actions: { position: 'absolute', left: 18, right: 18, height: 50, flexDirection: 'row', gap: 10 },
  act: { flex: 1, borderRadius: 14, backgroundColor: '#ffffff', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  actText: { color: '#111111', fontSize: 15, fontFamily: 'HCC-Bold' },
  pressed: { transform: [{ scale: 0.97 }] },
  loyal: { position: 'absolute', left: 18, right: 18, height: 76, borderRadius: 16, backgroundColor: '#ffffff', paddingHorizontal: 14, paddingTop: 12, gap: 8 },
  points: { color: '#111111', fontSize: 20, fontFamily: 'HCC-Bold', fontVariant: ['tabular-nums'] },
  coin: { width: 12, height: 12, borderRadius: 6, backgroundColor: '#2d6bff', borderWidth: 2, borderColor: '#bcd0ff' },
  tabs: { position: 'absolute', left: 18, right: 18, height: 64, borderRadius: 22, backgroundColor: '#ffffff', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around' },
  sendWrap: { width: 64, alignItems: 'center' },
  send: { width: 66, height: 66, borderRadius: 33, backgroundColor: '#f04fd8', alignItems: 'center', justifyContent: 'center', marginTop: -26, borderWidth: 3, borderColor: '#ffffff', transform: [{ rotate: '-10deg' }] },
  sendText: { color: '#111111', fontSize: 20, fontFamily: 'HCC-Anton', letterSpacing: 0.5 },
  refer: { position: 'absolute', right: -6, top: -26, backgroundColor: '#5ec8f2', paddingHorizontal: 6, paddingVertical: 4, transform: [{ rotate: '14deg' }] },
  referText: { color: '#111111', fontSize: 8, lineHeight: 9, fontFamily: 'HCC-Bold', textAlign: 'center' },
});

export { LOOP };
