import { useEffect, useMemo, useRef } from "react";
import { AccessibilityInfo, StyleSheet, Text, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { scheduleOnUI } from "react-native-worklets";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, withSpring, withTiming } from "react-native-reanimated";
import { HorizonClose } from "./horizon-close";
import { HorizonFile } from "./horizon-file";
import { HorizonPanel } from "./horizon-panel";
import { horizonLayout, horizonSize as S, horizonTone } from "./horizon-palette";
import { cardScale } from "./horizon-state";
import { HORIZON_DRAG, type useHorizonUpload } from "./horizon-upload";
import { usePreferences } from "./i18n";
import { horizonContentLayout, horizonCopy, systemFace, useHorizonFonts } from "./ui";

// Approximate the sheet, independently of part B's internal asset layout.
const FILE_SHADOW = { inset: 26, down: 6, blur: 6, radius: 12 };

export function HorizonCard({ width, upload, fileName = "Project Brief.txt", fileSize = "97.23 KB", reduced }: {
  width: number; upload: ReturnType<typeof useHorizonUpload>; fileName?: string; fileSize?: string; reduced: boolean;
}) {
  const { t } = usePreferences();
  const { fx, phase, percent } = upload;
  const layout = horizonLayout(width);
  const fit = horizonContentLayout(width);
  const fontsLoaded = useHorizonFonts();
  const extension = (fileName.includes(".") ? fileName.slice(fileName.lastIndexOf(".") + 1) : "").slice(0, 4).toUpperCase();
  const { interaction, panelEnabled } = upload;
  const pan = useMemo(() => Gesture.Pan().minDistance(HORIZON_DRAG.threshold)
    .onBegin(() => { interaction.begin(); })
    .onStart(() => { interaction.start(); })
    .onUpdate((event) => { interaction.move(event.translationX, event.translationY); })
    .onEnd((event, success) => {
      if (success) interaction.release(event.translationX, event.translationY, event.velocityX, event.velocityY);
    })
    .onFinalize((_event, success) => { if (!success) interaction.finalize(); }), [interaction]);
  const panelTap = useMemo(() => Gesture.Tap().maxDistance(HORIZON_DRAG.threshold).maxDuration(60000)
    .onBegin(() => {
      interaction.touch();
      if (panelEnabled.get()) fx.press.set(withTiming(1, { duration: 90, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
    })
    .onEnd((_event, success) => { if (success) interaction.toggle(); })
    .onFinalize(() => {
      fx.press.set(reduced ? withTiming(0, { duration: 120, reduceMotion: ReduceMotion.Never }) : withSpring(0, { duration: 300, dampingRatio: 0.8 }));
    }), [interaction, panelEnabled, fx.press, reduced]);
  const content = useAnimatedStyle(() => ({
    transform: [{ translateX: fx.shiftX.get() }, { translateY: fx.shiftY.get() - fx.lift.get() * S.lift }, { scale: reduced ? 1 - 0.008 * fx.press.get() : cardScale(fx.crest.get(), fx.press.get()) }],
  }));
  const fileDepth = useAnimatedStyle(() => ({
    transform: [{ translateX: fx.shiftX.get() * S.fileParallax },
      { translateY: fx.shiftY.get() * S.fileParallax }, { scale: fit.fileScale }],
  }));
  const fileShadow = useAnimatedStyle(() => ({
    transform: [{ translateX: -fx.shiftX.get() * S.fileParallax },
      { translateY: -fx.shiftY.get() * S.fileParallax }],
  }));
  const previous = useRef(phase);
  useEffect(() => {
    if (phase !== previous.current && (phase === "done" || phase === "stopped")) {
      AccessibilityInfo.announceForAccessibility(phase === "done" ? t("Upload complete", "Tải lên hoàn tất") : t("Upload canceled", "Đã huỷ tải lên"));
    }
    previous.current = phase;
  }, [phase, t]);
  const action = phase === "idle" ? t("Start upload", "Bắt đầu tải lên")
    : phase === "uploading" ? t("Pause upload", "Tạm dừng tải lên")
    : phase === "paused" ? t("Resume upload", "Tiếp tục tải lên") : t("Upload again", "Tải lên lại");
  return (
    <GestureDetector gesture={pan}>
    <Animated.View collapsable={false} pointerEvents={fontsLoaded ? "auto" : "none"} accessibilityElementsHidden={!fontsLoaded} importantForAccessibility={fontsLoaded ? "auto" : "no-hide-descendants"} style={[styles.card, { width, opacity: fontsLoaded ? 1 : 0 }, content]}>
      <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
        style={[styles.fileShadow, { left: fit.file.x + FILE_SHADOW.inset * fit.fileScale,
          // It starts below the folded corner: a full-height shadow showed
          // as a dark block behind the cut corner of the sheet.
          top: fit.file.y + FILE_SHADOW.down + 34 * fit.fileScale, width: (S.fileWidth - FILE_SHADOW.inset - 6) * fit.fileScale, height: (S.fileHeight - 34) * fit.fileScale }, fileShadow]} />
      <Animated.View pointerEvents="none" style={[styles.absolute, { left: layout.file.x, top: layout.file.y,
        width: layout.file.width, height: layout.file.height, transformOrigin: "left center" }, fileDepth]}>
        <HorizonFile extension={extension} fx={fx} animated={!reduced} />
      </Animated.View>
      <View style={[styles.copy, { left: fit.copyLeft, width: fit.copyWidth }]}>
        <Text style={styles.name} numberOfLines={1} ellipsizeMode="middle" maxFontSizeMultiplier={1.3}>{fileName}</Text>
        <Text style={styles.size} numberOfLines={1} maxFontSizeMultiplier={1.3}>{fileSize}</Text>
      </View>
      <HorizonClose x={layout.close.x} y={layout.close.y} retry={upload.retry} enabled={upload.closeEnabled} fx={fx} form="glyph"
        reduced={reduced} onTouch={interaction.touch} onPress={interaction.close} />
      <GestureDetector gesture={panelTap}>
      <View collapsable={false} accessible style={[styles.absolute, { left: layout.panel.x, top: layout.panel.y, width: layout.panel.width, height: layout.panel.height }]}
        accessibilityRole="button" accessibilityLabel={action}
        accessibilityValue={{ min: 0, max: 100, now: percent }}
        onAccessibilityTap={() => scheduleOnUI(interaction.toggle)}
        accessibilityActions={[{ name: "activate", label: action }]}
        onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === "activate") scheduleOnUI(interaction.toggle); }}>
        {/* Mounted once the label's face has loaded, so it is never typeset in a fallback. */}
        {fontsLoaded ? <HorizonPanel width={width} phase={phase} percent={percent} fx={fx} reduced={reduced} displayPhase={upload.label} /> : null}
      </View>
      </GestureDetector>
    </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  card: { height: S.cardHeight },
  absolute: { position: "absolute" },
  fileShadow: { position: "absolute", borderRadius: FILE_SHADOW.radius, backgroundColor: "rgba(0,0,0,0.18)", filter: `blur(${FILE_SHADOW.blur}px)` },
  copy: { position: "absolute", left: S.copyLeft, top: S.copyTop, gap: 2 },
  name: { fontFamily: systemFace, fontWeight: "400", fontSize: 18, lineHeight: 24, letterSpacing: -0.4, color: horizonTone.ink },
  size: { fontFamily: systemFace, fontSize: 12, lineHeight: 16, fontVariant: ["tabular-nums"], color: horizonCopy.size },
});
