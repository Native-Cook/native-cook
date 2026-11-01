import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  type SkCanvas,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

/*
 Island Sleep Dial — the bedtime screen of an invented sleep app, Driftwood.

 Look (a Pinterest night scene of a small floating island under a crescent moon): a deep blue night, a
 grassy island with a cabin and pines floating on its rock, stars around it.

 Ours: the island is the alarm. A ring around it is the night: bedtime sits at the top and the moon
 rides the ring — drag the moon round to choose how long you sleep, in 15-minute steps with a haptic
 each. The more sleep you give it, the more the island settles: its cabin windows warm up, fireflies
 come out and the stars thicken. Set alarm closes the night: clouds roll in over the island and it
 drifts up as "Sleep well" fades in.

 autoPlay: LOOP = 9 s — 6 h at rest, the moon goes round to 9 h (1.0–3.0), back to 8 h (3.6–4.6), the
 alarm is set (clouds 5.6–6.4), the night reopens to 6 h (7.6–8.8) (frame 9 = frame 0). Reduced motion:
 the island does not bob, no fireflies drift; clouds fade.
*/

let debugState = "";

const LOOP = 9;
const MIN_H = 4;
const MAX_H = 11;
const START_H = 6;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};
const scriptAt = (t: number) => {
  "worklet";
  let h = START_H;
  if (t >= 1.0 && t < 3.0) h = START_H + 3 * smoother((t - 1.0) / 2.0);
  else if (t >= 3.0 && t < 3.6) h = 9;
  else if (t >= 3.6 && t < 7.6) h = 9 - smoother((t - 3.6) / 1.0);
  else if (t >= 7.6 && t < 8.8) h = 8 - 2 * smoother((t - 7.6) / 1.2);
  const cloud =
    t >= 5.6 && t < 7.6
      ? smoother((t - 5.6) / 0.8) * (1 - smoother((t - 7.0) / 0.6))
      : 0;
  return { h, cloud };
};
const fmtH = (h: number) => {
  "worklet";
  const m = Math.round(h * 4) * 15;
  return `${Math.floor(m / 60)}h ${m % 60 < 10 ? "0" : ""}${m % 60}m`;
};
const fmtWake = (h: number) => {
  "worklet";
  const m = (23 * 60 + Math.round(h * 4) * 15) % (24 * 60);
  const hh = Math.floor(m / 60);
  const mm = m % 60;
  return `${hh === 0 ? 12 : hh > 12 ? hh - 12 : hh}:${mm < 10 ? "0" : ""}${mm} ${hh < 12 ? "AM" : "PM"}`;
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

type Geo = {
  cx: number;
  cy: number;
  R: number;
  s: number;
  W: number;
  H: number;
};
type Out = { h: number; cloud: number; t: number; drag: number };

const drawNight = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const { cx, cy, R, s, W, H } = g;
  const calm = clamp01((o.h - MIN_H) / (MAX_H - MIN_H));
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: H },
      [Skia.Color("#141A3C"), Skia.Color("#26306A"), Skia.Color("#3B3F86")],
      null,
      0,
    ),
  );
  cv.drawRect(Skia.XYWHRect(0, 0, W, H), sky);
  // stars thicken as the night gets longer
  for (let i = 0; i < 46; i++) {
    const on = i < 16 + calm * 30 ? 1 : 0;
    if (!on) continue;
    const x = ((i * 97) % 389) * (W / 389);
    const y = ((i * 61) % 433) * ((H * 0.7) / 433);
    const tw = reduced
      ? 0.7
      : 0.45 + 0.4 * Math.sin((o.t * 2 * Math.PI * (1 + (i % 3))) / LOOP + i);
    cv.drawCircle(x, y, 1 + (i % 3) * 0.5, paint("#FFFFFF", tw));
  }
  // dial ring: bedtime at the top, the moon at the chosen wake time
  cv.drawCircle(cx, cy, R, paint("#FFFFFF", 0.12, 10));
  const a0 = -Math.PI / 2;
  const a1 = a0 + (o.h / 12) * Math.PI * 2;
  const arc = Skia.PathBuilder.Make();
  arc.addArc(
    Skia.XYWHRect(cx - R, cy - R, R * 2, R * 2),
    -90,
    (o.h / 12) * 360,
  );
  const ap = paint("#9FB2FF", 0.9, 10);
  cv.drawPath(arc.detach(), ap);
  for (let k = 0; k < 12; k++) {
    const a = a0 + (k / 12) * Math.PI * 2;
    cv.drawCircle(
      cx + Math.cos(a) * (R - 22),
      cy + Math.sin(a) * (R - 22),
      k % 3 === 0 ? 2.4 : 1.4,
      paint("#FFFFFF", 0.4),
    );
  }
  // bedtime marker
  cv.drawCircle(cx, cy - R, 15, paint("#2A3470"));
  cv.drawCircle(cx, cy - R, 15, paint("#9FB2FF", 1, 2));
  // island bobbing
  const bob = reduced
    ? 0
    : Math.sin((o.t * 2 * Math.PI * 2) / LOOP) * 5 - o.cloud * 40;
  const iy = cy + bob;
  // rock underside
  const rock = Skia.PathBuilder.Make();
  rock.moveTo(cx - s * 1.05, iy);
  rock.quadTo(cx - s * 0.6, iy + s * 0.9, cx - s * 0.1, iy + s * 1.35);
  rock.quadTo(cx + s * 0.25, iy + s * 0.8, cx + s * 1.05, iy);
  rock.close();
  cv.drawPath(rock.detach(), paint("#5B4E8C"));
  const rock2 = Skia.PathBuilder.Make();
  rock2.moveTo(cx - s * 0.2, iy);
  rock2.quadTo(cx + s * 0.1, iy + s * 0.7, cx - s * 0.1, iy + s * 1.35);
  rock2.quadTo(cx + s * 0.5, iy + s * 0.6, cx + s * 1.05, iy);
  rock2.close();
  cv.drawPath(rock2.detach(), paint("#463C73"));
  // grass top
  cv.drawOval(
    Skia.XYWHRect(cx - s * 1.1, iy - s * 0.32, s * 2.2, s * 0.6),
    paint("#4F9E63"),
  );
  cv.drawOval(
    Skia.XYWHRect(cx - s * 1.0, iy - s * 0.36, s * 2.0, s * 0.5),
    paint("#62B873"),
  );
  // pines
  for (let k = 0; k < 3; k++) {
    const px = cx + s * (0.45 + k * 0.22);
    const py = iy - s * 0.1 - k * s * 0.03;
    const sz = s * (0.34 - k * 0.06);
    const tr = Skia.PathBuilder.Make();
    tr.moveTo(px, py - sz * 1.8);
    tr.lineTo(px + sz * 0.5, py);
    tr.lineTo(px - sz * 0.5, py);
    tr.close();
    cv.drawPath(tr.detach(), paint(k % 2 ? "#2E6B55" : "#367A5F"));
  }
  // cabin with warm windows
  const hx = cx - s * 0.35;
  const hy = iy - s * 0.12;
  cv.drawRect(
    Skia.XYWHRect(hx - s * 0.32, hy - s * 0.42, s * 0.64, s * 0.42),
    paint("#8A6A8F"),
  );
  const roof = Skia.PathBuilder.Make();
  roof.moveTo(hx - s * 0.42, hy - s * 0.4);
  roof.lineTo(hx, hy - s * 0.78);
  roof.lineTo(hx + s * 0.42, hy - s * 0.4);
  roof.close();
  cv.drawPath(roof.detach(), paint("#E2667A"));
  const glow = 0.35 + 0.65 * calm;
  const wg = paint("#FFD36B", 0.35 * glow);
  wg.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 10, true));
  cv.drawCircle(hx, hy - s * 0.2, s * 0.32, wg);
  cv.drawRect(
    Skia.XYWHRect(hx - s * 0.2, hy - s * 0.3, s * 0.14, s * 0.14),
    paint("#FFD36B", glow),
  );
  cv.drawRect(
    Skia.XYWHRect(hx + s * 0.06, hy - s * 0.3, s * 0.14, s * 0.14),
    paint("#FFD36B", glow),
  );
  // fireflies with longer sleep
  const ff = Math.floor(calm * 7);
  for (let i = 0; i < ff; i++) {
    const ph = (o.t * 2 * Math.PI) / LOOP + i * 1.7;
    const fx = cx + Math.cos(ph * (i % 2 ? 1 : -1) + i) * s * (0.8 + 0.1 * i);
    const fy = iy - s * 0.6 + Math.sin(ph * 2 + i) * s * 0.25 - i * 3;
    const fg = paint("#FFF2A6", 0.8);
    fg.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 4, true));
    cv.drawCircle(fx, fy, reduced ? 2 : 3, fg);
  }
  // the moon on the ring
  const mx = cx + Math.cos(a1) * R;
  const my = cy + Math.sin(a1) * R;
  const halo = paint("#FFF4C9", 0.25 + 0.2 * o.drag);
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 14, true));
  cv.drawCircle(mx, my, 26 + 6 * o.drag, halo);
  cv.drawCircle(mx, my, 20 + 3 * o.drag, paint("#FFF1BF"));
  cv.drawCircle(mx + 8, my - 5, 17 + 3 * o.drag, paint("#2A3470"));
  // clouds roll in when the alarm is set
  if (o.cloud > 0) {
    // one layer so overlapping puffs read as one soft cloud bank
    const layer = Skia.Paint();
    layer.setAlphaf(0.95 * o.cloud);
    cv.saveLayer(layer);
    for (let k = 0; k < 6; k++) {
      const side = k % 2 ? 1 : -1;
      const x = cx + side * (W * 0.75 - o.cloud * W * (0.45 + 0.08 * (k >> 1)));
      const y = cy + s * (0.2 + 0.35 * (k >> 1)) - s * 0.3;
      const cp = paint("#E9ECFF");
      cv.drawCircle(x, y, s * 0.5, cp);
      cv.drawCircle(x + s * 0.45, y + s * 0.1, s * 0.38, cp);
      cv.drawCircle(x - s * 0.45, y + s * 0.12, s * 0.36, cp);
    }
    cv.restore();
  }
};

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

