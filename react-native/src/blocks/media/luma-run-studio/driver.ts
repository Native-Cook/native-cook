import type { SkImage } from '@shopify/react-native-skia';
import { authored, bounded, clamp, cubic, mix, original, quantized, recoveryParams, rest, rubber, same, sample, settledTarget, single, smooth, spring, zero, type Appearance, type Bitmap, type Params, type Scene } from './motion';

export type Owner='image'|'region'|'low'|'high'|'dial'|'compare';
export type Session={id:number;owner:Owner;base:Scene;x:number;y:number;lastX:number;lastY:number;lastTime:number;inherited:Params;velocity:Params;pointerY:number;changed:boolean;band:number;hapticTime:number};
export type Event={generation:number;time:number;from:Scene;target:Params;velocity:Params;duration:number;hold:boolean;phase:number;settled:boolean;requested:boolean;frames:Bitmap[]|null;offsets:boolean};
export type Model={ready:boolean;visible:boolean;autoPlay:boolean;reduced:boolean;preview:number|null;generation:number;sequence:number;epoch:number;manual:Scene|null;session:Session|null;event:Event|null;decoration:{time:number;from:Appearance}|null;comparison:{time:number;from:number;target:number;duration:number}|null;handed:boolean;kept:boolean;previous:'low'|'high';ignored:number[]};
export function initial(now:number,autoPlay=false,reduced=false,preview:number|null=null):Model {'worklet';return {ready:false,visible:true,autoPlay,reduced,preview,generation:0,sequence:-1,epoch:now,manual:null,session:null,event:null,decoration:null,comparison:null,handed:false,kept:false,previous:'low',ignored:[]};}
export function render(m:Model,now:number,images:Bitmap[],canonical:SkImage):{scene:Scene;velocity:Params} {'worklet';
  const base=original(canonical,m.kept);
  if(!m.ready)return {scene:base,velocity:zero()};
  if(!m.reduced&&(m.preview!==null||(!m.session&&!m.event&&m.autoPlay)))return authored(m.preview??now-m.epoch,images,m.kept);
  if(!m.reduced&&!m.session&&!m.event)return {scene:base,velocity:zero()};
  let scene={...(m.manual??base),kept:m.kept};const e=m.event;
  if(!m.reduced&&e?.phase===2&&e.frames){const age=clamp(now-e.time-2,0,.60),r=sample(e.frames,[0,.12,.24,.36,.48,.60],age),u=cubic(age/.60),a=scene.appearance;return {scene:{...scene,pair:r.pair,params:r.params,appearance:{bracketAlpha:a.bracketAlpha+(1-a.bracketAlpha)*u,handleScale:a.handleScale+(1-a.handleScale)*u,anticipation:a.anticipation*(1-u),lowOffset:a.lowOffset*(1-u),highOffset:a.highOffset*(1-u)},compare:scene.compare*(1-u),kept:m.kept},velocity:r.velocity};}
  if(m.decoration){const d=m.decoration,u=smooth((now-d.time)/.12);scene={...scene,appearance:{...scene.appearance,bracketAlpha:d.from.bracketAlpha+(1-d.from.bracketAlpha)*u,handleScale:d.from.handleScale+(1-d.from.handleScale)*u,anticipation:d.from.anticipation*(1-u)}};}
  if(m.comparison){const c=m.comparison;scene={...scene,compare:c.from+(c.target-c.from)*smooth((now-c.time)/c.duration)};}
  if(e?.offsets)scene={...scene,appearance:{...scene.appearance,lowOffset:spring(e.from.appearance.lowOffset,0,0,now-e.time),highOffset:spring(e.from.appearance.highOffset,0,0,now-e.time)}};
  return {scene,velocity:zero()};
}
export function ticking(m:Model) {'worklet';return m.ready&&m.visible&&m.preview===null&&(m.decoration!==null||m.comparison!==null||(!m.session&&(m.event?m.event.phase===2||m.event.offsets:!m.reduced&&m.autoPlay)));}
export function scope(m:Model,scene:Scene):Model {'worklet';return {...m,generation:m.generation+1,sequence:-1,manual:scene,event:null,decoration:null,comparison:null,handed:false};}
export function hit(m:Model,scene:Scene,x:number,y:number):Owner|null {'worklet';if(!m.ready||m.preview!==null||!m.visible)return null;const p=scene.params,a=scene.appearance;
  if(x>=280&&x<=369&&y>=532&&y<=576)return 'compare';if(x>=307&&x<=351&&y>=678&&y<=722)return 'dial';
  if(x>=2&&x<=391&&y>=609&&y<=653){const da=Math.abs(x-(24+345*p.low-a.anticipation+a.lowOffset)),db=Math.abs(x-(24+345*p.high+a.anticipation+a.highOffset));return da===db?m.previous:da<db?'low':'high';}
  if(x>=312+p.roiX&&x<=356+p.roiX&&y>=456+p.roiY&&y<=500+p.roiY)return 'region';
  return x>=29&&x<=364&&y>=136&&y<=520?'image':null;
}
export function begin(m:Model,scene:Scene,velocity:Params,id:number,owner:Owner,x:number,y:number,now:number):Model {'worklet';const next=scope(m,scene);return {...next,previous:owner==='low'||owner==='high'?owner:m.previous,session:{id,owner,base:scene,x,y,lastX:x,lastY:y,lastTime:now,inherited:velocity,velocity:zero(),pointerY:0,changed:false,band:-1,hapticTime:-1e9},comparison:owner==='compare'?{time:now,from:scene.compare,target:1,duration:.12}:null};}
export function moved(m:Model,x:number,y:number,now:number):{model:Model;request:Params|null;haptic:boolean} {'worklet';const s=m.session;if(!s||(x===s.lastX&&y===s.lastY))return {model:m,request:null,haptic:false};const dt=Math.max(1/240,now-s.lastTime),vx=clamp((x-s.lastX)/dt,-1200,1200),vy=clamp((y-s.lastY)/dt,-1200,1200),dx=x-s.x,dy=y-s.y;let p={...s.base.params},v=zero(),manual=m.manual,haptic=false,band=s.band,hapticTime=s.hapticTime;
  if(s.owner==='image'){p={...p,q:p.q-dy/220,roiX:p.roiX+dx*.35};v={...v,q:-vy/220,roiX:vx*.35};}
  else if(s.owner==='region'){p={...p,roiX:p.roiX+dx,roiY:p.roiY+dy};v={...v,roiX:vx,roiY:vy};}
  else if(s.owner==='low'){p={...p,low:clamp(p.low+dx/345,.08,p.high-.08)};v={...v,low:vx/345};}
  else if(s.owner==='high'){p={...p,high:clamp(p.high+dx/345,p.low+.08,.92)};v={...v,high:vx/345};}
  else if(s.owner==='dial'){const a=Math.atan2(s.y-700,s.x-329),b=Math.atan2(y-700,x-329),old=Math.atan2(s.lastY-700,s.lastX-329);p={...p,angle:clamp(p.angle+Math.atan2(Math.sin(b-a),Math.cos(b-a))*180/Math.PI,-90,90)};v={...v,angle:Math.atan2(Math.sin(b-old),Math.cos(b-old))*180/Math.PI/dt};}
  if(s.owner==='low'||s.owner==='high'){const isLow=s.owner==='low',raw=(isLow?s.base.params.low:s.base.params.high)+dx/345,actual=isLow?p.low:p.high,prior=isLow?s.base.appearance.lowOffset:s.base.appearance.highOffset,offset=rubber((raw-actual)*345)+prior*(1-smooth(Math.abs(dx)/44)),before=manual??s.base;manual={...before,appearance:{...before.appearance,...(isLow?{lowOffset:offset}:{highOffset:offset})}};const b=Math.floor(actual/.25);if(b!==band&&now-hapticTime>=.08){band=b;hapticTime=now;haptic=true;}}
  p=bounded(p);const request=s.owner!=='compare'&&!same(quantized(p),quantized((manual??s.base).params))?p:null;
  return {model:{...m,manual,session:{...s,lastX:x,lastY:y,lastTime:now,velocity:v,pointerY:vy,changed:true,band,hapticTime}},request,haptic};
}
export function finish(m:Model,scene:Scene,now:number,cancelled=false):Model {'worklet';const s=m.session;if(!s)return m;const weight=1-smooth(Math.max(0,now-s.lastTime)/.12),velocity=mix(zero(),s.changed?s.velocity:s.inherited,weight),next=scope(m,scene),hold=s.owner==='compare'&&!cancelled;
  return {...next,session:null,event:{generation:next.generation,time:now,from:scene,target:cancelled?rest():settledTarget(scene.params,s.pointerY*weight),velocity,duration:cancelled?.18:.60,hold,phase:0,settled:false,requested:false,frames:null,offsets:true},comparison:s.owner==='compare'?{time:now,from:scene.compare,target:0,duration:.16}:null};
}
export function published(m:Model,scene:Scene,generation:number,sequence:number,bitmap:Bitmap,now:number):Model {'worklet';if(generation!==m.generation||sequence<=m.sequence||!m.visible)return m;const changed=!same(quantized(scene.params),bitmap.params),manual={...(m.manual??scene),pair:single(bitmap),params:bitmap.params};return {...m,sequence,manual,kept:changed?false:m.kept,handed:m.handed||changed,decoration:changed&&!m.handed?{time:now,from:scene.appearance}:m.decoration};}
export function saved(m:Model,scene:Scene,now:number):Model {'worklet';if(!m.ready||m.session||m.preview!==null)return m;const n=scope(m,scene);return {...n,kept:true,event:{generation:n.generation,time:now,from:scene,target:scene.params,velocity:zero(),duration:0,hold:true,phase:0,settled:false,requested:false,frames:null,offsets:true}};}
export function recoveryKnots(p:Params):Params[] {'worklet';return [recoveryParams(p,0),recoveryParams(p,.12),recoveryParams(p,.24),recoveryParams(p,.36),recoveryParams(p,.48),recoveryParams(p,.60)];}
