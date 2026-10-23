
import { useFont, AlphaType, ColorType, BlurStyle, Canvas, ClipOp, matchFont, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect,useMemo } from 'react';
import { AppState,Platform,StyleSheet,View,useWindowDimensions } from 'react-native';
import { Gesture,GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue,useFrameCallback,useReducedMotion,useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 9.0;
type Props={autoPlay?:boolean;previewTime?:number};

function smooth(value: number) { 'worklet';const u=Math.min(1,Math.max(0,value));return u*u*u*(10+u*(-15+6*u)); }
function seg(t: number,a: number,b: number) { 'worklet';return smooth((t-a)/(b-a)); }
function pick<T>(condition: boolean,a: T,b: T): T { 'worklet';return condition?a:b; }
function color(hex: number,alpha=1) { 'worklet';return Skia.Color(`rgba(${(hex>>16)&255},${(hex>>8)&255},${hex&255},${Math.min(1,Math.max(0,alpha))})`); }
type Painter={canvas: SkCanvas;paint: SkPaint;fonts: Record<string,SkFont>;poster?:SkFont|null;blurFilter?:ReturnType<typeof Skia.MaskFilter.MakeBlur>|null};
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
function fit(p:Painter,value:string,x:number,y:number,size:number,width:number,hex:number,style='heavy') { 'worklet';const font=p.fonts[style];let measured=0;for(const item of font.getGlyphWidths(font.getGlyphIDs(value)))measured+=item;measured*=size/100*(style==='condensed'?.8:1);p.canvas.save();p.canvas.translate(x,y);p.canvas.scale(width/Math.max(1,measured),1);text(p,value,0,0,size,hex,style,1);p.canvas.restore(); }
function layerAlpha(p:Painter,alpha:number) { 'worklet';reset(p,0xffffff);p.paint.setAlphaf(Math.min(1,Math.max(0,alpha)));p.canvas.saveLayer(p.paint); }

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

function sticker(p:Painter,x:number,y:number,width:number) {
  'worklet';const font=p.poster;if(!font)return;const value='My Little Loop';let measured=40;
  for(const item of font.getGlyphWidths(font.getGlyphIDs(value)))measured+=item;
  p.canvas.save();p.canvas.translate(x-width/2,y);p.canvas.scale(width/measured,width*.27/170);
  reset(p,0x640b21,.6,20);p.canvas.drawText(value,25,132,p.paint,font);
  reset(p,0xfff0db,1,18);p.canvas.drawText(value,20,125,p.paint,font);
  reset(p,0x170e16,1,10);p.canvas.drawText(value,20,125,p.paint,font);
  reset(p,0xc51221);p.canvas.drawText(value,20,125,p.paint,font);p.canvas.restore();
}

const PATHS:Record<string,SkPath>={"M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z": Skia.PathBuilder.Make().moveTo(-10,-3).lineTo(-21,-12).lineTo(-25,-3).lineTo(-20,7).lineTo(-9,4).close().detach(),
        "M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66": Skia.PathBuilder.Make().moveTo(-116,-48).cubicTo(-118,-88,-76,-119,-42,-94).moveTo(32,-97).cubicTo(71,-128,117,-101,127,-66).detach(),
        "M -131 -9 C -127 51 -63 95 -8 133": Skia.PathBuilder.Make().moveTo(-131,-9).cubicTo(-127,51,-63,95,-8,133).detach(),
        "M -132 -8 Q -112 61 -45 99": Skia.PathBuilder.Make().moveTo(-132,-8).quadTo(-112,61,-45,99).detach(),
        "M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z": Skia.PathBuilder.Make().moveTo(-139,-35).cubicTo(-136,-96,-72,-126,-16,-77).cubicTo(-38,-87,-97,-82,-112,-31).close().detach(),
        "M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11": Skia.PathBuilder.Make().moveTo(-144,-31).lineTo(-69,-31).cubicTo(-88,-72,-28,-72,-45,-31).lineTo(2,-31).lineTo(2,16).cubicTo(-39,-2,-39,55,2,39).lineTo(2,124).moveTo(-70,-127).lineTo(-70,-74).cubicTo(-29,-92,-29,-33,-70,-49).lineTo(-70,11).lineTo(-116,11).cubicTo(-99,52,-155,52,-139,11).detach(),
        "M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9": Skia.PathBuilder.Make().moveTo(-20,-9).lineTo(-20,-34).cubicTo(-20,-66,23,-66,23,-34).lineTo(23,-9).detach(),
        "M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z": Skia.PathBuilder.Make().moveTo(-29,-5).quadTo(-31,-22,-16,-17).quadTo(-1,-12,2,-4).quadTo(11,-22,27,-16).quadTo(38,-10,28,8).lineTo(24,24).lineTo(-20,24).close().detach(),
        "M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21": Skia.PathBuilder.Make().moveTo(-29,0).lineTo(29,0).moveTo(0,-29).lineTo(0,29).moveTo(-21,-21).cubicTo(8,-13,10,12,-21,21).moveTo(21,-21).cubicTo(-8,-13,-10,12,21,21).detach(),
        "M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z": Skia.PathBuilder.Make().moveTo(-34,-6).quadTo(-36,-27,-17,-22).quadTo(-1,-17,2,-9).quadTo(8,-27,29,-21).quadTo(44,-14,33,9).lineTo(28,29).lineTo(-25,29).close().detach(),
        "M -5 -6 Q -1 -10 5 -5": Skia.PathBuilder.Make().moveTo(-5,-6).quadTo(-1,-10,5,-5).detach(),
        "M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z": Skia.PathBuilder.Make().moveTo(-56,-8).cubicTo(-71,-31,-92,-2,-50,27).cubicTo(-13,-1,-34,-30,-47,-10).close().detach(),
        "M -74 -97 Q -31 -115 -5 -75": Skia.PathBuilder.Make().moveTo(-74,-97).quadTo(-31,-115,-5,-75).detach(),
        "M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21": Skia.PathBuilder.Make().moveTo(-83,-85).lineTo(-52,28).lineTo(43,21).lineTo(79,-91).moveTo(-52,28).lineTo(0,-74).lineTo(43,21).moveTo(-52,28).lineTo(0,140).lineTo(43,21).detach(),
        "M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z": Skia.PathBuilder.Make().moveTo(0,-74).cubicTo(-90,-160,-160,-70,-136,0).cubicTo(-118,75,-52,99,0,140).cubicTo(52,100,118,75,136,0).cubicTo(160,-70,88,-160,0,-74).close().detach(),
        "M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z": Skia.PathBuilder.Make().moveTo(0,-74).lineTo(-83,-85).lineTo(-136,0).lineTo(-52,28).lineTo(0,140).lineTo(43,21).lineTo(136,0).lineTo(79,-91).close().detach(),
        "M 0 136 L -102 226 L -265 74": Skia.PathBuilder.Make().moveTo(0,136).lineTo(-102,226).lineTo(-265,74).detach(),
        "M 0 139 L -102 229 L -265 77": Skia.PathBuilder.Make().moveTo(0,139).lineTo(-102,229).lineTo(-265,77).detach(),
        "M 0 141 L -102 231 L -265 79": Skia.PathBuilder.Make().moveTo(0,141).lineTo(-102,231).lineTo(-265,79).detach(),
        "M 0 145 L -102 235 L -265 83": Skia.PathBuilder.Make().moveTo(0,145).lineTo(-102,235).lineTo(-265,83).detach(),
        "M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z": Skia.PathBuilder.Make().moveTo(10,-3).lineTo(21,-12).lineTo(25,-3).lineTo(20,7).lineTo(9,4).close().detach(),
        "M 121 26 Q 77 94 8 131": Skia.PathBuilder.Make().moveTo(121,26).quadTo(77,94,8,131).detach(),
        "M 5 -75 C 57 -132 129 -100 139 -42": Skia.PathBuilder.Make().moveTo(5,-75).cubicTo(57,-132,129,-100,139,-42).detach(),
        "M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z": Skia.PathBuilder.Make().moveTo(85,-108).cubicTo(159,-86,159,-5,106,59).cubicTo(145,-15,131,-65,85,-108).close().detach()};
function path(p: Painter,key: string,hex: number,width=0,alpha=1) { 'worklet';reset(p,hex,alpha,width);const value=PATHS[key];if(value)p.canvas.drawPath(value,p.paint); }
function clipPath(p: Painter,key: string) { 'worklet';const value=PATHS[key];if(value)p.canvas.clipPath(value,ClipOp.Intersect,true); }
function commitHaptic() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

function drawScene(p:Painter,t:number,tx:number,ty:number,touching:boolean) {
  'worklet';
    const open=seg(t,0.9,1.8)*(1-seg(t,6.6,7.6))
    const drift=seg(t,1.8,2.1)*(1-seg(t,5.6,5.9))
    const scroll=310*seg(t,1.8,5.6)*(1-seg(t,5.6,6.6))
    let lensX=196.5+18*Math.sin(6.283185307179586*t/9)*drift
    let lensY=420+95*open+12*Math.sin(12.566370614359172*t/9)*drift
    if (touching) { lensX=Math.min(278,Math.max(114,tx));lensY=Math.min(648,Math.max(320,ty)) }
    const lensScale=0.65+0.35*open
    r(p,0,0,393,852,0xB51027)
    grad(p,24,0,345,852,0xCA1A2B,0xC8172A,0xBE1429)
    line(p,24,0,24,852,0x6A1525,1,0.7)
    line(p,369,0,369,852,0x731527,1,0.7)
    // Small candy hearts frame one slanted, outlined script masthead.
    const hx=[169,196,117,38,49,341,350,300,316]
    const hy=[72,64,118,345,457,405,477,670,692]
    for (let idx=0;idx<9;idx++) {
        const i=(idx)
        p.canvas.save()
        p.canvas.translate((hx[idx]),(hy[idx]))
        p.canvas.rotate(-28+11*i,0,0)
        p.canvas.scale(pick(idx < 3,0.055,0.035),pick(idx < 3,0.055,0.035))
        path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x661729,35,0.35)
        path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF0D8,21)
        path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE95153,5)
        p.canvas.restore()
    }
    p.canvas.save()
    p.canvas.translate(58,88)
    p.canvas.rotate(-28,0,0)
    line(p,0,6,0,40,0xE0B8A4,5)
    p.canvas.scale(0.22,0.22)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xA20B21)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF14D58,0,0.35)
    path(p,"M -56 -8 C -71 -31 -92 -2 -50 27 C -13 -1 -34 -30 -47 -10 Z",0xFFF1D8)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(196.5,123)
    p.canvas.rotate(-10,0,0)
    sticker(p,0,0,375)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(80,236)
    p.canvas.rotate(-28,0,0)
    fit(p,"A LITTLE EVERYDAY",0,-6,13,139,0xFFF0D9,"condensed")
    fit(p,"CLUB FIELD NOTES",0,9,12,122,0xFFF0D9,"condensed")
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(303,231)
    p.canvas.rotate(9,0,0)
    text(p,"OCT.",0,-6,37,0x280D18,"heavy",1)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(332,326)
    p.canvas.rotate(18,0,0)
    e(p,4,47,47,7,0x6F112E,0.5)
    grad(p,-43,-42,86,79,0x8DADFF,0x4F7BE8,0x315BC9,7,true)
    r(p,-37,-37,74,67,0x244EA5,4)
    e(p,-3,-5,29,29,0xF36D3B)
    e(p,-3,-5,23,23,0xE74236)
    e(p,-3,-5,18,18,0xFF8B4E)
    e(p,-3,-5,8,8,0xFADE8B)
    e(p,-3,-5,2,2,0x3A3B72)
    line(p,28,-27,28,4,0xD8E7CD,3)
    line(p,28,4,11,17,0xD8E7CD,3)
    r(p,6,14,12,5,0x9BC0C9,1)
    e(p,-29,27,3,3,0xE9E1AD)
    line(p,-41,39,42,39,0xF07D8B,2)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(48,384)
    p.canvas.rotate(13,0,0)
    p.canvas.scale(0.20,0.20)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x751D58)
    path(p,"M 0 -74 L -83 -85 L -136 0 L -52 28 L 0 140 L 43 21 L 136 0 L 79 -91 Z",0xD657AF,3)
    path(p,"M -83 -85 L -52 28 L 43 21 L 79 -91 M -52 28 L 0 -74 L 43 21 M -52 28 L 0 140 L 43 21",0xF6A8D5,2)
    path(p,"M -74 -97 Q -31 -115 -5 -75",0xFAD5E9,5)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(355,639)
    p.canvas.rotate(11,0,0)
    p.canvas.scale(0.8,0.8)
    e(p,3,6,32,32,0x6A1433,0.35)
    e(p,0,0,30,30,0xEA718C)
    path(p,"M -29 0 L 29 0 M 0 -29 L 0 29 M -21 -21 C 8 -13 10 12 -21 21 M 21 -21 C -8 -13 -10 12 21 21",0x8A3154,2)
    e(p,-12,-15,7,3,0xF7AFB4,0.6)
    p.canvas.restore()
    // A split-colour puzzle heart, a nested candy and a small heart handbag.
    p.canvas.save()
    p.canvas.translate(43,632)
    p.canvas.rotate(-31,0,0)
    p.canvas.scale(0.28,0.28)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEAA9BF)
    p.canvas.save()
    clipPath(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    r(p,-150,-150,150,310,0xA9D5E4)
    path(p,"M -144 -31 L -69 -31 C -88 -72 -28 -72 -45 -31 L 2 -31 L 2 16 C -39 -2 -39 55 2 39 L 2 124 M -70 -127 L -70 -74 C -29 -92 -29 -33 -70 -49 L -70 11 L -116 11 C -99 52 -155 52 -139 11",0x5E889C,3)
    p.canvas.restore()
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF8D7DB,7)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(66,755)
    p.canvas.rotate(-12,0,0)
    p.canvas.scale(0.36,0.36)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xAA1129)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xF6C7BA,7)
    for (let idx=0;idx<4;idx++) {
        const scale=0.8-(idx)*0.17
        p.canvas.save()
        p.canvas.scale(scale,scale)
        path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFE0CB,5)
        p.canvas.restore()
    }
    e(p,-70,-73,10,7,0xFFF0D5,0.7)
    p.canvas.restore()
    p.canvas.save()
    p.canvas.translate(325,753)
    p.canvas.rotate(-8,0,0)
    path(p,"M -20 -9 L -20 -34 C -20 -66 23 -66 23 -34 L 23 -9",0xFFEBD2,5)
    path(p,"M -34 -6 Q -36 -27 -17 -22 Q -1 -17 2 -9 Q 8 -27 29 -21 Q 44 -14 33 9 L 28 29 L -25 29 Z",0xFBE7CE)
    path(p,"M -29 -5 Q -31 -22 -16 -17 Q -1 -12 2 -4 Q 11 -22 27 -16 Q 38 -10 28 8 L 24 24 L -20 24 Z",0xB00D2B)
    e(p,6,3,3,4,0xE1B55D)
    line(p,5,7,3,17,0xEED28E,2)
    e(p,3,17,3,3,0xF3D79A)
    line(p,-26,-4,-24,18,0xE77B87,1.5,0.7)
    p.canvas.restore()
    const candyX=[52,343,298,102]
    const candyY=[477,472,688,697]
    for (let idx=0;idx<4;idx++) {
        p.canvas.save()
        p.canvas.translate((candyX[idx]),(candyY[idx]))
        p.canvas.rotate(-30+(idx)*37,0,0)
        path(p,"M -10 -3 L -21 -12 L -25 -3 L -20 7 L -9 4 Z",0xEB4A5F)
        path(p,"M 10 -3 L 21 -12 L 25 -3 L 20 7 L 9 4 Z",0xAE0E2D)
        e(p,1,2,12,10,0x790E28,0.5)
        e(p,0,0,11,10,0xD9163B)
        e(p,-2,-3,7,5,0xF96075,0.65)
        path(p,"M -5 -6 Q -1 -10 5 -5",0xFFE8D5,1.3,0.8)
        line(p,-19,-6,-13,-1,0xFFACAF,1,0.8)
        p.canvas.restore()
    }
    // A metal dispenser remains visible before the ivory schedule unrolls.
    r(p,123,264,149,376,0x690D23,2,0.6)
    grad(p,126,266,143,368,0x55494A,0xE0D1BE,0x66514D,2,true)
    r(p,132,306,131,320,0x332E2D)
    grad(p,136,309,123,313,0x454342,0x8F8A7D,0x5B5751)
    grad(p,142,314,15,302,0x8B8C80,0xB4B3A0,0x7E8175,0,true,0.5)
    grad(p,246,314,8,302,0x26292A,0xAAA99B,0x474D47,0,true,0.5)
    grad(p,132,271,131,36,0x8E7772,0xE5D5C5,0xBEAAA0,1)
    r(p,144,276,107,25,0xF1E9D7,1)
    text(p,"MY LITTLE LOOP",197,278,9,0x9B2135,"condensed",1)
    text(p,"FIELD SCHEDULE",197,288,10,0x892337,"condensed",1)
    const dates=["19","20","21","22","23","24","25","26","27","28"]
    const first=["OPEN","CONCEPT","PROCESS","COLOUR","LOOP","COVER","STUDIO","SOFT","PAPER","DAWN"]
    const second=["STUDIO","NOTES","FILM","MIX","TEASER","PHOTO","PREVIEW","HOURS","PARTY","CLUB"]
    for (let layer=0;layer<2;layer++) {
        if (layer === 0 || open > 0.0001) {
            if (layer === 0) { p.canvas.save() } else { layerAlpha(p,open) }
            if (layer === 1) {
                p.canvas.translate(lensX,lensY)
                p.canvas.scale(lensScale,lensScale)
                p.canvas.rotate(-5,0,0)
                clipPath(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
                p.canvas.scale(1.7,1.7)
                p.canvas.translate(-lensX,-lensY)
                r(p,0,0,393,852,0xC8172A)
            }
            if (open > 0.0001) {
                r(p,130,310,135,490*open,0x64142A,0,0.25)
                grad(p,133,308,129,490*open,0xF8F6ED,0xFFFDF4,0xE6E4DA)
                p.canvas.save()
                clip(p,134,309,127,Math.max(0.001,488*open))
                for (let idx=0;idx<10;idx++) {
                    const i=(idx)
                    const y=333+i*118-scroll
                    if (y > 180 && y < 820) {
                        line(p,135,y-16,260,y-16,0x463C32,0.9)
                        text(p,dates[idx],139,y-10,12,0xAC253B,"bold")
                        if (idx % 3 === 0) {
                            fit(p,first[idx],200,y+9,24,109,0x25201F,"condensed")
                            fit(p,second[idx],200,y+37,28,109,0x25201F,"condensed")
                        }
                        if (idx % 3 === 1) {
                            fit(p,first[idx],200,y+9,23,109,0x2B211E,"condensed")
                            fit(p,second[idx],201,y+36,25,105,0x2B211E,"italic")
                        }
                        if (idx % 3 === 2) {
                            fit(p,first[idx],200,y+9,23,109,0xAC293C,"condensed")
                            fit(p,second[idx],200,y+35,30,109,0x25201F,"condensed")
                        }
                        text(p,"OCT / CLUB FIELD NOTES",198,y+82,5.3,0x73604D,"mono",1)
                    }
                }
                p.canvas.restore()
                line(p,134,309,134,308+490*open,0xFFF9E2,0.7)
            }
            p.canvas.restore()
        }
    }
    // An articulated metal arm joins the heart without hiding the agenda text.
    layerAlpha(p,open)
    p.canvas.translate(lensX,lensY)
    p.canvas.scale(lensScale,lensScale)
    p.canvas.rotate(-5,0,0)
    path(p,"M 0 145 L -102 235 L -265 83",0x6C1D38,19,0.45)
    path(p,"M 0 141 L -102 231 L -265 79",0x566272,16)
    path(p,"M 0 139 L -102 229 L -265 77",0xCED7CE,11)
    path(p,"M 0 136 L -102 226 L -265 74",0xFFF3DF,3)
    e(p,-102,229,15,15,0x6B7881)
    e(p,-102,228,12,12,0xE6E8D8)
    e(p,-102,228,7,7,0x818B98)
    e(p,-104,225,3,3,0xFFF5DE)
    e(p,-265,77,17,17,0x9EABB0)
    e(p,-265,77,12,12,0xE7E8D5)
    e(p,-265,77,5,5,0x66758A)
    p.canvas.save()
    p.canvas.translate(7,11)
    blur(p,7)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x641A32,22,0.42)
    p.canvas.restore()
    blur(p,0)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0x86877F,22)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xE2DFCA,17)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xB9BDB5,10)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xEDE3DA,3)
    p.canvas.save()
    p.canvas.translate(-1.5,-2)
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFCE6D6,1.5,0.8)
    p.canvas.restore()
    p.canvas.save()
    clipPath(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z")
    radial(p,-40,-55,170,0xFFF5DA,0xFFF5DA,0.12)
    path(p,"M -139 -35 C -136 -96 -72 -126 -16 -77 C -38 -87 -97 -82 -112 -31 Z",0xFFF4E2,0,0.16)
    path(p,"M 85 -108 C 159 -86 159 -5 106 59 C 145 -15 131 -65 85 -108 Z",0xF5D6D9,0,0.19)
    p.canvas.restore()
    e(p,-96+10*open,-82-7*open,8,2,0xFFFCEB,0.7)
    line(p,-97+10*open,-91-7*open,-95+10*open,-73-7*open,0xFFF9E7,1.2,0.65)
    path(p,"M -116 -48 C -118 -88 -76 -119 -42 -94 M 32 -97 C 71 -128 117 -101 127 -66",0xFFF9E8,3.5,0.6+0.25*open)
    path(p,"M 121 26 Q 77 94 8 131",0xE686A1,2,0.8)
    path(p,"M -132 -8 Q -112 61 -45 99",0xA8E1DA,1.5,0.8)
    path(p,"M -131 -9 C -127 51 -63 95 -8 133",0x7A2333,5,0.36)
    path(p,"M 5 -75 C 57 -132 129 -100 139 -42",0xFFF3DD,4.5,0.8)
    const flash=seg(t,0.95,1.15)*(1-seg(t,1.25,1.6))
    path(p,"M 0 -74 C -90 -160 -160 -70 -136 0 C -118 75 -52 99 0 140 C 52 100 118 75 136 0 C 160 -70 88 -160 0 -74 Z",0xFFF9E5,0,0.74*flash)
    p.canvas.restore()
    grain(p,0,0,393,852,0.055)

}

