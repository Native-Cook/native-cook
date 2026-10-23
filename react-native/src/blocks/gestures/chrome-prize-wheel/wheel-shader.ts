// Analytic chrome/enamel height fields; only wheel-fixed details enter the shutter integration.
import { ASSETS, PEGS, PRIZE_RADIUS, PRIZE_SWAY, PRIZE_TILE, PROFILE } from './wheel-contract';

/** A 72 Hz shutter, limited to 17 degrees even during unusually fast throws. */
export const WHEEL_SHUTTER = 1 / 72;
export const WHEEL_BLUR_CAP = 0.3;
export const WHEEL_BLUR_TAPS = 8;

export const WHEEL_SKSL = `
uniform shader uEnv;
uniform shader uEnvSoft;
uniform shader uPrizes;
uniform float2 uCentre;
uniform float uRadius;
uniform float uPixelRatio;
uniform float uTheta;
uniform float uSpan;
uniform float2 uTilt;
uniform float uEnvTurn;
uniform float uSwayA;
uniform float uSwayB;
uniform float uWinner;
uniform float uLift;
uniform float uGlow;
uniform float uWinnerPrize;
uniform float uWinnerPrizeScale;
uniform float uDim;
uniform float uComet;
uniform float uHubPress;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;
const float SECTOR = PI / 3.0;
const float BAND_INNER = ${PROFILE.bandInner};
const float RING_INNER = ${PROFILE.ringInner};
const float FACE_OUTER = ${PROFILE.faceOuter};
const float WEDGE_INNER = ${PROFILE.wedgeInner};
const float CORNER = ${PROFILE.wedgeCorner};
const float CHANNEL = ${PROFILE.channel};
const float HUB = ${PROFILE.hub};
const float DISC = ${PROFILE.hubDisc};
// The inner bevel rolls inward past the nominal disc edge; dimensions stay within the contract hub.
const float HUB_FACE = DISC-0.010;
const float HUB_CAP = 0.060;
const float HUB_GROOVE = 0.075;
const float KNURL_COUNT = 108.0;
const float PEG_RADIUS = ${PEGS.radius};
const float PEG_SIZE = ${PEGS.size};
const float PEG_PHASE = ${PEGS.phase};
const float PEG_SPACING = ${PEGS.spacing};
const float TILE = ${PRIZE_TILE};
const float PRIZE_R = ${PRIZE_RADIUS};

float sq(float v) { return v*v; }
float2 turn(float2 p, float a) {
  float c = cos(a), s = sin(a);
  return float2(c*p.x-s*p.y, s*p.x+c*p.y);
}
float wrap(float a) { return mod(a+PI, TAU)-PI; }
float aaWidth() { return 0.8 / (uRadius*uPixelRatio); }
float cover(float field) { return 1.0-smoothstep(-aaWidth(), aaWidth(), field); }
float3 focus(float3 rgb, float strength) {
  float grey = dot(rgb, float3(0.2126, 0.7152, 0.0722));
  return mix(rgb, float3(grey), 0.19*uDim*min(strength/0.62,1.0))*(1.0-strength*uDim);
}
// Inputs are screen-space slopes; environment directions use y UP, as in the contract.
float3 surfaceNormal(float2 slope, float z) {
  float3 n = normalize(float3(slope.x, -slope.y, z));
  float cx = cos(uTilt.x), sx = sin(uTilt.x);
  float cy = cos(uTilt.y), sy = sin(uTilt.y);
  n = float3(cx*n.x+sx*n.z, n.y, -sx*n.x+cx*n.z);
  return float3(n.x, cy*n.y-sy*n.z, sy*n.y+cy*n.z);
}
float2 envUV(float3 n) {
  float3 reflected = reflect(float3(0.0, 0.0, -1.0), n);
  reflected.xy = turn(reflected.xy, uEnvTurn);
  return float2(0.5+atan(reflected.x, reflected.z)/TAU,
    0.5-asin(clamp(reflected.y, -1.0, 1.0))/PI);
}
float3 sharpEnv(float3 n) {
  return uEnv.eval(envUV(n)*float2(${ASSETS.env.width}.0, ${ASSETS.env.height}.0)).rgb;
}
float3 softEnv(float3 n) {
  return uEnvSoft.eval(envUV(n)*float2(${ASSETS.envSoft.width}.0, ${ASSETS.envSoft.height}.0)).rgb;
}
float3 chrome(float3 n, float blue) {
  float3 f0 = mix(float3(0.92, 0.93, 0.97), float3(0.10, 0.14, 0.55), blue);
  float fresnel = pow(max(1.0-n.z, 0.0), 5.0);
  // Linear reflected radiance preserves the studio's black horizon and white softboxes.
  return sharpEnv(n)*mix(f0, float3(1.0), fresnel)*1.10;
}
// Rounded intersection of the annulus and the two radial half-planes; continuous across +/-PI.
float wedgeField(float2 p, float ang) {
  float2 q = turn(p, -ang);
  float radial = length(q);
  float3 edges = float3(radial-FACE_OUTER, WEDGE_INNER-radial,
    abs(q.y)*0.866025404-q.x*0.5+CHANNEL*0.5)+CORNER;
  return length(max(edges, 0.0))+min(max(max(edges.x, edges.y), edges.z), 0.0)-CORNER;
}
float2 wedgeGradient(float2 p, float ang) {
  const float e = 0.0008;
  return float2(wedgeField(p+float2(e,0.0),ang)-wedgeField(p-float2(e,0.0),ang),
    wedgeField(p+float2(0.0,e),ang)-wedgeField(p-float2(0.0,e),ang))/(2.0*e);
}
float3 enamel(float2 p, float ang, float field, float winning) {
  float radial = length(p);
  float bevel = 1.0-smoothstep(0.0, 0.027, -field);
  float2 gradient = wedgeGradient(p, ang);
  float3 n = surfaceNormal(gradient*bevel*1.9+p*0.10, 1.0);
  float illumination = max(dot(n, normalize(float3(-0.48,0.62,0.72))), 0.0);
  float radialMix = smoothstep(WEDGE_INNER,FACE_OUTER,radial);
  float3 blue = mix(float3(0.090,0.188,0.69),float3(0.18,0.482,1.0),radialMix);
  float edgeOcclusion = 1.0-0.40*exp(-max(-field,0.0)*65.0);
  float3 rgb = blue*(0.46+0.52*illumination)*edgeOcclusion;
  float fresnel = 0.055+0.36*pow(max(1.0-n.z,0.0),3.0);
  rgb += softEnv(n)*fresnel;
  // Fixed studio sheen crosses every wedge in the same screen direction.
  float sheen = exp(-sq((p.y+0.46+0.33*p.x)/0.19));
  rgb += float3(0.20,0.40,0.74)*sheen*0.25;
  rgb += float3(0.23,0.43,0.76)*bevel*sq(illumination)*(0.30+0.45*winning*uLift);
  // A clear-coat meniscus catches the window just inside the upper-left edge.
  float edgeLight = dot(gradient,normalize(float2(-0.48,-0.62)));
  float inset = max(-field,0.0);
  float glintWidth = max(0.0022,aaWidth()*1.15);
  float edgeGlint = exp(-sq((inset-0.010)/glintWidth))*max(edgeLight,0.0);
  rgb += float3(0.43,0.65,0.92)*edgeGlint*(0.34+0.18*winning*uLift);
  rgb *= 1.0-0.24*max(-edgeLight,0.0)*exp(-sq((inset-0.015)/0.022));
  rgb = focus(rgb, 0.62*(1.0-winning));
  float2 core = p-turn(float2(0.57,0.0),ang);
  float heart = exp(-dot(core,core)*13.0);
  rgb += winning*uGlow*(float3(0.04,0.29,0.48)+float3(0.48,0.75,0.80)*heart);
  return rgb;
}
float swayFor(float index) {
  if (index < 0.5) return uSwayA*${PRIZE_SWAY[0][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[0][1].toFixed(2)};
  if (index < 1.5) return uSwayA*${PRIZE_SWAY[1][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[1][1].toFixed(2)};
  if (index < 2.5) return uSwayA*${PRIZE_SWAY[2][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[2][1].toFixed(2)};
  if (index < 3.5) return uSwayA*${PRIZE_SWAY[3][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[3][1].toFixed(2)};
  if (index < 4.5) return uSwayA*${PRIZE_SWAY[4][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[4][1].toFixed(2)};
  return uSwayA*${PRIZE_SWAY[5][0].toFixed(2)}+uSwayB*${PRIZE_SWAY[5][1].toFixed(2)};
}
float4 prize(float2 p, float ang, float index, float winning) {
  float liftedScale = 1.0+0.07*uLift*winning;
  float2 centre = turn(float2(PRIZE_R*liftedScale,0.0),ang);
  float tileSide = TILE*liftedScale;
  // Undo the top-centre suspension, then the independent centre-based restock scale.
  float2 pivot = centre-float2(0.0,tileSide*0.5);
  float2 q = turn(p-pivot,-swayFor(index))-float2(0.0,tileSide*0.5);
  float restock = mix(1.0,max(uWinnerPrizeScale,0.001),winning);
  float2 uv = q/(tileSide*restock)+0.5;
  if (min(uv.x,uv.y)<0.0 || max(uv.x,uv.y)>1.0) return float4(0.0);
  float2 tileOrigin = float2(mod(index,3.0),floor(index/3.0))*${ASSETS.prizes.tile}.0;
  float4 rgba = uPrizes.eval(tileOrigin+clamp(uv*${ASSETS.prizes.tile}.0,0.5,${ASSETS.prizes.tile - 0.5}));
  rgba.rgb = focus(rgba.rgb,0.62*(1.0-winning));
  return rgba*mix(1.0,uWinnerPrize,winning);
}
float3 ringBase(float2 p, float radial) {
  float2 radialN = p/max(radial,0.00001);
  float3 rgb;
  if (radial>BAND_INNER) {
    // Knurled normals depend on rotation and are shaded inside the shutter pass instead.
    return float3(0.0);
  } else if (radial>RING_INNER) {
    float section = clamp((radial-0.905)/0.050,-0.995,0.995);
    float3 n = surfaceNormal(radialN*section,sqrt(max(1.0-section*section,0.001)));
    rgb = chrome(n,0.0);
    // Two low-weight tangent samples soften only the lathe finish, never the principal reflection.
    float2 tangentN = float2(-radialN.y,radialN.x);
    rgb = rgb*0.90+0.05*(softEnv(surfaceNormal(radialN*section+tangentN*0.13,n.z))
      +softEnv(surfaceNormal(radialN*section-tangentN*0.13,n.z)));
    rgb *= 1.0-0.88*exp(-sq((radial-0.865)/0.0024));
    rgb += float3(0.30,0.33,0.43)*(exp(-sq((radial-0.951)/0.0018))
      +0.65*exp(-sq((radial-0.859)/0.0018)));
    float ang = atan(p.y,p.x);
    float c1 = wrap(ang-(PI+uComet*PI));
    float c2 = -wrap(ang-(PI-uComet*PI));
    float spark = exp(-sq(c1/0.050))+exp(-sq(c2/0.050));
    float tail = exp(-sq((c1+0.095)/0.11))+exp(-sq((c2+0.095)/0.11));
    rgb += float3(0.90,0.85,1.0)*(spark*1.9+tail*0.48)
      *exp(-sq((radial-0.914)/0.022))*max(sin(PI*uComet),0.0);
  } else {
    float wall = smoothstep(FACE_OUTER,RING_INNER,radial);
    rgb = float3(0.024,0.033,0.10)+float3(0.07,0.085,0.23)*wall;
    rgb += softEnv(surfaceNormal(-radialN*1.8,1.0))*0.12*wall;
    rgb *= 1.0-0.42*exp(-(radial-FACE_OUTER)/0.009);
  }
  return focus(rgb,0.45);
}
float3 pegsAndBand(float2 p, float radial, float rotation, float3 base) {
  float ang = atan(p.y,p.x);
  if (radial>BAND_INNER) {
    float notchAngle = (ang-rotation)*KNURL_COUNT;
    // Fade before subpixel sampling or the eight shutter taps can turn grooves into aliases.
    float fade = (1.0-smoothstep(0.35,1.1,KNURL_COUNT*aaWidth()/radial))
      *(1.0-smoothstep(0.012,0.065,abs(uSpan)));
    float2 radialN = p/max(radial,0.00001);
    float2 tangentN = float2(-radialN.y,radialN.x);
    float bevel = smoothstep(BAND_INNER,1.0,radial);
    float knurl = sin(notchAngle+(radial-BAND_INNER)*38.0);
    float3 n = surfaceNormal(radialN*(0.24+bevel*1.2)+tangentN*0.006*knurl*fade,1.0);
    base = chrome(n,1.0)+float3(0.14,0.17,0.33)*exp(-sq((radial-0.996)/0.002));
    // A shallow satin cut remains legible in the dark band without biting into its silhouette.
    float cutLight = 0.55+0.45*max(dot(radialN,normalize(float2(-0.48,-0.62))),0.0);
    base += float3(0.012,0.020,0.040)*knurl*fade*cutLight
      *smoothstep(BAND_INNER,BAND_INNER+0.006,radial)*(1.0-smoothstep(0.991,1.0,radial));
    base = focus(base,0.45);
  }
  // Only the closest peg can cover this pixel (all pegs use identical hemisphere geometry).
  float pegAng = floor((ang-rotation-PEG_PHASE)/PEG_SPACING+0.5)*PEG_SPACING+rotation+PEG_PHASE;
  float2 home = turn(float2(PEG_RADIUS,0.0),pegAng);
  float2 d = p-home;
  float castD = length(d-float2(0.012,0.018));
  base *= 1.0-0.52*exp(-sq(castD/(PEG_SIZE*1.14)));
  float pegD = length(d);
  base *= 1.0-0.52*exp(-sq(pegD/(PEG_SIZE*1.16)));
  if (pegD < PEG_SIZE+aaWidth()) {
    float2 ball = d/PEG_SIZE;
    float3 n = surfaceNormal(ball,sqrt(max(1.0-dot(ball,ball),0.0001)));
    float3 metal = focus(chrome(n,0.0),0.45);
    base = mix(base,metal,cover(pegD-PEG_SIZE));
  }
  return base;
}
float3 faceAt(float2 p, float rotation) {
  float radial = length(p);
  float ang = atan(p.y,p.x);
  float index = mod(floor((ang-rotation+SECTOR*0.5)/SECTOR),6.0);
  float centreAng = rotation+index*SECTOR;
  float winning = abs(index-uWinner)<0.1 ? 1.0 : 0.0;
  float winnerAng = rotation+uWinner*SECTOR;
  float liftedScale = 1.0+0.07*uLift;
  float3 rgb = focus(float3(0.027,0.043,0.18),0.62);
  // The vacant recess remains visible behind a lifted wedge.
  float field = wedgeField(p,centreAng);
  if (field<aaWidth() && (winning<0.5 || uLift<0.001)) {
    rgb = mix(rgb,enamel(p,centreAng,field,winning),cover(field));
  }
  if (uWinner>=0.0 && uLift>0.001) {
    float shadowField = wedgeField((p-float2(0.018,0.031)*uLift)/liftedScale,winnerAng);
    float shadow = 1.0-smoothstep(-0.005,0.022+uLift*0.023,shadowField);
    rgb *= 1.0-shadow*0.67*min(uLift,1.0);
    float raisedD = wedgeField(p/liftedScale,winnerAng)*liftedScale;
    float spill = exp(-max(raisedD,0.0)/(0.012+0.02*uGlow));
    rgb += float3(0.025,0.17,0.28)*uGlow*spill;
    // A small lower/right extrusion separates the polished top from its cast shadow.
    float sideD = wedgeField((p-float2(0.002,0.011)*uLift)/liftedScale,winnerAng)*liftedScale;
    rgb = mix(rgb,float3(0.027,0.11,0.34)+uGlow*float3(0.03,0.21,0.35),cover(sideD));
    if (raisedD<aaWidth()) {
      rgb = mix(rgb,enamel(p/liftedScale,winnerAng,raisedD/liftedScale,1.0),cover(raisedD));
    }
  }
  rgb *= 1.0-0.55*exp(-max(FACE_OUTER-radial,0.0)*70.0)*(1.0-winning*uLift);
  // Check all six AABBs: an upright/swaying tile can cross its wedge's angular boundary.
  for (int i=0; i<6; ++i) {
    float f = float(i);
    float isWinner = abs(f-uWinner)<0.1 ? 1.0 : 0.0;
    if (isWinner<0.5) {
      float4 decal = prize(p,rotation+f*SECTOR,f,0.0);
      rgb = decal.rgb+rgb*(1.0-decal.a);
    }
  }
  if (uWinner>=0.0) {
    float4 decal = prize(p,winnerAng,uWinner,1.0);
    rgb = decal.rgb+rgb*(1.0-decal.a);
  }
  // Hub's contact and directional cast shadows lie over the enamel and decals.
  float underHub = length(p-float2(0.008,0.018));
  rgb *= 1.0-0.70*(1.0-smoothstep(HUB-0.012,HUB+0.040,underHub));
  return rgb;
}
float3 hubAt(float2 p, float radial) {
  float2 radialN = p/max(radial,0.00001);
  if (radial>HUB_FACE) {
    float section = clamp((radial-(HUB_FACE+HUB)*0.5)/((HUB-HUB_FACE)*0.5),-0.995,0.995);
    float3 rgb = chrome(surfaceNormal(radialN*section,sqrt(max(1.0-section*section,0.001))),0.0);
    rgb *= 1.0-0.72*exp(-sq((radial-HUB_FACE-0.005)/max(0.0018,aaWidth())));
    rgb += float3(0.22,0.24,0.30)*exp(-sq((radial-HUB+0.003)/max(0.0015,aaWidth())));
    return focus(rgb,0.62);
  }
  float3 n = surfaceNormal(p*(0.35+uHubPress*0.3),1.0);
  float3 room = mix(softEnv(n),sharpEnv(n),0.12);
  float a = atan(p.y,p.x)+uTilt.x*3.0+uTilt.y*2.0-uEnvTurn;
  float bow = pow(max(0.5+0.5*cos(2.0*(a+0.78)),0.0),1.25+uHubPress*1.65);
  // Turned aluminium: opposing silver fans and dark fans, separate from the mirrored bevel.
  float3 rgb = room*float3(0.30,0.31,0.34)
    +mix(float3(0.065,0.075,0.105),float3(0.69,0.72,0.80),bow);
  // Fine radial brushing is filtered near the centre to avoid a pinwheel alias.
  float brushFade = smoothstep(0.02,0.12,radial)*(1.0-smoothstep(0.7,4.0,aaWidth()*180.0/max(radial,0.01)));
  rgb += (sin(a*180.0)+0.5*sin(a*293.0))*0.010*brushFade;
  rgb += sin(radial*3400.0)*0.008*(1.0-smoothstep(0.7,2.2,aaWidth()*3400.0));
  float grooveWidth = max(0.0014,aaWidth());
  rgb *= 1.0-0.72*exp(-sq((radial-HUB_GROOVE)/grooveWidth));
  rgb += float3(0.13,0.15,0.20)*exp(-sq((radial-HUB_GROOVE-0.0028)/grooveWidth));
  // The central polished dome uses the same upright environment as the orbiting mirror balls.
  rgb *= 1.0-0.48*exp(-sq((radial-HUB_CAP)/0.004));
  if (radial<HUB_CAP+aaWidth()) {
    float2 capXY = p/HUB_CAP;
    float capCurve = 0.88+0.045*uHubPress;
    float3 capN = surfaceNormal(capXY*capCurve,sqrt(max(1.0-dot(capXY,capXY)*sq(capCurve),0.001)));
    float3 cap = chrome(capN,0.0);
    rgb = mix(rgb,cap,cover(radial-HUB_CAP));
  }
  // Both face and cap descend; the fixed bevel occludes a wider inner rim as the spring compresses.
  rgb *= (1.0-0.14*uHubPress)*(1.0-(0.32+0.38*uHubPress)
    *exp(-max(HUB_FACE-radial,0.0)/(0.003+uHubPress*0.010)));
  return focus(rgb,0.62);
}
float3 movingAt(float2 p, float radial, float rotation, float3 base) {
  if (radial>RING_INNER) base = pegsAndBand(p,radial,rotation,base);
  // A raised top can overhang the inner chrome lip; do not clip it to the recessed face.
  if (radial>FACE_OUTER) {
    if (uLift<0.001 || uWinner<0.0 || radial>FACE_OUTER*(1.0+0.07*uLift)+aaWidth()) return base;
    float raised = wedgeField(p/(1.0+0.07*uLift),rotation+uWinner*SECTOR)*(1.0+0.07*uLift);
    if (raised>aaWidth()) return base;
    return mix(base,faceAt(p,rotation),cover(raised));
  }
  return faceAt(p,rotation);
}
half4 main(float2 xy) {
  float2 p = (xy-uCentre)/uRadius;
  // Orthographic plane foreshortening is subtle at the contract's small tilt angles.
  p /= float2(cos(uTilt.x),cos(uTilt.y));
  float radial = length(p);
  float alpha = cover(radial-1.0);
  if (alpha<=0.0) return half4(0.0);
  if (radial<HUB-aaWidth()) return half4(hubAt(p,radial),1.0);
  float3 base = radial>FACE_OUTER ? ringBase(p,radial) : float3(0.0);
  float3 rgb;
  // Uniform early exit: at rest the eight-tap loop is not entered at all.
  if (abs(uSpan)*uRadius*uPixelRatio < 0.333333) {
    rgb = movingAt(p,radial,uTheta,base);
  } else {
    rgb = float3(0.0);
    float jitter = fract(52.9829189*fract(dot(floor(xy*uPixelRatio),float2(0.06711056,0.00583715))));
    for (int i=0; i<${WHEEL_BLUR_TAPS}; ++i) {
      float tapAngle = uTheta-uSpan*(float(i)+jitter)/${WHEEL_BLUR_TAPS}.0;
      rgb += movingAt(p,radial,tapAngle,base)/${WHEEL_BLUR_TAPS}.0;
    }
  }
  if (radial<HUB+aaWidth()) rgb = mix(rgb,hubAt(p,radial),cover(radial-HUB));
  return half4(max(rgb,0.0)*alpha,alpha);
}
`;
