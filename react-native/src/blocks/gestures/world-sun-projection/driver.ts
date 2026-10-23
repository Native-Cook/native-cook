import { authored,blend,bounded,clamp,project,reducedPose,released,rest,smooth,sun,type Point,type Pose } from './motion';
export type Session={id:number;from:Pose;pose:Pose;down:Point;last:Point;lastStamp:number;mode:0|1|2;timeHit:boolean;vy:number;vp:number;hour:number;haptic:number};
export type Event={id:number;stamp:number;from:Pose;target:Pose|null;vy:number;vp:number;phase:0|1|2};
export type Model={now:number;epoch:number;visible:boolean;generation:number;session:Session|null;event:Event|null;reduced:Pose;grid:boolean;frozen:boolean;frozenQ:number;information:boolean;ignored:number[]};
export function initial():Model{'worklet';return{now:0,epoch:0,visible:false,generation:0,session:null,event:null,reduced:reducedPose(),grid:false,frozen:false,frozenQ:0,information:false,ignored:[]};}
export function reset(m:Model,now:number,visible:boolean):Model{'worklet';return{...m,now,epoch:now,visible,generation:m.generation+1,session:null,event:null,ignored:[]};}
export function render(m:Model,autoPlay:boolean,reduced:boolean,preview?:number):Pose{
 'worklet';let p:Pose;if(preview!==undefined)p=authored(preview);else if(m.session)p=m.session.pose;else if(m.event){const e=m.event,age=e.phase===1?(e.target?.35:.6):Math.max(0,m.now-e.stamp);p=e.target?(reduced?e.target:blend(e.from,e.target,smooth(age/.35))):released(e.from,e.vy,e.vp,age,reduced);if(!reduced&&age>=2)p=age<2.6?blend(e.target??e.from,rest(),smooth((age-2)/.6)):autoPlay?authored(age-2.6):rest();}else p=reduced?m.reduced:autoPlay?authored(Math.max(0,m.now-m.epoch)):rest();return m.frozen?{...p,q:m.frozenQ}:p;
}
export function moving(m:Model,autoPlay:boolean,reduced:boolean,preview?:number){'worklet';return m.visible&&preview===undefined&&!m.session&&(m.event?m.event.phase!==1:!reduced&&autoPlay);}
export function complete(m:Model,id:number,phase:1|2|3,now:number,reduced:boolean):Model{'worklet';const e=m.event;if(!e||e.id!==id)return m;if(reduced)return{...m,now,reduced:e.target??e.from,event:null};return phase===3?{...m,now,epoch:e.stamp+2.6,event:null}:{...m,now,event:{...e,phase}};}
export function begin(m:Model,p:Pose,id:number,point:Point,now:number):Model{
 'worklet';if(m.session||m.ignored.length)return{...m,ignored:[...m.ignored,id]};const anchor=project(sun(p.q),p),hit=anchor!==null&&Math.hypot(point.x-anchor.x,point.y-anchor.y)<=44;return{...m,now,event:null,session:{id,from:{...p},pose:{...p},down:point,last:point,lastStamp:now,mode:0,timeHit:point.x>=331||hit,vy:0,vp:0,hour:Math.floor((309+p.q*350)/60),haptic:-1e10}};
}
export function move(m:Model,id:number,point:Point,now:number):Model{
 'worklet';const s=m.session;if(!s||s.id!==id||(s.last.x===point.x&&s.last.y===point.y))return m;const dx=point.x-s.down.x,dy=point.y-s.down.y,mode=s.mode===0&&Math.hypot(dx,dy)>=8?(s.timeHit?2:1):s.mode;let p={...s.pose};if(mode===2)p.q=clamp(s.from.q-dy/430);else if(mode===1)p=bounded({...p,yaw:s.from.yaw-.1*dx,pitch:s.from.pitch-.12*dy});const dt=Math.max(1/240,now-s.lastStamp);return{...m,now,frozenQ:m.frozen&&mode===2?p.q:m.frozenQ,session:{...s,pose:p,mode,last:point,lastStamp:now,vy:clamp((p.yaw-s.pose.yaw)/dt,-180,180),vp:clamp((p.pitch-s.pose.pitch)/dt,-180,180),hour:Math.floor((309+p.q*350)/60)}};
}
export function end(m:Model,id:number,point:Point|null,now:number,reduced:boolean):Model{
 'worklet';if(!m.session||m.session.id!==id)return{...m,ignored:m.ignored.filter(n=>n!==id)};if(point)m=move(m,id,point,now);const s=m.session!,generation=m.generation+1,fresh=point!==null&&now-s.lastStamp<=.12;return{...m,now,generation,session:null,reduced:reduced?s.pose:m.reduced,event:reduced?null:{id:generation,stamp:now,from:s.pose,target:null,vy:fresh?s.vy:0,vp:fresh?s.vp:0,phase:0}};
}
export function action(m:Model,p:Pose,now:number,kind:'time'|'grid'|'reset'|'freeze'|'information',value:number,reduced:boolean):Model{
 'worklet';const generation=m.generation+1,target=kind==='time'?{...p,q:clamp(value)}:kind==='reset'?{yaw:0,pitch:-8.5,q:p.q}:null;return{...m,now,generation,grid:kind==='grid'?!m.grid:m.grid,information:kind==='information'?!m.information:m.information,frozen:kind==='freeze'?!m.frozen:m.frozen,frozenQ:kind==='time'&&m.frozen?clamp(value):kind==='freeze'?p.q:m.frozenQ,reduced:reduced?(target??p):m.reduced,event:reduced&&!target?null:{id:generation,stamp:now,from:p,target,vy:0,vp:0,phase:0}};
}
