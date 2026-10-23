/** Child effects return LINEAR premultiplied radiance. Only SCENE_SKSL encodes sRGB. */
const CAP_UNIFORMS = `
uniform shader data;
uniform float2 centre;
uniform float scale, tileSize, pxPerPt, travel;
uniform float2 press;
uniform float3 albedoFrom, albedoTo;
uniform float progress;
uniform float2 origin;
uniform float radial;
uniform float3 upper, lower;
uniform float rimGain, innerGain;
uniform float3 led;
uniform float opacity, dim, gloss;
uniform float4 well;
uniform float softGradient;
uniform float2 flood;
uniform float mini;
uniform float3 face;
uniform float faceRows[10];
uniform float3 ink, deboss;
float channel(float2 q, float tile) {
  if (q.x < 0 || q.y < 0 || q.x >= tileSize || q.y >= tileSize) return 0;
  return data.eval(float2(clamp(q.x, 0.5, tileSize - 0.5) + tile * tileSize, clamp(q.y, 0.5, tileSize - 0.5))).r;
}
float3 decodeRGB(float3 c) {
  return float3(c.r <= 0.04045 ? c.r/12.92 : pow((c.r+0.055)/1.055,2.4),
    c.g <= 0.04045 ? c.g/12.92 : pow((c.g+0.055)/1.055,2.4),
    c.b <= 0.04045 ? c.b/12.92 : pow((c.b+0.055)/1.055,2.4));
}
float faceInk(float2 q) {
  float2 cell = floor((q - face.xy) / face.z + float2(6,5));
  if (cell.x < 0 || cell.x >= 12 || cell.y < 0 || cell.y >= 10) return 0;
  float rowBits = 0;
  for (int row=0; row<10; row++) { if (cell.y == float(row)) rowBits = faceRows[row]; }
  return mod(floor(rowBits / pow(2.0,cell.x)),2.0);
}
`;
const CAP_FINISH = `
  float clipped = travel > 0 ? restA : 1;
  float capA = movedA * clipped;
  float gap = travel > 0 ? restA * (1 - movedA) : 0;
  float2 towards = normalize(press) * well.w * pxPerPt;
  float edge = clamp((channel(restQ - towards, softTile) - channel(restQ, softTile)) * softGradient,0,1);
  rgb *= 1 - edge * well.z * max(0,travel);
  if (mini > 0.5) {
    float underInk = faceInk(local - press * travel - deboss.xy);
    rgb = mix(rgb, float3(1), underInk * deboss.z);
    rgb = mix(rgb, ink, faceInk(local - press * travel));
  }
  float3 interior = float3(well.x) + led * well.y * max(0,travel);
  return half4((rgb * capA + interior * gap) * opacity * dim, (capA + gap) * opacity);
}
`;
export const OPAQUE_SKSL = CAP_UNIFORMS + `
half4 main(float2 xy) {
  float2 local = (xy - centre) / scale;
  float2 restQ = local * pxPerPt + tileSize/2;
  float2 q = restQ - press * travel * pxPerPt;
  float restA = channel(restQ,5); float movedA = channel(q,5); float softTile = 6;
  if (restA + movedA <= 0) return half4(0);
  float blend = progress; float leading = 0;
  if (radial > 0.5 && progress > 0 && progress < 1) {
    float reach = length(abs(origin) + float2(tileSize / pxPerPt / 2));
    float radius = progress * (reach + flood.x);
    float delta = length(local - press * travel - origin) - radius;
    blend = 1 - smoothstep(-flood.x/2, flood.x/2, delta);
    leading = (1 - smoothstep(0, flood.x, abs(delta))) * flood.y * (1-progress);
  }
  float3 tint = mix(albedoFrom,albedoTo,blend);
  float3 rgb = tint * (channel(q,0) + channel(q,2)*upper + channel(q,3)*tint*rimGain + channel(q,4)*tint*innerGain)
    + channel(q,1)*gloss + leading;
` + CAP_FINISH;
export const GLASS_SKSL = CAP_UNIFORMS + `
uniform shader picture;
half4 main(float2 xy) {
  float2 local = (xy-centre)/scale;
  float2 restQ = local * pxPerPt + tileSize/2;
  float2 q = restQ - press * travel * pxPerPt;
  float restA = channel(restQ,2); float movedA = channel(q,2); float softTile = 3;
  if (restA + movedA <= 0) return half4(0);
  half4 photo = picture.eval(clamp(q,float2(0.5),float2(tileSize-0.5)));
  float3 rgb = decodeRGB(photo.rgb / max(float(photo.a),0.00001)) + channel(q,0)*upper + channel(q,1)*lower;
` + CAP_FINISH;

