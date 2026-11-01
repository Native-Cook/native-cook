import { Ionicons } from "@expo/vector-icons";
import {
  Canvas,
  createPicture,
  Picture,
  Skia,
  useImage,
  type SkCanvas,
  type SkImage,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedProps,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

/*
 Cashback Cloud Card — the rewards tab of an invented bank, Puffy.

 Look (a Pinterest cashback card: a blue card with a soft cream clay cloud holding a gold coin over
 "$0.36 Cashback" and a "New" chip).

 Ours: the cloud holds your cashback, and you collect it by squeezing. Press and hold the cloud and it
 squashes in your fingers, then starts to rain: gold coins tumble out one by one, spinning as they fall,
 and drop into the balance below — the cashback counts down as your main balance counts up, with a tick
 for every coin. Let go and the cloud springs back into shape with a wobble.

 autoPlay: LOOP = 9 s — rests, squeezed 1.0–1.3, six coins rain 1.3–3.6 and the amounts count, the cloud
 springs back 3.8, holds; the week's cashback comes back 7.2–8.2 (frame 9 = frame 0). Reduced motion:
 no squash or tumble; coins fade down in place.
*/

const LOOP = 9;
const COINS = 6;
const CASH = 0.36;
const MAIN = 1284.5;

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};
const wobble = (u: number) => {
  "worklet";
  return u < 0 ? 0 : Math.exp(-6 * u) * Math.sin(16 * u);
};
/** coin i's fall progress at time u since the rain started (0 not yet, 1 landed) */
const coinK = (u: number, i: number) => {
  "worklet";
  return clamp01((u - i * 0.36) / 0.6);
};
const scriptAt = (t: number) => {
  "worklet";
  const squeeze = t >= 1.0 && t < 3.8 ? smoother((t - 1.0) / 0.3) : 0;
  const release = t >= 3.8 ? wobble(t - 3.8) * 0.4 : 0;
  const rainU = t >= 1.3 && t < 7.2 ? t - 1.3 : -1;
  const refill = t >= 7.2 ? smoother((t - 7.2) / 1.0) : 0;
  return { squeeze, release, rainU, refill };
};

type Geo = {
  x: number;
  y: number;
  w: number;
  h: number;
  cloudY: number;
  landY: number;
};
type Out = {
  squeeze: number;
  release: number;
  rainU: number;
  refill: number;
  t: number;
};

const drawCard = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  cloud: SkImage | null,
  coin: SkImage | null,
  edge: SkImage | null,
  reduced: boolean,
) => {
  "worklet";
  const { x, y, w } = g;
  // coins falling (behind the cloud while they leave it)
  if (coin && edge && o.rainU >= 0) {
    for (let i = 0; i < COINS; i++) {
      const k = coinK(o.rainU, i);
      if (k <= 0 || k >= 1) continue;
      const cx = x + w * (0.36 + ((i * 37) % 30) / 100);
      const cy = g.cloudY - 30 + (g.landY - g.cloudY + 30) * k * k;
      const spin = reduced ? 1 : Math.cos(k * Math.PI * 3 + i);
      const sz = 46;
      const p = Skia.Paint();
      p.setAlphaf(reduced ? 1 - k : 1 - clamp01((k - 0.85) / 0.15));
      const img = Math.abs(spin) < 0.3 ? edge : coin;
      const sx = Math.abs(spin) < 0.3 ? 1 : Math.abs(spin);
      cv.drawImageRect(
        img,
        Skia.XYWHRect(0, 0, img.width(), img.height()),
        Skia.XYWHRect(cx - (sz * sx) / 2, cy - sz / 2, sz * sx, sz),
        p,
      );
    }
  }
  if (cloud) {
    // squeezed: squashes down and wider about its base, then wobbles back
    const sq = reduced ? 0 : 0.16 * o.squeeze + o.release * 0.16;
    const cw = w * 0.92 * (1 + sq * 0.6);
    const ch = cw * (560 / 900) * (1 - sq);
    const base = g.cloudY;
    cv.drawImageRect(
      cloud,
      Skia.XYWHRect(0, 0, cloud.width(), cloud.height()),
      Skia.XYWHRect(x + w / 2 - cw / 2, base - ch, cw, ch),
      Skia.Paint(),
    );
    // the week's coin sits on the cloud until it rains
    if (coin) {
      const show = o.rainU >= 0 ? 0 : 1;
      const back = o.refill;
      const a = Math.max(show, back);
      if (a > 0) {
        const p = Skia.Paint();
        p.setAlphaf(a);
        const s2 = 64 * (0.6 + 0.4 * a);
        cv.drawImageRect(
          coin,
          Skia.XYWHRect(0, 0, coin.width(), coin.height()),
          Skia.XYWHRect(
            x + w * 0.6 - s2 / 2,
            base - ch * 0.92 - s2 * 0.2,
            s2,
            s2,
          ),
          p,
        );
      }
    }
  }
  void y;
};

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

