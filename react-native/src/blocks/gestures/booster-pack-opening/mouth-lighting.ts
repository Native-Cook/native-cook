import type { Point } from './model';

export type PackLightingPose = {
  originX: number;
  originY: number;
  translateX: number;
  translateY: number;
  scale: number;
  /** Rotation in radians, around origin before translation. */
  rotation: number;
};

export type MouthLightingInput = {
  vertices: readonly Point[];
  textures: readonly Point[];
  pose: PackLightingPose;
  opening: number;
  width: number;
  height: number;
  reducedMotion?: boolean;
};

export const MOUTH_LIGHT_SAMPLES = 9;

function finite(value: number, fallback = 0) {
  'worklet';
  return Number.isFinite(value) ? value : fallback;
}

function bounded(value: number, low: number, high: number) {
  'worklet';
  return Math.max(low, Math.min(high, finite(value, low)));
}

function smooth(low: number, high: number, value: number) {
  'worklet';
  const t = bounded((value-low)/(high-low), 0, 1);
  return t*t*(3-2*t);
}

function unit3(x: number, y: number, z: number) {
  'worklet';
  const length = Math.max(Math.hypot(x,y,z), 0.000001);
  return [x/length,y/length,z/length];
}

function transform(point: Point, pose: PackLightingPose) {
  'worklet';
  const ox = finite(pose.originX);
  const oy = finite(pose.originY);
  const x = finite(point.x,ox)-ox;
  const y = finite(point.y,oy)-oy;
  const rotation = finite(pose.rotation);
  const scale = bounded(pose.scale,0,100);
  const c = Math.cos(rotation);
  const s = Math.sin(rotation);
  return {
    x: ox+scale*(c*x-s*y)+finite(pose.translateX),
    y: oy+scale*(s*x+c*y)+finite(pose.translateY),
  };
}

// Columns are not evenly spaced: the geometry reserves many columns for
// folded ends. Interpolate using the supplied UVs, including reversed tears.
function sampleRow(input: MouthLightingInput, u: number, row: number) {
  'worklet';
  const count = Math.floor(Math.min(input.vertices.length,input.textures.length)/5);
  const fallback = { x: finite(input.pose.originX), y: finite(input.pose.originY) };
  if (count === 0) return fallback;
  let nearest = 0;
  let nearestDistance = Infinity;
  for (let column = 0; column < count; column += 1) {
    const uv = finite(input.textures[column*5].x);
    const distance = Math.abs(u-uv);
    if (distance < nearestDistance) {
      nearest = column;
      nearestDistance = distance;
    }
    if (column+1 === count) continue;
    const nextUV = finite(input.textures[(column+1)*5].x);
    if (Math.abs(nextUV-uv) < 0.000001 || u < Math.min(uv,nextUV)
      || u > Math.max(uv,nextUV)) continue;
    const t = (u-uv)/(nextUV-uv);
    const a = input.vertices[column*5+row];
    const b = input.vertices[(column+1)*5+row];
    return {
      x: finite(a.x,fallback.x)+(finite(b.x,fallback.x)-finite(a.x,fallback.x))*t,
      y: finite(a.y,fallback.y)+(finite(b.y,fallback.y)-finite(a.y,fallback.y))*t,
    };
  }
  const point = input.vertices[nearest*5+row];
  return { x: finite(point.x,fallback.x), y: finite(point.y,fallback.y) };
}

/**
 * Deterministic lighting from the raw 56×5 wall and its rendered body pose.
 * The mesh is a 2D projection: opening supplies a calibrated virtual sheet
 * depth to estimate incidence. This is a 2.5D material, not recovered 3D.
 */
