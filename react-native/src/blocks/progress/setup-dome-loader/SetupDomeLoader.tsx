import { Ionicons } from "@expo/vector-icons";
import {
  BlurStyle,
  Canvas,
  createPicture,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import {
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

/*
 Setup Dome Loader — the "setting up your account" screen of an invented bank, Kinto.

 Look (a Pinterest video of a fintech app): a near-black page, a back arrow, the wordmark and a help
 button, then a glass cylinder of two arched panels standing on a lavender disc with a keyhole. A
 wallet, a pay card, a banknote and coins float round inside it while "We're setting up your
 account" waits underneath. When it is done the two panels swing shut into a single line of glass,
 the line runs down into the keyhole like a key, the keyhole lights green and a green check rises out
 of it on a green glow.

 Ours: the items orbit in depth (they pass behind and in front of the glass, turn on their own axes
 and bob); drag across the dome to spin the orbit faster, tap it to make everything hop. Three real
 setup steps tick off under the title with a progress bar. The collapse keeps one continuous line:
 panels close → line → bead drops → keyhole lights → check rises from the same keyhole.

 autoPlay: LOOP = 9 s — orbit to 6.4 s, panels close 6.4–7.0, the line drains 7.0–7.35, keyhole
 lights 7.35, check rises 7.45–7.95 and ticks 7.75–8.05, holds, then the check sinks back, the line
 grows up and the panels reopen with the items spiralling out by 9.0 (frame 9 = frame 0). Without
 autoPlay the setup runs once and holds on "You're all set" with Continue (which replays it).
 Reduced motion: items rest without orbiting; the finish crossfades.
*/

let debugState = "";

const LOOP = 9;
const ORBIT_W = (2 * Math.PI * 2) / LOOP; // two orbits per loop, so the loop closes
const STEPS = [
  "Verifying your details",
  "Creating your card",
  "Securing your wallet",
];

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const ramp = (t: number, a: number, b: number) => {
  "worklet";
  return clamp01((t - a) / (b - a));
};
const easeInOut = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
const easeOut = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return 1 - Math.pow(1 - x, 3);
};
const easeIn = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x;
};
const backOut = (k: number) => {
  "worklet";
  const x = clamp01(k);
  const c1 = 1.9;
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
const lerp = (a: number, b: number, k: number) => {
  "worklet";
  return a + (b - a) * k;
};

/** Every phase of the setup at time t (seconds into the loop). */
const phaseAt = (t: number) => {
  "worklet";
  const c =
    t < 8.75 ? easeInOut(ramp(t, 6.4, 7.0)) : 1 - easeOut(ramp(t, 8.75, 9.0));
  const b =
    t < 8.6 ? easeIn(ramp(t, 7.0, 7.35)) : 1 - easeOut(ramp(t, 8.6, 8.8));
  const k = t < 8.5 ? ramp(t, 7.3, 7.42) : 1 - ramp(t, 8.5, 8.65);
  const burst = t >= 7.35 && t < 7.95 ? (t - 7.35) / 0.6 : 0;
  const chk =
    t < 8.45 ? backOut(ramp(t, 7.45, 7.95)) : 1 - easeIn(ramp(t, 8.45, 8.65));
  const tick =
    t < 8.45 ? easeOut(ramp(t, 7.75, 8.05)) : 1 - ramp(t, 8.45, 8.55);
  const swap = t < 8.5 ? ramp(t, 7.4, 7.9) : 1 - ramp(t, 8.5, 8.9);
  const prog = t < 7.0 ? clamp01(t / 7.0) : t < 8.5 ? 1 : 1 - ramp(t, 8.5, 8.9);
  const step = t < 2.3 ? 0 : t < 4.6 ? 1 : 2;
  return { c, b, k, burst, chk, tick, swap, prog, step };
};

/** Items in the dome: kind, starting angle, height (0 bottom .. 1 top), size, own spin rate, hop delay */
const ITEMS = [
  { kind: 0, a: 0.0, h: 0.62, s: 1.0, spin: 1.0, d: 0.0 }, // wallet
  { kind: 1, a: 1.3, h: 0.45, s: 1.0, spin: -0.5, d: 0.06 }, // pay card
  { kind: 2, a: 2.6, h: 0.78, s: 1.0, spin: 1.5, d: 0.12 }, // banknote
  { kind: 3, a: 3.7, h: 0.3, s: 1.0, spin: 2.0, d: 0.03 }, // coin
  { kind: 3, a: 5.0, h: 0.56, s: 0.82, spin: -2.5, d: 0.09 }, // small coin
];

type Geo = { cx: number; by: number; R: number; Ry: number; H: number };

const paint = (color: string, alpha = 1, stroke = 0) => {
  "worklet";
  const p = Skia.Paint();
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  p.setAntiAlias(true);
  if (stroke > 0) {
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(stroke);
    p.setStrokeCap(StrokeCap.Round);
  }
  return p;
};

/** A glass panel: the part of the cylinder between angles ta and tb, with an arched top. */
const panelPath = (g: Geo, ta: number, tb: number) => {
  "worklet";
  const path = Skia.PathBuilder.Make();
  const n = 18;
  // the panel's screen width: its two edges and, if it passes the side, the cylinder's silhouette
  let x0 = g.cx + g.R * Math.cos(ta);
  let x1 = x0;
  for (let i = 0; i <= n; i++) {
    const x = g.cx + g.R * Math.cos(lerp(ta, tb, i / n));
    if (x < x0) x0 = x;
    if (x > x1) x1 = x;
  }
  const mid = (x0 + x1) / 2;
  const half = Math.max(0.5, (x1 - x0) / 2);
  const arch = half * 0.95;
  for (let i = 0; i <= n; i++) {
    const th = lerp(ta, tb, i / n);
    const x = g.cx + g.R * Math.cos(th);
    const y = g.by + g.Ry * Math.sin(th);
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  for (let i = n; i >= 0; i--) {
    const th = lerp(ta, tb, i / n);
    const x = g.cx + g.R * Math.cos(th);
    const k = clamp01(1 - Math.pow((x - mid) / half, 2));
    const y = g.by - g.H + arch - arch * Math.sqrt(k);
    path.lineTo(x, y);
  }
  path.close();
  return path.detach();
};

const drawPlatform = (cv: SkCanvas, g: Geo, lit: number) => {
  "worklet";
  const W2 = g.R * 1.42;
  const H2 = g.Ry * 1.42;
  const th = 16;
  // side
  const side = Skia.Path.Oval(
    Skia.XYWHRect(g.cx - W2, g.by - H2 + th, W2 * 2, H2 * 2),
  );
  cv.drawPath(side, paint("#4B4A9E"));
  cv.drawRect(Skia.XYWHRect(g.cx - W2, g.by, W2 * 2, th), paint("#4B4A9E"));
  // top
  const top = Skia.Paint();
  top.setAntiAlias(true);
  top.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: g.cx, y: g.by - H2 },
      { x: g.cx, y: g.by + H2 },
      [Skia.Color("#A9A7F5"), Skia.Color("#7C7AD9")],
      null,
      0,
    ),
  );
  cv.drawOval(Skia.XYWHRect(g.cx - W2, g.by - H2, W2 * 2, H2 * 2), top);
  cv.drawOval(
    Skia.XYWHRect(g.cx - W2 * 0.78, g.by - H2 * 0.78, W2 * 1.56, H2 * 1.56),
    paint("#C9C7FF", 0.35, 1.2),
  );
  cv.drawOval(
    Skia.XYWHRect(g.cx - W2 * 0.52, g.by - H2 * 0.52, W2 * 1.04, H2 * 1.04),
    paint("#C9C7FF", 0.22, 1),
  );
  // keyhole: a round head and a flared slot running toward the viewer
  const kc = lit > 0 ? "#3BE07A" : "#0B0B14";
  const key = Skia.PathBuilder.Make();
  key.addOval(Skia.XYWHRect(g.cx - 11, g.by - 7, 22, 12));
  key.moveTo(g.cx - 5, g.by + 1);
  key.lineTo(g.cx + 5, g.by + 1);
  key.lineTo(g.cx + 9, g.by + H2 * 0.62);
  key.lineTo(g.cx - 9, g.by + H2 * 0.62);
  key.close();
  const keyPath = key.detach();
  cv.drawPath(keyPath, paint("#0B0B14"));
  if (lit > 0) {
    cv.drawPath(keyPath, paint(kc, lit));
    const glow = paint("#3BE07A", 0.55 * lit);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    cv.drawPath(keyPath, glow);
  }
};