type Props = { autoPlay?: boolean };

export function IslandSleepDial({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "ISD-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "ISD-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "ISD-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const g: Geo = {
    cx: W / 2,
    cy: Math.round(H * 0.44),
    R: Math.round(W * 0.4),
    s: Math.round(W * 0.2),
    W,
    H,
  };

  const st = useSharedValue({
    h: START_H,
    drag: 0,
    dragK: 0,
    a0: 0,
    h0: START_H,
    cloud: 0,
    cloudT: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    lastStep: START_H * 4,
    setAt: -10,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ h: START_H, cloud: 0, t: 0, drag: 0 });
  const [asleep, setAsleep] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __islandSleep?: unknown };
    gl.__islandSleep = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__islandSleep;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
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
    s.dragK += (s.drag - s.dragK) * (1 - Math.exp(-12 * dt));
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({ h: sc.h, cloud: sc.cloud, t: dbg, drag: 0 });
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (Math.abs(s.h - START_H) < 0.01 && s.cloud < 0.001))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.h = sc.h;
      s.cloud = sc.cloud;
      out.set({ h: s.h, cloud: s.cloud, t: now, drag: s.dragK });
      return;
    }
    s.scripted = 0;
    // a set alarm closes the night for 1.6 s, then opens it again
    const u = now - s.setAt;
    s.cloud =
      u >= 0 && u < 2.0
        ? smoother(u / 0.8) * (1 - smoother((u - 1.4) / 0.6))
        : 0;
    const step = Math.round(s.h * 4);
    if (step !== s.lastStep) {
      s.lastStep = step;
      scheduleOnRN(buzz, 0);
    }
    out.set({ h: s.h, cloud: s.cloud, t: now, drag: s.dragK });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `h=${s.h.toFixed(2)} cloud=${s.cloud.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().cloud > 0.5,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setAsleep, v);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart((e) => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.a0 = Math.atan2(e.absoluteY - g.cy, e.absoluteX - g.cx);
      s.h0 = s.h;
    })
    .onUpdate((e) => {
      const s = st.get();
      const a = Math.atan2(e.absoluteY - g.cy, e.absoluteX - g.cx);
      let d = a - s.a0;
      if (d > Math.PI) d -= Math.PI * 2;
      if (d < -Math.PI) d += Math.PI * 2;
      s.a0 = a;
      s.h0 = Math.max(
        MIN_H - 0.3,
        Math.min(MAX_H + 0.3, s.h0 + (d / (Math.PI * 2)) * 12),
      );
      // snap the shown value to quarter hours, with a little rubber band past the ends
      const q = Math.round(s.h0 * 4) / 4;
      s.h = Math.max(MIN_H, Math.min(MAX_H, q));
      s.lastTouch = clock.get();
    })
    .onFinalize(() => {
      const s = st.get();
      s.drag = 0;
      s.lastTouch = clock.get();
    });

  const setAlarm = () => {
    "worklet";
    const s = st.get();
    s.setAt = clock.get();
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const onSet = () => {
    buzz(1);
    scheduleOnUI(setAlarm);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawNight(cv, g, out.get(), reduced)),
  );
  const dur = useAnimatedProps(() => {
    const v = fmtH(out.get().h);
    return { text: v, defaultValue: v } as never;
  });
  const wake = useAnimatedProps(() => {
    const v = `Bedtime 11:00 PM  ·  Wake ${fmtWake(out.get().h)}`;
    return { text: v, defaultValue: v } as never;
  });
  const sleepWell = useAnimatedStyle(() => ({
    opacity: out.get().cloud,
    transform: [{ translateY: (1 - out.get().cloud) * 10 }],
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.head, { top: insets.top + 12 }]}>
        <Text style={styles.small}>Tonight</Text>
        <AnimatedInput
          editable={false}
          style={styles.big}
          animatedProps={dur}
          accessibilityLabel="Sleep length"
        />
        <AnimatedInput
          editable={false}
          style={styles.sub}
          animatedProps={wake}
        />
      </View>
      <GestureDetector gesture={pan}>
        <View
          style={[
            styles.dialHit,
            { top: g.cy - g.R - 40, height: g.R * 2 + 80 },
          ]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Sleep length dial. Drag the moon around the island"
        />
      </GestureDetector>
      <Animated.Text
        style={[styles.sleepWell, { top: g.cy - 20 }, sleepWell]}
        pointerEvents="none"
      >
        Sleep well
      </Animated.Text>
      <View style={[styles.bottom, { bottom: insets.bottom + 18 }]}>
        <View style={styles.chip}>
          <Ionicons name="musical-notes-outline" size={16} color="#C9D2FF" />
          <Text style={styles.chipText}>Rain on a tin roof</Text>
        </View>
        <Pressable
          onPress={onSet}
          accessibilityRole="button"
          accessibilityLabel={asleep ? "Alarm set" : "Set alarm"}
          style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
        >
          <Ionicons name="alarm-outline" size={20} color="#141A3C" />
          <Text style={styles.ctaText}>
            {asleep ? "Alarm set" : "Set alarm"}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#141A3C" },
  head: { position: "absolute", left: 24, right: 24, alignItems: "center" },
  small: {
    fontFamily: "ISD-SemiBold",
    fontSize: 14,
    color: "#9FA9E0",
    letterSpacing: 0.5,
  },
  big: {
    fontFamily: "ISD-Bold",
    fontSize: 44,
    color: "#FFFFFF",
    padding: 0,
    textAlign: "center",
    minWidth: 220,
    fontVariant: ["tabular-nums"],
  },
  sub: {
    fontFamily: "ISD-Regular",
    fontSize: 14,
    color: "#C9D2FF",
    padding: 0,
    textAlign: "center",
    minWidth: 300,
  },
  dialHit: { position: "absolute", left: 0, right: 0 },
  sleepWell: {
    position: "absolute",
    left: 0,
    right: 0,
    textAlign: "center",
    fontFamily: "ISD-Bold",
    fontSize: 28,
    color: "#2A3470",
  },
  bottom: { position: "absolute", left: 20, right: 20, gap: 12 },
  chip: {
    alignSelf: "center",
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    height: 34,
    paddingHorizontal: 14,
    borderRadius: 17,
    backgroundColor: "rgba(255,255,255,0.1)",
  },
  chipText: { fontFamily: "ISD-SemiBold", fontSize: 13, color: "#C9D2FF" },
  cta: {
    height: 56,
    borderRadius: 28,
    backgroundColor: "#FFF1BF",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  ctaText: { fontFamily: "ISD-SemiBold", fontSize: 17, color: "#141A3C" },
  pressed: { transform: [{ scale: 0.96 }] },
});
