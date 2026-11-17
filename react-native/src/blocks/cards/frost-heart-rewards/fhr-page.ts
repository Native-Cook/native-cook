import { Skia, type SkFont, type SkPicture, type SkTypeface } from '@shopify/react-native-skia';

import { FHR_THEME as T } from './fhr-theme';

type Weight = 'regular'|'medium'|'semibold'|'bold';
export type FontSet = Record<Weight, SkTypeface>;
export type FontStyle = {size:number;weight:Weight;tracking:number};
export function fontFor(faces:FontSet, s:FontStyle):SkFont {
  // Skia 2.6 native reads this boolean through asNumber(), despite its TS signature.
  const f=Skia.Font(faces[s.weight],s.size); f.setSubpixel(1 as unknown as boolean); return f;
}
/** Retain fractional advances, including on Android; whole-glyph measurement breaks the match. */
export function textWidth(font:SkFont,text:string,tracking=0) {
  const widths=font.getGlyphWidths(font.getGlyphIDs(text));
  return widths.reduce((sum,w)=>sum+w,0)+Math.max(0,widths.length-1)*tracking;
}
export const ICONS = {
  back:'M29 79 L22 86 L29 93',
  chat:'M366 76 Q364 76 364 79 L364 90 Q364 92 367 92 L370 92 L370 97 L377 92 L383 92 Q386 92 386 89 L386 79 Q386 76 383 76 Z M369 81 L381 81 M369 85 L378 85',
  home:'M30 821 L30 818 L39 810 Q41 808 43 810 L49 815 M31 820 L31 830 Q31 832 34 832 L40 832 M46 819 A6 6 0 1 0 46 831 A6 6 0 1 0 46 819 M50 829 L54 833',
  list:'M111 812 L130 812 M111 820 L130 820 M111 828 L130 828',
  wallet:'M192 814 L198 809 L210 813 M192 813 L211 813 Q213 813 213 815 L213 829 Q213 831 211 831 L193 831 Q191 831 191 829 L191 816 Q191 813 194 813 M207 821 L213 821',
  cart:'M272 808 L276 822 L293 817 L289 811 L275 811 M278 827 A1 1 0 1 0 278 829 M288 827 A1 1 0 1 0 288 829',
  profile:'M375 820 A11 11 0 1 0 353 820 A11 11 0 1 0 375 820 M364 812 A3.5 3.5 0 1 0 364 819 A3.5 3.5 0 1 0 364 812 M357 827 Q364 819 371 827',
  clock:'M94 752 A4 4 0 1 0 94 760 A4 4 0 1 0 94 752 M94 753 L94 756 L97 757 M92 750 L96 750 M91 760 L90 762 M97 760 L98 762',
};

