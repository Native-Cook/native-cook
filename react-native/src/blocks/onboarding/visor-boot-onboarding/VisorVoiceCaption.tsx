import type { JSX } from "react";
import { StyleSheet, TextInput } from "react-native";
import type { TextInputProps } from "react-native";
import Animated, { useAnimatedProps, useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import {
  blendChannel,
  cameraAt,
  cameraBasis,
  clamp01,
  filmRise,
  filmTranscript,
  filmVoice,
  pageRise,
  pageWeight,
  project,
  voiceWaveY,
} from "./visorMotion";
import { C, HELMET_Y, HFOV_DEG, MOUTH } from "./visorScene";

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);
const TRANSCRIPT = "“Mark this trail for later.”";

export type VisorVoiceCaptionProps = {
  width: number;
  height: number;
  limitY: number;
  t: SharedValue<number>;
  page: SharedValue<number>;
  m: SharedValue<number>;
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
};

/**
 * What Halo heard, typed in under the voice waveform (step 3). A text view in the system font, placed with the same
 * camera maths as the waveform VisorHud draws, so the two stay together.
 */
export function VisorVoiceCaption(p: VisorVoiceCaptionProps): JSX.Element {
  const { width: w, height: h, limitY } = p;
  const style = useAnimatedStyle(() => {
    const t = p.t.get();
    const pg = p.page.get();
    const m = p.m.get();
    const vis = blendChannel(filmVoice(t), pageWeight(pg, 3), m);
    if (vis <= 0.004) return { opacity: 0, transform: [{ translateY: -9999 }] };
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(t, pg, m, p.yaw.get(), p.pitch.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const lift = blendChannel(filmRise(t), pageRise(pg), m);
    const o = [0, 0, 0];
    if (!project(c, b, w, h, [MOUTH[0], MOUTH[1] + HELMET_Y + lift, MOUTH[2]], o)) return { opacity: 0, transform: [{ translateY: -9999 }] };
    return { opacity: clamp01(vis), transform: [{ translateY: voiceWaveY(o[1], h, limitY) + 24 }] };
  });
  // the string is computed here, in this component's own animated props (a worklet handed in from outside did not
  // update the text on device)
  const props = useAnimatedProps(() => {
    const typed = blendChannel(filmTranscript(p.t.get()), 1, p.m.get());
    const n = Math.ceil(clamp01(typed) * TRANSCRIPT.length);
    return { text: TRANSCRIPT.slice(0, n) } as unknown as TextInputProps;
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.wrap, style]}>
      <AnimatedTextInput
        animatedProps={props}
        caretHidden
        defaultValue=""
        editable={false}
        pointerEvents="none"
        style={styles.text}
        underlineColorAndroid="transparent"
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: "absolute", left: 24, right: 24, top: 0, alignItems: "center" },
  text: { fontSize: 17, fontWeight: "500", color: C.ink, textAlign: "center", padding: 0, minWidth: 300 },
});
