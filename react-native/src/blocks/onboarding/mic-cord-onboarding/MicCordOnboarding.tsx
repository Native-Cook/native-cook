import { Canvas, Picture, type SkCanvas, Skia } from '@shopify/react-native-skia';
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

import { GRAB, LOOP, makeRope, N, NOTES, type Rope, stepNotes, stepRope } from './mco-physics';
import { type Cue, useMcoAudio } from './use-mco-audio';

/*
 Mic Cord Onboarding — the first screen of an invented podcast app.

 Look (the pin, a still of two screens; this is the first): a black page with pink and violet
 contour lines curling in from the corners, a pink retro microphone on a pink cable with music notes
 around it, "A Quick & Easy Way to Make Your Own Podcast" with "Easy Way" in pink, page dots and a
 pink Start Listening pill. In the pin a fist holds the mic; here it hangs on its cable.

 Motion (ours): the mic dangles from the top on a coiled cable, a Verlet rope. Grab it and fling it:
 it swings and twists on the cable, which stretches a little like a phone cord, and a fast swing puffs
 out music notes that float up and fade. The contour lines breathe.

 autoPlay: LOOP = 8 s. 1.0 s a finger grabs the mic and pulls it up to the right, 1.8 s lets go; the
 swing dies down and the mic is back at rest by 8 s, which equals frame 0.
 Reduced motion: the mic hangs still; dragging it moves it without notes.
*/

let debugState = '';
const H = 1 / 120;
const MIC = 1.4; // the mic's drawing scale

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};

/** A retro mic hanging head down: the handle at the cable end, a collar, a grilled capsule head. */
const drawMic = (c: SkCanvas, x: number, y: number, ang: number) => {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate((ang * 180) / Math.PI - 90, 0, 0);
  c.scale(MIC, MIC);
  const grad = (x0: number, x1: number, cols: string[]) => {
    const cs = [];
    for (let i = 0; i < cols.length; i++) cs.push(Skia.Color(cols[i]));
    return Skia.Shader.MakeLinearGradient({ x: x0, y: 0 }, { x: x1, y: 0 }, cs, null, 0);
  };
  // handle
  const hp = Skia.PathBuilder.Make();
  hp.moveTo(-13, 0);
  hp.lineTo(13, 0);
  hp.lineTo(19, 70);
  hp.lineTo(-19, 70);
  hp.close();
  const handle = Skia.Paint();
  handle.setAntiAlias(true);
  handle.setShader(grad(-19, 19, ['#7a1270', '#e43fcf', '#b0219e', '#5e0d56']));
  c.drawPath(hp.build(), handle);
  // collar
  const collar = Skia.Paint();
  collar.setAntiAlias(true);
  collar.setShader(grad(-30, 30, ['#5a0c52', '#ff7be6', '#c22eb0', '#4d0a46']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-30, 66, 60, 13), 4, 4), collar);
  // head: a capsule with a centre band
  const head = Skia.Paint();
  head.setAntiAlias(true);
  head.setShader(grad(-33, 33, ['#8f1683', '#ff6be3', '#ff4fd8', '#a81d98', '#5a0c52']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-33, 78, 66, 96), 33, 33), head);
  const slot = Skia.Paint();
  slot.setAntiAlias(true);
  slot.setStyle(1);
  slot.setStrokeWidth(3);
  slot.setStrokeCap(1);
  slot.setColor(Skia.Color('rgba(60,6,55,0.65)'));
  for (let i = 0; i < 9; i++) {
    const yy = 96 + i * 8.5;
    const half = 26 * Math.sqrt(Math.max(0, 1 - Math.pow((yy - 126) / 52, 2)));
    c.drawLine(-half, yy, half, yy, slot);
  }
  const band = Skia.Paint();
  band.setAntiAlias(true);
  band.setShader(grad(-6, 6, ['#6a0f60', '#ff9bec', '#6a0f60']));
  c.drawRect(Skia.XYWHRect(-5, 80, 10, 92), band);
  const hi = Skia.Paint();
  hi.setAntiAlias(true);
  hi.setColor(Skia.Color('#ffffff'));
  hi.setAlphaf(0.35);
  hi.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 4, true));
  c.drawOval(Skia.XYWHRect(-24, 92, 12, 54), hi);
  c.restore();
};

