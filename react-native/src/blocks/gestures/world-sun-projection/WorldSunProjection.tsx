import { Canvas,Group,matchFont,Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback,useEffect,useMemo,useState } from 'react';
import { AccessibilityInfo,AppState,Pressable,StyleSheet,Text,View,type LayoutChangeEvent,type ViewProps } from 'react-native';
import { Gesture,GestureDetector } from 'react-native-gesture-handler';
import Animated,{ useAnimatedProps,useAnimatedReaction,useDerivedValue,useFrameCallback,useReducedMotion,useSharedValue } from 'react-native-reanimated';
import Svg,{ Circle,Path } from 'react-native-svg';
import { scheduleOnRN,scheduleOnUI } from 'react-native-worklets';
import { draw } from './art';
import { action,begin,complete,end,initial,move,moving,render,reset,type Event } from './driver';
import { clamp,degrees,reducedPose,smooth,time,type Point } from './motion';
import { useWorldSunResources } from './resources';
export { LOOP } from './motion';
type Props={autoPlay?:boolean;previewTime?:number};
type Action='time'|'grid'|'reset'|'freeze'|'information';
function impact(){Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(()=>{});}
class WorldSunProjectionTimers{
 pending:ReturnType<typeof setTimeout>[]=[];
 clear(){for(const p of this.pending)clearTimeout(p);this.pending=[];}
 schedule(e:Event,reduced:boolean,finish:(id:number,phase:1|2|3)=>void){this.clear();const phases:readonly(readonly[number,1|2|3])[]=reduced?[[.12,1]]:[[e.target ? .35 : .6,1],[2,2],[2.6,3]];for(const[age,phase]of phases)this.pending.push(setTimeout(()=>finish(e.id,phase),Math.max(0,(e.stamp+age-Date.now()/1000)*1000)));}
}
function Icon({kind,size=14,tint='#FAF9F3',filled=false}:{kind:'sun'|'lock'|'view'|'cube'|'reset'|'more';size?:number;tint?:string;filled?:boolean}){
 const d={lock:'M7 10V7A5 5 0 0 1 17 7V10M5 10H19V22H5Z',view:'M8 3H3V8M16 3H21V8M3 16V21H8M16 21H21V16',cube:'M12 2L22 7V17L12 22L2 17V7ZM2 7L12 12L22 7M12 12V22M7 4.5L17 9.5V19.5',reset:'M3 3L10 10M3 9V3H9M21 21L14 14M15 21H21V15',more:'M4 12H4.1M12 12H12.1M20 12H20.1',sun:'M12 1V4M12 20V23M1 12H4M20 12H23M4.2 4.2L6.3 6.3M17.7 17.7L19.8 19.8M4.2 19.8L6.3 17.7M17.7 6.3L19.8 4.2'}[kind];
 return<Svg accessible={false} width={size} height={size} viewBox="0 0 24 24"><Path d={d} stroke={tint} fill="none" strokeWidth={kind==='more'?3:1.5} strokeLinecap="round" strokeLinejoin="round"/>{kind==='sun'&&<Circle cx={12} cy={12} r={5} stroke={tint} strokeWidth={1.5} fill={filled?tint:'none'}/>}</Svg>;
}
export function WorldSunProjection({autoPlay=false,previewTime}:Props){
 const art=useWorldSunResources(),ready=art!==null,initialReduced=useReducedMotion();
 const[reduced,setReduced]=useState(initialReduced),[active,setActive]=useState(AppState.currentState==='active'),[running,setRunning]=useState(false),[size,setSize]=useState({width:0,height:0}),[timers]=useState(()=>new WorldSunProjectionTimers());
 const s=Math.min(size.width/393,size.height/852),ox=(size.width-393*s)/2,oy=(size.height-852*s)/2,m=useSharedValue(initial());
 const pose=useDerivedValue(()=>render(m.get(),ready&&autoPlay,reduced,ready?previewTime:undefined));
 const fonts=useMemo(()=>({small:matchFont({fontFamily:'Helvetica Neue',fontSize:9.5}),bubble:matchFont({fontFamily:'Helvetica Neue',fontSize:10.5,fontWeight:'600'})}),[]);
 const clear=useCallback(()=>timers.clear(),[timers]);
 const finish=useCallback((id:number,phase:1|2|3)=>{'worklet';const old=m.get(),next=complete(old,id,phase,Date.now()/1000,reduced);if(old!==next)m.set(next);},[m,reduced]);
 const schedule=useCallback((e:Event)=>timers.schedule(e,reduced,(id,phase)=>scheduleOnUI(finish,id,phase)),[finish,reduced,timers]);
 const resetClock=useCallback((visible:boolean)=>{'worklet';m.set({...reset(m.get(),Date.now()/1000,visible),reduced:reducedPose()});},[m]);
 useEffect(()=>{let mounted=true;AccessibilityInfo.isReduceMotionEnabled().then(value=>{if(mounted)setReduced(value);});const sub=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>{mounted=false;sub.remove();};},[]);
 useEffect(()=>{const sub=AppState.addEventListener('change',state=>{const visible=state==='active';setActive(visible);clear();scheduleOnUI(resetClock,visible);});return()=>sub.remove();},[clear,resetClock]);
 useEffect(()=>{clear();scheduleOnUI(resetClock,AppState.currentState==='active');},[autoPlay,clear,previewTime,ready,reduced,resetClock]);
 useEffect(()=>()=>{clear();scheduleOnUI(resetClock,false);},[clear,resetClock]);
 const ticker=useFrameCallback(()=>{const old=m.get();m.set({...old,now:Date.now()/1000});},false);
 useAnimatedReaction(()=>moving(m.get(),autoPlay,reduced,previewTime),(value,previous)=>{if(value!==previous)scheduleOnRN(setRunning,value);});
 useEffect(()=>{ticker.setActive(active&&ready&&s>0&&running);return()=>ticker.setActive(false);},[active,ready,running,s,ticker]);
 const picture=useDerivedValue(()=>draw(pose.get(),art,m.get().grid,fonts,reduced&&m.get().event?.target?smooth((m.get().now-m.get().event!.stamp)/.12):1));
 const heroProps=useAnimatedProps<ViewProps>(()=>({accessibilityValue:{text:`${time(pose.get().q)}, heading ${degrees(pose.get().yaw)} degrees, pitch ${degrees(pose.get().pitch)} degrees`}}));
 const railProps=useAnimatedProps<ViewProps>(()=>({accessibilityValue:{text:time(pose.get().q)}}));
 const[grid,setGrid]=useState(false),[frozen,setFrozen]=useState(false),[information,setInformation]=useState(false);
 useAnimatedReaction(()=>[m.get().grid,m.get().frozen,m.get().information],(v,p)=>{if(!p||v.some((x,i)=>x!==p[i])){scheduleOnRN(setGrid,v[0]);scheduleOnRN(setFrozen,v[1]);scheduleOnRN(setInformation,v[2]);}});
 const down=useCallback((id:number,p:Point)=>{'worklet';const old=m.get();if(!old.visible||!ready||previewTime!==undefined||old.ignored.includes(id))return false;const next=begin(old,pose.get(),id,p,Date.now()/1000);m.set(next);if(next.session?.id!==id)return false;scheduleOnRN(clear);return true;},[clear,m,pose,previewTime,ready]);
 const moved=useCallback((id:number,p:Point)=>{'worklet';const old=m.get(),now=Date.now()/1000,next=move(old,id,p,now);if(next!==old){if(next.session&&old.session&&next.session.hour!==old.session.hour&&now-old.session.haptic>=1/6){next.session.haptic=now;scheduleOnRN(impact);}m.set(next);}},[m]);
 const up=useCallback((id:number,p:Point|null)=>{'worklet';const old=m.get(),next=end(old,id,p,Date.now()/1000,reduced);m.set(next);if(old.session?.id===id&&next.event)scheduleOnRN(schedule,next.event);},[m,reduced,schedule]);
 const touch=useMemo(()=>Gesture.Manual().enabled(active&&ready&&previewTime===undefined).shouldCancelWhenOutside(false)
 .onTouchesDown((e,manager)=>{for(const p of e.changedTouches)if(s>0&&down(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s}))manager.activate();})
 .onTouchesMove(e=>{for(const p of e.changedTouches)if(s>0)moved(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s});})
 .onTouchesUp((e,manager)=>{for(const p of e.changedTouches)if(s>0)up(p.id,{x:(p.absoluteX-ox)/s,y:(p.absoluteY-oy)/s});if(e.numberOfTouches===0)manager.end();})
 .onTouchesCancelled((e,manager)=>{for(const p of e.changedTouches)up(p.id,null);manager.end();})
 .onFinalize(()=>{const old=m.get();if(old.session)up(old.session.id,null);}),[active,down,m,moved,ox,oy,previewTime,ready,s,up]);
 const act=useCallback((kind:Action,value=0)=>{'worklet';const old=m.get();if(!old.visible||!ready||previewTime!==undefined||old.session)return;const next=action(old,pose.get(),Date.now()/1000,kind,value,reduced);m.set(next);if(next.event)scheduleOnRN(schedule,next.event);scheduleOnRN(impact);},[m,pose,previewTime,ready,reduced,schedule]);
 const box=(x:number,y:number,w:number,h:number)=>({position:'absolute' as const,left:x*s,top:y*s,width:w*s,height:h*s});
 const label=(font:number)=>({fontSize:font*s,color:'#FAF9F3',includeFontPadding:false});
 const preset=(q:number,title:string,y:number)=><Pressable onPress={()=>scheduleOnUI(act,'time',q)} accessibilityRole="button" accessibilityLabel={`Select ${time(q)}`} style={[box(340.5,y-22,44,44),styles.center]}><View style={[styles.chip,styles.center,{width:37*s,height:24*s,borderRadius:12*s}]}><Text allowFontScaling={false} style={label(10)}>{title}</Text></View></Pressable>;
 const onLayout=useCallback((e:LayoutChangeEvent)=>setSize(e.nativeEvent.layout),[]);
 return<View onLayout={onLayout} style={styles.screen}><StatusBar style="light"/>{s>0&&<View style={{position:'absolute',left:ox,top:oy,width:393*s,height:852*s}}>
 <Canvas pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}><Group transform={[{scale:s}]}><Picture picture={picture}/></Group></Canvas>
 <GestureDetector gesture={touch}><View collapsable={false} style={box(0,114,393,621)}><Animated.View pointerEvents="none" animatedProps={heroProps} accessible accessibilityRole="adjustable" accessibilityLabel="Alder Reach projected view" accessibilityHint="Drag to look around. Use the right rail to change the planning time." accessibilityActions={[{name:'increment'},{name:'decrement'}]} onAccessibilityAction={e=>scheduleOnUI(act,'time',clamp(pose.get().q+(e.nativeEvent.actionName==='increment'?15:-15)/350))} style={box(8,0,315,596)}/><Animated.View pointerEvents="none" animatedProps={railProps} accessible accessibilityRole="adjustable" accessibilityLabel="Planning time rail" accessibilityHint="Drag vertically to move the sun along its projected path." accessibilityActions={[{name:'increment'},{name:'decrement'}]} onAccessibilityAction={e=>scheduleOnUI(act,'time',clamp(pose.get().q+(e.nativeEvent.actionName==='increment'?15:-15)/350))} style={box(331,209,62,174)}/></View></GestureDetector>
 <View pointerEvents="none" style={[box(12,58,185,28),styles.chip,styles.center,{borderRadius:14*s,flexDirection:'row',gap:6*s}]}><Icon kind="sun" size={9*s} tint="#D5CC47" filled/><Text allowFontScaling={false} style={label(9)}>Golden hour</Text><Text allowFontScaling={false} style={{...label(9),fontVariant:['tabular-nums']}}>04:52 → 05:33</Text></View>
 {[{x:203,w:85,t:'Bearing'},{x:294,w:87,t:'Altitude'}].map(v=><View pointerEvents="none" key={v.t} style={[box(v.x,58,v.w,28),styles.chip,styles.center,{borderRadius:14*s,flexDirection:'row',gap:4*s}]}><Text allowFontScaling={false} style={label(10)}>{v.t}</Text><Icon kind="lock" size={8*s}/></View>)}
 <Pressable onPress={()=>scheduleOnUI(act,'time',0)} accessibilityRole="button" accessibilityLabel="Reset planning time" style={[box(12,508,63,44),styles.center]}><View style={[styles.chip,styles.center,{width:63*s,height:26*s,borderRadius:13*s}]}><Text allowFontScaling={false} style={{...label(11),fontWeight:'600'}}>Today</Text></View></Pressable>
 {preset(0,'Rise',238)}{preset(.34,'Now',270)}{preset(1,'Set',524)}
 <View pointerEvents="none" style={[box(12,735,369,83),{borderRadius:21*s,backgroundColor:'rgba(48,53,49,.82)',borderWidth:.7*s,borderColor:'rgba(255,255,255,.1)'}]}/>
 <Text allowFontScaling={false} style={[box(70,747,253,16),label(12),{textAlign:'center',fontWeight:'600'}]}>Alder Reach</Text><Text allowFontScaling={false} style={[box(70,765,253,12),label(8.5),{textAlign:'center',letterSpacing:.6*s,color:'#C7CDC7'}]}>{information?'OFFLINE SOLAR PLAN':'EAST COAST · UTC+0'}</Text>
 <Pressable onPress={()=>scheduleOnUI(act,'information')} accessibilityRole="button" accessibilityLabel="Show planning details" style={[box(12,736,44,44),styles.center]}><Icon kind="more" size={12*s}/></Pressable><Pressable onPress={()=>scheduleOnUI(act,'reset')} accessibilityRole="button" accessibilityLabel="Reset view direction" style={[box(337,736,44,44),styles.center]}><Icon kind="reset" size={12*s}/></Pressable>
 {[0,1,2].map(i=><Pressable key={i} accessibilityRole="button" accessibilityLabel={i===0?(grid?'Hide horizon grid':'Show horizon grid'):i===1?'Reset view direction':frozen?'Unfreeze planning time':'Freeze planning time'} accessibilityState={{selected:i===0?grid:i===1||frozen}} onPress={()=>scheduleOnUI(act,i===0?'grid':i===1?'reset':'freeze')} style={[box(24+i*120,776,105,44),styles.center]}><View style={[styles.center,{width:105*s,height:24*s,borderRadius:12*s,backgroundColor:`rgba(92,99,92,${(i===0?grid:i===1||frozen) ? .75 : .40})`}]}><Icon kind={i===0?'view':i===1?'cube':'sun'} size={14*s}/></View></Pressable>)}
 </View>}</View>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#293432',overflow:'hidden'},center:{justifyContent:'center',alignItems:'center'},chip:{backgroundColor:'rgba(41,52,50,.75)',borderColor:'rgba(255,255,255,.10)',borderWidth:.6}});
