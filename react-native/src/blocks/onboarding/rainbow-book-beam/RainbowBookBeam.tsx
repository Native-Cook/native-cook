import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, BlurStyle, PaintStyle, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

function impact() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

export const LOOP = 7.6;
const BG = '#040305';
function ease(value: number) { 'worklet'; const u = Math.max(0, Math.min(1, value)); return u * u * (3 - 2 * u); }
function paint(color: string, alpha = 1) {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(Skia.Color(color)); p.setAlphaf(Math.max(0, Math.min(1, alpha))); return p;
}
function fill(canvas: SkCanvas, path: SkPath, color: string, alpha = 1) {
  'worklet'; canvas.drawPath(path, paint(color, alpha));
}
function circle(x: number, y: number, radius: number) { 'worklet'; const p = Skia.Path.Make(); p.addCircle(x, y, radius); return p; }
function roundRect(x: number, y: number, width: number, height: number, radius: number) {
  'worklet'; const p = Skia.Path.Make(); p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, width, height), radius, radius)); return p;
}
type Fonts = Record<string, SkFont>;
function label(canvas: SkCanvas, fonts: Fonts, text: string, x: number, y: number, size: number, color: string, bold = false, alpha = 1) {
  'worklet'; const font = fonts[`${size}-${bold}`];
  const width = font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, n) => sum + n, 0);
  const metrics = font.getMetrics();
  canvas.drawText(text, x - width / 2, y - (metrics.ascent + metrics.descent) / 2, paint(color, alpha), font);
}


