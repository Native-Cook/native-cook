import { BlurMask, Canvas, Circle, Fill, Group, Shader, Skia, Text as SkiaText, TextBlob, matchFont, type SkFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { PixelRatio, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BOB_LIFT,
  BOB_TILT,
  type Finger,
  type Geo,
  type Home,
  LOOP,
  type Push,
  RIPPLES,
  RIPPLE_LIFE,
  type Sim,
  autoOffset,
  autoRipples,
  createSim,
  ghostAt,
  makePushes,
  ripplesAt,
  seg,
  simEnergy,
  stepSim,
  stirPos,
  swellAt,
} from './floatingLettersMotion';
import { WATER_SKSL } from './floatingLettersShader';

/*
 Floating Letters Stir — the opening screen of a meditation app. "let it settle" floats on a
 pale pool as twelve separate letters. A finger stirs the water: letters near its path are
 carried along, turn and drift, rings run out from the stroke and lift each letter as they
 pass, and then every letter eases home on its own spring until the phrase reads again.

 One Canvas: the water (a RuntimeEffect: depth gradient, caustics, rings, grain), then the
 letters from RSXform text blobs rebuilt on the UI thread (position, rotation and scale per
 glyph, measured at runtime): a soft shadow on the pool floor, a contact shadow, the bright
 rim where water clings to a floating thing, and the letter. The script and the live
 simulation are in floatingLettersMotion.ts, the water in floatingLettersShader.ts.
*/

type Props = {
  /** Plays the stir by itself every LOOP seconds; a touch takes over. */
  autoPlay?: boolean;
  /** The phrase, one string per line. */
  lines?: string[];
};

const DEFAULT_LINES = ['let it', 'settle'];
const CAPTION = 'Stir the water, then let it settle.';
const INK = '#173f47';
const MAX_SIZE = 96;
const SIDE = 34;
const LINE_GAP = 1.04;
const TRACKING = 0.02;
/** A glyph turns about the middle of its lowercase body: this far above the baseline, in em. */
const PIVOT_UP = 0.3;

const IDLE = 2;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const SPAWN_EVERY = 56;
const TICK_SPEED = 900;

/** Shadow on the pool floor: where it falls, and how far a ring's slope shifts it (refraction). */
const SHADOW_DX = 4;
const SHADOW_DY = 11;
const SHADOW_REFRACT = 5;

const DPR = PixelRatio.get();
const WIDE = Platform.OS === 'ios' ? 1 : 0;
const EFFECT = Skia.RuntimeEffect.Make(WATER_SKSL);

function advance(font: SkFont, text: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let total = 0;
  for (let i = 0; i < widths.length; i += 1) total += widths[i];
  return total;
}

/**
 The system's rounded face, bold. Families, never PostScript names (those draw nothing in Skia).
 Core Text answers an unknown family with Helvetica, so a face that measures like Helvetica was
 not found and the next one is tried.
*/
function roundedFont(size: number): SkFont {
  if (Platform.OS !== 'ios') return matchFont({ fontFamily: 'sans-serif', fontSize: size, fontWeight: 'bold' });
  const probe = 'settle';
  const missing = advance(matchFont({ fontFamily: 'Helvetica', fontSize: size, fontWeight: 'bold' }), probe);
  const families = ['.AppleSystemUIFontRounded', 'Arial Rounded MT Bold'];
  for (let i = 0; i < families.length; i += 1) {
    const font = matchFont({ fontFamily: families[i], fontSize: size, fontWeight: 'bold' });
    const w = advance(font, probe);
    if (w > 0 && Math.abs(w - missing) > size * 0.01) return font;
  }
  return matchFont({ fontFamily: 'System', fontSize: size, fontWeight: 'bold' });
}

type Layout = {
  font: SkFont;
  ids: number[];
  /** Pivot of each glyph relative to its pen position. */
  px: number[];
  py: number[];
  homes: Home[];
  pushes: Push[];
  geo: Geo;
  size: number;
  bottom: number;
};

function makeLayout(lines: string[], W: number, H: number, insetTop: number): Layout {
  let size = Math.min(MAX_SIZE, W * 0.245);
  let font = roundedFont(size);
  let widest = 0;
  for (let l = 0; l < lines.length; l += 1) widest = Math.max(widest, advance(font, lines[l]) + size * TRACKING * (lines[l].length - 1));
  if (widest > W - SIDE * 2) {
    size = Math.floor((size * (W - SIDE * 2)) / widest);
    font = roundedFont(size);
    widest = 0;
    for (let l = 0; l < lines.length; l += 1) widest = Math.max(widest, advance(font, lines[l]) + size * TRACKING * (lines[l].length - 1));
  }
  const gap = size * LINE_GAP;
  const block = gap * (lines.length - 1);
  const cy = Math.max(insetTop + 96 + block / 2 + size * 0.5, H * 0.4);
  const first = cy - block / 2 + size * PIVOT_UP;
  const ids: number[] = [];
  const px: number[] = [];
  const py: number[] = [];
  const homes: Home[] = [];
  for (let l = 0; l < lines.length; l += 1) {
    const line = lines[l];
    const lineIds = font.getGlyphIDs(line);
    const lineW = font.getGlyphWidths(lineIds);
    let x = (W - (advance(font, line) + size * TRACKING * (line.length - 1))) / 2;
    const baseline = first + gap * l;
    for (let k = 0; k < line.length; k += 1) {
      if (line[k] !== ' ') {
        ids.push(lineIds[k]);
        px.push(lineW[k] / 2);
        py.push(-size * PIVOT_UP);
        homes.push({ x: x + lineW[k] / 2, y: baseline - size * PIVOT_UP });
      }
      x += lineW[k] + size * TRACKING;
    }
  }
  const geo: Geo = { cx: W / 2, cy, hw: Math.min(W / 2 - 30, widest / 2 + 10), hh: block / 2 + size * 0.46, W, H };
  return { font, ids, px, py, homes, pushes: makePushes(homes, geo), geo, size, bottom: first + block + size * 0.3 };
}

/** Poses are a flat list of 6 per glyph: dx, dy, rotation, scale, shadow shift x, y. */
function blobFor(L: Layout, poses: number[], shadow: number) {
  'worklet';
  const xf = [];
  for (let i = 0; i < L.ids.length; i += 1) {
    const o = i * 6;
    const c = Math.cos(poses[o + 2]) * poses[o + 3];
    const s = Math.sin(poses[o + 2]) * poses[o + 3];
    const X = L.homes[i].x + poses[o] + shadow * (SHADOW_DX + poses[o + 4]);
    const Y = L.homes[i].y + poses[o + 1] + shadow * (SHADOW_DY + poses[o + 5]);
    xf.push(Skia.RSXform(c, s, X - c * L.px[i] + s * L.py[i], Y - s * L.px[i] - c * L.py[i]));
  }
  return Skia.TextBlob.MakeFromRSXformGlyphs(L.ids, xf, L.font);
}

function emptyRipples() {
  'worklet';
  const out: number[] = [];
  for (let k = 0; k < RIPPLES * 4; k += 1) out.push(0);
  return out;
}

function stirTick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function FloatingLettersStir({ autoPlay = true, lines = DEFAULT_LINES }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const L = useMemo(() => makeLayout(lines, W, H, insets.top), [lines, W, H, insets.top]);
  const count = L.ids.length;

  /** Loop time of the scripted stir. */
  const clock = useSharedValue(0);
  /** Ambient time (swell, caustics), period LOOP; keeps running under touch. */
  const amb = useSharedValue(0);
  /** Seconds since mount, for the age of live rings. */
  const live = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const idleFor = useSharedValue(0);
  const sim = useSharedValue<Sim | null>(null);
  const frame = useSharedValue(0);
  const finger = useSharedValue<Finger>({ x: 0, y: 0, vx: 0, vy: 0, on: 0 });
  const fingerAt = useSharedValue(0);
  const press = useSharedValue(0);
  /** Live rings: RIPPLES × [x, y, born (live time), strength]. */
  const liveRips = useSharedValue<number[]>(emptyRipples());
  const ripIndex = useSharedValue(0);
  const spawnedAt = useSharedValue(0);
  const spawnX = useSharedValue(0);
  const spawnY = useSharedValue(0);
  const ticked = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 50) / 1000;
    const now = live.get() + dt;
    live.set(now);
    if (!reduced) amb.set((amb.get() + dt) % LOOP);
    let fg = finger.get();
    // A finger that rests sends no updates: its last velocity must not keep pushing.
    if (fg.on > 0 && now - fingerAt.get() > 0.08 && (fg.vx !== 0 || fg.vy !== 0)) {
      fg = { x: fg.x, y: fg.y, vx: 0, vy: 0, on: 1 };
      finger.set(fg);
    }
    press.set(press.get() + (fg.on - press.get()) * Math.min(1, dt * 14));
    if (mode.get() === MODE_AUTO) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    const s = sim.get();
    if (!s) return;
    if (fg.on === 0) idleFor.set(idleFor.get() + dt);
    const settle = idleFor.get() > IDLE ? 1 : 0;
    const steps = dt > 1 / 90 ? 2 : 1;
    for (let k = 0; k < steps; k += 1) stepSim(s, L.homes, fg, L.geo, dt / steps, settle);
    sim.set(s);
    frame.set(frame.get() + 1);
    // Back to the script once the letters are home and the last ring has died: t = 0 is this rest.
    if (settle === 1 && simEnergy(s) < 0.3 && now - spawnedAt.get() > RIPPLE_LIFE) {
      clock.set(0);
      mode.set(MODE_AUTO);
    }
  });

  /** The rings on the water now: RIPPLES × [x, y, age, strength]. */
  const ripples = useDerivedValue(() => {
    if (mode.get() === MODE_AUTO) return autoRipples(clock.get(), L.geo, auto ? 1 : 0);
    const now = live.get();
    const b = liveRips.get();
    const out: number[] = [];
    for (let k = 0; k < RIPPLES; k += 1) {
      const age = now - b[k * 4 + 2];
      if (b[k * 4 + 3] > 0 && age < RIPPLE_LIFE) out.push(b[k * 4], b[k * 4 + 1], age, b[k * 4 + 3]);
      else out.push(0, 0, 0, 0);
    }
    return out;
  });

  /** Every glyph's pose: script or simulation, plus the swell and the rings passing under it. */
  const poses = useDerivedValue(() => {
    frame.get();
    const m = mode.get();
    const t = clock.get();
    const a = amb.get();
    const rips = ripples.get();
    const s = sim.get();
    const out: number[] = [];
    for (let i = 0; i < count; i += 1) {
      let dx = 0;
      let dy = 0;
      let rot = 0;
      let sc = 1;
      if (m === MODE_AUTO) {
        if (auto) {
          const o = autoOffset(t, i, L.pushes[i]);
          dx = o[0];
          dy = o[1];
          rot = o[2];
          sc = o[3];
        }
      } else if (s) {
        dx = s.x[i];
        dy = s.y[i];
        rot = s.r[i];
      }
      const sw = swellAt(a, i);
      const rp = ripplesAt(L.homes[i].x + dx, L.homes[i].y + dy, rips);
      out.push(dx, dy + sw[0] - BOB_LIFT * rp[0], rot + sw[1] + BOB_TILT * rp[1], sc, SHADOW_REFRACT * rp[1], SHADOW_REFRACT * rp[2]);
    }
    return out;
  });

  const letters = useDerivedValue(() => blobFor(L, poses.get(), 0));
  const floorShadow = useDerivedValue(() => blobFor(L, poses.get(), 1));

  /** The ghost finger of the script: [x, y, presence]. */
  const ghost = useDerivedValue(() => {
    if (mode.get() !== MODE_AUTO || !auto) return [0, 0, 0];
    const t = clock.get();
    const p = stirPos(t, L.geo);
    return [p[0], p[1], ghostAt(t)];
  });
  const ghostCentre = useDerivedValue(() => ({ x: ghost.get()[0], y: ghost.get()[1] }));
  const ghostOpacity = useDerivedValue(() => ghost.get()[2]);
  const ghostRadius = useDerivedValue(() => 17 * (1.25 - 0.25 * ghost.get()[2]));

  const uniforms = useDerivedValue(() => {
    const g = ghost.get();
    const f = finger.get();
    const touching = mode.get() !== MODE_AUTO;
    return {
      size: [W, H],
      focus: [L.geo.cx, L.geo.cy],
      phase: (Math.PI * 2 * amb.get()) / LOOP,
      rip: ripples.get(),
      touch: touching ? [f.x, f.y, press.get()] : [g[0], g[1], g[2]],
      wide: WIDE,
      dpr: DPR,
    };
  });

  // ---- Touch: the finger is the stir -----------------------------------------------------------
  function spawn(x: number, y: number, strength: number) {
    'worklet';
    const b = liveRips.get();
    const k = ripIndex.get();
    const next: number[] = [];
    for (let i = 0; i < RIPPLES * 4; i += 1) next.push(b[i]);
    next[k * 4] = x;
    next[k * 4 + 1] = y;
    next[k * 4 + 2] = live.get();
    next[k * 4 + 3] = strength;
    liveRips.set(next);
    ripIndex.set((k + 1) % RIPPLES);
    spawnedAt.set(live.get());
    spawnX.set(x);
    spawnY.set(y);
  }

  function takeOver() {
    'worklet';
    if (mode.get() === MODE_AUTO) {
      const s = createSim(count);
      const t = clock.get();
      if (auto) {
        // Start from where the script has the letters, moving as they were.
        const back = 1 / 60;
        for (let i = 0; i < count; i += 1) {
          const o = autoOffset(t, i, L.pushes[i]);
          const p = autoOffset(Math.max(0, t - back), i, L.pushes[i]);
          s.x[i] = o[0];
          s.y[i] = o[1];
          s.r[i] = o[2];
          s.vx[i] = (o[0] - p[0]) / back;
          s.vy[i] = (o[1] - p[1]) / back;
          s.vr[i] = (o[2] - p[2]) / back;
        }
      }
      sim.set(s);
      // The script's rings stay on the water: re-date them on the live clock.
      const scripted = autoRipples(t, L.geo, auto ? 1 : 0);
      const now = live.get();
      const kept: number[] = [];
      for (let k = 0; k < RIPPLES; k += 1) kept.push(scripted[k * 4], scripted[k * 4 + 1], now - scripted[k * 4 + 2], scripted[k * 4 + 3]);
      liveRips.set(kept);
      ripIndex.set(0);
      mode.set(MODE_TOUCH);
    }
    idleFor.set(0);
  }

  const stir = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced)
    .onBegin((e) => {
      takeOver();
      finger.set({ x: e.x, y: e.y, vx: 0, vy: 0, on: 1 });
      fingerAt.set(live.get());
      spawn(e.x, e.y, 0.7);
      ticked.set(0);
    })
    .onUpdate((e) => {
      finger.set({ x: e.x, y: e.y, vx: e.velocityX, vy: e.velocityY, on: 1 });
      fingerAt.set(live.get());
      idleFor.set(0);
      const speed = Math.hypot(e.velocityX, e.velocityY);
      if (Math.hypot(e.x - spawnX.get(), e.y - spawnY.get()) >= SPAWN_EVERY) spawn(e.x, e.y, 0.45 + 0.55 * seg(speed, 100, 1100));
      if (ticked.get() === 0 && speed > TICK_SPEED) {
        ticked.set(1);
        scheduleOnRN(stirTick);
      }
    })
    .onFinalize(() => {
      const f = finger.get();
      finger.set({ x: f.x, y: f.y, vx: 0, vy: 0, on: 0 });
      idleFor.set(0);
    });

  const phrase = lines.join(' ');
  // The line under the phrase is drawn on the water, so drifting letters pass over it, not under.
  const caption = useMemo(() => {
    const font = matchFont({ fontFamily: Platform.OS === 'ios' ? 'System' : 'sans-serif', fontSize: 17, fontWeight: '400' });
    return { font, x: (W - advance(font, CAPTION)) / 2, y: L.bottom + L.size * 0.34 + 17 };
  }, [W, L.bottom, L.size]);

  return (
    <View style={styles.root}>
      <GestureDetector gesture={stir}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="header" accessibilityLabel={`${phrase}. Stir the water with a finger and the letters drift, then settle.`}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>{EFFECT ? <Shader source={EFFECT} uniforms={uniforms} /> : null}</Fill>
            <SkiaText text={CAPTION} font={caption.font} x={caption.x} y={caption.y} color="rgba(23,63,71,0.62)" />
            <TextBlob blob={floorShadow} x={0} y={0} color="rgba(20,74,86,0.2)">
              <BlurMask blur={7} style="normal" />
            </TextBlob>
            <Group transform={CONTACT_SHIFT}>
              <TextBlob blob={letters} x={0} y={0} color="rgba(12,52,62,0.3)">
                <BlurMask blur={2.5} style="normal" />
              </TextBlob>
            </Group>
            <TextBlob blob={letters} x={0} y={0} style="stroke" strokeWidth={3.2} strokeJoin="round" color="rgba(255,255,255,0.5)">
              <BlurMask blur={1.2} style="normal" />
            </TextBlob>
            <TextBlob blob={letters} x={0} y={0} color={INK} />
            <Circle c={ghostCentre} r={ghostRadius} color="rgba(255,255,255,0.42)" opacity={ghostOpacity}>
              <BlurMask blur={1.5} style="normal" />
            </Circle>
            <Circle c={ghostCentre} r={ghostRadius} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.85)" opacity={ghostOpacity} />
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.brand, { top: insets.top + 18 }]}>
        <View style={styles.mark}>
          <View style={styles.markDot} />
        </View>
        <Text style={styles.brandName}>Stillwater</Text>
      </View>
      <View pointerEvents="none" style={[styles.foot, { bottom: insets.bottom + 22 }]}>
        <Text style={styles.session}>Evening unwind · 6 min</Text>
        <View style={styles.cta}>
          <LinearGradient colors={['#2c6c77', '#1f535d', '#173f47']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
          <Text style={styles.ctaText}>Begin</Text>
        </View>
      </View>
    </View>
  );
}

const CONTACT_SHIFT = [{ translateY: 2.5 }];

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#c5e3e1' },
  brand: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  mark: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 1.5,
    borderColor: 'rgba(23,63,71,0.78)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  markDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: 'rgba(23,63,71,0.78)' },
  brandName: { color: 'rgba(23,63,71,0.82)', fontSize: 16, fontWeight: '600', letterSpacing: 0.1 },
  foot: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 14 },
  session: { color: 'rgba(23,63,71,0.6)', fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  cta: {
    alignSelf: 'stretch',
    maxWidth: 420,
    width: '100%',
    height: 54,
    borderRadius: 27,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(13,50,58,0.32), 0 14px 30px rgba(13,50,58,0.22), inset 0 1px 0 rgba(255,255,255,0.22)',
  },
  ctaText: { color: '#f2f8f6', fontSize: 17, fontWeight: '600' },
});
