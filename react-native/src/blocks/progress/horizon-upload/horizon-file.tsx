import { useEffect } from "react";
import { AccessibilityInfo, Platform, StyleSheet, Text, View } from "react-native";
import { Canvas, Fill, ImageShader, Shader, Skia, useImage } from "@shopify/react-native-skia";
import Animated, {
  cancelAnimation, Easing, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withTiming,
} from "react-native-reanimated";
import { horizonSize } from "./horizon-palette";
import { HERO, part, type HorizonFx } from "./horizon-state";

// These 4x assets include eight points of transparent padding on every side.
// Geometry describes the visible bodies. The sheet has no ground shadow;
// the badge padding holds its contact shadow, and the pane owns the file shadow.
export const FILE_ASSET_SCALE = 4;
export const FILE_ASSET_PADDING = 8;
export const FILE_SHEET = { width: 90, height: 112, left: 26 } as const;
export const FILE_BADGE = { width: 82, height: 52, top: 41.2 } as const;
const SHEET_CANVAS = { x: 0, y: 0, width: FILE_SHEET.width + 2 * FILE_ASSET_PADDING, height: FILE_SHEET.height + 2 * FILE_ASSET_PADDING };
const BADGE_CANVAS = { x: 0, y: 0, width: FILE_BADGE.width + 2 * FILE_ASSET_PADDING, height: FILE_BADGE.height + 2 * FILE_ASSET_PADDING };
const FLOAT_PERIOD = 3200;
const PERSPECTIVE = 420;

// The images own the material. This shader only relights their premultiplied
// pixels and adds the shared sweep. Prefix identifiers to avoid Dawn/WGSL
// builtin collisions, which CanvasKit's SkSL compiler cannot detect.
const HORIZON_FILE_MATERIAL = `
uniform shader fAsset;
uniform float fPower;
uniform float fPulse;
uniform float fOpaque;
uniform float fGlint;
uniform float fSheet;

float3 fLit(float3 fColor) {
  float fK = sqrt(clamp((fPower - 0.06) / 0.94, 0.0, 1.0));
  float fGrey = dot(fColor, float3(0.2126, 0.7152, 0.0722));
  return mix(float3(fGrey * 0.87, fGrey * 0.94, fGrey * 1.12), fColor, 0.50 + 0.50 * fK)
    * (0.60 + 0.40 * fK);
}
half4 main(float2 fXY) {
  half4 fPixel = fAsset.eval(fXY);
  float fAlpha = fPixel.a;
  if (fAlpha < 0.001) return half4(0.0);
  float3 fColor = fPixel.rgb / fAlpha;
  // Clear glass still reflects the sweep. Its silhouette has no baked shadow,
  // so low-alpha sheet pixels are material, unlike the badge contact shadow.
  float fBody = fSheet > 0.5 ? smoothstep(0.015, 0.20, fAlpha) : smoothstep(0.3, 0.7, fAlpha);
  if (fSheet > 0.5) {
    float2 fPoint = fXY - float2(8.0);
    float fBand = fPoint.y + 0.42 * (fPoint.x - 45.0) - mix(151.0, -40.0, fGlint);
    float fGate = smoothstep(0.0, 0.10, fGlint) * (1.0 - smoothstep(0.90, 1.0, fGlint));
    float fShine = fGate * (0.23 * exp(-fBand * fBand / 80.0) + 0.25 * exp(-fBand * fBand / 5.0));
    float fRim = min(min(fPoint.x, 90.0 - fPoint.x), min(fPoint.y, 112.0 - fPoint.y));
    fColor *= 1.0 + 0.06 * fPulse * exp(-max(fRim, 0.0) / 5.0) * fBody;
    fColor += float3(0.72, 0.87, 1.0) * fShine * fBody;
  }
  // Flatten the transparent material onto the pane when transparency is
  // reduced, keeping the image's anti-aliased silhouette and soft shadow.
  if (fOpaque > 0.5) {
    fColor = mix(fColor, fColor * fAlpha + float3(13.0, 20.0, 38.0) / 255.0 * (1.0 - fAlpha), fBody);
    fAlpha = mix(fAlpha, 1.0, fBody);
  }
  return half4(clamp(fLit(fColor), 0.0, 1.0) * fAlpha, fAlpha);
}
`;
const MATERIAL = Skia.RuntimeEffect.Make(HORIZON_FILE_MATERIAL);
if (!MATERIAL) throw new Error("Horizon Upload: file lighting did not compile.");

