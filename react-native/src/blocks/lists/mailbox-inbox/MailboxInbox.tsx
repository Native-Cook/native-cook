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
 Mailbox Inbox — the inbox of an invented personal-mail app, Postbox.

 Look (Pinterest mail and envelope icons): a little red letterbox on a post with its flag up.

 Ours: the letterbox is the inbox. Tap it and its door swings open, the flag drops, and the letters fly
 out one after another, each envelope turning and growing into its row of the list (the flap lifts and
 the envelope becomes the card with the sender and preview). Swipe a row to the right and it folds back
 into an envelope and flies home into the box. Tap the box again and every letter goes back in, the door
 shuts and the flag goes up.

 autoPlay: LOOP = 9 s — closed with the flag up, opens 1.0–2.5 (letters staggered 0.12 s), the third
 letter is filed 4.2–5.0, everything goes back in 6.2–7.6 (frame 9 = frame 0). Reduced motion: rows fade
 in and out in place; the door does not swing.
*/

let debugState = "";

const LOOP = 9;
const MAIL = [
  {
    from: "Grandma Rose",
    text: "Your parcel of cookies is on its way!",
    time: "9:41",
    color: "#FF8A65",
  },
  {
    from: "Kinto Bank",
    text: "Your October statement is ready",
    time: "8:20",
    color: "#5A6BD0",
  },
  {
    from: "Pip & Co. Studio",
    text: "Invitation: spring open house",
    time: "Yesterday",
    color: "#2F9E62",
  },
  {
    from: "City Library",
    text: "The book you reserved is waiting",
    time: "Mon",
    color: "#E0A21F",
  },
];
const N = MAIL.length;

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
  let open = 0;
  if (t >= 1.0 && t < 2.5) open = (t - 1.0) / 1.5;
  else if (t >= 2.5 && t < 6.2) open = 1;
  else if (t >= 6.2 && t < 7.6) open = 1 - (t - 6.2) / 1.4;
  const filed =
    t >= 4.2 && t < 6.2
      ? smoother((t - 4.2) / 0.8)
      : t >= 6.2 && t < 7.6
        ? 1
        : 0;
  return { open, filed2: filed };
};
/** How far letter i is out of the box for a global open amount (staggered). */
const letterK = (open: number, i: number) => {
  "worklet";
  return smoother((open * 1.5 - i * 0.12) / 1.0);
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

type Geo = { cx: number; cy: number; s: number };
type Out = { open: number; filed: number[]; t: number };

const drawBox = (cv: SkCanvas, g: Geo, o: Out, reduced: boolean) => {
  "worklet";
  const { cx, cy, s } = g;
  const door = reduced ? 0 : smoother(o.open * 2.2);
  const flag = 1 - smoother(o.open * 2.4);
  // post and ground
  cv.drawOval(
    Skia.XYWHRect(cx - s * 0.9, cy + s * 1.55, s * 1.8, s * 0.3),
    paint("#8C6A50", 0.14),
  );
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(cx - s * 0.12, cy + s * 0.4, s * 0.24, s * 1.3),
      6,
      6,
    ),
    paint("#9C7356"),
  );
  // box body: a tunnel letterbox seen three-quarter
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(cx - s * 0.62, cy - s * 0.5, s * 1.46, s * 0.95),
      s * 0.47,
      s * 0.47,
    ),
    paint("#C8382C"),
  );
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(cx - s * 0.7, cy - s * 0.55, s * 1.4, s * 0.98),
      s * 0.47,
      s * 0.47,
    ),
    paint("#E8473A"),
  );
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(cx - s * 0.5, cy - s * 0.48, s * 0.9, s * 0.18),
      s * 0.09,
      s * 0.09,
    ),
    paint("#FF7A6B", 0.55),
  );
  // mouth: dark opening when the door swings away
  const mw = s * 0.62;
  const mh = s * 0.82;
  const mx = cx - s * 0.7 - mw * 0.1;
  const my = cy - s * 0.48;
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(mx, my, mw, mh), mw / 2, mw / 2),
    paint("#7A1E18", door),
  );
  // door: hinged on its bottom edge, it swings down toward the viewer
  cv.save();
  cv.translate(mx + mw / 2, my + mh);
  cv.scale(1, Math.max(0.05, Math.cos(door * Math.PI * 0.55)));
  cv.translate(-(mx + mw / 2), -(my + mh));
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(mx, my, mw, mh), mw / 2, mw / 2),
    paint(door > 0.5 ? "#C8382C" : "#F25A4C"),
  );
  cv.drawCircle(mx + mw / 2, my + mh * 0.3, s * 0.06, paint("#FFE07A"));
  cv.restore();
  // flag on the side, up when there is mail
  const fx = cx + s * 0.62;
  const fy = cy - s * 0.05;
  cv.save();
  cv.translate(fx, fy);
  cv.rotate(-90 * flag, 0, 0);
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(-s * 0.04, -s * 0.05, s * 0.62, s * 0.1), 3, 3),
    paint("#3B3F4A"),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(s * 0.4, -s * 0.05, s * 0.26, s * 0.26), 3, 3),
    paint("#FFC23D"),
  );
  cv.restore();
  cv.drawCircle(fx, fy, s * 0.05, paint("#3B3F4A"));
};

