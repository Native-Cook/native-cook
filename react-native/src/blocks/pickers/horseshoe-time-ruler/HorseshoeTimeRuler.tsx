
import { Canvas, ClipOp, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect,useMemo } from 'react';
import { AppState,Platform,StyleSheet,View,useWindowDimensions } from 'react-native';
import { Gesture,GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue,useFrameCallback,useReducedMotion,useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8.0;
type Props={autoPlay?:boolean;previewTime?:number};

function smooth(value: number) { 'worklet';const u=Math.min(1,Math.max(0,value));return u*u*u*(10+u*(-15+6*u)); }
function seg(t: number,a: number,b: number) { 'worklet';return smooth((t-a)/(b-a)); }
function pick<T>(condition: boolean,a: T,b: T): T { 'worklet';return condition?a:b; }
function color(hex: number,alpha=1) { 'worklet';return Skia.Color(`rgba(${(hex>>16)&255},${(hex>>8)&255},${hex&255},${Math.min(1,Math.max(0,alpha))})`); }
type Painter={canvas: SkCanvas;paint: SkPaint;fonts: Record<string,SkFont>;blurFilter?:ReturnType<typeof Skia.MaskFilter.MakeBlur>|null};
function reset(p: Painter,hex: number,alpha=1,width=0) { 'worklet';p.paint.setShader(null);p.paint.setMaskFilter(p.blurFilter??null);p.paint.setAlphaf(1);p.paint.setColor(color(hex,alpha));p.paint.setStyle(width>0?PaintStyle.Stroke:PaintStyle.Fill);p.paint.setStrokeWidth(width);p.paint.setStrokeCap(StrokeCap.Round);p.paint.setStrokeJoin(StrokeJoin.Round);p.paint.setAntiAlias(true); }
function r(p: Painter,x: number,y: number,w: number,h: number,hex: number,radius=0,alpha=1) { 'worklet';reset(p,hex,alpha);p.canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x,y,Math.max(0,w),Math.max(0,h)),radius,radius),p.paint); }
function e(p: Painter,x: number,y: number,rx: number,ry: number,hex: number,alpha=1) { 'worklet';reset(p,hex,alpha);p.canvas.drawOval(Skia.XYWHRect(x-rx,y-ry,rx*2,ry*2),p.paint); }
function line(p: Painter,x: number,y: number,xx: number,yy: number,hex: number,width=1,alpha=1) { 'worklet';reset(p,hex,alpha,width);p.canvas.drawLine(x,y,xx,yy,p.paint); }
function grad(p: Painter,x: number,y: number,w: number,h: number,a: number,b: number,d: number,radius=0,horizontal=false,alpha=1) { 'worklet';reset(p,0xffffff);p.paint.setShader(Skia.Shader.MakeLinearGradient({x,y},{x:horizontal?x+w:x,y:horizontal?y:y+h},[color(a,alpha),color(b,alpha),color(d,alpha)],[0,.5,1],TileMode.Clamp));p.canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x,y,Math.max(0,w),Math.max(0,h)),radius,radius),p.paint);p.paint.setShader(null); }
function radial(p: Painter,x: number,y: number,radius: number,a: number,b: number,alpha=1) { 'worklet';reset(p,0xffffff);p.paint.setShader(Skia.Shader.MakeRadialGradient({x,y},radius,[color(a,alpha),color(b,0)],[0,1],TileMode.Clamp));p.canvas.drawCircle(x,y,radius,p.paint);p.paint.setShader(null); }
function clip(p: Painter,x: number,y: number,w: number,h: number,radius=0) { 'worklet';p.canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(x,y,w,h),radius,radius),ClipOp.Intersect,true); }
function text(p: Painter,value: string,x: number,y: number,size: number,hex: number,style='regular',align=0,alpha=1) {
  'worklet';reset(p,hex,alpha);const font=p.fonts[style];const sx=style==='condensed'?.8:1;
  const widths=font.getGlyphWidths(font.getGlyphIDs(value));let width=0;for(const item of widths) width+=item;
  const scale=size/100,offset=align===1?width/2:align===2?width:0;
  p.canvas.save();p.canvas.translate(x-offset*scale*sx,y);p.canvas.scale(scale*sx,scale);p.canvas.drawText(value,0,-font.getMetrics().ascent,p.paint,font);p.canvas.restore();
}

