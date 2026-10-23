import {
  BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Rect, Skia, vec, type SkPath,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient as Gradient } from 'expo-linear-gradient';
import { useCallback, useMemo } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  KEYS, SLOTS, dragTo, loadedSlot, loopScales, makeSim, pressKey, resetToRest, scrubTo, slotX, stepAuto, stepSim, tapeRadius, type Sim,
} from './deck-sim';
import { CHASSIS, HAPTIC, MATERIAL, PAGER, TRANSPORT, QA_B_PAGE, QA_HOLD, QA_T, RESUME_MS, SCRUB, STEP } from './motion';
import { BRAND, COLOR, KEY_LABELS, LAYOUT, SCREEN, SPACE, TRACKS } from './theme';

const ART = {
  cassette: require('./assets/cassette.png'),
  reel: require('./assets/reel.png'),
  keyRed: require('./assets/key-red.png'),
  keyRedDown: require('./assets/key-red-down.png'),
  keyDark: require('./assets/key-dark.png'),
  keyDarkDown: require('./assets/key-dark-down.png'),
  covers: [require('./assets/cover-1.png'), require('./assets/cover-2.png'), require('./assets/cover-3.png')],
};

function playHaptic(kind: number) {
  if (kind === HAPTIC.latch) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.release) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else if (kind === HAPTIC.commit) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
  else if (kind === HAPTIC.tick) Haptics.selectionAsync();
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
}

// Holds a frozen state for the replica's screenshots.
function qaHold(st: Sim) {
  'worklet';
  resetToRest(st);
  if (QA_HOLD === 'a' || QA_HOLD === 'b') { st.latched = true; st.d[0] = 0.86; st.lit[0] = 1; st.led = 1; }
  if (QA_HOLD === 'b') { st.p = QA_B_PAGE; st.d[2] = 1; st.lit[2] = 1; }
}

