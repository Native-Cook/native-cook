import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  LinearGradient,
  Picture,
  Rect,
  Skia,
  useFont,
  vec,
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
 Dice Board Rewards — the "Lucky Lap" mini game of an invented shopping app, Basketly.

 Look (Pinterest shots of shopping apps' board games): an isometric loop of pastel tiles with coins,
 gifts and coupons on them, a pawn on the start tile, a die in a tray and a "rolls left" counter.

 Ours: flick the die up off its tray and it is a real cube in flight — it tumbles on two axes along an
 arc, lands in the middle of the board with a bounce and settles on its number (the spin always ends
 exactly on the rolled face because it adds whole turns to the face's own orientation). The pawn then
 hops tile by tile with a squash on every landing and a tick per hop, and the tile it stops on flips
 over to show the reward, which drops in as a coupon. Tap Roll for a quick throw.

 autoPlay: LOOP = 9 s — throw at 0.8 s lands a 5 (hops 2.0–3.8 s, the tile flips to "10% OFF"), the
 die flies home, throw at 4.6 s lands a 7th… a 6 and a 1 would not close the loop, so the second roll
 is a 7-step "lucky double" (3 + 4) that brings the pawn round to Start for the lap bonus at 8.3 s.
 Frame 9 = frame 0. Reduced motion: the die appears on its number without tumbling and the pawn slides.
*/

let debugState = "";

const LOOP = 9;
const HOP = 0.3;
const PATH: [number, number][] = [
  [3, 3],
  [3, 2],
  [3, 1],
  [3, 0],
  [2, 0],
  [1, 0],
  [0, 0],
  [0, 1],
  [0, 2],
  [0, 3],
  [1, 3],
  [2, 3],
];
const N = PATH.length;
const TILES = [
  { kind: "start", label: "START", c: "#FFD95A" },
  { kind: "coin", label: "+20", c: "#BFE7FF" },
  { kind: "gift", label: "", c: "#FFC7D9" },
  { kind: "coin", label: "+50", c: "#C9F2C7" },
  { kind: "star", label: "", c: "#E3D4FF" },
  { kind: "coin", label: "+20", c: "#BFE7FF" },
  { kind: "coupon", label: "10%", c: "#FFE2B8" },
  { kind: "gift", label: "", c: "#FFC7D9" },
  { kind: "coin", label: "+80", c: "#C9F2C7" },
  { kind: "star", label: "", c: "#E3D4FF" },
  { kind: "coupon", label: "5%", c: "#FFE2B8" },
  { kind: "coin", label: "+30", c: "#BFE7FF" },
];
const PRIZE: Record<number, string> = {
  0: "Lap bonus · +100 coins",
  1: "+20 coins",
  2: "Mystery gift",
  3: "+50 coins",
  4: "Lucky star · roll again",
  5: "+20 coins",
  6: "10% OFF your basket",
  7: "Mystery gift",
  8: "+80 coins",
  9: "Lucky star · roll again",
  10: "5% OFF your basket",
  11: "+30 coins",
};
// face orientations (rx, rz) that put each value on top
const FACE_ROT: [number, number][] = [
  [0, 0],
  [0, 0],
  [Math.PI / 2, 0],
  [0, -Math.PI / 2],
  [0, Math.PI / 2],
  [-Math.PI / 2, 0],
  [Math.PI, 0],
];

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const easeOut = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return 1 - Math.pow(1 - x, 3);
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};

type Throw = { at: number; value: number; steps: number; from: number };
/** One roll's state at time u since the throw. */
const rollAt = (th: Throw, u: number) => {
  "worklet";
  // flight 0..0.95 s, bounce, rest; flies home after the hops
  const fl = clamp01(u / 0.95);
  const hopsEnd = 1.15 + th.steps * HOP;
  const home = clamp01((u - hopsEnd - 0.5) / 0.45);
  const spin = 1 - easeOut(fl);
  const rx = FACE_ROT[th.value][0] + spin * Math.PI * 2 * 3;
  const rz = FACE_ROT[th.value][1] + spin * Math.PI * 2 * 2;
  const bounce =
    u > 0.95 && u < 1.25 ? Math.sin(((u - 0.95) / 0.3) * Math.PI) * 0.18 : 0;
  const hopU = (u - 1.15) / HOP;
  const hopsDone = clamp01(hopU / th.steps) * th.steps;
  const pawn =
    hopU <= 0
      ? th.from
      : hopU >= th.steps
        ? th.from + th.steps
        : th.from + Math.floor(hopU) + smoother(hopU - Math.floor(hopU));
  // the tile turns over to show its prize, then turns back once the toast has gone (0..2)
  const flip =
    clamp01((u - hopsEnd) / 0.35) + clamp01((u - hopsEnd - 1.1) / 0.35);
  const toast =
    u > hopsEnd + 0.2
      ? clamp01((u - hopsEnd - 0.2) / 0.3) *
        (1 - clamp01((u - hopsEnd - 0.9) / 0.2))
      : 0;
  return { fl, rx, rz, bounce, home, pawn, flip, toast, hopsDone };
};
const SCRIPT: Throw[] = [
  { at: 0.6, value: 6, steps: 6, from: 0 },
  { at: 4.6, value: 6, steps: 6, from: 6 },
];
const NO_THROW: Throw = { at: -10, value: 1, steps: 0, from: 0 };
const scriptAt = (t: number) => {
  "worklet";
  if (t < SCRIPT[0].at) {
    return { th: SCRIPT[0], u: -1, prev: NO_THROW, uPrev: -1 };
  }
  // the first roll keeps playing out (its tile turns back) after the second is thrown
  if (t >= SCRIPT[1].at)
    return {
      th: SCRIPT[1],
      u: t - SCRIPT[1].at,
      prev: SCRIPT[0],
      uPrev: t - SCRIPT[0].at,
    };
  return { th: SCRIPT[0], u: t - SCRIPT[0].at, prev: NO_THROW, uPrev: -1 };
};

type Geo = {
  cx: number;
  top: number;
  TW: number;
  TH: number;
  trayX: number;
  trayY: number;
};
const iso = (g: Geo, gx: number, gy: number) => {
  "worklet";
  return {
    x: g.cx + (gx - gy) * g.TW * 0.5,
    y: g.top + (gx + gy) * g.TH * 0.5,
  };
};
const tileAt = (g: Geo, idx: number) => {
  "worklet";
  const i = ((Math.floor(idx) % N) + N) % N;
  const f = idx - Math.floor(idx);
  const a = PATH[i];
  const b = PATH[(i + 1) % N];
  const p = iso(g, a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f);
  return { x: p.x, y: p.y + g.TH * 0.5 };
};
const paint = (c: string, a = 1) => {
  "worklet";
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(c));
  p.setAlphaf(a);
  return p;
};
const shade = (c: string, k: number) => {
  "worklet";
  const r = parseInt(c.slice(1, 3), 16) * k;
  const gg = parseInt(c.slice(3, 5), 16) * k;
  const b = parseInt(c.slice(5, 7), 16) * k;
  return `rgb(${Math.round(Math.min(255, r))},${Math.round(Math.min(255, gg))},${Math.round(Math.min(255, b))})`;
};

