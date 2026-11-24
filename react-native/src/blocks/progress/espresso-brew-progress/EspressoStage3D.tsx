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
import { barAt, camAt, cameraAt, cremaAt, cupAt, fillAt, filmDose, lerp, lightAt, LOOP, needleDeg, streamAt, swirlAt, tanHalfFovY } from "./espressoMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  COFFEE_BASE,
  COFFEE_REF,
  CREMA_H,
  CREMA_REF,
  CUP_REST,
  DOSE_H,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  GROUP_LIGHT_ON,
  GROUP_LIGHT_RGB,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  NEEDLE_PIVOT,
  SPOUT_TIP_Y,
  STREAM_REF,
  STREAM_TIPS,
} from "./espressoScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/espresso.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/espresso-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

const RAD = Math.PI / 180;
const Y_AXIS: [number, number, number] = [0, 1, 0];
const Z_AXIS: [number, number, number] = [0, 0, 1];
const ANCHOR_OFF: number[] = [0, -1];
const HIDDEN: [number, number, number] = [1e-4, 1e-4, 1e-4];
/** Below this a layer or a stream is not drawn (m). */
const MIN_H = 0.00015;
/** The light's emissive is only sent when it moved this much (of GROUP_LIGHT_ON). */
const LIGHT_STEP = 0.004;

export type EspressoStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While it plays this stage computes
   * the film itself from its own frame time; otherwise it shows the values below. */
  anchor: SharedValue<number[]>;
  /** A finger's place on the film's timeline (s), its dose (0 single .. 1 double) and its orbit (deg). */
  time: SharedValue<number>;
  dose: SharedValue<number>;
  orbit: SharedValue<number>;
  initialTime: number;
  initialDose: number;
  onReady?: () => void;
};

type Setup = {
  needle: Entity | null;
  cup: Entity | null;
  coffee: Entity | null;
  crema: Entity | null;
  streams: (Entity | null)[];
  light: MaterialInstance | null;
};

function Scene(props: EspressoStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const timeF = useFilamentSharedValueBridge(props.time, props.initialTime);
  const doseF = useFilamentSharedValueBridge(props.dose, props.initialDose);
  const orbitF = useFilamentSharedValueBridge(props.orbit, 0);

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
    // no light casts a shadow: the machine's contact shadow is a baked decal on the floor
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const lightEntity = asset.getFirstEntityByName("GroupLight");
    return {
      needle: asset.getFirstEntityByName("Needle") ?? null,
      cup: asset.getFirstEntityByName("Cup") ?? null,
      coffee: asset.getFirstEntityByName("Coffee") ?? null,
      crema: asset.getFirstEntityByName("Crema") ?? null,
      streams: [asset.getFirstEntityByName("Stream0") ?? null, asset.getFirstEntityByName("Stream1") ?? null],
      light: lightEntity != null ? renderableManager.getMaterialInstanceAt(lightEntity, 0) : null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const lightShown = useFilamentSharedValue(-1);

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
    const tt = playing ? filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP) : timeF.value;
    const dose = playing ? filmDose(tt) : doseF.value;
    const orbit = playing ? 0 : orbitF.value;
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(camAt(tt), orbit, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    if (s.needle != null) {
      transformManager.setEntityRotation(s.needle, needleDeg(barAt(tt)) * RAD, Z_AXIS, false);
      transformManager.setEntityPosition(s.needle, NEEDLE_PIVOT, true);
    }
    if (s.light != null) {
      const k = lightAt(tt);
      if (Math.abs(k - lightShown.value) > LIGHT_STEP || (k === 0 && lightShown.value !== 0)) {
        const e = GROUP_LIGHT_ON * k;
        s.light.setFloat3Parameter("emissiveFactor", [GROUP_LIGHT_RGB[0] * e, GROUP_LIGHT_RGB[1] * e, GROUP_LIGHT_RGB[2] * e]);
        lightShown.value = k;
      }
    }
    // the cup and what is in it
    const slide = [0, 0];
    cupAt(tt, slide);
    if (s.cup != null) transformManager.setEntityPosition(s.cup, [CUP_REST[0] + slide[0], CUP_REST[1] + slide[1], CUP_REST[2]], false);
    const total = lerp(DOSE_H[0], DOSE_H[1], dose) * fillAt(tt);
    const crema = Math.min(total, lerp(CREMA_H[0], CREMA_H[1], dose) * cremaAt(tt));
    const body = total - crema;
    if (s.coffee != null) {
      if (body < MIN_H) {
        transformManager.setEntityScale(s.coffee, HIDDEN, false);
      } else {
        transformManager.setEntityScale(s.coffee, [1, body / COFFEE_REF, 1], false);
        transformManager.setEntityPosition(s.coffee, [0, COFFEE_BASE, 0], true);
      }
    }
    if (s.crema != null) {
      if (crema < MIN_H) {
        transformManager.setEntityScale(s.crema, HIDDEN, false);
      } else {
        transformManager.setEntityRotation(s.crema, swirlAt(tt), Y_AXIS, false);
        transformManager.setEntityScale(s.crema, [1, crema / CREMA_REF, 1], true);
        transformManager.setEntityPosition(s.crema, [0, COFFEE_BASE + body, 0], true);
      }
    }
    // the streams run from the spout tips to the surface; only while the cup stands under them
    const surface = CUP_REST[1] + slide[1] + COFFEE_BASE + total;
    const gap = SPOUT_TIP_Y - surface;
    const st = [0, 0, 0];
    for (let i = 0; i < STREAM_TIPS.length; i++) {
      const e = s.streams[i];
      if (e == null) continue;
      streamAt(tt, i, st);
      const top = SPOUT_TIP_Y - st[1] * gap;
      const len = (st[0] - st[1]) * gap;
      if (len < MIN_H || st[2] <= 0 || slide[0] > 0.001) {
        transformManager.setEntityScale(e, HIDDEN, false);
        continue;
      }
      const tip = STREAM_TIPS[i];
      transformManager.setEntityScale(e, [st[2], len / STREAM_REF, st[2]], false);
      transformManager.setEntityPosition(e, [tip[0], top, tip[2]], true);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configuredAspect, doseF, lightShown, orbitF, setup, timeF, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/** Filament stage: the studio sweep, the espresso machine and the cup. It runs no clock of its own. */
export function EspressoStage3D(props: EspressoStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={{ enabled: false }}
        antiAliasing="FXAA"
        bloomOptions={{ enabled: true, strength: 0.06, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
        temporalAntiAliasingOptions={{ enabled: false }}
      >
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
