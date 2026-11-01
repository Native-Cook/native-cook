import { Ionicons, MaterialCommunityIcons } from "@expo/vector-icons";
import {
  Canvas,
  Circle,
  Group,
  LinearGradient,
  RadialGradient,
  Rect,
  Shader,
  vec,
} from "@shopify/react-native-skia";
import { useFonts } from "expo-font";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import {
  PixelRatio,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  interpolateColor,
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

import { worldFx } from "./twp-scene";
import { HUD, WORLDS } from "./twp-worlds";
import { CUES, useTwpAudio } from "./use-twp-audio";

/*
 Tiny World Picker — the worlds screen of an invented toy-box game.

 Look (a still of a game's world list): a dark teal sky with dust stars, a HUD of level, energy and
 two currencies, a column of square buttons on the right, a theme bubble over one small world, the
 world's name with a status pill under it, then the actions.

 Motion (ours): the world is a real 3D object, ray-marched in one shader, and the picker's whole idea
 is that dragging it sideways spins it like clay on a potter's wheel while it reshapes into the next
 world: the cloud planet's puffs pull into a banked race loop, the planet flattens into a burger
 stack, the burger opens a hole and stands up as an arena ring. The finger scrubs the morph; release
 commits by distance or velocity and the spin carries on with the release speed. A tap bounces the
 world like jelly. Names, pills, the button label and the pager follow the same morph amount.

 autoPlay: LOOP = 9 s, four worlds of 2.25 s: rest 1.35 s (slow turn), morph 0.75 s (the world spins
 2.58 rad while it reshapes and flattens), 0.15 s into a jelly wobble that rings into the next rest.
 Frame 9 s equals frame 0 (the turn adds up to 4π). Reduced motion: no idle turn, no squash, no
 autoplay; a pick changes the world in a quick dissolve.
*/

let debugState = "";

const N = WORLDS.length;
const LOOP = 9;
const SEG = LOOP / N;
const HOLD = 1.35;
const MORPH = 0.75;
const IDLE_SPIN = 0.25;
const SPIN_PER_MORPH = (4 * Math.PI - IDLE_SPIN * LOOP) / N;
const DRAG_SPIN = 0.0105; // radians per point of drag
const SETTLE_SPIN = 1.9; // radians per unit of morph while settling
const RESUME_AFTER = 3.5; // seconds without touch before autoplay resumes

const clamp01 = (v: number) => {
  "worklet";
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
const smoother = (k: number) => {
  "worklet";
  const x = clamp01(k);
  return x * x * x * (x * (x * 6 - 15) + 10);
};
const smoothstep = (a: number, b: number, v: number) => {
  "worklet";
  const x = clamp01((v - a) / (b - a));
  return x * x * (3 - 2 * x);
};
const wobble = (tau: number) => {
  "worklet";
  return tau < 0 ? 0 : -0.07 * Math.exp(-7 * tau) * Math.sin(17 * tau);
};
/** The autoplay script at loop time t: which worlds, how far the morph, the turn and the squash. */
const scriptAt = (t: number) => {
  "worklet";
  const K = Math.floor(t / SEG);
  const u = t - K * SEG;
  const mm = clamp01((u - HOLD) / MORPH);
  const m = smoother(mm);
  const done = HOLD + MORPH;
  const tau = u >= done ? u - done : u + SEG - done;
  const squash =
    (mm > 0 && mm < 1 ? 0.12 * Math.sin(Math.PI * mm) : 0) + wobble(tau);
  return {
    cur: K % N,
    nxt: (K + 1) % N,
    m,
    yaw: -(IDLE_SPIN * t + SPIN_PER_MORPH * (K + m)),
    sq: squash,
  };
};
/** How visible world i's labels are, given the current pair and the morph amount. */
const visOf = (i: number, cur: number, nxt: number, m: number) => {
  "worklet";
  const s = smoothstep(0.35, 0.65, m);
  let v = 0;
  if (i === cur) v += nxt === cur ? 1 : 1 - s;
  if (i === nxt && nxt !== cur) v += s;
  return v;
};

/** Labels swap in sequence (old out, then new in) so two names never overlap. */
const labelVisOf = (i: number, cur: number, nxt: number, m: number) => {
  "worklet";
  if (nxt === cur) return i === cur ? 1 : 0;
  if (i === cur) return 1 - smoothstep(0.14, 0.46, m);
  if (i === nxt) return smoothstep(0.48, 0.82, m);
  return 0;
};

type Phys = {
  cur: number;
  nxt: number;
  m: number;
  mV: number;
  target: number;
  yaw: number;
  yawV: number;
  sq: number;
  sqV: number;
  drag: number;
  edge: number;
  P0: number;
  yaw0: number;
  lastTouch: number;
  scripted: number;
  loopT: number;
  yawOff: number;
  armed: number;
  glow: number;
  carT: number;
};
type Out = {
  cur: number;
  nxt: number;
  m: number;
  yaw: number;
  sq: number;
  glow: number;
  t: number;
  tm: number;
};

/** stars: x, y in 0..1, radius, twinkle phase and rate (whole cycles per loop so the loop is seamless) */
const STARS = (() => {
  const out: { x: number; y: number; r: number; ph: number; k: number }[] = [];
  let s = 7;
  const rnd = () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };
  for (let i = 0; i < 70; i++) {
    out.push({
      x: rnd(),
      y: rnd(),
      r: 0.6 + rnd() * 1.1,
      ph: rnd() * 6.283,
      k: 1 + Math.floor(rnd() * 3),
    });
  }
  return out;
})();

type Props = { autoPlay?: boolean };

export function TinyWorldPicker({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const [fontsOk] = useFonts({
    "TWP-Title": require("./assets/fonts/TitanOne-Regular.ttf"),
    "TWP-Black": require("./assets/fonts/Nunito-Black.ttf"),
    "TWP-Bold": require("./assets/fonts/Nunito-ExtraBold.ttf"),
  });

  const cy = Math.round(H * 0.47);
  const S = Math.round(W * 0.44);
  const px = 1 / (S * PixelRatio.get());
  const dragSpan = W * 0.62;

  const play = useTwpAudio(!reduced);
  /** 0 morph, 1 commit, 2 tap */
  const cue = (n: number) => play(CUES[n]);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === "web") return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1)
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  const clock = useSharedValue(0);
  const debugT = useSharedValue(-1);
  const noAuto = useSharedValue(0);
  const fps = useSharedValue({ n: 0, rate: 0 });
  const ph = useSharedValue<Phys>({
    cur: 0,
    nxt: 0,
    m: 0,
    mV: 0,
    target: -1,
    yaw: 0,
    yawV: 0,
    sq: 0,
    sqV: 0,
    drag: 0,
    edge: 0,
    P0: 0,
    yaw0: 0,
    lastTouch: -100,
    scripted: 0,
    loopT: 0,
    yawOff: 0,
    armed: 0,
    glow: 0,
    carT: 0,
  });
  const [shown, setShown] = useState(0);
  const out = useSharedValue<Out>({
    cur: 0,
    nxt: 0,
    m: 0,
    yaw: 0,
    sq: 0,
    glow: 0,
    t: 0,
    tm: 0,
  });

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __tinyWorld?: unknown };
    g.__tinyWorld = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      manual: (on: boolean) => noAuto.set(on ? 1 : 0),
      state: () => debugState,
    };
    return () => {
      delete g.__tinyWorld;
    };
  }, [debugT, noAuto]);
  const mirror = (s: string) => {
    debugState = s;
  };

  useFrameCallback((info) => {
    // the loop and the ambient clock take wall time; physics takes a clamped step
    const wall = Math.max(0, (info.timeSincePreviousFrame ?? 16) / 1000);
    const dt = Math.min(wall, 0.05);
    const now = clock.get() + wall;
    clock.set(now);
    const p = ph.get();
    const dbg = debugT.get();
    p.carT = (p.carT + wall) % LOOP;

    if (dbg >= 0) {
      const s = scriptAt(dbg % LOOP);
      out.set({
        cur: s.cur,
        nxt: s.nxt,
        m: s.m,
        yaw: s.yaw,
        sq: s.sq,
        glow: 0,
        t: dbg % LOOP,
        tm: dbg % LOOP,
      });
      return;
    }

    const canScript =
      auto &&
      noAuto.get() === 0 &&
      p.drag === 0 &&
      now - p.lastTouch > RESUME_AFTER &&
      (p.scripted === 1 || (p.target < 0 && p.m === 0));
    if (canScript) {
      if (p.scripted === 0) {
        // resume from the current world's rest, carrying the turn so nothing jumps
        p.scripted = 1;
        p.loopT = p.cur * SEG + 0.6;
        p.yawOff = p.yaw - scriptAt(p.loopT).yaw;
      }
      p.loopT = (p.loopT + wall) % LOOP;
      const s = scriptAt(p.loopT);
      p.cur = s.cur;
      p.nxt = s.m > 0 ? s.nxt : s.cur;
      p.m = s.m >= 1 ? 0 : s.m;
      if (s.m >= 1) p.cur = s.nxt;
      p.yaw = s.yaw + p.yawOff;
      p.sq = s.sq;
      p.sqV = 0;
      p.mV = 0;
      p.yawV = -IDLE_SPIN;
    } else {
      p.scripted = 0;
      if (p.drag === 0) {
        if (p.m > 0 || p.target >= 0) {
          const tgt = p.target >= 0 ? p.target : p.m > 0.5 ? 1 : 0;
          const k = reduced ? 700 : 160;
          const c = reduced ? 53 : 2 * 0.82 * Math.sqrt(k);
          p.mV += (k * (tgt - p.m) - c * p.mV) * dt;
          p.m += p.mV * dt;
          if (p.m < 0) {
            p.m = 0;
            p.mV = 0;
          }
          if (p.m > 1.03) p.m = 1.03;
          const dir = p.nxt > p.cur ? 1 : -1;
          if (!reduced) p.yaw -= dir * p.mV * dt * SETTLE_SPIN;
          if (tgt === 1 && Math.abs(1 - p.m) < 0.004 && Math.abs(p.mV) < 0.08) {
            p.cur = p.nxt;
            p.m = 0;
            p.mV = 0;
            p.target = -1;
            if (!reduced) p.sqV -= 2.4;
            scheduleOnRN(buzz, 2);
            scheduleOnRN(cue, 1);
          } else if (tgt === 0 && p.m < 0.004 && Math.abs(p.mV) < 0.08) {
            p.m = 0;
            p.mV = 0;
            p.target = -1;
            p.nxt = p.cur;
          }
        }
        const idle = reduced ? 0 : -IDLE_SPIN;
        p.yawV += (idle - p.yawV) * (1 - Math.exp(-1.6 * dt));
        p.yaw += p.yawV * dt;
      }
      // squash: flatten while reshaping, resist at the ends, ring after a commit
      let sqT = 0;
      if (!reduced) {
        sqT = 0.12 * Math.sin(Math.PI * clamp01(p.m));
        if (p.drag === 1 && p.edge !== 0)
          sqT = 0.07 * Math.tanh(Math.abs(p.edge) * 3);
      }
      p.sqV += (260 * (sqT - p.sq) - 9 * p.sqV) * dt;
      p.sq += p.sqV * dt;
      if (p.sq > 0.3) p.sq = 0.3;
      if (p.sq < -0.25) p.sq = -0.25;
    }
    p.glow += (p.drag - p.glow) * (1 - Math.exp(-8 * dt));
    out.set({
      cur: p.cur,
      nxt: p.nxt,
      m: p.m,
      yaw: p.yaw,
      sq: p.sq,
      glow: p.glow,
      t: now,
      tm: p.carT,
    });
    if (__DEV__) {
      const f = fps.get();
      f.n += 1;
      if (Math.floor(now * 2) !== Math.floor((now - wall) * 2)) {
        f.rate = f.n * 2;
        f.n = 0;
      }
      if (Math.floor(now * 10) !== Math.floor((now - wall) * 10)) {
        scheduleOnRN(
          mirror,
          `cur=${p.cur} nxt=${p.nxt} m=${p.m.toFixed(3)} yaw=${p.yaw.toFixed(2)} sq=${p.sq.toFixed(3)} scripted=${p.scripted} drag=${p.drag} fps=${f.rate}`,
        );
      }
    }
  });

  // the world VoiceOver announces follows the committed world
  useAnimatedReaction(
    () => out.get().cur,
    (c, prev) => {
      if (c !== prev) scheduleOnRN(setShown, c);
    },
  );

  // ---- touch -------------------------------------------------------------------------------
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-24, 24])
    .onBegin(() => {
      const p = ph.get();
      p.lastTouch = clock.get();
    })
    .onStart(() => {
      const p = ph.get();
      const dir = p.m > 0 ? (p.nxt > p.cur ? 1 : -1) : 0;
      p.drag = 1;
      p.scripted = 0;
      p.target = -1;
      p.mV = 0;
      p.P0 = dir * p.m;
      p.yaw0 = p.yaw;
      p.armed = Math.abs(p.P0) > 0.42 ? 1 : 0;
    })
    .onUpdate((e) => {
      const p = ph.get();
      let P = p.P0 - e.translationX / dragSpan;
      while (P >= 1 && p.cur < N - 1) {
        p.cur += 1;
        p.P0 -= 1;
        P -= 1;
        scheduleOnRN(buzz, 2);
      }
      while (P <= -1 && p.cur > 0) {
        p.cur -= 1;
        p.P0 += 1;
        P += 1;
        scheduleOnRN(buzz, 2);
      }
      p.edge = 0;
      if (P > 0 && p.cur < N - 1) {
        p.nxt = p.cur + 1;
        p.m = Math.min(P, 1);
      } else if (P < 0 && p.cur > 0) {
        p.nxt = p.cur - 1;
        p.m = Math.min(-P, 1);
      } else {
        p.nxt = p.cur;
        p.m = 0;
        p.edge = P;
      }
      p.yaw = p.yaw0 + (reduced ? 0 : e.translationX * DRAG_SPIN);
      const armed = p.m > 0.42 ? 1 : 0;
      if (armed !== p.armed) {
        p.armed = armed;
        scheduleOnRN(buzz, 0);
      }
      p.lastTouch = clock.get();
    })
    .onFinalize((e) => {
      const p = ph.get();
      if (p.drag === 0) return;
      p.drag = 0;
      p.edge = 0;
      const dir = p.nxt > p.cur ? 1 : p.nxt < p.cur ? -1 : 0;
      if (dir !== 0) {
        const along = (-e.velocityX / dragSpan) * dir;
        const commit =
          (p.m > 0.42 && along > -0.9) || (along > 0.9 && p.m > 0.04);
        p.target = commit ? 1 : 0;
        p.mV = along;
        if (commit && p.m < 0.85) scheduleOnRN(cue, 0);
      }
      const v = reduced ? 0 : e.velocityX * DRAG_SPIN;
      p.yawV = v > 12 ? 12 : v < -12 ? -12 : v;
      p.lastTouch = clock.get();
    });

  const tap = Gesture.Tap()
    .maxDuration(260)
    .onEnd(() => {
      const p = ph.get();
      p.lastTouch = clock.get();
      p.scripted = 0;
      if (p.m > 0 && p.target < 0) p.target = p.m > 0.5 ? 1 : 0;
      if (!reduced) {
        p.sqV -= 2.6;
        p.yawV -= 3;
      }
      scheduleOnRN(buzz, 1);
      scheduleOnRN(cue, 2);
    });
  const gesture = Gesture.Race(pan, tap);

  const goTo = (i: number) => {
    "worklet";
    const p = ph.get();
    p.lastTouch = clock.get();
    p.scripted = 0;
    if (i === p.cur || p.drag === 1) return;
    if (p.m > 0) p.cur = p.m > 0.5 ? p.nxt : p.cur;
    p.nxt = i;
    p.m = 0;
    p.mV = 0;
    p.target = 1;
    scheduleOnRN(cue, 0);
  };
  const step = (d: number) => {
    "worklet";
    const p = ph.get();
    const i = p.cur + d;
    if (i >= 0 && i < N) goTo(i);
  };
  const onDot = (i: number) => scheduleOnUI(goTo, i);
  const onA11y = (name: string) =>
    scheduleOnUI(step, name === "increment" ? 1 : -1);

  // ---- drawing -----------------------------------------------------------------------------
  const uniforms = useDerivedValue(() => {
    const o = out.get();
    return {
      c: vec(W / 2, cy),
      S,
      yaw: o.yaw,
      wa: o.cur,
      wb: o.nxt,
      m: o.m > 1.03 ? 1.03 : o.m,
      sq: o.sq,
      glow: o.glow,
      px,
      tm: o.tm,
    };
  });
  const haloColor = useDerivedValue(() => {
    const o = out.get();
    const a = WORLDS[o.cur].halo;
    const b = WORLDS[o.nxt].halo;
    return interpolateColor(smoothstep(0.2, 0.8, o.m), [0, 1], [a, b]);
  });
  const haloColors = useDerivedValue(() => [
    haloColor.get(),
    "rgba(18,48,58,0)",
  ]);
  const twinkle = useDerivedValue(() => out.get().t);

  if (!fontsOk) return <View style={styles.screen} />;

  const bubbleY = cy - 178;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={W} height={H}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, H)}
            colors={["#163C49", "#0E2A34", "#081A21"]}
          />
        </Rect>
        <Group opacity={0.32}>
          <Rect x={0} y={cy - 260} width={W} height={520}>
            <RadialGradient
              c={vec(W / 2, cy)}
              r={W * 0.62}
              colors={haloColors}
            />
          </Rect>
        </Group>
        <Stars W={W} H={H} t={twinkle} />
        {worldFx && (
          <Rect x={0} y={cy - 250} width={W} height={500}>
            <Shader source={worldFx} uniforms={uniforms} />
          </Rect>
        )}
      </Canvas>

      {/* the world takes drags and taps */}
      <GestureDetector gesture={gesture}>
        <View
          style={[styles.touch, { top: cy - 170, height: 340 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="World"
          accessibilityHint="Drag sideways or swipe up and down with VoiceOver to pick another world"
          accessibilityValue={{
            text: `${WORLDS[shown].name}, ${WORLDS[shown].status.toLowerCase()}, world ${shown + 1} of ${N}`,
          }}
          accessibilityActions={[{ name: "increment" }, { name: "decrement" }]}
          onAccessibilityAction={(e) => onA11y(e.nativeEvent.actionName)}
        />
      </GestureDetector>

      {/* HUD */}
      <View
        style={[styles.hud, { top: insets.top + 6 }]}
        pointerEvents="box-none"
      >
        <View style={styles.row}>
          <View
            style={styles.levelRing}
            accessible
            accessibilityLabel={`Level ${HUD.level}`}
          >
            <Text style={styles.levelNum}>{HUD.level}</Text>
          </View>
          <View
            style={styles.energy}
            accessible
            accessibilityLabel={`Energy ${HUD.energy}`}
          >
            <Ionicons name="flash" size={14} color="#FFD84A" />
            <View style={styles.energyTrack}>
              <View
                style={[
                  styles.energyFill,
                  { width: `${HUD.energyFill * 100}%` },
                ]}
              />
            </View>
            <Text style={styles.hudNum}>{HUD.energy}</Text>
          </View>
        </View>
        <View style={styles.hudRight}>
          <View
            style={styles.pill}
            accessible
            accessibilityLabel={`${HUD.coins} coins`}
          >
            <View style={styles.coin}>
              <View style={styles.coinInner} />
            </View>
            <Text style={styles.hudNum}>{HUD.coins}</Text>
          </View>
          <View
            style={styles.pill}
            accessible
            accessibilityLabel={`${HUD.stars} of ${HUD.starsMax} stars`}
          >
            <Ionicons name="star" size={13} color="#FFB84A" />
            <Text style={styles.hudNum}>
              {HUD.stars}
              <Text style={styles.hudDim}> / {HUD.starsMax}</Text>
            </Text>
          </View>
        </View>
      </View>

      <View style={[styles.sideCol, { top: cy - 176 }]}>
        <SideButton icon="camera-outline" label="Snapshot" />
        <SideButton icon="gift-outline" label="Rewards" />
        <SideButton icon="map-outline" label="World map" />
      </View>

      {/* theme bubble */}
      <ThemeBubble out={out} top={bubbleY} left={W / 2 - 22} />

      {/* name + status, one layer per world, crossfading with the morph */}
      <View style={[styles.labels, { top: cy + 140 }]} pointerEvents="none">
        {WORLDS.map((wd, i) => (
          <WorldLabel key={wd.name} i={i} out={out} hidden={i !== shown} />
        ))}
      </View>

      <View style={[styles.statsRow, { top: cy + 226 }]} pointerEvents="none">
        {WORLDS.map((wd, i) => (
          <WorldStats key={wd.name} i={i} out={out} hidden={i !== shown} />
        ))}
      </View>

      <View style={[styles.dots, { top: cy + 252 }]}>
        {WORLDS.map((wd, i) => (
          <Dot key={wd.name} i={i} out={out} onPress={() => onDot(i)} />
        ))}
      </View>

      <View style={[styles.bottom, { bottom: insets.bottom + 12 }]}>
        <SideButton icon="information-circle-outline" label="World info" big />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${WORLDS[shown].cta}, ${WORLDS[shown].name}`}
          style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
        >
          {WORLDS.map((wd, i) => (
            <CtaLabel key={wd.name} i={i} out={out} hidden={i !== shown} />
          ))}
        </Pressable>
        <SideButton
          icon="grid-outline"
          label="Collection, 2 new"
          big
          badge={2}
        />
      </View>
    </View>
  );
}

function Stars({ W, H, t }: { W: number; H: number; t: SharedValue<number> }) {
  return (
    <Group>
      {STARS.map((s, i) => (
        <Star key={i} x={s.x * W} y={s.y * H} r={s.r} ph={s.ph} k={s.k} t={t} />
      ))}
    </Group>
  );
}

function Star({
  x,
  y,
  r,
  ph,
  k,
  t,
}: {
  x: number;
  y: number;
  r: number;
  ph: number;
  k: number;
  t: SharedValue<number>;
}) {
  const op = useDerivedValue(
    () =>
      0.18 +
      0.42 * (0.5 + 0.5 * Math.sin((t.get() * 2 * Math.PI * k) / LOOP + ph)),
  );
  return <Circle cx={x} cy={y} r={r} color="#B6E4EC" opacity={op} />;
}

function SideButton({
  icon,
  label,
  big,
  badge,
}: {
  icon: React.ComponentProps<typeof Ionicons>["name"];
  label: string;
  big?: boolean;
  badge?: number;
}) {
  const size = big ? 52 : 44;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [
        styles.sideBtn,
        { width: size, height: size, borderRadius: big ? 18 : 15 },
        pressed && styles.pressed,
      ]}
    >
      <Ionicons name={icon} size={big ? 24 : 21} color="#CFEFF4" />
      {badge ? (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{badge}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}

const GLYPH: Record<
  string,
  React.ComponentProps<typeof MaterialCommunityIcons>["name"]
> = {
  cloud: "weather-cloudy",
  flag: "flag-checkered",
  burger: "hamburger",
  arch: "pillar",
};

function ThemeBubble({
  out,
  top,
  left,
}: {
  out: SharedValue<Out>;
  top: number;
  left: number;
}) {
  const bob = useAnimatedStyle(() => {
    const o = out.get();
    const s = Math.sin(Math.PI * clamp01(o.m));
    return {
      transform: [
        { translateY: Math.sin((o.t * 2 * Math.PI * 2) / LOOP) * 2.5 - s * 6 },
        { scale: 1 - 0.12 * s },
      ],
    };
  });
  return (
    <Animated.View
      style={[styles.bubble, { top, left }, bob]}
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {WORLDS.map((wd, i) => (
        <Glyph key={wd.name} i={i} out={out} />
      ))}
    </Animated.View>
  );
}

function Glyph({ i, out }: { i: number; out: SharedValue<Out> }) {
  const st = useAnimatedStyle(() => {
    const o = out.get();
    return { opacity: visOf(i, o.cur, o.nxt, o.m) };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.center, st]}>
      <MaterialCommunityIcons
        name={GLYPH[WORLDS[i].glyph]}
        size={24}
        color="#FFFFFF"
      />
    </Animated.View>
  );
}

function WorldLabel({
  i,
  out,
  hidden,
}: {
  i: number;
  out: SharedValue<Out>;
  hidden: boolean;
}) {
  const wd = WORLDS[i];
  const st = useAnimatedStyle(() => {
    const o = out.get();
    const v = labelVisOf(i, o.cur, o.nxt, o.m);
    const incoming = i === o.nxt && o.nxt !== o.cur;
    return {
      opacity: v,
      transform: [
        { translateY: (1 - v) * (incoming ? 10 : -8) },
        { scale: 0.96 + 0.04 * v },
      ],
    };
  });
  const tint = wd.done ? "#6BDC74" : i === 2 ? "#FFC14D" : "#7FE6F5";
  return (
    <Animated.View
      style={[StyleSheet.absoluteFill, styles.labelLayer, st]}
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? "no-hide-descendants" : "auto"}
    >
      <Text style={styles.title} accessibilityRole="header">
        {wd.name}
      </Text>
      <View
        style={[
          styles.status,
          { borderColor: tint + "88", backgroundColor: tint + "1F" },
        ]}
      >
        {wd.done ? (
          <Ionicons
            name="checkmark-circle"
            size={14}
            color={tint}
            accessibilityElementsHidden
            importantForAccessibility="no"
          />
        ) : null}
        <Text style={[styles.statusText, { color: tint }]}>{wd.status}</Text>
      </View>
    </Animated.View>
  );
}

const STAT_ICONS = ["time-outline", "checkmark-done", "gift-outline"] as const;
const STAT_NAMES = ["best run", "tasks", "chests"];

function WorldStats({
  i,
  out,
  hidden,
}: {
  i: number;
  out: SharedValue<Out>;
  hidden: boolean;
}) {
  const st = useAnimatedStyle(() => {
    const o = out.get();
    const v = labelVisOf(i, o.cur, o.nxt, o.m);
    return { opacity: v, transform: [{ translateY: (1 - v) * 6 }] };
  });
  const wd = WORLDS[i];
  return (
    <Animated.View
      style={[StyleSheet.absoluteFill, styles.statsLayer, st]}
      accessible={!hidden}
      accessibilityElementsHidden={hidden}
      accessibilityLabel={wd.stats
        .map((x, k) => `${STAT_NAMES[k]} ${x}`)
        .join(", ")}
    >
      {wd.stats.map((x, k) => (
        <View key={STAT_NAMES[k]} style={styles.stat}>
          <Ionicons
            name={STAT_ICONS[k]}
            size={14}
            color="rgba(207,239,244,0.62)"
          />
          <Text style={styles.statText}>{x}</Text>
        </View>
      ))}
    </Animated.View>
  );
}

function CtaLabel({
  i,
  out,
  hidden,
}: {
  i: number;
  out: SharedValue<Out>;
  hidden: boolean;
}) {
  const st = useAnimatedStyle(() => {
    const o = out.get();
    const v = labelVisOf(i, o.cur, o.nxt, o.m);
    return { opacity: v, transform: [{ translateY: (1 - v) * 6 }] };
  });
  return (
    <Animated.View
      style={[StyleSheet.absoluteFill, styles.center, st]}
      pointerEvents="none"
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? "no-hide-descendants" : "auto"}
    >
      <Text style={styles.ctaText}>{WORLDS[i].cta}</Text>
    </Animated.View>
  );
}

function Dot({
  i,
  out,
  onPress,
}: {
  i: number;
  out: SharedValue<Out>;
  onPress: () => void;
}) {
  const st = useAnimatedStyle(() => {
    const o = out.get();
    const v = visOf(i, o.cur, o.nxt, o.m);
    return { width: 7 + 15 * v, opacity: 0.35 + 0.65 * v };
  });
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${WORLDS[i].name}, world ${i + 1} of ${N}`}
      style={styles.dotHit}
    >
      <Animated.View style={[styles.dot, st]} />
    </Pressable>
  );
}

