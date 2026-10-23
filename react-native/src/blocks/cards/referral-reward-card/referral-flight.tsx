import { StyleSheet } from "react-native";
import { BlurMask, Canvas, Circle, Path, Skia } from "@shopify/react-native-skia";
import { type DerivedValue, type SharedValue, useDerivedValue } from "react-native-reanimated";
import { TRAIL_END, comet } from "./referral-comet";
import { referralAt, type ReferralPath, type Point } from "./referral-path";
import { BUTTON_DOT } from "./ui";
import { easeInOut, easeOut, referralTone } from "./referral-palette";

// Both comets share the canvas over the card. Between flights their paths
// are empty and their clocks are at rest.

export function ReferralFlight({
  sling,
  cometClock,
  buttonShape,
  capsuleShape,
  fromCapsule,
  from,
  to,
  clock,
  tint,
}: {
  sling: SharedValue<ReferralPath | null>;
  cometClock: SharedValue<number>;
  buttonShape: SharedValue<number>;
  capsuleShape: SharedValue<number>;
  fromCapsule: SharedValue<boolean>;
  from: Point;
  to: Point;
  // 0 to TRAIL_END; rests at TRAIL_END.
  clock: SharedValue<number>;
  tint: DerivedValue<number[]>;
}) {
  const slingTrail = useDerivedValue(() => {
    const p = Skia.PathBuilder.Make();
    const t = cometClock.get();
    const path = sling.get();
    if (!path || t >= TRAIL_END) return p.build();
    if (t < 0) {
      // The waiting dot already belongs to this canvas, so at launch it
      // moves away as the head without crossing between two renderers.
      if ((fromCapsule.get() ? capsuleShape.get() : buttonShape.get()) >= 1) {
        const origin = path.samples[0];
        p.addCircle(origin.x, origin.y, BUTTON_DOT / 2);
      }
      return p.build();
    }
    const at = (u: number) => {
      "worklet";
      return referralAt(path, u);
    };
    comet(p, t, at, 0, 0, BUTTON_DOT / 2);
    return p.build();
  });
  // It leaves straight up and bends over into the mark.
  const trail = useDerivedValue(() => {
    const p = Skia.PathBuilder.Make();
    const t = clock.get();
    if (t <= 0 || t >= TRAIL_END) return p.build();
    const path = (u: number) => {
      "worklet";
      const w = easeInOut(u);
      const m = 1 - w;
      return {
        x: m * m * from.x + 2 * m * w * from.x + w * w * to.x,
        y: m * m * from.y + 2 * m * w * (to.y + 18) + w * w * to.y,
      };
    };
    comet(p, t, path, 0, 0);
    return p.build();
  });
  // A ring opens round the mark as the head arrives.
  const landed = useDerivedValue(() => Math.min(Math.max((clock.get() - 1) / (TRAIL_END - 1), 0), 1));
  const ringR = useDerivedValue(() => 15 + 13 * easeOut(landed.get()));
  const ringOpacity = useDerivedValue(() => {
    const q = landed.get();
    return q <= 0 || q >= 1 ? 0 : 0.55 * Math.pow(1 - q, 1.4);
  });

  return (
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Path path={slingTrail} color={tint}>
        <BlurMask blur={5} style="normal" />
      </Path>
      <Path path={slingTrail} color={referralTone.ring} />
      <Circle cx={to.x} cy={to.y} r={ringR} style="stroke" strokeWidth={1.25} color={referralTone.ring} opacity={ringOpacity} />
      <Path path={trail} color={tint}>
        <BlurMask blur={5} style="normal" />
      </Path>
      <Path path={trail} color={referralTone.ring} />
    </Canvas>
  );
}
