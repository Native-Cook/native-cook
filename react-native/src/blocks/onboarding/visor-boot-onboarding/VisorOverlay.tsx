import type { JSX } from "react";
import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import Animated, { useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import Svg, { Path } from "react-native-svg";
import { scheduleOnRN } from "react-native-worklets";

import { PressScale } from "@/shared/PressScale";

import {
  blendChannel,
  clamp01,
  filmCopy,
  filmCta,
  filmCtaAnchor,
  filmDot,
  lerp,
  pageCopy,
  pageCta,
  pageDot,
  seg,
} from "./visorMotion";
import { C } from "./visorScene";

/** The five copy blocks: step label (none on the intro and the end), title, body. */
const COPY = [
  { label: "", title: "Meet Halo", body: "Your visor shows what matters, right where you look. Setup takes about a minute." },
  { label: "Step 1 of 3", title: "See more at a glance", body: "Directions, distance and light appear on the visor only when you look up." },
  { label: "Step 2 of 3", title: "Stay aware", body: "Sensors on each side notice what\u2019s behind you and give one gentle pulse." },
  { label: "Step 3 of 3", title: "Hands-free", body: "Ask Halo anything. Answers appear on the visor, not out loud." },
  { label: "", title: "You\u2019re all set", body: "Next, a quick fit check. It takes about 40 seconds." },
] as const;

const DOT = 6;
const DOT_STRETCH = 18;
const DOT_GAP = 8;
const ROW_H = 56;
export const GUTTER = 24;

type Film = {
  t: SharedValue<number>;
  page: SharedValue<number>;
  m: SharedValue<number>;
};

function CopyBlock({ i, film, compact }: { i: number; film: Film; compact: boolean }) {
  const style = useAnimatedStyle(() => {
    const a = [0, 0];
    const b = [0, 0];
    filmCopy(i, film.t.get(), a);
    pageCopy(i, film.page.get(), b);
    const m = film.m.get();
    const o = blendChannel(a[0], b[0], m);
    return { opacity: o, transform: [{ translateY: blendChannel(a[1], b[1], m) }] };
  });
  const c = COPY[i];
  const size = compact ? 30 : 34;
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.copy, style]}>
      {c.label !== "" && <Text style={styles.label}>{c.label}</Text>}
      <Text style={[styles.title, { fontSize: size, lineHeight: Math.round(size * 1.18) }]}>{c.title}</Text>
      <Text style={[styles.body, compact && styles.bodyCompact]}>{c.body}</Text>
    </Animated.View>
  );
}

/** Stretch of dot i (0..1), the film's or the pages'. */
function dotK(film: Film, i: number): number {
  "worklet";
  return blendChannel(filmDot(i, film.t.get()), pageDot(i, film.page.get()), film.m.get());
}

function ctaK(film: Film): number {
  "worklet";
  return blendChannel(filmCta(film.t.get()), pageCta(film.page.get()), film.m.get());
}

/** Left edge of dot i in the row, from the stretch of the dots before it. */
function dotLeft(film: Film, i: number): number {
  "worklet";
  let x = 0;
  for (let j = 0; j < i; j++) x += DOT + DOT_STRETCH * dotK(film, j) + DOT_GAP;
  return x;
}

function Dot({ i, film, onPress }: { i: number; film: Film; onPress: (i: number) => void }) {
  const style = useAnimatedStyle(() => {
    const k = dotK(film, i);
    // the dots give way to the button as it grows
    const fade = 1 - clamp01(ctaK(film) * 3);
    return { width: DOT + DOT_STRETCH * k, opacity: (0.28 + 0.72 * k) * fade };
  });
  return (
    <Pressable accessibilityLabel={`Step ${i}`} hitSlop={{ top: 18, bottom: 18, left: 4, right: 4 }} onPress={() => onPress(i)}>
      <Animated.View style={[styles.dot, style]} />
    </Pressable>
  );
}

export type VisorOverlayProps = {
  film: Film;
  width: number;
  top: number;
  bottom: number;
  compact: boolean;
  copyHeight: number;
  reduced: boolean;
  onPage: (i: number) => void;
  onNext: () => void;
  onSkip: () => void;
  onStart: () => void;
};

