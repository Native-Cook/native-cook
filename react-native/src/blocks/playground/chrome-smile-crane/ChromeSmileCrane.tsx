
import { AlphaType, ColorType, BlurStyle, Canvas, ClipOp, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
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

function makeGrain() {
  const bytes=new Uint8Array(192*384*4);let seed=41389;
  for(let index=0;index<192*384;index++){
    seed=(Math.imul(seed,1664525)+1013904223)>>>0;const shade=96+(seed>>>24)%80;
    bytes[index*4]=shade;bytes[index*4+1]=shade;bytes[index*4+2]=shade;bytes[index*4+3]=255;
  }
  return Skia.Image.MakeImage({width:192,height:384,colorType:ColorType.RGBA_8888,alphaType:AlphaType.Premul},Skia.Data.fromBytes(bytes),192*4);
}
const GRAIN=makeGrain();
function grain(p:Painter,x:number,y:number,w:number,h:number,alpha:number) {
  'worklet';if(!GRAIN)return;reset(p,0xffffff);p.paint.setAlphaf(alpha);
  p.canvas.drawImageRect(GRAIN,Skia.XYWHRect(0,0,192,384),Skia.XYWHRect(x,y,w,h),p.paint);
}

const PATHS:Record<string,SkPath>={"M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z": Skia.PathBuilder.Make().moveTo(-12,11).quadTo(0,25,13,11).quadTo(1,19,-12,11).close().detach(),
        "M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z": Skia.PathBuilder.Make().moveTo(-12,12).quadTo(0,25,13,11).quadTo(1,19,-12,12).close().detach(),
        "M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z": Skia.PathBuilder.Make().moveTo(-15,4).lineTo(-31,31).lineTo(-25,47).lineTo(-19,34).lineTo(-5,10).close().detach(),
        "M -15 5 L -27 31": Skia.PathBuilder.Make().moveTo(-15,5).lineTo(-27,31).detach(),
        "M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z": Skia.PathBuilder.Make().moveTo(-15,5).lineTo(-27,31).lineTo(-23,38).lineTo(-18,31).lineTo(-7,10).close().detach(),
        "M -15 5 L -27 31 L -24 32 L -11 7 Z": Skia.PathBuilder.Make().moveTo(-15,5).lineTo(-27,31).lineTo(-24,32).lineTo(-11,7).close().detach(),
        "M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z": Skia.PathBuilder.Make().moveTo(-5,5).lineTo(-7,32).lineTo(-1,41).lineTo(4,30).lineTo(6,5).close().detach(),
        "M -5 5 L -7 32 L -3 34 L 0 5 Z": Skia.PathBuilder.Make().moveTo(-5,5).lineTo(-7,32).lineTo(-3,34).lineTo(0,5).close().detach(),
        "M 124 654 L 136 664 L 136 730 L 124 743 Z": Skia.PathBuilder.Make().moveTo(124,654).lineTo(136,664).lineTo(136,730).lineTo(124,743).close().detach(),
        "M 136 730 L 256 730 L 270 743 L 124 743 Z": Skia.PathBuilder.Make().moveTo(136,730).lineTo(256,730).lineTo(270,743).lineTo(124,743).close().detach(),
        "M 270 654 L 256 664 L 256 730 L 270 743 Z": Skia.PathBuilder.Make().moveTo(270,654).lineTo(256,664).lineTo(256,730).lineTo(270,743).close().detach(),
        "M 296 304 L 307 307 L 307 496 L 301 516 Z": Skia.PathBuilder.Make().moveTo(296,304).lineTo(307,307).lineTo(307,496).lineTo(301,516).close().detach(),
        "M 338 262 L 326 277 L 321 610 L 333 606 Z": Skia.PathBuilder.Make().moveTo(338,262).lineTo(326,277).lineTo(321,610).lineTo(333,606).close().detach(),
        "M 55 262 L 67 277 L 72 610 L 61 606 Z": Skia.PathBuilder.Make().moveTo(55,262).lineTo(67,277).lineTo(72,610).lineTo(61,606).close().detach(),
        "M 58 558 L 333 558 L 351 585 L 42 585 Z": Skia.PathBuilder.Make().moveTo(58,558).lineTo(333,558).lineTo(351,585).lineTo(42,585).close().detach(),
        "M 63 562 L 329 562 L 341 582 L 52 582 Z": Skia.PathBuilder.Make().moveTo(63,562).lineTo(329,562).lineTo(341,582).lineTo(52,582).close().detach(),
        "M 75 304 L 317 304 L 303 316 L 81 311 Z": Skia.PathBuilder.Make().moveTo(75,304).lineTo(317,304).lineTo(303,316).lineTo(81,311).close().detach(),
        "M 89 299 L 106 299 L 86 432 L 86 365 Z": Skia.PathBuilder.Make().moveTo(89,299).lineTo(106,299).lineTo(86,432).lineTo(86,365).close().detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    const descend = seg(t,2,3)*(1-seg(t,3.4,4.5))
    const grip = seg(t,3,3.4)*(1-seg(t,5.4,5.8))
    let clawX = 260-132*seg(t,1,2)+132*seg(t,4.5,5.4)
    if (touching) { clawX = Math.min(287,Math.max(105,tx)) }
    const clawY = 337+149*descend
    let ballX = 128.0
    let ballY = 510.0
    let ballAlpha = 1.0
    if (t >= 3 && t < 5.4) { ballX=128+(clawX-128)*seg(t,3,3.15);ballY=510+(clawY+32-510)*seg(t,3,3.15) }
    if (t >= 5.4 && t < 6.2) {
        const fall = Math.min(1,Math.max(0,(t-5.4)/0.8))
        ballX=260-63*fall
        ballY=369+335*fall*fall
    }
    if (t >= 6.2 && t < 7) { ballX=197;ballY=704;ballAlpha=1-seg(t,6.3,6.8) }
    if (t >= 7) { ballAlpha=seg(t,7,7.4) }
    grad(p,0,0,393,852,0x04BADD,0x1888AD,0x126B8B)
    radial(p,192,270,410,0x1BD7E3,0x137D9E,0.42)
    // The narrow upper case tapers into the wide, forward-projecting deck.
    grad(p,53,222,287,402,0x47515C,0xCCD0C7,0x707D84,22,true)
    path(p,"M 55 262 L 67 277 L 72 610 L 61 606 Z",0x293B48)
    path(p,"M 338 262 L 326 277 L 321 610 L 333 606 Z",0x273746)
    grad(p,66,297,261,314,0xA1A4A0,0xC6C8C1,0xA0AAA8,3,true)
    p.canvas.save()
    p.canvas.translate(196.5,68)
    p.canvas.scale(0.92,0.85)
    p.canvas.translate(-196.5,0)
    grad(p,47,187,299,91,0x778B95,0x354452,0x152C43,20)
    r(p,56,196,281,70,0xC6CFCE,13)
    r(p,59,199,275,63,0x13233E,11)
    grad(p,63,203,267,53,0x07122D,0x133C88,0x0A1739,8)
    radial(p,197,229,148,0x2D73DC,0x172F68,0.6)
    const letters = ["S","M","I","L","E"]
    for (let idx=0;idx<5;idx++) {
        const i = (idx)
        p.canvas.save()
        blur(p,3)
        text(p,letters[idx],112+42*i,207,32,0x6BAFFF,"heavy",1)
        p.canvas.restore()
        blur(p,0)
        text(p,letters[idx],112+42*i,207,32,0xE8FFF8,"heavy",1)
    }
    p.canvas.restore()
    // Reflected smiles live behind a shallow upper glass band, as in the source.
    grad(p,69,298,255,49,0x9FA5A5,0xD3D5CE,0x969F9E,3)
    p.canvas.save()
    clip(p,74,304,245,34,4)
    grad(p,74,304,245,34,0x737C81,0xADB3AE,0x696E76,4)
    blur(p,2)
    for (let idx=0;idx<3;idx++) {
        const i=(idx)
        e(p,128+65*i,326,25,12,0xE7C64B,0.32)
        e(p,120+65*i,324,2,3,0x7E6248,0.27)
        e(p,134+65*i,324,2,3,0x7E6248,0.27)
    }
    p.canvas.restore()
    blur(p,0)
    path(p,"M 75 304 L 317 304 L 303 316 L 81 311 Z",0xF5F2DF,0,0.14)
    line(p,70,347,322,347,0xF8F5E4,1.3,0.7)
    p.canvas.save()
    p.canvas.translate(196.5,119)
    p.canvas.scale(0.92,0.86)
    p.canvas.translate(-196.5,0)
    r(p,73,287,247,256,0x546B7B,35)
    r(p,76,289,241,251,0xECF0DF,32)
    r(p,79,292,235,245,0x7E96AE,30)
    r(p,82,295,229,239,0x0B2697,28)
    p.canvas.save()
    clip(p,83,296,227,237,27)
    grad(p,83,296,227,237,0x1247D2,0x1628A3,0x121F87)
    radial(p,194,318,158,0x2B8BF4,0x182B9B,0.85)
    grad(p,83,513,227,20,0x2C3CB7,0x3151CC,0x6383E3)
    const xs = [128,244,203,285,166,221,262]
    const ys = [510,457,474,490,495,514,525]
    const radii = [31,34,33,32,31,34,33]
    for (let idx=0;idx<7;idx++) {
        const index = pick(idx === 6,0,idx+1)
        const i = (index)
        const radius = (radii[index])
        let bx = (xs[index])
        let by = (ys[index])
        let alpha = 1.0
        if (index === 0) { bx=ballX;by=ballY;alpha=ballAlpha }
        if (index > 0) { by=by+0.6*Math.sin(6.283185307179586*t/8+i)*seg(t,0.8,1.1)*(1-seg(t,7,7.4)) }
        e(p,bx+3,by+6,radius+1,radius,0x070F4B,0.22*alpha)
        p.canvas.save()
        clip(p,bx-radius,by-radius,radius*2,radius*2,radius)
        grad(p,bx-radius,by-radius,radius*2,radius*2,0xFFE96D,0xF8C82A,0xAD7414,0,true,alpha)
        radial(p,bx-radius*0.32,by-radius*0.48,radius*1.35,0xFFFFAE,0xFFD332,0.84*alpha)
        e(p,bx-radius*0.4,by-radius*0.64,radius*0.16,radius*0.07,0xFFFBD2,0.5*alpha)
        if (index === 0) {
            r(p,bx-24,by-8,21,14,0x27303B,5,alpha)
            r(p,bx+3,by-8,21,14,0x27303B,5,alpha)
            line(p,bx-24,by-9,bx+24,by-9,0x1A2230,2,alpha)
            line(p,bx-3,by-4,bx+3,by-4,0x1A2230,2,alpha)
            line(p,bx-21,by-5,bx-13,by-7,0x8496A4,1.5,alpha*0.65)
            line(p,bx+6,by-5,bx+14,by-7,0x8496A4,1.5,alpha*0.65)
            p.canvas.save()
            p.canvas.translate(bx,by)
            path(p,"M -12 12 Q 0 25 13 11 Q 1 19 -12 12 Z",0x673E1E,0,alpha)
            p.canvas.restore()
        }
        if (index > 0 && index < 3) {
            line(p,bx-15,by-5,bx-8,by-7,0x86661C,2,alpha)
            line(p,bx+8,by-7,bx+15,by-5,0x86661C,2,alpha)
            e(p,bx-18,by+5,6,3,0xF8A160,0.6*alpha)
            e(p,bx+18,by+5,6,3,0xF8A160,0.6*alpha)
            e(p,bx,by+8,8,6,0xA46B20,alpha)
            e(p,bx,by+5,8,4,0xFFDD53,alpha)
        }
        if (index >= 3 && index < 6) {
            e(p,bx-11,by-5,3,5,0x5C481F,alpha)
            e(p,bx+11,by-5,3,5,0x5C481F,alpha)
            e(p,bx,by+10,14,9,0x8E4E22,alpha)
            e(p,bx,by+6,12,4,0xFFFADE,alpha)
            e(p,bx,by+15,6,3,0xF19263,alpha)
        }
        if (index >= 6) {
            e(p,bx-11,by-5,3,4,0x604624,alpha)
            line(p,bx+7,by-5,bx+15,by-5,0x604624,2,alpha)
            e(p,bx,by+10,9,5,0x9E6227,alpha)
        }
        p.canvas.restore()
    }
    // A twisted cable with bright and dark facets; the fingers close around the ball.
    line(p,clawX,301,clawX,clawY-9,0x1D3766,5,0.55)
    for (let idx=0;idx<15;idx++) {
        const i = (idx)
        const cy = 304+(clawY-317)*i/14
        line(p,clawX-3,cy,clawX+3,cy+4,0xD4DFD4,1.6)
        line(p,clawX+3,cy+4,clawX-3,cy+8,0x6E8DAD,1.2)
    }
    p.canvas.save()
    p.canvas.translate(clawX,clawY)
    grad(p,-20,-10,40,17,0x526D81,0xF3F4DD,0x738B9C,2,true)
    line(p,-20,-10,20,-10,0xF8F9E8,1.2)
    for (let idx=0;idx<2;idx++) {
        p.canvas.save()
        p.canvas.scale(pick(idx === 0,1.0,-1.0),1)
        p.canvas.rotate(22*(1-grip),0,0)
        path(p,"M -15 4 L -31 31 L -25 47 L -19 34 L -5 10 Z",0x526D81)
        path(p,"M -15 5 L -27 31 L -23 38 L -18 31 L -7 10 Z",0xD2D9D0)
        path(p,"M -15 5 L -27 31 L -24 32 L -11 7 Z",0xF5F5DC)
        path(p,"M -15 5 L -27 31",0xFCFAE7,0.65,0.75)
        p.canvas.restore()
    }
    path(p,"M -5 5 L -7 32 L -1 41 L 4 30 L 6 5 Z",0xC7D1D0)
    path(p,"M -5 5 L -7 32 L -3 34 L 0 5 Z",0xF6F4DB)
    p.canvas.restore()
    path(p,"M 89 299 L 106 299 L 86 432 L 86 365 Z",0xD3F7FF,0,0.12)
    path(p,"M 296 304 L 307 307 L 307 496 L 301 516 Z",0xA7D2FF,0,0.08)
    p.canvas.restore()
    line(p,87,532,307,532,0xDFE7DA,1.1,0.8)
    p.canvas.restore()
    // The base continues into the lower crop; the prize window remains fully visible.
    p.canvas.save()
    p.canvas.translate(196.5,-33.755)
    p.canvas.scale(1.08,1.145)
    p.canvas.translate(-196.5,0)
    grad(p,49,619,295,155,0x86979F,0xC6CFCA,0x9BA8A9,2,true)
    line(p,56,620,56,771,0xD7E1D8,1,0.5)
    grad(p,62,625,24,140,0xB6C5C4,0xDAE4DA,0xADBEBF,1,true,0.28)
    grad(p,315,625,14,140,0x667C8B,0x91A4AB,0xA3B3B6,1,true,0.28)
    line(p,337,620,337,771,0x344E61,1,0.6)
    r(p,115,646,164,105,0x597B8F,8)
    r(p,119,649,156,99,0xC8D9D6,6)
    r(p,123,653,148,91,0x0C286B,4)
    p.canvas.save()
    clip(p,124,654,146,89,3)
    grad(p,124,654,146,89,0x1238B5,0x244DCF,0x5F84D4)
    radial(p,202,686,81,0x6B9DF2,0x122C8B,0.5)
    path(p,"M 124 654 L 136 664 L 136 730 L 124 743 Z",0x072777)
    path(p,"M 270 654 L 256 664 L 256 730 L 270 743 Z",0x204AB6)
    path(p,"M 136 730 L 256 730 L 270 743 L 124 743 Z",0x91AED9)
    if (ballY > 630) {
        e(p,ballX+3,ballY+25,28,5,0x071B5F,0.35*ballAlpha)
        e(p,ballX,ballY,29,29,0xE8B81B,ballAlpha)
        p.canvas.save()
        clip(p,ballX-29,ballY-29,58,58,29)
        radial(p,ballX-9,ballY-14,44,0xFFF58C,0xE9BE17,0.9*ballAlpha)
        r(p,ballX-23,ballY-7,20,13,0x29303C,5,ballAlpha)
        r(p,ballX+3,ballY-7,20,13,0x29303C,5,ballAlpha)
        line(p,ballX-23,ballY-8,ballX+23,ballY-8,0x202939,1.8,ballAlpha)
        line(p,ballX-3,ballY-3,ballX+3,ballY-3,0x202939,2,ballAlpha)
        p.canvas.save()
        p.canvas.translate(ballX,ballY)
        path(p,"M -12 11 Q 0 25 13 11 Q 1 19 -12 11 Z",0x693F22,0,ballAlpha)
        p.canvas.restore()
        p.canvas.restore()
    }
    p.canvas.restore()
    line(p,122,747,273,747,0xEBF0DB,1.5,0.8)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(196.5,123)
    p.canvas.scale(1.045,0.9)
    p.canvas.translate(-196.5,0)
    path(p,"M 58 558 L 333 558 L 351 585 L 42 585 Z",0xD8DFD9)
    path(p,"M 63 562 L 329 562 L 341 582 L 52 582 Z",0x1B3E99)
    grad(p,43,584,307,35,0xAEBFC3,0x7F929C,0xD6E2DF,3,true)
    line(p,46,586,346,586,0xF5F8ED,1.1)
    for (let idx=0;idx<24;idx++) {
        const i = (idx)
        line(p,59,625+6*i,332,625+6*i,0xFFFFFF,0.45,0.035)
    }
    for (let idx=0;idx<2;idx++) {
        const i = (idx)
        const press = seg(t,2.8,3)*(1-seg(t,3.2,3.45))
        e(p,91+45*i,574,20,6.5,0x17315A,0.7)
        e(p,91+45*i,571+press,18,6,0x465765)
        e(p,91+45*i,569+press*2,17,5,0xD9DDD4)
        e(p,89+45*i,568+press*2,12,2.3,0xFAFBEC,0.9)
    }
    const lever = (clawX-196.5)/75
    e(p,281,575,25,8,0x0F2A52,0.45)
    e(p,281,573,22,8,0x6C899E)
    e(p,281,571,18,6,0xD7E0D2)
    line(p,281,568,281+lever*8,548,0x3A4E5B,10)
    line(p,279,567,279+lever*8,548,0xD3E1D6,3)
    e(p,281+lever*8,542,10,11,0x202C33)
    radial(p,278+lever*8,537,12,0x7C8984,0x1C2D36,0.5)
    p.canvas.restore()
    grain(p,0,0,393,852,0.035)

}

export default function ChromeSmileCrane({autoPlay=false,previewTime}:Props) {
  const {width,height}=useWindowDimensions(),reduced=useReducedMotion();
  const latest=useSharedValue(0),clock=useSharedValue(0),epoch=useSharedValue(-1),lastTouch=useSharedValue(-100),manual=useSharedValue(0),origin=useSharedValue(0),dragging=useSharedValue(false),velocity=useSharedValue(0),tx=useSharedValue(260),ty=useSharedValue(426.0),active=useSharedValue(true);
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
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(3.8);velocity.set(Math.max(-5,Math.min(5,event.velocityX/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts};r(p,0,0,width,height,0x08ACCB);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),260+(tx.get()-260)*strength,426.0+(ty.get()-426.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Chrome Smile Crane":"Chrome Smile Crane preview "+Math.round(previewTime*1000)} accessibilityHint="Drag to guide the claw. Tap to catch a smile." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="dark"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#08ACCB'}});
