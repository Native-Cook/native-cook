import {
  Canvas,
  Circle,
  ColorMatrix,
  Fill,
  Group,
  Image as SkiaImage,
  LinearGradient as SkiaGradient,
  Mask,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { AURORA } from './auroraShader';

type Props = {
  /** Tilts the forecast card left and right by itself while the aurora flows. */
  autoPlay?: boolean;
  onStart?: () => void;
  onSignIn?: () => void;
};

// The horizon: mountains over a black beach, keyed out of their bright sky so the aurora shows
// through, and reflected in the wet sand.
const HORIZON = 'https://images.unsplash.com/photo-1511300636408-a63a89df3482?auto=format&fit=crop&w=1200&h=720&q=80';
const HORIZON_WIDTH = 1.5; // times the screen's width
const HORIZON_ASPECT = 1200 / 720;
const HORIZON_TOP = 0.31; // share of the height where the photo's top sits, so the peaks rise behind the card
const HORIZON_RISE = 26; // pt the horizon rises into place on entrance
const GROUND_FADE = 110; // pt over which the photo's foot melts into the night ground

const NIGHT = '#040815';
const INK = '#f3f6ff';
const GREEN = '#6dffbf';
const SOFT = 'rgba(243,246,255,0.62)';
const FAINT = 'rgba(243,246,255,0.45)';

// Tonight's activity, hourly from 18:00 to 04:00; the marker sits on the peak.
const ACTIVITY = [1.6, 2.2, 3.0, 4.4, 6.3, 5.6, 4.7, 5.1, 3.8, 2.7, 2.0];
const NOW_INDEX = 4;
const HOURS = ['18:00', '21:00', '00:00', '03:00'];
const CHART_HEIGHT = 92;
const STATS = [
  { value: '12%', label: 'Cloud cover' },
  { value: '18%', label: 'Moonlight' },
  { value: '05:40', label: 'Dark until' },
];

// The self-playing loop: the card tilts left, then right, LOOP s round, while the aurora's flow
// comes back to where it started on the same period.
const LOOP = 10;
const AUTO_YAW = 9; // deg
const AUTO_PITCH = 4; // deg
const AUTO_RESUME = 3000; // ms after a touch
const AUTO_FADE = 900; // ms for the autoPlay tilt to come back in
const PULSES = 5; // the "now" marker pulses this many times a loop

// Dragging: the card tilts TILT_RATE deg per pt, freely up to MAX_TILT, then rubber-bands; let go,
// it springs flat carrying the finger's speed. The aurora bends towards the finger meanwhile.
const TILT_RATE = 0.09;
const MAX_TILT = 16;
const RUBBER = 0.5;
const RELEASE = { duration: 700, dampingRatio: 0.55 } as const;
const RELEASE_REDUCED = { duration: 300, dampingRatio: 1 } as const;
const PERSPECTIVE = 900;

// Entrance, ms from mount: the sky lights up, the horizon rises, the headline climbs out of its
// lines, the card lifts in and its curve draws on, then the buttons.
const SKY_IN = 1600;
const BAR_AT = 150;
const LINE_AT = [260, 360];
const LINE_IN = 760;
const SUB_AT = 520;
const CARD_AT = 600;
const CARD_IN = 850;
const CARD_RISE = 40;
const CURVE_AT = 950;
const CURVE_IN = 1150;
const STATS_AT = 1150;
const STAT_STAGGER = 80;
const CTA_AT = 1000;
const ITEM_IN = 600;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

/** A key that turns the photo into a night silhouette: dark ground stays, bright sky goes clear. */
const SILHOUETTE = [
  0.03, 0.06, 0.01, 0, 0.004,
  0.03, 0.06, 0.01, 0, 0.012,
  0.04, 0.08, 0.02, 0, 0.035,
  -0.53, -1.79, -0.18, 0, 1.6,
];

function rubber(value: number) {
  'worklet';
  const over = Math.abs(value) - MAX_TILT;
  if (over <= 0) return value;
  return Math.sign(value) * (MAX_TILT + (RUBBER * MAX_TILT * over) / (MAX_TILT + RUBBER * over));
}

/** A smooth curve through the activity points (Catmull-Rom as cubic Béziers), and its area. */
function activityPaths(width: number, height: number) {
  const top = 10;
  const max = 7;
  const pts = ACTIVITY.map((v, i) => ({
    x: (i / (ACTIVITY.length - 1)) * width,
    y: top + (1 - v / max) * (height - top),
  }));
  const line = Skia.PathBuilder.Make();
  line.moveTo(pts[0].x, pts[0].y);
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[Math.min(pts.length - 1, i + 2)];
    line.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y);
  }
  const stroke = line.build();
  line.lineTo(width, height).lineTo(0, height).close();
  return { stroke, area: line.detach(), now: pts[NOW_INDEX] };
}

