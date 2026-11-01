import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  Picture,
  Skia,
  useFont,
  useImage,
  type SkCanvas,
  type SkFont,
  type SkImage,
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
import { scheduleOnRN } from "react-native-worklets";

import LANDMARKS from "./assets/art/landmarks.json";

/*
 Quest Map Unroll — the level map of an invented kids' learning game, Spellwood.

 Look (a Pinterest game screen: a magic book open on a parchment map, a winding trail with numbered
 level badges ringed by stars, a "magic power" pill on top and item shop buttons below).

 Ours: the map arrives rolled up like a scroll. It unrolls down the screen — the curled edge rolls along
 and the parchment appears behind it — and each level badge pops up as the edge passes it. Drag the map
 up to roll it back (or down to unroll it), the edge follows your finger. Tap a level and a glowing wisp
 travels along the trail from your current level to it while a card with the level's name and stars
 rises.

 autoPlay: LOOP = 9 s — rolled up, unrolls 0.6–2.4, the wisp travels from level 3 to 4 (3.0–4.2), the
 level card shows 4.3–6.0, the map rolls back up 6.6–8.2 (frame 9 = frame 0). Reduced motion: the map
 fades in and out; the wisp jumps to the chosen level.
*/

let debugState = "";

const LOOP = 9;
const LEVELS = [
  { name: "Intro to spells", stars: 3 },
  { name: "Stone bridge", stars: 3 },
  { name: "Pumpkin patch", stars: 2 },
  { name: "Crystal cave", stars: 0 },
  { name: "Giant mushroom", stars: 0 },
  { name: "Wizard tower", stars: 0 },
];
const CURRENT = 2;

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
  const c = 2.2;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
};
const scriptAt = (t: number) => {
  "worklet";
  let u = 0;
  if (t >= 0.6 && t < 2.4) u = smoother((t - 0.6) / 1.8);
  else if (t >= 2.4 && t < 6.6) u = 1;
  else if (t >= 6.6 && t < 8.2) u = 1 - smoother((t - 6.6) / 1.6);
  const wisp =
    t >= 3.0 && t < 6.6 ? CURRENT + smoother((t - 3.0) / 1.2) : CURRENT;
  const card =
    t >= 4.3 && t < 6.0
      ? smoother((t - 4.3) / 0.4) * (1 - smoother((t - 5.6) / 0.4))
      : 0;
  return { u, wisp, card, sel: t >= 3.0 && t < 6.6 ? CURRENT + 1 : -1 };
};

type Geo = { x: number; y: number; w: number; h: number; nodes: number[][] };
type Out = { u: number; wisp: number; t: number };

const paintC = (c: string, a = 1) => {
  "worklet";
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(c));
  p.setAlphaf(a);
  return p;
};
/** Point along the trail at a fractional level index. */
const along = (g: Geo, f: number) => {
  "worklet";
  const i = Math.max(0, Math.min(g.nodes.length - 2, Math.floor(f)));
  const k = clamp01(f - i);
  const a = g.nodes[i];
  const b = g.nodes[i + 1];
  // a gentle S between badges so the wisp does not fly in straight lines
  const bend = Math.sin(k * Math.PI) * 18 * (i % 2 ? 1 : -1);
  return [a[0] + (b[0] - a[0]) * k + bend, a[1] + (b[1] - a[1]) * k];
};

