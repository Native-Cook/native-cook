import {
  BlurMask,
  Canvas,
  Fill,
  Group,
  ImageShader,
  Path,
  Picture,
  RoundedRect,
  Shader,
  type SkImage,
  Skia,
  Vertices,
  matchFont,
  vec,
  LinearGradient,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo } from 'react';
import { PixelRatio, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PROFILE_N, buildMap } from './foldedMapArt';
import { BACKDROP_SKSL, recordPin } from './foldedMapExtras';
import {
  IDLE_BEFORE_RETURN,
  LOOP,
  PERSPECTIVE,
  RETURN_FOR,
  type Card,
  clamp,
  easeInOut,
  easeOut,
  foldMesh,
  meshIndices,
  pinDrop,
  script,
} from './foldedMapMotion';

/*
 Folded Map Card — "Ridge Line Trail" in a hiking app: the offline map is a paper map folded in
 thirds. It unfolds in 3D (each panel turning on its crease, shaded by its angle), then the route
 draws itself on, the summit pin drops and the elevation profile rises. The loop and the fold
 geometry are in foldedMapMotion.ts (LOOP = 8 s).

 Touch: drag sideways on the map to fold or unfold it continuously (two panel widths of drag =
 fully open, rubber-banded past both ends); released, panel 1 springs to the nearer state with
 the finger's velocity (ω 11, ζ 0.72, a slight overshoot) and panel 2 follows on a softer spring
 (ω 22, ζ 0.8), which staggers them. A tap folds or unfolds it. Once open, the route draws in 1 s,
 the pin drops at 0.8 s, the profile rises from 0.2 s; folding takes them away first. Light haptic
 when the map lands open or shut. 2 s after the last touch it refolds in 0.8 s and the loop
 restarts. Reduced motion: no loop; the map unfolds without overshoot; everything else fades.
*/

type Props = {
  /** Plays the 8 s demo: unfold, draw the route, drop the pin, refold. */
  autoPlay?: boolean;
};

const P3 = Platform.OS === 'ios' ? 1 : 0;
const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const PAD = 20;
const ACCENT = '#E0643F';
const INK = '#EEF1E8';
const INK_SOFT = 'rgba(226,234,222,0.62)';

function feel() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Mesh = SharedValue<ReturnType<typeof foldMesh>>;

/** One panel: the map image on its strips (drawn as is), then its shading laid over it. */
function MapPanel({ mesh, k, image, atlasW, atlasH, indices }: { mesh: Mesh; k: number; image: SkImage; atlasW: number; atlasH: number; indices: number[] }) {
  const verts = useDerivedValue(() => mesh.get().panels[k].v);
  const tex = useDerivedValue(() => mesh.get().panels[k].t);
  const outline = useDerivedValue(() => {
    const q = mesh.get().panels[k].quad;
    const b = Skia.PathBuilder.Make();
    b.moveTo(q[0], q[1]);
    b.lineTo(q[2], q[3]);
    b.lineTo(q[4], q[5]);
    b.lineTo(q[6], q[7]);
    b.close();
    return b.build();
  });
  const from = useDerivedValue(() => mesh.get().panels[k].from);
  const to = useDerivedValue(() => {
    const p = mesh.get().panels[k];
    // A panel seen edge-on has both ends together; keep the gradient defined.
    return Math.abs(p.to.x - p.from.x) < 0.5 ? { x: p.from.x + 0.5, y: p.from.y } : p.to;
  });
  const tints = useDerivedValue(() => mesh.get().panels[k].tints);
  return (
    <>
      <Vertices vertices={verts} textures={tex} indices={indices} mode="triangles">
        <ImageShader image={image} x={0} y={0} width={atlasW} height={atlasH} fit="fill" tx="clamp" ty="clamp" />
      </Vertices>
      <Path path={outline}>
        <LinearGradient start={from} end={to} colors={tints} positions={[0, 0.12, 0.88, 1]} />
      </Path>
    </>
  );
}

