import * as THREE from "three/webgpu";
import { mix, positionLocal, smoothstep, vec3 } from "three/tsl";

export type SoftBox = {
  size: [number, number];
  at: [number, number, number];
  /** Radiance (linear); above 1 for highlights that read on glossy paint. */
  power: number;
  tint?: [number, number, number];
};

export type StudioOptions = {
  /** Lower hemisphere (the floor bounce), linear RGB. */
  floor: [number, number, number];
  /** Upper hemisphere, linear RGB. */
  sky: [number, number, number];
  /** Where the floor fades into the sky, as [from, to] of the up vector. */
  horizon?: [number, number];
  boxes: SoftBox[];
};

/**
 * A procedural studio environment (PMREM) built from three.js primitives:
 * a two-tone dome and a few emissive soft boxes. No HDR asset, and nothing
 * here touches the `three` module build, so it composes with three/webgpu.
 * Call after the renderer is initialized (from a scene's first update).
 */
export function makeStudio(
  renderer: THREE.WebGPURenderer,
  { floor, sky, horizon = [-0.8, -0.3], boxes }: StudioOptions,
) {
  const env = new THREE.Scene();
  const dome = new THREE.MeshBasicNodeMaterial({ side: THREE.BackSide });
  const up = positionLocal.normalize().y;
  dome.colorNode = mix(
    vec3(...floor),
    vec3(...sky),
    smoothstep(...horizon, up),
  );
  env.add(new THREE.Mesh(new THREE.SphereGeometry(20, 48, 24), dome));
  for (const { size, at, power, tint = [1, 1, 0.97] } of boxes) {
    const m = new THREE.MeshBasicNodeMaterial({ side: THREE.DoubleSide });
    m.colorNode = vec3(power * tint[0], power * tint[1], power * tint[2]);
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(...size), m);
    mesh.position.set(...at);
    mesh.lookAt(0, 0, 0);
    env.add(mesh);
  }
  const pmrem = new THREE.PMREMGenerator(renderer);
  const target = pmrem.fromScene(env, 0.035, 0.1, 100);
  env.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.geometry.dispose();
      (o.material as THREE.Material).dispose();
    }
  });
  pmrem.dispose();
  return target;
}
