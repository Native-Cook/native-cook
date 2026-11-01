import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  Group,
  Oval,
  Picture,
  processTransform3d,
  Rect,
  rrect,
  rect as skRect,
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
  type SharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

import { QUESTS } from "./pqc-art";

/*
 Pop-up Quest Card — the "Recommended for you" screen of an invented kids' adventure app, Wonder Trails.

 Look (a Pinterest shot of a kids' trip app): a soft off-white page with a search field, a two-by-two
 grid of quest cards whose pastel illustrations look like cut paper (a pirate isle, a rocket camp,
 space explorers, a castle), a dark pill in the tab bar, and a detail page with the scene on top and a
 white sheet holding the title, four category chips, the day's activities and Book Now.

 Ours: the card opens like a pop-up book. The card grows into the header (container transform) while
 its illustration's page tilts back into a floor and every cut-out — the castle, the ship, the palm,
 the sails — stands up off it at its own depth, so the flat picture becomes a little paper diorama;
 drag sideways to look around it (parallax from the real 3D placement), drag down to fold it flat and
 put the card back. The sheet rises with a staggered list.

 autoPlay: LOOP = 9 s — rest 0–1.0, the pirate card opens 1.0–2.0 (smootherstep), the diorama sways
 ±0.22 rad 2.0–5.8, folds back 5.8–6.8, rest to 9.0 (frame 9 = frame 0). Reduced motion: the card
 opens with a crossfade and the layers stay flat.
*/

let debugState = "";

