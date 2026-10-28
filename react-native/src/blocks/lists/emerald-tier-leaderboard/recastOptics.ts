import { RECAST_SURFACE_ORDER, type RecastFrame } from './recastMotion';

export const RECAST_WIDTH = 393;
export const RECAST_HEIGHT = 335;
export const RECAST_UNIFORM_FLOATS = 36;

const finite = (value: number, fallback = 0) => { 'worklet'; return Number.isFinite(value) ? value : fallback; };
const unit = (value: number) => { 'worklet'; return Math.max(0, Math.min(1, finite(value))); };

/** 144 bytes: motion vec4, four rectangle vec4s, four material vec4s. No image/texture readback. */
export function recastWriteUniforms(frame: RecastFrame, values: Float32Array) {
  'worklet';
  let changed = false;
  const put = (index: number, value: number) => {
    // Compare in float32 units: otherwise JS double -> GPU float rounding causes idle redraws.
    const rounded = Math.fround(value);
    if (values[index] !== rounded) { values[index] = rounded; changed = true; }
  };
  put(0, unit(frame.phase)); put(1, unit(frame.response)); put(2, unit(frame.responsePhase)); put(3, frame.reduced ? 1 : 0);
  for (let i = 0; i < 4; i++) {
    const body = frame.surfaces[RECAST_SURFACE_ORDER[i]];
    const width = Math.max(0, finite(body.width)), height = Math.max(0, finite(body.height));
    put(4 + i * 4, finite(body.x)); put(5 + i * 4, finite(body.y)); put(6 + i * 4, width); put(7 + i * 4, height);
    put(20 + i * 4, Math.max(0, Math.min(finite(body.radius), width / 2, height / 2)));
    put(21 + i * 4, Math.max(0, Math.min(15, finite(body.depth)))); put(22 + i * 4, unit(body.glow)); put(23 + i * 4, 0);
  }
  return changed;
}

/** Copy one immutable cross-runtime packet into the GPU runtime's private staging buffer. */
export function recastCopyUniforms(packet: Float32Array, values: Float32Array) {
  'worklet';
  if (packet.length !== RECAST_UNIFORM_FLOATS) throw new Error('Invalid Rank Recast uniform packet.');
  let changed = false;
  for (let i = 0; i < RECAST_UNIFORM_FLOATS; i++) {
    if (packet[i] !== values[i]) { values[i] = packet[i]; changed = true; }
  }
  return changed;
}

