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
 Lantern Wish Send — the send screen of an invented greeting app, Lumen Wishes.

 Look (a Pinterest night of paper sky lanterns drifting up through a purple sky).

 Ours: your message becomes a lantern. The note card folds down into a flat paper packet, the packet
 opens into a lantern, its flame catches and the paper starts to glow, and it rises — swaying, shrinking
 into the distance — to join everyone else's lanterns already drifting up the sky. Flick the lantern up
 to let it go sooner. The count of wishes sent tonight ticks when yours joins them.

 autoPlay: LOOP = 9 s — the note rests, folds 1.0–1.8, opens and lights 1.8–2.6, rises 2.6–6.4, a new
 blank note fades back in 6.8–8.4 (frame 9 = frame 0; the sky lanterns travel exactly one lap per
 loop). Reduced motion: the note crossfades into a lit lantern that fades out upward; sky lanterns stay.
*/

let debugState = "";

const LOOP = 9;
const SKY = 22;

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
/** Phases of one send, u seconds after Send. */
const sendAt = (u: number) => {
  "worklet";
  return {
    fold: smoother(u / 0.8),
    light: smoother((u - 0.8) / 0.8),
    rise: u > 1.6 ? clamp01((u - 1.6) / 3.8) : 0,
  };
};
const scriptAt = (t: number) => {
  "worklet";
  const s = sendAt(t - 1.0);
  const back = t >= 6.8 ? smoother((t - 6.8) / 1.6) : 0;
  return { ...s, sending: t >= 1.0 && t < 6.8 ? 1 : 0, back };
};

type Geo = { W: number; H: number; cx: number; cardY: number };
type Out = {
  fold: number;
  light: number;
  rise: number;
  sending: number;
  t: number;
  flick: number;
};

const drawSky = (
  cv: SkCanvas,
  g: Geo,
  o: Out,
  sky: SkImage | null,
  lit: SkImage | null,
  packet: SkImage | null,
  reduced: boolean,
) => {
  "worklet";
  if (sky)
    cv.drawImageRect(
      sky,
      Skia.XYWHRect(0, 0, sky.width(), sky.height()),
      Skia.XYWHRect(0, 0, g.W, g.H),
      Skia.Paint(),
    );
  // everyone else's lanterns: one lap per loop, far ones small and dim
  if (lit) {
    for (let i = 0; i < SKY; i++) {
      const depth = 0.25 + (((i * 37) % 100) / 100) * 0.75;
      const range = g.H * 1.2;
      const y0 = ((i * 211) % 997) / 997;
      const y = g.H * 1.05 - ((y0 + (reduced ? 0 : o.t / LOOP)) % 1) * range;
      const hx = Math.sin(i * 12.9898) * 43758.5453;
      const x =
        (hx - Math.floor(hx)) * g.W +
        (reduced ? 0 : Math.sin((o.t * 2 * Math.PI) / LOOP + i) * 10 * depth);
      const sz = 20 + 44 * depth * depth;
      const glow = Skia.Paint();
      glow.setColor(Skia.Color("#FFB45A"));
      glow.setAlphaf(0.18 * depth);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(0, sz * 0.5, true));
      cv.drawCircle(x, y, sz * 0.55, glow);
      const p = Skia.Paint();
      p.setAlphaf(0.45 + 0.55 * depth);
      cv.drawImageRect(
        lit,
        Skia.XYWHRect(0, 0, lit.width(), lit.height()),
        Skia.XYWHRect(x - sz * 0.375, y - sz * 0.5, sz * 0.75, sz),
        p,
      );
    }
  }
  // yours
  if (o.sending > 0 && lit && packet) {
    const size = lerp(170, 26, smoother(o.rise));
    const y = lerp(g.cardY, g.H * 0.12, o.rise) - o.flick * 40;
    const x = g.cx + (reduced ? 0 : Math.sin(o.rise * 7) * 22 * o.rise);
    const w = size * 0.75;
    const h = size;
    const stretch =
      o.light > 0 && o.light < 1 ? 1 + 0.08 * Math.sin(o.light * Math.PI) : 1;
    if (o.light > 0) {
      const glow = Skia.Paint();
      glow.setColor(Skia.Color("#FFB45A"));
      glow.setAlphaf(0.5 * o.light);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(0, size * 0.45, true));
      cv.drawCircle(x, y, size * 0.6, glow);
    }
    const pk = Skia.Paint();
    pk.setAlphaf(1 - o.light);
    cv.drawImageRect(
      packet,
      Skia.XYWHRect(0, 0, packet.width(), packet.height()),
      Skia.XYWHRect(
        x - w / 2,
        y - (h / 2) * (0.4 + 0.6 * o.fold),
        w,
        h * (0.4 + 0.6 * o.fold),
      ),
      pk,
    );
    const lp = Skia.Paint();
    lp.setAlphaf(o.light);
    cv.drawImageRect(
      lit,
      Skia.XYWHRect(0, 0, lit.width(), lit.height()),
      Skia.XYWHRect(x - w / 2, y - (h / 2) * stretch, w, h * stretch),
      lp,
    );
  }
};

