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
 Monster Age Picker — the "How old is …?" step of an invented kids' learning app, Munchies.

 Look (a Pinterest onboarding concept): a purple page, the question in a chunky display face, and a big
 friendly monster whose open mouth holds a row of teeth with numbers on them.

 Ours: the teeth are the picker. Drag along them and the row slides through the mouth with a detent and
 a haptic tick per tooth; the tooth in the middle stands taller and whiter, the monster's eyes follow
 your finger and the mouth stretches a little with speed. Tap a tooth to pick it. Continue makes the
 monster chomp on the chosen number (the upper jaw drops onto it, the eyes squint happily) and spring
 open again.

 autoPlay: LOOP = 9 s — rests on 5, slides to 8 (1.0–2.6 s), chomps (3.4–4.1 s), slides back to 5
 (6.2–7.8 s), rests (frame 9 = frame 0). Reduced motion: the row moves without the stretch, and the chomp
 is a short blink of the jaw.
*/

let debugState = "";

const LOOP = 9;
const MIN_AGE = 3;
const COUNT = 12;
const PITCH = 60;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};
const chompAt = (u: number) => {
  "worklet";
  // close fast, hold, reopen with a small overshoot
  if (u < 0 || u > 0.7) return 0;
  if (u < 0.16) return smoother(u / 0.16);
  if (u < 0.28) return 1;
  const k = (u - 0.28) / 0.42;
  return (1 - k) * (1 - k) * Math.cos(k * 4.2);
};
const scriptAt = (t: number) => {
  "worklet";
  let off = 2;
  if (t >= 1.0 && t < 2.6) off = 2 + 3 * smoother((t - 1.0) / 1.6);
  else if (t >= 2.6 && t < 6.2) off = 5;
  else if (t >= 6.2 && t < 7.8) off = 5 - 3 * smoother((t - 6.2) / 1.6);
  const jaw = chompAt(t - 3.4);
  return { off, jaw, chosen: t >= 3.5 && t < 6.2 ? 5 : -1 };
};

const paint = (c: string, a = 1) => {
  "worklet";
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(c));
  p.setAlphaf(a);
  return p;
};

type Geo = { cx: number; cy: number; W: number };
type Out = { off: number; v: number; jaw: number; look: number; blink: number };

const drawMonster = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  font: SkFont | null,
  big: SkFont | null,
) => {
  "worklet";
  const { cx, cy, W } = g;
  const R = W * 0.5;
  // body, ears and a soft shadow
  cv.drawOval(
    Skia.XYWHRect(cx - R * 0.8, cy + R * 0.95, R * 1.6, R * 0.22),
    paint("#3A1680", 0.25),
  );
  cv.drawCircle(cx - R * 0.72, cy - R * 0.62, R * 0.2, paint("#8E62F5"));
  cv.drawCircle(cx + R * 0.72, cy - R * 0.62, R * 0.2, paint("#8E62F5"));
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx - R * 0.3, y: cy - R * 0.5 },
      R * 1.6,
      [Skia.Color("#B996FF"), Skia.Color("#8C5CF6")],
      null,
      0,
    ),
  );
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(cx - R * 0.92, cy - R * 0.85, R * 1.84, R * 1.85),
      R * 0.8,
      R * 0.8,
    ),
    body,
  );
  // eyes: they follow the row and squint when it chomps
  const happy = clamp01((o.jaw - 0.4) / 0.4);
  for (let k = -1; k <= 1; k += 2) {
    const ex = cx + k * R * 0.34;
    const ey = cy - R * 0.5;
    cv.drawCircle(ex, ey, R * 0.2, paint("#FFFFFF"));
    const lx = Math.max(-1, Math.min(1, o.look)) * R * 0.07;
    cv.drawCircle(ex + lx, ey + R * 0.03, R * 0.095, paint("#24123F"));
    cv.drawCircle(
      ex + lx + R * 0.03,
      ey - R * 0.01,
      R * 0.03,
      paint("#FFFFFF"),
    );
    const lid = Math.max(o.blink, happy);
    if (lid > 0) {
      cv.save();
      cv.clipRect(
        Skia.XYWHRect(ex - R * 0.22, ey - R * 0.22, R * 0.44, R * 0.44 * lid),
        1,
        true,
      );
      cv.drawCircle(ex, ey, R * 0.205, paint("#9D72FA"));
      cv.restore();
      if (happy > 0) {
        const b = Skia.PathBuilder.Make();
        b.addArc(
          Skia.XYWHRect(ex - R * 0.13, ey - R * 0.02, R * 0.26, R * 0.2),
          200,
          140,
        );
        const sp = paint("#24123F", happy);
        sp.setStyle(PaintStyle.Stroke);
        sp.setStrokeWidth(5);
        sp.setStrokeCap(StrokeCap.Round);
        cv.drawPath(b.detach(), sp);
      }
    }
  }
  // the mouth: it stretches with speed and the upper jaw drops on a chomp
  const stretch = Math.min(1, Math.abs(o.v) / 12) * 0.06;
  const mw = W - 76 + W * stretch;
  const mx = cx - mw / 2;
  const mBot = cy + R * 0.72;
  const openTop = cy - R * 0.14;
  const toothH = 68;
  const closedTop = mBot - toothH - 22;
  const mTop = openTop + (closedTop - openTop) * o.jaw;
  const mouth = Skia.RRectXY(Skia.XYWHRect(mx, mTop, mw, mBot - mTop), 46, 46);
  cv.drawRRect(mouth, paint("#3B1460"));
  cv.save();
  cv.clipRRect(mouth, 1, true);
  cv.drawOval(
    Skia.XYWHRect(cx - mw * 0.34, mBot - 46, mw * 0.68, 90),
    paint("#FF7BA6"),
  );
  // upper teeth ride the jaw
  for (let i = -3; i <= 3; i++) {
    const tx = cx + i * 48;
    const tri = Skia.PathBuilder.Make();
    tri.moveTo(tx - 18, mTop - 2);
    tri.lineTo(tx + 18, mTop - 2);
    tri.lineTo(tx, mTop + 26);
    tri.close();
    cv.drawPath(tri.detach(), paint("#F4EEFF"));
  }
  // the numbered lower teeth
  for (let i = 0; i < COUNT; i++) {
    const dx = (i - o.off) * PITCH;
    if (Math.abs(dx) > mw / 2 + PITCH) continue;
    const near = clamp01(1 - Math.abs(i - o.off));
    const h = toothH - 14 + 14 * near;
    const w = 50;
    const curve = (dx * dx) / 2600;
    const ty = mBot - 16 - h + curve * 0.2;
    const r = Skia.RRectXY(
      Skia.XYWHRect(cx + dx - w / 2, ty, w, h + 30),
      16,
      16,
    );
    cv.drawRRect(r, paint(near > 0.5 ? "#FFFFFF" : "#E9DEFF"));
    if (near > 0.01) cv.drawRRect(r, paint("#FFF7C2", 0.35 * near));
    const f = near > 0.5 ? big : font;
    if (f) {
      const label = `${i + MIN_AGE}`;
      const tw = f.measureText(label).width;
      cv.drawText(
        label,
        cx + dx - tw / 2,
        ty + 34 + 4 * near,
        paint(near > 0.5 ? "#3B1460" : "#8B74B8"),
        f,
      );
    }
  }
  cv.restore();
};