/** Fades and rises a piece of the screen in, `at` ms after mount. */
function Enter({ at, rise = 12, reduced, style, children }: { at: number; rise?: number; reduced: boolean; style?: object; children: ReactNode }) {
  const shown = useSharedValue(0);
  useEffect(() => {
    shown.set(withDelay(at, withTiming(1, { duration: ITEM_IN, easing: EASE_OUT })));
  }, [at, shown]);
  const animated = useAnimatedStyle(() => ({
    opacity: shown.get(),
    transform: [{ translateY: reduced ? 0 : (1 - shown.get()) * rise }],
  }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/** One line of the headline, climbing out of a slot at its own baseline. */
function HeadlineLine({ text, at, reduced }: { text: string; at: number; reduced: boolean }) {
  const shown = useSharedValue(0);
  useEffect(() => {
    shown.set(withDelay(at, withTiming(1, { duration: LINE_IN, easing: EASE_OUT })));
  }, [at, shown]);
  const animated = useAnimatedStyle(() => ({
    opacity: reduced ? shown.get() : Math.min(1, shown.get() * 2),
    transform: [{ translateY: reduced ? 0 : (1 - shown.get()) * 50 }],
  }));
  return (
    <View style={styles.lineSlot}>
      <Animated.Text style={[styles.headline, animated]}>{text}</Animated.Text>
    </View>
  );
}

/** The white pill, dipping to 0.97 under the finger. */
function StartButton({ onPress }: { onPress?: () => void }) {
  const down = useSharedValue(1);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: down.get() }] }));
  return (
    <Pressable
      accessibilityRole="button"
      onPressIn={() => down.set(withTiming(0.97, { duration: 110, easing: EASE_OUT }))}
      onPressOut={() => down.set(withTiming(1, { duration: 160, easing: EASE_OUT }))}
      onPress={() => {
        if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
        onPress?.();
      }}
    >
      <Animated.View style={[styles.button, style]}>
        <Text style={styles.buttonLabel}>Get started</Text>
      </Animated.View>
    </Pressable>
  );
}

