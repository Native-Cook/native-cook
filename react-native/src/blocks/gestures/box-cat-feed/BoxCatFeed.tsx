import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  LinearGradient,
  PaintStyle,
  Picture,
  Rect,
  Skia,
  StrokeCap,
  vec,
  type SkCanvas,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
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

/*
 Box Cat Feed — the daily-treat corner of an invented banking app's pet, Mochi.

 Look (a Pinterest shot of a gamified finance app with a cat in a box): a lilac page, a cardboard box
 with a grey cat peeking over its rim, the pet's name and energy, and a treat to give.

 Ours: flick the fish biscuit from the bottom and it flies in a real arc toward the box; the cat sees it
 coming, springs up out of the box with its body stretching, catches it mid-air, drops back in with a
 squash, munches with its eyes shut and sends up hearts while the daily-treat counter ticks and the
 points pill pops. A weak flick still reaches (the arc is aimed for you). Tap the cat to make it duck.

 autoPlay: LOOP = 9 s — the cat peeks and blinks, a treat is flicked at 1.0 s (caught at 1.7 s, munched
 to 2.9 s), another at 5.0 s (caught at 5.7 s), the cat settles back by 7.6 s (frame 9 = frame 0).
 Reduced motion: the treat appears in the cat's paws; the cat does not jump.
*/

let debugState = "";

const LOOP = 9;
const FLY = 0.7;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const lerp = (a: number, b: number, k: number) => {
  "worklet";
  return a + (b - a) * k;
};
/** The cat's jump at time u after a throw: lift (0..1) and stretch, munching after the catch. */
const catAt = (u: number) => {
  "worklet";
  if (u < 0 || u > 2.6)
    return { lift: 0, stretch: 0, munch: 0, caught: 0, hearts: -1 };
  const up = clamp01((u - (FLY - 0.32)) / 0.32);
  const down = clamp01((u - FLY - 0.12) / 0.35);
  const lift = Math.sin((Math.PI / 2) * up) * (1 - down * down);
  const stretch = up > 0 && up < 1 ? 0.18 * Math.sin(Math.PI * up) : 0;
  const land =
    u > FLY + 0.47 && u < FLY + 0.75
      ? -0.14 * Math.sin(((u - FLY - 0.47) / 0.28) * Math.PI)
      : 0;
  const munch =
    u > FLY + 0.4 && u < FLY + 1.6 ? Math.abs(Math.sin((u - FLY) * 14)) : 0;
  return {
    lift,
    stretch: stretch + land,
    munch,
    caught: u >= FLY ? 1 : 0,
    hearts: u >= FLY + 0.3 ? u - FLY - 0.3 : -1,
  };
};
const scriptThrows = [1.0, 5.0];
const scriptAt = (t: number) => {
  "worklet";
  let at = -10;
  for (let i = 0; i < scriptThrows.length; i++)
    if (t >= scriptThrows[i]) at = scriptThrows[i];
  // the day's counters dip out and back at 8.25–8.75 s and reset at the dip's bottom, so the
  // loop's last frame meets its first
  const x = Math.min(1, Math.max(0, (t - 8.25) / 0.5));
  return {
    u: at > 0 ? t - at : -10,
    count: t >= 8.5 ? 0 : t >= 1.0 + FLY ? (t >= 5.0 + FLY ? 2 : 1) : 0,
    dip: Math.sin(x * Math.PI),
  };
};
/** The fish on its button: fades as it is flicked, comes back when the cat has eaten. */
const treatShown = (u: number) => {
  "worklet";
  if (u < 0) return 1;
  if (u < 0.12) return 1 - u / 0.12;
  if (u < 1.25) return 0;
  return Math.min(1, (u - 1.25) / 0.15);
};

const paint = (c: string, a = 1, stroke = 0) => {
  "worklet";
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(c));
  p.setAlphaf(a);
  if (stroke > 0) {
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(stroke);
    p.setStrokeCap(StrokeCap.Round);
  }
  return p;
};

