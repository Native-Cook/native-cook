import type { JSX, ReactNode } from "react";
import { useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import type { SharedValue } from "react-native-reanimated";

import { PressScale } from "@/shared/PressScale";

import { clamp01, posterUi, trackFill, welcomeItem, wordLetter } from "./spinMotion";
import { INK, MUTE, PAPER_TOP } from "./spinScene";

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

export const GUTTER = 24;
const WORD = "arca";

export type SpinOverlayProps = {
  width: number;
  height: number;
  top: number;
  bottom: number;
  compact: boolean;
  reduced: boolean;
  t: SharedValue<number>;
  yaw: SharedValue<number>;
  /** Baseline of the mark on the welcome screen; the wordmark sits under it. */
  finalBase: number;
  /** Screen y of the front of the floor track (the degree counter sits under it). */
  trackFront: number;
  onSkip: () => void;
  onStart: () => void;
  onSignIn: () => void;
};

/** The degrees turned so far, in a tiny animated text so the count never re-renders React. */
function Degrees({ t, yaw, top }: { t: SharedValue<number>; yaw: SharedValue<number>; top: number }) {
  const props = useAnimatedProps(() => {
    const d = Math.round(trackFill(t.get(), yaw.get()) * 360);
    return { text: `${d}°`, defaultValue: `${d}°` } as never;
  });
  const fade = useAnimatedStyle(() => ({ opacity: clamp01(posterUi(t.get())) }));
  return (
    <Animated.View pointerEvents="none" style={[styles.degreesBox, { top }, fade]}>
      <AnimatedTextInput animatedProps={props} editable={false} style={styles.degrees} underlineColorAndroid="transparent" />
    </Animated.View>
  );
}

function Letter({ ch, i, t }: { ch: string; i: number; t: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const k = wordLetter(t.get(), i);
    return { opacity: k, transform: [{ translateY: (1 - k) * 14 }] };
  });
  return <Animated.Text style={[styles.word, style]}>{ch}</Animated.Text>;
}

function Item({ j, t, children, style }: { j: number; t: SharedValue<number>; children: ReactNode; style?: object }) {
  const a = useAnimatedStyle(() => {
    const k = welcomeItem(t.get(), j);
    return { opacity: k, transform: [{ translateY: (1 - k) * 18 }] };
  });
  return <Animated.View style={[style, a]}>{children}</Animated.View>;
}

/** The poster's words (title, hint, degree counter, Skip) and the welcome screen the mark hands over to. */
export function SpinOverlay(p: SpinOverlayProps): JSX.Element {
  const { t, top, bottom, compact, reduced } = p;
  const poster = useAnimatedStyle(() => {
    const k = clamp01(posterUi(t.get()));
    return { opacity: k, transform: [{ translateY: (1 - k) * -8 }] };
  });
  // the welcome screen takes touches only while it is up, Skip only while the poster is (switched at the crossing,
  // never per frame)
  const [welcomeOn, setWelcomeOn] = useState(false);
  const [posterOn, setPosterOn] = useState(true);
  useAnimatedReaction(
    () => welcomeItem(t.get(), 2) > 0.5,
    (on, prev) => {
      if (on !== prev) scheduleOnRN(setWelcomeOn, on);
    },
  );
  useAnimatedReaction(
    () => posterUi(t.get()) > 0.5,
    (on, prev) => {
      if (on !== prev) scheduleOnRN(setPosterOn, on);
    },
  );
  const skip = useAnimatedStyle(() => ({ opacity: clamp01(posterUi(t.get())) }));

  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <Animated.View pointerEvents="none" style={[styles.posterHead, { top: top + (compact ? 20 : 36) }, poster]}>
        <Text style={[styles.title, compact && styles.titleCompact]}>Give it a full turn.</Text>
        <Text style={styles.body}>Drag the sculpture sideways. Arca opens when the circle closes.</Text>
      </Animated.View>
      <Degrees t={t} top={p.trackFront + (compact ? 14 : 22)} yaw={p.yaw} />
      <Animated.View pointerEvents={posterOn ? "auto" : "none"} style={[styles.skipBox, { bottom: bottom + 12 }, skip]}>
        <Pressable accessibilityRole="button" hitSlop={10} onPress={p.onSkip}>
          <Text style={styles.skip}>Skip</Text>
        </Pressable>
      </Animated.View>

      <View pointerEvents={welcomeOn ? "box-none" : "none"} style={StyleSheet.absoluteFill}>
        <View pointerEvents="none" style={[styles.wordRow, { top: p.finalBase + 10 }]}>
          {WORD.split("").map((ch, i) => (
            <Letter ch={ch} i={i} key={`${ch}${i}`} t={t} />
          ))}
        </View>
        <View pointerEvents="box-none" style={[styles.bottom, { bottom: bottom + 12 }]}>
          <Item j={0} t={t}>
            <Text style={[styles.title, compact && styles.titleCompact]}>{"Your money,\nin order."}</Text>
          </Item>
          <Item j={1} style={styles.bodyGap} t={t}>
            <Text style={styles.body}>Accounts, bills and savings goals in one calm place. Set up takes two minutes.</Text>
          </Item>
          <Item j={2} style={compact ? styles.buttonsCompact : styles.buttons} t={t}>
            <PressScale accessibilityRole="button" onPress={p.onStart} reduced={reduced} style={styles.primary}>
              <Text style={styles.primaryText}>Get started</Text>
            </PressScale>
          </Item>
          <Item j={3} t={t}>
            <PressScale accessibilityRole="button" onPress={p.onSignIn} reduced={reduced} scale={0.98} style={styles.secondary}>
              <Text style={styles.secondaryText}>I already have an account</Text>
            </PressScale>
          </Item>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  posterHead: { position: "absolute", left: GUTTER, right: GUTTER },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: "700", letterSpacing: -0.6 },
  titleCompact: { fontSize: 30, lineHeight: 35 },
  body: { color: MUTE, fontSize: 17, lineHeight: 23, marginTop: 10, maxWidth: 340 },
  bodyGap: { marginTop: 2 },
  degreesBox: { position: "absolute", left: 0, right: 0, alignItems: "center" },
  degrees: {
    color: INK,
    fontSize: 15,
    fontWeight: "600",
    fontVariant: ["tabular-nums"],
    textAlign: "center",
    minWidth: 64,
    padding: 0,
  },
  skipBox: { position: "absolute", alignSelf: "center" },
  skip: { color: MUTE, fontSize: 17, fontWeight: "600", paddingVertical: 10, paddingHorizontal: 16 },
  wordRow: { position: "absolute", left: 0, right: 0, flexDirection: "row", justifyContent: "center" },
  word: { color: INK, fontSize: 40, lineHeight: 46, fontWeight: "800", letterSpacing: -1 },
  bottom: { position: "absolute", left: GUTTER, right: GUTTER },
  buttons: { marginTop: 32 },
  buttonsCompact: { marginTop: 22 },
  primary: { height: 56, borderRadius: 28, backgroundColor: INK, alignItems: "center", justifyContent: "center" },
  primaryText: { color: PAPER_TOP, fontSize: 17, fontWeight: "600" },
  secondary: { height: 48, marginTop: 8, borderRadius: 24, alignItems: "center", justifyContent: "center" },
  secondaryText: { color: INK, fontSize: 17, fontWeight: "600" },
});
