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
 Bundle Pack Box — the bundle page of an invented sports shop, Kitform.

 Look (a Pinterest motion piece of a home-gym kit exploding in mid-air): the kit's pieces — a
 kettlebell, a dumbbell, a rolled mat, a bottle and a band — float apart on a warm studio backdrop.

 Ours: the floating kit is the bundle, and packing it is a pinch. Pinch the pieces together (or press
 Pack bundle) and they fly one after another in an arc into an open carton, each dropping below the
 rim; then the four flaps fold shut in 3D, a strip of tape runs across and a "Ready to ship" stamp
 lands. Pinch out to unpack: the tape snaps, the flaps fling open and the kit bursts back into the air.
 Everything follows the fingers, so you can stop half-packed; let go and it settles to whichever end
 is nearer.

 autoPlay: LOOP = 9 s — floats 0–1.2, packs 1.2–3.4, holds packed (tape and stamp) to 5.6, unpacks
 5.6–7.2, floats to 9.0 (frame 9 = frame 0). Reduced motion: no floating; packing crossfades.
*/

let debugState = "";

const LOOP = 9;

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
const tapeOf = (p: number) => {
  "worklet";
  return smoother((p - 0.93) / 0.07);
};
const scriptP = (t: number) => {
  "worklet";
  if (t < 1.2) return 0;
  if (t < 3.4) return smoother((t - 1.2) / 2.2);
  if (t < 5.6) return 1;
  if (t < 7.2) return 1 - smoother((t - 5.6) / 1.6);
  return 0;
};

const ITEMS = [
  { kind: 0, x: -0.62, y: -0.52, r: -0.3, s: 1.0 }, // kettlebell
  { kind: 1, x: 0.6, y: -0.6, r: 0.5, s: 1.0 }, // dumbbell
  { kind: 2, x: -0.7, y: 0.02, r: 0.2, s: 1.0 }, // mat
  { kind: 3, x: 0.68, y: -0.05, r: -0.25, s: 1.0 }, // bottle
  { kind: 4, x: 0.02, y: -0.62, r: 0.1, s: 1.0 }, // band
];

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

/** A box-space point (x right, y up, z toward the viewer) seen by a three-quarter camera. */
const P = (
  ox: number,
  oy: number,
  k: number,
  x: number,
  y: number,
  z: number,
) => {
  "worklet";
  const yaw = -0.6;
  const pitch = 0.42;
  const a = x * Math.cos(yaw) + z * Math.sin(yaw);
  const c = -x * Math.sin(yaw) + z * Math.cos(yaw);
  const b = y * Math.cos(pitch) - c * Math.sin(pitch);
  return [ox + a * k, oy - b * k];
};
const quad = (cv: SkCanvas, pts: number[][], color: string, a = 1) => {
  "worklet";
  const b = Skia.PathBuilder.Make();
  b.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) b.lineTo(pts[i][0], pts[i][1]);
  b.close();
  cv.drawPath(b.detach(), paint(color, a));
};