export function CassetteDeck({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const s = width / SCREEN.w;
  const oy = Math.max(0, (height - SCREEN.h * s) / 2);
  const reduced = useReducedMotion();
  const scales = useMemo(() => loopScales(), []);
  const sim = useSharedValue<Sim>((() => {
    const st = makeSim();
    st.k = scales;
    st.mode = autoPlay ? 0 : 1;
    return st;
  })());
  const p0 = useSharedValue(0);

  useFrameCallback((frame) => {
    'worklet';
    const st = sim.get();
    st.reduced = reduced;
    if (QA_HOLD) { qaHold(st); sim.set({ ...st }); return; }
    if (QA_T !== null) {
      if (st.acc !== -QA_T) {
        const q = makeSim();
        q.k = st.k;
        q.reduced = reduced;
        const n = Math.round(QA_T / STEP);
        for (let i = 0; i < n; i++) stepAuto(q);
        q.acc = -QA_T;
        sim.set(q);
      }
      return;
    }
    const dt = Math.min(0.05, (frame.timeSincePreviousFrame ?? 16) / 1000);
    st.acc += dt;
    while (st.acc >= STEP) {
      st.acc -= STEP;
      if (st.mode === 0) { stepAuto(st); continue; }
      st.idle += STEP;
      let free = !st.dragging && !st.scrubbing;
      for (let i = 0; i < KEYS; i++) if (st.held[i]) free = false;
      if (st.mode === 1 && autoPlay && free && st.idle * 1000 > RESUME_MS) st.mode = 2;
      if (st.mode === 2) {
        // Back to rest before the script resumes: the play key is released and the first cassette comes home.
        st.latched = false;
        st.target = SLOTS * Math.round(st.p / SLOTS);
        let still = Math.abs(st.p - st.target) < 0.002 && Math.abs(st.pv) < 0.01 && st.v === 0;
        for (let i = 0; i < KEYS; i++) if (st.d[i] > 0.002) still = false;
        for (let j = 0; j < SLOTS; j++) if (Math.abs(st.w[j]) > 0.003) still = false;
        if (still) { resetToRest(st); st.mode = 0; st.step = 0; continue; }
      }
      stepSim(st, STEP, true);
    }
    if (st.mode !== 0 && st.events.length) {
      for (let i = 0; i < st.events.length; i++) scheduleOnRN(playHaptic, st.events[i]);
    }
    st.events = [];
    // A new outer object each frame: setting the same reference does not re-run the animated styles.
    sim.set({ ...st });
  });

  const touch = useCallback(() => {
    'worklet';
    const st = sim.get();
    if (st.mode === 0) {
      for (let i = 0; i < KEYS; i++) st.held[i] = 0;
      if (st.dragging) dragTo(st, st.p, st.pv, true);
    }
    st.mode = 1;
    st.idle = 0;
    sim.set(st);
  }, [sim]);

  const press = useCallback((i: number, down: number) => {
    'worklet';
    touch();
    const st = sim.get();
    pressKey(st, i, down === 1);
    sim.set(st);
  }, [sim, touch]);

  const nudge = useCallback((dir: number) => {
    'worklet';
    touch();
    const st = sim.get();
    st.target = Math.round(st.target) + dir;
    if (st.reduced) { st.p = st.target; st.fade = 0; }
    sim.set(st);
  }, [sim, touch]);

  // One gesture on the cassette: started on a loaded reel it turns the reel (scrub), anywhere else it slides the cassettes.
  const grab = useSharedValue(0); // 0 slide, 1 left reel, 2 right reel
  const lastOmega = useSharedValue(0);
  const swipe = useMemo(() => {
    const C = LAYOUT.cassette;
    const centre = (i: number) => {
      'worklet';
      return { x: (C.x + LAYOUT.reels[i].cx) * s, y: (20 + LAYOUT.reels[i].cy) * s };
    };
    const packRadius = (st: Sim, i: number) => {
      'worklet';
      const slot = loadedSlot(st);
      return tapeRadius(i === 0 ? 1 - st.prog[slot] : st.prog[slot]);
    };
    return Gesture.Pan()
      .minDistance(2)
      .onBegin((e) => {
        touch();
        const st = sim.get();
        grab.set(0);
        if (loadedSlot(st) < 0 || st.reduced) return;
        for (let i = 0; i < 2; i++) {
          const o = centre(i);
          if (Math.hypot(e.x - o.x, e.y - o.y) < SCRUB.grabRadius * s) grab.set(i + 1);
        }
      })
      .onStart(() => { p0.set(sim.get().p); })
      .onUpdate((e) => {
        const st = sim.get();
        const g = grab.get();
        if (g === 0) dragTo(st, p0.get() - e.translationX / width, -e.velocityX / width, false);
        else {
          // Angular velocity of the finger about the reel's centre: ω = (r × v) / |r|².
          const o = centre(g - 1);
          const rx = e.x - o.x;
          const ry = e.y - o.y;
          const r2 = Math.max(rx * rx + ry * ry, 100 * s * s);
          const omega = (rx * e.velocityY - ry * e.velocityX) / r2;
          lastOmega.set(omega);
          scrubTo(st, omega, packRadius(st, g - 1), true);
        }
        sim.set(st);
      })
      .onFinalize((e) => {
        const st = sim.get();
        if (st.dragging) dragTo(st, st.p, -e.velocityX / width, true);
        if (st.scrubbing) scrubTo(st, lastOmega.get(), packRadius(st, grab.get() - 1), false);
        st.idle = 0;
        sim.set(st);
      });
  }, [sim, touch, p0, width, s, grab, lastOmega]);

  const L = LAYOUT;
  const c = L.cassette;

  return (
    <View style={[styles.screen, { backgroundColor: COLOR.bg }]}>
      <Backdrop sim={sim} s={s} oy={oy} width={width} height={height} />

      <GestureDetector gesture={swipe}>
        <View
          style={{ position: 'absolute', left: 0, top: oy + (c.y - 20) * s, width, height: (c.h + 40) * s }}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Cassette"
          accessibilityHint="Swipe left or right to change the cassette"
          accessibilityActions={[{ name: 'increment', label: 'Next cassette' }, { name: 'decrement', label: 'Previous cassette' }]}
          onAccessibilityAction={(e) => scheduleOnUI(nudge, e.nativeEvent.actionName === 'increment' ? 1 : -1)}
        >
          {[0, 1, 2].map((j) => <Cassette key={j} slot={j} sim={sim} s={s} width={width} />)}
        </View>
      </GestureDetector>

      {KEY_LABELS.map((label, i) => (
        <DeckKey key={label} index={i} label={label} sim={sim} s={s} oy={oy} press={press} />
      ))}
    </View>
  );
}

// Static and light-only layers: the lift above the cassette, the speaker grille, the LED and the key glyphs.
function Backdrop({ sim, s, oy, width, height }: { sim: { value: Sim }; s: number; oy: number; width: number; height: number }) {
  const L = LAYOUT;
  const grille = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const g = L.grille;
    const rows = Math.ceil(g.radius / (g.pitch * 0.866));
    for (let r = -rows; r <= rows; r++) {
      const y = r * g.pitch * 0.866;
      const shift = (Math.abs(r) % 2) * g.pitch * 0.5;
      for (let x = -g.radius - shift; x <= g.radius + g.pitch; x += g.pitch) {
        const px = x + shift;
        if (px * px + y * y > g.radius * g.radius) continue;
        b.addCircle((g.cx + px) * s, oy + (g.cy + y) * s, g.dot * s);
      }
    }
    return b.build();
  }, [s, oy, L.grille]);

  const glyphs = useMemo(() => glyphPaths(s, oy), [s, oy]);
  // The LED flickers with a seeded level while the tape plays (two incommensurate sines, deterministic per loop).
  const led = useDerivedValue(() => {
    const st = sim.value;
    const n = 0.5 + 0.25 * Math.sin(st.clock * 23.1) + 0.25 * Math.sin(st.clock * 37.7 + 1.3);
    return st.led * (1 - (st.reduced ? 0 : MATERIAL.ledFlicker * n * Math.min(1, Math.abs(st.v))));
  });
  // The lift above the cassette takes the hue of the cassettes in view, weighted by how close each is to the deck.
  const lift = useDerivedValue(() => {
    const st = sim.value;
    let r = 0; let g = 0; let b = 0; let a = 0; let wsum = 0;
    for (let j = 0; j < SLOTS; j++) {
      const wj = Math.max(0, 1 - Math.abs(slotX(j, st.p)));
      const c = COLOR.tints[j];
      r += c[0] * wj; g += c[1] * wj; b += c[2] * wj; a += c[3] * wj; wsum += wj;
    }
    const k = wsum > 0 ? 1 / wsum : 0;
    return [`rgba(${Math.round(r * k)}, ${Math.round(g * k)}, ${Math.round(b * k)}, ${(a * k).toFixed(3)})`, 'rgba(255,255,255,0)'];
  });
  const spill = useDerivedValue(() => (sim.value.reduced ? 0.6 : 1) * MATERIAL.keyGlow * Math.min(1, sim.value.lit[0]));
  const K = L.keys;
  const ledColor = useDerivedValue(() => interpolateColor(led.value, [0, 1], [COLOR.ledOff, COLOR.ledOn]));
  const pill = useMemo(() => pillPaths(s, oy), [s, oy]);

  const cx = (L.cassette.x + L.cassette.w / 2) * s;
  return (
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height}>
        <RadialGradient c={vec(cx, oy + 205 * s)} r={150 * s} colors={lift} />
      </Rect>
      <Path path={grille} color={COLOR.grille} />
      <Circle cx={(K.x0 + K.w / 2) * s} cy={oy + (K.y + K.h * 0.62) * s} r={SPACE.xxl * s} color={COLOR.redSpill} opacity={spill}>
        <BlurMask blur={22 * s} style="normal" />
      </Circle>
      <Circle cx={L.led.cx * s} cy={oy + L.led.cy * s} r={22 * s} color={COLOR.ledSpill} opacity={led}>
        <BlurMask blur={16 * s} style="normal" />
      </Circle>
      <Circle cx={L.led.cx * s} cy={oy + L.led.cy * s} r={L.led.r * 3 * s} color={COLOR.ledGlow} opacity={led}>
        <BlurMask blur={3 * s} style="normal" />
      </Circle>
      <Circle cx={L.led.cx * s} cy={oy + L.led.cy * s} r={L.led.r * s} color={ledColor} />
      {glyphs.map((g, i) => <Glyph key={i} path={g} index={i} sim={sim} s={s} />)}
      <Path path={pill.body}>
        <LinearGradient start={vec(0, pill.top)} end={vec(0, pill.bottom)} colors={[COLOR.pillTop, COLOR.pillBottom]} />
      </Path>
      <Path path={pill.note} color={COLOR.pillInk} />
    </Canvas>
  );
}

