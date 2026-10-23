import {
  ImageShader,
  Shader,
  Skia,
  type SkImage,
  type SkPoint,
  Vertices,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import {
  type SharedValue,
  useDerivedValue,
} from 'react-native-reanimated';

import { FOIL_MESH_INDICES, MESH_COLUMNS } from './geometry';
import { clamp, interpolateKeyframes, type Rect } from './model';

// These angles describe the out-of-plane rotation that a projected 2D mesh
// cannot retain. The light stays fixed; the turning, twisting foil reflects it.
// In the reference video the reflection reaches the right end at f133, then moves
// toward the middle as that end turns away between f140 and f162.
const MATERIAL_TIMES = [0, 66.667, 116.667, 150, 233.333, 333.333, 433.333, 533.333, 633.333, 733.333, 900, 1000];
const MATERIAL_ROLL = [-1.32, -1.14, -0.88, -0.66, 0.2, 1.02, 1.75, 2.05, 2.3, 2.48, 2.65, 2.8];
const MATERIAL_TWIST = [0.12, 0.5, 1.08, 1.5, 1.85, 2.12, 2.35, 2.58, 2.8, 3.02, 3.25, 3.4];

// Runtime effects also target ES2 devices, which require constant array indices.
const SURFACE_LOOKUP = Array.from({ length: MESH_COLUMNS - 1 }, (_, index) =>
  `if (column <= ${index + 1}.0) return mix(surface[${index}], surface[${index + 1}], column - ${index}.0);`,
).join('\n');

const FOIL_SHADER = `
uniform shader artwork;
uniform float4 packRect;
uniform float4 material;
uniform float pixelScale;
// Per column: signed facing, bending angle, texture height, projected height.
uniform float4 surface[${MESH_COLUMNS}];

float4 sampleSurface(float column) {
  ${SURFACE_LOOKUP}
  return surface[${MESH_COLUMNS - 1}];
}

half4 main(float2 xy) {
  float u = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
  float column = u * ${MESH_COLUMNS - 1}.0;
  float4 local = sampleSurface(column);
  float v = clamp((xy.y - packRect.y) / max(local.z, 0.001), 0.0, 1.0);
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);

  // The material was tuned on a photographed pack with a fixed pink light on
  // its left side. Remove that illumination from the purple substrate, keeping
  // the printed marks and pattern, before applying the changing reflection.
  float purpleInk = smoothstep(0.08, 0.28, print.b - print.g);
  float leftSubstrate = 1.0 - smoothstep(0.32, 0.4, u);
  // The unlit violet ink has little green. Its excess green estimates the
  // additive pink illumination more reliably than painting a dark UV stripe.
  float deglare = max(print.g - 0.025, 0.0)
    * purpleInk * leftSubstrate * material.z;
  float3 unlit = max(
    // Blue clips early in the photographed pink hotspot. Removing as much
    // blue as green would turn the recovered violet ink into a maroon patch.
    print - deglare * float3(1.05, 0.95, 0.35),
    print * float3(0.32, 0.08, 0.4)
  );

  // A softbox reflection is evaluated in foil coordinates, including both the
  // span-wise twist and the cross-strip bend. UVs then carry it through every
  // triangle's rotation, compression and inversion with the printed texture.
  float theta = material.x + (u - 0.5) * material.y
    + (v - 0.56) * 0.44 + local.y * 0.6;
  float3 normal = float3(sin(theta), 0.0, cos(theta));
  float facingLight = max(normal.z, 0.0);
  float broadReflection = pow(facingLight, 16.0);
  float sharpReflection = pow(facingLight, 88.0);
  float lowerReflection = 0.58 + 0.42 * smoothstep(0.1, 0.95, v);
  float reflection = (broadReflection * 0.42 + sharpReflection * 0.76)
    * lowerReflection * pow(abs(local.x), 0.65) * material.w;

  // The signed Jacobian of each mesh column decides which side is visible.
  // Adjacent parts of the strip can show different sides during the twist.
  float front = smoothstep(-0.075, 0.075, local.x);
  float diffuse = 0.69 + 0.22 * abs(local.x) + 0.09 * facingLight;
  float3 frontColor = unlit * diffuse;
  float reverseDetail = dot(unlit, float3(0.2126, 0.7152, 0.0722));
  float3 reverseColor = float3(0.32, 0.012, 0.72)
    * (0.72 + 0.28 * abs(local.x))
    + reverseDetail * float3(0.04, 0.005, 0.07);
  // The thin purple laminate keeps its printed marks visible as it twists.
  // In particular a dark printed panel and silver lettering remain legible on
  // the turned tip; replacing the whole reverse with purple erased that detail.
  float reversePrint = (1.0 - purpleInk) * 0.88
    * (1.0-smoothstep(1.90,2.20,material.x));
  reverseColor = mix(reverseColor, unlit * float3(0.72, 0.62, 0.90), reversePrint);
  float3 base = mix(reverseColor, frontColor, front);

  // The broad reflected light is pink/violet; its center tends toward silver,
  // with a restrained cyan interference fringe as the foil becomes edge-on.
  float interference = 0.5 + 0.5 * sin(theta * 5.0 + v * 2.1);
  float3 reflectionColor = mix(
    float3(1.0, 0.52, 0.93),
    float3(0.68, 0.9, 1.0),
    interference * 0.3
  );
  reflectionColor = mix(reflectionColor, float3(1.0, 0.97, 1.0), sharpReflection);
  float backsideReflection = pow(max(-normal.z, 0.0), 28.0) * 0.055;
  reflection = mix(backsideReflection * material.w, reflection, front);
  float3 lit = base + (1.0 - base) * reflectionColor * min(reflection, 0.94);

  float grazing = pow(1.0 - abs(local.x), 3.0);
  lit += grazing * float3(0.025, 0.014, 0.065) * material.w;

  // Thin colored edge, also in UV space. Its width compensates for projected
  // compression, so a turning strip does not keep an oversized glowing rim.
  float rimWidth = clamp(1.0 * pixelScale / max(local.w, pixelScale), 0.007, 0.1);
  float rim = smoothstep(1.0 - rimWidth, 1.0, v);
  float3 rimColor = mix(float3(0.3, 0.43, 0.83), float3(0.49, 1.0, 1.0), front);
  lit = mix(lit, rimColor, rim * (0.35 + 0.45 * front));
  return half4(clamp(lit, 0.0, 1.0) * sampled.a, sampled.a);
}
`;

type FlyingFoilMaterialProps = {
  image: SkImage;
  pack: Rect;
  vertices: SharedValue<SkPoint[]>;
  textures: SharedValue<SkPoint[]>;
  time: SharedValue<number>;
  opacity: SharedValue<number>;
  width: number;
  height: number;
  reducedMotion?: boolean;
};

export function FlyingFoilMaterial({
  image,
  pack,
  vertices,
  textures,
  time,
  opacity,
  width,
  height,
  reducedMotion = false,
}: FlyingFoilMaterialProps) {
  const source = useMemo(() => Skia.RuntimeEffect.Make(FOIL_SHADER), []);
  const uniforms = useDerivedValue(() => {
    const positions = vertices.get();
    const uv = textures.get();
    const surface: number[] = [];
    const last = Math.min(MESH_COLUMNS, Math.floor(positions.length / 2)) - 1;
    const firstTop = positions[0];
    const firstBottom = positions[1];
    const lastTop = positions[last * 2];
    const lastBottom = positions[last * 2 + 1];
    const globalX = firstTop && firstBottom && lastTop && lastBottom
      ? (lastTop.x + lastBottom.x - firstTop.x - firstBottom.x) * 0.5
      : pack.width;
    const globalY = firstTop && firstBottom && lastTop && lastBottom
      ? (lastTop.y + lastBottom.y - firstTop.y - firstBottom.y) * 0.5
      : 0;

    for (let column = 0; column < MESH_COLUMNS; column += 1) {
      const top = positions[column * 2];
      const bottom = positions[column * 2 + 1];
      const uvTop = uv[column * 2];
      const uvBottom = uv[column * 2 + 1];
      if (!top || !bottom || !uvTop || !uvBottom) {
        surface.push(1, 0, Math.max(pack.height * 0.18, 1), 1);
        continue;
      }
      const before = Math.max(0, column - 1);
      const after = Math.min(last, column + 1);
      const beforeTop = positions[before * 2];
      const beforeBottom = positions[before * 2 + 1];
      const afterTop = positions[after * 2];
      const afterBottom = positions[after * 2 + 1];
      const tangentX = (afterTop.x + afterBottom.x - beforeTop.x - beforeBottom.x) * 0.5;
      const tangentY = (afterTop.y + afterBottom.y - beforeTop.y - beforeBottom.y) * 0.5;
      const tangentLength = Math.max(Math.hypot(tangentX, tangentY), 0.001);
      const edgeX = bottom.x - top.x;
      const edgeY = bottom.y - top.y;
      const textureHeight = Math.max(uvBottom.y - uvTop.y, 0.001);
      const textureSpan = Math.max(uv[after * 2].x - uv[before * 2].x, 0.001);
      const stretch = Math.max(tangentLength / textureSpan, 0.001);
      const signedHeight = (tangentX * edgeY - tangentY * edgeX) / tangentLength;
      const facing = clamp(signedHeight / (textureHeight * stretch), -1, 1);
      const bend = Math.atan2(
        globalX * tangentY - globalY * tangentX,
        globalX * tangentX + globalY * tangentY,
      );
      surface.push(facing, bend, textureHeight, Math.hypot(edgeX, edgeY));
    }

    const t = reducedMotion ? 0 : clamp(time.get(), 0, 1000);
    return {
      packRect: [pack.x, pack.y, pack.width, pack.height],
      material: [
        interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_ROLL),
        interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_TWIST),
        clamp((t - 66.667) / 75, 0, 1),
        reducedMotion ? 0.16 : clamp((t - 50) / 58.333, 0, 1),
      ],
      pixelScale: Math.min(width / 393, height / 852),
      surface,
    };
  });

  const artwork = (
    <ImageShader
      image={image}
      x={pack.x}
      y={pack.y}
      width={pack.width}
      height={pack.height}
      fit="fill"
      tx="clamp"
      ty="clamp"
    />
  );

  return (
    <Vertices
      vertices={vertices}
      textures={textures}
      indices={FOIL_MESH_INDICES}
      mode="triangles"
      opacity={opacity}>
      {source ? <Shader source={source} uniforms={uniforms}>{artwork}</Shader> : artwork}
    </Vertices>
  );
}
