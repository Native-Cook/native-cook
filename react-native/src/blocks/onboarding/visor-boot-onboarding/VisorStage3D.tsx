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
import type { Entity, Float3, MaterialInstance, RNFCamera } from "react-native-filament";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue as useFilamentSharedValue } from "react-native-worklets-core";

import {
  blendChannel,
  cameraAt,
  filmLamps,
  filmRise,
  filmSweep,
  lerp,
  pageLamps,
  pageRise,
} from "./visorMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  HELMET_Y,
  HFOV_DEG,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  LAMP_STRENGTH,
  RIM_DIRECTION,
  RIM_LUX,
  SWEEP_FROM_DEG,
  SWEEP_LUX,
  SWEEP_TO_DEG,
} from "./visorScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/visor.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/visor-studio-ibl.ktx");

/** The typings omit setProjection's fifth argument; four arguments crash Hermes. */
type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};
/** RNF types setDirection as Float3[]; the native method takes one Float3. */
type LightVectorSetter = (entity: Entity, v: Float3) => void;

export type VisorStage3DProps = {
  width: number;
  height: number;
  /** Film loop time (s). */
  t: SharedValue<number>;
  /** Float page under a finger (0 intro .. 4 Get started). */
  page: SharedValue<number>;
  /** 0 = the film, 1 = the pages. */
  m: SharedValue<number>;
  /** The finger's orbit about the helmet (degrees). */
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
  /** Called once the model is loaded (the film's clock waits for it). */
  onReady?: () => void;
};

type Setup = { rig: Entity | null; shell: MaterialInstance[] };

function Scene(props: VisorStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const pF = useFilamentSharedValueBridge(props.page, 0);
  const mF = useFilamentSharedValueBridge(props.m, 0);
  const yawF = useFilamentSharedValueBridge(props.yaw, 0);
  const pitchF = useFilamentSharedValueBridge(props.pitch, 0);

  const key = useLightEntity(lightManager, {
    type: "directional",
    direction: KEY_DIRECTION,
    intensity: KEY_LUX,
    colorKelvin: 4300,
    castShadows: false,
  });
  const rim = useLightEntity(lightManager, {
    type: "directional",
    direction: RIM_DIRECTION,
    intensity: RIM_LUX,
    colorKelvin: 9000,
    castShadows: false,
  });
  const sweep = useLightEntity(lightManager, {
    type: "directional",
    direction: [0, -0.35, -1],
    intensity: 0,
    colorKelvin: 6500,
    castShadows: false,
  });
  useEntityInScene(scene, key);
  useEntityInScene(scene, rim);
  useEntityInScene(scene, sweep);

  // filmic tone: ACES legacy grading at 0 stops (patched into RNF; see index.tsx)
  useEffect(() => {
    workletContext.runAsync(() => {
      "worklet";
      engine.setACESLegacyColorGrading(0);
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
    const s: Setup = { rig: asset.getFirstEntityByName("HelmetRig") ?? null, shell: [] };
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      const e = ents[i];
      // RNF exposes no shadow bias; the plinth carries a baked contact shadow and the shell its own AO
      renderableManager.setCastShadow(e, false);
      renderableManager.setReceiveShadow(e, false);
      const n = renderableManager.getPrimitiveCount(e);
      for (let k = 0; k < n; k++) {
        const mi = renderableManager.getMaterialInstanceAt(e, k);
        // RNF registers the name as a getter called `getName` (its typings omit it)
        const name = (mi as unknown as { getName: string }).getName;
        if (name === "HelmetShell") s.shell.push(mi);
      }
    }
    return s;
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const lastLamp = useFilamentSharedValue(-1);
  const warned = useFilamentSharedValue(false);
  const setDir = lightManager.setDirection as unknown as LightVectorSetter;

  RenderCallbackContext.useRenderCallback(() => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      (camera as CameraWithFovDirection).setProjection(HFOV_DEG, aspect, CAMERA_NEAR, CAMERA_FAR, "horizontal");
      configuredAspect.value = aspect;
    }
    const t = tF.value;
    const p = pF.value;
    const m = mF.value;
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(t, p, m, yawF.value, pitchF.value, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    // the light sweep of the Get started beat swings across the front; only the film has it
    const sw = [0, 0];
    filmSweep(t, sw);
    const level = sw[0] * (1 - Math.min(1, Math.max(0, m)));
    const az = (lerp(SWEEP_FROM_DEG, SWEEP_TO_DEG, sw[1]) * Math.PI) / 180;
    setDir(sweep, [-Math.sin(az), -0.35, -Math.cos(az)]);
    lightManager.setIntensity(sweep, SWEEP_LUX * level);

    const s = setup;
    if (s == null) return;
    if (s.rig != null) {
      const rise = blendChannel(filmRise(t), pageRise(p), m);
      transformManager.setEntityRotation(s.rig, 0, [0, 1, 0], false);
      transformManager.setEntityPosition(s.rig, [0, HELMET_Y + rise, 0], true);
    }
    // panel lamps: only write when the level moved
    const lamp = blendChannel(filmLamps(t), pageLamps(p), m);
    const q = Math.round(lamp * 400);
    if (q !== lastLamp.value) {
      lastLamp.value = q;
      const k = LAMP_STRENGTH * lamp;
      for (let i = 0; i < s.shell.length; i++) {
        try {
          s.shell[i].setFloat3Parameter("emissiveFactor", [k, k, k]);
        } catch (err) {
          if (!warned.value) {
            warned.value = true;
            console.warn(`[VisorBoot] emissiveFactor failed: ${String(err)}`);
          }
        }
      }
    }
    /* eslint-enable react-hooks/immutability */
  }, [aspect, camera, configuredAspect, lastLamp, lightManager, mF, pF, pitchF, setDir, setup, sweep, tF, transformManager, warned, yawF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the helmet on its plinth. It never runs its own clock; it shows the film at the loop time it is
 * given (or the pages under a finger), so it cannot drift from the HUD and the copy around it. The view is
 * see-through: the studio backdrop and the plinth's floor shadow are drawn in Skia underneath it.
 */
export function VisorStage3D(props: VisorStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={{ enabled: false }}
        bloomOptions={{ enabled: true, strength: 0.14, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
        // No TAA: with a moving camera it dropped whole meshes for single frames on device. FXAA instead.
        antiAliasing="FXAA"
        temporalAntiAliasingOptions={{ enabled: false }}
      >
        {/* Transparent rendering makes RNF clear every frame, and lets the Skia backdrop show through. */}
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
