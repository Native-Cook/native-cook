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
import { boxSquash, cameraAt, FLAP_N, flapOpen, labelAt, LOOP, slideX, tanHalfFovY, tapeAt, yawAt } from "./parcelMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  FLAPS,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  LABEL,
  LABEL_YAW,
  TAPE_A,
  TAPE_B,
  TAPE_TOP,
} from "./parcelScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/parcel.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/parcel-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

/** The flap table flattened for the worklet: per flap [pivot xyz, axis xyz, open (radians), sink (m)]. */
const FLAP_NAMES: string[] = FLAPS.map((f) => f.node);
const FLAP_TABLE: number[] = [];
for (const f of FLAPS) FLAP_TABLE.push(f.pivot[0], f.pivot[1], f.pivot[2], f.axis[0], f.axis[1], f.axis[2], (f.open * Math.PI) / 180, f.sink);
const FLAP_STRIDE = 8;
const RAD = Math.PI / 180;
/** A piece that is "not there" is this small (a scale of 0 makes a matrix Filament cannot invert). */
const NONE = 0.0001;
/** The label tips about this axis on its way down (a corner first). */
const TILT_AXIS: [number, number, number] = [0.94, 0, 0.34];

export type ParcelStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while it runs, the UI frame time (ms) of its t = 0]. While it runs this stage computes the
   * time itself from its own frame time; otherwise it shows `t`. */
  anchor: SharedValue<number[]>;
  t: SharedValue<number>;
  /** What `t` holds at mount (the bridge cannot read it during render). */
  initialT: number;
  /** A finger's orbit (deg), added to the film's. */
  orbit: SharedValue<number>;
  onReady?: () => void;
};

const ANCHOR_OFF: number[] = [0, -1];

type Setup = {
  root: Entity | null;
  flaps: (Entity | null)[];
  tapeA: Entity | null;
  tapeTop: Entity | null;
  tapeB: Entity | null;
  label: Entity | null;
};

function Scene(props: ParcelStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const tF = useFilamentSharedValueBridge(props.t, props.initialT);
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
    // no light casts a shadow: the carton's contact shadow is a baked decal that moves with it
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const flaps: (Entity | null)[] = [];
    for (let i = 0; i < FLAP_N; i++) flaps.push(asset.getFirstEntityByName(FLAP_NAMES[i]) ?? null);
    return {
      root: asset.getFirstEntityByName("Box") ?? null,
      flaps,
      tapeA: asset.getFirstEntityByName("TapeA") ?? null,
      tapeTop: asset.getFirstEntityByName("TapeTop") ?? null,
      tapeB: asset.getFirstEntityByName("TapeB") ?? null,
      label: asset.getFirstEntityByName("Label") ?? null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  /** The pose last written (time and orbit): a still scene writes nothing. */
  const lastT = useFilamentSharedValue(-1);
  const lastOrbit = useFilamentSharedValue(0);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      const fovY = (2 * Math.atan(tanHalfFovY(aspect)) * 180) / Math.PI;
      (camera as CameraWithFovDirection).setProjection(fovY, aspect, CAMERA_NEAR, CAMERA_FAR, "vertical");
      configuredAspect.value = aspect;
      lastT.value = -1;
    }
    // The film is a pure function of time: while it plays this thread computes it for its own frame (the same
    // functions the UI thread runs, at the same vsync's time). Otherwise the time comes across (a tap's jump).
    const an = anchorF.value;
    const tt = an != null && an.length >= 2 && an[0] === 1 ? filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP) : tF.value;
    const orbit = orbitF.value;
    const s = setup;
    if (s == null || s.root == null) {
      lastT.value = -1;
      return;
    }
    if (tt === lastT.value && orbit === lastOrbit.value) return;
    lastT.value = tt;
    lastOrbit.value = orbit;

    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(tt, orbit, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    // the carton: its squash, its turn on the floor, its place along the slide (scale, then rotation, then position)
    transformManager.setEntityScale(s.root, [1, boxSquash(tt), 1], false);
    transformManager.setEntityRotation(s.root, yawAt(tt) * RAD, [0, 1, 0], true);
    transformManager.setEntityPosition(s.root, [slideX(tt), 0, 0], true);

    // the flaps: each turns about its hinge; a short one's hinge sinks as it closes
    for (let i = 0; i < FLAP_N; i++) {
      const e = s.flaps[i];
      if (e == null) continue;
      const o = i * FLAP_STRIDE;
      const open = flapOpen(i, tt);
      transformManager.setEntityRotation(e, FLAP_TABLE[o + 6] * open, [FLAP_TABLE[o + 3], FLAP_TABLE[o + 4], FLAP_TABLE[o + 5]], false);
      transformManager.setEntityPosition(e, [FLAP_TABLE[o], FLAP_TABLE[o + 1] - FLAP_TABLE[o + 7] * (1 - open), FLAP_TABLE[o + 2]], true);
    }

    // the tape: three pieces, each scaled along its length from where it starts
    const tp = [0, 0, 0];
    tapeAt(tt, tp);
    if (s.tapeA != null) {
      transformManager.setEntityScale(s.tapeA, [1, Math.max(NONE, tp[0]), 1], false);
      transformManager.setEntityPosition(s.tapeA, TAPE_A, true);
    }
    if (s.tapeTop != null) {
      transformManager.setEntityScale(s.tapeTop, [Math.max(NONE, tp[1]), 1, 1], false);
      transformManager.setEntityPosition(s.tapeTop, TAPE_TOP, true);
    }
    if (s.tapeB != null) {
      transformManager.setEntityScale(s.tapeB, [1, Math.max(NONE, tp[2]), 1], false);
      transformManager.setEntityPosition(s.tapeB, TAPE_B, true);
    }

    // the label: size and squash, its turn on the lid, its tilt on the way down, then its place above the lid
    if (s.label != null) {
      const lb = [0, 0, 0, 0];
      labelAt(tt, lb);
      const size = Math.max(NONE, lb[1]);
      transformManager.setEntityScale(s.label, [size * (1 + lb[3]), size, size * (1 + lb[3])], false);
      transformManager.setEntityRotation(s.label, LABEL_YAW * RAD, [0, 1, 0], true);
      transformManager.setEntityRotation(s.label, lb[2] * RAD, TILT_AXIS, true);
      transformManager.setEntityPosition(s.label, [LABEL[0], LABEL[1] + lb[0], LABEL[2]], true);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configuredAspect, lastOrbit, lastT, orbitF, setup, tF, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the studio floor and the carton. It never runs its own clock; it shows the film at the anchor's
 * time (or at `t`), with the finger's orbit added.
 */
export function ParcelStage3D(props: ParcelStage3DProps): JSX.Element {
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
