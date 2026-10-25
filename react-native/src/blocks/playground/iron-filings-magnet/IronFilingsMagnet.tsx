import { Atlas, Canvas, Picture, Skia, matchFont, useColorBuffer, useRSXformBuffer } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, drawPaper, drawReadings, drawUnder, makeFilingSprite, spriteRects } from './ironArt';
import { FILINGS, LOOP, axial, fieldAt, magnetsAt, makeGeo, nearestPole, scatter } from './ironMotion';

/*
 Iron Filings Magnet — "Field Notes": 900 iron filings on a sheet of graph paper, a bar magnet
 under it. The filings line up along the field and crowd at the poles; move the magnet and they
 turn after it, the far ones later. A second magnet waits at the right edge.

 Rendering: one Canvas. The paper is a Picture recorded once; the magnets and readings a small
 Picture per frame; the filings two Atlas draws (shadow, then metal) of one sprite with 900
 RSXforms written on the UI thread. Angles and positions are computed per frame in a worklet: in
 the loop from the magnets' path a little in the past (16 delay buckets, 30 – 520 ms by
 distance), under the finger with one damped spring per filing (stiffer near the magnet).

 Touch: drag either magnet (pull B in by its tab on the right); it follows the finger on a spring
 (ζ 0.8). Tap a magnet to turn it end for end (north and south swap: attraction instead of
 repulsion). Haptics: a tick on pick-up and on a flip, a light tap when two poles come within
 70 pt. 2 s after the last touch both magnets ease home (≈ 0.6 s) and the loop restarts.
 Reduced motion: no loop; filings settle at once instead of turning.
 */

type Props = { autoPlay?: boolean };

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const BUCKETS = 16;
const MAG_K = 300;
const MAG_C = 2 * 0.8 * Math.sqrt(MAG_K);

