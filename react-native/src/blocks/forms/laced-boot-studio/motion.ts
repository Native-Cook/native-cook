import geometry from './assets/geometry.json';

export const LOOP = 8;
export type Point = { x: number; y: number };
export type Curve = { a: Point; c1: Point; c2: Point; b: Point };
export type Scene = {
  progress: number; trims: number[]; spanAlpha: number[]; slacks: number[]; tensions: number[]; automaticSlack: number[];
  tailMorph: number; tip: Point; tangent: Point; tipBackoff: number; automaticAgletY: number; tipAlpha: number;
  velocityLag: number; overpull: Point; materials: number[]; soles: number[]; savedMix: number;
};
export const ARCS = geometry.arcs;
export function clamp(x: number, low = 0, high = 1) { 'worklet'; return Math.min(high, Math.max(low, x)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u * u * (3 - 2 * u); }
export function point(a: Point, b: Point, u: number): Point { 'worklet'; return { x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u }; }
export function residual(age: number) { 'worklet'; return 1 - smooth((age - .12) / .31); }
export function rows(s: Scene) { 'worklet'; return Math.min(6, Math.max(0, Math.floor(s.progress + 1e-8))); }
export function row(span: number) { 'worklet'; return span === 0 ? 0 : Math.floor((span + 1) / 2); }
export function eyelet(r: number, right: boolean): Point { 'worklet'; return { x: right ? 76 - .55 * r : 41 + .55 * r, y: 166 - 22 * r }; }
export function endpoints(span: number): [Point, Point] {
  'worklet'; if (span === 0) return [eyelet(0, false), eyelet(0, true)]; const r = row(span);
  return span % 2 === 0 ? [eyelet(r, false), eyelet(r, true)] : [eyelet(r - 1, true), eyelet(r, false)];
}
export function curve(span: number, slack: number): Curve {
  'worklet'; const [a, b] = endpoints(span);
  return { a, c1: { x: a.x + (b.x - a.x) * .33, y: a.y + (b.y - a.y) * .33 + slack }, c2: { x: a.x + (b.x - a.x) * .67, y: a.y + (b.y - a.y) * .67 + slack }, b };
}
export function curvePoint(c: Curve, t: number): Point {
  'worklet'; const u = 1 - t;
  return { x: u*u*u*c.a.x + 3*u*u*t*c.c1.x + 3*u*t*t*c.c2.x + t*t*t*c.b.x,
    y: u*u*u*c.a.y + 3*u*u*t*c.c1.y + 3*u*t*t*c.c2.y + t*t*t*c.b.y };
}
export function tangent(c: Curve, t: number): Point {
  'worklet'; const u = 1 - t, x = 3*u*u*(c.c1.x-c.a.x)+6*u*t*(c.c2.x-c.c1.x)+3*t*t*(c.b.x-c.c2.x), y = 3*u*u*(c.c1.y-c.a.y)+6*u*t*(c.c2.y-c.c1.y)+3*t*t*(c.b.y-c.c2.y), l = Math.max(1e-9, Math.hypot(x,y)); return { x:x/l, y:y/l };
}
export function prefix(c: Curve, t: number): Curve {
  'worklet'; const ab=point(c.a,c.c1,t),bc=point(c.c1,c.c2,t),cd=point(c.c2,c.b,t),abc=point(ab,bc,t),bcd=point(bc,cd,t); return {a:c.a,c1:ab,c2:abc,b:point(abc,bcd,t)};
}
export function segment(c: Curve, low: number, high: number): Curve {
  'worklet'; const upper=prefix(c,high);if(low<=0||high<=0)return upper;
  const part=prefix({a:upper.b,c1:upper.c2,c2:upper.c1,b:upper.a},1-low/high);return{a:part.b,c1:part.c2,c2:part.c1,b:part.a};
}
export function parameter(index: number, fraction: number) {
  'worklet'; const a=ARCS[index],d=clamp(fraction)*a.length;if(d<=0)return 0;
  for(let i=1;i<32;i++)if(a.distances[i]>=d){const u=(d-a.distances[i-1])/Math.max(1e-9,a.distances[i]-a.distances[i-1]);return(i-1+u)/31;}return 1;
}
export function nearest(index: number, p: Point) {
  'worklet'; const arc=ARCS[index];let fraction=0,distance=Infinity;
  for(let i=1;i<32;i++){const a=arc.points[i-1],b=arc.points[i],dx=b[0]-a[0],dy=b[1]-a[1],u=clamp(((p.x-a[0])*dx+(p.y-a[1])*dy)/Math.max(1e-9,dx*dx+dy*dy)),d=Math.hypot(p.x-a[0]-dx*u,p.y-a[1]-dy*u);
    if(d<distance){fraction=(arc.distances[i-1]+(arc.distances[i]-arc.distances[i-1])*u)/Math.max(1e-9,arc.length);distance=d;}}
  return {fraction,distance};
}
export function tipRoute(): Curve { 'worklet';return{a:{x:63,y:181},c1:{x:67,y:174},c2:{x:76,y:176},b:{x:76,y:166}}; }
export function tail(s: Scene): Curve { 'worklet';const c=curve(0,s.slacks[0]),u=s.tailMorph;return{a:c.a,c1:point({x:43,y:179},c.c1,u),c2:point({x:55,y:185},c.c2,u),b:curvePoint(tipRoute(),parameter(0,u))}; }
export function local(world: Point): Point { 'worklet';const a=4*Math.PI/180,x=world.x-272.5,y=world.y-308;return{x:Math.cos(a)*x+Math.sin(a)*y+58.5,y:-Math.sin(a)*x+Math.cos(a)*y+160}; }
export function accepts(world: Point) { 'worklet';const p=local(world);return p.x>=-12&&p.x<129&&p.y>=-12&&p.y<332; }
export function spanProgress(i: number, fraction: number) { 'worklet';return i===0?fraction:row(i)+(i%2===0?.5:0)+fraction*.5; }
export function activeSpan(p: number) { 'worklet';return p<1?0:Math.min(10,Math.max(1,Math.floor((p-1)*2)+1)); }
export function emptyScene(): Scene {
  'worklet';return{progress:0,trims:Array(11).fill(0),spanAlpha:Array(11).fill(1),slacks:Array(11).fill(6.375),tensions:Array(6).fill(.15),automaticSlack:Array(11).fill(0),tailMorph:0,tip:{x:63,y:181},tangent:{x:1,y:0},tipBackoff:0,automaticAgletY:0,tipAlpha:1,velocityLag:0,overpull:{x:0,y:0},materials:[1,0,0],soles:[1,0],savedMix:0};
}
export function clone(s: Scene): Scene { 'worklet';return{...s,trims:[...s.trims],spanAlpha:[...s.spanAlpha],slacks:[...s.slacks],tensions:[...s.tensions],automaticSlack:[...s.automaticSlack],tip:{...s.tip},tangent:{...s.tangent},overpull:{...s.overpull},materials:[...s.materials],soles:[...s.soles]}; }
export function updateTip(s: Scene) {
  'worklet';const i=activeSpan(s.progress);
  if(i===0){const c=tail(s);s.tip=c.b;s.tangent=tangent(c,1);}else{const c=curve(i,s.slacks[i]),t=parameter(i,s.trims[i]);s.tip=curvePoint(c,t);s.tangent=tangent(c,t);}
  s.tip={x:s.tip.x+s.overpull.x-s.tangent.x*s.tipBackoff,y:s.tip.y+s.overpull.y+s.automaticAgletY-s.tangent.y*s.tipBackoff};
}
export function canonical(progress: number, tensions: number[]): Scene {
  'worklet';const s=emptyScene();s.progress=clamp(progress,0,6);s.tensions=tensions.map(v=>clamp(v));s.tailMorph=clamp(progress);s.trims[0]=clamp(progress);
  for(let i=1;i<11;i++)s.trims[i]=clamp((progress-row(i)-(i%2===0?.5:0))*2);
  for(let i=0;i<11;i++)s.slacks[i]=7.5*(1-s.tensions[row(i)])+(i===0?0:2*Math.sin(Math.PI*s.trims[i]));updateTip(s);return s;
}
export function rest() { 'worklet';return canonical(0,Array(6).fill(.15)); }
export function finalScene() { 'worklet';return canonical(6,Array(6).fill(1)); }
export function authored(time: number): Scene {
  'worklet';const t=Math.max(0,time)%LOOP;let p=0;
  if(t>=1&&t<5.3){for(let r=0;r<6;r++){if(t<1+.48*r)break;p=Math.max(p,r+smooth((t-1-.48*r)/.40));}p=Math.min(6,p);}else if(t>=5.3&&t<7.55)p=6*(1-smooth((t-5.3)/2.25));
  let tensions=Array(6).fill(.15);
  if(t>=4.1&&t<5){for(let r=0;r<6;r++){const d=.035*(5-r),u=clamp((t-4.1-d)/(.62-d));tensions[r]=.15+.85*(1-Math.pow(1-u,3));}}else if(t>=5&&t<5.3)tensions=Array(6).fill(1-.85*smooth((t-5)/.3));
  const s=canonical(p,tensions);
  if(t>=3.88&&t<4.1){const b=Math.pow(Math.sin(Math.PI*(t-3.88)/.22),2);s.tipBackoff=2.5*b;s.automaticSlack=Array(11).fill(2.625*b);for(let i=0;i<11;i++)s.slacks[i]+=s.automaticSlack[i];}
  if(t>=4.1&&t<=4.72)s.automaticAgletY=-.9*Math.pow(Math.sin(Math.PI*(t-4.1)/.62),2);
  for(let r=0;r<6;r++){const start=1+.48*r,ends=r===0?[start+.4]:[start+.2,start+.4];for(const end of ends)if(t>=end&&t<end+.035)s.tipAlpha=0;}
  if(t>=5.3&&t<7.55)for(let half=2;half<=11;half++){const boundary=half*.5,value=1-boundary/6,inverse=.5-Math.sin(Math.asin(1-2*value)/3),passage=5.3+2.25*inverse;if(Math.abs(t-passage)<.0175)s.tipAlpha=0;}
  updateTip(s);return s;
}
export function held(captured: Scene, progress: number, tensions: number[], age: number, lag: number, overpull: Point): Scene {
  'worklet';const r=canonical(progress,tensions),original=canonical(captured.progress,captured.tensions),f=residual(age),travel=1-smooth(Math.abs(progress-captured.progress)/Math.max(.001,Math.min(1,progress>=captured.progress?6-captured.progress:captured.progress)));
  r.materials=[...captured.materials];r.soles=[...captured.soles];r.savedMix=captured.savedMix;r.spanAlpha=captured.spanAlpha.map(a=>1+(a-1)*f*travel);r.velocityLag=lag;r.overpull={...overpull};
  for(let i=0;i<11;i++){const rowIndex=row(i),tc=captured.tensions[rowIndex],current=r.tensions[rowIndex],edge=current>=tc?1-tc:tc,wt=edge<=0?1:1-smooth(Math.abs(current-tc)/edge),automatic=captured.slacks[i]-original.slacks[i]-captured.velocityLag*(1-tc);
    r.automaticSlack[i]=automatic*wt*f*travel;r.slacks[i]+=r.automaticSlack[i]+lag*(1-current);r.trims[i]+=(captured.trims[i]-original.trims[i])*f*travel;}
  r.tailMorph+=(captured.tailMorph-original.tailMorph)*f*travel;
  const tc=captured.tensions[5],current=r.tensions[5],edge=current>=tc?1-tc:tc,wt=edge<=0?1:1-smooth(Math.abs(current-tc)/edge);
  r.tipBackoff=captured.tipBackoff*f*wt*travel;r.automaticAgletY=captured.automaticAgletY*f*wt*travel;r.tipAlpha=1+(captured.tipAlpha-1)*f*travel;updateTip(r);
  const originalTip=clone(captured);updateTip(originalTip);r.tip.x+=(captured.tip.x-originalTip.tip.x)*f*travel;r.tip.y+=(captured.tip.y-originalTip.tip.y)*f*travel;
  if(age===0&&progress===captured.progress&&tensions.every((v,i)=>v===captured.tensions[i])&&lag===captured.velocityLag&&overpull.x===captured.overpull.x&&overpull.y===captured.overpull.y)return clone(captured);return r;
}
export function released(captured: Scene, age: number, downAge: number): Scene {
  'worklet';if(age===0)return clone(captured);const r=clone(captured),oldF=residual(downAge),f=oldF>1e-9?residual(downAge+age)/oldF:0,lag=captured.velocityLag*(1-smooth(age/.09));
  for(let i=0;i<11;i++){r.automaticSlack[i]=captured.automaticSlack[i]*f;const w=1-captured.tensions[row(i)];r.slacks[i]=captured.slacks[i]-captured.automaticSlack[i]*(1-f)-captured.velocityLag*w+lag*w;}
  r.velocityLag=lag;r.tipBackoff*=f;r.automaticAgletY*=f;r.tipAlpha=1+(captured.tipAlpha-1)*f;r.overpull=point(captured.overpull,{x:0,y:0},smooth(age/.43));updateTip(r);return r;
}
export function recovering(captured: Scene, age: number): Scene {
  'worklet';if(age<=0)return clone(captured);const u=smooth(age/.6);if(u>=1){const r=rest();r.materials=[...captured.materials];r.soles=[...captured.soles];return r;}
  const tensions=captured.tensions.map(v=>v+(.15-v)*u),r=held(captured,captured.progress*(1-u),tensions,0,captured.velocityLag*(1-u),point(captured.overpull,{x:0,y:0},u)),original=canonical(captured.progress,captured.tensions);
  for(let i=0;i<11;i++){const baseline=7.5*(1-r.tensions[row(i)])+(i===0?0:2*Math.sin(Math.PI*r.trims[i]));r.slacks[i]=baseline+(captured.slacks[i]-original.slacks[i])*(1-u);r.automaticSlack[i]=r.slacks[i]-baseline-r.velocityLag*(1-r.tensions[row(i)]);r.spanAlpha[i]=captured.spanAlpha[i]+(1-captured.spanAlpha[i])*u;}
  r.tipBackoff=captured.tipBackoff*(1-u);r.automaticAgletY=captured.automaticAgletY*(1-u);r.tipAlpha=1+(captured.tipAlpha-1)*(1-u);r.savedMix=captured.savedMix*(1-u);updateTip(r);return r;
}
export function resolving(captured: Scene, count: number, age: number): Scene {
  'worklet';if(age<=0)return clone(captured);const u=smooth(age/.12),r=canonical(count,captured.tensions);r.materials=[...captured.materials];r.soles=[...captured.soles];r.savedMix=captured.savedMix;
  for(let i=0;i<11;i++){if(r.trims[i]>captured.trims[i])r.spanAlpha[i]=u;else if(r.trims[i]<captured.trims[i]&&u<1){r.trims[i]=captured.trims[i];r.slacks[i]=captured.slacks[i];r.spanAlpha[i]=captured.spanAlpha[i]*(1-u);}}
  r.tipAlpha=u>=1?1:0;return r;
}