const glass = {
  backgroundColor: "rgba(7,26,33,0.62)",
  borderWidth: StyleSheet.hairlineWidth * 2,
  borderColor: "rgba(160,230,240,0.16)",
} as const;

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#0E2A34" },
  center: { alignItems: "center", justifyContent: "center" },
  touch: { position: "absolute", left: 0, right: 0 },
  hud: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    justifyContent: "space-between",
  },
  row: { flexDirection: "row", alignItems: "center", gap: 8, height: 42 },
  levelRing: {
    width: 42,
    height: 42,
    borderRadius: 21,
    borderWidth: 3,
    borderColor: "#FFD84A",
    backgroundColor: "#0B2129",
    alignItems: "center",
    justifyContent: "center",
  },
  levelNum: {
    fontFamily: "TWP-Title",
    fontSize: 16,
    color: "#FFFFFF",
    marginTop: 1,
  },
  energy: {
    ...glass,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 32,
    paddingHorizontal: 10,
    borderRadius: 16,
  },
  energyTrack: {
    width: 64,
    height: 8,
    borderRadius: 4,
    backgroundColor: "rgba(255,255,255,0.12)",
    overflow: "hidden",
  },
  energyFill: { height: 8, borderRadius: 4, backgroundColor: "#FFD84A" },
  hudRight: { alignItems: "flex-end", gap: 8 },
  pill: {
    ...glass,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    height: 32,
    paddingHorizontal: 12,
    borderRadius: 16,
  },
  hudNum: {
    fontFamily: "TWP-Black",
    fontSize: 14,
    color: "#FFFFFF",
    fontVariant: ["tabular-nums"],
  },
  hudDim: { fontFamily: "TWP-Bold", color: "rgba(207,239,244,0.55)" },
  coin: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: "#FFC531",
    alignItems: "center",
    justifyContent: "center",
  },
  coinInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: "#E39A12",
  },
  sideCol: { position: "absolute", right: 16, gap: 12 },
  sideBtn: { ...glass, alignItems: "center", justifyContent: "center" },
  badge: {
    position: "absolute",
    top: -5,
    right: -5,
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    backgroundColor: "#FF5A4E",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 2,
    borderColor: "#0B2129",
  },
  badgeText: { fontFamily: "TWP-Black", fontSize: 11, color: "#FFFFFF" },
  bubble: {
    position: "absolute",
    width: 44,
    height: 44,
    borderRadius: 14,
    ...glass,
    backgroundColor: "rgba(255,255,255,0.14)",
  },
  labels: { position: "absolute", left: 24, right: 24, height: 80 },
  labelLayer: { alignItems: "center" },
  title: {
    fontFamily: "TWP-Title",
    fontSize: 32,
    lineHeight: 38,
    color: "#FFFFFF",
    textShadowColor: "rgba(0,0,0,0.25)",
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 0,
  },
  status: {
    marginTop: 10,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    borderWidth: 1.5,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  statusText: { fontFamily: "TWP-Black", fontSize: 12, letterSpacing: 1.4 },
  statsRow: { position: "absolute", left: 24, right: 24, height: 20 },
  statsLayer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 18,
  },
  stat: { flexDirection: "row", alignItems: "center", gap: 5 },
  statText: {
    fontFamily: "TWP-Bold",
    fontSize: 13,
    color: "rgba(207,239,244,0.78)",
    fontVariant: ["tabular-nums"],
  },
  dots: {
    position: "absolute",
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 0,
  },
  dotHit: {
    width: 30,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  dot: { height: 7, borderRadius: 3.5, backgroundColor: "#CFEFF4" },
  bottom: {
    position: "absolute",
    left: 16,
    right: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  cta: {
    flex: 1,
    height: 56,
    borderRadius: 28,
    backgroundColor: "#FFD84A",
    borderBottomWidth: 4,
    borderBottomColor: "#D9A21E",
  },
  ctaText: {
    fontFamily: "TWP-Black",
    fontSize: 18,
    color: "#2A1E05",
    letterSpacing: 0.2,
  },
  pressed: { transform: [{ scale: 0.96 }] },
});
