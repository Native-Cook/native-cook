import { Ionicons } from "@expo/vector-icons";
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
 Fridge Door Grocery — the "what's in the fridge" screen of an invented grocery app, Pantry.

 Look (Pinterest fridges and grocery app shots): a mint retro fridge with a chrome handle on a warm
 kitchen wall.

 Ours: the fridge is the stock list. Drag the handle and the door swings open on its hinge in
 perspective (the free edge grows as it comes toward you, then its inside shelf of sauces shows), the
 light comes on and the shelves fill in from the back. Items running low pulse with a red ring and a
 "Low" tag; tap one and it flies into the basket. Let go near shut and the door snaps closed on its
 magnet with a little bounce.

 autoPlay: LOOP = 9 s — closed, opens 1.0–1.9 (with overshoot), milk then eggs fly to the basket
 2.5–3.9, the door closes and snaps 6.2–7.0, the basket is saved to the list by 8.2 (frame 9 = frame 0).
 Reduced motion: the door fades open and shut; items appear without flying.
*/

let debugState = "";

const LOOP = 9;
const ITEMS = [
  { kind: 0, shelf: 0, x: 0.18, low: true, name: "Milk" },
  { kind: 1, shelf: 0, x: 0.55, low: false, name: "Juice" },
  { kind: 2, shelf: 1, x: 0.22, low: true, name: "Eggs" },
  { kind: 3, shelf: 1, x: 0.66, low: false, name: "Cheese" },
  { kind: 4, shelf: 2, x: 0.2, low: false, name: "Broccoli" },
  { kind: 5, shelf: 2, x: 0.6, low: false, name: "Carrots" },
  { kind: 6, shelf: 3, x: 0.3, low: false, name: "Yogurt" },
  { kind: 7, shelf: 3, x: 0.7, low: false, name: "Jam" },
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
const backOut = (k: number) => {
  "worklet";
  const x = clamp01(k);
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
};
const scriptAt = (t: number) => {
  "worklet";
  let a = 0;
  if (t >= 1.0 && t < 1.9) a = backOut((t - 1.0) / 0.9);
  else if (t >= 1.9 && t < 6.2) a = 1;
  else if (t >= 6.2 && t < 7.0) {
    const k = (t - 6.2) / 0.8;
    a =
      1 -
      smoother(k) +
      (k > 0.85 ? Math.sin(((k - 0.85) / 0.15) * Math.PI) * 0.03 : 0);
  }
  const fly0 = t >= 2.5 && t < 8.2 ? clamp01((t - 2.5) / 0.7) : 0;
  const fly2 = t >= 3.2 && t < 8.2 ? clamp01((t - 3.2) / 0.7) : 0;
  const bag = (fly0 >= 1 ? 1 : 0) + (fly2 >= 1 ? 1 : 0);
  return { a, fly: [fly0, 0, fly2, 0, 0, 0, 0, 0], bag };
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
const quad = (cv: SkCanvas, pts: number[], color: string, a = 1) => {
  "worklet";
  const b = Skia.PathBuilder.Make();
  b.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) b.lineTo(pts[i], pts[i + 1]);
  b.close();
  cv.drawPath(b.detach(), paint(color, a));
};

const drawFood = (cv: SkCanvas, kind: number, u: number) => {
  "worklet";
  if (kind === 0) {
    cv.drawRect(
      Skia.XYWHRect(-u * 0.32, -u * 0.9, u * 0.64, u * 0.9),
      paint("#FFFFFF"),
    );
    quad(
      cv,
      [-u * 0.32, -u * 0.9, 0, -u * 1.15, u * 0.32, -u * 0.9],
      "#E9EEF5",
    );
    cv.drawRect(
      Skia.XYWHRect(-u * 0.32, -u * 0.55, u * 0.64, u * 0.22),
      paint("#5AA9FF"),
    );
  } else if (kind === 1) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.25, -u * 1.0, u * 0.5, u * 1.0),
        u * 0.12,
        u * 0.12,
      ),
      paint("#FFB23D"),
    );
    cv.drawRect(
      Skia.XYWHRect(-u * 0.12, -u * 1.15, u * 0.24, u * 0.18),
      paint("#2F9E62"),
    );
  } else if (kind === 2) {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.7, -u * 0.3, u * 1.4, u * 0.3),
        u * 0.06,
        u * 0.06,
      ),
      paint("#C9B9A0"),
    );
    for (let i = 0; i < 4; i++)
      cv.drawOval(
        Skia.XYWHRect(-u * 0.62 + i * u * 0.32, -u * 0.6, u * 0.26, u * 0.34),
        paint("#FFF2DE"),
      );
  } else if (kind === 3) {
    quad(
      cv,
      [-u * 0.6, 0, u * 0.6, 0, u * 0.6, -u * 0.38, -u * 0.6, -u * 0.12],
      "#FFD54A",
    );
    cv.drawCircle(-u * 0.2, -u * 0.12, u * 0.07, paint("#E8B52E"));
    cv.drawCircle(u * 0.25, -u * 0.2, u * 0.09, paint("#E8B52E"));
  } else if (kind === 4) {
    cv.drawRect(
      Skia.XYWHRect(-u * 0.08, -u * 0.4, u * 0.16, u * 0.4),
      paint("#7CC46A"),
    );
    for (let i = 0; i < 4; i++)
      cv.drawCircle(
        -u * 0.25 + (i % 2) * u * 0.5 - (i > 1 ? 0 : 0),
        -u * 0.55 - (i > 1 ? u * 0.18 : 0),
        u * 0.22,
        paint(i % 2 ? "#3F9E4E" : "#4FB35C"),
      );
  } else if (kind === 5) {
    for (let i = 0; i < 3; i++) {
      cv.save();
      cv.translate(-u * 0.3 + i * u * 0.3, 0);
      cv.rotate(-10 + i * 10, 0, 0);
      quad(cv, [-u * 0.1, -u * 0.8, u * 0.1, -u * 0.8, 0, 0], "#FF8A3D");
      cv.drawCircle(0, -u * 0.86, u * 0.08, paint("#4FB35C"));
      cv.restore();
    }
  } else if (kind === 6) {
    for (let i = 0; i < 2; i++) {
      quad(
        cv,
        [
          -u * 0.5 + i * u * 0.55,
          -u * 0.5,
          -u * 0.08 + i * u * 0.55,
          -u * 0.5,
          -u * 0.12 + i * u * 0.55,
          0,
          -u * 0.46 + i * u * 0.55,
          0,
        ],
        i ? "#FFB8C8" : "#C9B8FF",
      );
      cv.drawRect(
        Skia.XYWHRect(-u * 0.52 + i * u * 0.55, -u * 0.56, u * 0.46, u * 0.08),
        paint("#FFFFFF"),
      );
    }
  } else {
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.28, -u * 0.6, u * 0.56, u * 0.6),
        u * 0.1,
        u * 0.1,
      ),
      paint("#D9435B"),
    );
    cv.drawRect(
      Skia.XYWHRect(-u * 0.3, -u * 0.72, u * 0.6, u * 0.14),
      paint("#F2D9A0"),
    );
    cv.drawRect(
      Skia.XYWHRect(-u * 0.2, -u * 0.38, u * 0.4, u * 0.18),
      paint("#FFFFFF", 0.85),
    );
  }
};