/** The fixed UI is a recorded picture. Only balance, chips and the level badge redraw. */
export function makePage(faces:FontSet,layer:'base'|'points'='base'):SkPicture {
  const r=Skia.PictureRecorder(), c=r.beginRecording(Skia.XYWHRect(0,0,402,874));
  const p=Skia.Paint();p.setAntiAlias(true);
  const L=T.layout, C=T.palette;
  const rect=(b:{x:number;y:number;width:number;height:number},radius:number,color:string)=>{
    p.setColor(Skia.Color(color)); p.setShader(null); c.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x,b.y,b.width,b.height),radius,radius),p);
  };
  const text=(str:string,x:number,y:number,style:FontStyle,color:string,centerWidth?:number)=>{
    const f=fontFor(faces,style), glyphs=f.getGlyphIDs(str), widths=f.getGlyphWidths(glyphs);
    const bearing=f.measureText(str).x;
    let xx=centerWidth==null?x-bearing:x+(centerWidth-textWidth(f,str,style.tracking))/2;
    p.setColor(Skia.Color(color));p.setShader(null);
    Array.from(str).forEach((ch,i)=>{c.drawText(ch,xx,y,p,f);xx+=widths[i]+style.tracking;});
  };
  const label=(b:{text:string;x:number;baseline:number},style:FontStyle,color:string)=>text(b.text,b.x,b.baseline,style,color);
  const stroke=(svg:string,col:string,w=1.8)=>{p.setColor(Skia.Color(col));p.setStyle(1);p.setStrokeWidth(w);p.setStrokeCap(1);p.setStrokeJoin(1);const path=Skia.Path.MakeFromSVGString(svg);if(path)c.drawPath(path,p);p.setStyle(0);};
  const pill=(b:{x:number;y:number;width:number;height:number;radius:number;baseline:number;text:string},col:string,style:FontStyle=T.fonts.pill,ink:string=C.white)=>{
    rect(b,b.radius,col);text(b.text,b.x,b.baseline,style,ink,b.width);
  };
  if(layer==='points'){
  L.points.brands.forEach((b,i)=>{c.save();c.rotate(b.rotation,b.x+b.width/2,b.y+b.height/2);pill(b,i===0?C.pink:C.violet,{...T.fonts.pill,size:i===0?10.8:12.8});c.restore();});
  label(L.points.nextTitle,T.fonts.nextTitle,C.text);
  stroke('M347 372 L350 375.5 L347 379',C.body,1.6);
  label(L.points.amount,T.fonts.amount,C.text);label(L.points.of150,T.fonts.fraction,C.muted);
    return r.finishRecordingAsPicture();
  }
  stroke(ICONS.back,C.pink,1.8);stroke(ICONS.chat,C.text,1.6);
  label(L.nav.title,T.fonts.nav,C.text);pill(L.nav.brand,C.pink,T.fonts.navBrand);
  label(L.daily.heading,T.fonts.heading,C.text);stroke('M176 480 L182 486 L176 492',C.text,1.8);
  rect(L.filters.panel,L.filters.panel.radius,C.panel);
  L.filters.pills.forEach(b=>pill(b,b.selected?C.white:C.chip,T.fonts.filter,b.selected?C.black:C.body));
  // Fade the final chip into the panel, matching the reference crop at the right edge.
  p.setShader(Skia.Shader.MakeLinearGradient({x:354,y:0},{x:394,y:0},['#1A1A1C00','#1A1A1C'].map(Skia.Color),[0,1],0));
  c.drawRect(Skia.XYWHRect(354,590,45,46),p);p.setShader(null);
  rect(L.task.card,L.task.card.radius,C.card);rect(L.task.peek,L.task.peek.radius,C.card);
  // The task's small glass medallion is vector-drawn; never fetches artwork or a logo.
  const b=L.task.icon; p.setShader(Skia.Shader.MakeLinearGradient({x:b.x,y:b.y},{x:b.x+b.width,y:b.y+b.height},['#DFB5FF','#9A42E8','#F448F2','#DDCAFF'].map(Skia.Color),[0,0.28,0.65,1],0));
  c.drawCircle(b.x+b.width/2,b.y+b.height/2,b.width/2,p);p.setShader(null);
  p.setColor(Skia.Color('#F0DDFF'));p.setStyle(1);p.setStrokeWidth(1.3);c.drawCircle(b.x+b.width/2,b.y+b.height/2,b.width/2-1,p);p.setStyle(0);
  text('lumi',b.x,b.y+32,{size:17.5,weight:'bold',tracking:-1.1},C.white,b.width);
  L.task.title.forEach(b=>label(b,T.fonts.taskTitle,C.text));
  label(L.task.line1,T.fonts.taskBody,C.body);label(L.task.line2,T.fonts.taskBody,C.body);
  pill(L.task.reward,'#49494B',T.fonts.taskReward);pill(L.task.rewardUp,C.orange,T.fonts.pill);
  label(L.task.timer,T.fonts.taskTimer,C.timer);stroke(ICONS.clock,C.timer,1.3);
  // The peek only needs its top arc: the tab bar occludes its contents.
  p.setColor(Skia.Color(C.violet));c.drawCircle(52,815,23,p);
  rect(L.tabs.bar,0,C.panel);
  stroke(ICONS.home,C.activeTab,1.9);stroke(ICONS.list,C.tab,2.2);stroke(ICONS.wallet,C.tab,2);stroke(ICONS.cart,C.tab,2);stroke(ICONS.profile,C.tab,1.8);
  pill(L.tabs.badge,C.badgeRed,T.fonts.cartBadge);
  return r.finishRecordingAsPicture();
}