type Geo = { cx: number; rim: number; s: number; startY: number };
type Out = {
  u: number;
  blink: number;
  t: number;
  duck: number;
  count: number;
  dip: number;
};

const drawFish = (
  cv: SkCanvas,
  x: number,
  y: number,
  k: number,
  rot: number,
) => {
  "worklet";
  cv.save();
  cv.translate(x, y);
  cv.rotate(rot, 0, 0);
  cv.scale(k, k);
  cv.drawOval(Skia.XYWHRect(-16, -9, 28, 18), paint("#FFB25C"));
  const tail = Skia.PathBuilder.Make();
  tail.moveTo(10, 0);
  tail.lineTo(22, -9);
  tail.lineTo(22, 9);
  tail.close();
  cv.drawPath(tail.detach(), paint("#FF9A3D"));
  cv.drawCircle(-8, -2, 2.4, paint("#5A3A20"));
  cv.drawLine(-2, -6, -2, 6, paint("#E8893A", 1, 1.6));
  cv.restore();
};

const drawScene = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const { cx, rim, s } = g;
  const c = catAt(o.u);
  const lift = reduced ? 0 : c.lift;
  const bw = s * 2.3;
  const bh = s * 1.5;
  // box back + inside
  cv.drawOval(
    Skia.XYWHRect(cx - bw * 0.62, rim + bh - 10, bw * 1.24, 30),
    paint("#5B3F8C", 0.14),
  );
  const backFlap = Skia.PathBuilder.Make();
  backFlap.moveTo(cx - bw / 2 + 8, rim);
  backFlap.lineTo(cx - bw / 2 + 20, rim - s * 0.5);
  backFlap.lineTo(cx + bw / 2 - 20, rim - s * 0.5);
  backFlap.lineTo(cx + bw / 2 - 8, rim);
  backFlap.close();
  cv.drawPath(backFlap.detach(), paint("#C78C55"));
  cv.drawRect(Skia.XYWHRect(cx - bw / 2, rim - 6, bw, 14), paint("#8E5A33"));
  // cat: rises out of the box; body stretches in the jump and squashes on landing
  const duck = o.duck;
  const headY = rim - s * 0.42 - lift * s * 1.7 + duck * s * 0.5;
  const sy = 1 + c.stretch;
  cv.save();
  cv.clipRect(Skia.XYWHRect(cx - bw, -2000, bw * 2, rim + 4 + 2000), 1, true);
  cv.translate(cx, headY);
  cv.scale(1 / Math.sqrt(sy), sy);
  // body
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(-s * 0.55, s * 0.3, s * 1.1, s * 1.6),
      s * 0.5,
      s * 0.5,
    ),
    paint("#8E97A8"),
  );
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(-s * 0.3, s * 0.45, s * 0.6, s * 1.2),
      s * 0.3,
      s * 0.3,
    ),
    paint("#E9ECF2"),
  );
  // ears
  for (let k = -1; k <= 1; k += 2) {
    const ear = Skia.PathBuilder.Make();
    ear.moveTo(k * s * 0.55, -s * 0.2);
    ear.lineTo(k * s * 0.42, -s * 0.78);
    ear.lineTo(k * s * 0.08, -s * 0.42);
    ear.close();
    cv.drawPath(ear.detach(), paint("#8E97A8"));
    const inner = Skia.PathBuilder.Make();
    inner.moveTo(k * s * 0.45, -s * 0.28);
    inner.lineTo(k * s * 0.38, -s * 0.62);
    inner.lineTo(k * s * 0.18, -s * 0.42);
    inner.close();
    cv.drawPath(inner.detach(), paint("#F4A9B8"));
  }
  // head
  cv.drawOval(
    Skia.XYWHRect(-s * 0.68, -s * 0.52, s * 1.36, s * 1.04),
    paint("#98A1B2"),
  );
  // eyes: open, blinking, or shut happily while munching
  const shut = c.munch > 0 ? 1 : o.blink;
  for (let k = -1; k <= 1; k += 2) {
    if (shut > 0.5) {
      const b = Skia.PathBuilder.Make();
      b.addArc(
        Skia.XYWHRect(k * s * 0.27 - s * 0.1, -s * 0.12, s * 0.2, s * 0.14),
        200,
        140,
      );
      cv.drawPath(b.detach(), paint("#2B2433", 1, 3));
    } else {
      cv.drawCircle(k * s * 0.27, -s * 0.04, s * 0.09, paint("#2B2433"));
      cv.drawCircle(
        k * s * 0.27 + s * 0.03,
        -s * 0.07,
        s * 0.03,
        paint("#FFFFFF"),
      );
    }
    cv.drawLine(
      k * s * 0.42,
      s * 0.12,
      k * s * 0.82,
      s * 0.06,
      paint("#6E7686", 1, 1.4),
    );
    cv.drawLine(
      k * s * 0.42,
      s * 0.2,
      k * s * 0.82,
      s * 0.24,
      paint("#6E7686", 1, 1.4),
    );
  }
  cv.drawCircle(-s * 0.4, s * 0.12, s * 0.09, paint("#F4A9B8", 0.6));
  cv.drawCircle(s * 0.4, s * 0.12, s * 0.09, paint("#F4A9B8", 0.6));
  cv.drawOval(
    Skia.XYWHRect(-s * 0.06, s * 0.06, s * 0.12, s * 0.08),
    paint("#F48BA0"),
  );
  const mouth =
    0.05 + 0.1 * c.munch + (lift > 0.6 && c.caught === 0 ? 0.12 : 0);
  cv.drawOval(
    Skia.XYWHRect(-s * 0.1, s * 0.16, s * 0.2, s * mouth),
    paint("#5A2A3A"),
  );
  // the caught treat in its mouth
  if (c.caught > 0 && c.munch > 0)
    drawFish(
      cv,
      s * 0.02,
      s * 0.26,
      0.7 * (1 - clamp01((o.u - FLY - 1.2) / 0.4)),
      0,
    );
  cv.restore();
  // paws on the rim
  for (let k = -1; k <= 1; k += 2) {
    cv.drawOval(
      Skia.XYWHRect(
        cx + k * s * 0.4 - s * 0.18,
        rim - s * 0.08 - lift * s * 0.6,
        s * 0.36,
        s * 0.22,
      ),
      paint("#A7AFBE", 1 - lift),
    );
  }
  // box front
  const front = Skia.Paint();
  front.setAntiAlias(true);
  front.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: rim },
      { x: 0, y: rim + bh },
      [Skia.Color("#E3A86C"), Skia.Color("#CF9258")],
      null,
      0,
    ),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(cx - bw / 2, rim, bw, bh), 10, 10),
    front,
  );
  const flapL = Skia.PathBuilder.Make();
  flapL.moveTo(cx - bw / 2, rim);
  flapL.lineTo(cx - bw / 2 - s * 0.55, rim + s * 0.28);
  flapL.lineTo(cx - bw / 2 - s * 0.45, rim + s * 0.55);
  flapL.lineTo(cx - bw / 2, rim + s * 0.22);
  flapL.close();
  cv.drawPath(flapL.detach(), paint("#D69A5E"));
  const flapR = Skia.PathBuilder.Make();
  flapR.moveTo(cx + bw / 2, rim);
  flapR.lineTo(cx + bw / 2 + s * 0.55, rim + s * 0.28);
  flapR.lineTo(cx + bw / 2 + s * 0.45, rim + s * 0.55);
  flapR.lineTo(cx + bw / 2, rim + s * 0.22);
  flapR.close();
  cv.drawPath(flapR.detach(), paint("#C48650"));
  cv.drawLine(
    cx - bw * 0.3,
    rim + bh * 0.55,
    cx + bw * 0.3,
    rim + bh * 0.55,
    paint("#A86F3E", 0.5, 2),
  );
  // the treat in flight: a real arc from the bottom to the cat's mouth
  if (o.u >= 0 && o.u < FLY && !reduced) {
    const k = o.u / FLY;
    const tx = lerp(cx + s * 0.9, cx, k);
    const ty =
      lerp(g.startY, rim - s * 0.42 - s * 1.7 + s * 0.26, k) -
      Math.sin(Math.PI * k) * s * 2.2;
    drawFish(cv, tx, ty, 1, k * 540);
  }
  // hearts
  if (c.hearts >= 0 && c.hearts < 1.4) {
    for (let i = 0; i < 3; i++) {
      const h = c.hearts - i * 0.18;
      if (h < 0) continue;
      const hx = cx + (i - 1) * s * 0.6 + Math.sin(h * 6 + i) * 6;
      const hy = rim - s * 1.6 - h * s * 1.2;
      const a = 1 - clamp01(h / 1.0);
      const sz = s * 0.16 * (0.6 + 0.4 * clamp01(h * 4));
      const hp = Skia.PathBuilder.Make();
      hp.moveTo(hx, hy + sz);
      hp.cubicTo(
        hx - sz * 2,
        hy - sz * 0.4,
        hx - sz * 0.8,
        hy - sz * 1.6,
        hx,
        hy - sz * 0.5,
      );
      hp.cubicTo(
        hx + sz * 0.8,
        hy - sz * 1.6,
        hx + sz * 2,
        hy - sz * 0.4,
        hx,
        hy + sz,
      );
      cv.drawPath(hp.detach(), paint("#FF6B8B", a));
    }
  }
};

