import { Canvas, ClipOp, PaintStyle, Picture, Skia, type SkCanvas, type SkFont, StrokeCap, TileMode, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BAR_X,
  CELL_H,
  CELL_W,
  DURATION,
  FOCAL,
  FRAME_STEP,
  type Layout,
  PIC_H,
  PIC_W,
  PIC_X,
  PIC_Y,
  RADIUS,
  REST_TIME,
  type Shown,
  barW,
  barY,
  clamp01,
  clock,
  drawScene,
  easeInOut,
  easeOutBack,
  shownAtLoop,
  smoothstep,
  stripY,
  timecode,
  titleY,
  transportY,
  video,
  wrap,
} from './scene';

/*
 Filmstrip Arc Scrubber — a video player whose scrubber lifts into a curved 3D filmstrip of frames.
 A port of the SwiftUI block (FilmstripArcScrubber.swift); the numbers are the same.

 The "video" is a one-minute night drive drawn as a function of its time (scene.ts): the light goes
 from golden hour through dusk to night, the sun sets by 0:30 and the moon rises after 0:34, stars
 come out, far mountains and near hills drift past at two speeds, lane dashes rush at the camera,
 lamp posts light up along the verge and a car's tail lights ride ahead. Scrubbing really rewinds
 and forwards it, and the strip's thumbnails are the same scene at their own times.

 Look: near-black #0A0A0C; the video fills the top 60 % (12 pt margins, 28 pt corners) with a title
 chip, an HD chip and a mono timecode; then the title (24 pt bold) and a line about the episode, a
 4 pt progress bar with a 14 pt thumb and times, and transport buttons (back 10, play/pause in a
 64 pt white circle, forward 10).

 The strip: frames every 2.5 s of video, each a 64 × 82 film cell (sprocket holes top and bottom, a
 56 × 60 picture), wrapped round a cylinder of radius 230 seen in perspective (focal 520), so the
 frames turn away (up to about 75°) and shrink towards the sides, darkening as they turn; a dock
 magnification (1 + 0.38 · e^(−d²/1.2)) swells the frames near the centre, where a fixed white
 magnifier frame with the time above it marks the chosen one. It lifts out of the bar 98 pt above
 it, each frame flying out of the thumb with a stagger (0.07 of the lift per frame from the centre),
 and slides 40 pt per frame under the finger. While it is up the title fades out and the video
 scrubs with a short horizontal motion blur. On release the chosen frame grows from the magnifier
 into the video (0.6 s, easeInOut), the strip folds back (0.5 s) and playback resumes.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 2.0   playing from 0:18
   2.0         the thumb is pressed: playback holds, the strip lifts (0.5 s, ease out back)
   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
   7.0         released: the 0:15 frame flies up into the video (0.6 s), playback resumes
   7.2 – 7.7   the strip folds back into the bar
   10.0        0:18 again, playing (the same frame as 0)

 Touch: press the bar and drag sideways to scrub (a tick haptic per frame), release to jump to the
 nearest frame; tap the video or the play button to pause or play; tap back or forward to skip
 10 s. 2 s after the last touch the video scrubs back to 0:18 (0.6 s) and the loop starts over.
 Reduced motion: no loop (paused at 0:18); the strip is flat and fades in, with no motion blur and
 no flight.
 */

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const SANS = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

type Fonts = { code: SkFont; label: SkFont; ten: SkFont };

/** The manual state on the UI thread. */
type Manual = {
  time0: number;
  at: number;
  playing: boolean;
  /** [from, to, start, duration, 1 = ease out back]. */
  lift: number[];
  flyAt: number;
  flyTime: number;
  speed: number;
  speedAt: number;
};

function liftAt(t: number[], now: number) {
  'worklet';
  const u = (now - t[2]) / t[3];
  return t[0] + (t[1] - t[0]) * (t[4] === 1 ? easeOutBack(u) : easeInOut(u));
}

function timeOf(m: Manual, now: number) {
  'worklet';
  return m.playing ? wrap(m.time0 + now - m.at) : m.time0;
}