type Props = { autoPlay?: boolean };

export function CashbackCloudCard({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "CCC-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "CCC-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
    "CCC-Bold": require("./assets/fonts/Inter-Bold.ttf"),
  });
  const cloud = useImage(require("./assets/art/cloud.png"));
  const coin = useImage(require("./assets/art/coin.png"));
  const edge = useImage(require("./assets/art/coin-edge.png"));

  const cardX = 16;
  const cardY = insets.top + 120;
  const cardW = W - 32;
  const cardH = 430;
  const g: Geo = {
    x: cardX,
    y: cardY,
    w: cardW,
    h: cardH,
    cloudY: cardY + 262,
    landY: cardY + cardH - 120,
  };

  const st = useSharedValue({
    hold: 0,
    squeeze: 0,
    sqV: 0,
    rainAt: -100,
    releaseAt: -100,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    landed: 0,
  });
  const clock = useSharedValue(0);
  const out = useSharedValue<Out>({
    squeeze: 0,
    release: 0,
    rainU: -1,
    refill: 0,
    t: 0,
  });

  const tick = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0) Haptics.selectionAsync().catch(() => {});
    else
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(
        () => {},
      );
  };
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __cashCloud?: unknown };
    gl.__cashCloud = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => "",
    };
    return () => {
      delete gl.__cashCloud;
    };
  }, [debugT]);

  useFrameCallback((info) => {
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const s = st.get();
    const dbg = debugT.get();
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({
        squeeze: sc.squeeze,
        release: sc.release,
        rainU: sc.rainU,
        refill: sc.refill,
        t: dbg,
      });
      return;
    }
    if (
      auto &&
      s.hold === 0 &&
      now - s.lastTouch > 3.5 &&
      (s.scripted === 1 || now - s.rainAt > 9)
    ) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      out.set({
        squeeze: sc.squeeze,
        release: sc.release,
        rainU: sc.rainU,
        refill: sc.refill,
        t: now,
      });
      return;
    }
    s.scripted = 0;
    const target = s.hold ? 1 : 0;
    s.sqV += (300 * (target - s.squeeze) - 12 * s.sqV) * dt;
    s.squeeze += s.sqV * dt;
    if (s.hold && s.squeeze > 0.8 && now - s.rainAt > 9) s.rainAt = now;
    const rainU = now - s.rainAt < 9 ? now - s.rainAt : -1;
    let landed = 0;
    for (let i = 0; i < COINS; i++)
      if (rainU >= 0 && coinK(rainU, i) >= 1) landed++;
    if (landed !== s.landed) {
      s.landed = landed;
      if (landed > 0) scheduleOnRN(tick, landed === COINS ? 1 : 0);
    }
    const refill = rainU >= 7 ? smoother((rainU - 7) / 1.0) : 0;
    out.set({
      squeeze: Math.max(0, s.squeeze),
      release: 0,
      rainU: rainU >= 8 ? -1 : rainU,
      refill,
      t: now,
    });
  });

  const hold = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(80)
    .onBegin(() => {
      const s = st.get();
      s.hold = 1;
      s.scripted = 0;
      s.lastTouch = clock.get();
    })
    .onFinalize(() => {
      const s = st.get();
      s.hold = 0;
      s.lastTouch = clock.get();
    });

  const picture = useDerivedValue(() =>
    createPicture((cv) =>
      drawCard(cv, g, out.get(), cloud, coin, edge, reduced),
    ),
  );
  const landedFrac = useDerivedValue(() => {
    const o = out.get();
    if (o.rainU < 0) return 0;
    let n = 0;
    for (let i = 0; i < COINS; i++) n += coinK(o.rainU, i) >= 1 ? 1 : 0;
    return (n / COINS) * (1 - o.refill);
  });
  const cashText = useAnimatedProps(() => {
    const v = `$${(CASH * (1 - landedFrac.get())).toFixed(2)}`;
    return { text: v, defaultValue: v } as never;
  });
  const mainText = useAnimatedProps(() => {
    const v = `$${(MAIN + CASH * landedFrac.get()).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    return { text: v, defaultValue: v } as never;
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.title}>Rewards</Text>
        <View style={styles.round}>
          <Ionicons name="notifications-outline" size={21} color="#1E2A4A" />
        </View>
      </View>
      <View
        style={[styles.balance, { top: insets.top + 68 }]}
        accessible
        accessibilityLabel="Main balance"
      >
        <Text style={styles.balLabel}>Main balance</Text>
        <AnimatedInput
          editable={false}
          style={styles.balNum}
          animatedProps={mainText}
        />
      </View>
      <View
        style={[
          styles.card,
          { top: cardY, left: cardX, width: cardW, height: cardH },
        ]}
      >
        <View style={styles.newChip}>
          <Text style={styles.newText}>New</Text>
        </View>
        <View style={styles.cardBottom}>
          <AnimatedInput
            editable={false}
            style={styles.cash}
            animatedProps={cashText}
            accessibilityLabel="Cashback this week"
          />
          <Text style={styles.cashLabel}>Cashback this week</Text>
        </View>
      </View>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <GestureDetector gesture={hold}>
        <View
          style={[
            styles.cloudHit,
            {
              top: cardY + 20,
              left: cardX + 20,
              width: cardW - 40,
              height: 230,
            },
          ]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Cashback cloud. Press and hold to collect your cashback"
        />
      </GestureDetector>
      <View style={[styles.hintRow, { top: cardY + cardH + 18 }]}>
        <Ionicons name="hand-left-outline" size={18} color="#5A6B8F" />
        <Text style={styles.hint}>Squeeze the cloud to collect</Text>
      </View>
      <View style={[styles.list, { top: cardY + cardH + 58 }]}>
        {[
          ["cafe-outline", "Corner Café", "+$0.12"],
          ["cart-outline", "Greenleaf Market", "+$0.24"],
        ].map(([icon, name, amt]) => (
          <View key={name} style={styles.row}>
            <View style={styles.rowIcon}>
              <Ionicons
                name={icon as "cafe-outline"}
                size={18}
                color="#3D7BFF"
              />
            </View>
            <Text style={styles.rowName}>{name}</Text>
            <Text style={styles.rowAmt}>{amt}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#EEF3FF" },
  header: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  title: {
    fontFamily: "CCC-Bold",
    fontSize: 30,
    color: "#1E2A4A",
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
  balance: {
    position: "absolute",
    left: 20,
    right: 20,
    flexDirection: "row",
    alignItems: "baseline",
    gap: 8,
  },
  balLabel: { fontFamily: "CCC-Regular", fontSize: 14, color: "#5A6B8F" },
  balNum: {
    fontFamily: "CCC-SemiBold",
    fontSize: 16,
    color: "#1E2A4A",
    padding: 0,
    fontVariant: ["tabular-nums"],
    minWidth: 120,
  },
  card: {
    position: "absolute",
    borderRadius: 30,
    backgroundColor: "#3D7BFF",
    overflow: "hidden",
  },
  newChip: {
    position: "absolute",
    top: 18,
    right: 18,
    height: 30,
    paddingHorizontal: 14,
    borderRadius: 15,
    backgroundColor: "#D7F5E2",
    justifyContent: "center",
  },
  newText: { fontFamily: "CCC-SemiBold", fontSize: 14, color: "#1F7A45" },
  cardBottom: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    height: 150,
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 40,
    borderTopRightRadius: 40,
    paddingHorizontal: 26,
    paddingTop: 26,
  },
  cash: {
    fontFamily: "CCC-Bold",
    fontSize: 44,
    color: "#1E2A4A",
    padding: 0,
    fontVariant: ["tabular-nums"],
    minWidth: 160,
  },
  cashLabel: {
    fontFamily: "CCC-Regular",
    fontSize: 16,
    color: "#5A6B8F",
    marginTop: 4,
  },
  cloudHit: { position: "absolute" },
  hintRow: {
    position: "absolute",
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 8,
  },
  hint: { fontFamily: "CCC-SemiBold", fontSize: 14, color: "#5A6B8F" },
  list: { position: "absolute", left: 16, right: 16, gap: 10 },
  row: {
    height: 60,
    borderRadius: 18,
    backgroundColor: "#FFFFFF",
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
  },
  rowIcon: {
    width: 36,
    height: 36,
    borderRadius: 12,
    backgroundColor: "#E6EEFF",
    alignItems: "center",
    justifyContent: "center",
  },
  rowName: {
    flex: 1,
    fontFamily: "CCC-SemiBold",
    fontSize: 15,
    color: "#1E2A4A",
  },
  rowAmt: { fontFamily: "CCC-SemiBold", fontSize: 15, color: "#1F7A45" },
});