/** A note glyph: a head, a stem and a flag (kind 0), two beamed heads (1) or a plain stem (2). */
const drawNote = (c: SkCanvas, x: number, y: number, kind: number, a: number, rot: number, col: string) => {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(col));
  p.setAlphaf(a);
  const s = Skia.Paint();
  s.setAntiAlias(true);
  s.setStyle(1);
  s.setStrokeWidth(2.4);
  s.setStrokeCap(1);
  s.setColor(Skia.Color(col));
  s.setAlphaf(a);
  c.save();
  c.rotate(-20, 0, 0);
  c.drawOval(Skia.XYWHRect(-7, -5, 14, 10), p);
  c.restore();
  c.drawLine(6, -2, 6, -26, s);
  if (kind === 1) {
    c.save();
    c.translate(16, -4);
    c.rotate(-20, 0, 0);
    c.drawOval(Skia.XYWHRect(-7, -5, 14, 10), p);
    c.restore();
    c.drawLine(22, -6, 22, -30, s);
    const beam = Skia.Paint();
    beam.setAntiAlias(true);
    beam.setStyle(1);
    beam.setStrokeWidth(4.5);
    beam.setColor(Skia.Color(col));
    beam.setAlphaf(a);
    c.drawLine(6, -25, 22, -29, beam);
  } else if (kind === 0) {
    const fl = Skia.PathBuilder.Make();
    fl.moveTo(6, -26);
    fl.cubicTo(14, -22, 16, -16, 12, -10);
    c.drawPath(fl.build(), s);
  }
  c.restore();
};

type Props = { autoPlay?: boolean };

