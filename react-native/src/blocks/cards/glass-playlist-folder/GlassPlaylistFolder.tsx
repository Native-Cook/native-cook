import {
  BlurStyle,
  Canvas,
  FilterMode,
  MipmapMode,
  Picture,
  processTransform3d,
  type SkCanvas,
  type SkImage,
  Skia,
  TileMode,
  useImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clamp01,
  COVERS,
  easeInOut,
  LOOP,
  makeSprings,
  mix,
  openAt,
  seg,
  setTarget,

  type Springs,
  stepSprings,
  T,
} from './gpf-motion';
import { type Cue, useGpfAudio } from './use-gpf-audio';

/*
 Glass Playlist Folder — a playlist in the library of an invented music app, Lull, shown as a folder.

 Look (the pin, a still): a 3D folder of frosted green glass seen from below and to the left, album
 covers standing in it and peeking over its front, a green glow behind it on a near-black page,
 and a 3D glass cursor arrow pointing at it.

 Motion (ours): the cursor glides in and clicks; the front of the folder swings forward on its
 bottom hinge while the covers rise out of it one after another on springs and fan open; the
 frosted front blurs whatever is behind it as it moves; the cursor hovers a cover, which comes
 forward; then the folder closes, the covers dropping back in the reverse order.

 autoPlay: LOOP = 8 s (marks in gpf-motion.ts); frame 8 s equals frame 0.
 Touch: drag the folder up to open it with the finger (past 40 % or a flick it opens, otherwise it
 falls shut), tap to open or close it, tap a cover while it is open to bring it forward. 2 s after
 the last touch the loop resumes. Reduced motion: the folder rests open, still.
*/

let debugState = '';
const DEG = Math.PI / 180;

type Props = { autoPlay?: boolean; previewTime?: number };

const BG = '#050806';

// Fan of covers: x, rotation (deg) when open; peeking positions when shut.
const FAN = [
  { x: -70, r: -18, sx: -54, sr: -12 },
  { x: -24, r: -6, sx: -18, sr: -4 },
  { x: 24, r: 7, sx: 18, sr: 4 },
  { x: 70, r: 19, sx: 52, sr: 11 },
];

const base = (cx: number, cy: number) => {
  'worklet';
  return [
    { translateX: cx },
    { translateY: cy },
    { perspective: 760 },
    { rotateY: -20 * DEG },
    { rotateX: 12 * DEG },
    { scale: 1.14 },
  ];
};

const rr = (x: number, y: number, w: number, h: number, r: number) => {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
};

const vgrad = (y0: number, y1: number, cols: string[]) => {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    { x: 0, y: y0 },
    { x: 0, y: y1 },
    cols.map((c) => Skia.Color(c)),
    null,
    0,
  );
};

const drawBack = (c: SkCanvas) => {
  'worklet';
  // the folder's back, with its tab
  const b = Skia.PathBuilder.Make();
  b.addRRect(rr(-128, -84, 256, 184, 20));
  b.addRRect(rr(-128, -102, 100, 40, 14));
  const path = b.build();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(vgrad(-102, 100, ['#2f9a4a', '#16602b', '#0b3a19']));
  p.setAlphaf(0.92);
  c.drawPath(path, p);
  const s = Skia.Paint();
  s.setAntiAlias(true);
  s.setStyle(1);
  s.setStrokeWidth(1.4);
  s.setColor(Skia.Color('rgba(170,255,190,0.35)'));
  c.drawPath(path, s);
};

const drawCover = (c: SkCanvas, img: SkImage | null, i: number) => {
  'worklet';
  const w = 112;
  const h = 140;
  // card: dark rim, then the art
  const sh = Skia.Paint();
  sh.setColor(Skia.Color('#000000'));
  sh.setAlphaf(0.45);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawRRect(rr(-w / 2 + 2, -h / 2 + 6, w, h, 12), sh);
  const frame = Skia.Paint();
  frame.setAntiAlias(true);
  frame.setColor(Skia.Color('#0d0f10'));
  c.drawRRect(rr(-w / 2, -h / 2, w, h, 12), frame);
  if (img) {
    c.save();
    c.clipRRect(rr(-w / 2 + 4, -h / 2 + 4, w - 8, h - 8, 9), 1, true);
    const p = Skia.Paint();
    const sc = (h - 8) / img.height();
    const m = Skia.Matrix();
    m.translate(-((img.width() * sc) / 2), -h / 2 + 4);
    m.scale(sc, sc);
    p.setShader(img.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None, m));
    c.drawRect(Skia.XYWHRect(-w / 2, -h / 2, w, h), p);
    c.restore();
  }
  // a glossy sheen across the top
  const g = Skia.Paint();
  g.setAntiAlias(true);
  g.setShader(vgrad(-h / 2, 0, ['rgba(255,255,255,0.22)', 'rgba(255,255,255,0)']));
  c.drawRRect(rr(-w / 2 + 4, -h / 2 + 4, w - 8, h / 2, 9), g);
  void i;
};

