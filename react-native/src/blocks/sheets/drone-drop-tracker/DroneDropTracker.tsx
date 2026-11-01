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
import { scheduleOnRN } from "react-native-worklets";

/*
 Drone Drop Tracker — the order-tracking screen of an invented grocery delivery app, Hopper.

 Look (a Pinterest render of a delivery drone carrying a noodle box): a little toy town seen from above,
 a quadcopter with the parcel hanging under it.

 Ours: the town is the tracking map. The drone flies the route from the shop to your door with its
 rotors blurring, its shadow sliding on the ground below and the box swinging on its line, then hovers
 and lowers the box on the line to the doorstep, lets go and climbs away. The order sheet at the bottom
 is a real sheet: drag it up and the map zooms in on the drone while the steps appear; drag it down
 and the town comes back. Drag on the map to scrub the flight.

 autoPlay: LOOP = 9 s — flight 0–5.4 s (the sheet opens 2.4–3.1), the box is lowered 5.4–6.5, let go
 6.5, the drone climbs and flies home 6.6–8.6 while the sheet closes 7.8–8.5, a new box appears at the
 shop by 9.0 (frame 9 = frame 0). Reduced motion: no rotor blur or swing; the map does not zoom.
*/

let debugState = "";

const LOOP = 9;
const ROUTE: [number, number][] = [
  [0.16, 0.2],
  [0.16, 0.42],
  [0.5, 0.42],
  [0.5, 0.66],
  [0.82, 0.66],
  [0.82, 0.8],
];

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
/** Point along the route at fraction f, with the heading's sideways lean. */
const routeAt = (f: number) => {
  "worklet";
  let total = 0;
  const seg: number[] = [];
  for (let i = 0; i < ROUTE.length - 1; i++) {
    const d = Math.hypot(
      ROUTE[i + 1][0] - ROUTE[i][0],
      ROUTE[i + 1][1] - ROUTE[i][1],
    );
    seg.push(d);
    total += d;
  }
  let want = clamp01(f) * total;
  for (let i = 0; i < seg.length; i++) {
    if (want <= seg[i] || i === seg.length - 1) {
      const k = seg[i] > 0 ? Math.min(1, want / seg[i]) : 0;
      // round the corners: ease toward the next leg near the end of each leg
      return {
        x: lerp(ROUTE[i][0], ROUTE[i + 1][0], k),
        y: lerp(ROUTE[i][1], ROUTE[i + 1][1], k),
        dx: ROUTE[i + 1][0] - ROUTE[i][0],
        dy: ROUTE[i + 1][1] - ROUTE[i][1],
      };
    }
    want -= seg[i];
  }
  return { x: ROUTE[0][0], y: ROUTE[0][1], dx: 0, dy: 0 };
};

/** The script: route fraction, box drop (0 hanging … 1 on the doorstep), carried, sheet openness. */
const scriptAt = (t: number) => {
  "worklet";
  let f = 0;
  let drop = 0;
  let carried = 1;
  let back = 0;
  if (t < 5.4) f = smoother(t / 5.4);
  else if (t < 6.6) {
    f = 1;
    drop = smoother((t - 5.4) / 1.1);
    if (t > 6.5) carried = 0;
  } else if (t < 8.6) {
    f = 1;
    drop = 1;
    carried = 0;
    back = smoother((t - 6.6) / 2.0);
  } else {
    f = 0;
    drop = 0;
    carried = clamp01((t - 8.6) / 0.4);
    back = 1;
  }
  const sheet =
    t < 2.4
      ? 0
      : t < 3.1
        ? smoother((t - 2.4) / 0.7)
        : t < 7.8
          ? 1
          : 1 - smoother((t - 7.8) / 0.7);
  return { f, drop, carried, back, sheet };
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

const house = (
  cv: SkCanvas,
  x: number,
  y: number,
  w: number,
  roof: string,
  wall: string,
) => {
  "worklet";
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x - w / 2, y - w * 0.15, w, w * 0.55), 4, 4),
    paint(wall),
  );
  const r = Skia.PathBuilder.Make();
  r.moveTo(x - w * 0.6, y - w * 0.1);
  r.lineTo(x, y - w * 0.62);
  r.lineTo(x + w * 0.6, y - w * 0.1);
  r.close();
  cv.drawPath(r.detach(), paint(roof));
  cv.drawRect(
    Skia.XYWHRect(x - w * 0.1, y + w * 0.12, w * 0.2, w * 0.28),
    paint("#7A5A44"),
  );
};