function haptic(kind: 'tick' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type State = {
  ang: number[];
  vel: number[];
  ox: number[];
  oy: number[];
  /** How much a filing stands up in a pole's tuft (0 … 1), and how strong its field is (0 … 1). */
  stand: number[];
  pow: number[];
  /** Magnets now, their velocities, and where the finger wants them. */
  mag: number[];
  magV: number[];
  target: number[];
  /** Which magnet the finger holds (−1: none) and the grab offset. */
  held: number;
  grab: number[];
  near: number;
  tension: number;
};

export function IronFilingsMagnet({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const filings = useMemo(() => (g ? scatter(g) : null), [g]);
  const sprite = useMemo(() => makeFilingSprite(), []);
  const metalRects = useMemo(() => spriteRects(0), []);
  const shadowRects = useMemo(() => spriteRects(1), []);
  const fonts = useMemo<Fonts>(
    () => ({
      kicker: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
      title: matchFont({ fontFamily: SERIF, fontSize: 34, fontWeight: '700' }),
      sub: matchFont({ fontFamily: SERIF, fontSize: 14, fontWeight: '400' }),
      label: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
      value: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '600' }),
      pole: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '800' }),
    }),
    [],
  );
  const paper = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawPaper(canvas, g, fonts);
    return recorder.finishRecordingAsPicture();
  }, [g, size, fonts]);

  const now = useSharedValue(-1);
  const start = useSharedValue(-1);
  const resumeAt = useSharedValue(-1);
  const tick = useSharedValue(0);
  const state = useSharedValue<State | null>(null);
  const mags = useSharedValue<number[]>([0, 0, 0, 0, 0, Math.PI]);

  /** Angle and pull of filing i in a field of magnets m. */
  function filingTarget(i: number, m: number[], out: number[]) {
    'worklet';
    if (!g || !filings) return;
    const x = filings.x[i];
    const y = filings.y[i];
    const soft = 64 * g.u * g.u;
    const [bx, by] = fieldAt(x, y, m, g.half, soft);
    const strength = Math.hypot(bx, by) * g.half * g.half;
    const k = Math.min(1, Math.max(0, (strength - 0.003) / 0.03));
    const align = k * k * (3 - 2 * k);
    const a0 = filings.a0[i];
    out[0] = a0 + axial(Math.atan2(by, bx) - a0) * align;
    // Tufts: filings within ~160 pt of a pole are drawn in towards it (up to 26 pt, never past
    // it), so the ends crowd; the closest stand up.
    const [dx, dy, d] = nearestPole(x, y, m, g.half);
    const reach = Math.max(0, 1 - d / (160 * g.u));
    const move = Math.min(26 * g.u * Math.pow(reach, 1.6) * Math.min(1, strength * 4), d * 0.82);
    const pull = move / Math.max(d, 1);
    out[1] = dx * pull;
    out[2] = dy * pull;
    const near = Math.max(0, 1 - (d - move) / (44 * g.u));
    out[3] = near * near * align;
    out[4] = align;
  }

  function centreField(m: number[]) {
    'worklet';
    if (!g) return 0;
    const [bx, by] = fieldAt(g.cx, g.cy, m, g.half, 64 * g.u * g.u);
    return Math.hypot(bx, by) * g.half * g.half * 19;
  }

  useFrameCallback((frame) => {
    if (!g || !filings) return;
    const n = frame.timestamp / 1000;
    const prev = now.get();
    let s = state.get();
    if (!s) {
      const rest = magnetsAt(g, 0);
      s = {
        ang: new Array(FILINGS).fill(0),
        vel: new Array(FILINGS).fill(0),
        ox: new Array(FILINGS).fill(0),
        oy: new Array(FILINGS).fill(0),
        stand: new Array(FILINGS).fill(0),
        pow: new Array(FILINGS).fill(0),
        mag: rest.slice(),
        magV: [0, 0, 0, 0, 0, 0],
        target: rest.slice(),
        held: -1,
        grab: [0, 0],
        near: 0,
        tension: 0,
      };
      const out = [0, 0, 0, 0, 0];
      for (let i = 0; i < FILINGS; i += 1) {
        filingTarget(i, rest, out);
        s.ang[i] = out[0];
        s.ox[i] = out[1];
        s.oy[i] = out[2];
        s.stand[i] = out[3];
        s.pow[i] = out[4];
      }
      state.set(s);
      mags.set(rest);
      if (auto) start.set(n);
      tick.set(tick.get() + 1);
    }
    now.set(n);
    const dt = prev < 0 ? 0 : Math.min(n - prev, 1 / 30);
    const st = start.get();
    const out = [0, 0, 0, 0, 0];
    if (st >= 0 && n >= st) {
      // The loop: each filing reads the magnets as they were a moment ago.
      const t = (n - st) % LOOP;
      const nowM = magnetsAt(g, t);
      const past: number[][] = [];
      for (let b = 0; b < BUCKETS; b += 1) past.push(magnetsAt(g, (((t - 0.03 - b * 0.0327) % LOOP) + LOOP) % LOOP));
      for (let i = 0; i < FILINGS; i += 1) {
        const d = Math.hypot(filings.x[i] - nowM[0], filings.y[i] - nowM[1]);
        const b = Math.min(BUCKETS - 1, Math.floor(d / (520 * g.u) * BUCKETS));
        filingTarget(i, past[b], out);
        s.ang[i] = out[0];
        s.ox[i] = out[1];
        s.oy[i] = out[2];
        s.stand[i] = out[3];
        s.pow[i] = out[4];
        s.vel[i] = 0;
      }
      s.mag = nowM;
      s.target = nowM.slice();
      s.magV = [0, 0, 0, 0, 0, 0];
      mags.set(nowM);
      tick.set(tick.get() + 1);
      return;
    }
    // Under the finger: magnets on springs, each filing on its own spring.
    let moving = 0;
    for (let k = 0; k < 6; k += 1) {
      const a = MAG_K * (s.target[k] - s.mag[k]) - MAG_C * s.magV[k];
      s.magV[k] += a * dt;
      s.mag[k] += s.magV[k] * dt;
      moving += Math.abs(s.magV[k]) + Math.abs(s.target[k] - s.mag[k]);
    }
    if (reduced) s.mag = s.target.slice();
    const busy = moving > 0.05 || s.tension > 0.002 || s.held >= 0;
    if (busy || dt === 0) {
      let tension = 0;
      for (let i = 0; i < FILINGS; i += 1) {
        filingTarget(i, s.mag, out);
        if (reduced) {
          s.ang[i] = out[0];
          s.ox[i] = out[1];
          s.oy[i] = out[2];
        s.stand[i] = out[3];
        s.pow[i] = out[4];
          continue;
        }
        const d = Math.hypot(filings.x[i] - s.mag[0], filings.y[i] - s.mag[1]);
        const k = 340 / ((1 + d / (110 * g.u)) * (1 + d / (110 * g.u)));
        const c = 2 * 0.7 * Math.sqrt(k);
        const err = axial(out[0] - s.ang[i]);
        s.vel[i] += (k * err - c * s.vel[i]) * dt;
        s.ang[i] += s.vel[i] * dt;
        const f = 1 - Math.exp(-dt / 0.12);
        s.ox[i] += (out[1] - s.ox[i]) * f;
        s.oy[i] += (out[2] - s.oy[i]) * f;
        s.stand[i] += (out[3] - s.stand[i]) * f;
        s.pow[i] += (out[4] - s.pow[i]) * f;
        tension += Math.abs(err) + Math.abs(s.vel[i]) * 0.05;
      }
      s.tension = tension / FILINGS;
      mags.set(s.mag.slice());
      tick.set(tick.get() + 1);
    }
    // Two poles meeting: one light tap.
    const nn = Math.hypot(
      s.mag[0] + Math.cos(s.mag[2]) * g.half - (s.mag[3] + Math.cos(s.mag[5]) * g.half),
      s.mag[1] + Math.sin(s.mag[2]) * g.half - (s.mag[4] + Math.sin(s.mag[5]) * g.half),
    );
    const close = nn < 70 * g.u ? 1 : 0;
    if (close !== s.near) {
      s.near = close;
      if (close === 1 && s.held >= 0) scheduleOnRN(haptic, 'light');
    }
    const r = resumeAt.get();
    if (r >= 0 && n >= r && s.held < 0) {
      resumeAt.set(-1);
      s.target = magnetsAt(g, 0);
      start.set(n + 0.9);
    }
  });

  // Metal: tuft filings longer, weak-field ones a little shorter.
  const xforms = useRSXformBuffer(FILINGS, (val, i) => {
    'worklet';
    tick.get();
    const s = state.get();
    if (!s || !filings) return;
    const sc = (filings.len[i] / 48) * (0.8 + 0.2 * s.pow[i] + 0.5 * s.stand[i]);
    const cs = Math.cos(s.ang[i]) * sc;
    const sn = Math.sin(s.ang[i]) * sc;
    const x = filings.x[i] + s.ox[i];
    const y = filings.y[i] + s.oy[i];
    val.set(cs, sn, x - (cs * 24 - sn * 6), y - (sn * 24 + cs * 6));
  });

  // Shadows: a standing filing casts a longer, offset contact shadow.
  const shadowXforms = useRSXformBuffer(FILINGS, (val, i) => {
    'worklet';
    tick.get();
    const s = state.get();
    if (!s || !filings) return;
    const st = s.stand[i];
    const sc = (filings.len[i] / 48) * (0.8 + 0.2 * s.pow[i] + 0.8 * st);
    const cs = Math.cos(s.ang[i]) * sc;
    const sn = Math.sin(s.ang[i]) * sc;
    const x = filings.x[i] + s.ox[i] + 0.7 + 2.4 * st;
    const y = filings.y[i] + s.oy[i] + 1.2 + 3.2 * st;
    val.set(cs, sn, x - (cs * 24 - sn * 6), y - (sn * 24 + cs * 6));
  });

  // Tint (modulate): tufts darker, filings in a weak field fainter.
  const metalColors = useColorBuffer(FILINGS, (val, i) => {
    'worklet';
    tick.get();
    const s = state.get();
    if (!s) return;
    const k = 1 - 0.38 * s.stand[i];
    val[0] = k;
    val[1] = k;
    val[2] = k;
    val[3] = 0.5 + 0.5 * Math.max(s.pow[i], s.stand[i]);
  });

  const shadowColors = useColorBuffer(FILINGS, (val, i) => {
    'worklet';
    tick.get();
    const s = state.get();
    if (!s) return;
    val[0] = 1;
    val[1] = 1;
    val[2] = 1;
    val[3] = (0.55 + 0.45 * s.stand[i]) * (0.5 + 0.5 * Math.max(s.pow[i], s.stand[i]));
  });

  const under = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawUnder(canvas, g, mags.get(), fonts);
    return recorder.finishRecordingAsPicture();
  });

  const readings = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) {
      const m = mags.get();
      const onPaper = m[3] < g.paperX + g.paperW - 10 ? 4 : 2;
      drawReadings(canvas, g, centreField(m), onPaper, fonts);
    }
    return recorder.finishRecordingAsPicture();
  });

  function takeOver() {
    'worklet';
    const s = state.get();
    if (!s || !g) return;
    const n = now.get();
    const st = start.get();
    if (st >= 0 && n >= st) {
      s.target = s.mag.slice();
      s.magV = [0, 0, 0, 0, 0, 0];
    }
    start.set(-1);
    resumeAt.set(-1);
  }

  /** The magnet under (x, y): 0, 1 (B also by its tab at the right edge), or −1. */
  function pick(x: number, y: number) {
    'worklet';
    const s = state.get();
    if (!s || !g) return -1;
    for (let k = 0; k < 2; k += 1) {
      const dx = x - s.mag[k * 3];
      const dy = y - s.mag[k * 3 + 1];
      const a = s.mag[k * 3 + 2];
      const along = Math.abs(dx * Math.cos(a) + dy * Math.sin(a));
      const across = Math.abs(-dx * Math.sin(a) + dy * Math.cos(a));
      if (along < g.half + 26 * g.u && across < g.thick + 22 * g.u) return k;
    }
    if (x > g.w - 56 && Math.abs(y - s.mag[4]) < 44) return 1;
    return -1;
  }

  const drag = Gesture.Pan()
    .minDistance(4)
    .onStart((e) => {
      takeOver();
      const s = state.get();
      if (!s) return;
      const k = pick(e.x - e.translationX, e.y - e.translationY);
      s.held = k;
      if (k < 0) return;
      s.grab = [s.mag[k * 3] - (e.x - e.translationX), s.mag[k * 3 + 1] - (e.y - e.translationY)];
      if (k === 1 && s.mag[3] > g!.w - 20) s.grab = [-16, 0];
      scheduleOnRN(haptic, 'tick');
    })
    .onUpdate((e) => {
      const s = state.get();
      if (!s || s.held < 0 || !g) return;
      const k = s.held;
      const minY = g.fieldTop + 10;
      const maxY = g.fieldBottom - 10;
      s.target[k * 3] = Math.max(g.paperX - 20, Math.min(g.w + 30 * g.u, e.x + s.grab[0]));
      s.target[k * 3 + 1] = Math.max(minY, Math.min(maxY, e.y + s.grab[1]));
    })
    .onFinalize(() => {
      const s = state.get();
      if (s) s.held = -1;
      if (auto) resumeAt.set(now.get() + 2);
    });

  const flip = Gesture.Tap()
    .maxDuration(260)
    .onEnd((e) => {
      takeOver();
      const s = state.get();
      if (!s) return;
      const k = pick(e.x, e.y);
      if (k >= 0) {
        s.target[k * 3 + 2] += Math.PI;
        scheduleOnRN(haptic, 'tick');
      }
      if (auto) resumeAt.set(now.get() + 2);
    });

  const gesture = Gesture.Race(drag, flip);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && sprite ? (
        <GestureDetector gesture={gesture}>
          <View
            style={StyleSheet.absoluteFill}
            accessible
            accessibilityLabel="Iron filings on paper over a bar magnet. Drag a magnet to move the field, tap it to flip its poles."
          >
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={paper} />
              <Picture picture={under} />
              <Atlas image={sprite} sprites={shadowRects} transforms={shadowXforms} colors={shadowColors} colorBlendMode="modulate" />
              <Atlas image={sprite} sprites={metalRects} transforms={xforms} colors={metalColors} colorBlendMode="modulate" />
              <Picture picture={readings} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#2A1F18', overflow: 'hidden' },
});