const drawTile = (
  cv: SkCanvas,
  g: Geo,
  i: number,
  flip: number,
  font: SkFont | null,
) => {
  "worklet";
  const [gx, gy] = PATH[i];
  const p = iso(g, gx, gy);
  const hw = g.TW * 0.46;
  const hh = g.TH * 0.46;
  const cy = p.y + g.TH * 0.5;
  const t = TILES[i];
  const d = 12;
  // side
  const side = Skia.PathBuilder.Make();
  side.moveTo(p.x - hw, cy);
  side.lineTo(p.x, cy + hh);
  side.lineTo(p.x + hw, cy);
  side.lineTo(p.x + hw, cy + d);
  side.lineTo(p.x, cy + hh + d);
  side.lineTo(p.x - hw, cy + d);
  side.close();
  cv.drawPath(side.detach(), paint(shade(t.c, 0.78)));
  // top, flipping like a card when it pays out
  const sx = flip > 0 ? Math.abs(Math.cos(flip * Math.PI)) : 1;
  const top = Skia.PathBuilder.Make();
  top.moveTo(p.x - hw * sx, cy);
  top.lineTo(p.x, cy - hh);
  top.lineTo(p.x + hw * sx, cy);
  top.lineTo(p.x, cy + hh);
  top.close();
  const revealed = flip > 0.5 && flip < 1.5;
  cv.drawPath(top.detach(), paint(revealed ? "#FFFFFF" : t.c));
  if (sx < 0.2) return;
  cv.save();
  cv.translate(p.x, cy);
  cv.scale(sx, 0.55);
  if (revealed) {
    cv.drawCircle(0, 0, 15, paint("#FF6B5A"));
    cv.drawCircle(0, 0, 7, paint("#FFFFFF"));
  } else if (t.kind === "coin") {
    cv.drawCircle(0, -2, 14, paint("#F5B731"));
    cv.drawCircle(0, -2, 9, paint("#FFD766"));
  } else if (t.kind === "gift") {
    cv.drawRect(Skia.XYWHRect(-13, -12, 26, 22), paint("#FF6B8B"));
    cv.drawRect(Skia.XYWHRect(-3, -12, 6, 22), paint("#FFE066"));
    cv.drawRect(Skia.XYWHRect(-15, -16, 30, 7), paint("#FF8FA6"));
  } else if (t.kind === "star") {
    const s = Skia.PathBuilder.Make();
    for (let k = 0; k < 10; k++) {
      const a = -Math.PI / 2 + (k * Math.PI) / 5;
      const r = k % 2 ? 7 : 16;
      if (k === 0) s.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else s.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    s.close();
    cv.drawPath(s.detach(), paint("#9B6BFF"));
  } else if (t.kind === "coupon") {
    cv.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(-18, -11, 36, 22), 5, 5),
      paint("#FF8A3D"),
    );
  } else {
    cv.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(-22, -10, 44, 20), 10, 10),
      paint("#FF8A3D"),
    );
  }
  cv.restore();
  if (font && t.label && !revealed && t.kind !== "start") {
    const w = font.measureText(t.label).width;
    cv.drawText(
      t.label,
      p.x - w / 2,
      cy + hh * 0.75,
      paint("#3B3350", 0.8),
      font,
    );
  }
  if (font && t.kind === "start" && !revealed) {
    const w = font.measureText("START").width;
    cv.drawText("START", p.x - w / 2, cy + 4, paint("#FFFFFF"), font);
  }
};

