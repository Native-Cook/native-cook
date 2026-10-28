/** A bounded optical response on the existing PNG facets, not a reconstructed 3D gem. */
export const DIAMOND_WGSL = /* wgsl */ `
struct Uniforms { a: vec4f, b: vec4f, c: vec4f };
@group(0) @binding(0) var<uniform> u: Uniforms;
@group(0) @binding(1) var picture: texture_2d<f32>;
@group(0) @binding(2) var pictureSampler: sampler;
struct VertexOutput { @builtin(position) position: vec4f, @location(0) uv: vec2f };
@vertex fn vertexMain(@builtin(vertex_index) index: u32) -> VertexOutput {
  let positions = array<vec2f, 3>(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
  let p = positions[index];
  var out: VertexOutput;
  out.position = vec4f(p, 0.0, 1.0);
  out.uv = vec2f((p.x + 1.0) * 0.5, (1.0 - p.y) * 0.5);
  return out;
}
fn samplePicture(uv: vec2f) -> vec4f {
  if (any(uv < vec2f(0.0)) || any(uv > vec2f(1.0))) { return vec4f(0.0); }
  return textureSampleLevel(picture, pictureSampler, uv, 0.0);
}
fn cross2(a: vec2f, b: vec2f) -> f32 { return a.x * b.y - a.y * b.x; }
fn triangleMask(p: vec2f, a: vec2f, b: vec2f, c: vec2f) -> f32 {
  let ab = b-a; let bc = c-b; let ca = a-c;
  let distance = min(min(cross2(ab,p-a)/length(ab),cross2(bc,p-b)/length(bc)),cross2(ca,p-c)/length(ca));
  return smoothstep(-0.002,0.002,distance);
}
@fragment fn fragmentMain(in: VertexOutput) -> @location(0) vec4f {
  let power = clamp(u.a.z,0.0,1.0);
  let uv = (in.uv-vec2f(0.5))*u.b.zw+vec2f(0.5);
  let base = samplePicture(uv);
  // The actual 512x408 artwork's table/girdle/pavilion junctions, in source UV.
  let sourceSize = vec2f(512.0,408.0);
  let leftTip = vec2f(80.0,169.0)/sourceSize;
  let leftCrown = vec2f(145.0,105.0)/sourceSize;
  let tableLeft = vec2f(197.0,120.0)/sourceSize;
  let tableRight = vec2f(316.0,120.0)/sourceSize;
  let rightCrown = vec2f(366.0,103.0)/sourceSize;
  let rightTip = vec2f(431.0,169.0)/sourceSize;
  let girdleLeft = vec2f(174.0,189.0)/sourceSize;
  let girdleCentre = vec2f(256.0,189.0)/sourceSize;
  let girdleRight = vec2f(338.0,189.0)/sourceSize;
  let tip = vec2f(256.0,341.0)/sourceSize;
  let upperLeft = min(1.0,triangleMask(uv,leftTip,leftCrown,tableLeft)+triangleMask(uv,leftTip,tableLeft,girdleLeft));
  let upperCentre = min(1.0,triangleMask(uv,tableLeft,tableRight,girdleRight)+triangleMask(uv,tableLeft,girdleRight,girdleLeft));
  let upperRight = min(1.0,triangleMask(uv,tableRight,rightCrown,rightTip)+triangleMask(uv,tableRight,rightTip,girdleRight));
  let lowerLeft = triangleMask(uv,leftTip,girdleLeft,tip);
  let centreLeft = triangleMask(uv,girdleLeft,girdleCentre,tip);
  let centreRight = triangleMask(uv,girdleCentre,girdleRight,tip);
  let lowerRight = triangleMask(uv,girdleRight,rightTip,tip);
  let sum = upperLeft+upperCentre+upperRight+lowerLeft+centreLeft+centreRight+lowerRight;
  let table = max(0.0,1.0-sum);
  let total = max(0.001,table+sum);
  let facet = (upperLeft+2.0*upperRight+3.0*upperCentre+4.0*(lowerLeft+centreLeft)+5.0*(centreRight+lowerRight))/total;
  let plane = normalize((table*vec3f(0.0,-0.8,0.6)+upperLeft*vec3f(-0.65,-0.35,0.67)+
    upperCentre*vec3f(0.0,-0.4,0.91)+upperRight*vec3f(0.72,-0.3,0.62)+
    lowerLeft*vec3f(-0.72,0.48,0.5)+centreLeft*vec3f(-0.35,0.65,0.67)+
    centreRight*vec3f(0.36,0.65,0.67)+lowerRight*vec3f(0.72,0.48,0.5))/total);
  let light = normalize(vec3f(u.b.x*0.65+0.25,u.b.y*0.65-0.25,1.0));
  // Parent maps its material sweep to lightX=-1..1, including direct finger scrubbing.
  let sweep = clamp((u.b.x+1.0)*0.5,0.0,1.0);
  let facing = 0.5+0.5*cos(facet*1.72-sweep*6.2831853);
  let response = smoothstep(0.24,0.92,facing)*power*(0.75+0.25*max(0.0,dot(plane,light)));
  let shift = vec2f(1.5,-0.45)*response/max(1.0,u.c.x)*u.b.zw;
  let red = samplePicture(uv+shift);
  let blue = samplePicture(uv-shift);
  let dispersed = vec3f(select(base.r,red.r,red.a>0.001),base.g,select(base.b,blue.b,blue.a>0.001));
  let spectrum = vec3f(0.5)+0.5*cos(vec3f(0.0,2.1,4.2)+facet*1.13-sweep*4.2);
  let tint = vec3f(0.15,0.76,1.0)+spectrum*vec3f(0.52,0.18,0.43);
  var rgb = mix(base.rgb,dispersed,0.7*response);
  rgb = mix(rgb,tint,0.46*response);
  rgb += response*vec3f(0.08,0.15,0.18);
  rgb = clamp(rgb,vec3f(0.0),vec3f(1.0));
  // One contiguous, curved spectral fan below the actual tip. It is not an alpha-edge halo.
  let point = in.uv*u.c.x;
  let dx = point.x-u.c.x*0.5;
  let arcY = u.c.x*(93.5/114.0)+0.0075*dx*dx;
  let normal = (point.y-arcY)/sqrt(1.0+0.000225*dx*dx);
  let q = dx/30.0;
  let taper = exp(-q*q*q*q*1.6);
  let band = exp(-0.5*normal*normal/(1.4*1.4));
  let shoulder = exp(-0.5*normal*normal/(3.2*3.2));
  let spectrumFan = exp(-0.5*pow((vec3f(normal)-vec3f(1.0,0.0,-1.0))/1.25,vec3f(2.0)));
  let fanColour = mix(vec3f(0.30,0.85,1.0),spectrumFan/max(max(spectrumFan.r,spectrumFan.g),max(spectrumFan.b,0.001)),0.8);
  let fanAlpha = power*taper*(0.52*band+0.07*shoulder)*(1.0-base.a);
  return vec4f(rgb*base.a+fanColour*fanAlpha,base.a+fanAlpha);
}
`;
