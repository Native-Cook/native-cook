import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  PaintStyle,
  Picture,
  Skia,
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
 Dollhouse Floors — a listing page of an invented home-search app, Nestly.

 Look (Pinterest isometric cut-away homes): a two-storey house as a little cut-away model — the front
 walls removed so the rooms and furniture show — on a soft plate, under the listing's name and price.

 Ours: drag the model up (or tap a floor chip) and the house comes apart like a dollhouse: the roof
 lifts off first and the upper storey rises after it, each floor floating at its own height with its
 shadow on the floor below. Tap a storey to bring it forward — the others dim and the card under the
 model says what is on it. Drag down and it stacks back together with a little settle.

 autoPlay: LOOP = 9 s — closed 0–1.0, comes apart 1.0–2.2, the ground floor is picked 2.7–4.1, the
 first floor 4.1–5.5, nothing 5.5, stacks back 6.3–7.5, rest to 9.0 (frame 9 = frame 0). Reduced motion:
 floors fade apart and together instead of lifting.
*/

let debugState = "";

const LOOP = 9;
const X = 4;
const Y = 3;
const FH = 2.4;

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
  let e = 0;
  if (t >= 1.0 && t < 2.2) e = smoother((t - 1.0) / 1.2);
  else if (t >= 2.2 && t < 6.3) e = 1;
  else if (t >= 6.3 && t < 7.5) e = 1 - smoother((t - 6.3) / 1.2);
  const sel = t >= 2.7 && t < 4.1 ? 0 : t >= 4.1 && t < 5.5 ? 1 : -1;
  return { e, sel };
};

const FLOORS = [
  {
    name: "Ground floor",
    rooms: "Living room, kitchen, half bath",
    area: "68 m²",
  },
  { name: "First floor", rooms: "2 bedrooms, bath, study nook", area: "54 m²" },
  {
    name: "Roof",
    rooms: "Slate roof, attic storage, solar ready",
    area: "18 m²",
  },
];

type Geo = { cx: number; base: number; s: number };

const P = (g: Geo, x: number, y: number, z: number) => {
  "worklet";
  const a = g.s * 0.866;
  const b = g.s * 0.5;
  return [g.cx + (x - y) * a, g.base - (x + y) * b - z * g.s];
};
const paint = (c: string, a = 1) => {
  "worklet";
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(c));
  p.setAlphaf(a);
  return p;
};
const poly = (cv: SkCanvas, g: Geo, pts: number[][], c: string, a: number) => {
  "worklet";
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < pts.length; i++) {
    const q = P(g, pts[i][0], pts[i][1], pts[i][2]);
    if (i === 0) b.moveTo(q[0], q[1]);
    else b.lineTo(q[0], q[1]);
  }
  b.close();
  cv.drawPath(b.detach(), paint(c, a));
};
/** An iso box: the faces the camera sees (top, the -y side and the -x side). */
const box = (
  cv: SkCanvas,
  g: Geo,
  x: number,
  y: number,
  z: number,
  w: number,
  d: number,
  h: number,
  top: string,
  left: string,
  right: string,
  a: number,
) => {
  "worklet";
  poly(
    cv,
    g,
    [
      [x, y, z],
      [x + w, y, z],
      [x + w, y, z + h],
      [x, y, z + h],
    ],
    left,
    a,
  );
  poly(
    cv,
    g,
    [
      [x, y, z],
      [x, y + d, z],
      [x, y + d, z + h],
      [x, y, z + h],
    ],
    right,
    a,
  );
  poly(
    cv,
    g,
    [
      [x, y, z + h],
      [x + w, y, z + h],
      [x + w, y + d, z + h],
      [x, y + d, z + h],
    ],
    top,
    a,
  );
};