type Props = { autoPlay?: boolean };

export function MailboxInbox({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "MBI-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "MBI-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "MBI-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });

  const g: Geo = { cx: W / 2, cy: insets.top + 150, s: 88 };
  const listTop = insets.top + 296;
  const ROW = 78;

  const st = useSharedValue({
    open: 0,
    target: 0,
    filed: [0, 0, 0, 0],
    fileT: [0, 0, 0, 0],
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ open: 0, filed: [0, 0, 0, 0], t: 0 });

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __mailbox?: unknown };
    gl.__mailbox = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__mailbox;
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
      out.set({ open: sc.open, filed: [0, 0, sc.filed2, 0], t: dbg });
      return;
    }
    if (
      auto &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || (s.open < 0.001 && s.target === 0))
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.open = sc.open;
      s.target = sc.open > 0.5 ? 1 : 0;
      s.filed = [0, 0, sc.filed2, 0];
      out.set({ open: s.open, filed: s.filed, t: now });
      return;
    }
    s.scripted = 0;
    const speed = reduced ? 4 : 1 / 1.4;
    if (s.open < s.target) s.open = Math.min(s.target, s.open + dt * speed);
    if (s.open > s.target) s.open = Math.max(s.target, s.open - dt * speed);
    for (let i = 0; i < N; i++) {
      // a filed letter stays filed until the box closes; closing brings them all home anyway
      if (s.fileT[i] > 0) s.filed[i] = Math.min(1, s.filed[i] + dt / 0.8);
      if (s.open <= 0.001) {
        s.filed[i] = 0;
        s.fileT[i] = 0;
      }
    }
    out.set({
      open: s.open,
      filed: [s.filed[0], s.filed[1], s.filed[2], s.filed[3]],
      t: now,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `open=${s.open.toFixed(2)} target=${s.target} filed=${s.filed[0].toFixed(1)},${s.filed[1].toFixed(1)},${s.filed[2].toFixed(1)},${s.filed[3].toFixed(1)} scripted=${s.scripted}`,
      );
    }
  });

  const toggle = () => {
    "worklet";
    const s = st.get();
    s.target = s.target > 0.5 ? 0 : 1;
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const fileRow = (i: number) => {
    "worklet";
    const s = st.get();
    s.fileT[i] = 1;
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const onBox = () => {
    buzz(1);
    scheduleOnUI(toggle);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawBox(cv, g, out.get(), reduced)),
  );
  const flagCount = useAnimatedStyle(() => ({
    opacity: 1 - clamp01(out.get().open * 3),
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { top: insets.top + 8 }]}>
        <Text style={styles.title}>Postbox</Text>
        <Pressable
          style={styles.round}
          accessibilityRole="button"
          accessibilityLabel="Write a letter"
        >
          <Ionicons name="create-outline" size={21} color="#2B2F3A" />
        </Pressable>
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <Pressable
        onPress={onBox}
        style={[
          styles.boxHit,
          {
            top: g.cy - g.s * 0.7,
            left: g.cx - g.s,
            width: g.s * 2,
            height: g.s * 2.2,
          },
        ]}
        accessibilityRole="button"
        accessibilityLabel={`Letterbox, ${N} new letters. Tap to open or close`}
      />
      <Animated.View
        style={[
          styles.badge,
          { top: g.cy - g.s * 0.85, left: g.cx + g.s * 0.55 },
          flagCount,
        ]}
        pointerEvents="none"
      >
        <Text style={styles.badgeText}>{N}</Text>
      </Animated.View>
      <Text style={[styles.section, { top: listTop - 34 }]}>Today’s post</Text>
      {MAIL.map((m, i) => (
        <Letter
          key={m.from}
          i={i}
          out={out}
          g={g}
          top={listTop + i * (ROW + 10)}
          W={W}
          onFile={() => scheduleOnUI(fileRow, i)}
        />
      ))}
    </View>
  );
}

