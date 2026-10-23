import { useCallback, useEffect, useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import * as Haptics from "expo-haptics";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import {
  Canvas,
  Group,
  Image,
  Shadow,
  LinearGradient,
  Rect,
  Skia,
  drawAsPicture,
  interpolateColors,
  vec,
} from "@shopify/react-native-skia";
import {
  useOnDemandThreeScene,
  type OnDemandSceneFactory,
} from "../gpu/use-on-demand-three-scene";
import { DeviceArt, type Tilt } from "./device-art";
import type { Effects } from "./effects";
import { LiftLink, makeLiftScene, type Box, type Glow } from "./lift-scene";
import { tactile } from "./tactile-theme";
import { faceUpYaw, restingHalfTurn } from "./motion-logic";
import { referenceGeometry as g, rubberBand } from "./reference-motion";
import type { TactileControl } from "./use-tactile-control";
import { DEPTH, GAP, SQUASH, VISIBLE, VISIBLE_AT } from "./stage";

// Hold this long on the housing to pick the console up.
const HOLD = 320;
// A half turn per 260dp of horizontal drag; pitch follows vertical drag.
const YAW_PER_DP = Math.PI / 260;
const PITCH_PER_DP = 0.006;
const PITCH_MAX = 0.75;
const RAISE = { stiffness: 210, damping: 17, mass: 1 };
const SETTLE = { stiffness: 150, damping: 19, mass: 1 };
// Frames (ms) both drawings are up at a handoff: the 3D frame showing the
// new state reaches the screen a frame or two after the shared value flips.
const OVERLAP = 70;
const LOWER = {
  stiffness: 240,
  damping: 26,
  mass: 1,
  overshootClamping: true,
};

// The light as it is when picked up (read once, on the JS thread).
function glowOf(control: TactileControl): Glow {
  const fill = control.fill.get();
  const c = Skia.Color(
    interpolateColors(control.mix.get(), tactile.transferAt, [
      ...tactile.transfer[1],
    ]),
  );
  // sRGB → linear, the scene's working space.
  const lin = (v: number) =>
    v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  return {
    tip: g.track.x + g.track.width * fill,
    color: [lin(c[0]), lin(c[1]), lin(c[2])],
    power: control.lightAlpha.get() * Math.min(1, 0.25 + fill),
  };
}

// Stages, mirrored on the UI thread.
const REST = 0,
  PICKING = 1,
  HELD = 2,
  LOWERING = 3;

/**
 * Pick-up choreography of the console. Holding the housing records the live
 * artwork (DeviceArt) into a Skia picture, paints it into a texture three.js
 * samples with no copy, and swaps the 2D drawing for the 3D object at the
 * same pixels; the object then rises toward the viewer and follows the
 * finger. A flick turns it over to its back; a tap sets it down, face up.
 */
export function useConsoleLift({
  control,
  fx,
  tilt,
  enabled,
  haptics,
}: {
  control: TactileControl;
  fx: Effects;
  tilt: SharedValue<Tilt>;
  enabled: boolean;
  haptics: boolean;
}) {
  const lift = useSharedValue(0),
    yaw = useSharedValue(0),
    pitch = useSharedValue(0),
    stage = useSharedValue(REST),
    // 1 while the 3D object is drawn (it sits above the 2D drawing)...
    shown = useSharedValue(0),
    // ...and 1 while the 2D drawing under it is hidden. The two overlap for a
    // few frames at each handoff, so neither direction shows a gap.
    covered = useSharedValue(0),
    // Changes after each three render, so the Skia canvas redraws with it.
    tick = useSharedValue(0),
    grab = useSharedValue({ yaw: 0, pitch: 0 });
  const [lifted, setLifted] = useState(false);
  const [used, setUsed] = useState(false);
  const [link] = useState(() => new LiftLink());
  const receive = useCallback((pose: number[]) => link.setPose(pose), [link]);
  // Pushes the pose to the scene each UI frame it changes while the object
  // is up (and once more as it comes to rest).
  useAnimatedReaction(
    () => ({
      pose: [lift.get(), yaw.get(), pitch.get(), tilt.get().x, tilt.get().y],
      stage: stage.get(),
    }),
    (now, before) => {
      if (now.stage !== REST || (before && before.stage !== REST))
        scheduleOnRN(receive, now.pose);
    },
  );
  const still = useSharedValue<Tilt>({ x: 0, y: 0 });

  const buzz = useCallback(
    (style: Haptics.ImpactFeedbackStyle) => {
      if (haptics) void Haptics.impactAsync(style).catch(() => {});
    },
    [haptics],
  );
  const tickFace = useCallback(() => {
    if (haptics) void Haptics.selectionAsync().catch(() => {});
  }, [haptics]);

  useEffect(() => {
    let frames = 0;
    link.on({
      render: () => tick.set(++frames % 2),
      face: () => {
        shown.set(1);
        covered.set(withDelay(OVERLAP, withTiming(1, { duration: 0 })));
        stage.set(HELD);
        lift.set(withSpring(1, RAISE));
      },
    });
  }, [covered, link, lift, shown, stage, tick]);

  const begin = useCallback(async () => {
    if (!link.ready) {
      stage.set(REST);
      return;
    }
    buzz(Haptics.ImpactFeedbackStyle.Medium);
    setLifted(true);
    setUsed(true);
    try {
      const picture = await drawAsPicture(
        <DeviceArt control={control} fx={fx} tilt={still} baked />,
        Skia.XYWHRect(0, 0, 1000, 284),
      );
      link.start(picture, fx.liftLight ? glowOf(control) : null);
    } catch {
      stage.set(REST);
      setLifted(false);
    }
  }, [buzz, control, fx, link, stage, still]);

  const landed = useCallback(() => {
    link.stop();
    setLifted(false);
    buzz(Haptics.ImpactFeedbackStyle.Soft);
  }, [buzz, link]);

  const gestures = useMemo(() => {
    const follow = (x: number, y: number) => {
      "worklet";
      const start = grab.get();
      yaw.set(start.yaw + x * YAW_PER_DP);
      const p = start.pitch + y * PITCH_PER_DP;
      const over = Math.abs(p) - PITCH_MAX;
      pitch.set(
        over > 0 ? Math.sign(p) * (PITCH_MAX + rubberBand(over, 0.25)) : p,
      );
    };
    // Release: rest on whichever face the flick carries it to.
    const release = (vx: number, vy: number) => {
      "worklet";
      const v = vx * YAW_PER_DP;
      const from = restingHalfTurn(yaw.get(), 0);
      const to = restingHalfTurn(yaw.get(), v);
      yaw.set(
        withSpring(to * Math.PI, { ...SETTLE, velocity: v }, (done) => {
          if (done && to !== from) scheduleOnRN(tickFace);
        }),
      );
      pitch.set(withSpring(0, { ...SETTLE, velocity: vy * PITCH_PER_DP }));
    };
    const hold = () => {
      "worklet";
      cancelAnimation(yaw);
      cancelAnimation(pitch);
      grab.set({ yaw: yaw.get(), pitch: pitch.get() });
    };
    const moving = () => {
      "worklet";
      const s = stage.get();
      return s === PICKING || s === HELD;
    };

    const pickUp = Gesture.Pan()
      .enabled(enabled)
      .activateAfterLongPress(HOLD)
      .onStart(() => {
        if (stage.get() !== REST) return;
        stage.set(PICKING);
        hold();
        scheduleOnRN(begin);
      })
      .onUpdate((e) => {
        if (moving()) follow(e.translationX, e.translationY);
      })
      .onEnd((e) => {
        if (moving()) release(e.velocityX, e.velocityY);
      });

    const turn = Gesture.Pan()
      .onStart(() => {
        if (stage.get() === HELD) hold();
      })
      .onUpdate((e) => {
        if (stage.get() === HELD) follow(e.translationX, e.translationY);
      })
      .onEnd((e) => {
        if (stage.get() === HELD) release(e.velocityX, e.velocityY);
      });
    // Set down: face up whichever way it was turned, then lowered onto the
    // stage; the 2D drawing takes over on contact.
    const putDown = Gesture.Tap().onEnd(() => {
      if (stage.get() !== HELD) return;
      stage.set(LOWERING);
      const target = faceUpYaw(yaw.get());
      const away = Math.abs(target - yaw.get());
      yaw.set(withSpring(target, { stiffness: 200, damping: 24, mass: 1 }));
      pitch.set(withSpring(0, { stiffness: 200, damping: 24, mass: 1 }));
      lift.set(
        withDelay(
          Math.min(260, away * 90),
          withSpring(0, LOWER, (done) => {
            if (!done) return;
            yaw.set(0);
            pitch.set(0);
            covered.set(0);
            shown.set(withDelay(OVERLAP, withTiming(0, { duration: 0 })));
            stage.set(REST);
            scheduleOnRN(landed);
          }),
        ),
      );
    });
    return { pickUp, held: Gesture.Race(turn, putDown) };
  }, [
    begin,
    covered,
    enabled,
    grab,
    landed,
    lift,
    pitch,
    shown,
    stage,
    tickFace,
    yaw,
  ]);

  // Leaving the screen mid-lift: drop straight back to the 2D drawing.
  useEffect(
    () => () => {
      link.stop();
      shown.set(0);
      covered.set(0);
      stage.set(REST);
      lift.set(0);
      yaw.set(0);
      pitch.set(0);
    },
    [covered, link, lift, pitch, shown, stage, yaw],
  );

  return {
    lift,
    shown,
    covered,
    tick,
    link,
    lifted,
    used,
    pickUp: gestures.pickUp,
    held: gestures.held,
  };
}

export type ConsoleLift = ReturnType<typeof useConsoleLift>;

/**
 * The lifted console: three.js renders the object offscreen (on Skia's
 * Graphite device when there is one, see gpu/skia-gpu.ts) and Skia
 * composites it, with a contact shadow cast from
 * the rendered silhouette that grows and softens as the object rises.
 */
export function LiftLayer({
  lift,
  frame,
  housing,
  active,
  glow,
  fog,
}: {
  lift: ConsoleLift;
  /** fx.liftLight: mirror the lifted object in the glass floor. */
  glow: boolean;
  /** Page color as seen (ambient dim included), for the floor's fog. */
  fog: SharedValue<number>;
  /** The layer's box in its parent, dp. */
  frame: Box;
  /** The housing's box inside the layer, dp. */
  housing: Box;
  /** Mount the renderer (focused screen, effect on). */
  active: boolean;
}) {
  const { link, shown, tick } = lift;
  const height = lift.lift;
  const make = useCallback<OnDemandSceneFactory>(
    (renderer, size, gpu) =>
      makeLiftScene(
        renderer,
        size,
        { width: frame.width, height: frame.height },
        housing,
        link,
        gpu,
      ),
    [frame.width, frame.height, housing, link],
  );
  const size = useMemo(
    () =>
      active
        ? { width: frame.width, height: frame.height }
        : { width: 0, height: 0 },
    [active, frame.width, frame.height],
  );
  const image = useOnDemandThreeScene(size, make, [make]);
  const shadowDy = useDerivedValue(
    () => 4 + 34 * height.get() + tick.get() * 1e-3,
  );
  const shadowBlur = useDerivedValue(() => 5 + 17 * height.get());
  const shadowColor = useDerivedValue(
    () => `rgba(0,0,0,${(0.34 - 0.14 * height.get()).toFixed(3)})`,
  );
  // The glass floor under the lifted object: the same mirror line, squash
  // and fog as the 2D floor, which hands over to it as the object rises.
  const s = housing.width / 1000;
  const mirror = housing.y + housing.height + (GAP / 2) * s;
  const depth = housing.height * SQUASH * DEPTH;
  const mirrored = useDerivedValue(() => Math.min(1, height.get() * 1.6));
  const fogColors = useDerivedValue(() => {
    const c = Math.round(255 * fog.get());
    return VISIBLE.map((v) => `rgba(${c},${c},${c},${1 - v})`);
  });
  return (
    <GestureDetector gesture={lift.held}>
      <View
        style={StyleSheet.absoluteFill}
        pointerEvents={lift.lifted ? "auto" : "none"}
      >
        <Canvas
          pointerEvents="none"
          style={{
            position: "absolute",
            left: frame.x,
            top: frame.y,
            width: frame.width,
            height: frame.height,
          }}
        >
          <Group opacity={shown}>
            {glow && (
              <Group opacity={mirrored}>
                <Group
                  transform={[
                    { translateY: mirror },
                    { scaleY: -SQUASH },
                    { translateY: -mirror },
                  ]}
                >
                  <Image
                    image={image}
                    x={0}
                    y={0}
                    width={frame.width}
                    height={frame.height}
                    fit="fill"
                  />
                </Group>
                <Rect
                  x={0}
                  y={mirror}
                  width={frame.width}
                  height={frame.height - mirror}
                >
                  <LinearGradient
                    start={vec(0, mirror)}
                    end={vec(0, mirror + depth)}
                    colors={fogColors}
                    positions={VISIBLE_AT}
                  />
                </Rect>
              </Group>
            )}
            <Image
              image={image}
              x={0}
              y={0}
              width={frame.width}
              height={frame.height}
              fit="fill"
            >
              <Shadow
                dx={0}
                dy={shadowDy}
                blur={shadowBlur}
                color={shadowColor}
                shadowOnly
              />
            </Image>
            <Image
              image={image}
              x={0}
              y={0}
              width={frame.width}
              height={frame.height}
              fit="fill"
            />
          </Group>
        </Canvas>
      </View>
    </GestureDetector>
  );
}