const drawMap = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  page: SkImage | null,
  edge: SkImage | null,
  font: SkFont | null,
  reduced: boolean,
) => {
  "worklet";
  const u = reduced ? (o.u > 0.5 ? 1 : 0) : o.u;
  const edgeY = g.y + g.h * u;
  // the unrolled part of the page
  if (page && u > 0.001) {
    cv.save();
    cv.clipRect(Skia.XYWHRect(g.x, g.y, g.w, edgeY - g.y), 1, true);
    const p = Skia.Paint();
    p.setAlphaf(reduced ? o.u : 1);
    cv.drawImageRect(
      page,
      Skia.XYWHRect(0, 0, page.width(), page.height()),
      Skia.XYWHRect(g.x, g.y, g.w, g.h),
      p,
    );
    cv.restore();
  }
  // level badges pop up as the edge passes them
  for (let i = 0; i < g.nodes.length; i++) {
    const n = g.nodes[i];
    const pass = (edgeY - n[1]) / 60;
    if (pass <= 0) continue;
    const k = reduced ? 1 : backOut(pass);
    const done = i <= CURRENT;
    const cur =
      Math.abs(o.wisp - i) < 0.02 || (i === CURRENT && o.wisp < CURRENT + 0.02);
    cv.save();
    cv.translate(n[0], n[1]);
    cv.scale(k, k);
    if (cur) {
      const pulse = reduced
        ? 0.5
        : 0.5 + 0.5 * Math.sin((o.t * 2 * Math.PI * 3) / LOOP);
      cv.drawCircle(
        0,
        0,
        30 + 6 * pulse,
        paintC("#FFE38A", 0.35 + 0.25 * pulse),
      );
    }
    cv.drawCircle(0, 3, 22, paintC("#8B4A2B", 0.35));
    cv.drawCircle(0, 0, 22, paintC(done ? "#F4E3C8" : "#E7D2B1"));
    cv.drawCircle(0, 0, 18, paintC(done ? "#FFFFFF" : "#F1E2C8"));
    if (font) {
      const label = `${i + 1}`;
      const w = font.measureText(label).width;
      cv.drawText(label, -w / 2, 7, paintC(done ? "#B4532B" : "#A58F70"), font);
    }
    // stars on a little arc above the badge
    for (let s = 0; s < 3; s++) {
      const a = -Math.PI / 2 + (s - 1) * 0.55;
      const on = s < LEVELS[i].stars;
      const sx = Math.cos(a) * 32;
      const sy = Math.sin(a) * 32;
      const star = Skia.PathBuilder.Make();
      for (let q = 0; q < 10; q++) {
        const aa = -Math.PI / 2 + (q * Math.PI) / 5;
        const r = q % 2 ? 3 : 7;
        if (q === 0) star.moveTo(sx + Math.cos(aa) * r, sy + Math.sin(aa) * r);
        else star.lineTo(sx + Math.cos(aa) * r, sy + Math.sin(aa) * r);
      }
      star.close();
      cv.drawPath(star.detach(), paintC(on ? "#FFC23D" : "#D9C3A0"));
    }
    cv.restore();
  }
  // the wisp travelling along the trail
  if (u > 0.95) {
    const w = along(g, o.wisp);
    const glow = paintC("#FFE38A", 0.5);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 12, true));
    cv.drawCircle(w[0], w[1] - 30, 14, glow);
    cv.drawCircle(w[0], w[1] - 30, 6, paintC("#FFFBE6"));
    for (let s = 1; s < 6; s++) {
      const tw = along(g, Math.max(0, o.wisp - s * 0.04));
      cv.drawCircle(
        tw[0],
        tw[1] - 30,
        4 - s * 0.6,
        paintC("#FFE38A", 0.6 - s * 0.1),
      );
    }
  }
  // the roll: the curled edge riding the boundary, a rolled cylinder above when closed
  if (edge) {
    const rollH = 34 + 26 * (1 - u);
    cv.drawRect(
      Skia.XYWHRect(g.x - 6, edgeY - 8, g.w + 12, 18),
      paintC("#5A2E18", 0.18),
    );
    cv.drawImageRect(
      edge,
      Skia.XYWHRect(0, 0, edge.width(), edge.height()),
      Skia.XYWHRect(g.x - 8, edgeY - rollH * 0.55, g.w + 16, rollH),
      Skia.Paint(),
    );
  }
};

type Props = { autoPlay?: boolean };

