import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, type Layers, bakeBox, bakeTable, drawFrame, makeScene } from './matchstickArt';
import {
  BACK_FOR,
  IDLE_BEFORE_BACK,
  LOOP,
  MAX_SPARKS,
  type Pose,
  STRIKE_RUN,
  STRIKE_SPEED,
  type Sim,
  easeOut,
  hash,
  igniteTimes,
  loopPose,
  makeSim,
  simPose,
} from './matchstickMotion';
import { MATCH_FIRE_SKSL } from './matchstickShader';

/*
 Matchstick Strike Button — "Hearth", a candle shop's evening ritual whose call to action is lit
 with a real match.

 Look: a dark walnut table, a product card with an amber candle jar ("Fig & Cedar"), and a big
 oxblood matchbox with a sandpaper striker along its top edge, a printed cream border and the CTA
 "Strike to start" debossed in it. A long match lies above the box.

 Swipe the match head across the striker: fast enough (650 pt/s over 56 pt) and it catches —
 sparks are thrown back against the stroke and fall, the head flares into a fireball, a sulphur
 puff rises and a teardrop flame settles (a runtime shader that leans against the motion and
 flickers). Too slow, and only a dark scrape mark is left on the strip. Carry the flame under the
 CTA and its letters catch one by one (a white flash, then a gold glow); let go and the rest catch
 in a 60 ms chain. 2 s after the last touch the flame is blown out, the letters cool and a fresh
 match fades in at rest (0.9 s); then the loop starts again from t = 0 (matchstickMotion.ts).
 Reduced motion: no loop; the resting screen, and a strike still lights the CTA.
*/