/** One floating item, centred at (0,0), size ~ 1 unit = u px, turned about its vertical axis by sx (cos). */
const drawItem = (
  cv: SkCanvas,
  kind: number,
  u: number,
  sx: number,
  tilt: number,
  alpha: number,
) => {
  "worklet";
  cv.save();
  cv.rotate(tilt, 0, 0);
  cv.scale(Math.max(0.08, Math.abs(sx)), 1);
  const back = sx < 0;
  if (kind === 0) {
    // wallet: a folded leather wallet with a stitched flap and a snap
    const r = Skia.RRectXY(
      Skia.XYWHRect(-u * 0.9, -u * 0.6, u * 1.8, u * 1.2),
      u * 0.18,
      u * 0.18,
    );
    cv.drawRRect(r, paint(back ? "#4A2A1A" : "#6B3D24", alpha));
    cv.drawRRect(
      Skia.RRectXY(
        Skia.XYWHRect(-u * 0.9, -u * 0.6, u * 1.8, u * 0.62),
        u * 0.18,
        u * 0.18,
      ),
      paint(back ? "#5A3320" : "#80492B", alpha),
    );
    cv.drawLine(
      -u * 0.75,
      u * 0.0,
      u * 0.75,
      u * 0.0,
      paint("#C08A5C", 0.55 * alpha, 1.2),
    );
    if (!back)
      cv.drawCircle(u * 0.55, u * 0.02, u * 0.11, paint("#E8C27A", alpha));
  } else if (kind === 1) {
    // pay card: gold front with a QR, plain back
    const r = Skia.RRectXY(
      Skia.XYWHRect(-u * 0.62, -u * 0.85, u * 1.24, u * 1.7),
      u * 0.14,
      u * 0.14,
    );
    cv.drawRRect(r, paint(back ? "#6E5320" : "#3A2A14", alpha));
    cv.drawRRect(r, paint("#E3B34E", alpha, 2));
    if (!back) {
      const cell = u * 0.17;
      for (let i = 0; i < 5; i++) {
        for (let j = 0; j < 5; j++) {
          const on =
            (i * 7 + j * 3 + i * j) % 3 !== 0 ||
            (i < 2 && j < 2) ||
            (i > 2 && j < 2) ||
            (i < 2 && j > 2);
          if (on)
            cv.drawRect(
              Skia.XYWHRect(
                -u * 0.43 + i * cell,
                -u * 0.43 + j * cell,
                cell * 0.82,
                cell * 0.82,
              ),
              paint("#F2C55E", alpha),
            );
        }
      }
    }
  } else if (kind === 2) {
    // banknote: a green note that flutters (its corners lift with tilt)
    const p = Skia.PathBuilder.Make();
    const f = 0.12 * Math.sin(tilt * 3);
    p.moveTo(-u * 0.9, -u * 0.45 + f * u);
    p.lineTo(u * 0.9, -u * 0.45 - f * u);
    p.lineTo(u * 0.9, u * 0.45 - f * u);
    p.lineTo(-u * 0.9, u * 0.45 + f * u);
    p.close();
    cv.drawPath(p.detach(), paint(back ? "#1E9E58" : "#2FCB72", alpha));
    cv.drawCircle(0, 0, u * 0.22, paint("#1B8A4C", alpha, 1.5));
  } else {
    // coin: orange with a rim, its edge showing when it turns side-on
    cv.drawCircle(0, 0, u * 0.48, paint("#C9581F", alpha));
    cv.drawCircle(0, 0, u * 0.44, paint(back ? "#E07030" : "#F28A3A", alpha));
    cv.drawCircle(0, 0, u * 0.28, paint("#FFB070", 0.7 * alpha, 1.6));
  }
  cv.restore();
};

