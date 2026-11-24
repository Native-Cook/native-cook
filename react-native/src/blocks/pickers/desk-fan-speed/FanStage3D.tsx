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
import type { Entity, FrameInfo, MaterialInstance, RNFCamera } from "react-native-filament";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue as useFilamentSharedValue } from "react-native-worklets-core";

import { filamentFrameMs, filmTimeAt } from "./filamentClock";
import { blurAt, cameraAt, filmKey, filmSpeed, filmTurns, filmYaw, LOOP, tanHalfFovY } from "./fanMotion";
import {
  BLADES_PIVOT,
  BLUR_RGB,
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  HEAD_PIVOT,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  KEY_POSITIONS,
  KEY_TRAVEL,
  TARGET,
} from "./fanScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/fan.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/fan-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

const RAD = Math.PI / 180;
const Y_AXIS: [number, number, number] = [0, 1, 0];
const Z_AXIS: [number, number, number] = [0, 0, 1];
const ANCHOR_OFF: number[] = [0, -1];
const KEYS_OFF: number[] = [1, 0, 0, 0];
/** The blur's alpha is only sent when it moved this much. */
const BLUR_STEP = 0.004;

export type FanStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While it plays this stage computes
   * the film itself from its own frame time; otherwise it shows the values below. */
  anchor: SharedValue<number[]>;
  /** Blade angle (turns), blade speed (turns a second), head angle (deg), and how far each key is down (0..1). */
  turns: SharedValue<number>;
  speed: SharedValue<number>;
  yaw: SharedValue<number>;
  keys: SharedValue<number[]>;
  initialYaw: number;
  onReady?: () => void;
};

type Setup = {
  head: Entity | null;
  blades: Entity | null;
  blur: MaterialInstance | null;
  keys: (Entity | null)[];
};

function Scene(props: FanStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const turnsF = useFilamentSharedValueBridge(props.turns, 0);
  const speedF = useFilamentSharedValueBridge(props.speed, 0);
  const yawF = useFilamentSharedValueBridge(props.yaw, props.initialYaw);
  const keysF = useFilamentSharedValueBridge(props.keys, KEYS_OFF);

  const key = useLightEntity(lightManager, { type: "directional", direction: KEY_DIRECTION, intensity: KEY_LUX, colorKelvin: 5400, castShadows: false });
  const fill = useLightEntity(lightManager, { type: "directional", direction: FILL_DIRECTION, intensity: FILL_LUX, colorKelvin: 7000, castShadows: false });
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
    // no light casts a shadow: the fan's contact shadow is a baked decal on the floor
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const blurEntity = asset.getFirstEntityByName("BladeBlur");
    const blur = blurEntity != null ? renderableManager.getMaterialInstanceAt(blurEntity, 0) : null;
    if (blur != null) blur.setFloat4Parameter("baseColorFactor", [BLUR_RGB[0], BLUR_RGB[1], BLUR_RGB[2], 0]);
    const keys: (Entity | null)[] = [];
    for (let i = 0; i < KEY_POSITIONS.length; i++) keys.push(asset.getFirstEntityByName(`Key${i}`) ?? null);
    return {
      head: asset.getFirstEntityByName("Head") ?? null,
      blades: asset.getFirstEntityByName("Blades") ?? null,
      blur,
      keys,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const blurShown = useFilamentSharedValue(0);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      const fovY = (2 * Math.atan(tanHalfFovY(aspect)) * 180) / Math.PI;
      (camera as CameraWithFovDirection).setProjection(fovY, aspect, CAMERA_NEAR, CAMERA_FAR, "vertical");
      configuredAspect.value = aspect;
    }
    const an = anchorF.value;
    const playing = an != null && an.length >= 2 && an[0] === 1;
    const tt = playing ? filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP) : 0;
    const turns = playing ? filmTurns(tt) : turnsF.value;
    const speed = playing ? filmSpeed(tt) : speedF.value;
    const yaw = playing ? filmYaw(tt) : yawF.value;
    const eye = [0, 0, 0];
    cameraAt(speed, eye);
    camera.lookAt([eye[0], eye[1], eye[2]], TARGET, [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    if (s.head != null) {
      transformManager.setEntityRotation(s.head, yaw * RAD, Y_AXIS, false);
      transformManager.setEntityPosition(s.head, HEAD_PIVOT, true);
    }
    if (s.blades != null) {
      // clockwise for the viewer is a negative turn about z; only the fraction matters
      const frac = turns - Math.floor(turns);
      transformManager.setEntityRotation(s.blades, -frac * 2 * Math.PI, Z_AXIS, false);
      transformManager.setEntityPosition(s.blades, BLADES_PIVOT, true);
    }
    if (s.blur != null) {
      const a = blurAt(speed);
      if (Math.abs(a - blurShown.value) > BLUR_STEP || (a === 0 && blurShown.value !== 0)) {
        s.blur.setFloat4Parameter("baseColorFactor", [BLUR_RGB[0], BLUR_RGB[1], BLUR_RGB[2], a]);
        blurShown.value = a;
      }
    }
    const keysNow = keysF.value;
    for (let i = 0; i < KEY_POSITIONS.length; i++) {
      const k = s.keys[i];
      if (k == null) continue;
      const down = playing ? filmKey(tt, i) : keysNow[i];
      const p = KEY_POSITIONS[i];
      transformManager.setEntityPosition(k, [p[0], p[1] - down * KEY_TRAVEL, p[2]], false);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, blurShown, camera, configuredAspect, keysF, setup, speedF, transformManager, turnsF, yawF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/** Filament stage: the studio sweep and the fan. It runs no clock of its own. */
export function FanStage3D(props: FanStage3DProps): JSX.Element {
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