type Props = {
  /** Plays the 8 s strike-and-light loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(MATCH_FIRE_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const INK = '#F5E6D3';
const MUTE = 'rgba(245,230,211,0.64)';
const FAINT = 'rgba(245,230,211,0.42)';
const EMBER = '#F0A35C';

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;

function feel(kind: 'strike' | 'scrape') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'strike') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

export function MatchstickStrikeButton({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const fonts = useMemo<Fonts>(
    () => ({
      cta: matchFont({ fontFamily: SANS, fontSize: 31, fontWeight: '600' }),
      caps: matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '700' }),
      small: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '400' }),
      jar: matchFont({ fontFamily: SERIF, fontSize: 7.5, fontWeight: '700' }),
    }),
    [],
  );
  const scene = useMemo(() => (size ? makeScene(size.width, size.height, insets.top, insets.bottom, fonts) : null), [size, insets.top, insets.bottom, fonts]);
  const ignite = useMemo(() => (scene ? igniteTimes(scene.geo) : []), [scene]);
  const layers = useMemo<Layers | null>(() => {
    if (!scene) return null;
    const scale = Math.min(3, PixelRatio.get());
    const table = bakeTable(scene, fonts, scale);
    const box = bakeBox(scene, fonts, scale);
    return table && box ? { table, box } : null;
  }, [scene, fonts]);

  const now = useSharedValue(0);
  /** The frame clock, kept running for gestures even while nothing is drawn. */
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const lastTouch = useSharedValue(0);
  const finger = useSharedValue(0);
  const sim = useSharedValue<Sim>(makeSim(15));
  // The finger's grab offset from the head, and the last update's time (for the stroke speed).
  const grab = useSharedValue<number[]>([0, 0, 0]);

  const pose = useDerivedValue<Pose | null>(() => {
    if (!scene) return null;
    const at = now.get();
    if (mode.get() !== AUTO) return simPose(sim.get(), at, scene.geo);
    if (!auto) return loopPose(0, scene.geo, ignite);
    return loopPose((((at - loopStart.get()) % LOOP) + LOOP) % LOOP, scene.geo, ignite);
  });

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    clock.set(at);
    if (loopStart.get() < 0) loopStart.set(at);
    if (!auto && mode.get() === AUTO && now.get() > 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m !== AUTO && scene) {
      const s = sim.get();
      // Held, the match rises off the table and turns: tail up while striking, down once lit.
      const lit = s.litAt >= 0 && s.outAt < 0;
      const lift = finger.get() === 1 || s.lift > 0.01 ? 1 : 0;
      s.lift += (lift - s.lift) * (1 - Math.exp(-dt * 10));
      const angle = lit ? 28 : -22;
      s.angle += (angle - s.angle) * (1 - Math.exp(-dt * 6));
      s.vx *= Math.exp(-dt * 6);
      const lean = Math.max(-1, Math.min(1, -s.vx / 520));
      s.lean += (lean - s.lean) * (1 - Math.exp(-dt * 12));
      if (s.litAt >= 0) s.burnt = easeOut((at - s.litAt) / 0.4);
      s.phase = (2 * Math.PI * at) / LOOP;
      if (m === TOUCH && finger.get() === 0 && at - lastTouch.get() > IDLE_BEFORE_BACK) {
        mode.set(BACK);
        s.backAt = at;
        s.back = simPose(s, at, scene.geo).match;
        if (s.litAt >= 0 && s.outAt < 0) s.outAt = at;
        s.cool = at;
      } else if (m === BACK && at >= s.backAt + BACK_FOR) {
        sim.set(makeSim(15));
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Hands whatever the loop shows to the simulation, so a finger never makes anything jump. */
  function takeOver(at: number) {
    'worklet';
    if (!scene) return;
    const p = pose.get();
    const s = makeSim(15);
    if (p && mode.get() === AUTO && p.match.front > 0.5 && p.match.alpha > 0.99) {
      s.x = p.match.x;
      s.y = p.match.y;
      s.angle = p.match.angle;
      s.lift = p.match.lift;
      s.burnt = p.match.burnt;
      if (p.lit > 0.05) {
        s.litAt = at - 1;
        s.px = p.match.x;
        s.py = p.match.y;
      } else if (p.match.burnt > 0.5) {
        // A spent match: swap it for a fresh one at rest.
        s.x = scene.geo.rest.x;
        s.y = scene.geo.rest.y;
        s.angle = 0;
        s.lift = 0;
        s.burnt = 0;
      }
      for (let i = 0; i < p.heat.length; i += 1) if (p.heat[i] > 0.02) s.ignite[i] = at - 0.5;
      if (p.lit < 0.05 && p.heat.some((h) => h > 0.02)) s.cool = at;
    } else if (mode.get() === TOUCH) {
      return;
    } else {
      s.x = scene.geo.rest.x;
      s.y = scene.geo.rest.y;
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  function addSpark(s: Sim, x: number, y: number, dir: number, at: number) {
    'worklet';
    const n = s.sparks.length / 6;
    const ang = (-150 + 120 * hash(n + at * 7.3)) * (Math.PI / 180);
    const sp = 140 + 300 * hash(n * 1.9 + at);
    s.sparks.push(x, y, -dir * Math.cos(ang) * sp, Math.sin(ang) * sp, at, 0.28 + 0.3 * hash(n * 3.1 + at));
    if (s.sparks.length > MAX_SPARKS * 6) s.sparks.splice(0, 6);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      const at = clock.get();
      takeOver(at);
      const s = sim.get();
      grab.set([s.x - e.x, s.y - e.y, at]);
      finger.set(1);
      lastTouch.set(at);
    })
    .onUpdate((e) => {
      if (!scene) return;
      const at = clock.get();
      const s = sim.get();
      const g = grab.get();
      const g2 = scene.geo;
      const x = Math.max(8, Math.min(scene.w - 8, e.x + g[0]));
      const y = Math.max(insets.top + 40, Math.min(scene.h - 20, e.y + g[1]));
      const dx = x - s.x;
      s.x = x;
      s.y = y;
      s.vx = 0.6 * s.vx + 0.4 * e.velocityX;
      lastTouch.set(at);
      const lit = s.litAt >= 0 && s.outAt < 0;
      const onStrip = y > g2.strikerTop - 10 && y < g2.strikerBottom + 10 && x > g2.strikerLeft && x < g2.strikerRight;
      if (!lit && onStrip && s.lift > 0.3) {
        if (Math.abs(e.velocityX) > STRIKE_SPEED) {
          s.run += Math.abs(dx);
          if (!reduced) {
            addSpark(s, x - 4, y, Math.sign(dx) || 1, at);
            if (Math.abs(dx) > 6) addSpark(s, x, y + 1, Math.sign(dx) || 1, at);
          }
          if (s.run >= STRIKE_RUN) {
            s.litAt = at;
            s.outAt = -1;
            s.px = x;
            s.py = y;
            s.cool = -1;
            for (let i = 0; i < s.ignite.length; i += 1) s.ignite[i] = -1;
            scheduleOnRN(feel, 'strike');
          }
        } else if (Math.abs(dx) > 0.5) {
          // Too slow: the head only scrapes the grit.
          if (at - s.scrape[2] > 0.4) {
            s.scrape = [x, x, at];
            scheduleOnRN(feel, 'scrape');
          } else s.scrape = [s.scrape[0], x, at];
          s.run = 0;
        }
      } else if (!onStrip) {
        s.run = 0;
      }
      if (lit) {
        for (let i = 0; i < s.ignite.length; i += 1) {
          if (s.ignite[i] < 0 && Math.abs(x - g2.letters[i]) < 14 && y > g2.baseline - 46 && y < g2.baseline + 44) s.ignite[i] = at;
        }
      }
    })
    .onFinalize(() => {
      const at = clock.get();
      const s = sim.get();
      finger.set(0);
      lastTouch.set(at);
      if (s.litAt >= 0 && s.outAt < 0) {
        // Whatever the flame did not reach catches in a chain from left to right.
        let k = 0;
        for (let i = 0; i < s.ignite.length; i += 1) {
          if (s.ignite[i] < 0) {
            s.ignite[i] = at + 0.15 + 0.06 * k;
            k += 1;
          }
        }
      }
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    if (scene && layers && p) drawFrame(canvas, scene, layers, p, fonts, EFFECT, P3);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  const jarRight = scene ? scene.cardX + 18 + Math.round((scene.cardH - 32) * 0.8) + 16 : 0;

  return (
    <View style={styles.root} onLayout={onLayout}>
      {scene && layers ? (
        <GestureDetector gesture={pan}>
          <View
            style={StyleSheet.absoluteFill}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Strike to start. Swipe the match quickly across the strip on the matchbox to light it."
          >
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>HEARTH</Text>
        <Text style={styles.title}>Light the evening</Text>
        <Text style={styles.subtitle}>Wind-down ritual · 45 min</Text>
      </View>

      {scene ? (
        <>
          <View pointerEvents="none" style={[styles.cardText, { top: scene.cardY + 18, left: jarRight, right: scene.w - scene.cardX - scene.cardW + 16 }]}>
            <Text style={styles.cardKicker}>TONIGHT’S CANDLE</Text>
            <Text style={styles.cardTitle} numberOfLines={1}>
              Fig & Cedar
            </Text>
            <Text style={styles.cardMeta} numberOfLines={1}>
              Hand-poured soy · 3 wicks
            </Text>
            <View style={styles.tags}>
              <Text style={styles.tag}>40 h burn</Text>
              <Text style={styles.tag}>Woody</Text>
            </View>
          </View>
          <View pointerEvents="none" style={[styles.footer, { top: scene.boxY + scene.boxH + 30 }]}>
            <View style={styles.footItem}>
              <Text style={styles.footValue}>21:30</Text>
              <Text style={styles.footLabel}>Lights down</Text>
            </View>
            <View style={styles.rule} />
            <View style={styles.footItem}>
              <Text style={styles.footValue} numberOfLines={1}>
                Low Embers
              </Text>
              <Text style={styles.footLabel}>Playlist</Text>
            </View>
            <View style={styles.rule} />
            <View style={styles.footItem}>
              <Text style={styles.footValue}>30 %</Text>
              <Text style={styles.footLabel}>Lamps</Text>
            </View>
          </View>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#150E0A', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, gap: 4 },
  kicker: { color: EMBER, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  cardText: { position: 'absolute', gap: 3 },
  cardKicker: { color: EMBER, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  cardTitle: { color: INK, fontSize: 20, fontWeight: '600' },
  cardMeta: { color: MUTE, fontSize: 13, fontWeight: '500' },
  tags: { flexDirection: 'row', gap: 6, marginTop: 6 },
  tag: {
    color: INK,
    fontSize: 12,
    fontWeight: '600',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: 'rgba(245,230,211,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(245,230,211,0.18)',
  },
  footer: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', alignItems: 'center' },
  footItem: { flex: 1, alignItems: 'center', gap: 2 },
  footValue: { color: INK, fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'] },
  footLabel: { color: FAINT, fontSize: 12, fontWeight: '500' },
  rule: { width: StyleSheet.hairlineWidth, height: 28, backgroundColor: 'rgba(245,230,211,0.18)' },
});
