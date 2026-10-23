import { useState } from "react";
import { Platform, PixelRatio, StyleSheet, View, useWindowDimensions } from "react-native";
import { NumericText } from "expo-content-transition";
import { Canvas, Fill, Shader, Skia } from "@shopify/react-native-skia";
import Animated, {
  type DerivedValue, cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { HorizonStatusIcon } from "./horizon-icons";
import { horizonLayout, horizonSize as S, horizonTone } from "./horizon-palette";
import { type HorizonFx, type HorizonPhase } from "./horizon-state";
import { usePreferences } from "./i18n";
import { horizonFace } from "./ui";

export const PANEL_METRICS = { bleed: 18, percentage: 78, figureHeight: 36, figureRise: 19,
  glyph: 18, glyphBleed: 8, labelGap: 10, figureGap: 8, labelAdvance: 114 };

export function horizonPanelLayout(width: number, fontScale = 1) {
  const { panel, bar } = horizonLayout(width);
  // 114 pt includes the widest Vietnamese label plus pixel-hinting allowance.
  // Increase both text sizes together only as far as this row can fit.
  const fitScale = (bar.width - S.statusGlyph - PANEL_METRICS.labelGap - PANEL_METRICS.figureGap)
    / (PANEL_METRICS.percentage + PANEL_METRICS.labelAdvance);
  const labelAscentAtOne = (20 + 15 * (1.066 - 0.3)) / 2;
  const numberBaselineAtOne = Math.max(0, (PANEL_METRICS.figureHeight - 26 * 1.366) / 2) + 26 * 1.066;
  const verticalScale = (S.barY - S.barHeight / 2 - 3) / (numberBaselineAtOne + 20 - labelAscentAtOne);
  const typeScale = Math.min(fontScale, 1.3, verticalScale, width >= 303 ? Math.max(1, fitScale) : 1.3);
  const figureWidth = PANEL_METRICS.percentage * typeScale;
  const figureTop = Math.max(0, S.rowY - PANEL_METRICS.figureRise * typeScale);
  // Manrope's shared hhea ascent/descent are 1066/300 per 1000 em.
  const baseline = figureTop + numberBaselineAtOne * typeScale;
  const labelAscent = labelAscentAtOne * typeScale;
  const labelLeft = S.panelPad + S.statusGlyph + PANEL_METRICS.labelGap;
  return { panel, bar, typeScale, figureWidth, figureTop, baseline,
    labelTop: baseline - labelAscent, labelLeft,
    labelWidth: panel.width - S.panelPad - figureWidth - PANEL_METRICS.figureGap - labelLeft,
    figureClipHeight: Math.min(PANEL_METRICS.figureHeight * typeScale, S.barY - S.barHeight / 2 - figureTop - 3),
    glyphTop: baseline - 5.5 * typeScale - S.statusGlyph / 2,
  };
}

// The groove and its light share one analytic capsule. Prefix every shader
// declaration so SkSL-to-WGSL translation cannot collide with a builtin.
export const BAR_SHADER = `
uniform float uBarWidth;
uniform float uProgress;
uniform float uPower;
uniform float uPulse;
uniform float uPaused;
uniform float uCalm;
uniform float uShine;
uniform float uMotion;
uniform float uDone;
uniform float uScale;
uniform float uThickness;
uniform float uBleed;
uniform float3 uTrackRGB;
uniform float3 uFillRGB;
uniform float3 uBloomRGB;
uniform float3 uDeepRGB;
float horizonTube(float2 bPoint, float bSpan) {
  float bRadius = min(uThickness * 0.5, bSpan * 0.5);
  float2 bInset = abs(bPoint - float2(uBleed + bSpan * 0.5, uBleed + uThickness * 0.5))
    - float2(max(0.0, bSpan * 0.5 - bRadius), uThickness * 0.5 - bRadius);
  return length(max(bInset, 0.0)) + min(max(bInset.x, bInset.y), 0.0) - bRadius;
}
float horizonBell(float bOffset) { return exp(-bOffset * bOffset); }
half4 main(float2 bPoint) {
  float bSpan = uBarWidth * clamp(uProgress, 0.0, 1.0);
  float bY = bPoint.y - uBleed - uThickness * 0.5;
  float bGroove = clamp(0.5 - horizonTube(bPoint, uBarWidth) * uScale, 0.0, 1.0);
  float bFillSdf = horizonTube(bPoint, max(bSpan, 0.001));
  float bVisible = smoothstep(0.0, 1.0, bSpan);
  float bFill = clamp(0.5 - bFillSdf * uScale, 0.0, 1.0) * bVisible;
  float bNear = exp(-pow(max(bFillSdf, 0.0), 2.0) / 50.0);
  float bBloomAlpha = bNear * mix(0.55, 0.35, uCalm) * uPower * (1.0 - uPaused) * bVisible;
  float3 bRGB = mix(uDeepRGB, uBloomRGB, bNear) * bBloomAlpha;
  float bAlpha = bBloomAlpha;
  float bTopShade = 1.0 - 0.45 * smoothstep(0.0, 2.0, -bY);
  float bLip = horizonBell((bY - uThickness * 0.5 + 0.35) / 0.28) * 0.18;
  float3 bGrooveRGB = uTrackRGB * 0.55 * bTopShade + uBloomRGB * bLip;
  bRGB = bRGB * (1.0 - bGroove) + bGrooveRGB * bGroove;
  bAlpha = bAlpha * (1.0 - bGroove) + bGroove;
  float bHot = exp(-bY * bY / 0.65);
  float bBeatPos = uBleed + (1.0 - uPulse) * bSpan;
  float bBeat = horizonBell((bPoint.x - bBeatPos) / 13.0) * 0.35
    * smoothstep(0.0, 0.08, uPulse) * uMotion * (1.0 - uDone) * (1.0 - uPaused);
  float bSweep = 0.0;
  if (uShine > 0.0 && uShine < 1.0) {
    bSweep = horizonBell((bPoint.x - uBleed - uShine * uBarWidth) / 13.0)
      * 0.6 * sin(3.14159265 * uShine) * uMotion;
  }
  float3 bCoreRGB = clamp(mix(uBloomRGB, uFillRGB, bHot) * (1.0 + bBeat + bSweep), 0.0, 1.0);
  float bCoreAlpha = bFill * (1.0 - 0.45 * uPaused);
  bRGB = bRGB * (1.0 - bCoreAlpha) + bCoreRGB * bCoreAlpha;
  bAlpha = bAlpha * (1.0 - bCoreAlpha) + bCoreAlpha;
  float bHeadX = bPoint.x - uBleed - max(2.0, bSpan - 2.0);
  float bCap = exp(-bHeadX * bHeadX / 3.2 - bY * bY / 4.0);
  float bFlare = pow(max(0.0, 1.0 - abs(bHeadX) / 12.0), 2.0) * exp(-bY * bY / 0.8);
  float bHeadAlpha = clamp(bCap * 0.4 + bFlare * (0.2 + uPulse * 0.65), 0.0, 0.95)
    * bVisible * (1.0 - uPaused) * (1.0 - uDone);
  bRGB = bRGB * (1.0 - bHeadAlpha) + uFillRGB * bHeadAlpha;
  bAlpha = bAlpha * (1.0 - bHeadAlpha) + bHeadAlpha;
  return half4(bRGB, bAlpha);
}`;
const BAR_TRACK = Array.from(Skia.Color(horizonTone.track)).slice(0, 3);
const BAR_FILL = Array.from(Skia.Color(horizonTone.fill)).slice(0, 3);
const BAR_BLOOM = Array.from(Skia.Color(horizonTone.bloom)).slice(0, 3);
const BAR_DEEP = Array.from(Skia.Color(horizonTone.deep)).slice(0, 3);
const BAR_FX = (() => {
  const effect = Skia.RuntimeEffect.Make(BAR_SHADER);
  if (!effect) throw new Error("Horizon Upload: progress bar shader did not compile.");
  return effect;
})();

const PHASES: HorizonPhase[] = ["idle", "uploading", "paused", "done", "stopped"];

export function HorizonPanel({ width, phase, percent, fx, reduced, displayPhase }: {
  width: number; phase: HorizonPhase; percent: number; fx: HorizonFx; reduced: boolean; displayPhase: DerivedValue<HorizonPhase>;
}) {
  const { t } = usePreferences();
  const [labelPhase, setLabelPhase] = useState(phase);
  useAnimatedReaction(() => displayPhase.get(), (next, before) => {
    if (next !== before) scheduleOnRN(setLabelPhase, next);
  });
  const { fontScale } = useWindowDimensions();
  const { panel, bar, typeScale, figureWidth, figureTop, figureClipHeight, labelLeft, labelWidth, labelTop, glyphTop } = horizonPanelLayout(width, fontScale);
  const shine = useSharedValue(-1);
  const calm = useSharedValue(0);
  useAnimatedReaction(() => displayPhase.get() === "done", (ready, before) => {
    if (ready === before) return;
    cancelAnimation(shine); cancelAnimation(calm);
    if (ready) {
      shine.set(reduced ? 1 : 0);
      if (!reduced) shine.set(withTiming(1, { duration: 300, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    } else {
      shine.set(-1);
      calm.set(withTiming(0, { duration: 220, reduceMotion: ReduceMotion.Never }));
    }
  });
  useAnimatedReaction(() => shine.get() === 1 && displayPhase.get() === "done", (ready, before) => {
    if (ready && !before) calm.set(withTiming(1, { duration: 220, reduceMotion: ReduceMotion.Never }));
  });
  const paused = useSharedValue(phase === "paused" ? 1 : 0);
  const figure = useSharedValue(phase === "idle" || phase === "stopped" ? 0.4 : 1);
  useAnimatedReaction(() => displayPhase.get(), (next, before) => {
    if (next === before) return;
    paused.set(withTiming(next === "paused" ? 1 : 0, { duration: 220, reduceMotion: ReduceMotion.Never }));
    figure.set(withTiming(next === "idle" || next === "stopped" ? 0.4 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
  });
  const figureStyle = useAnimatedStyle(() => ({ opacity: figure.get() }));
  const scale = PixelRatio.get();
  const uniforms = useDerivedValue(() => ({
    uBarWidth: bar.width,
    uProgress: displayPhase.get() === "idle" || displayPhase.get() === "stopped" ? 0 : fx.progress.get(),
    uPower: fx.power.get(), uPulse: fx.pulse.get(), uPaused: paused.get(), uCalm: calm.get(),
    uShine: shine.get(), uMotion: reduced ? 0 : 1, uDone: displayPhase.get() === "done" ? 1 : 0,
    uScale: scale, uThickness: S.barHeight, uBleed: PANEL_METRICS.bleed,
    uTrackRGB: BAR_TRACK, uFillRGB: BAR_FILL, uBloomRGB: BAR_BLOOM, uDeepRGB: BAR_DEEP,
  }));
  const labels = {
    idle: t("Tap to upload", "Chạm để tải lên"),
    uploading: t("Uploading ...", "Đang tải lên ..."),
    paused: t("Paused", "Đã tạm dừng"),
    done: t("Uploaded", "Đã tải lên"),
    stopped: t("Canceled", "Đã huỷ"),
  };
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {/* The status coins are images (horizon-icons.tsx); their box carries 6 pt of padding for the glow. */}
      <View style={[styles.glyph, { left: S.panelPad - 6, top: glyphTop - 6 }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <HorizonStatusIcon phase={labelPhase} fx={fx} reduced={reduced} />
      </View>
      <View style={[styles.labels, { left: labelLeft, width: labelWidth, top: labelTop, height: 20 * typeScale }]}
        accessibilityLiveRegion="polite" accessible accessibilityLabel={labels[labelPhase]}>
        {PHASES.map((key) => <StatusLabel key={key} text={labels[key]} initial={phase === key} ownPhase={key} displayPhase={displayPhase} reduced={reduced} typeScale={typeScale} />)}
      </View>
      <Animated.View style={[styles.figure, { right: S.panelPad, top: figureTop, width: figureWidth, height: figureClipHeight }, figureStyle]}
        accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <NumericText value={`${percent} %`} alignment="end" fontWeight="400"
          fontSize={26 * typeScale / (Platform.OS === "android" ? fontScale : 1)}
          monospacedDigits color={horizonTone.ink} animated={!reduced} duration={220} bounce={0}
          enterScale={1} travel={0.22} maxBlurRadius={4}
          style={{ width: figureWidth, height: PANEL_METRICS.figureHeight * typeScale }} />
      </Animated.View>
      <Canvas style={[styles.bar, {
        left: bar.x - panel.x - PANEL_METRICS.bleed,
        top: bar.y - panel.y - PANEL_METRICS.bleed,
        width: bar.width + PANEL_METRICS.bleed * 2,
        height: bar.height + PANEL_METRICS.bleed * 2,
      }]}>
        <Fill><Shader source={BAR_FX} uniforms={uniforms} /></Fill>
      </Canvas>
    </View>
  );
}

function StatusLabel({ text, initial, ownPhase, displayPhase, reduced, typeScale }: {
  text: string; initial: boolean; ownPhase: HorizonPhase; displayPhase: DerivedValue<HorizonPhase>; reduced: boolean; typeScale: number;
}) {
  const show = useSharedValue(initial ? 1 : 0);
  useAnimatedReaction(() => displayPhase.get() === ownPhase, (visible, before) => {
    if (visible !== before) show.set(withTiming(visible ? 1 : 0, { duration: reduced ? 120 : 220, reduceMotion: ReduceMotion.Never }));
  });
  const style = useAnimatedStyle(() => ({
    opacity: show.get() * (ownPhase === "done" ? 1 : 0.82), filter: `blur(${reduced ? 0 : (1 - show.get()) * 4}px)`,
    transform: [{ translateY: reduced ? 0 : (displayPhase.get() === ownPhase ? 1 : -1) * (1 - show.get()) * 4 }],
  }));
  return <Animated.Text accessible={false} numberOfLines={1} ellipsizeMode="tail" allowFontScaling={false} style={[styles.label, { fontSize: 15 * typeScale, lineHeight: 20 * typeScale }, style]}>{text}</Animated.Text>;
}

const styles = StyleSheet.create({
  glyph: { position: "absolute", width: S.statusGlyph, height: S.statusGlyph },
  glyphCanvas: { position: "absolute", left: -PANEL_METRICS.glyphBleed, top: -PANEL_METRICS.glyphBleed, width: S.statusGlyph + 2 * PANEL_METRICS.glyphBleed, height: S.statusGlyph + 2 * PANEL_METRICS.glyphBleed },
  labels: { position: "absolute" },
  label: { position: "absolute", width: "100%", fontFamily: horizonFace.label, fontSize: 15, lineHeight: 20, color: horizonTone.ink, includeFontPadding: false },
  figure: { position: "absolute", flexDirection: "row", alignItems: "flex-start", overflow: "hidden" },
  bar: { position: "absolute" },
});