const drawPawn = (cv: SkCanvas, x: number, y: number, hop: number) => {
  "worklet";
  const h = Math.sin(Math.PI * hop) * 26;
  // squash on take-off and landing, stretch in the air
  const sq =
    1 +
    0.12 * Math.sin(Math.PI * hop) -
    0.14 * Math.max(0, 1 - hop * 6) * (hop > 0 ? 1 : 0);
  cv.drawOval(
    Skia.XYWHRect(x - 13 + h * 0.1, y - 4, 26 - h * 0.2, 9),
    paint("#3B3350", 0.18 - h * 0.003),
  );
  cv.save();
  cv.translate(x, y - h);
  cv.scale(1, sq);
  const body = Skia.PathBuilder.Make();
  body.moveTo(-14, 0);
  body.quadTo(-12, -22, -6, -30);
  body.lineTo(6, -30);
  body.quadTo(12, -22, 14, 0);
  body.close();
  cv.drawPath(body.detach(), paint("#FF5C5C"));
  cv.drawOval(Skia.XYWHRect(-15, -5, 30, 9), paint("#E84545"));
  cv.drawCircle(0, -38, 11, paint("#FF6E6E"));
  cv.drawCircle(-4, -42, 3.5, paint("#FFFFFF", 0.7));
  cv.restore();
};