/** The activity curve: it draws on from the left, its area fills in under it, the marker pops on the peak. */
function ActivityChart({ width, clock, reduced }: { width: number; clock: SharedValue<number>; reduced: boolean }) {
  const paths = useMemo(() => activityPaths(Math.max(1, width), CHART_HEIGHT), [width]);
  const drawn = useSharedValue(reduced ? 1 : 0);
  const marker = useSharedValue(0);
  useEffect(() => {
    if (reduced) {
      drawn.set(1);
      marker.set(withDelay(CURVE_AT, withTiming(1, { duration: 400 })));
      return;
    }
    drawn.set(withDelay(CURVE_AT, withTiming(1, { duration: CURVE_IN, easing: EASE_IN_OUT })));
    marker.set(withDelay(CURVE_AT + CURVE_IN * 0.55, withSpring(1, { duration: 500, dampingRatio: 0.5 })));
  }, [drawn, marker, reduced]);

  const end = useDerivedValue(() => Math.max(0.001, drawn.get()));
  const lineOpacity = useDerivedValue(() => (reduced ? marker.get() : drawn.get() > 0.001 ? 1 : 0));
  // The area is uncovered left to right with the line.
  const areaClip = useDerivedValue(() => ({ x: 0, y: 0, width: Math.max(0, width * drawn.get()), height: CHART_HEIGHT }));
  const dotR = useDerivedValue(() => 4.5 * Math.max(0, marker.get()));
  const pulse = useDerivedValue(() => ((clock.get() / LOOP) * PULSES) % 1);
  const ringR = useDerivedValue(() => 4.5 + 13 * pulse.get());
  const ringOpacity = useDerivedValue(() => (reduced ? 0 : 0.55 * (1 - pulse.get()) * Math.min(1, marker.get())));

  return (
    <Canvas style={{ width, height: CHART_HEIGHT }}>
      {/* Faint guides. */}
      {[0.33, 0.66].map((f) => (
        <Rect key={f} x={0} y={10 + f * (CHART_HEIGHT - 10)} width={width} height={StyleSheet.hairlineWidth} color="rgba(243,246,255,0.1)" />
      ))}
      <Rect x={paths.now.x - 0.5} y={0} width={1} height={CHART_HEIGHT} color="rgba(243,246,255,0.22)" opacity={marker} />
      <Group clip={areaClip}>
        <Path path={paths.area}>
          <SkiaGradient start={vec(0, 10)} end={vec(0, CHART_HEIGHT)} colors={['rgba(109,255,191,0.32)', 'rgba(109,255,191,0)']} />
        </Path>
      </Group>
      <Path path={paths.stroke} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" start={0} end={end} opacity={lineOpacity}>
        <SkiaGradient start={vec(0, 0)} end={vec(width, 0)} colors={['#4fd3c4', GREEN, '#9cf5d2', '#6f8cff']} positions={[0, 0.4, 0.7, 1]} />
      </Path>
      <Circle cx={paths.now.x} cy={paths.now.y} r={ringR} style="stroke" strokeWidth={1.4} color={GREEN} opacity={ringOpacity} />
      <Circle cx={paths.now.x} cy={paths.now.y} r={dotR} color="#ffffff" />
      <Circle cx={paths.now.x} cy={paths.now.y} r={dotR} style="stroke" strokeWidth={2} color={GREEN} />
    </Canvas>
  );
}

/** The card's edge: a hairline that is brightest along the top, where the light catches it. */
function GlassEdge({ width, height }: { width: number; height: number }) {
  if (width <= 0 || height <= 0) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <RoundedRect x={0.5} y={0.5} width={width - 1} height={height - 1} r={27.5} style="stroke" strokeWidth={1}>
        <SkiaGradient
          start={vec(0, 0)}
          end={vec(width * 0.35, height)}
          colors={['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.14)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0.2)']}
          positions={[0, 0.35, 0.75, 1]}
        />
      </RoundedRect>
    </Canvas>
  );
}

/**
 * A welcome screen under a live aurora: green and violet curtains at three depths ripple over a
 * night sky above a mountain horizon, behind a headline and a frosted glass card with tonight's
 * forecast. Dragging tilts the card in 3D, a sheen sliding across its glass, and the aurora bends
 * towards the finger; let go, the card springs flat.
 */
