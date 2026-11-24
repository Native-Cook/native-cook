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
import { cameraAt, filmBright, filmLevel, filmOrbit, filmPress, lerp, lightOut, LOOP, tanHalfFovY, warmColor } from "./switchMotion";
import {
  BOUNCE_DIRECTION,
  BOUNCE_LUX,
  BULB_FALLOFF,
  BULB_LUMENS,
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  GLOBE,
  GLOBE_EMISSIVE,
  IBL_INTENSITY,
  ROCK,
  ROCKER_PIVOT,
  SHADOW_RGB,
} from "./switchScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/switch.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/switch-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

export type SwitchStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While the film plays this stage
   * computes it itself from its own frame time; otherwise it shows the values below (a finger's, and the eases back). */
  anchor: SharedValue<number[]>;
  t: SharedValue<number>;
  /** The rocker, 0 (off) .. 1 (on). */
  press: SharedValue<number>;
  /** The filament, 0..1, and the brightness, 0..1. */
  level: SharedValue<number>;
  bright: SharedValue<number>;
  /** The camera's way round the wall, 0..1. */
  orbit: SharedValue<number>;
  onReady?: () => void;
};

const ANCHOR_OFF: number[] = [0, -1];

type Setup = {
  rocker: Entity | null;
  globe: MaterialInstance | null;
  shadow: MaterialInstance | null;
};

function Scene(props: SwitchStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const pressF = useFilamentSharedValueBridge(props.press, 0);
  const levelF = useFilamentSharedValueBridge(props.level, 0);
  const brightF = useFilamentSharedValueBridge(props.bright, 1);
  const orbitF = useFilamentSharedValueBridge(props.orbit, 0);

  // the bulb: a point light inside the globe; the bounce: the room answering it. Both start dark and are driven
  // by the render callback (never switched on in one frame).
  const bulb = useLightEntity(lightManager, {
    type: "point",
    position: GLOBE,
    intensity: 0,
    colorKelvin: 2700,
    falloffRadius: BULB_FALLOFF,
    castShadows: false,
  });
  const bounce = useLightEntity(lightManager, {
    type: "directional",
    direction: BOUNCE_DIRECTION,
    intensity: 0,
    colorKelvin: 3000,
    castShadows: false,
  });
  useEntityInScene(scene, bulb);
  useEntityInScene(scene, bounce);

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
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const g = asset.getFirstEntityByName("Globe");
    const sh = asset.getFirstEntityByName("PlateShadow");
    return {
      rocker: asset.getFirstEntityByName("Rocker") ?? null,
      globe: g != null ? renderableManager.getMaterialInstanceAt(g, 0) : null,
      shadow: sh != null ? renderableManager.getMaterialInstanceAt(sh, 0) : null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const lastLight = useFilamentSharedValue(-1);

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
    let tt = 0;
    let press = 0;
    let level = 0;
    let bright = 1;
    let orbit = 0;
    if (an != null && an.length >= 2 && an[0] === 1) {
      tt = filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP);
      press = filmPress(tt);
      level = filmLevel(tt);
      bright = filmBright(tt);
      orbit = filmOrbit(tt);
    } else {
      tt = tF.value;
      press = pressF.value;
      level = levelF.value;
      bright = brightF.value;
      orbit = orbitF.value;
    }
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(orbit, tt, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    if (s.rocker != null) {
      // off = +ROCK (the bottom half in), on = -ROCK (the top half in), about x through the pivot
      transformManager.setEntityRotation(s.rocker, lerp(ROCK, -ROCK, press), [1, 0, 0], false);
      transformManager.setEntityPosition(s.rocker, ROCKER_PIVOT, true);
    }
    // the light: written only when it changes
    const k = lightOut(level, bright);
    const q = Math.round(k * 1000);
    if (q !== lastLight.value) {
      lastLight.value = q;
      const col = [0, 0, 0];
      warmColor(k, col);
      lightManager.setIntensity(bulb, BULB_LUMENS * k);
      lightManager.setColor(bulb, [col[0], col[1], col[2]]);
      lightManager.setIntensity(bounce, BOUNCE_LUX * k);
      if (s.globe != null) {
        const e = GLOBE_EMISSIVE * k;
        s.globe.setFloat3Parameter("emissiveFactor", [col[0] * e, col[1] * e, col[2] * e]);
      }
      if (s.shadow != null) s.shadow.setFloat4Parameter("baseColorFactor", [SHADOW_RGB[0], SHADOW_RGB[1], SHADOW_RGB[2], k]);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, bounce, brightF, bulb, camera, configuredAspect, lastLight, levelF, lightManager, orbitF, pressF, setup, tF, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the wall, the rocker switch and the sconce. It never runs its own clock; it shows what the shared
 * values say (or the film at the anchor's time).
 */
export function SwitchStage3D(props: SwitchStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={{ enabled: false }}
        bloomOptions={{ enabled: true, strength: 0.14, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
        antiAliasing="FXAA"
        temporalAntiAliasingOptions={{ enabled: false }}
      >
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