const drawGlass = (
  cv: SkCanvas,
  g: Geo,
  a: number,
  b: number,
  c: number,
  d: number,
) => {
  "worklet";
  const glass = Skia.Paint();
  glass.setAntiAlias(true);
  glass.setColor(Skia.Color("#9C9BFF"));
  glass.setAlphaf(0.24);
  const rim = paint("#C3C2FF", 0.85, 2.2);
  const streak = paint("#E2E1FF", 0.28, 1.4);
  const L = panelPath(g, a, b);
  const Rr = panelPath(g, c, d);
  cv.drawPath(L, glass);
  cv.drawPath(Rr, glass);
  cv.drawPath(L, rim);
  cv.drawPath(Rr, rim);
  for (let s = 0; s < 3; s++) {
    const xl = g.cx + g.R * Math.cos(lerp(a, b, 0.18 + s * 0.12));
    const xr = g.cx + g.R * Math.cos(lerp(c, d, 0.7 + s * 0.1));
    cv.drawLine(xl, g.by - g.H * 0.82, xl, g.by - g.H * 0.18, streak);
    cv.drawLine(xr, g.by - g.H * 0.82, xr, g.by - g.H * 0.18, streak);
  }
};

type Out = { t: number; off: number; hop: number[]; clock: number };

const drawScene = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const ph = phaseAt(o.t);
  const u = g.R * 0.5;
  // green glow behind the check
  if (ph.chk > 0.01) {
    const gl = Skia.Paint();
    gl.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: g.cx, y: g.by - g.H * 0.62 },
        g.R * 2.4,
        [Skia.Color("rgba(59,224,122,0.32)"), Skia.Color("rgba(59,224,122,0)")],
        null,
        0,
      ),
    );
    gl.setAlphaf(clamp01(ph.chk));
    cv.drawRect(
      Skia.XYWHRect(
        g.cx - g.R * 2.6,
        g.by - g.H * 0.62 - g.R * 2.6,
        g.R * 5.2,
        g.R * 5.2,
      ),
      gl,
    );
  }
  // back wall between the panels: dark slats
  const open = 1 - ph.c;
  if (open > 0.01) {
    for (let i = 0; i < 6; i++) {
      const y = g.by - g.H + g.H * 0.12 + i * g.H * 0.13;
      cv.drawRect(
        Skia.XYWHRect(
          g.cx - g.R * 0.82 * open,
          y,
          g.R * 1.64 * open,
          g.H * 0.08,
        ),
        paint("#2B1830", 0.85 * open),
      );
    }
  }
  drawPlatform(cv, g, ph.k);
  // the burst when the key lands
  if (ph.burst > 0) {
    const r = lerp(0.3, 1.7, easeOut(ph.burst));
    cv.drawOval(
      Skia.XYWHRect(g.cx - g.R * r, g.by - g.Ry * r, g.R * 2 * r, g.Ry * 2 * r),
      paint("#3BE07A", 0.8 * (1 - ph.burst), 3),
    );
  }
  // items, back half first
  const items: { x: number; y: number; z: number; i: number }[] = [];
  for (let i = 0; i < ITEMS.length; i++) {
    const it = ITEMS[i];
    const r = 1 - ph.c;
    const phi = it.a + (reduced ? 0 : ORBIT_W * o.t + o.off) + ph.c * 2.4;
    const z = Math.sin(phi);
    const x = g.cx + g.R * 0.58 * r * Math.cos(phi);
    const bob = reduced
      ? 0
      : Math.sin(o.clock * ((2 * Math.PI * 3) / LOOP) + it.a * 2) * 4;
    const hy = lerp(it.h, 0.5, ph.c);
    const y = g.by - g.H * hy + g.Ry * 0.62 * r * z - bob - o.hop[i];
    items.push({ x, y, z, i });
  }
  const panelsA = lerp(Math.PI * 0.61, Math.PI * 0.5, ph.c);
  const panelsB = lerp(Math.PI * 1.39, Math.PI * 0.5, ph.c);
  const panelsC = lerp(-Math.PI * 0.39, Math.PI * 0.5, ph.c);
  const panelsD = lerp(Math.PI * 0.39, Math.PI * 0.5, ph.c);
  for (let n = 0; n < items.length; n++) {
    const p = items[n];
    if (p.z >= 0) continue;
    const it = ITEMS[p.i];
    cv.save();
    cv.translate(p.x, p.y);
    const sc = (0.86 + 0.16 * p.z) * it.s * (1 - 0.75 * ph.c);
    cv.scale(sc, sc);
    drawItem(
      cv,
      it.kind,
      u,
      Math.cos(o.clock * it.spin * ((2 * Math.PI) / LOOP) * 2 + it.a),
      Math.sin(o.clock * ((2 * Math.PI) / LOOP) + it.a) * 0.25,
      1 - ph.c * ph.c,
    );
    cv.restore();
  }
  if (ph.c < 0.995) drawGlass(cv, g, panelsA, panelsB, panelsC, panelsD);
  for (let n = 0; n < items.length; n++) {
    const p = items[n];
    if (p.z < 0) continue;
    const it = ITEMS[p.i];
    cv.save();
    cv.translate(p.x, p.y);
    const sc = (0.86 + 0.16 * p.z) * it.s * (1 - 0.75 * ph.c);
    cv.scale(sc, sc);
    drawItem(
      cv,
      it.kind,
      u,
      Math.cos(o.clock * it.spin * ((2 * Math.PI) / LOOP) * 2 + it.a),
      Math.sin(o.clock * ((2 * Math.PI) / LOOP) + it.a) * 0.25,
      1 - ph.c * ph.c,
    );
    cv.restore();
  }
  // the closed glass: one line that drains into the keyhole, a green bead riding its tip
  if (ph.c > 0.6 && ph.b < 0.999) {
    const top = lerp(g.by - g.H - g.R * 0.1, g.by - 2, ph.b);
    const a = clamp01((ph.c - 0.6) / 0.4);
    cv.drawLine(g.cx, top, g.cx, g.by - 2, paint("#C3C2FF", 0.9 * a, 2.4));
    if (ph.b > 0) {
      cv.drawCircle(g.cx, top, 4 + 6 * ph.b, paint("#3BE07A", 1));
      const glow = paint("#3BE07A", 0.5);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
      cv.drawCircle(g.cx, top, 8 + 6 * ph.b, glow);
    }
  }
  // the check rises out of the keyhole
  if (ph.chk > 0.005) {
    const e = ph.chk;
    const cy = lerp(g.by - 4, g.by - g.H * 0.62, Math.min(1, e));
    const r = lerp(8, g.R * 0.42, e);
    cv.drawCircle(g.cx, cy, r, paint("#2DD06B"));
    if (ph.tick > 0) {
      const tp = Skia.PathBuilder.Make();
      tp.moveTo(g.cx - r * 0.38, cy + r * 0.02);
      tp.lineTo(g.cx - r * 0.1, cy + r * 0.3);
      tp.lineTo(g.cx + r * 0.42, cy - r * 0.28);
      const sp = paint("#0E2416", 1, Math.max(2, r * 0.16));
      sp.setStrokeJoin(StrokeJoin.Round);
      const part = Skia.Path.Trim(tp.detach(), 0, ph.tick, false);
      if (part) cv.drawPath(part, sp);
    }
  }
};

