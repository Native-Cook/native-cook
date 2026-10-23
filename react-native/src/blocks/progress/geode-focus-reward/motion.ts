import { GEOMETRY, type Vec2, type Vec3 } from './geometry';

export const LOOP = 8;
export type PiecePose = { center: Vec3; rotation: Vec3; alpha: number };
export type HeroPose = { p: number; globalScale: number; globalY: number; glowGain: number; coreYaw: number; coreScale: number; manualYaw: number; crack: number; pieces: PiecePose[]; crumbAmount: number };
export type CopyPose = { restAlpha: number; resultAlpha: number[]; resultY: number[] };
export type Frame = { hero: HeroPose; copy: CopyPose };
export type Action = { stamp: number; from: Frame; kind: 'hold' | 'replay' | 'released'; opens: boolean; velocity: number; cancelled: boolean; yawTarget: number };
export type Yaw = { position: number; velocity: number; target: number; stamp: number };
export type Motion = { now: number; epoch: number; press: number | null; captured: Frame; translation: Vec2; yaw: Yaw; action: Action | null; kept: boolean; collapsed: boolean; fractureTicked: boolean };
export function clamp(x: number, lo = 0, hi = 1) { 'worklet'; return Math.min(hi, Math.max(lo, x)); }
export function smooth(x: number) { 'worklet'; const q = clamp(x); return q*q*(3-2*q); }
export function segment(x: number, a: number, b: number) { 'worklet'; return clamp((x-a)/(b-a)); }
export function sm(x: number, a: number, b: number) { 'worklet'; return smooth(segment(x,a,b)); }
export function mix(a: number, b: number, q: number) { 'worklet'; return a+(b-a)*q; }
export function add(a: Vec3,b: Vec3): Vec3 { 'worklet'; return [a[0]+b[0],a[1]+b[1],a[2]+b[2]]; }
export function sub(a: Vec3,b: Vec3): Vec3 { 'worklet'; return [a[0]-b[0],a[1]-b[1],a[2]-b[2]]; }
export function mul(a: Vec3,q: number): Vec3 { 'worklet'; return [a[0]*q,a[1]*q,a[2]*q]; }
export function mix3(a: Vec3,b: Vec3,q: number): Vec3 { 'worklet'; return [mix(a[0],b[0],q),mix(a[1],b[1],q),mix(a[2],b[2],q)]; }
export function dot(a: Vec3,b: Vec3) { 'worklet'; return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
export function cross(a: Vec3,b: Vec3): Vec3 { 'worklet'; return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]; }
export function normalized(a: Vec3): Vec3 { 'worklet'; return mul(a,1/Math.max(1e-12,Math.hypot(a[0],a[1],a[2]))); }
export function bezier(x: number,x1: number,y1: number,x2: number,y2: number) {
  'worklet'; if(x<=0)return 0;if(x>=1)return 1;let lo=0,hi=1;
  for(let i=0;i<18;i++){const u=(lo+hi)*.5,a=1-u,px=3*a*a*u*x1+3*a*u*u*x2+u*u*u;if(px<x)lo=u;else hi=u;}
  const u=(lo+hi)*.5,a=1-u;return 3*a*a*u*y1+3*a*u*u*y2+u*u*u;
}
export function ease(x: number) { 'worklet'; return bezier(x,.2,.8,.2,1); }
export function io(x: number) { 'worklet'; return bezier(x,.65,0,.35,1); }
export function spring(from: number,to: number,velocity: number,time: number,response: number,damping: number) {
  'worklet'; const w=2*Math.PI/response,a=damping*w,b=w*Math.sqrt(1-damping*damping),d=from-to;
  return to+Math.exp(-a*time)*(d*Math.cos(b*time)+(velocity+a*d)*Math.sin(b*time)/b);
}
export function decay(age: number,response: number,damping: number) { 'worklet'; return spring(1,0,0,Math.max(0,age),response,damping); }
export function rotate(p: Vec3,angles: Vec3): Vec3 {
  'worklet'; const y=angles[0]*Math.PI/180,x=angles[1]*Math.PI/180,z=angles[2]*Math.PI/180;
  const a: Vec3=[p[0]*Math.cos(y)+p[2]*Math.sin(y),p[1],-p[0]*Math.sin(y)+p[2]*Math.cos(y)];
  const b: Vec3=[a[0],a[1]*Math.cos(x)-a[2]*Math.sin(x),a[1]*Math.sin(x)+a[2]*Math.cos(x)];
  return [b[0]*Math.cos(z)-b[1]*Math.sin(z),b[0]*Math.sin(z)+b[1]*Math.cos(z),b[2]];
}
export function steady(progress: number): Frame {
  'worklet'; const p=clamp(progress),alpha=1-sm(p,.88,1);
  const pieces=GEOMETRY.pieces.map(piece=>{
    const delay=piece.delay/1.6,q=smooth((segment(p,.18,.94)-delay)/(1-delay)),rotation=mul(piece.finalRotation,q),pivot=sub(piece.pivot,piece.center),hinge=sub(pivot,rotate(pivot,rotation)),peel=1-sm(q,.60,1),end=sm(q,.60,1),radial=normalized([piece.center[0]+.001,piece.center[1],0]),overshoot=5*Math.sin(Math.PI*end)*(1-end);
    return {center:add(add(mix3(piece.center,piece.finalCenter,q),mul(hinge,peel)),mul(radial,overshoot)),rotation,alpha};
  });
  const result=sm(p,.78,.96);
  return {hero:{p,globalScale:1,globalY:0,glowGain:.35+.37*sm(p,.18,1),coreYaw:-8+14*sm(p,.18,1),coreScale:.94+.06*p,manualYaw:0,crack:sm(p,0,.18),pieces,crumbAmount:Math.sin(Math.PI*sm(p,.55,.98))*(1-sm(p,.88,1))},copy:{restAlpha:1-sm(p,.12,.25),resultAlpha:[result,result,result,result],resultY:[10*(1-result),10*(1-result),10*(1-result),10*(1-result)]}};
}
export function rest() { 'worklet'; return steady(0); }
export function reducedFrame() { 'worklet'; const f=steady(1);f.hero.glowGain=.72;f.hero.crumbAmount=0;return f; }
export function authored(time: number): Frame {
  'worklet'; const t=Math.max(0,time)%LOOP;let p=0;
  if(t<1.23)p=0;else if(t<2.60)p=.18*io(segment(t,1.23,2.60));else if(t<3.50)p=mix(.18,.65,ease(segment(t,2.60,3.50)));else if(t<4.20)p=mix(.65,1,ease(segment(t,3.50,4.20)));else if(t<5.50)p=1;else p=1-io(segment(t,5.50,7.10));
  const f=steady(p);
  if(t>=1.10&&t<1.23){const q=ease(segment(t,1.10,1.23));f.hero.globalScale=1-.035*q;f.hero.globalY=2*q;}
  else if(t>=1.23&&t<2.60){const q=sm(t,1.23,2.60);f.hero.globalScale=mix(.965,1,q);f.hero.globalY=2*(1-q);}
  else if(t>=5.50&&t<7.10){const q=sm(t,5.50,7.10);f.hero.globalScale=1-.015*q;f.hero.globalY=q;}
  else if(t>=7.10&&t<7.30){const u=t-7.10,r=decay(u,.25,.82)*(1-smooth(u/.20));f.hero.globalScale=1-.015*r;f.hero.globalY=r;}
  if(t<3.80)f.hero.glowGain=.35;else if(t<4.075)f.hero.glowGain=mix(.35,1,sm(t,3.80,4.075));else if(t<4.35)f.hero.glowGain=mix(1,.72,sm(t,4.075,4.35));else if(t<5.50)f.hero.glowGain=.72;else f.hero.glowGain=mix(.72,.35,sm(t,5.50,7.10));
  f.copy.restAlpha=1-sm(t,2.85,3.25)+sm(t,6.75,7.12);
  for(let i=0;i<4;i++){const q=ease(segment(t,4.05+i*.04,4.36+i*.04))*(1-sm(t,5.50,5.77));f.copy.resultAlpha[i]=q;f.copy.resultY[i]=10*(1-q);}
  return f;
}
export function moved(from: Frame,p: number,residual=1,copyResidual=1): Frame {
  'worklet'; const old=steady(from.hero.p),f=steady(p);
  f.hero.globalScale=1+(from.hero.globalScale-1)*residual;f.hero.globalY=from.hero.globalY*residual;
  f.hero.glowGain=clamp(f.hero.glowGain+(from.hero.glowGain-old.hero.glowGain)*residual,.35,1);
  f.hero.coreYaw+=(from.hero.coreYaw-old.hero.coreYaw)*residual;f.hero.coreScale+=(from.hero.coreScale-old.hero.coreScale)*residual;f.hero.manualYaw=from.hero.manualYaw;
  f.hero.crack=clamp(f.hero.crack+(from.hero.crack-old.hero.crack)*residual);f.hero.crumbAmount=clamp(f.hero.crumbAmount+(from.hero.crumbAmount-old.hero.crumbAmount)*residual);
  for(let i=0;i<12;i++){f.hero.pieces[i].center=add(f.hero.pieces[i].center,mul(sub(from.hero.pieces[i].center,old.hero.pieces[i].center),residual));f.hero.pieces[i].rotation=add(f.hero.pieces[i].rotation,mul(sub(from.hero.pieces[i].rotation,old.hero.pieces[i].rotation),residual));f.hero.pieces[i].alpha=clamp(f.hero.pieces[i].alpha+(from.hero.pieces[i].alpha-old.hero.pieces[i].alpha)*residual);}
  // Preserve Down exactly, then release authored copy lag as progress or hold time advances.
  const edgeDistance=p>=from.hero.p?1-from.hero.p:from.hero.p,copyTravel=edgeDistance>0?Math.abs(p-from.hero.p)/Math.min(.18,edgeDistance):0,copyWeight=residual*copyResidual*(1-smooth(copyTravel));
  f.copy.restAlpha=clamp(f.copy.restAlpha+(from.copy.restAlpha-old.copy.restAlpha)*copyWeight);
  for(let i=0;i<4;i++){f.copy.resultAlpha[i]=clamp(f.copy.resultAlpha[i]+(from.copy.resultAlpha[i]-old.copy.resultAlpha[i])*copyWeight);f.copy.resultY[i]+=(from.copy.resultY[i]-old.copy.resultY[i])*copyWeight;}
  return f;
}
export function held(from: Frame,elapsed: number,translation: Vec2,yawDeflection: number): Frame {
  'worklet'; if(elapsed<=.12&&translation[0]===0&&translation[1]===0&&yawDeflection===0)return from;
  const p=clamp(from.hero.p+.62*Math.max(0,elapsed-.12)-translation[1]/210),f=moved(from,p,1,1-sm(elapsed,.12,.43)),base=clamp(from.hero.manualYaw+.09*translation[0],-12,12),capturedTransient=from.hero.manualYaw-clamp(from.hero.manualYaw,-12,12);
  f.hero.manualYaw=clamp(base+capturedTransient*decay(Math.max(0,elapsed-.12),.4,.8)+yawDeflection,-17,17);return f;
}
export function released(from: Frame,opens: boolean,velocity: number,elapsed: number,cancelled: boolean,yawTarget?: number): Frame {
  'worklet'; const age=Math.max(0,elapsed);if(age===0)return from;
  const target=opens&&!cancelled?1:0,response=target===1?.45:.38,damping=target===1?.78:.85,p=clamp(spring(from.hero.p,target,cancelled?0:clamp(velocity,-1.6,1.6),age,response,damping)),f=moved(from,p,decay(age,response,damping));
  if(cancelled)f.hero.manualYaw=from.hero.manualYaw*(1-sm(age,0,.24));else if(yawTarget!==undefined)f.hero.manualYaw=yawTarget+(from.hero.manualYaw-yawTarget)*decay(age,.4,.8);return f;
}
export function replay(from: Frame,elapsed: number): Frame { 'worklet'; const f=steady(ease(segment(Math.max(0,elapsed),0,1.25)));f.hero.manualYaw=from.hero.manualYaw;return f; }
export function recovering(from: Frame,elapsed: number): Frame {
  'worklet'; if(elapsed<=0)return from;if(elapsed>=.65)return rest();
  const q=io(elapsed/.65),r=rest(),f: Frame={hero:{...from.hero,p:mix(from.hero.p,0,q),globalScale:mix(from.hero.globalScale,1,q),globalY:from.hero.globalY*(1-q),glowGain:mix(from.hero.glowGain,.35,q),coreYaw:mix(from.hero.coreYaw,-8,q),coreScale:mix(from.hero.coreScale,.94,q),manualYaw:from.hero.manualYaw*(1-q),crack:from.hero.crack*(1-q),crumbAmount:from.hero.crumbAmount*(1-q),pieces:from.hero.pieces.map((p,i)=>({center:mix3(p.center,r.hero.pieces[i].center,q),rotation:mix3(p.rotation,[0,0,0],q),alpha:mix(p.alpha,1,q)}))},copy:{restAlpha:mix(from.copy.restAlpha,1,q),resultAlpha:from.copy.resultAlpha.map(a=>a*(1-q)),resultY:from.copy.resultY.map(y=>mix(y,10,q))}};return f;
}
export function yawSpring(from: number,speed: number,to: number,time: number): {position: number; velocity: number} {
  'worklet';if(time<=0)return {position:from,velocity:speed};const omega=2*Math.PI/.4,damping=.8*omega,frequency=omega*Math.sqrt(1-.8*.8),displacement=from-to,sine=(speed+damping*displacement)/frequency,c=Math.cos(frequency*time),s=Math.sin(frequency*time),envelope=Math.exp(-damping*time),offset=displacement*c+sine*s;
  return {position:to+envelope*offset,velocity:envelope*((-displacement*frequency*s+sine*frequency*c)-damping*offset)};
}
export function zeroYaw(): Yaw { 'worklet'; return {position:0,velocity:0,target:0,stamp:-1}; }
export function sampleYaw(yaw: Yaw,now: number) {
  'worklet'; if(yaw.stamp<0)return {position:0,velocity:0};const age=Math.max(0,now-yaw.stamp);
  if(age<=.05)return yawSpring(yaw.position,yaw.velocity,yaw.target,age);const a=yawSpring(yaw.position,yaw.velocity,yaw.target,.05);return yawSpring(a.position,a.velocity,0,age-.05);
}
export function followYaw(yaw: Yaw,target: number,now: number): Yaw { 'worklet'; const p=sampleYaw(yaw,now);return {...p,target,stamp:now}; }
export function sampleAction(action: Action,elapsed: number): Frame { 'worklet'; if(action.kind==='hold')return action.from;if(action.kind==='replay')return replay(action.from,elapsed);return released(action.from,action.opens,action.velocity,elapsed,action.cancelled,action.yawTarget); }
export function drive(m: Motion,autoPlay: boolean,reduced: boolean,previewTime?: number): Frame {
  'worklet';if(reduced)return reducedFrame();if(previewTime!==undefined)return authored(Math.max(0,previewTime));
  if(m.press!==null)return held(m.captured,Math.max(0,m.now-m.press),m.translation,sampleYaw(m.yaw,m.now).position);
  if(m.action){const age=Math.max(0,m.now-m.action.stamp);return age<2?sampleAction(m.action,age):recovering(sampleAction(m.action,2),age-2);}
  return autoPlay?authored(Math.max(0,m.now-m.epoch)):rest();
}
export function hash(value: number) { 'worklet'; let x=(value+0x9E3779B9)>>>0;x=Math.imul(x^(x>>>16),0x7FEB352D)>>>0;x=Math.imul(x^(x>>>15),0x846CA68B)>>>0;return ((x^(x>>>16))>>>0)/4294967295; }
