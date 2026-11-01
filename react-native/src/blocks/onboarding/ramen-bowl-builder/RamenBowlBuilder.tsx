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
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

/*
 Ramen Bowl Builder — the "build your bowl" step of an invented meal-kit app, Slurp.

 Look (a Pinterest food animation: a red polka-dot ramen bowl with chopsticks lifting noodles on a
 peach background, while an egg, fish cake, sushi rolls, nori, shrimp and a rice ball fly around it).

 Ours: the flying ingredients are the toppings, and you build the bowl by hand. Each one floats on its
 own orbit, bobbing and turning; drag one and it follows your finger, drop it on the bowl and it dives
 in with a splash of broth and settles on the noodles (with a haptic), drop it anywhere else and it
 springs back to its orbit. The topping counter fills and the steam thickens as the bowl gets fuller.

 autoPlay: LOOP = 9 s — the five toppings dive in at 1.0, 2.1, 3.2, 4.3 and 5.4 s (0.7 s each), the bowl
 steams, then they lift out back to their orbits 7.6–8.8 (frame 9 = frame 0). Reduced motion: no
 floating; dropped toppings appear in the bowl.
*/

let debugState = "";

const LOOP = 9;
const N = 5;
const NAMES = ["Soft egg", "Fish cake", "Nori", "Shrimp", "Sushi roll"];

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
/** How far topping i is into the bowl in the script (0 floating … 1 settled). */
const scriptK = (t: number, i: number) => {
  "worklet";
  const at = 1.0 + i * 1.1;
  if (t < at) return 0;
  if (t < 7.6) return smoother((t - at) / 0.7);
  return 1 - smoother((t - 7.6 - (N - 1 - i) * 0.12) / 0.7);
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

const drawTopping = (cv: SkCanvas, kind: number, u: number) => {
  "worklet";
  if (kind === 0) {
    cv.drawOval(
      Skia.XYWHRect(-u * 0.55, -u * 0.42, u * 1.1, u * 0.84),
      paint("#FFFFFF"),
    );
    cv.drawCircle(0, 0, u * 0.26, paint("#FFAA1F"));
    cv.drawCircle(-u * 0.06, -u * 0.06, u * 0.08, paint("#FFD66B"));
  } else if (kind === 1) {
    cv.drawCircle(0, 0, u * 0.45, paint("#FFFFFF"));
    const sw = Skia.PathBuilder.Make();
    for (let k = 0; k < 40; k++) {
      const a = k * 0.42;
      const r = u * 0.04 + k * u * 0.0085;
      if (k === 0) sw.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else sw.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    cv.drawPath(sw.detach(), paint("#FF7A9C", 1, u * 0.07));
    for (let k = 0; k < 12; k++) {
      const a = (k / 12) * Math.PI * 2;
      cv.drawCircle(
        Math.cos(a) * u * 0.45,
        Math.sin(a) * u * 0.45,
        u * 0.06,
        paint("#FFFFFF"),
      );
    }
  } else if (kind === 2) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.32, -u * 0.55, u * 0.64, u * 1.1),
        u * 0.05,
        u * 0.05,
      ),
      paint("#1F3B2E"),
    );
    cv.drawLine(
      -u * 0.2,
      -u * 0.4,
      -u * 0.2,
      u * 0.4,
      paint("#2F5442", 1, 1.5),
    );
    cv.drawLine(u * 0.1, -u * 0.4, u * 0.1, u * 0.4, paint("#2F5442", 1, 1.5));
  } else if (kind === 3) {
    const sh = Skia.PathBuilder.Make();
    sh.addArc(Skia.XYWHRect(-u * 0.45, -u * 0.45, u * 0.9, u * 0.9), 200, 250);
    cv.drawPath(sh.detach(), paint("#FF7E4A", 1, u * 0.3));
    for (let k = 0; k < 4; k++) {
      const a = ((200 + k * 60) * Math.PI) / 180;
      cv.drawLine(
        Math.cos(a) * u * 0.3,
        Math.sin(a) * u * 0.3,
        Math.cos(a) * u * 0.6,
        Math.sin(a) * u * 0.6,
        paint("#FFFFFF", 0.7, 2),
      );
    }
  } else {
    cv.drawCircle(0, 0, u * 0.45, paint("#1F3B2E"));
    cv.drawCircle(0, 0, u * 0.36, paint("#FFFFFF"));
    cv.drawCircle(0, 0, u * 0.14, paint("#FF8A4C"));
    cv.drawCircle(u * 0.1, -u * 0.1, u * 0.07, paint("#7CC46A"));
  }
};

