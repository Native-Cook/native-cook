import { useMemo } from "react";
import { View } from "react-native";
import { Canvas, Circle, Group, Path, Skia, type SkPath } from "@shopify/react-native-skia";
import { type SharedValue, useDerivedValue } from "react-native-reanimated";
import { ICON_GRID, POINTS_ICON, REWARD_ICONS } from "./referral-icons";
import { referralTone } from "./referral-palette";

// The rewards in the card's corner: three discs holding a gift, a ticket and a
// crown, one for each tier, and beside them the points coin. A disc fills
// white when its tier is reached; the coin swells as points arrive.

const D = 30;
const R = D / 2;
// The discs overlap by 2 pt, like links of a chain, which leaves each icon clear.
const STEP = 28;
const GAP = 10;
const PAD = 3;
// Layout follows the visible discs; the canvas keeps its overshoot outside it.
export const TIERS_WIDTH = STEP * 2 + D + GAP + D;
export const TIERS_HEIGHT = D;

/** The centre of tier `index`'s mark, from the cluster's top left. */
export function tierCentre(index: number) {
  return { x: R + STEP * index, y: R };
}

// An icon is drawn 16.8 pt wide in its 30 pt disc, with a 1.5 pt stroke.
const SCALE = 0.7;
const STROKE = 1.5 / SCALE;
const HALF = (ICON_GRID * SCALE) / 2;
// The sparkle is a small drawing; on the coin it is shown 13 pt wide.
const SPARK_SCALE = 1.3;
const SPARK_HALF = (ICON_GRID * SPARK_SCALE) / 2;

export function ReferralTiers({
  level,
  bump,
  label,
}: {
  // How many tiers are full; it springs, so a ring overshoots as it fills.
  level: SharedValue<number>;
  bump: SharedValue<number>;
  label: string;
}) {
  // Null only if the path data were malformed.
  const icons = useMemo(() => REWARD_ICONS.map((d) => Skia.Path.MakeFromSVGString(d)), []);
  const spark = useMemo(() => Skia.Path.MakeFromSVGString(POINTS_ICON), []);
  const token = useDerivedValue(() => [{ scale: 1 + 0.12 * bump.get() }]);
  const tokenX = PAD + STEP * 2 + D + GAP + R;

  return (
    <View
      accessible
      accessibilityRole="image"
      accessibilityLabel={label}
      style={{ width: TIERS_WIDTH, height: TIERS_HEIGHT }}
    >
      <Canvas style={{ position: "absolute", left: -PAD, top: -PAD, width: TIERS_WIDTH + PAD * 2, height: TIERS_HEIGHT + PAD * 2 }}>
        {/* Right to left, so a filled ring lies over the one after it. */}
        {[2, 1, 0].map((i) => (
          <Tier key={i} index={i} level={level} path={icons[i]} x={PAD + R + STEP * i} />
        ))}
        <Group transform={token} origin={{ x: tokenX, y: PAD + R }}>
          <Circle cx={tokenX} cy={PAD + R} r={R} color={referralTone.ink} />
          {spark && (
            <Group transform={[{ translateX: tokenX - SPARK_HALF }, { translateY: PAD + R - SPARK_HALF }, { scale: SPARK_SCALE }]}>
              <Path path={spark} color={referralTone.night} />
            </Group>
          )}
        </Group>
      </Canvas>
    </View>
  );
}

function Tier({ index, level, path, x }: { index: number; level: SharedValue<number>; path: SkPath | null; x: number }) {
  const y = PAD + R;
  // 0 empty, 1 full; above 1 while the spring overshoots.
  const fill = useDerivedValue(() => Math.max(level.get() - index, 0));
  const disc = useDerivedValue(() => Math.min(fill.get() * 1.4, 1));
  const scale = useDerivedValue(() => {
    const f = Math.min(fill.get(), 1.2);
    return [{ scale: f <= 0 ? 1 : 0.84 + 0.16 * f }];
  });
  const outline = useDerivedValue(() => 1 - Math.min(fill.get() * 2, 1));
  const ink = useDerivedValue(() => (fill.get() > 0.5 ? referralTone.night : referralTone.sub));

  return (
    <Group transform={scale} origin={{ x, y }}>
      {/* A cut through whatever is already drawn keeps a filled disc apart
          from its neighbor, and lets the glass show in the gap. */}
      <Circle cx={x} cy={y} r={R + 1.5} blendMode="dstOut" opacity={disc} />
      <Circle cx={x} cy={y} r={R} color={referralTone.ink} opacity={disc} />
      <Circle cx={x} cy={y} r={R - 0.75} style="stroke" strokeWidth={1.5} color={referralTone.ringTrack} opacity={outline} />
      {path && (
        // The gift's box and the crown's band carry more ink below the centre.
        <Group transform={[{ translateX: x - HALF }, { translateY: y - HALF - (index === 1 ? 0 : 1) }, { scale: SCALE }]}>
          <Path path={path} style="stroke" strokeWidth={STROKE} strokeCap="round" strokeJoin="round" color={ink} />
        </Group>
      )}
    </Group>
  );
}