const drawFlap = (c: SkCanvas, open: number) => {
  'worklet';
  const x = -134;
  const y = -38;
  const w = 268;
  const h = 140;
  const r = rr(x, y, w, h, 22);
  // frosted glass: blur what is behind, tint it green, light its rim
  c.save();
  c.clipRRect(r, 1, true);
  c.saveLayer(undefined, null, Skia.ImageFilter.MakeBlur(14, 14, TileMode.Clamp, null));
  c.restore();
  const tint = Skia.Paint();
  tint.setAntiAlias(true);
  tint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y },
      { x: x + w * 0.6, y: y + h },
      [Skia.Color('rgba(110,235,135,0.42)'), Skia.Color('rgba(40,170,80,0.5)'), Skia.Color('rgba(20,110,50,0.62)')],
      null,
      0,
    ),
  );
  c.drawRRect(r, tint);
  // a band of light that slides as the flap swings
  const band = Skia.Paint();
  band.setAntiAlias(true);
  const by = mix(y - 40, y + h, open);
  band.setShader(
    Skia.Shader.MakeLinearGradient(
      { x, y: by },
      { x: x + 70, y: by + 120 },
      [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(230,255,235,0.28)'), Skia.Color('rgba(255,255,255,0)')],
      null,
      0,
    ),
  );
  c.drawRRect(r, band);
  c.restore();
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1.6);
  rim.setShader(vgrad(y, y + h, ['rgba(220,255,225,0.75)', 'rgba(120,230,150,0.25)', 'rgba(160,255,180,0.5)']));
  c.drawRRect(r, rim);
  // the app's mark and name
  const mx = x + 34;
  const my = y + h - 36;
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(Skia.Color('#0f3b1d'));
  c.drawCircle(mx, my, 15, dot);
  const arc = Skia.Paint();
  arc.setAntiAlias(true);
  arc.setStyle(1);
  arc.setStrokeCap(1);
  arc.setStrokeWidth(2.4);
  arc.setColor(Skia.Color('#7df59b'));
  for (let k = 0; k < 3; k++) {
    const b = Skia.PathBuilder.Make();
    const rad = 4 + k * 4;
    b.addArc(Skia.XYWHRect(mx - rad - 4, my - rad, rad * 2, rad * 2), -50, 100);
    c.drawPath(b.build(), arc);
  }
};

const drawCursor = (c: SkCanvas, x: number, y: number, press: number, a: number) => {
  'worklet';
  if (a <= 0.01) return;
  c.save();
  c.translate(x, y);
  const s = 1 - press * 0.16;
  c.scale(s, s);
  c.rotate(-8, 0, 0);
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, 0);
  b.lineTo(56, 26);
  b.quadTo(60, 28, 56, 31);
  b.lineTo(31, 38);
  b.lineTo(22, 62);
  b.quadTo(19, 66, 17, 61);
  b.close();
  const path = b.build();
  const glow = Skia.Paint();
  glow.setColor(Skia.Color('#3dff7a'));
  glow.setAlphaf(0.4 * a);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawPath(path, glow);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 50, y: 60 },
      [Skia.Color('#b6ffc8'), Skia.Color('#43d96c'), Skia.Color('#1b8d3c')],
      null,
      0,
    ),
  );
  p.setAlphaf(a);
  c.drawPath(path, p);
  const e = Skia.Paint();
  e.setAntiAlias(true);
  e.setStyle(1);
  e.setStrokeWidth(1.4);
  e.setColor(Skia.Color('#e6ffec'));
  e.setAlphaf(0.7 * a);
  c.drawPath(path, e);
  c.restore();
};