const drawItem = (cv: SkCanvas, kind: number, u: number) => {
  "worklet";
  if (kind === 0) {
    cv.drawCircle(0, u * 0.18, u * 0.5, paint("#2F3A4A"));
    cv.drawCircle(-u * 0.15, u * 0.02, u * 0.16, paint("#5A6B82", 0.6));
    cv.drawArc(
      Skia.XYWHRect(-u * 0.32, -u * 0.62, u * 0.64, u * 0.6),
      180,
      180,
      false,
      paint("#2F3A4A", 1, u * 0.14),
    );
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.22, u * 0.05, u * 0.44, u * 0.22),
        u * 0.06,
        u * 0.06,
      ),
      paint("#FF8A3D"),
    );
  } else if (kind === 1) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.5, -u * 0.06, u, u * 0.12),
        u * 0.05,
        u * 0.05,
      ),
      paint("#A9B4C2"),
    );
    for (let k = -1; k <= 1; k += 2) {
      cv.drawRRect(
        Skia.RRectXY(
          Skia.XYWHRect(k * u * 0.42 - u * 0.12, -u * 0.3, u * 0.24, u * 0.6),
          u * 0.08,
          u * 0.08,
        ),
        paint("#3D7BFF"),
      );
      cv.drawRRect(
        Skia.RRectXY(
          Skia.XYWHRect(k * u * 0.42 - u * 0.12, -u * 0.3, u * 0.08, u * 0.6),
          u * 0.04,
          u * 0.04,
        ),
        paint("#7FA9FF", 0.6),
      );
    }
  } else if (kind === 2) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.7, -u * 0.2, u * 1.4, u * 0.4),
        u * 0.2,
        u * 0.2,
      ),
      paint("#7CC97E"),
    );
    cv.drawOval(
      Skia.XYWHRect(u * 0.55, -u * 0.2, u * 0.26, u * 0.4),
      paint("#5CAE60"),
    );
    cv.drawOval(
      Skia.XYWHRect(u * 0.62, -u * 0.1, u * 0.12, u * 0.2),
      paint("#3F8C44"),
    );
    cv.drawLine(
      -u * 0.5,
      -u * 0.2,
      -u * 0.5,
      u * 0.2,
      paint("#FFFFFF", 0.35, 2),
    );
  } else if (kind === 3) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.2, -u * 0.5, u * 0.4, u * 1.0),
        u * 0.14,
        u * 0.14,
      ),
      paint("#FF6B8B"),
    );
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.12, -u * 0.66, u * 0.24, u * 0.2),
        u * 0.06,
        u * 0.06,
      ),
      paint("#2F3A4A"),
    );
    cv.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(-u * 0.2, -u * 0.05, u * 0.4, u * 0.2), 0, 0),
      paint("#FFFFFF", 0.85),
    );
  } else {
    cv.drawOval(
      Skia.XYWHRect(-u * 0.5, -u * 0.3, u, u * 0.6),
      paint("#FFC23D", 1, u * 0.12),
    );
    cv.drawOval(
      Skia.XYWHRect(-u * 0.32, -u * 0.18, u * 0.64, u * 0.36),
      paint("#FFD978", 1, u * 0.08),
    );
  }
};

type Geo = {
  cx: number;
  cy: number;
  k: number;
  u: number;
  spread: number;
  by: number;
};
type Out = { p: number; t: number; tape: number; burst: number };