function tactile(kind: 'tick' | 'thump') {
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

export function FilmstripArcScrubber({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const fonts = useMemo<Fonts>(
    () => ({
      // Menlo has only regular and bold: 500 would match bold, heavier than SF Mono Medium.
      code: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '400' }),
      label: matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '600' }),
      ten: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
    }),
    [],
  );
  const layout: Layout = { w: size.width, h: size.height, top: insets.top, bottom: insets.bottom };

  const clockSV = useSharedValue(-1);
  const autoStart = useSharedValue(-1);
  /** The scrub back to rest: [when, from time], or []. */
  const returning = useSharedValue<number[]>([]);
  const manual = useSharedValue<Manual>({
    time0: REST_TIME,
    at: 0,
    playing: false,
    lift: [0, 0, 0, 1, 0],
    flyAt: -1,
    flyTime: 0,
    speed: 0,
    speedAt: 0,
  });
  const zone = useSharedValue(0);
  const scrub = useSharedValue({ start: 0, last: 0, lastAt: 0, frame: 0 });
  const resumeAt = useSharedValue(-1);

  const shownAt = (now: number): Shown => {
    'worklet';
    const a = autoStart.get();
    if (a >= 0 && now >= a && !reduced) return shownAtLoop(now - a);
    const r = returning.get();
    if (r.length === 2) {
      const u = (now - r[0]) / 0.6;
      return { time: r[1] + (REST_TIME - r[1]) * easeInOut(u), lift: 0, fly: 0, flyTime: 0, speed: 0, playing: false };
    }
    const m = manual.get();
    let fly = 0;
    if (m.flyAt >= 0 && !reduced) {
      const u = (now - m.flyAt) / 0.6;
      if (u >= 0 && u < 1) fly = easeInOut(u);
    }
    const speed = reduced ? 0 : m.speed * Math.exp(-(now - m.speedAt) * 6);
    return { time: timeOf(m, now), lift: liftAt(m.lift, now), fly, flyTime: m.flyTime, speed, playing: m.playing };
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (clockSV.get() < 0) {
      manual.set({ ...manual.get(), at: now });
      if (autoPlay && !reduced) autoStart.set(now);
    }
    clockSV.set(now);
    const r = resumeAt.get();
    if (r >= 0 && now >= r) {
      resumeAt.set(-1);
      returning.set([now, shownAt(now).time]);
      autoStart.set(now + 0.6);
    }
  });

  const shown = useDerivedValue(() => shownAt(clockSV.get()));

  const gesture = useMemo(() => {
    const takeOver = (now: number) => {
      'worklet';
      resumeAt.set(-1);
      const s = shownAt(now);
      const m: Manual = { ...manual.get(), time0: s.time, at: now, playing: s.playing, lift: [s.lift, s.lift, 0, 1, 0] };
      // A flight under way carries on (roughly where it was).
      if (s.fly > 0) {
        m.flyAt = now - s.fly * 0.6;
        m.flyTime = s.flyTime;
      }
      manual.set(m);
      autoStart.set(-1);
      returning.set([]);
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = clockSV.get();
        takeOver(now);
        const by = barY(layout);
        const ty = transportY(layout);
        const v = video(layout);
        let z = 0;
        if (e.y > by - 36 && e.y < by + 30 && e.x > BAR_X - 20 && e.x < BAR_X + barW(layout) + 20) z = 1;
        else if (Math.hypot(e.x - layout.w / 2, e.y - ty) < 38) z = 2;
        else if (Math.hypot(e.x - (layout.w / 2 - 96), e.y - ty) < 30) z = 3;
        else if (Math.hypot(e.x - (layout.w / 2 + 96), e.y - ty) < 30) z = 4;
        else if (e.x >= v.x && e.x <= v.x + v.w && e.y >= v.y && e.y <= v.y + v.h) z = 5;
        zone.set(z);
        if (z === 1) {
          const m = manual.get();
          const t = timeOf(m, now);
          manual.set({ ...m, time0: t, playing: false, at: now, lift: [liftAt(m.lift, now), 1, now, reduced ? 0.2 : 0.45, reduced ? 0 : 1] });
          scrub.set({ start: t, last: t, lastAt: now, frame: Math.round(t / FRAME_STEP) });
          scheduleOnRN(tactile, 'thump');
        }
      })
      .onUpdate((e) => {
        if (zone.get() !== 1) return;
        const now = clockSV.get();
        const sc = scrub.get();
        const t = Math.min(DURATION - 0.01, Math.max(0, sc.start + (e.translationX / 40) * FRAME_STEP));
        const m = { ...manual.get(), time0: t };
        const dt = now - sc.lastAt;
        if (dt > 0.001) {
          m.speed = 0.6 * m.speed * Math.exp(-(now - m.speedAt) * 6) + (0.4 * (t - sc.last)) / dt;
          m.speedAt = now;
        }
        manual.set(m);
        const f = Math.round(t / FRAME_STEP);
        if (f !== sc.frame) scheduleOnRN(tactile, 'tick');
        scrub.set({ start: sc.start, last: t, lastAt: now, frame: f });
      })
      .onFinalize(() => {
        const now = clockSV.get();
        const z = zone.get();
        zone.set(0);
        const m = { ...manual.get() };
        if (z === 1) {
          const snap = Math.min(DURATION - FRAME_STEP, Math.round(m.time0 / FRAME_STEP) * FRAME_STEP);
          m.time0 = snap;
          m.at = now;
          m.playing = true;
          m.flyAt = now;
          m.flyTime = snap;
          m.lift = [liftAt(m.lift, now), 0, now + (reduced ? 0 : 0.2), reduced ? 0.2 : 0.5, 0];
          scheduleOnRN(tactile, 'thump');
        } else if (z === 2 || z === 5) {
          m.time0 = timeOf(m, now);
          m.at = now;
          m.playing = !m.playing;
          scheduleOnRN(tactile, 'thump');
        } else if (z === 3 || z === 4) {
          const d = z === 3 ? -10 : 10;
          m.time0 = wrap(timeOf(m, now) + d);
          m.at = now;
          m.speed = d * 3;
          m.speedAt = now;
          scheduleOnRN(tactile, 'thump');
        }
        manual.set(m);
        if (autoPlay && !reduced) resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [size.width, size.height, insets.top, insets.bottom, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, layout.w, layout.h));
    if (layout.w > 0) drawScreen(canvas, layout, shown.get(), reduced, fonts);
    return recorder.finishRecordingAsPicture();
  });

  const titleStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(shown.get().lift) }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const v = video(layout);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size.width > 0 ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Night Drive, episode 3, video player">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
          <View style={[styles.chip, { left: v.x + 16, top: v.y + 16 }]} pointerEvents="none">
            <Text style={styles.chipText}>Night Drive · Ep. 3</Text>
          </View>
          <View style={[styles.hdWrap, { left: v.x + v.w - 16 - 60, top: v.y + 19 }]} pointerEvents="none">
            <View style={styles.hd}>
              <Text style={styles.hdText}>HD</Text>
            </View>
          </View>
          <Animated.View style={[styles.title, { top: titleY(layout) + 28 - 28, width: layout.w - 48 }, titleStyle]} pointerEvents="none">
            <Text style={styles.titleText} numberOfLines={1}>
              Night Drive
            </Text>
            <Text style={styles.desc} numberOfLines={1}>
              Episode 3 · The coast road after midnight
            </Text>
          </Animated.View>
        </>
      ) : null}
    </View>
  );
}