type Props = { autoPlay?: boolean };

export function MonsterAgePicker({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "MAP-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "MAP-Black": require("./assets/fonts/Nunito-Black.ttf"),
  });
  const font = useFont(require("./assets/fonts/Nunito-Black.ttf"), 22);
  const big = useFont(require("./assets/fonts/Nunito-Black.ttf"), 28);

  const g: Geo = { cx: W / 2, cy: Math.round(H * 0.5), W };

  const st = useSharedValue({
    off: 2,
    v: 0,
    drag: 0,
    off0: 2,
    target: 2,
    jawT: -10,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    lastTick: 2,
    pinnedChosen: -2,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({ off: 2, v: 0, jaw: 0, look: 0, blink: 0 });
  const [age, setAge] = useState(MIN_AGE + 2);
  const [chosen, setChosen] = useState(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __monsterAge?: unknown };
    gl.__monsterAge = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__monsterAge;
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
    const bp = now % 4.2;
    const blink = bp < 0.14 ? Math.sin((bp / 0.14) * Math.PI) : 0;
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({ off: sc.off, v: 0, jaw: sc.jaw, look: 0, blink: 0 });
      // a pinned frame shows the caption autoplay would show at that moment
      if (sc.chosen !== s.pinnedChosen) {
        s.pinnedChosen = sc.chosen;
        scheduleOnRN(setChosen, sc.chosen);
      }
      return;
    }
    let jaw = 0;
    if (
      auto &&
      s.drag === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || Math.abs(s.off - 2) < 0.002)
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0.2;
      }
      const prevOff = s.off;
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      s.v = (sc.off - prevOff) / Math.max(dt, 0.001);
      s.off = sc.off;
      s.target = Math.round(sc.off);
      jaw = sc.jaw;
    } else {
      s.scripted = 0;
      if (s.drag === 0) {
        const k = reduced ? 600 : 220;
        const c = 2 * 0.9 * Math.sqrt(k);
        s.v += (k * (s.target - s.off) - c * s.v) * dt;
        s.off += s.v * dt;
      }
      jaw = reduced ? (now - s.jawT < 0.25 ? 1 : 0) : chompAt(now - s.jawT);
    }
    const tick = Math.round(s.off);
    if (tick !== s.lastTick) {
      s.lastTick = tick;
      if (s.scripted === 0) scheduleOnRN(buzz, 0);
      scheduleOnRN(setAge, Math.max(0, Math.min(COUNT - 1, tick)) + MIN_AGE);
      scheduleOnRN(setChosen, -1);
    }
    out.set({
      off: s.off,
      v: s.v,
      jaw,
      look: reduced ? 0 : Math.max(-1, Math.min(1, s.v / 6)),
      blink,
    });
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `off=${s.off.toFixed(2)} target=${s.target} jaw=${jaw.toFixed(2)} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => (out.get().jaw > 0.5 ? 1 : 0),
    (v, prev) => {
      if (v === 1 && prev === 0)
        scheduleOnRN(setChosen, Math.round(out.get().off));
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetX([-6, 6])
    .onStart(() => {
      const s = st.get();
      s.drag = 1;
      s.scripted = 0;
      s.off0 = s.off;
    })
    .onUpdate((e) => {
      const s = st.get();
      let off = s.off0 - e.translationX / PITCH;
      if (off < 0) off = -(1 - Math.exp(off)) * 0.5;
      if (off > COUNT - 1)
        off = COUNT - 1 + (1 - Math.exp(-(off - COUNT + 1))) * 0.5;
      s.v = -e.velocityX / PITCH;
      s.off = off;
      s.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.drag = 0;
      const proj = s.off - (e.velocityX / PITCH) * 0.18;
      s.target = Math.max(0, Math.min(COUNT - 1, Math.round(proj)));
      s.v = -e.velocityX / PITCH;
      s.lastTouch = clock.get();
    });
  const tap = Gesture.Tap().onEnd((e) => {
    const s = st.get();
    const i = Math.round(s.off + (e.absoluteX - g.cx) / PITCH);
    s.target = Math.max(0, Math.min(COUNT - 1, i));
    s.scripted = 0;
    s.lastTouch = clock.get();
  });
  const gesture = Gesture.Race(pan, tap);

  const chomp = () => {
    "worklet";
    const s = st.get();
    s.jawT = clock.get();
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const stepBy = (d: number) => {
    "worklet";
    const s = st.get();
    s.target = Math.max(0, Math.min(COUNT - 1, s.target + d));
    s.scripted = 0;
    s.lastTouch = clock.get();
  };
  const onContinue = () => {
    buzz(1);
    scheduleOnUI(chomp);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawMonster(cv, g, out.get(), font, big)),
  );

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.top, { paddingTop: insets.top + 10 }]}>
        <View
          style={styles.progress}
          accessible
          accessibilityLabel="Step 2 of 4"
        >
          {[0, 1, 2, 3].map((i) => (
            <View key={i} style={[styles.seg, i < 2 && styles.segOn]} />
          ))}
        </View>
        <Text style={styles.title} accessibilityRole="header">
          How old is Mia?
        </Text>
        <Text style={styles.sub}>
          {chosen >= 0
            ? `Yum! ${chosen + MIN_AGE} years old`
            : "Slide along the teeth"}
        </Text>
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={gesture}>
        <View
          style={[styles.hit, { top: g.cy - W * 0.12, height: W * 0.5 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Age"
          accessibilityValue={{ text: `${age} years old` }}
          accessibilityActions={[{ name: "increment" }, { name: "decrement" }]}
          onAccessibilityAction={(e) =>
            scheduleOnUI(
              stepBy,
              e.nativeEvent.actionName === "increment" ? 1 : -1,
            )
          }
        />
      </GestureDetector>
      <Pressable
        onPress={onContinue}
        accessibilityRole="button"
        accessibilityLabel={`Continue with ${age} years old`}
        style={({ pressed }) => [
          styles.cta,
          { bottom: insets.bottom + 20 },
          pressed && styles.pressed,
        ]}
      >
        <Text style={styles.ctaText}>Continue</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#6E3DE8" },
  top: {
    position: "absolute",
    left: 24,
    right: 24,
    alignItems: "center",
    zIndex: 1,
  },
  progress: { flexDirection: "row", gap: 6, marginBottom: 24 },
  seg: {
    width: 40,
    height: 6,
    borderRadius: 3,
    backgroundColor: "rgba(255,255,255,0.25)",
  },
  segOn: { backgroundColor: "#FFD84D" },
  title: { fontFamily: "MAP-Title", fontSize: 32, color: "#FFFFFF" },
  sub: {
    fontFamily: "MAP-Black",
    fontSize: 16,
    color: "rgba(255,255,255,0.75)",
    marginTop: 8,
  },
  hit: { position: "absolute", left: 24, right: 24 },
  cta: {
    position: "absolute",
    left: 24,
    right: 24,
    height: 58,
    borderRadius: 29,
    backgroundColor: "#FFD84D",
    alignItems: "center",
    justifyContent: "center",
    borderBottomWidth: 4,
    borderBottomColor: "#E0AE1F",
  },
  ctaText: { fontFamily: "MAP-Black", fontSize: 18, color: "#3B1460" },
  pressed: { transform: [{ scale: 0.96 }] },
});