export const SCENE_SKSL = `
uniform shader wellData;
uniform shader cap0; uniform shader cap1; uniform shader cap2; uniform shader cap3;
uniform shader mini0; uniform shader mini1; uniform shader mini2; uniform shader mini3; uniform shader mini4; uniform shader mini5;
uniform float u;
uniform float2 centres[4]; uniform float3 colors[4]; uniform float2 gains[4];
uniform float4 minis[6]; uniform float3 miniColors[6]; uniform float fanOrder[6];
uniform float3 scene;
uniform float burst, ring, selected, wellTile, pxPerPt, pageExposure, wellFeather;
uniform float4 page, ringLook; uniform float2 top; uniform float3 miniGlow;
float3 over(float3 below, half4 above) { return above.rgb + below * (1-above.a); }
float3 encodeRGB(float3 c) {
  c = max(c,float3(0));
  return float3(c.r <= 0.0031308 ? c.r*12.92 : 1.055*pow(c.r,1.0/2.4)-0.055,
    c.g <= 0.0031308 ? c.g*12.92 : 1.055*pow(c.g,1.0/2.4)-0.055,
    c.b <= 0.0031308 ? c.b*12.92 : 1.055*pow(c.b,1.0/2.4)-0.055);
}
half4 main(float2 xy) {
  if (xy.y < top.x) return half4(0,0,0,1);
  float d = length(xy-centres[3])/u;
  float f = d/page.x;
  float glow = f < page.w ? mix(page.y*burst,page.z,f/page.w) : mix(page.z,0,clamp((f-page.w)/(1-page.w),0,1));
  float3 rgb = scene * max(0,glow) * pageExposure;
  if (ring > 0) {
    float halo = 1 - smoothstep(0,ringLook.y/2,abs(d-ring*ringLook.x));
    rgb += scene * halo * ringLook.z * pow(max(0,1-ring),ringLook.w);
  }
  for (int i=0; i<4; i++) {
    float2 q = (xy-centres[i])/u*pxPerPt + wellTile/2;
    if (q.x >= 0 && q.y >= 0 && q.x < wellTile && q.y < wellTile) {
      float border = min(min(q.x,q.y),min(wellTile-q.x,wellTile-q.y));
      float feather = smoothstep(0,wellFeather*pxPerPt,border);
      q=clamp(q,float2(0.5),float2(wellTile-0.5));
      rgb += feather * colors[i] * (wellData.eval(q).r*gains[i].x + wellData.eval(q+float2(wellTile,0)).r*gains[i].y);
    }
  }
  for (int i=0; i<6; i++) {
    float md=length(xy-minis[i].xy)/max(0.001,minis[i].w*miniGlow.x);
    float mg=exp(-md*md*4) * minis[i].z * (selected == fanOrder[i] ? miniGlow.z : miniGlow.y);
    rgb += miniColors[i] * mg;
  }
` + [0, 1, 2, 3, 4, 5].map(i => `  if (minis[${i}].z > 0 && all(lessThan(abs(xy-minis[${i}].xy),float2(34*minis[${i}].w)))) rgb=over(rgb,mini${i}.eval(xy));`).join('\n') + `
  if (all(lessThan(abs(xy-centres[0]),float2(84*u)))) rgb=over(rgb,cap0.eval(xy));
  if (all(lessThan(abs(xy-centres[1]),float2(84*u)))) rgb=over(rgb,cap1.eval(xy));
  if (all(lessThan(abs(xy-centres[2]),float2(84*u)))) rgb=over(rgb,cap2.eval(xy));
  if (all(lessThan(abs(xy-centres[3]),float2(84*u)))) rgb=over(rgb,cap3.eval(xy));
  return half4(encodeRGB(rgb * smoothstep(top.x,top.x+top.y,xy.y)),1);
}
`;
