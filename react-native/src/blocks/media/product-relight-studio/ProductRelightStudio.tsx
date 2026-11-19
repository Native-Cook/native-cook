import { Host, Picker, Slider, Text as SwiftText } from "@expo/ui/swift-ui";
import { pickerStyle, tag, tint } from "@expo/ui/swift-ui/modifiers";
import { BlurMask, Canvas, Circle, FilterMode, Group, ImageShader, Line, MipmapMode, Rect, RoundedRect, Shader, useImage, vec } from "@shopify/react-native-skia";
import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useCallback, useEffect, useState } from "react";
import type { JSX } from "react";
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import {
  clamp01,
  compareDipAt,
  grabAt,
  IDLE_MS,
  LEVEL,
  levelAt,
  LIGHT_REACH,
  lightAt,
  LOOP,
  PRESET_HINTS,
  PRESET_NAMES,
  RETURN_MS,
  RIG_SIZE,
  rigMix,
  splitAt,
  splitGrabAt,
  weightsAt,
} from "./relightMotion";
import { relightEffect } from "./relightShader";

const ALBEDO = require("./assets/albedo.jpg");
const GBUFFER = require("./assets/gbuffer.png");
const MATERIAL = require("./assets/material.jpg");
const MATTE = require("./assets/matte.png");
const BEFORE = require("./assets/before.jpg");

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const RIG_MS = 800;
/** Skia writes a runtime effect's output as Display P3 on iOS; the shader converts from sRGB there. */
const WIDE = Platform.OS === "ios" ? 1 : 0;
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None } as const;

const C = {
  bg: "#0C0C0D",
  ink: "#F5F5F4",
  secondary: "rgba(245,245,244,0.64)",
  accent: "#FFD60A",
} as const;

