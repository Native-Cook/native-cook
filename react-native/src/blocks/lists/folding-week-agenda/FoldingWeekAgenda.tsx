
import { Canvas, ClipOp, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect,useMemo } from 'react';
import { AppState,Platform,StyleSheet,View,useWindowDimensions } from 'react-native';
import { Gesture,GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue,useFrameCallback,useReducedMotion,useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 7.2;
type Props={autoPlay?:boolean;previewTime?:number};

function smooth(value: number) { 'worklet';const u=Math.min(1,Math.max(0,value));return u*u*u*(10+u*(-15+6*u)); }
function seg(t: number,a: number,b: number) { 'worklet';return smooth((t-a)/(b-a)); }
function pick<T>(condition: boolean,a: T,b: T): T { 'worklet';return condition?a:b; }
function str(value: number) { 'worklet';return String(Math.trunc(value)); }
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

const PATHS:Record<string,SkPath>={"M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z": Skia.PathBuilder.Make().moveTo(12,45).lineTo(12,40).cubicTo(14,34,22,36,22,29).cubicTo(16,28,17,24,14,23).lineTo(17,17).cubicTo(12,3,36,-1,39,12).cubicTo(42,20,35,27,32,31).lineTo(33,36).lineTo(40,49).lineTo(14,49).close().detach(),
        "M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z": Skia.PathBuilder.Make().moveTo(143,786).lineTo(149,780).lineTo(155,786).lineTo(155,792).lineTo(143,792).close().detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    // A continuous calendar surface folds into the next appointment.
    const fold = seg(t,1.1,2.0)*(1-seg(t,3.6,4.6))
    const oldAlpha = 1-seg(fold,0.25,0.65)
    const compactAlpha = seg(fold,0.35,0.8)
    const panelHeight = 340-280*fold
    r(p,0,0,393,852,0xFCFCFA)
    radial(p,196,478-276*fold,192,0xF3DDB2,0xFCFCFA,0.32*(1-fold))
    r(p,24,108,345,panelHeight,0xCECBC5,24,0.08)
    r(p,24,106,345,panelHeight,0x191A19,24)
    p.canvas.save()
    clip(p,24,106,345,panelHeight,24)
    text(p,"Upcoming",44,130-27*fold,24,0xF1F1EA,"regular",0,oldAlpha)
    text(p,"In the next 2 weeks",44,163-24*fold,21,0x70746F,"regular",0,oldAlpha)
    for (let idx=0;idx<3;idx++) {
        const i = (idx)
        e(p,335,138+8*i-50*fold,1.3,1.3,0x92978F,oldAlpha)
    }
    const week = ["MON","TUE","WED","THU","FRI","SAT","SUN"]
    for (let idx=0;idx<14;idx++) {
        const i = (idx)
        const column = (idx % 7)
        const row = Math.floor(i/7)
        const x = 58+46*column
        const y = 358+47*row-225*fold
        e(p,x,y,17.5,17.5,pick(idx === 4,0xE5E7DF,0x30332E),oldAlpha)
        text(p,str(i+8),x,y-7,12,pick(idx === 4,0x222922,0xE1E4DB),"regular",1,oldAlpha)
        if (idx < 7) { text(p,week[idx],x,325-215*fold,8,0x777D73,"mono",1,oldAlpha) }
        if (idx === 4) {
            e(p,x-6,y-10,2,2,0xCB78D8,oldAlpha)
            e(p,x,y-11,2,2,0x9CD657,oldAlpha)
            e(p,x+6,y-10,2,2,0x83C5FF,oldAlpha)
        }
    }
    r(p,42,124,28,28,0xEBEDE4,7,compactAlpha)
    line(p,50,133,62,133,0x6F766B,1.2,compactAlpha)
    line(p,50,139,59,139,0x6F766B,1.2,compactAlpha)
    text(p,"Next appointment",83,120,10,0x90998D,"regular",0,compactAlpha)
    text(p,"Studio check-in · 10:30",83,137,14,0xF2F3EB,"regular",0,compactAlpha)
    line(p,330,144,340,134,0xE4E6DF,1.5,compactAlpha)
    line(p,332,134,340,134,0xE4E6DF,1.5,compactAlpha)
    line(p,340,134,340,142,0xE4E6DF,1.5,compactAlpha)
    p.canvas.restore()
    const titles = ["Studio check-in","Color notes","The season ahead","Wellness review","A quieter morning","Movement session"]
    const subtitles = ["Action needed","Upcoming","Your personal roadmap","Completed","Completed","Completed"]
    const months = ["Mar","Mar","Jan","Jan","Dec","Nov"]
    const days = ["22","12","08","08","18","29"]
    const inks = [0xFFAE23,0xEAEDE5,0xFF8A3B,0xE9EDE5,0xD38A54,0xE4E6DE]
    p.canvas.save()
    clip(p,0,180-12*fold,393,640+12*fold)
    for (let idx=0;idx<6;idx++) {
        const i = (idx)
        const follow = seg(t,1.1+0.035*i,2.0+0.035*i)*(1-seg(t,3.6+0.035*i,4.6+0.035*i))
        const y = 469+91*i-280*follow
        r(p,24,y+2,345,81,0xD9DBD5,17,0.09)
        grad(p,24,y,345,81,0xF6F7F4,0xF8F9F6,0xF5F6F4,17)
        text(p,months[idx],40,y+14,12,0x969D92)
        text(p,days[idx],40,y+33,23,0x747D70)
        line(p,84,y+16,84,y+64,0xE8EBE4,1)
        r(p,100,y+15,49,49,inks[idx],9)
        if (idx === 0) {
            p.canvas.save()
            p.canvas.translate(100,y+15)
            path(p,"M 12 45 L 12 40 C 14 34 22 36 22 29 C 16 28 17 24 14 23 L 17 17 C 12 3 36 -1 39 12 C 42 20 35 27 32 31 L 33 36 L 40 49 L 14 49 Z",0x251C15)
            p.canvas.restore()
        }
        if (idx === 1 || idx === 3) {
            for (let jdx=0;jdx<3;jdx++) {
                const j = (jdx)
                r(p,110+10*j,y+26,3,27,0xC4C9BC,1)
                r(p,109+10*j,y+21,5,8,pick(jdx === 0,0xA4CE54,pick(jdx === 1,0xD57AC5,0x89BCEA)),1)
            }
        }
        if (idx === 2) {
            for (let jdx=0;jdx<4;jdx++) {
                const j = (jdx)
                e(p,111+24*(jdx % 2),y+28+23*Math.floor(j/2),10,10,0xFFD755,0.5)
            }
            line(p,103,y+39,145,y+39,0xFFE17F,1.2,0.65)
        }
        if (idx > 3) {
            e(p,124,y+31,10,11,0x572E21)
            r(p,111,y+42,26,22,0x674130,9)
        }
        text(p,titles[idx],162,y+21,14,0x373D34)
        text(p,subtitles[idx],162,y+44,12,0x9CA495)
        if (idx < 2) {
            e(p,337,y+41,15,15,0xEFF1EA)
            r(p,332,y+39,10,9,0xCAD0C3,3)
            line(p,334,y+38,334,y+34,0xAFBAA7,1)
            line(p,340,y+38,340,y+34,0xAFBAA7,1)
            line(p,334,y+34,340,y+34,0xAFBAA7,1)
        }
        if (idx === 2) { text(p,"›",338,y+23,29,0x333B2F,"regular",1) }
        if (idx > 2) {
            e(p,337,y+40,11,11,0xA2E8CB)
            line(p,332,y+40,336,y+44,0xFAFFF7,1.8)
            line(p,336,y+44,342,y+36,0xFAFFF7,1.8)
        }
    }
    p.canvas.restore()
    grad(p,0,808,393,44,0xFCFCFA,0xFCFCFA,0xFCFCFA,0,false,0.95)
    p.canvas.save()
    p.canvas.translate(196.5,0)
    p.canvas.scale(160.0/147.0,1)
    p.canvas.translate(-196.5,0)
    r(p,123,762,147,50,0x070C08,26)
    e(p,149,787,18,18,0x2E372E)
    path(p,"M 143 786 L 149 780 L 155 786 L 155 792 L 143 792 Z",0xF2F7EC)
    for (let idx=0;idx<3;idx++) {
        const i = (idx)
        r(p,180+4*i,787-3*i,2.5,8+3*i,0xA2ADA0,1)
        line(p,219+3*i,782+3*i,225+3*i,782+3*i,0x9EAA9C,1.4)
        e(p,253,782+5*i,1.2,1.2,0x9EAA9C)
    }
    p.canvas.restore()

}

export default function FoldingWeekAgenda({autoPlay=false,previewTime}:Props) {
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
    'worklet';if(previewTime!==undefined)return Math.max(0,previewTime)%LOOP;if(reduced)return 0.0;
    const age=Math.max(0,now-touched);
    if(touched>=0){if(isDragging)return value;if(age<2)return Math.min(LOOP-.001,Math.max(0,value+speed*age*Math.exp(-8*age)));if(age<2.4)return value*(1-smooth((age-2)/.4));return autoPlay?(age-2.4)%LOOP:0;}
    return autoPlay&&started>=0?Math.max(0,now-started)%LOOP:0;
  }
  const pan=Gesture.Pan().minDistance(0).enabled(!reduced)
    .onBegin(event=>{origin.set(currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()));manual.set(origin.get());clock.set(latest.get());dragging.set(true);lastTouch.set(clock.get());const scale=Math.min(width/393,height/852);tx.set(event.x/scale);ty.set(event.y/scale);})
    .onUpdate(event=>{const scale=Math.min(width/393,height/852);manual.set(Math.min(LOOP-.001,Math.max(0,origin.get()+-event.translationY/scale/90)));tx.set(event.x/scale);ty.set(event.y/scale);lastTouch.set(clock.get());})
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(2.4);velocity.set(Math.max(-5,Math.min(5,-event.velocityY/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts};r(p,0,0,width,height,0xFCFCFA);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),196.5+(tx.get()-196.5)*strength,426.0+(ty.get()-426.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Folding Week Agenda":"Folding Week Agenda preview "+Math.round(previewTime*1000)} accessibilityHint="Drag upward to fold the calendar. Tap to preview the compact agenda." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="dark"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#FCFCFA'}});