export function AuroraGlassWelcome({ autoPlay = false, onStart, onSignIn }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [card, setCard] = useState({ width: 0, height: 0 });
  const horizon = useImage(HORIZON);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const clock = useSharedValue(0);
  // The tilt from the finger, and how much of the self-playing tilt shows (it fades out on a touch).
  const userYaw = useSharedValue(0);
  const userPitch = useSharedValue(0);
  const gain = useSharedValue(auto ? 1 : 0);
  const touchX = useSharedValue(0.5);
  const touchY = useSharedValue(0.4);
  const touchPull = useSharedValue(0);
  const pressed = useSharedValue(0);
  const sky = useSharedValue(0);
  const cardIn = useSharedValue(0);

  useEffect(() => {
    gain.set(withTiming(auto ? 1 : 0, { duration: AUTO_FADE }));
  }, [auto, gain]);

  useEffect(() => {
    sky.set(withTiming(1, { duration: reducedMotion ? 600 : SKY_IN, easing: EASE_OUT }));
    cardIn.set(withDelay(CARD_AT, withTiming(1, { duration: CARD_IN, easing: EASE_OUT })));
  }, [sky, cardIn, reducedMotion]);

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 40) / 1000;
    if (!(dt > 0)) return;
    // Under reduced motion the sky still moves, at half speed.
    clock.set((clock.get() + dt * (reducedMotion ? 0.5 : 1)) % LOOP);
  });

  // The self-playing tilt at this moment of the loop: left and back, right and back.
  const autoYaw = useDerivedValue(() => AUTO_YAW * Math.sin((clock.get() / LOOP) * Math.PI * 2) * gain.get());
  const autoPitch = useDerivedValue(() => AUTO_PITCH * Math.sin((clock.get() / LOOP) * Math.PI * 4) * gain.get());
  const yaw = useDerivedValue(() => userYaw.get() + autoYaw.get());
  const pitch = useDerivedValue(() => userPitch.get() + autoPitch.get());

  const uniforms = useDerivedValue(() => {
    const k = gain.get();
    const t = (clock.get() / LOOP) * Math.PI * 2;
    // Under autoPlay the light leans with the card; a finger takes over from it.
    const ax = 0.5 + 0.32 * Math.sin(t);
    const pull = touchPull.get();
    return {
      size: [Math.max(1, width), Math.max(1, height)],
      phase: t,
      pointer: [ax + (touchX.get() - ax) * pull, 0.3 + (touchY.get() - 0.3) * pull],
      pull: Math.max(pull, 0.5 * k),
      reveal: sky.get(),
    };
  });

  const lift = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  // Where the tilt was when the finger came down, so a drag starts from the card as it is.
  const userYawStart = useSharedValue(0);
  const userPitchStart = useSharedValue(0);
  const dragging = useSharedValue(false);

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(2)
    .onStart((event) => {
      // The self-playing tilt hands over to the finger where it is.
      dragging.set(true);
      userYawStart.set(yaw.get());
      userPitchStart.set(pitch.get());
      userYaw.set(yaw.get());
      userPitch.set(pitch.get());
      gain.set(0);
      touchX.set(event.x / Math.max(1, width));
      touchY.set(event.y / Math.max(1, height));
      touchPull.set(withTiming(1, { duration: 250, easing: EASE_OUT }));
      pressed.set(withTiming(1, { duration: 160, easing: EASE_OUT }));
      scheduleOnRN(lift);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      userYaw.set(rubber(event.translationX * TILT_RATE + userYawStart.get()));
      userPitch.set(rubber(-event.translationY * TILT_RATE + userPitchStart.get()));
      touchX.set(event.x / Math.max(1, width));
      touchY.set(event.y / Math.max(1, height));
    })
    .onFinalize((event) => {
      if (!dragging.get()) return;
      dragging.set(false);
      const spring = reducedMotion ? RELEASE_REDUCED : RELEASE;
      const vx = Number.isFinite(event.velocityX) ? event.velocityX : 0;
      const vy = Number.isFinite(event.velocityY) ? event.velocityY : 0;
      userYaw.set(withSpring(0, { ...spring, velocity: vx * TILT_RATE }));
      userPitch.set(withSpring(0, { ...spring, velocity: -vy * TILT_RATE }));
      touchPull.set(withTiming(0, { duration: 1200, easing: EASE_OUT }));
      pressed.set(withTiming(0, { duration: 220, easing: EASE_OUT }));
      if (auto) gain.set(withDelay(AUTO_RESUME, withTiming(1, { duration: AUTO_FADE, easing: Easing.inOut(Easing.quad) })));
    });

  const cardStyle = useAnimatedStyle(() => {
    const e = cardIn.get();
    return {
      opacity: e,
      transform: [
        { perspective: PERSPECTIVE },
        { translateY: reducedMotion ? 0 : (1 - e) * CARD_RISE },
        { rotateX: `${pitch.get()}deg` },
        { rotateY: `${yaw.get()}deg` },
        { scale: 1 + 0.015 * pressed.get() - (reducedMotion ? 0 : (1 - e) * 0.04) },
      ],
    };
  });
  // The sheen slides across the glass opposite the tilt, brighter the more it is tilted.
  const sheenStyle = useAnimatedStyle(() => {
    const y = yaw.get();
    const x = pitch.get();
    return {
      opacity: Math.min(1, 0.2 + Math.hypot(y, x) / MAX_TILT),
      transform: [{ translateX: (-y / MAX_TILT) * 220 }, { translateY: (x / MAX_TILT) * 80 }],
    };
  });
  const chipDot = useAnimatedStyle(() => ({ opacity: 0.5 + 0.5 * Math.sin((clock.get() / LOOP) * Math.PI * PULSES) ** 2 }));

  const horizonW = width * HORIZON_WIDTH;
  const horizonH = horizonW / HORIZON_ASPECT;
  const horizonTop = height * HORIZON_TOP;
  const groundTop = horizonTop + horizonH - 2;
  const horizonTransform = useDerivedValue(() => [{ translateY: reducedMotion ? 0 : (1 - sky.get()) * HORIZON_RISE }]);
  const horizonOpacity = useDerivedValue(() => Math.min(1, sky.get() * 1.6));

  const chartWidth = Math.max(0, card.width - 40);

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          if (w > 0 && h > 0) setSize({ width: w, height: h });
        }}
      >
        {size && AURORA && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={AURORA} uniforms={uniforms} />
            </Fill>
            {horizon && (
              <Group transform={horizonTransform} opacity={horizonOpacity}>
                <Mask
                  mode="luminance"
                  mask={
                    <Rect x={0} y={horizonTop} width={width} height={horizonH}>
                      <SkiaGradient start={vec(0, horizonTop)} end={vec(0, horizonTop + horizonH * 0.16)} colors={['black', 'white']} />
                    </Rect>
                  }
                >
                  <SkiaImage image={horizon} x={(width - horizonW) / 2} y={horizonTop} width={horizonW} height={horizonH} fit="cover">
                    <ColorMatrix matrix={SILHOUETTE} />
                  </SkiaImage>
                </Mask>
              </Group>
            )}
            <Rect x={0} y={groundTop - GROUND_FADE} width={width} height={height - groundTop + GROUND_FADE}>
              <SkiaGradient start={vec(0, groundTop - GROUND_FADE)} end={vec(0, groundTop)} colors={['rgba(4,8,21,0)', NIGHT]} />
            </Rect>
          </Canvas>
        )}

        <Enter at={BAR_AT} rise={0} reduced={reducedMotion} style={[styles.bar, { top: insets.top + 10 }]}>
          <Text style={styles.brand}>SOLVIND</Text>
          <View style={styles.chip}>
            <Animated.View style={[styles.chipDot, chipDot]} />
            <Text style={styles.chipText}>Clear · −4°</Text>
          </View>
        </Enter>

        <View style={[styles.head, { top: insets.top + 74 }]} pointerEvents="none">
          <HeadlineLine text="Chase the" at={LINE_AT[0]} reduced={reducedMotion} />
          <HeadlineLine text="northern light." at={LINE_AT[1]} reduced={reducedMotion} />
          <Enter at={SUB_AT} reduced={reducedMotion}>
            <Text style={styles.sub}>Live forecasts, wake-up alerts and quiet spots far from the city glow.</Text>
          </Enter>
        </View>

        <View style={[styles.stage, { paddingBottom: insets.bottom + 10 }]} pointerEvents="box-none">
          <Animated.View
            style={[styles.card, cardStyle]}
            onLayout={(event: LayoutChangeEvent) => {
              const { width: w, height: h } = event.nativeEvent.layout;
              setCard((c) => (c.width === w && c.height === h ? c : { width: w, height: h }));
            }}
          >
            <BlurView intensity={42} tint="dark" style={StyleSheet.absoluteFill} />
            <View style={[StyleSheet.absoluteFill, styles.cardTint]} />
            <LinearGradient
              colors={['rgba(255,255,255,0.13)', 'rgba(255,255,255,0.03)', 'rgba(255,255,255,0)']}
              locations={[0, 0.28, 0.6]}
              style={StyleSheet.absoluteFill}
              pointerEvents="none"
            />
            <Animated.View style={[styles.sheen, sheenStyle]} pointerEvents="none">
              <LinearGradient
                colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.16)', 'rgba(255,255,255,0)']}
                locations={[0.32, 0.5, 0.68]}
                start={{ x: 0, y: 0.15 }}
                end={{ x: 1, y: 0.85 }}
                style={StyleSheet.absoluteFill}
              />
            </Animated.View>
            <GlassEdge width={card.width} height={card.height} />

            <View style={styles.cardBody}>
              <View style={styles.cardHead}>
                <View style={{ gap: 4 }}>
                  <Text style={styles.eyebrow}>TONIGHT · VÍK</Text>
                  <View style={styles.kpRow}>
                    <Text style={styles.kp}>Kp 6.3</Text>
                    <Text style={styles.level}>High</Text>
                  </View>
                </View>
                <View style={styles.window}>
                  <Text style={styles.windowValue}>21:40 – 01:10</Text>
                  <Text style={styles.windowLabel}>Best window</Text>
                </View>
              </View>

              <View style={{ gap: 6 }}>
                {chartWidth > 0 && <ActivityChart width={chartWidth} clock={clock} reduced={reducedMotion} />}
                <View style={styles.hours}>
                  {HOURS.map((h) => (
                    <Text key={h} style={styles.hour}>
                      {h}
                    </Text>
                  ))}
                </View>
              </View>

              <View style={styles.stats}>
                {STATS.map((s, i) => (
                  <Enter key={s.label} at={STATS_AT + i * STAT_STAGGER} rise={8} reduced={reducedMotion} style={[styles.stat, i > 0 && styles.statRule]}>
                    <Text style={styles.statValue}>{s.value}</Text>
                    <Text style={styles.statLabel}>{s.label}</Text>
                  </Enter>
                ))}
              </View>
            </View>
          </Animated.View>

          <Enter at={CTA_AT} reduced={reducedMotion} style={styles.ctas}>
            <StartButton onPress={onStart} />
            <Pressable accessibilityRole="button" onPress={onSignIn} hitSlop={10}>
              <Text style={styles.link}>I already have an account</Text>
            </Pressable>
          </Enter>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: NIGHT,
  },
  bar: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 32,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brand: {
    color: INK,
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 3.6,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.22)',
  },
  chipDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: GREEN,
  },
  chipText: {
    color: 'rgba(243,246,255,0.88)',
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  head: {
    position: 'absolute',
    left: 24,
    right: 24,
  },
  lineSlot: {
    overflow: 'hidden',
    height: 50,
  },
  headline: {
    color: INK,
    fontSize: 44,
    lineHeight: 50,
    fontWeight: '700',
    letterSpacing: -1.4,
  },
  sub: {
    marginTop: 12,
    color: SOFT,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
    maxWidth: 310,
  },
  stage: {
    ...StyleSheet.absoluteFill,
    // Lifted well in front: a card tilted in 3D would otherwise be cut where it dips behind the sky's layer.
    zIndex: 400,
    justifyContent: 'flex-end',
    paddingHorizontal: 18,
    gap: 16,
  },
  card: {
    borderRadius: 28,
    borderCurve: 'continuous',
    overflow: 'hidden',
    boxShadow: '0 28px 56px rgba(0, 2, 10, 0.55)',
  },
  cardTint: {
    backgroundColor: 'rgba(10, 18, 44, 0.3)',
  },
  sheen: {
    position: 'absolute',
    top: -80,
    bottom: -80,
    left: -220,
    right: -220,
  },
  cardBody: {
    padding: 20,
    gap: 14,
  },
  cardHead: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  eyebrow: {
    color: FAINT,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.5,
  },
  kpRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 10,
  },
  kp: {
    color: INK,
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -0.8,
    fontVariant: ['tabular-nums'],
  },
  level: {
    color: GREEN,
    fontSize: 15,
    fontWeight: '600',
  },
  window: {
    alignItems: 'flex-end',
    gap: 2,
    paddingBottom: 4,
  },
  windowValue: {
    color: INK,
    fontSize: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  windowLabel: {
    color: FAINT,
    fontSize: 12,
    fontWeight: '500',
  },
  hours: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  hour: {
    color: FAINT,
    fontSize: 11,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  stats: {
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(255,255,255,0.16)',
    paddingTop: 14,
  },
  stat: {
    flex: 1,
    gap: 2,
  },
  statRule: {
    borderLeftWidth: StyleSheet.hairlineWidth,
    borderLeftColor: 'rgba(255,255,255,0.16)',
    paddingLeft: 14,
  },
  statValue: {
    color: INK,
    fontSize: 17,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  statLabel: {
    color: FAINT,
    fontSize: 12,
    fontWeight: '500',
  },
  ctas: {
    gap: 14,
  },
  button: {
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ffffff',
  },
  buttonLabel: {
    color: '#08102a',
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  link: {
    color: 'rgba(243,246,255,0.72)',
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
    paddingVertical: 2,
  },
});