const PATHS:Record<string,SkPath>={"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145": Skia.PathBuilder.Make().moveTo(82,145).lineTo(82,548).cubicTo(82,700,311,700,311,548).lineTo(311,145).detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    // A single path bends the linear day into a continuous horseshoe.
    const travel = 410*seg(t,1,3.8)*(1-seg(t,4.8,7))
    r(p,0,0,393,852,0x070708)
    p.canvas.save()
    p.canvas.translate(0,-22)
    grad(p,0,55,393,102,0x232225,0x262528,0x242326)
    r(p,53,97,70,30,0x2D2C2F,5)
    r(p,130,97,130,30,0x2D2C2F,5)
    r(p,267,97,72,30,0x2D2C2F,5)
    text(p,"Friday",88,105,12,0xD6D4D9,"regular",1)
    text(p,"23 Oct 2026",195,105,12,0xD6D4D9,"regular",1)
    text(p,"13:36",303,105,12,0xD6D4D9,"regular",1)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(0,-32)
    clip(p,20,155,353,575)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x171619,111)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x050506,105)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x27262A,100)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x09090B,94)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x2F2E32,88)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x08080A,82)
    path(p,"M 82 145 L 82 548 C 82 700 311 700 311 548 L 311 145",0x252428,74)
    const names = ["Morning pages","Studio review","A walk outside","Time to make","Lunch with team","Creative hour","Evening reset","Plan tomorrow"]
    const times = ["08:00","09:30","11:00","12:15","13:30","15:00","17:30","19:00"]
    for (let idx=0;idx<64;idx++) {
        const i = (idx)
        let distance = i*18+travel
        if (distance >= 1150) { distance = distance-1150 }
        let x = 82.0
        let y = 155+distance
        let angle = 90.0
        if (distance > 393 && distance < 753) {
            const theta = 3.141592653589793-(distance-393)/114.5
            x = 196.5+114.5*Math.cos(theta)
            y = 548+114.5*Math.sin(theta)
            angle = theta*180/3.141592653589793-90
        }
        if (distance >= 753) {
            x = 311
            y = 548-(distance-753)
            angle = -90
        }
        p.canvas.save()
        p.canvas.translate(x,y)
        p.canvas.rotate(angle,0,0)
        line(p,0,-36,0,-32,0x545158,0.7,0.55)
        line(p,0,32,0,36,0x545158,0.7,0.55)
        p.canvas.restore()
    }
    for (let idx=0;idx<8;idx++) {
        const i = (idx)
        let distance = 45+i*143+travel
        if (distance >= 1150) { distance = distance-1150 }
        let x = 82.0
        let y = 155+distance
        let angle = 90.0
        if (distance > 393 && distance < 753) {
            const theta = 3.141592653589793-(distance-393)/114.5
            x = 196.5+114.5*Math.cos(theta)
            y = 548+114.5*Math.sin(theta)
            angle = theta*180/3.141592653589793-90
        }
        if (distance >= 753) {
            x = 311
            y = 548-(distance-753)
            angle = -90
        }
        p.canvas.save()
        p.canvas.translate(x,y)
        p.canvas.rotate(angle,0,0)
        line(p,0,-36,0,36,pick(idx === 4,0xEF3B59,0x434147),pick(idx === 4,1.8,0.8))
        text(p,times[idx],8,-22,9,0x969199,"mono")
        text(p,names[idx],8,-7,10,0x8B858F)
        p.canvas.restore()
    }
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(0,-48)
    line(p,196.5,616,196.5,729,0x117AFF,1.5,0.9)
    line(p,194.5,623,194.5,690,0x0A4D91,0.7,0.7)
    for (let idx=0;idx<8;idx++) {
        const i = (idx)
        e(p,196.5,626+14*i,1.5,1.5,0x50A7FF,0.45)
    }
    radial(p,196.5,593,35,0x208EFF,0x070708,0.13)
    r(p,169,565,55,58,0x070708,28)
    e(p,196.5,593,25,25,0x0B1826)
    e(p,196.5,593,23,23,0x1987F9)
    e(p,196.5,593,20,20,0x050A0E)
    p.canvas.save()
    p.canvas.translate(196.5,593)
    p.canvas.rotate(-42+travel*0.085,0,0)
    line(p,0,0,12,-10,0x258FFF,2.2)
    line(p,0,0,-3,6,0x258FFF,2)
    e(p,0,0,2.2,2.2,0x59B9FF)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(196.5,553)
    p.canvas.rotate(-90,0,0)
    text(p,"IN 18 HOURS",0,-6,9,0x77717D,"mono")
    p.canvas.restore()
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(0,-14)
    e(p,42,756,7,7,0x157DE0)
    e(p,42,756,5.8,5.8,0x070708)
    line(p,42,756,42,752,0x268DF7,1)
    line(p,42,756,45,757,0x268DF7,1)
    text(p,"Days",42,780,13,0x368FE6,"regular",1)
    text(p,"Follow the day",196.5,780,14,0x368FE6,"regular",1)
    e(p,351,786,8,8,0x1F7FE5)
    e(p,351,786,6.5,6.5,0x070708)
    line(p,347,789,355,781,0x368FE6,1.2)
    line(p,350,781,355,781,0x368FE6,1.2)

    p.canvas.restore()

}