type Geo = {
  x: number;
  y: number;
  w: number;
  h: number;
  bagX: number;
  bagY: number;
};
type Out = { a: number; fly: number[]; t: number };

const drawFridge = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  font: SkFont | null,
  reduced: boolean,
) => {
  "worklet";
  const { x, y, w, h } = g;
  // wall shadow and feet
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x + 10, y + 14, w, h), 30, 30),
    paint("#8A6A4A", 0.12),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x + w * 0.12, y + h - 6, 24, 16), 6, 6),
    paint("#4A5560"),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x + w * 0.88 - 24, y + h - 6, 24, 16), 6, 6),
    paint("#4A5560"),
  );
  // body and the lit interior
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 30, 30),
    paint("#8FD3C1"),
  );
  const open = clamp01(o.a);
  const ix = x + 14;
  const iy = y + 14;
  const iw = w - 28;
  const ih = h - 28;
  const lit = Skia.Paint();
  lit.setAntiAlias(true);
  lit.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: iy },
      { x: 0, y: iy + ih },
      [Skia.Color("#FFFDF4"), Skia.Color("#EAF3F6")],
      null,
      0,
    ),
  );
  cv.drawRRect(Skia.RRectXY(Skia.XYWHRect(ix, iy, iw, ih), 20, 20), lit);
  // shelves and food, filling in from the back as the light comes on
  const shelfH = ih / 4;
  for (let sIdx = 0; sIdx < 4; sIdx++) {
    const sy = iy + shelfH * (sIdx + 1) - 10;
    cv.drawRect(Skia.XYWHRect(ix + 6, sy, iw - 12, 6), paint("#CFE3EA"));
  }
  for (let i = 0; i < ITEMS.length; i++) {
    const it = ITEMS[i];
    const appear = reduced
      ? open > 0.5
        ? 1
        : 0
      : smoother(open * 1.6 - 0.3 - it.shelf * 0.08);
    const fl = o.fly[i];
    if (fl >= 1) continue;
    const bx = ix + iw * it.x;
    const by = iy + shelfH * (it.shelf + 1) - 10;
    const fx = bx + (g.bagX - bx) * smoother(fl);
    const fy = by + (g.bagY - by) * smoother(fl) - Math.sin(Math.PI * fl) * 90;
    cv.save();
    cv.translate(fx, fy + (1 - appear) * 12);
    const sc = (0.85 + 0.15 * appear) * (1 - 0.6 * fl);
    cv.scale(sc, sc);
    if (it.low && fl === 0 && appear > 0.9) {
      const pulse = reduced
        ? 0.5
        : 0.5 + 0.5 * Math.sin((o.t * 2 * Math.PI * 3) / LOOP);
      cv.drawCircle(
        0,
        -shelfH * 0.3,
        shelfH * (0.3 + 0.05 * pulse),
        paint("#FF5A4E", 0.08 + 0.12 * pulse),
      );
    }
    const lay = Skia.Paint();
    lay.setAlphaf(appear);
    cv.saveLayer(lay);
    drawFood(cv, it.kind, shelfH * 0.55);
    cv.restore();
    if (it.low && fl === 0 && font && appear > 0.9) {
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(-20, 2, 40, 18), 9, 9),
        paint("#FF5A4E"),
      );
      const tw = font.measureText("Low").width;
      cv.drawText("Low", -tw / 2, 15, paint("#FFFFFF"), font);
    }
    cv.restore();
  }
  // the door, hinged on the left: closed it covers the inside; open it swings toward you
  const ang = reduced ? (open > 0.5 ? 1.95 : 0) : o.a * 1.95;
  const cosA = Math.cos(ang);
  const sinA = Math.sin(ang);
  const near = 1 + 0.16 * sinA;
  const hx = x;
  const ex = x + w * cosA;
  const topL = y;
  const botL = y + h;
  const topR = y - (h * (near - 1)) / 2;
  const botR = y + h + (h * (near - 1)) / 2;
  const fade = reduced ? 1 - open : 1;
  if (cosA > 0) {
    // outside of the door
    quad(cv, [hx, topL, ex, topR, ex, botR, hx, botL], "#9FDCCB", fade);
    quad(
      cv,
      [hx, topL + 4, ex, topR + 4, ex, topR + 10, hx, topL + 10],
      "#C6EEE2",
      fade * 0.8,
    );
    const hxp = hx + (ex - hx) * 0.86;
    const hy0 = topL + (topR - topL) * 0.86 + h * 0.3;
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(hxp - 5, hy0, 10 * Math.max(0.3, cosA), h * 0.24),
        5,
        5,
      ),
      paint("#DDE3EA", fade),
    );
    // a fridge magnet
    cv.drawCircle(
      hx + (ex - hx) * 0.3,
      topL + h * 0.18,
      12 * Math.max(0.3, cosA),
      paint("#FF8A65", fade),
    );
  } else {
    // the inside of the door with a sauce shelf
    quad(cv, [hx, topL, ex, topR, ex, botR, hx, botL], "#DDF1EA");
    for (let sh = 0; sh < 3; sh++) {
      const k = 0.28 + sh * 0.28;
      quad(
        cv,
        [
          hx,
          topL + h * k,
          ex,
          topR + (botR - topR) * k,
          ex,
          topR + (botR - topR) * (k + 0.05),
          hx,
          topL + h * (k + 0.05),
        ],
        "#CFE3EA",
      );
    }
  }
};

