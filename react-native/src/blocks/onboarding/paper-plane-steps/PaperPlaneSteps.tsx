import { BlurMask, Canvas, DashPathEffect, Fill, Group, ImageShader, LinearGradient, Path, Shader, Skia, Vertices, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { PixelRatio, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { buildPaper } from './planeArt';
import { type Face, makePlan } from './planeGeometry';
import { IDLE_BEFORE_RETURN, LOOP, STEPS, type Out, type Xf, clamp, easeOutBack, flight, paperMesh, screenOf, script, seg, tintOf } from './planeMotion';
import { BACKDROP_SKSL } from './planeShader';

/*
 Paper Plane Steps — onboarding for "Postcard", an app for slow letters to faraway friends. Each
 step is a sheet of writing paper; Next folds it into a paper plane in four visible folds and
 throws it, and the next sheet slides up. The loop is in planeMotion.ts (LOOP = 10 s), the folds
 in planeGeometry.ts.

 Touch: drag up on the sheet to fold it (60 pt of drag per fold, the flaps following the finger).
 Let go past the second fold, or flick up, and it finishes folding (3–8 folds a second with the
 flick) and flies (0.6–1.2 s, faster for a harder flick); short of it, it unfolds again. Next
 plays the same at 0.32 s a fold. A selection tick per finished fold and a light impact on the
 throw. 2 s after the last touch the first sheet slides back up (0.8 s) and the loop restarts.
 Reduced motion: no loop; Next cross-fades to the next sheet (the plane is not thrown).
*/

type Props = {
  /** Plays the 10 s demo: fold and throw sheet 1, sheet 2 arrives, fold and throw it, back to 1. */
  autoPlay?: boolean;
};

const P3 = Platform.OS === 'ios' ? 1 : 0;
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const INK = '#F4EFE6';
const INK_SOFT = 'rgba(236,230,220,0.66)';
const ACCENT = '#E8835A';
// A degenerate triangle stands in for an empty mesh (Vertices needs at least one).
const NONE = [{ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 0 }];
const EMPTY: Out = { sv: [], st: [], mv: [], mt: [], mpoly: [], all: [], flap: 1, sh: [] };

function orNone(a: { x: number; y: number }[]) {
  'worklet';
  return a.length >= 3 ? a : NONE;
}

/** Outlines (x, y pairs with NaN between them) as one path, moved by (dx, dy). */
function polyPath(pts: number[], dx: number, dy: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  let first = true;
  for (let i = 0; i < pts.length; i += 2) {
    const x = pts[i];
    if (Number.isNaN(x)) {
      if (!first) b.close();
      first = true;
      continue;
    }
    if (first) b.moveTo(x + dx, pts[i + 1] + dy);
    else b.lineTo(x + dx, pts[i + 1] + dy);
    first = false;
  }
  return b.build();
}

function feel(kind: number) {
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function PaperPlaneSteps({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [step, setStep] = useState(0);

  const top = insets.top + 16;
  const sheetY = top + 116;
  // The sheet is as large as the screen allows between the title and the dots above Next.
  const ctaTop = height - insets.bottom - 16 - 56;
  const SW = Math.round(Math.min(width - 48, 340, (ctaTop - 44 - sheetY) / 1.32));
  const SH = Math.round(SW * 1.32);
  const cx = width / 2;
  const cy = sheetY + SH / 2;
  const dotsY = sheetY + SH + 22;

  const plan = useMemo(() => makePlan(SW, SH), [SW, SH]);
  const flat: Face = useMemo(() => ({ pts: [0, 0, SW, 0, SW, SH, 0, SH], uv: [0, 0, SW, 0, SW, SH, 0, SH], back: 0, layer: 0 }), [SW, SH]);
  const paper = useMemo(
    () =>
      buildPaper(SW, SH, Math.min(2, PixelRatio.get()), {
        kicker: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
        title: matchFont({ fontFamily: SERIF, fontSize: 29, fontWeight: '700' }),
        body: matchFont({ fontFamily: SERIF, fontSize: 15.5, fontWeight: '400' }),
        mark: matchFont({ fontFamily: SANS, fontSize: 6.5, fontWeight: '700' }),
      }),
    [SW, SH],
  );
  const backdropFx = useMemo(() => Skia.RuntimeEffect.Make(BACKDROP_SKSL), []);
  const backdropUniforms = useMemo(() => ({ size: [width, height], p3: P3 }), [width, height]);

  // What is drawn (from the loop, or from the controller under touch).
  const sheet = useSharedValue(0);
  const fold = useSharedValue(0);
  const tuck = useSharedValue(0);
  const fly = useSharedValue(-1);
  const inc = useSharedValue(1);
  const incP = useSharedValue(-1);
  const dot = useSharedValue(0);
  // Loop and controller.
  const clock = useSharedValue(0);
  const mode = useSharedValue(0); // 0 loop, 1 dragging, 2 controller
  const run = useSharedValue(0); // 0 idle, 1 folding, 2 flying, 4 unfolding, 5 returning
  const rate = useSharedValue(3);
  const flyDur = useSharedValue(1);
  const idle = useSharedValue(0);
  const startFold = useSharedValue(0);
  const grab = useSharedValue(false);

  useEffect(() => {
    clock.set(0);
    mode.set(0);
  }, [auto, clock, mode]);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m === 0) {
      const s = auto ? script((clock.get() + dt) % LOOP) : script(0);
      if (auto) clock.set((clock.get() + dt) % LOOP);
      sheet.set(s[0]);
      fold.set(s[1]);
      tuck.set(s[2]);
      fly.set(s[3]);
      inc.set(s[4]);
      incP.set(s[5]);
      dot.set(s[6]);
      return;
    }
    const r = run.get();
    if (r === 1) {
      const before = fold.get();
      const next = Math.min(4, before + dt * rate.get());
      fold.set(next);
      if (Math.floor(next) > Math.floor(before)) scheduleOnRN(feel, 0);
      if (next >= 4) {
        run.set(2);
        fly.set(-1);
      }
    } else if (r === 2) {
      tuck.set(Math.min(1, tuck.get() + dt / 0.2));
      if (tuck.get() > 0.6 || fly.get() >= 0) {
        if (fly.get() < 0) scheduleOnRN(feel, 1);
        fly.set(Math.min(1, Math.max(0, fly.get()) + dt / flyDur.get()));
      }
      if (fly.get() > 0.85 && incP.get() < 0) {
        inc.set((sheet.get() + 1) % STEPS);
        incP.set(0);
      }
    } else if (r === 4) {
      fold.set(Math.max(0, fold.get() - dt * 5));
      if (fold.get() <= 0) run.set(0);
    }
    if (incP.get() >= 0) {
      incP.set(Math.min(1, incP.get() + dt / 0.8));
      if (incP.get() >= 1) {
        const back = run.get() === 5;
        sheet.set(inc.get());
        fold.set(0);
        tuck.set(0);
        fly.set(-1);
        incP.set(-1);
        run.set(0);
        idle.set(0);
        scheduleOnRN(setStep, inc.get());
        if (back) {
          mode.set(0);
          clock.set(0);
          return;
        }
      }
    }
    const target = incP.get() > 0.1 ? inc.get() : sheet.get();
    dot.set(dot.get() + (target - dot.get()) * Math.min(1, dt * 10));
    if (m === 2 && run.get() === 0) {
      idle.set(idle.get() + dt);
      if (idle.get() > IDLE_BEFORE_RETURN) {
        if (sheet.get() === 0 && fold.get() === 0) {
          mode.set(0);
          clock.set(0);
        } else {
          run.set(5);
          inc.set(0);
          incP.set(0);
        }
      }
    }
  });

  /** Hands the loop's frame to the controller, mid-fold or mid-flight as it may be. */
  function takeOver() {
    'worklet';
    idle.set(0);
    if (mode.get() !== 0) return;
    const t = clock.get();
    const s = auto ? script(t) : script(0);
    const second = t >= 5.2 && t < 9.4;
    rate.set(second ? 1 / 0.3 : 1 / 0.45);
    flyDur.set(second ? 1.0 : 1.2);
    if (s[5] >= 0 || s[3] >= 0) run.set(2);
    else if (s[1] > 0) run.set(1);
    else run.set(0);
    mode.set(2);
  }

  const next = () => {
    if (mode.get() === 0) {
      // A JS-thread copy of takeOver (it touches only shared values).
      const t = clock.get();
      const s = auto ? script(t) : script(0);
      if (s[1] > 0 || s[3] >= 0 || s[5] >= 0) return;
      mode.set(2);
    }
    idle.set(0);
    if (run.get() !== 0) return;
    if (reduced) {
      inc.set((sheet.get() + 1) % STEPS);
      incP.set(0);
      return;
    }
    rate.set(1 / 0.32);
    flyDur.set(1.0);
    run.set(1);
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-10, 10])
    .onStart(() => {
      takeOver();
      grab.set(run.get() === 0 && !reduced);
      if (!grab.get()) return;
      mode.set(1);
      startFold.set(fold.get());
    })
    .onUpdate((e) => {
      if (!grab.get()) return;
      const before = fold.get();
      const f = clamp(startFold.get() - e.translationY / 60, 0, 3.999);
      fold.set(f);
      if (Math.floor(f) > Math.floor(before)) scheduleOnRN(feel, 0);
    })
    .onEnd((e) => {
      if (!grab.get()) return;
      if (fold.get() >= 2 || e.velocityY < -900) {
        rate.set(clamp(-e.velocityY / 300, 3, 8));
        flyDur.set(clamp(900 / Math.max(500, -e.velocityY), 0.6, 1.2));
        run.set(1);
      } else run.set(4);
    })
    .onFinalize(() => {
      if (!grab.get()) return;
      grab.set(false);
      mode.set(2);
      idle.set(0);
    });

  // The paper (or the plane) this frame.
  const xfNow = useDerivedValue<Xf>(() => {
    const f = fly.get();
    if (f >= 0) {
      const p = flight(f, cx, cy, width);
      return { ox: p[0], oy: p[1], cx: SW / 2, cy: SH / 2, sx: 0.62 * Math.cos(p[4]), sy: 1, rot: p[2], scale: p[3], bank: p[4] };
    }
    return { ox: cx, oy: cy, cx: SW / 2, cy: SH / 2, sx: 1 - 0.38 * tuck.get(), sy: 1, rot: 0, scale: 1 - 0.14 * tuck.get(), bank: 0 };
  });
  const mesh = useDerivedValue(() => (fly.get() >= 1 ? EMPTY : paperMesh(plan, fold.get(), sheet.get(), xfNow.get())));
  const incoming = useDerivedValue(() => {
    const p = incP.get();
    if (p < 0) return EMPTY;
    const e = reduced ? 1 : easeOutBack(p);
    const xf: Xf = { ox: cx, oy: cy + (1 - e) * (height - sheetY + 40), cx: SW / 2, cy: SH / 2, sx: 1, sy: 1, rot: (1 - e) * -0.07, scale: 1, bank: 0 };
    return paperMesh({ sw: SW, sh: SH, folds: [], final: [flat], maxLayer: 1 }, 4, inc.get(), xf);
  });
  const opacityIn = useDerivedValue(() => (reduced ? clamp(incP.get() * 2, 0, 1) : 1));
  const opacityPaper = useDerivedValue(() => (reduced && incP.get() >= 0 ? 1 - clamp(incP.get() * 2, 0, 1) : 1));

  const paperShadow = useDerivedValue(() => {
    const f = fly.get();
    const alt = f >= 0 ? Math.sin(Math.PI * Math.min(1, f * 1.4)) * 50 : 0;
    return polyPath(mesh.get().sh, 4 + alt * 0.4, 10 + alt);
  });
  const paperShadowOpacity = useDerivedValue(() => (fly.get() >= 0 ? 0.5 * (1 - seg(fly.get(), 0.2, 0.6)) : 0.5));
  const incomingShadow = useDerivedValue(() => polyPath(incoming.get().sh, 4, 10));

  const trail = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    const f = fly.get();
    if (f <= 0.02) return b.build();
    for (let i = 0; i <= 28; i += 1) {
      const p = flight((f * i) / 28, cx, cy, width);
      if (i === 0) b.moveTo(p[0], p[1]);
      else b.lineTo(p[0], p[1]);
    }
    return b.build();
  });
  const trailOpacity = useDerivedValue(() => (fly.get() < 0 ? 0 : 0.6 * (1 - seg(fly.get(), 0.7, 1)) * seg(fly.get(), 0.02, 0.12)));

  const verts = useDerivedValue(() => orNone(mesh.get().sv));
  const texs = useDerivedValue(() => orNone(mesh.get().st));
  const flapVerts = useDerivedValue(() => orNone(mesh.get().mv));
  const flapTexs = useDerivedValue(() => orNone(mesh.get().mt));
  const inVerts = useDerivedValue(() => orNone(incoming.get().sv));
  const inTexs = useDerivedValue(() => orNone(incoming.get().st));
  // Shading laid over the image: the turning flap by its angle, the flying plane's wings by its bank.
  const flapShade = useDerivedValue(() => polyPath(mesh.get().mpoly, 0, 0));
  const edges = useDerivedValue(() => polyPath(mesh.get().all, 0, 0));
  const flapTint = useDerivedValue(() => tintOf(mesh.get().flap));
  const planeShade = useDerivedValue(() => (xfNow.get().bank > 0.01 ? polyPath(mesh.get().all, 0, 0) : Skia.PathBuilder.Make().build()));
  const wingFrom = useDerivedValue(() => {
    const p = screenOf(xfNow.get(), 0, SH * 0.5, 0);
    return { x: p[0], y: p[1] };
  });
  const wingTo = useDerivedValue(() => {
    const p = screenOf(xfNow.get(), SW, SH * 0.5, 0);
    const f = wingFrom.get();
    return Math.hypot(p[0] - f.x, p[1] - f.y) < 0.5 ? { x: f.x + 0.5, y: f.y } : { x: p[0], y: p[1] };
  });
  const wingTints = useDerivedValue(() => {
    const b = xfNow.get().bank;
    return [`rgba(255,250,236,${(0.22 * b).toFixed(3)})`, 'rgba(255,250,236,0)', `rgba(22,20,38,${(0.34 * b).toFixed(3)})`];
  });

  const pillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: dot.get() * 16 }] }));

  const shader = <ImageShader image={paper} x={0} y={0} width={SW * 4} height={SH} fit="fill" tx="clamp" ty="clamp" />;

  return (
    <View style={styles.root}>
      <Canvas style={StyleSheet.absoluteFill}>
        {backdropFx ? (
          <Fill>
            <Shader source={backdropFx} uniforms={backdropUniforms} />
          </Fill>
        ) : null}
        <Path path={incomingShadow} color="rgba(6,10,24,0.5)" opacity={opacityIn}>
          <BlurMask blur={14} style="normal" />
        </Path>
        <Vertices vertices={inVerts} textures={inTexs} mode="triangles" opacity={opacityIn}>
          {shader}
        </Vertices>
        <Path path={trail} style="stroke" strokeWidth={1.6} strokeCap="round" color="rgba(244,239,230,1)" opacity={trailOpacity}>
          <DashPathEffect intervals={[5, 8]} />
        </Path>
        <Path path={paperShadow} color="rgba(6,10,24,1)" opacity={paperShadowOpacity}>
          <BlurMask blur={14} style="normal" />
        </Path>
        <Group opacity={opacityPaper}>
          <Vertices vertices={verts} textures={texs} mode="triangles">
            {shader}
          </Vertices>
          <Vertices vertices={flapVerts} textures={flapTexs} mode="triangles">
            {shader}
          </Vertices>
          <Path path={flapShade} color={flapTint} />
          {/* The folded edges, faint, so the stacked layers read. */}
          <Path path={edges} style="stroke" strokeWidth={0.8} strokeJoin="round" color="rgba(90,70,48,0.28)" />
          <Path path={planeShade}>
            <LinearGradient start={wingFrom} end={wingTo} colors={wingTints} positions={[0, 0.5, 1]} />
          </Path>
        </Group>
      </Canvas>

      <View pointerEvents="box-none" style={[styles.header, { top }]}>
        <View style={styles.brandRow}>
          <View style={styles.brand}>
            <View style={styles.brandMark} />
            <Text style={styles.brandText}>Postcard</Text>
          </View>
          <Text style={styles.skip}>Skip</Text>
        </View>
        <Text style={styles.title}>Send notes to faraway friends</Text>
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.touch, { top: sheetY, height: SH, left: cx - SW / 2, width: SW }]}
          accessible
          accessibilityLabel="Onboarding letter. Swipe up to fold it into a paper plane and send it."
        />
      </GestureDetector>

      <View pointerEvents="none" style={[styles.dots, { top: dotsY, left: cx - 23 }]}>
        {[0, 1, 2].map((i) => (
          <View key={i} style={[styles.dot, { left: i * 16 }]} />
        ))}
        <Animated.View style={[styles.pill, pillStyle]} />
      </View>

      <Pressable onPress={next} style={({ pressed }) => [styles.cta, { bottom: insets.bottom + 16, transform: [{ scale: pressed ? 0.97 : 1 }] }]}>
        <Text style={styles.ctaText}>{step === STEPS - 1 ? 'Start writing' : 'Next'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#1B2236' },
  header: { position: 'absolute', left: 24, right: 24, gap: 12 },
  brandRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 24 },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  brandMark: { width: 16, height: 12, borderRadius: 2, backgroundColor: ACCENT, transform: [{ rotate: '-8deg' }] },
  brandText: { color: INK, fontSize: 15, fontWeight: '700', letterSpacing: 0.2 },
  skip: { color: INK_SOFT, fontSize: 15, fontWeight: '500' },
  title: { color: INK, fontSize: 26, lineHeight: 32, fontWeight: '700', letterSpacing: -0.4 },
  touch: { position: 'absolute' },
  dots: { position: 'absolute', width: 46, height: 8 },
  dot: { position: 'absolute', top: 0, width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(244,239,230,0.25)' },
  pill: { position: 'absolute', top: 0, left: -7, width: 22, height: 8, borderRadius: 4, backgroundColor: ACCENT },
  cta: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: ACCENT,
    boxShadow: '0 1px 2px rgba(40,16,6,0.4), 0 12px 28px rgba(232,131,90,0.28), inset 0 1px 0 rgba(255,226,206,0.5)',
  },
  ctaText: { color: '#2A140B', fontSize: 17, fontWeight: '700' },
});
