import { BlurStyle, Canvas, Picture, processTransform3d, type SkCanvas, type SkFont, Skia, type Matrix4, useFont } from '@shopify/react-native-skia';
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

import { BACK, clamp01, COPY, DEG, DOLLAR, EURO, FRONT, key, LOOP, mix, PAGES, scriptPage, smooth } from './cco-motion';
import { type Cue, useCcoAudio } from './use-cco-audio';

/*
 Coin Card Onboarding — the welcome carousel of an invented crypto wallet.

 Look (the pin, a still): a soft pink page, the wallet's mark and Skip at the top, two glossy cards
 tilted in space (magenta in front, lilac behind) with two silver coins floating over them, "The
 Card That Works Anywhere", a line of copy, page dashes, a black Get Started and an outlined Sign
 In. The wallet's name and the coins' symbols are ours.

 Motion (ours): the scene is the pager. Swipe and the cards turn in perspective and the coins
 tumble a full turn per page, all as a continuous function of the swipe, so a half-way drag holds
 the scene half-way; page 2 swaps the coins over the card, page 3 lays the cards down flat, one on
 the other, under the coins. The copy slides with the swipe and the dashes stretch.

 autoPlay: LOOP = 9 s. Page 1, 1.6 s page 2, 4.2 s page 3, 6.8 s back to page 1. Frame 9 s equals 0.
 Reduced motion: pages change on a short spring; the coins do not tumble.
*/

let debugState = '';

/** A glossy card in its own 3D pose, centred on (x, y). */
const drawCard = (
  c: SkCanvas,
  x: number,
  y: number,
  rx: number,
  ry: number,
  rz: number,
  front: boolean,
  small: SkFont,
  big: SkFont,
) => {
  'worklet';
  const W = 252;
  const H = 160;
  c.save();
  c.concat(
    processTransform3d([
      { translateX: x },
      { translateY: y },
      { perspective: 640 },
      { rotateZ: rz * DEG },
      { rotateX: rx * DEG },
      { rotateY: ry * DEG },
      { translateX: -W / 2 },
      { translateY: -H / 2 },
    ]),
  );
  const rr = Skia.RRectXY(Skia.XYWHRect(0, 0, W, H), 18, 18);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: W, y: H },
      front
        ? [Skia.Color('#e0479a'), Skia.Color('#8e1450'), Skia.Color('#5c0a33'), Skia.Color('#c72f7f')]
        : [Skia.Color('#c7a6ff'), Skia.Color('#9b72ee'), Skia.Color('#7c55d6'), Skia.Color('#b896ff')],
      [0, 0.45, 0.72, 1],
      0,
    ),
  );
  c.drawRRect(rr, body);
  // a sheen that slides as the card turns
  const k = clamp01(0.5 + ry / 60);
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: W * (k - 0.6), y: 0 },
      { x: W * (k + 0.4), y: H },
      [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.32)'), Skia.Color('rgba(255,255,255,0)')],
      [0, 0.5, 1],
      0,
    ),
  );
  c.drawRRect(rr, sheen);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1);
  rim.setColor(Skia.Color('rgba(255,255,255,0.35)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.5, 0.5, W - 1, H - 1), 17.5, 17.5), rim);
  // the wallet's mark: an eight-armed asterisk
  const mark = Skia.Paint();
  mark.setAntiAlias(true);
  mark.setStyle(1);
  mark.setStrokeWidth(3);
  mark.setStrokeCap(1);
  mark.setColor(Skia.Color('#ffffff'));
  for (let i = 0; i < 4; i++) {
    const a = (i * Math.PI) / 4;
    c.drawLine(26 - Math.cos(a) * 8, 26 - Math.sin(a) * 8, 26 + Math.cos(a) * 8, 26 + Math.sin(a) * 8, mark);
  }
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('rgba(255,255,255,0.75)'));
  c.drawText('Balance', 18, H - 52, ink, small);
  ink.setColor(Skia.Color('#ffffff'));
  c.drawText(front ? '$ 24 597' : '$ 2 597', 18, H - 22, ink, big);
  const w1 = big.getGlyphWidths(big.getGlyphIDs(front ? '$ 24 597' : '$ 2 597'));
  let tw = 0;
  for (let i = 0; i < w1.length; i++) tw += w1[i];
  ink.setColor(Skia.Color('rgba(255,255,255,0.55)'));
  c.drawText('.36', 18 + tw, H - 22, ink, big);
  c.restore();
};

