import { memo, type Ref } from 'react';
import { StyleSheet, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { PROFILE_LAYOUT as L, sourceBadgeOrigin, type ProfileSourceGeometry } from './PrismaticGameProfileLayout';
import { BadgeMaterial } from './PrismaticGameProfileMaterials';
import { cardPlaneMatrix, peelSealChannels, peelSealLift, type PrismaticController, type PrismaticFrame } from './PrismaticGameProfileMotion';
import type { PlayerCardScene } from './PrismaticGameProfilePlayerCard';

const BADGES = [
  { name: 'Crown', image: require('./assets/crown.png'), colors: ['#B766F7', '#C780FA'] as const },
  { name: 'Shield', image: require('./assets/shield.png'), colors: ['#FFCA4C', '#FFD866'] as const },
  { name: 'Diamond', image: require('./assets/diamond.png'), colors: ['#D5E43B', '#E3EC67'] as const },
];
const SECTORS=Array.from({length:52},(_,i)=>{const a=i*2*Math.PI/52,b=(i+1)*2*Math.PI/52;return `M79 79L${79+79*Math.cos(a)} ${79+79*Math.sin(a)}A79 79 0 0 1 ${79+79*Math.cos(b)} ${79+79*Math.sin(b)}Z`;});
const Badge = memo(function Badge({ index, frame, renderer, onRenderer, player,sourceGeometry }: { index: number; frame: SharedValue<PrismaticFrame>; renderer: 'auto' | 'skia'; onRenderer: (ready: boolean) => void; player: SharedValue<PlayerCardScene>;sourceGeometry:SharedValue<ProfileSourceGeometry> }) {
  const item = BADGES[index];
  const world = useAnimatedStyle(() => {
    const f=player.get(); const selected=f.index === index && f.progress > 0; const p=selected && !f.reduced ? f.progress : 0;
    return {zIndex:f.index === index ? 1002 : 0,opacity:f.reduced&&selected?f.content:1,transformOrigin:[196.5,380,0] as [number,number,number],transform:[{matrix:cardPlaneMatrix(p,f.tiltX,f.tiltY,f.reduced)}]};
  });
  const pose = useAnimatedStyle(() => {
    const f=player.get();const raw=f.index===index?f.progress:0,phase=peelSealChannels(raw,f.reduced),p=phase.y;
    const source=sourceBadgeOrigin(frame.get().rail,index,sourceGeometry.get());
    const x=p > 0 ? f.sourceX : source.x;
    const y=p > 0 ? f.sourceY : source.y;
    const size=p > 0 ? f.sourceSize : source.size;
    return { opacity:1-0.3*Math.min(1,Math.abs(index-frame.get().rail))*(1-p),transform: [{ translateX:x-196.5+(f.sealX-x)*phase.x }, { translateY:y-455+(587-y)*phase.y-peelSealLift(raw,f.reduced) }, { scale:size/158+(f.sealScale-size/158)*phase.scale }] };
  });
  const materialPose=useAnimatedStyle(()=>{
    const f=player.get(),p=peelSealChannels(f.index===index?f.progress:0,f.reduced).scale,d=Math.min(1,Math.abs(index-frame.get().rail));
    const source=sourceBadgeOrigin(frame.get().rail,index,sourceGeometry.get());
    const ratio=index===1?728/768:index===0?512/410:512/408;
    const fitWidth=Math.min(132-52*d,(140-68*d)*ratio)*sourceGeometry.get().rail.size/164;
    const factor=fitWidth/(114*source.size/158);
    return {transform:[{scale:1+(factor-1)*(1-p)}]};
  });
  const backing = useAnimatedStyle(() => {
    const f=player.get(); const t=Math.max(0,Math.min(1,(f.progress-0.015)/0.155));
    return { opacity:f.index === index ? 1-t*t*(3-2*t) : 1 };
  });
  return <Animated.View style={[StyleSheet.absoluteFill,world]} shouldRasterizeIOS renderToHardwareTextureAndroid pointerEvents="none" accessible={false}>
    <Animated.View style={[styles.badge, pose]}>
      <Animated.View style={[StyleSheet.absoluteFill,backing]}><View style={styles.plate}><Svg width={158} height={158} accessible={false}>{SECTORS.map((d,i)=><Path key={i} d={d} fill={item.colors[i%2]} />)}</Svg></View></Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill,materialPose]}><BadgeMaterial index={index} asset={item.image} frame={frame} renderer={renderer} onRenderer={onRenderer} /></Animated.View>
    </Animated.View>
  </Animated.View>;
});

export function PrismaticRail({ scene, renderer, onRenderer, inspect, targetRef, player, hidden }: { scene: PrismaticController; renderer: 'auto' | 'skia'; onRenderer: (ready: boolean) => void; inspect: () => void; targetRef?: Ref<View>; player: SharedValue<PlayerCardScene>; hidden: boolean }) {
  const sourceGeometry=scene.sourceGeometry;
  // This metadata changes only on font/layout measurement, never on scene frames.
  const target=useAnimatedStyle(()=>{const g=sourceGeometry.get().rail;return {top:g.targetTop,height:g.targetHeight};});
  return <>
    {BADGES.map((_, index) => <Badge key={index} index={index} frame={scene.frame} renderer={renderer} onRenderer={onRenderer} player={player} sourceGeometry={sourceGeometry} />)}
    <GestureDetector gesture={scene.gesture}><Animated.View ref={targetRef} style={[styles.target,target]} collapsable={false} accessible accessibilityElementsHidden={hidden} importantForAccessibility={hidden?'no-hide-descendants':'auto'} accessibilityRole="adjustable" accessibilityLabel={`${BADGES[scene.selected].name} badge, ${scene.selected+1} of 3`}
      accessibilityHint={scene.reduced ? 'Use previous or next to browse. Activate to open your player card; Back returns to your profile.' : 'Swipe left or right to browse. Pull the selected badge upward to form your player card, or activate it. Pull the card downward to return.'}
      accessibilityValue={{ min: 1, max: 3, now: scene.selected+1, text: BADGES[scene.selected].name }}
      accessibilityActions={[{ name: 'increment', label: 'Next badge' }, { name: 'decrement', label: 'Previous badge' }, { name: 'activate', label: 'Open player card' }]}
      onAccessibilityAction={({ nativeEvent: { actionName } }) => { if (actionName === 'activate') inspect(); else scene.select(scene.selected+(actionName === 'increment' ? 1 : -1)); }}
      testID="prismatic-carousel" /></GestureDetector>
  </>;
}
const styles = StyleSheet.create({ badge: { position: 'absolute', left:117.5,top:376,width:158,height:158 }, plate: { width: 158, height: 158, borderRadius: 79, overflow: 'hidden' }, target: { position: 'absolute', left:0,top:L.rail.targetTop,width:L.stage.width,height:L.rail.targetHeight,zIndex:3 } });
