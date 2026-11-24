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
import { cameraAt, filmPose, knobRad, leanAt, ledAt, LOOP, meterAt, POSE, tanHalfFovY } from "./mixerMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  CHANNELS,
  EXPOSURE,
  FADER_BASE,
  FILL_DIRECTION,
  FILL_LUX,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  KNOB_POS,
  LAMP_ON,
  LAMP_RGB,
  LED_AMBER,
  LED_GREEN,
  LED_ON,
  LED_RED,
  LEDS,
  MUTE_POS,
  MUTE_TRAVEL,
  TARGET,
  TRAVEL,
} from "./mixerScene";
import { useFilamentSharedValueBridge } from "./useFilamentSharedValueBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const MODEL_SOURCE = require("./assets/models/mixer.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/mixer-ibl.ktx");

type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

const Y_AXIS: [number, number, number] = [0, 1, 0];
const ANCHOR_OFF: number[] = [0, -1];
/** LEDs and lamps are written only when their level moved by a step of this size. */
const GLOW_STEPS = 48;
/** Reduced motion's meters: the signal at this moment (both voices and the music up). */
const STILL_T = 2.6;

export type MixerStage3DProps = {
  width: number;
  height: number;
  /** The film's clock: [1 while the film plays, the UI frame time (ms) of its t = 0]. While it plays this stage computes
   * the film itself from its own frame time; otherwise it shows the values below. */
  anchor: SharedValue<number[]>;
  /** A finger's pose (12 numbers: faders, knobs, mutes) and the camera's lean. */
  pose: SharedValue<number[]>;
  lean: SharedValue<number>;
  initialPose: number[];
  /** Reduced motion: the meters stand at one moment of the signal. */
  still: boolean;
  onReady?: () => void;
};

type Setup = {
  faders: (Entity | null)[];
  knobs: (Entity | null)[];
  mutes: (Entity | null)[];
  lamps: (MaterialInstance | null)[];
  /** CHANNELS x LEDS, channel-major. */
  leds: (MaterialInstance | null)[];
};

