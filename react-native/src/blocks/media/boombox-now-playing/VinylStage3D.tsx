import type { JSX } from "react";
import { useMemo } from "react";
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
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSharedValue as useFilamentSharedValue, Worklets } from "react-native-worklets-core";

import { focalFor, LOOP, platterAt, sceneAt, sheenAngle } from "./vinylMotion";
import {
  CAMERA_FAR,
  CAMERA_NEAR,
  EXPOSURE_STOPS,
  IBL_INTENSITY,
  KEY_DIRECTION,
  KEY_LUX,
  RIM_DIRECTION,
  RIM_LUX,
  SHEEN_GLOW,
  TO_KEY,
} from "./vinylScene";
import { useExposure, useFilamentSharedValueBridge } from "./useFilamentBridge";

// Metro and RNF need statically analysable asset modules for bundled GLB / KTX files.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const VINYL_SOURCE = require("./assets/models/vinyl.glb");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const IBL_SOURCE = require("./assets/env/studio-ibl.ktx");

/** The typings omit setProjection's fifth argument; four arguments crash Hermes. */
type CameraWithFovDirection = RNFCamera & {
  setProjection(fov: number, aspect: number, near: number, far: number, direction: "horizontal" | "vertical"): void;
};

export type VinylStage3DProps = {
  width: number;
  height: number;
  /** Stage time (the film's time, or held / eased after a touch) and the ease toward the close (0..1). */
  stage: SharedValue<number>;
  ret: SharedValue<number>;
  /** The record's spin (rad, clockwise as the viewer sees side A). */
  platter: SharedValue<number>;
  /**
   * The film's clock anchor: [1 while the film plays, the UI frame time (ms) of its t = 0 (-1 before it starts), the
   * spin the film adds platterAt(t) to]. It changes only when the film starts, resumes or hands over to a finger.
   */
  anchor: SharedValue<number[]>;
  /** The stage time the screen opens on (the bridge only forwards changes, so it is seeded with it). */
  startT: number;
  onReady?: () => void;
};

type Setup = { record: Entity | null; spin: Entity | null; sheen: Entity | null; sleeve: Entity | null };

/** The film may start once the record has been on screen for this many Filament frames (its textures are up). */
const READY_FRAMES = 3;
/** Fixed view options (module level, so a re-render never re-applies them). */
const AO_OPTIONS = { enabled: false } as const;
const BLOOM_OPTIONS = { enabled: true, strength: 0.04, levels: 5, threshold: true, highlight: 1000, quality: "MEDIUM" } as const;
const TAA_OPTIONS = { enabled: false } as const;

function useLights() {
  const { lightManager, scene } = useFilamentContext();
  // RNF has no shadow bias; the soft contact shadows are drawn in Skia under the transparent view instead
  const key = useLightEntity(lightManager, { type: "directional", direction: KEY_DIRECTION as [number, number, number], intensity: KEY_LUX, colorKelvin: 5200, castShadows: false });
  const rim = useLightEntity(lightManager, { type: "directional", direction: RIM_DIRECTION as [number, number, number], intensity: RIM_LUX, colorKelvin: 7500, castShadows: false });
  useEntityInScene(scene, key);
  useEntityInScene(scene, rim);
}