const drawStorey = (
  cv: SkCanvas,
  g: Geo,
  z: number,
  kind: number,
  a: number,
) => {
  "worklet";
  // slab
  box(cv, g, 0, 0, z - 0.25, X, Y, 0.25, "#E9C99B", "#C9A574", "#D8B585", a);
  // back walls (the cut-away keeps only these)
  poly(
    cv,
    g,
    [
      [0, Y, z],
      [X, Y, z],
      [X, Y, z + FH],
      [0, Y, z + FH],
    ],
    "#F6EFE4",
    a,
  );
  poly(
    cv,
    g,
    [
      [X, 0, z],
      [X, Y, z],
      [X, Y, z + FH],
      [X, 0, z + FH],
    ],
    "#EDE3D4",
    a,
  );
  poly(
    cv,
    g,
    [
      [0, Y - 0.12, z + FH],
      [X, Y - 0.12, z + FH],
      [X, Y, z + FH],
      [0, Y, z + FH],
    ],
    "#D9CBB6",
    a,
  );
  poly(
    cv,
    g,
    [
      [X - 0.12, 0, z + FH],
      [X, 0, z + FH],
      [X, Y, z + FH],
      [X - 0.12, Y, z + FH],
    ],
    "#D9CBB6",
    a,
  );
  // a window on each wall
  poly(
    cv,
    g,
    [
      [1.3, Y - 0.01, z + 0.9],
      [2.4, Y - 0.01, z + 0.9],
      [2.4, Y - 0.01, z + 1.9],
      [1.3, Y - 0.01, z + 1.9],
    ],
    "#BFE3F2",
    a,
  );
  poly(
    cv,
    g,
    [
      [X - 0.01, 0.8, z + 0.9],
      [X - 0.01, 1.8, z + 0.9],
      [X - 0.01, 1.8, z + 1.9],
      [X - 0.01, 0.8, z + 1.9],
    ],
    "#BFE3F2",
    a,
  );
  if (kind === 0) {
    // rug, sofa, table, kitchen run
    poly(
      cv,
      g,
      [
        [0.4, 0.4, z + 0.01],
        [2.0, 0.4, z + 0.01],
        [2.0, 1.8, z + 0.01],
        [0.4, 1.8, z + 0.01],
      ],
      "#F2B8A2",
      a,
    );
    box(cv, g, 0.5, 2.1, z, 1.8, 0.7, 0.45, "#5FA8A0", "#4C8E86", "#56998F", a);
    box(
      cv,
      g,
      0.5,
      2.6,
      z + 0.45,
      1.8,
      0.2,
      0.4,
      "#5FA8A0",
      "#4C8E86",
      "#56998F",
      a,
    );
    box(cv, g, 0.9, 0.8, z, 0.8, 0.6, 0.35, "#B07A4A", "#8E5E35", "#9D6A3E", a);
    box(
      cv,
      g,
      X - 0.7,
      0.3,
      z,
      0.6,
      2.4,
      0.9,
      "#F4F6F8",
      "#D5DBE2",
      "#E3E8EE",
      a,
    );
    box(
      cv,
      g,
      X - 0.7,
      0.3,
      z + 0.9,
      0.6,
      2.4,
      0.06,
      "#7C8898",
      "#66717F",
      "#707C8B",
      a,
    );
    box(cv, g, 2.4, 0.5, z, 0.7, 0.7, 0.7, "#5DBB6B", "#46A65A", "#52B062", a);
  } else {
    // bed, nightstand, bath
    box(cv, g, 0.3, 1.4, z, 1.6, 1.4, 0.4, "#F7F9FC", "#D6DEE8", "#E4EAF2", a);
    box(
      cv,
      g,
      0.3,
      1.4,
      z + 0.4,
      1.6,
      0.6,
      0.12,
      "#7E9CFF",
      "#6A86E6",
      "#7590F2",
      a,
    );
    box(
      cv,
      g,
      0.3,
      2.7,
      z + 0.4,
      1.6,
      0.25,
      0.5,
      "#B98BFF",
      "#9D72E6",
      "#AA7EF2",
      a,
    );
    box(cv, g, 2.1, 2.3, z, 0.5, 0.5, 0.5, "#B07A4A", "#8E5E35", "#9D6A3E", a);
    box(
      cv,
      g,
      X - 1.2,
      0.3,
      z,
      1.0,
      1.6,
      0.55,
      "#FFFFFF",
      "#DCE3EA",
      "#E8EDF2",
      a,
    );
    poly(
      cv,
      g,
      [
        [X - 1.1, 0.4, z + 0.56],
        [X - 0.3, 0.4, z + 0.56],
        [X - 0.3, 1.8, z + 0.56],
        [X - 1.1, 1.8, z + 0.56],
      ],
      "#BFE3F2",
      a,
    );
  }
};
const drawRoof = (cv: SkCanvas, g: Geo, z: number, a: number) => {
  "worklet";
  const h = 1.7;
  box(cv, g, 0, 0, z - 0.2, X, Y, 0.2, "#D9CBB6", "#BCAE98", "#C9BBA5", a);
  // gable end (toward the camera) and the near slope
  poly(
    cv,
    g,
    [
      [0, 0, z],
      [0, Y, z],
      [0, Y / 2, z + h],
    ],
    "#F0E6D8",
    a,
  );
  poly(
    cv,
    g,
    [
      [-0.15, -0.15, z],
      [X + 0.15, -0.15, z],
      [X + 0.15, Y / 2, z + h],
      [-0.15, Y / 2, z + h],
    ],
    "#5B6475",
    a,
  );
  for (let i = 1; i < 5; i++) {
    const k = i / 5;
    const q0 = P(g, -0.15, -0.15 + (Y / 2 + 0.15) * k, z + h * k);
    const q1 = P(g, X + 0.15, -0.15 + (Y / 2 + 0.15) * k, z + h * k);
    const p = paint("#4A5262", a);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(1.2);
    cv.drawLine(q0[0], q0[1], q1[0], q1[1], p);
  }
  box(
    cv,
    g,
    X - 1.1,
    Y / 2 - 0.2,
    z + h * 0.5,
    0.45,
    0.45,
    1.2,
    "#C97B5A",
    "#A9624A",
    "#B86D51",
    a,
  );
  poly(
    cv,
    g,
    [
      [0.6, 0.0, z + 0.6],
      [1.4, 0.0, z + 0.6],
      [1.4, 0.4, z + 0.98],
      [0.6, 0.4, z + 0.98],
    ],
    "#7FB4E8",
    a,
  );
};