function tapFeel(): void {
  void Haptics.selectionAsync();
}
function grabFeel(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

/** Shader uniforms (module level: the React Compiler gotcha). The slider's level scales the key's power. */
function uniformsFor(size: number, lx: number, ly: number, w0: number, w1: number, w2: number, level: number, split: number) {
  "worklet";
  const r = new Array<number>(RIG_SIZE);
  rigMix(w0, w1, w2, r);
  const k = r[3] * (Math.max(0.04, level) / LEVEL);
  return {
    size,
    light: [lx, ly, r[4]],
    keyCol: [r[0] * k, r[1] * k, r[2] * k],
    rimCol: [r[5] * r[8], r[6] * r[8], r[7] * r[8]],
    ambCol: [r[9], r[10], r[11]],
    paper: [r[12], r[13], r[14]],
    soft: r[15],
    split,
    wide: WIDE,
  };
}

/** Light (photo widths, y up) → point on the stage. */
function toStage(v: number, size: number, flip: boolean): number {
  "worklet";
  return size * (0.5 + (flip ? -v : v));
}

function argmax3(a: number, b: number, c: number): number {
  "worklet";
  return a >= b && a >= c ? 0 : b >= c ? 1 : 2;
}

// ---------------------------------------------------------------- pieces

function PresetTitleLine({ i, w, scale }: { i: number; w: SharedValue<number>; scale: number }) {
  const style = useAnimatedStyle(() => {
    const k = clamp01(w.get());
    return { opacity: clamp01((k - 0.35) / 0.65), transform: [{ translateY: (1 - k) * 6 * scale }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
      <Text accessibilityRole="header" style={[styles.title, { fontSize: 28 * scale, lineHeight: 34 * scale }]}>
        {PRESET_NAMES[i]}
      </Text>
      <Text numberOfLines={1} style={[styles.hint, { fontSize: 15 * scale, lineHeight: 20 * scale, marginTop: 2 * scale }]}>
        {PRESET_HINTS[i]}
      </Text>
    </Animated.View>
  );
}

/** The system segmented control (SwiftUI through @expo/ui); the film drives it through `selected`. */
function PresetPicker({ selected, onSelect, width }: { selected: number; onSelect: (i: number) => void; width: number }): JSX.Element {
  if (Platform.OS === "ios") {
    return (
      <Host colorScheme="dark" style={{ width, height: 32 }}>
        <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={onSelect} selection={selected}>
          {PRESET_NAMES.map((o, i) => (
            <SwiftText key={o} modifiers={[tag(i)]}>
              {o}
            </SwiftText>
          ))}
        </Picker>
      </Host>
    );
  }
  return (
    <View style={[styles.segRow, { width }]}>
      {PRESET_NAMES.map((o, i) => (
        <Pressable accessibilityRole="button" key={o} onPress={() => onSelect(i)} style={[styles.segCell, i === selected && styles.segOn]}>
          <Text style={styles.segText}>{o}</Text>
        </Pressable>
      ))}
    </View>
  );
}

/** The system slider on iOS; elsewhere a plain bar stands in. */
function IntensitySlider({ value, onChange, onEditing, width }: { value: number; onChange: (v: number) => void; onEditing: (a: boolean) => void; width: number }): JSX.Element {
  if (Platform.OS === "ios") {
    return (
      <Host colorScheme="dark" style={{ width, height: 30 }}>
        <Slider max={1} min={0} modifiers={[tint(C.ink)]} onEditingChanged={onEditing} onValueChange={onChange} value={value} />
      </Host>
    );
  }
  return (
    <View accessibilityLabel="Light intensity" style={[styles.track, { width }]}>
      <View style={[styles.fill, { width: width * value }]} />
    </View>
  );
}

/**
 * The preset picker and the intensity row with their React state. The state lives here, not in the screen: the film's
 * slider steps (every frame while the intensity goes up and back) used to re-render the whole screen, canvas included.
 */
function Controls({
  scale,
  column,
  w0,
  w1,
  w2,
  level,
  sliding,
  onChoose,
  onIntensity,
  onSliding,
}: {
  scale: number;
  column: number;
  w0: SharedValue<number>;
  w1: SharedValue<number>;
  w2: SharedValue<number>;
  level: SharedValue<number>;
  sliding: SharedValue<boolean>;
  onChoose: (i: number) => void;
  onIntensity: (v: number) => void;
  onSliding: (active: boolean) => void;
}): JSX.Element {
  const [selected, setSelected] = useState(0);
  const [sliderLevel, setSliderLevel] = useState(LEVEL);
  useAnimatedReaction(
    () => argmax3(w0.get(), w1.get(), w2.get()),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setSelected, v);
    },
  );
  useAnimatedReaction(
    () => (sliding.get() ? -1 : Math.round(level.get() * 100)),
    (v, prev) => {
      if (v >= 0 && v !== prev) scheduleOnRN(setSliderLevel, v / 100);
    },
  );
  const choose = useCallback(
    (i: number) => {
      setSelected(i);
      onChoose(i);
    },
    [onChoose],
  );
  const intensity = useCallback(
    (v: number) => {
      setSliderLevel(v);
      onIntensity(v);
    },
    [onIntensity],
  );
  return (
    <>
      <View style={{ marginTop: 14 * scale }}>
        <PresetPicker onSelect={choose} selected={selected} width={column} />
      </View>
      <View style={[styles.sliderRow, { marginTop: 18 * scale, gap: 12 * scale }]}>
        <Text style={[styles.sliderLabel, { fontSize: 15 * scale }]}>Intensity</Text>
        <IntensitySlider onChange={intensity} onEditing={onSliding} value={sliderLevel} width={column - 96 * scale} />
        <Text style={[styles.value, { fontSize: 15 * scale, width: 32 * scale }]}>{Math.round(sliderLevel * 100)}</Text>
      </View>
    </>
  );
}

// ---------------------------------------------------------------- the screen

export type ProductRelightStudioProps = { autoPlay?: boolean };

export function ProductRelightStudio({ autoPlay = true }: ProductRelightStudioProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const scale = Math.min(1.1, Math.max(0.88, width / 393));
  const top = insets.top + 44 * scale;
  const size = Math.min(width, 560, height - top - insets.bottom - 250 * scale);
  const side = (width - size) / 2;
  const column = Math.min(width - 40 * scale, 520);

  const albedo = useImage(ALBEDO);
  const gbuf = useImage(GBUFFER);
  const mat = useImage(MATERIAL);
  const matte = useImage(MATTE);
  const before = useImage(BEFORE);
  const ready = albedo != null && gbuf != null && mat != null && matte != null && before != null;

  const t = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const loaded = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const lx = useSharedValue(-0.262);
  const ly = useSharedValue(0.279);
  const w0 = useSharedValue(1);
  const w1 = useSharedValue(0);
  const w2 = useSharedValue(0);
  /** The intensity slider, 0..1 (LEVEL is the rig as designed). */
  const level = useSharedValue(LEVEL);
  const grab = useSharedValue(0);
  const split = useSharedValue(0);
  const splitGrab = useSharedValue(0);
  const dip = useSharedValue(0);
  const dragging = useSharedValue(false);
  const draggingSplit = useSharedValue(false);
  const sliding = useSharedValue(false);
  const rigBusy = useSharedValue(false);
  useEffect(() => {
    loaded.set(ready);
  }, [loaded, ready]);


  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (!loaded.get()) {
        t.set(0);
      } else {
        if (loopStart.get() < 0) loopStart.set(now);
        t.set(((now - loopStart.get()) / 1000) % LOOP);
      }
      const tt = t.get();
      const l = [0, 0];
      lightAt(tt, l);
      lx.set(l[0]);
      ly.set(l[1]);
      const w = [0, 0, 0];
      weightsAt(tt, w);
      w0.set(w[0]);
      w1.set(w[1]);
      w2.set(w[2]);
      grab.set(grabAt(tt));
      split.set(splitAt(tt));
      splitGrab.set(splitGrabAt(tt));
      dip.set(compareDipAt(tt));
      level.set(levelAt(tt));
    } else if (m === MODE_MANUAL) {
      const busy = dragging.get() || draggingSplit.get() || sliding.get() || rigBusy.get();
      if (lastActivity.get() < 0 || busy) {
        lastActivity.set(now);
      } else if (film && now - lastActivity.get() >= IDLE_MS) {
        // 2 s after the last touch: the rig, the light and the divider ease back to the poster, then the film runs
        mode.set(MODE_RETURN);
        const timing = { duration: RETURN_MS, easing: EASE_IN_OUT };
        const l = [0, 0];
        lightAt(0, l);
        lx.set(withTiming(l[0], timing));
        ly.set(withTiming(l[1], timing));
        w0.set(withTiming(1, timing));
        w1.set(withTiming(0, timing));
        w2.set(withTiming(0, timing));
        level.set(withTiming(LEVEL, timing));
        split.set(withTiming(0, timing));
        splitGrab.set(withTiming(0, timing));
        grab.set(
          withTiming(0, timing, (finished) => {
            "worklet";
            if (!finished) return;
            t.set(0);
            loopStart.set(-1);
            mode.set(MODE_AUTO);
          }),
        );
      }
    }
  });

  /** Any touch takes the photo over where the film is. */
  const takeOver = useCallback(() => {
    "worklet";
    if (mode.get() === MODE_RETURN) {
      cancelAnimation(lx);
      cancelAnimation(ly);
      cancelAnimation(w0);
      cancelAnimation(w1);
      cancelAnimation(w2);
      cancelAnimation(level);
      cancelAnimation(grab);
      cancelAnimation(split);
      cancelAnimation(splitGrab);
    }
    dip.set(0);
    mode.set(MODE_MANUAL);
    lastActivity.set(-1);
  }, [dip, grab, lastActivity, level, lx, ly, mode, split, splitGrab, w0, w1, w2]);

  /** The light follows the finger: a photo editor's light is placed, not nudged. */
  const place = useCallback(
    (x: number, y: number) => {
      "worklet";
      const nx = x / size - 0.5;
      const ny = -(y / size - 0.5);
      lx.set(Math.max(-LIGHT_REACH, Math.min(LIGHT_REACH, nx)));
      ly.set(Math.max(-LIGHT_REACH, Math.min(LIGHT_REACH, ny)));
    },
    [lx, ly, size],
  );

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      "worklet";
      takeOver();
      // a touch on the divider moves it; anywhere else places the light
      if (split.get() > 0.02 && Math.abs(e.x - split.get() * size) < 28) {
        draggingSplit.set(true);
        splitGrab.set(withTiming(1, { duration: 160 }));
        return;
      }
      dragging.set(true);
      grab.set(withTiming(1, { duration: 160 }));
      place(e.x, e.y);
      scheduleOnRN(grabFeel);
    })
    .onUpdate((e) => {
      "worklet";
      if (draggingSplit.get()) split.set(clamp01(e.x / size));
      else place(e.x, e.y);
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
      draggingSplit.set(false);
      grab.set(withTiming(0, { duration: 260 }));
      splitGrab.set(withTiming(0, { duration: 260 }));
    });

  const choose = useCallback(
    (i: number) => {
      tapFeel();
      takeOver();
      rigBusy.set(true);
      const timing = { duration: RIG_MS, easing: EASE_IN_OUT };
      w0.set(withTiming(i === 0 ? 1 : 0, timing));
      w1.set(withTiming(i === 1 ? 1 : 0, timing));
      w2.set(
        withTiming(i === 2 ? 1 : 0, timing, () => {
          "worklet";
          rigBusy.set(false);
        }),
      );
    },
    [rigBusy, takeOver, w0, w1, w2],
  );
  const setIntensity = useCallback(
    (v: number) => {
      takeOver();
      level.set(v);
    },
    [level, takeOver],
  );
  const slidingState = useCallback(
    (active: boolean) => {
      sliding.set(active);
      lastActivity.set(-1);
    },
    [lastActivity, sliding],
  );
  const compare = useCallback(() => {
    tapFeel();
    takeOver();
    split.set(withTiming(split.get() > 0.02 ? 0 : 0.5, { duration: 500, easing: EASE_IN_OUT }));
  }, [split, takeOver]);

  // ---- what the canvas reads
  const uniforms = useDerivedValue(() => uniformsFor(size, lx.get(), ly.get(), w0.get(), w1.get(), w2.get(), level.get(), split.get()));
  // the softbox gizmo: a thin outline with a small handle, quiet until a finger holds it
  const boxT = useDerivedValue(() => [
    { translateX: toStage(lx.get(), size, false) },
    { translateY: toStage(ly.get(), size, true) },
    { scale: 1 + 0.08 * grab.get() },
  ]);
  const boxOpacity = useDerivedValue(() => 0.3 + 0.7 * grab.get());
  const splitX = useDerivedValue(() => split.get() * size);
  const splitShown = useDerivedValue(() => clamp01(split.get() * 30));
  const lineP1 = useDerivedValue(() => vec(splitX.get(), 0));
  const lineP2 = useDerivedValue(() => vec(splitX.get(), size));
  const knobT = useDerivedValue(() => [{ translateX: splitX.get() }, { translateY: size * 0.5 }, { scale: 1 + 0.12 * splitGrab.get() }]);
  const labelBefore = useAnimatedStyle(() => ({ opacity: 0.9 * clamp01((split.get() - 0.14) * 8) }));
  const labelAfter = useAnimatedStyle(() => ({ opacity: 0.9 * clamp01((0.86 - split.get()) * 8) * clamp01(split.get() * 30) }));
  const compareStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.55 * dip.get() }));
  const rect = { x: 0, y: 0, width: size, height: size };
  const bw = 22 * scale;
  const bh = 30 * scale;
  const knob = 11 * scale;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View pointerEvents="box-none" style={[styles.bar, { top: insets.top, height: 44 * scale, paddingHorizontal: 20 * scale }]}>
        <View style={{ width: 80 * scale }} />
        <Text style={[styles.barTitle, { fontSize: 17 * scale }]}>Light</Text>
        <Pressable accessibilityLabel="Compare with the original" accessibilityRole="button" hitSlop={10} onPress={compare} style={{ width: 80 * scale, alignItems: "flex-end" }}>
          <Animated.Text style={[styles.barDone, { fontSize: 17 * scale }, compareStyle]}>Compare</Animated.Text>
        </Pressable>
      </View>
      <GestureDetector gesture={pan}>
        <View accessible accessibilityHint="Drag on the photo to move the softbox; drag the divider to compare" accessibilityLabel="Product photo of a pocket watch" style={{ position: "absolute", top, left: side, width: size, height: size }}>
          {ready && relightEffect != null ? (
            <Canvas style={{ width: size, height: size }}>
              <Rect height={size} width={size} x={0} y={0}>
                <Shader source={relightEffect} uniforms={uniforms}>
                  <ImageShader fit="fill" image={albedo} rect={rect} sampling={SAMPLING} />
                  <ImageShader fit="fill" image={gbuf} rect={rect} sampling={SAMPLING} />
                  <ImageShader fit="fill" image={mat} rect={rect} sampling={SAMPLING} />
                  <ImageShader fit="fill" image={matte} rect={rect} sampling={SAMPLING} />
                  <ImageShader fit="fill" image={before} rect={rect} sampling={SAMPLING} />
                </Shader>
              </Rect>
              {/* before | after: a hairline and a small frosted knob */}
              <Group opacity={splitShown}>
                <Line color="rgba(255,255,255,0.7)" p1={lineP1} p2={lineP2} strokeWidth={1} />
                <Group transform={knobT}>
                  <Circle color="rgba(0,0,0,0.18)" cx={0} cy={1.5 * scale} r={knob + 1}>
                    <BlurMask blur={4 * scale} style="normal" />
                  </Circle>
                  <Circle color="rgba(255,255,255,0.92)" cx={0} cy={0} r={knob} />
                  <Line color="#3A3A3C" p1={vec(-3 * scale, -3.5 * scale)} p2={vec(-6 * scale, 0)} strokeCap="round" strokeWidth={1.5} />
                  <Line color="#3A3A3C" p1={vec(-6 * scale, 0)} p2={vec(-3 * scale, 3.5 * scale)} strokeCap="round" strokeWidth={1.5} />
                  <Line color="#3A3A3C" p1={vec(3 * scale, -3.5 * scale)} p2={vec(6 * scale, 0)} strokeCap="round" strokeWidth={1.5} />
                  <Line color="#3A3A3C" p1={vec(6 * scale, 0)} p2={vec(3 * scale, 3.5 * scale)} strokeCap="round" strokeWidth={1.5} />
                </Group>
              </Group>
              {/* the softbox: a thin outline (a soft dark line under it keeps it legible on white paper) and a small handle */}
              <Group opacity={boxOpacity} transform={boxT}>
                <RoundedRect color="rgba(0,0,0,0.22)" height={bh} r={6 * scale} strokeWidth={3} style="stroke" width={bw} x={-bw / 2} y={-bh / 2}>
                  <BlurMask blur={2} style="normal" />
                </RoundedRect>
                <RoundedRect color="rgba(255,255,255,0.95)" height={bh} r={6 * scale} strokeWidth={1.25} style="stroke" width={bw} x={-bw / 2} y={-bh / 2} />
                <Circle color="rgba(0,0,0,0.2)" cx={0} cy={0} r={4 * scale} />
                <Circle color="white" cx={0} cy={0} r={3 * scale} />
              </Group>
            </Canvas>
          ) : null}
          <Animated.View pointerEvents="none" style={[styles.pill, { left: 12 * scale, top: 12 * scale }, labelBefore]}>
            <Text style={[styles.pillText, { fontSize: 12 * scale }]}>Before</Text>
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.pill, { right: 12 * scale, top: 12 * scale }, labelAfter]}>
            <Text style={[styles.pillText, { fontSize: 12 * scale }]}>After</Text>
          </Animated.View>
        </View>
      </GestureDetector>
      <View pointerEvents="box-none" style={{ position: "absolute", top: top + size + 20 * scale, left: (width - column) / 2, width: column }}>
        <View style={{ height: 58 * scale }}>
          <PresetTitleLine i={0} scale={scale} w={w0} />
          <PresetTitleLine i={1} scale={scale} w={w1} />
          <PresetTitleLine i={2} scale={scale} w={w2} />
        </View>
        <Controls
          column={column}
          level={level}
          onChoose={choose}
          onIntensity={setIntensity}
          onSliding={slidingState}
          scale={scale}
          sliding={sliding}
          w0={w0}
          w1={w1}
          w2={w2}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.bg },
  bar: { position: "absolute", left: 0, right: 0, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  barTitle: { color: C.ink, fontWeight: "600" },
  barDone: { color: C.accent, fontWeight: "600" },
  title: { color: C.ink, fontWeight: "700", letterSpacing: -0.4 },
  hint: { color: C.secondary, fontWeight: "400" },
  pill: { position: "absolute", paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10, backgroundColor: "rgba(0,0,0,0.28)" },
  pillText: { color: "#FFFFFF", fontWeight: "500" },
  segRow: { flexDirection: "row", height: 32, borderRadius: 9, padding: 2, backgroundColor: "rgba(118,118,128,0.24)" },
  segCell: { flex: 1, borderRadius: 7, alignItems: "center", justifyContent: "center" },
  segOn: { backgroundColor: "#636366" },
  segText: { color: C.ink, fontSize: 13, fontWeight: "600" },
  sliderRow: { flexDirection: "row", alignItems: "center" },
  sliderLabel: { color: C.ink, fontWeight: "500" },
  track: { height: 4, borderRadius: 2, backgroundColor: "rgba(245,245,244,0.14)", overflow: "hidden", marginVertical: 13 },
  fill: { height: "100%", backgroundColor: C.ink },
  value: { color: C.secondary, fontWeight: "500", textAlign: "right", fontVariant: ["tabular-nums"] },
});
