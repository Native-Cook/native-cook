import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

export const LOOP = 8;
const BG = '#060708';
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

function draw(c: SkCanvas, f: Fonts, t: number) {
  'worklet'; const fold=ease((t-0.6)/2)*(1-ease((t-5)/2.4));
  const turn=ease((t-2.6)/4.8)*Math.PI*2,yaw=-0.55*fold+turn,pitch=-0.35*fold;
  for(let face=0;face<6;face++)for(let row=0;row<9;row++)for(let col=0;col<9;col++){
    const u=col/4-1,v=row/4-1;let x=u,y=v,z=-1;
    if(face===1)z=1;else if(face===2){x=-1;z=u;}else if(face===3){x=1;z=u;}else if(face===4){y=-1;z=v;}else if(face===5){y=1;z=v;}
    const index=face*81+row*9+col,flatX=(index%27-13)*8,flatY=(Math.floor(index/27)-8.5)*8;
    x=flatX*(1-fold)+x*68*fold;y=flatY*(1-fold)+y*68*fold;z*=68*fold;
    const rx=x*Math.cos(yaw)+z*Math.sin(yaw),rz=-x*Math.sin(yaw)+z*Math.cos(yaw);
    const ry=y*Math.cos(pitch)-rz*Math.sin(pitch),depth=y*Math.sin(pitch)+rz*Math.cos(pitch),perspective=400/(400+depth);
    const near=Math.min(1,Math.max(0,0.5-depth/240)),edge=(row===0||row===8||col===0||col===8)?fold:0;
    const radius=0.9+near*0.7+0.35*edge;
    fill(c,circle(196.5+rx*perspective,350+ry*perspective,radius),'#FFFFFF',Math.min(1,0.18+0.72*near+0.18*edge));
  }
  label(c,f,'forme',196.5,175,34,'#FFFFFF',true);label(c,f,'EVERY DETAIL FINDS ITS PLACE',196.5,488,11,'#85888F');
  label(c,f,'Small pieces.',196.5,568,30,'#FFFFFF',true);label(c,f,'A bigger picture.',196.5,605,30,'#FFFFFF',true);
  label(c,f,'Give your next idea a little dimension.',196.5,649,14,'#A5A5AE');
  fill(c,roundRect(28,700,337,54,27),'#FFFFFF');label(c,f,'Bring it together',196.5,727,15,BG,true);
  label(c,f,'Made of possibilities',196.5,783,11,'#85888F');
}


/** Deterministic scene; previewTime is optional for still previews. */
export function ParticleFoldSplash({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => { const sub = AppState.addEventListener('change', state => setActive(state === 'active')); return () => sub.remove(); }, []);
  const time = useSharedValue(0), elapsed = useSharedValue(0), held = useSharedValue(0);
  const touching = useSharedValue(false), released = useSharedValue(-1);
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? 4.8 : 0); }, [previewTime, reduced, time]);
  const frame = useFrameCallback(info => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    if (touching.get()) { time.set(held.get()); return; }
    if (released.get() >= 0) {
      const age = elapsed.get() - released.get();
      time.set(age < 2 ? held.get() : age < 2.6 ? held.get() * (1 - ease((age - 2) / 0.6)) : autoPlay ? (age - 2.6) % LOOP : 0);
    } else { time.set(autoPlay ? elapsed.get() % LOOP : 0); }
  }, false);
  useEffect(() => { frame.setActive(active && !reduced && previewTime === undefined); return () => frame.setActive(false); }, [active, reduced, previewTime, frame]);
  const fonts = useMemo(() => {
    const result: Fonts = {};
    for (const size of [11, 12, 14, 15, 16, 18, 24, 30, 32, 34, 36, 42, 46]) for (const bold of [false, true]) {
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
    draw(canvas, fonts, time.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, fonts]);
  const haptic = () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); };
  const pan = Gesture.Pan().minDistance(0).enabled(!reduced && previewTime === undefined)
    .onBegin(e => { touching.set(true); held.set(Math.max(0, Math.min(7.4, e.x / width * 7.4))); scheduleOnRN(haptic); })
    .onUpdate(e => { held.set(Math.max(0, Math.min(7.4, e.x / width * 7.4))); })
    .onEnd(e => { if (Math.abs(e.translationX) < 8) held.set(4.8); })
    .onFinalize(() => { touching.set(false); released.set(elapsed.get()); });
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityLabel="Forme. Small pieces. A bigger picture. Drag to fold the particles.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
