import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, StrokeJoin, type SkFont, type SkImage } from '@shopify/react-native-skia';
import type { Scene } from './motion';
import type { BaseResources } from './resources';

export type Fonts={title:SkFont;mode:SkFont;small:SkFont;label:SkFont;number:SkFont;degree:SkFont;button:SkFont;hint:SkFont};
function color(hex:number,alpha=1){'worklet';return Float32Array.of((hex>>16&255)/255,(hex>>8&255)/255,(hex&255)/255,alpha);}
/** All artwork is an ordinary-sRGB image; Canvas only composites two exact cached results. */
export function draw(scene:Scene|null,art:BaseResources|null,fonts:Fonts,height:number,bottom:number,ready:boolean){'worklet';
  const r=Skia.PictureRecorder(),c=r.beginRecording(Skia.XYWHRect(0,0,393,height)),paint=Skia.Paint();paint.setAntiAlias(true);
  const fill=(hex:number,alpha=1)=>{paint.setShader(null);paint.setBlendMode(BlendMode.SrcOver);paint.setStyle(PaintStyle.Fill);paint.setColor(color(hex,alpha));};
  const stroke=(hex:number,width=1,alpha=1)=>{fill(hex,alpha);paint.setStyle(PaintStyle.Stroke);paint.setStrokeWidth(width);paint.setStrokeCap(StrokeCap.Round);paint.setStrokeJoin(StrokeJoin.Round);};
  const line=(x:number,y:number,xx:number,yy:number,hex:number,width=1,alpha=1)=>{stroke(hex,width,alpha);c.drawLine(x,y,xx,yy,paint);};
  const image=(bitmap:SkImage,x:number,y:number,w:number,h:number,alpha=1)=>{fill(0xffffff,alpha);c.drawImageRectOptions(bitmap,Skia.XYWHRect(0,0,bitmap.width(),bitmap.height()),Skia.XYWHRect(x,y,w,h),FilterMode.Linear,MipmapMode.None,paint);};
  const text=(value:string,x:number,y:number,font:SkFont,hex:number,align=0)=>{fill(hex);const glyphs=font.getGlyphIDs(value),widths=font.getGlyphWidths(glyphs);let width=0;for(let i=0;i<widths.length;i++)width+=widths[i];const metrics=font.getMetrics();c.drawText(value,x-(align===1?width/2:align===2?width:0),y-(metrics.ascent+metrics.descent)/2,paint,font);};
  fill(0x171918);c.drawRect(Skia.XYWHRect(0,0,393,height),paint);
  if(art&&scene){const p=scene.params,a=scene.appearance;image(art.frame,0,105,393,462);const rect=Skia.XYWHRect(29,136,335,384);c.save();c.clipRRect(Skia.RRectXY(rect,10,10),ClipOp.Intersect,true);image(art.canonical,29,136,335,384);
    if(p.q>0){const roi=Skia.XYWHRect(59+p.roiX,178+p.roiY,275,300);c.save();c.clipRect(roi,ClipOp.Intersect,false);c.saveLayer(paint,roi);image(scene.pair.a.image,29,136,335,384);image(scene.pair.b.image,29,136,335,384,scene.pair.mix);fill(0xffffff);paint.setBlendMode(BlendMode.DstIn);c.drawImageRectOptions(art.mask,Skia.XYWHRect(0,0,art.mask.width(),art.mask.height()),roi,FilterMode.Linear,MipmapMode.None,paint);c.restore();c.restore();image(art.canonical,29,136,335,384,scene.compare);}c.restore();
    stroke(0xD8E68B,1,a.bracketAlpha);
    const corners=[[59,178,1,1],[334,178,-1,1],[59,478,1,-1],[334,478,-1,-1]];
    for(let i=0;i<4;i++){const v=corners[i],x=v[0]+p.roiX,y=v[1]+p.roiY,sx=v[2],sy=v[3],path=Skia.PathBuilder.Make();path.moveTo(x+sx*6,y).lineTo(x+sx*3,y).quadTo(x,y,x,y+sy*3).lineTo(x,y+sy*6);c.drawPath(path.detach(),paint);}
    const low=24+345*p.low-a.anticipation+a.lowOffset,high=24+345*p.high+a.anticipation+a.highOffset;
    fill(0x484E43);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24,629,345,4),2,2),paint);fill(0xCDD979);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(low,629,Math.max(0,high-low),4),2,2),paint);
    const radius=10*a.handleScale;for(const x of [low,high]){fill(0,.30);c.drawCircle(x,632.5,radius,paint);fill(0xD8E68B);c.drawCircle(x,631,radius,paint);stroke(0xffffff,1,.16);c.drawCircle(x,631,radius-.5,paint);line(x,628,x,634,0x374022,1.2);}
    fill(0x242724);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24,670,345,60),12,12),paint);stroke(0x484E43,1);c.drawCircle(329,700,21,paint);
    for(let i=0;i<8;i++){const theta=i*Math.PI/4;line(329+Math.cos(theta)*17,700+Math.sin(theta)*17,329+Math.cos(theta)*19,700+Math.sin(theta)*19,0xffffff,1,.2);}
    const theta=p.angle*Math.PI/180,dx=Math.cos(theta),dy=Math.sin(theta),arrow=Skia.PathBuilder.Make();arrow.moveTo(329-11*dx,700-11*dy).lineTo(329+11*dx,700+11*dy).moveTo(329+7*dx-3*dy,700+7*dy+3*dx).lineTo(329+11*dx,700+11*dy).lineTo(329+7*dx+3*dy,700+7*dy-3*dx);stroke(0xD8E68B,1.4);c.drawPath(arrow.detach(),paint);
    text(`${Math.round(p.low*100)}–${Math.round(p.high*100)}%`,369,601,fonts.number,0x9EA394,2);text(`${Math.round(p.angle)}°`,40,713.5,fonts.degree,0xF0EEDC);
  }
  text('Luma Run',196.5,86,fonts.title,0xF0EEDC,1);line(46,78,38,86,0xF0EEDC,1.8);line(38,86,46,94,0xF0EEDC,1.8);
  if(scene?.kept){line(344,86,349,91,0xF0EEDC,1.6);line(349,91,358,80,0xF0EEDC,1.6);}else{stroke(0xF0EEDC,1.4);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(343,82,16,12),3,3),paint);line(351,76,351,89,0xF0EEDC,1.4);line(347,85,351,89,0xF0EEDC,1.4);line(355,85,351,89,0xF0EEDC,1.4);}
  text('Pixel sorting',24,554,fonts.mode,0xF0EEDC);text(ready?'Bright runs · preview ready':'Preparing local preview…',24,574.5,fonts.small,0x9EA394);
  fill(0x242724);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(280,537,89,34),17,17),paint);stroke(0x484E43,.8);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(280,537,89,34),17,17),paint);text('Original',324.5,554,fonts.small,0xF0EEDC,1);
  text('Luma window',24,601,fonts.label,0xF0EEDC);text('Direction',40,691.5,fonts.label,0x9EA394);
  fill(0xD8E68B);c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24,737-bottom,345,56),15,15),paint);text(scene?.kept?'Edit kept':'Keep this edit',scene?.kept?203.5:196.5,765-bottom,fonts.button,0x20251A,1);if(scene?.kept){line(158,764-bottom,162,768-bottom,0x20251A,1.4);line(162,768-bottom,169,761-bottom,0x20251A,1.4);}text('All changes stay on this device',196.5,809.5-bottom,fonts.hint,0x9EA394,1);
  return r.finishRecordingAsPicture();
}
