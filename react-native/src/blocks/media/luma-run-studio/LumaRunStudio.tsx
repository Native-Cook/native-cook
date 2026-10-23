import { Canvas, Group, matchFont, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, Platform, Pressable, StyleSheet, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector, type GestureTouchEvent, type GestureStateManager } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { draw, type Fonts } from './art';
import { begin, finish, hit, initial, moved, published, recoveryKnots, render, saved, scope, ticking, type Event, type Model, type Owner } from './driver';
import { bounded, editing, original, quantized, reducedParams, same, settlement, mix, rest, smooth, zero, type Bitmap, type Params, type Scene } from './motion';
import { resources, type BaseResources, type Resources } from './resources';
import { LumaRunStudioWorker } from './worker';

export { LOOP } from './motion';
type Props={autoPlay?:boolean;previewTime?:number;onBack?:()=>void};
function impact(){Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(()=>{});}
function withoutID(ids:number[],id:number){'worklet';const out:number[]=[];for(const value of ids)if(value!==id)out.push(value);return out;}
class LumaRunStudioService {
  worker:LumaRunStudioWorker|null=null;
  timers:ReturnType<typeof setTimeout>[]=[];
  generation=0;
  clear(){for(const timer of this.timers)clearTimeout(timer);this.timers=[];}
  reset(generation:number){this.generation=generation;this.clear();this.worker?.invalidate(generation);}
  request(params:Params,generation:number){if(generation!==this.generation)return;this.worker?.request(params,generation);}
  later(event:Event,age:number,callback:()=>void){this.timers.push(setTimeout(()=>{if(this.generation===event.generation)callback();},Math.max(0,(event.time+age-Date.now()/1000)*1000)));}
  close(){this.clear();this.worker?.close();this.worker=null;}
}
function Adjustable({owner,scene,s,onAdjust}:{owner:Owner;scene:SharedValue<Scene|null>;s:number;onAdjust:(owner:Owner,delta:number)=>void}){
  const props=useAnimatedProps<ViewProps>(()=>{const p=scene.get()?.params??rest();const label=owner==='image'?`Canyon preview, Strength ${Math.round(p.q*100)} percent`:owner==='region'?`Sorting region, Horizontal ${Math.round(p.roiX)}, vertical ${Math.round(p.roiY)}`:owner==='dial'?`Sort direction, ${Math.round(p.angle)} degrees`:`${owner==='low'?'Low':'High'} luma threshold, ${Math.round((owner==='low'?p.low:p.high)*100)} percent`;return {accessibilityLabel:label};});
  const style=useAnimatedStyle(()=>{const p=scene.get()?.params??rest(),a=scene.get()?.appearance??editing();if(owner==='region')return {left:(312+p.roiX)*s,top:(456+p.roiY)*s};if(owner==='low')return {left:(24+345*p.low-a.anticipation+a.lowOffset-22)*s};if(owner==='high')return {left:(24+345*p.high+a.anticipation+a.highOffset-22)*s};return {};});
  const rect=owner==='image'?[29,136,335,384]:owner==='region'?[312,456,44,44]:owner==='dial'?[307,678,44,44]:[0,609,44,44];
  return <Animated.View accessible pointerEvents="none" accessibilityRole="adjustable" accessibilityActions={[{name:'increment'},{name:'decrement'}]} onAccessibilityAction={e=>onAdjust(owner,(e.nativeEvent.actionName==='increment'?1:-1)*(owner==='dial'?15:owner==='low'||owner==='high'?.01:owner==='region'?5:.1))} animatedProps={props} style={[{position:'absolute',left:rect[0]*s,top:rect[1]*s,width:rect[2]*s,height:rect[3]*s},style]}/>;
}
/** Local luminance-bounded sorting with exact cached loop knots and one latest-intent worker. */
export function LumaRunStudio({autoPlay=false,previewTime,onBack}:Props){
  const router=useRouter();
  const initialReduced=useReducedMotion(),safe=useSafeAreaInsets();
  const [reduced,setReduced]=useState(initialReduced),[art,setArt]=useState<BaseResources|null>(null),[loaded,setLoaded]=useState<Resources|null>(null),[size,setSize]=useState({width:0,height:0});
  const [service]=useState(()=>new LumaRunStudioService());
  const m=useSharedValue<Model>(initial(0,autoPlay,reduced,previewTime??null));
  const clock=useSharedValue(0),scale=size.width/393,height=scale>0?Math.max(852,size.height/scale):852,bottom=scale>0?Math.max(0,safe.bottom/scale-34):0;
  const bitmaps=useMemo(()=>loaded?.loop??[],[loaded]);
  const drawingArt=useMemo(()=>art?{canonical:art.canonical,frame:art.frame,mask:art.mask}:null,[art]);
  const result=useDerivedValue(()=>drawingArt?render(m.get(),clock.get(),bitmaps,drawingArt.canonical):null);
  const scene=useDerivedValue(()=>result.get()?.scene??null);
  const fontFamily=Platform.OS==='ios'?'Helvetica Neue':'sans-serif';
  const fonts=useMemo<Fonts>(()=>({title:matchFont({fontFamily,fontSize:18,fontWeight:'500'}),mode:matchFont({fontFamily,fontSize:15,fontWeight:'500'}),small:matchFont({fontFamily,fontSize:11}),label:matchFont({fontFamily,fontSize:13,fontWeight:'500'}),number:matchFont({fontFamily,fontSize:12}),degree:matchFont({fontFamily,fontSize:19}),button:matchFont({fontFamily,fontSize:15,fontWeight:'600'}),hint:matchFont({fontFamily,fontSize:10})}),[fontFamily]);
  const picture=useDerivedValue(()=>draw(scene.get(),drawingArt,fonts,height,bottom,m.get().ready));
  const ticker=useFrameCallback(()=>{clock.set(Date.now()/1000);},false);
  const activate=useCallback((active:boolean)=>ticker.setActive(active),[ticker]);
  useAnimatedReaction(()=>ticking(m.get()),(active,previous)=>{if(active!==previous)scheduleOnRN(activate,active);},[activate]);
  const resetWorker=useCallback((generation:number)=>service.reset(generation),[service]);
  const request=useCallback((params:Params,generation:number)=>service.request(params,generation),[service]);
  const workerRecovery=useCallback((params:Params[],generation:number)=>{if(generation===service.generation)service.worker?.recovery(params,generation);},[service]);
  const tryRecovery=useCallback((generation:number)=>{'worklet';const old=m.get(),e=old.event;if(!e||old.reduced||generation!==old.generation||!e.settled||e.requested)return;const p=(old.manual??e.from).params;if(!e.hold&&!same(quantized(p),quantized(e.target)))return;m.set({...old,event:{...e,requested:true}});scheduleOnRN(workerRecovery,recoveryKnots(p),generation);},[m,workerRecovery]);
  const complete=useCallback((generation:number,kind:number)=>{'worklet';const old=m.get();if(old.generation!==generation||!old.visible)return;const now=Date.now()/1000;clock.set(now);if(kind===0&&old.decoration){const manual=old.manual?{...old.manual,appearance:{...old.manual.appearance,bracketAlpha:1,handleScale:1,anticipation:0}}:null;m.set({...old,manual,decoration:null});return;}if(kind===1&&old.comparison){m.set({...old,manual:old.manual?{...old.manual,compare:old.comparison.target}:null,comparison:null});return;}const e=old.event;if(!e)return;if(kind===2){m.set({...old,manual:old.manual?{...old.manual,appearance:{...old.manual.appearance,lowOffset:0,highOffset:0}}:null,event:{...e,settled:true,offsets:false}});tryRecovery(generation);}else if(kind===3)m.set({...old,event:{...e,phase:2}});else if(kind===4){const next={...old,generation:generation+1,manual:null,event:null,decoration:null,comparison:null,epoch:e.time+2.60};m.set(next);scheduleOnRN(resetWorker,next.generation);}else if(kind===5)m.set({...old,event:null});},[clock,m,resetWorker,tryRecovery]);
  const scheduleComparison=useCallback((generation:number,duration:number)=>{service.timers.push(setTimeout(()=>scheduleOnUI(complete,generation,1),duration*1000));},[complete,service]);
  const scheduleAppearance=useCallback((generation:number)=>{service.timers.push(setTimeout(()=>scheduleOnUI(complete,generation,0),120));},[complete,service]);
  const commitImage=useCallback((generation:number,sequence:number,bitmap:Bitmap)=>{'worklet';const old=m.get(),visible=scene.get();if(!visible)return;const now=Date.now()/1000,next=published(old,visible,generation,sequence,bitmap,now);if(next===old)return;clock.set(now);m.set(next);if(!old.decoration&&next.decoration)scheduleOnRN(scheduleAppearance,generation);tryRecovery(generation);},[clock,m,scene,scheduleAppearance,tryRecovery]);
  const commitRecovery=useCallback((generation:number,frames:Bitmap[])=>{'worklet';const old=m.get();if(old.generation===generation&&old.event){clock.set(Date.now()/1000);m.set({...old,event:{...old.event,frames}});}},[clock,m]);
  const scheduleEvent=useCallback((event:Event,isReduced:boolean)=>{
    service.reset(event.generation);
    if(isReduced){if(!event.hold)service.request(event.target,event.generation);service.later(event,.12,()=>scheduleOnUI(complete,event.generation,5));return;}
    if(!event.hold){const times=event.duration<.60?[.125,.18]:[.125,.25,.375,.5,.60];for(const age of times)service.later(event,age,()=>service.request(event.duration<.60?mix(event.from.params,rest(),smooth(age/.18)):settlement(event.from.params,event.velocity,event.target,age),event.generation));}
    service.later(event,event.hold?0:event.duration,()=>scheduleOnUI(complete,event.generation,2));service.later(event,2,()=>scheduleOnUI(complete,event.generation,3));service.later(event,2.60,()=>scheduleOnUI(complete,event.generation,4));
  },[complete,service]);
  const reset=useCallback((visible:boolean)=>{'worklet';const old=m.get(),now=Date.now()/1000,next={...initial(now,autoPlay,reduced,previewTime??null),generation:old.generation+1,ready:loaded!==null,visible};clock.set(now);m.set(next);scheduleOnRN(resetWorker,next.generation);if(next.ready&&reduced&&drawingArt){const start=original(drawingArt.canonical);m.set({...next,manual:start});scheduleOnRN(request,reducedParams(),next.generation);}},[autoPlay,clock,drawingArt,loaded,m,previewTime,reduced,request,resetWorker]);
  useEffect(()=>{let alive=true;void resources(base=>{if(alive)setArt(base);}).then(value=>{if(!alive)return;const worker=new LumaRunStudioWorker(value.rgba,value.canonical);worker.onImage=(g,n,b)=>scheduleOnUI(commitImage,g,n,b);worker.onRecovery=(g,frames)=>scheduleOnUI(commitRecovery,g,frames);service.worker=worker;setLoaded(value);AccessibilityInfo.announceForAccessibility('Local preview ready');}).catch(()=>{});return()=>{alive=false;service.close();};},[commitImage,commitRecovery,service]);
  useEffect(()=>{let current=true;void AccessibilityInfo.isReduceMotionEnabled().then(value=>{if(current)setReduced(value);});const sub=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>{current=false;sub.remove();};},[]);
  useEffect(()=>{scheduleOnUI(reset,AppState.currentState==='active');},[reset]);
  useEffect(()=>{const sub=AppState.addEventListener('change',state=>scheduleOnUI(reset,state==='active'));return()=>sub.remove();},[reset]);
  useEffect(()=>()=>{ticker.setActive(false);service.clear();},[service,ticker]);
  const onLayout=useCallback((e:LayoutChangeEvent)=>setSize(e.nativeEvent.layout),[]);
  const save=useCallback(()=>{'worklet';const old=m.get(),visible=scene.get();if(!visible)return;const now=Date.now()/1000,next=saved(old,visible,now);if(next===old)return;clock.set(now);m.set(next);scheduleOnRN(scheduleEvent,next.event!,next.reduced);scheduleOnRN(impact);},[clock,m,scene,scheduleEvent]);
  const keep=useCallback(()=>scheduleOnUI(save),[save]);
  const leave=useCallback(()=>{const current=m.get();if(!current.visible||current.session)return;service.reset(service.generation+1);scheduleOnUI(reset,false);if(onBack)onBack();else router.back();},[m,onBack,reset,router,service]);
  const adjust=useCallback((owner:Owner,delta:number)=>{'worklet';const old=m.get(),visible=scene.get();if(!visible||!old.ready||old.session||old.preview!==null)return;const now=Date.now()/1000;let target={...visible.params};if(owner==='low')target.low=Math.min(target.high-.08,Math.max(.08,target.low+delta));else if(owner==='high')target.high=Math.min(.92,Math.max(target.low+.08,target.high+delta));else if(owner==='dial')target.angle=Math.min(90,Math.max(-90,target.angle+delta));else if(owner==='region')target.roiX=Math.min(30,Math.max(-30,target.roiX+delta));else target.q=Math.min(1,Math.max(0,target.q+delta));target=bounded(target);const n=scope(old,visible),event:Event={generation:n.generation,time:now,from:visible,target,velocity:zero(),duration:.60,hold:false,phase:0,settled:false,requested:false,frames:null,offsets:true};clock.set(now);m.set({...n,event});scheduleOnRN(scheduleEvent,event,n.reduced);scheduleOnRN(impact);},[clock,m,scene,scheduleEvent]);
  const onAdjust=useCallback((owner:Owner,delta:number)=>scheduleOnUI(adjust,owner,delta),[adjust]);
  const compareAction=useCallback(()=>{'worklet';const old=m.get(),visible=scene.get();if(!visible||!old.ready||old.session||old.preview!==null)return;const now=Date.now()/1000,n=scope(old,visible),event:Event={generation:n.generation,time:now,from:visible,target:visible.params,velocity:zero(),duration:0,hold:true,phase:0,settled:false,requested:false,frames:null,offsets:true};clock.set(now);m.set({...n,event,comparison:{time:now,from:visible.compare,target:visible.compare>=.5?0:1,duration:.16}});scheduleOnRN(scheduleEvent,event,n.reduced);scheduleOnRN(scheduleComparison,n.generation,.16);},[clock,m,scene,scheduleComparison,scheduleEvent]);
  const onCompare=useCallback(()=>scheduleOnUI(compareAction),[compareAction]);
  const end=useCallback((x:number,y:number,cancelled:boolean)=>{'worklet';let old=m.get();if(!old.session)return;if(!cancelled){const changed=moved(old,x,y,Date.now()/1000);old=changed.model;if(changed.request)scheduleOnRN(request,changed.request,old.generation);}const visible=render(old,clock.get(),bitmaps,drawingArt!.canonical).scene,now=Date.now()/1000,next=finish(old,visible,now,cancelled);clock.set(now);m.set(next);if(next.event)scheduleOnRN(scheduleEvent,next.event,next.reduced);if(next.comparison)scheduleOnRN(scheduleComparison,next.generation,.16);if(!cancelled)scheduleOnRN(impact);},[bitmaps,clock,drawingArt,m,request,scheduleComparison,scheduleEvent]);
  const touchDown=useCallback((e:GestureTouchEvent,manager:GestureStateManager)=>{'worklet';const old=m.get();if(old.session){const ids=[...old.ignored];for(const t of e.changedTouches)if(t.id!==old.session.id)ids.push(t.id);m.set({...old,ignored:ids});return;}if(old.ignored.length||!drawingArt||!scene.get()||scale<=0){manager.fail();return;}const t=e.changedTouches[0];if(!t){manager.fail();return;}const x=t.x/scale,y=t.y/scale,owner=hit(old,scene.get()!,x,y);if(!owner){manager.fail();return;}const now=Date.now()/1000,next=begin(old,scene.get()!,result.get()!.velocity,t.id,owner,x,y,now);clock.set(now);m.set(next);scheduleOnRN(resetWorker,next.generation);if(owner==='compare')scheduleOnRN(scheduleComparison,next.generation,.12);manager.begin();manager.activate();},[clock,drawingArt,m,resetWorker,result,scale,scene,scheduleComparison]);
  const touchMove=useCallback((e:GestureTouchEvent)=>{'worklet';const old=m.get(),s=old.session;if(!s)return;for(const t of e.allTouches){if(t.id!==s.id)continue;const now=Date.now()/1000,next=moved(old,t.x/scale,t.y/scale,now);if(next.model!==old){clock.set(now);m.set(next.model);if(next.request)scheduleOnRN(request,next.request,old.generation);if(next.haptic)scheduleOnRN(impact);}break;}},[clock,m,request,scale]);
  const touchUp=useCallback((e:GestureTouchEvent,manager:GestureStateManager)=>{'worklet';const old=m.get();let ids=old.ignored;for(const t of e.changedTouches){ids=withoutID(ids,t.id);if(old.session?.id===t.id){end(t.x/scale,t.y/scale,false);manager.end();}}const next=m.get();m.set({...next,ignored:ids});},[end,m,scale]);
  const touchCancel=useCallback((_e:GestureTouchEvent,manager:GestureStateManager)=>{'worklet';const old=m.get(),s=old.session;if(s)end(s.lastX,s.lastY,true);m.set({...m.get(),ignored:[]});manager.fail();},[end,m]);
  const gesture=useMemo(()=>Gesture.Manual().shouldCancelWhenOutside(false).onTouchesDown(touchDown).onTouchesMove(touchMove).onTouchesUp(touchUp).onTouchesCancelled(touchCancel),[touchDown,touchMove,touchUp,touchCancel]);
  const buttonProps=useAnimatedProps<ViewProps>(()=>({accessibilityLabel:m.get().kept?'Edit kept':'Keep this edit'}));
  return <View onLayout={onLayout} style={styles.root}><StatusBar style="light"/><GestureDetector gesture={gesture}><View style={StyleSheet.absoluteFill}>
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Group transform={[{scale:scale||1}]}><Picture picture={picture}/></Group></Canvas>
    <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={leave} style={{position:'absolute',left:20*scale,top:64*scale,width:44*scale,height:44*scale}}/>
    <Animated.View accessible accessibilityRole="button" animatedProps={buttonProps} onAccessibilityTap={keep} style={{position:'absolute',left:329*scale,top:64*scale,width:44*scale,height:44*scale}}><Pressable accessibilityElementsHidden importantForAccessibility="no-hide-descendants" onPress={keep} style={StyleSheet.absoluteFill}/></Animated.View>
    <Adjustable owner="image" scene={scene} s={scale} onAdjust={onAdjust}/><Adjustable owner="region" scene={scene} s={scale} onAdjust={onAdjust}/><Adjustable owner="low" scene={scene} s={scale} onAdjust={onAdjust}/><Adjustable owner="high" scene={scene} s={scale} onAdjust={onAdjust}/><Adjustable owner="dial" scene={scene} s={scale} onAdjust={onAdjust}/>
    <View accessible pointerEvents="none" accessibilityRole="button" accessibilityLabel="Original, hold to compare" onAccessibilityTap={onCompare} style={{position:'absolute',left:280*scale,top:532*scale,width:89*scale,height:44*scale}}/>
    <Animated.View accessible accessibilityRole="button" animatedProps={buttonProps} onAccessibilityTap={keep} style={{position:'absolute',left:24*scale,top:(737-bottom)*scale,width:345*scale,height:56*scale}}><Pressable accessibilityElementsHidden importantForAccessibility="no-hide-descendants" onPress={keep} style={StyleSheet.absoluteFill}/></Animated.View>
    <View accessible pointerEvents="none" accessibilityLabel={loaded?'Local preview ready':'Preparing local preview'} style={{position:'absolute',left:24*scale,top:566*scale,width:230*scale,height:17*scale}}/>
  </View></GestureDetector></View>;
}
const styles=StyleSheet.create({root:{flex:1,backgroundColor:'#171918'}});
