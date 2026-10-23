import { activeSpan, authored, clamp, clone, finalScene, held, local, nearest, recovering, released, resolving, rest, smooth, spanProgress, ARCS, type Point, type Scene } from './motion';

export type MaterialChange={stamp:number;materials:number[];soles:number[];targetMaterials:number[];targetSoles:number[]};
export type Confirmation={stamp:number;ageAtAnchor:number;startMix:number;anchorMix:number};
export type Presentation={materials:number[];soles:number[];change:MaterialChange|null;confirmation:Confirmation|null;held:{scene:Scene;saveAge:number|null;saveStartMix:number}|null};
export type Session={id:number;from:Scene;downDate:number;anchor:Point;last:Point;lastDate:number;lagDate:number;progress:number;tensions:number[];stage:number;pullAnchor:Point|null;pullTensions:number[];lag:number;overpull:Point;lastSigned:number;passageDate:number|null;supportActive:boolean;terminal:Scene|null};
export type Event={id:number;stamp:number;from:Scene;downAge:number;rows:number|null;phase:0|1|2};
export type Motion={now:number;epoch:number;visible:boolean;generation:number;selectedLeather:number;selectedSole:number;favorite:boolean;session:Session|null;event:Event|null;presentation:Presentation;reducedScene:Scene;ignored:number[]};
export function weights(n:number,index:number){ 'worklet';const a:number[]=[];for(let i=0;i<n;i++)a.push(i===index?1:0);return a; }
export function initialPresentation():Presentation{ 'worklet';return{materials:[1,0,0],soles:[1,0],change:null,confirmation:null,held:null}; }
export function confirmationAge(c:Confirmation,now:number){ 'worklet';return c.ageAtAnchor+Math.max(0,now-c.stamp); }
export function confirmationMix(c:Confirmation,now:number){ 'worklet';if(now<=c.stamp)return c.anchorMix;const age=confirmationAge(c,now),rise=c.startMix+(1-c.startMix)*smooth(age/.12);return rise*(1-smooth((age-1.2)/.18)); }
export function presentationValues(p:Presentation,now:number){
  'worklet';if(p.held)return{materials:p.held.scene.materials,soles:p.held.scene.soles,savedMix:p.held.scene.savedMix};
  let materials=p.materials,soles=p.soles;if(p.change){const c=p.change,u=smooth((now-c.stamp)/.18);materials=c.materials.map((v,i)=>v+(c.targetMaterials[i]-v)*u);soles=c.soles.map((v,i)=>v+(c.targetSoles[i]-v)*u);}
  return{materials,soles,savedMix:p.confirmation?confirmationMix(p.confirmation,now):0};
}
export function capturedPresentation(p:Presentation,s:Scene,displayedAt:number):Presentation{ 'worklet';return{materials:[...s.materials],soles:[...s.soles],change:null,confirmation:null,held:{scene:clone(s),saveAge:p.confirmation?Math.min(1.38,confirmationAge(p.confirmation,displayedAt)):null,saveStartMix:p.confirmation?.startMix??0}}; }
export function releasedPresentation(p:Presentation,leather:number,sole:number,now:number):Presentation{
  'worklet';if(!p.held)return p;const cap=p.held,materials=cap.scene.materials,soles=cap.scene.soles,tm=weights(3,leather),ts=weights(2,sole),changed=materials.some((v,i)=>v!==tm[i])||soles.some((v,i)=>v!==ts[i]);
  return{materials,soles,held:null,change:changed?{stamp:now,materials,soles,targetMaterials:tm,targetSoles:ts}:null,confirmation:cap.saveAge===null?null:{stamp:now,ageAtAnchor:cap.saveAge,startMix:cap.saveStartMix,anchorMix:cap.scene.savedMix}};
}
export function initialMotion():Motion{ 'worklet';return{now:0,epoch:0,visible:false,generation:0,selectedLeather:0,selectedSole:0,favorite:false,session:null,event:null,presentation:initialPresentation(),reducedScene:finalScene(),ignored:[]}; }
export function resetMotion(m:Motion,now:number,visible:boolean):Motion{ 'worklet';return{...m,now,epoch:now,visible,generation:m.generation+1,session:null,event:null,ignored:[],reducedScene:finalScene(),presentation:{...initialPresentation(),materials:weights(3,m.selectedLeather),soles:weights(2,m.selectedSole)}}; }
export function heldScene(s:Session,now:number):Scene{
  'worklet';if(s.terminal&&!s.supportActive)return clone(s.terminal);const age=Math.max(0,now-s.downDate),lag=s.lag*(1-smooth(Math.max(0,now-s.lagDate)/.09));
  const r=held(s.from,s.progress,s.tensions,age,lag,s.overpull);if(s.passageDate!==null&&now-s.passageDate<.035)r.tipAlpha=0;return r;
}
export function drive(m:Motion,autoPlay:boolean,reduced:boolean,previewTime:number|undefined):Scene{
  'worklet';let s:Scene;
  if(previewTime!==undefined)s=authored(previewTime);else if(m.session)s=heldScene(m.session,m.now);else if(m.event){const e=m.event,age=Math.max(0,m.now-e.stamp);
    if(e.rows!==null){s=resolving(e.from,e.rows,age);if(!reduced&&age>=2)s=recovering(resolving(e.from,e.rows,.12),age-2);}
    else if(age<2)s=released(e.from,age,e.downAge);else if(age<2.6)s=recovering(released(e.from,2,e.downAge),age-2);else s=autoPlay&&!reduced?authored(age-2.6):rest();
  }else if(reduced)s=clone(m.reducedScene);else s=autoPlay?authored(Math.max(0,m.now-m.epoch)):rest();
  const v=presentationValues(m.presentation,m.now);s.materials=[...v.materials];s.soles=[...v.soles];s.savedMix=v.savedMix;return s;
}
export function moving(m:Motion,autoPlay:boolean,reduced:boolean,previewTime:number|undefined){
  'worklet';if(!m.visible||previewTime!==undefined)return false;const p=m.presentation;if(!p.held&&(p.change||p.confirmation))return true;if(m.session)return m.session.supportActive;if(m.event)return m.event.phase!==1;return !reduced&&autoPlay;
}
export function tick(m:Motion,now:number):Motion{
  'worklet';let session=m.session,p=m.presentation;
  if(session?.supportActive){const end=Math.max(session.downDate+.43,session.lagDate+.09);if(now>=end)session={...session,supportActive:false,terminal:heldScene(session,end)};}
  if(!p.held){let change=p.change,confirmation=p.confirmation,materials=p.materials,soles=p.soles;
    if(change&&now>=change.stamp+.18){materials=change.targetMaterials;soles=change.targetSoles;change=null;}
    if(confirmation&&now>=confirmation.stamp+Math.max(0,1.38-confirmation.ageAtAnchor))confirmation=null;
    if(change!==p.change||confirmation!==p.confirmation)p={...p,materials,soles,change,confirmation};
  }return{...m,now,session,presentation:p};
}
export function beginSession(m:Motion,s:Scene,id:number,world:Point,now:number):Motion{
  'worklet';const pt=local(world);return{...m,now,generation:m.generation+1,event:null,presentation:capturedPresentation(m.presentation,s,m.now),session:{id,from:clone(s),downDate:now,anchor:pt,last:pt,lastDate:now,lagDate:now,progress:s.progress,tensions:[...s.tensions],stage:activeSpan(s.progress),pullAnchor:s.progress>=6?pt:null,pullTensions:[...s.tensions],lag:s.velocityLag,overpull:{...s.overpull},lastSigned:-Infinity,passageDate:null,supportActive:true,terminal:null}};
}
export function movedSession(s:Session,world:Point,now:number):Session{
  'worklet';const pt=local(world);if(pt.x===s.last.x&&pt.y===s.last.y)return s;const dt=Math.max(1/240,now-s.lastDate),speed=Math.hypot(pt.x-s.last.x,pt.y-s.last.y)/dt;
  let progress=s.progress,tensions=[...s.tensions],overpull={...s.overpull},stage=s.stage,pullAnchor=s.pullAnchor,pullTensions=s.pullTensions,lastSigned=s.lastSigned,passageDate=s.passageDate;
  if(pullAnchor){const delta=.85*(pullAnchor.y-pt.y)/36;tensions=pullTensions.map(v=>clamp(v+delta));const raw=pullTensions[5]+delta,outside=(raw-clamp(raw))*36/.85;overpull={x:0,y:-clamp(outside/(1+Math.abs(outside)/48)*.45,-6,6)};
  }else{const pointer={x:s.from.tip.x+pt.x-s.anchor.x,y:s.from.tip.y+pt.y-s.anchor.y},a=ARCS[stage],projection=nearest(stage,pointer);let candidate=spanProgress(stage,projection.fraction);
    if(stage>0){const previous=nearest(stage-1,pointer);if(previous.distance<projection.distance)candidate=spanProgress(stage-1,previous.fraction);}
    const endpoint=a.points[31],before=a.points[30],nx=endpoint[0]-before[0],ny=endpoint[1]-before[1],signed=(pointer.x-endpoint[0])*nx+(pointer.y-endpoint[1])*ny,distance=Math.hypot(pointer.x-endpoint[0],pointer.y-endpoint[1]);
    if(distance<=9&&signed>=0&&lastSigned<=0){candidate=spanProgress(stage,1);passageDate=now;if(stage<10){stage++;lastSigned=-Infinity;}else{pullAnchor=pt;pullTensions=tensions;}}
    else{candidate=Math.min(candidate,spanProgress(stage,1)-1e-5);lastSigned=signed;}progress=clamp(candidate,0,6);
  }
  return{...s,progress,tensions,overpull,stage,pullAnchor,pullTensions,lastSigned,passageDate,last:pt,lastDate:now,lagDate:now,lag:Math.min(3,speed*.003),supportActive:true,terminal:null};
}
export function finishEvent(m:Motion,id:number,phase:1|2|3,now:number,reduced:boolean):Motion{
  'worklet';const e=m.event;if(!e||e.id!==id)return m;
  if(reduced&&phase===1)return{...m,now,reducedScene:drive({...m,now:e.stamp+.43},false,true,undefined),event:null};
  if(phase===3)return{...m,now,epoch:e.stamp+2.6,event:null};return{...m,now,event:{...e,phase}};
}
export function actionEvent(m:Motion,s:Scene,now:number,reduced:boolean):Motion{ 'worklet';return reduced?{...m,now,reducedScene:clone(s),event:null}:{...m,now,generation:m.generation+1,event:{id:m.generation+1,stamp:now,from:clone(s),downAge:0,rows:null,phase:0}}; }
export function withoutID(ids:number[],id:number){ 'worklet';const a:number[]=[];for(const v of ids)if(v!==id)a.push(v);return a; }
