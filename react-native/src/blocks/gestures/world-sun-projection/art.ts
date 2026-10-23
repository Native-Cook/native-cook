import { ClipOp,PaintStyle,Skia,type SkCanvas,type SkFont,type SkPaint } from '@shopify/react-native-skia';
import { camera,clamp,dot,project,sun,time,type Point,type Pose,type Vec } from './motion';
import type { Resources } from './resources';
function color(hex:number,alpha=1){'worklet';return Float32Array.of(((hex>>16)&255)/255,((hex>>8)&255)/255,(hex&255)/255,alpha);}
function line(directions:Vec[],pose:Pose){
 'worklet';const path=Skia.PathBuilder.Make(),c=camera(pose),near=.0600001;let previous:Vec|null=null;
 for(const d of directions){if(previous){const z0=dot(previous,c.forward),z1=dot(d,c.forward);if(z0>near||z1>near){const den=z1-z0,u=Math.abs(den)>1e-12?(near-z0)/den:0,cross={x:previous.x*(1-u)+d.x*u,y:previous.y*(1-u)+d.y*u,z:previous.z*(1-u)+d.z*u},a=project(z0>near?previous:cross,pose),b=project(z1>near?d:cross,pose);if(a&&b){path.moveTo(a.x,a.y);path.lineTo(b.x,b.y);}}}previous=d;}
 return path.detach();
}
function textAt(c:SkCanvas,text:string,p:Point,font:SkFont,paint:SkPaint,center=false){'worklet';const metrics=font.getMetrics(),width=center?font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a,b)=>a+b,0):0;c.drawText(text,p.x-width/2,p.y-(metrics.ascent+metrics.descent)/2,paint,font);}
export type Fonts={small:SkFont;bubble:SkFont};
export function draw(pose:Pose,art:Resources|null,grid:boolean,fonts:Fonts,markerAlpha:number){
 'worklet';const recorder=Skia.PictureRecorder(),c=recorder.beginRecording(Skia.XYWHRect(0,0,393,852)),paint=Skia.Paint();paint.setAntiAlias(true);paint.setColor(color(0x293432));c.drawRect(Skia.XYWHRect(0,0,393,852),paint);
 if(!art)return recorder.finishRecordingAsPicture();
 paint.setColor(color(0xFFFFFF));paint.setShader(art.effect.makeShaderWithChildren([pose.yaw,pose.pitch],[art.shader]));c.drawRect(Skia.XYWHRect(0,0,393,852),paint);paint.setShader(null);
 c.save();c.clipRect(Skia.XYWHRect(0,0,393,852),ClipOp.Intersect,true);paint.setStyle(PaintStyle.Stroke);
 if(grid)for(const elevation of[-15,0,15,30,45,60,75]){const dirs:Vec[]=[];for(let i=0;i<=96;i++){const a=(-100+i*200/96)*Math.PI/180,e=elevation*Math.PI/180;dirs.push({x:Math.sin(a)*Math.cos(e),y:Math.sin(e),z:Math.cos(a)*Math.cos(e)});}paint.setColor(color(0xCBD9D8,.4));paint.setStrokeWidth(.55);c.drawPath(line(dirs,pose),paint);}
 const dirs:Vec[]=[];for(let i=0;i<64;i++)dirs.push(sun(-.1+i/63*1.22));const guide=line(dirs,pose);paint.setColor(color(0x000000,.26));paint.setStrokeWidth(2.4);c.drawPath(guide,paint);paint.setColor(color(0xFAF9F3,.72));paint.setStrokeWidth(1.1);c.drawPath(guide,paint);paint.setStyle(PaintStyle.Fill);
 let previous:Point|null=null;paint.setColor(color(0xFAF9F3));for(let hour=6;hour<=11;hour++){const p=project(sun((hour*60-309)/350),pose);if(!p||p.x<=-25||p.x>=360||p.y<=110||p.y>=710)continue;if(previous&&Math.hypot(p.x-previous.x,p.y-previous.y)<28)continue;textAt(c,String(hour).padStart(2,'0')+':00',{x:p.x+10,y:p.y},fonts.small,paint);previous=p;}
 const tick=project(sun(.12),pose);if(tick){paint.setColor(color(0xD5CC47));paint.setStrokeWidth(1);c.drawLine(tick.x-4,tick.y,tick.x+4,tick.y,paint);c.drawLine(tick.x,tick.y-4,tick.x,tick.y+4,paint);}
 const selected=project(sun(pose.q),pose);if(selected){paint.setColor(color(0xFAF9F3,markerAlpha));c.drawCircle(selected.x,selected.y,11,paint);paint.setStyle(PaintStyle.Stroke);paint.setColor(color(0x000000,.23*markerAlpha));paint.setStrokeWidth(.8);c.drawCircle(selected.x,selected.y,11,paint);paint.setStyle(PaintStyle.Fill);
 const center={x:clamp(selected.x+36,52,341),y:clamp(selected.y-20,111,698)};if(Math.abs(center.x-selected.x-36)>.1||Math.abs(center.y-selected.y+20)>.1){const dx=selected.x-center.x,dy=selected.y-center.y,length=Math.max(1,Math.hypot(dx,dy));paint.setColor(color(0xFAF9F3,.8*markerAlpha));paint.setStrokeWidth(.8);c.drawLine(center.x+dx/length*13,center.y+dy/length*13,center.x+dx/length*22,center.y+dy/length*22,paint);}
 paint.setColor(color(0xFAF9F3,markerAlpha));c.drawRRect(Skia.RRectXY(Skia.XYWHRect(center.x-36,center.y-12.5,72,25),12.5,12.5),paint);paint.setColor(color(0x293432,markerAlpha));textAt(c,time(pose.q),center,fonts.bubble,paint,true);}
 paint.setColor(color(0xFAF9F3,.44));paint.setStrokeWidth(.7);for(const sign of[-1,1]){c.drawLine(196.5+sign*5,426,196.5+sign*8,426,paint);c.drawLine(196.5,426+sign*5,196.5,426+sign*8,paint);}c.restore();return recorder.finishRecordingAsPicture();
}