/** Header, copy, pager and the Get started button that grows out of the pager's pill. */
export function VisorOverlay(p: VisorOverlayProps): JSX.Element {
  const { film, width, top, bottom, compact, copyHeight, reduced } = p;
  const rowW = Math.min(width, 520) - GUTTER * 2;
  const [ctaLive, setCtaLive] = useState(false);
  useAnimatedReaction(
    () => ctaK(film) > 0.5,
    (on, prev) => {
      if (on !== prev) scheduleOnRN(setCtaLive, on);
    },
  );

  const button = useAnimatedStyle(() => {
    const g = ctaK(film);
    const anchor = filmCtaAnchor(film.t.get()) * (1 - clamp01(film.m.get())) + 3 * clamp01(film.m.get());
    const left0 = dotLeft(film, Math.round(anchor));
    const h = lerp(DOT, ROW_H, g);
    const w = lerp(DOT + DOT_STRETCH, rowW, g);
    return {
      opacity: g > 0.002 ? 1 : 0,
      left: lerp(left0, 0, Math.min(1, g)),
      width: w,
      height: h,
      top: (ROW_H - h) / 2,
      borderRadius: h / 2,
    };
  });
  const buttonLabel = useAnimatedStyle(() => {
    const g = ctaK(film);
    return { opacity: seg(g, 0.6, 1), transform: [{ translateY: (1 - seg(g, 0.6, 1)) * 6 }] };
  });
  const next = useAnimatedStyle(() => {
    const g = ctaK(film);
    return { opacity: 1 - clamp01(g * 2.5), transform: [{ scale: 1 - 0.1 * clamp01(g * 2.5) }] };
  });

  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <View pointerEvents="box-none" style={[styles.header, { top: top + 10 }]}>
        <Text style={styles.brandText}>Halo</Text>
        <Pressable accessibilityRole="button" hitSlop={14} onPress={p.onSkip}>
          <Text style={styles.skip}>Skip</Text>
        </Pressable>
      </View>

      <View pointerEvents="none" style={[styles.copyBox, { bottom: bottom + 20 + ROW_H + 22, height: copyHeight, width: rowW }]}>
        {COPY.map((_, i) => (
          <CopyBlock compact={compact} film={film} i={i} key={i} />
        ))}
      </View>

      <View pointerEvents="box-none" style={[styles.row, { bottom: bottom + 20, width: rowW }]}>
        <View style={styles.dots}>
          {[0, 1, 2, 3].map((i) => (
            <Dot film={film} i={i} key={i} onPress={p.onPage} />
          ))}
        </View>
        <Animated.View style={[styles.next, next]}>
          <PressScale accessibilityLabel="Next" accessibilityRole="button" onPress={p.onNext} reduced={reduced} scale={0.94} style={styles.nextPress}>
            <Svg height={18} viewBox="0 0 18 18" width={18}>
              <Path d="M2 9h13M10 3.5 15.5 9 10 14.5" fill="none" stroke={C.ink} strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} />
            </Svg>
          </PressScale>
        </Animated.View>
        <View pointerEvents={ctaLive ? "auto" : "none"} style={StyleSheet.absoluteFill}>
          <PressScale accessibilityLabel="Get Started" accessibilityRole="button" onPress={p.onStart} reduced={reduced} scale={0.97} style={StyleSheet.absoluteFill}>
            <Animated.View style={[styles.button, button]} />
            <Animated.View pointerEvents="none" style={[styles.buttonLabelRow, buttonLabel]}>
              <Text style={styles.buttonText}>Get Started</Text>
              <Svg height={16} viewBox="0 0 18 18" width={16}>
                <Path d="M2 9h13M10 3.5 15.5 9 10 14.5" fill="none" stroke={C.bgEdge} strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} />
              </Svg>
            </Animated.View>
          </PressScale>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { position: "absolute", left: GUTTER, right: GUTTER, height: 32, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  brandText: { fontSize: 17, fontWeight: "700", color: C.ink },
  skip: { fontSize: 17, color: C.mute },
  copyBox: { position: "absolute", left: GUTTER },
  copy: { justifyContent: "flex-end" },
  label: { fontSize: 13, fontWeight: "600", color: C.amber, marginBottom: 8 },
  title: { fontWeight: "700", color: C.ink, letterSpacing: -0.4 },
  body: { fontSize: 17, lineHeight: 23, color: C.mute, marginTop: 10, maxWidth: 340 },
  bodyCompact: { fontSize: 15, lineHeight: 20, marginTop: 6 },
  row: { position: "absolute", left: GUTTER, height: ROW_H, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  dots: { flexDirection: "row", alignItems: "center", gap: DOT_GAP, height: ROW_H },
  dot: { height: DOT, borderRadius: DOT / 2, backgroundColor: C.ink },
  next: { width: ROW_H, height: ROW_H },
  nextPress: { width: ROW_H, height: ROW_H, borderRadius: ROW_H / 2, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: "rgba(238,240,242,0.28)", alignItems: "center", justifyContent: "center" },
  button: { position: "absolute", backgroundColor: C.ink },
  buttonLabelRow: { ...StyleSheet.absoluteFill, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10 },
  buttonText: { fontSize: 17, fontWeight: "600", color: C.bgEdge },
});
