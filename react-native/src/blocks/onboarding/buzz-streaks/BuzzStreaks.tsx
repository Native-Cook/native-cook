import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Image, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Canvas, LinearGradient as SkiaLinearGradient, Path, RoundedRect, Skia } from '@shopify/react-native-skia';
import Animated, { Easing, cancelAnimation, useAnimatedReaction, useAnimatedStyle, useReducedMotion, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { flow, type FlowKey } from './motion';
import { BuzzStreaksMascot, BuzzStreaksSparks } from './BuzzStreaksMascots';
import { BuzzStreaksRewardBackground, BuzzStreaksRewardBadges, BuzzStreaksRewards } from './BuzzStreaksRewards';
import { BuzzStreaksArtwork } from './BuzzStreaksArtwork';
import { BuzzStreaksBonus, BuzzStreaksPrize } from './BuzzStreaksIllustrations';
import { BuzzStreaksCaption, BuzzStreaksLabelShimmer, useBuzzStreaksTextAssets } from './BuzzStreaksTextMotion';
import { type BuzzStreaksMotionProfile } from './BuzzStreaksSignature';
import { heroDomino } from './BuzzStreaksHero';
import { BuzzStreaksPortalLips, BuzzStreaksRelayPackets } from './BuzzStreaksSignatureDrawing';
import { BuzzStreaksSignatureHero } from './BuzzStreaksSignatureHero';
import data from './assets/poses.json';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

const ART = {
  background: require('./assets/background.png'), wordmark: require('./assets/wordmark.png'),
  invite: require('./assets/invite.png'),
  phone: require('./assets/phone.png'), coin: require('./assets/coin.png'),
  daycoin: require('./assets/daycoin.png'),day1:require('./assets/day-1.png'),day2:require('./assets/day-2.png'),day3:require('./assets/day-3.png'),day4:require('./assets/day-4.png'),day5:require('./assets/daycoin.png'), label: require('./assets/start-label.png'),
};
const READY_BITS = {
  orange: 1, pink: 2, 'coin-pink5': 4, 'coin-gold1': 8, 'coin-gold2': 16,
  background: 32, wordmark: 64, label: 128, invite: 256, phone: 512, coin: 1024,
  day1: 2048, day2: 4096, day3: 8192, day4: 16384, daycoin: 32768,
  textFont: 65536, labelMask: 131072,
} as const;
const BASE_ART_READY = 65535;
const END = 10.699;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
const RIBBON = Skia.Path.MakeFromSVGString('M 0 0 L 132 0 L 124.08 20 L 7.92 20 Z')!;
function pairs(rows: number[][]): readonly FlowKey[] { return rows.map(row => [row[0],row[1]] as const); }
const layout=Object.fromEntries(Object.entries(data.layout).map(([key,rows])=>[key,pairs(rows)])) as Record<keyof typeof data.layout,readonly FlowKey[]>;

/** One 10.7s scene. The reference ends expanded; this component deliberately holds that state. */
export function BuzzStreaks({autoPlay=false,enhancedText=true,motionProfile='approved'}:{autoPlay?:boolean;enhancedText?:boolean;motionProfile?:BuzzStreaksMotionProfile}) {
  const {width,height}=useWindowDimensions();const scale=Math.min(width/360,height/780);
  const reduced=useReducedMotion();const clock=useSharedValue(reduced?10.699:0);
  const pressProgress=useSharedValue(0);
  const [active,setActive]=useState(AppState.currentState==='active');
  const [artReady,setArtReady]=useState(0);
  const markReady=useCallback((kind:keyof typeof READY_BITS)=>setArtReady(value=>value|READY_BITS[kind]),[]);
  const enhanced=enhancedText&&!reduced;
  const signatureEnabled=motionProfile!=='approved'&&!reduced;
  const characterEnabled=motionProfile==='character'&&!reduced;
  const expressiveVfx=signatureEnabled&&!characterEnabled;
  const textAssets=useBuzzStreaksTextAssets(enhanced);
  const [playingIntent,setPlayingIntent]=useState(autoPlay&&!reduced);
  const playing=useRef(autoPlay&&!reduced);
  const pendingFrom=useRef<number|null>(null);
  const generation=useRef(0);
  const [playRevision,setPlayRevision]=useState(0);
  const [phase,setPhase]=useState(reduced?4:0);
  useAnimatedReaction(()=>clock.get()<1.5?0:clock.get()<2.9?1:clock.get()<4.2?2:clock.get()<7.5?3:4,(value,previous)=>{if(value!==previous)scheduleOnRN(setPhase,value)});
  const [fonts]=useFonts({BuzzMedium:require('./assets/Gilroy-Medium.ttf'),BuzzSemiBold:require('./assets/Gilroy-SemiBold.ttf'),BuzzExtraBold:require('./assets/Gilroy-ExtraBold.ttf')});
  const required=BASE_ART_READY|(enhanced?READY_BITS.textFont|READY_BITS.labelMask:0);
  const optionalReady=enhanced?(textAssets.font?READY_BITS.textFont:0)|(textAssets.mask?READY_BITS.labelMask:0):0;
  const ready=fonts&&((artReady|optionalReady)&required)===required;
  useEffect(()=>{
    const subscription=AppState.addEventListener('change',state=>{
      const nextActive=state==='active';
      if(!nextActive){cancelAnimation(clock);cancelAnimation(pressProgress);pressProgress.set(0)}
      setActive(nextActive);
      // An inactive/active pair can be batched back to the same Boolean even though
      // the imperative cancellation already ran. Reconcile every foreground event.
      if(nextActive)setPlayRevision(value=>value+1);
    });
    return()=>subscription.remove();
  },[clock,pressProgress]);
  useEffect(()=>()=>cancelAnimation(pressProgress),[pressProgress]);
  const finish=useCallback((completedGeneration:number)=>{
    // A completed older replay may already be queued on JS when a new tap arrives.
    if(completedGeneration!==generation.current)return;
    playing.current=false;
    setPlayingIntent(false);
  },[]);
  useEffect(()=>{
    const canPlay=active&&ready&&playing.current&&!reduced;
    const requestedFrom=canPlay?pendingFrom.current:null;
    if(canPlay)pendingFrom.current=null;
    if(reduced){pendingFrom.current=null;playing.current=false}
    // Reset, inspect and start on the same UI job. A JS read immediately after
    // clock.set(from) can otherwise see the previous END and leave replay at0.
    scheduleOnUI((from:number|null,shouldPlay:boolean,reduceMotion:boolean,token:number)=>{
      'worklet';
      cancelAnimation(clock);
      if(reduceMotion){clock.set(END);scheduleOnRN(finish,token);return}
      if(!shouldPlay)return;
      if(from!==null)clock.set(from);
      const position=clock.get();
      if(position>=END){scheduleOnRN(finish,token);return}
      clock.set(withTiming(END,{duration:(END-position)*1000,easing:Easing.linear},finished=>{
        'worklet';
        if(finished===true)scheduleOnRN(finish,token);
      }));
    },requestedFrom,canPlay,reduced,generation.current);
    return()=>cancelAnimation(clock);
  },[active,ready,clock,playingIntent,playRevision,reduced,finish]);
  function play(from=0){
    if(!ready)return;
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    cancelAnimation(clock);
    generation.current+=1;
    pendingFrom.current=reduced?END:from;
    playing.current=!reduced;
    setPlayingIntent(!reduced);
    setPlayRevision(value=>value+1);
  }
  const header=useAnimatedStyle(()=>({transform:[{translateY:flow(clock.get(),layout.header)}]}));
  const panel=useAnimatedStyle(()=>({transform:[{translateY:flow(clock.get(),layout.panel)}]}));
  const bonus=useAnimatedStyle(()=>({opacity:clock.get()>=7.5?1:0,transform:[{translateY:flow(clock.get(),layout.bonusY)-511}]}));
  const days=useAnimatedStyle(()=>({transform:[{translateX:flow(clock.get(),layout.daysX)}]}));
  const invite=useAnimatedStyle(()=>({opacity:flow(clock.get(),layout.invite_opacity),transform:[{translateY:flow(clock.get(),layout.invite_y)},{scale:flow(clock.get(),layout.invite_scale)}]}));
  const back=useAnimatedStyle(()=>({opacity:flow(clock.get(),layout.back_opacity),transform:[{translateY:flow(clock.get(),layout.back_y)},{scale:flow(clock.get(),layout.back_scale)}]}));
  const pressFeedback=useAnimatedStyle(()=>({transform:[{scale:1-(reduced?.015:.035)*pressProgress.get()},{translateY:reduced?0:1.5*pressProgress.get()}]}));
  // Mount native text only after registration; hiding an already-mounted fallback font is insufficient.
  if(!fonts)return <View style={s.root}/>;
  return <View style={s.root}><View accessibilityElementsHidden={!ready} pointerEvents={ready?'auto':'none'} style={[s.stage,{opacity:ready?1:0,transform:[{scale}],left:(width-360)/2,top:(height-780)/2}]}>
    <Image source={ART.background} onLoad={()=>markReady('background')} style={StyleSheet.absoluteFill}/>
    <Animated.View accessible accessibilityRole="header" accessibilityLabel="Buzz Streaks. Win up to ₹1000." style={[s.hero,header]} pointerEvents="none"><Image source={ART.wordmark} onLoad={()=>markReady('wordmark')} style={s.wordmark}/><BuzzStreaksPrize style={s.prize} clock={clock} enhanced={enhanced}/></Animated.View>
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <BuzzStreaksRewardBackground clock={clock} signatureEnabled={signatureEnabled}/>
      {expressiveVfx&&<><BuzzStreaksSignatureHero clock={clock}/><BuzzStreaksRelayPackets clock={clock}/></>}
      <BuzzStreaksSparks clock={clock}/>
      <BuzzStreaksMascot kind="orange" clock={clock} onReady={markReady} signatureEnabled={signatureEnabled} characterEnabled={characterEnabled}/>
      <BuzzStreaksMascot kind="pink" clock={clock} onReady={markReady} signatureEnabled={signatureEnabled} characterEnabled={characterEnabled}/>
      {characterEnabled&&<BuzzStreaksRelayPackets clock={clock} characterEnabled/>}
      <BuzzStreaksRewards clock={clock} onReady={markReady} signatureEnabled={signatureEnabled} characterEnabled={characterEnabled}/>
      {expressiveVfx&&<BuzzStreaksSignatureHero clock={clock} foreground/>}
    </Canvas>
    <BuzzStreaksRewardBadges clock={clock} signatureEnabled={signatureEnabled}/>
    {enhanced?<><Animated.View accessible accessibilityLabel="Invite your friends by buzzing" accessibilityElementsHidden={phase!==0} importantForAccessibility={phase===0?'auto':'no-hide-descendants'} style={[s.captionFrame,invite]}><BuzzStreaksCaption text="Invite your friends by buzzing" start={.05} clock={clock} animate={autoPlay||playingIntent} font={textAssets.font}/></Animated.View><Animated.View accessible accessibilityLabel="They buzz you back" accessibilityElementsHidden={phase<1||phase>=3} importantForAccessibility={phase>=1&&phase<3?'auto':'no-hide-descendants'} style={[s.captionFrame,back]}><BuzzStreaksCaption text="They buzz you back" start={1.52} clock={clock} animate={autoPlay||playingIntent} font={textAssets.font}/></Animated.View></>:<><Animated.Text accessibilityElementsHidden={phase!==0} importantForAccessibility={phase===0?'auto':'no-hide-descendants'} style={[s.caption,invite]}>Invite your friends by buzzing</Animated.Text><Animated.Text accessibilityElementsHidden={phase<1||phase>=3} importantForAccessibility={phase>=1&&phase<3?'auto':'no-hide-descendants'} style={[s.caption,back]}>They buzz you back</Animated.Text></>}
    <View style={s.movingClip} pointerEvents="box-none">
    <Animated.View accessibilityElementsHidden={phase<3} importantForAccessibility={phase>=3?'auto':'no-hide-descendants'} style={[s.how,panel]}><Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><RoundedRect x={0} y={0} width={330} height={224} r={25} style="stroke" strokeWidth={1}><SkiaLinearGradient start={{x:165,y:0}} end={{x:165,y:224}} colors={['#aa65d3','rgba(170,101,211,0)']}/></RoundedRect>{signatureEnabled&&<BuzzStreaksPortalLips clock={clock}/>}</Canvas><View style={s.dotted}/><View style={s.rows}>
      <Row art={ART.invite} title="Buzz friends by inviting" body="Send them a link to join the game" onReady={markReady}/>
      <Row art={ART.phone} title="They “Buzz” you back" body="By opening the link" onReady={markReady}/>
      <Row art={ART.coin} title="Both earn Free Cash!" body="After buzzing each other" onReady={markReady}/>
    </View><View style={s.ribbon}><Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><Path path={RIBBON}><SkiaLinearGradient start={{x:66,y:0}} end={{x:66,y:20}} colors={['#e875ff','#aa00f0']}/></Path></Canvas><Text style={s.ribbonText}>HOW BUZZING WORKS</Text></View></Animated.View>
    <Animated.View accessibilityElementsHidden={phase<4} importantForAccessibility={phase>=4?'auto':'no-hide-descendants'} style={[s.bonuses,bonus]}><View style={[s.bonus,{width:157}]}><Text style={s.h3}>Buzz more friends</Text><Text style={s.body}>Get <Text style={s.highlight}>₹5 bonus</Text> each{'\n'}for your first 5 friends</Text><BuzzStreaksBonus clock={clock}/></View><View style={[s.bonus,{width:165}]}><Text style={s.h3}>Daily streaks</Text><Text style={s.body}>Keep the streak alive{'\n'}to earn more</Text><Animated.View style={[s.days,days]}>{[1,2,3,4,5,6,7].map(day=><View key={day} style={s.day}><DayCoin day={day} clock={clock} signatureEnabled={signatureEnabled} onReady={markReady}/><Text style={s.dayLabel}>DAY {day}</Text></View>)}</Animated.View></View></Animated.View>
    </View>
    <AnimatedPressable accessible collapsable={false} accessibilityRole="button" testID="buzz-start" accessibilityLabel="Start buzzing" onPressIn={()=>pressProgress.set(reduced?1:withTiming(1,{duration:90,easing:Easing.out(Easing.cubic)}))} onPressOut={()=>pressProgress.set(reduced?0:withSpring(0,{stiffness:420,damping:26,mass:.7}))} onPress={()=>play()} style={[s.start,pressFeedback]}><LinearGradient colors={['#b4d61a','#b1d214']} start={{x:0,y:.5}} end={{x:1,y:.5}} style={StyleSheet.absoluteFill}/><LinearGradient colors={['#c9e73c','#d9f650']} start={{x:0,y:.5}} end={{x:1,y:.5}} style={s.startInner}/><View style={s.label}><Image source={ART.label} onLoad={()=>markReady('label')} resizeMode="stretch" style={s.labelImage}/>{enhanced&&<BuzzStreaksLabelShimmer clock={clock} mask={textAssets.mask}/>}</View></AnimatedPressable>
    <Pressable accessible collapsable={false} accessibilityRole="button" testID="buzz-back" accessibilityLabel="Replay introduction" onPress={()=>play()} style={[s.navHit,{left:9}]}><View style={s.nav}><Text style={s.navArrow}>←</Text></View></Pressable>
    <Pressable accessible collapsable={false} accessibilityRole="button" testID="buzz-info" accessibilityLabel="How buzzing works" onPress={()=>play(7.25)} style={[s.navHit,{left:307}]}><View style={s.nav}><Text style={s.navInfo}>ⓘ</Text></View></Pressable>
  </View></View>;
}
function DayCoin({day,clock,signatureEnabled,onReady}:{day:number;clock:SharedValue<number>;signatureEnabled:boolean;onReady:(kind:keyof typeof READY_BITS)=>void}) {
  const motion=useAnimatedStyle(()=>{
    const w=heroDomino(clock.get(),day,signatureEnabled);
    return {transform:[{translateY:-12*w}]};
  });
  const shadow=useAnimatedStyle(()=>{
    const w=heroDomino(clock.get(),day,signatureEnabled);
    return {opacity:.28*w,transform:[{scaleX:1-.32*w}]};
  });
  const artwork=<BuzzStreaksArtwork name={`day-${Math.min(day,5)}`} source={ART[`day${Math.min(day,5)}` as 'day1']} width={34} height={34} style={s.coin} onLoad={()=>onReady(day>=5?'daycoin':`day${day}` as 'day1'|'day2'|'day3'|'day4')}/>;
  return day>=5&&signatureEnabled?<View style={s.coin}><Animated.View pointerEvents="none" style={[s.coinShadow,shadow]}/><Animated.View style={[s.coin,motion]}>{artwork}</Animated.View></View>:artwork;
}
function Row({art,title,body,onReady}:{art:number;title:string;body:string;onReady:(kind:'invite'|'phone'|'coin')=>void}) {const name=art===ART.coin?'coin':art===ART.phone?'phone':'invite';const rect=art===ART.coin?[5,6,28,30]:art===ART.phone?[1,-4,36,44]:[4,4,32,33];return <View style={s.row}><View style={s.rowArt}><BuzzStreaksArtwork name={name} source={art} width={rect[2]} height={rect[3]} style={{position:'absolute',left:rect[0],top:rect[1]}} onLoad={()=>onReady(name)}/></View><View style={s.rowText}><Text style={[s.h3,s.rowTitle]}>{title}</Text><Text style={s.body}>{body}</Text></View></View>}
const s=StyleSheet.create({
  root:{flex:1,backgroundColor:'#6623a9'},stage:{position:'absolute',width:360,height:780,overflow:'hidden'},hero:{position:'absolute',left:86,top:79,width:190,height:163},wordmark:{position:'absolute',left:40,top:0,width:107,height:51},prize:{position:'absolute',left:0,top:64,width:190,height:99},
  captionFrame:{position:'absolute',top:634,width:360,height:24},caption:{position:'absolute',top:634,width:360,textAlign:'center',fontFamily:'BuzzSemiBold',fontSize:19.6,lineHeight:24,color:'white'},
  movingClip:{position:'absolute',left:0,top:0,width:360,height:674,overflow:'hidden'},how:{position:'absolute',left:15,top:0,width:330,height:224,borderRadius:25,backgroundColor:'#7a3bbb',overflow:'hidden'},rows:{top:37},row:{height:66,flexDirection:'row',paddingLeft:16,gap:10},rowArt:{width:42,height:44},rowText:{paddingTop:3,flex:1},h3:{fontFamily:'BuzzSemiBold',fontSize:15,lineHeight:20,color:'white',marginBottom:2},rowTitle:{fontSize:15.25},body:{fontFamily:'BuzzMedium',fontSize:12.8,lineHeight:18,color:'#c9a5e6'},ribbon:{position:'absolute',left:98,top:0,width:132,height:20,alignItems:'center'},ribbonText:{fontFamily:'BuzzExtraBold',fontSize:9.5,lineHeight:16,paddingTop:1,color:'white'},dotted:{position:'absolute',left:33,top:60,height:125,borderLeftWidth:1,borderColor:'#bd84e3',borderStyle:'dashed'},
  bonuses:{position:'absolute',left:15,top:511,width:330,height:158,flexDirection:'row',gap:8},bonus:{height:158,borderRadius:21,backgroundColor:'#7a3bbb',overflow:'hidden',padding:16},highlight:{backgroundColor:'#f16ca4',color:'white'},days:{position:'absolute',top:89,left:5,flexDirection:'row',gap:17},day:{alignItems:'center',gap:7},coin:{width:34,height:34},dayLabel:{fontFamily:'BuzzExtraBold',fontSize:11,lineHeight:12,color:'white'},
  coinShadow:{position:'absolute',left:5,top:32,width:24,height:4,borderRadius:12,backgroundColor:'#471174'},
  start:{position:'absolute',left:14,top:674,width:332,height:57,borderRadius:7,borderWidth:1,borderColor:'#abd416',backgroundColor:'#b9db18',overflow:'hidden',alignItems:'center',justifyContent:'center'},startInner:{position:'absolute',left:6,right:6,top:4,bottom:6,borderRadius:4,backgroundColor:'#d2f644'},label:{width:106,height:23},labelImage:{position:"absolute",left:0,top:0,width:106,height:23},navHit:{position:'absolute',top:66,width:45,height:45,alignItems:'center',justifyContent:'center'},nav:{width:33,height:33,borderRadius:17,backgroundColor:'white',alignItems:'center',justifyContent:'center'},navArrow:{fontSize:25,lineHeight:29,color:'#17221b'},navInfo:{fontSize:20,color:'#17221b'},
});
