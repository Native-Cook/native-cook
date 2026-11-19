import type { JSX } from "react";
import { useEffect, useMemo } from "react";
import { StyleSheet, View } from "react-native";
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
import type { Entity, RNFCamera } from "react-native-filament";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue as useFilamentSharedValue } from "react-native-worklets-core";

import { archYaw, cameraAt, pearlY, stageVisible } from "./spinMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  HFOV_DEG,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
} from "./spinScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/arca.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/arca-studio-ibl.ktx");

/** The typings omit setProjection's fifth argument; four arguments crash Hermes. */
type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

export type SpinStage3DProps = {
  width: number;
  height: number;
  /** Film time (s): the camera, the fold and the pearl. */
  t: SharedValue<number>;
  /** Turn of the whole sculpture (degrees): the film's or the finger's. */
  yaw: SharedValue<number>;
  onReady?: () => void;
};

type Setup = {
  rig: Entity | null;
  /** Pivots of the three arches, in the order of ARCHES (the outer one never turns on its own). */
  arches: (Entity | null)[];
  pearl: Entity | null;
};

function Scene(props: SpinStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const yawF = useFilamentSharedValueBridge(props.yaw, 0);

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
    colorKelvin: 7200,
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
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      // no shadow maps (RNF has no bias); the floor shadows are drawn in Skia under the view
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    return {
      rig: asset.getFirstEntityByName("ArcaRig") ?? null,
      arches: [
        asset.getFirstEntityByName("ArchOuter") ?? null,
        asset.getFirstEntityByName("ArchMiddle") ?? null,
        asset.getFirstEntityByName("ArchInner") ?? null,
      ],
      pearl: asset.getFirstEntityByName("Pearl") ?? null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);

  RenderCallbackContext.useRenderCallback(() => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      (camera as CameraWithFovDirection).setProjection(HFOV_DEG, aspect, CAMERA_NEAR, CAMERA_FAR, "horizontal");
      configuredAspect.value = aspect;
    }
    const t = tF.value;
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(t, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);
    /* eslint-enable react-hooks/immutability */

    const s = setup;
    if (s == null) return;
    if (s.rig != null) transformManager.setEntityRotation(s.rig, (yawF.value * Math.PI) / 180, [0, 1, 0], false);
    for (let i = 1; i < 3; i++) {
      const e = s.arches[i];
      if (e != null) transformManager.setEntityRotation(e, (archYaw(t, i) * Math.PI) / 180, [0, 1, 0], false);
    }
    if (s.pearl != null) transformManager.setEntityPosition(s.pearl, [0, pearlY(t), 0], false);
  }, [aspect, camera, configuredAspect, setup, tF, transformManager, yawF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the Arca sculpture alone. The view is see-through: the paper backdrop, the floor track and the
 * shadows are drawn in Skia underneath, the flat mark over it. It never runs its own clock; it shows what the shared
 * values say, so it cannot drift from the 2D layers. It fades out once the flat mark has taken its place.
 */
export function SpinStage3D(props: SpinStage3DProps): JSX.Element {
  const { width, height, t } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  const fade = useAnimatedStyle(() => ({ opacity: stageVisible(t.get()) }));
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, fade]}>
      <View style={style}>
        <FilamentScene
          ambientOcclusionOptions={{ enabled: false }}
          bloomOptions={{ enabled: true, strength: 0.05, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
          antiAliasing="FXAA"
          temporalAntiAliasingOptions={{ enabled: false }}
        >
          <FilamentView enableTransparentRendering style={style}>
            <Scene {...props} />
          </FilamentView>
        </FilamentScene>
      </View>
    </Animated.View>
  );
}
