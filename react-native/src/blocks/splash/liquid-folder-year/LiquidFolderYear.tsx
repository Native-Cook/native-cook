import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Fill, Group, Rect, Shader, Skia, matchFont, PaintStyle, StrokeCap, StrokeJoin, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { LIQUID_FOLDER_SHADER } from './liquid-folder-shader';
const effect = Skia.RuntimeEffect.Make(LIQUID_FOLDER_SHADER);
if (!effect) throw new Error('Liquid Folder Year shader did not compile.');
function impact() { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }

export const LOOP = 8.8;
const BG = '#030305';
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
function stroke(c: SkCanvas,p: SkPath,color: string,width: number,alpha=1) { 'worklet'; const ink=paint(color,alpha);ink.setStyle(PaintStyle.Stroke);ink.setStrokeWidth(width);ink.setStrokeCap(StrokeCap.Round);ink.setStrokeJoin(StrokeJoin.Round);c.drawPath(p,ink); }
function draw(c: SkCanvas,f: Fonts,t: number,r: number) {
 'worklet';const active=ease((t-1.4)/0.5)*(1-ease((t-4.6)/0.5))*r;for(let i=0;i<24;i++){const phase=(t/LOOP*3+hash(i+4))%1,a=i*2.39996,r=58+85*phase;fill(c,circle(196.5+Math.cos(a)*r,405+Math.sin(a)*r*0.65,0.6+1.2*hash(i+5)),i%3===0?'#7EF9EA':'#E9A0FF',active*Math.pow(Math.sin(phase*Math.PI),2));}
 label(c,f,'VELA / YEARBOOK',196.5,122,11,'#908899');label(c,f,'Make room for more.',196.5,191,27,'#F4EEF8',true);label(c,f,'A new year. A whole new kind of canvas.',196.5,228,14,'#908899');label(c,f,'New possibilities',196.5,568,13,'#F4EEF8',true);label(c,f,'Let’s start something new.',196.5,599,13,'#A79AAE');stroke(c,roundRect(49,687,295,52,26),'#6E4282',1);label(c,f,'Create your yearbook',196.5,713,15,'#EEDDF5',true);label(c,f,'IDEAS HAVE A WAY OF FINDING YOU',196.5,776,11,'#908899');
}

/** Deterministic scene; previewTime is optional for still previews. */
export function LiquidFolderYear({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
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
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? 4.8 : 0); recovery.set(1); }, [previewTime, reduced, time, recovery]);
  const frame = useFrameCallback(info => {
    if (startEpoch.get() < 0) startEpoch.set(info.timestamp);
    elapsed.set((info.timestamp - startEpoch.get()) / 1000);
    if (touching.get()) { recovery.set(1); time.set(held.get()); return; }
    if (tapAt.get() >= 0 && elapsed.get() - tapAt.get() < (4.8 - tapFrom.get()) / 2.8461538461538463) {
      recovery.set(1); time.set(tapFrom.get() + Math.max(0, elapsed.get() - tapAt.get()) * 2.8461538461538463); return;
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
  const uniforms = useDerivedValue(() => ({ uTime: time.get(), uRecovery: recovery.get() }));
  const scale = Math.min(width / 393, height / 852);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));

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
      .onUpdate(e => { held.set(Math.max(0, Math.min(5.2, touchOrigin.get() + (e.absoluteX - pointerOrigin.get()) / width * 5.2))); time.set(held.get()); })
      .onEnd((event, success) => {
        if (!success) return;
        held.set(Math.max(0, Math.min(5.2, touchOrigin.get() + (event.absoluteX - pointerOrigin.get()) / width * 5.2))); time.set(held.get());
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
        tapFrom.set(time.get() >= 1.1 && time.get() < 4.6 ? time.get() : 1.1); tapAt.set(elapsed.get());
        touching.set(false); held.set(4.8); time.set(tapFrom.get()); recovery.set(1); released.set(elapsed.get());
        scheduleOnRN(impact);
      });
    return Gesture.Exclusive(pan, tap);
  }, [autoPlay, enabled, width, stopped, startEpoch, elapsed, armClock, touchOrigin, pointerOrigin, time, tapAt, panning, touching, held, released, tapFrom, recovery]);
  return <GestureDetector gesture={gesture}><View style={styles.screen} accessible accessibilityLabel="Vela yearbook. A folder turns into liquid and the new year. Drag to reveal.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Fill color={BG} /><Group transform={[{ translateX: (width-393*scale)/2 }, { translateY: (height-852*scale)/2 }, { scale }]}><Rect x={0} y={0} width={393} height={852}><Shader source={effect!} uniforms={uniforms} /></Rect></Group><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
