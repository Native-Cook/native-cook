import { Image } from 'expo-image';
import { useLayoutEffect, useState, type Ref } from 'react';
import { Platform, StyleSheet, useWindowDimensions, type Text, type TextLayoutEventData, type NativeSyntheticEvent } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle } from 'react-native-svg';

import { IDENTITY_ACTOR_BASE as A, type ProfileSourceGeometry, type SourceMetric } from './PrismaticGameProfileLayout';
import { cardPlaneMatrix, initialIdentityLayout, peelIdentityPose, peelRectCoverage, peelPortraitIslandBounds, peelSourcePortraitOpacity, type PlayerIdentityLayout } from './PrismaticGameProfileMotion';
import { peelAvatarBounds, peelSurfaceBounds } from './PrismaticPeelFormation';
import { PlayerCardMaterials, type PlayerCardMaterialFrame } from './PrismaticPlayerCardMaterials';

export type PlayerCardScene = PlayerCardMaterialFrame & { index: number; content: number; catchStrength: number; sealX: number; sealScale: number };
export type PlayerSealLayout = {x:number;scale:number;identity:PlayerIdentityLayout};

/** Every card layer uses this same WORLD pivot, including the selected seal in the rail. */
export function usePlayerCardWorld(frame: SharedValue<PlayerCardScene>) {
  return useAnimatedStyle(() => {
    const f = frame.get();
    return {opacity:f.reduced&&f.progress>0?f.content:1,transformOrigin: [196.5,380,0] as [number,number,number], transform: [
      { matrix: cardPlaneMatrix(f.progress,f.tiltX,f.tiltY,f.reduced) },
    ] };
  });
}

export function PlayerCardSubstrate({ frame }: { frame: SharedValue<PlayerCardScene> }) {
  const world = usePlayerCardWorld(frame);
  const material = useDerivedValue<PlayerCardMaterialFrame>(() => {
    const f = frame.get();
    return { progress: f.progress, tiltX: f.tiltX, tiltY: f.tiltY, reduced: f.reduced, active: f.active,
      sourceX: f.sourceX, sourceY: f.sourceY, sourceSize: f.sourceSize,catchStrength:f.catchStrength,peelAvoidance:f.peelAvoidance };
  });
  return <Animated.View pointerEvents="none" accessible={false} shouldRasterizeIOS renderToHardwareTextureAndroid style={[StyleSheet.absoluteFill,styles.substrate,world]}>
    <PlayerCardMaterials frame={material} width={320} height={590} radius={34} foilStart={348} />
  </Animated.View>;
}

type Metric = { width: number; height: number };
type Metrics = { name: Metric; handle: Metric; skills: Metric; score: Metric };
const DISPLAY = Platform.OS === 'ios' ? 'ArialRoundedMTBold' : 'sans-serif';