type Out = {
  f: number;
  drop: number;
  carried: number;
  back: number;
  sheet: number;
  t: number;
  live: number;
};
type Geo = { W: number; mapH: number; top: number; focusY: number };

const drawMap = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const W = g.W;
  const H = g.mapH;
  // where the drone is (flying home reverses the route)
  const pos = o.back > 0 ? routeAt(1 - o.back) : routeAt(o.f);
  const px = pos.x * W;
  const py = g.top + pos.y * H;
  // camera: zoom in on the drone as the sheet opens
  const z = reduced ? 1 : 1 + 0.5 * o.sheet;
  cv.save();
  const fx = lerp(W / 2, px, o.sheet);
  const fy = lerp(g.top + H / 2, py, o.sheet);
  cv.translate(lerp(0, W / 2 - fx, o.sheet), lerp(0, g.focusY - fy, o.sheet));
  cv.translate(fx, fy);
  cv.scale(z, z);
  cv.translate(-fx, -fy);
  // ground, blocks, roads
  cv.drawRect(Skia.XYWHRect(-W, g.top - H, W * 3, H * 3), paint("#BFE6C9"));
  for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 5; j++) {
      cv.drawRRect(
        Skia.RRectXY(
          Skia.XYWHRect(
            (0.02 + i * 0.25) * W,
            g.top + H * (0.02 + j * 0.22),
            W * 0.21,
            H * 0.18,
          ),
          14,
          14,
        ),
        paint("#D3F0D9"),
      );
    }
  }
  const roads = paint("#FFFFFF", 1, 16);
  for (let i = 0; i < 4; i++)
    cv.drawLine(
      0.0 * W,
      g.top + H * (0.2 + i * 0.22),
      1 * W,
      g.top + H * (0.2 + i * 0.22),
      roads,
    );
  for (let i = 0; i < 4; i++)
    cv.drawLine(
      (0.245 * i + 0.005 + 0.12 * 0) * W,
      g.top - 20,
      (0.245 * i + 0.005) * W,
      g.top + H + 20,
      roads,
    );
  // trees and houses
  for (let i = 0; i < 18; i++) {
    const tx = (((i * 0.137) % 1) * 0.96 + 0.02) * W;
    const ty = g.top + H * (((i * 0.311) % 1) * 0.94 + 0.03);
    cv.drawCircle(tx + 2, ty + 3, 9, paint("#2E6B4A", 0.18));
    cv.drawCircle(tx, ty, 9, paint(i % 3 ? "#5DBB6B" : "#46A65A"));
  }
  const roofs = ["#FF8A65", "#7E9CFF", "#FFC857", "#B98BFF"];
  for (let i = 0; i < 9; i++) {
    house(
      cv,
      (0.1 + ((i * 0.29) % 1) * 0.8) * W,
      g.top + H * (0.1 + ((i * 0.43) % 1) * 0.8),
      26,
      roofs[i % 4],
      "#FFF6EA",
    );
  }
  // shop and home
  const sx = ROUTE[0][0] * W;
  const sy = g.top + ROUTE[0][1] * H;
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(sx - 26, sy - 22, 52, 36), 8, 8),
    paint("#FFFFFF"),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(sx - 26, sy - 30, 52, 12), 6, 6),
    paint("#FF6B5A"),
  );
  const hx = ROUTE[ROUTE.length - 1][0] * W;
  const hy = g.top + ROUTE[ROUTE.length - 1][1] * H;
  house(cv, hx, hy, 40, "#2F9E62", "#FFFFFF");
  cv.drawOval(Skia.XYWHRect(hx - 22, hy + 22, 44, 12), paint("#2F9E62", 0.2));
  // route: travelled part solid, rest dotted
  const rp = Skia.PathBuilder.Make();
  for (let i = 0; i < ROUTE.length; i++) {
    const x = ROUTE[i][0] * W;
    const y = g.top + ROUTE[i][1] * H;
    if (i === 0) rp.moveTo(x, y);
    else rp.lineTo(x, y);
  }
  const route = rp.detach();
  const dots = paint("#2F3A4A", 0.5, 3);
  dots.setPathEffect(Skia.PathEffect.MakeDash([2, 9], 0));
  cv.drawPath(route, dots);
  const done = Skia.Path.Trim(route, 0, o.back > 0 ? 1 - o.back : o.f, false);
  if (done && o.back === 0) cv.drawPath(done, paint("#2F3A4A", 0.9, 4));
  // the dropped box waits on the doorstep
  if (o.carried < 1 && o.back > 0 && o.back < 1) {
    cv.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(hx - 9, hy + 18, 18, 14), 3, 3),
      paint("#D9A066"),
    );
  }
  // drone: altitude 1 in flight, lower while dropping
  const hover = reduced ? 0 : Math.sin(o.t * ((14 * Math.PI) / LOOP)) * 1.5;
  const dy = py - 34 + hover;
  // shadow on the ground
  cv.drawOval(
    Skia.XYWHRect(px - 22 + 10, py - 6 + 6, 44, 14),
    paint("#1E3A2A", 0.16),
  );
  // line and box
  const line = 10 + 44 * o.drop;
  const sway = reduced
    ? 0
    : Math.sin(o.t * ((8 * Math.PI) / LOOP)) *
      4 *
      (1 - o.drop) *
      (o.f > 0 && o.f < 1 ? 1 : 0.3);
  if (o.carried > 0) {
    cv.drawLine(
      px,
      dy + 8,
      px + sway,
      dy + 8 + line,
      paint("#2F3A4A", 0.8 * o.carried, 1.4),
    );
    cv.save();
    cv.translate(px + sway, dy + 8 + line);
    cv.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(-10, 0, 20, 16), 3, 3),
      paint("#D9A066", o.carried),
    );
    cv.drawRect(Skia.XYWHRect(-10, 6, 20, 3), paint("#FF6B5A", o.carried));
    cv.restore();
  }
  // body and four rotors
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(px - 13, dy - 7, 26, 16), 7, 7),
    paint("#F4F6FA"),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(px - 13, dy - 7, 26, 16), 7, 7),
    paint("#C9D1DC", 1, 1.2),
  );
  cv.drawCircle(px, dy + 1, 4, paint("#3D7BFF"));
  for (let k = 0; k < 4; k++) {
    const ax = px + (k % 2 ? 22 : -22);
    const ay = dy + (k < 2 ? -9 : 9);
    cv.drawLine(px, dy, ax, ay, paint("#C9D1DC", 1, 3));
    cv.drawOval(
      Skia.XYWHRect(ax - 13, ay - 4, 26, 8),
      paint("#2F3A4A", reduced ? 0.5 : 0.18),
    );
    const a = o.t * ((114 * Math.PI) / LOOP) + k;
    cv.drawLine(
      ax - Math.cos(a) * 12,
      ay - Math.sin(a) * 3.5,
      ax + Math.cos(a) * 12,
      ay + Math.sin(a) * 3.5,
      paint("#2F3A4A", 0.6, 2),
    );
  }
  cv.restore();
};

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