const LOOP = 9;
const PERSP = 760;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoothstep = (a: number, b: number, v: number) => {
  "worklet";
  const x = clamp01((v - a) / (b - a));
  return x * x * (3 - 2 * x);
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
const scriptAt = (t: number) => {
  "worklet";
  let o = 0;
  if (t >= 1.0 && t < 2.0) o = smoother(t - 1.0);
  else if (t >= 2.0 && t < 5.8) o = 1;
  else if (t >= 5.8 && t < 6.8) o = 1 - smoother(t - 5.8);
  const yaw =
    t >= 2.0 && t < 5.8 ? 0.22 * Math.sin(((t - 2.0) / 3.8) * 2 * Math.PI) : 0;
  return { o, yaw };
};

type Box = { x: number; y: number; w: number; h: number };

type Props = { autoPlay?: boolean };

export function PopUpQuestCard({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "PQC-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
    "PQC-Black": require("./assets/fonts/Nunito-Black.ttf"),
  });

  const top = insets.top;
  const cw = (W - 44) / 2;
  const ch = 214;
  const gridY = top + 176;
  const cards: Box[] = [0, 1, 2, 3].map((i) => ({
    x: 16 + (i % 2) * (cw + 12),
    y: gridY + Math.floor(i / 2) * (ch + 12),
    w: cw,
    h: ch,
  }));
  const illus: Box[] = cards.map((c) => ({
    x: c.x + 8,
    y: c.y + 8,
    w: c.w - 16,
    h: 138,
  }));
  const hero: Box = { x: 0, y: 0, w: W, h: Math.round(H * 0.5) };
  // the open page sits inside the header with a margin, so its edges show when it tilts back
  const page: Box = {
    x: 26,
    y: top + 70,
    w: W - 52,
    h: Math.round(H * 0.46) - top - 70 - 16,
  };
  const sheetTop = Math.round(H * 0.46);

  const st = useSharedValue({
    o: 0,
    v: 0,
    target: 0,
    yaw: 0,
    yawV: 0,
    drag: 0,
    mode: 0,
    o0: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const act = useSharedValue(0);
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue({ o: 0, yaw: 0 });
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __popQuest?: unknown };
    gl.__popQuest = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__popQuest;
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
      const sc = scriptAt(dbg % LOOP);
      act.set(0);
      out.set({ o: sc.o, yaw: sc.yaw });
      return;
    }
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.o < 0.001 && s.target === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 7.0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      if (act.get() !== 0 && sc.o === 0) act.set(0);
      s.o = sc.o;
      s.yaw = sc.yaw;
      s.v = 0;
      s.yawV = 0;
      s.target = sc.o > 0.5 ? 1 : 0;
    } else {
      s.scripted = 0;
      if (s.drag === 0) {
        const k = reduced ? 500 : 120;
        const c = 2 * 0.86 * Math.sqrt(k);
        s.v += (k * (s.target - s.o) - c * s.v) * dt;
        s.o += s.v * dt;
        if (s.o < 0) {
          s.o = 0;
          s.v = 0;
        }
        s.yawV += (-160 * s.yaw - 18 * s.yawV) * dt;
        s.yaw += s.yawV * dt;
      }
    }
    out.set({ o: s.o, yaw: s.yaw });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `o=${s.o.toFixed(3)} target=${s.target} yaw=${s.yaw.toFixed(2)} act=${act.get()} scripted=${s.scripted}`,
      );
    }
  });

  // React follows the UI thread: which quest is on top, and whether the detail page takes touches
  useAnimatedReaction(
    () => act.get(),
    (a, prev) => {
      if (a !== prev) scheduleOnRN(setActive, a);
    },
  );
  useAnimatedReaction(
    () => out.get().o > 0.5,
    (o, prev) => {
      if (o !== prev) scheduleOnRN(setOpen, o);
    },
  );

  const openCard = (i: number) => {
    "worklet";
    const s = st.get();
    if (s.o > 0.02 && act.get() !== i) return;
    act.set(i);
    s.scripted = 0;
    s.target = 1;
    s.lastTouch = clock.get();
  };
  const closeCard = () => {
    "worklet";
    const s = st.get();
    s.scripted = 0;
    s.target = 0;
    s.lastTouch = clock.get();
  };
  const onCard = (i: number) => {
    buzz(1);
    scheduleOnUI(openCard, i);
  };
  const onBack = () => {
    buzz(1);
    scheduleOnUI(closeCard);
  };

  const pan = Gesture.Pan()
    .minDistance(8)
    .onStart((e) => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.mode = Math.abs(e.translationX) > Math.abs(e.translationY) ? 1 : 0;
      s.o0 = s.o;
      s.v = 0;
    })
    .onUpdate((e) => {
      const s = st.get();
      if (s.mode === 1) {
        const y = e.translationX * 0.0022;
        s.yaw = y > 0.45 ? 0.45 : y < -0.45 ? -0.45 : y;
      } else {
        s.o = clamp01(s.o0 - Math.max(0, e.translationY) / (hero.h * 0.9));
      }
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      if (s.mode === 1) s.yawV = e.velocityX * 0.0015;
      else {
        const close = s.o < 0.75 || e.velocityY > 700;
        s.target = close ? 0 : 1;
        s.v = -e.velocityY / (hero.h * 0.9);
        if (close) scheduleOnRN(buzz, 0);
      }
      s.lastTouch = clock.get();
    });

  // ---- the diorama ---------------------------------------------------------------------------
  const quest = QUESTS[active];
  const frame = useDerivedValue(() => {
    const o = out.get().o;
    const e = reduced ? 1 : o;
    const from = illus[act.get()];
    return {
      x: lerp(from.x, page.x, e),
      y: lerp(from.y, page.y, e),
      w: lerp(from.w, page.w, e),
      h: lerp(from.h, page.h, e),
      r: lerp(18, 0, e),
      e,
      cx: lerp(from.x, hero.x, e),
      cy: lerp(from.y, hero.y, e),
      cw: lerp(from.w, hero.w, e),
      ch: lerp(from.h, sheetTop + 40, e),
    };
  });
  const clip = useDerivedValue(() => {
    const f = frame.get();
    return rrect(skRect(f.cx, f.cy, f.cw, f.ch), f.r, f.r);
  });
  const heroOpacity = useDerivedValue(() => {
    const o = out.get().o;
    return reduced ? clamp01(o * 2) : o > 0.001 ? 1 : 0;
  });
  const bg = useDerivedValue(() => {
    const f = frame.get();
    return skRect(f.cx, f.cy, f.cw, f.ch);
  });

  const nextIn = useAnimatedStyle(() => {
    const o = out.get().o;
    const k = smoothstep(0, 0.55, o);
    return { opacity: 1 - k, transform: [{ scale: 1 - 0.04 * k }] };
  });
  const sheet = useAnimatedStyle(() => {
    const k = smoothstep(0.25, 1, out.get().o);
    return { transform: [{ translateY: (1 - k) * (H - sheetTop + 40) }] };
  });
  const backBtn = useAnimatedStyle(() => ({
    opacity: smoothstep(0.6, 1, out.get().o),
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Animated.View
        style={[StyleSheet.absoluteFill, nextIn]}
        pointerEvents={open ? "none" : "auto"}
      >
        <View style={[styles.header, { top: top + 8 }]}>
          <View>
            <Text style={styles.hello}>Hi, Mia & Theo</Text>
            <Text style={styles.headline}>Where to next?</Text>
          </View>
          <View style={styles.avatar} accessible accessibilityLabel="Profile">
            <Ionicons name="happy-outline" size={22} color="#5A4A3A" />
          </View>
        </View>
        <View
          style={[styles.search, { top: top + 74 }]}
          accessible
          accessibilityRole="search"
          accessibilityLabel="Search next adventure"
        >
          <Ionicons name="search" size={18} color="#8C8A93" />
          <Text style={styles.searchText}>Search next adventure…</Text>
        </View>
        <Text
          style={[styles.section, { top: top + 140 }]}
          accessibilityRole="header"
        >
          Recommended for you!
        </Text>
        {QUESTS.map((q, i) => (
          <QuestCard
            key={q.title}
            i={i}
            box={cards[i]}
            ib={illus[i]}
            out={out}
            act={act}
            onPress={() => onCard(i)}
          />
        ))}
        <View style={[styles.tabs, { bottom: insets.bottom + 8 }]}>
          {[
            "compass-outline",
            "heart-outline",
            "sparkles",
            "document-text-outline",
            "notifications-outline",
          ].map((n, i) => (
            <View
              key={n}
              style={[styles.tab, i === 2 && styles.tabOn]}
              accessible
              accessibilityRole="tab"
              accessibilityState={{ selected: i === 2 }}
            >
              <Ionicons
                name={n as "heart-outline"}
                size={21}
                color={i === 2 ? "#FFFFFF" : "#6B6876"}
              />
            </View>
          ))}
        </View>
      </Animated.View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Group opacity={heroOpacity} clip={clip}>
          <Rect rect={bg} color={quest.bg} />
          {quest.layers.map((l, li) => (
            <PopLayer
              key={`${active}-${li}`}
              li={li}
              hinge={l.hinge}
              out={out}
              frame={frame}
              reduced={reduced}
              quest={active}
            />
          ))}
        </Group>
      </Canvas>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.heroHit, { height: hero.h }]}
          pointerEvents={open ? "auto" : "none"}
          accessible={open}
          accessibilityLabel={`${quest.title} pop-up scene. Drag sideways to look around, drag down to close`}
        />
      </GestureDetector>
      <Animated.View
        style={[styles.back, { top: top + 6 }, backBtn]}
        pointerEvents={open ? "auto" : "none"}
      >
        <Pressable
          onPress={onBack}
          accessibilityRole="button"
          accessibilityLabel="Back to quests"
          style={({ pressed }) => [styles.backBtn, pressed && styles.pressed]}
        >
          <Ionicons name="chevron-back" size={22} color="#2A2633" />
        </Pressable>
      </Animated.View>

      <Animated.View
        style={[
          styles.sheet,
          { top: sheetTop, paddingBottom: insets.bottom + 12 },
          sheet,
        ]}
        pointerEvents={open ? "auto" : "none"}
      >
        <View style={styles.grabber} />
        <Text style={styles.title} accessibilityRole="header">
          {quest.title}!
        </Text>
        <View style={styles.cats}>
          {quest.cats.map(([icon, label], i) => (
            <Stagger key={label} out={out} k={i}>
              <View style={styles.cat}>
                <View style={[styles.catIcon, { backgroundColor: quest.bg }]}>
                  <Ionicons
                    name={icon as "map-outline"}
                    size={22}
                    color={quest.tint}
                  />
                </View>
                <Text style={styles.catText}>{label}</Text>
              </View>
            </Stagger>
          ))}
        </View>
        <Text style={styles.subhead}>Activities:</Text>
        {quest.acts.map((a, i) => (
          <Stagger key={a.title} out={out} k={4 + i}>
            <View style={styles.act}>
              <View style={[styles.actIcon, { backgroundColor: quest.bg }]}>
                <Ionicons
                  name={a.icon as "map-outline"}
                  size={20}
                  color={quest.tint}
                />
              </View>
              <View style={styles.actBody}>
                <Text style={styles.actTitle}>{a.title}</Text>
                <Text style={styles.actSub}>{a.sub}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color="#B4B0BE" />
            </View>
          </Stagger>
        ))}
        <View style={styles.bookRow}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Book ${quest.title}`}
            style={({ pressed }) => [styles.book, pressed && styles.pressed]}
          >
            <Text style={styles.bookText}>
              Book now · {quest.price.replace("From ", "")}
            </Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Pick dates"
            style={({ pressed }) => [styles.cal, pressed && styles.pressed]}
          >
            <Ionicons name="calendar-outline" size={22} color="#E2574C" />
          </Pressable>
        </View>
      </Animated.View>
    </View>
  );
}

/** One cut-out of the open card, placed in 3D: it stands on the tilted page along its hinge line. */
function PopLayer({
  li,
  hinge,
  out,
  frame,
  reduced,
  quest,
}: {
  li: number;
  hinge: number;
  out: SharedValue<{ o: number; yaw: number }>;
  frame: SharedValue<{
    x: number;
    y: number;
    w: number;
    h: number;
    r: number;
    e: number;
    cx: number;
    cy: number;
    cw: number;
    ch: number;
  }>;
  reduced: boolean;
  quest: number;
}) {
  const matrix = useDerivedValue(() => {
    const o = out.get();
    const f = frame.get();
    // cover while it is a card, contain once it is the open page
    const sc = lerp(
      Math.max(f.w / 1000, f.h / 700),
      Math.min(f.w / 1000, f.h / 700),
      f.e,
    );
    const cx = f.x + f.w / 2;
    const bot = f.y + f.h;
    const phi = reduced ? 0 : 0.82 * smoothstep(0.15, 1, o.o);
    const yaw = reduced ? 0 : o.yaw;
    if (li === 1) {
      // the page itself: tilts back about its bottom edge
      return processTransform3d([
        { translateX: cx },
        { translateY: bot },
        { perspective: PERSP },
        { rotateY: yaw },
        { rotateX: phi },
        { scale: sc },
        { translateX: -500 },
        { translateY: -700 },
      ]);
    }
    const s = (700 - hinge) * sc;
    return processTransform3d([
      { translateX: cx },
      { translateY: bot },
      { perspective: PERSP },
      { rotateY: yaw },
      { translateY: -s * Math.cos(phi) },
      { translateZ: -s * Math.sin(phi) },
      { scale: sc },
      { translateX: -500 },
      { translateY: -hinge },
    ]);
  });
  // cut-outs cast a soft shadow on the page once they stand up
  const shade = useDerivedValue(() =>
    reduced ? 0 : 0.14 * smoothstep(0.4, 1, out.get().o),
  );
  return (
    <Group matrix={matrix}>
      <Picture picture={QUESTS[quest].layers[li].pic} />
      {li === 1
        ? QUESTS[quest].layers.map((l, k) =>
            k > 1 && l.hinge < 700 ? (
              <Oval
                key={k}
                x={150}
                y={l.hinge - 26}
                width={700}
                height={34}
                color="#1D2A3A"
                opacity={shade}
              />
            ) : null,
          )
        : null}
    </Group>
  );
}

function QuestCard({
  i,
  box,
  ib,
  out,
  act,
  onPress,
}: {
  i: number;
  box: Box;
  ib: Box;
  out: SharedValue<{ o: number; yaw: number }>;
  act: SharedValue<number>;
  onPress: () => void;
}) {
  const q = QUESTS[i];
  const sc = Math.max(ib.w / 1000, ib.h / 700);
  // the card's own art hides while it is the one flying open
  const hide = useAnimatedStyle(() => ({
    opacity: act.get() === i && out.get().o > 0.001 ? 0 : 1,
  }));
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${q.title}, ${q.price}`}
      style={({ pressed }) => [
        styles.card,
        { left: box.x, top: box.y, width: box.w, height: box.h },
        pressed && styles.pressed,
      ]}
    >
      <Animated.View
        style={[styles.cardArt, { height: ib.h, backgroundColor: q.bg }, hide]}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Group
            transform={[
              { translateX: ib.w / 2 },
              { translateY: ib.h },
              { scale: sc },
              { translateX: -500 },
              { translateY: -700 },
            ]}
          >
            {q.layers.map((l, k) => (
              <Picture key={k} picture={l.pic} />
            ))}
          </Group>
        </Canvas>
        <View style={styles.heart}>
          <Ionicons name="heart-outline" size={15} color="#3A3442" />
        </View>
      </Animated.View>
      <Text style={styles.cardTitle} numberOfLines={1}>
        {q.title}
      </Text>
      <Text style={styles.cardPrice}>{q.price}</Text>
    </Pressable>
  );
}

