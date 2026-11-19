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
import {
  cameraAt,
  clamp01,
  easeInOutSine,
  filmCarat,
  filmOpen,
  filmShot,
  filmSteps,
  filmYaw,
  LOOP,
  METAL_STEPS,
  STONE_STEPS,
} from "./ringMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  CARAT_MODEL,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  HFOV_DEG,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  LID_HINGE,
  LID_OPEN,
  METALS,
  RING_Y,
  STONE_LOCAL_Y,
  STONES,
} from "./ringScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/ring.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/ring-studio-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

function linear(hex: string): number[] {
  const n = parseInt(hex.slice(1), 16);
  const out = [0, 0, 0];
  for (let i = 0; i < 3; i++) {
    const c = ((n >> (16 - 8 * i)) & 255) / 255;
    out[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }
  return out;
}

/** Metals [r, g, b, rough] x 3 and stones [r, g, b, glow] x 3, linear, flat for the worklet. */
const METAL_TABLE: number[] = [];
for (const m of METALS) {
  const c = linear(m.color);
  METAL_TABLE.push(c[0], c[1], c[2], m.rough);
}
const STONE_TABLE: number[] = [];
for (const s of STONES) {
  const c = linear(s.color);
  STONE_TABLE.push(c[0], c[1], c[2], s.glow);
}
const ANCHOR_OFF: number[] = [0, -1, 0, 0, CARAT_MODEL];
const METAL_N = METALS.length;
const STONE_N = STONES.length;

/** The lid's hinge mirrored in the floor (the reflection's lid turns about it). */
const HINGE_M: [number, number, number] = [LID_HINGE[0], -LID_HINGE[1], LID_HINGE[2]];

export type RingStage3DProps = {
  width: number;
  height: number;
  /**
   * The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0, metal home, stone home, carat
   * home]. While it plays this stage computes the film itself from its own frame time; otherwise it shows the values
   * below (a finger's, and the eases back).
   */
  anchor: SharedValue<number[]>;
  t: SharedValue<number>;
  /** The lid, 0 (shut) .. 1 (open), a little past 1 in the snap's overshoot, a hair below 0 in the press. */
  open: SharedValue<number>;
  /** The camera's shot: 0 the open box, 1 the ring, 2 the macro on the stone. */
  shot: SharedValue<number>;
  /** The box's turn (rad). */
  yaw: SharedValue<number>;
  mFrom: SharedValue<number>;
  mTo: SharedValue<number>;
  mProg: SharedValue<number>;
  sFrom: SharedValue<number>;
  sTo: SharedValue<number>;
  sProg: SharedValue<number>;
  carat: SharedValue<number>;
  onReady?: () => void;
};

type Setup = {
  base: Entity | null;
  lid: Entity | null;
  ring: Entity | null;
  baseM: Entity | null;
  lidM: Entity | null;
  setting: Entity[];
  metal: MaterialInstance[];
  stone: MaterialInstance[];
};

function Scene(props: RingStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const openF = useFilamentSharedValueBridge(props.open, 0);
  const shotF = useFilamentSharedValueBridge(props.shot, 0);
  const yawF = useFilamentSharedValueBridge(props.yaw, 0);
  const mFromF = useFilamentSharedValueBridge(props.mFrom, 0);
  const mToF = useFilamentSharedValueBridge(props.mTo, 0);
  const mProgF = useFilamentSharedValueBridge(props.mProg, 0);
  const sFromF = useFilamentSharedValueBridge(props.sFrom, 0);
  const sToF = useFilamentSharedValueBridge(props.sTo, 0);
  const sProgF = useFilamentSharedValueBridge(props.sProg, 0);
  const caratF = useFilamentSharedValueBridge(props.carat, CARAT_MODEL);

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
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const setting: Entity[] = [];
    const metal: MaterialInstance[] = [];
    const stone: MaterialInstance[] = [];
    const st = asset.getFirstEntityByName("Stone");
    const hd = asset.getFirstEntityByName("Head");
    const gl = asset.getFirstEntityByName("Gallery");
    const bd = asset.getFirstEntityByName("Band");
    if (st != null) {
      setting.push(st);
      stone.push(renderableManager.getMaterialInstanceAt(st, 0));
    }
    if (hd != null) {
      setting.push(hd);
      metal.push(renderableManager.getMaterialInstanceAt(hd, 0));
    }
    if (gl != null) {
      setting.push(gl);
      metal.push(renderableManager.getMaterialInstanceAt(gl, 0));
    }
    if (bd != null) metal.push(renderableManager.getMaterialInstanceAt(bd, 0));
    return {
      base: asset.getFirstEntityByName("Base") ?? null,
      lid: asset.getFirstEntityByName("Lid") ?? null,
      ring: asset.getFirstEntityByName("Ring") ?? null,
      baseM: asset.getFirstEntityByName("BaseM") ?? null,
      lidM: asset.getFirstEntityByName("LidM") ?? null,
      setting,
      metal,
      stone,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const lastMat = useFilamentSharedValue("");
  const lastCarat = useFilamentSharedValue(-1);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      (camera as CameraWithFovDirection).setProjection(HFOV_DEG, aspect, CAMERA_NEAR, CAMERA_FAR, "horizontal");
      configuredAspect.value = aspect;
    }
    // The film is a pure function of time, so while it plays this thread computes it for its own frame (the same
    // functions the UI thread runs, at the same vsync's time); values handed over between threads arrived a frame late
    // now and then, and the box froze while the glints moved on. A finger's values (and the eases back) still come
    // across.
    const an = anchorF.value;
    let o = 0;
    let shot = 0;
    let tt = 0;
    let yaw = 0;
    let ct = CARAT_MODEL;
    let mFrom = 0;
    let mTo = 0;
    let mProg = 0;
    let sFrom = 0;
    let sTo = 0;
    let sProg = 0;
    if (an != null && an.length >= 5 && an[0] === 1) {
      tt = filmTimeAt(filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame), an[1], LOOP);
      o = filmOpen(tt);
      shot = filmShot(tt);
      yaw = filmYaw(tt);
      const f = [0, 0, 0];
      filmSteps(tt, METAL_STEPS, f);
      mFrom = (an[2] + f[0]) % METAL_N;
      mTo = (an[2] + f[1]) % METAL_N;
      mProg = f[2];
      filmSteps(tt, STONE_STEPS, f);
      sFrom = (an[3] + f[0]) % STONE_N;
      sTo = (an[3] + f[1]) % STONE_N;
      sProg = f[2];
      ct = filmCarat(tt, an[4]);
    } else {
      o = openF.value;
      shot = shotF.value;
      tt = tF.value;
      yaw = yawF.value;
      ct = caratF.value;
      mFrom = mFromF.value;
      mTo = mToF.value;
      mProg = mProgF.value;
      sFrom = sFromF.value;
      sTo = sToF.value;
      sProg = sProgF.value;
    }
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(o, shot, tt, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    // the whole box turns about its centre; the lid turns about its hinge first. The reflection (the base and lid
    // shells mirrored in the floor, baked in the model) does the same, mirrored: same turn, the lid the other way.
    if (s.base != null) transformManager.setEntityRotation(s.base, yaw, [0, 1, 0], false);
    if (s.baseM != null) transformManager.setEntityRotation(s.baseM, yaw, [0, 1, 0], false);
    if (s.lid != null) {
      transformManager.setEntityRotation(s.lid, -LID_OPEN * o, [1, 0, 0], false);
      transformManager.setEntityPosition(s.lid, LID_HINGE, true);
      transformManager.setEntityRotation(s.lid, yaw, [0, 1, 0], true);
    }
    if (s.lidM != null) {
      transformManager.setEntityRotation(s.lidM, LID_OPEN * o, [1, 0, 0], false);
      transformManager.setEntityPosition(s.lidM, HINGE_M, true);
      transformManager.setEntityRotation(s.lidM, yaw, [0, 1, 0], true);
    }
    if (s.ring != null) {
      transformManager.setEntityPosition(s.ring, [0, RING_Y, 0], false);
      transformManager.setEntityRotation(s.ring, yaw, [0, 1, 0], true);
    }
    // carat: the stone and its prongs scale about the girdle's centre (cube root of the weight)
    if (Math.abs(ct - lastCarat.value) > 0.0005) {
      lastCarat.value = ct;
      const k = Math.cbrt(ct / CARAT_MODEL);
      for (let i = 0; i < s.setting.length; i++) {
        transformManager.setEntityPosition(s.setting[i], [0, -STONE_LOCAL_Y, 0], false);
        transformManager.setEntityScale(s.setting[i], [k, k, k], true);
        transformManager.setEntityPosition(s.setting[i], [0, STONE_LOCAL_Y, 0], true);
      }
    }
    // metal and stone colours, blended (written only when they change)
    const ma = Math.round(mFrom) * 4;
    const mb = Math.round(mTo) * 4;
    const mk = easeInOutSine(clamp01(mProg));
    const sa = Math.round(sFrom) * 4;
    const sb = Math.round(sTo) * 4;
    const sk = easeInOutSine(clamp01(sProg));
    const matKey = `${ma},${mb},${Math.round(mk * 300)},${sa},${sb},${Math.round(sk * 300)}`;
    if (matKey !== lastMat.value) {
      lastMat.value = matKey;
      const mc = [0, 0, 0, 0];
      const sc = [0, 0, 0, 0];
      for (let i = 0; i < 4; i++) {
        mc[i] = METAL_TABLE[ma + i] + (METAL_TABLE[mb + i] - METAL_TABLE[ma + i]) * mk;
        sc[i] = STONE_TABLE[sa + i] + (STONE_TABLE[sb + i] - STONE_TABLE[sa + i]) * sk;
      }
      for (let i = 0; i < s.metal.length; i++) {
        s.metal[i].setFloat4Parameter("baseColorFactor", [mc[0], mc[1], mc[2], 1]);
        s.metal[i].setFloatParameter("roughnessFactor", mc[3]);
      }
      for (let i = 0; i < s.stone.length; i++) {
        // the base colour tints the stone's mirror reflections; the emissive lights its baked facet pattern
        s.stone[i].setFloat4Parameter("baseColorFactor", [sc[0], sc[1], sc[2], 1]);
        s.stone[i].setFloat3Parameter("emissiveFactor", [sc[0] * sc[3], sc[1] * sc[3], sc[2] * sc[3]]);
      }
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, caratF, configuredAspect, lastCarat, lastMat, mFromF, mProgF, mToF, openF, sFromF, sProgF, shotF, sToF, setup, tF, transformManager, yawF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the velvet box, the ring and the box's reflection under the floor (the Skia floor over the stage
 * fades it out with distance). It never runs its own clock; it shows what the shared values say, so
 * the glints drawn over it (projected with the same camera) stay on the stone.
 */
export function RingStage3D(props: RingStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={{ enabled: false }}
        bloomOptions={{ enabled: true, strength: 0.08, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
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
