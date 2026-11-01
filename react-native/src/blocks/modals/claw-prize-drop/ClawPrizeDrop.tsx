import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  useImage,
  type SkCanvas,
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
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

/*
 Claw Prize Drop — the daily claw game of an invented shopping app, Grabbit.

 Look (a Pinterest claw-machine game: a sky-blue page, a candy-striped awning over a glass case of cute
 prizes on clouds, a claw with a number tag, a red base with a prize chute and a round play button).

 Ours: the claw is yours to steer — drag across the glass and it slides on its rail with a little
 swing — and Grab sends it down. It drops on its cable, the jaws close, and if a prize is under it the
 prize comes up with it, swaying; the claw carries it to the chute, opens, and the prize tumbles down
 into the base, where a coupon pops out. Miss and the claw comes up empty with a shrug.

 autoPlay: LOOP = 9 s — the claw slides over the star 0.4–1.2, drops 1.2–1.9, grabs 1.9–2.15, lifts
 2.15–2.85, carries to the chute 2.85–3.5, lets go 3.5 (prize falls to 3.9), the coupon shows 4.0–5.6,
 the claw goes home 5.6–6.6 and a new star drops into the case 7.6–8.4 (frame 9 = frame 0).
 Reduced motion: no swing or tumble; the prize appears in the chute.
*/

let debugState = "";

const LOOP = 9;
const PRIZES = 6;
const NAMES = [
  "Plush bear",
  "Bunny ball",
  "Sleepy star",
  "Mint capsule",
  "Coin sack",
  "Gift coupon",
];
const REWARD = [
  "15% off toys",
  "Free delivery",
  "20% off snacks",
  "Mystery gift",
  "+200 coins",
  "10% off your basket",
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

type Geo = {
  x: number;
  y: number;
  w: number;
  h: number;
  railY: number;
  floorY: number;
  chuteX: number;
  homeX: number;
  px: number[];
  py: number[];
  ps: number;
};
type Grab = { at: number; fromX: number; prize: number };
/** The grab sequence u seconds after Grab, aimed at prize p (-1 = miss). */
const grabAt = (g: Geo, gr: Grab, u: number) => {
  "worklet";
  let x = gr.fromX;
  let drop = 0;
  let close = 0;
  let carry = 0;
  let fall = -1;
  if (u >= 0) {
    const down = smoother(u / 0.7);
    const shut = smoother((u - 0.7) / 0.25);
    const up = smoother((u - 0.95) / 0.7);
    const move = smoother((u - 1.65) / 0.65);
    const open = u >= 2.3 ? smoother((u - 2.3) / 0.2) : 0;
    drop = down * (1 - up);
    close = shut * (1 - open);
    if (gr.prize >= 0) {
      x = lerp(gr.fromX, g.chuteX, move);
      carry = u >= 0.9 && u < 2.3 ? 1 : 0;
      fall = u >= 2.3 ? u - 2.3 : -1;
    }
  }
  return { x, drop, close, carry, fall };
};
const scriptAt = (g: Geo, t: number) => {
  "worklet";
  const target = g.px[2];
  if (t < 1.2) {
    return {
      x: lerp(g.homeX, target, smoother((t - 0.4) / 0.8)),
      drop: 0,
      close: 0,
      carry: 0,
      fall: -1,
      prize: 2,
      coupon: 0,
      respawn: 1,
    };
  }
  const gr = { at: 1.2, fromX: target, prize: 2 };
  const r = grabAt(g, gr, t - 1.2);
  let x = r.x;
  if (t >= 5.6) x = lerp(g.chuteX, g.homeX, smoother((t - 5.6) / 1.0));
  const coupon =
    t >= 4.0 && t < 5.6
      ? smoother((t - 4.0) / 0.3) * (1 - smoother((t - 5.2) / 0.4))
      : 0;
  const respawn = t >= 7.6 ? smoother((t - 7.6) / 0.8) : 0;
  return {
    x,
    drop: r.drop,
    close: r.close,
    carry: r.carry,
    fall: r.fall,
    prize: 2,
    coupon,
    respawn,
  };
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

type Out = {
  x: number;
  drop: number;
  close: number;
  carry: number;
  fall: number;
  prize: number;
  respawn: number;
  swing: number;
  t: number;
};

const drawMachine = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  imgs: (SkImage | null)[],
  reduced: boolean,
) => {
  "worklet";
  const { x, y, w, h } = g;
  // case back, clouds on the floor
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 18, 18),
    paint("#CDEBFF"),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x + 8, y + 8, w - 16, h - 16), 12, 12),
    paint("#E5F5FF"),
  );
  for (let k = 0; k < 7; k++)
    cv.drawCircle(
      x + 20 + (k * (w - 40)) / 6,
      g.floorY + 18,
      34,
      paint("#FFFFFF"),
    );
  // prizes on the pile (the grabbed one leaves it)
  for (let i = 0; i < PRIZES; i++) {
    const img = imgs[i];
    if (!img) continue;
    let px = g.px[i];
    let py = g.py[i];
    let sc = 1;
    let rot = 0;
    let alpha = 1;
    if (i === o.prize) {
      if (o.carry > 0) {
        px = o.x + o.swing * 0.6;
        py = g.railY + 40 + (g.floorY - g.railY - 40) * o.drop + g.ps * 0.55;
        rot = o.swing * 0.8;
      } else if (o.fall >= 0) {
        if (o.fall > 0.5) {
          if (o.respawn <= 0) continue;
          // a new prize drops back onto the pile
          sc = 0.6 + 0.4 * o.respawn;
          py = g.py[i] - (1 - o.respawn) * 80;
          alpha = o.respawn;
        } else {
          const f = o.fall / 0.5;
          px = g.chuteX;
          py =
            g.railY + 40 + g.ps * 0.55 + (y + h + 40 - (g.railY + 40)) * f * f;
          rot = reduced ? 0 : f * 140;
          alpha = 1 - clamp01((f - 0.8) / 0.2);
        }
      }
    }
    const s = (g.ps / img.width()) * sc;
    cv.save();
    cv.translate(px, py);
    cv.rotate(rot, 0, 0);
    cv.scale(s, s);
    const p = Skia.Paint();
    p.setAlphaf(alpha);
    cv.drawImage(img, -img.width() / 2, -img.height() / 2, p);
    cv.restore();
  }
  // rail, cable and claw
  cv.drawRect(Skia.XYWHRect(x + 6, g.railY - 8, w - 12, 8), paint("#9AA9BF"));
  const cx = o.x + o.swing;
  const cy = g.railY + 30 + (g.floorY - g.railY - 40) * o.drop;
  cv.drawLine(o.x, g.railY, cx, cy - 18, paint("#5D6B80", 1, 3));
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(cx - 18, cy - 24, 36, 22), 8, 8),
    paint("#3A4558"),
  );
  const open = 1 - o.close;
  for (let k = -1; k <= 1; k += 2) {
    const jaw = Skia.PathBuilder.Make();
    jaw.moveTo(cx + k * 10, cy - 4);
    jaw.quadTo(
      cx + k * (20 + 10 * open),
      cy + 18,
      cx + k * (6 + 10 * open),
      cy + 34,
    );
    cv.drawPath(jaw.detach(), paint("#3A4558", 1, 7));
  }
  // glass shine and the frame
  cv.drawRect(
    Skia.XYWHRect(x + 18, y + 20, 10, h - 60),
    paint("#FFFFFF", 0.35),
  );
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 18, 18),
    paint("#FF6B6B", 1, 6),
  );
  // chute opening on the left of the floor
  cv.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(g.chuteX - 34, g.floorY - 6, 68, 16), 6, 6),
    paint("#2C3446", 0.85),
  );
};