function Glyph({ path, index, sim, s }: { path: SkPath; index: number; sim: { value: Sim }; s: number }) {
  const lit = useDerivedValue(() => sim.value.lit[index]);
  const color = useDerivedValue(() => interpolateColor(lit.value, [0, 1], [COLOR.glyphOff, COLOR.glyphOn]));
  return (
    <Group>
      <Path path={path} color={COLOR.glyphGlow} opacity={lit}>
        <BlurMask blur={2.5 * s} style="normal" />
      </Path>
      <Path path={path} color={color} />
    </Group>
  );
}

// The small music button at the bottom left: a light grey pill with a note.
function pillPaths(s: number, oy: number) {
  const P = LAYOUT.pill;
  const x = P.x * s;
  const y = oy + P.y * s;
  const body = Skia.PathBuilder.Make();
  body.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, P.w * s, P.h * s), (P.h / 2) * s, (P.h / 2) * s));
  const note = Skia.PathBuilder.Make();
  const cx = x + (P.w / 2) * s;
  const cy = y + (P.h / 2) * s;
  note.addOval(Skia.XYWHRect(cx - 3.2 * s, cy + 1.2 * s, 3.4 * s, 2.6 * s));
  note.addRect(Skia.XYWHRect(cx - 0.2 * s, cy - 5 * s, 0.9 * s, 7.6 * s));
  note.moveTo(cx + 0.7 * s, cy - 5 * s);
  note.quadTo(cx + 2.6 * s, cy - 3.2 * s, cx + 3 * s, cy - 1.4 * s);
  note.lineTo(cx + 0.7 * s, cy - 2.6 * s);
  note.close();
  return { body: body.build(), note: note.build(), top: y, bottom: y + P.h * s };
}

