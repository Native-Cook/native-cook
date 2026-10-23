import { Canvas, Group, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawHero } from './art';
import { clamp, drive, followYaw, rest, zeroYaw, type Action, type Motion } from './motion';
import { useGeodeResources } from './resources';

export { LOOP } from './motion';
type Props = { autoPlay?: boolean; previewTime?: number };
function tactile(impact=false) { if(impact)Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(()=>{});else Haptics.selectionAsync().catch(()=>{}); }
class GeodeFocusRewardIdleTimers {
  pending: ReturnType<typeof setTimeout>[]=[];
  clear(){for(const timer of this.pending)clearTimeout(timer);this.pending=[];}
  schedule(stamp: number,reopen: ()=>void,finish: ()=>void){this.clear();this.pending=[setTimeout(reopen,Math.max(0,(stamp+2-Date.now()/1000)*1000)),setTimeout(finish,Math.max(0,(stamp+2.65-Date.now()/1000)*1000))];}
}

/** The mineral already exists behind twelve solid shell pieces; touch drives their authored fracture. */
export function GeodeFocusReward({autoPlay=false,previewTime}: Props) {
  const art=useGeodeResources(),ready=art!==null,initialReduced=useReducedMotion(),safe=useSafeAreaInsets();
  const [reduced,setReduced]=useState(initialReduced),[size,setSize]=useState({width:0,height:0}),[awake,setAwake]=useState(false),[ui,setUi]=useState(1);
  const [timers]=useState(()=>new GeodeFocusRewardIdleTimers()),s=size.width/393,height=s>0?Math.max(852,size.height/s):852,bottom=s>0?Math.max(0,safe.bottom/s-34):0;
  const m=useSharedValue<Motion>({now:0,epoch:0,press:null,captured:rest(),translation:[0,0],yaw:zeroYaw(),action:null,kept:false,collapsed:false,fractureTicked:false});
  const cardOpacity=useSharedValue(1),heroOpacity=useSharedValue(1);
  const pose=useDerivedValue(()=>drive(m.get(),ready&&autoPlay,reduced,ready?previewTime:undefined));
  const clearIdle=useCallback(()=>timers.clear(),[timers]);
  const idleReopen=useCallback((stamp: number)=>{
    'worklet';const old=m.get();if(old.action?.stamp!==stamp||old.press!==null)return;const now=Date.now()/1000;
    m.set({...old,now,collapsed:false});cardOpacity.set(withTiming(1,{duration:reduced?180:200}));
  },[cardOpacity,m,reduced]);
  const idleFinish=useCallback((stamp: number)=>{
    'worklet';const old=m.get();if(old.action?.stamp!==stamp||old.press!==null)return;const now=Date.now()/1000;
    m.set({...old,now,epoch:stamp+2.65,action:null,kept:false,collapsed:false,fractureTicked:false});cardOpacity.set(1);
  },[cardOpacity,m]);
  const scheduleIdle=useCallback((stamp: number)=>{
    timers.schedule(stamp,()=>scheduleOnUI(idleReopen,stamp),()=>scheduleOnUI(idleFinish,stamp));
  },[idleFinish,idleReopen,timers]);
  useEffect(()=>{const sub=AccessibilityInfo.addEventListener('reduceMotionChanged',setReduced);return()=>sub.remove();},[]);
  useEffect(()=>{
    clearIdle();const now=Date.now()/1000;
    m.set(old=>({...old,now,epoch:now,press:null,translation:[0,0],yaw:zeroYaw(),action:null,kept:false,collapsed:false,fractureTicked:false}));cardOpacity.set(1);heroOpacity.set(1);
  },[cardOpacity,clearIdle,heroOpacity,m,ready,reduced]);
  useEffect(()=>{if(autoPlay){const now=Date.now()/1000;m.set(old=>old.press!==null||old.action?old:{...old,now,epoch:now});}},[autoPlay,m]);
  useEffect(()=>()=>{clearIdle();},[clearIdle]);
  const ticker=useFrameCallback(()=>{const old=m.get();m.set({...old,now:Date.now()/1000});},false);
  const collapsed=(ui&32)!==0,kept=(ui&64)!==0;
  useEffect(()=>{ticker.setActive(ready&&s>0&&!reduced&&!collapsed&&previewTime===undefined&&(autoPlay||awake));return()=>ticker.setActive(false);},[autoPlay,awake,collapsed,previewTime,ready,reduced,s,ticker]);
  useAnimatedReaction(()=>m.get().press!==null||m.get().action!==null,(next,before)=>{if(next!==before)scheduleOnRN(setAwake,next);});
  useAnimatedReaction(()=>pose.get().hero.p>=.62,(crossed,before)=>{
    const old=m.get();if(crossed&&!before&&old.press!==null&&!old.fractureTicked&&!reduced){m.set({...old,fractureTicked:true});scheduleOnRN(tactile,true);}
  });
  useAnimatedReaction(()=>{
    const f=pose.get(),state=m.get();return (f.copy.restAlpha>=.5?1:0)|(f.copy.resultAlpha[0]>=.5?2:0)|(f.copy.resultAlpha[1]>=.5?4:0)|(f.copy.resultAlpha[3]>=.5?8:0)|(f.copy.resultAlpha[3]>=.8?16:0)|(state.collapsed?32:0)|(state.kept?64:0);
  },(next,before)=>{if(next!==before)scheduleOnRN(setUi,next);});
  const picture=useDerivedValue(()=>drawHero(pose.get().hero,art,height));
  const rewardStyle=useAnimatedStyle(()=>({opacity:cardOpacity.get()})),heroStyle=useAnimatedStyle(()=>({opacity:heroOpacity.get()}));
  const restStyle=useAnimatedStyle(()=>({opacity:pose.get().copy.restAlpha}));
  const result0Style=useAnimatedStyle(()=>({opacity:pose.get().copy.resultAlpha[0],transform:[{translateY:pose.get().copy.resultY[0]*s}]}));
  const result1Style=useAnimatedStyle(()=>({opacity:pose.get().copy.resultAlpha[1],transform:[{translateY:pose.get().copy.resultY[1]*s}]}));
  const result2Style=useAnimatedStyle(()=>({opacity:pose.get().copy.resultAlpha[2],transform:[{translateY:pose.get().copy.resultY[2]*s}]}));
  const result3Style=useAnimatedStyle(()=>({opacity:pose.get().copy.resultAlpha[3],transform:[{translateY:pose.get().copy.resultY[3]*s}]}));
  const actionProps=useAnimatedProps<ViewProps>(()=>({pointerEvents:pose.get().copy.resultAlpha[3]>=.8?'auto':'none',accessibilityElementsHidden:pose.get().copy.resultAlpha[3]<.5,importantForAccessibility:pose.get().copy.resultAlpha[3]<.5?'no-hide-descendants':'auto'}));
  const hitProps=useAnimatedProps<ViewProps>(()=>({accessibilityValue:{text:reduced?'Seven sessions completed':`${Math.trunc(pose.get().hero.p*100)} percent revealed`}}));
  const begin=useCallback(()=>{
    'worklet';if(!ready||reduced||previewTime!==undefined||m.get().collapsed)return;const old=m.get(),now=Date.now()/1000,from=pose.get();
    m.set({...old,now,press:now,captured:from,translation:[0,0],yaw:zeroYaw(),action:null,fractureTicked:false});scheduleOnRN(clearIdle);
  },[clearIdle,m,pose,previewTime,ready,reduced]);
  const update=useCallback((dx: number,dy: number,vx: number)=>{
    'worklet';const old=m.get();if(old.press===null||s<=0)return;const now=Date.now()/1000;
    m.set({...old,now,translation:[dx/s,dy/s],yaw:followYaw(old.yaw,clamp(vx/s*.006,-5,5),now)});
  },[m,s]);
  const end=useCallback((vy: number,cancelled: boolean)=>{
    'worklet';const old=m.get();if(old.press===null||s<=0)return;const now=Date.now()/1000,from=pose.get(),pressure=now-old.press>.12?.62:0,velocity=cancelled?0:clamp(pressure-vy/s/210,-1.6,1.6),yawTarget=clamp(old.captured.hero.manualYaw+.09*old.translation[0],-12,12);
    const action: Action={stamp:now,from,kind:'released',opens:!cancelled&&from.hero.p>=.62,velocity,cancelled,yawTarget};
    m.set({...old,now,press:null,translation:[0,0],yaw:zeroYaw(),action});scheduleOnRN(scheduleIdle,now);
  },[m,pose,s,scheduleIdle]);
  const pan=Gesture.Pan().minDistance(0).activateAfterLongPress(1).onBegin(begin).onUpdate(e=>update(e.translationX,e.translationY,e.velocityX)).onEnd(e=>end(e.velocityY,false)).onFinalize((_e,success)=>{if(!success)end(0,true);});
  const act=useCallback((kind: 'keep'|'replay'|'close'|'reopen'|'reveal')=>{
    'worklet';if(!ready)return;if(kind==='reveal'&&(reduced||previewTime!==undefined))return;
    const old=m.get(),now=Date.now()/1000,from=pose.get(),action: Action={stamp:now,from,kind:kind==='replay'&&!reduced?'replay':kind==='reveal'?'released':'hold',opens:kind==='reveal',velocity:0,cancelled:false,yawTarget:from.hero.manualYaw};
    m.set({...old,now,press:null,action,kept:kind==='keep'?true:kind==='replay'?false:old.kept,collapsed:kind==='close'?true:kind==='reopen'?false:old.collapsed,fractureTicked:kind==='reveal'?true:kind==='replay'?false:old.fractureTicked});
    if(kind==='close')cardOpacity.set(withTiming(0,{duration:reduced?180:240}));if(kind==='reopen')cardOpacity.set(withTiming(1,{duration:reduced?180:240}));
    if(kind==='replay'&&reduced){heroOpacity.set(withTiming(.78,{duration:90},finished=>{if(finished)heroOpacity.set(withTiming(1,{duration:90}));}));}
    scheduleOnRN(scheduleIdle,now);scheduleOnRN(tactile,kind==='keep'||kind==='reveal');
  },[cardOpacity,heroOpacity,m,pose,previewTime,ready,reduced,scheduleIdle]);
  const onLayout=useCallback((e: LayoutChangeEvent)=>setSize(e.nativeEvent.layout),[]);
  const box=(x: number,y: number,w: number,h: number)=>({position:'absolute' as const,left:x*s,top:y*s,width:w*s,height:h*s});
  const txt=(fontSize: number,weight: '400'|'500'|'600'='400',tint='#ECEDE5')=>({fontSize:fontSize*s,fontWeight:weight,color:tint,includeFontPadding:false});
  const hidden=(yes: boolean)=>({accessibilityElementsHidden:yes,importantForAccessibility:yes?'no-hide-descendants' as const:'auto' as const});
  return <View onLayout={onLayout} style={styles.screen}>
    <StatusBar style="light" />
    {s>0&&<>
      <View pointerEvents="none" {...hidden(true)} style={StyleSheet.absoluteFill}>
        <View style={[StyleSheet.absoluteFill,{opacity:.10}]}>
          <View style={[box(24,76,114,28),styles.centerY]}><Text allowFontScaling={false} style={txt(23,'500')}>Stillvale</Text></View>
          <View style={[box(24,206,313,28),styles.centerY]}><Text allowFontScaling={false} style={txt(19,'500')}>Today</Text></View>
          <View style={[box(24,287,345,188),{borderRadius:20*s,backgroundColor:'rgba(89,97,93,.35)'}]} />
          <View style={[box(71.5,322.5,250,125),{flexDirection:'row',alignItems:'flex-end',justifyContent:'center',gap:12*s}]}>{[42,66,53,89,73,104,118].map((h,i)=><View key={i} style={{width:18*s,height:h*s,borderRadius:9*s,backgroundColor:'#B6CC55'}} />)}</View>
          {['Today','Stones','You'].map((title,i)=><View key={title} style={[box([74,196.5,319][i]-35,772-bottom,70,44),styles.center]}><Text allowFontScaling={false} style={txt(19)}>{['☀︎','⬡','○'][i]}</Text><Text allowFontScaling={false} style={[txt(10,'500'),{marginTop:5*s}]}>{title}</Text></View>)}
        </View>
      </View>
      <Animated.View pointerEvents={collapsed?'none':'box-none'} {...hidden(collapsed)} style={[StyleSheet.absoluteFill,rewardStyle]}>
        <LinearGradient pointerEvents="none" colors={['#08090A','#232421','#080909']} locations={[0,.5,1]} style={[box(16,136,361,608),{borderRadius:27*s,borderWidth:.7*s,borderColor:'rgba(255,255,255,.07)',overflow:'hidden'}]} />
        <Animated.View pointerEvents="none" {...hidden(true)} style={[StyleSheet.absoluteFill,heroStyle]}><Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><Group transform={[{scale:s}]}><Picture picture={picture} /></Group></Canvas></Animated.View>
        <Animated.View pointerEvents="none" {...hidden((ui&1)===0)} style={[StyleSheet.absoluteFill,restStyle]}>
          <View style={[box(40,202,313,28),styles.center]}><Text allowFontScaling={false} style={txt(20,'600')}>Stone earned</Text></View>
          <View style={[box(40,654,313,18),styles.center]}><Text allowFontScaling={false} style={[txt(10,'600','#999D96'),{letterSpacing:.4*s}]}>Hold to reveal your stone</Text></View>
        </Animated.View>
        <Animated.View pointerEvents="none" {...hidden((ui&2)===0)} style={[StyleSheet.absoluteFill,result0Style]}>
          <View style={[box(40,174,313,17),styles.center]}><Text allowFontScaling={false} style={txt(12,'400','#999D96')}>New stone</Text></View>
          <View style={[box(40,198,313,28),styles.center]}><Text allowFontScaling={false} style={txt(22,'600')}>Mosslight</Text></View>
        </Animated.View>
        <Animated.View pointerEvents="none" {...hidden((ui&4)===0)} style={[StyleSheet.absoluteFill,result1Style]}><View style={[box(40,234,313,36),styles.center]}><Text allowFontScaling={false} style={[txt(12.5,'400','#999D96'),{lineHeight:17*s,textAlign:'center'}]}>Seven focused sessions.{'\n'}A little more room to think.</Text></View></Animated.View>
        <Animated.View pointerEvents="none" {...hidden(true)} style={[box(155,282.5,83,5),{flexDirection:'row',gap:8*s},result2Style]}>{Array.from({length:7},(_,i)=><View key={i} style={{width:5*s,height:5*s,borderRadius:2.5*s,backgroundColor:'rgba(182,204,85,.82)'}} />)}</Animated.View>
        <Animated.View animatedProps={actionProps} style={[StyleSheet.absoluteFill,result3Style]}>
          <Pressable accessibilityRole="button" accessibilityLabel={kept?'Added to collection':'Keep stone'} onPress={()=>scheduleOnUI(act,'keep')} style={[box(32,626,329,44),styles.center]}><LinearGradient pointerEvents="none" colors={['#D9A849','#B6CC55','#74DD4C']} start={{x:0,y:.5}} end={{x:1,y:.5}} style={[StyleSheet.absoluteFill,{borderRadius:22*s}]} /><Text allowFontScaling={false} style={txt(14,'500','#18250D')}>{kept?'✓  Added to collection':'Keep stone'}</Text></Pressable>
          <Pressable accessibilityRole="button" onPress={()=>scheduleOnUI(act,'replay')} style={[box(134,679,125,44),styles.center]}><Text allowFontScaling={false} style={txt(12.5,'400','#999D96')}>Replay</Text></Pressable>
        </Animated.View>
        <Pressable accessibilityRole="button" accessibilityLabel="Close reward" onPress={()=>scheduleOnUI(act,'close')} style={[box(18,138,44,44),styles.center]}><Text allowFontScaling={false} style={txt(21,'400','#999D96')}>×</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Skip reward" onPress={()=>scheduleOnUI(act,'close')} style={[box(318,138,44,44),styles.center]}><Text allowFontScaling={false} style={txt(12.5,'500','#999D96')}>Skip</Text></Pressable>
        <GestureDetector gesture={pan}><Animated.View animatedProps={hitProps} accessible accessibilityLabel="Mosslight stone" accessibilityHint={reduced?'Your earned stone.':'Hold or drag upward to reveal your stone.'} accessibilityActions={!reduced&&previewTime===undefined?[{name:'reveal',label:'Reveal stone'}]:[]} onAccessibilityAction={e=>{if(e.nativeEvent.actionName==='reveal')scheduleOnUI(act,'reveal');}} style={box(80,310,233,285)} /></GestureDetector>
      </Animated.View>
      {collapsed&&<Pressable accessibilityRole="button" accessibilityLabel="View stone" onPress={()=>scheduleOnUI(act,'reopen')} style={[box(108.5,416,176,48),styles.center,{borderRadius:24*s,backgroundColor:'#232923'}]}><Text allowFontScaling={false} style={txt(15,'500')}>⬡  View stone</Text></Pressable>}
    </>}
  </View>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#07090A',overflow:'hidden'},center:{alignItems:'center',justifyContent:'center'},centerY:{justifyContent:'center'}});
