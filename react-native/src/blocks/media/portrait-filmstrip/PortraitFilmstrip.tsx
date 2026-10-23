import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8;
type Props = { autoPlay?: boolean; previewTime?: number };
const photos = [require('./assets/portrait.jpg'), require('./assets/mountain.jpg'), require('./assets/forest.jpg')];
const titles = ['Quiet mornings', 'Beyond the ridge', 'A softer world'];
function ease(a: number, b: number, t: number) { 'worklet'; const x = Math.min(1, Math.max(0, (t - a) / (b - a))); return x * x * x * (10 + x * (-15 + 6 * x)); }
function impact() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }
function Frame({ index, time, weight, height }: { index: number; time: SharedValue<number>; weight: SharedValue<number>; height: number }) {
  const n = (index + 3) % 3;
  const labels = useAnimatedStyle(() => { const scroll=ease(3.75,4.55,time.get())*(1-ease(6.85,7.6,time.get()))*weight.get();return {opacity:index===0?1-ease(.15,.75,scroll):index===1?ease(.25,.8,scroll):0}; });
  const style = useAnimatedStyle(() => {
    const t = time.get();
    const z = (index === 0 ? ease(1.05,1.85,t)*(1-ease(2.8,3.55,t)) : index === 1 ? ease(4.85,5.45,t)*(1-ease(6.05,6.65,t)):0)*weight.get();
    const scroll = ease(3.75,4.55,t)*(1-ease(6.85,7.6,t))*weight.get();
    const width = 271 + 122 * z;
    return { width, height: 530 + (height - 530) * z, left: (393 - width) / 2, top: (160 + index * 550 - 550 * scroll) * (1 - z), zIndex: z > 0 ? 20 : index + 2 };
  });
  const faceStyle = useAnimatedStyle(() => {
    const t=time.get(),w=weight.get();
    const z=(index===0?ease(1.05,1.85,t)*(1-ease(2.8,3.55,t)):index===1?ease(4.85,5.45,t)*(1-ease(6.05,6.65,t)):0)*w;
    const scroll=ease(3.75,4.55,t)*(1-ease(6.85,7.6,t))*w,bend=Math.sin(Math.PI*z),closing=index===0?t>2.8:t>6.05;
    const yaw=5.5*bend*(closing?-1:1)-3*Math.sin(Math.PI*scroll)*(1-z);
    return {borderRadius:30*(1-z),transform:[{perspective:850},{rotateY:`${yaw}deg`},{rotateX:`${-2*bend}deg`}]};
  });
  const imageStyle=useAnimatedStyle(()=>{const t=time.get();const z=(index===0?ease(1.05,1.85,t)*(1-ease(2.8,3.55,t)):index===1?ease(4.85,5.45,t)*(1-ease(6.05,6.65,t)):0)*weight.get();const bend=Math.sin(Math.PI*z);return {transform:[{scale:1+36*bend/(271+122*z)},{translateX:-9*bend}]};});
  // Keep the yawed face inside a flat stacking context so neighboring cards cannot cut through it.
  return <Animated.View collapsable={false} style={[styles.frame,style]}><Animated.View style={[styles.photo,faceStyle]}>
    <Animated.Image source={photos[n]} style={[StyleSheet.absoluteFill,{width:"100%",height:"100%"},imageStyle]} resizeMode="cover" />
    <LinearGradient colors={['transparent', 'rgba(0,0,0,0.6)']} style={styles.shade} />
    <Animated.View style={[styles.caption,labels]}><Text style={styles.kicker}>JOURNAL / 0{n + 1}</Text><Text style={styles.title}>{titles[n]}</Text></Animated.View>
    <Animated.View style={[styles.arrow,labels]}><Text style={styles.arrowText}>↗</Text></Animated.View>
  </Animated.View></Animated.View>;
}
export function PortraitFilmstrip({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 393, height: 852 });
  const clock = useSharedValue(0), last = useSharedValue(-1), manual = useSharedValue(0), origin = useSharedValue(0), touching = useSharedValue(false);
  const dragBase=useSharedValue(0),velocity=useSharedValue(0),tapDuration=useSharedValue(0);
  const time = useDerivedValue(() => {
    if (previewTime !== undefined) return previewTime % LOOP;
    if (reduced) return 2.5;
    const d = last.get() < 0 ? -1 : clock.get() - last.get();
    if (touching.get()) return manual.get();
    if (d >= 0 && d < 2.7) return Math.min(7.6,Math.max(0,manual.get()+Math.min(tapDuration.get(),d)+velocity.get()*.16*(1-Math.exp(-d/.16))));
    return autoPlay ? (d >= 2.7 ? d - 2.7 : clock.get()) % LOOP : 0;
  });
  const weight=useDerivedValue(()=>{const d=clock.get()-last.get();return previewTime!==undefined||reduced||touching.get()||last.get()<0||d>=2.7?1:1-ease(2,2.7,d);});
  useFrameCallback(frame => { if (origin.get() === 0) origin.set(frame.timestamp); clock.set((frame.timestamp - origin.get()) / 1000); }, !reduced && previewTime === undefined);
  useEffect(() => { if (previewTime !== undefined) clock.set(previewTime); }, [previewTime, clock]);
  const gesture = Gesture.Race(
    Gesture.Pan().minDistance(8).onStart(()=>{dragBase.set(time.get());manual.set(time.get());velocity.set(0);tapDuration.set(0);touching.set(true);last.set(clock.get());}).onUpdate(event => { manual.set(Math.min(7.6, Math.max(0, dragBase.get() - event.translationY / (135 * size.width / 393)))); last.set(clock.get()); }).onEnd(event=>{velocity.set(Math.min(3,Math.max(-3,-event.velocityY/(135*size.width/393))));}).onFinalize(()=>{if(touching.get()){touching.set(false);last.set(clock.get());}}),
    Gesture.Tap().onEnd(() => { velocity.set(0);const next=time.get()>3.75&&time.get()<6.85;tapDuration.set(next?.6:.8);manual.set(next?4.85:1.05); last.set(clock.get()); scheduleOnRN(impact); }),
  );
  const chrome = useAnimatedStyle(() => { const t=time.get(); return { opacity: 1 - Math.max(ease(1.05,1.85,t)*(1-ease(2.8,3.55,t)),ease(4.85,5.45,t)*(1-ease(6.05,6.65,t)))*weight.get() }; });
  const first = useAnimatedStyle(() => ({ opacity:1-ease(.3,.7,ease(3.75,4.55,time.get())*(1-ease(6.85,7.6,time.get()))*weight.get()) }));
  const second = useAnimatedStyle(() => ({ opacity:ease(.3,.7,ease(3.75,4.55,time.get())*(1-ease(6.85,7.6,time.get()))*weight.get()) }));
  const s=size.width/393,h=size.height/s;
  const layout=(event: LayoutChangeEvent) => setSize(event.nativeEvent.layout);
  return <View style={styles.screen} onLayout={layout} accessibilityLabel="Field Notes photo journal. Drag vertically to explore the filmstrip, or tap to expand a photo.">
    <StatusBar style="light"/><GestureDetector gesture={gesture}><Animated.View style={{ width:393,height:h,transform:[{scale:s}],transformOrigin:'top left' }}>
      <Animated.View style={[styles.header,chrome]}><Text style={styles.brand}>FIELD NOTES</Text><View><Animated.Text style={[styles.counter,first]}>01 / 03</Animated.Text><Animated.Text style={[styles.counter,styles.overlap,second]}>02 / 03</Animated.Text></View></Animated.View>
      {[-1,0,1,2].map(index => <Frame key={index} index={index} time={time} weight={weight} height={h} />)}
      <Animated.Text style={[styles.hint,{top:h-58},chrome]}>DRAG TO EXPLORE</Animated.Text>
    </Animated.View></GestureDetector>
  </View>;
}
const styles=StyleSheet.create({
  screen:{flex:1,backgroundColor:'#10100F',overflow:'hidden'},frame:{position:'absolute',isolation:'isolate'},photo:{position:'absolute',top:0,right:0,bottom:0,left:0,overflow:'hidden',borderWidth:0.7,borderColor:'rgba(255,255,255,.12)'},shade:{position:'absolute',left:0,right:0,bottom:0,height:'55%'},caption:{position:'absolute',left:20,bottom:27,gap:5},kicker:{color:'rgba(255,255,255,.7)',fontSize:9,fontWeight:'500',letterSpacing:1.9},title:{color:'#fff',fontSize:18,fontWeight:'600'},arrow:{position:'absolute',right:18,bottom:28,width:28,height:28,borderRadius:14,backgroundColor:'#fff',alignItems:'center',justifyContent:'center'},arrowText:{fontSize:19,color:'#000'},header:{position:'absolute',left:25,right:25,top:143,zIndex:22,flexDirection:'row',justifyContent:'space-between'},brand:{fontSize:12,fontWeight:'500',letterSpacing:2.2,color:'rgba(255,255,255,.64)'},counter:{fontSize:11,fontWeight:'500',fontVariant:['tabular-nums'],color:'rgba(255,255,255,.64)'},overlap:{position:'absolute',right:0,top:0},hint:{position:'absolute',width:393,textAlign:'center',fontSize:9,fontWeight:'500',letterSpacing:2,color:'rgba(255,255,255,.35)'},
});