function glyphPaths(s: number, oy: number) {
  const L = LAYOUT.glyphs;
  const out = [];
  for (let i = 0; i < 5; i++) {
    const cx = (L.x0 + i * LAYOUT.keys.pitch) * s;
    const cy = oy + L.y * s;
    const b = Skia.PathBuilder.Make();
    const P = (pts: number[][]) => {
      b.moveTo(cx + pts[0][0] * s, cy + pts[0][1] * s);
      for (let k = 1; k < pts.length; k++) b.lineTo(cx + pts[k][0] * s, cy + pts[k][1] * s);
      b.close();
    };
    if (i === 0) P([[-3, -4], [4, 0], [-3, 4]]);
    if (i === 1) { P([[-3.5, -4], [-1.2, -4], [-1.2, 4], [-3.5, 4]]); P([[1.2, -4], [3.5, -4], [3.5, 4], [1.2, 4]]); }
    if (i === 2) { P([[-4, -4], [2, 0], [-4, 4]]); P([[2.4, -4], [4.2, -4], [4.2, 4], [2.4, 4]]); }
    if (i === 3) { P([[0, -3.6], [-5, 0], [0, 3.6]]); P([[5, -3.6], [0, 0], [5, 3.6]]); }
    if (i === 4) { P([[0, -3.6], [5, 0], [0, 3.6]]); P([[-5, -3.6], [0, 0], [-5, 3.6]]); }
    out.push(b.build());
  }
  return out;
}