export function QuestMapUnroll({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "QMU-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "QMU-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "QMU-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });
  const font = useFont(require("./assets/fonts/Nunito-Black.ttf"), 18);
  const page = useImage(require("./assets/art/map-page.webp"));
  const edge = useImage(require("./assets/art/paper-edge.png"));

  const mw = W - 40;
  const mh = Math.round(mw * (1300 / 900));
  const maxH = H - insets.top - 96 - insets.bottom - 120;
  const sc = Math.min(1, maxH / mh);
  const gw = Math.round(mw * sc);
  const gh = Math.round(mh * sc);
  const g: Geo = {
    x: Math.round((W - gw) / 2),
    y: insets.top + 96,
    w: gw,
    h: gh,
    nodes: (LANDMARKS as { x: number; y: number }[]).map((p) => [
      Math.round((W - gw) / 2) + (p.x / 900) * gw,
      insets.top + 96 + (p.y / 1300) * gh,
    ]),
  };

  const st = useSharedValue({
    u: 0,
    v: 0,
    target: 0,
    drag: 0,
    u0: 0,
    wisp: CURRENT,
    wispTo: CURRENT,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    card: 0,
    cardT: -10,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ u: 0, wisp: CURRENT, t: 0 });
  const cardV = useSharedValue(0);
  const cardLevel = useSharedValue(CURRENT + 1);
  const [sel, setSel] = useState(CURRENT + 1);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __questMap?: unknown };
    gl.__questMap = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__questMap;
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
    if (dbg >= 0) {
      const sc2 = scriptAt(dbg % LOOP);
      out.set({ u: sc2.u, wisp: sc2.wisp, t: dbg });
      cardV.set(sc2.card);
      cardLevel.set(CURRENT + 1);
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 ||
        (s.u < 0.001 && s.target === 0 && s.wisp === CURRENT))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc2 = scriptAt(s.loopT);
      s.u = sc2.u;
      s.target = sc2.u > 0.5 ? 1 : 0;
      s.wisp = sc2.wisp;
      s.wispTo = sc2.wisp;
      out.set({ u: s.u, wisp: s.wisp, t: now });
      cardV.set(sc2.card);
      cardLevel.set(CURRENT + 1);
      return;
    }
    s.scripted = 0;
    if (s.drag === 0) {
      const k = reduced ? 400 : 70;
      const c = 2 * 0.95 * Math.sqrt(k);
      s.v += (k * (s.target - s.u) - c * s.v) * dt;
      s.u = clamp01(s.u + s.v * dt);
    }
    if (s.wisp !== s.wispTo) {
      const dir = s.wispTo > s.wisp ? 1 : -1;
      s.wisp += dir * dt * (reduced ? 20 : 1.1);
      if ((dir > 0 && s.wisp >= s.wispTo) || (dir < 0 && s.wisp <= s.wispTo)) {
        s.wisp = s.wispTo;
        s.cardT = now;
        scheduleOnRN(buzz, 1);
      }
    }
    cardLevel.set(Math.round(s.wispTo));
    const cu = now - s.cardT;
    cardV.set(
      cu >= 0 && cu < 2.2
        ? smoother(cu / 0.35) * (1 - smoother((cu - 1.8) / 0.4))
        : 0,
    );
    out.set({ u: s.u, wisp: s.wisp, t: now });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `u=${s.u.toFixed(2)} wisp=${s.wisp.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => cardLevel.get(),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setSel, v);
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetY([-10, 10])
    .onStart(() => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.u0 = s.u;
    })
    .onUpdate((e) => {
      const s = st.get();
      s.u = clamp01(s.u0 + e.translationY / g.h);
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      s.target =
        e.velocityY > 500 ? 1 : e.velocityY < -500 ? 0 : s.u > 0.5 ? 1 : 0;
      s.v = e.velocityY / g.h;
      s.lastTouch = clock.get();
      scheduleOnRN(buzz, 0);
    });
  const tap = Gesture.Tap().onEnd((e) => {
    const s = st.get();
    s.lastTouch = clock.get();
    s.scripted = 0;
    if (s.u < 0.9) {
      s.target = 1;
      return;
    }
    for (let i = 0; i < g.nodes.length; i++) {
      if (
        Math.hypot(e.absoluteX - g.nodes[i][0], e.absoluteY - g.nodes[i][1]) <
        34
      ) {
        s.wispTo = i;
        scheduleOnRN(buzz, 0);
      }
    }
  });
  const gesture = Gesture.Race(pan, tap);

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawMap(cv, g, out.get(), page, edge, font, reduced)),
  );
  const card = useAnimatedStyle(() => ({
    opacity: cardV.get(),
    transform: [{ translateY: (1 - cardV.get()) * 30 }],
  }));

  if (!fontsOk) return <View style={styles.screen} />;
  const lv = LEVELS[Math.max(0, Math.min(LEVELS.length - 1, sel))];

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.header, { top: insets.top + 12 }]}>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Back"
        >
          <Ionicons name="arrow-back" size={22} color="#5A2E18" />
        </Pressable>
        <View
          style={styles.power}
          accessible
          accessibilityLabel="Magic power 334"
        >
          <Ionicons name="flash" size={16} color="#FFFFFF" />
          <Text style={styles.powerNum}>334</Text>
          <Text style={styles.powerText}>MAGIC POWER</Text>
        </View>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Settings"
        >
          <Ionicons name="settings" size={20} color="#5A2E18" />
        </Pressable>
      </View>
      <GestureDetector gesture={gesture}>
        <View
          style={[
            styles.mapHit,
            { top: g.y - 20, height: g.h + 40, left: g.x, width: g.w },
          ]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Level map. Drag down to unroll, tap a level to travel there"
        />
      </GestureDetector>
      <View style={[styles.shop, { bottom: insets.bottom + 14 }]}>
        {[
          ["extension-puzzle", "Daily puzzle", "#FF8A5B"],
          ["gift", "Gift bag", "#B26BFF"],
          ["bulb", "Free hints", "#FFC23D"],
          ["storefront", "Hint shop", "#4FB548"],
        ].map(([icon, label, color]) => (
          <Pressable
            key={label}
            style={({ pressed }) => [styles.shopBtn, pressed && styles.pressed]}
            accessibilityRole="button"
            accessibilityLabel={label}
          >
            <View style={[styles.shopIcon, { backgroundColor: color }]}>
              <Ionicons name={icon as "gift"} size={22} color="#FFFFFF" />
            </View>
            <Text style={styles.shopText}>{label}</Text>
          </Pressable>
        ))}
      </View>
      <Animated.View
        style={[styles.card, { bottom: insets.bottom + 96 }, card]}
        pointerEvents="none"
        accessibilityLiveRegion="polite"
      >
        <View style={styles.cardBadge}>
          <Text style={styles.cardBadgeText}>{sel + 1}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle}>{lv.name}</Text>
          <Text style={styles.cardSub}>
            {lv.stars > 0
              ? `${lv.stars} of 3 stars`
              : "New level · 4 spells to learn"}
          </Text>
        </View>
        <View style={styles.play}>
          <Ionicons name="play" size={20} color="#FFFFFF" />
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#7A2E4A" },
  header: {
    position: "absolute",
    left: 16,
    right: 16,
    height: 56,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  round: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: "#FFD45A",
    alignItems: "center",
    justifyContent: "center",
    borderBottomWidth: 3,
    borderBottomColor: "#D99A1F",
  },
  power: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 44,
    paddingHorizontal: 16,
    borderRadius: 14,
    backgroundColor: "#4FB548",
    borderBottomWidth: 3,
    borderBottomColor: "#2F8A2C",
  },
  powerNum: { fontFamily: "QMU-Title", fontSize: 20, color: "#FFFFFF" },
  powerText: {
    fontFamily: "QMU-Black",
    fontSize: 11,
    color: "#E8FFD8",
    letterSpacing: 0.8,
  },
  mapHit: { position: "absolute" },
  card: {
    position: "absolute",
    left: 20,
    right: 20,
    height: 76,
    borderRadius: 24,
    backgroundColor: "#FFF4DD",
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
  },
  cardBadge: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: "#FFFFFF",
    borderWidth: 3,
    borderColor: "#F4E3C8",
    alignItems: "center",
    justifyContent: "center",
  },
  cardBadgeText: { fontFamily: "QMU-Title", fontSize: 18, color: "#B4532B" },
  cardTitle: { fontFamily: "QMU-Black", fontSize: 17, color: "#5A2E18" },
  cardSub: {
    fontFamily: "QMU-Bold",
    fontSize: 13,
    color: "#9A7050",
    marginTop: 2,
  },
  shop: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    justifyContent: "space-between",
  },
  shopBtn: { width: 82, alignItems: "center", gap: 4 },
  shopIcon: {
    width: 52,
    height: 52,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    borderBottomWidth: 3,
    borderBottomColor: "rgba(0,0,0,0.18)",
  },
  shopText: { fontFamily: "QMU-Black", fontSize: 11, color: "#FFE3EC" },
  pressed: { transform: [{ scale: 0.96 }] },
  play: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: "#4FB548",
    alignItems: "center",
    justifyContent: "center",
  },
});