function Stagger({
  out,
  k,
  children,
}: {
  out: SharedValue<{ o: number; yaw: number }>;
  k: number;
  children: React.ReactNode;
}) {
  const st = useAnimatedStyle(() => {
    const v = smoothstep(0.45 + k * 0.05, 0.8 + k * 0.05, out.get().o);
    return { opacity: v, transform: [{ translateY: (1 - v) * 12 }] };
  });
  return <Animated.View style={st}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#FBF8F3" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  hello: { fontFamily: "PQC-Bold", fontSize: 14, color: "#8C8A93" },
  headline: {
    fontFamily: "PQC-Black",
    fontSize: 26,
    color: "#2A2633",
    marginTop: 2,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#F6E3C8",
    alignItems: "center",
    justifyContent: "center",
  },
  search: {
    position: "absolute",
    left: 16,
    right: 16,
    height: 50,
    borderRadius: 25,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingHorizontal: 18,
    shadowColor: "#3A2E1F",
    shadowOpacity: 0.06,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  searchText: { fontFamily: "PQC-Bold", fontSize: 15, color: "#A9A6B2" },
  section: {
    position: "absolute",
    left: 20,
    fontFamily: "PQC-Black",
    fontSize: 20,
    color: "#2A2633",
  },
  card: {
    position: "absolute",
    borderRadius: 26,
    backgroundColor: "#FFFFFF",
    padding: 8,
    shadowColor: "#3A2E1F",
    shadowOpacity: 0.07,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
  },
  cardArt: { borderRadius: 18, overflow: "hidden" },
  heart: {
    position: "absolute",
    top: 8,
    right: 8,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "rgba(255,255,255,0.85)",
    alignItems: "center",
    justifyContent: "center",
  },
  cardTitle: {
    fontFamily: "PQC-Black",
    fontSize: 15,
    color: "#2A2633",
    marginTop: 10,
    marginLeft: 4,
  },
  cardPrice: {
    fontFamily: "PQC-Bold",
    fontSize: 12,
    color: "#8C8A93",
    marginTop: 2,
    marginLeft: 4,
  },
  tabs: {
    position: "absolute",
    left: 24,
    right: 24,
    height: 60,
    borderRadius: 30,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    shadowColor: "#3A2E1F",
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
  },
  tab: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  tabOn: { width: 64, backgroundColor: "#2A2633" },
  heroHit: { position: "absolute", left: 0, right: 0, top: 0 },
  back: { position: "absolute", left: 16 },
  backBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(255,255,255,0.92)",
    alignItems: "center",
    justifyContent: "center",
  },
  sheet: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    paddingHorizontal: 20,
  },
  grabber: {
    alignSelf: "center",
    width: 40,
    height: 5,
    borderRadius: 3,
    backgroundColor: "#E6E1EA",
    marginTop: 10,
  },
  title: {
    fontFamily: "PQC-Black",
    fontSize: 28,
    color: "#2A2633",
    marginTop: 14,
  },
  cats: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 16,
  },
  cat: { alignItems: "center", gap: 6, width: 72 },
  catIcon: {
    width: 52,
    height: 52,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
  },
  catText: { fontFamily: "PQC-Bold", fontSize: 12, color: "#6B6876" },
  subhead: {
    fontFamily: "PQC-Black",
    fontSize: 18,
    color: "#2A2633",
    marginTop: 20,
    marginBottom: 6,
  },
  act: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    height: 64,
    borderRadius: 20,
    backgroundColor: "#FBF8F3",
    paddingHorizontal: 12,
    marginTop: 8,
  },
  actIcon: {
    width: 42,
    height: 42,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  actBody: { flex: 1 },
  actTitle: { fontFamily: "PQC-Black", fontSize: 15, color: "#2A2633" },
  actSub: {
    fontFamily: "PQC-Bold",
    fontSize: 12,
    color: "#8C8A93",
    marginTop: 2,
  },
  bookRow: { flexDirection: "row", gap: 12, marginTop: "auto" },
  book: {
    flex: 1,
    height: 56,
    borderRadius: 28,
    backgroundColor: "#2A2633",
    alignItems: "center",
    justifyContent: "center",
  },
  bookText: { fontFamily: "PQC-Black", fontSize: 17, color: "#FFFFFF" },
  cal: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: "#FDECEA",
    alignItems: "center",
    justifyContent: "center",
  },
  pressed: { transform: [{ scale: 0.96 }] },
});