const drawScene = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const { cx, cy, k } = g;
  const W2 = 0.9;
  const H2 = 0.62;
  const D2 = 0.7;
  const by = g.by; // box floor
  // shadow
  cv.drawOval(
    Skia.XYWHRect(cx - k * 1.35, by - k * 0.22, k * 2.7, k * 0.66),
    paint("#7A4B2A", 0.13),
  );
  // inner back and left walls, visible through the open top
  quad(
    cv,
    [
      P(cx, by, k, -W2, 0, -D2),
      P(cx, by, k, W2, 0, -D2),
      P(cx, by, k, W2, H2 * 2, -D2),
      P(cx, by, k, -W2, H2 * 2, -D2),
    ],
    "#B9834F",
  );
  quad(
    cv,
    [
      P(cx, by, k, -W2, 0, -D2),
      P(cx, by, k, -W2, 0, D2),
      P(cx, by, k, -W2, H2 * 2, D2),
      P(cx, by, k, -W2, H2 * 2, -D2),
    ],
    "#A8733F",
  );
  // items: on their way in, the ones already inside are drawn before the front walls
  const p = o.p;
  for (let pass = 0; pass < 2; pass++) {
    for (let i = 0; i < ITEMS.length; i++) {
      const it = ITEMS[i];
      const ki = reduced ? (p > 0.5 ? 1 : 0) : smoother(p * 1.45 - i * 0.11);
      const inside = ki > 0.78;
      if ((pass === 0) !== inside) continue;
      const bob = reduced
        ? 0
        : Math.sin((o.t * 2 * Math.PI * (1 + (i % 2))) / LOOP + i) *
          6 *
          (1 - ki);
      const ex = cx + it.x * g.spread;
      const ey = cy + it.y * g.spread + bob;
      const top = P(
        cx,
        by,
        k,
        (i - 2) * 0.22,
        H2 * 2 + 0.3,
        i % 2 ? 0.15 : -0.15,
      );
      const deep = P(cx, by, k, (i - 2) * 0.22, H2 * 0.9, i % 2 ? 0.15 : -0.15);
      let x = 0;
      let y = 0;
      if (ki < 0.78) {
        const f = ki / 0.78;
        x = lerp(ex, top[0], f);
        y = lerp(ey, top[1], f) - Math.sin(Math.PI * f) * g.u * 0.9;
      } else {
        const f = (ki - 0.78) / 0.22;
        x = lerp(top[0], deep[0], f);
        y = lerp(top[1], deep[1], f);
      }
      cv.save();
      cv.translate(x, y);
      cv.rotate(
        (it.r * (1 - ki) +
          (o.burst > 0 ? Math.sin(o.burst * 9 + i) * 0.4 * (1 - o.burst) : 0)) *
          57.3,
        0,
        0,
      );
      const sc = it.s * lerp(1, 0.62, ki);
      cv.scale(sc, sc);
      drawItem(cv, it.kind, g.u);
      cv.restore();
    }
    if (pass === 0) {
      // front and right walls cover what is inside
      quad(
        cv,
        [
          P(cx, by, k, -W2, 0, D2),
          P(cx, by, k, W2, 0, D2),
          P(cx, by, k, W2, H2 * 2, D2),
          P(cx, by, k, -W2, H2 * 2, D2),
        ],
        "#D9A066",
      );
      quad(
        cv,
        [
          P(cx, by, k, W2, 0, D2),
          P(cx, by, k, W2, 0, -D2),
          P(cx, by, k, W2, H2 * 2, -D2),
          P(cx, by, k, W2, H2 * 2, D2),
        ],
        "#C48A50",
      );
      const l = P(cx, by, k, -W2 * 0.6, H2 * 1.1, D2);
      const r = P(cx, by, k, W2 * 0.6, H2 * 1.1, D2);
      cv.drawLine(l[0], l[1], r[0], r[1], paint("#8A5A30", 0.35, 2));
      // a little printed logo on the front
      const lg = P(cx, by, k, 0, H2 * 0.75, D2);
      cv.drawCircle(lg[0], lg[1], k * 0.12, paint("#8A5A30", 0.28));
    }
  }
  // flaps: front, back, left, right, folding from open (flat outward) to closed over the top
  const fl = reduced ? (p > 0.95 ? 1 : 0) : smoother((p - 0.72) / 0.22);
  const ang = (1 - fl) * 2.2;
  const flaps = [
    { ax: [-W2, W2], z: D2, dir: 1, len: D2 },
    { ax: [-W2, W2], z: -D2, dir: -1, len: D2 },
  ];
  for (let f = 0; f < 2; f++) {
    const F = flaps[f];
    const hy = H2 * 2 + F.len * Math.sin(ang);
    const z2 = F.z - F.dir * F.len * Math.cos(ang);
    quad(
      cv,
      [
        P(cx, by, k, F.ax[0], H2 * 2, F.z),
        P(cx, by, k, F.ax[1], H2 * 2, F.z),
        P(cx, by, k, F.ax[1], hy, z2),
        P(cx, by, k, F.ax[0], hy, z2),
      ],
      f === 0 ? "#E3AE73" : "#C9925A",
    );
  }
  for (let f = 0; f < 2; f++) {
    const sx = f === 0 ? -W2 : W2;
    const dir = f === 0 ? -1 : 1;
    const hy = H2 * 2 + W2 * 0.55 * Math.sin(ang);
    const x2 = sx - dir * W2 * 0.55 * Math.cos(ang);
    quad(
      cv,
      [
        P(cx, by, k, sx, H2 * 2, -D2),
        P(cx, by, k, sx, H2 * 2, D2),
        P(cx, by, k, x2, hy, D2),
        P(cx, by, k, x2, hy, -D2),
      ],
      f === 0 ? "#D29B60" : "#DDA86C",
    );
  }
  // tape across the closed top
  if (o.tape > 0) {
    const t0 = P(cx, by, k, -0.12, H2 * 2 + 0.002, D2 + 0.02);
    const t1 = P(cx, by, k, -0.12, H2 * 2 + 0.002, D2 - 2 * D2 * o.tape);
    const t2 = P(cx, by, k, 0.12, H2 * 2 + 0.002, D2 - 2 * D2 * o.tape);
    const t3 = P(cx, by, k, 0.12, H2 * 2 + 0.002, D2 + 0.02);
    quad(cv, [t0, t1, t2, t3], "#F4E2B8", 0.92);
    const d0 = P(cx, by, k, -0.12, H2 * 2, D2);
    const d1 = P(cx, by, k, 0.12, H2 * 2, D2);
    const d2 = P(cx, by, k, 0.12, H2 * 2 - 0.3 * o.tape, D2);
    const d3 = P(cx, by, k, -0.12, H2 * 2 - 0.3 * o.tape, D2);
    quad(cv, [d0, d1, d2, d3], "#F4E2B8", 0.92);
  }
};

