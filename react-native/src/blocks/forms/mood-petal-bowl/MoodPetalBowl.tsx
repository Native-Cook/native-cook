import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { draw } from './art';
import { dateX, days, dominantMood, drive, hexes, initialRecords, rgba, smooth, stepped, stirring, titles, transition, type Motion, type Owner, type Point, type Scene, type Session } from './motion';
import { useMoodResources } from './resources';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number };
const AnimatedInput=Animated.createAnimatedComponent(TextInput);
const AnimatedPressable=Animated.createAnimatedComponent(Pressable);
function impact(){Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(()=>{});}
function ownerBit(owner: Owner){'worklet';return owner==='bowl'?1:2;}
function capitalized(text: string){'worklet';return text.charAt(0)+text.slice(1).toLowerCase();}
function moving(current: Session,point: Point,now: number): Session {
  'worklet';if(current.lastPoint.x===point.x&&current.lastPoint.y===point.y)return current;
  const dt=Math.max(1/240,now-current.lastDate),velocity={x:(point.x-current.lastPoint.x)/dt,y:(point.y-current.lastPoint.y)/dt},translation={x:point.x-current.anchor.x,y:point.y-current.anchor.y};
  return {...current,lastPoint:point,lastDate:now,velocity,translation,current:current.owner==='bowl'?stirring(current.base,current.anchor,translation,velocity):current.current};
}
function displacement(session:Session){'worklet';let distance=0;for(let i=0;i<7;i++)distance=Math.max(distance,Math.hypot(session.base.tokens[i].center.x-session.current.tokens[i].center.x,session.base.tokens[i].center.y-session.current.tokens[i].center.y));return distance;}
class MoodPetalBowlSaveTimer {
  pending: ReturnType<typeof setTimeout>|null=null;
  clear(){if(this.pending!==null)clearTimeout(this.pending);this.pending=null;}
  schedule(stamp: number,finish:()=>void){this.clear();this.pending=setTimeout(finish,Math.max(0,(stamp+2-Date.now()/1000)*1000));}
}
function MoodLabel({pose,mood,s}:{pose:SharedValue<Scene>;mood:number;s:number}){
  const style=useAnimatedStyle(()=>({opacity:pose.get().labelWeights[mood],transform:[{translateX:pose.get().labelOffsets[mood]*s}]}));
  return <Animated.Text accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" allowFontScaling={false} style={[styles.label,{left:60*s,top:451*s,width:273*s,height:55*s,fontSize:49.5*s,lineHeight:57*s,letterSpacing:.6*s},style]}>{titles[mood]}</Animated.Text>;
}
function SavedCaption({pose,s}:{pose:SharedValue<Scene>;s:number}){
  const props=useAnimatedProps(()=>{const scene=pose.get(),text=`Saved for ${days[scene.captionDay]}`;return {text,defaultValue:text,accessibilityLabel:text,accessibilityElementsHidden:scene.captionAlpha<.5,importantForAccessibility:scene.captionAlpha<.5?'no-hide-descendants' as const:'auto' as const};});
  const style=useAnimatedStyle(()=>({opacity:pose.get().captionAlpha}));
  return <AnimatedInput animatedProps={props} editable={false} caretHidden contextMenuHidden allowFontScaling={false} defaultValue="Saved for Thursday" style={[styles.caption,{left:60*s,top:517*s,width:273*s,height:18*s,fontSize:11.5*s},style]} />;
}
function CalendarDay({pose,m,day,s,onChoose}:{pose:SharedValue<Scene>;m:SharedValue<Motion>;day:number;s:number;onChoose:(day:number)=>void}){
  const dot=useAnimatedStyle(()=>{const d=pose.get().dots[day];return {backgroundColor:`rgba(${d.r*255},${d.g*255},${d.b*255},${d.a})`};});
  const props=useAnimatedProps(()=>{const record=m.get().records[day];return {accessibilityValue:{text:record===null?'No mood saved':capitalized(titles[record])},accessibilityState:{selected:pose.get().selectedDay===day}};});
  return <View style={{position:'absolute',left:(dateX[day]-22)*s,top:559*s,width:44*s,height:88*s}}>
    <Animated.View accessible={false} pointerEvents="none" style={[{position:'absolute',left:19*s,top:12*s,width:6*s,height:6*s,borderRadius:3*s},dot]} />
    <Text accessible={false} allowFontScaling={false} style={[styles.day,{top:27*s,height:20*s,fontSize:14*s,lineHeight:20*s}]}>{['Mon','Tue','Wed','Thu','Fri','Sat','Sun'][day]}</Text>
    <Text accessible={false} allowFontScaling={false} style={[styles.date,{top:48.2*s,height:24*s,fontSize:20*s,lineHeight:24*s}]}>{12+day}</Text>
    <AnimatedPressable animatedProps={props} accessibilityRole="button" accessibilityLabel={`${days[day]} ${12+day}`} onPress={()=>onChoose(day)} style={StyleSheet.absoluteFill} />
  </View>;
}