/** Draw a storey as one layer, so a dimmed storey fades as a whole instead of turning see-through. */
const layered = (cv: SkCanvas, a: number) => {
  "worklet";
  const p = Skia.Paint();
  p.setAlphaf(a);
  cv.saveLayer(p);
};

type Out = { e: number; sel: number; selK: number[] };

const drawHouse = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const e = o.e;
  const lift1 = reduced ? 0 : 1.3 * smoother((e - 0.15) / 0.85);
  const lift2 = reduced ? 0 : 1.3 * smoother(e / 0.8) + lift1;
  const z1 = FH + 0.25 + lift1;
  const z2 = 2 * (FH + 0.25) + lift2;
  // plate and soft shadows that spread as the floors part
  const pc = P(g, X / 2, Y / 2, -0.3);
  cv.drawOval(
    Skia.XYWHRect(pc[0] - g.s * 4.2, pc[1] - g.s * 1.6, g.s * 8.4, g.s * 3.6),
    paint("#E7DCCB"),
  );
  cv.drawOval(
    Skia.XYWHRect(pc[0] - g.s * 3.2, pc[1] - g.s * 1.0, g.s * 6.4, g.s * 2.4),
    paint("#3A2E22", 0.08),
  );
  const a0 = 1 - 0.62 * o.selK[0];
  const a1 = 1 - 0.62 * o.selK[1];
  const a2 = 1 - 0.62 * o.selK[2];
  layered(cv, a0);
  drawStorey(cv, g, 0, 0, 1);
  cv.restore();
  if (lift1 > 0.05) {
    const sh = P(g, X / 2, Y / 2, FH + 0.02);
    cv.drawOval(
      Skia.XYWHRect(sh[0] - g.s * 2.6, sh[1] - g.s * 0.9, g.s * 5.2, g.s * 1.8),
      paint("#3A2E22", 0.1 * Math.min(1, lift1)),
    );
  }
  layered(cv, a1);
  drawStorey(cv, g, z1, 1, 1);
  cv.restore();
  if (lift2 - lift1 > 0.05) {
    const sh = P(g, X / 2, Y / 2, z1 + FH + 0.02);
    cv.drawOval(
      Skia.XYWHRect(sh[0] - g.s * 2.6, sh[1] - g.s * 0.9, g.s * 5.2, g.s * 1.8),
      paint("#3A2E22", 0.1 * Math.min(1, lift2 - lift1)),
    );
  }
  layered(cv, reduced ? 1 - e * 0.9 : a2);
  drawRoof(cv, g, z2, 1);
  cv.restore();
};

type Props = { autoPlay?: boolean };