type Props = { autoPlay?: boolean };

export function ClawPrizeDrop({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "CPD-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "CPD-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "CPD-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });
  const i0 = useImage(require("./assets/art/prize-bear.png"));
  const i1 = useImage(require("./assets/art/prize-bunny.png"));
  const i2 = useImage(require("./assets/art/prize-star.png"));
  const i3 = useImage(require("./assets/art/prize-capsule.png"));
  const i4 = useImage(require("./assets/art/prize-coinbag.png"));
  const i5 = useImage(require("./assets/art/prize-ticket.png"));

  const mx = 28;
  const my = insets.top + 150;
  const mw = W - 56;
  const mh = Math.round(H * 0.42);
  const ps = Math.round(mw * 0.24);
  const floorY = my + mh - 40;
  const g: Geo = {
    x: mx,
    y: my,
    w: mw,
    h: mh,
    railY: my + 22,
    floorY,
    chuteX: mx + 50,
    homeX: mx + mw * 0.5,
    ps,
    px: [
      mx + mw * 0.36,
      mx + mw * 0.58,
      mx + mw * 0.78,
      mx + mw * 0.47,
      mx + mw * 0.68,
      mx + mw * 0.88,
    ],
    py: [
      floorY - ps * 0.35,
      floorY - ps * 0.3,
      floorY - ps * 0.42,
      floorY - ps * 0.85,
      floorY - ps * 0.82,
      floorY - ps * 0.7,
    ],
  };

  const st = useSharedValue({
    x: g.homeX,
    sw: 0,
    swV: 0,
    drag: 0,
    x0: 0,
    grab: { at: -100, fromX: 0, prize: -1 } as Grab,
    taken: [0, 0, 0, 0, 0, 0],
    coins: 1200,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    x: g.homeX,
    drop: 0,
    close: 0,
    carry: 0,
    fall: -1,
    prize: -1,
    respawn: 1,
    swing: 0,
    t: 0,
  });
  const couponV = useSharedValue(0);
  const prizeOut = useSharedValue(-1);
  const [won, setWon] = useState(2);

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __claw?: unknown };
    gl.__claw = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__claw;
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
      const sc = scriptAt(g, dbg % LOOP);
      out.set({
        x: sc.x,
        drop: sc.drop,
        close: sc.close,
        carry: sc.carry,
        fall: sc.fall,
        prize: sc.prize,
        respawn: sc.respawn,
        swing: 0,
        t: dbg,
      });
      couponV.set(sc.coupon);
      prizeOut.set(2);
      return;
    }
    const busy = now - s.grab.at < 3.2;
    if (
      auto &&
      s.drag === 0 &&
      !busy &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || Math.abs(s.x - g.homeX) < 1)
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      const prevX = s.x;
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(g, s.loopT);
      s.x = sc.x;
      const vx = (s.x - prevX) / Math.max(dt, 0.001);
      s.swV += (-90 * s.sw - 7 * s.swV - vx * 0.9) * dt;
      s.sw += s.swV * dt;
      out.set({
        x: sc.x,
        drop: sc.drop,
        close: sc.close,
        carry: sc.carry,
        fall: sc.fall,
        prize: sc.prize,
        respawn: sc.respawn,
        swing: reduced ? 0 : s.sw,
        t: now,
      });
      couponV.set(sc.coupon);
      prizeOut.set(2);
      return;
    }
    s.scripted = 0;
    const u = now - s.grab.at;
    const r = grabAt(g, s.grab, u);
    const prevX = s.x;
    if (u >= 0 && u < 3.2) s.x = r.x;
    else if (u >= 3.2 && u < 4.2)
      s.x = lerp(g.chuteX, g.homeX, smoother(u - 3.2));
    const vx = (s.x - prevX) / Math.max(dt, 0.001);
    s.swV += (-90 * s.sw - 7 * s.swV - vx * 0.9) * dt;
    s.sw += s.swV * dt;
    const respawn = s.grab.prize >= 0 ? smoother((u - 4.6) / 0.8) : 1;
    out.set({
      x: s.x,
      drop: r.drop,
      close: r.close,
      carry: r.carry,
      fall: r.fall,
      prize: s.grab.prize,
      respawn,
      swing: reduced ? 0 : s.sw,
      t: now,
    });
    couponV.set(
      s.grab.prize >= 0 && u >= 2.8 && u < 4.6
        ? smoother((u - 2.8) / 0.3) * (1 - smoother((u - 4.2) / 0.4))
        : 0,
    );
    prizeOut.set(s.grab.prize);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `x=${s.x.toFixed(0)} u=${u.toFixed(2)} prize=${s.grab.prize} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => (couponV.get() > 0.5 ? prizeOut.get() : -1),
    (v, prev) => {
      if (v !== prev && v >= 0) {
        scheduleOnRN(setWon, v);
        scheduleOnRN(buzz, 2);
      }
    },
  );

  const pan = Gesture.Pan()
    .onStart(() => {
      const s = st.get();
      if (clock.get() - s.grab.at < 4.2) return;
      s.drag = 1;
      s.scripted = 0;
      s.x0 = s.x;
    })
    .onUpdate((e) => {
      const s = st.get();
      if (s.drag === 0) return;
      s.x = Math.max(g.x + 40, Math.min(g.x + g.w - 30, s.x0 + e.translationX));
      s.lastTouch = clock.get();
    })
    .onFinalize(() => {
      const s = st.get();
      s.drag = 0;
      s.lastTouch = clock.get();
    });

  const grab = () => {
    "worklet";
    const s = st.get();
    const now = clock.get();
    if (now - s.grab.at < 4.2) return;
    let best = -1;
    let bd = g.ps * 0.45;
    for (let i = 0; i < PRIZES; i++) {
      const d = Math.abs(s.x - g.px[i]);
      if (d < bd) {
        bd = d;
        best = i;
      }
    }
    s.grab = { at: now, fromX: s.x, prize: best };
    s.scripted = 0;
    s.lastTouch = now;
  };
  const onGrab = () => {
    buzz(1);
    scheduleOnUI(grab);
  };

  const picture = useDerivedValue(() =>
    createPicture((cv) =>
      drawMachine(cv, g, out.get(), [i0, i1, i2, i3, i4, i5], reduced),
    ),
  );
  const coupon = useAnimatedStyle(() => ({
    opacity: couponV.get(),
    transform: [
      { translateY: (1 - couponV.get()) * 20 },
      { scale: 0.9 + 0.1 * couponV.get() },
    ],
  }));

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { top: insets.top + 8 }]}>
        <Pressable
          style={styles.close}
          accessibilityRole="button"
          accessibilityLabel="Close"
        >
          <Ionicons name="close" size={22} color="#FFFFFF" />
        </Pressable>
        <View style={styles.coins} accessible accessibilityLabel="1,200 coins">
          <View style={styles.coinDot} />
          <Text style={styles.coinText}>1,200</Text>
        </View>
      </View>
      <View style={[styles.signWrap, { top: insets.top + 64 }]}>
        <View style={styles.sign}>
          <Text style={styles.signText}>Grabbit</Text>
        </View>
      </View>
      <View
        style={[styles.awning, { top: my - 26, left: mx - 10, width: mw + 20 }]}
      >
        {Array.from({ length: 9 }).map((_, k) => (
          <View
            key={k}
            style={[
              styles.stripe,
              { backgroundColor: k % 2 ? "#FFFFFF" : "#FF5A5A" },
            ]}
          />
        ))}
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={pan}>
        <View
          style={[
            styles.glassHit,
            { top: my, left: mx, width: mw, height: mh },
          ]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Claw. Drag sideways to move it over a prize"
        />
      </GestureDetector>
      <View
        style={[
          styles.base,
          {
            top: my + mh - 6,
            left: mx - 10,
            width: mw + 20,
            height: 200,
          },
        ]}
      >
        <View style={styles.chute}>
          <Animated.View
            style={[styles.coupon, coupon]}
            accessibilityLiveRegion="polite"
          >
            <Ionicons name="gift" size={18} color="#FF5A5A" />
            <View>
              <Text style={styles.couponTitle}>{NAMES[won]}</Text>
              <Text style={styles.couponText}>{REWARD[won]}</Text>
            </View>
          </Animated.View>
        </View>
        <View style={styles.controls}>
          <Text style={styles.hint}>
            Slide the claw, then grab · 3 tries left
          </Text>
          <Pressable
            onPress={onGrab}
            accessibilityRole="button"
            accessibilityLabel="Grab"
            style={({ pressed }) => [styles.grab, pressed && styles.pressed]}
          >
            <Ionicons name="hand-left" size={24} color="#FFFFFF" />
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#8FD3FF" },
  header: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  close: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#3D8BFF",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 3,
    borderColor: "#FFFFFF",
  },
  coins: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 36,
    paddingHorizontal: 12,
    borderRadius: 18,
    backgroundColor: "#FFFFFF",
  },
  coinDot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: "#FFC23D",
    borderWidth: 3,
    borderColor: "#FFDE7A",
  },
  coinText: { fontFamily: "CPD-Black", fontSize: 15, color: "#2C3446" },
  signWrap: { position: "absolute", left: 0, right: 0, alignItems: "center" },
  sign: {
    height: 52,
    paddingHorizontal: 26,
    borderRadius: 18,
    backgroundColor: "#FF5A5A",
    justifyContent: "center",
    borderWidth: 4,
    borderColor: "#FFFFFF",
  },
  signText: { fontFamily: "CPD-Title", fontSize: 26, color: "#FFFFFF" },
  awning: {
    position: "absolute",
    height: 34,
    flexDirection: "row",
    borderRadius: 10,
    overflow: "hidden",
  },
  stripe: { flex: 1 },
  glassHit: { position: "absolute" },
  base: {
    position: "absolute",
    backgroundColor: "#E84A4A",
    borderRadius: 18,
    padding: 14,
    gap: 12,
  },
  chute: {
    height: 84,
    borderRadius: 14,
    backgroundColor: "#2C3446",
    justifyContent: "center",
    paddingHorizontal: 12,
  },
  coupon: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    height: 58,
    borderRadius: 12,
    backgroundColor: "#FFF4E6",
    paddingHorizontal: 12,
  },
  couponTitle: { fontFamily: "CPD-Black", fontSize: 15, color: "#2C3446" },
  couponText: { fontFamily: "CPD-Bold", fontSize: 13, color: "#E84A4A" },
  controls: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  hint: { fontFamily: "CPD-Bold", fontSize: 14, color: "#FFE3E3" },
  grab: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "#FF8A3D",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 4,
    borderColor: "#FFFFFF",
  },
  pressed: { transform: [{ scale: 0.96 }] },
});