// MARK: Drawing

/** Where the magnified centre frame's picture sits. */
function magnifiedPicture(l: Layout) {
  'worklet';
  const mag = 1.38;
  const cy = stripY(l) + (PIC_Y + PIC_H / 2 - CELL_H / 2) * mag;
  return { x: l.w / 2 - (PIC_W * mag) / 2, y: cy - (PIC_H * mag) / 2, w: PIC_W * mag, h: PIC_H * mag };
}

function transport(canvas: SkCanvas, l: Layout, playing: boolean, font: SkFont) {
  'worklet';
  const cy = transportY(l);
  const cx = l.w / 2;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Float32Array.of(1, 1, 1, 1));
  canvas.drawCircle(cx, cy, 32, paint);
  paint.setColor(Float32Array.of(0, 0, 0, 1));
  if (playing) {
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 9, cy - 11, 6, 22), 2, 2), paint);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 3, cy - 11, 6, 22), 2, 2), paint);
  } else {
    const tri = Skia.Path.Make();
    tri.moveTo(cx - 7, cy - 12);
    tri.lineTo(cx + 13, cy);
    tri.lineTo(cx - 7, cy + 12);
    tri.close();
    canvas.drawPath(tri, paint);
  }
  const white = Float32Array.of(1, 1, 1, 0.9);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(2);
  line.setStrokeCap(StrokeCap.Round);
  line.setColor(white);
  paint.setColor(white);
  for (const [dir, x] of [
    [-1, cx - 96],
    [1, cx + 96],
  ]) {
    // A circular arrow round "10", open at the top; its head turns back (or on) over the top.
    const arc = Skia.Path.Make();
    for (let k = 0; k <= 40; k++) {
      const a = ((-60 + (310 * k) / 40) * Math.PI) / 180;
      const qx = x + dir * -14 * Math.cos(a);
      const qy = cy + 14 * Math.sin(a);
      if (k === 0) arc.moveTo(qx, qy);
      else arc.lineTo(qx, qy);
    }
    canvas.drawPath(arc, line);
    // The head sits at the start (−60°), pointing along the arc away from it.
    const a0 = (-60 * Math.PI) / 180;
    const tipX = x + dir * -14 * Math.cos(a0);
    const tipY = cy + 14 * Math.sin(a0);
    const hx = -dir * Math.sin(a0);
    const hy = -Math.cos(a0);
    const head = Skia.Path.Make();
    head.moveTo(tipX + hx * 6, tipY + hy * 6);
    head.lineTo(tipX - hy * 4.5 * dir, tipY + hx * 4.5 * dir);
    head.lineTo(tipX + hy * 4.5 * dir, tipY - hx * 4.5 * dir);
    head.close();
    canvas.drawPath(head, paint);
    const tw = font.measureText('10').width;
    canvas.drawText('10', x - tw / 2, cy + 4, paint, font);
  }
}