type Props = { autoPlay?: boolean };

export function SetupDomeLoader({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "SDL-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "SDL-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "SDL-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const g: Geo = {
    cx: W / 2,
    by: Math.round(H * 0.47),
    R: Math.round(W * 0.25),
    Ry: Math.round(W * 0.25 * 0.3),
    H: Math.round(W * 0.25 * 1.75),
  };

  const t = useSharedValue(0);
  const clock = useSharedValue(0);
  const off = useSharedValue({ off: 0, v: 0 });
  const hops = useSharedValue({
    y: [0, 0, 0, 0, 0],
    v: [0, 0, 0, 0, 0],
    delay: [-1, -1, -1, -1, -1],
  });
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    t: 0,
    off: 0,
    hop: [0, 0, 0, 0, 0],
    clock: 0,
  });
  const doneBuzz = useSharedValue(0);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __setupDome?: unknown };
    gl.__setupDome = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__setupDome;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const success = () => {
    if (process.env.EXPO_OS === "web") return;
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(
      () => {},
    );
  };
  const light = () => {
    if (process.env.EXPO_OS === "web") return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    // the loop takes wall time; the touch physics a clamped step
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const dbg = debugT.get();
    let tt = t.get();
    if (dbg >= 0) tt = dbg;
    else if (autoPlay) tt = (tt + wall) % LOOP;
    else tt = Math.min(tt + wall, 8.3);
    t.set(tt);
    if (dbg < 0 && !autoPlay && tt >= 7.35 && doneBuzz.get() === 0) {
      doneBuzz.set(1);
      scheduleOnRN(success);
    }
    const o = off.get();
    o.v *= Math.exp(-1.4 * dt);
    o.off += o.v * dt;
    const hp = hops.get();
    for (let i = 0; i < 5; i++) {
      if (hp.delay[i] >= 0) {
        hp.delay[i] -= dt;
        if (hp.delay[i] < 0) {
          hp.v[i] = 260;
          hp.delay[i] = -1;
        }
      }
      hp.v[i] -= 1400 * dt;
      hp.y[i] += hp.v[i] * dt;
      if (hp.y[i] < 0) {
        hp.y[i] = 0;
        hp.v[i] = Math.abs(hp.v[i]) > 60 ? -hp.v[i] * 0.35 : 0;
      }
    }
    out.set({
      t: tt,
      off: o.off,
      hop: [hp.y[0], hp.y[1], hp.y[2], hp.y[3], hp.y[4]],
      clock: dbg >= 0 ? dbg : now,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `t=${tt.toFixed(2)} off=${o.off.toFixed(2)} spin=${o.v.toFixed(2)} hop0=${hp.y[0].toFixed(1)}`,
      );
    }
  });

  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .onUpdate((e) => {
      const o = off.get();
      o.v = Math.max(-9, Math.min(9, o.v + e.velocityX * 0.00012));
    })
    .onEnd((e) => {
      const o = off.get();
      o.v = Math.max(-9, Math.min(9, e.velocityX * 0.006));
    });
  const tap = Gesture.Tap().onEnd(() => {
    const hp = hops.get();
    for (let i = 0; i < 5; i++) hp.delay[i] = ITEMS[i].d;
    scheduleOnRN(light);
  });
  const gesture = Gesture.Race(pan, tap);

  const restart = () => {
    "worklet";
    t.set(0);
    doneBuzz.set(0);
  };
  const onContinue = () => {
    light();
    scheduleOnUI(restart);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => {
      drawScene(cv, g, out.get(), reduced);
    }),
  );
  const tval = useDerivedValue(() => out.get().t);

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>

      <View style={[styles.header, { top: insets.top + 4 }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back"
          style={styles.headBtn}
          hitSlop={6}
        >
          <Ionicons name="arrow-back" size={22} color="#E9E8F5" />
        </Pressable>
        <View style={styles.brand} accessible accessibilityLabel="Kinto">
          <Text style={styles.brandText}>kinto</Text>
          <View style={styles.brandMark} />
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Help"
          style={styles.headBtn}
          hitSlop={6}
        >
          <Ionicons name="help-circle-outline" size={23} color="#E9E8F5" />
        </Pressable>
      </View>

      <GestureDetector gesture={gesture}>
        <View
          style={[
            styles.touch,
            { top: g.by - g.H - g.R * 0.5, height: g.H + g.R },
          ]}
          accessible
          accessibilityRole="image"
          accessibilityLabel="Your wallet, card and money floating in a glass case while the account is set up"
        />
      </GestureDetector>

      <View
        style={[styles.copy, { top: g.by + g.Ry * 1.42 + 52 }]}
        pointerEvents="box-none"
      >
        <Swap t={tval} which={0}>
          <Text style={styles.title} accessibilityRole="header">
            {"We're setting up\nyour account"}
          </Text>
          <Steps t={tval} />
        </Swap>
        <Swap t={tval} which={1}>
          <Text style={styles.title} accessibilityRole="header">
            {"You're all set"}
          </Text>
          <Text style={styles.sub}>Your card and wallet are ready to use.</Text>
        </Swap>
      </View>

      <ContinueButton
        t={tval}
        bottom={insets.bottom + 16}
        onPress={onContinue}
      />
    </View>
  );
}