/** A point of the cube rotated by rx (about x) then rz (about z), seen from a three-quarter camera. */
const proj = (
  x: number,
  y: number,
  size: number,
  rx: number,
  rz: number,
  vx: number,
  vy: number,
  vz: number,
) => {
  "worklet";
  const cyw = Math.cos(Math.PI / 4);
  const syw = Math.sin(Math.PI / 4);
  const cp = Math.cos(0.62);
  const sp = Math.sin(0.62);
  const b = vy * Math.cos(rx) - vz * Math.sin(rx);
  const c = vy * Math.sin(rx) + vz * Math.cos(rx);
  const a2 = vx * Math.cos(rz) - b * Math.sin(rz);
  const b2 = vx * Math.sin(rz) + b * Math.cos(rz);
  const a3 = a2 * cyw + c * syw;
  const c3 = -a2 * syw + c * cyw;
  const b4 = b2 * cp - c3 * sp;
  const c4 = b2 * sp + c3 * cp;
  return [x + a3 * size, y - b4 * size, c4];
};

const drawDie = (
  cv: SkCanvas,
  x: number,
  y: number,
  size: number,
  rx: number,
  rz: number,
) => {
  "worklet";
  // faces: normal, value, u axis, v axis
  const faces = [
    [0, 1, 0, 1, 1, 0, 0, 0, 0, 1],
    [0, -1, 0, 6, 1, 0, 0, 0, 0, -1],
    [0, 0, 1, 2, 1, 0, 0, 0, 1, 0],
    [0, 0, -1, 5, -1, 0, 0, 0, 1, 0],
    [1, 0, 0, 3, 0, 0, -1, 0, 1, 0],
    [-1, 0, 0, 4, 0, 0, 1, 0, 1, 0],
  ];
  const order: number[] = [];
  const depth: number[] = [];
  for (let f = 0; f < 6; f++) {
    const n = proj(x, y, size, rx, rz, faces[f][0], faces[f][1], faces[f][2]);
    const o = proj(x, y, size, rx, rz, 0, 0, 0);
    depth.push(n[2] - o[2]);
    order.push(f);
  }
  for (let i = 0; i < 6; i++) {
    for (let j = i + 1; j < 6; j++) {
      if (depth[order[j]] < depth[order[i]]) {
        const tmp = order[i];
        order[i] = order[j];
        order[j] = tmp;
      }
    }
  }
  for (let k = 0; k < 6; k++) {
    const f = order[k];
    if (depth[f] <= 0.001) continue;
    const F = faces[f];
    const nx = F[0];
    const ny = F[1];
    const nz = F[2];
    const ux = F[4];
    const uy = F[5];
    const uz = F[6];
    const vx = F[7];
    const vy = F[8];
    const vz = F[9];
    const pth = Skia.PathBuilder.Make();
    const corners = [
      [-1, -1],
      [1, -1],
      [1, 1],
      [-1, 1],
    ];
    for (let c = 0; c < 4; c++) {
      const u = corners[c][0] * 0.92;
      const v = corners[c][1] * 0.92;
      const q = proj(
        x,
        y,
        size,
        rx,
        rz,
        nx + ux * u + vx * v,
        ny + uy * u + vy * v,
        nz + uz * u + vz * v,
      );
      if (c === 0) pth.moveTo(q[0], q[1]);
      else pth.lineTo(q[0], q[1]);
    }
    pth.close();
    const lit = 0.72 + 0.28 * depth[f];
    cv.drawPath(pth.detach(), paint(shade("#FFFFFF", lit)));
    // pips
    const val = F[3];
    const pips: number[][] =
      val === 1
        ? [[0, 0]]
        : val === 2
          ? [
              [-0.5, -0.5],
              [0.5, 0.5],
            ]
          : val === 3
            ? [
                [-0.5, -0.5],
                [0, 0],
                [0.5, 0.5],
              ]
            : val === 4
              ? [
                  [-0.5, -0.5],
                  [0.5, -0.5],
                  [-0.5, 0.5],
                  [0.5, 0.5],
                ]
              : val === 5
                ? [
                    [-0.5, -0.5],
                    [0.5, -0.5],
                    [0, 0],
                    [-0.5, 0.5],
                    [0.5, 0.5],
                  ]
                : [
                    [-0.5, -0.55],
                    [0.5, -0.55],
                    [-0.5, 0],
                    [0.5, 0],
                    [-0.5, 0.55],
                    [0.5, 0.55],
                  ];
    for (let pp = 0; pp < pips.length; pp++) {
      const q = proj(
        x,
        y,
        size,
        rx,
        rz,
        nx * 1.001 + ux * pips[pp][0] + vx * pips[pp][1],
        ny * 1.001 + uy * pips[pp][0] + vy * pips[pp][1],
        nz * 1.001 + uz * pips[pp][0] + vz * pips[pp][1],
      );
      cv.drawCircle(
        q[0],
        q[1],
        size * 0.15 * (0.6 + 0.4 * depth[f]),
        paint(val === 1 ? "#FF4D5E" : "#2E2A3D"),
      );
    }
  }
};

