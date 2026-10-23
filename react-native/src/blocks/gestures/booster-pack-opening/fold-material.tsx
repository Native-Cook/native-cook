import {
  ImageShader,
  Shader,
  Skia,
  type SkImage,
  type SkPoint,
  Vertices,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { PREVIEW_MESH_COLUMNS, PREVIEW_FOIL_MESH_INDICES, PREVIEW_MESH_ROWS } from './geometry';
import { clamp, type Rect } from './model';

// Constant indices keep the material compatible with Skia's ES2 backend.
const SURFACE_LOOKUP = Array.from({ length: PREVIEW_MESH_COLUMNS - 1 }, (_, index) =>
  `if (column <= ${index + 1}.0) return mix(foldSurface[${index}], foldSurface[${index + 1}], column - ${index}.0);`,
).join('\n');
const CROSS_LOOKUP = SURFACE_LOOKUP.replaceAll('foldSurface', 'foldCross');

const FOLD_SHADER = `
uniform shader artwork;
uniform float4 packRect;
// Maximum projected compression, bent span, swipe direction, sheen strength.
uniform float4 fold;
// Texture height, compression, first-row stretch, and upper-edge slope.
uniform float4 foldSurface[${PREVIEW_MESH_COLUMNS}];
// Projected distance from the crest at quarter, half and three-quarter rows,
// divided by texture height. Retain the folded cross-section beyond row one.
uniform float3 foldCross[${PREVIEW_MESH_COLUMNS}];

float4 sampleFold(float column) {
  ${SURFACE_LOOKUP}
  return foldSurface[${PREVIEW_MESH_COLUMNS - 1}];
}

float3 sampleCross(float column) {
  ${CROSS_LOOKUP}
  return foldCross[${PREVIEW_MESH_COLUMNS - 1}];
}

half4 main(float2 xy) {
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);
  float u = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
  float column = acos(clamp(1.0-2.0*u, -1.0, 1.0)) / 3.14159265359 * ${PREVIEW_MESH_COLUMNS - 1}.0;
  float4 local = sampleFold(column);
  float v = clamp((xy.y - packRect.y) / max(local.x, 0.001), 0.0, 1.0);
  float d = fold.z > 0.0 ? u : 1.0-u;
  float bent = smoothstep(0.015, 0.17, fold.x);

  // The 2D projection loses the sheet's depth. Continue the bend's lighting
  // softly into its shoulder, as seen at f42–48, without moving the artwork.
  float shoulder = max(0.48, fold.y + 0.12);
  float across = 1.0-smoothstep(0.0, shoulder, d);
  float upper = 1.0-smoothstep(0.12, 0.92, v);
  float shade = bent * across * upper;
  float compression = max(local.y, fold.x * across * 0.48);
  float facing = clamp(1.0-1.45*compression*pow(max(1.0-v, 0.0), 0.45), 0.0, 1.0);

  // A photographed pink softbox is illumination, not printed ink. Remove
  // it only where the fold turns away; print and the unbent panel retain color.
  float purpleInk = smoothstep(0.08, 0.28, print.b-print.g);
  float deglare = max(print.g-0.035, 0.0) * purpleInk * shade * 0.95;
  float3 unlit = max(print-deglare*float3(1.05, 0.95, 0.35),
    print*float3(0.32, 0.08, 0.40));
  float diffuse = 1.0-shade*(0.50+0.16*(1.0-facing));
  float3 lit = unlit * diffuse;

  // A broad satin reflection settles toward the foot of the bend as its
  // compression increases. It follows material UVs, so holding or reversing
  // a gesture holds or reverses the lighting too; there is no light timer.
  float sheenV = 0.60+fold.x*0.14-d*0.38;
  float sheenWidth = 0.20-0.065*fold.x;
  float band = exp(-pow((v-sheenV)/sheenWidth, 2.0));
  float crestCenter = 0.13+0.12*smoothstep(0.20, 0.65, fold.x);
  float crest = exp(-pow((d-crestCenter)/0.12, 2.0));
  float settling = 1.0-smoothstep(0.38, 0.73, fold.x)*0.28;
  float exposure = mix(0.13, 0.52, smoothstep(0.25, 0.68, fold.x));
  float reflection = band*crest*bent*settling*exposure*fold.w;
  lit = mix(lit, float3(0.95, 0.84, 0.80), reflection);

  // A restrained grazing reflection traces the rounded crest. It is inside
  // the material and never produces an emissive outline around the corner.
  float edge = (1.0-smoothstep(0.0, 0.022, v))*across*bent;
  lit += float3(0.10, 0.055, 0.17)*edge*fold.w;

  // At f57–66 the turned laminate starts exposing its purple reverse. This
  // is a narrow surface inside the curved silhouette, with a dark contact
  // crease separating it from the print, rather than a glowing stroke.
  float turned = smoothstep(0.56, 0.72, fold.x);
  float tipTaper = smoothstep(0.0, 0.065, d);
  float shoulderTaper = 1.0-smoothstep(fold.y*0.42, fold.y*0.88+0.001, d);
  float reverseExposure = turned*tipTaper*shoulderTaper;
  float reverseWidth = packRect.z*0.007*reverseExposure;
  // Convert material UV distance into distance normal to the projected edge.
  // Compensation keeps the reverse thin even where the folded mesh compresses.
  float3 cross = sampleCross(column);
  float distance = v < 0.125 ? v*max(local.z, 0.0)
    : v < 0.25 ? mix(0.125*max(local.z, 0.0), cross.x, (v-0.125)*8.0)
    : v < 0.5 ? mix(cross.x, cross.y, (v-0.25)*4.0)
    : v < 0.75 ? mix(cross.y, cross.z, (v-0.5)*4.0)
    : mix(cross.z, 1.0-local.y, (v-0.75)*4.0);
  float edgeDistance = max(distance, 0.0)*local.x/sqrt(1.0+local.w*local.w);
  float acrossReverse = edgeDistance/max(reverseWidth, packRect.z*0.00001);
  float reverseMask = (1.0-smoothstep(0.78, 1.12, acrossReverse))*reverseExposure;
  float outerRidge = 1.0-smoothstep(0.05, 0.45, acrossReverse);
  float3 reverseColor = mix(float3(0.77, 0.40, 1.0),
    float3(0.97, 0.61, 1.0), outerRidge);
  lit = mix(lit, reverseColor, reverseMask);
  float contactCrease = smoothstep(0.85, 1.12, acrossReverse)
    *(1.0-smoothstep(1.20, 1.75, acrossReverse))*reverseExposure;
  lit *= 1.0-contactCrease*0.28;
  return half4(clamp(lit, 0.0, 1.0)*sampled.a, sampled.a);
}
`;

type FoldMaterialProps = {
  image: SkImage;
  pack: Rect;
  vertices: SharedValue<SkPoint[]>;
  textures: SharedValue<SkPoint[]>;
  direction: SharedValue<1 | -1>;
  opacity: SharedValue<number>;
  reducedMotion?: boolean;
};

export function FoldMaterial({
  image, pack, vertices, textures, direction, opacity, reducedMotion = false,
}: FoldMaterialProps) {
  const source = useMemo(() => Skia.RuntimeEffect.Make(FOLD_SHADER), []);
  const uniforms = useDerivedValue(() => {
    const positions = vertices.get();
    const uv = textures.get();
    const swipeDirection = direction.get();
    const foldSurface: number[] = [];
    const foldCross: number[] = [];
    let compression = 0;
    let bentArea = 0;
    let previousU = 0;
    let previousBend = 0;
    for (let column = 0; column < PREVIEW_MESH_COLUMNS; column += 1) {
      const start = column*PREVIEW_MESH_ROWS;
      const end = start+PREVIEW_MESH_ROWS-1;
      const top = positions[start];
      const bottom = positions[end];
      const uvTop = uv[start];
      const uvBottom = uv[end];
      const textureHeight = uvTop && uvBottom ? Math.max(uvBottom.y-uvTop.y, 0.001) : 1;
      const local = top && bottom && textureHeight > 0.001
        ? clamp(1-(bottom.y-top.y)/textureHeight, 0, 0.95) : 0;
      const nextRow = positions[start+1];
      const nextUV = uv[start+1];
      const uvRowHeight = uvTop && nextUV ? nextUV.y-uvTop.y : 0;
      const rowStretch = top && nextRow && uvRowHeight > 0.001
        ? (nextRow.y-top.y)/uvRowHeight : 1;
      const before = positions[Math.max(0, column-1)*PREVIEW_MESH_ROWS];
      const after = positions[Math.min(PREVIEW_MESH_COLUMNS-1, column+1)*PREVIEW_MESH_ROWS];
      const edgeSlope = before && after && Math.abs(after.x-before.x) > 0.001
        ? (after.y-before.y)/(after.x-before.x) : 0;
      const materialU = uvTop ? (uvTop.x-pack.x)/pack.width : previousU;
      const bend = Math.sqrt(local);
      if (column > 0) bentArea += (bend+previousBend)*0.5*Math.abs(materialU-previousU);
      previousU = materialU;
      previousBend = bend;
      compression = Math.max(compression, local);
      foldSurface.push(textureHeight, local, rowStretch, edgeSlope);
      for (const row of [2, 4, 6]) {
        const point = positions[start+row];
        foldCross.push(top && point ? Math.max((point.y-top.y)/textureHeight, 0) : row/8);
      }
    }
    // Integrate the sampled bend using its actual, nonuniform column spacing.
    // A last-bent-column cutoff would make the shadow jump as the fold widens.
    const extent = compression > 0
      ? clamp(2*bentArea/Math.sqrt(compression), 0, 1) : 0;
    return {
      packRect: [pack.x, pack.y, pack.width, pack.height],
      fold: [compression, extent, swipeDirection, reducedMotion ? 0.4 : 1],
      foldSurface,
      foldCross,
    };
  });
  const artwork = <ImageShader image={image} x={pack.x} y={pack.y}
    width={pack.width} height={pack.height} fit="fill" tx="clamp" ty="clamp" />;
  return (
    <Vertices vertices={vertices} textures={textures} indices={PREVIEW_FOIL_MESH_INDICES}
      mode="triangles" opacity={opacity}>
      {source ? <Shader source={source} uniforms={uniforms}>{artwork}</Shader> : artwork}
    </Vertices>
  );
}
