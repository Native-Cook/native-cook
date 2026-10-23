import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, Pressable, StyleSheet, Text, TextInput, View, type LayoutChangeEvent, type TextInputProps, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { draw } from './art';
import { actionEvent, beginSession, drive, finishEvent, heldScene, initialMotion, movedSession, moving, releasedPresentation, resetMotion, tick, weights, withoutID, type Event, type Motion } from './driver';
import { accepts, clone, rows, type Point, type Scene } from './motion';
import { useLacedResources } from './resources';

export { LOOP } from './motion';
type Props={autoPlay?:boolean;previewTime?:number;onBack?:()=>void};
const AnimatedPressable=Animated.createAnimatedComponent(Pressable),AnimatedInput=Animated.createAnimatedComponent(TextInput);
const ink='#302C27',secondary='#79766F';
function impact(){Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(()=>{});}
function announce(){AccessibilityInfo.announceForAccessibility('Pair saved');}
class LacedBootStudioTimers{
 pending:ReturnType<typeof setTimeout>[]=[];
 clear(){for(const t of this.pending)clearTimeout(t);this.pending=[];}
 schedule(e:Event,finish:(id:number,phase:1|2|3)=>void){this.clear();for(const[age,phase]of [[.43,1],[2,2],[2.6,3]] as const)this.pending.push(setTimeout(()=>finish(e.id,phase),Math.max(0,(e.stamp+age-Date.now()/1000)*1000)));}
}
function Choice({index,sole,m,s,onChoose}:{index:number;sole:boolean;m:SharedValue<Motion>;s:number;onChoose:()=>void}){
 const border=useAnimatedStyle(()=>({borderColor:(sole?m.get().selectedSole:m.get().selectedLeather)===index?ink:'transparent'}));
 const props=useAnimatedProps<ViewProps>(()=>({accessibilityState:{selected:(sole?m.get().selectedSole:m.get().selectedLeather)===index}}));
 return <AnimatedPressable animatedProps={props} accessibilityRole="button" accessibilityLabel={sole?(index===0?'Dark sole':'Natural sole'):['Tan leather','Charcoal leather','Chestnut leather'][index]} onPress={onChoose} style={{position:'absolute',left:((sole?242+58*index:38+40*index)-(sole?26:22))*s,top:618*s,width:(sole?52:44)*s,height:44*s,alignItems:'center',justifyContent:'center'}}>
 <View accessible={false} pointerEvents="none" style={{width:(sole?48:24)*s,height:(sole?20:24)*s,borderRadius:(sole?10:12)*s,backgroundColor:sole?['#45352D','#98734F'][index]:['#A56D48','#493F36','#6F4F3A'][index]}}/>
 <Animated.View pointerEvents="none" style={[{position:'absolute',width:(sole?52:32)*s,height:(sole?24:32)*s,borderRadius:(sole?12:16)*s,borderWidth:(sole?.8:1)*s},border]}/></AnimatedPressable>;
}
function RowReadout({pose,s}:{pose:SharedValue<Scene>;s:number}){
 const props=useAnimatedProps<TextInputProps>(()=>{const text=`${rows(pose.get())} / 6 ROWS`;return{text,defaultValue:text} as TextInputProps;});
 return <AnimatedInput animatedProps={props} defaultValue="0 / 6 ROWS" editable={false} caretHidden accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none" underlineColorAndroid="transparent" style={{position:'absolute',left:24*s,top:706*s,width:345*s,height:16*s,padding:0,margin:0,color:ink,fontSize:11*s,fontFamily:Platform.OS==='ios'?'Menlo':'monospace',includeFontPadding:false,lineHeight:16*s}}/>;
}
/** Eleven continuous leather-thread spans answer the finger, then tighten row by row. */
export function LacedBootStudio({autoPlay=false,previewTime,onBack}:Props){
 const art=useLacedResources(),ready=art!==null,initialReduced=useReducedMotion();
 const[reduced,setReduced]=useState(initialReduced),[active,setActive]=useState(AppState.currentState==='active'),[size,setSize]=useState({width:0,height:0}),[running,setRunning]=useState(false),[favorite,setFavorite]=useState(false),[timers]=useState(()=>new LacedBootStudioTimers());
 const s=Math.min(size.width/393,size.height/852),ox=(size.width-393*s)/2,oy=(size.height-852*s)/2;
 const m=useSharedValue<Motion>(initialMotion()),pose=useDerivedValue(()=>drive(m.get(),ready&&autoPlay,reduced,ready?previewTime:undefined));
 const clear=useCallback(()=>timers.clear(),[timers]);
 const complete=useCallback((id:number,phase:1|2|3)=>{'worklet';const old=m.get(),next=finishEvent(old,id,phase,Date.now()/1000,reduced);if(next!==old)m.set(next);},[m,reduced]);
 const schedule=useCallback((e:Event)=>timers.schedule(e,(id,phase)=>scheduleOnUI(complete,id,phase)),[complete,timers]);
 const reset=useCallback((visible:boolean)=>{'worklet';m.set(resetMotion(m.get(),Date.now()/1000,visible));},[m]);
 useEffect(()=>{let mounted=true;AccessibilityInfo.isReduceMotionEnabled().then(value=>{if(mounted)setReduced(value);});const sub=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>{mounted=false;sub.remove();};},[]);
 useEffect(()=>{const sub=AppState.addEventListener('change',state=>{const visible=state==='active';setActive(visible);clear();scheduleOnUI(reset,visible);});return()=>sub.remove();},[clear,reset]);
 useEffect(()=>{clear();scheduleOnUI(reset,AppState.currentState==='active');},[autoPlay,clear,previewTime,ready,reduced,reset]);
 useEffect(()=>()=>{clear();scheduleOnUI(reset,false);},[clear,reset]);
 const ticker=useFrameCallback(()=>{const old=m.get();m.set(tick(old,Date.now()/1000));},false);
 useAnimatedReaction(()=>moving(m.get(),autoPlay,reduced,previewTime),(next,previous)=>{if(next!==previous)scheduleOnRN(setRunning,next);});
 useEffect(()=>{ticker.setActive(active&&ready&&s>0&&running);return()=>ticker.setActive(false);},[active,ready,running,s,ticker]);
 const picture=useDerivedValue(()=>draw(pose.get(),art));
 const heroProps=useAnimatedProps<ViewProps>(()=>({accessibilityValue:{text:`${rows(pose.get())} of 6 rows, ${Math.round(pose.get().tensions[5]*100)} percent tension`}}));
 const ctaProps=useAnimatedProps<ViewProps>(()=>({accessibilityLabel:pose.get().savedMix>.5?'Pair saved, 245 dollars':'Save this pair, 245 dollars'}));
 const saveOpacity=useAnimatedStyle(()=>({opacity:1-pose.get().savedMix})),savedOpacity=useAnimatedStyle(()=>({opacity:pose.get().savedMix}));
 const begin=useCallback((id:number,world:Point)=>{
  'worklet';const old=m.get();if(!old.visible||!ready||reduced||previewTime!==undefined||old.ignored.includes(id))return false;
  if(old.session||!accepts(world)){m.set({...old,ignored:[...old.ignored,id]});return false;}
  m.set(beginSession(old,pose.get(),id,world,Date.now()/1000));scheduleOnRN(clear);return true;
 },[clear,m,pose,previewTime,ready,reduced]);
 const move=useCallback((id:number,world:Point)=>{
  'worklet';const old=m.get(),session=old.session;if(!session||session.id!==id)return;const now=Date.now()/1000,next=movedSession(session,world,now);
  if(next!==session){m.set({...old,now,session:next});if(Math.floor(next.progress+1e-8)>Math.floor(session.progress+1e-8))scheduleOnRN(impact);}
 },[m]);
 const end=useCallback((id:number,world:Point|null,cancelled:boolean)=>{
  'worklet';const old=m.get(),session=old.session;if(!session||session.id!==id){if(old.ignored.includes(id))m.set({...old,ignored:withoutID(old.ignored,id)});return;}
  const now=Date.now()/1000,current=!cancelled&&world?movedSession(session,world,now):session,from=heldScene(current,now),generation=old.generation+1;
  const event:Event={id:generation,stamp:now,from,downAge:Math.max(0,now-current.downDate),rows:null,phase:0};
  m.set({...old,now,generation,session:null,event,presentation:releasedPresentation(old.presentation,old.selectedLeather,old.selectedSole,now),ignored:withoutID(old.ignored,id)});scheduleOnRN(schedule,event);
 },[m,schedule]);
 const touch=useMemo(()=>Gesture.Manual().enabled(active&&ready&&!reduced&&previewTime===undefined).shouldCancelWhenOutside(false)
  .onTouchesDown((event,manager)=>{for(const p of event.changedTouches)if(s>0&&begin(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s}))manager.activate();})
  .onTouchesMove(event=>{for(const p of event.changedTouches)if(s>0)move(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s});})
  .onTouchesUp((event,manager)=>{for(const p of event.changedTouches)if(s>0)end(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s},false);if(event.numberOfTouches===0)manager.end();})
  .onTouchesCancelled((event,manager)=>{for(const p of event.changedTouches)end(p.id,null,true);manager.end();})
  .onFinalize(()=>{const old=m.get();if(old.session)end(old.session.id,null,true);}),[active,begin,end,m,move,ox,oy,previewTime,ready,reduced,s]);
 const act=useCallback((kind:'leather'|'sole'|'save'|'favorite'|'row'|'back',index=0)=>{
  'worklet';const old=m.get();if(!old.visible||!ready||previewTime!==undefined||old.session)return;const now=Date.now()/1000,captured=pose.get();let next=actionEvent(old,captured,now,reduced);
  if(kind==='back'){m.set(resetMotion(old,now,true));scheduleOnRN(clear);if(onBack)scheduleOnRN(onBack);return;}
  if(kind==='leather'||kind==='sole'){
   const leather=kind==='leather'?index:old.selectedLeather,sole=kind==='sole'?index:old.selectedSole;
   next={...next,selectedLeather:leather,selectedSole:sole,presentation:{...old.presentation,materials:[...captured.materials],soles:[...captured.soles],change:{stamp:now,materials:[...captured.materials],soles:[...captured.soles],targetMaterials:weights(3,leather),targetSoles:weights(2,sole)}}};
  }else if(kind==='save'){next={...next,presentation:{...old.presentation,confirmation:{stamp:now,ageAtAnchor:0,startMix:captured.savedMix,anchorMix:captured.savedMix}}};scheduleOnRN(announce);}
  else if(kind==='favorite'){next={...next,favorite:!old.favorite};scheduleOnRN(setFavorite,!old.favorite);}
  else if(kind==='row'){
   const count=Math.max(0,Math.min(6,rows(captured)+index));if(count===rows(captured))return;
   next={...next,generation:old.generation+1,event:{id:old.generation+1,stamp:now,from:clone(captured),downAge:0,rows:count,phase:0}};
  }
  m.set(next);if(next.event)scheduleOnRN(schedule,next.event);scheduleOnRN(impact);
 },[clear,m,onBack,pose,previewTime,ready,reduced,schedule]);
 const onLayout=useCallback((e:LayoutChangeEvent)=>setSize(e.nativeEvent.layout),[]);
 const box=(x:number,y:number,w:number,h:number)=>({position:'absolute' as const,left:x*s,top:y*s,width:w*s,height:h*s});
 const copy=(x:number,y:number,w:number,h:number,font:number)=>[styles.copy,box(x,y,w,h),{fontSize:font*s,lineHeight:h*s}];
 return <View onLayout={onLayout} style={styles.screen}><StatusBar style="dark"/>{s>0&&<View style={{position:'absolute',left:ox,top:oy,width:393*s,height:852*s}}>
  <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}><Group transform={[{scale:s}]}><Picture picture={picture}/></Group></Canvas>
  <Pressable onPress={()=>scheduleOnUI(act,'back')} accessibilityRole="button" accessibilityLabel="Back" style={[box(18,55,44,44),styles.center]}><Svg width={18*s} height={18*s} viewBox="0 0 18 18"><Path d="M11 3L5 9L11 15" stroke={ink} strokeWidth={1.5} fill="none" strokeLinecap="round" strokeLinejoin="round"/></Svg></Pressable>
  <Text allowFontScaling={false} style={[...copy(79,68,235,18,11),{textAlign:'center',fontWeight:'500',letterSpacing:2*s}]}>ATELIER ROWE</Text>
  <Pressable onPress={()=>scheduleOnUI(act,'save')} accessibilityRole="button" accessibilityLabel="Save pair" style={[box(331,55,44,44),styles.center]}><Svg width={22*s} height={22*s} viewBox="0 0 22 22"><Path d="M4 7H18V20H4ZM7 7V5C7 0 15 0 15 5V7" stroke={ink} strokeWidth={1.3} fill="none" strokeLinejoin="round"/></Svg></Pressable>
  <GestureDetector gesture={touch}><View collapsable={false} style={box(23,114,347,383)}>
   <Animated.View animatedProps={heroProps} accessible accessibilityRole="adjustable" accessibilityLabel="Alder Lace Boot lacing" accessibilityHint="Pull the lace through each eyelet, then pull upward to tighten." accessibilityActions={[{name:'increment'},{name:'decrement'},{name:'laceNext',label:'Lace next row'}]} onAccessibilityAction={e=>{const name=e.nativeEvent.actionName;if(name==='increment'||name==='laceNext')scheduleOnUI(act,'row',1);else if(name==='decrement')scheduleOnUI(act,'row',-1);}} style={box(175,22,149,344)}/>
  </View></GestureDetector>
  <Text allowFontScaling={false} style={[...copy(24,523,296,30,25),{fontFamily:Platform.OS==='ios'?'Georgia':'serif',fontWeight:'600'}]}>Alder Lace Boot</Text>
  <Pressable onPress={()=>scheduleOnUI(act,'favorite')} accessibilityRole="button" accessibilityLabel={favorite?'Remove from favorites':'Add to favorites'} style={[box(329,516,44,44),styles.center]}><Svg width={21*s} height={21*s} viewBox="0 0 24 24"><Path d="M12 21C9 18 2 13 2 7.7C2 2 9 1.8 12 6C15 1.8 22 2 22 7.7C22 13 15 18 12 21Z" stroke={ink} strokeWidth={1.4} fill={favorite?ink:'none'} strokeLinejoin="round"/></Svg></Pressable>
  <Text allowFontScaling={false} style={[...copy(24,560,345,18,12),styles.secondary]}>Hand-finished leather</Text>
  <Text allowFontScaling={false} style={[...copy(24,601,150,16,9),{fontWeight:'500',letterSpacing:1.5*s}]}>LEATHER</Text>
  <Text allowFontScaling={false} style={[...copy(218,601,151,16,9),{fontWeight:'500',letterSpacing:1.5*s}]}>SOLE</Text>
  {[0,1,2].map(i=><Choice key={`leather-${i}`} index={i} sole={false} m={m} s={s} onChoose={()=>scheduleOnUI(act,'leather',i)}/>)}
  {[0,1].map(i=><Choice key={`sole-${i}`} index={i} sole m={m} s={s} onChoose={()=>scheduleOnUI(act,'sole',i)}/>)}
  <Text allowFontScaling={false} style={[...copy(24,678,345,16,11),styles.secondary]}>Pull the lace through each eyelet.</Text>
  <RowReadout pose={pose} s={s}/>
  <AnimatedPressable animatedProps={ctaProps} accessibilityRole="button" accessibilityLabel="Save this pair, 245 dollars" onPress={()=>scheduleOnUI(act,'save')} style={[box(24,754,345,54),{borderRadius:6*s,backgroundColor:'#608674',justifyContent:'center'}]}>
   <Animated.Text accessible={false} allowFontScaling={false} style={[styles.cta,{left:20*s,fontSize:15*s},saveOpacity]}>Save this pair</Animated.Text>
   <Animated.View accessible={false} pointerEvents="none" style={[{position:'absolute',left:20*s,flexDirection:'row',alignItems:'center'},savedOpacity]}><Svg width={13*s} height={13*s} viewBox="0 0 16 16"><Path d="M2 8L6 12L14 3" fill="none" stroke="#F2EDE8" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round"/></Svg><Text accessible={false} allowFontScaling={false} style={{color:'#F2EDE8',fontSize:15*s,fontWeight:'600',marginLeft:7*s}}>Pair saved</Text></Animated.View>
   <Text accessible={false} allowFontScaling={false} style={{position:'absolute',right:20*s,color:'#F2EDE8',fontSize:13*s}}>$245</Text>
  </AnimatedPressable>
 </View>}</View>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#F2EDE8',overflow:'hidden'},center:{alignItems:'center',justifyContent:'center'},copy:{color:ink,padding:0,margin:0,includeFontPadding:false,textAlignVertical:'center'},secondary:{color:secondary},cta:{position:'absolute',color:'#F2EDE8',fontWeight:'600',includeFontPadding:false}});
