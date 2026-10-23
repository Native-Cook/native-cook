import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkColor, type SkImage, type SkPaint } from '@shopify/react-native-skia';

import { ARCS, curve, parameter, segment, tail, type Curve, type Scene } from './motion';
import type { Resources } from './resources';

function color(hex:number,alpha=1):SkColor { 'worklet';return Float32Array.of(((hex>>16)&255)/255,((hex>>8)&255)/255,(hex&255)/255,alpha); }
function image(c:SkCanvas,im:SkImage,rect:{x:number;y:number;width:number;height:number},p:SkPaint|null=null){ 'worklet';c.drawImageRectOptions(im,Skia.XYWHRect(0,0,im.width(),im.height()),rect,FilterMode.Linear,MipmapMode.None,p); }
function blend(c:SkCanvas,images:SkImage[],weights:number[],rect:{x:number;y:number;width:number;height:number}){
  'worklet';c.saveLayer(undefined,rect);const p=Skia.Paint();p.setBlendMode(BlendMode.Plus);
  for(let i=0;i<images.length;i++)if(weights[i]>0){p.setAlphaf(weights[i]);image(c,images[i],rect,p);}c.restore();
}
function path(c:Curve){ 'worklet';return Skia.PathBuilder.Make().moveTo(c.a.x,c.a.y).cubicTo(c.c1.x,c.c1.y,c.c2.x,c.c2.y,c.b.x,c.b.y).detach(); }
function stroke(c:SkCanvas,shape:ReturnType<typeof path>,hex:number,width:number,alpha=1){ 'worklet';const p=Skia.Paint();p.setAntiAlias(true);p.setColor(color(hex,alpha));p.setStyle(PaintStyle.Stroke);p.setStrokeWidth(width);p.setStrokeCap(StrokeCap.Round);c.drawPath(shape,p); }
function lace(c:SkCanvas,s:Scene){
  'worklet';for(let i=0;i<11;i++){
    if(i>0&&s.trims[i]<=0)continue;const cv=i===0?tail(s):curve(i,s.slacks[i]),arc=ARCS[i],trim=i===0?1:s.trims[i],start=i===0?.035:parameter(i,Math.min(trim,1.6/arc.length)),end=i===0?1:parameter(i,Math.max(0,trim-1.6/arc.length));
    const alpha=Skia.Paint();alpha.setAlphaf(s.spanAlpha[i]);c.saveLayer(alpha,Skia.XYWHRect(-12,-12,141,344));
    for(const pt of trim>=.97?[cv.a,cv.b]:[cv.a]){c.save();const mask=Skia.PathBuilder.Make().addOval(Skia.XYWHRect(pt.x-2.4,pt.y-2.4,4.8,4.8)).detach();c.clipPath(mask,ClipOp.Intersect,true);stroke(c,path(cv),0x443126,2.8);c.restore();}
    if(end>start){const front=path(segment(cv,start,end));c.save();c.translate(0,1);stroke(c,front,0,4.8,.17);c.restore();if(i>0){c.save();c.translate(0,.8);stroke(c,front,0,3.5,.14);c.restore();}stroke(c,front,0x564135,2.8);c.save();c.translate(-.35,-.45);stroke(c,front,0xA78763,.65);c.restore();}c.restore();
  }
}
function aglet(c:SkCanvas,s:Scene){
  'worklet';const a=Math.atan2(s.tangent.y,s.tangent.x),co=Math.cos(a),si=Math.sin(a);c.save();c.concat([co,-si,s.tip.x,si,co,s.tip.y,0,0,1]);
  const r=Skia.RRectXY(Skia.XYWHRect(-7,-1.4,7,2.8),.6,.6),p=Skia.Paint();p.setAntiAlias(true);p.setAlphaf(s.tipAlpha);
  p.setShader(Skia.Shader.MakeLinearGradient({x:-4,y:-1.4},{x:-2,y:1.4},[color(0xE4CCA0),color(0xAD9065),color(0x806447)],[0,.5,1],TileMode.Clamp));c.drawRRect(r,p);
  p.setShader(null);p.setColor(color(0x715539,s.tipAlpha));p.setStyle(PaintStyle.Stroke);p.setStrokeWidth(.3);c.drawRRect(r,p);c.restore();
}
/** Cached leather/soles remain still; only eleven continuous lace spans and aglet are rebuilt. */
export function draw(s:Scene,art:Resources|null){
  'worklet';const rec=Skia.PictureRecorder(),c=rec.beginRecording(Skia.XYWHRect(0,0,393,852));
  if(!art)return rec.finishRecordingAsPicture();c.drawPicture(art.paper);
  for(const left of [true,false]){const a=(left?-4:4)*Math.PI/180,co=Math.cos(a),si=Math.sin(a);c.save();c.concat([co,-si,left?123.5:272.5,si,co,308,0,0,1]);if(left)c.scale(-1,1);c.translate(-58.5,-160);
    image(c,art.shadow,Skia.XYWHRect(-46,-40,209,422));blend(c,art.soles,s.soles,Skia.XYWHRect(-4,-4,125,332));blend(c,left?art.left:art.bodies,s.materials,Skia.XYWHRect(0,0,117,320));
    if(!left){lace(c,s);blend(c,art.eyelets,s.materials,Skia.XYWHRect(0,0,117,190));aglet(c,s);}c.restore();}
  const p=Skia.Paint();p.setAntiAlias(true);for(let i=0;i<3;i++){p.setColor(color(i===0?0x50473E:0xC8C0B6));c.drawOval(Skia.XYWHRect(185.7+i*9,491.2,3.6,3.6),p);}return rec.finishRecordingAsPicture();
}