/** One face of a coin at depth z in the coin's pose; the front carries the embossed symbol. */
const drawFace = (c: SkCanvas, base: Matrix4, z: number, r: number, front: boolean, sym: string, font: SkFont) => {
  'worklet';
  c.save();
  c.concat(base);
  c.concat(processTransform3d([{ translateZ: z }]));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -r, y: -r },
      { x: r, y: r },
      [Skia.Color('#ffffff'), Skia.Color('#c9ccd4'), Skia.Color('#8e939e'), Skia.Color('#e9ebf0')],
      [0, 0.4, 0.75, 1],
      0,
    ),
  );
  c.drawCircle(0, 0, r, p);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(1);
  ring.setStrokeWidth(2);
  ring.setColor(Skia.Color('rgba(120,124,136,0.55)'));
  c.drawCircle(0, 0, r * 0.8, ring);
  if (front) {
    // the symbol, embossed: a dark offset, a light offset, the face colour on top
    const g = font.getGlyphWidths(font.getGlyphIDs(sym));
    let gw = 0;
    for (let i = 0; i < g.length; i++) gw += g[i];
    const sx = -gw / 2;
    const sy = r * 0.36;
    const t = Skia.Paint();
    t.setAntiAlias(true);
    t.setColor(Skia.Color('#7d828d'));
    c.drawText(sym, sx + 1, sy + 1.2, t, font);
    t.setColor(Skia.Color('#ffffff'));
    c.drawText(sym, sx - 0.8, sy - 0.8, t, font);
    t.setColor(Skia.Color('#c3c7cf'));
    c.drawText(sym, sx, sy, t, font);
  }
  c.restore();
};

/** The milled edge: the disc repeated a point apart back through the coin's thickness. */
const drawEdge = (c: SkCanvas, base: Matrix4, r: number, T: number) => {
  'worklet';
  for (let k = 1; k < T; k++) {
    c.save();
    c.concat(base);
    c.concat(processTransform3d([{ translateZ: -k }]));
    const e = Skia.Paint();
    e.setAntiAlias(true);
    e.setColor(Skia.Color(k % 2 === 0 ? '#9ca1ab' : '#868b96'));
    c.drawCircle(0, 0, r, e);
    c.restore();
  }
};

/** A silver coin with thickness, turned by flipX about its horizontal axis and flipY about its vertical one. */
const drawCoin = (c: SkCanvas, x: number, y: number, r: number, flipX: number, flipY: number, sym: string, font: SkFont) => {
  'worklet';
  const T = 9;
  const base = processTransform3d([
    { translateX: x },
    { translateY: y },
    { perspective: 520 },
    { rotateX: flipX * DEG },
    { rotateY: flipY * DEG },
  ]);
  // draw the far side first
  if (Math.cos(flipX * DEG) * Math.cos(flipY * DEG) >= 0) {
    drawFace(c, base, -T, r, false, sym, font);
    drawEdge(c, base, r, T);
    drawFace(c, base, 0, r, true, sym, font);
  } else {
    drawFace(c, base, 0, r, true, sym, font);
    drawEdge(c, base, r, T);
    drawFace(c, base, -T, r, false, sym, font);
  }
};

type Props = { autoPlay?: boolean };