function hash(x: number) { 'worklet'; const v=Math.sin(x*12.9898)*43758.5453;return v-Math.floor(v); }
function polygon(points: number[][]) { 'worklet'; const p=Skia.Path.Make();p.moveTo(points[0][0],points[0][1]);for(let i=1;i<points.length;i++)p.lineTo(points[i][0],points[i][1]);p.close();return p; }
function stroke(c: SkCanvas,p: SkPath,color: string,width: number,alpha=1) { 'worklet'; const ink=paint(color,alpha);ink.setStyle(PaintStyle.Stroke);ink.setStrokeWidth(width);ink.setStrokeCap(StrokeCap.Round);ink.setStrokeJoin(StrokeJoin.Round);c.drawPath(p,ink); }
function glow(c: SkCanvas,p: SkPath,color: string,blur: number,alpha: number) { 'worklet'; const ink=paint(color,alpha);ink.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal,blur,true));c.drawPath(p,ink); }
function gradient(c: SkCanvas,p: SkPath,colors: string[],start: number[],end: number[],alpha=1) { 'worklet';const ink=paint('#FFFFFF',alpha);ink.setShader(Skia.Shader.MakeLinearGradient({x:start[0],y:start[1]},{x:end[0],y:end[1]},colors.map(color=>Skia.Color(color)),null,TileMode.Clamp));c.drawPath(p,ink); }
function beamFill(c: SkCanvas,p: SkPath,color: string,top: number,alpha: number) { 'worklet';const ink=paint('#FFFFFF',alpha);ink.setShader(Skia.Shader.MakeLinearGradient({x:196.5,y:495},{x:196.5,y:top},[Skia.Color('#FFF4D1'),Skia.Color(color),Skia.Color(color+'00')],[0,0.84,1],TileMode.Clamp));c.drawPath(p,ink); }
function beamEdge(c: SkCanvas,path: SkPath,top: number,beam: number) {
  'worklet'; const ink=paint('#FFFFFF',beam);ink.setStyle(PaintStyle.Stroke);ink.setStrokeCap(StrokeCap.Round);ink.setStrokeJoin(StrokeJoin.Round);
  ink.setShader(Skia.Shader.MakeLinearGradient({x:196.5,y:495},{x:196.5,y:top},[Skia.Color('rgba(255,241,216,.16)'),Skia.Color('rgba(255,224,245,.32)'),Skia.Color('rgba(255,224,245,0)')],[0,0.84,1],TileMode.Clamp));
  ink.setStrokeWidth(2.6);ink.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal,2,true));c.drawPath(path,ink);
  ink.setMaskFilter(null);ink.setStrokeWidth(0.65);c.drawPath(path,ink);
}
function draw(c: SkCanvas,f: Fonts,t: number,r: number) {
  'worklet';
  const phase=t/LOOP*Math.PI*2,openingBase=ease((t-1.2)/0.72)*(1-ease((t-5.55)/0.85)),age=Math.max(0,t-1.2);
  const recoil=0.24*Math.sin(age*12)*Math.exp(-age*2.9)*ease(age/0.1)*(1-ease((age-1.15)/0.35)),opening=Math.max(0,openingBase+recoil)*r;
  const beam=ease((t-1.72)/0.72)*(1-ease((t-4.8)/0.9))*r;
  label(c,f,'ORIA / A NEW CHAPTER',196.5,106,11,'#A497A4');label(c,f,'Stories open worlds.',196.5,173,27,'#F8EEF5',true);label(c,f,'Follow your curiosity, one page at a time.',196.5,210,14,'#A497A4');
  const reflected=Skia.Path.Make();reflected.addOval(Skia.XYWHRect(108.5,548,176,18));glow(c,reflected,'#E64BB0',12,0.04+beam*0.18);
  if(beam>0.001){
    const palette=['#FF3C77','#E63DAA','#E748C2','#FFEAA0','#F997D9'];
    const edges:SkPath[]=[];
    for(let band=0;band<5;band++){
      const points:number[][]=[],reflections:number[][]=[];const edge=Skia.Path.Make();
      for(let side=0;side<=1;side++)for(let step=0;step<=96;step++){
        const v=(side===0?step:96-step)/96*beam,width=(3+70*Math.pow(v,0.8))*(1+0.045*Math.sin(phase*4-v*6)),boundary=band+(side===0?0:1)-2.5;
        const sway=Math.sin(v*20-phase*14)*5*Math.pow(v,0.8)+10*v*v*Math.sin(phase*2),x=196.5+sway+boundary/2.5*width+Math.sin(v*26-phase*14+boundary*0.8)*1.7*v;
        points.push([x,495-238*v]);if(side===1){if(step===0)edge.moveTo(x,495-238*v);else edge.lineTo(x,495-238*v);}reflections.push([196.5+(x-196.5)*0.65*(1-0.55*v),519+238*v*0.19]);
      }
      const p=polygon(points),mirror=polygon(reflections);if(band<4)edges.push(edge);
      glow(c,mirror,palette[band],4,0.09*r);gradient(c,mirror,[palette[band],palette[band]+'00'],[196.5,519],[196.5,566],0.2*r);
      glow(c,p,palette[band],13,0.27*beam);beamFill(c,p,palette[band],495-238*beam,0.88*beam);
    }
    for(const edge of edges)beamEdge(c,edge,495-238*beam,beam);
    for(let i=0;i<12;i++){
      const v=(i*0.173+t/LOOP*3)%1,sway=Math.sin(v*20-phase*14)*5*Math.pow(v,0.8)+10*v*v*Math.sin(phase*2),x=196.5+sway+(hash(i+3)-0.5)*(5+118*Math.pow(v,0.8)),y=495-238*v,radius=1.4+2.6*hash(i+54);
      const alpha=ease(v/0.08)*(1-ease((v-0.85)/0.15))*ease((beam-v)/0.12)*r;
      glow(c,circle(x,y,radius*2),'#FFF7E9',6,alpha*0.7);fill(c,circle(x,y,radius),'#FFF7E9',alpha);
    }
  }
  const anticipate=Math.sin(Math.PI*Math.min(1,Math.max(0,(t-1.02)/0.22)));
  c.save();c.translate(196.5,500);c.rotate(-12*(1-opening)-6*anticipate*anticipate*r,0,0);c.translate(-196.5,-500);
  const openAlpha=ease(opening/0.22),closed=1-openAlpha;
  if(closed>0.001){const solid=polygon([[188.5,412],[206.5,415],[206.5,507],[188.5,504]]);gradient(c,solid,['#FFB826','#FF773A','#AF368F'],[188,412],[207,507],closed);stroke(c,solid,'#FFD256',0.9,closed);fill(c,polygon([[188.5,412],[191.5,413],[191.5,505],[188.5,504]]),'#A52E8E',closed);}
  if(openAlpha>0.001){
    const angle=opening*Math.PI/2,span=8*Math.cos(angle)+83*Math.sin(angle),tip=500-88*Math.cos(angle)-18*Math.sin(angle);
    for(const side of [-1,1]){
      const cover=Skia.Path.Make();cover.moveTo(196.5,501);cover.quadTo(196.5+side*span*0.54,491-45*Math.cos(angle),196.5+side*span,tip);cover.lineTo(196.5+side*(span+3*Math.sin(angle)),tip+8);cover.quadTo(196.5+side*span*0.55,506-35*Math.cos(angle),196.5,510);cover.close();
      glow(c,cover,'#FF5B67',12,0.22*openAlpha);gradient(c,cover,['#7D267F','#B82C9D','#F36663'],[196.5+side*span,tip],[196.5,510],openAlpha);stroke(c,cover,'#FF996B',0.7,openAlpha*0.7);
    }
    for(let layer=0;layer<3;layer++){
      const j=layer,leaf=ease((t-1.26-j*0.055)/0.72)*(1-ease((t-5.30-j*0.06)/0.82))*r,a=leaf*Math.PI/2,w=8*Math.cos(a)+81*Math.sin(a),tip=500-88*Math.cos(a)-18*Math.sin(a)-j*1.4,curl=Math.pow(Math.sin(leaf*Math.PI),2)*(7+j*3)*Math.sin(phase*8+j);
      for(const side of [-1,1]){
        const page=Skia.Path.Make();page.moveTo(196.5,498-j*0.6);page.quadTo(196.5+side*w*0.48,489-56*Math.cos(a)-10*Math.sin(a)-curl,196.5+side*w,tip-5);page.lineTo(196.5+side*(w+2*Math.sin(a)),tip+3);page.quadTo(196.5+side*w*0.55,503-39*Math.cos(a)-3*Math.sin(a),196.5,504-j*0.5);page.close();
        gradient(c,page,layer===2?['#FFD457','#FF9A3F','#FF583B']:['#FF9E33','#E96C38','#B94B69'],[196.5+side*w,tip-5],[196.5,504],openAlpha);stroke(c,page,'#FFD256',0.65,openAlpha*(0.4+j*0.2));
      }
    }
    const spine=Skia.Path.Make();spine.moveTo(188.5,505);spine.quadTo(196.5,517,204.5,505);stroke(c,spine,'#69BBD1',1.3,0.7*openAlpha);
  }
  c.restore();
  label(c,f,'One page. Endless places.',196.5,621,17,'#F8EEF5',true);fill(c,roundRect(42,700,309,54,27),'#F5D5EA');label(c,f,'Open your world',196.5,727,15,'#40152F',true);label(c,f,'A LITTLE WONDER, EVERY DAY',196.5,783,11,'#A497A4');
}

