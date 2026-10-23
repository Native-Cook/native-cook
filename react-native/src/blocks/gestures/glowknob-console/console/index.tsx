import { useEffect, useMemo, useState } from "react";
import { View, useWindowDimensions } from "react-native";
import { useIsFocused } from "../navigation";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
} from "react-native-reanimated";
import { Txt, useMotionPrefs } from "../ui";
import { usePreferences } from "../i18n";
import { screenSpacing } from "../theme";
import { TactileConsole } from "./tactile-console";
import { specimen, type Tilt } from "./device-art";
import { useTactileControl } from "./use-tactile-control";
import { allEffects, type Effects } from "./effects";
import { StageAmbient, StageFloor } from "./stage";
import { TiltSource } from "./tilt";
import { useIncreaseContrast } from "./use-increase-contrast";
import { referenceGeometry as g } from "./reference-motion";
import { LiftLayer, useConsoleLift } from "./console-lift";
import { tactile } from "./tactile-theme";
import type { Box } from "./lift-scene";

// The specimen keeps phone proportions on tablets: past this width it would
// only grow, not gain detail.
const MAX_WIDTH = 520;
// The renderer is built once the power-on has played, off its critical path.
const WARM_UP = 1400;
// Height of the lift layer around the housing, in housing heights.
const LIFT_ROOM = 3.4;

// The page is the specimen alone: the tactile Volume / Brightness console
// standing on a white glass stage.
export function Showcase() {
  // Increase Contrast keeps the object and its states but drops decorative
  // light: spill, glass, sheen, reflection and the page ambience.
  const highContrast = useIncreaseContrast();
  const fx = useMemo<Effects>(
    () =>
      highContrast
        ? {
            ...allEffects,
            spill: false,
            glass: false,
            sheen: false,
            reflection: false,
            ambient: false,
          }
        : allEffects,
    [highContrast],
  );
  const control = useTactileControl(false, false, fx);
  const tilt = useSharedValue<Tilt>({ x: 0, y: 0 });
  const focused = useIsFocused();
  const prefs = useMotionPrefs();
  const { t } = usePreferences();
  const insets = useSafeAreaInsets();
  const { width: screen, fontScale } = useWindowDimensions();
  const [width, setWidth] = useState(Math.min(MAX_WIDTH, screen - 40));
  const [root, setRoot] = useState({ width: 0, height: 0 });
  const [origin, setOrigin] = useState<{ x: number; y: number } | null>(null);
  const [warm, setWarm] = useState(false);
  const scale = width / g.width;
  const largeText = fontScale >= 1.35;
  const liftable = fx.lift && !control.reduced && !largeText;
  const lift = useConsoleLift({
    control,
    fx,
    tilt,
    enabled: liftable && warm,
    haptics: prefs.haptics,
  });

  useEffect(() => {
    if (!liftable || !focused) return;
    const id = setTimeout(() => setWarm(true), WARM_UP);
    return () => clearTimeout(id);
  }, [liftable, focused]);

  // The lift layer spans the page's width and LIFT_ROOM housing heights
  // around the housing, so the vanishing point sits on the object.
  const layer = useMemo(() => {
    if (!origin || root.width === 0) return null;
    const housing = {
      x: origin.x,
      y: origin.y + specimen.top * scale,
      width: g.width * scale,
      height: g.height * scale,
    };
    const height = Math.min(root.height, housing.height * LIFT_ROOM);
    const top = housing.y + housing.height / 2 - height / 2;
    const frame: Box = { x: 0, y: top, width: root.width, height };
    return {
      frame,
      housing: { ...housing, y: housing.y - top } as Box,
    };
  }, [origin, root.width, root.height, scale]);

  // The page as seen: white, dimmed by the brightness ambience.
  const { brightness } = control;
  const dimmed = fx.ambient && !control.reduced;
  const pageTone = useDerivedValue(() =>
    dimmed ? 1 - (1 - Math.max(0, Math.min(1, brightness.get()))) * 0.1 : 1,
  );
  // Worklets capture this shared value alone, never the whole lift object.
  const raised = lift.lift;
  const floorStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(1, raised.get() * 1.6),
  }));
  const hintStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(1, raised.get() * 2),
  }));

  return (
    <View
      style={{ flex: 1 }}
      onLayout={(e) =>
        setRoot({
          width: e.nativeEvent.layout.width,
          height: e.nativeEvent.layout.height,
        })
      }
    >
      <StageAmbient control={control} fx={fx} />
      <View
        style={{
          flex: 1,
          justifyContent: "center",
          paddingHorizontal: screenSpacing(screen).edge,
        }}
      >
        <View
          style={{ width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center" }}
          onLayout={(e) => {
            const { x, y, width: w } = e.nativeEvent.layout;
            setWidth(w);
            setOrigin({ x, y });
          }}
        >
          {fontScale < 1.35 && (fx.spill || fx.reflection) && (
            <Animated.View style={floorStyle} pointerEvents="none">
              <StageFloor control={control} fx={fx} tilt={tilt} scale={scale} />
            </Animated.View>
          )}
          <TactileConsole
            control={control}
            fx={fx}
            tilt={tilt}
            highContrast={highContrast}
            pickUp={liftable ? lift.pickUp : undefined}
            standIn={lift.covered}
          />
        </View>
      </View>
      {liftable && layer && (
        <LiftLayer
          lift={lift}
          frame={layer.frame}
          housing={layer.housing}
          active={warm && focused}
          glow={fx.liftLight}
          fog={pageTone}
        />
      )}
      {liftable && (
        <Animated.View
          pointerEvents="none"
          style={[
            {
              position: "absolute",
              left: 0,
              right: 0,
              bottom: insets.bottom + 28,
              alignItems: "center",
            },
            lift.lifted ? null : hintStyle,
          ]}
        >
          <Txt variant="caption" style={{ color: tactile.hint }}>
            {lift.lifted
              ? t(
                  "Drag to turn it over · Tap to set it down",
                  "Kéo để lật · Chạm để đặt xuống",
                )
              : lift.used
                ? " "
                : t(
                    "Hold the console to pick it up",
                    "Giữ console để nhấc lên",
                  )}
          </Txt>
        </Animated.View>
      )}
      {fx.tilt && focused && !control.reduced && <TiltSource tilt={tilt} />}
    </View>
  );
}