/** These native nodes stay mounted at both endpoints. Only their transforms change. */
export function PlayerCardIdentity({ frame, hidden, focusRef, cardContentAccessible, seal,sourceGeometry,onSourceMeasure,caption,hasScore }: { frame: SharedValue<PlayerCardScene>; hidden: boolean; focusRef?: Ref<Text>; cardContentAccessible: boolean; seal: SharedValue<PlayerSealLayout>;sourceGeometry:SharedValue<ProfileSourceGeometry>;onSourceMeasure:(key:SourceMetric,value:number)=>void;caption:string;hasScore:boolean }) {
  const world = usePlayerCardWorld(frame);
  const {fontScale}=useWindowDimensions();
  const [headingHeight,setHeadingHeight]=useState(36);
  const [metrics,setMetrics] = useState<Metrics>({ name: {width:120,height:38},handle:{width:99,height:18},skills:{width:30,height:17},score:{width:49,height:26} });
  const measure = (key: keyof Metrics) => (event: NativeSyntheticEvent<TextLayoutEventData>) => {
    const line = event.nativeEvent.lines[0]; if (!line) return;
    setMetrics(old => Math.abs(old[key].width-line.width)<0.1 && Math.abs(old[key].height-line.height)<0.1 ? old : { ...old,[key]:{width:line.width,height:line.height} });
  };
  // Native font sizes remain system-scaled. Only the optional hero enlargement
  // and measured target positions adapt once, outside the gesture/frame loop.
  const scoreScale=Math.max(1,Math.min(2,180/Math.max(1,metrics.score.width)));
  const scoreDefaultTop=Math.max(578,555+metrics.skills.height*1.15+4);
  const footerShift=Math.max(0,scoreDefaultTop+metrics.score.height*scoreScale-657);
  const skillsTop=555-footerShift, scoreTop=scoreDefaultTop-footerShift;
  const handleTop=Math.min(Math.max(486,449+metrics.name.height*1.1+4),skillsTop-metrics.handle.height*1.08-12);
  const nameTop=Math.min(449,handleTop-metrics.name.height*1.1-4);
  const headingTop=109-8*Math.max(0,Math.min(1,fontScale-1));
  const portraitTop=Math.max(163,headingTop+headingHeight+14);
  const portraitHeight=Math.max(140,nameTop-16-portraitTop);
  const avatarScale=(portraitHeight-15.8)/124;
  const avatarTravel=portraitTop+portraitHeight/2+2.05-avatarScale-A.avatar.centerY;
  useLayoutEffect(() => {
    const right=64.5+metrics.score.width*scoreScale;
    const scale=Math.min(1.05,Math.max(0.3,(346.5-right-12)/114));
    const x=Math.min(346.5-57*scale,Math.max(286.5,right+12+57*scale));
    seal.set({x,scale,identity:{avatarY:A.avatar.centerY+avatarTravel,avatarScale,portraitTop,portraitHeight,
      name:{...metrics.name,x:64.5+metrics.name.width*1.1/2,y:nameTop+metrics.name.height*1.1/2,scale:1.1},
      handle:{...metrics.handle,x:64.5+metrics.handle.width*1.08/2,y:handleTop+metrics.handle.height*1.08/2,scale:1.08},
      skills:{...metrics.skills,x:64.5+metrics.skills.width*1.15/2,y:skillsTop+metrics.skills.height*1.15/2,scale:1.15},
      score:{...metrics.score,x:64.5+metrics.score.width*scoreScale/2,y:scoreTop+metrics.score.height*scoreScale/2,scale:scoreScale}}});
  },[metrics,scoreScale,seal,avatarTravel,avatarScale,nameTop,handleTop,skillsTop,scoreTop,portraitTop,portraitHeight]);
  const avatar = useAnimatedStyle(() => {
    const f=frame.get(),source=sourceGeometry.get().avatar,layout=seal.get().identity??initialIdentityLayout();
    const bounds=peelAvatarBounds(f.progress,source.centerX,source.centerY,source.diameter,layout.avatarY,layout.avatarScale,f.reduced);
    const scale=bounds.width/118;
    return {transform:[{translateX:bounds.x+59*scale-A.avatar.centerX},{translateY:bounds.y+61*scale-A.avatar.centerY},{scale}]};
  });
  const avatarImage=useAnimatedStyle(()=>{
    const f=frame.get(),phase=1-peelSourcePortraitOpacity(f.progress,f.reduced),source=sourceGeometry.get().avatar;
    const sourceScale=(source.diameter+6)/source.templateSize,factor=(source.diameter-8)*1.08/(148*sourceScale);
    return {transform:[{translateY:4*(1-phase)},{scale:factor+(1-factor)*phase}]};
  });
  const clip=useAnimatedStyle(()=>{const f=frame.get(),source=peelSourcePortraitOpacity(f.progress,f.reduced);return {borderRadius:14+48*source,backgroundColor:`rgba(182,140,214,${source})`};});
  const rim=useAnimatedStyle(()=>{const f=frame.get();return {opacity:peelSourcePortraitOpacity(f.progress,f.reduced)};});
  const portrait=useAnimatedStyle(()=>{
    const f=frame.get(),bounds=peelSurfaceBounds(f.progress,f.sourceX,f.sourceY,f.sourceSize,f.reduced);
    const island=peelPortraitIslandBounds(f.progress,f.reduced,sourceGeometry.get(),seal.get().identity);
    const coverage=peelRectCoverage(bounds,island.x,island.y,island.width,island.height);
    const t=Math.max(0,Math.min(1,(f.progress-0.46)/0.16)),phase=f.reduced?(f.progress>0?1:0):t*t*(3-2*t);
    return {opacity:phase*coverage,transform:[{translateX:island.x+island.width/2-196.5},{translateY:island.y+island.height/2-(portraitTop+portraitHeight/2)},{scale:island.width/284}]};
  });
  const name=useAnimatedStyle(()=>{const f=frame.get(),pose=peelIdentityPose('name',f.progress,f.reduced,sourceGeometry.get(),seal.get().identity);return {transform:[{translateX:pose.x-196.5},{translateY:pose.y-pose.baseY},{scale:pose.scale}]};});
  const handle=useAnimatedStyle(()=>{const f=frame.get(),pose=peelIdentityPose('handle',f.progress,f.reduced,sourceGeometry.get(),seal.get().identity);return {transform:[{translateX:pose.x-196.5},{translateY:pose.y-pose.baseY},{scale:pose.scale}]};});
  const skills=useAnimatedStyle(()=>{const f=frame.get(),pose=peelIdentityPose('skills',f.progress,f.reduced,sourceGeometry.get(),seal.get().identity);return {transform:[{translateX:pose.x-196.5},{translateY:pose.y-pose.baseY},{scale:pose.scale}]};});
  const score=useAnimatedStyle(()=>{const f=frame.get(),pose=peelIdentityPose('score',f.progress,f.reduced,sourceGeometry.get(),seal.get().identity);return {transform:[{translateX:pose.x-196.5},{translateY:pose.y-pose.baseY},{scale:pose.scale}]};});
  const heading = useAnimatedStyle(() => { const f=frame.get(); return {opacity:f.content,transform:[{translateY:f.reduced ? 0 : 1.5*(1-f.content)}]}; });
  return <Animated.View pointerEvents="none" shouldRasterizeIOS renderToHardwareTextureAndroid style={[StyleSheet.absoluteFill,styles.identity,world]} accessibilityElementsHidden={hidden} importantForAccessibility={hidden?'no-hide-descendants':'auto'}>
    <Animated.View style={[styles.portrait,{top:portraitTop,height:portraitHeight},portrait]} accessible={false}>
      <Image source={require('./assets/landscape.png')} contentFit="cover" allowDownscaling enforceEarlyResizing transition={0} style={StyleSheet.absoluteFill} accessible={false} />
    </Animated.View>
    <Animated.View style={[styles.avatarGroup,avatar]} accessible={false}>
      <Animated.View style={[StyleSheet.absoluteFill,rim]}><Svg width={140} height={140} accessible={false}>
        <Circle cx={70} cy={70} r={70} fill="#60D9EC"/>
        <Circle cx={70} cy={70} r={65.53191489361701} fill="#FFFFFF"/>

      </Svg></Animated.View>
      <Animated.View style={[styles.avatarClip,clip]}><Animated.View style={[styles.avatar,avatarImage]}><Image source={require('./assets/avatar.png')} contentFit="contain" allowDownscaling={false} enforceEarlyResizing={false} transition={0} style={StyleSheet.absoluteFill} accessible={false}/></Animated.View></Animated.View>
    </Animated.View>
    <Animated.Text ref={focusRef} onTextLayout={measure('name')} onLayout={e=>onSourceMeasure('nameHeight',e.nativeEvent.layout.height*32/38)} style={[styles.name,name]} accessibilityRole="header">Everen</Animated.Text>
    <Animated.Text onTextLayout={measure('handle')} onLayout={e=>onSourceMeasure('handleHeight',e.nativeEvent.layout.height*20/18)} style={[styles.handle,handle]}>@evanoff92</Animated.Text>
    <Animated.Text onTextLayout={measure('skills')} onLayout={e=>onSourceMeasure('skillsHeight',e.nativeEvent.layout.height*20/17)} style={[styles.skills,skills]}>{caption}</Animated.Text>
    <Animated.Text onTextLayout={measure('score')} onLayout={e=>onSourceMeasure('scoreHeight',e.nativeEvent.layout.height*32/26)} style={[styles.score,score,{opacity:hasScore?1:0}]} accessible={hasScore} accessibilityElementsHidden={!hasScore} importantForAccessibility={hasScore?'auto':'no-hide-descendants'}>1.500</Animated.Text>
    <Animated.View onLayout={({nativeEvent:{layout}}) => setHeadingHeight(old=>Math.abs(old-layout.height)<0.1?old:layout.height)} style={[styles.heading,{top:headingTop},heading]} accessibilityElementsHidden={!cardContentAccessible} importantForAccessibility={cardContentAccessible?'auto':'no-hide-descendants'}><Animated.Text style={styles.cardTitle} accessibilityRole="header">PLAYER CARD</Animated.Text><Animated.Text style={styles.cardSubtitle}>Profile preview</Animated.Text></Animated.View>
  </Animated.View>;
}