export function FoldedMapCard({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;

  const top = insets.top + 16;
  const MW = width - PAD * 2;
  const pw = MW / 3;
  const MH = Math.round(clamp(height * 0.36, 196, 300));
  const mapY = top + 132;
  const profY = mapY + MH + 28;
  // The profile card takes the room down to the button.
  const ctaTop = height - insets.bottom - 16 - 56;
  const PROFILE_H = Math.round(clamp(ctaTop - 20 - profY - 50, 56, 120));
  const card: Card = useMemo(() => ({ x: PAD, y: mapY, w: MW, h: MH, pw }), [mapY, MW, MH, pw]);

  const art = useMemo(() => {
    const fonts = {
      bold: matchFont({ fontFamily: FAMILY, fontSize: 9, fontWeight: '700' }),
      small: matchFont({ fontFamily: FAMILY, fontSize: 7.5, fontWeight: '500' }),
      italic: matchFont({ fontFamily: FAMILY, fontSize: 8.5, fontStyle: 'italic', fontWeight: '500' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: 17, fontWeight: '800' }),
    };
    return buildMap(MW, MH, pw, Math.min(3, PixelRatio.get()), fonts);
  }, [MW, MH, pw]);
  const indices = useMemo(() => meshIndices(), []);
  const pin = useMemo(() => recordPin(), []);
  const backdropFx = useMemo(() => Skia.RuntimeEffect.Make(BACKDROP_SKSL), []);
  const backdropUniforms = useMemo(() => ({ size: [width, height], p3: P3 }), [width, height]);

  const clock = useSharedValue(0);
  const mode = useSharedValue(0); // 0 loop, 1 dragging, 2 released, 3 returning
  const u1 = useSharedValue(0);
  const u2 = useSharedValue(0);
  const u1V = useSharedValue(0);
  const u2V = useSharedValue(0);
  const goal = useSharedValue(0);
  const route = useSharedValue(0);
  const pinP = useSharedValue(0);
  const rise = useSharedValue(0);
  const shown = useSharedValue(0); // seconds since the map lay open, while touched
  const idle = useSharedValue(0);
  const ret = useSharedValue(0);
  const fromU = useSharedValue(0);
  const startU = useSharedValue(0);

  useEffect(() => {
    clock.set(0);
    mode.set(0);
  }, [auto, clock, mode]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m === 0) {
      if (!auto) {
        u1.set(0);
        u2.set(0);
        route.set(0);
        pinP.set(0);
        rise.set(0);
        return;
      }
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const s = script(t);
      u1.set(s[0]);
      u2.set(s[1]);
      route.set(s[2]);
      pinP.set(s[3]);
      rise.set(s[4]);
      return;
    }
    const before = u1.get();
    if (m === 2) {
      const w = 11;
      const z = reduced ? 1 : 0.72;
      const a = -w * w * (u1.get() - goal.get()) - 2 * z * w * u1V.get();
      u1V.set(u1V.get() + a * dt);
      u1.set(u1.get() + u1V.get() * dt);
      if (Math.abs(u1.get() - goal.get()) < 0.004 && Math.abs(u1V.get()) < 0.02) idle.set(idle.get() + dt);
      if ((goal.get() === 1 && before < 0.99 && u1.get() >= 0.99) || (goal.get() === 0 && before > 0.01 && u1.get() <= 0.01)) scheduleOnRN(feel);
      if (idle.get() > IDLE_BEFORE_RETURN) {
        mode.set(3);
        ret.set(0);
        fromU.set(u1.get());
      }
    } else if (m === 3) {
      const r = Math.min(1, ret.get() + dt / RETURN_FOR);
      ret.set(r);
      u1.set(fromU.get() * (1 - easeInOut(r)));
      if (r >= 1) {
        mode.set(0);
        clock.set(0);
        u1.set(0);
        u2.set(0);
        shown.set(0);
        return;
      }
    }
    // Panel 2 trails panel 1.
    const w2 = 22;
    const a2 = -w2 * w2 * (u2.get() - u1.get()) - 2 * 0.8 * w2 * u2V.get();
    u2V.set(u2V.get() + a2 * dt);
    u2.set(u2.get() + u2V.get() * dt);
    if (m === 3) u2.set(u2.get() + (u1.get() - u2.get()) * ret.get());
    // Route, pin and profile while the map lies open; they leave four times faster.
    const open = u1.get() > 0.96 && u2.get() > 0.96 && m !== 3;
    shown.set(open ? shown.get() + dt : Math.max(0, shown.get() - dt * 4));
    const e = shown.get();
    route.set(easeInOut(e / 1.0));
    pinP.set(clamp((e - 0.8) / 0.4, 0, 1));
    rise.set(clamp((e - 0.2) / 0.8, 0, 1));
  });

  function takeOver() {
    'worklet';
    if (mode.get() === 0) {
      u1V.set(0);
      u2V.set(0);
      shown.set(route.get() > 0.99 ? 1.2 : route.get());
    }
    idle.set(0);
  }

  function rubber(v: number) {
    'worklet';
    if (v < 0) return (v / (1 - v * 4)) * 0.4;
    if (v > 1) return 1 + ((v - 1) / (1 + (v - 1) * 4)) * 0.4;
    return v;
  }

  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart(() => {
      takeOver();
      mode.set(1);
      startU.set(u1.get());
    })
    .onUpdate((e) => {
      u1.set(rubber(startU.get() + e.translationX / (2 * pw)));
      u1V.set(e.velocityX / (2 * pw));
    })
    .onEnd(() => {
      goal.set(u1.get() + u1V.get() * 0.25 > 0.5 ? 1 : 0);
    })
    .onFinalize(() => {
      mode.set(2);
      idle.set(0);
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((_e, ok) => {
      if (!ok) return;
      takeOver();
      goal.set(u1.get() > 0.5 ? 0 : 1);
      mode.set(2);
    });

  const mesh = useDerivedValue(() => foldMesh(card, MW, u1.get(), reduced ? u1.get() : u2.get()));

  // The map's shadow on the table: each panel's footprint, pushed out by its height.
  const shadowPath = useDerivedValue(() => {
    const f = mesh.get().frames;
    const b = Skia.PathBuilder.Make();
    for (let k = 0; k < 3; k += 1) {
      const ox = f[k * 3];
      const oz = f[k * 3 + 1];
      const psi = f[k * 3 + 2];
      const x1 = ox + pw * Math.cos(psi);
      const z1 = oz - pw * Math.sin(psi);
      const lift0 = -oz;
      const lift1 = -z1;
      const a = Math.min(ox, x1);
      const bb = Math.max(ox, x1);
      const lift = Math.max(lift0, lift1);
      b.addRect(Skia.XYWHRect(card.x + a + 3 + lift * 0.08, card.y + 6 + lift * 0.12, Math.max(2, bb - a), card.h));
    }
    return b.build();
  });
  const footprint = useDerivedValue(() => {
    const f = mesh.get().frames;
    let maxX = pw;
    for (let k = 0; k < 3; k += 1) maxX = Math.max(maxX, f[k * 3] + pw * Math.cos(f[k * 3 + 2]));
    return Skia.RRectXY(Skia.XYWHRect(card.x, card.y + 1.5, maxX, card.h), 1, 1);
  });

  // The route through the folds, drawn up to its progress.
  const routePath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const pts = art.route;
    const n = pts.length / 2;
    const upto = Math.floor(route.get() * (n - 1));
    if (route.get() <= 0.001) return b.build();
    const f = mesh.get().frames;
    for (let i = 0; i <= upto; i += 1) {
      const mx = pts[i * 2];
      const my = pts[i * 2 + 1];
      const k = clamp(Math.floor(mx / pw), 0, 2);
      const lx = mx - k * pw;
      const X = f[k * 3] + lx * Math.cos(f[k * 3 + 2]);
      const Z = f[k * 3 + 1] - lx * Math.sin(f[k * 3 + 2]);
      const s = PERSPECTIVE / (PERSPECTIVE + Z);
      const x = card.x + card.w / 2 + (X - card.w / 2) * s;
      const y = card.y + card.h / 2 + (my - card.h / 2) * s;
      if (i === 0) b.moveTo(x, y);
      else b.lineTo(x, y);
    }
    return b.build();
  });
  const routeOpacity = useDerivedValue(() => clamp(route.get() * 6, 0, 1));
  const summit = useMemo(() => [art.route[art.route.length - 2], art.route[art.route.length - 1]], [art.route]);
  const pinT = useDerivedValue(() => {
    const d = pinDrop(pinP.get());
    return [{ translateX: card.x + summit[0] }, { translateY: card.y + summit[1] - Math.max(0, d) }, { scale: d < 0 ? 0.6 : 1 }];
  });
  const pinShadowOpacity = useDerivedValue(() => (pinP.get() > 0 ? 1 - Math.max(0, pinDrop(pinP.get())) / 30 : 0));
  const pinOpacity = useDerivedValue(() => (reduced ? pinP.get() : clamp(pinP.get() * 5, 0, 1)));

  // Elevation profile: bars of the route's height rising left to right, 40 ms apart.
  const profileW = MW - 32;
  const profilePath = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const r = rise.get();
    if (r <= 0) return b.build();
    const bw = profileW / PROFILE_N;
    for (let i = 0; i < PROFILE_N; i += 1) {
      const k = reduced ? 1 : easeOut(clamp((r * 1.6 - (i / PROFILE_N) * 0.6) / 1, 0, 1));
      const h = (8 + art.profile[i] * (PROFILE_H - 8)) * k;
      if (h < 0.5) continue;
      b.addRRect(Skia.RRectXY(Skia.XYWHRect(PAD + 16 + i * bw + 1, profY + 30 + PROFILE_H - h, bw - 2.2, h), 1.5, 1.5));
    }
    return b.build();
  });
  const profileOpacity = useDerivedValue(() => (reduced ? rise.get() : 1));

  const infoStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(u1.get() * 2.4, 0, 1) }));
  const profileLabels = useAnimatedStyle(() => ({ opacity: clamp(rise.get() * 3, 0, 1) }));

  return (
    <View style={styles.root}>
      <Canvas style={StyleSheet.absoluteFill}>
        {backdropFx ? (
          <Fill>
            <Shader source={backdropFx} uniforms={backdropUniforms} />
          </Fill>
        ) : null}
        {/* Profile card. */}
        <RoundedRect x={PAD} y={profY} width={MW} height={PROFILE_H + 50} r={20} color="rgba(232,240,226,0.05)" />
        <Path path={profilePath} opacity={profileOpacity}>
          <LinearGradient start={vec(0, profY + 30)} end={vec(0, profY + 30 + PROFILE_H)} colors={['#F08A5D', ACCENT, '#9C3B26']} positions={[0, 0.5, 1]} />
        </Path>
        {/* Shadows of the paper, then the paper. */}
        <Path path={shadowPath} color="rgba(2,10,6,0.5)">
          <BlurMask blur={12} style="normal" />
        </Path>
        <RoundedRect rect={footprint} color="rgba(2,10,6,0.45)">
          <BlurMask blur={2.5} style="normal" />
        </RoundedRect>
        {[0, 1, 2].map((k) => (
          <MapPanel key={k} mesh={mesh} k={k} image={art.image} atlasW={MW + pw} atlasH={MH} indices={indices} />
        ))}
        <Group opacity={routeOpacity}>
          <Path path={routePath} style="stroke" strokeWidth={4.5} strokeCap="round" strokeJoin="round" color="rgba(250,244,228,0.9)" />
          <Path path={routePath} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color={ACCENT} />
        </Group>
        <Group opacity={pinOpacity}>
          <Group transform={[{ translateX: card.x + summit[0] }, { translateY: card.y + summit[1] }]}>
            <RoundedRect x={-6} y={-2} width={12} height={4} r={2} color="rgba(40,20,10,0.35)" opacity={pinShadowOpacity}>
              <BlurMask blur={1.5} style="normal" />
            </RoundedRect>
          </Group>
          <Group transform={pinT}>
            <Picture picture={pin} />
          </Group>
        </Group>
      </Canvas>

      <View pointerEvents="none" style={[styles.header, { top }]}>
        <Text style={styles.kicker}>SAVED OFFLINE · ALDER VALLEY</Text>
        <Text style={styles.title}>Ridge Line Trail</Text>
        <View style={styles.stats}>
          {[
            ['14.2 km', 'Distance'],
            ['820 m', 'Ascent'],
            ['5 h 10 min', 'Moving time'],
          ].map(([v, l]) => (
            <View key={l} style={styles.stat}>
              <Text style={styles.statValue}>{v}</Text>
              <Text style={styles.statLabel}>{l}</Text>
            </View>
          ))}
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.info, { top: mapY + 12, left: PAD + pw + 22 }, infoStyle]}>
        <Text style={styles.infoKicker}>PAPER MAP</Text>
        <Text style={styles.infoTitle}>Sheet 7 · 1:25 000</Text>
        <Text style={styles.infoBody}>Waterproof print, contours every 20 m, huts and water sources marked.</Text>
        <Text style={styles.infoHint}>Drag to unfold  ›</Text>
      </Animated.View>

      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View
          style={[styles.touch, { top: mapY, height: MH, left: PAD, width: MW }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Folded paper map of the trail. Tap or drag sideways to unfold it."
        />
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.profileHead, { top: profY + 12 }]}>
        <Text style={styles.profileTitle}>Elevation</Text>
        <Animated.Text style={[styles.profileRange, profileLabels]}>1 394 m → 2 214 m</Animated.Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.profileFoot, { top: profY + 30 + PROFILE_H + 4 }, profileLabels]}>
        <Text style={styles.profileTick}>0 km</Text>
        <Text style={styles.profileTick}>Larch Hut · 6.1 km</Text>
        <Text style={styles.profileTick}>14.2 km</Text>
      </Animated.View>

      <View pointerEvents="none" style={[styles.cta, { bottom: insets.bottom + 16 }]}>
        <Text style={styles.ctaText}>Start route</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#13221A' },
  header: { position: 'absolute', left: PAD, right: PAD, gap: 4 },
  kicker: { color: '#A9C79A', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  title: { color: INK, fontSize: 32, lineHeight: 38, fontWeight: '700', letterSpacing: -0.5 },
  stats: { flexDirection: 'row', gap: 24, marginTop: 10 },
  stat: { gap: 1 },
  statValue: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  statLabel: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  info: { position: 'absolute', right: PAD, gap: 4 },
  infoKicker: { color: '#A9C79A', fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  infoTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  infoBody: { color: INK_SOFT, fontSize: 13, lineHeight: 18 },
  infoHint: { color: ACCENT, fontSize: 13, fontWeight: '700', marginTop: 8 },
  touch: { position: 'absolute' },
  profileHead: { position: 'absolute', left: PAD + 16, right: PAD + 16, flexDirection: 'row', justifyContent: 'space-between' },
  profileTitle: { color: INK, fontSize: 13, fontWeight: '600' },
  profileFoot: { position: 'absolute', left: PAD + 16, right: PAD + 16, flexDirection: 'row', justifyContent: 'space-between' },
  profileTick: { color: INK_SOFT, fontSize: 11, fontWeight: '500', fontVariant: ['tabular-nums'] },
  profileRange: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  cta: {
    position: 'absolute',
    left: PAD,
    right: PAD,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: ACCENT,
    boxShadow: '0 1px 2px rgba(60,20,8,0.4), 0 12px 28px rgba(224,100,63,0.28), inset 0 1px 0 rgba(255,220,200,0.45)',
  },
  ctaText: { color: '#FFF4EC', fontSize: 17, fontWeight: '700' },
});