/** One film cell, 64 × 82: black film, four sprocket holes along each edge, the picture. */
function cell(canvas: SkCanvas, time: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(0.06, 0.06, 0.07, 1));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, CELL_W, CELL_H), 3, 3), p);
  p.setColor(Float32Array.of(1, 1, 1, 0.32));
  for (let j = 0; j < 4; j++) {
    const x = 6 + j * 14.5;
    for (const y of [3, CELL_H - 7.5]) canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, 8.5, 4.5), 1.3, 1.3), p);
  }
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(PIC_X, PIC_Y, PIC_W, PIC_H), 2, 2), ClipOp.Intersect, true);
  drawScene(canvas, { x: PIC_X, y: PIC_Y, w: PIC_W, h: PIC_H }, time, true);
  canvas.restore();
}

/** The filmstrip on its cylinder. */
function strip(canvas: SkCanvas, l: Layout, s: Shown, reduce: boolean) {
  'worklet';
  const lift = s.lift;
  if (lift <= 0.001) return;
  const c = s.time / FRAME_STEP;
  const cx = l.w / 2;
  const cy = stripY(l) + (1 - Math.min(1, lift)) * 60;
  const thumbX = BAR_X + (barW(l) * s.time) / DURATION;
  const thumbY = barY(l);
  const last = Math.floor(DURATION / FRAME_STEP) - 1;
  const cells: number[][] = [];
  for (let k = Math.max(0, Math.floor(c) - 7); k <= Math.min(last, Math.floor(c) + 8); k++) cells.push([k - c, k]);
  cells.sort((a, b) => Math.abs(b[0]) - Math.abs(a[0]));
  for (const [d, k] of cells) {
    const theta = (d * CELL_W) / RADIUS;
    if (!reduce && Math.abs(theta) > 1.32) continue;
    if (reduce && Math.abs(d) > 3.4) continue;
    const u = reduce ? clamp01(lift) : easeInOut(lift * 1.5 - Math.abs(d) * 0.07);
    if (u <= 0.001) continue;
    const mag = reduce ? 1 : 1 + 0.38 * Math.exp((-d * d) / 1.2);
    const hw = (CELL_W / 2) * mag;
    const hh = (CELL_H / 2) * mag;
    // A point of the cell's plane (lx, ly from its centre) on screen.
    const corner = (lx: number, ly: number) => {
      if (reduce) return { x: cx + d * (CELL_W + 4) + lx, y: cy + ly };
      const x = RADIUS * Math.sin(theta) + Math.cos(theta) * lx;
      const z = RADIUS * (Math.cos(theta) - 1) - Math.sin(theta) * lx;
      const kz = FOCAL / (FOCAL - z);
      return { x: cx + x * kz, y: cy + ly * kz };
    };
    // Unfolding out of the thumb.
    const small = 0.12;
    const lerp = (ax: number, ay: number, b: { x: number; y: number }) => ({ x: ax + (b.x - ax) * u, y: ay + (b.y - ay) * u });
    const tl = lerp(thumbX - (CELL_W / 2) * small, thumbY - (CELL_H / 2) * small, corner(-hw, -hh));
    const tr = lerp(thumbX + (CELL_W / 2) * small, thumbY - (CELL_H / 2) * small, corner(hw, -hh));
    const bl = lerp(thumbX - (CELL_W / 2) * small, thumbY + (CELL_H / 2) * small, corner(-hw, hh));
    canvas.save();
    canvas.concat(Skia.Matrix([(tr.x - tl.x) / CELL_W, (bl.x - tl.x) / CELL_H, tl.x, (tr.y - tl.y) / CELL_W, (bl.y - tl.y) / CELL_H, tl.y, 0, 0, 1]));
    const layer = Skia.Paint();
    layer.setAlphaf(reduce ? u : smoothstep(0, 0.6, u));
    canvas.saveLayer(layer, Skia.XYWHRect(0, 0, CELL_W, CELL_H));
    cell(canvas, k * FRAME_STEP);
    const dark = reduce ? 0 : 0.7 * smoothstep(0.15, 1.3, Math.abs(theta));
    if (dark > 0.01) {
      const p = Skia.Paint();
      p.setColor(Float32Array.of(0, 0, 0, dark));
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, CELL_W, CELL_H), 3, 3), p);
    }
    canvas.restore();
    canvas.restore();
  }
  // The magnifier over the chosen frame (the time above it is drawn last, by timeChip).
  const a = reduce ? clamp01(lift) : smoothstep(0.4, 1, lift);
  const mag = reduce ? 1 : 1.38;
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(2);
  ring.setColor(Float32Array.of(1, 1, 1, 0.95 * a));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - (CELL_W * mag) / 2 - 3, cy - (CELL_H * mag) / 2 - 3, CELL_W * mag + 6, CELL_H * mag + 6), 8, 8), ring);
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0A0A0C' },
  chip: { position: 'absolute', height: 28, paddingHorizontal: 12, borderRadius: 14, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'center' },
  chipText: { fontSize: 13, fontWeight: '600', color: '#FFFFFF' },
  hdWrap: { position: 'absolute', width: 60, alignItems: 'flex-end' },
  hd: { height: 22, paddingHorizontal: 8, borderRadius: 6, borderWidth: 1.2, borderColor: 'rgba(255,255,255,0.7)', justifyContent: 'center' },
  hdText: { fontSize: 11, fontWeight: '700', color: '#FFFFFF' },
  title: { position: 'absolute', left: 24, height: 56, justifyContent: 'center', gap: 6 },
  titleText: { fontSize: 24, fontWeight: '700', color: '#FFFFFF' },
  desc: { fontSize: 14, color: 'rgba(255,255,255,0.55)' },
});