type Geo = {
  cx: number;
  cy: number;
  s: number;
  homes: number[][];
  slots: number[][];
};
type Out = {
  k: number[];
  dragI: number;
  dx: number;
  dy: number;
  t: number;
  back: number[];
  bx: number[];
  by: number[];
};

const drawScene = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const { cx, cy, s } = g;
  let full = 0;
  for (let i = 0; i < N; i++) full += o.k[i];
  full /= N;
  // steam
  for (let k = 0; k < 3; k++) {
    const ph = (o.t * 2 * Math.PI) / LOOP + k * 2.1;
    const sp = Skia.PathBuilder.Make();
    const x0 = cx + (k - 1) * s * 0.35;
    sp.moveTo(x0, cy - s * 0.25);
    sp.cubicTo(
      x0 + Math.sin(ph) * 14,
      cy - s * 0.55,
      x0 - Math.sin(ph) * 14,
      cy - s * 0.8,
      x0 + Math.sin(ph + 1) * 10,
      cy - s * 1.05,
    );
    cv.drawPath(sp.detach(), paint("#FFFFFF", 0.25 + 0.45 * full, 7));
  }
  // chopsticks lifting noodles
  const lift = reduced ? 0 : Math.sin((o.t * 2 * Math.PI * 2) / LOOP) * 6;
  cv.drawLine(
    cx - s * 0.1,
    cy - s * 0.7 + lift,
    cx + s * 1.1,
    cy - s * 1.15 + lift,
    paint("#C2533C", 1, 7),
  );
  cv.drawLine(
    cx - s * 0.05,
    cy - s * 0.58 + lift,
    cx + s * 1.15,
    cy - s * 1.0 + lift,
    paint("#D8644A", 1, 7),
  );
  for (let k = 0; k < 4; k++) {
    const nx = cx + s * 0.05 + k * 7;
    cv.drawLine(
      nx,
      cy - s * 0.62 + lift,
      nx + Math.sin(o.t * ((8 * Math.PI) / LOOP) + k) * 3,
      cy - s * 0.1,
      paint("#FFD878", 1, 5),
    );
  }
  // broth and noodles
  cv.drawOval(
    Skia.XYWHRect(cx - s * 1.0, cy - s * 0.3, s * 2.0, s * 0.6),
    paint("#E8914A"),
  );
  cv.drawOval(
    Skia.XYWHRect(cx - s * 0.92, cy - s * 0.24, s * 1.84, s * 0.48),
    paint("#F6B460"),
  );
  for (let k = 0; k < 6; k++) {
    const ny = cy - s * 0.12 + (k % 3) * s * 0.1;
    const nw = Skia.PathBuilder.Make();
    nw.moveTo(cx - s * 0.7, ny);
    for (let q = 1; q <= 8; q++)
      nw.lineTo(cx - s * 0.7 + q * s * 0.18, ny + Math.sin(q * 1.7 + k) * 5);
    cv.drawPath(nw.detach(), paint("#FFE08A", 1, 4));
  }
  // toppings already in (drawn on the noodles), splashes where they landed
  for (let i = 0; i < N; i++) {
    const k = o.k[i];
    if (k <= 0.999 || i === o.dragI) continue;
    cv.save();
    cv.translate(g.slots[i][0], g.slots[i][1]);
    cv.scale(1, 0.62);
    drawTopping(cv, i, s * 0.56);
    cv.restore();
  }
  // bowl body with polka dots
  const body = Skia.PathBuilder.Make();
  body.moveTo(cx - s * 1.08, cy);
  body.cubicTo(
    cx - s * 1.0,
    cy + s * 0.95,
    cx + s * 1.0,
    cy + s * 0.95,
    cx + s * 1.08,
    cy,
  );
  body.close();
  const bodyPath = body.detach();
  cv.drawPath(bodyPath, paint("#E8443A"));
  cv.save();
  cv.clipPath(bodyPath, 1, true);
  for (let k = 0; k < 14; k++) {
    const dx = cx - s * 0.95 + (k % 7) * s * 0.32 + (k > 6 ? s * 0.16 : 0);
    const dy = cy + s * 0.22 + (k > 6 ? s * 0.28 : 0);
    cv.drawCircle(dx, dy, s * 0.07, paint("#FFFFFF"));
  }
  cv.restore();
  cv.drawOval(
    Skia.XYWHRect(cx - s * 1.1, cy - s * 0.08, s * 2.2, s * 0.16),
    paint("#C7342C"),
  );
  cv.drawOval(
    Skia.XYWHRect(cx - s * 0.55, cy + s * 0.68, s * 1.1, s * 0.16),
    paint("#B02C25"),
  );
  // toppings in the air: floating home, diving to the bowl, or under the finger
  for (let i = 0; i < N; i++) {
    const k = o.k[i];
    if (k > 0.999 && i !== o.dragI) continue;
    const h = g.homes[i];
    const bob = reduced
      ? 0
      : Math.sin((o.t * 2 * Math.PI * (1 + (i % 2))) / LOOP + i * 1.4) * 8;
    let x = h[0];
    let y = h[1] + bob;
    let rot = reduced ? 0 : Math.sin((o.t * 2 * Math.PI) / LOOP + i) * 18;
    let sc = 1;
    if (i === o.dragI) {
      x = o.dx;
      y = o.dy;
      sc = 1.12;
    } else if (o.back[i] > 0) {
      x = lerp(o.bx[i], h[0], smoother(o.back[i]));
      y = lerp(o.by[i], h[1] + bob, smoother(o.back[i]));
    }
    if (k > 0 && i !== o.dragI) {
      const sx = o.back[i] < 0 ? o.bx[i] : x;
      const sy = o.back[i] < 0 ? o.by[i] : y;
      x = lerp(sx, g.slots[i][0], k);
      y = lerp(sy, g.slots[i][1], k) - Math.sin(Math.PI * k) * s * 0.6;
      rot *= 1 - k;
      sc = lerp(1, 0.9, k);
    }
    cv.save();
    cv.translate(x, y);
    cv.rotate(rot, 0, 0);
    cv.scale(sc, sc);
    drawTopping(cv, i, s * 0.56);
    cv.restore();
    // splash droplets as it lands
    if (k > 0.8 && k < 1) {
      const sk = (k - 0.8) / 0.2;
      for (let d = 0; d < 6; d++) {
        const a = Math.PI + (d / 5) * Math.PI;
        cv.drawCircle(
          g.slots[i][0] + Math.cos(a) * s * 0.3 * sk,
          g.slots[i][1] + Math.sin(a) * s * 0.25 * sk,
          4 * (1 - sk),
          paint("#F6B460"),
        );
      }
    }
  }
};