type Props = { autoPlay?: boolean };

export function FridgeDoorGrocery({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "FDG-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "FDG-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });
  const font = useFont(require("./assets/fonts/Nunito-Black.ttf"), 11);

  const fw = Math.round(W * 0.62);
  const g: Geo = {
    x: Math.round((W - fw) / 2) - 10,
    y: insets.top + 120,
    w: fw,
    h: Math.round(H * 0.56),
    bagX: W - 52,
    bagY: insets.top + 30,
  };

  const st = useSharedValue({
    a: 0,
    v: 0,
    target: 0,
    drag: 0,
    a0: 0,
    fly: [0, 0, 0, 0, 0, 0, 0, 0],
    flyOn: [0, 0, 0, 0, 0, 0, 0, 0],
    bag: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    snapped: 1,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    a: 0,
    fly: [0, 0, 0, 0, 0, 0, 0, 0],
    t: 0,
  });
  const bagN = useSharedValue(0);
  const [bag, setBag] = useState(0);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __fridge?: unknown };
    gl.__fridge = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__fridge;
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
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
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
      out.set({ a: sc.a, fly: sc.fly, t: dbg });
      bagN.set(sc.bag);
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.a < 0.001 && s.target === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.a = sc.a;
      s.target = sc.a > 0.5 ? 1 : 0;
      out.set({ a: s.a, fly: sc.fly, t: now });
      bagN.set(sc.bag);
      return;
    }
    s.scripted = 0;
    if (s.drag === 0) {
      const k = 120;
      const c = 2 * 0.62 * Math.sqrt(k);
      s.v += (k * (s.target - s.a) - c * s.v) * dt;
      s.a += s.v * dt;
      if (s.a < 0) {
        s.a = 0;
        if (s.snapped === 0) {
          s.snapped = 1;
          s.v = Math.abs(s.v) * 0.2;
          scheduleOnRN(buzz, 1);
        }
      }
      if (s.a > 0.05) s.snapped = 0;
    }
    for (let i = 0; i < 8; i++) {
      if (s.flyOn[i] === 1 && s.fly[i] < 1) {
        s.fly[i] = Math.min(1, s.fly[i] + dt / 0.7);
        if (s.fly[i] >= 1) {
          s.bag += 1;
          scheduleOnRN(buzz, 2);
        }
      }
    }
    bagN.set(s.bag);
    out.set({
      a: s.a,
      fly: [
        s.fly[0],
        s.fly[1],
        s.fly[2],
        s.fly[3],
        s.fly[4],
        s.fly[5],
        s.fly[6],
        s.fly[7],
      ],
      t: now,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `a=${s.a.toFixed(2)} target=${s.target} bag=${s.bag} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => bagN.get(),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setBag, v);
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart(() => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.a0 = s.a;
      s.v = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      s.a = clamp01(s.a0 - e.translationX / (g.w * 1.1));
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      s.target =
        e.velocityX < -400 ? 1 : e.velocityX > 400 ? 0 : s.a > 0.35 ? 1 : 0;
      s.v = -e.velocityX / (g.w * 1.1);
      s.lastTouch = clock.get();
    });
  const tap = Gesture.Tap().onEnd((e) => {
    const s = st.get();
    s.lastTouch = clock.get();
    s.scripted = 0;
    if (s.a < 0.6) {
      s.target = 1;
      return;
    }
    const ix = g.x + 14;
    const iy = g.y + 14;
    const iw = g.w - 28;
    const shelfH = (g.h - 28) / 4;
    for (let i = 0; i < ITEMS.length; i++) {
      const it = ITEMS[i];
      const bx = ix + iw * it.x;
      const by = iy + shelfH * (it.shelf + 1) - 10 - shelfH * 0.3;
      if (
        Math.abs(e.absoluteX - bx) < shelfH * 0.45 &&
        Math.abs(e.absoluteY - by) < shelfH * 0.45 &&
        s.flyOn[i] === 0
      ) {
        s.flyOn[i] = 1;
        scheduleOnRN(buzz, 0);
      }
    }
  });
  const gesture = Gesture.Race(pan, tap);

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawFridge(cv, g, out.get(), font, reduced)),
  );
  // the basket pops as each item lands in it
  const bagPop = useAnimatedStyle(() => {
    const f = out.get().fly;
    let k = 0;
    for (let i = 0; i < 8; i++)
      if (f[i] > 0.85 && f[i] < 1)
        k = Math.max(k, Math.sin(((f[i] - 0.85) / 0.15) * Math.PI));
    return { transform: [{ scale: 1 + 0.18 * k }] };
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.header, { top: insets.top + 8 }]}>
        <View>
          <Text style={styles.small}>Pantry</Text>
          <Text style={styles.title}>Your fridge</Text>
        </View>
        <Animated.View
          style={[styles.bag, bagPop]}
          accessible
          accessibilityLabel={`Basket, ${bag} items`}
        >
          <Ionicons name="basket" size={22} color="#2A2A33" />
          {bag > 0 ? (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{bag}</Text>
            </View>
          ) : null}
        </Animated.View>
      </View>
      <GestureDetector gesture={gesture}>
        <View
          style={[
            styles.hit,
            { top: g.y, left: g.x - 30, width: g.w + 80, height: g.h },
          ]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Fridge. Drag the door to open it, tap an item to add it to the basket. Milk and eggs are running low"
        />
      </GestureDetector>
      <View style={[styles.note, { bottom: insets.bottom + 20 }]}>
        <Ionicons name="alert-circle" size={20} color="#FF5A4E" />
        <Text style={styles.noteText}>
          2 items running low · tap to restock
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FBEFE2" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  small: { fontFamily: "FDG-Bold", fontSize: 14, color: "#A08A72" },
  title: { fontFamily: "FDG-Black", fontSize: 28, color: "#2A2A33" },
  bag: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  badge: {
    position: "absolute",
    top: -2,
    right: -2,
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: "#FF5A4E",
    alignItems: "center",
    justifyContent: "center",
  },
  badgeText: { fontFamily: "FDG-Black", fontSize: 11, color: "#FFFFFF" },
  hit: { position: "absolute" },
  note: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 56,
    borderRadius: 20,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingHorizontal: 16,
  },
  noteText: { fontFamily: "FDG-Bold", fontSize: 15, color: "#2A2A33" },
});
