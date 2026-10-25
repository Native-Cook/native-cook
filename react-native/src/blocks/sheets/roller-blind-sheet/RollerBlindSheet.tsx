import { BlurMask, Canvas, Circle, Fill, Group, LinearGradient, Path, RadialGradient, Rect, RoundedRect, Shader, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BookIcon, BulbIcon, FanIcon, LampIcon, MoonIcon, SleepIcon, SunriseIcon, ThermoIcon } from './RollerBlindIcons';
import { RollNumber } from './RollerBlindRoll';
import { type BlindGeo, LOOP, cordAt, coverOf, detentFor, easeInOut, fingerAt, hemAt, makeGeo, rollRadius, rubberHem } from './rollerBlindMotion';
import { FABRIC_SKSL, ROOM_SKSL } from './rollerBlindShader';

/*
 Roller Blind Sheet — a smart-home room ("Nook — Bedroom") whose bottom sheet is a linen roller
 blind. Pull the bead on its cord down: the blind unrolls from the roller (the roll gets thinner),
 the morning light through the window dims the room and glows through the weave, and the scene
 presets printed on the fabric come down with it. Let go and the hem springs to the nearest detent
 (rolled up, the window's sill, or covering the room's controls) with a bounce, carrying the
 release velocity; a short tug from a detent lets the spring roller wind it back up. The cord
 swings as a pendulum driven by the hem's acceleration.

 Two Canvases: the room (ROOM_SKSL) under the controls, and the blind over them: an opaque
 rectangle painted with FABRIC_SKSL from the roller to the hem, the roll, hem bar and brackets as
 gradient shapes, blurred shadows, the cord and the bead. The printed scenes are RN views in a
 box clipped at the roller and at the hem (they only ever show on the fabric); the cards below the
 window are dimmed by one uniform overlay. The rest frame (the poster) is the blind half down at
 the sill. Two seconds after the last touch the blind eases back to the sill (0.9 s, ease-in-out)
 and the loop restarts from t = 0.
 Reduced motion: the blind sits at the sill, still; dragging still works, with no bounce.
 */

const INK = '#2B2622';
const MUTE = 'rgba(43,38,34,0.6)';
const PRINT = '#4A3626';
const PRINT_MUTE = '#6E5844';
const ACCENT = '#C9793A';
const IDLE = 2;
const RETURN = 0.9;
const MODE_AUTO = 0;
const MODE_HAND = 1;
const MODE_RETURN = 2;
/** The hem's spring after a release (ω = 2π / 0.5 s, ζ 0.55) and the cord's pendulum (1.4 Hz). */
const K_HEM = 158;
const C_HEM = 13.8;
const W_CORD = Math.PI * 2 * 1.4;
const CORD_LEN = 64;

const SCENES = [
  { title: 'Wind down', detail: 'Lamps at 20 %, 2700 K', Icon: MoonIcon },
  { title: 'Reading', detail: 'Bedside lamp, warm white', Icon: BookIcon },
  { title: 'Sleep', detail: 'Everything off, blind closed', Icon: SleepIcon },
  { title: 'Wake gently', detail: 'Sunrise light from 06:45', Icon: SunriseIcon },
];
const CONTENT_H = 292;

function impact() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

/** Where the hem rests (the poster): half down, at the window's sill. */
function restHemOf(g: BlindGeo) {
  'worklet';
  return g.half;
}

type Motion = {
  now: SharedValue<number>;
  mode: SharedValue<number>;
  autoStart: SharedValue<number>;
  lastTouch: SharedValue<number>;
  touching: SharedValue<number>;
  /** Hem [y, velocity, target], cord [angle (rad), angular velocity], drag [start hem, start detent]. */
  hem: SharedValue<number[]>;
  cord: SharedValue<number[]>;
  drag: SharedValue<number[]>;
  landed: SharedValue<number>;
  returnFrom: SharedValue<number[]>;
  returnStart: SharedValue<number>;
};

function Card({ label, value, children }: { label: string; value: string; children: ReactNode }) {
  return (
    <View style={styles.control}>
      <View style={styles.controlIcon}>{children}</View>
      <Text style={styles.controlLabel}>{label}</Text>
      <Text style={styles.controlValue}>{value}</Text>
    </View>
  );
}