type Props = { autoPlay?: boolean };

export function BoxCatFeed({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "BCF-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "BCF-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "BCF-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });

  const g: Geo = {
    cx: W / 2,
    rim: Math.round(H * 0.56),
    s: Math.round(W * 0.2),
    startY: H - insets.bottom - 120,
  };

  const st = useSharedValue({
    at: -10,
    count: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    duck: 0,
    duckV: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    u: -10,
    blink: 0,
    t: 0,
    duck: 0,
    count: 0,
    dip: 0,
  });
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __boxCat?: unknown };
    gl.__boxCat = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__boxCat;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0)
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
    const bp = now % 3.4;
    const blink = bp < 0.14 ? 1 : 0;
    s.duckV += (-160 * s.duck - 14 * s.duckV) * dt;
    s.duck += s.duckV * dt;
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({
        u: sc.u,
        blink: 0,
        t: dbg,
        duck: 0,
        count: sc.count,
        dip: sc.dip,
      });
      return;
    }
    if (
      auto &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || now - s.at > 2.8)
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      out.set({
        u: sc.u,
        blink,
        t: now,
        duck: s.duck,
        count: sc.count,
        dip: sc.dip,
      });
      return;
    }
    s.scripted = 0;
    out.set({
      u: now - s.at,
      blink,
      t: now,
      duck: s.duck,
      count: now - s.at >= FLY ? s.count : Math.max(0, s.count - 1),
      dip: 0,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `u=${(now - s.at).toFixed(2)} count=${s.count} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().count,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setCount, v);
    },
  );

  const flick = Gesture.Pan().onEnd((e) => {
    const s = st.get();
    const now = clock.get();
    if (now - s.at < 1.6) return;
    if (e.translationY < -24 || e.velocityY < -300) {
      s.at = now;
      s.count = (s.count % 3) + 1;
      s.scripted = 0;
      s.lastTouch = now;
      scheduleOnRN(buzz, 0);
    }
  });
  const pet = Gesture.Tap().onEnd(() => {
    const s = st.get();
    s.duckV += 4;
    s.lastTouch = clock.get();
    scheduleOnRN(buzz, 0);
  });

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawScene(cv, g, out.get(), reduced)),
  );
  const restTreat = useAnimatedStyle(() => {
    const u = out.get().u;
    const k = treatShown(u);
    return {
      opacity: k,
      transform: [{ scale: 0.8 + 0.2 * k }],
    };
  });
  const points = useAnimatedStyle(() => {
    const u = out.get().u - FLY;
    const k = u >= 0 && u < 0.4 ? Math.sin((u / 0.4) * Math.PI) : 0;
    return {
      opacity: 1 - out.get().dip,
      transform: [{ scale: 1 + 0.18 * k }],
    };
  });
  const counters = useAnimatedStyle(() => {
    return { opacity: 1 - out.get().dip };
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={W} height={H}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, H)}
            colors={["#EDE6FF", "#F6F2FF", "#FFF4EC"]}
          />
        </Rect>
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.header, { top: insets.top + 10 }]}>
        <View>
          <Text style={styles.small}>Your bank buddy</Text>
          <Text style={styles.name}>Mochi</Text>
        </View>
        <Animated.View
          style={[styles.points, points]}
          accessible
          accessibilityLabel={`${1240 + count * 10} points`}
        >
          <Ionicons name="sparkles" size={14} color="#7A4DFF" />
          <Text style={styles.pointsText}>{1240 + count * 10} pts</Text>
        </Animated.View>
      </View>
      <Animated.View style={[styles.mood, { top: insets.top + 86 }, counters]}>
        <Text style={styles.moodText}>
          {count > 0
            ? "Purring. Thanks for the snack!"
            : "Peeking out for a snack…"}
        </Text>
      </Animated.View>
      <GestureDetector gesture={pet}>
        <View
          style={[styles.catHit, { top: g.rim - g.s * 1.3, height: g.s * 2.4 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Mochi the cat. Tap to pet"
        />
      </GestureDetector>
      <View style={[styles.daily, { bottom: insets.bottom + 26 }]}>
        <View>
          <Text style={styles.dailyTitle}>Daily treats</Text>
          <Animated.View style={[styles.dots, counters]}>
            {[0, 1, 2].map((i) => (
              <View key={i} style={[styles.dot, i < count && styles.dotOn]} />
            ))}
          </Animated.View>
        </View>
        <GestureDetector gesture={flick}>
          <Animated.View
            style={[styles.treat, restTreat]}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Fish treat. Flick it up to Mochi"
            accessibilityHint="Swipe up"
          >
            <Ionicons name="fish" size={26} color="#FF9A3D" />
            <Text style={styles.treatText}>Flick up</Text>
          </Animated.View>
        </GestureDetector>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F3EEFF" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  small: { fontFamily: "BCF-Bold", fontSize: 14, color: "#8A7FA8" },
  name: { fontFamily: "BCF-Title", fontSize: 30, color: "#2B2433" },
  points: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    backgroundColor: "#FFFFFF",
  },
  pointsText: { fontFamily: "BCF-Black", fontSize: 15, color: "#2B2433" },
  mood: { position: "absolute", left: 20, right: 20 },
  moodText: { fontFamily: "BCF-Bold", fontSize: 15, color: "#6E6488" },
  catHit: { position: "absolute", left: 60, right: 60 },
  daily: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 84,
    borderRadius: 26,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingLeft: 20,
    paddingRight: 10,
  },
  dailyTitle: { fontFamily: "BCF-Black", fontSize: 16, color: "#2B2433" },
  dots: { flexDirection: "row", gap: 6, marginTop: 8 },
  dot: { width: 26, height: 8, borderRadius: 4, backgroundColor: "#EDE6FF" },
  dotOn: { backgroundColor: "#7A4DFF" },
  treat: {
    width: 110,
    height: 64,
    borderRadius: 20,
    backgroundColor: "#FFF1DE",
    alignItems: "center",
    justifyContent: "center",
  },
  treatText: { fontFamily: "BCF-Black", fontSize: 12, color: "#C9772F" },
});
