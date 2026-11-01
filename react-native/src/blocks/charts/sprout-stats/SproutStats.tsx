import {
  Canvas,
  createPicture,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  useFont,
  type SkCanvas,
  type SkFont,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import {
  Pressable,
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
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

/*
 Sprout Stats — the weekly stats of an invented habit app, Sprout, for mindful minutes.

 Look (Pinterest habit-garden apps): every day of the week is a potted plant whose height is how long
 you practised, swaying a little on a windowsill.

 Ours: the garden is a chart in disguise. Switch to Chart (or drag up on the garden) and every plant
 becomes its bar: the stem straightens and thickens into the bar, the leaves fold away, the flower
 shrinks into the bar's rounded cap and the pot sinks into the axis, so the eye never loses which plant
 was which day. Drag sideways to read a day; the scrubbed day lifts and its minutes show. Drag down (or
 Garden) and the bars sprout again.

 autoPlay: LOOP = 9 s — garden sways 0–1.4, becomes a chart 1.4–2.6, scrubs Monday → Sunday 3.0–5.4,
 goes back to a garden 6.0–7.2, sways to 9.0 (frame 9 = frame 0). Reduced motion: no sway; the switch
 crossfades.
*/

let debugState = "";

const LOOP = 9;
const DAYS = ["M", "T", "W", "T", "F", "S", "S"];
const MINUTES = [12, 20, 8, 25, 18, 30, 15];
const COLORS = [
  "#FF9AA2",
  "#FFB86B",
  "#FFD86B",
  "#9BE08B",
  "#7FD3E8",
  "#A7A8FF",
  "#E7A6FF",
];
const MAXV = 30;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};
const lerp = (a: number, b: number, k: number) => {
  "worklet";
  return a + (b - a) * k;
};
const mixHex = (a: string, b: string, k: number) => {
  "worklet";
  const r = Math.round(
    lerp(parseInt(a.slice(1, 3), 16), parseInt(b.slice(1, 3), 16), k),
  );
  const g = Math.round(
    lerp(parseInt(a.slice(3, 5), 16), parseInt(b.slice(3, 5), 16), k),
  );
  const bb = Math.round(
    lerp(parseInt(a.slice(5, 7), 16), parseInt(b.slice(5, 7), 16), k),
  );
  return `rgb(${r},${g},${bb})`;
};
const scriptAt = (t: number) => {
  "worklet";
  let m = 0;
  if (t >= 1.4 && t < 2.6) m = smoother((t - 1.4) / 1.2);
  else if (t >= 2.6 && t < 6.0) m = 1;
  else if (t >= 6.0 && t < 7.2) m = 1 - smoother((t - 6.0) / 1.2);
  const scrub = t >= 3.0 && t < 5.4 ? ((t - 3.0) / 2.4) * 6 : -1;
  return { m, scrub };
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

type Geo = { x0: number; step: number; base: number; hmax: number };
type Out = { m: number; scrub: number; t: number };

const drawGarden = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  font: SkFont | null,
  reduced: boolean,
) => {
  "worklet";
  const m = o.m;
  // axis grows out of the windowsill
  cv.drawLine(
    g.x0 - 30,
    g.base,
    g.x0 + g.step * 6 + 30,
    g.base,
    paint("#E7DED0", 1, lerp(10, 2, m)),
  );
  for (let k = 1; k <= 3; k++) {
    const y = g.base - (g.hmax * k) / 3;
    cv.drawLine(
      g.x0 - 30,
      y,
      g.x0 + g.step * 6 + 30,
      y,
      paint("#E7DED0", m * 0.8, 1),
    );
  }
  for (let i = 0; i < 7; i++) {
    const x = g.x0 + g.step * i;
    const h = (MINUTES[i] / MAXV) * g.hmax;
    const sel = o.scrub >= 0 ? clamp01(1 - Math.abs(o.scrub - i)) : 0;
    const lift = sel * 6 * m;
    const potH = 26 * (1 - m);
    const sway = reduced
      ? 0
      : Math.sin((o.t * 2 * Math.PI * (i % 2 ? 1 : 2)) / LOOP + i * 1.3) *
        7 *
        (1 - m);
    const topY = g.base - h - lift;
    const startY = lerp(g.base - potH, g.base - 13, m);
    // stem → bar: a thick round-capped curve that straightens and widens
    const width = lerp(5, 26, m);
    const stem = Skia.PathBuilder.Make();
    stem.moveTo(x, startY);
    const bend = (1 - m) * (i % 2 ? 10 : -10);
    stem.cubicTo(
      x + bend,
      lerp(startY, topY, 0.35),
      x - bend + sway * 0.5,
      lerp(startY, topY, 0.7),
      x + sway,
      topY + width / 2,
    );
    const col = mixHex("#5DAE5B", COLORS[i], smoother((m - 0.15) / 0.55));
    const sp = paint(col, 1, width);
    cv.drawPath(stem.detach(), sp);
    // leaves fold away
    const lk = 1 - smoother(m * 1.6);
    if (lk > 0.01) {
      for (let k = 0; k < 2; k++) {
        const ly = lerp(startY, topY, 0.35 + k * 0.25);
        const lx = x + sway * (0.35 + k * 0.25);
        const dir = k % 2 ? 1 : -1;
        cv.save();
        cv.translate(lx, ly);
        cv.rotate(dir * -35 - sway, 0, 0);
        cv.scale(lk, lk);
        cv.drawOval(
          Skia.XYWHRect(dir > 0 ? 0 : -18, -5, 18, 10),
          paint("#6CC46A"),
        );
        cv.restore();
      }
    }
    // flower → cap
    const fx = x + sway;
    const fy = topY + width / 2;
    const pk = 1 - m;
    if (pk > 0.01) {
      for (let k = 0; k < 6; k++) {
        const a =
          (k / 6) * Math.PI * 2 + o.t * (Math.PI / LOOP) * (reduced ? 0 : 1);
        cv.drawCircle(
          fx + Math.cos(a) * 9 * pk,
          fy - 6 * pk + Math.sin(a) * 9 * pk,
          6.5 * pk,
          paint(COLORS[i]),
        );
      }
      cv.drawCircle(fx, fy - 6 * pk, 5 * pk, paint("#FFE07A"));
    }
    // pot sinks into the axis
    if (potH > 0.5) {
      const pot = Skia.PathBuilder.Make();
      pot.moveTo(x - 17, g.base - potH);
      pot.lineTo(x + 17, g.base - potH);
      pot.lineTo(x + 13, g.base);
      pot.lineTo(x - 13, g.base);
      pot.close();
      cv.drawPath(pot.detach(), paint("#D9825B"));
      cv.drawRRect(
        Skia.RRectXY(
          Skia.XYWHRect(x - 19, g.base - potH - 2, 38, Math.min(8, potH)),
          3,
          3,
        ),
        paint("#E89A73"),
      );
    }
    if (font) {
      const lab = DAYS[i];
      const w = font.measureText(lab).width;
      cv.drawText(
        lab,
        x - w / 2,
        g.base + 24,
        paint(sel > 0.5 ? "#2A2633" : "#9A93A6"),
        font,
      );
      const vk = clamp01((m - 0.6) / 0.4);
      if (vk > 0) {
        const v = `${MINUTES[i]}`;
        const vw = font.measureText(v).width;
        cv.drawText(
          v,
          x - vw / 2,
          topY - 8,
          paint("#2A2633", vk * (0.55 + 0.45 * sel)),
          font,
        );
      }
    }
  }
};

type Props = { autoPlay?: boolean };

export function SproutStats({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "SS-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "SS-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });
  const font = useFont(require("./assets/fonts/Nunito-ExtraBold.ttf"), 14);

  const g: Geo = {
    x0: 46,
    step: (W - 92) / 6,
    base: Math.round(H * 0.7),
    hmax: Math.round(H * 0.34),
  };

  const st = useSharedValue({
    m: 0,
    v: 0,
    target: 0,
    drag: 0,
    m0: 0,
    scrub: -1,
    mode: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    lastDay: -1,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ m: 0, scrub: -1, t: 0 });
  const [day, setDay] = useState(-1);
  const [chart, setChart] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __sprout?: unknown };
    gl.__sprout = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__sprout;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const tick = () => {
    if (process.env.EXPO_OS === "web") return;
    Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((info) => {
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const s = st.get();
    const dbg = debugT.get();
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({ m: sc.m, scrub: sc.scrub, t: dbg });
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.m < 0.001 && s.target === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.m = sc.m;
      s.target = sc.m > 0.5 ? 1 : 0;
      s.scrub = sc.scrub;
      out.set({ m: s.m, scrub: s.scrub, t: now });
      return;
    }
    s.scripted = 0;
    if (s.drag === 0 || s.mode === 1) {
      const k = reduced ? 600 : 110;
      const c = 2 * 0.85 * Math.sqrt(k);
      s.v += (k * (s.target - s.m) - c * s.v) * dt;
      s.m += s.v * dt;
    }
    const d = s.scrub >= 0 ? Math.round(s.scrub) : -1;
    if (d !== s.lastDay) {
      s.lastDay = d;
      if (d >= 0) scheduleOnRN(tick);
    }
    out.set({ m: s.m, scrub: s.scrub, t: now });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `m=${s.m.toFixed(2)} scrub=${s.scrub.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => (out.get().scrub >= 0 ? Math.round(out.get().scrub) : -1),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setDay, v);
    },
  );
  useAnimatedReaction(
    () => out.get().m > 0.5,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setChart, v);
    },
  );

  const pan = Gesture.Pan()
    .onStart((e) => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.mode = Math.abs(e.translationY) > Math.abs(e.translationX) ? 0 : 1;
      s.m0 = s.m;
      s.v = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      if (s.mode === 0) s.m = clamp01(s.m0 - e.translationY / 180);
      else s.scrub = Math.max(0, Math.min(6, (e.x - (g.x0 - 16)) / g.step));
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      if (s.mode === 0) {
        s.target =
          e.velocityY < -300 ? 1 : e.velocityY > 300 ? 0 : s.m > 0.5 ? 1 : 0;
        s.v = -e.velocityY / 180;
      } else s.scrub = -1;
      s.lastTouch = clock.get();
    });

  const setMode = (c: number) => {
    "worklet";
    const s = st.get();
    s.target = c;
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const onMode = (c: number) => {
    tick();
    scheduleOnUI(setMode, c);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawGarden(cv, g, out.get(), font, reduced)),
  );
  const thumb = useAnimatedStyle(() => ({
    transform: [{ translateX: clamp01(out.get().m) * 104 }],
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.head, { top: insets.top + 10 }]}>
        <Text style={styles.kicker}>THIS WEEK</Text>
        <Text style={styles.title}>Mindful minutes</Text>
        <Text style={styles.summary} accessibilityLiveRegion="polite">
          {day >= 0
            ? `${["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][day]} · ${MINUTES[day]} min`
            : "128 min · 18% more than last week"}
        </Text>
        <View style={styles.segment} accessibilityRole="tablist">
          <Animated.View style={[styles.segThumb, thumb]} />
          {["Garden", "Chart"].map((l, i) => (
            <Pressable
              key={l}
              onPress={() => onMode(i)}
              style={styles.segItem}
              accessibilityRole="tab"
              accessibilityState={{ selected: (i === 1) === chart }}
            >
              <Text
                style={[styles.segText, (i === 1) === chart && styles.segOn]}
              >
                {l}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={pan}>
        <View
          style={[
            styles.hit,
            { top: g.base - g.hmax - 60, height: g.hmax + 100 },
          ]}
          accessible
          accessibilityLabel={`Mindful minutes this week: ${MINUTES.map((v, i) => `${["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][i]} ${v}`).join(", ")}`}
          accessibilityHint="Drag up for the chart, sideways to read a day"
        />
      </GestureDetector>
      <View style={[styles.tips, { bottom: insets.bottom + 20 }]}>
        <View style={styles.tip}>
          <Text style={styles.tipNum}>5</Text>
          <Text style={styles.tipText}>day streak</Text>
        </View>
        <View style={styles.tip}>
          <Text style={styles.tipNum}>30</Text>
          <Text style={styles.tipText}>best day · Sat</Text>
        </View>
        <View style={styles.tip}>
          <Text style={styles.tipNum}>18</Text>
          <Text style={styles.tipText}>daily average</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FCF8F1" },
  head: { position: "absolute", left: 24, right: 24 },
  kicker: {
    fontFamily: "SS-Bold",
    fontSize: 12,
    letterSpacing: 1.3,
    color: "#B09A7A",
  },
  title: {
    fontFamily: "SS-Black",
    fontSize: 28,
    color: "#2A2633",
    marginTop: 2,
  },
  summary: {
    fontFamily: "SS-Bold",
    fontSize: 15,
    color: "#7D7488",
    marginTop: 4,
  },
  segment: {
    flexDirection: "row",
    width: 212,
    height: 40,
    borderRadius: 20,
    backgroundColor: "#F0E8DC",
    marginTop: 16,
    padding: 4,
  },
  segThumb: {
    position: "absolute",
    top: 4,
    left: 4,
    width: 104,
    height: 32,
    borderRadius: 16,
    backgroundColor: "#FFFFFF",
  },
  segItem: { flex: 1, alignItems: "center", justifyContent: "center" },
  segText: { fontFamily: "SS-Bold", fontSize: 14, color: "#9A93A6" },
  segOn: { color: "#2A2633" },
  hit: { position: "absolute", left: 0, right: 0 },
  tips: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    gap: 10,
  },
  tip: {
    flex: 1,
    height: 74,
    borderRadius: 20,
    backgroundColor: "#FFFFFF",
    padding: 12,
    justifyContent: "center",
  },
  tipNum: { fontFamily: "SS-Black", fontSize: 22, color: "#2A2633" },
  tipText: { fontFamily: "SS-Bold", fontSize: 12, color: "#9A93A6" },
});
