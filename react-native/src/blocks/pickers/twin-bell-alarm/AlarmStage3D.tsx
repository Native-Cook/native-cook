import type { JSX } from "react";
import { useEffect, useMemo } from "react";
import { View } from "react-native";
import {
  EnvironmentalLight,
  FilamentScene,
  FilamentView,
  RenderCallbackContext,
  useEntityInScene,
  useFilamentContext,
  useLightEntity,
  useModel,
  useWorkletMemo,
} from "react-native-filament";
import type { Entity, FrameInfo, RNFCamera } from "react-native-filament";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue as useFilamentSharedValue } from "react-native-worklets-core";

import { cameraAt, filmHour, filmMinute, filmPush, filmRing, LOOP, ringAt, tanHalfFovY } from "./alarmMotion";
import {
  BELL_L_PIVOT,
  BELL_R_PIVOT,
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  GLASS_ON,
  HAMMER_PIVOT,
  HOUR_PIVOT,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  MINUTE_PIVOT,
} from "./alarmScene";
import { filamentFrameMs, filmTimeAt } from "./filamentClock";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/alarm.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/alarm-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

const RAD = Math.PI / 180;
const Z_AXIS: [number, number, number] = [0, 0, 1];
/** A piece that is "not there" is this small (a scale of 0 makes a matrix Filament cannot invert). */
const NONE = 0.0001;

export type AlarmStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While the film plays this stage
   * computes it itself from its own frame time; otherwise it shows the values below (a finger's, and the eases back). */
  anchor: SharedValue<number[]>;
  /** The hands (deg clockwise from 12, not wrapped), and what they hold at mount. */
  hour: SharedValue<number>;
  minute: SharedValue<number>;
  initialHour: number;
  initialMinute: number;
  /** Seconds into a ring (negative: not ringing). */
  ring: SharedValue<number>;
  /** The camera's push, 0..1, and a finger's orbit (deg). */
  push: SharedValue<number>;
  orbit: SharedValue<number>;
  onReady?: () => void;
};

const ANCHOR_OFF: number[] = [0, -1];

type Setup = {
  root: Entity | null;
  hour: Entity | null;
  minute: Entity | null;
  hammer: Entity | null;
  bellL: Entity | null;
  bellR: Entity | null;
};

function Scene(props: AlarmStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const hourF = useFilamentSharedValueBridge(props.hour, props.initialHour);
  const minuteF = useFilamentSharedValueBridge(props.minute, props.initialMinute);
  const ringF = useFilamentSharedValueBridge(props.ring, -1);
  const pushF = useFilamentSharedValueBridge(props.push, 0);
  const orbitF = useFilamentSharedValueBridge(props.orbit, 0);

  const key = useLightEntity(lightManager, {
    type: "directional",
    direction: KEY_DIRECTION,
    intensity: KEY_LUX,
    colorKelvin: 5400,
    castShadows: false,
  });
  const fill = useLightEntity(lightManager, {
    type: "directional",
    direction: FILL_DIRECTION,
    intensity: FILL_LUX,
    colorKelvin: 7000,
    castShadows: false,
  });
  useEntityInScene(scene, key);
  useEntityInScene(scene, fill);

  useEffect(() => {
    workletContext.runAsync(() => {
      "worklet";
      engine.setACESLegacyColorGrading(EXPOSURE);
    });
  }, [engine, workletContext]);

  const model = useModel(MODEL_SOURCE);
  const asset = model.state === "loaded" ? model.asset : undefined;
  useEffect(() => {
    if (asset != null) onReady?.();
  }, [asset, onReady]);

  const setup = useWorkletMemo<Setup | null>(() => {
    "worklet";
    if (asset == null) return null;
    // no light casts a shadow: the clock's contact shadow is a baked decal on the floor
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    if (!GLASS_ON) {
      const glass = asset.getFirstEntityByName("Glass");
      if (glass != null) transformManager.setEntityScale(glass, [NONE, NONE, NONE], false);
    }
    return {
      root: asset.getFirstEntityByName("Clock") ?? null,
      hour: asset.getFirstEntityByName("HourHand") ?? null,
      minute: asset.getFirstEntityByName("MinuteHand") ?? null,
      hammer: asset.getFirstEntityByName("Hammer") ?? null,
      bellL: asset.getFirstEntityByName("BellL") ?? null,
      bellR: asset.getFirstEntityByName("BellR") ?? null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      const fovY = (2 * Math.atan(tanHalfFovY(aspect)) * 180) / Math.PI;
      (camera as CameraWithFovDirection).setProjection(fovY, aspect, CAMERA_NEAR, CAMERA_FAR, "vertical");
      configuredAspect.value = aspect;
    }
    // The film is a pure function of time: while it plays this thread computes it for its own frame (the same
    // functions the UI thread runs, at the same vsync's time). A finger's values (and the eases back) come across.
    const an = anchorF.value;
    let hourAt = 0;
    let minuteAt = 0;
    let ringT = -1;
    let push = 0;
    if (an != null && an.length >= 2 && an[0] === 1) {
      const tt = filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP);
      hourAt = filmHour(tt);
      minuteAt = filmMinute(tt);
      ringT = filmRing(tt);
      push = filmPush(tt);
    } else {
      hourAt = hourF.value;
      minuteAt = minuteF.value;
      ringT = ringF.value;
      push = pushF.value;
    }
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(push, orbitF.value, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    // the hands: clockwise for the viewer is a negative turn about z
    if (s.hour != null) {
      transformManager.setEntityRotation(s.hour, -hourAt * RAD, Z_AXIS, false);
      transformManager.setEntityPosition(s.hour, HOUR_PIVOT, true);
    }
    if (s.minute != null) {
      transformManager.setEntityRotation(s.minute, -minuteAt * RAD, Z_AXIS, false);
      transformManager.setEntityPosition(s.minute, MINUTE_PIVOT, true);
    }
    // the ring: hammer, bells, and the whole clock rocking on its legs, shuffling and hopping
    const r = [0, 0, 0, 0, 0, 0];
    ringAt(ringT, r);
    if (s.hammer != null) {
      transformManager.setEntityRotation(s.hammer, r[0], Z_AXIS, false);
      transformManager.setEntityPosition(s.hammer, HAMMER_PIVOT, true);
    }
    if (s.bellL != null) {
      transformManager.setEntityRotation(s.bellL, r[1], Z_AXIS, false);
      transformManager.setEntityPosition(s.bellL, BELL_L_PIVOT, true);
    }
    if (s.bellR != null) {
      transformManager.setEntityRotation(s.bellR, r[2], Z_AXIS, false);
      transformManager.setEntityPosition(s.bellR, BELL_R_PIVOT, true);
    }
    if (s.root != null) {
      transformManager.setEntityRotation(s.root, r[3], Z_AXIS, false);
      transformManager.setEntityPosition(s.root, [r[4], r[5], 0], true);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configuredAspect, hourF, minuteF, orbitF, pushF, ringF, setup, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the studio sweep and the alarm clock. It never runs its own clock; it shows what the shared values
 * say (or the film at the anchor's time).
 */
export function AlarmStage3D(props: AlarmStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene ambientOcclusionOptions={{ enabled: false }} antiAliasing="FXAA" bloomOptions={{ enabled: false }} temporalAntiAliasingOptions={{ enabled: false }}>
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