export const RECAST_SKSL = /* sksl */ `
uniform float4 motion;
uniform float2 flags;
uniform float4 rect0; uniform float4 rect1; uniform float4 rect2; uniform float4 rect3;
uniform float4 material0; uniform float4 material1; uniform float4 material2; uniform float4 material3;
float roundedDistance(float2 p,float4 rect,float radius) {
  float2 q=abs(p-rect.xy-rect.zw*.5)-(rect.zw*.5-radius);
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float topMask(float2 p,float4 rect,float depth) {
  if(depth<.01) return 0.0;
  float level=clamp((rect.y-p.y)/max(depth,.01),0.0,1.0);
  float inset=rect.z*(14.0/123.0)*level;
  return smoothstep(rect.x+inset-.55,rect.x+inset+.55,p.x)*(1.0-smoothstep(rect.x+rect.z-inset-.55,rect.x+rect.z-inset+.55,p.x))
    *smoothstep(rect.y-depth-.55,rect.y-depth+.55,p.y)*(1.0-smoothstep(rect.y-.55,rect.y+.55,p.y));
}
float4 over(float4 front,float4 back) { return front+back*(1.0-front.a); }
float3 backdrop(float2 p) {
  // Known procedural emerald page field, never sampled from a native view or a photo.
  float y=clamp(p.y/335.0,0.0,1.0);
  float3 c=mix(float3(.024,.294,.216),float3(.031,.047,.035),smoothstep(0.0,1.0,y));
  return c*(.93+.07*cos(p.x*.012));
}
float4 opticalBody(float2 p,float4 rect,float4 mat) {
  if(rect.z<=0.0||rect.w<=0.0) return float4(0.0);
  if(p.x<rect.x-1.0||p.x>rect.x+rect.z+1.0||p.y<rect.y-mat.y-1.0||p.y>rect.y+rect.w+1.0) return float4(0.0);
  float distance=roundedDistance(p,rect,mat.x);
  float front=1.0-smoothstep(-.55,.55,distance);
  float top=topMask(p,rect,mat.y);
  float coverage=max(front,top);
  if(coverage<=0.0) return float4(0.0);
  float2 uv=(p-rect.xy)/max(rect.zw,float2(.01));
  float edge=1.0-smoothstep(1.0,8.0,max(0.0,-distance));
  float2 radial=(uv-.5)*float2(1.0,.55);
  float2 normal=radial/max(length(radial),.001);
  float2 refracted=p+normal*(2.0+mat.y*.22)*edge;
  float3 sampleColor=backdrop(refracted);
  float band=uv.x+uv.y*.28-mix(-.35,1.55,motion.z);
  float sweep=exp(-band*band/.014)*motion.y;
  float wave=pow(.5+.5*sin(uv.x*10.0+uv.y*5.0+motion.x*6.2831853),5.0);
  float sway=sin(motion.x*3.14159265);
  float movement=sway*sway*(1.0-motion.w);
  float caustic=wave*smoothstep(.58,1.0,uv.y)*(.035*movement+.08*motion.y);
  float alpha=(edge*.065+sweep*.20+caustic+top*(.025+.06*motion.y))*coverage;
  float3 tint=mix(sampleColor,float3(.72,.94,.84),clamp(sweep*2.0+caustic*2.0+top*.35,0.0,1.0));
  return float4(tint*alpha,alpha);
}
float4 body(float2 p,float4 rect,float4 mat) {
  if(rect.z<=0.0||rect.w<=0.0) return float4(0.0);
  if(p.x<rect.x-24.0||p.x>rect.x+rect.z+24.0||p.y<rect.y-mat.y-12.0||p.y>rect.y+rect.w+28.0) return float4(0.0);
  float distance=roundedDistance(p,rect,mat.x);
  float front=1.0-smoothstep(-.55,.55,distance);
  float top=topMask(p,rect,mat.y);
  float moving=sin(motion.x*3.14159265)*(1.0-motion.w);
  float shadowDistance=roundedDistance(p-float2(0.0,3.0+moving*3.0),rect,mat.x);
  float shadow=exp(-pow(max(0.0,shadowDistance)/(5.0+moving*5.0),2.0))*(.24-moving*.06);
  float4 result=float4(float3(.003,.012,.008)*shadow,shadow);
  float2 uv=(p-rect.xy)/max(rect.zw,float2(.01));
  float reservoir=smoothstep(.43,1.0,uv.y);
  float2 rightPoint=(uv-float2(.90,1.06))/float2(.50,.38);
  float2 leftPoint=(uv-float2(.13,1.08))/float2(.62,.36);
  float3 color=float3(.022,.043,.041)+float3(.010,.013,.012)*(1.0-clamp(uv.y,0.0,1.0));
  float energy=flags.x>.5 ? .045 : .14+.30*mat.z;
  color+=reservoir*energy*(float3(.20,.70,.45)*exp(-dot(rightPoint,rightPoint))+float3(.045,.21,.19)*exp(-dot(leftPoint,leftPoint)));
  float grain=fract(sin(dot(floor((p-rect.xy)*1.7),float2(12.9898,78.233)))*43758.5453)-.5;
  color+=grain*.007;
  float rim=1.0-smoothstep(.10,1.15,abs(distance));
  float rimPower=.075+.07*(1.0-clamp(uv.x,0.0,1.0))+smoothstep(.65,1.0,uv.y)*(.08+.16*mat.z);
  color+=float3(.55,.84,.72)*rim*rimPower;
  float alpha=front*(flags.x>.5 ? 1.0 : .985);
  float topLevel=clamp((rect.y-p.y)/max(mat.y,.01),0.0,1.0);
  float3 topColor=mix(float3(.032,.081,.069),float3(.091,.183,.15),topLevel);
  float4 face=over(float4(clamp(color,0.0,1.0)*alpha,alpha),float4(topColor*top,top));
  result=over(face,result);
  if(flags.x<.5&&flags.y>0.0) result=over(opticalBody(p,rect,mat)*flags.y,result);
  return result;
}
half4 main(float2 p) {
  float4 result=float4(0.0);
  result=over(body(p,rect0,material0),result);
  result=over(body(p,rect1,material1),result);
  result=over(body(p,rect2,material2),result);
  result=over(body(p,rect3,material3),result);
  return half4(result);
}`;