export function deriveMouthLighting(input: MouthLightingInput) {
  'worklet';
  const width = Math.max(finite(input.width,1),0.001);
  const height = Math.max(finite(input.height,width),0.001);
  const aspect = height/width;
  const aperture = bounded(finite(input.opening)/width,0,0.25);
  const centers: Point[] = [];
  const depths: Point[] = [];
  let localCenter = { x: 0, y: 0 };
  for (let index = 0; index < MOUTH_LIGHT_SAMPLES; index += 1) {
    const u = index/(MOUTH_LIGHT_SAMPLES-1);
    const middle = sampleRow(input,u,2);
    const rear = transform(sampleRow(input,u,0),input.pose);
    const front = transform(sampleRow(input,u,4),input.pose);
    centers.push(transform(middle,input.pose));
    depths.push({ x: front.x-rear.x, y: front.y-rear.y });
    localCenter = { x: localCenter.x+middle.x/9, y: localCenter.y+middle.y/9 };
  }
  const localStart = sampleRow(input,0.125,2);
  const localEnd = sampleRow(input,0.875,2);
  const direction = localEnd.x >= localStart.x ? 1 : -1;
  const dx = (centers[7].x-centers[1].x)*direction;
  const dy = (centers[7].y-centers[1].y)*direction;
  const chordLength = Math.hypot(dx,dy);
  const roll = chordLength > width*0.000001 ? Math.atan2(dy,dx) : finite(input.pose.rotation);
  const tx = Math.cos(roll);
  const ty = Math.sin(roll);
  const projectedWidth = Math.max(chordLength/0.75,width*0.000001);
  const normalDepths: number[] = [];
  let meanDepth = 0;
  let meanShear = 0;
  let worldX = 0;
  let worldY = 0;
  for (let index = 0; index < MOUTH_LIGHT_SAMPLES; index += 1) {
    const depth = depths[index];
    const h = bounded((-depth.x*ty+depth.y*tx)/projectedWidth,-0.25,0.25);
    normalDepths.push(h);
    meanDepth += h/9;
    meanShear += (depth.x*tx+depth.y*ty)/projectedWidth/9;
    worldX += centers[index].x/width/9;
    worldY += centers[index].y/width/9;
  }
  const bowX = centers[4].x-(centers[1].x+centers[7].x)*0.5;
  const bowY = centers[4].y-(centers[1].y+centers[7].y)*0.5;
  const bow = bounded((-bowX*ty+bowY*tx)/projectedWidth,-0.2,0.2);
  const response = input.reducedMotion ? 0.55 : 1;
  const liningSurface: number[] = [];
  for (let index = 0; index < MOUTH_LIGHT_SAMPLES; index += 1) {
    const a = centers[Math.max(0,index-1)];
    const b = centers[Math.min(MOUTH_LIGHT_SAMPLES-1,index+1)];
    const x = (b.x-a.x)*direction;
    const y = (b.y-a.y)*direction;
    const angle = Math.hypot(x,y) > width*0.000001
      ? Math.atan2(tx*y-ty*x,tx*x+ty*y) : 0;
    // Angular displacement is signed in material-U coordinates. Depth is a
    // dimensionless departure from the mean, so uniform scaling cancels out.
    liningSurface.push(bounded(angle,-0.6,0.6)*direction*response,
      bounded(normalDepths[index]-meanDepth,-0.12,0.12)*response);
  }

  // Fixed finite camera and softbox positions, in viewport-width units.
  // Translation changes their incident directions; no lamp follows the pack.
  const lightX = 0.50-worldX;
  const lightY = aspect*0.38-worldY;
  const lightZ = 1.45;
  const light = unit3(lightX,lightY,lightZ);
  const view = unit3(0.50-worldX,aspect*0.46-worldY,2.8);
  const half = unit3(light[0]+view[0],light[1]+view[1],light[2]+view[2]);
  const lightPitch = Math.atan2(light[1],light[2]);
  const viewPitch = Math.atan2(view[1],view[2]);
  const referenceLightPitch = Math.atan2(aspect*(0.38-0.58),lightZ);
  const referenceViewPitch = Math.atan2(aspect*(0.46-0.58),2.8);
  const openingAngle = Math.atan2(aperture,0.058);
  const incidence = openingAngle+0.22*(lightPitch-referenceLightPitch)
    +0.26*(viewPitch-referenceViewPitch)+0.10*roll+0.15*bow;
  const normal = unit3(-ty*Math.cos(openingAngle),tx*Math.cos(openingAngle),Math.sin(openingAngle));
  const facing = bounded(normal[0]*half[0]+normal[1]*half[1]+normal[2]*half[2],0,1);
  const referenceDistance = lightZ*lightZ+Math.pow(aspect*(0.38-0.58),2);
  const attenuation = bounded(referenceDistance/(lightX*lightX+lightY*lightY+lightZ*lightZ),0.22,1.12);
  const exposed = smooth(0.008,0.031,aperture);
  const exposure = exposed*bounded(0.75+facing*0.50,0,1)*attenuation;
  const physicalCenter = bounded(0.5+0.45*Math.atan2(half[0],half[2])
    -0.18*roll-0.25*bow,0.12,0.88);
  const liningCenter = direction === 1 ? physicalCenter : 1-physicalCenter;
  const white = smooth(Math.atan2(0.036,0.058),Math.atan2(0.049,0.058),incidence);
  const violet = smooth(Math.atan2(0.048,0.058),Math.atan2(0.055,0.058),incidence);
  const tint = [0.86+0.14*white-0.08*violet,1-0.40*violet,1];
  const muted = [0.82,0.80,0.93];
  const liningTint = tint.map((value,index) => input.reducedMotion
    ? value*0.75+muted[index]*0.25 : value);
  const sideAngle = (incidence-Math.atan2(0.040,0.058))/0.068;
  const sideLight = Math.exp(-sideAngle*sideAngle)*exposed*attenuation*response;
  const secondPhysical = bounded(0.79+0.35*Math.atan2(light[0],light[2])
    -0.10*roll,0.08,0.92);
  return {
    center: liningCenter,
    secondCenter: direction === 1 ? secondPhysical : 1-secondPhysical,
    strength: bounded(exposure*(input.reducedMotion ? 0.65 : 1),0,1),
    opening: aperture,
    travel: bounded((worldY*width-localCenter.y)/height,-2,2),
    liningCenter,
    liningLight: bounded(exposure*(input.reducedMotion ? 0.65 : 1),0,1),
    sideLight: bounded(sideLight,0,1),
    liningTilt: bounded((0.025+0.25*roll+0.35*meanShear
      +0.035*(viewPitch-referenceViewPitch))*direction*response,-0.12,0.12),
    liningTint,
    liningSurface,
  };
}