function Scene(props: MixerStage3DProps): JSX.Element {
  const { width, height, onReady, still } = props;
  const { camera, transformManager, renderableManager, lightManager, scene, engine, workletContext } = useFilamentContext();
  const anchorF = useFilamentSharedValueBridge(props.anchor, ANCHOR_OFF);
  const poseF = useFilamentSharedValueBridge(props.pose, props.initialPose);
  const leanF = useFilamentSharedValueBridge(props.lean, 0);

  const key = useLightEntity(lightManager, { type: "directional", direction: KEY_DIRECTION, intensity: KEY_LUX, colorKelvin: 5000, castShadows: false });
  const fill = useLightEntity(lightManager, { type: "directional", direction: FILL_DIRECTION, intensity: FILL_LUX, colorKelvin: 7200, castShadows: false });
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
    // no light casts a shadow: the desk's contact shadow is a baked decal on the floor
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      renderableManager.setCastShadow(ents[i], false);
      renderableManager.setReceiveShadow(ents[i], false);
    }
    const faders: (Entity | null)[] = [];
    const knobs: (Entity | null)[] = [];
    const mutes: (Entity | null)[] = [];
    const lamps: (MaterialInstance | null)[] = [];
    const leds: (MaterialInstance | null)[] = [];
    for (let c = 0; c < CHANNELS; c++) {
      faders.push(asset.getFirstEntityByName(`Fader${c}`) ?? null);
      knobs.push(asset.getFirstEntityByName(`Knob${c}`) ?? null);
      mutes.push(asset.getFirstEntityByName(`Mute${c}`) ?? null);
      const lens = asset.getFirstEntityByName(`MuteLens${c}`);
      lamps.push(lens != null ? renderableManager.getMaterialInstanceAt(lens, 0) : null);
      for (let i = 0; i < LEDS; i++) {
        const e = asset.getFirstEntityByName(`Led${c}_${i}`);
        leds.push(e != null ? renderableManager.getMaterialInstanceAt(e, 0) : null);
      }
    }
    return { faders, knobs, mutes, lamps, leds };
  }, [asset]);

  const aspect = width / height;
  const configuredAspect = useFilamentSharedValue(0);
  /** What each LED and lamp shows now (in GLOW_STEPS), so a frame writes only what changed. */
  const glow = useFilamentSharedValue<number[]>([]);

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
    const frameMs = filamentFrameMs(frameInfo.timestamp, frameInfo.timeSinceLastFrame);
    // the meters never stop: the film's time while it plays, the stage's own clock otherwise
    const tt = playing ? filmTimeAt(frameMs, an[1], LOOP) : still ? STILL_T : (frameMs / 1000) % LOOP;
    const p = new Array<number>(POSE);
    if (playing) {
      filmPose(tt, p);
    } else {
      const src = poseF.value;
      for (let i = 0; i < POSE; i++) p[i] = src[i];
    }
    const eye = [0, 0, 0];
    cameraAt(playing ? leanAt(tt) : leanF.value, eye);
    camera.lookAt([eye[0], eye[1], eye[2]], TARGET, [0, 1, 0]);

    const s = setup;
    if (s == null) return;
    // a plain copy of what is shown (a Worklets Core array is a proxy), written back at the end
    const shownGlow = glow.value;
    const n = CHANNELS * (LEDS + 1);
    const g: number[] = [];
    for (let i = 0; i < n; i++) g.push(shownGlow.length === n ? shownGlow[i] : -1);
    for (let c = 0; c < CHANNELS; c++) {
      const f = s.faders[c];
      if (f != null) {
        const b = FADER_BASE[c];
        transformManager.setEntityPosition(f, [b[0], b[1], b[2] - p[c] * TRAVEL], false);
      }
      const kn = s.knobs[c];
      if (kn != null) {
        transformManager.setEntityRotation(kn, knobRad(p[4 + c]), Y_AXIS, false);
        transformManager.setEntityPosition(kn, KNOB_POS[c], true);
      }
      const mute = p[8 + c];
      const m = s.mutes[c];
      if (m != null) {
        const mp = MUTE_POS[c];
        transformManager.setEntityPosition(m, [mp[0], mp[1] - mute * MUTE_TRAVEL, mp[2]], false);
      }
      const lampLevel = Math.round((1 - mute) * GLOW_STEPS);
      const li = CHANNELS * LEDS + c;
      const lamp = s.lamps[c];
      if (lamp != null && g[li] !== lampLevel) {
        g[li] = lampLevel;
        const e = (LAMP_ON * lampLevel) / GLOW_STEPS;
        lamp.setFloat3Parameter("emissiveFactor", [LAMP_RGB[0] * e, LAMP_RGB[1] * e, LAMP_RGB[2] * e]);
      }
      const level = meterAt(c, tt, p[c], p[4 + c], mute);
      for (let i = 0; i < LEDS; i++) {
        const idx = c * LEDS + i;
        const mi = s.leds[idx];
        const q = Math.round(ledAt(level, i) * GLOW_STEPS);
        if (mi == null || g[idx] === q) continue;
        g[idx] = q;
        const col = i === LEDS - 1 ? LED_RED : i === LEDS - 2 ? LED_AMBER : LED_GREEN;
        const e = (LED_ON * q) / GLOW_STEPS;
        mi.setFloat3Parameter("emissiveFactor", [col[0] * e, col[1] * e, col[2] * e]);
      }
    }
    glow.value = g;
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configuredAspect, glow, leanF, poseF, setup, still, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/** Filament stage: the studio sweep and the mixing desk. It runs no clock of its own. */
export function MixerStage3D(props: MixerStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={{ enabled: false }}
        antiAliasing="FXAA"
        bloomOptions={{ enabled: true, strength: 0.1, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" }}
        temporalAntiAliasingOptions={{ enabled: false }}
      >
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