type Props = { autoPlay?: boolean };

export function LanternWishSend({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "LWS-Serif": require("./assets/fonts/InstrumentSerif-Italic.ttf"),
    "LWS-Regular": require("./assets/fonts/Inter-Regular.ttf"),
    "LWS-SemiBold": require("./assets/fonts/Inter-SemiBold.ttf"),
  });
  const sky = useImage(require("./assets/art/sky.webp"));
  const lit = useImage(require("./assets/art/lantern.png"));
  const packet = useImage(require("./assets/art/lantern-folded.png"));

  const cardY = Math.round(H * 0.6);
  const g: Geo = { W, H, cx: W / 2, cardY };

  const st = useSharedValue({
    at: -100,
    flick: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
  });
  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const out = useSharedValue<Out>({
    fold: 0,
    light: 0,
    rise: 0,
    sending: 0,
    t: 0,
    flick: 0,
  });
  const backV = useSharedValue(1);
  const [count, setCount] = useState(1248);
  const [text, setText] = useState(
    "Happy 80th, Grandma Lin. Thank you for every bedtime story.",
  );

  useEffect(() => {
    if (!__DEV__) return;
    const gl = globalThis as { __lantern?: unknown };
    gl.__lantern = {
      at: (x: number) => debugT.set(x),
      play: () => debugT.set(-1),
      state: () => debugState,
    };
    return () => {
      delete gl.__lantern;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };
  const buzz = (k: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (k === 0)
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
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
    s.flick *= Math.exp(-3 * dt);
    if (dbg >= 0) {
      const sc = scriptAt(dbg % LOOP);
      out.set({
        fold: sc.fold,
        light: sc.light,
        rise: sc.rise,
        sending: sc.sending,
        t: dbg,
        flick: 0,
      });
      backV.set(sc.sending ? 0 : dbg % LOOP < 1.0 ? 1 : sc.back);
      return;
    }
    const busy = now - s.at < 6.2;
    if (auto && !busy && now - s.lastTouch > 3.5) {
      if (s.scripted === 0) {
        s.scripted = 1;
        s.loopT = 0;
      }
      s.loopT = (s.loopT + wall) % LOOP;
      const sc = scriptAt(s.loopT);
      out.set({
        fold: sc.fold,
        light: sc.light,
        rise: sc.rise,
        sending: sc.sending,
        t: now,
        flick: 0,
      });
      backV.set(sc.sending ? 0 : s.loopT < 1.0 ? 1 : sc.back);
      return;
    }
    s.scripted = 0;
    const u = now - s.at;
    const p = sendAt(u * (u > 1.6 ? 1 + s.flick * 0.6 : 1));
    const sending = u >= 0 && u < 6.0 ? 1 : 0;
    out.set({
      fold: p.fold,
      light: p.light,
      rise: p.rise,
      sending,
      t: now,
      flick: s.flick,
    });
    backV.set(sending ? 0 : smoother((u - 6.0) / 1.0));
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - wall) * 4)) {
      scheduleOnRN(
        mirror,
        `u=${u.toFixed(2)} sending=${sending} scripted=${s.scripted}`,
      );
    }
  });

  useAnimatedReaction(
    () => out.get().rise > 0.9 && out.get().sending === 1,
    (v, prev) => {
      if (v === prev) return;
      scheduleOnRN(setCount, v ? 1249 : 1248);
      if (v) scheduleOnRN(buzz, 1);
    },
  );

  const send = () => {
    "worklet";
    const s = st.get();
    const now = clock.get();
    if (now - s.at < 6.2) return;
    s.at = now;
    s.scripted = 0;
    s.lastTouch = now;
  };
  const onSend = () => {
    buzz(0);
    scheduleOnUI(send);
  };
  const flick = Gesture.Pan().onEnd((e) => {
    const s = st.get();
    if (e.velocityY < -400) {
      s.flick = 1;
      s.lastTouch = clock.get();
    }
  });

  const picture = useDerivedValue(() =>
    createPicture((cv) => drawSky(cv, g, out.get(), sky, lit, packet, reduced)),
  );
  const card = useAnimatedStyle(() => {
    const o = out.get();
    const b = backV.get();
    // the card folds away into the packet, then a fresh one fades back in
    const fold = o.sending ? o.fold : 0;
    const v = o.sending ? 1 - fold : b;
    return {
      opacity: v,
      transform: [
        { translateY: o.sending ? fold * 40 : (1 - b) * 20 },
        { scaleY: o.sending ? 1 - 0.7 * fold : 1 },
        { scaleX: o.sending ? 1 - 0.45 * fold : 1 },
      ],
    };
  });

  if (!fontsOk) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.head, { top: insets.top + 16 }]}>
        <Text style={styles.title}>Send a wish</Text>
        <Text style={styles.count}>
          {count.toLocaleString("en-US")} lanterns lit tonight
        </Text>
      </View>
      <GestureDetector gesture={flick}>
        <View
          style={[
            styles.flickHit,
            { top: insets.top + 100, bottom: H - cardY + 100 },
          ]}
          accessible={false}
        />
      </GestureDetector>
      <Animated.View style={[styles.card, { top: cardY - 90 }, card]}>
        <Text style={styles.to}>To Grandma Lin</Text>
        <TextInput
          value={text}
          onChangeText={setText}
          multiline
          maxLength={120}
          style={styles.note}
          accessibilityLabel="Your wish"
          placeholder="Write a wish…"
          placeholderTextColor="#B69A8A"
        />
      </Animated.View>
      <View style={[styles.bottom, { bottom: insets.bottom + 18 }]}>
        <Pressable
          onPress={onSend}
          accessibilityRole="button"
          accessibilityLabel="Light and send the lantern"
          style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
        >
          <Ionicons name="flame" size={20} color="#3A1F4F" />
          <Text style={styles.ctaText}>Light & send</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#2A1A4A" },
  head: { position: "absolute", left: 24, right: 24, alignItems: "center" },
  title: { fontFamily: "LWS-Serif", fontSize: 40, color: "#FFF1DE" },
  count: {
    fontFamily: "LWS-Regular",
    fontSize: 14,
    color: "#D9C6F2",
    marginTop: 4,
  },
  flickHit: { position: "absolute", left: 0, right: 0 },
  card: {
    position: "absolute",
    left: 40,
    right: 40,
    minHeight: 180,
    borderRadius: 20,
    backgroundColor: "#FFF6E9",
    padding: 20,
    shadowColor: "#FFB45A",
    shadowOpacity: 0.35,
    shadowRadius: 30,
    shadowOffset: { width: 0, height: 0 },
  },
  to: {
    fontFamily: "LWS-SemiBold",
    fontSize: 13,
    color: "#B0705A",
    letterSpacing: 0.4,
  },
  note: {
    fontFamily: "LWS-Serif",
    fontSize: 26,
    lineHeight: 32,
    color: "#3A1F4F",
    marginTop: 8,
    padding: 0,
  },
  bottom: { position: "absolute", left: 20, right: 20 },
  cta: {
    height: 58,
    borderRadius: 29,
    backgroundColor: "#FFC46B",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  ctaText: { fontFamily: "LWS-SemiBold", fontSize: 17, color: "#3A1F4F" },
  pressed: { transform: [{ scale: 0.96 }] },
});
