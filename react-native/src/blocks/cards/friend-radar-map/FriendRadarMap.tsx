import { BlurStyle, Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
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

import { FRIENDS, mapPicture, portraitPicture } from './frm-art';
import { type Cue, useFrmAudio } from './use-frm-audio';

/*
 Friend Radar Map — the "friends nearby" screen of an invented ride app.

 Look (the pin, a still of a two-screen promo; this is the first): a black street map, a friend's
 photo in a trapezoid frame glowing acid green where they are, smaller photo pins for others, a huge
 bitmap name with a white glow, two lines of mono caps and a dark Order button with a magenta knob.
 The pin's app and people are real; ours are invented, and the portraits are bitmap prints made by
 rules, not photos.

 Motion (ours): swipe the name sideways (or tap a pin) and the map's camera glides to the next
 friend, pulling back a little on the way; their pin grows into the glowing trapezoid, which leans a
 different way for each, and the name decodes letter by letter through random pixel glyphs. Hold the
 Order button: it fills magenta and the ride is on its way.

 autoPlay: LOOP = 9 s, three friends, 3 s each (glide 0-0.9 s, decode 0.3-1.1 s). Frame 9 s = 0.
 Reduced motion: the camera cuts with a fade, the name appears whole.
*/

let debugState = '';
const LOOP = 9;
const N = FRIENDS.length;
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=?';

const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
const easeOutBack = (k: number) => {
  'worklet';
  const x = clamp01(k);
  const c1 = 1.5;
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
};

type Props = { autoPlay?: boolean };

export function FriendRadarMap({ autoPlay = false }: Props) {
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
    'FRM-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
    'FRM-MonoBold': require('./assets/fonts/JetBrainsMono-Bold.ttf'),
  });
  const nameFont = useFont(require('./assets/fonts/PixelifySans-700.ttf'), 64);
  const labelFont = useFont(require('./assets/fonts/JetBrainsMono-Medium.ttf'), 11);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const FX = w / 2;
  const FY = insets.top + h * 0.3; // where the chosen friend sits on screen

  const map = useMemo(() => mapPicture(labelFont), [labelFont]);
  const faces = useMemo(() => FRIENDS.map((_, i) => portraitPicture(i, 46, 40, 3)), []);

  const play = useFrmAudio(!reduced);
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
  // the chosen friend, the one before, and when the move began (clock or loop time)
  const sel = useSharedValue({ cur: 0, prev: 0, since: 10 });
  const order = useSharedValue({ hold: 0, k: 0, doneAt: -10 });
  const debugT = useSharedValue(-1);
  const [cur, setCur] = useState(0);
  const [ordered, setOrdered] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __friendRadar?: unknown };
    g.__friendRadar = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__friendRadar;
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
    const s = sel.get();
    const od = order.get();
    let t = time.get();
    const scripted = dbg >= 0 || (auto && od.hold === 0 && now - last.get() >= 2);
    if (dbg >= 0) {
      t = dbg;
      debugT.set(-2);
    } else if (dbg === -2) {
      // pinned
    } else if (scripted) {
      t += dt;
      if (t >= LOOP) t -= LOOP;
    }
    if (scripted || dbg === -2) {
      const i = Math.floor(t / 3) % N;
      s.cur = i;
      s.prev = (i + N - 1) % N;
      s.since = t - i * 3;
    } else s.since += dt;
    // the order button: fills while held, drains when let go, done at full
    if (od.hold === 1 && od.k < 1) {
      od.k = Math.min(1, od.k + dt / 1.1);
      if (od.k >= 1) {
        od.doneAt = now;
        scheduleOnRN(setOrdered, true);
        scheduleOnRN(cue, 'ordered');
        scheduleOnRN(buzz, 2);
      }
    } else if (od.hold === 0 && od.k < 1) od.k = Math.max(0, od.k - dt * 3);
    if (od.k >= 1 && now - od.doneAt > 2.6) {
      od.k = 0;
      scheduleOnRN(setOrdered, false);
    }
    time.set(t);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} cur=${s.cur} since=${s.since.toFixed(2)}`);
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
      const s = sel.get();
      return s.since < 0.3 ? s.prev : s.cur;
    },
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setCur, n);
    },
  );

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = sel.get();
    const a = FRIENDS[s.prev];
    const b = FRIENDS[s.cur];
    // the camera: glides from the last friend to this one, pulling back a little half way
    const k = reduced ? 1 : easeInOut(s.since / 0.9);
    const camX = mix(a.x, b.x, k);
    const camY = mix(a.y, b.y, k);
    const zoom = reduced ? 1 : 1 - 0.22 * Math.sin(Math.PI * clamp01(s.since / 0.9));
    c.save();
    c.translate(FX, FY);
    c.scale(zoom, zoom);
    c.translate(-camX, -camY);
    c.drawPicture(map);
    c.restore();
    const toScreen = (x: number, y: number) => {
      'worklet';
      return { x: FX + (x - camX) * zoom, y: FY + (y - camY) * zoom };
    };
    // the other friends as small photo pins on leader lines
    for (let i = 0; i < N; i++) {
      if (i === s.cur && s.since > 0.35) continue;
      if (i === s.prev && s.prev !== s.cur && s.since < 0.3 && !reduced) continue;
      const f = FRIENDS[i];
      const p = toScreen(f.x, f.y);
      const dot = Skia.Paint();
      dot.setAntiAlias(true);
      dot.setColor(Skia.Color('#ffffff'));
      c.drawCircle(p.x, p.y, 4, dot);
      const line = Skia.Paint();
      line.setAntiAlias(true);
      line.setStrokeWidth(1.5);
      line.setColor(Skia.Color('#ffffff'));
      c.drawLine(p.x, p.y, p.x + 10, p.y - 22, line);
      c.save();
      c.translate(p.x + 2, p.y - 46);
      c.drawRect(Skia.XYWHRect(-1.5, -1.5, 31, 27), dot);
      c.scale(28 / 138, 24 / 120);
      c.drawPicture(faces[i]);
      c.restore();
    }
    // the chosen friend: the pin grows into the glowing trapezoid, leaning its own way; the one
    // before shrinks back into its pin first, so a change of friend is never a cut
    const gOut = reduced || s.prev === s.cur ? 0 : 1 - clamp01(s.since / 0.3) * clamp01(s.since / 0.3);
    const gIn = reduced ? 1 : easeOutBack((s.since - 0.35) / 0.55);
    for (let pass = 0; pass < 2; pass++) {
      const who = pass === 0 ? s.prev : s.cur;
      const g = pass === 0 ? gOut : gIn;
      if (g <= 0.01 || (pass === 0 && s.prev === s.cur)) continue;
      const fr = FRIENDS[who];
      const p = toScreen(fr.x, fr.y);
      const W = 150;
      const H = 104;
      const sk = fr.skew;
      const sc = mix(0.2, 1, g);
      c.save();
      c.translate(p.x, p.y - 40 * g);
      c.scale(sc, sc);
      const tz = Skia.PathBuilder.Make();
      tz.moveTo(-W / 2 - W * sk * 0.2, -H / 2);
      tz.lineTo(W / 2 - W * sk * 0.2, -H / 2 - H * sk * 0.25);
      tz.lineTo(W * 0.38 + W * sk * 0.15, H / 2);
      tz.lineTo(-W * 0.38 + W * sk * 0.15, H / 2 + H * sk * 0.2);
      tz.close();
      const path = tz.build();
      const pulse = 0.75 + 0.25 * Math.sin((2 * Math.PI * 3 * time.get()) / LOOP);
      const glow = Skia.Paint();
      glow.setAntiAlias(true);
      glow.setColor(Skia.Color('#8dff5a'));
      glow.setAlphaf(0.55 * pulse);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 28, true));
      c.drawPath(path, glow);
      c.save();
      c.clipPath(path, 1, true);
      c.translate(-W / 2 - 10, -H / 2 - 14);
      c.scale((W + 20) / 138, (H + 28) / 120);
      c.drawPicture(faces[who]);
      c.restore();
      const rim = Skia.Paint();
      rim.setAntiAlias(true);
      rim.setStyle(1);
      rim.setStrokeWidth(3);
      rim.setStrokeJoin(1);
      rim.setColor(Skia.Color('#0a0a0a'));
      c.drawPath(path, rim);
      c.restore();
    }
    // the name, decoding letter by letter
    if (nameFont) {
      const out = !reduced && s.prev !== s.cur && s.since < 0.3;
      const name = out ? a.name : b.name;
      const n = name.length;
      const chars: string[] = [];
      for (let i = 0; i < n; i++) {
        let ch = '';
        if (out) {
          // the last letters go first: scrambled, then gone
          const start = 0.03 + (n - 1 - i) * 0.025;
          if (s.since < start) ch = name[i];
          else if (s.since < start + 0.1) ch = GLYPHS[Math.floor(hash(i * 7 + Math.floor(s.since * 30)) * GLYPHS.length)];
        } else {
          const start = 0.3 + i * 0.07;
          const lock = start + 0.32;
          if (reduced || s.since >= lock) ch = name[i];
          else if (s.since >= start) ch = GLYPHS[Math.floor(hash(i * 13 + Math.floor(s.since * 22)) * GLYPHS.length)];
        }
        chars.push(ch === '' ? ' ' : ch);
      }
      const text = chars.join('');
      const tws = nameFont.getGlyphWidths(nameFont.getGlyphIDs(name));
      if (name.length === 0) return rec.finishRecordingAsPicture();
      let tw = 0;
      for (let i = 0; i < tws.length; i++) tw += tws[i];
      const fit = Math.min(1, (w - 40) / tw);
      const ny = h - insets.bottom - 228;
      c.save();
      c.translate(w / 2 - (tw * fit) / 2, ny);
      c.scale(fit, fit);
      const halo = Skia.Paint();
      halo.setAntiAlias(true);
      halo.setColor(Skia.Color('#ffffff'));
      halo.setAlphaf(0.55);
      halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
      c.drawText(text, 0, 0, halo, nameFont);
      const outline = Skia.Paint();
      outline.setAntiAlias(true);
      outline.setStyle(1);
      outline.setStrokeWidth(8);
      outline.setStrokeJoin(0);
      outline.setColor(Skia.Color('#050505'));
      c.drawText(text, 0, 0, outline, nameFont);
      const fill = Skia.Paint();
      fill.setAntiAlias(true);
      fill.setColor(Skia.Color('#ffffff'));
      c.drawText(text, 0, 0, fill, nameFont);
      c.restore();
    }
    // a ride ordered: magenta and green pixels burst up off the button (a hold-to-confirm payoff)
    const od = order.get();
    const since = clock.get() - od.doneAt;
    if (!reduced && od.k >= 1 && since >= 0 && since < 1.4) {
      const ox = 20 + 6 + 26;
      const oy = h - insets.bottom - 22 - 32;
      const pp = Skia.Paint();
      for (let i = 0; i < 40; i++) {
        const a = -Math.PI / 2 + (hash(i * 3.1) - 0.3) * 1.9;
        const v = 260 + hash(i * 7.7) * 420;
        const x = ox + Math.cos(a) * v * since;
        const y = oy + Math.sin(a) * v * since + 700 * since * since;
        pp.setColor(Skia.Color(i % 3 === 0 ? '#8dff5a' : i % 3 === 1 ? '#d63ce0' : '#ffffff'));
        pp.setAlphaf(1 - Math.min(1, Math.max(0, (since - 0.8) / 0.6)));
        const sz = 4 + (i % 3) * 2;
        c.drawRect(Skia.XYWHRect(Math.round(x / 3) * 3, Math.round(y / 3) * 3, sz, sz), pp);
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, FX, FY, map, faces, nameFont]);

  // ---------------------------------------------------------------- touch
  const go = (dir: number) => {
    'worklet';
    const s = sel.get();
    s.prev = s.cur;
    s.cur = (s.cur + dir + N) % N;
    s.since = 0;
    last.set(clock.get());
    scheduleOnRN(cue, 'glide');
    scheduleOnRN(buzz, 0);
  };
  const swipe = Gesture.Pan()
    .activeOffsetX([-14, 14])
    .onEnd((e) => {
      if (Math.abs(e.translationX) > 40 || Math.abs(e.velocityX) > 500) go(e.translationX < 0 ? 1 : -1);
    });
  const tapPin = Gesture.Tap().onEnd((e) => {
    // a tap near another friend's pin picks them
    const s = sel.get();
    const b = FRIENDS[s.cur];
    for (let i = 0; i < N; i++) {
      if (i === s.cur) continue;
      const f = FRIENDS[i];
      const x = FX + (f.x - b.x);
      const y = FY + (f.y - b.y) - 34;
      if (Math.abs(e.x - x) < 36 && Math.abs(e.y - y) < 36) {
        go(((i - s.cur + N) % N) === 1 ? 1 : -1);
        return;
      }
    }
  });
  const holdGesture = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(40)
    .onBegin(() => {
      const od = order.get();
      if (od.k >= 1) return;
      od.hold = 1;
      last.set(clock.get());
      scheduleOnRN(buzz, 1);
    })
    .onFinalize(() => {
      order.get().hold = 0;
      last.set(clock.get());
    });
  const fillStyle = useAnimatedStyle(() => {
    tick.get();
    return { width: `${order.get().k * 100}%` };
  });
  const decode = useAnimatedStyle(() => {
    tick.get();
    const s = sel.get();
    const k = s.since < 0.3 && s.prev !== s.cur ? 1 - clamp01(s.since / 0.25) : clamp01((s.since - 0.5) / 0.4);
    return { opacity: reduced ? 1 : k, transform: [{ translateY: (1 - k) * 6 }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;
  const f = FRIENDS[cur];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={Gesture.Exclusive(swipe, tapPin)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${f.name.toLowerCase()} ${f.street.toLowerCase()}`}
          accessibilityValue={{ text: `Friend ${cur + 1} of ${N}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => scheduleOnUI(go, e.nativeEvent.actionName === 'increment' ? 1 : -1)}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
          <LinearGradient colors={['rgba(11,11,13,0)', 'rgba(11,11,13,0.85)', '#0b0b0d']} locations={[0, 0.45, 1]} style={[styles.fade, { height: 330 }]} pointerEvents="none" />
        </View>
      </GestureDetector>
      <Animated.View style={[styles.lines, { bottom: insets.bottom + 120 }, decode]} pointerEvents="none">
        <Text style={styles.line} allowFontScaling={false}>
          {f.street}
        </Text>
        <Text style={[styles.line, styles.lineDim]} allowFontScaling={false}>
          {f.sooner}
        </Text>
      </Animated.View>
      <GestureDetector gesture={holdGesture}>
        <View
          style={[styles.order, { bottom: insets.bottom + 22 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={ordered ? 'Ride on the way' : `Order a ride to ${f.name.toLowerCase()} for 5 dollars, hold to confirm`}
        >
          <Animated.View style={[styles.orderFill, fillStyle]} />
          <View style={styles.knob}>
            <Text style={styles.knobGlyph} allowFontScaling={false}>
              {ordered ? '✓' : '›'}
            </Text>
          </View>
          <View style={styles.orderText}>
            <Text style={styles.orderLabel} allowFontScaling={false}>
              {ordered ? 'RIDE ON' : 'HOLD TO ORDER'}
            </Text>
            <Text style={styles.orderLabel} allowFontScaling={false}>
              {ordered ? 'THE WAY' : 'A RIDE FOR $5'}
            </Text>
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0b0b0d' },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  lines: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 4 },
  line: { color: '#ffffff', fontSize: 14, fontFamily: 'FRM-MonoBold', letterSpacing: 0.6 },
  lineDim: { color: 'rgba(255,255,255,0.6)', fontSize: 11, fontFamily: 'FRM-Mono' },
  order: { position: 'absolute', left: 20, right: 20, height: 64, borderRadius: 32, backgroundColor: '#1b1b1f', flexDirection: 'row', alignItems: 'center', overflow: 'hidden', borderWidth: 1, borderColor: 'rgba(255,255,255,0.08)' },
  orderFill: { position: 'absolute', left: 0, top: 0, bottom: 0, backgroundColor: '#c932d4' },
  knob: { width: 52, height: 52, borderRadius: 26, marginLeft: 6, backgroundColor: '#d63ce0', alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#0b0b0d' },
  knobGlyph: { color: '#0b0b0d', fontSize: 28, lineHeight: 32, fontFamily: 'FRM-MonoBold' },
  orderText: { flex: 1, alignItems: 'flex-end', paddingRight: 24 },
  orderLabel: { color: '#ffffff', fontSize: 13, fontFamily: 'FRM-MonoBold', letterSpacing: 0.5 },
});

export { LOOP };
