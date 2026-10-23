import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, ClipOp, Skia, matchFont, BlurStyle, PaintStyle, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

function impact() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

export const LOOP = 6;
const BG = '#050207';
function ease(value: number) { 'worklet'; const u = Math.max(0, Math.min(1, value)); return u * u * (3 - 2 * u); }
function paint(color: string, alpha = 1) {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(Skia.Color(color)); p.setAlphaf(Math.max(0, Math.min(1, alpha))); return p;
}
function fill(canvas: SkCanvas, path: SkPath, color: string, alpha = 1) {
  'worklet'; canvas.drawPath(path, paint(color, alpha));
}
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
function stroke(c: SkCanvas,p: SkPath,color: string,width: number,alpha=1) { 'worklet'; const ink=paint(color,alpha);ink.setStyle(PaintStyle.Stroke);ink.setStrokeWidth(width);ink.setStrokeCap(StrokeCap.Round);ink.setStrokeJoin(StrokeJoin.Round);c.drawPath(p,ink); }
function glow(c: SkCanvas,p: SkPath,color: string,blur: number,alpha: number) { 'worklet'; const ink=paint(color,alpha);ink.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal,blur,true));c.drawPath(p,ink); }
function gradient(c: SkCanvas,p: SkPath,colors: string[],start: number[],end: number[],alpha=1) { 'worklet';const ink=paint('#FFFFFF',alpha);ink.setShader(Skia.Shader.MakeLinearGradient({x:start[0],y:start[1]},{x:end[0],y:end[1]},colors.map(color=>Skia.Color(color)),null,TileMode.Clamp));c.drawPath(p,ink); }
function draw(c: SkCanvas,f: Fonts,t: number,r: number) {
  'worklet';
  gradient(c,roundRect(0,0,393,852,0),['#050207','#0B030F','#241027'],[196,100],[196,852]);
  const event=ease((t-1.2)/0.35)*(1-ease((t-3)/0.5))*r,caption=ease((t-1.75)/0.4)*(1-ease((t-3.9)/0.6))*r;
  label(c,f,'PERKS / REWARDS',196.5,126,11,'#997B9E');label(c,f,'A little extra.',196.5,190,29,'#F1DBF5',true);label(c,f,'From your everyday moments.',196.5,229,14,'#997B9E');
  for(let wave=0;wave<2;wave++){const u=(t-1.52-wave*0.16)/1.35;if(u>0&&u<1){const q=1-Math.pow(1-u,3),alpha=ease(u/0.09)*Math.pow(1-u,2)*0.62*r;stroke(c,roundRect(133.5-48*q,337-22*q,126+96*q,46+44*q,23+22*q),'#C453CE',0.8,alpha);}}
  const anticipation=Math.sin(Math.PI*Math.min(1,Math.max(0,(t-1.05)/0.2))),age=Math.max(0,t-1.25);
  const impulse=Math.sin(age*17)*Math.exp(-age*5.2)*ease(age/0.08)*(1-ease((age-0.75)/0.3));
  const kick=(-0.025*anticipation*anticipation+0.15*impulse)*r;
  c.save();c.translate(196.5,360);c.scale(1+kick,1+kick);c.translate(-196.5,-360);
  glow(c,roundRect(134,337,125,46,23),'#AA2DB7',16,0.11+0.16*event);fill(c,roundRect(133.5,337,126,46,23),'#100512');stroke(c,roundRect(133.5,337,126,46,23),'#A448AD',1.2);
  c.save();c.translate(154,360);c.rotate(-14-9*impulse*r,0,0);c.translate(-154,-360);
  fill(c,roundRect(140,343,28,35,2),'#7B258D');fill(c,roundRect(142,341,24,33,1.5),'#F7C0FA');stroke(c,roundRect(145,344,18,27,0.5),'#C16CCE',0.5);label(c,f,'$',154,358,20,'#80278F',true);c.restore();
  const value=45*ease((t-1.25)/0.7)*(1-ease((t-3.9)/0.6))*r,expanded=ease(value/10);
  c.save();c.clipRect(Skia.XYWHRect(174,344,74,32),ClipOp.Intersect,true);label(c,f,'$',199-9*expanded,360,22,'#DE57E8',true);
  for(let column=0;column<2;column++){const turns=column===0?Math.floor(value/10)+ease(value%10-9):value,digit=Math.floor(turns)%10,offset=turns-Math.floor(turns),x=column===0?205:218;
    for(let row=0;row<=1;row++){const number=(digit+row)%10;if(column===0&&number===0)continue;label(c,f,String(number),x,360+(row-offset)*32,22,'#DE57E8',true);}}
  c.restore();c.restore();
  for(let i=0;i<22;i++){const seed=hash(i+2),q=(t-1.43-i%5*0.028)/(1.25+0.25*seed);if(q>0&&q<1){const side=i%2===0?-1:1,u=(1-Math.exp(-3*q))/(1-Math.exp(-3)),x=196.5+side*(29+(77+38*seed)*u),y=360-(78+45*seed)*q+(100+36*hash(i+31))*q*q;
    const alpha=ease(q/0.055)*(1-ease((q-0.62)/0.38))*r,flutter=Math.cos(q*(12+9*seed)+seed*6.28),w=(3+3*seed)*(0.18+0.82*Math.abs(flutter)),h=8+5*hash(i+23);
    c.save();c.translate(x,y);c.rotate(side*(q*4+seed*2)*180/Math.PI,0,0);const p=Skia.Path.Make();p.moveTo(-w/2,-h/2);p.quadTo(0,-h/2-3*flutter,w/2,-h/2);p.lineTo(w/2,h/2);p.quadTo(0,h/2-3*flutter,-w/2,h/2);p.close();
    gradient(c,p,flutter>0?['#FEA8FF','#BF3BD0','#6E217F']:['#7C278F','#DD65E8','#A839BA'],[-w,0],[w,0],alpha);stroke(c,p,'#FFD3FF',0.35,alpha*Math.max(0,flutter)*0.65);c.restore();}}
  label(c,f,'LIFETIME CASHBACK',196.5,445-16*caption,11,'#997B9E');label(c,f,'$1,367 earned so far',196.5,476-16*caption,14,'#E1ABE6');fill(c,roundRect(42,652,309,54,27),'#E9B7EF');label(c,f,'Claim your cashback',196.5,679,15,'#36103E',true);label(c,f,'GOOD THINGS ADD UP',196.5,745,11,'#997B9E');
}