function Cassette({ slot, sim, s, width }: { slot: number; sim: { value: Sim }; s: number; width: number }) {
  const L = LAYOUT;
  const c = L.cassette;
  const track = TRACKS[slot];
  const frame = useAnimatedStyle(() => {
    const st = sim.value;
    const x = slotX(slot, st.p);
    const yaw = st.reduced ? 0 : Math.max(-PAGER.rotateMax, Math.min(PAGER.rotateMax, -st.pv * PAGER.rotatePerSpeed));
    return {
      opacity: st.reduced ? 0.35 + 0.65 * st.fade : 1,
      transform: [
        { perspective: 900 * s }, { translateX: x * width },
        { translateY: (st.dip * CHASSIS.dipTravel + st.rattle * CHASSIS.rattleTravel) * s }, { rotateY: `${yaw}deg` },
      ],
    };
  });
  // The shell's side faces show as it yaws, and a sheen fixed in the room slides over the plastic as it moves.
  const yawOf = (st: Sim) => {
    'worklet';
    return st.reduced ? 0 : Math.max(-1, Math.min(1, (-st.pv * PAGER.rotatePerSpeed) / PAGER.rotateMax));
  };
  const edgeL = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0, yawOf(sim.value)) }] }));
  const edgeR = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0, -yawOf(sim.value)) }] }));
  const sheen = useAnimatedStyle(() => {
    const st = sim.value;
    const x = slotX(slot, st.p);
    return {
      opacity: st.reduced ? 0 : Math.min(1, Math.abs(x) * MATERIAL.sheenGain + Math.abs(yawOf(st))),
      transform: [{ translateX: -x * MATERIAL.sheenTravel * c.w * s }],
    };
  });
  const reelL = useAnimatedStyle(() => ({ transform: [{ rotate: `${sim.value.ang[slot * 2]}rad` }] }));
  const reelR = useAnimatedStyle(() => ({ transform: [{ rotate: `${sim.value.ang[slot * 2 + 1]}rad` }] }));
  const packL = useAnimatedStyle(() => ({ transform: [{ scale: tapeRadius(1 - sim.value.prog[slot]) / L.tapePack.max }] }));
  const packR = useAnimatedStyle(() => ({ transform: [{ scale: tapeRadius(sim.value.prog[slot]) / L.tapePack.max }] }));
  // Angular motion blur when winding: ghost copies of the hub trail it by the angle turned in one 60 Hz shutter.
  const ghost = (reel: number, n: number) => {
    'worklet';
    const st = sim.value;
    const o = Math.abs(st.w[slot]) * TRANSPORT.baseOmega;
    const amt = st.reduced ? 0 : Math.max(0, Math.min(1, (o - MATERIAL.blurFrom) / MATERIAL.blurSpan));
    const lag = (st.w[slot] * TRANSPORT.baseOmega * MATERIAL.shutter * n) / MATERIAL.ghosts;
    return { opacity: amt * (0.55 - 0.15 * n), transform: [{ rotate: `${st.ang[slot * 2 + reel] - lag}rad` }] };
  };
  const ghosts = [
    [useAnimatedStyle(() => ghost(0, 1)), useAnimatedStyle(() => ghost(0, 2))],
    [useAnimatedStyle(() => ghost(1, 1)), useAnimatedStyle(() => ghost(1, 2))],
  ];

  const w = L.window;
  const pack = L.tapePack.max * 2 * s;
  return (
    <Animated.View
      pointerEvents="none"
      style={[{ position: 'absolute', left: c.x * s, top: 20 * s, width: c.w * s, height: c.h * s }, frame]}
    >
      <Animated.View style={[styles.edge, { left: -MATERIAL.edge * s + s, width: MATERIAL.edge * s, borderTopLeftRadius: 6 * s, borderBottomLeftRadius: 6 * s, transformOrigin: 'right' }, edgeL]}>
        <Gradient colors={COLOR.edgeL} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Animated.View style={[styles.edge, { right: -MATERIAL.edge * s + 3 * s, width: MATERIAL.edge * s, borderTopRightRadius: 6 * s, borderBottomRightRadius: 6 * s, transformOrigin: 'left' }, edgeR]}>
        <Gradient colors={COLOR.edgeR} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Image source={ART.cassette} style={StyleSheet.absoluteFill} contentFit="fill" />
      <View style={{ position: 'absolute', left: w.x * s, top: w.y * s, width: w.w * s, height: w.h * s, borderRadius: (w.h / 2) * s, overflow: 'hidden' }}>
        {[packL, packR].map((st, i) => (
          <Animated.View
            key={i}
            style={[{
              position: 'absolute', width: pack, height: pack, borderRadius: pack / 2, backgroundColor: COLOR.tape,
              borderWidth: s, borderColor: COLOR.tapeEdge,
              left: (L.reels[i].cx - w.x) * s - pack / 2, top: (L.reels[i].cy - w.y) * s - pack / 2,
            }, st]}
          />
        ))}
      </View>
      {ghosts.map((pair, i) => pair.map((st, n) => (
        <Animated.View
          key={`g${i}${n}`}
          style={[{
            position: 'absolute', width: L.reelSize * s, height: L.reelSize * s,
            left: (L.reels[i].cx - L.reelSize / 2) * s, top: (L.reels[i].cy - L.reelSize / 2) * s,
          }, st]}
        >
          <Image source={ART.reel} style={StyleSheet.absoluteFill} />
        </Animated.View>
      )))}
      {[reelL, reelR].map((st, i) => (
        <Animated.View
          key={i}
          style={[{
            position: 'absolute', width: L.reelSize * s, height: L.reelSize * s,
            left: (L.reels[i].cx - L.reelSize / 2) * s, top: (L.reels[i].cy - L.reelSize / 2) * s,
          }, st]}
        >
          <Image source={ART.reel} style={StyleSheet.absoluteFill} />
        </Animated.View>
      ))}
      <Image
        source={ART.covers[track.cover]}
        style={{ position: 'absolute', left: L.cover.x * s, top: L.cover.y * s, width: L.cover.size * s, height: L.cover.size * s, borderRadius: L.cover.radius * s }}
      />
      <Text
        numberOfLines={1}
        style={{ position: 'absolute', left: L.title.x * s, top: L.title.y * s, fontSize: L.title.size * s, fontWeight: '700', letterSpacing: L.title.tracking * s, color: COLOR.labelInk }}
      >
        {track.title}
      </Text>
      <Text
        numberOfLines={1}
        style={{ position: 'absolute', left: L.artist.x * s, top: L.artist.y * s, fontSize: L.artist.size * s, fontWeight: '400', letterSpacing: L.artist.tracking * s, color: COLOR.labelMute }}
      >
        {track.artist}
      </Text>
      <Text
        style={{ position: 'absolute', right: L.wordmark.right * s, top: L.wordmark.y * s, fontSize: L.wordmark.size * s, fontWeight: '900', letterSpacing: L.wordmark.tracking * s, color: COLOR.wordmark }}
      >
        {BRAND}
      </Text>
      <View style={[styles.sheenClip, { borderRadius: 7 * s, left: s, top: s, right: 3 * s, bottom: 6 * s }]}>
        <Animated.View style={[{ position: 'absolute', top: 0, bottom: 0, left: -c.w * 0.5 * s, width: c.w * 2 * s }, sheen]}>
          <Gradient
            colors={COLOR.sheen}
            locations={[0.3, 0.46, 0.5, 0.54, 0.7]}
            start={{ x: 0, y: 0.2 }}
            end={{ x: 1, y: 0.8 }}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>
      </View>
    </Animated.View>
  );
}