type Props = { autoPlay?: boolean };

export function DroneDropTracker({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "DDT-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "DDT-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "DDT-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const SHEET_MIN = 150 + insets.bottom;
  const SHEET_MAX = Math.round(H * 0.55);
  const g: Geo = {
    W,
    top: insets.top + 56,
    mapH: H - insets.top - 56 - SHEET_MIN + 30,
    focusY: insets.top + 56 + (H - SHEET_MAX - insets.top - 56) * 0.42,
  };

  const st = useSharedValue({
    f: 0,
    drop: 0,
    carried: 1,
    back: 0,
    sheet: 0,
    sv: 0,
    sTarget: 0,
    drag: 0,
    s0: 0,
    scrub: 0,
    f0: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    delivered: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    f: 0,
    drop: 0,
    carried: 1,
    back: 0,
    sheet: 0,
    t: 0,
    live: 0,
  });
  const [delivered, setDelivered] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __droneDrop?: unknown };
    gl.__droneDrop = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__droneDrop;
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
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({ ...sc, t: dbg, live: 0 });
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      s.scrub === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.f < 0.001 && s.back === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.f = sc.f;
      s.drop = sc.drop;
      s.carried = sc.carried;
      s.back = sc.back;
      s.sheet = sc.sheet;
      s.sTarget = sc.sheet > 0.5 ? 1 : 0;
      out.set({ ...sc, t: now, live: 0 });
      return;
    }
    s.scripted = 0;
    if (s.drag === 0) {
      const k = 180;
      const c = 2 * 0.9 * Math.sqrt(k);
      s.sv += (k * (s.sTarget - s.sheet) - c * s.sv) * dt;
      s.sheet += s.sv * dt;
    }
    if (s.scrub === 0 && s.back === 0 && s.f < 1)
      s.f = Math.min(1, s.f + dt / 30);
    if (s.f >= 1 && s.drop < 1 && s.scrub === 0) {
      s.drop = Math.min(1, s.drop + dt / 1.1);
      if (s.drop >= 1 && s.delivered === 0) {
        s.delivered = 1;
        s.carried = 0;
        scheduleOnRN(buzz, 1);
      }
    }
    out.set({
      f: s.f,
      drop: s.drop,
      carried: s.carried,
      back: s.back,
      sheet: s.sheet,
      t: now,
      live: 1,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `f=${s.f.toFixed(2)} drop=${s.drop.toFixed(2)} sheet=${s.sheet.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().drop > 0.98,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setDelivered, v);
    },
  );

  const sheetPan = Gesture.Pan()
    .onStart(() => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.s0 = s.sheet;
      s.sv = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      s.sheet = clamp01(s.s0 - e.translationY / (SHEET_MAX - SHEET_MIN));
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      s.sTarget =
        e.velocityY < -500 ? 1 : e.velocityY > 500 ? 0 : s.sheet > 0.5 ? 1 : 0;
      s.sv = -e.velocityY / (SHEET_MAX - SHEET_MIN);
      s.lastTouch = clock.get();
      scheduleOnRN(buzz, 0);
    });
  const mapScrub = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart(() => {
      const s = st.get();
      s.scrub = 1;
      s.scripted = 0;
      s.f0 = s.f;
      s.back = 0;
      s.carried = 1;
      s.drop = 0;
      s.delivered = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      s.f = clamp01(s.f0 + e.translationX / (W * 1.2));
      s.lastTouch = clock.get();
    })
    .onFinalize(() => {
      const s = st.get();
      s.scrub = 0;
      s.lastTouch = clock.get();
    });

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawMap(cv, g, out.get(), reduced)),
  );
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [
      { translateY: (1 - out.get().sheet) * (SHEET_MAX - SHEET_MIN) },
    ],
  }));
  const details = useAnimatedStyle(() => {
    const v = clamp01((out.get().sheet - 0.35) / 0.5);
    return { opacity: v, transform: [{ translateY: (1 - v) * 14 }] };
  });
  const eta = useAnimatedProps(() => {
    const o = out.get();
    const v =
      o.drop > 0.98
        ? "Delivered"
        : o.f >= 1
          ? "Lowering your order"
          : `Arriving in ${Math.max(1, Math.ceil((1 - o.f) * 6))} min`;
    return { text: v, defaultValue: v } as never;
  });
  const bar = useAnimatedStyle(() => {
    const o = out.get();
    // on the way home the bar drains back to the next order's start, so the loop has no jump
    const k = Math.min(1, Math.max(0, (o.back - 0.55) / 0.45));
    const drain = k * k * (3 - 2 * k);
    return {
      transform: [
        {
          scaleX:
            o.back > 0 ? 1 - 0.85 * drain : 0.15 + 0.75 * o.f + 0.1 * o.drop,
        },
      ],
    };
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={mapScrub}>
        <View
          style={[styles.mapHit, { top: g.top, height: g.mapH - 40 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Delivery map. Drag sideways to replay the flight"
        />
      </GestureDetector>
      <View style={[styles.header, { paddingTop: insets.top + 4 }]}>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Back"
        >
          <Ionicons name="chevron-back" size={22} color="#1F2A37" />
        </Pressable>
        <Text style={styles.headTitle}>Order #4821</Text>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Help"
        >
          <Ionicons name="help" size={20} color="#1F2A37" />
        </Pressable>
      </View>

      <Animated.View style={[styles.sheet, { height: SHEET_MAX }, sheetStyle]}>
        <GestureDetector gesture={sheetPan}>
          <View
            style={styles.sheetTop}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Order details. Drag up to open"
          >
            <View style={styles.grabber} />
            <View style={styles.row}>
              <View style={styles.droneIcon}>
                <Ionicons
                  name={delivered ? "checkmark" : "paper-plane"}
                  size={20}
                  color="#FFFFFF"
                />
              </View>
              <View style={{ flex: 1 }}>
                <AnimatedInput
                  editable={false}
                  style={styles.eta}
                  animatedProps={eta}
                />
                <Text style={styles.sub}>
                  Drone K-7 · 2 bags from Greenleaf Market
                </Text>
              </View>
            </View>
            <View style={styles.track}>
              <Animated.View style={[styles.fill, bar]} />
            </View>
          </View>
        </GestureDetector>
        <Animated.View style={[styles.details, details]}>
          {[
            ["bag-check-outline", "Order packed", "6:42 PM", true],
            ["paper-plane-outline", "In the air", "6:44 PM", true],
            [
              "download-outline",
              "Lowered to your doorstep",
              delivered ? "6:51 PM" : "about 6:51 PM",
              delivered,
            ],
          ].map(([icon, title, time, on]) => (
            <View key={title as string} style={styles.step}>
              <View style={[styles.stepIcon, on ? styles.stepOn : null]}>
                <Ionicons
                  name={icon as "bag-check-outline"}
                  size={18}
                  color={on ? "#FFFFFF" : "#8A94A6"}
                />
              </View>
              <Text style={styles.stepTitle}>{title as string}</Text>
              <Text style={styles.stepTime}>{time as string}</Text>
            </View>
          ))}
          <View style={styles.actions}>
            <Pressable
              style={({ pressed }) => [
                styles.action,
                pressed && styles.pressed,
              ]}
              accessibilityRole="button"
              accessibilityLabel="Message the hub"
            >
              <Ionicons name="chatbubble-outline" size={18} color="#1F2A37" />
              <Text style={styles.actionText}>Message</Text>
            </Pressable>
            <Pressable
              style={({ pressed }) => [
                styles.action,
                styles.actionDark,
                pressed && styles.pressed,
              ]}
              accessibilityRole="button"
              accessibilityLabel="Drop instructions"
            >
              <Ionicons name="location-outline" size={18} color="#FFFFFF" />
              <Text style={[styles.actionText, { color: "#FFFFFF" }]}>
                Drop spot
              </Text>
            </Pressable>
          </View>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#BFE6C9" },
  mapHit: { position: "absolute", left: 0, right: 0 },
  header: {
    position: "absolute",
    top: 0,
    left: 16,
    right: 16,
    flexDirection: "row",
    alignItems: "center",
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
  headTitle: {
    fontFamily: "DDT-SemiBold",
    fontSize: 16,
    color: "#1F2A37",
    backgroundColor: "#FFFFFF",
    paddingHorizontal: 14,
    height: 36,
    lineHeight: 36,
    borderRadius: 18,
    overflow: "hidden",
  },
  sheet: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    shadowColor: "#1F2A37",
    shadowOpacity: 0.12,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: -4 },
  },
  sheetTop: { paddingHorizontal: 20, paddingBottom: 12 },
  grabber: {
    alignSelf: "center",
    width: 40,
    height: 5,
    borderRadius: 3,
    backgroundColor: "#E1E6EE",
    marginTop: 10,
    marginBottom: 14,
  },
  row: { flexDirection: "row", alignItems: "center", gap: 12 },
  droneIcon: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: "#2F9E62",
    alignItems: "center",
    justifyContent: "center",
  },
  eta: { fontFamily: "DDT-Bold", fontSize: 20, color: "#1F2A37", padding: 0 },
  sub: {
    fontFamily: "DDT-Regular",
    fontSize: 13,
    color: "#6B7686",
    marginTop: 2,
  },
  track: {
    height: 6,
    borderRadius: 3,
    backgroundColor: "#EDF1F6",
    marginTop: 14,
    overflow: "hidden",
  },
  fill: {
    height: 6,
    width: "100%",
    borderRadius: 3,
    backgroundColor: "#2F9E62",
    transformOrigin: "left",
  },
  details: { paddingHorizontal: 20, paddingTop: 6 },
  step: { flexDirection: "row", alignItems: "center", gap: 12, height: 50 },
  stepIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: "#EDF1F6",
    alignItems: "center",
    justifyContent: "center",
  },
  stepOn: { backgroundColor: "#2F9E62" },
  stepTitle: {
    flex: 1,
    fontFamily: "DDT-SemiBold",
    fontSize: 15,
    color: "#1F2A37",
  },
  stepTime: { fontFamily: "DDT-Regular", fontSize: 13, color: "#8A94A6" },
  actions: { flexDirection: "row", gap: 12, marginTop: 14 },
  action: {
    flex: 1,
    height: 50,
    borderRadius: 25,
    backgroundColor: "#EDF1F6",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  actionDark: { backgroundColor: "#1F2A37" },
  actionText: { fontFamily: "DDT-SemiBold", fontSize: 15, color: "#1F2A37" },
  pressed: { transform: [{ scale: 0.96 }] },
});
