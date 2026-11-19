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
  filmDial,
  filmMacro,
  filmOpen,
  filmYaw,
  lidOf,
  liftOf,
  LOOP,
  tipOf,
} from "./watchMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  CUSHION_Y,
  DIALS,
  EXPOSURE,
  FILL_DIRECTION,
  FILL_LUX,
  HFOV_DEG,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  LID_HINGE,
  LID_OPEN,
  LIFT,
  TILT,
} from "./watchScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/watch.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/watch-studio-ibl.ktx");

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

/** The dials' colours (linear), metalness and roughness, flattened for the worklet: [r, g, b, metal, rough] x 3. */
const DIAL_TABLE: number[] = [];
for (const d of DIALS) {
  const c = linear(d.color);
  DIAL_TABLE.push(c[0], c[1], c[2], d.metal, d.rough);
}

export type WatchStage3DProps = {
  width: number;
  height: number;
  /**
   * The clocks: [1 while the film plays, the UI frame time (ms) of its t = 0, dial home, second-hand seconds, at UI
   * frame time (ms; -1 not set)]. While the film plays this stage computes it itself from its own frame time;
   * otherwise it shows the values below (a finger's, and the eases back), and runs the second hand from the anchor.
   */
  anchor: SharedValue<number[]>;
  t: SharedValue<number>;
  /** The case's state, 0 (closed) .. 1 (lid open) .. 2 (pillow up). */
  open: SharedValue<number>;
  /** The watch's turn (rad): the film's, or the finger's. */
  yaw: SharedValue<number>;
  /** The camera's push to the dial, 0..1. */
  macro: SharedValue<number>;
  /** Dial state: from / to index into DIALS and the blend 0..1. */
  dFrom: SharedValue<number>;
  dTo: SharedValue<number>;
  dProg: SharedValue<number>;
  /** Clock seconds for the second hand. */
  secs: SharedValue<number>;
  onReady?: () => void;
};

const ANCHOR_OFF: number[] = [0, -1, 0, 0, -1];
const DIAL_N = DIALS.length;

type Setup = {
  lid: Entity | null;
  cushion: Entity | null;
  second: Entity | null;
  dial: MaterialInstance | null;
};

function Scene(props: WatchStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const tF = useFilamentSharedValueBridge(props.t, 0);
  const openF = useFilamentSharedValueBridge(props.open, 0);
  const yawF = useFilamentSharedValueBridge(props.yaw, 0);
  const macroF = useFilamentSharedValueBridge(props.macro, 0);
  const fromF = useFilamentSharedValueBridge(props.dFrom, 0);
  const toF = useFilamentSharedValueBridge(props.dTo, 0);
  const progF = useFilamentSharedValueBridge(props.dProg, 0);
  const secsF = useFilamentSharedValueBridge(props.secs, 0);

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
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const d = asset.getFirstEntityByName("Dial");
    return {
      lid: asset.getFirstEntityByName("Lid") ?? null,
      cushion: asset.getFirstEntityByName("Cushion") ?? null,
      second: asset.getFirstEntityByName("HandSecond") ?? null,
      dial: d != null ? renderableManager.getMaterialInstanceAt(d, 0) : null,
    };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  const lastDial = useFilamentSharedValue("");

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configuredAspect.value !== aspect) {
      (camera as CameraWithFovDirection).setProjection(HFOV_DEG, aspect, CAMERA_NEAR, CAMERA_FAR, "horizontal");
      configuredAspect.value = aspect;
    }
    // The film is a pure function of time, so while it plays this thread computes it for its own frame (the same
    // functions the UI thread runs, at the same vsync's time); values handed over between threads arrived a frame late
    // now and then, and the case froze while the glint moved on. A finger's values (and the eases back) still come
    // across; the second hand runs from the anchor's clock.
    const an = anchorF.value;
    const ok = an != null && an.length >= 5;
    const ms = filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame);
    let o = 0;
    let macro = 0;
    let tt = 0;
    let yaw = 0;
    let dFrom = 0;
    let dTo = 0;
    let dProg = 0;
    let secs = 0;
    if (ok && an[0] === 1) {
      tt = filmTimeAt(ms, an[1], LOOP);
      o = filmOpen(tt);
      yaw = filmYaw(tt);
      macro = filmMacro(tt);
      const f = [0, 0, 0];
      filmDial(tt, f);
      dFrom = (an[2] + f[0]) % DIAL_N;
      dTo = (an[2] + f[1]) % DIAL_N;
      dProg = f[2];
      secs = tt;
    } else {
      o = openF.value;
      macro = macroF.value;
      tt = tF.value;
      yaw = yawF.value;
      dFrom = fromF.value;
      dTo = toF.value;
      dProg = progF.value;
      secs = ok && an[4] >= 0 ? an[3] + Math.max(0, ms - an[4]) / 1000 : secsF.value;
    }
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(o, macro, tt, c);
    camera.lookAt([c[0], c[1], c[2]], [c[3], c[4], c[5]], [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    if (s.lid != null) {
      transformManager.setEntityRotation(s.lid, -LID_OPEN * lidOf(o), [1, 0, 0], false);
      transformManager.setEntityPosition(s.lid, LID_HINGE, true);
    }
    if (s.cushion != null) {
      transformManager.setEntityRotation(s.cushion, TILT * tipOf(o), [1, 0, 0], false);
      transformManager.setEntityRotation(s.cushion, yaw, [0, 1, 0], true);
      transformManager.setEntityPosition(s.cushion, [0, CUSHION_Y + LIFT * liftOf(o), 0], true);
    }
    if (s.second != null) {
      const whole = Math.floor(secs);
      const frac = clamp01((secs - whole) / 0.08);
      const ang = ((whole + (1 - Math.pow(1 - frac, 3))) * Math.PI) / 30;
      transformManager.setEntityRotation(s.second, -ang, [0, 1, 0], false);
    }
    // the dial's colour: blended between two dials (written only when it changes)
    if (s.dial != null) {
      const a = Math.round(dFrom) * 5;
      const b = Math.round(dTo) * 5;
      const k = easeInOutSine(clamp01(dProg));
      const dialKey = `${a},${b},${Math.round(k * 400)}`;
      if (dialKey !== lastDial.value) {
        lastDial.value = dialKey;
        const col = [0, 0, 0];
        for (let i = 0; i < 3; i++) col[i] = DIAL_TABLE[a + i] + (DIAL_TABLE[b + i] - DIAL_TABLE[a + i]) * k;
        s.dial.setFloat4Parameter("baseColorFactor", [col[0], col[1], col[2], 1]);
        s.dial.setFloatParameter("metallicFactor", DIAL_TABLE[a + 3] + (DIAL_TABLE[b + 3] - DIAL_TABLE[a + 3]) * k);
        s.dial.setFloatParameter("roughnessFactor", DIAL_TABLE[a + 4] + (DIAL_TABLE[b + 4] - DIAL_TABLE[a + 4]) * k);
      }
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configuredAspect, fromF, lastDial, macroF, openF, progF, secsF, setup, tF, toF, transformManager, yawF]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the walnut case, its pillow and the watch. It never runs its own clock; it shows what the shared
 * values say. The view is see-through over the Skia backdrop.
 */
export function WatchStage3D(props: WatchStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
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
  );
}