type KeyProps = {
  index: number;
  label: string;
  sim: { value: Sim };
  s: number;
  oy: number;
  press: (i: number, down: number) => void;
};

function DeckKey({ index, label, sim, s, oy, press }: KeyProps) {
  const K = LAYOUT.keys;
  const red = index === 0;
  const up = useAnimatedStyle(() => {
    const d = sim.value.d[index];
    const dip = sim.value.dip * CHASSIS.dipTravel * s;
    return { opacity: 1 - d, transform: [{ translateY: sim.value.reduced ? 0 : d * K.travel * s + dip }] };
  });
  const down = useAnimatedStyle(() => {
    const d = sim.value.d[index];
    const dip = sim.value.dip * CHASSIS.dipTravel * s;
    return { opacity: d, transform: [{ translateY: sim.value.reduced ? 0 : (d - 1) * K.travel * s + dip }] };
  });
  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .onBegin(() => press(index, 1))
    .onFinalize(() => press(index, 0)), [press, index]);
  const onTap = useCallback(() => {
    scheduleOnUI(press, index, 1);
    setTimeout(() => scheduleOnUI(press, index, 0), 160);
  }, [press, index]);

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={{ position: 'absolute', left: (K.x0 + index * K.pitch) * s, top: oy + K.y * s, width: K.w * s, height: K.h * s }}
        accessible
        accessibilityRole="button"
        accessibilityLabel={label}
        onAccessibilityTap={onTap}
      >
        <Animated.View style={[StyleSheet.absoluteFill, up]}>
          <Image source={red ? ART.keyRed : ART.keyDark} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, down]}>
          <Image source={red ? ART.keyRedDown : ART.keyDarkDown} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden' },
  edge: { position: 'absolute', top: 6, bottom: 10, overflow: 'hidden' },
  sheenClip: { position: 'absolute', overflow: 'hidden' },
});
