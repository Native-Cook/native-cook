import { BlendMode, ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkColor, type SkImage, type SkPoint } from '@shopify/react-native-skia';

import { GEOMETRY, type MeshPiece, type Vec2, type Vec3 } from './geometry';
import { add, clamp, cross, dot, hash, mul, normalized, rotate, sub, type HeroPose, type PiecePose } from './motion';
import type { Resources } from './resources';

type Face = { world: Vec3[]; uv: Vec2[]; cut: boolean; depth: number; shade: number };
export function color(r: number,g: number,b: number,a=1): SkColor { 'worklet'; return Float32Array.of(r,g,b,a); }
function polygon(points: SkPoint[]) { 'worklet'; const p=Skia.PathBuilder.Make().moveTo(points[0].x,points[0].y);for(let i=1;i<points.length;i++)p.lineTo(points[i].x,points[i].y);return p.close().detach(); }
function image(c: SkCanvas,im: SkImage,x: number,y: number,w: number,h: number,alpha=1,blend?: BlendMode) {
  'worklet'; const paint=Skia.Paint();paint.setAlphaf(alpha);paint.setBlendMode(blend??BlendMode.SrcOver);
  c.drawImageRectOptions(im,Skia.XYWHRect(0,0,im.width(),im.height()),Skia.XYWHRect(x,y,w,h),FilterMode.Linear,MipmapMode.None,paint);
}
function transformed(point: Vec3,piece: MeshPiece,state: PiecePose,yaw: number): Vec3 { 'worklet'; return rotate(add(state.center,rotate(sub(point,piece.center),state.rotation)),[yaw,0,0]); }
function project(v: Vec3): SkPoint { 'worklet'; const k=760/(760-v[2]);return {x:196.5+v[0]*k,y:446+v[1]*k}; }
function affine(source: Vec2[],target: SkPoint[]) {
  'worklet'; const [s0,s1,s2]=source,[d0,d1,d2]=target,ux=s1[0]-s0[0],uy=s1[1]-s0[1],vx=s2[0]-s0[0],vy=s2[1]-s0[1],det=ux*vy-vx*uy;
  if(Math.abs(det)<.00001)return null;
  const dx=d1.x-d0.x,dy=d1.y-d0.y,ex=d2.x-d0.x,ey=d2.y-d0.y,a=(dx*vy-ex*uy)/det,c=(ex*ux-dx*vx)/det,b=(dy*vy-ey*uy)/det,d=(ey*ux-dy*vx)/det;
  return [a,c,d0.x-a*s0[0]-c*s0[1],b,d,d0.y-b*s0[0]-d*s0[1],0,0,1];
}
function triangle(c: SkCanvas,face: Face,art: Resources,glow: number) {
  'worklet'; const points=face.world.map(project),transform=affine(face.uv,points);if(!transform)return;
  const center={x:(points[0].x+points[1].x+points[2].x)/3,y:(points[0].y+points[1].y+points[2].y)/3},coverage=points.map(p=>{const dx=p.x-center.x,dy=p.y-center.y,length=Math.max(.001,Math.hypot(dx,dy));return {x:p.x+dx/length*.32,y:p.y+dy/length*.32};}),path=polygon(coverage);
  c.save();c.clipPath(path,ClipOp.Intersect,false);c.concat(transform);if(face.cut)image(c,art.cut,0,0,64,24);else image(c,art.rock,-80,-120,160,240);c.restore();
  const shade=Skia.Paint();shade.setAntiAlias(false);shade.setColor(color(0,0,0,1-face.shade));c.drawPath(path,shade);
  if(face.cut){shade.setBlendMode(BlendMode.Plus);shade.setColor(color(.88,.44,.08,.06*glow));c.drawPath(path,shade);}
}
function cracks(c: SkCanvas,index: number,pose: HeroPose) {
  'worklet'; const state=pose.pieces[index],piece=GEOMETRY.pieces[index];
  for(let branch=0;branch<GEOMETRY.cracks.length;branch++){
    const crack=GEOMETRY.cracks[branch];if(crack.piece!==index)continue;const amount=clamp(pose.crack*4-branch);if(amount<=0)continue;
    const a=project(transformed(crack.a,piece,state,pose.manualYaw)),b=project(transformed(add(crack.a,mul(sub(crack.b,crack.a),amount)),piece,state,pose.manualYaw)),path=Skia.PathBuilder.Make().moveTo(a.x,a.y).lineTo(b.x,b.y).detach(),width=.6+2.4*pose.crack,p=Skia.Paint();
    p.setAntiAlias(true);p.setStyle(PaintStyle.Stroke);p.setStrokeCap(StrokeCap.Round);p.setStrokeWidth(width+.6);p.setColor(color(.025,.05,.032));c.drawPath(path,p);
    p.setBlendMode(BlendMode.Plus);p.setStrokeWidth(Math.max(.4,width*.45));p.setShader(Skia.Shader.MakeLinearGradient(a,b,[color(.91,.39,.08),color(.45,.72,.16)],[0,1],TileMode.Clamp));c.drawPath(path,p);
  }
}
function piece(c: SkCanvas,index: number,pose: HeroPose,art: Resources) {
  'worklet'; const mesh=GEOMETRY.pieces[index],state=pose.pieces[index],light=normalized([-.5,-.7,1]),faces: Face[]=[];
  for(const tri of mesh.triangles){
    const vertices=[tri.a,tri.b,tri.c],world=vertices.map(v=>transformed(v.point,mesh,state,pose.manualYaw)),normal=normalized(cross(sub(world[1],world[0]),sub(world[2],world[0]))),center=mul(add(add(world[0],world[1]),world[2]),1/3);
    if(dot(normal,[-center[0],-center[1],760-center[2]])<=0)continue;const diffuse=Math.max(0,dot(normal,light));
    faces.push({world,uv:vertices.map(v=>v.uv),cut:tri.cut,depth:center[2],shade:tri.cut?.63+.36*diffuse:.78+.22*diffuse});
  }
  faces.sort((a,b)=>a.depth-b.depth);const group=Skia.Paint();group.setAlphaf(state.alpha);c.saveLayer(group);
  for(const face of faces)triangle(c,face,art,pose.glowGain);if(pose.crack>.00001)cracks(c,index,pose);c.restore();
}
function core(c: SkCanvas,pose: HeroPose,art: Resources) {
  'worklet';const yaw=clamp(pose.coreYaw+pose.manualYaw,-26,26),index=(yaw+26)/4,a=Math.min(13,Math.max(0,Math.floor(index))),b=Math.min(13,a+1),q=index-a;
  c.save();c.translate(196.5,438);c.scale(pose.coreScale,pose.coreScale);c.saveLayer();
  if(b===a||q<=0)image(c,art.cores[a],-82,-106,164,212);else {image(c,art.cores[a],-82,-106,164,212,1-q);image(c,art.cores[b],-82,-106,164,212,q,BlendMode.Plus);}
  c.restore();c.restore();
}
function crumbs(c: SkCanvas,pose: HeroPose) {
  'worklet';for(let i=0;i<12;i++){const state=pose.pieces[i],angle=hash(i+5513)*2*Math.PI,size=1.5+2.5*hash(i+5669),travel=pose.crumbAmount*(8+18*hash(i+5781)),p=project(rotate(add(state.center,[Math.cos(angle)*travel,Math.sin(angle)*travel,4]),[pose.manualYaw,0,0])),path=polygon([{x:p.x-size*.6,y:p.y-size*.4},{x:p.x+size*.6,y:p.y-size*.25},{x:p.x+size*.1,y:p.y+size*.6}]),paint=Skia.Paint();paint.setAntiAlias(true);paint.setColor(color(.24,.28,.25,pose.crumbAmount*state.alpha*.65));c.drawPath(path,paint);}
}
/** Same projected solids and native image color management as the frozen Swift Canvas. */
export function drawHero(pose: HeroPose,art: Resources | null,height: number) {
  'worklet';const recorder=Skia.PictureRecorder(),c=recorder.beginRecording(Skia.XYWHRect(0,0,393,height));
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(16,136,361,608),27,27),ClipOp.Intersect,true);
  if(!art){const paint=Skia.Paint();paint.setAntiAlias(true);paint.setColor(Skia.Color('#505A52'));c.drawPath(polygon(GEOMETRY.hull.map(p=>project([p[0],p[1],12]))),paint);return recorder.finishRecordingAsPicture();}
  image(c,art.floor,46.5,544,300,100);image(c,art.contact,144.5,577,104,36);
  c.save();c.clipRect(Skia.XYWHRect(16,250,361,350),ClipOp.Intersect,true);c.translate(196.5,446+pose.globalY);c.scale(pose.globalScale,pose.globalScale);c.translate(-196.5,-446);
  image(c,art.glow,76.5,283,240,310,pose.glowGain);
  const order=GEOMETRY.pieces.map((_,i)=>i).sort((a,b)=>rotate(pose.pieces[a].center,[pose.manualYaw,0,0])[2]-rotate(pose.pieces[b].center,[pose.manualYaw,0,0])[2]);let drew=false;
  for(const i of order){const depth=rotate(pose.pieces[i].center,[pose.manualYaw,0,0])[2];if(!drew&&depth>=-16){core(c,pose,art);drew=true;}if(pose.pieces[i].alpha>.0001)piece(c,i,pose,art);}
  if(!drew)core(c,pose,art);if(pose.crumbAmount>.0001)crumbs(c,pose);c.restore();return recorder.finishRecordingAsPicture();
}