/** Deterministic scene; previewTime is optional for still previews. */
export function RainbowBookBeam({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [armed, setArmed] = useState(false);
  const armClock = useCallback(() => setArmed(true), []);
  const stopClock = useCallback(() => setArmed(false), []);
  useEffect(() => { const sub = AppState.addEventListener('change', state => setActive(state === 'active')); return () => sub.remove(); }, []);
  const time = useSharedValue(0), elapsed = useSharedValue(0), held = useSharedValue(0);
  const touching = useSharedValue(false), released = useSharedValue(-1);
  const recovery = useSharedValue(1);
  const startEpoch = useSharedValue(-1), stopped = useSharedValue(false);
  const panning = useSharedValue(false);
  const touchOrigin = useSharedValue(0), tapAt = useSharedValue(-1), tapFrom = useSharedValue(0);
  const pointerOrigin = useSharedValue(0);
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? 3.6 : 0); recovery.set(1); }, [previewTime, reduced, time, recovery]);
  const frame = useFrameCallback(info => {
    if (startEpoch.get() < 0) startEpoch.set(info.timestamp);
    elapsed.set((info.timestamp - startEpoch.get()) / 1000);
    if (touching.get()) { recovery.set(1); time.set(held.get()); return; }
    if (tapAt.get() >= 0 && elapsed.get() - tapAt.get() < (3.6 - tapFrom.get()) / 2.125) {
      recovery.set(1); time.set(tapFrom.get() + Math.max(0, elapsed.get() - tapAt.get()) * 2.125); return;
    }
    if (released.get() >= 0) {
      const age = elapsed.get() - released.get();
      time.set(age < 2.6 ? held.get() : autoPlay ? (age - 2.6) % LOOP : 0);
      recovery.set(age < 2.6 ? 1 - ease((age - 2) / 0.6) : 1);
      if (!autoPlay && age >= 2.6 && !stopped.get()) { stopped.set(true); scheduleOnRN(stopClock); }
    } else { recovery.set(1); time.set(autoPlay ? elapsed.get() % LOOP : 0); }
  }, false);
  useEffect(() => { frame.setActive(active && (autoPlay || armed) && !reduced && previewTime === undefined); return () => frame.setActive(false); }, [active, autoPlay, armed, reduced, previewTime, frame]);
  const fonts = useMemo(() => {
    const result: Fonts = {};
    for (const size of [11, 12, 13, 14, 15, 17, 20, 22, 27, 29]) for (const bold of [false, true]) {
      result[`${size}-${bold}`] = matchFont({ fontFamily: Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: size, fontWeight: bold ? '700' : '400' });
    }
    return result;
  }, []);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.drawColor(Skia.Color(BG));
    const scale = Math.min(width / 393, height / 852);
    canvas.translate((width - 393 * scale) / 2, (height - 852 * scale) / 2); canvas.scale(scale, scale);
    draw(canvas, fonts, time.get(), recovery.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, fonts]);
  const enabled = !reduced && previewTime === undefined;
  // Arming the paused clock must not replace handlers during an active drag.
  const gesture = useMemo(() => {
    const pan = Gesture.Pan().minDistance(4).enabled(enabled)
      // Native pan translation restarts at activation; keep the full touch-down distance.
      .onBegin(event => { pointerOrigin.set(event.absoluteY); })
      .onStart(() => {
        stopped.set(false);
        if (!autoPlay) { startEpoch.set(-1); elapsed.set(0); scheduleOnRN(armClock); }
        touchOrigin.set(time.get()); tapAt.set(-1);
        panning.set(true); touching.set(true); held.set(time.get());
      })
      .onUpdate(e => { held.set(Math.max(0, Math.min(4.8, touchOrigin.get() - (e.absoluteY - pointerOrigin.get()) / 240 * 3.6))); time.set(held.get()); })
      .onEnd((event, success) => {
        if (!success) return;
        held.set(Math.max(0, Math.min(4.8, touchOrigin.get() - (event.absoluteY - pointerOrigin.get()) / 240 * 3.6))); time.set(held.get());
        scheduleOnRN(impact);
      })
      .onFinalize(() => {
        if (!panning.get()) return;
        panning.set(false); touching.set(false); released.set(elapsed.get());
      });
    const tap = Gesture.Tap().maxDistance(8).enabled(enabled)
      .onEnd((_event, success) => {
        if (!success) return;
        stopped.set(false);
        if (!autoPlay) { startEpoch.set(-1); elapsed.set(0); scheduleOnRN(armClock); }
        tapFrom.set(time.get() >= 1.05 && time.get() < 3.4 ? time.get() : 1.05); tapAt.set(elapsed.get());
        touching.set(false); held.set(3.6); time.set(tapFrom.get()); recovery.set(1); released.set(elapsed.get());
        scheduleOnRN(impact);
      });
    return Gesture.Exclusive(pan, tap);
  }, [autoPlay, enabled, stopped, startEpoch, elapsed, armClock, touchOrigin, pointerOrigin, time, tapAt, panning, touching, held, released, tapFrom, recovery]);
  return <GestureDetector gesture={gesture}><View style={styles.screen} accessible accessibilityLabel="Oria reading. Stories open worlds. Drag upward to open the book.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