type Out = {
  th: Throw;
  u: number;
  prev: Throw;
  uPrev: number;
  pawnIdle: number;
};

type Props = { autoPlay?: boolean };

export function DiceBoardRewards({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "DBR-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "DBR-Black": require("./assets/fonts/Nunito-Black.ttf"),
  });
  const font = useFont(require("./assets/fonts/Nunito-Black.ttf"), 11);

  const TW = Math.round((W - 20) / 4);
  const g: Geo = {
    cx: W / 2,
    top: Math.round(H * 0.27),
    TW,
    TH: TW / 2,
    trayX: W / 2,
    trayY: H - insets.bottom - 175,
  };

  const st = useSharedValue({
    th: { at: -10, value: 1, steps: 0, from: 0 } as Throw,
    prev: { at: -10, value: 1, steps: 0, from: 0 } as Throw,
    pos: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    lastHop: 0,
    rolls: 3,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    th: { at: -10, value: 1, steps: 0, from: 0 },
    u: -1,
    prev: { at: -10, value: 1, steps: 0, from: 0 },
    uPrev: -1,
    pawnIdle: 0,
  });
  const [toastText, setToastText] = useState("");
  const [rolls, setRolls] = useState(3);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __diceBoard?: unknown };
    gl.__diceBoard = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__diceBoard;
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
    const now = clock.get() + wall;
    clock.set(now);
    const s = st.get();
    const dbg = debugT.get();
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({ ...sc, pawnIdle: 0 });
      return;
    }
    const busy = now - s.th.at < 1.15 + s.th.steps * HOP + 2.0;
    if (
      auto &&
      !busy &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || s.pos % N === 0)
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      out.set({ ...sc, pawnIdle: now });
      s.pos =
        sc.u < 0
          ? 0
          : (sc.th.from +
              Math.min(
                sc.th.steps,
                Math.max(0, Math.floor((sc.u - 1.15) / HOP) + 1),
              )) %
            N;
      return;
    }
    s.scripted = 0;
    const u = now - s.th.at;
    const r = rollAt(s.th, u);
    const hop = Math.floor(r.hopsDone);
    if (u > 1.15 && hop !== s.lastHop && hop <= s.th.steps) {
      s.lastHop = hop;
      scheduleOnRN(buzz, 0);
      if (hop === s.th.steps) scheduleOnRN(buzz, 2);
    }
    out.set({
      th: s.th,
      u,
      prev: s.prev,
      uPrev: now - s.prev.at,
      pawnIdle: now,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `pos=${s.pos} value=${s.th.value} u=${u.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => {
      const o = out.get();
      if (rollAt(o.th, o.u).toast > 0.01) return (o.th.from + o.th.steps) % N;
      if (o.uPrev > 0 && rollAt(o.prev, o.uPrev).toast > 0.01)
        return (o.prev.from + o.prev.steps) % N;
      return -1;
    },
    (v, prev) => {
      if (v !== prev && v >= 0) scheduleOnRN(setToastText, PRIZE[v]);
    },
  );

  const throwDie = (power: number) => {
    "worklet";
    const s = st.get();
    const now = clock.get();
    if (now - s.th.at < 1.15 + s.th.steps * HOP + 0.6 && s.th.at > 0) return;
    const rnd = Math.sin(now * 12.9898 + s.pos * 78.233) * 43758.5453;
    const value = 1 + Math.floor((rnd - Math.floor(rnd)) * 6);
    s.prev = s.th;
    s.th = { at: now, value, steps: value, from: s.pos };
    s.pos = (s.pos + value) % N;
    s.lastHop = 0;
    s.scripted = 0;
    s.lastTouch = now;
    void power;
    s.rolls = s.rolls > 0 ? s.rolls - 1 : 3;
    scheduleOnRN(buzz, 1);
    scheduleOnRN(setRolls, s.rolls);
  };
  const flick = Gesture.Pan().onEnd((e) => {
    if (e.translationY < -30 || e.velocityY < -500)
      throwDie(Math.min(1, -e.velocityY / 2000));
  });
  const onRoll = () => scheduleOnUI(throwDie, 0.6);

  const picture = useDerivedValue(() =>
    createPicture((cv) => {
      const o = out.get();
      const r = rollAt(o.th, o.u);
      const landed = (o.th.from + o.th.steps) % N;
      const landedPrev = (o.prev.from + o.prev.steps) % N;
      const flipPrev = o.uPrev > 0 ? rollAt(o.prev, o.uPrev).flip : 0;
      // board plate under the loop
      const c0 = iso(g, 0, 0);
      const c3 = iso(g, 4, 4);
      cv.drawOval(
        Skia.XYWHRect(
          g.cx - g.TW * 2.4,
          (c0.y + c3.y) / 2 - g.TH * 1.7 + 10,
          g.TW * 4.8,
          g.TH * 4.4,
        ),
        paint("#2D2550", 0.07),
      );
      for (let i = 0; i < N; i++) {
        const f =
          i === landed && o.u > 0
            ? r.flip
            : i === landedPrev && flipPrev > 0 && flipPrev < 2
              ? flipPrev
              : 0;
        drawTile(cv, g, i, f, font);
      }
      // the centre plate: the shop's logo
      const midC = iso(g, 1.5, 1.5);
      const mid = { x: midC.x, y: midC.y + g.TH * 0.5 };
      cv.drawOval(
        Skia.XYWHRect(
          mid.x - g.TW * 0.8,
          mid.y - g.TH * 0.55,
          g.TW * 1.6,
          g.TH * 1.1,
        ),
        paint("#FFFFFF", 0.8),
      );
      // pawn
      const pp = tileAt(g, o.u < 0 ? o.th.from : r.pawn);
      const frac = r.pawn - Math.floor(r.pawn);
      const pawnHop = o.u > 1.15 ? frac : 0;
      // die: tray → arc → centre → home
      const size = g.TW * 0.3;
      let dx = g.trayX;
      let dy = g.trayY;
      let rx = FACE_ROT[o.th.value][0];
      let rz = FACE_ROT[o.th.value][1];
      if (o.u >= 0 && r.home < 1) {
        const f = r.fl;
        const ex = mid.x;
        const ey = mid.y - 6;
        dx = g.trayX + (ex - g.trayX) * easeOut(f);
        dy =
          g.trayY +
          (ey - g.trayY) * f -
          Math.sin(Math.PI * f) * 170 -
          r.bounce * 60;
        if (r.home > 0) {
          const h = smoother(r.home);
          dx = ex + (g.trayX - ex) * h;
          dy = ey + (g.trayY - ey) * h - Math.sin(Math.PI * h) * 80;
        }
        rx = reduced ? FACE_ROT[o.th.value][0] : r.rx;
        rz = reduced ? FACE_ROT[o.th.value][1] : r.rz;
      }
      cv.drawOval(
        Skia.XYWHRect(
          dx - size * 1.3,
          (o.u >= 0 && r.home < 1 ? mid.y : g.trayY) + size * 0.9,
          size * 2.6,
          size * 0.7,
        ),
        paint("#2D2550", 0.16),
      );
      // whichever is nearer the viewer is drawn last
      if (dy + size < pp.y) {
        drawDie(cv, dx, dy, size, rx, rz);
        drawPawn(cv, pp.x, pp.y - 4, pawnHop);
      } else {
        drawPawn(cv, pp.x, pp.y - 4, pawnHop);
        drawDie(cv, dx, dy, size, rx, rz);
      }
    }),
  );

  const toast = useAnimatedStyle(() => {
    const o = out.get();
    const v = Math.max(
      rollAt(o.th, o.u).toast,
      o.uPrev > 0 ? rollAt(o.prev, o.uPrev).toast : 0,
    );
    return {
      opacity: v,
      transform: [{ translateY: (1 - v) * -16 }, { scale: 0.94 + 0.06 * v }],
    };
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
            colors={["#FFE9D6", "#FFF6EC", "#F0EAFF"]}
          />
        </Rect>
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.header, { top: insets.top + 8 }]}>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Close"
        >
          <Ionicons name="close" size={22} color="#2E2A3D" />
        </Pressable>
        <Text style={styles.title} accessibilityRole="header">
          Lucky Lap
        </Text>
        <View style={styles.coins} accessible accessibilityLabel="2,480 coins">
          <View style={styles.coinDot} />
          <Text style={styles.coinText}>2,480</Text>
        </View>
      </View>
      <Text style={[styles.sub, { top: insets.top + 62 }]}>
        Land on a coupon to win it for your basket
      </Text>

      <Animated.View
        style={[styles.toast, { top: insets.top + 96 }, toast]}
        pointerEvents="none"
        accessibilityLiveRegion="polite"
      >
        <Ionicons name="pricetag" size={18} color="#FF6B5A" />
        <Text style={styles.toastText}>{toastText}</Text>
      </Animated.View>

      <GestureDetector gesture={flick}>
        <View
          style={[styles.tray, { top: g.trayY - 70, left: W / 2 - 80 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Die. Flick up to roll"
          onAccessibilityTap={onRoll}
        />
      </GestureDetector>
      <Text style={[styles.hint, { top: g.trayY + 52 }]}>
        Flick the die up to roll
      </Text>
      <View style={[styles.bottom, { bottom: insets.bottom + 16 }]}>
        <Text style={styles.rolls}>{rolls} rolls left today</Text>
        <Pressable
          onPress={onRoll}
          accessibilityRole="button"
          accessibilityLabel="Roll"
          style={({ pressed }) => [styles.roll, pressed && styles.pressed]}
        >
          <Ionicons name="dice" size={20} color="#FFFFFF" />
          <Text style={styles.rollText}>Roll</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FFF6EC" },
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
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  title: { fontFamily: "DBR-Title", fontSize: 26, color: "#2E2A3D" },
  coins: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 34,
    paddingHorizontal: 12,
    borderRadius: 17,
    backgroundColor: "#FFFFFF",
  },
  coinDot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: "#F5B731",
    borderWidth: 3,
    borderColor: "#FFD766",
  },
  coinText: { fontFamily: "DBR-Black", fontSize: 14, color: "#2E2A3D" },
  sub: {
    position: "absolute",
    left: 24,
    right: 24,
    textAlign: "center",
    fontFamily: "DBR-Black",
    fontSize: 14,
    color: "#8A8098",
  },
  toast: {
    position: "absolute",
    alignSelf: "center",
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 22,
    backgroundColor: "#FFFFFF",
    shadowColor: "#2D2550",
    shadowOpacity: 0.12,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
  },
  toastText: { fontFamily: "DBR-Black", fontSize: 15, color: "#2E2A3D" },
  tray: { position: "absolute", width: 160, height: 120 },
  hint: {
    position: "absolute",
    left: 0,
    right: 0,
    textAlign: "center",
    fontFamily: "DBR-Black",
    fontSize: 13,
    color: "#A196B0",
  },
  bottom: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  rolls: { fontFamily: "DBR-Black", fontSize: 15, color: "#5B5270" },
  roll: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    height: 56,
    paddingHorizontal: 28,
    borderRadius: 28,
    backgroundColor: "#FF6B5A",
    borderBottomWidth: 4,
    borderBottomColor: "#D94A3A",
  },
  rollText: { fontFamily: "DBR-Black", fontSize: 18, color: "#FFFFFF" },
  pressed: { transform: [{ scale: 0.96 }] },
});
