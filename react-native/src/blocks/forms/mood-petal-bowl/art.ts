import { BlendMode, ClipOp, FilterMode, MipmapMode, Skia, type SkCanvas, type SkColor, type SkImage, type SkPaint } from '@shopify/react-native-skia';

import { layouts, painterOrder, type Scene } from './motion';
import type { Resources } from './resources';

function color(hex: number, alpha=1): SkColor { 'worklet';return Float32Array.of(((hex>>16)&255)/255,((hex>>8)&255)/255,(hex&255)/255,alpha); }
function image(c: SkCanvas, im: SkImage, x: number, y: number, width: number, height: number, paint?: SkPaint) { 'worklet';c.drawImageRectOptions(im,Skia.XYWHRect(0,0,im.width(),im.height()),Skia.XYWHRect(x,y,width,height),FilterMode.Linear,MipmapMode.None,paint??null); }
function pile(c: SkCanvas,scene: Scene,art: Resources) {
  'worklet';const paint=Skia.Paint();paint.setAntiAlias(true);
  for(const id of painterOrder){const p=scene.tokens[id];if(p.alpha<=0)continue;const a=p.angle*Math.PI/180,co=Math.cos(a),si=Math.sin(a),side=104*layouts[p.mood].radii[id]/43;c.save();c.concat([co,-si,p.center.x,si,co,p.center.y,0,0,1]);paint.setAlphaf(p.alpha);image(c,art.sprites[p.mood],-side/2,-side/2,side,side,paint);c.restore();}
}
function mask(c: SkCanvas,art: Resources,soft: boolean) {
  'worklet';const paint=Skia.Paint();paint.setBlendMode(BlendMode.DstIn);c.saveLayer(paint,Skia.XYWHRect(9,111,375,345));c.clipPath(soft?art.disk:art.sharpVisibility,ClipOp.Intersect,true);image(c,soft?art.softStrip:art.sharpStrip,9,111,375,345);c.restore();
}
/** Both weighted passes retain opaque occlusion before the single aggregate filter. */
export function draw(scene: Scene,art: Resources|null,height: number,bottom: number) {
  'worklet';const recorder=Skia.PictureRecorder(),c=recorder.beginRecording(Skia.XYWHRect(0,0,393,height)),p=Skia.Paint();p.setAntiAlias(true);p.setColor(color(0xF4F1EA));c.drawRect(Skia.XYWHRect(0,0,393,height),p);
  if(art){
    c.drawPicture(art.background);
    const guard=Skia.XYWHRect(9,111,375,345);
    c.save();c.clipRect(guard,ClipOp.Intersect,true);
    const crop=Skia.Paint();crop.setImageFilter(art.crop);c.saveLayer(crop,guard);
    c.saveLayer(undefined,guard);pile(c,scene,art);mask(c,art,false);c.restore();
    const add=Skia.Paint();add.setBlendMode(BlendMode.Plus);add.setAlphaf(.86);c.saveLayer(add,guard);
    const filter=Skia.Paint();filter.setImageFilter(art.softFilter);c.saveLayer(filter,guard);pile(c,scene,art);c.restore();mask(c,art,true);c.restore();
    c.restore();c.restore();
    c.drawPicture(art.rim);c.save();c.translate(0,-bottom);c.drawPicture(art.footer);c.restore();
  }
  return recorder.finishRecordingAsPicture();
}