function Scene(props: VinylStage3DProps): JSX.Element {
  const { width, height, onReady } = props;
  const { camera, transformManager, renderableManager } = useFilamentContext();
  // Filament draws on its own display link. Values handed over from the UI thread every frame arrived late for about
  // one Filament frame in five (that frame drew the old pose, the next jumped two steps: 18 % of the moving frames
  // stalled). While the film plays, the render callback therefore computes the pose itself from its own frame time and
  // the film's clock anchor (which changes rarely); only a finger's record (and the ease back) still comes across
  // as values, in one packed hop a frame instead of three.
  const { stage, ret, platter, anchor, startT } = props;
  const pack = useDerivedValue(() => [stage.get(), ret.get(), platter.get()]);
  const packF = useFilamentSharedValueBridge(pack, [startT, 0, platterAt(startT)]);
  const anchorF = useFilamentSharedValueBridge(anchor, [0, -1, 0]);
  useExposure(EXPOSURE_STOPS);
  useLights();

  const model = useModel(VINYL_SOURCE);
  const asset = model.state === "loaded" ? model.asset : undefined;
  // the film's clock starts when the record is really on screen, not when the file has loaded (its entities and
  // textures reach the GPU a few frames later; starting at load made the first slide jump)
  const readyJS = useMemo(() => Worklets.createRunOnJS(() => onReady?.()), [onReady]);
  const drawn = useFilamentSharedValue(0);

  const setup = useWorkletMemo<Setup | null>(() => {
    "worklet";
    if (asset == null) return null;
    const s: Setup = {
      record: asset.getFirstEntityByName("Record") ?? null,
      spin: asset.getFirstEntityByName("Spin") ?? null,
      sheen: asset.getFirstEntityByName("SheenSpin") ?? null,
      sleeve: asset.getFirstEntityByName("Sleeve") ?? null,
    };
    const sheenMats: MaterialInstance[] = [];
    const ents = asset.getRenderableEntities();
    for (let i = 0; i < ents.length; i++) {
      const e = ents[i];
      renderableManager.setCastShadow(e, false);
      renderableManager.setReceiveShadow(e, false);
      let n = 0;
      try {
        n = renderableManager.getPrimitiveCount(e);
      } catch {
        continue;
      }
      for (let p = 0; p < n; p++) {
        const mi = renderableManager.getMaterialInstanceAt(e, p);
        // RNF registers the name as a getter called `getName` (its typings omit it)
        if ((mi as unknown as { getName: string }).getName === "Sheen") sheenMats.push(mi);
      }
    }
    for (let i = 0; i < sheenMats.length; i++) sheenMats[i].setFloat3Parameter("emissiveFactor", [SHEEN_GLOW, SHEEN_GLOW, SHEEN_GLOW]);
    return s;
  }, [asset]);

  const aspect = width / height;
  const hfov = (2 * Math.atan(width / 2 / focalFor(width, height)) * 180) / Math.PI;
  const configured = useFilamentSharedValue(0);

  RenderCallbackContext.useRenderCallback((frameInfo: FrameInfo) => {
    "worklet";
    /* eslint-disable react-hooks/immutability -- Worklets Core cells, written on the Filament thread only */
    if (configured.value !== aspect) {
      (camera as CameraWithFovDirection).setProjection(hfov, aspect, CAMERA_NEAR, CAMERA_FAR, "horizontal");
      configured.value = aspect;
    }
    let st = 0;
    let rt = 0;
    let spin = 0;
    const an = anchorF.value;
    if (an[0] > 0.5) {
      // the film: its time for this frame (shown at the next vsync, on the same CADisplayLink clock that stamps the
      // UI thread's frames), through the same pure functions the shadows and the gestures use
      let tt = 0;
      if (an[1] >= 0) {
        const step = frameInfo.timeSinceLastFrame > 0.004 && frameInfo.timeSinceLastFrame < 0.0125 ? 1000 / 120 : 1000 / 60;
        const at = frameInfo.timestamp / 1e6 + step;
        tt = (((at - an[1]) / 1000) % LOOP + LOOP) % LOOP;
      }
      st = tt;
      spin = an[2] + platterAt(tt);
    } else {
      const v = packF.value;
      st = v[0];
      rt = v[1];
      spin = v[2];
    }
    const sc = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    sceneAt(st, rt, sc);
    camera.lookAt([sc[8], sc[9], sc[10]], [sc[11], sc[12], sc[13]], [0, 1, 0]);
    const s = setup;
    if (s == null) return;
    if (drawn.value < READY_FRAMES) {
      drawn.value += 1;
      if (drawn.value === READY_FRAMES) readyJS();
    }
    // the record's whole body: its yaw about the upright axis, then its place
    if (s.record != null) {
      transformManager.setEntityRotation(s.record, sc[3], [0, 1, 0], false);
      transformManager.setEntityPosition(s.record, [sc[0], sc[1], sc[2]], true);
    }
    // the spin on its own axis, clockwise as the viewer sees side A: a negative angle about +z
    if (s.spin != null) transformManager.setEntityRotation(s.spin, -spin, [0, 0, 1], false);
    // the groove highlight stays with the light and the eye, not with the record's spin
    if (s.sheen != null) transformManager.setEntityRotation(s.sheen, sheenAngle(sc, TO_KEY), [0, 0, 1], false);
    if (s.sleeve != null) {
      transformManager.setEntityRotation(s.sleeve, sc[7], [0, 1, 0], false);
      transformManager.setEntityPosition(s.sleeve, [sc[4], sc[5], sc[6]], true);
    }
    /* eslint-enable react-hooks/immutability */
  }, [anchorF, aspect, camera, configured, drawn, hfov, packF, readyJS, setup, transformManager]);

  return <EnvironmentalLight intensity={IBL_INTENSITY} source={IBL_SOURCE} />;
}

/**
 * Filament stage: the record and its sleeve in a clean studio. It has no clock of its own; it shows the stage time it
 * is given (the same pure functions as the shadows and the gestures), so nothing can drift. The view is see-through:
 * the studio paper is a gradient behind it, with the Skia contact shadows on it.
 */
export function VinylStage3D(props: VinylStage3DProps): JSX.Element {
  const { width, height } = props;
  const style = useMemo(() => ({ width, height }), [width, height]);
  return (
    <View pointerEvents="none" style={style}>
      <FilamentScene
        ambientOcclusionOptions={AO_OPTIONS}
        bloomOptions={BLOOM_OPTIONS}
        // No TAA: with a moving camera it dropped whole meshes for single frames on this device; FXAA instead.
        antiAliasing="FXAA"
        temporalAntiAliasingOptions={TAA_OPTIONS}
      >
        <FilamentView enableTransparentRendering style={style}>
          <Scene {...props} />
        </FilamentView>
      </FilamentScene>
    </View>
  );
}