type Props = { autoPlay?: boolean };

export function BundlePackBox({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "BPB-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "BPB-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "BPB-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const g: Geo = {
    cx: W / 2,
    cy: Math.round(H * 0.29),
    k: W * 0.19,
    u: W * 0.17,
    spread: W * 0.38,
    by: Math.round(H * 0.5),
  };

  const st = useSharedValue({
    p: 0,
    v: 0,
    target: 0,
    pinch: 0,
    p0: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    burstAt: -10,
    armed: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ p: 0, t: 0, tape: 0, burst: 0 });
  const [packed, setPacked] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __bundleBox?: unknown };
    gl.__bundleBox = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__bundleBox;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
    else if (k === 1)
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
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
    if (dbg >= 0) {
      const tt = dbg % LOOP;
      const p = scriptP(tt);
      out.set({
        p,
        t: tt,
        tape: tapeOf(p),
        burst: tt >= 5.6 && tt < 6.4 ? (tt - 5.6) / 0.8 : 0,
      });
      return;
    }
    if (
      auto &&
      s.pinch === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.p < 0.001 && s.target === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 7.4;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      s.p = scriptP(s.loopT);
      s.v = 0;
      s.target = s.p > 0.5 ? 1 : 0;
      const b = s.loopT >= 5.6 && s.loopT < 6.4 ? (s.loopT - 5.6) / 0.8 : 0;
      out.set({ p: s.p, t: now, tape: tapeOf(s.p), burst: b });
      return;
    }
    s.scripted = 0;
    if (s.pinch === 0) {
      const kk = reduced ? 500 : 60;
      const c = 2 * Math.sqrt(kk);
      s.v += (kk * (s.target - s.p) - c * s.v) * dt;
      s.p += s.v * dt;
      if (s.p < 0) s.p = 0;
      if (s.p > 1) s.p = 1;
    }
    const armed = s.p > 0.97 ? 1 : 0;
    if (armed !== s.armed) {
      s.armed = armed;
      if (armed) scheduleOnRN(buzz, 2);
      else s.burstAt = now;
    }
    const burst = now - s.burstAt < 0.8 ? (now - s.burstAt) / 0.8 : 0;
    out.set({ p: s.p, t: now, tape: tapeOf(s.p), burst });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `p=${s.p.toFixed(2)} target=${s.target} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().p > 0.97,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setPacked, v);
    },
  );

  const pinch = Gesture.Pinch()
    .onStart(() => {
      const s = st.get();
      s.pinch = 1;
      s.scripted = 0;
      s.p0 = s.p;
    })
    .onUpdate((e) => {
      const s = st.get();
      s.p = clamp01(s.p0 + (1 - e.scale) * 1.6);
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.pinch === 0) return;
      s.pinch = 0;
      s.target =
        e.velocity < -0.4 ? 1 : e.velocity > 0.4 ? 0 : s.p > 0.5 ? 1 : 0;
      s.lastTouch = clock.get();
      scheduleOnRN(buzz, 1);
    });

  const toggle = () => {
    "worklet";
    const s = st.get();
    s.target = s.target > 0.5 ? 0 : 1;
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const onCta = () => {
    buzz(1);
    scheduleOnUI(toggle);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawScene(cv, g, out.get(), reduced)),
  );
  const stamp = useAnimatedStyle(() => {
    const v = clamp01((out.get().p - 0.96) / 0.04);
    return {
      opacity: v,
      transform: [{ rotate: "-8deg" }, { scale: 1.4 - 0.4 * v }],
    };
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.header, { top: insets.top + 6 }]}>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Back"
        >
          <Ionicons name="chevron-back" size={22} color="#2A2420" />
        </Pressable>
        <Text style={styles.brand}>Kitform</Text>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Bag, 1 item"
        >
          <Ionicons name="bag-handle-outline" size={21} color="#2A2420" />
        </Pressable>
      </View>
      <GestureDetector gesture={pinch}>
        <View
          style={[styles.stage, { top: insets.top + 60, height: H * 0.56 }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel={
            packed
              ? "The kit packed and taped in its box"
              : "The kit floating apart. Pinch to pack it"
          }
        />
      </GestureDetector>
      <Animated.View
        style={[styles.stamp, { top: g.cy - 40, left: W / 2 + 30 }, stamp]}
        pointerEvents="none"
      >
        <Text style={styles.stampText}>READY{"\n"}TO SHIP</Text>
      </Animated.View>
      <View style={[styles.sheet, { paddingBottom: insets.bottom + 14 }]}>
        <Text style={styles.kicker}>BUNDLE · 5 PIECES</Text>
        <Text style={styles.title}>Home Gym Starter</Text>
        <Text style={styles.desc}>
          Kettlebell 8 kg, adjustable dumbbell, travel mat, 750 ml bottle and a
          light band.
        </Text>
        <View style={styles.priceRow}>
          <Text style={styles.price}>$129</Text>
          <Text style={styles.was}>$166</Text>
          <View style={styles.save}>
            <Text style={styles.saveText}>Save 22%</Text>
          </View>
        </View>
        <Pressable
          onPress={onCta}
          accessibilityRole="button"
          accessibilityLabel={packed ? "Unpack the bundle" : "Pack the bundle"}
          style={({ pressed }) => [
            styles.cta,
            packed && styles.ctaDone,
            pressed && styles.pressed,
          ]}
        >
          <Ionicons
            name={packed ? "checkmark-circle" : "cube-outline"}
            size={20}
            color="#FFFFFF"
          />
          <Text style={styles.ctaText}>
            {packed ? "Packed · Add bundle to bag" : "Pack bundle"}
          </Text>
        </Pressable>
        <Text style={styles.hint}>
          Tip: pinch the kit together to pack it yourself
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F6E9DA" },
  header: {
    position: "absolute",
    left: 16,
    right: 16,
    height: 44,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  round: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(255,255,255,0.8)",
    alignItems: "center",
    justifyContent: "center",
  },
  brand: {
    fontFamily: "BPB-Bold",
    fontSize: 18,
    color: "#2A2420",
    letterSpacing: -0.3,
  },
  stage: { position: "absolute", left: 0, right: 0 },
  stamp: {
    position: "absolute",
    borderWidth: 3,
    borderColor: "#E2574C",
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  stampText: {
    fontFamily: "BPB-Bold",
    fontSize: 13,
    lineHeight: 15,
    color: "#E2574C",
    letterSpacing: 1.2,
    textAlign: "center",
  },
  sheet: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    paddingHorizontal: 22,
    paddingTop: 22,
  },
  kicker: {
    fontFamily: "BPB-SemiBold",
    fontSize: 12,
    letterSpacing: 1.2,
    color: "#B07A45",
  },
  title: {
    fontFamily: "BPB-Bold",
    fontSize: 24,
    color: "#2A2420",
    marginTop: 6,
  },
  desc: {
    fontFamily: "BPB-Regular",
    fontSize: 15,
    lineHeight: 21,
    color: "#7A706A",
    marginTop: 6,
  },
  priceRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginTop: 14,
  },
  price: { fontFamily: "BPB-Bold", fontSize: 26, color: "#2A2420" },
  was: {
    fontFamily: "BPB-Regular",
    fontSize: 16,
    color: "#A79D96",
    textDecorationLine: "line-through",
  },
  save: {
    paddingHorizontal: 10,
    height: 26,
    borderRadius: 13,
    backgroundColor: "#FFE7DE",
    justifyContent: "center",
  },
  saveText: { fontFamily: "BPB-SemiBold", fontSize: 13, color: "#E2574C" },
  cta: {
    marginTop: 16,
    height: 56,
    borderRadius: 28,
    backgroundColor: "#2A2420",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  ctaDone: { backgroundColor: "#2F9E62" },
  ctaText: { fontFamily: "BPB-SemiBold", fontSize: 17, color: "#FFFFFF" },
  hint: {
    fontFamily: "BPB-Regular",
    fontSize: 13,
    color: "#A79D96",
    textAlign: "center",
    marginTop: 10,
  },
  pressed: { transform: [{ scale: 0.96 }] },
});
