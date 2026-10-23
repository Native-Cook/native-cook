
import { BlurStyle, Canvas, ClipOp, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
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
function blur(p:Painter,radius:number) { 'worklet';p.blurFilter=radius>.35?Skia.MaskFilter.MakeBlur(BlurStyle.Normal,radius,true):null; }
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

const PATHS:Record<string,SkPath>={"M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z": Skia.PathBuilder.Make().moveTo(-4,84).lineTo(-1,93).lineTo(9,96).lineTo(-1,99).lineTo(-4,108).lineTo(-7,99).lineTo(-17,96).lineTo(-7,93).close().detach(),
        "M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z": Skia.PathBuilder.Make().moveTo(-57,-31).lineTo(-70,-43).lineTo(-57,-56).lineTo(-45,-44).close().detach(),
        "M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z": Skia.PathBuilder.Make().moveTo(-63,25).lineTo(-38,10).lineTo(-18,23).lineTo(-23,55).lineTo(-54,61).lineTo(-70,42).close().detach(),
        "M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z": Skia.PathBuilder.Make().moveTo(-65,-70).cubicTo(-82,-90,-95,-60,-56,-39).cubicTo(-22,-63,-33,-85,-48,-72).cubicTo(-53,-84,-61,-83,-65,-70).close().detach(),
        "M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z": Skia.PathBuilder.Make().moveTo(-71,82).lineTo(-69,64).quadTo(-52,54,-36,64).lineTo(-32,82).lineTo(-43,82).lineTo(-48,75).lineTo(-57,75).lineTo(-61,82).close().detach(),
        "M 18 8 L 53 -4 L 51 32 Z": Skia.PathBuilder.Make().moveTo(18,8).lineTo(53,-4).lineTo(51,32).close().detach(),
        "M 34 74 Q 41 79 48 74": Skia.PathBuilder.Make().moveTo(34,74).quadTo(41,79,48,74).detach(),
        "M 35 127 L 52 98 L 70 127 Z": Skia.PathBuilder.Make().moveTo(35,127).lineTo(52,98).lineTo(70,127).close().detach(),
        "M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z": Skia.PathBuilder.Make().moveTo(45,-103).lineTo(54,-96).lineTo(70,-100).lineTo(75,-94).lineTo(52,-86).lineTo(36,-91).lineTo(39,-96).lineTo(49,-93).close().detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    const turn = 120*(seg(t,1,2)+seg(t,3.1,4.1)+seg(t,5.2,6.3))
    r(p,0,0,393,852,0xFCFBFD)
    e(p,196.5,508,130,8,0xBDBDC4,0.08)
    let order = [0,1,2]
    for (let idx=0;idx<3;idx++) {
        for (let jdx=0;jdx<2;jdx++) {
            const a = ((order[jdx])*120-turn)*3.141592653589793/180
            const b = ((order[jdx+1])*120-turn)*3.141592653589793/180
            if (Math.cos(a) > Math.cos(b)) {
                const hold = order[jdx]
                order[jdx] = order[jdx+1]
                order[jdx+1] = hold
            }
        }
    }
    for (let idx=0;idx<3;idx++) {
        const card = order[idx]
        const theta = ((card)*120-turn)*3.141592653589793/180
        const depth = Math.cos(theta)
        const scale = 0.72+0.28*(depth+1)/2
        const yaw = 0.5+0.5*Math.abs(depth)
        const x = 196.5+143*Math.sin(theta)
        const y = 335-13*depth
        p.canvas.save()
        p.canvas.translate(x,y)
        p.canvas.scale(scale*yaw,scale)
        p.canvas.skew(0,-0.12*Math.sin(theta))
        p.canvas.rotate(2.5*Math.sin(theta),0,0)
        e(p,4,152,80,9,0x6C7074,0.07)
        blur(p,1.8*Math.max(0,1-depth))
        r(p,-85,-135,172,272,0x8A9493,12,0.7)
        grad(p,-84,-136,168,270,pick(card === 0,0xD6DEDC,pick(card === 1,0xF5F5F6,0xEEEFF0)),pick(card === 0,0xB9C7C5,pick(card === 1,0xFDFCFD,0xE3E5E6)),pick(card === 0,0xCAD3D0,pick(card === 1,0xF2F2F2,0xD8DCDD)),12)
        p.canvas.save()
        clip(p,-84,-136,168,270,12)
        if (card === 0) {
            text(p,"h.",-70,-127,18,0x172224,"heavy")
            text(p,"ONE",12,-129,24,0x202B2B,"heavy")
            p.canvas.save()
            p.canvas.translate(-68,104)
            p.canvas.rotate(-90,0,0)
            text(p,"MORE GOOD",0,0,21,0x202B2B,"heavy")
            p.canvas.restore()
            p.canvas.save()
            p.canvas.translate(72,-86)
            p.canvas.rotate(90,0,0)
            text(p,"DAYS",0,0,22,0x202B2B,"heavy")
            p.canvas.restore()
            for (let jdx=0;jdx<2;jdx++) {
                const j = (jdx)
                const cx = -10+19*j
                const cy = -36+108*j
                e(p,cx-22,cy-28,12,13,0xEDB532)
                e(p,cx+22,cy-28,12,13,0xEDB532)
                e(p,cx,cy+3,38,36,0xC78A29,0.25)
                e(p,cx,cy,38,37,0xF9C840)
                e(p,cx,cy+17,29,19,0xDE9E20,0.20)
                e(p,cx-8,cy-11,24,26,0xFFE16D,0.30)
                e(p,cx-22,cy-28,6,8,0xFFE781,0.45)
                e(p,cx+22,cy-28,6,8,0xFFE781,0.45)
                radial(p,cx-12,cy-14,41,0xFFE783,0xFBC937,0.8)
                e(p,cx-11,cy-2,2.5,3,0x302720)
                e(p,cx+10,cy-2,2.5,3,0x302720)
                e(p,cx-19,cy+8,5,3,0xEF8A53,0.8)
                e(p,cx+18,cy+8,5,3,0xEF8A53,0.8)
                e(p,cx,cy+9,9,7,0xFFF3CA)
                e(p,cx,cy+6,3,2.1,0x4D3422)
                line(p,cx,cy+9,cx,cy+12,0x4D3422,1.1)
            }
        }
        if (card === 1) {
            text(p,"h.",-71,-127,18,0x191D21,"heavy")
            p.canvas.save()
            p.canvas.translate(33,-95)
            p.canvas.rotate(24,0,0)
            r(p,-24,-12,65,27,0x3CEB69,2)
            text(p,"PLAY",7,-5,10,0x122A17,"heavy",1)
            p.canvas.restore()
            path(p,"M -65 -70 C -82 -90 -95 -60 -56 -39 C -22 -63 -33 -85 -48 -72 C -53 -84 -61 -83 -65 -70 Z",0x4547DF)
            p.canvas.save()
            p.canvas.rotate(-17,0,0)
            r(p,-88,-26,176,31,0xFFDE11)
            p.canvas.restore()
            path(p,"M 18 8 L 53 -4 L 51 32 Z",0x8F50D7)
            path(p,"M -63 25 L -38 10 L -18 23 L -23 55 L -54 61 L -70 42 Z",0xFE7724)
            e(p,41,67,26,26,0xFFE30D)
            e(p,34,63,1.5,2,0x342F1A)
            e(p,48,63,1.5,2,0x342F1A)
            path(p,"M 34 74 Q 41 79 48 74",0x342F1A,1.2)
            p.canvas.save()
            p.canvas.translate(-47,98)
            p.canvas.rotate(-39,0,0)
            r(p,-38,-14,76,30,0x5153E8,3)
            text(p,"GOOD STUFF",0,-6,9,0xE3E4FF,"heavy",1)
            p.canvas.restore()
            p.canvas.save()
            p.canvas.translate(72,116)
            p.canvas.rotate(14,0,0)
            r(p,-24,-24,48,48,0x25AB60,2)
            text(p,"+",0,-17,29,0x0C6A37,"heavy",1)
            p.canvas.restore()
            e(p,-4,96,18,18,0xFBD519)
            path(p,"M -4 84 L -1 93 L 9 96 L -1 99 L -4 108 L -7 99 L -17 96 L -7 93 Z",0xFEF6A1)
        }
        if (card === 2) {
            text(p,"h.",-72,-128,20,0xC8CECE,"heavy")
            text(p,"travel",-73,-82,37,0xCBD1D2)
            text(p,"love",-7,-33,39,0xCBD1D2)
            text(p,"sports",-73,16,36,0xCBD1D2)
            text(p,"game",-22,59,37,0xCBD1D2)
            text(p,"hike",-72,100,34,0xCBD1D2)
            path(p,"M -57 -31 L -70 -43 L -57 -56 L -45 -44 Z",0xC9CFD0,2.2)
            path(p,"M 45 -103 L 54 -96 L 70 -100 L 75 -94 L 52 -86 L 36 -91 L 39 -96 L 49 -93 Z",0xC7CDCF,2)
            path(p,"M 35 127 L 52 98 L 70 127 Z",0xBFC8C8,2.2)
            path(p,"M -71 82 L -69 64 Q -52 54 -36 64 L -32 82 L -43 82 L -48 75 L -57 75 L -61 82 Z",0xC6CDCF,2.4)
            line(p,-63,67,-63,73,0xC6CDCF,2)
            line(p,-66,70,-60,70,0xC6CDCF,2)
        }
        // The same contact chip sits above every printed surface.
        r(p,-13,-108,29,37,0x8A9192,5)
        grad(p,-11.5,-106.5,26,34,0xD0D3CE,0xAFB6B4,0xDDE0D8,4,true)
        line(p,-3,-106,-3,-97,0x7D8784,0.65)
        line(p,6,-106,6,-97,0x7D8784,0.65)
        r(p,-4,-97,11,15,0xB8BFBB,2)
        line(p,-11,-89,-4,-89,0x7D8784,0.65)
        line(p,7,-89,14,-89,0x7D8784,0.65)
        line(p,-3,-82,-3,-73,0x7D8784,0.65)
        line(p,6,-82,6,-73,0x7D8784,0.65)
        line(p,-81,-133,80,-133,0xFFFFFF,0.9,0.65)
        line(p,-82,-125,-82,125,0xFFFFFF,0.7,0.27+0.2*Math.max(0,depth))
        line(p,82,-125,82,125,0x4A5552,0.8,0.2)
        p.canvas.restore()
        blur(p,0)
        p.canvas.restore()
    }
    text(p,"Your everyday,",196.5,585,31,0x161719,"bold",1)
    text(p,"with more play.",196.5,623,31,0x161719,"bold",1)
    text(p,"Three little ways to make it yours.",196.5,681,14,0x939194,"regular",1)
    line(p,191,746,196.5,752,0xB1ADB3,1.3)
    line(p,196.5,752,202,746,0xB1ADB3,1.3)

}

export default function ColourOrbitCards({autoPlay=false,previewTime}:Props) {
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
    .onUpdate(event=>{const scale=Math.min(width/393,height/852);manual.set(Math.min(LOOP-.001,Math.max(0,origin.get()+event.translationX/scale/90)));tx.set(event.x/scale);ty.set(event.y/scale);lastTouch.set(clock.get());})
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(2.5);velocity.set(Math.max(-5,Math.min(5,event.velocityX/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts};r(p,0,0,width,height,0xFCFBFD);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),196.5+(tx.get()-196.5)*strength,426.0+(ty.get()-426.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Colour Orbit Cards":"Colour Orbit Cards preview "+Math.round(previewTime*1000)} accessibilityHint="Drag sideways to orbit the cards. Tap to bring the next print forward." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="dark"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#FCFBFD'}});
