import type { ReactNode } from "react";
import { BlurMask, Group, LinearGradient, Path, Skia, vec } from "@shopify/react-native-skia";
import { useDerivedValue } from "react-native-reanimated";
import { beat } from "./ember-motion";
import type { EmberMotion } from "./ember-values";
import { E, emberHeart, emberHeartKeyAngle, emberHeartTone as H } from "./ember-theme";

// One retained outline. Local clips bound the constant-radius filters; no
// offscreen group layers or path allocation in the heartbeat worklet.
const HEART = Skia.Path.MakeFromSVGString(emberHeart)!;
const ORIGIN = [{ translateX: E.bleed + E.heart.x }, { translateY: E.bleed + E.heart.y }];
const CENTRE = vec(28, 26);
const BOUNDS = { x: -7, y: -7, width: 70, height: 70 };
const KEY = vec(28 + Math.cos(emberHeartKeyAngle) * 35, 26 + Math.sin(emberHeartKeyAngle) * 35);
const KEY_END = vec(56 - KEY.x, 52 - KEY.y);
// Concentric with the circular lobe, 3 pt inset, 45 degrees around the key.
const ARC_START = emberHeartKeyAngle - Math.PI / 8;
const ARC_END = emberHeartKeyAngle + Math.PI / 8;
const CRESCENT = Skia.Path.MakeFromSVGString(
  `M${14 + 10.75 * Math.cos(ARC_START)} ${16.75 + 10.75 * Math.sin(ARC_START)} A10.75 10.75 0 0 1 ${14 + 10.75 * Math.cos(ARC_END)} ${16.75 + 10.75 * Math.sin(ARC_END)}`,
)!;
const CAUSTIC = Skia.Path.MakeFromSVGString("M20 53 A8 2 0 1 0 36 53 A8 2 0 1 0 20 53 Z")!;
const CANVAS_CENTRE = vec(E.bleed + E.heart.x + 28, E.bleed + E.heart.y + 26);
const OCCLUSION = Skia.PathBuilder.MakeFromPath(HEART).offset(E.bleed + E.heart.x, E.bleed + E.heart.y).detach();

// The lens occludes airborne light before it can wash over the native ink.
// Scale the clip with the lens, then undo that transform for the effects.
// These are canvas clips/transforms, not offscreen layers or rebuilt paths.
export function EmberHeartOcclusion({ phase, reduced, children }: Pick<EmberMotion, "phase" | "reduced"> & { children: ReactNode }) {
  const scale = useDerivedValue(() => reduced ? 1 : beat(phase.get()));
  const pulse = useDerivedValue(() => [{ scale: scale.get() }]);
  const undo = useDerivedValue(() => [{ scale: 1 / scale.get() }]);
  return (
    <Group transform={pulse} origin={CANVAS_CENTRE} clip={OCCLUSION} invertClip>
      <Group transform={undo} origin={CANVAS_CENTRE}>{children}</Group>
    </Group>
  );
}

export function EmberHeart({ phase, glow, quality, reduced }: Pick<EmberMotion, "phase" | "glow" | "quality" | "reduced">) {
  const envelope = useDerivedValue(() => reduced ? 0 : Math.max(0, Math.min(1, (beat(phase.get()) - 1) / 0.12)));
  const pulse = useDerivedValue(() => [{ scale: 1 + 0.12 * envelope.get() }]);
  const rim = useDerivedValue(() => 0.55 + 0.35 * envelope.get());
  const caustic = useDerivedValue(() => 0.16 + 0.22 * envelope.get());
  // Low keeps the state-driven pool, without decorative beat modulation.
  const poolBeat = useDerivedValue(() => quality === "low" ? 0 : envelope.get());
  const poolStart = useDerivedValue(() => vec(28, 37 - 3 * glow.get() - 4 * poolBeat.get()));
  const poolOpacity = useDerivedValue(() => 0.78 + 0.22 * poolBeat.get());

  return (
    <Group transform={ORIGIN} clip={BOUNDS}>
      {/* Contact stays on the floor while the vessel breathes above it. */}
      {quality !== "low" && (
        <Group>
          <Path path={HEART} transform={[{ translateY: 2 }]} color={H.contactShadow} opacity={0.18}>
            <BlurMask blur={5} style="normal" />
          </Path>
          <Path path={CAUSTIC} color={H.contactCaustic} opacity={caustic}>
            <BlurMask blur={5} style="normal" />
          </Path>
        </Group>
      )}
      <Group transform={pulse} origin={CENTRE}>
        <Path path={HEART} opacity={0.86}>
          <LinearGradient start={vec(28, 3)} end={vec(28, 50)}
            colors={[H.bodyTop, H.bodyMiddle, H.bodyTip]} positions={[0, 0.55, 1]} />
        </Path>
        <Group clip={HEART}>
          <Path path={HEART} opacity={poolOpacity}>
            <LinearGradient start={poolStart} end={vec(28, 50)}
              colors={[H.poolSurface, H.poolMiddle, H.poolTip]} positions={[0, 0.55, 1]} />
          </Path>
          {/* Clipping a 3 pt stroke leaves a 1.5 pt inner wall. */}
          <Path path={HEART} style="stroke" strokeWidth={3}>
            <LinearGradient start={vec(28, 3)} end={vec(28, 38)} colors={[H.depthTop, H.depthClear]} />
          </Path>
          {/* Separate lights: coals below, faint key only on the left lobe. */}
          <Path path={HEART} style="stroke" strokeWidth={2} opacity={rim}>
            <LinearGradient start={vec(28, 22)} end={vec(28, 50)} colors={[H.rimEmberClear, H.rimEmber]} />
          </Path>
          <Group clip={{ x: 0, y: 0, width: 28, height: 22 }}>
            <Path path={HEART} style="stroke" strokeWidth={2} opacity={0.30}>
              <LinearGradient start={KEY} end={KEY_END} colors={[H.rimKey, H.rimKeyClear]} positions={[0, 0.18]} />
            </Path>
          </Group>
          <Path path={CRESCENT} style="stroke" strokeWidth={1.5} strokeCap="round" color={H.specularCrescent} opacity={0.5} />
        </Group>
      </Group>
    </Group>
  );
}