export function CoinCardOnboarding({ autoPlay = false }: Props) {
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
    'CCO-Title': require('./assets/fonts/Outfit-Medium.ttf'),
    'CCO-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'CCO-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'CCO-Bold': require('./assets/fonts/Inter-Bold.ttf'),
  });
  const small = useFont(require('./assets/fonts/Inter-Medium.ttf'), 12);
  const big = useFont(require('./assets/fonts/Outfit-Medium.ttf'), 26);
  const sym = useFont(require('./assets/fonts/Inter-Bold.ttf'), 34);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const SY = insets.top + 236; // the scene's centre

  const play = useCcoAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const pager = useSharedValue({ p: 0, v: 0, target: 0, drag: 0, finger: 0, start: 0 });
  const pos = useSharedValue(0);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __coinCard?: unknown };
    g.__coinCard = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__coinCard;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const springPager = (pg: { p: number; v: number; target: number }, dt: number, k: number) => {
    'worklet';
    const c = 2 * Math.sqrt(k) * 0.92;
    const n = Math.max(1, Math.ceil(dt / (1 / 240)));
    const hh = dt / n;
    for (let i = 0; i < n; i++) {
      pg.v += (k * (pg.target - pg.p) - c * pg.v) * hh;
      pg.p += pg.v * hh;
    }
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    const pg = pager.get();
    let t = time.get();
    if (dbg >= 0) {
      // replay the script from 0 so the pinned frame is where the loop is
      const sim = { p: 0, v: 0, target: 0 };
      let st = 0;
      while (st < dbg) {
        const d = Math.min(1 / 120, dbg - st);
        st += d;
        sim.target = scriptPage(st);
        springPager(sim, d, 60);
      }
      pg.p = sim.p;
      pg.v = sim.v;
      pg.target = sim.target;
      t = dbg;
      debugT.set(-2);
    } else if (dbg === -2) {
      // pinned
    } else if (pg.drag === 1) {
      pg.v = dt > 0 ? pg.v * 0.6 + ((pg.finger - pg.p) / dt) * 0.4 : 0;
      pg.p = pg.finger;
    } else {
      const touched = now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
        pg.target = scriptPage(t);
        springPager(pg, dt, 60);
      } else springPager(pg, dt, reduced ? 300 : 150);
    }
    time.set(t);
    pos.set(pg.p);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} p=${pg.p.toFixed(3)} target=${pg.target}`);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // a soft tick and the coin's ting as the page changes
  useAnimatedReaction(
    () => Math.round(pos.get()),
    (n, prev) => {
      if (prev !== null && n !== prev) {
        scheduleOnRN(cue, 'flip');
        scheduleOnRN(buzz, 0);
      }
    },
  );

  const scene = useDerivedValue(() => {
    const p = pos.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, 520));
    if (!small || !big || !sym) return rec.finishRecordingAsPicture();
    const y0 = SY - insets.top;
    // a soft shadow on the page under the cards
    const sh = Skia.Paint();
    sh.setColor(Skia.Color('#b0306f'));
    sh.setAlphaf(mix(0.16, 0.26, Math.max(0, Math.min(1, p - 1))));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 26, true));
    c.drawOval(Skia.XYWHRect(cx - 130, y0 + 92, 260, 46), sh);
    drawCard(c, cx + key(BACK.x, p), y0 + key(BACK.y, p), key(BACK.rx, p), key(BACK.ry, p), key(BACK.rz, p), false, small, big);
    drawCard(c, cx + key(FRONT.x, p), y0 + key(FRONT.y, p), key(FRONT.rx, p), key(FRONT.ry, p), key(FRONT.rz, p), true, small, big);
    const tumble = reduced ? 0 : 1;
    drawCoin(c, cx + key(DOLLAR.x, p), y0 + key(DOLLAR.y, p), key(DOLLAR.r, p), 0, key(DOLLAR.flip, p) * tumble + 18, '$', sym);
    drawCoin(c, cx + key(EURO.x, p), y0 + key(EURO.y, p), key(EURO.r, p), key(EURO.flip, p) * tumble + 14, -16, '€', sym);
    return rec.finishRecordingAsPicture();
  }, [w, cx, SY, small, big, sym, insets.top]);

  // the page: pink at the top fading to white, a soft bloom behind the scene
  const backdrop = useDerivedValue(() => {
    // each page has its own wash (pink, mint, lilac), blended as the pager moves, like a parallax
    // onboarding's backdrop; the bloom behind the scene takes the same hue
    const p = Math.max(0, Math.min(PAGES - 1, pos.get()));
    const i = Math.min(PAGES - 2, Math.floor(p));
    const k = smooth(p - i);
    const TOPS = [
      [247, 211, 234],
      [207, 240, 226],
      [226, 214, 252],
    ];
    const BLOOM = [
      [244, 170, 214],
      [140, 222, 190],
      [190, 165, 250],
    ];
    const tc = [0, 0, 0];
    const bc = [0, 0, 0];
    for (let j = 0; j < 3; j++) {
      tc[j] = Math.round(mix(TOPS[i][j], TOPS[i + 1][j], k));
      bc[j] = Math.round(mix(BLOOM[i][j], BLOOM[i + 1][j], k));
    }
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const g = Skia.Paint();
    g.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: 0 },
        { x: 0, y: h },
        [Skia.Color(`rgb(${tc[0]},${tc[1]},${tc[2]})`), Skia.Color('#fbf7fb'), Skia.Color('#ffffff')],
        [0, 0.62, 1],
        0,
      ),
    );
    c.drawRect(Skia.XYWHRect(0, 0, w, h), g);
    const b = Skia.Paint();
    b.setShader(
      Skia.Shader.MakeRadialGradient({ x: cx, y: SY + 30 }, w * 0.7, [Skia.Color(`rgba(${bc[0]},${bc[1]},${bc[2]},0.55)`), Skia.Color(`rgba(${bc[0]},${bc[1]},${bc[2]},0)`)], null, 0),
    );
    c.drawRect(Skia.XYWHRect(0, 0, w, h), b);
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, SY]);

  // ---------------------------------------------------------------- copy, dashes, touch
  const layer = (i: number) => {
    'worklet';
    const d = pos.get() - i;
    const k = clamp01(1 - Math.abs(d) * 1.8);
    return { opacity: k, transform: [{ translateX: -d * 44 }] };
  };
  const c0 = useAnimatedStyle(() => {
    pos.get();
    return layer(0);
  });
  const c1 = useAnimatedStyle(() => {
    pos.get();
    return layer(1);
  });
  const c2 = useAnimatedStyle(() => {
    pos.get();
    return layer(2);
  });
  const copies = [c0, c1, c2];
  const dash = (i: number) => {
    'worklet';
    const k = clamp01(1 - Math.abs(pos.get() - i));
    return { width: mix(6, 22, k), opacity: mix(0.3, 1, k) };
  };
  const d0 = useAnimatedStyle(() => {
    pos.get();
    return dash(0);
  });
  const d1 = useAnimatedStyle(() => {
    pos.get();
    return dash(1);
  });
  const d2 = useAnimatedStyle(() => {
    pos.get();
    return dash(2);
  });
  const dashes = [d0, d1, d2];
  const [cur, setCur] = useState(0);
  useAnimatedReaction(
    () => Math.round(Math.max(0, Math.min(PAGES - 1, pos.get()))),
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setCur, n);
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onBegin(() => {
      last.set(clock.get());
    })
    .onStart(() => {
      const pg = pager.get();
      pg.drag = 1;
      pg.start = pg.p;
      pg.finger = pg.p;
    })
    .onUpdate((e) => {
      last.set(clock.get());
      const pg = pager.get();
      let f = pg.start - e.translationX / (w * 0.8);
      if (f < 0) f = -0.3 * Math.tanh(-f / 0.3);
      if (f > PAGES - 1) f = PAGES - 1 + 0.3 * Math.tanh((f - PAGES + 1) / 0.3);
      pg.finger = f;
    })
    .onFinalize((e) => {
      last.set(clock.get());
      const pg = pager.get();
      if (pg.drag !== 1) return;
      pg.drag = 0;
      pg.v = -e.velocityX / (w * 0.8);
      const proj = pg.p + pg.v * 0.2;
      pg.target = Math.max(0, Math.min(PAGES - 1, Math.round(proj)));
      scheduleOnRN(cue, 'page');
    });

  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const goToUI = (n: number) => {
    'worklet';
    last.set(clock.get());
    const pg = pager.get();
    pg.target = Math.max(0, Math.min(PAGES - 1, n));
    scheduleOnRN(buzz, 1);
    scheduleOnRN(cue, 'page');
  };
  const goTo = (n: number) => scheduleOnUI(goToUI, n);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${COPY[cur].title.replace('\n', ' ')}. ${COPY[cur].body.replace('\n', ' ')}`}
          accessibilityValue={{ text: `Page ${cur + 1} of ${PAGES}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => goTo(cur + (e.nativeEvent.actionName === 'increment' ? 1 : -1))}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Picture picture={backdrop} />
            </Canvas>
          ) : null}
          {size ? (
            <Canvas style={[styles.scene, { top: insets.top, width: w }]} pointerEvents="none">
              <Picture picture={scene} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View style={[styles.bar, { top: insets.top + 8 }]}>
        <View style={styles.brand}>
          <View style={styles.logo}>
            {[0, 45, 90, 135].map((a) => (
              <View key={a} style={[styles.arm, { transform: [{ rotate: `${a}deg` }] }]} />
            ))}
          </View>
          <Text style={styles.brandText} allowFontScaling={false}>
            Sorel
          </Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Skip to the last page" onPress={() => goTo(PAGES - 1)} style={({ pressed }) => [styles.skip, pressed && styles.pressed]}>
          <Text style={styles.skipText} allowFontScaling={false}>
            Skip
          </Text>
        </Pressable>
      </View>

      <View style={[styles.copy, { top: insets.top + 420 }]} pointerEvents="none">
        {COPY.map((cp, i) => (
          <Animated.View key={i} style={[styles.copyLayer, copies[i]]} {...(i !== cur ? { accessibilityElementsHidden: true, importantForAccessibility: 'no-hide-descendants' as const } : {})}>
            <Text style={styles.title} allowFontScaling={false}>
              {cp.title}
            </Text>
            <Text style={styles.body} allowFontScaling={false}>
              {cp.body}
            </Text>
          </Animated.View>
        ))}
      </View>

      <View style={[styles.dashes, { bottom: insets.bottom + 150 }]} pointerEvents="none">
        {[0, 1, 2].map((i) => (
          <Animated.View key={i} style={[styles.dash, dashes[i]]} />
        ))}
      </View>
      <View style={[styles.buttons, { bottom: insets.bottom + 18 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Get started" onPress={() => buzz(1)} style={({ pressed }) => [styles.primary, pressed && styles.pressed]}>
          <Text style={styles.primaryText} allowFontScaling={false}>
            Get Started
          </Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Sign in" onPress={() => buzz(0)} style={({ pressed }) => [styles.secondary, pressed && styles.pressed]}>
          <Text style={styles.secondaryText} allowFontScaling={false}>
            Sign In
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#fdf7fb' },
  scene: { position: 'absolute', left: 0, height: 520 },
  bar: { position: 'absolute', left: 20, right: 20, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  logo: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#e0479a', alignItems: 'center', justifyContent: 'center' },
  arm: { position: 'absolute', width: 16, height: 3, borderRadius: 1.5, backgroundColor: '#ffffff' },
  brandText: { color: '#1b1b1f', fontSize: 17, fontFamily: 'CCO-Title' },
  skip: { height: 34, paddingHorizontal: 16, borderRadius: 17, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  skipText: { color: '#1b1b1f', fontSize: 14, fontFamily: 'CCO-Medium' },
  copy: { position: 'absolute', left: 24, right: 24, height: 150 },
  copyLayer: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  title: { color: '#141416', fontSize: 31, lineHeight: 37, fontFamily: 'CCO-Title', textAlign: 'center', letterSpacing: -0.4 },
  body: { color: '#55555c', fontSize: 15, lineHeight: 21, fontFamily: 'CCO-Regular', textAlign: 'center', marginTop: 14 },
  dashes: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 5 },
  dash: { height: 4, borderRadius: 2, backgroundColor: '#141416' },
  buttons: { position: 'absolute', left: 20, right: 20, gap: 10 },
  primary: { height: 54, borderRadius: 27, backgroundColor: '#0d0d0f', alignItems: 'center', justifyContent: 'center' },
  primaryText: { color: '#ffffff', fontSize: 16, fontFamily: 'CCO-Medium' },
  secondary: { height: 54, borderRadius: 27, borderWidth: 1.5, borderColor: '#141416', alignItems: 'center', justifyContent: 'center' },
  secondaryText: { color: '#141416', fontSize: 16, fontFamily: 'CCO-Medium' },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