/** Deterministic scene; previewTime is optional for still previews. */
export function CashbackConfetti({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
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
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? 2.2 : 0); recovery.set(1); }, [previewTime, reduced, time, recovery]);
  const frame = useFrameCallback(info => {
    if (startEpoch.get() < 0) startEpoch.set(info.timestamp);
    elapsed.set((info.timestamp - startEpoch.get()) / 1000);
    if (touching.get()) { recovery.set(1); time.set(held.get()); return; }
    if (tapAt.get() >= 0 && elapsed.get() - tapAt.get() < (3.1 - tapFrom.get()) / 2.05) {
      recovery.set(1); time.set(tapFrom.get() + Math.max(0, elapsed.get() - tapAt.get()) * 2.05); return;
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
      .onBegin(event => { pointerOrigin.set(event.absoluteX); })
      .onStart(() => {
        stopped.set(false);
        if (!autoPlay) { startEpoch.set(-1); elapsed.set(0); scheduleOnRN(armClock); }
        touchOrigin.set(time.get()); tapAt.set(-1);
        panning.set(true); touching.set(true); held.set(time.get());
      })
      .onUpdate(e => { held.set(Math.max(0, Math.min(3.0, touchOrigin.get() + (e.absoluteX - pointerOrigin.get()) / width * 3.0))); time.set(held.get()); })
      .onEnd((event, success) => {
        if (!success) return;
        held.set(Math.max(0, Math.min(3.0, touchOrigin.get() + (event.absoluteX - pointerOrigin.get()) / width * 3.0))); time.set(held.get());
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
        tapFrom.set(time.get() >= 1.05 && time.get() < 2.6 ? time.get() : 1.05); tapAt.set(elapsed.get());
        touching.set(false); held.set(3.1); time.set(tapFrom.get()); recovery.set(1); released.set(elapsed.get());
        scheduleOnRN(impact);
      });
    return Gesture.Exclusive(pan, tap);
  }, [autoPlay, enabled, width, stopped, startEpoch, elapsed, armClock, touchOrigin, pointerOrigin, time, tapAt, panning, touching, held, released, tapFrom, recovery]);
  return <GestureDetector gesture={gesture}><View style={styles.screen} accessible accessibilityLabel="Perks rewards. Claim your cashback. Drag to celebrate.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