/** The time above the magnifier: over everything, like the SwiftUI version's text overlay. */
function timeChip(canvas: SkCanvas, l: Layout, s: Shown, reduce: boolean, font: SkFont) {
  'worklet';
  const lift = s.lift;
  if (lift <= 0.001) return;
  const a = reduce ? clamp01(lift) : smoothstep(0.4, 1, lift);
  const cx = l.w / 2;
  const label = clock(s.time);
  const lw = font.measureText(label).width + 16;
  const ly = stripY(l) - CELL_H * 0.69 - 18;
  const pill = Skia.Paint();
  pill.setAntiAlias(true);
  pill.setColor(Float32Array.of(1, 1, 1, a));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - lw / 2, ly - 10, lw, 20), 10, 10), pill);
  pill.setColor(Float32Array.of(0, 0, 0, a));
  canvas.drawText(label, cx - lw / 2 + 8, ly + 4.5, pill, font);
}

function drawScreen(canvas: SkCanvas, l: Layout, s: Shown, reduce: boolean, fonts: Fonts) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Float32Array.of(0.04, 0.04, 0.047, 1));
  canvas.drawRect(Skia.XYWHRect(0, 0, l.w, l.h), paint);
  // The video, smeared sideways while it is scrubbed.
  const v = video(l);
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(v.x, v.y, v.w, v.h), 28, 28), ClipOp.Intersect, true);
  drawScene(canvas, v, s.time, false);
  const blur = reduce ? 0 : clamp01((Math.abs(s.speed) - 1.5) / 10);
  if (blur > 0.02) {
    const dir = s.speed > 0 ? 1 : -1;
    for (let k = 1; k <= 3; k++) {
      const layer = Skia.Paint();
      layer.setAlphaf(0.3 * blur);
      canvas.saveLayer(layer);
      canvas.translate(-dir * k * 7 * blur, 0);
      drawScene(canvas, v, s.time - dir * k * 0.08, true);
      canvas.restore();
    }
  }
  canvas.restore();
  // The timecode over the video.
  // Progress bar and times.
  const by = barY(l);
  const bw = barW(l);
  const p = s.time / DURATION;
  const lift = clamp01(s.lift);
  paint.setColor(Float32Array.of(1, 1, 1, 0.18));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(BAR_X, by - 2, bw, 4), 2, 2), paint);
  paint.setColor(Float32Array.of(1, 1, 1, 0.9));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(BAR_X, by - 2, bw * p, 4), 2, 2), paint);
  const tx = BAR_X + bw * p;
  if (lift > 0.01) {
    const g = Skia.Paint();
    g.setShader(Skia.Shader.MakeRadialGradient({ x: tx, y: by }, 22, [Float32Array.of(1, 1, 1, 0.25 * lift), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp));
    canvas.drawCircle(tx, by, 22, g);
  }
  paint.setColor(Float32Array.of(1, 1, 1, 1));
  canvas.drawCircle(tx, by, 7 * (1 + 0.4 * lift), paint);
  paint.setColor(Float32Array.of(1, 1, 1, 0.55));
  canvas.drawText(clock(s.time), BAR_X, by + 24, paint, fonts.code);
  const remain = `-${clock(DURATION - s.time)}`;
  canvas.drawText(remain, BAR_X + bw - fonts.code.measureText(remain).width, by + 24, paint, fonts.code);
  transport(canvas, l, s.playing, fonts.ten);
  strip(canvas, l, s, reduce);
  // The chosen frame flying up into the video.
  if (s.fly > 0.001 && s.fly < 0.999) {
    const f = s.fly;
    const from = magnifiedPicture(l);
    const r = { x: from.x + (v.x - from.x) * f, y: from.y + (v.y - from.y) * f, w: from.w + (v.w - from.w) * f, h: from.h + (v.h - from.h) * f };
    const layer = Skia.Paint();
    layer.setAlphaf(1 - smoothstep(0.75, 1, f));
    canvas.saveLayer(layer);
    canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(r.x, r.y, r.w, r.h), 3 + 25 * f, 3 + 25 * f), ClipOp.Intersect, true);
    drawScene(canvas, r, s.flyTime, f < 0.5);
    canvas.restore();
  }
  // Text the SwiftUI version lays over its canvas: the timecode and the time above the magnifier.
  const code = timecode(s.time);
  const cw = fonts.code.measureText(code).width;
  paint.setColor(Float32Array.of(0, 0, 0, 0.35));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(v.x + 16, v.y + v.h - 40, cw + 20, 24), 12, 12), paint);
  paint.setColor(Float32Array.of(1, 1, 1, 0.9));
  canvas.drawText(code, v.x + 26, v.y + v.h - 40 + 16.5, paint, fonts.code);
  timeChip(canvas, l, s, reduce, fonts.label);
}