// Only the quiet float has a local clock. A restart never has to cancel an
// independent celebration animation: the owner's signals drive every cue.
export function HorizonFile({ extension = "TXT", fx, animated }: {
  /** The extension on the badge, at most four characters, shown in capitals. */
  extension: string;
  fx: HorizonFx;
  /** False under Reduce Motion: the badge crossfades instead of turning over. */
  animated: boolean;
}) {
  const phase = useSharedValue(0);
  const opaque = useSharedValue(0);
  const sheetImage = useImage(require("./assets/file-sheet.png"));
  const badgeImage = useImage(require("./assets/file-badge.png"));
  const doneImage = useImage(require("./assets/file-badge-done.png"));

  useEffect(() => {
    if (!animated) return;
    phase.set(0);
    phase.set(withRepeat(withTiming(1, { duration: FLOAT_PERIOD, easing: Easing.linear }), -1, false));
    return () => cancelAnimation(phase);
  }, [animated, phase]);

  useEffect(() => {
    if (Platform.OS !== "ios") return;
    let active = true;
    let changed = false;
    const subscription = AccessibilityInfo.addEventListener("reduceTransparencyChanged", (enabled) => {
      changed = true;
      opaque.set(enabled ? 1 : 0);
    });
    void AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => {
      if (active && !changed) opaque.set(enabled ? 1 : 0);
    }).catch(() => {});
    return () => { active = false; subscription.remove(); };
  }, [opaque]);

  const turn = useDerivedValue(() => {
    const crest = fx.crest.get();
    const done = Math.min(1, Math.max(0, fx.done.get()));
    // During the ceremony, crest owns the turn. At either resting endpoint,
    // done owns it, including the owner's 300 ms fade when restarting.
    if (!animated || crest <= 0 || crest >= 1) return done;
    const t = part(crest, HERO.flip[0], HERO.flip[1]);
    return t * t * (3 - 2 * t);
  });
  const movement = useDerivedValue(() => {
    if (!animated) return { sheet: 0, badge: 0 };
    const amplitude = part(fx.power.get(), 0.06, 1);
    const angle = phase.get() * Math.PI * 2;
    const crest = fx.crest.get();
    const rise = part(crest, HERO.flip[0], (HERO.flip[0] + HERO.flip[1]) / 2);
    const settle = part(crest, HERO.flip[1], HERO.settle[1]);
    const lift = -4 * Math.sin(rise * Math.PI / 2) * (1 - settle) ** 2;
    return {
      sheet: 2 * amplitude * Math.sin(angle) + lift,
      badge: amplitude * Math.sin(angle - Math.PI / 2),
    };
  });
  const sheetUniforms = useDerivedValue(() => {
    // This portion of the shared sweep crosses the file in the upper card.
    const sweep = part(fx.crest.get(), HERO.sweep[0], HERO.sweep[1]);
    return {
      fPower: fx.power.get(), fPulse: fx.pulse.get(), fOpaque: opaque.get(), fSheet: 1,
      fGlint: animated ? part(sweep, (0.45 - HERO.sweep[0]) / (HERO.sweep[1] - HERO.sweep[0]),
        (0.62 - HERO.sweep[0]) / (HERO.sweep[1] - HERO.sweep[0])) : 0,
    };
  });
  const frontUniforms = useDerivedValue(() => ({
    fPower: fx.power.get(), fPulse: 0, fGlint: 0, fOpaque: opaque.get(), fSheet: 0,
  }));
  const floatStyle = useAnimatedStyle(() => ({ transform: [{ translateY: movement.get().sheet }] }));
  const badgeStyle = useAnimatedStyle(() => ({ transform: [{ translateY: movement.get().badge }] }));
  const frontStyle = useAnimatedStyle(() => ({
    opacity: animated ? (turn.get() < 0.5 ? 1 : 0) : 1 - turn.get(),
    transform: [{ perspective: PERSPECTIVE }, { rotateX: `${animated ? turn.get() * 180 : 0}deg` }],
  }));
  const backStyle = useAnimatedStyle(() => ({
    opacity: animated ? (turn.get() >= 0.5 ? 1 : 0) : turn.get(),
    transform: [{ perspective: PERSPECTIVE }, { rotateX: `${animated ? (turn.get() - 1) * 180 : 0}deg` }],
  }));
  const stampStyle = useAnimatedStyle(() => ({ opacity: 0.72 + 0.28 * part(fx.power.get(), 0.06, 1) }));
  const label = extension.trim().toUpperCase().slice(0, 4) || "TXT";

  // Keep the allocated box but reveal the three layers together. In particular,
  // the text never flashes on its own while native image decoding is pending.
  if (!sheetImage || !badgeImage || !doneImage) {
    return <View pointerEvents="none" accessible={false} accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants" style={styles.root} />;
  }

  return (
    <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants" style={[styles.root, floatStyle]}>
      <Canvas pointerEvents="none" style={styles.sheetCanvas}>
        <Fill><Shader source={MATERIAL!} uniforms={sheetUniforms}>
          <ImageShader image={sheetImage} fit="fill" rect={SHEET_CANVAS} tx="decal" ty="decal" />
        </Shader></Fill>
      </Canvas>
      <Animated.View style={[styles.badge, badgeStyle]}>
        <Animated.View style={[styles.face, frontStyle]}>
          <Canvas pointerEvents="none" style={styles.badgeCanvas}>
            <Fill><Shader source={MATERIAL!} uniforms={frontUniforms}>
              <ImageShader image={badgeImage} fit="fill" rect={BADGE_CANVAS} tx="decal" ty="decal" />
            </Shader></Fill>
          </Canvas>
          <Animated.View style={[styles.stamp, stampStyle]}>
            <View style={styles.engraving}>
              <Text allowFontScaling={false} adjustsFontSizeToFit minimumFontScale={0.7} numberOfLines={1} style={[styles.letters, styles.letterEdge]}>{label}</Text>
              <Text allowFontScaling={false} adjustsFontSizeToFit minimumFontScale={0.7} numberOfLines={1} style={styles.letters}>{label}</Text>
            </View>
          </Animated.View>
        </Animated.View>
        <Animated.View style={[styles.face, backStyle]}>
          <Canvas pointerEvents="none" style={styles.badgeCanvas}>
            <Fill><Shader source={MATERIAL!} uniforms={frontUniforms}>
              <ImageShader image={doneImage} fit="fill" rect={BADGE_CANVAS} tx="decal" ty="decal" />
            </Shader></Fill>
          </Canvas>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { width: horizonSize.fileWidth, height: horizonSize.fileHeight, overflow: "visible" },
  sheetCanvas: {
    position: "absolute", left: FILE_SHEET.left - FILE_ASSET_PADDING, top: -FILE_ASSET_PADDING,
    width: SHEET_CANVAS.width, height: SHEET_CANVAS.height,
  },
  badge: { position: "absolute", left: 0, top: FILE_BADGE.top, width: FILE_BADGE.width, height: FILE_BADGE.height },
  face: { ...StyleSheet.absoluteFill, backfaceVisibility: "hidden" },
  badgeCanvas: {
    position: "absolute", left: -FILE_ASSET_PADDING, top: -FILE_ASSET_PADDING,
    width: BADGE_CANVAS.width, height: BADGE_CANVAS.height,
  },
  stamp: { ...StyleSheet.absoluteFill, alignItems: "center", justifyContent: "center" },
  engraving: { width: 64, height: 26 },
  letters: {
    ...StyleSheet.absoluteFill, fontFamily: Platform.OS === "ios" ? "System" : "sans-serif",
    fontSize: 20, lineHeight: 26, fontWeight: "800", letterSpacing: 0.2,
    color: "#1B2647", textAlign: "center", includeFontPadding: false,
  },
  letterEdge: { transform: [{ translateY: 0.5 }], color: "rgba(206, 224, 255, 0.48)" },
});
