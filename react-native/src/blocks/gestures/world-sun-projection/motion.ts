export const LOOP=8;
export type Point={x:number;y:number};
export type Vec=Point&{z:number};
export type Pose={yaw:number;pitch:number;q:number};
export const yawSamples=[-22,-12,0,4,8,15,22],pitchSamples=[-14,-8.5,4,20,34,48,58];
export function rest():Pose{'worklet';return{yaw:0,pitch:-8.5,q:0};}
export function reducedPose():Pose{'worklet';return{yaw:4,pitch:20,q:.5};}
export function clamp(x:number,a=0,b=1){'worklet';return Math.min(b,Math.max(a,x));}
export function smooth(x:number){'worklet';const u=clamp(x);return u*u*(3-2*u);}
export function lerp(a:number,b:number,u:number){'worklet';return a+(b-a)*u;}
export function blend(a:Pose,b:Pose,u:number):Pose{'worklet';return{yaw:lerp(a.yaw,b.yaw,u),pitch:lerp(a.pitch,b.pitch,u),q:lerp(a.q,b.q,u)};}
export function bounded(p:Pose):Pose{'worklet';return{yaw:clamp(p.yaw,-22,22),pitch:clamp(p.pitch,-14,58),q:clamp(p.q)};}
export function authored(seconds:number):Pose{
 'worklet';const t=Math.max(0,seconds)%LOOP;if(t<1||t>=7.55)return rest();if(t<3){const u=smooth((t-1)/2);return{yaw:8*u,pitch:lerp(-8.5,48,u),q:.34*u};}if(t<4.9)return{yaw:8,pitch:48,q:lerp(.34,1,smooth((t-3)/1.9))};if(t<5.75)return{yaw:8,pitch:48,q:1};return blend({yaw:8,pitch:48,q:1},rest(),smooth((t-5.75)/1.8));
}
export function dot(a:Vec,b:Vec){'worklet';return a.x*b.x+a.y*b.y+a.z*b.z;}
export function camera(p:Pose){'worklet';const y=p.yaw*Math.PI/180,t=p.pitch*Math.PI/180;return{forward:{x:Math.sin(y)*Math.cos(t),y:Math.sin(t),z:Math.cos(y)*Math.cos(t)},right:{x:Math.cos(y),y:0,z:-Math.sin(y)},up:{x:-Math.sin(y)*Math.sin(t),y:Math.cos(t),z:-Math.cos(y)*Math.sin(t)}};}
export function sun(q:number):Vec{'worklet';const a=(-6+30*q)*Math.PI/180,e=(-.5+64*Math.sin(Math.PI*q/2))*Math.PI/180;return{x:Math.sin(a)*Math.cos(e),y:Math.sin(e),z:Math.cos(a)*Math.cos(e)};}
export function project(d:Vec,p:Pose):Point|null{'worklet';const c=camera(p),z=dot(d,c.forward);return z>.06?{x:196.5+510*dot(d,c.right)/z,y:426-510*dot(d,c.up)/z}:null;}
export function degrees(x:number){'worklet';return Math.trunc(x+(x>=0?.5:-.5));}
export function time(q:number){'worklet';const m=309+Math.round(clamp(q)*350);return String(Math.floor(m/60)).padStart(2,'0')+':'+String(m%60).padStart(2,'0');}
export function carry(velocity:number,age:number){'worklet';if(age<=0||age>=.6)return 0;const w=2*Math.PI/.35,z=.85,wd=w*Math.sqrt(1-z*z),v=2*Math.tanh(velocity/(2*w))*w,g=age<.42?1:1-smooth((age-.42)/.18);return v/wd*Math.exp(-z*w*age)*Math.sin(wd*age)*g;}
export function released(from:Pose,vy:number,vp:number,age:number,reduced:boolean):Pose{'worklet';return bounded({...from,yaw:from.yaw+(reduced?0:carry(vy,age)),pitch:from.pitch+(reduced?0:carry(vp,age))});}
function bracket(a:number[],x:number){'worklet';x=clamp(x,a[0],a[a.length-1]);if(x<=a[0])return[0,0,0];for(let i=1;i<a.length;i++)if(x<=a[i])return[i-1,i,(x-a[i-1])/(a[i]-a[i-1])];return[a.length-1,a.length-1,0];}
export function atlasWeights(p:Pose){'worklet';const x=bracket(yawSamples,p.yaw),y=bracket(pitchSamples,p.pitch);return[{index:y[0]*7+x[0],weight:(1-x[2])*(1-y[2])},{index:y[0]*7+x[1],weight:x[2]*(1-y[2])},{index:y[1]*7+x[0],weight:(1-x[2])*y[2]},{index:y[1]*7+x[1],weight:x[2]*y[2]}].filter(v=>v.weight>0);}