export function GlassPlaylistFolder({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const c1 = useImage(require('./assets/covers/cover-1.webp'));
  const c2 = useImage(require('./assets/covers/cover-2.webp'));
  const c3 = useImage(require('./assets/covers/cover-3.webp'));
  const c4 = useImage(require('./assets/covers/cover-4.webp'));

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const cy = insets.top + 318;

  const play = useGpfAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(previewTime ?? 0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const springs = useSharedValue<Springs>(makeSprings(reduced ? 1 : 0));
  const tick = useSharedValue(0);
  const drag = useSharedValue(-1); // -1 not dragging, else the open amount the finger holds
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __glassFolder?: unknown };
    g.__glassFolder = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__glassFolder;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const s = springs.get();
    const dbg = debugT.get();
    let t = time.get();
    const touched = drag.get() >= 0 || now - last.get() < 2;
    if (dbg >= 0) t = dbg;
    else if (auto && !touched) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    time.set(t);
    if (dbg >= 0 || (auto && !touched)) {
      setTarget(s, openAt(t), now);
      s.liftTarget = t >= T.hover[0] && t < T.hover[1] ? 1 : 0;
      s.hovered = 1;
    }
    if (drag.get() >= 0) {
      // the finger holds the flap
      s.open = drag.get();
      s.ov = 0;
    }
    stepSprings(s, dt, now);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} open=${s.open.toFixed(2)} target=${s.target}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && !reduced && size !== null && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [active, reduced, size, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    c.drawColor(Skia.Color(BG));
    const t = time.get();
    const s = springs.get();
    const open = s.open;
    // green glow, breathing a little brighter when open
    const glow = Skia.Paint();
    glow.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx, y: cy - 120 },
        320,
        [Skia.Color('rgba(40,170,70,0.62)'), Skia.Color('rgba(20,90,40,0.25)'), Skia.Color('rgba(5,20,10,0)')],
        [0, 0.45, 1],
        0,
      ),
    );
    glow.setAlphaf(0.75 + 0.25 * clamp01(open));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), glow);
    const B = base(cx, cy);
    // back
    c.save();
    c.concat(processTransform3d([...B, { translateZ: -16 }]));
    drawBack(c);
    c.restore();
    // covers, rising and fanning
    const imgs = [c1, c2, c3, c4];
    for (let i = 0; i < COVERS; i++) {
      const k = s.covers[i];
      const f = FAN[i];
      const bob = Math.sin((2 * Math.PI * 2 * t) / LOOP + i * 1.3) * 2.5 * clamp01(k);
      const lift = s.hovered === i ? s.lift : 0;
      c.save();
      c.concat(
        processTransform3d([
          ...B,
          { translateZ: -9 + i * 2 + lift * 40 },
          { translateX: mix(f.sx, f.x, k) },
          { translateY: mix(-32, -116, k) + bob - lift * 16 },
          { rotateZ: mix(f.sr, f.r, k) * DEG },
          { scale: 1 + lift * 0.06 },
        ]),
      );
      drawCover(c, imgs[i], i);
      c.restore();
    }
    // the front flap, hinged at its bottom edge
    c.save();
    c.concat(
      processTransform3d([...B, { translateZ: 8 }, { translateY: 102 }, { rotateX: -open * 50 * DEG }, { translateY: -102 }]),
    );
    drawFlap(c, clamp01(open));
    c.restore();
    // the cursor
    const restX = cx + 112;
    const restY = cy + 196;
    const a1 = easeInOut(seg(t, T.approach[0], T.approach[1]));
    const toHover = easeInOut(seg(t, T.hover[0] - 0.5, T.hover[0]));
    const leave = easeInOut(seg(t, T.leave[0], T.leave[1]));
    const home = easeInOut(seg(t, T.home[0], T.home[1]));
    let x = mix(restX, cx + 40, a1);
    let y = mix(restY, cy + 50, a1);
    x = mix(x, cx - 4, toHover);
    y = mix(y, cy - 140, toHover);
    x = mix(x, cx + 150, leave);
    y = mix(y, cy + 90, leave);
    x = mix(x, restX, home);
    y = mix(y, restY, home);
    const press = t >= T.click - 0.08 && t < T.click + 0.22 ? Math.sin(seg(t, T.click - 0.08, T.click + 0.22) * Math.PI) : 0;
    const fingerAway = drag.get() >= 0 ? 0 : 1;
    drawCursor(c, x, y, press, fingerAway);
    // click ripple
    const rp = seg(t, T.click, T.click + 0.45);
    if (rp > 0 && rp < 1) {
      const r = Skia.Paint();
      r.setAntiAlias(true);
      r.setStyle(1);
      r.setStrokeWidth(1.6);
      r.setColor(Skia.Color('#8dffb0'));
      r.setAlphaf(0.7 * (1 - rp));
      c.drawCircle(cx + 40, cy + 50, 8 + rp * 34, r);
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, cy, c1, c2, c3, c4]);

  // ---------------------------------------------------------------- copy and controls
  const metaStyle = useAnimatedStyle(() => {
    const k = clamp01(springs.get().open + tick.get() * 0);
    return { transform: [{ translateY: k * 6 }], opacity: 1 - k * 0.15 };
  });
  const playPress = useSharedValue(0);
  const playStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - playPress.get() * 0.04 }] }));

  const folderTouch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined && !reduced)
    .onBegin((e) => {
      if (Math.abs(e.x - cx) > 170 || Math.abs(e.y - cy) > 200) return;
      drag.set(springs.get().open);
      last.set(clock.get());
    })
    .onUpdate((e) => {
      if (drag.get() < 0) return;
      const s = springs.get();
      const base0 = s.target;
      drag.set(clamp01(base0 + -e.translationY / 160));
      last.set(clock.get());
    })
    .onFinalize((e) => {
      if (drag.get() < 0) return;
      const s = springs.get();
      const now = clock.get();
      const moved = Math.hypot(e.translationX, e.translationY) > 10;
      const v = drag.get();
      drag.set(-1);
      last.set(now);
      if (!moved) {
        // a tap: on an open folder over a cover brings it forward, otherwise toggles
        if (s.target === 1 && e.y < cy - 60) {
          const i = Math.max(0, Math.min(3, Math.floor((e.x - (cx - 120)) / 60)));
          s.hovered = i;
          s.liftTarget = s.liftTarget === 1 ? 0 : 1;
          scheduleOnRN(buzz, 2);
          return;
        }
        s.liftTarget = 0;
        setTarget(s, s.target === 1 ? 0 : 1, now);
        scheduleOnRN(cue, s.target === 1 ? 'open' : 'close');
        scheduleOnRN(buzz, 0);
        return;
      }
      const target = v > 0.4 || e.velocityY < -500 ? 1 : 0;
      s.ov = -e.velocityY / 160;
      s.open = v;
      s.liftTarget = 0;
      setTarget(s, target, now);
      scheduleOnRN(cue, target === 1 ? 'open' : 'close');
      scheduleOnRN(buzz, 0);
    });
  const playTouch = Gesture.Tap()
    .maxDuration(2000)
    .onBegin(() => playPress.set(withSpring(1, { duration: 150, dampingRatio: 1 })))
    .onFinalize((_e, ok) => {
      playPress.set(withSpring(0, { duration: 320, dampingRatio: 0.7 }));
      if (ok) {
        scheduleOnRN(cue, 'tap');
        scheduleOnRN(buzz, 1);
      }
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={folderTouch}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.head, { top: insets.top + 12 }]} pointerEvents="none">
        <Text style={styles.headTitle} allowFontScaling={false}>
          Library
        </Text>
        <Text style={styles.headSub} allowFontScaling={false}>
          Folders
        </Text>
      </View>
      <Animated.View style={[styles.meta, { bottom: insets.bottom + 28 }, metaStyle]}>
        <Text style={styles.kicker} allowFontScaling={false}>
          FOLDER · 4 PLAYLISTS
        </Text>
        <Text style={styles.title} allowFontScaling={false}>
          Late Night Drive
        </Text>
        <Text style={styles.sub} allowFontScaling={false}>
          96 songs · 5 h 12 min · updated today
        </Text>
        <GestureDetector gesture={playTouch}>
          <Animated.View style={[styles.play, playStyle]} accessible accessibilityRole="button" accessibilityLabel="Play">
            <Text style={styles.playText} allowFontScaling={false}>
              ▶  Play all
            </Text>
          </Animated.View>
        </GestureDetector>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  head: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  headTitle: { color: '#ffffff', fontSize: 30, fontWeight: '700', letterSpacing: -0.6 },
  headSub: { color: 'rgba(200,255,215,0.6)', fontSize: 15, fontWeight: '500' },
  meta: { position: 'absolute', left: 24, right: 24, alignItems: 'flex-start' },
  kicker: { color: 'rgba(150,255,180,0.7)', fontSize: 12, fontWeight: '600', letterSpacing: 1.6 },
  title: { color: '#ffffff', fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 6 },
  sub: { color: 'rgba(255,255,255,0.55)', fontSize: 15, marginTop: 4 },
  play: {
    marginTop: 20,
    height: 50,
    paddingHorizontal: 26,
    borderRadius: 25,
    backgroundColor: '#4ce07a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playText: { color: '#04170a', fontSize: 16, fontWeight: '700' },
});

export { LOOP };