export function MicCordOnboarding({ autoPlay = false }: Props) {
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
    'MCO-Bold': require('./assets/fonts/Montserrat-Bold.ttf'),
    'MCO-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const AX = w * 0.52;
  const AY = insets.top - 30;

  const play = useMcoAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const tick = useSharedValue(0);
  const rope = useSharedValue<Rope>(makeRope(AX, AY));
  const rest = useSharedValue<Rope | null>(null);
  const acc = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const grabOff = useSharedValue({ dx: 0, dy: 0, at: 0, sx: 0, sy: 0 });

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __micCord?: unknown };
    g.__micCord = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__micCord;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** The rope hanging still: the loop starts and ends here. */
  const restPose = () => {
    'worklet';
    const r = makeRope(AX, AY);
    for (let i = 0; i < 600; i++) stepRope(r, AX, AY, H, 0.96);
    for (let i = 0; i < N; i++) {
      r.px[i] = r.x[i];
      r.py[i] = r.y[i];
    }
    return r;
  };
  const copyRope = (r: Rope) => {
    'worklet';
    return { ...r, x: r.x.slice(), y: r.y.slice(), px: r.px.slice(), py: r.py.slice(), notes: r.notes.slice() };
  };
  /** One fixed step of the scripted loop at loop time t. */
  const scriptStep = (r: Rope, t: number) => {
    'worklet';
    const rp = rest.get();
    if (t >= GRAB.at && t < GRAB.release && rp) {
      const k = smooth((t - GRAB.at) / (GRAB.release - GRAB.at - 0.15));
      r.held = 1;
      r.hx = rp.x[N - 1] + GRAB.dx * k;
      r.hy = rp.y[N - 1] + GRAB.dy * k;
    } else r.held = 0;
    stepRope(r, AX, AY, H, 0.992);
    stepNotes(r, H, reduced);
    // ease back onto the rest pose over the last 0.6 s, so the loop closes
    if (t > LOOP - 0.6 && rp) {
      const k = smooth((t - (LOOP - 0.6)) / 0.6) * 0.08;
      for (let i = 0; i < N; i++) {
        r.x[i] += (rp.x[i] - r.x[i]) * k;
        r.y[i] += (rp.y[i] - r.y[i]) * k;
        r.px[i] += (rp.px[i] - r.px[i]) * k;
        r.py[i] += (rp.py[i] - r.py[i]) * k;
      }
    }
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    if (!rest.get()) {
      rest.set(restPose());
      rope.set(copyRope(rest.get() as Rope));
    }
    const dbg = debugT.get();
    let t = time.get();
    let r = rope.get();
    if (dbg >= 0) {
      r = copyRope(rest.get() as Rope);
      let st = 0;
      while (st + H <= dbg) {
        st += H;
        scriptStep(r, st);
      }
      rope.set(r);
      t = dbg;
      debugT.set(-2);
    } else if (dbg !== -2) {
      const touched = r.held === 1 || now - last.get() < 2;
      let a = acc.get() + dt;
      if (auto && !touched) {
        while (a >= H) {
          a -= H;
          t += H;
          if (t >= LOOP) {
            t -= LOOP;
            r = copyRope(rest.get() as Rope);
            rope.set(r);
          }
          scriptStep(r, t);
        }
      } else {
        while (a >= H) {
          a -= H;
          if (!reduced || r.held === 1) stepRope(r, AX, AY, H, 0.992);
          stepNotes(r, H, reduced);
        }
      }
      acc.set(a);
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} held=${r.held} speed=${r.speed.toFixed(0)}`);
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
    const r = rope.get();
    const ph = (2 * Math.PI * time.get()) / LOOP;
    // contour lines curling in from two corners, breathing once a loop
    for (let side = 0; side < 2; side++) {
      for (let i = 0; i < 16; i++) {
        const pb = Skia.PathBuilder.Make();
        const base = side === 0 ? insets.top + 40 + i * 7 : h - 210 + i * 7;
        for (let k = 0; k <= 40; k++) {
          const x = side === 0 ? -20 + k * 7 : w - 260 + k * 7;
          const y = base + 26 * Math.sin(x * 0.018 + i * 0.16 + ph + side * 2) + (side === 0 ? -k * 1.4 : k * 0.6);
          if (k === 0) pb.moveTo(x, y);
          else pb.lineTo(x, y);
        }
        const lp = Skia.Paint();
        lp.setAntiAlias(true);
        lp.setStyle(1);
        lp.setStrokeWidth(1);
        lp.setColor(Skia.Color(i % 2 === 0 ? '#ff4fd8' : '#8a5cff'));
        lp.setAlphaf(0.22 + 0.25 * (1 - i / 16));
        c.drawPath(pb.build(), lp);
      }
    }
    // the cable: the rope smoothed, then coiled round it
    const pb = Skia.PathBuilder.Make();
    pb.moveTo(r.x[0], r.y[0]);
    for (let i = 1; i < N - 1; i++) pb.quadTo(r.x[i], r.y[i], (r.x[i] + r.x[i + 1]) / 2, (r.y[i] + r.y[i + 1]) / 2);
    pb.lineTo(r.x[N - 1], r.y[N - 1]);
    const cm = Skia.ContourMeasureIter(pb.build(), false, 1).next();
    if (cm) {
      const L = cm.length();
      const coil = Skia.PathBuilder.Make();
      let first = true;
      for (let s = 0; s <= L; s += 1.6) {
        const pt = cm.getPosTan(s);
        const nx = -pt[1].y;
        const ny = pt[1].x;
        const o = 5.5 * Math.sin((s / 8.5) * 2 * Math.PI) * Math.min(1, (L - s) / 20, s / 20 + 0.2);
        if (first) coil.moveTo(pt[0].x + nx * o, pt[0].y + ny * o);
        else coil.lineTo(pt[0].x + nx * o, pt[0].y + ny * o);
        first = false;
      }
      const cp = coil.build();
      const shadow = Skia.Paint();
      shadow.setAntiAlias(true);
      shadow.setStyle(1);
      shadow.setStrokeWidth(4.5);
      shadow.setStrokeJoin(1);
      shadow.setColor(Skia.Color('#7d1172'));
      c.drawPath(cp, shadow);
      const cord = Skia.Paint();
      cord.setAntiAlias(true);
      cord.setStyle(1);
      cord.setStrokeWidth(2.6);
      cord.setStrokeJoin(1);
      cord.setColor(Skia.Color('#ff5ad8'));
      c.drawPath(cp, cord);
    }
    // the mic, hanging along the cable's last segment
    const ang = Math.atan2(r.y[N - 1] - r.y[N - 3], r.x[N - 1] - r.x[N - 3]);
    drawMic(c, r.x[N - 1], r.y[N - 1], ang);
    // notes
    const cols = ['#ff7be6', '#b9a2ff', '#8ff0d0'];
    for (let i = 0; i < NOTES; i++) {
      const o = i * 6;
      const age = r.notes[o + 4];
      if (age > 1.6) continue;
      const a = Math.min(1, age / 0.15) * (1 - smooth((age - 0.9) / 0.7));
      drawNote(c, r.notes[o], r.notes[o + 1], r.notes[o + 5], a, Math.sin(age * 4 + i) * 18, cols[i % 3]);
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, insets.top]);

  // ---------------------------------------------------------------- touch
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const r = rope.get();
      last.set(clock.get());
      // the mic's middle, about 90 pt below the cable end along the cable
      const ang = Math.atan2(r.y[N - 1] - r.y[N - 3], r.x[N - 1] - r.x[N - 3]);
      const mx = r.x[N - 1] + Math.cos(ang) * 90 * MIC;
      const my = r.y[N - 1] + Math.sin(ang) * 90 * MIC;
      if (Math.hypot(e.x - mx, e.y - my) > 100) return;
      r.held = 1;
      grabOff.set({ dx: r.x[N - 1] - e.x, dy: r.y[N - 1] - e.y, at: clock.get(), sx: e.x, sy: e.y });
      r.hx = r.x[N - 1];
      r.hy = r.y[N - 1];
      scheduleOnRN(cue, 'grab');
      scheduleOnRN(buzz, 0);
    })
    .onUpdate((e) => {
      const r = rope.get();
      if (r.held !== 1) return;
      last.set(clock.get());
      const g = grabOff.get();
      r.hx = e.x + g.dx;
      r.hy = Math.max(AY + 40, e.y + g.dy);
    })
    .onFinalize((e) => {
      const r = rope.get();
      if (r.held !== 1) return;
      last.set(clock.get());
      r.held = 0;
      const g = grabOff.get();
      // a tap without a drag gives it a nudge
      if (Math.hypot(e.x - g.sx, e.y - g.sy) < 6) {
        r.px[N - 1] = r.x[N - 1] - 7;
        r.py[N - 1] = r.y[N - 1] + 2;
      }
      if (Math.hypot(e.velocityX, e.velocityY) > 500) scheduleOnRN(cue, 'swing');
      scheduleOnRN(buzz, 1);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="image" accessibilityLabel="A pink microphone hanging on its cable. Drag it to swing it.">
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.copy, { top: h * 0.56 }]} pointerEvents="none">
        <Text style={styles.title} allowFontScaling={false} accessibilityRole="header">
          A Quick & <Text style={styles.pink}>Easy Way</Text>
          {'\n'}to Make Your Own{'\n'}Podcast
        </Text>
        <View style={styles.dots}>
          <View style={[styles.dot, styles.dotOn]} />
          <View style={styles.dot} />
          <View style={styles.dot} />
        </View>
      </View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Start listening"
        onPress={() => {
          buzz(1);
          cue('notes');
        }}
        style={({ pressed }) => [styles.cta, { bottom: insets.bottom + 34 }, pressed && styles.pressed]}
      >
        <LinearGradient colors={['#ffa3d7', '#ff4fd8', '#c46bff']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.ctaFill}>
          <Text style={styles.ctaText} allowFontScaling={false}>
            Start Listening
          </Text>
        </LinearGradient>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0c0b0f' },
  copy: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  title: { color: '#ffffff', fontSize: 30, lineHeight: 38, fontFamily: 'MCO-Bold', textAlign: 'center', letterSpacing: -0.3 },
  pink: { color: '#ff63dd' },
  dots: { flexDirection: 'row', gap: 8, marginTop: 30, alignItems: 'center' },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.3)' },
  dotOn: { width: 18, backgroundColor: '#ff63dd' },
  cta: { position: 'absolute', alignSelf: 'center', width: 240, height: 56, borderRadius: 28, overflow: 'hidden' },
  ctaFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  ctaText: { color: '#ffffff', fontSize: 17, fontFamily: 'MCO-SemiBold' },
  pressed: { transform: [{ scale: 0.97 }] },
});

export { LOOP };