function Letter({
  i,
  out,
  g,
  top,
  W,
  onFile,
}: {
  i: number;
  out: SharedValue<Out>;
  g: Geo;
  top: number;
  W: number;
  onFile: () => void;
}) {
  const m = MAIL[i];
  const rowW = W - 32;
  const dx = useSharedValue(0);
  const style = useAnimatedStyle(() => {
    const o = out.get();
    const k = letterK(o.open, i) * (1 - o.filed[i]);
    // from the box's mouth: small, turned, then flying down into its row
    const fromX = g.cx - g.s * 0.68 - (16 + rowW / 2);
    const fromY = g.cy - g.s * 0.1 - top - 39;
    const arc = Math.sin(Math.PI * k) * -60;
    const x = fromX * (1 - k) + dx.get();
    // rows below a filed letter close the gap
    let shift = 0;
    for (let j = 0; j < i; j++) shift += smoother(o.filed[j] * 1.4 - 0.3) * 88;
    const y = fromY * (1 - k) + arc - shift;
    const sc = 0.22 + 0.78 * k;
    return {
      opacity: k < 0.02 ? 0 : 1,
      transform: [
        { translateX: x },
        { translateY: y },
        { rotate: `${(1 - k) * (i % 2 ? 24 : -18)}deg` },
        { scale: sc },
      ],
    };
  });
  const envelope = useAnimatedStyle(() => {
    const k = letterK(out.get().open, i) * (1 - out.get().filed[i]);
    return { opacity: 1 - clamp01((k - 0.45) / 0.3) };
  });
  const content = useAnimatedStyle(() => {
    const k = letterK(out.get().open, i) * (1 - out.get().filed[i]);
    return { opacity: clamp01((k - 0.55) / 0.3) };
  });
  const swipe = Gesture.Pan()
    .activeOffsetX([12, 999])
    .onUpdate((e) => {
      dx.set(Math.max(0, e.translationX));
    })
    .onEnd((e) => {
      if (e.translationX > 90 || e.velocityX > 700) scheduleOnRN(onFile);
      dx.set(0);
    });
  return (
    <GestureDetector gesture={swipe}>
      <Animated.View
        style={[styles.row, { top, width: rowW }, style]}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${m.from}: ${m.text}. ${m.time}`}
        accessibilityHint="Swipe right to file it"
      >
        <Animated.View
          style={[StyleSheet.absoluteFill, styles.envelope, envelope]}
        >
          <View style={styles.flap} />
          <View style={[styles.stamp, { backgroundColor: m.color }]} />
        </Animated.View>
        <Animated.View style={[styles.rowInner, content]}>
          <View style={[styles.avatar, { backgroundColor: m.color }]}>
            <Text style={styles.initial}>{m.from[0]}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <View style={styles.rowTop}>
              <Text style={styles.from}>{m.from}</Text>
              <Text style={styles.time}>{m.time}</Text>
            </View>
            <Text style={styles.text} numberOfLines={1}>
              {m.text}
            </Text>
          </View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F4F1EA" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  title: {
    fontFamily: "MBI-Bold",
    fontSize: 30,
    color: "#2B2F3A",
    letterSpacing: -0.5,
  },
  round: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  boxHit: { position: "absolute" },
  badge: {
    position: "absolute",
    minWidth: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: "#2B2F3A",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 6,
  },
  badgeText: { fontFamily: "MBI-Bold", fontSize: 13, color: "#FFFFFF" },
  section: {
    position: "absolute",
    left: 20,
    fontFamily: "MBI-SemiBold",
    fontSize: 15,
    color: "#8A8578",
  },
  row: {
    position: "absolute",
    left: 16,
    height: 78,
    borderRadius: 18,
    backgroundColor: "#FFFFFF",
    shadowColor: "#2B2F3A",
    shadowOpacity: 0.06,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
  envelope: {
    borderRadius: 18,
    backgroundColor: "#FFF8EC",
    borderWidth: 1,
    borderColor: "#EADFC8",
    overflow: "hidden",
  },
  flap: {
    position: "absolute",
    top: -60,
    left: "50%",
    marginLeft: -60,
    width: 120,
    height: 120,
    transform: [{ rotate: "45deg" }],
    backgroundColor: "#F3E7CF",
  },
  stamp: {
    position: "absolute",
    top: 10,
    right: 12,
    width: 22,
    height: 26,
    borderRadius: 3,
  },
  rowInner: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: "center",
    justifyContent: "center",
  },
  initial: { fontFamily: "MBI-Bold", fontSize: 18, color: "#FFFFFF" },
  rowTop: { flexDirection: "row", justifyContent: "space-between" },
  from: { fontFamily: "MBI-SemiBold", fontSize: 16, color: "#2B2F3A" },
  time: { fontFamily: "MBI-Regular", fontSize: 13, color: "#9A958A" },
  text: {
    fontFamily: "MBI-Regular",
    fontSize: 14,
    color: "#6B6760",
    marginTop: 3,
  },
});