export default function HeartLoupeAgenda({autoPlay=false,previewTime}:Props) {
  const titleFont=useFont(require('./assets/lobster.ttf'),100);
  const {width,height}=useWindowDimensions(),reduced=useReducedMotion();
  const latest=useSharedValue(0),clock=useSharedValue(0),epoch=useSharedValue(-1),lastTouch=useSharedValue(-100),manual=useSharedValue(0),origin=useSharedValue(0),dragging=useSharedValue(false),velocity=useSharedValue(0),tx=useSharedValue(196.5),ty=useSharedValue(420.0),active=useSharedValue(true);
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
    'worklet';if(previewTime!==undefined)return Math.max(0,previewTime)%LOOP;if(reduced)return 4.5;
    const age=Math.max(0,now-touched);
    if(touched>=0){if(isDragging)return value;if(age<2)return Math.min(LOOP-.001,Math.max(0,value+speed*age*Math.exp(-8*age)));if(age<2.4)return value*(1-smooth((age-2)/.4));return autoPlay?(age-2.4)%LOOP:0;}
    return autoPlay&&started>=0?Math.max(0,now-started)%LOOP:0;
  }
  const pan=Gesture.Pan().minDistance(0).enabled(!reduced)
    .onBegin(event=>{origin.set(currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()));manual.set(origin.get());clock.set(latest.get());dragging.set(true);lastTouch.set(clock.get());const scale=Math.min(width/393,height/852);tx.set(event.x/scale);ty.set(event.y/scale);})
    .onUpdate(event=>{const scale=Math.min(width/393,height/852);manual.set(Math.min(LOOP-.001,Math.max(0,origin.get()+-event.translationY/scale/90)));tx.set(event.x/scale);ty.set(event.y/scale);lastTouch.set(clock.get());})
    .onFinalize(event=>{if(Math.abs(event.translationX)+Math.abs(event.translationY)<5)manual.set(4.5);velocity.set(Math.max(-5,Math.min(5,-event.velocityY/Math.min(width/393,height/852)/90)));dragging.set(false);lastTouch.set(clock.get());scheduleOnRN(commitHaptic);});
  const picture=useDerivedValue(()=>{
    const recorder=Skia.PictureRecorder(),canvas=recorder.beginRecording(Skia.XYWHRect(0,0,width,height)),paint=Skia.Paint();
    const p:Painter={canvas,paint,fonts,poster:titleFont};r(p,0,0,width,height,0xC71D35);
    const scale=Math.min(width/393,height/852);canvas.translate((width-393*scale)/2,(height-852*scale)/2);canvas.scale(scale,scale);
    const strength=reduced||previewTime!==undefined?0:dragging.get()?1:1-smooth((clock.get()-lastTouch.get()-2)/.4);
    drawScene(p,currentTime(clock.get(),epoch.get(),lastTouch.get(),manual.get(),velocity.get(),dragging.get()),196.5+(tx.get()-196.5)*strength,420.0+(ty.get()-420.0)*strength,strength>0);return recorder.finishRecordingAsPicture();
  },[width,height,fonts,autoPlay,previewTime,reduced,titleFont]);
  function accessibleMove(direction:number){const now=latest.get();clock.set(now);manual.set(Math.max(0,Math.min(LOOP-.001,manual.get()+direction)));lastTouch.set(now);commitHaptic();}
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityRole="adjustable" accessibilityLabel={previewTime===undefined?"Heart Loupe Agenda":"Heart Loupe Agenda preview "+Math.round(previewTime*1000)} accessibilityHint="Drag the glass heart to look closer. Swipe upward to explore the schedule." accessibilityActions={[{name:'increment',label:'Next phase'},{name:'decrement',label:'Previous phase'},{name:'activate',label:'Preview effect'}]} onAccessibilityAction={event=>accessibleMove(event.nativeEvent.actionName==='decrement'?-1:1)}><StatusBar style="light"/><Canvas style={StyleSheet.absoluteFill}><Picture picture={picture}/></Canvas></View></GestureDetector>;
}
const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#C71D35'}});