export const RECAST_WGSL = /* wgsl */ `
struct RecastUniforms { motion: vec4f, rects: array<vec4f,4>, materials: array<vec4f,4> };
@group(0) @binding(0) var<uniform> recast: RecastUniforms;
struct Vertex { @builtin(position) position: vec4f, @location(0) uv: vec2f };
@vertex fn recastVertex(@builtin(vertex_index) index:u32)->Vertex {
  let vertices=array<vec2f,3>(vec2f(-1.0,-1.0),vec2f(3.0,-1.0),vec2f(-1.0,3.0));
  let p=vertices[index];var out:Vertex;out.position=vec4f(p,0.0,1.0);out.uv=vec2f((p.x+1.0)*.5,(1.0-p.y)*.5);return out;
}
fn roundedDistance(p:vec2f,rect:vec4f,radius:f32)->f32 {
  let q=abs(p-rect.xy-rect.zw*.5)-(rect.zw*.5-radius);
  return length(max(q,vec2f(0.0)))+min(max(q.x,q.y),0.0)-radius;
}
fn topMask(p:vec2f,rect:vec4f,depth:f32)->f32 {
  if(depth<.01){return 0.0;}
  let level=clamp((rect.y-p.y)/max(depth,.01),0.0,1.0);let inset=rect.z*(14.0/123.0)*level;
  return smoothstep(rect.x+inset-.55,rect.x+inset+.55,p.x)*(1.0-smoothstep(rect.x+rect.z-inset-.55,rect.x+rect.z-inset+.55,p.x))
   *smoothstep(rect.y-depth-.55,rect.y-depth+.55,p.y)*(1.0-smoothstep(rect.y-.55,rect.y+.55,p.y));
}
fn backdrop(p:vec2f)->vec3f {
  let y=clamp(p.y/335.0,0.0,1.0);
  let c=mix(vec3f(.024,.294,.216),vec3f(.031,.047,.035),smoothstep(0.0,1.0,y));
  return c*(.93+.07*cos(p.x*.012));
}
fn optics(p:vec2f,rect:vec4f,mat:vec4f)->vec4f {
  if(rect.z<=0.0||rect.w<=0.0){return vec4f(0.0);}
  if(p.x<rect.x-1.0||p.x>rect.x+rect.z+1.0||p.y<rect.y-mat.y-1.0||p.y>rect.y+rect.w+1.0){return vec4f(0.0);}
  let distance=roundedDistance(p,rect,mat.x);let front=1.0-smoothstep(-.55,.55,distance);let top=topMask(p,rect,mat.y);let coverage=max(front,top);
  if(coverage<=0.0){return vec4f(0.0);}
  let uv=(p-rect.xy)/max(rect.zw,vec2f(.01));let edge=1.0-smoothstep(1.0,8.0,max(0.0,-distance));
  let radial=(uv-.5)*vec2f(1.0,.55);let normal=radial/max(length(radial),.001);let refracted=p+normal*(2.0+mat.y*.22)*edge;let sampleColor=backdrop(refracted);
  let band=uv.x+uv.y*.28-mix(-.35,1.55,recast.motion.z);let sweep=exp(-band*band/.014)*recast.motion.y;
  let wave=pow(.5+.5*sin(uv.x*10.0+uv.y*5.0+recast.motion.x*6.2831853),5.0);
  let sway=sin(recast.motion.x*3.14159265);let movement=sway*sway*(1.0-recast.motion.w);
  let caustic=wave*smoothstep(.58,1.0,uv.y)*(.035*movement+.08*recast.motion.y);
  let alpha=(edge*.065+sweep*.20+caustic+top*(.025+.06*recast.motion.y))*coverage;
  let tint=mix(sampleColor,vec3f(.72,.94,.84),clamp(sweep*2.0+caustic*2.0+top*.35,0.0,1.0));
  return vec4f(tint*alpha,alpha);
}
@fragment fn recastFragment(in:Vertex)->@location(0) vec4f {
  let p=in.uv*vec2f(393.0,335.0);var result=vec4f(0.0);
  // Painter order matches Skia. A foreground body occludes optics on every body behind it.
  for(var i=0;i<4;i++) {
    let rect=recast.rects[i];let mat=recast.materials[i];
    if(rect.z>0.0&&rect.w>0.0) {
      let coverage=max(1.0-smoothstep(-.55,.55,roundedDistance(p,rect,mat.x)),topMask(p,rect,mat.y));
      result=optics(p,rect,mat)+result*(1.0-coverage);
    }
  }
  return result;
}`;
