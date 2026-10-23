import type { SkImage } from '@shopify/react-native-skia';

export type Params = { q:number; angle:number; low:number; high:number; roiX:number; roiY:number };
export type Bitmap = { image:SkImage; params:Params };
export type ImagePair = { a:Bitmap; b:Bitmap; mix:number };
export type Appearance = { bracketAlpha:number; handleScale:number; anticipation:number; lowOffset:number; highOffset:number };
export type Scene = { pair:ImagePair; params:Params; appearance:Appearance; compare:number; kept:boolean };
export type Knot = { time:number; params:Params };
export const LOOP=8;
export function rest():Params { 'worklet';return {q:0,angle:0,low:.44,high:.58,roiX:0,roiY:0}; }
export function reducedParams():Params { 'worklet';return {q:1,angle:0,low:.28,high:.78,roiX:0,roiY:0}; }
export function zero():Params { 'worklet';return {q:0,angle:0,low:0,high:0,roiX:0,roiY:0}; }
export function clamp(x:number,a=0,b=1) { 'worklet';return Math.min(b,Math.max(a,x)); }
export function smooth(x:number) { 'worklet';const u=clamp(x);return u*u*(3-2*u); }
export function cubic(x:number) { 'worklet';const u=clamp(x);return u<.5?4*u*u*u:1-Math.pow(-2*u+2,3)/2; }
export function mix(a:Params,b:Params,u:number):Params { 'worklet';return {q:a.q+(b.q-a.q)*u,angle:a.angle+(b.angle-a.angle)*u,low:a.low+(b.low-a.low)*u,high:a.high+(b.high-a.high)*u,roiX:a.roiX+(b.roiX-a.roiX)*u,roiY:a.roiY+(b.roiY-a.roiY)*u}; }
export function bounded(p:Params):Params { 'worklet';const low=clamp(p.low,.08,.84);return {q:clamp(p.q),angle:clamp(p.angle,-90,90),low,high:clamp(p.high,low+.08,.92),roiX:clamp(p.roiX,-30,30),roiY:clamp(p.roiY,-42,42)}; }
export function quantized(p:Params):Params { 'worklet';const low=clamp(Math.floor(p.low*200+.5),16,168),high=clamp(Math.floor(p.high*200+.5),low+16,184);return {q:clamp(Math.floor(p.q*64+.5)/64),angle:clamp(Math.floor(p.angle+.5),-90,90),low:low/200,high:high/200,roiX:clamp(Math.floor(p.roiX+.5),-30,30),roiY:clamp(Math.floor(p.roiY+.5),-42,42)}; }
export function same(a:Params,b:Params) { 'worklet';return a.q===b.q&&a.angle===b.angle&&a.low===b.low&&a.high===b.high&&a.roiX===b.roiX&&a.roiY===b.roiY; }
export function key(p:Params) { return [p.q,p.angle,p.low,p.high,p.roiX,p.roiY].join('/'); }
export function authoredParams(time:number):Params { 'worklet';const t=Math.max(0,time)%LOOP,window=cubic((t-1.30)/1.35);let p:Params={q:1-Math.pow(1-clamp((t-1.30)/1.88),3),angle:90*cubic((t-2.85)/2),low:.44-.16*window,high:.58+.20*window,roiX:0,roiY:0};if(t>=5.55){const back=cubic((t-5.55)/1.60);p={...p,q:1-cubic((t-5.55)/1.40),angle:90*(1-back),low:.28+.16*back,high:.78-.20*back};}if(t>=7.15||t<=1.30)return rest();if(t>=6.95)p.q=0;return p; }
export function makeKnots():Knot[] { const times=[0];for(let k=0;k<=15;k++)times.push(k===15?2.65:1.30+1.35*k/15);for(let k=1;k<=3;k++)times.push(k===3?2.85:2.65+.20*k/3);for(let k=1;k<=4;k++)times.push(k===4?3.18:2.85+.33*k/4);for(let k=1;k<=21;k++)times.push(k===21?4.85:3.18+1.67*k/21);times.push(5.55);for(let k=1;k<=17;k++)times.push(k===17?6.95:5.55+1.40*k/17);times.push(7.15);return times.map(time=>({time,params:authoredParams(time)})); }
export const knots=makeKnots();
export const loopTimes=knots.map(k=>k.time);
export function spring(start:number,velocity:number,target:number,elapsed:number,response=.42,damping=.82,end=.60,tail=.42) { 'worklet';if(elapsed<=0)return start;if(elapsed>=end)return target;const w=2*Math.PI/response,c=damping*w,wd=w*Math.sqrt(1-damping*damping),a=start-target,b=(velocity+c*a)/wd,raw=Math.exp(-c*elapsed)*(a*Math.cos(wd*elapsed)+b*Math.sin(wd*elapsed));return target+raw*(1-smooth((elapsed-tail)/(end-tail))); }
export function editing():Appearance { 'worklet';return {bracketAlpha:1,handleScale:1,anticipation:0,lowOffset:0,highOffset:0}; }
export function appearance(time:number):Appearance { 'worklet';const t=Math.max(0,time)%LOOP;if(t<=1.10||t>=7.40)return editing();if(t<1.30)return {...editing(),anticipation:2*Math.sin(Math.PI*(t-1.10)/.20)};if(t<7.15){const u=1-Math.pow(1-clamp((t-1.30)/1.88),3);return {...editing(),bracketAlpha:1-.78*u,handleScale:1+.02*u};}return {...editing(),bracketAlpha:clamp(spring(.22,0,1,t-7.15,.32,.85,.25,.18)),handleScale:spring(1.02,0,1,t-7.15,.32,.85,.25,.18)}; }
export function single(bitmap:Bitmap):ImagePair { 'worklet';return {a:bitmap,b:bitmap,mix:0}; }
export function original(image:SkImage,kept=false):Scene { 'worklet';return {pair:single({image,params:rest()}),params:rest(),appearance:editing(),compare:0,kept}; }
export function sample(images:Bitmap[],times:number[],age:number):{pair:ImagePair;params:Params;velocity:Params} { 'worklet';if(age<=times[0])return {pair:single(images[0]),params:images[0].params,velocity:zero()};if(age>=times[times.length-1]){const b=images[images.length-1];return {pair:single(b),params:b.params,velocity:zero()};}let i=0;while(i+1<times.length&&age>=times[i+1])i++;const span=times[i+1]-times[i],u=clamp((age-times[i])/span),a=images[i],b=images[i+1],params=mix(a.params,b.params,u);return {pair:{a,b,mix:params.q===0?0:u},params,velocity:{q:(b.params.q-a.params.q)/span,angle:(b.params.angle-a.params.angle)/span,low:(b.params.low-a.params.low)/span,high:(b.params.high-a.params.high)/span,roiX:(b.params.roiX-a.params.roiX)/span,roiY:(b.params.roiY-a.params.roiY)/span}}; }
export function authored(time:number,images:Bitmap[],kept=false):{scene:Scene;velocity:Params} { 'worklet';const t=Math.max(0,time)%LOOP,s=sample(images,loopTimes,t);return {scene:{pair:s.pair,params:s.params,appearance:appearance(t),compare:0,kept},velocity:s.velocity}; }
export function settledTarget(p:Params,velocityY:number):Params { 'worklet';return bounded({...p,q:p.q>.5||velocityY< -600?1:0,angle:Math.floor(p.angle/15+.5)*15,low:Math.floor(p.low*100+.5)/100,high:Math.floor(p.high*100+.5)/100}); }
export function settlement(from:Params,velocity:Params,target:Params,t:number):Params { 'worklet';if(t<=0)return from;if(t>=.60)return target;return bounded({q:spring(from.q,velocity.q,target.q,t),angle:spring(from.angle,velocity.angle,target.angle,t),low:spring(from.low,velocity.low,target.low,t),high:spring(from.high,velocity.high,target.high,t),roiX:spring(from.roiX,velocity.roiX,target.roiX,t),roiY:spring(from.roiY,velocity.roiY,target.roiY,t)}); }
export function recoveryParams(p:Params,t:number):Params { 'worklet';return t<=0?p:t>=.60?rest():mix(p,rest(),cubic(t/.60)); }
export function rubber(over:number) { 'worklet';return over/(1+Math.abs(over)/80)*.55; }