export function DollhouseFloors({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "DHF-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "DHF-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "DHF-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const g: Geo = {
    cx: W / 2 + 6,
    base: Math.round(H * 0.63),
    s: Math.round(W * 0.074),
  };

  const st = useSharedValue({
    e: 0,
    v: 0,
    target: 0,
    drag: 0,
    e0: 0,
    sel: -1,
    selK: [0, 0, 0],
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ e: 0, sel: -1, selK: [0, 0, 0] });
  const [sel, setSel] = useState(-1);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __dollhouse?: unknown };
    gl.__dollhouse = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__dollhouse;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const s = st.get();
    const dbg = debugT.get();
    let e = s.e;
    let selNow = s.sel;
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      e = sc.e;
      selNow = sc.sel;
    } else if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.e < 0.001 && s.sel === -1))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.e = sc.e;
      s.target = sc.e > 0.5 ? 1 : 0;
      s.sel = sc.sel;
      e = s.e;
      selNow = s.sel;
    } else {
      s.scripted = 0;
      if (s.drag === 0) {
        const k = 140;
        const c = 2 * 0.72 * Math.sqrt(k);
        s.v += (k * (s.target - s.e) - c * s.v) * dt;
        s.e += s.v * dt;
      }
      e = s.e;
    }
    for (let i = 0; i < 3; i++) {
      const want = selNow >= 0 && selNow !== i ? 1 : 0;
      s.selK[i] += (want - s.selK[i]) * (1 - Math.exp(-10 * dt));
    }
    out.set({ e, sel: selNow, selK: [s.selK[0], s.selK[1], s.selK[2]] });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `e=${e.toFixed(2)} sel=${selNow} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().sel,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setSel, v);
    },
  );
  useAnimatedReaction(
    () => out.get().e > 0.5,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setOpen, v);
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onStart(() => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.e0 = s.e;
      s.v = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      const raw = s.e0 - e.translationY / 220;
      s.e = raw < 0 ? raw * 0.2 : raw > 1 ? 1 + (raw - 1) * 0.25 : raw;
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      s.target =
        e.velocityY < -400 ? 1 : e.velocityY > 400 ? 0 : s.e > 0.5 ? 1 : 0;
      if (s.target === 0) s.sel = -1;
      s.v = -e.velocityY / 220;
      s.lastTouch = clock.get();
      scheduleOnRN(buzz, 1);
    });
  const tap = Gesture.Tap().onEnd((ev) => {
    const s = st.get();
    s.lastTouch = clock.get();
    s.scripted = 0;
    if (s.e < 0.5) {
      s.target = 1;
      return;
    }
    // which storey's slab is nearest the touch
    const lift1 = 1.3 * smoother((s.e - 0.15) / 0.85);
    const lift2 = 1.3 * smoother(s.e / 0.8) + lift1;
    const zs = [0.8, FH + 0.25 + lift1 + 0.8, 2 * (FH + 0.25) + lift2 + 0.6];
    let best = -1;
    let bd = 1e9;
    for (let i = 0; i < 3; i++) {
      const q = P(g, X / 2, Y / 2, zs[i]);
      const d = Math.abs(ev.absoluteY - q[1]);
      if (d < bd) {
        bd = d;
        best = i;
      }
    }
    s.sel = s.sel === best ? -1 : best;
    scheduleOnRN(buzz, 0);
  });
  const gesture = Gesture.Race(pan, tap);

  const pick = (i: number) => {
    "worklet";
    const s = st.get();
    s.scripted = 0;
    s.lastTouch = clock.get();
    if (i < 0) {
      s.target = s.target > 0.5 ? 0 : 1;
      s.sel = -1;
    } else {
      s.target = 1;
      s.sel = s.sel === i ? -1 : i;
    }
  };
  const onChip = (i: number) => {
    buzz(0);
    scheduleOnUI(pick, i);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawHouse(cv, g, out.get(), reduced)),
  );
  const info = useAnimatedStyle(() => {
    const o = out.get();
    const v = clamp01((o.e - 0.6) / 0.4);
    return { opacity: v, transform: [{ translateY: (1 - v) * 10 }] };
  });

  if (!fontsOk) return <View style={styles.screen} />;
  const f = sel >= 0 ? FLOORS[sel] : null;

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
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Save home"
        >
          <Ionicons name="heart-outline" size={21} color="#2A2420" />
        </Pressable>
      </View>
      <View style={[styles.titleBlock, { top: insets.top + 62 }]}>
        <Text style={styles.kicker}>FOR SALE · OPEN HOUSE SAT</Text>
        <Text style={styles.title}>Maple Row Townhouse</Text>
        <Text style={styles.meta}>3 bd · 2 ba · 122 m² · Built 2019</Text>
      </View>
      <GestureDetector gesture={gesture}>
        <View
          style={[
            styles.model,
            { top: insets.top + 150, height: g.base - insets.top - 130 },
          ]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={
            open
              ? "Floors pulled apart. Tap a floor to focus it, drag down to close"
              : "Cut-away model of the house. Drag up to see each floor"
          }
        />
      </GestureDetector>
      <Animated.View
        style={[styles.info, { top: g.base + 40 }, info]}
        pointerEvents="none"
        accessibilityLiveRegion="polite"
      >
        <Text style={styles.infoTitle}>
          {f ? `${f.name} · ${f.area}` : "Tap a floor to look inside"}
        </Text>
        <Text style={styles.infoText}>
          {f ? f.rooms : "3 levels · 140 m² with the attic"}
        </Text>
      </Animated.View>
      <View style={[styles.chips, { bottom: insets.bottom + 96 }]}>
        {["Roof", "First", "Ground"].map((c, k) => {
          const i = 2 - k;
          const on = sel === i;
          return (
            <Pressable
              key={c}
              onPress={() => onChip(i)}
              accessibilityRole="button"
              accessibilityState={{ selected: on }}
              accessibilityLabel={`${c} floor`}
              style={({ pressed }) => [
                styles.chip,
                on && styles.chipOn,
                pressed && styles.pressed,
              ]}
            >
              <Text style={[styles.chipText, on && styles.chipTextOn]}>
                {c}
              </Text>
            </Pressable>
          );
        })}
        <Pressable
          onPress={() => onChip(-1)}
          accessibilityRole="button"
          accessibilityLabel={
            open ? "Stack the floors" : "Pull the floors apart"
          }
          style={({ pressed }) => [
            styles.chip,
            styles.chipIcon,
            pressed && styles.pressed,
          ]}
        >
          <Ionicons
            name={open ? "contract-outline" : "layers-outline"}
            size={18}
            color="#2A2420"
          />
        </Pressable>
      </View>
      <View style={[styles.bottom, { bottom: insets.bottom + 16 }]}>
        <View>
          <Text style={styles.price}>$684,000</Text>
          <Text style={styles.est}>Est. $3,210/mo</Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Book a viewing"
          style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
        >
          <Text style={styles.ctaText}>Book a viewing</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FBF6EE" },
  header: {
    position: "absolute",
    left: 16,
    right: 16,
    height: 44,
    flexDirection: "row",
    justifyContent: "space-between",
  },
  round: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  titleBlock: { position: "absolute", left: 20, right: 20 },
  kicker: {
    fontFamily: "DHF-SemiBold",
    fontSize: 12,
    letterSpacing: 1.1,
    color: "#B07A45",
  },
  title: {
    fontFamily: "DHF-Bold",
    fontSize: 26,
    color: "#2A2420",
    marginTop: 4,
  },
  meta: {
    fontFamily: "DHF-Regular",
    fontSize: 15,
    color: "#7A706A",
    marginTop: 4,
  },
  model: { position: "absolute", left: 0, right: 0 },
  info: { position: "absolute", left: 20, right: 20, alignItems: "center" },
  infoTitle: { fontFamily: "DHF-SemiBold", fontSize: 16, color: "#2A2420" },
  infoText: {
    fontFamily: "DHF-Regular",
    fontSize: 14,
    color: "#7A706A",
    marginTop: 3,
  },
  chips: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
  },
  chip: {
    height: 40,
    paddingHorizontal: 18,
    borderRadius: 20,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: "#EADFCF",
    justifyContent: "center",
    alignItems: "center",
  },
  chipOn: { backgroundColor: "#2A2420", borderColor: "#2A2420" },
  chipIcon: { width: 44, paddingHorizontal: 0 },
  chipText: { fontFamily: "DHF-SemiBold", fontSize: 14, color: "#2A2420" },
  chipTextOn: { color: "#FFFFFF" },
  bottom: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  price: { fontFamily: "DHF-Bold", fontSize: 22, color: "#2A2420" },
  est: {
    fontFamily: "DHF-Regular",
    fontSize: 13,
    color: "#7A706A",
    marginTop: 2,
  },
  cta: {
    height: 56,
    paddingHorizontal: 26,
    borderRadius: 28,
    backgroundColor: "#2A2420",
    justifyContent: "center",
  },
  ctaText: { fontFamily: "DHF-SemiBold", fontSize: 16, color: "#FFFFFF" },
  pressed: { transform: [{ scale: 0.96 }] },
});