function Swap({
  t,
  which,
  children,
}: {
  t: SharedValue<number>;
  which: 0 | 1;
  children: React.ReactNode;
}) {
  const st = useAnimatedStyle(() => {
    const s = phaseAt(t.get()).swap;
    // sequential: the old copy leaves over the first half, the new one arrives over the second
    const v = which === 0 ? 1 - clamp01(s * 2) : clamp01(s * 2 - 1);
    return {
      opacity: v,
      transform: [{ translateY: (1 - v) * (which === 0 ? -8 : 10) }],
    };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.center, st]}>
      {children}
    </Animated.View>
  );
}

function Steps({ t }: { t: SharedValue<number> }) {
  const bar = useAnimatedStyle(() => ({
    transform: [{ scaleX: phaseAt(t.get()).prog }],
  }));
  return (
    <View style={styles.steps}>
      {STEPS.map((s, i) => (
        <StepRow key={s} t={t} i={i} label={s} />
      ))}
      <View style={styles.track}>
        <Animated.View style={[styles.fill, bar]} />
      </View>
      <Text style={styles.note}>This can take up to 2 minutes</Text>
    </View>
  );
}

function StepRow({
  t,
  i,
  label,
}: {
  t: SharedValue<number>;
  i: number;
  label: string;
}) {
  const done = useAnimatedStyle(() => {
    const tt = t.get();
    const end = [2.3, 4.6, 7.0][i];
    // the ticks fade out as the loop resets, so its last frame meets its first
    const d = tt >= end ? 1 - ramp(tt, 8.5, 8.9) : 0;
    return { opacity: d };
  });
  const active = useAnimatedStyle(() => {
    const tt = t.get();
    const start = [0, 2.3, 4.6][i];
    const end = [2.3, 4.6, 7.0][i];
    const on = tt >= start && tt < end;
    const reset = (i === 0 ? 1 : 0.4) - 0.9;
    return {
      opacity: on ? 1 : tt >= end ? 0.9 + reset * ramp(tt, 8.5, 8.9) : 0.4,
    };
  });
  return (
    <Animated.View style={[styles.stepRow, active]}>
      <View style={styles.stepIcon}>
        <View style={styles.stepDot} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.center, done]}>
          <Ionicons name="checkmark-circle" size={18} color="#3BE07A" />
        </Animated.View>
      </View>
      <Text style={styles.stepText}>{label}</Text>
    </Animated.View>
  );
}