const styles=StyleSheet.create({
  substrate:{zIndex:1000},identity:{zIndex:1001},
  portrait:{position:'absolute',left:54.5,top:163,width:284,height:270,borderRadius:28,overflow:'hidden',borderWidth:1.5,borderColor:'#D6AFE5',backgroundColor:'#A66FC0'},
  avatarGroup:{position:'absolute',left:A.avatar.templateLeft,top:A.avatar.templateTop,width:A.avatar.templateSize,height:A.avatar.templateSize},
  avatarClip:{position:'absolute',left:11,top:9,width:118,height:124,borderRadius:62,overflow:'hidden',backgroundColor:'#8A6B8F'},
  avatar:{position:'absolute',left:-15,top:-16,width:148,height:156},
  name:{position:'absolute',top:A.name.top,left:60,width:273,textAlign:'center',fontFamily:DISPLAY,fontSize:32,lineHeight:38,fontWeight:'900',letterSpacing:-0.7,color:'#071E2C',includeFontPadding:false},
  handle:{position:'absolute',top:A.handle.top,left:70,width:253,textAlign:'center',color:'#5C6A75',fontSize:13,lineHeight:18,fontWeight:'600',includeFontPadding:false},
  skills:{position:'absolute',top:A.skills.top,left:70,width:253,textAlign:'center',color:'#5C6A75',fontSize:13,lineHeight:17,fontWeight:'600',includeFontPadding:false},
  score:{position:'absolute',top:A.score.top,left:133,width:127,textAlign:'center',color:'#071F2B',fontSize:20,lineHeight:26,fontWeight:'900',includeFontPadding:false},
  heading:{position:'absolute',left:64.5,top:109},cardTitle:{fontSize:14,lineHeight:18,fontWeight:'900',letterSpacing:0.7,color:'#111C42'},cardSubtitle:{fontSize:12,lineHeight:18,fontWeight:'600',color:'#8B718C'},
});
