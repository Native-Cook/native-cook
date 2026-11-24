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

import { filamentFrameMs, filmTimeAt } from "./filamentClock";
import {
  boltAt,
  boltDir,
  cameraAt,
  DEG_PER_NUMBER,
  DOOR_DEG,
  doorAt,
  filmDial,
  filmUnlock,
  HANDLE_DEG,
  handleAt,
  LOOP,
  tanHalfFovY,
} from "./safeMotion";
import {
  BOLT_COUNT,
  BOLT_TRAVEL,
  CAMERA_FAR,
  CAMERA_NEAR,
  DIAL_PIVOT,
  DOOR_CENTRE,
  DOOR_HINGE,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  HANDLE_PIVOT,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  TARGET,
} from "./safeScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/safe.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/safe-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

const RAD = Math.PI / 180;
const Y_AXIS: [number, number, number] = [0, 1, 0];
const Z_AXIS: [number, number, number] = [0, 0, 1];
const ANCHOR_OFF: number[] = [0, -1];

export type SafeStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While it plays this stage computes
   * the film itself from its own frame time; otherwise it shows the values below. */
  anchor: SharedValue<number[]>;
  /** The dial (unwrapped numbers), the unlock timeline (s) and the wrong-code shake (m along x). */
  dial: SharedValue<number>;
  unlock: SharedValue<number>;
  shake: SharedValue<number>;
  initialDial: number;
  initialUnlock: number;
  code: readonly [number, number, number];
  onReady?: () => void;
};

type Setup = {
  root: Entity | null;
  door: Entity | null;
  dial: Entity | null;
  handle: Entity | null;
  bolts: (Entity | null)[];
};

function Scene(props: SafeStage3DProps): JSX.Element {
  const { width, height, onReady, code } = props;
  const c0 = code[0];
  const c1 = code[1];
  const c2 = code[2];
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const dialF = useFilamentSharedValueBridge(props.dial, props.initialDial);
  const unlockF = useFilamentSharedValueBridge(props.unlock, props.initialUnlock);
  const shakeF = useFilamentSharedValueBridge(props.shake, 0);

  const key = useLightEntity(lightManager, { type: "directional", direction: KEY_DIRECTION, intensity: KEY_LUX, colorKelvin: 5200, castShadows: false });
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
    // no light casts a shadow: the safe's contact shadow is a baked decal on the floor
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const bolts: (Entity | null)[] = [];
    for (let i = 0; i < BOLT_COUNT; i++) bolts.push(asset.getFirstEntityByName(`Bolt${i}`) ?? null);
    return {
      root: asset.getFirstEntityByName("Safe") ?? null,
      door: asset.getFirstEntityByName("Door") ?? null,
      dial: asset.getFirstEntityByName("Dial") ?? null,
      handle: asset.getFirstEntityByName("Handle") ?? null,
      bolts,
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
    const an = anchorF.value;
    let dialAt = 0;
    let u = 0;
    if (an != null && an.length >= 2 && an[0] === 1) {
      const tt = filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP);
      dialAt = filmDial(tt, c0, c1, c2);
      u = filmUnlock(tt);
    } else {
      dialAt = dialF.value;
      u = unlockF.value;
    }
    const open = doorAt(u);
    const eye = [0, 0, 0];
    cameraAt(open, eye);
    camera.lookAt([eye[0], eye[1], eye[2]], TARGET, [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    if (s.root != null) transformManager.setEntityPosition(s.root, [shakeF.value, 0, 0], false);
    if (s.door != null) {
      // opening towards the viewer is a negative turn about y
      transformManager.setEntityRotation(s.door, -open * DOOR_DEG * RAD, Y_AXIS, false);
      transformManager.setEntityPosition(s.door, DOOR_HINGE, true);
    }
    if (s.dial != null) {
      transformManager.setEntityRotation(s.dial, dialAt * DEG_PER_NUMBER * RAD, Z_AXIS, false);
      transformManager.setEntityPosition(s.dial, DIAL_PIVOT, true);
    }
    if (s.handle != null) {
      transformManager.setEntityRotation(s.handle, -handleAt(u) * HANDLE_DEG * RAD, Z_AXIS, false);
      transformManager.setEntityPosition(s.handle, HANDLE_PIVOT, true);
    }
    const d = [0, 0];
    for (let i = 0; i < BOLT_COUNT; i++) {
      const b = s.bolts[i];
      if (b == null) continue;
      boltDir(i, d);
      const pull = boltAt(u, i) * BOLT_TRAVEL;
      transformManager.setEntityPosition(b, [DOOR_CENTRE[0] - d[0] * pull, DOOR_CENTRE[1] - d[1] * pull, DOOR_CENTRE[2]], false);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, c0, c1, c2, camera, configuredAspect, dialF, setup, shakeF, transformManager, unlockF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/** Filament stage: the studio sweep and the safe. It runs no clock of its own. */
export function SafeStage3D(props: SafeStage3DProps): JSX.Element {
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
