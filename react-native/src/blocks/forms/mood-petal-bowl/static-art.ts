import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkColor, type SkImage, type SkPath } from '@shopify/react-native-skia';

function color(hex:number,alpha=1):SkColor{return Float32Array.of(((hex>>16)&255)/255,((hex>>8)&255)/255,(hex&255)/255,alpha);}
function image(c:SkCanvas,im:SkImage,x:number,y:number,w:number,h:number){c.drawImageRectOptions(im,Skia.XYWHRect(0,0,im.width(),im.height()),Skia.XYWHRect(x,y,w,h),FilterMode.Linear,MipmapMode.None);}
/** Static art is recorded once after decode; none of these paths is rebuilt per frame. */
export function makeStaticArt(paper:SkImage,header:SkImage,softStrip:SkImage,disk:SkPath){
  const backgroundRecorder=Skia.PictureRecorder(),c=backgroundRecorder.beginRecording(Skia.XYWHRect(0,0,393,1024)),p=Skia.Paint();p.setAntiAlias(true);
  image(c,paper,0,0,393,1024);image(c,header,0,0,393,128);
  p.setStyle(PaintStyle.Stroke);p.setStrokeWidth(.7);p.setColor(color(0x827F77,.18));p.setPathEffect(Skia.PathEffect.MakeDash([3,4],0));c.drawLine(0,682,393,682,p);p.setPathEffect(null);
  const trend=Skia.PathBuilder.Make().moveTo(0,686).cubicTo(18,666,28,644,43,660).cubicTo(58,676,80,712,104,712).lineTo(393,712).detach();p.setStrokeWidth(1.2);p.setColor(color(0x191A17));c.drawPath(trend,p);p.setStyle(PaintStyle.Fill);
  const guard=Skia.XYWHRect(9,111,375,345);c.save();c.clipRect(guard,ClipOp.Intersect,true);c.saveLayer(undefined,guard);p.setColor(color(0xE9E0D4,.44));c.drawRect(guard,p);
  const mask=Skia.Paint();mask.setBlendMode(BlendMode.DstIn);c.saveLayer(mask,guard);c.clipPath(disk,ClipOp.Intersect,true);image(c,softStrip,9,111,375,345);c.restore();c.restore();c.restore();
  const background=backgroundRecorder.finishRecordingAsPicture();

  const rimRecorder=Skia.PictureRecorder(),r=rimRecorder.beginRecording(Skia.XYWHRect(9,111,375,345)),ink=Skia.Paint();ink.setAntiAlias(true);ink.setStyle(PaintStyle.Stroke);ink.setStrokeWidth(.8);ink.setColor(color(0xFCF8F0,.30));
  const lower=Skia.PathBuilder.Make().moveTo(43.5,273).cubicTo(43.5,357.499,112.001,426,196.5,426).cubicTo(280.999,426,349.5,357.499,349.5,273).detach();r.drawPath(lower,ink);
  const lip=Skia.PathBuilder.Make().moveTo(43.5,273).lineTo(349.5,273).detach();ink.setColor(color(0xFCF8F0,.40));ink.setStrokeWidth(1.2);ink.setImageFilter(Skia.ImageFilter.MakeBlur(.6,.6,TileMode.Decal));r.drawPath(lip,ink);
  const rim=rimRecorder.finishRecordingAsPicture();

  const footerRecorder=Skia.PictureRecorder(),f=footerRecorder.beginRecording(Skia.XYWHRect(0,0,393,852)),glyph=Skia.Paint();glyph.setAntiAlias(true);glyph.setColor(color(0x1D1E19));f.drawRRect(Skia.RRectXY(Skia.XYWHRect(24,754,345,62),31,31),glyph);
  glyph.setColor(color(0x10A97B));glyph.setStyle(PaintStyle.Stroke);glyph.setStrokeCap(StrokeCap.Round);glyph.setStrokeWidth(2);f.drawLine(73,785,91,785,glyph);f.drawLine(82,776,82,794,glyph);
  glyph.setColor(color(0xF3F1E7));glyph.setStrokeWidth(1.5);f.drawLine(150,781,168,781,glyph);f.drawLine(150,789,168,789,glyph);glyph.setStyle(PaintStyle.Fill);f.drawCircle(156,781,2.2,glyph);f.drawCircle(162,789,2.2,glyph);f.drawCircle(232,780.5,2.5,glyph);f.drawCircle(240,781.2,2,glyph);
  f.save();f.translate(224,773);const busts=Skia.PathBuilder.Make().moveTo(2,19).cubicTo(2,11.6,14,11.6,14,19).close().moveTo(13.8,18).cubicTo(13,12,22,12,22,18).close().detach();f.drawPath(busts,glyph);f.restore();
  glyph.setStyle(PaintStyle.Stroke);const xs=[307,311,315,319],heights=[13,20,16,7];for(let i=0;i<4;i++)f.drawLine(xs[i],794.5,xs[i],794.5-heights[i],glyph);
  return {background,rim,footer:footerRecorder.finishRecordingAsPicture()};
}
