export const LOOP = 8;
export type Mood = 0 | 1 | 2 | 3;
export type Point = { x: number; y: number };
export type RGBA = { r: number; g: number; b: number; a: number };
export type Token = { id: number; mood: Mood; center: Point; angle: number; alpha: number; batchAge: number };
export type Scene = { tokens: Token[]; labelWeights: number[]; labelOffsets: number[]; capsuleX: number; dots: RGBA[]; selectedDay: number; captionAlpha: number; captionDay: number };
export type Records = (Mood | null)[];
export type Transition = { from: Scene; mood: Mood; day: number; dots: RGBA[]; direction: number; speedup: number; outgoing: Token[] };
export type Owner = 'bowl' | 'label';
export type Session = { owner: Owner; generation: number; base: Scene; current: Scene; anchor: Point; lastPoint: Point; lastDate: number; velocity: Point; translation: Point; haptic: boolean };
export type Event = { stamp: number; kind: 'transition'; plan: Transition } | { stamp: number; kind: 'release'; from: Scene; base: Scene; cancelled: boolean } | { stamp: number; kind: 'hold'; scene: Scene };
export type Motion = { now: number; epoch: number; visible: boolean; generation: number; records: Records; chosenMood: Mood; chosenDay: number; event: Event | null; session: Session | null; blocked: number; tokens: { bowl: number | null; label: number | null }; quietSave: { stamp: number; day: number } | null };
export const titles = ['CALM', 'LOW', 'BRIGHT', 'TENSE'];
export const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const hexes = [0x0CB782, 0x567DF0, 0xF1DF67, 0xEE1644];
export const dateX = [40, 92.1666666667, 144.3333333333, 196.5, 248.6666666667, 300.8333333333, 353];
export const initialRecords: Records = [2, 1, 0, 0, null, null, null];
export const painterOrder = [0, 2, 1, 3, 4, 6, 5];
export const layouts = [
  { radii: [59,48,54,31,37,41,29], targets: [[101,258],[183,295],[281,282],[282,350],[116,346],[190,376],[244,366]], angles: [20,31,-12,44,11,-27,58] },
  { radii: [61,43,56,33,36,39,29], targets: [[110,282],[181,317],[247,258],[289,326],[116,347],[194,376],[254,357]], angles: [-20,31,-12,44,11,-27,58] },
  { radii: [53,40,82,34,37,40,29], targets: [[110,286],[168,317],[230,272],[296,284],[138,359],[203,376],[258,358]], angles: [20,31,-12,44,11,-27,58] },
  { radii: [61,38,51,31,34,42,26], targets: [[106,273],[202,278],[279,315],[286,352],[115,350],[192,373],[205,326]], angles: [-20,31,-12,44,11,-27,58] },
];
export const spawns = [{x:104,y:151},{x:222,y:122},{x:270,y:160},{x:301,y:130},{x:151,y:145},{x:197,y:105},{x:245,y:135}];
function makeContour(mood: number): Point[] {
  if (mood === 3) {
    const vertices = Array.from({ length: 24 }, (_, i) => { const a = i * Math.PI / 12 - Math.PI / 2, r = i % 2 === 0 ? 1 : .82; return { x: Math.cos(a) * r, y: Math.sin(a) * r }; }), poly: Point[] = [];
    for (let i = 0; i < 24; i++) {
      const v = vertices[i], before = vertices[(i + 23) % 24], after = vertices[(i + 1) % 24];
      const inset = (p: Point) => { const length = Math.hypot(p.x - v.x, p.y - v.y), d = Math.min(3 / 43, length * .4); return { x: v.x + (p.x - v.x) * d / length, y: v.y + (p.y - v.y) * d / length }; };
      const a = inset(before), b = inset(after);
      for (let j = 0; j < 12; j++) { const u = j / 11, s = 1 - u; poly.push({ x: s*s*a.x+2*s*u*v.x+u*u*b.x, y: s*s*a.y+2*s*u*v.y+u*u*b.y }); }
    }
    const lengths = [0]; for (let i = 0; i < poly.length; i++) lengths.push(lengths[lengths.length - 1] + Math.hypot(poly[(i + 1) % poly.length].x - poly[i].x, poly[(i + 1) % poly.length].y - poly[i].y));
    return Array.from({ length: 256 }, (_, i) => { const at = lengths[lengths.length - 1] * i / 256, found = lengths.findIndex(v => v > at), k = Math.min(poly.length - 1, Math.max(0, (found < 0 ? poly.length : found) - 1)), q = (at - lengths[k]) / (lengths[k + 1] - lengths[k]), a = poly[k], b = poly[(k + 1) % poly.length]; return { x: a.x + (b.x - a.x) * q, y: a.y + (b.y - a.y) * q }; });
  }
  return Array.from({ length: 256 }, (_, i) => { const a = i * 2 * Math.PI / 256, r = mood === 0 ? .8 + .2 * Math.cos(4*a) : mood === 1 ? .86 + .14 * Math.cos(8*a) : .77 + .23 * Math.cos(4*a), phase = mood === 0 ? Math.PI / 4 : 0; return { x: Math.cos(a + phase) * r, y: Math.sin(a + phase) * r * (mood === 1 ? 1.08 : 1) }; });
}
export const contours = [0,1,2,3].map(makeContour);
export function clamp(x: number, a = 0, b = 1) { 'worklet'; return Math.min(b, Math.max(a, x)); }
export function smooth(x: number) { 'worklet'; const u = clamp(x); return u*u*(3-2*u); }
function curve(t: number, a: number, b: number) { 'worklet'; const s = 1 - t; return 3*s*s*t*a+3*s*t*t*b+t*t*t; }
function bezier(x: number, x1: number, y1: number, x2: number, y2: number) { 'worklet'; x = clamp(x); if (x === 0 || x === 1) return x; let lo = 0, hi = 1; for (let i = 0; i < 26; i++) { const m = (lo + hi) / 2; if (curve(m,x1,x2) < x) lo = m; else hi = m; } return curve((lo+hi)/2,y1,y2); }
export function ease(x: number) { 'worklet'; return bezier(x,.2,.8,.2,1); }
export function io(x: number) { 'worklet'; return bezier(x,.65,0,.35,1); }
export function stepped(mood: Mood, delta: number): Mood { 'worklet'; return ((mood + delta + 4) % 4) as Mood; }
export function dominantMood(s: Scene): Mood { 'worklet'; let k = 0; for (let i = 1; i < 4; i++) if (s.labelWeights[i] > s.labelWeights[k]) k = i; return k as Mood; }
export function rgba(hex: number, alpha = 1): RGBA { 'worklet'; return { r: ((hex>>16)&255)/255, g: ((hex>>8)&255)/255, b: (hex&255)/255, a: alpha }; }
export function mix(a: RGBA, b: RGBA, q: number): RGBA { 'worklet'; return { r:a.r+(b.r-a.r)*q, g:a.g+(b.g-a.g)*q, b:a.b+(b.b-a.b)*q, a:a.a+(b.a-a.a)*q }; }
export function cloneToken(p: Token): Token { 'worklet'; return { ...p, center: { ...p.center } }; }
export function cloneScene(s: Scene): Scene { 'worklet'; return { ...s, tokens: s.tokens.map(cloneToken), labelWeights: [...s.labelWeights], labelOffsets: [...s.labelOffsets], dots: s.dots.map(p => ({ ...p })) }; }
export function interpolate(a: Token, b: Token, q: number): Token { 'worklet'; return { ...a, center: { x:a.center.x+(b.center.x-a.center.x)*q, y:a.center.y+(b.center.y-a.center.y)*q }, angle:a.angle+(b.angle-a.angle)*q, alpha:a.alpha+(b.alpha-a.alpha)*q }; }
export function isContained(token: Token) {
  'worklet'; const a = token.angle * Math.PI / 180, c = Math.cos(a), s = Math.sin(a), r = layouts[token.mood].radii[token.id];
  for (const p of contours[token.mood]) { const x = token.center.x+r*(c*p.x-s*p.y), y=token.center.y+r*(s*p.x+c*p.y); if (y>=273 && Math.hypot(x-196.5,y-273)>151.5) return false; } return true;
}
export function contained(from: Token, desired: Token): Token {
  'worklet'; if (from.center.x === desired.center.x && from.center.y === desired.center.y && from.angle === desired.angle) return desired; if (isContained(desired)) return desired;
  let low=0,high=1; for (let i=0;i<12;i++) { const q=(low+high)/2; if (isContained(interpolate(from,desired,q))) low=q; else high=q; } return interpolate(from,desired,low);
}
export function colors(records: Records, day: number, mood: Mood) { 'worklet'; return records.map((saved,i) => rgba(i===day ? hexes[mood] : saved === null ? 0xE6E1D6 : hexes[saved])); }
export function settled(mood: Mood = 0, day = 3, records?: Records, poster = false): Scene {
  'worklet'; const saved=records??initialRecords,layout=layouts[mood]; return { tokens: Array.from({length:7},(_,i) => ({ id:i,mood,center:{x:layout.targets[i][0],y:layout.targets[i][1]},angle:layout.angles[i],alpha:poster&&i<4?0:1,batchAge:2 })), labelWeights:[0,1,2,3].map(i=>i===mood?1:0),labelOffsets:[0,0,0,0],capsuleX:dateX[day],dots:colors(saved,day,mood),selectedDay:day,captionAlpha:0,captionDay:day };
}
export function rest(records?: Records) { 'worklet'; return settled(0,3,records,true); }
export function contactTime(id: number, mood: Mood) { 'worklet'; return (35+Math.sqrt(35*35+2*1750*(layouts[mood].targets[id][1]-spawns[id].y)))/1750; }
export function flight(id: number, mood: Mood, age: number, bounce: number): Token {
  'worklet'; const layout=layouts[mood],s=Math.max(0,age),t=contactTime(id,mood),target={x:layout.targets[id][0],y:layout.targets[id][1]},spawn=spawns[id],sign=id%2===0?1:-1,spin=sign*(65+9*id); let center={...spawn},angle=layout.angles[id]-spin*t;
  if(s<t) { const u=s/t,q=1-Math.pow(1-u,3); center={x:spawn.x+(target.x-spawn.x)*q+sign*8*Math.pow(Math.sin(Math.PI*u),2),y:spawn.y-35*s+.5*1750*s*s};angle+=spin*s; }
  else { const v=s-t,e=1-smooth(v/bounce);center={x:target.x+sign*(2+.4*id)*Math.exp(-8*v)*Math.sin(13*v)*e,y:target.y-(18+2*id)*Math.exp(-7*v)*Math.abs(Math.sin(14*v))*e};angle=layout.angles[id]+sign*24*Math.exp(-7*v)*Math.sin(14*v)*e; }
  return {id,mood,center,angle,alpha:smooth(age/.08),batchAge:age};
}
export function authored(time: number, records?: Records): Scene {
  'worklet'; const t=Math.max(0,time)%LOOP,scene=rest(records);
  if(t<3.14) {
    const ids=[0,2,1,3];for(let rank=0;rank<ids.length;rank++) scene.tokens[ids[rank]]=flight(ids[rank],0,t-1.10-rank*.075,.56);
    let jostle=0;for(let rank=0;rank<ids.length;rank++) { const age=t-(1.10+rank*.075+contactTime(ids[rank],0));if(age>0&&age<.45)jostle=Math.max(jostle,Math.pow(Math.sin(Math.PI*age/.45),2)); }
    const impulses=[[-3,-4,-4],[2,-6,5],[3,-3,-3]];for(let i=4;i<7;i++){const d=impulses[i-4];scene.tokens[i].center.x+=d[0]*jostle;scene.tokens[i].center.y+=d[1]*jostle;scene.tokens[i].angle+=d[2]*jostle;}
    if(t>=2.82){const q=ease((t-2.82)/.30);for(let i=0;i<7;i++){scene.tokens[i].alpha*=1-q;scene.tokens[i].center.y+=5*q;}}
  } else if(t<5.94) {
    const ids=[0,2,1,4,3,5,6];for(let rank=0;rank<ids.length;rank++)scene.tokens[ids[rank]]=flight(ids[rank],2,t-3.14-rank*.085,.56);
    if(t>=5.55){const q=ease((t-5.55)/.33);for(let i=0;i<7;i++){scene.tokens[i].alpha*=1-q;scene.tokens[i].center.y+=5*q;}}
  } else {const ids=[4,5,6];for(let rank=0;rank<ids.length;rank++)scene.tokens[ids[rank]]=flight(ids[rank],0,t-5.94-rank*.070,.56);}
  if(t>=2.94&&t<5.72){const q=ease((t-2.94)/.24);scene.labelWeights=[1-q,0,q,0];scene.labelOffsets=[12*q,0,-12*(1-q),0];scene.dots[3]=mix(rgba(hexes[0]),rgba(hexes[2]),q);}
  else if(t>=5.72&&t<7.25){const q=ease((t-5.72)/.24);scene.labelWeights=[q,0,1-q,0];scene.labelOffsets=[-12*(1-q),0,12*q,0];scene.dots[3]=mix(rgba(hexes[2]),rgba(hexes[0]),q);}
  return t>=7.25?rest(records):scene;
}
export function transition(scene: Scene, mood: Mood, day: number, records: Records, direction: number, velocityX = 0): Transition {
  'worklet'; return {from:scene,mood,day,dots:colors(records,day,mood),direction,speedup:Math.min(.15,Math.abs(velocityX)/6000),outgoing:scene.tokens.map(p=>contained(p,{...p,center:{x:p.center.x,y:p.center.y+5}}))};
}
export function springRemaining(time: number, response: number, damping: number) { 'worklet'; const omega=2*Math.PI/response,beta=omega*Math.sqrt(1-damping*damping);return Math.exp(-damping*omega*time)*(Math.cos(beta*time)+damping*omega/beta*Math.sin(beta*time)); }
function capsuleProgress(time: number) { 'worklet'; if(time<=0)return 0;if(time>=.50)return 1;return 1-springRemaining(time,.30,.85)*(1-smooth((time-.35)/.15)); }
export function transitioning(plan: Transition, elapsed: number): Scene {
  'worklet'; if(elapsed<=0)return plan.from;const s=cloneScene(plan.from),q=ease(elapsed/.24);
  for(let i=0;i<4;i++){const target=i===plan.mood;s.labelWeights[i]=plan.from.labelWeights[i]*(1-q)+(target?q:0);const start=target&&plan.from.labelWeights[i]===0?-12*plan.direction:plan.from.labelOffsets[i];s.labelOffsets[i]=target?start*(1-q):start+12*plan.direction*q;}
  s.dots=plan.from.dots.map((p,i)=>mix(p,plan.dots[i],q));s.capsuleX=plan.from.capsuleX+(dateX[plan.day]-plan.from.capsuleX)*capsuleProgress(elapsed);s.selectedDay=plan.day;s.captionAlpha=plan.from.captionAlpha*(1-q);
  if(elapsed<.25){const fade=ease(elapsed/.25);s.tokens=plan.from.tokens.map((a,i)=>({...interpolate(a,plan.outgoing[i],fade),alpha:a.alpha*(1-fade)}));}
  else {const ids=[0,2,1,4,3,5,6];for(let rank=0;rank<ids.length;rank++){const delay=Math.max(0,rank*.070-rank*plan.speedup/6);s.tokens[ids[rank]]=flight(ids[rank],plan.mood,elapsed-.25-delay,.50);}}
  return s;
}
export function stirring(scene: Scene, anchor: Point, translation: Point, velocity: Point): Scene {
  'worklet';if(translation.x===0&&translation.y===0&&velocity.x===0&&velocity.y===0)return scene;
  const vx=clamp(velocity.x,-900,900),vy=clamp(velocity.y,-900,900),x=28*Math.tanh((translation.x+.018*vx)/48),y=20*Math.tanh((translation.y+.018*vy)/48),rotation=35*Math.tanh(vx/900+translation.x/80),result=cloneScene(scene);
  for(let i=0;i<7;i++)if(scene.tokens[i].alpha>0){const p=scene.tokens[i],distance=Math.hypot(p.center.x-anchor.x,p.center.y-anchor.y),w=Math.pow(Math.max(0,1-distance/110),2);result.tokens[i]=contained(p,{...p,center:{x:p.center.x+x*w,y:p.center.y+y*w},angle:p.angle+rotation*w});}return result;
}
export function released(scene: Scene, base: Scene, elapsed: number, cancelled: boolean): Scene {
  'worklet';if(elapsed<=0)return scene;const duration=cancelled ? .24 : .70;if(elapsed>=duration)return base;const weight=cancelled?1-smooth(elapsed/duration):springRemaining(elapsed,.48,.72)*(1-smooth(elapsed/duration)),result=cloneScene(scene);
  for(let i=0;i<7;i++)result.tokens[i]=contained(base.tokens[i],interpolate(base.tokens[i],scene.tokens[i],weight));return result;
}
export function recovering(scene: Scene, elapsed: number, records: Records): Scene {
  'worklet';if(elapsed<=0)return scene;const target=rest(records);if(elapsed>=.70)return target;const s=cloneScene(scene),q=io(elapsed/.70);
  for(let i=0;i<4;i++){s.labelWeights[i]+=(target.labelWeights[i]-s.labelWeights[i])*q;s.labelOffsets[i]*=1-q;}s.dots=scene.dots.map((p,i)=>mix(p,target.dots[i],q));s.capsuleX+=(target.capsuleX-s.capsuleX)*q;s.selectedDay=3;s.captionAlpha*=1-q;
  if(elapsed<.20){const fade=ease(elapsed/.20);for(let i=0;i<7;i++)s.tokens[i].alpha*=1-fade;}else{s.tokens=target.tokens;const fade=ease((elapsed-.20)/.50);for(let i=0;i<7;i++)s.tokens[i].alpha*=fade;}return s;
}
export function eventScene(event: Event, age: number): Scene { 'worklet'; if(event.kind==='transition')return transitioning(event.plan,age);if(event.kind==='release')return released(event.from,event.base,age,event.cancelled);return event.scene; }
export function drive(m: Motion, autoPlay: boolean, reduced: boolean, previewTime?: number): Scene {
  'worklet';if(reduced){const s=settled(m.chosenMood,m.chosenDay,m.records);if(m.quietSave){s.captionAlpha=1;s.captionDay=m.quietSave.day;}return s;}if(previewTime!==undefined)return authored(previewTime,m.records);if(m.session)return m.session.current;
  if(m.event){const age=Math.max(0,m.now-m.event.stamp);if(age<2)return eventScene(m.event,age);if(age<2.70)return recovering(eventScene(m.event,2),age-2,m.records);return autoPlay?authored(age-2.70,m.records):rest(m.records);}return autoPlay?authored(Math.max(0,m.now-m.epoch),m.records):rest(m.records);
}