/** Seven independent mood bodies share one bounded, frosted receiving bowl. */
export function MoodPetalBowl({autoPlay=false,previewTime}:Props){
  const art=useMoodResources(),ready=art!==null,initialReduced=useReducedMotion(),safe=useSafeAreaInsets();
  const [reduced,setReduced]=useState(initialReduced),[active,setActive]=useState(true),[size,setSize]=useState({width:0,height:0}),[flags,setFlags]=useState(0),[timer]=useState(()=>new MoodPetalBowlSaveTimer());
  const s=size.width/393,height=s>0?Math.max(852,size.height/s):852,bottom=s>0?Math.max(0,safe.bottom/s-34):0,held=(flags&1)!==0,hasEvent=(flags&2)!==0;
  const m=useSharedValue<Motion>({now:0,epoch:0,visible:true,generation:0,records:[...initialRecords],chosenMood:0,chosenDay:3,event:null,session:null,blocked:0,tokens:{bowl:null,label:null},quietSave:null});
  const pose=useDerivedValue(()=>drive(m.get(),ready&&autoPlay,reduced,ready?previewTime:undefined));
  const clearQuiet=useCallback(()=>timer.clear(),[timer]);
  const finishQuiet=useCallback((stamp:number)=>{'worklet';const old=m.get();if(old.quietSave?.stamp===stamp)m.set({...old,quietSave:null});},[m]);
  const scheduleQuiet=useCallback((stamp:number)=>timer.schedule(stamp,()=>scheduleOnUI(finishQuiet,stamp)),[finishQuiet,timer]);
  const reset=useCallback((visible:boolean)=>{'worklet';const old=m.get(),now=Date.now()/1000;m.set({...old,now,epoch:now,visible,generation:old.generation+1,event:null,session:null,blocked:0,tokens:{bowl:null,label:null},quietSave:null});},[m]);
  useEffect(()=>{let mounted=true;AccessibilityInfo.isReduceMotionEnabled().then(value=>{if(mounted)setReduced(value);});const sub=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>{mounted=false;sub.remove();};},[]);
  useEffect(()=>{const sub=AppState.addEventListener('change',state=>{const visible=state==='active';setActive(visible);clearQuiet();scheduleOnUI(reset,visible);});return()=>sub.remove();},[clearQuiet,reset]);
  useEffect(()=>{clearQuiet();scheduleOnUI(reset,AppState.currentState==='active');},[clearQuiet,ready,reduced,reset]);
  useEffect(()=>()=>{clearQuiet();scheduleOnUI(reset,false);},[clearQuiet,reset]);
  const restart=useCallback(()=>{'worklet';const old=m.get();if(!old.session&&!old.event){const now=Date.now()/1000;m.set({...old,now,epoch:now});}},[m]);
  useEffect(()=>{scheduleOnUI(restart);},[autoPlay,restart]);
  const ticker=useFrameCallback(()=>{const old=m.get(),now=Date.now()/1000;if(!old.session&&old.event&&now-old.event.stamp>=2.70)m.set({...old,now,event:null,chosenMood:0,chosenDay:3,epoch:old.event.stamp+2.70});else m.set({...old,now});},false);
  useEffect(()=>{ticker.setActive(active&&ready&&s>0&&!reduced&&previewTime===undefined&&!held&&(autoPlay||hasEvent));return()=>ticker.setActive(false);},[active,autoPlay,hasEvent,held,previewTime,ready,reduced,s,ticker]);
  useAnimatedReaction(()=>{const state=m.get();return(state.session?1:0)|(state.event?2:0);},(next,before)=>{if(next!==before)scheduleOnRN(setFlags,next);});
  const picture=useDerivedValue(()=>draw(pose.get(),art,height,bottom));
  const capsule=useAnimatedStyle(()=>({transform:[{translateX:(pose.get().capsuleX-22)*s}]}));
  const bowlProps=useAnimatedProps<ViewProps>(()=>{const scene=pose.get();return {accessibilityLabel:`${capitalized(titles[dominantMood(scene)])} mood, selected ${days[scene.selectedDay]} ${12+scene.selectedDay}`};});
  const choose=useCallback((mood:0|1|2|3,day:number,direction:number,velocity=0,captured?:Scene)=>{
    'worklet';const old=m.get();if(!old.visible||!ready||previewTime!==undefined||old.session)return;const now=Date.now()/1000,current=captured??pose.get();m.set({...old,now,chosenMood:mood,chosenDay:day,quietSave:null,event:reduced?null:{stamp:now,kind:'transition',plan:transition(current,mood,day,old.records,direction,velocity)}});scheduleOnRN(clearQuiet);scheduleOnRN(impact);
  },[clearQuiet,m,pose,previewTime,ready,reduced]);
  const chooseMood=useCallback((step:number)=>{'worklet';const scene=pose.get();choose(stepped(dominantMood(scene),step),scene.selectedDay,step);},[choose,pose]);
  const chooseDay=useCallback((day:number)=>{'worklet';const old=m.get(),scene=pose.get();choose(old.records[day]??0,day,day<scene.selectedDay?-1:1);},[choose,m,pose]);
  const start=useCallback((owner:Owner,point:Point)=>{
    'worklet';const old=m.get();if(!old.visible||!ready||reduced||previewTime!==undefined||(old.blocked&ownerBit(owner))!==0)return;
    if(old.session){if(old.session.owner!==owner)m.set({...old,blocked:old.blocked|ownerBit(owner),tokens:{...old.tokens,[owner]:null}});return;}
    const now=Date.now()/1000,capture=pose.get(),generation=old.generation+1;
    m.set({...old,now,generation,event:null,quietSave:null,tokens:{...old.tokens,[owner]:generation},session:{owner,generation,base:capture,current:capture,anchor:point,lastPoint:point,lastDate:now,velocity:{x:0,y:0},translation:{x:0,y:0},haptic:false}});scheduleOnRN(clearQuiet);
  },[clearQuiet,m,pose,previewTime,ready,reduced]);
  const update=useCallback((owner:Owner,point:Point)=>{
    'worklet';const old=m.get(),current=old.session;if(!current||current.owner!==owner||old.tokens[owner]!==current.generation)return;const now=Date.now()/1000,next=moving(current,point,now);if(next===current)return;let haptic=next.haptic;
    if(owner==='bowl'&&!haptic&&displacement(next)>=3){haptic=true;scheduleOnRN(impact);}
    m.set({...old,now,session:{...next,haptic}});
  },[m]);
  const end=useCallback((owner:Owner,point:Point|null,cancelled:boolean)=>{
    'worklet';let old=m.get();const bit=ownerBit(owner);if((old.blocked&bit)!==0){m.set({...old,blocked:old.blocked&~bit,tokens:{...old.tokens,[owner]:null}});return;}const current=old.session;if(!current||current.owner!==owner||old.tokens[owner]!==current.generation)return;
    const now=Date.now()/1000,session=point&&!cancelled?moving(current,point,now):current;old={...old,now,session:null,tokens:{...old.tokens,[owner]:null}};
    if(owner==='bowl'&&!session.haptic&&displacement(session)>=3)scheduleOnRN(impact);
    if(owner==='label'&&!cancelled&&Math.abs(session.translation.x)>=48){const step=session.translation.x<0?1:-1,mood=stepped(dominantMood(session.base),step),velocity=session.velocity.x*(1-smooth(Math.max(0,now-session.lastDate)/.12));m.set({...old,chosenMood:mood,chosenDay:session.base.selectedDay,quietSave:null,event:{stamp:now,kind:'transition',plan:transition(session.current,mood,session.base.selectedDay,old.records,step,velocity)}});scheduleOnRN(impact);}
    else if(owner==='bowl')m.set({...old,event:{stamp:now,kind:'release',from:session.current,base:session.base,cancelled}});
    else m.set({...old,event:{stamp:now,kind:'hold',scene:session.current}});
  },[m]);
  const bowlPan=Gesture.Pan().minDistance(0).activateAfterLongPress(1).onBegin(e=>{if(s>0)start('bowl',{x:e.x/s+35,y:e.y/s+137});}).onUpdate(e=>{if(s>0)update('bowl',{x:e.x/s+35,y:e.y/s+137});}).onEnd(e=>{if(s>0)end('bowl',{x:e.x/s+35,y:e.y/s+137},false);}).onFinalize((_e,success)=>{if(!success)end('bowl',null,true);});
  const labelPan=Gesture.Pan().minDistance(0).activateAfterLongPress(1).onBegin(e=>{if(s>0)start('label',{x:e.x/s+60,y:e.y/s+451});}).onUpdate(e=>{if(s>0)update('label',{x:e.x/s+60,y:e.y/s+451});}).onEnd(e=>{if(s>0)end('label',{x:e.x/s+60,y:e.y/s+451},false);}).onFinalize((_e,success)=>{if(!success)end('label',null,true);});
  bowlPan.simultaneousWithExternalGesture(labelPan);labelPan.simultaneousWithExternalGesture(bowlPan);
  const save=useCallback(()=>{
    'worklet';const old=m.get();if(!old.visible||!ready||previewTime!==undefined||old.session)return;const now=Date.now()/1000,current=pose.get(),day=current.selectedDay,mood=dominantMood(current),records=[...old.records];records[day]=mood;const scene={...current,dots:current.dots.map((d,i)=>i===day?rgba(hexes[mood]):d),captionAlpha:1,captionDay:day};m.set({...old,now,records,chosenMood:mood,chosenDay:day,quietSave:reduced?{stamp:now,day}:null,event:reduced?null:{stamp:now,kind:'hold',scene}});if(reduced)scheduleOnRN(scheduleQuiet,now);scheduleOnRN(impact);
  },[m,pose,previewTime,ready,reduced,scheduleQuiet]);
  const onLayout=useCallback((e:LayoutChangeEvent)=>setSize(e.nativeEvent.layout),[]);
  const box=(x:number,y:number,width:number,h:number)=>({position:'absolute' as const,left:x*s,top:y*s,width:width*s,height:h*s});
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="dark" />
    {s>0&&<>
      <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}><Group transform={[{scale:s}]}><Picture picture={picture} /></Group></Canvas>
      <Text allowFontScaling={false} style={[styles.intro,box(84,170.5,225,22),{fontSize:16*s,lineHeight:22*s}]}>Today I feel</Text>
      <GestureDetector gesture={bowlPan}><Animated.View animatedProps={bowlProps} accessible accessibilityRole="adjustable" accessibilityLabel="Calm mood, selected Thursday 15" accessibilityHint="Swipe up or down to change mood. Drag to stir the bowl." accessibilityActions={[{name:'increment'},{name:'decrement'}]} onAccessibilityAction={e=>{if(e.nativeEvent.actionName==='increment'||e.nativeEvent.actionName==='decrement')scheduleOnUI(chooseMood,e.nativeEvent.actionName==='increment'?1:-1);}} style={box(35,137,323,293)} /></GestureDetector>
      {[0,1,2,3].map(mood=><MoodLabel key={mood} mood={mood} pose={pose} s={s} />)}
      <GestureDetector gesture={labelPan}><View collapsable={false} accessible={false} style={box(60,451,273,55)} /></GestureDetector>
      <Pressable accessibilityRole="button" accessibilityLabel="Previous mood" onPress={()=>scheduleOnUI(chooseMood,-1)} style={[box(21,457.5,44,44),styles.center]}><View style={{width:0,height:0,borderTopWidth:5*s,borderBottomWidth:5*s,borderRightWidth:6.5*s,borderTopColor:'transparent',borderBottomColor:'transparent',borderRightColor:'#191A17'}} /></Pressable>
      <Pressable accessibilityRole="button" accessibilityLabel="Next mood" onPress={()=>scheduleOnUI(chooseMood,1)} style={[box(328,457.5,44,44),styles.center]}><View style={{width:0,height:0,borderTopWidth:5*s,borderBottomWidth:5*s,borderLeftWidth:6.5*s,borderTopColor:'transparent',borderBottomColor:'transparent',borderLeftColor:'#191A17'}} /></Pressable>
      <SavedCaption pose={pose} s={s} />
      <Animated.View accessible={false} pointerEvents="none" style={[box(0,559,44,88),{backgroundColor:'#EDE8DE',borderRadius:22*s},capsule]} />
      {[0,1,2,3,4,5,6].map(day=><CalendarDay key={day} day={day} pose={pose} m={m} s={s} onChoose={d=>scheduleOnUI(chooseDay,d)} />)}
      <Pressable accessibilityRole="button" accessibilityLabel="Save mood" onPress={()=>scheduleOnUI(save)} style={box(60,763-bottom,44,44)} />
    </>}
  </View>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#F4F1EA',overflow:'hidden'},center:{alignItems:'center',justifyContent:'center'},intro:{color:'#191A17',textAlign:'center',textAlignVertical:'center',includeFontPadding:false},label:{position:'absolute',color:'#191A17',fontWeight:'400',textAlign:'center',textAlignVertical:'center',includeFontPadding:false},caption:{position:'absolute',padding:0,margin:0,color:'#827F77',textAlign:'center',textAlignVertical:'center',includeFontPadding:false},day:{position:'absolute',width:'100%',color:'#827F77',textAlign:'center',textAlignVertical:'center',includeFontPadding:false},date:{position:'absolute',width:'100%',color:'#191A17',fontWeight:'600',textAlign:'center',textAlignVertical:'center',includeFontPadding:false}});
