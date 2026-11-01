import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  Group,
  LinearGradient,
  PaintStyle,
  Picture,
  Rect,
  Shader,
  Skia,
  StrokeCap,
  vec,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import {
  PixelRatio,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { petFx } from "./ehm-scene";

/*
 Evolve Hold Morph — the pet screen of an invented self-care app, Sprout.

 Look (a Pinterest shot of a casual game's "Evolve!" moment, and habit pets like it): a soft mint page,
 the pet's name and level, the pet standing on a little nest in the middle, its stages under it, an
 energy card and a big "Hold to evolve" button.

 Ours: the pet is a clay sculpture ray-marched in one shader, and evolving is a hold. Holding the
 button (or the pet) charges a ring round it; the pet stretches, trembles and glows, and past half
 charge its shape starts to become the next form — the egg's shell swells into a chick, the chick grows
 a crest, wings and tail into a bird — because the two forms' distance fields are mixed by the charge.
 Let go early and it all springs back; hold to the end and it commits with a sparkle burst, a jelly
 wobble and a success haptic. Haptic ticks speed up as the charge builds. Tap the pet to make it hop.

 autoPlay: LOOP = 9 s — egg rests, charges 1.0–2.4 and becomes a chick; the chick charges 4.4–5.8 and
 becomes a bird; the bird charges 7.6–9.0 back into a new egg (frame 9 = frame 0). Reduced motion: no
 tremble or wobble; a completed hold crossfades the forms.
*/

let debugState = "";

const LOOP = 9;
const CHARGE_T = 1.4;
const NAMES = ["Egg", "Chick", "Bluebird"];
const CTA = ["Hold to hatch", "Hold to evolve", "Hold for a new egg"];

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoothstep = (a: number, b: number, v: number) => {
  "worklet";
  const x = clamp01((v - a) / (b - a));
  return x * x * (3 - 2 * x);
};
const wobble = (tau: number) => {
  "worklet";
  return tau < 0 ? 0 : -0.08 * Math.exp(-6 * tau) * Math.sin(16 * tau);
};
const chargeShape = (c: number) => {
  "worklet";
  // stretch up while charging, back to rest exactly when the morph completes
  return -0.07 * Math.sin(Math.PI * clamp01(c));
};
/** The script at loop time t. */
const scriptAt = (t: number) => {
  "worklet";
  const seg = [
    { s: 0, a: 1.0, b: 2.4 },
    { s: 1, a: 4.4, b: 5.8 },
    { s: 2, a: 7.6, b: 9.0 },
  ];
  let stage = 0;
  let c = 0;
  let commitAt = -10;
  for (let i = 0; i < 3; i++) {
    const g = seg[i];
    if (t >= g.b) {
      stage = (g.s + 1) % 3;
      commitAt = g.b;
    } else if (t >= g.a) {
      stage = g.s;
      c = (t - g.a) / (g.b - g.a);
    }
  }
  // the egg settles from the commit that closed the last loop (9 s = 0 s), so the loop has no jump
  if (t < 1.0) commitAt = 0;
  const m = smoothstep(0.5, 1, c);
  const sq = chargeShape(c) + (commitAt >= 0 ? wobble(t - commitAt) : 0);
  return {
    stage,
    c,
    m,
    sq,
    burst: commitAt > 0 && commitAt < 9 && stage !== 0 ? t - commitAt : -1,
  };
};

type Out = {
  stage: number;
  c: number;
  m: number;
  sq: number;
  yaw: number;
  burst: number;
  blink: number;
  t: number;
  hop: number;
  hold: number;
};

type Props = { autoPlay?: boolean };

export function EvolveHoldMorph({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "EHM-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "EHM-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "EHM-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });

  const cy = Math.round(H * 0.43);
  const S = Math.round(W * 0.42);
  const px = 1 / (S * PixelRatio.get());

  const st = useSharedValue({
    stage: 0,
    c: 0,
    cV: 0,
    hold: 0,
    sq: 0,
    sqV: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    burstAt: -10,
    tick: 0,
    hop: 0,
    hopV: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    stage: 0,
    c: 0,
    m: 0,
    sq: 0,
    yaw: 0,
    burst: -1,
    blink: 0,
    t: 0,
    hop: 0,
    hold: 0,
  });
  const [stage, setStage] = useState(0);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __evolve?: unknown };
    gl.__evolve = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__evolve;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
    else if (k === 1)
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(
        () => {},
      );
  };

  useFrameCallback((info) => {
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const s = st.get();
    const dbg = debugT.get();
    const blinkPh = now % 3.6;
    const blink = blinkPh < 0.14 ? Math.sin((blinkPh / 0.14) * Math.PI) : 0;
    const sway = reduced ? 0 : 0.28 * Math.sin((now * 2 * Math.PI) / LOOP);
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({
        stage: sc.stage,
        c: sc.c,
        m: sc.m,
        sq: sc.sq,
        yaw: 0.28 * Math.sin((dbg * 2 * Math.PI) / LOOP),
        burst: sc.burst,
        blink: 0,
        t: dbg,
        hop: 0,
        hold: 0,
      });
      return;
    }
    let yaw = sway;
    if (
      auto &&
      s.hold === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.c < 0.001 && Math.abs(s.sq) < 0.004))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = s.stage === 0 ? 0.9 : s.stage === 1 ? 3.6 : 6.8;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.stage = sc.stage;
      s.c = sc.c;
      s.sq = sc.sq;
      s.sqV = 0;
      s.burstAt = sc.burst >= 0 ? now - sc.burst : -10;
    } else {
      s.scripted = 0;
      if (s.hold === 1) {
        s.c += dt / CHARGE_T;
        // haptic ticks get closer together as the charge builds
        const ticks = Math.floor(s.c * (4 + s.c * 8));
        if (ticks !== s.tick) {
          s.tick = ticks;
          scheduleOnRN(buzz, 0);
        }
        if (s.c >= 1) {
          s.c = 0;
          s.cV = 0;
          s.stage = (s.stage + 1) % 3;
          s.burstAt = s.stage === 0 ? -10 : now;
          s.hold = 0;
          s.tick = 0;
          if (!reduced) s.sqV -= 1.6;
          scheduleOnRN(buzz, 2);
        }
      } else if (s.c > 0) {
        // let go early: the charge drains and the shape springs back
        s.cV += (-90 * s.c - 14 * s.cV) * dt;
        s.c += s.cV * dt;
        if (s.c < 0.001) {
          s.c = 0;
          s.cV = 0;
        }
      }
      const target = reduced ? 0 : chargeShape(s.c);
      s.sqV += (240 * (target - s.sq) - 8 * s.sqV) * dt;
      s.sq += s.sqV * dt;
      if (s.hold === 1 && !reduced) yaw += Math.sin(now * 46) * 0.05 * s.c;
    }
    s.hopV -= 1600 * dt;
    s.hop += s.hopV * dt;
    if (s.hop < 0) {
      s.hop = 0;
      s.hopV = Math.abs(s.hopV) > 80 ? -s.hopV * 0.3 : 0;
    }
    const m = reduced ? (s.c > 0.95 ? 1 : 0) : smoothstep(0.5, 1, s.c);
    out.set({
      stage: s.stage,
      c: s.c,
      m,
      sq: s.sq,
      yaw,
      burst: s.burstAt > 0 ? now - s.burstAt : -1,
      blink,
      t: now,
      hop: s.hop,
      hold: s.hold,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `stage=${s.stage} c=${s.c.toFixed(2)} hold=${s.hold} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().stage,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setStage, v);
    },
  );

  // one hold gesture per detector (a gesture object cannot be shared between two detectors)
  const makeHold = () =>
    Gesture.LongPress()
      .minDuration(0)
      .maxDistance(80)
      .onBegin(() => {
        const s = st.get();
        s.hold = 1;
        s.scripted = 0;
        s.tick = 0;
        s.lastTouch = clock.get();
      })
      .onFinalize(() => {
        const s = st.get();
        s.hold = 0;
        s.lastTouch = clock.get();
      });
  const holdG = makeHold();
  const holdPet = makeHold();
  const petTap = Gesture.Tap()
    .maxDuration(200)
    .onEnd(() => {
      const s = st.get();
      s.hopV = 380;
      s.lastTouch = clock.get();
      scheduleOnRN(buzz, 1);
    });

  const uniforms = useDerivedValue(() => {
    const o = out.get();
    const nxt = (o.stage + 1) % 3;
    return {
      c: vec(W / 2, cy),
      S,
      yaw: o.yaw,
      fa: o.stage,
      fb: nxt,
      m: o.m,
      sq: o.sq,
      charge: o.c,
      blink: o.blink,
      px,
    };
  });
  const lift = useDerivedValue(() => [{ translateY: -out.get().hop }]);
  const nest = useNest(W, cy, S);
  const fx = useDerivedValue(() =>
    createPicture((cv) => {
      const o = out.get();
      // charge ring
      if (o.c > 0.001) {
        const p = Skia.Paint();
        p.setAntiAlias(true);
        p.setStyle(PaintStyle.Stroke);
        p.setStrokeWidth(6);
        p.setStrokeCap(StrokeCap.Round);
        p.setColor(Skia.Color("#FFB53B"));
        p.setAlphaf(0.25 + 0.75 * clamp01(o.c * 3));
        const r = S * 0.82;
        const b = Skia.PathBuilder.Make();
        b.addArc(
          Skia.XYWHRect(W / 2 - r, cy - r - 4, r * 2, r * 2),
          -90,
          360 * o.c,
        );
        cv.drawPath(b.detach(), p);
      }
      // sparkle burst after an evolution
      if (o.burst >= 0 && o.burst < 0.9) {
        const k = o.burst / 0.9;
        for (let i = 0; i < 14; i++) {
          const a = (i / 14) * Math.PI * 2 + 0.3;
          const d = S * (0.45 + 0.75 * (1 - Math.pow(1 - k, 3)));
          const sp = Skia.Paint();
          sp.setAntiAlias(true);
          sp.setColor(Skia.Color(i % 2 ? "#FFD34D" : "#FF8FB1"));
          sp.setAlphaf(1 - k);
          cv.drawCircle(
            W / 2 + Math.cos(a) * d,
            cy - 4 + Math.sin(a) * d * 0.9,
            (i % 3 === 0 ? 7 : 4.5) * (1 - k * 0.6),
            sp,
          );
        }
      }
    }),
  );

  const ctaFill = useAnimatedStyle(() => ({
    transform: [{ scaleX: out.get().c }],
  }));
  const ctaPress = useAnimatedStyle(() => ({
    transform: [{ scale: out.get().hold ? 0.97 : 1 }],
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={W} height={H}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, H)}
            colors={["#DDF3E8", "#F3FAF2", "#FFF7EA"]}
          />
        </Rect>
        {/* nest: a soft woven ring under the pet */}
        <Picture picture={nest} />
        {petFx && (
          <Group transform={lift}>
            <Rect x={0} y={cy - S * 1.2} width={W} height={S * 2.3}>
              <Shader source={petFx} uniforms={uniforms} />
            </Rect>
          </Group>
        )}
        <Picture picture={fx} />
      </Canvas>

      <View style={[styles.header, { top: insets.top + 8 }]}>
        <View>
          <Text style={styles.small}>Your pet</Text>
          <Text style={styles.name}>Pip the {NAMES[stage]}</Text>
        </View>
        <View
          style={styles.level}
          accessible
          accessibilityLabel={`Level ${4 + stage}`}
        >
          <Ionicons name="leaf" size={14} color="#2F9E62" />
          <Text style={styles.levelText}>Lv {4 + stage}</Text>
        </View>
      </View>

      <GestureDetector gesture={Gesture.Exclusive(petTap, holdPet)}>
        <View
          style={[styles.petHit, { top: cy - S * 0.95, height: S * 1.75 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={`Pip the ${NAMES[stage]}. Tap to play, press and hold to evolve`}
        />
      </GestureDetector>

      <View style={[styles.stages, { top: cy + S * 0.9 }]}>
        {NAMES.map((n, i) => (
          <View key={n} style={styles.stageItem}>
            <View style={[styles.stageDot, i <= stage && styles.stageDotOn]} />
            <Text style={[styles.stageText, i === stage && styles.stageTextOn]}>
              {n}
            </Text>
          </View>
        ))}
      </View>

      <View
        style={[styles.card, { bottom: insets.bottom + 100 }]}
        accessible
        accessibilityLabel="Evolution energy full, 120 of 120"
      >
        <View style={styles.cardIcon}>
          <Ionicons name="flash" size={18} color="#F2A516" />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle}>Evolution energy</Text>
          <View style={styles.energy}>
            <View style={styles.energyFill} />
          </View>
        </View>
        <Text style={styles.cardNum}>120/120</Text>
      </View>

      <GestureDetector gesture={holdG}>
        <Animated.View
          style={[styles.cta, { bottom: insets.bottom + 20 }, ctaPress]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={CTA[stage]}
          accessibilityHint="Press and hold until the ring is full"
        >
          <Animated.View style={[styles.ctaFill, ctaFill]} />
          <Ionicons name="sparkles" size={18} color="#3B2A06" />
          <Text style={styles.ctaText}>{CTA[stage]}</Text>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

function useNest(W: number, cy: number, S: number) {
  return useDerivedValue(() =>
    createPicture((cv) => {
      const y = cy + S * 0.62;
      const shadow = Skia.Paint();
      shadow.setAntiAlias(true);
      shadow.setColor(Skia.Color("#2C5A44"));
      shadow.setAlphaf(0.14);
      cv.drawOval(
        Skia.XYWHRect(W / 2 - S * 0.62, y - S * 0.05, S * 1.24, S * 0.22),
        shadow,
      );
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeCap(StrokeCap.Round);
      p.setStrokeWidth(5);
      for (let i = 0; i < 9; i++) {
        p.setColor(Skia.Color(i % 2 ? "#C98E52" : "#B57A40"));
        const a = -0.15 + i * 0.04;
        cv.drawArc(
          Skia.XYWHRect(
            W / 2 - S * (0.56 - a * 0.2),
            y - S * 0.12 + i * 1.6,
            S * (1.12 - a * 0.4),
            S * 0.26,
          ),
          10 + i * 4,
          160 - i * 6,
          false,
          p,
        );
      }
    }),
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#EAF6EF" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  small: { fontFamily: "EHM-Bold", fontSize: 14, color: "#6E8A7C" },
  name: {
    fontFamily: "EHM-Title",
    fontSize: 26,
    color: "#22362C",
    marginTop: 2,
  },
  level: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 34,
    paddingHorizontal: 12,
    borderRadius: 17,
    backgroundColor: "#FFFFFF",
  },
  levelText: { fontFamily: "EHM-Black", fontSize: 14, color: "#22362C" },
  petHit: { position: "absolute", left: 40, right: 40 },
  stages: {
    position: "absolute",
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "center",
    gap: 28,
  },
  stageItem: { alignItems: "center", gap: 6 },
  stageDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: "#C9DCD1",
  },
  stageDotOn: { backgroundColor: "#2F9E62" },
  stageText: { fontFamily: "EHM-Bold", fontSize: 13, color: "#8AA396" },
  stageTextOn: { color: "#22362C" },
  card: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 64,
    borderRadius: 20,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
  },
  cardIcon: {
    width: 36,
    height: 36,
    borderRadius: 12,
    backgroundColor: "#FFF2D6",
    alignItems: "center",
    justifyContent: "center",
  },
  cardTitle: { fontFamily: "EHM-Black", fontSize: 14, color: "#22362C" },
  energy: {
    height: 6,
    borderRadius: 3,
    backgroundColor: "#F1EBDD",
    marginTop: 6,
    overflow: "hidden",
  },
  energyFill: { height: 6, width: "100%", backgroundColor: "#F2B63A" },
  cardNum: { fontFamily: "EHM-Black", fontSize: 14, color: "#8A6A1E" },
  cta: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 60,
    borderRadius: 30,
    backgroundColor: "#FFE29A",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    overflow: "hidden",
    borderBottomWidth: 4,
    borderBottomColor: "#E8B94A",
  },
  ctaFill: {
    position: "absolute",
    left: 0,
    top: 0,
    bottom: 0,
    width: "100%",
    backgroundColor: "#FFC93D",
    transformOrigin: "left",
  },
  ctaText: { fontFamily: "EHM-Black", fontSize: 18, color: "#3B2A06" },
});
