import type { JSX } from "react";
import { useEffect, useMemo } from "react";
import { View } from "react-native";
import {
  EnvironmentalLight,
  FilamentScene,
  FilamentView,
  RenderCallbackContext,
  useAnimator,
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
import { easeInOutCubic, foldTime, G_H, G_PT, G_W, LOOP, PHASE, poseAt, poseRotation, seg } from "./planeMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  FOLD_DRIVER,
  FOLD_STEPS,
  HINGES,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  SHEET_L,
  VFOV_DEG,
} from "./planeScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/plane.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/plane-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

/** The hinge table flattened for the worklet: per hinge [pivot xyz, axis xyz, radians, step start, step end]. */
const HINGE_N = HINGES.length;
const HINGE_NAMES: string[] = HINGES.map((h) => h.node);
const HINGE_TABLE: number[] = [];
for (const h of HINGES) {
  const step = FOLD_STEPS[h.step];
  HINGE_TABLE.push(h.pivot[0], h.pivot[1], h.pivot[2], h.axis[0], h.axis[1], h.axis[2], (h.deg * Math.PI) / 180, step[0], step[1]);
}
const USE_ANIMATOR = FOLD_DRIVER === "animator";
const TAN_HALF = Math.tan((VFOV_DEG * Math.PI) / 360);

export type PlaneStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while it runs, the UI frame time (ms) of its t = 0]. While it runs this stage computes the
   * time itself from its own frame time; otherwise it shows `t`. */
  anchor: SharedValue<number[]>;
  t: SharedValue<number>;
  /** The screen's geometry (see planeMotion G_*). */
  geo: SharedValue<number[]>;
  onReady?: () => void;
};

const ANCHOR_OFF: number[] = [0, -1];
/** Until the geometry arrives the plane stays parked. */
const GEO_OFF: number[] = [];

type Setup = {
  root: Entity | null;
  panels: (Entity | null)[];
};

function Scene(props: PlaneStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const geoF = useFilamentSharedValueBridge(props.geo, GEO_OFF);

  const key = useLightEntity(lightManager, {
    type: "directional",
    direction: KEY_DIRECTION,
    intensity: KEY_LUX,
    colorKelvin: 5600,
    castShadows: false,
  });
  const fill = useLightEntity(lightManager, {
    type: "directional",
    direction: FILL_DIRECTION,
    intensity: FILL_LUX,
    colorKelvin: 7500,
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
  // the baked fold: one glTF animation (index 0) that turns every panel about its hinge
  const animator = useAnimator(asset);
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
    const panels: (Entity | null)[] = [];
    for (let i = 0; i < HINGE_N; i++) panels.push(asset.getFirstEntityByName(HINGE_NAMES[i]) ?? null);
    return { root: asset.getFirstEntityByName("Plane") ?? null, panels };
  }, [asset]);

  const aspect = width / height;
  const configured = useFilamentSharedValue(0);
  const lastFold = useFilamentSharedValue(-1);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    const g = geoF.value;
    if (g == null || g.length < 8) return;
    // metres per point: the sheet is g[G_PT] wide on screen
    const m = SHEET_L / g[G_PT];
    const stamp = aspect * 1000 + g[G_H] + g[G_PT] * 0.001;
    if (configured.value !== stamp) {
      // straight at the plane z = 0 from the distance where the screen's height is g[G_H] points
      (camera as CameraWithFovDirection).setProjection(VFOV_DEG, aspect, CAMERA_NEAR, CAMERA_FAR, "vertical");
      camera.lookAt([0, 0, (g[G_H] * m) / 2 / TAN_HALF], [0, 0, 0], [0, 1, 0]);
      configured.value = stamp;
    }
    const an = anchorF.value;
    const tt = an != null && an.length >= 2 && an[0] === 1 ? filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP) : tF.value;

    const s = setup;
    if (s == null || s.root == null) return;
    // outside the 3D part of the film the plane is parked at no size (the layer above it is transparent then)
    if (tt < PHASE.liftEnd - 0.1 || tt > PHASE.flightEnd + 0.1) {
      transformManager.setEntityScale(s.root, [0.0001, 0.0001, 0.0001], false);
      return;
    }

    // the fold: written only when its time changes
    const ft = foldTime(tt);
    if (ft !== lastFold.value) {
      lastFold.value = ft;
      if (USE_ANIMATOR && animator != null) {
        animator.applyAnimation(0, ft);
        animator.updateBoneMatrices();
      } else {
        for (let i = 0; i < HINGE_N; i++) {
          const e = s.panels[i];
          if (e == null) continue;
          const o = i * 9;
          const k = easeInOutCubic(seg(ft, HINGE_TABLE[o + 7], HINGE_TABLE[o + 8]));
          transformManager.setEntityRotation(e, HINGE_TABLE[o + 6] * k, [HINGE_TABLE[o + 3], HINGE_TABLE[o + 4], HINGE_TABLE[o + 5]], false);
          transformManager.setEntityPosition(e, [HINGE_TABLE[o], HINGE_TABLE[o + 1], HINGE_TABLE[o + 2]], true);
        }
      }
    }

    // the pose: scale, then the rotation, then the place on the plane z = 0 (screen points to metres)
    const p = [0, 0, 0, 0, 0, 0];
    poseAt(tt, g, p);
    const r = [0, 0, 1, 0];
    poseRotation(p[2], p[3], p[4], r);
    transformManager.setEntityScale(s.root, [p[5], p[5], p[5]], false);
    transformManager.setEntityRotation(s.root, r[3], [r[0], r[1], r[2]], true);
    transformManager.setEntityPosition(s.root, [(p[0] - g[G_W] / 2) * m, (g[G_H] / 2 - p[1]) * m, 0], true);
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, animator, aspect, camera, configured, geoF, lastFold, setup, tF, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the sheet of paper, see-through over the whole chat. It never runs its own clock; it shows the film
 * at the anchor's time (or at `t`).
 */
export function PlaneStage3D(props: PlaneStage3DProps): JSX.Element {
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