export function RollerBlindSheet({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo<BlindGeo | null>(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const room = useMemo(() => Skia.RuntimeEffect.Make(ROOM_SKSL), []);
  const blind = useMemo(() => Skia.RuntimeEffect.Make(FABRIC_SKSL), []);

  const m: Motion = {
    now: useSharedValue(-1),
    mode: useSharedValue(MODE_AUTO),
    autoStart: useSharedValue(0),
    lastTouch: useSharedValue(0),
    touching: useSharedValue(0),
    hem: useSharedValue<number[]>([0, 0, 0]),
    cord: useSharedValue<number[]>([0, 0]),
    drag: useSharedValue<number[]>([0, 0]),
    landed: useSharedValue(1),
    returnFrom: useSharedValue<number[]>([0, 0]),
    returnStart: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    const prev = m.now.get();
    const dt = prev < 0 ? 0 : Math.min(0.033, now - prev);
    if (prev < 0) m.autoStart.set(now);
    m.now.set(now);
    if (!g) return;
    const mode = m.mode.get();
    if (mode === MODE_HAND) {
      const h = m.hem.get().slice();
      const c = m.cord.get().slice();
      if (m.touching.get() === 0) {
        // the hem springs to its detent carrying the release velocity
        const before = h[0] - h[2];
        const acc = -K_HEM * (h[0] - h[2]) - (reduced ? 2 * Math.sqrt(K_HEM) : C_HEM) * h[1];
        h[1] += acc * dt;
        h[0] += h[1] * dt;
        if (h[0] < g.up - 18 * g.unit) {
          h[0] = g.up - 18 * g.unit;
          h[1] = 0;
        }
        if (m.landed.get() === 0 && before * (h[0] - h[2]) <= 0) {
          m.landed.set(1);
          scheduleOnRN(impact);
        }
        // the cord swings on (a lightly damped pendulum), kicked when the hem stops hard
        c[1] += (-W_CORD * W_CORD * c[0] - 1.2 * c[1]) * dt;
        c[1] += (Math.max(-3000, Math.min(3000, acc)) / (CORD_LEN * g.unit)) * dt * 0.02;
        c[0] += c[1] * dt;
        if (now - m.lastTouch.get() > IDLE && Math.abs(h[1]) < 5 && autoPlay) {
          m.mode.set(MODE_RETURN);
          m.returnStart.set(now);
          m.returnFrom.set([h[0], c[0]]);
        }
      } else {
        c[0] *= Math.exp(-dt * 12);
        c[1] = 0;
      }
      m.hem.set(h);
      m.cord.set(c);
    } else if (mode === MODE_RETURN && now - m.returnStart.get() >= RETURN) {
      m.mode.set(MODE_AUTO);
      m.autoStart.set(now);
    }
  });

  /** [hem y, cord angle (deg), finger alpha] for this frame. */
  const state = useDerivedValue(() => {
    const now = m.now.get();
    if (!g) return [0, 0, 0];
    const mode = m.mode.get();
    if (mode === MODE_HAND) return [m.hem.get()[0], (m.cord.get()[0] * 180) / Math.PI, m.touching.get()];
    if (mode === MODE_RETURN) {
      const k = easeInOut(Math.min(1, (now - m.returnStart.get()) / RETURN));
      const from = m.returnFrom.get();
      return [from[0] + (restHemOf(g) - from[0]) * k, ((from[1] * 180) / Math.PI) * (1 - k), 0];
    }
    if (!auto) return [restHemOf(g), 0, 0];
    const t = (now - m.autoStart.get()) % LOOP;
    return [hemAt(g, t), cordAt(t), fingerAt(t)[0]];
  });

  const roomUniforms = useDerivedValue(() => {
    const geo = g ?? makeGeo(1, 1, 0, 0);
    return { size: [geo.w, geo.h], win: [geo.winX, geo.winY, geo.winW, geo.winH], dim: coverOf(geo, state.get()[0]) * 0.5, unit: geo.unit };
  });
  const fabricUniforms = useDerivedValue(() => {
    const geo = g ?? makeGeo(1, 1, 0, 0);
    const s = state.get();
    const cover = coverOf(geo, s[0]);
    return {
      win: [geo.winX, geo.winY, geo.winW, geo.winH],
      roll: [geo.x0, geo.x1, geo.rollerY, rollRadius(geo, s[0])],
      hem: [s[0], 0, 1 - cover * 0.3, geo.unit],
    };
  });
  // The blind's shapes: fabric from the roller's axis to the hem, the roll, the hem bar.
  const fabricH = useDerivedValue(() => (g ? Math.max(0, state.get()[0] - g.rollerY) : 0));
  const shadowH = useDerivedValue(() => fabricH.get() + 10 * (g?.unit ?? 1));
  const hemBarY = useDerivedValue(() => state.get()[0] - 2 * (g?.unit ?? 1));
  const hemShadowY = useDerivedValue(() => state.get()[0] + 6 * (g?.unit ?? 1));
  const rollR = useDerivedValue(() => (g ? rollRadius(g, state.get()[0]) : 0));
  const rollY = useDerivedValue(() => (g ? g.rollerY - rollR.get() : 0));
  const rollH = useDerivedValue(() => rollR.get() * 2);
  const rollTop = useDerivedValue(() => vec(0, rollY.get()));
  const rollBottom = useDerivedValue(() => vec(0, rollY.get() + rollH.get()));
  const hemTop = useDerivedValue(() => vec(0, hemBarY.get()));
  const hemBottom = useDerivedValue(() => vec(0, hemBarY.get() + 11 * (g?.unit ?? 1)));

  const cordTop = useDerivedValue(() => vec(g ? g.w / 2 : 0, state.get()[0] + 8 * (g?.unit ?? 1)));
  const bead = useDerivedValue(() => {
    const s = state.get();
    const a = (s[1] * Math.PI) / 180;
    const L = CORD_LEN * (g?.unit ?? 1);
    return vec((g ? g.w / 2 : 0) + Math.sin(a) * L, s[0] + 8 * (g?.unit ?? 1) + Math.cos(a) * L);
  });
  const cordPath = useDerivedValue(() => {
    const a = cordTop.get();
    const b = bead.get();
    return Skia.PathBuilder.Make().moveTo(a.x, a.y).lineTo(b.x, b.y).build();
  });
  const beadX = useDerivedValue(() => bead.get().x);
  const beadY = useDerivedValue(() => bead.get().y + 9);
  const beadLight = useDerivedValue(() => vec(bead.get().x - 3, bead.get().y + 6));
  const fingerAlpha = useDerivedValue(() => state.get()[2] * 0.9);
  const closed = useDerivedValue(() => (g ? Math.max(0, Math.min(100, ((state.get()[0] - g.up) / (g.full - g.up)) * 100)) : 0));

  // a tick as the hem passes the sill while dragged
  useAnimatedReaction(
    () => (g && m.mode.get() === MODE_HAND && m.touching.get() === 1 ? (state.get()[0] > g.half ? 1 : 0) : -1),
    (v, prev) => {
      if (v >= 0 && prev !== null && prev >= 0 && v !== prev) scheduleOnRN(tick);
    },
  );

  // The print's clip is a real box from under the roll down to the hem: its height follows the
  // hem (a small absolute subtree, so the layout pass per frame is cheap), and the print sits on
  // its bottom edge, so it only ever shows on the fabric.
  const printClip = useAnimatedStyle(() => {
    if (!g) return { height: 0 };
    return { height: Math.max(0, state.get()[0] - g.rollerY - 14 * g.unit) };
  });
  const dimStyle = useAnimatedStyle(() => ({ opacity: g ? coverOf(g, state.get()[0]) * 0.22 : 0 }));

  function grab(y: number) {
    'worklet';
    if (!g) return false;
    const s = state.get();
    const near = Math.abs(y - s[0]) < 60 * g.unit || Math.abs(y - (s[0] + CORD_LEN * g.unit)) < 50 * g.unit;
    if (!near) return false;
    const atDetent = Math.abs(s[0] - g.half) < 6 || Math.abs(s[0] - g.full) < 6;
    m.mode.set(MODE_HAND);
    m.touching.set(1);
    m.lastTouch.set(m.now.get());
    m.hem.set([s[0], 0, s[0]]);
    m.cord.set([(s[1] * Math.PI) / 180, 0]);
    m.drag.set([s[0], atDetent ? 1 : 0]);
    return true;
  }

  function pull(ty: number) {
    'worklet';
    if (!g || m.touching.get() === 0) return;
    const d = m.drag.get();
    const h = m.hem.get();
    m.hem.set([rubberHem(g, d[0] + ty), h[1], h[2]]);
    m.lastTouch.set(m.now.get());
  }

  function letGo(ty: number, vy: number) {
    'worklet';
    if (!g || m.touching.get() === 0) return;
    const d = m.drag.get();
    const h = m.hem.get();
    // a short tug from a detent lets the spring roller wind the blind up
    const tug = d[1] === 1 && ty > 4 && ty < 44 * g.unit && vy < 900;
    const target = tug ? g.up : detentFor(g, h[0], vy);
    m.hem.set([h[0], vy, target]);
    m.landed.set(0);
    m.touching.set(0);
    m.lastTouch.set(m.now.get());
    // the bead swings as the hand lets go
    m.cord.set([0, -Math.max(-6, Math.min(6, vy / 400))]);
  }

  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      if (grab(e.y)) scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      pull(e.translationY);
    })
    .onEnd((e) => {
      letGo(e.translationY, e.velocityY);
    })
    .onFinalize((e) => {
      letGo(e.translationY, e.velocityY);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const u = g?.unit ?? 1;
  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && room && blind ? (
        <>
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={room} uniforms={roomUniforms} />
            </Fill>
          </Canvas>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
            <Text style={styles.kicker}>NOOK · BEDROOM</Text>
            <Text style={styles.title}>Bedroom</Text>
            <View style={styles.meta}>
              <Text style={styles.metaText}>21.5 °C · 3 lights on · Blind </Text>
              <RollNumber value={closed} digits={3} line={20} style={styles.metaText} suffix="% closed" />
            </View>
          </View>

          <View pointerEvents="none" style={[styles.controls, { top: g.controlsY, bottom: insets.bottom + 16 }]}>
            <View style={styles.row}>
              <Card label="Ceiling light" value="60 %">
                <BulbIcon color={INK} />
              </Card>
              <Card label="Bedside lamp" value="Warm · 30 %">
                <LampIcon color={INK} />
              </Card>
            </View>
            <View style={styles.row}>
              <Card label="Heating" value="21.5 °C">
                <ThermoIcon color={INK} />
              </Card>
              <Card label="Air purifier" value="Auto · quiet">
                <FanIcon color={INK} />
              </Card>
            </View>
          </View>
          <Animated.View pointerEvents="none" style={[styles.dim, { top: g.controlsY - 10 * u }, dimStyle]} />

          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable" accessibilityLabel="Roller blind. Pull the bead down to lower it; a short tug rolls it up.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Rect x={g.x0 - 3 * u} y={g.rollerY} width={g.x1 - g.x0 + 6 * u} height={shadowH} color="rgba(40,28,18,0.22)">
                  <BlurMask blur={6 * u} style="normal" />
                </Rect>
                {/* a plain linen backing under the shaded fabric, so the blind can never be see-through */}
                <Rect x={g.x0} y={g.rollerY} width={g.x1 - g.x0} height={fabricH} color="#D9CDB8" />
                <Rect x={g.x0} y={g.rollerY} width={g.x1 - g.x0} height={fabricH}>
                  <Shader source={blind} uniforms={fabricUniforms} />
                </Rect>
                <Rect x={g.x0} y={hemShadowY} width={g.x1 - g.x0} height={10 * u} color="rgba(40,28,18,0.25)">
                  <BlurMask blur={5 * u} style="normal" />
                </Rect>
                <RoundedRect x={g.x0 - 4 * u} y={hemBarY} width={g.x1 - g.x0 + 8 * u} height={11 * u} r={3 * u}>
                  <LinearGradient start={hemTop} end={hemBottom} colors={['#C49A6C', '#93694A', '#5E4029']} />
                </RoundedRect>
                <RoundedRect x={g.x0 - 4 * u} y={rollY} width={g.x1 - g.x0 + 8 * u} height={rollH} r={rollR}>
                  <LinearGradient start={rollTop} end={rollBottom} colors={['#9E9482', '#EDE4D3', '#D8CDB9', '#8A806F']} positions={[0, 0.32, 0.7, 1]} />
                </RoundedRect>
                <RoundedRect x={g.x0 - 15 * u} y={g.rollerY - 16 * u} width={13 * u} height={32 * u} r={3 * u}>
                  <LinearGradient start={vec(0, g.rollerY - 16 * u)} end={vec(0, g.rollerY + 16 * u)} colors={['#F1EFEC', '#B9B5AF', '#8E8A84']} />
                </RoundedRect>
                <RoundedRect x={g.x1 + 2 * u} y={g.rollerY - 16 * u} width={13 * u} height={32 * u} r={3 * u}>
                  <LinearGradient start={vec(0, g.rollerY - 16 * u)} end={vec(0, g.rollerY + 16 * u)} colors={['#F1EFEC', '#B9B5AF', '#8E8A84']} />
                </RoundedRect>
                <Path path={cordPath} style="stroke" strokeWidth={1.6 * u} strokeCap="round" color="#8C7B66" />
                <Group>
                  <Circle cx={beadX} cy={beadY} r={9 * u}>
                    <RadialGradient c={beadLight} r={12 * u} colors={['#D8A877', '#9C6A41', '#5E3C22']} />
                  </Circle>
                  <Circle cx={beadX} cy={beadY} r={20 * u} style="stroke" strokeWidth={2} color="rgba(255,250,240,0.85)" opacity={fingerAlpha} />
                  <Circle cx={beadX} cy={beadY} r={20 * u} color="rgba(255,250,240,0.18)" opacity={fingerAlpha} />
                </Group>
              </Canvas>
              <Animated.View pointerEvents="none" style={[styles.clip, { top: g.rollerY + 14 * u, left: g.x0, right: g.w - g.x1 }, printClip]}>
                <View style={[styles.printed, { height: CONTENT_H, bottom: 12 * u }]}>
                  <Text style={styles.printKicker}>SCENES</Text>
                  <Text style={styles.printTitle}>Set the mood</Text>
                  {SCENES.map(({ title, detail, Icon }) => (
                    <View key={title} style={styles.scene}>
                      <View style={styles.sceneIcon}>
                        <Icon color={PRINT} size={20} />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.sceneTitle}>{title}</Text>
                        <Text style={styles.sceneDetail}>{detail}</Text>
                      </View>
                    </View>
                  ))}
                </View>
              </Animated.View>
            </View>
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#E6DED2', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 6 },
  meta: { flexDirection: 'row', alignItems: 'center', marginTop: 4, overflow: 'hidden' },
  metaText: { color: MUTE, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  controls: { position: 'absolute', left: 20, right: 20, gap: 12 },
  row: { flex: 1, flexDirection: 'row', gap: 12, maxHeight: 112 },
  control: {
    flex: 1,
    borderRadius: 20,
    borderCurve: 'continuous',
    padding: 14,
    justifyContent: 'flex-end',
    gap: 2,
    backgroundColor: 'rgba(252,249,244,0.86)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(43,38,34,0.1)',
    boxShadow: '0 1px 2px rgba(60,44,30,0.12), 0 10px 24px rgba(60,44,30,0.10), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  controlIcon: { position: 'absolute', top: 14, left: 14 },
  controlLabel: { color: MUTE, fontSize: 12, fontWeight: '600' },
  controlValue: { color: INK, fontSize: 16, fontWeight: '700' },
  dim: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: '#2A221C' },
  clip: { position: 'absolute', overflow: 'hidden' },
  printed: { position: 'absolute', left: 0, right: 0, paddingHorizontal: 28, justifyContent: 'flex-end' },
  printKicker: { color: PRINT_MUTE, fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  printTitle: { color: PRINT, fontSize: 24, fontWeight: '800', letterSpacing: -0.3, marginTop: 4, marginBottom: 10 },
  scene: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    height: 54,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(78,60,44,0.28)',
  },
  sceneIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.2,
    borderColor: 'rgba(78,60,44,0.4)',
  },
  sceneTitle: { color: PRINT, fontSize: 16, fontWeight: '700' },
  sceneDetail: { color: PRINT_MUTE, fontSize: 13, marginTop: 1 },
});
