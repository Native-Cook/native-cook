
import { AlphaType, ColorType, BlurStyle, Canvas, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect,useMemo } from 'react';
import { AppState,Platform,StyleSheet,View,useWindowDimensions } from 'react-native';
import { Gesture,GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue,useFrameCallback,useReducedMotion,useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 10.0;
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
function text(p: Painter,value: string,x: number,y: number,size: number,hex: number,style='regular',align=0,alpha=1) {
  'worklet';reset(p,hex,alpha);const font=p.fonts[style];const sx=style==='condensed'?.8:1;
  const widths=font.getGlyphWidths(font.getGlyphIDs(value));let width=0;for(const item of widths) width+=item;
  const scale=size/100,offset=align===1?width/2:align===2?width:0;
  p.canvas.save();p.canvas.translate(x-offset*scale*sx,y);p.canvas.scale(scale*sx,scale);p.canvas.drawText(value,0,-font.getMetrics().ascent,p.paint,font);p.canvas.restore();
}
function fit(p:Painter,value:string,x:number,y:number,size:number,width:number,hex:number,style='heavy') { 'worklet';const font=p.fonts[style];let measured=0;for(const item of font.getGlyphWidths(font.getGlyphIDs(value)))measured+=item;measured*=size/100*(style==='condensed'?.8:1);p.canvas.save();p.canvas.translate(x,y);p.canvas.scale(width/Math.max(1,measured),1);text(p,value,0,0,size,hex,style,1);p.canvas.restore(); }

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

const PATHS:Record<string,SkPath>={"M -107 -1 Q 0 12 107 -1": Skia.PathBuilder.Make().moveTo(-107,-1).quadTo(0,12,107,-1).detach(),
        "M -107 0 L -84 103 L 84 103 L 107 0": Skia.PathBuilder.Make().moveTo(-107,0).lineTo(-84,103).lineTo(84,103).lineTo(107,0).detach(),
        "M -107 0 L 107 0 L 84 103 L -84 103 Z": Skia.PathBuilder.Make().moveTo(-107,0).lineTo(107,0).lineTo(84,103).lineTo(-84,103).close().detach(),
        "M -107 0 Q 0 -16 107 0": Skia.PathBuilder.Make().moveTo(-107,0).quadTo(0,-16,107,0).detach(),
        "M -107 0 Q 0 13 107 0": Skia.PathBuilder.Make().moveTo(-107,0).quadTo(0,13,107,0).detach(),
        "M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z": Skia.PathBuilder.Make().moveTo(-19,-14).cubicTo(-25,-21,-30,-10,-18,-4).cubicTo(-7,-10,-13,-21,-19,-14).close().detach(),
        "M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z": Skia.PathBuilder.Make().moveTo(-21,-10).cubicTo(-30,-29,-17,-37,-2,-29).cubicTo(15,-38,29,-26,19,-10).lineTo(18,26).lineTo(-19,26).close().detach(),
        "M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z": Skia.PathBuilder.Make().moveTo(-23,-42).lineTo(-5,-42).lineTo(-5,5).quadTo(9,7,9,18).quadTo(8,30,-6,31).lineTo(-30,27).quadTo(-36,21,-29,12).lineTo(-23,5).close().detach(),
        "M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z": Skia.PathBuilder.Make().moveTo(-25,-11).cubicTo(-38,-33,-19,-46,-2,-36).cubicTo(18,-46,36,-27,24,-11).lineTo(23,32).lineTo(-24,32).close().detach(),
        "M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15": Skia.PathBuilder.Make().moveTo(-27,15).lineTo(-27,-14).cubicTo(-27,-53,28,-53,28,-14).lineTo(28,15).detach(),
        "M -28 -13 C -29 -48 27 -48 28 -13": Skia.PathBuilder.Make().moveTo(-28,-13).cubicTo(-29,-48,27,-48,28,-13).detach(),
        "M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z": Skia.PathBuilder.Make().moveTo(-5,-38).lineTo(14,-38).lineTo(14,9).quadTo(31,11,31,22).quadTo(29,34,15,34).lineTo(-9,29).quadTo(-15,22,-8,13).lineTo(-5,9).close().detach(),
        "M 38 -45 L 46 -36 L 46 10 L 38 15 Z": Skia.PathBuilder.Make().moveTo(38,-45).lineTo(46,-36).lineTo(46,10).lineTo(38,15).close().detach(),
        "M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z": Skia.PathBuilder.Make().moveTo(4,-16).lineTo(6,-10).lineTo(13,-10).lineTo(8,-6).lineTo(10,1).lineTo(4,-3).lineTo(-2,1).lineTo(0,-6).lineTo(-5,-10).lineTo(2,-10).close().detach(),
        "M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z": Skia.PathBuilder.Make().moveTo(65.028,217.280).lineTo(64.084,222.000).lineTo(47.092,222.000).lineTo(63.140,146.952).lineTo(80.604,144.592).lineTo(66.444,210.790).quadTo(69.748,213.150,73.170,213.150).quadTo(79.896,213.150,85.029,205.362).quadTo(90.162,197.574,92.994,185.538).quadTo(95.826,173.502,95.826,161.230).quadTo(95.826,149.666,91.283,144.297).quadTo(86.740,138.928,78.598,138.928).quadTo(69.748,138.928,62.727,142.409).quadTo(55.706,145.890,51.694,152.380).quadTo(47.682,158.870,47.682,167.602).quadTo(47.682,171.968,48.508,173.679).quadTo(49.334,175.390,49.334,175.626).quadTo(43.198,175.626,39.953,173.030).quadTo(36.708,170.434,36.708,164.652).quadTo(36.708,156.628,42.726,149.430).quadTo(48.744,142.232,58.833,137.866).quadTo(68.922,133.500,80.604,133.500).quadTo(96.770,133.500,104.440,143.058).quadTo(112.110,152.616,112.110,167.484).quadTo(112.110,180.936,107.567,193.975).quadTo(103.024,207.014,95.472,215.274).quadTo(87.920,223.534,79.424,223.534).quadTo(72.344,223.534,65.028,217.280).close().detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    grad(p,0,0,393,852,0x86ADB0,0x8AB2B4,0xA8C2C2)
    // Scalloped cloud silhouettes sit over a softer diagonal body.
    for (let idx=0;idx<7;idx++) {
        const i=(idx)
        const x = -65+i*79
        const y=435+i*39
        p.canvas.save()
        blur(p,13)
        e(p,x+4,y+54,110,82,0xD9E2DC,0.64)
        e(p,x+27,y+71,107,85,0xEEF0E6,0.74)
        p.canvas.restore()
        blur(p,0)
    }
    for (let idx=0;idx<18;idx++) {
        const i=(idx)
        const x = -25+i*28
        const y=420+i*15+13*Math.sin(i*2.1)
        const radius=24+7*Math.sin(i*1.7+1)
        p.canvas.save()
        blur(p,5)
        e(p,x+7,y+14,radius*1.14,radius*1.1,0xBCCFCB,0.40)
        e(p,x,y,radius,radius,0xEDF0E5,0.83)
        e(p,x-5,y-5,radius*0.76,radius*0.70,0xF8F7EB,0.34)
        p.canvas.restore()
        blur(p,0)
    }
    for (let idx=0;idx<5;idx++) {
        const i=(idx)
        p.canvas.save()
        blur(p,25)
        e(p,-53+124*i,747+21*Math.sin(i*2.7),130,114,0xE5EBE3,0.42)
        p.canvas.restore()
        blur(p,0)
    }
    const pixelX = [48.0, 52.2, 56.4, 60.6, 64.8, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 48.0, 48.0, 48.0, 52.2, 56.4, 60.6, 64.8, 78.3, 82.5, 86.7, 90.9, 95.1, 78.3, 78.3, 78.3, 82.5, 86.7, 90.9, 95.1, 95.1, 95.1, 78.3, 82.5, 86.7, 90.9, 95.1, 108.6, 112.8, 117.0, 121.2, 125.4, 108.6, 108.6, 108.6, 112.8, 117.0, 121.2, 125.4, 125.4, 125.4, 108.6, 112.8, 117.0, 121.2, 125.4, 138.9, 143.1, 147.3, 151.5, 155.7, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 138.9, 138.9, 138.9, 143.1, 147.3, 151.5, 155.7, 169.2, 186.0, 169.2, 173.4, 186.0, 169.2, 173.4, 186.0, 169.2, 177.6, 186.0, 169.2, 181.8, 186.0, 169.2, 181.8, 186.0, 169.2, 186.0, 199.5, 203.7, 207.9, 212.1, 216.3, 207.9, 207.9, 207.9, 207.9, 207.9, 207.9, 229.8, 234.0, 238.2, 242.4, 246.6, 238.2, 238.2, 238.2, 238.2, 238.2, 229.8, 234.0, 238.2, 242.4, 246.6, 264.3, 268.5, 272.7, 260.1, 276.9, 260.1, 276.9, 260.1, 264.3, 268.5, 272.7, 276.9, 260.1, 276.9, 260.1, 276.9, 260.1, 276.9, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 290.4, 294.6, 298.8, 303.0, 307.2, 320.7, 324.9, 329.1, 333.3, 337.5, 320.7, 320.7, 320.7, 324.9, 329.1, 333.3, 337.5, 337.5, 337.5, 320.7, 324.9, 329.1, 333.3, 337.5]
    const pixelY = [94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 98.8, 98.8, 98.8, 103.6, 103.6, 103.6, 108.4, 108.4, 108.4, 113.2, 113.2, 113.2, 118.0, 118.0, 118.0, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 98.8, 98.8, 103.6, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 113.2, 118.0, 118.0, 122.8, 122.8, 94.0, 98.8, 103.6, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8, 94.0, 94.0, 94.0, 94.0, 94.0, 98.8, 103.6, 108.4, 108.4, 108.4, 108.4, 108.4, 113.2, 118.0, 122.8, 122.8, 122.8, 122.8, 122.8]
    for (let idx=0;idx<167;idx++) {
        r(p,(pixelX[idx]),(pixelY[idx]),3.8,4.2,0xF0A0CC)
    }
    // Lobster capital D as one cached outline keeps the decorative initial identical across ports.
    path(p,"M 65.028 217.280 L 64.084 222.000 L 47.092 222.000 L 63.140 146.952 L 80.604 144.592 L 66.444 210.790 Q 69.748 213.150 73.170 213.150 Q 79.896 213.150 85.029 205.362 Q 90.162 197.574 92.994 185.538 Q 95.826 173.502 95.826 161.230 Q 95.826 149.666 91.283 144.297 Q 86.740 138.928 78.598 138.928 Q 69.748 138.928 62.727 142.409 Q 55.706 145.890 51.694 152.380 Q 47.682 158.870 47.682 167.602 Q 47.682 171.968 48.508 173.679 Q 49.334 175.390 49.334 175.626 Q 43.198 175.626 39.953 173.030 Q 36.708 170.434 36.708 164.652 Q 36.708 156.628 42.726 149.430 Q 48.744 142.232 58.833 137.866 Q 68.922 133.500 80.604 133.500 Q 96.770 133.500 104.440 143.058 Q 112.110 152.616 112.110 167.484 Q 112.110 180.936 107.567 193.975 Q 103.024 207.014 95.472 215.274 Q 87.920 223.534 79.424 223.534 Q 72.344 223.534 65.028 217.280 Z",0xF0A0CC)
    fit(p,"ESIGNERS",236,120,92,237,0xF0A0CC,"condensed")
    p.canvas.save()
    p.canvas.translate(107,135)
    p.canvas.rotate(-8,0,0)
    e(p,0,0,19,9.5,0xF4EFD6)
    text(p,"for",0,-8,13,0x799C9D,"italic",1)
    p.canvas.restore()
    let basketX=196.5+29*Math.sin(6.283185307179586*t/10+0.8)*seg(t,0.65,1)*(1-seg(t,8.4,9.2))
    if (touching) { basketX=Math.min(257,Math.max(136,tx)) }
    let shake=0.0
    for (let idx=0;idx<6;idx++) {
        const age=t-(1.95+(idx)*1.15)
        if (age >= 0) { shake=shake+2.3*Math.exp(-9*age)*Math.sin(19*age) }
    }
    const basketY=651+shake
    // Back wire and handles sit behind the falling objects.
    e(p,basketX,764,109,9,0x506B71,0.13)
    e(p,basketX,757,87,3,0x4A5964,0.19)
    p.canvas.save()
    p.canvas.translate(basketX,basketY)
    p.canvas.scale(0.86,1)
    path(p,"M -107 0 L 107 0 L 84 103 L -84 103 Z",0xD99CB7,2)
    line(p,-96,-7,-9,-1,0xA62F67,4)
    line(p,9,-1,96,-7,0xB92A61,4)
    line(p,-95,-8,-10,-2,0xF17CA7,1.1)
    line(p,10,-2,95,-8,0xF69EB9,1.1)
    path(p,"M -107 0 Q 0 -16 107 0",0xF8CBDD,3)
    p.canvas.restore()
    const xs=[201,160,239,192,221,258]
    const ys=[694,704,708,717,712,704]
    const angles=[0,-14,3,0,5,5]
    for (let idx=0;idx<6;idx++) {
        const i=(idx)
        const begin=0.85+i*1.15
        const raw=Math.min(1,Math.max(0,(t-begin)/1.1))
        const landed=Math.max(0,t-begin-1.1)
        const exit=seg(t,8.4+i*0.025,9.1+i*0.025)
        const alpha=seg(t,begin,begin+0.12)*(1-exit)
        const targetY=(ys[idx])
        const y=300+(targetY-300)*raw*raw-14*Math.exp(-11*landed)*Math.abs(Math.sin(15*landed))-110*exit
        const x=(xs[idx])+(basketX-196.5)*raw*raw+18*Math.sin(raw*3.141592653589793)*(pick(idx % 2 === 0,1.0,-1.0))
        p.canvas.save()
        p.canvas.translate(x,y)
        p.canvas.rotate((angles[idx])+(1-raw)*pick(idx % 2 === 0,-14.0,14.0)-13*exit+5*Math.exp(-10*landed)*Math.sin(17*landed),0,0)
        p.canvas.scale(0.9+0.1*raw,0.9+0.1*raw)
        e(p,4,34,35,6,0x587073,0.13*alpha)
        if (idx === 0) {
            grad(p,-38,-50,76,65,0xF0EAD1,0xD9D2B8,0xB9BAA5,6,false,alpha)
            path(p,"M 38 -45 L 46 -36 L 46 10 L 38 15 Z",0xA0AA9B,0,alpha)
            r(p,-32,-44,62,47,0x898E80,5,alpha)
            grad(p,-29,-41,56,41,0x223B46,0x64858A,0x263E47,5,false,alpha)
            e(p,-10,-31,16,5,0x99B5AD,0.23*alpha)
            text(p,"hello.",0,-28,14,0xF7F2D7,"italic",1,alpha)
            r(p,-22,15,44,9,0xB9BEAC,2,alpha)
            grad(p,-37,25,81,17,0xE5E4CE,0xD1D4C4,0xADB9AA,2,false,alpha)
            for (let row=0;row<3;row++) {
                for (let col=0;col<11;col++) {
        r(p,-31+(col)*6.5,28+(row)*3,5,2,0x798F87,0.4,0.65*alpha)
    }
            }
            e(p,29,8,1.5,1.5,0x79A65E,alpha)
        }
        if (idx === 1) {
            r(p,-28,-42,57,80,0xA8B0AF,2,alpha)
            grad(p,-31,-45,57,79,0x234FD6,0x1C41BA,0x142C91,3,true,alpha)
            line(p,-26,-41,-26,29,0x90A0EA,1,0.75*alpha)
            text(p,"Thoughts",-23,-30,11,0xF4EAD5,"italic",0,alpha)
            text(p,"& tiny",-21,-11,12,0xF4EAD5,"italic",0,alpha)
            text(p,"plans",-20,5,14,0xF4EAD5,"italic",0,alpha)
            e(p,6,24,5,5,0xF6DC55,alpha)
        }
        if (idx === 2) {
            path(p,"M -23 -42 L -5 -42 L -5 5 Q 9 7 9 18 Q 8 30 -6 31 L -30 27 Q -36 21 -29 12 L -23 5 Z",0xECEBD9,0,alpha)
            path(p,"M -5 -38 L 14 -38 L 14 9 Q 31 11 31 22 Q 29 34 15 34 L -9 29 Q -15 22 -8 13 L -5 9 Z",0x28A65E,0,alpha)
            line(p,-23,-31,-7,-31,0xD8D7C8,3,alpha)
            line(p,-5,-27,12,-27,0xEAEADB,4,alpha)
            path(p,"M -19 -14 C -25 -21 -30 -10 -18 -4 C -7 -10 -13 -21 -19 -14 Z",0xD94E4C,0,alpha)
            path(p,"M 4 -16 L 6 -10 L 13 -10 L 8 -6 L 10 1 L 4 -3 L -2 1 L 0 -6 L -5 -10 L 2 -10 Z",0xE2D94D,0,alpha)
        }
        if (idx === 3) {
            e(p,19,0,15,19,0x2B57AD,alpha)
            e(p,19,0,9,12,0xEFEBD7,alpha)
            grad(p,-23,-22,44,48,0xE7EEDB,0xFFF4DA,0xCACBB5,6,false,alpha)
            for (let col=0;col<5;col++) {
        r(p,-19+(col)*8,-19,3.5,40,0x244AAB,1,alpha)
    }
            e(p,-1,-21,22,7,0xE8B4A0,alpha)
            e(p,-1,-21,17,4,0x8B332E,alpha)
            e(p,-5,-23,10,1.5,0xC57958,alpha)
        }
        if (idx === 4) {
            path(p,"M -25 -11 C -38 -33 -19 -46 -2 -36 C 18 -46 36 -27 24 -11 L 23 32 L -24 32 Z",0xAC602A,0,alpha)
            path(p,"M -21 -10 C -30 -29 -17 -37 -2 -29 C 15 -38 29 -26 19 -10 L 18 26 L -19 26 Z",0xF1C46A,0,alpha)
            e(p,-4,-2,16,19,0xFFE1A1,0.8*alpha)
            for (let col=0;col<7;col++) {
                const j=(col)
                e(p,-14+4.5*j,-12+23*Math.abs(Math.sin(j*4.3)),1,1.3,0xB37B36,0.55*alpha)
            }
            r(p,-10,-7,18,13,0xF9DC5C,2,alpha)
            line(p,-9,-6,6,-6,0xFFF1A4,1,alpha)
        }
        if (idx === 5) {
            path(p,"M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0x731D35,12,alpha)
            path(p,"M -27 15 L -27 -14 C -27 -53 28 -53 28 -14 L 28 15",0xB92B4B,6,alpha)
            path(p,"M -28 -13 C -29 -48 27 -48 28 -13",0xE27587,1.5,0.8*alpha)
            e(p,-27,13,11,18,0xA82745,alpha)
            e(p,-27,13,7,12,0x5B112C,alpha)
            e(p,-29,9,4,7,0xE06478,0.6*alpha)
            e(p,27,13,11,18,0xA82745,alpha)
            e(p,27,13,7,12,0x5B112C,alpha)
            e(p,25,9,4,7,0xE06478,0.6*alpha)
            r(p,-30,0,5,28,0xF28B95,2,0.45*alpha)
        }
        p.canvas.restore()
    }
    // Front wires occlude the packed objects instead of sitting behind them.
    p.canvas.save()
    p.canvas.translate(basketX,basketY)
    p.canvas.scale(0.86,1)
    path(p,"M -107 0 L -84 103 L 84 103 L 107 0",0xD99CB7,3)
    path(p,"M -107 0 L -84 103 L 84 103 L 107 0",0xFBC9DD,1.1)
    for (let idx=0;idx<12;idx++) {
        const x = -98+(idx)*17.8
        line(p,x+1.7,6,x*0.8+1.7,101,0x7C4169,3,0.13)
        line(p,x,4,x*0.8,99,0xE3A5C1,1.8)
        line(p,x-0.8,4,x*0.8-0.8,99,0xF9D5E0,0.7)
    }
    for (let idx=0;idx<4;idx++) {
        const y=21+(idx)*24
        const w=107-y*0.225
        line(p,-w,y,w,y,0xE2A0BB,1.8)
        line(p,-w,y-1,w,y-1,0xFBD4E0,0.7)
    }
    path(p,"M -107 0 Q 0 13 107 0",0xD692B2,3)
    path(p,"M -107 -1 Q 0 12 107 -1",0xFFD8E6,1)
    p.canvas.restore()
    grain(p,0,0,393,852,0.08)

}

export default function CloudBasketEssentials({autoPlay=false,previewTime}:Props) {
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
    'worklet';if(previewTime!==undefined)return Math.max(0,previewTime)%LOOP;if(reduced)return 7.9;
    const age=Math.max(0,now-touched);
    if(touched>=0){if(isDragging)return value;if(age<2)return Math.min(LOOP-.001,Math.max(0,value+speed*age*Math.exp(-8*age)));if(age<2.4)return value*(1-smooth((age-2)/.4));return autoPlay?(age-2.4)%LOOP:0;}
    return autoPlay&&started>=0?Math.max(0,now-started)%LOOP:0;
  }
  const pan=Gesture.Pan().minDistance(0).enabled(!reduced)
    .onBegin(event=>{origin.set(currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()));manual.set(origin.get());clock.set(latest.get());dragging.set(true);lastTouch.set(clock.get());const scale=Math.min(width/393,height/852);tx.set(event.x/scale);ty.set(event.y/scale);})
    .onUpdate(event=>{const scale=Math.min(width/393,height/852);manual.set(Math.min(LOOP-.001,Math.max(0,origin.get()+event.translationX/scale/90)));tx.set(event.x/scale);ty.set(event.y/scale);lastTouch.set(clock.get());})
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(7.9);velocity.set(Math.max(-5,Math.min(5,event.velocityX/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts};r(p,0,0,width,height,0x89ACB0);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),196.5+(tx.get()-196.5)*strength,426.0+(ty.get()-426.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Cloud Basket Essentials":"Cloud Basket Essentials preview "+Math.round(previewTime*1000)} accessibilityHint="Drag the basket to catch your essentials. Tap to pack it." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="dark"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#89ACB0'}});