function ContinueButton({
  t,
  bottom,
  onPress,
}: {
  t: SharedValue<number>;
  bottom: number;
  onPress: () => void;
}) {
  const st = useAnimatedStyle(() => {
    const s = phaseAt(t.get()).swap;
    const v = clamp01(s * 2 - 1);
    return { opacity: v, transform: [{ translateY: (1 - v) * 16 }] };
  });
  return (
    <Animated.View style={[styles.ctaWrap, { bottom }, st]}>
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel="Continue"
        style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
      >
        <Text style={styles.ctaText}>Continue</Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#0D0D12" },
  center: { alignItems: "center" },
  header: {
    position: "absolute",
    left: 12,
    right: 12,
    height: 44,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  headBtn: {
    width: 44,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  brand: { flexDirection: "row", alignItems: "flex-start" },
  brandText: {
    fontFamily: "SDL-Bold",
    fontSize: 19,
    color: "#F4F3FA",
    letterSpacing: -0.4,
  },
  brandMark: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: "#F6B73C",
    marginLeft: 2,
    marginTop: 4,
  },
  touch: { position: "absolute", left: 24, right: 24 },
  copy: { position: "absolute", left: 24, right: 24, height: 230 },
  title: {
    fontFamily: "SDL-SemiBold",
    fontSize: 22,
    lineHeight: 29,
    color: "#F4F3FA",
    textAlign: "center",
  },
  sub: {
    marginTop: 10,
    fontFamily: "SDL-Regular",
    fontSize: 15,
    lineHeight: 21,
    color: "#9E9DB5",
    textAlign: "center",
  },
  steps: { marginTop: 20, width: 260 },
  stepRow: { flexDirection: "row", alignItems: "center", gap: 10, height: 28 },
  stepIcon: {
    width: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
  },
  stepDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: "#8E8DB0",
  },
  stepText: { fontFamily: "SDL-Regular", fontSize: 15, color: "#D9D8EA" },
  track: {
    marginTop: 14,
    height: 4,
    borderRadius: 2,
    backgroundColor: "rgba(255,255,255,0.08)",
    overflow: "hidden",
  },
  fill: {
    height: 4,
    width: "100%",
    borderRadius: 2,
    backgroundColor: "#8B89F0",
    transformOrigin: "left",
  },
  note: {
    marginTop: 10,
    fontFamily: "SDL-Regular",
    fontSize: 13,
    color: "#6F6E86",
    textAlign: "center",
  },
  ctaWrap: { position: "absolute", left: 20, right: 20 },
  cta: {
    height: 54,
    borderRadius: 27,
    backgroundColor: "#F4F3FA",
    alignItems: "center",
    justifyContent: "center",
  },
  ctaText: { fontFamily: "SDL-SemiBold", fontSize: 17, color: "#0D0D12" },
  pressed: { transform: [{ scale: 0.96 }] },
});