export default function HorseshoeTimeRuler({autoPlay=false,previewTime}:Props) {
  const {width,height}=useWindowDimensions(),reduced=useReducedMotion();
  const latest=useSharedValue(0),clock=useSharedValue(0),epoch=useSharedValue(-1),lastTouch=useSharedValue(-100),manual=useSharedValue(0),origin=useSharedValue(0),dragging=useSharedValue(false),velocity=useSharedValue(0),tx=useSharedValue(196.5),ty=useSharedValue(426.0),active=useSharedValue(true);
  const fonts=useMemo(()=>{
    const family=Platform.OS==='ios'?'Helvetica':'sans-serif';
    return {regular:matchFont({fontFamily:family,fontSize:100}),bold:matchFont({fontFamily:family,fontSize:100,fontWeight:'700'}),heavy:matchFont({fontFamily:family,fontSize:100,fontWeight:'900'}),condensed:matchFont({fontFamily:Platform.OS==='ios'?'Helvetica Neue':family,fontSize:100,fontWeight:'900'}),mono:matchFont({fontFamily:Platform.OS==='ios'?'Menlo':'monospace',fontSize:100}),serif:matchFont({fontFamily:Platform.OS==='ios'?'Georgia':'serif',fontSize:100,fontWeight:'bold'}),italic:matchFont({fontFamily:Platform.OS==='ios'?'Georgia':'serif',fontSize:100,fontWeight:'bold',fontStyle:'italic'})};
  },[]);
  const callback=useFrameCallback(frame=>{
    'worklet';if(!active.get())return;const now=frame.timestamp/1000;latest.set(now);if(epoch.get()<0)epoch.set(now);
    if(autoPlay||dragging.get()||now-lastTouch.get()<2.4)clock.set(now);
  },!reduced&&previewTime===undefined);
  useEffect(()=>{callback.setActive(!reduced&&previewTime===undefined);},[callback,reduced,previewTime]);
  useEffect(()=>{const sub=AppState.addEventListener('change',state=>{active.set(state==='active');if(state==='active'){epoch.set(-1);lastTouch.set(-100);}});return ()=>sub.remove();},[active,epoch,lastTouch]);
  function currentTime(now:number,started:number,touched:number,value:number,speed:number,isDragging:boolean) {
    'worklet';if(previewTime!==undefined)return Math.max(0,previewTime)%LOOP;if(reduced)return 3.8;
    const age=Math.max(0,now-touched);
    if(touched>=0){if(isDragging)return value;if(age<2)return Math.min(LOOP-.001,Math.max(0,value+speed*age*Math.exp(-8*age)));if(age<2.4)return value*(1-smooth((age-2)/.4));return autoPlay?(age-2.4)%LOOP:0;}
    return autoPlay&&started>=0?Math.max(0,now-started)%LOOP:0;
  }
  const pan=Gesture.Pan().minDistance(0).enabled(!reduced)
    .onBegin(event=>{origin.set(currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()));manual.set(origin.get());clock.set(latest.get());dragging.set(true);lastTouch.set(clock.get());const scale=Math.min(width/393,height/852);tx.set(event.x/scale);ty.set(event.y/scale);})
    .onUpdate(event=>{const scale=Math.min(width/393,height/852);manual.set(Math.min(LOOP-.001,Math.max(0,origin.get()+-event.translationY/scale/90)));tx.set(event.x/scale);ty.set(event.y/scale);lastTouch.set(clock.get());})
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(3.8);velocity.set(Math.max(-5,Math.min(5,-event.velocityY/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts};r(p,0,0,width,height,0x070708);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),196.5+(tx.get()-196.5)*strength,426.0+(ty.get()-426.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Horseshoe Time Ruler":"Horseshoe Time Ruler preview "+Math.round(previewTime*1000)} accessibilityHint="Drag vertically to travel through the day. Tap to explore the afternoon." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="light"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#070708'}});