type Props = { autoPlay?: boolean };

export function RamenBowlBuilder({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "RBB-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "RBB-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "RBB-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });

  const cx = W / 2;
  const cy = Math.round(H * 0.6);
  const s = Math.round(W * 0.29);
  const g: Geo = {
    cx,
    cy,
    s,
    homes: [
      [cx - s * 1.15, cy - s * 1.45],
      [cx + s * 0.25, cy - s * 1.85],
      [cx - s * 1.25, cy - s * 0.55],
      [cx + s * 1.25, cy - s * 0.5],
      [cx - s * 0.45, cy - s * 2.15],
    ],
    slots: [
      [cx - s * 0.45, cy - s * 0.12],
      [cx + s * 0.12, cy - s * 0.18],
      [cx - s * 0.08, cy - s * 0.02],
      [cx + s * 0.55, cy - s * 0.05],
      [cx + s * 0.32, cy + s * 0.04],
    ],
  };

  const st = useSharedValue({
    k: [0, 0, 0, 0, 0],
    placed: [0, 0, 0, 0, 0],
    back: [0, 0, 0, 0, 0],
    bx: [0, 0, 0, 0, 0],
    by: [0, 0, 0, 0, 0],
    dragI: -1,
    dx: 0,
    dy: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    k: [0, 0, 0, 0, 0],
    dragI: -1,
    dx: 0,
    dy: 0,
    t: 0,
    back: [0, 0, 0, 0, 0],
    bx: [0, 0, 0, 0, 0],
    by: [0, 0, 0, 0, 0],
  });
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __ramen?: unknown };
    gl.__ramen = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__ramen;
    };
  }, [debugT]);
  const mirror = (m: string) => {
    debugState = m;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
    else
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  useFrameCallback((info) => {
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const m = st.get();
    const dbg = debugT.get();
    if (dbg >= 0) {
      const t = dbg % LOOP;
      out.set({
        k: [
          scriptK(t, 0),
          scriptK(t, 1),
          scriptK(t, 2),
          scriptK(t, 3),
          scriptK(t, 4),
        ],
        dragI: -1,
        dx: 0,
        dy: 0,
        t,
        back: [0, 0, 0, 0, 0],
        bx: [0, 0, 0, 0, 0],
        by: [0, 0, 0, 0, 0],
      });
      return;
    }
    let none = 1;
    for (let i = 0; i < N; i++) if (m.placed[i] > 0 || m.k[i] > 0) none = 0;
    if (
      auto &&
      m.dragI < 0 &&
      now - m.lastTouch > 3.5 &&
      (m.scripted === 1 || none === 1)
    ) {
      if (m.scripted === 0) {
        m.scripted = 1;
        m.loopT = 0;
      }
      m.loopT = (m.loopT + wall) % LOOP;
      const t = m.loopT;
      for (let i = 0; i < N; i++) {
        m.k[i] = scriptK(t, i);
        m.placed[i] = m.k[i] > 0.5 ? 1 : 0;
        m.bx[i] = g.homes[i][0];
        m.by[i] = g.homes[i][1];
        m.back[i] = 0;
      }
      out.set({
        k: [m.k[0], m.k[1], m.k[2], m.k[3], m.k[4]],
        dragI: -1,
        dx: 0,
        dy: 0,
        t: now,
        back: [0, 0, 0, 0, 0],
        bx: [m.bx[0], m.bx[1], m.bx[2], m.bx[3], m.bx[4]],
        by: [m.by[0], m.by[1], m.by[2], m.by[3], m.by[4]],
      });
      return;
    }
    m.scripted = 0;
    for (let i = 0; i < N; i++) {
      if (m.placed[i] === 1 && m.k[i] < 1) {
        m.k[i] = Math.min(1, m.k[i] + dt / (reduced ? 0.05 : 0.55));
        if (m.k[i] >= 1) scheduleOnRN(buzz, 1);
      }
      if (m.back[i] > 0 && m.back[i] < 1)
        m.back[i] = Math.min(1, m.back[i] + dt / 0.45);
    }
    out.set({
      k: [m.k[0], m.k[1], m.k[2], m.k[3], m.k[4]],
      dragI: m.dragI,
      dx: m.dx,
      dy: m.dy,
      t: now,
      back: [m.back[0], m.back[1], m.back[2], m.back[3], m.back[4]],
      bx: [m.bx[0], m.bx[1], m.bx[2], m.bx[3], m.bx[4]],
      by: [m.by[0], m.by[1], m.by[2], m.by[3], m.by[4]],
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `placed=${m.placed.join("")} drag=${m.dragI} scripted=${m.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => {
      const k = out.get().k;
      let c = 0;
      for (let i = 0; i < N; i++) if (k[i] > 0.95) c++;
      return c;
    },
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setCount, v);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(0)
    .onStart((e) => {
      const m = st.get();
      m.scripted = 0;
      m.lastTouch = clock.get();
      let best = -1;
      let bd = s * 0.55;
      for (let i = 0; i < N; i++) {
        if (m.placed[i] === 1) continue;
        const d = Math.hypot(
          e.absoluteX - g.homes[i][0],
          e.absoluteY - g.homes[i][1],
        );
        if (d < bd) {
          bd = d;
          best = i;
        }
      }
      m.dragI = best;
      m.dx = e.absoluteX;
      m.dy = e.absoluteY;
      if (best >= 0) scheduleOnRN(buzz, 0);
    })
    .onUpdate((e) => {
      const m = st.get();
      m.dx = e.absoluteX;
      m.dy = e.absoluteY;
      m.lastTouch = clock.get();
    })
    .onFinalize(() => {
      const m = st.get();
      const i = m.dragI;
      m.dragI = -1;
      if (i < 0) return;
      m.bx[i] = m.dx;
      m.by[i] = m.dy;
      const inBowl = Math.hypot(m.dx - g.cx, (m.dy - g.cy) * 1.6) < s * 1.3;
      if (inBowl) {
        m.placed[i] = 1;
        m.k[i] = 0;
        m.back[i] = -1;
      } else {
        m.back[i] = 0.001;
      }
      m.lastTouch = clock.get();
    });

  const reset = () => {
    "worklet";
    const m = st.get();
    for (let i = 0; i < N; i++) {
      if (m.placed[i] === 1) {
        m.placed[i] = 0;
        m.k[i] = 0;
        m.bx[i] = g.slots[i][0];
        m.by[i] = g.slots[i][1];
        m.back[i] = 0.001;
      }
    }
    m.scripted = 0;
    m.lastTouch = clock.get();
  };
  const onNext = () => {
    buzz(0);
    if (count >= N) scheduleOnUI(reset);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawScene(cv, g, out.get(), reduced)),
  );

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.head, { top: insets.top + 10 }]}>
        <Text style={styles.step}>STEP 2 OF 4</Text>
        <Text style={styles.title}>Top your bowl</Text>
        <Text style={styles.sub}>Drag the toppings into the broth</Text>
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={pan}>
        <View
          style={[
            styles.stage,
            { top: insets.top + 120, bottom: insets.bottom + 120 },
          ]}
          accessible
          accessibilityLabel={`Ramen bowl with ${count} of ${N} toppings. Drag a topping into the bowl`}
        />
      </GestureDetector>
      <View style={[styles.card, { bottom: insets.bottom + 18 }]}>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle}>
            Toppings {count}/{N}
          </Text>
          <Text style={styles.cardSub} numberOfLines={1}>
            {count === 0
              ? "Pick five to finish the bowl"
              : NAMES.slice(0, Math.max(1, count)).join(" · ")}
          </Text>
        </View>
        <Pressable
          onPress={onNext}
          accessibilityRole="button"
          accessibilityLabel={
            count >= N ? "Next step" : `${N - count} toppings to go`
          }
          style={({ pressed }) => [
            styles.cta,
            count < N && styles.ctaOff,
            pressed && styles.pressed,
          ]}
        >
          <Text style={styles.ctaText}>
            {count >= N ? "Next" : `${N - count} to go`}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FFC891" },
  head: { position: "absolute", left: 24, right: 24 },
  step: {
    fontFamily: "RBB-Black",
    fontSize: 12,
    letterSpacing: 1.4,
    color: "#B4532B",
  },
  title: {
    fontFamily: "RBB-Title",
    fontSize: 30,
    color: "#3A1A10",
    marginTop: 4,
  },
  sub: { fontFamily: "RBB-Bold", fontSize: 15, color: "#8A4A2E", marginTop: 2 },
  stage: { position: "absolute", left: 0, right: 0 },
  card: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 76,
    borderRadius: 26,
    backgroundColor: "#FFF3E6",
    flexDirection: "row",
    alignItems: "center",
    paddingLeft: 18,
    paddingRight: 10,
    gap: 10,
  },
  cardTitle: { fontFamily: "RBB-Black", fontSize: 17, color: "#3A1A10" },
  cardSub: {
    fontFamily: "RBB-Bold",
    fontSize: 13,
    color: "#9A6A50",
    marginTop: 2,
  },
  cta: {
    height: 56,
    paddingHorizontal: 22,
    borderRadius: 28,
    backgroundColor: "#E8443A",
    justifyContent: "center",
  },
  ctaOff: { backgroundColor: "#F2A090" },
  ctaText: { fontFamily: "RBB-Black", fontSize: 16, color: "#FFFFFF" },
  pressed: { transform: [{ scale: 0.96 }] },
});
